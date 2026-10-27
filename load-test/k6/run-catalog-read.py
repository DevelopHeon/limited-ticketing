#!/usr/bin/env python3
"""Record one read-only catalog experiment without restarting services or overwriting prior runs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
from baseline_results import RunArtifacts, metric

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / 'load-test/k6/results'


def enabled_metric(text):
    match = re.search(r'^sale_cache_enabled\s+([01](?:\.0)?)\s*$', text, re.MULTILINE)
    if not match:
        raise ValueError('Missing observed sale_cache_enabled metric')
    return float(match.group(1)) == 1


def validate_settings(fixture, mode, phase, iterations, vus):
    ids = fixture.get('saleProductIds', [])
    if (not isinstance(fixture.get('runId'), str) or not fixture['runId'] or len(ids) < 20
            or fixture.get('productCount') != len(ids) or len(set(ids)) != len(ids)
            or any(type(value) is not int or value <= 0 for value in ids)):
        raise ValueError('Invalid catalog fixture')
    if (mode not in ('detail', 'list') or phase not in ('cold', 'steady') or iterations <= 0
            or not 1 <= vus <= 50 or (phase == 'cold' and (vus != 1 or (mode == 'detail' and iterations > len(ids))))):
        raise ValueError('Invalid catalog scenario')


def metrics(base_url):
    request = urllib.request.Request(base_url.rstrip('/') + '/actuator/prometheus', headers={'X-Admin-Id': 'admin'})
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.read().decode()


def execute(args):
    fixture_path = Path(args.fixture).resolve()
    fixture = json.loads(fixture_path.read_text())
    validate_settings(fixture, args.mode, args.phase, args.iterations, args.vus)
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.label) or not fixture_path.is_relative_to(RESULTS):
        raise ValueError('Use a safe label and a fixture under load-test/k6/results')
    before = metrics(args.base_url)
    if enabled_metric(before) != args.cache_enabled:
        raise ValueError('Observed application cache setting differs from the requested experiment')
    source_paths = [*sorted((ROOT / 'sale/src/main').rglob('*')), ROOT / 'app/src/main/resources/application.yml',
                    Path(__file__).resolve(), ROOT / 'load-test/k6/catalog-read.js', ROOT / 'load-test/k6/lib/catalog-read-contract.mjs',
                    ROOT / 'load-test/k6/sql/prepare-catalog-read.sql', ROOT / 'load-test/k6/sql/verify-catalog-read.sql']
    metadata = {'mode': args.mode, 'phase': args.phase, 'iterations': args.iterations, 'vus': args.vus,
                'cacheEnabled': args.cache_enabled, 'baseUrl': args.base_url, 'fixture': str(fixture_path.relative_to(ROOT)),
                'fixtureSha256': hashlib.sha256(fixture_path.read_bytes()).hexdigest(),
                'jarSha256': hashlib.sha256((ROOT / 'app/build/libs/app-0.0.1-SNAPSHOT.jar').read_bytes()).hexdigest(),
                'sourceHashes': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_paths if p.is_file()},
                'baseHead': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                'workingTreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True)),
                'k6Version': subprocess.check_output(['k6', 'version'], text=True).strip()}
    with RunArtifacts(fixture_path.parent, args.label, metadata) as run:
        (run.path / 'metrics-before.txt').write_text(before)
        env = os.environ.copy()
        env.update(BASE_URL=args.base_url, FIXTURE_FILE=str(fixture_path), READ_MODE=args.mode, CACHE_PHASE=args.phase,
                   ITERATIONS=str(args.iterations), VUS=str(args.vus), SUMMARY_PATH=str(run.path / 'summary.json'))
        with (run.path / 'k6.log').open('x') as output:
            result = subprocess.run(['k6', 'run', '--out', 'json=' + str(run.path / 'metrics.json'),
                                     str(ROOT / 'load-test/k6/catalog-read.js')], cwd=ROOT, env=env,
                                    stdout=output, stderr=subprocess.STDOUT, timeout=90)
        (run.path / 'metrics-after.txt').write_text(metrics(args.base_url))
        summary = json.loads((run.path / 'summary.json').read_text())
        valid = (result.returncode == 0 and metric(summary, 'catalog_requests', 'count') == args.iterations
                 and metric(summary, 'catalog_unexpected', 'count') == 0 and metric(summary, 'checks', 'rate') == 1)
        run.manifest['status'] = 'PASS' if valid else 'FAIL'
        if not valid:
            raise RuntimeError('Catalog workload failed; original evidence is preserved')
        print(run.path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--base-url', default='http://localhost:8081')
    parser.add_argument('--cache-enabled', type=lambda value: {'true': True, 'false': False}[value], required=True)
    parser.add_argument('--mode', choices=['detail', 'list'], required=True)
    parser.add_argument('--phase', choices=['cold', 'steady'], required=True)
    parser.add_argument('--iterations', type=int, default=300)
    parser.add_argument('--vus', type=int, default=5)
    execute(parser.parse_args())

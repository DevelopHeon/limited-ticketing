#!/usr/bin/env python3
"""Run one observable direct/waiting experiment and retain its evidence."""
import argparse
from contextlib import ExitStack
import json
import os
import signal
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

from baseline_environment import Environment, ROOT, Sampler
from baseline_results import RunArtifacts, evaluate_results, result_exit_code, utc_now, write_json, observations_complete

SCENARIOS = {
    'smoke': {'members': 10, 'inventory': 3, 'rate': 5, 'duration': 2, 'preVus': 20, 'maxVus': 100},
    'baseline': {'members': 1000, 'inventory': 100, 'rate': 100, 'duration': 10, 'preVus': 200, 'maxVus': 1000},
    'observe-5000': {'members': 5000, 'inventory': 500, 'rate': 500, 'duration': 10, 'preVus': 500, 'maxVus': 2000},
}


def drain(environment, fixture, deadline_seconds=120):
    deadline = time.monotonic() + deadline_seconds
    while True:
        snapshot = environment.verify(fixture)
        active = sum(snapshot['queue'].get(key, 0) for key in ('WAITING', 'PROCESSING'))
        if not active or time.monotonic() >= deadline:
            return snapshot
        time.sleep(2)


def prepare(environment, run, name, count, inventory):
    fixture = environment.prepare_fixture(run.manifest['runId'] + '-' + name, count, inventory)
    write_json(run.path / f'{name}.json', fixture)
    # shortcut: key-preparation failure retains the saved fixture; add owned cleanup if local failures accumulate.
    try:
        if environment.ensure_fixture_target(fixture).get('status') != 'OPEN':
            raise RuntimeError('Prepared fixture is not OPEN at the target API')
        environment.issue_keys(fixture)
    finally:
        write_json(run.path / f'{name}.json', fixture)
    return fixture


def warmup(environment, run, flow):
    fixture = prepare(environment, run, 'warmup', 20, 20)
    endpoint = 'purchases' if flow == 'direct' else 'queue-entries'
    try:
        for member in fixture['members']:
            status, body = environment.http(f"/api/sale-products/{fixture['saleProductId']}/{endpoint}",
                                            member=member['memberId'], method='POST',
                                            headers={'Idempotency-Key': member['purchaseRequestKey']})
            if status != (201 if flow == 'direct' else 202) or not body or not body.get('data'):
                raise RuntimeError('Warmup request failed')
        snapshot = drain(environment, fixture)
        write_json(run.path / 'warmup-verification.json', snapshot)
        if snapshot['purchases'] != {'PENDING_PAYMENT': 20} or snapshot['inventory'] != {
                'available': 0, 'reserved': 20, 'sold': 0, 'total': 20}:
            raise RuntimeError('Warmup did not preserve expected reservations')
    finally:
        snapshot = environment.verify(fixture)
        write_json(run.path / 'warmup-cleanup.json', environment.cleanup(fixture, snapshot))


def run_k6(environment, run, fixture, settings, flow):
    variables = {'BASE_URL': environment.base_url, 'FLOW': flow, 'RATE': str(settings['rate']),
                 'DURATION_SECONDS': str(settings['duration']), 'PRE_ALLOCATED_VUS': str(settings['preVus']),
                 'MAX_VUS': str(settings['maxVus']), 'FIXTURE_FILE': str(run.path / 'fixture.json'),
                 'SUMMARY_PATH': str(run.path / 'summary.json')}
    args = ['k6', 'run']
    for key, value in variables.items():
        args.extend(['-e', f'{key}={value}'])
    args.append(str(ROOT / 'load-test/k6/flash-sale-baseline.js'))
    with (run.path / 'k6.stdout.log').open('w') as stdout, (run.path / 'k6.stderr.log').open('w') as stderr:
        process = subprocess.Popen(args, cwd=ROOT, stdout=stdout, stderr=stderr)
        try:
            return process.wait(timeout=settings['duration'] + 60)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


def execute(args):
    settings = SCENARIOS[args.scenario]
    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + args.flow + '-' + uuid.uuid4().hex[:8]
    output_root = Path(args.output_root).resolve()
    metadata = {'flow': args.flow, 'scenario': args.scenario, 'input': settings,
                'httpTimeoutSeconds': 10, 'drainDeadlineSeconds': 120, 'warmupScrapeWaitSeconds': 5}
    with RunArtifacts(output_root, run_id, metadata) as run, ExitStack() as resources:
        print(f'Run artifacts: {run.path}', flush=True)
        environment = None
        fixture = None
        sampler = None
        start = None
        process_exit = None
        snapshot = None
        interrupted = False
        try:
            environment = Environment(args.base_url, os.environ.get('PROMETHEUS_URL', 'http://localhost:9090'),
                                      os.environ.get('COMPOSE_PROJECT_NAME', 'limited-ticketing'))
            resources.enter_context(environment.exclusive_run())
            environment.preflight()
            run.manifest['environment'] = environment.metadata()
            write_json(run.path / 'manifest.json', run.manifest)
            warmup(environment, run, args.flow)
            environment.ensure_idle()
            # Let warmup counters be scraped before the measured flow increments them.
            time.sleep(5)
            fixture = prepare(environment, run, 'fixture', settings['members'], settings['inventory'])
            environment.ensure_idle()
            sampler = Sampler(environment, fixture['saleProductId'], run.path / 'samples.csv')
            sampler.start()
            start = time.time()
            run.manifest['measurementStartedAt'] = utc_now()
            process_exit = run_k6(environment, run, fixture, settings, args.flow)
            run.manifest['measurementFinishedAt'] = utc_now()
            snapshot = drain(environment, fixture)
            run.manifest['drainFinishedAt'] = utc_now()
        except (Exception, KeyboardInterrupt) as error:
            interrupted = True
            run.manifest['error'] = {'kind': type(error).__name__, 'message': str(error) if isinstance(error, (RuntimeError, ValueError, TimeoutError)) else 'External operation interrupted or failed'}
        finally:
            # Collect independent database evidence even when k6 exits nonzero or is interrupted.
            if environment and fixture:
                try:
                    snapshot = snapshot or environment.verify(fixture)
                    write_json(run.path / 'verification.json', snapshot)
                except Exception as error:
                    run.manifest['verificationError'] = type(error).__name__
            if start:
                # Allow the final five-second scrape to include the last HTTP outcomes.
                if not interrupted:
                    time.sleep(5)
                end = time.time()
                run.manifest['observationRange'] = {'start': start - 5, 'measurementStart': start, 'end': end, 'scrapeGraceSeconds': 5}
                if sampler:
                    sampler.stop()
                observations = environment.prometheus(start - 5, end)
                write_json(run.path / 'prometheus.json', observations)
            else:
                observations = {}
            summary_path = run.path / 'summary.json'
            try:
                summary = json.loads(summary_path.read_text()) if summary_path.exists() else {}
            except ValueError:
                summary = {}
            observed = bool(sampler) and observations_complete(observations, sampler.errors, environment.metrics_target, args.flow)
            verdicts = evaluate_results(args.flow, settings['members'], settings['inventory'], summary, snapshot, observed)
            if interrupted or process_exit not in (0, 99):
                verdicts['workload'] = 'INCOMPLETE'
            run.manifest.update(verdicts=verdicts, k6ExitCode=process_exit, samplerErrors=sampler.errors if sampler else None)
            exit_code = result_exit_code(verdicts)
            if environment and fixture and snapshot:
                try:
                    run.manifest['cleanup'] = environment.cleanup(fixture, snapshot)
                    if run.manifest['cleanup']['status'] != 'PASS':
                        exit_code = 2
                except Exception as error:
                    run.manifest['cleanup'] = {'status': 'INCOMPLETE', 'error': type(error).__name__}
                    exit_code = 2
            run.manifest.update(exitCode=exit_code, status='PASS' if exit_code == 0 else 'FAIL' if exit_code == 1 else 'INCOMPLETE')
            print(json.dumps({'runId': run_id, 'verdicts': verdicts, 'exitCode': exit_code}, ensure_ascii=False), flush=True)
    return exit_code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--flow', choices=('direct', 'waiting'), required=True)
    parser.add_argument('--scenario', choices=SCENARIOS, required=True)
    parser.add_argument('--base-url', default=os.environ.get('BASE_URL', 'http://localhost:8081'))
    parser.add_argument('--output-root', default=str(ROOT / 'load-test/k6/results'))
    args = parser.parse_args()
    def interrupt(signum, frame):
        raise KeyboardInterrupt()
    signal.signal(signal.SIGTERM, interrupt)
    return execute(args)


if __name__ == '__main__':
    sys.exit(main())

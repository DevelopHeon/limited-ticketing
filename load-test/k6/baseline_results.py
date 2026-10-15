"""Evaluation contracts and durable run artifacts; no external service access."""
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, data):
    temporary = Path(str(path) + '.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    os.replace(temporary, path)


class RunArtifacts:
    def __init__(self, output_root, run_id, metadata):
        self.path = Path(output_root) / run_id
        self.path.mkdir(parents=True, exist_ok=False)
        self.manifest = {'schemaVersion': 1, 'runId': run_id, 'startedAt': utc_now(),
                         'status': 'INCOMPLETE', **metadata}
        write_json(self.path / 'manifest.json', self.manifest)

    def __enter__(self):
        return self

    def __exit__(self, kind, error, traceback):
        if error is not None:
            self.manifest['status'] = 'INCOMPLETE'
            self.manifest['error'] = type(error).__name__
        self.manifest['finishedAt'] = utc_now()
        self.manifest['artifacts'] = {
            str(path.relative_to(self.path)): {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                                            'bytes': path.stat().st_size}
            for path in sorted(self.path.rglob('*'))
            if path.is_file() and path.name not in ('manifest.json', 'manifest.json.tmp')
        }
        write_json(self.path / 'manifest.json', self.manifest)
        return False


def metric(summary, name, field):
    entry = summary.get('metrics', {}).get(name)
    if not isinstance(entry, dict):
        return None
    return entry.get('values', entry).get(field)


def evaluate_results(flow, expected, inventory, summary, verification, observations):
    result = {'workload': 'PASS', 'integrity': 'PASS', 'observability': 'PASS' if observations else 'INCOMPLETE',
              'latency': 'INCOMPLETE', 'reasons': []}
    required = [('iterations', 'count'), ('dropped_iterations', 'count'), ('checks', 'rate'),
                ('baseline_unexpected', 'count'), ('baseline_reserved', 'count'),
                ('baseline_sold_out', 'count'), ('baseline_accepted', 'count')]
    values = {name: metric(summary, name, field) for name, field in required}
    if any(value is None for value in values.values()):
        result['workload'] = 'INCOMPLETE'
        result['reasons'].append('Missing workload summary')
    elif (values['iterations'] != expected or values['dropped_iterations'] != 0
          or values['checks'] != 1 or values['baseline_unexpected'] != 0
          or (flow == 'waiting' and values['baseline_accepted'] != expected)
          or (flow == 'direct' and (values['baseline_reserved'] != inventory
                                   or values['baseline_sold_out'] != expected - inventory))):
        result['workload'] = 'FAIL'
        result['reasons'].append('Workload, payload, drop or outcome count mismatch')
    if not verification or not verification.get('inventory'):
        result['integrity'] = 'INCOMPLETE'
        result['reasons'].append('Missing final database verification')
    else:
        queue = verification.get('queue', {})
        stock = verification['inventory']
        purchases = verification.get('purchases', {})
        valid = (purchases.get('PENDING_PAYMENT', 0) == inventory and sum(purchases.values()) == inventory
                 and stock == {'available': 0, 'reserved': inventory, 'sold': 0, 'total': inventory}
                 and all(verification.get(field) == 0 for field in
                         ('mismatchedConnections', 'duplicateConnections', 'duplicatePurchases', 'foreignMembers')))
        if flow == 'waiting':
            valid = valid and (queue.get('RESERVED', 0) == inventory
                               and queue.get('SOLD_OUT', 0) == expected - inventory
                               and sum(queue.values()) == expected)
        else:
            valid = valid and not queue
        redis = verification.get('redis', {})
        if queue.get('WAITING', 0) + queue.get('PROCESSING', 0) > 0:
            result['integrity'] = 'INCOMPLETE'
            result['reasons'].append('Requests remain active after drain deadline')
        elif verification.get('redisError') or 'redis' not in verification:
            result['integrity'] = 'INCOMPLETE'
            result['reasons'].append('Redis verification unavailable; database evidence retained')
        elif not valid or redis != {'waiting': 0, 'processing': 0}:
            result['integrity'] = 'FAIL'
            result['reasons'].append('Final inventory, purchase or queue relationship mismatch')
    p95 = metric(summary, 'baseline_request_duration', 'p(95)')
    p99 = metric(summary, 'baseline_request_duration', 'p(99)')
    checks = {'httpP95': 'INCOMPLETE' if p95 is None or p99 is None else 'PASS' if p95 < 500 else 'FAIL'}
    if flow == 'waiting':
        completion = (verification or {}).get('completionSeconds', {})
        completed_p95 = completion.get('p95')
        checks['completionP95'] = ('INCOMPLETE' if completed_p95 is None or completion.get('count') != expected
                                   else 'PASS' if completed_p95 < 60 else 'FAIL')
    result['latencyChecks'] = checks
    result['latency'] = ('INCOMPLETE' if 'INCOMPLETE' in checks.values()
                         else 'FAIL' if 'FAIL' in checks.values() else 'PASS')
    return result


def result_exit_code(verdicts):
    statuses = [verdicts[key] for key in ('workload', 'integrity', 'observability', 'latency')]
    if 'INCOMPLETE' in statuses:
        return 2
    return 1 if 'FAIL' in statuses else 0


def observations_complete(observations, sample_errors, expected_target, flow):
    required = ('up', 'httpCount', 'httpBuckets', 'heap', 'threads', 'hikari')
    if sample_errors or any(data.get('status') != 'success' for data in observations.values()):
        return False
    endpoint = 'purchases' if flow == 'direct' else 'queue-entries'
    uri = '/api/sale-products/{saleProductId}/' + endpoint
    for name in required:
        rows = [row for row in observations.get(name, {}).get('data', {}).get('result', [])
                if row.get('metric', {}).get('instance') == expected_target
                and row.get('metric', {}).get('job') == 'flash-sale']
        if name in ('httpCount', 'httpBuckets'):
            rows = [row for row in rows if row['metric'].get('uri') == uri
                    and row['metric'].get('method') == 'POST']
        values = [value for row in rows for value in row.get('values', [])]
        if not values or (name == 'up' and any(float(value[1]) != 1 for value in values)):
            return False
        if name == 'httpCount' and not any(len(row.get('values', [])) >= 2
                and float(row['values'][-1][1]) > float(row['values'][0][1]) for row in rows):
            return False
    return True

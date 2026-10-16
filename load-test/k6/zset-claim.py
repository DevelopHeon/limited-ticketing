#!/usr/bin/env python3
"""Measure the production claim Lua on owned lab keys, never business queues."""
import argparse
import csv
import hashlib
import json
import math
import subprocess
import uuid
from datetime import datetime, timezone
from pathlib import Path

from baseline_results import RunArtifacts, write_json

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'purchasewaiting/src/main/resources/redis/queue-claim.lua'


def redis(container, *args):
    result = subprocess.run(['docker', 'exec', container, 'redis-cli', '--json', *map(str, args)],
                            check=True, capture_output=True, text=True, timeout=15)
    return json.loads(result.stdout)


def eval_stats(container):
    raw = subprocess.check_output(['docker', 'exec', container, 'redis-cli', '--raw', 'INFO', 'commandstats'], text=True)
    line = next((line for line in raw.splitlines() if line.startswith('cmdstat_eval:')), None)
    if line is None:
        return 0, 0
    fields = dict(item.split('=', 1) for item in line.split(':', 1)[1].split(','))
    return int(fields['calls']), int(fields['usec'])


def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)]


def measure(container, keys, size, batch, samples, source):
    populate = """for i=1,tonumber(ARGV[1]) do
redis.call('ZADD',KEYS[1],i,string.format('00000000-0000-0000-0000-%012x',i)) end
redis.call('EXPIRE',KEYS[1],600) return redis.call('ZCARD',KEYS[1])"""
    reset = """redis.call('DEL',KEYS[2],KEYS[3])
for i=1,#ARGV,2 do redis.call('ZADD',KEYS[1],ARGV[i+1],ARGV[i]) end
return redis.call('ZCARD',KEYS[1])"""
    rows = []
    try:
        assert redis(container, 'EVAL', populate, 1, keys[0], size) == size
        memory = redis(container, 'MEMORY', 'USAGE', keys[0])
        encoding = redis(container, 'OBJECT', 'ENCODING', keys[0])
        for index in range(samples + 5):
            original = redis(container, 'ZRANGE', keys[0], 0, batch - 1, 'WITHSCORES')
            before_calls, before_us = eval_stats(container)
            claimed = redis(container, 'EVAL', source, 3, *keys, batch, batch, 0, uuid.uuid4().hex)
            after_calls, after_us = eval_stats(container)
            assert after_calls - before_calls == 1, 'Concurrent EVAL invalidated this sample'
            duration = after_us - before_us
            assert len(claimed) // 2 == redis(container, 'ZCARD', keys[1]) == min(size, batch), 'Claim count mismatch'
            # redis-cli JSON WITHSCORES returns pairs; keep score restoration outside the timed command.
            flattened = [value for pair in original for value in pair]
            assert redis(container, 'EVAL', reset, 3, *keys, *flattened) == size, 'Lab reset lost a waiting member'
            if index >= 5:
                rows.append({'size': size, 'batch': batch, 'sample': index - 4, 'serverUs': duration})
        durations = [row['serverUs'] for row in rows]
        return rows, {'size': size, 'batch': batch, 'samples': samples, 'encoding': encoding,
                      'waitingMemoryBytes': memory, 'p50Us': percentile(durations, .5),
                      'p95Us': percentile(durations, .95), 'maxUs': max(durations)}
    finally:
        redis(container, 'DEL', *keys)
        assert redis(container, 'EXISTS', *keys) == 0, 'Owned lab keys were not removed'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--redis-container', required=True)
    parser.add_argument('--samples', type=int, default=20)
    args = parser.parse_args()
    if args.samples < 5:
        parser.error('--samples must be at least 5')
    source = SOURCE.read_text()
    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-zset-' + uuid.uuid4().hex[:8]
    keys = [f'lab:{run_id}:{kind}' for kind in ('waiting', 'processing', 'tokens')]
    metadata = {'kind': 'zset-claim', 'luaSha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                'source': str(SOURCE.relative_to(ROOT)), 'warmupSamples': 5,
                'redisVersion': subprocess.check_output(['docker', 'exec', args.redis_container, 'redis-server', '--version'], text=True).strip(),
                'gitRevision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                'workingTreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
                'note': 'INFO commandstats EVAL delta around production claim only; rejects concurrent EVAL; warm cache; excludes HTTP/DB/network'}
    with RunArtifacts(ROOT / 'load-test/k6/results', run_id, metadata) as run:
        print(run.path, flush=True)
        summaries = []
        with (run.path / 'samples.csv').open('w', newline='') as output:
            writer = csv.DictWriter(output, fieldnames=['size', 'batch', 'sample', 'serverUs'])
            writer.writeheader()
            for size in (1000, 5000, 10000):
                for batch in (1, 10, 50, 100):
                    rows, summary = measure(args.redis_container, keys, size, batch, args.samples, source)
                    writer.writerows(rows)
                    summaries.append(summary)
        write_json(run.path / 'summary.json', summaries)
        run.manifest['status'] = 'COMPLETE'
        run.manifest['cleanup'] = 'PASS'


if __name__ == '__main__':
    main()

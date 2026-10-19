"""External connections, resource observations and fixture lifecycle for local experiments."""
import csv
import fcntl
import hashlib
import tempfile
from contextlib import contextmanager
import json
import os
import platform
import shutil
import subprocess
import threading
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from baseline_results import utc_now

ROOT = Path(__file__).resolve().parents[2]
WORKER_DEFAULTS = {
    'QUEUE_MAX_OUTSTANDING': '10000', 'QUEUE_GLOBAL_CONCURRENT_PROCESSING': '10', 'DB_POOL_MAX_SIZE': '30',
    'QUEUE_MAX_CONCURRENT_PROCESSING': '10', 'QUEUE_CLAIM_BATCH_SIZE': '10',
    'QUEUE_WORKER_FIXED_DELAY': '100', 'QUEUE_WORKER_INITIAL_DELAY': '1000',
    'QUEUE_RECOVERY_FIXED_DELAY': '5000', 'QUEUE_RECOVERY_INITIAL_DELAY': '5000',
    'QUEUE_RECOVERY_BATCH_SIZE': '1000', 'QUEUE_PROCESSING_TIMEOUT_SECONDS': '30',
}


class Environment:
    def __init__(self, base_url, prometheus_url, compose_project):
        self.base_url = base_url.rstrip('/')
        self.prometheus_url = prometheus_url.rstrip('/')
        target = urllib.parse.urlsplit(self.base_url)
        host = 'host.docker.internal' if target.hostname in ('localhost', '127.0.0.1', '::1') else target.hostname
        self.metrics_target = f'{host}:{target.port or (443 if target.scheme == "https" else 80)}'
        self.compose = ['docker', 'compose', '-p', compose_project]
        if not os.environ.get('DATABASE_URL'):
            raise ValueError('DATABASE_URL is required for the target local database')
        parsed = urllib.parse.urlsplit(os.environ['DATABASE_URL'])
        if parsed.scheme not in ('postgres', 'postgresql') or not parsed.hostname or not parsed.path.strip('/'):
            raise ValueError('DATABASE_URL must be a PostgreSQL URL with a database name')
        # Keep passwords out of process arguments and logs.
        self.db_env = os.environ.copy()
        self.db_env.update(PGHOST=parsed.hostname, PGPORT=str(parsed.port or 5432),
                           PGDATABASE=parsed.path.strip('/'), PGUSER=urllib.parse.unquote(parsed.username or ''),
                           PGPASSWORD=urllib.parse.unquote(parsed.password or ''), PGCONNECT_TIMEOUT='5')
        self.db_env.pop('DATABASE_URL', None)

    @contextmanager
    def exclusive_run(self):
        host = self.db_env['PGHOST']
        if host in ('localhost', '127.0.0.1', '::1'):
            host = 'loopback'
        identity = ':'.join([host, self.db_env['PGPORT'], self.db_env['PGDATABASE']])
        name = hashlib.sha256(identity.encode()).hexdigest()[:24]
        path = Path(tempfile.gettempdir()) / f'flash-sale-baseline-{name}.lock'
        with path.open('a') as stream:
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise RuntimeError('Another baseline is already running against this database') from None
            try:
                yield
            finally:
                fcntl.flock(stream, fcntl.LOCK_UN)

    def command(self, args, timeout=20, env=None, input_text=None):
        try:
            result = subprocess.run(args, cwd=ROOT, env=env, capture_output=True, text=True, timeout=timeout, input=input_text)
        except subprocess.TimeoutExpired:
            raise TimeoutError(f'{Path(args[0]).name} command timed out') from None
        if result.returncode:
            # External stderr may include a connection string; retain only a safe failure category.
            raise RuntimeError(f'{Path(args[0]).name} exited with code {result.returncode}')
        return result.stdout.strip()

    def sql(self, query=None, file=None, variables=None):
        # Use stdin: a 5,000-member fixture exceeds macOS's per-argument limit.
        lines = []
        for name, value in (variables or {}).items():
            escaped = str(value).replace("\\", "\\\\").replace("'", "\\'")
            lines.append(f"\\set {name} '{escaped}'")
        lines.append(Path(file).read_text() if file else query)
        return self.command(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1'],
                            env=self.db_env, input_text='\n'.join(lines))

    def http(self, path, member=None, admin=False, body=None, method='GET', headers=None):
        request_headers = dict(headers or {})
        if member is not None:
            request_headers['X-Member-Id'] = str(member)
        if admin:
            request_headers['X-Admin-Id'] = 'admin'
        payload = None
        if body is not None:
            payload = json.dumps(body).encode()
            request_headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(self.base_url + path, data=payload, headers=request_headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                data = json.loads(raw)
            except ValueError:
                data = None
            return error.code, data

    def redis_count(self, sale_id, kind):
        return int(self.command([*self.compose, 'exec', '-T', 'redis', 'redis-cli', 'ZCARD', f'{kind}:sale:{sale_id}']))

    def verify(self, fixture):
        data = json.loads(self.sql(file=ROOT / 'load-test/k6/sql/verify-flash-sale.sql',
                                   variables={'fixture': json.dumps(fixture)}))
        try:
            data['redis'] = {key: self.redis_count(fixture['saleProductId'], key) for key in ('waiting', 'processing')}
        except Exception as error:
            data['redisError'] = type(error).__name__
        data['recordedAt'] = utc_now()
        return data

    def prepare_fixture(self, run_id, count, inventory):
        fixture = json.loads(self.sql(file=ROOT / 'load-test/k6/sql/prepare-flash-sale.sql', variables={
            'run_id': run_id, 'member_count': count, 'inventory_quantity': inventory,
        }))
        return fixture

    def issue_keys(self, fixture):
        for member in fixture['members']:
            status, data = self.http('/api/idempotency-keys', member=member['memberId'],
                                     method='POST', body={'operation': 'PURCHASE'})
            if status != 200 or not isinstance(data, dict) or not data.get('data', {}).get('idempotencyKey'):
                raise RuntimeError('Idempotency key preparation failed')
            member['purchaseRequestKey'] = data['data']['idempotencyKey']
        return fixture

    def ensure_fixture_target(self, fixture):
        status, body = self.http(f"/api/sale-products/{fixture['saleProductId']}")
        data = body.get('data', {}) if isinstance(body, dict) else {}
        if (status != 200 or data.get('saleProductId') != fixture['saleProductId']
                or data.get('productName') != 'baseline-' + fixture['runId']):
            raise RuntimeError('API and database fixture do not identify the same experiment')
        return data

    def ensure_idle(self):
        counts = self.sql(query="""SELECT
            (SELECT COUNT(*) FROM reservation_queue_entries WHERE status IN ('WAITING','DISPATCHED','PROCESSING')) +
            (SELECT COUNT(*) FROM purchases WHERE status = 'PENDING_PAYMENT')""")
        if int(counts) != 0:
            raise RuntimeError('Target database has active waiting entries or pending purchases; resolve their owning requests first')

    def cleanup(self, fixture, snapshot):
        if snapshot.get('redisError'):
            return {'status': 'SKIPPED', 'reason': 'Redis evidence unavailable; fixture retained for investigation'}
        if sum(snapshot.get('queue', {}).get(key, 0) for key in ('WAITING', 'DISPATCHED', 'PROCESSING')):
            return {'status': 'SKIPPED', 'reason': 'Active requests retained for investigation'}
        allowed = {item['memberId'] for item in fixture['members']}
        if any(purchase['memberId'] not in allowed for purchase in snapshot.get('pendingPurchases', [])):
            raise RuntimeError('Cleanup rejected a purchase outside the fixture')
        target = self.ensure_fixture_target(fixture)
        for purchase in snapshot.get('pendingPurchases', []):
            status, _ = self.http(f"/api/purchases/{purchase['purchaseId']}/cancel", member=purchase['memberId'], method='POST')
            if status != 204:
                raise RuntimeError('Fixture purchase cleanup failed')
        if target.get('status') != 'CLOSED':
            status, _ = self.http(f"/api/admin/sale-products/{fixture['saleProductId']}/close", admin=True, method='POST')
            if status != 200:
                raise RuntimeError('Fixture sale cleanup failed')
        return {'status': 'PASS', 'finishedAt': utc_now()}

    def preflight(self):
        for tool in ('psql', 'k6', 'docker', 'java'):
            if not shutil.which(tool):
                raise RuntimeError(f'Required tool missing: {tool}')
        self.sql(query='SELECT 1')
        if self.command([*self.compose, 'exec', '-T', 'redis', 'redis-cli', 'PING']) != 'PONG':
            raise RuntimeError('Redis preflight failed')
        status, health = self.http('/actuator/health')
        if status != 200 or health.get('status') != 'UP':
            raise RuntimeError('Application health is not UP')
        self.ensure_idle()

    def metadata(self):
        raw = self.command([*self.compose, 'ps', '--all', '--format', 'json'])
        try:
            containers = json.loads(raw)
        except json.JSONDecodeError:
            containers = [json.loads(line) for line in raw.splitlines() if line]
        if isinstance(containers, dict):
            containers = [containers]
        ids = [row['ID'] for row in containers]
        inspected = json.loads(self.command(['docker', 'inspect', *ids])) if ids else []
        resources = []
        for row in inspected:
            image = json.loads(self.command(['docker', 'image', 'inspect', row['Image']]))[0]
            resources.append({'name': row['Name'], 'image': row['Config']['Image'], 'imageId': row['Image'],
                              'digests': image.get('RepoDigests', []),
                              'memoryLimit': row['HostConfig']['Memory'], 'nanoCpus': row['HostConfig']['NanoCpus']})
        java = subprocess.run(['java', '-version'], capture_output=True, text=True).stderr.splitlines()[0]
        return {'gitRevision': self.command(['git', 'rev-parse', 'HEAD']),
                'workingTreeDirty': bool(self.command(['git', 'status', '--porcelain'])),
                'tools': {'k6': self.command(['k6', 'version']), 'python': platform.python_version(),
                          'psql': self.command(['psql', '--version']), 'java': java},
                'host': {'os': platform.platform(), 'architecture': platform.machine(), 'cpuCount': os.cpu_count(),
                         'memoryBytes': int(self.command(['sysctl', '-n', 'hw.memsize'])) if platform.system() == 'Darwin' else os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES')},
                'docker': json.loads(self.command(['docker', 'info', '--format', '{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}'])),
                'containers': resources,
                'worker': {key: os.environ.get(key, value) for key, value in WORKER_DEFAULTS.items()},
                'workerConfigSource': 'operator launch environment and repository defaults; not read from a remote config endpoint',
                'declaredJvmOptions': os.environ.get('BASELINE_JVM_OPTIONS', 'UNRECORDED'),
                'appProfile': os.environ.get('SPRING_PROFILES_ACTIVE', 'local'), 'composeProject': self.compose[-1],
                'baseUrl': self.base_url, 'prometheusUrl': self.prometheus_url,
                'expectedMetricsTarget': self.metrics_target}

    def prometheus(self, start, end):
        queries = {'up': 'up{job="flash-sale"}',
                   'httpCount': 'http_server_requests_seconds_count{job="flash-sale"}',
                   'httpBuckets': 'http_server_requests_seconds_bucket{job="flash-sale"}',
                   'heap': 'jvm_memory_used_bytes{job="flash-sale",area="heap"}',
                   'gc': 'jvm_gc_pause_seconds_sum{job="flash-sale"}',
                   'threads': 'jvm_threads_live_threads{job="flash-sale"}',
                   'executor': '{__name__=~"executor_(active_threads|queued_tasks|pool_size_threads|completed_tasks_total)",job="flash-sale",name="reservation.queue"}',
                   'hikari': '{__name__=~"hikaricp_connections_(active|pending|timeout_total)",job="flash-sale"}'}
        results = {}
        for name, query in queries.items():
            params = urllib.parse.urlencode({'query': query, 'start': start, 'end': end, 'step': 5})
            try:
                with urllib.request.urlopen(self.prometheus_url + '/api/v1/query_range?' + params, timeout=10) as response:
                    results[name] = json.load(response)
            except (OSError, ValueError):
                results[name] = {'status': 'error'}
        return results


class Sampler:
    def __init__(self, environment, sale_id, path):
        self.environment, self.sale_id, self.path = environment, sale_id, path
        self.errors = 0
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self.collect, daemon=True)

    def collect(self):
        with self.path.open('w', newline='', encoding='utf-8') as stream:
            writer = csv.writer(stream)
            writer.writerow(['recordedAt', 'waiting', 'processing', 'dbLockWaiters', 'error'])
            while not self.stop_event.is_set():
                row = [utc_now(), '', '', '', '']
                try:
                    row[1] = self.environment.redis_count(self.sale_id, 'waiting')
                    row[2] = self.environment.redis_count(self.sale_id, 'processing')
                    row[3] = int(self.environment.sql(query="SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'"))
                except Exception as error:
                    row[4] = type(error).__name__
                    self.errors += 1
                writer.writerow(row)
                stream.flush()
                self.stop_event.wait(1)

    def start(self):
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        self.thread.join(timeout=45)
        if self.thread.is_alive():
            self.errors += 1

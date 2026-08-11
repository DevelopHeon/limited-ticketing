import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8081';
const saleProductId = __ENV.SALE_PRODUCT_ID;
const rate = positiveNumber('RATE', 100);
const durationSeconds = positiveNumber('DURATION_SECONDS', 10);
const expectedIterations = rate * durationSeconds;
const scenarioDurationMilliseconds = durationSeconds * 1000 - 1;
const preAllocatedVus = positiveNumber('PRE_ALLOCATED_VUS', Math.min(expectedIterations, 200));
const maxVus = positiveNumber('MAX_VUS', Math.max(preAllocatedVus, 1000));
const p95ThresholdMs = positiveNumber('P95_THRESHOLD_MS', 1000);
const p99ThresholdMs = positiveNumber('P99_THRESHOLD_MS', 2000);
const verifyIdempotency = (__ENV.VERIFY_IDEMPOTENCY || 'false').toLowerCase() === 'true';
const memberIds = loadMemberIds();

if (!saleProductId) {
  throw new Error('SALE_PRODUCT_ID is required');
}
if (memberIds.length < expectedIterations) {
  throw new Error(`At least ${expectedIterations} distinct member IDs are required, but ${memberIds.length} were provided`);
}

export const idempotencyKeyDuration = new Trend('idempotency_key_duration');
export const enqueueDuration = new Trend('waiting_room_enqueue_duration');
export const acceptedEntries = new Counter('waiting_room_accepted_entries');
export const idempotentRetries = new Counter('waiting_room_idempotent_retries');
export const keyFailures = new Counter('waiting_room_key_failures');
export const unexpectedResponses = new Counter('waiting_room_unexpected_responses');
export const acceptedRate = new Rate('waiting_room_accepted_rate');

http.setResponseCallback(http.expectedStatuses(200, 202));

export const options = {
  scenarios: {
    waiting_room_enqueue: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: `${scenarioDurationMilliseconds}ms`,
      preAllocatedVUs: preAllocatedVus,
      maxVUs: maxVus,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    waiting_room_enqueue_duration: [`p(95)<${p95ThresholdMs}`, `p(99)<${p99ThresholdMs}`],
    waiting_room_accepted_rate: ['rate==1'],
    waiting_room_key_failures: ['count==0'],
    waiting_room_unexpected_responses: ['count==0'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const iterationIndex = Number(exec.scenario.iterationInTest);
  if (iterationIndex >= expectedIterations) {
    return;
  }

  const memberId = memberIds[iterationIndex];
  const keyResponse = http.post(
    `${baseUrl}/api/idempotency-keys`,
    JSON.stringify({ operation: 'PURCHASE' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Member-Id': memberId,
      },
      tags: { operation: 'issue_idempotency_key' },
    },
  );
  idempotencyKeyDuration.add(keyResponse.timings.duration);

  if (keyResponse.status !== 200) {
    keyFailures.add(1);
    acceptedRate.add(0);
    return;
  }

  const idempotencyKey = keyResponse.json('data.idempotencyKey');
  const enqueueResponse = enqueue(memberId, idempotencyKey);
  enqueueDuration.add(enqueueResponse.timings.duration);

  const accepted = enqueueResponse.status === 202;
  acceptedRate.add(accepted);
  if (accepted) {
    acceptedEntries.add(1);
  } else {
    unexpectedResponses.add(1);
  }

  check(enqueueResponse, {
    'queue entry is accepted': (response) => response.status === 202,
    'queue entry id is returned': (response) => Boolean(response.json('data.queueEntryId')),
  });

  if (accepted && verifyIdempotency) {
    const retriedResponse = enqueue(memberId, idempotencyKey);
    const sameEntryId = retriedResponse.status === 202
      && retriedResponse.json('data.queueEntryId') === enqueueResponse.json('data.queueEntryId');

    if (sameEntryId) {
      idempotentRetries.add(1);
    } else {
      unexpectedResponses.add(1);
      acceptedRate.add(0);
    }

    check(retriedResponse, {
      'same request key returns the same queue entry': () => sameEntryId,
    });
  }
}

function enqueue(memberId, idempotencyKey) {
  return http.post(
    `${baseUrl}/api/sale-products/${saleProductId}/queue-entries`,
    null,
    {
      headers: {
        'X-Member-Id': memberId,
        'Idempotency-Key': idempotencyKey,
      },
      tags: { operation: 'enqueue_waiting_room' },
    },
  );
}

function loadMemberIds() {
  if (__ENV.MEMBER_IDS_FILE) {
    const ids = JSON.parse(open(__ENV.MEMBER_IDS_FILE));
    if (!Array.isArray(ids)) {
      throw new Error('MEMBER_IDS_FILE must contain a JSON array');
    }
    return ids.map(String);
  }

  return (__ENV.MEMBER_IDS || '')
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
}

function positiveNumber(name, defaultValue) {
  const value = Number(__ENV[name] || defaultValue);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

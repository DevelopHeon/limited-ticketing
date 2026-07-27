import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const saleProductId = __ENV.SALE_PRODUCT_ID;
const memberIds = (__ENV.MEMBER_IDS || '')
  .split(',')
  .map((value) => value.trim())
  .filter((value) => value.length > 0);
const vus = Number(__ENV.VUS || memberIds.length);

if (!saleProductId) {
  throw new Error('SALE_PRODUCT_ID is required');
}
if (memberIds.length < vus) {
  throw new Error('MEMBER_IDS must contain at least VUS distinct member IDs');
}

export const reservationDuration = new Trend('reservation_duration');
export const reservationCreatedDuration = new Trend('reservation_created_duration');
export const reservationConflictDuration = new Trend('reservation_conflict_duration');
export const reservationSuccesses = new Counter('reservation_successes');
export const soldOutResponses = new Counter('sold_out_responses');
export const unexpectedResponses = new Counter('unexpected_responses');
export const expectedResponseRate = new Rate('expected_response_rate');

http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  scenarios: {
    sale_open: {
      executor: 'per-vu-iterations',
      vus,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    reservation_duration: ['p(95)<1000'],
    unexpected_responses: ['count==0'],
    expected_response_rate: ['rate==1'],
  },
};

export function setup() {
  const idempotencyKeys = {};

  for (const memberId of memberIds.slice(0, vus)) {
    const response = http.post(
      `${baseUrl}/api/idempotency-keys`,
      JSON.stringify({ operation: 'PURCHASE' }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Member-Id': memberId,
        },
      },
    );

    if (response.status !== 200) {
      throw new Error(`Failed to issue an idempotency key for member ${memberId}: ${response.status}`);
    }

    idempotencyKeys[memberId] = response.json('data.idempotencyKey');
  }

  return { idempotencyKeys };
}

export default function (data) {
  const memberId = memberIds[__VU - 1];
  const response = http.post(
    `${baseUrl}/api/sale-products/${saleProductId}/purchases`,
    null,
    {
      headers: {
        'X-Member-Id': memberId,
        'Idempotency-Key': data.idempotencyKeys[memberId],
      },
    },
  );

  reservationDuration.add(response.timings.duration);

  if (response.status === 201) {
    reservationSuccesses.add(1);
    reservationCreatedDuration.add(response.timings.duration);
    expectedResponseRate.add(1);
  } else if (response.status === 409) {
    soldOutResponses.add(1);
    reservationConflictDuration.add(response.timings.duration);
    expectedResponseRate.add(1);
  } else {
    unexpectedResponses.add(1);
    expectedResponseRate.add(0);
  }

  check(response, {
    'reservation result is created or conflict': (res) => res.status === 201 || res.status === 409,
  });
}

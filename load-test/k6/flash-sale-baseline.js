import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { classifyResponse, validateFixture } from './lib/baseline-contract.mjs';

const flow = __ENV.FLOW;
const rate = Number(__ENV.RATE);
const duration = Number(__ENV.DURATION_SECONDS);
if (!['direct', 'waiting'].includes(flow) || !Number.isInteger(rate) || rate <= 0
    || !Number.isInteger(duration) || duration <= 0) throw new Error('Valid FLOW, RATE and DURATION_SECONDS are required');
const expected = rate * duration;
const fixture = JSON.parse(open(__ENV.FIXTURE_FILE));
validateFixture(fixture, expected);
const endpoint = flow === 'direct' ? 'purchases' : 'queue-entries';
const counters = {
  reserved: new Counter('baseline_reserved'),
  sold_out: new Counter('baseline_sold_out'),
  accepted: new Counter('baseline_accepted'),
  unexpected: new Counter('baseline_unexpected'),
};
const requestDuration = new Trend('baseline_request_duration', true);
const reservedDuration = new Trend('baseline_reserved_duration', true);
const soldOutDuration = new Trend('baseline_sold_out_duration', true);
http.setResponseCallback(http.expectedStatuses(...(flow === 'direct' ? [201, 409] : [202])));
export const options = {
  scenarios: { baseline: {
    executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration: `${duration * 1000 - 500 / rate}ms`,
    preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS), maxVUs: Number(__ENV.MAX_VUS), gracefulStop: '30s',
  } },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: { checks: ['rate==1'], baseline_unexpected: ['count==0'], dropped_iterations: ['count==0'] },
};
export default function () {
  const index = Number(exec.scenario.iterationInTest);
  if (index >= expected) return;
  const member = fixture.members[index];
  const response = http.post(`${__ENV.BASE_URL}/api/sale-products/${fixture.saleProductId}/${endpoint}`, null, {
    headers: { 'X-Member-Id': String(member.memberId), 'Idempotency-Key': member.purchaseRequestKey },
    timeout: '10s', tags: { name: `/api/sale-products/{saleProductId}/${endpoint}`, flow },
  });
  let body = null;
  try { body = response.json(); } catch (_) { /* A malformed response is an unexpected business result. */ }
  const outcome = classifyResponse(flow, response.status, body);
  for (const [name, counter] of Object.entries(counters)) counter.add(outcome === name ? 1 : 0);
  requestDuration.add(response.timings.duration);
  if (outcome === 'reserved') reservedDuration.add(response.timings.duration);
  if (outcome === 'sold_out') soldOutDuration.add(response.timings.duration);
  check(response, { '업무 응답 상태와 본문이 유효하다': () => outcome !== 'unexpected' });
}
export function handleSummary(data) {
  return { [__ENV.SUMMARY_PATH]: JSON.stringify(data, null, 2) };
}

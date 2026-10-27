import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { validateCatalogFixture, detailTarget, validCatalogResponse } from './lib/catalog-read-contract.mjs';

const fixture = JSON.parse(open(__ENV.FIXTURE_FILE));
validateCatalogFixture(fixture);
const mode = __ENV.READ_MODE;
const cold = __ENV.CACHE_PHASE === 'cold';
const iterations = Number(__ENV.ITERATIONS);
const vus = Number(__ENV.VUS);
if (!['detail', 'list'].includes(mode) || !['cold', 'steady'].includes(__ENV.CACHE_PHASE)
    || !Number.isSafeInteger(iterations) || iterations <= 0 || !Number.isSafeInteger(vus) || vus <= 0 || vus > 50
    || (cold && (vus !== 1 || (mode === 'detail' && iterations > fixture.saleProductIds.length)))) {
  throw new Error('Valid READ_MODE, CACHE_PHASE, ITERATIONS and VUS (1..50) are required; cold requires one VU');
}
const durations = new Trend('catalog_request_duration', true);
const requests = new Counter('catalog_requests');
const unexpected = new Counter('catalog_unexpected');
const listPath = page => `/api/sale-products?status=OPEN&page=${page}&size=20&sort=salePrice,asc`;
export const options = {
  scenarios: { catalog: { executor: 'shared-iterations', vus, iterations, maxDuration: '60s', gracefulStop: '10s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: { checks: ['rate==1'], catalog_unexpected: ['count==0'], catalog_requests: [`count==${iterations}`] },
};

function read(path, expectedId, measured) {
  const response = http.get(__ENV.BASE_URL + path, { timeout: '10s', tags: { name: `catalog-${mode}`, phase: measured ? __ENV.CACHE_PHASE : 'prewarm' } });
  let body = null;
  try { body = response.json(); } catch (_) { /* Invalid JSON fails the response contract. */ }
  const valid = validCatalogResponse(expectedId === null ? 'list' : 'detail', expectedId, response.status, body, fixture.runId);
  check(response, { '상품 조회 상태와 실험 대상이 일치한다': () => valid });
  if (measured) {
    requests.add(1);
    unexpected.add(valid ? 0 : 1);
    durations.add(response.timings.duration);
  }
  if (!valid) throw new Error('Catalog response contract failed');
}

export function setup() {
  if (!cold) {
    for (const id of fixture.saleProductIds) read(`/api/sale-products/${id}`, id, false);
    if (mode === 'list') read(listPath(0), null, false);
  } else if (mode === 'list') {
    const id = fixture.saleProductIds[0];
    read(`/api/sale-products/${id}`, id, false);
  }
}

export default function () {
  const index = Number(exec.scenario.iterationInTest);
  if (mode === 'detail') {
    const id = detailTarget(fixture.saleProductIds, index, cold);
    read(`/api/sale-products/${id}`, id, true);
  } else {
    read(listPath(cold ? index : 0), null, true);
  }
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY_PATH]: JSON.stringify(data, null, 2) };
}

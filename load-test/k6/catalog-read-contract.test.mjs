import test from 'node:test';
import assert from 'node:assert/strict';
import { validateCatalogFixture, detailTarget, validCatalogResponse } from './lib/catalog-read-contract.mjs';

const fixture = { runId: 'test', productCount: 100, saleProductIds: Array.from({ length: 100 }, (_, i) => i + 1) };
test('서로 다른 판매 100개의 fixture만 실험 대상으로 사용한다', () => {
  validateCatalogFixture(fixture);
  assert.throws(() => validateCatalogFixture({ ...fixture, saleProductIds: Array(100).fill(1) }));
});
test('반복 상세 조회의 80%는 인기 상품 10개에 배분하고 나머지도 재현 가능하게 배분한다', () => {
  const ids = Array.from({ length: 1000 }, (_, i) => detailTarget(fixture.saleProductIds, i, false));
  assert.equal(ids.filter(id => id <= 10).length, 800);
  assert.equal(new Set(ids).size, 100);
});
test('cold 상세 조회는 같은 판매를 반복하거나 데이터 범위를 벗어나지 않는다', () => {
  assert.deepEqual(fixture.saleProductIds.map((_, i) => detailTarget(fixture.saleProductIds, i, true)), fixture.saleProductIds);
  assert.throws(() => detailTarget(fixture.saleProductIds, 100, true));
});
test('다른 DB의 같은 ID나 오류 응답을 정상 상세 조회로 판정하지 않는다', () => {
  const body = { code: 'SUCCESS', data: { saleProductId: 1, productName: 'catalog-test-1', status: 'OPEN' } };
  assert.equal(validCatalogResponse('detail', 1, 200, body, 'test'), true);
  assert.equal(validCatalogResponse('detail', 2, 200, body, 'test'), false);
  assert.equal(validCatalogResponse('detail', 1, 200, body, 'other'), false);
  assert.equal(validCatalogResponse('detail', 1, 500, body, 'test'), false);
});

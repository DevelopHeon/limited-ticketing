import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyResponse, validateFixture } from './lib/baseline-contract.mjs';

test('재고 부족 코드인 409만 정상 품절로 분류한다', () => {
  assert.equal(classifyResponse('direct', 409, { code: 'INSUFFICIENT_INVENTORY' }), 'sold_out');
  assert.equal(classifyResponse('direct', 409, { code: 'ACTIVE_PURCHASE_EXISTS' }), 'unexpected');
  assert.equal(classifyResponse('direct', 503, { code: 'INSUFFICIENT_INVENTORY' }), 'unexpected');
});
test('201이어도 구매 식별자와 결제 대기 상태가 없으면 실패로 분류한다', () => {
  assert.equal(classifyResponse('direct', 201, { data: { purchaseId: 3, status: 'PENDING_PAYMENT' } }), 'reserved');
  assert.equal(classifyResponse('direct', 201, { data: { status: 'PENDING_PAYMENT' } }), 'unexpected');
  assert.equal(classifyResponse('direct', 201, { data: { purchaseId: 3, status: 'PAID' } }), 'unexpected');
});
test('대기 수락 응답의 식별자와 상태를 검증하고 업무 실패를 숨기지 않는다', () => {
  const id = 'b2c0d8e9-c818-4d5e-b2cf-3d25cd2a97fa';
  assert.equal(classifyResponse('waiting', 202, { data: { queueEntryId: id, status: 'WAITING' } }), 'accepted');
  assert.equal(classifyResponse('waiting', 202, { data: { queueEntryId: id, status: 'RESERVED' } }), 'accepted');
  assert.equal(classifyResponse('waiting', 202, { data: { queueEntryId: id, status: 'FAILED' } }), 'unexpected');
  assert.equal(classifyResponse('waiting', 202, { data: { queueEntryId: 'invalid', status: 'WAITING' } }), 'unexpected');
  assert.equal(classifyResponse('waiting', 202, null), 'unexpected');
});
test('중복 회원이나 요청 키로 유입 조건이 왜곡되는 fixture를 거부한다', () => {
  const fixture = { saleProductId: 1, initialInventory: 1, members: [
    { memberId: 1, purchaseRequestKey: 'a' }, { memberId: 2, purchaseRequestKey: 'b' },
  ] };
  assert.doesNotThrow(() => validateFixture(fixture, 2));
  assert.throws(() => validateFixture({ ...fixture, members: [fixture.members[0], fixture.members[0]] }, 2));
  assert.throws(() => validateFixture({ ...fixture, members: [fixture.members[0], { memberId: 2, purchaseRequestKey: 'a' }] }, 2));
  assert.throws(() => validateFixture(fixture, 3));
});

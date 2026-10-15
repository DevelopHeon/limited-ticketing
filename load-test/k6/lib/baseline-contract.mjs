export function classifyResponse(flow, status, body) {
  const data = body?.data;
  if (flow === 'direct') {
    if (status === 201 && Number.isSafeInteger(data?.purchaseId) && data.purchaseId > 0
        && data.status === 'PENDING_PAYMENT') return 'reserved';
    if (status === 409 && body?.code === 'INSUFFICIENT_INVENTORY') return 'sold_out';
  } else if (flow === 'waiting' && status === 202
      && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data?.queueEntryId ?? '')
      && ['WAITING', 'PROCESSING', 'RESERVED', 'SOLD_OUT'].includes(data?.status)) {
    return 'accepted';
  }
  return 'unexpected';
}

export function validateFixture(fixture, expectedCount) {
  const members = fixture?.members;
  if (!Number.isSafeInteger(fixture?.saleProductId) || fixture.saleProductId <= 0
      || !Number.isSafeInteger(fixture?.initialInventory) || fixture.initialInventory <= 0
      || !Array.isArray(members) || members.length !== expectedCount) {
    throw new Error('Fixture must match the planned workload');
  }
  const ids = new Set();
  const keys = new Set();
  for (const member of members) {
    if (!Number.isSafeInteger(member.memberId) || member.memberId <= 0
        || typeof member.purchaseRequestKey !== 'string' || !member.purchaseRequestKey.trim()
        || ids.has(member.memberId) || keys.has(member.purchaseRequestKey)) {
      throw new Error('Fixture members and request keys must be valid and distinct');
    }
    ids.add(member.memberId);
    keys.add(member.purchaseRequestKey);
  }
}

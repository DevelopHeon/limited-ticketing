export function validateCatalogFixture(fixture) {
  const ids = fixture?.saleProductIds;
  if (typeof fixture?.runId !== 'string' || !fixture.runId || !Array.isArray(ids) || ids.length < 20
      || fixture.productCount !== ids.length || new Set(ids).size !== ids.length
      || ids.some(id => !Number.isSafeInteger(id) || id <= 0)) {
    throw new Error('A unique catalog fixture with at least 20 sale products is required');
  }
}

export function detailTarget(ids, index, cold) {
  if (!Number.isSafeInteger(index) || index < 0 || (cold && index >= ids.length)) {
    throw new Error('Invalid or repeated cold request index');
  }
  if (cold) return ids[index];
  const hotCount = Math.min(10, ids.length - 1);
  const slot = index % 10;
  return slot < 8 ? ids[(Math.floor(index / 10) * 8 + slot) % hotCount]
    : ids[hotCount + (Math.floor(index / 10) * 2 + slot - 8) % (ids.length - hotCount)];
}

export function validCatalogResponse(mode, requestedId, status, body, runId) {
  const data = body?.data;
  if (status !== 200 || body?.code !== 'SUCCESS' || !data) return false;
  if (mode === 'detail') {
    return data.saleProductId === requestedId && typeof data.productName === 'string'
      && data.productName.startsWith(`catalog-${runId}-`) && data.status === 'OPEN';
  }
  return Array.isArray(data.items) && data.items.length <= 20 && Number.isSafeInteger(data.totalCount)
    && data.totalCount >= 0 && data.items.every(item => Number.isSafeInteger(item.saleProductId) && item.status === 'OPEN');
}

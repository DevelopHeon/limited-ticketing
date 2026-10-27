-- psql -v fixture='<fixture JSON>'
WITH fixture_ids AS (
    SELECT value::BIGINT AS id FROM JSONB_ARRAY_ELEMENTS_TEXT(:'fixture'::JSONB -> 'saleProductIds')
)
SELECT JSON_BUILD_OBJECT(
    'catalogCount', (SELECT COUNT(*) FROM sale_products),
    'openCount', (SELECT COUNT(*) FROM sale_products WHERE status = 'OPEN'),
    'fixtureSaleCount', (SELECT COUNT(*) FROM sale_products WHERE id IN (SELECT id FROM fixture_ids)),
    'catalogHash', (SELECT MD5(COALESCE(JSONB_AGG(JSONB_BUILD_OBJECT(
        'sale', TO_JSONB(s), 'product', TO_JSONB(p)) ORDER BY s.id)::TEXT, '[]'))
        FROM sale_products s JOIN products p ON p.id = s.product_id),
    'purchaseCount', (SELECT COUNT(*) FROM purchases),
    'waitingCount', (SELECT COUNT(*) FROM reservation_queue_entries),
    'inventory', (SELECT JSON_BUILD_OBJECT('available', SUM(available_quantity),
        'reserved', SUM(reserved_quantity), 'sold', SUM(sold_quantity)) FROM inventories),
    'fixtureInventory', (SELECT JSON_BUILD_OBJECT('available', SUM(available_quantity),
        'reserved', SUM(reserved_quantity), 'sold', SUM(sold_quantity)) FROM inventories
        WHERE sale_product_id IN (SELECT id FROM fixture_ids))
);

-- One independently derived snapshot, scoped to this fixture's sale and members.
WITH input AS (
    SELECT :'fixture'::JSONB AS fixture
), expected AS (
    SELECT (member->>'memberId')::BIGINT AS member_id, member->>'purchaseRequestKey' AS request_key
    FROM input, JSONB_ARRAY_ELEMENTS(fixture->'members') member
), sale AS (
    SELECT (fixture->>'saleProductId')::BIGINT AS id FROM input
), purchase_rows AS (
    SELECT p.* FROM purchases p, sale WHERE p.sale_product_id = sale.id
), queue_rows AS (
    SELECT q.* FROM reservation_queue_entries q, sale WHERE q.sale_product_id = sale.id
)
SELECT JSON_BUILD_OBJECT(
    'purchases', COALESCE((SELECT JSON_OBJECT_AGG(status, n) FROM
        (SELECT status, COUNT(*) n FROM purchase_rows GROUP BY status) counts), '{}'::JSON),
    'queue', COALESCE((SELECT JSON_OBJECT_AGG(status, n) FROM
        (SELECT status, COUNT(*) n FROM queue_rows GROUP BY status) counts), '{}'::JSON),
    'inventory', (SELECT JSON_BUILD_OBJECT('available', available_quantity, 'reserved', reserved_quantity,
        'sold', sold_quantity, 'total', available_quantity + reserved_quantity + sold_quantity)
        FROM inventories i, sale WHERE i.sale_product_id = sale.id),
    'mismatchedConnections', (SELECT COUNT(*) FROM queue_rows q LEFT JOIN purchases p ON p.id = q.purchase_id
        LEFT JOIN expected e ON e.member_id = q.member_id AND e.request_key = q.purchase_request_key
        WHERE e.member_id IS NULL OR (q.status = 'RESERVED' AND
            (p.id IS NULL OR p.member_id <> q.member_id OR p.sale_product_id <> q.sale_product_id
             OR p.purchase_request_key <> q.purchase_request_key OR p.status <> 'PENDING_PAYMENT'))
            OR (q.status <> 'RESERVED' AND q.purchase_id IS NOT NULL)),
    'duplicateConnections', (SELECT COUNT(*) FROM
        (SELECT purchase_id FROM queue_rows WHERE purchase_id IS NOT NULL GROUP BY purchase_id HAVING COUNT(*) > 1) d),
    'duplicatePurchases', (SELECT COUNT(*) FROM
        (SELECT member_id FROM purchase_rows GROUP BY member_id HAVING COUNT(*) > 1) d),
    'foreignMembers', (SELECT COUNT(*) FROM purchase_rows p LEFT JOIN expected e
        ON p.member_id = e.member_id AND p.purchase_request_key = e.request_key WHERE e.member_id IS NULL),
    'completionSeconds', (SELECT JSON_BUILD_OBJECT('count', COUNT(*),
        'p50', PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM completed_at - created_at)),
        'p95', PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM completed_at - created_at)),
        'p99', PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM completed_at - created_at)))
        FROM queue_rows WHERE completed_at IS NOT NULL),
    'pendingPurchases', COALESCE((SELECT JSON_AGG(JSON_BUILD_OBJECT('purchaseId', id, 'memberId', member_id))
        FROM purchase_rows WHERE status = 'PENDING_PAYMENT'), '[]'::JSON)
);

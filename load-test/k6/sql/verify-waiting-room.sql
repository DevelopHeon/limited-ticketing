\if :{?sale_product_id}
\else
    \echo 'sale_product_id is required'
    \quit
\endif

SELECT status, COUNT(*) AS entry_count
FROM reservation_queue_entries
WHERE sale_product_id = :sale_product_id
GROUP BY status
ORDER BY status;

SELECT status, COUNT(*) AS purchase_count
FROM purchases
WHERE sale_product_id = :sale_product_id
GROUP BY status
ORDER BY status;

SELECT
    available_quantity,
    reserved_quantity,
    sold_quantity,
    available_quantity + reserved_quantity + sold_quantity AS total_quantity
FROM inventories
WHERE sale_product_id = :sale_product_id;

SELECT
    COUNT(*) FILTER (WHERE status IN ('WAITING', 'DISPATCHED', 'PROCESSING')) AS active_entries,
    COUNT(*) FILTER (WHERE status IN ('RESERVED', 'SOLD_OUT', 'FAILED')) AS completed_entries,
    COUNT(*) AS total_entries
FROM reservation_queue_entries
WHERE sale_product_id = :sale_product_id;

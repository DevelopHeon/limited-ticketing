-- psql -v run_id=... -v product_count=100
WITH params AS MATERIALIZED (
    SELECT :'run_id'::TEXT AS run_id, :'product_count'::INTEGER AS product_count
), products_created AS (
    INSERT INTO products (name, benefit_description, active)
    SELECT 'catalog-' || run_id || '-' || n, 'Catalog cache fixture', TRUE
    FROM params CROSS JOIN LATERAL GENERATE_SERIES(1, product_count) n
    RETURNING id
), sales_created AS (
    INSERT INTO sale_products (product_id, sale_price, sale_starts_at, sale_ends_at, purchase_limit_per_member, status)
    SELECT id, 10000, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 minute',
           (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') + INTERVAL '2 hours', 1, 'OPEN'
    FROM products_created RETURNING id
), inventories_created AS (
    INSERT INTO inventories (sale_product_id, available_quantity, reserved_quantity, sold_quantity)
    SELECT id, 100, 0, 0 FROM sales_created RETURNING sale_product_id
)
SELECT JSON_BUILD_OBJECT('runId', params.run_id, 'productCount', params.product_count,
    'saleProductIds', (SELECT JSON_AGG(sale_product_id ORDER BY sale_product_id) FROM inventories_created))
FROM params;

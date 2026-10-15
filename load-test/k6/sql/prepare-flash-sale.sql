-- psql -v run_id=... -v member_count=... -v inventory_quantity=...
WITH params AS MATERIALIZED (
    SELECT :'run_id'::TEXT AS run_id,
           :'member_count'::INTEGER AS member_count,
           :'inventory_quantity'::BIGINT AS inventory_quantity
), inserted_members AS (
    INSERT INTO members (external_id, name, status)
    SELECT 'baseline-' || params.run_id || '-' || n, 'baseline member ' || n, 'ACTIVE'
    FROM params CROSS JOIN LATERAL GENERATE_SERIES(1, params.member_count) n
    RETURNING id
), inserted_product AS (
    INSERT INTO products (name, benefit_description, active)
    SELECT 'baseline-' || run_id, 'Flash sale baseline fixture', TRUE FROM params
    RETURNING id
), inserted_sale AS (
    INSERT INTO sale_products (product_id, sale_price, sale_starts_at, sale_ends_at, purchase_limit_per_member, status)
    SELECT id, 10000, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 minute',
           (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') + INTERVAL '2 hours', 1, 'OPEN'
    FROM inserted_product
    RETURNING id
), inserted_inventory AS (
    INSERT INTO inventories (sale_product_id, available_quantity, reserved_quantity, sold_quantity)
    SELECT inserted_sale.id, params.inventory_quantity, 0, 0 FROM inserted_sale CROSS JOIN params
    RETURNING sale_product_id
)
SELECT JSON_BUILD_OBJECT(
    'runId', params.run_id, 'saleProductId', inserted_inventory.sale_product_id,
    'initialInventory', params.inventory_quantity,
    'members', (SELECT JSON_AGG(JSON_BUILD_OBJECT('memberId', id) ORDER BY id) FROM inserted_members)
) FROM params CROSS JOIN inserted_inventory;

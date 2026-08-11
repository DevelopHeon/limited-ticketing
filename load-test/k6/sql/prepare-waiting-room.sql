-- Change only member_count and inventory_quantity for each test scenario.
-- Smoke: 10 / 3, Load: 1000 / 100, Stress: 5000 / 500, Spike: 10000 / 1000
WITH params AS MATERIALIZED (
    SELECT
        TO_CHAR(CLOCK_TIMESTAMP(), 'YYYYMMDDHH24MISSMS') AS test_run_id,
        CAST(1000 AS INTEGER) AS member_count,
        CAST(100 AS BIGINT) AS inventory_quantity
),
inserted_members AS (
    INSERT INTO members (external_id, name, status)
    SELECT
        'waiting-room-' || params.test_run_id || '-' || numbers.number,
        'waiting room member ' || numbers.number,
        'ACTIVE'
    FROM params
    CROSS JOIN LATERAL GENERATE_SERIES(1, params.member_count) AS numbers(number)
    ON CONFLICT (external_id) DO UPDATE
    SET name = EXCLUDED.name,
        status = 'ACTIVE'
    RETURNING id
),
inserted_product AS (
    INSERT INTO products (name, benefit_description, active)
    SELECT
        'waiting-room-' || params.test_run_id,
        'Redis ZSET waiting room load test',
        TRUE
    FROM params
    RETURNING id
),
inserted_sale_product AS (
    INSERT INTO sale_products (
        product_id,
        sale_price,
        sale_starts_at,
        sale_ends_at,
        purchase_limit_per_member,
        status
    )
    SELECT
        inserted_product.id,
        10000,
        (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 minute',
        (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul') + INTERVAL '2 hours',
        1,
        'OPEN'
    FROM inserted_product
    RETURNING id, product_id
),
inserted_inventory AS (
    INSERT INTO inventories (
        sale_product_id,
        available_quantity,
        reserved_quantity,
        sold_quantity
    )
    SELECT
        inserted_sale_product.id,
        params.inventory_quantity,
        0,
        0
    FROM inserted_sale_product
    CROSS JOIN params
    RETURNING id, sale_product_id
)
SELECT
    params.test_run_id,
    params.member_count,
    params.inventory_quantity,
    inserted_product.id AS product_id,
    inserted_sale_product.id AS sale_product_id
FROM params
CROSS JOIN inserted_product
CROSS JOIN inserted_sale_product
CROSS JOIN inserted_inventory;

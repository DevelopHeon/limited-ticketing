ALTER TABLE purchases ADD COLUMN purchase_request_key VARCHAR(100);

UPDATE purchases
SET purchase_request_key = 'legacy-' || id
WHERE purchase_request_key IS NULL;

ALTER TABLE purchases ALTER COLUMN purchase_request_key SET NOT NULL;

ALTER TABLE purchases
    ADD CONSTRAINT uk_purchase_member_request_key UNIQUE (member_id, purchase_request_key);

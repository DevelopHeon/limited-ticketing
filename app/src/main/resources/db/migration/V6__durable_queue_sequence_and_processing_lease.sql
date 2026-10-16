CREATE TABLE reservation_queue_sequences (
    sale_product_id BIGINT PRIMARY KEY REFERENCES sale_products(id),
    last_sequence BIGINT NOT NULL CHECK (last_sequence BETWEEN 1 AND 9007199254740991)
);

INSERT INTO reservation_queue_sequences (sale_product_id, last_sequence)
SELECT sale_product_id, MAX(queue_sequence)
FROM reservation_queue_entries
GROUP BY sale_product_id;

ALTER TABLE reservation_queue_entries
    ADD COLUMN processing_token UUID,
    ADD COLUMN lease_until TIMESTAMP,
    ADD CONSTRAINT chk_queue_exact_score CHECK (queue_sequence BETWEEN 1 AND 9007199254740991);

UPDATE reservation_queue_entries
SET processing_token = gen_random_uuid(), lease_until = processing_started_at + INTERVAL '30 seconds'
WHERE status = 'PROCESSING';

ALTER TABLE reservation_queue_entries
    ADD CONSTRAINT chk_queue_processing_lease CHECK (
        status <> 'PROCESSING' OR (processing_token IS NOT NULL AND lease_until IS NOT NULL)
    );

CREATE INDEX idx_queue_processing_lease
    ON reservation_queue_entries (lease_until, id) WHERE status = 'PROCESSING';
CREATE INDEX idx_queue_waiting_cursor
    ON reservation_queue_entries (sale_product_id, queue_sequence, id) WHERE status = 'WAITING';

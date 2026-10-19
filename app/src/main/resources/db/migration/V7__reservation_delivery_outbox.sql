ALTER TABLE reservation_queue_entries
    ADD COLUMN dispatched_at TIMESTAMP,
    ADD COLUMN execution_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (execution_attempts BETWEEN 0 AND 3);
UPDATE reservation_queue_entries
SET execution_attempts = 1, dispatched_at = processing_started_at WHERE status = 'PROCESSING';
DROP INDEX uk_queue_entry_active_member_sale;
CREATE UNIQUE INDEX uk_queue_entry_active_member_sale ON reservation_queue_entries (member_id, sale_product_id)
    WHERE status IN ('WAITING', 'DISPATCHED', 'PROCESSING');
CREATE INDEX idx_queue_in_flight ON reservation_queue_entries (status, sale_product_id)
    WHERE status IN ('DISPATCHED', 'PROCESSING');
ALTER TABLE reservation_queue_entries ADD CONSTRAINT chk_dispatched_delivery
    CHECK (status <> 'DISPATCHED' OR (processing_token IS NOT NULL AND dispatched_at IS NOT NULL AND lease_until IS NULL));

CREATE TABLE reservation_job_outbox (
    event_id UUID PRIMARY KEY,
    waiting_entry_id UUID NOT NULL REFERENCES reservation_queue_entries(id) ON DELETE RESTRICT,
    sale_product_id BIGINT NOT NULL REFERENCES sale_products(id) ON DELETE RESTRICT,
    processing_token UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    publish_attempts INTEGER NOT NULL DEFAULT 0 CHECK (publish_attempts >= 0),
    failure_code VARCHAR(100),
    UNIQUE (waiting_entry_id, event_type, processing_token)
);
CREATE INDEX idx_reservation_outbox_pending ON reservation_job_outbox (next_attempt_at, created_at, event_id)
    WHERE published_at IS NULL;

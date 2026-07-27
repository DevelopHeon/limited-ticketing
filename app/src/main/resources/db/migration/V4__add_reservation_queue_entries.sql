CREATE TABLE reservation_queue_entries (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL,
    sale_product_id BIGINT NOT NULL,
    purchase_request_key VARCHAR(100) NOT NULL,
    queue_sequence BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    purchase_id BIGINT,
    failure_code VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    processing_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_queue_entry_member_request_key UNIQUE (member_id, purchase_request_key)
);

CREATE UNIQUE INDEX uk_queue_entry_active_member_sale
    ON reservation_queue_entries (member_id, sale_product_id)
    WHERE status IN ('WAITING', 'PROCESSING');

CREATE INDEX idx_queue_entry_waiting_sale
    ON reservation_queue_entries (sale_product_id, queue_sequence)
    WHERE status = 'WAITING';

CREATE INDEX idx_queue_entry_processing_started_at
    ON reservation_queue_entries (processing_started_at)
    WHERE status = 'PROCESSING';

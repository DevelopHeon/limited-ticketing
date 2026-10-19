ALTER TABLE reservation_job_outbox
    ADD COLUMN claim_id UUID,
    ADD COLUMN claim_until TIMESTAMP,
    ADD CONSTRAINT chk_reservation_outbox_claim_pair
        CHECK ((claim_id IS NULL) = (claim_until IS NULL));

DROP INDEX idx_reservation_outbox_pending;
CREATE INDEX idx_reservation_outbox_pending ON reservation_job_outbox (next_attempt_at, claim_until, created_at, event_id)
    WHERE published_at IS NULL;

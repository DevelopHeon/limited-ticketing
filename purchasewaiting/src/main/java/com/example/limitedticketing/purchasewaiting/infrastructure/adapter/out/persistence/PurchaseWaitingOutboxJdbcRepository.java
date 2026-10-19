package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.record.ReservationOutboxRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseWaitingOutboxJdbcRepository {

    private static final String REQUESTED_EVENT_TYPE = "PurchaseReservationRequested";
    private final JdbcTemplate jdbcTemplate;

    public PurchaseWaitingOutboxJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertIfAbsent(ReservationOutboxRecord record) {
        jdbcTemplate.update("""
            INSERT INTO reservation_job_outbox
                (event_id, waiting_entry_id, sale_product_id, processing_token, event_type,
                 topic, message_key, payload, created_at, next_attempt_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (waiting_entry_id, event_type, processing_token) DO NOTHING
            """, record.eventId(), record.waitingEntryId(), record.saleProductId(), record.processingToken(), record.eventType(),
            record.topic(), record.messageKey(), record.payload(), record.createdAt(), record.createdAt());
    }

    public boolean containsRequest(UUID eventId, UUID entryId, Long saleProductId, UUID token) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM reservation_job_outbox
              WHERE event_id = ? AND waiting_entry_id = ? AND sale_product_id = ?
                AND processing_token = ? AND event_type = ?)
            """, Boolean.class, eventId, entryId, saleProductId, token, REQUESTED_EVENT_TYPE));
    }

    public Optional<LocalDateTime> findPublishedAt(UUID entryId, UUID token) {
        return jdbcTemplate.query("""
            SELECT published_at FROM reservation_job_outbox WHERE waiting_entry_id = ?
              AND processing_token = ? AND event_type = ? AND published_at IS NOT NULL
            """, (rs, row) -> rs.getObject("published_at", LocalDateTime.class),
            entryId, token, REQUESTED_EVENT_TYPE).stream().findFirst();
    }

    public List<ReservationOutboxRecord> claimReady(UUID claimId, LocalDateTime now, LocalDateTime claimUntil, int limit) {
        return jdbcTemplate.query("""
            WITH candidates AS (
                SELECT event_id FROM reservation_job_outbox
                WHERE published_at IS NULL AND next_attempt_at <= ?
                    AND (claim_until IS NULL OR claim_until <= ?)
                ORDER BY created_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED
            ), claimed AS (
                UPDATE reservation_job_outbox AS outbox
                SET claim_id = ?, claim_until = ?, publish_attempts = publish_attempts + 1
                FROM candidates WHERE outbox.event_id = candidates.event_id
                RETURNING outbox.*
            )
            SELECT * FROM claimed ORDER BY created_at, event_id
            """, (rs, row) -> new ReservationOutboxRecord(rs.getObject("event_id", UUID.class),
            rs.getObject("waiting_entry_id", UUID.class), rs.getLong("sale_product_id"),
            rs.getObject("processing_token", UUID.class), rs.getString("event_type"),
            rs.getString("topic"), rs.getString("message_key"), rs.getString("payload"),
            rs.getObject("created_at", LocalDateTime.class), rs.getInt("publish_attempts"), rs.getObject("claim_id", UUID.class)), now, now, limit, claimId, claimUntil);
    }

    public boolean markPublished(UUID eventId, UUID claimId, LocalDateTime now) {
        return jdbcTemplate.update("""
            UPDATE reservation_job_outbox SET published_at = ?, claim_id = NULL, claim_until = NULL, failure_code = NULL
            WHERE event_id = ? AND claim_id = ? AND claim_until > ? AND published_at IS NULL
            """, now, eventId, claimId, now) == 1;
    }

    public boolean recordFailure(UUID eventId, UUID claimId, LocalDateTime now, LocalDateTime nextAttempt, String failureCode) {
        return jdbcTemplate.update("""
            UPDATE reservation_job_outbox SET next_attempt_at = ?, failure_code = ?, claim_id = NULL, claim_until = NULL
            WHERE event_id = ? AND claim_id = ? AND claim_until > ? AND published_at IS NULL
            """, nextAttempt, failureCode, eventId, claimId, now) == 1;
    }

    public long countPending() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM reservation_job_outbox WHERE published_at IS NULL", Long.class);
    }

    public double oldestPendingSeconds(LocalDateTime now) {
        return jdbcTemplate.queryForObject("SELECT coalesce(greatest(extract(epoch FROM (?::timestamp - min(created_at))), 0), 0) FROM reservation_job_outbox WHERE published_at IS NULL", Double.class, now);
    }
}

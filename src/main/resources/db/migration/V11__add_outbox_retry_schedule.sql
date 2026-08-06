ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE outbox_events
SET next_attempt_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_pending_next_attempt
    ON outbox_events (next_attempt_at, created_at)
    WHERE status = 'PENDING';

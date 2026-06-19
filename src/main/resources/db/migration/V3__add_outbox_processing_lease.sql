ALTER TABLE outbox_events
    ADD COLUMN processing_at TIMESTAMP(6),
    ADD COLUMN processing_by VARCHAR(100);

CREATE INDEX idx_outbox_events_status_processing_at
    ON outbox_events(status, processing_at);

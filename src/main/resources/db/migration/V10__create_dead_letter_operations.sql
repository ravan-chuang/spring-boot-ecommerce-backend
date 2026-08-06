ALTER TABLE outbox_events
    ADD COLUMN correlation_id VARCHAR(128);

CREATE INDEX idx_outbox_events_correlation_id
    ON outbox_events(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE dead_letter_events (
    id UUID PRIMARY KEY,
    dlt_topic VARCHAR(255) NOT NULL,
    dlt_partition INTEGER NOT NULL,
    dlt_offset BIGINT NOT NULL,
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER,
    original_offset BIGINT,
    message_key VARCHAR(512),
    payload TEXT,
    headers_json TEXT NOT NULL,
    outbox_event_id UUID,
    correlation_id VARCHAR(128),
    exception_class VARCHAR(255),
    exception_message TEXT,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quarantined_at TIMESTAMPTZ,
    quarantined_by INTEGER,
    replay_started_at TIMESTAMPTZ,
    replay_attempts INTEGER NOT NULL DEFAULT 0,
    replayed_at TIMESTAMPTZ,
    replayed_by INTEGER,

    CONSTRAINT uk_dead_letter_events_dlt_position
        UNIQUE (dlt_topic, dlt_partition, dlt_offset),
    CONSTRAINT fk_dead_letter_events_quarantined_by
        FOREIGN KEY (quarantined_by)
        REFERENCES users(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_dead_letter_events_replayed_by
        FOREIGN KEY (replayed_by)
        REFERENCES users(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_dead_letter_events_status_received_at
    ON dead_letter_events(status, received_at DESC);

CREATE INDEX idx_dead_letter_events_correlation_id
    ON dead_letter_events(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX idx_dead_letter_events_outbox_event_id
    ON dead_letter_events(outbox_event_id)
    WHERE outbox_event_id IS NOT NULL;

CREATE TABLE dead_letter_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    dead_letter_event_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_user_id INTEGER,
    actor_email VARCHAR(255),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dead_letter_audit_event
        FOREIGN KEY (dead_letter_event_id)
        REFERENCES dead_letter_events(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_dead_letter_audit_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_dead_letter_audit_event_created_at
    ON dead_letter_audit_logs(dead_letter_event_id, created_at ASC);

CREATE INDEX idx_dead_letter_audit_actor_created_at
    ON dead_letter_audit_logs(actor_user_id, created_at DESC)
    WHERE actor_user_id IS NOT NULL;

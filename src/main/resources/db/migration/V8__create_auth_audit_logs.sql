CREATE TABLE auth_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER,
    event_type VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64),
    device_name VARCHAR(255),
    details VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_auth_audit_logs_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_auth_audit_logs_user_created_at
    ON auth_audit_logs(user_id, created_at DESC);

CREATE INDEX idx_auth_audit_logs_event_outcome_created_at
    ON auth_audit_logs(event_type, outcome, created_at DESC);

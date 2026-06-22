ALTER TABLE refresh_tokens
    ADD COLUMN session_id UUID;

UPDATE refresh_tokens
SET session_id = id
WHERE session_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN session_id SET NOT NULL;

ALTER TABLE refresh_tokens
    ADD COLUMN device_name VARCHAR(255) NOT NULL DEFAULT 'Unknown device',
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_refresh_tokens_session_id
    ON refresh_tokens(session_id);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, last_used_at DESC)
    WHERE revoked_at IS NULL;

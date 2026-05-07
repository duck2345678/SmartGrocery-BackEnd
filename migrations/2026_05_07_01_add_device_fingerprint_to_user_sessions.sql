ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_user_sessions_device_fingerprint
    ON user_sessions(device_fingerprint);
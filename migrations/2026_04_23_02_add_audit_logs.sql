CREATE TABLE IF NOT EXISTS audit_logs (
    audit_log_id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_state JSONB,
    after_state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created_at
    ON audit_logs (actor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created_at
    ON audit_logs (action_type, created_at DESC);

ALTER TABLE audit_logs
    ADD CONSTRAINT fk_audit_logs_actor
    FOREIGN KEY (actor_id) REFERENCES users(user_id);


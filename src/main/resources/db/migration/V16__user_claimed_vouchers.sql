ALTER TABLE vouchers
    ADD COLUMN IF NOT EXISTS claim_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS user_claimed_vouchers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    voucher_id BIGINT NOT NULL REFERENCES vouchers(voucher_id) ON DELETE CASCADE,
    claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NULL,
    CONSTRAINT uk_user_claimed_voucher UNIQUE (user_id, voucher_id),
    CONSTRAINT chk_user_claimed_voucher_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_user_claimed_vouchers_user_status
    ON user_claimed_vouchers(user_id, status, is_used);

CREATE INDEX IF NOT EXISTS idx_user_claimed_vouchers_voucher
    ON user_claimed_vouchers(voucher_id);

CREATE TABLE IF NOT EXISTS voucher_claim_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(40) NOT NULL,
    result VARCHAR(20) NOT NULL,
    encrypted_payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_voucher_claim_logs_action_created
    ON voucher_claim_logs(action, created_at);

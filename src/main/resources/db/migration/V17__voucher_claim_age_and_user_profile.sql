ALTER TABLE users
    ADD COLUMN IF NOT EXISTS birth_date DATE NULL;

ALTER TABLE vouchers
    ADD COLUMN IF NOT EXISTS min_age INTEGER NULL,
    ADD COLUMN IF NOT EXISTS max_age INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_vouchers_claimable_window
    ON vouchers(active, hidden, valid_from, valid_until, usage_limit, claim_count);
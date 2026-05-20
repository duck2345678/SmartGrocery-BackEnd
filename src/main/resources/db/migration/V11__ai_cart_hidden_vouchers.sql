ALTER TABLE cart_items
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS ai_list_code VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS ai_list_name VARCHAR(160);

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT c.conname
    INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE t.relname = 'cart_items'
      AND c.contype = 'u'
      AND (
        SELECT array_agg(a.attname::text ORDER BY a.attname::text)
        FROM unnest(c.conkey) AS cols(attnum)
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = cols.attnum
      ) = ARRAY['cart_id', 'variant_id']::text[]
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE cart_items DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_items_variant_source_list
    ON cart_items(cart_id, variant_id, source, ai_list_code);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_list_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS ai_list_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS reward_voucher_id BIGINT;

ALTER TABLE vouchers
    ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reveal_trigger VARCHAR(40) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS assigned_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS unlocked_by_order_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_reward_voucher') THEN
        ALTER TABLE orders
            ADD CONSTRAINT fk_orders_reward_voucher
            FOREIGN KEY (reward_voucher_id) REFERENCES vouchers(voucher_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vouchers_assigned_user') THEN
        ALTER TABLE vouchers
            ADD CONSTRAINT fk_vouchers_assigned_user
            FOREIGN KEY (assigned_user_id) REFERENCES users(user_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vouchers_unlocked_by_order') THEN
        ALTER TABLE vouchers
            ADD CONSTRAINT fk_vouchers_unlocked_by_order
            FOREIGN KEY (unlocked_by_order_id) REFERENCES orders(order_id);
    END IF;
END $$;

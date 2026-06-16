ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS order_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS route VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_order_id ON notifications(order_id);

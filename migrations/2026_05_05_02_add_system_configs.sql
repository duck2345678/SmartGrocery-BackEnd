CREATE TABLE IF NOT EXISTS system_configs (
    config_key VARCHAR(120) PRIMARY KEY,
    config_value TEXT NOT NULL,
    value_type VARCHAR(30) NOT NULL DEFAULT 'STRING',
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_configs (config_key, config_value, value_type, description, is_active)
VALUES
    ('ORDER_BLOCK_START_TIME', '22:00', 'TIME', 'Giờ bắt đầu chặn đặt đơn', TRUE),
    ('ORDER_BLOCK_END_TIME', '06:00', 'TIME', 'Giờ kết thúc chặn đặt đơn', TRUE),
    ('ORDER_ISSUE_TIMEOUT_MINUTES', '30', 'INTEGER', 'Thời gian chờ khách xử lý sự cố đơn hàng', TRUE),
    ('ORDER_ASSIGN_LEASE_MINUTES', '10', 'INTEGER', 'Thời gian lease cho đơn được assign', TRUE),
    ('ORDER_ASSIGN_RETRY_SECONDS', '15', 'INTEGER', 'Chu kỳ quét lại hàng đợi đơn', TRUE)
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    value_type = EXCLUDED.value_type,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

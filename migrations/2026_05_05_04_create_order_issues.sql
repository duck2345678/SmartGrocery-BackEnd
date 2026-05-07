CREATE TABLE IF NOT EXISTS order_issues (
    issue_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    issue_type VARCHAR(50) NOT NULL,
    issue_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_action VARCHAR(50) NULL,
    resolved_action VARCHAR(50) NULL,
    requested_quantity INT NULL,
    available_quantity INT NULL,
    note VARCHAR(500) NULL,
    evidence_url VARCHAR(500) NULL,
    requested_by BIGINT NULL,
    resolved_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_issues_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_issues_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(order_item_id) ON DELETE SET NULL,
    CONSTRAINT fk_order_issues_requested_by FOREIGN KEY (requested_by) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_order_issues_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_order_issues_order_id ON order_issues(order_id);
CREATE INDEX IF NOT EXISTS idx_order_issues_status_created_at ON order_issues(issue_status, created_at);

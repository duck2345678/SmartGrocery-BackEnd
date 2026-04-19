ALTER TABLE orders ADD COLUMN assignee_id BIGINT NULL;
ALTER TABLE orders ADD COLUMN lease_expires_at TIMESTAMP NULL;

ALTER TABLE orders
  ADD CONSTRAINT fk_orders_assignee
  FOREIGN KEY (assignee_id) REFERENCES users(user_id);


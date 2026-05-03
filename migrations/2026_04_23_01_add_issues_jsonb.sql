CREATE TABLE IF NOT EXISTS issues (
  issue_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_item_id BIGINT NULL,
  reporter_id BIGINT NOT NULL,
  issue_type VARCHAR(50) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  details JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_issues_order_id ON issues(order_id);
CREATE INDEX IF NOT EXISTS idx_issues_reporter_id ON issues(reporter_id);
CREATE INDEX IF NOT EXISTS idx_issues_created_at ON issues(created_at DESC);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_issues_order'
  ) THEN
    ALTER TABLE issues
      ADD CONSTRAINT fk_issues_order
      FOREIGN KEY (order_id) REFERENCES orders(order_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_issues_order_item'
  ) THEN
    ALTER TABLE issues
      ADD CONSTRAINT fk_issues_order_item
      FOREIGN KEY (order_item_id) REFERENCES order_items(order_item_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_issues_reporter'
  ) THEN
    ALTER TABLE issues
      ADD CONSTRAINT fk_issues_reporter
      FOREIGN KEY (reporter_id) REFERENCES users(user_id);
  END IF;
END $$;

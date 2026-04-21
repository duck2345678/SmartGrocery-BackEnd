ALTER TABLE order_items ADD COLUMN IF NOT EXISTS allow_substitution BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS picked_quantity INT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS is_substituted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS substituted_variant_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS substitution_reason VARCHAR(255);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_order_items_substituted_variant'
  ) THEN
    ALTER TABLE order_items
      ADD CONSTRAINT fk_order_items_substituted_variant
      FOREIGN KEY (substituted_variant_id) REFERENCES product_variants(variant_id);
  END IF;
END $$;

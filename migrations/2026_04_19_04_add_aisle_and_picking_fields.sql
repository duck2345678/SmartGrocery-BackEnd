ALTER TABLE product_variants ADD COLUMN aisle_location VARCHAR(20);

ALTER TABLE order_items ADD COLUMN picked_quantity INT;
ALTER TABLE order_items ADD COLUMN is_substituted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_items ADD COLUMN substituted_variant_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN substitution_reason VARCHAR(255);

ALTER TABLE order_items
  ADD CONSTRAINT fk_order_items_substituted_variant
  FOREIGN KEY (substituted_variant_id) REFERENCES product_variants(variant_id);


ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS color VARCHAR(80);
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS size VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_products_status_category
    ON products (status, category_id);

CREATE INDEX IF NOT EXISTS idx_products_name_lower
    ON products (lower(product_name));

CREATE INDEX IF NOT EXISTS idx_products_code_lower
    ON products (lower(product_code));

CREATE INDEX IF NOT EXISTS idx_product_variants_sku_lower
    ON product_variants (lower(sku));

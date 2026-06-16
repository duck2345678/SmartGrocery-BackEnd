ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS flash_sale_starts_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS flash_sale_stock_limit INTEGER,
    ADD COLUMN IF NOT EXISTS flash_sale_sold_count INTEGER NOT NULL DEFAULT 0;

UPDATE product_variants
SET flash_sale_sold_count = 0
WHERE flash_sale_sold_count IS NULL;


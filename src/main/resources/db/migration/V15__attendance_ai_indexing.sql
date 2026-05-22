-- V15__attendance_ai_indexing.sql
-- Optimizing indexes for active variants, discounted variants, and AI product searches.

-- 1. Index on product_variants status and net_price for high-performance active/discount variants retrieval
CREATE INDEX IF NOT EXISTS idx_product_variants_status_price ON product_variants(status, net_price);

-- 2. Enable pg_trgm extension for trigram matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 3. GIN index using gin_trgm_ops on products(product_name) for rapid full-text/fuzzy search by AI Chatbot
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING gin (product_name gin_trgm_ops);

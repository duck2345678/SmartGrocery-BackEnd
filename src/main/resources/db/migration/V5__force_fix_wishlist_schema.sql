-- Force recreate wishlist tables with correct column references
DROP TABLE IF EXISTS wishlist_items;
DROP TABLE IF EXISTS wishlists;

-- Add Flash Sale support to product_variants
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS flash_sale_ends_at TIMESTAMP WITHOUT TIME ZONE;

-- Create wishlists table
CREATE TABLE wishlists (
    wishlist_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Create wishlist_items table
CREATE TABLE wishlist_items (
    id BIGSERIAL PRIMARY KEY,
    wishlist_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_wishlist_item_header FOREIGN KEY (wishlist_id) REFERENCES wishlists(wishlist_id),
    CONSTRAINT fk_wishlist_item_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT uk_wishlist_product UNIQUE (wishlist_id, product_id)
);

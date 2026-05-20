CREATE TABLE meals (
    meal_id BIGSERIAL PRIMARY KEY,
    meal_name VARCHAR(200) NOT NULL,
    description TEXT,
    meal_category VARCHAR(50),
    dietary_goal VARCHAR(100),
    flavor_profile VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE meal_ingredients (
    meal_ingredient_id BIGSERIAL PRIMARY KEY,
    meal_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    role VARCHAR(20),
    quantity VARCHAR(255),
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_meal FOREIGN KEY (meal_id) REFERENCES meals(meal_id) ON DELETE CASCADE,
    CONSTRAINT fk_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);

CREATE INDEX idx_meal_category ON meals(meal_category);
CREATE INDEX idx_meal_ingredients_meal ON meal_ingredients(meal_id);

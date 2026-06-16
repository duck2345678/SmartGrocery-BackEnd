CREATE TABLE IF NOT EXISTS shopping_scenario (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS shopping_scenario_item (
    id BIGSERIAL PRIMARY KEY,
    scenario_code VARCHAR(50) NOT NULL REFERENCES shopping_scenario(code) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,
    entity_value VARCHAR(100) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    CONSTRAINT chk_shopping_scenario_item_type CHECK (entity_type IN ('CATEGORY', 'KEYWORD'))
);

CREATE INDEX IF NOT EXISTS idx_shopping_scenario_item_scenario
    ON shopping_scenario_item(scenario_code, priority);

INSERT INTO shopping_scenario (code, name, description, is_active)
VALUES
    ('PICNIC', 'Di picnic', 'Goi y cac mon co ban cho buoi di picnic hoac da ngoai.', TRUE),
    ('BREAKFAST', 'Do an sang', 'Goi y nhanh cho bua sang tai nha hoac mang di.', TRUE),
    ('BABY_CARE', 'Cham soc em be', 'Cac san pham cham soc ca nhan va do dung co ban cho em be.', TRUE),
    ('CLEANING', 'Don dep nha cua', 'Cac san pham ve sinh va cham soc nha cua.', TRUE),
    ('OFFICE_SNACK', 'An vat van phong', 'Do uong va do an vat phu hop tai van phong.', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO shopping_scenario_item (scenario_code, entity_type, entity_value, priority)
VALUES
    ('PICNIC', 'CATEGORY', 'CAT_DRINK', 10),
    ('PICNIC', 'CATEGORY', 'CAT_SNACK', 20),
    ('PICNIC', 'CATEGORY', 'CAT_FRUIT', 30),
    ('PICNIC', 'KEYWORD', 'khan giay', 40),
    ('BREAKFAST', 'CATEGORY', 'CAT_DAIRY', 10),
    ('BREAKFAST', 'KEYWORD', 'banh mi', 20),
    ('BREAKFAST', 'KEYWORD', 'ngu coc', 30),
    ('BREAKFAST', 'KEYWORD', 'ca phe', 40),
    ('BABY_CARE', 'CATEGORY', 'CAT_PERSONAL', 10),
    ('BABY_CARE', 'KEYWORD', 'ta em be', 20),
    ('BABY_CARE', 'KEYWORD', 'khan uot', 30),
    ('CLEANING', 'CATEGORY', 'CAT_HOU', 10),
    ('CLEANING', 'KEYWORD', 'nuoc lau san', 20),
    ('CLEANING', 'KEYWORD', 'nuoc rua chen', 30),
    ('OFFICE_SNACK', 'CATEGORY', 'CAT_SNACK', 10),
    ('OFFICE_SNACK', 'CATEGORY', 'CAT_DRINK', 20)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS shopping_scenario_alias (
    id BIGSERIAL PRIMARY KEY,
    scenario_code VARCHAR(50) NOT NULL REFERENCES shopping_scenario(code) ON DELETE CASCADE,
    alias_text VARCHAR(150) NOT NULL,
    normalized_alias VARCHAR(150) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shopping_scenario_alias_normalized
    ON shopping_scenario_alias(normalized_alias);

INSERT INTO shopping_scenario_alias (scenario_code, alias_text, normalized_alias)
VALUES
    ('CLEANING', 'nha do', 'nha do'),
    ('CLEANING', 'nha ban', 'nha ban'),
    ('CLEANING', 'don nha', 'don nha'),
    ('CLEANING', 've sinh nha', 've sinh nha'),
    ('PICNIC', 'di picnic', 'di picnic'),
    ('PICNIC', 'di da ngoai', 'di da ngoai'),
    ('BREAKFAST', 'do an sang', 'do an sang'),
    ('BREAKFAST', 'bua sang', 'bua sang'),
    ('BABY_CARE', 'do cho be', 'do cho be'),
    ('BABY_CARE', 'cham soc em be', 'cham soc em be'),
    ('OFFICE_SNACK', 'an vat van phong', 'an vat van phong')
ON CONFLICT DO NOTHING;

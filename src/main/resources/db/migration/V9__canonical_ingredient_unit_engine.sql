CREATE TABLE IF NOT EXISTS ingredient_canonical (
    id BIGSERIAL PRIMARY KEY,
    canonical_code VARCHAR(120) NOT NULL UNIQUE,
    canonical_name_vi VARCHAR(200) NOT NULL,
    canonical_name_en VARCHAR(200),
    ingredient_family VARCHAR(80) NOT NULL,
    default_dimension VARCHAR(20) NOT NULL,
    average_weight_per_unit_g NUMERIC(12, 4),
    average_volume_per_unit_ml NUMERIC(12, 4),
    density_g_per_ml NUMERIC(12, 6),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ingredient_dimension
        CHECK (default_dimension IN ('mass', 'volume', 'count'))
);

CREATE INDEX IF NOT EXISTS idx_ingredient_canonical_family_active
    ON ingredient_canonical (ingredient_family, is_active);

CREATE TABLE IF NOT EXISTS ingredient_alias (
    id BIGSERIAL PRIMARY KEY,
    canonical_id BIGINT NOT NULL,
    alias_text_raw VARCHAR(200) NOT NULL,
    alias_text_norm VARCHAR(200) NOT NULL,
    lang VARCHAR(12) NOT NULL DEFAULT 'vi',
    source VARCHAR(20) NOT NULL DEFAULT 'manual',
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 1.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ingredient_alias_canonical
        FOREIGN KEY (canonical_id) REFERENCES ingredient_canonical(id) ON DELETE CASCADE,
    CONSTRAINT ck_ingredient_alias_source
        CHECK (source IN ('manual', 'seed', 'llm', 'import'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ingredient_alias_norm_lang_active
    ON ingredient_alias (alias_text_norm, lang)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_ingredient_alias_canonical_active
    ON ingredient_alias (canonical_id, is_active);

CREATE TABLE IF NOT EXISTS unit_canonical (
    id BIGSERIAL PRIMARY KEY,
    unit_code VARCHAR(60) NOT NULL UNIQUE,
    dimension VARCHAR(20) NOT NULL,
    to_base_factor NUMERIC(14, 6) NOT NULL,
    base_unit_code VARCHAR(60) NOT NULL,
    is_approximate BOOLEAN NOT NULL DEFAULT FALSE,
    default_mass_g NUMERIC(12, 4),
    default_volume_ml NUMERIC(12, 4),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_unit_dimension
        CHECK (dimension IN ('mass', 'volume', 'count')),
    CONSTRAINT ck_unit_factor_positive
        CHECK (to_base_factor > 0),
    CONSTRAINT ck_unit_approx_defaults
        CHECK (
            is_approximate = FALSE
            OR default_mass_g IS NOT NULL
            OR default_volume_ml IS NOT NULL
            OR dimension = 'count'
        )
);

CREATE INDEX IF NOT EXISTS idx_unit_canonical_dimension_active
    ON unit_canonical (dimension, is_active);

CREATE TABLE IF NOT EXISTS unit_alias (
    id BIGSERIAL PRIMARY KEY,
    unit_canonical_id BIGINT NOT NULL,
    alias_text_raw VARCHAR(120) NOT NULL,
    alias_text_norm VARCHAR(120) NOT NULL,
    locale VARCHAR(12) NOT NULL DEFAULT 'vi',
    source VARCHAR(20) NOT NULL DEFAULT 'manual',
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 1.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_alias_unit_canonical
        FOREIGN KEY (unit_canonical_id) REFERENCES unit_canonical(id) ON DELETE CASCADE,
    CONSTRAINT ck_unit_alias_source
        CHECK (source IN ('manual', 'seed', 'llm', 'import'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_unit_alias_norm_locale_active
    ON unit_alias (alias_text_norm, locale)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_unit_alias_canonical_active
    ON unit_alias (unit_canonical_id, is_active);

ALTER TABLE meal_ingredients
    ADD COLUMN IF NOT EXISTS canonical_ingredient_id BIGINT,
    ADD COLUMN IF NOT EXISTS quantity_value NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS quantity_unit_raw VARCHAR(80),
    ADD COLUMN IF NOT EXISTS quantity_unit_canonical_id BIGINT,
    ADD COLUMN IF NOT EXISTS quantity_parse_status VARCHAR(20) NOT NULL DEFAULT 'UNPARSED',
    ADD COLUMN IF NOT EXISTS quantity_parse_confidence NUMERIC(5, 4);

ALTER TABLE meal_ingredients
    ADD CONSTRAINT fk_meal_ingredients_canonical_ingredient
        FOREIGN KEY (canonical_ingredient_id) REFERENCES ingredient_canonical(id) ON DELETE SET NULL;

ALTER TABLE meal_ingredients
    ADD CONSTRAINT fk_meal_ingredients_quantity_unit
        FOREIGN KEY (quantity_unit_canonical_id) REFERENCES unit_canonical(id) ON DELETE SET NULL;

ALTER TABLE meal_ingredients
    ADD CONSTRAINT ck_meal_ingredients_quantity_parse_status
        CHECK (quantity_parse_status IN ('PARSED', 'APPROX', 'FAILED', 'REVIEW', 'UNPARSED'));

CREATE TABLE IF NOT EXISTS catalog_sync_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_catalog_sync_aggregate_type
        CHECK (aggregate_type IN ('INGREDIENT_ALIAS', 'UNIT_ALIAS', 'INGREDIENT_CANONICAL', 'UNIT_CANONICAL', 'PRODUCT_INGREDIENT_MATCH')),
    CONSTRAINT ck_catalog_sync_event_type
        CHECK (event_type IN ('UPSERT', 'DELETE', 'DEACTIVATE')),
    CONSTRAINT ck_catalog_sync_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'DEAD'))
);

CREATE INDEX IF NOT EXISTS idx_catalog_sync_outbox_status_retry
    ON catalog_sync_outbox (status, next_retry_at, id);

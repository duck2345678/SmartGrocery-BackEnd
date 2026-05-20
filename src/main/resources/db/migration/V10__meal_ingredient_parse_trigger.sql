CREATE OR REPLACE FUNCTION normalize_vi_text(input_text TEXT)
RETURNS TEXT AS $$
BEGIN
    IF input_text IS NULL THEN
        RETURN '';
    END IF;
    RETURN regexp_replace(
        replace(replace(lower(trim(input_text)), 'đ', 'd'), 'Đ', 'd'),
        '[^a-z0-9\\s]',
        ' ',
        'g'
    );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION parse_quantity_value_token(token TEXT)
RETURNS NUMERIC AS $$
DECLARE
    t TEXT := lower(trim(token));
BEGIN
    IF t = '' THEN
        RETURN NULL;
    END IF;
    IF t IN ('nua', '1/2') THEN
        RETURN 0.5;
    ELSIF t = '1/3' THEN
        RETURN 0.3333;
    ELSIF t = '1/4' THEN
        RETURN 0.25;
    ELSIF t IN ('vai', 'vài') THEN
        RETURN 3;
    END IF;
    RETURN replace(t, ',', '.')::NUMERIC;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION meal_ingredients_parse_and_map()
RETURNS TRIGGER AS $$
DECLARE
    normalized_name TEXT;
    source_name TEXT;
    qty_norm TEXT;
    value_token TEXT;
    unit_token TEXT;
    parsed_value NUMERIC;
BEGIN
    source_name := COALESCE(NULLIF(NEW.generic_name, ''), (SELECT p.product_name FROM products p WHERE p.product_id = NEW.product_id));
    normalized_name := normalize_vi_text(source_name);

    IF NEW.canonical_ingredient_id IS NULL AND normalized_name <> '' THEN
        SELECT ia.canonical_id
        INTO NEW.canonical_ingredient_id
        FROM ingredient_alias ia
        WHERE ia.alias_text_norm = normalized_name
          AND ia.lang = 'vi'
          AND ia.is_active = TRUE
        LIMIT 1;
    END IF;

    IF NEW.quantity IS NULL OR btrim(NEW.quantity) = '' THEN
        NEW.quantity_parse_status := COALESCE(NEW.quantity_parse_status, 'UNPARSED');
        NEW.quantity_parse_confidence := COALESCE(NEW.quantity_parse_confidence, 0);
        RETURN NEW;
    END IF;

    qty_norm := normalize_vi_text(NEW.quantity);
    value_token := split_part(qty_norm, ' ', 1);
    unit_token := btrim(substring(qty_norm from length(value_token) + 1));
    parsed_value := parse_quantity_value_token(value_token);

    IF parsed_value IS NOT NULL THEN
        NEW.quantity_value := COALESCE(NEW.quantity_value, parsed_value);
        NEW.quantity_unit_raw := COALESCE(NULLIF(NEW.quantity_unit_raw, ''), unit_token);
        IF NEW.quantity_unit_canonical_id IS NULL AND unit_token <> '' THEN
            SELECT ua.unit_canonical_id
            INTO NEW.quantity_unit_canonical_id
            FROM unit_alias ua
            WHERE ua.alias_text_norm = unit_token
              AND ua.locale = 'vi'
              AND ua.is_active = TRUE
            LIMIT 1;
        END IF;
        IF NEW.quantity_unit_canonical_id IS NOT NULL THEN
            NEW.quantity_parse_status := 'PARSED';
            NEW.quantity_parse_confidence := COALESCE(NEW.quantity_parse_confidence, 0.85);
        ELSE
            NEW.quantity_parse_status := 'REVIEW';
            NEW.quantity_parse_confidence := COALESCE(NEW.quantity_parse_confidence, 0.45);
        END IF;
    ELSE
        NEW.quantity_parse_status := COALESCE(NEW.quantity_parse_status, 'FAILED');
        NEW.quantity_parse_confidence := COALESCE(NEW.quantity_parse_confidence, 0);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_meal_ingredients_parse_and_map ON meal_ingredients;
CREATE TRIGGER trg_meal_ingredients_parse_and_map
BEFORE INSERT OR UPDATE ON meal_ingredients
FOR EACH ROW
EXECUTE FUNCTION meal_ingredients_parse_and_map();

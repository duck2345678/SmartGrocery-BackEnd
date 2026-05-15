ALTER TABLE user_nutrition_profiles
    ADD COLUMN IF NOT EXISTS food_constraints JSONB;


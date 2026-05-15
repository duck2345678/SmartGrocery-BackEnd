ALTER TABLE user_nutrition_profiles
    ADD COLUMN IF NOT EXISTS ai_interaction_points INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS received_first_order_reward BOOLEAN NOT NULL DEFAULT FALSE;

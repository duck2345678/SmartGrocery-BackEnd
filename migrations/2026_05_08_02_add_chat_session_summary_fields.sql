ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS conversation_summary TEXT,
    ADD COLUMN IF NOT EXISTS last_summarized_message_id BIGINT;

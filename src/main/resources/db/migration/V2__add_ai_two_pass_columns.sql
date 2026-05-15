-- SQL Migration for Two-pass AI Chat Orchestration
-- Target: PostgreSQL (Supabase)

-- Add columns for Two-pass pipeline and streaming status
ALTER TABLE chat_messages
ADD COLUMN IF NOT EXISTS reply_status VARCHAR(40) DEFAULT 'DONE',
ADD COLUMN IF NOT EXISTS validated_action_snapshot JSONB,
ADD COLUMN IF NOT EXISTS fallback_reply TEXT,
ADD COLUMN IF NOT EXISTS final_reply TEXT,
ADD COLUMN IF NOT EXISTS reply_error_code VARCHAR(80),
ADD COLUMN IF NOT EXISTS reply_error_message TEXT,
ADD COLUMN IF NOT EXISTS reply_started_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS reply_completed_at TIMESTAMP;

-- Create index for status-based monitoring
CREATE INDEX IF NOT EXISTS idx_chat_messages_reply_status ON chat_messages(reply_status);

-- Commentary:
-- We use JSONB for validated_action_snapshot to allow efficient searching 
-- and analysis of what AI proposed before it was rendered into text.
-- Default status is 'DONE' to maintain compatibility with legacy records.

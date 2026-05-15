-- ==============================================================================
-- 1. BACKUP (TẠO BẢNG LƯU TRỮ TRƯỚC KHI XÓA)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS backup_chat_sessions AS SELECT * FROM chat_sessions;
CREATE TABLE IF NOT EXISTS backup_chat_messages AS SELECT * FROM chat_messages;
CREATE TABLE IF NOT EXISTS backup_chat_message_feedbacks AS SELECT * FROM chat_message_feedbacks;

-- ==============================================================================
-- 2. DROP BẢNG GỐC CHAT AI
-- ==============================================================================
DROP TABLE IF EXISTS chat_message_feedbacks CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS chat_sessions CASCADE;

-- LƯU Ý: Không xóa bảng 'users' vì đây là bảng dùng chung.
-- Các Foreign Keys trỏ đến users sẽ tự động bị xóa do CASCADE hoặc do bảng con bị xóa.

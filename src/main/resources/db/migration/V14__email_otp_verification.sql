-- V14: Email OTP Verification support
-- Thêm cột email_verified_at vào bảng users
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP NULL;

-- Mở rộng bảng otp_verifications với các cột mới cần thiết
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMP NULL;
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS last_sent_at TIMESTAMP NULL;

-- Index tối ưu cho các truy vấn OTP
CREATE INDEX IF NOT EXISTS idx_otp_email_purpose_status_expires
    ON otp_verifications(email, purpose, status, expires_at);

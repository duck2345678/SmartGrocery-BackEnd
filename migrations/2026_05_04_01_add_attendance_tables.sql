-- shift_schedules: Lịch phân ca cho nhân viên theo ngày
CREATE TABLE IF NOT EXISTS shift_schedules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    work_date DATE NOT NULL,
    shift_type VARCHAR(10) NOT NULL, -- 'S','C','G','P','F','OFF'
    selected_blocks VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, work_date)
);

-- attendance_records: Bản ghi check-in/check-out
CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    work_date DATE NOT NULL,
    shift_type VARCHAR(10) NOT NULL,
    block_number INT NOT NULL DEFAULT 1,     -- 1 cho ca thường, 1 hoặc 2 cho ca G
    check_in_at TIMESTAMP,
    check_out_at TIMESTAMP,
    check_in_status VARCHAR(20),              -- 'ON_TIME','LATE'
    check_out_status VARCHAR(20),             -- 'ON_TIME','EARLY'
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, work_date, block_number)
);

CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON attendance_records(user_id, work_date);
CREATE INDEX IF NOT EXISTS idx_shift_user_date ON shift_schedules(user_id, work_date);

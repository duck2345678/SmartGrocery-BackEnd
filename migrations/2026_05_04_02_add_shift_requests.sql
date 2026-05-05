CREATE TABLE shift_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    work_date DATE NOT NULL,
    shift_type VARCHAR(10) NOT NULL,
    selected_blocks VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    admin_note TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, work_date)
);

CREATE INDEX idx_shift_requests_user_date ON shift_requests(user_id, work_date);
CREATE INDEX idx_shift_requests_status_date ON shift_requests(status, work_date);

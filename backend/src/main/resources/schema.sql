-- =========================================================================================
-- DATABASE SCHEMA: ALGORITHM HYPERPARAMETERS & LOTTERY SYSTEM
-- Bảng lưu trữ siêu tham số mô hình học máy XGBoost kết hợp đối chuẩn quốc tế (Powerball/Mega Millions)
-- =========================================================================================

CREATE TABLE IF NOT EXISTS algorithm_hyperparameters (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    draw_date VARCHAR(50) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'POWER',
    model VARCHAR(150) NOT NULL DEFAULT 'XGBoost Multi-Factor Optimization',
    hyperparameters_json TEXT NOT NULL,
    readme_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT
);

ALTER TABLE algorithm_hyperparameters ADD COLUMN IF NOT EXISTS readme_content TEXT;

CREATE INDEX IF NOT EXISTS idx_hyperparameters_category ON algorithm_hyperparameters(category);
CREATE INDEX IF NOT EXISTS idx_hyperparameters_created_at ON algorithm_hyperparameters(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_hyperparameters_version ON algorithm_hyperparameters(version);

-- Bảng kết quả xổ số
CREATE TABLE IF NOT EXISTS lottery_numbers (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    draw_date VARCHAR(50) NOT NULL,
    numbers VARCHAR(255) NOT NULL,
    special_number INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT
);

-- Bảng vé số người dùng kiểm tra
CREATE TABLE IF NOT EXISTS user_tickets (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    draw_date VARCHAR(50) NOT NULL,
    numbers VARCHAR(255) NOT NULL,
    special_number INTEGER,
    matched_numbers VARCHAR(255),
    matched_count INTEGER DEFAULT 0,
    matched_special BOOLEAN DEFAULT FALSE,
    prize VARCHAR(100),
    prize_amount VARCHAR(100),
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT
);

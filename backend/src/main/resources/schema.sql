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

-- Bảng lưu trữ biến số (Deviation Variables) giữa số AI và kết quả thực tế
CREATE TABLE IF NOT EXISTS lottery_deviation_variables (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    base_draw_date VARCHAR(50) NOT NULL,
    target_draw_date VARCHAR(50) NOT NULL,
    ai_predicted_number INTEGER NOT NULL,
    actual_number INTEGER NOT NULL,
    variable_delta INTEGER NOT NULL,
    variable_type VARCHAR(100) NOT NULL,
    pattern_name VARCHAR(150),
    probability_shift DOUBLE PRECISION,
    transformation_rule TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_dev_vars_category ON lottery_deviation_variables(category);
CREATE INDEX IF NOT EXISTS idx_dev_vars_target_date ON lottery_deviation_variables(target_draw_date DESC);
CREATE INDEX IF NOT EXISTS idx_dev_vars_created_at ON lottery_deviation_variables(created_at DESC);


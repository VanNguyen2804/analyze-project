-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V1__init_database_schema.sql
-- Mô tả: Khởi tạo toàn bộ cấu trúc bảng, ràng buộc, chỉ mục và dữ liệu ban đầu cho hệ thống xổ số
-- Database: PostgreSQL
-- =========================================================================================

-- 1. BẢNG SIÊU THAM SỐ THUẬT TOÁN (ALGORITHM HYPERPARAMETERS)
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

CREATE INDEX IF NOT EXISTS idx_hyperparameters_category ON algorithm_hyperparameters(category);
CREATE INDEX IF NOT EXISTS idx_hyperparameters_created_at ON algorithm_hyperparameters(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_hyperparameters_version ON algorithm_hyperparameters(version);

-- 2. BẢNG KẾT QUẢ KỲ QUAY XỔ SỐ (LOTTERY NUMBERS)
CREATE TABLE IF NOT EXISTS lottery_numbers (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    draw_date DATE NOT NULL,
    special_number INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_lottery_numbers_category ON lottery_numbers(category);
CREATE INDEX IF NOT EXISTS idx_lottery_numbers_draw_date ON lottery_numbers(draw_date DESC);
CREATE INDEX IF NOT EXISTS idx_lottery_numbers_cat_date ON lottery_numbers(category, draw_date DESC);

-- 3. BẢNG DANH SÁCH BÓNG CỦA KỲ QUAY (LOTTERY SELECTED NUMBERS)
CREATE TABLE IF NOT EXISTS lottery_selected_numbers (
    lottery_id BIGINT NOT NULL,
    number_order INTEGER NOT NULL,
    number_value INTEGER NOT NULL,
    CONSTRAINT pk_lottery_selected_numbers PRIMARY KEY (lottery_id, number_order),
    CONSTRAINT fk_lottery_selected_numbers_lottery FOREIGN KEY (lottery_id) REFERENCES lottery_numbers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lottery_selected_numbers_val ON lottery_selected_numbers(number_value);

-- 4. BẢNG VÉ SỐ NGƯỜI DÙNG KIỂM TRA (USER TICKET - JPA ENTITY)
CREATE TABLE IF NOT EXISTS user_ticket (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(255),
    draw_date VARCHAR(255),
    prize VARCHAR(255),
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_ticket_numbers (
    user_ticket_id BIGINT NOT NULL,
    numbers INTEGER,
    CONSTRAINT fk_user_ticket_numbers_ticket FOREIGN KEY (user_ticket_id) REFERENCES user_ticket(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_ticket_numbers_tid ON user_ticket_numbers(user_ticket_id);

-- 5. BẢNG VÉ SỐ NGƯỜI DÙNG TƯƠNG THÍCH SCHEMA TRUY VẤN CŨ (USER TICKETS)
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

-- 6. BẢNG BÀI TẬP VÀ LẦN THỬ TIẾNG PHÁP (FRENCH EXERCISE & ATTEMPT)
CREATE TABLE IF NOT EXISTS french_exercise (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(255),
    level VARCHAR(255),
    sentence VARCHAR(255),
    translation VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS french_attempt (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT,
    expected_sentence VARCHAR(255),
    user_input VARCHAR(255),
    is_correct BOOLEAN,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. BẢNG KẾT QUẢ KỲ QUAY RỜI (LOTTERY RESULT)
CREATE TABLE IF NOT EXISTS lottery_result (
    id BIGSERIAL PRIMARY KEY,
    draw_id VARCHAR(255),
    draw_date DATE,
    num1 INTEGER,
    num2 INTEGER,
    num3 INTEGER,
    num4 INTEGER,
    num5 INTEGER,
    num6 INTEGER
);

-- =========================================================================================
-- DỮ LIỆU KHỞI TẠO BAN ĐẦU (SEED DATA)
-- =========================================================================================

-- Nạp dữ liệu 47 kỳ quay lịch sử ban đầu
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (1, '2026-08-05', 'MEGA', NULL, '2026-09-19 16:18:43', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (1, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (1, 1, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (1, 2, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (1, 3, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (1, 4, 28) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (1, 5, 39) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (2, '2026-08-07', 'MEGA', NULL, '2026-09-19 16:18:18', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (2, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (2, 1, 8) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (2, 2, 19) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (2, 3, 30) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (2, 4, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (2, 5, 43) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (3, '2026-08-09', 'MEGA', NULL, '2026-09-19 16:17:49', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (3, 0, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (3, 1, 17) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (3, 2, 20) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (3, 3, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (3, 4, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (3, 5, 35) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (4, '2026-08-12', 'MEGA', NULL, '2026-09-19 16:17:33', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (4, 0, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (4, 1, 17) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (4, 2, 22) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (4, 3, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (4, 4, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (4, 5, 40) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (5, '2026-08-14', 'MEGA', NULL, '2026-09-19 16:16:59', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (5, 0, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (5, 1, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (5, 2, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (5, 3, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (5, 4, 35) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (5, 5, 44) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (6, '2026-08-16', 'MEGA', NULL, '2026-09-19 16:16:31', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (6, 0, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (6, 1, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (6, 2, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (6, 3, 19) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (6, 4, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (6, 5, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (7, '2026-08-19', 'MEGA', NULL, '2026-09-19 16:16:05', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (7, 0, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (7, 1, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (7, 2, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (7, 3, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (7, 4, 40) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (7, 5, 43) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (8, '2026-08-20', 'POWER', NULL, '2026-09-19 07:29:40', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (8, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (8, 1, 8) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (8, 2, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (8, 3, 38) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (8, 4, 39) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (8, 5, 51) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (9, '2026-08-21', 'MEGA', NULL, '2026-09-19 16:15:49', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (9, 0, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (9, 1, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (9, 2, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (9, 3, 38) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (9, 4, 43) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (9, 5, 45) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (10, '2026-08-22', 'POWER', NULL, '2026-09-19 07:28:02', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (10, 0, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (10, 1, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (10, 2, 19) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (10, 3, 21) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (10, 4, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (10, 5, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (11, '2026-08-23', 'MEGA', NULL, '2026-09-19 16:15:24', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (11, 0, 4) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (11, 1, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (11, 2, 17) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (11, 3, 22) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (11, 4, 32) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (11, 5, 39) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (12, '2026-08-25', 'POWER', NULL, '2026-09-19 07:25:06', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (12, 0, 5) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (12, 1, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (12, 2, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (12, 3, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (12, 4, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (12, 5, 40) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (13, '2026-08-26', 'MEGA', NULL, '2026-09-19 16:14:54', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (13, 0, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (13, 1, 10) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (13, 2, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (13, 3, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (13, 4, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (13, 5, 40) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (14, '2026-08-27', 'POWER', NULL, '2026-09-19 07:24:43', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (14, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (14, 1, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (14, 2, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (14, 3, 21) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (14, 4, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (14, 5, 44) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (15, '2026-08-28', 'MEGA', NULL, '2026-09-19 16:14:28', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (15, 0, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (15, 1, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (15, 2, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (15, 3, 22) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (15, 4, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (15, 5, 39) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (16, '2026-08-29', 'POWER', NULL, '2026-09-19 07:24:17', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (16, 0, 5) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (16, 1, 10) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (16, 2, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (16, 3, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (16, 4, 34) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (16, 5, 45) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (17, '2026-08-30', 'MEGA', NULL, '2026-09-19 16:13:52', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (17, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (17, 1, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (17, 2, 12) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (17, 3, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (17, 4, 37) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (17, 5, 45) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (18, '2026-09-01', 'POWER', NULL, '2026-09-19 07:19:11', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (18, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (18, 1, 17) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (18, 2, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (18, 3, 44) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (18, 4, 49) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (18, 5, 55) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (19, '2026-09-02', 'MEGA', NULL, '2026-09-19 16:13:30', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (19, 0, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (19, 1, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (19, 2, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (19, 3, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (19, 4, 35) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (19, 5, 44) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (20, '2026-09-03', 'POWER', NULL, '2026-09-19 07:18:53', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (20, 0, 8) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (20, 1, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (20, 2, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (20, 3, 42) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (20, 4, 46) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (20, 5, 47) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (21, '2026-09-04', 'MEGA', NULL, '2026-09-19 16:12:30', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (21, 0, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (21, 1, 21) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (21, 2, 23) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (21, 3, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (21, 4, 34) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (21, 5, 45) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (22, '2026-09-05', 'POWER', NULL, '2026-09-19 07:10:40', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (22, 0, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (22, 1, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (22, 2, 24) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (22, 3, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (22, 4, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (22, 5, 47) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (23, '2026-09-06', 'MEGA', NULL, '2026-09-19 16:11:48', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (23, 0, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (23, 1, 14) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (23, 2, 22) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (23, 3, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (23, 4, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (23, 5, 40) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (24, '2026-09-08', 'POWER', NULL, '2026-09-19 07:10:07', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (24, 0, 8) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (24, 1, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (24, 2, 14) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (24, 3, 23) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (24, 4, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (24, 5, 54) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (25, '2026-09-09', 'MEGA', NULL, '2026-09-19 16:11:01', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (25, 0, 12) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (25, 1, 17) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (25, 2, 20) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (25, 3, 21) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (25, 4, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (25, 5, 43) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (26, '2026-09-10', 'POWER', NULL, '2026-09-19 07:09:42', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (26, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (26, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (26, 2, 28) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (26, 3, 32) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (26, 4, 51) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (26, 5, 53) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (27, '2026-09-11', 'MEGA', NULL, '2026-09-19 16:10:39', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (27, 0, 14) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (27, 1, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (27, 2, 20) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (27, 3, 21) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (27, 4, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (27, 5, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (28, '2026-09-12', 'POWER', NULL, '2026-09-19 07:07:50', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (28, 0, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (28, 1, 24) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (28, 2, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (28, 3, 43) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (28, 4, 47) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (28, 5, 54) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (29, '2026-09-13', 'MEGA', NULL, '2026-09-19 16:10:14', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (29, 0, 4) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (29, 1, 12) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (29, 2, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (29, 3, 34) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (29, 4, 38) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (29, 5, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (30, '2026-09-15', 'POWER', NULL, '2026-09-19 07:07:00', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (30, 0, 24) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (30, 1, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (30, 2, 32) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (30, 3, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (30, 4, 47) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (30, 5, 52) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (31, '2026-09-16', 'MEGA', NULL, '2026-09-19 16:09:50', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (31, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (31, 1, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (31, 2, 10) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (31, 3, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (31, 4, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (31, 5, 23) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (32, '2026-09-17', 'POWER', 15, '2026-09-28 15:37:36', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (32, 0, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (32, 1, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (32, 2, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (32, 3, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (32, 4, 37) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (32, 5, 45) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (33, '2026-09-18', 'MEGA', NULL, '2026-09-19 16:09:23', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (33, 0, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (33, 1, 12) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (33, 2, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (33, 3, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (33, 4, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (33, 5, 43) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (34, '2026-09-19', 'POWER', 50, '2026-09-28 15:37:10', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (34, 0, 4) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (34, 1, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (34, 2, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (34, 3, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (34, 4, 22) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (34, 5, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (35, '2026-09-20', 'MEGA', NULL, '2026-09-20 15:08:22', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (35, 0, 10) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (35, 1, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (35, 2, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (35, 3, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (35, 4, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (35, 5, 38) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (36, '2026-09-22', 'POWER', 10, '2026-09-28 15:36:43', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (36, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (36, 1, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (36, 2, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (36, 3, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (36, 4, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (36, 5, 46) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (37, '2026-09-23', 'MEGA', NULL, '2026-09-26 14:44:54', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (37, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (37, 1, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (37, 2, 12) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (37, 3, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (37, 4, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (37, 5, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (38, '2026-09-24', 'POWER', 27, '2026-09-28 15:36:18', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (38, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (38, 1, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (38, 2, 23) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (38, 3, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (38, 4, 26) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (38, 5, 28) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (39, '2026-09-25', 'MEGA', NULL, '2026-09-26 14:43:58', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (39, 0, 3) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (39, 1, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (39, 2, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (39, 3, 28) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (39, 4, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (39, 5, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (40, '2026-09-26', 'POWER', 49, '2026-09-28 15:35:09', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (40, 0, 14) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (40, 1, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (40, 2, 21) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (40, 3, 38) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (40, 4, 48) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (40, 5, 52) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (41, '2026-09-27', 'MEGA', NULL, '2026-09-27 14:33:56', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (41, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (41, 1, 4) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (41, 2, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (41, 3, 25) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (41, 4, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (41, 5, 39) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (42, '2026-09-28', 'POWER', 11, '2026-09-29 18:24:26', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (42, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (42, 1, 4) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (42, 2, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (42, 3, 17) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (42, 4, 35) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (42, 5, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (43, '2026-09-30', 'MEGA', NULL, '2026-09-30 19:19:57', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (43, 0, 2) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (43, 1, 9) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (43, 2, 24) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (43, 3, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (43, 4, 30) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (43, 5, 33) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (44, '2026-10-02', 'MEGA', NULL, '2026-10-02 17:19:05', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (44, 0, 1) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (44, 1, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (44, 2, 20) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (44, 3, 27) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (44, 4, 31) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (44, 5, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (45, '2026-10-03', 'POWER', 41, '2026-10-03 13:36:53', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (45, 0, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (45, 1, 11) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (45, 2, 13) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (45, 3, 16) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (45, 4, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (45, 5, 54) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (46, '2026-10-04', 'MEGA', NULL, '2026-10-04 13:33:58', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (46, 0, 15) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (46, 1, 20) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (46, 2, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (46, 3, 37) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (46, 4, 40) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (46, 5, 45) ON CONFLICT DO NOTHING;
INSERT INTO lottery_numbers (id, draw_date, category, special_number, created_at, note) VALUES (47, '2026-10-06', 'POWER', 1, '2026-10-06 13:30:00', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (47, 0, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (47, 1, 7) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (47, 2, 18) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (47, 3, 20) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (47, 4, 24) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (47, 5, 27) ON CONFLICT DO NOTHING;

SELECT setval('lottery_numbers_id_seq', (SELECT COALESCE(MAX(id), 1) FROM lottery_numbers));

-- Nạp siêu tham số thuật toán cơ bản v1.0.0 và v1.1.0
INSERT INTO algorithm_hyperparameters (id, version, draw_date, category, model, hyperparameters_json, readme_content, created_at, note)
VALUES (
    1,
    'v1.0.0',
    '2026-09-20',
    'ALL',
    'XGBoost Multi-Factor Optimization',
    '{"max_depth": 6, "learning_rate": 0.05, "n_estimators": 250, "subsample": 0.85, "colsample_bytree": 0.85, "objective": "binary:logistic", "eval_metric": "logloss", "features": ["frequency_last_100", "frequency_last_30", "frequency_last_10", "current_gap", "average_gap", "gap_deviation_zscore", "co_occurrence_with_hot", "co_occurrence_with_special", "parity_ratio_balance", "sum_window_drift"], "globalBenchmarking": {"usPowerballCorrelation": 0.35, "usMegaMillionsCorrelation": 0.35, "randomSingularityDampening": 0.15, "datasetDepthDraws": 1500}, "constraints": {"oddEvenAllowed": ["3/3", "4/2", "2/4"], "sumRangeFilter": [84, 144], "maxConsecutivePairsAllowed": 2}, "actionableAdvice": "Baseline XGBoost configuration."}',
    '# Thông số cơ bản v1.0.0',
    '2026-09-20 10:00:00',
    'Baseline model parameters'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO algorithm_hyperparameters (id, version, draw_date, category, model, hyperparameters_json, readme_content, created_at, note)
VALUES (
    2,
    'v1.1.0',
    '2026-09-28',
    'POWER',
    'XGBoost Multi-Factor Optimization',
    '{"max_depth": 7, "learning_rate": 0.04, "n_estimators": 320, "subsample": 0.9, "colsample_bytree": 0.8, "objective": "binary:logistic", "eval_metric": "logloss", "weights": {"co_occurrence_pair": 0.85, "repeat_exhaustion_penalty": -0.45, "special_migration_momentum": 0.38, "exponential_decay_lambda": 0.16, "gap_min_ratio": 0.8, "gap_max_ratio": 2.2}, "features": ["frequency_last_100", "frequency_last_30", "frequency_last_10", "current_gap", "average_gap", "gap_deviation_zscore", "co_occurrence_pair_affinity", "special_migration_momentum", "parity_ratio_balance", "sum_window_drift"], "globalBenchmarking": {"usPowerballCorrelation": 0.4, "usMegaMillionsCorrelation": 0.35, "randomSingularityDampening": 0.2, "datasetDepthDraws": 2000}, "constraints": {"oddEvenAllowed": ["3/3", "4/2", "2/4"], "sumRangeFilter": [77, 137], "maxConsecutivePairsAllowed": 2}, "actionableAdvice": "Cập nhật lại trọng số thuật toán XGBoost cho kỳ quay kế tiếp: Ưu tiên lọc loại trừ các số kiệt sức lặp, đẩy cao trọng số liên kết cặp đồng xuất hiện."}',
    '# Báo cáo Cập nhật Thuật toán & Đối chuẩn Toàn cầu (v1.1.0)

## 1. Bối cảnh hiệu chỉnh kỳ 2026-09-28
- Đối chiếu kết quả kỳ quay Power 6/55 ngày 2026-09-28.
- Cải tiến: Nâng trọng số liên kết cặp đồng xuất hiện lên 0.85, phạt số lặp kiệt sức -0.45.',
    '2026-09-28 18:30:00',
    'Cập nhật trọng số theo báo cáo đối chiếu vé kỳ 2026-09-28'
) ON CONFLICT (id) DO NOTHING;

SELECT setval('algorithm_hyperparameters_id_seq', (SELECT COALESCE(MAX(id), 1) FROM algorithm_hyperparameters));

-- Nạp bài tập tiếng Pháp mẫu
INSERT INTO french_exercise (id, category, level, sentence, translation) VALUES
(1, 'GREETING', 'A1', 'Bonjour, comment allez-vous ?', 'Xin chào, bạn khỏe không?'),
(2, 'GREETING', 'A1', 'Bonne journée !', 'Chúc một ngày tốt lành!'),
(3, 'FOOD', 'A1', 'Je voudrais un café, s''il vous plaît.', 'Tôi muốn một tách cà phê, làm ơn.')
ON CONFLICT (id) DO NOTHING;

SELECT setval('french_exercise_id_seq', (SELECT COALESCE(MAX(id), 1) FROM french_exercise));

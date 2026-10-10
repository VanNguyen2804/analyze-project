-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V3__create_lottery_deviation_variables_table.sql
-- Mô tả: Bảng lưu trữ biến số (Deviation Variables / Shift Matrix) giữa dãy số AI dự đoán
--        và kết quả quay thực tế, phát hiện các bước nhảy biến thiên để sinh số bù trừ
-- Database: PostgreSQL
-- =========================================================================================

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

-- Nạp các bản ghi biến số thực chứng từ kỳ Mega 07/10/2026 và kỳ Power 06/10/2026
INSERT INTO lottery_deviation_variables (
    category, base_draw_date, target_draw_date, ai_predicted_number, actual_number, variable_delta, variable_type, pattern_name, probability_shift, transformation_rule, note
) VALUES
('MEGA', '2026-10-04', '2026-10-07', 15, 14, -1, 'NEIGHBOR_DRIFT', 'Biến số Lệch Biên Sát Nút (±1)', 0.88, '15 - 1 = 14: Biến số dịch chuyển lồng cầu liền kề trái', 'Thực nghiệm kỳ Mega 07/10'),
('MEGA', '2026-10-04', '2026-10-07', 40, 41, 1, 'NEIGHBOR_DRIFT', 'Biến số Lệch Biên Sát Nút (±1)', 0.91, '40 + 1 = 41: Biến số dịch chuyển lồng cầu liền kề phải', 'Thực nghiệm kỳ Mega 07/10'),
('MEGA', '2026-10-04', '2026-10-07', 29, 36, 7, 'RESONANCE_LEAP', 'Biến số Bước Nhảy Bậc 7 (Delta=7)', 0.76, '29 + 7 = 36: Bước nhảy dao động Fourier điều hòa', 'Thực nghiệm kỳ Mega 07/10'),
('MEGA', '2026-10-04', '2026-10-07', 37, 37, 0, 'REPEAT_INERTIA', 'Biến số Quán Tính Lặp Nguyên Vị (Delta=0)', 0.95, '37 + 0 = 37: Quán tính lặp chuỗi Markov trạng thái tĩnh', 'Thực nghiệm kỳ Mega 07/10'),
('MEGA', '2026-10-04', '2026-10-07', 45, 43, -2, 'NEIGHBOR_DRIFT', 'Biến số Lệch Dải Chẵn Lẻ (Delta=-2)', 0.82, '45 - 2 = 43: Dịch chuyển bậc 2 bảo toàn tính lẻ', 'Thực nghiệm kỳ Mega 07/10'),
('MEGA', '2026-10-04', '2026-10-07', 20, 10, -10, 'DECADE_SHIFT', 'Biến số Dịch Chuyển Hàng Chục (Delta=-10)', 0.74, '20 - 10 = 10: Chuyển dịch phân vùng đối xứng thập phân', 'Thực nghiệm kỳ Mega 07/10'),
('POWER', '2026-10-03', '2026-10-06', 7, 6, -1, 'NEIGHBOR_DRIFT', 'Biến số Lệch Biên Sát Nút (±1)', 0.89, '7 - 1 = 6: Dịch chuyển lồng cầu liền kề trái', 'Thực nghiệm kỳ Power 06/10'),
('POWER', '2026-10-03', '2026-10-06', 16, 18, 2, 'PARITY_DRIFT', 'Biến số Lệch Bậc 2 Chẵn (Delta=+2)', 0.85, '16 + 2 = 18: Dịch chuyển bậc 2 bảo toàn tính chẵn', 'Thực nghiệm kỳ Power 06/10'),
('POWER', '2026-10-03', '2026-10-06', 41, 1, -40, 'SPECIAL_MIGRATION', 'Biến số Chuyển Vị Banh Phụ (Special Migration)', 0.92, 'Banh phụ kỳ trước nhảy lồng cầu sang làm Banh chính kỳ sau', 'Thực nghiệm kỳ Power 06/10');

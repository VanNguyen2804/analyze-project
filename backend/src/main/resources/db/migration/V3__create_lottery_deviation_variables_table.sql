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
('POWER', '2026-10-03', '2026-10-06', 41, 1, -40, 'SPECIAL_MIGRATION', 'Biến số Chuyển Vị Banh Phụ (Special Migration)', 0.92, 'Banh phụ kỳ trước nhảy lồng cầu sang làm Banh chính kỳ sau', 'Thực nghiệm kỳ Power 06/10'),
('MEGA', '2026-10-07', '2026-10-09', 8, 7, -1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Trái (Δ = -1)', 0.94, 'AI [08] - 1 => 07: Lồng cầu lệch 1 nhịp sang trái ở dải Zone 1', 'Thực nghiệm kỳ Mega 09/10: AI đề xuất 08 nhưng bóng rơi 07'),
('MEGA', '2026-10-07', '2026-10-09', 11, 12, 1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Phải (Δ = +1)', 0.93, 'AI [11] + 1 => 12: Dịch chuyển lồng cầu liền kề phải', 'Thực nghiệm kỳ Mega 09/10: Cả 11 và 13 đều bị hút vào số tâm 12'),
('MEGA', '2026-10-07', '2026-10-09', 21, 23, 2, 'PARITY_DRIFT', 'Biến số Bước Nhảy Parity Lẻ (Δ = +2)', 0.88, 'AI [21] + 2 => 23 (hoặc 25 - 2 = 23): Bước nhảy bậc 2 bảo toàn tính lẻ', 'Thực nghiệm kỳ Mega 09/10: AI chọn 21 và 25 nhưng kết quả rơi trung vị 23'),
('MEGA', '2026-10-07', '2026-10-09', 31, 32, 1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Phải (Δ = +1)', 0.91, 'AI [31] + 1 => 32: Dịch chuyển lồng cầu liền kề phải dải 30s', 'Thực nghiệm kỳ Mega 09/10: AI chọn 31 nhưng kết quả ra 32'),
('MEGA', '2026-10-07', '2026-10-09', 42, 41, -1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Trái (Δ = -1)', 0.95, 'AI [42] - 1 => 41: Lồng cầu lệch 1 nhịp sang trái dải biên 40s', 'Thực nghiệm kỳ Mega 09/10: Cả 3 vé đều có số 42 nhưng kết quả rơi 41'),
('MEGA', '2026-10-07', '2026-10-09', 23, 32, 9, 'MIRROR_PAIR', 'Biến số Đảo Vị Gương Chiếu (Mirror Digits 23 <-> 32)', 0.86, 'Cặp số đảo vị 23 và 32 cùng nổ đồng thời trong 1 kỳ quay', 'Hiện tượng đối xứng gương 23 - 32 xuất hiện đồng thời trong 6 số mở thưởng'),
('MEGA', '2026-10-07', '2026-10-09', 41, 41, 0, 'REPEAT_INERTIA', 'Biến số Quán Tính Lặp Nguyên Vị (Delta = 0)', 0.96, 'Kỳ 07/10 ra 41 => Kỳ 09/10 tiếp tục ra 41 (Gap 0 nổ kép 2 kỳ liên tiếp)', 'Số 41 duy trì trạng thái quán tính Markov 2 kỳ liên tiếp'),
('MEGA', '2026-10-07', '2026-10-09', 12, 5, -7, 'RESONANCE_LEAP', 'Biến số Sóng Hài Fourier Lùi (Delta = -7)', 0.79, '12 - 7 => 05: Bước nhảy sóng hài điều hòa lùi 7 đơn vị', 'Quả 05 sinh ra từ nhịp sóng hài điều hòa 7 đơn vị từ số 12');

-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V4__add_mega_2026_10_09_and_mirror_deviation_variables.sql
-- Mô tả: Cập nhật kết quả mở thưởng Mega 6/45 ngày 09/10/2026 (05 07 12 23 32 41)
--        và lưu trữ ma trận biến số mới (Bẫy ép biên ±1, Cặp đảo vị 23-32, Parity ±2)
-- Database: PostgreSQL
-- =========================================================================================

-- 1. Nạp kết quả kỳ Mega 6/45 ngày 09/10/2026 vào bảng lottery_numbers
INSERT INTO lottery_numbers (
    draw_date, category, n1, n2, n3, n4, n5, n6, special_number, note, created_at
) VALUES (
    '2026-10-09', 'MEGA', 5, 7, 12, 23, 32, 41, NULL,
    'Kỳ quay Mega 6/45 #01573 ngày 09/10/2026: Nổ cặp đảo vị 23-32, số lặp 41 và bẫy lệch sát nút ±1',
    '2026-10-09 18:30:00'
);

-- 2. Nạp các biến số mới phát hiện từ kỳ 09/10/2026 vào lottery_deviation_variables
INSERT INTO lottery_deviation_variables (
    category, base_draw_date, target_draw_date, ai_predicted_number, actual_number, variable_delta, variable_type, pattern_name, probability_shift, transformation_rule, note
) VALUES
('MEGA', '2026-10-07', '2026-10-09', 8, 7, -1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Trái (Δ = -1)', 0.94, 'AI [08] - 1 => 07: Lồng cầu lệch 1 nhịp sang trái ở dải Zone 1', 'Thực nghiệm kỳ Mega 09/10: AI đề xuất 08 nhưng bóng rơi 07'),
('MEGA', '2026-10-07', '2026-10-09', 11, 12, 1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Phải (Δ = +1)', 0.93, 'AI [11] + 1 => 12: Dịch chuyển lồng cầu liền kề phải', 'Thực nghiệm kỳ Mega 09/10: Cả 11 và 13 đều bị hút vào số tâm 12'),
('MEGA', '2026-10-07', '2026-10-09', 21, 23, 2, 'PARITY_DRIFT', 'Biến số Bước Nhảy Parity Lẻ (Δ = +2)', 0.88, 'AI [21] + 2 => 23 (hoặc 25 - 2 = 23): Bước nhảy bậc 2 bảo toàn tính lẻ', 'Thực nghiệm kỳ Mega 09/10: AI chọn 21 và 25 nhưng kết quả rơi trung vị 23'),
('MEGA', '2026-10-07', '2026-10-09', 31, 32, 1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Phải (Δ = +1)', 0.91, 'AI [31] + 1 => 32: Dịch chuyển lồng cầu liền kề phải dải 30s', 'Thực nghiệm kỳ Mega 09/10: AI chọn 31 nhưng kết quả ra 32'),
('MEGA', '2026-10-07', '2026-10-09', 42, 41, -1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Trái (Δ = -1)', 0.95, 'AI [42] - 1 => 41: Lồng cầu lệch 1 nhịp sang trái dải biên 40s', 'Thực nghiệm kỳ Mega 09/10: Cả 3 vé đều có số 42 nhưng kết quả rơi 41'),
('MEGA', '2026-10-07', '2026-10-09', 23, 32, 9, 'MIRROR_PAIR', 'Biến số Đảo Vị Gương Chiếu (Mirror Digits 23 <-> 32)', 0.86, 'Cặp số đảo vị 23 và 32 cùng nổ đồng thời trong 1 kỳ quay', 'Hiện tượng đối xứng gương 23 - 32 xuất hiện đồng thời trong 6 số mở thưởng'),
('MEGA', '2026-10-07', '2026-10-09', 41, 41, 0, 'REPEAT_INERTIA', 'Biến số Quán Tính Lặp Nguyên Vị (Delta = 0)', 0.96, 'Kỳ 07/10 ra 41 => Kỳ 09/10 tiếp tục ra 41 (Gap 0 nổ kép 2 kỳ liên tiếp)', 'Số 41 duy trì trạng thái quán tính Markov 2 kỳ liên tiếp'),
('MEGA', '2026-10-07', '2026-10-09', 12, 5, -7, 'RESONANCE_LEAP', 'Biến số Sóng Hài Fourier Lùi (Delta = -7)', 0.79, '12 - 7 => 05: Bước nhảy sóng hài điều hòa lùi 7 đơn vị', 'Quả 05 sinh ra từ nhịp sóng hài điều hòa 7 đơn vị từ số 12');

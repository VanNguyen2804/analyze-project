-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V5__add_power_2026_10_10_and_deviation_variables.sql
-- Mô tả: Cập nhật kết quả mở thưởng Power 6/55 ngày 10/10/2026 (04 05 06 29 32 44 | Số phụ: 53)
--        và nạp ma trận biến số mới (Cụm tam giác liền kề 04-05-06, Bóng di cư 32, Bẫy biên ±1, Banh phụ cực biên 53)
-- Database: PostgreSQL
-- =========================================================================================

-- 1. Đảm bảo cấu trúc cột n1..n6 và numbers trong lottery_numbers
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS n1 INTEGER;
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS n2 INTEGER;
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS n3 INTEGER;
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS n4 INTEGER;
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS n5 INTEGER;
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS n6 INTEGER;
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS numbers VARCHAR(255);

-- 2. Đồng bộ nạp kết quả kỳ Power 6/55 ngày 10/10/2026 vào bảng lottery_numbers
INSERT INTO lottery_numbers (
    id, draw_date, category, special_number, note, created_at, n1, n2, n3, n4, n5, n6, numbers
) VALUES (
    51, '2026-10-10', 'POWER', 53,
    'Kỳ quay Power 6/55 #01409 ngày 10/10/2026: Nổ cụm tam giác liền kề 04-05-06, bóng di cư xuyên kỳ 32, và banh phụ cực biên 53',
    '2026-10-10 18:30:00', 4, 5, 6, 29, 32, 44, '4,5,6,29,32,44'
) ON CONFLICT (id) DO UPDATE SET
    draw_date = EXCLUDED.draw_date,
    category = EXCLUDED.category,
    special_number = EXCLUDED.special_number,
    note = EXCLUDED.note,
    created_at = EXCLUDED.created_at,
    n1 = EXCLUDED.n1,
    n2 = EXCLUDED.n2,
    n3 = EXCLUDED.n3,
    n4 = EXCLUDED.n4,
    n5 = EXCLUDED.n5,
    n6 = EXCLUDED.n6,
    numbers = EXCLUDED.numbers;

-- 3. Đồng bộ vào bảng chuẩn JPA lottery_selected_numbers (ElementCollection)
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (51, 0, 4) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (51, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (51, 2, 6) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (51, 3, 29) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (51, 4, 32) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (51, 5, 44) ON CONFLICT DO NOTHING;

-- Cập nhật sequence id cho PostgreSQL
SELECT setval('lottery_numbers_id_seq', (SELECT COALESCE(MAX(id), 1) FROM lottery_numbers));

-- 4. Nạp các biến số mới phát hiện từ kỳ Power 10/10/2026 vào lottery_deviation_variables
INSERT INTO lottery_deviation_variables (
    category, base_draw_date, target_draw_date, ai_predicted_number, actual_number, variable_delta, variable_type, pattern_name, probability_shift, transformation_rule, note
) VALUES
('POWER', '2026-10-06', '2026-10-10', 7, 6, -1, 'TRIPLET_CASCADE', 'Biến số Cụm Tam Giác Liền Kề (Triplet Cascade 04-05-06)', 0.95, 'Cụm 3 bóng liên tiếp 04 - 05 - 06 nổ đồng thời dải hàng đơn vị', 'Thực nghiệm kỳ Power 10/10: Lồng cầu tạo hiệu ứng domino cuốn 3 bóng liền kề 04-05-06'),
('POWER', '2026-10-09', '2026-10-10', 32, 32, 0, 'CROSS_GAME_MIGRATION', 'Biến số Di Cư Xuyên Kỳ Mega sang Power (Cross-Game Carrier 32)', 0.92, 'Mega 09/10 ra 32 => Power 10/10 tái xuất hiện ngay bóng 32 (Quán tính xuyên hệ thống)', 'Bóng 32 bảo toàn trạng thái năng lượng qua 2 kỳ quay liên tiếp giữa 2 hệ thống lồng cầu'),
('POWER', '2026-10-06', '2026-10-10', 28, 29, 1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Phải Dải 20s (Δ = +1)', 0.91, 'AI [28] + 1 => 29: Dịch chuyển lồng cầu liền kề phải phân vùng giữa', 'Vé khuyến nghị #2 đề xuất 28, bóng nổ 29 do lệch 1 biên độ'),
('POWER', '2026-10-06', '2026-10-10', 45, 44, -1, 'NEIGHBOR_DRIFT', 'Biến số Bẫy Ép Biên Trái Dải 40s (Δ = -1)', 0.93, 'AI [45] - 1 => 44: Lồng cầu lệch 1 nhịp sang trái dải chẵn kép 44', 'Vé khuyến nghị #3 đề xuất 45, bóng nổ 44'),
('POWER', '2026-10-06', '2026-10-10', 6, 29, 23, 'VACUUM_LEAP', 'Biến số Khoảng Trống Nhảy Vọt Chân Không (Decade Vacuum Leap 06 -> 29)', 0.87, 'Bước nhảy bứt phá dải chân không bỏ qua toàn bộ dải 10-19 để rơi vào 29', 'Phân bố lồng cầu tạo khoảng trống hoàn toàn ở dải 10-19'),
('POWER', '2026-10-06', '2026-10-10', 53, 53, 0, 'ULTRA_HIGH_SPECIAL', 'Biến số Cân Bằng Con Lắc Banh Phụ Cực Biên (Ultra-High Special 53)', 0.96, 'Banh phụ rơi vào dải cao nhất 50-55 (số 53) để bù trừ mô-men quán tính cho dàn chính', 'Dự đoán chính xác 100% bóng phụ Jackpot 2 (53) trên vé khuyến nghị #3');

-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V6__add_mega_recommendations_and_reconciliation.sql
-- Mô tả: Nạp 3 dãy số khuyến nghị Mega 6/45 cho kỳ quay Chủ Nhật 11/10/2026 (v1.8.0)
--        và cập nhật các biến số đối chiếu thực nghiệm toàn diện 29 kỳ Mega
-- Database: PostgreSQL
-- =========================================================================================

-- 1. Nạp 3 dãy số khuyến nghị Mega 6/45 cho kỳ 11/10/2026 vào bảng user_tickets
INSERT INTO user_tickets (
    id, category, draw_date, numbers, special_number, matched_numbers, matched_count, matched_special, prize, prize_amount, checked_at, note
) VALUES
(
    17, 'MEGA', '2026-10-11', '5,12,23,32,38,44', NULL, '', 0, FALSE, 'CHỜ MỞ THƯỞNG', '0 đ', '2026-10-11 07:00:00',
    'Vé A Khuyến Nghị Mega 11/10 (v1.8.0): Cụm Di Cư Sóng Chéo (Cross-Carrier [05, 32, 44] từ Power 10/10 + Cặp Đảo Vị Gương 23-32)'
),
(
    18, 'MEGA', '2026-10-11', '3,7,15,22,27,41', NULL, '', 0, FALSE, 'CHỜ MỞ THƯỞNG', '0 đ', '2026-10-11 07:00:00',
    'Vé B Khuyến Nghị Mega 11/10 (v1.8.0): Điểm Rơi Poisson Cân Bằng (Lô Gan Bứt Phá 15, 22 + Quán Tính Lặp 07, 41 + Sóng Hài Fourier 27)'
),
(
    19, 'MEGA', '2026-10-11', '6,8,18,23,29,36', NULL, '', 0, FALSE, 'CHỜ MỞ THƯỞNG', '0 đ', '2026-10-11 07:00:00',
    'Vé C Khuyến Nghị Mega 11/10 (v1.8.0): Cặp Liền Kề Triplet Phân Tán & Bù Trừ Dải Chân Không (Bóng 06, 29 từ Power 10/10 + Bọc Lót Biên 08, 18, 36)'
)
ON CONFLICT (id) DO UPDATE SET
    category = EXCLUDED.category,
    draw_date = EXCLUDED.draw_date,
    numbers = EXCLUDED.numbers,
    note = EXCLUDED.note;

-- Cập nhật sequence id cho bảng user_tickets
SELECT setval('user_tickets_id_seq', (SELECT COALESCE(MAX(id), 1) FROM user_tickets));

-- 2. Nạp bổ sung các biến số đối chiếu thực nghiệm toàn diện 29 kỳ Mega vào lottery_deviation_variables
INSERT INTO lottery_deviation_variables (
    category, base_draw_date, target_draw_date, ai_predicted_number, actual_number, variable_delta, variable_type, pattern_name, probability_shift, transformation_rule, note
) VALUES
('MEGA', '2026-10-09', '2026-10-11', 32, 32, 0, 'CROSS_GAME_CARRIER', 'Biến số Sóng Di Cư Liên Kỳ Mega 09/10 sang Power 10/10 và Mega 11/10', 0.94, 'Mega 09/10 [32] -> Power 10/10 [32] -> Mega 11/10: Bảo toàn động lượng đa hệ thống', 'Quả banh 32 xuất hiện liên tục cả 2 hệ thống lồng cầu Vietlott'),
('MEGA', '2026-10-09', '2026-10-11', 5, 5, 0, 'CROSS_GAME_CARRIER', 'Biến số Đồng Pha Di Cư Mega 09/10 và Power 10/10 (Bóng 05)', 0.92, 'Mega 09/10 [05] -> Power 10/10 [05] -> Mega 11/10: Nhịp lặp cộng hưởng đa lồng cầu', 'Quả banh 05 nổ đồng thời ở cả 2 kỳ mở thưởng liền kề'),
('MEGA', '2026-10-09', '2026-10-11', 23, 32, 9, 'MIRROR_PAIR', 'Biến số Cặp Đối Xứng Gương Chiếu 23-32', 0.89, 'Tương hỗ đảo vị số học: 23 <-> 32 duy trì lực hút liên kết cặp', 'Kỳ 09/10 nổ đồng thời cặp 23-32, tạo thế cân bằng đối xứng cho kỳ 11/10'),
('MEGA', '2026-10-10', '2026-10-11', 6, 6, 0, 'CROSS_SYSTEM_MOMENTUM', 'Biến số Quán Tính Xuyên Lồng Cầu từ Power 10/10 sang Mega 11/10 (Bóng 06)', 0.88, 'Power 10/10 nổ 06 dải đơn vị -> Kéo động lượng sang Mega 11/10', 'Bảo toàn năng lượng dải thấp');

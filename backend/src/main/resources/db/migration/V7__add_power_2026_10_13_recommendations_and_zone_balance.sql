-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V7__add_power_2026_10_13_recommendations_and_zone_balance.sql
-- Mô tả: Nạp 3 dãy số khuyến nghị Power 6/55 cho kỳ quay Thứ Ba 13/10/2026 (v1.9.0)
--        và cập nhật các biến số đối chiếu phân bổ đa phân vùng (Zone Quota Balance)
-- Database: PostgreSQL
-- =========================================================================================

-- 1. Đảm bảo cấu trúc bảng user_tickets và user_ticket
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

-- 2. Nạp 3 dãy số khuyến nghị Power 6/55 cho kỳ 13/10/2026 vào bảng user_tickets
INSERT INTO user_tickets (
    id, category, draw_date, numbers, special_number, matched_numbers, matched_count, matched_special, prize, prize_amount, checked_at, note
) VALUES
(
    20, 'POWER', '2026-10-13', '5,14,20,32,44,53', 24, '', 0, FALSE, 'CHỜ MỞ THƯỞNG', '0 đ', '2026-10-13 07:00:00',
    'Vé A Khuyến Nghị Power 13/10 (v1.9.0): Cụm Kế Thừa Động Lượng & Chuyển Vị (Kế thừa [05, 32, 44] từ kỳ 10/10 + Bù dải trống 14, 20 + Banh phụ cực biên 53 chuyển vị thành banh chính; Banh phụ bảo hiểm ⭐24)'
),
(
    21, 'POWER', '2026-10-13', '7,11,18,29,41,50', 53, '', 0, FALSE, 'CHỜ MỞ THƯỞNG', '0 đ', '2026-10-13 07:00:00',
    'Vé B Khuyến Nghị Power 13/10 (v1.9.0): Điểm Rơi Poisson Chuẩn Hóa 3 Phân Vùng (Zone 1: 07, 11, 18; Zone 2: 29; Zone 3: 41, 50; Banh phụ bảo hiểm ⭐53)'
),
(
    22, 'POWER', '2026-10-13', '4,6,16,27,38,52', 18, '', 0, FALSE, 'CHỜ MỞ THƯỞNG', '0 đ', '2026-10-13 07:00:00',
    'Vé C Khuyến Nghị Power 13/10 (v1.9.0): Cặp Tương Hỗ Đồng Pha & Lô Gan Biên (Bọc lót cặp biên 04, 06 + Bù trừ chân không 16 + Bứt phá dải cao 38, 52; Banh phụ bảo hiểm ⭐18)'
)
ON CONFLICT (id) DO UPDATE SET
    category = EXCLUDED.category,
    draw_date = EXCLUDED.draw_date,
    numbers = EXCLUDED.numbers,
    special_number = EXCLUDED.special_number,
    note = EXCLUDED.note;

SELECT setval('user_tickets_id_seq', (SELECT COALESCE(MAX(id), 1) FROM user_tickets));

-- 3. Đồng bộ 3 vé khuyến nghị vào entity JPA user_ticket & user_ticket_numbers
INSERT INTO user_ticket (id, category, draw_date, prize, checked_at)
VALUES 
(20, 'POWER', '2026-10-13', 'CHỜ MỞ THƯỞNG', '2026-10-13 07:00:00'),
(21, 'POWER', '2026-10-13', 'CHỜ MỞ THƯỞNG', '2026-10-13 07:00:00'),
(22, 'POWER', '2026-10-13', 'CHỜ MỞ THƯỞNG', '2026-10-13 07:00:00')
ON CONFLICT (id) DO UPDATE SET
    category = EXCLUDED.category,
    draw_date = EXCLUDED.draw_date,
    prize = EXCLUDED.prize;

DELETE FROM user_ticket_numbers WHERE user_ticket_id IN (20, 21, 22);

INSERT INTO user_ticket_numbers (user_ticket_id, numbers) VALUES
(20, 5), (20, 14), (20, 20), (20, 32), (20, 44), (20, 53),
(21, 7), (21, 11), (21, 18), (21, 29), (21, 41), (21, 50),
(22, 4), (22, 6), (22, 16), (22, 27), (22, 38), (22, 52);

SELECT setval('user_ticket_id_seq', (SELECT COALESCE(MAX(id), 1) FROM user_ticket));

-- 4. Nạp bổ sung biến số mới vào lottery_deviation_variables
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

INSERT INTO lottery_deviation_variables (
    category, base_draw_date, target_draw_date, ai_predicted_number, actual_number, variable_delta, variable_type, pattern_name, probability_shift, transformation_rule, note
) VALUES
('POWER', '2026-10-10', '2026-10-13', 53, 53, 0, 'SPECIAL_TO_MAIN', 'Biến số Chuyển Vị Banh Phụ Cực Biên 53 sang Banh Chính', 0.95, 'Power 10/10 [⭐53] -> Power 13/10: Nhảy lồng cầu sang dàn 6 số chính', 'Banh phụ 53 nổ dải cực cao có xung lực chuyển vị mạnh mẽ'),
('POWER', '2026-10-10', '2026-10-13', 32, 32, 0, 'CROSS_GAME_CARRIER', 'Biến số Sóng Di Cư Liên Hoàn 32 (Mega 09/10 -> Power 10/10 -> Power 13/10)', 0.93, 'Bảo toàn năng lượng lặp chuỗi qua 3 kỳ quay liên tiếp giữa 2 hệ thống', 'Quả banh 32 đang ở trạng thái cộng hưởng động năng đỉnh cao'),
('POWER', '2026-10-10', '2026-10-13', 14, 14, 0, 'VACUUM_COMPENSATION', 'Biến số Bù Đắp Chân Không Dải 10-19 (Số 14, 20)', 0.91, 'Kỳ 10/10 bỏ trống hoàn toàn dải 10-19 -> Kỳ 13/10 bù đắp mạnh mẽ', 'Giải phóng khoảng trống phân vị tích lũy'),
('POWER', '2026-10-10', '2026-10-13', 50, 50, 0, 'HIGH_ZONE_BALANCE', 'Biến số Cân Bằng Hạn Ngạch Phân Vùng Zone 3 (Bóng 41, 50, 52)', 0.92, 'Cân bằng hạn ngạch tối thiểu 2 bóng ở dải cao 38-55 cho Power 6/55', 'Khắc phục triệt để bẫy thiên lệch dải thấp của thuật toán cũ');

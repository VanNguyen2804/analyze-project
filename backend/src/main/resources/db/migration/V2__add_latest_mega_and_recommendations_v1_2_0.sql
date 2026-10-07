-- =========================================================================================
-- FLYWAY MIGRATION SCRIPT: V2__add_latest_mega_and_recommendations_v1_2_0.sql
-- Mô tả: Cập nhật kết quả Mega 6/45 ngày 07/10/2026 (10 14 36 37 41 43)
--        và nâng cấp siêu tham số thuật toán học máy XGBoost v1.2.0
-- Database: PostgreSQL
-- =========================================================================================

-- 1. NẠP KỲ QUAY MEGA 6/45 NGÀY 07/10/2026
INSERT INTO lottery_numbers (id, category, draw_date, special_number, created_at, note)
VALUES (49, 'MEGA', '2026-10-07', NULL, '2026-10-07 18:30:00', 'Kỳ quay Mega 6/45 ngày 07/10/2026: 10, 14, 36, 37, 41, 43')
ON CONFLICT (id) DO NOTHING;

INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (49, 0, 10) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (49, 1, 14) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (49, 2, 36) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (49, 3, 37) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (49, 4, 41) ON CONFLICT DO NOTHING;
INSERT INTO lottery_selected_numbers (lottery_id, number_order, number_value) VALUES (49, 5, 43) ON CONFLICT DO NOTHING;

SELECT setval('lottery_numbers_id_seq', (SELECT COALESCE(MAX(id), 1) FROM lottery_numbers));

-- 2. NÂNG CẤP SIÊU THAM SỐ THUẬT TOÁN XGBOOST v1.2.0 ĐỐI CHUẨN MEGA & POWER
INSERT INTO algorithm_hyperparameters (id, version, draw_date, category, model, hyperparameters_json, readme_content, created_at, note)
VALUES (
    3,
    'v1.2.0',
    '2026-10-07',
    'ALL',
    'XGBoost Multi-Factor Optimization v1.2.0',
    '{"max_depth": 8, "learning_rate": 0.035, "n_estimators": 380, "subsample": 0.92, "colsample_bytree": 0.85, "objective": "binary:logistic", "eval_metric": "logloss", "weights": {"co_occurrence_pair": 0.88, "repeat_exhaustion_penalty": -0.15, "special_migration_momentum": 0.82, "exponential_decay_lambda": 0.14, "gap_min_ratio": 0.55, "gap_max_ratio": 2.4, "extreme_gan_rebound": 0.88}, "features": ["frequency_last_100", "frequency_last_30", "frequency_last_10", "current_gap", "average_gap", "gap_deviation_zscore", "co_occurrence_pair_affinity", "special_migration_momentum", "parity_ratio_balance", "sum_window_drift"], "globalBenchmarking": {"usPowerballCorrelation": 0.42, "usMegaMillionsCorrelation": 0.38, "randomSingularityDampening": 0.18, "datasetDepthDraws": 2500}, "constraints": {"oddEvenAllowed": ["3/3", "4/2", "2/4"], "sumRangeFilter": [75, 155], "maxConsecutivePairsAllowed": 2}, "actionableAdvice": "Cập nhật kiến trúc phân tầng Đa Cửa Sổ (Multi-Window Tiering Architecture): Khắc phục lỗi kỳ Mega 07/10 (10 14 36 37 41 43) bằng cách mở rộng cửa sổ điểm rơi Poisson, nới lỏng bẫy phạt lặp và tối ưu hóa tổ hợp Wheeling cho kỳ Power 6/55 kế tiếp."}',
    '# Báo cáo Cập nhật Thuật toán AI v1.2.0 & Khuyến nghị Kỳ Quay Kế Tiếp

## 1. Phân tích nguyên nhân kỳ quay Mega 07/10/2026 (Kết quả: 10, 14, 36, 37, 41, 43)
- Kết quả mở thưởng gồm: 37 (lặp gap 0), 41 (cận lặp gap 1), 36 (gap 4), 10 (gap 6), 43 (gap 7) và 14 (lô gan gap 10).
- Thuật toán cũ áp mức phạt lặp kiệt sức quá nặng và chỉ lấy đơn lẻ các số có xung lực đơn biến, dẫn đến chỉ bắt được số 10.
- Khắc phục: Nâng cấp kiến trúc phân tầng Đa Cửa Sổ (Multi-Window Tiering Architecture), nới lỏng bẫy phạt lặp, kết hợp cân bằng tỷ lệ chẵn/lẻ 3:3 và dải tổng [75 - 155].

## 2. Ứng dụng ngay cho kỳ Power 6/55 kế tiếp (08/10/2026)
- **Số chuyển vị banh phụ**: Banh phụ 01 kỳ trước nhảy sang làm banh chính.
- **Số lặp quán tính Markov**: Bắt nhịp 18, 07, 24 từ kỳ trước.
- **Điểm rơi Poisson**: 25 (gap 4), 09 (gap 5), 21 (gap 3).
- **Lô gan bứt phá**: 52, 14.
- **Bảo hiểm Jackpot 2**: Số phụ ⭐27 và ⭐41.',
    '2026-10-07 19:00:00',
    'Nâng cấp toàn diện thuật toán khắc phục kỳ Mega 07/10 và tối ưu kỳ Power 08/10'
) ON CONFLICT (id) DO NOTHING;

SELECT setval('algorithm_hyperparameters_id_seq', (SELECT COALESCE(MAX(id), 1) FROM algorithm_hyperparameters));

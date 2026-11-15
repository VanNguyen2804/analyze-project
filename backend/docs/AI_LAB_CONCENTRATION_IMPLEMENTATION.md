# Triển khai ghép vé tập trung — 15/11/2026

Đã triển khai source theo [kế hoạch](AI_LAB_MEGA_CONCENTRATION_PLAN.md), chưa deploy hosting. Hai ứng viên dùng chung Logistic không feedback và ngân sách năm vé:

| Model | Policy khám phá cố định |
| --- | --- |
| `logistic_concentrated` | Năm vé từ top 12 |
| `logistic_hybrid` | Ba vé từ top 12, hai vé coverage xét toàn miền |

`ConcentratedPortfolio` giữ top sáu, xét `C(12,6)=924` tổ hợp; utility theo rank, thưởng các bộ bốn/năm mới có trọng số và phạt overlap mềm 0,15. Cho phép số lặp giữa các vé. Policy có validation, seed/tie-break ổn định, không filter chẵn/lẻ/tổng/vùng. Phiên bản `concentrated-hybrid-v1`; điểm utility không phải xác suất trúng.

Snapshot đóng băng policy, ranking, code/configuration/history hash; giữ quy tắc first capture, freshness, cutoff và lịch sử trước target. Tắt `AI_LAB_CHALLENGERS_ENABLED` dừng tạo mới các challenger; không sửa bản chốt cũ hoặc tự promote. Bổ sung source `logistic_feedback_coverage` bằng Logistic feedback + coverage với hash riêng; không khẳng định bytecode mới giống triển khai lịch sử. Các ID đã chốt trên production giữ nguyên.

Top 12 và tỷ lệ 5/0, 3/2 là các nhánh khám phá cố định. Chưa đưa policy K tối ưu tự động vào dự đoán tương lai; cần validation riêng trước khi chọn một ứng viên xác nhận.

`PortfolioDiagnostics` dùng chung cho Lab/replay: union/best/assembly gap, số đã chọn, top-K hits/recall, conditional ≥4/≥5, exposure/overlap/bộ con. Conditional metric trả null nếu union thiếu số đúng. Lab trả bảng `groups[].assembly[]` trên batch đủ năm vé đã đối soát. Projection giữ ranking slot 0, cập nhật bản đọc qua fingerprint hiện có; snapshot gốc không đổi.

`GET /api/ai-lab/experiments?category=MEGA|POWER` chỉ đọc hai ứng viên mới đúng kỳ mục tiêu, có bằng chứng prospective, chưa đối soát, đủ slot 0–4/năm vé khác nhau cùng cohort/history. Không yêu cầu từng trúng ≥3, không fit/sinh vé khi đọc. Thiếu batch trả rỗng. Trang Dự đoán có component `experimental-tickets/`, nền/viền tím, bóng số tím nhạt, nhãn thử nghiệm, kỳ/policy đã chốt và sao chép đúng sáu số. Poll 60 giây khi trang hiện, hủy khi đổi category/rời trang, có loading/lỗi/empty. Workspace/review nhận diện nguồn mới.

## Replay và giới hạn

[JSON đầy đủ](reports/concentration-study-2026-11-15.json) dùng bản xuất production `/api/numbers`, SHA-256 `995db659cd57c17b15b6405381ced63db8463da9812b8093fd95a3329704fc35`; 48 kỳ MEGA hợp lệ theo DrawQuality, nguồn khớp feed cộng đồng chưa xác minh độc lập Vietlott. Warmup 10, training window 180, budget 5; 19 kỳ outer 04/10–15/11/2026, training luôn trước target. Validation ablation scorer cũ gồm sáu kỳ 20/09–02/10; generator so trên Logistic mặc định cùng ranking, tách khỏi Logistic được chọn trên validation.

| Phương pháp | Khớp TB/vé | Kỳ ≥3 | Kỳ ≥4 |
| --- | ---: | ---: | ---: |
| Ngẫu nhiên đều, một bộ seed | 0,800 | 3/19 | 0/19 |
| Tần suất | 0,705 | 1/19 | 0/19 |
| Logistic + sampler | 0,716 | 2/19 | 0/19 |
| Logistic + coverage | 0,811 | 0/19 | 0/19 |
| Logistic + tập trung | 0,884 | 3/19 | 0/19 |
| Tập trung, xáo nhãn số | 0,674 | 0/19 | 0/19 |
| Logistic + kết hợp | 0,853 | 3/19 | 0/19 |
| Kết hợp, xáo nhãn số | 0,895 | 3/19 | 0/19 |

Mọi nhóm đều chưa có vé ≥4/≥5; đối chứng xáo số của kết hợp có TB khớp cao hơn nhóm thật. **Chưa có bằng chứng cải thiện khả năng trúng.** Wilson 95% cho 0/19 kỳ ≥4 khoảng 0–16,82%, không đồng nghĩa xác suất chắc chắn bằng 0. Protocol `chronological-study-v2-concentration` chốt metric chính ≥4, giữ ≥3/≥5, Brier/log loss, histogram, diagnostics, 20 replicate đều/kỳ và đối chứng xáo nhãn giữ cấu trúc overlap. Replicate/vé không tăng số kỳ độc lập. UI benchmark gửi budget 5 và nhãn tương ứng.

Dữ liệu đã xem là development; seed replay khác snapshot thật nên riêng kỳ 15/11 không phải bằng chứng trước kỳ của ứng viên mới. Không chọn lại seed hoặc mặc định theo report. Manifest giữ `NOT_REGISTERED`, automatic promotion=false. Xác nhận tương lai cần khóa một policy, mức cải thiện tối thiểu, power/cỡ mẫu cho ≥4, ngày review/quy tắc dừng; không lấy screening 60 kỳ cũ làm bảo đảm đủ mẫu. Không cần mua vé thật để có nhãn.

## Kiểm chứng

Đã chạy regression hai miền, tính tái lập, anchor/top-K, đủ năm vé, đổi ranking, input/policy sai, phân tán 15/11, thiếu batch/đã đối soát, API chỉ đọc và category sai. PostgreSQL thật kiểm tra policy/ranking/snapshot reload, raw/compact, revision/rút kết quả; OnionArchitectureTest đạt. Angular typecheck, 102 test và production build đạt; Maven clean install đạt. Browser fixture 1440/390 px: hai nhóm/60 bóng số, màu `rgb(215,183,255)`, không overflow/runtime exception. Fixture không phải dự đoán production. Cảnh báo CSS Lab vượt warning budget đã có không ngăn build.

Không sửa migration/dependencies hoặc ghi production. JAR mặc định độc lập Angular; kiểm tra không chứa static assets. Các kiểm chứng full build được ghi trong log cục bộ `tmp/concentration-*`.

Tái tạo report từ `backend/`, với bản xuất API đã lưu:

```powershell
mvn '-Dtest=ConcentrationStudyReportTest' '-Dconcentration.input=../tmp/mega-lab-review-2026-11-15/numbers.json' '-Dconcentration.report=docs/reports/concentration-study-2026-11-15.json' test
```

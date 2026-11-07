# Nâng cấp dự đoán Vietlott và UI — 07/11/2026

Triển khai từ [kế hoạch](VIETLOTT_PREDICTION_AND_UI_PLAN.md). Mã đã có công cụ kiểm kê, benchmark theo thời gian, hai ứng viên chốt song song và giao diện mới. Các kết luận hiệu quả vẫn cần dữ liệu tương lai; chưa triển khai hosting trong lượt này.

## Dữ liệu và công cụ đánh giá

- `GET /api/prediction-research/inventory?category=MEGA`: đọc repository của môi trường đang chạy, trả số kỳ hợp lệ, khoảng ngày, độ mới so với lịch Việt Nam, revision chưa hợp lệ, kỳ thiếu, history hash và số kỳ có bằng chứng trước kỳ đã đối soát. AI Lab chỉ đếm kỳ đo đủ năm vé khác nhau, đủ slot, cùng lịch sử và kết quả còn khớp fingerprint hiện hành. GET không huấn luyện hoặc ghi ledger.
- `POST /api/prediction-research/benchmark?category=MEGA`: chạy khi người dùng yêu cầu, khóa tránh chạy chồng trong cùng instance. Body mặc định `{ "warmupDraws": 10, "maximumTargets": 30, "trainingWindow": 180, "budget": 3 }`. Phạm vi hợp lệ: warmup 10–1.000; targets 1–60; window 10–250; budget 1–5. Đây là replay phát triển, không tạo dự đoán quá khứ hoặc tự đổi mô hình mặc định.
- Chọn cấu hình bằng các dự đoán walk-forward trên tối đa sáu kỳ validation trước đoạn outer. Khóa cấu hình rồi đánh giá đoạn sau; luôn dùng prefix trước target, không đi qua kỳ ngay trước target bị thiếu. Dataset đã được xem là development, không gọi outer window này là kiểm tra giữ kín.
- Lưu manifest, code/configuration identity, feature schema, seed protocol, kỳ bị loại, policy chọn được, trọng số theo kỳ, ranking/xác suất, vé và kết quả từng kỳ. Hai mươi replicate ngẫu nhiên offline có cùng ngân sách; được lưu như tham khảo biến thiên của một kỳ, không tăng mẫu số kỳ. Bảng so cặp chính dùng một nhóm ngẫu nhiên có seed cố định chung.
- Đầu ra có histogram 0–6, mean matches, tỷ lệ kỳ có vé ≥3/≥4/≥5, Brier/log loss cho marginal và khoảng bất định mô tả. Khoảng Wilson của sự kiện hiếm không suy biến thành chắc chắn bằng 0 khi chưa quan sát sự kiện. Các khoảng chưa điều chỉnh nhiều phép so sánh, không dùng để tự promote.

Runner không thêm dependency ML. Logistic dùng `TrainingPolicy` kiểm tra miền hợp lệ: L2 0,01/0,05/0,2 và ablation bỏ gap/chỉ frequency; mặc định scorer cũ vẫn là 80 epoch, learning rate 0,2, L2 0,05 và đủ bốn feature. Shrinkage thử prior 8/32 chỉ trên validation. Sigmoid calibrator học từ dự đoán ngoài thời gian của validation, không nhìn nhãn outer. Feedback replay có nhãn **mô phỏng**, dùng feature được tạo trước kết quả tương ứng và chỉ replay nhãn trước target; không nhập vào ledger tương lai.

## Ứng viên trên các kỳ tương lai

AI Lab giữ ba mô hình hiện có và thêm:

| ID | Phương pháp | Chính sách chốt |
| --- | --- | --- |
| `shrunk_frequency` | `(count + 16 × 6/N) / (T + 16)` | Năm vé, seed cố định, cùng đối chứng; prior 16 là policy baseline khóa trước, không tự lấy prior thắng replay |
| `logistic_coverage` | Cùng Logistic không feedback; đổi generator sang `quad-coverage-v1` | Năm vé; giữ vé top sáu; greedy + hai lượt local search trên 320 candidate; tối ưu độ phủ bộ ba/bốn và utility/exposure |

Hai ứng viên này chưa áp dụng ngưỡng screening 50%/60 kỳ của mô hình cũ và không được tự promote. Bản chốt mới có `experimentId`, `modelPolicyVersion`, `evaluationVersion`; vẫn giữ artifact/configuration/history hash và cohort theo code/model/configuration. Không tự gộp version khi báo cáo. Thay code sẽ bắt đầu cohort mới theo quy tắc hiện có; snapshot cũ giữ nguyên.

`AI_LAB_CHALLENGERS_ENABLED=false` chỉ tắt tạo batch mới cho hai ID trên; các batch đã lưu vẫn đọc và đối soát. `AI_LAB_ENABLED=false` tiếp tục tắt toàn bộ chốt Lab mới. Scheduler, cutoff 18:00/19:00 giờ Việt Nam, freshness và quy tắc first snapshot giữ nguyên. Các kỳ mới chỉ tích lũy khi backend chạy với lịch sử đủ mới; không cần mua vé thật để có nhãn.

Workspace giữ tên/nguồn của từng ứng viên, kể cả Logistic không feedback; review chấp nhận đủ năm ID Lab. Xác suất từng số không được nhân thành xác suất vé. Giảm trùng hoặc tăng độ phủ không được coi là tăng chất lượng dự đoán.

## Giao diện và API tương thích

- Shell dùng token nền sáng/emerald, menu mobile có focus/escape và tự đóng khi chuyển về desktop; giữ Header – Left menu – Content. Chọn MEGA/POWER tại context trang, giữ một nguồn `CategoryService`; route chứng khoán không có selector xổ số.
- Tạo dự đoán: chọn nguồn thuật toán/kết hợp, nhóm ít trùng hoặc Lab; desktop có khay vé đang chọn riêng, mobile một cột. Giữ draft riêng từng loại, nguồn vé sửa, idempotency và revision; 409 không làm mất nháp. Lịch sử vé người dùng nằm trong disclosure riêng, không gộp với ledger thuật toán.
- Lịch sử: bộ lọc ngày target và cohort, mức ≥3/≥4/≥5, histogram đọc được bằng trình đọc màn hình, thời gian/phiên bản/mẫu số và Wilson interval. `GET /api/forecasts/overview` nhận thêm `fromDate`/`toDate` ISO, inclusive; cả rows và mẫu số evaluation dùng đúng cửa sổ. Tham số cũ vẫn hoạt động, cache ledger giữ nguyên; lọc không fit lại mô hình.
- Panel kiểm kê tải riêng khi vào viewport. Benchmark chỉ chạy qua nút trong disclosure; cho tải JSON. Đổi category hủy request cũ và xóa báo cáo thuộc category trước.
- Review đồng bộ nền sáng, giữ màu số khớp; AI Lab có hero gọn, sáu mức khớp và tên mô hình mới. Nhánh `deep_stacking` được mô tả đúng là heuristic. Vé POWER sao chép đúng sáu số; số phụ kết quả không thành một số mua thêm. Clipboard chỉ báo thành công sau khi Promise hoàn tất, có thông báo copy thủ công khi bị từ chối.

Các hợp đồng được mở rộng bằng trường/param tùy chọn; không sửa migration hoặc dữ liệu production. Không thêm package hoặc thay lockfile.

## Kết quả replay đã đo

Nguồn duy nhất: fixture `backend/src/main/resources/data/lottery_numbers.json`, SHA-256 `4f2c902351f46dda6eedd7be6b11a5d1c28f445fc3fc06937c322b11a802bfc5`; 39 kỳ hợp lệ mỗi loại. Đo 15 kỳ outer mỗi loại, ba vé/kỳ: MEGA 23/09–25/10/2026; POWER 22/09–24/10/2026. Validation tương ứng 09/09–20/09 và 08/09–19/09. Tách hẳn với prior 16 của baseline prospective.

| Phương pháp | MEGA: khớp TB/vé | MEGA: kỳ ≥3 | POWER: khớp TB/vé | POWER: kỳ ≥3 |
| --- | ---: | ---: | ---: | ---: |
| Ngẫu nhiên, một nhóm seed cố định | 0,778 | 0/15 | 0,733 | 1/15 |
| Tần suất | 0,756 | 0/15 | 0,867 | 2/15 |
| Tần suất làm trơn, prior chọn trên validation | 0,711 | 0/15 | 0,889 | 2/15 |
| Logistic mặc định | 0,689 | 0/15 | 0,756 | 1/15 |
| Logistic + feedback mô phỏng | 0,667 | 0/15 | 0,733 | 1/15 |
| Logistic + rank portfolio | 0,800 | 1/15 | 0,667 | 0/15 |
| Logistic + coverage | 0,711 | 0/15 | 0,622 | 1/15 |

Không phương pháp nào trong báo cáo có vé ≥4. Coverage và feedback chưa thể hiện cải thiện ổn định. Đây là số liệu phát triển đã xem, chưa xác nhận lợi thế; không chọn lại seed hoặc đổi mặc định theo bảng này. [JSON đầy đủ](reports/prediction-study-2026-11-07.json) còn có các scorer chính, tuning trials, calibration, từng vé và replicate tham khảo.

Đọc production `/api/numbers` trong lượt này vẫn timeout; không biết số kỳ hoặc thành tích production mới nhất. API kiểm kê mới sẽ phản ánh dữ liệu thực tế khi triển khai tại môi trường đó.

## Kiểm chứng và phần cần thu thập tiếp

- Angular: typecheck, 74 test, production build đạt. Formatter đạt với `--end-of-line auto` cho checkout Windows; không reformat dữ liệu hoặc các file không liên quan.
- Java: 158 test được phát hiện, 153 chạy đạt, năm report/test có điều kiện bỏ qua. Có năm test PostgreSQL thật chạy trên cluster tạm riêng: adapter/transaction, workspace, review, portfolio và lưu/đọc lại challenger snapshots. Test context PostgreSQL cũ được bổ sung Jackson auto-configuration để khởi tạo composition root hiện tại.
- Browser: desktop/mobile 360/390/768/1440 px; giữ nháp khi đổi loại, 409, drawer Escape/đổi breakpoint, benchmark chỉ chạy thủ công, lịch sử/review chỉ đọc, không overflow hoặc runtime exception. Dùng state fixtures và response archive ở server localhost riêng; không dùng các fixture đó làm bằng chứng dự đoán. [Báo cáo browser](reports/vietlott-ui-check-2026-11-07.json).
- Build Angular trước Maven, đóng gói lại JAR và đối chiếu từng asset. Còn cảnh báo CSS AI Lab vượt mức warning 10 kB, dưới error budget; không ngăn build.

XGBoost thật vẫn là hạng mục có điều kiện của kế hoạch: chưa thêm khi audit live chưa thực hiện được và baseline/replay chưa chứng minh lợi thế. Xác nhận tương lai chưa đăng ký mức effect/sample size/ngày review; manifest ghi `NOT_REGISTERED`, không coi các dashboard đang quan sát là kiểm định xác nhận. Cần khóa protocol đó sau khi có kiểm kê production và ứng viên đáng xác nhận. Hosting chưa được deploy; không tuyên bố đã tích lũy dữ liệu tương lai hoặc cải thiện xác suất trúng từ việc build/test đạt.

Tái tạo báo cáo fixture, từ `backend/`:

```powershell
mvn.cmd '-Dtest=PredictionStudyReportTest' '-Dprediction.study.report=docs/reports/prediction-study-2026-11-07.json' test
```

# Analyze Project

## Trang dự đoán có người dùng chọn vé

Trong `/analyze`, xem hai dãy thuật toán và bộ kết hợp; chỉnh trọng số/giữ/bỏ số trước khi chọn vé. Khu vực AI Lab có thể hiển thị 3–5 dãy đã lưu cho đúng loại/kỳ, theo quy tắc luân phiên mô hình; đây không phải thứ hạng xác suất trúng. Chọn gợi ý hoặc tự nhập, sửa sáu số rồi chốt danh sách trước 18:00 giờ Việt Nam. Tối đa 25 vé khác nhau mỗi bản chốt. Chốt là lưu lựa chọn trong ứng dụng, không phải mua vé.

Bản nháp lưu riêng theo loại trong trình duyệt; nếu đang giữ vé nháp cũ, bấm **Dùng gợi ý mới, bỏ nháp** để chuyển nguồn. Sau khi chốt vẫn có thể chỉnh và chốt revision mới trước cutoff; bản cuối có hiệu lực và các bản cũ còn lịch sử. Khu vực bên dưới hiển thị kết quả từng vé, vé tốt nhất, độ phủ các số khác nhau và đối chứng cùng ngân sách vé; có nút tải lại và đối soát. Tab lịch sử tự động cũ và trang AI Lab vẫn có ledger riêng.

Khi triển khai, Flyway chạy V13 tạo `prediction_suggestions`, `prediction_user_batches`, `prediction_user_settlements`. Không chuyển lịch sử dò vé cũ thành vé đã chốt trước kỳ. Backend kiểm tra độ mới của lịch sử khi chốt gợi ý; khoảng chờ 18:00–19:00 không đóng băng dự đoán kỳ sau bằng lịch sử thiếu kỳ liền trước. Vé tự nhập có thể chốt trước cutoff dù mô hình đang chờ.

| API mới | Công dụng |
| --- | --- |
| `POST /api/prediction-workspace/suggestions` | Tạo bản gợi ý bất biến cho category/algorithm/algorithm2 |
| `POST /api/prediction-workspace/suggestions/{id}/combine` | Tạo biến thể theo primaryWeight, keep, exclude trên cùng ranking |
| `GET /api/prediction-workspace/lab-suggestions?category=MEGA&targetDate=YYYY-MM-DD` | Đọc tối đa năm gợi ý Lab đúng kỳ, không sinh vé |
| `POST /api/prediction-workspace/commit` | Chốt danh sách với suggestionId, category, targetDate, expectedRevision, requestKey, labCount và tickets (sourceId/numbers) |
| `GET /api/prediction-workspace/history?category=MEGA` | Đọc các revision và đối soát vé người dùng |
| `POST /api/prediction-workspace/reconcile` | Đối soát; scheduler cũng kiểm tra mỗi phút |

Giữ nguyên requestKey và payload khi gửi lại sau lỗi mạng. HTTP 409 yêu cầu kiểm tra nguồn/kỳ/revision trước khi chốt lại; không tự đổi targetDate. Vé sửa vẫn liên kết về sourceId của bản gợi ý đang chốt. Chọn số dãy Lab 3–5 chỉ đổi số lựa chọn hiển thị; việc chọn/sửa không ghi ngược mô hình hoặc ledger Lab.

Kiểm thử PostgreSQL thật có thể bật bằng `WORKSPACE_TEST_JDBC` và `WORKSPACE_TEST_USER`, dùng database kiểm thử với tài khoản được tạo/xóa schema. Test tự tạo schema ngẫu nhiên, áp dụng V13 và dọn riêng schema đó. Không cấu hình bằng kết nối production. Nếu JDBC URL có query parameters, cấu hình một URL kiểm thử không có query trước khi chạy test. CI dùng PostgreSQL 16 tạm riêng.

Kế hoạch và báo cáo phát triển: [workspace](backend/docs/PREDICTION_WORKSPACE_PLAN.md), [so sánh cách kết hợp](backend/docs/reports/workspace-development-2026-10-28.md).

## Phòng thử nghiệm AI

Mở `/ai-lab` từ menu để theo dõi nhiều dãy dự đoán trước kỳ: Logistic Regression và tần suất suy giảm theo thời gian, mỗi mô hình 5 vé/kỳ. Backend tự chốt và đối soát mỗi phút khi đang chạy; trang tự cập nhật mỗi phút. Chỉ học trên kết quả hợp lệ trước kỳ đích, cần ít nhất 10 kỳ và kỳ hoàn tất mới nhất.

Khi triển khai bản mới, Flyway chạy migration V12 tạo ledger thử nghiệm riêng. Có thể đặt `AI_LAB_ENABLED=false` để tắt tạo vé mới; đối soát bản cũ vẫn hoạt động. `FORECAST_ENABLED` chỉ điều khiển ledger dự đoán chính.

Trang so sánh tỷ lệ khớp từ ba số của từng vé và từng kỳ với đối chứng cùng 5 vé. Mốc sàng lọc 60 kỳ và hai khối 30 kỳ là quy ước thử nghiệm; mô hình đạt chỉ được đánh dấu ứng viên cần xác nhận thêm, không tự thay thuật toán chính. Chưa có bằng chứng về lợi thế dự đoán.

Ứng dụng chọn bộ 6 số MEGA 6/45 và POWER 6/55 dựa trên thuật toán và thống kê; lưu dự đoán trước kỳ, đối soát kết quả và đánh giá trên các dự đoán đã lưu. Hiện chưa chứng minh được lợi thế dự đoán đáng tin cậy.

## Cấu trúc

- `frontend/`: Angular, giao diện `/analyze` gồm tab Tạo dự đoán và Lịch sử & đối soát.
- `backend/`: Spring Boot Java 17, PostgreSQL, Flyway và kiểm thử Java.
- `Dockerfile`, `render.yaml`: build/triển khai Angular cùng Spring Boot.
- `.github/`: hướng dẫn agent và skills; `.vscode/`: cấu hình IDE.
- `.env.example`: mẫu biến môi trường; `metadata.json`: metadata AI Studio được giữ lại.
- `backend/docs/reports/`: bằng chứng kiểm chứng lịch sử.
- `backend/archive/legacy-node-data/`: dữ liệu của ứng dụng Node/Vite cũ, giữ để đối chiếu, không tự nhập vào database.

Ứng dụng Node/Vite riêng ở root đã được loại bỏ. Không còn lệnh npm tại root; chạy npm trong `frontend/` và Maven trong `backend/`.

## Chạy và kiểm tra

Cài Node tương thích với toolchain Angular, Java 17+, Maven và PostgreSQL. Cấu hình kết nối bằng `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; tham khảo `.env.example` và `backend/src/main/resources/application.properties`. Spring không tự đọc file `.env`; cần đưa các giá trị vào môi trường chạy hoặc cấu hình IDE.

Build frontend:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run build
```

Từ root, kiểm thử và build backend:

```powershell
Set-Location backend
mvn.cmd install
java -jar target/analyzeproject-0.0.1-SNAPSHOT.jar
```

Build Angular trước Maven để profile `local-frontend-build` đóng gói giao diện mới vào JAR. Spring mặc định dùng cổng 8080, có thể cấu hình `PORT`. Để phát triển giao diện riêng, chạy `npm.cmd start` trong `frontend/` và cấu hình `frontend/src/environments/environment.ts` trỏ đến backend; cấu hình hiện tại gọi API cùng origin.

## Đồng bộ kết quả

Spring scheduler mặc định chạy sau khởi động 10 giây, sau đó mỗi 900 giây; đối soát 90 ngày gần nhất. Nguồn mặc định là feed cộng đồng `vietvudanh/vietlott-data`, không phải API chính thức. `power645.jsonl` là MEGA, `power655.jsonl` là POWER; phần tử thứ bảy của POWER là số phụ.

Có thêm lượt đồng bộ lúc **19:00 giờ Việt Nam** mỗi ngày khi backend đang hoạt động. Chỉ nhận kết quả của ngày hiện tại từ mốc này; nếu nguồn chưa có kết quả, hệ thống thử lại theo chu kỳ trên. Giờ quay/chốt dự đoán 18:00 được giữ nguyên.

Các biến chính: `LOTTERY_SYNC_ENABLED`, `LOTTERY_SYNC_INTERVAL_SECONDS`, `LOTTERY_SYNC_STARTUP_DELAY_SECONDS`, `LOTTERY_SYNC_LOOKBACK_DAYS`, `LOTTERY_SYNC_TIMEOUT_SECONDS`, `LOTTERY_SYNC_MEGA_URL`, `LOTTERY_SYNC_POWER_URL`.

Chỉ nhập ngày hiện tại sau 19:00 giờ Việt Nam; không nhập ngày tương lai. Dự đoán chỉ sử dụng revision mới nhất có chất lượng hợp lệ. Xung đột được cách ly, sửa đổi có audit; `SOURCE_MATCHED` chỉ có nghĩa khớp nguồn đã cấu hình.

- `GET /api/lottery-sync/status`: trạng thái lần đồng bộ, lỗi và độ mới của nguồn.
- `GET /api/lottery-sync/quality`: chất lượng dữ liệu và lịch sử đối soát.

## Lưu dự đoán và đánh giá

Mở `/analyze`, chọn tab **Lịch sử & đối soát** để xem các bộ số đã chốt và kết quả đo. Đường dẫn cũ `/forecasts` tự chuyển sang `/analyze/history`. Hệ thống kiểm tra sau khởi động 12 giây rồi mỗi 60 giây khi ứng dụng đang chạy; API dự đoán cũng thử lưu bộ chính đang hiển thị.

Bản đầu tiên theo loại/kỳ/thuật toán được giữ nguyên, kèm cấu hình, phiên bản, lịch sử và dấu SHA-256. Chốt trước 18:00 giờ Việt Nam; đối soát sau 19:00 khi có kết quả hợp lệ. Không sinh bù dự đoán quá khứ. Chỉ đo bộ số đã lưu trước kỳ, tách nhóm theo phiên bản/cấu hình và so sánh với tần suất nền, ngẫu nhiên. Không tự lưu mọi vé biến thể hoặc vé kết hợp.

| API | Chức năng |
| --- | --- |
| `GET /api/forecasts` | Chỉ đọc snapshot, sự kiện đối soát và trạng thái |
| `POST /api/forecasts/capture` | Thử lưu dự đoán kỳ tiếp theo và đối soát |
| `POST /api/forecasts/reconcile` | Đối soát bộ đã lưu, không tính lại dự đoán |
| `GET /api/forecasts/evaluation?category=MEGA` | Đánh giá snapshot đã lưu; bỏ category để xem cả hai loại |

Đặt `FORECAST_ENABLED=false` để tắt tạo snapshot mới. Dữ liệu hoạt động nằm trong PostgreSQL qua migration V11; việc di chuyển thư mục trong repository không thay đổi database đang chạy. Sáu snapshot JSON cũ đã được giữ trong thư mục archive, chưa chuyển vào sổ PostgreSQL.

Xem [hiện trạng và công việc tiếp theo](PROJECT_ANALYSIS.md) và [hướng dẫn triển khai](DEPLOY_RENDER.md).

Trang `/forecasts` dùng `GET /api/forecasts/overview?category=MEGA&page=0&size=50` để tải một trang gọn cùng thống kê. Các GET chỉ đọc, cache tối đa 10 giây; nút **Đối soát kết quả** hoặc scheduler mới chạy đối soát. Khi có tác vụ lưu/đối soát đang chạy, POST có thể trả `status: BUSY`; giao diện không tự gửi lại liên tục. Lịch sử đầu vào đầy đủ vẫn truy cập qua GET ledger để audit.

## Phân tích kỳ quay

Tab **Phân tích kỳ quay** tại `/analyze/review` đối chiếu vé đã chốt, vé đã dò và snapshot thuật toán/AI Lab. `/latest-analysis` chuyển tới tab này. Mở trang và đổi bộ lọc không tạo gợi ý dự đoán hoặc thay cấu hình; badge menu dùng API kết quả, không chạy backtest.

- `GET /api/analyze/draw-report?category=POWER&date=2026-10-29&algorithm=temporal_learning`: kết quả đủ chất lượng, bằng chứng trước kỳ, chi tiết số, các nhóm vé và đánh giá cùng ngân sách. Bỏ ngày để lấy kỳ mới nhất đủ điều kiện; `evidenceId` chọn đúng snapshot trong cùng loại/kỳ/thuật toán.
- Thuật toán gồm ba id trong catalog, `combined_ensemble`, `lab-logistic`, `lab-recency`. Snapshot cũ thiếu rank/score giữ trạng thái thiếu; không dựng lại bằng cấu hình hiện tại. Điểm mới lưu là điểm nội bộ, không phải xác suất trúng đã hiệu chuẩn.
- `POST /api/analyze/evaluate-tickets` với `{ "category": "POWER", "date": "2026-10-29", "tickets": [[8,15,29,33,43,49]] }`: chỉ dò từ 1–25 vé, không lưu. Mỗi vé đúng sáu số nguyên khác nhau; số phụ POWER được xét trong chính sáu số đó.
- `/official-draw-analysis` giữ adapter chỉ đọc cho client cũ. Xuất báo cáo không gọi `/update-algorithm`. API cập nhật cấu hình chỉ nhận patch các khóa scorer thực sự đọc, kiểm tra miền hợp lệ và giữ các phần cấu hình không thay đổi; báo cáo đối soát không được nhận làm tham số mô hình.

Snapshot mới lưu ranking/score từ chính lượt scoring đã tạo vé. Workspace giữ hai ranking, trọng số và giữ/bỏ số; Lab ghi rõ vé top sáu hay lấy mẫu có seed. Không sửa các snapshot cũ hoặc tạo lịch sử dự đoán bù. Xem [kế hoạch và giới hạn](backend/docs/ANALYZE_REPAIR_PLAN.md).

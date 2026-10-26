# Analyze Project

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

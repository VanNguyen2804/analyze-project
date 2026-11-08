# Analyze Project

Hiệu năng Stock/AI Lab và health check `/api/health`: xem [cấu hình, kiểm chứng và triển khai](backend/docs/PAGE_PERFORMANCE.md).

## Chứng khoán Việt Nam

Vị thế chỉ cần **giá vốn bình quân và số cổ phiếu nắm giữ**. Hệ thống tự lập kịch bản mua/bán từ nến đóng, hiển thị điều kiện và lượng/vốn mô phỏng; xem [công thức và giới hạn](backend/docs/STOCK_TWO_INPUT_SCENARIOS.md).

Mở `/stocks` hoặc `/stocks/FPT` để xem VIC, FPT, SZC, MBB: nến ngày/15 phút, volume, EMA20, VNINDEX, phân tích vị thế và mô hình học máy ba lớp mua/theo dõi/bán. MEGA/POWER và lịch xổ số được ẩn trên trang chứng khoán. Mô hình thực sự huấn luyện từ lịch sử, có kiểm định theo thời gian và đối chứng; chưa chứng minh lợi thế giao dịch.

Nguồn công khai hiện ở chế độ thử nghiệm, tín hiệu hành động phụ thuộc chất lượng dữ liệu. Vị thế tính chênh lệch giá tham khảo bằng VND, ghi rõ mốc giá và chưa tính phí/thuế/sự kiện quyền; giao diện không yêu cầu phí mua/bán. Xem [cấu hình, phương pháp và giới hạn](backend/docs/STOCKS_IMPLEMENTATION.md), [báo cáo dữ liệu thật](backend/docs/reports/stocks-development-2026-11-03.json). Không có thay đổi schema hoặc chức năng đặt lệnh.

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

## Thử nghiệm MEGA giảm trùng vé

Trang `/analyze` có vùng **Giảm trùng vé** khi chọn MEGA. Phiên bản `mega-portfolio-v2` chọn ba vé bổ sung từ danh mục giảm trùng, loại các bộ giống ba gợi ý gốc và snapshot mô hình/AI Lab đã lưu trước kỳ cho cùng loại/ngày. So sánh bộ số sau khi sắp tăng dần, không phụ thuộc thứ tự nhập; mỗi cặp vé thử nghiệm vẫn trùng tối đa ba số. Dùng `temporal_learning` với tham số mặc định đóng băng theo phiên bản code. Phiên bản v1 từng giữ lại vé top sáu, nên có thể trùng bộ gốc; lịch sử v1 được giữ nguyên và đo riêng. Chưa có bằng chứng phiên bản mới tăng khả năng trúng.

Mỗi kỳ chốt nguyên khối bốn nhóm, mỗi nhóm ba vé: giảm trùng, ba vé đầu của cách ghép cũ trên cùng ranking, tần suất + giảm trùng, và ngẫu nhiên đều có seed cố định theo phiên bản/loại/kỳ. Snapshot giữ ranking, cấu hình, toàn bộ lịch sử đầu vào và bộ vé của mọi nhóm. Scheduler kiểm tra mỗi phút, cần ít nhất mười kỳ hợp lệ và kết quả kỳ liền trước; chốt trước 18:00 và đối soát từ 19:00 giờ Việt Nam. Retry và nhiều instance cùng chốt vẫn giữ bản đầu tiên. Không sinh bù các kỳ đã quay.

`GET /api/mega-portfolio?page=0` chỉ đọc; `POST /api/mega-portfolio/run` chủ động kiểm tra chốt kỳ mới và đối soát. UI mở trang chỉ GET, có nút chạy riêng và lịch sử phân trang mười bản chốt. Trước khi hiện hoặc sao chép, UI còn đối chiếu với gợi ý thực đang hiển thị, gồm bộ kết hợp đã chỉnh và Lab đúng kỳ; bộ trùng được ẩn kèm thông báo, không tự dựng vé thay thế hoặc sửa snapshot. Chờ gợi ý gốc khi đổi thuật toán/kỳ để tránh hiện vé trùng trong lúc tải. Vì vậy số vé bổ sung hiển thị có thể ít hơn ba, còn thống kê vẫn tính đúng ba vé đã chốt và lịch sử giữ đầy đủ.

Thống kê tách theo phiên bản gồm số kỳ có vé khớp ≥3, số khớp TB/vé và độ phủ số kết quả khác nhau, với cùng tập kỳ/cùng ngân sách cho mọi nhóm. Kết quả sửa/cách ly được đối soát lại và loại khỏi thống kê khi chưa hợp lệ. Chưa có kết quả dùng trạng thái thiếu dữ liệu; không tự áp dụng thử nghiệm vào thuật toán chính.

Flyway V14 tạo `mega_portfolio_snapshots` và `mega_portfolio_settlements`, tách khỏi AI Lab và vé người dùng. V15 thêm ràng buộc theo kỳ/phiên bản: v2 có thể chốt riêng cho kỳ tương lai đã có v1, không ghi đè dữ liệu cũ và không chốt bù quá khứ. Mỗi kỳ/phiên bản giữ bản đầu tiên. `MEGA_PORTFOLIO_ENABLED=false` tắt chốt mới nhưng vẫn đối soát bản cũ. Kiểm thử PostgreSQL thật dùng cùng biến `WORKSPACE_TEST_JDBC` / `WORKSPACE_TEST_USER`, tạo schema riêng và kiểm tra nâng cấp V14 → V15. Chỉ các kỳ được chốt sau khi triển khai mới tạo dữ liệu thử nghiệm tương lai.

## Phòng thử nghiệm AI

Bản nâng cấp 07/11 thêm tần suất làm trơn và Logistic ghép vé theo độ phủ, mỗi mô hình năm vé/kỳ, giữ riêng bằng chứng theo phiên bản. Có thể tắt chốt mới của hai ứng viên bằng `AI_LAB_CHALLENGERS_ENABLED=false`; bản cũ vẫn được đối soát. Tab lịch sử có bộ lọc ngày/phiên bản, các mức ≥3/≥4/≥5 và phần kiểm kê/benchmark chạy thủ công. Xem [hướng dẫn, API, kết quả và giới hạn](backend/docs/VIETLOTT_RESEARCH_IMPLEMENTATION.md).

Mở `/ai-lab` từ menu để theo dõi nhiều dãy dự đoán trước kỳ: Logistic Regression và tần suất suy giảm theo thời gian, mỗi mô hình 5 vé/kỳ. Backend tự chốt và đối soát mỗi phút khi đang chạy; trang tự cập nhật mỗi phút. Chỉ học trên kết quả hợp lệ trước kỳ đích, cần ít nhất 10 kỳ và kỳ hoàn tất mới nhất.

Khi triển khai bản mới, Flyway chạy migration V12 tạo ledger thử nghiệm riêng. Có thể đặt `AI_LAB_ENABLED=false` để tắt tạo vé mới; đối soát bản cũ vẫn hoạt động. `FORECAST_ENABLED` chỉ điều khiển ledger dự đoán chính.

Trang theo dõi từ vé khớp 1–2 số: trung bình số khớp/vé, tổng lượt khớp, histogram 0–6 và các mức ≥1/≥2/≥3 so với đối chứng cùng năm vé. Logistic `ai-lab-dense-feedback-v2` cập nhật trọng số từ phản hồi từng số đúng/sai của các vé chốt trước kỳ; không chờ khớp ba số. Tổng lượt khớp qua nhiều vé/kỳ không phải một vé trúng. Xem [phương pháp và cấu hình phản hồi](backend/docs/AI_LAB_PROGRESSIVE_LEARNING.md).

Mốc sàng lọc ≥3, 60 kỳ và hai khối 30 kỳ vẫn là quy ước thử nghiệm ở phần chi tiết; ghi nhận khớp 1–2 số không tự nâng mô hình thành ứng viên hoặc đổi thuật toán chính. Các phiên bản cũ được giữ riêng. Chưa có bằng chứng bước học mới tăng khả năng trúng.

Ứng dụng chọn bộ 6 số MEGA 6/45 và POWER 6/55 dựa trên thuật toán và thống kê; lưu dự đoán trước kỳ, đối soát kết quả và đánh giá trên các dự đoán đã lưu. Hiện chưa chứng minh được lợi thế dự đoán đáng tin cậy.

## Cấu trúc

- `frontend/`: Angular, giao diện `/analyze` gồm tab Tạo dự đoán và Lịch sử & đối soát.
- `backend/`: Spring Boot Java 17, PostgreSQL, Flyway và kiểm thử Java.
- Backend tổ chức theo Onion Architecture: `presentation → application → domain`, `infrastructure → application`; xem [bản đồ và quy tắc phụ thuộc](backend/docs/ONION_ARCHITECTURE.md).
- `render.yaml`: Angular Static Site và Spring Boot Web Service độc lập; `backend/Dockerfile` build API. Dockerfile gốc giữ chế độ chạy chung tùy chọn.
- `.github/`: hướng dẫn agent và skills; `.vscode/`: cấu hình IDE.
- `.env.example`: mẫu biến môi trường; `metadata.json`: metadata AI Studio được giữ lại.
- `backend/docs/reports/`: bằng chứng kiểm chứng lịch sử.
- `backend/archive/legacy-node-data/`: dữ liệu của ứng dụng Node/Vite cũ, giữ để đối chiếu, không tự nhập vào database.

Ứng dụng Node/Vite riêng ở root đã được loại bỏ. Không còn lệnh npm tại root; chạy npm trong `frontend/` và Maven trong `backend/`.

## Triển khai Render riêng frontend/backend

Xem [hướng dẫn chuyển đổi từng bước trên Render](backend/docs/RENDER_SPLIT_DEPLOYMENT.md): frontend dùng Root Directory `frontend`, Web Service hiện có dùng `backend`, cấu hình API_BASE_URL/CORS và rewrite SPA. Static Site phục vụ `/landing` độc lập với cold start backend.

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

Maven mặc định build API độc lập, không đóng gói Angular. Chế độ chạy chung chỉ bật khi chủ động dùng `-Pbundle-frontend` và `APP_FRONTEND_ENABLED=true`. Spring mặc định dùng cổng 8080, có thể cấu hình `PORT`. Để phát triển giao diện riêng, chạy `npm.cmd start` trong `frontend/` và cấu hình `frontend/src/environments/environment.ts` trỏ đến backend; cấu hình hiện tại gọi API cùng origin.

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

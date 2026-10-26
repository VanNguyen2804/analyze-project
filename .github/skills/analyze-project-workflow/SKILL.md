---
name: analyze-project-workflow
description: Analyze and refactor analyze-project, replace hard-coded prediction behavior, update lottery algorithms, and verify Angular/Spring Boot. Use for project analysis, prediction bugs, backtesting, scoring configuration, and related refactors; keep unrelated tasks within their original scope.
---

# Quy trình phân tích và cập nhật analyze-project

Đọc [quy ước dự án](../../SKILL.md), [hướng dẫn agent](../../AGENT.md) và [phân tích hiện tại](../../../PROJECT_ANALYSIS.md) khi bắt đầu task liên quan. Xác minh lại với code vì tài liệu có thể cũ. Yêu cầu hiện tại của người dùng quyết định phạm vi công việc.

## Bản đồ dự án

| Stack | Điểm bắt đầu | Dữ liệu / kiểm thử |
| --- | --- | --- |
| Spring Boot | `backend/src/main/java/com/example/analyzeproject/service/AnalyzeService.java`, `PredictionSupport.java` | PostgreSQL, `backend/src/test/` |
| Angular | `frontend/src/app/feature/prediction/`, `frontend/src/app/core/models/` và các service | `frontend/package.json`, `frontend/angular.json` |

Các đường dẫn code tính từ root repo; chạy lệnh build trong đúng thư mục frontend/backend. Docker ở root build Angular và Spring Boot. Ứng dụng Node/Vite ở root đã bị loại bỏ theo yêu cầu người dùng. Dữ liệu cũ được giữ tại `backend/archive/legacy-node-data/`, không tự nhập vào PostgreSQL; báo cáo lịch sử tại `backend/docs/reports/`.

## Phân tích và chọn cách thay hard-code

- Xem `git status` và giữ nguyên các thay đổi hiện có của người dùng. Lần theo request → history / cấu hình → feature → ranking → sinh vé → DTO → UI và reconciliation.
- Phân biệt luật nghiệp vụ với dữ liệu bị đóng đinh: miền MEGA 45 / POWER 55, vé sáu số và lịch quay là luật hiện có; dãy dự đoán cố định, ngày cũ, tỷ lệ thành công preset cần dữ liệu, cấu hình hoặc trạng thái thiếu dữ liệu để thay thế.
- Đưa hệ số điều chỉnh vào cấu hình có kiểm tra kiểu, giá trị hữu hạn, miền hợp lệ và fallback rõ ràng. Không chỉ đổi magic number thành một hằng số khác rồi coi là đã sửa nghiệp vụ.
- Với thay đổi dùng chung, kiểm tra hợp đồng API giữa Spring Boot và Angular cùng fallback của UI.
- Không sửa migration Flyway lịch sử hay nạp dữ liệu mẫu thành dữ liệu thật để kiểm thử refactor. Giữ nguyên quy ước UI trong `.github/SKILL.md`; không mở rộng task thành đổi thiết kế ngoài yêu cầu.

## Bất biến thuật toán

- Custom history rỗng giữ nguyên ý nghĩa rỗng, không fallback sang dữ liệu live hoặc tạo vé giả.
- Lọc đúng category, kiểm tra sáu số nguyên khác nhau trong miền hợp lệ, sắp history theo thời gian và chỉ tính một revision của mỗi kỳ; ưu tiên revision mới nhất theo metadata hiện có.
- Backtest kỳ D chỉ dùng các kỳ trước D, kể cả khi không có kỳ nào trước đó. Đánh giá đúng vé sáu số được chọn, không lấy sáu số đầu của pool đã sắp tăng dần.
- Backtest dùng tham số hiện tại chưa tái hiện cấu hình lịch sử. Chỉ khẳng định tái lập lịch sử khi có snapshot dự đoán và phiên bản tham số tương ứng trước kỳ quay.
- Cùng history, category, thuật toán và cấu hình phải tái lập được ranking. Bỏ random jitter trong scoring; mô phỏng cần RNG có seed từ đầu vào ổn định.
- Thuật toán thứ hai phải thực sự chạy trên cùng snapshot history. Kết hợp hai ranking bằng trọng số rõ ràng, tie-break ổn định, không chèn số được chọn tay. Helper hiện dùng tổng `1 / rank` với trọng số bằng nhau.
- Tái sử dụng feature đã tính khi có cùng định nghĩa để tránh quét lại toàn bộ history cho từng candidate; kiểm chứng nếu thay đổi ý nghĩa feature.
- Vé đủ sáu số khác nhau và trong miền hợp lệ. Số phụ POWER của mỗi vé không trùng các số chính của chính vé đó.
- Tính lịch theo giờ Việt Nam và kiểm tra cutoff 18:00, đổi tháng / năm, ngày mới nhất trong dữ liệu nằm sau ngày hiện tại. Giữ luật cutoff hiện hành trừ khi người dùng yêu cầu thay đổi.
- Thống kê dùng đúng mẫu và mẫu số. Độ chính xác chưa đo dùng `null` / `N/A`, không điền phần trăm preset. Trường `probabilityPercent` hiện có thể là điểm heuristic, không phải xác suất trúng đã hiệu chuẩn.
- UI không tự tạo vé, ngày, kết quả trúng hoặc độ tin cậy để bù trường API thiếu. Chi tiết thuật toán thứ hai lấy từ chính kết quả thứ hai.
- Không xem tên XGBoost / Deep Stacking / AI là bằng chứng đã huấn luyện mô hình. Phân biệt heuristic, mô phỏng và mô hình thực sự được huấn luyện.

## Kiểm chứng

Thay đổi thuật toán cần regression test cho hành vi quan sát được: history thay đổi / đảo thứ tự / rỗng, duplicate và input sai, lựa chọn thuật toán thứ hai, rank fusion đối xứng, số phụ không trùng, tính tái lập, thống kê từ dữ liệu và kỳ backtest đầu tiên.

```powershell
# Tại backend/ — kiểm thử và build Spring Boot
mvn test
mvn clean install

# Tại frontend/ — build Angular
npm run build
```

Với thay đổi code ứng dụng, build riêng Angular và Spring Boot trước khi kết luận hoàn tất theo quy ước dự án. Thay đổi chỉ tài liệu / skill thì kiểm tra frontmatter, nội dung, liên kết và diff, không cần build ứng dụng.

Kiểm tra Node thực tế và `engines` của toolchain trước khi build Angular. Lần kiểm tra trước dùng Node 24 vì Node mặc định 20.9 không đáp ứng dependencies Angular; đây là ghi nhận môi trường, không phải yêu cầu pin vĩnh viễn. Không tự nâng dependencies để né lỗi môi trường. Với PowerShell, tránh shell glob test mà Node đang dùng không hỗ trợ và tránh lệnh xóa kiểu Unix.

Không gọi unit test có mock là kiểm thử PostgreSQL thật. Nếu không chạy được check, ghi rõ check, nguyên nhân và phần chưa xác minh.

Khi học liên kết giữa các kỳ, không coi hai bản ghi liền nhau là hai kỳ quay liên tiếp nếu thiếu kỳ ở giữa theo lịch. So sánh mô hình học với phương pháp nền trên cùng cửa sổ kiểm tra; phân biệt số khớp trong một vé và tổng độ phủ nhiều vé. Vé người dùng cung cấp sau kỳ quay chỉ dùng đối chiếu, không làm bằng chứng dự đoán trước kỳ. Với dữ liệu nhập mới, kiểm tra nguồn, trùng dữ liệu và xung đột; tạo migration mới thay vì sửa migration lịch sử. Khi build JAR sau Angular, kiểm tra JAR chứa đúng assets vừa build.

## Kết thúc và ghi nhận

Tóm tắt thay đổi, lý do, checks thực sự đã chạy và hạn chế còn lại bằng ngôn ngữ người dùng. Cập nhật `PROJECT_ANALYSIS.md` khi kiến trúc, hành vi thuật toán hoặc phát hiện quan trọng thay đổi. Chỉ cập nhật skill khi có quy ước được chấp nhận hoặc bài học có bằng chứng, không lưu log từng phiên.

Không suy ra cải thiện xác suất trúng từ việc sửa code, tăng điểm sigmoid, đổi seed hoặc đạt test. Nếu đánh giá thực nghiệm, nêu dữ liệu, cách chia theo thời gian, tham số và metric thực sự đã đo.

## Nguồn kết quả và scheduler

Khi làm sạch dữ liệu theo yêu cầu người dùng, dùng `DrawQuality`: sửa đổi phải có audit trước/sau; chỉ áp dụng sửa số chính đã đối chiếu trong `reviewed_draw_corrections.json`, hoặc điền số phụ thiếu khi sáu số chính khớp. Các xung đột chưa xác nhận được cách ly. Dự đoán/backtest dùng revision mới nhất đủ điều kiện, không fallback sang revision cũ khi revision mới chưa được xác minh. Script V10 chèn dữ liệu cũ đã bị bỏ; migration schema audit hiện mang tên `V10__add_draw_quality_audit.sql`, tiếp nối V9 theo yêu cầu người dùng, không chèn lại dữ liệu kỳ quay. Không coi `SOURCE_MATCHED` từ feed cộng đồng là xác minh chính thức của Vietlott.

Khi cần kết quả kỳ mới, kiểm tra feed và trạng thái đồng bộ trước khi yêu cầu người dùng nhập tay. Spring dùng `backend/src/main/java/com/example/analyzeproject/sync/`; cấu hình và cách chạy trong `README.md`. Nguồn mặc định là `vietvudanh/vietlott-data`: `power645.jsonl` tương ứng MEGA, `power655.jsonl` tương ứng POWER, phần tử thứ bảy là số phụ. Không gọi nguồn cộng đồng là API chính thức. Kiểm tra ngày, giờ Việt Nam, độ mới, kỳ thiếu, trùng và xung đột; không ghi đè lịch sử khác nguồn. Phân biệt cutoff nhận kết quả 18:30 với cutoff dự đoán 18:00. Spring scheduler dùng transaction và advisory lock. Khi nhập thêm dữ liệu, báo cáo backtest cũ là snapshot của lịch sử cũ, không tiếp tục gọi đó là metric hiện tại.

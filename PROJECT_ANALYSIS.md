# Hiện trạng và hướng phát triển dự án

## Cập nhật 27/10/2026 — Nhận kết quả từ 19:00

Đổi mốc tiếp nhận kết quả ngày hiện tại, xác định kỳ hoàn tất mới nhất và đối soát snapshot từ 19:00 giờ Việt Nam. Trước 19:00, kết quả của hôm nay chưa được nhập từ feed hoặc đưa vào lịch sử huấn luyện/đối soát; đúng 19:00 mới đủ điều kiện nếu đã có kết quả hợp lệ. Áp dụng chung cho ledger dự đoán chính và AI lab qua ForecastProtocol. Giữ giờ quay và cutoff chốt dự đoán 18:00.

Scheduler kết quả có thêm cron 19:00 theo Asia/Ho_Chi_Minh, đồng thời giữ lượt khởi động và chu kỳ thử lại 900 giây. Backend phải hoạt động và feed phải có dữ liệu; mốc này không bảo đảm nguồn bên ngoài trả kết quả ngay lập tức. Đã cập nhật nhãn giao diện và hướng dẫn hiện hành; không sửa báo cáo/migration lịch sử hoặc dữ liệu production.

Kiểm chứng: 44 kiểm thử Java và 22 kiểm thử frontend đạt; production build và Maven install thành công; 20 asset Angular trong JAR khớp SHA-256. Kiểm thử xác nhận 18:59:59 chưa nhận/đối soát kết quả hôm nay, đúng 19:00 mới cho phép; kiểm tra cả MEGA/POWER và cùng thời điểm biểu diễn theo UTC. Chưa triển khai production.

## Cập nhật 27/10/2026 — Tách component và style frontend

- Tách 8 component con: PredictionChart, LinkageTimeline, GapPressure, SpacingDistribution, TicketMixer, DrawReconciliation, ReconciliationDetail và HyperparameterHistory. Mỗi component có TS/HTML/CSS riêng; trang cha giữ điều phối dự đoán, chẩn đoán và cập nhật cấu hình chung.
- Template dự đoán giảm từ khoảng 2.373 xuống 1.434 dòng; template phân tích kỳ mới nhất từ khoảng 2.934 xuống 1.005 dòng. Các phần hiển thị chi tiết nhiều tab vẫn có template dài; chúng đã có phạm vi dữ liệu và trách nhiệm riêng, không đặt ngưỡng số dòng cứng cho mọi component.
- Chuyển các thẻ style nhúng của Entry/LatestDrawAnalysis sang CSS riêng; thêm header stylesheet và chuyển style tĩnh của bộ ghép vé/hộp xem cấu hình. Style dùng chung theo feature được import trong stylesheet của component sở hữu DOM, giữ encapsulation.
- Biểu đồ defer từng chế độ khi mở lần đầu và giữ trạng thái bộ lọc khi chuyển qua lại. Tab chi tiết đối soát dùng model để giữ lựa chọn trong lúc tải lại. Request lịch sử/đối soát/khuyến nghị hủy lượt cũ khi thay lựa chọn và hủy khi component bị destroy.
- Bổ sung kiểm thử component cho lựa chọn/copy vé, xem và kích hoạt cấu hình, hủy request, sự kiện phiên bản, giữ bộ lọc khi đổi chế độ và style thực tế sau khi tách. Không thay thuật toán dự đoán hoặc dữ liệu production.

Kiểm chứng: format:check/typecheck đạt; 22 kiểm thử ChromeHeadless đạt; production build thành công không có cảnh báo template. Backend `mvn clean install` đạt với 43 kiểm thử Java. Đối chiếu SHA-256 của 20 asset trong JAR/static fallback đều khớp, không còn asset cũ. Kiểm tra style qua computed style trong browser tests, chưa thực hiện kiểm tra ảnh chụp hoặc triển khai production.

## Cập nhật 27/10/2026 — Chuẩn hóa Angular 22

- Chạy migration chính thức của Angular cho control flow, `inject`, lazy routes và import standalone. Template dùng `@if/@for`, không còn `*ngIf/*ngFor`; bỏ constructor injection và quản lý Subscription/OnDestroy thủ công trong frontend. Component dùng OnPush rõ ràng.
- Header/menu, lịch sử dự đoán và AI lab dùng signals; số liệu dẫn xuất dùng computed. CategoryService là nguồn MEGA/POWER duy nhất, giữ observable bridge tương thích cho phần phân tích/form cũ.
- Thêm DTO thuật toán/lịch sử/AI lab, ForecastApiService; tách phép tính biểu đồ khỏi PredictionComponent thành hàm thuần có kiểm thử. Tách template/style inline của workspace và menu; chuẩn hóa format toàn frontend. Các DTO phân tích động và form cũ chưa được chuyển hết khỏi `any`/field thường.
- Chuyển application/dev-server/Karma sang `@angular/build`; bỏ dependency builder Webpack, thêm Prettier và các lệnh format/typecheck/test:ci. Bật noUnusedLocals/noUnusedParameters và class-field semantics chuẩn; thay baseUrl bằng alias @app. Giữ Zone.js và FormsModule cho các luồng cũ, không thay thuật toán backend.
- Giữ output `frontend/dist/analyzeproject` để Maven/Docker/CI tiếp tục đóng gói; build:sync dùng script Node thay lệnh rm/cp chỉ chạy trên Unix. Docker dùng Node 24 và npm ci theo lockfile. CI chạy toàn bộ frontend suite thay vì chỉ chọn vài file.
- Runtime/compiler/CLI/builder Angular được chốt đồng bộ 22.2.0. Gói tải ban đầu trong lần build local cuối sau chuyển đổi khoảng 364 kB, trước đó khoảng 747 kB; phần còn lại tải qua lazy chunks. Đây là số liệu bundle build, không phải đo thời gian tải production.

Quy ước và lệnh phát triển: [frontend/README.md](frontend/README.md).

Kiểm chứng cuối đợt: format:check và typecheck đạt; toàn bộ 16 kiểm thử ChromeHeadless đạt; Angular production build không còn cảnh báo template/builder deprecated. `mvn clean install` đạt với 43 kiểm thử Java; đối chiếu SHA-256 đủ 16 asset (bao gồm lazy chunks) trong JAR và static fallback, không còn asset build cũ. Backend vẫn có cảnh báo unchecked Java có sẵn. Chưa chạy workflow trên GitHub, build Docker hoặc triển khai production trong đợt này.

## Cập nhật 27/10/2026 — Gỡ chức năng học tiếng Pháp

Đã xóa component học tiếng Pháp (TS/HTML/CSS), controller, hai entity và hai repository Java; gỡ API tiếng Pháp khỏi service dùng chung, constructor/test fixture, route `/french`, liên kết header và khóa dịch riêng của chức năng. Route mặc định và route không xác định chuyển sang `/analyze`. Đồng bộ lại static assets của backend để loại bỏ bundle cũ chứa trang đã xóa.

Giữ tính năng đổi ngôn ngữ giao diện vi/en/fr. Migration V1 được giữ nguyên vì chứa schema/seed lịch sử của toàn dự án; các bảng tiếng Pháp cũ chưa bị xóa khỏi database và không còn được mã ứng dụng sử dụng. Không thay đổi dữ liệu production.

Kiểm chứng sau khi gỡ: Angular production build và 12 kiểm thử ChromeHeadless đạt; backend `mvn clean install` đạt với 43 kiểm thử Java. Không còn tham chiếu chức năng tiếng Pháp trong mã ứng dụng/static assets; JAR không còn các class French và chứa đúng 9 asset Angular mới.

## Cập nhật 27/10/2026 — Phòng thử nghiệm AI

- Thêm trang `/ai-lab` và menu **Phòng thử nghiệm AI**, độc lập với trang dự đoán chính. Mỗi loại có hai mô hình: Logistic Regression thực sự học trọng số từ nhãn quá khứ và tần suất suy giảm theo thời gian (phương pháp thống kê). Không dùng LLM hoặc API AI bên ngoài.
- Mỗi mô hình chốt 5 vé khác nhau/kỳ, gồm vé đứng đầu và các vé lấy mẫu có trọng số với seed cố định. Đối chứng tần suất và ngẫu nhiên cùng có 5 vé, được lưu cùng thời điểm; hai mô hình dùng chung các vé đối chứng cho cùng loại/kỳ.
- Logistic dùng frequency toàn lịch sử, recent10, decay có bán rã 10 kỳ quan sát, gap tối đa 30 kỳ; học full-batch 80 epoch, learning rate 0.2, L2 0.05. Đặc trưng huấn luyện được tính trước khi tiêu thụ kết quả kỳ tương ứng. Cần ít nhất 10 kỳ hợp lệ và kỳ hoàn tất mới nhất để chốt; dùng sáu số chính, không đánh giá số phụ POWER.
- Scheduler chạy sau 20 giây rồi mỗi 60 giây khi backend hoạt động; kiểm tra lưu bản đầu tiên và đối soát. Cutoff 18:00, công bố kết quả 19:00 theo giờ Việt Nam. Không sinh bù quá khứ; bản cũ không bị ghi đè. Transaction lưu cả nhóm 5 vé và khóa đối soát dùng riêng cho phòng thử nghiệm.
- Migration mới `V12__add_ai_lab.sql` tạo `ai_lab_snapshots`, `ai_lab_settlements`, tách khỏi ledger chính. `AI_LAB_ENABLED=false` tắt tạo mới nhưng vẫn đối soát bản cũ. `FORECAST_ENABLED` điều khiển riêng trang dự đoán chính.
- API `GET /api/ai-lab?category=MEGA&page=0` chỉ đọc, phân trang 50 vé; `POST /api/ai-lab/run` kiểm tra chốt hai loại và đối soát. Trang tự tải lại mỗi phút, hủy request cũ khi đổi loại/rời trang và chặn gửi POST trùng.
- Báo cáo tách theo loại/mô hình/phiên bản mã/cấu hình, dùng settlement mới nhất; chỉ đo kỳ đủ 5 vé hợp lệ và đã đối soát toàn bộ. Hiển thị tỷ lệ từng vé đạt ≥3 và tỷ lệ kỳ có ít nhất một vé đạt ≥3, cùng hai đối chứng có cùng ngân sách vé.
- Quy tắc sàng lọc v1: ít nhất 60 kỳ đã đo, 60 kỳ gần nhất liên tiếp theo lịch; cả hai khối 30 kỳ gần nhất có tỷ lệ kỳ đạt ≥3 từ 50%; cận dưới chênh lệch ghép cặp 95% xấp xỉ trên toàn nhóm lớn hơn 0 với cả hai đối chứng. Đây là quy ước thử nghiệm, chưa được hiệu chỉnh cho việc xem lặp lại/nhiều mô hình hoặc phụ thuộc theo thời gian. Nhãn `VALIDATION_CANDIDATE` yêu cầu xác nhận trên các kỳ tương lai mới, không tự áp dụng mô hình vào dự đoán chính.
- Chưa có bằng chứng hiệu quả thực tế, chưa triển khai production hoặc chạy migration V12 trên database thật trong đợt này. Ưu tiên phát triển chuyển sang học máy và thực nghiệm theo yêu cầu người dùng; không mở rộng hạng mục kiểm thử PostgreSQL.
- Kiểm chứng local: Angular production build đạt; 12 kiểm thử ChromeHeadless cho workspace/history/AI lab đạt; `mvn install` đạt với 43 kiểm thử Java, gồm kiểm tra học máy, ngân sách vé, giữ bản đã chốt, loại dữ liệu tương lai, thống kê theo kỳ, điều kiện ứng viên và route/API qua MockMvc. Đối chiếu SHA-256 của 9 asset Angular trong JAR đều khớp; JAR chứa mô hình và migration V12. Còn các cảnh báo template/deprecation Angular và unchecked Java có sẵn. MockMvc/mock repository không phải kiểm thử database thật.

Kế hoạch tiếp theo: [lộ trình phát triển và tiêu chí nghiệm thu](backend/docs/DEVELOPMENT_PLAN.md). Đợt 26/10/2026 bổ sung workflow CI kiểm thử hai feature Angular, Java và đối chiếu asset trong JAR; chưa bao gồm PostgreSQL thật hoặc xác nhận workflow chạy trên GitHub.

Cập nhật ngày **26/10/2026**, sau khi dọn cấu trúc theo yêu cầu chỉ giữ frontend và backend. Tài liệu phản ánh repository, không xác nhận phiên bản hoặc dữ liệu production.

## 1. Mục tiêu

Xác định và đề xuất bộ **6 số chính Vietlott** bằng thuật toán và thống kê cho MEGA 6/45, POWER 6/55. Số phụ POWER xử lý riêng. Đánh giá bằng số chính khớp, tỷ lệ từ 3 số và so sánh phương pháp nền; không dùng điểm heuristic như xác suất trúng đã được hiệu chuẩn.

**Chưa có bằng chứng đáng tin cậy về lợi thế dự đoán.** Việc sửa code, đạt kiểm thử hoặc tăng số khớp trên mẫu phát triển không tự chứng minh hiệu quả tương lai.

## 2. Cấu trúc hiện tại

| Thành phần | Vị trí | Vai trò |
| --- | --- | --- |
| Angular | `frontend/` | Giao diện dự đoán, nhập kết quả, phân tích, `/forecasts` |
| Spring Boot Java 17 | `backend/` | API, thuật toán, đồng bộ kết quả và lưu dự đoán |
| PostgreSQL / Flyway | `backend/src/main/resources/db/migration/` | Lưu dữ liệu hoạt động, schema hiện tại đến V11 |
| Triển khai | `Dockerfile`, `render.yaml`, `DEPLOY_RENDER.md` | Build Angular rồi đóng gói cùng Spring Boot |
| Cấu hình môi trường / IDE | `.env.example`, `.vscode/` | Giữ nguyên khi dọn dự án |
| AI / agents / skills | `metadata.json`, `.github/` | Giữ lại; workflow cập nhật theo cấu trúc mới |
| Báo cáo lịch sử | `backend/docs/reports/` | Bằng chứng của các lần kiểm tra trước |
| Dữ liệu ứng dụng cũ | `backend/archive/legacy-node-data/` | Lưu đối chiếu, không tự nhập vào PostgreSQL |

Đã xóa ứng dụng Node/Vite độc lập ở root: `server.ts`, `src/`, `tests/`, `scripts/`, package/lockfile gốc, cấu hình Vite/TypeScript gốc, `index.html`, dependency/build/cache/thử nghiệm gốc. Không xóa mã trong frontend/backend hoặc sửa dữ liệu production.

Không còn lệnh `npm test`, `npm run dev` hay `npm run build` ở root. Chạy npm trong `frontend/`; chạy Maven trong `backend/`. Frontend có cấu hình TypeScript, package và lockfile riêng, không phụ thuộc cấu hình root đã xóa.

Build Angular trước Maven để profile `local-frontend-build` lấy giao diện từ `frontend/dist/analyzeproject`. Docker cũng build hai phần riêng và dùng PostgreSQL. Tài liệu vận hành chính: [README](README.md).

## 3. Dữ liệu và chất lượng

Tài nguyên seed nằm trong `backend/src/main/resources/data/`; dữ liệu hoạt động của Spring nằm trong PostgreSQL. Seed lịch sử Flyway, JSON và database đang chạy có thể khác nhau; phải kiểm tra sau đồng bộ, không suy ra số lượng production từ JSON.

Snapshot JSON sau làm sạch có 79 bản ghi: 39 MEGA hợp lệ, 39 POWER hợp lệ và một POWER 28/09/2026 bị cách ly. Đã sửa 4 kỳ có số chính sai và điền 11 số phụ thiếu, có audit trước/sau cùng nguồn đối chiếu. Đây là số liệu của bộ JSON đã kiểm tra, không phải truy vấn production hiện tại.

- `UNVERIFIED`: chưa đủ xác minh; `SOURCE_MATCHED`: khớp nguồn cấu hình; `QUARANTINED`: cách ly.
- Chỉ revision mới nhất đủ điều kiện, dấu nhận diện giá trị còn khớp mới được dùng cho dự đoán, thống kê, backtest và dò vé.
- Sửa tay ngày/loại/số làm mất điều kiện cho đến khi xác minh lại; không fallback sang revision cũ hợp lệ.
- Sửa số chính chỉ theo danh sách `reviewed_draw_corrections.json`; chỉ điền số phụ thiếu khi cả 6 số chính khớp. Không tự đổi ngày hoặc xóa xung đột.
- `SOURCE_MATCHED` với feed cộng đồng không phải chứng nhận độc lập của Vietlott.

Nguồn mặc định: `vietvudanh/vietlott-data`, không cần API key; `power645.jsonl` là MEGA, `power655.jsonl` là POWER. Scheduler chờ 10 giây sau khởi động, sau đó 900 giây giữa các lần hoàn tất; mặc định đối soát 90 ngày. Chỉ nhận ngày hiện tại sau 19:00 giờ Việt Nam, không nhận ngày tương lai. Có transaction/advisory lock và ghi audit; lỗi từng loại được xử lý độc lập.

`GET /api/lottery-sync/status` trả trạng thái trong bộ nhớ; `GET /api/lottery-sync/quality` trả chất lượng và audit đã lưu. Tăng `LOTTERY_SYNC_LOOKBACK_DAYS` khi cần lịch sử dài hơn. Ứng dụng phải chạy để scheduler hoạt động.

## 4. Thuật toán

Mô hình `temporal_learning`, phiên bản **temporal-links-v2**, kết hợp 9 thành phần: tần suất toàn lịch sử; chuyển tiếp một/hai kỳ; lặp số; lân cận ±1; cùng đuôi; số phụ sang số chính; tần suất bán rã 5 và 15 kỳ quan sát.

Các thống kê điều kiện được làm trơn về tần suất nền. Trọng số cập nhật theo số khớp trong bộ 6 của từng thành phần sau khi quan sát kết quả. Brier chỉ còn là chỉ số chẩn đoán điểm số. Mỗi lượt kiểm thử dự đoán trước khi học kết quả đích; không coi hai bản ghi thiếu kỳ theo lịch là chuyển tiếp liên tiếp. Có fallback tần suất khi hỗn hợp có tổng số khớp kém hơn phương pháp nền trên các kỳ đã đánh giá trước đó.

Danh mục API còn có `bayesian_graph`, `deep_stacking`; một số tên/nhánh cũ mang tính heuristic. Tên XGBoost hoặc AI không chứng minh đã huấn luyện mô hình. Gộp thứ hạng dùng tổng `1/rank`, tie-break ổn định. Thuật toán phụ thực sự chạy; frontend không tự chế bộ phụ/kết hợp khi API thiếu.

Mã chính nằm trong `backend/src/main/java/com/example/analyzeproject/`:

- `service/AnalyzeService.java`, `PredictionSupport.java`: dự đoán và đối soát lịch sử.
- `service/TemporalRelationshipModel.java`: học liên kết.
- `service/DrawQuality.java`, gói `sync/`: chất lượng và đồng bộ.
- `service/ForecastService.java`, `ForecastProtocol.java`, `repository/ForecastStore.java`: lưu và đánh giá dự đoán thực tế.

## 5. Lưu dự đoán, đối soát và đánh giá độc lập

Đã triển khai trong Spring và giao diện Angular `/forecasts`:

- Tự kiểm tra sau khởi động 12 giây, sau đó mỗi 60 giây; thử lưu bộ 6 số chính của từng thuật toán trong danh mục cho kỳ tiếp theo. API dự đoán cũng thử lưu bộ đang hiển thị.
- Giữ nguyên bản đầu tiên theo loại/kỳ/thuật toán. Thay cấu hình hoặc gọi lại không ghi đè; UI thông báo nếu bộ đang tính khác bản đã chốt. Chưa tự lưu riêng mọi vé biến thể/kết hợp.
- Lưu thời điểm, kỳ đích, phiên bản mô hình, dấu nhận diện mã, cấu hình dùng thực tế, lịch sử đầu vào và SHA-256. Không nhận ngày quá khứ/thời điểm tạo do client chỉ định để chèn hồi tố.
- Cần ít nhất 5 kỳ hợp lệ và kỳ hoàn tất mới nhất theo lịch. Mốc chốt là 18:00 giờ Việt Nam; sau đó chọn kỳ tiếp theo. Lọc dữ liệu tương lai/kết quả chưa công bố.
- Bộ đối chứng tần suất và ngẫu nhiên được lưu cùng thời điểm, không dùng kết quả đích để chọn seed.
- Sau 19:00, đối soát với kết quả mới nhất hợp lệ. Các sự kiện `PENDING`, `SETTLED`, `RESULT_UNAVAILABLE` được ghi thêm, không sửa snapshot. Kết quả đổi/cách ly sẽ cập nhật thống kê theo sự kiện mới nhất; chạy lại cùng trạng thái không tạo trùng.
- Chỉ đo snapshot có thời gian/dấu dữ liệu hợp lệ và trạng thái mới nhất `SETTLED`. Tách nhóm theo loại, thuật toán, phiên bản mã/mô hình và cấu hình. Không dùng backtest hoặc snapshot chưa có kết quả làm mẫu đã đo.
- Báo cáo số khớp TB, phân bố 0–6, tỷ lệ từ 3 số, hai đối chứng và chênh lệch ghép cặp với khoảng 95% xấp xỉ. Không tự chọn/tối ưu cấu hình từ nhóm này. Nhãn dưới 30 kỳ chỉ là quy ước mẫu nhỏ, không phải ngưỡng chứng minh hiệu quả.

PostgreSQL có `forecast_snapshots` và `forecast_settlements` qua `V11__add_forecast_ledger.sql`; V10 là migration schema audit dữ liệu. Không sửa migration lịch sử để chèn dự đoán. `FORECAST_ENABLED=false` tắt tạo snapshot mới nhưng vẫn cho xem/đối soát bản cũ.

| API | Chức năng |
| --- | --- |
| `GET /api/forecasts` | Chỉ đọc snapshot, sự kiện và trạng thái; không tự chạy đối soát |
| `POST /api/forecasts/capture` | Thử chốt các thuật toán cho kỳ tiếp theo và đối soát |
| `POST /api/forecasts/reconcile` | Đối soát lại, không tính lại dự đoán |
| `GET /api/forecasts/evaluation?category=MEGA` | Đánh giá độc lập; bỏ category để xem cả hai loại |

Sáu dự đoán JSON đã tạo ngày 26/10 cho POWER 27/10 và MEGA 28/10 được bảo toàn tại `backend/archive/legacy-node-data/forecast_ledger.json`. **Chưa tự chuyển các bản này vào PostgreSQL**. Các lần chạy PostgreSQL tạm chỉ là kiểm chứng; không xác nhận đã lưu chúng trên production. Không được tính lại hoặc đổi thời điểm cũ nếu thực hiện chuyển đổi sau này.

## 6. Kết quả đo và giới hạn

Báo cáo phát triển V1/V2 trước khi bỏ Node dùng JSON 39 kỳ hợp lệ mỗi loại, 34 lượt đánh giá sau 5 kỳ khởi tạo:

| Chỉ số lịch sử | MEGA | POWER |
| --- | --- | --- |
| V1 số khớp TB/vé | 0,6765 | 0,7059 |
| V2 số khớp TB/vé sau fallback | 0,7059 | 0,7647 |
| Tần suất nền | 0,7059 | 0,7647 |
| Kỳ vọng chọn ngẫu nhiên `36/N` | 0,8000 | 0,6545 |
| V2 khớp từ 3 số | 0/34 | 1/34 |

Đây là phép tính lại trên mẫu đã biết trong quá trình phát triển, không phải số liệu đánh giá độc lập của Spring production. V2 bằng tần suất nền, chưa chứng minh lợi thế. Kỳ vọng ngẫu nhiên là lý thuyết, không phải kết quả một kiểm định.

Kết quả MEGA 25/10 lưu đã đối soát là 04–13–22–25–40–41. Tái dựng theo lịch sử đến 23/10: V1 chọn 03–06–15–27–31–36; V2 chọn 03–06–15–16–27–36; cả hai khớp 0. Không có snapshot trước giờ quay để xác nhận chính xác bộ đã hiển thị cho người dùng. Không suy rộng phép tái dựng này sang mọi vé/thuật toán.

## 7. Kiểm chứng

Trước khi dọn: 30 kiểm thử Java, 33 kiểm thử Node cũ, Angular production, build JAR; PostgreSQL 16.4 tạm chạy Flyway V1–V11, kiểm tra lưu đồng thời, đối soát, cách ly/xác minh lại và restart. Chín asset Angular đã được đối chiếu với JAR. Không kết nối production.

Sau khi dọn đã build Angular production và chạy `mvn.cmd install` thành công với 30 kiểm thử Java; hai phần không phụ thuộc file root đã xóa. Không còn bộ kiểm thử Node hoặc script Node/PostgreSQL gốc; báo cáo vẫn được giữ làm bằng chứng lịch sử. Kiểm thử Java còn nguyên trong `backend/src/test/`. Các cảnh báo template/deprecation Angular và unchecked Java có sẵn vẫn cần xử lý riêng.

Lệnh chạy từ đúng thư mục:

```powershell
# frontend/
npm.cmd run build

# backend/
mvn.cmd install
```

## 8. Những việc cần cải thiện

| Ưu tiên | Việc cần làm | Mục đích |
| --- | --- | --- |
| P0 | Chạy backend thực tế qua các kỳ tương lai | Chốt trước 18:00, đối soát sau 19:00, tích lũy bằng chứng độc lập |
| P0 | Quy trình phát triển/chọn tham số/kiểm tra tách theo thời gian | Không chọn cấu hình thắng từ chính tập đánh giá độc lập |
| P1 | Quyết định chuyển đổi dữ liệu archive khi cần | Đối chiếu PostgreSQL, giữ thời điểm và nguồn gốc snapshot; không nhập trùng/ghi đè |
| P1 | Mở rộng lịch sử đã xác minh | Đo kỳ thiếu, đối chiếu nguồn và database; tránh thống kê trên mẫu ngắn |
| P1 | Đo đóng góp từng thành phần | Chỉ thay bán rã/trọng số khi có kết quả ngoài tập chọn tham số |
| P1 | Chuẩn hóa dependency, runtime, build và CI | Build sạch Angular/JAR/Docker; cấp PostgreSQL tạm và kiểm tra migration/API tự động bằng công cụ thuộc backend |
| P2 | Rà soát nhãn/diễn giải/heuristic cũ | Không mô tả điểm số là xác suất; loại thông tin cố định gây hiểu nhầm |
| P2 | Vận hành và schema | Lưu lịch sử sync bền vững, cảnh báo stale; đánh giá `ddl-auto=update` cùng `validate-on-migrate=false` |

## 9. Bằng chứng lịch sử

- [V1](backend/docs/reports/learning-v1-2026-10-26.json), [V2](backend/docs/reports/learning-v2-2026-10-26.json).
- [Lưu dự đoán Node/restart trước khi ngừng Node](backend/docs/reports/forecast-node-2026-10-26.json).
- [PostgreSQL tạm, migration/API/restart](backend/docs/reports/forecast-postgres-2026-10-26.json).
- [Tổng hợp làm sạch dữ liệu](backend/docs/reports/data-cleanup-summary-2026-10-25.json), [kiểm chứng PostgreSQL](backend/docs/reports/data-cleanup-postgres-2026-10-25.json).

Các đường dẫn/lệnh Node nằm trong nội dung báo cáo cũ là thông tin lịch sử, không còn là hướng dẫn chạy dự án hiện tại.

## 10. Tối ưu trang forecasts (26/10/2026)

Phát hiện từ mã: trang trước đây tải hai GET song song, cả hai gọi lại đối soát toàn bộ ledger, truy vấn lịch sử kết quả và chờ cùng advisory lock PostgreSQL. Mỗi lần tải còn gửi toàn bộ history/configuration của từng snapshot về giao diện. Đây là nguồn tải lặp đáng kể; chưa có log production để xác nhận nguyên nhân duy nhất của HTTP 503.

Đã thay đổi:

- Trang gọi một `GET /api/forecasts/overview?category=MEGA&page=0&size=50`, trả hàng hiển thị và đánh giá cùng một phiên đọc. Mỗi trang mặc định 50 dòng, giới hạn 100; báo cáo đánh giá vẫn tính trên toàn bộ loại đã chọn, không chỉ trang hiện tại.
- GET ledger/evaluation/overview chỉ đọc; đối soát do scheduler hoặc nút/API POST riêng thực hiện. Không lấy khóa ghi và không truy vấn bảng kết quả xổ số trên đường tải trang.
- Cache trong bộ nhớ 10 giây, dùng chung cho các GET đồng thời trong một instance; đọc hai bảng ledger trong transaction read-only repeatable-read. Cache bị vô hiệu hóa sau khi lưu snapshot hoặc đối soát thành công. Replica khác có thể thấy thay đổi sau tối đa TTL cache; đối soát tự động vẫn theo chu kỳ 60 giây.
- Dữ liệu trả về overview không chứa lịch sử huấn luyện/cấu hình đầy đủ; GET ledger gốc vẫn phục vụ nhu cầu audit.
- Chặn tác vụ capture/reconcile chồng nhau trong cùng instance bằng trạng thái `BUSY`, thay vì xếp thêm lượt chờ. PostgreSQL vẫn giữ lock/transaction bảo vệ nhiều replica.
- Angular hủy request cũ khi đổi loại/rời trang, chặn gửi POST trùng, phân trang và không tự retry 503. Nút tải lại chỉ đọc; nút đối soát mới chạy đối soát.

Kiểm chứng: 34 kiểm thử Java đạt; 4 kiểm thử trang forecasts chạy bằng ChromeHeadless đạt; Angular production build đạt. Kiểm thử đồng thời bằng repository mock xác nhận 8 lượt đọc dùng chung một lần lấy ledger, không gọi truy vấn lịch sử hoặc đối soát. Đây không phải benchmark PostgreSQL/production; chưa khẳng định loại bỏ mọi nguyên nhân 503 như thiếu RAM, cold start hoặc giới hạn nền tảng hosting.

## 11. Gộp giao diện dự đoán và đối soát thành hai tab (26/10/2026)

Trang `/analyze` hiện là workspace chung với hai tab: **Tạo dự đoán** (`/analyze`) và **Lịch sử & đối soát** (`/analyze/history`). Route `/forecasts` chuyển hướng sang tab lịch sử; menu chỉ còn một mục Dự đoán & kết quả. Liên kết xem bộ đã chốt trên trang dự đoán đi thẳng đến tab lịch sử.

Mỗi tab dùng route con, chỉ tạo component đang mở. Tab dự đoán không tải API lịch sử ở nền; khi rời tab lịch sử, các request đang chờ bị hủy. MEGA/POWER dùng trạng thái chung của AnalyzeService với header, đồng bộ lựa chọn khi đổi tab; không gọi lại history khi phát lại cùng một category.

Tab lịch sử có thẻ tổng số bộ đã lưu/đã đối soát/chưa đánh giá/khớp từ 3 số; các số này lấy từ dữ liệu thực tế và không gán tỷ lệ preset. Thẻ bộ số hiển thị kỳ đích, thuật toán, thời điểm lưu, trạng thái, kết quả và số khớp; số khớp được tô màu kèm nhãn hỗ trợ truy cập. Có bộ lọc trạng thái trong trang hiện tại, phân trang 50 bộ và bảng đối chứng theo phiên bản/cấu hình. CSS hỗ trợ màn hình nhỏ; giữ riêng thao tác tải lại, lưu kỳ tiếp theo và đối soát. Không thay đổi cơ chế lưu snapshot hoặc thuật toán backend.

Kiểm chứng: Angular production build thành công; 8 kiểm thử ChromeHeadless đạt cho HTTP, đồng bộ category, thống kê/tô số khớp, route chuyển hướng và việc chỉ mở component của tab đang chọn. Backend `mvn install` đạt với 34 kiểm thử Java. Chưa triển khai production; kiểm chứng giao diện qua DOM/browser tests, chưa kiểm tra bằng ảnh chụp trình duyệt riêng vì lệnh chụp bị hệ thống chặn.

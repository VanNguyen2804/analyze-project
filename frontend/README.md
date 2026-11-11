# Frontend Angular

Stock, AI Lab v? Analyze d?ng health check chung r?i nh?n/poll t?c v? n?n; tr?ng th?i kh?i ??ng/c?p nh?t d?ng signal, gi? k?t qu? khi refresh v? h?y polling khi r?i trang. Xem [h?p ??ng async v? c?u h?nh Render worker](../backend/docs/ASYNC_PAGE_JOBS.md).

Frontend dùng Angular 22.2.0 với standalone components, OnPush và lazy routes. Các package Angular runtime/compiler/CLI/builder được chốt cùng phiên bản trong package và lockfile. Mã nguồn chia thành `src/app/core/`, `feature/`, `share/`; route cấu hình trong `src/app/app.routes.ts`.

Chạy trong thư mục `frontend/`, với Node tương thích dependencies đã khai báo:

```powershell
npm.cmd ci
npm.cmd start
npm.cmd run format:check
npm.cmd run typecheck
npm.cmd run test:ci
npm.cmd run build
```

API do Spring Boot trong `../backend/` cung cấp. Cấu hình địa chỉ API ở `src/environments/environment.ts` và `environment.prod.ts`; giá trị rỗng gọi API cùng origin. Khi chạy Angular dev server riêng, cấu hình địa chỉ backend tương ứng.

Trang `/analyze` đọc các bộ số AI Lab cho kỳ tới; `/analyze/history` xem ledger tự động; `/analyze/review` phân tích kỳ và nhóm vé. `/forecasts` chuyển hướng sang lịch sử, `/latest-analysis` sang tab phân tích kỳ. `/ai-lab` theo dõi các mô hình thử nghiệm; component `feature/ai-lab/lab-progress/` nhận report riêng qua input, quản lý mức so sánh ≥1/≥2/≥3 và hiển thị trung bình/lũy kế/histogram. Phản hồi đã học hiển thị trên từng snapshot mới, không tính lại việc học ở browser. Không suy ra xác suất trúng từ điểm xếp hạng heuristic.

## Quy ước frontend

### Trang chào và tải theo nhu cầu

`/` hiển thị `feature/welcome/` với lời chào Analyze, không tự chuyển sang trang dự đoán. Logo và mục Trang chủ quay về đây; nút Khám phá menu mở menu hiện có. Trang chào không gọi API nghiệp vụ. Badge kết quả ở menu chỉ tải trên các trang xổ số, không tải tại trang chủ hoặc chứng khoán.

Router dùng `NoPreloading`: các trang standalone dùng `loadComponent`; `/analyze` tải cấu hình `prediction.routes.ts` qua `loadChildren`, rồi tải riêng từng tab. Các URL truy cập trực tiếp và redirect cũ được giữ, URL không khớp quay về trang chủ. Biểu đồ chứng khoán (gồm thư viện chart) dùng `@defer (on viewport)`; placeholder giữ chỗ, lỗi tải chunk có thông báo. Các component ghép vé/portfolio cũ không còn được gắn trên trang dự đoán AI Lab.

Lazy loading giảm công việc trên trình duyệt và tránh gọi API trang dự đoán khi mở trang chủ. Trên cấu hình Render tách riêng, Static Site phục vụ landing ngay cả khi backend ngủ; các chức năng gọi API vẫn phải chờ backend khởi động.

### Tách component và stylesheet

- Mỗi component có template và stylesheet riêng khi nội dung đáng kể; không đặt thẻ `<style>` trong HTML. Các style tĩnh của header, bộ ghép vé và hộp xem cấu hình nằm trong CSS; binding style phụ thuộc dữ liệu vẫn được giữ khi cần.
- Tách theo trách nhiệm và trạng thái độc lập, không chỉ theo số dòng. Truyền dữ liệu bằng `input`/`model`, phát hành động bằng `output`; không truyền nguyên instance component cha xuống con.
- `prediction-chart/` quản lý tải/cache lịch sử và chế độ biểu đồ; `linkage-timeline/`, `gap-pressure/`, `spacing-distribution/` sở hữu phần hiển thị/tương tác tương ứng. Các chế độ được defer khi mở lần đầu và giữ bộ lọc sau khi đã mở.
- `ticket-mixer/` là component ghép sáu số cũ, hiện không còn được trang dự đoán AI Lab sử dụng.
- `latest-draw-analysis/` đọc DTO `DrawReport`, quản lý context đã áp dụng và hủy request bằng `switchMap`. `ticket-group/` trình bày từng vé và thống kê nhóm; `evidence-details/` lọc chi tiết số của snapshot đã chọn; `draw-reconciliation/` chỉ nhận và trình bày đánh giá nhiều kỳ, không gọi prediction hoặc cập nhật cấu hình. Các component chi tiết đối soát/cấu hình cũ chưa còn được route này sử dụng.
- `hyperparameter-history/` quản lý bảng và hộp xem JSON/README; phát reload/activate để trang cha điều phối cập nhật cấu hình.
- Style dùng chung trong mỗi feature nằm ở `prediction-shared.css` và `analysis-shared.css`; style riêng nằm cạnh component sở hữu DOM. Giữ encapsulation, không dùng `::ng-deep` hoặc chuyển toàn bộ CSS ra global để né ranh giới component.

Component cũ `prediction/mega-portfolio/` hiển thị thử nghiệm MEGA và các đối chứng đã chốt từ `/api/mega-portfolio`, hiện không còn gắn trên trang dự đoán. Component tự quản lý tải/phân trang/timer, hủy request khi bị tháo khỏi trang. Input `context` chứa kỳ và bộ số gốc/Lab để kiểm tra trùng. DTO/HTTP nằm ở `core/models/mega-portfolio.model.ts` và `core/services/mega-portfolio-api.service.ts`; ledger và API được giữ nguyên.

### Angular và kiểm chứng

- Dùng `@if`, `@for`, `@let`; import trực tiếp pipe/directive cần thiết thay vì `CommonModule`. Với danh sách vé/nhóm dữ liệu, track bằng ID/cohort; với ô nhập có vị trí cố định, track bằng `$index`.
- Dùng `inject()` và field `readonly` cho dependency; component dùng `ChangeDetectionStrategy.OnPush`. Các component dùng chung trong shell đã chuyển sang signals.
- Dùng `signal` cho trạng thái cục bộ và `computed` cho số liệu dẫn xuất; lịch sử và AI lab không cần gọi thủ công change detection. Các form/phân tích cũ vẫn dùng FormsModule và field thông thường khi phù hợp, kết hợp `markForCheck()` cho callback bất đồng bộ. Không chuyển toàn ứng dụng sang zoneless trong đợt này.
- MEGA/POWER có một nguồn duy nhất tại `CategoryService`: `category()` cho template/signals và `category$` cho luồng RxJS. `AnalyzeService` giữ bridge tương thích; không tạo thêm subject để đồng bộ category.
- HTTP của lịch sử và AI lab nằm trong `ForecastApiService`; DTO nằm ở `core/models/forecast.model.ts`. Không để `any` lan vào các luồng mới. Các DTO phân tích động cũ chưa được thay toàn bộ bằng kiểu cụ thể.
- Subscription/timer theo vòng đời dùng `takeUntilDestroyed`. Request tải trang lịch sử/AI lab và badge menu dùng `switchMap` để hủy lượt cũ. Tính toán biểu đồ nằm trong hàm thuần `feature/prediction/linkage-chart.ts`, tách khỏi component.
- TypeScript bật strict, strictTemplates, noUnusedLocals/noUnusedParameters, class field semantics chuẩn; alias `@app/*` thay cho baseUrl đã deprecated. Giữ tên file hiện tại nhất quán với dự án.
- Dùng `npm run format` trước khi commit. CI kiểm tra formatter, kiểu dữ liệu, toàn bộ frontend suite và production build.

Build dùng `@angular/build:application`; test dùng `@angular/build:karma`. Không còn builder Webpack cũ; giữ Jasmine/Karma hiện tại để chạy các kiểm thử đã có. Node 24 là runtime dùng trong `.nvmrc`, CI và Docker. Tham khảo [Angular control flow](https://angular.dev/reference/migrations/control-flow), [inject migration](https://angular.dev/reference/migrations/inject-function) và [build system](https://angular.dev/tools/cli/build-system-migration).

`npm run build:sync` build và đồng bộ asset sang backend bằng script Node chạy trên Windows/Linux; chỉ xóa các bundle sinh tự động cũ. Đầu ra vẫn ở `dist/analyzeproject/` để tương thích Maven, Docker và CI.

Maven mặc định không đóng gói frontend. Để chạy chung tùy chọn, build Angular rồi dùng `mvn clean install -Pbundle-frontend` và `APP_FRONTEND_ENABLED=true`. Xem [hướng dẫn toàn dự án](../README.md).

### Render Static Site

Dùng `API_BASE_URL=https://analyze-project.onrender.com` và `npm run build:render`; publish `dist/analyzeproject`. Script sinh cấu hình API công khai `assets/app-config.js` trước bootstrap, không gọi server để đọc cấu hình. Không đặt secrets vào biến này. `npm run build` thông thường giữ mặc định same-origin. API có timeout 180 giây và không tự retry ghi dữ liệu. Xem [hướng dẫn cấu hình Render và CORS](../backend/docs/RENDER_SPLIT_DEPLOYMENT.md).


`share/components/lab-highlights/` hiển thị dự đoán kỳ tới của thuật toán Lab từng trúng ≥3 số trên trang AI Lab. Trang `/analyze` đọc endpoint `/api/ai-lab/predictions` riêng, hiển thị mọi mô hình theo tỷ lệ kỳ có vé ≥3, thêm ≥2/≥1 và số kỳ đã đối soát. Mỗi mô hình có bộ năm vé cho kỳ tới, nút sao chép và chi tiết từng nhóm phiên bản lịch sử. Hủy request khi đổi category, bỏ polling khi ẩn tab; không gọi tạo hai thuật toán hay bộ kết hợp. Xem [cách tính và hợp đồng API](../backend/docs/AI_LAB_PREDICTIONS.md).


Trang dự đoán AI Lab dùng bảng màu xanh đêm với style riêng tại `prediction.component.css`; theme workspace tại `feature/prediction/prediction-night.css` được giới hạn trong trang dự đoán. Kiểm tra trình duyệt bằng fixture desktop 1440 px, mobile 390 px: không tràn ngang, đủ tỷ lệ/số kỳ, đổi MEGA/POWER và không gọi POST sinh dự đoán; không dùng fixture để đánh giá độ chính xác.


Stock: `stock-position-form` chỉ nhập giá vốn và lượng giữ, đặt đầu cột vị thế; `stock-candle-scenarios` hiển thị `scenarios` từ backend, điều kiện nến, lượng/vốn mô phỏng và cảnh báo. `stock-position-plan` chỉ dành cho response kế hoạch thủ công cũ; mặc định session mới không tạo policy hạn mức.


Chart Stock: `stock-chart-projections.ts` tính nhịp neo/Fibonacci/hồi quy từ nến đóng; `stock-projection-layer.ts` vẽ primitive và vùng bên phải, không thêm candle hoặc ngày tương lai. Checkbox chọn quạt/Fib ngang/kênh và cửa sổ 30/60/120 nến trong `stock-chart.component.html`; [công thức và kiểm chứng](../backend/docs/STOCK_CHART_TECHNICAL_TOOLS.md).

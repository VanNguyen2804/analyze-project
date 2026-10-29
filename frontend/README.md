# Frontend Angular

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

Trang `/analyze` tạo dự đoán; `/analyze/history` xem ledger tự động; `/analyze/review` phân tích kỳ và nhóm vé. `/forecasts` chuyển hướng sang lịch sử, `/latest-analysis` sang tab phân tích kỳ. `/ai-lab` theo dõi các mô hình thử nghiệm. Không suy ra xác suất trúng từ điểm xếp hạng heuristic.

## Quy ước frontend

### Tách component và stylesheet

- Mỗi component có template và stylesheet riêng khi nội dung đáng kể; không đặt thẻ `<style>` trong HTML. Các style tĩnh của header, bộ ghép vé và hộp xem cấu hình nằm trong CSS; binding style phụ thuộc dữ liệu vẫn được giữ khi cần.
- Tách theo trách nhiệm và trạng thái độc lập, không chỉ theo số dòng. Truyền dữ liệu bằng `input`/`model`, phát hành động bằng `output`; không truyền nguyên instance component cha xuống con.
- `prediction-chart/` quản lý tải/cache lịch sử và chế độ biểu đồ; `linkage-timeline/`, `gap-pressure/`, `spacing-distribution/` sở hữu phần hiển thị/tương tác tương ứng. Các chế độ được defer khi mở lần đầu và giữ bộ lọc sau khi đã mở.
- `ticket-mixer/` quản lý lựa chọn sáu số và phát sự kiện copy cho trang dự đoán.
- `latest-draw-analysis/` đọc DTO `DrawReport`, quản lý context đã áp dụng và hủy request bằng `switchMap`. `ticket-group/` trình bày từng vé và thống kê nhóm; `evidence-details/` lọc chi tiết số của snapshot đã chọn; `draw-reconciliation/` chỉ nhận và trình bày đánh giá nhiều kỳ, không gọi prediction hoặc cập nhật cấu hình. Các component chi tiết đối soát/cấu hình cũ chưa còn được route này sử dụng.
- `hyperparameter-history/` quản lý bảng và hộp xem JSON/README; phát reload/activate để trang cha điều phối cập nhật cấu hình.
- Style dùng chung trong mỗi feature nằm ở `prediction-shared.css` và `analysis-shared.css`; style riêng nằm cạnh component sở hữu DOM. Giữ encapsulation, không dùng `::ng-deep` hoặc chuyển toàn bộ CSS ra global để né ranh giới component.

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

Để đóng gói chung, build Angular trước rồi chạy Maven trong `backend/`. Xem [hướng dẫn toàn dự án](../README.md).

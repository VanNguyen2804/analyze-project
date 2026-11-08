# Tách Angular Static Site và Spring Boot trên Render

## Cấu hình cuối cùng

| Service | Vai trò | URL dự kiến |
| --- | --- | --- |
| `analyze-frontend` | Angular Static Site trên CDN | `https://analyze-frontend.onrender.com/landing` |
| `analyze-project` hiện có | Spring Boot API, giữ kết nối Aiven | `https://analyze-project.onrender.com/api/health` |

Tên miền Static Site có thể được Render cấp tên khác nếu tên dự kiến đã dùng. Luôn lấy URL thực tế trong Dashboard. Giữ URL backend hiện có để không cần di chuyển database. URL `analyze-project.onrender.com` sẽ là API; người dùng mở URL của Static Site.

Frontend gọi HTTPS trực tiếp tới backend qua cấu hình công khai `API_BASE_URL`. Backend giới hạn CORS bằng `APP_CORS_ALLOWED_ORIGINS`. Chỉ cần rewrite SPA trên Static Site; **không thêm rewrite `/api/*`**. CORS không thay thế xác thực hoặc phân quyền của API.

## Thứ tự chuyển đổi thủ công

1. Commit/push các thay đổi lên branch đang deploy. Nếu Web Service đang dùng Dockerfile gốc, bản này vẫn hỗ trợ kiểu chạy chung để làm bước chuyển tiếp. Chưa đổi Root Directory cho đến khi frontend mới đã sẵn sàng.
2. Tạo Static Site mới và ghi lại URL thực tế.
3. Đặt CORS trên Web Service hiện có, deploy và kiểm tra frontend mới gọi API đọc được. Sau đó chuyển backend sang cấu hình độc lập ở phần dưới. Không tạo Web Service thứ hai cùng chạy scheduler với database production.

### A. Tạo frontend

Render Dashboard → **New → Static Site** → kết nối cùng Git repository và cùng branch:

| Trường | Giá trị |
| --- | --- |
| Name | `analyze-frontend` |
| Root Directory | `frontend` |
| Build Command | `npm ci && npm run build:render` |
| Publish Directory | `dist/analyzeproject` |

Trong Environment:

```text
NODE_VERSION=24.22.1
API_BASE_URL=https://analyze-project.onrender.com
```

`API_BASE_URL` là origin backend, không thêm `/api`, username/password hoặc token. Đây là thông tin công khai, không nhập biến database vào Static Site. Script kiểm tra HTTPS origin và ghi `assets/app-config.js` sau build. Sau khi đổi API_BASE_URL phải deploy lại frontend.

Trong **Redirects/Rewrites**, thêm:

| Source | Destination | Action |
| --- | --- | --- |
| `/*` | `/index.html` | Rewrite |

Quy tắc này cho phép mở trực tiếp hoặc refresh `/landing`, `/entry`, `/analyze/history`, `/stocks/FPT`. Angular tự chuyển `/` và URL không hợp lệ về `/landing`.

### B. Sửa Web Service hiện có

Mở **analyze-project → Settings / Build & Deploy**. Giữ service hiện có và các biến database. Đặt:

| Trường | Giá trị |
| --- | --- |
| Runtime | Docker (giữ nguyên) |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` — tính từ Root Directory |
| Docker Build Context Directory | `.` — tính từ Root Directory |
| Health Check Path | `/api/health` |

Trong Environment, giữ `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` cùng các biến nghiệp vụ đang có. Thêm/cập nhật:

```text
PORT=10000
SPRING_PROFILES_ACTIVE=prod
APP_FRONTEND_ENABLED=false
APP_CORS_ALLOWED_ORIGINS=https://analyze-frontend.onrender.com
```

Thay CORS bằng **URL frontend thực tế**, không có path hoặc dấu `/` cuối. Nếu dùng thêm custom domain, khai báo nhiều origin cách nhau bằng dấu phẩy. Bỏ các wildcard CORS ở controller để cấu hình tập trung có hiệu lực. Giữ nguyên các giá trị database đang hoạt động; không tạo lại database hoặc chạy reset/migration thủ công.

Chọn **Manual Deploy → Deploy latest commit**. Chờ log `Started AnalyzeProjectApplication` và health check thành công. `/landing` trên backend sẽ trả 404 khi chạy độc lập — đây là hành vi dự kiến; frontend phục vụ đường dẫn này.

### C. Kiểm tra trước khi chuyển người dùng

- Mở URL Static Site tại `/landing`: hiển thị lời chào; Network không có request `/api/`.
- Mở `/entry` hoặc `/analyze/history` từ menu: Request URL phải là backend; response là JSON, không phải HTML của SPA.
- Refresh trực tiếp `/stocks/FPT` và `/analyze/history`: không bị CDN 404.
- Kiểm tra preflight OPTIONS của một thao tác ghi, sau đó dùng thao tác nghiệp vụ đã định thực hiện để xác nhận lưu được. Không tạo dữ liệu giả trên production chỉ để thử.
- Khi backend Free ngủ, landing vẫn mở; trang chức năng chờ backend thức dậy. API timeout sau 180 giây và đi vào nhánh xử lý lỗi hiện có; không tự retry thao tác ghi. Nếu thao tác ghi timeout, kiểm tra dữ liệu đã được lưu chưa trước khi bấm lại.

Backend Free vẫn có thể ngủ; scheduler cũng không chạy trong thời gian service ngủ. Tách frontend không đảm bảo chốt/đồng bộ đúng giờ nếu backend chưa hoạt động.

## Nếu dùng Blueprint

`render.yaml` mô tả hai service trên. Nếu service hiện tại đã thuộc Blueprint, sync Blueprint hiện có; không tạo Blueprint trùng để tránh backend/scheduler thứ hai. Cập nhật `APP_CORS_ALLOWED_ORIGINS` sau khi biết URL Static Site. Nếu service hiện tại được tạo thủ công, làm theo Dashboard trước; không cần tạo Blueprint mới chỉ để chuyển đổi.

## Build và rollback

```powershell
# frontend/
$env:API_BASE_URL='https://analyze-project.onrender.com'
npm run build:render

# backend/ — độc lập, không lấy Angular dù frontend đã build
mvn clean install
```

Maven mặc định loại thư mục static khỏi JAR; CI kiểm tra điều này. `mvn clean` tránh giữ assets trong `target/classes` của các lần build chung trước đó.

Dockerfile gốc chỉ là phương án chạy chung tùy chọn. Nếu cần quay lại trong lúc chuyển đổi, đặt Root Directory của Web Service trống, Dockerfile Path `./Dockerfile`, Docker Build Context `.`, `APP_FRONTEND_ENABLED=true`, rồi deploy lại. Dockerfile này chủ động build profile `bundle-frontend`. Frontend Static Site có thể giữ nguyên trong thời gian kiểm tra. Với build chung thủ công: build Angular, chạy `mvn clean install -Pbundle-frontend`, bật `APP_FRONTEND_ENABLED=true` khi chạy JAR.

## Nguồn chính thức

- [Static Sites](https://render.com/docs/static-sites)
- [Monorepo và Root Directory](https://render.com/docs/monorepo-support)
- [Redirects/Rewrites cho SPA](https://render.com/docs/redirects-rewrites)
- [Blueprint reference](https://render.com/docs/blueprint-spec)
- [Free Web Services và sleep](https://render.com/docs/free#spinning-down-on-idle)

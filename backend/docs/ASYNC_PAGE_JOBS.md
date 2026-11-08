# Tải Stock, AI Lab và Analyze qua tác vụ nền

Frontend dùng `?async=true` trên API hiện có. Backend trả `202` và mã tác vụ khi chưa có kết quả, hoặc `200` và kết quả được cache. `GET /api/page-jobs/{id}` chỉ đọc trạng thái/kết quả; không gọi provider, huấn luyện hoặc tạo gợi ý. Bỏ `async=true` giữ hợp đồng đồng bộ cũ.

## Luồng của từng trang

| API có chế độ async | Thực thi / cache |
| --- | --- |
| `GET /api/stocks/watchlist?async=true` | Một worker Stock trong JVM, hàng đợi RAM |
| `GET /api/stocks/FPT/analysis?mode=SWING&async=true` | Cùng worker Stock; ưu tiên phân tích mã đang xem trước watchlist đang xếp hàng |
| `GET /api/ai-lab?category=MEGA&page=0&async=true` | Hàng đợi PostgreSQL, worker đọc projection gọn đã kiểm tra hợp lệ |
| `GET /api/ai-lab/highlights?category=MEGA&async=true` | Đọc dữ liệu Lab có sẵn, không huấn luyện/chốt/đối soát |
| `POST /api/ai-lab/run?async=true` | Theo dõi thao tác chốt/đối soát qua job |
| `POST /api/prediction-workspace/suggestions?async=true` | Tính và lưu gợi ý trên worker, tái sử dụng kết quả cho cùng đầu vào |

Response:

```json
{"id":"l-00000000-0000-0000-0000-000000000001","status":"QUEUED","data":null,"message":null,"updatedAt":"2026-11-08T10:00:00Z"}
```

Trạng thái: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`. Khi thành công, `data` có cùng nội dung nghiệp vụ với response đồng bộ. Job đang chờ trả `Retry-After: 2`. Lỗi tác vụ được phản ánh trong trạng thái `FAILED`; API không gửi stack trace cho trình duyệt.

## Giới hạn và tính nhất quán

- Tác vụ trùng đầu vào dùng chung mã job, kể cả nhiều web instance đối với tác vụ xổ số. Hàng đợi bền mặc định tối đa 64 job đang chờ/chạy; khi đầy trả `503 SERVICE_BUSY`. PostgreSQL khóa ngắn khi enqueue, claim bằng `FOR UPDATE SKIP LOCKED`; không giữ transaction DB trong suốt quá trình tính.
- Worker xổ số mặc định một tác vụ đồng thời; cấu hình hỗ trợ 1–4. Worker Stock luôn một tác vụ, hàng đợi tối đa 16, giữ tối đa 64 bản ghi RAM. Không đưa giá vốn, số lượng/vị thế hoặc OHLCV vào hàng đợi bền.
- Cache job xổ số xét revision dữ liệu, kỳ mục tiêu, đầu vào và phiên bản code. Trigger V16 tăng revision khi kết quả, số chính, cấu hình hoặc ledger Lab thực sự thay đổi. Cache Analyze tối đa 300 giây; các loại khác 30 giây. Thao tác sửa/chốt vé vẫn kiểm tra lịch sử/cutoff ở use case hiện có.
- Stock có cache snapshot trước provider tối đa 60 giây, luôn kiểm tra lại trạng thái độ mới. RAM mất khi process restart. Snapshot/vị thế vẫn gắn với JVM phục vụ HTTP; giữ backend Stock **một web instance**. Muốn scale web ngang phải thiết kế cache snapshot dùng chung và kiểm tra chính sách lưu dữ liệu thị trường trước.
- Lease job bền được gia hạn mỗi 15 giây, hết hạn sau 60 giây nếu worker chết. Job đọc có thể được nhận lại một lần. Job có ghi (`ANALYZE`, `LAB_RUN`) được đánh dấu thất bại khi mất lease, không tự chạy lại một thao tác có thể đã ghi một phần. Người dùng kiểm tra trạng thái rồi chủ động thử lại; các bản chốt/ledger cũ vẫn giữ nguyên.
- Worker đánh dấu job thất bại và yêu cầu ngắt sau 10 phút. Completion có claim token nên không ghi đè một job đã thất bại hoặc được worker khác nhận lại. Ngắt tính toán CPU là cooperative; use case kiểm tra ngắt trước khi lưu gợi ý/capture. Không thể bảo đảm hard kill một thư viện Java không đáp ứng interrupt.
- Job xếp hàng quá 10 phút hết hiệu lực ở lần enqueue/claim tiếp theo. Analyze giữ kỳ mục tiêu lúc nhận yêu cầu và từ chối chạy nếu đã qua cutoff sang kỳ khác; trình duyệt cần tải lại theo kỳ mới.
- Job xổ số hoàn tất/thất bại được dọn sau một giờ khi enqueue tiếp. Job Stock hoàn tất được dọn sau 10 phút hoặc khi cần chỗ trống. Nếu trình duyệt nhận `404` cho một job RAM sau restart, tải lại trang để nhận job mới.

V17 bổ sung projection đọc gọn và phiên bản kiểm tra. Lần đọc đầu sau nâng cấp có thể phải kiểm tra các snapshot cũ; việc này chạy trên worker. Các lần sau không tải lại raw history của từng snapshot. SQL phân trang theo category/ngày/id, có index. Thống kê nhóm và highlights vẫn tổng hợp từ toàn bộ **projection gọn** của category, chưa phải bảng thống kê tăng dần. Sửa raw snapshot làm mất hiệu lực projection; thay phiên bản logic kiểm tra sẽ dựng lại projection. Snapshot gốc và settlement audit không bị thay thế.

## Frontend

Một health check dùng chung cho các request đồng thời, chỉ chạy khi vào trang cần job. Landing không gọi API. Health check được cache 60 giây, có retry giới hạn khi server đang khởi động, chờ tối đa 180 giây; đây là liveness, không bảo đảm DB/provider khỏe.

Sau khi backend sẵn sàng, frontend gửi yêu cầu một lần rồi poll trạng thái, bắt đầu từ hai giây và giãn dần tối đa 10 giây. Poll không chồng nhau, tạm dừng khi tab ẩn, hủy khi đổi đầu vào/rời trang, giới hạn tổng thời gian chờ job 10 phút. Không tự retry POST. Lỗi đọc/cập nhật không xóa dữ liệu đang xem; đổi mã/category/thuật toán không hiển thị kết quả cũ như kết quả của lựa chọn mới. Các API ghi tương tác như position/commit không đưa vào hàng đợi dùng chung.

## Render: chạy ngay với service hiện có

1. Commit/push code, sync **Blueprint hiện có**, deploy backend trước rồi frontend. V16/V17 được Flyway chạy tự động. Các migration chỉ thêm cấu trúc/projection; không reset dữ liệu.
2. `render.yaml` mặc định giữ một backend hiện có, `plan: free`, chạy cả API, worker và scheduler. Các biến mới đã được khai báo:

   ```text
   PAGE_JOB_WORKER_ENABLED=true
   STOCK_PAGE_WORKER_ENABLED=true
   PAGE_JOB_CONCURRENCY=1
   APP_SCHEDULING_ENABLED=true
   ```

3. Giữ `healthCheckPath: /api/health`, cấu hình CORS và URL frontend như [hướng dẫn tách hosting](RENDER_SPLIT_DEPLOYMENT.md).
4. Kiểm tra `/api/health`, sau đó mở Stock/AI Lab/Analyze trên frontend. Trong Network, thấy `async=true`, response `202` khi cache trống và các request `/api/page-jobs/...`; cuối cùng `SUCCEEDED`. Cache còn hạn có thể trả kết quả ngay mà không poll.
5. Xem logs `Page job ... completed in ... ms`, CPU/RAM/restart và số kết nối DB trong Dashboard. So sánh riêng cold start, job mới và cache hit. Không chạy load test hoặc POST chốt dữ liệu giả trên production.

Free vẫn ngủ sau 15 phút không có traffic; hàng đợi trong cùng JVM không chạy trong lúc service ngủ. Async giúp HTTP thoát khỏi công việc nặng sau khi ứng dụng đã hoạt động, không cấp thêm CPU hoặc loại bỏ cold start. Muốn scheduler chạy ổn định, chọn compute trả phí cho service.

## Render: tách worker riêng khi chọn compute trả phí

Đây là cấu hình tùy chọn có phát sinh phí; cấu hình mặc định không tự tạo worker trả phí. Thêm service dưới vào **cùng `services:` của `render.yaml` hiện có**. Không tạo Blueprint/backend trùng. Chọn compute phù hợp trong Dashboard; ví dụ dùng `1c-2g` cho worker Java:

```yaml
  - type: worker
    name: analyze-compute
    runtime: docker
    rootDir: backend
    dockerfilePath: ./Dockerfile
    dockerContext: .
    region: singapore
    plan: 1c-2g
    numInstances: 1
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod,worker
      - key: PAGE_JOB_WORKER_ENABLED
        value: "true"
      - key: STOCK_PAGE_WORKER_ENABLED
        value: "false"
      - key: PAGE_JOB_CONCURRENCY
        value: "1"
      - key: APP_SCHEDULING_ENABLED
        value: "true"
      - key: DB_MAX_POOL_SIZE
        value: "2"
      - key: DB_MIN_IDLE
        value: "1"
      - key: SPRING_DATASOURCE_URL
        sync: false
      - key: SPRING_DATASOURCE_USERNAME
        sync: false
      - key: SPRING_DATASOURCE_PASSWORD
        sync: false
```

Trong cùng lần chỉnh Blueprint, sửa phần **Web Service `analyze-project`**:

```text
PAGE_JOB_WORKER_ENABLED=false
STOCK_PAGE_WORKER_ENABLED=true
APP_SCHEDULING_ENABLED=false
DB_MAX_POOL_SIZE=3
DB_MIN_IDLE=1
```

Worker dùng đúng database và các biến nghiệp vụ đang dùng trên backend (feed, bật/tắt forecast/Lab/portfolio, cấu hình feedback...). Điền các biến secret tại Render, không commit giá trị vào YAML. Ví dụ tổng pool DB của web và worker là 3 + 2; điều chỉnh theo giới hạn Aiven thực tế. Nếu web tiếp tục dùng Free thì API vẫn có cold start dù worker đang chạy.

Tạm tắt Auto Sync trong lúc sửa cấu hình, push và Manual Sync Blueprint, kiểm tra cả hai process chạy đúng vai trò rồi bật lại theo workflow của bạn. Worker profile không mở HTTP listener; không đặt health-check HTTP cho Background Worker. Cấu hình Blueprint là nguồn quản lý các biến đã khai báo, vì vậy chỉnh chúng trong YAML để lần sync sau không khôi phục giá trị cũ.

Giữ **một process bật scheduler**. Chưa scale ngang service có `APP_SCHEDULING_ENABLED=true`; tăng `PAGE_JOB_CONCURRENCY` từ 1 lên 2 chỉ sau khi CPU/RAM/DB còn dư. Muốn thêm nhiều worker instance, dùng service worker khác tắt scheduler hoặc triển khai cơ chế bầu scheduler chung trước. Tăng HTTP threads không làm một phép tính nhanh hơn.

Rollback về chế độ một backend: đặt `PAGE_JOB_WORKER_ENABLED=true`, `APP_SCHEDULING_ENABLED=true` trên web và dừng worker riêng. Giữ V16/V17; không rollback/xóa schema để đổi vai trò. Các client cũ vẫn dùng endpoint đồng bộ.

Nguồn: [Render Free](https://render.com/docs/free), [Background Workers](https://render.com/docs/background-workers), [Blueprint](https://render.com/docs/blueprint-spec), [Scaling](https://render.com/docs/scaling).

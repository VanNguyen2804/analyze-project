<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

Project architecture, completed algorithm fixes, validation commands, and remaining work are documented in [PROJECT_ANALYSIS.md](PROJECT_ANALYSIS.md).

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/9648a017-762c-49e9-8c3e-53f514eb93b4

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Optionally set `PORT` in the process environment (default: `3000`). The root prediction code uses stored history and does not require a Gemini API key.
3. Run the app:
   `npm run dev`

Run regression tests with `npm test` and type checks with `npm run lint`. Docker uses the separate Angular/Spring Boot stack described in the project analysis.

## Tự động cập nhật kết quả Vietlott

Spring Boot và Node đều bật scheduler mặc định: chạy sau khi khởi động 10 giây, sau đó kiểm tra mỗi 15 phút (tính từ lúc lần trước hoàn tất). Ứng dụng phải đang chạy; nếu máy chủ ngủ hoặc dừng, scheduler sẽ bù dữ liệu trong cửa sổ cấu hình khi chạy lại.

Nguồn miễn phí, không cần API key: [vietvudanh/vietlott-data](https://github.com/vietvudanh/vietlott-data) (MIT). Đây là **feed cộng đồng thu thập từ Vietlott**, không phải API chính thức hoặc cam kết cập nhật tức thì. File `power645.jsonl` là **MEGA 6/45**; `power655.jsonl` là **POWER 6/55**. Mỗi dòng chứa `date`, `id`, `result`, `process_time`; phần tử thứ bảy của POWER là số phụ.

| Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `LOTTERY_SYNC_ENABLED` | `true` | Bật/tắt đồng bộ |
| `LOTTERY_SYNC_INTERVAL_SECONDS` | `900` | Khoảng nghỉ giữa hai lần kiểm tra, 60–86400 giây |
| `LOTTERY_SYNC_STARTUP_DELAY_SECONDS` | `10` | Chờ sau khởi động, 0–3600 giây |
| `LOTTERY_SYNC_LOOKBACK_DAYS` | `90` | Bù kỳ thiếu trong 1–7300 ngày gần nhất |
| `LOTTERY_SYNC_TIMEOUT_SECONDS` | `20` | Timeout mỗi nguồn, 1–120 giây |
| `LOTTERY_SYNC_MEGA_URL` | [power645.jsonl](https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power645.jsonl) | Nguồn NDJSON MEGA qua HTTPS |
| `LOTTERY_SYNC_POWER_URL` | [power655.jsonl](https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power655.jsonl) | Nguồn NDJSON POWER qua HTTPS |

Chỉ nhận kết quả đủ sáu số hợp lệ; POWER bắt buộc có số phụ khác sáu số chính. Ngày hiện tại chỉ nhập sau 18:30 giờ `Asia/Ho_Chi_Minh`, không nhập ngày tương lai. Chạy lại không tạo bản ghi trùng. Lỗi mạng / dữ liệu nguồn không hợp lệ được thử lại ở lần kế tiếp, độc lập cho mỗi loại. Số đã lưu khác nguồn, kể cả thiếu số phụ, được giữ nguyên và liệt kê là `CONFLICT` để đối soát; không tự ghi đè lịch sử.

Xem trạng thái bằng `GET /api/lottery-sync/status`: thời điểm kiểm tra, ngày mới nhất của nguồn, ngày dự kiến, số kỳ thêm / giữ nguyên, chi tiết xung đột hoặc lỗi. Trạng thái lần chạy nằm trong bộ nhớ và reset khi restart; kết quả đã nhập và nguồn trong `note` được lưu bền vững. `STALE` cho biết nguồn chưa có kỳ mới nhất theo lịch. Frontend lấy kết quả từ API hiện có khi tải lại lịch sử / dự đoán.

Đồng bộ thủ công cho Node (dừng Node server trước vì JSON chỉ hỗ trợ một tiến trình ghi):

```powershell
npm run sync:results
# Tùy chọn đường dẫn dữ liệu và báo cáo:
npm run sync:results -- data/lottery_numbers.json reports/lottery-sync.json
```

Spring Boot ghi thẳng vào PostgreSQL qua transaction, có advisory lock giữa các scheduler replica. Không cần thêm migration theo từng ngày. Muốn lấy lịch sử dài hơn hoặc bù sau thời gian dừng quá 90 ngày, tăng `LOTTERY_SYNC_LOOKBACK_DAYS` trước khi khởi động.

Đối chiếu 25/10/2026: MEGA 23/10 kỳ #01579 = **02–05–11–22–32–40**; POWER 24/10 kỳ #01415 = **06–07–18–41–44–50**, số phụ **53**. Báo cáo nhập lần đầu của resource: [lottery-sync-resources-2026-10-25.json](reports/lottery-sync-resources-2026-10-25.json); báo cáo chạy lại không trùng của Node: [lottery-sync-2026-10-25.json](reports/lottery-sync-2026-10-25.json).

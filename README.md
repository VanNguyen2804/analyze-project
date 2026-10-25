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

Chỉ nhận kết quả đủ sáu số hợp lệ; POWER bắt buộc có số phụ khác sáu số chính. Ngày hiện tại chỉ nhập sau 18:30 giờ `Asia/Ho_Chi_Minh`, không nhập ngày tương lai. Chạy lại không tạo bản ghi trùng. Lỗi mạng / dữ liệu nguồn không hợp lệ được thử lại ở lần kế tiếp, độc lập cho mỗi loại. Quy trình làm sạch bên dưới điền số phụ còn thiếu khi sáu số chính khớp và áp dụng các sửa đổi đã được đối chiếu; mọi xung đột khác được cách ly, không tự ghi đè.

Xem trạng thái bằng `GET /api/lottery-sync/status`: thời điểm kiểm tra, ngày mới nhất của nguồn, ngày dự kiến, số kỳ thêm / giữ nguyên, chi tiết xung đột hoặc lỗi. Trạng thái lần chạy nằm trong bộ nhớ và reset khi restart; kết quả đã nhập và nguồn trong `note` được lưu bền vững. `STALE` cho biết nguồn chưa có kỳ mới nhất theo lịch. Frontend lấy kết quả từ API hiện có khi tải lại lịch sử / dự đoán.

Đồng bộ thủ công cho Node (dừng Node server trước vì JSON chỉ hỗ trợ một tiến trình ghi):

```powershell
npm run sync:results
# Tùy chọn đường dẫn dữ liệu và báo cáo:
npm run sync:results -- data/lottery_numbers.json reports/lottery-sync.json
```

Spring Boot ghi thẳng vào PostgreSQL qua transaction, có advisory lock giữa các scheduler replica. Không cần thêm migration theo từng ngày. Muốn lấy lịch sử dài hơn hoặc bù sau thời gian dừng quá 90 ngày, tăng `LOTTERY_SYNC_LOOKBACK_DAYS` trước khi khởi động.

Đối chiếu 25/10/2026: MEGA 23/10 kỳ #01579 = **02–05–11–22–32–40**; POWER 24/10 kỳ #01415 = **06–07–18–41–44–50**, số phụ **53**. Báo cáo nhập lần đầu của resource: [lottery-sync-resources-2026-10-25.json](reports/lottery-sync-resources-2026-10-25.json); báo cáo chạy lại không trùng của Node: [lottery-sync-2026-10-25.json](reports/lottery-sync-2026-10-25.json).

## Làm sạch và kiểm soát chất lượng dữ liệu

Báo cáo dự đoán/đối soát lấy số kỳ, ngày, số khớp và phiên bản cấu hình từ API. Các chỉ số chưa có dữ liệu hiển thị N/A; backtest dùng cấu hình hiện tại và chỉ đo những kỳ tạo được vé sáu số. Phần đề xuất cấu hình mới chỉ cho áp dụng khi backend có một đề xuất đã được đánh giá. Danh sách thuật toán và số kỳ kiểm thử lấy từ kho dữ liệu hiện tại.

Mỗi bản ghi có `quality.status`: chưa có metadata là `UNVERIFIED`, khớp feed là `SOURCE_MATCHED`, cần đối soát là `QUARANTINED`. `SOURCE_MATCHED` có nghĩa khớp nguồn cộng đồng đã cấu hình, không phải chứng nhận độc lập từ Vietlott. Mỗi lần sửa hoặc chuyển trạng thái lưu thời điểm, nguồn, lý do, giá trị trước/sau và ghi chú cũ trong `quality.audit`.

- Scheduler điền số phụ thiếu chỉ khi sáu số chính khớp nguồn; không thay một số phụ đã có nhưng khác nguồn.
- Sửa số chính chỉ khi cả giá trị cũ và giá trị từ feed khớp một mục trong [danh sách đã đối chiếu](backend/src/main/resources/data/reviewed_draw_corrections.json). Danh sách có URL nguồn thứ hai cho bốn kỳ đã kiểm tra; không phải dữ liệu sinh dự đoán.
- Xung đột mới, bản ghi không có trong feed trong khoảng ngày đang đối soát và revision cũ bị cách ly. Giữ nguyên bản ghi để kiểm tra, không tự đổi ngày hoặc xóa.
- Dự đoán, thống kê phân tích, dò vé và backtest chỉ dùng revision mới nhất có trạng thái `SOURCE_MATCHED` và dấu nhận diện giá trị còn khớp. Sửa tay số/ngày/loại xổ số sẽ làm bản ghi mất điều kiện sử dụng cho đến lần đối soát tiếp theo. Không quay về revision cũ đã khớp nguồn nếu revision mới nhất chưa được xác minh.
- `GET /api/lottery-sync/quality` trả số bản ghi đủ điều kiện/bị loại và toàn bộ lịch sử đối soát. `/api/numbers` vẫn cho xem bản ghi bị cách ly. Lịch sử kiểm tra vé và các báo cáo backtest cũ vẫn là dữ liệu của lần chạy trước, cần tính lại khi đối chiếu với lịch sử đã sửa.
- `V10__add_draw_quality_audit.sql` chỉ thêm cột `quality_json` cho PostgreSQL, không chèn dữ liệu kỳ quay. Migration này được đổi tên từ V11 để tiếp nối V9; script V10 chèn kết quả cũ vẫn đã bị loại bỏ. Sau khi khởi động bản mới, scheduler đối soát cửa sổ mặc định 90 ngày; tăng `LOTTERY_SYNC_LOOKBACK_DAYS` nếu muốn xác minh các kỳ cũ hơn. Bản ghi ngoài cửa sổ chưa được đối soát tiếp tục bị loại khỏi tập học nếu chưa có trạng thái hợp lệ.

Trong bộ JSON hiện tại: sửa bốn kỳ, điền 11 số phụ, cách ly POWER 28/09/2026 vì không có kỳ tương ứng trong feed (dãy số này xuất hiện ở kỳ 29/09). Lần đồng bộ cũng nhận thêm MEGA 25/10. Có 79 bản ghi, 78 đủ điều kiện. Chạy lại không tạo thêm bản ghi hoặc audit event. Xem [báo cáo tổng hợp](reports/data-cleanup-summary-2026-10-25.json).

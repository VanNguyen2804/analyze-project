# Trang chứng khoán — triển khai 03/11/2026

## Sử dụng

Chọn **Chứng khoán Việt Nam** ở menu, hoặc mở `/stocks/FPT`. Có VIC, FPT, SZC, MBB, biểu đồ nến/volume/EMA20, tham chiếu VNINDEX, chế độ nến ngày và 15 phút, form phân tích một lô mua. MEGA/POWER và lịch xổ số bị ẩn trên `/stocks` và mọi route con; quay lại trang xổ số khôi phục bộ chọn.

Trang tự cập nhật mỗi 60 giây khi tab hiển thị và không có tác vụ đang chạy. Watchlist dùng nến ngày và có timestamp riêng; biểu đồ và phân tích cùng một snapshot. Đổi mã/mode hủy request cũ. Vị thế gửi POST theo snapshot đang xem, không lưu server/localStorage hoặc ghi log payload; response có `Cache-Control: no-store`.

## Dữ liệu và cấu hình

Backend gọi endpoint Entrade trong kế hoạch qua HTTP client Java, allowlist mã/interval, timeout kết nối 5 giây/request 15 giây. Nến ngày yêu cầu tối đa 5 năm; intraday 60 ngày chia cửa sổ 10 ngày. Kiểm tra OHLCV hữu hạn, biên giá, timestamp và duplicate; nếu nguồn lỗi thì giữ cache với nhãn cảnh báo và khóa nhận định hành động. Cache chung trong RAM 60 giây; không lưu dữ liệu thị trường vào database khi quyền lưu trữ chưa xác minh. Không cần migration.

Các biến trong `.env.example` phải được đưa vào môi trường chạy; Spring không tự đọc `.env`:

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `STOCKS_SOURCE_ENABLED` | `true` | Bật truy cập nguồn công khai để thử nghiệm; tắt nếu phạm vi sử dụng chưa cho phép |
| `STOCKS_SOURCE_PRICE_SCALE` | `1` | Hệ số từ đơn vị nguồn sang VND sau khi đối chiếu; không tự suy đoán 1.000 |
| `STOCKS_SOURCE_PRICE_BASIS` | `UNKNOWN` | `RAW`, `ADJUSTED` hoặc `UNKNOWN` sau khi xác minh |
| `STOCKS_SOURCE_VERIFIED` | `false` | Chỉ bật sau khi xác minh quyền dùng, đơn vị, timestamp, lịch giao dịch và cơ sở giá của mọi mã/interval dùng trong trang |
| `STOCKS_ML_BUY_THRESHOLD` | `0.60` | Ngưỡng điểm lớp mua; phải lớn hơn 0,34 và nhỏ hơn 1 |
| `STOCKS_ML_SELL_THRESHOLD` | `0.60` | Ngưỡng điểm lớp bán |
| `STOCKS_ML_ROUND_TRIP_COST` | `0.004` | Chi phí khứ hồi giả định cho nhãn huấn luyện, tỷ lệ 0,4%; không phải biểu phí giao dịch hiện hành |

Mặc định hiển thị giá theo **đơn vị nguồn chưa xác minh**, cho xem biểu đồ/chỉ báo và kết quả khảo sát mô hình; không bật tín hiệu hành động hoặc P&L VND. Không bật cờ verified chỉ để bỏ cảnh báo. P&L chỉ mở khi giá raw VND đủ mới; là chênh lệch giá chưa điều chỉnh quyền, không phải tổng lợi nhuận. Thiếu phí thì không tính lợi nhuận ròng/hòa vốn. Không biết số lượng có thể bán thì không giả định lệnh bán thực hiện được.

Daily đóng bảo thủ sau 16:00 giờ Việt Nam; intraday chỉ dùng timestamp + 15 phút đã qua. Đây chưa phải chứng nhận ý nghĩa timestamp của provider. Freshness dùng giới hạn bảo thủ; chưa có lịch nghỉ lễ chính thức, `marketStatus=UNVERIFIED_CALENDAR`. Cần hoàn thiện lịch sàn trước khi bật verified. `fetchedAt` là thời điểm tải, không phải timestamp giá mới. UI hiển thị nến chưa đóng khác màu.

Nguồn truy cập công khai không đồng nghĩa giấy phép tái phân phối. Chưa triển khai/publish trang lên hosting trong thay đổi này.

## Học máy thực sự được huấn luyện

`StockLearningEngine` là multinomial logistic regression 3 lớp, Java thuần, phiên bản `stock-softmax-purged-v1`; độc lập hoàn toàn scoring xổ số. Không thêm Python hoặc dịch vụ AI. Trọng số được học lại từ lịch sử snapshot, không phải tín hiệu được gán tay. Mô hình/chỉ báo tính trong RAM; chưa lưu model/ledger dự báo bất biến.

- Sáu đặc trưng: lợi suất 1/5 nến, chênh lệch EMA9/21, RSI14, ATR14/close, log khối lượng tương đối 20 nến trước. RSI và ATR dùng trung bình đơn, không gọi là Wilder RSI/ATR. Domain tự tính, chưa sử dụng ta4j của phương án ban đầu.
- Mục tiêu: biến động từ **open nến sau** tới close sau 10 nến (SWING) hoặc 4 nến (INTRADAY). Biên trung tính bằng chi phí giả định cộng phần biến động ATR; nhãn intraday không băng qua ngày giao dịch khác.
- Cần tối thiểu 305 nến ngày hoặc 299 nến intraday, đồng thời đủ nhãn hợp lệ. 65% phần đầu làm khởi tạo; ba đoạn kiểm định về sau, mỗi đoạn chỉ huấn luyện trên quá khứ. Purge toàn bộ horizon để nhãn train kết thúc trước feature test. Mean/std chỉ fit trên train; regularization L2 0,02, learning rate 0,08, 180 vòng; không chọn lại tham số theo báo cáo hiện tại.
- Mô hình cuối học tất cả nhãn đã trưởng thành trước snapshot. Trả số mẫu, accuracy, balanced accuracy, log loss, baseline lớp phổ biến tính trên train của từng fold, các mốc fold và trọng số chuẩn hóa đã học.
- Chỉ xét tín hiệu thử nghiệm nếu có đủ ba lớp, mỗi lớp kiểm định ≥10 mẫu, ≥60 mẫu kiểm định, accuracy vượt baseline và balanced accuracy >1/3. Sau đó cần đạt ngưỡng điểm, xác nhận MA/RSI, dữ liệu đủ mới và đã xác minh. Không đạt thì `WATCH`; lỗi chất lượng dữ liệu thì `UNAVAILABLE`.
- Điểm softmax chưa hiệu chuẩn, không phải xác suất lời. Đây là kiểm định phân loại với các nhãn có thể chồng lấn, chưa phải backtest chiến lược khớp lệnh hoặc bằng chứng lợi nhuận. Bộ kiểm định nay đã là dữ liệu phát triển, không được gọi lại là tập giữ kín nếu dùng nó để điều chỉnh mô hình.

Probe chỉ đọc có thể chạy chủ động bằng `mvn test "-Dtest=StockLiveProbeTest" "-Dstocks.liveProbe=true"`; báo cáo [JSON](reports/stocks-development-2026-11-03.json). Không chạy probe mạng trong CI thông thường. Báo cáo ghi đơn vị/cơ sở giá chưa xác minh. Với lịch sử lấy ngày 03/11/2026, cả bốn mã SWING và FPT INTRADAY đều `VALIDATION_WEAK`; không chứng minh lợi thế mua/bán.

## API và phần còn lại của kế hoạch

- `GET /api/stocks/instruments`, `GET /api/stocks/watchlist`.
- `GET /api/stocks/{symbol}/analysis?mode=SWING|INTRADAY` trả luôn nến, benchmark, metadata, nhận định, mô hình và `snapshotId`.
- `POST /api/stocks/{symbol}/position-analysis?snapshotId=...` dùng snapshot còn trong cache, kiểm tra mã trùng; snapshot hết hạn trả 400 yêu cầu tải lại.

API gộp biểu đồ và phân tích để tránh hai nguồn snapshot lệch nhau; chưa tách endpoint candles. Chưa triển khai lưu bền dữ liệu/sự kiện quyền, khung tuần/tháng, VWAP, bộ SMA200/MACD, tổng lợi nhuận sau quyền, lịch sàn đầy đủ, backtest lợi nhuận sau thanh toán/khớp lệnh, xác thực/danh mục nhiều lô hoặc đặt lệnh. Đây là bản đầu của trang và mô hình học máy, chưa phải hoàn tất mọi giai đoạn của kế hoạch gốc.

Biểu đồ dùng Lightweight Charts 5.2.0; giữ attribution và đường dẫn theo [hướng dẫn chính thức](https://tradingview.github.io/lightweight-charts/docs), [NOTICE phiên bản](https://github.com/tradingview/lightweight-charts/blob/v5.2.0/NOTICE).

## Kiểm chứng

Angular production build và Maven `clean install` đạt; Maven `install` sau bổ sung che dữ liệu vị thế trong log cũng đạt. 49 kiểm thử Angular trên Edge headless đạt, gồm route/category, hủy request và render chart thật ở hai khung cùng marker giá mua. Java phát hiện 112 kiểm thử: 104 đạt, 8 kiểm thử có điều kiện bỏ qua (không cấu hình PostgreSQL kiểm thử, báo cáo phát triển và probe mạng chủ động). Probe dữ liệu mạng được chạy riêng thành công. Không kiểm thử PostgreSQL thật ở lần này, không có migration mới. Typecheck và Prettier trên các file frontend thay đổi đạt; 24 assets Angular khớp byte trong JAR. Kiểm tra định dạng toàn repo còn báo 39 file có sẵn ngoài phần thay đổi. Build vẫn có cảnh báo budget CSS AI Lab và Java unchecked có sẵn. Chưa chạy ứng dụng đầy đủ trên database hoặc triển khai hosting.

# Trang chứng khoán — triển khai 03/11/2026

Luồng vị thế mặc định hiện chỉ nhập giá vốn và số lượng; tự lập kịch bản theo nến. Xem [thiết kế hai thông tin](STOCK_TWO_INPUT_SCENARIOS.md). Các mục hạn mức và execution bên dưới mô tả kế hoạch thủ công cũ, vẫn được giữ tương thích ở API.

Cập nhật 04/11/2026: sửa vùng vẽ khi layout ban đầu chưa có kích thước; chủ động resize ở animation frame kế tiếp, chuẩn hóa ngày nến độc lập locale. Bỏ hai ô phí mua/chi phí bán và số lãi/lỗ ròng trên giao diện. Phân tích vị thế trả lãi/lỗ theo giá tham khảo khi có đơn vị quy đổi rõ ràng, tách khỏi điều kiện xác nhận tín hiệu mua/bán.

Kiểm chứng bản sửa 04/11: Angular build và Maven install đạt; 54 kiểm thử Angular và 113 kiểm thử Java đạt, tám kiểm thử Java có điều kiện bỏ qua. Typecheck/Prettier phần frontend sửa đạt; 24 assets khớp byte trong JAR. Kiểm thử có tình huống vùng biểu đồ ban đầu ẩn rồi hiện, đổi khung, nhập vị thế không có phí, quy đổi nghìn VND đúng một lần và dữ liệu giá cũ. Truy cập đọc nguồn Entrade thành công cho cả bốn mã; chưa triển khai hosting hoặc kiểm thử toàn ứng dụng với PostgreSQL thật trong lần sửa này.

## Sử dụng

### Chart production và bảng nhận định ngày — 04/11/2026

Kiểm chứng lần cập nhật này: Angular build và Maven install đạt; 60 kiểm thử Angular và 122 kiểm thử Java đạt, 9 kiểm thử Java có điều kiện bỏ qua. Đã kiểm tra trực quan production bundle bằng dữ liệu VIC thật. Chưa triển khai hosting hoặc chạy tích hợp với PostgreSQL thật.

Tái hiện lỗi bằng response VIC thật trên production bundle: hai timestamp `1672099200` và `1672106400` cùng ngày 27/12/2022 làm trùng time trên chart. Renderer ném `Value is null`, trục giá vẫn hiện nhưng nến/EMA không vẽ. Adapter và frontend hiện giữ bản timestamp muộn nhất mỗi business date, không cộng volume. Bản muộn có close 26,7 và volume 1.844.000; không cộng thêm 317.300 từ bản sớm. Đối chiếu ảnh production trước/sau cùng response đã xác nhận nến/EMA được vẽ lại. Regression kiểm tra cả dữ liệu trùng ngày và pixel màu trong pane, không chỉ canvas tồn tại.

Chart có EMA20/50, mây Ichimoku tô giữa Senkou A/B bật/tắt và khoảng xem 1M/3M/6M/toàn bộ; mode 15 phút có 1/5/20 phiên. UI giữ nội dung khi polling cùng mã/mode, thu gọn chú giải và thêm `stock-daily-desk`.

`dailyDesk` trả tối đa 10 phiên đóng gần nhất, độc lập mode đang xem. Chọn ngày để đọc bảng **hiện tại / điều kiện nghiêng mua / điều kiện nghiêng bán / trạng thái**. Mỗi ngày dùng đúng prefix lịch sử tới ngày đó; đây là tính lại nhận định, không phải snapshot dự đoán đã chốt trước ngày quay.

- RSI14 dùng Wilder, so với phiên trước: mua cần RSI tăng trong 50–70, giảm dưới 50 cảnh báo yếu; quá mua/quá bán không tự tạo lệnh.
- EMA20/50/200 seed từ close đầu prefix. Bảng ghi giá đường trung bình và bất đẳng thức phải đạt; EMA200 cần đủ 200 nến.
- Ichimoku dùng Tenkan9/Kijun26 và Senkou A/B9/26/52 dịch 26 phiên. Mây đang nhìn tại i dùng giá trị tính ở i−26, cần 78 nến; ghi đỉnh/đáy mây và vị trí giá. Bản này không dùng Chikou làm điều kiện giao dịch.
- Volume ngày so TB20 nến trước, ghi số cổ phiếu hiện có và mức cần đạt 1,2×. Khối kịch bản intraday vẫn so cùng giờ phiên trước.
- Fibonacci lấy high/low 60 nến trước phiên đánh giá, ghi ngày đáy/đỉnh, hướng và các mức 23,6/38,2/50/61,8/78,6%. Đáy trước đỉnh dùng retracement từ đỉnh; đỉnh trước đáy dùng hồi từ đáy. Đây là biên cửa sổ, không phải nhận diện sóng. Bảng ghi giá cần vượt/mất và yêu cầu kết hợp EMA/volume.

Số điều kiện nghiêng mua/bán trên sáu hàng không phải xác suất thắng. Bối cảnh tăng/giảm cần ít nhất bốn điều kiện cùng phía và không quá một điều kiện phía kia; không tự thay cổng xác nhận giá/volume hoặc kiểm tra nguồn. Các ngưỡng thay đổi khi có nến mới. Tham khảo khái niệm/công thức [Ichimoku](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/ichimoku-cloud), [Fibonacci](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/fibonacci-retracement); cách chọn cửa sổ/phối hợp là quy tắc thử nghiệm của ứng dụng.

Chuẩn hóa ngày và RSI/EMA thay đổi đầu vào: model mới `stock-softmax-purged-v2-wilder`, kịch bản giá `stock-price-scenarios-v2-wilder`. Báo cáo JSON 03/11 là phiên bản cũ, chưa chứng minh hiệu quả phiên bản mới. Không sửa kết quả lịch sử để coi như đã đánh giá lại.

### Khuyến nghị theo mốc giá — 04/11/2026

Khối kết luận chính dùng lời và mốc giá, thay điểm mua/bán dạng phần trăm. Điểm softmax và metric kiểm định nằm trong details thu gọn; ghi rõ không phải tỷ lệ vốn nên mua/bán hoặc xác suất có lời. `analysis.recommendation` là hợp đồng riêng của quy tắc kỹ thuật `stock-price-scenarios-v1`; giữ `analysis.signal` cũ để tương thích. Nhận định vị thế lấy action của quy tắc giá mới, vẫn xét điều kiện rủi ro và khả năng bán.

Mốc hỗ trợ/kháng cự lấy low/high của 20 nến đã đóng **trước** nến đánh giá. Mốc mua = kháng cự + 0,25 ATR; mốc giảm/bán = hỗ trợ − 0,25 ATR (không âm). Chỉ cân nhắc mua khi close vượt mốc mua, MA nhanh > MA chậm, RSI <70, volume ≥1,2 lần mức so sánh và giá chưa đi quá mốc mua hơn 1 ATR. Thiếu xác nhận thì giải thích yếu tố thiếu; high vượt mốc nhưng close không giữ được được mô tả là nguy cơ vượt giả. Close dưới mốc giảm/bán đưa kịch bản giảm rủi ro **cho người đang nắm giữ**, không phải bán khống.

Volume SWING so với 20 nến trước. INTRADAY so cùng timestamp giờ Việt Nam của tối đa 20 phiên trước, cần ít nhất năm phiên khác ngày; không trộn volume các thời điểm khác nhau trong phiên. Dữ liệu thiếu, đang hình thành, cũ hoặc chưa verified/cơ sở giá UNKNOWN không được dùng để xác nhận hành động. Nến đang hình thành không đổi kết luận từ nến đã đóng. Các kịch bản vẫn có thể hiển thị với nhãn chờ xác nhận khi nguồn còn hạn chế.

Vùng dừng lỗ/chốt lời cho kịch bản mua được tính từ mốc mua theo −1,5 ATR / +3 ATR, không phải giá chắc chắn sẽ đạt, không phải điểm thoát vị thế riêng theo giá vốn. Tham số là cấu hình ban đầu chưa có backtest chứng minh hiệu quả; khuyến nghị kỹ thuật tách khỏi phân loại học máy. Giá và các mốc dùng cùng đơn vị của biểu đồ; ghi thời điểm nến dùng đánh giá. Cơ sở tham khảo khái niệm: [Fidelity về hỗ trợ/kháng cự](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/support-and-resistance), [Schwab về volume và breakout](https://www.schwab.com/learn/story/trading-volume-as-market-indicator). Các hệ số cụ thể bên trên là lựa chọn triển khai, không phải tham số được hai nguồn xác nhận hiệu quả.

Có thể cấu hình bằng `STOCKS_RECOMMENDATION_BREAKOUT_ATR` (0,25; miền 0–2), `STOCKS_RECOMMENDATION_VOLUME_MULTIPLE` (1,2; miền 1–5), `STOCKS_RECOMMENDATION_STOP_ATR` (1,5; >0 đến 10), `STOCKS_RECOMMENDATION_TARGET_ATR` (3; lớn hơn stop và ≤20), `STOCKS_RECOMMENDATION_MAX_BUY_RSI` (70; miền 50–90). Mọi hệ số phải hữu hạn, cấu hình sai làm startup thất bại rõ ràng.

Kiểm chứng phần khuyến nghị: Angular build và Maven install đạt; 56 kiểm thử Angular và 118 kiểm thử Java đạt, tám kiểm thử Java có điều kiện bỏ qua. Kiểm tra các trường hợp vượt thật/vượt giả, volume yếu, giá đã đi xa, phá hỗ trợ, dữ liệu cũ/chưa xác minh, nến đang chạy và so volume cùng giờ; kiểm tra điểm % mặc định thu gọn. Typecheck/Prettier frontend sửa đạt, 24 assets frontend khớp byte trong JAR. Chưa backtest hiệu quả riêng của bộ quy tắc mới, chưa chạy PostgreSQL thật hoặc deploy trong lần thay đổi này.

Chọn **Chứng khoán Việt Nam** ở menu, hoặc mở `/stocks/FPT`. Có VIC, FPT, SZC, MBB, biểu đồ nến/volume/EMA20, tham chiếu VNINDEX, chế độ nến ngày và 15 phút, form phân tích một lô mua. MEGA/POWER và lịch xổ số bị ẩn trên `/stocks` và mọi route con; quay lại trang xổ số khôi phục bộ chọn.

Trang tự cập nhật mỗi 60 giây khi tab hiển thị và không có tác vụ đang chạy. Watchlist dùng nến ngày và có timestamp riêng; biểu đồ và phân tích cùng một snapshot. Đổi mã/mode hủy request cũ. Vị thế gửi POST theo snapshot đang xem, không lưu server/localStorage hoặc ghi log payload; response có `Cache-Control: no-store`.

## Dữ liệu và cấu hình

Backend gọi endpoint Entrade trong kế hoạch qua HTTP client Java, allowlist mã/interval, timeout kết nối 5 giây/request 15 giây. Nến ngày yêu cầu tối đa 5 năm; intraday 60 ngày chia cửa sổ 10 ngày. Kiểm tra OHLCV hữu hạn, biên giá, timestamp và duplicate; nếu nguồn lỗi thì giữ cache với nhãn cảnh báo và khóa nhận định hành động. Cache chung trong RAM 60 giây; không lưu dữ liệu thị trường vào database khi quyền lưu trữ chưa xác minh. Không cần migration.

Các biến trong `.env.example` phải được đưa vào môi trường chạy; Spring không tự đọc `.env`:

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `STOCKS_SOURCE_ENABLED` | `true` | Bật truy cập nguồn công khai để thử nghiệm; tắt nếu phạm vi sử dụng chưa cho phép |
| `STOCKS_SOURCE_PRICE_SCALE` | `1000` | Quy đổi giá nến cổ phiếu Entrade từ nghìn đồng sang VND. Nếu cấu hình cũ dùng `1`, metadata là `THOUSAND_VND` và vị thế quy đổi một lần sang VND; VNINDEX luôn là điểm |
| `STOCKS_SOURCE_PRICE_BASIS` | `UNKNOWN` | `RAW`, `ADJUSTED` hoặc `UNKNOWN` sau khi xác minh |
| `STOCKS_SOURCE_VERIFIED` | `false` | Chỉ bật sau khi xác minh quyền dùng, đơn vị, timestamp, lịch giao dịch và cơ sở giá của mọi mã/interval dùng trong trang |
| `STOCKS_ML_BUY_THRESHOLD` | `0.60` | Ngưỡng điểm lớp mua; phải lớn hơn 0,34 và nhỏ hơn 1 |
| `STOCKS_ML_SELL_THRESHOLD` | `0.60` | Ngưỡng điểm lớp bán |
| `STOCKS_ML_ROUND_TRIP_COST` | `0.004` | Chi phí khứ hồi giả định cho nhãn huấn luyện, tỷ lệ 0,4%; không phải biểu phí giao dịch hiện hành |

Mặc định hiển thị giá nến cổ phiếu theo VND; cờ verified tiếp tục tắt và tín hiệu hành động vẫn bị hạn chế. Phép tính lãi/lỗ theo giá không phụ thuộc cờ xác minh tổng thể: dùng giá cuối cùng của snapshot và ghi mốc nến, cảnh báo khi dữ liệu cũ/đang hình thành hoặc cơ sở giá chưa xác nhận. Không có giá, đơn vị không quy đổi được, chỉ có giá ADJUSTED hoặc ngày giá trước ngày mua thì không tính. Kết quả chưa tính phí, thuế, cổ tức hoặc thay đổi số lượng do quyền; không hiển thị lợi nhuận ròng/hòa vốn. Không biết số lượng có thể bán thì không giả định lệnh bán thực hiện được. Không bật verified chỉ để bỏ cảnh báo.

Daily đóng bảo thủ sau 16:00 giờ Việt Nam; intraday chỉ dùng timestamp + 15 phút đã qua. Đây chưa phải chứng nhận ý nghĩa timestamp của provider. Freshness dùng giới hạn bảo thủ; chưa có lịch nghỉ lễ chính thức, `marketStatus=UNVERIFIED_CALENDAR`. Cần hoàn thiện lịch sàn trước khi bật verified. `fetchedAt` là thời điểm tải, không phải timestamp giá mới. UI hiển thị nến chưa đóng khác màu.

Nguồn truy cập công khai không đồng nghĩa giấy phép tái phân phối. Chưa triển khai/publish trang lên hosting trong thay đổi này.

## Học máy thực sự được huấn luyện

`StockLearningEngine` là multinomial logistic regression 3 lớp, Java thuần, phiên bản `stock-softmax-purged-v2-wilder`; độc lập hoàn toàn scoring xổ số. Không thêm Python hoặc dịch vụ AI. Trọng số được học lại từ lịch sử snapshot, không phải tín hiệu được gán tay. Mô hình/chỉ báo tính trong RAM; chưa lưu model/ledger dự báo bất biến.

- Sáu đặc trưng: lợi suất 1/5 nến, chênh lệch EMA9/21, RSI14 Wilder, ATR14/close, log khối lượng tương đối 20 nến trước. ATR vẫn dùng trung bình đơn true range, không gọi là Wilder ATR. Domain tự tính, chưa sử dụng ta4j của phương án ban đầu.
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

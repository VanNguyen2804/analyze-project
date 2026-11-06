# Kế hoạch nâng cấp nhận định vị thế và UI chứng khoán

Ngày lập: 05/11/2026. Trạng thái: đã triển khai ngày 06/11/2026; xem báo cáo `reports/stock-position-ui-2026-11-06.md`.

## 1. Mục tiêu và phạm vi

Trang trả lời ba câu hỏi dựa trên giá vốn của người dùng: đang lãi/lỗ bao nhiêu,
nếu tiếp tục giảm thì xử lý thế nào, nếu tăng thì chốt lời theo các bước nào.
Người dùng đã chọn giao diện **nền tối xanh navy, điểm nhấn xanh ngọc và tím**.

Bản đầu áp dụng cho các mã và hai chế độ phân tích hiện có. Kế hoạch dùng giá vốn
bình quân cùng số lượng đang giữ của một mã; hỗ trợ mô phỏng mua thêm và bán từng
phần. Nhận định thị trường, nhận định theo ngày và nhận định vị thế có nhãn riêng.
Việc ghi nhận giao dịch do người dùng nhập; ứng dụng không kết nối đặt lệnh.

## 2. Hiện trạng đã kiểm tra

| Thành phần | Đã có | Cần thay đổi |
| --- | --- | --- |
| `stocks.component` | Giá mua, số lượng, ngày mua, số có thể bán, P&L | Ngưỡng bị ép 10%; input dùng chung giữa mã; polling làm mất kết quả |
| `StockService.position()` | Tính P&L theo snapshot, quy đổi VND/nghìn VND | Bỏ qua `maxLossPercent`; nhánh cắt lỗ chưa dùng đủ cổng chất lượng dữ liệu |
| `StockRecommendationEngine` | Mốc mua/bán, stop/target theo ATR | Đây là kịch bản mua từ breakout, chưa phải mốc theo giá vốn cá nhân |
| `StockDailyDesk` | RSI, EMA, Ichimoku, volume, Fibonacci theo từng ngày | Chuyển bảng rộng sang thẻ trên mobile; giữ đúng mốc lịch sử |
| `StockChartComponent` | Nến, EMA, mây, đường giá mua | Thêm các mốc của kế hoạch và bật/tắt lớp vị thế |
| API vị thế | Gắn `snapshotId`, response `no-store` | Trả kế hoạch có cấu trúc và thời điểm tính rõ ràng |

Kế hoạch này mở rộng tài liệu `STOCKS_IMPLEMENTATION.md` và
`VIETNAM_STOCKS_PAGE_PLAN.md`, không coi những phần đã có là tính năng cần xây lại.

## 3. Đầu vào và cách quản lý vị thế

**Nhóm chính:** giá vốn bình quân (VND/cổ phiếu), số lượng đang giữ, số lượng có thể
bán và ngày mua nếu biết. Đổi nhãn “giá mua” thành “giá vốn bình quân” để người đã
mua nhiều lần không nhập nhầm giá của một lô.

**Thiết lập kế hoạch:** ngưỡng cảnh báo lỗ, giới hạn dừng rủi ro, ngân sách mua thêm,
giới hạn tổng vốn cho mã, số đợt mua thêm và các mục tiêu chốt lời. Mức 10% hiện có
không còn bị ép ở frontend/backend. Các ngưỡng là cấu hình người dùng, không được
trình bày như tham số đã chứng minh hiệu quả. Bình giá cần có ngân sách rõ ràng;
thiếu ngân sách thì chỉ hiển thị điều kiện và yêu cầu bổ sung.

Lưu state trong phiên theo mã; đổi chế độ biểu đồ không đổi chủ sở hữu vị thế.
Refresh giữ form và kế hoạch gần nhất, đánh dấu đang cập nhật, chỉ thay kết quả
khi response khớp mã, snapshot và phiên bản input hiện hành. Giao dịch đã nhập
không bị polling đánh dấu thực hiện lại. Phiên bản đầu chưa thêm lưu danh mục
server hoặc tự bật lưu dữ liệu tài chính dài hạn trong trình duyệt.

## 4. Bộ quy tắc kế hoạch

### 4.1. Hai ngưỡng lỗ riêng biệt

- **Ngưỡng cảnh báo:** bắt đầu đánh giá phương án giữ, mua thêm có điều kiện hoặc
  giảm vị thế. Đây là mức giảm tính từ giá vốn tham chiếu lúc lập kế hoạch.
- **Giới hạn dừng rủi ro:** giá hủy kế hoạch hoặc trần lỗ bằng tiền; có độ ưu tiên
  cao hơn phương án mua thêm. Ngưỡng cảnh báo phải nằm trước giới hạn này.
- Khi bình giá, giữ mốc dừng và ngân sách rủi ro gốc; không tự hạ mốc dừng theo giá
  vốn mới hoặc tăng ngân sách để hợp thức hóa việc mua thêm.

| Trạng thái | Nhận định và nội dung hiển thị |
| --- | --- |
| Chưa chạm cảnh báo | Tiếp tục theo dõi, khoảng cách tới ngưỡng lỗ và mục tiêu lời |
| Vượt cảnh báo, còn điều kiện bảo vệ | Mô phỏng các đợt bình giá, chỉ rõ điều kiện đã đạt/còn thiếu |
| Vượt cảnh báo, thiếu ngân sách/xác nhận | Chờ xác nhận; nêu lý do chưa đủ điều kiện mua thêm |
| Mất mốc bảo vệ hoặc vượt trần rủi ro | Hủy phương án mua thêm; đánh giá giảm vị thế theo lượng có thể bán |
| Đạt mục tiêu lời | Hiển thị bước chốt lời tương ứng và bảo vệ phần còn lại |
| Thiếu dữ liệu tin cậy | Chỉ tính kịch bản giả định; chưa xác nhận hành động |

### 4.2. Bình quân giá có điều kiện

Cho phép lập tối đa hai đợt mua thêm trong bản đầu. Mỗi đợt có vùng giá tham khảo,
điều kiện xác nhận, số lượng, vốn cần thêm và điều kiện hủy. Ngân sách mỗi đợt do
người dùng phân bổ; tổng không vượt ngân sách còn lại hoặc giới hạn vốn của mã.

Mốc tham khảo lấy từ hỗ trợ và cấu trúc giá đã có. Điều kiện thử nghiệm ban đầu:
giá dự kiến mua thấp hơn giá vốn, còn trên mốc dừng; nến đánh giá đã đóng giữ/lấy
lại hỗ trợ; đóng cửa không thấp hơn nến trước; volume đạt ngưỡng xác nhận của chế
độ hiện hành. Đây là quy tắc sản phẩm cần đánh giá, không khẳng định tối ưu.
Nến trong ngày và nến ngày dùng đúng bộ xác nhận riêng của chúng.

Tính lại giới hạn rủi ro sau từng đợt theo **tiền**, không chỉ theo % trên giá vốn
mới. Nếu không còn khối lượng mua hợp lệ trong hạn mức thì đợt đó bị chặn.
Chuẩn hóa bước giá/lô theo metadata được xác minh; khi thiếu metadata, hiển thị
lượng nguyên giả định và chưa khẳng định đó là lượng lệnh hợp lệ trên sàn.

`Giá vốn mới = (giá vốn × lượng đang giữ + giá mua thêm × lượng mua thêm) / tổng lượng`

Thẻ mô phỏng hiển thị trước/sau: tổng vốn, giá vốn, lượng giữ, P&L tại giá tham
chiếu, mức tăng cần để hòa vốn và lỗ giả định nếu giá về mốc dừng. Số tiền lỗ tại
mốc dừng tính cả vốn tăng thêm; đối với kế hoạch có bán một phần, tính thêm tiền
đã thu và phần vị thế còn lại để tránh mất dấu lỗ của cả kế hoạch.

Ví dụ kiểm thử số học, không phải ngưỡng đầu tư đề xuất: đang giữ 1.000 cổ phiếu
giá vốn 30.000; giả định mua thêm 500 ở 27.000 thì vốn thêm 13,5 triệu và giá vốn
mới 29.000. Tại giá 27.000, lỗ gộp vẫn là 3 triệu trước phí. Nếu giá tiếp tục về
25.000, lỗ gộp sau mua thêm là 6 triệu thay vì 5 triệu khi không mua thêm.

### 4.3. Chốt lời theo giai đoạn

Hai mốc TP1/TP2 do người dùng đặt theo % trên giá vốn hoặc giá cụ thể; mốc sau
phải cao hơn mốc trước. Hỗ trợ/kháng cự và ATR được hiển thị làm bối cảnh để đánh
giá các mục tiêu, không thay thế giá vốn cá nhân.

- Tỷ lệ bán được tính trên lượng gốc tại lúc tạo phiên bản kế hoạch; ghi rõ cơ sở
  này. Tổng lượng các bước không vượt lượng đang giữ và lượng có thể bán.
- Mỗi bước có giá kích hoạt, lượng bán, tiền thu dự kiến, lãi gộp và lượng còn lại.
- Khi giá vượt nhiều mốc trong một lần cập nhật, tổng lượng vẫn tuân thủ giới hạn;
  không tự coi các bước trước đã khớp. Nếu chưa biết lượng có thể bán, bước ở trạng
  thái cần xác nhận.
- Bước đã được người dùng ghi nhận thực hiện không được đề xuất lại. Cần nhập giá
  và lượng thực tế để cập nhật kế hoạch; chạm mốc giá chỉ là điều kiện kích hoạt.
- Phần còn lại có mốc bảo vệ lợi nhuận tùy chọn. Chỉ triển khai trailing khi có
  mốc bắt đầu rõ ràng và giữ được đỉnh đã quan sát trong phiên kế hoạch; stop của
  vị thế mua chỉ được nâng lên, không tự hạ xuống khi giá giảm.

Các tiền/lãi ở bản đầu là gộp trước phí/thuế; không đưa lại các ô phí vào form
chính hoặc gọi kết quả là lãi ròng khi chưa có dữ liệu chi phí.

## 5. Dữ liệu và kiến trúc

Thêm `StockPositionPlanEngine` thuần tính toán, nhận snapshot, vị thế và policy.
`StockService` xử lý validation, lấy snapshot và điều phối. Giữ engine nhận định
thị trường riêng; kế hoạch vị thế tham chiếu các mức giá có cấu trúc thay vì đọc
chuỗi lời giải thích hay dùng điểm softmax làm tỷ lệ vốn.

Mở rộng request/response vị thế bằng cấu hình và `positionPlan`:

- `version`, `policy`, `snapshotId`, phiên bản input và mốc lập kế hoạch;
- `valuationPrice/valuationAsOf` cho P&L và `decisionPrice/decisionAsOf` cho nến
  xác nhận; đơn vị và cơ sở giá;
- trạng thái kế hoạch, số vốn/lượng còn lại, các mốc bảo vệ và mục tiêu;
- danh sách bước với trigger, điều kiện, số lượng, tiền, trạng thái và lý do chặn;
- giá trị trước/sau và các giả định chưa được xác nhận.

Cổng dữ liệu dùng chung cho khuyến nghị thị trường và vị thế: nguồn đã xác minh,
đủ mới, có đơn vị quy đổi và giá RAW tương thích với giá vốn. Giá nến đang hình
thành có thể phục vụ P&L tham khảo nhưng không dùng làm xác nhận đã đóng nến.
UNKNOWN/ADJUSTED hoặc sự kiện quyền chưa xử lý phải hiện lý do không thể so sánh
trực tiếp; không bỏ cổng này trong nhánh vượt ngưỡng lỗ.

Freshness hiện dùng thời gian đơn giản, chưa có lịch nghỉ sàn đầy đủ. Giữ cách
chặn thận trọng và hiển thị timestamp thật; cải thiện lịch phiên khi có nguồn
được xác minh, không tự đổi nhãn stale thành realtime.

Khi chọn ngày lịch sử trong Daily Desk, chỉ đổi nhận định kỹ thuật ngày đó.
Kế hoạch vị thế vẫn ghi rõ mốc snapshot hiện hành, không gán giá vốn hiện tại
cho một quyết định lịch sử hoặc dùng dữ liệu tương lai để dựng lại kế hoạch cũ.

## 6. Thiết kế UI đã chọn

**Màu và hình thức:** nền navy `#08111F`, thẻ `#111E33`, viền `#253653`, chữ chính
`#E6EEF8`; teal `#2DD4BF` cho hành động/lãi, tím `#A78BFA` cho lớp kế hoạch,
đỏ hồng `#FB7185` cho lỗ/rủi ro, amber `#FBBF24` cho chờ xác nhận. Dùng gradient
nhẹ ở thẻ tổng quan, chữ số tabular và khoảng trắng rõ. Kiểm tra tương phản và
focus khi triển khai; trạng thái luôn có nhãn/icon, không chỉ phụ thuộc màu.

**Bố cục desktop:**

1. Header mã cổ phiếu + watchlist + giá/thay đổi + thời gian nguồn.
2. Dải KPI: giá vốn, giá tham chiếu, lãi/lỗ VND và %, vốn đang dùng.
3. Lưới 8/4: biểu đồ lớn bên trái, “Kế hoạch vị thế” bên phải. Form chỉnh giá vốn
   và hạn mức mở theo nhu cầu thay vì chiếm toàn bộ phần đầu trang.
4. Thẻ kịch bản bình giá/chốt lời với trạng thái và so sánh trước/sau.
5. “Cơ sở nhận định”: Daily Desk, góc nhìn thị trường, chỉ báo và kiểm định mô hình.

Biểu đồ thêm lớp bật/tắt giá vốn, cảnh báo lỗ, mốc hủy, vùng bình giá, TP1/TP2;
tooltip chỉ rõ nguồn mức giá và cùng đơn vị biểu đồ. Thống nhất theme tối cả chart,
form, dropdown, bảng và các component con để không trộn mảng sáng/tối.

**Mobile:** KPI hai cột; kế hoạch và trạng thái lên trước biểu đồ; form trong panel
thu gọn; Daily Desk dùng thẻ thay bảng cuộn ngang; nút cập nhật dễ chạm và không
che nội dung/focus. Kiểm tra ở 390, 768 và 1440px.

Tái sử dụng chart/daily desk/recommendation. Tách component vị thế thành form,
tổng quan và kế hoạch; `StocksComponent` giữ điều phối request/state theo mã.

## 7. Thứ tự triển khai và tiêu chí hoàn tất

| Giai đoạn | Đầu ra | Tiêu chí kiểm chứng |
| --- | --- | --- |
| 1. Nền tảng vị thế | Cấu hình ngưỡng thật, state theo mã, hai mốc thời gian, cổng dữ liệu | Đổi mã không lẫn giá vốn; refresh không mất kế hoạch; dữ liệu không đủ không xác nhận hành động |
| 2. Bình giá | Engine, hai ngưỡng, các đợt và mô phỏng trước/sau | Đúng công thức; không vượt vốn/rủi ro; không hạ stop gốc; thiếu điều kiện thì giải thích |
| 3. Chốt lời | Hai mục tiêu, bán từng phần, ghi nhận thực tế | Đúng lượng; không vượt sellable; không lặp bước; không tự coi giao dịch đã khớp |
| 4. UI navy/teal/tím | Bố cục mới, component vị thế, chart levels và mobile cards | Dễ đọc lãi/lỗ và bước tiếp theo; màu/nhãn thống nhất; responsive và bàn phím hoạt động |
| 5. Kiểm thử tích hợp | Unit/API/frontend, build và kiểm tra trực quan | Cùng input/snapshot/policy cho cùng kết quả; không ảnh hưởng trang xổ số |

Ca kiểm thử trọng yếu: biên đúng hai ngưỡng lỗ; cả hai ngưỡng bị vượt cùng lúc;
ngân sách không đủ một đơn vị mua; giá gap vượt nhiều mục tiêu; quantity/sellable
thiếu hoặc bằng 0; VND/nghìn VND; raw/adjusted/unknown; nến chưa đóng; giá cũ;
ngày giá trước ngày mua; sửa input/đổi mã khi request còn chạy; mua/bán giả định
khác giao dịch đã ghi nhận; đường trên chart được thay/xóa đúng, không tích lũy.

Các kiểm thử chức năng xác nhận tính toán và hành vi sản phẩm. Đánh giá hiệu quả
đầu tư là công việc riêng: chốt policy rồi đo trên dữ liệu tương lai hoặc mô phỏng
lịch sử có chi phí, trượt giá và hạn chế thực thi; không suy ra hiệu quả từ UI đẹp
hoặc vài ví dụ tính đúng.

## 8. Cơ sở tham khảo

Các quy tắc cụ thể ở trên là đề xuất thiết kế của ứng dụng. Nguyên tắc yêu cầu
ngân sách, thời hạn và mức chịu rủi ro tham khảo [Investor.gov — Asset Allocation
and Diversification](https://www.investor.gov/introduction-investing/getting-started/asset-allocation).
Phân biệt mức kích hoạt với giá thực hiện tham khảo [Investor.gov — Stop, Stop-Limit,
and Trailing Stop Orders](https://www.investor.gov/introduction-investing/general-resources/news-alerts/alerts-bulletins/investor-bulletins-15).
Đây là tài liệu khái niệm; không dùng nó để suy ra loại lệnh hoặc quy định giao
dịch của sàn Việt Nam. Các ngưỡng, tỷ lệ và điều kiện cần được kiểm chứng riêng.

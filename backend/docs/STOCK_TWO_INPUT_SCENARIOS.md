# Kịch bản vị thế từ hai thông tin

Trang Stock chỉ yêu cầu giá vốn bình quân (VND/cổ phiếu) và số cổ phiếu đang giữ. Form đặt trước các kịch bản ở cột vị thế, kể cả trên mobile. Không yêu cầu ngày mua, lượng có thể bán, phần trăm dừng lỗ, ngân sách thêm, tổng vốn hoặc phân bổ từng đợt. Dữ liệu vị thế vẫn chỉ ở bộ nhớ theo mã trong phiên trình duyệt.

## Luồng và API

`StockApiService` gửi `{purchasePrice, quantity}` đến endpoint position-analysis với snapshotId đang xem. Không tự điền ngân sách hoặc suy ra số cổ phiếu có thể bán. Backend trả P&L theo giá mới nhất và `scenarios` tính từ nến đã đóng. P&L nến đang chạy có thể đổi, còn giá/điều kiện kịch bản không lấy dữ liệu nến đó. `positionPlan` và các API hạn mức/giao dịch cũ vẫn tương thích cho client có kế hoạch rõ ràng; giao diện mới không tạo execution hoặc ledger giao dịch. Nếu chủ động chuyển kế hoạch cũ sang chế độ mới, giá vốn/lượng còn lại được giữ, các hạn mức cũ không chuyển thành dữ liệu đã xác nhận.

`StockCandleScenarios` là domain thuần Java, dùng `Policy` có validation; phiên bản `stock-candle-scenarios-v1`. Hỗ trợ/kháng cự lấy low/high 20 nến đóng trước nến đánh giá; EMA/RSI và ATR14 dùng định nghĩa hiện có. Đây là quy tắc thử nghiệm, chưa được kiểm định lợi nhuận.

- Mua hồi phục: mốc hỗ trợ + 0,25 ATR; nến kiểm tra vùng này rồi giữ lại hỗ trợ, đóng không thấp hơn mở, EMA nhanh trên chậm, RSI dưới 70 và khối lượng xác nhận của khung đang xem.
- Mua vượt kháng cự: kháng cự + 0,25 ATR; nến đóng vượt mốc, chưa xa hơn một ATR, cùng điều kiện xu hướng/RSI/khối lượng.
- Hai cách mua là phương án thay thế. Mỗi cách mô phỏng `floor(25% × lượng đang giữ)` cổ phiếu. Số tiền cần bổ sung là giá kích hoạt × lượng mô phỏng, không phải ngân sách đã có hay mức vốn phù hợp với người dùng.
- Bán đầu ở kháng cự với 50% lượng gốc, làm tròn xuống nhưng ít nhất một cổ phiếu; bán phần còn lại ở kháng cự + 2 ATR. Nếu mốc dưới giá vốn, gọi là giảm vị thế khi hồi phục, không gọi là chốt lời. Số lượng và P&L của từng phần không cộng hai lần; mốc mở rộng mô phỏng đã bán phần đầu.
- Giảm rủi ro nếu nến đóng tại/dưới hỗ trợ − 0,25 ATR. Khi mốc này không dương, fallback dương dùng khoảng cách 1,5 ATR tính từ giá đóng. Bán bảo vệ là phương án thay thế cho các mục tiêu bán, không cộng thêm lượng bán.

Mốc trên là kỹ thuật theo snapshot, tự thay đổi khi nến đóng thay đổi; không thay cho ngưỡng chịu lỗ cá nhân hoặc các lệnh chờ đã đặt. Chưa biết sức mua, lượng đã về tài khoản, lô/bước giá hoặc phí/thuế; UI ghi rõ lượng nguyên là mô phỏng. Không dự đoán chắc chắn giá tương lai. Xem khái niệm [ATR](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/atr) và [hỗ trợ/kháng cự](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/support-and-resistance?print=true); các hệ số ở đây là policy của ứng dụng, không phải khuyến nghị đã được những nguồn đó xác nhận.

## Chất lượng dữ liệu và kiểm chứng

Chỉ xác nhận điều kiện khi dữ liệu RAW đã xác minh, đơn vị VND hoặc THOUSAND_VND rõ ràng và còn mới. Giá điều chỉnh/không rõ đơn vị không so với giá vốn thô. Thiếu 55 nến đóng hoặc ATR bằng 0 không tạo mốc giả. Dữ liệu chưa xác minh/cũ chỉ hiển thị tham khảo; sau khi sửa vị thế/đổi snapshot, UI bỏ trạng thái đạt điều kiện cho đến lần tính lại. Các đường mốc trên chart gắn đúng snapshot và quy đổi về đơn vị biểu đồ.

Kiểm thử gồm chỉ hai trường nhập và hai trường request; các phương án mua/bán và tính giá vốn; không tự xác nhận tiền/lượng có thể bán; không mua thêm khi mất hỗ trợ; quy đổi VND/THOUSAND_VND; nến chưa đóng không đổi mức kỹ thuật; thiếu dữ liệu/ATR 0/cũ/chưa xác minh; form không còn hạn mức; trạng thái stale không xác nhận điều kiện. Fixture browser được xuất từ test backend bằng system property `stock.scenario.report`, chỉ chứa dữ liệu tổng hợp để kiểm tra UI.

Kiểm chứng hoàn tất: Angular formatter/typecheck, 84 test trình duyệt và production build đạt; Maven install phát hiện 178 test, 171 đạt/bảy optional bỏ qua, gồm PostgreSQL thật và OnionArchitectureTest. 26 assets Angular trong JAR khớp byte với bản build. Trình duyệt desktop 1440 px/mobile 390 px có đúng hai input và hai trường request, hiển thị năm kịch bản cùng mốc trên chart, không lỗi JavaScript/tràn ngang. Fixture là dữ liệu tổng hợp, không phải bằng chứng hiệu quả giao dịch. Chưa deploy production.

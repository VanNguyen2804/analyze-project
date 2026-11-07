# Công cụ kỹ thuật và vùng ngoại suy trên chart Stock

Chart mặc định bật Fibonacci hình quạt; có thêm checkbox Fib ngang và kênh xu hướng. Tất cả vẽ trên Lightweight Charts bằng primitive, bám theo kéo/zoom/resize, không phát sinh request hoặc ghi dữ liệu. EMA20/50, Ichimoku và mốc vị thế giữ hoạt động cũ. Template/CSS chart được tách ra file riêng.

## Cách chọn dữ liệu và vẽ

`stock-chart-projections.ts` nhận các nến đã chuẩn hóa theo thời gian biểu đồ, chỉ dùng nến đã đóng. Có cửa sổ 30/60/120 nến, mặc định 60; tối thiểu 20 nến và ATR14 dương. Nhận diện pivot cao/thấp bằng hai nến trước và hai nến sau đã có trong snapshot. Chọn cặp trái loại gần nhất có khoảng cách ít nhất hai nến và biên độ ít nhất một ATR. Nếu không có pivot phù hợp, dùng hai cực trị trong cửa sổ và ghi rõ kiểu neo đó có thể đổi khi có nến mới. Khi không có nhịp đủ rõ, không tạo công cụ giả.

- Fibonacci quạt dùng ba tỷ lệ 38,2% / 50% / 61,8%. Với điểm A, B tại chỉ số a, b và giá Pa, Pb, mức hồi tại b là `Pb - (Pb-Pa)*r`; tia tại chỉ số i là `Pa + (Pb-Pa)*(1-r)*(i-a)/(b-a)`. Ba tia đều xuất phát A và đi qua đúng mức hồi trên đường dọc qua B. Áp dụng đối xứng cho nhịp tăng/giảm. Điểm A/B và ngày/giá hiển thị để kiểm tra lựa chọn tự động.
- Fib ngang kéo từ B qua các mức hồi dùng cùng A/B; bật riêng khi cần.
- Kênh xu hướng dùng hồi quy tuyến tính OLS trên giá đóng trong cửa sổ. Hai biên cách đường giữa hai lần RMS phần dư. Chỉ là hành lang ngoại suy mô tả; không phải khoảng tin cậy 95%, dự báo thống kê đã hiệu chuẩn hoặc xác suất thắng.

Giá vẫn dùng đơn vị của chart; không tự đổi thành VND nếu nguồn đang là THOUSAND_VND. Cách vẽ tham khảo định nghĩa [Fibonacci Fan của Fidelity](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/fibonacci-fan) và [MetaTrader](https://www.metatrader5.com/en/terminal/help/objects/fibo/fibo_fan). Đây là công cụ dựng hình, không phải chứng minh lợi thế giao dịch.

## Phần tương lai và hiệu năng

Chừa 20 khoảng nến bên phải khi có mô hình và công cụ đang bật. Dùng logical indices để vẽ; không tạo timestamp cho các phiên chưa xảy ra, không giả định nghỉ lễ, không thêm OHLCV hoặc nến dự báo. Vùng tô nền bắt đầu giữa nến cuối và khoảng kế tiếp, ghi nhãn KỊCH BẢN; đường đứt tiếp tục tới mép phải. Đối với intraday đây là khoảng nến, không khẳng định có đúng 20 phiên hay giờ giao dịch kế tiếp.

`StockProjectionLayer` dùng media coordinates và các view giữ reference ổn định. Lightweight Charts 5.2 chỉ chuyển được logical index nguyên: vị trí nửa nến được nội suy giữa hai tọa độ nguyên, tránh đánh dấu nhầm toàn bộ quá khứ thành tương lai. Primitive chỉ bổ sung khoảng giá quanh giá đóng ±3 ATR để không ép nến phẳng vì các tia dốc. Phần tia vượt khung giá bị clip. Công cụ có thể thay đổi khi cửa sổ/nến đóng thay đổi; không bảo đảm vùng giá sẽ được tôn trọng. Bật/tắt lớp chỉ yêu cầu vẽ lại, không huấn luyện hoặc tải thêm lịch sử. Khi hủy component, primitive được detach cùng chart.

## Kiểm chứng

Unit tests cho hai hướng quạt; tia qua A/mức hồi tại B; chỉ dùng nến đóng/cửa sổ đang chọn; pivot có hai nến xác nhận; thiếu lịch sử/flat ATR; hồi quy khớp giá đóng. Test dùng chart library thật kiểm tra số nến không tăng, vùng bên phải có pixel đường kỹ thuật và nhãn KỊCH BẢN nằm đúng sau nến cuối, xóa mô hình khi dữ liệu chuyển sang history thiếu. Browser fixture kiểm tra desktop/mobile, các checkbox, đổi cửa sổ, không có API mutation và lỗi JavaScript. Fixture không phải dữ liệu hay hiệu suất giao dịch thật.

K?t qu?: typecheck/formatter, 90 test frontend, production build v? Maven install (`-DskipTests` v? l??t n?y ch? ??i frontend) ??t. To?n b? 26 assets Angular kh?p JAR. Browser 1440/390 px kh?ng tr?n ngang, ??i c?a s?/b?t t?t l?p ??t, kh?ng l?i JavaScript ho?c API mutation. Ch?a deploy production.

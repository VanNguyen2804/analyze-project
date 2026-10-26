# Kế hoạch phát triển hướng tới mục tiêu dự đoán

## Điều chỉnh ưu tiên ngày 27/10/2026

Theo yêu cầu mới, tập trung vào thuật toán/học máy và phòng thử nghiệm `/ai-lab`. Tạm gác mở rộng kiểm thử PostgreSQL; thứ tự giai đoạn cũ bên dưới là tham khảo, không còn là thứ tự triển khai bắt buộc.

1. **Đã triển khai mã:** Logistic Regression và mô hình suy giảm thời gian; mỗi mô hình 5 vé/kỳ; ledger riêng, tự chốt trước kỳ và tự đối soát. Giữ nguyên bộ vé đầu tiên, đối chứng cùng số vé, theo dõi hiệu quả theo kỳ và phiên bản.
2. **Thu thập tương lai:** triển khai backend chạy ổn định, theo dõi dữ liệu đủ mới, tránh bỏ kỳ; tích lũy kết quả thực tế trước khi kết luận. Không tính dữ liệu mô phỏng hoặc test thành bằng chứng thực tế.
3. **Sàng lọc dài hạn:** quy tắc v1 là 60 kỳ liên tiếp, mỗi khối 30 kỳ gần nhất có ít nhất 50% kỳ đạt một vé ≥3, cận dưới chênh lệch ghép cặp xấp xỉ dương với cả đối chứng tần suất và ngẫu nhiên. Đây là tiêu chí kỹ thuật ban đầu, không phải kiểm định xác nhận; cần xử lý xem lặp lại, nhiều mô hình và phụ thuộc thời gian khi thiết kế giai đoạn xác nhận.
4. **Cải tiến có phiên bản:** nghiên cứu đặc trưng và mô hình mới bằng walk-forward trên dữ liệu phát triển; đóng băng ứng viên trước khi kiểm tra trên kỳ tương lai mới. Không sửa cấu hình trên cùng nhóm rồi gộp thành bằng chứng chung.
5. **Áp dụng:** chỉ cân nhắc đổi mô hình chính sau giai đoạn xác nhận tương lai có quy tắc và thời điểm đánh giá ghi trước. Nhãn ứng viên trên trang thử nghiệm không tự thay thuật toán chính.

Chi tiết API, tiêu chí và giới hạn: [PROJECT_ANALYSIS](../../PROJECT_ANALYSIS.md).

Ngày lập: **26/10/2026**. Phạm vi: Angular + Spring Boot hiện tại.

## Mục tiêu và tiêu chí quyết định

Đề xuất sáu số chính MEGA/POWER và đo xem phương pháp có cải thiện so với tần suất nền và ngẫu nhiên hay không. Hiện chưa chứng minh được lợi thế. Mục tiêu triển khai là tạo được bằng chứng có thể kiểm tra; không cam kết đạt số khớp hoặc tỷ lệ trúng cụ thể.

Chỉ số chính: chênh lệch số khớp trung bình trên mỗi vé so với tần suất nền, ghép cặp cùng kỳ. Chỉ số bổ sung: phân bố 0–6 số khớp, tỷ lệ từ ba số và chênh lệch so với đối chứng ngẫu nhiên. Tách MEGA/POWER, phiên bản và cấu hình. Không cộng nhiều thuật toán cùng kỳ thành các kỳ độc lập.

## Lộ trình theo điều kiện nghiệm thu

Thời lượng dưới đây là ước lượng công việc phát triển, bắt đầu khi triển khai từng giai đoạn; thời gian thu thập dự đoán tương lai tính riêng.

| Giai đoạn | Công việc | Nghiệm thu | Dự kiến |
| --- | --- | --- | --- |
| 1 — Kiểm chứng bản build | CI với lockfile; kiểm thử workspace/history và Java; đối chiếu từng asset Angular trong JAR | Workflow đạt trên PR; báo cáo test được lưu. Bổ sung kiểm thử AppComponent vào toàn bộ suite sau khi sửa fixture cũ | 1–2 ngày |
| 2 — Dữ liệu và vận hành | Báo cáo kỳ thiếu theo lịch, revision bị cách ly, độ mới từng loại; lịch sử sync bền vững; kiểm tra PostgreSQL tạm, migration và restart trong CI | Không dùng revision chưa hợp lệ; kiểm thử database thật xác nhận snapshot không trùng/ghi đè, settlement đúng và giữ qua restart; cảnh báo khi thiếu kỳ hoàn tất mới nhất | 3–5 ngày |
| 3 — Quy trình thực nghiệm | Công cụ backend chạy walk-forward; phân chia development/validation/test theo ngày; đóng băng cấu hình và ghi manifest trước khi mở test | Báo cáo có hash dữ liệu, khoảng ngày, cấu hình, phiên bản, seed, số kỳ bị loại; mọi dự đoán chỉ dùng dữ liệu trước kỳ đích | 4–6 ngày |
| 4 — Đo đóng góp thành phần | So tần suất với temporal; lần lượt bỏ từng thành phần; chỉ chọn tham số trên development/validation | Một cấu hình ứng viên được chọn theo quy tắc ghi trước; chạy test giữ kín một lần; báo cáo cả trường hợp giảm chất lượng | 3–5 ngày |
| 5 — Theo dõi tương lai | Chạy scheduler ổn định; chốt trước 18:00 giờ Việt Nam; đối soát sau 18:30; kiểm tra bỏ lỡ và độ trễ | Bản đầu tiên giữ nguyên; không sinh bù quá khứ; báo cáo theo phiên bản cố định, đối chứng cùng kỳ và khoảng bất định | Liên tục |

## Quy trình thực nghiệm cần triển khai ở giai đoạn 3

1. Kiểm kê lịch sử hợp lệ thực tế trong PostgreSQL; chốt snapshot dữ liệu và các ngày ranh giới trước khi chạy. Nếu dữ liệu quá ngắn để chia ba phần có ý nghĩa, ưu tiên bổ sung lịch sử và ghi rõ chưa đủ mẫu.
2. Dùng đoạn sớm để phát triển đặc trưng; đoạn kế tiếp để chọn tham số bằng walk-forward. Mỗi lượt chỉ học từ các kỳ trước kỳ đích. Cả ứng viên và đối chứng dùng cùng cửa sổ, cùng tập kỳ hợp lệ.
3. Ghi manifest bất biến: ngày ranh giới, cấu hình ứng viên, metric chính, danh sách phép so sánh, seed, hash dữ liệu và phiên bản mã. Không chọn seed bằng kết quả kiểm tra.
4. Chạy đoạn test cuối sau khi đóng băng. Báo cáo chênh lệch ghép cặp với cả hai đối chứng, độ bất định và số phép thử; nêu rõ dữ liệu lịch sử từng được xem trong phát triển không phải tập giữ kín mới.
5. Với thay đổi tiếp theo, dùng dữ liệu tương lai mới để xác nhận. Không tối ưu trên sổ dự đoán độc lập rồi tiếp tục gọi cùng sổ đó là kiểm tra độc lập.

Không coi mốc 30 kỳ là bằng chứng hiệu quả. Chọn số kỳ và thời điểm đánh giá trước khi đo dựa trên mức cải thiện cần phát hiện và độ biến thiên; tránh dừng ngay khi có kết quả thuận lợi. Nếu không có lợi thế ổn định, giữ phương pháp nền và ghi nhận kết quả thực nghiệm.

## Phần đã thực hiện trong đợt này

- Thêm `.github/workflows/verify.yml`: cài từ lockfile, kiểm thử hai feature Angular, build production, kiểm thử/build Java và đối chiếu toàn bộ asset trong JAR.
- Workflow chưa bao gồm PostgreSQL thật, Docker hoặc toàn bộ Angular suite. Đây là công việc tiếp theo, không được suy ra từ unit test có mock.
- Không thay đổi thuật toán hoặc nạp archive vào database. Việc vận hành production và tích lũy dự đoán tương lai cần môi trường chạy thực tế.

Kiểm chứng local: Angular production build đạt; 8 kiểm thử ChromeHeadless của hai feature đạt; `mvn clean install` đạt với 34 kiểm thử Java. Sau khi Angular build xong, đóng gói lại JAR và đối chiếu 9 asset đều khớp từng byte. Còn cảnh báo template/deprecation Angular và unchecked Java có sẵn. Chưa chạy workflow trên GitHub hoặc kiểm thử PostgreSQL thật trong đợt này.

## Thứ tự thực hiện tiếp theo

Ưu tiên giai đoạn 2 rồi giai đoạn 3 trước khi tối ưu trọng số. Mỗi thay đổi mô hình phải có báo cáo tái lập và phiên bản riêng. Chỉ đề xuất thay mô hình mặc định khi kết quả ngoài tập chọn tham số đủ thuyết phục theo quy tắc đánh giá đã ghi trước.

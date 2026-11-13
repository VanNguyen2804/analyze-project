# Triển khai nghiên cứu ngày 13/11/2026

Đã triển khai một phần kế hoạch, chưa hoàn tất toàn bộ lộ trình:

- Benchmark `chronological-study-v2` mặc định năm vé; frontend gửi cùng ngân sách và hiển thị tương ứng.
- Thêm `logistic_shrunk`: chọn lambda từ 0/0,25/0,5/0,75/1 bằng Brier trên validation trước outer; chiếu xác suất về [0,1] với tổng sáu. Manifest ghi lambda và mọi trial. Không tạo ID prospective mới hoặc đổi mô hình chính.
- Thêm `logistic_triple_coverage` vào benchmark: đảo trọng số ưu tiên triples/quads, giữ candidate pool, anchor, rank/exposure và ngân sách hiện có. Đây là surrogate độ phủ, chưa phải tối ưu trực tiếp xác suất có vé ≥3.
- Lịch sử ưu tiên từng slot chỉ nhận đúng cohort hiện tại. Lịch sử tổng thuật toán vẫn giữ các nhóm phiên bản tham khảo. Popup giải thích phạm vi mới.

Kiểm tra: 13 test backend AiLabPredictionsTest/PredictionResearchTest đạt; hai test Angular research panel đạt; production build đạt trước chỉnh nội dung benchmark từ ba thành năm vé; Angular test đã biên dịch thay đổi cuối. Cảnh báo CSS AI Lab vượt warning budget vẫn có sẵn. `git diff --check` đạt.

Replay local trong [JSON](prediction-study-2026-11-13.json): fixture cũ, 15 kỳ outer mỗi loại, năm vé/kỳ. MEGA uniform 3/15 kỳ ≥3, Logistic selected/shrunk và hai coverage đều 2/15. POWER uniform và Logistic selected/shrunk 0/15; hai coverage 2/15. Validation chọn lambda=1 ở cả hai loại; phép chiếu tổng sáu vẫn có tác động nhỏ. Không có bằng chứng nâng cấp ổn định từ mẫu này. Seed protocol đổi theo version nên không so trực tiếp với báo cáo v1 để quy mọi chênh lệch cho thuật toán.

Chưa triển khai: xuất inventory live đầy đủ; tách fingerprint; chia riêng vùng calibration; runner offline quy mô archive; generator tối ưu sự kiện ≥3 bằng mô phỏng; lưu snapshot quyết định top-k và đồng hạng; đăng ký effect/sample size/review prospective; chốt các ứng viên mới song song. Không có migration hoặc deployment trong lượt này. Không coi kế hoạch đã hoàn tất hoặc đã tăng độ chính xác.

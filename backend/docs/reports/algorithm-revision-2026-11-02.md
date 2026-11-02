# Đối chiếu bốn sửa đổi thuật toán — 02/11/2026

Đã sửa hợp đồng tham số, chuyển tiếp qua khoảng thiếu kỳ, expert số phụ không áp dụng cho MEGA và graph degree đếm lặp tần suất. Các sửa đổi đạt kiểm thử hành vi, nhưng phát lại phát triển không cho thấy cải thiện đồng đều: Bayes giảm trên cả hai loại; temporal MEGA giảm một lượt khớp; deep stacking POWER tăng hai lượt khớp. Không chỉnh lại công thức, support, trọng số hoặc seed theo kết quả này.

## Dữ liệu và phương pháp

- Baseline là commit `7de6ed6` trước sửa thuật toán, chạy trong worktree tạm. Cùng file `AlgorithmRevisionReportTest` được dùng cho cả baseline và code mới.
- Đầu vào là `backend/src/main/resources/data/lottery_numbers.json` hiện có trong repository, không đọc hoặc thay dữ liệu production. 79 bản ghi, lọc qua `DrawQuality` còn 39 kỳ MEGA và 39 kỳ POWER. Không dùng kỳ 01/11 để chọn tham số.
- Hai lượt dùng cùng byte đầu vào, SHA-256 `4f2c902351f46dda6eedd7be6b11a5d1c28f445fc3fc06937c322b11a802bfc5`. Khi tạo worktree trên Windows, copy nguyên byte resource trước `mvn clean test` để tránh khác hash do Git chuyển LF/CRLF.
- Mỗi loại lấy mười kỳ đầu khởi tạo, đánh giá 29 kỳ tiếp theo: MEGA 21/08–25/10/2026; POWER 20/08–24/10/2026. Mỗi dự đoán chỉ dùng các kỳ trước ngày đích. Dữ liệu đã được xem trước là dữ liệu phát triển, không phải test giữ kín hoặc dự đoán thực sự chốt trước kỳ.
- Dùng tham số mặc định của từng revision, không đọc cấu hình active từ database. Phiên bản mới là `temporal-links-v3`, `bayesian-pair-excess-v2`, `copula-scheduled-v2`; graph có prior 8, minimum support 3, weight 0.85. Xem [công thức và phạm vi](../ALGORITHM_SCORING.md).
- Một vé top sáu mỗi thuật toán/mỗi kỳ. Đối chứng top sáu tần suất và 100 vé ngẫu nhiên đều, mỗi vé có seed cố định theo protocol/category/ngày/replicate. Đây là so sánh ở ngân sách một vé; 100 lần ngẫu nhiên chỉ để ước lượng biến thiên của đối chứng, không được tính thành 100 kỳ độc lập và không đại diện ngân sách người dùng mua 100 vé.

## Kết quả

| Loại | Thuật toán | Số khớp TB/vé trước | Sau | Kỳ có vé ≥3 trước | Sau |
| --- | --- | ---: | ---: | ---: | ---: |
| MEGA | Temporal | 0.759 | 0.724 | 0/29 | 0/29 |
| MEGA | Bayes graph | 0.966 | 0.862 | 2/29 | 0/29 |
| MEGA | Deep stacking | 0.897 | 0.897 | 0/29 | 0/29 |
| POWER | Temporal | 0.897 | 0.897 | 1/29 | 1/29 |
| POWER | Bayes graph | 0.966 | 0.724 | 1/29 | 0/29 |
| POWER | Deep stacking | 0.897 | 0.966 | 1/29 | 2/29 |

Đối chứng không đổi giữa hai lượt. MEGA: tần suất 0.759 số/vé và 0/29 kỳ ≥3; ngẫu nhiên trung bình 0.795 số/vé, tỷ lệ ≥3 trung bình 2.07%. POWER: tần suất 0.862 số/vé và 1/29 kỳ ≥3; ngẫu nhiên trung bình 0.668 số/vé, tỷ lệ ≥3 trung bình 1.34%.

Thay graph giải quyết feature đếm lại tần suất, không bảo đảm feature mới hữu ích cho kỳ tiếp theo. Tăng hai lượt khớp ở deep stacking POWER cũng chưa chứng minh lợi thế. Không chọn lại tham số để làm đẹp cùng cửa sổ này; cần đánh giá trên kỳ tương lai mới trước khi khẳng định cải thiện. Các báo cáo cũ vẫn là snapshot theo code và dữ liệu cũ, không được viết lại thành kết quả revision mới.

Chi tiết số liệu và vé từng kỳ: [baseline](algorithm-revision-before-2026-11-02.json), [revision mới](algorithm-revision-after-2026-11-02.json). Hai JSON chứa cùng input hash, ngày, budget, seed protocol và các đối chứng để kiểm tra độc lập.

## Kiểm chứng kỹ thuật

Angular production build đạt với Node 24.15.0. Maven `clean install` với bật phát lại và PostgreSQL kiểm thử đạt: 102 kiểm thử được phát hiện, 100 chạy đạt, hai báo cáo phát triển cũ có điều kiện được bỏ qua. Cả bốn kiểm thử PostgreSQL chạy trên server tạm riêng localhost, schema ngẫu nhiên đã được dọn và server đã tắt. 23 assets Angular khớp SHA-256 trong JAR. JAR kiểm tra có SHA-256 `bb77ed0dd915a454f3a687abfa74f7c85edf47207b05007fbaf9d1262a4174a1`.

Không thay dependencies, migration, UI hoặc dữ liệu production. Chưa triển khai bản sửa lên production. Còn cảnh báo budget CSS AI Lab và unchecked Java đã có. Kiểm thử đạt xác nhận hành vi triển khai, không xác nhận lợi thế dự đoán.

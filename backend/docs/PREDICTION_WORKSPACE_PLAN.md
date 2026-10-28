# Kế hoạch trang dự đoán sau rà soát — 28/10/2026

Trạng thái: đã triển khai bản đầu trong workspace ngày 28/10/2026, chưa deploy. Yêu cầu đã thống nhất: người dùng điều khiển trang dự đoán; AI Lab tự chạy thử nghiệm, cung cấp thêm 3–5 dãy để lựa chọn; giữ bộ kết hợp. Các quy tắc vận hành không phải bằng chứng tăng hiệu quả.

API và cách sử dụng: [README](../../README.md). Luồng kết hợp được chỉnh trước khi chọn vé; đổi sang bộ gợi ý khác cần bỏ bản nháp rõ ràng để giữ nguồn bất biến. Lịch sử vé chốt hiển thị ngay trong trang dự đoán, tab lịch sử cũ phục vụ ledger tự động. Đã có báo cáo phát lại phát triển và kiểm thử PostgreSQL thật trên schema tạm riêng; chưa có bằng chứng tương lai để thay thuật toán mặc định.

## Mục tiêu và cách hiểu dữ liệu

Trang dự đoán giúp chọn thuật toán, điều chỉnh bộ kết hợp, chọn hoặc sửa vé từ nhiều nguồn rồi chốt trước kỳ. AI Lab tự huấn luyện/sinh vé theo cấu hình thử nghiệm, chốt và đối soát riêng. Hai luồng dùng chung kết quả hợp lệ và quy tắc lịch quay; các lựa chọn của người dùng không sửa mô hình hoặc bản đã chốt của AI Lab.

Kỳ MEGA 21/10: vé id 26 khớp 10,22; vé id 27 khớp 14,25. Độ phủ chung là bốn số khác nhau, mỗi vé khớp hai số. Người dùng xác nhận đây là hai thuật toán; database cũ không ghi nguồn thuật toán để xác minh độc lập. Đây chưa phải một vé sáu số khớp bốn số, cũng chưa phải bằng chứng bộ kết hợp đã giữ cả bốn số.

40 kỳ hợp lệ mỗi loại đủ cho thử nghiệm phát triển ban đầu; không phải tập kiểm tra giữ kín vì đã được xem. 30 vé hợp lệ đã dò thiếu thời điểm chốt và nguồn thuật toán; không dùng chúng làm bằng chứng can thiệp trước kỳ. Báo cáo dữ liệu: [audit](reports/prediction-data-audit-2026-10-28.md).

## Lỗ hổng và quyết định đề xuất

### 1. Gợi ý tự động và vé người dùng chưa có vòng đời riêng

`ForecastService.predict` hiện tự lưu vé chính; scheduler cũng lưu theo thuật toán/kỳ. `ForecastStore` giữ bản đầu tiên theo khóa loại/ngày/thuật toán. Khóa này không biểu diễn nhiều cấu hình, cặp thuật toán, bộ kết hợp hoặc thao tác người dùng. Không đổi ý nghĩa ledger cũ thành vé người dùng đã chốt.

Tạo bản gợi ý bất biến gồm hai vé thuật toán, vé kết hợp, hai ranking đầy đủ, tham số và snapshot lịch sử. Bản gợi ý có id riêng; cùng một bản phải dùng cùng category, targetDate và history hash. Cho phép người dùng tạo bản nháp khác từ cấu hình khác, liên kết về đúng bản gợi ý họ đã xem.

Vé người dùng có vòng đời nháp → chốt trước kỳ → chờ kết quả → đã đối soát/kết quả cần xác minh. Chốt là lưu toàn bộ danh sách người dùng chọn, gợi ý gốc và nguồn; không tính lại số ở thời điểm chốt. Bản chốt bất biến. Nếu chỉnh trước cutoff, tạo revision mới với liên kết bản trước; thống kê dùng revision có hiệu lực cuối cùng trước cutoff, giữ audit của mọi revision. Vé nhập sau cutoff phục vụ dò vé lịch sử, không đưa vào đánh giá dự đoán trước kỳ. Việc chốt trong ứng dụng không chứng minh vé đã được mua thực tế.

### 2. AI Lab cần kênh đọc đúng kỳ và có truy vết lựa chọn

API overview hiện phân trang lịch sử, chưa có giao kèo cung cấp đúng nhóm gợi ý cho kỳ đang chọn. Bổ sung API chỉ đọc theo category + targetDate; trả id snapshot, model/version, thời điểm chốt, trainingThrough, history hash và sáu số. Không lấy vài dòng mới nhất của trang lịch sử rồi coi là kỳ hiện tại.

Quy tắc v1 đề xuất: mặc định năm dãy tổng cộng, có thể xem ba/bốn/năm từ cùng một thứ tự cố định. Lấy luân phiên theo mô hình và slot đã lưu, thứ tự mô hình cố định có phiên bản; loại vé trùng số, giữ tất cả nguồn khi một vé thuộc nhiều mô hình. Đây là quy tắc phân bổ lựa chọn, không phải xếp hạng xác suất trúng. Slot đầu của từng mô hình là top sáu theo điểm; các slot tiếp theo là lấy mẫu có trọng số, không phải thứ hạng chất lượng giảm dần.

Chỉ dùng batch đầy đủ và hợp lệ của từng mô hình; khi chỉ có một mô hình sẵn sàng, hiển thị rõ nguồn thực có. Thiếu ba vé hợp lệ thì hiển thị số thực có hoặc trạng thái chưa sẵn sàng, không sinh bù hoặc lấy kỳ khác. AI Lab lỗi/đang tắt không chặn thao tác dự đoán thủ công. Nếu thay chính sách chọn, ghi phiên bản mới và đánh giá riêng nhóm được giới thiệu; không dùng thành tích của toàn bộ mười vé để quảng bá cho nhóm năm vé.

Lưu danh sách id AI Lab đã giới thiệu cùng phiên bản chính sách vào bản gợi ý; vé người dùng chọn/sửa giữ liên kết nguồn. AI Lab gốc được tính đúng một lần trong ledger của nó, việc đưa sang trang dự đoán không nhân đôi thành tích mô hình.

### 3. Ranh giới 18:00/19:00 và độ mới chưa đủ chặt

`ForecastProtocol.target` chuyển kỳ từ 18:00, còn `expected` chỉ tính kỳ hôm nay từ 19:00. Vì vậy code có đường đi chốt cho kỳ sau trong 18:00–19:00 bằng dữ liệu thiếu kỳ vừa quay, rồi cơ chế giữ bản đầu tiên ngăn cập nhật. Đây là khả năng suy ra từ code, chưa xác nhận từng xảy ra trên production.

Đề xuất: gợi ý đủ điều kiện chốt cho kỳ D phải có kỳ liền trước D theo lịch và revision đủ chất lượng. Trong khoảng chờ công bố/đồng bộ, trạng thái là chờ kết quả mới nhất; không tự đóng băng bản gợi ý thiếu dữ liệu. Giữ cutoff chốt 18:00 và tiếp nhận kết quả 19:00 giờ Việt Nam. Vé tự nhập vẫn có thể chốt cho kỳ tương lai trước cutoff, nhưng không được gán nguồn mô hình chưa sẵn sàng.

Máy chủ kiểm tra lại loại/ngày/cutoff, tình trạng nguồn và số hợp lệ khi lưu. Nếu dữ liệu nguồn thay đổi so với bản người dùng đang xem, báo cần tải bản mới; không âm thầm thay vé hoặc đổi sang kỳ tiếp theo. Bản AI Lab đã chốt không bị ghi đè; nếu không đủ độ mới thì không dùng làm gợi ý hiện hành. Mọi đối soát dùng kết quả đủ điều kiện và lưu sự kiện sửa/vô hiệu hóa khi revision kết quả thay đổi.

### 4. Can thiệp của người dùng phải có phạm vi và dữ liệu tái lập

V1 gồm chọn hai thuật toán, trọng số kết hợp có giới hạn, giữ/bỏ số, sửa trực tiếp và tự nhập vé. Cấu hình là riêng cho bản nháp; không gọi API kích hoạt hyperparameter toàn cục. Cùng history/ranking, tham số và quy tắc phải sinh cùng kết quả.

Giữ không quá sáu số, không vừa giữ vừa loại một số, miền số đúng loại và đủ sáu số khác nhau. Với POWER, thống kê đang xét sáu số chính; thông tin số phụ nếu còn hiển thị phải giữ tách riêng, không nhập thành số thứ bảy của vé. Cặp cùng một thuật toán được coi là một nguồn lặp, không phải hai ý kiến độc lập.

Lưu ranking đầy đủ và tham số hợp nhất vì chỉ hai vé top sáu không đủ tái tạo `PredictionSupport.fuse` vốn cộng nghịch đảo thứ hạng trên toàn miền số. Lưu cả sửa đổi thủ công và vé cuối; chốt đúng dữ liệu đã hiển thị.

### 5. So sánh hiệu quả phải cùng số vé và đúng mẫu số

Ba gợi ý thuật toán cộng năm gợi ý AI Lab là tối đa tám lựa chọn, không mặc nhiên là tám vé người dùng chơi. Tách danh sách được gợi ý, danh sách đã chốt và ghi nhận đã mua nếu có. Không so độ phủ của tám vé với ba vé rồi kết luận mô hình tốt hơn.

Đánh giá từng vé bằng số khớp 0–6 và tỷ lệ từ ba/bốn số; đánh giá nhóm bằng vé tốt nhất, tỷ lệ kỳ có vé đạt ngưỡng và độ phủ số khác nhau. Độ phủ là giao của hợp các vé với kết quả, không cộng số khớp có thể bị lặp. Luôn kèm số vé và số kỳ. Phân tích nhóm dùng vé khác nhau; nếu sau này có số lượng mua lặp, số lượng đó không làm tăng độ phủ.

Đối chứng có cùng category, kỳ và số vé; seed cố định trước khi thấy kết quả, không chọn seed thắng. So sánh can thiệp bằng cặp vé gốc–vé sửa của cùng bản chốt. Vé hoàn toàn tự nhập không có cặp gốc; báo riêng. Sự khác biệt quan sát được chưa chứng minh tác động nhân quả, nhất là khi người dùng chọn nguồn sau khi xem nhiều gợi ý. Không backtest được quyết định thủ công quá khứ mà không có snapshot; chỉ đánh giá phần này trên dữ liệu tương lai đã lưu.

### 6. Tự học và độc lập phiên bản chưa được định nghĩa đúng

AI Lab hiện fit Logistic từ lịch sử bằng bộ đặc trưng và quy trình cố định; mô hình recency là thống kê. Nó không tự phát minh thuật toán hoặc tự điều chỉnh toàn bộ pipeline theo thắng/thua. Việc chọn vé của người dùng không tự trở thành nhãn đúng cho mô hình.

`AiLabService.codeVersion` hiện phụ thuộc `ForecastProtocol.codeVersion`, trong đó có class `AnalyzeService`, `TemporalRelationshipModel`, `PredictionSupport`. Sửa scoring trang dự đoán có thể làm đổi nhóm phiên bản AI Lab dù mô hình Lab không đổi. Đề xuất tách fingerprint theo phụ thuộc thực tế: mô hình, đặc trưng, tạo vé, cấu hình và chính sách dữ liệu/đánh giá có tác động. Thay đổi ngữ nghĩa dùng chung vẫn cần phiên bản mới. Chỉ áp dụng cho snapshot mới, không viết lại cohort lịch sử.

### 7. Chốt phải chịu được retry, đồng thời và lỗi từng phần

Giao dịch lưu bản gợi ý/nhóm vé chốt phải nguyên khối; khóa chống request lặp và revision kỳ vọng ngăn double-click, retry mạng hoặc hai tab chốt chồng nhau. Kiểm tra thời gian máy chủ tại thao tác lưu, không tin giờ trình duyệt. Request cũ sau khi đổi MEGA/POWER/kỳ không được cập nhật state mới; AI Lab lỗi không xóa nháp người dùng.

Migration bổ sung ledger mới, giữ ledger tự động, AI Lab và lịch sử dò vé cũ. Không biến checkedAt thành thời điểm mua/chốt, không tự sửa vé cũ 113 hoặc toàn 0. Dữ liệu sai giữ để audit, loại khỏi đánh giá.

## Thứ tự triển khai đã điều chỉnh

1. Validation và hợp đồng dữ liệu: category/kỳ, snapshot, nguồn, revision, cutoff và độ mới. Nghiệm thu input sai bị chặn ở backend và frontend, dữ liệu cũ không mất.
2. Ledger gợi ý và vé người dùng, chốt nguyên khối/chống lặp. Nghiệm thu truy lại đúng vé đã nhìn thấy, chỉnh trước cutoff có lịch sử và không đếm hai revision thành hai bộ chơi.
3. API gợi ý AI Lab và quy tắc chọn 3–5 vé theo đúng kỳ. Nghiệm thu không ghi ngược Lab, không lấy vé kỳ cũ, thiếu dữ liệu có trạng thái rõ.
4. Giao diện ba phần: gợi ý thuật toán; 3–5 gợi ý Lab; vé người dùng. Hỗ trợ chỉnh/chốt cùng cảnh báo nguồn thay đổi, trạng thái chờ và đối soát riêng.
5. Báo cáo cùng ngân sách vé và thử nghiệm cách kết hợp trên dữ liệu phát triển. Đóng băng ứng viên, metric và thời điểm đánh giá trước khi theo dõi kỳ tương lai mới. Không bắt việc ra mắt luồng chọn/chốt phải chờ chứng minh thuật toán mới tốt hơn.

## Kiểm chứng bắt buộc khi triển khai

- Kiểm tra sáu số hợp lệ, số trùng, giữ/bỏ mâu thuẫn, trọng số không hữu hạn và fallback thiếu lịch sử.
- Kiểm tra kỳ MEGA 21/10 cho 2 + 2 không trùng = phủ 4; trường hợp cùng số trúng ở hai vé chỉ đếm một lần trong độ phủ.
- Kiểm tra 17:59:59, 18:00, 18:59:59, 19:00 theo giờ Việt Nam; chờ feed, dữ liệu bị cách ly, chuyển tháng/năm; không chốt sai kỳ từ màn hình cũ.
- Kiểm tra chọn AI Lab đúng loại/kỳ, thứ tự tái lập, loại trùng và giữ nguồn; lỗi/thiếu batch không sinh vé giả hoặc chặn bản nháp.
- Kiểm tra cạnh tranh/retry/rollback với các transaction mới; unit test có mock không thay được kiểm tra hành vi lưu nguyên khối và ràng buộc database thực tế.
- Kiểm tra gợi ý gốc, chỉnh thủ công và Lab gốc đối soát độc lập, revision kết quả thay đổi được phản ánh, không đếm trùng mẫu.
- Chạy kiểm thử hành vi phù hợp, build Angular và Maven, đối chiếu assets trong JAR theo quy ước repo. Trạng thái kiểm chứng bản triển khai ghi tại `PROJECT_ANALYSIS.md`.

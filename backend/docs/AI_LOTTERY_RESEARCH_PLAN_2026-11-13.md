# Kế hoạch cải thiện thuật toán sau rà soát tin AI trúng xổ số

Ngày: 13/11/2026, giờ Việt Nam. Trạng thái: **đề xuất triển khai, chưa thay mô hình hoặc chạy thử nghiệm mới**. Kế hoạch nối tiếp các phần đã triển khai trong [báo cáo nghiên cứu 07/11](VIETLOTT_RESEARCH_IMPLEMENTATION.md) và [feedback + coverage 10/11](AI_LAB_FEEDBACK_COVERAGE.md).

## 1. Kiểm chứng câu chuyện người dùng gửi

Link người dùng: [video CafeF trên TikTok](https://www.tiktok.com/@cafef_official/video/7707577378620345621). Công cụ không truy cập được video; đã tìm và đọc [bài CafeF ngày 11/11/2026](https://cafef.vn/nhan-vien-van-phong-trung-so-195-ty-dong-nho-phan-mem-tu-tao-bang-ai-188261111123531292.chn) cùng [bài tiếng Trung ngày 30/10](https://www.thepaper.cn/newsDetail_forward_34369003) mà nội dung dẫn về.

Theo các bài này, ông Liu ở Tây An dùng AI viết chương trình chọn 20 số từ 80, tự sửa rồi ghép sáu bộ 11 số chọn 10; một bộ mang về tổng 5,08 triệu NDT. Lần mua được kể diễn ra ngày 28/8, kỳ 2026230; ngày đăng bài không phải ngày quay. Bài tiếng Trung dẫn nguồn Thiểm Tây福彩/Anhui Traffic Radio và nêu rõ lần trúng không chứng minh AI tăng xác suất.

Trong các nguồn đã đọc không có mã nguồn, tên mô hình, tập huấn luyện, toàn bộ lần thua hoặc chuỗi dự đoán có dấu thời gian trước kỳ. Vì thế chưa thể tái tạo thuật toán hay xác nhận lợi thế. Không kết luận người đó không trúng; cũng không lấy một lần trúng làm bằng chứng cho chất lượng dự báo.

Happy 8 khác MEGA/POWER của dự án. Phần có thể thử nghiệm là quy trình chấm điểm → ghép danh mục vé → ghi lại can thiệp thủ công; không sao chép quy tắc 20/80, số liên tiếp hoặc số lặp thành điều kiện bắt buộc.

## 2. Hiện trạng xác minh trong lần rà soát này

### Dữ liệu đang chạy và dữ liệu phát triển

GET chỉ đọc tới `/api/prediction-research/inventory` trên `https://analyze-project.onrender.com` đã trả HTTP 200 cho cả hai loại, khoảng 22:37 ngày 13/11:

| Chỉ số quan sát | MEGA | POWER |
| --- | --- | --- |
| Kỳ hợp lệ | 47 | 47 |
| Lịch sử đến | 13/11/2026 | 12/11/2026 |
| Đủ mới so với lịch ứng dụng | Có | Có |
| Kỳ thiếu trong khoảng dữ liệu | 0 | 0 |
| Kỳ ledger thuật toán chính đã đối soát | 8 | 8 |

Đây là lịch sử kết quả, không phải 47 kỳ kiểm chứng dự đoán tương lai. `SOURCE_MATCHED` là khớp nguồn cấu hình, chưa phải đối chiếu độc lập Vietlott. Các lần đọc tiếp để lưu toàn bộ phản hồi gặp 502/503. [Nhật ký kiểm kê](reports/prediction-research-inventory-2026-11-13.json) phân biệt quan sát thành công ban đầu với lỗi ở lần lưu sau; không có bản raw đầy đủ của lần thành công. Chưa xác nhận được số kỳ Lab theo từng cohort hoặc phiên bản binary production có trùng workspace hiện tại.

Báo cáo fixture ngày 07/11 có 39 kỳ hợp lệ mỗi loại, chỉ 15 kỳ outer, ba vé/kỳ. Logistic, feedback mô phỏng và coverage chưa cải thiện ổn định; không có vé ≥4 trong báo cáo đó. Không gọi kết quả fixture này là hiệu quả production hiện tại.

### Các thành phần đã có và khoảng trống

| Thành phần | Đã có | Việc cần làm tiếp |
| --- | --- | --- |
| `AiLabModel` | Logistic L2 với frequency/recent10/decay10/gap30; recency; tần suất làm trơn; 5 vé/mô hình | Thử làm trơn xác suất Logistic, ablation và lịch học có kiểm soát; không tăng độ phức tạp khi chưa đủ dữ liệu |
| `AiLabFeedback` | Feature đóng băng, nhãn đúng/sai toàn miền số, đối chứng không feedback | Đo lợi ích riêng của replay và trọng số cộng thêm cho số đã chọn; kết quả cũ đã được base fit học, replay làm thay trọng số dữ liệu chứ không tạo mẫu mới |
| `CoveragePortfolio` | Tối ưu phủ bộ bốn, bộ ba, rank và exposure | Mục tiêu đang thiên về bộ bốn, còn nhu cầu hiện tại là nhiều kỳ có vé ≥3; thử mục tiêu phù hợp, đo trên cùng ngân sách |
| `PredictionBenchmark` | Prefix theo thời gian, tuning, calibration, baseline, manifest | Mặc định 30 targets/3 vé; tối đa 6 kỳ validation và 60 targets; không đủ để xác nhận lợi thế nhỏ. Nâng runner và so với 5 vé production |
| `AiLabPredictions` | Ưu tiên khớp lặp lại, điểm Wilson, thống kê từng slot và popup lịch sử | Lịch sử đang gộp nhiều cohort theo model, rồi ghép theo slot; vị trí lấy mẫu không bảo đảm cùng ý nghĩa sau thay generator. Tách bằng chứng chính sách hiện tại và lịch sử tham khảo |
| Version và snapshot | Hash code/config/history, chốt trước kỳ, đối soát | `AiLabService` nằm trong fingerprint: sửa class rộng có thể tách cohort dù scoring không đổi. Tách identity theo phụ thuộc thực sự, giữ audit và snapshot cũ |

Code tham chiếu: [mô hình](../src/main/java/com/example/analyzeproject/domain/service/AiLabModel.java), [ghép vé](../src/main/java/com/example/analyzeproject/domain/service/CoveragePortfolio.java), [runner](../src/main/java/com/example/analyzeproject/application/service/PredictionBenchmark.java), [xếp hạng](../src/main/java/com/example/analyzeproject/application/service/AiLabPredictions.java), [dịch vụ Lab](../src/main/java/com/example/analyzeproject/application/service/AiLabService.java).

## 3. Mục tiêu và nguyên tắc đo

Mục tiêu sản phẩm là chọn được danh mục **5 vé khác nhau mỗi loại/kỳ**, có thứ tự tham khảo, lý do và bằng chứng truy lại được. Mục tiêu thử nghiệm chính là tỷ lệ kỳ có ít nhất một vé khớp ≥3 số chính. Theo dõi thêm số vé ≥3, vé ≥4/≥5/≥6, khớp trung bình, độ phủ và chi phí tương ứng; không tối ưu đồng thời mọi chỉ số rồi chọn chỉ số đẹp nhất.

Tách hai câu hỏi: mô hình có dự báo từng số tốt hơn đồng đều không; cách ghép 5 vé có cải thiện phân bố kết quả của danh mục không. Cải thiện độ phủ không tự chứng minh mô hình biết số nào sẽ ra. Với quay đều, mỗi bộ sáu số hợp lệ có cơ hội như nhau; đổi overlap có thể đổi xác suất đạt giải nhỏ của cả nhóm, còn cơ hội khớp sáu số của B bộ khác nhau vẫn là B/C(N,6).

Ngân sách chuẩn 5 vé áp dụng cho ứng viên và tất cả đối chứng. Báo cáo 1 vé dành riêng cho người chỉ chọn ưu tiên đầu; báo cáo 3 vé có thể giữ cho khả năng so sánh cũ. Không lấy kết quả tốt nhất của sáu mô hình, tức 30 vé, so với một mô hình 5 vé.

Đơn vị thống kê là kỳ quay; 45/55 nhãn số, 5 vé hoặc hàng nghìn mô phỏng cùng kỳ không thành hàng nghìn kỳ độc lập. Chỉ dùng thời điểm, feature và nhãn có thể biết trước kỳ đích.

## 4. Lộ trình triển khai

### P0 — Kiểm kê và cố định hợp đồng thực nghiệm

1. Hoàn thành xuất inventory đầy đủ khi máy chủ sẵn sàng: các kỳ hợp lệ, provenance, revision, thời gian và số kỳ prospective từng cohort. Đối chiếu version triển khai với repo.
2. Lập báo cáo chất lượng nguồn; nếu bổ sung archive kết quả, nhập qua cơ chế provenance/quarantine hiện có sau đối chiếu nguồn. Giữ MEGA/POWER riêng; không nhập kết quả Happy 8 làm nhãn học.
3. Tạo `ExperimentManifest`: ID, loại, hash dữ liệu/code, feature/model/generator/ranking policy, budget, seed, danh sách phép thử, metric và thời điểm review. Ghi nhận mọi phép thử thất bại.
4. Tách fingerprint scorer, generator, feedback và policy chọn vé; thay đổi ngữ nghĩa tạo version mới. Không sửa cohort/snapshot cũ hoặc gộp version chỉ để tăng mẫu.

Nghiệm thu: một bản xuất tái lập được, phân biệt rõ lịch sử quay và dự đoán chốt trước kỳ; mọi con số đều có nguồn và khoảng ngày.

### P1 — Nâng benchmark và kiểm tra mô hình hiện tại

1. Nâng runner thành tác vụ offline có giới hạn tài nguyên, đủ replay toàn bộ archive đã xác minh. Production vẫn có timeout/giới hạn; không tăng tải GET phục vụ UI.
2. Chia theo kỳ: train trước validation, calibration sau vùng tuning, rồi outer test. Lặp các cửa sổ thời gian nếu đủ dữ liệu. Với chỉ 47 kỳ, ghi rõ thiếu dữ liệu; không chia quá nhiều vùng nhỏ rồi coi là đáng tin cậy. Dữ liệu đã xem vẫn là development. Cách giữ thứ tự thời gian dựa trên [TimeSeriesSplit](https://scikit-learn.org/stable/modules/generated/sklearn.model_selection.TimeSeriesSplit.html).
3. Giữ bộ đối chứng đồng đều và tần suất; scorer và generator đổi riêng từng lần. Khóa seed trước kết quả, dùng cùng bộ kịch bản ngẫu nhiên để giảm nhiễu so sánh.
4. Xuất chênh lệch ghép cặp theo kỳ, khoảng bất định theo kỳ, histogram và metric primary. Kiểm định nhiều ứng viên cần điều chỉnh nhiều phép so sánh. Không chọn cấu hình và báo hiệu quả trên cùng đoạn; đây là vấn đề selection bias được phân tích trong [Cawley & Talbot, JMLR 2010](https://www.jmlr.org/beta/papers/v11/cawley10a.html).
5. Với xác suất từng số, giữ Brier/log loss so với 6/N. Calibration chỉ fit trên dự đoán ngoài mẫu thời gian, tách vùng chọn model và vùng fit calibrator. Không xem số lượng nhãn số trong vài kỳ là cỡ mẫu độc lập lớn. Tham khảo [tài liệu calibration](https://scikit-learn.org/stable/modules/calibration.html) và [Gneiting & Raftery 2007](https://doi.org/10.1198/016214506000001437).

Nghiệm thu: cùng manifest sinh cùng kết quả; khi thêm/sửa một kết quả tương lai, dự đoán của các target trước đó giữ nguyên; đổi số vé không âm thầm đổi mẫu số.

### P2 — Hai ứng viên nâng cấp có phạm vi nhỏ

**Ứng viên A: Logistic làm trơn về nền đồng đều.** Dùng `q_i = (1 − λ) × 6/N + λ × p_i`, với λ khóa từ một tập nhỏ gồm cả 0 và 1 trên vùng validation. Nếu đầu ra được gọi là marginal probability, phải bảo đảm 0≤q≤1 và tổng bằng 6 bằng phép chiếu có định nghĩa rõ. Mọi biến đổi nằm trong pipeline validation/test. So với Logistic không feedback và uniform; không mặc định đặc trưng gap hoặc số nóng/lạnh có ích. Chưa thêm XGBoost/LSTM chỉ vì tên mô hình mạnh hơn.

**Ứng viên B: generator ưu tiên sự kiện ≥3.** Tận dụng candidate pool và local search hiện có, thử trọng số phủ bộ ba hoặc mục tiêu trực tiếp `P(max matches ≥3)` của 5 vé trên các kịch bản sáu số. Chạy hai nhánh để phân biệt tác dụng: kịch bản đồng đều đo hình học danh mục; kịch bản theo model là giả định thử nghiệm, phải mô tả cơ chế lấy mẫu sáu số và không diễn giải thành xác suất thật.

Kịch bản dùng để tối ưu phải tách seed với kịch bản đánh giá; lịch sử tương lai không tham gia tối ưu. Có kiểm tra Monte Carlo error, overlap, exposure và số vé khác nhau. Không chặn cứng chỉ được dùng top 20 số, không ép chẵn/lẻ hay số liên tiếp nếu chưa có ablation ngoài mẫu. Lợi ích cuối cùng vẫn đo trên kết quả quay chưa dùng để chọn cấu hình.

**Feedback:** giữ bản hiện tại để so cặp, thử bỏ selected-extra-weight trước. Tách hiệu ứng “ưu tiên dữ liệu gần” và “ưu tiên số đã chọn”. Không chỉ học từ vé thắng. Nếu không cải thiện ngoài mẫu, giữ nhánh không feedback làm lựa chọn đơn giản hơn.

Nghiệm thu: mỗi ứng viên trả 5 vé hợp lệ/tái lập, có cấu hình và manifest riêng; báo cả kết quả không cải thiện. Không triển khai ứng viên thua baseline để gọi là nâng cấp độ chính xác.

### P3 — Ưu tiên dãy và UI gắn với đúng bằng chứng

1. Giữ popup vừa làm, bổ sung phạm vi **chính sách hiện tại** và **lịch sử mọi phiên bản**. Mặc định dùng bằng chứng tương thích với generator/slot policy hiện tại; dữ liệu ít thì ghi “chưa đủ bằng chứng”. Không lấy thành tích slot lấy mẫu cũ để gán xác suất cho một dãy số mới.
2. Ngưỡng khớp lặp lại ≥2 và Wilson hiện tại là quy tắc hiển thị, chưa phải mô hình dự báo hoặc bằng chứng vượt ngẫu nhiên. Khi thành tích bằng nhau, hiển thị đồng hạng hoặc ghi rõ thứ tự kỹ thuật.
3. Chốt trước cutoff bản chọn ưu tiên gồm source ticket IDs, thứ tự, `rankingPolicyVersion`, `evidenceThrough`, phạm vi cohort và lý do. Đo riêng chính sách chọn top 1/top 3/top 5 qua kỳ tương lai; không lấy dãy tốt nhất sau đối soát làm “dãy đã ưu tiên”.
4. Giải thích bằng dữ kiện: “3/24 kỳ cùng chính sách có vé ≥3; mẫu còn nhỏ”, thay cho “AI tự tin”. Popup có kết quả từng kỳ, phiên bản, bộ số và số khớp; trường hợp đổi generator cần cảnh báo phạm vi dữ liệu.

Nghiệm thu: UI truy lại đúng snapshot đã hiển thị, cùng bằng chứng sinh cùng thứ tự; kết quả cập nhật sau kỳ không viết lại quyết định ưu tiên trước kỳ.

### P4 — Xác nhận trên các kỳ tương lai

Sau development, chỉ chọn tối đa một ứng viên scorer và một generator đáng thử; giữ baseline và incumbent cố định để so cặp. Đăng ký trước mức cải thiện có ý nghĩa, cách kiểm định, alpha/điều chỉnh phép thử, cỡ mẫu và thời điểm review; cỡ mẫu phải tính từ baseline và effect mong muốn, không mặc định 30/60 kỳ là đủ.

Chạy song song bằng dự đoán giấy, không cần mua vé. Scheduler chốt trước cutoff, đối soát đúng revision; bỏ kỳ nào phải có lý do. Không dừng ngay khi vừa có chuỗi thắng. Nếu muốn xem quyết định liên tục, cần thiết kế kiểm định tuần tự trước khi bắt đầu.

Chỉ đề nghị thay mặc định khi đạt tiêu chí primary đã khóa, chênh lệch so với incumbent và uniform theo protocol đủ rõ, các chỉ số phụ không suy giảm quá giới hạn đã đăng ký, và không có lỗi dữ liệu/rò rỉ. Không đạt thì báo chưa chứng minh cải thiện. Brier tốt hơn không tự suy ra tỷ lệ vé ≥3 tốt hơn.

## 5. Chia gói công việc và kiểm thử

| Thứ tự | Gói thay đổi | Đầu ra review được |
| --- | --- | --- |
| 1 | Inventory + manifest + identity | Báo cáo live đầy đủ; schema experiment/policy; kiểm tra version compatibility |
| 2 | Runner cùng 5 vé + ablation | JSON theo từng kỳ; baseline/uncertainty; test rò rỉ tương lai, replay xác định và thay revision |
| 3 | Logistic làm trơn + generator ≥3 | Hai ứng viên tách biệt, kết quả development và chi phí tính toán; test miền số, tổng marginal và độ phủ |
| 4 | Priority snapshot + UI | Lịch sử quyết định top k, đồng hạng, popup đúng cohort; test cutoff, đổi loại, thiếu dữ liệu, bàn phím và mobile |
| 5 | Đăng ký prospective + báo cáo | Manifest xác nhận, lịch thu thập/review và tiêu chí thay mặc định; không tự promote từ dashboard |

Ưu tiên thực hiện gói 1–2 trước. Chúng giải quyết trực tiếp hạn chế của dữ liệu hiện tại và tạo căn cứ chọn gói 3. Việc viết code có thể hoàn thành trước, nhưng thời gian tích lũy bằng chứng phụ thuộc số kỳ và mức cải thiện cần phát hiện; không hứa một tuần sẽ chứng minh dự báo tốt hơn.

Trong lượt này chỉ nghiên cứu, đọc API và viết kế hoạch/nhật ký. Không chạy benchmark nặng trên production, nhập archive, sửa database, đổi mô hình mặc định hoặc triển khai hosting.

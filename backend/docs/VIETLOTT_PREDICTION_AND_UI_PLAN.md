# Kế hoạch nâng cấp dự đoán Vietlott và giao diện

Ngày rà soát: **07/11/2026**, giờ Việt Nam. Trạng thái: **kế hoạch chi tiết, chưa triển khai**. Người dùng đã xác nhận chỉ lập kế hoạch ở lượt này; phạm vi dự kiến gồm cả thuật toán và UI.

## 1. Kết luận và mục tiêu

Ưu tiên tiếp theo là xác minh lượng dữ liệu đủ điều kiện, xây quy trình so sánh thống nhất, rồi thử cải tiến mô hình số và cách ghép vé như hai bài toán riêng. Giao diện được làm mới song song trên Angular hiện có, giữ cấu trúc Header – Left menu – Content.

Mục tiêu sản phẩm: người dùng biết đang chọn vé cho kỳ nào, nguồn gợi ý nào, đã chốt hay chưa và phương pháp đó đã đạt kết quả gì trên bao nhiêu kỳ. Mục tiêu nghiên cứu: kiểm tra khả năng cải thiện ngoài mẫu với cùng số vé, đặc biệt theo dõi vé khớp ≥4 số. Không đặt nghiệm thu thành cam kết trúng 4–6 số.

Các nguồn chuyên môn tham khảo hỗ trợ phương pháp huấn luyện/đánh giá và bố trí danh mục; chưa cung cấp bằng chứng một thuật toán dự đoán Vietlott ổn định vượt ngẫu nhiên. Khi các bộ sáu số có xác suất xuất hiện ngang nhau và các kỳ độc lập, lịch sử không tự tạo lợi thế cho bộ số kế tiếp. Cần đo thay vì suy ra hiệu quả từ tên mô hình hoặc lượng dữ liệu.

## 2. Hiện trạng đã xác minh

### Dữ liệu và giới hạn của lần rà soát

| Nguồn | Điều xác minh được | Cách dùng trong kế hoạch |
| --- | --- | --- |
| JSON trong repository | 79 bản ghi: 39 MEGA, 40 POWER; kiểm tra tương đương quy tắc `DrawQuality` cho 39 kỳ hợp lệ mỗi loại, một POWER cách ly | Fixture phát triển, không đại diện dung lượng production |
| Khoảng ngày fixture hợp lệ | MEGA 29/07–25/10/2026; POWER 28/07–24/10/2026 | Không gọi là dữ liệu mới nhất ngày 07/11 |
| Báo cáo sửa thuật toán 02/11 | Dùng 10 kỳ khởi tạo và 29 kỳ đánh giá mỗi loại; đã xem trong phát triển | Bằng chứng lịch sử, không phải test giữ kín hoặc dự đoán chốt trước kỳ |
| API production ngày rà soát | GET `/api/numbers`, `/api/lottery-sync/status`, `/api/ai-lab?category=MEGA` và POWER đều hết thời gian chờ 45 giây | Chưa xác minh được số kỳ hiện tại, phiên bản đang chạy hoặc thành tích mới nhất; không suy ra production thiếu dữ liệu hay ngừng hoạt động |

Fixture: [lottery_numbers.json](../src/main/resources/data/lottery_numbers.json), SHA-256 `4f2c902351f46dda6eedd7be6b11a5d1c28f445fc3fc06937c322b11a802bfc5`. Quy tắc đối chiếu: [DrawQuality.java](../src/main/java/com/example/analyzeproject/domain/service/DrawQuality.java). Không nhập archive, thay kết quả hoặc gọi API ghi trong lần lập kế hoạch này.

### Tận dụng phần đã có

| Thành phần | Hiện trạng | Khoảng trống cần xử lý |
| --- | --- | --- |
| AI Lab | Logistic thực sự học trọng số có L2; bốn feature lịch sử; có bản không feedback, recency và năm vé/mô hình/kỳ | Chưa có bằng chứng feedback cải thiện ngoài mẫu; cần ablation và chọn cấu hình theo thời gian |
| Đánh giá xác suất | Có Brier, log loss, bảng hiệu chuẩn và baseline `6/N` | Mở rộng báo cáo thống nhất; giữ tách xác suất từng số và xác suất một vé |
| Thuật toán chính | Temporal, Bayes/graph và heuristic kết hợp | `deep_stacking` có `sXGB = 0.55 × momentum + 0.45 × frequency`, không phải XGBoost đã train; mô tả API/UI chưa đồng nhất với catalog |
| Danh mục vé | `rank-portfolio-v1` giữ vé top sáu, giảm dùng lặp số, giới hạn overlap | Tối ưu rank/exposure, chưa trực tiếp tối ưu tỷ lệ vé ≥4; phủ nhiều số không bảo đảm gom số đúng vào một vé |
| Snapshot/đối soát | Có lịch sử đóng băng, hash, cutoff, phiên bản, đối chứng; so cặp Logistic với thuật toán chính theo vé slot 0 | Phải báo số kỳ ghép cặp của đúng phiên bản, không lấy tổng mọi vé làm mẫu độc lập |
| Workspace/UI | Có chọn/sửa/chốt vé, ba tab tạo/lịch sử/review, AI Lab và đối chứng | Thiết kế và thuật ngữ phân tán; nhiều nhóm nối dài; headline lịch sử chủ yếu ≥3 |

Điểm bắt đầu code: [AiLabModel](../src/main/java/com/example/analyzeproject/domain/service/AiLabModel.java), [AiLabProbability](../src/main/java/com/example/analyzeproject/application/service/AiLabProbability.java), [AnalyzeService](../src/main/java/com/example/analyzeproject/application/service/AnalyzeService.java), [TicketPortfolio](../src/main/java/com/example/analyzeproject/domain/service/TicketPortfolio.java), [ForecastProtocol](../src/main/java/com/example/analyzeproject/application/service/ForecastProtocol.java).

Kết quả phát triển cũ không cải thiện đồng đều sau sửa scoring. Trong thử nghiệm portfolio, tăng độ phủ số cũng có trường hợp làm giảm số kỳ có vé ≥3. Xem [báo cáo thuật toán 02/11](reports/algorithm-revision-2026-11-02.md), [portfolio 27/10](reports/portfolio-development-2026-10-27.md), [đối chứng feedback 05/11](reports/ai-lab-feedback-comparison-2026-11-05.md) và [so sánh snapshot 05/11](reports/snapshot-comparison-and-portfolio-2026-11-05.md). Không tối ưu lại vào các kỳ đã thất bại để gọi đó là cải thiện ngoài mẫu.

### Vì sao mới thấy vé khớp ba số chưa đủ kết luận

Theo luật sáu số của [MEGA](https://info.vietlott-sms.vn/game_mega.html) và [POWER](https://info.vietlott-sms.vn/game_power.html), với giả thiết quay đều, số chính khớp `K` của một vé có:

`P(K = k) = C(6, k) × C(N − 6, 6 − k) / C(N, 6)`.

| Mốc lý thuyết cho một vé | MEGA, N = 45 | POWER, N = 55 |
| --- | ---: | ---: |
| Số khớp kỳ vọng | 0,8000 | 0,6545 |
| Khớp đúng 3 số | 2,2441% | 1,2711% |
| Khớp ≥3 số chính | 2,3834% | 1,3329% |
| Khớp ≥4 số chính | 0,13935% | 0,06187% |

Đây là phép tính tổ hợp được tính lại trong lượt rà soát, không phải kết quả đo của ứng dụng. Trong 1.000 lượt vé ngẫu nhiên, số vé ≥4 kỳ vọng chỉ khoảng 1,39 MEGA hoặc 0,62 POWER. Nhiều vé cùng kỳ có kết quả phụ thuộc nhau; không dùng `1 − (1 − p)^B` để tự suy ra tỷ lệ của nhóm vé chồng số. Mọi metric phải kèm số kỳ, số vé và ngân sách.

## 3. Kế hoạch dữ liệu, thuật toán và thực nghiệm

### P0 — Kiểm kê và chốt protocol

1. Đọc lại API production khi truy cập được; thống kê riêng MEGA/POWER: kỳ hợp lệ, khoảng ngày, revision cách ly, kỳ thiếu theo lịch, độ mới, số kỳ có snapshot trước cutoff và số kỳ đã đối soát. Đối chiếu phiên bản triển khai với code trong repo.
2. Tách ba tập: kết quả quay để học; dự đoán đóng băng trước kỳ để đánh giá; vé người dùng dò sau kỳ để đối chiếu. Một kỳ có 100 vé vẫn chỉ có một kết quả quay. Feedback không tạo thêm kỳ độc lập.
3. Xuất manifest với hash dữ liệu, provenance, chất lượng, các kỳ bị loại và lý do; giữ ngày/giờ Việt Nam. Nếu cần bổ sung lịch sử, dùng đường nhập hiện có, audit xung đột và revision; feed cộng đồng `SOURCE_MATCHED` không được đổi thành “Vietlott xác minh chính thức”. Dữ liệu Mỹ chỉ là tham khảo phương pháp, không trộn vào nhãn Vietlott.
4. Chốt trước khi thử: mục tiêu, ngày chia dữ liệu, ứng viên, số cấu hình được thử, ngân sách vé, baseline, seed, metric và thời điểm xem kết quả xác nhận. Chọn ngày ranh giới sau kiểm kê; không đặt giả một số kỳ tối thiểu rồi gọi là đủ dữ liệu.

**Nghiệm thu:** báo cáo có thể tái lập, phân biệt dữ liệu local/live, số kỳ đủ điều kiện theo cohort và phần chưa kiểm chứng. Không dùng các thống kê cũ làm thành tích hiện tại.

### P1 — Benchmark dùng chung, tách chất lượng số và chất lượng ghép vé

Đưa runner vào application service gọi domain, dùng repository/store ports theo Onion. Tái sử dụng các protocol, scorer và test báo cáo hiện có; không chạy benchmark nặng trong GET phục vụ UI.

- **Trục mô hình số:** cùng lịch sử/cửa sổ, cùng generator vé; so uniform, tần suất, tần suất làm trơn, Logistic có/không feedback và các thuật toán hiện hành.
- **Trục ghép vé:** giữ nguyên ranking của một mô hình, so generator cũ, `rank-portfolio-v1` và ứng viên coverage. Không đồng thời đổi mô hình và generator rồi quy công cho một bên.
- **Ngân sách:** chọn ba vé/kỳ là ngân sách chính cho thử nghiệm workspace mới. Một vé top sáu và năm vé AI Lab là các báo cáo phụ riêng; giữ nguyên ngân sách ledger hiện có. Không so ba vé ứng viên với một vé đối chứng.
- **Đối chứng ngẫu nhiên:** baseline đều có seed khóa trước; chạy nhiều replicate offline để ước lượng biến thiên. Không chọn seed tốt nhất; replicate không được cộng vào số kỳ đánh giá. Baseline tần suất và hiện hành phải cùng tập kỳ hợp lệ.
- **Chia theo thời gian:** inner walk-forward chọn tham số, outer window đánh giá cấu hình đã chọn; toàn bộ 45/55 hàng của một kỳ phải cùng fold. Scaler, feature selection, calibrator và mô hình chỉ fit bằng dữ liệu được phép trước target. Khoảng thiếu kỳ không bị coi là kỳ liên tiếp.
- **Dữ liệu đã xem:** fixture và các kỳ đã dùng điều chỉnh là development. Chỉ gọi holdout khi thực sự chưa dùng trong chọn mô hình; xác nhận chính dùng các kỳ tương lai mới sau khi khóa ứng viên. Không sinh bù prospective snapshot.
- **Replay feedback:** tại kỳ D chỉ dùng kết quả trước D cùng feature đã tạo trước kết quả tương ứng. Thiếu snapshot thì báo không có feedback; nếu tái tạo để mô phỏng phát triển, phải gắn nhãn riêng và không ghi vào prospective ledger. Báo số kỳ feedback thực sự được sử dụng để ablation không bị so hai nhánh giống nhau mà không biết.

Lưu kết quả theo từng kỳ: actual, vé, score/ranking, baseline, budget, feature schema, model/policy version, cấu hình, thời điểm dữ liệu, seed và hash. Báo cáo JSON phục vụ tái lập, Markdown phục vụ review. Phương pháp tham khảo: [TimeSeriesSplit](https://scikit-learn.org/stable/modules/generated/sklearn.model_selection.TimeSeriesSplit.html) và nghiên cứu về [selection bias khi chọn mô hình](https://www.jmlr.org/papers/v11/cawley10a.html).

**Nghiệm thu:** đảo thứ tự đầu vào không đổi kết quả; thay dữ liệu ở kỳ tương lai không đổi dự đoán quá khứ; các bên so sánh cùng category, kỳ đánh giá, lịch sử đầu vào và ngân sách, giữ version của từng bên; báo đầy đủ cả cấu hình thất bại. Replay dùng cấu hình hiện tại luôn được ghi là replay phát triển.

### P2 — Thử ứng viên đơn giản trước

| Ứng viên | Thay đổi dự kiến | Điều kiện và giới hạn |
| --- | --- | --- |
| Tần suất co về prior | `p_i = (c_i + κ × 6/N) / (T + κ)`; κ được chọn trên inner validation | Baseline xác suất dễ kiểm tra; tổng marginal bằng 6 với dữ liệu đủ sáu số/kỳ. Với cùng κ và cửa sổ cho mọi số, ranking giống tần suất, không tuyên bố tự cải thiện top sáu |
| Logistic hiện có | Tune có giới hạn L2/cửa sổ; ablation bỏ từng feature, có/không replay feedback, có/không decay | Tận dụng mô hình đã học thật; feedback phải chứng minh đóng góp trên các kỳ ghép cặp. Không tăng thưởng chỉ vì vừa có vé trúng |
| Hiệu chuẩn | Thử sigmoid từ dự đoán ngoài fold, so với không hiệu chuẩn | Không fit calibrator trên kết quả test; không ưu tiên isotonic khi ít kỳ. Brier/log loss không thay cho kết quả vé |
| Heuristic hiện hành | Ablation từng bonus lân cận/gap/graph và thành phần ensemble | Kiểm tra đóng góp thật; không diễn giải số gan là “đến hạn phải xuất hiện” |
| XGBoost thật, tùy kết quả P0–P2 | Challenger giới hạn độ sâu, regularization, early stopping chỉ trên validation | Chỉ thêm nếu đủ dữ liệu cho quy trình đo; cần feature schema, artifact, training cutoff, config và version. Đánh giá chi phí JVM/native/build trước khi thêm dependency |

Tần suất làm trơn là suy luận áp dụng từ [partial pooling](https://mc-stan.org/learn-stan/case-studies/pool-binary-trials.html), không phải mô hình Vietlott đã được chứng minh. Cách hiệu chuẩn tham khảo [tài liệu scikit-learn](https://scikit-learn.org/stable/modules/calibration.html); Java có thể train XGBoost qua [XGBoost4J](https://xgboost.readthedocs.io/en/stable/jvm/java_intro.html). Tham khảo thuật toán không bắt buộc đổi stack sang Python hoặc thêm một dịch vụ ML mới.

Không ưu tiên LSTM/Transformer, reinforcement learning, hàng chục feature cặp/bộ ba hoặc stacking phức tạp trong đợt đầu. Số hàng `(kỳ, số)` lớn hơn số kỳ không giải quyết thiếu nhãn độc lập. Các marginal cũng không xác định đầy đủ phân phối sáu số; không nhân sáu xác suất để ghi “xác suất trúng vé”.

**Nghiệm thu:** chọn tối đa một ứng viên cho mỗi loại để xác nhận tương lai, theo quy tắc đã chốt; nếu không đạt thì kết luận chưa có lợi thế và giữ mô hình đang dùng. Không đổi mặc định chỉ vì một kỳ có vé ≥4.

### P3 — Thử cách ghép vé hướng tới ≥4 số

Áp dụng như một policy mới bên cạnh `rank-portfolio-v1`, không thay/sửa các vé đã chốt:

1. Sinh candidate vé hợp lệ từ ranking, vẫn cho phép mọi số trong miền được xét. Quy định trước giới hạn số candidate, seed và runtime.
2. Greedy rồi local search để giảm vé trùng, cân bằng exposure và tăng số bộ ba/bộ bốn khác nhau được phủ. Điểm rank và ràng buộc overlap là tham số thử nghiệm, không ép overlap≤3 thành chân lý cho mọi mục tiêu.
3. So chính sách giữ vé top sáu với chính sách được phép bố trí lại toàn bộ nhóm trong tập phát triển. Khóa một policy trước xác nhận; không thử nhiều policy rồi chỉ công bố nhóm thắng.
4. Độ phủ bộ bốn là chỉ số trung gian. Metric cuối là kết quả từng vé và tỷ lệ kỳ có ít nhất một vé ≥3/≥4 ở cùng budget. Nếu mô phỏng outcome, công bố rõ giả thiết phân phối, seed và sai số Monte Carlo; không gọi xác suất mô phỏng là tỷ lệ trúng đo được.

[Covering designs](https://arxiv.org/abs/math/9502238) là cơ sở tham khảo cho bố trí tập con. Bao phủ một pool không bảo đảm sáu số kết quả thuộc pool; chưa có cam kết bảo đảm trúng với ba hoặc năm vé. Thử nghiệm v1 phải dùng chung ranking để đo riêng đóng góp của generator.

### P4 — Tiêu chí đánh giá và đổi mô hình mặc định

| Tầng đánh giá | Metric và mẫu số | Vai trò |
| --- | --- | --- |
| Xác suất mỗi số | Brier, log loss so `6/N`, reliability bins có count; tổng hợp từng kỳ rồi mới so cặp | Chọn/xem chất lượng mô hình xác suất; không áp lên điểm heuristic |
| Một vé | Mean matches, histogram đúng 0–6, số/tỷ lệ vé ≥3/≥4/≥5 | Theo dõi ranking bằng vé top sáu cố định; không chọn vé tốt nhất sau kỳ làm đại diện mô hình |
| Nhóm ba vé | Tỷ lệ kỳ có ít nhất một vé ≥3 là metric xác nhận chính được đề xuất; ≥4/≥5/6 và best-of-budget là metric phụ | Sự kiện ≥4 hiếm; luôn báo số lần và khoảng bất định, không chọn tham số chỉ từ một sự kiện |
| Đối chiếu cùng kỳ | Chênh lệch ứng viên − baseline, số kỳ ghép cặp, ngân sách và phiên bản | Làm cơ sở quyết định; độ phủ nhiều vé không được gọi là một vé trúng |

Trước khi khóa ứng viên, dùng biến thiên trên development để xác định mức cải thiện tối thiểu có ý nghĩa, số kỳ cần quan sát và ngày/điều kiện kết thúc. Nếu muốn chuyển metric chính sang ≥4, phải chốt lại protocol và lượng mẫu trước khi xem kết quả xác nhận; không hứa có kết luận sau 30 hoặc 60 kỳ.

Khoảng bất định phải dùng kỳ quay làm đơn vị: ghép cặp cùng ngày; xem xét block bootstrap theo thời gian khi có phụ thuộc. Với tỷ lệ sự kiện hiếm, báo count và khoảng phù hợp cả trường hợp zero-event; không dùng bootstrap suy biến toàn 0 để khẳng định chắc chắn bằng 0. Khóa danh sách phép so sánh và điều chỉnh cho nhiều phép thử; không dừng sớm khi biểu đồ vừa tốt. Các dashboard đang xem hằng ngày mang nhãn thống kê mô tả cho đến thời điểm đánh giá đã định.

Ngưỡng hiện có “60 kỳ, hai khối 30 kỳ ≥50% đạt vé ≥3” chỉ là screening cũ. Không hạ ngưỡng để tạo badge thành công; thay tiêu chí bằng protocol có version khi triển khai. Đổi mô hình chính cần kết quả xác nhận mới, cùng budget và baseline, dữ liệu đủ chất lượng, cấu hình đóng băng và phương án quay về phiên bản trước.

`AiLabService` hiện fingerprint cả phần báo cáo như `AiLabProbability`; thay cách tính báo cáo có thể tạo cohort mới dù scorer không đổi. Bổ sung `experimentId`, `modelPolicyVersion`, `evaluationVersion`, vẫn giữ `codeVersion`/artifact hash để audit. Không gộp cohort cũ tự động; chỉ lập nhóm tương đương khi có chứng cứ/test cùng hành vi và báo rõ cách nhóm.

## 4. Kế hoạch UI hiện đại hóa

### Thiết kế và luồng chính

Giữ Angular, router, `CategoryService`, module `core/feature/share` và các component hiện có. Chọn nền sáng trung tính, chữ tối tương phản cao, accent emerald; MEGA/POWER dùng màu nhận diện phụ. Định nghĩa token màu/spacing/radius/typography, card gọn, icon có nhãn, giảm gradient/khối hero cao. Không đổi UI nghiệp vụ chứng khoán; regression shell trên route chứng khoán để tránh ảnh hưởng ngoài phạm vi.

```text
Header: logo / tên ứng dụng                         ngôn ngữ / trạng thái
Sidebar      Context: MEGA | POWER · kỳ mục tiêu · dữ liệu đến ngày …
             Tạo dự đoán | Lịch sử & hiệu quả | Phân tích kỳ quay

             [Nguồn: Thuật toán / Kết hợp / Nhóm giảm trùng / AI Lab]
             [Các card vé sáu số + nguồn]       [Vé đang chọn]
             [Thông tin nguồn, xem chi tiết]   [Sửa / bỏ vé]
                                               [Chốt vé cho kỳ …]
```

Mobile: sidebar thành drawer; nội dung một cột; nhóm vé đã chọn và nút chốt gần nhau, không che ô nhập hoặc focus. Bố cục desktop hai cột có thể dùng sticky cho khay vé; lịch sử chi tiết vào tab đã có hoặc disclosure, không mở tất cả nhóm cùng lúc. Khi chuyển vị trí lịch sử, giữ riêng vé người dùng có revision và ledger thuật toán; không gộp thành một loại bằng chứng.

### Hạng mục theo màn hình

| Màn hình/thành phần | Thay đổi cụ thể | Nghiệm thu |
| --- | --- | --- |
| Shell/header/sidebar | Token chung; category có một vị trí trong context xổ số; thay absolute-center bằng grid/flex; link trực tiếp `/analyze/review`; giữ redirect cũ | Đổi tab giữ category, active menu đúng, mobile không chồng logo; nút tương tác chính cao tối thiểu 44px theo tiêu chí thiết kế đợt này |
| Tạo dự đoán/user desk | Nhóm nguồn dạng tab; card sáu số; nhãn gợi ý/đã sửa/đã chốt; khay vé chọn; xác nhận thành công gắn kỳ và nguồn | Giữ draft, `requestKey`, `expectedRevision`, provenance; hiển thị conflict 409, tránh chốt trùng hoặc mất chỉnh sửa; MEGA portfolio chỉ hiện đúng loại/kỳ và tiếp tục loại bộ trùng gốc/Lab theo context |
| Lịch sử & hiệu quả | Tóm tắt cùng ngân sách, số kỳ, histogram 0–6; chọn mức ≥3/≥4/≥5; so với baseline; bộ lọc phiên bản/khoảng ngày | Phân biệt theo vé/theo kỳ/độ phủ; null là “Chưa đo”, 0 là đã đo bằng 0; không gộp version chỉ để tăng mẫu |
| Phân tích kỳ quay | Kết quả và các vé khớp lên đầu; đồng bộ nền với workspace; explanation/hash/chi tiết xuống disclosure | Đúng snapshot được chọn; không tính lại bằng cấu hình hiện tại khi snapshot thiếu điểm |
| AI Lab | Giảm hero; đưa số kỳ đủ điều kiện, model/version, trạng thái bằng chứng và baseline lên đầu; giữ Brier/calibration/feedback comparison | Thanh tiến độ dữ liệu không được diễn giải thành xác suất thắng; không lặp xây lại metric đã có |

Biểu đồ dự kiến: histogram 0–6, chênh lệch hiệu quả theo kỳ và reliability diagram cho Logistic. Luôn có bảng số liệu tương ứng; tooltip chỉ bổ sung thông tin. Không cần thêm thư viện chart trong U1; đánh giá khả năng tái sử dụng khi có hợp đồng dữ liệu U3.

### Sửa nội dung dễ gây hiểu nhầm

- Đồng bộ catalog/DTO/UI: “heuristic kết hợp”, “Logistic Regression”, “XGBoost đã huấn luyện” chỉ khi đúng implementation. Bỏ copy về “dao động lồng cầu”, “bảo hiểm giải thưởng” và “phân bố xác suất XGBoost” của nhánh heuristic.
- Phân biệt điểm xếp hạng, xác suất một số xuất hiện, xác suất của vé và tỷ lệ quan sát. Gắn nhãn “Backtest phát triển” hoặc “Đã lưu trước kỳ quay”; không đặt một phần trăm “độ tin cậy” chung.
- POWER vẫn chỉ chọn sáu số. Backend [TicketAnalysis](../src/main/java/com/example/analyzeproject/application/service/TicketAnalysis.java) đã tính Jackpot 2 từ năm số chính và số đặc biệt nằm trong vé. UI cũ [copyTicket](../../frontend/src/app/feature/prediction/prediction.component.ts) còn nối số phụ tham khảo vào clipboard: sửa luồng sao chép vé chỉ xuất sáu số; số đặc biệt của kết quả nằm ở phần kết quả riêng. Giữ tương thích DTO lịch sử; không coi trường dự đoán số phụ là một số mua thêm. Luật tham khảo [POWER Vietlott SMS](https://info.vietlott-sms.vn/game_power.html).
- Chỉ báo “Đã sao chép” sau khi clipboard thực sự thành công; nếu bị từ chối thì có cách chọn/copy thủ công. Giữ ngôn ngữ Việt rõ ràng, cập nhật các khóa i18n có liên quan; không đưa tên database hoặc hash kỹ thuật lên mặt chính.

### Trạng thái và hợp đồng API

Mở rộng các DTO hiện có theo hướng thêm trường, không phá client/snapshot cũ. Dự kiến overview có `evidenceType`, khoảng ngày, `budget`, `eligibleDraws`, `pairedDraws`, `excludedByReason`, histogram, rate ≥3/≥4/≥5, chênh lệch/interval, `experimentId` và version. Tên cuối được chốt cùng contract tests; score có loại rõ ràng để frontend không tự suy ra xác suất.

Tái sử dụng `ForecastOverview`, `LabOverview`, `DrawReport`, `WorkspaceApiService`; metric mới lấy từ backend cùng một định nghĩa. Overview nhỏ/phân trang, chi tiết tải khi mở; không gọi backtest cho từng card. Runner offline xuất artifact; chỉ thêm store/migration mới nếu cần lưu benchmark bền vững, không sửa migration cũ.

Các state bắt buộc: loading, empty, API lỗi, dữ liệu cũ, chưa đủ dữ liệu, kết quả chưa xác minh, chờ quay, chờ đối soát, đã chốt và nháp đã sửa. Đổi category/filter hủy request cũ; polling không xóa lựa chọn hoặc nháp. Mở trang lịch sử/review chỉ đọc; tạo/chốt/đối soát giữ hành động có chủ đích theo API hiện có.

Điểm sửa chính: [frontend README](../../frontend/README.md), [workspace](../../frontend/src/app/feature/prediction/prediction-workspace.component.ts), [prediction](../../frontend/src/app/feature/prediction/), [forecasts](../../frontend/src/app/feature/forecasts/), [review](../../frontend/src/app/feature/latest-draw-analysis/), [AI Lab](../../frontend/src/app/feature/ai-lab/), [models](../../frontend/src/app/core/models/). Kiểm tra đường dẫn component thực tế trước từng đợt sửa.

## 5. Các đợt triển khai đề xuất

Ước lượng là ngày công phát triển, chưa gồm thời gian chờ kỳ quay, bổ sung lịch sử hoặc vận hành production. Mỗi đợt tạo diff và báo cáo riêng; không cần triển khai hết nghiên cứu mới có UI tốt hơn.

| Đợt | Công việc | Phụ thuộc | Sản phẩm và điều kiện kết thúc | Ước lượng |
| --- | --- | --- | --- | --- |
| 1 | P0 kiểm kê + U1 shell/tokens/copy/POWER sáu số | Quyền đọc dữ liệu thực tế; UI có thể làm độc lập | Manifest dữ liệu; shell responsive; nội dung đúng; regression luồng hiện có | 2–3 ngày |
| 2 | P1 benchmark + U2 bố cục chọn/chốt | Manifest, metric/budget đã khóa; U2 dùng API hiện có | Runner tái lập; baseline cùng kỳ; workspace giữ draft và nguồn vé | 3–5 ngày |
| 3 | P2 ablation + P3 thử portfolio | Benchmark đạt | Báo cả kết quả âm; tối đa một ứng viên mỗi loại; XGBoost có thể hoãn nếu dữ liệu chưa phù hợp | 4–6 ngày |
| 4 | U3 dashboard hiệu quả/review/Lab + contract | Schema báo cáo ổn định | So sánh có mẫu số/version, biểu đồ và bảng đồng nhất, đầy đủ trạng thái thiếu dữ liệu | 3–4 ngày |
| 5 | P4 chạy song song trên kỳ tương lai | Cấu hình/protocol khóa, backend vận hành | Snapshot trước cutoff, đối soát, lịch đánh giá, theo dõi bỏ kỳ và rollback | 1–2 ngày thiết lập; thu thập theo thiết kế mẫu |

Tổng dự kiến **13–20 ngày công**, có thể song song backend/UI; XGBoost và bổ sung lịch sử lớn cần ước lượng lại sau P0. Ưu tiên bàn giao đợt 1–2 trước. Dữ liệu chưa đủ không cản cải thiện UI hoặc benchmark; nó cản kết luận về hiệu quả dự đoán.

## 6. Kiểm chứng và tiêu chí hoàn tất khi triển khai

- Thuật toán: history rỗng/đảo thứ tự/sai/trùng/revision mới cách ly; kỳ thiếu; feature chỉ dùng quá khứ; fit/calibration đúng fold; seed ổn định; mọi vé sáu số khác nhau; ngân sách bằng nhau; metric dùng đúng kỳ và giữ paired cohort.
- Snapshot/vận hành: chốt trước 18:00, đối soát sau 19:00 giờ Việt Nam; thiếu kỳ ngay trước target thì chưa sẵn sàng; không backfill dự đoán, không sửa snapshot cũ; retry/idempotency và kết quả bị rút/sửa giữ đúng audit.
- UI: MEGA/POWER, chuyển tab/category, request đến sai thứ tự, pending không tính là thua, null/0, chốt 409, clipboard lỗi, sao chép đúng sáu số POWER, khôi phục nháp. Kiểm tra route chứng khoán sau đổi shell.
- Trực quan/truy cập: viewport 360/390/768/1440, zoom 200%, bàn phím/focus, contrast, reduced motion; chụp trước/sau bằng dữ liệu cùng trạng thái. Trạng thái không chỉ truyền bằng màu.
- Kiến trúc: chạy `OnionArchitectureTest`; giữ domain thuần, logic thống kê ở backend, component có input/output riêng và stylesheet cạnh component; không truyền nguyên instance cha.
- Khi đổi code: kiểm tra Node thực tế và toolchain, chạy format/typecheck/test frontend phù hợp, `npm.cmd run build` trong frontend rồi `mvn.cmd install` trong backend; kiểm tra assets Angular mới nằm đúng trong JAR. Chạy PostgreSQL integration khi thay store/migration; không gọi mock test là PostgreSQL thật.
- Build/test đạt xác nhận triển khai đúng, không xác nhận tăng xác suất trúng. Báo riêng kết quả kỹ thuật và kết quả nghiên cứu. Dùng snapshot versioned để quay lại champion; không rollback bằng xóa ledger.

## 7. Kiểm chứng riêng cho tài liệu này

Đã đọc workflow `.github`, đối chiếu mã backend/frontend và các báo cáo hiện có; tra cứu nguồn phương pháp; đếm lại fixture và tính baseline tổ hợp. Rà soát UI từ code, chưa kiểm tra giao diện chạy thực tế trong trình duyệt. Production API timeout nên chưa có audit live ngày 07/11.

Lượt này chỉ cập nhật tài liệu kế hoạch và liên kết định hướng; không thay thuật toán, giao diện ứng dụng, database, dependencies hoặc triển khai hosting. Kiểm tra liên kết local và `git diff --check`; không chạy build ứng dụng cho thay đổi chỉ tài liệu theo workflow của dự án.

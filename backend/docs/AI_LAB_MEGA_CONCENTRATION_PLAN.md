# AI Lab MEGA 15/11/2026 — Đối chiếu và kế hoạch cải thiện ghép vé

Trạng thái: **đã phân tích dữ liệu production và lập kế hoạch; chưa triển khai thay đổi thuật toán**. Phạm vi là AI Lab theo xác nhận của người dùng. Ngân sách đánh giá giữ năm vé/mô hình/kỳ. Không thay UI, cấu hình, dữ liệu hoặc bản dự đoán đã chốt.

Cập nhật sau khi người dùng cho phép triển khai: đã thêm hai ứng viên tập trung/kết hợp, chẩn đoán phân tán, benchmark năm vé và phần thử nghiệm màu tím trên trang Dự đoán. Xem [triển khai và giới hạn](AI_LAB_CONCENTRATION_IMPLEMENTATION.md). Các phần dưới giữ như kế hoạch ban đầu; xác nhận tương lai và chọn policy tối ưu cần dữ liệu tiếp theo.

## 1. Kết luận từ dữ liệu thật

Nhóm `logistic_coverage` đã phủ đúng **06, 22, 24, 26, 44** trong kỳ MEGA 15/11/2026. Đây là độ phủ của năm vé; vé tốt nhất khớp **2/6**, chưa có vé khớp ba số.

Kết quả được API lưu là **06 22 24 26 40 44**, kỳ nguồn `01589`, trạng thái `SOURCE_MATCHED`. Feed cộng đồng trùng kết quả; scheduler MEGA báo `SUCCESS`, `sourceStale=false`. Chưa xác minh độc lập qua Vietlott: trang chính thức trả HTTP 403 trong lần đọc này. Không gọi trạng thái khớp feed cộng đồng là xác nhận chính thức.

Năm vé được chốt cùng lúc `2026-11-13T15:15:29.917880217Z`, tức khoảng **22:15 ngày 13/11 giờ Việt Nam**, học từ **47 kỳ**, kỳ cuối 13/11. API draw-report nhận đủ năm bằng chứng `SAVED_BEFORE_DRAW`, cùng history hash và chính sách `quad-coverage-v1`. Phân tích dùng ranking đã lưu, không huấn luyện lại với kết quả 15/11.

| Vé / slot | Dãy đã chốt | Số khớp | Số lượng |
| --- | --- | --- | ---: |
| 1 / 0 | 08 12 24 27 29 35 | 24 | 1 |
| 2 / 1 | 07 16 22 27 32 34 | 22 | 1 |
| 3 / 2 | 13 17 18 19 21 42 | — | 0 |
| 4 / 3 | 04 11 26 28 37 44 | 26, 44 | 2 |
| 5 / 4 | 01 02 06 10 15 33 | 06 | 1 |

Toàn bộ năm vé chọn **29 số khác nhau** trong 30 vị trí; chỉ số 27 lặp hai lần. Chín trong mười cặp vé không chung số, cặp còn lại chung một số. Số 40 không có trong nhóm này.

Các nhóm cùng kỳ trên production, mỗi nhóm đủ năm vé:

| Mô hình | Số khác nhau đã chọn | Độ phủ kết quả | Khớp theo slot 0–4 | Vé tốt nhất |
| --- | ---: | ---: | --- | ---: |
| `logistic` | 24 | 3/6 | 1, 0, 1, 0, 2 | 2 |
| `logistic_no_feedback` | 24 | 3/6 | 1, 0, 1, 0, 2 | 2 |
| `recency` | 19 | 2/6 | 1, 1, 0, 0, 0 | 1 |
| `shrunk_frequency` | 23 | 4/6 | 2, 1, 1, 1, 0 | 2 |
| `logistic_coverage` | 29 | 5/6 | 1, 1, 0, 2, 1 | 2 |
| `logistic_feedback_coverage` | 30 | 4/6 | 1, 2, 0, 0, 1 | 2 |

Logistic có/không feedback cho cùng năm dãy trong kỳ này. Chúng không phải mười vé độc lập. Số 40 có trong nhóm khác; kết luận “thiếu 40” chỉ áp dụng nhóm coverage đang xét. Đối chứng ngẫu nhiên đã chốt có số khớp `2,0,2,1,0`: cùng trung bình **1 số/vé**, cùng vé tốt nhất **2 số** như coverage. Đây chỉ là một kỳ và một bộ seed đối chứng.

## 2. Tại sao số đúng bị phân tán

### Ranking chỉ ưu tiên được một phần số đã ra

| Số kết quả | Hạng trước kỳ | Điểm nội bộ | Nằm ở vé coverage |
| --- | ---: | ---: | --- |
| 06 | 7 | 0,133449681 | 5 |
| 22 | 40 | 0,133095946 | 2 |
| 24 | 2 | 0,133518130 | 1 |
| 26 | 8 | 0,133449466 | 4 |
| 40 | 44 | 0,132961273 | Không có |
| 44 | 10 | 0,133429699 | 4 |

Top sáu là `08 24 27 29 35 12`, chỉ chứa 24. Top mười chứa bốn số kết quả: 06, 24, 26, 44. Vì 22 ở hạng 40, ghép vé chỉ từ top 10/12/16 sẽ không thể giữ đủ năm số đúng đã quan sát; cũng không đảm bảo bốn số còn lại cùng nằm trên một trong năm vé.

Toàn bộ 45 điểm nằm trong khoảng **0,132935589–0,133587168**, tỷ số cao nhất/thấp nhất khoảng **1,0049**. Chênh lệch nhỏ không chứng minh các hạng đầu có lợi thế rõ rệt. Trường lưu ghi `uncalibrated_model_score`, không phải xác suất trúng đã hiệu chuẩn.

### Bộ ghép đang thưởng độ phủ và phạt lặp số

Trong [CoveragePortfolio.java](../src/main/java/com/example/analyzeproject/domain/service/CoveragePortfolio.java), `quad-coverage-v1`:

- Giữ một vé gồm top sáu; tạo tối đa 320 lượt candidate từ toàn miền 1–45 với RNG có seed.
- Chọn vé bằng greedy và hai lượt tìm kiếm cục bộ.
- Tối đa hóa `số bộ bốn / 15 + 0,2 × số bộ ba / 20 + 0,35 × tổng utility / 6 − 0,10 × lượt lặp`, với cấu hình mặc định trong checkout.
- Đếm các bộ ba/bốn khác nhau **bất kể chúng có tín hiệu dự đoán mạnh hay yếu**; không có objective trực tiếp cho xác suất một vé khớp năm số.

Năm vé thực tế đã phủ 100 bộ ba và 75 bộ bốn, bằng số tối đa với năm vé khi không lặp các bộ con đó. Một khi hai thành phần này bão hòa, chúng không phân biệt được nhiều phương án ghép khác nhau. Phạt lặp số tiếp tục khuyến khích trải rộng. Hiện tượng chỉ lặp một số phù hợp với mục tiêu này; không phải bằng chứng generator đã nhận ra rồi cố ý tách năm số thắng.

Generator còn chuyển rank sang utility tuyến tính `1 − (rank−1)/45`, rồi sampling bằng trọng số `0,25 + utility`. Chênh lệch trọng số candidate cao/thấp khoảng 4,59 lần dù điểm mô hình kỳ này gần như bằng nhau. Đây là một biến đổi heuristic cần kiểm định riêng; không nên coi thứ hạng là độ tin cậy mạnh, hoặc khẳng định riêng biến đổi này gây ra kết quả.

Chính sách và vé production khớp mô tả trên; chưa chứng minh bytecode lịch sử giống hoàn toàn checkout hiện tại. Production có thêm `logistic_feedback_coverage` chưa có trong danh sách `MODELS` local. Cần đối chiếu phiên bản triển khai trước khi sửa; không triển khai lại checkout cũ và vô tình bỏ nhánh đang chạy.

### Mô hình học từng số, chưa học cách ghép cả vé

[AiLabModel.java](../src/main/java/com/example/analyzeproject/domain/service/AiLabModel.java) dùng frequency, recent10, decay10, gap30 để chấm từng số. Feedback cũng cập nhật nhãn từng số một lần mỗi kỳ; nó không trực tiếp học rằng một nhóm bốn/năm số nên cùng xuất hiện trên vé. Không có cơ sở coi số 22 được chọn qua coverage là dự báo mạnh tương đương số hạng 2.

AI Lab standard và coverage không có bộ lọc chẵn/lẻ loại dãy toàn chẵn. Không quy kết kỳ toàn số chẵn này cho các quy tắc của pipeline dự đoán khác.

### Phủ rộng khiến “có đủ năm số” dễ xảy ra hơn vẻ ngoài

Với **một tập 29 số cố định trước kỳ**, giả sử kết quả quay đều sáu số không hoàn lại trong 45 số:

```text
Số kết quả được phủ kỳ vọng = 6 × 29 / 45 = 3,8667
P(phủ ít nhất 5 số) = [C(29,5) × C(16,1) + C(29,6)] / C(45,6)
                    = 29,1600%
```

Đây là tham chiếu tổ hợp cho độ phủ của cả tập, **không phải xác suất một vé trúng năm số**, cũng không phải kiểm định đã điều chỉnh việc nhìn nhiều mô hình rồi chọn nhóm tốt nhất. Kết quả 5/6 này chưa chứng minh mô hình gần dự đoán được một vé 5/6.

## 3. Kế hoạch triển khai đề xuất

Mục tiêu: tăng khả năng **một trong năm vé** có nhiều số khớp, được kiểm chứng ngoài dữ liệu dùng chọn tham số. Không chọn tay 06/22/24/26/44, không tăng số vé để làm thành tích tốt lên, không sửa dự đoán đã chốt.

### P0 — Chốt dữ liệu và phiên bản làm mốc

Đối chiếu kỳ 15/11 đã hoàn tất trong lượt này. Trước triển khai, xuất manifest runtime và đối chiếu mã của sáu model production, hash cấu hình, lịch sử, generator, seed và model weights với source tương ứng. Kiểm tra lịch sử 47 kỳ: miền số, revision, khoảng thiếu, nguồn và cutoff. Tăng dữ liệu chỉ sau audit, nhập qua luồng có provenance; không tự coi toàn bộ feed cộng đồng là lịch sử đã xác minh.

Đầu ra: một bộ dữ liệu phát triển đóng băng; danh sách kỳ/snapshot bị loại có lý do; biết chính xác nhánh coverage có/không feedback. Tái sử dụng ledger/protocol hiện có.

### P1 — Bổ sung chẩn đoán chọn số và ghép vé

Mở rộng thống kê hiện có trong `TicketAnalysis`, `AiLabProgress` và `PredictionBenchmark`; không xây lại phần đối soát. Tính theo **một model/version/history/kỳ, đủ năm slot**:

| Chỉ số | Ý nghĩa / yêu cầu |
| --- | --- |
| `unionHits`, `uniqueNumberCount` | Bao nhiêu số kết quả nằm trong toàn bộ năm vé và đã trải rộng bao nhiêu số |
| `bestTicketHits`, histogram 0–6 | Số khớp trên vé tốt nhất, kết quả thực tế từng vé |
| `topKHits`, `topKRecall` | Khả năng giữ số kết quả ở top K của ranking đóng băng, K chốt trước đánh giá |
| `assemblyGap = unionHits − bestTicketHits` | Mô tả mức phân tán; kỳ này bằng 3, không phải ba số “đáng ra chắc chắn ghép được” |
| `conditionalHit4/5` | Tỷ lệ có vé ≥4/≥5 khi tập ứng viên đủ ≥4/≥5 số kết quả; ghi rõ mẫu số, không lấy điều kiện sau kỳ làm quyết định dự đoán |
| Exposure, overlap, bộ con 4/5 | Nhìn thấy tập trung/độ phủ và saturation của objective |
| Độ phân tán điểm, Brier/log loss | Phân biệt thay thứ hạng với tín hiệu dự đoán đã được đo |

API draw-report hiện đã có coverage/best/overlap cho nhóm vé; Lab progress đã có histogram, mức ≥1…≥6 và đối chứng. Bổ sung cách gom batch và liên kết ranking; giữ tên/ý nghĩa các trường cũ. UI nằm ngoài lần lập kế hoạch này.

### P2 — Thử bộ ghép tập trung, giữ nguyên scorer để đo riêng tác dụng

So sánh các generator trên **cùng ranking/điểm/history đã đóng băng**, cùng năm vé:

1. `seeded-weighted-v1` và `quad-coverage-v1` hiện tại làm mốc.
2. Challenger tập trung: tạo tổ hợp sáu số từ top K, thử một lưới nhỏ K = 10/12/16 trong validation; chọn năm vé theo utility và mức phủ các nhóm 4/5 có trọng số. Cho phép lặp có kiểm soát để các số tốt có cơ hội đi cùng nhau. Đây là giả thuyết thử nghiệm, chưa phải mặc định mới.
3. Challenger kết hợp: dành 2 hoặc 3 vé cho tập top K, các vé còn lại xét toàn miền bằng chính sách chốt trước. Mục đích là đo đánh đổi giữa tập trung và bỏ sót số hạng thấp. Không gắn số hạng thấp cụ thể từ kỳ 15/11 vào policy.

Giữ vé top sáu giống nhau giữa các nhánh nếu muốn đo riêng bốn slot còn lại; ghi rõ constraint này trong manifest. Số candidate, seed, tie-break, lưới tham số và số phép thử phải cố định. Điểm weighted-subset là utility, không gắn nhãn xác suất thắng.

Tách một ablation về cách dùng điểm: rank tuyến tính hiện tại so với utility có làm trơn/hiệu chuẩn chỉ từ dữ liệu quá khứ. Không đồng thời đổi scorer, feedback và generator rồi quy toàn bộ khác biệt cho ghép vé. Điểm gần phẳng là lý do cần đối chứng tập trung, không phải lý do tự tăng độ sắc của score.

Không ưu tiên học bộ năm hoặc thêm mô hình phức tạp ngay trên 47 kỳ. Nếu sau audit và tăng lịch sử vẫn thấy lỗi ranking ổn định, mới thử feature cặp có làm trơn/ngưỡng support riêng; so sánh ablation và kiểm tra ngoài mẫu. Liên kết lịch sử không tự chứng minh số sẽ đi cùng nhau ở kỳ tới.

### P3 — Đánh giá theo thời gian và cùng ngân sách

Mở rộng `PredictionBenchmark` với `budget=5` rõ ràng (mặc định hiện tại là 3). Cho mỗi kỳ D, mọi training/feedback/hiệu chuẩn chỉ dùng dữ liệu trước D. Chọn K, tỷ lệ tập trung và hệ số trong validation trước cửa sổ đánh giá; không chọn lại seed theo kết quả. Kỳ 15/11 đã được xem dùng làm case phân tích/regression, không làm kiểm tra giữ kín cho phương án mới.

Đối chứng gồm sampler cũ, coverage cũ, tần suất và vé ngẫu nhiên đều, cùng năm vé. Để tách lợi ích của scorer và cấu trúc ghép vé, thêm đối chứng xáo nhãn số của chính portfolio hoặc generator dùng điểm đồng đều, giữ budget/overlap tương ứng. Các replicate làm rõ dao động Monte Carlo; chúng không tăng số kỳ độc lập.

Chốt trước phép so sánh và metric chính **tỷ lệ kỳ có ít nhất một vé ≥4 số**; báo cáo thêm ≥3, ≥5, trung bình `bestTicketHits`, ranking recall và độ phủ. Nếu sự kiện ≥4 quá ít, ghi chưa đủ bằng chứng, không đổi metric chính sau khi nhìn kết quả. `bestTicketHits` là chẩn đoán nhạy hơn, không thay thế bằng chứng cho mục tiêu ≥4/≥5.

Đơn vị tính bất định là kỳ quay: so cặp trên cùng kỳ, bootstrap theo kỳ/block phù hợp và khoảng cho sự kiện hiếm; không xem năm vé hoặc nhiều mô hình cùng kỳ là mẫu độc lập. Ghi số thử/correction nhiều phép so sánh. Chỉ chuyển một ứng viên đã chọn sang giai đoạn xác nhận; không tự promote theo replay.

### P4 — Chốt thử nghiệm tương lai và tiêu chí hoàn tất

Lưu ứng viên mới dưới version riêng bằng scheduler/ledger hiện có, giữ nguyên các snapshot cũ và đối chứng. Đăng ký trước mức cải thiện tối thiểu, cỡ mẫu theo power analysis, thời điểm review, phép so sánh chính và quy tắc dừng; không lấy ngưỡng 60 kỳ screening sẵn có làm bảo đảm đủ mẫu cho sự kiện hiếm ≥4/≥5.

Chỉ đề xuất thay mặc định khi đánh giá tương lai cho thấy lợi ích vượt mức đã đăng ký và khoảng bất định phù hợp; nếu chưa đủ sự kiện hoặc bằng chứng không thuận lợi thì tiếp tục giữ đối chứng/mặc định. Không cần mua vé thật để chạy đánh giá này.

## 4. Kiểm chứng khi triển khai

- Regression cho batch đúng năm vé/sáu số khác nhau, toàn miền hợp lệ, không trùng nguyên vé, kết quả tái lập và không sửa ledger cũ.
- Chỉ số pool/best/exposure tính độc lập; đảo thứ tự vé không đổi kết quả; duplicate, batch thiếu, snapshot trễ, kết quả revision/rút lại được xử lý đúng.
- Case 15/11 phải tính lại ra `unionHits=5`, `bestTicketHits=2`, `uniqueNumberCount=29`, ranks `7/40/2/8/44/10`; không dùng test ép generator mới tạo một dãy thắng đã biết.
- Với cùng scorer, giải thích rõ thay đổi chỉ do generator; kiểm tra history không chứa target, feedback không lấy tương lai, tie-break/seed/configuration hash được lưu.
- Không áp filter chẵn/lẻ/tổng/vùng chưa được kiểm định. Không tăng budget trong so sánh hoặc cộng vé trùng giữa mô hình thành bằng chứng độc lập.
- Chạy test domain/service/persistence liên quan và `OnionArchitectureTest`; build riêng Angular và Maven theo workflow khi có sửa code ứng dụng.

## 5. Nguồn, artifact và giới hạn lần phân tích này

[JSON đối chiếu](reports/mega-lab-review-2026-11-15.json) chứa 30 vé của sáu nhóm cùng kỳ, số khớp tính lại, ranking/feature của số kết quả, bằng chứng chốt trước kỳ, lịch sử đánh giá theo cohort, công thức tổ hợp, URL nguồn và SHA-256 các response.

Đọc GET `/api/ai-lab?category=MEGA&page=0` và `page=1`, `/api/numbers`, `/api/lottery-sync/status`, hai draw-report của `lab-logistic_coverage`/`lab-logistic`. Raw response và script tính lại nằm cục bộ tại `tmp/mega-lab-review-2026-11-15/`; JSON báo cáo được giữ trong repo, raw tạm không phải dependency của ứng dụng.

Nguồn trực tiếp: [AI Lab production](https://analyze-project.onrender.com/api/ai-lab?category=MEGA&page=0), [bằng chứng coverage kỳ 15/11](https://analyze-project.onrender.com/api/analyze/draw-report?category=MEGA&date=2026-11-15&algorithm=lab-logistic_coverage), [feed cộng đồng](https://raw.githubusercontent.com/vietvudanh/vietlott-data/main/data/power645.jsonl). API phân trang có thể đổi khi thêm kỳ; dùng artifact đã lưu để đối chiếu lần này.

Đã tính giao tập độc lập cho 30 vé, kiểm tra đủ slot/batch, thời điểm chốt, fingerprint kết quả, ranking và các bộ con. Chưa chạy benchmark ứng viên mới, chưa chứng minh cải thiện xác suất trúng. Chỉ thêm tài liệu/báo cáo, nên kiểm tra JSON, nội dung, liên kết và diff; không chạy lại build ứng dụng.

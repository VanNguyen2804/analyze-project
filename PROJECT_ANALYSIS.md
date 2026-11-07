# Hiện trạng và hướng phát triển dự án

## Triển khai Vietlott và UI — 07/11/2026

Đã triển khai [công cụ nghiên cứu và UI theo kế hoạch](backend/docs/VIETLOTT_RESEARCH_IMPLEMENTATION.md): inventory runtime; benchmark chronological với validation riêng, ablation Logistic/feedback mô phỏng/calibration, shrinkage và coverage generator; hai ứng viên Lab chốt song song có công tắc riêng. Snapshot cũ giữ nguyên, cohort không tự gộp, không promote mô hình theo replay. Lịch sử có lọc ngày/cấu hình và mức ≥3/≥4/≥5; shell responsive, khay chọn vé và review/Lab thống nhất, sao chép POWER sáu số và xử lý clipboard đúng.

Replay fixture 39 kỳ hợp lệ mỗi loại, 15 kỳ outer/loại và ba vé/kỳ chưa có vé ≥4; coverage/feedback chưa cho thấy cải thiện ổn định. API production vẫn timeout nên chưa xác minh dữ liệu live. Angular typecheck/74 test/build đạt; Java 158 test, 153 đạt/năm có điều kiện bỏ qua, gồm năm test PostgreSQL thật; kiểm tra browser dùng fixture riêng. Chưa deploy hosting, thêm XGBoost hoặc thu thập đủ bằng chứng xác nhận tương lai. Chi tiết và JSON tại tài liệu triển khai; kế hoạch dưới đây giữ như mốc lịch sử.

## Kế hoạch dự đoán Vietlott và UI — 07/11/2026

Đã lập [kế hoạch chi tiết](backend/docs/VIETLOTT_PREDICTION_AND_UI_PLAN.md) theo yêu cầu chỉ lập plan trước. Rà soát xác nhận đã có Logistic học thật, bản không feedback, Brier/log loss, đối chứng và portfolio; nhánh `deep_stacking` vẫn là heuristic, chưa phải XGBoost được huấn luyện. Kế hoạch ưu tiên kiểm kê dữ liệu, benchmark theo thời gian/cùng ngân sách, ablation và thử ghép vé riêng với ranking; UI thống nhất shell, luồng chọn/chốt, báo cáo mẫu số/phiên bản và cách sao chép sáu số POWER.

Fixture trong repo vẫn có 39 kỳ hợp lệ mỗi loại; API production hết thời gian chờ trong lần kiểm tra nên chưa xác minh được lượng dữ liệu hoặc thành tích hiện tại. Không suy ra mức cải thiện từ tên thuật toán hoặc số vé đã dò. Chưa sửa code ứng dụng, dữ liệu hoặc triển khai; chỉ kiểm tra tài liệu/liên kết/diff, không chạy lại build ứng dụng.

## Chart production và nhận định ngày — 04/11/2026

Tái hiện lỗi chỉ hiện trục giá trên production bundle với response VIC: hai timestamp 1672099200/1672106400 cùng business date 27/12/2022 khiến renderer Lightweight Charts ném `Value is null`. Adapter và frontend chuẩn hóa một nến/ngày, lấy timestamp muộn nhất, không cộng volume. Regression dùng đúng hai timestamp này và kiểm tra pixel nến/EMA trong pane; đã đối chiếu ảnh production trước/sau trên cùng response thật.

Thêm `StockDailyDesk` và `dailyDesk` trong snapshot: 10 phiên đóng gần nhất, từng phiên chỉ dùng prefix tới ngày đó. Bảng hiện tại/điều kiện mua/điều kiện bán cho RSI14 Wilder, EMA20/50/200, Ichimoku dịch đúng 26 phiên, volume TB20 và Fibonacci biên 60 nến với ngày đáy/đỉnh, giá từng mức. Thống kê sáu điều kiện không phải xác suất thắng. UI chọn phiên, ma trận trạng thái, chú giải thu gọn; chart thêm EMA50, mây tô và khoảng xem 1M/3M/6M, giữ nội dung khi polling cùng mã/mode. Không xác nhận giao dịch từ nguồn chưa verified.

Model version mới `stock-softmax-purged-v2-wilder`, quy tắc `stock-price-scenarios-v2-wilder` vì chuẩn hóa ngày, RSI Wilder và EMA toàn prefix. Báo cáo 03/11 giữ riêng cho phiên bản cũ. [Công thức, giới hạn, chẩn đoán](backend/docs/STOCKS_IMPLEMENTATION.md).

## Khuyến nghị chứng khoán theo giá — 04/11/2026

Thêm `StockRecommendationEngine` thuần domain và `analysis.recommendation`: nến đóng vượt kháng cự 20 nến trước + đệm ATR, xác nhận volume/xu hướng/RSI mới cân nhắc mua; đóng dưới hỗ trợ có đệm ATR gợi ý giảm rủi ro nếu đang giữ. Có diễn giải vượt giả/thiếu volume/RSI cao/mua đuổi, mốc mua/bán và vùng dừng lỗ/chốt lời giả định theo ATR. Volume intraday so cùng giờ của ít nhất năm phiên trước. Không dùng nến chưa đóng, không xác nhận hành động trên nguồn chưa verified hoặc dữ liệu cũ; không biến điểm mô hình thành xác suất lời. Chính sách cấu hình được, chưa có bằng chứng hiệu quả giao dịch.

Component `stock-recommendation` trình bày lời khuyên theo điều kiện và giá cụ thể; điểm % của học máy đưa xuống details thu gọn, phân biệt với tỷ lệ vốn mua/bán. Vị thế dùng action kỹ thuật mới, giữ tách biệt phép tính P&L. [Phương pháp và cấu hình](backend/docs/STOCKS_IMPLEMENTATION.md).

Kiểm chứng: Angular build/Maven install đạt; 56 kiểm thử Angular và 118 kiểm thử Java đạt, tám kiểm thử có điều kiện bỏ qua. Typecheck, định dạng frontend sửa và 24 assets trong JAR đạt. Chưa đo hiệu quả giao dịch của quy tắc mới hoặc triển khai hosting.

## Sửa trang chứng khoán 04/11/2026

Tách tính lãi/lỗ theo giá khỏi cờ `verified` của nguồn: với đơn vị VND hoặc nghìn VND rõ ràng, vị thế trả giá dùng tính, phần trăm và số tiền chênh lệch; cảnh báo khi cơ sở điều chỉnh giá chưa xác nhận hoặc dữ liệu cũ, không mở tín hiệu mua/bán bằng cách bật cờ giả. Không dùng giá trước ngày mua, chuỗi ADJUSTED hoặc đơn vị chưa quy đổi được. Adapter mặc định quy đổi giá nến cổ phiếu ×1.000; cấu hình cũ scale=1 mang đơn vị `THOUSAND_VND`, quy đổi một lần trong phép tính vị thế. VNINDEX giữ đơn vị điểm.

Bỏ phí mua/chi phí bán khỏi form và request frontend; bỏ kết quả lãi/lỗ ròng/hòa vốn khỏi màn hình. Sửa chart host thành block, khởi tạo khi vùng chứa có kích thước, resize qua requestAnimationFrame để tránh vòng lặp ResizeObserver; giải phóng observer/frame khi rời trang. Ngày nến được tạo theo ISO từ formatToParts ở giờ Việt Nam thay vì phụ thuộc cách locale `en-CA` định dạng chuỗi. Có kiểm thử vùng biểu đồ từ ẩn sang hiện và tình huống giá mua 234.000 VND × 200 cổ phiếu như ảnh người dùng.

Kiểm chứng: Angular build/Maven install đạt; 54 kiểm thử Angular và 113 kiểm thử Java đạt, tám kiểm thử Java có điều kiện bỏ qua. Typecheck và định dạng frontend sửa đạt; 24 assets frontend khớp byte trong JAR. Chưa triển khai hosting hoặc chạy PostgreSQL thật trong lần sửa này.

## AI Lab 03/11/2026 — Học phản hồi từ vé khớp 1–2 số

Theo yêu cầu đổi cả cách học, Logistic mới mang phiên bản `ai-lab-dense-feedback-v2`: sau huấn luyện lịch sử, replay tối đa 60 kỳ chốt thật có đủ batch/đặc trưng đóng băng và kết quả còn hợp lệ. Mỗi kỳ có một nhãn đúng/sai cho mỗi số; số được chọn trên nhiều vé không tạo cập nhật lặp. Mọi mức khớp 0–6 tham gia; số từng được chọn có thêm trọng số mất mát 0,25, bước gradient 0,05. Đối soát lại từ dữ liệu hiện tại, giới hạn trước kỳ đích; không phụ thuộc settlement cũ khi kết quả bị sửa/rút lại. Đóng băng policy, trọng số và lineage/hash trong explanation; không sửa snapshot cũ, migration, mô hình tần suất hoặc thuật toán chính. Clock được inject vào AiLabService để kiểm thử cutoff có thể tái lập.

Thêm `AiLabProgress` tính trung bình, tổng lượt khớp, histogram đúng 0–6, các mức ≥1/≥2/≥3 và timeline chỉ từ kỳ có đủ năm vé đã đối soát; trả cùng cohort cũ để giữ phân tách phiên bản. Component `lab-progress` hiển thị mức ≥1 mặc định và cho đổi mức; mức cao nhất đã quan sát không phải trạng thái ứng viên. Cổng sàng lọc ≥3/60 kỳ giữ nguyên. [Chi tiết phương pháp và cấu hình](backend/docs/AI_LAB_PROGRESSIVE_LEARNING.md). Chưa có bằng chứng cải thiện xác suất trúng; tổng lượt khớp không phải một vé ghép từ nhiều kỳ.

Kiểm chứng: Angular build, Maven clean install/install, typecheck và định dạng frontend thay đổi đạt; 52 kiểm thử Angular và 110 kiểm thử Java đạt, tám kiểm thử Java có điều kiện bỏ qua. 24 assets Angular khớp byte trong JAR. Không chạy PostgreSQL thật, không sửa dữ liệu production hoặc triển khai hosting.

## Trang chứng khoán 03/11/2026

Thêm lazy route `/stocks` và `/stocks/:symbol`, menu chứng khoán; ẩn bộ chọn MEGA/POWER và lịch xổ số theo route, giữ các trang xổ số hiện có. Frontend có watchlist, biểu đồ Lightweight Charts 5.2.0, hai mode, form vị thế theo snapshot; không lưu/log vị thế. Backend theo Onion: `StockMarketDataPort`, adapter Entrade, `StockService`, các record stock và `StockController`; cache RAM, không migration hoặc lưu dữ liệu thị trường bền khi chưa xác minh quyền dùng.

Mô hình `stock-softmax-purged-v1` là logistic regression đa lớp thực sự học trọng số trên OHLCV, tách theo mã/mode; ba đoạn kiểm định mở rộng, purge nhãn tương lai, scaler fit trên train, baseline lớp phổ biến. Chỉ xét tín hiệu mua/bán khi kiểm định và chất lượng dữ liệu đạt; mặc định nguồn chưa verified nên khóa hành động/P&L VND. Báo cáo dữ liệu thật ngày 03/11: bốn mã có 1.246 nến ngày, FPT 672 nến 15 phút; cả bốn SWING và FPT INTRADAY chưa vượt điều kiện kiểm định. Không có bằng chứng hiệu quả giao dịch hoặc lợi nhuận thực thi. Các metric là kết quả khảo sát dữ liệu cơ sở giá chưa xác minh.

Chi tiết cấu hình, API, công thức, giới hạn và phần chưa triển khai của kế hoạch gốc tại [STOCKS_IMPLEMENTATION.md](backend/docs/STOCKS_IMPLEMENTATION.md). Bản đầu chưa có sự kiện quyền/lịch sàn hoàn chỉnh, lưu model/ledger, hoặc backtest lợi nhuận sau thanh toán/khớp lệnh; không coi đã hoàn tất mọi giai đoạn kế hoạch.

## Triển khai mã 02/11/2026 — Sửa bốn phần scoring

Đã thực hiện bốn mục rà soát theo yêu cầu người dùng. API patch cấu hình trả HTTP 400 với ba khóa không còn được scorer dùng (`poissonGapMinRatio`, `poissonGapMaxRatio`, `repeatExhaustionPenalty`), nhưng giữ nguyên cấu hình lịch sử khi merge một patch hợp lệ. Thêm tham số graph có validation: `pairGraphPriorStrength` mặc định 8 và `pairGraphMinSupport` mặc định 3; `coOccurrenceWeight` hiện thực sự điều khiển thành phần graph của Bayes.

`DrawTransitionModel` ở domain chỉ học các cặp kỳ liên tiếp theo lịch, dùng số lần nguồn có đích hợp lệ làm mẫu số và không dùng context cũ qua khoảng thiếu. `TemporalRelationshipModel` phiên bản `temporal-links-v3` bỏ expert `special_to_main` khỏi trọng số, cập nhật thành tích và explanation của MEGA; POWER giữ chín expert. `PairRelationshipModel` đo residual đồng xuất hiện so với mức tham chiếu có hiệu chỉnh chọn sáu số không hoàn lại, giới hạn theo marginal, ngưỡng support và làm trơn; thay graph degree đếm lại tần suất. Đây là heuristic, mức tham chiếu chỉ chính xác ở trường hợp đồng đều, chưa phải mô hình đã xác nhận khả năng dự đoán.

Scorer Bayes mang phiên bản `bayesian-pair-excess-v2`, deep stacking `copula-scheduled-v2`; cả lượt chính/phụ và workspace lưu đúng phiên bản. Fingerprint bao gồm helper mới; explanation đóng băng diagnostics chuyển tiếp. Không thay snapshot cũ, migration, UI hoặc dependencies. Công thức, cấu hình và giới hạn tại [scoring](backend/docs/ALGORITHM_SCORING.md).

Kiểm chứng: Angular build và Maven `clean install` đạt; 100 kiểm thử chạy đạt, gồm bốn kiểm thử PostgreSQL thật và phát lại phát triển, hai báo cáo có điều kiện cũ được bỏ qua. 23 assets Angular khớp byte trong JAR. Server PostgreSQL kiểm thử đã tắt; chưa triển khai hoặc sửa production.

Phát lại code cũ/mới trên cùng byte dữ liệu, 29 kỳ mỗi loại sau mười kỳ khởi tạo, một vé/thuật toán/kỳ: temporal MEGA 0.759 → 0.724 số khớp/vé; Bayes MEGA 0.966 → 0.862, POWER 0.966 → 0.724; deep stacking POWER 0.897 → 0.966. Temporal POWER và deep stacking MEGA giữ số khớp TB. Không có cải thiện đồng đều; không chọn lại tham số theo báo cáo. Đây là lịch sử phát triển đến 25/10, không phải đánh giá production hiện tại hoặc bằng chứng tăng xác suất trúng. Xem [so sánh đầy đủ và dữ liệu tái lập](backend/docs/reports/algorithm-revision-2026-11-02.md).

## Rà soát thuật toán 02/11/2026 — Các cập nhật nên ưu tiên

Đối chiếu code sau refactor Onion và các báo cáo phát triển 26–28/10, cùng đối chiếu kỳ MEGA 01/11. Chưa thay thuật toán, tham số, UI hoặc dữ liệu; chưa chạy backtest mới hoặc đọc lại metric production. Các kết quả lịch sử bên dưới chỉ là snapshot đã có.

- **Hợp đồng cấu hình:** `PredictionParameters` vẫn chấp nhận `poissonGapMinRatio`, `poissonGapMaxRatio`, `repeatExhaustionPenalty`, nhưng `AnalyzeService` chỉ khai báo/đọc các giá trị này, không dùng chúng để tính điểm hoặc chọn vé. Cần loại khỏi danh sách patch có hiệu lực hoặc thiết kế lại cách dùng có kiểm chứng; không tự nối các hệ số cũ vào công thức rồi coi là cải thiện.
- **Liên kết qua kỳ thiếu:** `TemporalRelationshipModel` đã kiểm tra liên tiếp theo lịch. Khối dựng `transitionMatrix` trong `AnalyzeService` vẫn nối hai phần tử liền nhau của history mà không kiểm tra lịch; ma trận này được nhánh heuristic dùng để tính điểm Markov. Cần áp dụng cùng quy tắc loại chuyển tiếp qua khoảng thiếu và thêm regression test. Đây là rủi ro khi history thiếu kỳ, không phải bằng chứng dữ liệu hiện tại đang thiếu.
- **Thành phần trùng trong MEGA:** MEGA không có số phụ, nhưng temporal vẫn phân trọng số cho `special_to_main`. Thành phần này trả lại marginal frequency, trùng với expert frequency. Cần thử loại expert không áp dụng theo category và chuẩn hóa trọng số trên các expert hoạt động; lưu phiên bản mô hình mới. Việc bỏ trùng chưa chứng minh tăng số khớp.
- **Điểm graph chưa đo liên kết độc lập:** với các kỳ hợp lệ đúng sáu số, cách cộng `pairMatrix` hiện tại khiến tổng hàng bằng `5 * frequency[number]`. Vì vậy `coOccurSum` trong nhánh Bayes chỉ là biến đổi của tần suất; sau ngưỡng cap còn có thể bão hòa. Không suy rộng kết luận này sang mọi phép dùng ma trận cặp hoặc Jaccard. Một ứng viên mới có thể đo phần đồng xuất hiện vượt kỳ vọng khi lấy sáu số không hoàn lại, có làm trơn và ngưỡng support; cần đánh giá trước khi thay mặc định.
- **Thực nghiệm:** giữ mô hình cũ làm đối chứng, tách MEGA/POWER, đo ranking riêng với phân bổ vé, dùng cùng kỳ/cùng ngân sách và nhiều seed đối chứng đã định trước. Chọn cấu hình trên dữ liệu phát triển theo walk-forward, đóng băng ứng viên rồi xác nhận trên các kỳ tương lai mới. Báo cáo 28/10 thử trọng số 0,25/0,5/0,75 chưa cho lợi thế ổn định; không dùng lại nó làm tập giữ kín. Chưa có cơ sở đổi trọng số hoặc thêm mô hình phức tạp chỉ từ một kỳ thua.

Thứ tự đề xuất: sửa hợp đồng cấu hình và xử lý khoảng thiếu trước; sau đó thử một phiên bản temporal không có expert trùng, rồi mới nghiên cứu lại điểm graph. Kiểm chứng đợt rà soát này là đọc code, đối chiếu công thức và báo cáo hiện có; chỉ cập nhật tài liệu nên không chạy build ứng dụng.

## Refactor 02/11/2026 — Backend theo Onion Architecture

Tách backend thành `domain`, `application`, `infrastructure`, `presentation`. Domain chứa mô hình thuần Java và các quy tắc chọn lịch sử, quan hệ theo thời gian, rank fusion/danh mục vé; không phụ thuộc Spring/JPA/Jackson. Application điều phối use case qua repository/store ports thuần Java, không phụ thuộc implementation database hoặc HTTP. JPA entities được tách riêng và ánh xạ hai chiều, giữ ID/timestamp, thứ tự số và toàn bộ audit JSON; collection được sao chép để tránh sửa entity qua domain. Spring Data và JDBC implements các ports; cấu hình lắp ghép tại `infrastructure/config/ApplicationConfig.java`.

Scheduler, cấu hình database, HTTP feed và transaction nằm ở infrastructure. Decorator nhập kết quả giữ advisory lock trong cùng transaction; decorator báo cáo giữ `REPEATABLE_READ`/read-only. `ConflictException` ở application được controller workspace chuyển thành HTTP 409 với `message` cũ. Trạng thái đồng bộ được đọc qua input port, controller không phụ thuộc scheduler cụ thể. API, thuật toán, migration và snapshot lịch sử giữ nguyên. Package mới làm đổi fingerprint bytecode của các snapshot tạo sau refactor; cohort phiên bản tiếp tục tách riêng, không sửa hoặc sinh bù dữ liệu cũ.

Kiểm thử bổ sung kiểm tra phụ thuộc trên bytecode, roundtrip mọi trường domain/entity cùng audit lồng nhau, HTTP 409 và wiring/transaction với PostgreSQL thật. Kiểm tra database tạo schema ngẫu nhiên từ DDL cũ, không nhập seed và dùng Hibernate `validate`; kiểm tra bốn JPA adapters, nhập idempotent và rollback. Bản đồ package, quy tắc mở rộng và cách chạy tại [kiến trúc backend](backend/docs/ONION_ARCHITECTURE.md).

Kiểm chứng: Angular production build đạt với Node 24.15.0; Maven `clean install` đạt, phát hiện 88 kiểm thử, 86 chạy đạt và hai báo cáo phát triển có điều kiện được bỏ qua. Cả bốn kiểm thử PostgreSQL đã chạy trên PostgreSQL 16.4 tạm, lắng nghe riêng localhost; các schema test đã được dọn và server tạm đã tắt. 23 assets Angular khớp SHA-256 trong JAR, không còn package backend cũ trong artifact. Còn cảnh báo CSS AI Lab vượt budget khuyến nghị và unchecked Java đã có. Không thay dependency ứng dụng hoặc dữ liệu production.

## Sửa 01/11/2026 — Vé thử nghiệm là vé bổ sung, không lặp bộ gốc

Nguyên nhân trùng bộ `09 15 22 27 40 41`: v1 chủ động giữ vé top sáu làm vé thử nghiệm đầu tiên. Theo yêu cầu người dùng, đổi chính sách sang `mega-portfolio-v2`: lấy ba vé từ danh mục giảm trùng sau khi loại ba gợi ý gốc của scorer và vé forecast/AI Lab hợp lệ đã chốt cho cùng MEGA/kỳ. So sánh bộ sáu số đã chuẩn hóa, độc lập thứ tự nhập; giữ tối đa ba số trùng giữa hai vé thử nghiệm. Danh sách bộ bị loại được lưu và kiểm tra hash cùng snapshot. Không sửa ranking/scoring hoặc chọn tham số/seed theo kết quả kỳ 01/11.

Migration mới V15 cho phép một bản chốt mỗi kỳ/phiên bản. V1 được giữ nguyên byte JSON, đọc/đối soát và thống kê riêng; v2 có thể chốt cho kỳ tương lai đã có v1, vẫn phải đủ lịch sử và trước cutoff. Không sinh bù v2 cho kỳ đã quay. API chỉ chọn v2 cho bộ bổ sung hiện tại; lịch sử giữ mọi phiên bản và trả nhãn phiên bản rõ ràng.

UI nhận context từ chính gợi ý đang hiển thị, gồm hai thuật toán, bộ kết hợp đã chỉnh và Lab. Chưa có context đúng kỳ thì chờ; nếu bộ thử nghiệm trùng gợi ý hiện tại, ẩn và chặn sao chép kể cả trước lượt render kế tiếp. Không tự tạo vé thay thế hoặc sửa bộ đã chốt; thông báo số bộ bị ẩn, tính số vé/độ phủ theo phần đang hiển thị, còn bảng đánh giá và lịch sử dùng đủ bộ đã lưu. Vì người dùng có thể đổi bộ kết hợp sau khi chốt, số vé bổ sung hiển thị có thể ít hơn ba.

Kiểm chứng: format/typecheck đạt; 46 kiểm thử Edge headless đạt. Angular production build và Maven `clean install` đạt; 83 kiểm thử Java được phát hiện, 81 chạy đạt, hai báo cáo phát triển có điều kiện không chạy. Kiểm thử PostgreSQL thật xác nhận nâng V14 → V15 giữ JSON v1, hai phiên bản cùng kỳ, cạnh tranh/retry, rollback và đối soát sửa/cách ly. 23 assets Angular khớp byte trong JAR và có migration V15. Còn cảnh báo CSS AI Lab/unchecked Java đã có.

Smoke JAR/schema tạm dùng bản sao 84 kết quả đủ điều kiện từ bản xuất production ngày 01/11. V2 chốt ba vé cho 04/11, không trùng bất kỳ bộ nào trong 13 snapshot mô hình/Lab MEGA cùng kỳ đã tạo cục bộ. Browser 1440/390 px xác nhận vé đúng snapshot, không tràn ngang trên di động; ép bộ kết hợp trùng một vé thử nghiệm thì UI còn hai vé và thông báo, bỏ chỉnh sửa hiện lại ba vé, snapshot không đổi, đổi POWER ẩn vùng thử nghiệm. Đây là kiểm chứng cục bộ; chưa triển khai bản sửa lên Render hoặc thay dữ liệu production.

## Đối chiếu kết quả MEGA 01/11/2026 — Thử nghiệm chưa cải thiện kỳ đầu

Đọc chỉ đọc production xác nhận kết quả `05 09 17 18 34 36` đã đồng bộ và `mega-portfolio-v1` hiện đã được triển khai, chốt kỳ 01/11 lúc 17:43 giờ Việt Nam. Ba vé thử nghiệm khớp `1, 1, 0`; cách ghép cũ cùng ba vé khớp `1, 2, 1`; tần suất + giảm trùng `0, 1, 1`; ngẫu nhiên `0, 0, 1`. Thử nghiệm phủ 18 số đã chọn nhưng chỉ có hai số kết quả, bằng độ phủ kết quả của wheel cũ dùng chín số. Kỳ này giảm trùng kém wheel về TB/vé và vé tốt nhất; chưa có cơ sở kết luận dài hạn từ một kỳ.

Bảy lượt dò mới id 90–96 gồm sáu bộ khác nhau vì id 90/94 trùng nhau; chưa xác định mua trùng hay dò lại. Toàn bộ dùng 22 số khác nhau, chỉ phủ `09 36`, không có vé từ ba số. Snapshot mô hình gốc trước kỳ xếp `05/17/18/34` ở hạng `41/35/34/40`; đây là quan sát, không suy ra nên ưu tiên số gan. Giảm trùng chỉ đổi cách phân bổ, chưa nâng chất lượng ranking. AI Lab kỳ này cũng không có vé từ ba số ở cả hai nhóm năm vé.

Giữ nguyên thuật toán và cấu hình; đánh giá có thể tiếp tục tự động bằng snapshot, không cần người dùng mua vé để kiểm chứng. Phương pháp, bảy bộ số, đối chứng và dấu kiểm chứng tại [đối chiếu MEGA 01/11](backend/docs/reports/mega-review-2026-11-01.md). Chỉ bổ sung tài liệu, không sửa production hoặc chạy build ứng dụng.

## Triển khai mã 01/11/2026 — Thử nghiệm MEGA giảm trùng vé trên kỳ tương lai

Theo yêu cầu người dùng, thêm vùng **Giảm trùng vé** riêng trong `/analyze`, chỉ hiện khi chọn MEGA. `MegaPortfolioService` / `MegaPortfolioProtocol` dùng ranking `temporal_learning` với tham số mặc định cố định, cùng cách chọn `rank-portfolio-v1` đã phát lại ngày 27/10. Mỗi kỳ chốt ba vé thử nghiệm; mỗi cặp trùng tối đa ba số. Ba nhóm đối chứng cùng ngân sách gồm ba vé đầu của wheel cũ từ cùng ranking, tần suất + giảm trùng và ngẫu nhiên đều có seed cố định theo phiên bản/loại/kỳ. Không đổi scoring, trọng số mặc định hoặc mô hình AI Lab hiện có.

Migration mới V14 tạo hai bảng ledger riêng, lưu nguyên khối mọi nhóm cùng ranking, cấu hình, history/hash và phiên bản code. Ghi bản đầu tiên bất biến theo kỳ; transaction/advisory lock xử lý retry/cạnh tranh giữa instance. Chỉ chốt kỳ mới trước 18:00 giờ Việt Nam, cần ít nhất mười kỳ và kết quả kỳ liền trước; kiểm tra lại cutoff sau tính toán. Scheduler chạy mỗi phút, đối soát từ 19:00 và giữ audit khi kết quả sửa/cách ly. `MEGA_PORTFOLIO_ENABLED=false` tắt chốt mới, vẫn đối soát cũ. GET `/api/mega-portfolio?page=0` chỉ đọc; POST `/api/mega-portfolio/run` chủ động chốt/đối soát.

UI hiển thị ba vé thực đã lưu và sao chép đúng bộ số; các nhóm đối chứng có thể mở xem. Báo cáo tách theo phiên bản code/chính sách, dùng cùng các kỳ đã đối soát cho mọi nhóm, gồm số kỳ có vé ≥3, số khớp TB/vé và độ phủ số kết quả khác nhau. Seed đổi theo kỳ không làm tách nhóm chính sách. Kỳ chưa có kết quả không có tỷ lệ giả; kết quả không hợp lệ bị loại khỏi thống kê. Lịch sử phân trang mười kỳ có số khớp từng vé. Component hủy request/timer khi đổi sang POWER; tải trang không chốt thử nghiệm qua GET.

Kiểm chứng: typecheck và format các file frontend thay đổi đạt; 45 kiểm thử Edge headless đạt. Angular production build và Maven `clean install` đạt, 80 kiểm thử Java được phát hiện, 78 chạy đạt và hai báo cáo phát triển có điều kiện không chạy. Ba kiểm tra PostgreSQL thực tế chạy trong các schema tạm, gồm kiểm tra mới cho V14, cạnh tranh chốt, retry, rollback, đọc bền vững và đối soát sửa/cách ly. 23 assets Angular cùng migration V14 có trong JAR và assets khớp byte với bản build. Còn cảnh báo stylesheet AI Lab vượt mức khuyến nghị và unchecked Java đã có.

Smoke JAR trên schema tạm riêng, dùng bản sao 41 kỳ MEGA từ feed cộng đồng đến 30/10, xác nhận chốt đúng bốn nhóm ba vé cho kỳ 01/11 và retry giữ nguyên snapshot. Trình duyệt 1440/390 px hiển thị đúng vé từ snapshot, đủ đối chứng, không lỗi runtime, không tràn ngang trên điện thoại; chuyển POWER ẩn vùng thử nghiệm. Đây là kiểm chứng cục bộ, không phải snapshot production hoặc đánh giá tăng khả năng trúng. Chưa triển khai Render và chưa thay dữ liệu/cấu hình production; thử nghiệm tương lai sẽ bắt đầu ghi sau khi triển khai.

## Kiểm tra 01/11/2026 — Vé thu thập và khả năng cập nhật thuật toán

Đọc chỉ đọc API production `/api/analyze/user-history`, `/api/numbers` và `/api/prediction-workspace/history` cho cả hai loại. Có 43 vé đã dò: 16 MEGA và 27 POWER; sau kiểm tra sáu số nguyên khác nhau trong miền và loại trùng theo loại/ngày/bộ số, còn 41 vé hợp lệ trên 12 cặp loại/ngày. Hai vé sai cũ id 29 và 36 vẫn được giữ để audit. Đối chiếu lại bộ số với kết quả `SOURCE_MATCHED`: 18 vé khớp 0, 16 vé khớp 1, năm vé khớp 2 và hai vé khớp 3; không có vé khớp từ bốn số. Trạng thái nguồn là khớp feed cộng đồng, không phải xác minh độc lập với Vietlott.

Riêng các ngày quay 25–31/10 có 18 vé hợp lệ trên năm cặp loại/ngày. MEGA 30/10: ba vé id 84–86 khớp lần lượt 3, 2, 1; vé 84 `06 15 22 27 40 41` khớp `22 40 41`. POWER 31/10: ba vé id 87–89 đều khớp một số chính (`18`). Không diễn giải một vé khớp ba số thành bằng chứng thuật toán cải thiện.

Workspace cả MEGA và POWER trả `batches: []`. `UserTicket` vẫn chỉ lưu thông tin dò vé, không có nguồn thuật toán hoặc bản chốt trước kỳ. Code scoring không dùng vé này để học/chỉnh trọng số; phần `verifiedWinningTickets` chỉ bổ sung đối chiếu và liên kết lịch sử vào báo cáo. Việc học từ lịch sử kết quả là luồng riêng. Chưa đủ bằng chứng để đổi thuật toán/trọng số mặc định từ mẫu vé đã dò; các phát lại giảm trùng và kết hợp 27–28/10 vẫn là báo cáo phát triển trên dữ liệu cũ, không phải metric cập nhật đến 01/11.

Làm rõ phạm vi sau khi kiểm tra thêm `/api/ai-lab`: workspace rỗng không có nghĩa hệ thống thiếu dự đoán chốt trước kỳ. AI Lab đã lưu riêng nguồn mô hình, phiên bản/cấu hình, năm vé/mô hình/kỳ và hai đối chứng tần suất/ngẫu nhiên cùng ngân sách; tự đối soát và báo cáo theo nhóm phiên bản. API hiện có 30 vé MEGA (ba kỳ, hai kỳ đã đánh giá cho mỗi mô hình) và 40 vé POWER (bốn kỳ, ba kỳ đã đánh giá cho mỗi mô hình). Recency MEGA 30/10 có một trong năm vé khớp từ ba số; các nhóm Logistic đã đánh giá và Recency POWER chưa có vé đạt ngưỡng này. Tất cả nhóm ở trạng thái `COLLECTING`; mỗi nhóm phiên bản hiện chỉ có 0–2 kỳ đã đánh giá. Quy tắc sàng lọc hiện yêu cầu ít nhất 60 kỳ với các điều kiện bổ sung; nhãn ứng viên không tự thay thuật toán chính. Bước tiếp theo cho đánh giá mô hình là sử dụng và theo dõi ledger AI Lab đã có; thiếu nguồn/chốt trước kỳ chỉ áp dụng cho lịch sử dò vé người chơi, không áp dụng cho AI Lab.

Không sửa code ứng dụng, cấu hình hoặc dữ liệu production. Kiểm chứng bằng GET API, tính giao tập số bằng Python và đối chiếu đường đọc repository trong code; không chạy build vì chỉ bổ sung tài liệu, không gọi đây là kiểm thử PostgreSQL trực tiếp.

## Triển khai 29/10/2026 — Báo cáo theo snapshot và dò vé thống nhất

Thêm `DrawAnalysisService`, DTO `DrawReport` Java/TypeScript, GET `/api/analyze/draw-report` và POST `/api/analyze/evaluate-tickets` chỉ tính, không lưu. Báo cáo không phụ thuộc dịch vụ scoring hoặc cấu hình active; chỉ đọc kết quả đủ chất lượng/đúng mốc 19:00, snapshot trước cutoff 18:00, đúng loại/kỳ/thuật toán và lịch sử đến kỳ liền trước. Snapshot cũ không có điểm/ranking giữ trạng thái thiếu; không dựng lại bằng code hiện tại. API `official-draw-analysis` giữ adapter và công thức giải thích chung cũ đã bị bỏ.

`TicketAnalysis` thống nhất số khớp/giải/độ phủ, loại vé trùng khi tính nhóm và giữ vé sai cũ để audit. POWER xét số phụ trong sáu số trên vé, không dựa vào một số phụ do người dùng đoán riêng. Báo cáo giữ tách nhóm vé đã dò, batch chốt có hiệu lực và gợi ý; đối chiếu bản gốc–bản cuối chỉ trên các cặp có nguồn. Thống kê nhiều kỳ dùng snapshot đủ điều kiện hoặc batch user có hiệu lực, tách ngân sách/phiên bản và đối chứng ghép cặp cùng số vé; Lab cần đủ năm slot hợp lệ, không chọn vé tốt nhất sau kỳ.

Snapshot mới của ledger/workspace/Lab lưu chi tiết từ chính scoring: toàn ranking, score, thành phần đầu vào có sẵn, trọng số expert/Logistic khi có, chính sách lấy mẫu và cấu hình giữ/bỏ của bộ kết hợp. Không thay số được chọn hoặc thuật toán, không backfill snapshot cũ. Payload JSON mở rộng không cần migration mới; các cohort/code version cũ không bị viết lại. API cập nhật tham số chặn báo cáo/khóa không được scorer đọc và merge patch hợp lệ vào cấu hình hiện có.

Sửa routing theo code thực tế: `/analyze` vẫn chọn vé; thêm tab `/analyze/review`, chuyển `/latest-analysis` sang tab mới. Component mới đồng bộ context và hủy request cũ; dò thử không ghi, xuất báo cáo không kích hoạt cấu hình, chi tiết số giải thích theo snapshot được chọn. Trang chọn vé hiển thị số khác nhau và mức trùng lớn nhất trước khi chốt. Badge menu chuyển từ backtest sang API kết quả đơn thuần. Giữ layout shell và không hiển thị thông tin database trên giao diện.

Kiểm chứng: typecheck/Prettier cho các file thay đổi, 42 kiểm thử Edge headless và build Angular đạt. Maven `clean install` rồi `install` với bản cuối đạt; 70 kiểm thử Java được phát hiện, 68 chạy đạt và hai báo cáo phát triển có điều kiện không chạy. Hai kiểm tra PostgreSQL thực tế chạy trên schema tạm, gồm đọc JSON cũ/mới, luồng chỉ đọc không tạo bản ghi, revision/retry/rollback. 23 assets Angular trong JAR và static fallback khớp byte. Còn cảnh báo CSS AI Lab vượt mức khuyến nghị đã có và unchecked Java.

Smoke trên JAR và PostgreSQL tạm xác nhận số liệu năm vé 29/10, trình duyệt 1440/390 px không tràn ngang, không lỗi runtime và dò thử qua endpoint không ghi. Fixture lấy từ bản xuất API cục bộ; các snapshot dùng để kiểm tra tích hợp được gắn code version `synthetic-local-smoke-fixture`, không được coi là phục hồi bằng chứng production. Lượt đo cục bộ 20 request/endpoint: badge backtest cũ median/P95 282,65/335,26 ms, API kết quả thay thế 62,06/81,10 ms, báo cáo mới 101,81/114,27 ms. Đây không phải benchmark Render hoặc bằng chứng tăng khả năng trúng.

Chưa triển khai Render, chưa thay dữ liệu/cấu hình production. Theo dõi lợi thế mô hình/giảm trùng vẫn cần kỳ tương lai theo chính sách chốt trước; không chuyển mô hình hoặc trọng số mặc định dựa vào kỳ 29/10. Tham chiếu: [kế hoạch sửa](backend/docs/ANALYZE_REPAIR_PLAN.md).

Kiểm tra lại bản JAR cuối: các request từ trình duyệt chỉ gồm catalog, kết quả, báo cáo và POST dò thử; không có backtest hoặc tạo suggestion khi chỉ xem trang. Số bản ghi snapshot/batch/vé/cấu hình không đổi sau đọc và dò thử. Một request tạo gợi ý chủ động riêng trên schema tạm cho kỳ 31/10 trả bộ chính `07 11 14 18 25 27`; JDBC xác nhận lưu đủ 55 chi tiết mỗi thuật toán, chín thành phần expert và chín trọng số. API thật trả 400 cho vé thập phân. Các xác nhận này không tác động database production và không backfill kỳ 29/10.

## Kế hoạch 29/10/2026 — Sửa tính đúng đắn của trang /analyze

Đã lập [kế hoạch sửa /analyze](backend/docs/ANALYZE_REPAIR_PLAN.md), chưa triển khai code. Ưu tiên luồng đọc không ghi, truy đúng snapshot/kỳ/thuật toán, giải thích từ dữ liệu đã lưu và thống kê từng vé/nhóm thống nhất; sau đó đồng bộ state Angular, tách báo cáo khỏi áp dụng cấu hình và đánh giá cùng ngân sách trên kỳ tương lai. Rà code bổ sung thấy `loadRecommendations` gọi POST tạo suggestion khi đọc trang; component đối soát chỉ reload theo category và luồng cha chưa hủy request theo context. Nguy cơ response cũ ghi đè chưa được tái hiện bằng browser trong lần lập plan. Không suy từ việc sửa trang ra tăng xác suất trúng, không thay mặc định thuật toán.

## Kiểm tra POWER 29/10/2026 — Vé tập trung và giải thích chưa bám thuật toán

Đọc API production: năm vé mới khớp lần lượt 0, 1, 0, 1, 2 số; phủ ba số kết quả khác nhau trên 19 số đã chọn. Ba vé đầu chỉ phủ chín số, hai vé trùng 5/6. Snapshot trước kỳ của `bayesian_graph` và `deep_stacking` trùng nhau; vé tốt nhất trùng Logistic AI Lab đã lưu từ 27/10, chưa đủ bằng chứng chọn mô hình thắng. Workspace POWER chưa có batch chốt.

Xác nhận bằng API và code: `official-draw-analysis` trả cùng điểm/lý do cho ba thuật toán, chỉ đổi tên; công thức giải thích độc lập với ranking/snapshot dự đoán. API thiếu `allNumberDetails` mà frontend dùng cho số trượt. Kết quả mới đã đồng bộ từ feed cộng đồng; xung đột scheduler thuộc bản ghi cũ 28/09 cách ly. Chưa sửa ứng dụng, cấu hình hoặc production. Chi tiết và dấu vết kiểm chứng tại [đối chiếu POWER 29/10](backend/docs/reports/power-review-2026-10-29.md).

## Triển khai 28/10/2026 — Trang dự đoán có người dùng chọn và chốt vé

Thêm `WorkspaceService`, `WorkspaceProtocol`, `WorkspaceStore` và `/api/prediction-workspace`. Migration V13 tạo bảng gợi ý bất biến, các revision vé người dùng và sự kiện đối soát; giữ riêng ledger tự động cũ và AI Lab. Trang dự đoán tạo gợi ý qua API mới, không gọi luồng tự lưu vé chính của `ForecastService.predict` khi mở trang.

Giao diện giữ hai thuật toán và bộ kết hợp, cho chỉnh trọng số/giữ/bỏ số trước khi chọn vé; thêm 3–5 gợi ý AI Lab đúng loại/kỳ theo chính sách luân phiên mô hình/slot có phiên bản. Vé trùng từ nhiều mô hình giữ đủ nguồn. Chỉ nhận batch đầy đủ, cùng phiên bản/cấu hình và lịch sử đủ mới; thiếu Lab không chặn tự nhập. Bản nháp lưu trong trình duyệt theo loại, giữ bản gợi ý gốc khi tải lại. Đổi bộ gợi ý dùng thao tác bỏ nháp rõ ràng để tránh gán nguồn mới cho vé đã chọn.

Người dùng sửa hoặc tự nhập tối đa 25 vé; backend kiểm tra sáu số nguyên khác nhau trong miền hợp lệ và loại vé trùng. Chốt danh sách nguyên khối trước 18:00 giờ Việt Nam, chống request trùng bằng request key, dùng revision kỳ vọng để phát hiện hai tab chỉnh đồng thời. Các lần sửa trước cutoff tạo revision mới; bản mới nhất có hiệu lực, bản cũ vẫn giữ audit. Mỗi vé giữ nguồn, số gốc, số cuối và dấu đã chỉnh; chốt trong ứng dụng không chứng minh mua vé.

Đối soát riêng vé người dùng, vé gốc tương ứng và gợi ý thuật toán/Lab. Đối chứng tần suất và ngẫu nhiên dùng cùng số vé người dùng chốt, seed cố định theo loại/ngày/ngân sách. Độ phủ dùng hợp các số khớp, không cộng trùng giữa vé. Kết quả được sửa/cách ly sẽ tạo sự kiện đối soát mới hoặc vô hiệu hóa; GET lịch sử chỉ đọc. Giao diện hiển thị các bản chốt và kết quả ngay trong khu vực chọn vé; tab lịch sử cũ tiếp tục phục vụ ledger tự động.

Chặn chốt gợi ý cho kỳ tiếp theo nếu chưa có kết quả kỳ liền trước, gồm khoảng 18:00–19:00; giữ cutoff hiện hành. Tách fingerprint AI Lab khỏi class scoring của trang dự đoán. Dò vé cũ cũng chặn input sai trước khi ghi; dữ liệu cũ không bị sửa/xóa.

Phát lại phát triển trên bản xuất API ngày 28/10 có 40 kỳ hợp lệ mỗi loại, khởi tạo 10 và đo 30 kỳ. Thử trọng số kết hợp 0,25/0,5/0,75 với đối chứng cùng ngân sách thực tế; chưa có lợi thế ổn định, không tự đổi trọng số mặc định. Phương pháp và giới hạn tại [báo cáo workspace](backend/docs/reports/workspace-development-2026-10-28.md). Đây không phải tái hiện cấu hình production lịch sử hoặc đánh giá quyết định người dùng trước kỳ.

Kiểm chứng: typecheck/format và 35 kiểm thử frontend qua Edge headless đạt; kiểm tra tương tác và chiều rộng 390 px không tràn. Production build frontend và `mvn clean install` thành công; 57 kiểm thử Java chạy thành công, một kiểm thử báo cáo portfolio cũ được bỏ qua theo điều kiện. PostgreSQL tạm riêng xác nhận migration V13, cạnh tranh/retry, xung đột revision, rollback, đọc dữ liệu đã lưu và hiệu lực settlement. 22 assets Angular trong JAR/static fallback khớp byte; JAR có migration V13. CI đã thêm service PostgreSQL để chạy kiểm tra này, chưa xác nhận workflow trên GitHub. Còn cảnh báo kích thước CSS AI Lab đã có và unchecked Java. Chưa triển khai Render hoặc thay đổi database production.

Smoke test JAR trên cluster PostgreSQL tạm riêng chạy migration đến V13 và khởi động thành công; API thật xác nhận trạng thái chờ dữ liệu, chốt vé tự nhập, retry trả cùng id và lịch sử đọc đúng bản có hiệu lực. Format check frontend thực hiện với line ending LF; sau kiểm tra trả các file không đổi về CRLF của working copy Windows để tránh diff ngoài phạm vi.

## Kiểm tra 28/10/2026 — Dữ liệu kết quả và vé người dùng

Đọc API production: 40 kỳ MEGA hợp lệ và 40 kỳ POWER hợp lệ, không thiếu kỳ theo lịch trong khoảng cuối tháng 7 đến 28/10; một bản ghi POWER 28/09 đang cách ly. 32 vé đã dò gồm 30 vé hợp lệ trên chín cặp loại/ngày; hai vé POWER chứa 113 hoặc toàn số 0. Luồng dò vé chưa chặn đầy đủ input sai. Chưa sửa dữ liệu hoặc code ứng dụng.

Vé người dùng chưa có nguồn thuật toán, snapshot hay thời điểm chốt trước kỳ; `checkedAt` là thời điểm dò sau khi có kết quả. Vì vậy chưa dùng chúng làm bằng chứng dự đoán trước kỳ hoặc huấn luyện có giám sát theo nguồn vé. Người dùng làm rõ trường hợp MEGA 21/10 là hai vé khớp 2 + 2, phủ bốn số khác nhau (10, 14, 22, 25); không phải một vé khớp bốn số. Bộ kết hợp chưa có ledger riêng, không đồng nhất với thuật toán `deep_stacking`.

Số liệu, giới hạn và hướng cải thiện: [kiểm tra dữ liệu dự đoán](backend/docs/reports/prediction-data-audit-2026-10-28.md). Đây là kiểm tra qua API và đối chiếu code, không phải kiểm thử SQL/PostgreSQL hoặc bằng chứng thuật toán cải thiện.

Kế hoạch hiện hành: [trang dự đoán có người dùng can thiệp và gợi ý AI Lab](backend/docs/PREDICTION_WORKSPACE_PLAN.md). Rà soát phát hiện đường đi có thể chốt kỳ tiếp theo lúc 18:00–19:00 khi chưa có kết quả kỳ vừa quay, và fingerprint AI Lab phụ thuộc code scoring của trang dự đoán. Đây là phát hiện từ code, chưa tái hiện production hoặc sửa ứng dụng trong đợt lập kế hoạch.

## Cập nhật 27/10/2026 — Bỏ ma trận seed, đưa dãy mở rộng xuống dưới bộ số gốc

Kiểm tra cho thấy ma trận biến số lấy từ `lottery_deviation_variables` qua repository/API, nhưng migration V3 và các migration lịch sử có chèn dữ liệu cố định; thuật toán không đọc bảng này để chấm điểm hay học. Bỏ section ma trận, các wrapper API frontend không còn dùng, request tải ma trận và các thao tác đồng bộ vốn đánh dấu thành công cả khi lỗi. Giữ nguyên database, migration và API backend để không mất lịch sử hoặc phá tương thích.

Thêm `ExpandedTicketsComponent` ngay dưới ba bộ số gốc bằng content projection. Nút mở/đóng điều khiển danh sách mặc định ẩn; khi mở hiển thị 5 vé từ response hiện tại, có chọn số vé, sao chép và lựa chọn thử nghiệm giảm trùng lặp (tắt mặc định). Bỏ dàn vé trùng lặp ở khu vực chuyên sâu. Không đổi thuật toán và không tự tạo dữ liệu ma trận thay thế.

Kiểm chứng: typecheck, format:check, kiểm thử giao diện và production build đạt; kiểm tra trình duyệt desktop/di động xác nhận mặc định ẩn, bấm mở thấy vé thật ngay sau bộ số gốc, không có ma trận và không tràn ngang. Chưa triển khai Render.

## Cập nhật 27/10/2026 — Bỏ điểm heuristic khỏi trang dự đoán

Theo yêu cầu người dùng, bỏ điểm xếp hạng /100 khỏi biểu đồ và popup số; bỏ điểm biến thể cùng các cột xác suất lệch/chuyển dịch chưa được hiệu chuẩn trong phần mở rộng. Biểu đồ mặc định hiển thị tần suất thực tế, có thể chuyển sang khoảng cách kỳ. Không thay thuật toán chọn số. Thêm liên kết tới lịch sử & đối soát để xem hiệu quả trên các dự đoán đã lưu trước kỳ; không thay điểm heuristic bằng tỷ lệ chính xác suy đoán hoặc thống kê từ việc tính lại trên kết quả đã biết.

Kiểm chứng: 27 kiểm thử frontend và typecheck đạt; kiểm thử tương tác xác nhận điểm đầu vào 99,9 không xuất hiện trong giao diện, biểu đồ sử dụng số lần xuất hiện/khoảng cách thực. Production build thành công, còn cảnh báo stylesheet AI lab đã có. Chưa triển khai Render.

## Cập nhật 27/10/2026 — Đơn giản hóa Dự đoán & kết quả

Đưa ba bộ số (hai thuật toán và bộ kết hợp) lên đầu trang với nút sao chép riêng, kỳ mục tiêu, mốc quay/nhận kết quả và nguồn lịch sử. Tách `PredictionOverviewComponent` để hiển thị bộ số cùng biểu đồ tương tác: chọn mô hình, điểm xếp hạng/tần suất/khoảng cách kỳ, chọn từng số để xem chi tiết và ma trận giao nhau giữa ba bộ. Điểm được ghi rõ là thang nội bộ, không phải xác suất trúng; các thanh thể hiện dữ liệu thực trả về từ backend.

Thông báo bản đầu tiên đã chốt nằm giữa bộ số và biểu đồ, giữ cảnh báo khi bộ đang hiển thị khác bản đã lưu. Bộ chọn thuật toán, biểu đồ lịch sử và phần chuyên sâu được thu gọn; hai phần lớn tải bằng `@defer` khi mở lần đầu. Giữ các chức năng biến thể, ghép vé và cấu hình trong phần mở rộng. Menu di động chuyển thành lưới hai cột để giảm chiều cao điều hướng. Không thay thuật toán hoặc API.

Kiểm chứng: 27 kiểm thử frontend, typecheck, format:check và production build đạt; chỉ còn cảnh báo kích thước stylesheet AI lab đã có từ đợt trước. Kiểm tra Edge với dữ liệu GET từ ứng dụng đang chạy ở 1440 và 390 px: ba bộ số hiển thị, không tràn ngang; bộ số đầu nằm trong viewport 844 px trên di động. Kiểm tra đổi mô hình/chỉ số và mở biểu đồ lịch sử/phần ghép vé đều hoạt động. Chưa triển khai Render.

## Cập nhật 27/10/2026 — Làm mới giao diện phòng thử nghiệm AI

Thiết kế lại `/ai-lab` với phần giới thiệu xanh đậm/mint, minh họa quỹ đạo bằng CSS, thẻ mô hình tím/cam, biểu đồ thanh từ số liệu thực, tiến độ đối soát và thẻ bộ số có đánh dấu số khớp. Chuyển hướng dẫn sang cột bên; tiêu chí sàng lọc và chi tiết phiên bản có thể mở rộng. Bổ sung trạng thái đang tải, danh sách trống, nút chọn MEGA/POWER có `aria-pressed`, nhãn trạng thái tiếng Việt và hỗ trợ giảm chuyển động. Giữ nguyên API, thuật toán, ngân sách vé và các thao tác chốt/đối soát/phân trang.

Kiểm chứng: typecheck và 25 kiểm thử frontend đạt; production build thành công. Có cảnh báo stylesheet AI lab khoảng 14,35 kB vượt mốc khuyến nghị 10 kB, vẫn dưới ngưỡng lỗi 20 kB. Kiểm tra trực quan bằng Edge headless với dữ liệu GET từ production ở viewport 1440 và 390 px; không có tràn ngang. Chưa triển khai Render.

## Cập nhật 27/10/2026 — Thử nghiệm giảm trùng lặp vé

Thêm lựa chọn thử nghiệm `rank-portfolio-v1` trong trang dự đoán: giữ vé top 6, các vé sau giảm lặp số; mỗi cặp vé trùng tối đa 3 số. Giữ bộ tạo vé cũ làm mặc định vì phát lại trên 29 kỳ POWER trước ngày 27/10 cho số kỳ có vé khớp ≥3 giảm từ 3 xuống 1, dù độ phủ 3 vé tăng từ 9 lên 18 số. MEGA có kết quả trái chiều: số kỳ ≥3 tăng từ 1 lên 2 nhưng số khớp trung bình giảm. Không có bằng chứng cải thiện ổn định; không chỉnh trọng số theo báo cáo này.

Sửa request dự đoán chồng lấn khi đổi MEGA/POWER/thuật toán: hủy lượt cũ, xóa payload đang hiển thị và từ chối response sai category; hủy request biến lệch cùng lượt. Chưa xác định lỗi này có gây ra ba vé người dùng nhập kỳ 27/10 hay không.

Phương pháp, giới hạn và cách tái lập: [báo cáo phát triển](backend/docs/reports/portfolio-development-2026-10-27.md). Chưa triển khai production. Vé thử nghiệm chưa có ledger tương lai riêng và không được gộp vào bằng chứng dự đoán đã chốt.

## Cập nhật 27/10/2026 — Nhận kết quả từ 19:00

Đổi mốc tiếp nhận kết quả ngày hiện tại, xác định kỳ hoàn tất mới nhất và đối soát snapshot từ 19:00 giờ Việt Nam. Trước 19:00, kết quả của hôm nay chưa được nhập từ feed hoặc đưa vào lịch sử huấn luyện/đối soát; đúng 19:00 mới đủ điều kiện nếu đã có kết quả hợp lệ. Áp dụng chung cho ledger dự đoán chính và AI lab qua ForecastProtocol. Giữ giờ quay và cutoff chốt dự đoán 18:00.

Scheduler kết quả có thêm cron 19:00 theo Asia/Ho_Chi_Minh, đồng thời giữ lượt khởi động và chu kỳ thử lại 900 giây. Backend phải hoạt động và feed phải có dữ liệu; mốc này không bảo đảm nguồn bên ngoài trả kết quả ngay lập tức. Đã cập nhật nhãn giao diện và hướng dẫn hiện hành; không sửa báo cáo/migration lịch sử hoặc dữ liệu production.

Kiểm chứng: 44 kiểm thử Java và 22 kiểm thử frontend đạt; production build và Maven install thành công; 20 asset Angular trong JAR khớp SHA-256. Kiểm thử xác nhận 18:59:59 chưa nhận/đối soát kết quả hôm nay, đúng 19:00 mới cho phép; kiểm tra cả MEGA/POWER và cùng thời điểm biểu diễn theo UTC. Chưa triển khai production.

## Cập nhật 27/10/2026 — Tách component và style frontend

- Tách 8 component con: PredictionChart, LinkageTimeline, GapPressure, SpacingDistribution, TicketMixer, DrawReconciliation, ReconciliationDetail và HyperparameterHistory. Mỗi component có TS/HTML/CSS riêng; trang cha giữ điều phối dự đoán, chẩn đoán và cập nhật cấu hình chung.
- Template dự đoán giảm từ khoảng 2.373 xuống 1.434 dòng; template phân tích kỳ mới nhất từ khoảng 2.934 xuống 1.005 dòng. Các phần hiển thị chi tiết nhiều tab vẫn có template dài; chúng đã có phạm vi dữ liệu và trách nhiệm riêng, không đặt ngưỡng số dòng cứng cho mọi component.
- Chuyển các thẻ style nhúng của Entry/LatestDrawAnalysis sang CSS riêng; thêm header stylesheet và chuyển style tĩnh của bộ ghép vé/hộp xem cấu hình. Style dùng chung theo feature được import trong stylesheet của component sở hữu DOM, giữ encapsulation.
- Biểu đồ defer từng chế độ khi mở lần đầu và giữ trạng thái bộ lọc khi chuyển qua lại. Tab chi tiết đối soát dùng model để giữ lựa chọn trong lúc tải lại. Request lịch sử/đối soát/khuyến nghị hủy lượt cũ khi thay lựa chọn và hủy khi component bị destroy.
- Bổ sung kiểm thử component cho lựa chọn/copy vé, xem và kích hoạt cấu hình, hủy request, sự kiện phiên bản, giữ bộ lọc khi đổi chế độ và style thực tế sau khi tách. Không thay thuật toán dự đoán hoặc dữ liệu production.

Kiểm chứng: format:check/typecheck đạt; 22 kiểm thử ChromeHeadless đạt; production build thành công không có cảnh báo template. Backend `mvn clean install` đạt với 43 kiểm thử Java. Đối chiếu SHA-256 của 20 asset trong JAR/static fallback đều khớp, không còn asset cũ. Kiểm tra style qua computed style trong browser tests, chưa thực hiện kiểm tra ảnh chụp hoặc triển khai production.

## Cập nhật 27/10/2026 — Chuẩn hóa Angular 22

- Chạy migration chính thức của Angular cho control flow, `inject`, lazy routes và import standalone. Template dùng `@if/@for`, không còn `*ngIf/*ngFor`; bỏ constructor injection và quản lý Subscription/OnDestroy thủ công trong frontend. Component dùng OnPush rõ ràng.
- Header/menu, lịch sử dự đoán và AI lab dùng signals; số liệu dẫn xuất dùng computed. CategoryService là nguồn MEGA/POWER duy nhất, giữ observable bridge tương thích cho phần phân tích/form cũ.
- Thêm DTO thuật toán/lịch sử/AI lab, ForecastApiService; tách phép tính biểu đồ khỏi PredictionComponent thành hàm thuần có kiểm thử. Tách template/style inline của workspace và menu; chuẩn hóa format toàn frontend. Các DTO phân tích động và form cũ chưa được chuyển hết khỏi `any`/field thường.
- Chuyển application/dev-server/Karma sang `@angular/build`; bỏ dependency builder Webpack, thêm Prettier và các lệnh format/typecheck/test:ci. Bật noUnusedLocals/noUnusedParameters và class-field semantics chuẩn; thay baseUrl bằng alias @app. Giữ Zone.js và FormsModule cho các luồng cũ, không thay thuật toán backend.
- Giữ output `frontend/dist/analyzeproject` để Maven/Docker/CI tiếp tục đóng gói; build:sync dùng script Node thay lệnh rm/cp chỉ chạy trên Unix. Docker dùng Node 24 và npm ci theo lockfile. CI chạy toàn bộ frontend suite thay vì chỉ chọn vài file.
- Runtime/compiler/CLI/builder Angular được chốt đồng bộ 22.2.0. Gói tải ban đầu trong lần build local cuối sau chuyển đổi khoảng 364 kB, trước đó khoảng 747 kB; phần còn lại tải qua lazy chunks. Đây là số liệu bundle build, không phải đo thời gian tải production.

Quy ước và lệnh phát triển: [frontend/README.md](frontend/README.md).

Kiểm chứng cuối đợt: format:check và typecheck đạt; toàn bộ 16 kiểm thử ChromeHeadless đạt; Angular production build không còn cảnh báo template/builder deprecated. `mvn clean install` đạt với 43 kiểm thử Java; đối chiếu SHA-256 đủ 16 asset (bao gồm lazy chunks) trong JAR và static fallback, không còn asset build cũ. Backend vẫn có cảnh báo unchecked Java có sẵn. Chưa chạy workflow trên GitHub, build Docker hoặc triển khai production trong đợt này.

## Cập nhật 27/10/2026 — Gỡ chức năng học tiếng Pháp

Đã xóa component học tiếng Pháp (TS/HTML/CSS), controller, hai entity và hai repository Java; gỡ API tiếng Pháp khỏi service dùng chung, constructor/test fixture, route `/french`, liên kết header và khóa dịch riêng của chức năng. Route mặc định và route không xác định chuyển sang `/analyze`. Đồng bộ lại static assets của backend để loại bỏ bundle cũ chứa trang đã xóa.

Giữ tính năng đổi ngôn ngữ giao diện vi/en/fr. Migration V1 được giữ nguyên vì chứa schema/seed lịch sử của toàn dự án; các bảng tiếng Pháp cũ chưa bị xóa khỏi database và không còn được mã ứng dụng sử dụng. Không thay đổi dữ liệu production.

Kiểm chứng sau khi gỡ: Angular production build và 12 kiểm thử ChromeHeadless đạt; backend `mvn clean install` đạt với 43 kiểm thử Java. Không còn tham chiếu chức năng tiếng Pháp trong mã ứng dụng/static assets; JAR không còn các class French và chứa đúng 9 asset Angular mới.

## Cập nhật 27/10/2026 — Phòng thử nghiệm AI

- Thêm trang `/ai-lab` và menu **Phòng thử nghiệm AI**, độc lập với trang dự đoán chính. Mỗi loại có hai mô hình: Logistic Regression thực sự học trọng số từ nhãn quá khứ và tần suất suy giảm theo thời gian (phương pháp thống kê). Không dùng LLM hoặc API AI bên ngoài.
- Mỗi mô hình chốt 5 vé khác nhau/kỳ, gồm vé đứng đầu và các vé lấy mẫu có trọng số với seed cố định. Đối chứng tần suất và ngẫu nhiên cùng có 5 vé, được lưu cùng thời điểm; hai mô hình dùng chung các vé đối chứng cho cùng loại/kỳ.
- Logistic dùng frequency toàn lịch sử, recent10, decay có bán rã 10 kỳ quan sát, gap tối đa 30 kỳ; học full-batch 80 epoch, learning rate 0.2, L2 0.05. Đặc trưng huấn luyện được tính trước khi tiêu thụ kết quả kỳ tương ứng. Cần ít nhất 10 kỳ hợp lệ và kỳ hoàn tất mới nhất để chốt; dùng sáu số chính, không đánh giá số phụ POWER.
- Scheduler chạy sau 20 giây rồi mỗi 60 giây khi backend hoạt động; kiểm tra lưu bản đầu tiên và đối soát. Cutoff 18:00, công bố kết quả 19:00 theo giờ Việt Nam. Không sinh bù quá khứ; bản cũ không bị ghi đè. Transaction lưu cả nhóm 5 vé và khóa đối soát dùng riêng cho phòng thử nghiệm.
- Migration mới `V12__add_ai_lab.sql` tạo `ai_lab_snapshots`, `ai_lab_settlements`, tách khỏi ledger chính. `AI_LAB_ENABLED=false` tắt tạo mới nhưng vẫn đối soát bản cũ. `FORECAST_ENABLED` điều khiển riêng trang dự đoán chính.
- API `GET /api/ai-lab?category=MEGA&page=0` chỉ đọc, phân trang 50 vé; `POST /api/ai-lab/run` kiểm tra chốt hai loại và đối soát. Trang tự tải lại mỗi phút, hủy request cũ khi đổi loại/rời trang và chặn gửi POST trùng.
- Báo cáo tách theo loại/mô hình/phiên bản mã/cấu hình, dùng settlement mới nhất; chỉ đo kỳ đủ 5 vé hợp lệ và đã đối soát toàn bộ. Hiển thị tỷ lệ từng vé đạt ≥3 và tỷ lệ kỳ có ít nhất một vé đạt ≥3, cùng hai đối chứng có cùng ngân sách vé.
- Quy tắc sàng lọc v1: ít nhất 60 kỳ đã đo, 60 kỳ gần nhất liên tiếp theo lịch; cả hai khối 30 kỳ gần nhất có tỷ lệ kỳ đạt ≥3 từ 50%; cận dưới chênh lệch ghép cặp 95% xấp xỉ trên toàn nhóm lớn hơn 0 với cả hai đối chứng. Đây là quy ước thử nghiệm, chưa được hiệu chỉnh cho việc xem lặp lại/nhiều mô hình hoặc phụ thuộc theo thời gian. Nhãn `VALIDATION_CANDIDATE` yêu cầu xác nhận trên các kỳ tương lai mới, không tự áp dụng mô hình vào dự đoán chính.
- Chưa có bằng chứng hiệu quả thực tế, chưa triển khai production hoặc chạy migration V12 trên database thật trong đợt này. Ưu tiên phát triển chuyển sang học máy và thực nghiệm theo yêu cầu người dùng; không mở rộng hạng mục kiểm thử PostgreSQL.
- Kiểm chứng local: Angular production build đạt; 12 kiểm thử ChromeHeadless cho workspace/history/AI lab đạt; `mvn install` đạt với 43 kiểm thử Java, gồm kiểm tra học máy, ngân sách vé, giữ bản đã chốt, loại dữ liệu tương lai, thống kê theo kỳ, điều kiện ứng viên và route/API qua MockMvc. Đối chiếu SHA-256 của 9 asset Angular trong JAR đều khớp; JAR chứa mô hình và migration V12. Còn các cảnh báo template/deprecation Angular và unchecked Java có sẵn. MockMvc/mock repository không phải kiểm thử database thật.

Kế hoạch tiếp theo: [lộ trình phát triển và tiêu chí nghiệm thu](backend/docs/DEVELOPMENT_PLAN.md). Đợt 26/10/2026 bổ sung workflow CI kiểm thử hai feature Angular, Java và đối chiếu asset trong JAR; chưa bao gồm PostgreSQL thật hoặc xác nhận workflow chạy trên GitHub.

Cập nhật ngày **26/10/2026**, sau khi dọn cấu trúc theo yêu cầu chỉ giữ frontend và backend. Tài liệu phản ánh repository, không xác nhận phiên bản hoặc dữ liệu production.

## 1. Mục tiêu

Xác định và đề xuất bộ **6 số chính Vietlott** bằng thuật toán và thống kê cho MEGA 6/45, POWER 6/55. Số phụ POWER xử lý riêng. Đánh giá bằng số chính khớp, tỷ lệ từ 3 số và so sánh phương pháp nền; không dùng điểm heuristic như xác suất trúng đã được hiệu chuẩn.

**Chưa có bằng chứng đáng tin cậy về lợi thế dự đoán.** Việc sửa code, đạt kiểm thử hoặc tăng số khớp trên mẫu phát triển không tự chứng minh hiệu quả tương lai.

## 2. Cấu trúc hiện tại

| Thành phần | Vị trí | Vai trò |
| --- | --- | --- |
| Angular | `frontend/` | Giao diện dự đoán, nhập kết quả, phân tích, `/forecasts` |
| Spring Boot Java 17 | `backend/` | API, thuật toán, đồng bộ kết quả và lưu dự đoán |
| PostgreSQL / Flyway | `backend/src/main/resources/db/migration/` | Lưu dữ liệu hoạt động, schema hiện tại đến V11 |
| Triển khai | `Dockerfile`, `render.yaml`, `DEPLOY_RENDER.md` | Build Angular rồi đóng gói cùng Spring Boot |
| Cấu hình môi trường / IDE | `.env.example`, `.vscode/` | Giữ nguyên khi dọn dự án |
| AI / agents / skills | `metadata.json`, `.github/` | Giữ lại; workflow cập nhật theo cấu trúc mới |
| Báo cáo lịch sử | `backend/docs/reports/` | Bằng chứng của các lần kiểm tra trước |
| Dữ liệu ứng dụng cũ | `backend/archive/legacy-node-data/` | Lưu đối chiếu, không tự nhập vào PostgreSQL |

Đã xóa ứng dụng Node/Vite độc lập ở root: `server.ts`, `src/`, `tests/`, `scripts/`, package/lockfile gốc, cấu hình Vite/TypeScript gốc, `index.html`, dependency/build/cache/thử nghiệm gốc. Không xóa mã trong frontend/backend hoặc sửa dữ liệu production.

Không còn lệnh `npm test`, `npm run dev` hay `npm run build` ở root. Chạy npm trong `frontend/`; chạy Maven trong `backend/`. Frontend có cấu hình TypeScript, package và lockfile riêng, không phụ thuộc cấu hình root đã xóa.

Build Angular trước Maven để profile `local-frontend-build` lấy giao diện từ `frontend/dist/analyzeproject`. Docker cũng build hai phần riêng và dùng PostgreSQL. Tài liệu vận hành chính: [README](README.md).

## 3. Dữ liệu và chất lượng

Tài nguyên seed nằm trong `backend/src/main/resources/data/`; dữ liệu hoạt động của Spring nằm trong PostgreSQL. Seed lịch sử Flyway, JSON và database đang chạy có thể khác nhau; phải kiểm tra sau đồng bộ, không suy ra số lượng production từ JSON.

Snapshot JSON sau làm sạch có 79 bản ghi: 39 MEGA hợp lệ, 39 POWER hợp lệ và một POWER 28/09/2026 bị cách ly. Đã sửa 4 kỳ có số chính sai và điền 11 số phụ thiếu, có audit trước/sau cùng nguồn đối chiếu. Đây là số liệu của bộ JSON đã kiểm tra, không phải truy vấn production hiện tại.

- `UNVERIFIED`: chưa đủ xác minh; `SOURCE_MATCHED`: khớp nguồn cấu hình; `QUARANTINED`: cách ly.
- Chỉ revision mới nhất đủ điều kiện, dấu nhận diện giá trị còn khớp mới được dùng cho dự đoán, thống kê, backtest và dò vé.
- Sửa tay ngày/loại/số làm mất điều kiện cho đến khi xác minh lại; không fallback sang revision cũ hợp lệ.
- Sửa số chính chỉ theo danh sách `reviewed_draw_corrections.json`; chỉ điền số phụ thiếu khi cả 6 số chính khớp. Không tự đổi ngày hoặc xóa xung đột.
- `SOURCE_MATCHED` với feed cộng đồng không phải chứng nhận độc lập của Vietlott.

Nguồn mặc định: `vietvudanh/vietlott-data`, không cần API key; `power645.jsonl` là MEGA, `power655.jsonl` là POWER. Scheduler chờ 10 giây sau khởi động, sau đó 900 giây giữa các lần hoàn tất; mặc định đối soát 90 ngày. Chỉ nhận ngày hiện tại sau 19:00 giờ Việt Nam, không nhận ngày tương lai. Có transaction/advisory lock và ghi audit; lỗi từng loại được xử lý độc lập.

`GET /api/lottery-sync/status` trả trạng thái trong bộ nhớ; `GET /api/lottery-sync/quality` trả chất lượng và audit đã lưu. Tăng `LOTTERY_SYNC_LOOKBACK_DAYS` khi cần lịch sử dài hơn. Ứng dụng phải chạy để scheduler hoạt động.

## 4. Thuật toán

Mô hình `temporal_learning`, phiên bản **temporal-links-v2**, kết hợp 9 thành phần: tần suất toàn lịch sử; chuyển tiếp một/hai kỳ; lặp số; lân cận ±1; cùng đuôi; số phụ sang số chính; tần suất bán rã 5 và 15 kỳ quan sát.

Các thống kê điều kiện được làm trơn về tần suất nền. Trọng số cập nhật theo số khớp trong bộ 6 của từng thành phần sau khi quan sát kết quả. Brier chỉ còn là chỉ số chẩn đoán điểm số. Mỗi lượt kiểm thử dự đoán trước khi học kết quả đích; không coi hai bản ghi thiếu kỳ theo lịch là chuyển tiếp liên tiếp. Có fallback tần suất khi hỗn hợp có tổng số khớp kém hơn phương pháp nền trên các kỳ đã đánh giá trước đó.

Danh mục API còn có `bayesian_graph`, `deep_stacking`; một số tên/nhánh cũ mang tính heuristic. Tên XGBoost hoặc AI không chứng minh đã huấn luyện mô hình. Gộp thứ hạng dùng tổng `1/rank`, tie-break ổn định. Thuật toán phụ thực sự chạy; frontend không tự chế bộ phụ/kết hợp khi API thiếu.

Mã chính nằm trong `backend/src/main/java/com/example/analyzeproject/`:

- `service/AnalyzeService.java`, `PredictionSupport.java`: dự đoán và đối soát lịch sử.
- `service/TemporalRelationshipModel.java`: học liên kết.
- `service/DrawQuality.java`, gói `sync/`: chất lượng và đồng bộ.
- `service/ForecastService.java`, `ForecastProtocol.java`, `repository/ForecastStore.java`: lưu và đánh giá dự đoán thực tế.

## 5. Lưu dự đoán, đối soát và đánh giá độc lập

Đã triển khai trong Spring và giao diện Angular `/forecasts`:

- Tự kiểm tra sau khởi động 12 giây, sau đó mỗi 60 giây; thử lưu bộ 6 số chính của từng thuật toán trong danh mục cho kỳ tiếp theo. API dự đoán cũng thử lưu bộ đang hiển thị.
- Giữ nguyên bản đầu tiên theo loại/kỳ/thuật toán. Thay cấu hình hoặc gọi lại không ghi đè; UI thông báo nếu bộ đang tính khác bản đã chốt. Chưa tự lưu riêng mọi vé biến thể/kết hợp.
- Lưu thời điểm, kỳ đích, phiên bản mô hình, dấu nhận diện mã, cấu hình dùng thực tế, lịch sử đầu vào và SHA-256. Không nhận ngày quá khứ/thời điểm tạo do client chỉ định để chèn hồi tố.
- Cần ít nhất 5 kỳ hợp lệ và kỳ hoàn tất mới nhất theo lịch. Mốc chốt là 18:00 giờ Việt Nam; sau đó chọn kỳ tiếp theo. Lọc dữ liệu tương lai/kết quả chưa công bố.
- Bộ đối chứng tần suất và ngẫu nhiên được lưu cùng thời điểm, không dùng kết quả đích để chọn seed.
- Sau 19:00, đối soát với kết quả mới nhất hợp lệ. Các sự kiện `PENDING`, `SETTLED`, `RESULT_UNAVAILABLE` được ghi thêm, không sửa snapshot. Kết quả đổi/cách ly sẽ cập nhật thống kê theo sự kiện mới nhất; chạy lại cùng trạng thái không tạo trùng.
- Chỉ đo snapshot có thời gian/dấu dữ liệu hợp lệ và trạng thái mới nhất `SETTLED`. Tách nhóm theo loại, thuật toán, phiên bản mã/mô hình và cấu hình. Không dùng backtest hoặc snapshot chưa có kết quả làm mẫu đã đo.
- Báo cáo số khớp TB, phân bố 0–6, tỷ lệ từ 3 số, hai đối chứng và chênh lệch ghép cặp với khoảng 95% xấp xỉ. Không tự chọn/tối ưu cấu hình từ nhóm này. Nhãn dưới 30 kỳ chỉ là quy ước mẫu nhỏ, không phải ngưỡng chứng minh hiệu quả.

PostgreSQL có `forecast_snapshots` và `forecast_settlements` qua `V11__add_forecast_ledger.sql`; V10 là migration schema audit dữ liệu. Không sửa migration lịch sử để chèn dự đoán. `FORECAST_ENABLED=false` tắt tạo snapshot mới nhưng vẫn cho xem/đối soát bản cũ.

| API | Chức năng |
| --- | --- |
| `GET /api/forecasts` | Chỉ đọc snapshot, sự kiện và trạng thái; không tự chạy đối soát |
| `POST /api/forecasts/capture` | Thử chốt các thuật toán cho kỳ tiếp theo và đối soát |
| `POST /api/forecasts/reconcile` | Đối soát lại, không tính lại dự đoán |
| `GET /api/forecasts/evaluation?category=MEGA` | Đánh giá độc lập; bỏ category để xem cả hai loại |

Sáu dự đoán JSON đã tạo ngày 26/10 cho POWER 27/10 và MEGA 28/10 được bảo toàn tại `backend/archive/legacy-node-data/forecast_ledger.json`. **Chưa tự chuyển các bản này vào PostgreSQL**. Các lần chạy PostgreSQL tạm chỉ là kiểm chứng; không xác nhận đã lưu chúng trên production. Không được tính lại hoặc đổi thời điểm cũ nếu thực hiện chuyển đổi sau này.

## 6. Kết quả đo và giới hạn

Báo cáo phát triển V1/V2 trước khi bỏ Node dùng JSON 39 kỳ hợp lệ mỗi loại, 34 lượt đánh giá sau 5 kỳ khởi tạo:

| Chỉ số lịch sử | MEGA | POWER |
| --- | --- | --- |
| V1 số khớp TB/vé | 0,6765 | 0,7059 |
| V2 số khớp TB/vé sau fallback | 0,7059 | 0,7647 |
| Tần suất nền | 0,7059 | 0,7647 |
| Kỳ vọng chọn ngẫu nhiên `36/N` | 0,8000 | 0,6545 |
| V2 khớp từ 3 số | 0/34 | 1/34 |

Đây là phép tính lại trên mẫu đã biết trong quá trình phát triển, không phải số liệu đánh giá độc lập của Spring production. V2 bằng tần suất nền, chưa chứng minh lợi thế. Kỳ vọng ngẫu nhiên là lý thuyết, không phải kết quả một kiểm định.

Kết quả MEGA 25/10 lưu đã đối soát là 04–13–22–25–40–41. Tái dựng theo lịch sử đến 23/10: V1 chọn 03–06–15–27–31–36; V2 chọn 03–06–15–16–27–36; cả hai khớp 0. Không có snapshot trước giờ quay để xác nhận chính xác bộ đã hiển thị cho người dùng. Không suy rộng phép tái dựng này sang mọi vé/thuật toán.

## 7. Kiểm chứng

Trước khi dọn: 30 kiểm thử Java, 33 kiểm thử Node cũ, Angular production, build JAR; PostgreSQL 16.4 tạm chạy Flyway V1–V11, kiểm tra lưu đồng thời, đối soát, cách ly/xác minh lại và restart. Chín asset Angular đã được đối chiếu với JAR. Không kết nối production.

Sau khi dọn đã build Angular production và chạy `mvn.cmd install` thành công với 30 kiểm thử Java; hai phần không phụ thuộc file root đã xóa. Không còn bộ kiểm thử Node hoặc script Node/PostgreSQL gốc; báo cáo vẫn được giữ làm bằng chứng lịch sử. Kiểm thử Java còn nguyên trong `backend/src/test/`. Các cảnh báo template/deprecation Angular và unchecked Java có sẵn vẫn cần xử lý riêng.

Lệnh chạy từ đúng thư mục:

```powershell
# frontend/
npm.cmd run build

# backend/
mvn.cmd install
```

## 8. Những việc cần cải thiện

| Ưu tiên | Việc cần làm | Mục đích |
| --- | --- | --- |
| P0 | Chạy backend thực tế qua các kỳ tương lai | Chốt trước 18:00, đối soát sau 19:00, tích lũy bằng chứng độc lập |
| P0 | Quy trình phát triển/chọn tham số/kiểm tra tách theo thời gian | Không chọn cấu hình thắng từ chính tập đánh giá độc lập |
| P1 | Quyết định chuyển đổi dữ liệu archive khi cần | Đối chiếu PostgreSQL, giữ thời điểm và nguồn gốc snapshot; không nhập trùng/ghi đè |
| P1 | Mở rộng lịch sử đã xác minh | Đo kỳ thiếu, đối chiếu nguồn và database; tránh thống kê trên mẫu ngắn |
| P1 | Đo đóng góp từng thành phần | Chỉ thay bán rã/trọng số khi có kết quả ngoài tập chọn tham số |
| P1 | Chuẩn hóa dependency, runtime, build và CI | Build sạch Angular/JAR/Docker; cấp PostgreSQL tạm và kiểm tra migration/API tự động bằng công cụ thuộc backend |
| P2 | Rà soát nhãn/diễn giải/heuristic cũ | Không mô tả điểm số là xác suất; loại thông tin cố định gây hiểu nhầm |
| P2 | Vận hành và schema | Lưu lịch sử sync bền vững, cảnh báo stale; đánh giá `ddl-auto=update` cùng `validate-on-migrate=false` |

## 9. Bằng chứng lịch sử

- [V1](backend/docs/reports/learning-v1-2026-10-26.json), [V2](backend/docs/reports/learning-v2-2026-10-26.json).
- [Lưu dự đoán Node/restart trước khi ngừng Node](backend/docs/reports/forecast-node-2026-10-26.json).
- [PostgreSQL tạm, migration/API/restart](backend/docs/reports/forecast-postgres-2026-10-26.json).
- [Tổng hợp làm sạch dữ liệu](backend/docs/reports/data-cleanup-summary-2026-10-25.json), [kiểm chứng PostgreSQL](backend/docs/reports/data-cleanup-postgres-2026-10-25.json).

Các đường dẫn/lệnh Node nằm trong nội dung báo cáo cũ là thông tin lịch sử, không còn là hướng dẫn chạy dự án hiện tại.

## 10. Tối ưu trang forecasts (26/10/2026)

Phát hiện từ mã: trang trước đây tải hai GET song song, cả hai gọi lại đối soát toàn bộ ledger, truy vấn lịch sử kết quả và chờ cùng advisory lock PostgreSQL. Mỗi lần tải còn gửi toàn bộ history/configuration của từng snapshot về giao diện. Đây là nguồn tải lặp đáng kể; chưa có log production để xác nhận nguyên nhân duy nhất của HTTP 503.

Đã thay đổi:

- Trang gọi một `GET /api/forecasts/overview?category=MEGA&page=0&size=50`, trả hàng hiển thị và đánh giá cùng một phiên đọc. Mỗi trang mặc định 50 dòng, giới hạn 100; báo cáo đánh giá vẫn tính trên toàn bộ loại đã chọn, không chỉ trang hiện tại.
- GET ledger/evaluation/overview chỉ đọc; đối soát do scheduler hoặc nút/API POST riêng thực hiện. Không lấy khóa ghi và không truy vấn bảng kết quả xổ số trên đường tải trang.
- Cache trong bộ nhớ 10 giây, dùng chung cho các GET đồng thời trong một instance; đọc hai bảng ledger trong transaction read-only repeatable-read. Cache bị vô hiệu hóa sau khi lưu snapshot hoặc đối soát thành công. Replica khác có thể thấy thay đổi sau tối đa TTL cache; đối soát tự động vẫn theo chu kỳ 60 giây.
- Dữ liệu trả về overview không chứa lịch sử huấn luyện/cấu hình đầy đủ; GET ledger gốc vẫn phục vụ nhu cầu audit.
- Chặn tác vụ capture/reconcile chồng nhau trong cùng instance bằng trạng thái `BUSY`, thay vì xếp thêm lượt chờ. PostgreSQL vẫn giữ lock/transaction bảo vệ nhiều replica.
- Angular hủy request cũ khi đổi loại/rời trang, chặn gửi POST trùng, phân trang và không tự retry 503. Nút tải lại chỉ đọc; nút đối soát mới chạy đối soát.

Kiểm chứng: 34 kiểm thử Java đạt; 4 kiểm thử trang forecasts chạy bằng ChromeHeadless đạt; Angular production build đạt. Kiểm thử đồng thời bằng repository mock xác nhận 8 lượt đọc dùng chung một lần lấy ledger, không gọi truy vấn lịch sử hoặc đối soát. Đây không phải benchmark PostgreSQL/production; chưa khẳng định loại bỏ mọi nguyên nhân 503 như thiếu RAM, cold start hoặc giới hạn nền tảng hosting.

## 11. Gộp giao diện dự đoán và đối soát thành hai tab (26/10/2026)

Trang `/analyze` hiện là workspace chung với hai tab: **Tạo dự đoán** (`/analyze`) và **Lịch sử & đối soát** (`/analyze/history`). Route `/forecasts` chuyển hướng sang tab lịch sử; menu chỉ còn một mục Dự đoán & kết quả. Liên kết xem bộ đã chốt trên trang dự đoán đi thẳng đến tab lịch sử.

Mỗi tab dùng route con, chỉ tạo component đang mở. Tab dự đoán không tải API lịch sử ở nền; khi rời tab lịch sử, các request đang chờ bị hủy. MEGA/POWER dùng trạng thái chung của AnalyzeService với header, đồng bộ lựa chọn khi đổi tab; không gọi lại history khi phát lại cùng một category.

Tab lịch sử có thẻ tổng số bộ đã lưu/đã đối soát/chưa đánh giá/khớp từ 3 số; các số này lấy từ dữ liệu thực tế và không gán tỷ lệ preset. Thẻ bộ số hiển thị kỳ đích, thuật toán, thời điểm lưu, trạng thái, kết quả và số khớp; số khớp được tô màu kèm nhãn hỗ trợ truy cập. Có bộ lọc trạng thái trong trang hiện tại, phân trang 50 bộ và bảng đối chứng theo phiên bản/cấu hình. CSS hỗ trợ màn hình nhỏ; giữ riêng thao tác tải lại, lưu kỳ tiếp theo và đối soát. Không thay đổi cơ chế lưu snapshot hoặc thuật toán backend.

Kiểm chứng: Angular production build thành công; 8 kiểm thử ChromeHeadless đạt cho HTTP, đồng bộ category, thống kê/tô số khớp, route chuyển hướng và việc chỉ mở component của tab đang chọn. Backend `mvn install` đạt với 34 kiểm thử Java. Chưa triển khai production; kiểm chứng giao diện qua DOM/browser tests, chưa kiểm tra bằng ảnh chụp trình duyệt riêng vì lệnh chụp bị hệ thống chặn.

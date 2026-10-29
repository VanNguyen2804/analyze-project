# Kế hoạch sửa trang /analyze

Ngày lập: 29/10/2026. Trạng thái: đã triển khai luồng báo cáo, lưu bằng chứng mới và các kiểm tra tính đúng đắn tại workspace; chưa triển khai production. Kết quả kiểm chứng và giới hạn được ghi ở [PROJECT_ANALYSIS.md](../../PROJECT_ANALYSIS.md). Đánh giá lợi thế của mô hình mới cần kỳ tương lai, không được coi là hoàn tất bằng việc sửa trang. Căn cứ: [đối chiếu POWER 29/10](reports/power-review-2026-10-29.md), code Spring/Angular hiện tại và [workflow dự án](../../.github/skills/analyze-project-workflow/SKILL.md).

Điều chỉnh sau khi xác minh routing: phần giải thích cũ nằm ở `/latest-analysis`; `/analyze` là trang chọn vé. Báo cáo mới nằm tại `/analyze/review`, liên kết cũ chuyển hướng tới tab này. Giữ nguyên trang chọn vé và bổ sung mức tập trung của danh sách trước khi chốt.

## 1. Mục tiêu và phạm vi

Trang phải trả lời được bốn câu hỏi bằng dữ liệu truy vết được: kỳ này ra gì; từng vé khớp bao nhiêu; mô hình đã chọn/bỏ số nào trước kỳ; cả nhóm vé có hiệu quả ra sao so với đối chứng cùng ngân sách.

Hiệu quả vận hành gồm đúng loại/kỳ/nguồn, số liệu nhất quán, không tạo dữ liệu khi chỉ xem trang và không hiển thị kết quả request cũ. Hiệu quả dự đoán phải được đo riêng trên nhiều kỳ; sửa lỗi trang không tự chứng minh tăng khả năng trúng.

Giữ layout Header/Left menu/Content và phong cách hiện có. Điều chỉnh nhãn, trạng thái và dữ liệu trong các khu vực phục vụ phân tích; đây là đề xuất trong plan, chưa thực hiện thay đổi UI. Không làm lại trang dự đoán hoặc AI Lab; tận dụng snapshot/workspace đã có. Không đổi thuật toán, mặc định giảm trùng hoặc trọng số trong đợt sửa tính đúng đắn.

## 2. Những lỗi và giới hạn đã xác định

| Vấn đề | Bằng chứng | Ảnh hưởng |
| --- | --- | --- |
| Giải thích độc lập với thuật toán được chọn | `AnalyzeService.analyzeOfficialDraw` dùng `algorithm` chỉ ở nhãn; ba response ngày 29/10 giống nhau ngoài tên | Xếp hạng/lý do không giải thích quyết định thật của mô hình |
| Dữ liệu số trượt thiếu hợp đồng | API không trả `allNumberDetails` mà component cha đọc | Thống kê có fallback thay vì chi tiết mô hình |
| Điểm và văn bản nói quá dữ liệu | Sigmoid công thức chung được đặt tên `probabilityPercent`; lý do ghép theo ngưỡng gap | Người dùng dễ hiểu thành xác suất hoặc nguyên nhân số ra/không ra |
| Đọc trang có tác dụng ghi | `DrawReconciliationComponent.loadRecommendations` gọi `getPrediction`, nay POST `/prediction-workspace/suggestions`; `WorkspaceService.create` lưu suggestion | Xem phân tích kỳ cũ có thể tạo gợi ý kỳ tương lai, tăng tính toán và lưu trữ |
| Các phần chưa dùng cùng bộ lọc | `ngOnChanges` của đối soát chỉ tải lại theo category; có state thuật toán/ngày riêng; request cha chỉ hủy khi destroy | Có nguy cơ trộn ngày/thuật toán hoặc response cũ ghi đè khi đổi bộ lọc nhanh; chưa tái hiện bằng browser trong lần lập plan |
| Snapshot cũ không đủ mọi chi tiết | Forecast ledger lưu vé/history/config nhưng không lưu ranking/feature đầy đủ; workspace lưu ranking nhưng chưa có đủ feature/đóng góp | Không được hứa tái hiện chính xác điểm lịch sử chỉ bằng tính lại code hiện tại |
| Đối soát và áp dụng cấu hình bị đặt gần nhau | Nút cập nhật gửi báo cáo/adjustments sang API lưu cấu hình; lấy bản mới nhất làm cấu hình hiện hành | Lưu báo cáo không đồng nghĩa huấn luyện hay chứng minh sửa sai |

## 3. Giao kèo dữ liệu đề xuất

Tạo DTO Java và TypeScript riêng cho phân tích, thay dần `Map`/`any` trên luồng này. Đề xuất endpoint chỉ đọc `GET /api/analyze/draw-report?category=...&date=...&algorithm=...`; hỗ trợ chọn thêm nguồn/id snapshot khi có nhiều bản hợp lệ. Endpoint cũ giữ adapter trong thời gian chuyển đổi, không trả tên thuật toán mới trên số liệu chung.

Các khối của response:

- `context`: category, ngày kết quả thực tế, thuật toán, thời điểm lập báo cáo, phiên bản schema. Ngày để trống được server xác định rồi trả lại; không tìm được ngày yêu cầu thì báo rõ, không tự chuyển ngày khác.
- `draw`: sáu số chính, số phụ nếu có, nguồn, chất lượng, fingerprint/revision, thời điểm cập nhật và trạng thái chờ/kết quả bị cách ly.
- `evidence`: nguồn WORKSPACE / FORECAST / AI_LAB, snapshot id, thời điểm tạo/chốt, phạm vi vé, model/config/code version, trainingThrough, history hash; trạng thái thiếu snapshot hoặc thiếu chi tiết giải thích. Các nguồn cùng tồn tại, không tự lấy nguồn này thay nguồn kia.
- `numberDetails`: một record cho từng số trong miền 45/55, gồm rank/score và feature thực có; mỗi record ghi nguồn/thời điểm. Dữ liệu thiếu dùng null kèm lý do, không ép thành zero. Điểm nội bộ ghi rõ là score; chỉ dùng probability nếu đã có quy trình hiệu chuẩn và đánh giá tương ứng.
- `ticketResults`: vé gốc/vé chỉnh/vé tự nhập, id nguồn hoặc nguồn chưa biết, sáu số, số khớp, số không khớp, số phụ khớp và nhãn giải từ một bộ dò thống nhất.
- `portfolio`: số vé đã nhập và số vé khác nhau, số độc nhất đã chọn, lượt khớp thô, độ phủ duy nhất, vé tốt nhất, phân bố khớp 0–6, ma trận giao nhau và tần suất lặp số giữa vé.
- `evaluation`: loại đánh giá, số kỳ/số vé và đối chứng đủ điều kiện; phân biệt snapshot thật với phát lại bằng code/cấu hình hiện tại.

Nhập vé để xem thử dùng hàm đánh giá thuần hoặc endpoint POST chỉ tính, không ghi. Lưu lịch sử phải là thao tác riêng có chủ đích. GET báo cáo không tạo suggestion, chốt vé, đối soát ghi sự kiện hoặc thay cấu hình.

## 4. Thứ tự triển khai và nghiệm thu

### Đợt 1 — Nguồn dữ liệu đúng và luồng đọc không ghi (P0)

1. Tạo `DrawAnalysisService` và DTO chuyên biệt, để logic trình bày/báo cáo ra khỏi class scoring khi phù hợp.
2. Đọc kết quả qua `DrawQuality`; chỉ một revision hiệu lực mỗi kỳ. Kiểm tra category/date/algorithm ở backend; dùng clock có thể kiểm thử và lịch Việt Nam hiện hành.
3. Adapter đọc workspace, ledger và AI Lab theo category + targetDate + id nguồn. Đọc revision batch đang có hiệu lực; vé đã dò sau kỳ giữ nhóm riêng. Vé giống nhau chỉ được ghi nhận “trùng bộ số”, không tự gán provenance.
4. Bỏ đường `loadRecommendations -> getPrediction` khỏi trang phân tích; thay bằng dữ liệu snapshot hoặc API báo cáo chỉ đọc. Giữ chức năng chủ động tạo gợi ý ở trang dự đoán.
5. Tách thống kê chung trước kỳ khỏi giải thích mô hình. Xóa các mẫu diễn giải về “điểm rơi”, số gan phải ra hoặc nguyên nhân tất định; thay bằng quan sát có số liệu.

Nghiệm thu: mở/làm mới/đổi ngày trên `/analyze` không làm tăng suggestion/forecast/batch hoặc thay config. Kết quả cách ly/chưa nhận không được dùng để tuyên bố trúng/trượt. Chọn thuật toán truy vấn đúng nguồn tương ứng; hai thuật toán có thể thật sự trùng vé/ranking và phải hiển thị đúng việc đó, không ép kết quả khác nhau.

### Đợt 2 — Giải thích dựa trên dữ liệu đã lưu (P0)

1. Với snapshot có ranking/chi tiết: dùng đúng bản đó, không tính lại bằng cấu hình đang active.
2. Bổ sung dữ liệu giải thích vào snapshot mới ngay lúc sinh dự đoán, cùng lịch sử/cấu hình đã dùng: ranking đầy đủ, score semantics, feature thực được sử dụng, model/version và tie-break. Bộ kết hợp lưu hai ranking, trọng số và keep/exclude. Chỉ ghi đóng góp feature khi mô hình thực sự cung cấp được, không suy ra đóng góp từ tên feature.
3. Dùng chung kết quả scoring giữa dự đoán và dữ liệu giải thích; không thêm công thức sigmoid phụ để lấp trường còn thiếu. Giữ quy tắc dự đoán và vé mặc định như cũ.
4. Snapshot cũ chỉ có vé: vẫn dò được số khớp, nhưng giải thích rank/score lịch sử là “chưa lưu”. Nếu hỗ trợ phát lại, đặt thao tác riêng, gắn nhãn code/cấu hình hiện tại và history trước D; không ghi kết quả phát lại thành snapshot trước kỳ.
5. Version payload JSON mới có kiểm tra đọc tương thích snapshot cũ. Chỉ thêm migration mới nếu cần chỉ mục/bảng/cột; không sửa V11/V13 hoặc backfill điểm lịch sử. Kiểm tra fingerprint: chuyển code báo cáo không được âm thầm viết lại cohort cũ; đánh dấu rõ chuyển phiên bản snapshot mới, tách phiên bản báo cáo và scoring theo phụ thuộc thực tế.

Nghiệm thu: đổi active config hoặc thêm kỳ mới không làm đổi nội dung giải thích của snapshot đã lưu. Lịch sử đầu vào của giải thích trước kỳ D không chứa D hoặc tương lai. Có chi tiết số không được chọn, ngoài sáu số đã ra; thiếu feature/rank vẫn là thiếu, không có lý do giả. Các vé top sáu trước/sau thay đổi instrumentation giữ nguyên với cùng đầu vào.

### Đợt 3 — Dò vé và báo cáo nhóm dùng chung phép tính (P0)

1. Tái sử dụng validation/số khớp/độ phủ hiện có từ workspace qua helper thuần phù hợp; thống nhất bộ dò giữa backend và frontend, tránh ba bản quy tắc giải lệch nhau.
2. Chặn số không nguyên, trùng, thiếu/thừa hoặc ngoài miền; không lọc bỏ số sai rồi coi vé còn lại là hợp lệ. Vé cũ sai giữ nguyên để audit và loại khỏi mẫu hợp lệ.
3. POWER xét sáu số trên vé; số phụ của kết quả được kiểm tra trong sáu số đó khi xét trường hợp 5 số chính + số phụ. Không để ô “số phụ dự đoán” riêng làm thay đổi quyền trúng của vé; kiểm tra lại contract cũ và nhãn giao diện.
4. Vé nhập trùng hiển thị rõ; tính độ phủ và phân tích nhóm trên vé khác nhau. Nếu theo dõi số lượng mua thì tách trường số lượng, không biến thành nhiều mẫu dự đoán độc lập.
5. Trả mức trùng và số bị bỏ sót bằng giao/hợp tập hợp. Không biến “trùng nhiều” thành nguyên nhân chắc chắn của việc không trúng.

Nghiệm thu bằng fixture cục bộ ngày 29/10: khớp `[0,1,0,1,2]`, bốn lượt khớp, ba số kết quả duy nhất, 19 số đã chọn, vé tốt nhất hai số, vé 80/81 trùng năm số. Fixture MEGA 21/10: hai vé khớp hai số khác nhau mỗi vé phải là độ phủ bốn, không phải một vé khớp bốn. Kiểm tra thêm sáu số chính, năm chính + phụ, năm chính không phụ, input sai và vé trùng.

### Đợt 4 — Đồng bộ trạng thái và trình bày trên Angular (P1)

1. Một nguồn state đã áp dụng gồm category/date/algorithm/source; nếu giữ nút tra cứu thì tách bộ lọc đang chỉnh khỏi bộ lọc đang hiển thị. Các component con nhận cùng context, không âm thầm dùng kỳ/thuật toán riêng.
2. Dùng `switchMap` hoặc cơ chế hủy tương đương cho cả luồng kết quả, vé và chi tiết; kiểm tra context response trước khi áp dụng. Bỏ request lặp do vừa set category vừa gọi fetch trực tiếp. Khi đổi context xóa dữ liệu cũ và giữ trạng thái loading/error đúng request.
3. Trong layout hiện có, ưu tiên thứ tự: kết quả/nguồn → từng vé và tổng nhóm → giải thích mô hình → lịch sử đánh giá. Chuyên sâu tải khi mở, không tính lại ranking mỗi lần render.
4. Hiển thị rõ “Đã lưu trước kỳ”, “Dò sau kỳ”, “Phát lại” và “Chưa có dữ liệu”. Đổi nhãn `hitRate` thành đúng độ phủ hoặc tỷ lệ vé đạt ngưỡng; không dùng chung một chữ “độ chính xác” cho các mẫu số khác nhau.
5. Bản xuất/copy báo cáo lấy cùng DTO/summary với phần hiển thị; không còn đoạn xuất gọi score là xác suất hay đọc tham số hiện tại để diễn giải vé lịch sử.

Nghiệm thu: đổi POWER → MEGA, đổi ngày/thuật toán liên tục trong mạng chậm không trộn dữ liệu; component cha và con cùng context đã áp dụng; lỗi nguồn phụ không làm mất kết quả/vé hợp lệ. Kiểm tra desktop và mobile 390 px, thao tác bàn phím và trạng thái dữ liệu trống.

### Đợt 5 — Đánh giá cải thiện và thao tác cấu hình (P1/P2)

1. Tách “xuất/lưu báo cáo đối soát” khỏi “áp dụng cấu hình”. Báo cáo đơn thuần không đi qua `update-algorithm`. Bản chỉnh tham số phải liệt kê khóa mô hình thực sự đọc, cấu hình trước/sau và kết quả đánh giá; giữ các trường không chỉnh, kiểm tra kiểu/miền và không nhận `evaluationSummary` như tham số mô hình.
2. Không tự chọn trọng số, ưu tiên số gan hoặc đổi sang Logistic vì kỳ 29/10. Giữ cấu hình mặc định trong đợt sửa trang; nêu rõ lưu config không đồng nghĩa huấn luyện lại.
3. Báo cáo riêng từng loại và từng phiên bản: số khớp trung bình/vé, tỷ lệ kỳ có vé >=3 và >=4, vé tốt nhất/nhóm, độ phủ, số vé và số kỳ. So sánh cùng kỳ, ngân sách vé khác nhau đã loại trùng và snapshot đủ điều kiện; không gộp vé trên một kỳ thành nhiều kỳ độc lập.
4. Thử nghiệm giảm trùng hoặc mô hình khác là bước tiếp theo có phiên bản: dùng dữ liệu phát triển để đề xuất, chốt trước chính sách chọn/seed/cấu hình/metric/cỡ mẫu và mốc đánh giá tương lai. Báo cáo cả số khớp lẫn độ phủ để không tăng độ phủ bằng cách hy sinh vé tốt nhất mà không biết.
5. Đối chứng tần suất và ngẫu nhiên cùng ngân sách; dùng cơ chế controls đã có, không chọn seed theo kết quả. Nêu độ bất định theo kỳ; nếu thêm nhiều lượt Monte Carlo, tách chúng khỏi số kỳ quan sát thực.

Nghiệm thu: có thể kết luận “chưa đủ dữ liệu/chưa hơn đối chứng”; không đổi mẫu số hoặc chọn riêng vé/mô hình thắng sau khi thấy kết quả. Đợt 1–4 có thể ra mắt khi đạt kiểm chứng tính đúng, không phải chờ mô hình mới chứng minh lợi thế.

## 5. Điểm sửa chính trong repo

| Khu vực | Công việc dự kiến |
| --- | --- |
| `controller/AnalyzeController.java`, `service/AnalyzeService.java` | Adapter API cũ; chuyển báo cáo sang service riêng; giữ scoring có thể tái sử dụng |
| `service/DrawAnalysisService.java`, `dto/` (mới) | Context, nguồn bằng chứng, report có kiểu và trạng thái thiếu dữ liệu |
| `ForecastProtocol`, `ForecastService`, `WorkspaceService`, AI Lab | Đọc snapshot theo id/kỳ và lưu chi tiết mới khi cần; không dựng lại lịch sử đã chốt |
| `WorkspaceProtocol`, helper dò vé | Tính số khớp/độ phủ/ma trận thống nhất; giữ semantics hiện có |
| `core/models/`, `core/services/analyze.service.ts` | DTO và phương thức đọc báo cáo riêng với tạo prediction |
| `feature/latest-draw-analysis/` | Context request, báo cáo từng vé/nhóm, chi tiết nguồn, export và thao tác cấu hình |
| Java tests, Angular specs | Hợp đồng API, fixture đối chiếu, snapshot cũ/mới, hủy request và luồng đọc không ghi |

## 6. Kiểm chứng và đưa vào sử dụng

- Unit/API tests cho đúng algorithm/source, dữ liệu rỗng, snapshot thiếu rank, không dùng kết quả D để giải thích trước D, revision kết quả bị sửa/cách ly và null metrics.
- Integration test trên PostgreSQL tạm cho adapter đọc snapshot, JSON cũ/mới, revision batch có hiệu lực và mở/refresh báo cáo không tạo bản ghi. Không gọi mock repository là kiểm thử PostgreSQL.
- Angular tests về context/hủy request, nhãn nguồn, summary/export nhất quán, không gọi POST tạo suggestion và thao tác nhập vé.
- Kiểm tra cutoff 17:59:59/18:00 và nhận kết quả 18:59:59/19:00 theo Asia/Ho_Chi_Minh, thiếu feed và đổi tháng/năm.
- Đo số request, số lần scoring và thời gian phản hồi trước/sau trên cùng bộ dữ liệu. GET báo cáo không chạy prediction mới; cache nếu cần phải phụ thuộc context, snapshot id và fingerprint kết quả để không dùng revision lỗi thời. Không cam kết ngưỡng thời gian khi chưa có baseline.
- Khi triển khai code: chạy typecheck/format và tests phù hợp, `npm run build`, `mvn clean install`; đối chiếu assets Angular trong JAR. Kiểm tra môi trường Node đáp ứng dependencies hiện có.
- Smoke test staging trước khi deploy: mở kỳ 29/10 thấy đúng năm vé, nguồn không bị gán nhầm và thiếu giải thích cũ được ghi rõ. Kiểm tra số bản ghi trước/sau luồng chỉ xem; chuẩn bị quay lại bản ứng dụng trước nếu contract lỗi, giữ nguyên snapshot/audit đã lưu. Migration nếu có phải tương thích ngược.

Đầu ra mỗi đợt: diff có thể review, bằng chứng checks thực sự đã chạy, giới hạn còn lại và cập nhật `PROJECT_ANALYSIS.md`. Các mục trên giữ vai trò tiêu chí nghiệm thu; kết quả thực thi được cập nhật trong tài liệu hiện trạng, không thay đổi dữ liệu hoặc cấu hình production.

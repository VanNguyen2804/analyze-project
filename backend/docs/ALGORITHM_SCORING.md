# Quy tắc scoring sau cập nhật 02/11/2026

Các thay đổi này sửa cách dùng dữ liệu và cấu hình, chưa chứng minh tăng khả năng dự đoán. Công thức graph mới là heuristic có phiên bản; không coi điểm sigmoid hoặc điểm liên kết cặp là xác suất trúng đã hiệu chuẩn.

## Phiên bản và phạm vi

| Scorer | Phiên bản mới | Thay đổi |
| --- | --- | --- |
| `temporal_learning` | `temporal-links-v3` | MEGA chỉ học/kết hợp tám expert; POWER giữ chín expert. |
| `bayesian_graph` | `bayesian-pair-excess-v2` | Dùng phần đồng xuất hiện vượt kỳ vọng thay cho tổng hàng ma trận cặp. |
| `deep_stacking` | `copula-scheduled-v2` | Chỉ học chuyển tiếp giữa các kỳ liên tiếp theo lịch, dùng đúng số lần có nguồn–đích hợp lệ làm mẫu số. |

Hai lượt scoring dùng cùng snapshot history và cùng feature đã tính. Kết quả chính/phụ, workspace, frozen explanation và forecast mang phiên bản tương ứng. Fingerprint mã bao gồm các helper domain mới. Snapshot cũ giữ nguyên; cùng khóa kỳ/thuật toán vẫn giữ bản đầu tiên đã chốt, không ghi đè hoặc sinh bù bằng scorer mới.

## Hợp đồng cấu hình

`POST /api/analyze/update-algorithm` từ chối patch chứa `poissonGapMinRatio`, `poissonGapMaxRatio` hoặc `repeatExhaustionPenalty` bằng HTTP 400, nêu đúng khóa. Đây là các khóa không được scorer dùng. Bản cấu hình lịch sử vẫn đọc được và các giá trị cũ được giữ khi merge một patch hợp lệ; không sửa migration hoặc xóa cấu hình cũ. Kiểm tra khoảng min/max đã bỏ cùng với hai khóa gap cũ, nên giá trị lịch sử không còn cản việc sửa một tham số đang dùng.

| Tham số graph | Mặc định | Miền patch hợp lệ |
| --- | ---: | --- |
| `coOccurrenceWeight` | 0.85 | Số hữu hạn trong [0,100]; 0 tắt đóng góp graph vào điểm Bayes. |
| `pairGraphPriorStrength` | 8 | Số hữu hạn trong [0.01,10000]. |
| `pairGraphMinSupport` | 3 | Số nguyên trong [1,1000]. |

Các khóa có thể nằm ở root hoặc trong cấu trúc `recommendedAdjustments` hiện có. Khi cấu hình đã lưu thiếu hoặc sai kiểu/miền cho hai tham số graph mới, scorer dùng mặc định; giá trị support phân số không bị cắt cụt thành số nguyên. Các tham số temporal và các khóa còn được scorer dùng giữ hợp đồng hiện có. Không phải mọi tham số áp dụng cho mọi thuật toán: graph dùng ở Bayes/heuristic tổng quát, temporal dùng các tham số temporal.

## Điểm liên kết cặp

`PairRelationshipModel` nhận feature của lịch sử hợp lệ đã chốt trước kỳ đích:

- `T`: số kỳ trong history; `N`: 45 hoặc 55.
- `f_i`, `f_j`: số kỳ xuất hiện từng số; `C_ij`: số kỳ có cả hai.
- Context là sáu số của kỳ mới nhất trong history, bỏ chính candidate, không lấy kết quả kỳ đích.

Kỳ vọng heuristic là `E_ij = f_i * f_j / T * N * 5 / (6 * (N - 1))`, giới hạn trong khoảng khả thi `[max(0, f_i + f_j - T), min(f_i, f_j)]`. Hệ số hiệu chỉnh cho mức đồng đều khớp việc chọn sáu số không hoàn lại: khi đã có một số, còn năm vị trí trong `N - 1` số. Xem [mô hình lấy mẫu không hoàn lại của SciPy](https://docs.scipy.org/doc/scipy-1.14.0/reference/generated/scipy.stats.hypergeom.html). Với các marginal thực nghiệm không đồng đều, đây là một mức tham chiếu xấp xỉ do dự án thiết kế, không phải xác suất điều kiện chính xác hoặc một mô hình đã được xác nhận.

Khi `min(f_i, f_j)` đạt `pairGraphMinSupport`, residual là `(C_ij - E_ij) / (min(f_i, f_j) + pairGraphPriorStrength)`; chưa đủ support thì đóng góp bằng 0. Lấy trung bình trên toàn context khác candidate, kể cả các cặp chưa đủ support để không khuếch đại một cặp ít mẫu. Cặp không quan sát được có thể mang residual âm. Context có một số xuất hiện trong mọi kỳ không tự tạo bằng chứng liên kết. Tại mức đồng đều, feature có kỳ vọng 0 thay vì đếm lặp tần suất.

Nhánh Bayes dùng `1.8 * coOccurrenceWeight * pairGraph` thay cho graph degree cũ; các thành phần Bayes khác giữ nguyên. `allDetails.features` giữ `pairGraph` và `pairGraphSupportedPairs` từ chính lượt tính điểm. Tham số được cố định trước báo cáo phát lại, không chọn lại theo số trúng.

## Chuyển tiếp theo lịch

`DrawTransitionModel` chỉ thêm cạnh khi ngày đích đúng kỳ kế tiếp của category. Bản ghi cách ly/không hợp lệ được lọc trước; nếu việc lọc tạo khoảng thiếu kỳ, không nối tắt qua khoảng đó. Mẫu số cho từng số nguồn chỉ đếm các lần số đó nằm trong một kỳ có kỳ đích liên tiếp hợp lệ; không cộng kỳ cuối chưa có đích vào mẫu số.

Giữ trọng số context 0.70 cho kỳ mới nhất và 0.30 cho kỳ trước đó. Context cũ chỉ tham gia nếu hai kỳ cuối liên tiếp theo lịch. Cả hai context đọc cùng mô hình chuyển tiếp một kỳ; không diễn giải thành xác suất Markov hai bước. `scoringDiagnostics` lưu số cặp kỳ đã học, số khoảng thiếu bị bỏ và phiên bản policy, rồi được đóng băng vào explanation.

## Expert theo category

MEGA không phân trọng số, cập nhật thành tích hoặc công bố `special_to_main` trong patterns/explanation. Trọng số ban đầu là 1/8 trên tám expert hợp lệ, được chuẩn hóa sau mỗi lượt học. POWER giữ 1/9 trên chín expert. Giữ cơ chế dự đoán trước khi cập nhật nhãn, mục tiêu số chính khớp, fallback tần suất và kiểm tra khoảng thiếu đã có.

## Kiểm chứng và giới hạn

Regression tests kiểm tra HTTP 400 trước khi ghi cấu hình, cấu hình lịch sử còn đọc được, dữ liệu rỗng/sai/đảo thứ tự, cặp có cùng tần suất nhưng quan hệ khác nhau, mức đồng đều chọn sáu số, support/làm trơn, khoảng thiếu, mẫu số chuyển tiếp, expert theo category, phiên bản của cả hai lượt scoring và explanation đã lưu. `OnionArchitectureTest` bảo vệ chiều phụ thuộc; các phép tính mới ở domain thuần Java.

`AlgorithmRevisionReportTest` là phát lại phát triển có điều kiện. Chạy cùng file test trên baseline và revision mới bằng `mvn test -Dtest=AlgorithmRevisionReportTest -Dalgorithm.revision.report=<output.json>`. History lấy từ resource trong repository, lọc qua `DrawQuality`, khởi tạo mười kỳ, đo một vé mỗi thuật toán mỗi kỳ. Đối chứng gồm top sáu tần suất và trung bình 100 vé ngẫu nhiên có seed cố định theo protocol/category/ngày/replicate. Các lần ngẫu nhiên không được tính thành kỳ độc lập. Chi tiết và giới hạn mẫu tại [báo cáo cập nhật](reports/algorithm-revision-2026-11-02.md).

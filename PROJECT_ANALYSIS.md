# Hiện trạng và hướng phát triển dự án

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

Nguồn mặc định: `vietvudanh/vietlott-data`, không cần API key; `power645.jsonl` là MEGA, `power655.jsonl` là POWER. Scheduler chờ 10 giây sau khởi động, sau đó 900 giây giữa các lần hoàn tất; mặc định đối soát 90 ngày. Chỉ nhận ngày hiện tại sau 18:30 giờ Việt Nam, không nhận ngày tương lai. Có transaction/advisory lock và ghi audit; lỗi từng loại được xử lý độc lập.

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
- Sau 18:30, đối soát với kết quả mới nhất hợp lệ. Các sự kiện `PENDING`, `SETTLED`, `RESULT_UNAVAILABLE` được ghi thêm, không sửa snapshot. Kết quả đổi/cách ly sẽ cập nhật thống kê theo sự kiện mới nhất; chạy lại cùng trạng thái không tạo trùng.
- Chỉ đo snapshot có thời gian/dấu dữ liệu hợp lệ và trạng thái mới nhất `SETTLED`. Tách nhóm theo loại, thuật toán, phiên bản mã/mô hình và cấu hình. Không dùng backtest hoặc snapshot chưa có kết quả làm mẫu đã đo.
- Báo cáo số khớp TB, phân bố 0–6, tỷ lệ từ 3 số, hai đối chứng và chênh lệch ghép cặp với khoảng 95% xấp xỉ. Không tự chọn/tối ưu cấu hình từ nhóm này. Nhãn dưới 30 kỳ chỉ là quy ước mẫu nhỏ, không phải ngưỡng chứng minh hiệu quả.

PostgreSQL có `forecast_snapshots` và `forecast_settlements` qua `V11__add_forecast_ledger.sql`; V10 là migration schema audit dữ liệu. Không sửa migration lịch sử để chèn dự đoán. `FORECAST_ENABLED=false` tắt tạo snapshot mới nhưng vẫn cho xem/đối soát bản cũ.

| API | Chức năng |
| --- | --- |
| `GET /api/forecasts` | Xem snapshot, sự kiện, trạng thái; cập nhật đối soát từ kết quả đã lưu |
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
| P0 | Chạy backend thực tế qua các kỳ tương lai | Chốt trước 18:00, đối soát sau 18:30, tích lũy bằng chứng độc lập |
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

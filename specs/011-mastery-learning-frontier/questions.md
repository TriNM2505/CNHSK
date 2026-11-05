# Chốt — Spec 011

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md` §12, constitution v1.4.1 (AC-04, AC-09, AC-10, BUS-04/05/08/09/16), spec 003 §0 (engine, sự kiện), `SessionFinishedEvent`, `ItemGradedEvent`, `ChoiceItemFactory`, `TopicStudyService`, `SecurityConfig` (`/api/me/**`), migration V1 (`user_progress`), `UserProgress` (005), `database.md` §4.9/§13, UC-042…UC-055, `screen-fields.md` SCR-010/SCR-013/SCR-019, `Caytrithuc/01-architecture-overview`, `02-dev-integration-spec`, `03-ba-spec`, plan `261017-1654` (phase 1, 4, báo cáo `brainstorm-261018-2108`, bộ tính `cay-tri-thuc-cnhsk.html`), plan `261102-2104` phase 3, spec 010 và 012 (bản trên máy).

Đã tự tính lại ví dụ Quốc Bảo NP3-004 theo bộ số v2.0: Σw = 2,407, mastery 53,30; thêm thi 80 → 61,13. Khớp đặc tả.

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Lưu mastery | **0 bảng mới**: V6 thêm `mp_state`, `confidence`, `evidence_item_ids BIGINT[]` vào `user_progress`; minh chứng = `attempt_items` |
| Q2 | Trọng số | **Theo v2.0**: bối cảnh (thi 1,0 · quiz/chẩn đoán 0,8 · GV chấm 0,6 · luyện/game 0,5) × dạng câu (tự luận 1 · TN4 0,75 · TN3 0,67 · đúng/sai 0,5) × vai trò (chính 1 · phụ 0,5 · mã lỗi 1) |
| Q3 | Biên học tập | **Tiên quyết đạt củng cố trở lên (≥ 60)** — giữ red-team #1; chỉ gợi ý + tô màu |
| Q4 | Tầng mục từ, SCR-013 | **Làm FSRS + SCR-013** |
| Q5 | Trạng thái `inferred` | **Có**, theo v2.0 |
| Q6 | Luật 7 ngày | **Giữ** |
| Q7 | Phạm vi màn | **Chỉ phần cây**: SCR-019, khối gợi ý + ôn tập SCR-010, SCR-013 |
| Q8 | Thang | **0–100, vững ≥ 80** |
| F1 | Tham số FSRS | **FSRS-5 mặc định**, retention 0,9, 1–365 ngày, UTC, thiếu cấu hình không khởi động |
| F2 | Rating | **Đúng = Good, sai = Again**; chỉ thẻ tự đánh giá chọn 4 mức |
| F3 | Vào lịch ôn | **Học ở `TOPIC_STUDY` hoặc lần đầu trả lời câu có chấm** |
| F4 | Ôn → mastery | **Câu chọn tính minh chứng luyện (0,5)**; thẻ tự đánh giá không |
| D | Mặc định | **Duyệt**: minh chứng ở `SessionFinishedEvent` (bỏ dở/hết hạn không tính); biên không khoá nội dung, cổng 90% giữ; không minh chứng từ luyện viết (`BUS-16`) và phát âm; V6 thêm cột + `'LEXEME'` vào CHECK; FE trước; chỉ file trên máy |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| Y1 | AC-04 (22 bảng `learning`) ↔ v2.0 dùng bảng `evidence`, `mastery_current` | Q1 |
| Y2 | UC-042 (0–1), UC-044 (FSRS cho knowledge point), UC-051/SCR-019 ("đã thuộc ≥ 0,9") ↔ v2.0 MP-1.0 (0–100, ≥ 80) | Q8; RFC Q1 |
| Y3 | Trọng số plan phase 3 (test 1 · chẩn đoán 1 · luyện 0,6 · game 0,5) ↔ v2.0 | Q2; sửa plan phase 3 |
| Y4 | Biên v2.0 (tiên quyết `extend`/`inferred`) ↔ red-team #1 (`consolidate`) | Q3; RFC |
| Y5 | Plan phase 3 gán SCR-013 là "KID cần ôn" ↔ screen-fields: SCR-013 ôn mục từ FSRS | Theo screen-fields (Q4) |
| Y6 | `BUS-16` luyện viết chưa chốt; phát âm chưa nối KID | Không minh chứng, không FSRS |
| Y7 | Plan phase 3 định sửa `PracticeService.finish` | Listener `SessionFinishedEvent` (003 §0.9) |
| Y8 | `/api/me/**` đang mở cho mọi role đăng nhập ↔ 011 chỉ `LEARNER` | Dùng `/api/mastery/**`, `/api/reviews/**` |
| Y9 | Màu v2.0 (BA spec): đỏ cho cả `insufficient` | Giữ màu v2.0 nhưng nhãn chữ "Chưa đủ minh chứng" khác "Cần ôn"; người mới toàn `notyet` (xám) |

### 2026-11-03 — Xác nhận thêm

| # | Câu hỏi | Leader chốt |
|---|---|---|
| D2 | SCR-013 ôn dở rồi tắt app | **Tính ngay từng câu** (`DUE_REVIEW` xử lý ở `ItemGradedEvent`); chỉ áp dụng màn ôn từ |
| OQ-2 | 19 trọng số FSRS-5 | **Giữ nguyên số trong spec**, không đối chiếu |
| — | Sửa plan phase 3, 4 cho khớp spec | **Sửa ngay** (đã sửa) |

### Còn mở

- OQ-1: tên tiếng Việt 6 miền (010 OQ-1).

## 2026-11-04 — Leader duyệt Shadow Plan 011/T-01 (Dũng, luồng C)

| # | Nội dung | Leader chốt |
|---|---|---|
| T01-1 | Type practice dùng chung | Chỉ thêm `DUE_REVIEW` vào cuối `PracticeSource` trong `features/practice/types.ts`. `FlashcardRating` (`AGAIN`, `HARD`, `GOOD`, `EASY`) đặt ở `features/mastery/types.ts`; nhận diện thẻ bằng literal `item.type === 'FLASHCARD_RATING'`. Giữ cấu trúc type của nguồn cũ. |
| T01-2 | Đăng ký preview ngay T-01 | `catalog.preview.ts` export fixture tự soạn và `previews = [] satisfies readonly PreviewEntry[]`; thêm import/spread vào `app/preview/registry.ts`. Catalog màn hình rỗng, ba route dùng fallback sẵn có đến T-02–T-04. |

Allowlist đã duyệt: 14 file, gồm 11 file trong Shadow Plan v2 và `app/preview/registry.ts`, `tasks.md`, `questions.md`. Leader cho phép đánh ✅ T-01 sau gate. Ba route `/learn`, `/learn/review`, `/learn/progress` dùng `LEARNER`, layout `learner`; OQ-1 giữ mã miền. Làm trên worktree riêng từ `origin/main` (base `5aaf744`), nhánh `spec/011-t01`; một conventional commit và PR Draft, leader merge. Thay đổi dở `CharacterQueryRepository.java` ở checkout gốc không thuộc task này. Thứ tự tiếp theo: T-06 rồi T-11, mỗi task có Shadow Plan riêng.

### Kết quả gate T-01

- Base sau rebase: `c3a5fc7` (origin/main). Các cập nhật base không đổi cây mã frontend đã kiểm thử.
- Backend: `.\mvnw.cmd -B verify` — BUILD SUCCESS; 2.167 test, 0 failure, 0 error, 0 skip; JaCoCo line coverage 96,55% (6.376/6.604 dòng), ngưỡng 80% giữ nguyên. Testcontainers chạy hạ tầng riêng.
- Lượt verify trước bị JVM dừng vì thiếu bộ nhớ native. Lượt phục hồi dùng biến môi trường chỉ trong tiến trình: `JAVA_TOOL_OPTIONS=-Xmx1024m`, `MAVEN_OPTS=-Xmx384m`; không đổi cấu hình repo hoặc bỏ test.
- Frontend: `npm run test` — 1.547 test/135 file pass; `npm run lint` — exit 0, 0 error, 1 warning có sẵn tại `shared/ui/LevelBadge.tsx`; `npm run build` — pass.
- T-01 thêm 52 test trong 3 file cho transport API, fixture và quyền route. AT-01 chỉ phủ phần FE/transport; AT-24–26 thuộc T-02–T-04, AT-27 thuộc T-15.
- Self-check: đúng allowlist 14 file; không đổi dependency, schema, OpenAPI, mã lỗi hoặc SecurityConfig; không thêm secret/dữ liệu thật, BOM hoặc TODO. OQ-1 vẫn giữ mã miền.

## 2026-11-04 — Leader duyệt Shadow Plan 011/T-05 (Dũng, luồng C)

- Ưu tiên T-05 trước T-02 để mở đường T-07, T-09; không tự chuyển task sau.
- Allowlist 10 file: V6, hai entity, hai repository, hai file test, `database.md`, `tasks.md`, `questions.md`. Đánh ✅ T-05 sau khi gate xanh theo duyệt của leader.
- V6 chép nguyên khối §0.1 và có test đối chiếu nội dung với spec. Lệch thì dừng báo leader, không tự sửa SQL khóa; không sửa V1–V5.
- Hai entity chỉ map cột mình dùng trong `learning.user_progress`, dùng khóa ghép hiện có. Mọi query repository lọc `target_type` bằng literal và có `userId`; không thêm association hoặc FK. Cột không map để DB lấy default.
- Test nâng cấp V5 → V6 dùng container riêng theo mẫu V5. Test Spring dùng hạ tầng của `AbstractIntegrationTest`, Flyway và `ddl-auto: validate`, gồm entity 005 giữ nguyên.
- Duyệt sửa thêm đúng hai dòng thiếu tại DDL `database.md` §4.9: `CHECK (stability > 0)` và `ix_prog_type` vốn đã có trong V1. Đây là đồng bộ tài liệu, không thêm SQL ngoài khối V6. Chỉ sửa §4.9, §11, §13; giữ nguyên nội dung V5 của 010.
- Được dùng `JAVA_TOOL_OPTIONS=-Xmx1024m`, `MAVEN_OPTS=-Xmx384m`, `DEBUG=false` chỉ trong tiến trình chạy gate; ghi lại kết quả thật, không đổi cấu hình hoặc hạ coverage.
- Neon do leader thực hiện sau merge: tạo branch backup, kiểm chưa có dòng `KNOWLEDGE_POINT`, kiểm tên `user_progress_target_type_check` trước V6. Task không chạy SQL hay migration lên Neon.

## 2026-11-05 — Leader duyệt bổ sung hai file test Spec 005

- Allowlist tăng từ 10 lên 12 file, thêm `src/test/java/com/cnhsk/learning/pronunciation/PronStageHandlerIntegrationTest.java` và `src/test/java/com/cnhsk/learning/pronunciation/PronunciationIntegrationTest.java`.
- Bốn assertion snapshot `SELECT *` của 005 thất bại vì `PgArray.equals()` không so giá trị cột `evidence_item_ids` mới. Helper trong từng file chuyển mọi giá trị `java.sql.Array` thành `List` bằng `getArray()` trước khi so sánh; giữ đầy đủ cột, assertion và ý nghĩa trước bằng sau. Không sửa nghiệp vụ 005, V5 hoặc SQL V6.
- Sau fetch, base nhánh T-05 là `fa1ad24`; `origin/main` là `211e7e3`, đi trước 47 commit và chỉ có migration tới V5. Giữ nguyên base theo chỉ đạo leader, không rebase hoặc merge main vào nhánh. Leader tích hợp và chạy lại full verify trên main mới nhất khi review.
- Nếu full verify chỉ thất bại ở nhóm môi trường đã biết (FR023, NFR-P04 QUESTION, PEXPIRETIME, CapturedOutput), ghi kết quả thật trong PR; lỗi khác thì dừng báo leader. Chỉ đánh ✅ T-05 khi gate xanh.
- Trước khi chạy V6 trên Neon sau merge, leader tạo backup `before-v6-<ngày>`, kiểm số dòng `KNOWLEDGE_POINT` bằng 0 và có `user_progress_target_type_check`; task không kết nối Neon.

### Kết quả gate T-05

- Base kiểm thử: `fa1ad24`; không rebase hoặc merge main sau chỉ đạo leader. Fetch trước PR thấy `origin/main` là `96ac2aa`, vẫn chỉ có migration tới V5. Full `verify` chạy toàn repo trên Docker/Testcontainers, Flyway bật và `ddl-auto: validate` giữ nguyên.
- Hai class mới T-05: 31 test PASS (26 migration, 5 entity/repository), 0 failure/error/skip. Có đối chiếu V6 với SQL khóa tại §0.1, tên CHECK và `ck_prog_mp`, nâng cấp V5 → V6 trên container riêng, validate entity 005, round-trip và cô lập user/target type.
- Hồi quy riêng hai class 005 sau sửa helper: 41 test PASS (26 handler, 15 pronunciation), 0 failure/error/skip; `BUILD SUCCESS`, 03:48 phút. Lỗi bốn assertion snapshot ở lượt verify trước đã hết; không loại cột hoặc giảm assertion.
- Full `.\mvnw.cmd -B verify`: `BUILD SUCCESS`, exit 0, 33:34 phút; 2.363 test trong 139 class, 0 failure, 0 error, 0 skip; số liệu log khớp Surefire XML. Cả 31 test T-05 và 41 test hồi quy 005 đều PASS trong lượt full này.
- JaCoCo line coverage 96,35% (6.625/6.876 dòng); branch coverage 87,55% (2.807/3.206 nhánh). Coverage check toàn bộ ≥ 80% PASS; không đổi ngưỡng hay cấu hình test.
- Biến môi trường chỉ trong tiến trình gate: `JAVA_TOOL_OPTIONS=-Xmx1024m`, `MAVEN_OPTS=-Xmx384m`, `DEBUG=false`. Docker Desktop được khởi động để chạy container riêng; không chạy SQL lên Neon.
- Log cục bộ: `%TEMP%\cnhsk-011-t05-targeted.log`, `%TEMP%\cnhsk-011-t05-pron-regression.log`, `%TEMP%\cnhsk-011-t05-verify-resumed.log`; báo cáo Surefire và JaCoCo trong `target/`, không commit artifact/log.
- Đánh ✅ T-05 theo duyệt của leader sau gate xanh. Không có câu hỏi nghiệp vụ còn mở trong T-05; leader tích hợp với main mới nhất và kiểm thử lại trước merge. Không tự chuyển sang T-06 hoặc T-02.

## 2026-11-05 — Leader chốt H1–H7 và nghiệm thu 011/T-06

### Quyết định H1–H7

- **H1 — Cấu hình MP-1.0:** chỉ khai báo 12 khóa đã duyệt: `window`, `max-practice-in-window`, `min-evidence`, `min-distinct-days`, `min-confidence`, `half-life-days`, `review-below`, `extend-from`, `hysteresis`, và ba khóa `due-days.review`, `due-days.consolidate`, `due-days.extend`. Mặc định lần lượt là `8`, `3`, `3`, `2`, `1.0`, `30`, `60`, `80`, `3`, `3`, `7`, `14`. Các field cấu hình dùng kiểu nullable và Jakarta Validation; quan hệ chéo kiểm `max-practice-in-window`, `min-evidence`, `min-distinct-days` không vượt `window`, `0 < review-below < extend-from ≤ 100`, `0 ≤ hysteresis < review-below`. Thiếu khóa, giá trị ngoài miền hoặc quan hệ sai làm ứng dụng không khởi động. Không thêm khóa `seen-item-days`, `inferred-*`, trọng số hay cấu hình FSRS trong T-06. `Settings` là record thuần.
- **H2 — Làm tròn:** mastery làm tròn `HALF_UP` một chữ số thập phân và dùng chính giá trị đã làm tròn để so ngưỡng trạng thái. `59.94 → 59.9` vẫn `review`; `59.95 → 60.0` thành `consolidate`. Giá trị trung gian và confidence giữ độ chính xác cao.
- **H3 — Cửa sổ:** xếp `answeredAt` giảm dần, rồi `itemId` giảm dần; lấy tối đa 8 minh chứng. Bỏ qua PRACTICE vượt quá 3 nhưng tiếp tục xét minh chứng cũ hơn để lấp cửa sổ. Năm PRACTICE và năm QUIZ cho cửa sổ 3 PRACTICE + 5 QUIZ. Không sửa collection đầu vào.
- **H4 — Chuyển trạng thái:** quyết định trực tiếp theo giá trị cuối, có vùng đệm 3 điểm; không giới hạn một bậc mỗi lần. `consolidate` giữ ở 58, xuống `review` ở 56; `extend` giữ ở 78, xuống `consolidate` ở 76; `extend` có thể xuống thẳng `review` ở 56 và `review` có thể lên thẳng `extend` ở 85. `notyet`/`insufficient` dùng ngưỡng thẳng 60/80.
- **H5 — Điểm:** điểm minh chứng là số nguyên trong đoạn 0–100; cả 50 và 40 trong ví dụ §4.1 đều hợp lệ.
- **H6 — Bán rã:** đúng công thức FR-009: nghỉ lâu không làm mastery value giảm nếu điểm không đổi; chỉ confidence giảm theo bán rã. Khi confidence dưới `1.0`, trạng thái đọc là `insufficient`. E10 nói “giá trị giảm” là chưa chính xác; không sửa spec trong T-06.
- **H7 — Đầu vào:** `itemId` trùng là vi phạm điều kiện đầu vào vì đây là khóa chính `attempt_items`; calculator không tự khử trùng. Nếu `answeredAt > today`, tuổi được tính bằng 0.

Mastery tính thuần, không tự đọc đồng hồ hệ thống. Số ngày khác nhau và hạn ôn dùng `Asia/Ho_Chi_Minh`; `notyet` và `insufficient` không có hạn ôn. T-06 định nghĩa mã `inferred` tường minh nhưng không suy ra trạng thái này.

### Kết quả gate T-06

- Bốn test T-06 (`MasteryConfigTest`, `MasteryCalculatorTest`, `MasteryStateTest`, `MasteryStateMachineTest`): **21 test, 0 failure, 0 error, 0 skipped**.
- Full `verify`: **BUILD SUCCESS**, 3.124 test trong 194 suite, 0 failure, 0 error, 0 skipped.
- JaCoCo LINE: **96,94%** — 9.089 dòng covered / 9.376 tổng (287 missed). JaCoCo CLASS counter: 501 covered + 4 missed = **505 classes**. BRANCH: 4.140 covered / 4.667 tổng (527 missed); mọi coverage check đạt.
- Gate chạy trong worktree `spec/011-t06`, không dùng `.env` hoặc Neon. Chỉ sửa 11 file allowlist; không thêm dependency, migration, bảng, endpoint hay mã lỗi. Không push hoặc tạo PR.

## 2026-11-05 — Leader duyệt 011/T-11: FSRS-5 và API dùng chung với 014/T-12

Allowlist 7 file: `FsrsConfig.java`, `FsrsScheduler.java`, `FsrsConfigTest.java`, `FsrsSchedulerTest.java`, `application.yaml`, `questions.md`, `tasks.md`. Base nhánh `spec/011-t11`: `ff2c235` trên origin/main, đã có PR #106 (`e7c7453`). Chỉ đánh ✅ T-11 khi gate xanh.

### Bảy quyết định đã chốt

1. **Thời gian:** `t` là số ngày lẻ giữa hai `Instant` UTC, tính cả giây/nanosecond. So `Duration` trực tiếp: dưới 24 giờ dùng nhánh ngắn hạn; đúng 24 giờ trở lên dùng nhánh dài hạn. `nextReviewAt = now + intervalDays` ngày UTC, không đọc đồng hồ hệ thống.
2. **Ôn ngắn hạn:** S′ dùng công thức ngắn hạn; D′ vẫn cập nhật theo công thức chung; mọi lần `review` với AGAIN tăng `lapseCount`, kể cả trong ngày. FR-019 chưa nói rõ hai hành vi sau trong nhánh ngắn hạn; đây là quyết định bổ sung của leader, không phải tự suy diễn từ spec.
3. **Lần đầu:** `first` trả `lapseCount = 0` với cả bốn rating; lần học đầu chưa tính là quên.
4. **Đầu vào:** Memory yêu cầu stability dương/hữu hạn, difficulty trong [1,10], lapseCount không âm; sai ném `IllegalArgumentException`. Nếu `now < lastReviewAt`, coi thời gian đã qua bằng 0, dùng nhánh ngắn hạn để xử lý đồng hồ lệch nhẹ, giống H7 của T-06.
5. **Weights:** đúng 19 số hữu hạn và chỉ w0…w3 phải dương; không thêm giới hạn cho các weights còn lại. Không kẹp stability. Nếu S′ tính được không hữu hạn hoặc không dương, ném `IllegalStateException`. D0(4) phải lấy giá trị chưa kẹp `w4 − exp(3 × w5) + 1`, sau đó mới kẹp D′ vào [1,10]. Dãy mặc định giữ nguyên §0.5.
6. **API:** leader duyệt các chữ ký bên dưới, khóa khi merge để 014/T-12 dùng. Scheduler không làm tròn S/D theo cột DB; phần làm tròn theo độ chính xác cột DB thuộc 014/T-12 (XuanHuy). Chỉ khoảng ôn được làm tròn HALF_UP, theo công thức tổng quát của `desired-retention`, rồi giới hạn trong min/max; retention 0,9 tương đương HALF_UP(S′).
7. **Bàn giao:** commit trên máy mượn, xuất Git bundle + SHA-256; trên máy được phép push phải kiểm `git bundle verify`, SHA-256, đúng 7 file và tác giả, rồi push nhánh `spec/011-t11` và tạo Draft PR. Không push từ máy mượn, không dùng `.env`, không chạy SQL/migration lên Neon, không sửa ACL hay cấu hình hệ thống.

### Public API đã duyệt, khóa khi merge

Package `com.cnhsk.learning.review`. `FsrsScheduler` là public final, constructor `public FsrsScheduler(FsrsConfig.Settings settings)`, hai method `public Result first(Rating g, Instant now)` và `public Result review(Memory prev, Rating g, Instant now)`.

- `FsrsScheduler.Rating`: public enum `AGAIN`, `HARD`, `GOOD`, `EASY`, có `public int grade()` tương ứng 1/2/3/4.
- `FsrsScheduler.Memory`: public record `(BigDecimal stability, BigDecimal difficulty, Instant lastReviewAt, int lapseCount)`.
- `FsrsScheduler.Result`: public record `(BigDecimal stability, BigDecimal difficulty, int lapseCount, int intervalDays, Instant nextReviewAt)`.
- `FsrsConfig.Settings`: public record `(List<Double> weights, BigDecimal desiredRetention, int minIntervalDays, int maxIntervalDays)`, sao chép list bất biến. Lấy bằng `FsrsConfig.settings()`; bản thân scheduler và các record không cần Spring context, repository hay DB.
- `cnhsk.fsrs`: chỉ `weights`, `desired-retention`, `min-interval-days`, `max-interval-days`, `session-max-items`. Cấu hình thiếu/sai chặn startup. `session-max-items` mặc định 50, phải dương, không tham gia phép tính scheduler.

### Backlog T-06 — chỉ ghi nhận

`MasteryConfig` hiện chưa chặn trường hợp `extend-from − hysteresis < review-below`. Leader yêu cầu ghi nhận trong T-11; chưa sửa logic hoặc test T-06. Việc bổ sung điều kiện này cần duyệt riêng.

### Kết quả gate T-11

- Base kiểm thử và fetch sau gate: `ff2c235`, không đổi base trong lúc chạy test. Worktree không có `.env`; không kết nối Neon.
- Targeted `FsrsConfigTest,FsrsSchedulerTest`: **BUILD SUCCESS**, exit 0, 34,372 giây; **73 test** (36 cấu hình, 37 scheduler), 0 failure, 0 error, 0 skipped. Có 2.000 lượt ôn với seed cố định, golden value FR-019, biên 24 giờ/đồng hồ lệch, D0(4) chưa kẹp và lỗi stability từ weights fixture.
- Full `.\mvnw.cmd -B verify`: **BUILD SUCCESS**, exit 0, **13:56 phút**; **3.328 test trong 202 suite**, 0 failure, 0 error, 0 skipped. Số liệu log khớp Surefire XML; cả 73 test T-11 PASS trong lượt full.
- JaCoCo LINE **96,99%** — **9.423/9.715 dòng** (292 missed); BRANCH **89,00%** — 4.355/4.893 nhánh (538 missed). Mọi coverage check đạt, ngưỡng LINE toàn bộ ≥ 80% giữ nguyên.
- Sandbox không truy cập được parent POM Maven và Docker pipe. Chạy targeted/full bằng PowerShell ngoài sandbox theo quyền được cấp; Docker server 29.9.1, PostgreSQL/Redis/Mailpit qua Testcontainers. Không sửa ACL hoặc cấu hình hệ thống, không bỏ test, Flyway và Hibernate validation giữ nguyên.
- Biến môi trường chỉ trong tiến trình gate: `JAVA_TOOL_OPTIONS=-Xmx1024m`, `MAVEN_OPTS=-Xmx384m`, `DEBUG=false`. Log cục bộ `%TEMP%\cnhsk-011-t11-targeted.log`, `%TEMP%\cnhsk-011-t11-verify.log`; report trong `target/surefire-reports` và `target/site/jacoco`, không commit log/artifact.
- Đánh ✅ T-11 sau gate xanh theo duyệt. Self-check: đúng allowlist 7 file, không dependency/schema/migration/endpoint/mã lỗi mới, không sửa T-06 hoặc `application-test.yaml`, không BOM/secret/dữ liệu thật. Thay đổi dở `CharacterQueryRepository.java` ở checkout gốc giữ nguyên. Commit và bundle chỉ thực hiện tại bước bàn giao; không push từ máy mượn.

# Chốt — Spec 011

## 2026-11-05 — Leader duyệt 011/T-07

- Q-T07-01: thêm `seen-item-days` (nguyên, ≥ 1, mặc định 7) và đúng các khóa trọng số §0.4; thiếu/thừa khóa hoặc trọng số ngoài (0,1] chặn startup. Giữ nguyên record Settings T-06, không thêm inferred-*.
- Q-T07-02: cửa sổ lặp là khoảng mở (answered_at − 168 giờ UTC, answered_at); đúng 168 giờ không chặn, câu trước trong cùng lượt cũng tính.
- Q-T07-03: mọi câu trả lời đúng trước đó của cùng người chặn câu hiện tại, dù lượt suspicious/ABANDONED/EXPIRED hoặc meta không hợp lệ. Câu hiện tại đúng hay sai đều bị bỏ; E5 chỉ nêu ví dụ “làm đúng lại”. Một truy vấn lịch sử cho cả lượt.
- Q-T07-04: ánh xạ tên DUE_REVIEW → PRACTICE để dùng sau T-12; listener finish bỏ qua. Không thêm enum hoặc test tích hợp nguồn này trong T-07.
- Q-T07-05: phân bổ lại nhãn hiện tại cho ID cũ; không còn thuộc KID đang cập nhật thì bỏ, không tự bổ sung KID khác. T-08 rebuild bổ sung sau.
- Q-T07-06: chỉ bảo vệ cạnh tranh cùng KID theo E7; không thêm khóa user/câu. Khác KID có thể tính trùng khi cạnh tranh; T-08 rebuild xử lý.
- Leader duyệt so w_static bằng giá trị BigDecimal (compareTo), không khóa scale và không làm tròn thêm. EvidenceAllocatorTest đổi assertion 0.6/0.3/1.0/0.25 sang isEqualByComparingTo; bổ sung nghiệm thu SECONDARY 0.125. Giá trị kỳ vọng không đổi.
- Allowlist 11 file; một commit và Draft PR từ máy chính, không Git bundle; không chạy SQL/migration trên Neon.

### Kết quả gate T-07 (đối chiếu log ngày 2026-11-06)

- Base kiểm thử `df81d04`. Fetch trước bàn giao thấy origin/main `a0ca1fd` (PR #109/#110); không đổi learning/mastery, PracticeService, SessionFinishedEvent, V5/V6 hoặc application.yaml, không trùng allowlist. Không rebase/merge.
- Targeted đúng sáu class đã duyệt: BUILD SUCCESS, exit 0, 28 test, 0 failure/error/skip; 01:18 phút. AT-12 hai finish cùng KID và hai KID đảo thứ tự PASS; E8 lỗi ghi KID thứ hai rollback tiến độ, items, study_events, trạng thái lượt PASS. NFR-P01: 10 câu và 50 câu cùng 12 truy vấn đọc.
- Full `.\mvnw.cmd -B verify`: BUILD SUCCESS, exit 0, 47:35 phút, kết thúc 2026-11-05 19:16:42 +07:00; 3.342 test trong 205 suite, 0 failure/error/skip (Surefire XML khớp log). Không có báo cáo Failsafe riêng.
- JaCoCo LINE 96,8753% = 9.673 covered / 9.985 tổng (312 missed); BRANCH 88,7179% = 4.498/5.070. Mọi coverage check PASS, giữ ngưỡng toàn bộ 80%.
- Docker/Testcontainers, Flyway và Hibernate validate hoạt động. Biến môi trường chỉ tiến trình: DEBUG=false, JAVA_TOOL_OPTIONS=-Xmx1024m, MAVEN_OPTS=-Xmx384m. Log `%TEMP%\cnhsk-011-t07-targeted.log`, `%TEMP%\cnhsk-011-t07-verify.log`; không commit log/report.
- EvidenceAllocatorTest đổi so sánh trọng số từ BigDecimal.equals sang isEqualByComparingTo theo leader: 0.6/0.3/1.0/0.25 không đổi giá trị; test riêng 0.125 PASS. Không khóa scale, không làm tròn thêm. Code T-07 không dùng BigDecimal.equals hoặc BigDecimal làm khóa Map/Set.
- Đánh ✅ riêng T-07 sau gate xanh theo duyệt. Không sửa backlog MasteryConfig của T-06; giới hạn Q-T07-06 giữ nguyên, không có câu hỏi T-07 mới.

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

## 2026-11-06 — Chốt và nghiệm thu 011/T-09

### Các quyết định Q-T09 đã được leader duyệt

1. **Q-T09-01 — Trạng thái khi đọc:** tính lại value/confidence bằng `clock.instant()`, rồi gọi `MasteryStateMachine.transition` với `mp_state` đã lưu làm trạng thái trước. GET không ghi DB. `dueAt` trả `next_review_at` đã lưu cho `review`/`consolidate`/`extend`; các trạng thái `notyet`/`insufficient`/`inferred` trả `null`.
2. **Q-T09-02 — MasteryReader:** khóa API `learning.mastery.MasteryReader.valuesOf(long userId, Set<String> kids) → Map<String, MasteryReader.MasteryView>`, với record lồng `MasteryView(Integer value, String state)`. Tập rỗng trả map rỗng, không truy vấn. Mọi KID được yêu cầu có khóa; KID chưa có dữ liệu hoặc không tồn tại trong cây trả `null/notyet`.
3. **Q-T09-03 — Làm tròn và inferred:** value làm tròn HALF_UP từ value đã làm tròn một chữ số (làm tròn hai lần là chủ đích). `inferred-min-confidence > 0`; `inferred-from` thuộc `(0,100]`; ngưỡng so sánh dùng `≥`; cấu hình sai chặn startup.
4. **Q-T09-04 — Truy vấn và API:** GET `/api/mastery` dùng ba truy vấn theo AT-16; phân bổ lại evidence thực hiện trong bộ nhớ bằng hàm thuần dùng chung tách từ `EvidenceAllocator`. `MasteryReader` tối đa ba truy vấn, không tăng theo số KID. Security matcher `/api/mastery` và `/api/mastery/**` dành cho LEARNER, đứng trước `.authenticated()`. Giữ mã lỗi `KNOWLEDGE_NODE_NOT_FOUND` hiện có; không sửa OpenAPI, calculator, state machine hoặc migration. Kiểu integer/int64 phải khớp OpenAPI.
5. **Q-T09-05 — Đánh dấu hoàn tất:** được đánh ✅ T-09 khi gate xanh và ghi số liệu gate tại tasks.md.

### Ghi nhận điều tra fixture integration test

Các fixture gọi `PracticeService.start()` trong test class `@Transactional`, trong khi `PracticeService.finish()` mở transaction `REQUIRES_NEW`. Session chưa commit vì vậy không hiển thị với transaction finish, dẫn đến `notOwned`. Hai test class đã bỏ transaction bao ngoài và dọn fixture sau mỗi test; không sửa production code, assertion hay expected result. Integration fixture T-07 được dọn có giới hạn trong Testcontainers để dữ liệu còn lại không làm sai số lượng KID của AT-16.

### Kết quả gate T-09

- Targeted regression: **49 test, 0 failure, 0 error, 0 skipped**, exit 0; gồm OpenAPI contract, SecurityConfig và các regression mastery/T-07.
- Full `mvnw verify`: **BUILD SUCCESS**, **3.499 test**, 0 failure, 0 error, 0 skipped, exit 0. AT-12/E8/NFR-P01 được chạy lại trong full suite; không phát hiện lỗi.
- JaCoCo LINE: **96,78%** — **10.190/10.529 dòng** (339 missed); mọi coverage check đạt.
- Trong lượt full verify, origin/main được fetch từ `74f1c67` lên `83a815f`. Thay đổi `application.yaml` trên main chỉ thêm khối `cnhsk.ai`; khối `cnhsk.mastery` của T-09 nằm ở vùng riêng, không chồng lấn. Không rebase/merge main.

## 2026-11-06 — Chốt 011/T-08

1. `MasteryRebuilder.rebuild(userId)` chỉ được gọi trong test và bảo trì; không API, scheduled job hay tự kích hoạt khi nhãn đổi. Người bảo trì chọn người học cần chạy.
2. `MasteryUpdater.update(userId, fresh, at)` nhận thời điểm phát lại; overload hiện có gọi overload này với `clock.instant()` để giữ nguyên hành vi finish. Rebuild truyền `attempt.submittedAt`.
3. Cập nhật mastery và rebuild khóa transaction theo user bằng `pg_advisory_xact_lock(hashtextextended('mastery:user:' || userId, 0))` trước INSERT/FOR UPDATE; sau đó vẫn khóa progress row theo `knowledge_points.id` tăng dần. Việc thêm một truy vấn khóa mỗi finish không làm NFR-P01 tăng theo số câu.
4. **Q-T08-04 thay thế:** allocator giữ nguyên và tra nhãn theo từng lượt. Rebuild cho kết quả theo nhãn tại lúc phát lại từng lượt (E9). Chỉ chạy khi bảo trì, lúc nhãn ổn định. Nếu nhãn đổi giữa chừng thì chạy lại rebuild; thao tác lặp lại an toàn vì luôn xóa rồi dựng lại từ đầu.
5. Lịch sử sắp `submitted_at ASC`, rồi `attempt.id ASC` khi thời điểm trùng. Mỗi rebuild xóa trong cùng transaction và chỉ các dòng `KNOWLEDGE_POINT` của đúng user; lỗi thì rollback.
6. AT-13 dùng 40 lượt `SUBMITTED` trộn nguồn, chưa có `DUE_REVIEW`; fixture đặt `answered_at` bằng SQL và điều khiển `Clock`. **011/T-14 bổ sung ca DUE_REVIEW cho AT-13.**
7. AT-13 so sánh `mastery`, `confidence`, `mp_state`, `evidence_item_ids` và `next_review_at` giữa finish/listener thật với rebuild. Có ca đổi nhãn câu trước khi rebuild để xác nhận E9.

### Kết quả gate T-08

- Targeted T-08 cùng regression mastery T-06/T-07/T-09 và SecurityConfig: **92 test, 0 failure, 0 error, 0 skipped**, exit 0. Riêng `MasteryRebuilderIntegrationTest`: **5 test PASS**, gồm AT-13 40 lượt trộn nguồn, rebuild lặp ổn định, đổi nhãn E9, cách ly user/FLASHCARD, rollback và finish chạy song song với rebuild trên PostgreSQL/Testcontainers.
- Full `.\mvnw.cmd -B verify`: **BUILD SUCCESS**, **3.578 test**, 0 failure, 0 error, 0 skipped, exit 0; thời gian 15:46. Docker Testcontainers hoạt động; không phát sinh ngoại lệ môi trường được liệt kê.
- JaCoCo LINE: **96,82%** — **10.428/10.770 dòng** (342 missed); mọi coverage check đạt.
- Regression T-07 giữ NFR-P01: **16 truy vấn đọc** cho cả lượt 10 câu và 50 câu.

## 2026-11-06 — Chốt 011/T-10

1. `MasteryQueryRepository` kiểm tra khả dụng câu hỏi theo lô. Một KID có câu dùng được khi có ít nhất một câu `APPROVED` gắn nhãn `PRIMARY`; test đối chiếu kết quả batch với `KnowledgeTreeService.approvedQuestionIds(Set.of(kid), null, 200)` cho từng KID. Nếu lệch, task dừng và báo leader.
2. Recommendation sắp `REVIEW` theo value tăng dần rồi KID tăng dần; nhiều tiên quyết của cùng nút theo KID tăng dần; `CONSOLIDATE` theo KID tăng dần; `NEW` theo HSK tăng dần rồi KID tăng dần.
3. Dựng danh sách theo thứ tự nhóm FR-015, chèn tiên quyết ngay trước review, bỏ KID trùng và lấy 5 phần tử đầu. Nếu phần tử thứ 5 là `PREREQ`, vẫn giữ dù `REVIEW` liên quan bị cắt.
4. Mẫu reason cố định, không gọi AI: `PREREQ` — `Ôn {nameVi tiên quyết} trước khi ôn {nameVi nút cần ôn}.`; `REVIEW` — `Cần ôn: {value}/100.` với một chữ số thập phân và dấu phẩy Việt Nam; `CONSOLIDATE` — `Đến hạn củng cố {nameVi}.`; `NEW` — `Nút mới đã đủ tiên quyết.`
5. `value` luôn có mặt trong JSON và được phép null; `notyet` trả null, còn `insufficient`/trạng thái có value trả giá trị đọc lại theo today. Schema sinh OpenAPI 3.1 phải là number/null, không có format.
6. Mỗi KID chỉ xuất hiện một lần, giữ lần đầu xuất hiện; áp dụng cho cả `PREREQ`, `REVIEW`, `CONSOLIDATE`, `NEW`.

### Kết quả gate T-10

- OpenAPI sinh: **1 test PASS**, 0 failure/error/skip, exit 0; kiểm path, `maxItems=5`, `value` là number/null và không có format.
- T-10 targeted: **8 test PASS**, 0 failure/error/skip, exit 0. Regression mastery T-06/T-07/T-08/T-09 và SecurityConfig: **56 test PASS**, 0 failure/error/skip, exit 0.
- Full `.\mvnw.cmd -B verify`: **BUILD SUCCESS**, **3.620 test**, 0 failure, 0 error, 0 skipped, exit 0; Testcontainers dùng Docker hoạt động. Không phát sinh lỗi thuộc các ngoại lệ môi trường được duyệt.
- JaCoCo LINE: **96,74%** — **10.671/11.031 dòng** (360 missed); toàn bộ coverage checks đạt.
- NFR truy vấn: StatementInspector đo **4 SELECT với 10 nút và 4 SELECT với 50 nút** (fixture không có evidence). Test đối chiếu tập KID của batch với `KnowledgeTreeService.approvedQuestionIds` cho từng KID.

## 2026-11-07 — Chốt 011/T-13 (ReviewListener)

1. **Q-T13-01 — Dependency:** T-13 phụ thuộc T-05 và T-11. T-12 không chặn T-13. T-13 bắt đầu trên main có PR #125 đã merge để dùng FsrsScheduler với giới hạn stability mới.
2. **Q-T13-02 — TOPIC_STUDY:** `LEARNED` và `KNOWN` là minh chứng FSRS; không yêu cầu `is_correct=true`. Vẫn loại suspicious và metadata theo FR-008; không áp dụng cửa sổ 7 ngày.
3. **Q-T13-03 — Rating:** Lần đầu TOPIC_STUDY: `LEARNED → GOOD`, `KNOWN → EASY`; nếu cùng lexeme có cả hai thì chọn `LEARNED → GOOD`. Lần đầu câu chấm điểm: có câu sai hợp lệ thì `AGAIN`, nếu không thì có câu đúng hợp lệ sẽ là `GOOD`. Khi đã có lịch, câu chấm điểm dùng cùng quy tắc sai/đúng; TOPIC_STUDY dùng `GOOD`. Mỗi lượt cập nhật tối đa một lần cho mỗi lexeme.
4. **Q-T13-04 — Precision:** Khi lưu stability dùng scale 3 và difficulty scale 2, cả hai `RoundingMode.HALF_UP`. Scheduler không bị sửa; giới hạn stability dùng S_MAX của phiên bản FsrsScheduler đã merge.
5. **Q-T13-05 — Mastery LEXEME:** T-13 không ghi cột `mastery` của dòng LEXEME; phần này để T-14.
6. **Q-T13-06 — Whitelist nguồn:** Chỉ xử lý rõ các nguồn có minh chứng theo FR-005 và `TOPIC_STUDY`. Nguồn không hỗ trợ bị bỏ qua và ghi WARN. Không thêm enum hoặc xử lý `DUE_REVIEW`; ca tích hợp DUE_REVIEW thuộc T-14.

### Kết quả gate T-13

- Targeted `ReviewListenerTest,ReviewListenerIntegrationTest`: **18 test, 0 failure, 0 error, 0 skipped**, exit 0. Bao gồm PostgreSQL/Testcontainers, transaction rollback, concurrency và NFR query.
- NFR query: sau khi dùng chung một KID trong lượt 10 và 50 item, **22 SELECT / 10 item** và **22 SELECT / 50 item**; assertion bình đẳng được giữ nguyên.
- Regression mastery/FSRS liên quan T-06/T-07/T-08/T-09/T-10/T-11/T-13: **139 test, 0 failure, 0 error, 0 skipped**, exit 0.
- Full `verify`: **3.817 test, 0 error, 0 skipped, 1 failure**, exit 1. Lỗi duy nhất `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`: **31.459 ms** so với ngưỡng **30.000 ms**; thuộc ngoại lệ NFR_P04 đã được leader chấp thuận. Không sửa hoặc nới assertion.
- JaCoCo LINE từ execution data của chính lượt full verify: **96,72%** — **10.992/11.365 dòng** (373 missed). Báo cáo được tạo bằng execution data của lượt này sau khi Surefire hoàn tất toàn bộ suite.

## 2026-11-11 — Chốt và nghiệm thu 011/T-12

1. **Q-T12-01 — Enum/API contract:** `PracticeSource.DUE_REVIEW` được thêm vào enum nguồn và contract. `ServedItemView.type` tiếp tục là chuỗi mở; không đóng schema bằng enum `ItemType`. `FLASHCARD_RATING` được nêu trong description của trường `type`.
2. **Q-T12-02 — study_events:** DUE_REVIEW tiếp tục ghi `event_type='STUDY'`; chỉ `GAME_ROUND` dùng `GAME`. Không sửa `PracticeService` hoặc thêm migration.
3. `DUE_REVIEW` dùng đúng `(PRACTICE, IMMEDIATE, 2 giờ, allowSkip=true, maxScoreFromServed=false)`; `FLASHCARD_RATING` có `gradable=false`. Giá trị cũ và thứ tự cũ được giữ nguyên.
4. T-12 không triển khai rating/FSRS/ReviewHandler. Các giá trị AGAIN/HARD/GOOD/EASY và luồng review thuộc T-14. Test xác nhận enum đồng bộ/round-trip, flashcard được ghi nhận nhưng không chấm điểm, và nguồn cũ giữ hành vi.
5. Không cần migration: `attempts.ref_type` và item type không có CHECK ràng buộc các giá trị này.

### Kết quả gate T-12

- Targeted (`PracticeCoreTest,PracticeApiTest,PracticeApiIntegrationTest,PracticeControllerIntegrationTest,PracticeServiceTest`): **136 test, 0 failure, 0 error, 0 skipped**, exit 0. Fixture tích hợp tạo lexeme giả trong DB Testcontainers để thỏa FK; không thay assertion hay production code.
- Regression practice/review/FSRS/mastery/frontier/recommendation: **531 test, 0 failure, 0 error, 0 skipped**, exit 0.
- Full `mvnw -B verify`: **BUILD SUCCESS**, **4.229 test**, 0 failure, 0 error, 0 skipped, exit 0; Testcontainers/PostgreSQL chạy được.
- JaCoCo LINE từ execution data của chính lượt full verify: **96,72%** — **11.825/12.226 dòng** (401 missed); coverage check đạt.

# Câu hỏi và quyết định — Spec #021

## 2026-11-14 · G0 T-08 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T08-01 | Làm FE khi V9/backend cuộc thi chưa merge | Nhánh từ cc7b7ec, dùng mẫu tự soạn trong catalog.preview.ts và test. Adapter giữ contract, không thay lỗi API thật bằng mẫu. Nối thật sau T-05; T-09 để sau T-07 | ✅ Chốt |
| Q-T08-02 | File dùng chung với 019/T-06 và 015 | Leader chuyển tin cho Đạt; không chờ phản hồi mới code. Ẩn qua pageContext.ts, không sửa AssistantHost.tsx. Chỉ thêm Cuộc thi trong navigation.ts và cập nhật Layouts.test.tsx sau khi 019/T-06 merge/rebase; không sửa LearnerLayout.tsx hay lưới mobile. PR sau rebase và chạy lại AssistantPromptBuilderTest | ✅ Chốt |
| Q-T08-03 | MyResult trước công bố | Fixture bám YAML: trường điểm/hạng có mặt và bằng null. UI luôn kiểm published; không hiện điểm, hạng, bảng hoặc đáp án trước công bố | ✅ Chốt |
| Q-T08-04 | Phân trang hạng | Hiện đúng trang server trả, không tự thêm query page/size ngoài contract. Chốt phân trang cùng T-05 | ✅ Chốt cho T-08 |
| Q-T08-05 | Hết giờ khi đang lưu đáp án | Khoá nhập, chờ các request lưu đã bắt đầu, rồi gọi submit đúng một lần. Chống trùng giữa nút bấm, timer và StrictMode. Lỗi giữ trạng thái chưa nộp và nút thử lại; server quyết ân hạn | ✅ Chốt |
| Q-T08-06 | QA responsive | Kiểm SCR-048…051 tại 320/375/640 px, kèm ảnh trong PR; đủ loading/empty/error/ready và trước/sau công bố | ✅ Chốt |
| Q-T08-07 | Full Vitest còn lỗi chập chờn SCR-059 ngoài allowlist T-08 | Leader duyệt ghi rõ full Vitest 1 fail SCR-059, chạy riêng pass 64/64 rồi push và mở Draft PR. T-08 giữ đúng 35 file; sửa race SCR-059 bằng PR riêng trên nhánh test/scr059-form-wait, chỉ một file test, giữ assertion và timeout | ✅ Chốt |
| Q-T08-08 | Đưa ảnh QA vào PR | Đính kèm qa-scr-048-051.png qua GitHub, không commit ảnh vào repo | ✅ Chốt |

## 2026-11-14 · Leader chốt khi viết spec

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Bước đăng ký riêng (UC-091, BR-092-2) khi DB không có bảng đăng ký | Bỏ: vào thi trong khung giờ = đăng ký; không huỷ đăng ký; `max_participants` đếm lượt `CONTEST` | ✅ Chốt |
| Q2 | Đề của cuộc thi bị lộ qua Thi thử nếu `PUBLISHED` | Đề để `DRAFT`, chỉ đường cuộc thi mở; sau cuộc thi có thể công khai thành đề thi thử | ✅ Chốt |
| Q3 | Trao thưởng và ai thấy email người đạt giải | Không trao thưởng trong hệ thống (13/11). Email chỉ ở endpoint winners cho `CONTENT_EDITOR` (SCR-070/071 là màn của `CONTENT_EDITOR`; plan cũ ghi ADMIN), ghi `audit_logs` mỗi lần xem | ✅ Chốt |
| Q4 | Số migration index một lượt/người | **V9**; `app_settings` của 023 chuyển sang **V10** | ✅ Chốt |
| Q5 | Trạng thái `CLOSED` và job đóng cuộc thi | Không dùng; trạng thái lịch tính theo giờ (BR-090-4) | ✅ Chốt |
| Q6 | Lượt đáng ngờ | Cờ tự động của engine loại khỏi bảng trừ khi ban tổ chức "Giữ" trước khi công bố; chuyển tab chỉ ghi nhận (BR-092-9) | ✅ Chốt |
| Q7 | Email xác thực khi thi | Bắt buộc (BR-091-4, UC-091 T6) | ✅ Chốt |
| Q8 | Vào muộn | Cho vào tới `ends_at`, thời gian còn lại bị cắt theo `ends_at`; cảnh báo trước khi vào | ✅ Chốt |
| Q9 | Xem lại đáp án sau cuộc thi | Không làm (SCR-051 chưa định nghĩa; đề có thể thành đề thi thử) | ✅ Chốt |

## Dữ kiện đã kiểm trên main `72d575e`

- `community.contests` (V2) có `exam_id`, `starts_at`, `ends_at`, `max_participants`, `prizes` JSONB, `status` `DRAFT/PUBLISHED/CLOSED/CANCELLED`, `ranking_published_at`, `ck_contest_rank`, `ck_contest_exam`.
- `attempts.kind` CHECK đã có `CONTEST`; `AttemptKind.CONTEST` đã có; `PracticeSource` chưa có `CONTEST`.
- `ExamAttemptService.start` chặn đề không `PUBLISHED` — `ContestHandler` dựng câu như vậy nhưng không kiểm `PUBLISHED`.
- `/api/public/community/**` đã `permitAll`; `/api/community/**` đã `LEARNER`.

## 2026-11-14 · G0 T-01 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T01-01 | Đồng bộ enum nguồn với contract của 003 | Thêm `CONTEST` vào cuối enum sau `QUIZ_RETRY` trong OpenAPI 003 và cả hai enum Java, cùng commit; giữ nguyên `PracticeCoreTest` | ✅ Chốt |
| Q-T01-02 | Phạm vi AT-01 của T-01 | Kiểm catalog index (unique, cột, predicate), chèn trùng bị từ chối và các trường hợp khác người/cuộc thi/kind vẫn hợp lệ trên PostgreSQL Testcontainers. Không tạo container riêng để nâng V8 → V9; mọi integration test đã chạy Flyway V1 → V9 | ✅ Chốt |
| Q-T01-03 | Phạm vi AT-13 khi chưa có `ContestHandler` | T-01 chèn thẳng lượt `CONTEST` để kiểm `existsActiveTest` và trợ lý trả 403 `ASSISTANT_BLOCKED_DURING_TEST`, không gọi stub/không trừ hạn mức; kiểm lượt đã nộp/bỏ dở/hết hạn/người khác không chặn. `EvidenceAllocatorTest` kiểm `CONTEST → null`. Phần chạy trọn tạo/nộp qua engine và chứng minh mastery không đổi chuyển sang T-02, không thuộc gate T-01 | ✅ Chốt |
| Q-T01-04 | Backup và áp migration trên Neon | Chỉ leader backup và chạy V7 → V10 theo thứ tự merge. Tường chỉ kiểm V9 bằng Testcontainers, không truy cập Neon | ✅ Chốt |
| Q-T01-05 | Fixture `PracticeServiceTest` giả định chỉ `GAME_ROUND` có TTL động | Leader duyệt file thứ 14: cấp override theo `source.ttl() == null`; loại `CONTEST` khỏi nhóm TTL cố định; cả `GAME_ROUND` và `CONTEST` đều được kiểm override thiếu/0/âm. Không sửa `PracticeService` hay assertion của nguồn khác | ✅ Chốt |

## 2026-11-14 · G0 T-02 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T02-01 | Interface có chín phương thức nhưng T-02 chỉ triển khai bốn | `ContestAttemptService` triển khai `start`, `countAttempts`, `submit`, `exam`, chưa `implements ContestAttemptApi`, không tạo stub. T-03 thêm năm phương thức còn lại rồi gắn interface. Leader đính chính khi duyệt T-03: T-03 phụ thuộc T-02; T-04 chỉ phụ thuộc T-02, không phụ thuộc T-03 | ✅ Chốt |
| Q-T02-02 | Ân hạn nộp thay đổi trong lúc thi | Đọc `SettingsApi.getInt(CONTEST_SUBMIT_GRACE_SECONDS)`; chụp giá trị vào snapshot khi tạo lượt. `expiresAt = deadline + grace`; cấu hình mới chỉ áp dụng cho lượt tạo sau, resume giữ snapshot/deadline/expiry cũ. Không khai lại cấu hình YAML | ✅ Chốt |
| Q-T02-03 | Điểm dùng để xếp hạng cuộc thi | Dùng số câu đúng (`score` hiện tại của engine), không dùng trọng số `exam_questions.points` và không sửa engine. FR-006 xếp `score DESC`, `duration_ms ASC`, phù hợp `ix_att_ref_rank` của V8 | ✅ Chốt |
| Q-T02-04 | Khóa khi nộp với engine `REQUIRES_NEW` | Không giữ khóa dòng lượt ở transaction ngoài trước khi gọi engine; kiểm hai lần nộp song song, chỉ một lần ghi kết quả/bằng chứng/sự kiện | ✅ Chốt |
| Q-T02-05 | Phần AT-13 chuyển từ T-01 | T-02 chạy trọn tạo lượt → lưu câu trả lời → nộp bằng engine; kiểm trợ lý bị chặn trước provider/hạn mức, mọi cột mastery không đổi và lượt cuộc thi không tạo bằng chứng mastery, kể cả khi tái phân bổ | ✅ Chốt |

## 2026-11-15 · G0 T-03 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T03-01 | `setExcluded` không có lượt `SUBMITTED` khớp người/cuộc thi | Trả 404 `CONTEST_NOT_FOUND` bằng mã có sẵn. Chỉ sửa `suspicious`, quyết định lặp lại vẫn thành công | ✅ Chốt |
| Q-T03-02 | Sắp thí sinh khi có điểm/thời gian NULL | `participants` trả mọi trạng thái và cờ; sắp `score DESC NULLS LAST, duration_ms ASC NULLS LAST, submitted_at ASC NULLS LAST, user_id ASC` | ✅ Chốt |
| Q-T03-03 | Lượt `SUBMITTED` thiếu `score` hoặc `max_score` | Không vào ranking/rankOf/total; vẫn xuất hiện trong myAttempt/participants. Ba phép đọc hạng dùng chung điều kiện `status='SUBMITTED' AND suspicious=false AND score IS NOT NULL AND max_score IS NOT NULL`, trong phạm vi `kind='CONTEST'` và cuộc thi được hỏi. `rankOf` không có hàng hợp lệ trả `Optional.empty()` | ✅ Chốt |
| Q-T03-04 | Làm T-03 khi #191 chưa push merge lên main | Leader duyệt nhánh từ đúng `c2f85bb9330b819f41a3e8714a3a57dd8bf970a5`; #191 được merge `--no-ff` giữ commit này làm cha. Draft PR base main ghi phụ thuộc #191 nếu cần. Hash merge T-02 để trống tới khi leader gửi, điền trong cùng PR | ✅ Chốt |
| Q-T03-05 | Phạm vi AT-12 | T-03 kiểm giữ/loại trong learning; khóa sau công bố, quyền và audit thuộc community/T-06. T-03 hoàn thiện đủ chín phương thức và gắn `ContestAttemptApi`, giữ nguyên chữ ký interface | ✅ Chốt |
| Q-T03-06 | Gate | `Remove-Item Env:DEBUG -ErrorAction SilentlyContinue` trước mỗi lệnh Maven; test liên quan rồi full verify. Chỉ năm ngoại lệ môi trường đã duyệt được phép bàn giao có điều kiện; nếu xảy ra vẫn báo FAILED và số liệu thật, không skip hoặc hạ assertion/ngưỡng | ✅ Chốt |

T-03 triển khai trên đúng base `c2f85bb` theo leader duyệt. Hai integration test mới: 24 test, F0/E0/S0. Gate hồi quy theo G0: 367 test, F0/E0/S0. Full `mvnw.cmd -B verify`: 6.032 test, F0/E0/S0, native exit 0, BUILD SUCCESS; không dùng ngoại lệ môi trường. `Env:DEBUG` được xóa trước từng lệnh Maven. JaCoCo LINE từ đúng lượt full: 14.981 covered / 15.428 total = 97,10%; mọi coverage check đạt. Bốn file code/test giữ nguyên hash từ đầu tới cuối full verify; chỉ cập nhật tài liệu bàn giao sau gate.

Sau gate, `origin/main` đã có merge #191 tại `ec3f865`, khớp hash leader đã báo; commit `c2f85bb` là ancestor của main. Điền hash này vào T-02 trong `tasks.md`, giữ T-03 ở 🔄 chờ review; không rebase nhánh T-03.

## 2026-11-15 · G0 T-04 — leader duyệt 10 file

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T04-01 | Base và bean `ContestAttemptApi` | Nhánh `spec/021-t04` từ đúng `c2c3bd55a5227ecb6e6c225eac59c0fc6379e6f3` (đầu #202). Leader merge #202 `--no-ff`, giữ commit này làm cha; không rebase T-04. Phụ thuộc nghiệp vụ vẫn T-02; Draft PR ghi **phụ thuộc #202**. Nếu #202 phải sửa, leader báo | ✅ Chốt |
| Q-T04-02 | Hai kiểm kê 009 bắt nhầm namespace 021 | Duyệt `CommunityContractTest` và file thứ 10 `CommentModerationControllerIntegrationTest`. Loại đúng `/api/community/contests`, `/api/public/community/contests` và đường con; collector handler loại package `com.cnhsk.community.contest` và package con, như game. Giữ đúng 24 operation và kiểm tra âm, thêm `contests-other` vẫn thuộc 009. `/api/admin/contests/**` không thuộc kiểm kê này | ✅ Chốt |
| Q-T04-03 | Phạm vi và gate | FR-009/FR-010, AT-14; entity khớp V2, chỉ `PUBLISHED`, trạng thái từ `Clock`, giờ trả `+07:00`. `participantCount`/đề qua `ContestAttemptApi`; không đọc trực tiếp bảng learning/auth trong production. Không migration, dependency, `SecurityConfig`, contract hoặc mã lỗi mới. Xóa `Env:DEBUG` trước từng Maven; test liên quan + contract 009 + module boundary, rồi full verify/JaCoCo LINE. Chỉ đúng năm ngoại lệ môi trường đã duyệt, không skip/nới assertion hay gọi exit 1 là PASS | ✅ Chốt |

Shadow plan đã duyệt: đọc constitution/constraints, spec 021 + YAML, spec 012 §0, V2 và mẫu controller/test hiện có; sửa đúng allowlist dưới đây; chạy test liên quan rồi full verify; tự kiểm, commit đúng 10 file, push nhánh và mở Draft PR base main. Không merge hoặc chuyển Ready.

1. `src/main/java/com/cnhsk/community/contest/Contest.java`
2. `src/main/java/com/cnhsk/community/contest/ContestRepository.java`
3. `src/main/java/com/cnhsk/community/contest/ContestQueryService.java`
4. `src/main/java/com/cnhsk/community/contest/web/ContestViews.java`
5. `src/main/java/com/cnhsk/community/contest/web/PublicContestController.java`
6. `src/test/java/com/cnhsk/community/contest/PublicContestIntegrationTest.java`
7. `specs/021-contests/questions.md`
8. `specs/021-contests/tasks.md`
9. `src/test/java/com/cnhsk/contract/CommunityContractTest.java`
10. `src/test/java/com/cnhsk/community/moderation/CommentModerationControllerIntegrationTest.java`

Năm ngoại lệ giữ nguyên từ gate chung (đối chiếu `specs/013-credit-cards/questions.md`): hai assertion Redis của `LoginAttemptServiceIntegrationTest` (`FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry` và `redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings`); assertion p95 của `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget`, `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`, `QuestionAdminControllerIntegrationTest.NFR_P02_fiveThousandSelfAuthoredQuestionsAreListedByKidWithinOneHundredMillisecondsP95`. Không mở rộng sang assertion chức năng hoặc lỗi khác trong cùng method.

Checkpoint trước triển khai phần chính: hai file kiểm kê 009 đã sửa và chạy riêng **31/F0/E0/S0**, Maven native exit 0. Lượt đầu bị sandbox chặn Maven dependency resolution, chưa chạy test; lượt ngoài sandbox thành công. Chưa dùng checkpoint này thay cho gate sau khi thêm hai GET.

## 2026-11-15 · T-04 — lịch sử gate đầu và leader duyệt file 11

**T-04 cập nhật kiểm kê entity của 009 (CommunityArchitectureTest) thêm Contest**.

Full verify đầu giữ nguyên kết quả **FAILED/native exit 1: 6.074/F2/E0/S0**, 385 suite, từ `11:34:34.7325405` đến `11:54:07.4801050` (+07). Hai failure ngoài năm ngoại lệ: `CommunityArchitectureTest.communityHasExactlyTheFourMappedEntities` (tập cũ có 4 entity, thực tế thêm `Contest`) và `entitiesUseExplicitCommunityTablesAndScalarReferences` (map cũ chưa có `Contest → contests`). Tám file Java T-04 giữ nguyên hash qua lượt full này. JaCoCo report/check riêng trên đúng exec full đầu: native exit 0, LINE **15.057/15.504 = 97,12%**; coverage đạt không thay đổi verdict full FAILED. Log/XML/exec của lượt đầu được giữ riêng trong `target/` và thư mục TEMP của lượt chạy.

Trước full đầu: targeted đầu **131/F1/E1/S0**, native 1 (offset JSON thành UTC và fixture `SUBMITTED` thiếu `submitted_at`). Bản sửa annotation đầu không compile do Jackson không có thuộc tính đã dùng; không có test được chạy ở checkpoint đó. Targeted tiếp theo **131/F1/E0/S0**, native 1 (còn offset UTC). Sau khi dùng pattern múi giờ đã có trong repo và thêm test bảo toàn microsecond, targeted **132/F0/E0/S0**, native 0; riêng T-04 **42/F0/E0/S0**. Giữ mọi kết quả cũ, không coi lượt lỗi là ngoại lệ môi trường.

Sau full đầu, leader yêu cầu giữ allowlist 10 file và dừng; chưa commit/push/PR. Chỉ đạo tiếp theo duyệt file thứ 11: `src/test/java/com/cnhsk/community/CommunityArchitectureTest.java`. Phạm vi chỉ thêm `Contest.class` vào tập entity (đổi tên method thành `communityHasExactlyTheFiveMappedEntities`) và thêm `Contest.class, "contests"` vào map bảng, cùng import cần thiết. Giữ schema `community`, mọi kiểm tra cấm quan hệ JPA/`ElementCollection`, hai luật ranh giới module và `repositoriesProvideOnlyTheJpaFoundation`. `Contest` vẫn là entity V2 với `exam_id`/`created_by` scalar; repository không kế thừa `JpaRepository`.

Shadow plan tiếp tục đã duyệt: sửa file 11 và tài liệu trong allowlist; chạy riêng `CommunityArchitectureTest` → bộ test liên quan → full verify mới, xóa `Env:DEBUG` trước từng Maven. Chỉ khi đạt gate mới commit đúng **11 file**, push `spec/021-t04`, mở Draft PR base main ghi **phụ thuộc #202**; giữ base `c2c3bd5`, không rebase/merge/Ready.

### Kết quả sau duyệt file 11

- `CommunityArchitectureTest` riêng: **5/F0/E0/S0**, native exit 0.
- Bộ liên quan 10 suite (T-04, cả hai kiểm kê 009, `ModuleBoundaryTest`, kiến trúc community, engine/đồng thời/xếp hạng/loại thí sinh và cô lập mastery cuộc thi): **137/F0/E0/S0**, native exit 0. Riêng `PublicContestIntegrationTest`: **42/F0/E0/S0**, kiểm guest và sáu role, cookie/CSRF, dữ liệu công khai, validation/404, sắp nhóm/phân trang, biên starts/ends theo `Clock`, port learning thật, `+07:00` và microsecond.
- Full verify lần hai: **6.074/F0/E0/S0**, 385 suite mới, native exit 0, **BUILD SUCCESS**; `12:06:37.8394609` → `12:27:16.4960247` (+07), trong deadline `13:06:37.8394609`. Tổng XML khớp Maven; không dùng ngoại lệ môi trường. Lượt đầu **6.074/F2/E0/S0 — FAILED** phía trên vẫn được giữ nguyên.
- JaCoCo từ đúng exec full lần hai: **15.057/15.504 LINE = 97,12%**, report/check đạt. Hai package `community.contest` và `.web`: **76/76 LINE = 100%**. Một session, không class mismatch; chín file Java trong allowlist giữ nguyên hash từ trước đến sau full. Exec SHA-256 `1B510A60C1F1F695C3E97C7B9A3C625860C4CF7A6FC084E9C1A534B6043228F2`.
- Evidence riêng: `target/t04-architecture-approved*`, `t04-targeted-approved*`, `t04-full2*`; log/XML/report full đầu vẫn tại `t04-full*`, `t04-full-failed-reports/`, `t04-full1-jacoco/` và TEMP riêng. `Env:DEBUG` được xóa trước từng lệnh Maven.

Self-check trước nộp: **Global PASS** (stack/dependency/naming giữ đúng; chỉ gọi chéo qua `learning.api.ContestAttemptApi`); **Business PASS** (chỉ `PUBLISHED`, trạng thái từ giờ server, response không có PII/đáp án/điểm; không đổi nghiệp vụ tiền/chấm điểm); **Safety PASS** (đúng 11 file đã duyệt, entity giữ schema `community` và scalar ID, không migration/SQL Neon/config bảo mật/dependency/secret/xóa file). Hai kiểm kê 009 vẫn khóa đúng 24 operation và giữ kiểm tra âm. `git diff --check` sạch, không `TODO` trong code T-04 mới; Maven không cấu hình Java linter riêng. OpenAPI đã được duyệt, hai GET khớp path/method/status của YAML, không sửa contract. Task chờ leader review; chưa tự nhận merge.

## 2026-11-15 · G0 T-05 — leader duyệt 15 file

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T05-01 | Làm T-05 khi #204 chưa merge | Được làm trước từ đúng `6b426209895a07511d6fe96ddc420aabc8c41927` (đầu #204), nhánh `spec/021-t05`. Leader merge #204 giữ commit này làm cha; không rebase. Draft PR ghi **phụ thuộc #204** | ✅ Chốt |
| Q-T05-02 | `/results` có giới hạn page/size nhưng thiếu response 422 | Duyệt file thứ 15 `contracts/openapi-contests.yaml`: chỉ thêm `'422': { $ref: '#/components/responses/Error' }` cho GET `/api/community/contests/{id}/results`. Page âm hoặc size ngoài 1–50 trả `VALIDATION_FAILED` 422; không đổi phần khác của YAML. Contract được cập nhật trước code | ✅ Chốt |
| Q-T05-03 | AT-08 nói không lộ điểm, YAML yêu cầu các field của MyResult | Theo Q-T08-03: trước công bố `mine={status,submittedAt,score:null,maxScore:null,durationMs:null,rank:null,excluded:null}`, `ranking=null`. Test cả JSON thô để chứng minh không lộ giá trị thật | ✅ Chốt |

Shadow plan đã duyệt: FR-011…FR-014 / AT-05, AT-08…AT-10, AT-15; test bổ sung phần HTTP của xếp hạng và hồi quy nộp/đồng thời. Sửa đúng 14 file G0 và YAML đã bổ sung, không sửa `AuthApi`, `SecurityConfig`, migration, FE hoặc hai kiểm kê 009. Chỉ thêm `CONTEST_FULL` (422) ngay trước `INTERNAL_ERROR` trên base; giữ thứ tự mã cũ, leader gộp với mã mới của main khi merge.

FR-011 giữ thứ tự: không PUBLISHED → 404; requireVerifiedEmail → 403 ACCOUNT_UNVERIFIED; người tạo → 403 FORBIDDEN; không ONGOING → 422 CONTEST_NOT_OPEN; chưa có lượt + đủ sức chứa → 422 CONTEST_FULL; rồi gọi start. Khóa `contest:start:{id}` bằng `pg_advisory_xact_lock(hashtextextended(:key, 0))`, kiểm lượt/đếm/start trong cùng transaction READ_COMMITTED. Nộp không giữ khóa ngoài qua engine REQUIRES_NEW. Sự kiện chỉ TAB_HIDDEN, tối đa một lần/10 giây/người bằng RateLimiter hiện có; vượt vẫn 204, chỉ ghi audit. Kết quả lấy tên từ một lô `AuthApi.publicProfiles`, không email/avatar/đáp án. Giờ trả +07:00.

Gate: xóa `Env:DEBUG` trước từng Maven; test mới + hồi quy contest/lỗi/contract 009/kiến trúc, rồi full verify với Testcontainers và JaCoCo LINE từ đúng full exec ≥80%. Chỉ đúng năm ngoại lệ môi trường đã ghi ở T-04; exit 1 vẫn FAILED. Xong mới commit đúng 15 file, push nhánh và mở Draft PR; không merge/Ready, không tự cập nhật ✅.

Allowlist T-05 đã duyệt:

1. `src/main/java/com/cnhsk/community/contest/ContestRepository.java`
2. `src/main/java/com/cnhsk/community/contest/ContestService.java`
3. `src/main/java/com/cnhsk/community/contest/ContestQueryService.java`
4. `src/main/java/com/cnhsk/community/contest/web/ContestViews.java`
5. `src/main/java/com/cnhsk/community/contest/web/ContestController.java`
6. `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`
7. `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`
8. `src/test/java/com/cnhsk/community/contest/ContestFixtures.java`
9. `src/test/java/com/cnhsk/community/contest/ContestAttemptControllerIntegrationTest.java`
10. `src/test/java/com/cnhsk/community/contest/ContestStartConcurrencyIntegrationTest.java`
11. `src/test/java/com/cnhsk/community/contest/ContestEventControllerIntegrationTest.java`
12. `src/test/java/com/cnhsk/community/contest/ContestResultsControllerIntegrationTest.java`
13. `specs/021-contests/questions.md`
14. `specs/021-contests/tasks.md`
15. `specs/021-contests/contracts/openapi-contests.yaml`

Checkpoint test: `test-compile` native exit 0. Lượt liên quan đầu **232/F0/E1/S0**, native exit 1: fixture của test ranking cố đặt `auth.users.display_name=NULL`, vi phạm NOT NULL của V1; không phải ngoại lệ môi trường. Đã dùng tên tự soạn hợp lệ trong ca ranking và giữ kiểm tra tên null bằng ca riêng khi port `AuthApi.publicProfiles` không trả hồ sơ. Lượt sau **233/F0/E0/S0**, 16 suite, native exit 0; riêng bốn suite T-05 **65/F0/E0/S0**. NFR-P02 với 1.000 lượt: 30 mẫu sau 10 warm-up, p50=14 ms, p95=19 ms (<300 ms), một lô tên/trang. Giữ log/XML lượt lỗi tại `target/t05-targeted*`; không skip hoặc nới assertion.

### T-05 · Kết quả gate và self-check

- Full `mvnw.cmd -B verify`: **6.139/F0/E0/S0**, 389 suite mới; native exit 0, **BUILD SUCCESS**. Bắt đầu `2026-11-15T13:10:31.6858157+07:00`, kết thúc `13:32:12.2172165+07:00`, trong deadline `14:10:31.6858157+07:00`. Tổng XML khớp Maven. Không dùng năm ngoại lệ môi trường; không có skip.
- JaCoCo từ đúng exec full: **15.160/15.609 LINE = 97,12%**; report/check đạt. Hai package `community.contest` và `.web`: **178/180 LINE = 98,89%**. Một session, class mismatch 0; 12 file Java và YAML giữ nguyên hash trước/sau full. Exec SHA-256 `A9D554326E5D1E42C9426C612913A422199251D5E1CE42AB7C665BA317EC0BB4`.
- Bốn suite T-05 trong full: **65/F0/E0/S0**. NFR-P02 cùng lượt full, 1.000 lượt/30 mẫu sau 10 warm-up: **p50=10 ms, p95=15 ms**, dưới 300 ms; một lần gọi `publicProfiles` cho mỗi trang, không N+1 tên. Race HTTP ba người/sức chứa hai cho đúng hai lượt mới và một 422; 20 request cùng người chỉ tạo một lượt; hai request nộp ghi kết quả một lần.
- OpenAPI generated của bốn operation T-05 khớp path/method/operationId/status/security với YAML đã duyệt; YAML chỉ thêm một dòng 422 của `/results`. JSON trước công bố giữ các trường nhạy cảm null và không chứa giá trị thật; response không có email/avatar/đáp án. Hai kiểm kê 009 vẫn khóa đúng 24 operation.
- Evidence: `target/t05-test-compile.log`, `t05-targeted*`, `t05-full*`, `target/site/jacoco/` và TEMP riêng của full. Lượt targeted đầu FAILED/native 1 vẫn giữ nguyên ở trên. `Env:DEBUG` được xóa trước từng lệnh Maven.

Self-check: **Global PASS** (Java 17/stack/dependency/naming đúng; community chỉ gọi auth/learning qua API); **Business PASS** (đúng thứ tự FR-011, sức chứa và start cùng khóa/transaction, server chấm điểm/giữ deadline, events chỉ audit, tên theo lô và không lộ PII/đáp án); **Safety PASS** (đúng 15 file đã duyệt, không AuthApi/SecurityConfig/migration/FE/hai test 009, không SQL Neon/secret/xóa file). `git diff --check` sạch, không BOM, không TODO/FIXME trong code thuộc allowlist; Maven không cấu hình Java linter riêng. Giữ mã mới ngay trước INTERNAL_ERROR trên base, không rebase; leader xử lý thứ tự enum khi merge. Task chờ review; không tự đánh dấu ✅ hoặc merge.

## 2026-11-15 · G0 T-06 — leader duyệt 17 file

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T06-01 | Base `196043d` chưa có archive của 023/T-06 | Không merge main vào nhánh. Chờ hash leader gửi sau khi #206 merge, tạo `spec/021-t06` từ hash đó; bản gộp chứa cả T-05 và #195. Trong lúc chờ được soạn code/test cục bộ, chưa commit | ✅ Chốt |
| Q-T06-02 | Thời điểm xét thí sinh | GET và PATCH participants đều chỉ khi `ENDED`; gọi sớm → 409 `CONTEST_LOCKED`. PATCH sau công bố xếp hạng cũng → 409 `CONTEST_LOCKED` | ✅ Chốt |
| Q-T06-03 | Đổi đề khi cuộc thi PUBLISHED chưa bắt đầu | PUT kiểm lại đề như publish: null/không tồn tại/ARCHIVED/không câu dùng được → 422 `CONTEST_EXAM_INVALID`, trước khi ghi. Khóa `exam:usage:{id}` cho cả đề cũ và mới theo id tăng dần trước khóa dòng contest, giữ tới commit/rollback | ✅ Chốt |
| Q-T06-04 | Truy vấn audit theo actor | Chỉ thêm một hàm `AuditService.countByActors(action, targetType, targetId)` với một truy vấn GROUP BY actor; không đổi `countByTargets` hoặc `recordDetail`. Community không đọc trực tiếp schema shared. Test trong `AuditTargetCountIntegrationTest` | ✅ Chốt |
| Q-T06-05 | Quyền và mã lỗi | Matcher `/api/admin/contests`, `/api/admin/contests/**` → CONTENT_EDITOR trước `/api/admin/**`. Ba mã `CONTEST_LOCKED`, `CONTEST_RANKING_TOO_EARLY`, `CONTEST_HAS_ATTEMPTS` (409) đặt sau mã cuối của base mới, ngay trước INTERNAL_ERROR | ✅ Chốt |

Phủ FR-017…FR-023, AT-12/16/17 và guard archive thật của 023. Allowlist đúng 17 file: sáu file `community/contest` (`Contest`, `ContestRepository`, `ContestAdminService`, `ContestExamUsageGuard`, `web/ContestViews`, `web/ContestAdminController`); `AuditService`, `SecurityConfig`, `ErrorCode`; `GlobalExceptionHandlerTest`, `AuditTargetCountIntegrationTest`; bốn test `community/contest` (`ContestAdminControllerIntegrationTest`, `ContestAdminParticipantsIntegrationTest`, `ContestAdminConcurrencyIntegrationTest`, `ContestExamUsageGuardIntegrationTest`); `questions.md`, `tasks.md` của 021.

Gate: xóa `Env:DEBUG` trước mỗi Maven; test contest/audit/error liên quan, ExamAdmin + ExamPublication của 023, ModuleBoundaryTest, CommunityArchitectureTest rồi full `mvnw.cmd -B verify`. Báo số test/F/E/S và JaCoCo LINE ≥ 80%. Chỉ năm ngoại lệ môi trường đã duyệt được phép bàn giao có điều kiện; nếu gặp vẫn báo FAILED, không skip hoặc hạ assertion/ngưỡng. Commit `feat(contests): add contest administration`, đúng 17 file, mở Draft PR. Không sửa AuthApi/interface/contract/migration/FE, không Neon. Chưa có kết quả gate trong giai đoạn soạn cục bộ chờ hash base.

Checkpoint soạn cục bộ: `mvnw.cmd -B compile` ngoài sandbox đạt **BUILD SUCCESS/native exit 0**, hoàn tất `2026-11-15T14:11:29+07:00`; lượt sandbox đầu bị chặn truy cập Maven Central khi resolve parent, chưa compile/test. Đã xóa `Env:DEBUG` trước cả hai lệnh. Đây chỉ là compile production, chưa phải test/gate: test archive cần bản gộp #206 và #195 theo Q-T06-01. Chưa tạo nhánh `spec/021-t06`, chưa commit.

### Targeted trên base nháp theo chỉ đạo leader

Leader duyệt chạy trước đúng sáu lớp: `ContestAdminControllerIntegrationTest`, `ContestAdminParticipantsIntegrationTest`, `AuditTargetCountIntegrationTest`, `GlobalExceptionHandlerTest`, `ModuleBoundaryTest`, `CommunityArchitectureTest`. Hai lớp guard/concurrency để sau trên base mới. Maven testCompile luôn biên dịch mọi test source, nên tạm chuyển riêng source guard (gọi API archive chưa có ở base nháp) vào `target/t06-deferred/`, luôn đưa lại bằng `finally`; không sửa POM/assertion hoặc dùng cờ skip.

Lượt targeted đầu: XML đủ sáu suite, **124/F0/E1/S0 — FAILED**. Controller 55/F0/E1/S0; participants 23/F0/E0/S0; audit 7/F0/E0/S0; error handler 25/F0/E0/S0; module boundary 9/F0/E0/S0; kiến trúc community 5/F0/E0/S0. Lỗi do fixture `UNUSABLE` đặt câu hỏi thành `DRAFT`, không thuộc CHECK/`QuestionStatus`; sửa đúng test mới sang `PENDING_REVIEW`, giữ nguyên assertion và production. PowerShell exit 1; `$ErrorActionPreference=Stop` bắt thêm cảnh báo JVM trên stderr nên không lưu đủ Maven summary/native exit; sáu XML đã kết thúc đầy đủ, tiến trình test đã thoát. Lượt sau chỉ dùng Continue lúc gọi native Maven để lấy đúng `$LASTEXITCODE`. Log và XML lượt lỗi được giữ ở `target/t06-draft-targeted-1.log` và `target/t06-draft-targeted-1-reports/`. Không coi đây là ngoại lệ môi trường được miễn.

Sau sửa fixture, chạy lại đúng sáu lớp trên cùng base nháp: **124/F0/E0/S0**, native exit **0**, **BUILD SUCCESS**, hoàn tất `2026-11-15T14:22:00+07:00` (Maven 01:30). Controller **55**, participants **23**, audit **7**, error handler **25**, module boundary **9**, kiến trúc community **5**; cả sáu XML khớp tổng Maven, không dùng ngoại lệ và không skip. `Env:DEBUG` đã xóa trước Maven. Log `target/t06-draft-targeted-2.log`, XML tại `target/surefire-reports/`; lịch sử lượt đầu giữ nguyên. Source guard đã được đưa lại, worktree đúng 17 file, `git diff --check` sạch. Chưa chạy `ContestExamUsageGuardIntegrationTest`, `ContestAdminConcurrencyIntegrationTest` hoặc full verify; chờ hash leader xác nhận rồi tạo nhánh từ bản gộp mới, giữ nguyên yêu cầu không merge main vào nhánh và chưa commit.

Self-check giai đoạn nháp: **Global PASS** (stack/dependency/naming giữ nguyên; ModuleBoundaryTest và CommunityArchitectureTest xanh); **Business PASS trong phạm vi sáu lớp đã chạy** (role/cookie-CSRF, trạng thái và biên thời gian, xếp hạng sau quyết định, ghi audit/rollback, truy vấn actor và profile theo lô đúng); **Safety PASS** (đúng 17 file, không AuthApi/interface/migration/FE/Neon/secret/xóa file; chưa branch/commit/push). Kiểm thử archive/khóa liên thông và full gate vẫn còn phải thực hiện trên base được leader gửi.

### Base chính thức và gate sau merge #206

Leader gửi hash `9ba14a6510baf29849bbdfc89df3477e49cc485a` (#206, verify trên máy leader 6.391/F0/E0/S0). Đã fetch và tạo `spec/021-t06` từ đúng hash này; `196043d` là ancestor. Chuyển đúng 17 file từ nháp, không merge main/rebase. Trong allowlist chỉ ErrorCode và GlobalExceptionHandlerTest khác giữa base nháp và base mới: giữ toàn bộ mã/test mới của main, thêm đúng ba mã 409 sau CONTEST_FULL, ngay trước INTERNAL_ERROR và cập nhật kiểm tra tương ứng. Không tự đổi thứ tự khi #207 thêm mã sau đó; leader xử lý lúc merge.

Lượt guard/concurrency đầu **FAILED/native exit 1, 0 test được chạy**: JaCoCo không mở được destfile vì đường dẫn repo có dấu bị hỏng khi Surefire fork trên Windows. Sửa cách chạy bằng thư mục TEMP ASCII riêng cho mỗi lượt (`-Djava.io.tmpdir`), không đổi POM/JaCoCo threshold hoặc source. Log `target/t06-guard-1.log` giữ nguyên; đây là lỗi khởi động đã khắc phục, không dùng ngoại lệ môi trường để bỏ qua gate.

Lượt chạy lại hai lớp trên base chính thức: **19/F0/E0/S0**, native exit **0**, **BUILD SUCCESS**, hoàn tất `2026-11-15T14:34:18+07:00`. `ContestExamUsageGuardIntegrationTest` **13** test dùng guard và archive 023 thật; `ContestAdminConcurrencyIntegrationTest` **6** test. Chứng minh hai thứ tự archive–publish/đổi đề, giữ khóa cả đề cũ và mới và lấy theo id tăng dần; huỷ–vào thi, xét thí sinh–công bố, hai lần công bố cùng lúc. Log/XML ở `target/t06-guard-2.log`, `target/t06-guard-2-reports/`; đã xóa Env:DEBUG trước từng Maven. Chưa dùng kết quả này thay full verify.

### Hồi quy liên quan — phát hiện kiểm kê 009 bắt nhầm admin contest

Lượt liên quan trên base chính thức **514/F1/E0/S0 — FAILED**, native exit **1**, hoàn tất `2026-11-15T14:37:48+07:00` (Maven 02:03). Toàn bộ test contest, audit, ErrorCode, ExamAdmin/ExamPublication và boundary đều đạt; failure duy nhất `CommunityContractTest.AC01_registeredOperationsAndRuntimeDocumentationMatchTheSource`, tại assertion so documented operation với nguồn 24 operation. Log `target/t06-related.log`; XML lượt lỗi giữ riêng tại `target/t06-related-failed-reports/`. Đã xóa Env:DEBUG, dùng TEMP ASCII riêng, không skip/miễn assertion.

Q-T06-06 (CHỜ DUYỆT): Ghi chú G0 nhận định admin contest nằm ngoài `/api/community` nên không ảnh hưởng kiểm kê 009; code thật của `CommunityContractTest` dùng `springdoc.packages-to-scan=com.cnhsk.community`, vì vậy OpenAPI vẫn chứa đủ chín operation admin contest. Collector registered đã loại package contest; collector documented chỉ lọc hai namespace contest người học/công khai, chưa có `/api/admin/contests`. Đề xuất bổ sung file thứ 18 `src/test/java/com/cnhsk/contract/CommunityContractTest.java`: chỉ mở rộng `isContestPath` cho đúng root và đường con admin contest, bổ sung kiểm tra âm `contests-other`, giữ nguyên tập 24 operation/assertion và production/OpenAPI. Chưa sửa file ngoài allowlist; đã gửi câu hỏi xin duyệt. Full verify chưa chạy vì failure này còn hiện hữu và không thuộc năm ngoại lệ môi trường.

Leader sau đó **DUYỆT Q-T06-06/file 18**: mở rộng `isContestPath` đúng `/api/admin/contests` và đường con, thêm ca âm `/api/admin/contests-other` vẫn được `readOperations` tính. Giữ nguyên mọi assertion hiện có và inventory 24 operation của 009; không sửa CommentModerationControllerIntegrationTest vì lớp này đã xanh và lọc theo path. Kế hoạch tiếp tục: bộ liên quan rồi full verify, xóa Env:DEBUG trước từng Maven; chỉ commit đúng **18 file** khi đạt gate, message `feat(contests): add contest administration`, mở Draft PR. Lượt F1 phía trên giữ nguyên lịch sử.

Bộ liên quan sau sửa file 18: **514/F0/E0/S0**, native exit **0**, **BUILD SUCCESS**, 29 suite, hoàn tất `2026-11-15T14:45:22+07:00` (Maven 02:40). `CommunityContractTest` 19/F0/E0/S0; kiểm kê 009 giữ source/runtime/documented 24 operation, kiểm tra âm prefix mới vẫn được tính. Log/XML: `target/t06-related2.log`, `target/t06-related2-reports/`. Không sửa thêm production/assertion hoặc CommentModerationControllerIntegrationTest. Full verify bắt đầu `2026-11-15T14:46:49.5326651+07:00`, đã chụp SHA-256 của đúng 16 file Java trong allowlist và dùng TEMP ASCII riêng; kết quả sẽ được ghi sau khi lượt full kết thúc.

### Kết quả full verify và tự kiểm trước nộp

- `mvnw.cmd -B verify` (chỉ thêm `-Djava.io.tmpdir=<TEMP ASCII riêng>` để tránh lỗi đường dẫn Windows): **6.490/F0/E0/S0**, **408 suite**, native exit **0**, **BUILD SUCCESS**. Bắt đầu `2026-11-15T14:46:49.5326651+07:00`, Maven hoàn tất `15:13:38+07:00`, thời gian **26:47**; sáu nghìn bốn trăm chín mươi test trong XML khớp tổng Maven. Không dùng ngoại lệ môi trường, không skip; Env:DEBUG được xóa trước Maven.
- JaCoCo LINE từ đúng lượt full: **15.638 covered / 16.097 total = 97,148537%**, mọi coverage check đạt. Một session, class mismatch **0**; hai package `community.contest` và `.web` tổng **373/377 LINE = 98,94%**. Exec SHA-256 `6445E5F94AC079ED80E9B473F35271542753D80BD7FDC9FB715B88B7C03C38CF`.
- Cả **16 file Java** trong allowlist giữ nguyên SHA-256 từ trước đến sau full verify; sau gate chỉ cập nhật hai file tài liệu bàn giao. Evidence: `target/t06-full.log`, `t06-full-meta.json`, `t06-full-result.json`, `t06-full-jacoco.exec`, `target/surefire-reports/`, `target/site/jacoco/` và TEMP riêng. Các lượt E1/F1 và lỗi khởi động trước đó giữ nguyên lịch sử ở trên.
- **Global PASS:** Java 17/stack/dependency/naming đúng, không đổi contract đã khóa; ModuleBoundaryTest và CommunityArchitectureTest xanh. Backend không cấu hình Java linter riêng; `git diff --check` sạch.
- **Business PASS:** mọi API mới yêu cầu CONTENT_EDITOR; kiểm đúng trạng thái/biên thời gian/đề trước khi ghi; khóa exam cũ/mới tăng dần, giữ tới commit; dùng archive và engine thật trong test. Audit quyết định/công bố cùng transaction, rollback khi audit lỗi; profile theo lô, số sự kiện theo actor/cuộc thi bằng một truy vấn. Response không có email/đáp án.
- **Safety PASS:** đúng **18 file được duyệt**, không sửa AuthApi/interface/contract/migration/FE/dependency/CommentModerationControllerIntegrationTest; không SQL Neon/secret/xóa file, không merge main/rebase. T-06 giữ 🔄 chờ leader review; Q-T06-01…06 đều đã chốt, không còn câu hỏi mở.

## 2026-11-15 · G0 T-07 — leader duyệt 10 file

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T07-01 | Giải chồng lấn/khoảng đảo | Mỗi hạng một dòng; chồng lấn lấy giải đầu tiên theo thứ tự JSON; khoảng fromRank > toRank bỏ qua. Chỉ đọc ranking tới max(toRank) của giải hợp lệ, dừng sớm khi hết dòng. Không sửa validation T-06 | ✅ Chốt |
| Q-T07-02 | Role trong port email | AuthApi.contactEmails không kiểm role/currentUser, nhất quán maskedEmails/publicProfiles. ContestAdminService kiểm CONTENT_EDITOR và PUBLISHED + ranking_published_at trước lookup. Thêm ArchUnit trong ContestWinnersIntegrationTest đã thuộc allowlist: chỉ production class thuộc community.contest được gọi port, kể cả khi gọi implementation cụ thể | ✅ Chốt |
| Q-T07-03 | Audit và nhất quán | Mỗi lần thành công, kể cả [], ghi CONTEST_WINNERS_VIEW; không email trong audit/log; audit lỗi không trả email. Transaction REPEATABLE_READ có ghi audit, Cache-Control no-store | ✅ Chốt |
| Q-T07-04 | Làm trong lúc chờ main | Leader cho soạn worktree nháp từ 24264bf, chạy AuthApi*Test, ContestWinnersIntegrationTest, ModuleBoundaryTest, CommunityArchitectureTest; chưa commit. Có hash main chứa #209 do leader báo mới tạo spec/021-t07, chuyển 10 file rồi full verify/commit/Draft PR | ✅ Chốt |

FR-024, AT-18/19. Chữ ký mới `Map<Long, String> contactEmails(Set<Long> userIds)`; chỉ trả email các ID được hỏi và tồn tại, tập rỗng không query, input sai bị từ chối như publicProfiles. Không đổi các hàm AuthApi đang có. Không @MockitoBean AuthApi/SettingsApi; dùng bean thật hoặc spy/mock lớp cụ thể. Chỉ mới winners response có email; thiếu cuộc thi 404 CONTEST_NOT_FOUND, chưa công bố 409 CONTEST_LOCKED; không mã lỗi mới.

Allowlist đúng 10 file: AuthApi.java, AuthApiService.java, ContestAdminService.java, web/ContestAdminController.java, web/ContestViews.java, AuthApiServiceTest.java, AuthApiIntegrationTest.java, ContestWinnersIntegrationTest.java (mới), questions.md và tasks.md của 021. Không SecurityConfig/contract/FE/migration/dependency/Neon. Xóa Env:DEBUG trước mỗi Maven, dùng TEMP ASCII riêng. Targeted nháp trước; sau hash chính thức chạy full verify, báo F/E/S và JaCoCo LINE ≥ 80%; chỉ năm ngoại lệ môi trường đã duyệt. Commit `feat(contests): add audited winner contacts`. Chưa có kết quả test tại thời điểm ghi G0 này.

### Targeted nháp từ đầu #209

Chạy đúng `AuthApi*Test,ContestWinnersIntegrationTest,ModuleBoundaryTest,CommunityArchitectureTest` trên worktree detached từ `24264bf4af6eced18dcfd59c7e16cb30d241befc`: **76/F0/E0/S0**, native exit **0**, **BUILD SUCCESS**, 5 suite, hoàn tất `2026-11-15T15:40:31+07:00` (Maven 02:00). AuthApiIntegrationTest **4**, AuthApiServiceTest **31**, ContestWinnersIntegrationTest **27**, ModuleBoundaryTest **9**, CommunityArchitectureTest **5**; tổng năm XML khớp Maven. Env:DEBUG đã xóa, TEMP ASCII riêng; không skip/miễn assertion, không dùng ngoại lệ. Log/XML tại `target/t07-draft-targeted-1.log`, `target/surefire-reports/`.

Test xác nhận port không phụ thuộc principal, chỉ trả tập ID được hỏi; endpoint guard trước lookup, đúng email/giải kể cả hạng 50/51/101, khoảng chồng lấn/đảo và dừng theo giới hạn/hết dòng. Mỗi lần xem thành công (kể cả []) ghi audit, audit lỗi không trả email; email không vào audit/log hoặc các response khác của 021. ArchUnit kiểm production và ca âm: learning, package gần giống `community.contests` và gọi implementation cụ thể đều bị chặn; caller thuộc `community.contest` được phép.

Self-check nháp: **Global PASS** (stack/dependency/contract giữ nguyên, boundary xanh); **Business PASS trong phạm vi targeted** (guard và lọc hạng server, profile/email theo tập winners, audit/PII đúng); **Safety PASS** (đúng 10 file, không đổi hàm AuthApi cũ/SecurityConfig/migration/FE, không Neon/secret/xóa file, không @MockitoBean AuthApi/SettingsApi). Chưa tạo nhánh chính thức/commit, chưa full verify; chờ hash main chứa #209 do leader gửi.

### Leader đổi chỉ đạo base — triển khai ngay từ đầu #209

Leader cho tạo `spec/021-t07` từ đúng `24264bf4af6eced18dcfd59c7e16cb30d241befc`, không chờ hash main: T-07 không cần thay đổi mới của main. #209 được leader merge giữ commit này làm cha, Draft PR T-07 base main ghi **phụ thuộc #209** nếu bản gộp chưa lên GitHub; không rebase. Chỉ khi #209 phải sửa, leader báo để chốt tiếp. Đã chuyển đúng 10 file từ nháp, đối chiếu SHA-256 của 8 file Java khớp bản targeted 76/F0/E0/S0; chuẩn bị full verify. ErrorCode không đổi, không thêm mã. Chỉ commit/push/Draft PR sau gate, message `feat(contests): add audited winner contacts`.

### Full verify — bàn giao có điều kiện theo ngoại lệ đã duyệt

- Full `mvnw.cmd -B verify` (chỉ thêm TEMP ASCII riêng): **6.521/F1/E0/S0 — FAILED**, **409 suite**, native exit **1**. Bắt đầu `2026-11-15T15:47:02.6988507+07:00`, kết thúc `16:17:38+07:00`, Maven **30:33**. Tổng XML khớp Maven; **8 file Java** giữ nguyên SHA-256 qua lượt full, không skip/hạ ngưỡng/failure-ignore.
- Failure duy nhất: `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`, assertion p95 **30.496 ms < 30.000 ms** không đạt (vượt 496 ms). Đây đúng assertion p95 trong năm ngoại lệ môi trường đã được leader duyệt, không mở rộng miễn sang lỗi chức năng/query count. Năm lần confirm 5.000 dòng đã qua kiểm tra kết quả trước assertion p95; các assertion query-count phía sau chưa được thực thi vì test dừng tại p95.
- Giữ nguyên verdict full FAILED. Chạy riêng `jacoco:report@report jacoco:check@check` trên **đúng exec full**: native exit **0**, mọi coverage check đạt; LINE **15.675/16.134 = 97,155076%**, một session, class mismatch **0**. Exec SHA-256 `E199241B375FC7E55FF3BA2C1D63F2267C8F0196B485C104FDA0185B84E19FBA`. Coverage riêng không biến full thành xanh.
- Diagnostic một lần, chạy đúng method NFR-P04 **không sửa** để thử thực thi các assertion query-count bị chặn phía sau p95; TEMP/exec riêng, không thay kết quả full. Diagnostic **1/F1/E0/S0 — FAILED**, native exit **1**, kết thúc `2026-11-15T16:34:15+07:00`: samples `[37672, 20078, 17594, 17666, 18123]` ms, p95 **37.672 > 30.000 ms**. Vẫn dừng tại p95, nên không nhận query-count 21/20 là đã chạy trong hai lượt này. Không chạy lặp để chọn kết quả xanh và không sửa file ngoài allowlist. Source importer không đổi so với base #209; lịch sử T-06 trên cùng parent đã có full xanh, nhưng không dùng kết quả đó thay verdict T-07.
- Evidence full tách riêng trước diagnostic: `target/t07-full.log`, `t07-full-meta.json`, `t07-full-result.json`, `t07-full-reports/` (409 XML), `t07-full-jacoco.exec`, `t07-full-coverage.log`, `t07-full-coverage-result.json`, `target/site/jacoco/`; diagnostic ở `t07-nfr-diagnostic.log`, `t07-nfr-diagnostic-report.xml`. Env:DEBUG được xóa trước từng Maven; full exec giữ nguyên hash sau diagnostic.

Self-check trước Draft PR: **Global PASS** (stack/dependency/contract đúng; ModuleBoundaryTest, CommunityArchitectureTest và luật email xanh); **Business PASS trong test của T-07** (27 winners test, port nội bộ không principal, guard trước lookup, email theo tập winners và audit/no-store đúng); **Safety PASS** (đúng 10 file, không đổi hàm AuthApi cũ/ErrorCode/SecurityConfig/contract/migration/FE, không Neon/secret/xóa file/rebase). **Gate full FAILED có điều kiện** do đúng ngoại lệ p95 trên, không báo xanh và ghi rõ giới hạn query-count. Backend không cấu hình linter Java riêng; diff sạch/không BOM được kiểm trước commit. T-07 giữ 🔄 chờ leader review/verify bản gộp; không còn câu hỏi nghiệp vụ mở.

# Câu hỏi và quyết định — Spec #023

## 2026-11-14 · Leader chốt khi viết spec

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | `topics.prereq_ids` chưa code nào dùng (003 Q1 "mọi chủ đề mở") — 023 có làm cổng khoá không | Có: `LOCKED` + `lockedBy` ở danh sách/chi tiết 003, `TOPIC_LOCKED` khi học/luyện/kiểm tra; chủ đề không đặt tiên quyết vẫn mở | ✅ Chốt |
| Q2 | Danh mục khoá màn 078 (UC-116 để ngỏ) | Chỉ khoá nghiệp vụ §0.2; không model AI, timeout, cron, trọng số mastery, ngưỡng xếp lớp, secret; chuyển chỗ đọc 013/014/015/019; 021/022 dùng thẳng | ✅ Chốt |
| Q3 | Sửa đề đã công bố | Không; chỉ lưu trữ/nhân bản | ✅ Chốt |
| Q4 | Số migration | V10 (021 lấy V9); merge sau V9 | ✅ Chốt |
| Q5 | Chống sửa đè đề nháp | `etag` = SHA-256 nội dung; không thêm cột | ✅ Chốt |
| Q6 | Đề đang gắn cuộc thi | `learning.api.ExamUsageGuard`, 021 cài; `learning` không đọc bảng `community` | ✅ Chốt |
| Q7 | SCR-058 | Thuộc 024 | ✅ Chốt |

## Dữ kiện đã kiểm trên main `16603e7`

- `exams.hsk_level` CHECK 1–6; `exams.sections` JSONB `[{index,name,type,duration_min}]`; `exam_questions(exam_id, question_id, section_index, order_index, points)`.
- `/api/exams` (012) chỉ liệt kê `PUBLISHED`; `ExamAttemptService` chặn đề không `PUBLISHED`.
- `topics.prereq_ids BIGINT[]` có trong entity `Topic`, không nơi nào đọc để khoá; 003 đã có `COMPLETED` theo lượt `TOPIC_TEST` ≥ 90%.
- `cnhsk.assistant.daily-limit` đọc bằng `@Value` ở `AssistantDailyLimit`; các khoá khác đọc qua cấu hình `cnhsk.*` trong `application.yaml`.
- 5 test `learning.practice.*` đọc `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml`.

## 2026-11-14 · G0 cài đặt đã duyệt

- Base triển khai: `origin/main` `110867c60a3d2a3d74bd2a5a68f685b602f136b1`, có V9.
- Đúng 12 file theo G0; V10 chỉ thêm bảng, không seed. Không chạy SQL/migration trên Neon.
- Bỏ test nâng cấp V9→V10 bằng DB riêng theo leader; kiểm schema, Flyway validate/no-op
  và bảo toàn dữ liệu khi gọi lại migration trên PostgreSQL Testcontainers.
- Mặc định đọc `cnhsk.<key>` từ yaml, thiếu/sai kiểu/ngoài khoảng làm khởi tạo thất bại.
  Bổ sung duy nhất mặc định `cnhsk.contest.submit-grace-seconds: 5` của 021 còn thiếu trên base.
- Cache theo khoá 30 giây, gồm mặc định/fallback; đọc DB khi cache miss dùng
  `PROPAGATION_NOT_SUPPORTED`, tạm ngưng transaction caller để lỗi SELECT không làm hỏng nó.
- Dòng DB sai kiểu/tràn int/ngoài khoảng → mặc định yaml, cùng giới hạn WARN toàn service
  một lần/phút; log tĩnh, không exception/giá trị DB. Phục hồi sau TTL hoặc invalidate.
- Helper invalidate cấp package đồng bộ với lần nạp; T-02 gọi sau commit ghi/reset.
- Cache hit kiểm không DB/transaction và báo độ trễ sau warm-up; không assert timing JUnit.
  REST/audit/reset và ràng buộc chéo khi ghi thuộc T-02; chuyển consumer thuộc T-03/T-04.

## 2026-11-14 · Leader duyệt tiếp tục gate

- T-01 chỉ đổi backend, yaml và tài liệu; FE không phải gate bắt buộc. Gate bắt buộc:
  targeted → ModuleBoundaryTest → full Maven verify (Java 17, Docker).
- Full Vitest: 2.676 test, 2 fail (route SCR-012 không thấy heading và SCR-059).
  Leader cho chạy riêng `src/app/routes.test.tsx`; kết quả 24/24 pass, xác định chập chờn
  và được tiếp tục G2. SCR-059 chạy riêng 64/64 pass theo ngoại lệ đã duyệt.
- Giữ nguyên source/test FE; ghi cả kết quả full và chạy riêng trong Draft PR.

## 2026-11-14 · Leader duyệt G0 quản trị đề thi

- Base triển khai: `a9b07d48666a9ad963b0f0a686326329f55a6c74`; nhánh `spec/023-t05`.
- `created_by` lấy từ principal `AuthenticatedUser` ở controller, không qua `AuthApi`.
- Đạt giữ bảng exams / QuestionItemFactory (012); leader báo Đạt và báo Tường về
  interface `learning.api.ExamUsageGuard` để 021 cài guard ở PR riêng.
- Duyệt đưa validator thuần đọc và interface guard vào phần danh sách/chi tiết.
  Inject `List<ExamUsageGuard>`; rỗng → `inUse=false`, bất kỳ guard true → true.
- Giữ nguyên factory/engine 012. Media không dựng được → `QUESTION_UNSUPPORTED`.
- Câu trùng hoặc index ngoài sections → `422 VALIDATION_FAILED`; title clone thiếu/null
  lấy nguồn, chuỗi trắng → 422; tìm kiếm escape `%`, `_` như ký tự literal.
- Audit `EXAM_CLONE` trên đề mới, detail `{sourceExamId}`, cùng transaction ghi đề.
- Etag SHA-256 từ nội dung đề + liên kết câu theo thứ tự cố định, không gồm metadata
  ngân hàng câu; khóa `FOR UPDATE` → kiểm DRAFT → so etag. Clone không cần etag.
- Ba mã mới đặt ngay trước `INTERNAL_ERROR`, sau `INVALID_GAME_CODE` hiện có.

### Đề nhập cũ thiếu thông tin phần thi — mở rộng allowlist 17 → 18 file

Đề import từ 012 chỉ có index/name; bổ sung type qua màn sửa đề.

- Đọc thiếu `type` → `type=null`; thiếu `duration_min` → `durationMin=null`; không đoán.
- Mỗi phần thiếu type có `publicationErrors` `SECTION_TYPE_MISSING`; phần công bố sau
  dùng lại validator để chặn. Đề cũ PUBLISHED vẫn xem được ở chế độ chỉ đọc.
- Clone giữ nguyên JSON sections gốc; bản clone DRAFT phải chọn type khi PUT.
- Etag hash JSON gốc, không bổ sung mặc định vào dữ liệu trước khi hash.
- Leader duyệt thêm `contracts/openapi-content-admin.yaml`: tách `ExamSectionView` nullable
  cho phía đọc; `ExamSection`/`ExamWrite` vẫn bắt buộc type và durationMin hợp lệ.
- Không sửa importer, factory hoặc engine 012; test detail/null/lỗi, clone nguyên JSON,
  etag ổn định và chỉnh sửa bổ sung type.

## 2026-11-14 · G0 API quản trị cài đặt đã duyệt

- Base: `origin/main` `ca7707832ef44309d14c4c5e55dd3a528ff1cba9`; nhánh `spec/023-t02`.
- Sau khi #187 merge, rebase theo chỉ đạo leader lên `dc8adb426c4f044aa49d7fab69d3f5a110dab0b5`;
  giữ nguyên mã lỗi EXAM, kiểm thử và các quyết định đã có trên main, chạy lại G2 sau tích hợp.
- Leader duyệt 19 file và bổ sung tường minh `SettingKey.java` để khai `auditId`: tổng 20 file.
- spec đặt controller ở shared nên tên người sửa lấy qua interface đảo chiều:
  `shared.api.ActorNameLookup`, adapter `auth.service.SettingsActorNameLookup` gọi `AuthApi.displayNames`.
  Không đọc bảng auth từ shared; chỉ trả id/tên hiển thị, không email.
- Mọi PUT/DELETE lấy cùng advisory transaction lock `settings`. Kiểm tra khoảng từng khoá và
  `min-hanzi < max-hanzi` theo giá trị hiệu lực sau ghi hoặc reset; vi phạm trả 422 `SETTING_OUT_OF_RANGE`.
- Audit dùng `target_type = SETTING`, `target_id = SettingKey.auditId` khai tường minh 1–18;
  không bao giờ dùng lại id, không dùng ordinal/hash. `oldValue` là giá trị hiệu lực trước ghi;
  reset có `newValue = null`. Ghi audit cả khi giá trị không đổi hoặc reset không có dòng override.
- Ghi/reset và audit cùng transaction. Chỉ invalidate cache của instance trong `afterCommit`;
  rollback không invalidate. Instance khác vẫn theo TTL 30 giây của API đọc.
- Chỉ 18 khoá whitelist xuất hiện hoặc sửa được; khoá nhạy cảm/không tồn tại trả 404 trước khi
  đọc body hay truy vấn store. Giá trị không phải số nguyên hoặc ngoài khoảng trả 422.
- Hai mã `SETTING_NOT_FOUND`, `SETTING_OUT_OF_RANGE` đặt cuối ngay trước `INTERNAL_ERROR`;
  nếu PR #187 merge trước thì giữ mọi mã EXAM trên main khi rebase theo chỉ đạo leader.
- Gate backend: targeted → hồi quy auth/security/audit/cài đặt + `ModuleBoundaryTest` → full verify
  với Java 17, Docker/Testcontainers; không chạy SQL/migration trên Neon. FE không thay đổi.

## 2026-11-15 · G0 cổng tiên quyết chủ đề đã duyệt

- Nhánh `spec/023-t07`, base `8e4c3906f9739e93221d69b5de4fabfe4a3623b8`, đúng 24 file.
  Không chờ #195; leader xử lý thứ tự mã lỗi khi tích hợp. TOPIC_LOCKED đặt cuối trước
  INTERNAL_ERROR; sau khi gộp #195 sẽ đứng sau EXAM_PUBLISH_INVALID/EXAM_IN_USE.
- Predicate completed giữ đúng `bool_or(ref_type='TOPIC_TEST' AND status='SUBMITTED'
  AND max_score>0 AND score*100>=90*max_score)`, định nghĩa một chỗ trong TopicLockQuery,
  TopicQueryRepository tham chiếu lại. Tiên quyết trực tiếp dùng AND theo đúng user;
  không lan khoá đệ quy theo trạng thái UI LOCKED của tiên quyết.
- ID trong prereq_ids không tồn tại được BỎ QUA, không khoá và không trả name null;
  LockedTopicRef.name bắt buộc. T-08 sẽ chặn ID không tồn tại khi đặt tiên quyết.
- Danh sách giữ đúng một SQL aggregate cho toàn trang, gồm lockedBy; không N+1 hoặc cache.
  Detail giữ query tổng hợp + words. LOCKED ưu tiên mọi trạng thái; tiến độ/best score giữ nguyên.
- Khoá: bốn can* false, testBlockedReason=TOPIC_LOCKED, retryAvailableAt=null,
  requiredWords theo công thức cũ. Không khoá trả lockedBy=[].
- Start mới: FOR SHARE topic đích → SELECT tiên quyết mới nhất → tạo lượt, cùng transaction.
  Admin UPDATE cùng row phải serialize; share trước → lượt commit rồi mới thêm tiên quyết;
  admin trước → start đọc điều kiện mới. Không thêm khoá này cho resume.
- Study/test active còn hạn trả 200 trước cổng. Practice POST luôn tạo mới nên phải qua cổng;
  GET session/active, answers/finish/abandon của lượt cũ giữ nguyên. Lượt hết hạn không bypass.
- TOPIC_LOCKED 422 trả details.lockedBy=[{id,name}]; giữ protected fields ở handler.
  YAML 003, TopicContractTest và đủ năm test practice đọc YAML đổi cùng PR; giữ error_code string
  và nguyên status/lỗi engine chung, không thêm enum toàn cục hoặc nới assert cũ.
- Trước mọi Maven command xoá Env:DEBUG; Java17/Docker/Testcontainers; đo/log thời gian,
  không assert hiệu năng theo thời gian. Targeted → hồi quy + ModuleBoundaryTest → full verify;
  chỉ năm ngoại lệ môi trường cũ được báo kèm tên/số đo, lỗi khác dừng.

### Schema lỗi topic tách khỏi ErrorResponse dùng chung — leader duyệt sửa

- Lượt full verify đầu đã dừng FAILED sau 1580 test, F2/E0/S0:
  ReferenceContractTest và ReminderContractTest. Nguyên nhân là thêm `details` vào
  ErrorResponse của YAML 003 làm thay đổi schema mà contract 018/020 tham chiếu,
  trong khi runtime của hai module vẫn giữ schema cũ. Giữ lịch sử lỗi này trong evidence.
- Giữ nguyên ErrorResponse chung, không thêm details. Thêm TopicLockedError và
  TopicLockedDetails riêng; chỉ response 422 của ba POST study-sessions,
  practice-sessions và tests tham chiếu schema mới. Annotation TopicController dùng
  cùng schema; TopicContractTest đối chiếu ref, field, required và lockedBy của runtime/YAML.
- Không sửa test hoặc YAML 018/020; allowlist vẫn 24 file. Gate chạy lại:
  targeted → ReferenceContractTest + ReminderContractTest + TopicContractTest → hồi quy
  + ModuleBoundaryTest → full verify; xoá Env:DEBUG trước mỗi Maven command.

## 2026-11-15 · G0 API tiên quyết đã duyệt

- Allowlist 20 file: 19 file G0 và Topic.java chỉ thêm import/@DynamicUpdate, không đổi mapping khác.
- Theo chỉ đạo mới, tạo spec/023-t08 từ đúng `00088afdda54ba54396cccde35bd67cc19af3c84`
  (đầu PR #203), không chờ merge và không rebase. Draft PR base main ghi phụ thuộc #203;
  trước khi #203 merge, diff có thể bao gồm T-07. Leader merge --no-ff giữ commit cha.
- Gốc theo cạnh còn hiệu lực: bỏ ID mất khi đọc prerequisite, tính isRoot và DFS;
  chỉ trỏ ID mất vẫn là gốc, khớp cổng T-07. PUT chặn mọi ID mới không tồn tại bằng
  404 TOPIC_NOT_FOUND, sau ghi danh sách đích sạch. CYCLE kiểm trước NO_ROOT;
  PREREQUISITE_NO_ROOT giữ làm lớp bảo vệ.
- Khoá transaction chung `pg_advisory_xact_lock(hashtextextended('topic-prereq',0))`
  trước FOR UPDATE topic đích, rồi đọc đồ thị mới nhất; DFS stack/ba màu trên toàn đồ thị;
  chỉ UPDATE prereq_ids và audit trong cùng transaction. Writer tương thích FOR SHARE của T-07.
- Giữ thứ tự input, không âm thầm dedup; tối đa 10 int64. Warning cấp cao hơn vẫn 200,
  PUT luôn có warnings array; GET bỏ warnings. Audit TOPIC_PREREQ_UPDATE/TOPIC/id có before/after
  đúng principal AuthenticatedUser, kể cả PUT không đổi; lỗi audit rollback UPDATE.
- TopicPrerequisiteError chỉ dùng cho 422 của PUT: details.path cho CYCLE, mảng validation
  cho VALIDATION_FAILED, không bắt buộc cho NO_ROOT. Giữ ErrorResponse chung, YAML 003 và contract/test 018/020.
  TopicContractTest chỉ bổ sung hai path admin vào exact inventory, giữ mọi kiểm chứng learner.
- Topic.java thêm @DynamicUpdate để importer-style save thay name/hsk/order không ghi lại
  prereq_ids cũ. Test giữ managed entity cũ, PUT ở transaction khác commit trước rồi save name;
  đọc DB sau cả hai commit phải giữ prerequisite mới. Không sửa TopicRowWriter.
- Hai mã PREREQUISITE_CYCLE, PREREQUISITE_NO_ROOT sau TOPIC_LOCKED, ngay trước INTERNAL_ERROR
  trên base nhánh. Leader giữ/tích hợp các mã QUESTION_NOT_PENDING/AI_GENERATION_* của main.
- Matcher /api/admin/topics và /** cần CONTENT_EDITOR trước /api/admin/**;
  ADMIN đơn lẻ không có quyền. Cookie PUT cần CSRF, bearer giữ cơ chế hiện tại.
- Gate Java17/Testcontainers: xoá Env:DEBUG trước mọi Maven command; targeted → hồi quy
  cổng/practice/contract/security/audit/importer topic + ModuleBoundaryTest → full verify.
  NFR-P03 chỉ đo/log đồ thị 2000 node, không assert thời gian. Không migration/dependency/FE/Neon.
- Targeted đầu: 93 F2/E0/S0. Hai test mới validWritesPreserveOrderWarnAndAuditEvenWhenTheListDoesNotChange
  và selfTwoAndThreeStepCyclesAreRejectedAndTheWholeGraphIsChecked so JsonNode trực tiếp:
  JSON đọc lại dùng IntNode, expected từ List<Long> dùng LongNode. Leader duyệt sửa 5 assertion
  sang kiểm array/số nguyên/int64 rồi List<Long> containsExactlyElementsOf; giữ giá trị, thứ tự,
  số lượng, fixture và production. Giữ log lượt đầu trong evidence.
- Leader cho tự sửa lỗi assertion trong test mới thuộc task mà không dừng chờ lần nữa;
  các lỗi khác vẫn theo quy trình dừng và năm ngoại lệ môi trường đã duyệt.
- Gate sau sửa: targeted 93 F0/E0/S0; hồi quy 634 F0/E0/S0, gồm importer topic,
  contract 018/020, năm test practice đọc YAML và ModuleBoundaryTest 9/9;
  full verify 6216 F0/E0/S0, JaCoCo LINE 97,044617% (15269/15734), không dùng ngoại lệ môi trường.
  DFS 2000 node/19945 cạnh: targeted 4,2836 ms, full 9,0914 ms; chỉ log, không assert timing.

## 2026-11-15 · G0 giao diện tiên quyết và khoá chủ đề đã duyệt

- Nhánh `spec/023-t11`, base `3716330e91aee70499ed5360129e503685b7ce9c` (đầu #207).
  Phụ thuộc #207; không rebase/merge main vào nhánh. Leader xử lý thứ tự merge.
- Leader duyệt 39 file: bỏ hai file Java khỏi đề xuất 41 file ban đầu.
  SCR-067 dùng layout admin nên KHÔNG thêm SCREEN_NAMES; giữ nguyên
  AssistantPromptBuilder.java và AssistantPromptBuilderTest.java. Gate BE chỉ chạy
  AssistantPromptBuilderTest, xoá Env:DEBUG trước mỗi Maven command; không full verify.
- Duyệt bổ sung file thứ 40: `cnhsk-web/src/features/library/pages/Scr028DeckListPage.test.tsx`.
  Chỉ thêm `lockedBy: []` vào fixture topic dùng chung cho mock getTopics và promise
  phân trang (typecheck báo tại dòng gọi mock/resolver); không đổi assertion/nghiệp vụ library.
  Leader cũng duyệt trước các test/fixture/preview ngoài allowlist chỉ thiếu lockedBy hoặc
  kiểu LOCKED tương ứng do contract mới; phiên này không cần thêm file nào khác.
- SCR-067 gọi GET có hsk/q/page/size; hai bộ lọc/phân trang độc lập cho topic và ứng viên.
  Giữ lựa chọn xuyên trang/tìm kiếm; tối đa 10, không trùng, không tự trỏ; [] để bỏ hết.
  Cấp chọn trên UI dùng SUPPORTED_HSK_LEVELS, dữ liệu/types vẫn giữ HSK 1–9.
- PUT gửi thứ tự đã chọn, chặn gửi lặp khi pending, dùng shared apiRequest để giữ CSRF/auth.
  CYCLE hiện đường vòng khép kín theo details.path; tên ngoài trang được tra qua GET không lọc,
  size=50 và đọc tiếp trang cần thiết; không thêm endpoint. Tra lỗi/ID mất hiện ID rõ ràng,
  không bịa tên. NO_ROOT giữ draft; 404 đọc lại và đối chiếu ID trước lần lưu thủ công tiếp theo.
  Mạng/5xx đọc lại trước khi cho gửi tiếp; không tự retry PUT. Warnings từ response 200
  hiện riêng sau khi lưu. Giữ nguyên ghi chú phân biệt tiên quyết chủ đề (cổng 90%) và KID.
- SCR-011 tách article/link tên chủ đề để link lockedBy không lồng link. SCR-011/012 hiện
  biểu tượng, chữ Đang khoá, ngưỡng 90% và tên tiên quyết bấm được; giữ tiến độ cũ.
  LOCKED ẩn điều hướng bắt đầu học/luyện/kiểm tra. Ba POST bị TOPIC_LOCKED dùng
  details.lockedBy và chuyển trang hiện tại sang trạng thái khoá ngay. Vẫn khôi phục lượt
  học/luyện/kiểm tra còn hiệu lực qua hook hiện tại; không tạo lượt mới khi đã biết LOCKED.
- Route SCR-067 chỉ CONTENT_EDITOR, layout admin; menu dùng role đúng cơ chế AdminLayout.
  ADMIN đơn lẻ không kế thừa quyền. App/shared và vocab 003 được leader thông báo người giữ.
  Không sửa backend nghiệp vụ, YAML, schema, dependency, engine practice hoặc dữ liệu Neon.

### Evidence gate và lịch sử sửa trong phạm vi

- npm ci PASS. Typecheck đầu phát hiện fixture library thiếu lockedBy (đã dừng xin và được
  duyệt file 40); các lỗi types mới trong allowlist được sửa, không nới required lockedBy.
- Targeted đầu 256 test: F3 trong test route mới, do kỳ vọng mọi role đều có tiêu đề 403
  giống nhau. Giữ hành vi hiện có: TEACHER/MODERATOR/ACCOUNTANT có thông báo khu vực
  chưa xây dựng; test vẫn kiểm /403, không mở SCR-067 và không gọi API. Không sửa auth guard.
- Targeted tiếp theo F2 trong test mới của study/practice do thông báo tạm bị thay DOM
  khi trạng thái khoá chuyển lên trang cha. Sửa production để chỉ trang cha render lý do,
  không tạo thông báo tạm; không nới assertion. Targeted cuối 256/256, 16 file PASS.
- Full Vitest: 214 file, 2871/2871 PASS, không dùng ngoại lệ chập chờn. Log cục bộ:
  `C:\KLTN_FALL2026\023-t11-fe-test.log`.
- Lint đầu có lỗi test mới import fixture preview (quy tắc chỉ file preview được import).
  Đổi sang fixture tự soạn ngay trong test, giữ assertion. Kiểm lại 19/19 test liên quan PASS.
  Sau chỉnh giao diện nút lưu, SCR-067 14/14 PASS. Lint cuối: 0 lỗi, 1 warning cũ
  react-refresh/only-export-components ở shared/ui/LevelBadge.tsx (file không đổi).
- Build TypeScript/Vite cuối PASS. QA Chromium headless bằng dữ liệu tự soạn, chỉ local:
  24 trạng thái preview (8 trạng thái × 320/375/640) và 3 phép đo route thật, tổng 27;
  document.scrollWidth không vượt clientWidth, không page error. Kiểm chọn/lưu với CSRF,
  vòng bằng tên, cảnh báo cấp, khoá list/detail, POST đua TOPIC_LOCKED và ADMIN bị chặn.
  Ảnh và số đo: `C:\KLTN_FALL2026\qa-023-t11\`; không commit artifact QA/file nháp.
- Maven đầu bị sandbox chặn dependency resolution trước khi chạy test; chạy lại có quyền
  truy cập phù hợp: Java 17, AssistantPromptBuilderTest 19 F0/E0/S0, BUILD SUCCESS.
  Log cuối: `C:\KLTN_FALL2026\023-t11-assistant-test-final.log`.
- Self-check: PASS phạm vi 40 file, contract/types, quyền route/menu, không thêm thư viện,
  không sửa SQL/BE nghiệp vụ/YAML, không commit secret/PII/dữ liệu đề thi thật.
  Hai file Java, package.json/package-lock.json và shadow-plans giữ ngoài commit.

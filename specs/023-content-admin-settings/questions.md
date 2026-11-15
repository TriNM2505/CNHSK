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

## 2026-11-15 · G0 công bố/lưu trữ đề đã duyệt

- Nhánh `spec/023-t06`; 15 file đã duyệt. `ExamUsageGuard.java` chỉ thêm Javadoc,
  giữ chữ ký. Đạt giữ exams/factory 012; Tường cài guard cuộc thi 021, phối hợp qua leader.
- Bắt đầu sau #189 merge (`3164b35`), cập nhật nhánh lên main mới nhất trước gate,
  base sau rebase `9e6773ba27a5572de438a2a01d1ba8780d0d9911` theo chỉ đạo leader.
  Giữ toàn bộ mã lỗi main; hai mã `EXAM_PUBLISH_INVALID`, `EXAM_IN_USE` đặt sau
  `GAME_UNAVAILABLE`, cuối ngay trước `INTERNAL_ERROR`.
- Publish chỉ khoá dòng exam: kiểm DRAFT, etag, dùng validator chỉ đọc đã có; gom đủ lỗi
  trong `details` của `EXAM_PUBLISH_INVALID`. Đề cũ thiếu type bị chặn bằng
  `SECTION_TYPE_MISSING`; không đoán type hoặc đổi importer/factory/engine.
- Khoá tư vấn chung: `pg_advisory_xact_lock(hashtextextended('exam:usage:' || examId, 0))`.
  Bất kỳ luồng nào lấy cả khoá này và khoá dòng `learning.exams` PHẢI lấy khoá tư vấn TRƯỚC
  (023 archive, 021 công bố/sửa cuộc thi gắn đề). Giữ khoá cùng transaction tới commit/rollback.
  Javadoc `learning.api.ExamUsageGuard` là định nghĩa chuẩn để hai bên dùng cùng chuỗi khoá.
- Archive: khoá tư vấn → khoá dòng → kiểm trạng thái/guard → đổi trạng thái + audit.
  Đã ARCHIVED trả 200, không gọi guard/không thêm audit, giữ nguyên `published_at`.
  Không có bean guard hoặc mọi bean false → không chặn; một true → `EXAM_IN_USE` 409.
- Audit EXAM_PUBLISH/EXAM_ARCHIVE đúng principal, target EXAM/id và before/after trạng thái;
  cùng transaction ghi. Lỗi audit/ghi rollback toàn bộ. Lượt thi đã mở tiếp tục bằng snapshot.
- Test cạnh tranh chạy archive/publish trên cùng đề ở cả hai thứ tự, kiểm chờ khoá PostgreSQL,
  không deadlock, trạng thái và audit đúng theo thứ tự thắng; mô phỏng writer 021 dùng cùng khoá.
- Targeted đầu tiên: 165 F1/E0/S0, ca nộp lượt đang mở nhận 403 vì fixture `@Transactional`
  chưa commit còn `PracticeService.finish` dùng REQUIRES_NEW. Leader duyệt sửa riêng fixture:
  commit thật, code/title/user duy nhất; AfterEach chỉ dọn ID test sở hữu, child trước parent.
  Giữ nguyên kỳ vọng 200/SUBMITTED/điểm đúng, không sửa engine hoặc nới test.
- Gate sau rebase: targeted → hồi quy + ModuleBoundaryTest → full verify một lần;
  Testcontainers/Java 17, báo F/E/S/LINE thực tế; lỗi ngoài ngoại lệ đã duyệt phải dừng.

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

## 2026-11-15 · G0 kiểm thử hợp đồng quản trị nội dung đã duyệt

- Nhánh `spec/023-t12`, base `e681945eb8f26d67efdf42040bb5fefbb6db6edb` (main đã merge #207,
  có T-05/T-06/T-08). Không rebase/merge main vào nhánh.
- Đúng 3 file: thêm `src/test/java/com/cnhsk/contract/ContentAdminContractTest.java`, sửa
  tasks.md và questions.md của 023. Không đổi controller, YAML, test khác hoặc dependency.
- Khoá đúng 10 path / 13 operation: settings 3/4, exams 5/7, topics 2/2.
  Đối chiếu hai chiều path + method + status giữa YAML và GET /v3/api-docs;
  đối chiếu tập operation đăng ký qua RequestMappingHandlerMapping, bắt cả endpoint bị ẩn.
- Namespace chỉ root hoặc root + "/": /api/admin/settings, /api/admin/exams, /api/admin/topics.
  Handler thuộc shared.settings hoặc learning.examadmin (kể cả package con) được kiểm cả
  khi mapping đi ra ngoài namespace. Phần vocab chỉ TopicAdminController, không gom
  TopicController của learner. Handler từ package khác đặt trong namespace admin vẫn bị kiểm.
  Mapping không có method tường minh và mapping trùng cũng fail; không bỏ qua HEAD/OPTIONS/PATCH/TRACE.
- Ca tự kiểm dùng dữ liệu trong bộ nhớ: path/method/status thiếu hoặc thừa, default/wildcard
  response, boundary namespace/package, metadata PathItem, owner lạ, mapping không method/trùng.
  Không thêm controller giả vào context, không sửa YAML để thử âm tính.
- Khi lệch thực tế, test gom báo cáo an toàn vào target/content-admin-contract-mismatches.json
  gồm path/method/status/owner; không commit báo cáo. Dừng báo leader, không sửa production/YAML
  hoặc nới assertion để xanh.
- Khảo sát base: ExamContractTest quét learning.exam.web, không khoá exam-admin. TopicContractTest
  đã kiểm kê đủ 5 path learner + 2 path admin của T-08; TopicAdminContractTest khoá đúng 2 path
  admin topics. Các test giữ nguyên, chạy hồi quy cùng ModuleBoundaryTest trước full verify.
- Test mới dùng AbstractIntegrationTest/Testcontainers riêng, Java 17, profile test/dev để bật
  Springdoc; MockMvcPrint.NONE. Không SQL/migration trên Neon. Xoá Env:DEBUG trước mọi Maven command.
  Gate: ContentAdminContractTest → ExamContractTest/TopicContractTest/TopicAdminContractTest/
  ModuleBoundaryTest → full verify, JaCoCo LINE ≥80%, chỉ 5 ngoại lệ môi trường đã duyệt.
- Sau T-12, thứ tự theo chỉ đạo mới của leader: T-03 → T-04 → T-09 → T-10;
  mỗi task tiếp theo lập G0 riêng, không triển khai cùng phạm vi T-12.

### Evidence gate

- Maven đầu bị sandbox chặn dependency resolution trước khi chạy test. Chạy lại với quyền
  truy cập dependency/cache và Docker phù hợp, giữ Java 17 và xoá Env:DEBUG trước mỗi lệnh.
- ContentAdminContractTest: 6 F0/E0/S0, BUILD SUCCESS. Bao gồm một ca đối chiếu runtime
  thực và năm ca tự kiểm trong bộ nhớ. 10 path / 13 operation và các tập status đều khớp;
  không sinh target/content-admin-contract-mismatches.json, không cần sửa controller/YAML.
  Log cục bộ: `C:\KLTN_FALL2026\023-t12-targeted-final.log`.
- Hồi quy: ExamContractTest 3/3, TopicContractTest 6/6, TopicAdminContractTest 3/3,
  ModuleBoundaryTest 9/9; tổng 21 F0/E0/S0, BUILD SUCCESS. Các test giữ nguyên.
  Log cục bộ: `C:\KLTN_FALL2026\023-t12-regression.log`.
- Full `mvnw.cmd -B verify`: 6457 F0/E0/S0, BUILD SUCCESS, toàn bộ coverage check PASS;
  JaCoCo LINE 97,115925% (15557 covered / 16019 total, 462 missed). Không dùng ngoại lệ
  môi trường; ContentAdminContractTest chạy lại trong full vẫn 6/6. Tổng thời gian 27:49 phút.
  Log cục bộ: `C:\KLTN_FALL2026\023-t12-verify.log`.
- Không có mismatch thực tế hoặc lỗi assertion mới cần sửa. Không sinh báo cáo mismatch,
  không sửa test khác/controller/YAML. File target và log cục bộ không commit.
- Self-check PASS: đúng allowlist 3 file, không BOM, diff check sạch, không TODO/skip mới,
  không thêm dependency, không đổi schema/SQL/FE, không commit secret hoặc dữ liệu thật.

## 2026-11-15 · G0 chuyển consumer hạn mức đã duyệt

- Nhánh `spec/023-t03`, base `43912434db80ea4027cdf0cdbc7f5a7f8686ea84`.
  Leader duyệt đúng 19 file (ADD 1, MODIFY 18), không rebase/merge main.
  Phần consumer 013/015 đã được leader điều phối; không gửi tin bên ngoài phiên.
- MODIFY production: `learning/credit/EntitlementService.java`, `WalletQueryService.java`,
  `learning/assistant/AssistantDailyLimit.java`, `learning/ai/TokenBudget.java`.
  Cả bốn giữ reference SettingsApi và đọc getInt tại thời điểm dùng; wallet đọc một lần
  cho mỗi projection FREE. PREMIUM giữ benefits hiện có, không dùng hạn mức FREE.
- MODIFY test credit: `EntitlementServiceTest`, `EntitlementServiceIntegrationTest`,
  `EntitlementConcurrencyIntegrationTest`, `EntitlementRedisIntegrationTest`,
  `WalletQueryServiceTest`, `WalletQueryIntegrationTest`, `CreditTransactionHistoryIntegrationTest`,
  `PlanMaintenanceIntegrationTest`, `PlanMaintenanceConcurrencyIntegrationTest`.
  MODIFY test 015: `AssistantDailyLimitTest`, `TokenBudgetTest`, `TokenBudgetIntegrationTest`.
  ADD `shared/settings/SettingsConsumerIntegrationTest.java`.
  Hai file tài liệu MODIFY: `tasks.md`, `questions.md`; toàn bộ test dùng đuôi `.java`.
- Q1 đã duyệt: TokenBudget.check đọc Redis bình thường dùng 0 khi chưa có key;
  chặn BUDGET_EXCEEDED nếu used >= budget từ SettingsApi. Budget 0 chặn trước supplier
  ngay lần đầu. Redis lỗi hoặc NumberFormatException vẫn fail-open và log.warn như 015;
  AiConfig validation giữ nguyên, không đổi Lua/key/TTL/hoàn lượt/trừ điểm/idempotency.
- Không reset counter khi sửa/reset setting, không thêm cache/fallback riêng cho consumer.
  Test cũ chỉ đổi wiring constructor sang mock lớp cụ thể SettingsService, giữ các fixture
  hạn mức và mọi assertion cũ. Không có @MockitoBean SettingsApi; test tích hợp mới dùng
  bean SettingsService/SettingsApi thật và API ADMIN để thay đổi/reset ba setting.
- Test mới kiểm AT-06 qua POST assistant thật, daily 0, tăng/reset không khởi động lại;
  billing 0/2/3, wallet/entitlement nhất quán, credit fallback, idempotency và hoàn quota;
  token 0 với key chưa có, tăng/giảm/reset không xoá số token đã dùng. Unit test kiểm
  đọc lại trên cùng instance, key/TTL daily và fail-open token khi Redis/counter lỗi.
- Dữ liệu tự soạn, chỉ Testcontainers PostgreSQL/Redis. Fixture snapshot/restore đúng
  ba setting và counter token chung, dọn counter theo learner riêng; không Neon/.env.
- Gate: Java 17, Remove-Item Env:DEBUG trước mỗi Maven command; targeted 13 lớp,
  hồi quy consumer/settings + ModuleBoundaryTest, full verify và JaCoCo LINE >= 80%.
  Chỉ năm ngoại lệ môi trường đã duyệt; mọi lỗi thực tế khác ngoài phạm vi phải dừng.

### Evidence gate và lịch sử sửa trong phạm vi consumer

- Targeted đầu: 152 F3/E0/S0, cả ba lỗi trong SettingsConsumerIntegrationTest mới:
  fixture format ZonedDateTime bằng BASIC_ISO_DATE có hậu tố offset, đọc sai key daily.
  Sửa fixture dùng toLocalDate() như production; không đổi production/key/TTL hoặc
  assertion cũ. Bổ sung assertion số quota để kiểm mặc định không thể pass với mảng rỗng.
  Log cục bộ: `C:\KLTN_FALL2026\023-t03-targeted.log` (không commit).
- Targeted sau sửa: 152 F0/E0/S0, gồm 6 test tích hợp mới và 12 lớp test đổi wiring.
  Log: `C:\KLTN_FALL2026\023-t03-targeted-final.log` (không commit).
- Hồi quy consumer/settings + ModuleBoundaryTest: 207 F0/E0/S0, boundary 9/9.
  Log: `C:\KLTN_FALL2026\023-t03-regression.log` (không commit).
- Full verify một lượt: 6562 F0/E0/S0, BUILD SUCCESS trong 27 phút 43 giây;
  ModuleBoundaryTest 9/9, JaCoCo LINE 97,141801% (15770/16234), coverage gate PASS.
  Không dùng ngoại lệ môi trường nào trong năm ngoại lệ đã duyệt. Log:
  `C:\KLTN_FALL2026\023-t03-verify.log`; JaCoCo ở `target/site/jacoco/`, không commit.
- Self-check PASS: đúng ADD 1 + MODIFY 18; mọi assertion cũ giữ nguyên; không mock
  SettingsApi; không giữ hạn mức trong field/constructor, thêm cache/fallback hay reset
  counter. Không đổi Lua/key/TTL/idempotency/hoàn lượt/trừ điểm, AiConfig validation,
  PREMIUM benefits, schema/migration, dependency, controller/YAML hoặc security.
  Ranh giới module giữ nguyên; không thêm JOIN xuyên schema trong production.
  Test dùng bcrypt cost 12 và dữ liệu tự soạn, không secret/PII thật/Neon/.env;
  không thay đổi luồng quyền, CSRF hoặc ledger. Không commit log/artifact/file nháp;
  git diff --check, allowlist/base/branch và UTF-8 không BOM đều PASS.

## 2026-11-15 · G0 chuyển consumer thư viện, quiz và chấm bài đã duyệt

- Leader duyệt 25 file (ADD 2, MODIFY 23); nhánh `spec/023-t04` từ đúng
  `d34ca971d905f05ee566a16ed4adb617d66e28db` (đầu #213), không dùng main `09b89ef`.
  Draft PR ghi phụ thuộc #213; leader giữ commit cha khi merge, member không rebase.
  Đạt (022) đã được leader báo trước về việc đổi nguồn đọc grading.
- MODIFY production 014: `LibraryRateLimiter`, `NotebookService`, `DeckService`, `CardService`;
  019: `QuizCatalog`, `QuizSessionService`, `QuizHandler`;
  022: `GradingRequestService`, `TeacherGradingService`, `EssayRules`.
  Tất cả thuộc `src/main/java/com/cnhsk/learning/{library,quiz,grading}/`, đuôi `.java`.
- MODIFY test 014: `LibraryRateLimiterTest`, `LibraryRateLimiterIntegrationTest`;
  019: `QuizCatalogIntegrationTest`, `QuizHandlerTest`, `QuizSessionIntegrationTest`;
  022: `EssayRulesTest`, `GradingRequestServiceTest`, `TeacherGradingServiceTest`,
  `GradingRepositoryIntegrationTest`, `GradingExpiryJobIntegrationTest`,
  `web/TeacherGradingControllerIntegrationTest`. Đều thuộc `src/test/java/com/cnhsk/learning/`.
  ADD `src/test/java/com/cnhsk/shared/settings/SettingsLibraryQuizConsumerIntegrationTest.java`
  và `SettingsGradingConsumerIntegrationTest.java` cùng thư mục. MODIFY hai tài liệu
  `specs/023-content-admin-settings/tasks.md`, `questions.md`; không sửa file ngoài allowlist.
- Q1 đã duyệt: dùng d34ca97 để giữ lịch sử tài liệu T-03 và mẫu fixture consumer;
  không sửa SettingsConsumerIntegrationTest của T-03. Hai lớp mới dùng chung support
  package-private trong file library/quiz, theo cùng cách dùng API ADMIN/bean thật,
  snapshot/restore setting, fixture tự soạn và dọn counter riêng từng người dùng.
- Q2 đã duyệt: EssayRules nhận record LengthLimits(minHanzi,maxHanzi,maxChars) bất biến,
  service dựng tại thời điểm dùng. EssayRulesTest chỉ đổi đối số fixture, giữ kỳ vọng
  và thông báo lỗi null. Không đưa SettingsApi vào validator thuần.
- Chuyển đủ 6 khoá library, 2 khoá quiz, 6 khoá grading. 021 đã dùng SettingsApi tại
  ContestAttemptService nên chỉ hồi quy, không đổi code. Consumer chỉ giữ reference API;
  không cache/fallback riêng hoặc giữ giá trị lúc constructor. Hạn mức trong details
  và kiểm tra dùng cùng giá trị local; queue đọc payout một lần cho mỗi projection.
- Giữ Lua/key/TTL/fail-open Redis, khoá/transaction, hoàn lượt/ledger. Hạ hạn mức không
  xoá nội dung hay counter. Resume quiz giữ snapshot; min-seconds đọc khi nộp cho cả
  QUIZ/QUIZ_RETRY, 0 tắt kiểm tra nhanh; session-minutes vẫn dùng QuizConfig.
- Grading policy/giới hạn bài và deadline của thao tác mới dùng setting tại thời điểm dùng.
  Không ghi lại deadline đã lưu; payout hoàn tất dùng giá trị lưu trong request.
  Giữ max-chars, cron và validation LibraryConfig/QuizConfig/GradingConfig.
- Test cũ chỉ đổi wiring/adapter fixture và stub payout sang SettingsService cụ thể;
  mọi kỳ vọng cũ giữ nguyên. Không @MockitoBean SettingsApi. Hai integration test mới
  không mock/spy SettingsApi/SettingsService; đổi/reset qua HTTP API ADMIN thật.
- Gate Java 17/Testcontainers: Remove-Item Env:DEBUG trước mỗi Maven command;
  targeted 13 lớp → hồi quy 014/019/022, contest 021, settings/contract/security và
  ModuleBoundaryTest → full verify, JaCoCo LINE >= 80%. Chỉ 5 ngoại lệ môi trường đã duyệt;
  lỗi khác ngoài phạm vi dừng báo leader. Không FE/Neon/.env/schema/YAML/dependency.

### Evidence gate và lịch sử sửa trong phạm vi T-04

- Targeted đầu: 216 F2/E0/S0; 206 test cũ xanh, hai lỗi đều trong test mới.
  Quiz so expiry nanosecond từ response tạo với timestamp PostgreSQL đọc lại làm tròn
  microsecond. Sửa test lấy snapshot đã lưu trước đổi setting rồi so nguyên response
  resume sau đổi/reset, vẫn kiểm chính xác ID/items/thứ tự/expiry đã lưu.
  Test payout gọi GET teacher sau COMPLETED kỳ vọng 200, nhưng API hiện có chỉ cho
  teacher đọc ASSIGNED. Giữ 403 hiện có; kiểm payout bất biến qua DB, ledger và trạng
  thái COMPLETED qua API learner. Không đổi production/quyền/assertion cũ.
  Log cục bộ: `C:\KLTN_FALL2026\023-t04-targeted.log` (không commit).
- Targeted sau sửa: 216 F0/E0/S0, BUILD SUCCESS; 206 test cũ và 10 test tích hợp mới.
  Log: `C:\KLTN_FALL2026\023-t04-targeted-final.log` (không commit).
- Trên base d34ca97 chưa có ContentAdminContractTest của T-12 (đã lên main ở nhánh khác).
  Giữ base leader cấp, không chép/cherry-pick test hoặc rebase; hồi quy các contract
  LibraryContractTest, QuizContractTest, GradingContractTest hiện có trên base.
- Hồi quy 69 lớp: 1058 F0/E0/S0, BUILD SUCCESS; ModuleBoundaryTest 9/9.
  Gồm toàn bộ library/quiz/grading/contest/settings, ba contract và quyền quiz.
  Log: `C:\KLTN_FALL2026\023-t04-regression.log` (không commit).
- Full verify một lượt: 6572 F0/E0/S0, BUILD SUCCESS trong 29 phút 31 giây;
  ModuleBoundaryTest 9/9, JaCoCo LINE 97,143209% (15778/16242), coverage gate PASS.
  Không dùng ngoại lệ môi trường nào trong năm ngoại lệ đã duyệt. Log:
  `C:\KLTN_FALL2026\023-t04-verify.log`; coverage ở `target/site/jacoco/`, không commit.
- Self-check PASS: đúng ADD 2 + MODIFY 23, base/branch đúng, mọi kỳ vọng test cũ
  giữ nguyên (EssayRules chỉ đổi adapter đối số theo Q2). Không mock SettingsApi,
  cache/fallback riêng hay giữ hạn mức số trong constructor/field. Không đổi validation
  ba Config, session-minutes/max-chars/cron, Lua/key/TTL, snapshot quiz, deadline/payout
  đã lưu, idempotency, hoàn lượt, chấm điểm, ledger, schema/migration, dependency,
  controller/YAML/security/FE hoặc code contest 021. Module boundary giữ nguyên.
  Fixture tự soạn, bcrypt cost 12; không Neon/.env/secret/PII thật/đề HSK có bản quyền.
  git diff --check và UTF-8 không BOM PASS; log/target/file nháp shadow-plans ngoài commit.

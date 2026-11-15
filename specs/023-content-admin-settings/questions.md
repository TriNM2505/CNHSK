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

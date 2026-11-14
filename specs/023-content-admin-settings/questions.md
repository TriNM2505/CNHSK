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

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

# Chốt triển khai — Spec 004

## 2026-10-30 — T-01 Shadow Plan

- Leader duyệt ngoại lệ một lần: `git fetch origin` rồi `git merge --ff-only origin/main`; sau đó không tự merge, rebase hoặc pull main.
- `GRAMMAR_LESSON` đã có trong `learning.practice.PracticeSource`, không sửa file đó.
- Thêm `LESSON_READ(false)` và `GRAMMAR_CHOICE(true)` cuối `learning.practice.ItemType`; thêm cùng thứ tự vào `learning.api.PracticeApi.ItemType`, không thêm phương thức API.
- `SecurityConfig` chỉ thêm quyền `LEARNER` cho `GET /api/grammar` và `GET /api/grammar/{id}`; endpoint POST lessons thuộc T-02.
- Thêm `GRAMMAR_POINT_NOT_FOUND` ngay trước `INTERNAL_ERROR`, đồng bộ danh sách thứ tự trong `GlobalExceptionHandlerTest`.
- T-01 không dùng điều kiện JSONB, không tạo migration. Tiến độ chỉ tính lượt `SUBMITTED` của chính user từ `AuthApi.currentUser()` bằng một truy vấn tổng hợp cho danh sách.
- Tạo test backend trong `src/test/.../learning/grammar/**`; test frontend để T-04 và T-05.

## 2026-10-31 — T-01 FR007 test alignment

- Leader duyệt sửa duy nhất assertion của `FR007_studyCardsCannotBeGradedWhileQuestionTypesCan`: `STUDY_CARD` và `LESSON_READ` là các loại không chấm điểm; các `ItemType` còn lại chấm điểm. Giữ nguyên tên test và không sửa `ItemType`.

## 2026-10-31 — T-04 SCR-017 acceptance

- FE tải toàn bộ danh sách điểm ngữ pháp một lần rồi chia bảy tab ở client; không gọi riêng `hskLevel=7,8,9`. Cách này cần thiết để mỗi tab hiện số điểm và tab mặc định chọn theo dữ liệu trả về.
- T-04 chỉ bổ sung test acceptance cho SCR-017; không sửa production code khi implementation hiện tại đã khớp FR-004.

## 2026-11-01 — T-02 GrammarLessonService

- Leader chốt ngày 2026-10-31 và cho làm sau khi PR #38 (spec 006 T-03) đưa `PracticeService.recordAndFinish` lên `main`; không tự gọi `start` rồi `finish`, không sửa `PracticeService`.
- `recordAndFinish` chỉ dùng cho điểm không có bài tập (mọi câu `gradable=false`); điểm có bài tập đi qua `PracticeService.start`.
- FR-014: advisory lock theo giao dịch cho cặp user + điểm ngữ pháp trước khi tìm hoặc tạo lượt, để hai request đồng thời chỉ tạo một lượt.
- Gọi `rateLimiter.checkStart(userId)` trước `recordAndFinish`; `GRAMMAR_LESSON` vẫn tính quota 120 lượt/giờ (chỉ `CHAR_WRITING` được miễn).
- `readDurationMs` ngoài 0…3 600 000 → 422; id sai định dạng → 422; id không phải `GRAMMAR` → 404 `GRAMMAR_POINT_NOT_FOUND`.
- Test bắt buộc: response không chứa đáp án; AT-09 kiểm `learned=true` qua `GET /api/grammar`.

## 2026-11-01 — T-02 readTooFast (duyệt tạm)

- Leader duyệt tạm: T-02 giữ `readTooFast` ở `meta` của lượt (response và snapshot) vì chưa có `GrammarLessonHandler`.
- **AT-06 mới đạt ở response.** Phần bằng chứng trên `LESSON_READ.meta` (spec §10) chuyển sang T-03.
- T-03 bắt buộc, chưa đủ thì leader không nghiệm thu T-03:
  - `GrammarLessonHandler` ghi `readTooFast=true` vào `meta` của đúng câu `LESSON_READ` trong `attempt_items`; cờ do server tính, không lấy từ client.
  - Bỏ `readTooFast` khỏi `meta` của lượt (response và snapshot) để chỉ còn một nguồn.
  - Sửa test T-02 đang assert `meta` của lượt sang truy vấn DB thật: giải thích 500 ký tự đọc 2 giây thì có cờ, 100 ký tự thì không; kiểm cả nhánh không có bài tập (FR-013) và có bài tập. Việc sửa test T-02 được phép trong T-03 và phải nêu trong Shadow Plan T-03.

## 2026-11-01 — T-03 GrammarLessonHandler

- Leader duyệt Shadow Plan T-03, đúng 6 file: `GrammarLessonHandler` (mới), `GrammarLessonService`, `GrammarLessonHandlerIntegrationTest` (mới), `GrammarLessonIntegrationTest`, `tasks.md`, `questions.md`. Không sửa `PracticeService`, `PracticeSourceHandler`, `SecurityConfig`, OpenAPI, migration, frontend.
- `readTooFast` chỉ còn một nguồn: `GrammarLessonHandler.enrichMeta` tự tính ở server cho câu `LESSON_READ` (đọc dưới 5 giây và `extra.explanation` ≥ 300 ký tự) rồi engine ghi vào `attempt_items.meta`. Handler không bao giờ lưu `clientMeta` của client. `meta` của lượt (response và snapshot) không còn cờ. Comment trong code không ghi mã task.
- `answered` chỉ tính câu `GRAMMAR_CHOICE` được engine tính điểm (counted); `percent` = `floor(correct*100/answered)`, `null` khi `answered = 0`, khớp `FinishResult.percent` và `bestPercent`.
- `exercisesTotal` đếm câu `GRAMMAR_CHOICE` trong snapshot của lượt, không đọc lexeme hiện tại.
- `firstTimeLearned` = không có lượt `GRAMMAR_LESSON` `SUBMITTED` nào khác của cùng user + điểm, loại trừ lượt hiện tại (cách của `TopicTestHandler`). Chấp nhận rủi ro không khóa khi finish.
- Kết quả thực tế: engine **chấp nhận** `skipped=true` cho `GRAMMAR_CHOICE` (`GRAMMAR_LESSON.allowSkip = true`). Câu đó vẫn được ghi (`meta.skipped = true`, `is_correct NULL`) nhưng không tính vào `answered`, nên `skippedExercises = true`.
- `POST answer` cho câu 1 (`LESSON_READ`) của lượt có bài tập trả 409 `ANSWER_ALREADY_SUBMITTED`; `meta` và `duration_ms` của câu đó không đổi.

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

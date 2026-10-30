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

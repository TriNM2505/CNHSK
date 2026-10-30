# Questions and decisions — Spec 006

## Quyết định leader ngày 2026-10-31

| # | Quyết định | Trạng thái |
|---|---|---|
| Q-T07-01 | T-07 hoàn thiện trực tiếp trên `cnhsk-web/src/features/hanzi/pages/Scr015CharacterRecognitionPage.tsx`; không tạo `RecognitionPage.tsx`. | **Chốt — leader 2026-10-31** |
| Q-T08/09-01 | T-08/T-09 đối chiếu và hoàn thiện code hiện có; chỉ giữ phần có test chứng minh đạt FR/AT. Canvas, 3 chế độ viết và thử thách phải triển khai thật; file `.preview` chỉ phục vụ xem trước. | **Chốt — leader 2026-10-31** |
| Q-SCOPE-01 | File dùng chung hoặc ngoài cột File phải liệt kê và xin duyệt trước khi sửa. Riêng `ChoiceItemFactory` có 3 thay đổi đã được leader duyệt cho T-02. | **Chốt — leader 2026-10-31** |
| Q-AC09-01 | Ngoại lệ AC-09 được leader chấp nhận, chỉ cho `meta->>'mode'` của `WRITING_RESULT`. Chỉ áp dụng cho `recallUnlocked` (T-01), `repeatWithin10Min` (T-03), và tập chữ đủ điều kiện thử thách (T-04); luôn lọc trước bằng cột thật. Hoàn thành xác định qua `attempt_items.answer = 'COMPLETED'`; truy vấn đặt trong repository `learning.hanzi`. Không thêm index meta, thống kê/xếp hạng/báo cáo theo meta. | **Chốt — leader 2026-10-31** |
| Q-T01-REPO | Truy vấn chữ và bằng chứng viết đặt trong `learning.hanzi.CharacterQueryRepository`; hoàn nguyên `content.repository.LexemeRepository` về main, không sửa thêm file module `content`. Repository Hanzi giữ phần kiểm tra `recallUnlocked` để các task viết/thử thách dùng lại. | **Chốt — leader 2026-10-31** |
| Q-T01-OPENAPI | Hai endpoint `GET /api/characters` và `GET /api/characters/{id}` công bố response `403` dùng schema `ErrorResponse`, khớp quyền LEARNER trong SecurityConfig. Test phân quyền phải gọi endpoint qua integration/security test. | **Chốt — leader 2026-10-31** |
| Q-T01-TEST | Chỉ sửa assertion trong `PracticeCoreTest.FR007_studyCardsCannotBeGradedWhileQuestionTypesCan` để `STUDY_CARD` và `WRITING_RESULT` là hai loại không chấm điểm; giữ nguyên tên test và không sửa test khác. | **Chốt — leader 2026-10-31** |

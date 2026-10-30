# Tasks: Ngữ pháp HSK — Spec #004

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-grammar.yaml`](contracts/openapi-grammar.yaml)

**Tổng: 5 task**, ≤ 4 giờ/task, một task một PR `agent/004-<task>`. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
**Điều kiện:** spec 003 đợt 0 (T-01…T-08) và T-13 (component FE chung) xong.

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | `GRAMMAR_LESSON` đã có trong `PracticeSource`; thêm `LESSON_READ`/`GRAMMAR_CHOICE` vào `ItemType`; `GrammarQueryService` + `GET /api/grammar`, `GET /api/grammar/{id}` (truy vấn tổng hợp `learned`/`bestPercent`) | `…/learning/grammar/*`, `…/learning/practice/ItemType.java`, `…/learning/api/PracticeApi.java` | FR-001…FR-005 · AT-01…AT-04 | ✅ |
| T-02 | `GrammarLessonService` + `POST /lessons`: câu `LESSON_READ` ghi sẵn, `readTooFast`, chọn/trộn bài tập, không có bài tập → kết thúc ngay, tiếp tục lượt | `…/learning/grammar/GrammarLessonService.java` | FR-009…FR-014 · AT-05…AT-10 | ⬜ |
| T-03 | `GrammarLessonHandler` (`extra`) | `…/learning/grammar/GrammarLessonHandler.java` | FR-015, FR-016 · AT-11, AT-12 | ⬜ |
| T-04 | SCR-017 Grammar List (7 tab) | `cnhsk-web/src/features/grammar/pages/GrammarListPage.tsx` | FR-004 · AT-13 | ⬜ |
| T-05 | SCR-018 Grammar Lesson: nội dung, đo thời gian đọc, bài tập (component chung), bỏ qua có xác nhận, kết thúc | `cnhsk-web/src/features/grammar/**` | FR-006…FR-008, FR-017, FR-018 · AT-13 | ⬜ |

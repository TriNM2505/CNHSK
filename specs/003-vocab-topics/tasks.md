# Tasks: Từ vựng theo chủ đề + bộ máy lượt luyện — Spec #003

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-practice-vocab.yaml`](contracts/openapi-practice-vocab.yaml)

**Tổng: 17 task**, mỗi task ≤ 4 giờ, một task = một PR `agent/003-<task>`. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
`…/` = `src/main/java/com/cnhsk/`.

**Điều kiện bắt đầu:** spec 001 xong; spec 002 xong T-01…T-03 (entity nội dung, `PinyinUtils`). Q5 ở §9 đã duyệt.

---

## Đợt 0 · Bộ máy lượt luyện (§0) — làm trước mọi màn

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | `V4__practice_engine.sql` (§0.12); entity `Attempt`, `AttemptItem` (+ `served_items`, `draft_answers`, `meta` JSON); repository | `db/migration/V4__practice_engine.sql`, `…/learning/practice/entity/*` | §0.12 | ✅ 2026-10-30: `verify` xanh (451 test, không skip); line coverage toàn bộ 93,32%, practice 100%. V4 chỉ kiểm thử bằng Testcontainers, chưa chạy Neon |
| T-02 | Kiểu lõi: `PracticeSource` (đủ 9 giá trị §0.2, chỉ 3 nguồn 003 có handler), `FeedbackMode`, `ItemType`, `Skill`, `ServedItem`, `ServedItemView` (mapper bỏ `answer`/`accept`), `PracticeSourceHandler` | `…/learning/practice/*` | §0.2–§0.5 · AT-01 | ✅ 2026-10-30: 27 test mới; `verify` xanh (478 test, không skip); line coverage toàn bộ 93,55%, practice 100%. Q-T02-01 đã chốt tại `questions.md` |
| T-03 | `PracticeService.start/view/findActive` + `PracticeController` GET `/sessions/{id}`, `/active`; E-R1…E-R3; giới hạn tạo lượt E-R12 | `PracticeService.java`, `web/PracticeController.java`, `PracticeRateLimiter.java` | FR-001, FR-002, FR-010 · AT-02, AT-03, AT-11 | ✅ 2026-10-30: Q-T03-01…05 đã chốt và triển khai; Java 17 mvnw verify xanh (531 test, 0 failure/error/skip; chạy ngoài sandbox). JaCoCo line coverage toàn bộ 93,96% (2503/2664), practice 100% (332/332). OpenAPI đã đồng bộ; git diff --check sạch |
| T-04 | `PracticeService.answer` cho 3 kiểu phản hồi; E-R4…E-R12; `PinyinGrader` (§0.7) | `PracticeService.java`, `grading/*` | FR-004…FR-007 · AT-04…AT-12, AT-18 | ⬜ |
| T-05 | `PracticeService.finish` (E-F1…E-F7) + sự kiện §0.9 + `study_events` | `PracticeService.java`, `event/*` | FR-008, FR-009 · AT-13…AT-15, AT-17 | ⬜ |
| T-06 | Hết hạn lazy + job E-F9; giữ bằng chứng khi hết hạn; `abandon` | `PracticeMaintenance.java`, `PracticeService.java` | E-F8, E-F9, §0.11 · AT-16, AT-16b, AT-16c | ⬜ |
| T-07 | `ChoiceItemFactory` (G-1…G-6) + test thống kê 100 lượt | `generate/ChoiceItemFactory.java` | FR-011 · AT-19…AT-21 | ⬜ |
| T-08 | `learning.api.PracticeApi` + DTO; cập nhật `ModuleBoundaryTest` (community chỉ phụ thuộc `learning.api`) | `…/learning/api/PracticeApi.java` | FR-012 | ⬜ |

> **Đợt 0 là nền của 6 spec.** Coverage đợt này ≥ 90%. Test AT-01 phải quét JSON **mọi** response bắt đầu lượt.

## Đợt 1 · Chủ đề (BE)

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-09 | `TopicQueryService`: truy vấn tổng hợp §4, `status`, `bestTestPercent`; `GET /api/topics`, `GET /api/topics/{id}` (gồm `actions`) | `…/learning/vocab/*` | FR-013…FR-018 · AT-22…AT-24 | ⬜ |
| T-10 | `TopicStudyService` + `POST /study-sessions` (10 từ chưa học, tiếp tục lượt đang làm) | `…/learning/vocab/TopicStudyService.java` | FR-019…FR-022 · AT-25…AT-28 | ⬜ |
| T-11 | `TopicPracticeService` + `POST /practice-sessions` (2 chế độ, ưu tiên từ yếu, luyện lại từ sai) | `…/learning/vocab/TopicPracticeService.java` | FR-025…FR-030 · AT-29…AT-32 | ⬜ |
| T-12 | `TopicTestService` + `TopicTestHandler` + `POST /tests` (điều kiện, chờ 10 phút, 20 câu 4 dạng, thay thế) | `…/learning/vocab/TopicTestService.java` | FR-031…FR-040 · AT-33…AT-39 | ⬜ |

## Đợt 2 · Frontend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-13 | Khung FE luyện tập dùng chung: `features/practice/` — hook `usePracticeSession` (bắt đầu/khôi phục/trả lời/kết thúc), component `ChoiceQuestion`, `PinyinInput`, `AudioButton` (đếm nghe lại, "Không nghe được"), `SessionSummary` | `cnhsk-web/src/features/practice/**` | FR-043, FR-045 | ⬜ |
| T-14 | SCR-011 Topic List | `cnhsk-web/src/features/vocab/pages/TopicListPage.tsx` (SCR-011) | FR-041 | ⬜ |
| T-15 | SCR-012 khung + bước "Học từ mới" (thẻ từ) | `cnhsk-web/src/features/vocab/**` | FR-042, FR-023, FR-024 | ⬜ |
| T-16 | SCR-012 bước luyện nhận diện / nghe (dùng T-13) | `cnhsk-web/src/features/vocab/**` | FR-043 | ⬜ |
| T-17 | SCR-012 bước kiểm tra (`DEFERRED`: đi lại giữa câu, nháp, xác nhận nộp, kết quả, luyện lại từ sai) | `cnhsk-web/src/features/vocab/**` | FR-044 · AT-40 | ⬜ |

---

**Thứ tự:** T-01 → … → T-08 → T-09 → T-10 → T-11 → T-12 → T-13 → T-14 → T-15 → T-16 → T-17.
**Rủi ro cao:** T-04, T-05 (đua, transaction, sự kiện), T-07 (đáp án kép).

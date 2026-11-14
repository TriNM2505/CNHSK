# Tasks: Quiz theo chủ đề — Spec #019

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-quiz.yaml`](contracts/openapi-quiz.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 8 task**, ≤ 4 giờ/task, một task một nhánh `spec/019-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 019 `APPROVED`. **Không migration.** Điểm khoá: ⚿K1 (enum chung với 021). 017 không chặn.

## Đợt 1 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | **⚿K1** `PracticeSource.QUIZ`, `QUIZ_RETRY` (§0.2); đồng bộ enum API và OpenAPI 003, giữ thứ tự cũ. `CONTEST` do 021 thêm riêng theo G0 leader duyệt 2026-11-14 | `…/learning/practice/PracticeSource.java`, `…/learning/api/PracticeApi.java`, contract 003 + test | §0.2 | MERGED (#157) |
| T-02 | Tách khỏi học chính: `EvidenceAllocator.contextOf` trả `null` cho `QUIZ`/`QUIZ_RETRY`; `existsActiveTest` thêm `'QUIZ'`; test mastery không đổi, chặn trợ lý lượt đầu và hồi quy `PLACEMENT`/`TOPIC_TEST`. Leader đã phối hợp 011/012/015 | `…/learning/mastery/EvidenceAllocator.java`, `…/learning/practice/entity/AttemptRepository.java` + test; T-01 đã merge #157 | FR-016, FR-017 · AT-11, AT-12 | MERGED (#160) |
| T-03 | `QuizCatalog`: một truy vấn chủ đề đủ câu PRIMARY đúng KID, lịch sử cá nhân và total; `GET /api/quizzes`; `QuizConfig`/`cnhsk.quiz.*`; matcher LEARNER; test override ngưỡng, query count cứng và đo p95 không gate thời gian. Thông báo FR-005 thuộc T-06 | `…/learning/quiz/{QuizConfig,QuizCatalog,QuizRepository}.java`, `…/quiz/web/QuizController.java`, `application.yaml`, `SecurityConfig.java` + test | FR-003…FR-005, FR-018 · AT-01, AT-02 phần catalog | MERGED (#165) |
| T-04 | `QuizQuestionPicker` cân `qtype`, APPROVED/PRIMARY đúng KID, MULTIPLE_CHOICE/TRUE_FALSE không media; đồng bộ catalog; engine QUIZ/QUIZ_RETRY, resume, chuẩn hoá điểm, thời gian server, khoá nộp đua; POST attempts, QUIZ_NOT_AVAILABLE; quota Redis chung 30 lượt/giờ, resume miễn lượt. G0 leader duyệt 18 file, base 02bd90d; G2: 4937/0/0/0, LINE 96,9087% | `…/learning/quiz/{QuizQuestionPicker,QuizHandler,QuizSessionService,QuizRepository}.java`, controller, engine, ErrorCode + test và questions | FR-006…FR-011 · AT-03…AT-10 | MERGED (#168) |
| T-05 | `QuizResultService` + GET result từ snapshot/evidence; `firstAttempt` qua `ObjectProvider<LeaderboardService>` tuỳ chọn, lookup ngoài transaction đọc, lỗi hạng vẫn 200/null; contract interface phối hợp 017, ownership trước nguồn/trạng thái. G0 duyệt 12 file, base 13f1d96; G2 4990/0/0/0, LINE 96,9349%; leader duyệt G3, một commit và PR thường | service, repository/controller, interface 017, ErrorCode + test/kiểm kê và questions | FR-012, FR-013 · AT-13…AT-16 | ✅ MERGED — PR #171 |

## Đợt 2 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | SCR-043 và placeholder SCR-044, menu Cộng đồng/Quiz; 23 file, base ad48029 cập nhật fast-forward từ c7edc28. Kiểm kê 009 pass. Leader chấp nhận full Vitest 1 fail Scr059 khi chạy riêng pass 64/64; lint 0 error, build pass, BE 21/21. QA Playwright phiên Chromium sạch đạt 320/375/640/1280 px, menu/active/focus và 5 ảnh ngoài repo; leader duyệt G3 | feature quiz, route/registry/preview, layout/navigation + test, kiểm kê 009, AssistantPromptBuilder + test, questions | FR-005, FR-019, FR-020, FR-023 | ✅ MERGED — PR #177 |
| T-07 | SCR-044 thật: resume, engine answers/finish/abandon, xác nhận trong trang, ẩn trợ lý qua pageContext; SCR-045: kết quả server, hạng lần đầu và Top 10 độc lập. 28 đường dẫn, đã rebase main 3fe9ab0 sau #177; G2 full Vitest 2676/2676, lint 0 error, build đạt, BE 21 F0/E0/S0, QA Playwright 4 viewport và 10 ảnh ngoài repo | feature quiz + types practice, routeAccess, pageContext/test 015, AssistantPromptBuilder/test, questions/tasks | FR-009, FR-012…FR-015, FR-017, FR-019, FR-021…FR-023 · AT-17 | ✅ MERGED — PR #179 |
| T-08 | QuizContractTest so hai chiều path/method/operationId/status, đúng 3 path/3 operation, handler package quiz.web; không so security, giữ YAML. Base 00efcf8, 3 file; G2 targeted 1, regression 166, boundary 9, full verify 5360 F0/E0/S0; LINE 96,9187% | `t/contract/QuizContractTest.java`, questions.md, tasks.md | AT-18 | CHỜ REVIEW — Draft PR |

## Thứ tự

```
T-01(⚿K1) ─► T-02
          └─► T-04 ─► T-05 ─► T-08
T-03 (song song, không chờ)
T-06 ─► T-07 (song song từ đầu bằng dữ liệu mẫu; nối thật sau T-04/T-05)
```

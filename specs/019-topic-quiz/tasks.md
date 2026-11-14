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
| T-05 | `QuizResultService` + GET result từ snapshot/evidence; `firstAttempt` qua `ObjectProvider<LeaderboardService>` tuỳ chọn, lookup ngoài transaction đọc, lỗi hạng vẫn 200/null; contract interface phối hợp 017, ownership trước nguồn/trạng thái. G0 duyệt 12 file, base 13f1d96; G2 4990/0/0/0, LINE 96,9349%; leader duyệt G3, một commit và PR thường | service, repository/controller, interface 017, ErrorCode + test/kiểm kê và questions | FR-012, FR-013 · AT-13…AT-16 | CHỜ REVIEW |

## Đợt 2 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | SCR-043 và placeholder SCR-044, menu Cộng đồng/Quiz; 23 file, base ad48029 cập nhật fast-forward từ c7edc28. Kiểm kê 009 pass. Leader chấp nhận full Vitest 1 fail Scr059 khi chạy riêng pass 64/64; lint 0 error, build pass, BE 21/21. QA Playwright phiên Chromium sạch đạt 320/375/640/1280 px, menu/active/focus và 5 ảnh ngoài repo; leader duyệt G3 | feature quiz, route/registry/preview, layout/navigation + test, kiểm kê 009, AssistantPromptBuilder + test, questions | FR-005, FR-019, FR-020, FR-023 | CHỜ REVIEW |
| T-07 | SCR-044 (nhãn tính hạng, điều hướng câu, nộp có xác nhận trong trang, thoát) dùng engine answers/finish; ẩn panel trợ lý (**báo người giữ 015**); SCR-045 (tóm tắt, đáp án, hạng lần đầu + top 10 từ API 017, nút điều hướng) | `web/features/quiz/components/**`, `web/features/assistant/components/AssistantHost.tsx` + test | FR-015, FR-021, FR-022 · AT-17 | ⬜ |
| T-08 | Test hợp đồng `/v3/api-docs` so `openapi-quiz.yaml` | `t/contract/QuizContractTest.java` | AT-18 | ⬜ |

## Thứ tự

```
T-01(⚿K1) ─► T-02
          └─► T-04 ─► T-05 ─► T-08
T-03 (song song, không chờ)
T-06 ─► T-07 (song song từ đầu bằng dữ liệu mẫu; nối thật sau T-04/T-05)
```

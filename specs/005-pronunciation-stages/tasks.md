# Tasks: Luyện phát âm 8 tầng — Spec #005

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-pronunciation.yaml`](contracts/openapi-pronunciation.yaml)

**Tổng: 4 task**, ≤ 4 giờ/task, một task một commit; nhánh `spec/005-pronunciation-stages`. Quy ước chung: `specs/001-auth-rbac/tasks.md`; áp dụng chỉ đạo mới của leader, không dùng tên nhánh chứa `agent`.
**Điều kiện:** spec 003 đợt 0 và spec 002 T-01, T-02 đã có trên main (V4). Leader cho phép bắt đầu, không chờ T-13 của spec 003.

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Dùng `PRON_STAGE` đã có (`allowSkip=false`, 7 ngày) trong cả hai `PracticeSource`; thêm `PRON_CHOICE` cuối hai `ItemType`; entity `UserProgress` (chỉ các cột dùng); `PronStageQueryService` + `GET /stages` (luật mở FR-004), gọi trực tiếp `PracticeService.findActive` trong module learning | `…/learning/pronunciation/*`, `…/learning/progress/UserProgress.java`; Q-T01-01/02 (leader duyệt 2026-10-30): `…/learning/practice/ItemType.java`, `…/learning/api/PracticeApi.java` (chỉ enum), `…/shared/config/SecurityConfig.java` (đúng 1 matcher GET); `src/test/java/com/cnhsk/learning/pronunciation/PronStageQueryServiceTest.java`, `src/test/java/com/cnhsk/learning/pronunciation/PronunciationIntegrationTest.java`, `tasks.md`, `questions.md` | FR-001…FR-005 · AT-01, AT-02 (phần GET), AT-16, AT-17 | ✅ 2026-10-30: 32 test mới (17 unit + 15 integration); gate chọn lọc cùng ModuleBoundaryTest/PracticeApiTest: 64 test xanh; Java 17 Maven Wrapper verify trên PowerShell thường có Docker: 1369 test, 0 failure/error/skip, đối chiếu Surefire XML. JaCoCo line toàn bộ 96,31% (4385/4553), practice 100% (743/743), pronunciation 97,22% (35/36). OpenAPI runtime GET khớp contract; không đổi migration/dependency/test nền; diff sạch, UTF-8 không BOM/TODO. Giữ local, không push theo chỉ đạo người dùng |
| T-02 | `PronStageSessionService` + `POST /stages/{n}/sessions` (kiểm khoá/không có đơn vị, tiếp tục, sinh câu) | `…/learning/pronunciation/PronStageSessionService.java` | FR-006…FR-009 · AT-03…AT-07 | ⬜ |
| T-03 | `PronStageHandler`: chưa đủ → 422, độ chính xác, upsert tiến độ, mở tầng kế `ON CONFLICT DO NOTHING`, `extra`, cờ nghi ngờ audio | `…/learning/pronunciation/PronStageHandler.java` | FR-010…FR-016, §11 · AT-08…AT-15, AT-18…AT-20 | ⬜ |
| T-04 | SCR-016: danh sách tầng + màn làm bài (component chung spec 003 T-13, không có "Bỏ qua") + kết quả | `cnhsk-web/src/features/pronunciation/**` | FR-017…FR-020 · AT-21 | ⬜ |

**Rủi ro cao:** T-03 (đua mở tầng — test AT-13 bắt buộc chạy với 2 luồng thật trên Testcontainers).

# Tasks: Luyện phát âm 8 tầng — Spec #005

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-pronunciation.yaml`](contracts/openapi-pronunciation.yaml)

**Tổng: 4 task**, ≤ 4 giờ/task, một task một PR `agent/005-<task>`. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
**Điều kiện:** spec 003 đợt 0 + T-13 xong; spec 002 T-01, T-02 xong.

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Thêm `PRON_STAGE` (`allowSkip=false`, 7 ngày) vào `PracticeSource`, `PRON_CHOICE` vào `ItemType`; entity `UserProgress` (chỉ các cột dùng); `PronStageQueryService` + `GET /stages` (luật mở FR-004) | `…/learning/pronunciation/*`, `…/learning/progress/UserProgress.java` | FR-001…FR-005 · AT-01, AT-02, AT-16, AT-17 | ⬜ |
| T-02 | `PronStageSessionService` + `POST /stages/{n}/sessions` (kiểm khoá/không có đơn vị, tiếp tục, sinh câu) | `…/learning/pronunciation/PronStageSessionService.java` | FR-006…FR-009 · AT-03…AT-07 | ⬜ |
| T-03 | `PronStageHandler`: chưa đủ → 422, độ chính xác, upsert tiến độ, mở tầng kế `ON CONFLICT DO NOTHING`, `extra`, cờ nghi ngờ audio | `…/learning/pronunciation/PronStageHandler.java` | FR-010…FR-016, §11 · AT-08…AT-15, AT-18…AT-20 | ⬜ |
| T-04 | SCR-016: danh sách tầng + màn làm bài (component chung spec 003 T-13, không có "Bỏ qua") + kết quả | `cnhsk-web/src/features/pronunciation/**` | FR-017…FR-020 · AT-21 | ⬜ |

**Rủi ro cao:** T-03 (đua mở tầng — test AT-13 bắt buộc chạy với 2 luồng thật trên Testcontainers).

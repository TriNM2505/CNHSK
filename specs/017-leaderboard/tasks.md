# Tasks: Bảng xếp hạng công khai — Spec #017

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-leaderboard.yaml`](contracts/openapi-leaderboard.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 7 task**, ≤ 4 giờ/task, một task một nhánh `spec/017-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 017 `APPROVED`. **Không chờ 019/007** — test bằng dữ liệu `attempts` mẫu.

**Ghi chú T-04 (Q-T01-04):** lọc tài khoản bị cấm trong SQL trước khi đánh hạng bằng `user_id <> ALL(:banned)` hoặc `NOT (user_id = ANY(:banned))` với mảng; không dùng `NOT IN (...)` cho danh sách có thể rỗng.

## Đợt 1 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | `AuthApi.bannedUserIds()`, `AuthApi.publicProfiles(ids)`, record `PublicProfile` + cài đặt + test (**module 001 — báo leader trước**) | `…/auth/api/{AuthApi,PublicProfile}.java`, `…/auth/service/AuthApiService.java`, `t/auth/AuthApiServiceTest.java` | §0.3, FR-003, FR-010 | MERGED (#153) |
| T-02 | `learning.api.GameCode` (6 mã + tên tiếng Việt) — thuộc 017/T-02, 007 dùng lại theo Q-T02-02; nếu 007 đã tạo thì bỏ qua task này, ghi PR tham chiếu | `…/learning/api/GameCode.java` + `t/learning/api/GameCodeTest.java` | FR-015, §0.3 · Q-T02-01…03 | MERGED (#163) |
| T-03 | Migration index `ix_att_ref_rank` (**V8 được leader cấp**, Q-T03-02; V9 dành 023), test Flyway/catalog/upgrade; EXPLAIN để T-04 | `src/main/resources/db/migration/V8__leaderboard_index.sql`, `t/learning/leaderboard/LeaderboardMigrationIntegrationTest.java` | §0.1 · AT-16 phần migration | MERGED (#170) |
| T-04 | `LeaderboardPeriods` (kỳ tuần/tháng/all giờ VN, `Clock`) + `LeaderboardRepository` (SQL chủ đề + game, top 20, hạng của tôi, loại banned) + test Testcontainers dữ liệu mẫu; một statement top/myEntry/total, tie-break `user_id` theo FR-009; quyết định Q-T04-01…03 | `…/learning/leaderboard/LeaderboardPeriods.java`, `…/learning/leaderboard/LeaderboardRepository.java`, `t/learning/leaderboard/LeaderboardPeriodsTest.java`, `t/learning/leaderboard/LeaderboardRepositoryIntegrationTest.java` | FR-004…FR-012 · AT-02…AT-10, AT-16 | 🔄 Đang triển khai/G2, base `13f1d96`; chưa duyệt hoàn thành |
| T-05 | `LeaderboardServiceImpl` cài đặt interface 019 giữ nguyên một method; cache top/total/banned TTL 60 s, myEntry đọc DB, profile batch lookup; `rankOf` kỳ all; 4 GET, `INVALID_GAME_CODE`, permitAll/optional auth và guest rate limit; Q-T05-01…08 | `…/learning/leaderboard/LeaderboardServiceImpl.java`, `…/learning/leaderboard/web/LeaderboardController.java`, catalog/DTO/cache/client/limiter và test trong allowlist 20 file G0 + `t/shared/exception/GlobalExceptionHandlerTest.java` được duyệt bổ sung; `ErrorCode.java`, `SecurityConfig.java`, `JwtAuthenticationFilter.java`; không sửa interface | FR-001…FR-003, FR-013…FR-017 · AT-01, AT-11…AT-15 | 🔄 G2 sau rebase `f6e471e`, nhánh `spec/017-t05`; chờ gate mới, chưa hoàn thành |

## Đợt 2 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | Nền FE `leaderboard`: types, api, route công khai `/leaderboards/topics`, `/leaderboards/games`, menu; SCR-046 + SCR-047 (bộ chọn, tab kỳ, bảng top 5 + "Xem thêm" tới 20, hạng của tôi, 4 trạng thái, responsive thẻ ≤ 640 px) | `web/features/leaderboard/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts`, `web/shared/layout/navigation.ts` + test | FR-018…FR-022 · AT-17 | ⬜ |
| T-07 | Test hợp đồng `/v3/api-docs` so `openapi-leaderboard.yaml` (path/method/status hai chiều, khuôn `LibraryContractTest`) | `t/contract/LeaderboardContractTest.java` | AT-18 | ⬜ |

## Thứ tự

```
T-01 ─┐
T-02 ─┼─► T-04 ─► T-05 ─► T-07
T-03 ─┘
T-06 (song song từ đầu bằng dữ liệu mẫu; nối thật sau T-05)
```

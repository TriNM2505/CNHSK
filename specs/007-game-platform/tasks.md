# Tasks: Khung game và `cnhsk-game` — Spec #007

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-games.yaml`](contracts/openapi-games.yaml)

**Tổng: 11 task**, ≤ 4 giờ/task, một task một PR `agent/007-<task>`. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
**Điều kiện:** spec 001 xong (cookie, CORS, FR-090); spec 003 đợt 0 xong.

## Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Thêm `GAME_ROUND` vào `PracticeSource` (`allowSkip=false`, `maxScoreFromServed=true`); `PracticeApi.gameStats(userId)`, `countRecentGameRounds(userId, since)` | `…/learning/practice/*`, `…/learning/api/PracticeApi.java` | FR-004, FR-007 | ⬜ |
| T-02 | `learning.api.ContentLookup` + cài đặt `ContentLookupImpl` (`pool`, `LexemeCard`, `pinyinKey`, `meaningKeys`, ưu tiên từ đã học, mặc định cấp 1) | `…/learning/api/ContentLookup.java`, `…/learning/content/ContentLookupImpl.java` | FR-020…FR-022 · AT-17 | ⬜ |
| T-03 | `GameCode`, `GameDefinition`, `GameRegistry` (kiểm đủ 4 khi khởi động), 4 định nghĩa **giả** cho test | `…/community/game/*` | FR-001 · AT-01 | ⬜ |
| T-04 | `GET /api/community/games` (công khai, cache phần chung, `myStats`) | `…/community/game/web/GameController.java`, `GameCatalogService.java` | FR-002…FR-005 · AT-02, AT-03 | ⬜ |
| T-05 | `GameRoundService.start` + `POST /{code}/rounds` (giới hạn 30/giờ, bỏ ván cũ cùng game, `buildRound`, `PracticeApi.start`, `deadline`) | `…/community/game/GameRoundService.java` | FR-006…FR-011 · AT-04…AT-08 | ⬜ |
| T-06 | Trả lời / khôi phục / kết thúc / bỏ: kiểm `GAME_ROUND`, sở hữu, `ROUND_TIME_OVER`, từ chối trường lạ, dịch mã lỗi `SESSION_*` → `ROUND_*`, `GameRoundHandler` (trần, `extra`) | `GameRoundService.java`, `GameRoundHandler.java`, controller | FR-012…FR-019 · AT-09…AT-16 | ⬜ |
| T-07 | ArchUnit: `community..` chỉ phụ thuộc `learning.api..`; integration SC-001 (cookie + origin game) | `t/architecture/ModuleBoundaryTest.java`, `t/community/game/SharedLoginIT.java` | §6 · AT-17, AT-18 | ⬜ |

## Frontend `cnhsk-game`

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-08 | Khởi tạo app (Vite React TS, Tailwind token chung, ESLint/Prettier/Vitest, cổng 5174, route `/`, `/play/:code`, 404); README chạy dev cùng `cnhsk-web` | `cnhsk-game/**` (khung) | FR-023, FR-027 | ⬜ |
| T-09 | `shared/api/client.ts` (cookie/Bearer/XSRF/refresh/WebView), `useCurrentUser`, nút đăng nhập, cầu nối WebView + `public/webview-harness.html` | `cnhsk-game/src/shared/**`, `cnhsk-game/public/webview-harness.html` | FR-024…FR-026, FR-030 · AT-19, AT-20, AT-22 | ⬜ |
| T-10 | SCR-035 Game Hub | `cnhsk-game/src/app/pages/GameHubPage.tsx` | FR-028, FR-031 | ⬜ |
| T-11 | `GameShell` (SCR-036 khung): sẵn sàng, đếm ngược, đồng hồ theo server, hàng đợi lượt, tự kết thúc, thoát có xác nhận, màn kết quả, `ROUND_FINISHED`; game giả để test | `cnhsk-game/src/shared/ui/GameShell.tsx`, `cnhsk-game/src/games/_demo/**` (chỉ test) | FR-029, FR-031 · AT-21 | ⬜ |

**Rủi ro cao:** T-06 (chống gian lận — AT-14 chạy song song thật), T-09 (cookie + WebView — kiểm tay bằng harness).

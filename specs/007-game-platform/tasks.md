# Tasks: Khung game và `cnhsk-game` — Spec #007

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-games.yaml`](contracts/openapi-games.yaml)

**Tổng: 11 task**, ≤ 4 giờ/task, một task một PR `spec/007-t0x` (T-10/T-11: `spec/007-t10`, `spec/007-t11`). Quy ước chung: `specs/001-auth-rbac/tasks.md`.
**Điều kiện:** spec 001 xong (cookie, CORS, FR-090); spec 003 đợt 0 xong.

## Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Chỉ bổ sung phần chưa có: `GameStatsApi.gameStats(userId)` + `countRecentGameRounds(userId, since)` qua query service chỉ đọc; `GAME_ROUND`/cấu hình đã có. Giữ nguyên `PracticeApi` và test khoá 4 phương thức | `…/learning/api/GameStatsApi.java`, `…/learning/practice/GameStatsQueryService.java` + test | FR-004, FR-007, FR-018 · dữ liệu AT-03/AT-05 | ✅ MERGED — PR #169 / f77747d |
| T-02 | `learning.api.ContentLookup` + cài đặt `ContentLookupImpl` (`pool`, `LexemeCard` đúng 11 trường, `pinyinKey`, `meaningKeys`, ưu tiên từ đã học, mặc định cấp 1) | `…/learning/api/ContentLookup.java`, `…/learning/content/ContentLookupImpl.java` + test | FR-020…FR-022 · AT-17 | ✅ MERGED — PR #173 / cc7b7ec |
| T-03 | Dùng lại `learning.api.GameCode` đã có đủ 6 mã (không tạo enum mới); `GameDefinition`, `GameRegistry` kiểm đủ 6 khi khởi động + 6 định nghĩa **giả** cho test; đồng bộ spec/OpenAPI sang 6 mã trong PR T-03 | `…/community/game/*`, `spec.md`, `contracts/openapi-games.yaml` | FR-001 · AT-01 | ✅ MERGED — PR #180 / fbf7ad0 |
| T-04 | `GET /api/community/games` (công khai, cache phần chung, `myStats`) | `…/community/game/web/GameController.java`, `GameCatalogService.java` | FR-002…FR-005 · AT-02, AT-03 | 🔄 chờ review — targeted/hồi quy 178/178; full verify 5408 F0/E0/S0, LINE 96,9323% |
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

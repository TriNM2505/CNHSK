# Tasks: Luật 4 game — Spec #008

> Nguồn: [`spec.md`](spec.md) v1.0.0 · Hợp đồng: [`../007-game-platform/contracts/openapi-games.yaml`](../007-game-platform/contracts/openapi-games.yaml)

**Tổng: 8 task**, ≤ 4 giờ/task, một task một PR `agent/008-<task>`. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
**Điều kiện:** spec 007 xong (T-01…T-11). Mỗi game có thể giao Codex độc lập (BE trước, FE sau).

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Tiện ích chung: 4 `ItemType`, `GameItemBuilders` (chọn nhiễu theo `meaningKeys`/`pinyinKey`, trộn + gán khoá), bỏ 4 định nghĩa giả khỏi đăng ký chính | `…/community/game/common/*` | GM-1…GM-7 · AT-16 | ⬜ |
| T-02 | `CharRainGame` | `…/community/game/rain/*` | FR-001…FR-004 · AT-01…AT-04 | ⬜ |
| T-03 | `PinyinMatchGame` | `…/community/game/pinyin/*` | FR-007…FR-011 · AT-05…AT-08 | ⬜ |
| T-04 | `RadicalMatchGame` + `RadicalLookalikes` | `…/community/game/radical/*` | FR-014…FR-017 · AT-09…AT-11 | ⬜ |
| T-05 | `CharCatchGame` | `…/community/game/catchgame/*` (`catch` là từ khoá Java) | FR-019…FR-024 · AT-12…AT-15 | ⬜ |
| T-06 | FE Mưa chữ + Ghép Bộ thủ | `cnhsk-game/src/games/char-rain/**`, `radical-match/**` | FR-005, FR-006, FR-018, FR-026…FR-028 · AT-17 | ⬜ |
| T-07 | FE Ghép Pinyin | `cnhsk-game/src/games/pinyin-match/**` | FR-012, FR-013 · AT-18 | ⬜ |
| T-08 | FE Bắt Chữ | `cnhsk-game/src/games/char-catch/**` | FR-025, FR-028 · AT-19 | ⬜ |

**Rủi ro cao:** T-03 (bảng dùng chung phương án), T-06 (hoạt ảnh rơi + bỏ lỡ chính xác).
**Ghi chú:** package `catch` không hợp lệ trong Java → dùng `catchgame`.

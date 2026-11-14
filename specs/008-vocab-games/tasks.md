# Tasks: Luật 6 game — Spec #008

> Nguồn: [`spec.md`](spec.md) v1.1.0 · Hợp đồng: [`../007-game-platform/contracts/openapi-games.yaml`](../007-game-platform/contracts/openapi-games.yaml)

**Tổng: 11 task**, ≤ 4 giờ/task, một task một nhánh `spec/008-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `game/` = `cnhsk-game/src/`.
**Điều kiện bắt đầu:** spec 008 `APPROVED`; spec 007 T-01…T-11 xong (khung `GameDefinition`, `GameShell`, `ContentLookup`). **Không migration.** Mã game dùng `learning.api.GameCode` (017/T-02, #163).
Mỗi game giao độc lập được (BE trước, FE sau). Nguồn FE: app 6 game ở nhánh `feat/dat-six-games` — chuyển vào `game/games/<code-kebab>/`, không nối API trực tiếp (FR-027).

## Đợt 1 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Tiện ích chung: `ItemType` mới `TRACE_CHAR`, `PINYIN_PAIR`, `RADICAL_CHOICE`, `CATCH_CHOICE` (practice + `PracticeApi`, giữ thứ tự cũ, thêm cuối); chấm `TRACE_CHAR` = `answer == "PASS"`; `GameItemBuilders` (chọn nhiễu theo `meaningKeys`/`pinyinKey`, trộn + gán khoá); bỏ định nghĩa giả khỏi đăng ký | `…/learning/practice/ItemType.java`, `…/learning/api/PracticeApi.java`, `…/community/game/common/*` + test | GM-1…GM-8 · AT-16 | ⬜ |
| T-02 | `CharRainGame` (tô nét) | `…/community/game/rain/*` + test | FR-001…FR-004 · AT-01…AT-03 | ⬜ |
| T-03 | `PinyinMatchGame` | `…/community/game/pinyin/*` + test | FR-007…FR-011 · AT-05…AT-08 | ⬜ |
| T-04 | `RadicalMatchGame` + `RadicalLookalikes` | `…/community/game/radical/*` + test | FR-014…FR-017 · AT-09…AT-11 | ⬜ |
| T-05 | `CharCatchGame` | `…/community/game/catchgame/*` + test | FR-019…FR-024 · AT-12…AT-15 | ⬜ |
| T-06 | `BrushStrokeGame` + `FlyingDartGame` (`accept` có/không thanh, `minDurationMs` theo âm tiết); test xếp hạng 6 game qua `LeaderboardRepository` | `…/community/game/{brush,dart}/*` + test | FR-029…FR-032, FR-035…FR-038, FR-041 · AT-20, AT-21, AT-23, AT-24 | ⬜ |

## Đợt 2 · Frontend (`cnhsk-game`)

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | Engine dùng chung: chuyển `engine/{arcade,strokeScore,path,pinyin}.ts` + test từ `feat/dat-six-games`; dữ liệu nét từ gói `hanzi-writer-data` (không commit file dữ liệu tự sinh); bỏ thưởng chuỗi/tốc độ khỏi hiển thị điểm | `game/engine/**`, `cnhsk-game/package.json` | GM-2, GM-8, FR-006 | ⬜ |
| T-08 | FE Mưa chữ (tô nét) + Ghép Bộ thủ | `game/games/char-rain/**`, `radical-match/**` | FR-004…FR-006, FR-018, FR-026…FR-028 · AT-04 | ⬜ |
| T-09 | FE Ghép Pinyin | `game/games/pinyin-match/**` | FR-012, FR-013 · AT-18 | ⬜ |
| T-10 | FE Bắt Chữ | `game/games/char-catch/**` | FR-025, FR-028 · AT-19 | ⬜ |
| T-11 | FE Thần Bút + Phi Đao (chỉ chế độ 60 s, ẩn chế độ màn — Q8); route `/play/brush-stroke`, `/play/flying-dart`, chuyển hướng `/arcade/*` | `game/games/{brush-stroke,flying-dart}/**`, router | FR-033, FR-034, FR-039, FR-040 · AT-17, AT-22 | ⬜ |

## Thứ tự

```
007 T-01…T-11 ─► T-01 ─┬─► T-02, T-03, T-04, T-05, T-06 (song song)
                       └─► T-07 ─► T-08, T-09, T-10, T-11 (song song; nối thật khi BE game tương ứng đã merge)
```

**Rủi ro cao:** T-01 (đổi enum dùng chung — giữ thứ tự, chạy hồi quy practice), T-03 (bảng dùng chung phương án), T-07 (dữ liệu nét), T-11 (bàn phím ảo + chữ rơi trên điện thoại).
**Ghi chú:** package `catch` không hợp lệ trong Java → dùng `catchgame`.

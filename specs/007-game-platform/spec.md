# Feature Specification: Khung game và ứng dụng `cnhsk-game`

**Feature:** `007-game-platform`
**Feature tree:** 5.6 Game Box (phần khung) · **Module:** `community` (package `community.game`) + app mới `cnhsk-game`
**Use case:** UC-087 (khung chơi), UC-088 (lưu điểm) · **Actor:** `LEARNER`
**Màn hình:** SCR-035 Game Hub · SCR-036 Game Session (khung chung)
**Scope:** MVP · **Priority:** P1

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | DRAFT — chờ cả nhóm duyệt |
| **Phụ thuộc** | Spec 001 (cookie tên miền cha, CORS 2 origin, `/api/auth/me`, FR-090 redirect về trang game) · Spec 003 §0 (bộ máy lượt luyện, `PracticeApi`) |
| **Spec đi kèm** | 008 (luật 4 game) — 007 khoá trước, 008 cắm `GameDefinition` vào |
| **Quyết định chung** | Brainstorm 2026-10-19: D-03, D-04, D-09 · Plan validation V1 (app riêng), V6 (React + CSS, không engine) |

> **Codex đọc:** `AGENTS.md` → spec 001 §0, §12 → spec 003 §0 → file này → `contracts/openapi-games.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| App `cnhsk-game` (`game.cnhsk.com`): khung, đăng nhập chung, Game Hub, khung ván chơi `GameShell` | Luật/giao diện từng game (spec 008) |
| API ván: tạo, trả lời từng lượt (chấm ở server), kết thúc, bỏ | Bảng xếp hạng công khai SCR-047 (V2, UC-083) |
| Chống gian lận: server cấp ván, chấm từng lượt, trần điểm, thời gian, 30 ván/giờ | Game gõ pinyin 5.7 (V2) |
| Hợp đồng WebView mobile (tiêm token, `postMessage`) | Code app React Native |
| `learning.api.ContentLookup` — kho từ cho game | Chế độ demo cho khách (BR-087-12 "nếu có") |
| Thành tích cá nhân: điểm cao nhất, số ván | Mastery từ game (cây tri thức; sự kiện spec 003 §0.9 đã sẵn) |

**Nguồn lượt:** `GAME_ROUND` — `kind=GAME`, `game_code` bắt buộc (`ck_game_code`), `IMMEDIATE`, `allowSkip=false`, `maxScoreFromServed=true`,
hạn = 3 s đếm ngược + thời gian ván + 15 s. **Không thay đổi DB** (`attempts.game_code` đã có).

### 0.1 · Vì sao chấm từng lượt ở server (D-09)

UC-088 mô tả client gửi "kết quả từng item" cuối ván. Ghép Pinyin cần **phản hồi tức thì** mà UC-087 cấm gửi đáp án theo ván.
Chấm từng lượt qua bộ máy spec 003 giải quyết cả hai: client chỉ biết đúng/sai **sau khi** đã chọn, điểm luôn do server đếm,
trần điểm = số lượt đã phát × điểm mỗi lượt — không thể vượt. Đổi lại: mỗi lượt một request (~50–150 ms), chấp nhận được với
game chạm/chuột nhịp vừa.

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-05** | Demo trọn sản phẩm | Game Box là tính năng MVP, mobile phải chơi được (5.6) |
| **BG-06** | Giải thích bảo mật khi bảo vệ | Cookie tên miền cha, CORS tường minh, chống gian lận điểm |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Đăng nhập ở `cnhsk.com` → mở `game.cnhsk.com` đã đăng nhập | 100% (spec 001 SC-002) |
| **SC-002** | Không cách nào ghi điểm vượt trần hoặc gửi điểm tự tính | 100% |
| **SC-003** | Response tạo ván không chứa đáp án | 100% |
| **SC-004** | Cùng một trang game chạy trong WebView với token tiêm | Kiểm bằng trang thử `webview-harness.html` (FR-030) |

---

## 2 · User Stories

### US-1 · Chơi một ván (P1)
**Given** tôi đã đăng nhập ở web chính
**When** tôi mở trang game, chọn "Ghép Pinyin", bấm "Bắt đầu"
**Then** server cấp ván, tôi chơi, mỗi lượt biết đúng/sai ngay, hết giờ thấy điểm, số đúng/sai, thời gian, "Kỷ lục mới!" nếu có

### US-2 · Chưa đăng nhập (P1)
**Given** tôi mở thẳng `game.cnhsk.com` khi chưa đăng nhập
**Then** tôi thấy danh sách game và nút "Đăng nhập để chơi" → về `cnhsk.com/login`, đăng nhập xong **quay lại đúng trang game**

### US-3 · Chơi trên mobile (P1)
**Given** app mobile mở trang game trong WebView và tiêm token
**Then** tôi chơi được như web; hết ván app nhận thông báo "đã chơi xong"

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Ván thứ 31 trong 60 phút | **429** `ROUND_LIMIT_EXCEEDED` kèm `retryAfterSeconds` |
| E2 | Trả lời lượt sau khi hết giờ ván | **422** `ROUND_TIME_OVER`, lượt không tính |
| E3 | Gửi `{score: 999999}` tới bất kỳ endpoint | Trường lạ bị từ chối (**422** `VALIDATION_FAILED`); không endpoint nào nhận điểm |
| E4 | Nộp lại ván đã kết thúc | **409** `ROUND_ALREADY_SUBMITTED` |
| E5 | Thoát giữa ván | Ván `ABANDONED`, không điểm, không vào thành tích (BR-087-15) |
| E6 | Mất mạng giữa ván | Lượt chưa gửi được giữ trong hàng đợi, gửi lại theo thứ tự khi có mạng; quá giờ → bị từ chối (E2) |
| E7 | Đóng tab không kết thúc | Ván tự `EXPIRED` sau hạn (job spec 003), không vào thành tích |
| E8 | Token hết hạn giữa ván (web) | Client gọi refresh rồi gửi lại lượt **một lần** (spec 001 FR-037) |
| E9 | Token hết hạn trong WebView | Gửi `TOKEN_EXPIRED` cho app, chờ `TOKEN_REFRESHED` ≤ 10 s, gửi lại; không có → `AUTH_REQUIRED` |
| E10 | Game không đủ dữ liệu (vd chưa có chữ có bộ thủ) | Thẻ game hiện "Chưa đủ dữ liệu", nút khoá; gọi tạo ván → **422** `GAME_UNAVAILABLE` |

---

## 3 · Functional Requirements

### Nhóm 1 — Danh mục game (FR-001…FR-005) · SCR-035

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL khai 4 game bằng `enum GameCode { CHAR_RAIN, PINYIN_MATCH, RADICAL_MATCH, CHAR_CATCH }` và interface `GameDefinition` (`code, name, description, skill, howToPlay, timeLimitSeconds, pointsPerCorrect, isAvailable(ctx), buildRound(ctx), validateAnswer(item, answer, elapsedMs), uiConfig()`); spec 008 cài đặt 4 lớp. **Không** bảng `game_catalog` (feature tree 5.6) |
| **FR-002** | `GET /api/community/games` SHALL trả 4 game theo thứ tự enum: `{code, name, description, skill, howToPlay, timeLimitSeconds, playable, unavailableReason, myStats}`; **công khai** (khách xem được, `myStats = null`) |
| **FR-003** | `playable = isAvailable(ctx)`; `false` → `unavailableReason = "NOT_ENOUGH_CONTENT"` (BR-087-11) |
| **FR-004** | `myStats` (đã đăng nhập) SHALL là `{bestPoints, roundsPlayed, lastPlayedAt}` chỉ tính ván `SUBMITTED` của người gọi cho game đó; `bestPoints = max(score) × pointsPerCorrect` |
| **FR-005** | Kết quả FR-002 cho khách SHALL được cache 60 s (`@Cacheable`, khoá theo `playable`); phần `myStats` không cache |

### Nhóm 2 — Tạo ván (FR-006…FR-011) · UC-087

| Mã | Yêu cầu |
|---|---|
| **FR-006** | `POST /api/community/games/{code}/rounds` `{hskLevel?}` SHALL yêu cầu `LEARNER`; `code` ngoài enum → **400** `INVALID_GAME_CODE`; `hskLevel` 1–9 nếu có |
| **FR-007** | THE system SHALL đếm ván `kind=GAME` của người gọi có `served_at > now() − 60 phút`; ≥ **30** → **429** `ROUND_LIMIT_EXCEEDED`, `retryAfterSeconds` = thời điểm ván cũ nhất trong cửa sổ + 60 phút − `now()` (BR-087-8) |
| **FR-007b** | WHEN người gọi có ván `IN_PROGRESS` chưa hết giờ **cùng game**, THE system SHALL chuyển ván cũ sang `ABANDONED` trước khi tạo ván mới (một người một ván đang chạy mỗi game) |
| **FR-008** | THE system SHALL gọi `GameDefinition.buildRound(ctx)` với `ctx = {userId, hskLevel, contentLookup}`; trả rỗng/không đủ → **422** `GAME_UNAVAILABLE` (E10) |
| **FR-009** | THE system SHALL tạo lượt qua `PracticeApi.start` (`source=GAME_ROUND`, `gameCode`, `items`, `ttlOverride = 3 s + timeLimit + 15 s`, `sessionMeta = {timeLimitSeconds, pointsPerCorrect, uiConfig}`) — `served_items` là snapshot đối chiếu (BR-087-6) |
| **FR-010** | Response **201** SHALL là `{roundId, gameCode, serverTime, startedAt, timeLimitSeconds, deadline, pointsPerCorrect, uiConfig, items:[ServedItemView]}` — **không** có `answer`/`accept` (SC-003, BR-087-5); `roundId = attempts.id` |
| **FR-011** | Đồng hồ ván tính từ `startedAt = served_at` của **server**; `deadline = startedAt + 3 s (đếm ngược) + timeLimitSeconds`. Client hiển thị đồng hồ theo `deadline − (serverTime − clientNow lúc nhận)`; không có tạm dừng (chốt open issue SCR-036) |

### Nhóm 3 — Trả lời, kết thúc, bỏ (FR-012…FR-019) · UC-088

| Mã | Yêu cầu |
|---|---|
| **FR-012** | `POST /api/community/games/rounds/{id}/answers` `{no, answer, durationMs}` SHALL: kiểm lượt tồn tại **và** `ref_type='GAME_ROUND'` (khác → **404** `ROUND_NOT_FOUND`); chủ sở hữu (**403** `ROUND_NOT_OWNED`); `now() ≤ deadline + 2 s` (**422** `ROUND_TIME_OVER`); rồi gọi `PracticeApi.answer` (E-R4…E-R12 spec 003) và `GameDefinition.validateAnswer` |
| **FR-013** | Body có trường ngoài `{no, answer, durationMs}` (vd `score`, `points`, `correct`) → **422** `VALIDATION_FAILED` (`@JsonIgnoreProperties(ignoreUnknown = false)`, E3) |
| **FR-014** | Response SHALL là `{no, correct, correctAnswer, pointsAwarded, totalPoints, answeredCount}`; `pointsAwarded = correct ? pointsPerCorrect : 0`; `totalPoints` = số câu đúng đến giờ × `pointsPerCorrect` (đếm từ `attempt_items`) |
| **FR-015** | `POST /api/community/games/rounds/{id}/finish` SHALL gọi `PracticeApi.finish`; ván đã kết thúc → **409** `ROUND_ALREADY_SUBMITTED`; quá hạn → **422** `ROUND_EXPIRED` |
| **FR-016** | `GameRoundHandler.onFinish` SHALL **khẳng định trần**: `score ≤ max_score` (= số lượt đã phát) — vi phạm là lỗi lập trình → ném `IllegalStateException` (rollback, log `ERROR`). Test cố ý chứng minh không đường nào vượt (SC-002, BR-088-4) |
| **FR-017** | `extra` SHALL là `{points, maxPoints, correct, wrong, missed, accuracy, durationMs, skill, bestPoints, newBest, suspicious}`; `missed` = lượt đã phát chưa trả lời; `accuracy = floor(correct*100/(correct+wrong))` (null nếu 0); `newBest = points > bestPoints trước ván này` (BR-088-11) |
| **FR-018** | Ván `suspicious` (spec 003 E-F5: ≥ 30% lượt nhanh hơn `minDurationMs` của game) **vẫn lưu điểm** (UC-088 A2) nhưng **không** tính vào `bestPoints` (FR-004 lọc `suspicious = false`) |
| **FR-019** | `POST /api/community/games/rounds/{id}/abandon` SHALL gọi `PracticeApi.abandon` (E5) → **204** |

### Nhóm 4 — `learning.api.ContentLookup` (FR-020…FR-022)

| Mã | Yêu cầu |
|---|---|
| **FR-020** | THE system SHALL cung cấp `ContentLookup.pool(GameContentQuery q) → List<LexemeCard>` với `q = {kinds, hskLevels, requireAudio, requireRadical, requireStrokeCount, preferLearnedBy (userId), limit (≤ 200)}` (BR-087-10) |
| **FR-021** | `LexemeCard` SHALL gồm `{id, kind, text, pinyin, pinyinKey, meaningShort, meaningKeys, radical, strokeCount, audioUrl, hskLevel}`; `pinyinKey` = `toNumbered(pinyin)` bỏ khoảng trắng (khoá đồng âm, spec 003 G-3); `meaningKeys` = tập mảnh `meaning_norm` (khoá trùng nghĩa, G-2); `meaningShort` = mảnh đầu `meaning_vi` |
| **FR-022** | `preferLearnedBy` SHALL ưu tiên từ người đó đã học (spec 003 FR-015); không đủ → bổ sung từ cấp `hskLevels`; người chưa học gì và không chọn cấp → cấp **1** (BR-087-9, UC-087 A4). Module `community` **chỉ** gọi `learning.api` (UC-087 `CROSS_MODULE_DIRECT_READ`; ArchUnit) |

### Nhóm 5 — App `cnhsk-game` (FR-023…FR-031)

| Mã | Yêu cầu |
|---|---|
| **FR-023** | THE system SHALL có app `cnhsk-game/` cùng stack `cnhsk-web` (React 18.3, Vite 5, TS 5.7, Tailwind 3.4, react-router 6.28; cùng token màu `design.md` §6), cổng dev **5174**, cấu trúc `src/{app, games/<game-code>/, shared/{api, auth, ui}}`; biến `VITE_API_BASE_URL`, `VITE_WEB_ORIGIN` |
| **FR-024** | `shared/api/client.ts` SHALL: `credentials: 'include'` **mọi** request (`kien-truc.md` §2.2); thêm `Authorization: Bearer` khi có `window.__CNHSK_TOKEN__`; thêm `X-XSRF-TOKEN` từ cookie `XSRF-TOKEN` cho request đổi trạng thái **khi không có** token tiêm (thiếu cookie → gọi `GET /api/auth/csrf` một lần); 401 `TOKEN_EXPIRED` → web: refresh rồi gửi lại một lần (E8); WebView: E9 |
| **FR-025** | `shared/auth/useCurrentUser` SHALL gọi `GET /api/auth/me` khi tải; 401 sau khi đã thử refresh → trạng thái khách |
| **FR-026** | Khách bấm "Đăng nhập để chơi": web → `window.location = {VITE_WEB_ORIGIN}/login?redirect={encodeURIComponent(location.href)}` (spec 001 FR-090 đã cho phép origin game); WebView → `postMessage({type:'AUTH_REQUIRED'})` |
| **FR-027** | Route: `/` = SCR-035 Game Hub; `/play/:code` = SCR-036 Game Session; khác → trang 404 có nút về Hub |
| **FR-028** | SCR-035 SHALL hiện 4 thẻ game (tên, mô tả, kỹ năng luyện, thời lượng, "Cách chơi" mở rộng được, thành tích cá nhân khi đăng nhập), nút "Chơi" (khoá khi `playable=false` kèm "Chưa đủ dữ liệu"), bộ chọn cấp HSK tuỳ chọn ("Từ tôi đang học" mặc định). **Không** có nút bảng xếp hạng (V2) |
| **FR-029** | `GameShell` (SCR-036 khung) SHALL lo: màn sẵn sàng ("Bắt đầu" → tạo ván), đếm ngược 3 s trước khi hiện lượt đầu (server đã cộng 3 s vào `deadline`, FR-011), đồng hồ theo `deadline`, điểm hiện tại, hàng đợi gửi lượt tuần tự (E6), tự `finish` khi hết giờ hoặc hết lượt, nút "Thoát" (xác nhận trong trang → `abandon`), màn kết quả (FR-017: điểm, đúng/sai/bỏ lỡ, độ chính xác, thời gian, "Kỷ lục mới!", "Chơi lại", "Về danh sách"). Game cụ thể chỉ nhận `items`, `uiConfig`, `submitAnswer(no, answer)` |
| **FR-030** | Hợp đồng WebView SHALL: đọc `window.__CNHSK_TOKEN__`, `window.__CNHSK_PLATFORM__ === 'mobile'`; gửi qua `window.ReactNativeWebView?.postMessage(JSON)`: `{type:'AUTH_REQUIRED'}`, `{type:'TOKEN_EXPIRED'}`, `{type:'ROUND_FINISHED', gameCode, points}`, `{type:'EXIT'}`; nhận `message` `{type:'TOKEN_REFRESHED', token}`. Có `public/webview-harness.html` mô phỏng app (iframe + tiêm token + log message) để kiểm tay. Token **không bao giờ** qua URL |
| **FR-031** | Giao diện game SHALL dùng được bằng cảm ứng (vùng chạm ≥ 44 px), màn 360 px trở lên, tôn trọng `prefers-reduced-motion` (giảm chuyển động, game vẫn chơi được) |

---

## 4 · Data Model

Không bảng mới. Ván = `learning.attempts` (`kind='GAME'`, `ref_type='GAME_ROUND'`, `game_code`), lượt = `attempt_items`.
`study_events.event_type = 'GAME'` (spec 003 E-F6). Thành tích (FR-004):

```sql
SELECT game_code, max(score) AS best_correct, count(*) AS rounds, max(submitted_at) AS last_played
FROM learning.attempts
WHERE user_id = :u AND kind = 'GAME' AND status = 'SUBMITTED'
GROUP BY game_code;                       -- best: thêm AND suspicious = false (FR-018)
```

Truy vấn nằm trong `learning` và lộ ra qua `PracticeApi.gameStats(userId)` (`AC-03`).

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `INVALID_GAME_CODE` | 400 | `code` ngoài enum | FR-006 |
| `ROUND_LIMIT_EXCEEDED` | 429 | ≥ 30 ván/60 phút | FR-007 |
| `GAME_UNAVAILABLE` | 422 | Không đủ dữ liệu | FR-008 |
| `ROUND_NOT_FOUND` | 404 | Không có / không phải ván game | FR-012 |
| `ROUND_NOT_OWNED` | 403 | Ván người khác (IDOR) | FR-012 |
| `ROUND_TIME_OVER` | 422 | Trả lời sau `deadline + 2 s` | FR-012 |
| `ROUND_ALREADY_SUBMITTED` | 409 | Kết thúc/bỏ lần 2 | FR-015 |
| `ROUND_EXPIRED` | 422 | Quá hạn lượt | FR-015 |
| `VALIDATION_FAILED` | 422 | Trường lạ (`score`…), dữ liệu sai | FR-013 |
| Lỗi bộ máy | | `ITEM_NOT_IN_SESSION` (= `ITEM_NOT_IN_ROUND`), `ANSWER_ALREADY_SUBMITTED`, `OPTION_NOT_IN_ITEM`, `RATE_LIMITED` — spec 003 | |

Bộ máy trả mã `SESSION_*`; lớp `community.game` **dịch** sang mã `ROUND_*` tương ứng để khớp UC.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **HR-03** cookie tên miền cha | Spec 001 FR-014/FR-015 — test SC-001 ở đây (T-11) |
| **HR-04** CORS | Spec 001 FR-028 (origin game) |
| **HR-06 / BUS-09** không tin điểm client | §0.1, FR-013, FR-016 |
| **AC-03** | `community.game` chỉ dùng `learning.api.PracticeApi`, `ContentLookup`; ArchUnit cấm `community..` → `learning..` trừ `learning.api..` |
| **AC-10** | Sự kiện spec 003 trong transaction — mastery từ game sau này cùng commit |
| Package | `community.game` (`GameCode`, `GameDefinition`, `GameRegistry`, `GameRoundService`, `GameRoundHandler`, `web.GameController`) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Trả lời một lượt (gồm mạng nội bộ) | < 120 ms p95 phía server |
| `NFR-P02` | Tạo ván | < 300 ms p95 |
| `NFR-P03` | Bundle `cnhsk-game` (gzip, chưa tính dữ liệu nét) | < 250 KB |
| `NFR-C01` | 50 người chơi đồng thời | Không lỗi |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Client gửi điểm | Không — không endpoint nào nhận điểm |
| Bảng `game_scores` / `ranking_entries` | Không — `attempts` là nguồn duy nhất (BR-088-10) |
| Mastery | Không (sự kiện sẵn sàng) |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Chấm từng lượt ở server thay cho nộp kết quả cuối ván | Chốt (D-09, §0.1) — khác mô tả UC-088 bước 1–6 nhưng thoả mọi BR của UC-088 |
| Q2 | Không có tạm dừng | Chốt — open issue SCR-036 |
| Q3 | Một ván đang chạy mỗi game mỗi người (FR-007b) | Chốt |
| Q4 | Ván `suspicious` lưu điểm nhưng không vào kỷ lục | Chốt (UC-088 A2) |
| Q5 | Danh mục game công khai cho khách | Chốt — Game Hub là màn công khai theo `design.md` |
| Q6 | App riêng `cnhsk-game`, React + CSS | Đã chốt ở plan (V1, V6) |

---

## 10 · Móc nối cây tri thức

| Chỗ | Bây giờ | Khi có cây |
|---|---|---|
| `ItemGradedEvent` từ `GAME_ROUND` | Không ai nghe | Cập nhật mastery với trọng số game thấp (BR-088-6), cùng transaction (`AC-10`), một nửa nếu `suspicious` |
| Chọn từ cho ván | Từ đã học → theo cấp | Ưu tiên từ đang yếu/gần hạn ôn |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Mạng chậm làm game phản xạ kém mượt | Game chọn nhịp ≥ 1,5 s/lượt (spec 008); `GameShell` hiện phản hồi lạc quan "đã chọn" trước khi server trả |
| Người chơi dò đáp án bằng nhiều ván | Mỗi ván lượt khác nhau; 30 ván/giờ; không ảnh hưởng mastery ở MVP |
| Dev: `localhost:5173` và `5174` chia sẻ cookie theo host | Đúng hành vi trình duyệt (cookie không theo cổng) — đủ để thử đăng nhập chung ở dev |

---

## 12 · Màn hình

| SCR | Route (`cnhsk-game`) | Guard | API | Field |
|---|---|---|---|---|
| SCR-035 Game Hub | `/` | Công khai (thành tích khi đăng nhập) | `GET /api/community/games`, `/api/auth/me` | `screen-fields.md` dòng 18042 |
| SCR-036 Game Session | `/play/:code` | `LEARNER` (khách → FR-026) | tạo ván, trả lời, kết thúc, bỏ | dòng 18446 |

| Open issue | Chốt |
|---|---|
| SCR-036: hằng số điểm, thời gian tối thiểu, hạn ván | `pointsPerCorrect`, `timeLimitSeconds`, `minDurationMs` khai trong `GameDefinition` (giá trị ở spec 008); hạn = 3 s + thời gian + 15 s |
| SCR-036: tạm dừng | Không có (Q2) |
| SCR-035/036: nút bảng xếp hạng | Ẩn (V2) |

---

## 13 · API Contract

[`contracts/openapi-games.yaml`](contracts/openapi-games.yaml)

| Method · Path | Auth | Thành công | Lỗi |
|---|---|---|---|
| `GET /api/community/games` | Tuỳ chọn | 200 `[GameInfo]` | — |
| `POST /api/community/games/{code}/rounds` | `LEARNER` | 201 `RoundStart` | 400, 401, 422, 429 |
| `GET /api/community/games/rounds/{id}` | `LEARNER` | 200 `RoundView` (khôi phục khi tải lại) | 403, 404 |
| `POST /api/community/games/rounds/{id}/answers` | `LEARNER` | 200 `RoundAnswerResult` | 400, 403, 404, 409, 422, 429 |
| `POST /api/community/games/rounds/{id}/finish` | `LEARNER` | 200 `RoundResult` | 403, 404, 409, 422 |
| `POST /api/community/games/rounds/{id}/abandon` | `LEARNER` | 204 | 403, 404, 409 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | Đăng ký 4 `GameDefinition` giả · T `GameRegistry` có đủ 4, thiếu một → ứng dụng không khởi động |
| AT-02 | FR-002, FR-003 | Khách gọi danh mục · T 200, `myStats=null`; game giả không đủ dữ liệu · T `playable=false`, `NOT_ENOUGH_CONTENT` |
| AT-03 | FR-004, FR-018 | 3 ván (5, 8 đúng; 9 đúng nhưng `suspicious`) · T `bestPoints = 8 × pointsPerCorrect`, `roundsPlayed=3` |
| AT-04 | FR-006 | `code=SNAKE` · T 400 `INVALID_GAME_CODE`; khách tạo ván · T 401 |
| AT-05 | FR-007, E1 | 30 ván trong giờ · T ván 31 → 429, `retryAfterSeconds` đúng (đồng hồ giả) |
| AT-06 | FR-007b | Tạo ván 2 cùng game khi ván 1 đang chạy · T ván 1 `ABANDONED` |
| AT-07 | FR-008, E10 | Game giả trả rỗng · T 422 `GAME_UNAVAILABLE` |
| AT-08 | FR-009, FR-010, SC-003 | T `attempts.kind=GAME`, `game_code` đúng, `served_items` có đáp án; response không có `answer`/`accept` |
| AT-09 | FR-012 | Ván người khác · T 403 `ROUND_NOT_OWNED`; id của lượt `TOPIC_TEST` · T 404 `ROUND_NOT_FOUND` |
| AT-10 | FR-012, E2 | Trả lời sau `deadline + 3 s` (đồng hồ giả) · T 422 `ROUND_TIME_OVER`, không có `attempt_items` mới |
| AT-11 | FR-013, E3 | Body có `score` · T 422; `POST /finish` body `{score:999999}` · T 422 |
| AT-12 | FR-014 | 3 lượt đúng · T `totalPoints = 3 × pointsPerCorrect` |
| AT-13 | FR-015, E4 | Kết thúc 2 lần · T 409 `ROUND_ALREADY_SUBMITTED` |
| AT-14 | FR-016, SC-002 | Thử mọi đường (trả lời lặp, câu lạ, song song 20 request cùng câu) · T `score ≤ số lượt đã phát` luôn đúng |
| AT-15 | FR-017 | 20 lượt, 12 đúng, 5 sai, 3 bỏ lỡ · T `missed=3`, `accuracy=70`, `maxPoints = 20 × pointsPerCorrect` |
| AT-16 | FR-019, E5 | Bỏ ván · T `ABANDONED`, không vào `roundsPlayed` |
| AT-17 | FR-020…FR-022 | Người chưa học, không chọn cấp · T toàn chữ/từ cấp 1; đã học 15 từ · T ván ưu tiên các từ đó; ArchUnit: `community` không import `learning` ngoài `learning.api` |
| AT-18 | SC-001 (HR-03) | Integration: đăng nhập WEB lấy cookie, gọi `/api/community/games` với `Origin: http://localhost:5174` + cookie · T `myStats` có giá trị; preflight trả `Access-Control-Allow-Origin` đúng origin game |
| AT-19 | FR-024, FR-025 (FE) | Vitest: có `__CNHSK_TOKEN__` → gửi `Authorization`, không gửi XSRF; không có → gửi XSRF; 401 `TOKEN_EXPIRED` → refresh 1 lần |
| AT-20 | FR-026 (FE) | Khách bấm đăng nhập · T URL `…/login?redirect=` chứa `location.href` mã hoá |
| AT-21 | FR-029 (FE) | `GameShell` với game giả: tự kết thúc khi hết giờ; hàng đợi gửi tuần tự khi mất mạng; "Thoát" có xác nhận → `abandon`; màn kết quả hiện "Kỷ lục mới!" khi `newBest` |
| AT-22 | FR-030 (FE) | Harness: tiêm token → chơi được; `ROUND_FINISHED` gửi đúng; `TOKEN_REFRESHED` cập nhật token |

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)

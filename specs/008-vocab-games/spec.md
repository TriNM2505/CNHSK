# Feature Specification: Luật 4 game Game Box

**Feature:** `008-vocab-games`
**Feature tree:** 5.6 Game Box (luật từng game) · **Module:** `community` (package `community.game.<code>`) + `cnhsk-game/src/games/**`
**Use case:** UC-087 (BR-087-1, BR-087-13, BR-087-14), UC-088 (trần điểm) · **Actor:** `LEARNER`
**Màn hình:** SCR-036 Game Session (4 biến thể)
**Scope:** MVP · **Priority:** P1

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | DRAFT — chờ cả nhóm duyệt |
| **Phụ thuộc** | Spec 007 (`GameDefinition`, `GameShell`, `ContentLookup`) · Spec 002 (dữ liệu `CHAR`/`WORD`, `radical`, `audio_url`) |
| **Quyết định chung** | Brainstorm 2026-10-19: D-09 · Plan V2 (4 game như use case), V6 (React + CSS) |

> **Codex đọc:** `AGENTS.md` → spec 007 → file này → `tasks.md`. Không có API mới (dùng hợp đồng spec 007).

---

## 0 · Phạm vi và luật chung

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Luật, sinh lượt, điều kiện khả dụng, giao diện của 4 game | Game gõ pinyin (5.7, V2) · bảng xếp hạng (V2) |
| Tham số: thời gian, số lượt, điểm, thời gian tối thiểu | Âm thanh hiệu ứng, nhạc nền (YAGNI) |

### 0.1 · Luật chung cho cả 4 game (GM-xx)

| Mã | Luật |
|---|---|
| **GM-1** | **Một lượt = một câu chọn** chấm ở server (spec 007 §0.1). Đáp án client gửi là khoá phương án (`A`–`F`) |
| **GM-2** | `pointsPerCorrect = 10` cho cả 4 game; không có thưởng tốc độ/chuỗi (điểm phải tính được từ `attempt_items` — trần = số lượt × 10) |
| **GM-3** | Phương án gây nhiễu tuân spec 003 G-2 (trùng nghĩa) bằng `LexemeCard.meaningKeys` và G-3 (đồng âm) bằng `pinyinKey`: **không** phương án nào khác đáp án đúng mà cũng đúng |
| **GM-4** | Chọn từ: `ContentLookup.pool` với `preferLearnedBy = userId`, `hskLevels = [hskLevel]` nếu người chơi chọn; lấy tối đa 200 thẻ rồi chọn ngẫu nhiên theo nhu cầu game; một thẻ xuất hiện **tối đa một lần** làm đáp án đúng trong một ván |
| **GM-5** | Lượt bỏ lỡ (hết thời gian của lượt trên client) **không gửi**; tính vào `missed` khi kết thúc (spec 007 FR-017) |
| **GM-6** | `minDurationMs` mỗi game (bảng §0.2) dùng cho cờ nghi ngờ (spec 003 E-R8, E-F5) |
| **GM-7** | Câu sinh ra **không** chứa đáp án trong phần gửi client: không có trường nào tiết lộ thứ tự đúng (khoá gán sau khi trộn, spec 003 G-6) |

### 0.2 · Bảng tham số

| Game | `code` | Kỹ năng (`skill`) | Thời gian | Số lượt phát | Phương án/lượt | `minDurationMs` | Trần điểm |
|---|---|---|---|---|---|---|---|
| Mưa chữ | `CHAR_RAIN` | `CHARACTER` | 60 s | 30 | 4 | 400 | 300 |
| Ghép Pinyin | `PINYIN_MATCH` | `VOCAB` | 90 s | 18 (3 bảng × 6) | 6 | 600 | 180 |
| Ghép Bộ thủ | `RADICAL_MATCH` | `CHARACTER` | 90 s | 15 | 4 | 700 | 150 |
| Bắt Chữ | `CHAR_CATCH` | `LISTENING` | 75 s | 20 | 6 | 300 | 200 |

`ItemType` mới (spec 003): `RAIN_CHOICE`, `PINYIN_PAIR`, `RADICAL_CHOICE`, `CATCH_CHOICE` — đều `gradable`.

---

## 1 · Context & Goal

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | 4 game chơi được hết ván trên web và WebView với dữ liệu HSK 1 đã nhập | 4/4 |
| **SC-002** | Không lượt nào có hai đáp án đúng (đồng âm, trùng nghĩa, trùng bộ thủ) | 0 trên 1 000 ván sinh thử |
| **SC-003** | Không lượt nào lộ đáp án trong response | 100% |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | **Mưa chữ:** chữ/từ rơi từ trên xuống; tôi bấm đúng nghĩa trong 4 nút trước khi chữ chạm đất |
| US-2 | **Ghép Pinyin:** bảng 6 từ và 6 thẻ pinyin; tôi chạm một từ rồi chạm thẻ pinyin của nó; ghép xong bảng thì sang bảng mới |
| US-3 | **Ghép Bộ thủ:** hiện một chữ Hán; tôi chọn đúng bộ thủ của chữ trong 4 bộ |
| US-4 | **Bắt Chữ:** hiện yêu cầu (nghĩa, pinyin hoặc âm thanh) và lưới 6 chữ; tôi chạm đúng chữ trong 4 giây |

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Người chơi mới chưa học, không chọn cấp | Dùng cấp 1 (spec 007 FR-022) |
| E2 | Cấp chọn có ít dữ liệu hơn số lượt cần | Phát ít lượt hơn (tối thiểu §3 mỗi game); dưới tối thiểu → `GAME_UNAVAILABLE` |
| E3 | Từ có hai cách đọc (多音字) ở Ghép Pinyin | Chỉ dùng `WORD` (≥ 2 chữ) — ngữ cảnh từ xác định cách đọc (BR-087-13) |
| E4 | Hai chữ cùng bộ thủ trong Ghép Bộ thủ | Không ảnh hưởng — phương án là **bộ thủ**, phải khác nhau đôi một |
| E5 | Bắt Chữ dạng âm thanh, audio lỗi | Lượt đó dùng dạng nghĩa thay (server quyết định lúc sinh — chỉ chọn dạng âm thanh khi có `audio_url`); lỗi tải trên client → người chơi bỏ lỡ lượt, không bị trừ |
| E6 | Ghép Pinyin: chọn sai thẻ | Lượt của từ đó tính sai; hiện cặp đúng 800 ms rồi gỡ cả từ và thẻ đúng khỏi bảng |

---

## 3 · Functional Requirements

### Nhóm 1 — Mưa chữ `CHAR_RAIN` (FR-001…FR-006)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `buildRound` SHALL lấy thẻ `CHAR` và `WORD` (ưu tiên `CHAR` khi đủ), chọn **30** thẻ làm đề (tối thiểu **8**, ít hơn → không khả dụng) |
| **FR-002** | Mỗi lượt `RAIN_CHOICE`: `prompt.text` = chữ/từ; 4 phương án = `meaningShort` của đề + 3 thẻ khác có `meaningKeys` **rời** với đề (GM-3); không đủ 3 nhiễu → bỏ thẻ đó |
| **FR-003** | `isAvailable` SHALL đúng khi pool cấp 1 (hoặc của người chơi) sinh được ≥ 8 lượt hợp lệ |
| **FR-004** | `uiConfig = {fallStartMs: 6000, fallStepMs: 150, fallMinMs: 3000, lanes: 1}` — lượt thứ `i` (từ 0) rơi trong `max(fallMinMs, fallStartMs − i × fallStepMs)` ms |
| **FR-005** | Giao diện SHALL: một chữ rơi tại một thời điểm (CSS `transform: translateY` + `transition` tuyến tính theo thời gian rơi), 4 nút nghĩa cố định đáy màn (≥ 44 px), chạm nút → khoá nút, gửi lượt; đúng → chữ vỡ (lớp CSS 200 ms) và sang lượt sau; sai → chữ đỏ + rung, hiện nghĩa đúng 600 ms, sang lượt sau; chữ chạm đất chưa trả lời → bỏ lỡ (GM-5) |
| **FR-006** | `prefers-reduced-motion`: chữ hiện cố định với thanh đếm thời gian ngang thay cho chuyển động rơi |

### Nhóm 2 — Ghép Pinyin `PINYIN_MATCH` (FR-007…FR-013)

| Mã | Yêu cầu |
|---|---|
| **FR-007** | `buildRound` SHALL chỉ dùng thẻ `WORD` có `pinyin` (E3), chia **3 bảng × 6 từ** (tối thiểu 1 bảng = 6 từ; tạo số bảng đầy đủ tối đa có thể, ≤ 3) |
| **FR-008** | Trong một bảng: 6 `pinyinKey` **khác nhau đôi một**, 6 `text` khác nhau (GM-3) |
| **FR-009** | Mỗi từ là một lượt `PINYIN_PAIR`: `prompt.text` = từ, `options` = **6 thẻ pinyin của bảng** (hiển thị `pinyin` có dấu), cùng bộ khoá `A`–`F` cho cả 6 lượt của bảng (một khoá = một thẻ), thứ tự thẻ trộn một lần cho mỗi bảng; `answer` = khoá thẻ của chính từ đó |
| **FR-010** | `uiConfig = {boards: [[1,2,3,4,5,6],[7,…,12],[13,…,18]]}` (số thứ tự lượt theo bảng) |
| **FR-011** | `validateAnswer`: không thêm luật ngoài spec 003 (mỗi từ một lần — E-R5) |
| **FR-012** | Giao diện SHALL: hai cột (từ | thẻ pinyin) — màn hẹp: hai hàng lưới; chạm từ → chọn (viền), chạm thẻ → gửi `PINYIN_PAIR` cho từ đang chọn với khoá thẻ; đúng → cả hai mờ dần và gỡ; sai → E6; hết 6 từ (đã trả lời) → bảng sau; hết bảng → `GameShell` kết thúc |
| **FR-013** | Một thẻ đã được gỡ (ghép đúng hoặc là thẻ đúng của từ đã sai) SHALL không chọn được nữa |

### Nhóm 3 — Ghép Bộ thủ `RADICAL_MATCH` (FR-014…FR-018)

| Mã | Yêu cầu |
|---|---|
| **FR-014** | `buildRound` SHALL dùng thẻ `CHAR` có `radical` (`requireRadical`), chọn **15** chữ (tối thiểu **8**); tập bộ thủ khác nhau trong pool ≥ 4 |
| **FR-015** | Mỗi lượt `RADICAL_CHOICE`: `prompt.text` = chữ, `prompt.hint` = `meaningShort`; 4 phương án = bộ thủ đúng + 3 bộ thủ **khác** lấy từ chữ khác trong pool, khác nhau đôi một (BR-087-14, E4) |
| **FR-016** | Ưu tiên nhiễu là bộ thủ **trông giống** khi có: danh sách cặp dễ nhầm cố định trong code `RadicalLookalikes` (`氵/冫`, `扌/才`, `亻/彳`, `日/曰`, `木/禾`, `礻/衤`, `宀/冖`, `艹/竹`, `讠/言`, `钅/金`) — chỉ dùng khi bộ đó có trong pool |
| **FR-017** | `isAvailable` SHALL đúng khi ≥ 8 chữ có `radical` và ≥ 4 bộ thủ khác nhau |
| **FR-018** | Giao diện SHALL: chữ to giữa màn (≥ 96 px), nghĩa nhỏ bên dưới, 4 nút bộ thủ (≥ 64 px chữ); đúng → nhấn mạnh bộ thủ, sang lượt sau sau 500 ms; sai → hiện bộ thủ đúng 800 ms |

### Nhóm 4 — Bắt Chữ `CHAR_CATCH` (FR-019…FR-025)

| Mã | Yêu cầu |
|---|---|
| **FR-019** | `buildRound` SHALL dùng thẻ `CHAR`/`WORD`, chọn **20** đề (tối thiểu **8**) |
| **FR-020** | Dạng yêu cầu luân phiên theo thứ tự lượt: `MEANING` → `PINYIN` → `AUDIO`; `AUDIO` chỉ khi đề có `audioUrl`, không có → `MEANING` (E5) |
| **FR-021** | Mỗi lượt `CATCH_CHOICE`: `prompt` = `{hint: meaningShort}` (MEANING) · `{pinyin}` (PINYIN) · `{audioUrl}` (AUDIO, `rules.hasAudio=true`, `maxReplays=1`); `prompt.text` **không** có; 6 phương án là chữ/từ |
| **FR-022** | Nhiễu: dạng MEANING loại trùng nghĩa (G-2); dạng PINYIN/AUDIO **chỉ** loại đồng âm cùng thanh (G-3, so `pinyinKey`); cùng âm khác thanh (`shì`/`shī`) **được phép** — đó là điểm luyện thanh điệu |
| **FR-023** | `uiConfig = {perItemMs: 4000, gridColumns: 3}` |
| **FR-024** | `isAvailable` SHALL đúng khi sinh được ≥ 8 lượt với 5 nhiễu hợp lệ |
| **FR-025** | Giao diện SHALL: yêu cầu ở trên (nghĩa / pinyin / nút loa tự phát **sau** khi người chơi đã bấm "Bắt đầu" — trình duyệt cho phép phát vì có tương tác), lưới 3×2 ô chữ, thanh 4 s; chạm ô → gửi; đúng → ô xanh; sai → ô đỏ + ô đúng xanh 500 ms; hết 4 s → bỏ lỡ; sang lượt sau |

### Nhóm 5 — Chung (FR-026…FR-028)

| Mã | Yêu cầu |
|---|---|
| **FR-026** | Mỗi game SHALL có `howToPlay` tiếng Việt ≤ 300 ký tự (hiện ở SCR-035) và `description` ≤ 120 ký tự |
| **FR-027** | Mỗi game frontend SHALL là một thư mục `cnhsk-game/src/games/<code-kebab>/` xuất một component nhận `{items, uiConfig, submitAnswer, onMissed, disabled}` từ `GameShell` — không tự gọi API |
| **FR-028** | Phản hồi đúng/sai SHALL hiện **lạc quan** khi server chưa trả (khoá thao tác, hiệu ứng "đang chấm" ≤ 300 ms) rồi theo kết quả server; server lỗi → hiện "Mất kết nối, đang thử lại" (hàng đợi `GameShell`) |

---

## 4 · Data Model

Không bảng mới. Bộ thủ đọc từ `lexemes.radical` (spec 002 §4.1). Lượt lưu theo spec 003 §0.3, ví dụ Ghép Pinyin:

```json
{ "no": 3, "type": "PINYIN_PAIR", "skill": "VOCAB", "lexemeId": 42,
  "prompt": { "text": "朋友" },
  "options": [ {"key":"A","text":"lǎoshī"}, {"key":"B","text":"péngyou"}, {"key":"C","text":"xuésheng"},
               {"key":"D","text":"shíhou"}, {"key":"E","text":"yīfu"}, {"key":"F","text":"māma"} ],
  "answer": "B", "rules": { "minDurationMs": 600 } }
```

---

## 5 · Error Matrix

Không có mã lỗi mới — dùng spec 007 §5 và spec 003 §5. Game không đủ dữ liệu → `GAME_UNAVAILABLE`.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **BUS-09 / HR-06** | Chấm ở server từng lượt |
| **AC-03** | Sinh lượt trong `community.game.*` từ `LexemeCard` (`ContentLookup`), không đọc bảng `learning` |
| V6 | React + CSS (transition/keyframes), không canvas engine |
| Package | `community.game.rain`, `.pinyin`, `.radical`, `.catchgame` (`catch` là từ khoá Java) — mỗi gói một `GameDefinition` + `…ItemBuilder` |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `buildRound` | < 100 ms với pool 200 thẻ |
| `NFR-U01` | 60 fps hoạt ảnh trên điện thoại tầm trung (chỉ animate `transform`/`opacity`) | Kiểm tay |
| `NFR-U02` | Chơi được trên màn 360 × 640 không cuộn | Kiểm tay |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Điểm tính ở client | Không |
| Thêm game ngoài 4 game MVP | Không (BR-087-1) |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Luật cụ thể 4 game (UC chỉ có tên + BR-087-13/14) | **Cần nhóm duyệt** — đề xuất §3 |
| Q2 | 10 điểm/lượt đúng, không thưởng tốc độ | Chốt tạm — đổi được bằng hằng số, nhưng thưởng tốc độ cần thời điểm phát từng lượt ở server (chưa có) |
| Q3 | Ghép Pinyin chỉ dùng từ ≥ 2 chữ | Chốt (BR-087-13) |
| Q4 | Bắt Chữ cho phép nhiễu cùng âm khác thanh | Chốt — mục tiêu luyện thanh điệu |
| Q5 | Danh sách bộ thủ dễ nhầm 10 cặp cố định | Chốt tạm — giáo viên bổ sung sau |

---

## 10 · Móc nối cây tri thức

Mỗi lượt có `lexemeId` + `skill` → `ItemGradedEvent` đủ để cây cập nhật mastery với trọng số game (spec 007 §10).

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Mưa chữ phụ thuộc độ trễ mạng | Phản hồi lạc quan (FR-028); nhịp rơi ≥ 3 s |
| Dữ liệu bộ thủ thiếu → Ghép Bộ thủ không khả dụng | Hiện "Chưa đủ dữ liệu" (spec 007 FR-003); nhập thêm `radical` qua spec 002 |

---

## 12 · Màn hình

SCR-036 Game Session (`screen-fields.md` dòng 18446) — 4 biến thể trong `GameShell`. Route `cnhsk-game` `/play/char-rain`, `/play/pinyin-match`,
`/play/radical-match`, `/play/char-catch` (`code` dạng kebab trên URL, đổi sang enum khi gọi API).
Ô nhập pinyin bàn phím trong SCR-036 thuộc game gõ pinyin (V2) — không có ở 4 game này.

---

## 13 · API Contract

Không có endpoint mới — [`../007-game-platform/contracts/openapi-games.yaml`](../007-game-platform/contracts/openapi-games.yaml).
`uiConfig` theo từng game: FR-004, FR-010, FR-023 (`RADICAL_MATCH`: `{}`).

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001, FR-002 | Pool 50 thẻ · T 30 lượt, mỗi lượt 4 phương án, đúng một phương án là nghĩa của đề |
| AT-02 | FR-002, SC-002 | 1 000 ván với pool có 爸爸/父亲 · T không lượt nào có cả hai nghĩa trùng |
| AT-03 | FR-003, E2 | Pool 7 thẻ · T `isAvailable=false`; tạo ván → 422 |
| AT-04 | FR-004 | T thời gian rơi lượt 0 = 6000, lượt 20 = 3000, lượt 25 = 3000 |
| AT-05 | FR-007, E3 | Pool chỉ có `CHAR` · T Ghép Pinyin không khả dụng |
| AT-06 | FR-008, SC-002 | 1 000 ván · T không bảng nào có 2 thẻ cùng `pinyinKey` |
| AT-07 | FR-009, FR-010 | T 6 lượt cùng bảng có cùng `options`; khoá đúng của mỗi lượt khác nhau đôi một |
| AT-08 | FR-007 | Pool 14 từ · T 2 bảng (12 lượt) |
| AT-09 | FR-014, FR-015 | T mỗi lượt 4 bộ thủ khác nhau, đúng một bộ của chữ |
| AT-10 | FR-016 | Pool có chữ bộ 氵 và chữ bộ 冫 · T lượt chữ bộ 氵 có 冫 trong nhiễu |
| AT-11 | FR-017 | Pool 10 chữ chỉ 3 bộ thủ · T không khả dụng |
| AT-12 | FR-020, E5 | Đề không có audio · T dạng `MEANING` thay `AUDIO` |
| AT-13 | FR-021, SC-003 | T lượt `AUDIO`/`MEANING` không có `prompt.text`; JSON không có `answer` |
| AT-14 | FR-022 | Dạng PINYIN đề 是 · T không có 事 (đồng âm); có thể có 诗 (shī) |
| AT-15 | GM-2 | Ván Bắt Chữ đúng 20/20 · T `points=200 = maxPoints` |
| AT-16 | GM-4 | T trong một ván không thẻ nào làm đề hai lần |
| AT-17 | FR-005, FR-006 (FE) | Vitest: chữ chạm đất → `onMissed`; nút khoá sau khi chạm; reduced-motion hiện thanh thời gian |
| AT-18 | FR-012, FR-013 (FE) | Chọn từ rồi thẻ → gửi đúng khoá; thẻ đã gỡ không chọn được; xong bảng → bảng sau |
| AT-19 | FR-025, FR-028 (FE) | Hết 4 s → `onMissed`; phản hồi lạc quan rồi theo server |

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)

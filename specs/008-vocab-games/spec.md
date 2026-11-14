# Feature Specification: Luật 6 game Game Box

**Feature:** `008-vocab-games`
**Feature tree:** 5.6 Game Box (luật từng game), 5.7 Game gõ pinyin (Phi Đao) · **Module:** `community` (package `community.game.<code>`) + `cnhsk-game/src/games/**`
**Use case:** UC-087 (BR-087-1, BR-087-13, BR-087-14), UC-088 (trần điểm) · **Actor:** `LEARNER`
**Màn hình:** SCR-036 Game Session (6 biến thể)
**Scope:** MVP · **Priority:** P1

| Trường | Giá trị |
|---|---|
| **Version** | 1.1.0 |
| **Status** | APPROVED — leader duyệt 2026-11-14, được code theo `tasks.md` |
| **Phụ thuộc** | Spec 007 (`GameDefinition`, `GameShell`, `ContentLookup`) · Spec 002 (dữ liệu `CHAR`/`WORD`, `radical`, `stroke_count`, `audio_url`) · Spec 017 (`learning.api.GameCode`, bảng xếp hạng game) |
| **Quyết định chung** | Brainstorm 2026-10-19: D-09 · RFC đợt hoàn thiện H11 (6 game như nhau: lưu điểm, xếp hạng) · Leader chốt 2026-11-14 (§9) |

> **Người code đọc:** `AGENTS.md` → spec 007 → file này → `tasks.md`. Không có API mới (dùng hợp đồng spec 007).

### Thay đổi so với 1.0.0

| Mục | 1.0.0 | 1.1.0 |
|---|---|---|
| Số game | 4 | **6**: thêm Thần Bút `BRUSH_STROKE`, Phi Đao `FLYING_DART` |
| Mưa chữ | Chọn nghĩa chữ đang rơi | **Tô từng nét** chữ Hán theo app `cnhsk-game` (leader chốt 14/11) |
| Mã game | Enum riêng trong 007 | `learning.api.GameCode` (017/T-02, PR #163) — 6 mã |
| Bảng xếp hạng | Ngoài phạm vi (V2) | Mọi ván `SUBMITTED` ghi `game_code`, điểm server tính → 017 xếp hạng (FR-041) |
| `ItemType` mới | 4 | `TRACE_CHAR`, `PINYIN_PAIR`, `RADICAL_CHOICE`, `CATCH_CHOICE`; Phi Đao dùng lại `PINYIN_INPUT` có sẵn |

---

## 0 · Phạm vi và luật chung

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Luật, sinh lượt, điều kiện khả dụng, giao diện của 6 game | Chế độ "màn" (stage) của Thần Bút/Phi Đao — cần API luyện không lưu, để sau (Q8) |
| Tham số: thời gian, số lượt, điểm, thời gian tối thiểu | Thưởng chuỗi/tốc độ (Q2) · âm thanh hiệu ứng, nhạc nền (YAGNI) |
| Ghi ván để 017 xếp hạng | Server chấm lại nét vẽ (Q6) |

### 0.1 · Luật chung cho cả 6 game (GM-xx)

| Mã | Luật |
|---|---|
| **GM-1** | **Một lượt = một câu** gửi lên server và được ghi ở server (spec 007 §0.1). Game chọn: đáp án là khoá phương án (`A`–`F`). Phi Đao: chuỗi pinyin đã gõ. Game tô nét: `PASS` (GM-8) |
| **GM-2** | `pointsPerCorrect = 10` cho cả 6 game; **không** có thưởng tốc độ hay chuỗi — điểm luôn tính được từ `attempt_items` (trần = số lượt đã phát × 10, spec 007 FR-016) |
| **GM-3** | Phương án gây nhiễu tuân spec 003 G-2 (trùng nghĩa) bằng `LexemeCard.meaningKeys` và G-3 (đồng âm) bằng `pinyinKey`: **không** phương án nào khác đáp án đúng mà cũng đúng |
| **GM-4** | Chọn từ: `ContentLookup.pool` với `preferLearnedBy = userId`, `hskLevels = [hskLevel]` nếu người chơi chọn; lấy tối đa 200 thẻ rồi chọn ngẫu nhiên theo nhu cầu game; một thẻ xuất hiện **tối đa một lần** làm đề trong một ván |
| **GM-5** | Lượt bỏ lỡ (hết thời gian của lượt trên client) **không gửi**; tính vào `missed` khi kết thúc (spec 007 FR-017) |
| **GM-6** | `minDurationMs` từng lượt (`rules.minDurationMs`, §0.2) dùng cho cờ nghi ngờ (spec 003 E-R8, E-F5: ≥ 30% lượt nhanh hơn mức tối thiểu → ván `suspicious`) |
| **GM-7** | Câu gửi client **không** chứa đáp án: không có trường nào tiết lộ thứ tự đúng (khoá gán sau khi trộn, spec 003 G-6). Ngoại lệ có chủ đích: game tô nét cần dữ liệu nét của chữ để vẽ — đó là đề, không phải đáp án |
| **GM-8** | **Game tô nét** (Mưa chữ, Thần Bút) chấm nét trên trình duyệt (`gradeStroke`); client chỉ gửi `answer = "PASS"` khi hoàn thành chữ. Server **không** tin điểm client: tự tính điểm theo GM-2, giữ trần, và lượt có `durationMs < rules.minDurationMs` bị đếm là nhanh bất thường (GM-6). Rủi ro chấp nhận ở §11 |

### 0.2 · Bảng tham số

| Game | `GameCode` | Kỹ năng (`skill`) | Thời gian ván | Số lượt phát | Lựa chọn/lượt | `minDurationMs` mỗi lượt | Trần điểm |
|---|---|---|---|---|---|---|---|
| Mưa chữ | `CHAR_RAIN` | `WRITING` | 180 s | 10 | — (tô nét) | `strokeCount × 300` | 100 |
| Ghép Pinyin | `PINYIN_MATCH` | `VOCAB` | 90 s | 18 (3 bảng × 6) | 6 | 600 | 180 |
| Ghép Bộ thủ | `RADICAL_MATCH` | `CHARACTER` | 90 s | 15 | 4 | 700 | 150 |
| Bắt Chữ | `CHAR_CATCH` | `LISTENING` | 75 s | 20 | 6 | 300 | 200 |
| Thần Bút | `BRUSH_STROKE` | `WRITING` | 60 s | 60 | — (tô 1 nét) | 300 | 600 |
| Phi Đao | `FLYING_DART` | `VOCAB` | 60 s | 60 | — (gõ pinyin) | `400 + 150 × số âm tiết` | 600 |

`ItemType` mới (spec 003): `TRACE_CHAR`, `PINYIN_PAIR`, `RADICAL_CHOICE`, `CATCH_CHOICE` — đều `gradable`. `TRACE_CHAR`: `answer = "PASS"`, đúng khi client gửi `PASS`. Phi Đao dùng `PINYIN_INPUT` + `PinyinGrader` có sẵn.

---

## 1 · Context & Goal

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | 6 game chơi được hết ván trên web và WebView với dữ liệu HSK 1 đã nhập | 6/6 |
| **SC-002** | Không lượt nào có hai đáp án đúng (đồng âm, trùng nghĩa, trùng bộ thủ) | 0 trên 1 000 ván sinh thử |
| **SC-003** | Không lượt nào lộ đáp án trong response (trừ dữ liệu nét của game tô nét, GM-7) | 100% |
| **SC-004** | Ván của cả 6 game hiện trên bảng xếp hạng game của 017 | 6/6 |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | **Mưa chữ:** 10 chữ Hán lần lượt hiện; tôi tô từng nét theo đúng thứ tự, nét sai thì viết lại; xong chữ thì sang chữ sau |
| US-2 | **Ghép Pinyin:** bảng 6 từ và 6 thẻ pinyin; tôi chạm một từ rồi chạm thẻ pinyin của nó; ghép xong bảng thì sang bảng mới |
| US-3 | **Ghép Bộ thủ:** hiện một chữ Hán; tôi chọn đúng bộ thủ của chữ trong 4 bộ |
| US-4 | **Bắt Chữ:** hiện yêu cầu (nghĩa, pinyin hoặc âm thanh) và lưới 6 chữ; tôi chạm đúng chữ trong 4 giây |
| US-5 | **Thần Bút:** chữ Hán rơi từ trên xuống; tôi tô đúng nét đầu của chữ trước khi nó chạm đất để hạ chữ; ván 60 giây |
| US-6 | **Phi Đao:** từ rơi từ trên xuống kèm nghĩa; tôi gõ pinyin (có dấu, số thanh hoặc không dấu) rồi Enter để phóng dao; ván 60 giây |

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Người chơi mới chưa học, không chọn cấp | Dùng cấp 1 (spec 007 FR-022) |
| E2 | Cấp chọn có ít dữ liệu hơn số lượt cần | Phát ít lượt hơn (tối thiểu §3 mỗi game); dưới tối thiểu → `GAME_UNAVAILABLE` |
| E3 | Từ có hai cách đọc (多音字) ở Ghép Pinyin | Chỉ dùng `WORD` (≥ 2 chữ) — ngữ cảnh từ xác định cách đọc (BR-087-13) |
| E4 | Hai chữ cùng bộ thủ trong Ghép Bộ thủ | Không ảnh hưởng — phương án là **bộ thủ**, phải khác nhau đôi một |
| E5 | Bắt Chữ dạng âm thanh, audio lỗi | Lượt đó dùng dạng nghĩa thay (server quyết định lúc sinh — chỉ chọn dạng âm thanh khi có `audio_url`); lỗi tải trên client → người chơi bỏ lỡ lượt, không bị trừ |
| E6 | Ghép Pinyin: chọn sai thẻ | Lượt của từ đó tính sai; hiện cặp đúng 800 ms rồi gỡ cả từ và thẻ đúng khỏi bảng |
| E7 | Game tô nét: client không có dữ liệu nét cho chữ server phát | Client gửi lượt đó là bỏ lỡ (không gửi, GM-5) và ghi log cảnh báo; server chỉ phát chữ có `stroke_count` (FR-001, FR-029) |
| E8 | Phi Đao: gõ thanh cho một số âm tiết, bỏ thanh âm tiết khác (`ni3hao`) | Client báo "Gõ đủ thanh cho mọi âm tiết hoặc bỏ hết thanh", **không gửi**, cho gõ lại trong thời gian rơi còn lại; nếu vẫn gửi (client cũ) → server chấm sai |
| E9 | Phi Đao: chữ một âm tiết nhiều cách đọc (行 xíng/háng) | Chỉ chấp nhận `pinyin` đã lưu của thẻ; nghĩa hiện kèm để định hướng cách đọc |
| E10 | Thần Bút/Phi Đao: người chơi hạ hết 60 chữ trước 60 giây | `GameShell` kết thúc ván ngay (spec 007 FR-029) |

---

## 3 · Functional Requirements

### Nhóm 1 — Mưa chữ `CHAR_RAIN` · tô nét (FR-001…FR-006)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `buildRound` SHALL lấy thẻ `CHAR` có `strokeCount` (`requireStrokeCount`), chọn **10** chữ làm đề (tối thiểu **5**, ít hơn → không khả dụng) |
| **FR-002** | Mỗi lượt `TRACE_CHAR`: `prompt = {text, pinyin, hint: meaningShort}`, không có `options`; `answer = "PASS"`; `rules.minDurationMs = strokeCount × 300` |
| **FR-003** | `isAvailable` SHALL đúng khi pool sinh được ≥ 5 chữ có `strokeCount` |
| **FR-004** | `uiConfig = {requiredStrokes: "ALL", strokeOrder: true}` — phải tô **mọi** nét theo đúng thứ tự |
| **FR-005** | Giao diện SHALL: khung tô (SVG `viewBox 0 0 1000 1000`), nét mẫu mờ, điểm tròn và mũi tên chỉ đầu/hướng nét kế tiếp; chấm từng nét bằng `gradeStroke` (ngưỡng `STROKE_THRESHOLDS` trong `cnhsk-game/src/engine/strokeScore.ts`); nét đạt → giữ nét, nét chưa đạt → hiện gợi ý tiếng Việt và viết lại nét đó; tô xong nét cuối → gửi `PASS` với `durationMs` từ lúc chữ hiện; số sao (1–3) theo độ chính xác chỉ hiển thị, **không** gửi server |
| **FR-006** | Dữ liệu nét lấy từ gói `hanzi-writer-data` đóng gói trong `cnhsk-game` (không tải CDN lúc chạy, không commit file dữ liệu sinh tay bị `.gitignore` chặn); dùng chuột, bút hoặc ngón tay (Pointer Events) |

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

### Nhóm 6 — Thần Bút `BRUSH_STROKE` (FR-029…FR-034)

| Mã | Yêu cầu |
|---|---|
| **FR-029** | `buildRound` SHALL lấy thẻ `CHAR` có `strokeCount`, chọn **60** chữ (tối thiểu **20**, ít hơn → không khả dụng) |
| **FR-030** | Mỗi lượt `TRACE_CHAR`: `prompt = {text, hint: meaningShort}`; `answer = "PASS"`; `rules.minDurationMs = 300` |
| **FR-031** | `uiConfig = {requiredStrokes: 1, fallStartMs: 6000, fallStepMs: 150, fallMinMs: 3000}` — chỉ tô **nét đầu tiên** của chữ; lượt thứ `i` (từ 0) rơi trong `max(fallMinMs, fallStartMs − i × fallStepMs)` ms (`fallDurationMs`, `cnhsk-game/src/engine/arcade.ts`, chế độ `time`) |
| **FR-032** | `isAvailable` SHALL đúng khi pool sinh được ≥ 20 chữ có `strokeCount` |
| **FR-033** | Giao diện SHALL: một chữ rơi tại một thời điểm (chỉ animate `transform`), khung tô bên dưới; tô đạt nét đầu (`gradeStroke`) → gửi `PASS`, chữ vỡ (200 ms), sang chữ sau; nét chưa đạt → gợi ý, tô lại khi chữ còn rơi; chạm đất → bỏ lỡ (GM-5), hiện chữ và pinyin 600 ms; hiện điểm, số chữ đã hạ, thời gian còn lại |
| **FR-034** | `prefers-reduced-motion`: chữ cố định, thanh thời gian ngang thay chuyển động rơi |

### Nhóm 7 — Phi Đao `FLYING_DART` (FR-035…FR-040)

| Mã | Yêu cầu |
|---|---|
| **FR-035** | `buildRound` SHALL lấy thẻ `WORD` và `CHAR` có `pinyin` (ưu tiên `WORD`), chọn **60** đề (tối thiểu **20**) |
| **FR-036** | Mỗi lượt `PINYIN_INPUT`: `prompt = {text, hint: meaningShort}`; `accept` = `[pinyin, pinyin bỏ dấu thanh]` — chấm bằng `PinyinGrader.matches` có sẵn (chấp nhận có dấu, số thanh, hoặc không thanh; `ü` = `v`/`u:`); `accept` **không** gửi client (GM-7); `rules.minDurationMs = 400 + 150 × số âm tiết` |
| **FR-037** | `uiConfig = {fallStartMs: 6000, fallStepMs: 150, fallMinMs: 3000, maxInputLength: 40}` — cùng công thức rơi FR-031 |
| **FR-038** | `isAvailable` SHALL đúng khi pool sinh được ≥ 20 đề có `pinyin` |
| **FR-039** | Giao diện SHALL: một từ rơi kèm nghĩa nhỏ; ô nhập luôn focus (bàn phím ảo mở trên điện thoại), Enter → kiểm định dạng cục bộ (E8) rồi gửi; đúng → dao bay và chữ vỡ, sang từ sau; sai → rung ô nhập, xoá chuỗi, cho gõ lại khi từ còn rơi (mỗi lần gửi là một câu trả lời — lần gửi đầu tiên của lượt được tính, spec 003 E-R5); chạm đất → bỏ lỡ, hiện pinyin đúng 600 ms |
| **FR-040** | Ô nhập SHALL có `autocomplete="off"`, `autocapitalize="off"`, `spellcheck=false`, nhãn truy cập "Gõ pinyin"; `prefers-reduced-motion` như FR-034 |

### Nhóm 8 — Xếp hạng (FR-041)

| Mã | Yêu cầu |
|---|---|
| **FR-041** | Mọi ván của 6 game SHALL lưu `attempts.kind='GAME'`, `game_code` = `GameCode.name()`, `score` = số lượt đúng do server chấm, `suspicious` theo GM-6 — đủ để 017 xếp hạng (spec 017 §0.2). 008 **không** gọi 017 và không thêm cột |

---

## 4 · Data Model

Không bảng mới, không migration. Bộ thủ đọc từ `lexemes.radical`, số nét từ `lexemes.stroke_count` (spec 002 §4.1). Lượt lưu theo spec 003 §0.3, ví dụ:

```json
{ "no": 3, "type": "PINYIN_PAIR", "skill": "VOCAB", "lexemeId": 42,
  "prompt": { "text": "朋友" },
  "options": [ {"key":"A","text":"lǎoshī"}, {"key":"B","text":"péngyou"}, {"key":"C","text":"xuésheng"},
               {"key":"D","text":"shíhou"}, {"key":"E","text":"yīfu"}, {"key":"F","text":"māma"} ],
  "answer": "B", "rules": { "minDurationMs": 600 } }
```

```json
{ "no": 7, "type": "TRACE_CHAR", "skill": "WRITING", "lexemeId": 118,
  "prompt": { "text": "水", "hint": "nước" },
  "answer": "PASS", "rules": { "minDurationMs": 300 } }
```

```json
{ "no": 12, "type": "PINYIN_INPUT", "skill": "VOCAB", "lexemeId": 77,
  "prompt": { "text": "学习", "hint": "học tập" },
  "accept": ["xuéxí", "xuexi"], "rules": { "minDurationMs": 700 } }
```

---

## 5 · Error Matrix

Không có mã lỗi mới — dùng spec 007 §5 và spec 003 §5. Game không đủ dữ liệu → `GAME_UNAVAILABLE`.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **BUS-09 / HR-06** | Ghi và chấm ở server từng lượt; game tô nét theo GM-8 |
| **AC-03** | Sinh lượt trong `community.game.*` từ `LexemeCard` (`ContentLookup`), không đọc bảng `learning` |
| V6 | React + CSS (transition/keyframes) + SVG cho khung tô, không canvas engine |
| Package | `community.game.rain`, `.pinyin`, `.radical`, `.catchgame` (`catch` là từ khoá Java), `.brush`, `.dart` — mỗi gói một `GameDefinition` + `…ItemBuilder` |
| Mã game | Chỉ dùng `learning.api.GameCode`; không tạo enum thứ hai (017 Q-T02-02) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `buildRound` | < 100 ms với pool 200 thẻ |
| `NFR-U01` | 60 fps hoạt ảnh trên điện thoại tầm trung (chỉ animate `transform`/`opacity`) | Kiểm tay |
| `NFR-U02` | Chơi được trên màn 360 × 640 không cuộn (Phi Đao: khi bàn phím ảo mở, chữ rơi vẫn thấy) | Kiểm tay |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Điểm tính ở client | Không — server tính theo GM-2; game tô nét chỉ gửi `PASS` (GM-8) |
| Game ngoài 6 mã `GameCode` | Không (H11) |

---

## 9 · Quyết định

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Luật cụ thể 6 game theo §3 | ✅ Leader duyệt 2026-11-14 |
| Q2 | 10 điểm/lượt đúng, **không** thưởng tốc độ hay chuỗi (giữ engine 007: `score` = số câu đúng) | ✅ Leader chốt 2026-11-14 |
| Q3 | Ghép Pinyin chỉ dùng từ ≥ 2 chữ | ✅ Chốt (BR-087-13) |
| Q4 | Bắt Chữ cho phép nhiễu cùng âm khác thanh | ✅ Chốt — mục tiêu luyện thanh điệu |
| Q5 | Danh sách bộ thủ dễ nhầm 10 cặp cố định | ✅ Chốt tạm — giáo viên bổ sung sau |
| Q6 | Game tô nét: client chấm nét, gửi `PASS`; server tự tính điểm, giữ trần, cờ nhanh bất thường (GM-8) — không chấm lại nét ở server | ✅ Leader chốt 2026-11-14 |
| Q7 | Mưa chữ = tô nét (theo app `cnhsk-game`), không phải chọn nghĩa | ✅ Leader chốt 2026-11-14 |
| Q8 | Thần Bút/Phi Đao chỉ chế độ tính giờ 60 s được lưu và xếp hạng; chế độ màn (stage) để sau vì cần API luyện không lưu — app ẩn chế độ màn trong bản này | ✅ Leader chốt 2026-11-14 |
| Q9 | Phi Đao chấp nhận pinyin không thanh (theo app); có thanh thì phải đủ mọi âm tiết | ✅ Chốt theo app — leader có thể bắt buộc thanh bằng cách bỏ phần tử không dấu trong `accept` |

---

## 10 · Móc nối cây tri thức

Mỗi lượt có `lexemeId` + `skill` → `ItemGradedEvent` đủ để cây cập nhật mastery với trọng số game (spec 007 §10).

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Game tô nét tin client báo `PASS` | Không có tiền thật; trần điểm theo số lượt phát; lượt nhanh hơn `minDurationMs` → ván `suspicious`, bị 017 loại khỏi bảng (LESSON-003 chỉ yêu cầu trần, thời gian tối thiểu, tần suất) |
| Thần Bút/Phi Đao phụ thuộc độ trễ mạng | Phản hồi lạc quan (FR-028); nhịp rơi ≥ 3 s |
| Dữ liệu bộ thủ/số nét thiếu → game không khả dụng | Hiện "Chưa đủ dữ liệu" (spec 007 FR-003); nhập thêm qua spec 002 |
| `ü` gõ thành `u` không phân biệt `lǜ`/`lù` khi bỏ thanh | Giới hạn đã biết của app; có thanh thì vẫn phân biệt qua `v` |

---

## 12 · Màn hình

SCR-036 Game Session (`screen-fields.md` dòng 18446) — 6 biến thể trong `GameShell`. Route `cnhsk-game` `/play/char-rain`, `/play/pinyin-match`, `/play/radical-match`, `/play/char-catch`, `/play/brush-stroke`, `/play/flying-dart` (`code` dạng kebab trên URL, đổi sang `GameCode` khi gọi API). Route cũ của app (`/arcade/brush`, `/arcade/dart`) chuyển về route mới.

---

## 13 · API Contract

Không có endpoint mới — [`../007-game-platform/contracts/openapi-games.yaml`](../007-game-platform/contracts/openapi-games.yaml) (007/T-03 cập nhật enum lên 6 mã).
`uiConfig` theo từng game: FR-004, FR-010, FR-023, FR-031, FR-037 (`RADICAL_MATCH`: `{}`).

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001, FR-002 | Pool 30 chữ có `strokeCount` · T 10 lượt `TRACE_CHAR`, `minDurationMs = strokeCount × 300`, JSON không có `answer` |
| AT-02 | FR-001, FR-003 | Pool 4 chữ có `strokeCount` · T không khả dụng; tạo ván → 422 |
| AT-03 | GM-8 | Ván Mưa chữ gửi 10 `PASS` với `durationMs` = 100 · T điểm 100, ván `suspicious=true` |
| AT-04 | FR-005 (FE) | Vitest: nét sai không chuyển nét; tô đủ nét → gọi `submitAnswer('PASS')` đúng một lần |
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
| AT-16 | GM-4 | T trong một ván không thẻ nào làm đề hai lần (cả 6 game) |
| AT-17 | FR-033, FR-034 (FE) | Vitest: chữ chạm đất → `onMissed`; tô đạt → `PASS`; reduced-motion hiện thanh thời gian |
| AT-18 | FR-012, FR-013 (FE) | Chọn từ rồi thẻ → gửi đúng khoá; thẻ đã gỡ không chọn được; xong bảng → bảng sau |
| AT-19 | FR-025, FR-028 (FE) | Hết 4 s → `onMissed`; phản hồi lạc quan rồi theo server |
| AT-20 | FR-029, FR-031 | Pool 80 chữ · T 60 lượt `TRACE_CHAR`; `fallDurationMs`: lượt 0 = 6000, lượt 20 = 3000, lượt 25 = 3000 |
| AT-21 | FR-036, SC-003 | T lượt Phi Đao không có `accept` trong response; `xuéxí`, `xue2xi2`, `xuexi` đúng; `xue2xi` và `xuéxì` sai |
| AT-22 | FR-039, E8 (FE) | Vitest: `ni3hao` không gửi, hiện thông báo; gửi sai → xoá ô, vẫn gõ lại được khi từ còn rơi |
| AT-23 | FR-041, SC-004 | Mỗi game một ván `SUBMITTED` · T truy vấn bảng game của 017 (`LeaderboardRepository.gameRanking`) thấy đúng `game_code` và điểm |
| AT-24 | §0.2 | T `GameDefinition` của 6 game có `timeLimitSeconds`, số lượt, `skill` đúng bảng §0.2 |

---

## 15 · Gói việc

[`tasks.md`](tasks.md)

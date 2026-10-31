# Feature Specification: Nhận diện và viết chữ Hán

**Feature:** `006-hanzi-recognition-writing`
**Feature tree:** 1.1 Luyện viết · 1.2 Nhận diện chữ · **Module:** `learning` (package `learning.hanzi`)
**Use case:** UC-015 (theo nét) · UC-016 (nhớ rồi viết) · UC-017 (thử thách) · UC-018 (nghe chép) · UC-019 (nhìn chữ chọn nghĩa) · UC-020 (nghe chọn chữ)
**Actor:** `LEARNER` · **Màn hình:** SCR-014 Character Writing · SCR-015 Character Recognition
**Scope:** MVP · **Priority:** P1 (nhận diện) · P2 (viết)

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — hợp đồng API đã duyệt, được code |
| **Approved by** | Leader (2026-10-30) |
| **Locked on** | 2026-10-30 — đổi hợp đồng phải sửa OpenAPI + spec trước, leader duyệt lại |
| **Phụ thuộc** | Spec 001 · Spec 002 (`LEXEME` kind `CHAR`: `stroke_count`, `radical`, `audio_url`) · Spec 003 §0 |
| **Quyết định chung** | Brainstorm 2026-10-19: D-01, D-03, D-04, D-08, D-12 |

> **Codex đọc:** `AGENTS.md` → spec 003 §0 → file này → `contracts/openapi-hanzi.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Nhận diện: nhìn chữ chọn nghĩa, nghe chọn chữ — chấm ở server | Mastery, trọng số mastery từ luyện viết (`BUS-16` chưa phê chuẩn) |
| Viết 4 chế độ bằng `hanzi-writer` (client chấm hình học), server kiểm hợp lý và **lưu bằng chứng** | Server chấm toạ độ nét (BR-015-2) |
| Thử thách 10 chữ, 20 giây/chữ, tối đa 3 lỗi | Lượt free / tính phí luyện viết (UC-126 chưa định nghĩa — không tự đặt) |
| Danh sách, tìm chữ để luyện | Dữ liệu nét lưu trong DB (`stroke_data`) — dùng bộ dữ liệu của `hanzi-writer` |

**Vì sao gỡ được chặn `BUS-16`:** `BUS-16` chặn việc *tính mastery* từ kết quả do client chấm. Spec này **không** tính mastery:
chỉ lưu kết quả kèm hệ số đề xuất của UC trong `meta`. Khi `BUS-16` được phê chuẩn, cây tri thức đọc lại `meta` (D-08).

**Nguồn lượt mới:** `CHAR_RECOGNITION` (`PRACTICE`, `IMMEDIATE`, 2 giờ, `allowSkip=true`) · `CHAR_WRITING` (`PRACTICE`, `RECORD`, 30 phút) ·
`WRITING_CHALLENGE` (`WRITING_CHALLENGE`, `DEFERRED`, hạn = `served_at + 200 s + 10 s`).
**Loại câu mới:** `CHAR_MEANING_CHOICE` (`CHARACTER`) · `AUDIO_CHAR_CHOICE` (`LISTENING`) · `WRITING_RESULT` (`gradable=false`, `WRITING`).

**Thư viện FE mới (cần duyệt, D-12):** `hanzi-writer@3.7.3`. Dữ liệu nét tải từ `https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/{char}.json`
qua `charDataLoader` tự viết (ghim phiên bản). **Không thay đổi DB.**

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-01** | Người Việt nhớ mặt chữ | Luyện viết đủ 4 mức hỗ trợ; nhận diện có nhiễu cùng bộ thủ |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Response nhận diện không chứa đáp án | 100% |
| **SC-002** | Gửi kết quả viết phi lý (accuracy 1.5, 12 nét trong 0.5 s, xem mẫu 3 lần) bị từ chối | 100% |
| **SC-003** | Nộp thử thách 2 lần / quá hạn / chữ lạ bị từ chối | 100% |

---

## 2 · User Stories

### US-1 · Nhìn chữ chọn nghĩa (P1)
**Given** tôi chọn HSK 1, chế độ "Nhìn chữ chọn nghĩa"
**When** tôi trả lời 10 câu
**Then** mỗi câu biết đúng/sai + nghĩa đúng ngay; nhiễu ưu tiên chữ cùng bộ thủ

### US-2 · Viết theo nét rồi nhớ rồi viết (P2)
**Given** chữ 好 có dữ liệu nét
**When** tôi viết theo nét xong
**Then** chế độ "Nhớ rồi viết" của 好 mở; trước đó gọi thẳng bị từ chối

### US-3 · Thử thách (P2)
**Given** tôi đã viết theo nét xong ≥ 10 chữ
**When** tôi bắt đầu thử thách
**Then** server chọn 10 chữ, tôi có 200 giây, sai 3 chữ thì kết thúc; kết quả chỉ tính chữ đã viết xong

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Chữ không có `stroke_count` | Không mở chế độ viết (`STROKE_DATA_MISSING`); vẫn xem/nhận diện được |
| E2 | `hanzi-writer` không tải được dữ liệu nét của chữ | Frontend hiện "Chữ này chưa có dữ liệu nét", chuyển chế độ xem chữ (UC-015 A3), **không** gửi kết quả |
| E3 | Viết cùng chữ cùng chế độ hai lần trong 10 phút | Lần 2 vẫn lưu, `meta.repeatWithin10Min = true` (BR-015-7) |
| E4 | Thoát giữa chừng | Gửi `completed=false` — lưu buổi học, `counted=false` (BR-015-6) |
| E5 | Audio lỗi ở nghe chọn chữ | "Không nghe được" → không tính sai (BR-020-4) |
| E6 | Nghe chép mà chữ không có audio | **422** `AUDIO_NOT_AVAILABLE`, nút ẩn |
| E7 | Hết giờ thử thách khi đang viết chữ thứ 6 | Client tự nộp 5 chữ đã xong + chữ 6 `completed=false` |
| E8 | Mất mạng, nộp thử thách sau hạn | **422** `SESSION_EXPIRED` (= `CHALLENGE_EXPIRED` của UC-017) |

---

## 3 · Functional Requirements

### Nhóm 1 — Tra chữ (FR-001…FR-003)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `GET /api/characters?hskLevel=&q=&page=&size=` SHALL trả chữ (`kind='CHAR'`) phân trang (`size` 1–50, mặc định 20), lọc cấp 1–9, `q` (1–20 ký tự) khớp `text` chính xác **hoặc** tiền tố `pinyin_norm` (sau khi chuẩn hoá `q` như spec 002 FR-031); sắp `hsk_level`, `frequency DESC`, `id`. Mỗi phần tử: `{id, text, pinyin, meaning, hskLevel, strokeCount, hasAudio}` |
| **FR-002** | `GET /api/characters/{id}` SHALL trả `{id, text, pinyin, sinoViet, meaning, hskLevel, radical, strokeCount, audioUrl, writing:{strokeAvailable, recallUnlocked, dictationAvailable, lastPracticedAt}}`; không phải `CHAR` → **404** `CHARACTER_NOT_FOUND` |
| **FR-003** | `strokeAvailable = strokeCount ≠ null`; `recallUnlocked` = có `WRITING_RESULT` `mode=STROKE`, `completed=true` của người gọi cho chữ này; `dictationAvailable = strokeAvailable ∧ audioUrl ≠ null` |

### Nhóm 2 — Nhận diện (FR-004…FR-009) · UC-019, UC-020, SCR-015

| Mã | Yêu cầu |
|---|---|
| **FR-004** | `POST /api/characters/recognition-sessions` `{mode, hskLevel, count}` (`mode ∈ {CHAR_TO_MEANING, AUDIO_TO_CHAR}`, `hskLevel` 1–9, `count` 5–20 mặc định 10) SHALL tạo lượt `CHAR_RECOGNITION` (`ref_id = NULL`) |
| **FR-005** | Chọn chữ: chữ `CHAR` cùng cấp (AUDIO_TO_CHAR: chỉ chữ có `audio_url`), ưu tiên chữ người gọi **trả lời sai gần nhất**, rồi chữ **chưa từng gặp** trong `CHAR_RECOGNITION`, rồi ngẫu nhiên |
| **FR-006** | `CHAR_TO_MEANING` → câu `CHAR_MEANING_CHOICE`: `prompt.text` = chữ, 4 phương án nghĩa. Nhiễu: tầng 0 = **cùng `radical`** cùng cấp (BR-019-4), rồi theo spec 003 G-1 (không có chủ đề), G-2, G-5 |
| **FR-007** | `AUDIO_TO_CHAR` → câu `AUDIO_CHAR_CHOICE`: `prompt.audioUrl`, 4 phương án là chữ; G-3 loại chữ **cùng pinyin kèm thanh** (BR-020-3); `rules = {hasAudio:true, maxReplays:3, minDurationMs:800}` |
| **FR-008** | Không chọn được chữ nào (hoặc G-4 loại hết) → **200** `{session:null, reason:"NO_CHARACTERS"}` (UC-019 A2 — không phải lỗi) |
| **FR-009** | Trả lời/kết thúc theo bộ máy spec 003 (`IMMEDIATE`): bỏ qua không tính (UC-019 A1), quá nhanh → `suspicious` (A3), nghe lại > 3 → 400 |

### Nhóm 3 — Ghi kết quả viết một chữ (FR-010…FR-019) · UC-015, UC-016, UC-018

| Mã | Yêu cầu |
|---|---|
| **FR-010** | `POST /api/writing/attempts` SHALL nhận `{lexemeId, mode, completed, accuracy, strokeMisses, durationMs, usedAnimation, previewCount, previewMs, replayCount}`; `mode ∈ {STROKE, RECALL, DICTATION}` |
| **FR-011** | Kiểm chung: chữ tồn tại (**404** `CHARACTER_NOT_FOUND`); `strokeCount ≠ null` (**422** `STROKE_DATA_MISSING`); `accuracy` 0…1 (**400** `INVALID_ACCURACY`); `strokeMisses` 0…`strokeCount*10`; `durationMs` 0…3 600 000 (**422** `VALIDATION_FAILED`) |
| **FR-012** | WHEN `completed = true` và `durationMs < strokeCount × 200` (DICTATION: `< max(strokeCount × 200, 1500)`), THE system SHALL trả **400** `IMPOSSIBLE_DURATION`, **không ghi** gì, log `WARN` (`userId`, `lexemeId`, không log nội dung khác) |
| **FR-013** | `RECALL`: chưa `recallUnlocked` → **403** `MODE_LOCKED`; `usedAnimation=true` → **400** `HINT_USED_IN_RECALL`; `previewCount > 2` hoặc `previewMs > 11 000` → **400** `PREVIEW_TOO_LONG` |
| **FR-014** | `DICTATION`: chữ không có audio → **422** `AUDIO_NOT_AVAILABLE`; `replayCount > 3` → **400** `TOO_MANY_REPLAYS`; `usedAnimation=true` → **400** `CHARACTER_REVEALED` |
| **FR-015** | `STROKE`: `previewCount`, `replayCount` phải 0 hoặc vắng (**422** `VALIDATION_FAILED`) |
| **FR-016** | Hợp lệ → THE system SHALL trong **một transaction**: tạo lượt `CHAR_WRITING` (`ref_id = lexemeId`) có một câu `WRITING_RESULT` ghi sẵn (`answer = 'COMPLETED'`/`'INCOMPLETE'`, `duration_ms`, `meta` FR-017), rồi kết thúc lượt — dùng `PracticeService` (spec 003 FR-002 `preRecorded`) |
| **FR-017** | `meta` SHALL gồm `{mode, completed, accuracy, strokeMisses, strokeCount, usedAnimation, previewCount, previewMs, replayCount, repeatWithin10Min, coefficientProposal}`. `coefficientProposal` (đề xuất UC, chưa phê chuẩn): `STROKE` 0.5 (×0.5 nếu `usedAnimation`) · `RECALL` 1.0 − 0.25×`previewCount` · `DICTATION` 1.2 − 0.2×max(0, `replayCount` − 1); `completed=false` → 0 |
| **FR-018** | `repeatWithin10Min = true` khi đã có `WRITING_RESULT` `completed=true` cùng chữ, cùng `mode`, của người gọi trong 10 phút trước (E3) |
| **FR-019** | Response **201** `{attemptId, recorded:true, counted, recallUnlocked}`; `counted = completed ∧ ¬repeatWithin10Min`; ≤ **300** lần ghi/giờ mỗi người (Redis `writing:{userId}:{yyyyMMddHH}`) → **429** `RATE_LIMITED` |

`CHAR_WRITING` chỉ tính giới hạn FR-019; không tính practice:start (E-R12).

### Nhóm 4 — Thử thách (FR-020…FR-027) · UC-017

| Mã | Yêu cầu |
|---|---|
| **FR-020** | `POST /api/writing/challenges` SHALL tính tập chữ đủ điều kiện = chữ có `WRITING_RESULT` `mode=STROKE`, `completed=true` của người gọi **và** `strokeCount ≠ null`; < 10 chữ → **422** `NOT_ENOUGH_CHARACTERS` kèm `have`, `need=10` |
| **FR-021** | Server chọn **10** chữ (BR-017-2), ưu tiên chữ luyện viết **lâu nhất chưa luyện lại**, hoà thì ngẫu nhiên; tạo lượt `WRITING_CHALLENGE` với 10 câu `WRITING_RESULT` (mỗi câu `prompt = {text, pinyin, meaning, strokeCount}`), `expires_at = served_at + 210 s` |
| **FR-022** | Response **201** = `SessionView` + `meta = {timeLimitSeconds: 200, maxMistakes: 3, perCharSeconds: 20}` |
| **FR-023** | WHEN có lượt thử thách `IN_PROGRESS` chưa hết hạn, THE system SHALL trả lại lượt đó (`200`) |
| **FR-024** | Nộp: `POST /api/practice/sessions/{id}/finish` `{answers:[{no, answer: 'COMPLETED'|'FAILED'|'INCOMPLETE', durationMs, clientMeta:{accuracy, strokeMisses}}]}`. Bộ máy kiểm E-R1…E-R4 (chữ lạ → `ITEM_NOT_IN_SESSION` = `CHARACTER_NOT_IN_CHALLENGE`), nộp lại → **409** (`CHALLENGE_ALREADY_SUBMITTED`), quá hạn → **422** `SESSION_EXPIRED` |
| **FR-025** | `WritingChallengeHandler.onFinish` SHALL kiểm: `answers` không có `no` trùng (**400** `RESULT_COUNT_MISMATCH`); `FAILED` ≤ 3; mọi `COMPLETED` có `durationMs ≥ strokeCount × 200` — vi phạm → **400** `IMPOSSIBLE_DURATION` (rollback, lượt vẫn mở tới hạn) |
| **FR-026** | Câu không có trong `answers` được bộ máy ghi `answer = NULL`, `meta.missing = true` (spec 003 E-F3) — tính là chưa hoàn thành (BR-017-7). `WritingChallengeHandler` **trước khi** bộ máy ghi (hook `enrichMeta`) bổ sung `meta` như FR-017 với `mode='CHALLENGE'`, `coefficientProposal` = 1.2 khi `COMPLETED`, 0 khi khác |
| **FR-027** | `extra = {completedChars, failedChars, incompleteChars, total:10, finishedReason}`; `finishedReason` = `MAX_MISTAKES` nếu `failedChars = 3`, `ALL_DONE` nếu `completedChars + failedChars = 10`, còn lại `TIME_UP` |

### Nhóm 5 — Giao diện (FR-028…FR-034)

| Mã | Yêu cầu |
|---|---|
| **FR-028** | SCR-015: chọn chế độ + cấp HSK, câu hỏi (chữ to hoặc nút nghe), 4 lựa chọn, phản hồi + nghĩa đúng, "Bỏ qua", "Không nghe được" (chỉ chế độ nghe), "Câu tiếp", tổng kết — dùng component chung spec 003 T-13 |
| **FR-029** | SCR-014 trang chọn chữ: tìm theo chữ/pinyin, lọc cấp, danh sách FR-001; bấm chữ → trang luyện với 4 nút chế độ; chế độ không dùng được bị khoá kèm lý do ("Viết theo nét trước", "Chữ chưa có âm thanh", "Chữ chưa có dữ liệu nét") |
| **FR-030** | Component `HanziCanvas` bọc `hanzi-writer`: `charDataLoader` ghim `hanzi-writer-data@2.0.1`; lỗi tải → sự kiện `onDataError` (E2); kích thước khung 300×300 px (co giãn theo màn hình nhỏ, tối thiểu 240) |
| **FR-031** | `STROKE`: `showOutline:true`, `showCharacter:false`, `quiz({showHintAfterMisses:1})`; nút "Xem lại nét" → `animateCharacter()` và đặt `usedAnimation=true`; `accuracy = max(0, 1 − totalMistakes/strokeCount)` (làm tròn 2 chữ số) |
| **FR-032** | `RECALL`: hiện chữ mẫu 5 s (đếm ngược), rồi ẩn chữ và viền, `quiz({showHintAfterMisses:false})`; nút "Xem mẫu thêm" (tối đa 2 lần × 3 s, cộng `previewMs`); nút "Chưa nhớ — về viết theo nét" (không gửi kết quả RECALL, UC-016 A2) |
| **FR-033** | `DICTATION`: không hiện chữ/viền, nút nghe (tối đa 3), "Không nghe được" → hiện chữ và chuyển sang `STROKE` (không gửi DICTATION, BR-018-7); sau khi nộp hiện chữ đúng + nghe lại (UC-018 A3) |
| **FR-034** | Thử thách: đồng hồ 200 s, chữ hiện pinyin + nghĩa (không hiện mẫu), `quiz` không gợi ý; một chữ **thất bại** khi `hanzi-writer` báo ≥ 3 lần sai ở chữ đó → `mistakes + 1`, sang chữ kế; dừng khi xong 10 chữ, hết giờ (tự nộp) hoặc `mistakes = 3`; mất mạng → giữ kết quả trong `sessionStorage` và gửi lại (UC-017 A3) |

---

## 4 · Data Model

Đọc `learning.lexemes` (`kind='CHAR'`). Ghi qua bộ máy spec 003. **Không thay đổi DB.**
Truy vấn `recallUnlocked` / tập đủ điều kiện thử thách dùng `attempt_items` `item_type='WRITING_RESULT'` + `meta->>'mode'`.

> `AC-09` nói JSONB không dùng cho truy vấn điều kiện. Ở đây lọc `meta->>'mode'` **sau khi** đã lọc bằng cột thật (`user_id`, `ref_type`, `item_type`, `lexeme_id`) — tập nhỏ.
> Nếu nhóm muốn tuyệt đối không đọc JSONB có điều kiện: thay `ref_type` bằng 3 giá trị `CHAR_WRITING_STROKE/RECALL/DICTATION`. **Chốt:** giữ một `CHAR_WRITING` + `meta.mode` (Q4).

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `CHARACTER_NOT_FOUND` | 404 | id không phải `CHAR` | FR-002, FR-011 |
| `STROKE_DATA_MISSING` | 422 | Chữ không có `stroke_count` | FR-011 |
| `INVALID_ACCURACY` | 400 | `accuracy` ngoài 0…1 | FR-011 |
| `IMPOSSIBLE_DURATION` | 400 | Viết nhanh phi lý | FR-012, FR-025 |
| `MODE_LOCKED` | 403 | RECALL khi chưa viết theo nét | FR-013 |
| `HINT_USED_IN_RECALL` | 400 | RECALL báo dùng gợi ý | FR-013 |
| `PREVIEW_TOO_LONG` | 400 | Xem mẫu quá giới hạn | FR-013 |
| `AUDIO_NOT_AVAILABLE` | 422 | Nghe chép chữ không có audio | FR-014 |
| `TOO_MANY_REPLAYS` | 400 | Nghe lại > 3 | FR-014, FR-009 |
| `CHARACTER_REVEALED` | 400 | Nghe chép báo đã hiện chữ | FR-014 |
| `NOT_ENOUGH_CHARACTERS` | 422 | < 10 chữ đủ điều kiện | FR-020 |
| `RESULT_COUNT_MISMATCH` | 400 | Kết quả thử thách trùng/thừa | FR-025 |
| `RATE_LIMITED` | 429 | > 300 lần ghi/giờ | FR-019 |
| `VALIDATION_FAILED` | 422 | Tham số sai | FR-001, FR-004, FR-011, FR-015 |
| Lỗi bộ máy | | Spec 003 §5 | |

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **BUS-09** | Nhận diện chấm ở server. Viết: server **không chấm**, chỉ kiểm hợp lý + lưu; không có điểm/mastery từ viết |
| **BUS-16** | Không bị vi phạm (không tính mastery) — giữ trạng thái "chưa phê chuẩn" |
| `AGENTS.md` §3 | `hanzi-writer` là thư viện mới → duyệt (Q1) |
| Package | `learning.hanzi` (`CharacterQueryService`, `RecognitionService`, `WritingService`, `WritingChallengeService`, `WritingChallengeHandler`, `web.*`) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `GET /api/characters` | < 200 ms p95 |
| `NFR-P02` | `POST /api/writing/attempts` | < 150 ms p95 |
| `NFR-U01` | `HanziCanvas` dùng được bằng ngón tay trên màn 360 px | Kiểm tay trên điện thoại thật |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Mastery/FSRS | Không |
| `questions`, `question_options` | Không — sinh từ `lexemes` (D-01) |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Thêm `hanzi-writer@3.7.3`, dữ liệu nét `hanzi-writer-data@2.0.1` từ jsDelivr | **Chốt** (leader 2026-10-30) — ghim đúng phiên bản; jsDelivr lỗi thì chuyển chế độ xem (E2) |
| Q2 | `IMPOSSIBLE_DURATION` từ chối không ghi (theo UC-015 T3) | Chốt |
| Q3 | Nghe chép: thời gian tối thiểu `max(nét×200, 1500 ms)` vì DB không lưu độ dài audio | Chốt tạm — thêm `audio_duration_ms` khi nhập nếu nhóm muốn chặt hơn |
| Q4 | Một nguồn `CHAR_WRITING` + `meta.mode` | Chốt (§4) |
| Q5 | Thử thách: chữ thất bại khi sai ≥ 3 nét ở chữ đó | Chốt — UC-017 không định nghĩa "sai" |
| Q6 | Nhận diện không gắn chủ đề, chọn theo cấp HSK | Chốt — chốt open issue SCR-015 (đường dẫn `/api/characters/recognition-sessions`) |

---

## 10 · Móc nối cây tri thức

| Chỗ | Bây giờ | Khi có cây |
|---|---|---|
| `meta.coefficientProposal` | Lưu | Khi `BUS-16` phê chuẩn: listener `ItemGradedEvent` (`WRITING_RESULT`) dùng hệ số × độ tin cậy |
| Thử thách chọn chữ | Lâu chưa luyện nhất | Ưu tiên chữ gần hạn ôn / yếu (BR-017-3) |
| Nhận diện | Ưu tiên chữ sai gần nhất | Ưu tiên theo mastery |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Nghe chép: dữ liệu nét (lộ chữ đúng) nằm trong response (UC-018) | Không tránh được khi chấm ở client; không tính mastery |
| jsDelivr không truy cập được | Chữ rơi về chế độ xem (E2). Phương án dự phòng: copy file JSON cần dùng vào `cnhsk-web/public/hanzi-data/` và đổi URL — một dòng cấu hình `VITE_HANZI_DATA_URL` |
| `stroke_count` trong DB lệch với dữ liệu `hanzi-writer` | Server dùng `stroke_count` DB cho luật thời gian; lệch chỉ làm ngưỡng lỏng/chặt chút |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-015 Character Recognition | `/learn/characters/recognition` | `LEARNER` | `POST /api/characters/recognition-sessions` + bộ máy | `screen-fields.md` dòng 7143 |
| SCR-014 Character Writing | `/learn/characters/writing` (chọn chữ) · `/learn/characters/writing/:lexemeId` (luyện) · `/learn/characters/writing/challenge` | `LEARNER` | FR-001, FR-002, FR-010, FR-020 | dòng 6286 |

| Open issue | Chốt |
|---|---|
| SCR-014: `BUS-16` chặn mastery | Không tính mastery, lưu `meta` (D-08); ô "Kết quả ghi nhớ" hiện "Đã ghi nhận" / "Chưa tính (luyện lại trong 10 phút)" |
| SCR-014: chế độ cũ (xem mẫu/tô theo/viết tự do) lệch 4 chế độ UC | Theo 4 chế độ UC |
| SCR-014: UC-126 hạn mức | Không có hạn mức |
| SCR-015: đường dẫn API lệch | `/api/characters/recognition-sessions` + `/api/practice/sessions/**` |

---

## 13 · API Contract

[`contracts/openapi-hanzi.yaml`](contracts/openapi-hanzi.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/characters` | 200 `CharacterPage` | 422 |
| `GET /api/characters/{id}` | 200 `CharacterDetail` | 404 |
| `POST /api/characters/recognition-sessions` | 201 `SessionView` · 200 `{session:null}` | 422, 429 |
| `POST /api/writing/attempts` | 201 `WritingAttemptResult` | 400, 403, 404, 422, 429 |
| `POST /api/writing/challenges` | 201 mới · 200 tiếp tục | 422, 429 |
| Nộp thử thách | `POST /api/practice/sessions/{id}/finish` (spec 003) | 400, 409, 422 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | `q=hao` · T có 好; `q=好` · T đúng 1; `size=51` · T 422 |
| AT-02 | FR-002, FR-003 | Chữ không `stroke_count` · T `strokeAvailable=false`; sau khi viết STROKE xong · T `recallUnlocked=true` |
| AT-03 | FR-004, FR-006, SC-001 | T không có `answer`; với chữ có bộ thủ 氵, ≥ 1 nhiễu cùng bộ khi cấp có đủ |
| AT-04 | FR-005 | G sai chữ X lượt trước · T X có trong lượt mới |
| AT-05 | FR-007 | G 是, 事 cùng cấp · W 100 lượt nghe · T không câu nào có cả hai |
| AT-06 | FR-008 | Cấp 9 chưa có chữ · T 200 `session=null` |
| AT-07 | FR-009, E5 | `audioFailed` · T không tính sai |
| AT-08 | FR-011 | `accuracy=1.5` · T 400; chữ không `stroke_count` · T 422 |
| AT-09 | FR-012 | 12 nét, `durationMs=500`, `completed=true` · T 400, không có dòng `attempts` mới |
| AT-10 | FR-013 | RECALL chưa STROKE · T 403; `usedAnimation` · T 400; `previewCount=3` · T 400 |
| AT-11 | FR-014, E6 | DICTATION chữ không audio · T 422; `replayCount=4` · T 400 |
| AT-12 | FR-016, FR-017 | STROKE hợp lệ · T 1 lượt `SUBMITTED`, 1 `attempt_items` `WRITING_RESULT`, `meta.coefficientProposal=0.5`; `usedAnimation` · T 0.25 |
| AT-13 | FR-017 | RECALL `previewCount=2` · T 0.5; DICTATION `replayCount=3` · T 0.8 |
| AT-14 | FR-018, E3 | Viết lại sau 3 phút · T `repeatWithin10Min=true`, `counted=false` |
| AT-15 | FR-019, E4 | `completed=false` · T ghi, `counted=false`, `coefficientProposal=0` |
| AT-16 | FR-019 | Lần ghi thứ 301 trong giờ · T 429 |
| AT-17 | FR-020 | 5 chữ đủ điều kiện · T 422 `have=5, need=10` |
| AT-18 | FR-021, FR-022 | T 10 chữ khác nhau, đều thuộc tập đủ điều kiện, `timeLimitSeconds=200` |
| AT-19 | FR-024 | Nộp 2 lần · T 409; nộp sau 211 s (đồng hồ giả) · T 422; `no=11` · T 400 |
| AT-20 | FR-025 | `no` trùng · T 400 `RESULT_COUNT_MISMATCH`; 4 `FAILED` · T 400; chữ 12 nét `COMPLETED` trong 1 s · T 400, lượt vẫn `IN_PROGRESS` |
| AT-21 | FR-026, FR-027, E7 | 5 xong, 1 dở, không gửi 4 · T 5 `COMPLETED`, 5 `INCOMPLETE`, `finishedReason=TIME_UP` |
| AT-22 | FR-027 | 3 `FAILED` · T `MAX_MISTAKES` |
| AT-23 | FR-028…FR-034 (FE) | Vitest với `hanzi-writer` giả: STROKE tính `accuracy` đúng; RECALL ẩn mẫu sau 5 s, tối đa 2 lần xem thêm; DICTATION "Không nghe được" chuyển STROKE và không gửi; thử thách dừng ở `mistakes=3`, tự nộp khi hết giờ; lỗi tải dữ liệu nét → chế độ xem |

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)

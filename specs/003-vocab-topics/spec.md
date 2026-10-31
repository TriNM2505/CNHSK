# Feature Specification: Từ vựng theo chủ đề + bộ máy lượt luyện

**Feature:** `003-vocab-topics`
**Feature tree:** 1.5 Từ vựng theo chủ đề · **Module:** `learning` (package `learning.practice`, `learning.vocab`)
**Use case:** UC-025 → UC-029 · **Actor:** `LEARNER`
**Màn hình:** SCR-011 Topic List · SCR-012 Topic Study
**Scope:** MVP · **Priority:** P0

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — hợp đồng API đã duyệt, được code |
| **Approved by** | Leader (2026-10-30) |
| **Locked on** | 2026-10-30 — đổi hợp đồng phải sửa OpenAPI + spec trước, leader duyệt lại |
| **Phụ thuộc** | Spec 001 (đăng nhập, lỗi, CSRF) · Spec 002 (dữ liệu `lexemes`, `topics`, `topic_items`; `PinyinUtils`) |
| **Spec dùng lại phần §0** | 004, 005, 006, 007, 008 |
| **Quyết định chung** | Brainstorm 2026-10-19: D-01, D-03, D-04, D-05, D-06, D-11 |

> **Codex đọc theo thứ tự:** `AGENTS.md` → spec 001 §0 → spec 002 §4 → file này → `contracts/openapi-practice-vocab.yaml` → `tasks.md`.

---

## 0 · Bộ máy lượt luyện (dùng chung cho 003→008)

Mọi chế độ học và game đều là **một lượt** (`learning.attempts`) gồm **các câu** đã phát (`served_items`).
Viết luật chống gian lận **một lần** ở đây; spec sau chỉ khai báo câu và luật riêng.

### 0.1 · Khái niệm

| Khái niệm | Lưu ở | Ghi chú |
|---|---|---|
| **Lượt** (session) | 1 dòng `attempts` | `kind`, `ref_type` = nguồn (§0.2), `ref_id`, `served_at`, `expires_at`, `status` |
| **Câu đã phát** (served item) | `attempts.served_items` JSONB | Ghi **một lần** khi tạo lượt, **có đáp án**; chỉ server đọc |
| **Câu trả lời** | 1 dòng `attempt_items` / câu | Ghi khi chấm; `order_index` = số câu (`no`) |
| **Bản nháp** (chế độ nộp cả bài) | `attempts.draft_answers` JSONB | Ghi đè được tới khi nộp |
| **Nhật ký học** | `study_events` | 1 dòng khi kết thúc lượt |

### 0.2 · Nguồn lượt (`PracticeSource`, lưu ở `attempts.ref_type`)

| `ref_type` | `attempts.kind` | `ref_id` | Kiểu phản hồi | Hạn | Spec |
|---|---|---|---|---|---|
| `TOPIC_STUDY` | `PRACTICE` | topic id | `RECORD` | 2 giờ | 003 |
| `TOPIC_PRACTICE` | `PRACTICE` | topic id | `IMMEDIATE` | 2 giờ | 003 |
| `TOPIC_TEST` | `TOPIC_TEST` | topic id | `DEFERRED` | 24 giờ | 003 |
| `GRAMMAR_LESSON` | `PRACTICE` | lexeme id (GRAMMAR) | `IMMEDIATE` | 2 giờ | 004 |
| `PRON_STAGE` | `PRACTICE` | `pron_stages.id` | `IMMEDIATE` | 7 ngày | 005 |
| `CHAR_RECOGNITION` | `PRACTICE` | — | `IMMEDIATE` | 2 giờ | 006 |
| `CHAR_WRITING` | `PRACTICE` | lexeme id (CHAR) | `RECORD` | 30 phút | 006 |
| `WRITING_CHALLENGE` | `WRITING_CHALLENGE` | — | `DEFERRED` | theo luật 006 | 006 |
| `GAME_ROUND` | `GAME` | — (`game_code` có cột riêng) | `IMMEDIATE` | theo game | 007/008 |

Java: `enum PracticeSource { …; AttemptKind kind(); FeedbackMode feedback(); Duration ttl(); boolean allowSkip(); boolean maxScoreFromServed(); }` — spec sau **thêm** giá trị, không sửa giá trị cũ.
`allowSkip`: `true` cho mọi nguồn 003, 004, 006; `false` cho `PRON_STAGE` (005) và `GAME_ROUND` (007).

**Ba kiểu phản hồi:**

| Kiểu | Trả lời một câu | Kết thúc |
|---|---|---|
| `IMMEDIATE` | Chấm ngay, ghi `attempt_items`, trả đúng/sai + đáp án đúng + giải thích. Mỗi câu **một lần** | Tính điểm từ `attempt_items` |
| `DEFERRED` | Lưu vào `draft_answers`, trả `{accepted:true}`; **được đổi** đáp án tới khi nộp. Không trả đúng/sai | Chấm toàn bộ, ghi `attempt_items`, câu chưa trả lời = sai |
| `RECORD` | Ghi `attempt_items` với `is_correct = NULL` (không có đúng/sai), mỗi câu một lần | Không có điểm (`score = NULL`) |

### 0.3 · Câu đã phát — cấu trúc `served_items`

`served_items = { "meta": { … cấu hình lượt, không có đáp án … }, "items": [ câu, câu, … ] }`. Mỗi câu:

```json
{ "no": 1, "type": "MEANING_CHOICE", "skill": "VOCAB", "lexemeId": 123,
  "prompt": { "text": "我们", "pinyin": null, "audioUrl": null, "sentence": null, "hint": null },
  "options": [ { "key": "A", "text": "chúng tôi" }, { "key": "B", "text": "họ" } ],
  "answer": "A", "accept": null, "explanation": null,
  "rules": { "minDurationMs": 800, "maxReplays": 3, "hasAudio": false } }
```

- `answer` (khoá phương án) và `accept` (danh sách đáp án gõ chấp nhận) **không bao giờ** ra client trước khi câu được chấm. DTO gửi client là record riêng `ServedItemView` **không có hai trường này** (test assert).
- **Sửa 2026-10-30 (leader duyệt, Q-T04-01/03):** `items` và `meta` không chứa đáp án server; `answered[].answer` được chứa câu trả lời của chính người học để khôi phục, kể cả `preRecorded` và tiếp tục lượt. `correctAnswer` chỉ trả cho câu đã ghi/chấm khi feedback là `IMMEDIATE`/`RECORD`. Response answer và GET session của `DEFERRED` trước `finish` không có field `correct`, `correctAnswer`, `accept`, `explanation`.
- `type` thuộc `ItemType` (§0.4); `skill` thuộc `Skill` (§0.5).

### 0.4 · Loại câu (`ItemType`, lưu `attempt_items.item_type`)

| `type` | Chấm | Đáp án client gửi |
|---|---|---|
| `STUDY_CARD` | Không chấm (`RECORD`) | `LEARNED` hoặc `KNOWN` |
| `MEANING_CHOICE` | So khoá | khoá `A`–`F` |
| `AUDIO_WORD_CHOICE` | So khoá | khoá |
| `CLOZE_CHOICE` | So khoá | khoá |
| `PINYIN_INPUT` | §0.7 | chuỗi ≤ 100 |
| Loại spec sau thêm | Khai trong spec đó | |

**Câu không chấm:** `ItemType` có cờ `gradable`. Câu `gradable = false` (`STUDY_CARD`, `LESSON_READ` của 004, `WRITING_RESULT` của 006)
**luôn** được ghi theo cách `RECORD` (FR-007) dù lượt có kiểu phản hồi nào, và không tính vào `score`/`max_score`.

### 0.5 · Kỹ năng (`attempt_items.skill`)

`VOCAB` · `LISTENING` · `READING` · `CHARACTER` · `WRITING` · `GRAMMAR` · `PRONUNCIATION` — 6 giá trị đầu khớp `knowledge_points.skill_type` để cây tri thức map được, thêm `PRONUNCIATION`.

### 0.6 · Luật chung khi trả lời (E-R)

| Mã | Luật | Vi phạm |
|---|---|---|
| **E-R1** | Lượt phải tồn tại; nếu không tồn tại thì xử lý giống E-R2 (`BUS-01`) | **403** `ATTEMPT_NOT_OWNED`, cùng thông báo với E-R2 |
| **E-R2** | `attempts.user_id` = người gọi (`BUS-01`) | **403** `ATTEMPT_NOT_OWNED` |
| **E-R3** | `status = IN_PROGRESS` và `now() ≤ expires_at`; quá hạn → chuyển `EXPIRED` | **422** `SESSION_EXPIRED` · **409** `SESSION_ALREADY_SUBMITTED` |
| **E-R4** | `no` phải có trong `served_items` | **400** `ITEM_NOT_IN_SESSION` |
| **E-R5** | `IMMEDIATE`/`RECORD`: mỗi câu một lần — kiểm trước, unique `(attempt_id, order_index)` chặn đua | **409** `ANSWER_ALREADY_SUBMITTED` |
| **E-R6** | Câu chọn: khoá phải thuộc `options` của câu | **400** `OPTION_NOT_IN_ITEM` |
| **E-R7** | `durationMs` 0…3 600 000; thiếu `answer` khi không `skipped`/`audioFailed`; `skipped=true` và `audioFailed=true` loại trừ nhau (Q-T04-02, leader duyệt 2026-10-30) | **422** `VALIDATION_FAILED` |
| **E-R8** | `durationMs < rules.minDurationMs` → vẫn chấm, `meta.suspicious = true` | không lỗi |
| **E-R9** | `replayCount > rules.maxReplays`. **`replayCount` = tổng số lần người học đã phát audio của câu đó, kể cả lần đầu** (0 = chưa nghe). `maxReplays: 3` ⇒ nghe tối đa 3 lần (BR-020-5) | **400** `TOO_MANY_REPLAYS` |
| **E-R10** | `audioFailed = true` chỉ hợp lệ khi `rules.hasAudio`; câu ghi `is_correct = NULL`, `meta.audioFailed = true`, **không tính** vào điểm | **400** `AUDIO_NOT_IN_ITEM` |
| **E-R11** | `skipped = true`: `IMMEDIATE` → `is_correct = NULL`, không tính điểm; `DEFERRED` → coi như chưa trả lời (sai khi nộp). Nguồn có `allowSkip = false` (vd `PRON_STAGE`, `GAME_ROUND`) từ chối | **400** `SKIP_NOT_ALLOWED` |
| **E-R12** | ≤ **600** câu trả lời/giờ và ≤ **120** lượt mới/giờ mỗi người (Redis `practice:ans:{userId}:{yyyyMMddHH}`, `practice:start:…`; Redis lỗi → bỏ qua, log cảnh báo) | **429** `RATE_LIMITED` kèm `retryAfterSeconds` |

**Sửa 2026-10-30 (leader duyệt):** E-R1/E-R2 trả cùng **403** `ATTEMPT_NOT_OWNED` và cùng message cho lượt không tồn tại hoặc không thuộc người gọi. Áp dụng ngay cho hai GET của T-03; T-04…T-06 phải áp dụng cho `answers`/`finish`/`abandon`. Thay thế mọi quy định 404 `SESSION_NOT_FOUND` cũ theo Constitution `BUS-01`.

### 0.7 · Chấm câu gõ pinyin (`PINYIN_INPUT`)

1. Chuẩn hoá đáp án người học: NFC, chữ thường, `ü`/`u:`/`v` → `v`; nếu có nguyên âm mang dấu → `PinyinUtils.toNumbered` (spec 002 FR-033); bỏ khoảng trắng, `'`, `-`.
2. Chuẩn hoá mỗi phần tử `accept` cùng cách (server lưu sẵn dạng số, vd `wo3men5`).
3. Thanh nhẹ: `5` được bỏ — `wo3men` = `wo3men5`.
4. Đúng ⇔ trùng một phần tử `accept`. Thiếu số thanh ở âm tiết có thanh → **sai** (`hao` ≠ `hao3`, SCR-036).

### 0.8 · Kết thúc lượt

| Mã | Luật |
|---|---|
| **E-F1** | `POST /api/practice/sessions/{id}/finish` kiểm E-R1…E-R3; với `DEFERRED` nhận thêm `answers[]` (gộp vào nháp, câu sau ghi đè câu trước). Q-T05-02 (leader chốt 2026-10-30): mỗi phần tử mới tính vào giới hạn E-R12, nháp đã lưu không tính lại; vượt trả 429 `RATE_LIMITED` + `retryAfterSeconds`, rollback toàn bộ finish, giữ IN_PROGRESS và nháp cũ; Redis lỗi xử lý như T-04 |
| **E-F2** | Khoá dòng `attempts` (`SELECT … FOR UPDATE`) rồi mới chấm — hai request nộp song song: một thành công, một **409** |
| **E-F3** | `DEFERRED`: chấm mọi câu, ghi `attempt_items`; câu `gradable` không có đáp án → `is_correct = false`, `meta.missing = true`; câu `gradable = false` không có đáp án → `answer = NULL`, `is_correct = NULL`, `meta.missing = true` |
| **E-F4** | `score` = số câu đúng; `max_score` = số câu `gradable` tính điểm (trừ `audioFailed`, trừ `skipped` của `IMMEDIATE`); nguồn có `maxScoreFromServed = true` (`GAME_ROUND`) → `max_score` = số câu `gradable` **đã phát** trừ `audioFailed` (câu bỏ lỡ vẫn nằm trong mẫu số — trần điểm của ván); lượt không có câu `gradable` nào → cả hai `NULL` |
| **E-F5** | `duration_ms = submitted_at − served_at`; Q-T05-03 (leader chốt 2026-10-30): mẫu số `answeredCount` chỉ gồm câu `gradable` có đáp án thật, không thiếu/skip/RECORD/audioFailed; `suspiciousCount` là số câu trong tập đó có `meta.suspicious`. Cờ chung = `answeredCount > 0 && suspiciousCount*100 >= answeredCount*30`; OR với `PracticeSourceHandler.isSuspicious(FinishContext)` (default false) trước `onFinish`. Response, context `onFinish` và `SessionFinishedEvent` dùng cùng cờ cuối. Câu thiếu DEFERRED vẫn tính sai vào điểm |
| **E-F6** | `status = SUBMITTED`, `submitted_at = now()`; ghi `study_events` (`event_type = 'STUDY'`, với `GAME_ROUND` là `'GAME'`, `ref_type`/`ref_id` như lượt, `local_date` = ngày giờ Việt Nam, `duration_ms`) |
| **E-F7** | Gọi `PracticeSourceHandler.onFinish(...)` của nguồn **trong cùng transaction**; handler trả dữ liệu riêng (vd `passed`, `unlockedNextStage`) gộp vào response. Handler ném `ApiException` (vd lượt chưa làm đủ) → **rollback toàn bộ**, lượt giữ `IN_PROGRESS` |
| **E-F7b** | `PracticeSourceHandler` có thêm 2 hook mặc định rỗng: `validateAnswer(item, answer)` (ném `ApiException` để từ chối một câu — luật riêng của nguồn) và `enrichMeta(item, answer) → Map` (thêm trường vào `attempt_items.meta` trước khi ghi). Bộ máy gọi cả hai ở mọi đường ghi câu (trả lời từng câu và nộp cả bài) |
| **E-F8** | Lượt `IMMEDIATE`/`RECORD` hết hạn vẫn giữ mọi `attempt_items` đã ghi (bằng chứng không mất); chỉ đổi `status = EXPIRED` |
| **E-F9** | Job mặc định phút 05 mỗi giờ, zone `Asia/Ho_Chi_Minh`, chuyển `IN_PROGRESS` có `expires_at < now` sang `EXPIRED`; truy cập lượt quá hạn cũng chuyển ngay (lazy). Q-T06-01 (leader chốt 2026-10-30): `@Scheduled(cron="${cnhsk.practice.expire-cron:0 5 * * * *}", zone="Asia/Ho_Chi_Minh")`; profile test đặt `cnhsk.practice.expire-cron=-`, bean maintenance vẫn tồn tại để gọi trực tiếp; không đổi/xóa bằng chứng |

### 0.9 · Móc nối cây tri thức (D-04)

| Sự kiện | Khi | Dữ liệu |
|---|---|---|
| `ItemGradedEvent` | Mỗi câu được chấm hoặc ghi nhận (cả `RECORD`) | `userId, sessionId, source, refId, no, lexemeId, skill, itemType, correct (Boolean, null được), meta` |
| `SessionFinishedEvent` | Sau E-F7 | `userId, sessionId, source, refId, score, maxScore, suspicious` |

Phát bằng `ApplicationEventPublisher` **đồng bộ trong transaction**. Đợt này **không có listener**. Spec cây tri thức thêm
`@EventListener` để cập nhật mastery cùng transaction (`AC-10`) — không sửa code bộ máy.

### 0.10 · Sinh câu chọn từ `lexemes` (`ChoiceItemFactory`)

| Mã | Luật |
|---|---|
| **G-1** | Nguồn nhiễu theo thứ tự ưu tiên: cùng chủ đề (nếu có) → cùng `kind` cùng `hsk_level` → cùng `kind` mọi cấp. Lấy ngẫu nhiên trong mỗi tầng |
| **G-2** | Loại ứng viên: cùng `id`; cùng `text`; **trùng nghĩa** — tách `meaning_norm` theo `;` và `,`, bỏ khoảng trắng hai đầu, hai từ trùng nghĩa nếu có một mảnh giống hệt nhau |
| **G-3** | Câu có phương án là **chữ/từ Hán** (nghe chọn chữ): loại thêm ứng viên **đồng âm** — `toNumbered(pinyin)` bỏ khoảng trắng bằng nhau (`是`/`事`) |
| **G-4** | Thiếu nhiễu (cần đủ `optionCount − 1`) → **không sinh câu đó**, log `WARN` kèm `lexemeId`. Nhiễu lấy ngoài chủ đề → đặt `served_items.meta.mixedDistractors = true` (cấp lượt, trả trong `SessionView.meta`) |
| **G-5** | Phương án nghĩa hiển thị = mảnh đầu tiên của `meaning_vi` (trước `;`); so trùng dùng toàn bộ mảnh (G-2) |
| **G-6** | Trộn thứ tự rồi gán khoá `A`, `B`, `C`, … theo thứ tự đã trộn (`ThreadLocalRandom`, không phải dữ liệu bí mật nên không cần `SecureRandom`) |

### 0.11 · Endpoint chung của bộ máy

| Method · Path | Mục đích |
|---|---|
| `GET /api/practice/sessions/{id}` | Xem lại lượt (khôi phục): câu **không đáp án**, câu đã trả lời kèm kết quả (`IMMEDIATE`/`RECORD`) hoặc nháp (`DEFERRED`), `status`, `expiresAt` |
| `GET /api/practice/sessions/active?source=&refId=` | Lượt `IN_PROGRESS` chưa hết hạn mới nhất của người gọi cho nguồn đó; không có → **204** |
| `POST /api/practice/sessions/{id}/answers` | Trả lời một câu (§0.6) |
| `POST /api/practice/sessions/{id}/finish` | Kết thúc (§0.8) |
| `POST /api/practice/sessions/{id}/abandon` | Bỏ lượt: E-R1…E-R3, `IN_PROGRESS → ABANDONED`, không điểm, không `study_events`, không sự kiện; câu đã ghi giữ nguyên (E-F8). **204** |

**Sửa 2026-10-30 (leader duyệt):** `/active` có `refId` thì lọc đúng `ref_id`; thiếu thì tìm trên mọi `ref_id` của nguồn. Luôn lọc người gọi, `IN_PROGRESS`, `expires_at >= now`, chọn `served_at DESC, id DESC`; không có trả 204. GET xem lại của chủ sở hữu trả 200 cho mọi trạng thái; `IN_PROGRESS` quá hạn được đổi `EXPIRED` trước khi trả view. Query/path sai kiểu hoặc thiếu `source` trả 422 `VALIDATION_FAILED`; chỉ `LEARNER` được gọi hai GET (sai role: 403).

`StartPracticeCommand` gồm: `userId, source, refId, gameCode` (chỉ `GAME_ROUND` → `attempts.game_code`), `items, preRecorded, ttlOverride, sessionMeta`.
`sessionMeta` (cấu hình hiển thị, vd thời gian ván, số lỗi tối đa) lưu ở `served_items.meta` (§0.3) và trả trong `SessionView.meta`; **không** được chứa đáp án.

Endpoint **bắt đầu** lượt nằm ở từng tính năng (spec 003 §3, 004, 005, 006, 007).

**Lớp `api` cho module khác (`AC-03`):** `learning.api.PracticeApi` — `start(StartPracticeCommand)`, `answer(...)`, `finish(...)`, `view(...)` trả DTO; module `community` (game) chỉ dùng lớp này.

### 0.12 · Thay đổi DB — `V4__practice_engine.sql`

| # | Thay đổi | Vì sao |
|---|---|---|
| 1 | `ALTER TABLE learning.attempt_items ADD COLUMN item_type VARCHAR(30) NOT NULL` | Thống kê theo loại câu cần cột thật (`AC-09`) |
| 2 | `ADD COLUMN skill VARCHAR(20) NOT NULL CHECK (skill IN ('VOCAB','LISTENING','READING','CHARACTER','WRITING','GRAMMAR','PRONUNCIATION'))` | Cây tri thức map theo kỹ năng |
| 3 | `ADD COLUMN meta JSONB NOT NULL DEFAULT '{}'` | Cờ ghi một lần (nghi ngờ, lỗi audio, số lần nghe lại…) — chỉ đọc |
| 4 | `CREATE UNIQUE INDEX uq_ai_order ON learning.attempt_items(attempt_id, order_index)` | E-R5 ở mức DB |
| 5 | `CREATE INDEX ix_att_user_ref ON learning.attempts(user_id, ref_type, ref_id, status)` | Tìm lượt đang làm, điểm kiểm tra cao nhất |
| 6 | `CREATE INDEX ix_ai_lexeme ON learning.attempt_items(lexeme_id, attempt_id) WHERE lexeme_id IS NOT NULL` | Đếm từ đã học |

Bảng rỗng lúc chạy nên `NOT NULL` không cần giá trị mặc định.

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-05** | Demo trọn luồng học → kiểm tra | Chủ đề là luồng học chính (P0) |
| **BG-01** | Người Việt học từ có âm Hán Việt | Thẻ từ hiện `sino_viet` |

**Vấn đề:** use case gốc phụ thuộc mastery, FSRS và mở khoá 90% — thuộc cây tri thức đang hoãn (D-06). Spec này làm
phần học/luyện/kiểm tra chạy độc lập, lưu bằng chứng để cây dùng lại.

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Người học đi hết một chủ đề: học → luyện nhận diện → luyện nghe → kiểm tra | không lỗi trên dữ liệu thật đã nhập |
| **SC-002** | `items`/`meta` của response bắt đầu/khôi phục lượt không chứa đáp án server `answer`/`accept`; `answered[].answer` là đầu vào của người học | 100% endpoint |
| **SC-003** | % hoàn thành chủ đề luôn khớp số từ đã học | 100% — tính khi đọc, không có bản cache để lệch |
| **SC-004** | Không câu nào có hai đáp án đúng (trùng nghĩa, đồng âm) | 0 trên bộ test |

---

## 2 · User Stories

### US-1 · Học từ mới (P1)
**Given** chủ đề "Gia đình" có 15 từ, tôi chưa học từ nào
**When** tôi bấm "Học từ mới"
**Then** tôi thấy lần lượt 10 thẻ: chữ, pinyin, âm Hán Việt, nghĩa, ví dụ, nút nghe
**And** mỗi lần bấm "Tiếp" (hoặc "Tôi đã biết từ này"), từ đó được tính là đã học; % chủ đề tăng ngay

### US-2 · Luyện nhận diện / nghe (P1)
**Given** tôi đã học 8 từ
**When** tôi chọn "Luyện nhận diện"
**Then** tôi trả lời 8 câu chọn nghĩa, mỗi câu biết đúng/sai ngay, không câu nào có hai đáp án đúng

### US-3 · Kiểm tra cuối chủ đề (P1)
**Given** tôi đã học ≥ 80% số từ
**When** tôi làm bài kiểm tra và nộp
**Then** tôi thấy điểm, danh sách từ sai, nút "Luyện lại từ sai"; đạt ≥ 90% → chủ đề "Hoàn thành"

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Chủ đề 3 từ | Luyện: nhiễu lấy ngoài chủ đề (`mixedDistractors`); vẫn không đủ → `NOT_ENOUGH_WORDS` |
| E2 | 爸爸 và 父亲 cùng nghĩa "bố" trong chủ đề | Không bao giờ cùng xuất hiện trong một câu chọn nghĩa (G-2) |
| E3 | 是 và 事 cùng `shì` | Không cùng xuất hiện trong câu nghe chọn chữ (G-3) |
| E4 | Bỏ giữa buổi học (4/10 thẻ) | 4 từ vẫn tính đã học; lần sau "Học từ mới" phát 10 từ chưa học khác |
| E5 | Cùng từ thuộc 2 chủ đề | Học ở chủ đề A thì chủ đề B cũng tính đã học (tính khi đọc) |
| E6 | Nộp bài kiểm tra 2 lần song song | Một thành công, một **409** |
| E7 | Thi lại sau khi trượt 3 phút | **429** `RETRY_TOO_SOON` kèm `retryAfterSeconds` |
| E8 | Lần 1 đạt 95%, lần 2 đạt 70% | Chủ đề vẫn "Hoàn thành", điểm cao nhất 95 |
| E9 | Audio không tải được khi luyện nghe | Bấm "Không nghe được" → câu không tính, không sai |
| E10 | Đang có bài kiểm tra chưa nộp, bấm "Kiểm tra" lần nữa | Trả lại chính bài đó (tiếp tục), không tạo lượt mới |

---

## 3 · Functional Requirements

### Nhóm 1 — Bộ máy lượt luyện (FR-001…FR-012)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL cài đặt `PracticeService` với `start`, `answer`, `finish`, `view`, `findActive` đúng §0.1–§0.8 |
| **FR-002** | THE system SHALL ghi `served_items` một lần khi tạo lượt; không endpoint nào sửa được nó. `StartPracticeCommand` có thể kèm `preRecorded` — câu `gradable = false` được ghi `attempt_items` ngay khi tạo lượt (vd thời gian đọc bài ngữ pháp, spec 004) |
| **FR-003** | THE system SHALL trả câu cho client bằng `ServedItemView` không có `answer`, `accept` (SC-002) |
| **FR-004** | THE system SHALL áp dụng E-R1…E-R12 cho `POST /api/practice/sessions/{id}/answers` |
| **FR-005** | WHEN `IMMEDIATE`, THE system SHALL trả `{no, correct, correctAnswer, accept?, explanation, counted}` (`counted=false` với câu `skipped`/`audioFailed`) |
| **FR-006** | WHEN `DEFERRED`, THE system SHALL trả `{no, accepted:true}` và ghi `draft_answers[no] = {answer, durationMs, replayCount, skipped, audioFailed}`, `draft_saved_at = now()`; thay nháp giữ đầy đủ bằng chứng của đáp án mới (Q-T04-01, leader duyệt 2026-10-30). Response answer và GET session trước finish không có field `correct`/`correctAnswer` |
| **FR-007** | WHEN lượt `RECORD` **hoặc** câu `gradable = false`, THE system SHALL ghi `attempt_items` `is_correct = NULL`, `answer` = giá trị client gửi, trả `{no, recorded:true}` |
| **FR-008** | THE system SHALL áp dụng E-F1…E-F9 cho `finish`; response `{sessionId, status, score, maxScore, percent, suspicious, items:[{no, correct, correctAnswer, yourAnswer}], extra}` (`percent = floor(score*100/maxScore)`, `null` khi `maxScore` = 0 hoặc `NULL`) |
| **FR-009** | THE system SHALL phát `ItemGradedEvent`, `SessionFinishedEvent` (§0.9) trong transaction |
| **FR-010** | THE system SHALL cung cấp `GET /api/practice/sessions/{id}` và `/active` (§0.11) |
| **FR-011** | THE system SHALL cung cấp `ChoiceItemFactory` đúng G-1…G-6 và `PinyinGrader` đúng §0.7 |
| **FR-012** | THE system SHALL cung cấp `learning.api.PracticeApi` bọc `PracticeService` cho module khác; DTO không lộ entity |

### Nhóm 2 — Danh sách chủ đề (FR-013…FR-017) · UC-025, SCR-011

| Mã | Yêu cầu |
|---|---|
| **FR-013** | `GET /api/topics?hskLevel=` SHALL trả các chủ đề có ≥ 1 `topic_items` (`lexeme_id` khác NULL), lọc `hskLevel` 1–9 nếu có (khác → **422** `VALIDATION_FAILED`), sắp `hsk_level`, `order_index`, `id` |
| **FR-014** | Mỗi chủ đề SHALL có `{id, code, name, hskLevel, totalWords, learnedWords, percent, status, bestTestPercent}`; `percent = floor(learnedWords*100/totalWords)` |
| **FR-015** | **Từ đã học** (của một người) := lexeme có ít nhất một `attempt_items` `item_type='STUDY_CARD'` thuộc lượt `ref_type='TOPIC_STUDY'` của người đó, **bất kể chủ đề nào** (E5). THE system SHALL tính `learnedWords` bằng **một truy vấn tổng hợp** cho cả danh sách, không cache |
| **FR-016** | `status` SHALL là `COMPLETED` nếu có lượt `TOPIC_TEST` `SUBMITTED` của chủ đề với `score*100 ≥ 90*max_score` (`BUS-05`); `IN_PROGRESS` nếu `learnedWords > 0` hoặc có lượt bất kỳ của chủ đề; còn lại `NOT_STARTED`. **Không có `LOCKED`** đợt này (D-06) |
| **FR-017** | `bestTestPercent` SHALL là `max(floor(score*100/max_score))` trên các lượt `TOPIC_TEST` `SUBMITTED` của chủ đề, `null` nếu chưa thi |

### Nhóm 3 — Chi tiết chủ đề và học từ mới (FR-018…FR-024) · UC-026, SCR-012

| Mã | Yêu cầu |
|---|---|
| **FR-018** | `GET /api/topics/{id}` SHALL trả tiêu đề (như FR-014) + `words:[{lexemeId, text, pinyin, sinoViet, meaning, hskLevel, audioUrl, learned}]` theo `order_index` + `actions:{canStudy, canPracticeRecognition, canPracticeListening, canTakeTest, testBlockedReason, retryAvailableAt}`; chủ đề không tồn tại hoặc không có từ → **404** `TOPIC_NOT_FOUND` |
| **FR-019** | `POST /api/topics/{id}/study-sessions` SHALL tạo lượt `TOPIC_STUDY` với tối đa **10** từ **chưa học** của chủ đề theo `order_index`, mỗi từ một câu `STUDY_CARD` (`skill = VOCAB`); hết từ chưa học → **200** `{session: null, reason: "NO_NEW_WORDS"}` |
| **FR-020** | Câu `STUDY_CARD` SHALL mang trong `prompt`: `text, pinyin, sinoViet, meaning (đủ meaning_vi), examples[], audioUrl, kind` — không có trường bí mật |
| **FR-021** | Trả lời `STUDY_CARD` SHALL nhận `answer ∈ {LEARNED, KNOWN}`; khác → **422** `VALIDATION_FAILED`. `KNOWN` ghi `meta.selfDeclared = true`; `durationMs < 1000` ghi `meta.suspicious = true` (vẫn tính đã học — UC-026 T5) |
| **FR-022** | WHEN đã có lượt `TOPIC_STUDY` `IN_PROGRESS` chưa hết hạn của chủ đề, THE system SHALL trả lại lượt đó (tiếp tục) thay vì tạo mới |
| **FR-023** | Thẻ từ SHALL hiện chữ, pinyin, âm Hán Việt (ẩn nếu rỗng), nghĩa, ví dụ (chữ + pinyin + nghĩa), nút nghe (ẩn nếu không có `audioUrl`), nút "Tiếp" và "Tôi đã biết từ này" |
| **FR-024** | WHEN trả lời hết câu, THE frontend SHALL gọi `finish` rồi hiện "Đã học {n} từ mới" + gợi ý "Luyện nhận diện" |

### Nhóm 4 — Luyện nhận diện và nghe (FR-025…FR-030) · UC-027, UC-028

| Mã | Yêu cầu |
|---|---|
| **FR-025** | `POST /api/topics/{id}/practice-sessions` `{mode, focusLexemeIds?}` SHALL tạo lượt `TOPIC_PRACTICE` từ **từ đã học** của chủ đề, tối đa **10** câu, ưu tiên từ có ít câu trả lời đúng nhất trong các lượt `TOPIC_PRACTICE` trước (đếm `attempt_items`), hoà thì ngẫu nhiên |
| **FR-026** | `mode = RECOGNITION` SHALL sinh câu `MEANING_CHOICE` (`skill = VOCAB`): `prompt.text` = chữ Hán, **không** kèm pinyin; 4 phương án nghĩa (G-1, G-2, G-5) |
| **FR-027** | `mode = LISTENING` SHALL chỉ dùng từ có `audio_url`, sinh câu `AUDIO_WORD_CHOICE` (`skill = LISTENING`): `prompt.audioUrl`, 4 phương án là chữ Hán (G-1, G-3), `rules.hasAudio = true`, `maxReplays = 3` |
| **FR-028** | WHEN chưa học từ nào → **422** `NO_LEARNED_WORDS`; LISTENING mà không từ đã học nào có audio → **422** `NO_AUDIO_WORDS`; sau G-4 không sinh được câu nào → **422** `NOT_ENOUGH_WORDS` |
| **FR-029** | WHERE có `focusLexemeIds` (luyện lại từ sai), THE system SHALL chỉ dùng các từ đó; từ nào không thuộc chủ đề hoặc chưa học → **422** `INVALID_FOCUS_WORDS` |
| **FR-030** | `minDurationMs` = 800 với câu chọn (E-R8, UC-019 A3) |

### Nhóm 5 — Kiểm tra cuối chủ đề (FR-031…FR-040) · UC-029

| Mã | Yêu cầu |
|---|---|
| **FR-031** | `POST /api/topics/{id}/tests` SHALL kiểm: chủ đề có ≥ **4** từ; `learnedWords*100 ≥ 80*totalWords` (**422** `NOT_ENOUGH_LEARNED`, kèm `learnedWords`, `requiredWords = ceil(0.8*total)`) |
| **FR-032** | WHEN lượt `TOPIC_TEST` gần nhất của chủ đề đã `SUBMITTED`, **không đạt**, và nộp cách đây < **10 phút**, THE system SHALL trả **429** `RETRY_TOO_SOON` kèm `retryAfterSeconds` |
| **FR-033** | WHEN có lượt `TOPIC_TEST` `IN_PROGRESS` chưa hết hạn, THE system SHALL trả lại lượt đó (E10) |
| **FR-034** | THE system SHALL chọn tối đa **20** từ của chủ đề (nhiều hơn → ngẫu nhiên 20), trộn thứ tự, gán dạng câu theo vòng `MEANING_CHOICE → AUDIO_WORD_CHOICE → PINYIN_INPUT → CLOZE_CHOICE` (chỉ số `i % 4`) |
| **FR-035** | Thay thế khi thiếu dữ liệu: `AUDIO_WORD_CHOICE` cần `audio_url`; `CLOZE_CHOICE` cần ví dụ `examples[0].cn` **chứa** `text` của từ; thiếu → dùng `MEANING_CHOICE`. Câu không sinh được (G-4) → dùng `PINYIN_INPUT` |
| **FR-036** | `PINYIN_INPUT` (`skill = VOCAB`): `prompt.text` = chữ Hán + `prompt.hint` = nghĩa; `accept = [toNumbered(pinyin) bỏ khoảng trắng]`; chấm §0.7 |
| **FR-037** | `CLOZE_CHOICE` (`skill = READING`): `prompt.sentence` = câu ví dụ với **lần xuất hiện đầu** của từ thay bằng `＿＿`, `prompt.hint` = nghĩa câu (`examples[0].vi`); 4 phương án là chữ Hán (G-1, G-2) |
| **FR-038** | Lượt kiểm tra là `DEFERRED`, hạn 24 giờ; nộp: E-F1…E-F9; câu thiếu = sai (UC-029 `MISSING_ANSWERS`); `submitted − served < 2 s × số câu` → `suspicious` (vẫn chấm) |
| **FR-039** | `TopicTestHandler.onFinish` SHALL trả `extra = {passed, bestPercent, topicCompleted, firstCompletion, wrongWords:[{lexemeId, text, pinyin, meaning}]}`; `passed ⇔ score*100 ≥ 90*maxScore`; `firstCompletion` = lần đạt đầu tiên |
| **FR-040** | Lượt kiểm tra bỏ dở quá 24 giờ SHALL thành `EXPIRED`, không chấm (UC-029 A3) |

### Nhóm 6 — Giao diện (FR-041…FR-045)

| Mã | Yêu cầu |
|---|---|
| **FR-041** | SCR-011 SHALL hiện lưới thẻ chủ đề (tên, cấp HSK, `learnedWords/totalWords`, thanh %, nhãn trạng thái "Chưa học"/"Đang học"/"Hoàn thành", điểm kiểm tra cao nhất), bộ lọc cấp HSK 1–9 ("Tất cả" mặc định), lọc trạng thái phía client. Không hiện ổ khoá |
| **Làm rõ FR-041 (leader chốt 2026-10-31)** | Giai đoạn hiện tại bộ lọc chỉ hiện HSK 1–3 theo `SUPPORTED_HSK_LEVELS`; API và types vẫn nhận 1–9. |
| **FR-042** | SCR-012 SHALL có đầu trang chủ đề và **4 bước**: "Học từ mới" · "Luyện nhận diện" · "Luyện nghe" · "Kiểm tra cuối chủ đề"; bước bị khoá theo `actions` hiện lý do ("Học thêm {n} từ để mở bài kiểm tra", "Chủ đề chưa có âm thanh") |
| **FR-043** | Màn luyện SHALL: hiện câu, phát audio bằng `<audio>` (không tự phát trước khi người dùng bấm lần đầu), đếm số lần nghe lại (tối đa 3), nút "Không nghe được" (gửi `audioFailed`), nút "Bỏ qua" (gửi `skipped`), hiện đúng/sai + đáp án đúng sau khi chấm, "Câu tiếp", tổng kết cuối lượt |
| **FR-044** | Màn kiểm tra SHALL cho đi lại giữa các câu, lưu nháp từng câu ngay khi chọn/gõ (debounce 500 ms với ô gõ), nút "Nộp bài" có bước xác nhận trong trang ("Còn {k} câu chưa làm — câu chưa làm tính sai"), kết quả: điểm, đạt/không đạt, từ sai, nút "Luyện lại từ sai" (gọi FR-029) |
| **FR-045** | Mọi màn SHALL khôi phục lượt đang làm khi tải lại trang (dùng `/active` + `GET /sessions/{id}`) |

---

## 4 · Data Model

Đọc: `learning.topics`, `topic_items`, `lexemes` (spec 002). Ghi: `learning.attempts`, `attempt_items`, `study_events`.
**Không** ghi `user_progress` (D-06). Thay đổi DB: §0.12.

Truy vấn từ đã học (FR-015) — tham chiếu cho Codex:

```sql
SELECT ti.topic_id,
       count(*)                               AS total_words,
       count(l.lexeme_id)                     AS learned_words
FROM learning.topic_items ti
LEFT JOIN (
  SELECT DISTINCT ai.lexeme_id
  FROM learning.attempt_items ai
  JOIN learning.attempts a ON a.id = ai.attempt_id
  WHERE a.user_id = :userId AND a.ref_type = 'TOPIC_STUDY' AND ai.item_type = 'STUDY_CARD'
) l ON l.lexeme_id = ti.lexeme_id
WHERE ti.lexeme_id IS NOT NULL
GROUP BY ti.topic_id;
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `ATTEMPT_NOT_OWNED` | 403 | Lượt không tồn tại hoặc của người khác, cùng thông báo | E-R1, E-R2 |
| `SESSION_EXPIRED` | 422 | Quá hạn | E-R3 |
| `SESSION_ALREADY_SUBMITTED` | 409 | Đã kết thúc | E-R3, E-F2 |
| `ITEM_NOT_IN_SESSION` | 400 | `no` lạ | E-R4 |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời lại | E-R5 |
| `OPTION_NOT_IN_ITEM` | 400 | Khoá lạ | E-R6 |
| `TOO_MANY_REPLAYS` | 400 | Nghe lại > 3 | E-R9 |
| `AUDIO_NOT_IN_ITEM` | 400 | Báo lỗi audio cho câu không có audio | E-R10 |
| `SKIP_NOT_ALLOWED` | 400 | Bỏ qua ở nguồn không cho bỏ qua | E-R11 |
| `RATE_LIMITED` | 429 | Vượt E-R12 | E-R12 |
| `VALIDATION_FAILED` | 422 | Dữ liệu sai | E-R7, FR-013, FR-021 |
| `TOPIC_NOT_FOUND` | 404 | Chủ đề không có/không có từ | FR-018 |
| `NO_LEARNED_WORDS` | 422 | Chưa học từ nào | FR-028 |
| `NO_AUDIO_WORDS` | 422 | Không từ nào có audio | FR-028 |
| `NOT_ENOUGH_WORDS` | 422 | Không đủ nhiễu | FR-028 |
| `INVALID_FOCUS_WORDS` | 422 | Từ luyện lại không hợp lệ | FR-029 |
| `NOT_ENOUGH_LEARNED` | 422 | Học < 80% | FR-031 |
| `RETRY_TOO_SOON` | 429 | Thi lại < 10 phút | FR-032 |

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **BUS-09** chấm ở server | Mọi câu có đáp án chấm ở server; đáp án không ra client trước khi chấm |
| **BUS-01** | E-R2 |
| **BUS-02** cùng transaction | `finish` + handler + sự kiện trong một transaction |
| **AC-03** | Game chỉ dùng `learning.api.PracticeApi` |
| **AC-07** | `score`, `max_score` BIGINT; `percent` tính, không lưu |
| **AC-08** | Job E-F9 có `zone` |
| **AC-09** | `served_items`, `meta` ghi một lần; `draft_answers` là bản nháp (đã chốt ở `database.md`) |
| **AC-10** | Móc nối sự kiện đồng bộ để mastery sau này cùng transaction |

**Package:** `learning.practice` (`PracticeService`, `PracticeSource`, `ItemType`, `Skill`, `ServedItem`, `ServedItemView`, `ChoiceItemFactory`, `PinyinGrader`, `PracticeSourceHandler`, `event.*`, `web.PracticeController`, `PracticeMaintenance`) · `learning.vocab` (`TopicQueryService`, `TopicStudyService`, `TopicPracticeService`, `TopicTestService`, `TopicTestHandler`, `web.TopicController`) · `learning.api.PracticeApi`.

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `GET /api/topics` với 200 chủ đề, người dùng có 5 000 câu trả lời | < 300 ms p95 |
| `NFR-P02` | Trả lời một câu | < 150 ms p95 |
| `NFR-P03` | Nộp bài 20 câu | < 500 ms p95 |
| `NFR-S01` | Không lộ đáp án | SC-002 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Có mastery, FSRS, `next_review_at`, mở khoá 90% | **Không** — chỉ ghi bằng chứng + sự kiện |
| Dùng `questions`, `knowledge_points` | Không (D-01) |
| Ghi `user_progress` | Không (D-06) |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Chưa khoá chủ đề, mọi chủ đề mở | Chốt (D-06) — cây tri thức thêm `LOCKED` |
| Q2 | "Đã học" = có thẻ học, không phải mastery | Chốt |
| Q3 | Bài kiểm tra không có dạng viết chữ (viết ở spec 006) | Chốt — chốt open issue SCR-012 |
| Q4 | `KNOWN` ("Tôi đã biết") tính là đã học ngay, đánh cờ `selfDeclared`; hạ cấp khi kiểm tra sai thuộc cây | Chốt |
| Q5 | Migration `V4__practice_engine.sql` | **Chốt** (leader 2026-10-30) — chỉ test bằng Testcontainers; leader chạy lên Neon sau khi PR merge |
| Q6 | Ngưỡng 80% học trước khi thi, 90% đạt, chờ 10 phút | Chốt theo UC-029, `BUS-05` |

---

## 10 · Móc nối cây tri thức (làm sau, không làm bây giờ)

| Chỗ | Bây giờ | Khi có cây |
|---|---|---|
| `ItemGradedEvent` | Không ai nghe | Listener cập nhật mastery KID của từ (UC-042) |
| Trạng thái chủ đề | Không có `LOCKED` | `FrontierService` trả khoá/mở; FR-016 thêm `LOCKED` |
| Từ "đã thuộc" (`MASTERED`) | Không có | Từ mastery ≥ ngưỡng |
| `selfDeclared` | Chỉ ghi cờ | Kiểm tra sai → hạ cấp |
| Chủ đề hoàn thành | Chỉ hiển thị | Mở chủ đề sau (UC-046) |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| `GET /api/topics` tính trên `attempt_items` mỗi lần | Có index §0.12; quy mô đồ án; tránh lỗi lệch cache |
| Học viên bấm "Tiếp" liên tục để tăng % | Đã ghi `suspicious`; bài kiểm tra chấm server là thước đo thật |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-011 Topic List | `/learn/topics` | `LEARNER` | `GET /api/topics` | `screen-fields.md` dòng 4466 |
| SCR-012 Topic Study | `/learn/topics/:id` (bước con: `/study`, `/practice/recognition`, `/practice/listening`, `/test`) | `LEARNER` | §13 | dòng 4946 |

**Chốt open issue:**

| Open issue | Chốt |
|---|---|
| Lọc HSK 1–9 hay 1–6 | 1–9 (HSK 3.0, khớp `lexemes.hsk_level`) |
| Ô tìm chủ đề, chip trạng thái | Lọc phía client trên danh sách đã tải; không gọi API |
| Nút "Bỏ qua" | Luyện: không tính điểm (E-R11); kiểm tra: câu chưa làm = sai |
| Bài kiểm tra có dạng viết | Không (Q3) |
| Hạ cấp `self_declared` | Hoãn sang cây (Q4) |

---

## 13 · API Contract

[`contracts/openapi-practice-vocab.yaml`](contracts/openapi-practice-vocab.yaml)

| Method · Path | Thành công | Lỗi chính |
|---|---|---|
| `GET /api/practice/sessions/{id}` | 200 `SessionView` | 401, 403, 422 |
| `GET /api/practice/sessions/active?source&refId` | 200 `SessionView` · 204 | 401, 403, 422 |
| `POST /api/practice/sessions/{id}/answers` | 200 `AnswerResult` | 400, 401, 403, 409, 422, 429 |
| `POST /api/practice/sessions/{id}/finish` | 200 `FinishResult` | 400, 401, 403, 409, 422, 429 |
| `POST /api/practice/sessions/{id}/abandon` | 204 | 401, 403, 409, 422 |
| `GET /api/topics?hskLevel` | 200 `[TopicSummary]` | 422 |
| `GET /api/topics/{id}` | 200 `TopicDetail` | 404 |
| `POST /api/topics/{id}/study-sessions` | 201 `SessionView` · 200 `{session:null}` | 404, 429 |
| `POST /api/topics/{id}/practice-sessions` | 201 `SessionView` | 404, 422, 429 |
| `POST /api/topics/{id}/tests` | 201 `SessionView` (mới) · 200 (tiếp tục) | 404, 422, 429 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-002, FR-003 | W tạo/khôi phục mọi loại lượt · T `items`/`meta` không có đáp án server `answer`/`accept`; cho phép `answered[].answer` của người học. `DEFERRED` trước finish không có field `correct`/`correctAnswer`/`accept`/`explanation` (Q-T04-03) |
| AT-02 | E-R1, E-R2 | G lượt của A · W B xem/trả lời hoặc dùng id không tồn tại · T cùng 403 `ATTEMPT_NOT_OWNED` và cùng message |
| AT-03 | E-R3 | G lượt quá hạn (đồng hồ giả) · W trả lời · T 422 `SESSION_EXPIRED`, `status=EXPIRED` |
| AT-04 | E-R4 | W `no=99` · T 400 |
| AT-05 | E-R5 | W trả lời 2 lần cùng câu `IMMEDIATE`; W 2 request song song · T 409; DB chỉ 1 dòng |
| AT-06 | E-R6 | W khoá `Z` · T 400 `OPTION_NOT_IN_ITEM` |
| AT-07 | E-R8, FR-030 | W `durationMs=300` · T chấm bình thường, `meta.suspicious=true` |
| AT-08 | E-R9 | W `replayCount=4` · T 400 |
| AT-09 | E-R10, E9 | W `audioFailed` câu có audio · T `is_correct NULL`, `counted=false`; câu không audio · T 400 |
| AT-10 | E-R11 | `IMMEDIATE` skip → không tính; `DEFERRED` skip → sai khi nộp; nguồn `allowSkip=false` (nguồn giả trong test) → 400 `SKIP_NOT_ALLOWED` |
| AT-11 | E-R12 | W câu trả lời thứ 601 trong giờ · T 429; Redis dừng · T vẫn 200 |
| AT-12 | FR-006 | W đổi đáp án câu `DEFERRED` 2 lần · T nháp giữ lần sau, response không có `correct` |
| AT-13 | E-F2, E6 | W 2 `finish` song song · T 1 thành công, 1 409 |
| AT-14 | E-F3, E-F4 | G kiểm tra 20 câu, trả lời 15 (12 đúng) · T `score=12`, `maxScore=20`, 5 câu `meta.missing` |
| AT-15 | E-F6 | T `study_events` 1 dòng, `local_date` theo giờ VN (nộp lúc 23:30 UTC → ngày hôm sau) |
| AT-16 | E-F8 | G lượt học 4/10 thẻ rồi hết hạn · T 4 từ vẫn tính đã học |
| AT-16b | §0.11 abandon | W bỏ lượt đã trả lời 3 câu · T `ABANDONED`, 3 `attempt_items` còn, không `study_events`; bỏ lần 2 · T 409 |
| AT-16c | E-F4 `maxScoreFromServed` | Nguồn giả có cờ, phát 20 câu, trả lời 12 (10 đúng) · T `score=10`, `maxScore=20` |
| AT-17 | FR-009 | T listener test nhận đúng số `ItemGradedEvent` và 1 `SessionFinishedEvent`; listener ném lỗi → toàn bộ `finish` rollback |
| AT-18 | §0.7 | `wo3men`, `wǒmen`, `wo3 men5`, `WO3MEN` đúng với `wǒmen`; `women` sai; `nü3`/`nv3`/`nǚ` đúng với `nǚ` |
| AT-19 | G-2, E2 | G chủ đề có 爸爸, 父亲 (cùng "bố") · W sinh 100 lượt nhận diện · T không câu nào có cả hai |
| AT-20 | G-3, E3 | G 是, 事 · W sinh 100 lượt nghe · T không câu nào có cả hai |
| AT-21 | G-4, E1 | G chủ đề 3 từ, cùng cấp có thêm 5 từ · T câu có `mixedDistractors`; toàn DB chỉ 3 từ · T 422 `NOT_ENOUGH_WORDS` |
| AT-22 | FR-013 | `hskLevel=10` · T 422; chủ đề không có từ · T không xuất hiện |
| AT-23 | FR-015, E5 | G từ X thuộc chủ đề A, B; học ở A · T B tính X đã học |
| AT-24 | FR-016, FR-017, E8 | G thi 95 rồi 70 · T `COMPLETED`, `bestTestPercent=95` |
| AT-25 | FR-019, E4 | G học 4/10 · W bắt đầu lại sau khi lượt cũ hết hạn · T 10 từ chưa học, không chứa 4 từ cũ |
| AT-26 | FR-019 | G học hết · T 200 `session=null`, `reason=NO_NEW_WORDS` |
| AT-27 | FR-021 | `KNOWN` → `meta.selfDeclared`; `durationMs=200` → `suspicious`, vẫn tính học |
| AT-28 | FR-022, FR-033 | Gọi bắt đầu 2 lần · T cùng `sessionId` |
| AT-29 | FR-025 | G từ X đúng 3 lần, Y đúng 0 lần · T Y được ưu tiên |
| AT-30 | FR-026 | T câu nhận diện không có pinyin trong `prompt` |
| AT-31 | FR-027, FR-028 | G không từ đã học có audio · T 422 `NO_AUDIO_WORDS` |
| AT-32 | FR-029 | `focusLexemeIds` có từ chủ đề khác · T 422 `INVALID_FOCUS_WORDS` |
| AT-33 | FR-031 | Học 7/10 · T 422 `NOT_ENOUGH_LEARNED`, `requiredWords=8` |
| AT-34 | FR-032, E7 | Trượt rồi thi lại sau 3 phút · T 429, `retryAfterSeconds≈420`; sau khi **đạt** thì thi lại ngay được |
| AT-35 | FR-034, FR-035 | G 25 từ, 10 có audio, 5 có ví dụ chứa từ · T 20 câu, phân bổ đúng vòng + thay thế |
| AT-36 | FR-037 | `examples[0].cn = "我们是朋友"`, từ `朋友` · T `sentence = "我们是＿＿"` |
| AT-37 | FR-038 | Nộp sau 10 s cho 20 câu · T `suspicious=true`, vẫn có điểm |
| AT-38 | FR-039 | 18/20 · T `passed=true`, `firstCompletion=true`; lần sau đạt · T `firstCompletion=false` |
| AT-39 | FR-040 | Lượt kiểm tra 25 giờ · T `EXPIRED`, `finish` → 422 |
| AT-40 | FR-041…FR-045 (FE) | Vitest + Testing Library: lọc HSK; bước kiểm tra khoá có lý do; nghe lại tối đa 3; xác nhận nộp trong trang; tải lại trang khôi phục lượt |

**Sửa 2026-10-30 (leader duyệt):** SC-002, AT-01/AT-02 và hai GET §13 được đồng bộ với Q-T03-01…03; các response 404 của thao tác ghi session trong bảng §13/OpenAPI sẽ được bỏ khi triển khai T-04…T-06, theo E-R1 mới.

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)

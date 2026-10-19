# Feature Specification: Ngữ pháp HSK

**Feature:** `004-grammar`
**Feature tree:** 1.4 Ngữ pháp · **Module:** `learning` (package `learning.grammar`)
**Use case:** UC-023 (học điểm ngữ pháp) · UC-024 (ôn theo FSRS) **hoãn** · **Actor:** `LEARNER`
**Màn hình:** SCR-017 Grammar List · SCR-018 Grammar Lesson
**Scope:** MVP · **Priority:** P1

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | DRAFT — chờ cả nhóm duyệt |
| **Phụ thuộc** | Spec 001 · Spec 002 (`GRAMMAR`, `GRAMMAR_EXERCISE`) · Spec 003 §0 (bộ máy lượt luyện) |
| **Quyết định chung** | Brainstorm 2026-10-19: D-01, D-02, D-03, D-04, D-06 |

> **Codex đọc:** `AGENTS.md` → spec 003 §0 → file này → `contracts/openapi-grammar.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (lý do) |
|---|---|
| Danh sách điểm ngữ pháp theo cấp HSK (1–6, nhóm 7–9) | Lịch ôn FSRS, "Ôn hôm nay" (UC-024) — cây tri thức |
| Bài học: cấu trúc, giải thích tiếng Việt, ví dụ, lỗi thường gặp | Tạo/sửa điểm ngữ pháp trên giao diện — chỉ qua nhập file (spec 002) |
| Bài tập ngắn 3–5 câu chọn đáp án, chấm ở server | Dạng bài tập điền tự do, sắp xếp câu — mẫu v0 chỉ có trắc nghiệm |
| Ghi nhận "đã học", điểm bài tập cao nhất | `NO_KNOWLEDGE_POINT_LINKED`, rating Again/Hard/Good/Easy — cây tri thức |

**Không thay đổi DB.** Dữ liệu đọc từ `lexemes` (`kind='GRAMMAR'`, `extra` theo spec 002 §4.4–4.5).

**Nguồn lượt mới** (thêm vào `PracticeSource` của spec 003): `GRAMMAR_LESSON` — `kind=PRACTICE`, `ref_id` = id điểm ngữ pháp, `IMMEDIATE`, hạn 2 giờ.
**Loại câu mới:** `LESSON_READ` (`gradable=false`, `skill=GRAMMAR`) · `GRAMMAR_CHOICE` (`gradable=true`, `skill=GRAMMAR`).

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-05** | Demo luồng học | Ngữ pháp là một trong 4 chế độ học chính |

**Vấn đề:** UC-023 gắn việc học với FSRS và `knowledge_points` — đều thuộc cây tri thức. Spec này giữ phần học + bài tập,
ghi bằng chứng (thời gian đọc, kết quả từng câu) để cây tính rating sau.

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Người học mở được bài của 593 điểm ngữ pháp (khi đã nhập) | 100% |
| **SC-002** | Bài tập chấm ở server, không lộ đáp án trước khi trả lời | 100% |

---

## 2 · User Stories

### US-1 · Học một điểm ngữ pháp (P1)
**Given** tôi chọn HSK 2 → "是……的"
**When** tôi đọc cấu trúc, giải thích, ví dụ rồi bấm "Đã hiểu"
**Then** tôi làm 3–5 câu trắc nghiệm, mỗi câu biết đúng/sai và lời giải ngay
**And** cuối bài tôi thấy "Đúng 4/5 — đã ghi nhận bài học"

### US-2 · Bỏ qua bài tập (P2)
**Given** tôi đã đọc bài
**When** tôi bấm "Bỏ qua bài tập"
**Then** bài học vẫn được ghi nhận là đã học, nhưng không có điểm

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Điểm ngữ pháp chưa có bài tập | "Đã hiểu" ghi nhận bài học ngay, hiện "Điểm này chưa có bài tập" |
| E2 | Đọc 2 giây một bài giải thích dài | Vẫn ghi nhận, `meta.readTooFast = true` (cây dùng để giới hạn rating, UC-023 A3) |
| E3 | Học lại điểm đã học | Cho phép, tạo lượt mới (không 409 như UC-023 vì chưa có luồng ôn UC-024) |
| E4 | Có 12 bài tập | Mỗi lượt lấy ngẫu nhiên 5 |
| E5 | Mở lại trang giữa lúc làm bài tập | Khôi phục lượt đang làm (`/active`) |

---

## 3 · Functional Requirements

### Nhóm 1 — Danh sách (FR-001…FR-004) · SCR-017

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `GET /api/grammar?hskLevel=` SHALL nhận 0…3 giá trị `hskLevel` (1–9, lặp tham số, vd `hskLevel=7&hskLevel=8&hskLevel=9`); giá trị ngoài 1–9 → **422** `VALIDATION_FAILED`. Không có tham số → mọi cấp |
| **FR-002** | Mỗi phần tử SHALL có `{id, text, titleVi, hskLevel, structure, exerciseCount, learned, bestPercent, lastStudiedAt}`, sắp `hsk_level`, rồi `id` (thứ tự nhập) |
| **FR-003** | `learned` := người gọi có ít nhất một lượt `GRAMMAR_LESSON` `SUBMITTED` cho điểm đó; `bestPercent` := `max(floor(score*100/max_score))` trên các lượt đó có `max_score > 0`, `null` nếu không có; `lastStudiedAt` := `submitted_at` mới nhất. Tính bằng **một truy vấn tổng hợp** cho cả danh sách |
| **FR-004** | SCR-017 SHALL có 7 tab: "HSK 1" … "HSK 6", "HSK 7–9" (gửi `hskLevel=7,8,9`), mặc định tab đầu tiên có dữ liệu; mỗi tab hiện số điểm; mỗi dòng: chữ Hán `text`, `titleVi`, nhãn "Đã học" + điểm cao nhất nếu có. Tab không có dữ liệu: "Chưa có điểm ngữ pháp cho cấp này" |

### Nhóm 2 — Bài học (FR-005…FR-008) · SCR-018

| Mã | Yêu cầu |
|---|---|
| **FR-005** | `GET /api/grammar/{id}` SHALL trả `{id, text, titleVi, hskLevel, structure, explanation, commonMistake, examples:[{cn, pinyin, vi}], exerciseCount, learned, bestPercent, lastStudiedAt}` — **không** kèm bài tập; id không phải `GRAMMAR` → **404** `GRAMMAR_POINT_NOT_FOUND` |
| **FR-006** | SCR-018 SHALL hiện: cấp HSK, chữ + tên tiếng Việt, khung **Cấu trúc**, **Giải thích**, **Ví dụ** (chữ Hán, pinyin, nghĩa), **Lỗi thường gặp** (ẩn nếu rỗng), nút "Đã hiểu" |
| **FR-007** | Frontend SHALL đo thời gian đọc từ lúc nội dung hiển thị tới lúc bấm "Đã hiểu" (`performance.now()`), gửi làm `readDurationMs` |
| **FR-008** | SCR-018 **không** hiện "Lần ôn tiếp theo" (`nextReviewAt` thuộc UC-024 — chốt open issue: ẩn tới khi có cây) |

### Nhóm 3 — Bài tập (FR-009…FR-016)

| Mã | Yêu cầu |
|---|---|
| **FR-009** | `POST /api/grammar/{id}/lessons` `{readDurationMs}` (0…3 600 000) SHALL tạo lượt `GRAMMAR_LESSON` gồm: câu 1 `LESSON_READ` **ghi sẵn** (`preRecorded`, `answer='UNDERSTOOD'`, `duration_ms = readDurationMs`), các câu sau `GRAMMAR_CHOICE` |
| **FR-010** | `meta.readTooFast = true` WHEN `readDurationMs < 5000` **và** độ dài `extra.explanation` ≥ 300 ký tự (UC-023 A3) |
| **FR-011** | Số câu `GRAMMAR_CHOICE` = `min(5, exerciseCount)`; nhiều hơn 5 → chọn ngẫu nhiên 5 (E4); thứ tự câu ngẫu nhiên |
| **FR-012** | Mỗi `GRAMMAR_CHOICE`: `prompt.sentence = stem`, `options` = phương án đã nhập **trộn lại** và gán lại khoá `A…` (spec 003 G-6), `answer` = khoá mới của phương án đúng, `explanation` = `explanation_vi`; `minDurationMs = 800` |
| **FR-013** | WHEN `exerciseCount = 0`, THE system SHALL tạo lượt chỉ có `LESSON_READ` rồi **kết thúc ngay** trong cùng request, trả `201` kèm `finish` (E1) |
| **FR-014** | WHEN có lượt `GRAMMAR_LESSON` `IN_PROGRESS` chưa hết hạn của cùng điểm, THE system SHALL trả lại lượt đó (`200`) thay vì tạo mới; `readDurationMs` của request bị bỏ qua |
| **FR-015** | "Bỏ qua bài tập" SHALL gọi `finish` khi chưa trả lời hết; câu chưa trả lời không ghi (lượt `IMMEDIATE`, spec 003 E-F4), lượt vẫn `SUBMITTED` → `learned = true` (US-2) |
| **FR-016** | `GrammarLessonHandler.onFinish` SHALL trả `extra = {exercisesTotal, answered, correct, percent, skippedExercises, firstTimeLearned}`; `skippedExercises = answered < exercisesTotal` |

### Nhóm 4 — Giao diện bài tập (FR-017…FR-018)

| Mã | Yêu cầu |
|---|---|
| **FR-017** | Màn bài tập SHALL dùng component chung spec 003 T-13 (`ChoiceQuestion`); hiện "Câu {i}/{n}", lời giải sau khi chấm, nút "Bỏ qua bài tập" (bước xác nhận trong trang) |
| **FR-018** | Kết thúc SHALL hiện "Đúng {correct}/{total}" (hoặc "Đã ghi nhận bài học" khi bỏ qua/không có bài tập), nút "Quay lại danh sách" và "Học điểm tiếp theo" (điểm kế tiếp cùng cấp theo FR-002, ẩn nếu là điểm cuối) |

---

## 4 · Data Model

Đọc `learning.lexemes` (`kind='GRAMMAR'`). `extra` theo spec 002:

```json
{ "structure": "S + 是 + … + 的", "explanation": "…", "commonMistake": "…",
  "examples": [ { "cn": "我是昨天来的。", "pinyin": "Wǒ shì zuótiān lái de.", "vi": "Tôi đến hôm qua." } ],
  "exercises": [ { "no": 1, "stem": "他___坐飞机来的。", "options": [ {"key":"A","text":"是"}, {"key":"B","text":"在"} ],
                   "answerKey": "A", "explanation": "…" } ] }
```

Ghi: `attempts`, `attempt_items`, `study_events` qua bộ máy spec 003. Không thay đổi DB.

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `GRAMMAR_POINT_NOT_FOUND` | 404 | id không tồn tại hoặc không phải `GRAMMAR` | FR-005, FR-009 |
| `VALIDATION_FAILED` | 422 | `hskLevel` sai, quá 3 giá trị, `readDurationMs` ngoài khoảng | FR-001, FR-009 |
| Lỗi bộ máy (`SESSION_*`, `OPTION_NOT_IN_ITEM`, `ANSWER_ALREADY_SUBMITTED`, `RATE_LIMITED`…) | | Theo spec 003 §5 | |

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **BUS-09** | Bài tập chấm ở server qua bộ máy; `INVALID_EXERCISE_RESULT` của UC-023 = `ITEM_NOT_IN_SESSION`/`OPTION_NOT_IN_ITEM` |
| **AC-09** | `extra` chỉ đọc |
| Package | `learning.grammar` (`GrammarQueryService`, `GrammarLessonService`, `GrammarLessonHandler`, `web.GrammarController`) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `GET /api/grammar` (≤ 600 điểm) | < 300 ms p95 |
| `NFR-P02` | `GET /api/grammar/{id}` | < 100 ms p95 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| FSRS, `next_review_at`, rating | Không |
| `knowledge_points`, `questions` | Không |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | UC-024 (ôn FSRS) hoãn sang cây tri thức | Chốt |
| Q2 | Học lại điểm đã học tạo lượt mới, không 409 | Chốt (E3) |
| Q3 | Bài tập chỉ trắc nghiệm 2–4 phương án (mẫu v0) | Chốt — thêm dạng khác bằng addendum spec 002 + 004 |
| Q4 | Nhóm "HSK 7–9" gộp 3 cấp | Chốt — chốt open issue SCR-017 |

---

## 10 · Móc nối cây tri thức

| Chỗ | Bây giờ | Khi có cây |
|---|---|---|
| Rating FSRS (UC-023 bước 8) | Lưu bằng chứng: `LESSON_READ.meta.readTooFast`, kết quả từng câu | Listener `SessionFinishedEvent` (`GRAMMAR_LESSON`) tính rating: bỏ bài tập → Hard; đọc nhanh → tối đa Good |
| `nextReviewAt` trên SCR-018 | Ẩn | Hiện từ `user_progress` |
| "Ôn hôm nay" UC-024 | Không có | Spec ôn tập |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Người học bấm "Đã hiểu" ngay không đọc | Đã ghi `readTooFast`; chưa có mastery nên không có gì để "farm" |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-017 Grammar List | `/learn/grammar` (tab theo `?level=1…6` hoặc `7-9`) | `LEARNER` | `GET /api/grammar` | `screen-fields.md` dòng 8315 |
| SCR-018 Grammar Lesson | `/learn/grammar/:id` | `LEARNER` | `GET /api/grammar/{id}`, `POST …/lessons`, bộ máy spec 003 | dòng 8635 |

| Open issue | Chốt |
|---|---|
| SCR-017: API/route, phân trang, nhóm 7–9 chưa có | Route trên; không phân trang (≤ 600 dòng); nhóm 7–9 = 3 cấp gộp |
| SCR-018: `nextReviewAt` | Ẩn (FR-008) |

Trạng thái: Loading (skeleton danh sách/nội dung) · Empty (FR-004) · Error (thông báo + "Thử lại").

---

## 13 · API Contract

[`contracts/openapi-grammar.yaml`](contracts/openapi-grammar.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/grammar?hskLevel` | 200 `[GrammarSummary]` | 422 |
| `GET /api/grammar/{id}` | 200 `GrammarLesson` | 404 |
| `POST /api/grammar/{id}/lessons` | 201 `SessionView` (hoặc `{session, finish}` khi không có bài tập) · 200 tiếp tục | 404, 422, 429 |
| Trả lời / kết thúc | Bộ máy spec 003 | |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | `hskLevel=7&hskLevel=8&hskLevel=9` · T chỉ cấp 7–9; `hskLevel=0` · T 422; 4 giá trị · T 422 |
| AT-02 | FR-002, FR-003 | G học điểm X 2 lần (60%, 80%) · T `learned=true`, `bestPercent=80`, `lastStudiedAt` = lần sau |
| AT-03 | FR-005 | T response không có `exercises`, không có `answerKey` (assert chuỗi JSON) |
| AT-04 | FR-005 | id của một `WORD` · T 404 |
| AT-05 | FR-009 | T câu 1 `LESSON_READ` có `attempt_items` ngay khi tạo lượt, `is_correct NULL` |
| AT-06 | FR-010, E2 | `readDurationMs=2000`, giải thích 500 ký tự · T `readTooFast=true`; giải thích 100 ký tự · T không có cờ |
| AT-07 | FR-011, E4 | 12 bài tập · T 5 câu, 100 lượt phủ ≥ 10/12 bài |
| AT-08 | FR-012 | T khoá đáp án đúng sau trộn khớp phương án đúng gốc (100 lượt) |
| AT-09 | FR-013, E1 | 0 bài tập · T lượt `SUBMITTED` ngay, `learned=true`, `score NULL` |
| AT-10 | FR-014, E5 | Gọi `lessons` 2 lần · T cùng `sessionId` |
| AT-11 | FR-015, US-2 | Trả lời 1/5 rồi `finish` · T `SUBMITTED`, `maxScore=1`, `skippedExercises=true` |
| AT-12 | FR-016 | 4/5 đúng · T `percent=80`, `firstTimeLearned=true`; lần 2 · T `false` |
| AT-13 | FR-004, FR-017, FR-018 (FE) | Vitest: 7 tab; tab rỗng hiện thông báo; bỏ qua cần xác nhận trong trang; "Học điểm tiếp theo" ẩn ở điểm cuối |

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)

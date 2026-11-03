# Feature Specification: Mastery, biên học tập và lịch ôn mục từ

**Feature:** `011-mastery-learning-frontier`
**Feature tree:** 3.1 Mastery · 3.2 (phần biên học tập theo cây năng lực) · 3.4 (phần cây năng lực của tiến độ) · **Module:** `learning` (package `learning.mastery`, `learning.review`)
**Use case:** UC-042 · UC-043 · UC-044 · UC-047 (phần gợi ý theo nút) · UC-051 (phần cây) · **Actor:** `LEARNER`
**Màn hình:** SCR-019 Learning Progress · SCR-010 Learning Dashboard (khối gợi ý + số mục đến hạn) · SCR-013 Due Review. **Không thêm màn**
**Scope:** MVP · **Priority:** P0

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (RFC đợt cuối R1–R12 đã đồng thuận) |
| **Locked on** | 2026-11-03 |
| **Phụ thuộc** | Spec 001 · Spec 003 §0 (engine, sự kiện §0.9) · **Spec 010** (`KnowledgeTreeService` FR-024/FR-025, V5) · **Spec 012 §0.2** (`questionId` trong engine, 3 nguồn mới) |
| **Quyết định chung** | `questions.md` mục 2026-11-03 (Q1–Q8, F1–F4) · plan `261102-2104` phase 3 · Cây Năng Lực v2.0 (MP-1.0) |
| **Điều kiện APPROVED** | Spec 010 APPROVED (RFC Q1 — bổ sung Y2, Y3, Y4 §9) |

> **Codex đọc:** `AGENTS.md` → spec 003 §0 → spec 010 FR-024/FR-025 → spec 012 §0.2, §10 → file này → `contracts/openapi-mastery.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Mastery MP-1.0 theo nút KID, thang 0–100, 6 trạng thái (Q8, Q5) | Mastery "chính thức", giá trị gia tăng giáo viên, nhiều phiên bản cây |
| Minh chứng lấy từ `attempt_items` có sẵn, **0 bảng mới** (Q1) | Bảng `evidence`/`mastery` riêng (sửa AC-04) |
| Biên học tập: gợi ý + tô màu, tiên quyết đạt củng cố trở lên (Q3) | Khoá nội dung theo cây; cổng 90% chủ đề (`BUS-05`) giữ nguyên ở 003 |
| Gợi ý ôn ≤ 5 nút có lý do; luyện qua endpoint 012 | AI sinh bài theo nút yếu (feature 3.3) |
| Lịch ôn mục từ FSRS-5 (`BUS-04`) + SCR-013 (Q4, F1–F4) | Lịch ôn sổ tay (`FLASHCARD`, spec 014) |
| SCR-019 cây màu + chi tiết KID; SCR-010 khối gợi ý + số mục đến hạn (Q7) | Chuỗi ngày học, biểu đồ 7/30/90, bản đồ kỹ năng, báo cáo tuần, khối lượt/credit của SCR-010 |
| `MasteryReader` cho 012 FR-014 | Nhắc học (UC-054/055) |

**Không ghi minh chứng và không vào FSRS:** `CHAR_WRITING`, `WRITING_CHALLENGE` (`BUS-16` chưa chốt — `AGENTS.md` §12), `PRON_STAGE` (chưa nối KID), câu không có đúng/sai.

### 0.1 · Thay đổi DB — `V6__mastery_progress.sql` (Q1)

**Không thêm bảng** (giữ `AC-04`). Mở rộng `user_progress` (`database.md` §4.9) — bảng chưa có dòng `KNOWLEDGE_POINT` nào (chỉ `PRON_STAGE` của 005):

```sql
-- Spec 011 section 0.1: MP-1.0 mastery per KID and FSRS per lexeme on user_progress, after V5.

-- 1. LEXEME rows hold the FSRS schedule of a word, character or grammar point
ALTER TABLE learning.user_progress DROP CONSTRAINT user_progress_target_type_check;
ALTER TABLE learning.user_progress ADD CONSTRAINT user_progress_target_type_check
    CHECK (target_type IN ('KNOWLEDGE_POINT','TOPIC','PRON_STAGE','FLASHCARD','VIDEO','LEXEME'));

-- 2. KNOWLEDGE_POINT rows hold MP-1.0 state; evidence stays in attempt_items
ALTER TABLE learning.user_progress
    ADD COLUMN mp_state          VARCHAR(12)
               CHECK (mp_state IN ('notyet','insufficient','review','consolidate','extend')),
    ADD COLUMN confidence        NUMERIC(6,3) CHECK (confidence >= 0),
    ADD COLUMN evidence_item_ids BIGINT[]     NOT NULL DEFAULT '{}';
ALTER TABLE learning.user_progress
    ADD CONSTRAINT ck_prog_mp CHECK (target_type <> 'KNOWLEDGE_POINT' OR mp_state IS NOT NULL);
CREATE INDEX ix_prog_kp_state ON learning.user_progress(user_id, mp_state)
    WHERE target_type = 'KNOWLEDGE_POINT';
```

| Cột (dòng `KNOWLEDGE_POINT`, `target_id = knowledge_points.id`) | Ý nghĩa |
|---|---|
| `mastery` | Giá trị MP-1.0 lúc cập nhật cuối ÷ 100 (`0.533` = 53,3) |
| `confidence` | Σw của cửa sổ lúc cập nhật cuối |
| `mp_state` | Trạng thái lưu (có vùng đệm). `inferred` **không** lưu — suy ra khi đọc (FR-011) |
| `evidence_item_ids` | `attempt_items.id` trong cửa sổ (≤ 8) |
| `next_review_at` | Hạn ôn theo trạng thái (FR-010) |
| `state`, `stability`, `difficulty` | Giữ mặc định, không dùng |

| Cột (dòng `LEXEME`, `target_id = lexemes.id`) | Ý nghĩa |
|---|---|
| `stability`, `difficulty`, `next_review_at`, `review_count`, `lapse_count`, `last_seen_at` | Trạng thái FSRS-5 (FR-019) |
| `mastery` | Độ nhớ R lúc ôn cuối (0–1), chỉ hiển thị |
| `state` | `LEARNING` (không dùng) |

Tên ràng buộc `user_progress_target_type_check` là tên PostgreSQL tự đặt cho CHECK cột ở V1 — T-05 kiểm bằng test Testcontainers trước khi chạy.

### 0.2 · Thay đổi engine (sau spec 012 §0.2 — chỉ **thêm**)

| Thay đổi | Chi tiết |
|---|---|
| `PracticeSource.DUE_REVIEW` | `kind = PRACTICE`, `IMMEDIATE`, hạn 2 giờ, `allowSkip = true`, `maxScoreFromServed = false` |
| `ItemType.FLASHCARD_RATING(gradable = false)` | Thẻ tự đánh giá; đáp án client gửi `AGAIN` \| `HARD` \| `GOOD` \| `EASY` (ghi kiểu `RECORD`) |
| `PracticeApi`, contract 003, `web/features/practice/types.ts` | Đồng bộ enum |

### 0.3 · File dùng chung bị sửa (báo leader trước khi mở PR)

| File | Thay đổi | Task |
|---|---|---|
| `src/main/resources/db/migration/V6__mastery_progress.sql` | §0.1. **Leader chạy V6 lên Neon sau merge** | T-05 |
| `learning/progress/UserProgress.java` | Không sửa — 011 dùng entity riêng `KnowledgeProgress`, `LexemeSchedule` cùng bảng | T-05 |
| `learning/practice/PracticeSource.java`, `ItemType.java`, `learning/api/PracticeApi.java`, `specs/003-…/contracts/openapi-practice-vocab.yaml` | §0.2 | T-12 |
| `shared/config/SecurityConfig.java` | Matcher §12 (`LEARNER`), đặt trước `/api/me/**` không ảnh hưởng | T-09, T-10, T-14 |
| `src/main/resources/application.yaml` | Khối `cnhsk.mastery.*` (§0.4), `cnhsk.fsrs.*` (§0.5) | T-06, T-11 |
| `cnhsk-web/src/features/practice/types.ts` | `DUE_REVIEW`, `FLASHCARD_RATING` | T-01 |
| `cnhsk-web/src/app/routeAccess.ts`, `screenRegistry.ts` | 3 route §12, catalog feature `mastery` | T-01 |
| `docs/reference/database.md` | §4.9 (`user_progress` mới), §11 (V6), §13 (ghi "thêm cột, không xoá") | T-05 |

`ErrorCode.java` **không** thêm mã. `application-test.yaml` không đổi.

### 0.4 · Tham số MP-1.0 (`cnhsk.mastery.*`, Q2)

| Khoá | Mặc định | Nguồn |
|---|---|---|
| `window` / `max-practice-in-window` | 8 / 3 | v2.0 |
| `min-evidence` / `min-distinct-days` / `min-confidence` | 3 / 2 / 1,0 | v2.0 |
| `half-life-days` | 30 | v2.0 |
| `seen-item-days` | 7 (Q6) | v2.0 |
| `review-below` / `extend-from` / `hysteresis` | 60 / 80 / 3 | v2.0 |
| `inferred-min-confidence` / `inferred-from` | 2,0 / 80 (Q5) | v2.0 |
| `due-days` | `review: 3`, `consolidate: 7`, `extend: 14` | v2.0 |
| `context-weights` | `EXAM: 1.0`, `QUIZ: 0.8`, `GRADED: 0.6`, `PRACTICE: 0.5` | v2.0 |
| `form-weights` | `OPEN: 1.0`, `MCQ4: 0.75`, `MCQ3: 0.67`, `BINARY: 0.5` | v2.0 |
| `role-weights` | `PRIMARY: 1.0`, `SECONDARY: 0.5`, `ERROR: 1.0` | v2.0 |

Thiếu khoá hoặc giá trị ngoài miền hợp lệ → ứng dụng **không khởi động** (`@Validated` `@ConfigurationProperties`).

### 0.5 · Tham số FSRS-5 (`cnhsk.fsrs.*`, F1)

| Khoá | Mặc định |
|---|---|
| `weights` (19 số, FSRS-5 mặc định) | `0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621` |
| `desired-retention` | 0.9 |
| `min-interval-days` / `max-interval-days` | 1 / 365 (BR-044-5) |
| `session-max-items` | 50 (SCR-013) |

`weights` khác 19 phần tử hoặc thiếu → ứng dụng không khởi động (BR-044-7). Leader chốt dùng nguyên 19 số trên (OQ-2, 2026-11-03).

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-01** | Biết chính xác yếu điểm kiến thức nào | Mastery từng nút KID từ mọi bài có chấm |
| **BG-03** | Học đúng thứ tự | Biên học tập theo tiên quyết, gợi ý ≤ 5 nút mỗi ngày |
| **BG-06** | Nhớ lâu | Lịch ôn mục từ FSRS (`BUS-04`) |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Ví dụ Quốc Bảo NP3-004 của v2.0 | 53,3 (tin cậy 2,41) → 61,1 |
| **SC-002** | Tính lại toàn bộ (FR-013) = giá trị đang lưu | 100% |
| **SC-003** | Người mới: mọi nút `notyet`, không có nút "cần ôn" | 100% |
| **SC-004** | `finish` một đề 200 câu chậm thêm do mastery | < 300 ms p95 |

---

## 2 · User Stories

### US-1 · Thấy mình vững/yếu nút nào (P1)
Người học mở SCR-019, thấy cây năng lực theo miền và cấp, mỗi nút một màu theo trạng thái; bấm nút để xem điểm, độ tin cậy, các minh chứng gần nhất, tiên quyết.

### US-2 · Biết hôm nay học gì (P1)
SCR-010 hiện tối đa 5 nút nên luyện kèm lý do ("Cần ôn: 42/100", "Đến hạn củng cố", "Nút mới đã đủ tiên quyết"); bấm "Luyện" sang SCR-023 (012).

### US-3 · Ôn từ đến hạn (P1)
SCR-010 hiện "12 mục đến hạn"; SCR-013 cho ôn tối đa 50 mục, mục quá hạn lâu nhất trước, câu chọn nghĩa chấm ngay, thẻ ngữ pháp tự đánh giá; dừng giữa chừng không mất câu đã ôn.

### Edge cases

| # | Tình huống | Xử lý |
|---|---|---|
| E1 | Câu chưa gắn KID / từ chưa gắn nút | Không có minh chứng cho mục đó; luồng chính tiếp tục; log `WARN` (BR-042-7) |
| E2 | Lượt bị đánh dấu nghi gian lận | Mọi câu của lượt không thành minh chứng, không đổi FSRS |
| E3 | Lượt bỏ dở / hết hạn (trừ `DUE_REVIEW`) | Không minh chứng (chỉ xử lý khi `finish`) |
| E4 | Câu bỏ qua, thiếu, lỗi âm thanh | Không minh chứng, không đổi FSRS (SCR-013 "skip không tính sai") |
| E5 | Làm đúng lại cùng câu trong 7 ngày | Lần sau không thành minh chứng (Q6) |
| E6 | Câu bị lưu trữ trước khi lượt nộp | Câu đó không thành minh chứng |
| E7 | Hai lượt cùng nộp, cùng KID | Khoá dòng `user_progress` → cập nhật tuần tự, không mất minh chứng |
| E8 | Lỗi khi cập nhật mastery | `finish` rollback toàn bộ, lượt giữ `IN_PROGRESS` (AC-10, BR-035 T8) |
| E9 | Nhãn câu đổi sau khi đã có minh chứng | Lần cập nhật sau tính theo nhãn mới; `rebuild` cho kết quả theo nhãn mới (§11) |
| E10 | Lâu không học | Giá trị giảm theo bán rã khi đọc; tin cậy < 1,0 → `insufficient` |

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL cho mọi endpoint §13 chỉ `LEARNER`; dữ liệu luôn của **người gọi** (không có tham số `userId` — BR-051-1). Sai role → **403** `FORBIDDEN`. Ghi qua cookie cần CSRF. Lượt ôn không thuộc người gọi → **403** `ATTEMPT_NOT_OWNED` (engine) |

### Nhóm 1 — Engine (FR-002)

| Mã | Yêu cầu |
|---|---|
| **FR-002** | THE system SHALL thêm `DUE_REVIEW` và `FLASHCARD_RATING` theo §0.2 mà không đổi hành vi nguồn cũ (toàn bộ test 003–008, 012 xanh) |

### Nhóm 2 — Lưu (FR-003)

| Mã | Yêu cầu |
|---|---|
| **FR-003** | V6 §0.1 SHALL chạy trên DB đã có V1–V5; entity `KnowledgeProgress` (dòng `KNOWLEDGE_POINT`) và `LexemeSchedule` (dòng `LEXEME`) map cùng bảng `user_progress` với `@Where`/truy vấn lọc `target_type`; entity 005 (`UserProgress`) khởi động không đổi (`ddl-auto: validate`) |

### Nhóm 3 — Minh chứng (FR-004…FR-008)

| Mã | Yêu cầu |
|---|---|
| **FR-004** | `MasteryListener` SHALL nghe `SessionFinishedEvent` (003 §0.9) **đồng bộ trong transaction** của `finish` và xử lý mọi `attempt_items` của lượt (nguồn `DUE_REVIEW` bỏ qua ở đây — xử lý theo FR-023). Lỗi bất kỳ → ném lại để engine rollback (E8). Không sửa `PracticeService` |
| **FR-005** | Bối cảnh theo nguồn: `MOCK_EXAM` → `EXAM`; `PLACEMENT`, `TOPIC_TEST` → `QUIZ`; `TOPIC_PRACTICE`, `GRAMMAR_LESSON`, `CHAR_RECOGNITION`, `TARGETED_PRACTICE`, `GAME_ROUND`, `DUE_REVIEW` → `PRACTICE`; `TOPIC_STUDY`, `CHAR_WRITING`, `WRITING_CHALLENGE`, `PRON_STAGE` → **không** có minh chứng. Nguồn mới chưa khai báo → không minh chứng + log `WARN` |
| **FR-006** | THE system SHALL phân bổ mỗi câu thành minh chứng: **(a) câu có `question_id`** — nhãn lấy bằng **một** lời gọi `kidsOfQuestions` cho cả lượt: đúng → KID chính `s=100` vai trò `PRIMARY` và mỗi KID phụ `s=100` vai trò `SECONDARY`; sai và phương án đã chọn có `errorKid` → chỉ KID đó `s=0` vai trò `ERROR`; sai không mã lỗi → KID chính `s=0` `PRIMARY`. **(b) câu chỉ có `lexeme_id`** — KID = `kidOfLexeme`, không có thì với nguồn `TOPIC_*` dùng `kidOfTopic(refId)`; đúng `s=100`, sai `s=0`, vai trò `PRIMARY`. Không tìm được KID → bỏ (E1) |
| **FR-007** | `w_static` = `context-weights[bối cảnh]` × `form-weights[dạng]` × `role-weights[vai trò]`; dạng theo số phương án của câu đã phát: ≥ 4 → `MCQ4`, 3 → `MCQ3`, 2 → `BINARY`; không có phương án (`PINYIN_INPUT`) → `OPEN` |
| **FR-008** | Câu chỉ thành minh chứng khi: lượt `suspicious = false`; `is_correct` khác `NULL`; `meta` không có `skipped`, `missing`, `audioFailed`, `suspicious`; câu hỏi (nếu có) đang `APPROVED`; **không** có câu cùng `question_id` (hoặc cùng `lexeme_id` + `item_type` với câu không có `question_id`) của người đó được trả lời **đúng** trong `seen-item-days` ngày trước `answered_at` (E5) |

### Nhóm 4 — Tính mastery (FR-009…FR-013)

| Mã | Yêu cầu |
|---|---|
| **FR-009** | `MasteryCalculator` SHALL là lớp thuần (nhận `today`): sắp minh chứng mới nhất trước (`answered_at`, rồi `attempt_items.id` giảm dần); lấy tối đa `window`, bỏ minh chứng `PRACTICE` thứ `max-practice-in-window + 1` trở đi; `w = w_static × 0,5^(ngày/half-life)` với `ngày` = giờ chênh ÷ 24; `value = Σ(w·s)/Σw` (làm tròn 1 chữ số khi trả ra), `confidence = Σw`; cửa sổ rỗng → `value = null` |
| **FR-010** | Trạng thái SHALL tính: 0 minh chứng → `notyet`; "đủ" = số minh chứng ≥ `min-evidence`, số ngày Việt Nam khác nhau ≥ `min-distinct-days`, `confidence ≥ min-confidence`; chưa đủ → `insufficient`; đủ → so với trạng thái trước: lên `consolidate` khi `value ≥ 60`, lên `extend` khi `value ≥ 80`; từ `extend` xuống `consolidate` khi `value < 77`; từ `consolidate` xuống `review` khi `value < 57`; từ `insufficient`/`notyet` dùng ngưỡng thẳng 60/80. Hạn ôn `next_review_at` = lúc cập nhật + `due-days[state]`; `notyet`/`insufficient` → `NULL` |
| **FR-011** | WHEN đọc, THE system SHALL tính lại `value`, `confidence` với `today` từ `evidence_item_ids` (E10: tin cậy < `min-confidence` → hiển thị `insufficient`) và suy `inferred` cho nút `notyet` có **ít nhất một** nút nhận nó làm `PREREQ` với `value ≥ inferred-from` và `confidence ≥ inferred-min-confidence` (Q5). `inferred` không ghi vào DB |
| **FR-012** | WHEN có minh chứng mới cho KID, THE system SHALL: tạo dòng `KNOWLEDGE_POINT` nếu chưa có (`INSERT … ON CONFLICT DO NOTHING`), khoá dòng `FOR UPDATE` (E7), lấy ứng viên = minh chứng trong `evidence_item_ids` (phân bổ lại theo FR-006/FR-007) + minh chứng mới, chạy FR-009/FR-010, ghi `mastery`, `confidence`, `mp_state`, `evidence_item_ids`, `next_review_at`, `updated_at` |
| **FR-013** | `MasteryRebuilder.rebuild(userId)` SHALL xoá trạng thái `KNOWLEDGE_POINT` của người đó rồi phát lại mọi lượt `SUBMITTED` (và câu `DUE_REVIEW`) theo thời gian qua FR-006…FR-012; kết quả phải trùng trạng thái tính tăng dần (SC-002). Chỉ gọi trong test và bảo trì (không có endpoint) |

### Nhóm 5 — Biên học tập và gợi ý (FR-014…FR-015)

| Mã | Yêu cầu |
|---|---|
| **FR-014** | Nút **trên biên học tập** khi trạng thái (FR-011) ∉ {`extend`, `inferred`} **và** mọi tiên quyết trực tiếp (`PREREQ`) ở `consolidate`, `extend` hoặc `inferred` (Q3); nút không có tiên quyết luôn đủ điều kiện. Biên chỉ dùng để gợi ý và tô màu — **không** chặn truy cập nội dung |
| **FR-015** | `GET /api/mastery/recommendations` SHALL trả ≤ 5 nút, mỗi nút `{kid, nameVi, kind, reason, value}`, chỉ gồm nút có ≥ 1 câu dùng được (`approvedQuestionIds`): (1) `REVIEW` — nút `review`, giá trị tăng dần; trước mỗi nút này chèn tiên quyết trực tiếp đang `review` nếu có (`kind = PREREQ`); (2) `CONSOLIDATE` — nút `consolidate` có `next_review_at ≤ now`; (3) `NEW` — nút trên biên đang `notyet`/`insufficient`, cấp HSK thấp trước rồi `kid`. Không trùng nút. `reason` sinh từ mẫu câu (không gọi AI). Người mới → chỉ có `NEW` |

### Nhóm 6 — API đọc (FR-016…FR-018)

| Mã | Yêu cầu |
|---|---|
| **FR-016** | `GET /api/mastery` SHALL trả `{nodes:[{kid, value, confidence, evidenceCount, state, frontier, dueAt}]}` cho **mọi** nút của cây (nút chưa có dòng → `notyet`, `value = null` — BR-053-4), cùng thứ tự với 010 FR-015, đọc bằng ≤ 3 truy vấn (dòng tiến độ, `attempt_items` của mọi cửa sổ, cây) |
| **FR-017** | `GET /api/mastery/nodes/{kid}` SHALL trả nút (FR-016) + `prereqs:[{kid, nameVi, state}]` + `evidence:[{answeredAt, source, context, form, role, score, weight}]` (cửa sổ, mới nhất trước) + `approvedQuestionCount`. KID không có → **404** `KNOWLEDGE_NODE_NOT_FOUND` (010) |
| **FR-018** | `learning.mastery.MasteryReader.valuesOf(long userId, Set<String> kids) → Map<String, MasteryView(Integer value, String state)>` SHALL trả cho mọi KID yêu cầu (KID không có minh chứng → `value = null`, `state = notyet`; `value` làm tròn số nguyên) — giao diện spec 012 FR-014 dùng; chữ ký khoá khi T-09 merge |

### Nhóm 7 — Lịch ôn mục từ FSRS (FR-019…FR-023)

| Mã | Yêu cầu |
|---|---|
| **FR-019** | `FsrsScheduler` SHALL là lớp thuần cài FSRS-5 với §0.5: rating `AGAIN=1, HARD=2, GOOD=3, EASY=4`; `R(t,S) = (1 + 19/81·t/S)^−0,5`; lần đầu `S = w[G−1]`, `D = clamp(w4 − e^(w5·(G−1)) + 1, 1, 10)`; ôn cùng ngày (`t < 1`) `S' = S·e^(w17·(G−3+w18))`; nhớ được (`G ≥ 2`) `S' = S·(1 + e^w8·(11−D)·S^−w9·(e^(w10·(1−R)) − 1)·(G=2 ? w15 : 1)·(G=4 ? w16 : 1))`; quên (`G=1`) `S' = min(S, w11·D^−w12·((S+1)^w13 − 1)·e^(w14·(1−R)))`, `lapse_count + 1`; `D' = w7·D0(4) + (1−w7)·(D + (−w6·(G−3))·(10−D)/9)`, kẹp 1–10; khoảng ôn = `clamp(round(S'·(0,9^(1/−0,5) − 1)·81/19), 1, 365)` ngày (với retention 0,9 bằng `round(S')`). Thời gian tính UTC (BR-044-6) |
| **FR-020** | `ReviewListener` SHALL, ở cùng `SessionFinishedEvent` (FR-004), cập nhật FSRS cho mỗi `lexeme_id` của lượt (nguồn theo FR-005 trừ `DUE_REVIEW`, cộng `TOPIC_STUDY`): chưa có dòng `LEXEME` → tạo với lần ôn đầu (`TOPIC_STUDY` `LEARNED` → `GOOD`, `KNOWN` → `EASY`; câu có chấm: đúng → `GOOD`, sai → `AGAIN`) (F3, BR-044-4); đã có → một lần ôn mỗi lượt mỗi mục: có câu sai → `AGAIN`, ngược lại `GOOD` (F2). Câu không hợp lệ theo FR-008 (trừ luật 7 ngày) bị bỏ |
| **FR-021** | `GET /api/reviews/summary` SHALL trả `{dueCount, nextDueAt}`: `dueCount` = dòng `LEXEME` của người gọi có `next_review_at ≤ now`; `nextDueAt` = hạn sớm nhất còn lại hoặc `null` |
| **FR-022** | `POST /api/reviews/sessions` body `{ahead?: boolean}` SHALL: có lượt `DUE_REVIEW` dở còn hạn → **200** lượt đó; chọn ≤ `session-max-items` mục đến hạn, quá hạn lâu nhất trước (`ahead = true` → lấy cả mục chưa đến hạn theo hạn sớm nhất — "ôn trước"); không có mục → **200** `{session: null, reason: "NOTHING_DUE"}`; mỗi mục thành **một** câu: `WORD` → `MEANING_CHOICE`, `CHAR` → `CHAR_MEANING_CHOICE` (4 phương án qua `ChoiceItemFactory`), không đủ nhiễu hoặc `GRAMMAR` → `FLASHCARD_RATING` (prompt có chữ + nghĩa); tạo lượt `DUE_REVIEW` → **201** `SessionView` |
| **FR-023** | `ReviewHandler` SHALL: `validateAnswer` của `FLASHCARD_RATING` chỉ nhận 4 giá trị (khác → **422** `VALIDATION_FAILED`); nghe `ItemGradedEvent` của `DUE_REVIEW` (cùng transaction trả lời — "lưu ngay từng câu"): câu chọn → FSRS `GOOD`/`AGAIN` + minh chứng `PRACTICE` theo FR-006…FR-012 (F4); thẻ tự đánh giá → FSRS theo rating, **không** minh chứng (`BUS-09`); bỏ qua → không đổi gì |

### Nhóm 8 — Giao diện (FR-024…FR-026)

| Mã | Yêu cầu |
|---|---|
| **FR-024** | SCR-019 SHALL hiện cây theo 6 miền (cột) × cấp HSK 1–3 (hàng; giao diện chỉ 1–3), mỗi nút một ô màu: xám `notyet`, đỏ `insufficient`/`review`, vàng `consolidate`, xanh `extend`, xanh nhạt `inferred`; nút trên biên có viền nhấn; nhãn chữ trạng thái luôn kèm màu ("Chưa học", "Chưa đủ minh chứng", "Cần ôn", "Củng cố", "Vững", "Suy ra vững"). Chú giải ngưỡng: vững ≥ 80. Bấm nút → bảng chi tiết (FR-017): điểm, độ tin cậy, số minh chứng, danh sách minh chứng, tiên quyết, nút "Luyện nút này" → SCR-023 (012). Người mới: thông báo "Bạn chưa có minh chứng — hãy làm bài kiểm tra trình độ hoặc luyện một chủ đề" + liên kết SCR-020. Tên miền tạm hiện mã (010 OQ-1) |
| **FR-025** | SCR-010 SHALL có khối "Gợi ý hôm nay" (FR-015: tên nút, lý do, "Luyện") và khối "Ôn tập" (FR-021: "{n} mục đến hạn", "Ôn ngay" → SCR-013; 0 mục → giờ đến hạn kế tiếp + "Ôn trước"). Không hiện khối chưa có dữ liệu thật (chuỗi ngày, lượt, credit — ngoài phạm vi Q7) |
| **FR-026** | SCR-013 SHALL ôn từng câu (engine `IMMEDIATE`): câu chọn hiện đúng/sai ngay; thẻ tự đánh giá có 4 nút "Quên / Khó / Được / Dễ" và **không** hiện nút "Dễ" cho câu chọn; hiện số còn lại; "Tạm dừng" giữ câu đã ôn; kết thúc → hạn ôn kế tiếp. Rỗng: "Chưa có mục đến hạn" + "Ôn trước" |

### Key Entities

| Bảng | Dùng thế nào |
|---|---|
| `learning.user_progress` | Dòng `KNOWLEDGE_POINT` (MP-1.0), `LEXEME` (FSRS) |
| `learning.attempt_items`, `attempts` | Minh chứng (chỉ đọc) |
| `learning.knowledge_points`, `question_knowledge_points`, `questions`, `lexemes`, `topics` | Qua `KnowledgeTreeService` (010) |

---

## 4 · Data Model

V6 ở §0.1. Ví dụ dòng `KNOWLEDGE_POINT` sau ví dụ Quốc Bảo: `mastery = 0.533`, `confidence = 2.407`, `mp_state = 'review'`, `evidence_item_ids = '{901,877,850,812,790}'`, `next_review_at = updated_at + 3 ngày`.

### 4.1 · Ví dụ chuẩn (SC-001 — v2.0, kiểm ở AT-09)

| # | Ngày trước | Bối cảnh | Dạng | Vai trò | Điểm | w |
|---|---|---|---|---|---|---|
| 1 | 1 | `PRACTICE` | `OPEN` | `PRIMARY` | 0 | 0,4886 |
| 2 | 3 | `GRADED` | `OPEN` | `PRIMARY` | 50 | 0,5598 |
| 3 | 6 | `QUIZ` | `MCQ4` | `PRIMARY` | 100 | 0,5223 |
| 4 | 9 | `GRADED` | `OPEN` | `SECONDARY` | 100 | 0,2437 |
| 5 | 13 | `QUIZ` | `OPEN` | `PRIMARY` | 40 | 0,5923 |

Σw = 2,41 · mastery = **53,3** → `review`. Thêm minh chứng `EXAM` `OPEN` `PRIMARY` hôm nay 80 điểm (w = 1,0) → **61,1** → `consolidate`.

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | spec 001 |
| `FORBIDDEN` | 403 | Sai role | FR-001 |
| `CSRF_TOKEN_INVALID` | 403 | Ghi qua cookie thiếu CSRF | spec 001 |
| `VALIDATION_FAILED` | 422 | Body sai; rating thẻ ngoài 4 giá trị | FR-022, FR-023 |
| `KNOWLEDGE_NODE_NOT_FOUND` | 404 | KID không có (mã của 010) | FR-017 |
| `RATE_LIMITED` | 429 | E-R12 của engine | FR-022 |
| Lỗi làm bài (`ATTEMPT_NOT_OWNED`, `SESSION_EXPIRED`…) | | Spec 003 §0.6, §0.8 | FR-023 |

**Không thêm mã mới vào `ErrorCode.java`.**

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-01** | `contracts/openapi-mastery.yaml`; enum `PracticeSource` sửa ở contract 003 trong T-12 |
| **AC-03** | Chỉ bảng `learning`; nhãn/cây qua `learning.knowledge` |
| **AC-04** | 0 bảng mới |
| **AC-05** | `V6__mastery_progress.sql`, không sửa V1–V5 |
| **AC-06** | `mp_state`, `target_type` là `VARCHAR + CHECK` |
| **AC-08** | FSRS, bán rã tính UTC; "ngày khác nhau" của FR-010 theo ngày Việt Nam (`BUS-08`) |
| **AC-09** | Không thêm JSONB; `evidence_item_ids` là `BIGINT[]` (như `topics.prereq_ids`); đọc `attempt_items.meta` chỉ trong Java |
| **AC-10** | Mastery + FSRS ghi trong transaction `finish`/trả lời của engine |
| **BUS-04** | FSRS-5 cho lịch ôn mục từ; không Leitner |
| **BUS-05** | Không đụng cổng 90% chủ đề |
| **BUS-09** | Thẻ tự đánh giá không thành minh chứng |
| **BUS-16** | Luyện viết không minh chứng, không FSRS (§12 AGENTS) |
| Package | `learning.mastery` (`MasteryConfig`, `EvidenceAllocator`, `MasteryCalculator`, `MasteryStateMachine`, `MasteryUpdater`, `MasteryListener`, `MasteryRebuilder`, `MasteryQueryService`, `MasteryReader`, `FrontierService`, `RecommendationService`, `KnowledgeProgress`, `web.MasteryController`) · `learning.review` (`FsrsConfig`, `FsrsScheduler`, `LexemeSchedule`, `ReviewListener`, `ReviewSessionService`, `ReviewHandler`, `web.ReviewController`) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Phần mastery + FSRS trong `finish` đề 200 câu, 60 KID | < 300 ms p95 (SC-004); số truy vấn không tăng theo số câu |
| `NFR-P02` | `GET /api/mastery` cây 600 nút, người học có 600 dòng | < 200 ms p95 |
| `NFR-P03` | `GET /api/mastery/recommendations` | < 150 ms p95 |
| `NFR-P04` | `POST /api/reviews/sessions` 50 mục | < 500 ms p95 |
| `NFR-Q01` | `MasteryCalculator`, `FsrsScheduler` thuần, test không cần Spring | — |

---

## 8 · Constitution Check

| Mã | Kết quả |
|---|---|
| **AC-04** | ✅ 0 bảng mới |
| **AC-05** | ✅ Chỉ V6 |
| **AC-09** | ✅ Không JSONB ghi nhiều lần |
| **AC-10** | ✅ Cùng transaction |
| **BUS-01** | ✅ Chỉ dữ liệu người gọi |
| **BUS-04** | ✅ FSRS-5 cho lịch ôn; mastery KID dùng MP-1.0 — **chờ RFC Q1** (Y2) |
| **BUS-05** | ✅ Không đụng |
| **BUS-08** | ✅ Ngày học theo giờ Việt Nam; FSRS UTC |
| **BUS-09** | ✅ Chỉ kết quả server chấm thành minh chứng |
| **BUS-16** | ✅ Không quyết định thay — luyện viết bị loại |
| UC-042 BR-042-6, UC-051 BR-051-3, SCR-019 "mastered ≥ 0,9" | ⚠️ **Y2** — thang 0–100, vững ≥ 80 (Q8); RFC Q1 |
| UC-044 BR-044-1 "không dùng Leitner" | ✅ Không Leitner |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | 0 bảng mới: V6 thêm cột vào `user_progress`; minh chứng = `attempt_items` | Chốt 2026-11-03 |
| Q2 | Trọng số theo v2.0 (bối cảnh × dạng × vai trò) | Chốt 2026-11-03 |
| Q3 | Biên học tập: tiên quyết đạt củng cố trở lên (≥ 60) — lệch v2.0 (≥ 80) | Chốt 2026-11-03 — **ghi RFC (Y4)** |
| Q4 | Làm FSRS mục từ + SCR-013 | Chốt 2026-11-03 |
| Q5 | Có trạng thái `inferred` | Chốt 2026-11-03 |
| Q6 | Giữ luật 7 ngày | Chốt 2026-11-03 |
| Q7 | Chỉ phần cây: SCR-019, khối gợi ý + ôn tập của SCR-010, SCR-013 | Chốt 2026-11-03 |
| Q8 | Thang 0–100, vững ≥ 80 | Chốt 2026-11-03 — **ghi RFC (Y2)** |
| F1 | FSRS-5 mặc định, retention 0,9, 1–365 ngày, UTC, thiếu cấu hình không khởi động | Chốt 2026-11-03 |
| F2 | Câu khách quan: đúng `GOOD`, sai `AGAIN`; chỉ thẻ tự đánh giá chọn 4 mức | Chốt 2026-11-03 |
| F3 | Vào lịch ôn khi học ở `TOPIC_STUDY` hoặc lần đầu trả lời câu có chấm | Chốt 2026-11-03 |
| F4 | Câu chọn trong SCR-013 là minh chứng `PRACTICE`; thẻ tự đánh giá không | Chốt 2026-11-03 |
| D1 | Minh chứng ghi ở `SessionFinishedEvent`; lượt bỏ dở/hết hạn không tính | Chốt 2026-11-03 (mặc định đã duyệt) |
| D2 | **Ngoại lệ `DUE_REVIEW`**: xử lý từng câu ở `ItemGradedEvent` để "tạm dừng giữ câu đã ôn" (SCR-013 Must Show) | Chốt 2026-11-03 |
| Y1 | AC-04 22 bảng ↔ v2.0 dùng bảng `evidence`/`mastery` | Q1 |
| Y2 | UC-042/044/051, SCR-019: FSRS, 0–1, "đã thuộc ≥ 0,9" ↔ v2.0 MP-1.0 0–100, ≥ 80 | Q8 + RFC Q1 |
| Y3 | Trọng số plan phase 3 ↔ v2.0 | Q2: theo v2.0; sửa plan |
| Y4 | Biên v2.0 (extend) ↔ red-team #1 (consolidate) | Q3 |
| Y5 | Plan gán SCR-013 là "KID cần ôn" ↔ SCR-013 là ôn mục từ FSRS | Theo screen-fields (Q4) |
| Y6 | `BUS-16` (luyện viết), phát âm chưa nối KID | Loại khỏi minh chứng; không tự quyết |
| OQ-1 | Tên tiếng Việt 6 miền (010 OQ-1) | Mở — FE hiện mã |
| OQ-2 | 19 trọng số FSRS-5 | Chốt 2026-11-03: giữ nguyên số trong §0.5, không đối chiếu (rủi ro §11) |

---

## 10 · Móc nối

| Chỗ | 011 cung cấp | Dùng bởi |
|---|---|---|
| `MasteryReader` (FR-018) | Mastery theo tập KID | 012 FR-014 (điểm yếu sau bài thi) |
| Biên học tập + gợi ý | FR-014, FR-015 | 012 kết quả xếp trình độ (nút bắt đầu — làm sau), SCR-010 |
| Luyện nút | Liên kết SCR-023 `?kid=` | Endpoint 012 FR-015 |
| Tiến độ chủ đề (BR-042-8) | Không đổi | Chủ đề vẫn theo 003 (90%) |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Vai trò/KID của minh chứng cũ tính lại theo nhãn hiện tại | Nhãn đổi có audit (010); `rebuild` cho kết quả nhất quán |
| Câu bị lưu trữ sau khi đã vào cửa sổ vẫn được tính | Luật FR-008 xét lúc ghi; lưu trữ không xoá lịch sử |
| `user_progress` gộp nhiều loại dòng | Theo thiết kế `database.md` §4.9; entity riêng theo `target_type` |
| Biên ≥ 60 lệch v2.0 | Q3, cấu hình được nếu nhóm đổi về 80 |
| Lượt `IMMEDIATE` bỏ dở mất minh chứng | D1 — đơn giản, có cờ gian lận đầy đủ |
| FSRS dùng trọng số mặc định, chưa tối ưu theo dữ liệu | Đủ cho MVP; tối ưu cần nhiều lịch sử |
| 19 trọng số FSRS-5 chưa đối chiếu thư viện gốc (OQ-2) | Leader chốt giữ nguyên; sai số chỉ đổi độ dài khoảng ôn, không đổi luồng; sửa = đổi cấu hình |
| Không có tầng gộp mục từ → nút TV (công thức 68,5 của v2.0) | Nút TV tính từ câu luyện như nút khác; làm sau |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-019 Learning Progress | `/learn/progress` | `LEARNER` | `GET /api/knowledge-tree` (010), `GET /api/mastery`, `GET /api/mastery/nodes/{kid}` | `screen-fields.md` dòng 9160 |
| SCR-010 Learning Dashboard | `/learn` | `LEARNER` | `GET /api/mastery/recommendations`, `GET /api/reviews/summary` | dòng 4012 |
| SCR-013 Due Review | `/learn/review` | `LEARNER` | `POST /api/reviews/sessions`, engine `/api/practice/sessions/{id}` | dòng 5732 |

SecurityConfig (`LEARNER`): `GET /api/mastery`, `GET /api/mastery/nodes/{kid}`, `GET /api/mastery/recommendations`, `GET /api/reviews/summary`, `POST /api/reviews/sessions`.

| Open issue (screen-fields) | Chốt |
|---|---|
| SCR-019: phân loại kỹ năng chưa chốt | Dùng 6 miền v2.0 (010); không dùng 6 kỹ năng UC-053 |
| SCR-019: "mastered ≥ 0,9" | Vững ≥ 80 (Q8) |
| SCR-013: ôn lại câu sai trong ngày (UC-024) ↔ tối thiểu 1 ngày (UC-044) | Khoảng ôn tối thiểu 1 ngày (F1); ôn cùng ngày qua "Ôn trước" |
| SCR-010: khối lượt/credit, chuỗi ngày | Ngoài phạm vi (Q7) |

Trạng thái mỗi màn: Loading (skeleton) · Empty (FR-024, FR-026) · Error ("Thử lại").

---

## 13 · API Contract

[`contracts/openapi-mastery.yaml`](contracts/openapi-mastery.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/mastery` | 200 `MasteryTree` | 401, 403 |
| `GET /api/mastery/nodes/{kid}` | 200 `MasteryNodeDetail` | 401, 403, 404 |
| `GET /api/mastery/recommendations` | 200 `Recommendation[]` | 401, 403 |
| `GET /api/reviews/summary` | 200 `ReviewSummary` | 401, 403 |
| `POST /api/reviews/sessions` | 201/200 `SessionView` (003) · 200 `NothingDue` | 401, 403, 422, 429 |
| Ôn từng câu `/api/practice/sessions/**` | Spec 003 (enum thêm `DUE_REVIEW`) | Spec 003 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | `CONTENT_EDITOR` gọi `GET /api/mastery` · T 403; không token · T 401; `POST /api/reviews/sessions` qua cookie thiếu CSRF · T 403; hai người học · T mỗi người chỉ thấy dữ liệu mình |
| AT-02 | FR-002 | Full verify 003–008, 012 xanh; `DUE_REVIEW` `IMMEDIATE` 2 giờ; `FLASHCARD_RATING.gradable() = false` |
| AT-03 | FR-003 | V6 trên Testcontainers: tên CHECK đúng; dòng `LEXEME` chèn được; dòng `KNOWLEDGE_POINT` thiếu `mp_state` · T DB từ chối; entity 005 khởi động `validate` |
| AT-04 | FR-004, E8 | Nộp `TOPIC_TEST` · T dòng `KNOWLEDGE_POINT` có trong cùng transaction; listener ném lỗi giả · T `finish` 500, lượt vẫn `IN_PROGRESS`, không có dòng tiến độ; lượt hết hạn · T không có minh chứng |
| AT-05 | FR-005 | Mỗi nguồn → đúng bối cảnh; `CHAR_WRITING`, `WRITING_CHALLENGE`, `PRON_STAGE`, `TOPIC_STUDY` · T 0 minh chứng |
| AT-06 | FR-006 | Câu có KID chính A, phụ B: đúng · T A 100 `PRIMARY`, B 100 `SECONDARY`; chọn phương án có `errorKid` C · T chỉ C 0 `ERROR`; sai không mã lỗi · T A 0; câu từ chỉ có `lexeme_id` gắn nút · T 1 minh chứng; chủ đề có nút, từ không · T dùng nút chủ đề; không KID · T 0 minh chứng, log; `kidsOfQuestions` gọi 1 lần cho lượt 50 câu |
| AT-07 | FR-007 | TN 4 phương án `QUIZ` `PRIMARY` · T w_static 0,6; đúng/sai `PRACTICE` `SECONDARY` · T 0,125; `PINYIN_INPUT` `EXAM` · T 1,0 |
| AT-08 | FR-008, E2, E4, E5, E6 | Lượt `suspicious` · T 0 minh chứng; câu `skipped`/`audioFailed`/`missing`/meta `suspicious` · T bỏ; câu `ARCHIVED` · T bỏ; đúng câu X hôm qua, hôm nay làm X · T bỏ; đúng X 8 ngày trước · T tính |
| AT-09 | FR-009, SC-001 | Bảng §4.1 với `today` cố định · T 53,3, tin cậy 2,41 (±0,005); thêm `EXAM` 80 · T 61,1; 10 minh chứng · T dùng 8 mới nhất; 5 `PRACTICE` + 5 `QUIZ` · T cửa sổ có đúng 3 `PRACTICE`; rỗng · T `null` |
| AT-10 | FR-010 | 5 minh chứng cùng một ngày · T `insufficient`; §4.1 · T `review`, hạn +3 ngày; `consolidate` 58 · T giữ `consolidate`; 56 · T `review`; `extend` 78 · T giữ; 76 · T `consolidate`; `insufficient` → 61 · T `consolidate` |
| AT-11 | FR-011, E10 | Minh chứng 200 ngày trước · T đọc ra tin cậy < 1 → `insufficient`; nút `notyet` có nút phụ thuộc 85 tin cậy 2,5 · T `inferred`; tin cậy 1,5 · T vẫn `notyet`; DB không có `inferred` |
| AT-12 | FR-012, E7 | Hai `finish` song song cùng KID (2 luồng) · T `evidence_item_ids` có minh chứng của cả hai; cửa sổ đầy 8 · T minh chứng cũ nhất ra |
| AT-13 | FR-013, SC-002 | Người học có 40 lượt trộn nguồn · T `rebuild` cho `mastery`, `confidence`, `mp_state`, `evidence_item_ids` trùng trạng thái tăng dần |
| AT-14 | FR-014 | Nút C tiên quyết A (`consolidate`), B (`review`) · T C không trên biên; B lên `consolidate` · T C trên biên; nút không tiên quyết · T trên biên; nút `extend` · T không trên biên; nội dung chủ đề của nút ngoài biên vẫn mở (003 không đổi) |
| AT-15 | FR-015, SC-003 | Người mới · T chỉ `NEW`, ≤ 5, cấp thấp trước, không nút "cần ôn"; có 2 `review` (một có tiên quyết `review`), 1 `consolidate` đến hạn · T thứ tự `PREREQ`, `REVIEW`, `REVIEW`, `CONSOLIDATE`, `NEW`; nút không có câu dùng được · T không xuất hiện; `reason` không rỗng |
| AT-16 | FR-016 | Cây 60 nút, người học có 5 dòng · T 60 phần tử, 55 `notyet` `value=null`; ≤ 3 truy vấn (`StatementInspector`) |
| AT-17 | FR-017 | Nút có 5 minh chứng · T `evidence` 5 dòng mới nhất trước, `weight` khớp FR-009; KID không có · T 404 |
| AT-18 | FR-018 | `valuesOf` 3 KID (1 có dòng) · T đủ 3 khoá, 2 `null`/`notyet`, giá trị làm tròn |
| AT-19 | FR-019 | Lần đầu `AGAIN`/`HARD`/`GOOD`/`EASY` · T khoảng 1/1/3/16 ngày; `GOOD` lần 2 sau đúng hạn · T khoảng > lần 1; `AGAIN` · T `S'` ≤ `S`, `lapse_count + 1`; ôn cùng ngày · T công thức ngắn hạn; mọi khoảng trong 1–365; `weights` 18 phần tử · T ứng dụng không khởi động |
| AT-20 | FR-020 | `TOPIC_STUDY` `LEARNED` · T dòng `LEXEME` hạn +3 ngày; `KNOWN` · T hạn +16; luyện chủ đề đúng từ đã có · T `review_count + 1`; một lượt 2 câu cùng từ, 1 sai · T đúng 1 lần ôn `AGAIN`; lượt `suspicious` · T không đổi |
| AT-21 | FR-021 | 3 mục quá hạn, 2 chưa · T `dueCount=3`, `nextDueAt` = hạn sớm nhất của 2 mục; không có dòng · T `0`, `null` |
| AT-22 | FR-022 | 60 mục quá hạn · T 50 câu, quá hạn lâu nhất trước; từ `WORD` · T `MEANING_CHOICE` 4 phương án, `GRAMMAR` · T `FLASHCARD_RATING`; không có mục · T `NOTHING_DUE`; `ahead=true` · T lấy mục chưa đến hạn; gọi lại khi đang dở · T 200 cùng lượt |
| AT-23 | FR-023 | Trả lời câu chọn đúng · T FSRS cập nhật và có minh chứng `PRACTICE` ngay khi trả lời; thẻ `EASY` · T FSRS cập nhật, 0 minh chứng; thẻ `PERFECT` · T 422; bỏ qua · T không đổi; dừng sau 5 câu (không `finish`) · T 5 mục đã cập nhật; `finish` sau đó · T không xử lý lại |
| AT-24 | FR-024 (FE) | Vitest: 6 màu + nhãn chữ; viền biên; bấm nút mở chi tiết; người mới thấy hướng dẫn + liên kết SCR-020; chỉ cấp 1–3 |
| AT-25 | FR-025 (FE) | Vitest: khối gợi ý với lý do + "Luyện" → `/learn/practice/targeted?kid=`; "{n} mục đến hạn"; 0 mục → giờ kế tiếp + "Ôn trước"; không có khối chuỗi ngày/credit |
| AT-26 | FR-026 (FE) | Vitest: câu chọn phản hồi ngay, không có nút "Dễ"; thẻ có 4 nút; số còn lại giảm; "Tạm dừng" quay lại giữ tiến độ; rỗng có "Ôn trước" |
| AT-27 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-mastery.yaml` (path + status) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md)

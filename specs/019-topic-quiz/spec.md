# Feature Specification: Quiz theo chủ đề (thi đua, không đổi mastery)

**Feature:** `019-topic-quiz`
**Feature tree:** 5.2 Quiz theo chủ đề · **Module:** `learning` (package `learning.quiz`)
**Use case:** UC-080, UC-081 · **Actor:** `LEARNER`
**Màn hình:** SCR-043 Community Quiz List, SCR-044 Community Quiz Attempt, SCR-045 Community Quiz Result
**Scope:** đợt hoàn thiện · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (rà và duyệt 2026-11-13; quyết định chung H1–H14 plan `261113-1629`) |
| **Locked on** | 2026-11-13 |
| **Phụ thuộc** | 003 engine practice, 010 ngân hàng câu + KID, 012 `QUESTION_CHOICE` — đã merge. 017 (bảng xếp hạng) **không chặn** — khối hạng ẩn nếu 017 chưa merge |
| **Quyết định** | Brainstorm 2026-11-13: không đổi mastery (theo UC) · câu ngẫu nhiên từ kho · 10 câu, ẩn quiz nếu < 10 câu |
| **Plan** | `plans/261113-1629-viet-spec-con-lai-hoan-thien/phase-03-spec-019-quiz-cong-dong-va-ho-so.md` |

> **Codex đọc:** `AGENTS.md` → file này → `contracts/openapi-quiz.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Danh sách quiz = chủ đề có ≥ 10 câu `APPROVED` gắn KID chính của chủ đề (SCR-043) | Bảng `quiz_sets`, `quiz_answers`, `ranking_entries` trong UC — **không tạo** (§0.1) |
| Làm quiz 10 câu ngẫu nhiên, chấm ở server, không lộ đáp án (SCR-044, BR-080-4…6) | Đồng hồ đếm ngược — chỉ đo thời gian làm để xếp hạng |
| **Lần nộp đầu tiên** được tính hạng; làm lại không đổi hạng (BR-080-7, BR-080-8) | Tính bảng xếp hạng — 017 |
| Kết quả: điểm, số đúng/sai, thời gian, hạng lần đầu + top 10 (SCR-045, UC-081) | Hồ sơ cộng đồng SCR-042 — phụ lục 009 |
| **Không cập nhật mastery/tiến độ học** — ghi rõ trên giao diện (BR-080-2, BR-080-3) | Quiz do người học tự tạo |
| Chặn trợ lý (015) trong lượt quiz đầu tiên | Thưởng, huy hiệu |

### 0.1 · Lưu trữ trên bảng có sẵn — **không migration**

| Dữ liệu | Bảng/cột |
|---|---|
| Lượt quiz đầu tiên (tính hạng) | `learning.attempts` `kind='QUIZ'`, `ref_type='QUIZ'`, `ref_id=topic_id` (engine ghi `ref_type = PracticeSource.name()`); `uq_quiz_first(user_id, ref_id) WHERE kind='QUIZ' AND status='SUBMITTED'` bảo đảm mỗi người một lượt tính hạng |
| Lượt làm lại | `attempts` `kind='PRACTICE'`, `ref_type='QUIZ_RETRY'`, `ref_id=topic_id` — không đụng `uq_quiz_first`, không vào bảng xếp hạng |
| Câu trả lời | `learning.attempt_items` (engine) |
| Bộ câu đã phát | `attempts.served_items` (engine chụp, không có đáp án gửi client) |
| Nguồn câu | `learning.topics.knowledge_point_id` (V5) → `question_knowledge_points` (`role='PRIMARY'`) → `questions` `status='APPROVED'` |

### 0.2 · Hai nguồn engine mới (gom vào PR enum chung ⚿K1)

| `PracticeSource` | `AttemptKind` | Phản hồi | Hạn phiên | `allowSkip` | Ghi chú |
|---|---|---|---|---|---|
| `QUIZ` | `QUIZ` | `DEFERRED` (đáp án chỉ trả sau khi nộp) | 30 phút | `true` (bỏ câu = sai) | Lượt tính hạng |
| `QUIZ_RETRY` | `PRACTICE` | `DEFERRED` | 30 phút | `true` | Làm lại, không hạng |

Item dùng `ItemType.QUESTION_CHOICE` có sẵn (012). Cùng PR ⚿K1 còn có `CONTEST` (021).

### 0.3 · File dùng chung bị sửa (báo leader trước khi mở PR — H8)

- `PracticeSource` (⚿K1), `ErrorCode.java`, `SecurityConfig.java`, `routeAccess.ts`, `screenRegistry.ts`, `navigation.ts`.
- 011 `EvidenceAllocator.contextOf`: thêm `"QUIZ", "QUIZ_RETRY"` vào nhánh trả `null` (không minh chứng) — **báo người giữ 011**.
- `AttemptRepository.existsActiveTest`: thêm `'QUIZ'` vào danh sách `refType` (trợ lý bị chặn trong lượt tính hạng) — **báo người giữ 012/015**.
- FE 015 `AssistantHost`: ẩn panel ở route SCR-044.

---

## 1 · Context & Goal

Quiz theo chủ đề là trò thi đua nhẹ: làm nhanh 10 câu, so điểm với người khác. Vì là thi đua, **chỉ lần đầu** được tính để công bằng; làm lại để luyện thoải mái. Quiz **tách khỏi** việc học chính — không làm thay đổi mastery, tránh người học "cày quiz" để đẩy tiến độ giả.

**Thành công khi:** người học thấy chủ đề nào có quiz, làm xong thấy điểm và hạng lần đầu, làm lại không đổi hạng, và mastery không đổi sau quiz.

---

## 2 · User Stories

### US-1 · Chọn quiz (P1)
Là người học, tôi lọc quiz theo cấp HSK, thấy chủ đề nào đã làm và điểm lần đầu của mình.

### US-2 · Làm quiz (P1)
Là người học, tôi làm 10 câu, chuyển qua lại giữa các câu, nộp một lần; biết trước lượt này có tính hạng hay không.

### US-3 · Xem kết quả (P1)
Là người học, tôi thấy điểm, số đúng/sai, thời gian, đáp án từng câu, hạng lần đầu và top 10 của chủ đề.

### US-4 · Làm lại (P2)
Là người học, tôi làm lại quiz để luyện; giao diện nói rõ lần này không đổi hạng.

### Edge cases
- Chủ đề chưa gắn KID hoặc < 10 câu `APPROVED` → không xuất hiện trong danh sách; gọi trực tiếp → `QUIZ_NOT_AVAILABLE`.
- Câu bị chuyển `ARCHIVED` giữa chừng → lượt đang làm vẫn dùng snapshot `served_items` (engine).
- Đang có lượt `QUIZ`/`QUIZ_RETRY` chưa nộp cùng chủ đề → bấm "Bắt đầu" trả lại phiên đó (không tạo mới).
- Lượt đầu bỏ dở/hết hạn (`ABANDONED`/`EXPIRED`) → chưa có lượt tính hạng → lần sau vẫn là `QUIZ` (UC A2).
- Hai thiết bị cùng nộp lượt đầu (đua) → lượt nộp sau vi phạm `uq_quiz_first` → engine chuyển lượt đó thành `QUIZ_RETRY` (`kind='PRACTICE'`) trong cùng transaction rồi nộp; kết quả ghi "Lượt này không tính hạng".
- Làm quá nhanh (< 2 giây/câu trung bình) → `suspicious=true`, vẫn nộp được, **không** lên bảng (017 lọc), kết quả ghi "Lượt bị đánh dấu nghi vấn".
- Xem kết quả khi chưa nộp → `ATTEMPT_NOT_SUBMITTED` (chặn lộ đáp án, như UC-038).

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001…FR-002)
- **FR-001** Mọi endpoint 019 yêu cầu `LEARNER`; ghi cần CSRF (cookie).
- **FR-002** Chỉ xem/nộp lượt của chính mình (`ATTEMPT_NOT_OWNED`, engine có sẵn).

### Nhóm 1 — Danh sách (FR-003…FR-005) · SCR-043
- **FR-003** `GET /api/quizzes?hsk=&page=&size=` trả chủ đề có `knowledge_point_id` và **≥ `quiz.question-count`** (mặc định 10) câu `APPROVED` có KID chính = KID chủ đề: `topicId, topicName, hskLevel, questionCount (=10), availableQuestions, myFirstAttempt {score, submittedAt, suspicious} | null, myBestRetryScore | null`. Sắp `hsk_level, order_index`. `size ≤ 50`.
- **FR-004** Đếm câu bằng một truy vấn gộp theo chủ đề cho cả trang (không N+1).
- **FR-005** Đầu trang ghi: "Quiz là thi đua. Chỉ lần làm đầu tiên được tính xếp hạng. Quiz không ảnh hưởng tiến độ học." (BR-080-3).

### Nhóm 2 — Làm quiz (FR-006…FR-011) · SCR-044
- **FR-006** `POST /api/quizzes/{topicId}/attempts`: chủ đề không tồn tại → `TOPIC_NOT_FOUND`; không đủ câu → `QUIZ_NOT_AVAILABLE`. Có phiên `QUIZ`/`QUIZ_RETRY` đang mở cùng chủ đề → trả lại phiên đó (200). Ngược lại tạo phiên mới (201): **chưa có** lượt `QUIZ` `SUBMITTED` → source `QUIZ` (`rankingEligible=true`), có rồi → `QUIZ_RETRY` (`rankingEligible=false`).
- **FR-007** Chọn ngẫu nhiên 10 câu `APPROVED` có KID chính của chủ đề, **cân theo `qtype`** (chia đều số câu cho các dạng có trong kho, dạng thiếu thì bù dạng khác), không trùng câu trong một lượt. Câu đã `ARCHIVED`/`REJECTED` không bao giờ được chọn (BR-080-4).
- **FR-008** Response trả `sessionId, rankingEligible, expiresAt, items[]` qua engine — mỗi item chỉ có đề, phương án, `itemId`; **không** có đáp án, giải thích, `isCorrect` (BR-080-5).
- **FR-009** Trả lời và nộp dùng endpoint engine có sẵn: `POST /api/practice/sessions/{id}/answers`, `POST /api/practice/sessions/{id}/finish`. Chế độ `DEFERRED`: không trả đúng/sai từng câu trước khi nộp. Chấm ở server (BR-080-6).
- **FR-010** Khi nộp: `score = số câu đúng × 100 / 10` (0–100, số nguyên, AC-07); `duration_ms` = `submitted_at − served_at`; trung bình < `quiz.min-seconds-per-question` (mặc định 2) → `suspicious=true`.
- **FR-011** Nộp lượt `QUIZ` mà vi phạm `uq_quiz_first` → chuyển lượt thành `kind='PRACTICE'`, `ref_type='QUIZ_RETRY'` rồi nộp trong cùng transaction; không lỗi cho người dùng (edge case đua).

### Nhóm 3 — Kết quả (FR-012…FR-015) · SCR-045
- **FR-012** `GET /api/quiz-attempts/{sessionId}/result` (chỉ lượt `QUIZ`/`QUIZ_RETRY` của tôi, đã `SUBMITTED`): `topicId, topicName, score, correctCount, wrongCount, durationMs, rankingEligible, suspicious, items[] {itemId, prompt, options, chosen, correct, explanation}`.
- **FR-013** Kèm `firstAttempt {score, durationMs, rank, total} | null`: `rank`/`total` lấy từ `LeaderboardService.rankOf(userId, topicId)` của 017 (kỳ toàn thời gian). 017 chưa merge hoặc lượt đầu `suspicious` → `rank=null` (FE ẩn khối hạng).
- **FR-014** Top 10 hiển thị bằng FE gọi `GET /api/public/leaderboards/topics/{topicId}?period=all` (017) — 019 không tự tính.
- **FR-015** Kết quả ghi rõ: lượt hiện tại "Tính hạng" / "Làm lại — không tính hạng" / "Bị đánh dấu nghi vấn — không tính hạng"; không có câu chữ ngụ ý tăng mastery.

### Nhóm 4 — Tách khỏi học chính (FR-016…FR-017)
- **FR-016** `EvidenceAllocator.contextOf("QUIZ" | "QUIZ_RETRY") = null` → 011 không ghi minh chứng; không cập nhật `user_progress` (BR-080-2). Test khẳng định mastery không đổi sau quiz (UC-080 T3).
- **FR-017** `existsActiveTest` tính cả `refType='QUIZ'` → trợ lý trả `ASSISTANT_BLOCKED_DURING_TEST` trong lượt tính hạng; lượt `QUIZ_RETRY` không chặn. FE ẩn panel trợ lý ở SCR-044.

### Nhóm 5 — Cấu hình (FR-018)
- **FR-018** `quiz.question-count` (10, min 5, max 30), `quiz.min-seconds-per-question` (2, min 0, max 10), `quiz.session-minutes` (30) đọc qua `SettingsApi` (⚿K7 — 023) khi đã merge; trước đó đọc `cnhsk.quiz.*` trong `application.yaml` cùng giá trị mặc định.

### Nhóm 6 — Giao diện (FR-019…FR-023)
- **FR-019** Route `/community/quizzes` (SCR-043), `/community/quizzes/:topicId/play` (SCR-044), `/community/quizzes/attempts/:sessionId` (SCR-045); quyền `LEARNER`, layout học viên; mục "Quiz" trong nhóm Cộng đồng.
- **FR-020** SCR-043: lọc HSK; thẻ chủ đề (tên, cấp, 10 câu, "Đã làm: 80 điểm" / "Chưa làm"); nút "Bắt đầu" hoặc "Làm lại".
- **FR-021** SCR-044: nhãn "Lượt tính hạng" / "Làm lại"; thanh câu (đã trả lời x/10, bấm để nhảy câu); thời gian đã làm; nút Nộp có xác nhận trong trang khi còn câu chưa trả lời; nút Thoát (bỏ dở).
- **FR-022** SCR-045: điểm, đúng/sai, thời gian, trạng thái tính hạng; danh sách câu kèm đáp án đúng/đã chọn + giải thích; khối "Hạng lần đầu: #12/240" + top 10 (017); nút "Làm lại", "Quiz khác", "Bảng xếp hạng chủ đề" (`/leaderboards/topics?topicId=`).
- **FR-023** Dùng `shared/ui` + token `design.md` (H10); đủ 4 trạng thái; responsive ≤ 640 px; đúng/sai có chữ kèm màu (design.md §10).

### Key Entities
`QuizListing` (tính khi đọc) · `QuizAttempt` (`attempts` nguồn `QUIZ`/`QUIZ_RETRY`) · `QuizResult` (tính từ `attempts` + `attempt_items`).

---

## 4 · Data Model

Không migration. Truy vấn đếm kho câu (tham chiếu):

```sql
SELECT t.id, COUNT(q.id) AS available
FROM learning.topics t
JOIN learning.question_knowledge_points qk ON qk.knowledge_point_id = t.knowledge_point_id AND qk.role = 'PRIMARY'
JOIN learning.questions q ON q.id = qk.question_id AND q.status = 'APPROVED'
WHERE t.knowledge_point_id IS NOT NULL AND (:hsk IS NULL OR t.hsk_level = :hsk)
GROUP BY t.id
HAVING COUNT(q.id) >= :questionCount;
```

---

## 5 · Error Matrix

| Mã | HTTP | Khi nào |
|---|---|---|
| `QUIZ_NOT_AVAILABLE` *(mới)* | 422 | Chủ đề chưa gắn KID hoặc < `quiz.question-count` câu `APPROVED` |
| `TOPIC_NOT_FOUND` | 404 | Có sẵn |
| `ATTEMPT_NOT_OWNED` | 403 | Có sẵn (engine) |
| `ATTEMPT_NOT_SUBMITTED` | 422 | Có sẵn — xem kết quả khi chưa nộp |
| `SESSION_EXPIRED`, `SESSION_ALREADY_SUBMITTED`, `ITEM_NOT_IN_SESSION`, `OPTION_NOT_IN_ITEM` | có sẵn | Engine |
| `RATE_LIMITED` | 429 | > 30 lượt bắt đầu/giờ/người |
| `VALIDATION_FAILED` | 422 | `hsk`, phân trang sai |

Mã mới thêm ngay trước `INTERNAL_ERROR` theo thứ tự merge (H8).

## 6 · Architectural Constraints
- Module `learning`; chỉ ghi `attempts` qua engine practice (không ghi tay).
- Gọi 017 `LeaderboardService` trong cùng module; 017 chưa có → bean tuỳ chọn (`ObjectProvider`), không phụ thuộc cứng.
- Không gọi `community`; không đọc `auth` ngoài `AuthApi`.

## 7 · Non-Functional Requirements
- Danh sách p95 < 200 ms với 300 chủ đề (một truy vấn đếm).
- Chọn câu ngẫu nhiên không dùng `ORDER BY random()` trên toàn kho lớn: lấy id theo `qtype` rồi xáo trong bộ nhớ (kho mỗi KID nhỏ).
- Không log câu trả lời.

## 8 · Constitution Check
| Luật | Đáp ứng |
|---|---|
| AC-04 số bảng | Không thêm bảng |
| AC-07 điểm số nguyên | `score` 0–100 integer |
| BUS-09 chấm ở server | FR-009 |
| BR-080-2 không đổi mastery | FR-016 + test |
| Kiểm duyệt nội dung | Chỉ `APPROVED` (FR-007) |
| LESSON-003 | Không nhận điểm client; `suspicious` khi quá nhanh |

## 9 · Quyết định và câu cần leader xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Quiz không cập nhật mastery (theo BR-080-2) | ✅ Leader chốt 2026-11-13 |
| Q2 | Câu ngẫu nhiên từ kho `APPROVED` của KID chủ đề, cân theo dạng câu | ✅ Leader chốt 2026-11-13 |
| Q3 | 10 câu/lượt; ẩn quiz nếu < 10 câu; < 2 giây/câu → nghi vấn | ✅ Leader chốt 2026-11-13 |
| Q4 | Quiz đặt ở module `learning` (UC ghi `community`) để dùng engine không qua `learning.api` | ✅ Leader chốt 2026-11-13 |
| Q5 | Chặn trợ lý trong lượt tính hạng, không chặn lượt làm lại | ✅ Leader chốt 2026-11-13 |
| Q6 | Lượt đầu bị nghi vấn vẫn chiếm "lần đầu" (không làm lại để tính hạng) | ✅ Leader chốt 2026-11-13 |

## 10 · Móc nối
- **017:** đọc lượt `kind='QUIZ'`, `ref_type='QUIZ'`, `ref_id=topicId`, `SUBMITTED`, `suspicious=false`; 019 gọi `rankOf` cho SCR-045; FE dùng API top của 017.
- **011:** `EvidenceAllocator` bỏ qua `QUIZ`, `QUIZ_RETRY`.
- **012/015:** `existsActiveTest` thêm `QUIZ`; panel trợ lý ẩn ở SCR-044.
- **010/002:** kho câu và KID chủ đề nạp qua import; chủ đề cần `knowledge_point_id`.
- **021:** dùng chung PR enum ⚿K1 (`CONTEST`).
- **015:** thêm đoạn `help/` mô tả quiz sau khi merge.

## 11 · Rủi ro đã chấp nhận
- Câu ngẫu nhiên → mỗi người một đề hơi khác; chấp nhận vì cùng KID, cùng số câu, cân dạng câu.
- Nhiều chủ đề chưa đủ 10 câu → ít quiz lúc đầu; bổ sung câu qua import 010 hoặc câu AI đã duyệt (024).
- Người học có thể bỏ dở lượt đầu nhiều lần để xem câu trước → câu ngẫu nhiên giảm lợi ích; giới hạn 30 lượt/giờ.

## 12 · Màn hình
- **SCR-043:** ghi chú thi đua; lọc HSK; lưới thẻ chủ đề; nút Bắt đầu/Làm lại.
- **SCR-044:** nhãn tính hạng; câu hỏi; thanh điều hướng câu; thời gian; Nộp/Thoát.
- **SCR-045:** tóm tắt điểm; trạng thái tính hạng; đáp án từng câu; hạng lần đầu + top 10; nút điều hướng.

## 13 · API Contract
`contracts/openapi-quiz.yaml` — `GET /api/quizzes`, `POST /api/quizzes/{topicId}/attempts`, `GET /api/quiz-attempts/{sessionId}/result`. Trả lời/nộp: endpoint engine 003 có sẵn.

## 14 · Acceptance Tests

| AT | Kịch bản | Kỳ vọng |
|---|---|---|
| AT-01 | Chủ đề 12 câu APPROVED + 5 PENDING_REVIEW | Xuất hiện, `availableQuestions=12` |
| AT-02 | Chủ đề 9 câu APPROVED | Không xuất hiện; bắt đầu → 422 `QUIZ_NOT_AVAILABLE` |
| AT-03 | Lượt phát | 10 câu khác nhau, chỉ APPROVED, không có đáp án/giải thích |
| AT-04 | Kho 2 dạng câu (8 + 8) | Lượt có 5 + 5 |
| AT-05 | Lần đầu nộp 8/10 | `kind=QUIZ`, `score=80`, `rankingEligible=true` |
| AT-06 | Lần 2 nộp 10/10 | `kind=PRACTICE`, `ref_type=QUIZ_RETRY`; lượt đầu giữ nguyên |
| AT-07 | Lần đầu bỏ dở rồi bắt đầu lại | Phiên mới vẫn `QUIZ` |
| AT-08 | Bấm Bắt đầu khi có phiên đang mở | Trả lại phiên cũ (200) |
| AT-09 | Hai phiên `QUIZ` đua nộp | Một `QUIZ`, một chuyển `QUIZ_RETRY`; không lỗi 500 |
| AT-10 | Nộp 10 câu trong 12 giây | `suspicious=true`, kết quả ghi nghi vấn |
| AT-11 | Mastery trước/sau quiz | Không đổi; không dòng minh chứng mới (UC-080 T3) |
| AT-12 | Hỏi trợ lý khi đang lượt `QUIZ` | 403 `ASSISTANT_BLOCKED_DURING_TEST`; lượt `QUIZ_RETRY` → được hỏi |
| AT-13 | Xem kết quả khi chưa nộp | 422 `ATTEMPT_NOT_SUBMITTED` |
| AT-14 | Xem kết quả lượt người khác | 403 `ATTEMPT_NOT_OWNED` |
| AT-15 | 017 chưa có bean | Kết quả 200, `firstAttempt.rank=null` |
| AT-16 | Có `rankOf` | `firstAttempt.rank`, `total` đúng |
| AT-17 | FE: SCR-043 ghi chú thi đua; SCR-044 ẩn trợ lý; SCR-045 không chữ "tăng mastery" | Đúng |
| AT-18 | Hợp đồng `/v3/api-docs` so `openapi-quiz.yaml` | Khớp path/method/status hai chiều |

## 15 · Gói việc
Xem `tasks.md` — 8 task, ước tính BE 4 · FE 3 ngày công.

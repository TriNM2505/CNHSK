# Feature Specification: Đề thi thử và bài xếp trình độ

**Feature:** `012-mock-exam-placement`
**Feature tree:** 2.1 Làm đề thi thử · 2.2 Luyện riêng (phần luyện theo nút) · 2.3 Phân tích sau bài thi · **Module:** `learning` (package `learning.exam`)
**Use case:** UC-033 · UC-034 · UC-035 · UC-036 (bằng nháp của engine, Q12) · UC-038 · UC-039 · UC-040 · UC-041 · UC-130 · UC-131 · UC-110 (phần nạp đề) · **Actor:** `LEARNER`, `CONTENT_EDITOR`
**Màn hình:** SCR-020 Exam List · SCR-021 Exam Attempt · SCR-022 Exam Result · SCR-023 Targeted Practice · SCR-068 (thêm 2 loại dữ liệu). **Không thêm màn** (79 màn FINAL)
**Scope:** MVP · **Priority:** P0

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (RFC đợt cuối R1–R12 đã đồng thuận) |
| **Locked on** | 2026-11-03 |
| **Phụ thuộc** | Spec 001 (đăng nhập, lỗi, CSRF) · Spec 002 (luồng nhập §3, §4) · **Spec 003 §0 (engine luyện tập — 012 thêm giá trị, §0.2)** · **Spec 010** (`Question` entity, `KnowledgeTreeService` FR-024/FR-025, mẫu `QUESTION`) · Spec 011 (chỉ FR-014: đọc mastery) |
| **Quyết định chung** | `questions.md` mục 2026-11-02 (Q1–Q13) · plan `261102-2104` phase 4 |
| **Điều kiện APPROVED** | Spec 010 APPROVED (RFC Q1) · RFC ghi thêm xung đột X1, X2, X3 (§9) |

> **Codex đọc:** `AGENTS.md` → spec 003 §0 → spec 010 FR-024/FR-025 → file này → `contracts/openapi-exam.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Đề thi thử HSK 1–3 (nội dung), câu `MULTIPLE_CHOICE`, `TRUE_FALSE` không audio, không ảnh (Q2, Q8) | Câu nghe, ảnh, `FILL_BLANK`, `SENTENCE_ORDER`, `SENTENCE_MATCH`, `ESSAY` — sau đợt (cần mở rộng cách chấm của engine) |
| Làm đề **không đếm ngược**, hạn 24 giờ, chuyển phần tự do (Q1, BR-034-4) | Mô phỏng thi thật, đồng hồ, tự nộp khi hết giờ, chống gian lận |
| Điểm theo `exam_questions.points` (trọng số) chụp vào snapshot (Q4, Q4b) | Ngưỡng đỗ/trượt của đề, quy đổi thang 300 |
| Lưu nháp bằng kiểu `DEFERRED` của engine (Q12) | `PATCH /api/attempts/{id}/draft` riêng của UC-036 |
| Kết quả, lời giải, câu sai nhóm theo KID; 3–5 điểm yếu theo mastery khi 011 xong (Q6) | Tính mastery, ghi minh chứng — listener của spec 011 |
| Luyện theo nút từ câu sai (SCR-023, UC-041) bằng ngân hàng câu `APPROVED` | AI sinh câu (feature 3.3), chọn điểm yếu tự động khi không có KID — sau 011 |
| Bài xếp trình độ thích ứng theo khối (Q7) — chế độ của SCR-020/021/022 | Nút "bắt đầu học" trên biên học tập, 3 nút yếu theo mastery — spec 011 (§10) |
| Nạp đề bằng 2 mẫu nhập `EXAM`, `EXAM_QUESTION` (Q5) | SCR-065/066 quản trị đề trên giao diện |
| Đề miễn phí | Đề trả phí, trừ lượt (SCR-020 open issue — không bịa phí) |

**Không có migration** (Q13). Bảng dùng: `exams`, `exam_questions`, `questions`, `question_knowledge_points`, `knowledge_points`, `attempts`, `attempt_items` (V1, V4, V5 của 010).

### 0.1 · Ba nguồn lượt mới (bổ sung spec 003 §0.2)

| `ref_type` | `attempts.kind` | `ref_id` | Kiểu phản hồi | Hạn | `allowSkip` |
|---|---|---|---|---|---|
| `MOCK_EXAM` | `EXAM` | `exams.id` | `DEFERRED` | 24 giờ | `true` (bỏ qua = sai) |
| `PLACEMENT` | `EXAM` | `NULL` ở khối 1; id khối 1 ở khối 2+ (§0.4) | `DEFERRED` | 2 giờ mỗi khối | `true` |
| `TARGETED_PRACTICE` | `PRACTICE` | `NULL` | `IMMEDIATE` | 2 giờ | `true` |

`maxScoreFromServed = false` cho cả ba. `attempts.kind = 'EXAM'` có sẵn trong CHECK V1, `ref_type` là cột chữ tự do — không đổi CHECK.

### 0.2 · Thay đổi engine (file của spec 003 — chỉ **thêm**, Q2, Q3)

| Thay đổi | Chi tiết |
|---|---|
| `PracticeSource` | Thêm `MOCK_EXAM`, `PLACEMENT`, `TARGETED_PRACTICE` theo §0.1 (cuối enum) |
| `ItemType` | Thêm `QUESTION_CHOICE(gradable = true)`: chấm so khoá như `MEANING_CHOICE`; đáp án client gửi = khoá `A`–`F` |
| `ServedItem` | Thêm thành phần cuối `Long questionId`. **Giữ constructor 10 tham số cũ** (gọi `questionId = null`) — 36 chỗ gọi ở 003–008 không phải sửa |
| `PracticeApi.ServedItem`, `PracticeApi.PracticeSource`, `PracticeApi.ItemType` | Đồng bộ như trên (constructor cũ giữ nguyên) |
| `ItemGradedEvent` | Thêm thành phần cuối `Long questionId`, giữ constructor cũ |
| `PracticeService` | Ba chỗ ghi `AttemptItem` (trả lời, ghi `DEFERRED` lúc nộp, `preRecorded`) gọi `setQuestionId(item.questionId())`; `publishItem` truyền `questionId`. `validateItems`: `QUESTION_CHOICE` bắt buộc `questionId != null`, loại khác bắt buộc `null` → `IllegalArgumentException` |
| `ServedItemView` | **Không** thêm `questionId` (client không cần) |

Không đổi luật chấm, điểm, hạn, khoá dòng của engine. `attempt_items.question_id` có FK `ON DELETE RESTRICT` (V1) giữ lịch sử khi câu bị lưu trữ.

### 0.3 · Hai mẫu nhập mới (bổ sung spec 002 §4.0, sau 4 mẫu của 010)

| `dataType` | Nhãn trên SCR-068 | Khoá tự nhiên | Phụ thuộc |
|---|---|---|---|
| `EXAM` | Đề thi | `code` | — |
| `EXAM_QUESTION` | Câu trong đề thi | `(exam_code, question_code)` | `EXAM`, `QUESTION` |

Đi qua nguyên luồng spec 002 (kiểm tra → xem trước → xác nhận → ghi từng dòng `REQUIRES_NEW`). **Không commit đề HSK thật** (`HR-08`, DK-13); test dùng đề tự soạn trong `src/test/resources/import/exam/`.

### 0.4 · Bài xếp trình độ — thuật toán (lớp thuần `PlacementAlgorithm`)

Tham số cấu hình `cnhsk.placement.*` (mặc định trong `application.yaml`, **không** hard-code; Q9):

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `block-size` | 5 | Số câu mỗi khối, cùng một cấp |
| `min-items` | 25 | Số câu tối thiểu trước khi được dừng |
| `max-items` | 35 | Dừng cứng |
| `up-at` / `down-at` | 4 / 2 | Đúng ≥ 4 → lên cấp; đúng ≤ 2 → xuống cấp; đúng 3 → giữ cấp |
| `thresholds` | `{1: 60, 2: 65, 3: 75}` | % đúng tối thiểu để đạt cấp (tạm theo file md; leader đối chiếu v2.0 — OQ-1) |
| `sure-min-items` | 10 | Số câu tối thiểu ở cấp kết luận để kết quả "Chắc chắn" |
| `start-level` | 2 | Cấp khối đầu khi người học không chọn |
| `min-bank-per-level` | 10 | Số câu dùng được tối thiểu mỗi cấp 1–3 để mở bài |

1. **Kết quả khối:** `c` = số câu đúng (bỏ qua/không trả lời = sai). `c ≥ up-at` → `UP`; `c ≤ down-at` → `DOWN`; còn lại `STAY`.
2. **Cấp khối sau** = `clamp(L + (UP ? 1 : DOWN ? −1 : 0), 1, 3)`.
3. **Ước lượng** `estimate(blocks)`: xét `L = 3, 2, 1`, gặp cấp đầu tiên có `n(L) ≥ block-size` và `100·đúng(L)/n(L) ≥ thresholds[L]` → `HSK{L}`; riêng `L = 3` mà **hai khối cuối** đều ở cấp 3 và đều `UP` → `ABOVE_3` ("HSK 3 trở lên"). Không cấp nào đạt → `BELOW_1` ("Chưa tới HSK 1").
4. **Dừng** sau một khối khi: tổng câu ≥ `max-items`; **hoặc** tổng câu ≥ `min-items` và `estimate(tất cả khối) = estimate(bỏ khối cuối)`; **hoặc** không còn đủ `block-size` câu chưa phát ở cấp kế tiếp (`bankExhausted = true`).
5. **Độ tin cậy:** `SURE` khi `n(cấp kết luận) ≥ sure-min-items` (`BELOW_1` xét cấp 1, `ABOVE_3` xét cấp 3) và `bankExhausted = false`; ngược lại `TENTATIVE` ("Kết quả tạm").
6. **Lượt (run):** khối 1 có `ref_id = NULL`; `runId` = id lượt khối 1; khối 2+ có `ref_id = runId`. Khối `EXPIRED`/`ABANDONED` bị bỏ qua khi tính (chỉ khối `SUBMITTED`). Làm lại **không giới hạn** (Q10); lượt cũ không bị xoá.

### 0.5 · File dùng chung bị sửa (báo leader trước khi mở PR)

| File | Thay đổi | Task |
|---|---|---|
| `learning/practice/PracticeSource.java`, `ItemType.java`, `ServedItem.java`, `PracticeService.java`, `event/ItemGradedEvent.java`, `learning/api/PracticeApi.java` | §0.2 | T-06 |
| `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml` | Enum `PracticeSource` thêm 3 giá trị | T-06 |
| `cnhsk-web/src/features/practice/types.ts` | Union `PracticeSource` thêm 3 giá trị, `ItemType` thêm `QUESTION_CHOICE` | T-01 |
| `learning/content/importer/template/ImportDataType.java`, `ImportTemplate.java` + validator/writer mới | 2 mẫu §0.3 | T-07, T-09 |
| `specs/002-learning-data-import/contracts/openapi-import.yaml` | Enum `dataType` thêm 2; mã lỗi dòng thêm §4.1 | T-07 |
| Test 002/010 đang khẳng định "11 mẫu" | Đổi thành 13 | T-07 |
| `shared/exception/ErrorCode.java` + `GlobalExceptionHandlerTest` | 6 mã §5, thêm ngay trước `INTERNAL_ERROR` (sau các mã của 010) | T-10, T-11, T-14, T-15 |
| `shared/config/SecurityConfig.java` | Matcher §12 (`LEARNER`) | T-10, T-11, T-12, T-14, T-15, T-16 |
| `src/main/resources/application.yaml` | Khối `cnhsk.placement.*` §0.4 | T-11 |
| `cnhsk-web/src/features/content-import/types.ts`, `labels.ts` | 2 `dataType` + nhãn | T-05 |
| `cnhsk-web/src/app/routeAccess.ts`, `screenRegistry.ts` | 5 route §12, catalog feature `exam` | T-01 |
| `docs/reference/database.md` | §11: ghi nguồn `MOCK_EXAM`, `PLACEMENT`, `TARGETED_PRACTICE`; `attempt_items.question_id` đã được ghi | T-06 |

`application-test.yaml` **không** đổi.

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-01** | Biết chính xác yếu điểm kiến thức nào | Câu sai nhóm theo KID chính; "luyện ngay" đúng nút |
| **BG-02** | Người mới biết mình đang ở đâu | Bài xếp trình độ ra cấp ước lượng + hồ sơ 6 miền |
| **BG-05** | Demo trọn luồng học → thi → phân tích | Đề thi thử + phân tích KID + luyện nút |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Mô phỏng 1 000 người học/cấp (§14 AT-24): xếp đúng cấp | ≥ 80% mỗi cấp |
| **SC-002** | Mô phỏng: lệch quá 1 cấp | ≤ 2% |
| **SC-003** | Response làm bài không lộ đáp án (AT-10) | 100% |
| **SC-004** | Thử thật 5–10 người đã biết trình độ, ghi kết quả vào `questions.md` | Trước demo |

---

## 2 · User Stories

### US-1 · Làm đề thi thử (P1)
Người học chọn đề HSK 1–3, làm các phần tự do, đóng trang rồi quay lại vẫn còn câu đã chọn, bấm nộp và nhận điểm.

### US-2 · Xem kết quả và luyện chỗ sai (P1)
Sau khi nộp, người học thấy điểm theo phần, câu sai/bỏ trống, đáp án, lời giải, câu sai nhóm theo nút kiến thức; bấm "Luyện nút này" để làm câu cùng nút.

### US-3 · Kiểm tra trình độ (P1)
Người mới bấm "Kiểm tra trình độ", làm từng khối 5 câu (khối sau khó/dễ hơn tuỳ khối trước), sau 25–35 câu thấy: khoảng HSK mấy, chắc chắn hay tạm, mạnh/yếu miền nào.

### US-4 · Nạp đề (P1)
Biên tập viên nạp đề tự soạn qua SCR-068: file `EXAM` rồi file `EXAM_QUESTION`.

### Edge cases

| # | Tình huống | Xử lý |
|---|---|---|
| E1 | Câu bị lưu trữ hoặc sửa sau khi đề đã phát | Chấm và hiển thị theo snapshot `served_items` (BR-035-11, engine) |
| E2 | Đề chuyển `ARCHIVED` khi người học đang làm | Vẫn nộp được; danh sách ẩn đề |
| E3 | Câu chưa gắn KID | Vẫn chấm; không vào nhóm KID, không có "Luyện nút này"; log `WARN` (BR-035-10) |
| E4 | Nộp hai lần, hai request song song | Engine: một thành công, một **409** `SESSION_ALREADY_SUBMITTED` |
| E5 | Nộp sau 24 giờ | Engine: **422** `SESSION_EXPIRED`, lượt `EXPIRED`, không chấm |
| E6 | Bắt đầu đề khi còn lượt dở | Trả lượt dở (**200**), không tạo mới; muốn làm lại thì `abandon` (engine) rồi bắt đầu |
| E7 | Hai người cùng làm một đề | `myAttempts`, `myBest` chỉ tính lượt của chính người gọi |
| E8 | Đóng trang giữa khối xếp trình độ | Mở lại: `POST /api/placement/runs` trả khối đang dở |
| E9 | Khối xếp trình độ hết hạn | Bỏ qua khối đó; `next` phát khối mới cùng cấp |
| E10 | Ngân hàng một cấp hết câu chưa phát trong lượt | Dừng, kết quả `TENTATIVE` |
| E11 | Làm lại bài xếp trình độ nhiều lần | Ưu tiên câu chưa gặp ở lượt hoàn thành gần nhất (FR-018) |
| E12 | Đề có câu nghe/ảnh/dạng chưa hỗ trợ | Mẫu `EXAM_QUESTION` từ chối dòng (`QUESTION_NOT_USABLE`) |

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL áp quyền: mọi endpoint §13 → chỉ `LEARNER`; mẫu nhập §0.3 → chỉ `CONTENT_EDITOR` (spec 002 FR-001). Sai role → **403** `FORBIDDEN`; không có role kế thừa. Ghi qua cookie cần CSRF (spec 001). Lượt không tồn tại hoặc không thuộc người gọi → **403** `ATTEMPT_NOT_OWNED` cùng thông báo (`BUS-01`, 003 E-R1/E-R2) |

### Nhóm 1 — Mở rộng engine (FR-002…FR-004)

| Mã | Yêu cầu |
|---|---|
| **FR-002** | THE system SHALL thêm 3 nguồn §0.1 và `ItemType.QUESTION_CHOICE` §0.2 mà **không** đổi hành vi nguồn cũ. Toàn bộ test 003–008 giữ xanh |
| **FR-003** | THE engine SHALL nhận `ServedItem.questionId`, ghi vào `attempt_items.question_id` ở mọi đường ghi câu và đưa vào `ItemGradedEvent.questionId`; `QUESTION_CHOICE` thiếu `questionId` hoặc loại khác có `questionId` → `IllegalArgumentException` lúc bắt đầu (§0.2) |
| **FR-004** | `learning.exam.QuestionItemFactory` SHALL dựng `ServedItem` từ câu: `type = QUESTION_CHOICE`, `skill = READING`, `lexemeId = null`, `questionId`, `prompt.text = stem`, `options = [{key, text}]` theo `questions.options` (`TRUE_FALSE`: `A` "Đúng", `B` "Sai"), `answer` = khoá có `is_correct = true`, `explanation`, `rules = {minDurationMs: 1500, maxReplays: 0, hasAudio: false}`. **Câu dùng được** = `status = 'APPROVED'`, `qtype ∈ {MULTIPLE_CHOICE, TRUE_FALSE}`, `audio_url` và `image_url` rỗng, đúng 1 phương án đúng. Câu không dùng được bị loại khỏi lượt, log `WARN` (BR-035-6) |

### Nhóm 2 — Nạp đề (FR-005…FR-009) · SCR-068

| Mã | Yêu cầu |
|---|---|
| **FR-005** | `GET /api/admin/imports/templates` SHALL trả **13** mẫu: 11 mẫu của 002 + 010 rồi `EXAM`, `EXAM_QUESTION`, kèm `naturalKey`, `dependsOn`, `columns` theo FR-006, FR-008. File mẫu CSV có 1 dòng ví dụ **tự soạn**. SCR-068 SHALL có 13 lựa chọn loại dữ liệu với nhãn §0.3 |
| **FR-006** | Mẫu `EXAM` SHALL có cột: `code`* (`^[A-Z0-9_-]{3,50}$`), `title`* (≤ 200), `hsk_level`* (1–6, CHECK V1), `sections`* (1–10 phần, dạng `1:Đọc hiểu\|2:Ngữ pháp`; chỉ số 1–10 tăng dần không trùng, tên ≤ 100; sai → `INVALID_VALUE`), `status`* (`DRAFT` \| `PUBLISHED` \| `ARCHIVED`) |
| **FR-007** | WHEN ghi `EXAM`, THE system SHALL upsert theo `code`: `sections` lưu `[{index, name}]` (JSONB chỉ đọc), `created_by` = người nhập khi tạo; lần đầu sang `PUBLISHED` đặt `published_at = now()`. Hành động `CREATE`/`UPDATE`/`UNCHANGED` theo 002 FR-013 |
| **FR-008** | Mẫu `EXAM_QUESTION` SHALL có cột `exam_code`*, `question_code`*, `section_index`*, `order_index`* (1–999), `points` (1–10, mặc định 1). Kiểm: đề và câu tồn tại (`REFERENCE_NOT_FOUND`); `section_index` có trong `sections` của đề (`SECTION_NOT_FOUND`); câu không "dùng được" theo FR-004 (`QUESTION_NOT_USABLE`); `questions.hsk_level ≠ exams.hsk_level` (`LEVEL_MISMATCH`); `(exam, section_index, order_index)` trùng với dòng khác trong DB hoặc phía trên cùng file (`DUPLICATE_ORDER`); đề sẽ có > 200 câu (`INVALID_VALUE`) |
| **FR-009** | WHEN ghi `EXAM_QUESTION`, THE system SHALL upsert `exam_questions` theo `(exam_id, question_id)` (cập nhật `section_index`, `order_index`, `points`). Không gỡ câu khỏi đề qua nhập (§11). Thay đổi chỉ áp dụng cho lượt bắt đầu sau đó (snapshot) |

### Nhóm 3 — Đề thi thử (FR-010…FR-014)

| Mã | Yêu cầu |
|---|---|
| **FR-010** | `GET /api/exams?hskLevel` SHALL trả đề `PUBLISHED` có ≥ 1 câu dùng được (đề 0 câu bị ẩn, log `WARN` — BR-033-2), mỗi đề `{id, code, title, hskLevel, questionCount, sections:[{index, name, questionCount}], myAttempts, myBest, activeSessionId}`; `questionCount` đếm câu dùng được thật (BR-033-7); `myAttempts` = số lượt `SUBMITTED` của **người gọi**; `myBest` = `{score, maxScore, percent}` trọng số cao nhất hoặc `null`; `activeSessionId` = lượt `IN_PROGRESS` còn hạn hoặc `null`. Sắp `hskLevel`, `code`. `hskLevel` ngoài 1–9 → **422** `VALIDATION_FAILED`. Cấp không có đề → **200** `[]` |
| **FR-011** | `POST /api/exams/{id}/attempts` SHALL: đề không có → **404** `EXAM_NOT_FOUND`; `DRAFT`/`ARCHIVED` → **403** `EXAM_NOT_PUBLISHED`; 0 câu dùng được → **422** `EXAM_HAS_NO_QUESTIONS`; có lượt dở còn hạn → **200** lượt đó (E6). Ngược lại tạo lượt `MOCK_EXAM`, `refId = exam.id`, câu theo `section_index`, `order_index` (không xáo), `sessionMeta.exam = {examId, title, sections:[{index, name}], sectionOf:{no: index}, points:{no: points}}` → **201** `SessionView` (003). Giới hạn lượt chỉ là E-R12 của engine (Q11) |
| **FR-012** | `MockExamHandler.onFinish` SHALL trả `extra = {weightedScore, weightedMaxScore, weightedPercent, bySection:[{index, name, score, maxScore}]}` tính bằng `ExamScoring` (lớp thuần): `weightedMaxScore` = tổng `points` mọi câu đã phát; `weightedScore` = tổng `points` câu đúng; câu bỏ trống/không trả lời = sai (BR-035-5). `attempts.score`/`max_score` vẫn là số câu (engine E-F4) |
| **FR-013** | `GET /api/exam-attempts/{id}/result` SHALL chỉ cho lượt `MOCK_EXAM` của người gọi (khác → **403** `ATTEMPT_NOT_OWNED`); chưa `SUBMITTED` → **409** `ATTEMPT_NOT_SUBMITTED`. Trả `{sessionId, exam:{id, title, hskLevel}, submittedAt, score, maxScore, percent, correctCount, totalCount, bySection[], previous, items[], wrongByKid[]}`: điểm từ `ExamScoring` trên snapshot + `attempt_items`; `previous` = lượt `SUBMITTED` liền trước của người gọi cùng đề `{score, maxScore, percent, submittedAt}` hoặc `null` (BR-038-4/5); `items[] = {no, sectionIndex, stem, options[], yourAnswer, correctKey, result: CORRECT \| WRONG \| BLANK, explanation, primaryKid, primaryKidName, errorKid, errorNote}` — nội dung lấy từ **snapshot**, `explanation = null` khi trống; `errorKid`/`errorNote` của phương án người học chọn (010 `kidsOfQuestions`); `wrongByKid[] = {kid, nameVi, wrong, total}` chỉ gồm KID có câu sai/trống, sắp theo `kid` (ngữ cảnh, **không** xếp hạng — BR-040-3) |
| **FR-014** | `GET /api/exam-attempts/{id}/weak-points` SHALL (quyền như FR-013) trả 3–5 KID xuất hiện ở vai trò chính trong lượt, xếp theo mastery hiện tại tăng dần qua `MasteryReader` (§10; KID chưa có mastery = 0 — BR-040-4), mỗi phần tử `{kid, nameVi, mastery, state}`; đề có < 3 KID → trả đủ số có; không có KID nào → `[]`. **Làm sau khi 011 cung cấp `MasteryReader`** |

### Nhóm 4 — Luyện theo nút (FR-015…FR-016) · SCR-023

| Mã | Yêu cầu |
|---|---|
| **FR-015** | `POST /api/targeted-practice/sessions` body `{kid*, qtype?, excludeQuestionId?}` SHALL: KID không có → **404** `KNOWLEDGE_NODE_NOT_FOUND` (010); lấy câu dùng được (FR-004) có `kid` ở vai trò chính qua `approvedQuestionIds(kid, null, 200)`; ưu tiên cùng `qtype` nếu có, không đủ 10 câu thì bỏ điều kiện `qtype` (BR-041-2/3); bỏ `excludeQuestionId` nếu còn câu khác, nếu chỉ còn đúng câu đó thì giữ và đặt `sessionMeta.repeatedNos = [no]` (BR-041-4/5); chọn ngẫu nhiên tối đa 10 câu; tạo lượt `TARGETED_PRACTICE` → **201** `SessionView`. 0 câu → **200** `{session: null, reason: "NO_QUESTIONS"}` (không tự gọi AI — BR-041-7). Không áp khoá chủ đề (BR-041-8) |
| **FR-016** | `TargetedPracticeHandler.onFinish` SHALL trả `extra = {kid}`; phản hồi từng câu theo `IMMEDIATE` của engine |

### Nhóm 5 — Bài xếp trình độ (FR-017…FR-023)

| Mã | Yêu cầu |
|---|---|
| **FR-017** | `learning.exam.PlacementAlgorithm` SHALL là lớp thuần (không Spring, không DB) cài đúng §0.4: `decide(List<BlockResult>, PlacementConfig, boolean bankExhausted) → Next(level) \| Conclusion(level, confidence)` với `BlockResult(level, correct, total)` |
| **FR-018** | WHEN cần khối cấp `L`, THE system SHALL chọn `block-size` câu dùng được (FR-004) có `hsk_level = L` và KID chính: loại câu đã phát trong lượt này; ưu tiên câu chưa phát ở lượt **hoàn thành** gần nhất của người gọi (E11); xoay vòng theo miền của KID chính (thứ tự miền ngẫu nhiên, ngẫu nhiên trong miền). Không đủ → `bankExhausted = true` (§0.4 bước 4). `sessionMeta.placement = {runId, block, level}` |
| **FR-019** | `GET /api/placement/summary` SHALL trả `{active:{runId, sessionId}\|null, last:{runId, level, confidence, completedAt}\|null}` của người gọi; `active` = lượt chưa kết luận có khối `IN_PROGRESS` còn hạn |
| **FR-020** | `POST /api/placement/runs` body `{startLevel?}` (1–3, sai → **422** `VALIDATION_FAILED`) SHALL: có khối `IN_PROGRESS` còn hạn → **200** khối đó; một cấp 1–3 có < `min-bank-per-level` câu dùng được → **422** `PLACEMENT_BANK_INSUFFICIENT` kèm `details[{level, available}]`; ngược lại tạo khối 1 (`refId = NULL`) → **201** `{runId, session}` |
| **FR-021** | `POST /api/placement/runs/{runId}/next` SHALL (lượt không thuộc người gọi → **403** `ATTEMPT_NOT_OWNED`): có khối `IN_PROGRESS` còn hạn → **200** `{runId, session}`; ngược lại chạy `PlacementAlgorithm` trên các khối `SUBMITTED` → `Next` thì tạo khối mới (`refId = runId`) **201** `{runId, session}`; `Conclusion` thì **200** `{runId, session: null, concluded: true}`. Gọi lại sau khi đã kết luận → cùng kết quả (idempotent) |
| **FR-022** | `GET /api/placement/runs/{runId}/result` SHALL (quyền như FR-021; chưa kết luận → **409** `PLACEMENT_NOT_FINISHED`) trả `{runId, completedAt, level: BELOW_1\|HSK1\|HSK2\|HSK3\|ABOVE_3, confidence: SURE\|TENTATIVE, itemCount, byLevel:[{level, correct, total, percent}], domains:[{domain, correct, total, percent, measured, tentative}], wrongKids:[{kid, nameVi, wrong, total}]}`: `domains` luôn đủ 6 miền `NA CH TV NP NL VB` theo KID chính; miền 0 câu → `measured=false`, `percent=null` ("chưa đo", không đoán); `0 < total < 3` → `tentative=true`; `wrongKids` sắp theo `kid` (ngữ cảnh). Tính lại từ `attempt_items` mỗi lần (không lưu kết quả) |
| **FR-023** | Mỗi khối là một lượt `PLACEMENT` của engine: câu của khối không lộ đáp án trước khi nộp; `finish` theo E-F1…E-F7 của engine; `PlacementHandler.onFinish` trả `extra = {runId, block}` |

### Nhóm 6 — Giao diện (FR-024…FR-027)

| Mã | Yêu cầu |
|---|---|
| **FR-024** | SCR-020 SHALL có thẻ "Kiểm tra trình độ" ở đầu (FR-019: "Bắt đầu" / "Tiếp tục" / kết quả gần nhất + "Làm lại"); lưới đề nhóm theo cấp, chọn cấp chỉ 1–3 (`SUPPORTED_HSK_LEVELS`); thẻ đề: tên, số câu, số phần, số lần làm, điểm cao nhất của mình; nút "Bắt đầu" hoặc "Tiếp tục" (`activeSessionId`). Rỗng: "Đề HSK {n} đang được cập nhật". Không hiện giá, không hiện đề nháp |
| **FR-025** | SCR-021 SHALL có hai chế độ theo `session.source`. **`MOCK_EXAM`:** điều hướng phần/câu tự do, số câu đã làm/còn lại, đánh dấu câu (chỉ ở client), trạng thái "Đã lưu"/"Chưa lưu được" theo kết quả `POST answers` (lỗi không chặn làm tiếp, giữ đáp án ở `sessionStorage`), xác nhận nộp trong trang kèm số câu trống, sau nộp chỉ đọc và sang SCR-022; **không** có đồng hồ đếm ngược. **`PLACEMENT`:** "Khối {n}", 5 câu, nút bỏ qua, nộp khối → gọi `next` → khối mới hoặc sang kết quả; không hiện đáp án từng khối. Cả hai: không hiện đáp án/lời giải trước khi nộp |
| **FR-026** | SCR-022 SHALL có hai chế độ. **Đề thi** (FR-013): điểm trọng số + %, số câu đúng, điểm theo phần, so với lần trước của mình (ẩn khi lần đầu), danh sách câu lọc Tất cả/Sai/Bỏ trống (bỏ trống ghi chữ khác câu sai), đáp án đúng + lời giải ("Lời giải đang được cập nhật" khi trống), câu sai nhóm theo KID với nút "Luyện nút này" → SCR-023 (ẩn khi câu chưa có KID), khối "Điểm yếu cần luyện" (FR-014) bật khi 011 xong. **Không** hiện huy hiệu đỗ/trượt. **Xếp trình độ** (FR-022): cấp ước lượng, "Chắc chắn"/"Kết quả tạm", bảng theo cấp, hồ sơ 6 miền ("Chưa đo" cho miền không có câu), KID có câu sai + "Luyện nút này", nút "Làm lại" |
| **FR-027** | SCR-023 SHALL hiện KID + tên nút, câu từng cái với phản hồi ngay (engine `IMMEDIATE`), ghi chú "Câu bạn vừa làm sai" cho `repeatedNos`; rỗng: "Chưa có bài luyện cho nút này" (không có nút AI đợt này) |

### Key Entities

| Bảng | Dùng thế nào |
|---|---|
| `learning.exams` | Đề; `sections` JSONB chỉ đọc |
| `learning.exam_questions` | Câu trong đề, phần, thứ tự, `points` |
| `learning.questions`, `question_knowledge_points`, `knowledge_points` | Đọc qua entity/dịch vụ của 010 |
| `learning.attempts` | Lượt của engine (`ref_type` §0.1) |
| `learning.attempt_items` | Câu đã chấm, nay có `question_id` |

---

## 4 · Data Model

Không migration. `exams.sections` sau nhập: `[{"index": 1, "name": "Đọc hiểu"}, {"index": 2, "name": "Ngữ pháp"}]`.
`served_items.meta` của `MOCK_EXAM` (chỉ đọc, không có đáp án — engine `rejectAnswerMetadata`):

```json
{ "exam": { "examId": 7, "title": "Đề luyện HSK 2 số 1",
            "sections": [ { "index": 1, "name": "Đọc hiểu" } ],
            "sectionOf": { "1": 1, "2": 1 }, "points": { "1": 2, "2": 1 } } }
```

`PLACEMENT`: `{ "placement": { "runId": 41, "block": 3, "level": 2 } }` (`runId = null` ở khối 1, id lượt khối 1 dùng làm `runId`).

### 4.1 · Mã lỗi dòng mới (bổ sung spec 002 §4.8)

| Mã | Thông báo tiếng Việt (mẫu) |
|---|---|
| `SECTION_NOT_FOUND` | "Đề không có phần số {section_index}" |
| `QUESTION_NOT_USABLE` | "Câu chưa dùng được trong đề: cần APPROVED, trắc nghiệm hoặc đúng/sai, không audio, không ảnh" |
| `LEVEL_MISMATCH` | "Cấp HSK của câu khác cấp của đề" |
| `DUPLICATE_ORDER` | "Thứ tự {order_index} đã có trong phần {section_index}" |

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | spec 001 |
| `FORBIDDEN` | 403 | Sai role | FR-001 |
| `CSRF_TOKEN_INVALID` | 403 | Ghi qua cookie thiếu CSRF | spec 001 |
| `VALIDATION_FAILED` | 422 | Tham số sai | FR-010, FR-015, FR-020 |
| `EXAM_NOT_FOUND` | 404 | Đề không tồn tại | FR-011 |
| `EXAM_NOT_PUBLISHED` | 403 | Đề `DRAFT`/`ARCHIVED` | FR-011 |
| `EXAM_HAS_NO_QUESTIONS` | 422 | Đề 0 câu dùng được | FR-011 |
| `ATTEMPT_NOT_OWNED` | 403 | Lượt/lượt xếp trình độ không có hoặc không thuộc người gọi | FR-013, FR-014, FR-021, FR-022 |
| `ATTEMPT_NOT_SUBMITTED` | 409 | Xem kết quả lượt chưa nộp | FR-013, FR-014 |
| `KNOWLEDGE_NODE_NOT_FOUND` | 404 | KID không có (mã của 010) | FR-015 |
| `PLACEMENT_BANK_INSUFFICIENT` | 422 | Ngân hàng một cấp < `min-bank-per-level`, kèm `details` | FR-020 |
| `PLACEMENT_NOT_FINISHED` | 409 | Xem kết quả khi chưa kết luận | FR-022 |
| `RATE_LIMITED` | 429 | E-R12 của engine | FR-011, FR-015, FR-020, FR-021 |
| Lỗi làm bài (`SESSION_EXPIRED`, `SESSION_ALREADY_SUBMITTED`, `OPTION_NOT_IN_ITEM`, `ITEM_NOT_IN_SESSION`…) | | Theo spec 003 §0.6, §0.8 | FR-023 |
| Lỗi luồng nhập | | Theo spec 002 §5 | FR-005…FR-009 |

**Mã mới trong `ErrorCode.java`** (thứ tự cuối theo thứ tự merge thực tế — luật L2; thêm ngay trước `INTERNAL_ERROR`, sau 6 mã của 010, theo thứ tự): `EXAM_NOT_FOUND`, `EXAM_NOT_PUBLISHED`, `EXAM_HAS_NO_QUESTIONS`, `ATTEMPT_NOT_SUBMITTED`, `PLACEMENT_BANK_INSUFFICIENT`, `PLACEMENT_NOT_FINISHED`.
Đề là tài nguyên chung (không thuộc người dùng) nên trả **404**/**403** theo UC-034; lượt làm bài là tài nguyên của người dùng nên theo `BUS-01` (403, không 404).

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-01** | `contracts/openapi-exam.yaml`; enum `PracticeSource` sửa ở contract 003, `dataType` ở contract 002, cùng PR task tương ứng |
| **AC-03** | Chỉ bảng `learning`; câu và KID qua `learning.knowledge` (cùng module) |
| **AC-05** | Không migration |
| **AC-06** | Nguồn, loại câu, cấp kết luận là enum Java; DB không thêm CHECK |
| **AC-09** | `exams.sections`, `served_items.meta` chỉ đọc; không lọc SQL theo JSONB (đọc trong Java) |
| **AC-10** | 012 không ghi mastery; listener 011 chạy trong transaction `finish` của engine |
| **BUS-01** | Lượt và lượt xếp trình độ: 403 cho không tồn tại/không thuộc |
| **BUS-07** | Chỉ câu `APPROVED` ra người học (BR-037-1, BR-041-6) |
| **BUS-09** | Chấm ở server bằng engine; client chỉ gửi khoá |
| **HR-08** | Không commit đề/câu HSK thật; fixture tự soạn |
| Package | `learning.exam` (`QuestionItemFactory`, `ExamScoring`, `ExamQueryService`, `ExamAttemptService`, `ExamResultService`, `MockExamHandler`, `TargetedPracticeService`, `TargetedPracticeHandler`, `PlacementAlgorithm`, `PlacementConfig`, `PlacementService`, `PlacementHandler`, `web.ExamController`, `web.PlacementController`, `web.TargetedPracticeController`) · `learning.content.importer` (2 validator + 2 writer) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `GET /api/exams?hskLevel` với 20 đề, người gọi có 50 lượt | < 200 ms p95; số truy vấn không tăng theo số đề |
| `NFR-P02` | Bắt đầu đề 200 câu | < 500 ms p95 |
| `NFR-P03` | `GET …/result` đề 200 câu | < 300 ms p95; nhãn KID bằng **một** lời gọi `kidsOfQuestions` |
| `NFR-P04` | `next` / kết quả xếp trình độ (7 khối) | < 300 ms p95 |
| `NFR-Q01` | Mô phỏng `PlacementAlgorithm` 4 000 lượt chạy trong unit test | < 5 s, seed cố định |

---

## 8 · Constitution Check

| Mã | Kết quả |
|---|---|
| **HR-05** | ✅ Ghi qua cookie cần CSRF |
| **HR-07** | ✅ Validate tham số, mẫu nhập; SQL tham số hoá |
| **HR-08** | ✅ Không có đề thật trong repo |
| **AC-05** | ✅ Không migration |
| **AC-09** | ✅ JSONB chỉ đọc |
| **BUS-01** | ✅ 403 cho lượt không thuộc |
| **BUS-04** | ✅ Không đụng lịch ôn |
| **BUS-05** | ✅ Không đụng cổng 90% chủ đề; luyện nút không áp khoá (BR-041-8) |
| **BUS-07** | ✅ Chỉ câu `APPROVED` |
| **BUS-09** | ✅ Chấm server |
| **BUS-16** | ✅ Không đụng (trọng số mastery thuộc 011) |
| UC-033…041 "không mô phỏng thi thật" | ✅ Không đồng hồ (Q1) |
| screen-fields SCR-001/SCR-010 "không bịa luồng placement" | ⚠️ **Xung đột X2** — leader chốt Q7, ghi vào RFC |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Không đếm ngược, hạn 24 giờ (gỡ BLOCKED của SCR-021 về đồng hồ) | Chốt 2026-11-02 |
| Q2 | Chạy trên engine; đợt này chỉ `MULTIPLE_CHOICE`, `TRUE_FALSE` | Chốt 2026-11-02 |
| Q3 | Thêm `questionId` vào engine (§0.2, chỉ thêm) | Chốt 2026-11-02 |
| Q4 | Điểm trọng số theo `exam_questions.points` | Chốt 2026-11-02 |
| Q4b | `points` chụp vào `served_items.meta`, điểm tính lại từ snapshot; `attempts.score` giữ số câu | Chốt 2026-11-02 |
| Q5 | Nạp đề bằng mẫu `EXAM`, `EXAM_QUESTION` | Chốt 2026-11-02 |
| Q6 | Câu sai nhóm theo KID ngay; xếp 3–5 điểm yếu theo mastery chờ 011 | Chốt 2026-11-02 |
| Q7 | Bài xếp trình độ là chế độ của SCR-020/021/022, không thêm màn | Chốt 2026-11-02 — **ghi RFC (X2)** |
| Q8 | Không có câu nghe; miền cần audio ghi "chưa đo" | Chốt 2026-11-02 |
| Q9 | Ngưỡng tạm 60/65/75%, cấu hình được | Chốt 2026-11-02 |
| Q10 | Làm lại bài xếp trình độ không giới hạn | Chốt 2026-11-02 |
| Q11 | Chỉ giới hạn E-R12 của engine | Chốt 2026-11-02 |
| Q12 | Lưu nháp bằng `DEFERRED` của engine, không endpoint riêng | Chốt 2026-11-02 |
| Q13 | Đề miễn phí; 0 migration; FE trước; chỉ file trên máy | Chốt 2026-11-02 |
| X1 | Plan phase 4 ghi "có giờ, hết giờ tự nộp" ↔ UC "không mô phỏng thi thật" | Theo UC (Q1); sửa plan |
| X2 | screen-fields cấm bịa luồng placement ↔ yêu cầu leader | Q7; RFC cập nhật SCR-001/010/020/021/022 |
| X3 | SCR-022 cần xếp theo mastery ↔ mastery thuộc 011 | Q6; FR-014 làm sau 011 |
| X4 | UC-036 "tuỳ chọn chưa duyệt" ↔ engine đã lưu nháp | Q12 |
| X5 | BR-034-5 (2 lần nghe) ↔ engine E-R9 (3 lần) | Không phát sinh (Q8); chốt khi có câu nghe |
| X6 | UC trả 404 cho lượt không có ↔ `BUS-01` | Theo `BUS-01` (như 003) |
| X7 | `database.md` ghi `study_events.event_type='EXAM'` ↔ engine ghi `STUDY` cho mọi nguồn trừ game | Giữ engine (§11) |
| OQ-1 | Ngưỡng đạt từng cấp theo v2.0 | **Mở** — leader đối chiếu PDF, đổi cấu hình |
| OQ-2 | Kết quả thử thật 5–10 người (SC-004) | **Mở** — ghi vào `questions.md` trước demo |

---

## 10 · Móc nối cây tri thức

| Chỗ | 012 cung cấp | Spec 011 làm |
|---|---|---|
| Minh chứng | Event của engine có `source` + `questionId` (FR-003) | Listener (011 FR-005): `MOCK_EXAM` → bối cảnh `EXAM` (1,0), `PLACEMENT` → `QUIZ` (0,8), `TARGETED_PRACTICE` → `PRACTICE` (0,5); KID qua `kidsOfQuestions` |
| Điểm yếu sau bài thi (FR-014) | Cần giao diện `learning.mastery.MasteryReader`: `Map<String, MasteryView> valuesOf(long userId, Set<String> kids)`, `MasteryView(Integer value, String state)` — KID không có minh chứng trả `value = null` | Cài `MasteryReader` (chữ ký khoá khi 011 merge task tương ứng) |
| Kết quả xếp trình độ | `level`, `domains`, `wrongKids` | 3 nút yếu + nút đầu tiên trên biên học tập, nút "Xem cây năng lực" (SCR-019) |
| Luyện nút | `POST /api/targeted-practice/sessions` | Gợi ý ôn (SCR-010/013) gọi lại endpoint này |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Sửa file engine 003 (§0.2) | Chỉ thêm; constructor cũ giữ; T-06 chạy full verify 003–008 |
| Chỉ 2 dạng câu, không nghe | Engine chỉ chấm so khoá; mở rộng sau đợt |
| `FinishResult` của khối xếp trình độ có `correctAnswer` | Câu không lặp trong lượt; FE không hiện; lượt sau ưu tiên câu chưa gặp |
| Làm lại không giới hạn → có thể nhớ câu | Ngân hàng ≥ 30 câu/cấp + ưu tiên câu chưa gặp (FR-018) |
| Kết quả xếp trình độ tính lại mỗi lần | Thuật toán tất định, dữ liệu ≤ 35 câu; đổi `thresholds` thì kết quả cũ hiện theo ngưỡng mới |
| Nhãn KID đọc theo hiện tại, nội dung câu theo snapshot | Nhãn đổi có `audit_logs` (010); BR-109-5 |
| Không gỡ câu khỏi đề qua nhập | Đổi đề = tạo đề mới / lưu trữ đề cũ |
| `study_events` ghi `STUDY` cho đề thi (X7) | Không sửa engine thêm; thống kê không phân biệt đề |
| Thiếu ngân hàng 30 câu/cấp lúc demo | `min-bank-per-level = 10` cho phép chạy, kết quả có thể `TENTATIVE` |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-020 Exam List | `/learn/exams` | `LEARNER` | `GET /api/exams`, `GET /api/placement/summary`, `POST /api/exams/{id}/attempts`, `POST /api/placement/runs` | `screen-fields.md` dòng 9767 |
| SCR-021 Exam Attempt | `/learn/exams/sessions/:sessionId` | `LEARNER` | engine `/api/practice/sessions/{id}` (GET, `answers`, `finish`, `abandon`), `POST /api/placement/runs/{runId}/next` | dòng 10329 |
| SCR-022 Exam Result (đề) | `/learn/exams/results/:sessionId` | `LEARNER` | `GET /api/exam-attempts/{id}/result`, `…/weak-points` | dòng 11206 |
| SCR-022 Exam Result (xếp trình độ) | `/learn/placement/:runId` | `LEARNER` | `GET /api/placement/runs/{runId}/result` | dòng 11206 |
| SCR-023 Targeted Practice | `/learn/practice/targeted` (`?kid&qtype&exclude`) | `LEARNER` | `POST /api/targeted-practice/sessions`, engine | dòng 11825 |

SecurityConfig (`LEARNER`): `GET /api/exams`, `POST /api/exams/{id}/attempts`, `GET /api/exam-attempts/{id}/result`, `GET /api/exam-attempts/{id}/weak-points`, `POST /api/targeted-practice/sessions`, `GET /api/placement/summary`, `POST /api/placement/runs`, `POST /api/placement/runs/{runId}/next`, `GET /api/placement/runs/{runId}/result`.

| Open issue (screen-fields) | Chốt |
|---|---|
| SCR-020: đề trả phí | Miễn phí (Q13) |
| SCR-021: lưu nháp chưa duyệt | Q12 |
| SCR-021: đồng hồ | Q1 — không đếm ngược |
| SCR-021: 20 lượt/ngày, 2 lượt nghe | Q11; không có câu nghe (Q8) |
| SCR-021: độ dài `FILL_BLANK`/`ESSAY` | Ngoài phạm vi (Q2) |
| SCR-021/022: 404 cho bài riêng | `BUS-01` (X6) |
| SCR-022: huy hiệu đỗ/trượt | Không hiện |
| SCR-022: tự gửi bài viết đi chấm | Không có `ESSAY` |
| SCR-023: số câu tối đa | 10 câu/lượt (FR-015) |
| SCR-023: AI sinh câu | Ngoài đợt |

Trạng thái mỗi màn: Loading (skeleton) · Empty (FR-024, FR-027) · Error (thông báo + "Thử lại") · Read-only (SCR-021 sau nộp).

---

## 13 · API Contract

[`contracts/openapi-exam.yaml`](contracts/openapi-exam.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/exams` | 200 `ExamSummary[]` | 401, 403, 422 |
| `POST /api/exams/{id}/attempts` | 201/200 `SessionView` (003) | 401, 403, 404, 422, 429 |
| `GET /api/exam-attempts/{id}/result` | 200 `ExamResult` | 401, 403, 409 |
| `GET /api/exam-attempts/{id}/weak-points` | 200 `WeakPoint[]` | 401, 403, 409 |
| `POST /api/targeted-practice/sessions` | 201 `SessionView` · 200 `NoQuestions` | 401, 403, 404, 422, 429 |
| `GET /api/placement/summary` | 200 `PlacementSummary` | 401, 403 |
| `POST /api/placement/runs` | 201/200 `PlacementStep` | 401, 403, 422, 429 |
| `POST /api/placement/runs/{runId}/next` | 201/200 `PlacementStep` | 401, 403, 429 |
| `GET /api/placement/runs/{runId}/result` | 200 `PlacementResult` | 401, 403, 409 |
| Làm bài `/api/practice/sessions/**` | Spec 003 (enum `PracticeSource` thêm 3 giá trị) | Spec 003 |
| Mẫu nhập `/api/admin/imports/**` | Spec 002 (enum `dataType` + mã lỗi dòng mở rộng) | Spec 002 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | `CONTENT_EDITOR` gọi `GET /api/exams` · T 403; không token · T 401; `POST /api/exams/{id}/attempts` qua cookie thiếu CSRF · T 403 `CSRF_TOKEN_INVALID`; `LEARNER` nhập mẫu `EXAM` · T 403 |
| AT-02 | FR-002 | Full verify: mọi test 003–008 xanh; `PracticeSource.MOCK_EXAM.kind() = EXAM`, `feedback = DEFERRED`, `ttl = 24h`; `PLACEMENT` ttl 2h; `TARGETED_PRACTICE` `IMMEDIATE`; `QUESTION_CHOICE.gradable() = true` |
| AT-03 | FR-003 | Lượt `MOCK_EXAM` nộp xong · T mọi `attempt_items.question_id` khớp câu đã phát, `ItemGradedEvent.questionId` khớp; lượt `TOPIC_TEST` · T `question_id` NULL như cũ; `QUESTION_CHOICE` không `questionId` · T `IllegalArgumentException`; `MEANING_CHOICE` có `questionId` · T `IllegalArgumentException` |
| AT-04 | FR-004 | Câu MC, TF hợp lệ · T `ServedItem` đúng §FR-004; câu có `audio_url`, `image_url`, `FILL_BLANK`, `PENDING_REVIEW`, 2 đáp án đúng · T bị loại |
| AT-05 | FR-005 | `GET templates` · T 13 mẫu, `EXAM` rồi `EXAM_QUESTION` cuối, `dependsOn` đúng §0.3; CSV mẫu có header khớp `columns` |
| AT-06 | FR-006, FR-007 | `sections="2:A\|1:B"`, `"1:A\|1:B"`, 11 phần · T `INVALID_VALUE`; `hsk_level=7` · T lỗi; tạo `PUBLISHED` · T `published_at` có; nhập lại không đổi · T `UNCHANGED` |
| AT-07 | FR-008 | Mỗi ca một dòng lỗi đúng mã: câu không có; phần 3 khi đề có 2 phần; câu `FILL_BLANK`/có audio/`PENDING_REVIEW` (`QUESTION_NOT_USABLE`); câu HSK 3 vào đề HSK 2; hai dòng trùng `(phần, thứ tự)`; đề sẽ có 201 câu |
| AT-08 | FR-009 | Nhập lại đổi `points` · T `UPDATE`; lượt bắt đầu trước đó · T điểm theo `points` cũ (snapshot) |
| AT-09 | FR-010, E7 | 3 đề HSK 2: 1 `DRAFT`, 1 `PUBLISHED` 0 câu dùng được, 1 hợp lệ · T chỉ đề hợp lệ; hai người cùng làm · T mỗi người thấy `myAttempts`/`myBest` của mình; có lượt dở · T `activeSessionId`; `hskLevel=10` · T 422; HSK 3 không đề · T `[]`; số truy vấn không tăng theo số đề |
| AT-10 | FR-011, SC-003 | Bắt đầu · T 201, câu theo phần/thứ tự; JSON `items[]` không có `answer`, `accept`, `explanation`, `is_correct`, `isCorrect`, `correctKey`; `GET` session trước nộp sau khi trả lời · T `answered[]` không có `correct`/`correctAnswer`; đề không có · T 404; `DRAFT` · T 403 `EXAM_NOT_PUBLISHED`; 0 câu · T 422; bắt đầu lần 2 · T 200 cùng `sessionId` |
| AT-11 | FR-012, E4, E5 | Đề 3 câu `points` 2/1/1, đúng câu 1 và 3, câu 2 bỏ trống · T `extra.weightedScore=3`, `weightedMaxScore=4`, `attempts.score=2`, `max_score=3`; hai `finish` song song · T một 200, một 409; `finish` sau 24h · T 422 `SESSION_EXPIRED` |
| AT-12 | FR-013, E1, E3 | Kết quả: `result` phân biệt `WRONG`/`BLANK`; `correctKey`, `explanation` có; câu bị `TEACHER` sửa đáp án sau khi phát · T `correctKey` theo snapshot; câu chưa có KID · T `primaryKid=null`, không vào `wrongByKid`; chọn phương án có `error_kid` · T `errorKid` đúng; lần đầu · T `previous=null`, lần 2 · T có; lượt người khác / không tồn tại · T 403 cùng thông báo; lượt chưa nộp · T 409; lượt `TOPIC_TEST` của chính mình · T 403 |
| AT-13 | FR-014 | Stub `MasteryReader`: 6 KID mastery 70/20/null/90/40/55 · T 5 phần tử thứ tự null(0), 20, 40, 55, 70; đề 2 KID · T 2 phần tử |
| AT-14 | FR-015 | KID 15 câu (8 MC, 7 TF), `qtype=TRUE_FALSE` · T 10 câu: 7 TF + 3 MC; `excludeQuestionId` · T không có câu đó; KID chỉ 1 câu = câu exclude · T 1 câu, `repeatedNos=[1]`; KID 0 câu · T 200 `NO_QUESTIONS`; KID không có · T 404; câu `PENDING_REVIEW` · T không xuất hiện; chủ đề đang khoá · T vẫn tạo lượt |
| AT-15 | FR-016 | Trả lời câu · T phản hồi ngay đúng/sai + lời giải; `finish` · T `extra.kid` |
| AT-16 | FR-017 | Bảng giá trị: khối `[(2,5,5)]` → `Next(3)`; `[(2,2,5)]` → `Next(1)`; `[(2,3,5)]` → `Next(2)`; 5 khối đúng hết từ cấp 2 → `Conclusion(ABOVE_3, SURE)` sau 25 câu; 5 khối sai hết → `Conclusion(BELOW_1, SURE)`; 7 khối dao động → dừng ở 35 câu; `bankExhausted` → `TENTATIVE` |
| AT-17 | FR-018, E11 | Ngân hàng cấp 2 có 3 miền · T khối có ≥ 2 miền; không lặp câu trong lượt; lượt 2 sau lượt hoàn thành · T câu của lượt 1 chỉ được chọn khi hết câu mới |
| AT-18 | FR-019 | Chưa làm · T `{active:null, last:null}`; đang làm khối 2 · T `active.sessionId`; đã xong · T `last.level` |
| AT-19 | FR-020, E8 | Ngân hàng cấp 1 có 9 câu · T 422 `PLACEMENT_BANK_INSUFFICIENT` `details[{level:1, available:9}]`; `startLevel=4` · T 422; bắt đầu · T 201 `refId=null`, `runId = sessionId`; gọi lại · T 200 cùng khối |
| AT-20 | FR-021, E9 | Nộp khối 1 đúng 5/5 → `next` · T 201 khối cấp 3, `refId=runId`; gọi `next` 2 lần · T cùng khối; khối hết hạn · T `next` phát khối mới cùng cấp; đủ điều kiện dừng · T `concluded=true`, gọi lại vẫn vậy; `runId` của người khác · T 403 |
| AT-21 | FR-022 | Lượt 30 câu chỉ có miền TV, NP · T `domains` đủ 6, `NA CH NL VB` `measured=false`; miền 2 câu · T `tentative=true`; chưa kết luận · T 409; người khác · T 403 |
| AT-22 | FR-023 | Khối trước khi nộp · T không lộ đáp án (như AT-10); `finish` · T `extra.runId`, `extra.block` |
| AT-23 | §0.1, E2 | Đề chuyển `ARCHIVED` giữa lượt · T `finish` 200, danh sách không còn đề |
| AT-24 | FR-017, SC-001, SC-002 | Mô phỏng seed cố định, 1 000 lượt cho mỗi trình độ thật T ∈ {dưới 1, 1, 2, 3+}: xác suất đúng 0,85 ở cấp ≤ T, 0,40 ở cấp T+1, 0,30 ở cấp cao hơn · T xếp đúng ≥ 80% mỗi T, lệch > 1 cấp ≤ 2%; người đoán ngẫu nhiên (0,30 mọi cấp) · T không lần nào ra `HSK3`/`ABOVE_3` |
| AT-25 | FR-024 (FE) | Vitest: thẻ "Kiểm tra trình độ" 3 trạng thái; chọn cấp chỉ 1–3; rỗng "đang được cập nhật"; "Tiếp tục" khi có `activeSessionId`; không có giá |
| AT-26 | FR-025 (FE) | Vitest: `MOCK_EXAM` chuyển phần tự do, đếm đã làm/còn lại, lỗi lưu hiện "Chưa lưu được" mà vẫn làm tiếp, xác nhận nộp có số câu trống, không có đồng hồ đếm ngược; `PLACEMENT` nộp khối gọi `next` rồi tải khối mới hoặc sang kết quả |
| AT-27 | FR-026 (FE) | Vitest: chế độ đề — lọc Sai/Bỏ trống, bỏ trống ghi chữ khác sai, "Lời giải đang được cập nhật", không có nút luyện cho câu không KID, không có huy hiệu đỗ/trượt, ẩn so sánh lần đầu; chế độ xếp trình độ — "Chưa đo", "Kết quả tạm" |
| AT-28 | FR-027, FR-005 (FE) | Vitest: phản hồi ngay; ghi chú câu vừa sai; rỗng không có nút AI; SCR-068 có 13 lựa chọn đúng nhãn |
| AT-29 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-exam.yaml` (path + status) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md)

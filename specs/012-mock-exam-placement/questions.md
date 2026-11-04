# Chốt — Spec 012

## 2026-11-04 — T-11 kết quả đề thi thử (leader duyệt)

- Lượt mới lưu `hskLevel` trong `sessionMeta.exam`. Lượt cũ thiếu trường này đọc `hsk_level` theo `examId`, kể cả đề đã `ARCHIVED`. Thiếu cả dòng đề là dữ liệu hỏng và trả 500.
- `previous` lấy lượt đã nộp liền trước của cùng người và cùng đề theo thời điểm nộp, dùng ID khi trùng thời điểm. Phần trăm điểm trọng số làm tròn xuống như T-10.

## 2026-11-04 — T-05 SCR-023 và SCR-068 (leader duyệt)

- SCR-023 lấy `nameVi` từ `GET /api/knowledge-tree/nodes/{kid}` qua API client của exam, mã hoá KID trong URL và gọi song song với bắt đầu lượt. Nếu lỗi lấy tên, chỉ hiện KID và vẫn cho luyện.
- SCR-068 thêm `EXAM` và `EXAM_QUESTION` sau `NODE_LINK`. Khi thiếu metadata máy chủ, giữ lựa chọn và khoá tải mẫu, tải lên.

## 2026-11-04 — T-10 danh sách và bắt đầu đề thi thử (leader duyệt)

- Chọn `myBest` theo `percent` giảm dần. Nếu bằng nhau, chọn `score` cao hơn. Nếu vẫn bằng nhau, chọn lượt nộp sau. Trả cả `score`, `maxScore` và `percent` từ cùng lượt. Ví dụ, 8/10 (80%) đứng trên 9/20 (45%) khi trọng số đề thay đổi giữa hai lượt.

## 2026-11-04 — T-04 SCR-022 (leader duyệt)

- Cờ `SHOW_EXAM_WEAK_POINTS = false` nằm trong `Scr022ExamResultPage.tsx` và được truyền vào View. T-16 nối `getExamWeakPoints` và bật cờ.
- `Scr022ResultRoute` dùng `sessionId` cho kết quả đề và `runId` cho kết quả xếp trình độ. ID không hợp lệ hiển thị không tìm thấy và không gọi API.

## 2026-11-04 — T-03 SCR-021 (leader duyệt)

- Hook nhận chế độ mở theo sessionId riêng và gọi GET trực tiếp. Đổi ID huỷ request cũ và làm mới toàn bộ trạng thái lượt.
- MOCK_EXAM ghi lựa chọn vào sessionStorage theo sessionId trước POST answers, gửi phần chưa đồng bộ khi finish, rồi xoá nháp sau khi nộp thành công.
- PLACEMENT tính bỏ qua là chưa trả lời. Nếu next lỗi sau khi nộp khối, chỉ thử lại next. View không nhận đáp án đúng hay lời giải và không có đồng hồ đếm ngược.

## 2026-11-04 — T-02 SCR-020 (leader duyệt)

- `ExamSummary` không có `status`; `GET /api/exams` chỉ trả đề PUBLISHED theo FR-010. FE giữ nguyên contract, không thêm trường hoặc tự lọc đề nháp; test khẳng định không hiện giá.
- Thẻ kiểm tra trình độ ưu tiên `active` khi có cả `active` và `last`; chỉ hiện kết quả gần nhất khi không có lượt đang làm. Điểm `0%` khác `myBest = null` (chưa có điểm).

## 2026-11-04 — T-08 QuestionItemFactory (leader duyệt)

- Hàm public `isUsable(Question)` dùng chung cho nhập và phát câu; câu sai dạng bị loại, không chuẩn hoá.
- `TRUE_FALSE` phải có tập khoá đúng `{A, B}`; thiếu, thừa hoặc khoá khác đều bị loại. Nhãn phát luôn là A "Đúng", B "Sai", không dùng text trong DB.
- Mọi loại câu: khoá trùng hoặc rỗng bị loại. Audio/image rỗng nghĩa là null hoặc chuỗi trắng.
- WARN chỉ ghi questionId và lý do cố định, không ghi nội dung hay đáp án. Fixture IMPORT có code duy nhất theo V5.

## 2026-11-04 — T-06 engine (leader duyệt)

- Thêm cuối enum `PracticeSource`: `MOCK_EXAM`, `PLACEMENT`, `TARGETED_PRACTICE`; khớp §0.1 và không đổi luật của nguồn cũ.
- `QUESTION_CHOICE` là loại duy nhất bắt buộc có `questionId`; mọi loại khác phải có `questionId = null`. Engine ghi giá trị này ở cả ba đường tạo `attempt_items` và phát trong `ItemGradedEvent`.
- Không migration: `attempts.kind` đã nhận `EXAM`, `attempt_items.question_id` đã có từ V1. Giữ constructor cũ của `ServedItem` và `ItemGradedEvent`; không trả `questionId` cho client.

## 2026-11-04 — T-13 PlacementAlgorithm (leader duyệt)

- Spec §0.5 ghi khối `cnhsk.placement.*` thuộc T-11, nhưng `tasks.md` giao khối này cho T-13. T-13 thêm cấu hình; T-11 không thêm lại.

## 2026-11-02 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md` §12, constitution v1.4.1, spec 003 §0 (engine), `PracticeService`, `PracticeSource`, `ItemType`, `ServedItem`, `PracticeSourceHandler`, `event/*`, `PracticeApi`, `AttemptItem`, `TopicTestService` (mẫu bắt đầu/tiếp tục), `ErrorCode`, `SecurityConfig`, migration V1–V4 (`exams`, `exam_questions`, `attempts`, `attempt_items`), UC-033…UC-041, UC-130/131, `screen-fields.md` SCR-020…SCR-023 (Open Issues), `database.md`, `CONTEXT.md` (DK-13), spec 010 (bản trên máy), plan `261102-2104` phase 4, `cnhsk-web` `routeAccess.ts`, `features/practice/*`.

| #   | Câu hỏi                       | Leader chốt                                                                                                                                                                                                                                                                      |
| --- | ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Q1  | Đồng hồ đề thi                | **Không đếm ngược**, hạn 24 giờ như `TOPIC_TEST`, chỉ hiện thời gian đã làm                                                                                                                                                                                                      |
| Q2  | Bài thi chạy bằng gì          | **Engine 003 + chỉ `MULTIPLE_CHOICE`/`TRUE_FALSE`**: thêm `MOCK_EXAM`, `PLACEMENT` (+ `TARGETED_PRACTICE` cho SCR-023) và `ItemType.QUESTION_CHOICE`; không sửa logic chấm                                                                                                       |
| Q3  | Gắn câu vào lượt              | **Thêm `questionId` vào engine** (`ServedItem`, `PracticeApi`, `ItemGradedEvent`), ghi `attempt_items.question_id`; chỉ thêm, giữ constructor cũ                                                                                                                                 |
| Q4  | Cách tính điểm                | **Điểm trọng số** theo `exam_questions.points`                                                                                                                                                                                                                                   |
| Q4b | Lưu điểm trọng số             | **Snapshot `points` trong `served_items.meta`**, tính lại từ snapshot + `attempt_items`; `attempts.score` giữ số câu đúng                                                                                                                                                        |
| Q5  | Nạp đề                        | **2 mẫu nhập `EXAM` + `EXAM_QUESTION`** (11 → 13 mẫu)                                                                                                                                                                                                                            |
| Q6  | Phân tích điểm yếu SCR-022    | **Câu sai nhóm theo KID ngay; 3–5 điểm yếu theo mastery chờ 011**                                                                                                                                                                                                                |
| Q7  | Bài xếp trình độ              | **Chế độ của SCR-020/021/022**, không thêm màn; ghi xung đột screen-fields vào RFC                                                                                                                                                                                               |
| Q8  | Dạng câu, phần nghe           | **Chỉ MC/TF, không câu nghe**; miền cần audio ghi "chưa đo"                                                                                                                                                                                                                      |
| Q9  | Ngưỡng đạt cấp                | **Tạm 60/65/75%** (file md), cấu hình được; leader đối chiếu PDF v2.0                                                                                                                                                                                                            |
| Q10 | Làm lại bài xếp trình độ      | **Không giới hạn** (spec thêm: ưu tiên câu chưa gặp ở lượt trước)                                                                                                                                                                                                                |
| Q11 | Giới hạn lượt đề thi          | **Chỉ E-R12 của engine** (120 lượt mới/giờ)                                                                                                                                                                                                                                      |
| Q12 | Lưu nháp                      | **Dùng `DEFERRED` của engine**, không làm `PATCH /draft` riêng                                                                                                                                                                                                                   |
| Q13 | Mặc định còn lại              | **Duyệt**: đề miễn phí, 0 migration, 012 chỉ phát event (011 ghi minh chứng), FE trước BE, làm bài qua `/api/practice/sessions`, câu tự soạn, chỉ file trên máy                                                                                                                  |
| Q14 | `ItemType` phía FE trong T-01 | Mô tả T-01 nói thêm `QUESTION_CHOICE`, nhưng `features/practice/types.ts` không có `ItemType` và contract 003 quy định `ServedItemView.type` là `string`. **Không tạo `ItemType`, giữ `type: string`; chỉ dùng literal `QUESTION_CHOICE` khi so sánh.** (leader chốt 2026-11-03) |

### Xung đột tài liệu đã ghi nhận

| #   | Xung đột                                                                                                    | Xử lý                                                                        |
| --- | ----------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| X1  | Plan phase 4 "đề có giờ, hết giờ tự nộp" ↔ UC-033…041 "không mô phỏng thi thật"; SCR-021 BLOCKED vì đồng hồ | Theo UC (Q1); sửa plan phase 4                                               |
| X2  | screen-fields SCR-001/SCR-010 "không bịa màn/luồng placement" ↔ yêu cầu bài xếp trình độ của leader         | Q7; RFC cập nhật SCR-001/010/020/021/022                                     |
| X3  | SCR-022 "Mastery-ranked weak points", cấm xếp theo số câu sai ↔ mastery thuộc 011                           | Q6; FR-014/T-16 chờ 011                                                      |
| X4  | UC-036 "tuỳ chọn chưa duyệt" ↔ engine `DEFERRED` đã lưu nháp                                                | Q12                                                                          |
| X5  | BR-034-5 tối đa 2 lần nghe ↔ engine E-R9 3 lần                                                              | Không phát sinh (Q8)                                                         |
| X6  | UC dùng 404 cho lượt không tồn tại ↔ `BUS-01` (403)                                                         | Theo `BUS-01` như 003                                                        |
| X7  | `database.md` có `study_events.event_type='EXAM'` ↔ engine ghi `STUDY` mọi nguồn trừ game                   | Giữ engine, ghi rủi ro §11                                                   |
| X8  | Plan phase 4 cho bài xếp trình độ 5 dạng câu ↔ engine chỉ chấm so khoá                                      | Q2/Q8: chỉ MC/TF                                                             |
| X9  | `exams.hsk_level` CHECK 1–6 ↔ "DB/API giữ 1–9" (`AGENTS.md` §12)                                            | Mẫu `EXAM` nhận 1–6 theo CHECK; `GET /api/exams` nhận 1–9 (cấp 7–9 trả rỗng) |

### Còn mở

- OQ-1: ngưỡng đạt từng cấp theo Cây Năng Lực v2.0 (leader đối chiếu PDF; đổi `cnhsk.placement.thresholds`).
- OQ-2: kết quả thử thật 5–10 người (T-19) — ghi vào đây trước demo.
- ~~Plan phase 4 cần sửa theo X1, X8~~ — đã sửa 2026-11-03.

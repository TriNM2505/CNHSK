# Chốt — Spec 012

## 2026-11-06 — T-16 điểm yếu theo mastery (leader duyệt)

- Q-T16-01: Sắp mastery tăng dần, đồng điểm thì KID tăng dần. “3–5” nghĩa là lấy tối đa 5 và trả đủ số KID có nếu ít hơn 5, kể cả 0–2.
- Q-T16-02: Đọc mọi câu trong snapshot đã phát, kể cả câu đúng, sai và bỏ trống. Lấy primaryKid theo nhãn hiện tại, loại trùng và tra tên từ cây hiện tại như kết quả đề. Chưa có minh chứng thì mastery = 0 và giữ state do reader trả, thông thường là notyet.
- Q-T16-03: Bổ sung tài liệu runtime cho runId và body placement/targeted practice theo YAML như đã giao từ T-17. Giữ nguyên logic xử lý request và đường dẫn. Kiểm schema runtime cùng endpoint weak-points.

## 2026-11-05 — T-17 tích hợp màn đề thi (leader duyệt)

- SCR-022 mở luyện tập theo KID (FR-026). DTO kết quả hiện không có ID câu. SCR-023 tiếp tục chuyển tham số URL `exclude` hợp lệ thành `excludeQuestionId`. Truyền ID trực tiếp từ câu sai thuộc backlog.
- ID sai dạng, không dương hoặc vượt int64 ở API kết quả đề và hai API placement `next`/`result` trả 403 `ATTEMPT_NOT_OWNED` như ID không tồn tại. Giữ 422 cho ID sai ở API bắt đầu đề theo contract.
- AT-29 kiểm tám endpoint đã triển khai. Trước khi bổ sung annotation, tài liệu runtime chỉ khai báo 200 cho cả tám endpoint. `/weak-points` thuộc T-16.
- Backlog tài liệu runtime: kiểu `runId` của placement và schema body của placement/targeted practice cần mô tả theo YAML. T-17 chỉ đối chiếu path, method và status.
- SCR-021 lấy `runId` từ `refId ?? sessionId`. Nếu `next` lỗi sau khi nộp khối, màn hình giữ khối ở chế độ chỉ đọc và cho người dùng bấm «Thử lại» để gọi lại `next`.

## 2026-11-05 — T-18 dữ liệu demo HSK 1–3 (leader duyệt)

- Mỗi cấp có 30 câu tự soạn (20 trắc nghiệm, 10 đúng/sai), phân bổ CH 8, TV 8, NP 8 và VB 6. NA và NL cần nghe hoặc nói nên hiển thị «Chưa đo» với ngân hàng chỉ có câu hỏi bằng văn bản này (Q8).
- Dùng 12 nút demo đuôi `-901` thuộc dải `-9xx` dành cho dữ liệu demo. Mỗi câu có một KID chính. Bộ dữ liệu không cần cạnh giữa các nút.
- Mỗi cấp có một đề `PUBLISHED` gồm 12 câu trong ba phần. Nạp theo thứ tự `KNOWLEDGE_NODE` → `QUESTION` → `EXAM` → `EXAM_QUESTION` qua luồng import.

## 2026-11-04 — T-09 nhập câu vào đề (leader duyệt)

- Vị trí đang thuộc câu A trong DB vẫn bị giữ ở bước preview, kể cả khi dòng trước dự kiến chuyển A đi. Dòng đưa B vào vị trí đó trả DUPLICATE_ORDER vì mỗi dòng ghi độc lập.
- T-18: muốn đổi vị trí, nhập và xác nhận lần chuyển câu cũ ra một vị trí trống trước, sau đó mới nhập câu khác vào vị trí vừa giải phóng.
- Hai writer EXAM và EXAM_QUESTION cùng lấy khoá theo mã đề, rồi FOR UPDATE dòng đề trước khi kiểm lại và ghi.

## 2026-11-04 — T-07 nhập đề EXAM (leader duyệt)

- Cho phép đổi tên phần giữ nguyên chỉ số, thêm phần và bỏ phần trống. Bỏ hoặc đổi chỉ số phần đang có câu bị từ chối với INVALID_VALUE tại sections. Cấp mới lệch câu đang gắn bị từ chối với LEVEL_MISMATCH tại hsk_level.
- Preview kiểm liên kết theo lô, writer kiểm lại dưới khoá. EXAM_QUESTION có metadata từ T-07, xử lý upload thuộc T-09.

## 2026-11-04 — T-15 chuyển khối và kết quả xếp trình độ (leader duyệt)

- E9: khối mới nhất hết hạn được thay bằng khối cùng cấp và số thứ tự. Các câu đã phát ở khối hết hạn vẫn bị loại khỏi tập câu mới.
- E10: nếu ngân hàng cạn trước khi có khối nào nộp, kết luận `BELOW_1 / TENTATIVE` với 0 câu, đủ sáu miền chưa đo và không có `wrongKids`.
- Chỉ ghi một lần `placement.conclusion = {completedAt, bankExhausted}` vào khối gốc dưới khoá `placement:run:<userId>`. Engine ghi snapshot khi tạo Attempt; cột `served_items` có `updatable=false` nên các cập nhật sau đó của engine không ghi đè dấu mốc.

## 2026-11-04 — T-14 bắt đầu bài xếp trình độ (leader duyệt)

- FR-018 mô tả `runId` trong metadata. T-14 không lưu trường này vì snapshot được dựng trước khi DB cấp ID. Nguồn duy nhất là `refId ?? id`. Snapshot giữ `placement.block`, `placement.level` và `placement.kidOf` theo số câu.
- T-15 ghi một lần `placement.conclusion = {completedAt, bankExhausted}` vào snapshot khối gốc bằng `jsonb_set` dưới cùng khoá `placement:run:<userId>`. T-14 chỉ đọc dấu mốc, lấy lượt hoàn thành gần nhất và tính lại cấp, độ tin cậy. T-15 kiểm engine không ghi đè `served_items`.
- Cấu hình bắt buộc `min-bank-per-level >= block-size`. Sửa T-13 để `ABOVE_3` xét hai khối cuối đúng §0.4, giữ ngưỡng và seed mô phỏng.

## 2026-11-04 — T-12 luyện theo nút (leader duyệt)

- Mỗi POST có câu dùng được tạo lượt mới. Không dùng lại lượt dở, không khoá theo KID.
- Chỉ lưu `repeatedNos: [1]` khi câu duy nhất dùng được chính là `excludeQuestionId`. Không đọc lịch sử câu sai và không thêm cửa sổ thời gian. Kiểm luồng SCR-022 truyền ID câu tại T-17.
- Body sai trả 422 `VALIDATION_FAILED`. KID đúng dạng nhưng không tồn tại trả 404 `KNOWLEDGE_NODE_NOT_FOUND`.

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

## 2026-11-13 — T-19 G0 đã duyệt: chuẩn bị local và P00 kỹ thuật

Base được leader duyệt: `b8e812aae2aa15219c0e9203943a40d699a46cf1`, nhánh `spec/012-t19`. Không gộp main mới vào nhánh. Allowlist đang sửa: questions.md và tasks.md. Không sửa application.yaml hoặc thuật toán.

### Chốt G0 và cách thu thập

- Mục tiêu tuyển 8 người, cần ít nhất 5 lượt người thật hợp lệ, tối đa 10. Danh sách đang chờ leader chốt; hiện có **0 lượt người thật**.
- Mỗi người phải đồng ý trước và được biết chỉ dùng dữ liệu để chỉnh thuật toán. Chỉ ghi mã P01…P10, trình độ biết trước, loại nguồn xác nhận và kết quả. Không thu/đưa tên, email, số điện thoại, ảnh chứng chỉ, mật khẩu hay định danh tài khoản vào repo/PR/nhóm.
- Nguồn xác nhận: chứng chỉ / giáo viên xếp lớp / tự khai. Tự khai báo riêng, không tính vào điều kiện ít nhất 3 người lệch cùng chiều.
- Biết trước HSK3: HSK3 hoặc ABOVE_3 đều khớp. Biết trước HSK4 trở lên: ABOVE_3 khớp; HSK3 ghi “thấp hơn (trần ngân hàng)”, không dùng để chỉnh ngưỡng. Giữ nguyên nhãn gốc hệ thống trong bảng.
- Chỉ đề xuất chỉnh khi ít nhất 3 người đủ điều kiện lệch cùng chiều ở cùng cấp. Mỗi ngưỡng đổi tối đa ±5 điểm phần trăm mỗi vòng; lẻ tẻ chỉ ghi nhận.
- G1 phải ghi giá trị cũ → mới và lý do, chờ leader duyệt trước khi sửa. Nếu có thay đổi cấu hình được duyệt, chạy lại PlacementAlgorithmTest và PlacementSimulationTest 4.000 lượt, giữ nguyên seed/tiêu chí.
- Người thử làm trực tiếp trên máy Đạt. Không mở server ra LAN/internet, không dùng ngrok hay port-forward. Thử từ xa cần leader duyệt riêng.

### Môi trường và cấu hình đã dùng

PostgreSQL 18 / Redis 7 / Mailpit chạy project Docker riêng `cnhsk-t19-1113044249`; profile dev. Log startup đã xác nhận `jdbcUrl...jdbc:postgresql://127.0.0.1:15436/cnhsk_db` trước khi nhập. Kiểm listener: backend 18086, Vite 15176, PostgreSQL 15436, Redis 16386, SMTP 11036, Mailpit 18036 đều bind 127.0.0.1. Không dùng Neon.

JWT_SECRET, CARD_CODE_PEPPER và mật khẩu DB sinh ngẫu nhiên riêng cho local, không dùng pepper/.env demo. Tài khoản CONTENT_EDITOR nhập liệu và learner P00 dùng một lần, không dùng tài khoản nhóm. Mật khẩu chỉ nằm trong bộ nhớ process/environment của phiên; không ghi file hay commit. Không có .env trong worktree.

Cấu hình trên base, không đổi: thresholds {1:60, 2:65, 3:75}; block-size 5; min-items 25; max-items 35; up-at 4; down-at 2; sure-min-items 10; start-level 2; min-bank-per-level 10. Không phát hiện biến môi trường CNHSK_PLACEMENT* ghi đè.

### Nạp T-18 qua giao diện SCR-068

Đăng nhập CONTENT_EDITOR local, xem trước rồi xác nhận từng file theo README; không chèn câu hỏi bằng SQL.

| Thứ tự | File | Loại | Số dòng | HTTP xem trước / xác nhận | Dòng lỗi / trạng thái |
|---|---|---|---:|---|---|
| 1 | exam/00-knowledge-node.csv | KNOWLEDGE_NODE | 12 | 201 / 200 | 0 / COMPLETED |
| 2 | exam/01-question.csv | QUESTION | 90 | 201 / 200 | 0 / COMPLETED |
| 3 | exam/02-exam.csv | EXAM | 3 | 201 / 200 | 0 / COMPLETED |
| 4 | exam/03-exam-question.csv | EXAM_QUESTION | 36 | 201 / 200 | 0 / COMPLETED |

Ngân hàng tự soạn T-18: mỗi cấp HSK 1–3 có 30 câu (20 MC + 10 TF), CH 8 / TV 8 / NP 8 / VB 6. Dữ liệu NA/NL không có trong ngân hàng chữ, không đoán kết quả hai miền này.

### P00 — chỉ kiểm kỹ thuật, không thuộc mẫu SC-004

Chrome headless local: learner mới → SCR-020 → Kiểm tra trình độ → SCR-021, nộp 5 khối → SCR-022. Helper chọn phương án theo chu kỳ để kiểm luồng; không làm bài như một người học, không dùng đáp án đúng để điều khiển lựa chọn. Vì vậy **P00 không xác nhận trình độ của Đạt/người thật, không dùng đánh giá độ chính xác hoặc đề xuất chỉnh ngưỡng**.

| Mã | Trình độ biết trước | Nguồn | Cấp hệ thống | Độ tin cậy | byLevel đúng/tổng (1; 2; 3) | Số khối / câu | Thời gian | Ghi chú |
|---|---|---|---|---|---|---|---|---|
| P00 | Không áp dụng | Kiểm kỹ thuật tự động | BELOW_1 | SURE | 5/15; 4/10; 0/0 (không phát câu cấp 3) | 5 / 25 | 4 giây tự động, từ bắt đầu đến lấy kết quả | Loại khỏi mẫu người thật; không dùng 4 giây để ước lượng thời gian con người |

byLevel API trả cấp 1 = 5/15 (33%), cấp 2 = 4/10 (40%); không có hàng cấp 3 vì chưa phát câu. Bảng ghi 0/0 cho cấp chưa phát, không đổi thành 0% đúng.

| Miền | Đúng/tổng | Percent | measured | tentative | Giao diện |
|---|---|---:|---|---|---|
| NA | 0/0 | null | false | false | Chưa đo |
| CH | 2/6 | 33 | true | false | Đã đo |
| TV | 2/8 | 25 | true | false | Đã đo |
| NP | 1/5 | 20 | true | false | Đã đo |
| NL | 0/0 | null | false | false | Chưa đo |
| VB | 4/6 | 66 | true | false | Đã đo |

HTTP thật: POST /api/placement/runs → 201; 5 lần POST /api/practice/sessions/{id}/finish → 200; next khối 1–4 → 201; next sau khối 5 → 200, concluded=true, session=null; GET /api/placement/runs/{runId}/result → 200. SCR-022 hiện được nút Làm lại và nhãn Chưa đo.

### Kịch bản người thật và bảng chờ thu thập

Dự kiến tổ chức 20–30 phút/người theo G0, **chưa được đo với người thật**. Nhận đồng ý → ghi trình độ và loại nguồn → tài khoản local dùng một lần → SCR-020 → Kiểm tra trình độ (cấp bắt đầu mặc định 2) → làm độc lập qua các khối → SCR-022. Không gợi ý đáp án/tra cứu; dùng lượt hoàn thành đầu tiên. Ghi riêng sự cố mạng, hết hạn hoặc bỏ dở; không tính lượt chưa hoàn thành là kết quả.

Lưu byLevel đúng/tổng của cả cấp 1, 2, 3 từ API/result; cấp không xuất hiện ghi 0/0 (chưa đo). Phân tích G1 theo cấu hình đã dùng. Nếu leader gửi ngưỡng mới, thêm cột “tính lại theo ngưỡng mới”, không ghi đè nhãn kết quả gốc.

| Mã | Trình độ biết trước | Nguồn | Cấp hệ thống | Độ tin cậy | byLevel 1; 2; 3 | Số khối | Thời gian | Ghi chú sáu miền |
|---|---|---|---|---|---|---|---|---|
| P01 | Chưa thu thập | — | — | — | — | — | — | Chờ leader chốt người thử và sự đồng ý |
| P02 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |
| P03 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |
| P04 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |
| P05 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |
| P06 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |
| P07 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |
| P08 | Chưa thu thập | — | — | — | — | — | — | Chưa chạy |

P09/P10 chỉ bổ sung nếu cần và leader chốt. Chưa có cơ sở kết luận SC-004/OQ-2 đạt; không đề xuất thay đổi ngưỡng từ P00. T-19 giữ đang làm, không đánh ✅. T-17 được đánh ✅ theo PR #98, main 2023e6f và xác nhận của leader; không nhận kết quả P00 thay cho nghiệm thu toàn T-17.

Kiểm chuẩn bị: mvnw compile PASS; npm ci PASS; 4 lần nhập 0 lỗi; P00 kỹ thuật PASS; diff/BOM kiểm riêng trên hai file tài liệu. Không thay code/config hay lockfile, không sử dụng dữ liệu HSK thật, không có kết quả người thật được tự tạo.

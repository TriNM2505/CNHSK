# CNHSK — Đặc tả Use Case · Nhóm 2 · Luyện thi HSK

> **UC-033 → UC-041** · 9 use case · Tính năng 2.1 → 2.3
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
>
> ⚠️ **Giới hạn scope đã chốt:** KHÔNG mô phỏng kỳ thi thật — không đếm ngược đúng giờ chuẩn
> HSK, không môi trường thi nghiêm ngặt, không chống gian lận khi thi. Đây là **luyện tập**,
> không phải thi.

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
| --- | --- | --- | --- | --- | --- |
| UC-033 | Xem danh sách đề thi theo cấp HSK | `USER` | P0 | MVP | 2.1 |
| UC-034 | Làm đề thi thử | `USER` | P0 | MVP | 2.1 |
| UC-035 | Nộp bài và nhận điểm | `USER` | P0 | MVP | 2.1 |
| UC-036 | Tạm lưu bài thi đang làm | `USER` | P2 | MVP | 2.1 |
| UC-037 | Luyện riêng một dạng câu hỏi | `USER` | P1 | MVP | 2.2 |
| UC-038 | Xem kết quả chi tiết sau khi thi | `USER` | P1 | MVP | 2.3 |
| UC-039 | Xem lời giải từng câu sai | `USER` | P1 | MVP | 2.3 |
| UC-040 | Xem 3–5 điểm yếu nhất sau bài thi | `USER` | P1 | MVP | 2.3 |
| UC-041 | Bấm "luyện ngay" từ câu sai | `USER` | P1 | MVP | 2.3 |

> **Ánh xạ DB khi sinh code:** `attempt_answers` trong các luồng là
> `learning.attempt_items`; `user_knowledge_state` là `learning.user_progress`
> với `target_type='KNOWLEDGE_POINT'`; `question_options` là mảng JSONB
> `learning.questions.options`. Dùng tên và cấu trúc trong `database.md`, không tạo bảng cũ.

---

# UC-033 · Xem danh sách đề thi theo cấp HSK

| | |
| --- | --- |
| **UC-ID** | UC-033 · **Actor** `USER` · **Pri** P0 · **Scope** MVP · **FT** 2.1 |
| **Client** | Web · Mobile · Shared |

## Mô tả

Người học chọn cấp HSK để xem các đề thi hiện có, số câu, các phần thi và kết quả những lần đã làm. Lịch sử chỉ hiển thị bài làm của chính họ.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `exams` có đề đã ở trạng thái `PUBLISHED`
3. Mỗi đề có ≥ 1 dòng `exam_sections` và các `questions` liên kết

## Hậu điều kiện

Chỉ đọc — không đổi dữ liệu.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang "Luyện thi" |
| 2 | System | `GET /api/exams?hsk_level={n}` |
| 3 | System | Lọc `status = PUBLISHED`, đếm số câu mỗi đề |
| 4 | System | `LEFT JOIN attempts` của người đang đăng nhập để lấy số lần làm + điểm cao nhất |
| 5 | System | Trả danh sách kèm `question_count`, `section_count`, `my_attempts`, `my_best_score` |
| 6 | Client | Hiện lưới đề, nhóm theo cấp HSK, đánh dấu đề đã làm |

## Luồng thay thế

**A1 — Có bài đang làm dở**
Bước 4 phát hiện `attempts` trạng thái `IN_PROGRESS`. Hiện nút "Tiếp tục" thay vì "Bắt đầu"
(dẫn vào UC-036).

**A2 — Cấp HSK chưa có đề**
Hiện "HSK 5–6 đang được cập nhật". Hiện tại chỉ có HSK 1–4, riêng HSK 1 có 17 bộ đề.

**A3 — Không lọc cấp**
`GET /api/exams` trả hết, nhóm theo cấp ở client.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_EXAMS` | 200 (rỗng) | Cấp đó chưa có đề | Không phải lỗi — hiện "đang cập nhật" (A2) |
| `INVALID_HSK_LEVEL` | 400 | `hsk_level` ngoài 1–6 | Chặn |
| `EXAM_HAS_NO_QUESTIONS` | — | Đề `PUBLISHED` nhưng 0 câu hỏi | 🔴 **Ẩn khỏi danh sách**, báo `CONTENT_ADMIN`. Vào rồi mới thấy trống là trải nghiệm tệ nhất |
| `EXAM_QUESTION_COUNT_MISMATCH` | — | `exams.total_questions` lệch so với đếm thật | Dùng số đếm thật, ghi log lệch |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập | `GUEST` **không** xem được danh sách đề |
| `OTHER_USER_ATTEMPTS_LEAKED` | — | Bước 4 join sai, lộ `attempts` người khác | 🔴 Xem ghi chú |

> 🔴 **`OTHER_USER_ATTEMPTS_LEAKED` — lỗi dễ mắc khi viết câu JOIN.** Bước 4 join `attempts`
> để lấy điểm cao nhất. Nếu quên `WHERE attempts.user_id = :currentUser` thì `my_best_score`
> trả điểm của người khác — vừa sai vừa lộ dữ liệu. Không phải IDOR kinh điển (không sửa được
> gì) nhưng vẫn là rò rỉ. **Phải có test riêng cho trường hợp hai người cùng làm một đề.**

## Business rule

| # | Rule |
| --- | --- |
| BR-033-1 | Chỉ các đề ở trạng thái `PUBLISHED` mới được hiển thị cho người học. Đề `DRAFT` hoặc `ARCHIVED` không được xuất hiện trong danh sách luyện thi. |
| BR-033-2 | Đề đã xuất bản nhưng không có câu hỏi hợp lệ thì không được hiển thị cho người học. Hệ thống phải ghi nhận lỗi dữ liệu để người quản trị nội dung xử lý. |
| BR-033-3 | Thông tin lịch sử làm bài như số lần làm và điểm cao nhất chỉ được tính từ bài làm của chính người học đang đăng nhập. Không được lộ điểm hoặc lịch sử làm bài của người khác. |
| BR-033-4 | Người học phải đăng nhập mới xem được danh sách đề thi và lịch sử làm bài cá nhân. |
| BR-033-5 | Nếu cấp HSK chưa có đề, hệ thống trả danh sách rỗng và hiển thị thông báo nội dung đang được cập nhật, không coi đây là lỗi hệ thống. |
| BR-033-6 | Trong MVP, hệ thống ưu tiên các đề HSK đã có dữ liệu thật. HSK 5 và HSK 6 chỉ hiển thị khi đã có đề do thầy hoặc quản trị nội dung cung cấp và xuất bản. |
| BR-033-7 | Số câu và số phần của đề phải được tính từ dữ liệu thật đang liên kết với đề, không chỉ tin vào số tổng đã lưu sẵn nếu có dấu hiệu lệch. |

## API · DB

```
GET /api/exams
GET /api/exams?hsk_level={n}
```

`exams` · `exam_sections` · `questions` · `attempts` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | HSK 1 | 17 bộ đề, kèm lịch sử của mình |
| T2 | HSK 9 | 400 `INVALID_HSK_LEVEL` |
| T3 | HSK 5 | 200 rỗng, hiện "đang cập nhật" |
| T4 | Đề `PUBLISHED` 0 câu | Không xuất hiện |
| T5 | Hai user cùng làm đề A | Mỗi người thấy **điểm của mình** |
| T6 | Có bài `IN_PROGRESS` | Nút "Tiếp tục" |

---

# UC-034 · Làm đề thi thử

| | |
|---|---|
| **UC-ID** | UC-034 · **Actor** `USER` · **Pri** P0 · **Scope** MVP · **FT** 2.1 |

## Mô tả

Người học chọn đề và bắt đầu làm lần lượt các phần nghe, đọc, viết. Các câu trả lời thuộc về một lượt thi đang làm; việc nộp và chấm bài diễn ra khi họ chọn nộp.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Đề tồn tại, `status = PUBLISHED`, có câu hỏi
3. Không có `attempts` nào `IN_PROGRESS` cho đề này (hoặc chọn tiếp tục bài cũ — UC-036)

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Bắt đầu | `attempts` tạo dòng mới `status = IN_PROGRESS`, có `started_at`, `served_at` |
| Trả lời từng câu | Lưu tạm (UC-036), **chưa** chấm, **chưa** đổi mastery |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Bắt đầu làm đề" |
| 2 | System | Kiểm đề khả dụng, kiểm chưa có bài dở |
| 3 | System | `POST /api/exams/{id}/attempts` — tạo `attempts`, ghi `started_at` |
| 4 | System | Trả `attempt_id` + danh sách phần + câu hỏi, **KHÔNG kèm `is_correct` và `explanation`** |
| 5 | Client | Hiện phần 1 (thường là Nghe), phát audio |
| 6 | `USER` | Trả lời từng câu, chuyển phần |
| 7 | Client | Lưu tạm định kỳ (UC-036) |
| 8 | `USER` | Hoàn thành hết phần, bấm "Nộp bài" → UC-035 |

## Luồng thay thế

**A1 — Đã có bài dở cho đề này**
Bước 2 trả `ATTEMPT_IN_PROGRESS_EXISTS` kèm `attempt_id` cũ. Client hỏi "tiếp tục hay bỏ làm
lại". Chọn làm lại → `attempts` cũ chuyển `ABANDONED`, tạo bài mới.

**A2 — Nhảy phần**
Cho phép chuyển qua lại giữa các phần tự do (không mô phỏng thi thật). Nhưng phần Nghe: audio
chỉ phát **giới hạn số lần** để bài còn ý nghĩa.

**A3 — Rời trang giữa bài**
Không mất — UC-036 đã lưu tạm. Lần sau vào thấy nút "Tiếp tục".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `EXAM_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `EXAM_NOT_PUBLISHED` | 403 | Đề `DRAFT` hoặc `ARCHIVED` | 🔴 **Chặn** — đề nháp của `CONTENT_ADMIN` lộ ra là mất giá trị đề |
| `EXAM_HAS_NO_QUESTIONS` | 422 | Đề trống | Không cho bắt đầu |
| `ATTEMPT_IN_PROGRESS_EXISTS` | 409 | Đã có bài dở | Trả `attempt_id` cũ, cho chọn (A1) |
| `TOO_MANY_ATTEMPTS` | 429 | > 20 lượt/ngày | Chặn farm mastery bằng cách làm đề liên tục |
| `ANSWER_KEY_IN_RESPONSE` | — | Bước 4 lỡ trả `is_correct` | 🔴 Xem ghi chú |
| `AUDIO_NOT_AVAILABLE` | 422 | Phần Nghe thiếu audio | Không cho bắt đầu đề — làm đề Nghe không audio là vô nghĩa |
| `SECTION_ORDER_MISSING` | 500 | `exam_sections` không có `order_index` | Phần hiện sai thứ tự |

> 🔴 **`ANSWER_KEY_IN_RESPONSE` là lỗi chết người và rất dễ mắc.** Entity `Question` có quan hệ
> tới `question_options` (chứa `is_correct`) và cột `explanation`. Nếu trả entity thẳng ra
> JSON — hoặc dùng `@JsonIgnore` không đủ, hoặc một DTO quên loại trường — thì **toàn bộ đáp
> án nằm trong response**. Mở DevTools tab Network là thấy hết.
> **Bắt buộc:** DTO riêng cho lúc làm bài (`QuestionForAttemptDto`) và một **test tự động**
> assert response **không chứa** chuỗi `is_correct` / `explanation`.

> ⚠️ **`EXAM_NOT_PUBLISHED`:** dữ liệu đề là của giảng viên, constitution cấm commit. Đề đang
> `DRAFT` mà lộ ra thì người học làm trước, đến lúc `PUBLISHED` thì đề mất giá trị đo lường.

## Business rule

| # | Rule |
| --- | --- |
| BR-034-1 | Người học chỉ được bắt đầu làm các đề ở trạng thái `PUBLISHED`. Đề nháp hoặc đề đã lưu trữ không được mở cho người học. |
| BR-034-2 | Response khi bắt đầu hoặc tải bài đang làm không được chứa đáp án đúng, trường `is_correct`, lời giải chi tiết hoặc bất kỳ dữ liệu nào làm lộ đáp án. |
| BR-034-3 | Mỗi người học chỉ có một bài làm `IN_PROGRESS` cho cùng một đề tại một thời điểm. Nếu đã có bài đang làm, hệ thống phải cho tiếp tục bài cũ hoặc bỏ bài cũ để tạo bài mới. |
| BR-034-4 | Làm đề trong hệ thống là luyện tập, không phải mô phỏng kỳ thi thật. Người học được chuyển qua lại giữa các phần, trừ các giới hạn cần thiết để bảo toàn ý nghĩa học tập. |
| BR-034-5 | Với phần nghe, số lần phát audio phải có giới hạn để bài nghe còn giá trị luyện tập. Mặc định MVP là tối đa 2 lần phát cho mỗi audio, trừ khi đề hoặc dạng bài quy định khác. |
| BR-034-6 | Câu trả lời trong khi đang làm bài chỉ được lưu như dữ liệu đang làm, chưa chấm điểm và chưa cập nhật mastery cho đến khi người học nộp bài. |
| BR-034-7 | Đề thiếu audio bắt buộc ở phần nghe không được cho bắt đầu, vì người học không thể làm đúng mục tiêu của phần nghe. |
| BR-034-8 | Hệ thống có thể giới hạn số lượt bắt đầu làm đề trong ngày để tránh spam dữ liệu tiến độ, nhưng giới hạn này phải là cấu hình hệ thống, không hardcode trong nghiệp vụ. |
| BR-034-9 | Khi bắt đầu bài, server ghi snapshot câu hỏi, các lựa chọn và đáp án đúng vào `attempts.served_items`. Response chỉ trả phần người học cần làm, không chứa đáp án hoặc lời giải. |

## API · DB

```
POST /api/exams/{id}/attempts
GET  /api/attempts/{id}
```

`exams` · `exam_sections` · `questions` · `question_options` (đọc — **lọc bỏ `is_correct`**) · `attempts` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bắt đầu đề HSK1 | 201, `attempt_id`, `status = IN_PROGRESS` |
| T2 | Response bước 4 | **Không** chứa `is_correct`, **không** chứa `explanation` |
| T3 | Đề `DRAFT` | 403 `EXAM_NOT_PUBLISHED` |
| T4 | Bắt đầu lần 2 khi còn bài dở | 409 kèm `attempt_id` cũ |
| T5 | Bắt đầu 21 lượt trong ngày | 429 `TOO_MANY_ATTEMPTS` |
| T6 | Đề Nghe thiếu audio | 422 `AUDIO_NOT_AVAILABLE` |

---

# UC-035 · Nộp bài và nhận điểm

| | |
|---|---|
| **UC-ID** | UC-035 · **Actor** `USER` · **Pri** P0 · **Scope** MVP · **FT** 2.1 |

## Mô tả

Khi người học nộp bài, câu trả lời được chấm trên server. Họ nhận được điểm thi; kết quả từng câu cũng được lưu để xem lại và cập nhật tiến độ học.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `attempts` tồn tại, `status = IN_PROGRESS`, **thuộc người đang đăng nhập**
3. `attempts` chưa quá hạn (24h)

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Thành công | `attempts` → `SUBMITTED` có `submitted_at`, `score`; `attempt_answers` ghi từng câu; `user_knowledge_state` cập nhật mastery cho mọi điểm kiến thức liên quan |
| Thất bại | **Rollback toàn bộ** — `attempts` giữ `IN_PROGRESS` để nộp lại |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Nộp bài" |
| 2 | Client | `POST /api/attempts/{id}/submit` — gửi mảng `{question_id, answer}` |
| 3 | System | Kiểm sở hữu: `attempts.user_id = currentUser` |
| 4 | System | Kiểm `status = IN_PROGRESS` |
| 5 | System | Kiểm mọi `question_id` thuộc đề của `attempt` này |
| 6 | System | **Mở transaction** |
| 7 | System | Chấm từng câu theo đáp án trong `attempts.served_items` đã lưu khi bắt đầu bài |
| 8 | System | Ghi `attempt_answers` — `question_id`, `selected_option_id`, `is_correct`, `answered_at` |
| 9 | System | Tính điểm theo phần và tổng: `score`, `score_by_section` |
| 10 | System | Với mỗi câu, tra `question_knowledge_points` → cập nhật `user_knowledge_state` (FSRS) |
| 11 | System | `attempts.status = SUBMITTED`, ghi `submitted_at`, `score` |
| 12 | System | **Commit transaction** |
| 13 | System | Trả `{score, total, by_section, attempt_id}` |
| 14 | Client | Chuyển sang màn kết quả (UC-038) |

## Luồng thay thế

**A1 — Câu tự luận (`ESSAY`)**
Không chấm tự động được. Ghi `attempt_answers` với `is_correct = NULL`, loại khỏi mẫu số khi
tính điểm, hiện "phần viết cần người chấm" và gợi ý UC-103 (nhờ chấm bài thuê).

**A2 — Thiếu câu trả lời**
Không chặn. Câu thiếu ghi `selected_option_id = NULL`, `is_correct = false`.

**A3 — Nộp khi hết hạn 24h**
`attempts` → `ABANDONED`. **Không chấm, không đổi mastery.** Hiện "bài đã quá hạn".

**A4 — Câu hỏi bị `CONTENT_ADMIN` sửa giữa lúc làm bài**
Vẫn chấm theo snapshot của bài đã phát, không đổi đáp án của người học giữa chừng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ATTEMPT_NOT_OWNED` | 403 | `attempt` của người khác | 🔴 **IDOR** — ghi điểm vào bài người khác |
| `ATTEMPT_ALREADY_SUBMITTED` | 409 | Nộp 2 lần | 🔴 **Bắt buộc** — xem ghi chú |
| `ATTEMPT_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `ATTEMPT_EXPIRED` | 422 | Quá 24h | `ABANDONED`, không chấm (A3) |
| `ANSWER_FOR_UNKNOWN_QUESTION` | 400 | `question_id` không thuộc đề | Gian lận — ghi log |
| `DUPLICATE_ANSWER` | 400 | Hai đáp án cho cùng `question_id` | Chặn, không biết lấy cái nào |
| `OPTION_NOT_IN_QUESTION` | 400 | `option_id` lạ | Gian lận |
| `QUESTION_CHANGED_MID_ATTEMPT` | — | Đáp án đúng bị sửa sau `served_at` | Chấm theo snapshot, ghi nhận phiên bản đã phát |
| `QUESTION_DELETED_MID_ATTEMPT` | — | Câu bị xoá | **Loại câu khỏi mẫu số**, không tính sai cho người học |
| `MALFORMED_QUESTION` | — | 0 hoặc >1 đáp án đúng | Loại khỏi mẫu số, ghi `question_reports` |
| `NO_KNOWLEDGE_POINTS` | — | Câu chưa gắn nhãn kiến thức | Vẫn chấm điểm, **nhưng không cập nhật được mastery**. Ghi log — tính năng 2.3 sẽ mù chỗ này |
| `MASTERY_UPDATE_FAILED` | 500 | Bước 10 lỗi | **Rollback cả điểm** — không được có điểm mà mastery không đổi |
| `SCORE_CALCULATION_FAILED` | 500 | Lỗi tính điểm | Rollback, `attempts` về `IN_PROGRESS` |
| `IMPOSSIBLE_DURATION` | — | `submitted_at − started_at < số câu × 2s` | Ghi cờ `suspicious`, **vẫn chấm** nhưng mastery ×0.5 |

> 🔴 **`ATTEMPT_ALREADY_SUBMITTED` — vì sao không thể bỏ.** Nếu cho nộp lại cùng `attempt_id`:
> người học nộp lần 1 → nhận kết quả từng câu đúng/sai (UC-038) → **sửa đáp án sai** → nộp lại
> → điểm 100%. Vòng lặp này farm được mastery vô hạn và làm mọi thống kê tiến độ thành rác.
> `attempts` phải có unique constraint hoặc kiểm `status` **trong cùng transaction** với lệnh
> ghi (không kiểm trước rồi ghi sau — hai request song song sẽ lọt).

> 🔴 **`QUESTION_CHANGED_MID_ATTEMPT` là tình huống thật với 6 người làm song song.**
> `CONTENT_ADMIN` sửa đáp án đúng của câu X (UC-109) lúc 10:00. Người học bắt đầu bài lúc 9:50,
> nộp lúc 10:05. Chấm theo đáp án nào?
> **Quy tắc:** chấm theo đáp án trong `attempts.served_items` tại thời điểm `served_at`.
> Việc sửa câu hỏi sau đó chỉ áp dụng cho bài bắt đầu mới, không đổi bài đã phát.

> ⚠️ **`NO_KNOWLEDGE_POINTS` nối trực tiếp với phụ thuộc đã ghi ở tính năng 2.3:** "6.3 Nhãn
> kiến thức — không có nhãn thì không chạy". Câu không gắn nhãn thì UC-040 (điểm yếu nhất)
> không thấy nó, và UC-041 (luyện ngay) không biết luyện gì.

## Business rule

| # | Rule |
| --- | --- |
| BR-035-1 | Bài thi thử phải được chấm ở server. Client chỉ gửi câu trả lời của người học, không tự tính điểm cuối cùng. |
| BR-035-2 | Mỗi `attempt_id` chỉ được nộp một lần. Hệ thống phải kiểm tra trạng thái bài làm trong cùng thao tác ghi điểm để tránh hai request nộp song song cùng thành công. |
| BR-035-3 | Người học chỉ được nộp bài làm thuộc sở hữu của chính mình. Mọi thao tác nộp bài phải kiểm tra `attempt.user_id` với người dùng đang đăng nhập. |
| BR-035-4 | Khi nộp bài thành công, việc ghi điểm, ghi câu trả lời, cập nhật mastery và đổi trạng thái bài làm sang `SUBMITTED` phải được thực hiện nhất quán trong cùng một transaction. |
| BR-035-5 | Câu không trả lời được tính là sai, nhưng vẫn phải được ghi nhận để phân tích lỗi sai và thống kê sau bài làm. |
| BR-035-6 | Câu hỏi bị lỗi dữ liệu trong lúc chấm, ví dụ bị xóa hoặc có đáp án đúng không hợp lệ, không được tính sai cho người học. Câu đó phải bị loại khỏi mẫu số tính điểm và được ghi log cho quản trị nội dung. |
| BR-035-7 | Bài làm quá hạn xử lý cho phép thì không được chấm điểm và không được cập nhật mastery. Trạng thái bài làm chuyển sang `ABANDONED` nếu hệ thống có hỗ trợ trạng thái này. |
| BR-035-8 | Kết quả từ bài thi thử có trọng số mastery cao hơn các bài luyện lẻ, vì bài thi được chấm hoàn toàn ở server và bao phủ nhiều điểm kiến thức hơn. |
| BR-035-9 | Dữ liệu `attempt_answers` sau khi đã ghi không được xóa tùy tiện, vì đây là nguồn cho kết quả chi tiết, phân tích lỗi sai và xác định điểm yếu. |
| BR-035-10 | Nếu câu hỏi chưa được gắn nhãn kiến thức, hệ thống vẫn có thể tính điểm bài làm, nhưng không được dùng câu đó để cập nhật mastery hoặc phân tích điểm yếu. Lỗi thiếu nhãn phải được ghi nhận để bổ sung dữ liệu. |
| BR-035-11 | Đáp án chấm điểm phải lấy từ snapshot `attempts.served_items` tại lúc bài bắt đầu. Thay đổi câu hỏi sau `served_at` không được làm đổi kết quả của bài đang làm. |

## API · DB

```
POST /api/attempts/{id}/submit
```

| Bảng | Vai trò |
| --- | --- |
| `attempts` | Đọc (kiểm sở hữu + trạng thái) · **Ghi** (`status`, `score`, `submitted_at`) |
| `questions` · `question_options` | Đọc — **đây là nơi duy nhất đọc `is_correct`** |
| `question_knowledge_points` | Đọc — map câu → điểm kiến thức |
| `attempt_answers` | **Ghi** — một dòng mỗi câu |
| `user_knowledge_state` | **Ghi** — mastery + FSRS |
| `study_sessions` | **Ghi** |

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nộp đủ, đúng 80% | 200 `score = 80`, `attempt_answers` đủ dòng, mastery đổi |
| T2 | Nộp lần 2 | 409 `ATTEMPT_ALREADY_SUBMITTED`, điểm **không đổi** |
| T3 | Hai request nộp **song song** | Chỉ một thành công, cái kia 409 |
| T4 | `attempt` của user khác | 403 `ATTEMPT_NOT_OWNED` |
| T5 | Thiếu 5 câu | 5 câu ghi `is_correct = false` |
| T6 | Bài có câu `ESSAY` | `is_correct = NULL`, không vào mẫu số |
| T7 | Câu bị xoá giữa bài | Loại khỏi mẫu số, điểm không giảm |
| T8 | Lỗi ở bước 10 | Rollback hết, `attempts` vẫn `IN_PROGRESS` |
| T9 | Nộp sau 25h | 422 `ATTEMPT_EXPIRED` |
| T10 | Câu chưa gắn nhãn kiến thức | Vẫn có điểm, ghi log thiếu nhãn |

---

# UC-036 · Tạm lưu bài thi đang làm

| | |
|---|---|
| **UC-ID** | UC-036 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 2.1 |

> ⚠️ **UC này không có trong feature tree 2.1.** Catalog đã đánh dấu "cần chốt có làm không".
> Đặc tả dưới đây là **đề xuất**, chưa phải quyết định.

## Mô tả

Nếu chức năng lưu nháp được triển khai, người học có thể lưu câu trả lời khi đang làm bài để quay lại tiếp tục sau khi mất kết nối hoặc đóng trang. Bản nháp chưa được chấm và không làm thay đổi mastery.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `attempts` `IN_PROGRESS` thuộc người đang đăng nhập

## Hậu điều kiện

Câu trả lời tạm được lưu, **chưa chấm**, `attempts` vẫn `IN_PROGRESS`.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Client | Tự động mỗi 30 giây hoặc khi đổi phần |
| 2 | Client | `PATCH /api/attempts/{id}/draft` — mảng `{question_id, answer}` đã trả lời |
| 3 | System | Kiểm sở hữu + `status = IN_PROGRESS` |
| 4 | System | Ghi bản nháp vào `attempts.draft_answers`, cập nhật `draft_saved_at` |
| 5 | System | Trả `{saved_at, answered_count}` |
| 6 | Client | Hiện "đã lưu lúc HH:mm" |

## Luồng thay thế

**A1 — Mở lại bài dở**
`GET /api/attempts/{id}` trả câu hỏi **kèm** câu trả lời đã lưu tạm, client điền lại.

**A2 — Mất mạng**
Client giữ trong `sessionStorage`, gửi lại khi có mạng. Bản mới ghi đè bản cũ.

**A3 — Nộp bài ngay sau lưu tạm**
UC-035 dùng mảng câu trả lời **client gửi kèm lúc nộp**, không phải bản lưu tạm — tránh lệch.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ATTEMPT_NOT_OWNED` | 403 | Bài người khác | IDOR |
| `ATTEMPT_ALREADY_SUBMITTED` | 409 | Lưu nháp sau khi nộp | Chặn — nếu không thì sửa được bài đã nộp |
| `DRAFT_TOO_LARGE` | 413 | > 200 câu trả lời | Chặn payload lớn bất thường |
| `STALE_DRAFT` | 409 | Bản nháp gửi cũ hơn bản đã lưu | Bỏ qua bản cũ — hai tab cùng lưu sẽ ghi đè lẫn nhau |
| `DRAFT_SAVE_FAILED` | 500 | Lỗi ghi | **Không chặn người học làm tiếp** — chỉ hiện "chưa lưu được", giữ trong `sessionStorage` |

> Nơi lưu đã chốt trong `docs/reference/database.md`: `attempts.draft_answers JSONB`
> và `draft_saved_at`. Lưu nháp mỗi 30 giây, không ghi vào `attempt_answers` trước khi nộp.

> ⚠️ **`STALE_DRAFT`:** người học mở hai tab cùng làm một đề. Tab A lưu 20 câu, tab B lưu 5
> câu sau đó → bản 5 câu ghi đè bản 20 câu. Cần `version` hoặc `saved_at` để bỏ bản cũ.

## Business rule

| # | Rule |
| --- | --- |
| BR-036-1 | UC này là chức năng tùy chọn, không phải nghiệp vụ bắt buộc của MVP. Nơi lưu đã chốt là `attempts.draft_answers`; chỉ gen code khi nhóm đưa chức năng tùy chọn này vào phạm vi triển khai. |
| BR-036-2 | Dữ liệu lưu tạm chỉ phục vụ khôi phục bài đang làm, không được chấm điểm và không được cập nhật mastery. |
| BR-036-3 | Chỉ người sở hữu bài làm mới được lưu hoặc đọc lại bản nháp của bài đó. |
| BR-036-4 | Không được lưu nháp cho bài đã `SUBMITTED` hoặc `ABANDONED`. Sau khi bài đã kết thúc, mọi thay đổi câu trả lời phải bị từ chối. |
| BR-036-5 | Nếu lưu nháp thất bại, người học vẫn được tiếp tục làm bài. Giao diện chỉ thông báo chưa lưu được và có thể giữ tạm dữ liệu ở client. |
| BR-036-6 | Nếu có nhiều bản nháp từ nhiều tab, hệ thống phải dùng bản mới hơn và không để bản cũ ghi đè bản mới. |
| BR-036-7 | Thời hạn tồn tại của bản nháp không được dài hơn thời hạn hợp lệ của bài làm đang làm. |

## API · DB

```
PATCH /api/attempts/{id}/draft
GET   /api/attempts/{id}          (trả kèm bản nháp)
```

`attempts.draft_answers` và `draft_saved_at` (đọc/ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Lưu 20 câu, đóng tab, mở lại | 20 câu hiện lại đúng |
| T2 | Lưu nháp cho bài người khác | 403 |
| T3 | Lưu nháp sau khi nộp | 409 |
| T4 | Hai tab lưu, tab B cũ hơn | Bản của tab B bị bỏ |
| T5 | Redis/DB chết lúc lưu | Người học **vẫn làm tiếp được** |
| T6 | Mở bài dở sau 25h | 422 `ATTEMPT_EXPIRED` |

---

# UC-037 · Luyện riêng một dạng câu hỏi

| | |
|---|---|
| **UC-ID** | UC-037 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 2.2 |

## Mô tả

Người học chọn một dạng câu hỏi HSK để luyện riêng, chẳng hạn điền từ hoặc sắp xếp câu. Họ làm và nhận kết quả theo dạng bài đó mà không cần làm cả đề thi.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Kho `questions` có câu đúng dạng + đúng cấp HSK, `status = APPROVED`

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Trả lời | `attempt_answers` ghi (gắn `attempts` loại `PRACTICE`); mastery cập nhật trọng số vừa |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn dạng bài + cấp HSK + số câu |
| 2 | System | `GET /api/practice/questions?type={t}&hsk_level={n}&count=10` |
| 3 | System | Lọc `status = APPROVED`, ưu tiên điểm kiến thức mastery thấp |
| 4 | System | Tạo `attempts` loại `PRACTICE`, ghi `served_at` |
| 5 | System | Trả câu hỏi **không kèm đáp án** |
| 6 | `USER` | Trả lời từng câu |
| 7 | Client | `POST /api/practice/answer` sau **mỗi** câu (khác UC-035 nộp cả bài) |
| 8 | System | Chấm ở server, ghi `attempt_answers`, cập nhật mastery |
| 9 | System | Trả `{correct, correct_answer, explanation}` — hiện ngay |
| 10 | Client | Hiện đúng/sai + lời giải, sang câu tiếp |

## Luồng thay thế

**A1 — Dạng `ESSAY`**
Không chấm tự động. Hiện đề, cho viết, lưu lại, gợi ý UC-103 (nhờ chấm). **Không** cập nhật
mastery vì không biết đúng sai.

**A2 — Hết câu trong kho**
Trả ít hơn `count`. Nếu 0 câu → gợi ý dùng AI sinh bài (UC-048).

**A3 — Vào từ UC-041 ("luyện ngay")**
Tham số thêm `knowledge_point_id`, lọc câu theo đúng điểm kiến thức đó.

**A4 — Dạng `SENTENCE_ORDER` / `FILL_BLANK`**
Đáp án không phải `option_id` mà là chuỗi hoặc mảng thứ tự. Chấm bằng so sánh chuẩn hoá
(bỏ khoảng trắng, chuẩn hoá dấu câu).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INVALID_QUESTION_TYPE` | 400 | Dạng ngoài 7 dạng | Chặn |
| `NO_QUESTIONS_FOR_TYPE` | 200 (rỗng) | Kho hết câu dạng đó | Gợi ý AI sinh bài (A2) |
| `UNAPPROVED_QUESTION_SERVED` | — | Lọt câu `status = PENDING_REVIEW` | 🔴 Xem ghi chú |
| `ESSAY_NOT_AUTO_GRADABLE` | 200 | Dạng `ESSAY` | Không phải lỗi — không cập nhật mastery (A1) |
| `ANSWER_FORMAT_MISMATCH` | 400 | Gửi `option_id` cho `FILL_BLANK` | Chặn — mỗi dạng có format đáp án riêng |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời 2 lần cùng câu trong một lượt | Chặn farm |
| `OPTION_NOT_IN_QUESTION` | 400 | Đáp án lạ | Gian lận |
| `IMAGE_NOT_AVAILABLE` | 422 | Dạng `IMAGE_MATCH` thiếu ảnh | Loại câu khỏi bộ, báo `CONTENT_ADMIN` |
| `MALFORMED_QUESTION` | 500 | Sai số đáp án đúng | Ẩn câu, ghi `question_reports` |
| `RATE_LIMIT_EXCEEDED` | 429 | > 600 câu/giờ | Chặn bot |

> 🔴 **`UNAPPROVED_QUESTION_SERVED` là ràng buộc bắt buộc của tính năng 3.3.** Feature tree ghi
> rõ: "Mọi câu AI sinh vào hàng đợi duyệt. **Chỉ câu đã duyệt mới đến người học**". UC này là
> nơi câu hỏi đến người học, nên nó là **chốt cuối** thực thi luật đó.
> Quên `WHERE status = 'APPROVED'` là AI sinh câu sai ngữ pháp đi thẳng vào bài luyện — người
> học học sai, và hội đồng bảo vệ sẽ hỏi ngay về kiểm duyệt nội dung AI.

> ⚠️ **`ANSWER_FORMAT_MISMATCH`:** 7 dạng có 3 kiểu đáp án khác nhau — `option_id`
> (`MULTIPLE_CHOICE`, `TRUE_FALSE`, `IMAGE_MATCH`, `SENTENCE_MATCH`), chuỗi (`FILL_BLANK`),
> mảng thứ tự (`SENTENCE_ORDER`), văn bản dài (`ESSAY`). Một endpoint nhận hết thì phải
> validate theo dạng, không thì chấm sai im lặng.

## Business rule

| # | Rule |
| --- | --- |
| BR-037-1 | Chỉ câu hỏi ở trạng thái `APPROVED` mới được đưa vào bài luyện của người học. Câu hỏi do AI sinh hoặc nội dung mới nhập nhưng chưa duyệt không được xuất hiện. |
| BR-037-2 | Bài luyện riêng theo dạng câu hỏi phải được chấm ở server. Client không được tự quyết định đúng/sai cuối cùng. |
| BR-037-3 | Response phát câu hỏi cho người học không được chứa đáp án đúng, lời giải hoặc dữ liệu nội bộ làm lộ đáp án. |
| BR-037-4 | Sau mỗi câu trả lời trong chế độ luyện riêng, hệ thống có thể trả kết quả đúng/sai và lời giải ngay để người học học từ lỗi sai. |
| BR-037-5 | Hệ thống phải validate định dạng câu trả lời theo từng loại câu hỏi. Ví dụ câu chọn đáp án dùng `option_id`, câu điền chỗ trống dùng text, câu sắp xếp câu dùng danh sách thứ tự. |
| BR-037-6 | Câu hỏi được chọn nên ưu tiên các điểm kiến thức người học còn yếu hoặc vừa làm sai, nếu có dữ liệu mastery tương ứng. |
| BR-037-7 | Mastery từ luyện riêng một dạng có trọng số thấp hơn bài thi thử đầy đủ, vì phạm vi hẹp hơn và người học được xem phản hồi ngay sau từng câu. |
| BR-037-8 | Nếu kho không có câu hỏi phù hợp, hệ thống trả danh sách rỗng và thông báo chưa có bài luyện phù hợp. Việc sinh thêm câu bằng AI thuộc use case riêng, không tự động thực hiện trong UC này. |

## API · DB

```
GET  /api/practice/questions?type={t}&hsk_level={n}&count={c}
GET  /api/practice/questions?knowledge_point_id={k}   (từ UC-041)
POST /api/practice/answer
```

`questions` · `question_options` · `question_knowledge_points` (đọc) · `attempts` · `attempt_answers` · `user_knowledge_state` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `type=MULTIPLE_CHOICE`, HSK2, 10 câu | 10 câu `APPROVED`, không kèm đáp án |
| T2 | Kho có câu `PENDING_REVIEW` | **Không** xuất hiện trong response |
| T3 | `type=INVALID` | 400 |
| T4 | Gửi `option_id` cho `FILL_BLANK` | 400 `ANSWER_FORMAT_MISMATCH` |
| T5 | `type=ESSAY` | Lưu bài, mastery **không đổi** |
| T6 | Trả lời câu X lần 2 | 409 |
| T7 | `IMAGE_MATCH` thiếu ảnh | Câu bị loại, báo admin |
| T8 | Kho 0 câu dạng đó | 200 rỗng, gợi ý AI sinh |

---

# UC-038 · Xem kết quả chi tiết sau khi thi

| | |
|---|---|
| **UC-ID** | UC-038 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 2.3 |

## Mô tả

Sau khi thi, người học xem điểm tổng, kết quả từng kỹ năng, số câu đúng sai và thời gian làm bài. Nếu đã thi trước đó, họ cũng thấy kết quả lần này thay đổi ra sao.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `attempts` `status = SUBMITTED`, **thuộc người đang đăng nhập**
3. `attempt_answers` đã ghi đủ

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nộp bài (UC-035) hoặc vào từ lịch sử |
| 2 | System | `GET /api/attempts/{id}/result` |
| 3 | System | Kiểm sở hữu |
| 4 | System | Tổng hợp từ `attempt_answers`: đúng/sai, theo phần, theo kỹ năng |
| 5 | System | Tra lần làm **trước** cùng đề để so sánh |
| 6 | System | Trả `{score, total, correct_count, by_section, by_skill, duration, previous_score, delta}` |
| 7 | Client | Hiện màn kết quả kèm nút "xem câu sai" (UC-039), "điểm yếu" (UC-040) |

## Luồng thay thế

**A1 — Lần đầu làm đề này**
`previous_score = null`, `delta = null`. Client ẩn phần so sánh.

**A2 — Bài có câu `ESSAY` chưa chấm**
Hiện điểm phần tự động + ghi chú "phần viết chưa chấm". `score` **không** gộp phần chưa chấm.

**A3 — Xem lại bài cũ từ lịch sử**
Cùng endpoint. Dữ liệu `attempt_answers` **không bao giờ xoá** nên luôn xem lại được.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ATTEMPT_NOT_OWNED` | 403 | Xem kết quả người khác | 🔴 **IDOR** — `GET /api/attempts/999/result` phải kiểm `user_id` |
| `ATTEMPT_NOT_SUBMITTED` | 422 | Bài còn `IN_PROGRESS` | 🔴 **Chặn** — xem kết quả bài chưa nộp là thấy đáp án trước khi nộp |
| `ATTEMPT_NOT_FOUND` | 404 | ID sai | Về lịch sử |
| `ATTEMPT_ABANDONED` | 422 | Bài quá hạn không chấm | Hiện "bài không hoàn thành", không có điểm |
| `ANSWERS_MISSING` | 500 | `attempt_answers` rỗng dù `SUBMITTED` | Dữ liệu lệch — không tính được kết quả. Ghi log nghiêm trọng |
| `SKILL_BREAKDOWN_UNAVAILABLE` | 200 | Câu chưa gắn nhãn kỹ năng | Trả điểm tổng, `by_skill` rỗng, ghi log |
| `PREVIOUS_ATTEMPT_OF_OTHER_USER` | — | Bước 5 join thiếu `user_id` | 🔴 So sánh với điểm người khác |

> 🔴 **`ATTEMPT_NOT_SUBMITTED` là lỗ hổng lộ đáp án.** Màn kết quả trả đáp án đúng từng câu.
> Nếu cho gọi khi `attempts` còn `IN_PROGRESS`, người học mở tab thứ hai gọi
> `/api/attempts/{id}/result`, đọc hết đáp án, quay lại tab một điền đúng rồi nộp 100%.
> Kiểm `status = SUBMITTED` là **điều kiện bắt buộc**, không phải tùy chọn.

> 🔴 **`PREVIOUS_ATTEMPT_OF_OTHER_USER` — cùng loại lỗi JOIN như UC-033.** Bước 5 tìm "lần
> trước cùng đề": thiếu `AND user_id = :currentUser` là so sánh điểm mình với điểm người khác.
> Hai UC cùng lỗi ở hai chỗ → nên có **một** repository method dùng chung.

## Business rule

| # | Rule |
| --- | --- |
| BR-038-1 | Người học chỉ được xem kết quả của bài làm đã `SUBMITTED`. Không được xem kết quả của bài còn `IN_PROGRESS` để tránh lộ đáp án trước khi nộp. |
| BR-038-2 | Người học chỉ được xem kết quả bài làm của chính mình. Mọi request xem kết quả phải kiểm tra quyền sở hữu bài làm. |
| BR-038-3 | Kết quả chi tiết gồm điểm tổng, số câu đúng/sai, điểm theo phần hoặc kỹ năng nếu dữ liệu đề có nhãn tương ứng. |
| BR-038-4 | Phần so sánh với lần làm trước chỉ được so với lần làm trước của chính người học đó trên cùng đề. |
| BR-038-5 | Nếu đây là lần đầu người học làm đề, hệ thống không hiển thị phần so sánh điểm. |
| BR-038-6 | Nếu một số câu thiếu nhãn kỹ năng hoặc nhãn kiến thức, hệ thống vẫn hiển thị điểm tổng và ghi nhận thiếu dữ liệu để quản trị nội dung bổ sung. |
| BR-038-7 | Dữ liệu câu trả lời sau khi nộp phải được giữ lại để người học có thể xem lại kết quả bài cũ. |

## API · DB

```
GET /api/attempts/{id}/result
GET /api/attempts?exam_id={e}      (lịch sử làm đề)
```

`attempts` · `attempt_answers` · `questions` · `question_knowledge_points` · `exam_sections` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bài đã nộp của mình | 200, đủ `by_section`, `by_skill` |
| T2 | Bài `IN_PROGRESS` | 422 `ATTEMPT_NOT_SUBMITTED` |
| T3 | Bài người khác | 403 `ATTEMPT_NOT_OWNED` |
| T4 | Lần đầu làm đề | `previous_score = null` |
| T5 | Lần 2, cao hơn 10 điểm | `delta = +10` |
| T6 | Hai user cùng làm đề A | Mỗi người so với **lần trước của mình** |
| T7 | Bài có `ESSAY` | Ghi chú "chưa chấm", `score` không gộp |

---

# UC-039 · Xem lời giải từng câu sai

| | |
|---|---|
| **UC-ID** | UC-039 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 2.3 |

## Mô tả

Người học mở một câu làm sai để xem mình đã chọn gì, đáp án đúng là gì và vì sao. Từ lời giải, họ có thể chuyển ngay sang bài luyện liên quan.

## Tiền điều kiện

1. `attempts` `SUBMITTED`, thuộc người đang đăng nhập
2. Có ít nhất một câu sai
3. `questions.explanation` có nội dung

## Hậu điều kiện

Chỉ đọc. Nhưng là **điểm đầu vào** của UC-041.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Xem câu sai" từ UC-038 |
| 2 | System | `GET /api/attempts/{id}/wrong-answers` |
| 3 | System | Kiểm sở hữu + `status = SUBMITTED` |
| 4 | System | Lấy `attempt_answers WHERE is_correct = false` |
| 5 | System | Join `questions` lấy đề, `question_options` lấy đáp án đúng, `explanation` |
| 6 | System | Join `question_knowledge_points` lấy điểm kiến thức |
| 7 | Client | Hiện từng câu: đề · đáp án mình · đáp án đúng · lời giải · nhãn kiến thức · nút luyện |

## Luồng thay thế

**A1 — Đúng hết**
Trả rỗng. Hiện "bạn làm đúng tất cả câu".

**A2 — Câu sai không có lời giải**
Hiện đáp án đúng, phần lời giải ghi "chưa có lời giải". Cho người học bấm báo lỗi.

**A3 — Xem cả câu đúng**
`?include_correct=true` — có người muốn xem lại hết.

**A4 — Câu bỏ trống**
`selected_option_id = NULL`. Hiện "bạn không trả lời câu này" thay vì "bạn chọn sai".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ATTEMPT_NOT_OWNED` | 403 | Bài người khác | IDOR |
| `ATTEMPT_NOT_SUBMITTED` | 422 | Chưa nộp | 🔴 **Đây là endpoint lộ đáp án trực tiếp nhất** — chặn tuyệt đối |
| `NO_WRONG_ANSWERS` | 200 (rỗng) | Đúng hết | Không phải lỗi (A1) |
| `EXPLANATION_MISSING` | 200 | `explanation` rỗng | Hiện đáp án đúng, ghi log để `CONTENT_ADMIN` bổ sung (A2) |
| `NO_KNOWLEDGE_POINT` | 200 | Câu chưa gắn nhãn | **Ẩn nút "luyện ngay"** — không biết luyện gì. Ghi log |
| `QUESTION_DELETED` | 200 | Câu bị xoá sau khi làm | Hiện "câu hỏi đã bị xoá", vẫn hiện đáp án mình đã chọn từ `attempt_answers` |
| `OPTION_DELETED` | 500 | `question_options` bị xoá | Không biết đáp án đúng là gì. Chỉ hiện đề + lời giải |

> 🔴 **`ATTEMPT_NOT_SUBMITTED` ở UC này nguy hiểm hơn UC-038.** UC-038 trả điểm tổng hợp;
> UC này trả **đáp án đúng + lời giải từng câu**. Gọi được khi bài chưa nộp là đọc thẳng
> đáp án. Cả hai UC phải kiểm cùng một chỗ — nên đặt ở **một** guard dùng chung
> (`AttemptAccessGuard`) chứ không copy điều kiện vào từng controller.

> ⚠️ **`QUESTION_DELETED` — vì sao `attempt_answers` không được xoá.** DB v5 ghi
> `attempt_answers` là "5 bảng không được đụng, gốc của mọi phân tích lỗi sai". Nếu
> `CONTENT_ADMIN` xoá câu hỏi và cascade xoá `attempt_answers` thì lịch sử làm bài của người
> học biến mất. **Khoá ngoại tới `questions` phải là `ON DELETE RESTRICT` hoặc dùng soft delete.**

## Business rule

| # | Rule |
| --- | --- |
| BR-039-1 | Người học chỉ được xem lời giải của bài làm đã `SUBMITTED` và thuộc sở hữu của chính mình. |
| BR-039-2 | Với mỗi câu sai, hệ thống hiển thị đề bài, đáp án người học đã chọn, đáp án đúng, lời giải nếu có và nhãn kiến thức liên quan nếu có. |
| BR-039-3 | Câu bỏ trống phải được hiển thị khác với câu chọn sai, để người học biết mình không trả lời chứ không phải chọn nhầm. |
| BR-039-4 | Nếu câu sai thiếu lời giải, hệ thống vẫn hiển thị đáp án đúng và thông báo lời giải đang được cập nhật; không chặn toàn bộ màn hình. |
| BR-039-5 | Nếu câu sai chưa có nhãn kiến thức, hệ thống không hiển thị nút “luyện ngay” cho câu đó vì không biết cần luyện điểm kiến thức nào. |
| BR-039-6 | Câu hỏi đã được dùng trong lịch sử làm bài không được xóa cứng nếu còn được `attempt_answers` tham chiếu. Hệ thống phải dùng soft delete hoặc chặn xóa để giữ lịch sử học tập. |
| BR-039-7 | Endpoint xem câu sai không được hoạt động với bài đang làm dở, vì đây là endpoint lộ đáp án trực tiếp. |

## API · DB

```
GET /api/attempts/{id}/wrong-answers
GET /api/attempts/{id}/wrong-answers?include_correct=true
```

`attempts` · `attempt_answers` · `questions` · `question_options` · `question_knowledge_points` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bài sai 5 câu | 5 câu kèm đáp án đúng + lời giải |
| T2 | Bài `IN_PROGRESS` | 422 — **không lộ đáp án** |
| T3 | Bài người khác | 403 |
| T4 | Đúng hết | 200 rỗng |
| T5 | Câu không có `explanation` | Vẫn hiện đáp án đúng |
| T6 | Câu không gắn nhãn kiến thức | Không có nút "luyện ngay" |
| T7 | Xoá `questions` có `attempt_answers` | DB **chặn** (RESTRICT) |
| T8 | Câu bỏ trống | Hiện "không trả lời", không phải "chọn sai" |

---

# UC-040 · Xem 3–5 điểm yếu nhất sau bài thi

| | |
|---|---|
| **UC-ID** | UC-040 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 2.3 |

## Mô tả

Sau bài thi, người học xem những điểm kiến thức yếu nhất trong chính đề vừa làm, xếp theo mức độ nắm vững từ thấp đến cao. Hệ thống trả tối đa năm điểm; nếu đề có ít hơn ba điểm kiến thức hợp lệ thì hiển thị số lượng hiện có.

> **Nguồn dữ liệu:** `user_knowledge_state.mastery`, **không phải** đếm câu sai trong
> `attempt_answers`. Đã xác nhận khi phân tích phương án DB.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Bài thi vừa nộp đã cập nhật `user_knowledge_state` (UC-035 bước 10)
3. Câu hỏi trong đề **đã gắn nhãn** `question_knowledge_points`

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Điểm yếu của tôi" từ màn kết quả |
| 2 | System | `GET /api/attempts/{id}/weak-points` |
| 3 | System | Kiểm sở hữu + `SUBMITTED` |
| 4 | System | Lấy tập điểm kiến thức **xuất hiện trong đề này** |
| 5 | System | Với mỗi điểm: đọc `user_knowledge_state.mastery` hiện tại |
| 6 | System | Sắp theo `mastery` thấp → cao, lấy 5 đầu |
| 7 | System | Kèm số câu sai trong bài này cho **ngữ cảnh** (không phải tiêu chí sắp) |
| 8 | Client | Hiện danh sách kèm nút "luyện ngay" mỗi điểm (UC-041) |

## Luồng thay thế

**A1 — Đề có ít hơn 3 điểm kiến thức**
Trả đúng số có. Không cố lấy điểm kiến thức ngoài đề — sẽ không liên quan tới bài vừa làm.

**A2 — Mọi điểm đều mastery cao**
Vẫn trả 5 điểm thấp nhất, kèm cờ `all_strong: true` để client hiện "bạn nắm khá đều, đây là
phần còn có thể cải thiện".

**A3 — Xem điểm yếu toàn cục (không theo bài)**
`GET /api/me/weak-points` — lấy từ toàn bộ `user_knowledge_state`, không giới hạn theo đề.
Dùng cho UC-047 và UC-048.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_KNOWLEDGE_POINTS_IN_EXAM` | 422 | 🔴 Đề **chưa gắn nhãn** câu nào | Xem ghi chú |
| `ATTEMPT_NOT_OWNED` | 403 | Bài người khác | IDOR |
| `ATTEMPT_NOT_SUBMITTED` | 422 | Chưa nộp | Chặn |
| `KNOWLEDGE_STATE_MISSING` | — | Điểm kiến thức chưa có dòng `user_knowledge_state` | Coi `mastery = 0` → xếp đầu danh sách yếu. **Hợp lý**: chưa học gì thì yếu nhất |
| `MASTERY_NOT_UPDATED_YET` | — | UC-035 bước 10 thất bại | Điểm yếu tính theo mastery **cũ**, sai lệch. Cần UC-035 đảm bảo cùng transaction |
| `ALL_MASTERY_EQUAL` | 200 | Mọi điểm cùng mastery | Sắp thêm theo số câu sai trong bài để có thứ tự ổn định |

> 🔴 **`NO_KNOWLEDGE_POINTS_IN_EXAM` — đây là phụ thuộc feature tree đã ghi rõ:**
> "2.3 phụ thuộc **6.3 Nhãn kiến thức** — không có nhãn thì không chạy".
> `question_knowledge_points` cũng nằm trong "5 bảng tuyệt đối không đụng" với lý do
> "**không sửa được nếu không nhập lại dữ liệu**".
>
> **Hệ quả cụ thể:** nhập 17 bộ đề HSK1 mà quên gắn nhãn → UC-040 trả rỗng, UC-041 không có
> gì để luyện, UC-047 và UC-048 (lộ trình thông minh) cũng mù. Tính năng "bán được nhất" biến
> thành màn hình trống.
> **Bắt buộc:** UC-110 (nhập dữ liệu) phải có validate "mọi câu phải có ≥ 1 nhãn kiến thức",
> và UC-111 phải báo rõ câu nào thiếu.

> ⚠️ **`MASTERY_NOT_UPDATED_YET` là hệ quả trực tiếp của `MASTERY_UPDATE_FAILED` ở UC-035.**
> Vì UC này đọc mastery chứ không đếm câu sai, nếu bước cập nhật mastery lỗi mà điểm vẫn ghi
> thì điểm yếu hiện ra là của **trước khi làm bài**. Đó là lý do BR-035-4 bắt cùng transaction.

## Business rule

| # | Rule |
| --- | --- |
| BR-040-1 | Điểm yếu sau bài thi phải được xác định từ các điểm kiến thức xuất hiện trong đề vừa làm và trạng thái mastery hiện tại của người học. |
| BR-040-2 | Hệ thống trả từ 3 đến 5 điểm yếu nhất, sắp xếp theo mastery từ thấp đến cao. Nếu đề có ít hơn 3 điểm kiến thức thì trả đúng số lượng hiện có. |
| BR-040-3 | Số câu sai trong bài chỉ dùng để giải thích ngữ cảnh cho người học, không phải tiêu chí chính để xếp hạng điểm yếu. |
| BR-040-4 | Nếu một điểm kiến thức chưa có trạng thái học tập của người học, hệ thống coi mastery của điểm đó là 0 trong ngữ cảnh phân tích điểm yếu. |
| BR-040-5 | Chỉ các câu đã được gắn nhãn kiến thức mới có thể đóng góp vào phân tích điểm yếu. Nếu đề không có nhãn kiến thức, hệ thống không thể tạo danh sách điểm yếu đáng tin cậy. |
| BR-040-6 | Mỗi điểm yếu hiển thị phải có định danh điểm kiến thức để người học có thể bấm “luyện ngay”. |
| BR-040-7 | Nếu tất cả điểm kiến thức trong bài đều có mastery cao, hệ thống vẫn có thể trả các điểm thấp nhất tương đối và hiển thị thông báo rằng người học đang nắm khá đều. |

## API · DB

```
GET /api/attempts/{id}/weak-points
GET /api/me/weak-points              (toàn cục — A3)
```

`attempt_answers` · `question_knowledge_points` · `knowledge_points` · `user_knowledge_state` (đọc)

> **Index bắt buộc** (theo feature tree 3.1): `(user_id, mastery)` — thiếu index này thì truy
> vấn sắp theo mastery quét toàn bảng.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đề gắn nhãn đủ, sai 8 câu | 5 điểm yếu sắp theo mastery tăng |
| T2 | Đề chưa gắn nhãn câu nào | 422 `NO_KNOWLEDGE_POINTS_IN_EXAM` |
| T3 | Đề chỉ có 2 điểm kiến thức | Trả 2, không bù thêm |
| T4 | Điểm kiến thức chưa từng học | `mastery = 0`, xếp đầu |
| T5 | Bài người khác | 403 |
| T6 | Mọi mastery = 0.9 | 200, cờ `all_strong: true` |
| T7 | Mỗi điểm yếu | Có `knowledge_point_id` để UC-041 dùng |

---

# UC-041 · Bấm "luyện ngay" từ câu sai

| | |
|---|---|
| **UC-ID** | UC-041 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 2.3 |

## Mô tả

Người học bấm “Luyện ngay” ở một câu sai hoặc điểm yếu để luyện đúng điểm kiến thức liên quan. Hệ thống ưu tiên câu hỏi cùng dạng với câu vừa sai; nếu không đủ câu, có thể dùng dạng khác của cùng điểm kiến thức.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Câu sai / điểm yếu có `knowledge_point_id`
3. Kho có câu `APPROVED` cho điểm kiến thức đó

## Hậu điều kiện

Chuyển vào UC-037 với bộ lọc đã đặt. Không tạo dữ liệu mới ở UC này.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Luyện ngay" tại một câu sai |
| 2 | Client | Lấy `knowledge_point_id` + `question_type` của câu sai đó |
| 3 | Client | `GET /api/practice/questions?knowledge_point_id={k}&type={t}&count=10` |
| 4 | System | Lọc `status = APPROVED`, cùng điểm kiến thức, **loại câu vừa làm sai** khỏi bộ đầu |
| 5 | System | Trả bộ câu luyện |
| 6 | Client | Vào luồng UC-037 |
| 7 | `USER` | Luyện; mastery cập nhật qua UC-037 |

## Luồng thay thế

**A1 — Kho không có câu nào khác cùng điểm kiến thức**
Nới lỏng: bỏ lọc `type`, chỉ giữ `knowledge_point_id`. Nếu vẫn rỗng → gợi ý UC-048 (AI sinh
bài theo điểm yếu).

**A2 — Chỉ có đúng câu vừa làm sai**
Cho luyện lại câu đó, nhưng đánh cờ `same_question: true` để client hiện "đây là câu bạn vừa
làm sai".

**A3 — Vào từ UC-040 (điểm yếu) thay vì UC-039 (câu sai)**
Không có `question_type`. Lấy dạng câu hỏi **phổ biến nhất** của điểm kiến thức đó.

**A4 — Điểm kiến thức thuộc chủ đề đang khoá**
Vẫn cho luyện. Điểm kiến thức không giống chủ đề — người học đã gặp câu này trong đề thi rồi,
chặn lại là vô lý.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_KNOWLEDGE_POINT` | 422 | Câu sai chưa gắn nhãn | 🔴 Nút "luyện ngay" **không nên hiện** (BR-039-3). Nếu vẫn gọi → 422 |
| `KNOWLEDGE_POINT_NOT_FOUND` | 404 | `knowledge_point_id` sai | Chặn |
| `NO_PRACTICE_QUESTIONS` | 200 (rỗng) | Kho hết câu cho điểm đó | Nới lọc (A1), rồi gợi ý AI sinh bài |
| `ONLY_SAME_QUESTION_AVAILABLE` | 200 | Chỉ còn đúng câu vừa sai | Cho luyện, cờ `same_question` (A2) |
| `TYPE_MISMATCH_FOR_KNOWLEDGE_POINT` | 200 | Điểm kiến thức không có câu dạng đó | Bỏ lọc `type`, ghi log lệch dữ liệu |
| `UNAPPROVED_QUESTION_SERVED` | — | Lọt câu chưa duyệt | 🔴 Cùng rủi ro UC-037 — lọc `APPROVED` ở **repository**, không ở controller |
| `RATE_LIMIT_EXCEEDED` | 429 | Bấm liên tục | Chặn |

> ⚠️ **`TYPE_MISMATCH_FOR_KNOWLEDGE_POINT` là lệch dữ liệu âm thầm.** Người học sai một câu
> `FILL_BLANK` về điểm ngữ pháp "把 structure". Bấm "luyện ngay" → kho chỉ có câu
> `MULTIPLE_CHOICE` cho điểm đó. Nghiệm thu nói "mở **đúng dạng**" nhưng dữ liệu không cho.
> **Không nên trả rỗng** — luyện đúng điểm kiến thức sai dạng vẫn tốt hơn không luyện gì.
> Nhưng phải ghi log để `CONTENT_ADMIN` biết kho thiếu dạng nào.

> ⚠️ **A4 là một quyết định nghiệp vụ dễ bỏ sót.** Chủ đề khoá (UC-026 `TOPIC_LOCKED`) và điểm
> kiến thức yếu là **hai trục khác nhau**. Đề thi HSK3 chứa điểm kiến thức của chủ đề người học
> chưa mở. Nếu áp luật khoá chủ đề vào đây thì bấm "luyện ngay" báo 403 — người học không hiểu
> vì sao hệ thống chỉ ra điểm yếu rồi lại không cho luyện.

## Business rule

| # | Rule |
| --- | --- |
| BR-041-1 | Nút “luyện ngay” chỉ hiển thị khi câu sai hoặc điểm yếu có `knowledge_point_id` hợp lệ. |
| BR-041-2 | Khi người học bấm “luyện ngay” từ một câu sai, hệ thống ưu tiên lấy câu hỏi cùng điểm kiến thức và cùng dạng câu hỏi với câu vừa sai. |
| BR-041-3 | Nếu không có đủ câu cùng dạng, hệ thống được bỏ điều kiện dạng câu hỏi nhưng vẫn phải giữ điều kiện cùng điểm kiến thức. |
| BR-041-4 | Nếu có câu khác phù hợp, hệ thống nên loại câu người học vừa làm sai khỏi bộ luyện đầu tiên để tránh lặp lại ngay cùng một câu. |
| BR-041-5 | Nếu chỉ còn đúng câu người học vừa làm sai, hệ thống vẫn có thể cho luyện lại câu đó nhưng phải đánh dấu rõ đây là câu vừa sai. |
| BR-041-6 | Chỉ câu hỏi `APPROVED` mới được dùng cho “luyện ngay”. Câu chưa duyệt không được đưa đến người học. |
| BR-041-7 | Nếu không có câu hỏi phù hợp, hệ thống trả danh sách rỗng và thông báo chưa có bài luyện cho điểm kiến thức này. Việc sinh câu hỏi mới bằng AI thuộc use case riêng, không tự động chạy trong UC này. |
| BR-041-8 | Không áp dụng luật khóa chủ đề cho “luyện ngay” từ câu sai. Người học đã gặp điểm kiến thức đó trong đề thi, nên phải được luyện lại điểm yếu tương ứng. |

## API · DB

```
GET /api/practice/questions?knowledge_point_id={k}&type={t}
```

`questions` · `question_knowledge_points` · `knowledge_points` (đọc)

> **Index bắt buộc:** `question_knowledge_points(knowledge_point_id)` — truy vấn ngược từ điểm
> kiến thức về câu hỏi là đường nóng của cả tính năng 2.3 và 3.3.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Câu sai có nhãn, kho có câu cùng dạng | 10 câu cùng điểm + cùng dạng |
| T2 | Kho chỉ có dạng khác | Trả câu dạng khác, ghi log lệch |
| T3 | Kho rỗng cho điểm đó | 200 rỗng, gợi ý AI sinh |
| T4 | Chỉ còn đúng câu vừa sai | Trả câu đó, cờ `same_question` |
| T5 | Câu chưa gắn nhãn | 422 (và nút đã bị ẩn ở UC-039) |
| T6 | Điểm kiến thức thuộc chủ đề khoá | **Vẫn luyện được** |
| T7 | Kho có câu `PENDING_REVIEW` | **Không** xuất hiện |

---

# Tổng hợp exception nhóm 2

## Tám exception quan trọng nhất

| # | UC | Exception | Vì sao |
| --- | --- | --- | --- |
| 1 | UC-034 | `ANSWER_KEY_IN_RESPONSE` | Trả entity thẳng ra JSON là **toàn bộ đáp án nằm trong response**. Cần DTO riêng + test tự động |
| 2 | UC-035 | `ATTEMPT_ALREADY_SUBMITTED` | Nộp lại sau khi xem kết quả = farm mastery vô hạn. Phải kiểm **trong** transaction |
| 3 | UC-039 | `ATTEMPT_NOT_SUBMITTED` | Endpoint lộ đáp án trực tiếp nhất — gọi được khi chưa nộp là đọc thẳng đáp án |
| 4 | UC-040 | `NO_KNOWLEDGE_POINTS_IN_EXAM` | Quên gắn nhãn khi nhập đề → tính năng "bán được nhất" thành màn hình trống |
| 5 | UC-035 | `MASTERY_UPDATE_FAILED` | Có điểm mà mastery không đổi → UC-040 chỉ điểm yếu **sai** |
| 6 | UC-037 · UC-041 | `UNAPPROVED_QUESTION_SERVED` | Câu AI chưa duyệt đến người học — vi phạm luật kiểm duyệt của 3.3 |
| 7 | UC-036 | `DRAFT_SAVE_FAILED` | Lưu nháp thất bại thì người học vẫn tiếp tục làm bài và được báo trạng thái chưa lưu |
| 8 | UC-035 | `QUESTION_CHANGED_MID_ATTEMPT` | Chấm theo `attempts.served_items` để thay đổi của admin không ảnh hưởng bài đã phát |

## Bốn nhóm exception lặp lại khắp nhóm 2

| Nhóm | Xuất hiện ở | Bài học |
| --- | --- | --- |
| **Lộ đáp án** | UC-034 · UC-038 · UC-039 | Ba đường lộ khác nhau: DTO trả thừa trường · xem kết quả bài chưa nộp · xem câu sai bài chưa nộp. Cần **một** `AttemptAccessGuard` + test assert response không chứa `is_correct` |
| **IDOR trên `attempts`** | UC-035 · UC-036 · UC-038 · UC-039 · UC-040 | **Năm** UC cùng kiểm `attempts.user_id = currentUser`. Copy điều kiện 5 lần là chắc chắn quên 1 chỗ → dùng `OwnershipService` như đã nêu ở nhóm 1 |
| **JOIN thiếu `user_id`** | UC-033 · UC-038 | Lấy "điểm cao nhất của tôi" và "lần làm trước" mà thiếu điều kiện `user_id` → trả dữ liệu người khác. Cần test **hai user cùng làm một đề** |
| **Thiếu nhãn kiến thức** | UC-035 · UC-039 · UC-040 · UC-041 | Một nguyên nhân gốc (UC-110 nhập thiếu nhãn) làm mù **bốn** UC liên tiếp. Phải validate lúc nhập, không phát hiện lúc chạy |

---

# Khoảng trống thiết kế phát hiện ở nhóm 2

| # | Thiếu | UC bị ảnh hưởng | Mức |
| --- | --- | --- | --- |
| 1 | Nơi lưu nháp đã chốt là `attempts.draft_answers` và `draft_saved_at`; cần triển khai ghi mỗi 30 giây nếu đưa chức năng tùy chọn UC-036 vào phạm vi | UC-036 | ⚠️ Tùy chọn |
| 2 | Đã chốt chấm theo snapshot `attempts.served_items` tại `served_at`; cần bảo đảm snapshot được ghi một lần khi bắt đầu bài | UC-035 | Đã chốt quy tắc |
| 3 | Chưa có validate "mọi câu phải có ≥ 1 nhãn kiến thức" khi nhập đề | UC-040 · UC-041 | 🔴 Làm mù cả tính năng 2.3 |
| 4 | Chưa có DTO riêng cho câu hỏi lúc làm bài + test assert không lộ `is_correct` | UC-034 | 🔴 Lộ đáp án |
| 5 | Khoá ngoại `attempt_items → questions` chưa chốt `ON DELETE RESTRICT` | UC-039 | 🔴 Xoá câu hỏi là mất lịch sử làm bài |
| 6 | Chưa có `AttemptAccessGuard` dùng chung (sở hữu + trạng thái) | UC-035 → UC-040 | 🔴 5 chỗ kiểm rời rạc |
| 7 | Chưa có index `(user_id, mastery)` và `question_knowledge_points(knowledge_point_id)` | UC-040 · UC-041 | ⚠️ Quét toàn bảng |
| 8 | Chưa chốt giới hạn số lượt làm đề mỗi ngày | UC-034 | ⚠️ Farm mastery |
| 9 | Chưa chốt số lần phát audio tối đa phần Nghe | UC-034 | ⚠️ Bài nghe mất ý nghĩa |
| 10 | Chưa có trạng thái `ABANDONED` trong `attempts.status` (tài liệu chỉ nói `IN_PROGRESS`/`SUBMITTED`) | UC-035 · UC-036 | ⚠️ Cần thêm giá trị CHECK |
| 11 | UC-036 **chưa được chốt có làm hay không** | UC-036 | ⚠️ Catalog đã đánh dấu |

> **Sáu mục 🔴 ở trên đều là ràng buộc DB hoặc lớp bảo mật còn thiếu** — cùng loại với nhóm 1.
> Riêng mục 3 và 5 phải xử lý **trước** khi nhập dữ liệu đề thi thật, vì sau đó
> `question_knowledge_points` "không sửa được nếu không nhập lại".

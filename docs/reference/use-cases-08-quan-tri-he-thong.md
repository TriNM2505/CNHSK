# CNHSK — Đặc tả Use Case · Nhóm 6c + 6d + 7 · Quản trị nội dung, hệ thống & tham khảo

> **UC-108 → UC-119** · 12 use case · Tính năng 6.4 · 6.6 · 5.8 · 6.2 · 7.1 · 7.2
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
>
> Nhóm này chứa **UC-110 — nhập dữ liệu của thầy** — nơi quyết định chất lượng của mọi tính năng
> thông minh. Feature tree cảnh báo: *"**Chưa ai thấy file thật của thầy**"*.

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
|---|---|---|---|---|---|
| UC-108 | Duyệt câu hỏi AI sinh | `TEACHER` | P1 | MVP | 6.6 |
| UC-109 | Sửa nội dung câu hỏi | `TEACHER` `CONTENT_ADMIN` | P1 | MVP | 6.6 |
| UC-110 | Nhập dữ liệu đề thi từ file | `CONTENT_ADMIN` | **P0** | MVP | 6.4 |
| UC-111 | Xem báo cáo lỗi sau khi nhập | `CONTENT_ADMIN` | **P0** | MVP | 6.4 |
| UC-112 | Quản lý đề thi và kho câu hỏi | `CONTENT_ADMIN` | P1 | MVP | 6.6 |
| UC-113 | Tạo và quản lý cuộc thi | `CONTENT_ADMIN` | P2 | V2 | 5.8 |
| UC-114 | Quản lý người dùng (tìm, xem, khóa) | `SUPER_ADMIN` | P1 | MVP | 6.2 |
| UC-115 | Cấp và thu hồi role | `SUPER_ADMIN` | P1 | MVP | 6.2 |
| UC-116 | Cấu hình hệ thống | `SUPER_ADMIN` | P2 | MVP | 6.6 |
| UC-117 | Xem danh sách kênh YouTube và podcast | `GUEST` `USER` | P3 | V2 | 7.1 |
| UC-118 | Xem danh mục sách học tiếng Trung | `GUEST` `USER` | P3 | V2 | 7.2 |
| UC-119 | Quản lý danh mục tham khảo | `CONTENT_ADMIN` | P3 | V2 | 7.1 · 7.2 |

> **Nghiệm thu 6.6:** *"Mỗi role **chỉ thấy phần mình quản**; duyệt hàng loạt 20 mục dưới 2 phút"*.

---

# UC-108 · Duyệt câu hỏi AI sinh

| | |
|---|---|
| **UC-ID** | UC-108 · **Actor** `TEACHER` · **Pri** P1 · **Scope** MVP · **FT** 6.6 |

## Mô tả

`TEACHER` xem hàng đợi câu hỏi AI sinh (`PENDING_REVIEW` từ UC-049), duyệt hoặc từ chối.
**Đây là lớp bảo vệ duy nhất** chống nội dung AI sai — không có cách tự động nào phát hiện
`AI_HALLUCINATED_CONTENT`.

## Tiền điều kiện

1. Role `TEACHER`
2. Có câu `status = PENDING_REVIEW`, `source = AI`
3. `TEACHER` đọc được tiếng Trung đủ để đánh giá

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Duyệt | `status = APPROVED`, `reviewed_by`, `reviewed_at`; câu vào kho chung (UC-050) |
| Từ chối | `status = REJECTED` kèm lý do; **không** đến người học |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `TEACHER` | Mở hàng đợi duyệt câu hỏi |
| 2 | System | Kiểm role `TEACHER` |
| 3 | System | `GET /api/teacher/questions?status=PENDING_REVIEW` — sắp cũ nhất trước |
| 4 | System | Trả câu + đáp án + lời giải + nhãn kiến thức + `job_id` nguồn |
| 5 | `TEACHER` | Đọc, kiểm ngữ pháp và tính đúng đắn |
| 6 | `TEACHER` | Duyệt hoặc từ chối |
| 7 | Client | `PATCH /api/teacher/questions/{id}/review` — `{decision, reason?}` |
| 8 | System | `UPDATE ... WHERE id=? AND status='PENDING_REVIEW'` |
| 9 | System | Ghi `review_actions` — ai, quyết định gì, khi nào |
| 10 | Client | Bỏ câu khỏi hàng đợi |

## Luồng thay thế

**A1 — Duyệt hàng loạt**
Nghiệm thu 6.6: "duyệt hàng loạt **20 mục dưới 2 phút**". Nhận mảng id; **mỗi câu một
transaction** để một lỗi không chặn cả lô.

**A2 — Sửa rồi duyệt**
Câu gần đúng, chỉ sai một từ. Gọi UC-109 sửa trước, rồi duyệt. Ghi cả hai hành động.

**A3 — Không chắc**
Để lại hàng đợi, không quyết. Hoặc đánh dấu "cần ý kiến khác".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `TEACHER` | 🔴 **Kiểm ở server** |
| `QUESTION_NOT_PENDING` | 409 | Đã duyệt/từ chối | `WHERE status='PENDING_REVIEW'` |
| `CONCURRENT_REVIEW` | 409 | Hai `TEACHER` cùng duyệt | Cùng mẫu UC-076 · UC-105 · UC-107 |
| `EMPTY_REJECTION_REASON` | 400 | Từ chối không lý do | Bắt buộc — để cải thiện prompt AI |
| `APPROVED_WITHOUT_REVIEW` | — | 🔴 Duyệt mà không đọc | Xem ghi chú |
| `MALFORMED_QUESTION_APPROVED` | — | 🔴 Duyệt câu 2 đáp án đúng | Xem ghi chú |
| `BULK_APPROVE_ALL_BLINDLY` | — | 🔴 Chọn hết rồi duyệt | Xem ghi chú |
| `NO_REVIEW_AUDIT` | — | Không ghi `review_actions` | Bắt buộc — truy vết trách nhiệm |
| `REJECTED_QUESTION_SERVED` | — | 🔴 Câu `REJECTED` vẫn đến người học | Lọc `APPROVED` ở repository |
| `CONTENT_ADMIN_ATTEMPTED` | 403 | ⚠️ `CONTENT_ADMIN` duyệt câu AI | Xem ghi chú |

> 🔴 **`APPROVED_WITHOUT_REVIEW` + `BULK_APPROVE_ALL_BLINDLY` — rủi ro lớn nhất của UC này, và
> **không phải lỗi kỹ thuật**.** Nghiệm thu khuyến khích duyệt nhanh ("20 mục dưới 2 phút" = 6
> giây/câu). Nhưng UC-049 đã xác định: `AI_HALLUCINATED_CONTENT` **chỉ** người đọc phát hiện được.
> Sáu giây không đủ đọc một câu tiếng Trung và kiểm ngữ pháp.
>
> **Hai yêu cầu xung đột:** duyệt nhanh (nghiệm thu) vs duyệt kỹ (chất lượng).
> **Đề xuất giải quyết:**
> — "20 mục dưới 2 phút" nên hiểu là **thao tác UI không chậm**, không phải "đọc xong trong 6 giây"
> — Duyệt hàng loạt chỉ cho phép sau khi đã mở xem từng câu (client theo dõi), hoặc
> — Bỏ nút "chọn tất cả", chỉ cho chọn từng câu đã xem
>
> Đây là thiết kế chống lỗi con người, và là điểm **nên nói khi bảo vệ** — cho thấy nhóm hiểu
> giới hạn của kiểm duyệt.

> 🔴 **`MALFORMED_QUESTION_APPROVED`:** UC-049 đã validate (đúng 4 đáp án, đúng 1 `is_correct`).
> Nhưng nếu validate đó lỏng, câu lỗi vào hàng đợi và `TEACHER` duyệt → `MALFORMED_QUESTION`
> (UC-019) làm người học bị chấm sai oan.
> **Cần:** partial unique index đảm bảo đúng 1 `is_correct` mỗi câu — chặn ở DB, không dựa vào
> người duyệt để ý.

> ⚠️ **`CONTENT_ADMIN_ATTEMPTED`:** quyết định v2 cho `CONTENT_ADMIN` quyền "quản lý đề thi và
> câu hỏi" (6.6) nhưng **duyệt câu AI** thuộc `TEACHER`.
> Lý do hợp lý: duyệt câu AI cần **kiến thức tiếng Trung**, không phải quyền quản trị. Nhưng đây
> là ranh giới mờ — **cần chốt** `CONTENT_ADMIN` có duyệt được câu AI không.
> **Khuyến nghị:** không — giữ đúng phân công quyết định v2, và đó chính là separation of duties.

## Business rule

| # | Rule |
|---|---|
| BR-108-1 | Chỉ `TEACHER` (kiểm ở **server**) |
| BR-108-2 | `UPDATE ... WHERE status='PENDING_REVIEW'` |
| BR-108-3 | Lý do từ chối **bắt buộc** |
| BR-108-4 | Bắt buộc ghi `review_actions` |
| BR-108-5 | Duyệt lô: mỗi câu một transaction |
| BR-108-6 | Chỉ cho duyệt lô những câu đã mở xem |
| BR-108-7 | Câu `REJECTED`/`PENDING_REVIEW` **không bao giờ** đến người học |
| BR-108-8 | Partial unique index: đúng 1 `is_correct` mỗi câu |

## API · DB

```
GET   /api/teacher/questions?status=PENDING_REVIEW
PATCH /api/teacher/questions/{id}/review
PATCH /api/teacher/questions/bulk-review
```
`questions` · `question_options` · `question_knowledge_points` (đọc + ghi) · `review_actions` (ghi) · `ai_generation_jobs` (đọc)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | `TEACHER` duyệt câu | `APPROVED`, vào kho chung |
| T2 | `USER` gọi | 403 |
| T3 | `CONTENT_ADMIN` gọi | 403 (theo quyết định) |
| T4 | Từ chối không lý do | 400 |
| T5 | Hai `TEACHER` cùng duyệt | Một 200, một 409 |
| T6 | Duyệt lô 20 câu, câu thứ 5 lỗi | 19 câu thành công |
| T7 | Câu `REJECTED` | **Không** xuất hiện ở UC-037 |
| T8 | Sau khi duyệt | `review_actions` có dòng |

---

# UC-109 · Sửa nội dung câu hỏi

| | |
|---|---|
| **UC-ID** | UC-109 · **Actor** `TEACHER` `CONTENT_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.6 |

## Mô tả

Sửa đề bài, đáp án, lời giải, hoặc nhãn kiến thức của câu hỏi. Dùng khi phát hiện câu sai
(từ `question_reports` hoặc khi duyệt).

## Tiền điều kiện

1. Role `TEACHER` **hoặc** `CONTENT_ADMIN`
2. Câu hỏi tồn tại

## Hậu điều kiện

`questions`/`question_options` cập nhật; `review_actions` ghi thay đổi.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | Actor | Mở câu hỏi cần sửa |
| 2 | Actor | Sửa nội dung |
| 3 | Client | `PUT /api/admin/questions/{id}` |
| 4 | System | Kiểm role |
| 5 | System | Validate: đúng 4 đáp án, đúng 1 `is_correct`, có lời giải |
| 6 | System | **Kiểm có `attempt_answers` tham chiếu** (xem exception) |
| 7 | System | Cập nhật; ghi `review_actions` kèm nội dung cũ |
| 8 | System | Xoá cache liên quan |

## Luồng thay thế

**A1 — Câu đã có người làm**
🔴 Xem exception `QUESTION_CHANGED_AFTER_ATTEMPTS`.

**A2 — Sửa nhãn kiến thức**
Ảnh hưởng UC-040, UC-041, UC-047. Ghi log rõ vì `question_knowledge_points` là bảng "không được
đụng".

**A3 — Xoá câu hỏi**
Không xoá — đặt `status = ARCHIVED`. Xem exception.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `TEACHER`/`CONTENT_ADMIN` | Chặn |
| `QUESTION_CHANGED_AFTER_ATTEMPTS` | — | 🔴 Sửa đáp án của câu đã có người làm | Xem ghi chú |
| `MALFORMED_AFTER_EDIT` | 400 | Sửa thành 2 đáp án đúng | Validate + DB constraint |
| `QUESTION_DELETED_WITH_ATTEMPTS` | 409 | 🔴 Xoá câu có `attempt_answers` | `ON DELETE RESTRICT` — xem UC-039 |
| `KNOWLEDGE_POINT_REMOVED` | 400 | 🔴 Xoá hết nhãn kiến thức | Xem ghi chú |
| `NO_EDIT_AUDIT` | — | Không ghi nội dung cũ | Bắt buộc — cần để đối chiếu |
| `CACHE_NOT_INVALIDATED` | — | Cache còn nội dung cũ | Xoá cache sau khi sửa |
| `EDIT_DURING_ACTIVE_ATTEMPT` | — | 🔴 Sửa khi có người đang làm bài | Xem ghi chú |
| `CONTEST_QUESTION_EDITED` | — | 🔴 Sửa câu đang dùng trong cuộc thi | Xem ghi chú |

> 🔴 **`QUESTION_CHANGED_AFTER_ATTEMPTS` + `EDIT_DURING_ACTIVE_ATTEMPT` — đây là mặt khác của
> `QUESTION_CHANGED_MID_ATTEMPT` (UC-035).** Ở UC-035 tôi đã nêu: chưa có snapshot đề, nên chấm
> theo đáp án hiện tại.
> Từ phía admin, hệ quả cụ thể:
> — `CONTENT_ADMIN` sửa đáp án lúc 10:00. Người học bắt đầu 9:50, nộp 10:05 → chấm sai
> — `attempt_answers` cũ ghi `is_correct = true` theo đáp án **cũ**; giờ tra lại UC-039 thì đáp
> án đúng đã khác → người học thấy "bạn chọn A, đáp án đúng là B" dù lúc làm A **là** đúng
>
> **Cần chốt một trong ba:**
> — Cảnh báo khi sửa câu có `attempt_answers` (rẻ nhất, không chặn)
> — Tạo **phiên bản mới** của câu, giữ bản cũ cho `attempt_answers` cũ (đúng nhất, tốn công)
> — Chặn sửa đáp án, chỉ cho sửa lời giải và chính tả (thoả hiệp hợp lý)
> **Khuyến nghị cho 11 tuần:** phương án 3 + cảnh báo. Ghi rõ giới hạn.

> 🔴 **`KNOWLEDGE_POINT_REMOVED`:** xoá hết nhãn của một câu thì UC-040/041/047 mù chỗ đó
> (`NO_KNOWLEDGE_POINTS`). `question_knowledge_points` nằm trong "5 bảng tuyệt đối không đụng"
> với lý do "**không sửa được nếu không nhập lại dữ liệu**".
> **Cần:** `CHECK` hoặc validate: mỗi câu **luôn** có ≥ 1 nhãn.

> 🔴 **`CONTEST_QUESTION_EDITED`:** sửa câu đang dùng trong cuộc thi **đang diễn ra** (UC-092) là
> thay đổi luật giữa cuộc — với phần thưởng thật thì đây là tranh chấp.
> **Cần:** chặn sửa câu thuộc cuộc thi đang trong khung giờ.

## Business rule

| # | Rule |
|---|---|
| BR-109-1 | `TEACHER` hoặc `CONTENT_ADMIN` |
| BR-109-2 | Mỗi câu **luôn** có ≥ 1 nhãn kiến thức |
| BR-109-3 | Đúng 4 đáp án, đúng 1 `is_correct` — validate + DB constraint |
| BR-109-4 | **Không xoá** câu có `attempt_answers` — dùng `ARCHIVED` |
| BR-109-5 | Ghi `review_actions` kèm **nội dung cũ** |
| BR-109-6 | Cảnh báo khi sửa câu đã có người làm |
| BR-109-7 | Chặn sửa câu thuộc cuộc thi đang diễn ra |
| BR-109-8 | Xoá cache sau khi sửa |
| BR-109-9 | ⚠️ Chốt: có chặn sửa đáp án của câu đã dùng không |

## API · DB

```
PUT   /api/admin/questions/{id}
PATCH /api/admin/questions/{id}/archive
```
`questions` · `question_options` · `question_knowledge_points` (ghi) · `attempt_answers` · `contests` (đọc để kiểm) · `review_actions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Sửa lời giải | 200, `review_actions` có nội dung cũ |
| T2 | `USER` gọi | 403 |
| T3 | Sửa thành 2 đáp án đúng | 400 + DB chặn |
| T4 | Xoá hết nhãn kiến thức | 400 |
| T5 | Xoá câu có `attempt_answers` | 409 |
| T6 | Sửa câu đã có 50 người làm | Cảnh báo rõ |
| T7 | Sửa câu trong cuộc thi đang diễn ra | Chặn |

---

# UC-110 · Nhập dữ liệu đề thi từ file

| | |
|---|---|
| **UC-ID** | UC-110 · **Actor** `CONTENT_ADMIN` · **Pri** **P0** · **Scope** MVP · **FT** 6.4 |

## Mô tả

Nhập đề thi, từ vựng, ngữ pháp của thầy vào hệ thống. **P0 — không có thì hệ thống không chạy.**

Yêu cầu: nhập **có kiểm tra định dạng** · **báo cáo dòng lỗi** · **chạy lại không tạo bản trùng**.

> 🔴 **Rủi ro đã biết:** *"**Chưa ai thấy file thật của thầy**"* — 8 câu cần hỏi ở tài liệu
> database mục 11.

## Tiền điều kiện

1. Role `CONTENT_ADMIN`; CSRF
2. File đúng định dạng đã thống nhất
3. `knowledge_points` đã có để gắn nhãn

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Thành công | `exams`/`questions`/`words`/`grammar_points` thêm dòng; `import_runs` ghi kết quả |
| Có dòng lỗi | Dòng đúng được nhập, dòng lỗi báo lại; **không ghi dữ liệu hỏng** |
| Chạy lại | **Không** tạo bản trùng (idempotent) |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `CONTENT_ADMIN` | Chọn file, chọn loại dữ liệu |
| 2 | Client | `POST /api/admin/imports` (multipart) + CSRF |
| 3 | System | Kiểm role, kiểm kích thước và loại file |
| 4 | System | Tạo `import_runs` `status = RUNNING`, ghi `file_hash` |
| 5 | System | **Kiểm `file_hash` đã nhập chưa** — idempotent |
| 6 | System | Parse từng dòng |
| 7 | System | **Validate từng dòng**: định dạng · bắt buộc có nhãn kiến thức · đúng 1 đáp án đúng |
| 8 | System | Dòng lỗi → ghi vào danh sách lỗi, **không** ghi DB |
| 9 | System | Dòng đúng → `INSERT` (upsert theo khoá tự nhiên) |
| 10 | System | `import_runs` `status = COMPLETED`, ghi số dòng đúng/lỗi |
| 11 | System | Trả `import_run_id` |
| 12 | `CONTENT_ADMIN` | Xem báo cáo lỗi (UC-111) |

## Luồng thay thế

**A1 — Chạy lại cùng file**
Bước 5 nhận ra `file_hash` đã nhập → hỏi "đã nhập rồi, chạy lại?". Nếu chạy, upsert theo khoá
tự nhiên → **không** tạo bản trùng.

**A2 — File có 1.000 dòng, 50 lỗi**
950 dòng vào DB, 50 dòng báo lại. **Không** rollback cả file — nếu không thì một dòng lỗi chặn
toàn bộ.

**A3 — File sai định dạng hoàn toàn**
Bước 6 parse lỗi → `import_runs` `FAILED`, **không** ghi dòng nào.

**A4 — Chạy nền cho file lớn**
File > N dòng → trả `import_run_id` ngay, chạy nền, cập nhật tiến độ.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `CONTENT_ADMIN` | 🔴 Chặn — `FINANCE_ADMIN` không nhập đề |
| `UNKNOWN_FILE_FORMAT` | 400 | 🔴 Định dạng file của thầy chưa biết | Xem ghi chú |
| `MISSING_KNOWLEDGE_POINT` | — | 🔴 Dòng không có nhãn kiến thức | Xem ghi chú |
| `PARTIAL_IMPORT_ROLLED_BACK` | — | 🔴 Một dòng lỗi rollback cả file | Vi phạm nghiệm thu (A2) |
| `DUPLICATE_ON_RERUN` | — | 🔴 Chạy lại tạo bản trùng | Vi phạm nghiệm thu — cần khoá tự nhiên |
| `NO_NATURAL_KEY` | 500 | 🔴 Không có khoá để upsert | Xem ghi chú |
| `CORRUPT_DATA_WRITTEN` | — | 🔴 Ghi dữ liệu hỏng | Vi phạm nghiệm thu: "**không ghi dữ liệu hỏng**" |
| `MULTIPLE_CORRECT_ANSWERS` | — | Dòng có 2 đáp án đúng | Từ chối dòng; DB constraint chặn |
| `ENCODING_ERROR` | 400 | 🔴 File không UTF-8, chữ Hán thành `???` | Xem ghi chú |
| `COPYRIGHT_DATA_COMMITTED` | — | 🔴 Dữ liệu đề thi vào git | Xem ghi chú |
| `FILE_TOO_LARGE` | 413 | Quá giới hạn | Chia file |
| `IMPORT_TIMEOUT` | — | File lớn quá lâu | Chạy nền (A4) |
| `CONCURRENT_IMPORT` | 409 | Hai lần nhập cùng lúc | Một `import_runs RUNNING` mỗi lúc |
| `NO_IMPORT_AUDIT` | — | Không ghi ai nhập | `import_runs` bắt buộc có `created_by` |

> 🔴 **`UNKNOWN_FILE_FORMAT` là rủi ro số một của UC này, và là rủi ro **tiến độ**, không phải
> kỹ thuật.** Feature tree ghi rõ "chưa ai thấy file thật của thầy" với 8 câu cần hỏi.
> Hệ quả: viết parser trước khi biết định dạng là **viết lại từ đầu** khi file thật đến.
> Đây là UC **P0** — không có nó thì không có dữ liệu, không có dữ liệu thì 5 nhóm UC trước
> không demo được.
> **Khuyến nghị:** lấy file thật (dù chỉ 1 file mẫu) **trước** khi viết parser. Đây là việc phải
> làm ngay, không phải việc kỹ thuật.

> 🔴 **`MISSING_KNOWLEDGE_POINT` — validate quan trọng nhất của UC này.** UC-040 đã nêu:
> *"nhập 17 bộ đề HSK1 mà quên gắn nhãn → UC-040 trả rỗng, UC-041 không có gì để luyện, UC-047
> và UC-048 cũng mù. Tính năng 'bán được nhất' biến thành màn hình trống."*
> Và `question_knowledge_points` "**không sửa được nếu không nhập lại dữ liệu**".
> **Bắt buộc:** dòng không có nhãn kiến thức → **từ chối dòng đó**, báo ở UC-111. Không nhập rồi
> gắn nhãn sau — sẽ không ai làm.

> 🔴 **`NO_NATURAL_KEY` chặn yêu cầu "chạy lại không tạo bản trùng".** Upsert cần một khoá tự
> nhiên:
> — Câu hỏi: `(exam_id, section, question_number)`? hay hash nội dung?
> — Từ vựng: `(word, pinyin)`?
> — Ngữ pháp: `(hsk_level, grammar_code)`?
> **Chưa chốt** khoá nào. Không có khoá thì chạy lại là nhân đôi dữ liệu.
> **Cần chốt cùng lúc với định dạng file.**

> 🔴 **`ENCODING_ERROR` — lỗi cụ thể của dữ liệu tiếng Trung.** File Excel/CSV từ Windows tiếng
> Việt thường là `windows-1258` hoặc `windows-936`. Đọc bằng UTF-8 là chữ Hán thành `???` — và
> **ghi vào DB thành công** (không lỗi), chỉ là nội dung rác.
> Đây là loại lỗi tệ nhất: **không báo lỗi** mà dữ liệu sai. Phải phát hiện ở bước validate:
> kiểm dòng có ký tự Hán hợp lệ.

> 🔴 **`COPYRIGHT_DATA_COMMITTED`:** constitution cấm "commit dữ liệu đề thi HSK của giảng viên
> (bản quyền)". Nhưng khi test parser, rất tự nhiên để đặt file mẫu vào `src/test/resources/`.
> **Cần:** file test dùng dữ liệu **tự tạo**, không phải đề thật. Và `.gitignore` chặn thư mục
> chứa file thầy gửi.

## Business rule

| # | Rule |
|---|---|
| BR-110-1 | Chỉ `CONTENT_ADMIN` |
| BR-110-2 | Dòng **không có nhãn kiến thức** → từ chối dòng |
| BR-110-3 | Dòng lỗi **không** rollback dòng đúng |
| BR-110-4 | **Không ghi dữ liệu hỏng** |
| BR-110-5 | Chạy lại **không** tạo bản trùng — upsert theo khoá tự nhiên |
| BR-110-6 | Ghi `file_hash` để nhận ra file đã nhập |
| BR-110-7 | Buộc đọc file bằng encoding đúng; validate có ký tự Hán hợp lệ |
| BR-110-8 | **Không** commit dữ liệu thật của thầy vào git |
| BR-110-9 | `import_runs` bắt buộc `created_by` |
| BR-110-10 | Một lần nhập `RUNNING` mỗi lúc |
| BR-110-11 | ⚠️ **Chặn:** cần định dạng file thật + khoá tự nhiên trước khi viết parser |

## API · DB

```
POST /api/admin/imports
GET  /api/admin/imports/{id}
```
`import_runs` (ghi) · `exams` · `exam_sections` · `questions` · `question_options` · `question_knowledge_points` · `words` · `grammar_points` (ghi) · `knowledge_points` (đọc)

> ⚠️ **Lệch tài liệu:** feature tree 6.4 ghi bảng `import_batches`, DB v5 có **`import_runs`**
> (đổi tên từ `sync_runs`). Cần thống nhất tên.

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | File 100 dòng hợp lệ | 100 dòng vào DB, `import_runs COMPLETED` |
| T2 | File 100 dòng, 10 lỗi | **90 vào DB**, 10 báo lại |
| T3 | Chạy lại cùng file | **Không** tạo bản trùng |
| T4 | Dòng thiếu nhãn kiến thức | Dòng bị từ chối, báo rõ |
| T5 | File encoding `windows-936` | Phát hiện, báo lỗi — **không** ghi `???` |
| T6 | `FINANCE_ADMIN` gọi | 403 |
| T7 | File sai định dạng hoàn toàn | `FAILED`, **0 dòng** vào DB |
| T8 | Hai lần nhập song song | Một 409 |
| T9 | `git grep` trong repo | **Không** có dữ liệu đề thật |

---

# UC-111 · Xem báo cáo lỗi sau khi nhập

| | |
|---|---|
| **UC-ID** | UC-111 · **Actor** `CONTENT_ADMIN` · **Pri** **P0** · **Scope** MVP · **FT** 6.4 |

## Mô tả

Xem chi tiết dòng nào lỗi và lỗi gì. Nghiệm thu 6.4: *"File lỗi báo rõ **dòng nào sai**, không
ghi dữ liệu hỏng"*.

## Tiền điều kiện

Role `CONTENT_ADMIN`; `import_runs` tồn tại.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `CONTENT_ADMIN` | Mở kết quả lần nhập |
| 2 | Client | `GET /api/admin/imports/{id}` |
| 3 | System | Kiểm role |
| 4 | System | Trả tổng hợp: tổng dòng · thành công · lỗi · thời gian |
| 5 | System | Trả danh sách lỗi: **số dòng** · cột · giá trị · mã lỗi · thông báo |
| 6 | `CONTENT_ADMIN` | Sửa file, nhập lại (UC-110) |

## Luồng thay thế

**A1 — Xuất danh sách lỗi ra file** — để sửa trong Excel.
**A2 — Xem lịch sử các lần nhập** — `GET /api/admin/imports`.
**A3 — Không có lỗi** — hiện "nhập thành công 100%".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `CONTENT_ADMIN` | Chặn |
| `IMPORT_RUN_NOT_FOUND` | 404 | ID sai | Chặn |
| `NO_LINE_NUMBERS` | — | 🔴 Lỗi không có số dòng | Xem ghi chú |
| `ERROR_MESSAGE_UNCLEAR` | — | 🔴 "Validation failed" không nói gì | Xem ghi chú |
| `ERRORS_NOT_PERSISTED` | — | 🔴 Lỗi chỉ trong response, không lưu | Xem ghi chú |
| `TOO_MANY_ERRORS_TRUNCATED` | 200 | 5.000 dòng lỗi | Phân trang; hiện tổng số |
| `COPYRIGHT_DATA_IN_ERROR_LOG` | — | 🔴 Nội dung đề trong bảng lỗi | ⚠️ Xem ghi chú |
| `STACK_TRACE_EXPOSED` | — | Trả stack trace | Vi phạm "không lộ stack trace" — dùng `{error_code, message}` |

> 🔴 **`NO_LINE_NUMBERS` + `ERROR_MESSAGE_UNCLEAR` — vi phạm nghiệm thu trực tiếp.** Nghiệm thu
> nói "báo rõ **dòng nào sai**". Thông báo "có 50 dòng lỗi" mà không nói dòng nào thì
> `CONTENT_ADMIN` phải dò tay 1.000 dòng.
> **Cần:** mỗi lỗi có `line_number`, `column_name`, `actual_value`, `error_code`, và thông báo
> tiếng Việt cụ thể — ví dụ *"Dòng 47, cột 'knowledge_point': không tìm thấy điểm kiến thức
> 'GP-HSK3-205'"*.

> 🔴 **`ERRORS_NOT_PERSISTED`:** nếu danh sách lỗi chỉ nằm trong response của UC-110 thì đóng
> tab là mất. Với file 1.000 dòng và 50 lỗi thì đó là mất 50 lần dò.
> **Cần:** bảng lưu chi tiết lỗi theo `import_run_id`. Hiện `import_runs` chỉ ghi tổng hợp —
> **thiếu bảng chi tiết lỗi**.

> ⚠️ **`COPYRIGHT_DATA_IN_ERROR_LOG`:** bảng lỗi hiện `actual_value` để `CONTENT_ADMIN` biết sai
> gì — nghĩa là **nội dung đề thi** nằm trong DB bảng lỗi.
> Đây không phải vi phạm (dữ liệu vẫn trong hệ thống của nhóm, không lên git), nhưng cần: không
> xuất bảng lỗi ra ngoài, và dọn sau khi sửa xong.

## Business rule

| # | Rule |
|---|---|
| BR-111-1 | Chỉ `CONTENT_ADMIN` |
| BR-111-2 | Mỗi lỗi có **số dòng** + cột + giá trị + mã lỗi |
| BR-111-3 | Thông báo lỗi tiếng Việt, cụ thể |
| BR-111-4 | Lỗi **lưu vào DB**, không chỉ trong response |
| BR-111-5 | Phân trang khi nhiều lỗi |
| BR-111-6 | **Không** trả stack trace |
| BR-111-7 | Không xuất bảng lỗi ra ngoài hệ thống |

## API · DB

```
GET /api/admin/imports
GET /api/admin/imports/{id}
GET /api/admin/imports/{id}/errors
GET /api/admin/imports/{id}/errors/export
```
`import_runs` (đọc) · ⚠️ **bảng chi tiết lỗi — chưa có**

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Lần nhập có 10 lỗi | 10 dòng, mỗi dòng có `line_number` |
| T2 | Đóng tab, mở lại | Danh sách lỗi **vẫn còn** |
| T3 | `FINANCE_ADMIN` gọi | 403 |
| T4 | Lỗi thiếu nhãn kiến thức | Thông báo nói rõ mã nhãn không tìm thấy |
| T5 | 5.000 lỗi | Phân trang, hiện tổng |
| T6 | Kiểm response | **Không** có stack trace |

---

# UC-112 · Quản lý đề thi và kho câu hỏi

| | |
|---|---|
| **UC-ID** | UC-112 · **Actor** `CONTENT_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.6 |

## Mô tả

Tạo/sửa đề thi, gán câu hỏi vào đề, công bố đề (`DRAFT` → `PUBLISHED`), quản lý quan hệ tiên
quyết chủ đề.

## Tiền điều kiện

Role `CONTENT_ADMIN`; CSRF.

## Hậu điều kiện

`exams`/`exam_sections`/`topics` cập nhật.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `CONTENT_ADMIN` | Mở quản lý đề thi |
| 2 | System | Kiểm role |
| 3 | `CONTENT_ADMIN` | Tạo đề, thêm phần, gán câu hỏi |
| 4 | Client | `POST`/`PUT /api/admin/exams/{id}` + CSRF |
| 5 | System | Validate: có ≥ 1 phần, mỗi phần có ≥ 1 câu |
| 6 | System | Ghi `exams`/`exam_sections` `status = DRAFT` |
| 7 | `CONTENT_ADMIN` | Bấm "Công bố" |
| 8 | System | Validate đầy đủ trước khi `PUBLISHED` |
| 9 | System | `status = PUBLISHED`; đề hiện ở UC-033 |

## Luồng thay thế

**A1 — Sửa quan hệ tiên quyết chủ đề**
🔴 **Phải kiểm chu trình** — xem exception.

**A2 — Rút đề đã công bố**
`status = ARCHIVED`. Đề ẩn khỏi UC-033, nhưng `attempts` cũ **giữ nguyên**.

**A3 — Sao chép đề làm bản mới**
Copy cấu trúc, `status = DRAFT`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `CONTENT_ADMIN` | Chặn |
| `PUBLISH_EMPTY_EXAM` | 400 | 🔴 Công bố đề 0 câu | Chặn — UC-033 `EXAM_HAS_NO_QUESTIONS` |
| `PUBLISH_WITHOUT_AUDIO` | 400 | Phần Nghe thiếu audio | Chặn — UC-034 `AUDIO_NOT_AVAILABLE` |
| `PUBLISH_UNLABELED_QUESTIONS` | 400 | 🔴 Câu chưa gắn nhãn kiến thức | Xem ghi chú |
| `CIRCULAR_PREREQUISITE` | 400 | 🔴 Chu trình tiên quyết chủ đề | Xem ghi chú |
| `NO_ROOT_TOPIC` | 400 | 🔴 Mọi chủ đề có tiên quyết | Chặn — UC-045 cây toàn ổ khoá |
| `ARCHIVE_WITH_ACTIVE_ATTEMPTS` | 409 | Rút đề khi có người đang làm | ⚠️ Cảnh báo; `attempts` `IN_PROGRESS` vẫn nộp được |
| `UNPUBLISHED_EXAM_LEAKED` | — | 🔴 Đề `DRAFT` lộ ra | UC-034 `EXAM_NOT_PUBLISHED` |
| `SECTION_ORDER_MISSING` | 400 | Thiếu `order_index` | Chặn — UC-034 |
| `QUESTION_IN_MULTIPLE_EXAMS` | — | Câu dùng ở 2 đề | ⚠️ Cho phép (kho chung), nhưng cảnh báo |
| `NO_AUDIT_LOG` | — | Không ghi ai sửa | Bắt buộc |

> 🔴 **`PUBLISH_UNLABELED_QUESTIONS` là chốt cuối chặn `NO_KNOWLEDGE_POINTS_IN_EXAM` (UC-040).**
> UC-110 đã từ chối dòng thiếu nhãn khi nhập. Nhưng câu có thể được tạo tay ở UC-109, hoặc nhãn
> bị xoá sau đó.
> **Cần:** validate **lúc công bố** — mọi câu trong đề phải có ≥ 1 nhãn. Đây là kiểm cuối cùng
> trước khi đề đến người học; sau đó thì "không sửa được nếu không nhập lại".

> 🔴 **`CIRCULAR_PREREQUISITE` + `NO_ROOT_TOPIC` — đây là nơi UC-045 nói phải chặn.**
> UC-045 nêu: chu trình làm duyệt đồ thị **lặp vô hạn**, và không có chủ đề gốc thì người học
> thấy cây toàn ổ khoá.
> **Cần:** kiểm chu trình bằng DFS **trước khi commit** quan hệ tiên quyết, và đảm bảo còn ≥ 1
> chủ đề không có tiên quyết. Kiểm ở đây rẻ; phát hiện lúc người học mở trang là muộn.

## Business rule

| # | Rule |
|---|---|
| BR-112-1 | Chỉ `CONTENT_ADMIN` |
| BR-112-2 | Công bố cần: ≥ 1 phần · mỗi phần ≥ 1 câu · **mọi câu có nhãn** · phần Nghe có audio |
| BR-112-3 | Kiểm **chu trình** tiên quyết trước khi lưu |
| BR-112-4 | Luôn còn ≥ 1 chủ đề gốc |
| BR-112-5 | Rút đề = `ARCHIVED`, **không** xoá |
| BR-112-6 | Đề `DRAFT` **không** đến người học |
| BR-112-7 | `attempts` cũ giữ nguyên khi rút đề |
| BR-112-8 | Audit log mọi thay đổi |

## API · DB

```
GET   /api/admin/exams
POST  /api/admin/exams
PUT   /api/admin/exams/{id}
PATCH /api/admin/exams/{id}/publish
PATCH /api/admin/exams/{id}/archive
PUT   /api/admin/topics/{id}/prerequisites
```
`exams` · `exam_sections` · `questions` · `question_knowledge_points` · `topics` (đọc + ghi) · audit log

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Tạo đề, gán câu, công bố | `PUBLISHED`, hiện ở UC-033 |
| T2 | Công bố đề 0 câu | 400 |
| T3 | Công bố đề có câu thiếu nhãn | **400** |
| T4 | Đặt A cần B, B cần A | **400** `CIRCULAR_PREREQUISITE` |
| T5 | Xoá tiên quyết cuối của mọi chủ đề gốc | 400 `NO_ROOT_TOPIC` |
| T6 | `FINANCE_ADMIN` gọi | 403 |
| T7 | Rút đề có `attempts` | Cảnh báo; `attempts` giữ |
| T8 | Đề `DRAFT` | Không xuất hiện ở UC-033 |

---

# UC-113 · Tạo và quản lý cuộc thi

| | |
|---|---|
| **UC-ID** | UC-113 · **Actor** `CONTENT_ADMIN` · **Pri** P2 · **Scope** V2 · **FT** 5.8 |

## Mô tả

Tạo cuộc thi: đặt khung giờ, chọn đề, khai phần thưởng (`contests.prizes` JSONB), công bố.
Sau khi đóng: công bố xếp hạng.

## Tiền điều kiện

Role `CONTENT_ADMIN`; CSRF.

## Hậu điều kiện

`contests` thêm/cập nhật dòng.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `CONTENT_ADMIN` | Tạo cuộc thi |
| 2 | `CONTENT_ADMIN` | Đặt tên, khung giờ (**giờ Việt Nam**), đề, phần thưởng |
| 3 | Client | `POST /api/admin/contests` + CSRF |
| 4 | System | Kiểm role |
| 5 | System | Validate `starts_at < ends_at`, `starts_at > now()` |
| 6 | System | Validate `prizes` đúng cấu trúc |
| 7 | System | Ghi `contests` `status = DRAFT` |
| 8 | `CONTENT_ADMIN` | Công bố → UC-090 hiện |
| 9 | Sau khi đóng | `CONTENT_ADMIN` công bố xếp hạng |

## Luồng thay thế

**A1 — Sửa khung giờ trước khi bắt đầu** — cho phép; thông báo người đã đăng ký.
**A2 — Sửa khung giờ khi đang diễn ra** — 🔴 chặn. Xem exception.
**A3 — Huỷ cuộc thi** — `status = CANCELLED`, thông báo người đăng ký.
**A4 — Xem nhật ký gian lận** — đọc `contest_participants.cheat_events`, quyết định loại ai.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `CONTENT_ADMIN` | Chặn |
| `INVALID_TIME_RANGE` | 400 | `starts_at ≥ ends_at` | Chặn |
| `START_IN_PAST` | 400 | Bắt đầu trong quá khứ | Chặn |
| `EDIT_DURING_CONTEST` | 403 | 🔴 Sửa khung giờ/đề khi đang diễn ra | Xem ghi chú |
| `TIMEZONE_MISINTERPRETED` | — | 🔴 "20:00" hiểu là UTC | Xem ghi chú |
| `PRIZES_MALFORMED` | 400 | JSONB sai cấu trúc | Validate schema |
| `PRIZES_JSONB_UPDATED_LIVE` | — | 🔴 Sửa `prizes` khi đang diễn ra | Chặn — người thi đã thấy thưởng cũ |
| `RANKING_PUBLISHED_EARLY` | 403 | Công bố trước khi đóng | Chặn — UC-092 |
| `PRIZE_AS_CREDIT_WITHOUT_LEDGER` | — | 🔴 Trao thưởng bằng điểm không ghi sổ cái | Xem ghi chú |
| `FINANCE_ROLE_NEEDED_FOR_PRIZE` | — | ⚠️ `CONTENT_ADMIN` trao thưởng tiền | Xem ghi chú |
| `CONTEST_WITHOUT_QUESTIONS` | 400 | Công bố cuộc thi không có đề | Chặn |
| `CHEAT_EVENTS_JSONB_WRITE` | — | 🔴 Ghi JSONB vi phạm AC-09 | Mâu thuẫn đã nêu UC-091 |

> 🔴 **`EDIT_DURING_CONTEST` + `PRIZES_JSONB_UPDATED_LIVE`:** đổi luật giữa cuộc thi có **phần
> thưởng thật** là cơ sở khiếu nại. Người đăng ký dựa trên khung giờ và phần thưởng đã công bố.
> **Cần:** sau khi `starts_at` đã qua, chặn sửa `starts_at`, `ends_at`, đề, và `prizes`. Chỉ cho
> sửa mô tả.

> 🔴 **`TIMEZONE_MISINTERPRETED` — lần thứ năm vấn đề múi giờ.** `CONTENT_ADMIN` nhập "20:00"
> trên form. Nếu client gửi chuỗi `"20:00"` không kèm offset và server parse theo giờ máy chủ
> (UTC) thì cuộc thi mở lúc **03:00 sáng** giờ Việt Nam.
> **Cần:** client gửi ISO-8601 **có offset** (`2026-10-15T20:00:00+07:00`), server lưu
> `TIMESTAMPTZ`. Và form hiện rõ "giờ Việt Nam".

> 🔴 **`PRIZE_AS_CREDIT_WITHOUT_LEDGER` + `FINANCE_ROLE_NEEDED_FOR_PRIZE` — xung đột phân quyền
> cần chốt.** Feature tree 5.8: phần thưởng "trao thủ công hoặc **cộng điểm tài chính**".
> Nhưng cộng điểm tài chính là quyền `FINANCE_ADMIN` (UC-102), không phải `CONTENT_ADMIN`.
> Nếu `CONTENT_ADMIN` cộng điểm được thì việc tách 3 role mất ý nghĩa — họ tự tạo cuộc thi, tự
> thắng, tự trao thưởng bằng điểm.
> **Cần chốt:** `CONTENT_ADMIN` công bố người thắng; `FINANCE_ADMIN` thực hiện cộng điểm (qua
> UC-102 với `reason = CONTEST_PRIZE`). Hai người, hai bước — đúng separation of duties và là
> **điểm cộng khi bảo vệ**.

## Business rule

| # | Rule |
|---|---|
| BR-113-1 | Chỉ `CONTENT_ADMIN` tạo/quản lý cuộc thi |
| BR-113-2 | Khung giờ gửi kèm offset, lưu `TIMESTAMPTZ`, hiện "giờ Việt Nam" |
| BR-113-3 | Đang diễn ra → **chặn** sửa khung giờ, đề, `prizes` |
| BR-113-4 | Xếp hạng công bố **sau khi** đóng |
| BR-113-5 | Trao thưởng bằng điểm **phải** qua `FINANCE_ADMIN` + ghi sổ cái |
| BR-113-6 | Công bố cần có đề |
| BR-113-7 | Huỷ = `CANCELLED`, thông báo người đăng ký |
| BR-113-8 | ⚠️ Chốt cách ghi `cheat_events` (mâu thuẫn AC-09) |

## API · DB

```
POST  /api/admin/contests
PUT   /api/admin/contests/{id}
PATCH /api/admin/contests/{id}/publish
PATCH /api/admin/contests/{id}/cancel
POST  /api/admin/contests/{id}/publish-ranking
GET   /api/admin/contests/{id}/cheat-events
```
`contests` · `contest_participants` · `contest_submissions` (đọc + ghi) · audit log

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Tạo cuộc thi 20h–21h giờ VN | Lưu đúng `TIMESTAMPTZ` |
| T2 | Nhập "20:00", xem lại | Hiện **20:00 giờ VN**, không phải 03:00 |
| T3 | Sửa khung giờ khi đang diễn ra | 403 |
| T4 | Sửa `prizes` khi đang diễn ra | 403 |
| T5 | Công bố xếp hạng lúc 20h30 | 403 |
| T6 | `CONTENT_ADMIN` cộng điểm thưởng | 403 — cần `FINANCE_ADMIN` |
| T7 | `starts_at > ends_at` | 400 |
| T8 | Công bố cuộc thi không đề | 400 |

---

# UC-114 · Quản lý người dùng (tìm, xem, khóa)

| | |
|---|---|
| **UC-ID** | UC-114 · **Actor** `SUPER_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Tìm người dùng, xem thông tin, khóa/mở tài khoản. Quyết định v2: `SUPER_ADMIN` có "Quản lý
người dùng (khóa, mở, xem thông tin)".

## Tiền điều kiện

1. Role `SUPER_ADMIN`; CSRF
2. ⚠️ Cột trạng thái tài khoản (mục A quyết định v2 — **chưa chốt**)

## Hậu điều kiện

| Thao tác | Trạng thái |
|---|---|
| Khoá | `users.banned_at` (hoặc `suspended_at`); token bị thu hồi |
| Mở | Xoá dấu khoá |
| Xem | Chỉ đọc + audit log |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `SUPER_ADMIN` | Tìm theo email/tên |
| 2 | Client | `GET /api/admin/users?q={x}` |
| 3 | System | Kiểm role `SUPER_ADMIN` |
| 4 | System | Trả danh sách, **email đã che** |
| 5 | `SUPER_ADMIN` | Mở chi tiết một người |
| 6 | System | Trả đầy đủ + **ghi audit log** |
| 7 | `SUPER_ADMIN` | Bấm "Khoá tài khoản", nhập lý do |
| 8 | Client | `PATCH /api/admin/users/{id}/ban` + CSRF |
| 9 | System | Ghi `banned_at`, `banned_by`, `ban_reason` |
| 10 | System | **Thu hồi toàn bộ refresh token** của người đó |
| 11 | System | Audit log |

## Luồng thay thế

**A1 — Mở khoá** — xoá `banned_at`; **không** tự khôi phục token (người dùng đăng nhập lại).
**A2 — Khoá tạm (treo)** — `suspended_at` + `suspended_until`. `MANAGER` cũng làm được (UC-078).
**A3 — Xoá tài khoản** — 🔴 xem exception.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `SUPER_ADMIN` | Chặn |
| `STATUS_COLUMNS_MISSING` | 500 | 🔴 Chưa có `banned_at`/`suspended_at` | Xem ghi chú |
| `TOKENS_NOT_REVOKED` | — | 🔴 Khoá mà không thu hồi token | Xem ghi chú |
| `SELF_BAN` | 400 | 🔴 `SUPER_ADMIN` tự khoá mình | Xem ghi chú |
| `BAN_LAST_SUPER_ADMIN` | 400 | 🔴 Khoá `SUPER_ADMIN` cuối cùng | **Mất quyền quản trị hệ thống** |
| `HARD_DELETE_USER` | 403 | 🔴 Xoá cứng tài khoản | Xem ghi chú |
| `PII_EXPOSED_IN_LIST` | — | 🔴 Hiện email đầy đủ mọi người | Che ở danh sách (như UC-100) |
| `NO_ACCESS_AUDIT` | — | 🔴 Không ghi ai xem thông tin ai | Bắt buộc |
| `EMPTY_BAN_REASON` | 400 | Không lý do | Bắt buộc |
| `PASSWORD_HASH_IN_RESPONSE` | — | 🔴 Trả `password_hash` | **Không bao giờ** — `@JsonIgnore` + DTO |
| `BANNED_USER_STILL_ACTIVE` | — | 🔴 Bị khoá vẫn dùng được | Kiểm `banned_at` ở `JwtFilter` |

> 🔴 **`STATUS_COLUMNS_MISSING` — mục A quyết định v2 vẫn chưa chốt, và nó chặn UC này.**
> Quyết định v2 liệt kê 4 cột đề xuất (`email_verified_at`, `locked_until`, `suspended_at`,
> `banned_at`) nhưng ghi rõ *"Tài liệu hiện **chưa có** các cột này trong `users`"*.
> Không có cột thì UC-114 không làm được, và UC-012 (khoá sau N lần sai mật khẩu),
> UC-077/078 (treo tài khoản) cũng không.
> **Bốn UC cùng chờ một quyết định** — nên chốt sớm.

> 🔴 **`TOKENS_NOT_REVOKED` + `BANNED_USER_STILL_ACTIVE`:** đặt `banned_at` nhưng access token
> còn hiệu lực 15 phút, và refresh token còn 7 ngày → người bị khoá **vẫn dùng được** cho tới
> khi token hết hạn.
> **Cần cả hai:**
> — Thu hồi mọi refresh token ngay khi khoá (bước 10)
> — `JwtFilter` kiểm `banned_at` cho **mọi** request, không chỉ lúc đăng nhập
> Cách thứ hai tốn một truy vấn mỗi request — dùng cache ngắn nếu cần.

> 🔴 **`SELF_BAN` + `BAN_LAST_SUPER_ADMIN` — tự khoá mình ra khỏi hệ thống.** Nếu chỉ có một
> `SUPER_ADMIN` và người đó bị khoá (tự khoá hoặc bị người khác khoá) thì **không ai** cấp lại
> role được (UC-115 cần `SUPER_ADMIN`) → phải sửa trực tiếp trong DB.
> **Cần:** chặn tự khoá, và chặn khoá `SUPER_ADMIN` cuối cùng đang hoạt động.

> 🔴 **`HARD_DELETE_USER`:** `posts.author_id`, `follows.followee_id`, `game_scores.user_id` là
> các cột trỏ xuyên schema **không có khoá ngoại** (DB v5 §0.4). Xoá cứng user để lại dữ liệu
> mồ côi ở `community`, và `credit_transactions` (không bao giờ xoá) trỏ vào user không tồn tại.
> **Chốt:** **không hard delete user**. Chỉ `banned_at`. Đây là câu trả lời cho khoảng trống #15
> nhóm 5 (dọn `follows` khi xoá user) — không xoá thì không cần dọn.

## Business rule

| # | Rule |
|---|---|
| BR-114-1 | Chỉ `SUPER_ADMIN` |
| BR-114-2 | **Không hard delete** user — chỉ `banned_at` |
| BR-114-3 | Khoá → thu hồi **toàn bộ** refresh token |
| BR-114-4 | `JwtFilter` kiểm `banned_at` mỗi request |
| BR-114-5 | Chặn tự khoá; chặn khoá `SUPER_ADMIN` cuối cùng |
| BR-114-6 | Email **che** ở danh sách; đầy đủ ở chi tiết + audit log |
| BR-114-7 | **Không bao giờ** trả `password_hash` |
| BR-114-8 | Lý do khoá bắt buộc; ghi `banned_by` |
| BR-114-9 | ⚠️ **Chặn** tới khi chốt cột trạng thái (mục A) |

## API · DB

```
GET   /api/admin/users?q={x}
GET   /api/admin/users/{id}
PATCH /api/admin/users/{id}/ban
PATCH /api/admin/users/{id}/unban
```
`users` · `auth_tokens` (ghi) · audit log

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Khoá tài khoản | `banned_at` ghi, token **bị thu hồi** |
| T2 | Người bị khoá gọi API bằng token cũ | **401** |
| T3 | `MANAGER` gọi | 403 |
| T4 | `FINANCE_ADMIN` gọi | 403 |
| T5 | Tự khoá mình | 400 |
| T6 | Khoá `SUPER_ADMIN` cuối | 400 |
| T7 | Kiểm response | **Không** có `password_hash` |
| T8 | Danh sách | Email **đã che** |
| T9 | Xem chi tiết một người | Audit log có dòng |
| T10 | Cố `DELETE /api/admin/users/{id}` | **Không có** endpoint |

---

# UC-115 · Cấp và thu hồi role

| | |
|---|---|
| **UC-ID** | UC-115 · **Actor** `SUPER_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Gán/thu hồi role. `user_roles` là N-N, có `granted_by` để truy vết ai cấp quyền.

**Sáu role trong DB:** `USER` · `TEACHER` · `MANAGER` · `CONTENT_ADMIN` · `FINANCE_ADMIN` ·
`SUPER_ADMIN`. `GUEST` **không** phải dòng trong `roles`.

## Tiền điều kiện

1. Role `SUPER_ADMIN`; CSRF
2. Người nhận tồn tại, không bị khoá
3. Role tồn tại trong `roles`

## Hậu điều kiện

`user_roles` thêm/xoá dòng kèm `granted_by`, `granted_at`; audit log.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `SUPER_ADMIN` | Mở người dùng, chọn role |
| 2 | Client | `POST /api/admin/users/{id}/roles` — `{role_code}` + CSRF |
| 3 | System | Kiểm role `SUPER_ADMIN` |
| 4 | System | Kiểm `role_code` thuộc 6 role |
| 5 | System | Kiểm chưa có role đó |
| 6 | System | `INSERT user_roles` — `granted_by`, `granted_at` |
| 7 | System | **Audit log** riêng cho thao tác quyền |
| 8 | System | Thông báo người nhận |

## Luồng thay thế

**A1 — Thu hồi role** — `DELETE`; ghi audit log kèm ai thu hồi.
**A2 — Gán nhiều role** — cho phép (quyết định v2: leader có `SUPER_ADMIN` + `CONTENT_ADMIN` + `FINANCE_ADMIN`).
**A3 — Gán `GUEST`** — 400. `GUEST` không phải dòng trong `roles`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `SUPER_ADMIN` | 🔴 Xem ghi chú |
| `INVALID_ROLE_CODE` | 400 | Ngoài 6 role | Chặn |
| `GUEST_ROLE_ASSIGNED` | 400 | 🔴 Gán `GUEST` | `GUEST` là **trạng thái**, không phải role (A3) |
| `SELF_ROLE_GRANT` | 403 | 🔴 Tự cấp role cho mình | Xem ghi chú |
| `PRIVILEGE_ESCALATION_VIA_PROFILE` | — | 🔴 Cấp role qua `PUT /api/me` | Xem ghi chú |
| `ALREADY_HAS_ROLE` | 409 | Đã có | Idempotent hoặc 409 |
| `REVOKE_LAST_SUPER_ADMIN` | 400 | 🔴 Thu hồi `SUPER_ADMIN` cuối | **Mất quyền quản trị** |
| `ROLE_GRANTED_TO_BANNED_USER` | 422 | Người bị khoá | Chặn |
| `NO_GRANT_AUDIT` | — | 🔴 Không ghi `granted_by` | Quyết định v2 bắt buộc |
| `TOKEN_STILL_HAS_OLD_ROLES` | — | 🔴 Thu hồi role nhưng token còn quyền cũ | Xem ghi chú |
| `FINANCE_ROLE_GRANTED_CARELESSLY` | — | ⚠️ Cấp `FINANCE_ADMIN` dễ dãi | Role nguy hiểm nhất — nên cần xác nhận hai bước |

> 🔴 **`FORBIDDEN_ROLE` ở UC này là endpoint quan trọng nhất về bảo mật trong cả hệ thống.**
> Ai gọi được UC-115 thì **tự cấp mọi quyền** — kể cả `FINANCE_ADMIN` (sinh mã thẻ, điều chỉnh
> điểm). Một lỗi phân quyền ở đây là mất toàn bộ hệ thống.
> **Cần:** `@PreAuthorize("hasRole('SUPER_ADMIN')")` + test cho **cả 5 role khác** đều bị 403.

> 🔴 **`PRIVILEGE_ESCALATION_VIA_PROFILE` — cùng lỗ hổng `FORBIDDEN_FIELD` (UC-011).**
> UC-011 đã nêu: gửi `{"roles":["SUPER_ADMIN"]}` vào `PUT /api/me` là leo quyền nếu không
> whitelist trường.
> UC-115 là **đường hợp pháp duy nhất** để đổi role. Mọi endpoint khác phải **không** ghi được
> `user_roles`.
> **Cần:** ArchUnit hoặc kiểm code — chỉ `RoleService` được ghi `user_roles`, và chỉ được gọi
> từ UC-115. Cùng mẫu với `CreditService` (UC-094).

> 🔴 **`SELF_ROLE_GRANT`:** `SUPER_ADMIN` tự cấp `FINANCE_ADMIN` cho mình rồi sinh mã thẻ.
> Quyết định v2 ghi rõ *"`SUPER_ADMIN` **không tự động** có quyền của `CONTENT_ADMIN` hay
> `FINANCE_ADMIN`"* — nghĩa là phải gán thêm. Nhưng nếu tự gán được thì luật đó vô nghĩa.
> **Cần:** chặn `granted_by = user_id`. Phải có `SUPER_ADMIN` khác cấp — đúng separation of duties,
> và với nhóm 6 người demo thì có ít nhất 2 tài khoản `SUPER_ADMIN`.

> 🔴 **`TOKEN_STILL_HAS_OLD_ROLES`:** nếu JWT chứa danh sách role trong claim, thu hồi role mà
> token còn hiệu lực 15 phút → người đó **vẫn dùng quyền cũ** 15 phút. Với `FINANCE_ADMIN` thì
> 15 phút đủ sinh 1.000 mã thẻ.
> **Hai cách:**
> — Đọc role từ DB mỗi request (tốn truy vấn, chính xác ngay)
> — Giữ role trong token + thu hồi token khi đổi role (như UC-114 bước 10)
> **Khuyến nghị:** cách thứ hai — thu hồi token khi đổi role. Nhất quán với UC-114.

## Business rule

| # | Rule |
|---|---|
| BR-115-1 | **Chỉ** `SUPER_ADMIN` — test cả 5 role khác đều 403 |
| BR-115-2 | Chỉ `RoleService` được ghi `user_roles`; không endpoint nào khác |
| BR-115-3 | Chặn tự cấp role cho mình |
| BR-115-4 | Không thu hồi `SUPER_ADMIN` cuối cùng |
| BR-115-5 | `GUEST` **không** gán được |
| BR-115-6 | Bắt buộc `granted_by` + `granted_at` + audit log |
| BR-115-7 | Đổi role → **thu hồi token** của người đó |
| BR-115-8 | Một người nhiều role được |
| BR-115-9 | Không cấp role cho người bị khoá |

## API · DB

```
GET    /api/admin/users/{id}/roles
POST   /api/admin/users/{id}/roles
DELETE /api/admin/users/{id}/roles/{code}
```
`user_roles` · `roles` · `users` · `auth_tokens` (ghi) · audit log

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | `SUPER_ADMIN` cấp `TEACHER` | 201, `granted_by` ghi |
| T2 | `CONTENT_ADMIN` gọi | 403 |
| T3 | `FINANCE_ADMIN` gọi | 403 |
| T4 | `MANAGER` gọi | 403 |
| T5 | `TEACHER` gọi | 403 |
| T6 | `USER` gọi | 403 |
| T7 | Tự cấp `FINANCE_ADMIN` | 403 |
| T8 | `PUT /api/me` với `{"roles":[...]}` | Bị **bỏ qua** |
| T9 | Thu hồi `SUPER_ADMIN` cuối | 400 |
| T10 | Gán `GUEST` | 400 |
| T11 | Sau khi thu hồi role | Token cũ **không** dùng được quyền đó |

---

# UC-116 · Cấu hình hệ thống

| | |
|---|---|
| **UC-ID** | UC-116 · **Actor** `SUPER_ADMIN` · **Pri** P2 · **Scope** MVP · **FT** 6.6 |

## Mô tả

Đổi các tham số hệ thống mà không cần deploy lại.

> ⚠️ Catalog đã đánh dấu: *"Tính năng 6.6 nói chung, **chưa rõ cấu hình gì**"*.

## Tiền điều kiện

Role `SUPER_ADMIN`; CSRF.

## Hậu điều kiện

Cấu hình cập nhật; audit log; cache cấu hình xoá.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `SUPER_ADMIN` | Mở trang cấu hình |
| 2 | System | Kiểm role; trả danh sách tham số + giá trị hiện tại |
| 3 | `SUPER_ADMIN` | Đổi một giá trị |
| 4 | Client | `PUT /api/admin/settings/{key}` + CSRF |
| 5 | System | Validate theo loại và khoảng cho phép |
| 6 | System | Ghi giá trị mới + `updated_by` |
| 7 | System | Xoá cache cấu hình |
| 8 | System | Audit log kèm giá trị cũ và mới |

## Danh sách tham số đề xuất

Các ngưỡng đã xuất hiện trong 118 UC khác mà **không nên hardcode**:

| Nhó| Tham số | Nguồn |
|---|---|---|
| Học | Ngưỡng cổng chủ đề (90%) | UC-046 BR-046-1 |
| Học | Ngưỡng qua tầng phát âm (80%) | UC-021 BR-021-2 |
| Học | Hệ số mastery theo chế độ (0.5/1.0/1.2) | UC-015 → UC-018 |
| Thi | Hạn nộp bài dở (24h) | UC-035 |
| Thi | Số lượt làm đề/ngày (20) | UC-034 |
| Quota | Số lượt free mỗi tính năng (10) | UC-093 ⚠️ |
| Chấm bài | Tỉ lệ `teacher_payout` | UC-103 ⚠️ |
| Chấm bài | Hạn nhận / hạn chấm | UC-103 ⚠️ |
| Cộng đồng | Ngưỡng tự ẩn theo báo cáo | UC-075 ⚠️ |
| Cộng đồng | Số bài/ngày (5) | UC-069 |
| Game | Số ván/giờ (30) | UC-087 |
| Nhắc học | Giờ nhắc mặc định (20:00) | UC-054 |

## Luồng thay thế

**A1 — Đặt lại giá trị mặc định** — nút riêng.
**A2 — Xem lịch sử thay đổi** — audit log của từng tham số.
**A3 — Đổi tham số ảnh hưởng người đang dùng** — cảnh báo rõ (xem exception).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `SUPER_ADMIN` | Chặn |
| `UNKNOWN_SETTING_KEY` | 404 | Key lạ | Chặn — chỉ key đã khai |
| `VALUE_OUT_OF_RANGE` | 400 | Ngoài khoảng | Chặn |
| `THRESHOLD_CHANGE_BREAKS_PROGRESS` | — | 🔴 Đổi ngưỡng 90% → 95% | Xem ghi chú |
| `SECRET_IN_SETTINGS` | 403 | 🔴 Lưu API key / mật khẩu DB | Xem ghi chú |
| `CACHE_NOT_INVALIDATED` | — | Giá trị cũ còn trong cache | Xoá cache (bước 7) |
| `NO_CONFIG_AUDIT` | — | 🔴 Không ghi ai đổi gì | Bắt buộc, kèm giá trị cũ |
| `SETTING_TABLE_MISSING` | 500 | 🔴 Chưa có bảng lưu cấu hình | Xem ghi chú |
| `SCOPE_UNDEFINED` | — | ⚠️ Chưa rõ cấu hình gì | Catalog đã nêu |
| `CHANGE_DURING_CONTEST` | 403 | Đổi ngưỡng khi cuộc thi đang diễn ra | Chặn — như UC-113 |

> 🔴 **`THRESHOLD_CHANGE_BREAKS_PROGRESS` — đổi ngưỡng có hậu quả ngược.** Ngưỡng cổng 90% →
> 95%: người đã đạt 92% và **đã được mở** chủ đề sau giờ ở trạng thái "chưa đạt" nhưng chủ đề
> vẫn mở (BR-046-6: đã mở không đóng lại). Ngược lại 90% → 85% thì nhiều người đủ điều kiện mở
> nhưng **không có sự kiện nào** gọi UC-046 để mở — cùng vấn đề `STALE_PERCENT_READ`.
> **Cần:** đổi ngưỡng phải kèm job quét lại toàn bộ `user_topic_progress` và mở khoá bổ sung.
> Hoặc chốt: **không đổi ngưỡng sau khi có người dùng**.

> 🔴 **`SECRET_IN_SETTINGS`:** rất tự nhiên để đặt API key AI vào bảng cấu hình cho "dễ đổi".
> Nhưng constitution cấm "secret, API key, mật khẩu trong code, config hay log — dùng **biến môi
> trường**".
> Bảng cấu hình trong DB là **config**. Và `SUPER_ADMIN` đọc được bảng đó → ai có role đó thấy
> API key.
> **Chốt:** bảng cấu hình **chỉ** chứa tham số nghiệp vụ (ngưỡng, giới hạn), **không** chứa secret.

> 🔴 **`SETTING_TABLE_MISSING`:** 59 bảng hiện tại **không có** bảng cấu hình. Các ngưỡng đang là
> hằng số trong code.
> **Cần chốt:** thêm bảng `system_settings` hay giữ hằng số trong code?
> — Bảng: đổi được không cần deploy, nhưng thêm bảng + cache + màn quản trị
> — Hằng số: đơn giản, nhưng đổi ngưỡng phải deploy
> **Khuyến nghị cho 11 tuần:** hằng số trong một file cấu hình duy nhất (`application.yaml`),
> và UC-116 **cắt khỏi MVP** (P2, dễ cắt). Ghi rõ trong tài liệu để không bị hỏi.

## Business rule

| # | Rule |
|---|---|
| BR-116-1 | Chỉ `SUPER_ADMIN` |
| BR-116-2 | Chỉ key đã khai trước; validate khoảng |
| BR-116-3 | **Không** lưu secret trong cấu hình — dùng biến môi trường |
| BR-116-4 | Audit log kèm giá trị **cũ** và mới |
| BR-116-5 | Xoá cache sau khi đổi |
| BR-116-6 | Đổi ngưỡng học phải kèm job quét lại, hoặc không cho đổi |
| BR-116-7 | Chặn đổi khi cuộc thi đang diễn ra |
| BR-116-8 | ⚠️ Chốt: bảng cấu hình hay hằng số (khuyến nghị hằng số, cắt UC này) |

## API · DB

```
GET /api/admin/settings
PUT /api/admin/settings/{key}
```
⚠️ **bảng `system_settings` — chưa có** · audit log

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Đổi ngưỡng cổng 90 → 85 | Lưu, audit log có giá trị cũ |
| T2 | `CONTENT_ADMIN` gọi | 403 |
| T3 | Key lạ | 404 |
| T4 | Ngưỡng 150% | 400 |
| T5 | Lưu API key vào cấu hình | **Chặn** |
| T6 | Sau khi đổi | Cache đã xoá, giá trị mới có hiệu lực |

---

# UC-117 · Xem danh sách kênh YouTube và podcast

| | |
|---|---|
| **UC-ID** | UC-117 · **Actor** `GUEST` `USER` · **Pri** P3 · **Scope** V2 · **FT** 7.1 |

## Mô tả

Danh mục kênh YouTube và podcast học tiếng Trung — **chỉ link ra ngoài**, không nhúng nội dung.

## Tiền điều kiện

`learning_resources` có dòng `type = YOUTUBE`/`PODCAST`, đã công bố.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | Người dùng | Mở trang "Tài nguyên" |
| 2 | Client | `GET /api/public/resources?type=YOUTUBE` |
| 3 | System | Lấy `learning_resources` đã công bố |
| 4 | System | Trả tên, mô tả, cấp HSK phù hợp, **link ngoài** |
| 5 | Client | Hiện danh sách; bấm mở tab mới |

## Luồng thay thế

**A1 — Lọc theo cấp HSK** — `?hsk_level=3`.
**A2 — Kênh không còn tồn tại** — người dùng báo lỗi; `CONTENT_ADMIN` xử lý (UC-119).
**A3 — `GUEST` xem** — được (theo bảng quyền role).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `NO_RESOURCES` | 200 (rỗng) | Chưa có | Hiện "đang cập nhật" |
| `DEAD_LINK` | — | Kênh đã xoá | Cho báo lỗi (A2); không tự kiểm |
| `UNSAFE_EXTERNAL_LINK` | — | 🔴 Link tới trang độc hại | Xem ghi chú |
| `MISSING_NOOPENER` | — | 🔴 Thiếu `rel="noopener"` | Xem ghi chú |
| `EMBEDDED_CONTENT` | — | 🔴 Nhúng video thay vì link | Xem ghi chú |
| `INVALID_HSK_LEVEL` | 400 | Ngoài 1–9 | Chặn |
| `UNPUBLISHED_RESOURCE_LEAKED` | — | Dòng nháp lộ ra | Lọc trạng thái |

> 🔴 **`UNSAFE_EXTERNAL_LINK` — rủi ro riêng của tính năng chỉ chứa link.** `CONTENT_ADMIN` nhập
> link; nếu tài khoản đó bị chiếm hoặc nhập nhầm thì hệ thống dẫn người học tới trang lừa đảo,
> và **uy tín thuộc về CNHSK** vì link nằm trên trang của nhóm.
> **Cần:** whitelist domain (`youtube.com`, `youtu.be`, các nền tảng podcast đã duyệt) — validate
> ở UC-119 khi nhập, không phải khi hiện.

> 🔴 **`MISSING_NOOPENER`:** `target="_blank"` không có `rel="noopener noreferrer"` cho trang đích
> truy cập `window.opener` → **tabnabbing**: trang đích đổi tab gốc thành trang đăng nhập giả.
> Một thuộc tính HTML, chặn được một lớp tấn công.

> 🔴 **`EMBEDDED_CONTENT`:** feature tree 1.6 đã cảnh báo về bản quyền: *"Không tải video của
> người khác về máy chủ. Nếu nhúng YouTube thì chỉ nhúng, không lưu"*.
> Nhóm 7 là **danh mục tham khảo** — chỉ nên **link ra ngoài**, không nhúng. Đơn giản hơn và
> tránh hoàn toàn vấn đề bản quyền.

## Business rule

| # | Rule |
|---|---|
| BR-117-1 | Chỉ **link ra ngoài**, không nhúng nội dung |
| BR-117-2 | Whitelist domain — validate lúc nhập (UC-119) |
| BR-117-3 | Link ngoài dùng `rel="noopener noreferrer"` |
| BR-117-4 | Chỉ hiện dòng đã công bố |
| BR-117-5 | `GUEST` xem được |
| BR-117-6 | Cho người dùng báo link chết |

## API · DB

```
GET /api/public/resources?type={YOUTUBE|PODCAST}
```
`learning_resources` (đọc)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Xem danh sách kênh | Tên + mô tả + link |
| T2 | `GUEST` | Xem được |
| T3 | Kiểm HTML link ngoài | Có `rel="noopener noreferrer"` |
| T4 | Dòng chưa công bố | Không hiện |
| T5 | `hsk_level = 12` | 400 |

---

# UC-118 · Xem danh mục sách học tiếng Trung

| | |
|---|---|
| **UC-ID** | UC-118 · **Actor** `GUEST` `USER` · **Pri** P3 · **Scope** V2 · **FT** 7.2 |

## Mô tả

Danh mục sách: tên, tác giả, cấp HSK, mô tả, và **link nơi mua** — không có nội dung sách.

## Tiền điều kiện

`learning_resources` có dòng `type = BOOK`, đã công bố.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

Giống UC-117, khác `type = BOOK` và các trường (tác giả, nhà xuất bản, năm).

## Luồng thay thế

**A1 — Lọc theo cấp HSK** · **A2 — Không có link mua** (chỉ giới thiệu) · **A3 — `GUEST` xem**.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `NO_RESOURCES` | 200 (rỗng) | Chưa có | "Đang cập nhật" |
| `BOOK_CONTENT_HOSTED` | — | 🔴 Lưu file PDF sách | Xem ghi chú |
| `UNSAFE_EXTERNAL_LINK` | — | Link mua không an toàn | Whitelist (UC-119) |
| `MISSING_NOOPENER` | — | Thiếu thuộc tính | Như UC-117 |
| `AFFILIATE_LINK_UNDISCLOSED` | — | ⚠️ Link tiếp thị liên kết không khai báo | Xem ghi chú |
| `COVER_IMAGE_COPYRIGHT` | — | ⚠️ Ảnh bìa sách | Xem ghi chú |

> 🔴 **`BOOK_CONTENT_HOSTED` là rủi ro pháp lý nghiêm trọng nhất của nhóm 7.** Rất dễ nghĩ
> "danh mục sách" thành "tải sách về đọc". Lưu PDF sách có bản quyền lên máy chủ là **phân phối
> tác phẩm không phép** — nặng hơn nhiều so với nhúng video.
> Constitution cấm commit dữ liệu đề thi của thầy vì bản quyền; sách thương mại thì rủi ro cao hơn.
> **Chốt:** UC này **chỉ** là danh mục có link mua. Không lưu, không cho tải.

> ⚠️ **`AFFILIATE_LINK_UNDISCLOSED`:** nếu link mua là link tiếp thị liên kết (nhóm nhận hoa
> hồng) thì nên khai báo. Với đồ án chưa phải vấn đề, nhưng nếu dùng link affiliate thì ghi rõ.

> ⚠️ **`COVER_IMAGE_COPYRIGHT`:** ảnh bìa sách cũng có bản quyền. An toàn nhất là **không** lưu
> ảnh bìa, hoặc chỉ hotlink từ trang bán (và chấp nhận ảnh có thể mất).

## Business rule

| # | Rule |
|---|---|
| BR-118-1 | **Chỉ** danh mục + link mua — **không** lưu nội dung sách |
| BR-118-2 | Không lưu ảnh bìa; hoặc chỉ dẫn link |
| BR-118-3 | Whitelist domain link mua |
| BR-118-4 | `rel="noopener noreferrer"` |
| BR-118-5 | `GUEST` xem được |
| BR-118-6 | Khai báo nếu dùng link affiliate |

## API · DB

```
GET /api/public/resources?type=BOOK
```
`learning_resources` (đọc)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Xem danh mục sách | Tên, tác giả, cấp, link mua |
| T2 | Tìm file PDF trong hệ thống | **Không có** |
| T3 | `GUEST` | Xem được |
| T4 | Link mua | Có `rel="noopener"` |

---

# UC-119 · Quản lý danh mục tham khảo

| | |
|---|---|
| **UC-ID** | UC-119 · **Actor** `CONTENT_ADMIN` · **Pri** P3 · **Scope** V2 · **FT** 7.1 · 7.2 |

## Mô tả

Thêm/sửa/xoá mục trong `learning_resources` (kênh, podcast, sách), công bố hoặc ẩn.

## Tiền điều kiện

Role `CONTENT_ADMIN`; CSRF.

## Hậu điều kiện

`learning_resources` cập nhật; audit log.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `CONTENT_ADMIN` | Mở quản lý tài nguyên |
| 2 | `CONTENT_ADMIN` | Thêm mục: loại, tên, mô tả, cấp HSK, link |
| 3 | Client | `POST /api/admin/resources` + CSRF |
| 4 | System | Kiểm role |
| 5 | System | **Validate link theo whitelist domain** |
| 6 | System | Validate URL đúng định dạng, dùng `https` |
| 7 | System | Ghi `learning_resources` `published = false` |
| 8 | `CONTENT_ADMIN` | Kiểm lại rồi công bố |
| 9 | System | Audit log |

## Luồng thay thế

**A1 — Ẩn mục** — `published = false`; không xoá.
**A2 — Xử lý báo link chết** — kiểm, sửa link hoặc ẩn mục.
**A3 — Nhập nhiều mục từ file** — dùng luồng UC-110 nếu cần.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Không phải `CONTENT_ADMIN` | Chặn |
| `DOMAIN_NOT_WHITELISTED` | 400 | 🔴 Link ngoài whitelist | Chặn — UC-117/118 |
| `NON_HTTPS_LINK` | 400 | Link `http://` | Chặn |
| `MALFORMED_URL` | 400 | URL sai định dạng | Chặn |
| `JAVASCRIPT_URL` | 400 | 🔴 `javascript:` trong link | Xem ghi chú |
| `XSS_IN_DESCRIPTION` | — | 🔴 Script trong mô tả | Escape — `GUEST` cũng đọc |
| `PUBLISHED_WITHOUT_REVIEW` | — | ⚠️ Công bố ngay không kiểm | Mặc định `published = false` |
| `HARD_DELETE_RESOURCE` | — | Xoá cứng | Dùng `published = false` |
| `NO_AUDIT_LOG` | — | Không ghi ai thêm link | Bắt buộc — truy vết nếu link xấu |
| `FINANCE_ADMIN_ATTEMPTED` | 403 | Sai role | Chặn |

> 🔴 **`JAVASCRIPT_URL` — lỗ hổng XSS qua link.** Nhập `javascript:alert(document.cookie)` vào
> trường link. Nếu render thành `<a href="javascript:...">` thì bấm vào là chạy script — và
> `GUEST`/`USER` đều bấm được.
> **Cần:** validate scheme chỉ `https`. Whitelist domain (bước 5) đã chặn phần lớn, nhưng kiểm
> scheme là lớp riêng vì `javascript:` không có domain.

> 🔴 **`XSS_IN_DESCRIPTION`:** mô tả là văn bản tự do do `CONTENT_ADMIN` nhập, hiện cho **cả
> `GUEST`** (UC-117/118 là endpoint public). Cùng rủi ro UC-069 nhưng phạm vi rộng hơn — không
> cần đăng nhập để bị tấn công.

## Business rule

| # | Rule |
|---|---|
| BR-119-1 | Chỉ `CONTENT_ADMIN` |
| BR-119-2 | Link phải thuộc **whitelist domain** |
| BR-119-3 | Chỉ scheme `https` — chặn `javascript:`, `data:` |
| BR-119-4 | Mặc định `published = false` |
| BR-119-5 | Escape mô tả khi render |
| BR-119-6 | Ẩn thay vì xoá |
| BR-119-7 | Audit log ai thêm/sửa link |

## API · DB

```
GET    /api/admin/resources
POST   /api/admin/resources
PUT    /api/admin/resources/{id}
PATCH  /api/admin/resources/{id}/publish
PATCH  /api/admin/resources/{id}/unpublish
```
`learning_resources` (đọc + ghi) · audit log

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Thêm kênh YouTube | 201, `published = false` |
| T2 | Link `http://` | 400 |
| T3 | Link `javascript:alert(1)` | **400** |
| T4 | Domain ngoài whitelist | 400 |
| T5 | Mô tả có `<script>` | Render ra text ở endpoint public |
| T6 | `FINANCE_ADMIN` gọi | 403 |
| T7 | Sau khi thêm | Audit log có `created_by` |

---

# Tổng hợp exception nhóm 6c + 6d + 7

## Mười exception quan trọng nhất

| # | UC | Exception | Vì sao |
|---|---|---|---|
| 1 | UC-115 | `FORBIDDEN_ROLE` | Endpoint quan trọng nhất về bảo mật — ai gọi được thì **tự cấp mọi quyền** |
| 2 | UC-110 | `UNKNOWN_FILE_FORMAT` | **P0** và "chưa ai thấy file thật của thầy" — rủi ro **tiến độ**, không phải kỹ thuật |
| 3 | UC-110 | `MISSING_KNOWLEDGE_POINT` | Nhập thiếu nhãn → 4 UC lộ trình mù, và "không sửa được nếu không nhập lại" |
| 4 | UC-114 | `TOKENS_NOT_REVOKED` | Khoá tài khoản mà token còn hiệu lực → người bị khoá vẫn dùng được |
| 5 | UC-115 | `PRIVILEGE_ESCALATION_VIA_PROFILE` | Đổi role qua `PUT /api/me` — cùng lỗ hổng UC-011 |
| 6 | UC-108 | `BULK_APPROVE_ALL_BLINDLY` | Nghiệm thu khuyến khích duyệt nhanh, nhưng AI hallucination **chỉ người đọc** phát hiện được |
| 7 | UC-110 | `ENCODING_ERROR` | Chữ Hán thành `???` mà **không báo lỗi** — ghi dữ liệu rác thành công |
| 8 | UC-109 | `QUESTION_CHANGED_AFTER_ATTEMPTS` | Sửa đáp án làm `attempt_answers` cũ sai nghĩa |
| 9 | UC-113 | `PRIZE_AS_CREDIT_WITHOUT_LEDGER` | `CONTENT_ADMIN` cộng điểm thưởng = phá separation of duties |
| 10 | UC-118 | `BOOK_CONTENT_HOSTED` | Lưu PDF sách có bản quyền — rủi ro pháp lý nặng nhất |

## Bốn nhóm exception lặp lại

| Nhóm | Xuất hiện ở | Bài học |
|---|---|---|
| **Phân quyền sai role** | UC-108 → UC-119 (**cả 12 UC**) | Nghiệm thu 6.6: "mỗi role **chỉ thấy phần mình quản**". 12 UC, 4 role khác nhau. Cần test **ma trận**: mỗi endpoint × mỗi role → đúng 403 hoặc 200 |
| **Validate lúc nhập, không lúc chạy** | UC-110 · UC-112 · UC-119 | Nhãn kiến thức, chu trình tiên quyết, whitelist domain — chặn ở cửa vào rẻ hơn xử lý hậu quả ở 4 UC khác |
| **Không xoá, chỉ ẩn** | UC-109 · UC-112 · UC-114 · UC-119 | `ARCHIVED` / `banned_at` / `published = false`. Xoá cứng làm mồ côi dữ liệu ở bảng khác (cột trỏ xuyên schema không có FK) |
| **Audit log là lớp phòng vệ cuối** | UC-108 → UC-116 | Separation of duties không chặn hết được bằng code. `granted_by`, `created_by`, `reviewed_by`, `banned_by` — và `SUPER_ADMIN` xem được |

---

# Khoảng trống thiết kế phát hiện ở nhóm 6c + 6d + 7

| # | Thiếu | UC bị ảnh hưởng | Mức |
|---|---|---|---|
| 1 | **Chưa biết định dạng file thật của thầy** — 8 câu chưa hỏi | UC-110 · UC-111 | 🔴 Chặn UC **P0** |
| 2 | **Chưa chốt khoá tự nhiên** để upsert khi nhập lại | UC-110 | 🔴 Chạy lại tạo bản trùng |
| 3 | **Chưa có cột trạng thái tài khoản** (`banned_at`, `suspended_at`, `locked_until`) — mục A quyết định v2 | UC-114 (+ UC-012, UC-077, UC-078) | 🔴 **Bốn UC** cùng chờ |
| 4 | **Chưa có bảng chi tiết lỗi nhập** — `import_runs` chỉ có tổng hợp | UC-111 | 🔴 Vi phạm nghiệm thu "báo rõ dòng nào sai" |
| 5 | Chưa có kiểm chu trình tiên quyết khi lưu | UC-112 (+ UC-045, UC-046) | 🔴 Treo thuật toán |
| 6 | Chưa có validate "mọi câu có nhãn" **lúc công bố đề** | UC-112 (+ UC-040) | 🔴 Chốt cuối chặn tính năng 2.3 mù |
| 7 | Chưa chốt **`CONTENT_ADMIN` có duyệt câu AI không** | UC-108 | 🔴 Ranh giới role mờ |
| 8 | Chưa chốt **có chặn sửa đáp án câu đã dùng không** | UC-109 (+ UC-035) | 🔴 Chấm sai người học |
| 9 | Chưa có luật **thu hồi token khi đổi role / khoá tài khoản** | UC-114 · UC-115 | 🔴 Quyền cũ còn hiệu lực 15 phút |
| 10 | Chưa có `RoleService` độc quyền ghi `user_roles` + ArchUnit | UC-115 | 🔴 Leo quyền qua endpoint khác |
| 11 | Chưa chặn **tự cấp role** và **thu hồi `SUPER_ADMIN` cuối** | UC-115 | 🔴 Tự in quyền / mất quyền quản trị |
| 12 | Chưa chốt **`CONTENT_ADMIN` vs `FINANCE_ADMIN`** khi trao thưởng cuộc thi | UC-113 | 🔴 Phá separation of duties |
| 13 | **Chưa có bảng `system_settings`** — các ngưỡng là hằng số | UC-116 | 🔴 Hoặc chốt cắt UC-116 |
| 14 | Chưa có **whitelist domain** cho link ngoài | UC-117 → UC-119 | 🔴 Dẫn người học tới trang xấu |
| 15 | **Lệch tên bảng:** feature tree ghi `import_batches`, DB v5 có `import_runs` | UC-110 | ⚠️ Thống nhất tên |
| 16 | Chưa có `.gitignore` chặn thư mục dữ liệu thầy gửi | UC-110 | ⚠️ Bản quyền |
| 17 | Chưa có **test ma trận role × endpoint** | UC-108 → UC-119 | ⚠️ Nghiệm thu 6.6 |
| 18 | Chưa chốt xử lý **đổi ngưỡng sau khi có người dùng** | UC-116 | ⚠️ Mở khoá bổ sung hay không đổi |
| 19 | Chưa chốt **phạm vi cấu hình hệ thống** (catalog đã nêu) | UC-116 | ⚠️ "Chưa rõ cấu hình gì" |

> **Mười bốn mục 🔴.** Nổi bật:
> — **#1 và #2 là rủi ro tiến độ, không phải kỹ thuật.** UC-110 là P0; không có định dạng file
> thật thì parser viết xong phải viết lại. Việc cần làm **ngay** là lấy file mẫu từ thầy.
> — **#3 là quyết định bị bốn UC cùng chờ** — chốt một lần mở được cả bốn.
> — **#9, #10, #11 cùng thuộc một chủ đề:** kiểm soát quyền hạn. UC-115 là endpoint duy nhất
> đổi role, nên ba mục này phải làm cùng nhau.

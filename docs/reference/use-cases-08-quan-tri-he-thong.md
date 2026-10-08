# CNHSK — Đặc tả Use Case · Nhóm 6c + 6d + 7 · Quản trị nội dung, hệ thống & tham khảo

> **UC-108 → UC-119** · 12 Use Case  
> **Bản reviewed** · cập nhật 2026-10-08  
> Mục tiêu của bản này: làm nguồn chuẩn để viết Use Case Description và hỗ trợ code generation.  
> Không thêm Use Case mới, không bỏ Use Case hiện có; chỉ sửa logic, phạm vi, cách diễn đạt và cấu trúc để tránh mơ hồ.

---

## Bảng tra nhanh

| UC-ID | Use Case | Actor chính | Pri | Scope | FT | Trạng thái triển khai |
| --- | --- | --- | --- | --- | --- | --- |
| UC-108 | Duyệt câu hỏi AI sinh | `TEACHER` | P1 | MVP | 6.6 | READY |
| UC-109 | Sửa nội dung câu hỏi | `TEACHER`, `CONTENT_ADMIN` | P1 | MVP | 6.5 · 6.6 | READY |
| UC-110 | Nhập dữ liệu đề thi/từ vựng/ngữ pháp từ file | `CONTENT_ADMIN` | **P0** | MVP | 6.2 | **BLOCKED** — cần file mẫu thật, định dạng và khóa tự nhiên |
| UC-111 | Xem báo cáo lỗi sau khi nhập | `CONTENT_ADMIN` | **P0** | MVP | 6.2 | **PARTIALLY BLOCKED** — cần nơi lưu chi tiết lỗi import |
| UC-112 | Quản lý đề thi và kho câu hỏi | `CONTENT_ADMIN` | P1 | MVP | 6.3 · 6.5 | READY |
| UC-113 | Tạo và quản lý cuộc thi | `CONTENT_ADMIN` | P2 | V2 | 5.8 | V2 — không gen MVP |
| UC-114 | Quản lý người dùng | `SUPER_ADMIN` | P1 | MVP | 6.1 | **BLOCKED** — cần chốt trường trạng thái tài khoản |
| UC-115 | Cấp và thu hồi role | `SUPER_ADMIN` | P1 | MVP | 6.1 | READY |
| UC-116 | Cấu hình hệ thống | `SUPER_ADMIN` | P2 | Deferred | 6.6 | **DEFERRED** — phạm vi cấu hình và nơi lưu chưa chốt |
| UC-117 | Xem danh sách kênh YouTube và podcast | `GUEST`, `USER` | P3 | V2 | 7.1 | V2 — không gen MVP |
| UC-118 | Xem danh mục sách học tiếng Trung | `GUEST`, `USER` | P3 | V2 | 7.2 | V2 — không gen MVP |
| UC-119 | Quản lý danh mục tham khảo | `CONTENT_ADMIN` | P3 | V2 | 7.1 · 7.2 | V2 — không gen MVP |

> **Nguyên tắc phân quyền của nhóm này:** mỗi role chỉ được truy cập đúng phần quản trị được giao.  
> `SUPER_ADMIN` không tự động có quyền nghiệp vụ của `TEACHER`, `CONTENT_ADMIN` hoặc `FINANCE_ADMIN`.

---

# UC-108 · Duyệt câu hỏi AI sinh

| | |
| --- | --- |
| **UC-ID** | UC-108 |
| **Actor chính** | `TEACHER` |
| **Loại** | User Goal |
| **Pri** | P1 |
| **Scope** | MVP |
| **FT** | 6.6 |
| **Trạng thái triển khai** | READY |

## Mô tả

`TEACHER` xem các câu hỏi do AI sinh đang chờ kiểm duyệt, đọc đầy đủ nội dung câu hỏi, đáp án, lời giải, cấp HSK và nhãn kiến thức, sau đó quyết định **duyệt**, **sửa rồi duyệt**, **từ chối**, hoặc **chưa xử lý**.

Mục tiêu của Use Case là bảo đảm câu hỏi AI chỉ được đưa vào kho dùng chung sau khi có người đủ chuyên môn kiểm tra. Câu hỏi chưa duyệt hoặc bị từ chối không được xuất hiện ở bất kỳ luồng học nào của người học.

## Kích hoạt

`TEACHER` mở màn hình “Hàng đợi duyệt câu hỏi AI” hoặc chọn một câu `PENDING_REVIEW` để kiểm tra.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `TEACHER`.
2. Câu hỏi cần xử lý tồn tại, có `source = AI` và đang ở trạng thái `PENDING_REVIEW`.
3. Hệ thống có đủ dữ liệu cần thiết để đánh giá câu hỏi: nội dung, đáp án, lời giải, cấp HSK và nhãn kiến thức.

## Hậu điều kiện

### Khi duyệt thành công

- Câu hỏi chuyển sang `APPROVED`.
- Hệ thống lưu người duyệt và thời điểm duyệt.
- Hành động duyệt được ghi vào lịch sử review.
- Câu hỏi đủ điều kiện được dùng trong kho câu hỏi chung.

### Khi từ chối thành công

- Câu hỏi chuyển sang `REJECTED`.
- Lý do từ chối được lưu.
- Người xử lý và thời điểm xử lý được ghi lại.
- Câu hỏi không được đưa đến người học.

### Khi chưa đưa ra quyết định

- Câu hỏi giữ nguyên `PENDING_REVIEW`.
- Không có thay đổi ảnh hưởng đến kho câu hỏi cho người học.

### Khi xử lý thất bại

- Không để câu hỏi đổi trạng thái nhưng thiếu lịch sử review.
- Không ghi đè quyết định của một giáo viên khác đã xử lý trước đó.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `TEACHER` | Mở hàng đợi câu hỏi AI đang chờ duyệt. |
| 2 | System | Kiểm tra người dùng có role `TEACHER`. |
| 3 | System | Lấy các câu `PENDING_REVIEW`, ưu tiên câu chờ lâu hơn trước và phân trang. |
| 4 | `TEACHER` | Mở một câu hỏi cần kiểm tra. |
| 5 | System | Hiển thị nội dung câu hỏi, các đáp án, đáp án đúng, lời giải, cấp HSK, nhãn kiến thức và thông tin nguồn AI cần thiết. |
| 6 | `TEACHER` | Kiểm tra tính đúng đắn, ngữ pháp, mức độ phù hợp HSK và khả năng chỉ có một đáp án đúng. |
| 7 | `TEACHER` | Chọn **Duyệt**. |
| 8 | System | Kiểm tra lại câu vẫn đang `PENDING_REVIEW` và cấu trúc câu hỏi còn hợp lệ. |
| 9 | System | Chuyển câu sang `APPROVED`, ghi người duyệt, thời điểm duyệt và lịch sử review. |
| 10 | Client | Loại câu đã xử lý khỏi hàng đợi và hiển thị kết quả duyệt. |

## Luồng thay thế

**A1 — Từ chối câu hỏi**

1. Tại bước 7, `TEACHER` chọn **Từ chối**.
2. Hệ thống yêu cầu nhập lý do.
3. Hệ thống kiểm tra lý do không rỗng và câu vẫn đang `PENDING_REVIEW`.
4. Câu chuyển sang `REJECTED`; lịch sử review được ghi.
5. Câu bị loại khỏi hàng đợi đang xử lý và không được phục vụ cho người học.

**A2 — Sửa rồi duyệt**

1. `TEACHER` nhận thấy câu có thể sửa được.
2. Hệ thống chuyển sang UC-109 với đúng câu đang review.
3. Sau khi sửa hợp lệ, `TEACHER` quay lại UC-108.
4. Giáo viên đọc lại câu sau sửa và thực hiện quyết định duyệt hoặc từ chối.

**A3 — Chưa chắc chắn**

- `TEACHER` không đưa ra quyết định.
- Câu giữ nguyên `PENDING_REVIEW`.
- Không tạo trạng thái trung gian mới nếu dự án chưa định nghĩa.

**A4 — Duyệt hàng loạt**

- Chỉ những câu giáo viên đã mở xem mới được chọn để duyệt hàng loạt.
- Hệ thống xử lý từng câu theo cùng điều kiện của luồng duyệt đơn.
- Nếu một câu đã được người khác xử lý, câu đó bị báo xung đột; các câu hợp lệ khác vẫn được xử lý.
- Yêu cầu “20 mục dưới 2 phút” được hiểu là thao tác giao diện và xử lý hệ thống phải nhanh, không phải ép giáo viên đọc mỗi câu trong vài giây.

**A5 — Câu đã được giáo viên khác xử lý**

- Hệ thống không ghi đè quyết định cũ.
- Client tải lại trạng thái mới và loại câu khỏi hàng đợi nếu không còn `PENDING_REVIEW`.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên đăng nhập không hợp lệ | Không trả dữ liệu review. |
| `FORBIDDEN_ROLE` | 403 | Người dùng không có role `TEACHER` | Chặn tại server. |
| `QUESTION_NOT_FOUND` | 404 | Câu hỏi không tồn tại | Không thay đổi dữ liệu. |
| `QUESTION_NOT_PENDING` | 409 | Câu đã được duyệt/từ chối trước đó | Trả trạng thái hiện tại, không ghi đè. |
| `EMPTY_REJECTION_REASON` | 422 | Từ chối nhưng không có lý do | Yêu cầu nhập lý do. |
| `MALFORMED_QUESTION` | 422 | Câu không đạt cấu trúc tối thiểu | Không cho duyệt; chuyển sang sửa hoặc từ chối. |

## Business rule

| # | Rule |
| --- | --- |
| BR-108-1 | Chỉ `TEACHER` được duyệt hoặc từ chối câu hỏi do AI sinh. Việc kiểm tra quyền phải thực hiện ở server. |
| BR-108-2 | Chỉ câu hỏi có `status = PENDING_REVIEW` và `source = AI` mới được đưa vào hàng đợi duyệt. |
| BR-108-3 | Câu hỏi `PENDING_REVIEW` hoặc `REJECTED` không được xuất hiện trong bất kỳ bài học, bài luyện, quiz hoặc đề thi nào của người học. |
| BR-108-4 | Khi duyệt, hệ thống chuyển câu hỏi sang `APPROVED`, ghi người duyệt và thời điểm duyệt. |
| BR-108-5 | Khi từ chối, lý do từ chối là bắt buộc để phục vụ cải thiện prompt hoặc sửa nội dung sau này. |
| BR-108-6 | Nếu hai giáo viên duyệt cùng một câu, chỉ thao tác đầu tiên được ghi nhận. Thao tác sau phải bị từ chối vì câu không còn ở trạng thái chờ duyệt. |
| BR-108-7 | Duyệt hàng loạt chỉ được áp dụng cho các câu mà giáo viên đã mở xem. Không cho phép “chọn tất cả rồi duyệt” khi chưa xem nội dung. |
| BR-108-8 | Hệ thống phải kiểm tra cấu trúc câu hỏi trước khi cho duyệt: có nội dung, có lời giải, có nhãn kiến thức, và nếu là trắc nghiệm thì có đúng một đáp án đúng. |
| BR-108-9 | Mỗi hành động duyệt, sửa rồi duyệt, hoặc từ chối phải được ghi vào lịch sử review để truy vết trách nhiệm. |

## API · DB

```text
GET   /api/teacher/questions?status=PENDING_REVIEW
PATCH /api/teacher/questions/{id}/review
PATCH /api/teacher/questions/bulk-review
```

Dữ liệu liên quan: `questions`, `question_options`, `question_knowledge_points`, `review_actions`, `ai_generation_jobs`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `TEACHER` duyệt câu hợp lệ | `APPROVED`, có `reviewed_by`, `reviewed_at` và lịch sử review. |
| T2 | `USER` hoặc `CONTENT_ADMIN` gọi endpoint review | 403. |
| T3 | Từ chối không nhập lý do | 422, câu vẫn `PENDING_REVIEW`. |
| T4 | Hai giáo viên duyệt đồng thời | Một thành công, một 409. |
| T5 | Câu có hai đáp án đúng | Không cho duyệt. |
| T6 | Duyệt hàng loạt gồm một câu đã được xử lý | Câu xung đột bị báo riêng; các câu còn hợp lệ vẫn xử lý. |
| T7 | Câu `REJECTED` | Không xuất hiện trong luồng luyện của người học. |
| T8 | Duyệt thành công | Có bản ghi `review_actions`. |

---

# UC-109 · Sửa nội dung câu hỏi

| | |
| --- | --- |
| **UC-ID** | UC-109 |
| **Actor chính** | `TEACHER`, `CONTENT_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P1 |
| **Scope** | MVP |
| **FT** | 6.5 · 6.6 |
| **Trạng thái triển khai** | READY |

## Mô tả

`TEACHER` hoặc `CONTENT_ADMIN` sửa nội dung câu hỏi khi phát hiện lỗi trong quá trình review, quản lý kho câu hỏi hoặc xử lý phản hồi.

Use Case cho phép sửa đề bài, đáp án, lời giải và nhãn kiến thức trong phạm vi không làm sai nghĩa của lịch sử bài làm đã tồn tại. Nếu một câu đã được người học trả lời, hệ thống không cho sửa đáp án đúng hoặc thay đổi nội dung theo cách làm thay đổi kết quả chấm cũ.

## Kích hoạt

Actor mở một câu hỏi trong màn quản trị hoặc chọn **Sửa** từ hàng đợi review AI.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `TEACHER` hoặc `CONTENT_ADMIN`.
2. Câu hỏi cần sửa tồn tại.
3. Câu hỏi không thuộc một cuộc thi đang diễn ra.

## Hậu điều kiện

### Sửa thành công

- Nội dung được phép sửa được cập nhật.
- Câu hỏi vẫn có ít nhất một nhãn kiến thức.
- Câu trắc nghiệm vẫn có đúng một đáp án đúng.
- Hệ thống ghi người sửa, thời điểm sửa và dữ liệu cũ cần thiết để đối chiếu.
- Cache liên quan được làm mới nếu có.

### Sửa bị từ chối

- Nội dung cũ được giữ nguyên.
- Lịch sử các bài làm cũ không bị thay đổi nghĩa.

### Lưu trữ câu hỏi

- Câu chuyển sang `ARCHIVED`.
- Không xóa cứng dữ liệu câu hỏi hoặc lịch sử bài làm.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `CONTENT_ADMIN` | Mở câu hỏi cần sửa. |
| 2 | System | Kiểm tra quyền và tải nội dung hiện tại của câu hỏi. |
| 3 | `CONTENT_ADMIN` | Thay đổi đề bài, đáp án, lời giải hoặc nhãn kiến thức. |
| 4 | `CONTENT_ADMIN` | Bấm lưu. |
| 5 | System | Kiểm tra cấu trúc câu hỏi, đáp án đúng và nhãn kiến thức. |
| 6 | System | Kiểm tra câu đã từng được sử dụng trong bài làm hay chưa. |
| 7 | System | Nếu câu đã được sử dụng, xác định thay đổi có làm thay đổi cách chấm hay không. |
| 8 | System | Nếu thay đổi được phép, lưu nội dung mới và ghi lịch sử thay đổi. |
| 9 | System | Làm mới cache liên quan nếu có. |
| 10 | Client | Hiển thị nội dung sau khi cập nhật. |

## Luồng thay thế

**A1 — Câu đã có người làm**

- Chỉ cho phép các sửa đổi không làm thay đổi kết quả cũ, ví dụ sửa chính tả, diễn đạt, lời giải hoặc nhãn kiến thức nếu việc đổi nhãn không làm sai dữ liệu lịch sử.
- Nếu actor thay đáp án đúng hoặc sửa nội dung làm thay đổi cách chấm, hệ thống từ chối.
- Khi cần một câu có đáp án/nội dung mới, actor tạo câu hỏi mới và lưu trữ câu cũ.

**A2 — Sửa nhãn kiến thức**

- Hệ thống yêu cầu sau khi sửa câu vẫn còn ít nhất một nhãn.
- Thay đổi được ghi vào lịch sử vì ảnh hưởng đến phân tích điểm yếu và lộ trình học.

**A3 — Lưu trữ câu hỏi**

- Actor chọn lưu trữ thay vì xóa.
- Hệ thống chuyển câu sang `ARCHIVED`.
- Câu không được dùng cho lượt học mới nhưng lịch sử bài làm cũ vẫn giữ nguyên.

**A4 — Sửa từ UC-108**

- Sau khi lưu thành công, hệ thống quay lại ngữ cảnh review.
- Câu vẫn cần một quyết định duyệt/từ chối; thao tác sửa không tự đồng nghĩa với duyệt.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả hoặc sửa dữ liệu. |
| `FORBIDDEN_ROLE` | 403 | Không có role phù hợp | Chặn. |
| `QUESTION_NOT_FOUND` | 404 | Câu không tồn tại | Không thay đổi dữ liệu. |
| `MALFORMED_QUESTION` | 422 | Cấu trúc câu hỏi/đáp án không hợp lệ | Không lưu. |
| `KNOWLEDGE_POINT_REQUIRED` | 422 | Sau khi sửa không còn nhãn kiến thức | Không lưu. |
| `GRADING_CHANGE_NOT_ALLOWED` | 409 | Câu đã có người làm và sửa đổi làm thay đổi cách chấm | Giữ câu cũ; yêu cầu tạo câu mới. |
| `EDIT_DURING_ACTIVE_CONTEST` | 409 | Câu đang thuộc cuộc thi đang diễn ra | Không cho sửa. |

## Business rule

| # | Rule |
| --- | --- |
| BR-109-1 | Chỉ `TEACHER` hoặc `CONTENT_ADMIN` được sửa câu hỏi. |
| BR-109-2 | Mỗi câu hỏi phải luôn có ít nhất một nhãn kiến thức. Không được lưu câu hỏi không gắn với điểm kiến thức nào. |
| BR-109-3 | Câu hỏi trắc nghiệm phải có đúng một đáp án đúng. Không được lưu câu có nhiều đáp án đúng hoặc không có đáp án đúng. |
| BR-109-4 | Câu hỏi đã có người làm không được sửa đáp án đúng hoặc nội dung làm thay đổi cách chấm. Nếu cần sửa, tạo câu hỏi mới và lưu trữ câu cũ. |
| BR-109-5 | Với câu hỏi đã có người làm, chỉ cho sửa lỗi chính tả, lời giải, diễn đạt hoặc nhãn kiến thức nếu không làm sai kết quả cũ. |
| BR-109-6 | Không xóa cứng câu hỏi đã từng được dùng trong bài làm. Nếu không dùng nữa, chuyển trạng thái sang `ARCHIVED`. |
| BR-109-7 | Câu hỏi thuộc cuộc thi đang diễn ra không được sửa. |
| BR-109-8 | Mọi lần sửa phải ghi lịch sử thay đổi, gồm người sửa, thời điểm sửa và nội dung cũ cần thiết để đối chiếu. |
| BR-109-9 | Sau khi sửa câu hỏi, các cache hoặc dữ liệu hiển thị liên quan phải được làm mới để người học không thấy nội dung cũ. |

## API · DB

```text
PUT   /api/admin/questions/{id}
PATCH /api/admin/questions/{id}/archive
```

Dữ liệu liên quan: `questions`, `question_options`, `question_knowledge_points`, `attempt_answers`, `review_actions`; dữ liệu cuộc thi chỉ đọc khi cần kiểm tra câu đang được sử dụng.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Sửa lời giải của câu chưa có người làm | 200, nội dung mới và audit được lưu. |
| T2 | `USER` gọi endpoint sửa | 403. |
| T3 | Sửa thành hai đáp án đúng | 422, không lưu. |
| T4 | Xóa toàn bộ nhãn kiến thức | 422, không lưu. |
| T5 | Sửa đáp án đúng của câu đã có người làm | 409, câu cũ giữ nguyên. |
| T6 | Sửa lỗi chính tả của câu đã có người làm | Cho phép nếu không làm thay đổi cách chấm. |
| T7 | Lưu trữ câu đã có lịch sử làm bài | Câu chuyển `ARCHIVED`; lịch sử còn nguyên. |
| T8 | Sửa câu đang dùng trong cuộc thi đang diễn ra | 409. |

---

# UC-110 · Nhập dữ liệu đề thi, từ vựng và ngữ pháp từ file

| | |
| --- | --- |
| **UC-ID** | UC-110 |
| **Actor chính** | `CONTENT_ADMIN` |
| **Loại** | User Goal |
| **Pri** | **P0** |
| **Scope** | MVP |
| **FT** | 6.2 |
| **Trạng thái triển khai** | **BLOCKED — cần file mẫu thật, định dạng import và khóa tự nhiên cho từng loại dữ liệu** |

## Mô tả

`CONTENT_ADMIN` nhập dữ liệu do giáo viên cung cấp vào hệ thống, gồm đề thi/câu hỏi, từ vựng và ngữ pháp.

Use Case phải bảo đảm ba yêu cầu cốt lõi:

1. Dữ liệu được kiểm tra trước khi ghi vào kho chính.
2. Dòng lỗi được báo rõ nhưng không làm mất các dòng hợp lệ khác.
3. Chạy lại cùng dữ liệu không tạo bản trùng.

Đây là Use Case P0 vì dữ liệu nhập là nguồn cho luyện thi, kho câu hỏi, từ vựng, ngữ pháp và các tính năng phân tích điểm yếu. Tuy nhiên không được triển khai parser chính thức trước khi có ít nhất một file mẫu thật và thống nhất định dạng.

## Kích hoạt

`CONTENT_ADMIN` mở chức năng nhập dữ liệu, chọn loại dữ liệu và chọn file cần nhập.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `CONTENT_ADMIN`.
2. Với thao tác upload từ web dùng cookie, request có CSRF token hợp lệ.
3. Nhóm đã phê duyệt định dạng import cho loại dữ liệu đang nhập.
4. Nhóm đã xác định khóa tự nhiên dùng để nhận diện bản ghi trùng của loại dữ liệu đó.
5. Các `knowledge_points` cần dùng để gắn nhãn đã tồn tại nếu loại dữ liệu yêu cầu nhãn kiến thức.

> File “đúng định dạng” không phải tiền điều kiện do người dùng tự bảo đảm; hệ thống phải kiểm tra trong luồng.

## Hậu điều kiện

### File hợp lệ và toàn bộ dòng hợp lệ

- Tất cả dòng được import hoặc cập nhật theo khóa đã chốt.
- Không tạo bản trùng.
- Lần import được ghi nhận với người import, file hash, thời điểm và số dòng thành công.

### File có cả dòng hợp lệ và dòng lỗi

- Dòng hợp lệ được nhập.
- Dòng lỗi không được ghi vào kho chính.
- Mỗi lỗi được lưu để UC-111 hiển thị.
- Lần import ghi đúng số dòng thành công và số dòng lỗi.

### File sai cấu trúc hoàn toàn

- Không có dữ liệu học nào từ file được ghi vào kho chính.
- Lần import ghi nhận thất bại và lý do.

### Lỗi hệ thống trước khi hoàn tất một dòng

- Không để lại bản ghi nội dung của dòng đó ở trạng thái dở dang.
- Kết quả import phải phản ánh đúng số dòng đã xử lý thành công.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `CONTENT_ADMIN` | Chọn loại dữ liệu cần nhập và chọn file. |
| 2 | Client | Upload file và thông tin loại dữ liệu. |
| 3 | System | Kiểm tra quyền, CSRF, kích thước file và loại file được phép. |
| 4 | System | Tạo bản ghi lần import, lưu file hash và người thực hiện. |
| 5 | System | Kiểm tra file có phải lần import lặp lại của dữ liệu đã xử lý trước đó hay không. |
| 6 | System | Kiểm tra encoding và cấu trúc tổng thể của file. |
| 7 | System | Đọc từng dòng theo đúng template đã phê duyệt. |
| 8 | System | Với từng dòng, kiểm tra trường bắt buộc, kiểu dữ liệu, nhãn kiến thức và cấu trúc đáp án nếu là câu hỏi. |
| 9 | System | Dòng không hợp lệ được ghi vào danh sách lỗi và không được ghi vào kho chính. |
| 10 | System | Với dòng hợp lệ, xác định bản ghi theo khóa tự nhiên và thực hiện tạo mới hoặc cập nhật theo quy tắc import đã chốt. |
| 11 | System | Sau khi xử lý hết file, cập nhật tổng số dòng thành công, lỗi và trạng thái lần import. |
| 12 | System | Trả `import_run_id` cùng thống kê tổng quát. |
| 13 | `CONTENT_ADMIN` | Mở UC-111 để xem chi tiết dòng lỗi nếu có. |

## Luồng thay thế

**A1 — Chạy lại cùng file**

- Hệ thống nhận biết file hash đã từng được import.
- `CONTENT_ADMIN` được thông báo đây là file đã xử lý trước đó.
- Nếu tiếp tục import, hệ thống dùng khóa tự nhiên để cập nhật/giữ dữ liệu đúng quy tắc, không tạo bản sao trùng.

**A2 — File có một phần dữ liệu lỗi**

- Hệ thống không rollback toàn bộ file chỉ vì một dòng sai.
- Dòng hợp lệ vẫn được lưu.
- Dòng lỗi được giữ trong báo cáo lỗi của lần import.

**A3 — File sai template hoặc encoding không đọc được**

- Hệ thống kết thúc lần import ở trạng thái thất bại.
- Không ghi các dòng nội dung vào kho chính.
- Trả thông báo đủ rõ để người quản trị biết cần sửa file gì.

**A4 — Dòng thiếu nhãn kiến thức**

- Chỉ dòng đó bị từ chối.
- Báo cáo lỗi chỉ rõ vị trí và nhãn không tìm thấy.
- Không nhập câu rồi chờ gắn nhãn sau.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không nhận file. |
| `FORBIDDEN_ROLE` | 403 | Không có `CONTENT_ADMIN` | Không nhận file. |
| `CSRF_TOKEN_MISSING` | 403 | Request upload qua cookie thiếu CSRF | Không nhận file. |
| `UNSUPPORTED_FILE_FORMAT` | 422 | File không đúng template/định dạng đã duyệt | Không ghi dữ liệu học. |
| `ENCODING_ERROR` | 422 | Nội dung chữ Hán bị hỏng hoặc encoding không hỗ trợ | Không ghi dữ liệu hỏng. |
| `FILE_TOO_LARGE` | 413 | Vượt giới hạn file | Từ chối file. |
| `CONCURRENT_IMPORT` | 409 | Cùng loại/file đang được xử lý theo chính sách chống trùng | Không tạo lần import cạnh tranh. |
| `NATURAL_KEY_NOT_CONFIGURED` | Không phải runtime production | Chưa chốt khóa nhận diện trùng | **Không triển khai import loại dữ liệu đó** cho đến khi chốt. |

## Business rule

| # | Rule |
| --- | --- |
| BR-110-1 | UC này là P0 nhưng bị chặn cho đến khi nhóm có ít nhất một file mẫu thật từ thầy và thống nhất định dạng import. |
| BR-110-2 | Chỉ `CONTENT_ADMIN` được nhập dữ liệu đề thi, từ vựng, ngữ pháp hoặc câu hỏi từ file. |
| BR-110-3 | File import phải đúng định dạng đã chốt. File sai định dạng hoàn toàn thì không nhập dòng nào. |
| BR-110-4 | Mỗi dòng dữ liệu phải được kiểm tra trước khi ghi vào kho chính. Dòng lỗi không được ghi vào database. |
| BR-110-5 | Dòng hợp lệ vẫn được nhập dù cùng file có dòng lỗi. Một dòng lỗi không được làm rollback toàn bộ file. |
| BR-110-6 | Câu hỏi import bắt buộc phải có nhãn kiến thức. Dòng thiếu nhãn kiến thức bị từ chối. |
| BR-110-7 | Câu hỏi trắc nghiệm import phải có đúng một đáp án đúng. Dòng sai cấu trúc bị từ chối. |
| BR-110-8 | Import phải chạy lại an toàn. Chạy lại cùng file hoặc cùng dữ liệu không được tạo bản trùng. |
| BR-110-9 | Trước khi làm import, nhóm phải chốt khóa tự nhiên cho từng loại dữ liệu, ví dụ câu hỏi, từ vựng, ngữ pháp, để upsert đúng. |
| BR-110-10 | Hệ thống phải phát hiện lỗi encoding làm chữ Hán bị hỏng. Không được ghi dữ liệu dạng `???` hoặc ký tự rác vào kho chính. |
| BR-110-11 | Mỗi lần import phải ghi lịch sử import, gồm người import, thời điểm, loại dữ liệu, file hash, số dòng thành công và số dòng lỗi. |
| BR-110-12 | Không đưa file dữ liệu thật của thầy vào git hoặc test resource của dự án. Dữ liệu test phải là dữ liệu tự tạo. |

## API · DB

```text
POST /api/admin/imports
GET  /api/admin/imports/{id}
```

Dữ liệu liên quan: `import_runs`, `exams`, `exam_sections`, `questions`, `question_options`, `question_knowledge_points`, `words`, `grammar_points`, `knowledge_points`.

> **Blocker thiết kế:** chưa chốt nơi lưu chi tiết lỗi import cho UC-111. Không tự đặt tên bảng trong code trước khi thiết kế DB được duyệt.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | File 100 dòng hợp lệ | 100 dòng được import; thống kê đúng. |
| T2 | File 100 dòng có 10 dòng sai | 90 dòng hợp lệ được lưu, 10 lỗi được báo; không rollback toàn file. |
| T3 | Chạy lại cùng file | Không tạo bản trùng. |
| T4 | Câu hỏi thiếu nhãn kiến thức | Dòng bị từ chối, báo rõ dòng/cột. |
| T5 | File làm chữ Hán thành `???` | Từ chối trước khi ghi dữ liệu rác. |
| T6 | `FINANCE_ADMIN` gọi import | 403. |
| T7 | File sai template hoàn toàn | 0 dòng nội dung được ghi. |
| T8 | Câu trắc nghiệm có hai đáp án đúng | Dòng bị từ chối. |
| T9 | Kiểm repository | Không có file dữ liệu thật của giáo viên trong git/test resource. |

---

# UC-111 · Xem báo cáo lỗi sau khi nhập

| | |
| --- | --- |
| **UC-ID** | UC-111 |
| **Actor chính** | `CONTENT_ADMIN` |
| **Loại** | User Goal |
| **Pri** | **P0** |
| **Scope** | MVP |
| **FT** | 6.2 |
| **Trạng thái triển khai** | **PARTIALLY BLOCKED — cần cơ chế lưu bền vững chi tiết lỗi import** |

## Mô tả

`CONTENT_ADMIN` xem kết quả của một lần import và xác định chính xác dòng nào bị lỗi, lỗi ở trường nào và cần sửa như thế nào.

Mục tiêu của Use Case là giúp người quản trị có thể sửa file và nhập lại mà không phải dò thủ công toàn bộ dữ liệu. Vì vậy lỗi phải được lưu lại sau lần import, không chỉ tồn tại trong response của UC-110.

## Kích hoạt

`CONTENT_ADMIN` mở kết quả của một `import_run` sau khi UC-110 hoàn tất hoặc mở lại lịch sử import trước đó.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `CONTENT_ADMIN`.
2. Lần import cần xem tồn tại.
3. Hệ thống đã lưu kết quả tổng hợp của lần import.
4. Để xem lại chi tiết lỗi sau khi đóng tab, hệ thống phải có nơi lưu bền vững từng lỗi import.

## Hậu điều kiện

### Thành công

- Người quản trị xem được thống kê tổng số dòng, số dòng thành công và số dòng lỗi.
- Với mỗi lỗi, hiển thị vị trí dòng, trường/cột liên quan, mã lỗi và thông báo dễ hiểu.
- Không thay đổi dữ liệu đã import.

### Không thành công

- Không sửa hoặc xóa kết quả import.
- Không trả stack trace hoặc thông tin kỹ thuật nội bộ cho client.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `CONTENT_ADMIN` | Mở chi tiết một lần import. |
| 2 | System | Kiểm tra quyền và tìm `import_run` tương ứng. |
| 3 | System | Trả thông tin tổng hợp: loại dữ liệu, thời điểm, tổng dòng, số thành công, số lỗi và trạng thái. |
| 4 | System | Lấy danh sách lỗi của lần import theo phân trang. |
| 5 | System | Với mỗi lỗi, trả số dòng, tên cột/trường, giá trị gây lỗi ở mức cần thiết, mã lỗi và thông báo tiếng Việt rõ ràng. |
| 6 | Client | Hiển thị danh sách lỗi để người quản trị đối chiếu với file nguồn. |
| 7 | `CONTENT_ADMIN` | Sửa file và quay lại UC-110 khi cần nhập lại. |

## Luồng thay thế

**A1 — Import không có lỗi**

- Hiển thị “Nhập thành công” cùng số dòng thành công.
- Danh sách lỗi rỗng.

**A2 — Có rất nhiều lỗi**

- Hệ thống phân trang.
- Tổng số lỗi vẫn phải hiển thị đầy đủ.

**A3 — Xuất danh sách lỗi**

- Chỉ `CONTENT_ADMIN` được xuất.
- File xuất chỉ phục vụ sửa dữ liệu import.
- Không đưa stack trace hoặc dữ liệu nội bộ không cần thiết vào file.

**A4 — Xem lịch sử import**

- Hiển thị danh sách các lần import trước đó theo người thực hiện/thời gian ở mức hệ thống đã hỗ trợ.
- Mở một lần import sẽ quay về luồng chính của UC này.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả dữ liệu import. |
| `FORBIDDEN_ROLE` | 403 | Không có `CONTENT_ADMIN` | Chặn. |
| `IMPORT_RUN_NOT_FOUND` | 404 | Không tồn tại lần import | Không trả dữ liệu. |
| `INVALID_PAGINATION` | 422 | Tham số phân trang không hợp lệ | Trả lỗi validation. |
| `IMPORT_ERROR_STORAGE_NOT_READY` | Không phải runtime production | Chưa có nơi lưu chi tiết lỗi | **Không coi UC-111 là code-ready** cho đến khi thiết kế được chốt. |

## Business rule

| # | Rule |
| --- | --- |
| BR-111-1 | Chỉ `CONTENT_ADMIN` được xem báo cáo lỗi import. |
| BR-111-2 | Báo cáo lỗi phải gắn với một lần import cụ thể. |
| BR-111-3 | Mỗi lỗi phải có số dòng, tên cột hoặc vùng dữ liệu, giá trị gây lỗi nếu cần, mã lỗi và thông báo tiếng Việt dễ hiểu. |
| BR-111-4 | Danh sách lỗi phải được lưu lại để `CONTENT_ADMIN` có thể mở lại sau khi đóng tab. Không chỉ trả lỗi một lần trong response import. |
| BR-111-5 | Nếu có nhiều lỗi, danh sách lỗi phải phân trang. |
| BR-111-6 | Response lỗi không được trả stack trace hoặc lỗi kỹ thuật nội bộ. |
| BR-111-7 | Dữ liệu lỗi import không được xuất công khai ra ngoài hệ thống. Nếu có chức năng export lỗi, chỉ `CONTENT_ADMIN` được tải và chỉ phục vụ sửa file import. |
| BR-111-8 | Nếu import không có lỗi, hệ thống hiển thị trạng thái nhập thành công và số dòng đã nhập. |

## API · DB

```text
GET /api/admin/imports
GET /api/admin/imports/{id}
GET /api/admin/imports/{id}/errors
GET /api/admin/imports/{id}/errors/export
```

Dữ liệu liên quan: `import_runs` và **nơi lưu chi tiết lỗi import chưa được chốt tên/thiết kế**.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Lần import có 10 lỗi | Hiển thị đủ 10 lỗi; mỗi lỗi có `line_number`. |
| T2 | Đóng tab rồi mở lại | Danh sách lỗi vẫn còn. |
| T3 | `FINANCE_ADMIN` gọi | 403. |
| T4 | Lỗi thiếu nhãn kiến thức | Thông báo nêu rõ dòng và nhãn không tìm thấy. |
| T5 | Có 5.000 lỗi | Danh sách phân trang và hiển thị đúng tổng số lỗi. |
| T6 | Kiểm response lỗi | Không có stack trace. |
| T7 | Import không lỗi | Hiển thị trạng thái thành công và danh sách lỗi rỗng. |

---

# UC-112 · Quản lý đề thi và kho câu hỏi

| | |
| --- | --- |
| **UC-ID** | UC-112 |
| **Actor chính** | `CONTENT_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P1 |
| **Scope** | MVP |
| **FT** | 6.3 · 6.5 |
| **Trạng thái triển khai** | READY |

## Mô tả

`CONTENT_ADMIN` tạo và quản lý đề thi, tổ chức các phần của đề, gán câu hỏi từ kho câu hỏi và công bố đề cho người học.

Use Case này không thay thế UC-109: khi cần sửa nội dung một câu hỏi, actor sử dụng UC-109. UC-112 tập trung vào **cấu trúc đề và trạng thái công bố**.

Quan hệ tiên quyết của chủ đề được quản lý như một thao tác quản trị nội dung liên quan đến lộ trình, không phải bước bắt buộc của việc tạo đề. Phần này được mô tả ở luồng thay thế để tránh trộn hai mục tiêu trong luồng chính.

## Kích hoạt

`CONTENT_ADMIN` mở màn hình quản lý đề thi và chọn tạo mới hoặc chỉnh sửa một đề.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `CONTENT_ADMIN`.
2. Request ghi từ web dùng cookie có CSRF token hợp lệ.
3. Các câu hỏi được gán vào đề đã tồn tại trong kho câu hỏi.

## Hậu điều kiện

### Lưu nháp thành công

- Đề và cấu trúc phần được lưu ở trạng thái `DRAFT`.
- Đề không hiển thị cho người học.

### Công bố thành công

- Đề chuyển sang `PUBLISHED`.
- Cấu trúc đề hợp lệ.
- Mọi câu trong đề hợp lệ, đã được duyệt và có ít nhất một nhãn kiến thức.
- Audio bắt buộc của phần nghe đã sẵn sàng.
- Đề xuất hiện trong danh sách luyện thi.

### Lưu trữ đề

- Đề chuyển sang `ARCHIVED`.
- Người học không thể bắt đầu lượt mới.
- Lịch sử và bài đang làm đã được tạo trước đó không bị xóa.

### Thao tác thất bại

- Giữ trạng thái trước đó của đề.
- Không để đề chuyển `PUBLISHED` khi còn lỗi bắt buộc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `CONTENT_ADMIN` | Tạo đề mới hoặc mở một đề `DRAFT`. |
| 2 | System | Kiểm tra quyền và tải dữ liệu đề hiện tại. |
| 3 | `CONTENT_ADMIN` | Nhập/sửa thông tin đề, tạo các phần và xác định thứ tự phần. |
| 4 | `CONTENT_ADMIN` | Gán các câu hỏi phù hợp từ kho vào từng phần. |
| 5 | System | Kiểm tra dữ liệu cơ bản và lưu đề ở trạng thái `DRAFT`. |
| 6 | `CONTENT_ADMIN` | Kiểm tra đề và chọn **Công bố**. |
| 7 | System | Kiểm tra đề có ít nhất một phần và mỗi phần có ít nhất một câu. |
| 8 | System | Kiểm tra mọi câu đều được phép sử dụng, có nhãn kiến thức và có cấu trúc hợp lệ. |
| 9 | System | Kiểm tra audio bắt buộc của phần nghe. |
| 10 | System | Nếu tất cả điều kiện đạt, chuyển đề sang `PUBLISHED` và ghi audit. |
| 11 | Client | Hiển thị trạng thái đã công bố. |

## Luồng thay thế

**A1 — Lưu nháp**

- Actor lưu khi chưa muốn công bố.
- Hệ thống giữ `DRAFT`.
- Các kiểm tra bắt buộc để công bố chưa cần phải đạt toàn bộ, nhưng dữ liệu lưu không được sai kiểu/cấu trúc cơ bản.

**A2 — Lưu trữ đề đã công bố**

- `CONTENT_ADMIN` chọn lưu trữ.
- Đề chuyển sang `ARCHIVED`.
- Không cho bắt đầu attempt mới.
- Attempt đã tạo trước đó được giữ để bảo toàn lịch sử; attempt đang làm tiếp tục theo chính sách luyện thi đã chốt.

**A3 — Sao chép đề**

- Hệ thống tạo một đề mới ở trạng thái `DRAFT` với cấu trúc được sao chép.
- Bản sao không tự động `PUBLISHED`.

**A4 — Quản lý quan hệ tiên quyết chủ đề**

- Actor chỉnh quan hệ tiên quyết trong phần quản trị nội dung.
- Trước khi lưu, hệ thống kiểm tra không tạo chu trình và toàn bộ cây vẫn còn ít nhất một chủ đề gốc.
- Thao tác này không làm thay đổi trạng thái của một đề thi.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không cho quản lý đề. |
| `FORBIDDEN_ROLE` | 403 | Không có `CONTENT_ADMIN` | Chặn. |
| `PUBLISH_EMPTY_EXAM` | 422 | Đề/phần không có câu hỏi | Giữ `DRAFT`. |
| `PUBLISH_WITHOUT_AUDIO` | 422 | Phần nghe thiếu audio bắt buộc | Giữ `DRAFT`. |
| `PUBLISH_UNAPPROVED_QUESTION` | 422 | Có câu chưa được duyệt | Giữ `DRAFT`. |
| `PUBLISH_UNLABELED_QUESTION` | 422 | Có câu không có nhãn kiến thức | Giữ `DRAFT`. |
| `SECTION_ORDER_MISSING` | 422 | Thiếu thứ tự phần | Không công bố. |
| `CIRCULAR_PREREQUISITE` | 422 | Quan hệ tiên quyết tạo vòng lặp | Không lưu quan hệ. |
| `NO_ROOT_TOPIC` | 422 | Không còn chủ đề gốc | Không lưu quan hệ. |

## Business rule

| # | Rule |
| --- | --- |
| BR-112-1 | Chỉ `CONTENT_ADMIN` được tạo, sửa, công bố hoặc lưu trữ đề thi. |
| BR-112-2 | Đề ở trạng thái `DRAFT` không được hiển thị cho người học. |
| BR-112-3 | Chỉ đề `PUBLISHED` mới xuất hiện trong danh sách luyện thi của người học. |
| BR-112-4 | Trước khi công bố, đề phải có ít nhất một phần, mỗi phần có ít nhất một câu hỏi, và mọi câu hỏi phải hợp lệ. |
| BR-112-5 | Nếu đề có phần nghe, các câu cần audio bắt buộc phải có audio trước khi công bố. |
| BR-112-6 | Mọi câu hỏi trong đề công bố phải có ít nhất một nhãn kiến thức. Đây là chốt cuối để tránh phân tích điểm yếu bị rỗng. |
| BR-112-7 | Rút đề đã công bố thì chuyển sang `ARCHIVED`, không xóa cứng. |
| BR-112-8 | Bài làm cũ của người học phải được giữ lại khi đề bị lưu trữ. |
| BR-112-9 | Quan hệ tiên quyết giữa các chủ đề không được tạo vòng lặp. |
| BR-112-10 | Cây chủ đề phải có ít nhất một chủ đề gốc, tức là chủ đề không có tiên quyết. |
| BR-112-11 | Mọi thay đổi quan trọng với đề, câu hỏi hoặc chủ đề phải có audit log. |

## API · DB

```text
GET   /api/admin/exams
POST  /api/admin/exams
PUT   /api/admin/exams/{id}
PATCH /api/admin/exams/{id}/publish
PATCH /api/admin/exams/{id}/archive
PUT   /api/admin/topics/{id}/prerequisites
```

Dữ liệu liên quan: `exams`, `exam_sections`, `questions`, `question_knowledge_points`, `topics`, audit log.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tạo đề, gán câu hợp lệ, công bố | `PUBLISHED`, xuất hiện cho người học. |
| T2 | Công bố đề không có câu | 422, vẫn `DRAFT`. |
| T3 | Công bố đề có câu thiếu nhãn | 422. |
| T4 | Công bố đề có câu chưa `APPROVED` | 422. |
| T5 | Phần nghe thiếu audio bắt buộc | 422. |
| T6 | Tạo quan hệ A cần B, B cần A | 422 `CIRCULAR_PREREQUISITE`. |
| T7 | Lưu cấu trúc làm cây không còn chủ đề gốc | 422. |
| T8 | Lưu trữ đề có lịch sử attempt | Đề `ARCHIVED`; lịch sử vẫn còn. |
| T9 | `FINANCE_ADMIN` gọi | 403. |

---

# UC-113 · Tạo và quản lý cuộc thi

| | |
| --- | --- |
| **UC-ID** | UC-113 |
| **Actor chính** | `CONTENT_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P2 |
| **Scope** | V2 |
| **FT** | 5.8 |
| **Trạng thái triển khai** | V2 — không gen MVP |

## Mô tả

`CONTENT_ADMIN` tạo và quản lý cuộc thi học tập trong phạm vi V2: cấu hình tên, thời gian, đề thi, thông tin phần thưởng, công bố cuộc thi, hủy cuộc thi và công bố bảng xếp hạng sau khi cuộc thi kết thúc.

Use Case này chỉ quản lý nội dung và trạng thái cuộc thi. Nếu phần thưởng là điểm có giá trị tài chính, việc cộng điểm không được thực hiện trực tiếp bởi `CONTENT_ADMIN`.

## Kích hoạt

`CONTENT_ADMIN` mở màn hình quản lý cuộc thi và chọn tạo mới hoặc chỉnh sửa một cuộc thi.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `CONTENT_ADMIN`.
2. Request ghi từ web có CSRF hợp lệ.
3. Đề được chọn cho cuộc thi tồn tại và đủ điều kiện sử dụng.
4. Chính sách phần thưởng của cuộc thi đã được xác định.

## Hậu điều kiện

### Tạo/lưu nháp

- Cuộc thi được lưu ở `DRAFT`.
- Chưa hiển thị cho người học.

### Công bố

- Cuộc thi được hiển thị cho người học theo phạm vi V2.
- Thời gian, đề và phần thưởng công bố trở thành dữ liệu cam kết cho cuộc thi.

### Hủy

- Cuộc thi chuyển sang `CANCELLED`.
- Người đã đăng ký được thông báo theo cơ chế V2.

### Công bố xếp hạng

- Chỉ thực hiện sau khi cuộc thi kết thúc.
- Không tự động phát sinh giao dịch thưởng tài chính từ UC này.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `CONTENT_ADMIN` | Tạo cuộc thi mới. |
| 2 | `CONTENT_ADMIN` | Nhập tên, thời gian bắt đầu/kết thúc, chọn đề và nhập thông tin phần thưởng. |
| 3 | System | Kiểm tra quyền, định dạng thời gian và cấu trúc dữ liệu. |
| 4 | System | Kiểm tra thời gian bắt đầu nhỏ hơn thời gian kết thúc và đề hợp lệ. |
| 5 | System | Lưu cuộc thi ở `DRAFT`. |
| 6 | `CONTENT_ADMIN` | Kiểm tra lại và chọn **Công bố**. |
| 7 | System | Kiểm tra lại các điều kiện bắt buộc và chuyển cuộc thi sang trạng thái công khai. |
| 8 | System | Ghi audit cho thao tác công bố. |
| 9 | Client | Hiển thị trạng thái cuộc thi đã công bố. |

## Luồng thay thế

**A1 — Sửa trước khi cuộc thi bắt đầu**

- Cho phép sửa các thông tin được phép.
- Nếu cuộc thi đã công bố và thay đổi thông tin quan trọng, người đã đăng ký phải được thông báo.

**A2 — Hủy cuộc thi**

- Chuyển sang `CANCELLED`.
- Không xóa cứng dữ liệu cuộc thi hoặc dữ liệu người đã đăng ký.

**A3 — Công bố bảng xếp hạng**

- Chỉ thực hiện sau thời điểm kết thúc.
- Kết quả được lấy từ dữ liệu hợp lệ đã chấm ở server.

**A4 — Phần thưởng là điểm**

- `CONTENT_ADMIN` chỉ xác định/công bố người thắng theo chức năng cuộc thi.
- Việc thay đổi số dư điểm phải đi qua luồng tài chính được phân quyền cho `FINANCE_ADMIN`.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không cho quản lý cuộc thi. |
| `FORBIDDEN_ROLE` | 403 | Không có `CONTENT_ADMIN` | Chặn. |
| `INVALID_TIME_RANGE` | 422 | Thời gian bắt đầu không trước thời gian kết thúc | Không lưu/công bố. |
| `START_IN_PAST` | 422 | Tạo cuộc thi mới nhưng thời gian bắt đầu đã qua | Không công bố. |
| `EDIT_DURING_CONTEST` | 409 | Cố sửa thời gian/đề/phần thưởng khi đang diễn ra | Chặn. |
| `CONTEST_WITHOUT_VALID_EXAM` | 422 | Không có đề hợp lệ | Không công bố. |
| `RANKING_PUBLISHED_EARLY` | 409 | Công bố xếp hạng trước khi kết thúc | Chặn. |

## Business rule

| # | Rule |
| --- | --- |
| BR-113-1 | UC này thuộc V2. Không gen code cho MVP. |
| BR-113-2 | Chỉ `CONTENT_ADMIN` được tạo, sửa, công bố hoặc hủy cuộc thi. |
| BR-113-3 | Cuộc thi phải có khung giờ hợp lệ: thời gian bắt đầu nhỏ hơn thời gian kết thúc. |
| BR-113-4 | Khung giờ cuộc thi phải được nhập và hiển thị rõ theo giờ Việt Nam. |
| BR-113-5 | Sau khi cuộc thi đang diễn ra, không được sửa khung giờ, đề thi hoặc phần thưởng. |
| BR-113-6 | Cuộc thi công bố phải có đề thi hợp lệ. |
| BR-113-7 | Xếp hạng cuộc thi chỉ được công bố sau khi cuộc thi kết thúc. |
| BR-113-8 | Nếu phần thưởng là điểm hoặc giá trị tài chính, việc cộng thưởng phải do `FINANCE_ADMIN` xử lý qua luồng tài chính riêng, không do `CONTENT_ADMIN` tự cộng. |
| BR-113-9 | Hủy cuộc thi thì chuyển trạng thái sang `CANCELLED` và thông báo cho người đã đăng ký. |

## API · DB

```text
POST  /api/admin/contests
PUT   /api/admin/contests/{id}
PATCH /api/admin/contests/{id}/publish
PATCH /api/admin/contests/{id}/cancel
POST  /api/admin/contests/{id}/publish-ranking
```

Dữ liệu liên quan: `contests`, `contest_participants`, `contest_submissions`, audit log.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tạo cuộc thi có thời gian hợp lệ | Lưu `DRAFT`. |
| T2 | `starts_at >= ends_at` | 422. |
| T3 | Công bố cuộc thi không có đề hợp lệ | 422. |
| T4 | Sửa đề hoặc phần thưởng khi cuộc thi đang diễn ra | 409. |
| T5 | Công bố xếp hạng trước khi kết thúc | 409. |
| T6 | `CONTENT_ADMIN` cố cộng điểm thưởng trực tiếp | Không được phép qua UC này. |
| T7 | Hủy cuộc thi trước khi kết thúc | `CANCELLED`, dữ liệu lịch sử được giữ. |

---

# UC-114 · Quản lý người dùng

| | |
| --- | --- |
| **UC-ID** | UC-114 |
| **Actor chính** | `SUPER_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P1 |
| **Scope** | MVP |
| **FT** | 6.1 |
| **Trạng thái triển khai** | **BLOCKED — cần chốt trường trạng thái khóa/ban của tài khoản trước khi code** |

## Mô tả

`SUPER_ADMIN` tìm tài khoản người dùng, xem thông tin quản trị cần thiết, khóa tài khoản khi có lý do hợp lệ và mở khóa tài khoản đã bị khóa.

Use Case không hỗ trợ xóa cứng người dùng. Khóa tài khoản phải làm mất hiệu lực truy cập của tài khoản, nhưng không xóa lịch sử học, bài làm, bài viết, giao dịch hoặc dữ liệu liên quan.

Use Case này không bao gồm xử lý treo tài khoản do moderation của `MANAGER`; phần đó thuộc luồng moderation tương ứng.

## Kích hoạt

`SUPER_ADMIN` mở màn hình quản lý người dùng hoặc tìm một tài khoản cần kiểm tra.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có role `SUPER_ADMIN`.
2. Với thao tác khóa/mở khóa từ web, request có CSRF token hợp lệ.
3. Trước khi triển khai chức năng khóa, dự án đã chốt cách lưu trạng thái khóa/ban trên tài khoản.

## Hậu điều kiện

### Chỉ xem thông tin

- Không thay đổi dữ liệu tài khoản.
- Việc xem chi tiết tài khoản được ghi audit.

### Khóa thành công

- Tài khoản chuyển sang trạng thái bị khóa/ban theo thiết kế đã chốt.
- Lưu lý do, người thực hiện và thời điểm khóa.
- Tất cả refresh token hiện tại của tài khoản bị thu hồi.
- Tài khoản không tiếp tục sử dụng quyền truy cập cũ.

### Mở khóa thành công

- Trạng thái khóa được gỡ bỏ.
- Phiên/token cũ không được tự khôi phục.
- Người dùng phải đăng nhập lại.

### Thất bại

- Không để trạng thái khóa thay đổi nhưng token vẫn giữ theo cách mâu thuẫn với kết quả trả cho quản trị.
- Không xóa dữ liệu người dùng.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `SUPER_ADMIN` | Nhập email hoặc tên để tìm người dùng. |
| 2 | System | Kiểm tra quyền và tìm các tài khoản phù hợp. |
| 3 | System | Trả danh sách tối thiểu cần thiết; email được che một phần ở danh sách. |
| 4 | `SUPER_ADMIN` | Mở chi tiết một tài khoản. |
| 5 | System | Kiểm tra quyền, trả dữ liệu quản trị được phép xem và ghi audit truy cập. |
| 6 | `SUPER_ADMIN` | Chọn **Khóa tài khoản** và nhập lý do. |
| 7 | System | Kiểm tra không phải tài khoản của chính người thao tác và không phải `SUPER_ADMIN` cuối cùng đang hoạt động. |
| 8 | System | Cập nhật trạng thái khóa, người khóa, lý do và thời điểm. |
| 9 | System | Thu hồi toàn bộ refresh token của tài khoản. |
| 10 | System | Ghi audit cho thao tác khóa. |
| 11 | Client | Hiển thị trạng thái tài khoản đã bị khóa. |

## Luồng thay thế

**A1 — Mở khóa**

1. `SUPER_ADMIN` mở tài khoản đang bị khóa.
2. Chọn **Mở khóa**.
3. Hệ thống gỡ trạng thái khóa và ghi audit.
4. Không tạo lại các phiên đăng nhập cũ.
5. Người dùng đăng nhập lại nếu muốn tiếp tục sử dụng hệ thống.

**A2 — Tìm kiếm không có kết quả**

- Trả danh sách rỗng.
- Không coi đây là lỗi hệ thống.

**A3 — Xem thông tin nhưng không khóa**

- Kết thúc sau bước 5.
- Chỉ audit việc xem chi tiết; không thay đổi trạng thái tài khoản.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả dữ liệu quản trị. |
| `FORBIDDEN_ROLE` | 403 | Không có `SUPER_ADMIN` | Chặn. |
| `USER_NOT_FOUND` | 404 | Mở chi tiết ID không tồn tại | Không trả dữ liệu. |
| `SELF_BAN` | 409 | `SUPER_ADMIN` cố khóa chính mình | Chặn. |
| `BAN_LAST_SUPER_ADMIN` | 409 | Cố khóa `SUPER_ADMIN` cuối cùng đang hoạt động | Chặn. |
| `EMPTY_BAN_REASON` | 422 | Không nhập lý do | Không khóa. |
| `ACCOUNT_STATUS_NOT_DESIGNED` | Không phải runtime production | Chưa chốt trường trạng thái tài khoản | **Không triển khai chức năng khóa** cho đến khi chốt. |

## Business rule

| # | Rule |
| --- | --- |
| BR-114-1 | UC này bị chặn cho đến khi nhóm chốt các trường trạng thái tài khoản như `banned_at`, `suspended_at` hoặc `locked_until`. |
| BR-114-2 | Chỉ `SUPER_ADMIN` được tìm, xem chi tiết, khóa hoặc mở khóa tài khoản người dùng. |
| BR-114-3 | Danh sách người dùng chỉ hiển thị thông tin cần thiết. Email nên được che một phần ở danh sách. |
| BR-114-4 | Khi xem chi tiết người dùng, hệ thống phải ghi audit log người quản trị nào đã xem tài khoản nào. |
| BR-114-5 | Không bao giờ trả `password_hash`, token hoặc thông tin bảo mật nội bộ trong response quản lý người dùng. |
| BR-114-6 | Không hard delete tài khoản người dùng. Nếu cần chặn người dùng, dùng trạng thái khóa hoặc ban. |
| BR-114-7 | Khi khóa tài khoản, lý do khóa là bắt buộc và hệ thống phải ghi người khóa, thời điểm khóa. |
| BR-114-8 | Khi khóa tài khoản, hệ thống phải thu hồi toàn bộ refresh token của tài khoản đó. |
| BR-114-9 | Người bị khóa không được tiếp tục dùng API bằng token cũ. |
| BR-114-10 | `SUPER_ADMIN` không được tự khóa chính mình. |
| BR-114-11 | Không được khóa hoặc thu hồi quyền của `SUPER_ADMIN` cuối cùng đang hoạt động. |
| BR-114-12 | Mở khóa tài khoản không tự khôi phục phiên cũ. Người dùng phải đăng nhập lại. |

## API · DB

```text
GET   /api/admin/users?q={x}
GET   /api/admin/users/{id}
PATCH /api/admin/users/{id}/ban
PATCH /api/admin/users/{id}/unban
```

Dữ liệu liên quan: `users`, `auth_tokens`, audit log.

> **Blocker:** tên/cấu trúc trường trạng thái tài khoản phải được chốt ở thiết kế DB trước khi gen code. UC này không tự thêm `banned_at`, `suspended_at` hoặc `locked_until`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `SUPER_ADMIN` tìm theo email/tên | Danh sách đúng quyền; email được che. |
| T2 | Mở chi tiết một user | Trả dữ liệu cho phép; có audit. |
| T3 | Khóa user hợp lệ | Trạng thái khóa được ghi, refresh token bị thu hồi. |
| T4 | User bị khóa gọi API bằng phiên cũ | Bị từ chối theo cơ chế xác thực. |
| T5 | `MANAGER` hoặc `FINANCE_ADMIN` gọi UC-114 | 403. |
| T6 | `SUPER_ADMIN` tự khóa mình | 409. |
| T7 | Khóa `SUPER_ADMIN` cuối cùng | 409. |
| T8 | Mở khóa | Token cũ không tự hoạt động lại. |
| T9 | Kiểm response | Không có password hash/token nội bộ. |

---

# UC-115 · Cấp và thu hồi role

| | |
| --- | --- |
| **UC-ID** | UC-115 |
| **Actor chính** | `SUPER_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P1 |
| **Scope** | MVP |
| **FT** | 6.1 |
| **Trạng thái triển khai** | READY |

## Mô tả

`SUPER_ADMIN` cấp hoặc thu hồi role của một tài khoản.

Hệ thống hỗ trợ sáu role được lưu trong hệ thống: `USER`, `TEACHER`, `MANAGER`, `CONTENT_ADMIN`, `FINANCE_ADMIN`, `SUPER_ADMIN`. `GUEST` là trạng thái truy cập chưa đăng nhập, không phải role được gán cho tài khoản.

Một tài khoản có thể có nhiều role. `SUPER_ADMIN` không tự động có quyền của các role nghiệp vụ khác; chỉ những role được cấp rõ ràng mới có hiệu lực.

## Kích hoạt

`SUPER_ADMIN` mở chi tiết quyền của một tài khoản và chọn cấp hoặc thu hồi role.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có role `SUPER_ADMIN`.
2. Tài khoản mục tiêu tồn tại.
3. Role cần thao tác tồn tại trong danh sách role chính thức.
4. Với thao tác ghi từ web, request có CSRF token hợp lệ.

## Hậu điều kiện

### Cấp role thành công

- Quan hệ user-role được tạo đúng một lần.
- Lưu người cấp và thời điểm cấp.
- Audit log được ghi.
- Phiên/token cũ của tài khoản mục tiêu bị thu hồi để quyền mới chỉ có hiệu lực sau lần xác thực tiếp theo theo cơ chế hệ thống.

### Thu hồi role thành công

- Quan hệ user-role tương ứng bị loại bỏ.
- Audit log ghi người thu hồi và thời điểm.
- Phiên/token cũ bị thu hồi để quyền bị gỡ không tiếp tục sử dụng.

### Không có thay đổi

- Nếu cấp role đã có hoặc thu hồi role không còn tồn tại, hệ thống trả trạng thái idempotent và không tạo thay đổi lặp.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `SUPER_ADMIN` | Mở danh sách role của tài khoản mục tiêu. |
| 2 | System | Kiểm tra quyền và trả các role hiện có. |
| 3 | `SUPER_ADMIN` | Chọn một role cần cấp. |
| 4 | System | Kiểm tra role hợp lệ và tài khoản mục tiêu không bị khóa/ban. |
| 5 | System | Kiểm tra actor không tự cấp thêm role cho chính mình. |
| 6 | System | Nếu target chưa có role, tạo quan hệ user-role và ghi người cấp/thời điểm. |
| 7 | System | Ghi audit cho thao tác cấp quyền. |
| 8 | System | Thu hồi các phiên/token cũ của tài khoản mục tiêu theo cơ chế xác thực. |
| 9 | Client | Hiển thị danh sách role sau cập nhật. |

## Luồng thay thế

**A1 — Thu hồi role**

1. `SUPER_ADMIN` chọn một role đang có.
2. Hệ thống kiểm tra thao tác không làm mất `SUPER_ADMIN` cuối cùng đang hoạt động.
3. Hệ thống xóa quan hệ user-role.
4. Ghi audit.
5. Thu hồi phiên/token cũ của tài khoản mục tiêu.

**A2 — Cấp nhiều role**

- Thực hiện từng role theo luồng chính.
- Không có role nào được ngầm kế thừa chỉ vì tài khoản có `SUPER_ADMIN`.

**A3 — Cấp `GUEST`**

- Hệ thống từ chối vì `GUEST` không phải role lưu trong database.

**A4 — Cấp role đã có**

- Trả trạng thái hiện tại, không tạo thêm dòng trùng và không cấp lại lần hai.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Chặn. |
| `FORBIDDEN_ROLE` | 403 | Không có `SUPER_ADMIN` | Chặn. |
| `USER_NOT_FOUND` | 404 | Tài khoản mục tiêu không tồn tại | Không thay đổi role. |
| `INVALID_ROLE_CODE` | 422 | Role ngoài danh sách chính thức hoặc là `GUEST` | Không thay đổi role. |
| `SELF_ROLE_GRANT` | 403 | `SUPER_ADMIN` tự cấp thêm role cho mình | Chặn. |
| `ROLE_GRANTED_TO_BANNED_USER` | 409 | Tài khoản mục tiêu đang bị khóa/ban | Không cấp role. |
| `REVOKE_LAST_SUPER_ADMIN` | 409 | Thu hồi `SUPER_ADMIN` cuối cùng đang hoạt động | Chặn. |

## Business rule

| # | Rule |
| --- | --- |
| BR-115-1 | Chỉ `SUPER_ADMIN` được cấp hoặc thu hồi role. |
| BR-115-2 | `GUEST` không phải role lưu trong database, nên không được cấp hoặc thu hồi như role. |
| BR-115-3 | Hệ thống chỉ cho phép các role đã định nghĩa chính thức: `USER`, `TEACHER`, `MANAGER`, `CONTENT_ADMIN`, `FINANCE_ADMIN`, `SUPER_ADMIN`. |
| BR-115-4 | Một người dùng có thể có nhiều role nếu được cấp hợp lệ. |
| BR-115-5 | `SUPER_ADMIN` không được tự cấp thêm role cho chính mình. |
| BR-115-6 | Không được thu hồi role `SUPER_ADMIN` cuối cùng đang hoạt động. |
| BR-115-7 | Không cấp role mới cho tài khoản đang bị khóa hoặc bị ban. |
| BR-115-8 | Mỗi lần cấp role phải ghi người cấp, thời điểm cấp và audit log. |
| BR-115-9 | Mỗi lần thu hồi role phải ghi người thu hồi, thời điểm thu hồi và audit log. |
| BR-115-10 | Khi role của người dùng thay đổi, hệ thống phải thu hồi phiên/token cũ để quyền cũ không còn hiệu lực. |
| BR-115-11 | UC-115 là luồng hợp pháp duy nhất để thay đổi role. Các endpoint khác, ví dụ cập nhật hồ sơ cá nhân, không được ghi vào dữ liệu role. |

## API · DB

```text
GET    /api/admin/users/{id}/roles
POST   /api/admin/users/{id}/roles
DELETE /api/admin/users/{id}/roles/{code}
```

Dữ liệu liên quan: `user_roles`, `roles`, `users`, `auth_tokens`, audit log.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `SUPER_ADMIN` cấp `TEACHER` cho user hợp lệ | Role được tạo, có `granted_by`, audit và token cũ bị thu hồi. |
| T2 | `CONTENT_ADMIN`, `FINANCE_ADMIN`, `MANAGER`, `TEACHER` hoặc `USER` gọi | 403. |
| T3 | Tự cấp `FINANCE_ADMIN` | 403. |
| T4 | Cấp `GUEST` | 422. |
| T5 | Cấp role đã có | Không tạo dòng trùng. |
| T6 | Thu hồi `SUPER_ADMIN` cuối cùng | 409. |
| T7 | Cấp role cho tài khoản bị khóa | 409. |
| T8 | Gửi `roles` qua API cập nhật profile | Không thay đổi `user_roles`. |
| T9 | Sau khi thu hồi role | Phiên/token cũ không tiếp tục dùng quyền đã bị gỡ. |

---

# UC-116 · Cấu hình hệ thống

| | |
| --- | --- |
| **UC-ID** | UC-116 |
| **Actor chính** | `SUPER_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P2 |
| **Scope** | Deferred |
| **FT** | 6.6 |
| **Trạng thái triển khai** | **DEFERRED — chưa chốt danh sách cấu hình và chưa có nơi lưu cấu hình** |

## Mô tả

`SUPER_ADMIN` thay đổi các **tham số nghiệp vụ đã được phê duyệt** mà hệ thống cho phép cấu hình mà không cần sửa code.

Use Case này không cho phép tạo key tùy ý và không dùng để quản lý secret, API key, mật khẩu hoặc token.

Trong trạng thái hiện tại, dự án chưa chốt danh sách tham số nào bắt buộc phải chỉnh từ giao diện và chưa có nơi lưu cấu hình. Vì vậy UC-116 được giữ để bảo toàn traceability nhưng **không gen code trong MVP**.

## Kích hoạt

Sau khi UC được đưa trở lại scope, `SUPER_ADMIN` mở trang cấu hình hệ thống và chọn một tham số đã được khai báo để chỉnh sửa.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có role `SUPER_ADMIN`.
2. Danh sách key cấu hình được phép chỉnh đã được phê duyệt.
3. Mỗi key có kiểu dữ liệu, giá trị mặc định và khoảng hợp lệ.
4. Thiết kế nơi lưu cấu hình đã được duyệt.
5. Request ghi từ web có CSRF token hợp lệ.

## Hậu điều kiện

### Cập nhật thành công

- Chỉ key được phép mới thay đổi.
- Giá trị mới hợp lệ theo kiểu và khoảng cho phép.
- Ghi người thay đổi, thời điểm, giá trị cũ và giá trị mới vào audit.
- Cache cấu hình được làm mới nếu có.

### Cập nhật thất bại

- Giá trị cũ được giữ nguyên.
- Không ghi secret vào kho cấu hình.
- Không để một phần hệ thống dùng giá trị mới trong khi phần khác vẫn dùng giá trị cũ.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `SUPER_ADMIN` | Mở trang cấu hình hệ thống. |
| 2 | System | Kiểm tra quyền và trả danh sách các tham số được phép chỉnh. |
| 3 | `SUPER_ADMIN` | Chọn một tham số và nhập giá trị mới. |
| 4 | System | Kiểm tra key có trong danh sách cho phép. |
| 5 | System | Kiểm tra kiểu dữ liệu và phạm vi giá trị. |
| 6 | System | Kiểm tra thay đổi không vi phạm các giới hạn đang được áp dụng cho một hoạt động không được phép thay đổi giữa chừng. |
| 7 | System | Lưu giá trị mới và ghi audit. |
| 8 | System | Làm mới cache cấu hình nếu có. |
| 9 | Client | Hiển thị giá trị hiện tại sau cập nhật. |

## Luồng thay thế

**A1 — Chỉ xem cấu hình**

- `SUPER_ADMIN` mở trang và không chỉnh sửa.
- Không có thay đổi hoặc audit dạng “change”.

**A2 — Thay đổi có thể làm sai tiến độ đã có**

- Nếu chưa có quy tắc tính lại dữ liệu, hệ thống từ chối thay đổi.
- UC này không tự sinh thêm job migrate/tính lại nếu chưa được đặc tả.

**A3 — Cấu hình ảnh hưởng cuộc thi đang diễn ra**

- Từ chối thay đổi trong thời gian cuộc thi bị ảnh hưởng còn đang diễn ra.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Chặn. |
| `FORBIDDEN_ROLE` | 403 | Không có `SUPER_ADMIN` | Chặn. |
| `UNKNOWN_SETTING_KEY` | 404 | Key không nằm trong danh sách cho phép | Không tạo key mới. |
| `VALUE_OUT_OF_RANGE` | 422 | Giá trị sai kiểu hoặc ngoài khoảng | Giữ giá trị cũ. |
| `SECRET_SETTING_NOT_ALLOWED` | 422 | Cố lưu secret/API key/mật khẩu/token | Chặn. |
| `SETTING_CHANGE_NOT_SAFE` | 409 | Thay đổi làm sai tiến độ hoặc hoạt động đang diễn ra | Không cập nhật. |
| `SETTING_STORAGE_NOT_READY` | Không phải runtime production | Chưa có thiết kế lưu cấu hình | **Không triển khai UC trong MVP**. |

## Business rule

| # | Rule |
| --- | --- |
| BR-116-1 | UC này nên cắt khỏi MVP nếu nhóm chưa chốt rõ danh sách cấu hình và chưa có nơi lưu cấu hình. |
| BR-116-2 | Nếu triển khai, chỉ `SUPER_ADMIN` được xem và sửa cấu hình hệ thống. |
| BR-116-3 | Chỉ các key cấu hình đã được khai báo trước mới được sửa. Không cho tạo key tùy ý từ giao diện. |
| BR-116-4 | Mỗi cấu hình phải có kiểu dữ liệu, khoảng giá trị hợp lệ và mô tả rõ ràng. |
| BR-116-5 | Không lưu secret, API key, mật khẩu hoặc token trong bảng cấu hình. Secret phải dùng biến môi trường hoặc cơ chế bảo mật riêng. |
| BR-116-6 | Mọi lần đổi cấu hình phải ghi audit log, gồm giá trị cũ, giá trị mới, người đổi và thời điểm đổi. |
| BR-116-7 | Nếu đổi cấu hình ảnh hưởng tiến độ người học, hệ thống phải có quy trình tính lại hoặc phải cấm đổi sau khi đã có dữ liệu thật. |
| BR-116-8 | Không được đổi cấu hình ảnh hưởng cuộc thi đang diễn ra. |
| BR-116-9 | Sau khi đổi cấu hình, hệ thống phải làm mới cache cấu hình nếu có. |

## API · DB

```text
GET /api/admin/settings
PUT /api/admin/settings/{key}
```

> **Chưa code-ready:** nơi lưu `system_settings` chưa được duyệt. Không tự tạo bảng/migration từ UC này.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `CONTENT_ADMIN` gọi | 403. |
| T2 | Key không được khai báo | 404. |
| T3 | Giá trị ngoài khoảng | 422. |
| T4 | Cố lưu API key | 422. |
| T5 | Thay đổi ảnh hưởng dữ liệu đã có nhưng chưa có quy tắc tính lại | 409. |
| T6 | Cấu hình ảnh hưởng cuộc thi đang diễn ra | 409. |
| T7 | Chưa có nơi lưu cấu hình | UC không được bật trong MVP. |

---

# UC-117 · Xem danh sách kênh YouTube và podcast

| | |
| --- | --- |
| **UC-ID** | UC-117 |
| **Actor chính** | `GUEST`, `USER` |
| **Loại** | User Goal |
| **Pri** | P3 |
| **Scope** | V2 |
| **FT** | 7.1 |
| **Trạng thái triển khai** | V2 — không gen MVP |

## Mô tả

Người dùng xem danh sách các kênh YouTube và podcast được CNHSK giới thiệu để tự học tiếng Trung.

CNHSK chỉ hiển thị thông tin giới thiệu và liên kết ra trang bên ngoài. Hệ thống không tải về, sao chép, lưu trữ hoặc phát lại nội dung của bên thứ ba trong Use Case này.

## Kích hoạt

`GUEST` hoặc `USER` mở trang tài nguyên và chọn nhóm YouTube hoặc podcast.

## Tiền điều kiện

Không yêu cầu đăng nhập.

Để một tài nguyên xuất hiện trong danh sách, tài nguyên đó phải tồn tại trong danh mục và đang ở trạng thái được công bố.

## Hậu điều kiện

- Chỉ đọc dữ liệu.
- Không tạo tiến độ học, quota, lịch sử cá nhân hoặc dữ liệu sở hữu.
- Chỉ tài nguyên đã công bố được trả về.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `GUEST` / `USER` | Mở trang “Tài nguyên”. |
| 2 | `GUEST` / `USER` | Chọn loại YouTube hoặc podcast. |
| 3 | System | Lấy các tài nguyên đã công bố đúng loại. |
| 4 | System | Áp dụng bộ lọc HSK nếu người dùng chọn. |
| 5 | System | Trả tên, mô tả, cấp HSK phù hợp và URL bên ngoài. |
| 6 | Client | Hiển thị danh sách tài nguyên. |
| 7 | `GUEST` / `USER` | Chọn một tài nguyên. |
| 8 | Client | Mở liên kết bên ngoài trong tab mới với thuộc tính an toàn. |

## Luồng thay thế

**A1 — Lọc theo cấp HSK**

- Chỉ trả các tài nguyên có cấp HSK phù hợp với bộ lọc.

**A2 — Không có tài nguyên**

- Trả danh sách rỗng.
- Client hiển thị trạng thái “Đang cập nhật”.

**A3 — Phát hiện link chết**

- UC này không tạo thêm hệ thống báo cáo riêng.
- Người dùng có thể thông báo qua kênh hỗ trợ hiện có; `CONTENT_ADMIN` sửa hoặc ẩn tài nguyên bằng UC-119.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `INVALID_HSK_LEVEL` | 422 | Cấp HSK ngoài phạm vi hỗ trợ | Trả validation. |
| `RESOURCE_READ_ERROR` | 500 | Không tải được danh mục | Báo lỗi tải, không giả thành danh sách rỗng. |
| `UNPUBLISHED_RESOURCE_EXPOSED` | Không phải HTTP riêng | Lỗi lọc dữ liệu | Phải được ngăn ở truy vấn/DTO trước khi trả client. |

## Business rule

| # | Rule |
| --- | --- |
| BR-117-1 | UC này thuộc V2/P3. Không gen code cho MVP. |
| BR-117-2 | `GUEST` và `USER` đều được xem danh sách tài nguyên đã công bố. |
| BR-117-3 | Chỉ hiển thị tài nguyên có trạng thái đã công bố. Tài nguyên nháp hoặc đã ẩn không được hiển thị công khai. |
| BR-117-4 | Tính năng này chỉ hiển thị link ra ngoài, không tải, lưu, sao chép hoặc nhúng nội dung của bên thứ ba. |
| BR-117-5 | Link ngoài phải mở an toàn, dùng `rel="noopener noreferrer"` nếu mở tab mới. |
| BR-117-6 | Người dùng có thể báo link chết hoặc link sai để `CONTENT_ADMIN` xử lý sau. |
| BR-117-7 | Link tài nguyên phải được kiểm tra domain hợp lệ ở luồng quản lý tài nguyên, không đợi đến lúc hiển thị mới kiểm. |

## API · DB

```text
GET /api/public/resources?type={YOUTUBE|PODCAST}
```

Dữ liệu liên quan: `learning_resources`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `GUEST` xem danh sách YouTube | Xem được tài nguyên đã công bố. |
| T2 | `USER` xem podcast | Xem được tài nguyên đã công bố. |
| T3 | Tài nguyên chưa công bố | Không xuất hiện. |
| T4 | `hsk_level` ngoài phạm vi | 422. |
| T5 | Không có dữ liệu | 200 với danh sách rỗng. |
| T6 | Link mở tab mới | Có `rel="noopener noreferrer"`. |

---

# UC-118 · Xem danh mục sách học tiếng Trung

| | |
| --- | --- |
| **UC-ID** | UC-118 |
| **Actor chính** | `GUEST`, `USER` |
| **Loại** | User Goal |
| **Pri** | P3 |
| **Scope** | V2 |
| **FT** | 7.2 |
| **Trạng thái triển khai** | V2 — không gen MVP |

## Mô tả

Người dùng xem danh mục sách học tiếng Trung do CNHSK giới thiệu, gồm các thông tin như tên sách, tác giả, cấp độ phù hợp, mô tả và liên kết mua/tham khảo nếu có.

Use Case chỉ là danh mục tham khảo. CNHSK không lưu trữ, phân phối hoặc cho tải file PDF/nội dung sách có bản quyền.

## Kích hoạt

`GUEST` hoặc `USER` mở trang tài nguyên và chọn danh mục sách.

## Tiền điều kiện

Không yêu cầu đăng nhập.

Để một sách xuất hiện, mục tài nguyên tương ứng phải ở trạng thái đã công bố.

## Hậu điều kiện

- Chỉ đọc dữ liệu.
- Không lưu lịch sử cá nhân hoặc tiến độ học.
- Không trả file sách hoặc nội dung sách.
- Chỉ tài nguyên đã công bố được hiển thị.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `GUEST` / `USER` | Mở danh mục sách. |
| 2 | System | Lấy các tài nguyên loại `BOOK` đang được công bố. |
| 3 | System | Áp dụng bộ lọc HSK nếu có. |
| 4 | System | Trả tên sách, tác giả, thông tin xuất bản có sẵn, cấp độ phù hợp, mô tả và link mua/tham khảo nếu có. |
| 5 | Client | Hiển thị danh sách. |
| 6 | `GUEST` / `USER` | Chọn một sách để xem thông tin chi tiết. |
| 7 | Client | Hiển thị metadata; nếu có link ngoài thì cho phép mở link an toàn. |

## Luồng thay thế

**A1 — Lọc theo cấp HSK**

- Chỉ hiển thị sách phù hợp với cấp đã chọn.

**A2 — Sách không có link mua**

- Vẫn hiển thị thông tin giới thiệu.
- Không hiển thị nút mua.

**A3 — Không có sách**

- Trả danh sách rỗng và hiển thị “Đang cập nhật”.

**A4 — Link mua là affiliate**

- Nếu dự án sử dụng affiliate, giao diện phải hiển thị thông tin theo đúng business rule.
- Không tự thêm cơ chế affiliate nếu dự án không sử dụng.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `INVALID_HSK_LEVEL` | 422 | Cấp HSK ngoài phạm vi | Trả validation. |
| `RESOURCE_READ_ERROR` | 500 | Không tải được danh mục | Báo lỗi tải. |
| `BOOK_CONTENT_EXPOSED` | Không phải HTTP riêng | Dữ liệu trả ra chứa file/nội dung sách không được phép | Phải bị loại khỏi DTO/public API. |

## Business rule

| # | Rule |
| --- | --- |
| BR-118-1 | UC này thuộc V2/P3. Không gen code cho MVP. |
| BR-118-2 | `GUEST` và `USER` đều được xem danh mục sách đã công bố. |
| BR-118-3 | Danh mục sách chỉ hiển thị thông tin giới thiệu và link mua hoặc link tham khảo. |
| BR-118-4 | Hệ thống không lưu, không cho tải và không phân phối PDF hoặc nội dung sách có bản quyền. |
| BR-118-5 | Không lưu ảnh bìa sách nếu chưa chắc quyền sử dụng. Nếu cần ảnh, chỉ lưu link ảnh hoặc bỏ ảnh bìa. |
| BR-118-6 | Link mua hoặc link tham khảo phải thuộc domain được phép. |
| BR-118-7 | Nếu dùng link affiliate, giao diện phải ghi rõ đây là link tiếp thị liên kết. |
| BR-118-8 | Link ngoài phải mở an toàn, dùng `rel="noopener noreferrer"` nếu mở tab mới. |

## API · DB

```text
GET /api/public/resources?type=BOOK
```

Dữ liệu liên quan: `learning_resources`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `GUEST` mở danh mục sách | Xem được sách đã công bố. |
| T2 | Sách không có link mua | Vẫn hiển thị metadata; không có nút mua. |
| T3 | Mục chưa công bố | Không xuất hiện. |
| T4 | Kiểm response/API | Không có file PDF hoặc nội dung sách. |
| T5 | Link mua mở tab mới | Có thuộc tính mở link an toàn. |
| T6 | Không có sách | 200 với danh sách rỗng. |

---

# UC-119 · Quản lý danh mục tham khảo

| | |
| --- | --- |
| **UC-ID** | UC-119 |
| **Actor chính** | `CONTENT_ADMIN` |
| **Loại** | User Goal |
| **Pri** | P3 |
| **Scope** | V2 |
| **FT** | 7.1 · 7.2 |
| **Trạng thái triển khai** | V2 — không gen MVP |

## Mô tả

`CONTENT_ADMIN` thêm, sửa, công bố hoặc ẩn các tài nguyên tham khảo dùng cho UC-117 và UC-118, gồm kênh YouTube, podcast và sách.

Tài nguyên mới luôn ở trạng thái chưa công bố để người quản trị kiểm tra trước. Hệ thống chỉ lưu metadata và liên kết; không lưu nội dung của bên thứ ba.

## Kích hoạt

`CONTENT_ADMIN` mở màn hình quản lý tài nguyên và chọn tạo mới hoặc chỉnh sửa một mục.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `CONTENT_ADMIN`.
2. Request ghi từ web có CSRF token hợp lệ.
3. Loại tài nguyên nằm trong danh sách hệ thống hỗ trợ.
4. Danh sách domain được phép cho từng loại tài nguyên đã được cấu hình.

## Hậu điều kiện

### Tạo mới thành công

- Tài nguyên được lưu ở trạng thái chưa công bố.
- URL đã qua kiểm tra scheme và domain.
- Audit ghi người tạo và thời điểm.

### Sửa thành công

- Dữ liệu mới được lưu.
- Nếu URL thay đổi, URL mới phải được kiểm tra lại.
- Audit ghi thay đổi.

### Công bố

- Tài nguyên đủ điều kiện trở thành dữ liệu công khai cho UC-117/118.

### Ẩn

- Tài nguyên không còn xuất hiện công khai.
- Bản ghi vẫn được giữ, không xóa cứng.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `CONTENT_ADMIN` | Chọn **Thêm tài nguyên**. |
| 2 | `CONTENT_ADMIN` | Nhập loại, tên, mô tả, cấp HSK nếu có và URL. |
| 3 | System | Kiểm tra quyền và CSRF. |
| 4 | System | Kiểm tra loại tài nguyên hợp lệ. |
| 5 | System | Kiểm tra URL đúng định dạng và chỉ dùng `https`. |
| 6 | System | Kiểm tra domain thuộc danh sách được phép cho loại tài nguyên đó. |
| 7 | System | Lưu tài nguyên ở trạng thái chưa công bố và ghi audit. |
| 8 | `CONTENT_ADMIN` | Kiểm tra lại nội dung và chọn **Công bố**. |
| 9 | System | Kiểm tra lại dữ liệu bắt buộc và chuyển sang trạng thái công bố. |
| 10 | Client | Hiển thị trạng thái mới. |

## Luồng thay thế

**A1 — Sửa tài nguyên**

- Actor mở tài nguyên hiện có.
- Hệ thống áp dụng lại toàn bộ validation cho trường được thay đổi.
- Nếu URL thay đổi, phải kiểm tra lại scheme và whitelist domain.
- Ghi audit thay đổi.

**A2 — Ẩn tài nguyên**

- Actor chọn **Ẩn**.
- Hệ thống chuyển trạng thái về không công bố.
- Tài nguyên biến mất khỏi UC-117/118 nhưng vẫn còn trong quản trị.

**A3 — Xử lý link chết**

- `CONTENT_ADMIN` kiểm tra link.
- Nếu có URL thay thế hợp lệ thì cập nhật.
- Nếu chưa có URL thay thế, ẩn tài nguyên.
- Không tạo thêm một hệ thống quản lý report riêng trong UC này.

## Bảng exception

| Mã lỗi | HTTP | Khi xảy ra | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Chặn. |
| `FORBIDDEN_ROLE` | 403 | Không có `CONTENT_ADMIN` | Chặn. |
| `INVALID_RESOURCE_TYPE` | 422 | Loại tài nguyên không được hỗ trợ | Không lưu. |
| `NON_HTTPS_LINK` | 422 | URL không dùng `https` | Không lưu. |
| `MALFORMED_URL` | 422 | URL sai định dạng | Không lưu. |
| `DOMAIN_NOT_ALLOWED` | 422 | Domain ngoài danh sách được phép | Không lưu. |
| `UNSAFE_URL_SCHEME` | 422 | `javascript:`, `data:` hoặc scheme nguy hiểm | Không lưu. |
| `PUBLISH_INVALID_RESOURCE` | 422 | Thiếu dữ liệu bắt buộc khi công bố | Giữ trạng thái chưa công bố. |

## Business rule

| # | Rule |
| --- | --- |
| BR-119-1 | UC này thuộc V2/P3. Không gen code cho MVP. |
| BR-119-2 | Chỉ `CONTENT_ADMIN` được thêm, sửa, ẩn hoặc công bố tài nguyên tham khảo. |
| BR-119-3 | Tài nguyên mới tạo mặc định ở trạng thái chưa công bố. |
| BR-119-4 | Link tài nguyên phải dùng `https`. Không chấp nhận `http`, `javascript:`, `data:` hoặc URL sai định dạng. |
| BR-119-5 | Link tài nguyên phải thuộc danh sách domain được phép. |
| BR-119-6 | Mô tả tài nguyên phải được escape khi hiển thị vì đây là nội dung public cho cả `GUEST`. |
| BR-119-7 | Khi không muốn hiển thị tài nguyên nữa, hệ thống ẩn tài nguyên thay vì xóa cứng. |
| BR-119-8 | Mọi lần thêm, sửa, công bố hoặc ẩn tài nguyên phải có audit log. |

## API · DB

```text
GET    /api/admin/resources
POST   /api/admin/resources
PUT    /api/admin/resources/{id}
PATCH  /api/admin/resources/{id}/publish
PATCH  /api/admin/resources/{id}/unpublish
```

Dữ liệu liên quan: `learning_resources`, audit log.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Thêm kênh YouTube hợp lệ | Tạo ở trạng thái chưa công bố, có audit. |
| T2 | URL dùng `http://` | 422. |
| T3 | URL `javascript:alert(1)` | 422. |
| T4 | Domain ngoài whitelist | 422. |
| T5 | Công bố tài nguyên thiếu trường bắt buộc | 422, vẫn chưa công bố. |
| T6 | `FINANCE_ADMIN` gọi | 403. |
| T7 | Ẩn tài nguyên đã công bố | Không còn xuất hiện ở public API, bản ghi vẫn còn. |
| T8 | Sửa URL sang domain khác | URL mới được validate lại trước khi lưu. |

---

# Tổng hợp trạng thái triển khai

| Nhóm | UC | Kết luận |
| --- | --- | --- |
| AI review | UC-108 | MVP, READY. |
| Question management | UC-109 | MVP, READY; không sửa grading semantics của câu đã có lịch sử làm bài. |
| Teacher data import | UC-110 | P0 nhưng BLOCKED cho đến khi có file mẫu thật, template và natural key. |
| Import error report | UC-111 | P0, cần nơi lưu chi tiết lỗi trước khi code hoàn chỉnh. |
| Exam/question bank management | UC-112 | MVP, READY. |
| Contest | UC-113 | V2, không gen MVP. |
| User administration | UC-114 | MVP nhưng BLOCKED cho đến khi chốt dữ liệu trạng thái tài khoản. |
| Role management | UC-115 | MVP, READY. |
| System settings | UC-116 | DEFERRED, không gen MVP khi scope và storage chưa chốt. |
| Reference resources | UC-117 → UC-119 | V2, không gen MVP. |

## Các blocker phải được giải quyết trước khi dùng AI/codegen cho UC tương ứng

1. **UC-110:** lấy ít nhất một file mẫu thật của giáo viên; chốt template và khóa tự nhiên cho từng loại dữ liệu.
2. **UC-111:** chốt nơi lưu chi tiết lỗi import để đóng tab rồi vẫn xem lại được.
3. **UC-114:** chốt cấu trúc trạng thái tài khoản dùng để khóa/ban.
4. **UC-116:** chỉ đưa lại vào scope khi có danh sách setting cụ thể và nơi lưu được duyệt.
5. **UC-113, UC-117, UC-118, UC-119:** giữ nguyên V2; không gen vào MVP.

# UC-137 · Quản trị kho nội dung học

| | |
| --- | --- |
| **ID** | UC-137 |
| **Actor chính** | `CONTENT_ADMIN` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.4 |
| **Loại** | Use case tổng quát |

## Mô tả

Người quản trị nội dung đưa dữ liệu học vào hệ thống và giữ cho kho câu hỏi, đề thi luôn đúng. Mọi nội dung tới người học đều đi qua khâu kiểm duyệt của họ hoặc của giảng viên.

## Quan hệ use case

| Quan hệ | Use case | Điều kiện áp dụng |
| --- | --- | --- |
| `«extend»` | UC-108 Duyệt câu hỏi do trí tuệ nhân tạo sinh | Có câu hỏi trong hàng đợi duyệt |
| `«extend»` | UC-109 Sửa nội dung câu hỏi | Câu hỏi cần chỉnh trước khi dùng |
| `«extend»` | UC-110 Nhập dữ liệu từ file | Có dữ liệu mới cần đưa vào hệ thống |
| `«extend»` | UC-111 Xem báo cáo lỗi sau khi nhập | Lần nhập trước có dòng lỗi |
| `«extend»` | UC-112 Quản lý đề thi và kho câu hỏi | Cần tạo hoặc sửa đề thi |

## Tiền điều kiện

- Người dùng có vai trò quản trị nội dung
- Với việc nhập dữ liệu, file đúng định dạng đã quy định

## Hậu điều kiện

- Nội dung mới nằm trong kho ở trạng thái phù hợp
- Câu hỏi do trí tuệ nhân tạo sinh chỉ tới người học sau khi được duyệt
- Mỗi lần nhập dữ liệu có bản ghi kèm số dòng thành công và số dòng lỗi

## Luồng chính

1. `CONTENT_ADMIN` mở khu vực quản trị nội dung
2. Chọn việc cần làm là nhập dữ liệu, duyệt câu hỏi hay quản lý đề thi
3. Với nhập dữ liệu, tải file lên và xem trước kết quả kiểm tra
4. Hệ thống báo số dòng hợp lệ và liệt kê dòng lỗi kèm lý do
5. `CONTENT_ADMIN` xác nhận nhập các dòng hợp lệ
6. Với câu hỏi chờ duyệt, xem nội dung và quyết định nhận hay từ chối
7. Hệ thống ghi lại thao tác kèm người thực hiện
8. Nội dung đã duyệt trở nên khả dụng cho người học

## Luồng thay thế

**A1 · File nhập có cả dòng đúng và dòng sai**
Hệ thống lưu các dòng đúng và báo riêng các dòng sai, không bỏ cả file. Chi tiết ở UC-110.

**A2 · Sửa câu hỏi trước khi duyệt**
Người quản trị chỉnh nội dung rồi mới cho duyệt. Chi tiết ở UC-109.

**A3 · Câu hỏi đã có người học làm**
Không cho đổi đáp án đúng, vì sẽ làm sai lịch sử kết quả đã có.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN` | Không có vai trò quản trị nội dung | **403** | Từ chối truy cập |
| `FILE_FORMAT_INVALID` | File sai định dạng | **422** | Báo định dạng được chấp nhận |
| `IMPORT_ENCODING_ERROR` | File lỗi mã hóa chữ Hán | **422** | Chặn trước khi ghi, tránh dữ liệu hỏng |
| `QUESTION_IN_USE` | Đổi đáp án câu đã có người làm | **409** | Yêu cầu tạo phiên bản câu hỏi mới |
| `NO_KNOWLEDGE_POINT` | Câu hỏi chưa gắn điểm kiến thức | **422** | Không cho xuất bản câu hỏi |

> 🔴 **Câu hỏi chưa gắn điểm kiến thức không được xuất bản.** Người học trả lời
> câu đó sẽ không cập nhật được mức độ nắm vững, và lỗi này không báo gì cả nên
> rất khó phát hiện về sau.

## Business rule

| # | Rule |
| --- | --- |
| BR-137-1 | Câu hỏi do trí tuệ nhân tạo sinh luôn vào hàng đợi duyệt, không tới thẳng người học. |
| BR-137-2 | Mọi câu hỏi phải gắn ít nhất một điểm kiến thức trước khi xuất bản. |
| BR-137-3 | Nhập dữ liệu luôn có bước xem trước, không ghi thẳng vào kho. |
| BR-137-4 | File nhập có dòng sai thì vẫn lưu các dòng đúng, và báo riêng dòng sai kèm lý do. |
| BR-137-5 | Câu hỏi đã có người học làm thì không đổi được đáp án đúng, phải tạo phiên bản mới. |
| BR-137-6 | Xóa nội dung đã được dùng là đánh dấu lưu trữ, không xóa hẳn. |
| BR-137-7 | Dữ liệu đề thi do giảng viên cung cấp đưa vào qua màn nhập dữ liệu, không đặt trong mã nguồn. |

## API · DB

```
POST /api/admin/imports
GET  /api/admin/imports/{id}/errors
GET  /api/admin/questions/review
POST /api/admin/questions/{id}/approve
```

`questions` · `question_options` · `question_knowledge_points` · `exams` (đọc, ghi) · `audit_logs` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nhập file đúng định dạng | Xem trước rồi mới ghi vào kho |
| T2 | File có 10 dòng, 2 dòng sai | Lưu 8 dòng, báo riêng 2 dòng lỗi |
| T3 | File lỗi mã hóa chữ Hán | 422, chặn trước khi ghi |
| T4 | Xuất bản câu chưa gắn điểm kiến thức | 422 |
| T5 | Đổi đáp án câu đã có người làm | 409, yêu cầu tạo phiên bản mới |
| T6 | Người không có vai trò mở màn này | 403 |
| T7 | Kiểm tra sau khi duyệt câu hỏi | Có bản ghi người thực hiện |

---

---

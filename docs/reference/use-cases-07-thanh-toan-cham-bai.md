# CNHSK — Đặc tả Use Case · Nhóm 6a + 6b · Thanh toán & Chấm bài thuê

> **UC-093 → UC-107** · 15 use case · Tính năng 6.1 · 6.2
> **Bản chuẩn hóa Use Case** · cập nhật 2026-10-08
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
>
> 🔴 **Đây là nhóm dính tiền thật.** Feature tree ghi rõ: *"đây là chỗ thầy sẽ hỏi kỹ nhất khi
> bảo vệ. Chuẩn bị giải thích được 6 điểm bảo mật."*
>
> **PAYMENT_SCOPE đã chốt:** tiền thật thu ngoài hệ thống; CNHSK quản lý mã thẻ,
> điểm và quyền sử dụng, không tích hợp cổng thanh toán. FREE có 10 lượt/tính năng/tháng,
> reset 00:00 ngày 1 theo giờ Việt Nam.
>
> **Phạm vi cập nhật:** giữ nguyên 15 UC và phạm vi chức năng đã có; chuẩn hóa mô tả, tiền điều kiện, hậu điều kiện, luồng nghiệp vụ và exception để dùng tiếp cho Use Case Description/codegen.
> Business rule trong file được đồng bộ với PAYMENT_SCOPE đã chốt. Các chính sách chưa đủ quyết định như teacher payout, hạn nhận/hạn chấm và chuyển gói khác loại được ghi rõ là điều kiện chặn triển khai; không tự gán giá trị.
>
> **Nguồn đối chiếu:** [Hiến pháp](../../.specify/memory/constitution.md),
> [spec 001](../../specs/001-auth-rbac/spec.md), [thiết kế DB](database.md),
> [V1 hiện có](../../db/migration/V1__core_schema.sql). Thiết kế chờ duyệt không đồng nghĩa
> đã có migration. Mô tả API trong UC không thay thế bước duyệt OpenAPI trước triển khai.
>
> **Quy ước đọc:** phân biệt lỗi trả client với bất biến/kiểm thử nội bộ; trạng thái bình
> thường không phải exception. Lỗi theo `{error_code, message, request_id}`;
> thiếu xác thực trả 401, thiếu quyền trả 403, validation dữ liệu trả 422 theo quy ước dự án;
> lỗi mã thẻ không dùng được giữ thông báo chung tại UC-094. Kiểm quyền ở server,
> không suy diễn kế thừa role; truy cập dữ liệu xuyên module chỉ qua lớp API.

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
| --- | --- | --- | --- | --- | --- |
| UC-093 | Xem gói dịch vụ, số điểm và lượt còn lại | `USER` | P1 | MVP | 6.1 |
| UC-094 | Đổi mã CREDIT để cộng điểm | `USER` | P1 | MVP | 6.1 |
| UC-095 | Đổi mã SUBSCRIPTION để kích hoạt hoặc gia hạn gói | `USER` | P1 | MVP | 6.1 |
| UC-096 | Xác định và tiêu thụ quyền sử dụng cho tính năng tốn phí | Supporting UC | P1 | MVP | 6.1 |
| UC-097 | Hoàn quyền sử dụng khi hành động thất bại | Supporting UC | P1 | MVP | 6.1 |
| UC-098 | Xem lịch sử giao dịch điểm của mình | `USER` | P2 | MVP | 6.1 |
| UC-099 | Sinh lô mã thẻ nạp | `FINANCE_ADMIN` | P1 | MVP | 6.1 |
| UC-100 | Xem và đối chiếu sổ cái giao dịch điểm | `FINANCE_ADMIN` | P1 | MVP | 6.1 |
| UC-101 | Quản lý gói dịch vụ | `FINANCE_ADMIN` | P2 | MVP | 6.1 |
| UC-102 | Xử lý tranh chấp về điểm | `FINANCE_ADMIN` | P2 | MVP | 6.1 |
| UC-103 | Gửi yêu cầu nhờ chấm bài viết | `USER` | P2 | MVP | 6.2 |
| UC-104 | Xem hàng đợi bài chờ chấm | `TEACHER` | P2 | MVP | 6.2 |
| UC-105 | Nhận bài và gửi kết quả chấm | `TEACHER` | P2 | MVP | 6.2 |
| UC-106 | Xem trạng thái và kết quả chấm | `USER` | P2 | MVP | 6.2 |
| UC-107 | Hết hạn yêu cầu chấm chưa được nhận và hoàn quyền sử dụng | Scheduler | P2 | MVP | 6.2 |

---

## Sáu quy tắc bắt buộc của nhóm này

Mọi UC dưới đây tuân theo sáu quy tắc đã chốt cho bảng dính tiền:

| # | Quy tắc | UC thực thi |
| --- | --- | --- |
| 1 | Mã thẻ sinh bằng `SecureRandom`, ≥ 16 ký tự | UC-099 |
| 2 | **Chỉ lưu `code_hash`**, không bao giờ lưu mã thô | UC-099 · UC-094 |
| 3 | Nhập mã phải `SELECT ... FOR UPDATE` rồi đổi trạng thái **cùng transaction** | UC-094 · UC-095 |
| 4 | Giới hạn 5 lần nhập sai mỗi giờ mỗi tài khoản | UC-094 |
| 5 | Không ghi mã thẻ vào log, kể cả log lỗi | UC-094 · UC-099 |
| 6 | Mọi thay đổi điểm ghi `credit_transactions` kèm `balance_before` và `balance_after` | UC-094 → UC-097 |

> **Bản 5 thêm một lớp:** vì web dùng **cookie**, mọi thao tác đổi tiền **bắt buộc có CSRF
> token** của Spring Security. `SameSite=Lax` **không** chặn được tên miền phụ của chính mình.

---

# UC-093 · Xem gói dịch vụ, số điểm và lượt còn lại

| | |
|---|---|
| **UC-ID** | UC-093 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Người dùng xem quyền sử dụng hiện tại của tài khoản, gồm gói đang có hiệu lực, thời hạn gói nếu có, số dư điểm và số lượt còn lại của từng tính năng có giới hạn sử dụng: AI sinh bài, dịch, trợ lý ảo và nhờ giáo viên chấm bài.

Gói FREE có 10 lượt cho **mỗi tính năng** trong mỗi tháng dương lịch. Kỳ mới bắt đầu lúc 00:00 ngày 1 theo giờ Việt Nam. Nếu tài khoản đang có gói trả phí còn hiệu lực, hạn mức lấy từ cấu hình của gói đó; không tự cộng thêm một quỹ FREE riêng và không mặc định gói trả phí là không giới hạn.

Số lượt trong gói/quota và số dư điểm là hai nguồn sử dụng khác nhau. UC này chỉ dùng để xem thông tin, không tiêu thụ lượt, không trừ điểm và không thay đổi gói.

## Tiền điều kiện

1. Người dùng đã đăng nhập bằng tài khoản hợp lệ và được phép truy cập hệ thống.
2. Hệ thống xác định được gói hiện tại của tài khoản và cấu hình hạn mức tương ứng.
3. Hệ thống xác định được kỳ quota hiện tại theo giờ Việt Nam.
4. Người dùng **không cần** từng nạp mã, có điểm hoặc còn lượt mới được mở màn hình này.

## Hậu điều kiện

### Thành công

1. Người dùng nhận được gói hiện tại và thời hạn gói nếu có.
2. Người dùng nhận được số dư điểm hiện tại.
3. Người dùng nhận được số lượt đã dùng và số lượt còn lại của từng tính năng trong kỳ hiện tại.
4. Không có giao dịch điểm, thay đổi quota, kích hoạt/gia hạn gói hoặc thay đổi dữ liệu tài chính nào phát sinh từ UC này.

### Không thành công

1. Không thay đổi gói, số dư hoặc quota của người dùng.
2. Dữ liệu không đọc được không được tự thay bằng `0`, FREE hoặc “không giới hạn”.
3. Phần dữ liệu bị lỗi phải được hiển thị là chưa tải được để người dùng có thể thử lại.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở phần thông tin gói dịch vụ và số dư trong trang tài khoản. |
| 2 | Client | Gửi yêu cầu lấy thông tin gói/quota và số dư bằng phiên hiện tại. |
| 3 | System | Xác định người dùng từ phiên đăng nhập; không dùng `user_id` do client tự chọn. |
| 4 | System | Xác định gói đang có hiệu lực tại thời điểm kiểm tra. Nếu gói trả phí đã hết hạn, áp dụng quyền FREE. |
| 5 | System | Xác định kỳ sử dụng hiện tại theo giờ Việt Nam và lấy hạn mức của từng tính năng. |
| 6 | System | Lấy số lượt đã sử dụng trong đúng kỳ hiện tại và tính số lượt còn lại, không cho kết quả âm. |
| 7 | System | Lấy số dư điểm từ giao dịch đã ghi nhận gần nhất; nếu chưa từng có giao dịch thì số dư là 0. |
| 8 | System | Trả thông tin gói, thời hạn, quota từng tính năng và số dư điểm. |
| 9 | Client | Hiển thị các thông tin thành các mục riêng biệt để người dùng phân biệt rõ quota và điểm. |

## Luồng thay thế

**A1 — Tài khoản chưa từng có giao dịch điểm:** trả số dư bằng 0; không tạo giao dịch hay bản ghi giả chỉ để có số dư.

**A2 — Gói trả phí vừa hết hạn:** quyền sử dụng được xác định theo FREE ngay tại thời điểm đọc, không phụ thuộc việc job nền đã cập nhật trạng thái hay chưa.

**A3 — Một tính năng đã hết lượt:** vẫn hiển thị màn hình thành công với số lượt còn lại bằng 0; UC này không trả lỗi hết quyền sử dụng.

**A4 — Đã sang kỳ tháng mới nhưng bộ đếm cũ chưa được chuyển kỳ:** không dùng lượt đã sử dụng của tháng trước để tính tháng hiện tại. Nếu không xác định được kỳ đáng tin cậy, báo lỗi phần quota.

**A5 — Chỉ một phần dữ liệu đọc thất bại:** phần đọc được vẫn có thể hiển thị; phần lỗi hiển thị trạng thái chưa tải được và cho phép tải lại.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả dữ liệu tài khoản. |
| `ACCOUNT_NOT_ALLOWED` | 403 | Tài khoản không được phép truy cập | Không trả dữ liệu tài khoản. |
| `PLAN_CONFIGURATION_ERROR` | 500 | Không xác định được gói hoặc cấu hình gói | Báo lỗi phần gói; không tự chuyển sang FREE. |
| `QUOTA_DATA_ERROR` | 500 | Không xác định được kỳ hoặc dữ liệu quota không hợp lệ | Báo lỗi phần quota; không tự coi là hết lượt. |
| `CREDIT_DATA_ERROR` | 500 | Không xác định được số dư đáng tin cậy | Báo lỗi phần số dư; không tự trả 0. |

## Business rule

| # | Rule |
| --- | --- |
| BR-093-1 | PAYMENT_SCOPE đã chốt: tiền thật thu ngoài hệ thống; CNHSK quản lý mã thẻ, điểm và quyền sử dụng, không tích hợp cổng thanh toán. |
| BR-093-2 | Người dùng chỉ được xem gói, điểm và quota của chính mình. Endpoint dạng `/me`, không nhận `user_id` từ client. |
| BR-093-3 | Gói FREE có 10 lượt cho mỗi tính năng trong mỗi tháng dương lịch; gói trả phí dùng hạn mức được cấu hình cho gói đang có hiệu lực. |
| BR-093-4 | Tra từ điển, xem lịch sử AI, xem lịch sử giao dịch hoặc các thao tác đọc dữ liệu nội bộ không được tính vào quota tốn phí. |
| BR-093-5 | Gói hết hạn được coi là gói miễn phí. Hệ thống giữ lịch sử gói cũ, không xóa dữ liệu gói đã dùng. |
| BR-093-6 | Số dư điểm không được âm. Nếu còn dùng bảng điểm, database phải chặn `balance < 0`. |
| BR-093-7 | Không dùng kiểu số thực cho điểm hoặc tiền. Điểm dùng số nguyên; giá tiền nếu có dùng kiểu số chính xác. |
| BR-093-8 | Kỳ quota FREE là tháng dương lịch, reset lúc 00:00 ngày 1 theo giờ Việt Nam; các tính năng có bộ đếm độc lập. |

## API · DB

```
GET /api/me/subscription
GET /api/me/credits
```

Đọc `users.plan_code`, `plan_expires_at`, `free_usage`, `usage_reset_at`,
`plans` và `credit_transactions` qua API nội bộ của module sở hữu.
Các cột gói/quota và bảng `plans` là thiết kế chờ migration, không khẳng định đã có trong V1.
Không dùng `user_subscriptions`, `user_credits` hoặc `feature_usage` làm nguồn dữ liệu.
Thứ tự giao dịch phải ổn định theo thiết kế ledger; không chỉ dựa vào thời gian tạo có thể trùng.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tài khoản mới, FREE, chưa có giao dịch | Số dư 0; quota đúng cấu hình; không tạo dữ liệu. |
| T2 | Gửi kèm `user_id` người khác | Không đổi chủ thể truy vấn; không lộ dữ liệu người khác. |
| T3 | Hạn gói bằng thời điểm kiểm tra, job chưa chạy | Quyền được xác định theo FREE. |
| T4 | Gói trả phí còn hạn | Trả hạn mức cấu hình, không mặc định null/không giới hạn. |
| T5 | FREE đã dùng 3 lượt dịch trong kỳ | Còn 7 lượt dịch, các tính năng khác tính độc lập. |
| T6 | Lượt và số dư đều bằng 0 | Xem thông tin thành công, không trả 402. |
| T7 | Giao dịch số dư hoặc cấu hình bị lỗi | Báo lỗi, không tự trả số dư 0/gói FREE. |
| T8 | Qua đầu tháng, bộ đếm lưu còn thuộc kỳ trước | Không dùng số tháng trước làm lượt đã dùng kỳ mới; GET không ghi reset. |
| T9 | API điểm lỗi, API gói thành công | Hiển thị gói; phần điểm báo chưa tải được. |
| T10 | Mở màn hình nhiều lần | Không đổi số dư, bộ đếm hay lịch sử giao dịch. |

---

# UC-094 · Đổi mã CREDIT để cộng điểm

| | |
|---|---|
| **UC-ID** | UC-094 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Người dùng nhập mã thẻ loại `CREDIT` đã nhận từ kênh phân phối bên ngoài để đổi lấy điểm trong tài khoản CNHSK. Tiền thật được thu ngoài hệ thống; UC này không kết nối cổng thanh toán và không xác nhận giao dịch tiền bên ngoài.

Giá trị điểm luôn lấy từ dữ liệu mã do hệ thống quản lý. Client không được gửi số điểm cần cộng làm căn cứ xử lý. Mã hợp lệ, chưa dùng và còn hạn là các điều kiện được kiểm tra **trong luồng**, không phải tiền điều kiện của người dùng.

## Tiền điều kiện

1. Người dùng đã đăng nhập bằng tài khoản hợp lệ và được phép thực hiện thao tác nạp mã.
2. Với web sử dụng cookie, request thay đổi dữ liệu có CSRF token hợp lệ.
3. Hệ thống có dữ liệu mã thẻ và cơ chế ghi sổ cái điểm đang hoạt động.
4. Người dùng không cần từng có giao dịch hoặc số dư trước đó.

## Hậu điều kiện

### Thành công

1. Mã được chuyển sang trạng thái `USED` và ghi người sử dụng, thời điểm sử dụng.
2. Một giao dịch `TOPUP` được tạo với đúng số điểm của mã.
3. `balance_after = balance_before + amount`.
4. Mã không thể được sử dụng lần thứ hai.
5. Không lưu hoặc ghi log mã thô.

### Không thành công

1. Mã không bị tiêu thụ.
2. Không tạo `TOPUP` và số dư không thay đổi.
3. Lỗi hạ tầng không bị tính thành một lần nhập sai mã.
4. Không có trạng thái tài chính dở dang như mã đã dùng nhưng chưa cộng điểm hoặc ngược lại.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nhập mã và xác nhận nạp điểm. |
| 2 | Client | Gửi yêu cầu đổi mã kèm CSRF nếu là web dùng cookie. |
| 3 | System | Kiểm tra xác thực, quyền thao tác và giới hạn số lần nhập sai của tài khoản. |
| 4 | System | Kiểm tra định dạng mã, tính hash để tìm mã tương ứng; không log mã thô. |
| 5 | System | Kiểm tra mã ở trạng thái `UNUSED` và chưa hết hạn nếu mã có ngày hết hạn. |
| 6 | System | Xác định mã thuộc loại `CREDIT` và lấy giá trị điểm từ dữ liệu server. |
| 7 | System | Lấy số dư hiện tại của người dùng. |
| 8 | System | Ghi một giao dịch `TOPUP` với số dư trước và sau chính xác. |
| 9 | System | Chuyển mã sang `USED`, ghi người dùng và thời điểm sử dụng. |
| 10 | System | Hoàn tất các thay đổi như một thao tác nhất quán. |
| 11 | Client | Hiển thị số điểm vừa được cộng và số dư mới. |

## Luồng thay thế

**A1 — Mã không tồn tại, đã dùng, hết hạn hoặc đã bị vô hiệu hóa:** trả cùng một thông báo `INVALID_CARD_CODE`; không tiết lộ trạng thái thật của mã hoặc ai đã dùng mã.

**A2 — Vượt giới hạn nhập sai:** sau 5 lần nhập mã không hợp lệ trong một giờ, tài khoản bị chặn thử mã mới cho đến hết cửa sổ giới hạn. Đổi IP không reset bộ đếm.

**A3 — Hai request cùng dùng một mã:** chỉ một request được sử dụng mã thành công; request còn lại không được cộng điểm.

**A4 — Hai mã khác nhau được nạp đồng thời cho cùng tài khoản:** cả hai giao dịch hợp lệ vẫn phải nối tiếp đúng chuỗi số dư, không làm mất một khoản cộng.

**A5 — Mã là loại `SUBSCRIPTION`:** chuyển sang xử lý theo UC-095; không cộng điểm như CREDIT.

**A6 — Client mất response sau khi hệ thống đã commit:** gửi lại cùng mã không được cộng thêm điểm; người dùng kiểm tra số dư hoặc lịch sử giao dịch.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | Không xử lý mã. |
| `FORBIDDEN` | 403 | Tài khoản không được phép thao tác | Không xử lý mã. |
| `CSRF_TOKEN_MISSING` | 403 | Request web thiếu CSRF hợp lệ | Không xử lý mã. |
| `INVALID_CARD_CODE` | 400 | Mã không thể sử dụng | Trả thông báo chung; không cộng điểm. |
| `TOO_MANY_FAILED_ATTEMPTS` | 429 | Vượt giới hạn nhập sai | Tạm chặn thử mã mới. |
| `CREDIT_PROCESSING_ERROR` | 500 | Không thể cập nhật mã và sổ cái nhất quán | Rollback toàn bộ thay đổi. |

## Business rule

| # | Rule |
| --- | --- |
| BR-094-1 | Mã CREDIT được phát hành để phân phối ngoài hệ thống; CNHSK chỉ xác minh mã và cộng điểm, không thực hiện thu tiền qua cổng thanh toán. |
| BR-094-2 | Mã thẻ chỉ được dùng một lần. Sau khi dùng thành công, mã phải chuyển sang trạng thái đã dùng trong cùng thao tác cộng điểm. |
| BR-094-3 | Hệ thống không bao giờ lưu mã thẻ dạng thô. Chỉ lưu giá trị đã băm của mã. |
| BR-094-4 | Hệ thống không được ghi mã thẻ vào log, kể cả log lỗi, log request body hoặc exception message. |
| BR-094-5 | Khi người dùng nhập mã sai, mã đã dùng hoặc mã hết hạn, hệ thống trả một thông báo chung để tránh dò mã. |
| BR-094-6 | Mỗi tài khoản bị giới hạn số lần nhập sai trong một khoảng thời gian. Đề xuất 5 lần/giờ theo `user_id`. |
| BR-094-7 | Việc đổi trạng thái mã, cộng điểm và ghi giao dịch phải xảy ra nhất quán. Không được cộng điểm mà mã vẫn còn dùng được, hoặc mã đã dùng nhưng điểm chưa cộng. |
| BR-094-8 | Mọi thay đổi điểm phải ghi một dòng giao dịch có số dư trước và số dư sau. |
| BR-094-9 | Endpoint nạp mã là thao tác thay đổi giá trị điểm; với web dùng cookie, request bắt buộc có CSRF token hợp lệ. |
| BR-094-10 | Chỉ một service trung tâm được phép thay đổi số dư điểm. Không để nhiều module tự cập nhật trực tiếp số dư. |

## API · DB

```
POST /api/credits/redeem
```

| Bảng | Vai trò |
| --- | --- |
| `credit_cards` | Đọc có khóa; cập nhật trạng thái và người/thời điểm sử dụng. |
| `credit_transactions` | Đọc số dư; chỉ INSERT TOPUP, không UPDATE/DELETE giao dịch cũ. |
| `audit_logs` | Ghi truy vết theo chính sách, gồm nguồn phân phối khi áp dụng; bảo vệ PII và mã thẻ. |

Ghi điểm đi qua service trung tâm. Cơ chế chống đua theo tài khoản phải bao phủ cả
giao dịch đầu tiên; cột `seq`/unique theo thiết kế chưa có trong V1, không coi khóa
một dòng ledger cũ là giải pháp đầy đủ.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | CREDIT hợp lệ 100 điểm | Một TOPUP +100; thẻ USED; số dư trước/sau đúng. |
| T2 | Mã sai, USED, hết hạn hoặc VOIDED | Cùng thông báo; không cộng điểm hoặc lộ người đã dùng. |
| T3 | Mã hợp lệ không có hạn | Nạp được; không bị loại do so sánh NULL. |
| T4 | Hai request cùng mã | Chỉ một TOPUP và một lần dùng mã. |
| T5 | Hai mã khác nhau nạp đồng thời cho tài khoản mới | Không mất điểm; chuỗi số dư liên tục. |
| T6 | Đã nhập sai 5 lần, đổi IP rồi gửi tiếp | 429; bộ đếm vẫn theo tài khoản. |
| T7 | Thiếu/sai CSRF trên web | 403; không đổi thẻ hoặc ledger. |
| T8 | Lỗi ghi ledger/truy vết bắt buộc | Rollback; thẻ còn UNUSED; không tính thành sai mã. |
| T9 | Đọc DB và log sau cả thành công/lỗi | Không có mã thô; log lỗi không chứa request body nhạy cảm. |
| T10 | Commit thành công nhưng mất response, gửi lại | Không cộng lần hai; lịch sử có đúng một TOPUP. |
| T11 | Nhập thẻ SUBSCRIPTION | Sang UC-095, không tạo TOPUP. |

---

# UC-095 · Đổi mã SUBSCRIPTION để kích hoạt hoặc gia hạn gói

| | |
|---|---|
| **UC-ID** | UC-095 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Người dùng nhập mã thẻ loại `SUBSCRIPTION` để kích hoạt một gói dịch vụ hoặc gia hạn cùng gói đang còn hiệu lực. Thời lượng của mã được tính theo **số ngày** được cấu hình; không tự hiểu “gói tháng” là tháng dương lịch.

Nếu người dùng đang dùng cùng gói và gói còn hạn, thời lượng mới được cộng tiếp từ ngày hết hạn hiện tại để không làm mất thời gian đã có. Chính sách chuyển giữa hai gói trả phí khác loại chưa được chốt nên nhánh đó không được tự suy diễn.

## Tiền điều kiện

1. Người dùng đáp ứng điều kiện xác thực và bảo vệ thao tác đổi mã của UC-094.
2. Hệ thống xác định được gói và số ngày sử dụng gắn với mã SUBSCRIPTION.
3. Hệ thống có đủ dữ liệu để bảo toàn quyền lợi đã phát hành cho mã.
4. Việc mã tồn tại, chưa dùng và còn hạn được kiểm tra trong luồng, không giả định trước.

## Hậu điều kiện

### Thành công

1. Mã chuyển sang `USED` và ghi người sử dụng, thời điểm sử dụng.
2. Gói và ngày hết hạn của tài khoản được cập nhật.
3. Lịch sử kích hoạt/gia hạn được ghi để đối soát.
4. Số dư điểm không thay đổi.
5. Gia hạn cùng gói không làm mất thời gian còn lại.

### Không thành công

1. Mã không bị tiêu thụ.
2. Gói và ngày hết hạn cũ được giữ nguyên.
3. Không tạo lịch sử kích hoạt thành công một phần.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nhập mã gói và xác nhận sử dụng. |
| 2 | System | Thực hiện các kiểm tra chung về mã như UC-094. |
| 3 | System | Xác định mã thuộc loại `SUBSCRIPTION`; lấy gói và số ngày từ dữ liệu server. |
| 4 | System | Xác định gói hiện tại và thời hạn hiện tại của tài khoản. |
| 5 | System | Nếu tài khoản không có cùng gói trả phí còn hạn, dùng thời điểm kích hoạt làm mốc bắt đầu. |
| 6 | System | Nếu tài khoản đang có cùng gói còn hạn, dùng ngày hết hạn hiện tại làm mốc gia hạn. |
| 7 | System | Cộng số ngày của mã vào mốc đã xác định. |
| 8 | System | Cập nhật gói, ngày hết hạn, trạng thái mã và lịch sử trong cùng một thao tác nhất quán. |
| 9 | Client | Hiển thị tên gói và ngày hết hạn mới theo giờ Việt Nam. |

## Luồng thay thế

**A1 — Gia hạn cùng gói còn hiệu lực:** hạn mới bằng hạn cũ cộng số ngày của mã; không mất phần thời gian còn lại.

**A2 — Gói cũ đã hết hạn hoặc tài khoản đang FREE:** thời hạn mới tính từ thời điểm sử dụng mã.

**A3 — Mã là CREDIT:** chuyển sang UC-094; không kích hoạt gói.

**A4 — Đang có một gói trả phí khác loại:** không tự động nâng/hạ gói, quy đổi ngày hoặc ghi đè quyền hiện tại. Nhánh này chỉ được triển khai sau khi chính sách chuyển gói được phê duyệt.

**A5 — Gói đã ngừng cung cấp mới nhưng mã hợp lệ đã được phát hành trước đó:** không tự hủy quyền lợi của mã; xử lý theo quyền lợi đã được cấp cho mã nếu dữ liệu lịch sử xác định được.

**A6 — Hai mã gia hạn hợp lệ được dùng đồng thời:** cả hai lần gia hạn phải được cộng nối tiếp trên hạn mới nhất; không làm mất một lần gia hạn.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| Các lỗi đổi mã | Theo UC-094 | Mã không qua kiểm tra chung | Không kích hoạt hoặc gia hạn. |
| `PLAN_NOT_FOUND` | 500 | Không xác định được gói của mã | Không tiêu thụ mã. |
| `PLAN_CONFIGURATION_ERROR` | 500 | Không xác định được thời lượng/quyền lợi | Không tiêu thụ mã. |
| `PLAN_SWITCH_POLICY_NOT_DEFINED` | 409 | Đang có gói trả phí khác loại và chưa có chính sách chuyển gói | Giữ nguyên trạng thái hiện tại. |
| `SUBSCRIPTION_PROCESSING_ERROR` | 500 | Không thể cập nhật gói, lịch sử và trạng thái mã nhất quán | Rollback toàn bộ. |

## Business rule

| # | Rule |
| --- | --- |
| BR-095-1 | Mã SUBSCRIPTION được dùng để kích hoạt/gia hạn gói theo số ngày và quyền lợi được cấu hình; tiền thật được thu ngoài CNHSK. |
| BR-095-2 | Mã kích hoạt gói tháng áp dụng đầy đủ các rule bảo vệ mã thẻ của UC-094. |
| BR-095-3 | Khi kích hoạt gói thành công, mã phải chuyển sang trạng thái đã dùng và gói người dùng được cập nhật trong cùng một thao tác nhất quán. |
| BR-095-4 | Nếu người dùng đang còn hạn gói, thời hạn mới phải được cộng tiếp vào hạn cũ, không ghi đè từ ngày hiện tại làm mất phần đã mua. |
| BR-095-5 | Nếu gói cũ đã hết hạn, thời hạn mới được tính từ thời điểm kích hoạt. |
| BR-095-6 | Kích hoạt gói cũng phải ghi giao dịch hoặc lịch sử để người dùng và quản trị có thể đối soát. |
| BR-095-7 | Không xóa gói dịch vụ nếu còn mã chưa dùng hoặc người dùng đang sử dụng gói đó. |
| BR-095-8 | Thời điểm hết hạn gói phải được lưu nhất quán và hiển thị cho người dùng theo giờ Việt Nam. |

## API · DB

```
POST /api/credits/redeem      (cùng endpoint, phân nhánh theo card_type)
GET  /api/me/subscription
```

`credit_cards`, `users`, `plans`, `credit_transactions`; truy cập xuyên module qua API nội bộ.
Không có bảng `user_subscriptions` hay `user_credits`.
Liên kết thẻ với mã gói và nơi lưu snapshot quyền lợi/giá trị lịch sử chưa được mô tả
đầy đủ trong schema hiện có; cần thiết kế được duyệt trước triển khai, không tự thêm cột
hoặc suy ra gói từ `value`. Giá niêm yết không chứng minh số tiền thực thu ngoài hệ thống.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | FREE, thẻ gói hợp lệ 30 ngày | Hạn mới tính từ thời điểm kích hoạt cộng 30 ngày. |
| T2 | Cùng gói còn 20 ngày, nạp thêm 30 ngày | Hạn cũ cộng 30 ngày, không mất 20 ngày. |
| T3 | Gói vừa hết hạn đúng thời điểm kiểm tra | Tính từ thời điểm kích hoạt. |
| T4 | Kích hoạt ở cuối tháng | Cộng đúng số ngày thẻ, không tự chuyển thành tháng lịch. |
| T5 | Hai request cùng mã | Một lần tiêu thụ, một lần gia hạn. |
| T6 | Hai mã khác nhau cùng gia hạn | Cộng đủ thời lượng cả hai, lịch sử đủ hai lần. |
| T7 | Lỗi ghi SUBSCRIPTION | Gói/hạn và thẻ đều rollback. |
| T8 | Gia hạn thành công | Số dư điểm và số lượt đã dùng không bị reset. |
| T9 | Không xác định được gói/quyền đã cấp | Không tiêu thụ thẻ, không gán gói mặc định. |
| T10 | Kiểm lịch sử sau kích hoạt | Có SUBSCRIPTION amount 0; số dư trước bằng số dư sau. |
| T11 | Gia hạn đồng thời với TOPUP/DEDUCT của cùng tài khoản | SUBSCRIPTION nối đúng số dư mới nhất, không ghi dòng amount 0 với số dư cũ làm mất hiệu lực điểm đã nạp/trừ. |

---

# UC-096 · Xác định và tiêu thụ quyền sử dụng cho tính năng tốn phí

| | |
|---|---|
| **UC-ID** | UC-096 · **Actor** Supporting UC (được gọi từ UC khác) · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

UC hỗ trợ nội bộ này được các chức năng có chi phí gọi trước khi thực hiện hành động tốn phí: AI sinh bài, dịch, trợ lý AI và gửi yêu cầu chấm bài.

Hệ thống xác định người dùng còn lượt theo gói/quota hay phải dùng điểm. Một hành động chỉ được tiêu thụ **một** nguồn: nếu còn lượt thì dùng lượt; nếu hết lượt mới dùng điểm. Trong chính sách hiện tại, khi phải dùng điểm thì chi phí là 1 điểm cho một lượt của tính năng. Không thu đồng thời cả lượt và điểm.

Đây không phải Use Case do người dùng trực tiếp khởi chạy; nó là nghiệp vụ dùng chung được Use Case gọi `<<include>>`.

## Tiền điều kiện

1. Use Case gọi đã xác định được người dùng hiện tại và xác nhận người dùng đủ quyền thực hiện hành động.
2. Tính năng gọi thuộc danh sách tính năng có giới hạn sử dụng.
3. Hành động có định danh ổn định để nhận biết retry và chống tiêu thụ hai lần.
4. Use Case gọi chưa thực hiện phần phát sinh chi phí bên ngoài trước khi UC này cho phép.

## Hậu điều kiện

### Còn lượt trong gói/quota

1. Đúng một lượt của tính năng được ghi nhận đã sử dụng trong kỳ hiện tại.
2. Số dư điểm không thay đổi.
3. Không tạo giao dịch `DEDUCT`.

### Hết lượt nhưng còn điểm

1. Tạo đúng một giao dịch `DEDUCT` với amount theo chính sách hiện tại.
2. Số dư sau giao dịch được cập nhật chính xác và không âm.
3. Không tăng thêm bộ đếm quota cho cùng hành động.

### Không đủ quyền sử dụng

1. Không thay đổi quota hoặc số dư.
2. Use Case gọi không được thực hiện hành động tốn phí.

### Retry cùng hành động

1. Trả lại kết quả tiêu thụ đã ghi nhận.
2. Không tiêu thụ lần thứ hai.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Use Case gọi | Yêu cầu kiểm tra và tiêu thụ quyền sử dụng cho một hành động cụ thể. |
| 2 | System | Kiểm tra định danh hành động; nếu đã xử lý thì trả kết quả trước đó. |
| 3 | System | Xác định gói đang có hiệu lực và kỳ quota hiện tại của người dùng. |
| 4 | System | Lấy hạn mức và số lượt đã dùng của đúng tính năng. |
| 5 | System | Nếu còn lượt, tăng đúng một lượt đã dùng, ghi nguồn tiêu thụ là quota và kết thúc UC. |
| 6 | System | Nếu hết lượt, lấy số dư điểm hiện tại. |
| 7 | System | Nếu còn đủ điểm, ghi một giao dịch `DEDUCT`, ghi nguồn tiêu thụ là điểm và kết thúc UC. |
| 8 | System | Nếu hết lượt và không đủ điểm, từ chối quyền sử dụng. |
| 9 | System | Trả cho Use Case gọi biết nguồn đã sử dụng hoặc lý do bị từ chối. |

## Luồng thay thế

**A1 — Gói trả phí:** dùng hạn mức thực tế được cấu hình cho gói đang có hiệu lực; không mặc định gói trả phí là không giới hạn và không cộng thêm quỹ FREE riêng.

**A2 — Hành động không phát sinh chi phí:** không gọi UC-096. Ví dụ tra từ điển nội bộ, xem lịch sử, xem kết quả cũ hoặc bản dịch được phục vụ hoàn toàn từ cache theo chính sách đã chốt.

**A3 — Retry cùng hành động:** cùng định danh và cùng nội dung trả kết quả cũ, không tiêu thụ lại.

**A4 — Cùng định danh nhưng khác người dùng/tính năng/nội dung:** đây không phải retry hợp lệ; từ chối để tránh dùng nhầm kết quả cũ.

**A5 — API ngoài thất bại sau khi quyền sử dụng đã được tiêu thụ:** Use Case gọi xử lý retry theo chính sách của tính năng; khi xác định thất bại cuối cùng thì gọi UC-097.

## Bảng exception

| Mã lỗi | HTTP/Phạm vi | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INSUFFICIENT_CREDITS` | 402 qua API caller | Hết lượt và không đủ điểm | Từ chối trước khi thực hiện hành động tốn phí. |
| `FEATURE_CODE_UNKNOWN` | Nội bộ | Tính năng chưa có chính sách | Không tự dùng quota/giá của tính năng khác. |
| `IDEMPOTENCY_CONFLICT` | 409 qua API caller | Cùng khóa nhưng khác hành động | Không tiêu thụ thêm. |
| `USAGE_DATA_ERROR` | Nội bộ | Không xác định được quota hoặc số dư đáng tin cậy | Không cho miễn phí và không trừ tùy ý. |

## Business rule

| # | Rule |
| --- | --- |
| BR-096-1 | PAYMENT_SCOPE đã chốt: tính năng tốn phí dùng hạn mức của gói đang có hiệu lực; khi hết hạn mức mới dùng điểm theo chính sách đã duyệt. |
| BR-096-2 | Chỉ các hành động gọi API ngoài hoặc tiêu tốn chi phí vận hành thật mới được tính quota hoặc trừ điểm. |
| BR-096-3 | Tra từ điển nội bộ, xem lịch sử, xem kết quả cũ, bấm từ trong kết quả dịch hoặc đọc dữ liệu đã lưu không được trừ điểm. |
| BR-096-4 | Thứ tự xử lý là: hạn mức của gói đang có hiệu lực → điểm → chặn. Không cộng thêm một quỹ FREE riêng cho tài khoản đang có gói trả phí còn hạn. |
| BR-096-5 | Dùng quota miễn phí thì không được trừ thêm điểm. |
| BR-096-6 | Trừ điểm phải ghi giao dịch có số dư trước và số dư sau. |
| BR-096-7 | Mỗi hành động tốn phí phải có khóa idempotency để tránh retry làm trừ hai lần. |
| BR-096-8 | Khi hết quota và hết điểm, hệ thống phải từ chối trước khi gọi API ngoài. Không được gọi API rồi mới báo hết điểm. |
| BR-096-9 | Không được để số dư âm. |

## API · DB

Service nội bộ, không endpoint.

Đọc/cập nhật quota trên `users` qua API module sở hữu; đọc `plans`;
chỉ append `credit_transactions` khi dùng điểm; ghi liên hệ nguồn tiêu thụ trong dữ liệu
hành động tương ứng. Không dùng `user_subscriptions`, `feature_usage` hoặc `user_credits`.

Thiết kế lưu chống lặp và nguồn tiêu thụ phải bao phủ cả bốn tính năng, cả nhánh quota
không có giao dịch. Không coi một dòng DEDUCT hay `request_id` HTTP ngẫu nhiên mỗi lần retry
là giải pháp đầy đủ. Những chi tiết lưu trữ chưa có phải được duyệt trước triển khai;
UC này không thêm bảng/cột hoặc thay contract của caller.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Gói trả phí còn lượt | Tiêu thụ một lượt đúng cấu hình, không giảm điểm. |
| T2 | FREE còn 5 lượt | Tăng số đã dùng một lượt; không có DEDUCT. |
| T3 | Hết lượt, số dư 10 | Một DEDUCT -1, số dư sau 9. |
| T4 | Hết lượt, số dư 0 | 402; API ngoài chưa được gọi, không tạo yêu cầu thành công. |
| T5 | Gọi ngoài transaction | Không ghi dữ liệu, báo lỗi nội bộ. |
| T6 | Hai request cùng khóa và hành động | Một lần tiêu thụ, cùng kết quả ghi nhận. |
| T7 | Cùng khóa nhưng khác nội dung | 409; không sử dụng kết quả cũ hoặc trừ thêm. |
| T8 | Hai request tranh lượt/điểm cuối | Không vượt quota, không âm điểm; mỗi kết quả theo nguồn thực tế còn lại. |
| T9 | Cache hit/đọc lịch sử | Không gọi consume. |
| T10 | API retry hoặc gói hết hạn giữa hành động | Không tiêu thụ lần hai; hành động đã nhận được hoàn tất. |
| T11 | Tạo hành động lỗi sau tiêu thụ, chưa commit | Rollback cả hành động và lượt/điểm. |
| T12 | Hai request đầu tháng cạnh tranh với job reset | Chuyển kỳ nhất quán; không mất lượt đã dùng trong tháng mới. |

---

# UC-097 · Hoàn quyền sử dụng khi hành động thất bại

| | |
|---|---|
| **UC-ID** | UC-097 · **Actor** Supporting UC (được gọi từ UC khác) · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

UC hỗ trợ nội bộ này khôi phục **đúng nguồn quyền sử dụng đã bị tiêu thụ** khi một hành động tốn phí thất bại cuối cùng hoặc bị hủy/hết hạn theo quy tắc nghiệp vụ.

Nếu hành động đã dùng điểm, hệ thống tạo một giao dịch hoàn. Nếu hành động đã dùng quota, hệ thống hoàn quota tương ứng. UC này không dùng để cộng điểm thủ công và không được tự quyết định số hoàn dựa trên dữ liệu client gửi lên.

## Tiền điều kiện

1. Use Case gọi xác định được hành động cần hoàn, người dùng và tính năng liên quan.
2. Có bằng chứng về nguồn đã tiêu thụ cho hành động đó.
3. Hành động đã thất bại cuối cùng hoặc đạt điều kiện hủy/hết hạn hợp lệ; không còn trong giai đoạn retry có thể thành công.
4. Hành động chưa được hoàn trước đó.

## Hậu điều kiện

### Nếu nguồn là điểm

1. Tạo đúng một giao dịch `REFUND` tham chiếu tới `DEDUCT` gốc.
2. Số điểm hoàn lấy từ giao dịch gốc, không lấy từ amount do client cung cấp.
3. Giao dịch `DEDUCT` gốc vẫn được giữ nguyên.

### Nếu nguồn là quota

1. Khôi phục đúng lượt đã tiêu thụ theo chính sách của kỳ tương ứng.
2. Không tạo điểm mới và không tạo REFUND tiền/điểm cho cùng lần tiêu thụ quota.

### Nếu đã hoàn trước đó

1. Không hoàn lần thứ hai.
2. Trả lại kết quả hoàn đã ghi nhận.

### Nếu không xác định được nguồn đã tiêu thụ

1. Không tự cộng quota hoặc điểm.
2. Ghi nhận trường hợp cần đối soát.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Use Case gọi | Yêu cầu hoàn quyền sử dụng cho một hành động đã thất bại hoặc kết thúc theo điều kiện được hoàn. |
| 2 | System | Tìm bằng chứng tiêu thụ và kiểm người dùng, tính năng, trạng thái hành động. |
| 3 | System | Kiểm tra hành động đã được hoàn trước đó chưa. |
| 4 | System | Nếu nguồn là điểm, lấy `DEDUCT` gốc và xác định số hoàn từ giao dịch đó. |
| 5 | System | Ghi một `REFUND` vào số dư hiện tại và liên kết với giao dịch gốc. |
| 6 | System | Nếu nguồn là quota, phục hồi đúng lượt đã tiêu thụ thay vì cộng điểm. |
| 7 | System | Đánh dấu lần tiêu thụ đã được hoàn để chống xử lý lặp. |
| 8 | System | Trả kết quả cho Use Case gọi. |

## Luồng thay thế

**A1 — Hành động dùng quota:** hoàn quota; không yêu cầu phải có DEDUCT và không tạo REFUND điểm.

**A2 — Hành động thực tế chưa tiêu thụ nguồn nào:** không hoàn gì.

**A3 — Hành động vẫn đang retry hoặc đã thành công:** không hoàn.

**A4 — Có giao dịch khác sau DEDUCT gốc:** cộng khoản hoàn vào số dư hiện tại; không ghi đè số dư về giá trị cũ.

**A5 — Quota thuộc kỳ đã kết thúc:** không tự sửa quota của kỳ mới và không quy đổi thành điểm. Nhánh này chỉ triển khai sau khi chính sách hoàn qua kỳ được phê duyệt.

**A6 — Hai tiến trình cùng hoàn một hành động:** chỉ một lần hoàn có hiệu lực; lần còn lại nhận kết quả đã có.

## Bảng exception

| Mã lỗi | Phạm vi | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ORIGINAL_USAGE_NOT_FOUND` | Nội bộ | Không tìm thấy bằng chứng tiêu thụ | Không hoàn tùy ý; đưa vào đối soát. |
| `REFUND_AFTER_SUCCESS` | Nội bộ | Hành động đã thành công | Không hoàn. |
| `REFUND_SOURCE_MISMATCH` | Nội bộ | Nguồn cần hoàn không khớp bằng chứng gốc | Không thay đổi quota/điểm. |
| `REFUND_POLICY_NOT_DEFINED` | Nội bộ | Cần hoàn quota qua kỳ nhưng chưa có chính sách | Không tự sửa kỳ mới; chặn nhánh này. |
| `REFUND_PROCESSING_ERROR` | Nội bộ | Không thể hoàn nhất quán | Không đánh dấu đã hoàn; cho phép xử lý lại. |

## Business rule

| # | Rule |
| --- | --- |
| BR-097-1 | UC này áp dụng cho mọi hành động đã tiêu thụ quota hoặc điểm nhưng sau đó đủ điều kiện được hoàn theo quy tắc nghiệp vụ. |
| BR-097-2 | Nếu một hành động đã bị trừ điểm nhưng sau đó thất bại do lỗi hệ thống hoặc API ngoài, người dùng phải được hoàn lại đúng phần đã bị trừ. |
| BR-097-3 | Hoàn điểm không được sửa hoặc xóa giao dịch trừ điểm cũ. Hệ thống phải tạo một giao dịch hoàn mới để giữ lịch sử đầy đủ. |
| BR-097-4 | Số điểm hoàn phải lấy từ giao dịch trừ điểm gốc, không lấy từ tham số client gửi lên. |
| BR-097-5 | Một giao dịch trừ điểm chỉ được hoàn một lần. Gọi hoàn nhiều lần phải cho kết quả idempotent. |
| BR-097-6 | Nếu hành động dùng quota miễn phí thay vì điểm, hệ thống hoàn quota tương ứng thay vì cộng điểm. |
| BR-097-7 | Hệ thống chỉ hoàn khi xác định hành động thật sự thất bại. Không hoàn cho hành động đã xử lý thành công. |
| BR-097-8 | Cần có báo cáo hoặc kiểm tra định kỳ để phát hiện hành động đã trừ điểm nhưng thất bại mà chưa được hoàn. |

## API · DB

Service nội bộ; `credit_transactions`, quota trên `users` qua API module sở hữu,
và dữ liệu hành động/bằng chứng tiêu thụ. Không dùng `user_credits` hoặc `feature_usage`.

`ref_transaction_id` và unique index lọc `tx_type = 'REFUND'` **đã có trong V1**.
Chúng không thay thế cơ chế chống hoàn quota lặp. Transaction của UC-103/UC-107 phải bao
gồm thay đổi trạng thái và hoàn, không commit hoàn độc lập rồi để trạng thái rollback.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | DEDUCT -1, hành động thất bại cuối cùng | Một REFUND +1 trỏ đúng dòng gốc. |
| T2 | Hai lần gọi hoàn đồng thời | Một lần hoàn có hiệu lực, lần còn lại nhận kết quả đã có. |
| T3 | Không có bằng chứng tiêu thụ điểm | Không cộng điểm; ghi lỗi đối soát. |
| T4 | Caller/client cố quyết định số hoàn lớn hơn | Chỉ dùng DEDUCT gốc; không cộng số không có căn cứ. |
| T5 | Kiểm DEDUCT sau hoàn | Dòng gốc vẫn nguyên vẹn. |
| T6 | Lượt quota đã dùng trong kỳ hiện tại | Hoàn lượt đúng một lần, số dư điểm không đổi. |
| T7 | Không tiêu thụ gì | Không phát sinh REFUND hay quota mới. |
| T8 | Retry đang chạy hoặc đã thành công | Không hoàn. |
| T9 | Có TOPUP/DEDUCT khác xen giữa trước khi hoàn | Cộng vào số dư hiện tại, không ghi đè về số dư cũ. |
| T10 | Hoàn lỗi trong transaction hết hạn/hủy | Cả hoàn và chuyển trạng thái rollback. |
| T11 | Gửi thông báo thất bại sau commit | Không hoàn lần hai. |
| T12 | Quota thuộc kỳ trước | Không sửa bộ đếm kỳ mới; cần chính sách đã duyệt trước kiểm thử nghiệm thu nhánh này. |

---

# UC-098 · Xem lịch sử giao dịch điểm của mình

| | |
|---|---|
| **UC-ID** | UC-098 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Người dùng xem lịch sử các giao dịch điểm thuộc tài khoản của mình để biết điểm đã được cộng, trừ, hoàn hoặc điều chỉnh vì lý do gì. Đây là dữ liệu chỉ đọc; người dùng không được sửa hoặc xóa giao dịch.

## Tiền điều kiện

1. Người dùng đã đăng nhập và được phép truy cập dữ liệu cá nhân.
2. Không yêu cầu tài khoản phải từng có giao dịch hoặc còn số dư.

## Hậu điều kiện

### Thành công

1. Chỉ giao dịch của người dùng hiện tại được trả về.
2. Mỗi giao dịch hiển thị loại, amount có dấu, số dư trước/sau, lý do công khai và thời điểm.
3. Không thay đổi giao dịch, quota hoặc số dư.

### Không thành công

1. Không trả giao dịch của người dùng khác.
2. Không thay đổi dữ liệu.
3. Lỗi đọc dữ liệu không được hiển thị thành “chưa có giao dịch”.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang “Lịch sử giao dịch”. |
| 2 | Client | Gửi yêu cầu danh sách với tham số phân trang và bộ lọc loại giao dịch nếu có. |
| 3 | System | Xác định người dùng từ phiên đăng nhập và kiểm tra tham số. |
| 4 | System | Lấy các giao dịch chỉ thuộc tài khoản đó. |
| 5 | System | Sắp xếp giao dịch mới nhất trước theo thứ tự ổn định và phân trang. |
| 6 | System | Trả dữ liệu công khai của giao dịch; không trả mã thẻ, hash hoặc thông tin nội bộ. |
| 7 | Client | Hiển thị số điểm cộng/trừ bằng dấu rõ ràng và thời gian theo giờ Việt Nam. |

## Luồng thay thế

**A1 — Chưa có giao dịch hoặc bộ lọc không có kết quả:** trả danh sách rỗng hợp lệ; không coi là lỗi.

**A2 — Lọc theo loại giao dịch:** chỉ hiển thị loại được yêu cầu; không dùng danh sách đã lọc để kết luận chuỗi số dư bị đứt vì có thể có giao dịch loại khác nằm giữa.

**A3 — Giao dịch REFUND có tham chiếu:** chỉ trả thông tin tham chiếu được phép xem và cùng thuộc tài khoản hiện tại.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả lịch sử. |
| `TRANSACTIONS_NOT_OWNED` | 403 | Cố truy cập giao dịch người khác | Chặn và không tiết lộ dữ liệu. |
| `INVALID_FILTER` | 422 | Trang, kích thước trang hoặc bộ lọc sai | Trả lỗi validation. |
| `TRANSACTION_READ_ERROR` | 500 | Không đọc được sổ cái | Báo lỗi tải dữ liệu. |

## Business rule

| # | Rule |
| --- | --- |
| BR-098-1 | Lịch sử giao dịch điểm được triển khai vì PAYMENT_SCOPE đã chốt có cơ chế điểm và sổ cái giao dịch. |
| BR-098-2 | Người dùng chỉ được xem lịch sử giao dịch của chính mình. Endpoint dạng `/me`, không nhận `user_id` từ client. |
| BR-098-3 | Lịch sử phải hiển thị loại giao dịch, số điểm thay đổi, số dư trước, số dư sau, lý do và thời điểm. |
| BR-098-4 | Response lịch sử giao dịch không được chứa mã thẻ, mã hash của thẻ hoặc thông tin nội bộ không cần thiết. |
| BR-098-5 | Danh sách giao dịch phải phân trang. Không trả toàn bộ lịch sử trong một request. |
| BR-098-6 | Nếu người dùng chưa có giao dịch, hệ thống trả danh sách rỗng và hiển thị thông báo phù hợp. |
| BR-098-7 | Chuỗi số dư trong lịch sử phải liên tục; dòng sau phải khớp với số dư sau của dòng trước theo cùng người dùng. |

## API · DB

```
GET /api/me/credit-transactions
```

`credit_transactions` (đọc). V1 đã có index `(user_id, id DESC)`;
thiết kế `seq` theo tài khoản chưa được coi là migration hiện hữu.
Dùng DTO dành cho người dùng, không trả toàn bộ entity hoặc join thẻ để lộ hash.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xem lịch sử tài khoản có TOPUP, DEDUCT, REFUND | Đủ trường công khai, amount đúng dấu, số dư trước/sau đúng. |
| T2 | Hai người dùng có giao dịch | Mỗi người chỉ thấy của mình, kể cả tham chiếu REFUND. |
| T3 | Gửi `user_id` người khác | Không đổi chủ thể truy vấn. |
| T4 | Kiểm mọi trường response | Không có mã thẻ/hash hoặc thông tin lỗi nội bộ. |
| T5 | 500 giao dịch, nhiều dòng cùng thời điểm | Phân trang theo thứ tự ổn định; không trả toàn bộ. |
| T6 | Lọc chỉ REFUND trong chuỗi có các loại khác | Không báo đứt chuỗi chỉ vì dòng trung gian bị lọc. |
| T7 | SUBSCRIPTION amount 0 | Hiển thị đúng loại, số dư không đổi. |
| T8 | Không có dữ liệu và lỗi đọc DB | Hai trạng thái được phân biệt: rỗng thành công và lỗi tải dữ liệu. |
| T9 | Tham số trang/loại không hợp lệ | Lỗi validation, không vô tình truy vấn không giới hạn. |

---

# UC-099 · Sinh lô mã thẻ nạp

| | |
|---|---|
| **UC-ID** | UC-099 · **Actor** `FINANCE_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

`FINANCE_ADMIN` sinh một lô mã `CREDIT` hoặc `SUBSCRIPTION` để phân phối bên ngoài CNHSK. Hệ thống quản lý phát hành, trạng thái và việc đổi mã nhưng không xác nhận việc mua bán hoặc thu tiền ngoài hệ thống.

Mã được sinh bằng nguồn ngẫu nhiên an toàn và chỉ lưu dưới dạng hash. Mã thô chỉ xuất hiện **một lần** trong kết quả tạo lô để quản trị tải xuống; sau đó hệ thống không có chức năng xem lại mã thô.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có role `FINANCE_ADMIN`.
2. Request ghi từ web có CSRF hợp lệ.
3. Loại mã và giá trị/quyền lợi cần phát hành đã được cấu hình hợp lệ.
4. Với thẻ SUBSCRIPTION, hệ thống xác định được gói và quyền lợi được cấp cho mã.

## Hậu điều kiện

### Thành công

1. Hệ thống tạo đủ số lượng mã yêu cầu, tất cả ở trạng thái `UNUSED`.
2. Chỉ hash của mã được lưu trong hệ thống.
3. Lưu thông tin lô, người tạo, thời điểm tạo và metadata phân phối đã được duyệt.
4. Ghi audit cho lần phát hành.
5. Mã thô được trả đúng một lần trong response/CSV tạo lô.

### Không thành công

1. Không báo thành công với lô được tạo thiếu số lượng.
2. Không lưu hoặc log mã thô.
3. Nếu chưa commit thành công, không có lô phát hành một phần được coi là hợp lệ.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Chọn loại mã, số lượng, giá trị/quyền lợi, hạn nếu có và thông tin phân phối. |
| 2 | Client | Gửi yêu cầu tạo lô kèm CSRF. |
| 3 | System | Kiểm tra role, CSRF, số lượng và dữ liệu cấu hình. |
| 4 | System | Sinh đủ số lượng mã bằng `SecureRandom`, mỗi mã dài tối thiểu 16 ký tự. |
| 5 | System | Tính hash và kiểm tra không trùng trong lô cũng như dữ liệu hiện có. |
| 6 | System | Lưu đủ N mã ở trạng thái `UNUSED` cùng metadata lô. |
| 7 | System | Ghi audit, tuyệt đối không ghi mã thô. |
| 8 | System | Commit việc phát hành. |
| 9 | System | Sau commit, trả CSV/dữ liệu chứa mã thô đúng một lần. |
| 10 | `FINANCE_ADMIN` | Tải và bảo quản file mã bên ngoài hệ thống. |

## Luồng thay thế

**A1 — Va chạm mã/hash:** sinh lại mã bị trùng; không giảm số lượng lô và không trả lô thiếu mã.

**A2 — Không đặt hạn:** `expires_at = NULL`, mã không tự hết hạn.

**A3 — Vô hiệu lô:** chỉ mã `UNUSED` được chuyển sang `VOIDED`; mã `USED` giữ nguyên và không làm mất quyền đã nạp.

**A4 — Mất CSV/response sau commit:** không thể lấy lại mã thô. Quản trị có thể kiểm tra trạng thái lô, vô hiệu phần chưa dùng và tạo lô khác nếu cần.

**A5 — Redeem đồng thời với thao tác vô hiệu:** mỗi mã chỉ có một kết quả trạng thái hợp lệ; không được vừa `USED` vừa bị ghi đè `VOIDED`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Chưa xác thực | Không phát hành. |
| `FORBIDDEN_ROLE` | 403 | Không có FINANCE_ADMIN | Chặn. |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu/sai CSRF | Không thay đổi lô. |
| `BATCH_TOO_LARGE` | 422 | Số lượng vượt giới hạn 1.000 | Không phát hành một phần. |
| `INVALID_BATCH_DATA` | 422 | Giá trị/thời lượng/hạn/thông tin phân phối sai | Trả lỗi đúng trường. |
| `BATCH_CREATION_ERROR` | 500 | Không thể ghi đủ lô và audit | Rollback; không trả CSV thành công. |

## Business rule

| # | Rule |
| --- | --- |
| BR-099-1 | PAYMENT_SCOPE đã chốt: FINANCE_ADMIN được phát hành mã CREDIT/SUBSCRIPTION để phân phối ngoài hệ thống; CNHSK không tích hợp cổng thanh toán. |
| BR-099-2 | Chỉ `FINANCE_ADMIN` được sinh mã thẻ. Không dùng role `ADMIN` chung cho chức năng này. |
| BR-099-3 | Mã thẻ phải được sinh ngẫu nhiên bằng nguồn ngẫu nhiên an toàn, không dùng mã tuần tự hoặc mã dễ đoán. |
| BR-099-4 | Mã thẻ phải đủ dài để không thể dò thực tế. Đề xuất tối thiểu 16 ký tự. |
| BR-099-5 | Hệ thống chỉ lưu hash của mã thẻ, không lưu mã thô. |
| BR-099-6 | Mã thô chỉ được trả một lần ngay sau khi sinh để quản trị tải xuống. Sau đó không có chức năng xem lại mã thô. |
| BR-099-7 | Không ghi mã thô vào log trong mọi trường hợp. |
| BR-099-8 | Mỗi lần sinh mã phải ghi audit log gồm người sinh, thời điểm, số lượng mã, loại mã và tổng giá trị. |
| BR-099-9 | Mã hoặc lô mã không dùng nữa phải được vô hiệu hóa bằng trạng thái, không xóa dòng dữ liệu. |
| BR-099-10 | Giới hạn số lượng mã sinh trong một lô để tránh rủi ro vận hành. Đề xuất tối đa 1.000 mã/lô. |

## API · DB

```
POST  /api/admin/credit-cards/batch
GET   /api/admin/credit-cards?batch_id={b}     (chỉ trạng thái, KHÔNG mã)
PATCH /api/admin/credit-cards/batch/{b}/void
```

`credit_cards` (ghi/đọc trạng thái), `plans` khi phát hành thẻ gói, `audit_logs`.
Các cột kênh phân phối và liên kết/bảo lưu quyền lợi gói cần đối chiếu thiết kế chờ migration;
không khẳng định đã có đầy đủ trong V1. CSV chỉ là kết quả phát hành, không phải bằng chứng
bán mã hoặc thu tiền bên ngoài.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | FINANCE_ADMIN sinh 100 CREDIT hợp lệ | Đủ 100 UNUSED và audit; một response CSV. |
| T2 | CONTENT_ADMIN/USER không có FINANCE_ADMIN | 403. |
| T3 | SUPER_ADMIN không có FINANCE_ADMIN | 403; không có kế thừa quyền ngầm. |
| T4 | Số lượng 0, âm, không nguyên hoặc 2.000 | Validation thất bại; không có lô một phần. |
| T5 | Không đặt hạn và đặt hạn đã qua | Không hạn được phép; hạn đã qua bị từ chối. |
| T6 | Va chạm hash trong lô/DB | Xử lý an toàn, đủ số lượng UNIQUE hoặc rollback toàn bộ. |
| T7 | Lỗi giữa lúc ghi lô hoặc audit | Không phát hành lô một phần, không trả CSV thành công. |
| T8 | Kiểm DB/log/API tra cứu lô | Không lấy lại được mã thô; log không chứa mã. |
| T9 | Vô hiệu lô có cả UNUSED/USED/VOIDED | Chỉ UNUSED đổi thành VOIDED, lịch sử USED nguyên vẹn. |
| T10 | Redeem đồng thời với void | Mỗi mã chỉ có một kết quả hợp lệ, không vừa nạp vừa ghi đè USED. |
| T11 | Mất response sau commit | Tra được trạng thái lô; không tự sinh lô khác hoặc phục hồi mã. |
| T12 | Thiếu partner_code/issued_to theo kênh | Validation đúng trường; không áp đặt thêm quyền nạp theo issued_to. |

---

# UC-100 · Xem và đối chiếu sổ cái giao dịch điểm

| | |
|---|---|
| **UC-ID** | UC-100 · **Actor** `FINANCE_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

`FINANCE_ADMIN` xem, lọc, xuất và đối chiếu sổ cái điểm toàn hệ thống để kiểm tra biến động điểm và hỗ trợ xử lý tranh chấp. Sổ cái điểm **không phải** báo cáo doanh thu tiền mặt vì tiền thật được thu bên ngoài CNHSK.

UC này chỉ đọc dữ liệu sổ cái. Nếu phát hiện sai lệch, hệ thống không tự sửa hoặc tự tạo giao dịch điều chỉnh.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có role `FINANCE_ADMIN`.
2. Yêu cầu tra cứu có khoảng thời gian hợp lệ và có giới hạn phân trang/xuất dữ liệu.
3. Cơ chế audit truy cập dữ liệu tài chính đang hoạt động.

## Hậu điều kiện

### Thành công

1. Danh sách và tổng hợp được trả theo đúng bộ lọc.
2. Thao tác xem, xuất hoặc đối chiếu được ghi audit theo chính sách.
3. Không giao dịch nào bị sửa hoặc xóa.

### Không thành công

1. Không thay đổi sổ cái.
2. Không tự tạo `ADJUSTMENT` khi phát hiện lệch.
3. Không trả dữ liệu nhạy cảm nếu kiểm quyền/audit bắt buộc thất bại.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Chọn khoảng thời gian và bộ lọc người dùng/loại giao dịch nếu cần. |
| 2 | Client | Gửi yêu cầu danh sách với bộ lọc và phân trang. |
| 3 | System | Kiểm tra role và tính hợp lệ của phạm vi thời gian. |
| 4 | System | Lấy các giao dịch đúng bộ lọc, sắp xếp ổn định và phân trang. |
| 5 | System | Tính số liệu tổng hợp trên toàn phạm vi lọc, không chỉ trang hiện tại. |
| 6 | System | Ghi audit cho thao tác truy cập. |
| 7 | System | Trả danh sách, tổng hợp và thông tin phân trang. |
| 8 | Client | Hiển thị điểm có dấu, hạn chế PII và không gọi tổng điểm là doanh thu tiền mặt. |

## Luồng thay thế

**A1 — Tra một người dùng:** áp dụng bộ lọc theo người dùng nhưng vẫn giới hạn thời gian và phân trang.

**A2 — Xuất CSV:** áp dụng cùng quyền và bộ lọc; thao tác xuất cũng phải có audit và giới hạn phạm vi.

**A3 — Đối chiếu:** kiểm chuỗi số dư theo từng tài khoản trên cùng mốc dữ liệu nhất quán; tính đủ các loại giao dịch làm thay đổi điểm.

**A4 — Phát hiện sai lệch:** trả kết quả cần điều tra; không tự ghi điều chỉnh.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả sổ cái. |
| `FORBIDDEN_ROLE` | 403 | Không có FINANCE_ADMIN | Chặn. |
| `INVALID_DATE_RANGE` | 422 | Thiếu/sai khoảng thời gian | Không truy vấn bỏ giới hạn. |
| `UNBOUNDED_EXPORT` | 422 | Yêu cầu xuất không có giới hạn hợp lệ | Từ chối. |
| `LEDGER_READ_ERROR` | 500 | Không đọc được dữ liệu hoặc không ghi được audit bắt buộc | Báo lỗi; không tự sửa dữ liệu. |

## Business rule

| # | Rule |
| --- | --- |
| BR-100-1 | Sổ cái giao dịch điểm là một phần của MVP theo PAYMENT_SCOPE đã chốt; đây không phải sổ doanh thu tiền mặt bên ngoài CNHSK. |
| BR-100-2 | Chỉ `FINANCE_ADMIN` được xem sổ cái toàn hệ thống. |
| BR-100-3 | Mọi lần xem hoặc xuất sổ cái phải ghi audit log. |
| BR-100-4 | Danh sách sổ cái phải có bộ lọc thời gian bắt buộc và phân trang. |
| BR-100-5 | Khi hiển thị danh sách, thông tin người dùng phải được hạn chế. Email được che một phần; thông tin đầy đủ chỉ được xem ở ngữ cảnh điều tra có quyền phù hợp. |
| BR-100-6 | Dữ liệu sổ cái trong UC này chỉ được đọc, không được sửa hoặc xóa. |
| BR-100-7 | Hệ thống phải có đối chiếu giữa tổng số dư người dùng và tổng giao dịch để phát hiện lệch. |
| BR-100-8 | Xuất file sổ cái phải giới hạn theo khoảng thời gian, không cho xuất toàn bộ dữ liệu không giới hạn. |

## API · DB

```
GET /api/admin/credit-transactions
GET /api/admin/credit-transactions/export
GET /api/admin/credit-transactions/reconciliation
```

`credit_transactions` (đọc), dữ liệu người dùng tối thiểu qua API module sở hữu,
`audit_logs` (ghi). Không có `user_credits` để đối chiếu như một bảng số dư độc lập.
Giữ ba endpoint hiện có; không bổ sung cổng thanh toán hay báo cáo tiền thực thu ngoài hệ thống.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xem một tháng có nhiều trang | Danh sách đúng trang, tổng tính trên toàn bộ phạm vi lọc. |
| T2 | Tài khoản không có FINANCE_ADMIN | 403, không lộ dữ liệu. |
| T3 | Thiếu ngày hoặc từ ngày lớn hơn đến ngày | 422, không truy vấn bỏ giới hạn. |
| T4 | Danh sách và CSV có thông tin người dùng | PII được hạn chế/che; không có mã thẻ hoặc hash. |
| T5 | Xem/xuất/đối chiếu | Có audit đúng người và phạm vi. |
| T6 | Kỳ có số dư đầu kỳ, ADJUSTMENT âm/dương và TEACHER_PAYOUT | Công thức đầu kỳ + tổng amount = cuối kỳ tính đủ mọi loại. |
| T7 | SUBSCRIPTION amount 0 | Không tăng tổng điểm. |
| T8 | Phát sinh giao dịch đồng thời lúc đối chiếu | Không báo lệch giả do dùng hai mốc dữ liệu. |
| T9 | Phát hiện chuỗi lệch | Trả kết quả kiểm tra, không tự sửa ledger. |
| T10 | Cố sửa/xóa giao dịch qua UC này | Không có endpoint ghi/xóa ledger. |

---

# UC-101 · Quản lý gói dịch vụ

| | |
|---|---|
| **UC-ID** | UC-101 · **Actor** `FINANCE_ADMIN` · **Pri** P2 · **Scope** MVP · **FT** 6.1 |

## Mô tả

`FINANCE_ADMIN` quản lý danh mục các gói dịch vụ được CNHSK hỗ trợ, gồm tên/mã gói, giá niêm yết, thời lượng, quyền lợi và trạng thái còn cung cấp hay không. Phạm vi mã gói hiện tại là `FREE`, `PREMIUM`, `PREMIUM_PLUS`; UC này không mở đường tạo tùy ý loại gói mới ngoài phạm vi đã chốt.

Thay đổi danh mục chỉ áp dụng cho việc cấp quyền mới. Hệ thống không được sửa lịch sử hoặc làm mất quyền lợi đã cấp cho người dùng/mã đã phát hành trước đó.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có `FINANCE_ADMIN`.
2. Với thao tác ghi từ web, CSRF token hợp lệ.
3. Mã gói, cấu trúc quyền lợi và quy tắc thời lượng thuộc phạm vi được phê duyệt.
4. Nếu thay đổi có thể ảnh hưởng quyền lợi cũ, hệ thống phải có dữ liệu đủ để bảo toàn quyền đã cấp.

## Hậu điều kiện

### Thành công

1. Gói được tạo, cập nhật hoặc ngưng cung cấp đúng dữ liệu đã xác nhận.
2. Audit log ghi người thực hiện và thay đổi quan trọng.
3. Lịch sử cũ và quyền lợi đã cấp không bị thay đổi hồi tố.

### Không thành công

1. Dữ liệu gói cũ được giữ nguyên.
2. Người dùng và mã đã phát hành không bị mất quyền.
3. Không có thay đổi danh mục một phần nếu audit bắt buộc hoặc ghi dữ liệu thất bại.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Mở danh mục gói và chọn tạo gói còn thiếu trong phạm vi cho phép, sửa hoặc ngưng cung cấp. |
| 2 | Client | Gửi yêu cầu đọc hoặc thay đổi gói. |
| 3 | System | Kiểm tra role và CSRF đối với thao tác ghi. |
| 4 | System | Kiểm tra mã/tên, giá, thời lượng và cấu trúc quyền lợi. |
| 5 | System | Xác định thay đổi có ảnh hưởng người dùng đang còn hạn hoặc mã chưa dùng đã phát hành hay không. |
| 6 | System | Nếu có thể bảo toàn quyền đã cấp, lưu thay đổi danh mục và audit. |
| 7 | System | Nếu không thể bảo toàn quyền cũ, từ chối thay đổi gây ảnh hưởng. |
| 8 | Client | Hiển thị trạng thái gói sau cập nhật. |

## Luồng thay thế

**A1 — Đổi giá:** giá mới chỉ áp dụng cho giao dịch/cấp quyền mới; không sửa giá trị lịch sử đã ghi.

**A2 — Ngưng cung cấp:** đặt gói không hoạt động, không xóa; người đang còn hạn và mã hợp lệ đã phát hành tiếp tục được tôn trọng theo quyền đã cấp.

**A3 — Sửa quyền lợi hoặc thời lượng:** không áp hồi tố. Nếu chưa có cơ chế bảo toàn quyền đã cấp thì từ chối thay đổi.

**A4 — Tạo gói ngoài ba mã đã chốt:** từ chối; không tạo loại gói thứ tư ngoài spec.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không đọc/ghi danh mục quản trị. |
| `FORBIDDEN_ROLE` | 403 | Không có FINANCE_ADMIN | Chặn. |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu/sai CSRF | Không cập nhật. |
| `INVALID_PLAN_DATA` | 422 | Giá/thời lượng/quyền lợi không hợp lệ | Giữ dữ liệu cũ. |
| `DUPLICATE_PLAN` | 409 | Mã/tên gói trùng | Không tạo bản ghi trùng. |
| `GRANTED_BENEFIT_CANNOT_BE_PRESERVED` | 409 | Thay đổi làm mất quyền đã cấp | Từ chối thay đổi. |
| `PLAN_WRITE_ERROR` | 500 | Không ghi được dữ liệu/audit nhất quán | Rollback. |

## Business rule

| # | Rule |
| --- | --- |
| BR-101-1 | PAYMENT_SCOPE đã chốt có gói dịch vụ và mã SUBSCRIPTION; quản lý gói thuộc phạm vi MVP. |
| BR-101-2 | Chỉ `FINANCE_ADMIN` được tạo, sửa hoặc ngưng bán gói dịch vụ. |
| BR-101-3 | Giá gói không được âm và không dùng kiểu số thực. |
| BR-101-4 | Gói đã có người dùng hoặc mã chưa dùng trỏ tới không được xóa cứng. Chỉ được ngưng bán bằng trạng thái không hoạt động. |
| BR-101-5 | Đổi giá gói chỉ áp dụng cho giao dịch mới, không áp hồi tố cho người đã mua trước đó. |
| BR-101-6 | Giao dịch liên quan đến gói phải lưu giá tại thời điểm giao dịch, không chỉ lưu `plan_id`. |
| BR-101-7 | Mọi lần đổi giá, đổi quyền lợi hoặc ngưng bán gói phải ghi audit log. |
| BR-101-8 | Người đang có gói còn hạn vẫn được dùng đến hết hạn theo quyền lợi đã mua, trừ khi chính sách sản phẩm chốt khác bằng văn bản. |

## API · DB

```
GET   /api/admin/plans
POST  /api/admin/plans
PUT   /api/admin/plans/{id}
PATCH /api/admin/plans/{id}/deactivate
```

`plans` (đọc/ghi), gói trên `users` và `credit_cards` (kiểm ảnh hưởng),
`credit_transactions`/lịch sử quyền đã cấp (đọc), `audit_logs` (ghi).
Giữ nguyên đường dẫn API đã liệt kê; trước triển khai phải thống nhất cách tham chiếu
`{id}` với thiết kế khóa `plans.code` trong OpenAPI. Không tự sửa contract khác hoặc schema.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tạo mã gói hợp lệ chưa có | Tạo danh mục và audit nhất quán. |
| T2 | Mã ngoài ba mã cho phép hoặc mã đã tồn tại | Validation/xung đột tương ứng, không tạo gói tùy ý. |
| T3 | Giá âm, gói trả phí thiếu thời lượng | 422; không lưu dữ liệu sai. |
| T4 | FREE có hạn gói trống, quota theo chính sách | Hợp lệ; không bắt FREE phải có số ngày dương. |
| T5 | Đổi giá | Giá trị lịch sử đã cấp không đổi theo giá mới. |
| T6 | Ngưng cung cấp gói đang có người dùng/thẻ hợp lệ | Không mất quyền cũ, không xóa dữ liệu. |
| T7 | Sửa quyền lợi khi chưa bảo lưu được quyền cũ | 409; không chỉ cảnh báo rồi vẫn lưu. |
| T8 | Không có FINANCE_ADMIN hoặc thiếu CSRF | Bị chặn. |
| T9 | Audit lỗi | Thay đổi danh mục rollback. |

---

# UC-102 · Xử lý tranh chấp về điểm

| | |
|---|---|
| **UC-ID** | UC-102 · **Actor** `FINANCE_ADMIN` · **Pri** P2 · **Scope** MVP · **FT** 6.1 |

## Mô tả

`FINANCE_ADMIN` xác minh một khiếu nại liên quan đến số điểm bằng cách đối chiếu sổ cái và hành động nguồn. UC này không tạo thêm hệ thống quản lý ticket/khiếu nại; yêu cầu có thể được tiếp nhận từ kênh hỗ trợ hiện tại và phải có mã tham chiếu để truy vết.

Nếu sai lệch là hậu quả của một hành động thất bại đã tiêu thụ quyền sử dụng, xử lý theo UC-097. `ADJUSTMENT` chỉ dùng cho trường hợp cần điều chỉnh nghiệp vụ khác. Giao dịch cũ không bao giờ bị sửa hoặc xóa để “sửa số dư”.

## Tiền điều kiện

1. Người thao tác đã đăng nhập và có `FINANCE_ADMIN`.
2. Có mã tham chiếu hoặc thông tin đủ để xác định vụ việc và người dùng liên quan.
3. Với thao tác điều chỉnh từ web, CSRF token hợp lệ.
4. Có đủ căn cứ để xác định có cần điều chỉnh hay không; không tự cộng bù khi bằng chứng chưa đủ.

## Hậu điều kiện

### Có điều chỉnh nghiệp vụ

1. Tạo một giao dịch `ADJUSTMENT` mới với amount có dấu, số dư trước/sau, lý do và mã tham chiếu.
2. Ghi người thực hiện và audit.
3. Giao dịch cũ giữ nguyên.
4. Số dư sau điều chỉnh không âm.

### Là lỗi cần hoàn

1. Xử lý bằng UC-097.
2. Không tạo ADJUSTMENT thay cho REFUND.

### Không cần điều chỉnh

1. Số dư không thay đổi.
2. Audit việc truy cập/điều tra vẫn được ghi theo chính sách.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Nhận khiếu nại và tra cứu giao dịch liên quan bằng phạm vi phù hợp. |
| 2 | System | Kiểm tra quyền, trả dữ liệu cần thiết và ghi audit truy cập. |
| 3 | `FINANCE_ADMIN` | Đối chiếu thẻ, hành động nguồn, refund/adjustment và chuỗi số dư. |
| 4 | `FINANCE_ADMIN` | Kết luận: không điều chỉnh, hoàn theo UC-097 hoặc cần ADJUSTMENT. |
| 5 | `FINANCE_ADMIN` | Nếu cần điều chỉnh, nhập amount, lý do và mã tham chiếu. |
| 6 | System | Kiểm tra lại quyền, chặn tự điều chỉnh tài khoản của chính người thao tác và kiểm dữ liệu đầu vào. |
| 7 | System | Kiểm tra vụ việc chưa được xử lý lặp theo tham chiếu. |
| 8 | System | Lấy số dư hiện tại, kiểm số dư sau không âm và append `ADJUSTMENT`. |
| 9 | System | Ghi audit và hoàn tất transaction. |
| 10 | Client | Hiển thị kết quả xử lý. |

## Luồng thay thế

**A1 — Hành động thất bại đã trừ điểm nhưng chưa hoàn:** gọi UC-097; không dùng ADJUSTMENT để né cơ chế chống hoàn trùng.

**A2 — Hành động thực tế đã hoàn tất đúng:** không hoàn/điều chỉnh chỉ vì người dùng khiếu nại; giải thích bằng dữ liệu được phép công khai.

**A3 — Vụ việc đã được xử lý:** không tạo thêm giao dịch.

**A4 — Không đủ bằng chứng hoặc thiếu chính sách:** không tự đặt số điểm bù; ghi nhận vụ việc chưa thể điều chỉnh tại nơi tiếp nhận hiện hành.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không tra cứu/điều chỉnh. |
| `FORBIDDEN_ROLE` | 403 | Không có FINANCE_ADMIN | Chặn. |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu/sai CSRF | Không điều chỉnh. |
| `SELF_ADJUSTMENT` | 403 | Người thực hiện là tài khoản được điều chỉnh | Chặn. |
| `INVALID_ADJUSTMENT` | 422 | Thiếu lý do/tham chiếu, amount = 0 hoặc làm số dư âm | Không ghi giao dịch. |
| `DUPLICATE_ADJUSTMENT` | 409 | Vụ việc đã được xử lý | Không cộng/trừ lặp. |
| `ADJUSTMENT_WRITE_ERROR` | 500 | Không ghi được giao dịch/audit nhất quán | Rollback. |

## Business rule

| # | Rule |
| --- | --- |
| BR-102-1 | Tranh chấp điểm được xử lý trên sổ cái điểm đã chốt trong MVP; UC này không quản lý tranh chấp tiền mặt thu ngoài hệ thống. |
| BR-102-2 | Chỉ `FINANCE_ADMIN` được xử lý tranh chấp về điểm. |
| BR-102-3 | Không được sửa hoặc xóa giao dịch cũ để xử lý tranh chấp. Mọi điều chỉnh phải tạo giao dịch điều chỉnh mới. |
| BR-102-4 | Giao dịch điều chỉnh phải có lý do rõ ràng và mã tham chiếu khiếu nại hoặc nguồn xác minh. |
| BR-102-5 | Người xử lý điều chỉnh phải được ghi lại trong giao dịch hoặc audit log. |
| BR-102-6 | `FINANCE_ADMIN` không được tự điều chỉnh điểm cho chính tài khoản của mình. |
| BR-102-7 | Một tranh chấp chỉ được xử lý một lần để tránh cộng/trừ lặp. |
| BR-102-8 | Nếu lỗi là do hệ thống đã trừ điểm nhưng thao tác thất bại, phải xử lý bằng luồng hoàn điểm, không dùng điều chỉnh thủ công. |
| BR-102-9 | Điều chỉnh không được làm số dư người dùng âm. |

## API · DB

```
GET  /api/admin/users/{id}/credit-transactions
POST /api/admin/credit-adjustments
```

`credit_transactions` (đọc/append), `credit_cards` và hành động nguồn (đọc),
`audit_logs` (ghi). Không cập nhật `user_credits`.
Bằng chứng bên ngoài và kết luận không điều chỉnh ở nơi tiếp nhận hiện hành;
chưa có quyết định tạo kho khiếu nại trong CNHSK.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Điều chỉnh +50 có đủ căn cứ | Một ADJUSTMENT +50, đúng số dư và audit. |
| T2 | Điều chỉnh âm trong phạm vi số dư | Số dư giảm đúng, lịch sử cũ nguyên vẹn. |
| T3 | Thiếu lý do/tham chiếu, amount 0 hoặc làm âm số dư | 422; không đổi điểm. |
| T4 | Thiếu role hoặc tự điều chỉnh | 403. |
| T5 | Cùng tham chiếu gửi hai lần/đồng thời | Một lần có hiệu lực; lần trùng bị từ chối. |
| T6 | Thao tác thất bại đã trừ điểm | Dùng UC-097, không tạo ADJUSTMENT thay REFUND. |
| T7 | Đã hoàn hoặc đã điều chỉnh vụ việc | Không xử lý lặp. |
| T8 | Lỗi audit/ledger | Rollback toàn bộ điều chỉnh. |
| T9 | Mã do tài khoản khác dùng, chưa có căn cứ bù | Không tự cộng điểm, không lộ danh tính người dùng mã. |
| T10 | Không điều chỉnh hoặc cố sửa dòng ledger cũ | Số dư giữ nguyên; không có endpoint sửa/xóa ledger. |

---

# UC-103 · Gửi yêu cầu nhờ chấm bài viết

| | |
|---|---|
| **UC-ID** | UC-103 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Người học gửi một bản bài viết cụ thể để giáo viên nhận và chấm. Khi người học xác nhận gửi, hệ thống tạo một yêu cầu chấm và tiêu thụ đúng một nguồn quyền sử dụng thông qua UC-096.

Bản bài được gửi phải được giữ cố định để giáo viên chấm đúng nội dung người học đã xác nhận, kể cả khi bài nguồn sau đó được sửa. Trước khi xác nhận, người học phải được biết điều kiện sử dụng, thời hạn xử lý và điều kiện được hoàn.

> **Điều kiện chặn triển khai:** PAYMENT_SCOPE đã chốt, nhưng teacher payout, hạn nhận bài, hạn chấm và quy ước đo độ dài bài tiếng Trung chưa có quyết định cuối cùng. Không tự gán 70%, 24 giờ, 48 giờ hay 50–2.000 “từ” thành yêu cầu code cho đến khi được phê duyệt.

## Tiền điều kiện

1. Người dùng đã đăng nhập bằng tài khoản hợp lệ.
2. Bài nguồn thuộc người dùng hiện tại hoặc nội dung được nhập trực tiếp trong chính yêu cầu.
3. Bài viết có nội dung hợp lệ theo quy ước được phê duyệt.
4. Chính sách payout, hạn nhận và hạn chấm đã được chốt trước khi chức năng được mở cho production.
5. Với web dùng cookie, request gửi/hủy có CSRF hợp lệ.

## Hậu điều kiện

### Gửi thành công

1. Tạo đúng một yêu cầu `GRADING` ở trạng thái `PENDING`.
2. Lưu snapshot nội dung bài được gửi chấm.
3. Lưu chủ sở hữu, thời điểm gửi và hạn nhận theo chính sách đã chốt.
4. UC-096 tiêu thụ đúng một nguồn quota hoặc điểm và liên kết với yêu cầu.
5. Yêu cầu đủ điều kiện xuất hiện trong hàng đợi giáo viên.

### Gửi thất bại

1. Không tồn tại yêu cầu PENDING thành công.
2. Không mất quota hoặc điểm.
3. Không tạo dữ liệu tài chính dở dang.

### Hủy hợp lệ

1. Chỉ yêu cầu còn `PENDING` mới được hủy bởi chủ sở hữu.
2. Yêu cầu không còn xuất hiện trong hàng đợi nhận bài.
3. Quyền sử dụng được hoàn qua UC-097.
4. Lịch sử yêu cầu vẫn được giữ; không xóa cứng.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn bài ESSAY hoặc nhập bài tự viết cần giáo viên chấm. |
| 2 | Client | Hiển thị điều kiện sử dụng, thời hạn nhận/chấm và chính sách hoàn đã được duyệt. |
| 3 | `USER` | Kiểm tra nội dung và xác nhận gửi. |
| 4 | Client | Gửi nội dung/tham chiếu bài cùng định danh chống lặp và CSRF nếu cần. |
| 5 | System | Kiểm tra quyền sở hữu bài nguồn, nội dung và điều kiện hợp lệ. |
| 6 | System | Kiểm tra không có yêu cầu `PENDING` hoặc `ASSIGNED` khác cho cùng bài theo quy tắc đã duyệt. |
| 7 | System | Tạo yêu cầu PENDING và snapshot bài trong transaction hiện tại. |
| 8 | System | Gọi UC-096 để tiêu thụ đúng một nguồn và ghi bằng chứng nguồn tiêu thụ. |
| 9 | System | Commit yêu cầu và tiêu thụ như một thao tác nhất quán. |
| 10 | Client | Hiển thị trạng thái “Đang chờ giáo viên nhận” và hạn tương ứng. |

## Luồng thay thế

**A1 — Không còn quota và không đủ điểm:** UC-096 từ chối; toàn bộ thao tác tạo yêu cầu rollback, không có PENDING chưa được chi trả.

**A2 — Người học hủy khi còn PENDING:** kiểm chủ sở hữu và trạng thái hiện tại; kết thúc yêu cầu theo trạng thái hủy đã được thiết kế và gọi UC-097 trong cùng luồng nhất quán.

**A3 — Bài không hợp lệ:** trả lỗi validation trước khi tiêu thụ quota/điểm.

**A4 — Gửi lại do mất response:** nếu là retry của cùng hành động, trả yêu cầu đã ghi nhận và không tiêu thụ lần nữa. Nếu là yêu cầu mới nhưng cùng bài đang PENDING/ASSIGNED, từ chối tạo trùng.

**A5 — Thông báo lỗi sau commit:** yêu cầu và nguồn tiêu thụ vẫn có hiệu lực; không tự tạo lại hoặc hoàn chỉ vì thông báo thất bại.

**A6 — Bài nguồn thay đổi sau khi gửi:** giáo viên tiếp tục chấm snapshot đã gửi; không lấy nội dung mới thay vào giữa quá trình.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | Không tạo yêu cầu. |
| `FORBIDDEN` | 403 | Tài khoản không được phép thao tác | Không tạo yêu cầu. |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu/sai CSRF | Không tạo/không tiêu thụ. |
| `REQUEST_NOT_OWNED` | 403 | Bài nguồn không thuộc người gửi | Không lộ nội dung người khác. |
| `INSUFFICIENT_CREDITS` | 402 | Hết quota và không đủ điểm | Không tạo yêu cầu. |
| `INVALID_ESSAY` | 422 | Nội dung không đạt quy ước đã duyệt | Không tiêu thụ. |
| `DUPLICATE_ACTIVE_REQUEST` | 409 | Cùng bài đang PENDING hoặc ASSIGNED | Không tạo yêu cầu thứ hai. |
| `GRADING_POLICY_NOT_READY` | Điều kiện triển khai | Payout/deadline/quy ước bài chưa được duyệt | Không mở tính năng trên production. |
| `GRADING_REQUEST_WRITE_ERROR` | 500 | Không thể ghi yêu cầu và nguồn tiêu thụ nhất quán | Rollback toàn bộ. |

## Business rule

| # | Rule |
| --- | --- |
| BR-103-1 | Chức năng chấm bài chỉ được mở trên production sau khi chốt teacher payout, hạn giáo viên nhận bài và hạn chấm xong. |
| BR-103-2 | PAYMENT_SCOPE đã chốt; blocker còn lại của luồng chấm bài là payout, deadline và các chính sách chuyên môn liên quan, không phải cơ chế điểm/nạp mã. |
| BR-103-3 | Người dùng chỉ gửi yêu cầu chấm khi đã đăng nhập và còn quota hoặc điểm theo chính sách đã chốt. |
| BR-103-4 | Điểm hoặc quota bị trừ tại thời điểm gửi yêu cầu, đúng theo nghiệm thu của file. |
| BR-103-5 | Trừ điểm/quota và tạo yêu cầu chấm phải nhất quán. Không được trừ thành công nhưng không tạo yêu cầu. |
| BR-103-6 | Một bài viết chỉ được có một yêu cầu chấm đang chờ hoặc đang được xử lý. |
| BR-103-7 | Bài gửi chấm phải tuân theo quy ước độ dài đã được phê duyệt. Không hardcode ngưỡng hoặc cách đếm từ tiếng Trung trước khi quy ước này được chốt. |
| BR-103-8 | Người dùng được hủy yêu cầu khi yêu cầu còn ở trạng thái chờ teacher nhận. Khi hủy hợp lệ, hệ thống hoàn lại phần đã trừ. |
| BR-103-9 | Nội dung bài viết phải được escape khi teacher xem để tránh XSS. |
| BR-103-10 | Người dùng cần được thông báo rõ điều kiện hoàn điểm trước khi xác nhận gửi chấm. |

## API · DB

```
POST   /api/grading-requests
DELETE /api/grading-requests/{id}        (hủy nghiệp vụ khi còn PENDING, không xóa dữ liệu)
GET    /api/me/grading-requests
```

`service_requests` với `kind = 'GRADING'` (ghi), quota trên `users` qua API module sở hữu,
`credit_transactions` khi dùng/hoàn điểm. Tên `grading_requests` trong URL là tài nguyên API,
không phải tên bảng hiện có.

V1 có khóa ngoại từ `credit_transactions.request_id` tới `service_requests.id`;
vì vậy bản ghi yêu cầu phải được tạo trong transaction trước DEDUCT tham chiếu tới nó.
Nếu tiêu thụ thất bại, rollback yêu cầu chưa commit; không cần đổi schema hoặc trì hoãn khóa ngoại.

Cần xác định cách lưu bản bài, nguồn/kỳ tiêu thụ, khóa chống lặp, định danh bài tự viết
và trạng thái hủy trước triển khai. Schema hiện có không có CANCELLED; UC này không tự
thêm trạng thái/cột hoặc giả định một unique chống trùng GRADING đã tồn tại.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bài hợp lệ, còn quota | 201/PENDING; trừ một lượt, không giảm điểm. |
| T2 | Hết quota, còn điểm | Một yêu cầu và một DEDUCT đúng chi phí. |
| T3 | Hết quota và điểm | 402, không tạo yêu cầu. |
| T4 | Lỗi tạo yêu cầu hoặc lỗi sau consume trước commit | Tạo lỗi thì chưa thu; lỗi sau thu thì rollback cả yêu cầu và lượt/điểm. |
| T5 | Hai lần gửi cùng hành động/cùng bài đang hoạt động | Không tạo hoặc thu hai lần; phân biệt retry với yêu cầu trùng. |
| T6 | Bài nguồn hoặc yêu cầu hủy của người khác/không tồn tại | 403. |
| T7 | Hủy khi PENDING | Giữ lịch sử, khỏi hàng đợi và hoàn đúng nguồn nhất quán. |
| T8 | Hủy khi ASSIGNED hoặc cạnh tranh claim/expire | Không vừa hủy/hoàn vừa nhận bài thành công. |
| T9 | Thiếu CSRF hoặc bài không đạt quy ước đã duyệt | Chặn trước tiêu thụ. |
| T10 | Bài có script; nguồn bài thay đổi sau khi gửi | Hiển thị text an toàn; chấm đúng bản đã gửi. |
| T11 | Mất response/thông báo sau commit | Không tạo yêu cầu hoặc trừ lại. |
| T12 | Gửi bằng điểm với khóa ngoại request_id đang bật | Tạo yêu cầu chưa commit trước DEDUCT; thiếu điểm rollback cả yêu cầu, không có PENDING đã commit không được chi trả. |

---

# UC-104 · Xem hàng đợi bài chờ chấm

| | |
|---|---|
| **UC-ID** | UC-104 · **Actor** `TEACHER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Giáo viên xem các yêu cầu chấm hiện đang chờ nhận và có thể xem danh sách bài đang được giao cho chính mình. Hàng đợi chung chỉ trả metadata cần thiết để giáo viên quyết định nhận bài; không trả toàn bộ nội dung bài hoặc danh tính người học.

Việc nhìn thấy một bài trong danh sách không có nghĩa bài đã được giữ chỗ. Khi giáo viên bấm nhận, UC-105 phải kiểm tra lại trạng thái và thời hạn tại thời điểm thực hiện.

> **Điều kiện chặn triển khai:** phụ thuộc chính sách payout và hạn nhận/hạn chấm đã chốt ở UC-103/UC-105.

## Tiền điều kiện

1. Người dùng đã đăng nhập và có role `TEACHER`.
2. Tài khoản được phép thực hiện nghiệp vụ chấm bài.
3. Chính sách payout và thời hạn đã được phê duyệt để dữ liệu hàng đợi có ý nghĩa thống nhất.
4. Hàng đợi có thể rỗng; không yêu cầu phải có bài mới được mở màn hình.

## Hậu điều kiện

1. UC chỉ đọc; không chuyển trạng thái yêu cầu.
2. Không gán/đổi giáo viên nhận bài.
3. Không phát sinh payout, thay đổi điểm hoặc quota.
4. Giáo viên chỉ thấy metadata của các yêu cầu mình được phép xem.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `TEACHER` | Mở hàng đợi chấm bài. |
| 2 | Client | Gửi yêu cầu danh sách PENDING với tham số phân trang/bộ lọc hợp lệ. |
| 3 | System | Xác định giáo viên từ phiên và kiểm tra role TEACHER. |
| 4 | System | Lấy các yêu cầu `GRADING/PENDING` chưa hết hạn nhận và không phải bài của chính giáo viên hiện tại. |
| 5 | System | Sắp xếp theo hạn nhận gần nhất trước, dùng thứ tự ổn định và phân trang. |
| 6 | System | Trả mã yêu cầu, độ dài theo quy ước, thời điểm gửi, hạn nhận và payout được phép hiển thị. |
| 7 | Client | Hiển thị metadata; không hiển thị tên/email/ID người học hoặc toàn bộ bài viết. |
| 8 | `TEACHER` | Chọn một bài để nhận; chuyển UC-105. |

## Luồng thay thế

**A1 — Không có bài phù hợp:** trả danh sách rỗng thành công.

**A2 — Xem bài đang được giao cho chính mình:** chỉ trả yêu cầu `ASSIGNED` có `assignee` là giáo viên hiện tại; không cho xem bài được giao cho giáo viên khác.

**A3 — Bài vừa hết hạn nhưng scheduler chưa chạy:** không hiển thị trong hàng đợi PENDING; điều kiện thời hạn được kiểm ngay khi đọc.

**A4 — Hai giáo viên cùng nhìn thấy một bài:** danh sách không giữ chỗ; chỉ UC-105 quyết định ai nhận thành công.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả danh sách. |
| `FORBIDDEN_ROLE` | 403 | Không có TEACHER | Chặn. |
| `INVALID_FILTER` | 422 | Bộ lọc/phân trang không hợp lệ | Trả lỗi validation. |
| `QUEUE_READ_ERROR` | 500 | Không tải được hàng đợi/cấu hình bắt buộc | Báo lỗi; không giả thành danh sách rỗng. |

## Business rule

| # | Rule |
| --- | --- |
| BR-104-1 | Hàng đợi chấm chỉ được mở khi payout và các deadline của luồng chấm đã được chốt để metadata hiển thị nhất quán. |
| BR-104-2 | Chỉ `TEACHER` được xem hàng đợi bài chờ chấm. |
| BR-104-3 | Hàng đợi chỉ hiển thị các yêu cầu ở trạng thái `PENDING` và chưa quá hạn. |
| BR-104-4 | Danh sách hàng đợi không hiển thị danh tính người học để giảm thiên vị khi chọn bài. |
| BR-104-5 | Danh sách hàng đợi không trả toàn bộ nội dung bài viết. Chỉ trả metadata như độ dài, thời gian gửi, hạn xử lý và mức payout. |
| BR-104-6 | Hàng đợi phải phân trang. |
| BR-104-7 | Danh sách được sắp xếp theo hạn nhận gần nhất trước, kèm khóa phụ ổn định để phân trang nhất quán. |
| BR-104-8 | Bài đã có teacher nhận không được tiếp tục xuất hiện trong hàng đợi chung. |

## API · DB

```
GET /api/teacher/grading-requests?status=PENDING
```

`service_requests` (đọc), luôn lọc `kind = 'GRADING'`.
View ASSIGNED/mine và bộ lọc dùng cùng endpoint đã có, không thêm API đọc bài của người khác.
Nội dung đầy đủ chỉ được trả qua thao tác được phân quyền trong UC-105.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | TEACHER mở hàng đợi có nhiều trang | Metadata sắp theo hạn, thứ tự ổn định và có phân trang. |
| T2 | USER/CONTENT_ADMIN/SUPER_ADMIN không có TEACHER | 403. |
| T3 | Tài khoản có thêm TEACHER hợp lệ | Được xem theo quyền TEACHER, không suy diễn từ role khác. |
| T4 | Kiểm response | Không có danh tính người học hoặc toàn bộ bài viết. |
| T5 | Bài đã ASSIGNED, hết hạn hoặc đúng biên hết hạn | Không xuất hiện trong hàng đợi PENDING, dù job chưa chạy. |
| T6 | View mine của giáo viên A | Không lộ metadata bài đang giao giáo viên B. |
| T7 | Giáo viên có bài tự gửi | Không xuất hiện như bài có thể tự nhận. |
| T8 | Hai giáo viên nhìn thấy cùng một bài | Danh sách không giữ chỗ; claim phải tái kiểm ở UC-105. |
| T9 | Không có dữ liệu hoặc DB lỗi | Phân biệt rõ trạng thái rỗng và lỗi tải. |

---

# UC-105 · Nhận bài và gửi kết quả chấm

| | |
|---|---|
| **UC-ID** | UC-105 · **Actor** `TEACHER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Giáo viên nhận một yêu cầu chấm còn hiệu lực, đọc snapshot bài người học đã gửi, cho điểm, viết nhận xét/sửa lỗi và gửi kết quả. Một yêu cầu tại một thời điểm chỉ thuộc về một giáo viên; giáo viên chỉ được chấm bài mình đã nhận và không được tự chấm bài của chính mình.

Payout chỉ được ghi khi kết quả chấm hoàn tất hợp lệ. UC này không bao gồm rút tiền, chuyển khoản hoặc thanh toán tiền mặt cho giáo viên.

> **Điều kiện chặn triển khai:** payout, đơn vị/công thức payout và hạn nhận/hạn chấm chưa có quyết định cuối cùng. Không tự dùng 70%, 24 giờ hoặc 48 giờ từ thiết kế chờ duyệt làm yêu cầu code.

## Tiền điều kiện

### Khi nhận bài mới

1. Người dùng đã đăng nhập và có role `TEACHER`.
2. Yêu cầu là `GRADING/PENDING` và chưa hết hạn nhận.
3. Bài không thuộc chính giáo viên hiện tại.
4. Với web dùng cookie, request nhận bài có CSRF hợp lệ.

### Khi gửi kết quả

1. Yêu cầu đang ở trạng thái `ASSIGNED`.
2. Yêu cầu đang được giao cho chính giáo viên hiện tại.
3. Lần giao hiện tại còn hiệu lực và chưa hết hạn chấm.
4. Request gửi kết quả có CSRF hợp lệ nếu áp dụng.

## Hậu điều kiện

### Sau khi nhận bài

1. Yêu cầu chuyển sang `ASSIGNED`.
2. Ghi giáo viên nhận, thời điểm nhận và hạn chấm.
3. Chỉ người nhận hợp lệ được xem đầy đủ snapshot bài.

### Sau khi chấm thành công

1. Yêu cầu chuyển sang `COMPLETED`.
2. Điểm, nhận xét và phần sửa lỗi hợp lệ được lưu.
3. Payout được ghi đúng một lần theo chính sách được duyệt.
4. Người học có thể xem kết quả qua UC-106.

### Sau khi trả bài hoặc quá hạn chấm

1. Không phát sinh payout.
2. Lần giao hiện tại bị kết thúc.
3. Yêu cầu trở lại trạng thái phù hợp theo chính sách; không tự cấp vòng thời hạn mới nếu chưa được quy định.

### Khi xử lý thất bại

1. Không để trạng thái `COMPLETED` nhưng thiếu kết quả/payout bắt buộc hoặc ngược lại.
2. Giữ trạng thái trước transaction thất bại để có thể xử lý lại.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `TEACHER` | Chọn nhận một bài từ hàng đợi. |
| 2 | Client | Gửi yêu cầu nhận bài kèm CSRF nếu cần. |
| 3 | System | Kiểm tra role, trạng thái, hạn nhận và điều kiện không tự chấm. |
| 4 | System | Nếu hợp lệ, chuyển yêu cầu sang `ASSIGNED`, ghi người nhận, thời điểm nhận và hạn chấm. |
| 5 | System | Sau khi việc nhận bài thành công, trả snapshot bài đầy đủ cho giáo viên nhận. |
| 6 | `TEACHER` | Đọc bài và nhập điểm, nhận xét, phần sửa lỗi. |
| 7 | `TEACHER` | Xác nhận gửi kết quả. |
| 8 | System | Kiểm tra giáo viên vẫn là người đang giữ bài, lần giao hiện tại còn hiệu lực và chưa hết hạn chấm. |
| 9 | System | Kiểm tra điểm, nhận xét và cấu trúc sửa lỗi theo quy ước được duyệt. |
| 10 | System | Lưu kết quả, chuyển `COMPLETED` và ghi payout đúng một lần trong cùng luồng nhất quán. |
| 11 | System | Thông báo người học sau khi commit thành công. |

## Luồng thay thế

**A1 — Hai giáo viên cùng nhận:** chỉ một giáo viên chuyển trạng thái thành công; người còn lại nhận xung đột và không được xem toàn bộ bài.

**A2 — Giáo viên trả bài trước khi chấm:** chỉ người đang giữ bài được trả; kết thúc lần giao, không payout. Nếu yêu cầu còn đủ điều kiện thì trở lại PENDING theo chính sách đã duyệt.

**A3 — Quá hạn chấm:** không nhận kết quả/payout. Lần giao được thu hồi theo chính sách; UC-107 chỉ xử lý khi yêu cầu đã ở PENDING và hết hạn nhận.

**A4 — Gửi kết quả lần hai:** không tạo kết quả hoặc payout thứ hai.

**A5 — Đã trả bài nhưng client cũ vẫn gửi submission:** từ chối vì lần giao cũ không còn hiệu lực, kể cả giáo viên sau đó nhận lại chính bài đó.

**A6 — Mất response sau khi nhận bài:** nếu yêu cầu vẫn `ASSIGNED` cho chính giáo viên và còn hạn chấm, cho mở lại cùng lần giao; không thay người nhận, thời điểm nhận, hạn hoặc payout.

**A7 — Thông báo lỗi sau commit:** kết quả và payout đã commit vẫn có hiệu lực; không gửi lại kết quả/payout chỉ để phát thông báo.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không nhận/chấm/trả bài. |
| `FORBIDDEN_ROLE` | 403 | Không có TEACHER | Chặn. |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu/sai CSRF | Không thay đổi yêu cầu. |
| `SELF_GRADING` | 403 | Giáo viên là chủ bài | Chặn. |
| `NOT_ASSIGNED_TO_ME` | 403 | Cố chấm/trả bài không thuộc lần giao hiện tại | Không trả nội dung hoặc ghi kết quả. |
| `ALREADY_CLAIMED` | 409 | Giáo viên khác đã nhận hoặc trạng thái đã đổi | Không nhận bài. |
| `GRADING_DEADLINE_MISSED` | 422 | Hết hạn chấm | Không nhận kết quả/payout. |
| `INVALID_GRADING_RESULT` | 422 | Điểm/nhận xét/sửa lỗi không hợp lệ | Không chuyển COMPLETED. |
| `GRADING_POLICY_NOT_READY` | Điều kiện triển khai | Payout/deadline chưa chốt | Không mở tính năng trên production. |
| `GRADING_SAVE_ERROR` | 500 | Không lưu được kết quả và payout nhất quán | Rollback. |

## Business rule

| # | Rule |
| --- | --- |
| BR-105-1 | Việc nhận/chấm bài chỉ được mở trên production sau khi chốt đơn vị, công thức và nguồn teacher payout cùng deadline tương ứng. |
| BR-105-2 | Chỉ `TEACHER` được nhận và chấm bài. |
| BR-105-3 | Một yêu cầu chấm chỉ được một teacher nhận tại một thời điểm. |
| BR-105-4 | Teacher chỉ được chấm bài mà chính mình đã nhận. |
| BR-105-5 | Teacher không được tự chấm bài của chính mình. |
| BR-105-6 | Khi teacher nhận bài, yêu cầu chuyển từ `PENDING` sang `ASSIGNED` và ghi `teacher_id`, `assigned_at`, hạn chấm. |
| BR-105-7 | Khi teacher nộp kết quả, hệ thống chỉ chấp nhận nếu yêu cầu còn ở trạng thái `ASSIGNED` và thuộc teacher hiện tại. |
| BR-105-8 | Mỗi yêu cầu chỉ được hoàn tất một lần. Nộp kết quả nhiều lần không được trả payout nhiều lần. |
| BR-105-9 | Kết quả chấm phải có điểm hợp lệ và nhận xét không rỗng. |
| BR-105-10 | Payout cho teacher phải được tính hoàn toàn theo công thức và nguồn chi trả đã được phê duyệt; client không được quyết định payout. Nhánh dùng quota/gói chỉ được mở sau khi nguồn payout cho nhánh này được chốt. |
| BR-105-11 | Payout cho teacher phải được ghi nhận bằng giao dịch hoặc lịch sử riêng để đối soát. |
| BR-105-12 | Nếu quá hạn chấm, teacher không được nhận payout cho yêu cầu đó. |
| BR-105-13 | Nhận xét và phần sửa lỗi phải được escape khi hiển thị cho người học. |

## API · DB

```
PATCH /api/teacher/grading-requests/{id}/claim
PATCH /api/teacher/grading-requests/{id}/submit-grade
PATCH /api/teacher/grading-requests/{id}/release
```

`service_requests` (đọc/ghi), `credit_transactions` (append nếu payout là điểm theo
chính sách), lịch sử/audit liên quan. Không có `user_credits` để cập nhật số dư giáo viên.

Trong schema hiện có, người nhận là `assignee_id`, điểm chấm là `score`, nhận xét là
`feedback`, payout là `payout`; không coi tên nghiệp vụ `teacher_id`/`teacher_payout`
trong BR là các cột vật lý đã tồn tại. Cách lưu corrections, nhận diện lần giao, hạn nhận
gốc/hạn chấm và căn cứ chi trả cần được duyệt; một `deadline_at` không tự lưu được hai hạn độc lập.

Khôi phục phiên chấm dùng lại PATCH claim theo A7, không thêm endpoint. Response phải
giúp client xác định lần giao hiện hành để submission cũ không áp lên lần giao khác.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nhận và chấm hợp lệ theo chính sách đã duyệt | COMPLETED, đủ kết quả và đúng một lần ghi nhận payout. |
| T2 | Hai giáo viên claim đồng thời | Một thành công, một 409; chỉ người nhận được xem bài. |
| T3 | Claim bài hết hạn/đúng biên hoặc tự chấm | Không nhận được; tự chấm trả 403. |
| T4 | Nộp/trả bài của người khác | 403. |
| T5 | Điểm/nhận xét/corrections không hợp lệ | 422; không trả công. |
| T6 | Hai submission hoặc submit cạnh tranh release/timeout | Chỉ một chuyển trạng thái hợp lệ, không payout hai lần. |
| T7 | Release rồi nhận lại, gửi submission cũ | Không hoàn tất lần giao mới. |
| T8 | Quá hạn chấm | Không payout; thu hồi giao, giữ hạn nhận gốc, không tự gia hạn hàng đợi. |
| T9 | Lỗi ghi payout/kết quả | Rollback cả transaction, không COMPLETED một phần. |
| T10 | Payout do client khai hoặc vượt căn cứ chính sách | Không được sử dụng để chi trả. |
| T11 | Thông báo lỗi sau commit | Kết quả và payout không bị ghi lại. |
| T12 | Bài dùng quota/gói | Chỉ nghiệm thu sau khi chốt nguồn và cách tính payout; không mặc định 0 hay 70% của 0. |
| T13 | Tải lại trang hoặc mất response claim; vẫn ASSIGNED cho mình và còn hạn chấm | Gọi lại claim nhận bản bài/lần giao hiện hành; người nhận, assigned_at, hạn và payout giữ nguyên, không có giao dịch mới. |
| T14 | Gọi lại claim khi bài thuộc giáo viên khác, đã hết hạn chấm hoặc đã đổi trạng thái | Không trả nội dung theo A7, không giành lại bài hoặc gia hạn; áp dụng lỗi quyền/hạn/xung đột tương ứng. |

---

# UC-106 · Xem trạng thái và kết quả chấm

| | |
|---|---|
| **UC-ID** | UC-106 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Người học xem trạng thái xử lý của yêu cầu chấm do mình tạo và xem kết quả sau khi giáo viên hoàn tất. Chỉ yêu cầu `COMPLETED` mới trả điểm, nhận xét và phần sửa lỗi; yêu cầu chưa hoàn tất chỉ hiển thị trạng thái xử lý, không hiển thị kết quả tạm.

Xem lại yêu cầu hoặc kết quả không tiêu thụ thêm quota/điểm và không làm thay đổi mastery hay kết quả bài thi.

## Tiền điều kiện

1. Người dùng đã đăng nhập bằng tài khoản hợp lệ.
2. Yêu cầu cần xem thuộc người dùng hiện tại.
3. Yêu cầu là loại `GRADING`.
4. `COMPLETED` **không** phải tiền điều kiện của toàn UC vì người dùng vẫn cần xem được trạng thái PENDING/ASSIGNED/EXPIRED.

## Hậu điều kiện

1. Không thay đổi trạng thái yêu cầu.
2. Không thay đổi quota, số dư, bài nguồn, mastery hoặc điểm bài thi.
3. Không trả payout hoặc thông tin tài chính nội bộ cho người học.
4. Chỉ dữ liệu thuộc yêu cầu của người dùng hiện tại được trả về.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở danh sách yêu cầu chấm hoặc mở một yêu cầu cụ thể. |
| 2 | Client | Gửi yêu cầu danh sách/chi tiết bằng phiên hiện tại. |
| 3 | System | Xác định người dùng từ phiên và kiểm tra quyền sở hữu yêu cầu. |
| 4 | System | Kiểm tra trạng thái yêu cầu. |
| 5 | System | Nếu `COMPLETED`, lấy snapshot bài đã gửi, điểm, nhận xét và phần sửa lỗi được phép hiển thị. |
| 6 | System | Trả DTO dành cho người học, không chứa payout hoặc thông tin nội bộ không cần thiết. |
| 7 | Client | Hiển thị bài gốc và kết quả chấm theo cách an toàn, không thực thi HTML/script từ nội dung. |

## Luồng thay thế

**A1 — PENDING:** hiển thị “Đang chờ giáo viên nhận” cùng thông tin thời hạn được phép xem; không trả kết quả tạm.

**A2 — ASSIGNED:** hiển thị “Đang được chấm”; không trả score/feedback/corrections trước khi COMPLETED.

**A3 — EXPIRED hoặc đã hủy:** hiển thị trạng thái kết thúc và thông tin hoàn quyền sử dụng nếu có; không trả kết quả chấm.

**A4 — Xem lại kết quả cũ:** trả cùng dữ liệu đã lưu; không tiêu thụ thêm quota/điểm.

**A5 — Corrections bị lỗi cấu trúc nhưng score/feedback vẫn hợp lệ:** trả phần hợp lệ, bỏ phần corrections lỗi và ghi lỗi nội bộ. Nếu không thể xác định kết quả hợp lệ thì báo lỗi tải kết quả.

**A6 — Danh tính giáo viên:** chỉ hiển thị khi chính sách sản phẩm đã cho phép; không tự trả PII chỉ vì dữ liệu nội bộ có trường người chấm.

**A7 — Khiếu nại chuyên môn:** không thuộc UC này và không tự biến thành tranh chấp điểm UC-102. Chỉ triển khai khi có chính sách riêng được phê duyệt.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Phiên không hợp lệ | Không trả yêu cầu/kết quả. |
| `REQUEST_NOT_OWNED` | 403 | Yêu cầu không tồn tại, sai loại hoặc không thuộc người dùng | Không lộ dữ liệu. |
| `INVALID_FILTER` | 422 | Tham số danh sách không hợp lệ | Trả lỗi validation. |
| `RESULT_READ_ERROR` | 500 | Không đọc được kết quả hợp lệ | Báo lỗi; không dựng kết quả giả hoặc báo “chưa chấm”. |

## Business rule

| # | Rule |
| --- | --- |
| BR-106-1 | Người học chỉ được xem kết quả chấm của yêu cầu do chính mình tạo. |
| BR-106-2 | Chỉ yêu cầu ở trạng thái `COMPLETED` mới trả điểm, nhận xét và phần sửa lỗi. |
| BR-106-3 | Yêu cầu chưa chấm xong chỉ trả trạng thái xử lý, không trả điểm rỗng hoặc kết quả tạm. |
| BR-106-4 | Response cho người học không được chứa `teacher_payout` hoặc thông tin tài chính nội bộ. |
| BR-106-5 | Feedback và corrections phải được escape khi hiển thị. |
| BR-106-6 | Mặc định response người học không trả danh tính teacher. Chỉ hiển thị khi có chính sách sản phẩm được phê duyệt cho phép công khai danh tính người chấm. |
| BR-106-7 | Kết quả chấm đã hoàn tất phải được giữ để người học xem lại theo chính sách lưu trữ đã phê duyệt; UC này không tự cam kết lưu vĩnh viễn. |
| BR-106-8 | Trước khi mở chức năng chấm trả phí trên production, phải có quyết định rõ về việc có hay không có luồng khiếu nại/phúc khảo kết quả chuyên môn; UC-102 không thay thế luồng này. |

## API · DB

```
GET /api/me/grading-requests
GET /api/me/grading-requests/{id}
```

`service_requests` (đọc), luôn giới hạn `kind = 'GRADING'` và người dùng từ phiên.
DTO trả bài gốc/kết quả đã lưu, không trả toàn bộ payload/result tùy ý.
Không tự bổ sung API khiếu nại, sửa điểm thi hoặc cập nhật mastery.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | COMPLETED của mình | Bản bài đã gửi, điểm, nhận xét và phần sửa lỗi hợp lệ. |
| T2 | Yêu cầu của người khác, không tồn tại hoặc sai loại | 403, không lộ dữ liệu. |
| T3 | PENDING/ASSIGNED | Trạng thái, không có kết quả tạm hoặc score rỗng như kết quả chính thức. |
| T4 | EXPIRED/đã hủy theo mapping được duyệt | Không trả kết quả chấm; trạng thái xử lý đúng. |
| T5 | Điểm chấm bằng 0 | Hiển thị điểm 0, không coi là thiếu kết quả. |
| T6 | Kiểm cả trường ngoài và dữ liệu lồng | Không lộ payout/tài chính nội bộ hoặc danh tính chưa được phép. |
| T7 | Feedback/corrections/bài viết chứa script | Render an toàn, không thực thi mã. |
| T8 | Corrections hỏng nhưng điểm/feedback hợp lệ | Hiển thị phần hợp lệ, ghi lỗi nội bộ; không vừa trả 500 vừa coi thành công. |
| T9 | Xem lại và phân trang danh sách | Chỉ dữ liệu của mình, không trừ lượt/điểm. |

---

# UC-107 · Hết hạn yêu cầu chấm chưa được nhận và hoàn quyền sử dụng

| | |
|---|---|
| **UC-ID** | UC-107 · **Actor** Scheduler (kích hoạt theo thời gian) · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

UC tự động này định kỳ tìm các yêu cầu chấm vẫn ở trạng thái `PENDING` nhưng đã hết hạn để giáo viên nhận. Với mỗi yêu cầu đủ điều kiện, hệ thống kết thúc yêu cầu và gọi UC-097 để hoàn **đúng nguồn** quyền sử dụng mà người học đã tiêu thụ.

UC này không trực tiếp xử lý yêu cầu đang `ASSIGNED`. Trường hợp giáo viên đã nhận nhưng quá hạn chấm phải được UC-105 kết thúc lần giao trước khi yêu cầu có thể được UC-107 xem xét.

> **Điều kiện chặn triển khai:** hạn nhận bài phải được phê duyệt. Chính sách hoàn quota qua kỳ tháng khác cũng phải được quyết định trước khi nghiệm thu nhánh đó.

## Tiền điều kiện

1. Chính sách hạn nhận bài đã được phê duyệt và yêu cầu có thời điểm hết hạn xác định được.
2. Hệ thống xác định được nguồn quota hoặc điểm đã tiêu thụ khi tạo yêu cầu.
3. Scheduler đang chạy theo lịch đã cấu hình và dùng cùng quy ước thời gian của hệ thống.
4. Một vòng chạy có thể không tìm thấy yêu cầu; đây là kết quả bình thường.

## Hậu điều kiện

### Với yêu cầu đủ điều kiện

1. Yêu cầu không còn ở trạng thái `PENDING` và không thể được giáo viên nhận sau khi hết hạn.
2. UC-097 hoàn đúng nguồn quyền sử dụng.
3. Mỗi yêu cầu chỉ được hoàn một lần.
4. Thay đổi trạng thái và hoàn quyền sử dụng được ghi nhất quán.

### Với yêu cầu không còn đủ điều kiện

1. Không thay đổi trạng thái.
2. Không hoàn quota hoặc điểm.

### Khi xử lý thất bại

1. Không để trạng thái yêu cầu và kết quả hoàn lệch nhau.
2. Yêu cầu vẫn có thể được xử lý lại trong lần chạy sau nếu lỗi có thể khắc phục.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Scheduler | Kích hoạt vòng kiểm tra theo lịch. |
| 2 | System | Tìm các yêu cầu `GRADING/PENDING` đã đến hạn nhận theo lô giới hạn. |
| 3 | System | Với từng yêu cầu, kiểm tra lại trạng thái và deadline tại thời điểm xử lý. |
| 4 | System | Nếu yêu cầu vẫn đủ điều kiện, chuyển yêu cầu sang trạng thái hết hạn theo mô hình trạng thái đã được duyệt. |
| 5 | System | Gọi UC-097 để hoàn đúng nguồn quyền sử dụng. |
| 6 | System | Hoàn tất thay đổi trạng thái và hoàn như một thao tác nhất quán. |
| 7 | System | Gửi thông báo cho người học sau khi commit. |
| 8 | System | Tiếp tục yêu cầu tiếp theo; lỗi một yêu cầu không rollback các yêu cầu khác đã xử lý xong. |

## Luồng thay thế

**A1 — Giáo viên vừa nhận bài:** nếu claim đã commit trước khi UC-107 xử lý, bỏ qua yêu cầu và không hoàn.

**A2 — Người học vừa hủy:** nếu yêu cầu đã được hủy và hoàn ở UC-103, không xử lý/hoàn lần hai.

**A3 — Yêu cầu đang ASSIGNED nhưng giáo viên quá hạn chấm:** UC-107 không trực tiếp hoàn. UC-105 phải kết thúc lần giao trước.

**A4 — UC-097 hoàn thất bại:** không ghi nhận trạng thái hết hạn như đã xử lý thành công; cho phép xử lý lại.

**A5 — Scheduler ngừng hoặc bị trễ:** khi hoạt động lại, xử lý các yêu cầu tồn đọng đã quá hạn; không làm thay đổi deadline gốc.

**A6 — Thông báo thất bại sau commit:** không đảo ngược trạng thái hoặc khoản hoàn đã hoàn tất.

## Bảng exception

| Mã lỗi/Tình huống | Phạm vi | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `DEADLINE_UNDEFINED` | Nội bộ | Yêu cầu thiếu deadline hợp lệ | Không đoán hạn; ghi lỗi vận hành. |
| `REFUND_SOURCE_UNKNOWN` | Nội bộ | Không xác định được nguồn đã tiêu thụ | Không tự cộng quota/điểm; đưa vào đối soát. |
| `REFUND_PROCESSING_ERROR` | Nội bộ | UC-097 thất bại | Không để trạng thái/hoàn lệch nhau; xử lý lại. |
| `SCHEDULER_NOT_RUNNING` | Vận hành | Job không chạy/tồn đọng tăng | Cảnh báo vận hành và xử lý tồn đọng khi khôi phục. |

## Business rule

| # | Rule |
| --- | --- |
| BR-107-1 | UC tự động hết hạn chỉ được bật sau khi hạn giáo viên phải nhận bài được chốt và lưu được trên yêu cầu. |
| BR-107-2 | Hệ thống chỉ hoàn điểm cho yêu cầu còn ở trạng thái `PENDING` và đã quá hạn nhận bài. |
| BR-107-3 | Trước khi hoàn, hệ thống phải chuyển yêu cầu sang trạng thái hết hạn để teacher không thể nhận bài sau khi người học đã được hoàn. |
| BR-107-4 | Việc đổi trạng thái và hoàn điểm phải nhất quán. Không được hoàn điểm nhưng yêu cầu vẫn còn trong hàng đợi. |
| BR-107-5 | Hoàn điểm phải dùng luồng hoàn điểm chuẩn, không dùng điều chỉnh thủ công. |
| BR-107-6 | Mỗi yêu cầu chỉ được hoàn một lần. Job chạy lại không được hoàn trùng. |
| BR-107-7 | Nếu teacher nhận bài đúng lúc job hoàn điểm chạy, chỉ một trong hai thao tác được thành công. |
| BR-107-8 | Scheduler hoàn điểm phải có health check hoặc báo cáo số yêu cầu quá hạn chưa xử lý. |
| BR-107-9 | Giờ xử lý deadline thống nhất theo giờ Việt Nam hoặc timestamp chuẩn đã chốt, không để mỗi nơi hiểu một kiểu. |

## API · DB

Không endpoint; tác vụ định kỳ.

`service_requests` (đọc/ghi, `kind = 'GRADING'`), quota và `credit_transactions`
qua UC-097. Không dùng `grading_requests`, `feature_usage` hoặc `user_credits` như bảng.
Mỗi yêu cầu một transaction bao trùm trạng thái và hoàn; không gom toàn bộ hàng trăm yêu cầu
vào một transaction hoặc commit hoàn riêng trước khi trạng thái được chốt.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | PENDING trước/đúng/sau hạn nhận | Chưa đến hạn không xử lý; đúng/sau hạn đủ điều kiện EXPIRED và hoàn. |
| T2 | Job chưa chạy, giáo viên thử nhận bài đã quá hạn | Claim bị chặn; không phụ thuộc job dọn. |
| T3 | Claim/hủy/expire cạnh tranh | Chỉ một nhánh có hiệu lực; không vừa hoàn vừa nhận bài. |
| T4 | Job chạy lại hoặc hai instance cùng quét | Mỗi yêu cầu được hoàn tối đa một lần. |
| T5 | UC-097 lỗi | Trạng thái chưa commit EXPIRED; không có hoàn một phần. |
| T6 | Nguồn tiêu thụ là quota | Hoàn quota theo kỳ/chính sách, không cộng điểm. |
| T7 | Một yêu cầu lỗi trong lô | Các yêu cầu khác đã commit không bị rollback. |
| T8 | ASSIGNED chưa được nhánh timeout UC-105 xử lý | UC-107 không trực tiếp hoàn. |
| T9 | Sau khi hoàn và khi thông báo lỗi | Không trở lại hàng đợi; không hoàn lại chỉ để gửi thông báo. |
| T10 | Job hoạt động lại sau downtime | Xử lý được tập tồn đọng hợp lệ; báo số còn lỗi và tuổi tồn đọng. |
| T11 | Kiểm lịch và biên thời gian | Zone khai báo đúng, các luồng đọc/claim/expire dùng cùng quy ước hạn. |

---

# Tổng hợp exception nhóm 6a + 6b

## Mười hai exception quan trọng nhất

| # | UC | Exception | Vì sao |
| --- | --- | --- | --- |
| 1 | UC-094 | `BALANCE_BEFORE_AFTER_MISMATCH` | Ghi sổ cái nối đúng số dư theo tài khoản, kể cả hai mã nạp đồng thời và giao dịch đầu tiên. |
| 2 | UC-097 | `REFUND_NOT_TRIGGERED` | Hành động thất bại cuối cùng có tiêu thụ nhưng thiếu hoàn phải được phát hiện, không chờ khiếu nại. |
| 3 | UC-099 | `WEAK_RANDOM_USED` / `SEQUENTIAL_CODES` | Mã có giá trị phải dùng SecureRandom, không sinh theo quy luật dễ đoán. |
| 4 | UC-098 | `OTHER_USER_TRANSACTIONS_LEAKED` | Mọi truy vấn và liên hệ hoàn phải giới hạn đúng người dùng từ phiên. |
| 5 | UC-107 | `REFUND_RACE_WITH_CLAIM` | Claim, hủy và hết hạn không được cùng thành công cho một trạng thái yêu cầu. |
| 6 | UC-097 | `REFUND_WITHOUT_DEDUCT` | Với nguồn điểm phải có DEDUCT gốc; nguồn quota dùng bằng chứng riêng, không ép có DEDUCT. |
| 7 | UC-094 | `CARD_CODE_IN_LOG` | Không lộ mã qua body, lỗi validation, exception hoặc log DEBUG. |
| 8 | UC-096 | `NOT_IN_TRANSACTION` / `DOUBLE_DEDUCT` | Ghi tiêu thụ cùng hành động; gọi API ngoài sau commit và không trừ lại vì retry. |
| 9 | UC-105 | `NOT_ASSIGNED_TO_ME` + `DOUBLE_PAYOUT` | Kiểm đúng người, lần giao, trạng thái và hạn; hoàn tất và trả công chỉ một lần. |
| 10 | UC-099 | `RAW_CODE_RETRIEVABLE` | Không lấy lại mã thô; mất CSV không được âm thầm tạo thêm lô. |
| 11 | UC-102 | `LEDGER_ROW_MODIFIED` | Sổ cái append-only; sửa sai bằng giao dịch mới đúng loại, không sửa/xóa dòng cũ. |
| 12 | UC-095 | `SUBSCRIPTION_OVERWRITTEN` | Gia hạn giữ thời lượng còn lại, kể cả hai thẻ khác nhau kích hoạt đồng thời. |

Đây là danh sách rủi ro/kiểm soát, không phải tất cả đều là mã HTTP trả cho client.
Trạng thái rỗng, đã hoàn idempotent và kết quả đối soát lệch được mô tả riêng trong từng UC.

## Năm nhóm exception lặp lại

| Nhóm | Xuất hiện ở | Bài học |
| --- | --- | --- |
| **Thay đổi điểm không nhất quán** | UC-094 · UC-095 · UC-096 · UC-097 · UC-102 · UC-105 | Một service trung tâm append ledger; số dư lấy từ dòng cuối, không UPDATE `user_credits`. Bảo vệ cả lần ghi đầu và giao dịch khác loại đồng thời. |
| **Chuyển trạng thái có điều kiện** | UC-103 · UC-105 · UC-107 | Kiểm trạng thái, người sở hữu/nhận, lần giao và deadline dưới cơ chế đồng bộ; trạng thái và hoàn/payout cùng transaction. |
| **Tách biệt quyền hạn** | UC-094 · UC-099 · UC-100 · UC-101 · UC-102 · UC-105 | Chỉ role được cấp mới có quyền; cấm tự điều chỉnh/tự chấm theo BR. Không tự thêm chính sách cấm nạp mã mình tạo; audit giữ trách nhiệm truy vết. |
| **Tiêu thụ rồi thất bại** | UC-096 · UC-097 · UC-103 | API ngoài: commit hành động/tiêu thụ, gọi ngoài transaction, thất bại cuối cùng thì hoàn. Tạo yêu cầu chấm: tạo yêu cầu và tiêu thụ cùng transaction, lỗi trước commit thì rollback. |
| **Lộ dữ liệu qua response** | UC-098 · UC-100 · UC-104 · UC-106 | DTO riêng; không mã/hash thẻ, PII không cần thiết, bài đầy đủ trong hàng đợi hoặc payout trong kết quả người học. |

---

# Khoảng trống thiết kế phát hiện ở nhóm 6a + 6b

| # | Thiếu | UC bị ảnh hưởng | Mức |
| --- | --- | --- | --- |
| 1 | **Kỳ FREE đã chốt:** 10 lượt/tính năng/tháng, reset 00:00 ngày 1 giờ Việt Nam; cột quota/job chưa vì thế được coi là đã triển khai. | UC-093 · UC-096 | Đã chốt nghiệp vụ; cần kiểm chứng chuyển kỳ. |
| 2 | **Payout chưa đủ quyết định thống nhất:** đơn vị, mức, làm tròn 70% của 1 điểm và nguồn trả cho lượt quota/gói; không tự đặt 0 hay ngân sách bù. | UC-103 · UC-104 · UC-105 | Chặn mở luồng chấm trả phí. |
| 3 | **Hạn nhận/hạn chấm:** cần xác nhận hiệu lực giá trị 24/48 giờ trong DB chờ duyệt; lưu hạn nhận gốc độc lập hạn chấm, không cấp vòng chờ mới khi trả bài. | UC-103 · UC-105 · UC-107 | Chặn triển khai chính sách thời hạn đầy đủ. |
| 4 | **Đã có trong V1:** `ref_transaction_id` và unique REFUND theo `tx_type`; không tự thêm lại cùng ràng buộc. | UC-097 | Cần test hoàn đồng thời; chưa bảo vệ nhánh quota chỉ bằng unique ledger. |
| 5 | **Đã có trong V1:** CHECK số dư trước/sau không âm và sau = trước + amount. Kiểu điểm INTEGER hiện có còn lệch BIGINT của AC-07. | UC-093 · UC-094 · UC-096 | CHECK từng dòng chưa đủ chống đua chuỗi; đổi kiểu/schema cần duyệt riêng. |
| 6 | **Chưa đủ dữ liệu/ràng buộc payout theo phần đã trả:** không khẳng định CHECK `payout <= student_paid` hiện hữu hoặc dùng được cho lượt quota. | UC-105 | Phụ thuộc quyết định payout và thiết kế dữ liệu. |
| 7 | **Service trung tâm và đồng bộ ledger theo tài khoản:** không có `user_credits`; `seq`/unique mới nằm trong thiết kế, phải bảo vệ cả giao dịch đầu tiên. | UC-094 → UC-105 | Cần triển khai nghiệp vụ, không tự viết migration trong đợt sửa UC. |
| 8 | **Đối soát chuỗi:** cùng mốc dữ liệu, đủ loại giao dịch và số dư đầu/cuối kỳ; không nhầm điểm với tiền thu bên ngoài. | UC-094 · UC-098 · UC-100 | Cần kiểm thử, không chỉ cộng TOPUP - DEDUCT + REFUND. |
| 9 | **Phát hiện hành động cần hoàn nhưng chưa hoàn:** gồm quota; không đòi DEDUCT của hành động thành công có REFUND. | UC-097 | Cần liên hệ hành động và kết quả tiêu thụ/hoàn. |
| 10 | **Audit finance:** cả phát hành/thay đổi và đọc/xuất sổ cái; có bảng audit không đồng nghĩa mọi luồng đã ghi đủ. | UC-099 → UC-102 | Cần kiểm chứng từng đường thành công/lỗi. |
| 11 | **Liên kết thẻ-gói và snapshot quyền/giá trị lịch sử:** chưa đủ bảo toàn người dùng/thẻ cũ khi sửa gói; không lấy giá mới thay dữ liệu lịch sử. | UC-095 · UC-099 · UC-101 | Chặn nhánh không xác định được quyền đã cấp; cần thiết kế được duyệt. |
| 12 | **Bảo vệ append-only:** bảng và constraint không tự chứng minh quyền DB/các đường ghi đã cấm UPDATE/DELETE ledger. | UC-102 | Cần kiểm soát/kiểm thử, không tự đổi quyền DB trong đợt này. |
| 13 | **Kiểm soát sinh mã:** cần kiểm chứng SecureRandom, độ dài và không dùng mã tuần tự. | UC-099 | Cần kiểm thử/kiểm tra mã khi triển khai. |
| 14 | **Không lộ mã thô:** kiểm cả thành công, lỗi, CSV và mất response; không có đường tải lại mã. | UC-094 · UC-099 | Cần test bảo mật, không chỉ đọc cấu trúc bảng. |
| 15 | **Tham chiếu khiếu nại:** giữ nguồn tiếp nhận hiện hành; unique `dispute_ref` có trong V1, chưa có quyết định thêm bảng/hệ thống disputes. | UC-102 | Không mở rộng scope; unique chưa đủ chống mọi kiểu xử lý lặp. |
| 16 | **Khiếu nại kết quả chuyên môn và danh tính giáo viên:** cần chốt có/không; không đồng nhất với tranh chấp điểm hoặc tự thêm endpoint. | UC-103 · UC-106 | Phải rõ điều kiện trước mở tính năng trả phí. |
| 17 | **Ẩn danh hàng đợi đã có BR-104-4:** không trả danh tính học viên/bài đầy đủ trong list; không hứa bài tự viết không chứa PII. | UC-104 · UC-105 | Đã có yêu cầu; cần test response và quyền đọc bài. |
| 18 | **Hoàn sau thất bại cuối cùng:** retry chưa kết thúc/thành công không hoàn; số lần retry theo tính năng; hoàn quota qua tháng chưa có chính sách. | UC-097 · UC-103 · UC-107 | Không tự sửa bộ đếm tháng mới hoặc đổi quota thành điểm. |
| 19 | **Chống lặp và nguồn tiêu thụ:** lưu bền vững cho cả điểm/quota và bốn tính năng; cần định danh bài tự viết, mapping hủy và lần giao hiện hành. | UC-096 · UC-097 · UC-103 · UC-105 | Thiết kế chưa đủ; không tự thêm cột/trạng thái CANCELLED. |
| 20 | **Ngưỡng điều chỉnh/duyệt hai người:** chưa quyết định; vẫn bắt buộc căn cứ, cấm tự điều chỉnh và không âm số dư. | UC-102 | Không coi là tính năng đã có hoặc tự áp ngưỡng. |
| 21 | **Giám sát scheduler:** số lượng/tuổi yêu cầu quá hạn, lỗi hoàn và khả năng xử lý tồn đọng; không yêu cầu luôn bằng 0 giữa các lần chạy. | UC-105 · UC-107 | Lịch xử lý ASSIGNED quá hạn và ngưỡng cảnh báo phải rõ. |
| 22 | **PAYMENT_SCOPE đã chốt:** tiền thật thu ngoài hệ thống, không cổng thanh toán; các BR chứa điều kiện cũ được bảo toàn và ghi chú đối chiếu. | UC-093 → UC-107 | Không còn là quyết định treo chặn toàn nhóm; các thiếu hụt cụ thể ở trên vẫn còn. |

Các mục "đã có trong V1" chỉ xác nhận schema, không xác nhận service, API hoặc test đã
được triển khai. Điểm thiếu quyết định không được lấp bằng giá trị giả định để tuyên bố
tài liệu đã sẵn sàng code.

Chính sách chuyển giữa hai gói trả phí khác loại, các con số hạn mức Premium và quy ước
độ dài bài tiếng Trung cần được thống nhất ở nguồn đặc tả liên quan. Không tự sửa BR,
spec, OpenAPI hoặc migration trong lần cập nhật file này.

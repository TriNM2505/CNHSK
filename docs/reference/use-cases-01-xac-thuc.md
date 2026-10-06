# CNHSK — Đặc tả Use Case · Nhóm 0 · Xác thực & tài khoản

> **14 use case:** UC-001 → UC-014
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
> **Bản final** · cập nhật 2026-10-01

---

## Quy ước đặc tả

| Phần | Nghĩa |
|---|---|
| **Tiền điều kiện** | Phải đúng trước khi UC bắt đầu |
| **Hậu điều kiện** | Trạng thái hệ thống sau khi UC thành công |
| **Luồng chính** | Đường đi khi mọi thứ suôn sẻ |
| **Luồng thay thế** | Đường đi khác vẫn dẫn tới thành công |
| **Exception** | Đường đi thất bại — mã lỗi và xử lý |
| **Business rule** | Quy tắc nghiệp vụ UC phải tuân |

Mã lỗi theo định dạng `{error_code, message, request_id}` — HR-09.

---

# UC-001 · Đăng ký tài khoản bằng email

| | |
|---|---|
| **ID** | UC-001 |
| **Actor chính** | `GUEST` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Khách chưa có tài khoản tạo tài khoản mới bằng email và mật khẩu. Sau khi tạo, hệ thống
gửi email xác thực. Tài khoản chưa xác thực vẫn đăng nhập được nhưng bị hạn chế.

## Tiền điều kiện

- Người dùng chưa đăng nhập
- Email chưa tồn tại trong hệ thống

## Hậu điều kiện

- Một dòng mới trong `auth.users` với `email_verified_at = NULL`
- Role `USER` được gán trong `auth.user_roles`
- Một token loại `EMAIL_VERIFY` trong `auth.auth_tokens`, hết hạn sau 24 giờ
- Email xác thực đã gửi
- Ví điểm khởi tạo trong `learning.user_credits` với số dư 0
- Gói `FREE` được gán trong `learning.user_subscriptions`

## Luồng chính

1. `GUEST` mở trang đăng ký
2. Nhập: họ tên, email, mật khẩu, xác nhận mật khẩu
3. Tick đồng ý điều khoản
4. Bấm "Đăng ký"
5. Frontend validate sơ bộ (định dạng email, độ dài mật khẩu, hai mật khẩu khớp)
6. Gửi `POST /api/auth/register`
7. Server validate lại toàn bộ
8. Server kiểm email chưa tồn tại
9. Server hash mật khẩu bằng bcrypt cost ≥12
10. Server tạo `users` + gán role `USER` + tạo `user_credits` + gán gói `FREE` — **cùng một transaction**
11. Server sinh token `EMAIL_VERIFY`, lưu **hash** của token
12. Server gửi email chứa link xác thực
13. Server trả **201** kèm thông tin người dùng (**không** kèm hash mật khẩu)
14. Frontend chuyển sang trang "Kiểm tra email của bạn"

## Luồng thay thế

**A1 · Đăng ký rồi đăng nhập luôn không chờ xác thực**
Sau bước 13, hệ thống có thể trả luôn access token để người dùng vào dùng ngay,
với trạng thái `PENDING_VERIFY`. Các tính năng bị hạn chế: đăng bài, tham gia cuộc thi.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `EMAIL_ALREADY_EXISTS` | Email đã có tài khoản | **409** | Báo "Email này đã được dùng", gợi ý đăng nhập hoặc quên mật khẩu |
| `INVALID_EMAIL_FORMAT` | Email sai định dạng | **422** | Chỉ rõ field `email` |
| `WEAK_PASSWORD` | Mật khẩu không đủ mạnh | **422** | Nêu rõ yêu cầu: tối thiểu N ký tự, có chữ và số |
| `PASSWORD_MISMATCH` | Hai mật khẩu không khớp | **422** | Chỉ rõ field `confirmPassword` |
| `TERMS_NOT_ACCEPTED` | Chưa tick điều khoản | **422** | Chỉ rõ field `acceptTerms` |
| `EMAIL_SEND_FAILED` | Không gửi được email | **201** + cảnh báo | **Vẫn tạo tài khoản.** Trả 201 kèm flag `emailSent: false`, cho phép gửi lại |
| `RATE_LIMIT_EXCEEDED` | Đăng ký quá nhiều từ một IP | **429** | Giới hạn N lần/giờ mỗi IP, chống tạo tài khoản hàng loạt |
| `INTERNAL_ERROR` | Transaction thất bại | **500** | Rollback toàn bộ, không để lại tài khoản nửa vời |

> ⚠️ **Exception quan trọng nhất là `EMAIL_SEND_FAILED`.** Nếu rollback cả tài khoản khi
> email lỗi thì người dùng không đăng ký được chỉ vì SMTP tạm sự cố. Phải tách: tạo tài
> khoản trong transaction, gửi email **ngoài** transaction.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Mật khẩu hash bcrypt cost ≥12 hoặc argon2id — **HR-01** |
| BR-02 | Không bao giờ trả hash mật khẩu ra response |
| BR-03 | Token xác thực lưu **hash**, không lưu token thô |
| BR-04 | Email so sánh không phân biệt chữ hoa thường, lưu dạng chữ thường |
| BR-05 | Mọi tài khoản mới mặc định role `USER` và gói `FREE` |
| BR-06 | Tạo user + role + ví + gói trong **cùng transaction** |

## API

```
POST /api/auth/register
Body: { fullName, email, password, confirmPassword, acceptTerms }
201:  { id, fullName, email, emailVerified: false, emailSent: true }
409:  { error_code: "EMAIL_ALREADY_EXISTS", message, request_id }
422:  { error_code, message, details: [{field, issue}], request_id }
```

## Bảng DB liên quan

`auth.users` · `auth.roles` · `auth.user_roles` · `auth.auth_tokens` ·
`learning.user_credits` · `learning.user_subscriptions`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Dữ liệu hợp lệ | 201, có dòng trong `users`, email gửi đi |
| 2 | Email đã tồn tại | 409, không tạo dòng mới |
| 3 | Email viết HOA đã tồn tại chữ thường | 409 — kiểm không phân biệt hoa thường |
| 4 | Mật khẩu 5 ký tự | 422 |
| 5 | Hai mật khẩu khác nhau | 422 |
| 6 | Không tick điều khoản | 422 |
| 7 | SMTP lỗi | 201 + `emailSent: false`, tài khoản vẫn tạo |
| 8 | Gọi 10 lần liên tiếp từ một IP | Lần thứ N trả 429 |
| 9 | Kiểm response | **Không** chứa `passwordHash` |

---

# UC-002 · Xác thực email qua link

| | |
|---|---|
| **ID** | UC-002 |
| **Actor chính** | `GUEST` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng bấm link trong email để xác thực địa chỉ email. Sau khi xác thực, tài khoản
được mở đầy đủ tính năng.

## Tiền điều kiện

- Tài khoản tồn tại với `email_verified_at = NULL`
- Token `EMAIL_VERIFY` còn hạn và chưa dùng

## Hậu điều kiện

- `users.email_verified_at` được set thời điểm hiện tại
- Token `EMAIL_VERIFY` bị thu hồi (`revoked_at` được set)
- Người dùng dùng được đầy đủ tính năng

## Luồng chính

1. Người dùng mở email, bấm link `https://cnhsk.com/verify-email?token=XXX`
2. Frontend gọi `POST /api/auth/verify-email` với token
3. Server hash token nhận được, tìm trong `auth_tokens`
4. Server kiểm token: đúng loại, còn hạn, chưa thu hồi
5. Server set `email_verified_at` và thu hồi token — **cùng transaction**
6. Server trả **200**
7. Frontend hiện "Xác thực thành công", chuyển tới trang đăng nhập hoặc trang chủ

## Luồng thay thế

**A1 · Người dùng đã đăng nhập khi bấm link**
Bỏ qua bước chuyển tới đăng nhập, về thẳng trang chủ.

**A2 · Yêu cầu gửi lại email xác thực**
Nếu token hết hạn, người dùng bấm "Gửi lại" → `POST /api/auth/resend-verification` →
thu hồi token cũ, sinh token mới, gửi email mới.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `TOKEN_INVALID` | Token không tồn tại hoặc sai | **400** | "Link không hợp lệ", cho nhập email để gửi lại |
| `TOKEN_EXPIRED` | Token quá 24 giờ | **410** | "Link đã hết hạn", hiện nút "Gửi lại email" |
| `TOKEN_ALREADY_USED` | Token đã thu hồi | **409** | "Email đã được xác thực rồi", chuyển tới đăng nhập |
| `ALREADY_VERIFIED` | `email_verified_at` đã có giá trị | **200** | **Không báo lỗi** — coi như thành công, tránh gây hoang mang |
| `RATE_LIMIT_EXCEEDED` | Gửi lại quá nhiều lần | **429** | Giới hạn 3 lần/giờ |

> ⚠️ `ALREADY_VERIFIED` trả **200 không phải lỗi** là quyết định có chủ đích. Người dùng
> bấm link hai lần (ví dụ mở trong hai tab) không nên thấy thông báo lỗi.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Token so sánh bằng **hash**, không so token thô |
| BR-02 | Token dùng một lần — xác thực xong thu hồi ngay |
| BR-03 | Token hết hạn sau **24 giờ** |
| BR-04 | Set `email_verified_at` và thu hồi token trong cùng transaction |
| BR-05 | Không tiết lộ email nào gắn với token (chống dò) |

## API

```
POST /api/auth/verify-email
Body: { token }
200:  { verified: true }
400:  { error_code: "TOKEN_INVALID", ... }
410:  { error_code: "TOKEN_EXPIRED", ... }

POST /api/auth/resend-verification
Body: { email }
200:  { sent: true }   ← luôn trả 200 dù email không tồn tại (chống dò email)
429:  { error_code: "RATE_LIMIT_EXCEEDED", ... }
```

## Bảng DB liên quan

`auth.users` · `auth.auth_tokens`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Token hợp lệ | 200, `email_verified_at` được set |
| 2 | Token sai | 400 |
| 3 | Token quá 24h | 410 |
| 4 | Token đã dùng | 409 |
| 5 | Bấm link 2 lần | Lần 2 trả 200, không lỗi |
| 6 | Gửi lại với email không tồn tại | **200** (không tiết lộ) |
| 7 | Gửi lại 5 lần trong 1 giờ | Lần thứ 4 trả 429 |

---

# UC-003 · Đăng nhập trên web (cookie)

| | |
|---|---|
| **ID** | UC-003 |
| **Actor chính** | `GUEST` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng đăng nhập từ web chính `cnhsk.com`. Token được đặt trong cookie ở tên miền cha
`.cnhsk.com` để trang game `game.cnhsk.com` dùng chung.

## Tiền điều kiện

- Tài khoản tồn tại
- Tài khoản không bị khóa (`locked_until` là NULL hoặc đã qua)
- Tài khoản không bị ban

## Hậu điều kiện

- Cookie `access_token` được set với đủ 5 thuộc tính (HR-03)
- Cookie `refresh_token` được set, `HttpOnly`
- Một dòng token `REFRESH` trong `auth.auth_tokens` (lưu hash)
- `users.failed_login_count` reset về 0
- `users.last_login_at` cập nhật

## Luồng chính

1. `GUEST` mở trang đăng nhập
2. Nhập email và mật khẩu
3. Bấm "Đăng nhập"
4. Gửi `POST /api/auth/login`
5. Server tìm user theo email (chữ thường)
6. Server kiểm trạng thái tài khoản — chưa bị khóa, chưa bị ban
7. Server so mật khẩu bằng bcrypt **theo thời gian hằng định**
8. Server sinh access token (JWT) và refresh token
9. Server lưu hash refresh token vào `auth_tokens`
10. Server reset `failed_login_count`, cập nhật `last_login_at`
11. Server set hai cookie với `domain=cnhsk.com` · `HttpOnly` · `Secure` · `SameSite=Lax` · `maxAge`
12. Server trả **200** kèm thông tin người dùng và danh sách role
13. Frontend chuyển hướng theo role: `USER` → trang học · các role quản trị → trang quản trị

## Luồng thay thế

**A1 · Đăng nhập khi chưa xác thực email**
Vẫn cho đăng nhập, trả thêm `emailVerified: false`. Frontend hiện banner nhắc xác thực.
Các tính năng bị hạn chế: đăng bài, tham gia cuộc thi.

**A2 · Tài khoản có nhiều role**
Trả toàn bộ danh sách role. Frontend cho chọn "vào với vai nào" hoặc mặc định vai cao nhất.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `INVALID_CREDENTIALS` | Email không tồn tại **HOẶC** mật khẩu sai | **401** | **Cùng một thông báo cho cả hai** — chống dò email |
| `ACCOUNT_LOCKED` | `locked_until` còn hiệu lực | **423** | Báo còn bao nhiêu phút. **Đúng mật khẩu vẫn trả 423** |
| `ACCOUNT_BANNED` | `banned_at` có giá trị | **403** | "Tài khoản đã bị vô hiệu hóa", kèm cách liên hệ |
| `ACCOUNT_SUSPENDED` | `suspended_at` có giá trị | **403** | Báo lý do treo và thời hạn |
| `RATE_LIMIT_EXCEEDED` | Quá N lần thử/IP/khoảng thời gian | **429** | Giới hạn theo IP, không chỉ theo tài khoản |
| `VALIDATION_ERROR` | Thiếu email hoặc mật khẩu | **422** | Chỉ rõ field |

> 🔴 **Exception nguy hiểm nhất: `INVALID_CREDENTIALS` phải giống nhau cho hai trường hợp.**
> Nếu trả "Email không tồn tại" và "Mật khẩu sai" khác nhau thì kẻ tấn công dò được
> email nào có trong hệ thống — lỗ hổng *user enumeration*.

> ⚠️ **`ACCOUNT_LOCKED` phải trả 423 kể cả khi mật khẩu đúng.** Nếu mật khẩu đúng thì cho
> vào thì cơ chế khóa vô nghĩa.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Cookie đủ 5 thuộc tính: `domain=cnhsk.com` · `httpOnly` · `secure` · `sameSite=Lax` · `maxAge` — **HR-03** |
| BR-02 | Bỏ `domain` thì `game.cnhsk.com` không nhận được cookie → luồng đăng nhập chung hỏng |
| BR-03 | So mật khẩu theo **thời gian hằng định** — chống timing attack |
| BR-04 | Thông báo lỗi giống nhau cho email sai và mật khẩu sai |
| BR-05 | Refresh token lưu **hash**, không lưu thô |
| BR-06 | Rate limit theo **IP**, không chỉ theo tài khoản |
| BR-07 | Không log mật khẩu dù ở bất kỳ mức log nào |

## API

```
POST /api/auth/login
Body: { email, password }
200:  Set-Cookie: access_token=...; Domain=cnhsk.com; HttpOnly; Secure; SameSite=Lax; Max-Age=...
      Set-Cookie: refresh_token=...; Domain=cnhsk.com; HttpOnly; Secure; SameSite=Lax; Max-Age=...
      Body: { id, fullName, email, emailVerified, roles: ["USER"] }
401:  { error_code: "INVALID_CREDENTIALS", message: "Email hoặc mật khẩu không đúng", ... }
423:  { error_code: "ACCOUNT_LOCKED", message, details: { unlockAt }, ... }
```

## Bảng DB liên quan

`auth.users` · `auth.user_roles` · `auth.roles` · `auth.auth_tokens`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Email + mật khẩu đúng | 200, hai cookie được set đủ 5 thuộc tính |
| 2 | Email không tồn tại | 401 `INVALID_CREDENTIALS` |
| 3 | Mật khẩu sai | 401 **cùng thông báo như case 2** |
| 4 | Tài khoản đang khóa, mật khẩu **đúng** | **423** — không cho vào |
| 5 | Tài khoản bị ban | 403 |
| 6 | Chưa xác thực email | 200 + `emailVerified: false` |
| 7 | Kiểm cookie | Có `Domain=cnhsk.com`, `HttpOnly`, `Secure`, `SameSite=Lax` |
| 8 | Nhiều role | Trả đủ danh sách role |
| 9 | Thử 20 lần từ một IP | Trả 429 trước khi hết 20 |

---

# UC-004 · Đăng nhập trên mobile (header)

| | |
|---|---|
| **ID** | UC-004 |
| **Actor chính** | `GUEST` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Ứng dụng React Native đăng nhập và nhận token trong **body response**, không dùng cookie.
App tự lưu token vào secure storage và gửi qua header `Authorization` ở các request sau.

## Tiền điều kiện

Giống UC-003.

## Hậu điều kiện

- Access token và refresh token trả trong **body**, không set cookie
- Một dòng token `REFRESH` trong `auth.auth_tokens`
- App lưu token vào secure storage của thiết bị

## Luồng chính

1. Người dùng mở app, nhập email và mật khẩu
2. App gửi `POST /api/auth/login` kèm header `X-Client-Type: mobile`
3. Server xác thực giống UC-003 bước 5–10
4. Server thấy `X-Client-Type: mobile` → **không set cookie**
5. Server trả **200** kèm `accessToken` và `refreshToken` trong body
6. App lưu hai token vào secure storage
7. App gắn `Authorization: Bearer {accessToken}` vào mọi request sau

## Luồng thay thế

**A1 · Không có header `X-Client-Type`**
Server mặc định trả **cả hai**: set cookie và trả token trong body. Web bỏ qua phần body,
mobile bỏ qua cookie. Cách này an toàn hơn là đoán loại client.

## Exception

Giống UC-003, thêm:

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `SECURE_STORAGE_UNAVAILABLE` | Thiết bị không có secure storage | — | **Lỗi phía client.** App báo người dùng, không lưu token vào chỗ không an toàn |

## Business rule

| # | Rule |
|---|---|
| BR-01 | `JwtFilter` đọc **cookie trước, không có thì đọc header** — thiếu một trong hai là một loại client không đăng nhập được |
| BR-02 | Token trong body chỉ trả cho client mobile hoặc khi không xác định được loại |
| BR-03 | App **không** lưu token vào `AsyncStorage` thường — phải dùng secure storage |
| BR-04 | Các rule còn lại giống UC-003 |

## API

```
POST /api/auth/login
Header: X-Client-Type: mobile
Body: { email, password }
200:  { id, fullName, email, roles, accessToken, refreshToken, expiresIn }
      (KHÔNG có Set-Cookie)
```

## Bảng DB liên quan

Giống UC-003.

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Login với `X-Client-Type: mobile` | 200, có token trong body, **không** có `Set-Cookie` |
| 2 | Login không có header | 200, có **cả** cookie và token trong body |
| 3 | Gọi API khác với header `Authorization` | Được chấp nhận |
| 4 | Gọi API khác không có token | 401 |

---

# UC-005 · Đăng nhập vào trang game bằng cookie chung

| | |
|---|---|
| **ID** | UC-005 |
| **Actor chính** | `USER` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng đã đăng nhập ở `cnhsk.com` mở `game.cnhsk.com` và **không phải đăng nhập lại**
— cookie ở tên miền cha tự động được gửi kèm.

## Tiền điều kiện

- Đã đăng nhập thành công ở `cnhsk.com` (UC-003)
- Cookie `access_token` còn hạn, có `Domain=cnhsk.com`

## Hậu điều kiện

- Trang game biết người dùng là ai
- Không sinh token mới

## Luồng chính

1. `USER` đang ở `cnhsk.com`, bấm nút "Chơi game"
2. Trình duyệt mở `game.cnhsk.com`
3. Trang game load, gọi `GET /api/auth/me`
4. Trình duyệt **tự động** gửi cookie vì `Domain=cnhsk.com` phủ cả tên miền phụ
5. Server đọc cookie, xác thực JWT
6. Server trả **200** kèm thông tin người dùng
7. Trang game hiện tên người dùng, cho phép chơi và lưu điểm

## Luồng thay thế

**A1 · Cookie hết hạn khi đang ở trang game**
Trang game gọi `POST /api/auth/refresh` (UC-006). Thành công thì tiếp tục; thất bại thì
chuyển về `cnhsk.com/login`.

**A2 · Mở trực tiếp `game.cnhsk.com` mà chưa đăng nhập**
Trang game hiện chế độ khách: chơi được nhưng **không lưu điểm**, không vào xếp hạng.
Hoặc chuyển thẳng tới trang đăng nhập — tùy quyết định UX.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `NO_TOKEN` | Không có cookie | **401** | Chuyển về `cnhsk.com/login?returnUrl=game` |
| `TOKEN_EXPIRED` | Cookie hết hạn | **401** | Thử refresh (UC-006), thất bại thì về login |
| `CORS_BLOCKED` | `game.cnhsk.com` không có trong danh sách CORS | — | 🔴 **Lỗi cấu hình.** Phải khai báo cả hai tên miền — HR-04 |
| `COOKIE_NOT_SENT` | Cookie set thiếu `Domain` | — | 🔴 **Lỗi cấu hình.** Trang game không nhận được cookie — HR-03 |

> 🔴 **Hai exception cuối là lỗi cấu hình, không phải lỗi runtime.** Chúng xuất hiện khi
> dev quên khai báo `Domain` cho cookie hoặc quên thêm `game.cnhsk.com` vào CORS.
> Tài liệu kiến trúc gọi đây là *"chỗ hay mất cả buổi để sửa"*.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Cookie phải có `Domain=cnhsk.com` — thiếu là hỏng toàn bộ UC này |
| BR-02 | CORS khai báo **rõ cả hai** tên miền `cnhsk.com` và `game.cnhsk.com`, không dùng `*` — HR-04 |
| BR-03 | `withCredentials: true` phía client khi gọi cross-subdomain |
| BR-04 | Trang game **không** tự sinh token — chỉ dùng token đã có |
| BR-05 | ⚠️ `SameSite=Lax` **không** chặn CSRF giữa các tên miền phụ cùng site — cần CSRF token cho thao tác ghi |

## API

```
GET /api/auth/me
Cookie: access_token=...    (trình duyệt tự gửi)
200:  { id, fullName, email, roles }
401:  { error_code: "NO_TOKEN" | "TOKEN_EXPIRED", ... }
```

## Bảng DB liên quan

`auth.users` · `auth.user_roles`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng nhập ở `cnhsk.com`, mở `game.cnhsk.com` | 200, biết người dùng, **không** phải đăng nhập lại |
| 2 | Kiểm cookie gửi kèm | Request tới `game.cnhsk.com` có cookie |
| 3 | Cookie set không có `Domain` | Trang game **không** nhận được cookie → 401 |
| 4 | CORS chỉ khai `cnhsk.com` | Request từ trang game bị chặn |
| 5 | Mở trang game khi chưa đăng nhập | 401, chuyển về login hoặc chế độ khách |

---

# UC-006 · Làm mới access token bằng refresh token

| | |
|---|---|
| **ID** | UC-006 |
| **Actor chính** | `SYSTEM` (kích hoạt bởi client) |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Access token hết hạn. Client dùng refresh token để lấy access token mới mà không bắt
người dùng đăng nhập lại.

## Tiền điều kiện

- Refresh token còn hạn, chưa thu hồi
- Tài khoản không bị khóa hoặc ban

## Hậu điều kiện

- Access token mới được cấp
- **Refresh token cũ bị thu hồi, refresh token mới được cấp** (rotation)
- Cookie hoặc body cập nhật tùy loại client

## Luồng chính

1. Client gọi API nào đó, nhận **401** `TOKEN_EXPIRED`
2. Client gọi `POST /api/auth/refresh` kèm refresh token
3. Server hash refresh token, tìm trong `auth_tokens`
4. Server kiểm: đúng loại `REFRESH`, còn hạn, chưa thu hồi
5. Server kiểm tài khoản vẫn hợp lệ (chưa ban, chưa khóa)
6. Server **thu hồi refresh token cũ**
7. Server sinh access token mới **và** refresh token mới
8. Server lưu hash refresh token mới
9. Server trả **200** — set cookie hoặc trả body tùy client
10. Client thử lại request ban đầu

## Luồng thay thế

**A1 · Refresh chủ động trước khi hết hạn**
Client có thể refresh khi access token còn 1–2 phút, tránh request thất bại.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `REFRESH_TOKEN_INVALID` | Token không tồn tại | **401** | Buộc đăng nhập lại |
| `REFRESH_TOKEN_EXPIRED` | Token hết hạn | **401** | Buộc đăng nhập lại |
| `REFRESH_TOKEN_REUSED` | Token đã thu hồi mà vẫn dùng | **401** | 🔴 **Thu hồi TOÀN BỘ token của user** — dấu hiệu token bị đánh cắp |
| `ACCOUNT_BANNED` | Tài khoản bị ban sau khi đăng nhập | **403** | Thu hồi hết token, buộc đăng xuất |
| `CONCURRENT_REFRESH` | Hai request refresh cùng lúc | **200** hoặc **401** | Xem ghi chú dưới |

> 🔴 **`REFRESH_TOKEN_REUSED` là exception quan trọng nhất của UC này.** Refresh token
> dùng một lần rồi thu hồi. Nếu thấy token đã thu hồi được dùng lại, nghĩa là **có kẻ
> đánh cắp token**. Phản ứng đúng: thu hồi toàn bộ token của user đó, buộc đăng nhập lại
> trên mọi thiết bị. Đây gọi là *reuse detection*.

> ⚠️ **`CONCURRENT_REFRESH` là bẫy thật.** Mobile mở nhiều tab hoặc nhiều request song
> song đều nhận 401 cùng lúc → cả hai gọi refresh → request thứ hai dùng token đã bị
> thu hồi bởi request thứ nhất → bị coi là *reuse* → thu hồi hết → người dùng bị đăng
> xuất oan.
>
> **Cách chữa:** cho một khoảng ân hạn ngắn (ví dụ 10 giây) sau khi thu hồi, trong đó
> token cũ vẫn trả về access token mới nhưng không sinh refresh mới. Hoặc client dùng
> mutex, chỉ cho một request refresh tại một thời điểm.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Refresh token **dùng một lần** — rotation bắt buộc |
| BR-02 | Phát hiện dùng lại token đã thu hồi → thu hồi **toàn bộ** token của user |
| BR-03 | Refresh token lưu hash |
| BR-04 | Phải kiểm lại trạng thái tài khoản mỗi lần refresh — không tin token cũ |
| BR-05 | Có khoảng ân hạn cho refresh đồng thời |

## API

```
POST /api/auth/refresh
Cookie hoặc Body: { refreshToken }
200:  { accessToken, refreshToken, expiresIn }   ← mobile
      hoặc Set-Cookie mới                        ← web
401:  { error_code: "REFRESH_TOKEN_REUSED", ... } ← đã thu hồi hết token
```

## Bảng DB liên quan

`auth.auth_tokens` · `auth.users`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Refresh token hợp lệ | 200, token cũ bị thu hồi, token mới được cấp |
| 2 | Dùng lại token cũ sau khi refresh | 401 `REFRESH_TOKEN_REUSED` + **toàn bộ token của user bị thu hồi** |
| 3 | Token hết hạn | 401 |
| 4 | Tài khoản bị ban giữa lúc | 403, thu hồi hết token |
| 5 | Hai request refresh song song | Không được đăng xuất oan — kiểm cơ chế ân hạn |

---

# UC-007 · Đăng xuất

| | |
|---|---|
| **ID** | UC-007 |
| **Actor chính** | `USER` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng đăng xuất. Token bị thu hồi phía server, cookie bị xóa phía client.

## Tiền điều kiện

Đã đăng nhập.

## Hậu điều kiện

- Refresh token hiện tại bị thu hồi trong `auth_tokens`
- Cookie `access_token` và `refresh_token` bị xóa (set `Max-Age=0`)
- Access token cũ **vẫn hợp lệ** tới khi hết hạn — xem ghi chú

## Luồng chính

1. `USER` bấm "Đăng xuất"
2. Client gọi `POST /api/auth/logout`
3. Server thu hồi refresh token hiện tại
4. Server trả **200** kèm lệnh xóa cookie
5. Client xóa token khỏi bộ nhớ (mobile: xóa khỏi secure storage)
6. Client chuyển về trang chủ hoặc trang đăng nhập

## Luồng thay thế

**A1 · Đăng xuất khỏi mọi thiết bị**
`POST /api/auth/logout-all` → thu hồi **toàn bộ** refresh token của user.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `NO_TOKEN` | Gọi logout khi chưa đăng nhập | **200** | **Không báo lỗi** — logout là idempotent |
| `TOKEN_ALREADY_REVOKED` | Token đã thu hồi | **200** | Không báo lỗi |
| `INTERNAL_ERROR` | Không thu hồi được token | **200** + log | **Vẫn xóa cookie phía client.** Không để người dùng kẹt ở trạng thái không đăng xuất được |

> ⚠️ **Mọi exception của logout đều trả 200.** Đăng xuất là thao tác người dùng luôn phải
> làm được. Nếu server lỗi mà không xóa cookie thì người dùng tưởng vẫn đăng nhập.

> 🔴 **Điểm yếu phải biết: access token là JWT stateless nên KHÔNG thu hồi được.** Sau khi
> logout, access token cũ vẫn dùng được tới khi hết hạn. Đây là lý do access token phải
> **hết hạn ngắn**. Nếu cần thu hồi tức thì thì phải có blacklist trong Redis — tăng độ
> phức tạp, cân nhắc có cần không.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Logout là **idempotent** — gọi nhiều lần không lỗi |
| BR-02 | Luôn xóa cookie dù server có lỗi |
| BR-03 | Thu hồi refresh token, không chỉ xóa cookie |
| BR-04 | Access token không thu hồi được → phải hết hạn ngắn |

## API

```
POST /api/auth/logout
200:  Set-Cookie: access_token=; Max-Age=0
      Set-Cookie: refresh_token=; Max-Age=0
      Body: { loggedOut: true }

POST /api/auth/logout-all
200:  { loggedOut: true, devicesAffected: N }
```

## Bảng DB liên quan

`auth.auth_tokens`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Logout khi đã đăng nhập | 200, refresh token bị thu hồi, cookie bị xóa |
| 2 | Logout hai lần | Lần hai vẫn 200 |
| 3 | Logout khi chưa đăng nhập | 200 |
| 4 | Dùng refresh token sau logout | 401 |
| 5 | Dùng access token cũ sau logout | **Vẫn hợp lệ** tới khi hết hạn — ghi nhận giới hạn này |
| 6 | Logout-all với 3 thiết bị | Cả 3 refresh token bị thu hồi |

---

# UC-008 · Quên mật khẩu — yêu cầu đặt lại

| | |
|---|---|
| **ID** | UC-008 |
| **Actor chính** | `GUEST` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng quên mật khẩu, nhập email để nhận link đặt lại.

## Tiền điều kiện

Không đăng nhập.

## Hậu điều kiện

- Nếu email tồn tại: token `RESET_PASSWORD` được tạo, email đã gửi
- Nếu email không tồn tại: **không làm gì**, nhưng response giống hệt

## Luồng chính

1. `GUEST` bấm "Quên mật khẩu"
2. Nhập email
3. Gửi `POST /api/auth/forgot-password`
4. Server tìm user theo email
5. Nếu tìm thấy: thu hồi token reset cũ, sinh token mới, gửi email
6. Server trả **200** với thông báo chung: *"Nếu email tồn tại, chúng tôi đã gửi hướng dẫn"*
7. Frontend hiện thông báo đó

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `EMAIL_NOT_FOUND` | Email không có trong hệ thống | **200** | 🔴 **KHÔNG báo lỗi.** Trả 200 giống hệt trường hợp thành công |
| `RATE_LIMIT_EXCEEDED` | Yêu cầu quá nhiều | **429** | Giới hạn theo email và theo IP |
| `EMAIL_SEND_FAILED` | SMTP lỗi | **200** + log | Trả 200, ghi log để admin biết. Không tiết lộ cho client |
| `ACCOUNT_BANNED` | Tài khoản bị ban | **200** | Không gửi email, nhưng vẫn trả 200 |

> 🔴 **`EMAIL_NOT_FOUND` phải trả 200.** Nếu trả 404 thì kẻ tấn công dò được email nào có
> trong hệ thống. Thông báo phải là *"Nếu email tồn tại…"* — không xác nhận cũng không
> phủ nhận.

> ⚠️ **Rate limit phải theo cả email và IP.** Chỉ theo IP thì kẻ tấn công spam một email
> từ nhiều IP. Chỉ theo email thì spam nhiều email từ một IP.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Response **giống hệt** dù email có tồn tại hay không |
| BR-02 | Thời gian phản hồi nên gần giống nhau — chống timing attack |
| BR-03 | Token reset hết hạn ngắn hơn token xác thực email (ví dụ 1 giờ) |
| BR-04 | Sinh token mới thì thu hồi token reset cũ |
| BR-05 | Rate limit theo **cả** email và IP |

## API

```
POST /api/auth/forgot-password
Body: { email }
200:  { message: "Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu" }
429:  { error_code: "RATE_LIMIT_EXCEEDED", ... }
```

## Bảng DB liên quan

`auth.users` · `auth.auth_tokens`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Email tồn tại | 200, email gửi đi, token tạo ra |
| 2 | Email không tồn tại | **200** với cùng thông báo, không gửi email |
| 3 | So sánh response case 1 và 2 | **Giống hệt nhau** |
| 4 | Gọi 5 lần cùng email | Lần thứ N trả 429 |
| 5 | Gọi 5 lần khác email cùng IP | Lần thứ N trả 429 |
| 6 | Yêu cầu lần 2 khi token cũ còn hạn | Token cũ bị thu hồi |

---

# UC-009 · Đặt lại mật khẩu bằng token

| | |
|---|---|
| **ID** | UC-009 |
| **Actor chính** | `GUEST` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng bấm link trong email, nhập mật khẩu mới.

## Tiền điều kiện

Token `RESET_PASSWORD` còn hạn, chưa dùng.

## Hậu điều kiện

- `users.password_hash` cập nhật
- Token reset bị thu hồi
- 🔴 **Toàn bộ refresh token của user bị thu hồi** — buộc đăng nhập lại mọi thiết bị
- `failed_login_count` reset, `locked_until` xóa

## Luồng chính

1. Người dùng bấm link `https://cnhsk.com/reset-password?token=XXX`
2. Frontend hiện form nhập mật khẩu mới và xác nhận
3. Gửi `POST /api/auth/reset-password`
4. Server hash token, tìm trong `auth_tokens`, kiểm hợp lệ
5. Server validate mật khẩu mới
6. Server hash mật khẩu mới
7. Server cập nhật `password_hash`, thu hồi token reset, **thu hồi toàn bộ refresh token**, reset trạng thái khóa — **cùng transaction**
8. Server trả **200**
9. Frontend chuyển tới trang đăng nhập

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `TOKEN_INVALID` | Token sai | **400** | "Link không hợp lệ", cho yêu cầu lại |
| `TOKEN_EXPIRED` | Token quá hạn | **410** | "Link đã hết hạn", nút yêu cầu lại |
| `TOKEN_ALREADY_USED` | Token đã dùng | **409** | "Link đã được dùng", cho yêu cầu lại |
| `WEAK_PASSWORD` | Mật khẩu yếu | **422** | Nêu rõ yêu cầu |
| `PASSWORD_MISMATCH` | Hai ô không khớp | **422** | Chỉ rõ field |
| `SAME_AS_OLD_PASSWORD` | Mật khẩu mới giống cũ | **422** | Yêu cầu chọn mật khẩu khác |
| `ACCOUNT_BANNED` | Tài khoản bị ban | **403** | Không cho đặt lại |

> 🔴 **Hậu điều kiện quan trọng nhất: thu hồi TOÀN BỘ refresh token.** Người dùng đặt lại
> mật khẩu thường vì nghi tài khoản bị xâm nhập. Nếu không thu hồi hết thì kẻ tấn công
> đang có refresh token vẫn vào được — đặt lại mật khẩu thành vô nghĩa.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Thu hồi **toàn bộ** refresh token của user sau khi đổi mật khẩu |
| BR-02 | Reset `failed_login_count` và `locked_until` |
| BR-03 | Token reset dùng một lần |
| BR-04 | Mật khẩu mới phải khác mật khẩu cũ |
| BR-05 | Mọi thao tác trong cùng transaction |
| BR-06 | Gửi email thông báo "mật khẩu của bạn vừa được đổi" — để người dùng biết nếu không phải mình làm |

## API

```
POST /api/auth/reset-password
Body: { token, newPassword, confirmPassword }
200:  { reset: true }
410:  { error_code: "TOKEN_EXPIRED", ... }
422:  { error_code: "WEAK_PASSWORD", details: [...], ... }
```

## Bảng DB liên quan

`auth.users` · `auth.auth_tokens`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Token hợp lệ, mật khẩu mạnh | 200, đăng nhập được bằng mật khẩu mới |
| 2 | Đăng nhập bằng mật khẩu cũ | 401 |
| 3 | Refresh token cũ sau khi reset | **401** — đã bị thu hồi |
| 4 | Token đã dùng | 409 |
| 5 | Mật khẩu mới giống cũ | 422 |
| 6 | Tài khoản đang bị khóa | Reset xong thì mở khóa |

---

# UC-010 · Đổi mật khẩu khi đã đăng nhập

| | |
|---|---|
| **ID** | UC-010 |
| **Actor chính** | `USER` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng đang đăng nhập, đổi mật khẩu. **Phải nhập mật khẩu cũ** để xác nhận.

## Tiền điều kiện

Đã đăng nhập.

## Hậu điều kiện

- `password_hash` cập nhật
- 🔴 **Toàn bộ refresh token bị thu hồi TRỪ token của phiên hiện tại**

## Luồng chính

1. `USER` vào trang đổi mật khẩu
2. Nhập mật khẩu cũ, mật khẩu mới, xác nhận
3. Gửi `POST /api/auth/change-password`
4. Server xác thực JWT, lấy user
5. Server so mật khẩu cũ
6. Server validate mật khẩu mới
7. Server cập nhật hash, thu hồi các refresh token khác — **cùng transaction**
8. Server gửi email thông báo
9. Server trả **200**

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `INVALID_OLD_PASSWORD` | Mật khẩu cũ sai | **401** | Báo rõ "mật khẩu hiện tại không đúng" |
| `WEAK_PASSWORD` | Mật khẩu mới yếu | **422** | |
| `SAME_AS_OLD_PASSWORD` | Mới giống cũ | **422** | |
| `PASSWORD_MISMATCH` | Hai ô không khớp | **422** | |
| `RATE_LIMIT_EXCEEDED` | Thử mật khẩu cũ nhiều lần | **429** | Chống dò mật khẩu cũ |
| `TOKEN_EXPIRED` | Session hết hạn giữa lúc | **401** | Refresh rồi thử lại |

> ⚠️ **Ở đây `INVALID_OLD_PASSWORD` được báo rõ ràng** — khác UC-003. Lý do: người dùng
> đã đăng nhập nên không có nguy cơ *user enumeration*. Nhưng vẫn cần rate limit để
> chống dò mật khẩu.

> ⚠️ **Không thu hồi token của phiên hiện tại**, nếu không người dùng vừa đổi mật khẩu
> xong đã bị đăng xuất — trải nghiệm tệ.

## Business rule

| # | Rule |
|---|---|
| BR-01 | **Bắt buộc** nhập mật khẩu cũ |
| BR-02 | Thu hồi refresh token của các thiết bị khác, giữ phiên hiện tại |
| BR-03 | Mật khẩu mới khác mật khẩu cũ |
| BR-04 | Gửi email thông báo |
| BR-05 | Rate limit số lần thử mật khẩu cũ |

## API

```
POST /api/auth/change-password
Header: Authorization / Cookie
Body: { oldPassword, newPassword, confirmPassword }
200:  { changed: true, otherDevicesLoggedOut: N }
401:  { error_code: "INVALID_OLD_PASSWORD", ... }
```

## Bảng DB liên quan

`auth.users` · `auth.auth_tokens`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Mật khẩu cũ đúng, mới mạnh | 200 |
| 2 | Mật khẩu cũ sai | 401 |
| 3 | Phiên hiện tại sau khi đổi | **Vẫn đăng nhập được** |
| 4 | Thiết bị khác sau khi đổi | Bị đăng xuất |
| 5 | Thử mật khẩu cũ 10 lần | 429 |

---

# UC-011 · Xem và sửa thông tin cá nhân

| | |
|---|---|
| **ID** | UC-011 |
| **Actor chính** | `USER` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Người dùng xem và cập nhật hồ sơ: họ tên, ảnh đại diện, ngôn ngữ, mục tiêu HSK.

## Tiền điều kiện

Đã đăng nhập.

## Hậu điều kiện

`users` cập nhật các cột được phép sửa.

## Luồng chính

1. `USER` vào trang thông tin cá nhân
2. Client gọi `GET /api/me`
3. Server trả hồ sơ (**không** kèm hash mật khẩu)
4. `USER` sửa các field, bấm "Lưu"
5. Client gọi `PATCH /api/me`
6. Server validate, cập nhật
7. Server trả **200** kèm hồ sơ mới

## Luồng thay thế

**A1 · Đổi ảnh đại diện**
Upload file → kiểm loại và kích thước → lưu → cập nhật URL.

**A2 · Đổi email** — **KHÔNG cho đổi trực tiếp.** Phải xác thực email mới trước.
Đây là UC riêng, chưa có trong catalog. **Cần chốt có làm không.**

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `VALIDATION_ERROR` | Field sai định dạng | **422** | Chỉ rõ field |
| `FORBIDDEN_FIELD` | Cố sửa field không được phép (`id`, `email`, `roles`, `password_hash`) | **403** | 🔴 Chặn ở server, **không** tin frontend |
| `FILE_TOO_LARGE` | Ảnh quá lớn | **413** | Nêu giới hạn |
| `INVALID_FILE_TYPE` | Không phải ảnh | **422** | Chỉ nhận jpg, png, webp |
| `TOKEN_EXPIRED` | Session hết hạn | **401** | Refresh |

> 🔴 **`FORBIDDEN_FIELD` là lỗ hổng thật nếu quên.** Nếu server nhận cả body rồi
> `save()` thì kẻ tấn công gửi `{"roles": ["SUPER_ADMIN"]}` là leo thang đặc quyền.
> Phải **whitelist** field được sửa, không blacklist.

## Business rule

| # | Rule |
|---|---|
| BR-01 | **Whitelist** field được sửa: `fullName`, `avatarUrl`, `preferredLanguage`, `targetHskLevel` |
| BR-02 | **Không** cho sửa: `id`, `email`, `passwordHash`, `roles`, `emailVerifiedAt`, `createdAt` |
| BR-03 | Không trả `passwordHash` trong bất kỳ response nào |
| BR-04 | Đổi email là UC riêng, cần xác thực lại |

## API

```
GET   /api/me
200:  { id, fullName, email, emailVerified, avatarUrl, roles, targetHskLevel, createdAt }

PATCH /api/me
Body: { fullName?, avatarUrl?, preferredLanguage?, targetHskLevel? }
200:  { ...hồ sơ mới }
403:  { error_code: "FORBIDDEN_FIELD", details: [{field: "roles"}], ... }
```

## Bảng DB liên quan

`auth.users`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Sửa `fullName` | 200, cập nhật đúng |
| 2 | Gửi kèm `roles: ["SUPER_ADMIN"]` | **403** hoặc bỏ qua field — **role KHÔNG đổi** |
| 3 | Gửi kèm `email` | 403 hoặc bỏ qua |
| 4 | Kiểm response `GET /api/me` | **Không** có `passwordHash` |
| 5 | Upload file 20MB | 413 |
| 6 | Upload file .exe | 422 |

---

# UC-012 · Khóa tài khoản tạm sau N lần sai mật khẩu

| | |
|---|---|
| **ID** | UC-012 |
| **Actor chính** | `SYSTEM` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

Hệ thống tự khóa tài khoản tạm thời khi có quá nhiều lần đăng nhập sai liên tiếp —
chống dò mật khẩu.

## Tiền điều kiện

Tài khoản tồn tại, đang ở trạng thái bình thường.

## Hậu điều kiện

- `failed_login_count` tăng mỗi lần sai
- Đạt ngưỡng → `locked_until` được set
- Đăng nhập thành công → reset `failed_login_count` về 0

## Luồng chính

1. `GUEST` nhập mật khẩu sai
2. Server tăng `failed_login_count`
3. Server kiểm đã đạt ngưỡng chưa
4. Chưa đạt → trả **401** `INVALID_CREDENTIALS`
5. Đạt ngưỡng → set `locked_until = now + khoảng khóa`, trả **423** `ACCOUNT_LOCKED`
6. Trong thời gian khóa, mọi lần đăng nhập trả **423** kể cả mật khẩu đúng
7. Hết thời gian khóa → cho thử lại

## Luồng thay thế

**A1 · Người dùng đặt lại mật khẩu để mở khóa**
UC-009 reset `locked_until` → mở khóa ngay.

**A2 · `SUPER_ADMIN` mở khóa thủ công**
Thuộc UC-114.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `ACCOUNT_LOCKED` | Đang trong thời gian khóa | **423** | Trả kèm `unlockAt` để frontend đếm ngược |
| — | Mật khẩu **đúng** khi đang khóa | **423** | 🔴 Vẫn từ chối — nếu không thì cơ chế khóa vô nghĩa |
| — | Kẻ tấn công dùng cơ chế này để khóa tài khoản người khác | — | ⚠️ Xem ghi chú |

> ⚠️ **Rủi ro bị lợi dụng làm DoS.** Kẻ xấu biết email của bạn, cố tình nhập sai N lần
> để khóa tài khoản bạn. Cách giảm:
> - Khóa theo **cặp (tài khoản, IP)** thay vì chỉ theo tài khoản
> - Hoặc dùng CAPTCHA sau vài lần sai thay vì khóa hẳn
> - Hoặc khóa thời gian tăng dần: 1 phút → 5 phút → 15 phút
>
> **Cần chốt cách nào.** Tài liệu hiện chưa nói.

> 🔴 **Cột `locked_until` và `failed_login_count` CHƯA CÓ trong 59 bảng.** Đây là thiếu
> sót thật của thiết kế DB hiện tại. Xem `CONTEXT.md` §3 và Hiến pháp mục A.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Ngưỡng và thời gian khóa: **chưa chốt** — tham khảo `Move_home`: 5 lần sai → khóa 15 phút |
| BR-02 | Đang khóa thì từ chối kể cả mật khẩu đúng |
| BR-03 | Đăng nhập thành công reset `failed_login_count` |
| BR-04 | Đặt lại mật khẩu mở khóa ngay |
| BR-05 | Ghi log mọi lần đăng nhập thất bại kèm IP và thời điểm |

## API

```
POST /api/auth/login   (cùng endpoint UC-003)
423:  { error_code: "ACCOUNT_LOCKED",
        message: "Tài khoản tạm khóa do đăng nhập sai nhiều lần",
        details: { unlockAt: "2026-10-01T03:15:00Z", remainingMinutes: 12 },
        request_id }
```

## Bảng DB liên quan

`auth.users` — ⚠️ **cần thêm cột** `failed_login_count`, `locked_until`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Sai mật khẩu 1 lần | 401, `failed_login_count = 1` |
| 2 | Sai đủ ngưỡng | 423, `locked_until` được set |
| 3 | Mật khẩu **đúng** khi đang khóa | **423** |
| 4 | Hết thời gian khóa, mật khẩu đúng | 200 |
| 5 | Đăng nhập thành công | `failed_login_count` về 0 |
| 6 | Đặt lại mật khẩu khi đang khóa | Mở khóa |

---

# UC-013 · Chơi game trong WebView mobile

| | |
|---|---|
| **ID** | UC-013 |
| **Actor chính** | `USER` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 6.1 |

## Mô tả

App mobile mở trang game `game.cnhsk.com` trong WebView. Vì React Native không có cookie
như trình duyệt, **app phải tiêm token vào header trước khi trang game chạy**.

## Tiền điều kiện

- Đã đăng nhập trên app (UC-004)
- App có access token trong secure storage

## Hậu điều kiện

Trang game trong WebView biết người dùng, lưu điểm được.

## Luồng chính

1. `USER` bấm "Chơi game" trong app
2. App đọc access token từ secure storage
3. App mở WebView tới `game.cnhsk.com`, tiêm token — hai cách:
   - Tiêm biến `window.__CNHSK_TOKEN__` trước khi trang load
   - Hoặc gửi qua `postMessage` sau khi trang load
4. Trang game đọc token: **ưu tiên `window.__CNHSK_TOKEN__`, không có thì dùng cookie**
5. Trang game gắn `Authorization: Bearer` vào mọi request
6. Chơi và lưu điểm bình thường

## Luồng thay thế

**A1 · Token hết hạn giữa lúc đang chơi**
Trang game gửi `postMessage` về app yêu cầu token mới → app refresh (UC-006) → tiêm token
mới vào WebView.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `NO_TOKEN` | App không tiêm được token | **401** | Trang game hiện "Vui lòng đăng nhập lại", app xử lý |
| `TOKEN_EXPIRED` | Token hết hạn trong WebView | **401** | Yêu cầu app refresh qua `postMessage` |
| — | Trang game load trước khi token được tiêm | — | 🔴 **Race condition thật.** Xem ghi chú |
| — | WebView không cho tiêm biến | — | Dùng `postMessage` thay thế |

> 🔴 **Race condition là exception khó nhất của UC này.** Nếu trang game gọi API ngay khi
> load mà token chưa tiêm xong thì request đầu tiên thất bại.
>
> **Cách chữa:** trang game **chờ** token trước khi gọi API — hoặc chờ biến
> `window.__CNHSK_TOKEN__` xuất hiện, hoặc chờ `postMessage` với timeout. Không gọi API
> trong lúc chưa có token.

## Business rule

| # | Rule |
|---|---|
| BR-01 | Trang game đọc token: `window.__CNHSK_TOKEN__` → nếu không có thì cookie |
| BR-02 | **Một đoạn code chạy được cả hai nơi** — trên web không có biến nên dùng cookie, trong WebView có biến nên dùng header. Không viết hai nhánh logic |
| BR-03 | Trang game chờ token trước khi gọi API |
| BR-04 | WebView **không** cần thêm origin vào CORS — nó tải chính `game.cnhsk.com` |
| BR-05 | App không tiêm token vào URL (lộ trong history) |

## API

```
// Trong WebView, app tiêm:
window.__CNHSK_TOKEN__ = "eyJ...";
window.__CNHSK_PLATFORM__ = "mobile";

// Trang game:
const token = (window as any).__CNHSK_TOKEN__;
const headers = token ? { Authorization: `Bearer ${token}` } : {};
// không có token → dựa vào cookie (trường hợp web)
```

## Bảng DB liên quan

`auth.users` · `community.game_scores`

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Mở WebView với token đã tiêm | Trang game biết người dùng |
| 2 | Mở trang game trên web thường | Dùng cookie, vẫn chạy |
| 3 | Token chưa tiêm khi trang load | Trang game **chờ**, không gọi API lỗi |
| 4 | Token hết hạn giữa lúc chơi | App refresh, tiêm token mới |
| 5 | Lưu điểm từ WebView | Thành công, mastery cập nhật |

---

# UC-014 · Xem lịch sử đăng nhập

| | |
|---|---|
| **ID** | UC-014 |
| **Actor chính** | `USER` |
| **Priority** | P3 |
| **Scope** | V2 |
| **Tính năng gốc** | — ⚠️ **Không có trong feature tree** |

## Mô tả

Người dùng xem các lần đăng nhập gần đây (thời điểm, IP, thiết bị) để phát hiện truy cập
lạ.

> ⚠️ **UC này tôi đề xuất thêm, không có trong `feature-tree.md`.**
> Lý do đề xuất: cần thiết cho bảo mật, và là nơi người dùng phát hiện tài khoản bị xâm
> nhập. **Cần bạn chốt có làm không.**

## Tiền điều kiện

Đã đăng nhập.

## Hậu điều kiện

Không đổi trạng thái — chỉ đọc.

## Luồng chính

1. `USER` vào trang bảo mật
2. Client gọi `GET /api/me/login-history`
3. Server trả danh sách N lần đăng nhập gần nhất
4. Hiển thị: thời điểm, IP (đã mask), thiết bị, trạng thái thành công/thất bại

## Exception

| Mã | Tình huống | HTTP | Xử lý |
|---|---|---|---|
| `NO_DATA` | Chưa có lịch sử | **200** | Trả danh sách rỗng + empty state |
| `TOKEN_EXPIRED` | Session hết hạn | **401** | Refresh |

## Business rule

| # | Rule |
|---|---|
| BR-01 | Chỉ xem được lịch sử **của chính mình** — kiểm quyền sở hữu |
| BR-02 | IP hiển thị dạng mask một phần |
| BR-03 | Giữ lịch sử N ngày rồi xóa |
| BR-04 | ⚠️ **Chưa có bảng** để lưu — cần thêm `login_events` nếu làm UC này |

## API

```
GET /api/me/login-history?page=0&size=20
200:  { content: [{ at, ip, device, success }], totalElements, ... }
```

## Bảng DB liên quan

⚠️ **Chưa có bảng.** Cần thêm `auth.login_events` nếu chốt làm.

## Test case

| # | Input | Kết quả mong đợi |
|---|---|---|
| 1 | Có lịch sử | 200, danh sách đúng thứ tự mới nhất trước |
| 2 | Chưa có lịch sử | 200, danh sách rỗng |
| 3 | Gọi với ID người khác | **403** — chỉ xem được của mình |

---

# Tổng kết nhóm 0

## Exception đáng chú ý nhất

| # | Exception | UC | Vì sao quan trọng |
|---|---|---|---|
| 1 | `INVALID_CREDENTIALS` giống nhau cho email sai và mật khẩu sai | UC-003 | Chống *user enumeration* |
| 2 | `EMAIL_NOT_FOUND` trả **200** | UC-008 | Chống dò email |
| 3 | `REFRESH_TOKEN_REUSED` → thu hồi hết token | UC-006 | Phát hiện token bị đánh cắp |
| 4 | Reset mật khẩu → thu hồi **toàn bộ** refresh token | UC-009 | Không thu hồi thì reset vô nghĩa |
| 5 | `FORBIDDEN_FIELD` chặn sửa `roles` | UC-011 | Chống leo thang đặc quyền |
| 6 | `ACCOUNT_LOCKED` trả 423 kể cả mật khẩu đúng | UC-012 | Không thì cơ chế khóa vô nghĩa |
| 7 | Race condition tiêm token vào WebView | UC-013 | Request đầu tiên thất bại |
| 8 | `EMAIL_SEND_FAILED` vẫn trả 201 | UC-001 | SMTP lỗi không nên chặn đăng ký |
| 9 | Refresh đồng thời bị coi là *reuse* | UC-006 | Người dùng bị đăng xuất oan |

## Việc còn thiếu trong thiết kế hiện tại

| # | Thiếu | Ảnh hưởng UC |
|---|---|---|
| 1 | Cột `failed_login_count`, `locked_until` trong `users` | UC-012 |
| 2 | Cột `suspended_at`, `banned_at` trong `users` | UC-003 |
| 3 | Bảng `login_events` | UC-014 |
| 4 | Token loại `EMAIL_VERIFY` — hiện `auth_tokens.token_type` chỉ ghi `REFRESH` và `RESET_PASSWORD` | UC-001 · UC-002 |
| 5 | Ngưỡng khóa tài khoản và thời gian khóa | UC-012 |
| 6 | Quyết định chống DoS bằng khóa tài khoản | UC-012 |
| 7 | UC đổi email — chưa có | UC-011 A2 |

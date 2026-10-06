# CNHSK — Đặc tả Use Case · Nhóm 6a + 6b · Thanh toán & Chấm bài thuê

> **UC-093 → UC-107** · 15 use case · Tính năng 6.1 · 6.2
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
>
> 🔴 **Đây là nhóm dính tiền thật.** Feature tree ghi rõ: *"đây là chỗ thầy sẽ hỏi kỹ nhất khi
> bảo vệ. Chuẩn bị giải thích được 6 điểm bảo mật."*
>
> ⚠️ **Toàn bộ nhóm chờ `TODO(PAYMENT_SCOPE)`** — chưa chốt tiền thật hay giả lập.

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
| --- | --- | --- | --- | --- | --- |
| UC-093 | Xem gói dịch vụ và số điểm còn lại | `USER` | P1 | MVP | 6.1 |
| UC-094 | Nhập mã thẻ nạp để cộng điểm | `USER` | P1 | MVP | 6.1 |
| UC-095 | Nhập mã thẻ để kích hoạt gói tháng | `USER` | P1 | MVP | 6.1 |
| UC-096 | Trừ điểm khi dùng tính năng tốn phí | `SYSTEM` | P1 | MVP | 6.1 |
| UC-097 | Hoàn điểm khi tính năng lỗi | `SYSTEM` | P1 | MVP | 6.1 |
| UC-098 | Xem lịch sử giao dịch điểm của mình | `USER` | P2 | MVP | 6.1 |
| UC-099 | Sinh lô mã thẻ nạp | `FINANCE_ADMIN` | P1 | MVP | 6.1 |
| UC-100 | Xem sổ cái giao dịch toàn hệ thống | `FINANCE_ADMIN` | P1 | MVP | 6.1 |
| UC-101 | Quản lý gói dịch vụ (tạo, sửa giá) | `FINANCE_ADMIN` | P2 | MVP | 6.1 |
| UC-102 | Xử lý tranh chấp về điểm | `FINANCE_ADMIN` | P2 | MVP | 6.1 |
| UC-103 | Gửi yêu cầu nhờ chấm bài viết | `USER` | P2 | MVP | 6.2 |
| UC-104 | Xem hàng đợi bài chờ chấm | `TEACHER` | P2 | MVP | 6.2 |
| UC-105 | Nhận và chấm bài viết | `TEACHER` | P2 | MVP | 6.2 |
| UC-106 | Xem kết quả chấm và nhận xét | `USER` | P2 | MVP | 6.2 |
| UC-107 | Hoàn điểm khi teacher không nhận trong hạn | `SYSTEM` | P2 | MVP | 6.2 |

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

# UC-093 · Xem gói dịch vụ và số điểm còn lại

| | |
|---|---|
| **UC-ID** | UC-093 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Xem gói hiện tại (Thường / Premium tháng), số điểm còn lại, số lượt free còn lại của **từng**
tính năng tốn phí.

**Bốn tính năng tốn lượt:** AI sinh bài (3.3) · dịch (4.1) · trợ lý ảo (5.5) · chấm bài thuê (6.2)

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có `plans` trong hệ thống

## Hậu điều kiện

Chỉ đọc. **Không** tạo dòng `user_credits` nếu chưa có — trả 0.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang "Tài khoản của tôi" |
| 2 | Client | `GET /api/me/subscription` và `GET /api/me/credits` |
| 3 | System | Đọc `user_subscriptions` — kiểm `expires_at > now()` |
| 4 | System | Đọc `user_credits.balance` |
| 5 | System | Đọc `feature_usage` — đếm lượt đã dùng từng tính năng trong kỳ |
| 6 | System | Tính lượt free còn lại = 10 − đã dùng (gói Thường) |
| 7 | System | Trả `{plan, expires_at, balance, free_quota_remaining{}}` |
| 8 | Client | Hiện gói, điểm, và 4 thanh lượt còn lại |

## Luồng thay thế

**A1 — Chưa có `user_credits`**
`USER` mới chưa nạp gì. Trả `balance = 0`, **không** `INSERT` dòng. Tạo dòng lúc nạp lần đầu
(UC-094) là đủ.

**A2 — Gói Premium đã hết hạn**
`expires_at < now()`. Trả `plan = FREE` và ghi chú "gói tháng đã hết hạn ngày X". **Không** tự
xoá dòng `user_subscriptions` — giữ lịch sử.

**A3 — Premium còn hạn**
`free_quota_remaining` trả `null` cho cả 4 tính năng (không giới hạn), client hiện "Không giới hạn".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `CREDITS_NOT_OWNED` | 403 | 🔴 Xem điểm người khác | **IDOR** — endpoint `/me`, không nhận `user_id` |
| `NO_CREDIT_RECORD` | 200 | Chưa nạp lần nào | `balance = 0`, không tạo dòng (A1) |
| `EXPIRED_SUBSCRIPTION_SHOWN_ACTIVE` | — | 🔴 Không kiểm `expires_at` | Xem ghi chú |
| `QUOTA_PERIOD_UNDEFINED` | — | 🔴 "10 lượt free" — mỗi tháng? mỗi ngày? vĩnh viễn? | Xem ghi chú |
| `NEGATIVE_BALANCE` | — | 🔴 `balance < 0` | Xem ghi chú |
| `PLAN_NOT_FOUND` | 500 | `user_subscriptions.plan_id` trỏ tới gói đã xoá | Coi là `FREE`, ghi log. Không xoá `plans` đang dùng |
| `BALANCE_TYPE_FLOAT` | — | 🔴 `balance` dùng `FLOAT` | Vi phạm AC-07 — phải `BIGINT` hoặc `NUMERIC(12,2)` |
| `STALE_QUOTA_COUNT` | — | `feature_usage` đếm chậm hơn thực tế | Đếm trong cùng transaction lúc trừ (UC-096) |

> 🔴 **`QUOTA_PERIOD_UNDEFINED` là khoảng trống nghiệp vụ, không phải lỗi code.** Feature tree
> ghi: *"Thường — mỗi tính năng tốn phí dùng **10 lượt free**"*. Nhưng **không nói 10 lượt trong
> bao lâu**:
>
> | Cách hiểu | Hệ quả |
> | --- | --- |
> | 10 lượt **vĩnh viễn** cho mỗi tài khoản | Dùng hết là phải nạp — mô hình dùng thử |
> | 10 lượt **mỗi tháng**, reset đầu tháng | Người dùng free vẫn dùng được lâu dài |
> | 10 lượt **mỗi ngày** | Rất thoáng, chi phí API cao |
>
> Ba cách cho ba mô hình kinh doanh khác nhau, và ảnh hưởng trực tiếp đến `feature_usage` cần
> cột `period` hay không. **Phải chốt trước khi làm UC-096.**
> **Khuyến nghị:** 10 lượt vĩnh viễn — đơn giản nhất, không cần job reset, và đúng nghĩa "dùng thử".

> 🔴 **`NEGATIVE_BALANCE` không bao giờ được xảy ra.** Nếu trừ điểm không kiểm số dư trước
> (UC-096) thì `balance` âm → người dùng "nợ" hệ thống, và mọi báo cáo tài chính sai.
> **Cần:** `CHECK (balance >= 0)` ở DB. Ràng buộc này chặn được cả lớp bug mà code có thể quên.

> 🔴 **`EXPIRED_SUBSCRIPTION_SHOWN_ACTIVE`:** hiện "Premium" cho người đã hết hạn thì họ dùng
> tính năng tốn phí mà `QuotaService` chặn → thấy mâu thuẫn, báo lỗi, và `FINANCE_ADMIN` phải
> giải thích. Kiểm `expires_at` ở **mọi** chỗ đọc gói, không chỉ ở màn này.

## Business rule

| # | Rule |
| --- | --- |
| BR-093-1 | UC này bị chặn bởi `TODO(PAYMENT_SCOPE)`. Không gen code MVP cho đến khi nhóm chốt có dùng hệ thống điểm/nạp thẻ hay không. |
| BR-093-2 | Người dùng chỉ được xem gói, điểm và quota của chính mình. Endpoint dạng `/me`, không nhận `user_id` từ client. |
| BR-093-3 | Trong MVP không có payment, hệ thống chỉ cần hiển thị quota miễn phí còn lại cho các tính năng gọi API ngoài. |
| BR-093-4 | Tra từ điển, xem lịch sử AI, xem lịch sử giao dịch hoặc các thao tác đọc dữ liệu nội bộ không được tính vào quota tốn phí. |
| BR-093-5 | Gói hết hạn được coi là gói miễn phí. Hệ thống giữ lịch sử gói cũ, không xóa dữ liệu gói đã dùng. |
| BR-093-6 | Số dư điểm không được âm. Nếu còn dùng bảng điểm, database phải chặn `balance < 0`. |
| BR-093-7 | Không dùng kiểu số thực cho điểm hoặc tiền. Điểm dùng số nguyên; giá tiền nếu có dùng kiểu số chính xác. |
| BR-093-8 | Kỳ hạn quota miễn phí phải được chốt trước khi triển khai. Khuyến nghị đơn giản: quota miễn phí theo ngày hoặc theo tháng, không dùng nhiều cách hiểu song song. |

## API · DB

```
GET /api/me/subscription
GET /api/me/credits
```

`user_subscriptions` · `user_credits` · `plans` · `feature_usage` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `USER` gói Thường, chưa nạp | `plan = FREE`, `balance = 0` |
| T2 | Gửi kèm `user_id` người khác | Bị bỏ qua |
| T3 | Premium hết hạn hôm qua | `plan = FREE`, có ghi chú |
| T4 | Premium còn hạn | `free_quota_remaining = null` |
| T5 | `UPDATE user_credits SET balance = -10` | **DB chặn** (CHECK) |
| T6 | Đã dùng 3 lượt dịch | `free_quota_remaining.translate = 7` |

---

# UC-094 · Nhập mã thẻ nạp để cộng điểm

| | |
|---|---|
| **UC-ID** | UC-094 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Nhập mã thẻ → cộng điểm vào `user_credits`. Đây là **UC nguy hiểm nhất của cả hệ thống** —
nơi tiền thật vào.

**Cách mua:** người dùng liên hệ qua Zalo hoặc nền tảng riêng → công ty cung cấp mã thẻ.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có **CSRF token** hợp lệ (web dùng cookie)
3. Chưa vượt 5 lần nhập sai trong giờ
4. Mã tồn tại, `status = UNUSED`, chưa hết hạn

## Hậu điều kiện

| Kết quả | Trạng thái (cùng **một** transaction) |
| --- | --- |
| Thành công | `credit_cards.status = USED` + `used_by` + `used_at`; `user_credits.balance` tăng; `credit_transactions` thêm dòng có `balance_before`/`balance_after` |
| Mã sai | **Không đổi gì**; tăng bộ đếm nhập sai |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nhập mã thẻ, bấm "Nạp" |
| 2 | Client | `POST /api/credits/redeem` + **CSRF token** |
| 3 | System | Kiểm CSRF |
| 4 | System | Kiểm số lần nhập sai trong giờ ≤ 5 |
| 5 | System | Tính `hash(code)` — **không** lưu, không log mã thô |
| 6 | System | **Mở transaction** |
| 7 | System | `SELECT * FROM credit_cards WHERE code_hash = ? FOR UPDATE` |
| 8 | System | Kiểm `status = UNUSED` và `expires_at > now()` |
| 9 | System | `SELECT * FROM user_credits WHERE user_id = ? FOR UPDATE` (tạo nếu chưa có) |
| 10 | System | `balance_before = balance` |
| 11 | System | `balance = balance + card.value` |
| 12 | System | `credit_cards.status = USED`, `used_by`, `used_at` |
| 13 | System | `INSERT credit_transactions` — loại `TOPUP`, `balance_before`, `balance_after`, `card_id` |
| 14 | System | **Commit** |
| 15 | System | Trả `{new_balance, amount_added}` |

## Luồng thay thế

**A1 — Mã không tồn tại**
Bước 7 không tìm thấy. Tăng bộ đếm sai, trả `INVALID_CARD_CODE`. **Thông báo giống hệt** mã đã
dùng (xem exception).

**A2 — Mã đã dùng**
`status = USED`. Trả **cùng** thông báo với A1.

**A3 — Nhập sai lần thứ 6 trong giờ**
429, khoá nhập 1 giờ.

**A4 — Hai request nhập cùng mã đồng thời**
Bước 7 `FOR UPDATE` khoá dòng → request thứ hai chờ, thấy `USED`, trả lỗi. **Chỉ một** cộng điểm.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INVALID_CARD_CODE` | 400 | Mã sai **hoặc** đã dùng **hoặc** hết hạn | 🔴 **Một thông báo duy nhất** — xem ghi chú |
| `TOO_MANY_FAILED_ATTEMPTS` | 429 | > 5 lần sai/giờ | Khoá 1 giờ (quy tắc 3) |
| `CSRF_TOKEN_MISSING` | 403 | 🔴 Thiếu CSRF | Xem ghi chú |
| `CARD_CODE_IN_LOG` | — | 🔴 Mã thô vào log | Xem ghi chú |
| `CARD_CODE_STORED_RAW` | — | 🔴 Lưu mã thô trong DB | Vi phạm quy tắc 2 — chỉ lưu `code_hash` |
| `DOUBLE_REDEMPTION` | — | 🔴 Hai request cùng cộng điểm | `FOR UPDATE` bước 7 (A4) |
| `BALANCE_UPDATED_WITHOUT_LEDGER` | — | 🔴 Cộng điểm mà không ghi `credit_transactions` | Xem ghi chú |
| `LEDGER_WITHOUT_BALANCE_UPDATE` | — | 🔴 Ghi sổ cái mà không cộng điểm | Cùng transaction |
| `BALANCE_BEFORE_AFTER_MISMATCH` | — | 🔴 `balance_after ≠ balance_before + value` | Xem ghi chú |
| `CARD_EXPIRED` | 400 | Hết hạn | Cùng thông báo `INVALID_CARD_CODE` |
| `SELF_GENERATED_CARD` | 403 | `FINANCE_ADMIN` nạp mã mình vừa sinh | ⚠️ Xem ghi chú |
| `RATE_LIMIT_BYPASS_BY_IP` | — | Đổi IP để reset bộ đếm | Đếm theo **`user_id`**, không theo IP |
| `TIMING_ATTACK` | — | Mã tồn tại trả chậm hơn mã không tồn tại | ⚠️ Rủi ro thấp; dùng so sánh hằng thời gian nếu lo |

> 🔴 **`INVALID_CARD_CODE` phải là một thông báo duy nhất cho ba trường hợp.** Nếu phân biệt:
> — "Mã không tồn tại" vs "Mã đã được sử dụng" → kẻ tấn công **biết mã nào có thật**
> — Dò mã 16 ký tự là không khả thi, **nhưng** biết mã tồn tại là bước đầu để tìm ai đã dùng
> **Cùng nguyên tắc** với `INVALID_CREDENTIALS` ở UC-003 (không phân biệt email sai / mật khẩu sai).
> Ngoại lệ hợp lý: có thể phân biệt "mã hết hạn" nếu công ty muốn hỗ trợ người mua mã cũ — nhưng
> đó là **quyết định tường minh**, không phải mặc định.

> 🔴 **`CSRF_TOKEN_MISSING` — vì sao bản 5 thêm lớp này.** Web dùng **cookie** `SameSite=Lax`.
> `Lax` chặn được request từ tên miền **khác**, nhưng **không** chặn từ tên miền phụ của chính
> mình. Ta có `game.cnhsk.com` — nếu trang game bị XSS (UC-087) thì script ở đó gửi được request
> nạp thẻ kèm cookie hợp lệ.
> **Bắt buộc:** mọi endpoint đổi tiền có CSRF token. Không phải "nên có".

> 🔴 **`CARD_CODE_IN_LOG` là lỗi dễ mắc nhất và khó phát hiện nhất.** Ba đường lọt:
> — `log.info("Redeem request: {}", requestBody)` — log cả body
> — Exception message chứa mã: `throw new Exception("Card not found: " + code)`
> — Spring Boot log request body khi `DEBUG`
> **Cần:** một test đọc file log sau khi gọi API và assert **không** chứa mã. Đây là loại test
> ít ai viết nhưng ở đây là bắt buộc — quy tắc 5 nói "kể cả log lỗi".

> 🔴 **`BALANCE_UPDATED_WITHOUT_LEDGER` phá vỡ toàn bộ khả năng xử lý tranh chấp.**
> `credit_transactions` là "sổ cái tiền thật — **không bao giờ xoá dòng nào**" (5 bảng không
> được đụng). Nếu một đường nào đó đổi `balance` mà không ghi sổ cái thì:
> — UC-100 (sổ cái) và UC-098 (lịch sử) thiếu dòng
> — UC-102 (tranh chấp) **không thể** tái dựng số dư
> **Cách chặn cứng nhất:** trigger ở DB, hoặc một `CreditService` **duy nhất** có quyền
> `UPDATE user_credits`, và ArchUnit chặn mọi nơi khác gọi repository đó.

> 🔴 **`BALANCE_BEFORE_AFTER_MISMATCH` — sổ cái phải tự kiểm được.** Bất biến:
> `balance_after = balance_before ± amount`, và `balance_before` của dòng N = `balance_after`
> của dòng N−1 cho cùng `user_id`.
> **Cần:** một job đối chiếu chạy định kỳ, kiểm chuỗi liên tục. Lệch là dấu hiệu có đường ghi
> không qua `CreditService`.

> ⚠️ **`SELF_GENERATED_CARD` là câu hỏi separation of duties.** `FINANCE_ADMIN` sinh mã (UC-099)
> **và** có tài khoản `USER`. Sinh mã 1.000 điểm rồi tự nạp = tự in tiền.
> **Không chặn được bằng code** (cùng người, hai vai). Giảm bằng: `credit_cards.created_by` +
> `used_by` đều ghi, và `SUPER_ADMIN` xem được báo cáo "mã do ai sinh, ai dùng". Với đồ án thì
> ghi nhận là đủ — nhưng phải **trả lời được** khi hội đồng hỏi.

## Business rule

| # | Rule |
| --- | --- |
| BR-094-1 | UC này bị chặn bởi `TODO(PAYMENT_SCOPE)`. Không gen code MVP nếu dự án chưa chốt dùng mã nạp thật hoặc điểm giả lập. |
| BR-094-2 | Mã thẻ chỉ được dùng một lần. Sau khi dùng thành công, mã phải chuyển sang trạng thái đã dùng trong cùng thao tác cộng điểm. |
| BR-094-3 | Hệ thống không bao giờ lưu mã thẻ dạng thô. Chỉ lưu giá trị đã băm của mã. |
| BR-094-4 | Hệ thống không được ghi mã thẻ vào log, kể cả log lỗi, log request body hoặc exception message. |
| BR-094-5 | Khi người dùng nhập mã sai, mã đã dùng hoặc mã hết hạn, hệ thống trả một thông báo chung để tránh dò mã. |
| BR-094-6 | Mỗi tài khoản bị giới hạn số lần nhập sai trong một khoảng thời gian. Đề xuất 5 lần/giờ theo `user_id`. |
| BR-094-7 | Việc đổi trạng thái mã, cộng điểm và ghi giao dịch phải xảy ra nhất quán. Không được cộng điểm mà mã vẫn còn dùng được, hoặc mã đã dùng nhưng điểm chưa cộng. |
| BR-094-8 | Mọi thay đổi điểm phải ghi một dòng giao dịch có số dư trước và số dư sau. |
| BR-094-9 | Endpoint nạp mã là thao tác thay đổi giá trị tài chính/điểm, nên bắt buộc có CSRF token với web dùng cookie. |
| BR-094-10 | Chỉ một service trung tâm được phép thay đổi số dư điểm. Không để nhiều module tự cập nhật trực tiếp số dư. |

## API · DB

```
POST /api/credits/redeem
```

| Bảng | Vai trò |
| --- | --- |
| `credit_cards` | Đọc (`FOR UPDATE`) · **Ghi** (`status`, `used_by`, `used_at`) |
| `user_credits` | Đọc (`FOR UPDATE`) · **Ghi** (`balance`) |
| `credit_transactions` | **Ghi** — không bao giờ xoá |

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Mã hợp lệ 100 điểm | `balance` +100, sổ cái có dòng đủ `balance_before`/`after` |
| T2 | Mã đã dùng | 400, **cùng** thông báo T3 |
| T3 | Mã không tồn tại | 400, **cùng** thông báo T2 |
| T4 | **Hai request cùng mã song song** | Chỉ **một** cộng điểm |
| T5 | Nhập sai 6 lần | 429 |
| T6 | Đổi IP rồi nhập tiếp | **Vẫn** bị chặn (đếm theo user) |
| T7 | Thiếu CSRF token | 403 |
| T8 | Đọc file log sau khi nạp | **Không** chứa mã thẻ |
| T9 | Đọc `credit_cards` trong DB | Chỉ có hash, **không** có mã thô |
| T10 | Lỗi ở bước 13 | `balance` **không** đổi, `credit_cards` vẫn `UNUSED` |
| T11 | Job đối chiếu sổ cái | Chuỗi `balance_before`/`after` liên tục |

---

# UC-095 · Nhập mã thẻ để kích hoạt gói tháng

| | |
|---|---|
| **UC-ID** | UC-095 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Mã thẻ loại gói (không phải điểm) → kích hoạt Premium tháng. Premium = **không giới hạn** 4
tính năng tốn phí trong tháng.

## Tiền điều kiện

Như UC-094, thêm: mã có `card_type = SUBSCRIPTION` và trỏ tới một `plan`.

## Hậu điều kiện

| Kết quả | Trạng thái (cùng transaction) |
|---|---|
| Thành công | `credit_cards.status = USED`; `user_subscriptions` thêm/gia hạn với `expires_at`; `credit_transactions` ghi dòng loại `SUBSCRIPTION` |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nhập mã gói |
| 2 | Client | `POST /api/credits/redeem` + CSRF |
| 3 | System | Bước 3–8 như UC-094 |
| 4 | System | Nhận ra `card_type = SUBSCRIPTION` |
| 5 | System | `SELECT user_subscriptions FOR UPDATE` |
| 6 | System | Tính `expires_at` mới — **cộng dồn** nếu còn hạn (xem A1) |
| 7 | System | Upsert `user_subscriptions` |
| 8 | System | `credit_cards.status = USED` |
| 9 | System | `INSERT credit_transactions` loại `SUBSCRIPTION`, `amount = 0` điểm nhưng ghi giá trị gói |
| 10 | System | **Commit** |
| 11 | System | Trả `{plan, expires_at}` |

## Luồng thay thế

**A1 — Đang còn hạn Premium**
`expires_at` mới = `expires_at` cũ + 1 tháng (**cộng dồn**), không phải `now() + 1 tháng` —
nếu không thì người dùng mất phần còn lại.

**A2 — Gói đã hết hạn từ trước**
`expires_at` mới = `now() + 1 tháng`.

**A3 — Nhập mã điểm nhưng vào endpoint gói**
Cùng endpoint xử lý cả hai; phân nhánh theo `card_type`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| Mọi exception UC-094 | | | Áp dụng đầy đủ |
| `SUBSCRIPTION_OVERWRITTEN` | — | 🔴 Ghi đè `expires_at` thay vì cộng dồn | Xem ghi chú |
| `PLAN_NOT_FOUND` | 500 | Mã trỏ tới `plan` đã xoá | Rollback; không xoá `plans` đang có mã chưa dùng |
| `SUBSCRIPTION_WITHOUT_LEDGER` | — | 🔴 Kích hoạt gói mà không ghi sổ cái | Tranh chấp không tra được |
| `EXPIRES_AT_TIMEZONE` | — | 🔴 Tính hết hạn bằng giờ máy chủ | `TIMESTAMPTZ` + UTC; hiện cho người dùng theo giờ VN |
| `MONTH_LENGTH_AMBIGUOUS` | — | "1 tháng" từ 31/01 là ngày nào? | ⚠️ Xem ghi chú |
| `DOUBLE_ACTIVATION` | — | Hai request song song | `FOR UPDATE` cả `credit_cards` và `user_subscriptions` |

> 🔴 **`SUBSCRIPTION_OVERWRITTEN` — lỗi làm người dùng mất tiền mà không ai báo lỗi.** Người dùng
> còn 20 ngày Premium, nạp thêm một mã gói. Nếu đặt `expires_at = now() + 1 month` thì họ **mất
> 20 ngày** đã trả tiền.
> Người dùng thường không phát hiện ngay, và khi phát hiện thì là tranh chấp (UC-102) mà
> `FINANCE_ADMIN` phải tra sổ cái để xác minh.
> **Đúng:** `expires_at = GREATEST(expires_at, now()) + 1 month`.

> ⚠️ **`MONTH_LENGTH_AMBIGUOUS`:** kích hoạt ngày 31/01, "1 tháng sau" là 28/02 hay 03/03?
> PostgreSQL `+ INTERVAL '1 month'` từ 31/01 cho 28/02 (kẹp cuối tháng) — hợp lý và **nhất quán**.
> Dùng `INTERVAL` của DB thay vì tính bằng Java để tránh hai nơi hai kết quả. Ghi rõ luật này
> trong tài liệu để không bị hỏi bất ngờ.

## Business rule

| # | Rule |
| --- | --- |
| BR-095-1 | UC này bị chặn bởi `TODO(PAYMENT_SCOPE)`. Không gen code MVP nếu chưa chốt có bán gói tháng hay không. |
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

`credit_cards` · `user_subscriptions` · `plans` · `credit_transactions`

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chưa có gói, nạp mã tháng | `expires_at = now() + 1 tháng` |
| T2 | Còn 20 ngày, nạp thêm | `expires_at` **cộng dồn** = cũ + 1 tháng |
| T3 | Gói hết hạn 5 ngày trước | `expires_at = now() + 1 tháng` |
| T4 | Kích hoạt ngày 31/01 | `expires_at = 28/02` (nhất quán) |
| T5 | Hai request song song | Chỉ **một** lần cộng hạn |
| T6 | Sau khi kích hoạt | Sổ cái **có** dòng |

---

# UC-096 · Trừ điểm khi dùng tính năng tốn phí

| | |
|---|---|
| **UC-ID** | UC-096 · **Actor** `SYSTEM` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

`QuotaService` — cửa chung cho 4 tính năng tốn phí. Thứ tự: dùng lượt free trước → hết thì trừ
điểm → hết cả thì chặn.

**Bốn tính năng:** AI sinh bài (UC-048) · dịch (UC-060) · trợ lý ảo (UC-085) · chấm bài thuê (UC-103)

## Tiền điều kiện

1. Đang trong transaction của caller
2. `feature_code` thuộc 4 tính năng
3. `TODO(PAYMENT_SCOPE)` đã chốt

## Hậu điều kiện

| Trường hợp | Trạng thái |
| --- | --- |
| Premium còn hạn | **Không trừ gì**, cho qua |
| Còn lượt free | `feature_usage` +1; **không** trừ điểm; **không** ghi sổ cái |
| Hết free, còn điểm | `user_credits.balance` −1; `credit_transactions` ghi dòng `DEDUCT` |
| Hết cả | Ném `QUOTA_EXCEEDED` — **không đổi gì** |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Caller | `quotaService.consume(userId, featureCode)` |
| 2 | System | Kiểm đang trong transaction |
| 3 | System | Đọc `user_subscriptions` — Premium còn hạn → **trả ngay, không trừ** |
| 4 | System | `SELECT feature_usage FOR UPDATE` — đếm lượt đã dùng |
| 5 | System | Còn lượt free → `INSERT/UPDATE feature_usage`, trả `{source: FREE}` |
| 6 | System | Hết free → `SELECT user_credits FOR UPDATE` |
| 7 | System | `balance < 1` → ném `QUOTA_EXCEEDED` (rollback toàn bộ) |
| 8 | System | `balance_before = balance`; `balance -= 1` |
| 9 | System | `INSERT credit_transactions` loại `DEDUCT`, ghi `feature_code`, `balance_before`/`after` |
| 10 | System | Trả `{source: CREDIT, new_balance}` |

## Luồng thay thế

**A1 — Premium** — bước 3 trả ngay. **Không** ghi `feature_usage` (không giới hạn thì không cần đếm) — hoặc vẫn ghi để thống kê, cần chốt.

**A2 — Cache hit (dịch — UC-060)** — caller **không gọi** UC này vì không gọi API ngoài, không tốn tiền.

**A3 — Tính năng lỗi sau khi trừ** — UC-097 hoàn.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `QUOTA_EXCEEDED` | 402 | Hết free và hết điểm | Ném lỗi, **rollback**, không đổi gì |
| `NOT_IN_TRANSACTION` | 500 | 🔴 Gọi ngoài transaction | Xem ghi chú |
| `NEGATIVE_BALANCE` | 500 | Trừ khi `balance = 0` | `CHECK (balance >= 0)` chặn; nhưng phải kiểm ở bước 7 trước |
| `DEDUCT_WITHOUT_LEDGER` | — | 🔴 Trừ mà không ghi sổ cái | Cùng lỗi UC-094 |
| `FREE_QUOTA_COUNTED_AS_CREDIT` | — | 🔴 Dùng lượt free nhưng trừ cả điểm | Xem ghi chú |
| `DOUBLE_DEDUCT` | — | 🔴 Trừ hai lần cho một lượt dùng | Xem ghi chú |
| `QUOTA_PERIOD_UNDEFINED` | 500 | ⚠️ Chưa chốt kỳ hạn lượt free | Chặn — UC-093 đã nêu |
| `FEATURE_CODE_UNKNOWN` | 500 | `feature_code` lạ | Chặn — không biết tính phí thế nào |
| `CHARGED_FOR_NON_API_ACTION` | — | 🔴 Tính phí tra từ điển / xem lịch sử | Xem ghi chú |
| `RACE_ON_LAST_CREDIT` | — | Hai request cùng dùng điểm cuối | `FOR UPDATE` bước 6 |

> 🔴 **`NOT_IN_TRANSACTION` — cùng luật với `MasteryService` (UC-042), nhưng hậu quả là tiền.**
> Nếu trừ điểm ở transaction riêng: trừ thành công, tính năng chính lỗi, rollback tính năng
> nhưng **điểm đã mất**. Người dùng trả tiền cho việc không xảy ra.
> Đây là `QUOTA_DEDUCTED_BUT_API_FAILED` (UC-048, UC-060, UC-085) nhìn từ gốc.
> **Lưu ý tinh tế:** gọi API ngoài **không** được ở trong transaction (giữ khoá DB 10 giây).
> Nên thứ tự đúng là: trừ điểm (transaction A, commit) → gọi API → lỗi thì **hoàn** (transaction B).
> Nghĩa là `consume()` chạy trong transaction A, và **phải có** UC-097 làm đối trọng.

> 🔴 **`FREE_QUOTA_COUNTED_AS_CREDIT` — trừ hai lần cho một lượt.** Nếu bước 5 ghi
> `feature_usage` **rồi** vẫn chạy tiếp xuống bước 8 (thiếu `return`), người dùng mất cả lượt
> free **và** 1 điểm. Lỗi một dòng code, hậu quả là tiền.

> 🔴 **`DOUBLE_DEDUCT`:** caller gọi `consume()` hai lần cho một hành động (ví dụ retry ở tầng
> trên mà không phân biệt retry với lượt mới). Với dịch thì người dùng bấm "Dịch" một lần, mất
> 2 điểm.
> **Cần:** caller truyền một `idempotency_key` (ví dụ `job_id`, `request_id`), và `consume()` bỏ
> qua nếu key đã tồn tại. Hoặc chốt rõ: **chỉ** gọi `consume()` ở một chỗ duy nhất mỗi luồng.

> 🔴 **`CHARGED_FOR_NON_API_ACTION` — lần thứ tư vấn đề này xuất hiện** (UC-031, UC-061, UC-086,
> và đây). Kết luận đã rõ: **`QuotaService` tính theo lượt gọi API ngoài**, không theo tính năng.
> — Dịch một đoạn = 1 lượt (gọi API)
> — Bấm 10 từ trong kết quả dịch = **0 lượt** (đọc DB)
> — Mở lịch sử hỏi AI = **0 lượt**
> — Cache hit khi dịch = **0 lượt**
> Phải ghi thành luật trong `business.md`, không để mỗi người hiểu một cách.

## Business rule

| # | Rule |
| --- | --- |
| BR-096-1 | UC này bị chặn bởi `TODO(PAYMENT_SCOPE)`. Trong MVP chưa chốt payment, chỉ dùng quota miễn phí đơn giản thay vì trừ điểm. |
| BR-096-2 | Chỉ các hành động gọi API ngoài hoặc tiêu tốn chi phí vận hành thật mới được tính quota hoặc trừ điểm. |
| BR-096-3 | Tra từ điển nội bộ, xem lịch sử, xem kết quả cũ, bấm từ trong kết quả dịch hoặc đọc dữ liệu đã lưu không được trừ điểm. |
| BR-096-4 | Thứ tự xử lý khi có payment là: gói còn hạn → quota miễn phí → điểm trả phí → chặn. |
| BR-096-5 | Dùng quota miễn phí thì không được trừ thêm điểm. |
| BR-096-6 | Trừ điểm phải ghi giao dịch có số dư trước và số dư sau. |
| BR-096-7 | Mỗi hành động tốn phí phải có khóa idempotency để tránh retry làm trừ hai lần. |
| BR-096-8 | Khi hết quota và hết điểm, hệ thống phải từ chối trước khi gọi API ngoài. Không được gọi API rồi mới báo hết điểm. |
| BR-096-9 | Không được để số dư âm. |

## API · DB

Service nội bộ — không endpoint.

`user_subscriptions` · `feature_usage` · `user_credits` · `credit_transactions`

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Premium còn hạn | **Không** trừ gì, cho qua |
| T2 | Còn 5 lượt free | `feature_usage` +1, `balance` **không** đổi |
| T3 | Hết free, `balance = 10` | `balance = 9`, sổ cái có dòng `DEDUCT` |
| T4 | Hết free, `balance = 0` | 402, **không đổi gì** |
| T5 | Gọi ngoài `@Transactional` | Ném lỗi ngay |
| T6 | Dùng lượt free | Chỉ trừ 1 lượt, **không** trừ điểm |
| T7 | Gọi 2 lần cùng `idempotency_key` | Trừ **một** lần |
| T8 | Hai request cùng dùng điểm cuối | Một thành công, một 402 |
| T9 | Cache hit khi dịch | `consume()` **không** được gọi |

---

# UC-097 · Hoàn điểm khi tính năng lỗi

| | |
|---|---|
| **UC-ID** | UC-097 · **Actor** `SYSTEM` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Đối trọng của UC-096. Quy tắc 5 của bảng dính tiền: *"Gọi AI lỗi thì **hoàn điểm**"*.

Áp dụng cho: AI sinh bài lỗi (UC-049) · API dịch lỗi (UC-060) · trợ lý ảo timeout (UC-085) ·
teacher không nhận bài (UC-107).

## Tiền điều kiện

1. Đã có dòng `credit_transactions` loại `DEDUCT` cho hành động đó
2. Hành động thất bại **sau** khi trừ
3. Chưa hoàn cho hành động đó

## Hậu điều kiện

| Kết quả | Trạng thái (cùng transaction) |
| --- | --- |
| Hoàn | `user_credits.balance` tăng lại; `credit_transactions` thêm dòng **mới** loại `REFUND` |
| Đã hoàn rồi | Không làm gì (idempotent) |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Caller | `quotaService.refund(userId, originalTransactionId, reason)` |
| 2 | System | **Mở transaction** |
| 3 | System | Đọc dòng `DEDUCT` gốc — kiểm tồn tại và thuộc `userId` |
| 4 | System | Kiểm **chưa có** dòng `REFUND` trỏ tới nó |
| 5 | System | `SELECT user_credits FOR UPDATE` |
| 6 | System | `balance_before = balance`; `balance += amount` gốc |
| 7 | System | `INSERT credit_transactions` loại `REFUND`, `ref_transaction_id`, `reason`, `balance_before`/`after` |
| 8 | System | Nếu lượt free đã trừ → hoàn lượt trong `feature_usage` |
| 9 | System | **Commit** |
| 10 | System | Thông báo người dùng đã hoàn |

## Luồng thay thế

**A1 — Đã dùng lượt free (không trừ điểm)**
Hoàn **lượt**, không hoàn điểm. Đúng loại đã trừ.

**A2 — Premium (không trừ gì)**
Không hoàn gì. Không phải lỗi.

**A3 — Retry thành công ở lần 2**
Không hoàn — hành động cuối cùng đã xảy ra.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ORIGINAL_TRANSACTION_NOT_FOUND` | 500 | 🔴 Không tìm được dòng `DEDUCT` | Xem ghi chú |
| `ALREADY_REFUNDED` | — | Đã hoàn | **Idempotent** — bỏ qua im lặng |
| `DOUBLE_REFUND` | — | 🔴 Hoàn hai lần | Xem ghi chú |
| `REFUND_WITHOUT_DEDUCT` | — | 🔴 Hoàn khi chưa từng trừ | **Tự in điểm** — xem ghi chú |
| `REFUND_AMOUNT_MISMATCH` | — | 🔴 Hoàn nhiều hơn đã trừ | Chặn — `amount` lấy từ dòng gốc, **không** từ tham số |
| `REFUND_TRANSACTION_DELETED_ORIGINAL` | — | 🔴 Sửa/xoá dòng `DEDUCT` thay vì thêm `REFUND` | Xem ghi chú |
| `REFUND_NOT_OWNED` | 500 | Dòng gốc của người khác | Chặn |
| `REFUND_AFTER_SUCCESS` | — | Hoàn dù hành động đã thành công | Kiểm trạng thái hành động trước khi hoàn (A3) |
| `NO_REFUND_POLICY` | — | ⚠️ Chưa chốt: retry mấy lần rồi mới hoàn | Xem ghi chú |
| `REFUND_NOT_TRIGGERED` | — | 🔴 API lỗi mà **không ai** gọi hoàn | Xem ghi chú |

> 🔴 **`REFUND_WITHOUT_DEDUCT` là đường tự in điểm.** Nếu `refund()` nhận `amount` từ tham số
> và không kiểm dòng gốc, thì một bug (hoặc một endpoint lộ ra) cho phép cộng điểm tuỳ ý.
> **Bắt buộc:** `amount` **luôn** lấy từ dòng `DEDUCT` gốc; tham số chỉ có `originalTransactionId`
> và `reason`. Không có tham số `amount`.

> 🔴 **`REFUND_TRANSACTION_DELETED_ORIGINAL` vi phạm luật cốt lõi của sổ cái.**
> `credit_transactions` là "**không bao giờ xoá dòng nào**". Cách sai: xoá dòng `DEDUCT` để
> "hoàn". Cách đúng: thêm dòng `REFUND` **mới**.
> Vì sao quan trọng: UC-102 (tranh chấp) tái dựng số dư bằng cách cộng dồn sổ cái. Xoá một dòng
> là chuỗi `balance_before`/`after` đứt, và **không** ai biết đã từng trừ.

> 🔴 **`REFUND_NOT_TRIGGERED` là lỗi âm thầm tệ nhất của nhóm này.** Trừ điểm luôn chạy (vì nó
> ở đường thành công); hoàn điểm chỉ chạy ở đường **lỗi** — và đường lỗi ít được test.
> API dịch timeout 50 lần trong một ngày = 50 người mất điểm, và **không ai biết** cho tới khi
> có người khiếu nại.
> **Cần:** một báo cáo đối chiếu — đếm dòng `DEDUCT` mà hành động tương ứng `FAILED` nhưng
> **không** có dòng `REFUND`. Số đó phải bằng 0.

> ⚠️ **`NO_REFUND_POLICY`:** UC-049 retry 3 lần với backoff. Hoàn điểm sau lần 3 hay ngay lần 1?
> — Ngay lần 1: đơn giản, nhưng retry thành công thì phải trừ lại → phức tạp
> — Sau khi hết retry: đúng hơn, nhưng người dùng chờ 65 giây mới thấy điểm về
> **Khuyến nghị:** hoàn sau khi hết retry và job chuyển `FAILED`. Ghi rõ trong tài liệu.

## Business rule

| # | Rule |
| --- | --- |
| BR-097-1 | UC này chỉ áp dụng khi hệ thống đã chốt cơ chế trừ điểm hoặc quota. |
| BR-097-2 | Nếu một hành động đã bị trừ điểm nhưng sau đó thất bại do lỗi hệ thống hoặc API ngoài, người dùng phải được hoàn lại đúng phần đã bị trừ. |
| BR-097-3 | Hoàn điểm không được sửa hoặc xóa giao dịch trừ điểm cũ. Hệ thống phải tạo một giao dịch hoàn mới để giữ lịch sử đầy đủ. |
| BR-097-4 | Số điểm hoàn phải lấy từ giao dịch trừ điểm gốc, không lấy từ tham số client gửi lên. |
| BR-097-5 | Một giao dịch trừ điểm chỉ được hoàn một lần. Gọi hoàn nhiều lần phải cho kết quả idempotent. |
| BR-097-6 | Nếu hành động dùng quota miễn phí thay vì điểm, hệ thống hoàn quota tương ứng thay vì cộng điểm. |
| BR-097-7 | Hệ thống chỉ hoàn khi xác định hành động thật sự thất bại. Không hoàn cho hành động đã xử lý thành công. |
| BR-097-8 | Cần có báo cáo hoặc kiểm tra định kỳ để phát hiện hành động đã trừ điểm nhưng thất bại mà chưa được hoàn. |

## API · DB

Service nội bộ. `credit_transactions` · `user_credits` · `feature_usage`

> **Cần thêm:** cột `ref_transaction_id` trên `credit_transactions` + unique index
> `(ref_transaction_id) WHERE type = 'REFUND'`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | API lỗi sau khi trừ 1 điểm | `balance` +1, có dòng `REFUND` |
| T2 | Gọi hoàn 2 lần | Hoàn **một** lần |
| T3 | Hoàn khi chưa từng trừ | 500, **không** cộng điểm |
| T4 | Truyền `amount` lớn hơn | Bị bỏ qua — lấy từ dòng gốc |
| T5 | Dòng `DEDUCT` sau khi hoàn | **Vẫn còn** trong sổ cái |
| T6 | Đã dùng lượt free | Hoàn **lượt**, không hoàn điểm |
| T7 | Premium | Không hoàn gì |
| T8 | Báo cáo đối chiếu | 0 dòng `DEDUCT` thiếu `REFUND` |

---

# UC-098 · Xem lịch sử giao dịch điểm của mình

| | |
|---|---|
| **UC-ID** | UC-098 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Xem mọi thay đổi điểm của chính mình: nạp, trừ, hoàn — kèm số dư trước/sau và lý do.

## Tiền điều kiện

`USER` đã đăng nhập.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở "Lịch sử giao dịch" |
| 2 | Client | `GET /api/me/credit-transactions?page={p}` |
| 3 | System | Lấy `credit_transactions WHERE user_id = :currentUser` |
| 4 | System | Sắp mới nhất trước, phân trang |
| 5 | System | Trả `{type, amount, balance_before, balance_after, reason, created_at}` |
| 6 | Client | Hiện bảng |

## Luồng thay thế

**A1 — Chưa có giao dịch** — rỗng, hiện "bạn chưa có giao dịch nào".
**A2 — Lọc theo loại** — `?type=TOPUP`.
**A3 — Xem chi tiết một giao dịch** — hiện dòng liên quan (`REFUND` trỏ tới `DEDUCT` nào).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `TRANSACTIONS_NOT_OWNED` | 403 | 🔴 Xem giao dịch người khác | **IDOR** — endpoint `/me` |
| `CARD_CODE_IN_RESPONSE` | — | 🔴 Trả mã thẻ trong lịch sử | Xem ghi chú |
| `OTHER_USER_TRANSACTIONS_LEAKED` | — | 🔴 Thiếu `WHERE user_id` | **Lộ sổ cái toàn hệ thống** cho người dùng thường |
| `NO_TRANSACTIONS` | 200 (rỗng) | Chưa có | Không phải lỗi |
| `BALANCE_CHAIN_BROKEN` | — | 🔴 `balance_before` dòng N ≠ `balance_after` dòng N−1 | Xem ghi chú |
| `MISSING_PAGINATION` | — | Trả hết | Phân trang bắt buộc |
| `INTERNAL_REASON_LEAKED` | — | `reason` chứa chi tiết nội bộ | Dùng mã lý do + bản dịch, không dán message lỗi |

> 🔴 **`CARD_CODE_IN_RESPONSE`:** dòng `TOPUP` có `card_id`. Nếu join `credit_cards` và trả cả
> bảng thì lộ `code_hash` (không nghiêm trọng) — nhưng nếu ở đâu đó còn lưu mã thô thì lộ mã.
> **Cùng lý do** quy tắc 2 bắt chỉ lưu hash: có lưu thì có ngày lọt ra. Response chỉ cần
> `amount` và `created_at`, **không** cần tham chiếu thẻ.

> 🔴 **`OTHER_USER_TRANSACTIONS_LEAKED` là IDOR nghiêm trọng nhất trong cả tài liệu.** Thiếu
> `WHERE user_id` ở đây không chỉ lộ dữ liệu một người — nó lộ **toàn bộ sổ cái tài chính** cho
> bất kỳ ai đăng nhập. Cùng mẫu lỗi `SEARCHED_OTHER_USERS_NOTES` (UC-065) nhưng dữ liệu là tiền.
> **Cần test riêng:** hai user có giao dịch, mỗi người chỉ thấy của mình.

> 🔴 **`BALANCE_CHAIN_BROKEN` là nơi người dùng phát hiện lỗi trước ta.** Người dùng thấy dòng
> "số dư sau: 50" rồi dòng tiếp "số dư trước: 30" → biết ngay có gì sai, và đó là cơ sở khiếu nại.
> Đây chính là bất biến mà job đối chiếu (UC-094) phải kiểm — nhưng ở màn này nó **hiện ra cho
> người dùng thấy**. Lý do phải làm đúng từ đầu.

## Business rule

| # | Rule |
| --- | --- |
| BR-098-1 | UC này chỉ triển khai khi hệ thống có cơ chế điểm/giao dịch. Không gen code nếu MVP chỉ dùng quota đơn giản. |
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

`credit_transactions` (đọc)

> **Index cần:** `credit_transactions(user_id, created_at DESC)`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xem lịch sử của mình | Đủ dòng, có `balance_before`/`after` |
| T2 | **Hai user có giao dịch** | Mỗi người chỉ thấy **của mình** |
| T3 | Gửi `user_id` khác | Bị bỏ qua |
| T4 | Kiểm response | **Không** chứa mã thẻ |
| T5 | Nạp rồi trừ rồi hoàn | 3 dòng, chuỗi số dư **liên tục** |
| T6 | 500 giao dịch | Phân trang, không trả hết |

---

# UC-099 · Sinh lô mã thẻ nạp

| | |
|---|---|
| **UC-ID** | UC-099 · **Actor** `FINANCE_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Sinh lô mã thẻ để công ty bán. Quyết định v2 ghi: *"🔴 Đây là role nguy hiểm nhất. Mã thẻ phải
sinh bằng `SecureRandom`, ≥16 ký tự, lưu hash không lưu mã thô. Mọi thao tác phải ghi audit log."*

## Tiền điều kiện

1. Role **`FINANCE_ADMIN`** (không phải `CONTENT_ADMIN`, không phải `SUPER_ADMIN` mặc định)
2. CSRF token
3. `plan` hoặc giá trị điểm hợp lệ

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Thành công | N dòng `credit_cards` `status = UNUSED`, chỉ có `code_hash`; audit log ghi ai sinh, bao nhiêu, giá trị nào |
| Mã thô | **Hiện một lần duy nhất** cho `FINANCE_ADMIN` tải về; không lưu ở đâu |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Mở trang sinh mã, nhập số lượng + loại + giá trị |
| 2 | Client | `POST /api/admin/credit-cards/batch` + CSRF |
| 3 | System | Kiểm role `FINANCE_ADMIN` **ở server** |
| 4 | System | Kiểm số lượng ≤ giới hạn lô |
| 5 | System | Với mỗi mã: sinh bằng **`SecureRandom`**, ≥ 16 ký tự |
| 6 | System | Tính `code_hash`; **chỉ** lưu hash |
| 7 | System | `INSERT credit_cards` — `status = UNUSED`, `created_by`, `batch_id`, `expires_at` |
| 8 | System | Ghi **audit log**: ai, khi nào, bao nhiêu mã, tổng giá trị |
| 9 | System | Trả file CSV chứa mã thô — **một lần duy nhất** |
| 10 | `FINANCE_ADMIN` | Tải file, lưu ngoài hệ thống |

## Luồng thay thế

**A1 — Trùng mã (xác suất cực nhỏ)**
Unique trên `code_hash` → `INSERT` lỗi → sinh lại mã đó. Không bỏ cả lô.

**A2 — Đặt hạn dùng**
`expires_at` tuỳ chọn. Không đặt = không hết hạn.

**A3 — Vô hiệu một lô**
`status = VOIDED` cho cả `batch_id`. Không xoá dòng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN_ROLE` | 403 | 🔴 Không phải `FINANCE_ADMIN` | Xem ghi chú |
| `WEAK_RANDOM_USED` | — | 🔴 Dùng `Random` thay `SecureRandom` | Xem ghi chú |
| `CODE_TOO_SHORT` | — | 🔴 < 16 ký tự | Chặn — quy tắc 1 |
| `SEQUENTIAL_CODES` | — | 🔴 Mã theo số thứ tự | **Đoán được mã tiếp theo** — cấm tuyệt đối |
| `RAW_CODE_STORED` | — | 🔴 Lưu mã thô trong DB | Vi phạm quy tắc 2 |
| `RAW_CODE_IN_LOG` | — | 🔴 Mã thô vào log | Vi phạm quy tắc 5 |
| `RAW_CODE_RETRIEVABLE` | 403 | 🔴 Có endpoint xem lại mã thô | Xem ghi chú |
| `NO_AUDIT_LOG` | — | 🔴 Không ghi ai sinh | Không truy được trách nhiệm |
| `BATCH_TOO_LARGE` | 400 | > 1.000 mã/lô | Chặn — lô lớn khó kiểm soát |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu CSRF | Chặn |
| `HASH_COLLISION` | 500 | Trùng hash | Sinh lại (A1) |
| `CONTENT_ADMIN_ATTEMPTED` | 403 | `CONTENT_ADMIN` gọi | **Separation of duties** — xem ghi chú |

> 🔴 **`FORBIDDEN_ROLE` + `CONTENT_ADMIN_ATTEMPTED` là lý do tách `ADMIN` thành 3 role.**
> Quyết định v2 ghi rõ: *"Người nhập đề thi của thầy **không nên** có quyền sinh mã thẻ nạp tiền.
> Đây là điểm cộng khi bảo vệ."*
> UC này là nơi luật đó được thực thi. Nếu kiểm role là `hasRole('ADMIN')` chung thì việc tách
> 3 role thành vô nghĩa — và mất luôn điểm cộng khi bảo vệ.
> **Đúng:** `@PreAuthorize("hasRole('FINANCE_ADMIN')")`. Và `SUPER_ADMIN` **không** tự động có
> quyền này (quyết định v2: "Không tự động có quyền của `CONTENT_ADMIN` hay `FINANCE_ADMIN`").

> 🔴 **`WEAK_RANDOM_USED` + `SEQUENTIAL_CODES` là rủi ro #1 trong bảng bảo mật 6.1.**
> `java.util.Random` là PRNG tuyến tính — biết vài giá trị đầu là **tính được** cả dãy. Với mã
> thẻ thì kẻ tấn công mua 2 mã, suy ra mã còn lại của cả lô.
> **Cần:** `SecureRandom` + một test assert không dùng `Random`. ArchUnit chặn được import
> `java.util.Random` trong package thanh toán.

> 🔴 **`RAW_CODE_RETRIEVABLE` — mã thô hiện đúng một lần.** Nếu có endpoint "xem lại mã của lô X"
> thì mọi lợi ích của việc lưu hash biến mất: `FINANCE_ADMIN` bị chiếm tài khoản là kẻ tấn công
> tải hết mã chưa dùng.
> **Đúng:** trả mã thô **chỉ** trong response của lệnh sinh. Sau đó không có đường nào lấy lại.
> Mất file là phải vô hiệu lô và sinh lại (A3).

## Business rule

| # | Rule |
| --- | --- |
| BR-099-1 | UC này bị chặn bởi `TODO(PAYMENT_SCOPE)`. Không gen code MVP nếu chưa chốt bán mã nạp. |
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

`credit_cards` (ghi) · audit log

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `FINANCE_ADMIN` sinh 100 mã | 100 dòng `UNUSED`, CSV trả về |
| T2 | `CONTENT_ADMIN` gọi | **403** |
| T3 | `USER` gọi | 403 |
| T4 | `SUPER_ADMIN` (không có role finance) | 403 |
| T5 | Đọc `credit_cards` trong DB | Chỉ hash, **không** mã thô |
| T6 | Gọi lại xem mã lô cũ | **Không có** endpoint đó |
| T7 | Đọc log | **Không** chứa mã thô |
| T8 | ArchUnit | Package thanh toán **không** import `java.util.Random` |
| T9 | Sau khi sinh | Audit log có ai + số lượng + giá trị |
| T10 | Sinh 2.000 mã | 400 `BATCH_TOO_LARGE` |

---

# UC-100 · Xem sổ cái giao dịch toàn hệ thống

| | |
|---|---|
| **UC-ID** | UC-100 · **Actor** `FINANCE_ADMIN` · **Pri** P1 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Xem toàn bộ `credit_transactions` để đối soát và xử lý tranh chấp. Đây là quyền đọc dữ liệu
tài chính của **mọi** người dùng.

## Tiền điều kiện

Role `FINANCE_ADMIN`.

## Hậu điều kiện

Chỉ đọc — **nhưng** ghi audit log việc truy cập (xem exception).

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Mở sổ cái |
| 2 | Client | `GET /api/admin/credit-transactions?from={d}&to={d}&user_id={u}&type={t}` |
| 3 | System | Kiểm role `FINANCE_ADMIN` |
| 4 | System | Lấy giao dịch theo lọc, phân trang |
| 5 | System | Tính tổng: nạp · trừ · hoàn trong kỳ |
| 6 | System | **Ghi audit log** ai đã xem sổ cái |
| 7 | System | Trả dữ liệu + tổng hợp |
| 8 | Client | Hiện bảng + biểu đồ |

## Luồng thay thế

**A1 — Lọc theo một người** — dùng khi xử lý tranh chấp (UC-102).
**A2 — Xuất CSV** — cho đối soát ngoài hệ thống. Cũng ghi audit log.
**A3 — Đối chiếu tổng** — tổng `balance` mọi người = tổng nạp − tổng trừ + tổng hoàn.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN_ROLE` | 403 | Không phải `FINANCE_ADMIN` | Chặn ở server |
| `PII_EXPOSED_IN_LEDGER` | — | 🔴 Hiện email/tên thật mọi người | Xem ghi chú |
| `NO_ACCESS_AUDIT` | — | 🔴 Không ghi ai xem sổ cái | Xem ghi chú |
| `LEDGER_TOTALS_MISMATCH` | — | 🔴 Tổng sổ cái ≠ tổng `balance` | Xem ghi chú |
| `MISSING_DATE_FILTER` | 400 | Không có khoảng thời gian | Chặn — tránh quét toàn bảng |
| `UNBOUNDED_EXPORT` | 400 | Xuất toàn bộ không giới hạn | Giới hạn kỳ xuất |
| `SLOW_QUERY` | — | Thiếu index | `(created_at)`, `(user_id, created_at)` |
| `CONTENT_ADMIN_ATTEMPTED` | 403 | Separation of duties | Chặn |

> 🔴 **`PII_EXPOSED_IN_LEDGER` — cân bằng giữa làm được việc và bảo vệ dữ liệu.**
> `FINANCE_ADMIN` **cần** biết giao dịch của ai để xử lý tranh chấp. Nhưng hiện email đầy đủ của
> 1.000 người trên một màn hình là lộ PII không cần thiết.
> Constitution có luật PII masking. **Đề xuất:** danh sách hiện `user_id` + email **đã che**
> (`ng***@gmail.com`); xem đầy đủ phải vào chi tiết một người, và **lần đó** ghi audit log.

> 🔴 **`NO_ACCESS_AUDIT`:** quyết định v2 ghi *"Mọi thao tác phải ghi audit log"* cho
> `FINANCE_ADMIN`. Không chỉ thao tác **ghi** — mà cả thao tác **đọc** sổ cái.
> Lý do: nếu dữ liệu tài chính lọt ra ngoài, audit log là cách duy nhất biết ai đã truy cập.
> Không có log thì mọi `FINANCE_ADMIN` đều là nghi phạm như nhau.

> 🔴 **`LEDGER_TOTALS_MISMATCH` là kiểm tra sức khoẻ quan trọng nhất của hệ thống tiền.**
> Bất biến: `Σ user_credits.balance` = `Σ TOPUP − Σ DEDUCT + Σ REFUND`.
> Lệch nghĩa là có đường đổi `balance` không qua sổ cái (`BALANCE_UPDATED_WITHOUT_LEDGER`,
> UC-094) hoặc có dòng sổ cái không áp vào `balance`.
> **Cần:** hiện con số này ngay trên màn sổ cái. Lệch là biết ngay, không phải chờ khiếu nại.

## Business rule

| # | Rule |
| --- | --- |
| BR-100-1 | UC này chỉ triển khai khi hệ thống có điểm/giao dịch thật. Không gen code nếu MVP chỉ dùng quota đơn giản. |
| BR-100-2 | Chỉ `FINANCE_ADMIN` được xem sổ cái toàn hệ thống. |
| BR-100-3 | Mọi lần xem hoặc xuất sổ cái phải ghi audit log. |
| BR-100-4 | Danh sách sổ cái phải có bộ lọc thời gian bắt buộc và phân trang. |
| BR-100-5 | Khi hiển thị danh sách, thông tin người dùng phải được hạn chế. Email nên được che một phần; thông tin đầy đủ chỉ xem ở trang chi tiết khi thật sự cần. |
| BR-100-6 | Dữ liệu sổ cái trong UC này chỉ được đọc, không được sửa hoặc xóa. |
| BR-100-7 | Hệ thống phải có đối chiếu giữa tổng số dư người dùng và tổng giao dịch để phát hiện lệch. |
| BR-100-8 | Xuất file sổ cái phải giới hạn theo khoảng thời gian, không cho xuất toàn bộ dữ liệu không giới hạn. |

## API · DB

```
GET /api/admin/credit-transactions
GET /api/admin/credit-transactions/export
GET /api/admin/credit-transactions/reconciliation
```

`credit_transactions` · `user_credits` (đọc) · audit log (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `FINANCE_ADMIN` xem kỳ tháng | Danh sách + tổng hợp |
| T2 | `CONTENT_ADMIN` gọi | 403 |
| T3 | Không có khoảng thời gian | 400 |
| T4 | Danh sách | Email **đã che** |
| T5 | Sau khi xem | Audit log có dòng |
| T6 | Đối chiếu | `Σ balance` = `Σ` sổ cái |
| T7 | Cố `DELETE` một dòng sổ cái | **Không có** endpoint đó |

---

# UC-101 · Quản lý gói dịch vụ (tạo, sửa giá)

| | |
|---|---|
| **UC-ID** | UC-101 · **Actor** `FINANCE_ADMIN` · **Pri** P2 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Tạo và sửa `plans` — tên gói, giá, thời hạn, quyền. Giá do **nhóm tự set**.

## Tiền điều kiện

Role `FINANCE_ADMIN`; CSRF.

## Hậu điều kiện

`plans` thêm/cập nhật dòng. **Không** ảnh hưởng `user_subscriptions` đang hoạt động.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Mở quản lý gói |
| 2 | Client | `GET /api/admin/plans` |
| 3 | `FINANCE_ADMIN` | Tạo gói mới hoặc sửa giá |
| 4 | Client | `POST`/`PUT /api/admin/plans/{id}` + CSRF |
| 5 | System | Kiểm role |
| 6 | System | Validate: giá ≥ 0, thời hạn > 0, tên không trùng |
| 7 | System | Ghi `plans`; **giữ** giá cũ trong lịch sử (xem exception) |
| 8 | System | Audit log |

## Luồng thay thế

**A1 — Đổi giá gói đang có người dùng** — người đang dùng **không** bị ảnh hưởng; giá mới áp cho lần mua sau.
**A2 — Ngưng bán gói** — `is_active = false`, **không** xoá. Người đang dùng vẫn dùng hết hạn.
**A3 — Sửa quyền của gói** — ảnh hưởng ngay người đang dùng; cần cảnh báo rõ.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN_ROLE` | 403 | Không phải `FINANCE_ADMIN` | Chặn |
| `NEGATIVE_PRICE` | 400 | Giá < 0 | Chặn |
| `PRICE_TYPE_FLOAT` | — | 🔴 Giá dùng `FLOAT` | Vi phạm AC-07 — phải `NUMERIC(12,2)` |
| `PLAN_DELETED_WITH_ACTIVE_SUBS` | 409 | 🔴 Xoá gói đang có người dùng | Xem ghi chú |
| `PRICE_HISTORY_LOST` | — | 🔴 Sửa giá tại chỗ | Xem ghi chú |
| `RETROACTIVE_PRICE_CHANGE` | — | 🔴 Giá mới áp cho người đã mua | Xem ghi chú |
| `DUPLICATE_PLAN_NAME` | 409 | Tên trùng | Chặn |
| `NO_AUDIT_LOG` | — | Không ghi ai đổi giá | Bắt buộc |
| `PLAN_WITH_UNUSED_CARDS_DELETED` | 409 | Xoá gói còn mã chưa dùng | Chặn — UC-095 sẽ lỗi |

> 🔴 **`PRICE_HISTORY_LOST` + `RETROACTIVE_PRICE_CHANGE` là cùng một vấn đề kế toán.**
> Nếu `plans.price` sửa tại chỗ:
> — `credit_transactions` dòng cũ chỉ có `plan_id`, tra ra **giá mới** → sổ cái nói người dùng
> trả 200k khi họ trả 100k
> — Đối soát tài chính sai
> **Đúng:** `credit_transactions` ghi **giá tại thời điểm giao dịch** (snapshot), không chỉ
> `plan_id`. Đây là nguyên tắc kế toán cơ bản: bản ghi giao dịch phải tự đủ nghĩa.
> **Cùng loại vấn đề** với `QUESTION_CHANGED_MID_ATTEMPT` (UC-035) — dữ liệu tham chiếu đổi làm
> bản ghi cũ sai nghĩa.

> 🔴 **`PLAN_DELETED_WITH_ACTIVE_SUBS`:** xoá `plans` khi `user_subscriptions` còn trỏ tới →
> UC-093 trả `PLAN_NOT_FOUND`, người dùng đang trả tiền thấy "gói không tồn tại".
> **Đúng:** `is_active = false`, **không** xoá (A2). Cùng luật với soft delete ở UC-064.

## Business rule

| # | Rule |
| --- | --- |
| BR-101-1 | UC này bị chặn bởi `TODO(PAYMENT_SCOPE)`. Không gen code MVP nếu chưa chốt bán gói dịch vụ. |
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

`plans` (đọc + ghi) · `user_subscriptions` · `credit_cards` (đọc để kiểm) · audit log

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tạo gói mới | 201 |
| T2 | `CONTENT_ADMIN` gọi | 403 |
| T3 | Giá âm | 400 |
| T4 | Xoá gói đang có người dùng | 409 |
| T5 | Đổi giá 100k → 200k | Giao dịch cũ **vẫn** hiện 100k |
| T6 | Xoá gói còn mã chưa dùng | 409 |
| T7 | Sau khi đổi giá | Audit log có dòng |

---

# UC-102 · Xử lý tranh chấp về điểm

| | |
|---|---|
| **UC-ID** | UC-102 · **Actor** `FINANCE_ADMIN` · **Pri** P2 · **Scope** MVP · **FT** 6.1 |

## Mô tả

Người dùng khiếu nại "tôi nạp mà không thấy điểm" hoặc "bị trừ oan". `FINANCE_ADMIN` tra sổ
cái, xác minh, và điều chỉnh nếu cần.

> Rủi ro #4 trong bảng bảo mật 6.1: *"**Tranh chấp** — `credit_transactions` ghi mọi thay đổi
> điểm: ai, khi nào, lý do, số dư trước và sau. **Không bao giờ xoá**."*

## Tiền điều kiện

1. Role `FINANCE_ADMIN`; CSRF
2. Có khiếu nại cụ thể
3. Sổ cái đầy đủ cho người đó

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Điều chỉnh | Thêm dòng `credit_transactions` loại `ADJUSTMENT` kèm lý do + ai duyệt; `balance` đổi |
| Không điều chỉnh | Chỉ ghi kết luận; **không** đổi `balance` |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `FINANCE_ADMIN` | Nhận khiếu nại, mở sổ cái của người đó (UC-100 A1) |
| 2 | System | Trả mọi giao dịch + chuỗi `balance_before`/`after` |
| 3 | `FINANCE_ADMIN` | Đối chiếu: chuỗi liên tục? có `DEDUCT` thiếu `REFUND`? |
| 4 | `FINANCE_ADMIN` | Kết luận |
| 5 | Client | `POST /api/admin/credit-adjustments` — `{user_id, amount, reason, dispute_ref}` + CSRF |
| 6 | System | Kiểm role |
| 7 | System | **Transaction:** `SELECT user_credits FOR UPDATE` |
| 8 | System | Đổi `balance`; `INSERT credit_transactions` loại `ADJUSTMENT` kèm `performed_by` + `reason` |
| 9 | System | **Audit log** riêng |
| 10 | System | Thông báo người dùng |

## Luồng thay thế

**A1 — Mã đã dùng bởi người khác**
Tra `credit_cards.used_by`. Nếu khác người khiếu nại → mã bị chia sẻ hoặc bị lộ; không cộng điểm,
giải thích.

**A2 — `DEDUCT` thiếu `REFUND`** (lỗi của hệ thống)
Gọi UC-097 đúng cách thay vì `ADJUSTMENT` thủ công — giữ ngữ nghĩa sổ cái.

**A3 — Trừ điểm nhưng tính năng đã chạy xong**
Không hoàn. Giải thích kèm dòng sổ cái làm chứng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN_ROLE` | 403 | Không phải `FINANCE_ADMIN` | Chặn |
| `ADJUSTMENT_WITHOUT_REASON` | 400 | 🔴 Không có lý do | Xem ghi chú |
| `LEDGER_ROW_MODIFIED` | — | 🔴 Sửa/xoá dòng cũ để "sửa lỗi" | Xem ghi chú |
| `ADJUSTMENT_WITHOUT_AUDIT` | — | 🔴 Không ghi ai điều chỉnh | Xem ghi chú |
| `SELF_ADJUSTMENT` | 403 | 🔴 `FINANCE_ADMIN` cộng điểm cho chính mình | Xem ghi chú |
| `UNLIMITED_ADJUSTMENT` | 400 | Điều chỉnh số lớn bất thường | ⚠️ Cần ngưỡng + duyệt hai người |
| `NEGATIVE_RESULT_BALANCE` | 400 | Điều chỉnh làm `balance < 0` | `CHECK` chặn |
| `DUPLICATE_ADJUSTMENT` | 409 | Xử lý một khiếu nại hai lần | Unique trên `dispute_ref` |
| `NO_DISPUTE_RECORD` | — | ⚠️ Chưa có bảng lưu khiếu nại | Xem ghi chú |

> 🔴 **`LEDGER_ROW_MODIFIED` vi phạm luật cốt lõi.** Cách sai để "sửa" số dư: `UPDATE
> credit_transactions SET amount = ...` hoặc xoá dòng sai.
> Cách đúng: **thêm** dòng `ADJUSTMENT`. Sổ cái là append-only.
> **Cách chặn cứng:** không có endpoint `PUT`/`DELETE` nào cho `credit_transactions`; và nếu lo
> hơn thì thu hồi quyền `UPDATE`/`DELETE` trên bảng đó ở tầng DB (`REVOKE`).

> 🔴 **`SELF_ADJUSTMENT` — separation of duties ở mức cao nhất.** `FINANCE_ADMIN` cộng 10.000
> điểm cho tài khoản `USER` của chính mình là tự in tiền, và `ADJUSTMENT` có lý do hợp lý thì
> khó phát hiện.
> **Cần:** chặn `performed_by = target_user_id` ở code. Nhưng người đó dùng tài khoản khác thì
> không chặn được — cùng vấn đề `SELF_GENERATED_CARD` (UC-094).
> **Giảm bằng:** audit log + `SUPER_ADMIN` xem được báo cáo "điều chỉnh trong kỳ" và số tiền.
> Với đồ án, ghi nhận giới hạn là đủ; nhưng đây **chính là** câu thầy có thể hỏi về separation
> of duties.

> 🔴 **`ADJUSTMENT_WITHOUT_REASON` + `ADJUSTMENT_WITHOUT_AUDIT`:** điều chỉnh không lý do là
> không phân biệt được với gian lận. Mọi dòng `ADJUSTMENT` phải trả lời được: ai duyệt, vì sao,
> khiếu nại nào.

> ⚠️ **`NO_DISPUTE_RECORD`:** chưa có bảng nào lưu **khiếu nại**. Hiện `dispute_ref` chỉ là một
> chuỗi tự do.
> **Cần chốt:** có bảng `disputes` không, hay ghi ngoài hệ thống (Zalo/email)? Với đồ án thì ghi
> ngoài chấp nhận được, nhưng `reason` phải có mã tham chiếu để tra lại.

## Business rule

| # | Rule |
| --- | --- |
| BR-102-1 | UC này chỉ triển khai khi hệ thống có điểm/giao dịch thật. Không gen code nếu MVP chỉ dùng quota đơn giản. |
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

`credit_transactions` (append) · `user_credits` (ghi) · `credit_cards` (đọc) · audit log

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Điều chỉnh +50 kèm lý do | Dòng `ADJUSTMENT`, `balance` +50 |
| T2 | Không có lý do | 400 |
| T3 | `CONTENT_ADMIN` gọi | 403 |
| T4 | Cố sửa dòng sổ cái | **Không có** endpoint |
| T5 | Tự điều chỉnh cho mình | 403 |
| T6 | Cùng `dispute_ref` 2 lần | 409 |
| T7 | Điều chỉnh làm `balance < 0` | 400 |
| T8 | Sau khi điều chỉnh | Audit log có `performed_by` + lý do |

---

# UC-103 · Gửi yêu cầu nhờ chấm bài viết

| | |
|---|---|
| **UC-ID** | UC-103 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Người học làm bài viết → chọn "nhờ chấm" → **trừ điểm** → vào hàng đợi `TEACHER`.

Nghiệm thu 6.2: *"Trừ điểm **đúng lúc gửi yêu cầu**; teacher không nhận thì **hoàn điểm**"*.

## Tiền điều kiện

1. `USER` đã đăng nhập; CSRF
2. Có bài viết (từ UC-035 câu `ESSAY` hoặc tự viết)
3. Còn lượt hoặc điểm — tính năng tốn phí thứ 4

## Hậu điều kiện

| Kết quả | Trạng thái (cùng transaction) |
| --- | --- |
| Thành công | `grading_requests` `status = PENDING`; điểm bị trừ; `credit_transactions` ghi `DEDUCT` |
| Hết quota | 402, **không** tạo yêu cầu, **không** trừ |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Viết bài, bấm "Nhờ giáo viên chấm" |
| 2 | Client | `POST /api/grading-requests` + CSRF |
| 3 | System | Kiểm độ dài bài viết |
| 4 | System | Kiểm chưa có yêu cầu đang chờ cho bài này |
| 5 | System | **Mở transaction** |
| 6 | System | `quotaService.consume(...)` — UC-096 |
| 7 | System | `INSERT grading_requests` — `PENDING`, `deadline_at = now() + N giờ` |
| 8 | System | **Commit** |
| 9 | System | Thông báo hàng đợi `TEACHER` |
| 10 | Client | Hiện "đã gửi, dự kiến có kết quả trong N giờ" |

## Luồng thay thế

**A1 — Hết quota** — 402, gợi ý nạp. Không tạo yêu cầu.
**A2 — Huỷ yêu cầu trước khi teacher nhận** — cho phép, **hoàn điểm** (UC-097).
**A3 — Bài quá ngắn** — 400. Chấm bài 5 từ là tốn công teacher vô ích.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `QUOTA_EXCEEDED` | 402 | Hết lượt và điểm | Không tạo yêu cầu, không trừ |
| `QUOTA_DEDUCTED_BUT_REQUEST_FAILED` | 500 | 🔴 Trừ xong tạo yêu cầu lỗi | **Cùng transaction** — rollback cả hai |
| `ESSAY_TOO_SHORT` | 400 | < 50 từ | Chặn (A3) |
| `ESSAY_TOO_LONG` | 400 | > 2.000 từ | Chặn — công chấm quá lớn |
| `DUPLICATE_PENDING_REQUEST` | 409 | Đã có yêu cầu chờ cho bài này | Chặn trừ tiền hai lần |
| `TEACHER_PAYOUT_UNDEFINED` | 500 | 🔴 Chưa chốt teacher nhận bao nhiêu | Xem ghi chú |
| `DEADLINE_UNDEFINED` | 500 | 🔴 Chưa chốt hạn phải chấm | Xem ghi chú |
| `XSS_IN_ESSAY` | — | Script trong bài | Escape — `TEACHER` sẽ đọc bài này |
| `CSRF_TOKEN_MISSING` | 403 | Thiếu CSRF | Chặn — thao tác đổi tiền |
| `PII_IN_ESSAY` | — | Bài viết chứa thông tin cá nhân | `TEACHER` đọc được; cần điều khoản rõ |

> 🔴 **`TEACHER_PAYOUT_UNDEFINED` + `DEADLINE_UNDEFINED` — feature tree tự đặt hai câu hỏi này
> và chưa ai trả lời:** *"**Cần quyết:** Teacher nhận bao nhiêu phần trong số điểm người học
> trả? Có hạn thời gian phải chấm xong không?"*
>
> Cả hai chặn việc làm tính năng:
> — Không biết `teacher_payout` thì không tính được tiền trả teacher, và `credit_transactions`
> thiếu một loại giao dịch
> — Không có hạn thì UC-107 (hoàn điểm khi teacher không nhận) **không có mốc** để chạy
>
> **Khuyến nghị:** teacher nhận 70%, hạn nhận 24 giờ, hạn chấm 48 giờ sau khi nhận. Nhưng đây
> là quyết định của nhóm, không phải của tôi.

> ⚠️ **`XSS_IN_ESSAY` đặc biệt đáng chú ý vì `TEACHER` là người đọc.** Cùng rủi ro
> `XSS_IN_CONTENT` (UC-069) nhưng đối tượng là người có quyền cao hơn.

## Business rule

| # | Rule |
| --- | --- |
| BR-103-1 | UC này bị chặn cho đến khi nhóm chốt `teacher_payout`, hạn teacher nhận bài và hạn teacher chấm xong. |
| BR-103-2 | Không gen code MVP cho chấm bài thuê nếu dự án chưa chốt thanh toán/điểm. |
| BR-103-3 | Người dùng chỉ gửi yêu cầu chấm khi đã đăng nhập và còn quota hoặc điểm theo chính sách đã chốt. |
| BR-103-4 | Điểm hoặc quota bị trừ tại thời điểm gửi yêu cầu, đúng theo nghiệm thu của file. |
| BR-103-5 | Trừ điểm/quota và tạo yêu cầu chấm phải nhất quán. Không được trừ thành công nhưng không tạo yêu cầu. |
| BR-103-6 | Một bài viết chỉ được có một yêu cầu chấm đang chờ hoặc đang được xử lý. |
| BR-103-7 | Bài gửi chấm phải có độ dài hợp lý. Đề xuất 50–2.000 từ để tránh bài quá ngắn hoặc quá dài. |
| BR-103-8 | Người dùng được hủy yêu cầu khi yêu cầu còn ở trạng thái chờ teacher nhận. Khi hủy hợp lệ, hệ thống hoàn lại phần đã trừ. |
| BR-103-9 | Nội dung bài viết phải được escape khi teacher xem để tránh XSS. |
| BR-103-10 | Người dùng cần được thông báo rõ điều kiện hoàn điểm trước khi xác nhận gửi chấm. |

## API · DB

```
POST   /api/grading-requests
DELETE /api/grading-requests/{id}        (huỷ khi còn PENDING)
GET    /api/me/grading-requests
```

`grading_requests` (ghi) · `feature_usage` · `user_credits` · `credit_transactions`

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Gửi bài 200 từ, còn quota | 201, điểm **bị trừ ngay** |
| T2 | Hết quota | 402, **không** tạo yêu cầu |
| T3 | Tạo yêu cầu lỗi sau khi trừ | **Rollback** — điểm không mất |
| T4 | Bài 20 từ | 400 |
| T5 | Gửi lại cùng bài khi còn `PENDING` | 409 |
| T6 | Huỷ khi còn `PENDING` | Điểm **được hoàn** |
| T7 | Thiếu CSRF | 403 |
| T8 | Bài có `<script>` | `TEACHER` thấy text |

---

# UC-104 · Xem hàng đợi bài chờ chấm

| | |
|---|---|
| **UC-ID** | UC-104 · **Actor** `TEACHER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

`TEACHER` xem danh sách bài `PENDING`, chọn bài để nhận chấm.

## Tiền điều kiện

Role `TEACHER`.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `TEACHER` | Mở hàng đợi chấm bài |
| 2 | System | Kiểm role `TEACHER` |
| 3 | System | `GET /api/teacher/grading-requests?status=PENDING` |
| 4 | System | Sắp theo `deadline_at` gần nhất trước |
| 5 | System | Trả: độ dài bài, thời gian gửi, hạn, **tiền nhận được** |
| 6 | System | **Che danh tính người học** (xem exception) |
| 7 | `TEACHER` | Chọn bài → UC-105 |

## Luồng thay thế

**A1 — Hàng đợi rỗng** — hiện "không có bài chờ chấm".
**A2 — Xem bài mình đang chấm** — `?status=ASSIGNED&mine=true`.
**A3 — Lọc theo độ dài** — chọn bài ngắn để chấm nhanh.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN_ROLE` | 403 | Không phải `TEACHER` | Chặn ở server |
| `STUDENT_IDENTITY_EXPOSED` | — | 🔴 Hiện tên/email người học | Xem ghi chú |
| `ESSAY_CONTENT_IN_LIST` | — | 🔴 Trả nguyên nội dung bài trong danh sách | Xem ghi chú |
| `ASSIGNED_REQUEST_IN_QUEUE` | — | Bài đã có người nhận còn trong hàng đợi | Lọc `status = PENDING` |
| `EXPIRED_REQUEST_IN_QUEUE` | — | Bài quá hạn còn hiện | UC-107 phải dọn |
| `PAYOUT_NOT_SHOWN` | — | Không hiện tiền nhận | `TEACHER` không biết có đáng chấm không |
| `NO_PAGINATION` | — | Trả hết | Phân trang |

> 🔴 **`STUDENT_IDENTITY_EXPOSED` — chấm ẩn danh là quyết định nên chốt.** Nếu `TEACHER` thấy
> tên người học:
> — Có thể chấm ưu ái người quen (`TEACHER` là thầy hướng dẫn thật)
> — Và người học biết mình bị ai chấm thì có thể áp lực
> **Khuyến nghị:** hàng đợi ẩn danh (chỉ `request_id`), hiện danh tính **sau khi** chấm xong nếu
> cần. Đây là điểm cộng nhỏ khi bảo vệ về thiết kế công bằng.

> 🔴 **`ESSAY_CONTENT_IN_LIST`:** trả nguyên bài của 50 yêu cầu trong một response là vừa chậm
> vừa lộ nhiều dữ liệu hơn cần thiết. Danh sách chỉ cần metadata; nội dung đọc ở UC-105 sau khi
> **đã nhận** bài — và lần đó ghi được ai đã đọc.

## Business rule

| # | Rule |
| --- | --- |
| BR-104-1 | UC này bị chặn cho đến khi nhóm chốt `teacher_payout` và deadline. |
| BR-104-2 | Chỉ `TEACHER` được xem hàng đợi bài chờ chấm. |
| BR-104-3 | Hàng đợi chỉ hiển thị các yêu cầu ở trạng thái `PENDING` và chưa quá hạn. |
| BR-104-4 | Danh sách hàng đợi không hiển thị danh tính người học để giảm thiên vị khi chọn bài. |
| BR-104-5 | Danh sách hàng đợi không trả toàn bộ nội dung bài viết. Chỉ trả metadata như độ dài, thời gian gửi, hạn xử lý và mức payout. |
| BR-104-6 | Hàng đợi phải phân trang. |
| BR-104-7 | Danh sách nên sắp xếp theo hạn xử lý gần nhất trước. |
| BR-104-8 | Bài đã có teacher nhận không được tiếp tục xuất hiện trong hàng đợi chung. |

## API · DB

```
GET /api/teacher/grading-requests?status=PENDING
```

`grading_requests` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `TEACHER` xem hàng đợi | Danh sách sắp theo hạn |
| T2 | `USER` gọi | 403 |
| T3 | `CONTENT_ADMIN` gọi | 403 |
| T4 | Kiểm response | **Không** có tên/email người học |
| T5 | Kiểm response | **Không** có nội dung bài |
| T6 | Bài đã có người nhận | Không trong hàng đợi |

---

# UC-105 · Nhận và chấm bài viết

| | |
|---|---|
| **UC-ID** | UC-105 · **Actor** `TEACHER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

`TEACHER` nhận bài, đọc, cho điểm + nhận xét + sửa lỗi, và **nhận tiền** (`teacher_payout`).

Kết quả chấm gộp vào cùng bảng `grading_requests`: `score` · `feedback` · `corrections` ·
`teacher_payout`.

## Tiền điều kiện

1. Role `TEACHER`; CSRF
2. Yêu cầu `status = PENDING`, chưa quá hạn
3. Chưa có `TEACHER` khác nhận

## Hậu điều kiện

| Bước | Trạng thái |
| --- | --- |
| Nhận bài | `status = ASSIGNED`, `teacher_id`, `assigned_at`, `grading_deadline` |
| Chấm xong | `status = COMPLETED`, `score`, `feedback`, `corrections`, `teacher_payout`; `credit_transactions` ghi tiền trả teacher |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `TEACHER` | Bấm "Nhận chấm" |
| 2 | Client | `PATCH /api/teacher/grading-requests/{id}/claim` + CSRF |
| 3 | System | `UPDATE ... SET status='ASSIGNED', teacher_id=? WHERE id=? AND status='PENDING'` |
| 4 | System | 0 dòng → 409 (người khác nhận trước) |
| 5 | System | Trả nội dung bài **đầy đủ** |
| 6 | `TEACHER` | Đọc, cho điểm, viết nhận xét, sửa lỗi |
| 7 | Client | `PATCH .../{id}/submit-grade` — `{score, feedback, corrections}` |
| 8 | System | Kiểm `teacher_id = currentUser` |
| 9 | System | Validate `score` trong thang điểm |
| 10 | System | **Transaction:** ghi kết quả + tính `teacher_payout` + ghi `credit_transactions` |
| 11 | System | `status = COMPLETED` |
| 12 | System | Thông báo người học (UC-106) |

## Luồng thay thế

**A1 — Hai `TEACHER` cùng nhận** — bước 3 chỉ một thành công (`WHERE status='PENDING'`).
**A2 — Nhận rồi không chấm** — quá `grading_deadline` → về `PENDING` cho người khác, hoặc hoàn điểm người học (UC-107).
**A3 — Trả bài không chấm** — cho phép, về `PENDING`, **không** nhận tiền.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `FORBIDDEN_ROLE` | 403 | Không phải `TEACHER` | Chặn |
| `ALREADY_CLAIMED` | 409 | Người khác nhận trước | `WHERE status='PENDING'` (A1) |
| `NOT_ASSIGNED_TO_ME` | 403 | 🔴 Chấm bài người khác nhận | Xem ghi chú |
| `PAYOUT_WITHOUT_LEDGER` | — | 🔴 Trả tiền teacher không ghi sổ cái | Xem ghi chú |
| `PAYOUT_EXCEEDS_STUDENT_PAYMENT` | 400 | 🔴 `teacher_payout` > tiền người học trả | Xem ghi chú |
| `TEACHER_PAYOUT_UNDEFINED` | 500 | ⚠️ Chưa chốt tỉ lệ | Chặn — UC-103 đã nêu |
| `INVALID_SCORE` | 400 | Điểm ngoài thang | Chặn |
| `EMPTY_FEEDBACK` | 400 | Không nhận xét | Chặn — người học trả tiền cho nhận xét |
| `GRADING_DEADLINE_MISSED` | 422 | Quá hạn chấm | Về `PENDING` (A2) |
| `DOUBLE_PAYOUT` | — | 🔴 Nộp điểm 2 lần → trả tiền 2 lần | `WHERE status='ASSIGNED'` |
| `XSS_IN_FEEDBACK` | — | Script trong nhận xét | Escape — người học đọc |
| `SELF_GRADING` | 403 | `TEACHER` chấm bài của chính mình | Separation of duties |

> 🔴 **`NOT_ASSIGNED_TO_ME` + `DOUBLE_PAYOUT` — hai lỗ hổng tiền ở cùng một endpoint.**
> — Không kiểm `teacher_id` thì `TEACHER` A chấm bài `TEACHER` B nhận, và **A nhận tiền**
> — Không kiểm `status` thì nộp điểm 10 lần = nhận tiền 10 lần
> **Cách chặn cả hai bằng một câu SQL:**
> `UPDATE grading_requests SET ... WHERE id=? AND teacher_id=? AND status='ASSIGNED'`
> Trả 0 dòng thì lỗi. Cùng mẫu với `ATTEMPT_ALREADY_SUBMITTED` (UC-035) và `CONCURRENT_REVIEW`
> (UC-076) — **lần thứ ba** mẫu này xuất hiện.

> 🔴 **`PAYOUT_WITHOUT_LEDGER`:** tiền trả teacher là một dòng tiền thật. Nếu chỉ ghi
> `grading_requests.teacher_payout` mà không vào `credit_transactions` thì:
> — UC-100 (sổ cái) không thấy khoản chi này
> — Đối chiếu `LEDGER_TOTALS_MISMATCH` (UC-100) sẽ lệch
> **Cần:** một loại giao dịch `TEACHER_PAYOUT` trong `credit_transactions`.

> 🔴 **`PAYOUT_EXCEEDS_STUDENT_PAYMENT`:** nếu `teacher_payout` tính sai (ví dụ 150% thay vì 70%)
> thì hệ thống **lỗ** mỗi bài chấm. `CHECK` được: `teacher_payout ≤ student_paid`.

## Business rule

| # | Rule |
| --- | --- |
| BR-105-1 | UC này bị chặn cho đến khi nhóm chốt tỉ lệ hoặc số điểm `teacher_payout`. |
| BR-105-2 | Chỉ `TEACHER` được nhận và chấm bài. |
| BR-105-3 | Một yêu cầu chấm chỉ được một teacher nhận tại một thời điểm. |
| BR-105-4 | Teacher chỉ được chấm bài mà chính mình đã nhận. |
| BR-105-5 | Teacher không được tự chấm bài của chính mình. |
| BR-105-6 | Khi teacher nhận bài, yêu cầu chuyển từ `PENDING` sang `ASSIGNED` và ghi `teacher_id`, `assigned_at`, hạn chấm. |
| BR-105-7 | Khi teacher nộp kết quả, hệ thống chỉ chấp nhận nếu yêu cầu còn ở trạng thái `ASSIGNED` và thuộc teacher hiện tại. |
| BR-105-8 | Mỗi yêu cầu chỉ được hoàn tất một lần. Nộp kết quả nhiều lần không được trả payout nhiều lần. |
| BR-105-9 | Kết quả chấm phải có điểm hợp lệ và nhận xét không rỗng. |
| BR-105-10 | Payout cho teacher không được lớn hơn phần người học đã trả cho yêu cầu đó. |
| BR-105-11 | Payout cho teacher phải được ghi nhận bằng giao dịch hoặc lịch sử riêng để đối soát. |
| BR-105-12 | Nếu quá hạn chấm, teacher không được nhận payout cho yêu cầu đó. |
| BR-105-13 | Nhận xét và phần sửa lỗi phải được escape khi hiển thị cho người học. |

## API · DB

```
PATCH /api/teacher/grading-requests/{id}/claim
PATCH /api/teacher/grading-requests/{id}/submit-grade
PATCH /api/teacher/grading-requests/{id}/release
```

`grading_requests` (đọc + ghi) · `credit_transactions` (ghi) · `user_credits` (ghi teacher)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nhận + chấm bài | `COMPLETED`, teacher nhận tiền, sổ cái có dòng |
| T2 | Hai `TEACHER` cùng nhận | Một 200, một 409 |
| T3 | Chấm bài người khác nhận | 403 |
| T4 | Nộp điểm 2 lần | Trả tiền **một** lần |
| T5 | Nhận xét rỗng | 400 |
| T6 | `USER` gọi | 403 |
| T7 | `payout` > tiền người học trả | **DB chặn** (CHECK) |
| T8 | Quá hạn chấm | Về `PENDING`, không nhận tiền |

---

# UC-106 · Xem kết quả chấm và nhận xét

| | |
|---|---|
| **UC-ID** | UC-106 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Người học xem điểm, nhận xét, và các chỗ được sửa (`corrections`).

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `grading_requests` `status = COMPLETED`, **thuộc người đang đăng nhập**

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nhận thông báo, mở kết quả |
| 2 | Client | `GET /api/me/grading-requests/{id}` |
| 3 | System | **Kiểm sở hữu** |
| 4 | System | Kiểm `status = COMPLETED` |
| 5 | System | Trả `score`, `feedback`, `corrections`, bài gốc |
| 6 | Client | Hiện bài gốc kèm sửa lỗi nổi bật |

## Luồng thay thế

**A1 — Còn `PENDING`/`ASSIGNED`** — hiện trạng thái + dự kiến, **không** trả `score`.
**A2 — Khiếu nại kết quả chấm** — ⚠️ không có luồng khiếu nại. Xem exception.
**A3 — Xem lại bài cũ** — cùng endpoint; giữ vĩnh viễn.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `REQUEST_NOT_OWNED` | 403 | 🔴 Xem bài người khác | **IDOR** |
| `NOT_COMPLETED` | 200 | Chưa chấm xong | Trả trạng thái, **không** trả `score` (A1) |
| `TEACHER_IDENTITY_EXPOSED` | — | ⚠️ Hiện danh tính teacher | Xem ghi chú |
| `PAYOUT_EXPOSED_TO_STUDENT` | — | 🔴 Hiện teacher nhận bao nhiêu | Xem ghi chú |
| `XSS_IN_FEEDBACK` | — | Script trong nhận xét | Escape |
| `NO_GRADING_APPEAL` | — | ⚠️ Không có luồng khiếu nại | Xem ghi chú |
| `CORRECTIONS_MALFORMED` | 500 | `corrections` JSONB sai cấu trúc | Hiện nhận xét, bỏ phần sửa lỗi |

> 🔴 **`PAYOUT_EXPOSED_TO_STUDENT`:** người học trả 10 điểm, teacher nhận 7. Nếu response có
> `teacher_payout` thì người học biết hệ thống giữ 30% → dễ thành tranh luận không cần thiết, và
> đó là thông tin kinh doanh nội bộ.
> **Cần:** DTO riêng cho người học, **không** chứa `teacher_payout`. Cùng mẫu lỗi
> `ANSWER_KEY_IN_RESPONSE` — trả entity thẳng là lộ trường không nên lộ.

> ⚠️ **`NO_GRADING_APPEAL`:** người học trả tiền, nhận điểm 4/10 và nhận xét ngắn — không có
> cách nào khiếu nại.
> **Cần chốt:** có luồng khiếu nại chấm bài không? Nếu không thì phải ghi rõ trong điều khoản
> trước khi trừ tiền (UC-103). Đây là câu hỏi công bằng mà hội đồng có thể hỏi.

> ⚠️ **`TEACHER_IDENTITY_EXPOSED`:** ngược với UC-104 (ẩn danh người học). Hiện tên teacher giúp
> người học tin kết quả, nhưng cũng mở đường liên lạc ngoài hệ thống.
> **Đề xuất:** hiện tên teacher — người học trả tiền thì nên biết ai chấm.

## Business rule

| # | Rule |
| --- | --- |
| BR-106-1 | Người học chỉ được xem kết quả chấm của yêu cầu do chính mình tạo. |
| BR-106-2 | Chỉ yêu cầu ở trạng thái `COMPLETED` mới trả điểm, nhận xét và phần sửa lỗi. |
| BR-106-3 | Yêu cầu chưa chấm xong chỉ trả trạng thái xử lý, không trả điểm rỗng hoặc kết quả tạm. |
| BR-106-4 | Response cho người học không được chứa `teacher_payout` hoặc thông tin tài chính nội bộ. |
| BR-106-5 | Feedback và corrections phải được escape khi hiển thị. |
| BR-106-6 | Có thể hiển thị tên teacher cho người học nếu nhóm chốt rằng kết quả chấm không ẩn danh. |
| BR-106-7 | Kết quả chấm nên được giữ lại để người học xem lại sau này. |
| BR-106-8 | Trước khi triển khai trả phí, nhóm phải chốt rõ có hay không có luồng khiếu nại kết quả chấm. |

## API · DB

```
GET /api/me/grading-requests
GET /api/me/grading-requests/{id}
```

`grading_requests` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xem kết quả của mình | Điểm + nhận xét + sửa lỗi |
| T2 | Yêu cầu người khác | 403 |
| T3 | Còn `PENDING` | Trạng thái, **không** có `score` |
| T4 | Kiểm response | **Không** chứa `teacher_payout` |
| T5 | Nhận xét có `<script>` | Render ra text |

---

# UC-107 · Hoàn điểm khi teacher không nhận trong hạn

| | |
|---|---|
| **UC-ID** | UC-107 · **Actor** `SYSTEM` · **Pri** P2 · **Scope** MVP · **FT** 6.2 |

## Mô tả

Tác vụ định kỳ: yêu cầu `PENDING` quá hạn mà không `TEACHER` nào nhận → **hoàn điểm** người học.

Nghiệm thu 6.2: *"teacher không nhận thì **hoàn điểm**"*.

## Tiền điều kiện

1. Scheduler chạy với `zone = "Asia/Ho_Chi_Minh"`
2. Có `grading_requests` `PENDING` với `deadline_at < now()`
3. `deadline_at` đã được chốt (⚠️ UC-103)

## Hậu điều kiện

| Kết quả | Trạng thái (cùng transaction) |
| --- | --- |
| Hoàn | `status = EXPIRED`; điểm hoàn qua UC-097; `credit_transactions` có dòng `REFUND` |
| Đã hoàn | Idempotent |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Scheduler | Chạy mỗi 15 phút |
| 2 | System | `SELECT ... WHERE status='PENDING' AND deadline_at < now() FOR UPDATE SKIP LOCKED` |
| 3 | System | Với **mỗi** yêu cầu, một transaction riêng |
| 4 | System | `UPDATE ... SET status='EXPIRED' WHERE id=? AND status='PENDING'` |
| 5 | System | 0 dòng → bỏ qua (vừa có người nhận) |
| 6 | System | Gọi UC-097 hoàn điểm, `reason = TEACHER_TIMEOUT` |
| 7 | System | **Commit** |
| 8 | System | Thông báo người học đã hoàn điểm |

## Luồng thay thế

**A1 — Teacher nhận đúng lúc job chạy** — bước 4 trả 0 dòng, bỏ qua. Không hoàn oan.
**A2 — Quá hạn **chấm** (đã nhận nhưng không chấm)** — về `PENDING` (UC-105 A2), lần sau job này xử lý nếu vẫn không ai nhận.
**A3 — Hoàn thất bại** — rollback, lần chạy sau thử lại.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `DEADLINE_UNDEFINED` | 500 | 🔴 Chưa chốt hạn | **Chặn** — không có mốc thì job không chạy được |
| `REFUND_RACE_WITH_CLAIM` | — | 🔴 Hoàn cùng lúc teacher nhận | Xem ghi chú |
| `DOUBLE_REFUND` | — | Hoàn hai lần | UC-097 idempotent + unique `ref_transaction_id` |
| `WRONG_TIMEZONE` | — | 🔴 Scheduler không ghi `zone` | Hoàn sớm/muộn 7 tiếng |
| `SCHEDULER_NOT_RUNNING` | — | 🔴 Job không chạy | Xem ghi chú |
| `BATCH_TRANSACTION_TOO_LARGE` | — | Hoàn 500 yêu cầu trong một transaction | Mỗi yêu cầu một transaction (bước 3) |
| `DUPLICATE_SCHEDULER_INSTANCE` | — | Nhiều instance | `SKIP LOCKED` + `WHERE status='PENDING'` |
| `REFUND_WITHOUT_STATUS_CHANGE` | — | 🔴 Hoàn mà `status` vẫn `PENDING` | Teacher vẫn nhận được → mất tiền hai đầu |

> 🔴 **`REFUND_RACE_WITH_CLAIM` — kịch bản mất tiền hai đầu.** Job thấy yêu cầu quá hạn, bắt đầu
> hoàn. Cùng lúc `TEACHER` bấm "Nhận chấm". Nếu cả hai thành công:
> — Người học **được hoàn điểm**
> — `TEACHER` chấm bài và **nhận tiền**
> → Hệ thống trả tiền cho teacher mà không thu của ai.
> **Cách chặn:** cả hai đường đều dùng `UPDATE ... WHERE status='PENDING'`. Ai ghi trước thắng,
> người sau nhận 0 dòng và dừng. **Lần thứ tư** mẫu này xuất hiện (UC-035, UC-076, UC-105, đây).

> 🔴 **`REFUND_WITHOUT_STATUS_CHANGE`** là dạng nhẹ hơn của trên: hoàn điểm nhưng quên đổi
> `status` → yêu cầu vẫn trong hàng đợi UC-104 → teacher nhận và chấm → mất tiền.
> Vì vậy bước 4 (đổi status) phải **trước** bước 6 (hoàn), và cùng transaction.

> 🔴 **`SCHEDULER_NOT_RUNNING`** — cùng vấn đề UC-055. Ở đây hậu quả là **tiền**: người học mất
> điểm, không được hoàn, và không ai biết. Cần health check + báo cáo "số yêu cầu `PENDING` quá
> hạn" — phải bằng 0.

## Business rule

| # | Rule |
| --- | --- |
| BR-107-1 | UC này bị chặn cho đến khi nhóm chốt hạn teacher phải nhận bài. Không có deadline thì không thể chạy hoàn tự động. |
| BR-107-2 | Hệ thống chỉ hoàn điểm cho yêu cầu còn ở trạng thái `PENDING` và đã quá hạn nhận bài. |
| BR-107-3 | Trước khi hoàn, hệ thống phải chuyển yêu cầu sang trạng thái hết hạn để teacher không thể nhận bài sau khi người học đã được hoàn. |
| BR-107-4 | Việc đổi trạng thái và hoàn điểm phải nhất quán. Không được hoàn điểm nhưng yêu cầu vẫn còn trong hàng đợi. |
| BR-107-5 | Hoàn điểm phải dùng luồng hoàn điểm chuẩn, không dùng điều chỉnh thủ công. |
| BR-107-6 | Mỗi yêu cầu chỉ được hoàn một lần. Job chạy lại không được hoàn trùng. |
| BR-107-7 | Nếu teacher nhận bài đúng lúc job hoàn điểm chạy, chỉ một trong hai thao tác được thành công. |
| BR-107-8 | Scheduler hoàn điểm phải có health check hoặc báo cáo số yêu cầu quá hạn chưa xử lý. |
| BR-107-9 | Giờ xử lý deadline thống nhất theo giờ Việt Nam hoặc timestamp chuẩn đã chốt, không để mỗi nơi hiểu một kiểu. |

## API · DB

Không endpoint — tác vụ định kỳ.

`grading_requests` (đọc + ghi) · `credit_transactions` · `user_credits` (qua UC-097)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Yêu cầu quá hạn 1 giờ | `EXPIRED`, điểm **được hoàn** |
| T2 | Teacher nhận đúng lúc job chạy | **Một** trong hai thắng, không mất tiền hai đầu |
| T3 | Job chạy 2 lần | Hoàn **một** lần |
| T4 | Hai instance cùng chạy | Mỗi yêu cầu hoàn một lần |
| T5 | Hoàn lỗi | `status` **không** đổi, lần sau thử lại |
| T6 | Sau khi hoàn | `status = EXPIRED`, **không** còn trong hàng đợi |
| T7 | Báo cáo | 0 yêu cầu `PENDING` quá hạn |

---

# Tổng hợp exception nhóm 6a + 6b

## Mười hai exception quan trọng nhất

| # | UC | Exception | Vì sao |
| --- | --- | --- | --- |
| 1 | UC-094 | `BALANCE_UPDATED_WITHOUT_LEDGER` | Phá vỡ **toàn bộ** khả năng xử lý tranh chấp; sổ cái không tái dựng được |
| 2 | UC-097 | `REFUND_NOT_TRIGGERED` | Đường lỗi ít được test → nhiều người mất điểm mà **không ai biết** |
| 3 | UC-099 | `WEAK_RANDOM_USED` / `SEQUENTIAL_CODES` | Rủi ro #1 bảng bảo mật 6.1 — đoán được mã là mất tiền thật |
| 4 | UC-098 | `OTHER_USER_TRANSACTIONS_LEAKED` | Thiếu một `WHERE` là lộ **toàn bộ sổ cái tài chính** |
| 5 | UC-107 | `REFUND_RACE_WITH_CLAIM` | Mất tiền **hai đầu**: hoàn cho người học **và** trả teacher |
| 6 | UC-097 | `REFUND_WITHOUT_DEDUCT` | Đường **tự in điểm** nếu `amount` nhận từ tham số |
| 7 | UC-094 | `CARD_CODE_IN_LOG` | Ba đường lọt (log body, message lỗi, DEBUG); quy tắc 5 nói "kể cả log lỗi" |
| 8 | UC-096 | `NOT_IN_TRANSACTION` | Trừ điểm ở transaction riêng → người dùng trả tiền cho việc không xảy ra |
| 9 | UC-105 | `NOT_ASSIGNED_TO_ME` + `DOUBLE_PAYOUT` | Hai lỗ hổng tiền ở một endpoint; chặn được bằng một câu SQL |
| 10 | UC-099 | `RAW_CODE_RETRIEVABLE` | Có endpoint xem lại mã = mọi lợi ích của lưu hash biến mất |
| 11 | UC-102 | `LEDGER_ROW_MODIFIED` | Sổ cái phải append-only; sửa dòng là đứt chuỗi số dư |
| 12 | UC-095 | `SUBSCRIPTION_OVERWRITTEN` | Người dùng mất phần gói đã trả tiền, và không phát hiện ngay |

## Năm nhóm exception lặp lại

| Nhóm | Xuất hiện ở | Bài học |
| --- | --- | --- |
| **Đổi tiền không ghi sổ cái** | UC-094 · UC-095 · UC-096 · UC-102 · UC-105 | **Năm** chỗ đổi `balance`. Cần **một** `CreditService` duy nhất có quyền `UPDATE user_credits`, ArchUnit chặn mọi nơi khác. Hoặc trigger DB |
| **`UPDATE ... WHERE status=?`** | UC-105 · UC-107 (+ UC-035, UC-076) | **Bốn** chỗ cùng cần mẫu này: kiểm trạng thái **trong** câu UPDATE, không kiểm trước rồi ghi sau. Một dòng SQL thay cho khoá phân tán |
| **Separation of duties** | UC-094 · UC-099 · UC-100 · UC-101 · UC-102 · UC-105 | `FINANCE_ADMIN` sinh mã rồi tự nạp; tự điều chỉnh cho mình; `TEACHER` tự chấm bài mình. Không chặn hết được bằng code → **audit log là lớp phòng vệ cuối** |
| **Trừ rồi thất bại** | UC-096 · UC-097 · UC-103 (+ UC-048, UC-060, UC-085) | **Sáu** UC. Thứ tự đúng: trừ (commit) → gọi API → lỗi thì hoàn. Không gọi API trong transaction |
| **Lộ trường không nên lộ** | UC-098 (mã thẻ) · UC-100 (PII) · UC-104 (danh tính học viên) · UC-106 (`teacher_payout`) | Trả entity thẳng ra JSON là lộ. **Cùng mẫu** `ANSWER_KEY_IN_RESPONSE` (UC-034) — DTO riêng cho mỗi đối tượng đọc |

---

# Khoảng trống thiết kế phát hiện ở nhóm 6a + 6b

| # | Thiếu | UC bị ảnh hưởng | Mức |
| --- | --- | --- | --- |
| 1 | **Chưa chốt kỳ hạn "10 lượt free"** (vĩnh viễn / tháng / ngày) | UC-093 · UC-096 | 🔴 Chặn UC-096; ảnh hưởng mô hình kinh doanh |
| 2 | **Chưa chốt `teacher_payout`** — teacher nhận bao nhiêu phần | UC-103 · UC-105 | 🔴 Feature tree tự đặt câu hỏi này |
| 3 | **Chưa chốt hạn phải chấm / hạn nhận bài** | UC-103 · UC-105 · UC-107 | 🔴 UC-107 **không có mốc** để chạy |
| 4 | Chưa có cột `ref_transaction_id` + unique index cho `REFUND` | UC-097 | 🔴 Không chống được hoàn hai lần |
| 5 | Chưa có `CHECK (balance >= 0)` | UC-093 · UC-096 | 🔴 Số dư âm |
| 6 | Chưa có `CHECK (teacher_payout <= student_paid)` | UC-105 | 🔴 Hệ thống lỗ mỗi bài |
| 7 | **Chưa có `CreditService` độc quyền** ghi `user_credits` + ArchUnit chặn | UC-094 → UC-105 | 🔴 5 chỗ đổi tiền rời rạc |
| 8 | Chưa có **job đối chiếu** chuỗi `balance_before`/`after` | UC-094 · UC-098 · UC-100 | 🔴 Lệch không ai biết |
| 9 | Chưa có **báo cáo `DEDUCT` thiếu `REFUND`** | UC-097 | 🔴 Người dùng mất điểm âm thầm |
| 10 | Chưa có **audit log** cho thao tác `FINANCE_ADMIN` (kể cả **đọc** sổ cái) | UC-099 → UC-102 | 🔴 Quyết định v2 bắt buộc |
| 11 | `credit_transactions` chưa ghi **giá tại thời điểm** giao dịch | UC-101 | 🔴 Đổi giá làm sổ cái cũ sai nghĩa |
| 12 | Chưa thu hồi quyền `UPDATE`/`DELETE` trên `credit_transactions` ở tầng DB | UC-102 | 🔴 Sổ cái phải append-only |
| 13 | Chưa có ArchUnit chặn `java.util.Random` trong package thanh toán | UC-099 | 🔴 Rủi ro #1 |
| 14 | Chưa có **test đọc log** assert không chứa mã thẻ | UC-094 · UC-099 | 🔴 Quy tắc 5 |
| 15 | Chưa có bảng/cơ chế lưu **khiếu nại** (`disputes`) | UC-102 | ⚠️ `dispute_ref` là chuỗi tự do |
| 16 | Chưa chốt **luồng khiếu nại kết quả chấm bài** | UC-106 | ⚠️ Người học trả tiền, không có đường phản hồi |
| 17 | Chưa chốt **chấm ẩn danh** hay hiện danh tính người học | UC-104 | ⚠️ Công bằng khi chấm |
| 18 | Chưa chốt chính sách **retry trước khi hoàn** | UC-097 | ⚠️ Hoàn ngay hay sau khi hết retry |
| 19 | Chưa có `idempotency_key` cho `consume()` | UC-096 | ⚠️ Trừ hai lần khi retry |
| 20 | Chưa có ngưỡng + duyệt hai người cho `ADJUSTMENT` lớn | UC-102 | ⚠️ Tự in tiền |
| 21 | Chưa có health check scheduler UC-107 | UC-107 | ⚠️ Lỗi im lặng, hậu quả là tiền |
| 22 | ⚠️ **`TODO(PAYMENT_SCOPE)` chặn toàn bộ 15 UC** | UC-093 → UC-107 | 🔴 Tiền thật hay giả lập |

> **Mười bốn mục 🔴** — cao nhất trên mỗi UC trong toàn bộ tài liệu (14/15 UC).
> Ba nhóm:
> — **Quyết định nghiệp vụ còn thiếu** (#1, #2, #3, #22): chặn việc code, cần người quyết
> — **Ràng buộc DB + hạ tầng kiểm soát** (#4 → #8, #11 → #14): thêm được trong migration và test
> — **Báo cáo đối chiếu** (#8, #9): không phải tính năng người dùng thấy, nhưng là cách **duy
> nhất** phát hiện lệch trước khi có khiếu nại
>
> Feature tree nói *"đây là chỗ thầy sẽ hỏi kỹ nhất khi bảo vệ"* — 22 khoảng trống trên là danh
> sách cần trả lời được.

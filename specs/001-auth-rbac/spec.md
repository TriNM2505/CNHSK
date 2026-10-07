# Feature Specification: Xác thực, phân quyền RBAC và gói dịch vụ

**Feature:** `001-auth-rbac`
**Feature tree:** 6.1 (Tài khoản, gói dịch vụ) · 6.2 (Phân quyền — 6 role trong DB)
**Module:** `auth` (+ `plans` thuộc `learning`)
**Use case:** UC-001 → UC-014 (14 UC)
**Scope:** MVP · **Priority:** P0 — mọi feature khác phụ thuộc feature này
**Version:** 1.0 · **Ngày:** 2026-10-05

---

## 1 · Context & Goal

### 1.1 · Business goal feature này phục vụ

Theo `docs/reference/CONTEXT.md` §2:

| Mã | Business goal | Feature này đóng góp gì |
|---|---|---|
| **BG-05** | Demo được trọn luồng học → thi → phân tích → luyện lại | Không đăng nhập được thì không demo được gì. Đây là cửa vào |
| **BG-06** | Giải thích được 6 điểm bảo mật thanh toán khi bảo vệ | 3/6 điểm nằm ở feature này: mật khẩu, CSRF, phân quyền |
| **BG-04** | Nội dung qua kiểm duyệt trước khi tới người học | Role `TEACHER`/`MANAGER` là cơ chế thực thi |

### 1.2 · Vấn đề nghiệp vụ

Hệ thống có **ba client dùng chung một backend**: web `cnhsk.com`, web game
`game.cnhsk.com`, mobile React Native. Ba client này **không** xác thực giống nhau:

- Web dùng **cookie** — trình duyệt tự gửi, nên cần CSRF token
- Mobile dùng **header** — React Native không có cookie như trình duyệt
- Web game dùng **cùng cookie với web chính** — nhờ cookie đặt ở tên miền cha
- Mobile chơi game: WebView trỏ tới `game.cnhsk.com`, app **tiêm token vào header**

Đây là nguồn lỗi lớn nhất của feature: thiếu một nhánh thì **một loại client không
đăng nhập được**, và lỗi chỉ xuất hiện trên thiết bị thật.

### 1.3 · Success metric

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Cả 3 client đăng nhập thành công bằng cùng một tài khoản | 3/3 |
| **SC-002** | Người dùng mở `game.cnhsk.com` sau khi đã đăng nhập ở `cnhsk.com` | không phải đăng nhập lại |
| **SC-003** | Tài khoản chưa xác thực email vẫn học được, nhưng không đổi được điểm | đúng cả hai |
| **SC-004** | Sai mật khẩu 5 lần → khoá tạm; lần 6 trả 429 | 100% |
| **SC-005** | Người dùng A không đọc được dữ liệu của B qua đổi id trong URL | 403 ở mọi endpoint |

### 1.4 · Tech context

| Thành phần | Chốt |
|---|---|
| Backend | Spring Boot 3.5.14 · Java 17 · module `auth` |
| Token | jjwt 0.12.6 · access ngắn hạn + refresh dài hạn |
| Mật khẩu | bcrypt cost ≥ 12 hoặc argon2id (`HR-01`) |
| DB | `users` · `user_roles` · `auth_tokens` · `plans` |
| Frontend | React 18.3 · Vite 5 · TypeScript 5.7 |

---

## 2 · User Stories

### US-1 · Người học mới tạo tài khoản (P1)

**Là** khách chưa có tài khoản, **tôi muốn** đăng ký bằng email,
**để** bắt đầu học và lưu được tiến độ.

**Given** email chưa tồn tại trong hệ thống
**When** tôi nhập họ tên, email, mật khẩu hợp lệ và đồng ý điều khoản
**Then** tài khoản được tạo với role `USER`, gói `FREE`, và tôi nhận email xác thực
**And** tôi đăng nhập được ngay, không cần chờ xác thực email

### US-2 · Người học đăng nhập trên ba client (P1)

**Là** người đã có tài khoản, **tôi muốn** đăng nhập ở web, game, mobile bằng cùng
tài khoản, **để** tiến độ của tôi thống nhất ở mọi nơi.

**Given** tôi đã đăng nhập ở `cnhsk.com`
**When** tôi mở `game.cnhsk.com`
**Then** tôi đã đăng nhập sẵn, không phải nhập lại mật khẩu

**Given** tôi dùng app mobile
**When** tôi đăng nhập
**Then** token trả về trong body response (không phải cookie), app tự lưu

### US-3 · Người học lấy lại tài khoản khi quên mật khẩu (P1)

**Given** tôi quên mật khẩu
**When** tôi nhập email vào form quên mật khẩu
**Then** hệ thống trả thông báo **giống nhau** dù email có tồn tại hay không
**And** nếu email tồn tại, tôi nhận link đặt lại có hạn 1 giờ

### US-4 · Quản trị viên cấp quyền (P2)

**Là** `SUPER_ADMIN`, **tôi muốn** gán role cho người dùng,
**để** giao việc duyệt nội dung cho `TEACHER` và `MANAGER`.

**Given** tôi là `SUPER_ADMIN`
**When** tôi gán role `TEACHER` cho một người dùng
**Then** role được ghi với `granted_by = id của tôi`
**And** tôi **không** gán được role cho chính mình

### US-5 · Người học mua gói Premium (P2)

**Given** tôi đang ở gói `FREE` và đã dùng hết 10 lượt dịch
**When** tôi nhập mã thẻ gói Premium
**Then** `plan_code` của tôi thành `PREMIUM`, `plan_expires_at` = hôm nay + 30 ngày
**And** hạn mức lượt của tôi theo `plans.benefits` của gói `PREMIUM`

### Edge cases

| # | Tình huống | Hành vi mong đợi |
|---|---|---|
| E1 | Hai request đăng ký cùng email đồng thời | Một thành công, một nhận `EMAIL_ALREADY_EXISTS`. `UNIQUE` trên `users.email` chặn |
| E2 | SMTP lỗi khi gửi email xác thực | Đăng ký **vẫn trả 201**. Ghi log. Người dùng xin gửi lại sau (`NFR-A02`) |
| E3 | Refresh token bị dùng lại sau khi đã rotate | Thu hồi **toàn bộ** token của user đó, buộc đăng nhập lại |
| E4 | Token hết hạn giữa lúc đang làm bài thi | Bản nháp đã lưu ở `attempts.draft_answers`, không mất bài |
| E5 | Người dùng bị `SUPER_ADMIN` ban khi đang có session | Request tiếp theo trả 403, không chờ token hết hạn |
| E6 | Mobile mở WebView game nhưng chưa tiêm token | Trang game hiện trạng thái chưa đăng nhập, không crash |
| E7 | Đổi mật khẩu thành công | Thu hồi mọi refresh token **trừ** session hiện tại |
| E8 | Gói Premium hết hạn lúc 00:00 giờ Việt Nam | Job hạ về `FREE`; lượt đang dùng giữa phiên không bị cắt giữa chừng |

---

## 3 · Functional Requirements

> Ký hiệu EARS: **WHEN** (sự kiện) / **WHILE** (trạng thái) / **WHERE** (ngữ cảnh)
> + THE system SHALL. Mỗi FR map ít nhất một test.

### Nhóm 1 — Đăng ký (FR-001…FR-008) · UC-001

| Mã | Yêu cầu |
|---|---|
| **FR-001** | WHEN khách gửi `POST /api/auth/register` với email chưa tồn tại và mật khẩu hợp lệ, THE system SHALL tạo một dòng `users` với `email_verified_at = NULL`, gán role `USER` vào `user_roles`, đặt `plan_code = 'FREE'`, và trả **201** |
| **FR-002** | THE system SHALL thực hiện tạo user + gán role + đặt gói trong **cùng một transaction** (`BUS-02`) |
| **FR-003** | THE system SHALL hash mật khẩu bằng bcrypt cost ≥ 12 hoặc argon2id trước khi ghi (`HR-01`, `NFR-S01`) |
| **FR-004** | WHEN email đã tồn tại, THE system SHALL trả **409** `EMAIL_ALREADY_EXISTS` |
| **FR-005** | THE system SHALL validate: email đúng định dạng, mật khẩu ≥ 8 ký tự, hai lần nhập khớp nhau. Vi phạm → **422** kèm danh sách field |
| **FR-006** | WHEN tạo user thành công, THE system SHALL sinh token `EMAIL_VERIFY` trong `auth_tokens` hết hạn sau **24 giờ** và gửi email |
| **FR-007** | WHERE SMTP lỗi, THE system SHALL **vẫn trả 201** và ghi log lỗi, KHÔNG rollback việc tạo tài khoản (`NFR-A02`) |
| **FR-008** | THE system SHALL KHÔNG bao giờ ghi mật khẩu thô vào log (`NFR-S07`) |

### Nhóm 2 — Xác thực email (FR-009…FR-013) · UC-002

| Mã | Yêu cầu |
|---|---|
| **FR-009** | WHEN người dùng mở link xác thực với token hợp lệ chưa hết hạn, THE system SHALL đặt `email_verified_at = now()` và thu hồi token đó |
| **FR-010** | WHILE `email_verified_at IS NULL`, THE system SHALL cho đăng nhập và cho học, NHƯNG từ chối mọi thao tác đổi điểm với **403** `ACCOUNT_UNVERIFIED` |
| **FR-011** | WHEN token đã dùng hoặc hết hạn, THE system SHALL trả **400** `INVALID_VERIFY_TOKEN` |
| **FR-012** | THE system SHALL cho phép xin gửi lại email xác thực, tối đa **3 lần/giờ** mỗi tài khoản |
| **FR-013** | WHEN gửi lại, THE system SHALL thu hồi token `EMAIL_VERIFY` cũ trước khi sinh token mới |

### Nhóm 3 — Đăng nhập web bằng cookie (FR-014…FR-021) · UC-003

| Mã | Yêu cầu |
|---|---|
| **FR-014** | WHEN đăng nhập đúng từ web, THE system SHALL đặt cookie `access_token` với **đủ năm thuộc tính**: `Domain=cnhsk.com`, `HttpOnly=true`, `Secure=true`, `SameSite=Lax`, `Max-Age` (`HR-03`) |
| **FR-015** | THE system SHALL đặt `Domain=cnhsk.com` (tên miền cha), KHÔNG để trống. Để trống thì `game.cnhsk.com` **không nhận được cookie** và UC-005 hỏng |
| **FR-016** | WHEN đăng nhập đúng, THE system SHALL sinh refresh token, lưu **dạng hash** vào `auth_tokens`, KHÔNG lưu token thô |
| **FR-017** | WHEN mật khẩu sai, THE system SHALL tăng `users.failed_login_count` và trả **401** `INVALID_CREDENTIALS` |
| **FR-018** | THE system SHALL trả **cùng một thông báo** cho email không tồn tại và mật khẩu sai, để không tiết lộ email nào đã đăng ký |
| **FR-019** | WHEN đăng nhập thành công, THE system SHALL đặt lại `failed_login_count = 0` |
| **FR-020** | WHILE `banned_at IS NOT NULL`, THE system SHALL từ chối đăng nhập với **403** `ACCOUNT_BANNED` kèm `ban_reason` |
| **FR-021** | WHILE `suspended_until > now()`, THE system SHALL từ chối đăng nhập với **403** `ACCOUNT_SUSPENDED` kèm thời điểm hết hạn |

### Nhóm 4 — Đăng nhập mobile bằng header (FR-022…FR-025) · UC-004

| Mã | Yêu cầu |
|---|---|
| **FR-022** | WHERE request đến từ mobile (không có cookie), THE system SHALL trả access token và refresh token **trong body response**, KHÔNG đặt cookie |
| **FR-023** | THE system SHALL đọc token theo thứ tự: **cookie trước, không có thì đọc header** `Authorization: Bearer`. Thiếu một trong hai nhánh thì một loại client không đăng nhập được |
| **FR-024** | WHERE client dùng header, THE system SHALL KHÔNG yêu cầu CSRF token (không có cookie thì không có nguy cơ CSRF) |
| **FR-025** | THE system SHALL dùng **cùng một** endpoint `/api/auth/login` cho cả ba client, phân biệt bằng header `X-Client-Type` |

### Nhóm 5 — Đăng nhập trang game bằng cookie chung (FR-026…FR-030) · UC-005, UC-013

| Mã | Yêu cầu |
|---|---|
| **FR-026** | WHERE người dùng đã đăng nhập ở `cnhsk.com` và mở `game.cnhsk.com`, THE system SHALL nhận cookie tự động (nhờ `Domain=cnhsk.com`) và coi là đã đăng nhập |
| **FR-027** | THE system SHALL cung cấp `GET /api/auth/me` để trang game biết mình là ai — vì cookie `HttpOnly` nên JavaScript trang game **không đọc được** token |
| **FR-028** | THE system SHALL khai báo CORS cho `game.cnhsk.com` **tường minh**, CẤM dùng `*` (`HR-04`, `NFR-S05`) |
| **FR-029** | THE system SHALL đặt `Access-Control-Allow-Credentials: true` cho origin trang game, nếu không cookie không được gửi kèm |
| **FR-030** | WHERE mobile mở game trong WebView, THE app SHALL tiêm `Authorization` header **trước khi** trang chạy; THE system SHALL chấp nhận token từ header ở mọi endpoint game |

### Nhóm 6 — Làm mới và đăng xuất (FR-031…FR-037) · UC-006, UC-007

| Mã | Yêu cầu |
|---|---|
| **FR-031** | WHEN nhận refresh token hợp lệ, THE system SHALL cấp access token mới VÀ refresh token mới, rồi thu hồi refresh token cũ (rotation) |
| **FR-032** | WHEN một refresh token đã thu hồi được dùng lại, THE system SHALL thu hồi **toàn bộ** token của user đó và trả **401** `TOKEN_REUSE_DETECTED` |
| **FR-033** | THE system SHALL so sánh refresh token bằng cách hash token người dùng gửi rồi đối chiếu `auth_tokens`, KHÔNG so sánh token thô |
| **FR-034** | WHEN đăng xuất từ web, THE system SHALL xoá cookie (đặt `Max-Age=0`) và thu hồi refresh token của session đó |
| **FR-035** | WHEN đăng xuất từ mobile, THE system SHALL thu hồi refresh token nhận trong body |
| **FR-036** | THE system SHALL cho phép đăng xuất **mọi thiết bị** — thu hồi tất cả refresh token của user |
| **FR-037** | WHERE token hết hạn giữa lúc làm bài thi, THE system SHALL giữ nguyên `attempts.draft_answers` để không mất bài (E4) |

### Nhóm 7 — Quên và đổi mật khẩu (FR-038…FR-045) · UC-008, UC-009, UC-010

| Mã | Yêu cầu |
|---|---|
| **FR-038** | WHEN nhận yêu cầu quên mật khẩu, THE system SHALL trả **cùng một thông báo** dù email có tồn tại hay không, để không tiết lộ email đã đăng ký |
| **FR-039** | WHERE email tồn tại, THE system SHALL sinh token `RESET_PASSWORD` hết hạn **1 giờ**, lưu dạng hash, và gửi email |
| **FR-040** | THE system SHALL sinh token đặt lại bằng `SecureRandom`, độ dài ≥ 16 ký tự, KHÔNG tuần tự (`BUS-13`) |
| **FR-041** | THE system SHALL giới hạn **3 yêu cầu/giờ** mỗi email |
| **FR-042** | WHEN đặt lại mật khẩu thành công, THE system SHALL thu hồi token đó VÀ **toàn bộ** refresh token của user |
| **FR-043** | WHEN đổi mật khẩu khi đã đăng nhập, THE system SHALL yêu cầu mật khẩu cũ đúng trước khi đổi |
| **FR-044** | WHEN đổi mật khẩu thành công, THE system SHALL thu hồi mọi refresh token **trừ session hiện tại** (E7) |
| **FR-045** | THE system SHALL từ chối mật khẩu mới trùng mật khẩu cũ với **422** `PASSWORD_UNCHANGED` |

### Nhóm 8 — Khoá tài khoản tạm (FR-046…FR-050) · UC-012

| Mã | Yêu cầu |
|---|---|
| **FR-046** | WHEN `failed_login_count` đạt **5**, THE system SHALL đặt `locked_until = now() + 15 phút` |
| **FR-047** | WHILE `locked_until > now()`, THE system SHALL từ chối đăng nhập với **429** `ACCOUNT_LOCKED` kèm số giây còn lại, KỂ CẢ khi mật khẩu đúng |
| **FR-048** | THE system SHALL đếm số lần sai **theo tài khoản**, không theo IP — tránh một người dùng NAT chung làm khoá người khác |
| **FR-049** | WHEN `locked_until` đã qua, THE system SHALL cho đăng nhập lại và đặt lại bộ đếm |
| **FR-050** | THE system SHALL KHÔNG khoá vĩnh viễn do sai mật khẩu. Ban vĩnh viễn chỉ do `SUPER_ADMIN` thực hiện |

### Nhóm 9 — Thông tin cá nhân và lịch sử (FR-051…FR-057) · UC-011, UC-014

| Mã | Yêu cầu |
|---|---|
| **FR-051** | THE system SHALL cho người dùng xem và sửa: `display_name`, `avatar_url`, `hsk_level`, `reminder_time`, `reminder_channels` |
| **FR-052** | THE system SHALL KHÔNG cho người dùng tự sửa: `email`, `plan_code`, `plan_expires_at`, role, trạng thái tài khoản |
| **FR-053** | THE system SHALL kiểm quyền sở hữu trước khi trả hoặc sửa thông tin cá nhân — đối chiếu `user_id` trong JWT, KHÔNG tin tham số client gửi (`BUS-01`) |
| **FR-054** | WHEN người dùng A yêu cầu dữ liệu của B, THE system SHALL trả **403**, kể cả khi id không tồn tại (không trả 404 để không tiết lộ id nào có thật) |
| **FR-055** | THE system SHALL lưu `reminder_time` theo **giờ Việt Nam**; job nhắc chạy với `zone = "Asia/Ho_Chi_Minh"` (`BUS-08`, `AC-08`) |
| **FR-056** | THE system SHALL cho xem lịch sử đăng nhập: thời điểm, loại client, IP đã mask |
| **FR-057** | THE system SHALL mask IP và email trong lịch sử theo `BUS-06` (`NFR-S07`) |

### Nhóm 10 — Phân quyền RBAC (FR-058…FR-066) · feature 6.2

| Mã | Yêu cầu |
|---|---|
| **FR-058** | THE system SHALL hỗ trợ **sáu** role: `USER` · `TEACHER` · `MANAGER` · `CONTENT_ADMIN` · `FINANCE_ADMIN` · `SUPER_ADMIN`, lưu ở `user_roles.role_code` với `CHECK` constraint (`AC-06`) |
| **FR-058b** | WHERE request không có JWT, THE system SHALL coi là actor `GUEST` — KHÔNG tạo dòng `user_roles`, KHÔNG có `role_code` cho `GUEST` |
| **FR-058c** | WHERE tác vụ do job định kỳ chạy (actor `SYSTEM`), THE system SHALL bỏ qua `JwtFilter` và dùng quyền hệ thống. `SYSTEM` KHÔNG phải role trong DB |
| **FR-059** | THE system SHALL cho một người dùng có **nhiều role** đồng thời (bảng nối `user_roles`) |
| **FR-060** | WHEN người dùng gọi endpoint không đủ quyền, THE system SHALL trả **403**, KHÔNG trả 401 hay 404 |
| **FR-061** | THE system SHALL đọc role từ JWT, KHÔNG query DB mỗi request — trừ khi token đã bị thu hồi |
| **FR-062** | WHEN `SUPER_ADMIN` gán role, THE system SHALL ghi `granted_by` và `granted_at` vào `user_roles` |
| **FR-063** | THE system SHALL chặn tự cấp role cho chính mình — `CHECK (granted_by IS NULL OR granted_by <> user_id)` |
| **FR-064** | THE system SHALL ghi `audit_logs` cho mọi lần gán hoặc thu hồi role |
| **FR-065** | WHEN `SUPER_ADMIN` ban một user đang có session, THE system SHALL làm request tiếp theo của user đó trả **403**, không chờ token hết hạn (E5) |
| **FR-066** | THE system SHALL chỉ cho `SUPER_ADMIN` gán role. Năm role còn lại — kể cả `CONTENT_ADMIN` và `FINANCE_ADMIN` — KHÔNG gán được role |

### Nhóm 11 — Gói dịch vụ và lượt free (FR-067…FR-075) · feature 6.1

| Mã | Yêu cầu |
|---|---|
| **FR-067** | THE system SHALL lưu gói hiện tại ở `users.plan_code` tham chiếu `plans(code)`, với ba gói: `FREE`, `PREMIUM`, `PREMIUM_PLUS` |
| **FR-068** | THE system SHALL đặt `plan_code = 'FREE'` và `plan_expires_at = NULL` khi tạo tài khoản mới |
| **FR-069** | THE system SHALL ràng buộc: gói `FREE` có `plan_expires_at IS NULL`; gói khác **bắt buộc** có `plan_expires_at` |
| **FR-070** | THE system SHALL cấp **10 lượt mỗi tính năng mỗi tháng** cho gói `FREE`, theo `plans.benefits` |
| **FR-071** | THE system SHALL đếm lượt đã dùng ở `users.free_usage` (JSONB theo `feature_code`) và đặt lại về rỗng vào **00:00 ngày 1 mỗi tháng giờ Việt Nam**, cập nhật `usage_reset_at` |
| **FR-072** | WHEN người dùng hết lượt free và không còn điểm, THE system SHALL trả **402** `INSUFFICIENT_CREDITS` kèm gợi ý nâng gói |
| **FR-073** | WHEN nhập mã thẻ loại `SUBSCRIPTION`, THE system SHALL đặt `plan_code` theo thẻ và `plan_expires_at = now() + plan_days` |
| **FR-074** | WHEN gói hết hạn, THE job SHALL hạ `plan_code` về `FREE` và đặt `plan_expires_at = NULL`, chạy theo giờ Việt Nam |
| **FR-075** | WHERE gói hết hạn giữa lúc người dùng đang dùng một lượt, THE system SHALL để lượt đó hoàn tất, không cắt giữa chừng (E8) |

### Nhóm 12 — Mô hình phễu: nhận mã từ hệ sinh thái (FR-076…FR-082)

> CNHSK là **sản phẩm phễu** cho một hệ sinh thái khác. Tiền là tiền thật nhưng
> **CNHSK không thu tiền** — chủ hệ sinh thái thu, CNHSK chỉ nhận mã đã có giá trị.
> Xem Hiến pháp mục *Mô hình kinh doanh*.

| Mã | Yêu cầu |
|---|---|
| **FR-076** | THE system SHALL phân loại mọi mã thẻ theo `credit_cards.channel` thuộc một trong ba giá trị: `ECOSYSTEM_GIFT`, `PARTNER_BATCH`, `DIRECT` |
| **FR-077** | WHERE `channel = 'PARTNER_BATCH'`, THE system SHALL **bắt buộc** có `partner_code` — không biết giao lô cho ai thì không đối soát được |
| **FR-078** | WHERE `channel = 'ECOSYSTEM_GIFT'`, THE system SHALL **bắt buộc** có `issued_to` (email hoặc định danh người nhận) |
| **FR-079** | THE system SHALL KHÔNG tích hợp cổng thanh toán, KHÔNG xử lý webhook thanh toán. Mã thẻ là **đường duy nhất** điểm vào hệ thống |
| **FR-080** | THE system SHALL cung cấp báo cáo tỉ lệ đổi mã theo `campaign`: `COUNT(status='USED') / COUNT(*)` nhóm theo `channel, campaign` |
| **FR-081** | WHEN người dùng đổi mã `ECOSYSTEM_GIFT` thành công, THE system SHALL ghi `issued_to` của mã vào `audit_logs` để đối soát với bên thứ ba |
| **FR-082** | THE system SHALL cho `FINANCE_ADMIN` đặt `expires_at` **tuỳ chọn** cho mỗi lô. Mã không có hạn thì sống vĩnh viễn — rủi ro đã chấp nhận, xem §11 |
| **FR-082b** | THE system SHALL giới hạn **tối đa 100 mã mỗi lô**. Vượt → **422** `BATCH_SIZE_EXCEEDED`. Kiểm ở **server**, không chỉ ở form — một lần bấm nhầm hoặc gọi API trực tiếp không được sinh ra hàng nghìn mã |

### Nhóm 13 — Hạn mức khách và màn chờ xác thực (FR-083…FR-088)

Hai thứ này đi cùng nhau vì cả hai thuộc trạng thái **chưa có tài khoản dùng
được**: khách chưa đăng ký, và người vừa đăng ký nhưng chưa xác thực email.

| Mã | Yêu cầu |
|---|---|
| **FR-083** | THE system SHALL cấp `GUEST` **3 lượt tra cứu mỗi ngày** trên `/api/learning/dictionary`, đặt lại 00:00 giờ Việt Nam (`BUS-08`) |
| **FR-084** | THE system SHALL đếm lượt khách bằng **cookie** `guest_quota` (HttpOnly, SameSite=Lax, 24 giờ) và Redis đối chiếu theo khoá cookie — KHÔNG đếm theo IP, vì `FR-048` đã bác cách đó do NAT chung |
| **FR-085** | WHEN khách hết 3 lượt, THE system SHALL trả **429** `GUEST_QUOTA_EXCEEDED` kèm `reset_at`; frontend chuyển `/login?redirect=` |
| **FR-086** | THE system SHALL KHÔNG áp hạn mức khách lên màn nào khác — mọi màn học, luyện, thi đều yêu cầu đăng nhập trước |
| **FR-087** | WHEN đăng ký thành công, THE system SHALL để frontend chuyển sang `/verify-email`, màn này hiện email đã gửi tới và cho **gửi lại** theo giới hạn `FR-012` (3 lần/giờ) |
| **FR-088** | WHERE người dùng mở `/verify-email?token=`, THE system SHALL xác thực token qua `FR-009`. Màn này **không chặn** việc dùng hệ thống — theo `FR-010`, chưa xác thực vẫn đăng nhập và học được, chỉ chặn thao tác đổi điểm |

> **`FR-084` là ma sát, không phải bảo mật.** Xoá cookie hoặc mở tab ẩn danh là
> đặt lại lượt. Đã chấp nhận — xem `design.md` §5.5. Đừng thêm fingerprint để
> bịt lỗ này.

### Key Entities

| Bảng | Vai trò trong feature này |
|---|---|
| `users` | Tài khoản + trạng thái + gói. 18 cột hiện có + 4 cột gói chờ v7 |
| `user_roles` | Bảng nối user ↔ role. `role_code` dùng `CHECK`, không còn FK tới `roles` |
| `auth_tokens` | Gộp 3 loại token: `REFRESH`, `RESET_PASSWORD`, `EMAIL_VERIFY` |
| `plans` | Danh mục 3 gói + `benefits` JSONB (hạn mức theo `feature_code`) |
| `credit_cards` | Mã thẻ + `channel`/`partner_code`/`campaign`/`issued_to` (mô hình phễu) |
| `audit_logs` | Ghi mọi lần gán/thu hồi role (`FR-064`) và đổi mã quà tặng (`FR-081`) |

---

## 4 · Data Model

### 4.1 · `users` — cột liên quan feature này

```sql
id                 BIGSERIAL PRIMARY KEY
email              VARCHAR(255) NOT NULL UNIQUE
password_hash      VARCHAR(255) NOT NULL
display_name       VARCHAR(100) NOT NULL
avatar_url         TEXT
hsk_level          SMALLINT CHECK (hsk_level BETWEEN 1 AND 9)

-- Trạng thái tài khoản
email_verified_at  TIMESTAMPTZ          -- NULL = chưa xác thực
failed_login_count SMALLINT NOT NULL DEFAULT 0
locked_until       TIMESTAMPTZ          -- khoá tạm do sai mật khẩu
suspended_at       TIMESTAMPTZ
suspended_until    TIMESTAMPTZ
banned_at          TIMESTAMPTZ
banned_by          BIGINT REFERENCES users(id)
ban_reason         TEXT

-- Nhắc học: giờ Việt Nam, NULL = tắt
reminder_time      TIME
reminder_channels  VARCHAR(50) NOT NULL DEFAULT 'WEB'

-- Gói dịch vụ (thêm ở V3)
plan_code          VARCHAR(20) NOT NULL DEFAULT 'FREE' REFERENCES plans(code)
plan_expires_at    TIMESTAMPTZ
free_usage         JSONB NOT NULL DEFAULT '{}'
usage_reset_at     TIMESTAMPTZ NOT NULL DEFAULT now()

CONSTRAINT ck_users_ban_reason  CHECK (banned_at IS NULL OR ban_reason IS NOT NULL)
CONSTRAINT ck_users_plan_expiry CHECK (
  (plan_code = 'FREE' AND plan_expires_at IS NULL)
  OR (plan_code <> 'FREE' AND plan_expires_at IS NOT NULL)
)
```

> **Vì sao gói nằm trên `users` chứ không phải bảng `user_subscriptions` riêng:**
> gói được đọc **cùng lúc** với mọi lần kiểm quyền. Tách ra là thêm một JOIN vào
> đường nóng nhất của hệ thống.

### 4.2 · `user_roles`

```sql
user_id    BIGINT      NOT NULL REFERENCES users(id)
role_code  VARCHAR(20) NOT NULL
granted_by BIGINT      REFERENCES users(id)
granted_at TIMESTAMPTZ NOT NULL DEFAULT now()
PRIMARY KEY (user_id, role_code)

CONSTRAINT ck_ur_self      CHECK (granted_by IS NULL OR granted_by <> user_id)
CONSTRAINT ck_ur_role_code CHECK (role_code IN
  ('USER','TEACHER','MANAGER','CONTENT_ADMIN','FINANCE_ADMIN','SUPER_ADMIN'))

CREATE INDEX ix_ur_role ON user_roles(role_code, user_id);
```

> **Bảng `roles` sẽ bị bỏ** (chờ `database.md` duyệt). Nó chỉ có 1 lệnh `INSERT` với 4 giá trị tĩnh và
> không có màn CRUD role nào trong 32 màn thiết kế (xem `design.md` §5). `AC-06` nói enum lưu `VARCHAR + CHECK`.
> Bỏ nó giảm một JOIN ở mọi truy vấn quyền.
> Đã chứng minh `DROP TABLE roles CASCADE` **không** làm mất dữ liệu `user_roles`.

### 4.3 · `auth_tokens`

```sql
id         BIGSERIAL PRIMARY KEY
user_id    BIGINT      NOT NULL REFERENCES users(id)
token_hash VARCHAR(255) NOT NULL        -- chỉ lưu hash, không lưu token thô
token_type VARCHAR(20)  NOT NULL
           CHECK (token_type IN ('REFRESH','RESET_PASSWORD','EMAIL_VERIFY'))
expires_at TIMESTAMPTZ NOT NULL
revoked_at TIMESTAMPTZ
created_at TIMESTAMPTZ NOT NULL DEFAULT now()

CREATE INDEX ix_tokens_user ON auth_tokens(user_id) WHERE revoked_at IS NULL;
```

> Gộp 3 bảng v5 (`refresh_tokens` + 2 bảng token khác) thành 1. Ba loại token có
> **cùng** vòng đời: sinh → hết hạn hoặc thu hồi.

### 4.4 · `plans`

```sql
code          VARCHAR(20)  PRIMARY KEY CHECK (code IN ('FREE','PREMIUM','PREMIUM_PLUS'))
name          VARCHAR(100) NOT NULL
duration_days SMALLINT     CHECK (duration_days IS NULL OR duration_days > 0)
benefits      JSONB        NOT NULL DEFAULT '{}'
price         NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0)
is_active     BOOLEAN      NOT NULL DEFAULT true

CONSTRAINT ck_plan_free CHECK (code <> 'FREE' OR (price = 0 AND duration_days IS NULL))
```

Dữ liệu khởi tạo:

| `code` | `price` | `duration_days` | `benefits` (mỗi tháng) |
|---|---|---|---|
| `FREE` | 0 | `NULL` (vĩnh viễn) | 10 lượt mỗi tính năng |
| `PREMIUM` | 99.000 | 30 | 50 AI · 200 dịch · 5 chấm bài · 300 chat |
| `PREMIUM_PLUS` | 199.000 | 30 | 200 · 1000 · 20 · 1000 |

> **`duration_days` khác `benefits`:** `duration_days` là hạn của **gói**,
> `benefits` là hạn mức mỗi **chu kỳ tháng**. Gói `FREE` vĩnh viễn nhưng lượt vẫn
> reset mỗi tháng theo `users.usage_reset_at`.

### 4.5 · `credit_cards` — cột mô hình phễu (chờ v7)

```sql
channel      VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
             CHECK (channel IN ('ECOSYSTEM_GIFT','PARTNER_BATCH','DIRECT'))
partner_code VARCHAR(50)    -- bên thứ ba nhận lô; NULL với DIRECT
campaign     VARCHAR(50)    -- đợt phát, để đo tỉ lệ đổi
issued_to    VARCHAR(255)   -- email/định danh người nhận; chỉ ECOSYSTEM_GIFT

CONSTRAINT ck_card_partner CHECK (channel <> 'PARTNER_BATCH' OR partner_code IS NOT NULL)
CONSTRAINT ck_card_gift    CHECK (channel <> 'ECOSYSTEM_GIFT' OR issued_to IS NOT NULL)

CREATE INDEX ix_card_funnel  ON credit_cards(channel, campaign, status);
CREATE INDEX ix_card_partner ON credit_cards(partner_code, status)
  WHERE partner_code IS NOT NULL;
```

> **Không có bảng `partners`.** Hiện chỉ có **một** bên thứ ba (chủ hệ sinh thái).
> Một bảng cho một dòng dữ liệu là ngược YAGNI. Khi có bên thứ hai thì tách,
> lúc đó `partner_code` thành FK.

Truy vấn đo phễu — chỉ số sống của mô hình:

```sql
SELECT channel, campaign,
       COUNT(*) FILTER (WHERE status = 'USED') AS da_doi,
       COUNT(*)                                AS tong,
       ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'USED') / COUNT(*), 1) AS ti_le
FROM credit_cards
GROUP BY channel, campaign
ORDER BY channel, campaign;
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Nguyên nhân | FR | Xử lý |
|---|---|---|---|---|
| `EMAIL_ALREADY_EXISTS` | 409 | Email đã đăng ký | FR-004 | `UNIQUE` trên `users.email` chặn |
| `VALIDATION_FAILED` | 422 | Email sai định dạng, mật khẩu ngắn, không khớp | FR-005 | Trả danh sách field |
| `INVALID_CREDENTIALS` | 401 | Sai mật khẩu **hoặc** email không tồn tại | FR-017, FR-018 | 🔴 **Một thông báo duy nhất** |
| `ACCOUNT_LOCKED` | 429 | Sai > 5 lần/giờ | FR-047 | Khoá 15 phút, trả số giây còn lại |
| `ACCOUNT_BANNED` | 403 | `banned_at IS NOT NULL` | FR-020 | Kèm `ban_reason` |
| `ACCOUNT_SUSPENDED` | 403 | `suspended_until > now()` | FR-021 | Kèm thời điểm hết |
| `ACCOUNT_UNVERIFIED` | 403 | Chưa xác thực, thao tác đổi điểm | FR-010 | Cho học, chặn đổi điểm |
| `INVALID_VERIFY_TOKEN` | 400 | Token xác thực sai/hết hạn/đã dùng | FR-011 | Gợi ý xin gửi lại |
| `TOKEN_REUSE_DETECTED` | 401 | Refresh token đã thu hồi bị dùng lại | FR-032 | 🔴 Thu hồi **toàn bộ** token |
| `TOKEN_EXPIRED` | 401 | Access token hết hạn | FR-031 | Client tự gọi refresh |
| `PASSWORD_UNCHANGED` | 422 | Mật khẩu mới trùng mật khẩu cũ | FR-045 | — |
| `FORBIDDEN` | 403 | Không đủ role, hoặc truy cập dữ liệu người khác | FR-054, FR-060 | 🔴 KHÔNG trả 404 |
| `SELF_ROLE_GRANT` | 403 | Tự cấp role cho mình | FR-063 | DB `CHECK` chặn |
| `INSUFFICIENT_CREDITS` | 402 | Hết lượt free và hết điểm | FR-072 | Gợi ý nâng gói |
| `GUEST_QUOTA_EXCEEDED` | 429 | Khách hết 3 lượt tra cứu trong ngày | FR-085 | Kèm `reset_at` |
| `BATCH_SIZE_EXCEEDED` | 422 | Tạo lô quá 100 mã | FR-082b | Kiểm ở server |
| `CSRF_TOKEN_MISSING` | 403 | Web thiếu CSRF ở thao tác đổi tiền | `HR-05` | — |
| `TOO_MANY_REQUESTS` | 429 | Vượt giới hạn gửi lại email / quên mật khẩu | FR-012, FR-041 | — |

Mọi lỗi theo định dạng `{error_code, message, request_id}` — `HR-09`.
KHÔNG lộ stack trace.

---

## 6 · Architectural Constraints (AC Mapping)

| Ràng buộc | Feature này tuân thủ thế nào |
|---|---|
| **HR-01** Mật khẩu bcrypt ≥12 / argon2id | FR-003 |
| **HR-02** Không secret trong code | JWT secret đọc từ biến môi trường |
| **HR-03** Cookie đủ 5 thuộc tính | FR-014, FR-015 |
| **HR-04** CORS không dùng `*` | FR-028 |
| **HR-05** CSRF cho thao tác tiền | FR-073 (nhập mã thẻ gói) |
| **HR-07** Validate mọi input | FR-005 |
| **HR-09** Không lộ stack trace | §5 Error Matrix |
| **AC-01** API First | OpenAPI cho 14 endpoint viết **trước** khi code |
| **AC-02** Một app, 4 module | Thuộc module `auth`; `plans` thuộc `learning` |
| **AC-03** Module gọi nhau qua lớp `api` | `auth` đọc `plans` qua `PlanLookup`, KHÔNG query thẳng |
| **AC-06** Enum = VARCHAR + CHECK | `role_code`, `token_type`, `plan_code` |
| **AC-08** `TIMESTAMPTZ`, job ghi zone | FR-055, FR-074 |
| **BUS-01** Kiểm quyền sở hữu | FR-053, FR-054 |
| **BUS-02** Cùng transaction | FR-002 |
| **BUS-06** Mask PII | FR-057 |
| **BUS-08** Giờ Việt Nam | FR-055, FR-074 |
| **BUS-13** `SecureRandom` ≥16 ký tự | FR-040 |

---

## 7 · Non-Functional Requirements

### 7.1 · Performance

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Đăng nhập (gồm bcrypt verify) | < 500ms (p95) |
| `NFR-P02` | Kiểm token mỗi request | < 10ms (p95) — đọc từ JWT, không query DB |
| `NFR-P03` | `GET /api/auth/me` | < 100ms (p95) |

> bcrypt cost 12 **cố tình chậm** (~200–300ms). Đây là tính năng bảo mật,
> không phải lỗi hiệu năng. Không hạ cost để đạt mốc nhanh hơn.

### 7.2 · Security

| Mã | Yêu cầu |
|---|---|
| `NFR-S01` | Mật khẩu bcrypt cost ≥ 12 hoặc argon2id |
| `NFR-S02` | Token đặt lại mật khẩu: `SecureRandom` ≥ 16 ký tự, chỉ lưu hash |
| `NFR-S03` | ≤ 5 lần sai mật khẩu/giờ · ≤ 3 lần xin gửi lại email/giờ |
| `NFR-S04` | Thao tác nâng gói **bắt buộc** CSRF token (web dùng cookie) |
| `NFR-S05` | CORS khai origin tường minh cho `cnhsk.com` và `game.cnhsk.com` |
| `NFR-S06` | Lỗi trả `{error_code, message, request_id}` |
| `NFR-S07` | Log KHÔNG chứa mật khẩu, token, email người dùng |

### 7.3 · Scalability

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-C01` | Người dùng đồng thời (môi trường demo) | 50 |
| `NFR-C02` | Số role mỗi user | ≤ 4 |

### 7.4 · Availability

| Mã | Yêu cầu |
|---|---|
| `NFR-A01` | SMTP lỗi → đăng ký **vẫn thành công** (FR-007) |
| `NFR-A02` | Token hết hạn giữa bài thi → không mất bản nháp (FR-037) |

---

## 8 · Constitution Compliance Check

| Kiểm tra | Kết quả |
|---|---|
| Có vi phạm `HR-*` nào? | Không |
| Có vi phạm `AC-*` nào? | Không |
| Có vi phạm `BUS-*` nào? | Không |
| Có `TODO` nào trong spec? | Không — các điểm mở ghi ở §9 |
| Mọi FR có map được test? | Có — 75 FR, mỗi FR một test case |
| `BUS-16` (trọng số mastery) ảnh hưởng feature này? | Không — thuộc `002-learning-core` |
| `TODO(PAYMENT_SCOPE)` ảnh hưởng? | **Có** — xem §9 câu 2 |

---

## 9 · Open Questions

| # | Câu hỏi | Chặn gì | Đề xuất |
|---|---|---|---|
| 1 | JWT dùng HS256 hay RS256? | FR-016, FR-061 | HS256 — một backend duy nhất, không cần khoá công khai |
| 3 | Access token sống bao lâu? | FR-031 | 15 phút access · 30 ngày refresh |
| 4 | Lịch sử đăng nhập lưu ở đâu? | FR-056 | Chưa có bảng. Dùng `audit_logs` với `action='LOGIN'` thay vì thêm bảng mới |

### Đã chốt 2026-10-05

| # | Câu hỏi | Đáp án |
|---|---|---|
| ~~2~~ | `TODO(PAYMENT_SCOPE)` | **Tiền thật, thu ngoài hệ thống.** Giữ `HR-05`, `BUS-12`, `BUS-13`, `BUS-14`, `BUS-15`. KHÔNG tích hợp cổng thanh toán (FR-079) |
| ~~5~~ | 10 lượt free vĩnh viễn hay mỗi tháng | **Mỗi tháng**, reset 00:00 ngày 1 giờ Việt Nam (FR-070, FR-071) |

---

## 10 · Frontend Implementation Note

| Màn hình | File mockup gốc | Ghi chú |
|---|---|---|
| Đăng nhập / Đăng ký | `02-login.html` | Một trang, hai tab |
| Thông tin cá nhân | nằm trong `03-dashboard.html` | FR-051, FR-052 |
| Nâng gói | `17-billing.html` | FR-073, cần CSRF |
| Quản lý người dùng | `30-admin-users.html` | FR-062, FR-066 — chỉ `SUPER_ADMIN` |

> Mockup đã chuyển vào bản backup (`D:\CLHSK\learning-service\docs\mockup\`).
> Design token ở `docs/reference/design.md` §6.

**Ba trạng thái bắt buộc** mỗi màn có dữ liệu động: Empty · Loading · Error,
text tiếng Việt có dấu.

---

## 11 · Rủi ro nghiệp vụ đã chấp nhận

### 11.1 · Lô mã rò rỉ không phát hiện được

CNHSK **không có webhook thanh toán** (FR-079) nên không biết mã nào bên thứ ba đã bán.
Nếu file CSV chứa mã thô bị rò rỉ — qua email, máy cá nhân, chia sẻ nhầm — thì:

- Không có cách phát hiện **tự động**
- Người lạ đổi được mã, và sổ cái ghi nhận hợp lệ (vì mã đúng là mã thật)
- Không phân biệt được "khách hợp lệ" với "người lấy mã rò rỉ"

**Chủ dự án đã quyết không bắt buộc `expires_at`** (FR-082), nên lớp giảm thiểu duy nhất là
**theo dõi tỉ lệ đổi bất thường theo `batch_id`** qua index `ix_card_funnel`.

Dấu hiệu cần cảnh báo:

| Dấu hiệu | Ngưỡng đề xuất |
|---|---|
| Một lô đổi quá nhanh | > 50% lô trong 1 giờ |
| Đổi tập trung từ ít IP | > 10 mã cùng lô từ một IP |
| Lô cũ bỗng đổi ồ ạt | lô > 90 ngày mà đổi > 20% trong 1 ngày |

> Đây là **việc giám sát**, không phải ràng buộc DB. Nếu sau này muốn chặn cứng thì thêm
> `CHECK (channel <> 'PARTNER_BATCH' OR expires_at IS NOT NULL)` — một dòng, không phá
> dữ liệu đã có.

### 11.2 · Mã thô chỉ hiện một lần

UC-099 bước 9: file CSV mã thô trả về **một lần duy nhất**, không lưu ở đâu. Nếu
`FINANCE_ADMIN` mất file thì **cả lô không dùng được** — không có đường lấy lại, vì DB chỉ
có `code_hash` (`BUS-12`).

Đây là đánh đổi **có chủ đích**: lưu được mã thô để lấy lại thì mất luôn lớp bảo vệ của
việc chỉ lưu hash.

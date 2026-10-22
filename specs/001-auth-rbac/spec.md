# Feature Specification: Xác thực, tài khoản và phân quyền

**Feature:** `001-auth-rbac`
**Feature tree:** 6.1 (phần tài khoản) · 6.2 (Phân quyền — 6 role trong DB)
**Module:** `auth` · dùng chung `shared`
**Use case:** UC-001 → UC-005, UC-007 → UC-011, UC-012 («extend» UC-003/004), UC-014 (V2), UC-114, UC-115 · UC-006 đã chuyển thành FR (Nhóm 6)
**Màn hình:** SCR-001 (phần đăng nhập/đăng ký), SCR-002 → SCR-008, SCR-076, SCR-077, SCR-079
**Scope:** MVP, riêng UC-014 thuộc V2 · **Priority:** P0 — mọi feature khác phụ thuộc feature này

| Trường | Giá trị |
|---|---|
| **Version** | 1.1.0 |
| **Status** | DRAFT — chờ cả nhóm duyệt rồi mới `APPROVED` |
| **Approved by** | — |
| **Locked on** | — |
| **Thay đổi so với 1.0** | Đổi tên actor (18/10) · tách gói, lượt free, mã thẻ sang spec gói dịch vụ (credits-plans) · tách hạn mức khách sang spec từ điển · chốt 4 câu hỏi mở · chốt 8 mâu thuẫn tài liệu (Nhóm 14) · thêm §0 nền tảng và §12–§15 |

> **Codex đọc theo thứ tự:** `AGENTS.md` → file này → `contracts/openapi-auth.yaml` → `tasks.md`.
> Field chi tiết của từng màn nằm ở `docs/reference/screen-fields.md` theo số dòng ghi ở §12.
> Chỗ nào spec không nói: **dừng, ghi câu hỏi vào `questions.md`**, không tự giả định.

---

## 0 · Phạm vi và nền tảng đi kèm

Spec này là tính năng **đầu tiên** được code, nên nó mang theo phần nền tảng mà mọi tính năng sau dùng lại.
Các mục dưới là **bắt buộc**, có task riêng ở `tasks.md` đợt 0–1.

### 0.1 · Trong / ngoài phạm vi

| Trong phạm vi | Ngoài phạm vi (spec khác) |
|---|---|
| Đăng ký, xác thực email, đăng nhập web/mobile/trang game, làm mới token, đăng xuất | Gói dịch vụ, lượt free, đổi mã thẻ, sổ cái (FR-067→075 cũ ngoài FR-068, FR-076→082b) → spec gói dịch vụ (credits-plans) |
| Quên/đặt lại/đổi mật khẩu, tạm chặn nguồn đăng nhập | Hạn mức tra từ của khách (FR-083→086 cũ) → spec từ điển |
| Hồ sơ cá nhân (SCR-007), đổi mật khẩu (SCR-008) | Cài đặt giờ nhắc học (SCR-009) → spec nhắc học |
| Quản trị tài khoản: tìm, xem, cấm/gỡ cấm, cấp/thu hồi role (SCR-076, SCR-077) | Tạm khoá do kiểm duyệt cộng đồng (`MODERATOR`, V2) — cột `suspended_*` vẫn được **đọc** khi đăng nhập |
| Nền tảng §0.2 | Lịch sử đăng nhập (UC-014, V2) — chỉ đặc tả FR-056/057, chưa làm |

### 0.2 · Nền tảng đi kèm

| Hạng mục | Yêu cầu |
|---|---|
| **Migration** | Chuyển `db/migration/*.sql` vào `src/main/resources/db/migration/`. Viết lại `V1__core_schema.sql`, `V2__community.sql` **khớp đúng `docs/reference/database.md`**: 4 schema `auth` · `learning` · `community` · `shared` (theo RFC trong `database.md` sửa `AC-03`/`AC-04` — duyệt cùng spec này, rồi sửa `constitution.md` `AC-04` thành 4 schema), bỏ bảng `roles`, `role_code` dùng `CHECK` (§4.2). **Đóng băng** `learning.knowledge_points` và `learning.question_knowledge_points`: tạo đúng như `database.md` nhưng không code nào dùng (chờ cây tri thức). Không seed dữ liệu học |
| **Tài khoản quản trị đầu tiên** | Khi khởi động, nếu chưa có `ADMIN` nào và có biến môi trường `CNHSK_BOOTSTRAP_ADMIN_EMAIL` + `CNHSK_BOOTSTRAP_ADMIN_PASSWORD`, tạo một tài khoản `ADMIN` + `LEARNER`, `email_verified_at = now()`, ghi `audit_logs` `action='BOOTSTRAP_ADMIN'`. Đã có `ADMIN` thì bỏ qua. Không bao giờ log mật khẩu |
| **Định dạng lỗi** | Mọi lỗi: `{ "error_code": "...", "message": "...", "request_id": "...", "details": [...]? }`. `request_id` = header `X-Request-Id` nếu client gửi (≤ 64 ký tự, `[A-Za-z0-9-]`), không thì UUID v4; đặt vào MDC và header response. `details` chỉ có ở `VALIDATION_FAILED`: `[{ "field", "message" }]`. Không bao giờ trả stack trace (`HR-09`) |
| **Lớp lỗi** | `shared.exception.ApiException(ErrorCode code, HttpStatus status, String message, Map<String,Object> extra)`; `ErrorCode` là enum tập trung; `@RestControllerAdvice` duy nhất ở `shared.exception.GlobalExceptionHandler` |
| **Cấu hình** | CORS origin đọc từ `cnhsk.cors.allowed-origins` (danh sách, **không** `*`): dev `http://localhost:5173` (web), `http://localhost:5174` (game); prod `https://cnhsk.com`, `https://game.cnhsk.com`. `allowCredentials=true`. Jackson ghi thời gian ISO-8601 UTC. Bean `Clock` dùng mọi nơi cần "bây giờ" (để test) |
| **OpenAPI** | `springdoc-openapi-starter-webmvc-ui` phục vụ `/v3/api-docs` ở profile `dev`. Hợp đồng nguồn là `contracts/openapi-auth.yaml`; test hợp đồng so khớp |
| **Hạ tầng dev** | `docker-compose.yml`: PostgreSQL 18 (`cnhsk_db`), Redis 7, Mailpit (SMTP giả, UI cổng 8025). `.env.example` liệt kê mọi biến (§0.3). README: chạy được trong 5 lệnh |
| **Test nền** | `AbstractIntegrationTest` dùng Testcontainers PostgreSQL 18 + Redis 7; JaCoCo ngưỡng 80% (`ES-05`) |
| **Frontend khung** | `cnhsk-web` (React 18.3, Vite 5, TS 5.7, Tailwind 3.4, react-router 6.28, `lucide-react`, `clsx`, `tailwind-merge`). Cấu trúc theo `AGENTS.md`. Token màu từ `docs/reference/design.md` §6. `shared/api/client.ts`, `shared/auth/AuthProvider.tsx`, `shared/auth/RequireRole.tsx` (§12.2) |

### 0.3 · Biến môi trường

| Biến | Bắt buộc | Ví dụ dev | Ghi chú |
|---|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Có | `jdbc:postgresql://localhost:5432/cnhsk_db` | |
| `REDIS_HOST`, `REDIS_PORT` | Có | `localhost`, `6379` | |
| `JWT_SECRET` | Có | 64 ký tự ngẫu nhiên | HS256, ≥ 256 bit; thiếu hoặc ngắn hơn 32 byte → app **không khởi động** |
| `COOKIE_DOMAIN` | Có | `localhost` (dev) · `cnhsk.com` (prod) | |
| `COOKIE_SECURE` | Có | `false` (dev) · `true` (prod) | |
| `APP_WEB_BASE_URL` | Có | `http://localhost:5173` | Dùng dựng link trong email |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM` | Có | Mailpit `localhost:1025` | |
| `CNHSK_BOOTSTRAP_ADMIN_EMAIL`, `CNHSK_BOOTSTRAP_ADMIN_PASSWORD` | Không | | §0.2 |

---

## 1 · Context & Goal

### 1.1 · Business goal feature này phục vụ

Theo `docs/reference/CONTEXT.md` §2:

| Mã | Business goal | Feature này đóng góp gì |
|---|---|---|
| **BG-05** | Demo được trọn luồng học → thi → phân tích → luyện lại | Không đăng nhập được thì không demo được gì. Đây là cửa vào |
| **BG-06** | Giải thích được 6 điểm bảo mật thanh toán khi bảo vệ | 3/6 điểm nằm ở feature này: mật khẩu, CSRF, phân quyền |
| **BG-04** | Nội dung qua kiểm duyệt trước khi tới người học | Role `TEACHER`/`MODERATOR` là cơ chế thực thi |

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
| **SC-003** | Tài khoản chưa xác thực email vẫn học được, nhưng không làm được thao tác đổi điểm | đúng cả hai |
| **SC-004** | Cùng cặp email–IP sai mật khẩu 5 lần trong 15 phút → lần tiếp theo trả **423** `LOGIN_SOURCE_BLOCKED` trong 15 phút | 100% |
| **SC-005** | Người dùng A không đọc được dữ liệu của B qua đổi id trong URL | 403 ở mọi endpoint |
| **SC-006** | Bị cấm hoặc bị thu hồi role → request kế tiếp bị từ chối, không chờ token hết hạn | 100% |

### 1.4 · Tech context

| Thành phần | Chốt |
|---|---|
| Backend | Spring Boot 3.5.14 · Java 17 · module `auth` |
| Token | jjwt 0.12.6 · **HS256** · access **15 phút**, refresh **30 ngày**, xoay vòng mỗi lần làm mới |
| Mật khẩu | bcrypt cost **12** (`HR-01`) |
| DB | `auth.users` · `auth.user_roles` · `auth.auth_tokens` · `shared.audit_logs` |
| Redis | Bộ đếm đăng nhập sai, giới hạn gửi email, mốc vô hiệu phiên (FR-092) |
| Frontend | React 18.3 · Vite 5 · TypeScript 5.7 |

---

## 2 · User Stories

### US-1 · Người học mới tạo tài khoản (P1)

**Là** khách chưa có tài khoản, **tôi muốn** đăng ký bằng email,
**để** bắt đầu học và lưu được tiến độ.

**Given** email chưa tồn tại trong hệ thống
**When** tôi nhập họ tên, email, mật khẩu hợp lệ và đồng ý điều khoản
**Then** tài khoản được tạo với role `LEARNER`, gói `FREE`, và tôi nhận email xác thực
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

**Là** `ADMIN`, **tôi muốn** gán role cho người dùng,
**để** giao việc duyệt nội dung cho `TEACHER` và `MODERATOR`.

**Given** tôi là `ADMIN`
**When** tôi gán role `TEACHER` cho một người dùng
**Then** role được ghi với `granted_by = id của tôi`
**And** tôi **không** gán được role cho chính mình

### US-5 · Quản trị viên cấm tài khoản vi phạm (P2)

**Given** tôi là `ADMIN` và một tài khoản đang có phiên đăng nhập
**When** tôi cấm tài khoản đó kèm lý do
**Then** request kế tiếp của tài khoản đó trả **403** `ACCOUNT_BANNED`
**And** tôi gỡ cấm được, sau đó tài khoản đăng nhập lại bình thường

### Edge cases

| # | Tình huống | Hành vi mong đợi |
|---|---|---|
| E1 | Hai request đăng ký cùng email đồng thời | Một thành công, một nhận `EMAIL_ALREADY_EXISTS`. `UNIQUE` trên `users.email` chặn |
| E2 | SMTP lỗi khi gửi email xác thực | Đăng ký **vẫn trả 201**. Ghi log. Người dùng xin gửi lại sau (`NFR-A01`) |
| E3 | Refresh token bị dùng lại sau khi đã rotate | Thu hồi **toàn bộ** token của user đó, buộc đăng nhập lại |
| E4 | Token hết hạn giữa lúc đang làm bài | Client tự làm mới token (FR-031) rồi gửi lại request một lần; bài đang làm không mất |
| E5 | Người dùng bị `ADMIN` cấm khi đang có phiên | Request tiếp theo trả 403, không chờ token hết hạn |
| E6 | Mobile mở WebView game nhưng chưa tiêm token | Trang game hiện trạng thái chưa đăng nhập, không crash |
| E7 | Đổi mật khẩu thành công | Thu hồi mọi refresh token **trừ** phiên hiện tại |
| E8 | Hai `ADMIN` cùng gán một role cho một người cùng lúc | Một thành công, một nhận **409** `ROLE_ALREADY_GRANTED` (PK `user_id, role_code`) |
| E9 | `ADMIN` thu hồi role `ADMIN` của `ADMIN` cuối cùng còn hoạt động | **409** `REVOKE_LAST_ADMIN` |
| E10 | Mở link xác thực email đã dùng, tài khoản đã xác thực | **200**, trả trạng thái đã xác thực (idempotent), không báo lỗi |

---

## 3 · Functional Requirements

> Ký hiệu EARS: **WHEN** (sự kiện) / **WHILE** (trạng thái) / **WHERE** (ngữ cảnh)
> + THE system SHALL. Mỗi FR có ít nhất một acceptance test ở §14.

### Nhóm 1 — Đăng ký (FR-001…FR-008) · UC-001

| Mã | Yêu cầu |
|---|---|
| **FR-001** | WHEN khách gửi `POST /api/auth/register` với email chưa tồn tại và dữ liệu hợp lệ, THE system SHALL tạo một dòng `users` với `email_verified_at = NULL`, gán role `LEARNER` vào `user_roles`, đặt `plan_code = 'FREE'`, và trả **201** |
| **FR-002** | THE system SHALL thực hiện tạo user + gán role trong **cùng một transaction** (`BUS-02`) |
| **FR-003** | THE system SHALL hash mật khẩu bằng bcrypt cost 12 trước khi ghi (`HR-01`, `NFR-S01`) |
| **FR-004** | WHEN email (đã chuẩn hoá, FR-095) đã tồn tại, THE system SHALL trả **409** `EMAIL_ALREADY_EXISTS` |
| **FR-005** | THE system SHALL validate theo FR-091 (mật khẩu), FR-095 (email), `displayName` 2–100 ký tự sau khi bỏ khoảng trắng hai đầu, `acceptTerms = true`, hai lần nhập mật khẩu khớp. Vi phạm → **422** `VALIDATION_FAILED` kèm `details` từng field |
| **FR-006** | WHEN tạo user thành công, THE system SHALL sinh token `EMAIL_VERIFY` trong `auth_tokens` hết hạn sau **24 giờ** và gửi email chứa link `{APP_WEB_BASE_URL}/verify-email?token=...` **sau khi transaction commit** |
| **FR-007** | WHERE SMTP lỗi, THE system SHALL **vẫn trả 201** và ghi log lỗi (không ghi email), KHÔNG rollback việc tạo tài khoản (`NFR-A01`) |
| **FR-008** | THE system SHALL KHÔNG bao giờ ghi mật khẩu thô hoặc token thô vào log (`NFR-S07`) |

### Nhóm 2 — Xác thực email (FR-009…FR-013) · UC-002

| Mã | Yêu cầu |
|---|---|
| **FR-009** | WHEN người dùng gửi `POST /api/auth/verify-email` với token hợp lệ chưa hết hạn, THE system SHALL đặt `email_verified_at = now()` và thu hồi token đó |
| **FR-010** | WHILE `email_verified_at IS NULL`, THE system SHALL cho đăng nhập và cho học, NHƯNG từ chối mọi thao tác đổi điểm với **403** `ACCOUNT_UNVERIFIED`. Spec này cung cấp `AuthApi.requireVerifiedEmail(userId)` để spec khác gọi |
| **FR-011** | WHEN token không tồn tại, đã thu hồi hoặc hết hạn, THE system SHALL trả **400** `INVALID_VERIFY_TOKEN` — trừ trường hợp E10 (FR-094) |
| **FR-012** | THE system SHALL cho phép xin gửi lại email xác thực qua `POST /api/auth/verify-email/resend` (đã đăng nhập), tối đa **3 lần/giờ** mỗi tài khoản; vượt → **429** `TOO_MANY_REQUESTS` kèm `retryAfterSeconds` |
| **FR-013** | WHEN gửi lại, THE system SHALL thu hồi token `EMAIL_VERIFY` cũ trước khi sinh token mới |

### Nhóm 3 — Đăng nhập web bằng cookie (FR-014…FR-021) · UC-003

| Mã | Yêu cầu |
|---|---|
| **FR-014** | WHEN đăng nhập đúng từ web, THE system SHALL đặt cookie `access_token` với **đủ năm thuộc tính**: `Domain={COOKIE_DOMAIN}`, `HttpOnly`, `Secure={COOKIE_SECURE}`, `SameSite=Lax`, `Max-Age=900`, `Path=/` (`HR-03`) |
| **FR-014b** | WHEN đăng nhập đúng từ web, THE system SHALL đặt cookie `refresh_token` với `Domain={COOKIE_DOMAIN}`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Max-Age=2592000`, **`Path=/api/auth`** — chỉ gửi kèm các endpoint xác thực |
| **FR-015** | THE system SHALL đặt `Domain` là tên miền cha, KHÔNG để trống. Để trống thì `game.cnhsk.com` **không nhận được cookie** và UC-005 hỏng |
| **FR-016** | WHEN đăng nhập đúng, THE system SHALL sinh `session_id` UUID mới cho phiên, sinh refresh token ngẫu nhiên 32 byte (`SecureRandom`, Base64URL), lưu **SHA-256 hash** và `session_id` vào `auth_tokens`, KHÔNG lưu token thô. Access token là JWT HS256 với claim: `sub` (user id), `roles` (mảng), `ev` (email đã xác thực: bool), `iat`, `iatMs` (epoch milliseconds lúc phát), `exp`, `jti`, `sid` (`session_id` UUID) |
| **FR-017** | WHEN mật khẩu sai hoặc email không tồn tại, THE system SHALL tăng bộ đếm Redis của cặp email chuẩn hoá–IP và trả **401** `INVALID_CREDENTIALS` nếu chưa đạt ngưỡng chặn |
| **FR-018** | THE system SHALL trả **cùng một thông báo** "Email hoặc mật khẩu không đúng" cho email không tồn tại và mật khẩu sai; thời gian xử lý hai nhánh tương đương (vẫn chạy bcrypt với hash giả khi email không tồn tại) |
| **FR-019** | WHEN đăng nhập thành công, THE system SHALL xoá bộ đếm Redis của cặp email–IP đó |
| **FR-020** | WHILE `banned_at IS NOT NULL`, THE system SHALL từ chối đăng nhập với **403** `ACCOUNT_BANNED` kèm `banReason` |
| **FR-021** | WHILE `suspended_until > now()`, THE system SHALL từ chối đăng nhập với **403** `ACCOUNT_SUSPENDED` kèm `suspendedUntil` |

Nếu vừa bị cấm vừa bị treo, trả ACCOUNT_BANNED.

### Nhóm 4 — Đăng nhập mobile bằng header (FR-022…FR-025) · UC-004

| Mã | Yêu cầu |
|---|---|
| **FR-022** | WHERE header `X-Client-Type: MOBILE`, THE system SHALL trả `accessToken`, `refreshToken`, `accessTokenExpiresIn` (giây) **trong body response**, KHÔNG đặt cookie |
| **FR-023** | THE system SHALL đọc access token theo thứ tự: **cookie `access_token` trước, không có thì header** `Authorization: Bearer`. Thiếu một trong hai nhánh thì một loại client không đăng nhập được |
| **FR-024** | WHERE request xác thực bằng header (không có cookie `access_token`), THE system SHALL KHÔNG yêu cầu CSRF token |
| **FR-025** | THE system SHALL dùng **cùng một** endpoint `/api/auth/login` cho cả ba client, phân biệt bằng header `X-Client-Type` ∈ `WEB` · `MOBILE` (FR-089) |

### Nhóm 5 — Đăng nhập trang game bằng cookie chung (FR-026…FR-030) · UC-005

| Mã | Yêu cầu |
|---|---|
| **FR-026** | WHERE người dùng đã đăng nhập ở `cnhsk.com` và mở `game.cnhsk.com`, THE system SHALL nhận cookie tự động (nhờ `Domain` tên miền cha) và coi là đã đăng nhập |
| **FR-027** | THE system SHALL cung cấp `GET /api/auth/me` để trang game và web biết mình là ai — vì cookie `HttpOnly` nên JavaScript **không đọc được** token. Chưa đăng nhập → **401** `UNAUTHENTICATED` |
| **FR-028** | THE system SHALL khai báo CORS cho origin trang game **tường minh**, CẤM dùng `*` (`HR-04`, `NFR-S05`) |
| **FR-029** | THE system SHALL đặt `Access-Control-Allow-Credentials: true` cho origin web và trang game |
| **FR-030** | WHERE mobile mở game trong WebView, THE app SHALL tiêm `Authorization` header **trước khi** trang chạy; THE system SHALL chấp nhận token từ header ở mọi endpoint game (theo FR-023) |

### Nhóm 6 — Làm mới và đăng xuất (FR-031…FR-037) · (UC-006 cũ), UC-007

| Mã | Yêu cầu |
|---|---|
| **FR-031** | WHEN nhận refresh token hợp lệ tại `POST /api/auth/refresh` (cookie `refresh_token` ở web, body `{refreshToken}` ở mobile), THE system SHALL cấp access token mới VÀ refresh token mới, rồi thu hồi refresh token cũ (rotation), trong một transaction; refresh token mới và access token mới giữ nguyên `session_id` của phiên cũ |
| **FR-032** | WHEN một refresh token đã thu hồi được dùng lại, THE system SHALL kiểm tra trong cùng user và `session_id` có refresh-token row mới hơn hay không. Có row mới hơn cùng phiên → reuse: thu hồi **toàn bộ** refresh token của user đó, ghi `audit_logs` `action='TOKEN_REUSE'`, và trả **401** `TOKEN_REUSE_DETECTED`. Không có row mới hơn (token bị thu hồi có chủ đích, như đổi mật khẩu/đăng xuất) → chỉ trả **401** `INVALID_REFRESH_TOKEN`, không thu hồi các phiên khác và không ghi audit reuse |
| **FR-033** | THE system SHALL so sánh refresh token bằng cách hash SHA-256 token người dùng gửi rồi đối chiếu `auth_tokens.token_hash`, KHÔNG so sánh token thô |
| **FR-034** | WHEN đăng xuất từ web (`POST /api/auth/logout`), THE system SHALL xoá hai cookie (`Max-Age=0`, cùng `Domain`/`Path`) và thu hồi refresh token của phiên đó |
| **FR-035** | WHEN đăng xuất từ mobile, THE system SHALL thu hồi refresh token nhận trong body |
| **FR-036** | THE system SHALL cho phép đăng xuất **mọi thiết bị** (`POST /api/auth/logout-all`) — thu hồi tất cả refresh token của user và đặt mốc vô hiệu phiên (FR-092) |
| **FR-037** | WHERE access token hết hạn giữa lúc dùng, THE client SHALL gọi FR-031 rồi gửi lại request **một lần**; làm mới thất bại → chuyển SCR-002 kèm `redirect` |

### Nhóm 7 — Quên và đổi mật khẩu (FR-038…FR-045) · UC-008, UC-009, UC-010

| Mã | Yêu cầu |
|---|---|
| **FR-038** | WHEN nhận `POST /api/auth/forgot-password`, THE system SHALL trả **202** với **cùng một thông báo** dù email có tồn tại hay không |
| **FR-039** | WHERE email tồn tại và tài khoản không bị cấm, THE system SHALL sinh token `RESET_PASSWORD` hết hạn **1 giờ**, lưu dạng hash, thu hồi token `RESET_PASSWORD` cũ chưa dùng, và gửi email link `{APP_WEB_BASE_URL}/reset-password?token=...` |
| **FR-040** | THE system SHALL sinh token đặt lại bằng `SecureRandom` 32 byte Base64URL, KHÔNG tuần tự (`BUS-13`) |
| **FR-041** | THE system SHALL giới hạn **3 yêu cầu/giờ mỗi email** và **10 yêu cầu/giờ mỗi IP** (FR-093); vượt → vẫn trả **202** cùng thông báo nhưng không gửi email (không tiết lộ) |
| **FR-042** | WHEN đặt lại mật khẩu thành công (`POST /api/auth/reset-password`), THE system SHALL thu hồi token đó VÀ **toàn bộ** refresh token của user, xoá bộ đếm đăng nhập sai liên quan, đặt mốc vô hiệu phiên (FR-092), trả **204** |
| **FR-043** | WHEN đổi mật khẩu khi đã đăng nhập (`POST /api/me/password`), THE system SHALL yêu cầu mật khẩu hiện tại đúng; sai → **422** `CURRENT_PASSWORD_INCORRECT` |
| **FR-044** | WHEN đổi mật khẩu thành công, THE system SHALL xác định phiên hiện tại bằng claim JWT `sid` (`session_id`), rồi thu hồi mọi refresh token của user có `session_id <> sid` (E7). Không đặt marker FR-092; access token của phiên khác có thể còn hiệu lực đến khi hết hạn (tối đa 15 phút) |
| **FR-045** | THE system SHALL từ chối mật khẩu mới trùng mật khẩu hiện tại với **422** `PASSWORD_UNCHANGED` (cả FR-042 và FR-043) |

### Nhóm 8 — Tạm chặn nguồn đăng nhập (FR-046…FR-050) · UC-012 («extend» UC-003, UC-004)

| Mã | Yêu cầu |
|---|---|
| **FR-046** | WHEN cùng cặp email chuẩn hoá–IP sai **5 lần trong 15 phút**, THE system SHALL chặn cặp đó trong 15 phút. Khoá Redis: `auth:login-fail:{sha256(email)}:{ip}` (đếm, TTL 15 phút từ lần sai đầu), `auth:login-block:{sha256(email)}:{ip}` (TTL 15 phút). Tăng đếm bằng `INCR` nguyên tử |
| **FR-047** | WHILE cặp email–IP đang bị chặn, THE system SHALL từ chối đăng nhập với **423** `LOGIN_SOURCE_BLOCKED` kèm `blockedUntil`, kể cả khi mật khẩu đúng, **không** chạy bcrypt |
| **FR-048** | THE system SHALL đếm theo **cặp email–IP**, không khoá toàn bộ tài khoản hoặc mọi người dùng chung IP |
| **FR-049** | WHEN hết thời gian chặn, THE system SHALL cho cặp email–IP thử lại; đăng nhập thành công xoá bộ đếm |
| **FR-050** | THE system SHALL KHÔNG khoá vĩnh viễn do sai mật khẩu. Ban vĩnh viễn chỉ do `ADMIN` (FR-096). IP lấy từ `request.getRemoteAddr()`; chỉ đọc `X-Forwarded-For` khi bật `server.forward-headers-strategy=framework` sau proxy tin cậy |

### Nhóm 9 — Thông tin cá nhân và lịch sử (FR-051…FR-057) · UC-011, UC-014

| Mã | Yêu cầu |
|---|---|
| **FR-051** | THE system SHALL cho người dùng xem (`GET /api/me/profile`) và sửa (`PATCH /api/me/profile`) đúng 3 trường: `displayName`, `avatarUrl`, `hskLevel` (FR-095). Giờ nhắc học thuộc spec nhắc học |
| **FR-052** | THE system SHALL KHÔNG cho người dùng tự sửa: `email`, `plan_code`, `plan_expires_at`, role, trạng thái tài khoản. Body chứa trường không được phép → **422** `VALIDATION_FAILED` |
| **FR-053** | THE system SHALL lấy `user_id` từ JWT cho mọi endpoint `/api/me/*`, KHÔNG nhận id từ client (`BUS-01`) |
| **FR-054** | WHEN người dùng không phải `ADMIN` gọi endpoint có id người khác, THE system SHALL trả **403** `FORBIDDEN`, kể cả khi id không tồn tại |
| **FR-055** | (chuyển sang spec nhắc học) |
| **FR-056** | Ở V2, THE system SHALL chỉ cho `ADMIN` xem lịch sử đăng nhập **thành công** trong **90 ngày** gần nhất của tài khoản được chọn, lấy từ `shared.audit_logs` `action='LOGIN_SUCCESS'` (ghi từ MVP); bản ghi quá 90 ngày được job dọn |
| **FR-057** | THE system SHALL mask IP (`203.113.x.x`) và email (`ba•••@gmail.com`: giữ 2 ký tự đầu phần tên và tên miền) khi hiển thị cho quản trị (`BUS-06`, `NFR-S07`) |

### Nhóm 10 — Phân quyền RBAC (FR-058…FR-066) · feature 6.2, UC-115

| Mã | Yêu cầu |
|---|---|
| **FR-058** | THE system SHALL hỗ trợ **sáu** role: `LEARNER` · `TEACHER` · `MODERATOR` · `CONTENT_EDITOR` · `ACCOUNTANT` · `ADMIN`, lưu ở `user_roles.role_code` với `CHECK` (`AC-06`) |
| **FR-058b** | WHERE request không có token hợp lệ, THE system SHALL coi là `GUEST` — KHÔNG có dòng `user_roles`, KHÔNG có `role_code` |
| **FR-058c** | WHERE tác vụ do job định kỳ (`@Scheduled`) chạy, THE system SHALL bỏ qua `JwtFilter` và dùng quyền hệ thống. Job định kỳ KHÔNG phải actor và KHÔNG có `role_code` |
| **FR-059** | THE system SHALL cho một người dùng có **nhiều role** đồng thời; **không** role nào tự kế thừa quyền role khác |
| **FR-060** | WHEN người dùng đã đăng nhập gọi endpoint không đủ quyền, THE system SHALL trả **403** `FORBIDDEN`; chưa đăng nhập → **401** `UNAUTHENTICATED` |
| **FR-061** | THE system SHALL đọc role từ claim `roles` của JWT, KHÔNG query DB mỗi request; kiểm mốc vô hiệu phiên ở Redis (FR-092) |
| **FR-062** | WHEN `ADMIN` gán role (`POST /api/admin/users/{id}/roles`), THE system SHALL ghi `granted_by`, `granted_at` |
| **FR-063** | THE system SHALL chặn tự cấp role cho chính mình — service trả **403** `SELF_ROLE_GRANT`, DB có `CHECK (granted_by IS NULL OR granted_by <> user_id)` |
| **FR-064** | THE system SHALL ghi `audit_logs` cho mọi lần gán hoặc thu hồi role (`action='ROLE_GRANT'`/`'ROLE_REVOKE'`, `before`/`after` là danh sách role) |
| **FR-065** | WHEN `ADMIN` cấm hoặc thu hồi role của một user đang có phiên, THE system SHALL làm request tiếp theo của user đó bị từ chối, không chờ token hết hạn (FR-092) |
| **FR-066** | THE system SHALL chỉ cho `ADMIN` gán/thu hồi role. Năm role còn lại KHÔNG gán được role. Không gỡ được `ADMIN` của `ADMIN` cuối cùng còn hoạt động (**409** `REVOKE_LAST_ADMIN`); không tự thu hồi `ADMIN` của chính mình (**403** `SELF_ROLE_REVOKE`) |

### Nhóm 11 — Gói mặc định (FR-068)

| Mã | Yêu cầu |
|---|---|
| **FR-068** | THE system SHALL đặt `plan_code = 'FREE'` và `plan_expires_at = NULL` khi tạo tài khoản mới. Mọi logic gói, lượt, mã thẻ thuộc spec gói dịch vụ (credits-plans) |

> FR-067, FR-069→FR-075, FR-076→FR-082b đã **chuyển** sang spec gói dịch vụ (credits-plans); FR-083→FR-086 sang spec từ điển. Giữ số FR cũ để không đánh lại số.

### Nhóm 13 — Màn chờ xác thực (FR-087…FR-088)

| Mã | Yêu cầu |
|---|---|
| **FR-087** | WHEN đăng ký thành công, THE frontend SHALL chuyển sang SCR-004 hiện email đã gửi tới và cho **gửi lại** theo FR-012 |
| **FR-088** | WHERE người dùng mở `/verify-email?token=`, THE frontend SHALL gọi FR-009 tự động một lần. Màn này **không chặn** việc dùng hệ thống (FR-010) |

### Nhóm 14 — Chốt mâu thuẫn tài liệu (FR-089…FR-097)

Các điểm `screen-fields.md` ghi "open issue". Spec chốt như sau; tài liệu nguồn cập nhật theo ở bước khoá.

| Mã | Yêu cầu | Mâu thuẫn đã chốt |
|---|---|---|
| **FR-089** | WHERE `/api/auth/login` hoặc `/api/auth/refresh` thiếu `X-Client-Type` hoặc giá trị không thuộc `WEB`/`MOBILE`, THE system SHALL xử lý như `WEB` (chỉ đặt cookie, **không** trả token trong body) | UC-004 trả cả cookie và body — sai với BR04/05 |
| **FR-090** | WHEN đăng nhập web thành công, THE frontend SHALL chuyển tới: (1) `redirect` nếu là đường dẫn nội bộ người dùng có quyền, **hoặc** URL tuyệt đối có origin đúng bằng `VITE_GAME_ORIGIN` (trang game, spec 007) — mọi URL tuyệt đối khác bị bỏ qua để chống open redirect; (2) nếu chỉ có **một** khu vực được phép → khu vực đó (bảng §12.3); (3) nếu nhiều khu vực → SCR-001 ở trạng thái đã đăng nhập, hiện danh sách "Khu vực của bạn" để người dùng tự chọn. **Không** tự xếp hạng role | Đa role chưa định nghĩa đích đến |
| **FR-091** | THE system SHALL yêu cầu mật khẩu mới: 8–72 ký tự (giới hạn bcrypt), có ít nhất một chữ cái và một chữ số, không chứa khoảng trắng hai đầu, khác email. Thông báo: "Mật khẩu cần 8–72 ký tự, gồm chữ và số." | Độ mạnh mật khẩu chưa có |
| **FR-092** | WHEN cấm tài khoản, thu hồi role, đặt lại mật khẩu hoặc đăng xuất mọi thiết bị, THE system SHALL ghi Redis `auth:invalid-before:{userId}` với TTL 15 phút = vòng đời access token. Giá trị là JSON: cấm tài khoản (`FR-096`) → `{"invalidBeforeMs": <epochMillis>, "reason": "BANNED", "banReason": "<lý do>"}`; thu hồi role, đặt lại mật khẩu hoặc đăng xuất mọi thiết bị → `{"invalidBeforeMs": <epochMillis>, "reason": "REVOKED"}`. `JwtAuthenticationFilter` từ chối access token có `iatMs < invalidBeforeMs`: `reason=BANNED` → **403** `ACCOUNT_BANNED` kèm `banReason`; `reason=REVOKED` → **401** `SESSION_REVOKED`. Không có key hoặc `iatMs >= invalidBeforeMs` thì tiếp tục request. JWT thiếu `iatMs` bị từ chối như thiếu `sid` (**401** `UNAUTHENTICATED`). Redis lỗi khi đọc → bỏ qua marker, ghi log cảnh báo, không truy vấn DB trong filter. Một helper dùng chung chịu trách nhiệm serialize/deserialize marker để các luồng cấm/thu hồi/đăng xuất tái sử dụng. Client làm mới token (refresh vẫn hợp lệ ở trường hợp thu hồi role) để nhận role mới | Thu hồi role không có cơ chế vô hiệu token cũ |
| **FR-093** | THE system SHALL giới hạn quên mật khẩu 10 yêu cầu/giờ mỗi IP (khoá `auth:forgot:ip:{ip}`) và 3/giờ mỗi email (`auth:forgot:email:{sha256}`) | Giới hạn IP chưa có số |
| **FR-094** | WHEN token xác thực đã dùng và tài khoản **đã** xác thực, THE system SHALL trả **200** `{ "emailVerified": true }` (idempotent). Mọi trường hợp lỗi khác dùng một mã `INVALID_VERIFY_TOKEN` (400) | UC-002 400/410/409 vs FR-011 |
| **FR-095** | THE system SHALL chuẩn hoá email: bỏ khoảng trắng hai đầu, chữ thường, độ dài ≤ 255, đúng định dạng RFC 5322 đơn giản. Hồ sơ: `displayName` 2–100 ký tự; `avatarUrl` URL `https://` ≤ 500 ký tự hoặc `null` (không có upload ở MVP); `hskLevel` 1–9 hoặc `null` (cấp mục tiêu) | Tên field hồ sơ lệch UC/FR; avatar upload chưa có |
| **FR-096** | THE system SHALL cho `ADMIN` cấm (`POST /api/admin/users/{id}/ban`, `reason` 5–500 ký tự bắt buộc) và gỡ cấm (`DELETE /api/admin/users/{id}/ban`); cấm ghi `banned_at`, `banned_by`, `ban_reason`, thu hồi toàn bộ refresh token, FR-092; gỡ cấm xoá ba trường; cả hai ghi `audit_logs`. Không cấm chính mình (**403** `SELF_BAN`); không cấm `ADMIN` cuối cùng còn hoạt động (**409** `BAN_LAST_ADMIN`). Tạm khoá có thời hạn **không** có ở MVP | Hành động hạn chế tài khoản chưa chốt |
| **FR-097** | THE system SHALL cho `ADMIN` tìm tài khoản (`GET /api/admin/users`) theo `q` (khớp một phần email hoặc tên, không phân biệt hoa thường, ≥ 2 ký tự), lọc `role`, `status` ∈ `ACTIVE`·`UNVERIFIED`·`BANNED`; phân trang `page` (từ 0), `size` 1–50 (mặc định 20), sắp `createdAt` giảm dần; email trả về **đã mask** (FR-057). Xem chi tiết `GET /api/admin/users/{id}` trả email **đầy đủ** và ghi `audit_logs` `action='VIEW_USER_PII'` | Field danh sách người dùng chưa là hợp đồng |

### Key Entities

| Bảng | Vai trò trong feature này |
|---|---|
| `auth.users` | Tài khoản + trạng thái. Cột gói tạo đủ ở V1 nhưng logic thuộc spec gói dịch vụ |
| `auth.user_roles` | Bảng nối user ↔ role, `role_code` dùng `CHECK` |
| `auth.auth_tokens` | Gộp 3 loại token: `REFRESH`, `RESET_PASSWORD`, `EMAIL_VERIFY` |
| `shared.audit_logs` | Ghi gán/thu hồi role, cấm/gỡ cấm, đăng nhập thành công, tái dùng token, xem PII |

---

## 4 · Data Model

> DDL đầy đủ nằm ở `docs/reference/database.md` và được viết lại vào `V1__core_schema.sql` (§0.2).
> Mục này chỉ ghi cột và ràng buộc feature này **dùng trực tiếp**.

### 4.1 · `auth.users`

```sql
id                 BIGSERIAL PRIMARY KEY
email              VARCHAR(255) NOT NULL UNIQUE      -- luôn lưu dạng đã chuẩn hoá (FR-095)
password_hash      VARCHAR(255) NOT NULL
display_name       VARCHAR(100) NOT NULL
avatar_url         TEXT
hsk_level          SMALLINT CHECK (hsk_level BETWEEN 1 AND 9)
email_verified_at  TIMESTAMPTZ          -- NULL = chưa xác thực
suspended_at       TIMESTAMPTZ          -- chỉ đọc ở spec này
suspended_until    TIMESTAMPTZ
banned_at          TIMESTAMPTZ
banned_by          BIGINT REFERENCES auth.users(id)
ban_reason         TEXT
reminder_time      TIME                 -- spec nhắc học
reminder_channels  VARCHAR(50) NOT NULL DEFAULT 'WEB'
plan_code          VARCHAR(20) NOT NULL DEFAULT 'FREE' REFERENCES learning.plans(code)
plan_expires_at    TIMESTAMPTZ
free_usage         JSONB NOT NULL DEFAULT '{}'
usage_reset_at     TIMESTAMPTZ NOT NULL DEFAULT now()
created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()

CONSTRAINT ck_users_ban_reason  CHECK (banned_at IS NULL OR ban_reason IS NOT NULL)
CONSTRAINT ck_users_plan_expiry CHECK (
  (plan_code = 'FREE' AND plan_expires_at IS NULL)
  OR (plan_code <> 'FREE' AND plan_expires_at IS NOT NULL))
```

> **`free_usage`, `usage_reset_at` — spec này KHÔNG map vào entity** (chốt 2026-10-20). Cột có sẵn trong V1, có
> `DEFAULT` nên tạo user vẫn chạy; `ddl-auto: validate` không kiểm cột không map. `free_usage JSONB` là bộ đếm ghi
> thường xuyên → trái `AC-09` (JSONB chỉ cho dữ liệu chỉ đọc). Spec gói dịch vụ (credits-plans) thiết kế lại cách đếm
> lượt free (bảng đếm riêng) và bỏ hai cột này bằng migration mới.

**Trạng thái suy ra** (không lưu cột riêng): `BANNED` nếu `banned_at IS NOT NULL`; `UNVERIFIED` nếu `email_verified_at IS NULL`; còn lại `ACTIVE`. `ADMIN` "còn hoạt động" = có role `ADMIN` và `banned_at IS NULL`.

### 4.2 · `auth.user_roles`

```sql
user_id    BIGINT      NOT NULL REFERENCES auth.users(id)
role_code  VARCHAR(20) NOT NULL
granted_by BIGINT      REFERENCES auth.users(id)
granted_at TIMESTAMPTZ NOT NULL DEFAULT now()
PRIMARY KEY (user_id, role_code)
CONSTRAINT ck_ur_self      CHECK (granted_by IS NULL OR granted_by <> user_id)
CONSTRAINT ck_ur_role_code CHECK (role_code IN
  ('LEARNER','TEACHER','MODERATOR','CONTENT_EDITOR','ACCOUNTANT','ADMIN'))
CREATE INDEX ix_ur_role ON auth.user_roles(role_code, user_id);
```

### 4.3 · `auth.auth_tokens`

```sql
id         BIGSERIAL PRIMARY KEY
user_id    BIGINT       NOT NULL REFERENCES auth.users(id)
token_type VARCHAR(20)  NOT NULL CHECK (token_type IN ('REFRESH','RESET_PASSWORD','EMAIL_VERIFY'))
token_hash VARCHAR(255) NOT NULL UNIQUE      -- SHA-256 hex, không lưu token thô
expires_at TIMESTAMPTZ  NOT NULL
revoked_at TIMESTAMPTZ
created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
CREATE INDEX ix_tokens_user ON auth.auth_tokens(user_id) WHERE revoked_at IS NULL;
```

### 4.4 · `shared.audit_logs` — giá trị `action` feature này ghi

| `action` | `entity` | `before` / `after` |
|---|---|---|
| `BOOTSTRAP_ADMIN` | `user:{id}` | — / `{roles}` |
| `LOGIN_SUCCESS` | `user:{id}` | — / `{clientType, ipMasked}` |
| `TOKEN_REUSE` | `user:{id}` | — / `{ipMasked}` |
| `ROLE_GRANT` · `ROLE_REVOKE` | `user:{id}` | `{roles}` / `{roles}` |
| `USER_BAN` · `USER_UNBAN` | `user:{id}` | `{bannedAt, reason}` / `{bannedAt, reason}` |
| `VIEW_USER_PII` | `user:{id}` | — / — |

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Nguyên nhân | FR |
|---|---|---|---|
| `VALIDATION_FAILED` | 422 | Dữ liệu sai định dạng, kèm `details` | FR-005, FR-052, FR-091, FR-095 |
| `EMAIL_ALREADY_EXISTS` | 409 | Email đã đăng ký | FR-004 |
| `INVALID_CREDENTIALS` | 401 | Sai mật khẩu **hoặc** email không tồn tại — một thông báo | FR-017, FR-018 |
| `LOGIN_SOURCE_BLOCKED` | 423 | Cặp email–IP đang bị chặn; kèm `blockedUntil` | FR-047 |
| `ACCOUNT_BANNED` | 403 | Tài khoản bị cấm; kèm `banReason` | FR-020, FR-092 |
| `ACCOUNT_SUSPENDED` | 403 | `suspended_until > now()`; kèm `suspendedUntil` | FR-021 |
| `ACCOUNT_UNVERIFIED` | 403 | Chưa xác thực, thao tác đổi điểm | FR-010 |
| `INVALID_VERIFY_TOKEN` | 400 | Token xác thực sai/hết hạn/đã thu hồi | FR-011 |
| `EMAIL_ALREADY_VERIFIED` | 409 | Xin gửi lại email xác thực khi đã xác thực | FR-012 |
| `INVALID_RESET_TOKEN` | 400 | Token đặt lại sai/hết hạn/đã dùng | FR-042 |
| `UNAUTHENTICATED` | 401 | Không có token hợp lệ | FR-027, FR-060 |
| `TOKEN_EXPIRED` | 401 | Access token hết hạn — client gọi refresh | FR-037 |
| `SESSION_REVOKED` | 401 | Token phát trước mốc vô hiệu phiên | FR-092 |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh token không tồn tại/hết hạn | FR-031 |
| `TOKEN_REUSE_DETECTED` | 401 | Refresh token đã thu hồi bị dùng lại | FR-032 |
| `CURRENT_PASSWORD_INCORRECT` | 422 | Mật khẩu hiện tại sai khi đổi | FR-043 |
| `PASSWORD_UNCHANGED` | 422 | Mật khẩu mới trùng mật khẩu hiện tại | FR-045 |
| `FORBIDDEN` | 403 | Không đủ role, hoặc truy cập dữ liệu người khác | FR-054, FR-060 |
| `SELF_ROLE_GRANT` · `SELF_ROLE_REVOKE` · `SELF_BAN` | 403 | Thao tác lên chính mình | FR-063, FR-066, FR-096 |
| `ROLE_ALREADY_GRANTED` | 409 | Role đã có | E8 |
| `ROLE_NOT_GRANTED` | 404 | Thu hồi role người đó không có | FR-066 |
| `REVOKE_LAST_ADMIN` · `BAN_LAST_ADMIN` | 409 | Đụng `ADMIN` cuối cùng còn hoạt động | FR-066, FR-096 |
| `USER_NOT_FOUND` | 404 | Chỉ ở endpoint `/api/admin/users/{id}` (người gọi là `ADMIN`) | FR-097 |
| `CSRF_TOKEN_INVALID` | 403 | Request đổi trạng thái bằng cookie thiếu/sai CSRF | §6, `HR-05` |
| `TOO_MANY_REQUESTS` | 429 | Vượt giới hạn gửi lại email; kèm `retryAfterSeconds` | FR-012 |
| `INTERNAL_ERROR` | 500 | Lỗi không lường trước — chỉ trả `request_id`, không chi tiết | `HR-09` |

---

## 6 · Architectural Constraints (AC Mapping)

| Ràng buộc | Feature này tuân thủ thế nào |
|---|---|
| **HR-01** Mật khẩu bcrypt | FR-003 |
| **HR-02** Không secret trong code | `JWT_SECRET`, SMTP đọc từ biến môi trường (§0.3) |
| **HR-03** Cookie đủ 5 thuộc tính | FR-014, FR-014b, FR-015 |
| **HR-04** CORS không dùng `*` | FR-028 |
| **HR-05** CSRF | Double-submit: cookie `XSRF-TOKEN` (không `HttpOnly`, cùng `Domain`) + header `X-XSRF-TOKEN` bắt buộc cho mọi `POST/PUT/PATCH/DELETE` **xác thực bằng cookie**; miễn cho `/api/auth/login`, `/register`, `/forgot-password`, `/reset-password`, `/verify-email`, `/refresh` và request dùng header (FR-024). `GET /api/auth/csrf` cấp cookie |
| **HR-07** Validate mọi input | FR-005, FR-091, FR-095 (Bean Validation trên DTO `record`) |
| **HR-09** Không lộ stack trace | §0.2 định dạng lỗi |
| **AC-01** API First | `contracts/openapi-auth.yaml` có trước khi code; test so khớp |
| **AC-03** Module gọi nhau qua `api` | Spec khác dùng `auth.api.AuthApi`: `currentUser()`, `requireVerifiedEmail(userId)`, `hasRole(userId, role)` |
| **AC-04** Schema | 4 schema theo `database.md` (RFC, xem §9 câu 6) |
| **AC-06** Enum = VARCHAR + CHECK | `role_code`, `token_type` |
| **BUS-01** Kiểm quyền sở hữu | FR-053, FR-054 |
| **BUS-02** Cùng transaction | FR-002, FR-031 |
| **BUS-06** Mask PII | FR-057, FR-097 |
| **BUS-13** `SecureRandom` | FR-016, FR-040 |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Đăng nhập (gồm bcrypt) | < 500 ms p95 |
| `NFR-P02` | Kiểm token mỗi request (JWT + 1 lần đọc Redis FR-092) | < 10 ms p95 |
| `NFR-P03` | `GET /api/auth/me` | < 100 ms p95 |
| `NFR-S01` | bcrypt cost 12 — không hạ để đạt mốc nhanh hơn | |
| `NFR-S02` | Token đặt lại/xác thực/refresh: `SecureRandom` 32 byte, chỉ lưu hash | |
| `NFR-S03` | Đăng nhập sai: 5 lần/15 phút mỗi cặp email–IP (FR-046) · gửi lại email: 3/giờ · quên mật khẩu: 3/giờ/email, 10/giờ/IP | |
| `NFR-S04` | CSRF cho mọi request đổi trạng thái bằng cookie (§6 HR-05) | |
| `NFR-S05` | CORS khai origin tường minh cho web và trang game | |
| `NFR-S06` | Lỗi trả `{error_code, message, request_id}` | |
| `NFR-S07` | Log KHÔNG chứa mật khẩu, token, email người dùng (log id) | |
| `NFR-C01` | 50 người dùng đồng thời (demo) | |
| `NFR-A01` | SMTP lỗi → đăng ký vẫn thành công (FR-007) | |
| `NFR-A02` | Redis không phản hồi → đăng nhập vẫn chạy (bỏ qua đếm sai, ghi log cảnh báo); FR-092 coi như không có mốc | |

---

## 8 · Constitution Compliance Check

| Kiểm tra | Kết quả |
|---|---|
| Vi phạm `HR-*` / `AC-*` / `BUS-*`? | Không |
| `TODO` trong spec? | Không — điểm mở ở §9 |
| Mọi FR map được test? | Có — §14 |
| Có logic mastery / cây tri thức? | Không |
| Có logic gói, lượt, mã thẻ? | Không — chỉ FR-068 đặt mặc định `FREE` |

---

## 9 · Open Questions

| # | Câu hỏi | Trạng thái |
|---|---|---|
| 1 | HS256 hay RS256 | **Chốt:** HS256 (ADR-001) |
| 3 | Vòng đời token | **Chốt:** access 15 phút · refresh 30 ngày · xoay vòng (ADR-002) |
| 4 | Lịch sử đăng nhập lưu đâu | **Chốt:** `shared.audit_logs` `action='LOGIN_SUCCESS'` |
| 6 | Schema: `public` hay tách | **Chốt (chủ dự án 2026-10-06):** 4 schema + FK cross-schema được phép (RFC `AC-03`/`AC-04` trong `database.md`) — **cả nhóm duyệt RFC cùng spec; sau duyệt sửa `constitution.md`** |
| 7 | Đích đến khi có nhiều role (FR-090) | Đã chốt 2026-10-23: theo FR-090/AT-72 |

Không còn câu hỏi chặn code.

---

## 10 · Frontend Implementation Note

Chi tiết màn ở §12. **Ba trạng thái bắt buộc** cho mọi màn có dữ liệu động: Loading · Empty · Error, tiếng Việt có dấu.
Mockup tham khảo giao diện: `C:\CLHSK\cnhsk-mockup-79\screens\scr-0xx-*.html` (ngoài repo, chỉ để nhìn bố cục).

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Access token còn sống tối đa 15 phút nếu Redis mất mốc vô hiệu phiên | Redis lỗi là sự cố hiếm; NFR-A02 ưu tiên đăng nhập vẫn chạy |
| Đếm sai theo cặp email–IP: kẻ tấn công đổi IP để thử tiếp | Chặn khoá toàn tài khoản thì người dùng thật bị khoá oan (UC-012). Giám sát `audit_logs` |
| `COOKIE_DOMAIN=localhost` ở dev không chia sẻ cookie giữa hai cổng | Dev dùng `localhost` cho cả `5173` và `5174`: trình duyệt gửi cookie theo host, không theo cổng — đủ cho dev |

---

## 12 · Màn hình

### 12.1 · Danh sách

| SCR | Tên | Route | Guard | API | Field chi tiết |
|---|---|---|---|---|---|
| SCR-001 | Landing (phần tài khoản) | `/` | Không | `GET /api/auth/me` (biết đã đăng nhập) | `screen-fields.md` dòng 118 |
| SCR-002 | Sign In | `/login?redirect=` | Chỉ khách; đã đăng nhập → FR-090 | `POST /api/auth/login` | dòng 475 |
| SCR-003 | Register | `/register` | Chỉ khách | `POST /api/auth/register` | dòng 944 |
| SCR-004 | Verify Email | `/verify-email?token=` | Không | `POST /api/auth/verify-email`, `POST /api/auth/verify-email/resend` | dòng 1450 |
| SCR-005 | Forgot Password | `/forgot-password` | Chỉ khách | `POST /api/auth/forgot-password` | dòng 1843 |
| SCR-006 | Reset Password | `/reset-password?token=` | Không | `POST /api/auth/reset-password` | dòng 2164 |
| SCR-007 | User Profile | `/me/profile` | `LEARNER` | `GET/PATCH /api/me/profile` | dòng 2567 |
| SCR-008 | Change Password | `/me/password` | Đã đăng nhập | `POST /api/me/password` | dòng 3113 |
| SCR-076 | User List | `/admin/users` | `ADMIN` | `GET /api/admin/users` | dòng 38044 |
| SCR-077 | User Detail | `/admin/users/:id` | `ADMIN` | `GET /api/admin/users/{id}`, `POST/DELETE .../roles`, `POST/DELETE .../ban` | dòng 38501 |
| SCR-079 | Access Denied | `/403` | Không | — | dòng 39615 |

Field, nhãn, validate, thông báo lấy **đúng** từ `screen-fields.md`. Chỗ `screen-fields.md` ghi "open issue" → dùng quyết định ở Nhóm 14.
SCR-007 chỉ có 3 field của FR-051/FR-095; nút "Cài đặt nhắc học" dẫn tới SCR-009 (ngoài phạm vi — ẩn ở đợt này).

### 12.2 · Hành vi chung frontend

| Thành phần | Hành vi |
|---|---|
| `shared/api/client.ts` | `fetch` với `credentials: 'include'`, header `X-Client-Type: WEB`, `X-XSRF-TOKEN` lấy từ cookie `XSRF-TOKEN` cho request đổi trạng thái. Nhận 401 `TOKEN_EXPIRED`/`SESSION_REVOKED` → gọi `POST /api/auth/refresh` **một lần** (gộp các request song song chờ chung một lần refresh) rồi gửi lại; refresh lỗi → xoá trạng thái, chuyển `/login?redirect=` |
| `AuthProvider` | Khởi động gọi `GET /api/auth/me`; giữ `user {id, email, displayName, emailVerified, roles}` trong context; không lưu token ở `localStorage` |
| `RequireRole roles={[...]}` | Chưa đăng nhập → `/login?redirect=`; thiếu role → SCR-079 |
| Banner chưa xác thực | Mọi trang khu vực người học hiện banner "Xác minh email để dùng đầy đủ" khi `emailVerified=false`, có nút sang SCR-004 |
| Lỗi | Hiện `message` từ API; 500 → "Có lỗi xảy ra, mã: {request_id}" |

### 12.3 · Khu vực theo role (FR-090)

| Role | Khu vực | Màn mặc định |
|---|---|---|
| `LEARNER` | Học tập | SCR-010 (chưa có ở đợt này → tạm SCR-011 `/learn/topics`) |
| `TEACHER` | Giáo viên | SCR-056 (chưa có → SCR-079 với thông báo "đang xây dựng") |
| `MODERATOR` | Kiểm duyệt | SCR-060 (V2) |
| `CONTENT_EDITOR` | Nội dung | SCR-068 (spec nhập dữ liệu) |
| `ACCOUNTANT` | Kế toán | SCR-073 (spec gói dịch vụ) |
| `ADMIN` | Quản trị | SCR-076 |

Màn đích chưa tồn tại thì dùng màn tạm như bảng; cập nhật khi spec tương ứng xong.

---

## 13 · API Contract

Hợp đồng đầy đủ: [`contracts/openapi-auth.yaml`](contracts/openapi-auth.yaml) (OpenAPI 3.1). Tóm tắt:

| Method · Path | Auth | Request | Response thành công | Lỗi chính |
|---|---|---|---|---|
| `GET /api/auth/csrf` | Không | — | 204 + cookie `XSRF-TOKEN` | — |
| `POST /api/auth/register` | Không | `{email, password, confirmPassword, displayName, acceptTerms}` | 201 `{userId, email, emailVerified:false}` | 409, 422 |
| `POST /api/auth/verify-email` | Không | `{token}` | 200 `{emailVerified:true}` | 400 |
| `POST /api/auth/verify-email/resend` | Có | — | 202 | 409 `EMAIL_ALREADY_VERIFIED`, 429 |
| `POST /api/auth/login` | Không | `{email, password}` + `X-Client-Type` | 200 `{user}` (+ cookie ở WEB) · `{user, accessToken, refreshToken, accessTokenExpiresIn}` ở MOBILE | 401, 403, 422, 423 |
| `POST /api/auth/refresh` | Refresh | WEB: cookie · MOBILE: `{refreshToken}` | 200 như login | 401 |
| `POST /api/auth/logout` | Có | MOBILE: `{refreshToken}` | 204 | 401 |
| `POST /api/auth/logout-all` | Có | — | 204 | 401 |
| `POST /api/auth/forgot-password` | Không | `{email}` | 202 | 422 |
| `POST /api/auth/reset-password` | Không | `{token, newPassword, confirmPassword}` | 204 | 400, 422 |
| `GET /api/auth/me` | Có | — | 200 `{id, email, displayName, emailVerified, roles[], hskLevel, avatarUrl}` | 401 |
| `GET /api/me/profile` | Có | — | 200 `{displayName, avatarUrl, hskLevel, email, emailVerified}` | 401 |
| `PATCH /api/me/profile` | Có | `{displayName?, avatarUrl?, hskLevel?}` | 200 profile | 422 |
| `POST /api/me/password` | Có | `{currentPassword, newPassword, confirmPassword}` | 204 | 422 |
| `GET /api/admin/users` | `ADMIN` | `q, role, status, page, size` | 200 `{items[{id, emailMasked, displayName, roles, status, createdAt}], page, size, totalElements}` | 403, 422 |
| `GET /api/admin/users/{id}` | `ADMIN` | — | 200 `{id, email, displayName, roles[{code, grantedBy, grantedAt}], status, bannedAt, banReason, emailVerifiedAt, createdAt}` | 403, 404 |
| `POST /api/admin/users/{id}/roles` | `ADMIN` | `{roleCode}` | 204 | 403, 404, 409 |
| `DELETE /api/admin/users/{id}/roles/{roleCode}` | `ADMIN` | — | 204 | 403, 404, 409 |
| `POST /api/admin/users/{id}/ban` | `ADMIN` | `{reason}` | 204 | 403, 404, 409, 422 |
| `DELETE /api/admin/users/{id}/ban` | `ADMIN` | — | 204 | 403, 404 |

`user` trong login = shape của `GET /api/auth/me`.

---

## 14 · Acceptance Tests

Tên test chứa mã FR (vd `FR014_webLogin_setsAccessCookieWithFiveAttributes`). Integration test dùng Testcontainers (§0.2).
**G** = Given · **W** = When · **T** = Then.
Không có AT: FR-055 (đã chuyển), FR-056 (V2), FR-058c (spec này không có job định kỳ — kiểm ở spec đầu tiên có `@Scheduled`).

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001, FR-002, FR-068 | G email mới · W register hợp lệ · T 201; có 1 dòng `users` (`plan_code=FREE`, `email_verified_at` null) + 1 dòng `user_roles` `LEARNER` |
| AT-02 | FR-002 | G lỗi ghi `user_roles` (giả lập) · W register · T không còn dòng `users` nào (rollback) |
| AT-03 | FR-003 | W register · T `password_hash` bắt đầu `$2a$12$` |
| AT-04 | FR-004, E1 | G email đã có (khác hoa thường, có khoảng trắng) · W register · T 409 `EMAIL_ALREADY_EXISTS` |
| AT-05 | E1 | W 2 request register cùng email song song · T đúng 1 thành công, 1 nhận 409 |
| AT-06 | FR-005, FR-091 | W register mật khẩu `abcdefgh` · T 422, `details[0].field = password` |
| AT-07 | FR-005 | W register `acceptTerms=false`, mật khẩu không khớp, displayName 1 ký tự · T 422 có đủ 3 field trong `details` |
| AT-08 | FR-006 | W register · T có 1 token `EMAIL_VERIFY` hết hạn sau 24h ± 1 phút; Mailpit nhận 1 email chứa `/verify-email?token=` |
| AT-09 | FR-007, NFR-A01 | G SMTP không kết nối được · W register · T 201, tài khoản tồn tại |
| AT-10 | FR-008 | W register + login + đặt lại mật khẩu · T log không chứa mật khẩu, token thô, email |
| AT-11 | FR-009 | G token xác thực hợp lệ · W verify · T 200, `email_verified_at` có giá trị, token bị thu hồi |
| AT-12 | FR-010 | G chưa xác thực · W gọi `AuthApi.requireVerifiedEmail` · T ném `ACCOUNT_UNVERIFIED` 403; W login · T 200 |
| AT-13 | FR-011 | G token hết hạn / không tồn tại · W verify · T 400 `INVALID_VERIFY_TOKEN` |
| AT-14 | FR-094, E10 | G đã xác thực bằng token X · W verify lại token X · T 200 `{emailVerified:true}` |
| AT-15 | FR-012 | W resend 4 lần trong 1 giờ · T lần 4 trả 429 có `retryAfterSeconds` |
| AT-15b | FR-012 | G đã xác thực · W resend · T 409 `EMAIL_ALREADY_VERIFIED` |
| AT-16 | FR-013 | W resend · T token cũ có `revoked_at`, token mới hợp lệ |
| AT-17 | FR-014, FR-015 | W login WEB · T `Set-Cookie access_token` có `Domain`, `HttpOnly`, `Secure` (theo cấu hình), `SameSite=Lax`, `Max-Age=900` |
| AT-18 | FR-014b | W login WEB · T cookie `refresh_token` có `Path=/api/auth`, `Max-Age=2592000` |
| AT-19 | FR-016 | W login · T `auth_tokens.token_hash` = SHA-256 của token trong cookie; JWT có `sub`, `roles`, `ev`, `jti`, ký HS256 |
| AT-20 | FR-017, FR-018 | W login sai mật khẩu; W login email không tồn tại · T cả hai 401 `INVALID_CREDENTIALS`, cùng `message` |
| AT-21 | FR-019 | G 3 lần sai · W login đúng · T khoá đếm Redis bị xoá |
| AT-22 | FR-020 | G bị cấm · W login đúng · T 403 `ACCOUNT_BANNED` kèm `banReason` |
| AT-23 | FR-021 | G `suspended_until` ngày mai · W login · T 403 `ACCOUNT_SUSPENDED` kèm `suspendedUntil` |
| AT-24 | FR-022 | W login `X-Client-Type: MOBILE` · T body có `accessToken`, `refreshToken`; **không** có `Set-Cookie` |
| AT-25 | FR-023 | G token hợp lệ · W gọi `/api/auth/me` bằng cookie; bằng header · T cả hai 200 |
| AT-26 | FR-024 | W `PATCH /api/me/profile` bằng header, không CSRF · T 200 |
| AT-27 | §6 HR-05 | W `PATCH /api/me/profile` bằng cookie, không CSRF · T 403 `CSRF_TOKEN_INVALID`; có CSRF · T 200 |
| AT-28 | FR-025, FR-089 | W login không có `X-Client-Type` · T như WEB: có cookie, body không có token |
| AT-29 | FR-026, FR-027 | G cookie từ login · W `GET /api/auth/me` với `Origin: game` · T 200 đúng user |
| AT-30 | FR-027 | W `GET /api/auth/me` không token · T 401 `UNAUTHENTICATED` |
| AT-31 | FR-028, FR-029 | W preflight từ origin game · T `Access-Control-Allow-Origin` = origin đó, `Allow-Credentials: true`; từ origin lạ · T không có header CORS |
| AT-32 | FR-030 | W gọi endpoint bằng header `Authorization` không cookie · T 200 |
| AT-33 | FR-031 | W refresh WEB · T cookie mới, token cũ `revoked_at` có giá trị |
| AT-34 | FR-031 | W refresh MOBILE với body · T body có token mới |
| AT-35 | FR-032, E3 | G refresh token đã rotate · W dùng lại · T 401 `TOKEN_REUSE_DETECTED`, mọi refresh token của user bị thu hồi, có `audit_logs TOKEN_REUSE` |
| AT-36 | FR-033 | T DB không có cột nào chứa token thô (kiểm giá trị cookie ≠ mọi `token_hash`) |
| AT-37 | FR-034 | W logout WEB · T 2 cookie `Max-Age=0`, refresh token phiên bị thu hồi |
| AT-38 | FR-035 | W logout MOBILE với `refreshToken` · T token đó bị thu hồi |
| AT-39 | FR-036 | G 2 phiên · W logout-all · T mọi refresh token bị thu hồi; access token cũ → 401 `SESSION_REVOKED` |
| AT-40 | FR-037 (FE) | G access hết hạn · W gọi API · T client gọi refresh 1 lần rồi gửi lại thành công; 3 request song song → chỉ 1 lần refresh |
| AT-41 | FR-038 | W forgot-password email có và không có · T cả hai 202 cùng `message` |
| AT-42 | FR-039 | G email có · W forgot · T token `RESET_PASSWORD` hết hạn 1h, token cũ chưa dùng bị thu hồi, Mailpit nhận email |
| AT-43 | FR-039 | G tài khoản bị cấm · W forgot · T 202, không gửi email |
| AT-44 | FR-040 | T token đặt lại dài ≥ 43 ký tự Base64URL, hai lần sinh khác nhau |
| AT-45 | FR-041, FR-093 | W forgot 4 lần cùng email trong 1h · T lần 4 vẫn 202 nhưng không gửi email; 11 lần cùng IP · T lần 11 không gửi |
| AT-46 | FR-042 | W reset hợp lệ · T 204, mọi refresh token bị thu hồi, token reset bị thu hồi, đăng nhập được bằng mật khẩu mới |
| AT-47 | FR-042 | W reset với token đã dùng · T 400 `INVALID_RESET_TOKEN` |
| AT-48 | FR-043 | W đổi mật khẩu với `currentPassword` sai · T 422 `CURRENT_PASSWORD_INCORRECT` |
| AT-49 | FR-044, E7 | G 2 phiên · W đổi mật khẩu từ phiên A · T refresh của phiên B bị thu hồi, phiên A vẫn refresh được |
| AT-50 | FR-045 | W đổi/đặt lại mật khẩu trùng mật khẩu hiện tại · T 422 `PASSWORD_UNCHANGED` |
| AT-51 | FR-046, FR-047 | W 5 lần sai cùng email+IP · T lần 6 (kể cả đúng mật khẩu) 423 `LOGIN_SOURCE_BLOCKED` có `blockedUntil` |
| AT-52 | FR-048 | G cặp email–IP1 bị chặn · W login cùng email từ IP2 · T được xử lý bình thường |
| AT-53 | FR-049 | G bị chặn · W đợi TTL (đồng hồ giả) · T login đúng → 200, đếm bị xoá |
| AT-54 | FR-050 | W 50 lần sai · T không có cột nào trong `users` bị đổi |
| AT-55 | FR-051, FR-095 | W PATCH `{displayName:"Minh", hskLevel:3}` · T 200, DB đổi đúng 2 trường |
| AT-56 | FR-052 | W PATCH có `email` hoặc `roles` · T 422 |
| AT-57 | FR-053 | W PATCH kèm `userId` của người khác trong body · T 422 (trường không cho phép), dữ liệu người khác không đổi |
| AT-58 | FR-054 | G `LEARNER` · W `GET /api/admin/users/{id bất kỳ}` (có hoặc không tồn tại) · T 403 |
| AT-59 | FR-057 | W `GET /api/admin/users` · T email dạng `ba•••@gmail.com` |
| AT-60 | FR-058 | W insert `role_code='HACKER'` trực tiếp · T DB từ chối (CHECK) |
| AT-61 | FR-058b | W gọi endpoint công khai không token · T không truy vấn `user_roles` |
| AT-62 | FR-059 | G user có `LEARNER`+`TEACHER` · W login · T JWT `roles` có cả hai; endpoint `ADMIN` → 403 |
| AT-63 | FR-060 | G `LEARNER` · W gọi `/api/admin/users` · T 403 `FORBIDDEN`; không token → 401 |
| AT-64 | FR-061 | W gọi 100 request có token · T không có truy vấn SQL `user_roles` (đếm bằng log SQL) |
| AT-65 | FR-062, FR-064 | G `ADMIN` · W gán `TEACHER` · T `granted_by` = id admin; có `audit_logs ROLE_GRANT` |
| AT-66 | FR-063 | W admin gán role cho chính mình · T 403 `SELF_ROLE_GRANT` |
| AT-67 | E8 | W gán role đã có · T 409 `ROLE_ALREADY_GRANTED` |
| AT-68 | FR-065, FR-092 | G user B đang có access token chứa `TEACHER` · W admin thu hồi `TEACHER` · T request kế tiếp của B → 401 `SESSION_REVOKED`; B refresh → JWT mới không có `TEACHER` |
| AT-69 | FR-066 | G chỉ còn 1 `ADMIN` hoạt động · W thu hồi `ADMIN` của người đó (bởi admin khác bị cấm/không có) · T 409 `REVOKE_LAST_ADMIN`; tự thu hồi → 403 `SELF_ROLE_REVOKE` |
| AT-70 | FR-066 | G `CONTENT_EDITOR` · W gọi gán role · T 403 |
| AT-71 | FR-087, FR-088 (FE) | W đăng ký trên SCR-003 · T chuyển SCR-004 hiện email; mở `/verify-email?token=` → gọi verify đúng 1 lần |
| AT-72 | FR-090 (FE) | G user chỉ `ADMIN` → SCR-076; user `LEARNER`+`TEACHER` → SCR-001 có "Khu vực của bạn"; có `redirect=/admin/users` nhưng không phải `ADMIN` → bỏ qua redirect; `redirect=http://localhost:5174/play` (origin game) → chuyển đúng URL đó; `redirect=https://evil.com` → bỏ qua |
| AT-73 | FR-096, E5 | G B có phiên · W admin cấm B lý do "spam" · T request kế tiếp của B 403 `ACCOUNT_BANNED`; refresh của B bị thu hồi; có `audit_logs USER_BAN`; gỡ cấm → B đăng nhập được |
| AT-74 | FR-096 | W admin tự cấm mình · T 403 `SELF_BAN`; cấm `ADMIN` cuối cùng → 409 `BAN_LAST_ADMIN`; `reason` 3 ký tự → 422 |
| AT-75 | FR-097 | G 30 tài khoản · W `?q=minh&size=10&page=1` · T đúng trang 2, sắp `createdAt` giảm dần, `totalElements` đúng; `q` 1 ký tự → 422 |
| AT-76 | FR-097 | W admin xem chi tiết · T email đầy đủ; có `audit_logs VIEW_USER_PII` |
| AT-77 | §0.2 | G không có `ADMIN`, có 2 biến bootstrap · W khởi động · T có 1 `ADMIN`; khởi động lần 2 · T không tạo thêm |
| AT-78 | §0.2 | W request lỗi bất kỳ · T body có đủ `error_code`, `message`, `request_id`; header `X-Request-Id` khớp; không có `trace` |
| AT-79 | §0.3 | G `JWT_SECRET` 10 ký tự · W khởi động · T app dừng với thông báo rõ |
| AT-80 | NFR-A02 | G Redis dừng · W login đúng · T 200, log cảnh báo |

---

## 15 · Gói việc cho Codex

Chi tiết task, file, lệnh kiểm: [`tasks.md`](tasks.md). Thứ tự: đợt 0 nền tảng → hợp đồng OpenAPI → entity → đăng ký/xác thực → đăng nhập 3 client → vòng đời token → hồ sơ/mật khẩu → quản trị người dùng/role → frontend → kiểm thử tổng.

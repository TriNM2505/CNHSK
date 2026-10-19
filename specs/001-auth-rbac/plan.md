# Implementation Plan: Xác thực, tài khoản và phân quyền — Spec #001

> Nguồn: [`spec.md`](spec.md) v1.1.0 · [`checklists/requirements.md`](checklists/requirements.md)
> **Migration:** `V1__core_schema` (`V2__community` khong lien quan spec nay)
> **Module:** `auth` + nền `shared` · **Trạng thái:** chưa code

---

## 1 · Architectural Approach

Nền tảng xác thực cho **ba client dùng chung một backend**. Điểm khó nhất không phải JWT mà là
**ba cách truyền token khác nhau** trên cùng một bộ endpoint:

| Client | Token truyền bằng | CSRF |
|---|---|---|
| Web `cnhsk.com` | Cookie `Domain=cnhsk.com` · `HttpOnly` · `Secure` · `SameSite=Lax` | **Bắt buộc** |
| Web game `game.cnhsk.com` | **Cùng cookie đó** (tên miền phụ tự nhận) | Bắt buộc |
| Mobile React Native | Header `Authorization: Bearer` | Không cần |
| Mobile chơi game (WebView) | Header — app tiêm trước khi trang chạy | Không cần |

`JwtFilter` đọc **cookie trước, không có thì đọc header** (FR-023). Thiếu một nhánh thì một
loại client không đăng nhập được, và lỗi chỉ hiện trên thiết bị thật.

Chống brute force: Redis đếm theo **cặp email chuẩn hóa–IP** bằng thao tác nguyên tử và TTL (FR-048).
Sau 5 lần sai trong 15 phút, chặn cặp đó 15 phút; không khóa toàn bộ tài khoản hoặc người dùng chung IP.

Refresh token **rotation + reuse detection**: dùng lại token đã thu hồi → thu hồi **toàn bộ**
token của user (FR-032). Token chỉ lưu dạng hash (FR-033).

RBAC đọc role từ JWT, không query DB mỗi request (FR-061). Trái quyền → **403**, không phải
401 hay 404 (FR-060) — kể cả khi id không tồn tại, để không tiết lộ id nào có thật.

Gói dịch vụ đặt **trên `users`** (`plan_code`, `plan_expires_at`, `free_usage`) chứ không tách
bảng `user_subscriptions`: gói được đọc cùng lúc với mọi lần kiểm quyền, tách ra là thêm JOIN
vào đường nóng nhất.

---

## 2 · Components

Tên class và file khớp `tasks.md`. `…/` = `src/main/java/com/cnhsk/`.

| Thành phần | Trách nhiệm | FR | Task |
|---|---|---|---|
| `shared.exception.*`, `RequestIdFilter` | Định dạng lỗi, `request_id` | §0.2 | T-03 |
| `shared.config.CnhskProperties`, `SecurityConfig` | Cấu hình, filter chain, CORS, CSRF double-submit | FR-024, FR-028→029, FR-060 | T-04, T-10 |
| `shared.audit.AuditService` | Ghi `shared.audit_logs` | FR-064, FR-096, FR-097 | T-05 |
| `shared.security.JwtService`, `JwtAuthenticationFilter` | JWT HS256; đọc cookie **rồi** header; kiểm mốc vô hiệu phiên | FR-016, FR-023, FR-061, FR-092 | T-08, T-09 |
| `auth.entity.*`, `auth.repository.*` | `User`, `UserRole`, `AuthToken` | §4 | T-07 |
| `RegistrationService`, `EmailVerificationService`, `MailService` | Đăng ký, xác thực email | FR-001→013, FR-094 | T-13→T-15 |
| `LoginService`, `LoginAttemptService`, `CookieFactory` | Đăng nhập 3 client, chặn email–IP | FR-014→025, FR-046→050, FR-089 | T-11, T-16, T-17 |
| `RefreshTokenService`, `SessionInvalidationService` | Rotation, tái dùng, đăng xuất | FR-031→036, FR-092 | T-19, T-20 |
| `PasswordResetService`, `ProfileService` | Quên/đặt lại/đổi mật khẩu, hồ sơ | FR-038→045, FR-051→053 | T-21, T-22 |
| `AdminUserQueryService`, `RoleService`, `BanService` | Quản trị tài khoản, role, cấm | FR-054, FR-057→066, FR-096, FR-097 | T-23→T-25 |
| `auth.api.AuthApi` | Lớp `api` cho module khác | FR-010, `AC-03` | T-27 |

Gói dịch vụ, lượt free, mã thẻ (`PlanService`, `PlanLookup`) **đã chuyển** sang spec gói dịch vụ (credits-plans).

> **`AuthApi` phải làm trước các module khác.** Xong T-27 thì bỏ `allowEmptyShould(true)` trong `ModuleBoundaryTest`.

---
## 3 · Dependencies

### 3.1 · Migration

T-01 viết lại `V1__core_schema.sql` + `V2__community.sql` khớp `database.md`: 4 schema, bảng `plans`,
4 cột gói trên `users` (`plan_code` · `plan_expires_at` · `free_usage` · `usage_reset_at`), bỏ bảng `roles`,
`CHECK` cho `role_code`. Repo chưa deploy nên viết lại V1 được (`AC-05` chỉ cấm sửa migration **đã chạy**).

Đã verify trên pg-mem: 100/104 lệnh chạy, 4 lỗi còn lại là giới hạn pg-mem
(`gen_random_uuid`, `OVER`) — PostgreSQL 18 có cả hai.

### 3.2 · Thư viện

Đã có trong `pom.xml`: `spring-boot-starter-web` · `data-jpa` · `security` · `validation` ·
`flyway` · `postgresql` · `jjwt 0.12.6` · `archunit 1.5.0`.

**Cần thêm (T-02):** `jacoco-maven-plugin` (80%, `ES-05`) · `spring-boot-starter-data-redis` ·
`spring-boot-starter-mail` · `springdoc-openapi-starter-webmvc-ui` · `testcontainers` (postgresql, junit-jupiter).

### 3.3 · Feature phụ thuộc spec này

**Mọi** spec còn lại. `001` là nền tảng: không có `AuthApi` thì không module nào biết
người dùng là ai.

---

## 4 · 🔴 Hai lỗi cấu hình phải sửa TRƯỚC khi viết dòng code đầu tiên

Phát hiện khi lập plan. Không sửa thì ngày đầu code sẽ không chạy được, và mất thời gian
tìm nguyên nhân.

### 4.1 · Flyway không tìm thấy file migration

`application.yaml` **không khai** `spring.flyway.locations`, nên Flyway dùng mặc định
`classpath:db/migration` → tức `src/main/resources/db/migration/`, thư mục này **đang rỗng**.

Ba file SQL thật nằm ở `db/migration/` **ngoài** `src`.

Hai cách sửa:

| Cách | Việc |
|---|---|
| **A** (đề xuất) | Chuyển 3 file SQL vào `src/main/resources/db/migration/` |
| B | Thêm `locations: filesystem:db/migration` vào `application.yaml` |

Chọn **A**: migration là tài nguyên của ứng dụng, đóng gói cùng JAR khi deploy. Cách B làm
file SQL không có trong JAR, deploy ra server là Flyway không thấy gì.

### 4.2 · `schemas` khai 3 nhưng SQL không dùng prefix

```yaml
flyway:
  schemas: learning,auth,community
  default-schema: learning
```

Nhưng `V1__` tạo **24 bảng không có schema prefix** → tất cả vào `learning`. Hai schema
`auth` và `community` được tạo rỗng và không dùng.

`AC-04` ghi *"Một database `cnhsk_db`, ba schema (`auth` 4 bảng, `learning` 40 bảng,
`community` 15 bảng)"* — con số này là của **DB v5 (59 bảng)**, đã bị thay thế.

#### Quyết định của chủ dự án (2026-10-06): **giữ schema, thêm prefix vào SQL**

Lý do giữ: bảo toàn kiến trúc đã chốt — sơ đồ `kien-truc.md` map 1-1
`module → schema`, và 3 schema là thứ có thể bị hỏi khi bảo vệ.

Hai điều chỉnh kèm theo:

| # | Điều chỉnh | Vì sao |
|---|---|---|
| 1 | **Bốn** schema, không phải ba — thêm `shared` cho `audit_logs` | `audit_logs` ghi hành vi của cả ba module nghiệp vụ, không thuộc riêng module nào. Bốn schema khớp đúng **4 module Java** đã có |
| 2 | **Nới `AC-03`**: cho phép FK cross-schema, cấm module đọc bảng module khác qua repository | Có **17 FK** cắt ranh giới module (`community.posts → auth.users`, `contests → exams`…). FK là *ràng buộc toàn vẹn*, không phải *truy cập dữ liệu*. Bỏ FK để tuân thủ tuyệt đối sẽ mất toàn vẹn — đúng lỗi các bảng `_refs` của v5 đã gặp |

Phân bố: `auth` 3 · `learning` 22 · `community` 5 · `shared` 1.

> ⚠️ **Cần RFC** sửa `AC-03` + `AC-04` theo §Governance. Việc chặn, phải làm trước Pha 4.
>
> 🔴 **Rủi ro không verify được trước:** pg-mem cho `CREATE SCHEMA` chạy nhưng **bỏ qua
> prefix** — không mô phỏng schema thật. Nên không chứng minh được 17 FK cross-schema hoạt
> động trước khi chạy PostgreSQL 18 thật (`T-00e`). Ba thứ dễ sai nhất: thứ tự tạo schema
> trong Flyway · `search_path` cho `svc_app` · `@Table(schema=...)` thiếu ở một entity
> (lỗi runtime, không lỗi compile).

---

## 5 · Risks & Mitigations

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Cookie thiếu `Domain=cnhsk.com` → game không đăng nhập được | **Cao** | Test tích hợp mở `game.cnhsk.com` sau khi login ở `cnhsk.com` (SC-002) |
| `JwtFilter` chỉ đọc cookie → mobile hỏng | **Cao** | Test cả 3 client trong một suite (SC-001) |
| Refresh token bị trộm | Cao | Rotation + reuse detection thu hồi toàn bộ (FR-032) |
| Brute force tài khoản `ADMIN` | Cao | Chặn cặp email–IP sau 5 lần sai/15 phút; theo dõi log để phát hiện tấn công phân tán (FR-046…FR-049) |
| User enumeration qua thông báo lỗi | TB | Cùng một message cho email sai và mật khẩu sai (FR-018) |
| Lộ id qua status code | TB | 403 cho cả id không tồn tại (FR-054) |
| `ModuleBoundaryTest` còn `allowEmptyShould(true)` | TB | Bỏ flag ngay khi `auth` có class đầu tiên |
| bcrypt cost 12 làm login chậm ~250ms | Thấp | **Có chủ đích.** `NFR-P01` đặt 500ms p95 đã tính phần này. Không hạ cost |
| Coverage 80% khó đạt | TB | Đã nêu lo ngại, chủ dự án quyết 80%. Nếu tuần 8 không đạt thì xem lại mục này **trước** khi cắt feature |

---

## 6 · Questions for Human

| # | Câu hỏi | Chặn | Đề xuất |
|---|---|---|---|
| 1 | JWT HS256 hay RS256? | — | **Chốt:** HS256 |
| 2 | Access/refresh sống bao lâu? | — | **Chốt:** 15 phút / 30 ngày, xoay vòng |
| 3 | Lịch sử đăng nhập lưu ở đâu? | — | **Chốt:** `audit_logs` `action='LOGIN_SUCCESS'` |
| 4 | RFC `AC-03`/`AC-04` (4 schema, FK cross-schema) | T-01 | Cả nhóm duyệt cùng spec; sau duyệt sửa `constitution.md` |
| 5 | Redis đặt ở đâu | — | **Chốt:** Redis 7 trong `docker-compose.yml`; spec 001 dùng cho đếm sai, giới hạn gửi, mốc vô hiệu phiên |

---

## 7 · Constitution Check

| Lớp | Trọng tâm spec này | Trạng thái |
|---|---|---|
| `HR-01` bcrypt ≥12 | FR-003 | sẽ PASS |
| `HR-02` không secret trong code | JWT secret từ biến môi trường | sẽ PASS |
| `HR-03` cookie đủ 5 thuộc tính | FR-014, FR-015 | sẽ PASS |
| `HR-04` CORS không `*` | FR-028 | sẽ PASS |
| `HR-05` CSRF | double-submit cho request dùng cookie (spec §6) | sẽ PASS |
| `HR-09` không lộ stack trace | `GlobalExceptionHandler` | sẽ PASS |
| `AC-01` API First | OpenAPI **trước** khi code | chờ |
| `AC-03` module qua lớp `api` | `AuthApi` | sẽ PASS |
| **`AC-04` ba schema** | §4.2 — 4 schema theo RFC `database.md` | ⏳ chờ duyệt RFC |
| `AC-05` `ddl-auto: validate` | đã đúng trong `application.yaml` | PASS |
| `AC-06` enum VARCHAR+CHECK | `role_code`, `token_type`, `plan_code` | PASS |
| `BUS-01` kiểm quyền sở hữu | FR-053, FR-054 | sẽ PASS |
| `BUS-02` cùng transaction | FR-002 | sẽ PASS |
| `BUS-06` mask PII | FR-057 | sẽ PASS |
| `BUS-08` giờ Việt Nam | không áp dụng (gói/nhắc học đã chuyển spec khác) | — |
| `BUS-13` `SecureRandom` | FR-040 | sẽ PASS |
| `ES-05` coverage 80% | cần thêm `jacoco` | chờ |

> ⏳ **`AC-04` chờ duyệt RFC** cùng buổi duyệt spec. Không bắt đầu T-01 trước khi RFC được duyệt.

# Implementation Plan: Xác thực, RBAC và gói dịch vụ — Spec #001

> Nguồn: [`spec.md`](spec.md) v1.0 (82 FR) · [`checklists/requirements.md`](checklists/requirements.md)
> **Migration:** `V1__core_schema` (`V2__community` khong lien quan spec nay)
> ⚠️ Bang `plans` + 4 cot goi tren `users` **chua co trong SQL** — cho `database.md` duyet xong moi gen
> **Module:** `auth` (+ `plans` thuộc `learning`) · **Trạng thái:** chưa code

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

Chống brute force: đếm `failed_login_count` **theo tài khoản**, không theo IP (FR-048) —
tránh người dùng chung NAT làm khoá lẫn nhau. Khoá tạm 15 phút sau 5 lần sai.

Refresh token **rotation + reuse detection**: dùng lại token đã thu hồi → thu hồi **toàn bộ**
token của user (FR-032). Token chỉ lưu dạng hash (FR-033).

RBAC đọc role từ JWT, không query DB mỗi request (FR-061). Trái quyền → **403**, không phải
401 hay 404 (FR-060) — kể cả khi id không tồn tại, để không tiết lộ id nào có thật.

Gói dịch vụ đặt **trên `users`** (`plan_code`, `plan_expires_at`, `free_usage`) chứ không tách
bảng `user_subscriptions`: gói được đọc cùng lúc với mọi lần kiểm quyền, tách ra là thêm JOIN
vào đường nóng nhất.

---

## 2 · Components

Thư mục `src/main/java/com/cnhsk/auth/` đã có sẵn khung `api` · `controller` · `dto` ·
`entity` · `repository` · `service`.

| Thành phần | Trách nhiệm | File | FR |
|---|---|---|---|
| `AuthController` | register · verify · login · refresh · logout · forgot · reset | `auth/controller/AuthController.java` | FR-001…FR-045 |
| `ProfileController` | xem/sửa thông tin cá nhân, lịch sử đăng nhập | `auth/controller/ProfileController.java` | FR-051…FR-057 |
| `RoleController` | gán/thu hồi role (chỉ `ADMIN`) | `auth/controller/RoleController.java` | FR-058…FR-066 |
| `AuthService` | đăng ký, đăng nhập, lockout, rotation | `auth/service/AuthService.java` | FR-001…FR-050 |
| `TokenService` | sinh/verify/thu hồi `auth_tokens` (3 loại) | `auth/service/TokenService.java` | FR-016, FR-031…FR-037 |
| `RoleService` | gán role + ghi `audit_logs` | `auth/service/RoleService.java` | FR-062…FR-066 |
| `PlanService` | gói hiện tại, hạn mức, reset lượt tháng | `learning/payment/PlanService.java` | FR-067…FR-075 |
| `AuthApi` | **lớp `api`** cho module khác gọi — trả `UserId`, `Set<Role>` | `auth/api/AuthApi.java` | `AC-03` |
| `PlanLookup` | **lớp `api`** của `learning` cho `auth` đọc gói | `learning/api/PlanLookup.java` | `AC-03` |
| `JwtTokenProvider` | sinh/verify JWT HS256 | `shared/security/JwtTokenProvider.java` | FR-061 |
| `JwtFilter` | đọc cookie **rồi** header | `shared/security/JwtFilter.java` | FR-023 |
| `SecurityConfig` | filter chain · default deny · CORS tường minh | `shared/config/SecurityConfig.java` | FR-028, FR-029, FR-060 |
| `CsrfConfig` | CSRF cho thao tác đổi tiền, bỏ qua client dùng header | `shared/config/CsrfConfig.java` | FR-024, `HR-05` |
| `User` · `UserRole` · `AuthToken` · `Plan` | entity, enum-as-String | `auth/entity/*`, `learning/payment/entity/Plan.java` | `AC-06` |
| `GlobalExceptionHandler` | `{error_code, message, request_id}` | `shared/exception/GlobalExceptionHandler.java` | `HR-09` |

> **`AuthApi` phải làm trước các module khác.** `ModuleBoundaryTest` đang bật
> `allowEmptyShould(true)` vì chưa có code; khi `auth` có class đầu tiên, rule sẽ hoạt động
> thật và mọi vi phạm ranh giới bị chặn ngay.

---

## 3 · Dependencies

### 3.1 · Migration

`V1__core_schema.sql` (`users` · `user_roles` · `auth_tokens` · `audit_logs`) và
**Chưa có trong SQL** — chờ `database.md` duyệt xong: bảng `plans`, 4 cột gói trên
`users` (`plan_code` · `plan_expires_at` · `free_usage` · `usage_reset_at`), bỏ bảng `roles`.

Đã verify trên pg-mem: 100/104 lệnh chạy, 4 lỗi còn lại là giới hạn pg-mem
(`gen_random_uuid`, `OVER`) — PostgreSQL 18 có cả hai.

### 3.2 · Thư viện

Đã có trong `pom.xml`: `spring-boot-starter-web` · `data-jpa` · `security` · `validation` ·
`flyway` · `postgresql` · `jjwt 0.12.6` · `archunit 1.5.0`.

**Cần thêm:** `jacoco-maven-plugin` (coverage 80% theo `ES-05`).

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
| Brute force tài khoản `ADMIN` | Cao | Lockout 5 lần/15 phút theo tài khoản (FR-046…FR-049) |
| User enumeration qua thông báo lỗi | TB | Cùng một message cho email sai và mật khẩu sai (FR-018) |
| Lộ id qua status code | TB | 403 cho cả id không tồn tại (FR-054) |
| `ModuleBoundaryTest` còn `allowEmptyShould(true)` | TB | Bỏ flag ngay khi `auth` có class đầu tiên |
| bcrypt cost 12 làm login chậm ~250ms | Thấp | **Có chủ đích.** `NFR-P01` đặt 500ms p95 đã tính phần này. Không hạ cost |
| Coverage 80% khó đạt | TB | Đã nêu lo ngại, chủ dự án quyết 80%. Nếu tuần 8 không đạt thì xem lại mục này **trước** khi cắt feature |

---

## 6 · Questions for Human

| # | Câu hỏi | Chặn | Đề xuất |
|---|---|---|---|
| 1 | JWT HS256 hay RS256? | FR-016, FR-061 | **HS256** — một backend duy nhất, không cần khoá công khai |
| 2 | Access/refresh sống bao lâu? | FR-031 | **15 phút / 30 ngày** |
| 3 | Lịch sử đăng nhập lưu ở đâu? | FR-056 | `audit_logs` với `action='LOGIN'`, **không** thêm bảng |
| 4 | Sửa `AC-04` (bỏ 3 schema) — cần RFC | §4.2 | Đồng ý thì tôi viết RFC |
| 5 | `TODO(REDIS_PLACEMENT)` | chưa chặn spec này | Spec 001 không dùng Redis |

---

## 7 · Constitution Check

| Lớp | Trọng tâm spec này | Trạng thái |
|---|---|---|
| `HR-01` bcrypt ≥12 | FR-003 | sẽ PASS |
| `HR-02` không secret trong code | JWT secret từ biến môi trường | sẽ PASS |
| `HR-03` cookie đủ 5 thuộc tính | FR-014, FR-015 | sẽ PASS |
| `HR-04` CORS không `*` | FR-028 | sẽ PASS |
| `HR-05` CSRF thao tác tiền | FR-073 | sẽ PASS |
| `HR-09` không lộ stack trace | `GlobalExceptionHandler` | sẽ PASS |
| `AC-01` API First | OpenAPI **trước** khi code | chờ |
| `AC-03` module qua lớp `api` | `AuthApi`, `PlanLookup` | sẽ PASS |
| **`AC-04` ba schema** | **§4.2 — xung đột, cần RFC** | 🔴 **FAIL** |
| `AC-05` `ddl-auto: validate` | đã đúng trong `application.yaml` | PASS |
| `AC-06` enum VARCHAR+CHECK | `role_code`, `token_type`, `plan_code` | PASS |
| `BUS-01` kiểm quyền sở hữu | FR-053, FR-054 | sẽ PASS |
| `BUS-02` cùng transaction | FR-002 | sẽ PASS |
| `BUS-06` mask PII | FR-057 | sẽ PASS |
| `BUS-08` giờ Việt Nam | FR-055, FR-071, FR-074 | sẽ PASS |
| `BUS-13` `SecureRandom` | FR-040 | sẽ PASS |
| `ES-05` coverage 80% | cần thêm `jacoco` | chờ |

> 🔴 **Một FAIL: `AC-04`.** Phải giải quyết bằng RFC trước Pha 4, không được code rồi sửa sau.

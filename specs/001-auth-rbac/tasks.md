# Tasks: Xác thực, tài khoản và phân quyền — Spec #001

> Nguồn: [`spec.md`](spec.md) v1.1.0 · [`contracts/openapi-auth.yaml`](contracts/openapi-auth.yaml) · [`plan.md`](plan.md)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 36 task** — mỗi task ≤ 4 giờ, **một task = một nhánh `agent/001-<task>` = một PR**.
Đường dẫn Java viết tắt: `…/` = `src/main/java/com/cnhsk/`; test `t/` = `src/test/java/com/cnhsk/`.

### Quy ước cho mọi task

1. Đọc `AGENTS.md` → `spec.md` (mục ghi ở cột *Spec*) → OpenAPI. Không sửa file ngoài cột *File*.
2. Mỗi FR trong task có test tên bắt đầu bằng mã FR, vd `FR031_refresh_rotatesToken`. Test lấy kịch bản từ AT tương ứng ở `spec.md` §14.
3. Xong khi: test của task pass · `./mvnw verify` xanh (BE) hoặc `npm run lint && npm run typecheck && npm test` xanh (FE).
4. Spec không nói → dừng, ghi `specs/001-auth-rbac/questions.md`. Không tự giả định.
5. Commit: `feat(auth): FR-0xx <mô tả>`. Không nhắc AI.

---

## Đợt 0 · Nền tảng (§0)

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-00 | Hạ tầng dev: `docker-compose.yml` (PostgreSQL 18, Redis 7, Mailpit), `.env.example` đủ biến §0.3, README 5 lệnh chạy | `docker-compose.yml`, `.env.example`, `README.md` | §0.2, §0.3 | ✅ |
| T-01 | Migration: chuyển vào `src/main/resources/db/migration/`; viết lại `V1__core_schema.sql`, `V2__community.sql` khớp `database.md` (4 schema, `user_roles` có `CHECK` + `ix_ur_role`, bỏ `roles`, cột gói ở `users`). Xoá `db/migration/` cũ. Cấu hình `spring.flyway.schemas` | 2 file SQL, `application.yaml` | §0.2, §4 | ✅ leader dựng DB chung 2026-10-20 — V1 gộp luôn `plans` (seed `FREE`), `pron_stages`, `videos`, cột phễu, `seq`; Flyway chạy bằng `DB_MIGRATION_USER` (`database.md` §11) |
| T-02 | `pom.xml`: `jacoco-maven-plugin` (80%), `springdoc-openapi-starter-webmvc-ui`, `testcontainers` (postgresql, junit-jupiter), `spring-boot-starter-mail`, `spring-boot-starter-data-redis`. `AbstractIntegrationTest` (PostgreSQL 18 + Redis 7 + GreenMail hoặc Mailpit container) | `pom.xml`, `t/support/AbstractIntegrationTest.java` | §0.2 | ✅ |
| T-03 | Lớp lỗi: `ErrorCode` (mọi mã §5), `ApiException`, `GlobalExceptionHandler` (gồm Bean Validation → 422 `details`, mọi lỗi khác → 500 `INTERNAL_ERROR`), `RequestIdFilter` (MDC + header) | `…/shared/exception/*`, `…/shared/config/RequestIdFilter.java` | §0.2 · AT-78 | ✅ |
| T-04 | Cấu hình chung: `CnhskProperties` (`@ConfigurationProperties` cho JWT, cookie, CORS, web base URL), kiểm `JWT_SECRET` ≥ 32 byte khi khởi động, bean `Clock`, Jackson UTC | `…/shared/config/*` | §0.3 · AT-79 | ✅ |
| T-05 | `AuditService.record(actorId, action, entity, before, after)` ghi `shared.audit_logs` + entity/repository; hàm `Masking.email()`, `Masking.ip()` | `…/shared/audit/*`, `…/shared/util/Masking.java` | §4.4, FR-057 | ✅ |
| T-06 | Hợp đồng: kiểm `contracts/openapi-auth.yaml` hợp lệ (plugin `openapi-generator` validate hoặc test đọc bằng `swagger-parser`); test hợp đồng so `/v3/api-docs` với file nguồn cho path + status code | `pom.xml`, `t/contract/AuthContractTest.java` | §13 · `AC-01` | ✅ |

> T-01 là điểm khoá: sai migration thì `ddl-auto: validate` làm mọi entity hỏng. Chạy `./mvnw verify` với Testcontainers trước khi mở PR.

---

## Đợt 1 · Entity + bảo mật nền

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | Entity `User` (cột §4.1 **trừ** `free_usage`, `usage_reset_at` — không map, xem §4.1; trạng thái suy ra `status()`), `UserRole` (`@IdClass`), `AuthToken`; enum `RoleCode`, `TokenType` lưu `@Enumerated(STRING)`; 3 repository (gồm `countActiveAdmins()`, tìm kiếm có `Pageable`) | `…/auth/entity/*`, `…/auth/repository/*` | §4.1–4.3 · AT-60 | ✅ |
| T-08 | `JwtService`: phát JWT HS256 (`sub`, `roles`, `ev`, `iat`, `exp`, `jti`), verify, phân biệt hết hạn (`TOKEN_EXPIRED`) với không hợp lệ | `…/shared/security/JwtService.java` | FR-016, FR-061 | ✅ |
| T-09 | `JwtAuthenticationFilter`: **cookie `access_token` trước, rồi `Authorization: Bearer`**; kiểm mốc Redis JSON `{invalidBeforeMs, reason, banReason?}` theo `iatMs` (thiếu `iatMs` → 401; Redis lỗi → bỏ qua, log cảnh báo); helper dùng chung ghi/đọc marker; ghi `AuthenticatedUser` vào `SecurityContext` | `…/shared/security/*` | FR-023, FR-061, FR-092, NFR-A02 · AT-25, AT-32, AT-64 | ✅ |
| T-10 | `SecurityConfig`: mặc định từ chối; danh sách endpoint công khai; 401/403 qua `ErrorCode` — **bắt buộc** cấu hình `AuthenticationEntryPoint` và `AccessDeniedHandler` (gồm lỗi CSRF của `CsrfFilter`) trả JSON `{error_code, message, request_id}` với `UNAUTHENTICATED` / `TOKEN_EXPIRED` / `SESSION_REVOKED` / `FORBIDDEN` / `CSRF_TOKEN_INVALID` — mặc định Spring trả body rỗng, FE (`client.ts`) dựa vào `error_code` để tự làm mới phiên và lấy lại CSRF; CORS từ cấu hình (không `*`, `allowCredentials`); CSRF double-submit `XSRF-TOKEN` chỉ cho request dùng cookie, miễn các path ở §6; `GET /api/auth/csrf` | `…/shared/config/SecurityConfig.java`, `…/auth/controller/CsrfController.java` | FR-024, FR-028, FR-029, FR-060 · AT-26, AT-27, AT-31 + test: request thiếu token / sai CSRF trả đúng JSON có `error_code` | ✅ |
| T-11 | `CookieFactory`: tạo/xoá `access_token`, `refresh_token` (`Path=/api/auth`), `XSRF-TOKEN` đúng thuộc tính | `…/auth/service/CookieFactory.java` | FR-014, FR-014b, FR-015, FR-034 · AT-17, AT-18 | ✅ |
| T-12 | `TokenHasher` (SHA-256 hex) + `SecureTokenGenerator` (32 byte Base64URL); `RateLimiter` Redis (`INCR` + `EXPIRE` nguyên tử bằng Lua hoặc `SET NX EX` + `INCR`) | `…/shared/security/*` | FR-033, FR-040, NFR-S02 · AT-36, AT-44 | ✅ |

> **T-09 là task rủi ro cao nhất.** Sai thứ tự đọc token thì một loại client không đăng nhập được; lỗi chỉ hiện trên thiết bị thật. Test phải có cả ba: chỉ cookie, chỉ header, có cả hai (cookie thắng).

---

## Đợt 2 · Đăng ký + xác thực email

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-13 | `MailService`: gửi email xác thực và đặt lại mật khẩu (template văn bản tiếng Việt), chạy **sau commit** (`@TransactionalEventListener(AFTER_COMMIT)`), SMTP lỗi chỉ log | `…/auth/service/MailService.java`, `…/auth/event/*` | FR-006, FR-007 · AT-08, AT-09 | ✅ |
| T-14 | `POST /api/auth/register`: DTO + validate (FR-091, FR-095), chuẩn hoá email, bcrypt 12, tạo user + `LEARNER` + `FREE` một transaction; email trùng → 409; tạo token `EMAIL_VERIFY` hash, hạn 24 giờ và phát event gửi mail sau commit | `…/auth/controller/AuthController.java`, `…/auth/service/RegistrationService.java`, `…/auth/dto/*`, `…/auth/validation/*` | FR-001→FR-006, FR-008, FR-068, FR-091, FR-095 · AT-01→AT-08, AT-10 | ✅ |
| T-15 | `POST /api/auth/verify-email` (idempotent FR-094), `POST /api/auth/verify-email/resend` (3/giờ; Redis lỗi → fail-closed, 500 `INTERNAL_ERROR`). `AuthApi.requireVerifiedEmail` thuộc T-27 theo `plan.md` | `…/auth/service/EmailVerificationService.java`, controller | FR-009→FR-013, FR-094 · AT-11→AT-16 | ✅ |

---

## Đợt 3 · Đăng nhập ba client

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-16 | `POST /api/auth/login`: đọc `X-Client-Type` (thiếu/lạ → WEB), kiểm chặn email–IP **trước** bcrypt, bcrypt hash giả khi email không tồn tại, kiểm cấm/tạm khoá, tạo UUID `session_id`, phát access JWT có `sid=session_id` và refresh token cùng session, WEB đặt cookie / MOBILE trả body, ghi `LOGIN_SUCCESS` | `…/auth/service/LoginService.java`, controller | FR-014→FR-025, FR-089 · AT-17→AT-24, AT-28; T-22 bổ sung kiểm thử `sid` | ✅ |
| T-17 | Chặn nguồn đăng nhập: `LoginAttemptService`; phần còn lại sau T-16 là AT-21 (xóa bộ đếm khi login thành công), AT-51→AT-54 và AT-80 (Redis dừng, login đúng vẫn 200 + WARN) | `…/auth/service/LoginAttemptService.java` | FR-046→FR-050 · AT-21, AT-51→AT-54, AT-80 | T-17a ✅ · T-17 ✅ |
| T-18 | `GET /api/auth/me` (shape §13) | controller, `…/auth/service/CurrentUserService.java` | FR-026, FR-027 · AT-29, AT-30 | ✅ |

---

## Đợt 4 · Vòng đời token và mật khẩu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-19 | `POST /api/auth/refresh`: rotation một transaction (`SELECT … FOR UPDATE` dòng token), giữ nguyên `session_id`/JWT `sid`; token revoked có token mới hơn cùng session → reuse, thu hồi tất cả + `TOKEN_REUSE`; revoked không có successor → `INVALID_REFRESH_TOKEN`; WEB cookie / MOBILE body | `…/auth/service/RefreshTokenService.java` | FR-031→FR-033 · AT-33→AT-36; T-22 bổ sung regression theo session | ✅ |
| T-20 | `logout`, `logout-all`, `LogoutService`; dùng helper marker `shared.security.SessionInvalidationService` | `…/auth/service/LogoutService.java`, `AuthController.java`, repository | FR-034→FR-036, FR-092 · AT-37→AT-39 | ✅ |
| T-21 | `forgot-password` (luôn 202; giới hạn 3/giờ/email, 10/giờ/IP; bỏ qua tài khoản bị cấm) + `reset-password` (thu hồi mọi refresh, FR-092, `PASSWORD_UNCHANGED`) | `…/auth/service/PasswordResetService.java` | FR-038→FR-042, FR-045, FR-093 · AT-41→AT-47, AT-50 | ✅ |
| T-22 | Migration V2_1 thêm `auth.auth_tokens.session_id`; login tạo UUID và JWT `sid`, refresh rotation giữ nguyên; `GET/PATCH /api/me/profile` (3 field, trường lạ → 422); `POST /api/me/password` xác định phiên hiện tại bằng `sid=session_id`, xác thực mật khẩu hiện tại, thu hồi refresh token của phiên khác (không marker FR-092) | `V2_1__auth_session_id.sql`, `AuthToken`, `AuthTokenRepository`, `LoginService`, `RefreshTokenService`, `JwtService`, `AuthenticatedUser`, `JwtAuthenticationFilter`, `MeController`, `ProfileService` | FR-016, FR-031→FR-045, FR-051→FR-053 · AT-48→AT-50, AT-55→AT-57; migration, sid, refresh-reuse và concurrency regressions | ✅ |

---

## Đợt 5 · Quản trị tài khoản và role

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-23 | `GET /api/admin/users` (tìm, lọc, phân trang, email mask) + `GET /api/admin/users/{id}` (email đầy đủ, `VIEW_USER_PII`) | `…/auth/controller/AdminUserController.java`, `…/auth/service/AdminUserQueryService.java` | FR-054, FR-057, FR-097 · AT-58, AT-59, AT-75, AT-76 | ✅ |
| T-24 | Gán/thu hồi role: chặn tự cấp/tự thu hồi `ADMIN`, bảo vệ `ADMIN` cuối cùng bằng advisory lock dùng chung T-25, xử lý 409 trùng, ghi `audit_logs`; revoke ghi marker FR-092 `invalidBeforeMs` trước DB commit | `…/auth/service/RoleService.java`, `AdminGuardService`, controller | FR-058→FR-066, FR-092 · AT-60→AT-70 | ✅ |
| T-25 | Cấm/gỡ cấm: `reason` 5–500, chặn tự cấm và `ADMIN` cuối cùng, thu hồi refresh, FR-092, `audit_logs` | `…/auth/service/BanService.java`, controller | FR-020, FR-096 · AT-22, AT-73, AT-74 | ✅ |
| T-26 | `BootstrapAdminRunner` (`ApplicationRunner`) | `…/auth/service/BootstrapAdminRunner.java` | §0.2 · AT-77 | ✅ |
| T-27 | `auth.api.AuthApi` (`currentUser()`, `requireVerifiedEmail(userId)`, `hasRole(userId, role)`, `displayNames(userIds)`); bỏ `allowEmptyShould(true)` ở rule đã có lớp, giữ tạm cho package rỗng | `…/auth/api/*`, `…/auth/service/AuthApiService.java`, `t/architecture/ModuleBoundaryTest.java` | FR-010 · `AC-03` · AT-12; spec 002 FR-026 | ✅ |

---

## Đợt 6 · Frontend `cnhsk-web`

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-28 | Khởi tạo `cnhsk-web` (Vite React TS), Tailwind với token `design.md` §6, ESLint + Prettier, Vitest + Testing Library, script `lint`/`typecheck`/`test`; proxy Vite `/api` → `localhost:8080` | `cnhsk-web/**` (khung) | §0.2 | ✅ — proxy đã kiểm thật (HTTP 204) |
| T-29 | `shared/api/client.ts` (cookie, `X-Client-Type`, CSRF, refresh một lần cho request song song), `shared/auth/AuthProvider.tsx`, `RequireRole.tsx`, layout public/learner/admin, SCR-079, trang 404 | `cnhsk-web/src/shared/**`, `src/app/**` | §12.2, FR-037 · AT-40 | ✅ |
| T-30 | SCR-002 Sign In + SCR-003 Register + điều hướng FR-090 | `cnhsk-web/src/features/auth/**` | FR-087, FR-090 · AT-71, AT-72 | ✅ |
| T-31 | SCR-004 Verify Email + SCR-005 Forgot + SCR-006 Reset + banner chưa xác thực | `cnhsk-web/src/features/auth/**` | FR-088 · AT-71 | ⬜ |
| T-32 | SCR-007 Profile + SCR-008 Change Password | `cnhsk-web/src/features/account/**` | FR-051, FR-043 | ⬜ |
| T-33 | SCR-076 User List + SCR-077 User Detail (gán/thu hồi role, cấm/gỡ cấm có bước xác nhận trong trang) | `cnhsk-web/src/features/admin-users/**` | FR-096, FR-097 | ⬜ |
| T-34 | SCR-001 Landing phần tài khoản (nút đăng nhập/đăng ký, "Khu vực của bạn" khi đa role) | `cnhsk-web/src/features/landing/**` | FR-090 | ⬜ |

Mỗi màn: field, nhãn, thông báo lấy đúng `screen-fields.md` (dòng ở §12.1); đủ 3 trạng thái Loading · Empty · Error; test Vitest cho validate và xử lý lỗi API.

---

## Đợt 7 · Kiểm thử tổng

| ID | Task | Spec | TT |
|---|---|---|---|
| T-35 | Integration test luồng đủ: đăng ký → xác thực → đăng nhập WEB và MOBILE cùng tài khoản → `/me` từ origin game → refresh → đổi mật khẩu → đăng xuất; test mọi mã lỗi §5 có ít nhất một ca; coverage ≥ 80% | SC-001→SC-006 · §5 · `ES-05` | ⬜ |

---

## Thứ tự thực thi

```
T-00 → T-01 → T-02 → T-03 → T-04 → T-05 → T-06
     → T-07 → T-08 → T-09 → T-10 → T-11 → T-12
     → T-13 → T-14 → T-15 → T-17a (LoginAttemptService + kiểm thử Redis độc lập)
     → T-16 (tích hợp login; kiểm chứng AT-21/AT-80)
     → hoàn thiện kiểm thử T-17 → T-18
     → T-19 → T-20 → T-21 → T-22 → T-23 → T-24 → T-25 → T-26 → T-27
     → T-35
T-28 có thể chạy song song từ sau T-06; T-29→T-34 sau T-18 (cần API thật hoặc chạy cùng BE local)
```

**Ba task rủi ro cao:** T-09 (đọc token hai nhánh) · T-16 (cookie + chặn trước bcrypt) · T-19 (rotation + phát hiện tái dùng).

**Hiện trạng: 14/36 task xong.**
co

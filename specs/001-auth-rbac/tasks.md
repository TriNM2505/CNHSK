# Tasks: Xác thực, RBAC và gói dịch vụ — Spec #001

> Nguồn: [`spec.md`](spec.md) (82 FR) · [`plan.md`](plan.md)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 34 task** — 5 task chặn (`T-00a`…`T-00e`) + 29 task thực thi.

Phân công và ước lượng thời gian **không ghi ở đây** — xem `wbs-estimate.md`
(mã WBS `1.3.1`, `1.3.2`, `1.3.3` phủ spec này). File này chỉ trả lời **làm gì** và
**theo thứ tự nào**.

---

## Đợt 0 · Chặn — phải xong trước mọi task khác

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-00a | **RFC sửa `AC-04`**: bỏ 3 schema, dùng `public`. Cần cả nhóm duyệt | `.specify/memory/constitution.md` | `plan.md` §4.2 | 🔴 |
| T-00b | Chuyển `db/migration/*.sql` → `src/main/resources/db/migration/` | 3 file SQL | `plan.md` §4.1 | ⬜ |
| T-00c | Bỏ `flyway.schemas` khỏi `application.yaml` sau khi T-00a duyệt | `application.yaml` | `plan.md` §4.2 | ⬜ |
| T-00d | Thêm `jacoco-maven-plugin` vào `pom.xml`, ngưỡng 80% | `pom.xml` | `ES-05` | ⬜ |
| T-00e | Chạy `mvn flyway:migrate` trên PostgreSQL thật — xác nhận đủ bảng theo v7 | — | `plan.md` §3.1 | ⬜ |

> T-00a là **RFC**, không phải code. Không duyệt xong thì T-00c và mọi task entity đều treo.

---

## Đợt 1 · Nền tảng chung (`shared`)

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-01 | `GlobalExceptionHandler` — `{error_code, message, request_id}` | `shared/exception/` | `HR-09` · §5 Error Matrix | ⬜ |
| T-02 | `JwtTokenProvider` — sinh/verify HS256, secret từ env | `shared/security/` | FR-061 · `HR-02` | ⬜ |
| T-03 | `JwtFilter` — đọc **cookie trước, rồi header** | `shared/security/` | **FR-023** | ⬜ |
| T-04 | `SecurityConfig` — default deny · CORS tường minh 2 origin | `shared/config/` | FR-028, FR-029, FR-060 | ⬜ |
| T-05 | `CsrfConfig` — bật cho cookie, bỏ qua client dùng header | `shared/config/` | FR-024 · `HR-05` | ⬜ |

> **T-03 là task rủi ro cao nhất của spec này.** Sai thứ tự đọc token thì một loại client
> không đăng nhập được, và lỗi chỉ hiện trên thiết bị thật, không hiện ở unit test.

---

## Đợt 2 · Entity + Repository

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-06 | `User` entity — 22 cột, enum-as-String | `auth/entity/User.java` | §4.1 · `AC-06` | ⬜ |
| T-07 | `UserRole` entity — khoá kép, `role_code` dùng `CHECK` | `auth/entity/UserRole.java` | §4.2 | ⬜ |
| T-08 | `AuthToken` entity — 3 `token_type`, chỉ lưu hash | `auth/entity/AuthToken.java` | §4.3 · FR-033 | ⬜ |
| T-09 | `Plan` entity + `PlanRepository` | `learning/payment/entity/` | §4.4 | ⬜ |
| T-10 | Repository cho 3 entity trên | `auth/repository/` | — | ⬜ |

---

## Đợt 3 · Đăng ký + xác thực email

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-11 | `POST /api/auth/register` — tạo user + role + gói trong **1 transaction** | `AuthService` | FR-001…FR-008 | ⬜ |
| T-12 | Gửi email xác thực — SMTP lỗi **vẫn trả 201** | `AuthService` + `MailSender` | FR-006, FR-007 · `NFR-A01` | ⬜ |
| T-13 | `GET /api/auth/verify` + chặn thao tác đổi điểm khi chưa xác thực | `AuthService` | FR-009…FR-013 | ⬜ |

---

## Đợt 4 · Đăng nhập 3 client

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-14 | `POST /api/auth/login` — cookie đủ **5 thuộc tính** cho web | `AuthService` | FR-014…FR-021 · `HR-03` | ⬜ |
| T-15 | Nhánh mobile — token trong **body**, không đặt cookie | `AuthService` | FR-022…FR-025 | ⬜ |
| T-16 | `GET /api/auth/me` — cho trang game biết mình là ai | `AuthController` | **FR-027** | ⬜ |
| T-17 | Redis chặn theo cặp email–IP sau 5 lần sai/15 phút, TTL 15 phút; reset khi đăng nhập hoặc đặt lại mật khẩu thành công | `AuthService` | FR-046…FR-050 | ⬜ |

> **T-16 dễ bị bỏ sót.** Cookie `HttpOnly` nên JavaScript trang game **không đọc được**
> token — không có endpoint này thì trang game không biết đã đăng nhập hay chưa.

---

## Đợt 5 · Token lifecycle

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-18 | Refresh + **rotation + reuse detection** (thu hồi toàn bộ) | `TokenService` | FR-031…FR-033 | ⬜ |
| T-19 | Logout web (xoá cookie) · mobile (thu hồi) · mọi thiết bị | `AuthService` | FR-034…FR-036 | ⬜ |
| T-20 | Quên / đặt lại / đổi mật khẩu — `SecureRandom` ≥16 ký tự | `AuthService` | FR-038…FR-045 · `BUS-13` | ⬜ |

---

## Đợt 6 · RBAC + gói dịch vụ

| ID | Task | File | Spec ref | TT |
|---|---|---|---|---|
| T-21 | `RoleService` — gán/thu hồi role, chặn tự cấp, ghi `audit_logs` | `auth/service/RoleService.java` | FR-058…FR-066 | ⬜ |
| T-22 | `ProfileController` — xem/sửa cá nhân, **kiểm quyền sở hữu** | `auth/controller/` | FR-051…FR-055 · `BUS-01` | ⬜ |
| T-23 | `PlanService` + job reset lượt tháng (giờ Việt Nam) | `learning/payment/` | FR-067…FR-075 · `BUS-08` | ⬜ |
| T-24 | `AuthApi` + `PlanLookup` — lớp `api` cho module khác | `auth/api/`, `learning/api/` | `AC-03` | ⬜ |

> **T-24 làm cuối nhưng quan trọng nhất cho các spec sau.** Xong T-24 thì bỏ
> `allowEmptyShould(true)` trong `ModuleBoundaryTest` để rule hoạt động thật.

> **V2 — UC-014:** Chức năng `SUPER_ADMIN` xem lịch sử đăng nhập thành công
> (FR-056…FR-057) được triển khai sau MVP, không thuộc T-22.

---

## Đợt 7 · Kiểm thử

| ID | Task | Spec ref | TT |
|---|---|---|---|
| T-25 | OpenAPI cho 14 endpoint — viết **TRƯỚC** khi code (`AC-01`) | `AC-01` | ⬜ |
| T-26 | Integration test 3 client cùng một tài khoản | **SC-001** | ⬜ |
| T-27 | Test cookie chung `cnhsk.com` → `game.cnhsk.com` | **SC-002** | ⬜ |
| T-28 | Test 27 mã lỗi trong Error Matrix | §5 | ⬜ |
| T-29 | Coverage ≥ 80% (`ES-05`) | `ES-05` | ⬜ |

> T-25 đứng ở đợt 7 trong bảng nhưng **phải làm trước đợt 3** theo `AC-01` (API First).
> Để ở đây cho cùng nhóm "việc kiểm thử & hợp đồng".

---

## Thứ tự thực thi

```
Đợt 0 (chặn) → T-25 OpenAPI → Đợt 1 → Đợt 2 → Đợt 3 → Đợt 4 → Đợt 5 → Đợt 6 → T-26…T-29
```

**Ba task rủi ro cao, làm cẩn thận nhất:** T-03 (`JwtFilter` 2 nhánh) · T-14 (cookie 5 thuộc
tính) · T-18 (rotation + reuse detection).

**Hiện trạng: 0/34 task xong.** T-00a đang chặn — cần RFC sửa `AC-04`.

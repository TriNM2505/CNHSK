# Feature Specification: Thẻ nạp, ví điểm và gói dịch vụ

**Feature:** `013-credit-cards`
**Feature tree:** 6.1 Gói dịch vụ và điểm · **Module:** `learning` (package `learning.credit`) · sửa nhỏ `auth.api`
**Use case:** UC-093 · UC-094 · UC-095 · UC-096 · UC-097 · UC-098 · UC-099 · UC-100 · UC-101 · **Actor:** `LEARNER`, `ADMIN` (thay `ACCOUNTANT` — Q1)
**Màn hình:** SCR-030 Plan & Credits · SCR-031 Transaction History · SCR-073 Card Batch Management · SCR-074 Plan Management · SCR-075 Credit Ledger
**Scope:** MVP · **Priority:** P0

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (RFC đợt cuối R1–R12 đã đồng thuận) |
| **Locked on** | 2026-11-03 |
| **Phụ thuộc** | Spec 001 (đăng nhập, CSRF, `AuditService`, `RateLimiter`, `AuthApi`) |
| **Quyết định chung** | `questions.md` mục 2026-11-03 (Q1–Q7, D) · plan `261102-2104` phase 5 |
| **Điều kiện APPROVED** | RFC "gộp tài chính vào ADMIN" (Q1) được cả nhóm đồng thuận — sửa hiến pháp §Bảy actor, `business.md` §1, UC-099…102, SCR-073…075 |

> **Codex đọc:** `AGENTS.md` → hiến pháp §Mô hình kinh doanh, `BUS-12`…`BUS-15`, `HR-05` → spec 001 (`AuthApi`, `RateLimiter`) → file này → `contracts/openapi-credit.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| `ADMIN` sinh lô mã `CREDIT`/`SUBSCRIPTION`, xem lô, khoá lô (Q1) | Role `ACCOUNTANT` (giữ mã trong DB, không dùng) |
| Học viên nạp mã: `CREDIT` cộng điểm, `SUBSCRIPTION` kích hoạt/gia hạn **PREMIUM** (Q3) | `PREMIUM_PLUS`, đổi gói khác loại |
| Không mua gói bằng điểm (Q2) | Cổng thanh toán, thu tiền |
| `ADMIN` sửa gói FREE/PREMIUM qua SCR-074 (Q4) | Xoá gói |
| Ví, hạn mức, lịch sử giao dịch của mình | Tranh chấp, điều chỉnh tay (UC-102) |
| Sổ cái: xem, xuất CSV, đối chiếu (Q6) | Điều chỉnh tự động sau đối chiếu |
| `EntitlementApi`: trừ hạn mức → điểm → chặn, hoàn lại (Q5) | Tính năng tốn phí cụ thể (AI sinh câu, dịch, chấm bài — gọi API này sau) |
| Nạp mã **không** cần email đã xác minh (Q7) | |

**Không migration.** Bảng dùng: `credit_cards`, `credit_transactions`, `plans` (`learning`), `auth.users` (qua `AuthApi`), `shared.audit_logs`.

### 0.1 · Mã thẻ

| Mục | Quy định |
|---|---|
| Sinh | `SecureRandom`, 16 ký tự bảng Crockford base32 `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (80 bit, `BUS-13`); hiển thị `XXXX-XXXX-XXXX-XXXX` |
| Lưu | `code_hash` = hex(HMAC-SHA256(mã đã chuẩn hoá, `CARD_CODE_PEPPER`)) (`BUS-12`) |
| Chuẩn hoá khi nạp | Bỏ khoảng trắng, `-`; chữ hoa; `O→0`, `I`/`L→1`; khác 16 ký tự hợp lệ → coi như mã sai |
| Khoá bí mật | Biến môi trường `CARD_CODE_PEPPER` (≥ 32 byte), thiếu → ứng dụng không khởi động; `.env.example` chỉ có tên biến; `application-test.yaml` có giá trị **chỉ cho test** |
| Không bao giờ | Ghi mã thô hoặc hash vào log, audit, response sau lần sinh, URL (BR-094-4, BR-099-7) |
| `batch_id` | `B` + `yyyyMMdd` (giờ Việt Nam) + `-` + 6 ký tự base32 ngẫu nhiên (vd `B20261103-7K3QX9`) |
| `value` | `CREDIT`: số điểm 1–10 000 · `SUBSCRIPTION`: bằng `plan_days` (1–365) để thoả CHECK `value > 0` |

### 0.2 · Gói và hạn mức

| Mục | Quy định |
|---|---|
| Gói hiệu lực | `PREMIUM` khi `users.plan_code = 'PREMIUM'` và `plan_expires_at > now`; ngược lại `FREE` (BR-093-5) |
| Hạn mức FREE | **10 lượt/tính năng/tháng** (`AGENTS.md` §12) — hằng cấu hình `cnhsk.billing.free-monthly-limit: 10`, không đọc từ `benefits` |
| Hạn mức PREMIUM | `plans.benefits[feature]` do `ADMIN` nhập (thiếu khoá → 0) |
| Tính năng tốn phí | `AI_GENERATE`, `TRANSLATE`, `ASSISTANT`, `GRADING` (`database.md` `feature_code`) |
| Kỳ | Tháng dương lịch giờ Việt Nam; đặt lại **lười** khi tiêu thụ: `usage_reset_at` < đầu tháng hiện tại → `free_usage = {}`, `usage_reset_at` = đầu tháng (BR-093-8). Gia hạn **không** đặt lại bộ đếm |
| Giá điểm | 1 điểm mỗi hành động khi hết hạn mức (SCR-030) |
| Gia hạn | Còn hạn → cộng `plan_days` vào hạn cũ; hết hạn/FREE → từ `now` (BR-095-4/5) |
| Hạ gói | Job hằng ngày đặt `plan_code='FREE'`, `plan_expires_at=NULL` cho gói hết hạn; đọc luôn coi gói hết hạn là FREE |

### 0.3 · Thay đổi `AuthApi` (module `auth`, chỉ thêm)

| Phương thức | Việc |
|---|---|
| `PlanState lockPlan(long userId)` | `SELECT … FOR UPDATE` dòng `users`; trả `{planCode, planExpiresAt, freeUsage: Map<String,Integer>, usageResetAt}`. **Khoá duy nhất** cho mọi thay đổi ví/gói/hạn mức của một người |
| `void updatePlan(long userId, String planCode, Instant expiresAt)` | Ghi `plan_code`, `plan_expires_at`, `updated_at` (cần đã gọi `lockPlan`) |
| `void updateUsage(long userId, Map<String,Integer> usage, Instant resetAt)` | Ghi `free_usage`, `usage_reset_at` |
| `int downgradeExpiredPlans(Instant now)` | Hạ gói hết hạn về FREE |
| `Map<Long,String> maskedEmails(Set<Long> userIds)` | Email đã che theo `BUS-06` cho sổ cái |
| `long countActivePlan(String planCode, Instant now)` | Số người đang có gói còn hạn (SCR-074) |

### 0.4 · File dùng chung bị sửa (báo leader trước khi mở PR)

| File | Thay đổi | Task |
|---|---|---|
| `auth/api/AuthApi.java` + lớp cài đặt + test | §0.3 (**module của spec 001**) | T-06 |
| `shared/exception/ErrorCode.java` + `GlobalExceptionHandlerTest` | 6 mã §5, trước `INTERNAL_ERROR` (sau mã của 010, 012) | T-07, T-08, T-10, T-11, T-12 |
| `shared/config/SecurityConfig.java` | 3 matcher `LEARNER` đặt **trước** `/api/me/**`; `/api/admin/**` đã là `ADMIN` | T-08, T-09 |
| `src/main/resources/application.yaml` | `cnhsk.billing.*` | T-05, T-13 |
| `src/test/resources/application-test.yaml` | `cnhsk.billing.card-code-pepper` giá trị test | T-05 |
| `.env.example` | Thêm dòng `CARD_CODE_PEPPER=` (không giá trị) | T-05 |
| `cnhsk-web/src/app/routeAccess.ts`, `screenRegistry.ts`, `shared/auth/navigation.ts` | 5 route §12; mục điều hướng tài chính dưới `ADMIN` | T-01 |

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-07** | Phễu hệ sinh thái: mã tặng/đối tác dẫn người dùng vào CNHSK | Sinh lô theo `channel`, đếm đã dùng/tổng |
| **BG-08** | Tiền thật không bị mất hoặc tạo ra từ hư không | Hash mã, chặn dò, sổ cái chỉ ghi thêm, đối chiếu |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | 10 request song song cùng mã | Đúng 1 thành công, số dư đúng |
| **SC-002** | Mọi dòng sổ cái: `balance_after = balance_before + amount`, chuỗi `seq` liên tục | 100% (đối chiếu = 0 lỗi) |
| **SC-003** | DB, log, audit không chứa mã thô | 0 |

---

## 2 · User Stories

### US-1 · Nạp mã (P1)
Học viên nhập mã ở SCR-030: mã điểm cộng điểm vào ví; mã gói kích hoạt hoặc gia hạn PREMIUM.

### US-2 · Xem ví và lịch sử (P1)
Học viên thấy gói, hạn, số dư, lượt còn của từng tính năng, ngày đặt lại; xem lịch sử giao dịch có số dư trước/sau.

### US-3 · Phát hành mã (P1)
ADMIN sinh lô ≤ 100 mã, tải CSV ngay lúc sinh (không xem lại được); khoá lô khi lộ mã.

### US-4 · Vận hành gói và sổ cái (P2)
ADMIN sửa gói PREMIUM, xem/xuất sổ cái theo khoảng ngày, chạy đối chiếu.

### Edge cases

| # | Tình huống | Xử lý |
|---|---|---|
| E1 | Mã sai, đã dùng, hết hạn, bị khoá, do chính mình tạo | Cùng **400** `INVALID_CARD_CODE` một thông báo (BR-094-5) |
| E2 | Nhập sai lần thứ 6 trong giờ | **429** `RATE_LIMITED` + `retryAfterSeconds` |
| E3 | Hai người cùng nạp một mã | `UPDATE … WHERE status='UNUSED'` — một thành công, người kia E1 |
| E4 | Một người nạp hai mã cùng lúc | Khoá dòng `users` → giao dịch tuần tự, `seq` liên tục |
| E5 | Gia hạn khi còn 10 ngày, mã 30 ngày | Hạn mới = hạn cũ + 30 ngày |
| E6 | Đổi `benefits` PREMIUM khi có người đang dùng | **409** `PLAN_IN_USE` |
| E7 | Retry cùng khoá idempotency khi tiêu thụ | Trả kết quả cũ, không trừ lần hai |
| E8 | Hết hạn mức và hết điểm | **402** `INSUFFICIENT_CREDITS` trước khi gọi API ngoài (BR-096-8) |
| E9 | Hoàn hai lần | Lần hai không đổi gì (`uq_refund`) |
| E10 | Sang tháng mới | Bộ đếm hạn mức về 0 ở lần tiêu thụ đầu tiên |

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL áp quyền: `/api/admin/card-batches/**`, `/api/admin/plans/**`, `/api/admin/credit-transactions/**` → chỉ `ADMIN` (Q1); `POST /api/credits/redeem`, `GET /api/me/credits`, `GET /api/me/credit-transactions` → chỉ `LEARNER`, dữ liệu của người gọi (BR-093-2). `ACCOUNTANT` không có quyền nào ở đây. Sai role → **403** `FORBIDDEN`. Mọi thao tác ghi qua cookie cần CSRF (`HR-05`) |

### Nhóm 1 — Mã và sổ cái (FR-002…FR-004)

| Mã | Yêu cầu |
|---|---|
| **FR-002** | `CardCodes` SHALL sinh, chuẩn hoá và băm mã đúng §0.1; cùng mã + cùng khoá → cùng hash; đổi khoá → hash khác |
| **FR-003** | `LedgerService` SHALL là **nơi duy nhất** ghi `credit_transactions` (BR-094-10), chỉ `INSERT` (`BUS-14`, `BUS-15`); yêu cầu người gọi đã giữ `lockPlan`; `seq` = `seq` lớn nhất + 1; `balance_before` = `balance_after` dòng mới nhất (không có → 0); số dư âm → DB từ chối (rollback) |
| **FR-004** | Số dư SHALL = `balance_after` dòng mới nhất (không có bảng số dư — `BUS-14`) |

### Nhóm 2 — Lô mã (FR-005…FR-008) · SCR-073

| Mã | Yêu cầu |
|---|---|
| **FR-005** | `POST /api/admin/card-batches` body `{cardType*, value?, planDays?, quantity* (1–100), channel* (ECOSYSTEM_GIFT \| PARTNER_BATCH \| DIRECT), partnerCode?, campaign?, issuedTo?, expiresAt?}` SHALL kiểm: `CREDIT` cần `value` 1–10 000, không `planDays`; `SUBSCRIPTION` cần `planDays` 1–365, không `value`; `PARTNER_BATCH` cần `partnerCode`; `ECOSYSTEM_GIFT` cần `issuedTo`; `expiresAt` phải ở tương lai; sai → **422** `VALIDATION_FAILED`. `SUBSCRIPTION` khi dòng `PREMIUM` không có hoặc `is_active = false` → **422** `PLAN_NOT_AVAILABLE` |
| **FR-006** | WHEN hợp lệ, THE system SHALL sinh `quantity` mã khác nhau, ghi `credit_cards` (`UNUSED`, `created_by` = người gọi) và một dòng `audit_logs` `CARD_BATCH_CREATE` `{batchId, cardType, quantity, value, planDays, totalValue, channel}` (không có mã) trong một transaction; trả **201** `{batchId, codes:[16 ký tự có gạch]}` — lần **duy nhất** mã thô rời server (BR-099-6). Trùng hash (cực hiếm) → sinh lại mã đó |
| **FR-007** | `GET /api/admin/card-batches?page&size` SHALL trả lô mới nhất trước `{batchId, cardType, value, planDays, channel, partnerCode, campaign, issuedTo, expiresAt, total, unused, used, voided, createdAt, createdByName}` (gộp bằng SQL, không N+1); `GET …/{batchId}` trả một lô; không có → **404** `CARD_BATCH_NOT_FOUND`. Không trả mã, hash, người dùng mã |
| **FR-008** | `PATCH /api/admin/card-batches/{batchId}/void` SHALL chuyển mọi mã `UNUSED` của lô sang `VOIDED`, giữ nguyên mã `USED` (SCR-073), ghi audit `CARD_BATCH_VOID {batchId, voided}`; gọi lại → **200** `voided = 0` (idempotent); lô không có → **404** |

### Nhóm 3 — Nạp mã (FR-009…FR-012) · SCR-030

| Mã | Yêu cầu |
|---|---|
| **FR-009** | `POST /api/credits/redeem` body `{code*}` SHALL trước tiên kiểm chặn: người gọi có ≥ 5 lần sai trong 1 giờ **hoặc** IP có ≥ 30 lần sai trong 1 giờ → **429** `RATE_LIMITED` + `retryAfterSeconds` (NFR-S03; Redis qua `RateLimiter`; Redis lỗi → cho qua, log `WARN`) |
| **FR-010** | THE system SHALL `lockPlan(user)` rồi `UPDATE credit_cards SET status='USED', used_by=:user, used_at=now() WHERE code_hash=:hash AND status='UNUSED' AND (expires_at IS NULL OR expires_at > now()) AND created_by <> :user RETURNING …`; 0 dòng (gồm mã sai định dạng) → tăng bộ đếm sai, **400** `INVALID_CARD_CODE` cùng một thông báo cho mọi nguyên nhân (E1) |
| **FR-011** | WHEN mã `CREDIT`, THE system SHALL ghi `TOPUP` `amount = value`, `card_id`; WHEN mã `SUBSCRIPTION`, SHALL tính hạn mới (§0.2), `updatePlan(user, 'PREMIUM', hạn mới)`, ghi `SUBSCRIPTION` `amount = 0`, `card_id`, `reason = "PREMIUM +{n} ngày, giá niêm yết {price}"` (BR-101-6) — đổi mã, ghi sổ, đổi gói **trong một transaction** (BR-094-7, BR-095-3) |
| **FR-012** | Thành công SHALL trả **200** `{cardType, amount, balance, plan:{code, expiresAt}}`; không trả `batchId`, `card_id`. Request body có mã không được ghi log (lọc ở log request) |

### Nhóm 4 — Ví học viên (FR-013…FR-014) · SCR-030, SCR-031

| Mã | Yêu cầu |
|---|---|
| **FR-013** | `GET /api/me/credits` SHALL trả `{balance, plan:{code, name, expiresAt, active}, quota:[{feature, limit, used, remaining}], resetAt}`: gói theo §0.2; `used` = 0 nếu kỳ đã qua; `resetAt` = 00:00 ngày 1 tháng sau giờ Việt Nam. Chỉ đọc, không tiêu thụ (BR-093-4) |
| **FR-014** | `GET /api/me/credit-transactions?page&size` (size 1–100, mặc định 20) SHALL trả giao dịch của người gọi, `seq` giảm dần, mỗi dòng `{seq, type, amount, balanceBefore, balanceAfter, featureCode, reason, createdAt}` — **không** có `card_id`, mã, hash, `ref_transaction_id` (BR-098-4). Rỗng → `[]` |

### Nhóm 5 — Gói (FR-015…FR-016) · SCR-074

| Mã | Yêu cầu |
|---|---|
| **FR-015** | `GET /api/admin/plans` SHALL trả đúng 3 mã `FREE`, `PREMIUM`, `PREMIUM_PLUS` (mã chưa có dòng → `exists=false`), mỗi mã `{code, exists, name, description, durationDays, price, benefits, isActive, activeUsers}` |
| **FR-016** | `PUT /api/admin/plans/{code}` SHALL: `FREE` → chỉ sửa `name`, `description`; `PREMIUM` → tạo nếu chưa có hoặc sửa `{name*, description, durationDays* (1–365), price* (≥ 0, 2 chữ số thập phân), benefits* ({feature: 0–10 000} chỉ 4 khoá §0.2), isActive*}`; `PREMIUM_PLUS` → **422** `PLAN_NOT_AVAILABLE` (Q3); đổi `benefits` khi `countActivePlan('PREMIUM') > 0` → **409** `PLAN_IN_USE` (E6); ghi audit `PLAN_UPDATE` `before`/`after` (BR-101-7). Không có xoá. Ngưng bán (`isActive=false`) không thu hồi gói đang dùng (BR-101-8) |

### Nhóm 6 — Sổ cái (FR-017…FR-019) · SCR-075

| Mã | Yêu cầu |
|---|---|
| **FR-017** | `GET /api/admin/credit-transactions?from*&to*&type&page&size` SHALL bắt buộc `from`, `to` (ngày, `to − from` ≤ 92 ngày, sai → **422** `VALIDATION_FAILED`), trả trang dòng `{id, userId, maskedEmail, seq, type, amount, balanceBefore, balanceAfter, featureCode, reason, createdAt}` + `totals {count, credited, debited}` cho **toàn bộ** bộ lọc; ghi audit `LEDGER_READ {from, to, type}` mỗi lần gọi (`NFR-S08`) |
| **FR-018** | `GET /api/admin/credit-transactions/export?from*&to*&type` SHALL trả `text/csv` UTF-8 không BOM, cột như FR-017; > 10 000 dòng → **422** `EXPORT_TOO_LARGE`; ghi audit `LEDGER_EXPORT {from, to, rows}` |
| **FR-019** | `GET /api/admin/credit-transactions/reconciliation` SHALL kiểm toàn sổ: mỗi người `seq` liên tục từ 1, `balance_before(n) = balance_after(n−1)`, dòng đầu `balance_before = 0`; trả `{users, rows, totalBalance, totalAmount, issues:[{userId, maskedEmail, seq, kind}]}` với `totalBalance = Σ số dư cuối`, `totalAmount = Σ amount` (phải bằng nhau); ghi audit `LEDGER_RECONCILE`. **Không** tự sửa (SCR-075) |

### Nhóm 7 — Tiêu thụ quyền sử dụng (FR-020…FR-022)

| Mã | Yêu cầu |
|---|---|
| **FR-020** | `learning.api.EntitlementApi.consume(long userId, PaidFeature feature, String idempotencyKey) → Consumption{source: QUOTA \| CREDIT, transactionId}` SHALL chạy trong transaction của người gọi: khoá idempotency đã dùng (Redis `billing:idem:{userId}:{key}`, 24 giờ) → trả `Consumption` cũ (E7); `lockPlan`; đặt lại kỳ nếu cần; `used < limit` → `used + 1`, `QUOTA`; ngược lại số dư ≥ 1 → `DEDUCT` −1 `feature_code`, `CREDIT`; ngược lại **402** `INSUFFICIENT_CREDITS` (BR-096-4/8). Không cộng quỹ FREE riêng cho người có PREMIUM |
| **FR-021** | `EntitlementApi.refund(long userId, Consumption c)` SHALL: `CREDIT` → `REFUND` +1 `ref_transaction_id = c.transactionId` (một lần — `uq_refund`, lần sau không đổi gì); `QUOTA` → `used − 1` nếu cùng kỳ và > 0 (một lần — Redis `billing:refund:{userId}:{key}`). Số hoàn lấy từ giao dịch gốc, không từ tham số (BR-097-4) |
| **FR-022** | `PaidFeature` SHALL có đúng 4 giá trị §0.2. Đọc thông tin (FR-013, FR-014) không bao giờ tiêu thụ |

### Nhóm 8 — Job (FR-023)

| Mã | Yêu cầu |
|---|---|
| **FR-023** | `PlanMaintenance` SHALL chạy `@Scheduled(cron = "${cnhsk.billing.downgrade-cron:0 10 0 * * *}", zone = "Asia/Ho_Chi_Minh")` gọi `downgradeExpiredPlans(now)`; profile test đặt `-`, bean vẫn gọi trực tiếp được |

### Nhóm 9 — Giao diện (FR-024…FR-028)

| Mã | Yêu cầu |
|---|---|
| **FR-024** | SCR-030 SHALL hiện gói hiện tại + ngày hết hạn (giờ Việt Nam), số dư điểm, bảng lượt còn theo 4 tính năng, ngày đặt lại, ô nhập mã (tự thêm gạch, xác nhận trong trang trước khi nạp), kết quả nạp, liên kết SCR-031; ghi chú "Mã thẻ do hệ sinh thái phát hành; CNHSK không thu tiền". Lỗi mã: một câu chung; bị chặn: đếm ngược `retryAfterSeconds`. Không có nút mua/thanh toán, không giá cứng |
| **FR-025** | SCR-031 SHALL liệt kê dấu +/−, loại, số dư trước/sau, nội dung, thời gian, phân trang 20; rỗng "Chưa có giao dịch"; không có nút sửa/xoá |
| **FR-026** | SCR-073 SHALL có form sinh lô (trường điều kiện theo `channel`, `cardType`; giới hạn 100), sau khi sinh hiện **một lần** danh sách mã + nút "Tải CSV" + cảnh báo "Mã chỉ hiện một lần — tải ngay"; rời trang thì mất (không lưu `localStorage`); bảng lô với đã dùng/tổng; "Khoá lô" có xác nhận trong trang, ẩn khi không còn mã `UNUSED` |
| **FR-027** | SCR-074 SHALL hiện 3 mã gói; form FREE (tên, mô tả), PREMIUM (tên, mô tả, số ngày, giá niêm yết — ghi chú "giá niêm yết, không phải tiền đã thu", hạn mức 4 tính năng, đang bán); `PREMIUM_PLUS` chỉ đọc "Chưa hỗ trợ đợt này"; FREE hiện "10 lượt/tính năng/tháng"; số người đang dùng; lỗi `PLAN_IN_USE` giải thích ngay ô hạn mức |
| **FR-028** | SCR-075 SHALL có bộ lọc từ–đến (bắt buộc, ≤ 92 ngày), loại; bảng có email đã che, số có dấu, hai số dư; tổng của bộ lọc ("Tổng điểm", không gọi là doanh thu); nút "Xuất CSV" và "Đối chiếu" (kết quả nội tuyến, danh sách lỗi nếu có); không có sửa/xoá |

### Key Entities

| Bảng | Dùng thế nào |
|---|---|
| `learning.credit_cards` | Mã (hash), lô, trạng thái |
| `learning.credit_transactions` | Sổ cái chỉ ghi thêm |
| `learning.plans` | 3 mã; `benefits` JSONB chỉ đọc khi tiêu thụ |
| `auth.users` (qua `AuthApi`) | `plan_code`, `plan_expires_at`, `free_usage`, `usage_reset_at` |
| `shared.audit_logs` | `CARD_BATCH_CREATE`, `CARD_BATCH_VOID`, `PLAN_UPDATE`, `LEDGER_READ`, `LEDGER_EXPORT`, `LEDGER_RECONCILE` |

---

## 4 · Data Model

Không migration. Ví dụ sổ cái một người:

| seq | type | amount | before | after | Ghi chú |
|---|---|---|---|---|---|
| 1 | `TOPUP` | 100 | 0 | 100 | `card_id` 41 |
| 2 | `SUBSCRIPTION` | 0 | 100 | 100 | PREMIUM +30 ngày |
| 3 | `DEDUCT` | −1 | 100 | 99 | `feature_code = AI_GENERATE` |
| 4 | `REFUND` | 1 | 99 | 100 | `ref_transaction_id` = dòng 3 |

`users.free_usage` ví dụ: `{"AI_GENERATE": 3, "TRANSLATE": 10}`.

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | spec 001 |
| `FORBIDDEN` | 403 | Sai role | FR-001 |
| `CSRF_TOKEN_INVALID` | 403 | Ghi qua cookie thiếu CSRF | FR-001 |
| `VALIDATION_FAILED` | 422 | Body/tham số sai | FR-005, FR-014, FR-016, FR-017 |
| `INVALID_CARD_CODE` | 400 | Mã không dùng được (mọi nguyên nhân) | FR-010 |
| `RATE_LIMITED` | 429 | Nhập sai quá ngưỡng | FR-009 |
| `CARD_BATCH_NOT_FOUND` | 404 | Lô không có | FR-007, FR-008 |
| `PLAN_NOT_AVAILABLE` | 422 | PREMIUM chưa có/ngưng bán khi sinh lô gói; sửa `PREMIUM_PLUS` | FR-005, FR-016 |
| `PLAN_IN_USE` | 409 | Đổi `benefits` khi có người đang dùng | FR-016 |
| `EXPORT_TOO_LARGE` | 422 | Xuất > 10 000 dòng | FR-018 |
| `INSUFFICIENT_CREDITS` | 402 | Hết hạn mức và hết điểm (Java API; endpoint tính năng tốn phí trả nguyên mã) | FR-020 |

**Mã mới trong `ErrorCode.java`** (thứ tự cuối theo thứ tự merge thực tế — luật L2; trước `INTERNAL_ERROR`, sau mã của 010, 012, theo thứ tự): `INVALID_CARD_CODE`, `CARD_BATCH_NOT_FOUND`, `PLAN_NOT_AVAILABLE`, `PLAN_IN_USE`, `EXPORT_TOO_LARGE`, `INSUFFICIENT_CREDITS`.
Lô và gói là tài nguyên quản trị → 404 khi không có. Ví và lịch sử dùng `/me`, không có id ngoài nên không có IDOR.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-01** | `contracts/openapi-credit.yaml` |
| **AC-03** | `auth.users` chỉ ghi qua `AuthApi` (§0.3); module khác tiêu thụ qua `learning.api.EntitlementApi` |
| **AC-05** | Không migration |
| **AC-07** | `price` `NUMERIC(12,2)`; điểm giữ `INTEGER` như V1 (SCR-075: không migration) |
| **AC-09** | `plans.benefits`, `exams`… chỉ đọc; `users.free_usage` là JSONB ghi nhiều lần **theo thiết kế V1** — ⚠️ lệch AC-09, ghi RFC (W3) |
| **BUS-06** | Email che ở sổ cái |
| **BUS-12**, **BUS-13** | §0.1 |
| **BUS-14**, **BUS-15** | `LedgerService` chỉ `INSERT`; không có endpoint sửa/xoá |
| **HR-05** | CSRF mọi thao tác ghi |
| **NFR-S03** | ≤ 5 lần sai/giờ/người |
| **NFR-S08** | Audit cả đọc sổ cái |
| Package | `learning.credit` (`CardCodes`, `BillingConfig`, `LedgerService`, `CardBatchService`, `RedeemService`, `WalletQueryService`, `PlanAdminService`, `LedgerQueryService`, `ReconciliationService`, `EntitlementService`, `PlanMaintenance`, `web.*Controller`) · `learning.api.EntitlementApi`, `PaidFeature`, `Consumption` |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `POST /api/credits/redeem` | < 300 ms p95 |
| `NFR-P02` | Sinh lô 100 mã | < 2 s p95 |
| `NFR-P03` | `GET /api/me/credits` | < 100 ms p95 |
| `NFR-P04` | Đối chiếu 100 000 dòng | < 5 s p95 (một truy vấn cửa sổ `LAG`) |
| `NFR-S01` | Không mã thô/hash trong log, audit, response sau sinh | Test quét log (AT-04) |

---

## 8 · Constitution Check

| Mã | Kết quả |
|---|---|
| **HR-05** | ✅ |
| **AC-03** | ✅ Qua `AuthApi` |
| **AC-07** | ✅ |
| **AC-09** | ⚠️ `free_usage` (thiết kế V1) — W3 |
| **BUS-06** | ✅ |
| **BUS-12…15** | ✅ |
| §Bảy actor "`ACCOUNTANT` sinh mã, sổ cái; `ADMIN` **không** sinh mã, **không** đụng sổ cái" | ⚠️ **Mâu thuẫn — Q1 gộp vào ADMIN, chờ RFC** (điều kiện APPROVED) |
| BR-099-2 "Không dùng role ADMIN chung" | ⚠️ Như trên |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Gộp nghiệp vụ tài chính vào `ADMIN`, giữ mã `ACCOUNTANT` trong DB/code nhưng không dùng; thêm luật "không nạp mã do mình tạo" | Chốt 2026-11-03 — **chờ RFC** |
| Q2 | Không mua gói bằng điểm | Chốt 2026-11-03 |
| Q3 | Không migration; mã `SUBSCRIPTION` chỉ kích hoạt PREMIUM | Chốt 2026-11-03 |
| Q4 | `ADMIN` nhập giá trị gói qua SCR-074; không hard-code | Chốt 2026-11-03 |
| Q5 | Làm `EntitlementApi` (hạn mức → điểm → chặn, hoàn) | Chốt 2026-11-03 |
| Q6 | Sổ cái: xem + xuất CSV + đối chiếu | Chốt 2026-11-03 |
| Q7 | Nạp mã không cần email đã xác minh | Chốt 2026-11-03 |
| D | Mã 16 ký tự base32 + HMAC; chặn 5/giờ/người + 30/giờ/IP; lô ≤ 100; chỉ khoá mã chưa dùng; gia hạn cộng dồn; job hạ gói; không migration | Chốt 2026-11-03 |
| W1 | Hiến pháp/BR-099-2/SCR-073…075 cho `ACCOUNTANT` ↔ Q1 | RFC |
| W2 | Plan phase 5 "dùng điểm mua gói" ↔ UC | Q2; sửa plan |
| W3 | AC-09 (JSONB chỉ đọc) ↔ `users.free_usage` thiết kế V1 | Giữ V1; RFC ghi ngoại lệ |
| W4 | `credit_cards` không có cột gói (SCR-073 open issue) | Q3 |
| W5 | UC-099/CONTEXT 1 000 mã/lô ↔ SCR-073 100 | Theo SCR-073 (100) |
| W6 | Chống lặp tiêu thụ cần lưu bền (UC07 khoảng trống #19) | Redis 24 giờ, không thêm cột (§11) |
| W7 | UC-094 400 chung ↔ UC-136 404/409/410 | Theo UC-094 (400 chung) |

---

## 10 · Móc nối

| Chỗ | 013 cung cấp | Dùng bởi |
|---|---|---|
| `EntitlementApi.consume/refund` | Trừ hạn mức/điểm, hoàn (`BUS-03`) | AI sinh câu (3.3), dịch (014 sau), chấm bài (6.2) — **015 trợ lý không gọi** (leader: hỏi thoải mái) |
| Gói hiệu lực | `GET /api/me/credits` | Trợ lý 015 có thể đọc để trả lời "gói của tôi" |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| ADMIN vừa gán role vừa sinh mã (mất tách quyền) | Q1; vá bằng "không nạp mã do mình tạo" + audit mọi lô |
| Khoá idempotency ở Redis; Redis mất → có thể trừ hai lần | Không migration (W6); 1 điểm/lần, có đối chiếu và hoàn |
| `free_usage` JSONB ghi nhiều lần | Thiết kế V1 (W3); khoá dòng `users` mỗi lần |
| Chỉ PREMIUM | Q3; PREMIUM_PLUS cần cột gói trên thẻ |
| Đổi `benefits` bị chặn khi có người dùng | Không có snapshot quyền lợi (SCR-074 open issue) |
| Điểm `INTEGER` lệch BIGINT của hiến pháp | SCR-075: không migration đợt này |
| Mã đã phát ra file CSV có thể lộ | Khoá lô (FR-008) |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-030 Plan & Credits | `/account/credits` | `LEARNER` | `GET /api/me/credits`, `POST /api/credits/redeem` | `screen-fields.md` dòng 15885 |
| SCR-031 Transaction History | `/account/credits/history` | `LEARNER` | `GET /api/me/credit-transactions` | dòng 16293 |
| SCR-073 Card Batch Management | `/admin/card-batches` | `ADMIN` | `/api/admin/card-batches/**` | dòng 35942 |
| SCR-074 Plan Management | `/admin/plans` | `ADMIN` | `/api/admin/plans/**` | dòng 36703 |
| SCR-075 Credit Ledger | `/admin/ledger` | `ADMIN` | `/api/admin/credit-transactions/**` | dòng 37274 |

SecurityConfig: `POST /api/credits/redeem`, `GET /api/me/credits`, `GET /api/me/credit-transactions` → `LEARNER` (đặt trước `/api/me/**`); `/api/admin/**` giữ `ADMIN`.

| Open issue (screen-fields) | Chốt |
|---|---|
| SCR-030: 404/409/410 ↔ 400 chung | 400 chung (W7) |
| SCR-030: bắt buộc email xác minh | Không (Q7) |
| SCR-030/073/074: liên kết thẻ-gói, đổi gói khác loại | Chỉ PREMIUM (Q3) |
| SCR-073: 100 ↔ 1 000 | 100 |
| SCR-074: giá/hạn/hạn mức trả phí | ADMIN nhập (Q4) |
| SCR-075: `seq` chưa có, INTEGER ↔ BIGINT | `seq` có trong V1; giữ INTEGER |
| Role màn SCR-073…075 là `ACCOUNTANT` | `ADMIN` (Q1) |

Trạng thái mỗi màn: Loading · Empty · Error ("Thử lại") · Read-only (sổ cái, lô sau khi sinh).

---

## 13 · API Contract

[`contracts/openapi-credit.yaml`](contracts/openapi-credit.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `POST /api/credits/redeem` | 200 `RedeemResult` | 400, 401, 403, 422, 429 |
| `GET /api/me/credits` | 200 `Wallet` | 401, 403 |
| `GET /api/me/credit-transactions` | 200 `TransactionPage` | 401, 403, 422 |
| `POST /api/admin/card-batches` | 201 `CreatedBatch` | 401, 403, 422 |
| `GET /api/admin/card-batches` | 200 `BatchPage` | 401, 403, 422 |
| `GET /api/admin/card-batches/{batchId}` | 200 `BatchSummary` | 401, 403, 404 |
| `PATCH /api/admin/card-batches/{batchId}/void` | 200 `VoidResult` | 401, 403, 404 |
| `GET /api/admin/plans` | 200 `PlanView[]` | 401, 403 |
| `PUT /api/admin/plans/{code}` | 200 `PlanView` | 401, 403, 409, 422 |
| `GET /api/admin/credit-transactions` | 200 `LedgerPage` | 401, 403, 422 |
| `GET /api/admin/credit-transactions/export` | 200 `text/csv` | 401, 403, 422 |
| `GET /api/admin/credit-transactions/reconciliation` | 200 `Reconciliation` | 401, 403 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | `LEARNER` gọi `POST /api/admin/card-batches` · T 403; `ACCOUNTANT` gọi · T 403; `ADMIN` gọi `POST /api/credits/redeem` · T 403; không token · T 401; nạp qua cookie thiếu CSRF · T 403 |
| AT-02 | FR-002 | 10 000 mã sinh ra · T đúng 16 ký tự trong bảng, không trùng; `abcd-efgh-…` chuẩn hoá đúng; `O`/`I`/`L` thay đúng; cùng mã khác khoá · T hash khác; thiếu `CARD_CODE_PEPPER` · T không khởi động |
| AT-03 | FR-003, FR-004 | Hai luồng ghi giao dịch cùng người · T `seq` 1, 2 liên tục; trừ quá số dư · T rollback, không có dòng; không có câu `UPDATE`/`DELETE` lên `credit_transactions` trong code (ArchUnit/test tìm chuỗi) |
| AT-04 | FR-006, NFR-S01 | Sinh lô 100 · T 201, 100 mã, 100 dòng `UNUSED`, 1 audit không chứa mã; quét log và bảng `audit_logs` · T không có mã nào trong 100 mã; `GET` lô sau đó · T không có mã |
| AT-05 | FR-005 | `quantity=101`, `CREDIT` thiếu `value`, `SUBSCRIPTION` có `value`, `PARTNER_BATCH` thiếu `partnerCode`, `expiresAt` quá khứ · T 422; `SUBSCRIPTION` khi PREMIUM `is_active=false` · T 422 `PLAN_NOT_AVAILABLE` |
| AT-06 | FR-007 | 3 lô, 1 lô có 2 mã đã dùng · T đếm đúng; số truy vấn không tăng theo số lô; lô không có · T 404 |
| AT-07 | FR-008 | Lô 10 mã (3 `USED`) · T khoá → 7 `VOIDED`, 3 giữ `USED`; gọi lại · T `voided=0`; nạp mã đã khoá · T 400 chung |
| AT-08 | FR-009 | 5 lần sai · T lần 6 trả 429 kèm `retryAfterSeconds`, kể cả mã đúng; 30 lần sai từ một IP (nhiều tài khoản) · T 429; Redis tắt · T vẫn nạp được |
| AT-09 | FR-010, SC-001, E1, E3 | 10 request song song cùng mã (10 người) · T 1 thành công, 9 lỗi 400; mã sai định dạng, đã dùng, hết hạn, khoá, do chính mình tạo · T cùng 400 cùng message |
| AT-10 | FR-011, E5 | Mã `CREDIT` 100 · T `TOPUP` +100; mã `SUBSCRIPTION` 30 ngày cho FREE · T PREMIUM hạn = now + 30 ngày, giao dịch `amount=0`; còn 10 ngày · T hạn cũ + 30; lỗi giả khi ghi sổ · T mã vẫn `UNUSED`, gói không đổi |
| AT-11 | FR-012 | Response không có `batchId`/`cardId`; log request không chứa mã |
| AT-12 | FR-013, E10 | FREE đã dùng 3 `AI_GENERATE` tháng này · T `remaining=7`; tháng trước · T `used=0`; PREMIUM `benefits` thiếu `TRANSLATE` · T `limit=0`; gói hết hạn · T hiện FREE; gọi `GET` · T bộ đếm không đổi |
| AT-13 | FR-014 | 25 giao dịch · T trang 1 có 20, `seq` giảm dần, không có `cardId`; người khác · T không thấy |
| AT-14 | FR-015, FR-016, E6 | `GET` · T 3 mã; tạo PREMIUM · T 200, audit; đổi `benefits` khi có người đang PREMIUM · T 409; đổi `price` lúc đó · T 200; `PUT PREMIUM_PLUS` · T 422; FREE đổi `price` · T bị bỏ qua; `benefits` khoá lạ · T 422 |
| AT-15 | FR-017 | Thiếu `from` · T 422; 93 ngày · T 422; hợp lệ · T `totals` tính trên toàn bộ bộ lọc, email đã che, 1 audit `LEDGER_READ` |
| AT-16 | FR-018 | 30 dòng · T CSV header + 30 dòng, email che, audit `LEDGER_EXPORT`; 10 001 dòng · T 422 `EXPORT_TOO_LARGE` |
| AT-17 | FR-019, SC-002 | Sổ sạch · T `issues=[]`, `totalBalance = totalAmount`; chèn tay một dòng lệch số dư · T 1 issue đúng người/`seq`; không có dòng nào bị sửa |
| AT-18 | FR-020, E7, E8 | FREE 10 lượt: lần 1–10 · T `QUOTA`; lần 11 có 5 điểm · T `CREDIT`, `DEDUCT` −1; hết điểm · T 402; cùng khoá gọi 2 lần · T 1 lần trừ; PREMIUM hạn mức 0 · T trừ điểm ngay |
| AT-19 | FR-021, E9 | Hoàn `CREDIT` · T `REFUND` +1 trỏ dòng gốc; hoàn lại · T không thêm dòng; hoàn `QUOTA` · T `used − 1`; hoàn sau khi sang tháng · T không đổi |
| AT-20 | FR-022 | `PaidFeature.values()` · T đúng 4 giá trị |
| AT-21 | FR-023 | 2 người hết hạn, 1 còn · T job hạ 2 người về FREE, `plan_expires_at=NULL` |
| AT-22 | FR-024 (FE) | Vitest: hiện gói/hạn/số dư/4 dòng lượt/ngày đặt lại; nhập mã tự thêm gạch; lỗi một câu chung; 429 có đếm ngược; không có nút thanh toán |
| AT-23 | FR-025 (FE) | Vitest: dấu +/−, hai số dư, phân trang, rỗng |
| AT-24 | FR-026 (FE) | Vitest: trường điều kiện theo `channel`; > 100 báo lỗi; mã hiện một lần + "Tải CSV" + cảnh báo; tải lại trang không còn mã; "Khoá lô" có xác nhận |
| AT-25 | FR-027 (FE) | Vitest: 3 mã, PREMIUM_PLUS chỉ đọc, FREE "10 lượt", ghi chú giá niêm yết, lỗi `PLAN_IN_USE` ở ô hạn mức |
| AT-26 | FR-028 (FE) | Vitest: bắt buộc từ–đến, > 92 ngày báo lỗi, email che, "Tổng điểm", đối chiếu hiện lỗi nội tuyến, không có sửa/xoá |
| AT-27 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-credit.yaml` (path + status) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md)

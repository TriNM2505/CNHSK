# Tasks: Thẻ nạp, ví điểm và gói dịch vụ — Spec #013

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-credit.yaml`](contracts/openapi-credit.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 14 task**, ≤ 4 giờ/task, một task một nhánh `spec/013-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 013 đã `APPROVED` 2026-11-03 — theo thứ tự luồng trong `workflow-phu-thuoc-task-010-015.md` (plan `261102-2104`). **Thứ tự: FE trước, BE sau.** **Không commit `.env`, giá trị `CARD_CODE_PEPPER` thật, mã thẻ thật.**
File dùng chung (spec §0.4) phải nêu trong Shadow Plan từng task.

## Đợt 1 · Frontend bằng dữ liệu mẫu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Nền FE `credits`: `types.ts` theo OpenAPI, `api/*.ts`, `catalog.preview.ts` (ví, 25 giao dịch, 3 lô, 3 gói, sổ cái mẫu — **mã mẫu tự bịa, không dùng được**), `screens.ts`; 5 route §12 (2 `LEARNER` layout `learner`, 3 `ADMIN` layout `admin`) vào `routeAccess.ts` + `screenRegistry.ts`; mục điều hướng tài chính dưới `ADMIN` trong `navigation.ts` (**báo leader trước**) | `web/features/credits/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts`, `web/shared/auth/navigation.ts` | FR-001 (FE), §12 | ✅ (leader duyệt; chốt trong `questions.md`) |
| T-02 | SCR-030 Plan & Credits + SCR-031 Transaction History | `web/features/credits/pages/Scr030PlanCreditsPage.tsx`, `Scr031TransactionHistoryPage.tsx` + test | FR-024, FR-025 · AT-22, AT-23 | ✅ |
| T-03 | SCR-073 Card Batch Management (form điều kiện, mã hiện một lần + tải CSV ở client, bảng lô, khoá lô) | `web/features/credits/pages/Scr073CardBatchPage.tsx` + test | FR-026 · AT-24 | ✅ |
| T-04 | SCR-074 Plan Management + SCR-075 Credit Ledger | `web/features/credits/pages/Scr074PlanManagementPage.tsx`, `Scr075CreditLedgerPage.tsx` + test | FR-027, FR-028 · AT-25, AT-26 | ✅ |

## Đợt 2 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-05 | `BillingConfig` (`cnhsk.billing.*`, `CARD_CODE_PEPPER` ≥ 32 byte bắt buộc) + `CardCodes` (sinh/chuẩn hoá/HMAC) + test; `.env.example` thêm tên biến; `application-test.yaml` giá trị test. **Không phụ thuộc task nào** | `…/learning/credit/{BillingConfig,CardCodes}.java`, `application.yaml`, `src/test/resources/application-test.yaml`, `.env.example` | FR-002 · AT-02 | ✅ — PR #131 |
| T-06 | `AuthApi` thêm 6 phương thức §0.3 + cài đặt + test (**module 001 — báo leader trước**); `LedgerService` (chỉ `INSERT`, `seq`, số dư) + test đồng thời + kiểm "không có UPDATE/DELETE" | `…/auth/api/AuthApi.java`, lớp cài đặt `AuthApi`, `…/learning/credit/LedgerService.java` + test | FR-003, FR-004 · AT-03 | ✅ — PR #132, merge `8b3b228` |
| T-07 | Lô mã: `POST`/`GET`/`GET {batchId}`/`PATCH void` + audit; mã `CARD_BATCH_NOT_FOUND`, `PLAN_NOT_AVAILABLE` | `…/learning/credit/CardBatchService.java`, `…/credit/web/CardBatchController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-001, FR-005…FR-008 · AT-01, AT-04…AT-07 | ✅ — PR #133, merge e6bd160; Leader xác nhận integrated verify 4014 tests PASS; hai redeem assertions vẫn giao T-08 |
| T-08 | Nạp mã: chặn sai (`RateLimiter`), `UPDATE` nguyên tử, `TOPUP`/`SUBSCRIPTION` + gia hạn, lọc mã khỏi log request; mã `INVALID_CARD_CODE`; matcher `LEARNER` trước `/api/me/**`; **test 10 request song song** | `…/learning/credit/RedeemService.java`, `…/credit/web/WalletController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-009…FR-012 · AT-08…AT-11 | ✅ MERGED — PR #137, merge 09ea06a, head f3f1113; Leader xác nhận. Giữ toàn bộ lịch sử Full Verify 4213/F4/E0/S0 FAILED/exit1 và JaCoCo đã nghiệm thu trong questions/evidence. |
| T-09 | `GET /api/me/credits`, `GET /api/me/credit-transactions`; dùng lại matcher LEARNER hiện có | `…/learning/credit/WalletQueryService.java`, `WalletController.java`, bốn record view; `AuthApi`/`AuthApiService` và tests theo exact allowlist | FR-001, FR-004, FR-013, FR-014, FR-022 · AT-12, AT-13 | ✅ MERGED — PR #147, merge commit 7054115; Leader xác nhận Integrated Verify4306/F0/E0/S0 BUILD SUCCESS, JaCoCo đạt. Giữ toàn bộ G1 FAILED/UNKNOWN history trong questions/evidence. |
| T-10 | Gói: `GET /api/admin/plans`, `PUT /{code}` + audit; mã `PLAN_IN_USE` | `…/learning/credit/PlanAdminService.java`, `…/credit/web/PlanAdminController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest`; Q1#2 thêm AuthApiService/AuthPlanApiIntegrationTest | FR-015, FR-016 · AT-14 | 🔄 G2 verified theo whitelist Leader / G3 Draft đang thực hiện — exact19/base b2d3b12. Contract4 exit0; targeted214/F0/E0/S0 đúng11 suites exit0. Full duy nhất5177/F4/E0/S0 đúng332 suites FAILED/native1, outside0; LINE13490/13915=96.945742% từ đúng full exec, report/check exit0. Stage từngfile/commit/push thường/DraftPR main được Leader duyệt, không merge/Ready. Giữ regression cũ2383/F4/native1; không rerun regression/ordered isolation. Details và PR metadata trong questions/G2-G3 artifacts. |
| T-11 | Sổ cái: danh sách + tổng, xuất CSV, đối chiếu (`LAG`), audit đọc; mã `EXPORT_TOO_LARGE` | `…/learning/credit/{LedgerQueryService,ReconciliationService}.java`, `…/credit/web/LedgerAdminController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-017…FR-019 · AT-15…AT-17 | ⬜ |
| T-12 | `learning.api.EntitlementApi` + `PaidFeature` + `Consumption` + `EntitlementService` (hạn mức → điểm → chặn, idempotency Redis, hoàn); mã `INSUFFICIENT_CREDITS` | `…/learning/api/{EntitlementApi,PaidFeature,Consumption}.java`, `…/learning/credit/EntitlementService.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-020…FR-022 · AT-18…AT-20 | ✅ MERGED — PR #134, main 95f401f; giữ G1/G2 FAILED history trong questions/evidence |
| T-13 | `PlanMaintenance` job hạ gói hết hạn (cron cấu hình, test đặt `-`) | `…/learning/credit/PlanMaintenance.java`, `application.yaml` + test | FR-023 · AT-21 | ⬜ |

## Đợt 3 · Nối thật

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-14 | FE SCR-030/031/073/074/075 gọi backend thật; sửa lệch contract (lệch thì dừng báo leader); test hợp đồng `/v3/api-docs` so `openapi-credit.yaml` (path + status) | `web/features/credits/**`, `t/contract/CreditContractTest.java` | §13 · AT-22…AT-27 chạy với API thật | ⬜ |

## Thứ tự thực thi

```
T-01 → T-02 → T-03 → T-04
T-05                               (thuần, làm ngay)
T-06 → T-07 → T-08 → T-09
T-06 → T-10
T-06 → T-11
T-06 → T-12
T-06 → T-13
(T-04, T-09, T-10, T-11) → T-14
```

**Phụ thuộc spec khác:** chỉ spec 001 (đã xong). Mã lỗi thêm ngay trước `INTERNAL_ERROR` theo thứ tự merge thực tế (luật L2).

- T-02…T-04 dùng `catalog.preview.ts`; không chờ backend.
- T-07, T-08, T-10, T-11, T-12 cùng đụng `ErrorCode.java` nên merge lần lượt.
- **013 không phụ thuộc 010/011/012** → luồng làm 013 chạy song song hoàn toàn.

**Bốn task rủi ro cao:** T-08 (một mã một lần dưới tải đồng thời, không lộ mã) · T-06 (sửa `AuthApi` của 001, khoá dòng `users`) · T-12 (không trừ hai lần, không âm) · T-11 (audit đọc, che email).

**Hiện trạng: 10/14 task đã merge (T-01…T-09 và T-12). T-09 MERGED — PR #147, merge7054115. T-10 G1 STOP Round2 sau regression2383/F4/E0/S0 FAILED/native1, dù đúng152 suites/ngoài whitelist0; contract4 và targeted213 PASS/native0 không thay verdict. Base de732866/Q1#2/exact19; Q4 mới nhất chỉ validate semantics FREE name/description và JSON types field bị bỏ qua. Ordered25 same-Maven isolation, Full verify/JaCoCo/G2 NOT RUN; chưa G1 hoàn tất/G2/G3 hoặc merge T-10. Giữ mọi FAILED/UNKNOWN/STOP và recovery760/760. Chờ Leader quyết định mới; main948a89f chỉ Leader báo, chưa fetch/tích hợp.**

Lịch sử G1 Fix1/2 T-08: SPEC-014/T-14 đã merge PR #135 tại main 9510643; TranslationService đã sử dụng Entitlement API. Regression T-09 bao gồm caller translation, các kiểm tra 503/402 hiện có và practice/review suites đã merge ở base mới. Không dùng evidence T-08 để thay thế verification T-09.

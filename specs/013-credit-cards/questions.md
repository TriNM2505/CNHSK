# Chốt — Spec 013

## 2026-11-03 — Leader duyệt Shadow Plan 013/T-01

- Spec §0.4 và T-01 ghi nhầm `shared/auth/navigation.ts` (chỉ xử lý redirect); menu thực tế ở `shared/layout/navigation.ts`.
- T-01 thêm ba mục tài chính vào `adminNavigation`, mỗi mục chỉ có `role: 'ADMIN'`. Không sửa `learnerNavigation`, `LearnerLayout`, `PublicLayout`; menu học viên chờ PR điều hướng chung do leader giao.
- `screens.ts = {} satisfies ScreenCatalog`, theo tiền lệ 009/T-15 và 014/T-01. `ScreenRoute` tự hiển thị màn tạm; T-02–T-04 đăng ký page thật.
- `catalog.preview.ts` export fixture tự soạn và đăng ký vào `app/preview/registry.ts` ngay ở T-01. Mã mẫu không dùng được; không dùng email hay dữ liệu tài chính thật.
- `Benefits` dùng `Partial<Record<PaidFeature, number>>`: mô tả OpenAPI và FR-016 chỉ cho bốn khoá `PaidFeature`, không bắt buộc đủ bốn. Không sửa contract.
- Base là `origin/main` mới nhất đã có #56 và #57. Nếu 012/T-01 merge trước, rebase lên main mới trước khi xin review vì cùng ba file registry/route.
- Leader cho phép đánh ✅ T-01 trong cùng PR; đúng 15 file, một conventional commit, PR Draft, không tự merge. Nháp mô tả PR để ngoài repo.

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md` §12, constitution v1.4.1 (§Bảy actor, §Mô hình kinh doanh, HR-05, AC-03/07/09, BUS-06, BUS-12…15), migration V1 (`credit_cards`, `credit_transactions`, `plans`, `auth.users`, `user_roles`), `database.md` (`feature_code`), UC-093…UC-101 (`use-cases-07`), `screen-fields.md` SCR-030/031/073/074/075, `CONTEXT.md` (Lượt), `AuthApi`, `CurrentUser`, `AuditService`, `RateLimiter`, `SecurityConfig` (`/api/me/**`, `/api/admin/**`), plan `261102-2104` phase 5, 6, 7. Đếm `ACCOUNTANT`: 224 lần trong 51 file.

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Ai sinh/khoá lô thẻ | Leader: *"đang tính bỏ ACCOUNTANT cho vào ADMIN vì đây là hệ thống phễu, không quản lý doanh thu"* → sau phân tích chọn **gộp nghiệp vụ vào ADMIN, giữ mã role ACCOUNTANT** (không migration); thêm luật **không nạp mã do mình tạo** |
| Q2 | Dùng điểm mua gói | **Không, theo UC** |
| Q3 | Mã SUBSCRIPTION kích hoạt gói nào | **Không migration, chỉ PREMIUM** |
| Q4 | Giá trị gói trả phí | **Quản trị nhập qua SCR-074** |
| Q5 | Dịch vụ trừ lượt/điểm | **Làm luôn** (`EntitlementApi`) |
| Q6 | Sổ cái SCR-075 | **Xem + xuất file + đối chiếu** |
| Q7 | Bắt buộc email đã xác minh khi nạp | **Không** |
| D | Mặc định | **Duyệt**: mã 16 ký tự base32 + HMAC (`CARD_CODE_PEPPER` trong `.env`); chặn 5 lần sai/giờ/người + 30/giờ/IP; lô ≤ 100, mã thô trả một lần; chỉ khoá mã chưa dùng; gia hạn cộng dồn, job đêm hạ về FREE; cấm nạp mã do mình tạo; không migration; FE trước; chỉ file trên máy |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| W1 | Hiến pháp §Bảy actor, `business.md` §1, BR-099-2, BR-100-2, BR-101-2, SCR-073…075: chỉ `ACCOUNTANT`; "ADMIN không sinh mã, không đụng sổ cái" | Q1 — **RFC, điều kiện APPROVED** |
| W2 | Plan phase 5 "dùng điểm mua gói" | Q2; sửa plan |
| W3 | AC-09 JSONB chỉ đọc ↔ `users.free_usage` ghi mỗi lần tiêu thụ (V1) | Giữ V1, ghi ngoại lệ trong RFC |
| W4 | `credit_cards` không có cột gói | Q3 |
| W5 | UC-099/CONTEXT đề xuất 1 000 mã/lô ↔ SCR-073 chốt 100 | 100 |
| W6 | UC07 khoảng trống #19: chống lặp tiêu thụ cần lưu bền | Redis 24 giờ, không thêm cột; rủi ro §11 |
| W7 | UC-094 400 chung ↔ UC-136 404/409/410 | 400 chung |
| W8 | Red-team #6 (plan) "ACCOUNTANT và ADMIN" | Thay bằng Q1 |
| W9 | 015 trợ lý "hỏi thoải mái" ↔ `ASSISTANT` là một trong 4 tính năng tốn phí | 015 không gọi `EntitlementApi`; ghi lại khi viết 015 |

### Còn mở

- RFC Q1 (W1, W3) cần cả nhóm đồng thuận.
- Plan phase 5 cần sửa theo Q1, Q2, Q5, Q6 (chờ leader cho phép như phase 3, 4).

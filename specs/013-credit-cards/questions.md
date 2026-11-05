# Chốt — Spec 013

## 2026-11-05 — Leader duyệt T-02 và thiết kế bổ sung Page + View + API

Phạm vi: SCR-030, SCR-031; FR-024, FR-025; AT-22, AT-23. Đúng 12 file trong allowlist bổ sung của leader; một commit và Draft PR. Leader cho phép đánh ✅ T-02 sau khi gate đạt. Không mở lại menu tài chính; không sửa route, cấu hình ESLint, contract, types hay API T-01.

### Thiết kế đã chốt

- `Scr030PlanCreditsPage` dùng `getMyWallet(signal)` và `redeemCard({code})` từ `api/wallet.ts`; hiển thị ngay `RedeemResult`, rồi tải lại ví. Lỗi tải lại ví có vùng lỗi riêng, không đổi kết quả nạp thành thất bại.
- `Scr031TransactionHistoryPage` dùng `listMyTransactions({page, size: 20}, signal)`; trang API bắt đầu từ 0. `useApiQuery` bỏ qua phản hồi cũ và huỷ signal khi đổi trang/unmount; có test chuyển nhanh 1 → 2 → 1.
- `PlanCreditsView`, `TransactionHistoryView` nhận props dữ liệu/trạng thái/callback; giữ logic nhập mã, xác nhận nội tuyến, countdown, thời gian Việt Nam và điều khiển phân trang. Page/View không import fixture.
- `catalog.preview.ts` render hai View bằng fixture T-01, giữ ID và ba preview quản trị. Chỉ preview giữ trạng thái/bộ xử lý demo; không gọi API thật.
- `test-fixtures.ts` chỉ được các file test import; test mock module API và tạo dữ liệu mới bằng `structuredClone` trong `beforeEach`. Không có dữ liệu mẫu trong luồng Page gọi API.

### Q-T02-01…03

| Mã | Leader chốt |
|---|---|
| Q-T02-01 | FE bỏ khoảng trắng và `-`, đổi chữ ASCII thành hoa, nhóm 4 ký tự, không gạch cuối; chỉ kiểm đủ 16 ký tự `[A-Z0-9]`. Lượt nhập/dán có ký tự lạ hoặc quá dài bị từ chối nguyên lượt, hiện gợi ý định dạng. Crockford base32 và quy đổi O/I/L thuộc server T-08. Preview chỉ nhận đúng mã mẫu đã chỉ định; mã khác lỗi chung. |
| Q-T02-02 (cập nhật, thay chốt cũ) | Trạng thái và xử lý demo chỉ nằm trong `catalog.preview.ts`, trên bản sao. Fixture T-01 có ví và kết quả nạp cùng `balance=121`, trong khi `amount=100`: không dùng chúng nguyên trạng như snapshot trước/sau một lần nạp. Preview tính kết quả nhất quán với ví đầu vào, giữ nguyên fixture gốc; T-14 lưu ý khi đối chiếu dữ liệu thật. Page dùng kết quả server và tải lại ví. |
| Q-T02-03 | Không làm bộ lọc lịch sử. `screen-fields.md` có nhắc bộ lọc nhưng FR-025, AT-23 và contract chỉ có bảng + phân trang 20. Nếu cần, leader mở task riêng. |

### Ghi chú kỹ thuật

- Không spy hàm timer toàn cục khi dùng vi.useFakeTimers trên Vitest 2.1.9: restoreAllMocks gắn lại hàm giả.
- Chẩn đoán timer: trước 429 = 0; sau 429 = 2; sau tiến 1 giây = 1; sau unmount = 0. Page chỉ tạo một interval 250 ms; timer còn lại là `setImmediate` của jsdom cho sự kiện `select` sau `setSelectionRange`.
- Test cleanup dùng hành vi: số timer tăng sau 429, countdown giảm, unmount + chạy timer đang chờ trở về mức ban đầu; tiến thêm 10 giây không nạp lại/không `console.error`. Có ca kiểm đầu test tiếp theo dùng timer thật và `setTimeout` không có `.clock`. Không còn spy trên hàm timer.
- Helper nhập mã dùng `getByRole('textbox', { name: 'Mã thẻ' })` để tránh nhãn trùng. Kỳ vọng AT-22/AT-23 giữ nguyên ý nghĩa.
- `retryAfterSeconds` thiếu, sai kiểu, NaN, âm hoặc vô hạn dùng thông báo chung, không tạo countdown. Có test kết quả nạp thành công vẫn hiện khi tải lại ví lỗi, cùng kết quả muộn sau unmount/đổi tài khoản.

### Gate trên bản cuối

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `npm ci` | PASS; cài 333 package theo lockfile |
| Hai file test page, `--minWorkers=2 --maxWorkers=2` | 52 pass, 0 fail, 0 skip (SCR-030: 41; SCR-031: 11) |
| Shuffle hai file, seed `2611051`, `2611052`, `2611053` | Cả 3 lượt: 52 pass, 0 fail, 0 skip |
| `npm run test -- --minWorkers=2 --maxWorkers=2` | 151 file pass; 2.048 test pass, 0 fail, 0 skip; 205,35 giây |
| `npm run lint` | PASS; 0 error, 1 warning có sẵn tại `shared/ui/LevelBadge.tsx:6` (`react-refresh/only-export-components`) |
| `npm run build` | PASS; TypeScript + Vite 5.4.21, 2.203 module; Vite build 4,68 giây |

Lượt toàn bộ cuối không có test chập chờn thất bại, không cần chạy riêng lại file ngoài T-02. API T-01 khớp contract và giữ nguyên; kiểm thử tích hợp với backend thật và đối chiếu `/v3/api-docs` vẫn thuộc T-14.

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

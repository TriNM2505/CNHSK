# Chốt — Spec 013

## 2026-11-06 — Leader duyệt T-04: SCR-074 và SCR-075

Người làm: Tường. Base `origin/main` = `a3dc0da`, đã có T-03 (#113). Phạm vi FR-027, FR-028; AT-25, AT-26. Leader duyệt đúng 12 file, một conventional commit và Draft PR; cho phép đánh ✅ riêng T-04 sau khi gate FE đạt. Không sửa API/types/contract, routeAccess, screenRegistry, navigation, ESLint hoặc shared. Backend T-05…T-13 do NgoQuocHuyy làm; không chạy `mvnw verify` trong task FE này.

### Q-T04-01…03

| Mã | Leader chốt và cách áp dụng |
|---|---|
| Q-T04-01 | Giữ contract/FR-017: `from`, `to` là ngày `YYYY-MM-DD` theo giờ Việt Nam. Yêu cầu ban đầu về gửi UTC timestamp đã được đính chính. FE gửi nguyên ngày được chọn; backend đổi biên ngày sang UTC. API T-01 giữ nguyên. |
| Q-T04-02 | `to` bao gồm ngày cuối; kiểm `0 ≤ to − from ≤ 92` ngày. Hai ngày cách đúng 92 ngày hợp lệ, bao phủ 93 ngày lịch khi tính cả hai đầu. |
| Q-T04-03 | Khi gói hiện có thiếu khoá trong `benefits`, hiển thị khoá đó bằng 0. Khi hạn mức không thay đổi, gửi nguyên snapshot map đã nhận, không tự thêm khoá 0. Chỉ dựng đủ bốn khoá khi có thay đổi giá trị hạn mức hoặc tạo PREMIUM. Giá niêm yết là chuỗi thập phân, chuẩn hoá bằng thao tác chuỗi, không tính tiền bằng số thực. |

### Cách hiểu chung FE–BE

- **Backend T-11 — ngày lọc:** nhận `from/to` là ngày Việt Nam, `to` bao gồm ngày cuối. Dùng phạm vi nửa mở `[00:00 Asia/Ho_Chi_Minh của from, 00:00 Asia/Ho_Chi_Minh của ngày sau to)`, rồi đổi hai biên sang UTC khi truy vấn timestamp. Kiểm hiệu hai ngày trong khoảng 0…92, không cộng thêm 1 khi kiểm giới hạn. Cùng quy tắc cho danh sách và xuất CSV. FE không gửi UTC timestamp thay cho tham số `date`.
- **Backend T-10 — so sánh hạn mức:** nên so sánh `benefits` theo giá trị của bốn tính năng, thiếu khoá coi là 0, để không trả `PLAN_IN_USE` nhầm. FE giữ map nhận được khi chỉ sửa tên/mô tả/giá/số ngày/cờ đang bán; việc hiển thị khoá thiếu là 0 không tự biến thành thay đổi cấu hình. Có test snapshot PREMIUM thiếu `ASSISTANT`, sửa giá vẫn không thêm khoá; sửa giá trị hạn mức thì gửi đủ bốn khoá.
- **Đối chiếu:** FR-019 và API không nhận bộ lọc; kết quả luôn là toàn sổ. UI ghi rõ phạm vi toàn sổ, không suy luận lỗi chuỗi từ trang dữ liệu hoặc bộ lọc loại. Không tự điều chỉnh giao dịch. Phần legacy trong `screen-fields.md` về tra cứu tài khoản/điều chỉnh nằm ngoài phạm vi đã khoá của T-04.
- **CSV:** server tạo CSV; FE giữ nguyên Blob và dùng tên server trả, thiếu tên thì `credit-ledger.csv`. Không dựng lại CSV từ trang đang xem, không tự che/mở che email. `EXPORT_TOO_LARGE` hướng dẫn thu hẹp ngày/loại, không tải file và không xoá bảng/bộ lọc.

### Thiết kế và ghi chú kỹ thuật T-04

- `Scr074PlanManagementPage` gọi `listPlans(signal)` và `updatePlan(code, body)` từ `api/plans.ts`; kiểm ba mã gói đúng contract. View có form FREE/PREMIUM, PREMIUM_PLUS chỉ đọc, số người đang dùng từ server, xác nhận lưu ngay trong trang. `PLAN_IN_USE` gắn lỗi và giải thích vào các ô hạn mức, giữ draft. Kết quả lưu thành công vẫn hiện khi tải lại danh sách lỗi.
- `Scr075CreditLedgerPage` dùng `listLedger({from, to, type?, page, size: 20}, signal)`, `exportLedger(filter, signal)`, `reconcileLedger(signal)` từ `api/ledger.ts`. Ban đầu chưa có ngày thì không gọi danh sách; chỉ truy vấn sau khi áp dụng bộ lọc hợp lệ. Tổng dùng `totals` của toàn bộ bộ lọc từ server, mang nhãn "Tổng điểm"; email hiển thị nguyên `maskedEmail`. Các thời điểm giao dịch dùng `Intl.DateTimeFormat` với `Asia/Ho_Chi_Minh`.
- Export gắn với bộ lọc đã áp dụng; sửa draft/áp dụng phạm vi mới hoặc unmount sẽ huỷ request và bỏ qua Blob muộn, không tự tải. Chốt chống gửi lặp và thế hệ request ngăn phản hồi cũ xoá pending của request mới. Đối chiếu có trạng thái/lỗi/thử lại riêng và danh sách ba loại lỗi theo contract.
- Page/View không import preview hoặc test fixture. `test-fixtures.ts` chỉ được các file test import; dữ liệu mới tạo bằng `structuredClone` trong từng ca. Hai preview render View và callback demo trên bản sao, giữ fixture gốc, năm preview ID và ba preview T-01…T-03. Chỉ preview mô phỏng file CSV, Page thật luôn nhận file từ API.
- Không `confirm()`/dialog portal, không spy hàm timer toàn cục. Cleanup dùng cờ vòng đời và huỷ `AbortController`; đã bỏ hai thao tác tăng bộ đếm dư gây warning ref trong cleanup SCR-075, không tắt rule hay sửa ESLint. Sau sửa đã chạy lại test mục tiêu, ba seed, toàn bộ Vitest, lint và build.

### Gate T-04 trên bản cuối

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `npm ci` | PASS; 333 package theo lockfile |
| `npm run typecheck` | PASS |
| Hai Page T-04 + `credits/routes.test.tsx`, 2 worker | 137 pass, 0 fail, 0 skip; SCR-074: 40, SCR-075: 36, routes/preview: 61; 11,94 giây |
| Shuffle ba file, seed `2611064`, `2611065`, `2611066`, 2 worker | Cả ba: 137 pass, 0 fail, 0 skip; lần lượt 11,82 / 12,42 / 11,76 giây |
| `npm run test -- --minWorkers=2 --maxWorkers=2` | 154 file pass; 2.174 test pass, 0 fail, 0 skip; 209,73 giây |
| `npm run lint` | PASS; 0 error, chỉ 1 warning có sẵn `shared/ui/LevelBadge.tsx:6` (`react-refresh/only-export-components`) |
| `npm run build` | PASS; TypeScript + Vite 5.4.21, 2.212 module; Vite build 4,71 giây |

Lượt toàn bộ cuối không có fail ngoài T-04 nên không cần chạy riêng file test chập chờn. T-14 còn phần tích hợp backend thật; không mở lại menu tài chính trong T-04.

## 2026-11-06 — Leader duyệt T-03: SCR-073 Card Batch Management

Người làm: Tường. Phạm vi FR-026, AT-24; validation/payload theo FR-005…FR-008 và quyền FE theo FR-001. Base `origin/main` = `a0ca1fd`, đã có T-01 (#59), PR ẩn menu #61 và T-02 (#110). Leader duyệt đúng 9 file, một conventional commit và Draft PR; cho phép đánh ✅ riêng T-03 sau khi gate FE đạt. Backend T-05…T-13 thuộc NgoQuocHuyy; T-03 không sửa backend hay chạy `mvnw verify` theo chốt leader.

### Q-T03-01…04

| Mã | Leader chốt và cách áp dụng |
|---|---|
| Q-T03-01 | Chỉ bảng lô, phân trang 20 và số liệu từng lô từ `BatchSummary`. `screen-fields.md` còn nhắc bộ lọc, trạng thái từng mã và tổng hợp theo chiến dịch, nhưng contract không có API tương ứng; nếu cần leader mở task riêng. |
| Q-T03-02 | Xác nhận ngay trong trang cho cả phát hành và khoá lô, không dùng `confirm()` hoặc dialog portal. Xác nhận phát hành hiện đúng kênh, loại, số lượng, giá trị/số ngày, metadata sẽ gửi và hạn dùng theo giờ Việt Nam; huỷ giữ form và trả focus. Khoá chỉ tác động mã chưa dùng; số thực tế lấy từ server, chấp nhận `voided=0`. |
| Q-T03-03 | CSV tạo tại client: UTF-8 không BOM, xuống dòng `\r\n`, header `batchId,code`, một mã mỗi dòng; tên `card-batch-<batchId>.csv`, không có dữ liệu người nhận. Test kiểm byte BOM, UTF-8, CRLF, nội dung/tên file và không có ô bắt đầu bằng `=`, `+`, `-`, `@`. Dùng helper `downloadBlob` có sẵn, không sửa shared. |
| Q-T03-04 | Mã ở bộ nhớ màn đến khi rời/tải lại màn hoặc tạo thành công lô kế tiếp. Tải CSV không xoá mã. Khi mã cũ còn hiện, xác nhận cảnh báo "Mã lô trước sẽ mất khi phát hành thành công lô mới." Tạo lô mới thất bại giữ mã cũ. Khi lỗi mạng/kết quả POST không xác định: không tự gửi lại, hiện "Không rõ lô đã được tạo chưa — kiểm tra bảng lô trước khi tạo lại" và tải lại danh sách. |

### Thiết kế và ghi chú kỹ thuật T-03

- `Scr073CardBatchPage` dùng `listCardBatches({page, size: 20}, signal)`, `createCardBatch(body)`, `voidCardBatch(batchId)` từ `api/cardBatches.ts` T-01. API/types khớp contract và giữ nguyên. Page kiểm `ADMIN`, nội dung được tạo mới khi đổi tài khoản; đọc dùng `useApiQuery`, mutation có chốt chống gửi lặp và bỏ qua kết quả khi unmount.
- `CardBatchView` chỉ nhận props dữ liệu/trạng thái/callback. Form không ngầm chọn loại/kênh; đổi loại/kênh bỏ dữ liệu điều kiện cũ. Kiểm giới hạn số nguyên và độ dài theo contract; `SUBSCRIPTION` chỉ PREMIUM. Ô hạn dùng ghi rõ giờ Việt Nam, chuyển UTC khi gửi; hiển thị qua `Intl.DateTimeFormat` với `Asia/Ho_Chi_Minh`.
- Sau phát hành hiện ngay `CreatedBatch`, rồi tải lại trang đầu của danh sách. Lỗi tải danh sách có vùng lỗi/thử lại riêng, vẫn giữ mã và tải CSV. Có nút tải lại bảng để kiểm tra trạng thái mà không phải rời màn. Khoá lô chỉ thay số liệu sau khi server trả kết quả và tải lại; không sửa/xoá mã đã dùng.
- Preview `credits-batch-samples` render View và callback demo trên bản sao fixture T-01; giữ năm ID, fixture gốc và bốn preview còn lại. Mã demo tự soạn, có `U` ngoài bảng Crockford nên không dùng được. Test Page mock API, fixture chỉ lấy từ `test-fixtures.ts`; file này chỉ được test import. Không import fixture vào Page/View/screens.
- Không ghi mã vào storage/URL/log. Bảng đọc từ `BatchSummary`, không có mã thô/hash. Giữ guard/redirect và kiểm menu tài chính đang ẩn; route `/admin/card-batches` và shared registry/navigation không sửa.
- Giữ bài học timer của T-02: không spy hàm timer toàn cục. T-03 không tạo timer riêng. Có test phản hồi cũ khi chuyển nhanh trang 1 → 2 → 1, huỷ GET khi unmount, bỏ qua mutation sau unmount/đổi tài khoản, cùng kiểm fixture không bị sửa.
- Lượt build đầu phát hiện 6 lỗi `TS2769` trong hai file test T-03 do thêm `exact` vào `getByRole`. Đã bỏ tuỳ chọn không thuộc API này; `name` dạng chuỗi vẫn khớp chính xác, không đổi assertion AT-24. `npm run typecheck` đạt, sau đó chạy lại toàn bộ chuỗi gate dưới đây trên bản cuối.

### Gate T-03 trên bản cuối

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `npm ci` | PASS; 333 package theo lockfile |
| SCR-073 + `credits/routes.test.tsx`, 2 worker | 110 pass, 0 fail, 0 skip; Page 49, routes/preview 61; 11,31 giây |
| Shuffle hai file, seed `2611061`, `2611062`, `2611063`, 2 worker | Cả ba: 110 pass, 0 fail, 0 skip; lần lượt 11,07 / 11,12 / 10,84 giây |
| `npm run test -- --minWorkers=2 --maxWorkers=2` | 152 file pass; 2.098 test pass, 0 fail, 0 skip; 210,34 giây |
| `npm run lint` | PASS; 0 error, chỉ 1 warning có sẵn `shared/ui/LevelBadge.tsx:6` (`react-refresh/only-export-components`) |
| `npm run build` | PASS; TypeScript + Vite 5.4.21, 2.206 module; Vite build 4,81 giây |

Lượt toàn bộ cuối không có fail ở file ngoài T-03, không cần chạy riêng lại theo quy tắc test chập chờn của leader. Tích hợp với backend thật thuộc T-14; không mở lại menu tài chính trong task này.

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

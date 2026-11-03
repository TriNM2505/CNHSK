# Chốt — Spec 014

## 2026-11-04 — Leader duyệt 014/T-02

- SCR-024/025 là hai màn thật đầu tiên của library; dùng API client T-01, preview/test bằng dữ liệu tự soạn. Nghiệm thu nối backend thật ở T-15.
- Đã đăng nhập nhưng không có `LEARNER`: ẩn thao tác lưu và hiện **"Cần quyền học viên để lưu"**. Khách thấy **"Đăng nhập để lưu"**; chỉ `LEARNER` gọi API sổ tay/bộ thẻ.
- Hoạt hình chỉ hiện với mục từ `CHAR` có `strokeData != null`. Dùng nguyên `HanziCanvas` và loader dữ liệu của 006; không truyền payload nét vào canvas. `onDataError` ẩn riêng khối hoạt hình, phần còn lại của trang vẫn hiển thị. Chỉ xem/hoạt hình, không gọi quiz, ghi lượt luyện hoặc đổi mastery.
- Không đổi contract hay thêm bộ lọc HSK khi API chưa hỗ trợ. Giữ truy vấn/bộ lọc/trang khi đi sang chi tiết rồi quay về.
- Spec 013 và 015 tạm gác; 014/T-13, T-14 cũng gác theo phân công mới. Nút gợi ý dịch của `LEARNER` chỉ điều hướng tới route có sẵn; T-02 không gọi dịch hoặc entitlement.
- Leader cho phép đánh ✅ T-02 trong cùng PR; allowlist 14 file, một conventional commit, PR Draft, không tự merge.

## 2026-11-03 — Leader duyệt điều chỉnh 014/T-01

- Spec §0.4 và T-01 ghi nhầm `shared/auth/navigation.ts`: file này chỉ xử lý đích sau đăng nhập; menu thực tế ở `shared/layout/navigation.ts`.
- T-01 chỉ thêm route, catalog màn và preview; không sửa `shared/auth/navigation.ts`, `shared/layout/navigation.ts`, `LearnerLayout.tsx`, `PublicLayout.tsx` hoặc `Layouts.test.tsx`.
- Menu học viên chuyển sang PR riêng **điều hướng học viên chung**, sau khi các T-01 của 011, 012, 014, 015 merge; leader giao riêng.
- `screens.ts` dùng `{} satisfies ScreenCatalog`. Không đăng ký `ScreenUnderConstructionPage`: `ScreenRoute` đã tự hiển thị màn tạm cho SCR chưa có page. T-02–T-05 đăng ký page thật.
- Ghi nhận cùng lỗi đường dẫn ở 013/T-01: khi làm mục điều hướng ADMIN, sửa `shared/layout/navigation.ts` theo phân công của leader.
- Leader cho phép cập nhật ✅ T-01 trong PR này; một commit, PR Draft, không tự merge.

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md`, constitution v1.4.1 (BUS-01/03/04/09/10, HR-07, AC-09), migration V1 (`lexemes`, `lexeme_links`, `collections`, `collection_items`, `user_progress`, index trigram), UC-056…UC-068 (`use-cases-05`), `screen-fields.md` SCR-024…SCR-029, `SecurityConfig` (`/api/public/**`), plan `261102-2104` phase 6, 7, spec 011 (FR-019 FSRS), spec 013 (FR-020/FR-021 `EntitlementApi`).

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Ôn flashcard | **Trong SCR-029, 4 nút** (Quên/Khó/Được/Dễ), dùng `FsrsScheduler` 011, ≤ 100 thẻ/buổi, không đổi mastery |
| Q2 | Dịch câu SCR-026 | **Làm bằng Claude**, trừ lượt `TRANSLATE` qua `EntitlementApi` 013, hoàn khi lỗi |
| Q3 | Giới hạn tra cứu | **GUEST 60/giờ/IP**, LEARNER 600/giờ, cấu hình được |
| Q4 | Tra bộ thủ + số nét | **Làm bộ lọc đơn giản** |
| Q5 | Bộ từ chủ đề | **Có, chép từ chủ đề 003** |
| Q6 | Ghi chú đã xoá | **Khôi phục trong 30 ngày** |
| T1 | Lịch sử dịch | **Không lưu, chỉ cache** 7 ngày |
| T2 | Model dịch | **`claude-haiku-5-5`** |
| D | Mặc định | **Duyệt**: GUEST thấy đủ trường (chỉ ẩn nút lưu); không tìm thấy → 200 rỗng; dữ liệu riêng → 403; một sổ tay mặc định; thùng rác không có job xoá hẳn; chia sẻ bộ là V2; dịch theo UC-060; không migration; FE trước |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| V1 | UC-066 3 mức tự đánh giá ↔ SCR-013 4 mức | Q1: 4 mức |
| V2 | Giới hạn khách: UC-056 theo giờ (ví dụ 50) ↔ UC-132 theo ngày | Q3 |
| V3 | UC-056 "kết quả rút gọn" cho khách không định nghĩa | Đủ trường |
| V4 | UC-056 404 ↔ UC-132 200 khi không có kết quả | 200 rỗng |
| V5 | UC-064 `NOTE_NOT_FOUND` 404 ↔ `BUS-01` 403 | 403 `COLLECTION_NOT_OWNED` |
| V6 | UC-133 khôi phục ghi chú, thời hạn chưa duyệt | Q6 30 ngày — ghi RFC |
| V7 | Plan phase 6 "không dịch câu (tốn phí)" | Q2 — sửa plan phase 6 |
| V8 | Thẻ flashcard và mục từ 011 cùng một từ → hai lịch ôn | Chấp nhận (SCR-029) |
| V9 | UC-060 dùng bảng `feature_usage`, `translation_history` không có trong V1 | Dùng `EntitlementApi` + cache Redis (T1) |

### Còn mở

- Plan phase 6 cần sửa theo Q2 (có dịch), Q1 (ôn FSRS 4 mức), Q6 (thùng rác) — hỏi leader trước khi sửa.
- Thêm vào RFC chung: V6 (thời hạn 30 ngày).
- `ANTHROPIC_API_KEY`: leader đặt trong `.env` khi chạy dịch/trợ lý thật (nhắc cùng 015).

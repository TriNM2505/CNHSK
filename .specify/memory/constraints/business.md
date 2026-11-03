# constraints/business.md — Ràng buộc NGHIỆP VỤ

**Version:** 1.1.0 | **Cập nhật:** 2026-11-03 (RFC đợt cuối R1, R3, R9 — Hiến pháp v1.5.0) | **Maintainer:** Trí (leader) + cả nhóm
**Enforcement:** Code review + CI (logic nghiệp vụ cần người verify, không tự động 100%)

> 📎 **Mã chuẩn tắc ở hiến pháp.** File này giải thích CHI TIẾT; mã `BUS-01…BUS-15` và
> thứ tự ưu tiên nằm ở `constitution.md` mục *Quy tắc nghiệp vụ toàn dự án*.
> Khi hai file lệch nhau, **hiến pháp thắng**.

> Những rule "hiển nhiên với người nhưng AI không biết trừ khi được nói" — playbook gọi là
> anti-pattern *Implicit Assumption*.
> Trả lời câu hỏi: **"Nghiệp vụ hoạt động ra sao?"**

---

## 1. Authentication & Authorization

- **Mật khẩu:** hash bằng `bcrypt` cost ≥ **12** hoặc `argon2id`. Không bao giờ log plaintext
- **JWT:** **HS256** (chốt 2026-10-20, Hiến pháp v1.4.1), secret ≥ 256 bit từ biến môi trường `JWT_SECRET`.
  Access token **15 phút**, refresh token **30 ngày**, refresh lưu DB dạng hash, xoay vòng mỗi lần làm mới
- **Cookie cho web:** đủ 5 thuộc tính `domain=cnhsk.com` · `httpOnly` · `secure` ·
  `sameSite=Lax` · `maxAge`. Bỏ `domain` thì `game.cnhsk.com` không nhận được cookie
- **Header cho mobile:** React Native không có cookie như trình duyệt → dùng
  `Authorization: Bearer`. WebView chơi game: app tiêm token vào header
- **RBAC 6 role trong DB:** `LEARNER` · `TEACHER` · `MODERATOR` · `CONTENT_EDITOR` ·
  `ACCOUNTANT` · `ADMIN`. Từ 2026-11-03 `ACCOUNTANT` **giữ mã nhưng không có quyền** (RFC R9).
  Kiểm role từ JWT; trái quyền → **403** (không trả 401/404 để giấu endpoint)
- **Một người có nhiều role được** — bảng nối `user_roles` đã thiết kế cho việc này
- **Cộng 1 actor KHÔNG phải role trong DB:** `GUEST` — xem §1.1

| Role | Tên | Quyền | Phạm vi |
|---|---|---|---|
| `LEARNER` | Người học | Học, thi, chơi game, đăng bài (chờ duyệt) | người dùng cuối |
| `TEACHER` | Giáo viên | **Duy nhất** duyệt câu hỏi AI sinh và sửa nội dung câu hỏi; **chấm bài thuê** | chất lượng học thuật |
| `MODERATOR` | Kiểm duyệt viên | Duyệt bài đăng, xử lý báo cáo vi phạm | Community |
| `CONTENT_EDITOR` | Biên tập viên nội dung | Nhập dữ liệu, quản lý đề thi và kho câu hỏi (trạng thái, xếp vào đề — **không** sửa nội dung câu), cuộc thi, tài liệu tham khảo | vận hành nội dung |
| `ACCOUNTANT` | Kế toán | Không có quyền (giữ mã trong DB, RFC R9) | — |
| `ADMIN` | Quản trị viên | Quản lý người dùng, gán role, cấu hình hệ thống, xem lịch sử đăng nhập · 🔴 **tài chính**: sinh/khoá lô mã thẻ, quản lý gói, xem/xuất/đối chiếu sổ cái | quản trị tổng + **tiền thật** |

> 🔴 **Vì sao gộp tài chính vào `ADMIN` (RFC R9, 2026-11-03).** CNHSK là sản phẩm phễu, không quản lý
> doanh thu, nên không cần role kế toán riêng. Bù cho việc mất tách quyền — **bắt buộc**:
> không ai nạp được mã do chính mình tạo; `NFR-S08` ghi audit cả thao tác **đọc** sổ cái;
> `ADMIN` không tự cấp role cho mình. `CONTENT_EDITOR` không có quyền tài chính.
>
> `ADMIN` vẫn không phải role có mọi quyền: không duyệt hay sửa nội dung câu hỏi, không duyệt bài
> cộng đồng. Chỉ actor này mang chữ "Admin". Chi tiết: spec 013.

### 1.1 · Actor không phải role

| Actor | Số UC | Là gì | Kiểm quyền bằng |
|---|---|---|---|
| `GUEST` | 16 | Khách chưa đăng nhập, **không có dòng** trong `users` | **Không có JWT** — không phải `role_code` |

**Tổng: 7 actor = 6 role trong DB + `GUEST`.**

> ⚠️ Đừng thêm `GUEST` vào bảng `user_roles` — `GUEST` không có tài khoản.
>
> **Job định kỳ không phải actor.** Các job `@Scheduled` (hạ gói hết hạn, nhắc học, hoàn lượt
> quá hạn) nằm ở danh mục Background Job, chạy bằng quyền hệ thống, **không qua `JwtFilter`**.

> 📎 Canonical: constitution **HR-01**, **HR-03**, **HR-09**.

---

## 2. Điểm và tiền (money-critical)

- Mọi field **tiền**: DB `NUMERIC(12,2)`, Java `BigDecimal`. KHÔNG `double`/`float`
- Mọi field **điểm**: DB `BIGINT`, Java `Long`. KHÔNG `double`/`float`
- Số dư điểm **KHÔNG bao giờ âm** — DB `CHECK` + service validate
- Mọi `UPDATE` số dư PHẢI kèm một `INSERT` vào `credit_transactions` trong **cùng một
  transaction** (audit trail)
- **Chấm bài thuê:** trừ điểm đúng lúc gửi yêu cầu; teacher không nhận trong hạn thì **hoàn điểm**

> ⚠️ `TODO(PAYMENT_SCOPE)` — chưa chốt tiền thật hay giả lập. Đừng tự quyết.

> 📎 Canonical: constitution **AC-07**, **HR-05**, **BUS-02**, **BUS-03**, **BUS-14**, **BUS-15**.

---

## 3. Datetime

- DB: luôn `TIMESTAMPTZ`, lưu **UTC**. API JSON: ISO 8601 UTC
- Server logic: dùng `Instant` (UTC). Frontend convert `Asia/Ho_Chi_Minh` khi hiển thị
- Mọi `@Scheduled` PHẢI ghi rõ `zone = "Asia/Ho_Chi_Minh"`
- So sánh theo giờ Việt Nam (ví dụ nhắc lịch học buổi tối): PHẢI convert sang
  `Asia/Ho_Chi_Minh` **trước** khi so, không so trực tiếp giờ UTC

> 📎 Canonical: constitution **AC-08**, **BUS-08**.

---

## 4. Mastery và lộ trình học

> **Sửa theo RFC đợt cuối (R1, R3, R4, R5 — 2026-11-03).** Chi tiết: spec 010, 011.

- **Đơn vị đo mastery:** từng **nút năng lực KID** của Cây Năng Lực v2.0 (vd `NP3-004`), 6 miền
  `NA CH TV NP NL VB`. Một từ, chữ, chủ đề **không** phải nút — chúng **nối vào** nút (`knowledge_point_id`).
  Không đo theo bài học
- **Thuật toán mastery: MP-1.0** — trung bình có trọng số của ≤ 8 minh chứng gần nhất (bối cảnh ×
  dạng câu × vai trò × bán rã 30 ngày), thang **0–100**; trạng thái `notyet · insufficient · review (< 60) ·
  consolidate (60–79) · extend (≥ 80) · inferred`. **"Vững" = ≥ 80**
- **FSRS** vẫn là thuật toán **lịch ôn mục từ** (từ, chữ, ngữ pháp, flashcard) — `BUS-04` giữ nguyên;
  FSRS **không** ghi mastery của nút
- **Cập nhật khi:** nộp bài thi · luyện tập · kiểm tra chủ đề · bài xếp trình độ · **chơi game** — cùng
  transaction với lúc lưu bài/điểm (`AC-10`). Luyện viết chưa tính (`BUS-16` còn treo)
- **Biên học tập:** nút được gợi ý học khi mọi tiên quyết đạt **củng cố (≥ 60)**; chỉ gợi ý + tô màu,
  **không khoá** nội dung
- **Ngưỡng qua cổng chủ đề: 90%** — đạt thì mở chủ đề tiếp theo (`BUS-05`, không đổi)
- Chưa đạt 90% → hệ thống **chỉ sinh bài cho phần yếu**, không bắt học lại cả chủ đề

> 📎 Canonical: constitution **AC-10**, **BUS-02**, **BUS-04**, **BUS-05**.

---

## 5. Game và bảng xếp hạng (chống gian lận)

Trang game chạy trên trình duyệt — người dùng **sửa được mọi thứ** bằng DevTools.

Server PHẢI kiểm **ba điều kiện** trước khi lưu điểm:

| Nguy cơ | Cách chặn |
|---|---|
| Gửi điểm 999999 | Kiểm **trần điểm** theo từng game |
| Chơi 1 giây báo điểm tối đa | Kiểm **thời gian**: `answered_at − served_at` phải hợp lý |
| Gửi điểm liên tục | Kiểm **tần suất**: tối đa N ván/giờ mỗi tài khoản |
| Sửa danh sách từ để cộng mastery sai | Chỉ cộng mastery cho **từ server đã phát** ở bước lấy từ vựng |

> Đây không phải bảo mật tuyệt đối — chỉ cần đủ để bảng xếp hạng không vô nghĩa.

> 📎 Canonical: constitution **HR-06**, **BUS-09**, nguyên tắc **V. Không tin client**.

---

## 6. AI sinh nội dung — BẮT BUỘC kiểm duyệt

- Câu hỏi AI sinh vào DB với trạng thái **`PENDING_REVIEW`**
- **KHÔNG BAO GIỜ** hiện câu `PENDING_REVIEW` cho người học
- **Chỉ `TEACHER`** được duyệt và sửa nội dung câu hỏi. `CONTENT_EDITOR` và `ADMIN`
  **không** duyệt nội dung — duyệt cần chuyên môn kiến thức, và một người chịu trách nhiệm
  thì khi câu sai mới rõ ai sửa
- AI lỗi hoặc timeout → dùng ngân hàng câu hỏi có sẵn và ghi log, **không** để người học thấy lỗi

> Đây là điều nhóm đã chốt rõ: *"AI TỰ SINH, CÓ KIỂM DUYỆT"*.

> 📎 Canonical: constitution **BUS-07**.

---

## 7. API Rules

- **Phân trang:** list >50 bản ghi → server-side `Pageable`/`Page<T>` (mặc định 10, tối đa 100).
  List nhỏ → `List<T>` + phân trang phía client
- **Validation:** DTO dùng `@Valid` + Jakarta Bean Validation; vi phạm → **422** kèm danh sách field
- **Format lỗi thống nhất:** `{ "error_code": "...", "message": "...", "request_id": "..." }`
  Không trả plain string, không HTML, không stack trace
- **Empty / Loading / Error states:** mỗi trang có danh sách hoặc dữ liệu động PHẢI có đủ
  **ba trạng thái**, text tiếng Việt có dấu

> 📎 Canonical: constitution **HR-09**, **BUS-01**.

---

## 8. PII & Logging (mask bắt buộc)

| Loại | Cách mask | Ví dụ |
|---|---|---|
| Số điện thoại | ẩn 3 số giữa | `0912***456` |
| Email | ẩn phần local | `use***@domain.com` |
| **KHÔNG BAO GIỜ log** | mật khẩu · JWT secret · API key AI · key thanh toán | — |

---

## 9. Domain Glossary (thuật ngữ dễ hiểu sai)

| Thuật ngữ | Nghĩa chuẩn trong CNHSK |
|---|---|
| **Mastery** | Mức thành thạo 0–100 của một người với **một nút KID** (không phải với bài học hay chủ đề). Tính bằng MP-1.0; "vững" ≥ 80 (RFC R3, R4) |
| **Knowledge point** | **Một nút năng lực KID** của Cây Năng Lực v2.0 (vd `NP3-004`). Bảng `knowledge_points`. Từ/chữ/chủ đề nối vào nút qua `knowledge_point_id` (RFC R1) |
| **Biên học tập** | Các nút chưa vững mà mọi tiên quyết đã ≥ 60 — dùng để gợi ý, không khoá (RFC R5) |
| **Cổng kiểm tra (gate)** | Ngưỡng **90%** của một chủ đề. Đạt thì chủ đề sau mở |
| **Topic / Chủ đề** | Nhóm điểm kiến thức theo đề tài. Cây chủ đề có thứ tự mở dần |
| **Đề thi thử** | Chỉ **luyện tập**, KHÔNG mô phỏng thi thật. Mô phỏng thi thật đã bị loại khỏi scope |
| **Chấm bài thuê** | Người học trả điểm để teacher chấm bài viết. Bảng `grading_requests` |
| **Điểm (credit)** | Đơn vị trong hệ thống, `BIGINT`. Khác **tiền** (`NUMERIC(12,2)`) |
| **PENDING_REVIEW** | Trạng thái câu hỏi AI sinh chưa được duyệt. Không hiện cho người học |
| **Game** | Thuộc module **`community`**, không phải module riêng. Trang `game.cnhsk.com` là frontend riêng nhưng gọi cùng backend |
| **Sổ tay** | Ghi chú cá nhân dạng **TAKE NOTE**, không liên kết với tiến độ học |
| **Ba client** | Web chính `cnhsk.com` · Trang game `game.cnhsk.com` · Mobile React Native. Dùng **chung một backend** |
| **`import_runs`** | Bảng ghi lần nhập dữ liệu thầy cung cấp. Trước tên là `sync_runs` — đổi vì bản 5 không còn đồng bộ |

> ⚠️ **Thuật ngữ đã bỏ, đừng dùng lại:** `user_refs` · `topic_refs` · `vocab_refs` ·
> `sync_run_items` (4 bảng đã xoá ở bản 5) · "đồng bộ 2h sáng" (cơ chế đã bỏ hoàn toàn).

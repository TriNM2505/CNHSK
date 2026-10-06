# constraints/business.md — Ràng buộc NGHIỆP VỤ

**Version:** 1.0.0 | **Cập nhật:** 2026-09-28 | **Maintainer:** Trí (leader) + cả nhóm
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
- **JWT:** thuật toán chốt sau (HS256 hoặc RS256 — chưa quyết).
  Access token ngắn hạn, refresh token dài hạn, lưu DB dạng hash, có rotation
- **Cookie cho web:** đủ 5 thuộc tính `domain=cnhsk.com` · `httpOnly` · `secure` ·
  `sameSite=Lax` · `maxAge`. Bỏ `domain` thì `game.cnhsk.com` không nhận được cookie
- **Header cho mobile:** React Native không có cookie như trình duyệt → dùng
  `Authorization: Bearer`. WebView chơi game: app tiêm token vào header
- **RBAC 6 role trong DB:** `USER` · `TEACHER` · `MANAGER` · `CONTENT_ADMIN` ·
  `FINANCE_ADMIN` · `SUPER_ADMIN`.
  Kiểm role từ JWT; trái quyền → **403** (không trả 401/404 để giấu endpoint)
- **Một người có nhiều role được** — bảng nối `user_roles` đã thiết kế cho việc này
- **Cộng 2 actor KHÔNG phải role trong DB:** `GUEST` và `SYSTEM` — xem §1.1

| Role | Quyền | Phạm vi |
|---|---|---|
| `USER` | Học, thi, chơi game, đăng bài (chờ duyệt) | người dùng cuối |
| `TEACHER` | Duyệt câu hỏi AI sinh, sửa nội dung học, **chấm bài thuê** | Học tập |
| `MANAGER` | Duyệt bài đăng, xử lý báo cáo vi phạm | Community |
| `CONTENT_ADMIN` | Quản lý đề thi, kho câu hỏi, nhập dữ liệu, cuộc thi | nội dung |
| `FINANCE_ADMIN` | 🔴 Sinh lô mã thẻ, xem sổ cái, xử lý tranh chấp | **tiền thật** |
| `SUPER_ADMIN` | Quản lý người dùng, gán role, cấu hình hệ thống | quản trị |

> 🔴 **Vì sao `ADMIN` tách thành ba.** Một `ADMIN` làm được cả ba việc là vi phạm nguyên tắc
> **đặc quyền tối thiểu**. `FINANCE_ADMIN` chạm tiền thật nên phải là role hẹp nhất —
> `NFR-S08` yêu cầu ghi audit cả thao tác **đọc** sổ cái, không phân biệt được nếu chỉ có
> một `ADMIN`. Và `CONTENT_ADMIN` duyệt nội dung thì không cần quyền sinh mã thẻ.
>
> Trong 119 UC **không UC nào** dùng `ADMIN` đơn thuần — mọi UC quản trị đều chỉ rõ một
> trong ba role đã tách.

### 1.1 · Hai actor không phải role

| Actor | Số UC | Là gì | Kiểm quyền bằng |
|---|---|---|---|
| `GUEST` | 16 | Khách chưa đăng nhập, **không có dòng** trong `users` | **Không có JWT** — không phải `role_code` |
| `SYSTEM` | 14 | Job định kỳ `@Scheduled`: hạ gói hết hạn, nhắc học, hoàn lượt quá hạn | Chạy bằng quyền hệ thống, **không qua `JwtFilter`** |

**Tổng: 8 actor = 6 role trong DB + 2 actor không phải role.**

> ⚠️ Đừng thêm `GUEST` hay `SYSTEM` vào bảng `user_roles`. `GUEST` không có tài khoản;
> `SYSTEM` không phải người.

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

- **Đơn vị đo mastery:** từng điểm kiến thức — từ vựng · ngữ pháp · chữ · kỹ năng.
  Không đo theo bài học
- **Thuật toán:** FSRS. Đúng → mastery tăng, hạn ôn giãn ra; sai → giảm, hạn ôn gần lại
- **Cập nhật khi:** làm bài thi · luyện tập · học từ theo chủ đề · **chơi game (tức thì)**
- **Chơi game xong mastery đổi NGAY**, không chờ. `MasteryUpdater.applyGameResult()` chạy
  **cùng transaction** với lúc lưu điểm
- **Index bắt buộc:** `(user_id, due_at)` và `(user_id, mastery)`
- **Ngưỡng qua cổng chủ đề: 90%** — đạt thì mở chủ đề tiếp theo
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
- Chỉ `TEACHER` hoặc `CONTENT_ADMIN` được duyệt. `FINANCE_ADMIN` và `SUPER_ADMIN` **không**
  duyệt nội dung — họ không có chuyên môn kiến thức, và đó là lý do `ADMIN` bị tách
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
| **Mastery** | Mức thành thạo của một người với **một điểm kiến thức** (không phải với bài học hay chủ đề). Tính bằng FSRS |
| **Knowledge point** | Đơn vị kiến thức nhỏ nhất: một từ vựng, một điểm ngữ pháp, một chữ Hán, một kỹ năng. Bảng `knowledge_points` |
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

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

# AGENTS.md — CNHSK

> Đọc file này TRƯỚC mỗi phiên làm việc. Đây là bản rút gọn.
>
> **Luật đầy đủ:** `.specify/memory/constitution.md`
> **Chi tiết áp dụng:** `.specify/memory/constraints/`
> — `global.md` (công nghệ, thư viện được/cấm) · `business.md` (nghiệp vụ + **Domain
> Glossary**) · `safety.md` (**đọc trước khi làm bất cứ việc không hoàn tác được**)

## 1 · Bạn là ai

Kỹ sư senior trong nhóm 6 người làm đồ án tốt nghiệp IT, FPT University.

**Triết lý:** đơn giản hơn là khéo léo · tường minh hơn ngầm định.
**Thứ tự ưu tiên:** đúng > dễ đọc > hiệu năng > ngắn gọn.
**Luôn tự hỏi:** "có cách đơn giản hơn không?"

Không chắc về kiến trúc → **hỏi**, đừng đoán.
Thấy vi phạm ràng buộc → **báo**, đừng lách.

## 2 · Dự án là gì

Nền tảng học tiếng Trung cho người Việt. 33 tính năng (32 sau khi cắt 5.4), 11–12 tuần.

**Kiến trúc: Modular Monolith** — một ứng dụng Spring Boot `cnhsk-api` cổng 8080,
bốn module `auth` · `learning` · `community` · `shared`,
một database `cnhsk_db` ba schema, một Redis.

Ba client dùng chung backend:

| Client | Địa chỉ | Xác thực |
|---|---|---|
| Web chính | `cnhsk.com` | Cookie |
| Trang game | `game.cnhsk.com` | Cookie chung |
| Mobile | React Native | Header `Authorization` |

## 3 · Stack — KHÔNG được lệch

| Phần | Phiên bản |
|---|---|
| Java | **17** |
| Spring Boot | **3.5.14** |
| Build | Maven Wrapper (`./mvnw`) |
| Database | PostgreSQL |
| Migration | Flyway |
| JWT | jjwt 0.12.6 |
| React | **18.3** |
| Vite | **5** |
| TypeScript | **5.7** |
| CSS | Tailwind 3.4 |
| Router | react-router-dom 6.28 |

> Đây là phiên bản nhóm **đã dùng thật** ở dự án trước, không phải bản mới nhất.
> Đổi bất kỳ mục nào cần RFC và cả nhóm duyệt.

**Thư viện FE đã duyệt:** `tailwindcss` · `react-router-dom` · `lucide-react` · `clsx` · `tailwind-merge`.
Thêm thư viện khác **phải hỏi trước**.

## 4 · Phạm vi được phép

**Được đọc và ghi:** `src/` · `docs/` · `specs/` · `cnhsk-web/src/features/<feature của spec đang làm>/`
> Frontend **đã có** tại `cnhsk-web/` (React 18 + Vite + TS + Tailwind) — khung giao diện 19/20 màn web, gọi đúng
> endpoint theo OpenAPI của spec. Không sửa `cnhsk-web/src/shared/`, `src/features/practice/`, `src/app/` khi chưa
> báo người giữ phần nền. Xem giao diện không cần backend: `npm run dev` rồi mở `/__preview`.
>
> **Inventory UI FINAL: 79 màn, SCR-001–SCR-079.** `docs/generated/cnhsk-screen-list.docx` và
> `docs/generated/cnhsk-screen-flow.png` là artifact đã duyệt; không đổi ID, tên hoặc cấu trúc màn.
> Chi tiết field/action/role/readiness nằm trong `docs/reference/screen-fields.md`.
> **Mockup lịch sử nằm ngoài repo:** `C:\CLHSK\cnhsk-mockup\`, HTML tĩnh từ mô hình UI trước đây.
> Chỉ dùng tham khảo sau khi đối chiếu inventory 79 màn; không phải code React dùng được.
**Được chạy:** `./mvnw test` · `./mvnw compile` · lint · `git add` · `git commit`

**PHẢI hỏi người trước khi:**

- Xoá bất kỳ file nào
- Sửa `.specify/memory/constitution.md`
- Push lên `main`
- Thêm dependency mới
- Đổi schema database

## 5 · Cấm tuyệt đối

Vi phạm những điều này là **chặn merge**, không có ngoại lệ:

- **Không** lưu mật khẩu plaintext — bcrypt cost ≥12 hoặc argon2id
- **Không** để secret, API key, mật khẩu trong code, config hay log — dùng biến môi trường
- **Không** dùng `*` cho CORS ở bất kỳ môi trường nào
- **Không** nối chuỗi SQL với input người dùng
- **Không** commit dữ liệu đề thi HSK của giảng viên (bản quyền)
- **Không** lộ stack trace ra client — format lỗi `{error_code, message, request_id}`
- **Không** đặt `ddl-auto: update` — phải là `validate`, Flyway quản schema
- **Không** sửa file migration đã chạy — viết file version mới
- **Không** dùng `FLOAT` cho tiền hay điểm
- **Không** dùng kiểu `ENUM` của PostgreSQL — dùng `VARCHAR` + `CHECK`
- **Không** tin điểm số client gửi lên — server luôn kiểm lại
- **Không** nhắc đến AI trong commit message

## 6 · Quy ước code

| Loại | Quy ước |
|---|---|
| Bảng DB | `snake_case`, số nhiều |
| Package Java | chữ thường, gốc `com.cnhsk` |
| Component React | `PascalCase` |
| Route API | `kebab-case` |
| Khoá chính | `BIGSERIAL` |
| Thời gian | `TIMESTAMPTZ`, server UTC |
| Tiền | `NUMERIC(12,2)` |
| Điểm | `BIGINT` |
| Tác vụ định kỳ | bắt buộc ghi `zone = "Asia/Ho_Chi_Minh"` |

**Ranh giới module:** module gọi nhau CHỈ qua lớp `api` bằng lời gọi hàm.
Cấm module này đọc thẳng bảng của module kia. `ModuleBoundaryTest` kiểm điều này mỗi lần chạy test.

## 7 · API First

**Viết hoặc cập nhật OpenAPI TRƯỚC khi code endpoint.** Ít nhất một thành viên khác duyệt
contract rồi mới được implement.

Lý do: frontend và backend do người khác nhau làm, chạy song song. Contract là điểm hẹn
duy nhất giữa hai bên.

## 8 · Definition of Done

Một đầu việc chỉ xong khi đủ **cả bốn**:

1. Toàn bộ test pass
2. Lint sạch
3. OpenAPI đã cập nhật (nếu đổi endpoint)
4. Không còn `TODO` nào trong code

**Không tính là xong nếu:** còn `TODO`, test bị skip, hoặc chỉ "chạy được trên máy tôi".

## 9 · Cách làm việc

**Trước mỗi task mới:** viết shadow plan — sẽ đọc file nào, sửa file nào, chạy lệnh gì,
kết quả mong đợi. Dừng chờ duyệt rồi mới làm.

**Trước khi nộp:** tự kiểm tuân thủ constitution, in báo cáo self-check kể cả khi không
có vi phạm.

**Gặp edge case không có trong spec:** báo ngay, đừng tự giả định.

**Phân công AI:** Claude Code cho phase lớn và backend · Codex cho frontend và fix nhỏ.

## 10 · Tài liệu nguồn

### Thứ tự đọc

| # | File | Nội dung |
|---|---|---|
| 1 | `.specify/memory/constitution.md` | **Luật.** 42 mã rule, 4 lớp. Đứng trên mọi tài liệu khác |
| 2 | `docs/reference/CONTEXT.md` | Pha 0: Problem Statement · Business Goal · Domain Knowledge · NFR |
| 3 | `specs/NNN-*/spec.md` | Pha 1: đặc tả feature đang làm |
| 4 | `specs/NNN-*/plan.md` + `tasks.md` | Pha 2–3: cách làm và thứ tự |

### Tài liệu tra cứu — `docs/reference/`

**Mỗi tài liệu là một bản final — không có số version trong tên file.**

| File | Nội dung |
|---|---|
| `database.md` | **4 schema · 31 bảng.** Kiêm RFC sửa `AC-03`/`AC-04` (§18) |
| `feature-tree.md` | **33 tính năng** (32 phải làm — 5.4 Gia sư đã cắt) |
| `kien-truc.md` | 3 client, cookie, CORS, chống gian lận. ⚠️ Mục schema/số bảng đã lỗi thời — xem cảnh báo đầu file |
| `design.md` | Design token, component inventory, **79 màn FINAL**, IA/role/public-private và canonical Screen Flow §5.4 |
| `screen-fields.md` | Đặc tả **SCR-001–SCR-079**, 23 mục/màn: field, validation, action, state, permission, Open Issues và Mockup Readiness; RP3 §3 |
| `use-cases-01..08.md` | **119 UC · 714 business rule** |
| `wbs-estimate.md` | **66 đầu việc**, ước lượng PERT |
| `CONTEXT.md` | Pha 0 — đã nêu ở bảng thứ tự đọc trên |

> **Bốn file đã xoá** ở lần tái cấu trúc 2026-10-05: hai file "quyết định đã chốt"/"require v2",
> file catalog use case, và các bản thiết kế DB cũ. Nội dung còn giá trị đã chuyển vào Hiến
> pháp và `CONTEXT.md`.

## 11 · Trạng thái SQL — đọc trước khi viết entity

**Migration ở `src/main/resources/db/migration/`:** `V1__core_schema.sql` (4 schema · 26 bảng MVP) và
`V2__community.sql` (5 bảng `community`). Đây là **nguồn sự thật** của schema (`AC-05`) — entity viết theo SQL,
không theo trí nhớ. Bảng phân số version cho từng spec: `database.md` §11.

**Mỗi entity khai schema tường minh:** `@Table(name = "users", schema = "auth")`. Thiếu là
`ddl-auto: validate` báo lỗi lúc khởi động.

### Quy trình đổi schema (viết entity trước, Flyway vẫn là nguồn chính)

1. Sửa/thêm `@Entity` (schema, độ dài cột, kiểu dữ liệu).
2. Nhờ Hibernate **xuất câu SQL ra file** (không chạy lên DB) để làm bản nháp.
3. Viết migration mới `V{n}__mo-ta.sql` (số `n` theo `database.md` §11 hoặc xin leader), tự thêm phần
   Hibernate không sinh được: `CHECK` cho enum (`AC-06`), partial index, unique nhiều cột, FK, seed.
4. Chạy test với `ddl-auto: validate` — entity và migration lệch là đỏ, không merge.
5. **Cùng PR:** migration + entity + cập nhật `database.md` (bảng, DDL) + spec nếu hợp đồng API đổi.

**Cấm:** sửa file migration đã chạy · đặt `ddl-auto: update` · bật Flyway `outOfOrder`.

### DB dùng chung trên Neon

| Nhánh Neon | Dùng cho |
|---|---|
| `main` | DB chung — chỉ chạy migration đã merge vào `main` của git |
| nhánh riêng mỗi người | Tách từ `main`; thử migration ở đây, hỏng thì xoá tạo lại |

Biến môi trường (để trong `.env` ở gốc repo — đã `.gitignore`, **không bao giờ commit**, không dán vào chat/PR):

```
DB_URL=jdbc:postgresql://<host-khong-co-pooler>/cnhsk_db?sslmode=require
DB_USER=svc_app                     # app: chi doc/ghi du lieu
DB_PASSWORD=...
DB_MIGRATION_USER=neondb_owner      # Flyway: tao/sua bang
DB_MIGRATION_PASSWORD=...
```

Flyway cần kết nối **trực tiếp** (host không có `-pooler`). Test tự động vẫn dùng PostgreSQL trong
Testcontainers, không chạy test trên Neon.

## 12 · Chưa chốt — đừng tự quyết

- **`TODO(REDIS_PLACEMENT)`** — giảng viên nói đặt Redis giữa client và service, kiến trúc
  hiện đặt sau ứng dụng. Chờ hỏi lại giảng viên
- **`TODO(RATIFICATION_DATE)`** — chờ 6 thành viên xác nhận ngày phê chuẩn Hiến pháp
- **`BUS-16`** — trọng số mastery từ luyện viết. `hanzi-writer` chấm nét ở **client**, mâu
  thuẫn `BUS-09` (chấm ở server). Chặn feature 1.1
- **JWT HS256 hay RS256** — chặn FR-016, FR-061 của spec 001
- **Thời gian sống access/refresh token** — đề xuất 15 phút / 30 ngày, chưa chốt

### Đã chốt — đừng hỏi lại

| Chủ đề | Chốt |
|---|---|
| Thanh toán | **Tiền thật, thu ngoài hệ thống.** Không tích hợp cổng thanh toán. Xem Hiến pháp §Mô hình kinh doanh |
| Coverage | **80% toàn bộ** (`ES-05`) |
| Trạng thái tài khoản | Đã có `locked_until` `suspended_at` `banned_at` trong `V1__` |
| Chống IDOR | Thành `BUS-01` trong Hiến pháp — lặp 68 lần trong đặc tả |
| Lượt free | **10 lượt mỗi tính năng mỗi tháng**, reset 00:00 ngày 1 giờ Việt Nam |
| Actor | **7 actor = 6 role trong DB + `GUEST`.** DB: `LEARNER` `TEACHER` `MODERATOR` `CONTENT_EDITOR` `ACCOUNTANT` `ADMIN`. Không phải role: `GUEST` (chưa đăng nhập). Job định kỳ **không** phải actor. **Đừng** thêm `GUEST` vào `user_roles`. Chỉ `TEACHER` duyệt và sửa câu hỏi. Xem Hiến pháp §Bảy actor |

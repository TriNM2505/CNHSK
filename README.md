# CNHSK

**Chinese Learning and HSK Preparation Support System Based on Personalized Learning Pathways for Hoc Ba Company**

Nền tảng học tiếng Trung và luyện thi HSK cho người Việt, có lộ trình cá nhân hoá.
Đồ án tốt nghiệp — FPT University · 6 người · 11–12 tuần.

---

## Thứ tự đọc tài liệu

Đọc theo đúng thứ tự này. Mỗi file sau dựa trên file trước.

| # | File | Nội dung |
|---|---|---|
| 1 | [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | **Luật dự án.** 42 mã rule, 4 lớp. Đứng trên mọi tài liệu khác |
| 2 | [`docs/reference/CONTEXT.md`](docs/reference/CONTEXT.md) | Pha 0: Problem Statement · Business Goal · Domain Knowledge · NFR |
| 3 | [`docs/reference/feature-tree.md`](docs/reference/feature-tree.md) | 33 tính năng (32 phải làm — 5.4 Gia sư đã cắt) |
| 4 | [`docs/reference/kien-truc.md`](docs/reference/kien-truc.md) | Ba client, một backend, cookie dùng chung |
| 5 | [`docs/reference/database.md`](docs/reference/database.md) | 4 schema · 31 bảng · 17 FK cross-schema |
| 6 | [`specs/001-auth-rbac/`](specs/001-auth-rbac/) | Feature đang làm: spec · plan · tasks · checklist |

**Agent AI đọc:** [`AGENTS.md`](AGENTS.md) trước khi làm bất cứ việc gì.

---

## Tài liệu tra cứu

`docs/reference/`

| File | Nội dung |
|---|---|
`use-cases-01..08.md` | **119 use case · 714 business rule**, 8 nhóm |
[`design.md`](docs/reference/design.md) | Design token · component inventory · cấu trúc UI FINAL **79 màn** · phân nhóm/điều hướng theo flow đã duyệt |
[`screen-fields.md`](docs/reference/screen-fields.md) | Đặc tả UI chi tiết **SCR-001–SCR-079**: field, action, validation, role và mockup readiness (RP3 §3) |
`wbs-estimate.md` | 66 đầu việc, ước lượng PERT 3 điểm |

> **Inventory UI hiện hành: 79 màn, FINAL.** Nguồn chuẩn tắc: Screen List tại `docs/generated/cnhsk-screen-list.docx` và Screen Flow tại `docs/generated/cnhsk-screen-flow.png`; giữ nguyên artifact đã duyệt. Các sơ đồ ở `docs/reference/screen-flow/` là lịch sử, không dùng làm cấu trúc UI hiện hành.
>
> **Tình trạng nguồn ngày 2026-10-09:** hai artifact đã được đọc khi bắt đầu đồng bộ nhưng hiện không còn tại đường dẫn trên; chưa xác nhận vị trí mới. Đặc tả giữ nguyên inventory đã đọc và danh sách 79 màn được chủ dự án xác nhận. Cần đối chiếu lại file nguồn khi có đường dẫn; không tái tạo hoặc thay thế artifact.
>
> Mỗi tài liệu là **một bản final** — không có số version trong tên file.

---

## Kiến trúc

**Modular Monolith** — một ứng dụng Spring Boot `cnhsk-api` cổng 8080.

```
┌─ cnhsk.com ──────┐
├─ game.cnhsk.com ─┼──→ cnhsk-api (8080) ──→ PostgreSQL 18 (4 schema)
└─ React Native ───┘                     └──→ Redis
```

| Module | Schema | Bảng |
|---|---|---|
`auth` | `auth` | 3 |
`learning` | `learning` | 22 |
`community` | `community` | 5 |
`shared` | `shared` | 1 |

Ranh giới module do **ArchUnit** canh (`ModuleBoundaryTest`), không dựa vào người review.

---

## Stack

| Phần | Phiên bản |
|---|---|
Java | 17 |
Spring Boot | 3.5.14 |
PostgreSQL | 18 |
Flyway | nguồn duy nhất của schema · `ddl-auto: validate` |
React · Vite · TypeScript | 18.3 · 5 · 5.7 |
jjwt | 0.12.6 |
ArchUnit | 1.5.0 |

Đổi bất kỳ mục nào cần RFC và cả nhóm duyệt (`ES-01`).

---

## Chạy dự án

### Hạ tầng dev — T-00, spec 001

Chuẩn bị Docker Desktop đang chạy Linux containers và Docker Compose từ 2.23.1
(hỗ trợ `configs.content`). Backend dùng Java 17; frontend cần Node.js 20.19+ và npm.

Chạy **5 lệnh** sau trong PowerShell tại thư mục gốc repo. Nếu đã có `.env`, giữ
nguyên file của bạn và bỏ qua lệnh sao chép.

```powershell
# 1. Tạo cấu hình local từ template không chứa secret.
Copy-Item .env.example .env

# 2. Điền POSTGRES_PASSWORD, DB_PASSWORD và JWT_SECRET rồi lưu và đóng editor.
notepad .env

# 3. Kiểm tra cấu hình; --quiet tránh in mật khẩu đã nội suy.
docker compose --env-file .env config --quiet

# 4. Khởi động ba dịch vụ local và đợi PostgreSQL/Redis sẵn sàng.
docker compose --env-file .env up -d --wait --wait-timeout 120

# 5. Kiểm tra trạng thái và các cổng đã mở.
docker compose --env-file .env ps
```

`POSTGRES_PASSWORD` và `DB_PASSWORD` là hai mật khẩu local khác nhau, tự tạo bằng
trình quản lý mật khẩu. Với hướng dẫn nạp biến bên dưới, dùng chuỗi hex ngẫu nhiên
cho cả ba secret này để tránh ký tự có ý nghĩa đặc biệt trong `.env`.
`JWT_SECRET` phải sinh ngẫu nhiên từ ít nhất 32 byte (64 ký tự hex).
Không dùng giá trị mẫu hoặc chia sẻ các giá trị này trong git, ảnh chụp hay log.

| Dịch vụ | Địa chỉ local | Ghi chú |
|---|---|---|
| PostgreSQL 18 | `localhost:5432/cnhsk_db` | App dùng `DB_USER` (mặc định `svc_app`); owner là `postgres` |
| Redis 7 | `localhost:6379` | Bộ đếm và mốc vô hiệu phiên của spec 001 |
| Mailpit SMTP | `localhost:1025` | Không cần tài khoản, mật khẩu hoặc TLS |
| Mailpit UI | `http://localhost:8025` | Xem email dev đã được SMTP nhận |

Các cổng Docker chỉ bind `127.0.0.1`. PostgreSQL giữ dữ liệu trong named volume;
Redis và email Mailpit là dữ liệu dev tạm thời. Dùng `docker compose stop` để dừng
dịch vụ và giữ dữ liệu PostgreSQL. Không xoá volume để xử lý lỗi khởi động.

Compose chỉ tạo database rỗng và role ứng dụng không có quyền superuser, tạo DB
hay tạo role. Migration, schema và quyền truy cập bảng do T-01 xử lý theo
`docs/reference/database.md`; Flyway phải dùng owner riêng. Script tạo role chỉ
chạy ở lần khởi tạo volume đầu tiên. Đổi mật khẩu trong `.env` không tự đổi mật
khẩu đã lưu trong PostgreSQL.

Vị trí volume của PostgreSQL 18 là `/var/lib/postgresql` theo
[tài liệu image chính thức](https://hub.docker.com/_/postgres).

### Chạy backend sau khi hoàn thành các task nền tảng

Docker Compose đọc `.env` để cấu hình container; Maven/Spring hiện không tự nạp
file này. Trong cùng cửa sổ PowerShell sẽ chạy backend, nạp từng biến vào process:

```powershell
Get-Content .env | ForEach-Object {
    $entry = $_.Trim()
    if ($entry -and -not $entry.StartsWith('#')) {
        $key, $value = $entry -split '=', 2
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
```

Định dạng `.env.example` dùng `KEY=VALUE` không có dấu nháy hoặc comment cùng dòng;
giữ định dạng này khi điền giá trị để Compose và đoạn nạp trên đọc giống nhau.
Các biến bắt buộc/tùy chọn của spec nằm đầy đủ trong `.env.example` (§0.3).

T-00 chỉ xác nhận hạ tầng dev. Ở trạng thái repo hiện tại, migration còn nằm ngoài
classpath (T-01), cấu hình JWT/cookie/SMTP và endpoint auth còn thuộc các task
sau; việc ba container chạy không chứng minh backend/auth đã hoạt động.
Chỉ chạy lệnh dưới trên database local sau khi các task nền tảng tương ứng hoàn tất:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Test backend hiện có dùng H2 qua profile test:

```powershell
.\mvnw.cmd test
```

### Frontend hiện có

Frontend React 18 + Vite + TypeScript + Tailwind đã có tại `cnhsk-web/`:

```powershell
Set-Location cnhsk-web
npm ci
npm run dev
```

Mở `http://localhost:5173`. T-00 dựng các dịch vụ phụ trợ; nối API frontend với
backend và proxy Vite thuộc các task tích hợp sau.

### Đưa template vào commit

`.gitignore` hiện bỏ qua cả `.env.example` theo mẫu `.env.*`. Khi tạo commit T-00,
kiểm tra file template vẫn không chứa secret rồi stage **đúng ba file**:

```powershell
git add docker-compose.yml README.md
git add -f .env.example
git diff --cached --stat
```

Không force-add `.env` hoặc file chứa credentials thật.

---

## Trạng thái

| Pha SDD | Trạng thái |
|---|---|
0 · Context Discovery | ✅ `CONTEXT.md` |
1 · Specification | 🟡 **1/32** feature (`001-auth-rbac`) |
2 · Planning | 🟡 1/32 |
3 · Tasks | 🟡 1/32 |
4 · Implement | 🔴 chưa — xem `AGENTS.md` §11 |
5 · Validation | 🔴 chưa có CI |

**`db/migration/` hiện chỉ có `V1` và `V2`.** Những thứ đã thiết kế nhưng chưa vào SQL
(bảng `plans` · `pron_stages` · `videos`, cột gói, cột phễu, cột `seq`, prefix 4 schema)
liệt ở `AGENTS.md` §11.

---

## Quy trình

Dự án theo **Spec-Driven Development** kết hợp **Spec Kit**.

```
Spec → Plan → Tasks → Implement → Validate
```

Một feature làm **trọn vòng** rồi mới sang feature sau — không viết hết spec rồi mới code.

**Quy tắc:** mô tả `.md` xong → chủ dự án duyệt → mới gen SQL hoặc code.
Đừng tự viết migration.

---

## Điểm còn mở

| Mục | Chặn |
|---|---|
`TODO(RATIFICATION_DATE)` | chờ 6 thành viên xác nhận ngày phê chuẩn Hiến pháp |
`BUS-16` | trọng số mastery từ luyện viết (`hanzi-writer` chấm ở client) — chặn feature 1.1 |
JWT HS256 hay RS256 | chặn `FR-016`, `FR-061` của spec 001 |
Số liệu thị trường | `CONTEXT.md` §1.2 — cần khảo sát 20–30 người học HSK |

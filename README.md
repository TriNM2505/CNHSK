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
`design.md` | Design token · component inventory · 32 màn · **screen flow 7 sơ đồ** |
`screen-fields.md` | **Field từng màn** — kiểu, validate, API, mã lỗi (RP3 §3) |
`wbs-estimate.md` | 66 đầu việc, ước lượng PERT 3 điểm |

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

```bash
# Build + test
./mvnw clean test

# Chạy
./mvnw spring-boot:run
```

Cần PostgreSQL 18 ở `localhost:5432`, database `cnhsk_db`, tài khoản `svc_app`.
Biến môi trường: `DB_URL` · `DB_USER` · `DB_PASSWORD` · `REDIS_HOST`.

> ⚠️ **Frontend chưa tồn tại.** Bản nháp đã xoá. Khi dựng lại, tên thư mục bắt buộc là
> `cnhsk-web` theo `ES-04`.

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

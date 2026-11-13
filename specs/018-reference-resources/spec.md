# Feature Specification: Kênh YouTube, podcast và sách tham khảo

**Feature:** `018-reference-resources`
**Feature tree:** 7.1 Kênh & Podcast · 7.2 Sách bản quyền · **Module:** `learning` (package `learning.reference`)
**Use case:** UC-117, UC-118, UC-119 · **Actor:** `GUEST`, `LEARNER`, `CONTENT_EDITOR`
**Màn hình:** SCR-053 YouTube & Podcasts, SCR-054 Book Catalogue, SCR-055 Book Detail, SCR-072 Reference Management
**Scope:** đợt hoàn thiện · **Priority:** P3

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (rà và duyệt 2026-11-13; quyết định chung H1–H14 plan `261113-1629`) |
| **Locked on** | 2026-11-13 |
| **Phụ thuộc** | 001 (role, CSRF), `shared.audit` — đã merge. Không phụ thuộc spec mới nào |
| **Quyết định** | Brainstorm 2026-11-13: 1 bảng `reference_items` · domain cố định trong `application.yaml` · báo link chết chỉ ghi `audit_logs` |
| **Plan** | `plans/261113-1629-viet-spec-con-lai-hoan-thien/phase-08-spec-018-tai-lieu-tham-khao.md` |

> **Codex đọc:** `AGENTS.md` → file này → `contracts/openapi-references.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Danh mục kênh YouTube, podcast (SCR-053) và sách (SCR-054, SCR-055) đã công bố, khách xem được | Nhúng/phát video, podcast; lưu ảnh bìa, PDF, nội dung sách (BR-117-4, BR-118-4, BR-118-5) |
| Lọc theo loại, cấp HSK, tag | Tìm kiếm toàn văn, gợi ý theo người học |
| Mở link ngoài an toàn; nhãn link tiếp thị liên kết (BR-118-7) | Theo dõi click, đo affiliate |
| `LEARNER` báo link chết/sai → ghi `audit_logs` (BR-117-6) | Khách báo lỗi (chống spam) · luồng xử lý báo lỗi tự động |
| `CONTENT_EDITOR` thêm, sửa, công bố, ẩn, hiện lại; xem số lần bị báo (SCR-072) | Xoá cứng (BR-119-7) · `ADMIN` quản trị mục này (BR-119-2) |
| Kiểm URL `https` + domain cho phép theo loại (BR-119-4, BR-119-5) | Sửa danh sách domain lúc chạy — đổi bằng deploy (Q2) |

### 0.1 · Bảng mới duy nhất — `learning.reference_items` (H3 RFC; migration số V xin leader, H4)

```sql
CREATE TABLE learning.reference_items (
  id             BIGSERIAL    PRIMARY KEY,
  kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('YOUTUBE_CHANNEL','PODCAST','BOOK')),
  title          VARCHAR(200) NOT NULL,
  description    VARCHAR(2000),                       -- chữ thuần, FE escape (BR-119-6)
  url            TEXT         NOT NULL CHECK (url LIKE 'https://%'),
  thumbnail_url  TEXT         CHECK (thumbnail_url IS NULL OR thumbnail_url LIKE 'https://%'),
  hsk_min        SMALLINT     CHECK (hsk_min BETWEEN 1 AND 9),
  hsk_max        SMALLINT     CHECK (hsk_max BETWEEN 1 AND 9),
  tags           VARCHAR(30)[] NOT NULL DEFAULT '{}',
  -- chỉ BOOK
  author         VARCHAR(200),
  publisher      VARCHAR(200),
  published_year SMALLINT     CHECK (published_year BETWEEN 1900 AND 2100),
  is_affiliate   BOOLEAN      NOT NULL DEFAULT false,
  status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
  sort_order     INTEGER      NOT NULL DEFAULT 0,
  created_by     BIGINT       NOT NULL REFERENCES auth.users(id),
  published_at   TIMESTAMPTZ,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_ref_hsk      CHECK (hsk_min IS NULL OR hsk_max IS NULL OR hsk_min <= hsk_max),
  CONSTRAINT ck_ref_book     CHECK (kind = 'BOOK' OR (author IS NULL AND publisher IS NULL
                                    AND published_year IS NULL AND is_affiliate = false)),
  CONSTRAINT ck_ref_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX ix_ref_public ON learning.reference_items(kind, sort_order, id) WHERE status = 'PUBLISHED';
CREATE INDEX ix_ref_admin  ON learning.reference_items(kind, status, updated_at DESC);
GRANT SELECT, INSERT, UPDATE ON learning.reference_items TO svc_app;   -- không DELETE (BR-119-7)
GRANT USAGE ON SEQUENCE learning.reference_items_id_seq TO svc_app;
```

Kèm sửa `database.md` §3, §11 (leader, theo RFC): AC-04 cập nhật.

### 0.2 · Danh sách domain cho phép (Q2 — cố định trong cấu hình)

```yaml
cnhsk:
  reference:
    allowed-domains:
      YOUTUBE_CHANNEL: [youtube.com, youtu.be]
      PODCAST: [open.spotify.com, podcasts.apple.com, soundcloud.com, ximalaya.com, podcasts.google.com]
      BOOK: [tiki.vn, shopee.vn, fahasa.com, amazon.com, books.google.com, nxbgd.vn]
    thumbnail-domains: [i.ytimg.com, yt3.ggpht.com, salt.tikicdn.com, m.media-amazon.com]
```

So khớp **host** sau khi parse URL (chữ thường, bỏ `www.`): host bằng domain hoặc kết thúc bằng `.` + domain. Leader rà danh sách khi duyệt (Q5).

### 0.3 · File dùng chung bị sửa (báo leader trước khi mở PR — H8)

`ErrorCode.java`, `SecurityConfig.java` (permitAll `/api/public/references/**`; `LEARNER` cho báo lỗi; `CONTENT_EDITOR` cho `/api/admin/references/**`), `application.yaml`, `shared.audit.AuditService` (thêm hàm đếm theo đích — §3 FR-016), `routeAccess.ts`, `screenRegistry.ts`, `navigation.ts`.

---

## 1 · Context & Goal

Người học cần nguồn nghe/đọc thêm uy tín ngoài bài học. Dự án **chỉ giới thiệu và dẫn link**, không phân phối nội dung bản quyền — vừa hữu ích vừa an toàn pháp lý. Vì là nội dung công khai cho cả khách, mọi link và mô tả phải được kiểm và escape.

**Thành công khi:** khách xem được danh sách kênh/podcast/sách đã công bố, lọc theo HSK, mở link an toàn; biên tập viên quản lý được và biết link nào bị báo lỗi.

---

## 2 · User Stories

### US-1 · Xem kênh và podcast (P1)
Là khách hoặc người học, tôi chọn YouTube hoặc podcast, lọc theo cấp HSK, mở kênh ở tab mới.

### US-2 · Xem sách (P1)
Là khách hoặc người học, tôi duyệt danh mục sách theo HSK, xem chi tiết (tác giả, NXB, năm, mô tả) và mở link mua/tham khảo.

### US-3 · Báo link lỗi (P3)
Là người học, tôi báo một link chết/sai để biên tập viên sửa.

### US-4 · Quản lý tài nguyên (P1)
Là biên tập viên, tôi thêm, sửa, công bố, ẩn tài nguyên; thấy mục nào bị báo lỗi nhiều.

### Edge cases
- URL `http://`, `javascript:`, `data:`, sai định dạng, domain ngoài danh sách → từ chối khi lưu, không chờ lúc hiển thị (BR-117-7).
- Công bố khi thiếu dữ liệu bắt buộc → giữ nguyên trạng thái, báo lỗi (`PUBLISH_INVALID_RESOURCE`).
- Mục đã ẩn → không xuất hiện công khai; gọi chi tiết → 404.
- Sửa URL của mục đã công bố → kiểm lại domain; vẫn `PUBLISHED`.
- Báo lỗi lần 2 trong ngày cùng mục → 429.
- Mô tả chứa `<script>` → lưu nguyên chữ, FE render dạng text (không `dangerouslySetInnerHTML`).

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001…FR-003)
- **FR-001** `GET /api/public/references/**` không cần đăng nhập (`GUEST`, `LEARNER`, mọi role).
- **FR-002** Báo lỗi yêu cầu `LEARNER` + CSRF (cookie).
- **FR-003** `/api/admin/references/**` chỉ `CONTENT_EDITOR` (BR-119-2); ghi cần CSRF.

### Nhóm 1 — Công khai (FR-004…FR-008) · SCR-053, SCR-054, SCR-055
- **FR-004** `GET /api/public/references?kind=&hsk=&tag=&page=&size=` chỉ trả `status='PUBLISHED'` (lọc trong truy vấn, không ở FE — `UNPUBLISHED_RESOURCE_EXPOSED`). `kind` bắt buộc. `hsk` lọc mục có `hsk_min ≤ hsk ≤ hsk_max` (mục không ghi cấp luôn hiện). Sắp `sort_order, id`. `size ≤ 50`.
- **FR-005** DTO công khai: `id, kind, title, description, url, thumbnailUrl, hskMin, hskMax, tags` + với sách `author, publisher, publishedYear, isAffiliate`. **Không** có `createdBy`, `status`, số báo lỗi (`BOOK_CONTENT_EXPOSED`).
- **FR-006** `GET /api/public/references/{id}` cho SCR-055 (mọi loại đều trả được; FE chỉ dùng cho sách). Không tồn tại hoặc không `PUBLISHED` → `REFERENCE_NOT_FOUND`.
- **FR-007** FE mở link bằng `<a target="_blank" rel="noopener noreferrer">` (BR-117-5); sách `isAffiliate=true` hiện nhãn "Link tiếp thị liên kết" cạnh nút mua.
- **FR-008** Lỗi tải danh mục → trạng thái lỗi có nút thử lại, **không** giả thành danh sách rỗng (`RESOURCE_READ_ERROR`).

### Nhóm 2 — Báo lỗi (FR-009…FR-010)
- **FR-009** `POST /api/references/{id}/report {reason}` (`reason` ∈ `DEAD_LINK`, `WRONG_CONTENT`, `OTHER`; `note` ≤ 300 ký tự tuỳ chọn) — chỉ mục `PUBLISHED`. Ghi `audit_logs` `action='REFERENCE_REPORTED'`, `target_type='REFERENCE'`, `target_id`, `detail={reason, note}`.
- **FR-010** Giới hạn 1 lần/người/mục/ngày (giờ VN) bằng `RateLimiter` (Redis) → vượt `RATE_LIMITED`. Phản hồi 204.

### Nhóm 3 — Quản trị (FR-011…FR-018) · SCR-072
- **FR-011** `GET /api/admin/references?kind=&status=&reported=&page=` trả mọi trạng thái kèm `reportCount` (tổng `REFERENCE_REPORTED` từ khi `updated_at` gần nhất) và `lastReportedAt`; `reported=true` chỉ mục có `reportCount > 0`.
- **FR-012** `POST /api/admin/references` tạo mục `DRAFT` (BR-119-3); `PUT /api/admin/references/{id}` sửa mọi trường trừ `status`. Kiểm theo FR-015.
- **FR-013** `POST …/{id}/publish`: kiểm bắt buộc (`title`, `url`; sách thêm `author`) + kiểm lại URL → `PUBLISHED`, `published_at=now()` (nếu chưa có). Thiếu → `PUBLISH_INVALID_RESOURCE`, giữ trạng thái.
- **FR-014** `POST …/{id}/hide` → `HIDDEN`; `POST …/{id}/unhide` → `DRAFT` (phải công bố lại). Không có endpoint xoá (BR-119-7).
- **FR-015** Kiểm URL khi tạo/sửa/công bố: parse được, scheme `https`, không có thông tin đăng nhập trong URL, host khớp `allowed-domains[kind]` (§0.2); `thumbnailUrl` (tuỳ chọn) khớp `thumbnail-domains`. Lỗi tách mã: `MALFORMED_URL`, `NON_HTTPS_LINK` (gồm `javascript:`, `data:`), `DOMAIN_NOT_ALLOWED`.
- **FR-016** Thêm `AuditService.countByTargets(action, targetType, Set<Long> ids, Map<Long, Instant> since)` (hoặc tương đương) dùng index `ix_audit_target` — trả `Map<Long, ReportStat(count, lastAt)>`.
- **FR-017** Mọi thêm/sửa/công bố/ẩn/hiện ghi audit `REFERENCE_CREATED|UPDATED|PUBLISHED|HIDDEN|UNHIDDEN` kèm trước/sau (BR-119-8).
- **FR-018** Trường chỉ dành cho sách gửi kèm `kind` khác `BOOK` → `VALIDATION_FAILED` (DB `ck_ref_book` chặn lần nữa).

### Nhóm 4 — Giao diện (FR-019…FR-023)
- **FR-019** Route công khai `/references/media` (SCR-053, tab YouTube / Podcast), `/references/books` (SCR-054), `/references/books/:id` (SCR-055); quản trị `/content/references` (SCR-072, layout quản trị, `CONTENT_EDITOR`). Mục menu "Tài liệu tham khảo" ở menu công khai và học viên; "Tài nguyên tham khảo" ở menu biên tập viên.
- **FR-020** SCR-053/054: bộ lọc HSK + tag; lưới thẻ (ảnh nếu có, tên, mô tả rút gọn, cấp HSK, nút "Mở"); người học thấy nút "Báo lỗi" trong menu thẻ.
- **FR-021** SCR-055: thông tin sách, mô tả đầy đủ (text), nút mua/tham khảo + nhãn affiliate.
- **FR-022** SCR-072: bảng lọc loại/trạng thái/"có báo lỗi", cột số lần báo; form tạo/sửa (trường sách chỉ hiện khi `kind=BOOK`); nút Công bố / Ẩn / Hiện lại có xác nhận trong trang.
- **FR-023** Dùng `shared/ui` + token `design.md` (H10); đủ 4 trạng thái; responsive ≤ 640 px một cột, không cuộn ngang; bảng quản trị cuộn ngang trong khung riêng.

### Key Entities
`ReferenceItem` (bảng `reference_items`) · `ReferenceReport` (dòng `audit_logs` `REFERENCE_REPORTED`, không có bảng riêng).

---

## 4 · Data Model
Xem §0.1. Ràng buộc DB giữ đúng luật nghiệp vụ kể cả khi code sai: `https` cho URL, cột sách chỉ cho `BOOK`, `PUBLISHED` phải có `published_at`, không quyền `DELETE`.

## 5 · Error Matrix

| Mã | HTTP | Khi nào |
|---|---|---|
| `REFERENCE_NOT_FOUND` *(mới)* | 404 | Không tồn tại; công khai: không `PUBLISHED` |
| `MALFORMED_URL` *(mới)* | 422 | URL không parse được / có user:pass |
| `NON_HTTPS_LINK` *(mới)* | 422 | Scheme khác `https` (gồm `javascript:`, `data:`) |
| `DOMAIN_NOT_ALLOWED` *(mới)* | 422 | Host ngoài danh sách cho loại đó |
| `PUBLISH_INVALID_RESOURCE` *(mới)* | 422 | Công bố khi thiếu trường bắt buộc |
| `VALIDATION_FAILED` | 422 | `kind`, `hsk`, độ dài, trường sách sai loại, phân trang |
| `RATE_LIMITED` | 429 | Báo lỗi > 1 lần/ngày/mục; khách > 120 request/phút/IP |
| `UNAUTHENTICATED` / `FORBIDDEN` / `CSRF_TOKEN_INVALID` | 401/403 | Có sẵn |

Mã mới thêm ngay trước `INTERNAL_ERROR` theo thứ tự merge (H8).

## 6 · Architectural Constraints
- Module `learning`; không gọi `community`; `auth` chỉ qua `AuthApi` nếu cần tên người tạo (không cần ở bản này).
- `AuditService` thuộc `shared` — thêm hàm đọc không làm `shared` phụ thuộc module nghiệp vụ.
- Không gọi ra mạng để kiểm link (không HEAD request) — chỉ kiểm cú pháp + domain.

## 7 · Non-Functional Requirements
- Danh sách công khai p95 < 150 ms với 500 mục (index `ix_ref_public`).
- `reportCount` tính theo lô một truy vấn cho cả trang (không N+1).
- Không log `note` báo lỗi (có thể chứa thông tin người dùng gõ).

## 8 · Constitution Check
| Luật | Đáp ứng |
|---|---|
| AC-04 số bảng | +1 bảng đã có trong RFC H3 |
| Bản quyền (constitution, `AGENTS.md`) | Chỉ link + mô tả tự viết; dữ liệu mẫu tự soạn |
| Bảo mật nội dung công khai | Kiểm URL khi lưu, escape mô tả, `noopener noreferrer` |
| Không xoá cứng | Không quyền `DELETE`, không endpoint xoá |
| Audit | FR-009, FR-017 |

## 9 · Quyết định và câu cần leader xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | 1 bảng `reference_items` gộp kênh, podcast, sách | ✅ Leader chốt 2026-11-13 |
| Q2 | Domain cho phép cố định trong `application.yaml` | ✅ Leader chốt 2026-11-13 |
| Q3 | Báo link chết chỉ ghi `audit_logs`; SCR-072 đếm từ log | ✅ Leader chốt 2026-11-13 |
| Q4 | Chỉ `LEARNER` báo lỗi (khách không) | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q5 | Danh sách domain ban đầu ở §0.2 | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q6 | `unhide` đưa về `DRAFT` (phải công bố lại) | ✅ Leader duyệt cùng spec 2026-11-13 |

## 10 · Móc nối
- **016 (gác):** nếu làm sau, video học có thể link sang kênh nguồn ở đây — không phụ thuộc.
- **Phụ lục 014:** menu thư viện có mục "Tài liệu tham khảo" trỏ `/references/media`.
- **015:** thêm đoạn `help/` mô tả trang tài liệu tham khảo sau khi merge.
- **Leader:** chạy migration lên Neon ngay sau merge (H4, L3).

## 11 · Rủi ro đã chấp nhận
- Link ngoài có thể chết sau khi công bố → dựa vào báo lỗi + biên tập viên, không kiểm tự động.
- Đổi danh sách domain phải deploy.
- Đếm báo lỗi từ `audit_logs` chậm dần khi log lớn → có index `ix_audit_target`; chấp nhận ở quy mô đồ án.

## 12 · Màn hình
- **SCR-053:** tab YouTube / Podcast; lọc HSK, tag; lưới thẻ; nút Mở, Báo lỗi (người học).
- **SCR-054:** lọc HSK, tag; lưới sách (tên, tác giả, cấp); bấm mở SCR-055.
- **SCR-055:** chi tiết sách; nút mua/tham khảo; nhãn affiliate; quay lại danh mục.
- **SCR-072:** bảng quản trị + bộ lọc; form tạo/sửa; thao tác trạng thái; số lần bị báo.

## 13 · API Contract
`contracts/openapi-references.yaml` — công khai: `GET /api/public/references`, `GET /api/public/references/{id}`; người học: `POST /api/references/{id}/report`; quản trị: `GET/POST /api/admin/references`, `GET/PUT /api/admin/references/{id}`, `POST /api/admin/references/{id}/publish|hide|unhide`.

## 14 · Acceptance Tests

| AT | Kịch bản | Kỳ vọng |
|---|---|---|
| AT-01 | Khách xem `kind=YOUTUBE_CHANNEL` có 3 mục PUBLISHED, 2 DRAFT, 1 HIDDEN | Chỉ 3 mục |
| AT-02 | Lọc `hsk=2` | Mục có khoảng chứa 2 + mục không ghi cấp |
| AT-03 | DTO công khai | Không có `status`, `createdBy`, `reportCount` |
| AT-04 | Chi tiết mục HIDDEN | 404 `REFERENCE_NOT_FOUND` |
| AT-05 | Tạo URL `http://youtube.com/...` | 422 `NON_HTTPS_LINK` |
| AT-06 | Tạo URL `javascript:alert(1)` | 422 `NON_HTTPS_LINK` |
| AT-07 | Tạo YouTube với URL `https://evil.com/youtube.com` | 422 `DOMAIN_NOT_ALLOWED` |
| AT-08 | `https://m.youtube.com/@kenh` | Hợp lệ (tên miền con) |
| AT-09 | Tạo podcast có `author` | 422 `VALIDATION_FAILED` |
| AT-10 | Công bố sách thiếu `author` | 422 `PUBLISH_INVALID_RESOURCE`, vẫn DRAFT |
| AT-11 | Ẩn rồi hiện lại | `HIDDEN` → `DRAFT`; không công khai tới khi công bố |
| AT-12 | Người học báo lỗi 2 lần trong ngày cùng mục | Lần 1: 204 + 1 dòng audit; lần 2: 429 |
| AT-13 | Khách báo lỗi | 401 |
| AT-14 | SCR-072 `reported=true` | Chỉ mục có báo lỗi, `reportCount` đúng, một truy vấn đếm cho cả trang |
| AT-15 | `LEARNER` gọi `/api/admin/references` | 403 |
| AT-16 | Mọi thao tác quản trị | Có dòng audit tương ứng |
| AT-17 | Mô tả có `<script>` | FE hiện nguyên chữ, không chạy script |
| AT-18 | FE ≤ 640 px | Một cột, không cuộn ngang; link có `rel="noopener noreferrer"` |
| AT-19 | Hợp đồng `/v3/api-docs` so `openapi-references.yaml` | Khớp path/method/status hai chiều |

## 15 · Gói việc
Xem `tasks.md` — 8 task, ước tính BE 3 · FE 3 ngày công.

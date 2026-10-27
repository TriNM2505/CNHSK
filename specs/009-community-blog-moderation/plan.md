# Implementation Plan: Blog cộng đồng có kiểm duyệt — Spec #009

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-community.yaml`](contracts/openapi-community.yaml)
> **Migration:** không có — dùng `V2__community.sql` đã chạy trên DB chung
> **Module:** `community` · **Trạng thái:** chưa code

---

## 1 · Architectural Approach

Feature này không khó về thuật toán. Rủi ro nằm ở **chỗ lọc trạng thái** và **chỗ ghi đồng thời**:

| Rủi ro | Cách chặn trong thiết kế |
|---|---|
| Bài chưa duyệt lọt ra endpoint công khai | Controller công khai chỉ gọi `PostQueryService.published…`, service này chỉ gọi `PostRepository.findPublished…` (điều kiện `status = 'APPROVED'` nằm trong câu truy vấn). Không có `findAll` dùng chung |
| Bình luận đã ẩn vẫn hiện | Một hàm `CommentTreeBuilder.build(List<Comment>)` cắt nội dung và bỏ lá không hiển thị — mọi endpoint đọc cây đi qua hàm này |
| Hai `MODERATOR` ghi đè nhau | `UPDATE … WHERE id = ? AND status = 'PENDING'` (`@Modifying` query trả số dòng), 0 dòng → 404/409. Không khoá bi quan |
| Báo cáo song song tạo hai hồ sơ | `INSERT … ON CONFLICT (target_type, target_id) WHERE status = 'PENDING' DO UPDATE` dựa vào `uq_mod_target` có sẵn |
| Thích song song tạo hai dòng | Khoá chính `reactions` + `ON CONFLICT DO NOTHING` |
| XSS | Không render HTML ở đâu cả. Server lưu văn bản thuần; React render text |
| Lộ email | DTO `record` riêng cho mọi response; tên tác giả chỉ qua `AuthApi.displayNames` |

**Ranh giới module:** `community` không biết bảng `auth`. Mọi việc cần người dùng đi qua `AuthApi`:

| Cần | Gọi |
|---|---|
| Người đang gọi + role | `currentUser()` |
| Chặn tài khoản chưa xác thực | `requireVerifiedEmail(userId)` |
| Tên hiển thị theo lô | `displayNames(Set<Long>)` |

FK `posts.author_id → auth.users` có sẵn trong DB là ràng buộc toàn vẹn, không phải lý do để `JOIN`.

**Giới hạn tần suất đếm trong DB, không dùng Redis:** ba giới hạn (5 bài/ngày, 30 bình luận/giờ, 20 báo cáo/ngày) đếm
thẳng từ `posts`, `comments`, `reactions` theo `created_at`. Đúng tuyệt đối, không thêm khoá Redis, không lệch khi Redis lỗi.
Mốc ngày lấy từ bean `Clock` + `ZoneId.of("Asia/Ho_Chi_Minh")` để test được bằng đồng hồ giả.

---

## 2 · Components

```
com.cnhsk.community
├── common/        CommunityAuthors · CommunityLimits · CommunityProperties (cnhsk.community.*) · enum
├── post/          Post · PostRepository · PostService · PostQueryService
│   └── web/       PublicPostController (/api/public/community/posts) · MyPostController (/api/community/posts)
├── comment/       Comment · CommentRepository · CommentService · CommentTreeBuilder
│   └── web/       CommentController (bình luận, trả lời, sửa, xoá) — cây đọc ở PublicPostController
├── reaction/      Reaction · ReactionId · ReactionRepository · LikeService
└── moderation/    ModerationCase · ModerationCaseRepository · ReportService
    │              PostModerationService · ReportCaseService · CommentModerationService
    └── web/       ReportController (/api/community/reports) · ModerationController (/api/community/moderation/**)
```

| Thành phần | Ghi chú |
|---|---|
| `CommunityAuthors` | Nhận tập id, gọi `displayNames` **một lần**, trả `Map<Long, UserRef>`; id thiếu → `displayName = null` |
| `CommunityLimits` | `assertCanCreatePost(userId)`, `assertCanComment(userId)`, `assertCanReport(userId)` ném `ApiException(TOO_MANY_REQUESTS)` kèm `retryAfterSeconds` |
| `CommunityProperties` | `postsPerDay=5`, `commentsPerHour=30`, `reportsPerDay=20`, `commentEditWindow=15m` (§9 Q3 của spec) |
| `CommentTreeBuilder` | Thuần Java, không truy cập DB — test đơn vị cho FR-018 |
| `PostQueryService.countsFor(List<Long>)` | Hai truy vấn `GROUP BY` (bình luận `VISIBLE`, `LIKE`) cho cả trang (FR-014) |
| `ReportService.report` | Một transaction: chèn `REPORT`, upsert hồ sơ bằng native query, nối `reasons` bằng `jsonb ||` |
| `ReportCaseService.decide` | Một transaction: đóng hồ sơ có điều kiện → đổi nội dung → audit |

**Entity:**

| Entity | Bảng | Lưu ý map |
|---|---|---|
| `Post` | `community.posts` | `status`, `category` `@Enumerated(STRING)`; `review_reason TEXT` |
| `Comment` | `community.comments` | `depth SMALLINT` → `short`; không map quan hệ `@ManyToOne` tới `Post` (chỉ `postId`, `parentId`) để tránh tải lười ngoài ý muốn |
| `Reaction` | `community.reactions` | `@EmbeddedId ReactionId(userId, targetType, targetId, kind)` |
| `ModerationCase` | `community.moderation_cases` | `reasons` `@JdbcTypeCode(SqlTypes.JSON)` → `List<ReportEntry>` (chỉ đọc khi hiển thị; ghi bằng native query) |

`posts.updated_at` và `comments.updated_at` không có trigger trong DB → service đặt `updatedAt = now(clock)` mỗi lần ghi.

---

## 3 · Dependencies

### 3.1 · Migration
Không có. Entity sai lệch SQL → `ddl-auto: validate` báo lỗi khi khởi động test; sửa entity, **không** sửa V2.

### 3.2 · Thư viện
Không thêm. Native query + `jsonb ||` chạy bằng Spring Data JPA có sẵn.

### 3.3 · Phụ thuộc spec 001 (phải có trên `main`)

| Cần | Ở đâu |
|---|---|
| `AuthApi` 4 hàm | `auth.api.AuthApi` |
| `ErrorCode`, `ApiException`, `GlobalExceptionHandler` (422 `details`, 429 `retryAfterSeconds`) | `shared.exception` |
| `AuditService.record` | `shared.audit` |
| `SecurityConfig`, CSRF, CORS | `shared.config` |
| `AbstractIntegrationTest`, helper tạo user/role trong test | `src/test/java/com/cnhsk/support` |
| ArchUnit `ModuleBoundaryTest` | test có sẵn — chỉ cần chạy lại |

### 3.4 · Feature phụ thuộc spec này
Chưa có. UC-074 (theo dõi), quiz/xếp hạng sau này đọc bài/bình luận qua lớp `community.api` — **không** tạo lớp đó trước khi có người dùng (YAGNI).

---

## 4 · Risks & Mitigations

| Rủi ro | Giảm thiểu |
|---|---|
| `SecurityConfig` dùng chung với spec khác | Chỉ thêm 3 matcher (spec §6), đặt **trước** `anyRequest()`; test AT-01, AT-02 |
| Thêm mã vào `ErrorCode` cùng lúc spec 002/003 sửa file này | Thêm cuối enum, commit riêng nhỏ; xung đột merge chỉ ở vài dòng |
| `retryAfterSeconds` của 429 phụ thuộc cách spec 001 dựng body | Dùng đúng cơ chế spec 001 đã có cho `TOO_MANY_REQUESTS` |
| Đếm câu SQL trong AT-13, AT-24 dễ vỡ | Đo bằng Hibernate `StatementInspector` gắn trong profile test; so sánh hai kích thước trang thay vì số tuyệt đối khi có thể |
| `routeAccess.ts`/`screenRegistry.ts` là phần nền FE | Báo leader trước khi sửa (`AGENTS.md` §4); chỉ thêm dòng, không đổi dòng cũ |
| Người duyệt nhầm `ADMIN` là có quyền kiểm duyệt | AT-02 khoá hành vi |

---

## 5 · Questions for Human

Xem `spec.md` §9 — Q2, Q3, Q4, Q6 cần nhóm xác nhận khi duyệt spec. Không câu nào chặn đợt 0.

---

## 6 · Constitution Check

| Luật | Trạng thái |
|---|---|
| `AC-01` OpenAPI trước code | ✅ contract có trước; T-02 thêm test hợp đồng |
| `AC-03` ranh giới module | ✅ chỉ `AuthApi`; ArchUnit |
| `AC-05` Flyway | ✅ không đổi schema |
| `AC-06`, `AC-08`, `AC-09` | ✅ spec §6 |
| `HR-05`, `HR-07`, `HR-09` | ✅ spec §6 |
| `BUS-01`, `BUS-02`, `BUS-06`, `BUS-08`, `BUS-10` | ✅ spec §6 |
| `ES-05` integration test mọi endpoint | ✅ 24 operation đều có AT |

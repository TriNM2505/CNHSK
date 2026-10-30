# Feature Specification: Blog cộng đồng có kiểm duyệt

**Feature:** `009-community-blog-moderation`
**Feature tree:** 5.1 (blog cộng đồng) · **Module:** `community` (package `com.cnhsk.community`)
**Use case:** UC-069…UC-073, UC-075…UC-079 · **Actor:** `GUEST` · `LEARNER` · `MODERATOR`
**Màn hình:** SCR-039 · SCR-040 · SCR-041 · SCR-060 · SCR-061 · SCR-062 · SCR-063
**Scope:** V2 · **Priority:** P2. Giao cho thành viên đang rảnh; không rút người khỏi spec MVP (BR-069-1)

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — hợp đồng API đã duyệt, được code |
| **Approved by** | Leader (2026-10-29) |
| **Locked on** | 2026-10-29 — đổi hợp đồng phải sửa OpenAPI + spec trước, leader duyệt lại |
| **Phụ thuộc** | Spec 001 đã merge: đăng nhập, role `LEARNER`/`MODERATOR`, CSRF, định dạng lỗi, `AuditService`, `auth.api.AuthApi` |
| **Quyết định nguồn** | `plans/reports/brainstorm-261027-0940-community-spec-009-parallel-report.md` (ngoài repo, leader giữ) |

> **Codex đọc theo thứ tự:** `AGENTS.md` → spec 001 §0 (nền tảng) → file này → `contracts/openapi-community.yaml` → `plan.md` → `tasks.md`.
> Chỗ spec không nói: **dừng, ghi `questions.md`**, không tự giả định.

---

## 0 · Phạm vi

### 0.1 · Trong / ngoài phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Đăng bài, lưu nháp, sửa bài của mình (UC-069) | UC-074 theo dõi người dùng, feed "đang theo dõi", SCR-042 hồ sơ cộng đồng |
| Danh sách bài công khai + "Bài viết của tôi" (UC-070) | Quiz, bảng xếp hạng (UC-080…UC-084), trợ lý AI (UC-085/086), Game Box (UC-087/088), cuộc thi (UC-090+) |
| Bình luận, trả lời lồng ≤ 3 cấp, sửa trong 15 phút, tự xoá (UC-071, UC-072) | Kiểm nội dung bằng AI — bài luôn chờ `MODERATOR` duyệt tay (fallback `AI_MODERATION_UNAVAILABLE` của UC-069) |
| Thích/bỏ thích bài (UC-073) | Thích bình luận |
| Báo cáo bài/bình luận (UC-075) | **Tự ẩn theo ngưỡng báo cáo** — BR-075-7, ngưỡng chưa chốt |
| Duyệt / từ chối bài (UC-076, UC-077) | Duyệt hàng loạt (UC-076 A1) |
| Xử lý báo cáo gộp theo nội dung (UC-078) | Treo/khoá tài khoản (UC-077 A2, UC-078 A3) — `AuthApi` chưa có hàm; thêm sau khi leader duyệt |
| Ẩn / xoá / khôi phục bình luận (UC-079) | Ẩn cả nhánh bình luận (UC-079 A1) |
| | Khoá bình luận của bài: cột `comments_locked` có sẵn nhưng spec này không có thao tác đặt cờ, luôn `false` |
| | Tác giả tự xoá bài — `posts.status` không có `DELETED`; cần migration, để sau |
| | Thông báo cho tác giả/người báo cáo — chưa có hệ thống thông báo; tác giả xem trạng thái ở "Bài viết của tôi" |
| | Ảnh đại diện tác giả — `AuthApi.displayNames` chỉ trả tên |
| | Tìm kiếm theo từ khoá (SCR-039 open issue) |

### 0.2 · Không đổi schema

Dùng nguyên 4 bảng của `V2__community.sql` đã chạy trên DB chung: `posts`, `comments`, `reactions`, `moderation_cases`.
**Không có migration.** Cần thêm cột/bảng → dừng, báo leader cấp số `V5+` (`database.md` §11).

Tài liệu UC dùng tên cũ — spec này theo tên trong DB:

| Tên trong UC | Bảng thật |
|---|---|
| `likes` | `reactions` với `kind='LIKE'`, `target_type='POST'` |
| `moderation_reports` | `reactions` `kind='REPORT'` (mỗi người một dòng, chặn trùng) + `moderation_cases` (một hồ sơ chờ xử lý cho mỗi nội dung) |

### 0.3 · Chuẩn hoá mâu thuẫn giữa UC, screen-fields và spec 001

Spec/OpenAPI đứng trên `screen-fields.md` và mã lỗi rải rác trong UC.

| Mâu thuẫn | Chốt trong spec này |
|---|---|
| UC-069/071 trả **422** `EMAIL_NOT_VERIFIED` | Dùng `AuthApi.requireVerifiedEmail` của spec 001 → **403** `ACCOUNT_UNVERIFIED` |
| UC trả **400** cho rỗng/quá dài/sai phân loại | **422** `VALIDATION_FAILED` kèm `details` (quy ước spec 001) |
| Bình luận/thích/báo cáo bài chưa duyệt: **403** `POST_NOT_APPROVED` | **404** `POST_NOT_FOUND` — không tiết lộ bài chưa công khai có tồn tại |
| UC-069 A3 cấm sửa bài đã duyệt vs BR-069-6 cho sửa và về `PENDING` | Theo BR-069-6 (SCR-041 đã chốt A3 là lỗi thời) |
| Tiêu đề: UI cũ 200, DB 300 | **≤ 200** ký tự (chặt hơn DB, vừa UI) |
| UC-075 T5 "5 báo cáo thì tự ẩn" vs BR-075-7 | Không tự ẩn. `moderation_cases.auto_hidden` luôn `false` |
| UC-076 "một `MODERATOR` thì `ADMIN` duyệt" vs không kế thừa role | Chỉ `MODERATOR`. `ADMIN` không có role này thì 403 |
| `429` mỗi UC một mã | Một mã **429** `TOO_MANY_REQUESTS` kèm `retryAfterSeconds` (đã có ở spec 001) |
| Danh mục lý do báo cáo chưa có | 5 lý do ở FR-034 — **chốt tạm**, §9 Q2 |

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-04** | Nội dung qua kiểm duyệt trước khi tới người học | Bài chỉ công khai sau khi `MODERATOR` duyệt; bình luận xử lý theo báo cáo |
| **BG-02** | Giữ người học quay lại | Nơi hỏi bài, chia sẻ kinh nghiệm, tìm bạn học |

**Vấn đề:** mở cho người dùng viết nội dung công khai là mở ba đường tấn công: bài chưa duyệt lọt ra ngoài,
script trong bài/bình luận chạy trên máy `MODERATOR`, và sửa bài đã duyệt thành nội dung xấu.

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Bài `DRAFT`/`PENDING`/`REJECTED`/`HIDDEN` xuất hiện ở endpoint công khai | 0 lần |
| **SC-002** | `MODERATOR` duyệt → bài có trong danh sách công khai ở request kế tiếp | 100% |
| **SC-003** | Response công khai chứa `email` hoặc danh tính người báo cáo | 0 lần |
| **SC-004** | Tải danh sách 20 bài: số truy vấn SQL không đổi theo số bài | ≤ 5 truy vấn |

---

## 2 · User Stories

### US-1 · Người học đăng bài (P1)
**Given** tôi là `LEARNER` đã xác thực email
**When** tôi viết tiêu đề, nội dung, chọn "Hỏi bài", bấm "Gửi duyệt"
**Then** tôi thấy "Bài đã gửi, chờ duyệt"; bài nằm ở "Bài viết của tôi" với trạng thái Chờ duyệt, **không** có ở danh sách công khai

### US-2 · Kiểm duyệt bài (P1)
**Given** tôi là `MODERATOR`, có 3 bài chờ duyệt
**When** tôi mở hàng đợi (cũ nhất trước), đọc bài và bấm "Duyệt"
**Then** bài hiện ngay ở danh sách công khai
**When** tôi từ chối bài khác với lý do "Trùng chủ đề đã có"
**Then** tác giả thấy trạng thái Bị từ chối kèm lý do đó, sửa và gửi lại được

### US-3 · Thảo luận (P2)
**Given** bài đã duyệt
**When** tôi bình luận, người khác trả lời bình luận của tôi, tôi thích bài
**Then** cây bình luận hiện đúng cấp, số bình luận và số thích đúng với dữ liệu

### US-4 · Báo cáo và xử lý (P2)
**Given** 3 người báo cáo cùng một bình luận
**When** `MODERATOR` mở hàng đợi báo cáo
**Then** thấy **một** hồ sơ có 3 báo cáo, lý do và người báo cáo
**When** `MODERATOR` chọn "Ẩn nội dung" kèm ghi chú
**Then** bình luận thành "[đã bị ẩn]", các trả lời còn nguyên, hồ sơ đóng một lần cho cả 3 báo cáo

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Hai `MODERATOR` cùng bấm duyệt/từ chối một bài | Một 200, một **409** `POST_NOT_PENDING` (FR-042) |
| E2 | Tác giả sửa bài đã duyệt | Bài về `PENDING`, biến khỏi danh sách công khai tới khi duyệt lại (FR-011) |
| E3 | Client gửi `status`, `authorId`, `reviewedBy` trong body | Bị bỏ qua, chỉ ghi `title`, `content`, `category` (FR-004) |
| E4 | Trả lời bình luận ở cấp 3 | Trả lời gắn vào cùng cha với bình luận đó, ở cấp 3 (FR-022) |
| E5 | `parentId` thuộc bài chưa duyệt | **404** `COMMENT_NOT_FOUND`, response không chứa nội dung cha (FR-021) |
| E6 | Xoá bình luận đã có trả lời | Dòng giữ trong DB, hiện "[đã xoá]", trả lời còn (FR-026, FR-018) |
| E7 | Hai request thích song song | Đúng một dòng `reactions` (FR-031) |
| E8 | `MODERATOR` xử lý báo cáo về nội dung của chính mình | **403** `SELF_MODERATION` (FR-049) |
| E9 | Hồ sơ báo cáo đã đóng, người mới báo cáo cùng nội dung | Tạo hồ sơ `PENDING` mới (FR-036) |
| E10 | Tác giả bị cấm tài khoản | Bài và bình luận vẫn hiện với tên hiển thị hiện có; không lỗi |

---

## 3 · Functional Requirements

### Nhóm 1 — Quy ước chung (FR-001…FR-006)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL cho mọi request không token gọi `GET /api/public/community/**`. Mọi path `/api/community/**` cần đăng nhập; `/api/community/moderation/**` cần role `MODERATOR` (kiểm ở `SecurityConfig` **và** ở service bằng `AuthApi.currentUser()`); các path còn lại của `/api/community/**` cần role `LEARNER`. Không có kế thừa role: `ADMIN` không có `MODERATOR` → **403** `FORBIDDEN` |
| **FR-002** | THE system SHALL gọi `AuthApi.requireVerifiedEmail(userId)` trước mọi thao tác ghi bài, bình luận, báo cáo (BR-069-2, BR-071-2); chưa xác thực → **403** `ACCOUNT_UNVERIFIED`. Thích/bỏ thích **không** cần xác thực (BR-073-2) |
| **FR-003** | THE system SHALL lấy `author_id`, `user_id`, `reviewed_by`, `moderated_by`, `handled_by` từ phiên đăng nhập, không bao giờ từ body (BR-069-9) |
| **FR-004** | THE system SHALL nhận body bằng DTO `record` chỉ chứa trường được phép ghi (vd bài: `title`, `content`, `category`, `submit`). Trường lạ bị bỏ qua, không lỗi (UC-069 T2/T3) |
| **FR-005** | THE system SHALL lưu nội dung dạng **văn bản thuần** sau khi bỏ khoảng trắng hai đầu; không nhận HTML/Markdown, không render HTML ở bất kỳ client nào. Frontend hiển thị bằng text node của React (không `dangerouslySetInnerHTML`); xuống dòng hiển thị bằng CSS `white-space: pre-wrap` (HR-07, BR-069-8) |
| **FR-006** | THE system SHALL trả thông tin người dùng ra ngoài dưới dạng `{id, displayName}` lấy **theo lô** qua `AuthApi.displayNames(Set<Long>)` một lần mỗi request. Không trả entity, `email`, role. `displayName` không có → `null`, frontend hiện "Người dùng đã rời" (BR-070-5, `AC-03`, `BUS-06`) |

### Nhóm 2 — Bài viết (FR-007…FR-016) · UC-069, UC-070

| Mã | Yêu cầu |
|---|---|
| **FR-007** | `POST /api/community/posts` `{title, content, category, submit}` SHALL kiểm: `title` 1–200 ký tự, `content` 1–50 000 ký tự (sau khi bỏ khoảng trắng hai đầu), `category` thuộc `EXPERIENCE`·`QUESTION`·`STUDY_BUDDY`·`CULTURE`·`JOB`, `submit` boolean bắt buộc; sai → **422** `VALIDATION_FAILED` |
| **FR-008** | THE system SHALL giới hạn **5 bài tạo mới mỗi ngày giờ Việt Nam** mỗi tác giả (đếm `posts.created_at ≥ 00:00 Asia/Ho_Chi_Minh hôm nay`, mọi trạng thái); bài thứ 6 → **429** `TOO_MANY_REQUESTS`, `retryAfterSeconds` = số giây tới 00:00 hôm sau (BR-069-7, `BUS-08`) |
| **FR-009** | WHEN tạo bài, THE system SHALL ghi `status = 'PENDING'` nếu `submit = true`, `'DRAFT'` nếu `false`; trả **201** `OwnPost` (§13) |
| **FR-010** | `PUT /api/community/posts/{id}` cùng body và kiểm như FR-007 SHALL chỉ cho tác giả sửa; không phải tác giả **hoặc** bài không tồn tại → **403** `FORBIDDEN` (`BUS-01`). Bài `HIDDEN` → **409** `POST_NOT_EDITABLE` |
| **FR-011** | WHEN sửa bài ở `DRAFT`/`PENDING`/`REJECTED`/`APPROVED`, THE system SHALL đặt trạng thái mới = `PENDING` nếu `submit = true`, `DRAFT` nếu `false`. Bài đang `APPROVED` sau khi sửa **rời khỏi danh sách công khai ngay** (BR-069-6). Giữ `review_reason` cũ để tác giả còn thấy lý do lần trước; `published_at` giữ nguyên |
| **FR-012** | `GET /api/public/community/posts?category&page&size` SHALL trả **chỉ** bài `APPROVED`, lọc `category` nếu có, sắp `published_at` giảm dần rồi `id` giảm dần; `page` ≥ 0 (mặc định 0), `size` 1–50 (mặc định 20); sai → **422**. Trang quá lớn → `items` rỗng, 200 |
| **FR-013** | THE system SHALL lọc trạng thái ở **tầng repository**: một method `findPublished…` duy nhất cho mọi endpoint công khai; không có method trả bài không điều kiện ngoài endpoint `moderation` (UC-070 `PENDING_POST_LEAKED`) |
| **FR-014** | Mỗi phần tử danh sách SHALL có `{id, title, category, excerpt, author, publishedAt, commentCount, likeCount}`. `excerpt` = 200 ký tự đầu của `content`. `commentCount` = số bình luận `VISIBLE`; `likeCount` = số `reactions` `LIKE` của bài. Đếm bằng **một** truy vấn `GROUP BY` cho mỗi loại trên cả trang, không đếm từng bài (UC-070 T6) |
| **FR-015** | `GET /api/public/community/posts/{id}` SHALL trả bài `APPROVED` đầy đủ `content` + các trường FR-014; bài không tồn tại hoặc không `APPROVED` → **404** `POST_NOT_FOUND` (cùng một response) |
| **FR-016** | `GET /api/community/posts/mine?status&page&size` SHALL trả bài của người gọi (mọi trạng thái, lọc `status` nếu có), sắp `updated_at` giảm dần, phần tử `OwnPost` gồm `status` và `reviewReason` (chỉ khi `REJECTED` hoặc `HIDDEN`). `GET /api/community/posts/mine/{id}` trả một bài của mình để mở trình sửa; không phải của mình/không tồn tại → **403** `FORBIDDEN`. Không có endpoint nào cho người khác đọc bài chưa công khai (BR-070-7) |

### Nhóm 3 — Bình luận và trả lời (FR-017…FR-027) · UC-071, UC-072

| Mã | Yêu cầu |
|---|---|
| **FR-017** | `POST /api/community/posts/{id}/comments` `{content}` SHALL kiểm bài `APPROVED` (khác → **404** `POST_NOT_FOUND`), `content` 1–5 000 ký tự (**422**), ghi `parent_id = NULL`, `depth = 0`, `status = 'VISIBLE'`; trả **201** `Comment` |
| **FR-018** | `GET /api/public/community/posts/{id}/comments` SHALL đọc mọi bình luận của bài `APPROVED` bằng **một** truy vấn phẳng sắp `created_at` tăng dần, dựng cây ở code. Bình luận `VISIBLE` trả đủ; bình luận `HIDDEN`/`DELETED` **chỉ** được trả khi còn ít nhất một hậu duệ được trả, dưới dạng `{id, parentId, depth, status, content: null, author: null, createdAt}` — frontend hiện "[đã bị ẩn]" / "[đã xoá]" (BR-079-4, BR-079-5) |
| **FR-019** | THE system SHALL giới hạn **30 bình luận (gồm trả lời) mỗi 60 phút** mỗi người, đếm `comments.created_at > now() − 60 phút`; vượt → **429** `TOO_MANY_REQUESTS`, `retryAfterSeconds` = tới khi bình luận cũ nhất trong cửa sổ hết hạn (BR-071-9) |
| **FR-020** | `POST /api/community/comments/{parentId}/replies` `{content}` SHALL áp dụng FR-002, FR-017 (độ dài), FR-019 |
| **FR-021** | THE system SHALL tìm bình luận cha; không tồn tại **hoặc** bài của cha không `APPROVED` → **404** `COMMENT_NOT_FOUND` (cùng response, không lộ nội dung cha). Cha ở trạng thái nào cũng trả lời được (UC-072 A2). Bài của trả lời luôn = bài của cha (BR-072-3) |
| **FR-022** | THE system SHALL đặt: cha `depth < 3` → `parent_id = cha`, `depth = cha.depth + 1`; cha `depth = 3` → `parent_id = cha.parent_id`, `depth = 3` (BR-072-7). Response trả lời **không** chứa nội dung cha |
| **FR-023** | THE system SHALL KHÔNG có thao tác nào sửa `parent_id` hoặc `post_id` sau khi tạo (BR-072-5) |
| **FR-024** | `PUT /api/community/comments/{id}` `{content}` SHALL chỉ cho tác giả; không phải tác giả/không tồn tại → **403** `FORBIDDEN`. Bình luận không `VISIBLE` → **409** `COMMENT_NOT_EDITABLE`. Quá **15 phút** từ `created_at` → **409** `COMMENT_EDIT_WINDOW_EXPIRED` (BR-071-7). Kiểm độ dài như FR-017 |
| **FR-025** | Response `Comment` SHALL có `editedAt` = `updated_at` khi `updated_at > created_at` (đã sửa), ngược lại `null` |
| **FR-026** | `DELETE /api/community/comments/{id}` SHALL chỉ cho tác giả (`BUS-01`, **403** như FR-024), đặt `status = 'DELETED'`, `moderated_by = tác giả`, `moderation_reason = 'AUTHOR_DELETED'` (thoả `ck_cmt_mod`), trả **204**. Đã `DELETED`/`HIDDEN` → 204, không đổi gì. Không xoá cứng (`BUS-10`, BR-072-6) |
| **FR-027** | Số bình luận ở FR-014 và FR-015 SHALL không tính bình luận `HIDDEN`/`DELETED` |

### Nhóm 4 — Thích bài (FR-028…FR-031) · UC-073

| Mã | Yêu cầu |
|---|---|
| **FR-028** | `POST /api/community/posts/{id}/like` SHALL kiểm bài `APPROVED` (khác → **404** `POST_NOT_FOUND`), ghi `reactions(user_id, 'POST', id, 'LIKE')` bằng `INSERT … ON CONFLICT DO NOTHING`; trả **200** `{liked: true, likeCount}` dù đã thích trước đó (BR-073-5) |
| **FR-029** | `DELETE /api/community/posts/{id}/like` SHALL xoá dòng `LIKE` nếu có; trả **200** `{liked: false, likeCount}` dù chưa thích. Bài không `APPROVED` → **404** |
| **FR-030** | Tác giả được thích bài của mình (BR-073-6). Không có giới hạn tần suất thích — thao tác idempotent, không sinh dữ liệu rác (§9 Q4) |
| **FR-031** | THE system SHALL dựa vào khoá chính `reactions` để hai request song song chỉ tạo một dòng; `likeCount` luôn đếm từ `reactions`, không có cột đếm (BR-073-7) |

### Nhóm 5 — Báo cáo vi phạm (FR-032…FR-038) · UC-075

| Mã | Yêu cầu |
|---|---|
| **FR-032** | `POST /api/community/reports` `{targetType, targetId, reason, detail}` SHALL kiểm `targetType` ∈ `POST`·`COMMENT`, `reason` theo FR-034, `detail` ≤ 1 000 ký tự (bắt buộc khi `reason = 'OTHER'`); sai → **422** `VALIDATION_FAILED` |
| **FR-033** | THE system SHALL chỉ nhận báo cáo nội dung **đang công khai**: bài `APPROVED`; bình luận `VISIBLE` thuộc bài `APPROVED`. Khác hoặc không tồn tại → **404** `TARGET_NOT_FOUND` |
| **FR-034** | Lý do báo cáo SHALL là một trong: `SPAM` (Spam, quảng cáo) · `OFFENSIVE` (Ngôn từ xúc phạm) · `HARASSMENT` (Quấy rối, công kích cá nhân) · `COPYRIGHT` (Vi phạm bản quyền, chép đề thi) · `OTHER` (Khác — ghi rõ) |
| **FR-035** | Báo cáo nội dung của chính mình → **422** `SELF_REPORT` (BR-075-4). Quá **20 báo cáo mỗi ngày giờ Việt Nam** (đếm `reactions` `REPORT` của người gọi) → **429** `TOO_MANY_REQUESTS` (BR-075-8) |
| **FR-036** | Trong **một transaction** (`BUS-02`), THE system SHALL: (1) chèn `reactions(user_id, targetType, targetId, 'REPORT')` — trùng khoá chính → **409** `DUPLICATE_REPORT` (BR-075-3, một người một lần cho mỗi nội dung, kể cả sau khi hồ sơ cũ đã đóng); (2) upsert hồ sơ `moderation_cases` `PENDING` của nội dung đó bằng `INSERT … ON CONFLICT (target_type, target_id) WHERE status = 'PENDING' DO UPDATE SET report_count = report_count + 1, reasons = reasons || :entry`. `:entry` = `[{userId, reason, detail, reportedAt}]` |
| **FR-037** | THE system SHALL KHÔNG tự ẩn nội dung theo số báo cáo; `auto_hidden` luôn `false` (BR-075-7) |
| **FR-038** | Response **201** `{received: true}` — không trả id hồ sơ, số báo cáo hay ai khác đã báo cáo. Không endpoint nào ngoài `moderation` trả danh tính người báo cáo (BR-075-6) |

### Nhóm 6 — Duyệt bài (FR-039…FR-045) · UC-076, UC-077

| Mã | Yêu cầu |
|---|---|
| **FR-039** | `GET /api/community/moderation/posts?page&size` SHALL trả bài `PENDING`, sắp `created_at` tăng dần (cũ nhất trước), phân trang như FR-012; mỗi phần tử `{id, title, category, excerpt, author, createdAt, updatedAt}` |
| **FR-040** | `GET /api/community/moderation/posts/{id}` SHALL trả bài ở mọi trạng thái kèm `content`, `status`, `reviewReason`, `reviewedBy {id, displayName}`, `reviewedAt`; không tồn tại → **404** `POST_NOT_FOUND` |
| **FR-041** | `PATCH /api/community/moderation/posts/{id}/approve` SHALL: tác giả = người gọi → **403** `SELF_MODERATION` (BR-076-3, `ck_post_self`); rồi cập nhật bằng **một câu** `UPDATE … SET status='APPROVED', reviewed_by, reviewed_at = now(), published_at = COALESCE(published_at, now()) WHERE id = ? AND status = 'PENDING'` |
| **FR-042** | WHEN câu `UPDATE` ở FR-041/FR-043 ảnh hưởng 0 dòng, THE system SHALL trả **404** `POST_NOT_FOUND` nếu bài không tồn tại, ngược lại **409** `POST_NOT_PENDING` (BR-076-7). Không dùng khoá bi quan |
| **FR-043** | `PATCH /api/community/moderation/posts/{id}/reject` `{reason}` SHALL kiểm `reason` 1–1 000 ký tự sau khi bỏ khoảng trắng (**422**), kiểm `SELF_MODERATION` như FR-041, rồi `UPDATE … SET status='REJECTED', review_reason, reviewed_by, reviewed_at = now() WHERE id = ? AND status = 'PENDING'` (BR-077-5, BR-077-6). Chỉ giữ lý do gần nhất (BR-077-8) |
| **FR-044** | THE system SHALL ghi `audit_logs` trong cùng transaction: `COMMUNITY_POST_APPROVE` / `COMMUNITY_POST_REJECT`, `target_type='POST'`, `target_id`, `detail = {reason?}` (§4.3) |
| **FR-045** | Duyệt/từ chối trả **200** `ModerationPost` (FR-040) với trạng thái mới |

### Nhóm 7 — Xử lý báo cáo (FR-046…FR-051) · UC-078

| Mã | Yêu cầu |
|---|---|
| **FR-046** | `GET /api/community/moderation/reports?page&size` SHALL trả hồ sơ `PENDING`, sắp `report_count` giảm dần rồi `created_at` tăng dần; mỗi phần tử `{id, targetType, targetId, reportCount, latestReason, createdAt, targetPreview}`. `targetPreview` = 120 ký tự đầu của tiêu đề bài hoặc nội dung bình luận |
| **FR-047** | `GET /api/community/moderation/reports/{id}` SHALL trả hồ sơ ở mọi trạng thái: `status`, `reportCount`, `target` (bài: `{id, title, content, category, status, author}`; bình luận: `{id, content, status, author, postId, postTitle}`), `reports` = các phần tử `reasons` kèm `reporter {id, displayName}`, và `handledBy`, `handledAt`, `decisionNote` khi đã xử lý. Không tồn tại → **404** `REPORT_CASE_NOT_FOUND` |
| **FR-048** | `PATCH /api/community/moderation/reports/{id}` `{decision, note, commentAction}` SHALL kiểm `decision` ∈ `ACTION`·`REJECT`, `note` 1–1 000 ký tự, `commentAction` ∈ `HIDE`·`DELETE` **bắt buộc** khi `decision = ACTION` và hồ sơ trỏ tới bình luận, phải vắng mặt trong mọi trường hợp khác; sai → **422** `VALIDATION_FAILED`. Không có giá trị nào cấm/treo tài khoản (BR-078-7) |
| **FR-049** | Tác giả nội dung bị báo cáo = người gọi → **403** `SELF_MODERATION` |
| **FR-050** | Trong **một transaction**: đóng hồ sơ bằng `UPDATE moderation_cases SET status, handled_by, handled_at = now(), decision_note WHERE id = ? AND status = 'PENDING'` (0 dòng → **409** `REPORT_ALREADY_HANDLED`), rồi với `ACTION`: bài → `status='HIDDEN'`, `reviewed_by`, `reviewed_at`, `review_reason = note`; bình luận → `status = HIDDEN|DELETED`, `moderated_by`, `moderation_reason = note`. Nội dung đã ở trạng thái đích → không ghi lại, hồ sơ vẫn đóng (`TARGET_ALREADY_DELETED`). Với `REJECT`: không đổi nội dung (không có tự ẩn nên không có gì để khôi phục). Một hồ sơ chứa mọi báo cáo của nội dung → đóng một lần là đóng hết (BR-078-3, BR-078-6) |
| **FR-051** | THE system SHALL ghi `audit_logs` `COMMUNITY_REPORT_ACTION` / `COMMUNITY_REPORT_REJECT`, `target_type='REPORT_CASE'`, `detail = {targetType, targetId, commentAction?}`; trả **200** chi tiết hồ sơ (FR-047) |

### Nhóm 8 — Kiểm duyệt bình luận trực tiếp (FR-052…FR-055) · UC-079

| Mã | Yêu cầu |
|---|---|
| **FR-052** | `PATCH /api/community/moderation/comments/{id}` `{action, reason}` SHALL kiểm `action` ∈ `HIDE`·`DELETE`·`RESTORE`, `reason` 1–1 000 ký tự (BR-079-6); sai → **422**. Không tồn tại → **404** `COMMENT_NOT_FOUND` |
| **FR-053** | `HIDE` → `HIDDEN`, `DELETE` → `DELETED`, `RESTORE` → `VISIBLE`; ghi `moderated_by`, `moderation_reason = reason`. Bình luận đã ở trạng thái đích → **200**, không ghi, không audit (`ALREADY_HIDDEN`). `RESTORE` bình luận tác giả tự xoá (`moderation_reason = 'AUTHOR_DELETED'`) → **409** `COMMENT_NOT_RESTORABLE` |
| **FR-054** | Chỉ đổi đúng một bình luận; các trả lời giữ nguyên trạng thái (BR-079-7). Được xử lý bình luận của `MODERATOR` khác; **không** được xử lý bình luận của chính mình → **403** `SELF_MODERATION` |
| **FR-055** | THE system SHALL ghi `audit_logs` `COMMUNITY_COMMENT_HIDE` / `…_DELETE` / `…_RESTORE`, `target_type='COMMENT'`, `detail = {before, after, reason}` trong cùng transaction (BR-079-8); trả **200** `ModerationComment {id, postId, parentId, depth, status, content, author, createdAt, moderatedBy, moderationReason}` |

### Nhóm 9 — Trạng thái người xem và giao diện (FR-056…FR-063)

| Mã | Yêu cầu |
|---|---|
| **FR-056** | `GET /api/community/posts/{id}/viewer-state` SHALL trả `{liked, reported}` của người gọi với bài `APPROVED` (khác → **404**). Endpoint công khai không đọc phiên người xem |
| **FR-057** | SCR-039 SHALL có: tab "Bài công khai" / "Bài viết của tôi" (tab sau chỉ khi là `LEARNER`), lọc 5 danh mục (nhãn FR-061), thẻ bài theo FR-014, phân trang, nút "Viết bài" (`GUEST` bấm → SCR-002). Tab của tôi hiện nhãn trạng thái và `reviewReason` |
| **FR-058** | SCR-040 SHALL hiện bài, cây bình luận thụt theo `depth` (FR-018), nút thích (đổi ngay theo response, khoá khi đang gửi), ô bình luận, "Trả lời", "Sửa" (chỉ bình luận của mình, còn trong 15 phút), "Xoá" (bình luận của mình, xác nhận trong trang), "Báo cáo" (form trong trang: chọn lý do FR-034 + chi tiết). `GUEST` chỉ đọc, các nút dẫn tới SCR-002. Tác giả bài thấy nút "Sửa bài viết" → SCR-041 |
| **FR-059** | SCR-041 SHALL có ô tiêu đề (đếm ký tự /200), danh mục, nội dung (đếm /50 000), nút "Lưu nháp" (`submit=false`) và "Gửi duyệt" (`submit=true`). Sửa bài `APPROVED` hiện cảnh báo "Bài sẽ tạm ẩn khỏi cộng đồng cho tới khi được duyệt lại". Bài `REJECTED` hiện lý do lần trước. Kiểm độ dài phía client trước khi gửi (server vẫn kiểm lại) |
| **FR-060** | SCR-060 hàng đợi bài chờ duyệt, SCR-061 chi tiết + "Duyệt" / "Từ chối" (lý do bắt buộc, ≤ 1 000, trong trang). Nút "Duyệt"/"Từ chối" ẩn khi tác giả là người đang xem. 409 → báo "Bài đã được người khác xử lý", tải lại hàng đợi |
| **FR-061** | Nhãn danh mục: `EXPERIENCE` Chia sẻ kinh nghiệm · `QUESTION` Hỏi bài · `STUDY_BUDDY` Tìm bạn học · `CULTURE` Văn hoá · `JOB` Việc làm. Nhãn trạng thái: `DRAFT` Bản nháp · `PENDING` Chờ duyệt · `APPROVED` Đã đăng · `REJECTED` Bị từ chối · `HIDDEN` Đã bị ẩn |
| **FR-062** | SCR-062 hàng đợi hồ sơ báo cáo (FR-046), SCR-063 chi tiết (FR-047) với "Bác báo cáo" và "Xử lý nội dung" (bình luận: chọn Ẩn/Xoá; ghi chú bắt buộc). Danh tính người báo cáo chỉ hiện trên SCR-063 |
| **FR-063** | Khi người xem có role `MODERATOR`, SCR-040 SHALL tải cây bình luận từ `GET /api/community/moderation/posts/{id}/comments` thay cho endpoint công khai: trả **mọi** bình luận của bài (mọi trạng thái bài) kèm `content`, `author`, `status`, `moderatedBy`, `moderationReason` (phần tử `ModerationComment`), sắp `created_at` tăng dần; bài không tồn tại → **404** `POST_NOT_FOUND`. Mỗi bình luận có nút Ẩn / Xoá (khi `VISIBLE`) hoặc Khôi phục (khi `HIDDEN`/`DELETED` không phải `AUTHOR_DELETED`), lý do bắt buộc, xác nhận trong trang |

Trên SCR-040, FR-063 áp dụng cho bài APPROVED; bài ở trạng thái khác kiểm duyệt tại SCR-061.
### Key Entities

| Bảng | Dùng thế nào |
|---|---|
| `community.posts` | Bài, trạng thái, nhật ký duyệt gộp (`reviewed_by`, `reviewed_at`, `review_reason`) |
| `community.comments` | Bình luận lồng, soft delete bằng `status` |
| `community.reactions` | `LIKE` (thích bài) · `REPORT` (mỗi người một báo cáo cho mỗi nội dung). Không dùng `FOLLOW`, `USER`, `COLLECTION` |
| `community.moderation_cases` | Một hồ sơ `PENDING` gộp mọi báo cáo của một nội dung |
| `shared.audit_logs` | Mọi quyết định của `MODERATOR` (§4.3) |
| `auth.users` | **Chỉ** qua `AuthApi` — không entity, không repository, không `JOIN` trong module `community` |

---

## 4 · Data Model

Bảng đã có ở `V2__community.sql` — entity viết đúng theo SQL (`AC-05`), mỗi entity khai `schema = "community"`.

### 4.1 · Vòng đời bài viết

```
           submit=false                     submit=true
(tạo) ───────────────► DRAFT ──(sửa, submit=true)──► PENDING ◄──(tạo, submit=true)
                         ▲                             │  │
                         └──(sửa, submit=false)────────┘  ├─approve─► APPROVED ──(tác giả sửa)──► PENDING / DRAFT
                                                          │               │
                                                          └─reject──► REJECTED ──(tác giả sửa)──► PENDING / DRAFT
                                                                          │
                                         APPROVED/PENDING ──(hồ sơ báo cáo ACTION)──► HIDDEN (không sửa, không khôi phục trong spec này)
```

Chỉ `APPROVED` xuất hiện ở `/api/public/**`. `HIDDEN` chỉ tác giả (tab của tôi) và `MODERATOR` thấy.

### 4.2 · Vòng đời bình luận

| Từ | Thao tác | Sang | Ai |
|---|---|---|---|
| — | tạo / trả lời | `VISIBLE` | `LEARNER` |
| `VISIBLE` | sửa ≤ 15 phút | `VISIBLE` | tác giả |
| `VISIBLE` | tự xoá | `DELETED` (`AUTHOR_DELETED`) | tác giả |
| `VISIBLE` | `HIDE` / `DELETE` hoặc hồ sơ `ACTION` | `HIDDEN` / `DELETED` | `MODERATOR` |
| `HIDDEN` / `DELETED` (bởi `MODERATOR`) | `RESTORE` | `VISIBLE` | `MODERATOR` |

### 4.3 · `shared.audit_logs` — `action` spec này ghi

| `action` | `target_type` · `target_id` | `detail` |
|---|---|---|
| `COMMUNITY_POST_APPROVE` | `POST` · id bài | `{}` |
| `COMMUNITY_POST_REJECT` | `POST` · id bài | `{reason}` |
| `COMMUNITY_REPORT_ACTION` | `REPORT_CASE` · id hồ sơ | `{targetType, targetId, commentAction?}` |
| `COMMUNITY_REPORT_REJECT` | `REPORT_CASE` · id hồ sơ | `{targetType, targetId}` |
| `COMMUNITY_COMMENT_HIDE` · `…_DELETE` · `…_RESTORE` | `COMMENT` · id bình luận | `{before, after, reason}` |

Ghi bằng `AuditService` (spec 001). Tác giả tự xoá bình luận **không** ghi audit.

### 4.4 · `moderation_cases.reasons`

Mảng JSON chỉ nối thêm, chỉ đọc để hiển thị, **không** truy vấn điều kiện trên đó (`AC-09`):

```json
[{"userId": 42, "reason": "SPAM", "detail": null, "reportedAt": "2026-10-27T02:15:00Z"}]
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập ở `/api/community/**` | spec 001 |
| `FORBIDDEN` | 403 | Thiếu role; sửa/xoá/đọc tài nguyên không phải của mình hoặc không tồn tại | FR-001, FR-010, FR-016, FR-024, FR-026 |
| `CSRF_TOKEN_INVALID` | 403 | Request ghi bằng cookie thiếu/sai CSRF | spec 001 |
| `ACCOUNT_UNVERIFIED` | 403 | Chưa xác thực email khi ghi bài/bình luận/báo cáo | FR-002 |
| `SELF_MODERATION` | 403 | `MODERATOR` duyệt/từ chối/xử lý nội dung của chính mình | FR-041, FR-043, FR-049, FR-054 |
| `VALIDATION_FAILED` | 422 | Sai độ dài, danh mục, lý do, phân trang, thiếu `commentAction`… kèm `details` | FR-007, FR-012, FR-017, FR-032, FR-043, FR-048, FR-052 |
| `SELF_REPORT` | 422 | Báo cáo nội dung của mình | FR-035 |
| `POST_NOT_FOUND` | 404 | Bài không tồn tại hoặc không `APPROVED` (endpoint công khai/tương tác); không tồn tại (moderation) | FR-015, FR-017, FR-028, FR-040, FR-042 |
| `COMMENT_NOT_FOUND` | 404 | Cha không tồn tại/thuộc bài chưa duyệt; bình luận không tồn tại (moderation) | FR-021, FR-052 |
| `TARGET_NOT_FOUND` | 404 | Báo cáo nội dung không công khai/không tồn tại | FR-033 |
| `REPORT_CASE_NOT_FOUND` | 404 | Hồ sơ không tồn tại | FR-047 |
| `POST_NOT_EDITABLE` | 409 | Sửa bài `HIDDEN` | FR-010 |
| `POST_NOT_PENDING` | 409 | Duyệt/từ chối bài không còn `PENDING` | FR-042 |
| `COMMENT_NOT_EDITABLE` | 409 | Sửa bình luận không `VISIBLE` | FR-024 |
| `COMMENT_EDIT_WINDOW_EXPIRED` | 409 | Sửa sau 15 phút | FR-024 |
| `COMMENT_NOT_RESTORABLE` | 409 | Khôi phục bình luận tác giả tự xoá | FR-053 |
| `DUPLICATE_REPORT` | 409 | Đã báo cáo nội dung này | FR-036 |
| `REPORT_ALREADY_HANDLED` | 409 | Hồ sơ đã xử lý | FR-050 |
| `TOO_MANY_REQUESTS` | 429 | Vượt 5 bài/ngày, 30 bình luận/giờ, 20 báo cáo/ngày; kèm `retryAfterSeconds` | FR-008, FR-019, FR-035 |

Mã mới thêm vào `ErrorCode` của spec 001; định dạng `{error_code, message, request_id}` không đổi. `message` tiếng Việt, không chứa SQL.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-01** API First | `contracts/openapi-community.yaml` duyệt trước khi code; test hợp đồng so `/v3/api-docs` |
| **AC-03** Ranh giới module | Chỉ `auth.api.AuthApi` (`currentUser`, `requireVerifiedEmail`, `hasRole`, `displayNames`). Không import `auth.entity`/`auth.repository`/`auth.service`; ArchUnit có sẵn kiểm |
| **AC-05** Flyway | Không migration; entity theo `V2__community.sql`, `ddl-auto: validate` |
| **AC-06** Enum | `PostStatus`, `PostCategory`, `CommentStatus`, `ReactionKind`, `TargetType`, `CaseStatus`, `ReportReason` là enum Java `@Enumerated(STRING)` khớp `CHECK`; `ReportReason` chỉ nằm trong JSON, không có `CHECK` |
| **AC-08** Thời gian | `TIMESTAMPTZ`; mốc "ngày" của FR-008, FR-035 tính theo `Asia/Ho_Chi_Minh` qua bean `Clock` |
| **AC-09** JSONB | `reasons` chỉ nối thêm và đọc hiển thị |
| **HR-05** CSRF | Mọi `POST/PUT/PATCH/DELETE` qua cookie cần `X-XSRF-TOKEN` (cơ chế spec 001) |
| **HR-07** Validate + sanitize | Bean Validation trên DTO `record`; văn bản thuần, không render HTML (FR-005) |
| **HR-09** | Lỗi chung, không lộ SQL |
| **BUS-01** | Không phải chủ sở hữu hoặc không tồn tại → 403 cho tài nguyên của người dùng (FR-010, FR-016, FR-024, FR-026). Endpoint công khai trả 404 cho bài chưa công khai — không phải tài nguyên của người gọi |
| **BUS-02** | Báo cáo (FR-036), xử lý hồ sơ (FR-050), quyết định + audit cùng transaction |
| **BUS-06** | Không trả email ở bất kỳ response nào |
| **BUS-08** | Giới hạn theo ngày tính giờ Việt Nam |
| **BUS-10** | Bình luận soft delete bằng `status`; mọi truy vấn đọc lọc theo FR-018, FR-027 |

**Thay đổi ngoài module `community` (cần leader đồng ý khi duyệt spec):**

| File | Thay đổi |
|---|---|
| `…/shared/config/SecurityConfig.java` | `permitAll` cho `GET /api/public/community/**`; `hasRole("MODERATOR")` cho `/api/community/moderation/**`; `hasRole("LEARNER")` cho phần còn lại của `/api/community/**` |
| `…/shared/exception/ErrorCode.java` | Thêm mã ở §5 chưa có |
| `cnhsk-web/src/app/routeAccess.ts`, `screenRegistry.ts` | Thêm route §12 và catalog `community` |

**Thành phần:**

| Lớp | Trách nhiệm |
|---|---|
| `community.post.*` | Entity `Post`, `PostRepository` (gồm `findPublished…`), `PostService`, `PostQueryService`, controller công khai + của tôi |
| `community.comment.*` | `Comment`, `CommentRepository`, `CommentService`, `CommentTreeBuilder` |
| `community.reaction.*` | `Reaction` (khoá chính ghép), `ReactionRepository`, `LikeService` |
| `community.moderation.*` | `ModerationCase`, `ReportService`, `PostModerationService`, `ReportCaseService`, `CommentModerationService`, controller `moderation` |
| `community.common.*` | `CommunityAuthors` (gom id → `AuthApi.displayNames`), `CommunityLimits` (FR-008, FR-019, FR-035), enum |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Danh sách 20 bài (FR-012) | < 300 ms p95; ≤ 5 truy vấn (trang, đếm tổng, đếm bình luận, đếm thích, tên tác giả qua `AuthApi`) |
| `NFR-P02` | Cây 200 bình luận (FR-018) | < 300 ms p95; 1 truy vấn bình luận + 1 lần `displayNames` |
| `NFR-S01` | Không bài chưa công khai ở `/api/public/**` | SC-001, AT-10 |
| `NFR-S02` | Không danh tính người báo cáo ngoài `moderation` | SC-003, AT-31 |
| `NFR-S03` | Không HTML được render | FR-005, AT-48 |
| `NFR-R01` | Hai `MODERATOR` đồng thời không ghi đè nhau | FR-042, FR-050 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đổi schema | Không |
| Thêm dependency | Không (không cần thư viện sanitize vì không render HTML) |
| Đọc bảng module khác | Không — chỉ `AuthApi` |
| JSONB bị truy vấn điều kiện | Không |
| Mọi FR có test | Có — §14 |
| Mọi endpoint có integration test (`ES-05`) | Có — AT-01…AT-47, AT-51 |

---

## 9 · Quyết định đã chốt và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Phạm vi UC-069…073, UC-075…079; bỏ UC-074, khoá tài khoản, tự xoá bài, khoá bình luận | **Chốt** (leader 2026-10-27) |
| Q2 | 5 lý do báo cáo FR-034 | **Chốt** (leader 2026-10-29) |
| Q3 | Giới hạn 5 bài/ngày, 30 bình luận/giờ, 20 báo cáo/ngày, sửa bình luận 15 phút | **Chốt** (leader 2026-10-29) — đổi bằng cấu hình `cnhsk.community.*` |
| Q4 | Bỏ giới hạn 200 lượt thích/giờ của UC-073 | **Chốt** (leader 2026-10-29) — thích là idempotent, không tạo dữ liệu rác |
| Q5 | Không tự ẩn theo ngưỡng báo cáo | **Chốt** theo BR-075-7 |
| Q6 | Tiêu đề ≤ 200 ký tự | **Chốt** (leader 2026-10-29, §0.3) |
| Q7 | Sửa sơ đồ use case/UC cho khớp tên bảng `reactions`/`moderation_cases` | Gom vào PR tài liệu sau, không chặn spec |

---

## 10 · Frontend Implementation Note

Thư mục `cnhsk-web/src/features/community/**`; catalog `screens.ts` theo mẫu `features/admin-users/screens.ts`.
Gọi API qua `shared/api/client.ts` có sẵn (CSRF, tự làm mới phiên). Field, nhãn, trạng thái theo `screen-fields.md`
SCR-039/040/041/060–063, trừ chỗ §0.3 đã chuẩn hoá. Nội dung người dùng **chỉ** render bằng text (FR-005).
Mọi bước xác nhận (xoá bình luận, từ chối, xử lý báo cáo) nằm trong trang, không `window.confirm`.

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| `MODERATOR` duyệt tay không kịp khi nhiều bài (UC-076 `MODERATION_QUEUE_TOO_LARGE`) | Quy mô đồ án thấp; câu trả lời cho hội đồng: phương án "duyệt hết" an toàn, mở rộng được bằng duyệt theo tuổi tài khoản sau |
| Chỉ còn lý do từ chối gần nhất (UC-077 `REASON_HISTORY_LOST`) | Đánh đổi khi gộp nhật ký duyệt vào `posts` (BR-077-8); `audit_logs` vẫn giữ mọi lần |
| Một người không báo cáo lại được nội dung đã báo cáo, kể cả sau khi hồ sơ cũ đóng | Khoá chính `reactions` đơn giản và chống lạm dụng; người khác vẫn báo cáo được |
| `MODERATOR` ẩn bình luận phê bình mình (UC-079 `MANAGER_HIDES_OWN_CRITICISM`) | Không chặn được bằng code; mọi quyết định có `audit_logs` để `ADMIN` tra |
| Đếm bình luận theo giờ không có index `author_id` | Bảng nhỏ ở quy mô đồ án; thêm index cần migration — làm khi đo thấy chậm |

---

## 12 · Màn hình

| SCR | Route | Guard (`routeAccess`) | API | Field chi tiết |
|---|---|---|---|---|
| SCR-039 Community Post List | `/community` | `public` | public posts, mine | `screen-fields.md` dòng 20281 |
| SCR-040 Community Post Detail | `/community/posts/:id` | `public` | public post, comments, viewer-state, like, comment, reply, report; `MODERATOR`: moderation comments, comment moderation | dòng 20753 |
| SCR-041 Create / Edit Post | `/community/posts/new`, `/community/posts/:id/edit` | `['LEARNER']` | create, update, mine/{id} | dòng 21516 |
| SCR-060 Post Moderation Queue | `/moderation/posts` | `['MODERATOR']` | moderation posts | dòng 30019 |
| SCR-061 Post Moderation Detail | `/moderation/posts/:id` | `['MODERATOR']` | moderation post, approve, reject | dòng 30360 |
| SCR-062 Violation Report Queue | `/moderation/reports` | `['MODERATOR']` | moderation reports | dòng 30760 |
| SCR-063 Violation Report Detail | `/moderation/reports/:id` | `['MODERATOR']` | report detail, decision, comment moderation | dòng 31122 |

Layout: SCR-039/040 `public` (thanh điều hướng theo phiên), SCR-041 `learner`, SCR-060…063 `admin`.
Trạng thái bắt buộc mỗi màn: Loading (skeleton), Empty (theo FR-057, "Chưa có bài chờ duyệt", "Không có báo cáo chờ xử lý"),
Error (lỗi tải khác danh sách rỗng), nút đang gửi bị khoá.

---

## 13 · API Contract

[`contracts/openapi-community.yaml`](contracts/openapi-community.yaml)

| Method · Path | Request | Thành công | Lỗi chính |
|---|---|---|---|
| `GET /api/public/community/posts` | `category`, `page`, `size` | 200 `PostSummaryPage` | 422 |
| `GET /api/public/community/posts/{id}` | — | 200 `PostDetail` | 404 |
| `GET /api/public/community/posts/{id}/comments` | — | 200 `[Comment]` | 404 |
| `POST /api/community/posts` | `PostWrite` | 201 `OwnPost` | 403, 422, 429 |
| `PUT /api/community/posts/{id}` | `PostWrite` | 200 `OwnPost` | 403, 409, 422 |
| `GET /api/community/posts/mine` | `status`, `page`, `size` | 200 `OwnPostPage` | 422 |
| `GET /api/community/posts/mine/{id}` | — | 200 `OwnPost` | 403 |
| `GET /api/community/posts/{id}/viewer-state` | — | 200 `ViewerState` | 404 |
| `POST /api/community/posts/{id}/like` | — | 200 `LikeState` | 404 |
| `DELETE /api/community/posts/{id}/like` | — | 200 `LikeState` | 404 |
| `POST /api/community/posts/{id}/comments` | `CommentWrite` | 201 `Comment` | 403, 404, 422, 429 |
| `POST /api/community/comments/{id}/replies` | `CommentWrite` | 201 `Comment` | 403, 404, 422, 429 |
| `PUT /api/community/comments/{id}` | `CommentWrite` | 200 `Comment` | 403, 409, 422 |
| `DELETE /api/community/comments/{id}` | — | 204 | 403 |
| `POST /api/community/reports` | `ReportWrite` | 201 `ReportReceived` | 403, 404, 409, 422, 429 |
| `GET /api/community/moderation/posts` | `page`, `size` | 200 `ModerationPostPage` | 403, 422 |
| `GET /api/community/moderation/posts/{id}` | — | 200 `ModerationPost` | 403, 404 |
| `PATCH /api/community/moderation/posts/{id}/approve` | — | 200 `ModerationPost` | 403, 404, 409 |
| `PATCH /api/community/moderation/posts/{id}/reject` | `RejectWrite` | 200 `ModerationPost` | 403, 404, 409, 422 |
| `GET /api/community/moderation/reports` | `page`, `size` | 200 `ReportCasePage` | 403, 422 |
| `GET /api/community/moderation/reports/{id}` | — | 200 `ReportCaseDetail` | 403, 404 |
| `PATCH /api/community/moderation/reports/{id}` | `ReportDecision` | 200 `ReportCaseDetail` | 403, 404, 409, 422 |
| `GET /api/community/moderation/posts/{id}/comments` | — | 200 `[ModerationComment]` | 403, 404 |
| `PATCH /api/community/moderation/comments/{id}` | `CommentModeration` | 200 `ModerationComment` | 403, 404, 409, 422 |

---

## 14 · Acceptance Tests

Test BE dùng Testcontainers (`AbstractIntegrationTest` của spec 001). Dữ liệu tự tạo trong test, không seed.
Người dùng test tạo qua đường đăng ký/role của spec 001 hoặc helper test của spec 001 — không ghi thẳng bảng `auth` từ code `community`.

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | G không token · W `GET /api/public/community/posts` · T 200; W `POST /api/community/posts` · T 401 |
| AT-02 | FR-001 | G `ADMIN` không có `MODERATOR`; G `LEARNER` · W `GET /api/community/moderation/posts` · T 403 cả hai |
| AT-03 | FR-002 | G chưa xác thực email · W tạo bài, bình luận, báo cáo · T 403 `ACCOUNT_UNVERIFIED`; W thích · T 200 |
| AT-04 | FR-003, FR-004 | W tạo bài kèm `status:"APPROVED"`, `authorId: <người khác>` · T 201, `status = PENDING`, `author_id` = người gọi |
| AT-05 | FR-007 | W tiêu đề 201 ký tự; nội dung chỉ khoảng trắng; `category:"NEWS"` · T 422 `VALIDATION_FAILED` cả ba |
| AT-06 | FR-008 | G đã tạo 5 bài hôm nay (giờ VN, đồng hồ giả 23:30) · W bài thứ 6 · T 429, `retryAfterSeconds` = 1800 |
| AT-07 | FR-009 | W `submit=false` · T `DRAFT`; `submit=true` · T `PENDING` |
| AT-08 | FR-010 | W người khác sửa bài; W sửa id không tồn tại · T 403 cả hai, body giống nhau |
| AT-09 | FR-010 | G bài `HIDDEN` · W tác giả sửa · T 409 `POST_NOT_EDITABLE` |
| AT-10 | FR-011, FR-012, SC-001 | G bài `APPROVED` · W tác giả sửa `submit=true` · T bài `PENDING`, không còn ở danh sách công khai; `published_at` không đổi |
| AT-11 | FR-012, FR-013 | G 1 bài mỗi trạng thái `DRAFT`/`PENDING`/`APPROVED`/`REJECTED`/`HIDDEN` · W danh sách công khai · T đúng 1 bài |
| AT-12 | FR-012 | W `category=QUESTION`; `size=51`; `page=99` · T chỉ bài `QUESTION`; 422; 200 rỗng |
| AT-13 | FR-014, SC-004 | G 20 bài, mỗi bài 3 bình luận + 2 thích · W danh sách · T số đếm đúng; số câu SQL (đếm bằng `StatementInspector`/datasource proxy test) ≤ 5 và bằng nhau giữa trang 5 bài và trang 20 bài |
| AT-14 | FR-015 | W chi tiết bài `PENDING`; id không tồn tại · T 404 `POST_NOT_FOUND`, body giống nhau |
| AT-15 | FR-016 | G A có bài `PENDING`, B có bài `PENDING` · W A gọi `/mine` · T chỉ bài của A; W A gọi `/mine/{id của B}` · T 403 |
| AT-16 | FR-016 | G bài `REJECTED` có lý do · W `/mine` · T `reviewReason` có; bài `PENDING` · T `reviewReason` null |
| AT-17 | FR-006, SC-003 | W mọi endpoint đọc · T JSON không có khoá `email`, `passwordHash`, `roles` (duyệt cây JSON) |
| AT-18 | FR-017 | W bình luận bài `PENDING` · T 404; bài `APPROVED` · T 201 `depth=0` |
| AT-19 | FR-019 | G 30 bình luận trong 60 phút · W bình luận thứ 31 · T 429; đồng hồ +61 phút · T 201 |
| AT-20 | FR-021, E5 | W trả lời `parentId` của bài `PENDING`; `parentId` không tồn tại · T 404 `COMMENT_NOT_FOUND`, body không chứa nội dung cha |
| AT-21 | FR-022, E4 | G chuỗi cấp 0→1→2→3 · W trả lời bình luận cấp 3 · T `depth=3`, `parentId` = cha của bình luận cấp 3 |
| AT-22 | FR-018, E6 | G cha có 2 trả lời, cha bị tác giả xoá · W đọc cây · T cha `status=DELETED`, `content=null`, `author=null`; 2 trả lời đủ nội dung |
| AT-23 | FR-018 | G bình luận lá bị `MODERATOR` ẩn · W đọc cây · T không có trong response |
| AT-24 | FR-018, NFR-P02 | G 50 bình luận sâu 3 cấp · T 1 câu SQL đọc bình luận |
| AT-25 | FR-024 | W người khác sửa · T 403; tác giả sửa sau 16 phút (đồng hồ giả) · T 409 `COMMENT_EDIT_WINDOW_EXPIRED`; trong 10 phút · T 200, `editedAt` có |
| AT-26 | FR-026, FR-027 | W tác giả xoá · T 204, dòng còn trong DB `status=DELETED`, `moderation_reason=AUTHOR_DELETED`; `commentCount` giảm 1; xoá lần 2 · T 204 |
| AT-27 | FR-028, FR-029 | W thích 2 lần · T 200, `likeCount` +1 một lần; bỏ thích 2 lần · T 200, `likeCount` về cũ |
| AT-28 | FR-031, E7 | W 10 request thích song song cùng người · T đúng 1 dòng `reactions` |
| AT-29 | FR-028 | W thích bài `PENDING` · T 404 |
| AT-30 | FR-032, FR-034 | W `reason:"OTHER"` không `detail`; `reason:"FAKE"` · T 422 |
| AT-31 | FR-036, FR-038, US-4 | G 3 người báo cáo cùng bình luận · T 1 hồ sơ `PENDING`, `report_count=3`, `reasons` 3 phần tử; response mỗi lần chỉ `{received:true}` |
| AT-32 | FR-036 | W báo cáo lần 2 cùng người · T 409 `DUPLICATE_REPORT`, `report_count` không đổi |
| AT-33 | FR-033, FR-035 | W báo cáo bình luận `HIDDEN`; bài `PENDING` · T 404 `TARGET_NOT_FOUND`; báo cáo bài của mình · T 422 `SELF_REPORT`; báo cáo thứ 21 trong ngày · T 429 |
| AT-34 | FR-037 | G 10 người báo cáo một bài · T bài vẫn `APPROVED`, `auto_hidden=false` |
| AT-35 | FR-039 | G 3 bài `PENDING` tạo lệch giờ · W hàng đợi · T cũ nhất trước; không có bài trạng thái khác |
| AT-36 | FR-041, FR-044, SC-002 | W `MODERATOR` duyệt · T `APPROVED`, `reviewed_by` = người duyệt, `published_at` có, bài có ở danh sách công khai, `audit_logs COMMUNITY_POST_APPROVE` |
| AT-37 | FR-041 | G `MODERATOR` là tác giả · W duyệt bài của mình · T 403 `SELF_MODERATION`, bài vẫn `PENDING` |
| AT-38 | FR-042, E1 | W hai `MODERATOR` duyệt/từ chối song song cùng bài · T một 200, một 409; `status` khớp `reviewed_by` |
| AT-39 | FR-043 | W từ chối lý do rỗng · T 422; lý do hợp lệ · T `REJECTED`, tác giả thấy lý do ở `/mine`; duyệt bài `REJECTED` · T 409 |
| AT-40 | FR-047 | W `MODERATOR` xem hồ sơ AT-31 · T `reports` 3 phần tử có `reporter.displayName`; W tác giả bình luận gọi cùng endpoint · T 403 |
| AT-41 | FR-048 | W `decision:"BAN_USER"`; `ACTION` trên bình luận thiếu `commentAction`; `ACTION` trên bài có `commentAction` · T 422 cả ba, không đổi gì |
| AT-42 | FR-050, US-4 | W `ACTION` + `HIDE` hồ sơ AT-31 · T bình luận `HIDDEN`, `moderation_reason` = `note`, hồ sơ `ACTIONED`, trả lời con vẫn `VISIBLE`, `audit_logs COMMUNITY_REPORT_ACTION` |
| AT-43 | FR-050 | W `ACTION` hồ sơ bài · T bài `HIDDEN`, rời danh sách công khai, tác giả thấy `HIDDEN` + lý do ở `/mine` |
| AT-44 | FR-050 | W `REJECT` · T hồ sơ `REJECTED`, nội dung không đổi; xử lý lần 2 · T 409 `REPORT_ALREADY_HANDLED`; người mới báo cáo cùng nội dung · T hồ sơ `PENDING` mới (E9) |
| AT-45 | FR-049, FR-054 | W `MODERATOR` xử lý hồ sơ/bình luận của chính mình · T 403 `SELF_MODERATION` |
| AT-46 | FR-052, FR-053 | W `HIDE` thiếu lý do · T 422; `HIDE` 2 lần · T 200, `audit_logs` chỉ 1 dòng; `RESTORE` · T `VISIBLE`; `RESTORE` bình luận tác giả tự xoá · T 409 `COMMENT_NOT_RESTORABLE` |
| AT-47 | FR-056 | G đã thích, chưa báo cáo · W `viewer-state` · T `{liked:true, reported:false}`; bài `PENDING` · T 404 |
| AT-48 | FR-005 (FE) | Vitest: bài và bình luận chứa `<img src=x onerror=alert(1)>` hiển thị đúng chuỗi đó dạng text, không tạo thẻ `img` |
| AT-49 | FR-057…FR-063 (FE) | Vitest: `GUEST` không thấy tab "Bài viết của tôi", bấm "Viết bài" → `/login`; nút Sửa bình luận ẩn sau 15 phút; Từ chối không gửi khi lý do rỗng; Xử lý bình luận bắt buộc chọn Ẩn/Xoá; 409 hiện thông báo và tải lại hàng đợi |
| AT-50 | `AC-03` | ArchUnit: `community..` không phụ thuộc `auth..` ngoài `auth.api..` |
| AT-51 | FR-063 | G bình luận lá bị ẩn · W `MODERATOR` gọi `/moderation/posts/{id}/comments` · T có bình luận đó kèm `content`, `moderationReason`; W `LEARNER` gọi · T 403 |

---

## 15 · Gói việc cho Codex

[`plan.md`](plan.md) · [`tasks.md`](tasks.md)

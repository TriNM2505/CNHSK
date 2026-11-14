# Feature Specification: Quản trị đề thi, tiên quyết chủ đề và cài đặt hệ thống

**Feature:** `023-content-admin-settings`
**Feature tree:** 6.6 Quản trị nội dung · 3.2 Cây chủ đề có cổng kiểm tra (phần quản trị + cổng) · **Module:** `learning` (`learning.examadmin`, `learning.vocab`) + `shared` (`shared.settings`)
**Use case:** UC-112, UC-116, UC-137 (use-cases-08) · UC-045, UC-046 (use-cases-04) · **Actor:** `CONTENT_EDITOR`, `ADMIN`, `LEARNER`
**Màn hình:** SCR-065 Admin Exam List · SCR-066 Create / Edit Exam · SCR-067 Topic Prerequisites · SCR-078 System Settings (+ hiển thị khoá ở SCR-011/012 của 003)
**Scope:** V2 · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — leader duyệt 2026-11-14, được code theo `tasks.md` |
| **Phụ thuộc** | Spec 012 (`exams`, `exam_questions`, `QuestionItemFactory`) · 010 (`/api/admin/questions`, trạng thái câu) · 003 (danh sách/chi tiết chủ đề, `BUS-05`) · 001 (`AuditService`, role) · 013/014/015/019 (khoá cấu hình đang đọc từ yaml) |
| **Quyết định chung** | RFC đợt hoàn thiện H13 (⚿K7 `SettingsApi`) · plan `261113-1629` phase 6 · leader chốt 2026-11-14 (§9) |
| **Người code** | Xuân Huy |

> **Người code đọc:** `AGENTS.md` → spec 012 §0 → spec 003 FR-014…FR-018 → file này → `contracts/openapi-content-admin.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Đề thi: danh sách, tạo/sửa nháp, công bố có kiểm, lưu trữ, nhân bản (SCR-065/066) | Soạn câu hỏi — vẫn ở SCR-059 (010) |
| Tiên quyết **chủ đề** (`topics.prereq_ids`) có chống vòng (SCR-067) + **cổng khoá chủ đề** cho người học (`BUS-05`, Q1) | Tiên quyết **KID** của 010 (nhập qua 002) |
| `shared.app_settings` + `SettingsApi` (⚿K7) + màn 078 cho `ADMIN` | Khoá kỹ thuật (model AI, timeout, cron, trọng số mastery, ngưỡng xếp lớp) và mọi secret |
| Chuyển chỗ đọc khoá nghiệp vụ ở 013/014/015/019 sang `SettingsApi`; 021/022 đọc qua `SettingsApi` khi code | Sửa đề đã công bố (Q3) |

### 0.1 · Migration V10 (leader cấp 2026-11-14 — sau V9 của 021)

```sql
CREATE TABLE shared.app_settings (
  key        VARCHAR(100) PRIMARY KEY,
  value      TEXT         NOT NULL,
  updated_by BIGINT       NOT NULL REFERENCES auth.users(id),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Không có dòng = dùng mặc định (giá trị `application.yaml`). "Về mặc định" = xoá dòng. `database.md` AC-04 tăng thêm một bảng (T-01 cập nhật).
**V10 phải merge sau V9 (021/T-01)** — Flyway cấm chạy lệch thứ tự; nếu 023/T-01 xong trước, chờ V9 lên `main` rồi mới merge.

### 0.2 · Danh mục khoá `SettingKey` (Q2 — cố định trong code, không thêm khoá tự do)

| Khoá | Kiểu | Mặc định | Khoảng | Đang đọc ở |
|---|---|---|---|---|
| `billing.free-monthly-limit` | int | 10 | 0–1 000 | 013 |
| `assistant.daily-limit` | int | 200 | 0–10 000 | 015 `AssistantDailyLimit` |
| `ai.monthly-token-budget` | int | 2 000 000 | 0–100 000 000 | 015 |
| `library.guest-lookups-per-hour` | int | 60 | 1–10 000 | 014 |
| `library.authenticated-lookups-per-hour` | int | 600 | 1–10 000 | 014 |
| `library.max-notes` | int | 1 000 | 1–10 000 | 014 |
| `library.max-decks` | int | 50 | 1–500 | 014 |
| `library.max-cards` | int | 500 | 1–5 000 | 014 |
| `library.flashcard-reviews-per-hour` | int | 500 | 1–10 000 | 014 |
| `quiz.question-count` | int | 10 | 5–30 | 019 |
| `quiz.min-seconds-per-question` | int | 2 | 0–60 | 019 |
| `grading.claim-hours` | int | 48 | 1–168 | 022 |
| `grading.grading-hours` | int | 24 | 1–168 | 022 |
| `grading.teacher-payout-credits` | int | 0 | 0–10 | 022 |
| `grading.min-hanzi` · `grading.max-hanzi` | int | 50 · 500 | 1–1 000 · 10–2 000, `min < max` | 022 |
| `grading.max-active-claims` | int | 3 | 1–20 | 022 |
| `contest.submit-grace-seconds` | int | 5 | 0–60 | 021 |

Mặc định lấy từ `application.yaml` (khoá `cnhsk.<key>`), không chép số vào code. 024 thêm nhóm `ai-generate.*` khi viết spec 024.
**Cấm:** khoá nào có tên chứa `key`, `secret`, `password`, `pepper`, `token` (trừ `ai.monthly-token-budget` là con số) — test khẳng định (FR-003).

---

## 1 · Context & Goal

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | `CONTENT_EDITOR` dựng một đề HSK 3 đủ phần từ ngân hàng câu, công bố, người học thấy ở Thi thử | 1 đề |
| **SC-002** | `ADMIN` đổi giới hạn trợ lý/ngày ở 078, hệ thống áp dụng trong ≤ 30 giây, không khởi động lại | ✔ |
| **SC-003** | Cài `SettingsApi` không đổi hành vi: test cũ 013/014/015/019 xanh nguyên | 0 test sửa kỳ vọng |
| **SC-004** | Không tạo được vòng tiên quyết chủ đề | 0 |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | Là `CONTENT_EDITOR`, tôi xem danh sách đề, tạo đề nháp: mã, tên, cấp, các phần thi (tên, dạng, phút), chọn câu `APPROVED` từ ngân hàng vào từng phần, sắp thứ tự, đặt điểm |
| US-2 | Là `CONTENT_EDITOR`, tôi bấm "Công bố" và thấy rõ lỗi chặn (phần trống, câu chưa duyệt, thiếu audio phần nghe, tổng điểm lệch); đề đã công bố chỉ lưu trữ hoặc nhân bản |
| US-3 | Là `CONTENT_EDITOR`, tôi đặt chủ đề tiên quyết cho một chủ đề; hệ thống chặn vòng và cảnh báo tiên quyết cấp cao hơn |
| US-4 | Là người học, tôi thấy chủ đề "Đang khoá — cần đạt 90% bài kiểm tra chủ đề X" và không vào học/luyện/kiểm tra được cho tới khi đạt |
| US-5 | Là `ADMIN`, tôi xem các khoá được phép đổi theo nhóm, giá trị hiện tại, mặc định, khoảng, lịch sử; sửa có xác nhận hoặc đưa về mặc định |

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Hai biên tập viên sửa cùng đề nháp | Người lưu sau → **409** `EDIT_CONFLICT` (so `etag`) |
| E2 | Câu trong đề nháp bị lưu trữ ở 010 trước khi công bố | Công bố → **422** `EXAM_PUBLISH_INVALID`, `details` chỉ câu đó |
| E3 | Lưu trữ đề đang có người làm Thi thử | Được; lượt đang làm chạy tiếp bằng câu đã chụp (`served_items`) |
| E4 | Lưu trữ đề đang gắn cuộc thi `PUBLISHED` chưa kết thúc (021) | **409** `EXAM_IN_USE` |
| E5 | Chủ đề tiên quyết bị đặt cho chính nó / tạo vòng A→B→A | **422** `PREREQUISITE_CYCLE`, `details.path` |
| E6 | `ADMIN` đặt `grading.min-hanzi` ≥ `grading.max-hanzi` | **422** `SETTING_OUT_OF_RANGE` |
| E7 | Người học đã học dở chủ đề rồi biên tập viên mới thêm tiên quyết | Chủ đề chuyển sang khoá; tiến độ cũ giữ nguyên, hiện lại khi mở khoá |

---

## 3 · Functional Requirements

### Nhóm 1 — Cài đặt hệ thống ⚿K7 (FR-001…FR-008) · UC-116 · SCR-078

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `com.cnhsk.shared.settings.SettingKey` (enum) SHALL khai báo đúng các khoá §0.2: `key`, `group` (Thanh toán, Trợ lý & AI, Thư viện, Quiz, Chấm bài, Cuộc thi), `description` tiếng Việt, kiểu `INT`, `min`, `max`, đường dẫn yaml của mặc định |
| **FR-002** | `SettingsApi.getInt(SettingKey)` SHALL trả giá trị dòng `app_settings` nếu có, không thì mặc định yaml; cache trong bộ nhớ 30 giây; ghi/xoá xoá cache ngay trên instance đó (đa instance chấp nhận trễ ≤ 30 s); lỗi DB → dùng mặc định + log `WARN` một lần mỗi phút (không làm sập nghiệp vụ) |
| **FR-003** | Test SHALL khẳng định không `SettingKey` nào có tên chứa `key`/`secret`/`password`/`pepper`, và chỉ `ai.monthly-token-budget` chứa `token`; thiếu mặc định yaml cho một khoá → không khởi động |
| **FR-004** | `GET /api/admin/settings` (`ADMIN`) SHALL trả theo nhóm `{key, group, description, type, currentValue, defaultValue, min, max, overridden, updatedBy{id, displayName}?, updatedAt?}` |
| **FR-005** | `PUT /api/admin/settings/{key}` `{value: int, reason* (1–500)}` SHALL: khoá ngoài danh mục → **404** `SETTING_NOT_FOUND`; sai kiểu/ngoài khoảng/vi phạm ràng buộc chéo (`grading.min-hanzi < grading.max-hanzi`) → **422** `SETTING_OUT_OF_RANGE`; upsert dòng + `audit_logs` `SETTING_CHANGE` `{key, oldValue, newValue, reason}` trong một transaction; xoá cache; trả **200** mục FR-004 |
| **FR-006** | `DELETE /api/admin/settings/{key}` `{reason*}` SHALL xoá dòng (về mặc định), ghi `SETTING_RESET`, trả **200**; không có dòng → 200 (idempotent) |
| **FR-007** | `GET /api/admin/settings/{key}/history?page&size` SHALL đọc `audit_logs` của khoá đó (mới nhất trước) `{oldValue, newValue, reason, actor{id, displayName}, at}` |
| **FR-008** | SCR-078 `/admin/settings` (`['ADMIN']`, layout `admin`): bảng theo nhóm, ô sửa số có khoảng, xác nhận trong trang kèm lý do, "Về mặc định", lịch sử mở tại chỗ; cuối trang "Token AI đã dùng tháng này" (chỉ đọc, từ 015) |

### Nhóm 2 — Chuyển chỗ đọc (FR-009…FR-010)

| Mã | Yêu cầu |
|---|---|
| **FR-009** | Mọi chỗ đang đọc một khoá §0.2 từ yaml (013 hạn mức miễn phí; 014 sáu giới hạn thư viện; 015 giới hạn ngày + ngân sách token; 019 hai khoá quiz) SHALL đọc qua `SettingsApi` **tại thời điểm dùng** (không giữ trong field lúc khởi động). Mặc định giữ nguyên → hành vi không đổi; test cũ không sửa kỳ vọng (SC-003) |
| **FR-010** | 021 và 022 đọc khoá của mình qua `SettingsApi` nếu T-01 của 023 đã merge lúc code; nếu chưa, đọc yaml và 023/T-04 chuyển sau |

### Nhóm 3 — Đề thi (FR-011…FR-019) · UC-112 · SCR-065, SCR-066

| Mã | Yêu cầu |
|---|---|
| **FR-011** | `GET /api/admin/exams?status&hsk&q&page&size` (`CONTENT_EDITOR`) SHALL trả `{id, code, title, hskLevel, status, publishedAt, questionCount, inUse}` sắp `created_at` giảm; `q` tìm mã/tên |
| **FR-012** | `POST /api/admin/exams` `ExamWrite {code* (1–50, `[A-Z0-9-]`), title* (1–200), hskLevel* (1–6, CHECK `exams`), totalScore* (1–1 000), sections* [{name* (1–100), type* ∈ LISTENING·READING·WRITING, durationMin (0–180)}] (1–5), questions [{questionId, sectionIndex, points (1–100)}] (≤ 300)}` SHALL tạo `DRAFT` (`created_by`), ghi `exam_questions` theo thứ tự mảng (`order_index`); mã trùng → **409** `EXAM_CODE_TAKEN`; trả **201** `ExamDetail` |
| **FR-013** | `GET /api/admin/exams/{id}` SHALL trả `ExamDetail {…ExamWrite, id, status, publishedAt, etag, questions:[{questionId, sectionIndex, orderIndex, points, code, qtype, status, kid, hasAudio}], publicationErrors[]}`; `etag` = SHA-256 của trường đề + danh sách câu; `publicationErrors` = kết quả FR-015 tính sẵn |
| **FR-014** | `PUT /api/admin/exams/{id}` `ExamWrite + etag` SHALL: không `DRAFT` → **409** `EXAM_NOT_EDITABLE` (Q3); `etag` khác hiện tại → **409** `EDIT_CONFLICT`; thay toàn bộ phần + câu trong một transaction; câu không tồn tại → **404** `QUESTION_NOT_FOUND` (010) |
| **FR-015** | `POST /api/admin/exams/{id}/publish` `{etag}` SHALL kiểm (mọi lỗi gom trong `details`, **422** `EXAM_PUBLISH_INVALID`): mỗi phần ≥ 1 câu; mọi câu `APPROVED`, có KID, dựng được bằng `QuestionItemFactory` (dạng hỗ trợ); câu phần `LISTENING` có audio; tổng `points` = `totalScore`. Đạt → `PUBLISHED`, `published_at = now()`; ghi `audit_logs` `EXAM_PUBLISH` |
| **FR-016** | `POST /api/admin/exams/{id}/archive` SHALL: đã `ARCHIVED` → 200; `ExamUsageGuard` báo đang dùng → **409** `EXAM_IN_USE`; đặt `ARCHIVED`, `EXAM_ARCHIVE`. Lượt đang làm không bị ảnh hưởng (E3) |
| **FR-017** | `POST /api/admin/exams/{id}/clone` `{code*, title?}` SHALL tạo `DRAFT` mới chép phần + câu + điểm (mọi trạng thái nguồn); `EXAM_CLONE` |
| **FR-018** | `learning.api.ExamUsageGuard` (interface) `boolean isInUse(long examId)`; `learning` gọi mọi bean cài qua `ObjectProvider` (không có bean → không chặn). 021 cài: đề gắn cuộc thi `PUBLISHED` có `ends_at > now` → `true` (module `community` cài interface của `learning.api` — đúng chiều phụ thuộc AC-03). `inUse` ở FR-011 dùng cùng guard |
| **FR-019** | SCR-065 `/content/exams` + SCR-066 `/content/exams/new`, `/content/exams/:id` (`['CONTENT_EDITOR']`, `admin`): bảng đề + lọc; trình sửa phần thi, ô chọn câu mở danh sách `GET /api/admin/questions?status=APPROVED` (010) lọc KID/dạng, kéo thả thứ tự, ô điểm, tổng điểm tự cộng, khối "Lỗi chặn công bố" (FR-013), nút Lưu nháp/Công bố/Lưu trữ/Nhân bản (xác nhận trong trang); đề không `DRAFT` mở chỉ đọc; 409 `EDIT_CONFLICT` → "Đề vừa được người khác sửa, tải lại" |

### Nhóm 4 — Tiên quyết chủ đề + cổng (FR-020…FR-026) · UC-045, UC-046 · SCR-067

| Mã | Yêu cầu |
|---|---|
| **FR-020** | `GET /api/admin/topics?hsk&q&page&size` (`CONTENT_EDITOR`) SHALL trả `{id, name, hskLevel, prereqs:[{id, name, hskLevel}], isRoot}` |
| **FR-021** | `PUT /api/admin/topics/{id}/prerequisites` `{prereqIds (≤ 10, không trùng)}` SHALL: chủ đề/tiên quyết không tồn tại → **404** `TOPIC_NOT_FOUND`; chứa chính nó hoặc tạo vòng (DFS trên toàn đồ thị chủ đề sau khi đổi) → **422** `PREREQUISITE_CYCLE` `details.path=[id…]`; sau khi đổi không còn chủ đề gốc nào → **422** `PREREQUISITE_NO_ROOT`; lưu `topics.prereq_ids` + `audit_logs` `TOPIC_PREREQ_UPDATE {before, after}` trong một transaction (khoá tư vấn `topic-prereq` để hai người sửa không cùng tạo vòng); trả **200** mục FR-020 + `warnings` (tiên quyết có `hskLevel` cao hơn chủ đề) |
| **FR-022** | Chủ đề **mở** với người học khi mọi chủ đề trong `prereq_ids` có trạng thái `COMPLETED` của người đó (003 FR-016: lượt `TOPIC_TEST` đạt ≥ 90% — `BUS-05`). `prereq_ids` rỗng → luôn mở |
| **FR-023** | 003 FR-014/FR-018 SHALL thêm `status = LOCKED` (ưu tiên trên mọi trạng thái khác) và `lockedBy:[{id, name}]` (các tiên quyết chưa `COMPLETED`); tính bằng **một** truy vấn cho cả trang (không N+1) |
| **FR-024** | `POST /api/topics/{id}/study-sessions`, `/practice-sessions`, `/tests` SHALL trả **422** `TOPIC_LOCKED` `details.lockedBy` khi chủ đề khoá; tiếp tục lượt đang mở (resume) vẫn được (E7) |
| **FR-025** | Hợp đồng 003 (`openapi-practice-vocab.yaml`) SHALL thêm `LOCKED`, `lockedBy`, mã `TOPIC_LOCKED` trong **cùng PR** với FR-023/FR-024 và sửa các test đọc file đó (5 test `learning.practice.*`) — không để `main` đỏ |
| **FR-026** | SCR-067 `/content/topics/prerequisites` (`['CONTENT_EDITOR']`, `admin`): chọn chủ đề, chọn tiên quyết nhiều (tìm theo tên/cấp), hiển thị lỗi vòng kèm đường đi, cảnh báo cấp; ghi chú cố định "Đây là tiên quyết **chủ đề** (cổng 90%), khác tiên quyết KID của cây tri thức". SCR-011/012 (003): thẻ chủ đề khoá có biểu tượng + chữ "Đang khoá", lý do "Cần đạt 90% bài kiểm tra: …" bấm được sang chủ đề tiên quyết; nút học/luyện/kiểm tra ẩn |

---

## 4 · Data Model

Một bảng mới `shared.app_settings` (V10, §0.1). Dùng lại `learning.exams`, `exam_questions`, `topics.prereq_ids`, `shared.audit_logs`.
`exams.sections` ghi `[{index, name, type, duration_min}]` (đúng ghi chú V1).

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `SETTING_NOT_FOUND` *(mới)* | 404 | Khoá ngoài danh mục | FR-005…FR-007 |
| `SETTING_OUT_OF_RANGE` *(mới)* | 422 | Sai kiểu/khoảng/ràng buộc chéo | FR-005 |
| `EXAM_CODE_TAKEN` *(mới)* | 409 | Mã đề trùng | FR-012, FR-017 |
| `EXAM_NOT_EDITABLE` *(mới)* | 409 | Sửa/công bố đề không `DRAFT` | FR-014, FR-015 |
| `EDIT_CONFLICT` *(mới)* | 409 | `etag` cũ | FR-014, FR-015 |
| `EXAM_PUBLISH_INVALID` *(mới)* | 422 | Không qua kiểm công bố, `details` liệt kê | FR-015 |
| `EXAM_IN_USE` *(mới)* | 409 | Lưu trữ đề đang gắn cuộc thi chưa kết thúc | FR-016 |
| `EXAM_NOT_FOUND` *(012)* | 404 | Đề không có | FR-013…FR-017 |
| `QUESTION_NOT_FOUND` *(010)* | 404 | Câu trong body không có | FR-012, FR-014 |
| `PREREQUISITE_CYCLE` *(mới)* | 422 | Tạo vòng/tự trỏ | FR-021 |
| `PREREQUISITE_NO_ROOT` *(mới)* | 422 | Không còn chủ đề gốc | FR-021 |
| `TOPIC_NOT_FOUND` *(003)* | 404 | Chủ đề không có | FR-021 |
| `TOPIC_LOCKED` *(mới)* | 422 | Học/luyện/kiểm tra chủ đề đang khoá | FR-024 |

Mã mới thêm ngay trước `INTERNAL_ERROR` theo thứ tự trên `main` lúc làm; cập nhật `GlobalExceptionHandlerTest`.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-03** | `shared.settings` không phụ thuộc module nghiệp vụ; module nghiệp vụ gọi `SettingsApi`. `ExamUsageGuard` ở `learning.api`, `community` cài |
| **AC-05** | Một migration V10 (§0.1) |
| Secret | Không bao giờ vào `app_settings` (FR-003); `.env` giữ nguyên |
| Bảo mật | `SecurityConfig`: `/api/admin/settings/**` → `ADMIN`; `/api/admin/exams/**`, `/api/admin/topics/**` → `CONTENT_EDITOR` |
| Engine 012 | Không sửa; lượt đã phát giữ câu đã chụp |

| Lớp | Trách nhiệm |
|---|---|
| `shared.settings.{SettingKey, SettingsApi, SettingsService, SettingsController}` | FR-001…FR-007 |
| `learning.examadmin.{ExamAdminService, ExamPublicationValidator, ExamAdminController}` + `learning.api.ExamUsageGuard` | FR-011…FR-018 |
| `learning.vocab.{TopicPrerequisiteService, TopicLockQuery}` + `TopicAdminController` | FR-020…FR-024 |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `SettingsApi.getInt` khi trúng cache | < 1 µs, không truy vấn DB |
| `NFR-P02` | Danh sách chủ đề có tính khoá, 50 chủ đề | Số truy vấn không tăng theo số chủ đề |
| `NFR-P03` | Kiểm vòng trên 2 000 chủ đề | < 100 ms |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đổi schema | Một bảng (V10) |
| Secret trong DB | Không (FR-003) |
| Đổi hành vi mặc định khi thêm `SettingsApi` | Không (SC-003) |

---

## 9 · Quyết định

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | 023 làm luôn cổng khoá chủ đề (`LOCKED`, `TOPIC_LOCKED`); chủ đề không đặt tiên quyết vẫn mở | ✅ Leader chốt 2026-11-14 |
| Q2 | 078 chỉ khoá nghiệp vụ §0.2; chuyển chỗ đọc 013/014/015/019 sang `SettingsApi`, 021/022 dùng thẳng | ✅ Leader chốt 2026-11-14 |
| Q3 | Đề `PUBLISHED` không sửa, chỉ lưu trữ/nhân bản | ✅ Leader chốt 2026-11-14 |
| Q4 | `app_settings` = V10 (021 lấy V9) | ✅ Leader chốt 2026-11-14 |
| Q5 | Chống sửa đè bằng `etag` tính từ nội dung, không thêm cột | ✅ Chốt |
| Q6 | Đề đang gắn cuộc thi qua `ExamUsageGuard` (không đọc bảng `community` từ `learning`) | ✅ Chốt |
| Q7 | SCR-058 (hàng chờ duyệt AI) thuộc 024, không thuộc 023 | ✅ Chốt (plan phase 6) |

---

## 10 · Móc nối

- 012: `/api/exams` chỉ trả `PUBLISHED` — đề nháp/cuộc thi (021 Q2) không lộ.
- 021: cài `ExamUsageGuard`; chọn đề `DRAFT`/`PUBLISHED` qua FR-011.
- 022: đọc `grading.*`; 024: thêm nhóm `ai-generate.*` vào `SettingKey`.
- 015: số token đã dùng tháng (chỉ đọc) cho FR-008.

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Nhiều instance thấy giá trị mới chậm tới 30 s | Quy mô đồ án một instance; ghi rõ ở màn 078 |
| Thêm tiên quyết làm khoá chủ đề người học đang học dở (E7) | Đúng nghĩa cổng; tiến độ không mất |
| `ADMIN` đặt giá trị hợp lệ nhưng bất lợi (vd hạn mức 0) | Khoảng cứng + lịch sử + "Về mặc định" |

---

## 12 · Màn hình

| SCR | Route | Guard · layout | API |
|---|---|---|---|
| SCR-065 | `/content/exams` | `['CONTENT_EDITOR']` · `admin` | FR-011, FR-016, FR-017 |
| SCR-066 | `/content/exams/new`, `/content/exams/:id` | `['CONTENT_EDITOR']` · `admin` | FR-012…FR-017, `/api/admin/questions` |
| SCR-067 | `/content/topics/prerequisites` | `['CONTENT_EDITOR']` · `admin` | FR-020, FR-021 |
| SCR-078 | `/admin/settings` | `['ADMIN']` · `admin` | FR-004…FR-007 |
| SCR-011/012 (003) | có sẵn | có sẵn | FR-023, FR-024 |

Màn quản trị dùng layout `admin` → không cần `SCREEN_NAMES`. Menu quản trị: "Đề thi", "Tiên quyết chủ đề" (`CONTENT_EDITOR`), "Cài đặt hệ thống" (`ADMIN`).

---

## 13 · API Contract

[`contracts/openapi-content-admin.yaml`](contracts/openapi-content-admin.yaml) · thay đổi 003 (FR-025) sửa thẳng `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml` trong task T-07.

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/admin/settings` | 200 `[SettingItem]` | 401, 403 |
| `PUT` · `DELETE /api/admin/settings/{key}` | 200 `SettingItem` | 401, 403, 404, 422 |
| `GET /api/admin/settings/{key}/history` | 200 `SettingHistoryPage` | 401, 403, 404, 422 |
| `GET` · `POST /api/admin/exams` | 200 `AdminExamPage` · 201 `ExamDetail` | 401, 403, 404, 409, 422 |
| `GET` · `PUT /api/admin/exams/{id}` | 200 `ExamDetail` | 401, 403, 404, 409, 422 |
| `POST /api/admin/exams/{id}/publish` | 200 `ExamDetail` | 401, 403, 404, 409, 422 |
| `POST /api/admin/exams/{id}/archive` | 200 `ExamDetail` | 401, 403, 404, 409 |
| `POST /api/admin/exams/{id}/clone` | 201 `ExamDetail` | 401, 403, 404, 409, 422 |
| `GET /api/admin/topics` | 200 `AdminTopicPage` | 401, 403, 422 |
| `PUT /api/admin/topics/{id}/prerequisites` | 200 `AdminTopic` | 401, 403, 404, 422 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | §0.1 | Flyway V10 tạo bảng; nâng cấp V9 → V10 giữ dữ liệu |
| AT-02 | FR-001, FR-003 | Mọi `SettingKey` có mặc định yaml; không tên chứa `key/secret/password/pepper`; chỉ `ai.monthly-token-budget` chứa `token` |
| AT-03 | FR-002 | Không dòng → mặc định yaml; ghi → đọc lại ngay giá trị mới; DB lỗi → mặc định + một `WARN` |
| AT-04 | FR-005 | Khoá lạ · T 404; `assistant.daily-limit = -1` · T 422; `min-hanzi=600` khi `max-hanzi=500` · T 422; hợp lệ · T 200 + `audit_logs SETTING_CHANGE` |
| AT-05 | FR-006, FR-007 | Về mặc định 2 lần · T 200 cả hai; lịch sử có 2 dòng đúng thứ tự |
| AT-06 | FR-009, SC-002 | `assistant.daily-limit=1` qua API · T lượt hỏi thứ 2 trong ngày bị chặn, không khởi động lại |
| AT-07 | FR-009, SC-003 | Test cũ 013/014/015/019 chạy nguyên, không sửa kỳ vọng |
| AT-08 | Bảo mật | `CONTENT_EDITOR` gọi `/api/admin/settings` · T 403; `ADMIN` gọi `/api/admin/exams` · T 403 |
| AT-09 | FR-012, FR-013 | Tạo đề 3 phần 20 câu · T `DRAFT`, `exam_questions` đúng thứ tự, `publicationErrors` rỗng nếu hợp lệ; mã trùng · T 409 |
| AT-10 | FR-014, E1 | Hai người cùng `etag` lưu · T một 200, một 409 `EDIT_CONFLICT`; sửa đề `PUBLISHED` · T 409 `EXAM_NOT_EDITABLE` |
| AT-11 | FR-015, E2 | Phần trống; câu `DRAFT`; câu phần nghe không audio; tổng điểm lệch · T 422 `EXAM_PUBLISH_INVALID`, `details` đủ 4 lỗi |
| AT-12 | FR-015 | Công bố hợp lệ · T `PUBLISHED`, có ở `/api/exams` (012), bắt đầu thi thử được |
| AT-13 | FR-016, FR-018, E3, E4 | Guard trả `true` · T 409 `EXAM_IN_USE`; không guard · T `ARCHIVED`, lượt đang làm nộp được, đề rời `/api/exams` |
| AT-14 | FR-017 | Nhân bản đề `ARCHIVED` · T `DRAFT` mới đủ phần/câu/điểm, mã mới |
| AT-15 | FR-021, SC-004, E5 | Tự trỏ; A→B rồi B→A; vòng 3 bước · T 422 `PREREQUISITE_CYCLE` có `path`; hai người song song tạo vòng · T một bị chặn |
| AT-16 | FR-021 | Tiên quyết cấp cao hơn · T 200 kèm `warnings`; `audit_logs` có before/after |
| AT-17 | FR-022…FR-024, E7 | B tiên quyết A: chưa đạt A · T B `LOCKED`, `lockedBy=[A]`, bắt đầu học B · T 422 `TOPIC_LOCKED`; đạt A 90% · T B mở; lượt B đang dở trước khi thêm tiên quyết · T resume được |
| AT-18 | FR-023, NFR-P02 | 50 chủ đề · T số truy vấn bằng nhau giữa trang 5 và 50 |
| AT-19 | FR-025 | Test hợp đồng 003 và 5 test `learning.practice.*` đọc YAML xanh sau khi thêm `LOCKED` |
| AT-20 | FR-008, FR-019, FR-026 (FE) | Vitest: 078 chặn số ngoài khoảng trước khi gửi, xác nhận cần lý do; 066 tổng điểm tự cộng, đề không `DRAFT` chỉ đọc, 409 hiện thông báo tải lại; 067 hiện đường vòng; SCR-011 thẻ khoá có chữ + biểu tượng, nút học ẩn |
| AT-21 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-content-admin.yaml` (hai chiều path + method + status) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md) · [`questions.md`](questions.md)

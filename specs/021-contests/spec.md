# Feature Specification: Cuộc thi theo khung giờ

**Feature:** `021-contests`
**Feature tree:** 5.8 Cuộc thi có thưởng · 5.8.1 Chống gian lận · **Module:** `community` (package `community.contest`) + `learning` (engine thi, `learning.api.ContestAttemptApi`)
**Use case:** UC-090, UC-091, UC-092 (use-cases-06) · UC-113 (use-cases-08) · **Actor:** `GUEST`, `LEARNER`, `CONTENT_EDITOR`
**Màn hình:** SCR-048 Contest List · SCR-049 Contest Detail · SCR-050 Contest Attempt · SCR-051 Contest Results · SCR-070 Admin Contest List · SCR-071 Create / Edit Contest
**Scope:** V2 · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — leader duyệt 2026-11-14, được code theo `tasks.md` |
| **Phụ thuộc** | Spec 012 (engine thi thử `MockExamHandler`, `QuestionItemFactory`, đề `exams`) · Spec 003 (engine `PracticeService`) · Spec 001 (`AuthApi`, `AuditService`) · Spec 015 (`ActiveTestApi`) · 017/T-01 (`AuthApi.publicProfiles`) |
| **Quyết định chung** | RFC đợt hoàn thiện H9, H14 · plan `261113-1629` phase 5 · leader chốt 2026-11-13/14 (§9) |
| **Người code** | Tường |

> **Người code đọc:** `AGENTS.md` → spec 012 §0 (engine thi) → file này → `contracts/openapi-contests.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Danh sách, chi tiết cuộc thi cho khách và người học (UC-090) | Bước đăng ký/huỷ đăng ký riêng (UC-091) — **vào thi = đăng ký** (Q1) |
| Vào thi trong khung giờ, làm bài, nộp, một lượt/người (UC-092) | Trao thưởng trong hệ thống (điểm, tiền) — ban tổ chức liên hệ ngoài hệ thống (Q3) |
| Ghi nhận chuyển tab để ban tổ chức xem, không tự loại (BR-092-9) | Xem lại đáp án từng câu sau cuộc thi (SCR-051 chưa định nghĩa) |
| `CONTENT_EDITOR` tạo/sửa/công bố/huỷ cuộc thi, xét lượt đáng ngờ, công bố xếp hạng, xem liên hệ người đạt giải (UC-113, SCR-070/071) | Job tự đóng cuộc thi — trạng thái lịch tính theo giờ (BR-090-4) |
| Một migration: index một lượt/người (V9) | Bảng mới |

### 0.1 · Dữ liệu dùng lại

| Đối tượng | Lưu thế nào |
|---|---|
| Cuộc thi | `community.contests` (V2): `name`, `description`, `exam_id`, `starts_at`, `ends_at`, `max_participants`, `prizes` JSONB, `status` `DRAFT`·`PUBLISHED`·`CANCELLED` (**không dùng `CLOSED`**, Q5), `ranking_published_at`, `created_by` |
| Lượt thi | `learning.attempts` `kind='CONTEST'`, `ref_type='CONTEST'`, `ref_id = contest_id`, `PracticeSource.CONTEST`; câu hỏi chụp từ đề `exam_id` lúc bắt đầu (như `MOCK_EXAM`) |
| Đăng ký | Chính là lượt thi (Q1); `max_participants` đếm lượt `CONTEST` của cuộc thi |
| Chuyển tab | `shared.audit_logs` `action='CONTEST_CHEAT_EVENT'` (ghi chú sẵn ở V2), không thêm cột |
| `prizes` | `[{"fromRank":1,"toRank":1,"description":"…"}]` — mô tả chữ, tối đa 10 mục, `description` ≤ 200 ký tự |

### 0.2 · Migration V9 (leader cấp 2026-11-14, Q4)

```sql
CREATE UNIQUE INDEX uq_contest_attempt ON learning.attempts(user_id, ref_id) WHERE kind = 'CONTEST';
```

Chặn lượt thứ hai kể cả khi hai request đến cùng lúc, kể cả khi lượt đầu đã bỏ dở/hết hạn (BR-092-7). Xếp hạng lọc bằng `ix_att_ref_rank` (V8, 017). `app_settings` của 023 chuyển sang **V10**.

### 0.3 · Đề thi của cuộc thi (Q2)

Đề gắn với cuộc thi để **`DRAFT`** — không hiện ở danh sách Thi thử (`/api/exams` chỉ lấy `PUBLISHED`), chỉ đường cuộc thi được mở đề. Đề `ARCHIVED` không dùng được. Sau khi công bố xếp hạng, `CONTENT_EDITOR` có thể công khai đề thành đề thi thử bằng luồng quản trị đề (012/023) — 021 không làm việc này.

### 0.4 · Thời gian

| Mốc | Luật |
|---|---|
| Trạng thái lịch | `UPCOMING` (`now < starts_at`) · `ONGOING` (`starts_at ≤ now < ends_at`) · `ENDED` (`now ≥ ends_at`) — tính từ `Clock`, không lưu (BR-090-4) |
| Vào thi | Chỉ khi `ONGOING` (BR-092-3/4) |
| Hạn nộp của một lượt | `deadline = min(lúc bắt đầu + thời lượng đề, ends_at)`; thời lượng đề = tổng `sections[].duration_min` (0 → chỉ theo `ends_at`). Người vào muộn có ít thời gian hơn — hiện cảnh báo trước khi vào |
| Ân hạn nộp | `cnhsk.contest.submit-grace-seconds = 5` (BR-092-10) |
| Hiển thị | Giờ Việt Nam `Asia/Ho_Chi_Minh` (BR-090-5) |

---

## 1 · Context & Goal

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Người học vào thi, nộp và xem hạng sau công bố trên dữ liệu demo | 1 cuộc thi trọn vòng |
| **SC-002** | Không người nào có 2 lượt trong một cuộc thi, kể cả 20 request song song | 0 |
| **SC-003** | Không response nào ngoài endpoint `CONTENT_EDITOR` có email; không đáp án trước công bố | 100% |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | Là khách/người học, tôi xem các cuộc thi sắp diễn ra, đang diễn ra, đã kết thúc với giờ Việt Nam và mô tả giải |
| US-2 | Là người học đã xác thực email, tôi bấm "Vào thi" khi cuộc thi đang diễn ra, làm bài có đồng hồ theo hạn server, nộp một lần |
| US-3 | Là người học, sau khi ban tổ chức công bố, tôi xem điểm, thời gian, hạng của mình và bảng xếp hạng |
| US-4 | Là `CONTENT_EDITOR`, tôi tạo cuộc thi từ một đề, công bố, sửa trước giờ thi, huỷ khi chưa ai thi |
| US-5 | Là `CONTENT_EDITOR`, sau giờ thi tôi xem lượt đáng ngờ (thời gian bất thường, chuyển tab), loại hoặc giữ, rồi công bố xếp hạng và xem email người đạt giải để liên hệ |

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Hai tab cùng bấm "Vào thi" | Lượt thứ hai nhận lại phiên đang mở (resume), không tạo lượt mới |
| E2 | Đã nộp hoặc lượt đã hết hạn, bấm "Vào thi" lần nữa | **409** `CONTEST_ALREADY_ATTEMPTED` |
| E3 | Mất mạng lúc hết giờ | Client tự nộp khi còn trong ân hạn; quá ân hạn → lượt `EXPIRED`, không vào bảng (§11 R2) |
| E4 | Cuộc thi đủ `max_participants` | Người chưa có lượt → **422** `CONTEST_FULL`; người đang có lượt vẫn resume |
| E5 | `CONTENT_EDITOR` là người học và thi cuộc thi mình tạo | **403** `FORBIDDEN` |
| E6 | Sửa giờ/đề sau khi cuộc thi đã bắt đầu | **409** `CONTEST_LOCKED` |
| E7 | Công bố xếp hạng trước `ends_at` | **409** `CONTEST_RANKING_TOO_EARLY` (`ck_contest_rank`) |
| E8 | Đề bị `ARCHIVED` giữa chừng | Lượt đã bắt đầu vẫn chạy bằng câu đã chụp; người mới vào → **422** `CONTEST_EXAM_INVALID` |

---

## 3 · Functional Requirements

### Nhóm 1 — Engine lượt thi ở `learning` (FR-001…FR-008) · ⚿K8

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL thêm `PracticeSource.CONTEST(AttemptKind.CONTEST, DEFERRED, ttl = null, allowSkip = true, maxScoreFromServed = false)` (thêm cuối enum, giữ thứ tự) và đồng bộ `PracticeApi`; `ref_type='CONTEST'` |
| **FR-002** | `learning.api.ContestAttemptApi.start(userId, contestId, examId, Instant deadline)` SHALL: khoá tư vấn `contest:start:{contestId}`; có lượt `IN_PROGRESS` của người đó → trả lại (`resumed=true`); có lượt khác trạng thái → `ContestAlreadyAttempted`; đề không tồn tại, `ARCHIVED` hoặc không có câu dùng được → `ContestExamInvalid`; tạo lượt bằng câu chụp từ đề như `ExamAttemptService` (bỏ kiểm `PUBLISHED`, Q2), `ttlOverride = deadline + ân hạn − now`; trả `{sessionId, items (ServedItemView, không đáp án), deadline, resumed}` |
| **FR-003** | `ContestAttemptApi.countAttempts(contestId)` SHALL đếm lượt `CONTEST` mọi trạng thái (dùng cho `max_participants`, đếm trong cùng khoá FR-002) |
| **FR-004** | `ContestAttemptApi.submit(userId, contestId)` SHALL gọi `PracticeService.finish` cho lượt `IN_PROGRESS` của người đó; quá `deadline + ân hạn` → `ContestSubmitTooLate`; đã nộp → `ContestAlreadySubmitted`. Trả `{submittedAt}` — **không** trả điểm, đáp án, đúng/sai |
| **FR-005** | `POST /api/practice/sessions/{id}/finish` và `/abandon` SHALL từ chối lượt nguồn `CONTEST` → **409** `CONTEST_SUBMIT_REQUIRED` (chỉ nộp qua FR-012). `GET /api/practice/sessions/{id}` và `/answers` dùng được bình thường; `DEFERRED` không trả đúng/sai |
| **FR-006** | `ContestAttemptApi.myAttempt(userId, contestId)` → `{status, score, maxScore, durationMs, submittedAt, suspicious}` hoặc rỗng. `ranking(contestId, page, size)` → hàng `SUBMITTED` có `suspicious=false`, sắp `score DESC, duration_ms ASC NULLS LAST, submitted_at ASC, user_id ASC`, `rank = ROW_NUMBER()` + `total`. `rankOf(userId, contestId)` cùng thứ tự. `participants(contestId)` (cho ban tổ chức) → mọi lượt `{userId, status, score, maxScore, durationMs, submittedAt, suspicious}` |
| **FR-007** | `ContestAttemptApi.setExcluded(contestId, userId, boolean excluded)` SHALL đặt `attempts.suspicious` của lượt `SUBMITTED` đó (ban tổ chức xét lại cờ tự động hoặc tự loại — BR-092-9 không tự loại khỏi bảng **sau khi công bố**: cờ chỉ là đề xuất, người xét quyết trước khi công bố) |
| **FR-008** | `ActiveTestApi` (`existsActiveTest`) SHALL tính cả `kind='CONTEST'` → trợ lý trả `ASSISTANT_BLOCKED_DURING_TEST` khi đang thi (5.8.1); mastery **không** đổi từ lượt `CONTEST` (`EvidenceAllocator.contextOf` trả `null`, như QUIZ) |

### Nhóm 2 — Người học (FR-009…FR-016) · UC-090, UC-092 · SCR-048…051

| Mã | Yêu cầu |
|---|---|
| **FR-009** | `GET /api/public/community/contests?state&page&size` (khách + người học) SHALL trả cuộc thi `PUBLISHED`; `state` ∈ `UPCOMING`·`ONGOING`·`ENDED` (tuỳ chọn, §0.4); sắp: `ONGOING` theo `ends_at` tăng, `UPCOMING` theo `starts_at` tăng, `ENDED` theo `ends_at` giảm; khi không lọc thì nhóm theo thứ tự `ONGOING`, `UPCOMING`, `ENDED`. Phần tử `{id, name, startsAt, endsAt, state, prizes, maxParticipants, participantCount, rankingPublished}`. Không có `DRAFT`, `CANCELLED` (BR-090-3) |
| **FR-010** | `GET /api/public/community/contests/{id}` SHALL trả phần tử trên + `description`, `examTitle`, `durationMinutes`, `questionCount`; không `PUBLISHED` → **404** `CONTEST_NOT_FOUND` |
| **FR-011** | `POST /api/community/contests/{id}/attempts` (`LEARNER`) SHALL theo thứ tự: không `PUBLISHED` → 404; `AuthApi.requireVerifiedEmail` (BR-091-4) → 403 `ACCOUNT_UNVERIFIED`; người gọi là `created_by` → 403 `FORBIDDEN`; không `ONGOING` → **422** `CONTEST_NOT_OPEN`; chưa có lượt và đủ `max_participants` → **422** `CONTEST_FULL`; gọi FR-002 với `deadline` §0.4 → **201** (lượt mới) / **200** (resume) `ContestSession {sessionId, contestId, deadline, serverTime, items}`; `ContestAlreadyAttempted` → **409** `CONTEST_ALREADY_ATTEMPTED` |
| **FR-012** | `POST /api/community/contests/{id}/attempts/submit` SHALL gọi FR-004 → **200** `{submittedAt}`; không có lượt → 404 `CONTEST_NOT_FOUND`; trễ → **422** `CONTEST_NOT_OPEN`; đã nộp → **409** `CONTEST_ALREADY_ATTEMPTED` |
| **FR-013** | `POST /api/community/contests/{id}/attempts/events` `{type: TAB_HIDDEN}` SHALL ghi `audit_logs` `CONTEST_CHEAT_EVENT` (`target_type='CONTEST'`, `detail={type}`) khi người gọi có lượt `IN_PROGRESS`; tối đa 1 lần/10 giây/người (`RateLimiter`), vượt → bỏ qua và vẫn **204**; không ảnh hưởng điểm |
| **FR-014** | `GET /api/community/contests/{id}/results` (`LEARNER`) SHALL trả `{contest, published, mine, ranking}`: chưa công bố → `published=false`, `mine = {status, submittedAt}` (không điểm), `ranking = null`; đã công bố → `mine = {status, score, maxScore, durationMs, rank, excluded}` (hoặc `null` nếu không thi), `ranking` = trang FR-006 kèm `displayName` theo lô `AuthApi.publicProfiles`, **không** email/ảnh riêng tư (BR-092-8) |
| **FR-015** | SCR-048/049: thẻ cuộc thi có trạng thái lịch, giờ VN, mô tả giải; SCR-049 có đếm ngược tới mốc kế tiếp, nút "Vào thi" (chỉ `ONGOING`; khách → `/login` giữ đường quay lại; chưa xác thực email → hướng dẫn xác thực), cảnh báo trước khi vào "Mỗi người chỉ thi một lần; thời gian còn lại: X phút"; nút "Xem kết quả" khi đã nộp/đã công bố |
| **FR-016** | SCR-050: đồng hồ theo `deadline − (serverTime − giờ client lúc nhận)`, thanh câu, gửi câu trả lời qua `/api/practice/sessions/{id}/answers`, nút "Nộp bài" có xác nhận trong trang, **tự nộp** khi đồng hồ về 0; sự kiện `visibilitychange` ẩn trang → FR-013; tải lại trang → gọi lại FR-011 (resume). Trợ lý ẩn ở SCR-050 (như SCR-021/044). SCR-051 hiện kết quả FR-014, "Ban tổ chức sẽ liên hệ người đạt giải qua email đăng ký" khi đã công bố |

### Nhóm 3 — Ban tổ chức `CONTENT_EDITOR` (FR-017…FR-024) · UC-113 · SCR-070, SCR-071

| Mã | Yêu cầu |
|---|---|
| **FR-017** | `GET /api/admin/contests?status&page&size` SHALL trả mọi cuộc thi (kể cả `DRAFT`, `CANCELLED`) `{id, name, examId, examTitle, startsAt, endsAt, status, state, rankingPublishedAt, participantCount}`, sắp `starts_at` giảm |
| **FR-018** | `POST /api/admin/contests` `{name* (1–200), description? (≤ 5 000), examId?, startsAt*, endsAt*, maxParticipants? (1–100 000), prizes? (§0.1)}` SHALL tạo `DRAFT`, `created_by` = người gọi; `startsAt ≥ endsAt` → 422; `GET /api/admin/contests/{id}` trả đủ trường + `participantCount` |
| **FR-019** | `PUT /api/admin/contests/{id}` cùng body SHALL: `CANCELLED` → **409** `CONTEST_LOCKED`; `PUBLISHED` và `now ≥ starts_at` → **409** `CONTEST_LOCKED` (EDIT_DURING_CONTEST); `PUBLISHED` trước giờ thi: không được đặt `startsAt` trong quá khứ (422) |
| **FR-020** | `POST /api/admin/contests/{id}/publish` SHALL: chỉ từ `DRAFT`; `examId` null, đề không tồn tại, `ARCHIVED` hoặc không có câu dùng được → **422** `CONTEST_EXAM_INVALID`; `startsAt ≤ now` → 422; đặt `PUBLISHED` (`ck_contest_exam`) |
| **FR-021** | `POST /api/admin/contests/{id}/cancel` SHALL: từ `DRAFT`/`PUBLISHED`; đã có lượt (`countAttempts > 0`) → **409** `CONTEST_HAS_ATTEMPTS`; đặt `CANCELLED`. Không xoá cứng |
| **FR-022** | `GET /api/admin/contests/{id}/participants` SHALL (chỉ khi `ENDED`) trả FR-006 `participants` kèm `displayName`, `cheatEventCount` (đếm `audit_logs` theo người/cuộc thi, một truy vấn), sắp như xếp hạng; `PATCH /api/admin/contests/{id}/participants/{userId}` `{excluded: boolean, note* (1–500)}` SHALL gọi FR-007 (chỉ trước công bố; sau công bố → **409** `CONTEST_LOCKED`) và ghi `audit_logs` `CONTEST_PARTICIPANT_EXCLUDE`/`…_INCLUDE` |
| **FR-023** | `POST /api/admin/contests/{id}/ranking/publish` SHALL: không `PUBLISHED` → 409 `CONTEST_LOCKED`; `now < ends_at` → **409** `CONTEST_RANKING_TOO_EARLY`; đặt `ranking_published_at = now()` một lần (đã công bố → 409 `CONTEST_LOCKED`); ghi `audit_logs` `CONTEST_RANKING_PUBLISH` |
| **FR-024** | `GET /api/admin/contests/{id}/winners` SHALL (chỉ sau công bố) trả các hạng nằm trong `prizes` `{rank, userId, displayName, email, score, durationMs, prize}` — **endpoint duy nhất có email** (Q3); mỗi lần gọi ghi `audit_logs` `CONTEST_WINNERS_VIEW`. Email lấy qua `AuthApi` hàm mới `contactEmails(Set<Long>)` (**module 001 — báo leader trước khi sửa**) |

### Nhóm 4 — Giao diện ban tổ chức (FR-025…FR-026)

| Mã | Yêu cầu |
|---|---|
| **FR-025** | SCR-070 `/content/contests` (`['CONTENT_EDITOR']`, layout `admin`): bảng cuộc thi, lọc trạng thái, nút Tạo/Sửa/Huỷ/Công bố kết quả theo FR-019…FR-023 (nút bị khoá kèm lý do khi không hợp lệ); mọi xác nhận trong trang |
| **FR-026** | SCR-071 `/content/contests/new`, `/content/contests/:id`: form FR-018 (giờ nhập theo VN, chọn đề `DRAFT`/`PUBLISHED` chưa `ARCHIVED` từ danh sách đề quản trị), trường khoá khi `CONTEST_LOCKED`; sau giờ thi: bảng thí sinh FR-022 (cờ đáng ngờ, số lần chuyển tab, nút Loại/Giữ có ghi chú), nút "Công bố kết quả", rồi khối "Liên hệ người đạt giải" FR-024 (email chỉ hiện khi bấm "Hiện email", có ghi nhật ký) |

---

## 4 · Data Model

Không bảng mới. Một migration V9 (§0.2). Ví dụ:

```json
{ "id": 7, "name": "Thử thách HSK 3 tháng 12", "examId": 41, "startsAt": "2026-12-06T19:00:00+07:00",
  "endsAt": "2026-12-06T21:00:00+07:00", "maxParticipants": 200, "status": "PUBLISHED",
  "prizes": [ {"fromRank": 1, "toRank": 1, "description": "Bộ sách HSK 3 có chữ ký"},
              {"fromRank": 2, "toRank": 3, "description": "Thẻ nạp 50.000 đ"} ] }
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `CONTEST_NOT_FOUND` *(mới)* | 404 | Không tồn tại/không `PUBLISHED` (người học); không tồn tại (ban tổ chức); nộp khi không có lượt | FR-010…FR-012, FR-017…FR-024 |
| `CONTEST_NOT_OPEN` *(mới)* | 422 | Vào thi ngoài khung giờ; nộp quá hạn + ân hạn | FR-011, FR-012 |
| `CONTEST_FULL` *(mới)* | 422 | Đủ người | FR-011 |
| `CONTEST_ALREADY_ATTEMPTED` *(mới)* | 409 | Đã có lượt (đã nộp/hết hạn/bỏ) | FR-011, FR-012 |
| `CONTEST_SUBMIT_REQUIRED` *(mới)* | 409 | Gọi finish/abandon chung cho lượt cuộc thi | FR-005 |
| `CONTEST_EXAM_INVALID` *(mới)* | 422 | Đề thiếu/`ARCHIVED`/không có câu | FR-002, FR-020 |
| `CONTEST_LOCKED` *(mới)* | 409 | Sửa khi đã bắt đầu/đã huỷ; xét thí sinh hoặc công bố lần 2 sau công bố | FR-019, FR-022, FR-023 |
| `CONTEST_RANKING_TOO_EARLY` *(mới)* | 409 | Công bố trước `ends_at` | FR-023 |
| `CONTEST_HAS_ATTEMPTS` *(mới)* | 409 | Huỷ khi đã có lượt | FR-021 |
| `ACCOUNT_UNVERIFIED` | 403 | Chưa xác thực email | FR-011 |
| `FORBIDDEN` | 403 | Sai role; người tạo tự thi | FR-011 |
| `VALIDATION_FAILED` | 422 | Body/tham số sai | FR-009, FR-018…FR-022 |

Mã mới thêm vào `ErrorCode` ngay trước `INTERNAL_ERROR` theo thứ tự trên `main` lúc làm; cập nhật `GlobalExceptionHandlerTest`.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-03** | `community.contest` chỉ gọi `learning.api.ContestAttemptApi`, `ActiveTestApi` và `auth.api.AuthApi`; không đọc bảng `learning`/`auth` (ArchUnit `community_chi_duoc_goi_learning_qua_api`) |
| **BUS-09 / HR-06** | Chấm ở server, giờ ở server (BR-092-3, BR-092-6) |
| **BUS-06** | Email chỉ ở FR-024 |
| **AC-05** | Một migration V9 (§0.2) |
| Bảo mật | `SecurityConfig`: `GET /api/public/community/**` đã `permitAll`; `/api/community/**` đã `LEARNER`; thêm `/api/admin/contests/**` → `CONTENT_EDITOR` |

| Lớp | Trách nhiệm |
|---|---|
| `learning.contest.ContestHandler` + `learning.api.ContestAttemptApi` | FR-001…FR-008 |
| `community.contest.ContestService`, `ContestQueryService` | FR-009…FR-014 |
| `community.contest.ContestAdminService` | FR-017…FR-024 |
| Controller | `PublicContestController`, `ContestController`, `ContestAdminController` |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | 200 người cùng bấm "Vào thi" trong 1 phút | Không lỗi 5xx; mỗi người đúng 1 lượt |
| `NFR-P02` | Bảng xếp hạng 1 000 lượt | < 300 ms p95; tên theo lô một lần |
| `NFR-S01` | Không đáp án trước công bố, không email ngoài FR-024 | AT-08, AT-18 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đổi schema | Một index (V9), không bảng |
| Điểm do client gửi | Không |
| Tiền/sổ cái | Không — giải thưởng là mô tả chữ |

---

## 9 · Quyết định

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Bỏ bước đăng ký riêng: vào thi trong khung giờ = đăng ký; không huỷ đăng ký (lệch BR-091-6, BR-092-2 có chủ đích) | ✅ Leader chốt 2026-11-14 |
| Q2 | Đề của cuộc thi để `DRAFT`, chỉ cuộc thi mở đề; sau cuộc thi có thể công khai thành đề thi thử | ✅ Leader chốt 2026-11-14 |
| Q3 | Không trao thưởng trong hệ thống; ban tổ chức liên hệ qua email; email chỉ ở FR-024 cho `CONTENT_EDITOR` (màn SCR-070/071 là của `CONTENT_EDITOR`), có nhật ký | ✅ Leader chốt 2026-11-13 (thưởng) · 2026-11-14 (vai trò) |
| Q4 | Migration index một lượt/người = **V9**; `app_settings` (023) = **V10** | ✅ Leader chốt 2026-11-14 |
| Q5 | Không dùng `CLOSED`, không job đóng: trạng thái lịch tính theo giờ (BR-090-4) | ✅ Chốt |
| Q6 | Lượt `suspicious` (tự động theo thời gian bất thường của engine) không vào bảng trừ khi ban tổ chức "Giữ" trước khi công bố; chuyển tab chỉ ghi nhận (BR-092-9) | ✅ Chốt |
| Q7 | Yêu cầu email đã xác thực khi vào thi (BR-091-4, T6) | ✅ Chốt |
| Q8 | Người vào muộn được thời gian còn lại tới `ends_at`, không có mốc chặn vào muộn | ✅ Chốt — cảnh báo trước khi vào |

---

## 10 · Hợp đồng `learning.api.ContestAttemptApi` (⚿K8 — khoá trước khi code community)

```java
public interface ContestAttemptApi {
    ContestSession start(long userId, long contestId, long examId, Instant deadline); // FR-002
    long countAttempts(long contestId);                                              // FR-003
    Instant submit(long userId, long contestId);                                     // FR-004
    Optional<MyContestAttempt> myAttempt(long userId, long contestId);               // FR-006
    RankingPage ranking(long contestId, int page, int size);                         // FR-006
    Optional<Long> rankOf(long userId, long contestId);                              // FR-006
    List<ContestParticipant> participants(long contestId);                           // FR-006
    void setExcluded(long contestId, long userId, boolean excluded);                 // FR-007
    Optional<ExamSummary> exam(long examId); // {id, title, status, durationMinutes, questionCount} cho FR-010, FR-020

    record ContestSession(long sessionId, Instant deadline, boolean resumed, List<PracticeApi.ServedItemView> items) {}
    record MyContestAttempt(String status, Long score, Long maxScore, Integer durationMs, Instant submittedAt, boolean suspicious) {}
    record RankingRow(long rank, long userId, long score, long maxScore, Integer durationMs, Instant submittedAt) {}
    record RankingPage(List<RankingRow> items, int page, int size, long total) {}
    record ContestParticipant(long userId, String status, Long score, Long maxScore, Integer durationMs, Instant submittedAt, boolean suspicious) {}
    record ExamSummary(long id, String title, String status, int durationMinutes, int questionCount) {}
}
```

Lỗi nghiệp vụ ném `ApiException` với mã §5 (cùng kiểu engine hiện có). Tên record có thể đổi khi G0 T-01 nếu leader duyệt; **chữ ký phương thức giữ nguyên**.

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| R1 — Người học chép đề rồi đưa người khác trong lúc thi | Không chống được hoàn toàn ở web; một lượt/người, đáp án chỉ sau công bố, thời gian bất thường bị gắn cờ |
| R2 — Mất mạng quá ân hạn → lượt `EXPIRED`, không vào bảng | Ân hạn 5 s theo BR-092-10; client tự nộp và resume được trước hạn |
| R3 — Bỏ bước đăng ký: không nhắc trước giờ thi | Q1; người học tự theo dõi SCR-048 |
| R4 — Email người đạt giải lộ qua tài khoản `CONTENT_EDITOR` | Chỉ một endpoint, nhật ký mỗi lần xem, email ẩn tới khi bấm |

---

## 12 · Màn hình

| SCR | Route | Guard · layout | API |
|---|---|---|---|
| SCR-048 Contest List | `/contests` | public · `by-role` | FR-009 |
| SCR-049 Contest Detail | `/contests/:id` | public · `by-role` | FR-010, FR-011, FR-014 |
| SCR-050 Contest Attempt | `/contests/:id/attempt` | `['LEARNER']` · `learner` (ẩn trợ lý) | FR-011, FR-012, FR-013, `/api/practice/sessions/{id}/answers` |
| SCR-051 Contest Results | `/contests/:id/results` | `['LEARNER']` · `learner` | FR-014 |
| SCR-070 Admin Contest List | `/content/contests` | `['CONTENT_EDITOR']` · `admin` | FR-017, FR-021, FR-023 |
| SCR-071 Create / Edit Contest | `/content/contests/new`, `/content/contests/:id` | `['CONTENT_EDITOR']` · `admin` | FR-018…FR-024 |

SCR-048…051 dùng layout `by-role`/`learner` → thêm tên vào `AssistantPromptBuilder.SCREEN_NAMES` ("Danh sách cuộc thi", "Chi tiết cuộc thi", "Làm bài cuộc thi", "Kết quả cuộc thi") và chạy `AssistantPromptBuilderTest`. Menu: mục "Cuộc thi" trong nhóm Cộng đồng (cạnh Quiz của 019).

---

## 13 · API Contract

[`contracts/openapi-contests.yaml`](contracts/openapi-contests.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/public/community/contests` | 200 `ContestPage` | 422 |
| `GET /api/public/community/contests/{id}` | 200 `ContestDetail` | 404 |
| `POST /api/community/contests/{id}/attempts` | 201 · 200 `ContestSession` | 401, 403, 404, 409, 422 |
| `POST /api/community/contests/{id}/attempts/submit` | 200 `SubmitResult` | 401, 403, 404, 409, 422 |
| `POST /api/community/contests/{id}/attempts/events` | 204 | 401, 403, 404, 422 |
| `GET /api/community/contests/{id}/results` | 200 `ContestResults` | 401, 403, 404 |
| `GET` · `POST /api/admin/contests` | 200 `AdminContestPage` · 201 `AdminContest` | 401, 403, 422 |
| `GET` · `PUT /api/admin/contests/{id}` | 200 `AdminContest` | 401, 403, 404, 409, 422 |
| `POST /api/admin/contests/{id}/publish` | 200 `AdminContest` | 401, 403, 404, 409, 422 |
| `POST /api/admin/contests/{id}/cancel` | 200 `AdminContest` | 401, 403, 404, 409 |
| `GET /api/admin/contests/{id}/participants` | 200 `[Participant]` | 401, 403, 404, 409 |
| `PATCH /api/admin/contests/{id}/participants/{userId}` | 200 `Participant` | 401, 403, 404, 409, 422 |
| `POST /api/admin/contests/{id}/ranking/publish` | 200 `AdminContest` | 401, 403, 404, 409 |
| `GET /api/admin/contests/{id}/winners` | 200 `[Winner]` | 401, 403, 404, 409 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001, §0.2 | Flyway V9 tạo `uq_contest_attempt`; chèn tay 2 lượt `CONTEST` cùng người/cuộc thi → vi phạm unique |
| AT-02 | FR-002, E1, SC-002 | 20 request song song cùng người · T 1 lượt, mọi response cùng `sessionId` |
| AT-03 | FR-002, E2 | Lượt đã nộp/hết hạn · W vào lại · T 409 `CONTEST_ALREADY_ATTEMPTED` |
| AT-04 | FR-002, Q2 | Đề `DRAFT` · T vào thi được; `/api/exams` không có đề đó; đề `ARCHIVED` · T 422 `CONTEST_EXAM_INVALID` |
| AT-05 | §0.4, FR-011 | Đồng hồ giả: trước `starts_at`, đúng `ends_at` · T 422 `CONTEST_NOT_OPEN`; giữa giờ · T 201, `deadline = min(start + thời lượng, ends_at)` |
| AT-06 | FR-004, FR-012 | Nộp lúc `deadline + 4 s` · T 200; `deadline + 6 s` · T 422; nộp lần 2 · T 409 |
| AT-07 | FR-005 | `POST /api/practice/sessions/{id}/finish` lượt cuộc thi · T 409 `CONTEST_SUBMIT_REQUIRED` |
| AT-08 | FR-004, FR-014, SC-003 | Trước công bố: response nộp và kết quả không có `score`, `correct`, `answer`, `accept` |
| AT-09 | FR-011, E4 | `maxParticipants=2`, 3 người khác nhau song song · T đúng 2 lượt, người thứ 3 → 422 `CONTEST_FULL` |
| AT-10 | FR-011, E5, Q7 | Người tạo vào thi · T 403; email chưa xác thực · T 403 `ACCOUNT_UNVERIFIED`; khách · T 401 |
| AT-11 | FR-006, FR-014 | 5 lượt (một `suspicious`) · T bảng 4 hàng đúng thứ tự điểm → thời gian → nộp sớm → `user_id`; hạng không trùng |
| AT-12 | FR-007, FR-022, Q6 | Ban tổ chức "Giữ" lượt `suspicious` · T vào bảng; "Loại" lượt thường · T rời bảng; sau công bố · T 409 `CONTEST_LOCKED`; `audit_logs` có dòng |
| AT-13 | FR-008 | Đang thi · W hỏi trợ lý · T `ASSISTANT_BLOCKED_DURING_TEST`; mastery không đổi sau khi nộp |
| AT-14 | FR-009, FR-010 | `DRAFT`, `CANCELLED` không có ở danh sách công khai; chi tiết `DRAFT` → 404; `state` tính đúng theo đồng hồ giả |
| AT-15 | FR-013 | Sự kiện chuyển tab 3 lần trong 10 s · T 1 dòng `CONTEST_CHEAT_EVENT`, 204 cả ba; không có lượt · T 404 |
| AT-16 | FR-019, FR-020, FR-021 | Sửa sau giờ bắt đầu · T 409; công bố thiếu đề · T 422; huỷ khi đã có lượt · T 409 `CONTEST_HAS_ATTEMPTS`; huỷ khi chưa có · T `CANCELLED` |
| AT-17 | FR-023 | Công bố trước `ends_at` · T 409 `CONTEST_RANKING_TOO_EARLY`; sau · T 200; lần 2 · T 409 |
| AT-18 | FR-024, SC-003 | Trước công bố · T 409; sau · T email đúng người hạng có giải, `audit_logs CONTEST_WINNERS_VIEW`; `LEARNER` gọi · T 403; JSON các endpoint khác không có khoá `email` |
| AT-19 | AC-03 | ArchUnit: `community.contest` chỉ phụ thuộc `learning.api`, `auth.api` |
| AT-20 | FR-015, FR-016 (FE) | Vitest: nút "Vào thi" chỉ khi `ONGOING`; khách → `/login`; đồng hồ về 0 → gọi submit đúng một lần; tải lại → resume; trợ lý ẩn ở SCR-050 |
| AT-21 | FR-025, FR-026 (FE) | Vitest: trường khoá khi `CONTEST_LOCKED`; nút công bố khoá trước `ends_at`; email ẩn tới khi bấm "Hiện email" |
| AT-22 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-contests.yaml` (path + method + status hai chiều) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md) · [`questions.md`](questions.md)

# Feature Specification: Chấm bài viết thuê

**Feature:** `022-teacher-grading`
**Feature tree:** 6.2 Teacher chấm bài thuê · **Module:** `learning` (package `learning.grading`)
**Use case:** UC-103 … UC-107 (use-cases-07) · **Actor:** `LEARNER`, `TEACHER`, job hệ thống
**Màn hình:** SCR-032 Grading Request Submission · SCR-033 My Grading Requests · SCR-034 Grading Request Detail · SCR-056 Teacher Grading Queue · SCR-057 Teacher Grading Workspace
**Scope:** V2 · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — leader duyệt 2026-11-14, được code theo `tasks.md` |
| **Phụ thuộc** | Spec 013 (`EntitlementApi.consume/refund`, `PaidFeature.GRADING`, `LedgerService`) · Spec 001 (`AuthApi`, role `TEACHER`) |
| **Quyết định chung** | Leader 2026-11-13: giữ 022, làm phần lõi trước, chi phí đặt sau · leader chốt 2026-11-14 (§9) |
| **Người code** | Quốc Huy |

> **Người code đọc:** `AGENTS.md` → spec 013 FR-020/FR-021 (`EntitlementApi`) → file này → `contracts/openapi-grading.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Người học gửi bài viết tiếng Trung, trừ 1 lượt (`GRADING`), xem trạng thái/kết quả, huỷ khi chưa ai nhận (UC-103, UC-106) | Bài nói/ghi âm |
| Giáo viên xem hàng đợi ẩn danh, nhận, trả lại, chấm theo thang 4 tiêu chí (UC-104, UC-105) | Phúc khảo trong hệ thống (BR-106-8) — khiếu nại xử lý ngoài, `ADMIN` điều chỉnh qua 013 |
| Job hết hạn → hoàn lượt (UC-107) | Thông báo đẩy/email khi có kết quả |
| Trả giáo viên `TEACHER_PAYOUT` theo cấu hình (mặc định 0) | Rút tiền thật cho giáo viên; đánh giá giáo viên |

**Không migration, không bảng mới.** Dùng `learning.service_requests` (`kind='GRADING'`, có sẵn `assignee_id`, `assigned_at`, `deadline_at`, `score`, `feedback`, `payout`, `ck_req_deadline`, `ck_req_feedback`, `ck_req_self`, `ix_req_queue`) và `learning.credit_transactions` (`TEACHER_PAYOUT`, `REFUND`, cột `request_id`).

### 0.1 · Cấu hình `cnhsk.grading.*` (leader chốt 2026-11-14; chuyển sang màn 078 khi 023 có `SettingsApi`)

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `claim-hours` | 48 | Hạn giáo viên phải nhận bài, tính từ lúc gửi |
| `grading-hours` | 24 | Hạn chấm xong, tính từ lúc nhận |
| `teacher-payout-credits` | 0 | Điểm trả giáo viên mỗi bài chấm xong; 0 → không ghi giao dịch |
| `min-hanzi` · `max-hanzi` | 50 · 500 | Số chữ Hán của bài (đếm ký tự thuộc khối CJK Unified Ideographs) |
| `max-chars` | 3 000 | Tổng ký tự bài viết sau khi bỏ khoảng trắng hai đầu |
| `max-active-claims` | 3 | Số bài một giáo viên đang giữ (`ASSIGNED`) |
| `expiry-job-cron` | `0 */5 * * * *` | Chu kỳ job hết hạn |

Thiếu khoá → không khởi động.

### 0.2 · Vòng đời

```
PENDING ──claim──► ASSIGNED ──complete──► COMPLETED
   │  ▲               │
   │  └──release──────┤ (còn hạn nhận)
   │                  ├──release khi đã quá hạn nhận──► EXPIRED (+ hoàn)
   ├──cancel (người học)──► FAILED (+ hoàn), error_message = 'CANCELLED_BY_LEARNER'
   └──quá hạn nhận──► EXPIRED (+ hoàn)      ASSIGNED quá hạn chấm ──► EXPIRED (+ hoàn, không trả giáo viên)
```

`deadline_at` = hạn **nhận** khi `PENDING` (`created_at + claim-hours`); khi nhận, hạn nhận gốc chép vào `payload.claimDeadline` và `deadline_at` = hạn **chấm** (`assigned_at + grading-hours`). Mọi chuyển trạng thái là một câu `UPDATE … WHERE id = ? AND status = ? [AND …]` — 0 dòng nghĩa là thao tác khác đã thắng (BR-107-7).

### 0.3 · JSON

| Cột | Cấu trúc |
|---|---|
| `payload` | `{essay, essayHash, hanziCount, prompt?, hskLevel?, claimDeadline?, consumption: {source, transactionId, feature, idempotencyKey, periodStart}}` — `consumption` là biên nhận server cấp, **không bao giờ** trả ra API |
| `result` | `{criteria: {grammar, vocabulary, coherence, writing} (mỗi mục 0–25), corrections: [{original (≤ 500), corrected (≤ 500), note? (≤ 500)}] (≤ 50)}` |
| `score` | Tổng 4 tiêu chí (0–100) do server cộng |
| `feedback` | Nhận xét 1–5 000 ký tự |

---

## 1 · Context & Goal

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Gửi → nhận → chấm → người học xem kết quả trên dữ liệu demo (tài khoản `TEACHER` seed) | 1 vòng đầy đủ |
| **SC-002** | Hai giáo viên nhận cùng một bài cùng lúc | Đúng 1 thành công |
| **SC-003** | Mỗi yêu cầu được hoàn tối đa 1 lần, trả giáo viên tối đa 1 lần | 0 trùng sau 100 lần chạy job/nộp song song |
| **SC-004** | Không response nào cho người học có `payout`, `consumption`, danh tính giáo viên; hàng đợi không có danh tính người học và nội dung bài | 100% |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | Là người học, tôi dán bài viết tiếng Trung (50–500 chữ Hán), xem trước hạn nhận/hạn chấm và điều kiện hoàn lượt, xác nhận gửi; bị trừ 1 lượt chấm (gói) hoặc 1 điểm |
| US-2 | Là người học, tôi xem danh sách yêu cầu, chi tiết, kết quả (điểm 4 tiêu chí, nhận xét, sửa lỗi); huỷ khi chưa ai nhận và được hoàn |
| US-3 | Là giáo viên, tôi xem hàng đợi (độ dài, giờ gửi, hạn nhận, mức trả — không tên người học), nhận bài, đọc bài, chấm hoặc trả lại |
| US-4 | Là hệ thống, tôi đánh hết hạn yêu cầu quá hạn và hoàn lượt đúng một lần |

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Bấm gửi hai lần cùng một bài | Lần hai → **409** `GRADING_DUPLICATE_ACTIVE`, không trừ lần hai (khoá tư vấn theo người dùng) |
| E2 | Người học huỷ đúng lúc giáo viên nhận | Một trong hai thắng; huỷ thua → **409** `GRADING_NOT_CANCELLABLE` |
| E3 | Job hết hạn đúng lúc giáo viên nhận | Một trong hai thắng (BR-107-7) |
| E4 | Giáo viên nộp kết quả sau hạn chấm | **422** `GRADING_DEADLINE_MISSED`; job sẽ hoàn cho người học |
| E5 | Giáo viên là người gửi bài (vừa `LEARNER` vừa `TEACHER`) | Không thấy bài của mình trong hàng đợi; nhận trực tiếp → **403** `GRADING_SELF_GRADING` |
| E6 | Nộp kết quả hai lần (mạng chập) | Lần hai → **409** `GRADING_NOT_ASSIGNEE` (đã `COMPLETED`); không trả giáo viên hai lần |
| E7 | Hoàn lượt thất bại (lỗi DB) | Rollback cả chuyển trạng thái; job lần sau thử lại |

---

## 3 · Functional Requirements

### Nhóm 1 — Người học (FR-001…FR-008) · UC-103, UC-106 · SCR-032…034

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `GET /api/grading/policy` (`LEARNER`) SHALL trả `{claimHours, gradingHours, minHanzi, maxHanzi, maxChars, cost: 1, refundTerms}`; `refundTerms` là câu tiếng Việt cố định: "Được hoàn lượt nếu bạn huỷ trước khi có giáo viên nhận, hoặc nếu bài không được nhận/chấm đúng hạn" (BR-103-10) |
| **FR-002** | `POST /api/grading-requests` `{essay*, prompt? (≤ 500), hskLevel? (1–9)}` SHALL: bỏ khoảng trắng hai đầu; > `max-chars` hoặc số chữ Hán ngoài `[min-hanzi, max-hanzi]` → **422** `GRADING_INVALID_ESSAY` kèm `details{hanziCount}` (không trừ lượt) |
| **FR-003** | Trong **một transaction** (BR-103-5): khoá tư vấn `grading:submit:{userId}`; có yêu cầu `PENDING`/`ASSIGNED` của người gọi cùng `essayHash` (SHA-256 của bài đã chuẩn hoá khoảng trắng) → **409** `GRADING_DUPLICATE_ACTIVE`; `EntitlementApi.consume(userId, GRADING, "grading:" + UUID mới)` (hết lượt + hết điểm → **402** `INSUFFICIENT_CREDITS`, không tạo); chèn `service_requests` `PENDING`, `deadline_at = now + claim-hours`, `payload` §0.3. Trả **201** `GradingRequest` |
| **FR-004** | `GET /api/grading-requests?status&page&size` SHALL trả yêu cầu của người gọi, sắp `created_at` giảm; phần tử `{id, reference, essayPreview (100 ký tự), status, createdAt, deadline, completedAt, score}` (`score` chỉ khi `COMPLETED`) |
| **FR-005** | `GET /api/grading-requests/{id}` SHALL: không tồn tại, không phải `GRADING` hoặc của người khác → **403** `GRADING_REQUEST_NOT_OWNED` (BUS-01); trả `{id, reference, status, essay, prompt, hskLevel, createdAt, deadline, completedAt, result, refunded}`; `result = {score, criteria, feedback, corrections}` **chỉ** khi `COMPLETED` (BR-106-2/3); không có `payout`, `consumption`, `assigneeId` (BR-106-4/6) |
| **FR-006** | `POST /api/grading-requests/{id}/cancel` SHALL: không phải của mình → 403; trong một transaction `UPDATE … SET status='FAILED', error_message='CANCELLED_BY_LEARNER', completed_at=now() WHERE id=? AND status='PENDING'` (0 dòng → **409** `GRADING_NOT_CANCELLABLE`), rồi `EntitlementApi.refund` bằng biên nhận trong `payload`; trả **200** chi tiết (BR-103-8) |
| **FR-007** | `reference` = `GR-` + `id` 6 chữ số (vd `GR-000042`) để người học và giáo viên nói về cùng một yêu cầu mà không lộ danh tính |
| **FR-008** | SCR-032 `/grading/new`: ô nhập bài với đếm chữ Hán trực tiếp, đề bài và cấp HSK tuỳ chọn, khối "Chi phí: 1 lượt chấm hoặc 1 điểm", hạn nhận/hạn chấm, điều kiện hoàn (FR-001), xác nhận trong trang trước khi gửi; 402 → link mua gói/nạp điểm (013). SCR-033 `/grading`: danh sách + lọc trạng thái. SCR-034 `/grading/:id`: bài, trạng thái, hạn, kết quả 4 tiêu chí + nhận xét + bảng sửa lỗi (chỉ text, BR-106-5), nút "Huỷ yêu cầu" khi `PENDING`. Layout `learner`; thêm `SCREEN_NAMES` SCR-032/033/034 và chạy `AssistantPromptBuilderTest` |

### Nhóm 2 — Giáo viên (FR-009…FR-016) · UC-104, UC-105 · SCR-056, SCR-057

| Mã | Yêu cầu |
|---|---|
| **FR-009** | `GET /api/teacher/grading-queue?page&size` (`TEACHER`) SHALL trả yêu cầu `PENDING` còn hạn nhận, **trừ bài của chính người gọi**, sắp `deadline_at` tăng rồi `id` tăng (BR-104-7); phần tử `{id, reference, hanziCount, hskLevel, sentAt, claimDeadline, payoutCredits}` — **không** có nội dung bài, tên/id người học (BR-104-4/5) |
| **FR-010** | `GET /api/teacher/grading-requests/mine` SHALL trả bài `ASSIGNED` của người gọi `{id, reference, hanziCount, assignedAt, gradingDeadline}` |
| **FR-011** | `POST /api/teacher/grading-requests/{id}/claim` SHALL: người gọi là chủ bài → **403** `GRADING_SELF_GRADING`; đang giữ ≥ `max-active-claims` → **422** `GRADING_CLAIM_LIMIT`; `UPDATE … SET status='ASSIGNED', assignee_id, assigned_at=now(), deadline_at=now()+grading-hours, payload = payload || {claimDeadline: deadline_at cũ} WHERE id=? AND status='PENDING' AND deadline_at > now() AND user_id <> :teacher` (0 dòng → **409** `GRADING_ALREADY_CLAIMED`); trả **200** `TeacherGradingDetail` |
| **FR-012** | `GET /api/teacher/grading-requests/{id}` SHALL chỉ trả khi `status='ASSIGNED' AND assignee_id = người gọi` (khác → **403** `GRADING_NOT_ASSIGNEE`, không lộ nội dung): `{id, reference, essay, prompt, hskLevel, hanziCount, assignedAt, gradingDeadline, payoutCredits}` — không có danh tính người học |
| **FR-013** | `POST /api/teacher/grading-requests/{id}/release` SHALL chỉ cho người đang giữ; còn hạn nhận gốc (`payload.claimDeadline > now`) → về `PENDING`, `assignee_id/assigned_at = null`, `deadline_at = claimDeadline`; đã quá → `EXPIRED` + hoàn (như FR-017). Trả **204** |
| **FR-014** | `POST /api/teacher/grading-requests/{id}/complete` `{criteria {grammar, vocabulary, coherence, writing} (số nguyên 0–25), feedback* (1–5 000), corrections (≤ 50, §0.3)}` SHALL: sai → **422** `GRADING_INVALID_RESULT`; không phải bài mình giữ hoặc không còn `ASSIGNED` → **403** `GRADING_NOT_ASSIGNEE`; `now > deadline_at` → **422** `GRADING_DEADLINE_MISSED` (BR-105-12) |
| **FR-015** | Trong **một transaction**: `UPDATE … SET status='COMPLETED', score=tổng, feedback, result, payout=:payout, completed_at=now() WHERE id=? AND status='ASSIGNED' AND assignee_id=:teacher AND deadline_at >= now()` (0 dòng → 403 như FR-014); nếu `teacher-payout-credits > 0` → `LedgerService.append(TEACHER_PAYOUT, +payout, feature_code='GRADING', request_id=id)` cho giáo viên. Vì chỉ một câu `UPDATE` thắng, payout ghi đúng một lần (BR-105-8); payout lấy từ cấu hình, không từ client (BR-105-10) |
| **FR-016** | SCR-056 `/teacher/grading`: bảng hàng đợi + "Bài tôi đang giữ"; SCR-057 `/teacher/grading/:id`: bài (chỉ text, BR-103-9), hạn chấm đếm ngược, 4 ô điểm 0–25 + tổng tự cộng, nhận xét, bảng sửa lỗi thêm/xoá dòng, nút "Nộp kết quả" (xác nhận trong trang) và "Trả lại bài"; 409 khi nhận → thông báo "Bài đã có người nhận" và tải lại hàng đợi. Route `['TEACHER']`, layout `admin` |

### Nhóm 3 — Hết hạn và hoàn (FR-017…FR-019) · UC-107

| Mã | Yêu cầu |
|---|---|
| **FR-017** | `GradingExpiryJob` (cron `expiry-job-cron`) SHALL lấy tối đa 100 yêu cầu `GRADING` có `status IN ('PENDING','ASSIGNED') AND deadline_at < now()`; với **mỗi** yêu cầu, một transaction riêng: `UPDATE … SET status='EXPIRED', completed_at=now() WHERE id=? AND status=:cũ AND deadline_at < now()` rồi `EntitlementApi.refund` bằng biên nhận (BR-107-3/4/5). 0 dòng → bỏ qua (đã có thao tác khác) |
| **FR-018** | Mỗi yêu cầu hoàn tối đa một lần: chuyển trạng thái điều kiện là chốt chặn; `EntitlementApi.refund` tự chặn hoàn trùng (`uq_refund` với điểm, marker với lượt gói) (BR-107-6) |
| **FR-019** | Job SHALL ghi log `INFO` `{expired, refundFailed, remainingOverdue}` mỗi lần chạy; `remainingOverdue > 0` sau lần chạy → `WARN` (BR-107-8). Không log nội dung bài |

---

## 4 · Data Model

Không migration. Ví dụ một yêu cầu sau khi chấm:

```json
{ "id": 42, "kind": "GRADING", "status": "COMPLETED", "user_id": 15, "assignee_id": 7,
  "payload": { "essay": "我的周末……", "essayHash": "9f2c…", "hanziCount": 186, "hskLevel": 3,
               "claimDeadline": "2026-11-16T09:00:00Z",
               "consumption": { "source": "CREDIT", "transactionId": 901, "feature": "GRADING",
                                "idempotencyKey": "grading:5b1e…", "periodStart": "2026-11-01T00:00:00Z" } },
  "result": { "criteria": { "grammar": 20, "vocabulary": 18, "coherence": 21, "writing": 19 },
              "corrections": [ { "original": "我去了超市昨天", "corrected": "我昨天去了超市", "note": "Trạng ngữ thời gian đứng trước động từ" } ] },
  "score": 78, "feedback": "Bài mạch lạc…", "payout": 0 }
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `GRADING_INVALID_ESSAY` *(mới)* | 422 | Độ dài/số chữ Hán sai | FR-002 |
| `GRADING_DUPLICATE_ACTIVE` *(mới)* | 409 | Cùng bài đang chờ/đang chấm | FR-003 |
| `INSUFFICIENT_CREDITS` *(013)* | 402 | Hết lượt và điểm | FR-003 |
| `GRADING_REQUEST_NOT_OWNED` *(mới)* | 403 | Yêu cầu không có/không phải của mình/sai loại | FR-005, FR-006 |
| `GRADING_NOT_CANCELLABLE` *(mới)* | 409 | Huỷ khi không còn `PENDING` | FR-006 |
| `GRADING_SELF_GRADING` *(mới)* | 403 | Giáo viên nhận bài của chính mình | FR-011 |
| `GRADING_CLAIM_LIMIT` *(mới)* | 422 | Đang giữ đủ số bài | FR-011 |
| `GRADING_ALREADY_CLAIMED` *(mới)* | 409 | Bài đã có người nhận/đã hết hạn/đã huỷ | FR-011 |
| `GRADING_NOT_ASSIGNEE` *(mới)* | 403 | Xem/trả/nộp bài không do mình giữ | FR-012…FR-015 |
| `GRADING_DEADLINE_MISSED` *(mới)* | 422 | Nộp sau hạn chấm | FR-014 |
| `GRADING_INVALID_RESULT` *(mới)* | 422 | Điểm/nhận xét/sửa lỗi sai | FR-014 |
| `FORBIDDEN` | 403 | Sai role | mọi endpoint |
| `VALIDATION_FAILED` | 422 | Tham số trang/lọc sai | FR-004, FR-009 |

Mã mới thêm ngay trước `INTERNAL_ERROR` theo thứ tự trên `main` lúc làm; cập nhật `GlobalExceptionHandlerTest`.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| Tiền | Chỉ qua `EntitlementApi` (trừ/hoàn) và `LedgerService.append` (`TEACHER_PAYOUT`); không đổi CHECK sổ cái (H5) |
| **BUS-01** | Dữ liệu riêng → 403, không lộ tồn tại |
| **BUS-06** | Không email; người học không thấy giáo viên, giáo viên không thấy người học |
| Bảo mật | `SecurityConfig`: `/api/grading/**`, `/api/grading-requests/**` → `LEARNER`; `/api/teacher/**` → `TEACHER` |
| Hiển thị | Bài, nhận xét, sửa lỗi chỉ render text (BR-103-9, BR-105-13) |

| Lớp | Trách nhiệm |
|---|---|
| `learning.grading.GradingConfig` | §0.1 |
| `GradingRequestService` | FR-002…FR-006 |
| `TeacherGradingService` | FR-009…FR-015 |
| `GradingExpiryJob` | FR-017…FR-019 |
| `GradingRepository` | SQL điều kiện §0.2 |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Hàng đợi 1 000 yêu cầu `PENDING` | < 300 ms p95 (dùng `ix_req_queue`) |
| `NFR-R01` | 10 giáo viên nhận cùng một bài | 1 thành công, 9 × 409 |
| `NFR-R02` | Job và nhận bài chạy đua 100 lần | Không yêu cầu nào vừa `ASSIGNED` vừa được hoàn |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đổi schema | Không |
| Client quyết định tiền | Không — trừ 1 lượt theo 013, payout theo cấu hình |
| Mọi bước dính tiền idempotent | Có — chuyển trạng thái có điều kiện + `uq_refund` |

---

## 9 · Quyết định

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Hạn nhận 48 giờ, hạn chấm 24 giờ; quá hạn → `EXPIRED` + hoàn, giáo viên không được trả | ✅ Leader chốt 2026-11-14 |
| Q2 | Trả giáo viên mặc định 0 điểm (không ghi giao dịch); > 0 → `TEACHER_PAYOUT` đúng một lần, áp dụng cho cả bài trả bằng lượt gói lẫn bằng điểm | ✅ Leader chốt 2026-11-14 |
| Q3 | Bài 50–500 chữ Hán, ≤ 3 000 ký tự; thang 4 tiêu chí × 25 (ngữ pháp, từ vựng, mạch lạc, chữ viết) | ✅ Leader chốt 2026-11-14 |
| Q4 | Chi phí 1 lượt `GRADING` hoặc 1 điểm (cách `EntitlementApi.consume` hiện tính) | ✅ Theo 013 |
| Q5 | Người học huỷ khi `PENDING` → `FAILED` + `error_message='CANCELLED_BY_LEARNER'` + hoàn (CHECK `status` không có `CANCELLED`) | ✅ Chốt |
| Q6 | Ẩn danh hai chiều; không phúc khảo, không đánh giá giáo viên trong bản này | ✅ Chốt |
| Q7 | Giáo viên giữ tối đa 3 bài; được trả lại | ✅ Chốt |

---

## 10 · Móc nối

- 013: `EntitlementApi`, `LedgerService`, ví giáo viên xem ở màn sổ cái hiện có (`/api/me/credit-transactions` hiện `TEACHER_PAYOUT`).
- 023: khi có `SettingsApi`, các khoá §0.1 chuyển sang màn 078 — đọc qua một `GradingSettings` để chỉ đổi một chỗ.
- Seed demo: một tài khoản `TEACHER` (leader tạo trên Neon, không commit mật khẩu).

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Ít giáo viên → nhiều yêu cầu hết hạn | Người học được hoàn; leader theo dõi log FR-019 |
| Payout cho bài trả bằng lượt gói làm hệ thống mất điểm | Mặc định 0; bật > 0 là quyết định kinh doanh sau |
| Chất lượng chấm không kiểm được | Không phúc khảo (Q6); `ADMIN` điều chỉnh tay qua 013 khi có khiếu nại |

---

## 12 · Màn hình

| SCR | Route | Guard · layout | API |
|---|---|---|---|
| SCR-032 | `/grading/new` | `['LEARNER']` · `learner` | FR-001…FR-003 |
| SCR-033 | `/grading` | `['LEARNER']` · `learner` | FR-004 |
| SCR-034 | `/grading/:id` | `['LEARNER']` · `learner` | FR-005, FR-006 |
| SCR-056 | `/teacher/grading` | `['TEACHER']` · `admin` | FR-009, FR-010, FR-011 |
| SCR-057 | `/teacher/grading/:id` | `['TEACHER']` · `admin` | FR-012…FR-015 |

Menu: người học — mục "Chấm bài" (gần "Gói dịch vụ và điểm"); giáo viên — mục "Chấm bài" trong thanh bên quản trị.

---

## 13 · API Contract

[`contracts/openapi-grading.yaml`](contracts/openapi-grading.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/grading/policy` | 200 `GradingPolicy` | 401, 403 |
| `POST /api/grading-requests` | 201 `GradingRequest` | 401, 402, 403, 409, 422 |
| `GET /api/grading-requests` | 200 `GradingRequestPage` | 401, 403, 422 |
| `GET /api/grading-requests/{id}` | 200 `GradingRequest` | 401, 403 |
| `POST /api/grading-requests/{id}/cancel` | 200 `GradingRequest` | 401, 403, 409 |
| `GET /api/teacher/grading-queue` | 200 `QueuePage` | 401, 403, 422 |
| `GET /api/teacher/grading-requests/mine` | 200 `[AssignedItem]` | 401, 403 |
| `POST /api/teacher/grading-requests/{id}/claim` | 200 `TeacherGradingDetail` | 401, 403, 409, 422 |
| `GET /api/teacher/grading-requests/{id}` | 200 `TeacherGradingDetail` | 401, 403 |
| `POST /api/teacher/grading-requests/{id}/release` | 204 | 401, 403 |
| `POST /api/teacher/grading-requests/{id}/complete` | 200 `TeacherGradingDetail` | 401, 403, 422 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-002 | Bài 49 chữ Hán; 501 chữ Hán; 3 001 ký tự · T 422 `GRADING_INVALID_ESSAY`, không giao dịch, không yêu cầu |
| AT-02 | FR-003, E1 | Gửi cùng bài 2 lần song song · T 1 yêu cầu, 1 lần trừ, lần kia 409 |
| AT-03 | FR-003 | Hết lượt + 0 điểm · T 402, không yêu cầu; còn lượt gói · T `consumption.source=QUOTA`; hết lượt còn điểm · T giao dịch `DEDUCT -1` |
| AT-04 | FR-004, FR-005, SC-004 | JSON của người học không có `payout`, `consumption`, `assigneeId`; `score`/`result` null khi chưa `COMPLETED`; yêu cầu người khác · T 403 |
| AT-05 | FR-006, E2 | Huỷ khi `PENDING` · T `FAILED` + hoàn; huỷ khi `ASSIGNED` · T 409; huỷ đua với nhận · T đúng một bên thắng |
| AT-06 | FR-009, SC-004 | Hàng đợi không có `essay`, `userId`; không có bài của chính giáo viên; không có bài quá hạn nhận; sắp hạn gần trước |
| AT-07 | FR-011, SC-002 | 10 giáo viên nhận cùng bài song song · T 1 × 200, 9 × 409 `GRADING_ALREADY_CLAIMED` |
| AT-08 | FR-011, E5 | Nhận bài của mình · T 403 `GRADING_SELF_GRADING`; đang giữ 3 bài nhận bài thứ 4 · T 422 `GRADING_CLAIM_LIMIT` |
| AT-09 | FR-012 | Giáo viên khác xem bài đã giao · T 403 `GRADING_NOT_ASSIGNEE`, không có nội dung |
| AT-10 | FR-013 | Trả lại khi còn hạn nhận · T `PENDING`, `deadline_at` = hạn nhận gốc; khi đã quá · T `EXPIRED` + hoàn |
| AT-11 | FR-014 | Tiêu chí 26; nhận xét rỗng; 51 dòng sửa lỗi · T 422 `GRADING_INVALID_RESULT`; nộp sau hạn chấm · T 422 `GRADING_DEADLINE_MISSED` |
| AT-12 | FR-015, SC-003 | `teacher-payout-credits=2`: nộp 2 lần song song · T 1 × 200, `score` = tổng, đúng 1 giao dịch `TEACHER_PAYOUT +2` có `request_id`; `payout=0` · T không giao dịch |
| AT-13 | FR-017, FR-018, SC-003 | Yêu cầu `PENDING` quá hạn nhận và `ASSIGNED` quá hạn chấm · W chạy job 3 lần · T cả hai `EXPIRED`, mỗi yêu cầu đúng 1 lần hoàn |
| AT-14 | FR-017, E3, NFR-R02 | Job và nhận bài chạy đua 100 lần · T không yêu cầu nào vừa `ASSIGNED` vừa có hoàn |
| AT-15 | E7 | `refund` ném lỗi · T yêu cầu vẫn `PENDING`, lần chạy sau hoàn được |
| AT-16 | FR-019 | Log job có `expired`, `remainingOverdue`; không có nội dung bài |
| AT-17 | Bảo mật | `LEARNER` gọi `/api/teacher/**` · T 403; `TEACHER` không có `LEARNER` gọi `/api/grading-requests` · T 403 |
| AT-18 | FR-008, FR-016 (FE) | Vitest: đếm chữ Hán trực tiếp, nút gửi khoá ngoài khoảng; nhận xét/sửa lỗi chứa `<script>` hiện dạng text; tổng điểm tự cộng; 409 khi nhận → tải lại hàng đợi |
| AT-19 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-grading.yaml` (hai chiều path + method + status) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md) · [`questions.md`](questions.md)

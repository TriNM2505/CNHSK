# Feature Specification: Nhắc lịch học qua web và email

**Feature:** `020-study-reminder`
**Feature tree:** 3.5 Nhắc lịch học tự động (không mobile — H1) · **Module:** `auth` (cài đặt, gửi mail) + `learning` (job, banner) — package `auth.reminder`, `learning.reminder`
**Use case:** UC-054, UC-055 · **Actor:** `LEARNER`, Background Job
**Màn hình:** SCR-009 Study Reminder Settings · banner nhắc trên SCR-010 (không thêm màn) · trang xác nhận tắt nhắc (không phải màn mới — §12)
**Scope:** đợt hoàn thiện · **Priority:** P3

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (rà và duyệt 2026-11-13; quyết định chung H1–H14 plan `261113-1629`) |
| **Locked on** | 2026-11-13 |
| **Phụ thuộc** | 001 (`MailService`, xác thực email, JWT secret), 011 (`ReviewSessionService.summary`), 003 (`study_events` ghi khi nộp bài) — đã merge |
| **Quyết định** | Brainstorm 2026-11-13: vẫn nhắc khi đã học trong ngày (nếu còn thẻ đến hạn) · chỉ giờ tròn, job mỗi giờ · tắt nhắc bằng một lần bấm (token ký) |
| **Plan** | `plans/261113-1629-viet-spec-con-lai-hoan-thien/phase-04-spec-020-nhac-lich-hoc-qua-email.md` |

> **Codex đọc:** `AGENTS.md` → file này → `contracts/openapi-reminder.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Cài giờ nhắc (giờ tròn, giờ Việt Nam) + bật/tắt kênh `WEB`, `EMAIL` (SCR-009, UC-054) | Kênh đẩy mobile, bảng `user_devices` (H1 — bỏ mobile) |
| Job mỗi giờ gửi email nhắc khi còn thẻ đến hạn (UC-055) | Múi giờ riêng từng người (BR-054-1) |
| Banner nhắc trên SCR-010 cho kênh `WEB` | Thông báo đẩy trình duyệt (Web Push) |
| Tắt nhắc email bằng một lần bấm từ mail (token ký) + header `List-Unsubscribe` | Trang quản trị lịch sử gửi nhắc |
| Chống gửi trùng bằng unique index có sẵn, kể cả nhiều instance | Health-check scheduler riêng (§11) |

### 0.1 · Lưu trữ trên cột/bảng có sẵn — **không migration**

| Dữ liệu | Cột/bảng (V1) |
|---|---|
| Giờ nhắc | `auth.users.reminder_time TIME` (NULL = chưa cài → mặc định BR-054-2) |
| Kênh | `auth.users.reminder_channels VARCHAR(50) DEFAULT 'WEB'` — giá trị hợp lệ: `''`, `WEB`, `EMAIL`, `WEB,EMAIL` (chuẩn hoá thứ tự) |
| Đã gửi | `learning.study_events` `event_type='REMINDER_SENT'`, `channel='EMAIL'`, `local_date` giờ VN; `uq_reminder(user_id, local_date, channel) WHERE event_type='REMINDER_SENT'` |
| Thẻ đến hạn | 011 `ReviewSessionService.summary(userId).dueCount()` |

### 0.2 · Chia module (ArchUnit: `learning` → `auth` chỉ qua `AuthApi`; `auth` không biết `learning`)

```
SCR-009 ──► auth: GET/PUT /api/me/reminder            (đọc/ghi users.reminder_*)
mail link ──► FE /reminder/unsubscribe?token=… ──► auth: POST /api/public/reminder/unsubscribe
Job (learning, mỗi giờ) ──► AuthApi.reminderTargets(hour) ──► dueCount (011) ──► claim study_events
                         ──► AuthApi.sendStudyReminder(userId, dueCount)   (email chỉ nằm trong auth)
SCR-010 ──► learning: GET /api/reminders/today  ──► AuthApi.reminderSettings(userId) + dueCount + study_events
```

**Email người dùng không bao giờ rời module `auth`** — job chỉ biết `userId`.

### 0.3 · File dùng chung bị sửa (báo leader trước khi mở PR — H8)

`auth.api.AuthApi` (**module 001 — báo leader**: thêm `reminderSettings`, `reminderTargets`, `sendStudyReminder`; record `ReminderSettings`, `ReminderTarget`), `auth.service.MailService` (thêm mẫu mail nhắc + header unsubscribe), `ErrorCode.java`, `SecurityConfig.java`, `application.yaml` (+ `application-test.yaml` cron `-`), `routeAccess.ts`, `screenRegistry.ts`, SCR-010 (`web/features/mastery/...` — **báo người giữ 011**).

---

## 1 · Context & Goal

Học ngoại ngữ cần đều đặn; lịch ôn FSRS (011) chỉ có ích nếu người học quay lại đúng lúc. Nhắc đúng giờ, chỉ khi có việc cần làm, và tắt được dễ dàng — để người học không coi nhắc là spam. Nghiệm thu 3.5: **"đặt 20h thì nhận nhắc lúc 20h giờ Việt Nam"**.

**Thành công khi:** đặt 20:00 + bật email → 20:00 giờ VN nhận đúng một mail nếu còn thẻ đến hạn; không có thẻ thì không nhận; bấm "Tắt nhắc" trong mail một lần là tắt.

---

## 2 · User Stories

### US-1 · Cài nhắc (P1)
Là người học, tôi chọn giờ nhắc và kênh (web, email), lưu và thấy "Bạn sẽ được nhắc lúc 20:00".

### US-2 · Nhận nhắc email (P1)
Là người học đã bật email, tôi nhận mail đúng giờ đã chọn khi còn thẻ cần ôn, có nút vào ôn ngay.

### US-3 · Thấy nhắc trên web (P2)
Là người học bật kênh web, sau giờ nhắc tôi thấy banner trên trang học "Bạn có 12 thẻ cần ôn hôm nay".

### US-4 · Tắt nhắc nhanh (P2)
Là người nhận mail, tôi bấm "Tắt nhắc" trong mail rồi bấm một nút xác nhận là không nhận mail nhắc nữa, không cần đăng nhập.

### Edge cases
- Chưa từng cài → GET trả mặc định 20:00, `WEB` bật, `EMAIL` tắt; chưa ghi DB (BR-054-2, UC-054 A1).
- Bật email khi chưa xác thực → từ chối **toàn bộ** request, không đổi gì (UC-054 A2).
- Tắt hết kênh → cho phép (BR-054-3).
- Giờ có phút khác `00` → `VALIDATION_FAILED` (Q2).
- Đổi giờ trong ngày sau khi đã nhận nhắc hôm nay → không nhận lần hai (unique theo ngày).
- Không có thẻ đến hạn → không gửi, không ghi `REMINDER_SENT` (BR-055-2).
- SMTP lỗi → thử lại 2 lần; vẫn lỗi → xoá dòng giữ chỗ, ghi log; người khác vẫn được gửi (BR-055-5).
- Email đổi hoặc mất xác thực sau khi bật → job bỏ qua người đó (`reminderTargets` chỉ trả email đã xác thực).
- Tài khoản bị khoá → không gửi.
- Hai instance cùng chạy → chỉ instance giữ chỗ được (`INSERT … ON CONFLICT DO NOTHING`) mới gửi.
- Token tắt nhắc hết hạn/sai/đã dùng → trang báo "Liên kết không còn hiệu lực" + nút đăng nhập để tắt ở SCR-009.

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001…FR-002)
- **FR-001** `/api/me/reminder` yêu cầu `LEARNER`; người dùng lấy từ phiên, không nhận `userId` (BR-054-5). PUT cần CSRF.
- **FR-002** `POST /api/public/reminder/unsubscribe` không cần đăng nhập; chỉ hành động bằng token hợp lệ; rate limit theo IP.

### Nhóm 1 — Cài đặt (FR-003…FR-006) · SCR-009 · module `auth`
- **FR-003** `GET /api/me/reminder` → `{reminderTime: "HH:00", channels: {web, email}, emailVerified, configured, timezone: "Asia/Ho_Chi_Minh"}`. `reminder_time IS NULL` → `20:00`, `web=true`, `email=false`, `configured=false` (không ghi DB).
- **FR-004** `PUT /api/me/reminder {reminderTime, web, email}`: `reminderTime` đúng `^([01]\d|2[0-3]):00$`; sai → `VALIDATION_FAILED`.
- **FR-005** `email=true` mà `email_verified_at IS NULL` → `EMAIL_NOT_VERIFIED` (422), không đổi trường nào (atomic).
- **FR-006** Lưu `reminder_time`, `reminder_channels` chuẩn hoá (`WEB,EMAIL` | `WEB` | `EMAIL` | `''`); hiệu lực từ lần chạy kế (BR-054-7); audit `REMINDER_SETTINGS_UPDATED` (trước/sau, không email).

### Nhóm 2 — Job gửi email (FR-007…FR-013) · module `learning`
- **FR-007** `@Scheduled(cron = "${cnhsk.reminder.cron:0 0 * * * *}", zone = "Asia/Ho_Chi_Minh")`; test đặt `-`. Lấy `hour` = giờ hiện tại theo `Clock` giờ VN.
- **FR-008** `AuthApi.reminderTargets(LocalTime hour)` trả `ReminderTarget(userId)` của người có `reminder_time = hour`, `reminder_channels` chứa `EMAIL`, `email_verified_at IS NOT NULL`, `banned_at IS NULL` — sắp `user_id`, tối đa `reminder.batch-size` (mặc định 500; vượt → log WARN số còn lại, không gửi muộn).
- **FR-009** Với mỗi người: `dueCount = ReviewSessionService.summary(userId).dueCount()`; `dueCount = 0` → bỏ qua (BR-055-2). **Không** xét đã học hôm nay (Q1).
- **FR-010** Giữ chỗ: `INSERT INTO learning.study_events(user_id, event_type, channel, local_date) VALUES (…,'REMINDER_SENT','EMAIL', today_vn) ON CONFLICT DO NOTHING`; không chèn được → đã gửi hôm nay/instance khác đang gửi → bỏ qua (BR-055-3).
- **FR-011** `AuthApi.sendStudyReminder(userId, dueCount)` gửi mail (FR-014); lỗi → thử lại tối đa 2 lần (chờ 2 s); vẫn lỗi → xoá dòng giữ chỗ, log WARN `userId` (BR-055-5, BR-055-6).
- **FR-012** Mỗi người xử lý trong try/catch riêng; một lỗi không dừng vòng lặp.
- **FR-013** Cuối lượt log INFO: `hour`, số ứng viên, đã gửi, bỏ qua (không thẻ), trùng, lỗi — không email.

### Nhóm 3 — Nội dung mail (FR-014…FR-015) · module `auth`
- **FR-014** Tiêu đề "CNHSK · Bạn có {N} thẻ cần ôn hôm nay"; thân: lời chào bằng `display_name`, số thẻ, nút "Ôn ngay" → `{APP_WEB_BASE_URL}/learn/review`, dòng "Tắt nhắc qua email" → `{APP_WEB_BASE_URL}/reminder/unsubscribe?token={t}`. Văn bản thuần + HTML đơn giản; người gửi `MAIL_FROM`.
- **FR-015** Header `List-Unsubscribe: <{API_BASE}/api/public/reminder/unsubscribe?token={t}>` và `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058) — endpoint POST nhận cả token ở query.

### Nhóm 4 — Tắt nhắc một lần bấm (FR-016…FR-018) · module `auth`
- **FR-016** Token = `base64url(userId.expEpochSec.purpose) + "." + HMAC-SHA256(JWT_SECRET, …)`, `purpose="REMINDER_UNSUBSCRIBE"`, hạn 30 ngày; không chứa email; so sánh chữ ký thời gian hằng.
- **FR-017** `POST /api/public/reminder/unsubscribe` (token ở body `{token}` hoặc query `token`): hợp lệ → bỏ `EMAIL` khỏi `reminder_channels` (giữ `WEB`), audit `REMINDER_EMAIL_UNSUBSCRIBED` → 204. Đã tắt rồi → vẫn 204 (idempotent). Sai/hết hạn/user không tồn tại → `INVALID_UNSUBSCRIBE_TOKEN` (400).
- **FR-018** FE route công khai `/reminder/unsubscribe` đọc `token` từ query, hiện **một nút** "Tắt nhắc qua email"; bấm → gọi FR-017 → "Đã tắt. Bạn có thể bật lại trong Cài đặt nhắc học." **Không** gọi API khi chỉ mở trang (trình quét mail mở link không làm tắt nhắc).

### Nhóm 5 — Banner web (FR-019…FR-020) · module `learning`
- **FR-019** `GET /api/reminders/today` (`LEARNER`) → `{show, dueCount, reminderTime}`: `show=true` khi kênh `WEB` bật **và** giờ hiện tại VN ≥ giờ nhắc **và** `dueCount > 0`. (Kênh web theo Q1 cũng không xét đã học hôm nay.)
- **FR-020** SCR-010 hiện banner khi `show=true`: "Đã đến giờ ôn: {N} thẻ đang chờ" + nút "Ôn ngay" + nút đóng (ẩn tới hết ngày, nhớ bằng `localStorage` nếu có, không ghi DB).

### Nhóm 6 — Giao diện SCR-009 (FR-021…FR-023)
- **FR-021** Route `/me/reminders` (SCR-009), `LEARNER`, layout học viên; lối vào từ SCR-007 (hồ sơ) và menu tài khoản.
- **FR-022** Chọn giờ (00:00…23:00), 2 công tắc Web/Email, dòng "Múi giờ: Giờ Việt Nam (UTC+7)"; email chưa xác thực → công tắc Email vô hiệu + liên kết "Xác thực email" (SCR-004); Lưu → "Bạn sẽ được nhắc lúc 20:00" hoặc "Bạn đã tắt mọi nhắc học"; Huỷ → không lưu.
- **FR-023** Dùng `shared/ui` + token `design.md` (H10); đủ 4 trạng thái; responsive ≤ 640 px.

### Key Entities
`ReminderSettings(reminderTime, web, email, emailVerified, configured)` · `ReminderTarget(userId)` · `ReminderSent` (dòng `study_events`).

---

## 4 · Data Model
Không migration (§0.1). Ràng buộc có sẵn `uq_reminder` là cơ chế chống trùng duy nhất cần.

## 5 · Error Matrix

| Mã | HTTP | Khi nào |
|---|---|---|
| `EMAIL_NOT_VERIFIED` *(mới)* | 422 | Bật email khi chưa xác thực (UC-054 A2) |
| `INVALID_UNSUBSCRIBE_TOKEN` *(mới)* | 400 | Token sai chữ ký, hết hạn, sai mục đích, user không tồn tại |
| `VALIDATION_FAILED` | 422 | Giờ không phải `HH:00`, thiếu trường |
| `RATE_LIMITED` | 429 | Unsubscribe > 20 lần/phút/IP |
| `UNAUTHENTICATED` / `FORBIDDEN` / `CSRF_TOKEN_INVALID` | 401/403 | Có sẵn |

Mã mới thêm ngay trước `INTERNAL_ERROR` theo thứ tự merge (H8).

## 6 · Architectural Constraints
- `auth` sở hữu `users.reminder_*` và email; `learning` chỉ thấy `userId` qua `AuthApi` (ArchUnit `learning_chi_duoc_goi_auth_qua_api`, `auth_khong_duoc_biet_den_hai_module_kia`).
- Job ở `learning` vì cần `ReviewSessionService` (cùng module).
- Mọi tác vụ định kỳ ghi `zone = "Asia/Ho_Chi_Minh"` (constitution §6).

## 7 · Non-Functional Requirements
- 500 người/giờ gửi xong < 5 phút (Gmail SMTP ~ vài trăm ms/mail; nếu chậm hơn, tăng `batch-size` không giúp — chấp nhận ở quy mô đồ án, §11).
- Log không chứa email, tên, token (BR-055-6).
- Token unsubscribe không dùng lại được cho mục đích khác (`purpose`).

## 8 · Constitution Check
| Luật | Đáp ứng |
|---|---|
| AC-04 số bảng | Không thêm bảng |
| Giờ Việt Nam cho job | FR-007 |
| PII | Email chỉ trong `auth`; log chỉ `userId` |
| BUS-01 sở hữu | FR-001 dùng `/me` |
| Idempotent | `uq_reminder` + giữ chỗ trước khi gửi |

## 9 · Quyết định và câu cần leader xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Vẫn nhắc khi đã học trong ngày, miễn còn thẻ đến hạn | ✅ Leader chốt 2026-11-13 |
| Q2 | Chỉ giờ tròn; job mỗi giờ | ✅ Leader chốt 2026-11-13 |
| Q3 | Tắt nhắc một lần bấm bằng token ký (trang có 1 nút, không tắt khi chỉ mở link) | ✅ Leader chốt 2026-11-13 |
| Q4 | Nguồn "thẻ đến hạn" = 011 `ReviewSessionService.summary.dueCount` | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q5 | `reminder.batch-size` 500/giờ, vượt thì bỏ qua lượt đó (không gửi muộn) | ✅ Leader duyệt cùng spec 2026-11-13 |

## 10 · Móc nối
- **001:** `AuthApi` thêm 3 hàm; `MailService` thêm mẫu mail; dùng `JWT_SECRET` có sẵn để ký token.
- **011:** dùng `ReviewSessionService.summary`; SCR-010 thêm banner (báo người giữ 011).
- **023 (`SettingsApi`, ⚿K7):** `reminder.batch-size` chuyển sang `SettingsApi` khi 023 merge; trước đó đọc `cnhsk.reminder.batch-size`.
- **015:** thêm đoạn `help/` mô tả cài nhắc sau khi merge.

## 11 · Rủi ro đã chấp nhận
- Scheduler ngừng chạy là lỗi im lặng → chỉ có log INFO mỗi giờ; không làm health-check riêng.
- Gmail giới hạn số mail/ngày → quy mô demo không chạm ngưỡng.
- Người đặt giờ đã qua trong ngày → nhắc từ ngày mai.

## 12 · Màn hình
- **SCR-009:** chọn giờ tròn; công tắc Web/Email; múi giờ; trạng thái xác thực email; Lưu/Huỷ; thông báo kết quả.
- **SCR-010 (011):** banner nhắc khi `show=true`.
- **Trang `/reminder/unsubscribe`:** trang tiện ích công khai một nút (không tính vào 79 màn, cùng loại trang đích của link trong email như xác thực email).

## 13 · API Contract
`contracts/openapi-reminder.yaml` — `GET/PUT /api/me/reminder`, `POST /api/public/reminder/unsubscribe`, `GET /api/reminders/today`.

## 14 · Acceptance Tests

| AT | Kịch bản | Kỳ vọng |
|---|---|---|
| AT-01 | Chưa cài, GET | 20:00, web bật, email tắt, `configured=false`, không có dòng DB thay đổi |
| AT-02 | PUT 20:00, email bật (đã xác thực) | 200; DB `20:00`, `WEB,EMAIL` |
| AT-03 | PUT email bật, chưa xác thực | 422 `EMAIL_NOT_VERIFIED`; giờ cũ giữ nguyên |
| AT-04 | PUT `20:30` / `25:00` | 422 `VALIDATION_FAILED` |
| AT-05 | Tắt hết kênh | 200; `reminder_channels=''` |
| AT-06 | Job lúc 20:00 VN (`Clock` = 13:00 UTC), người đặt 20:00, 5 thẻ đến hạn | Gửi 1 mail; 1 dòng `REMINDER_SENT` `EMAIL` ngày VN |
| AT-07 | Như AT-06 nhưng 0 thẻ | Không mail, không dòng |
| AT-08 | Như AT-06, người đã học sáng nay | Vẫn gửi (Q1) |
| AT-09 | Chạy job 2 lần cùng giờ / hai luồng song song | Chỉ 1 mail |
| AT-10 | SMTP lỗi cho người A, B bình thường | B nhận mail; A không có dòng `REMINDER_SENT` |
| AT-11 | Người bị khoá / email chưa xác thực | Không gửi |
| AT-12 | Log sau lượt chạy | Không chứa email, tên, token |
| AT-13 | Mail có header `List-Unsubscribe`, `List-Unsubscribe-Post` và link FE | Đúng |
| AT-14 | POST unsubscribe token hợp lệ | 204; kênh còn `WEB`; audit |
| AT-15 | Gửi lại cùng token | 204 (idempotent) |
| AT-16 | Token sửa 1 ký tự / hết hạn / sai purpose | 400 `INVALID_UNSUBSCRIBE_TOKEN` |
| AT-17 | FE mở `/reminder/unsubscribe?token=…` không bấm | Không có request POST |
| AT-18 | `GET /api/reminders/today` trước/sau giờ nhắc, có/không thẻ, web tắt | `show` đúng từng ca |
| AT-19 | Scheduler có `zone="Asia/Ho_Chi_Minh"` | Test reflection khẳng định |
| AT-20 | Hợp đồng `/v3/api-docs` so `openapi-reminder.yaml` | Khớp path/method/status hai chiều |

## 15 · Gói việc
Xem `tasks.md` — 7 task, ước tính BE 2,5 · FE 1,5 ngày công.

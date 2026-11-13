# Câu hỏi và quyết định — Spec #020

## 2026-11-13 · T-01 · Q-T01-01 — ANSWERED

**Câu hỏi:** Chú thích `reminder_time NULL = tắt` trong V1 và `database.md` khác với FR-003/AT-01. Trường hợp NULL phải được hiểu thế nào?

**Leader chốt:** Làm theo FR-003/AT-01: NULL = chưa cài; GET trả `20:00`, `web=true`, `email=false`, `configured=false`, không ghi DB. Tắt nhắc bằng `reminder_channels=''`; sau khi PUT tắt cả hai kênh, giờ đã lưu vẫn có giá trị và GET trả `configured=true`, `web=false`, `email=false`. Giữ nguyên V1 đã chạy; chỉ sửa chú thích `reminder_time` trong `docs/reference/database.md`.

## 2026-11-13 · T-01 · Q-T01-02 — ANSWERED

**Câu hỏi:** PUT `20:00` ghi xuống PostgreSQL thành `13:00`; chỉ đọc lại qua entity có thể che lỗi vì hai lần chuyển múi giờ triệt tiêu nhau. Cách giữ đúng giờ nhắc Việt Nam trong cột `TIME` là gì?

**Leader chốt:** `hibernate.jdbc.time_zone=UTC` (`application.yaml`) làm ánh xạ `LocalTime` mặc định chuyển giờ từ JVM +07 sang UTC. Chỉ thêm `@JdbcTypeCode(SqlTypes.LOCAL_TIME)` trên `User.reminderTime` để bind/đọc `LocalTime` trực tiếp; giữ nguyên các field khác, cấu hình UTC, cột SQL và V1. Test JDBC kiểm `reminder_time::text` bằng `20:00:00`, cùng các biên `00:00:00`/`23:00:00`, và GET đọc lại đúng `HH:00`.

**Bàn giao T-03:** `reminderTargets(hour)` so sánh với cột `TIME` phải dùng cùng cách bind `LocalTime` trực tiếp, không tự chuyển giờ sang UTC. Giờ nhắc là giờ Việt Nam, không phải một thời điểm `Instant`.

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Đã học trong ngày thì có nhắc không (UC-055 A2) | Vẫn nhắc nếu còn thẻ đến hạn | ✅ Chốt |
| Q2 | Bước giờ nhắc, tần suất job | Chỉ giờ tròn, job mỗi giờ | ✅ Chốt |
| Q3 | Tắt nhắc từ email | Một lần bấm, token ký; trang có 1 nút (không tắt khi chỉ mở link — tránh trình quét mail) | ✅ Chốt |
| Q4 | Nguồn "thẻ đến hạn" | 011 `ReviewSessionService.summary.dueCount` | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q5 | Giới hạn mỗi lượt | 500 người/giờ, vượt thì bỏ qua lượt đó | ✅ Leader duyệt cùng spec 2026-11-13 |

## Dữ kiện đã kiểm trên main `2735ac8`
- `auth.users.reminder_time TIME`, `reminder_channels VARCHAR(50) DEFAULT 'WEB'`, `email_verified_at`, `banned_at`.
- `learning.study_events` có `REMINDER_SENT`, `channel`, `local_date`; `uq_reminder(user_id, local_date, channel) WHERE event_type='REMINDER_SENT'`.
- `PracticeService` ghi `study_events` STUDY/GAME theo ngày giờ VN khi nộp.
- `ReviewSessionService.summary(userId)` → `ReviewSummary(dueCount, nextDueAt)`.
- `MailService` (auth) có `sendVerificationEmail`, `sendPasswordResetEmail`, `send(...)` riêng tư.
- ArchUnit: `learning_chi_duoc_goi_auth_qua_api`, `auth_khong_duoc_biet_den_hai_module_kia`.
- Mẫu job: `PracticeMaintenance` `@Scheduled(cron=…, zone="Asia/Ho_Chi_Minh")`, test cron `-`.

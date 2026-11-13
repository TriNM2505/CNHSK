# Câu hỏi và quyết định — Spec #020

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

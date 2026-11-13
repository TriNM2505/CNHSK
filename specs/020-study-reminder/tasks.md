# Tasks: Nhắc lịch học — Spec #020

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-reminder.yaml`](contracts/openapi-reminder.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 7 task**, ≤ 4 giờ/task, một task một nhánh `spec/020-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 020 `APPROVED`. **Không migration.** Không phụ thuộc spec mới; ⚿K7 (023) chỉ đổi nơi đọc `batch-size` về sau.

## Đợt 1 · Backend `auth`

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | `ReminderSettingsService` + `GET/PUT /api/me/reminder` (mặc định không ghi DB, chuẩn hoá kênh, kiểm giờ tròn, `EMAIL_NOT_VERIFIED` atomic, audit) (**module 001 — báo leader**) | `…/auth/reminder/ReminderSettingsService.java`, `…/auth/web/MeController.java` (hoặc controller mới), `ErrorCode.java`, `SecurityConfig.java` + test | FR-001, FR-003…FR-006 · AT-01…AT-05 | 🔄 Chờ leader review — 2026-11-13: 71 targeted; full verify 4.457 test, F/E/S 0/0/0, exit 0; JaCoCo LINE 96,75% (12.177/12.586). Q-T01-01/Q-T01-02 ANSWERED; allowlist 14 file đã duyệt. |
| T-02 | `UnsubscribeTokens` (HMAC `JWT_SECRET`, purpose, hạn 30 ngày, so sánh thời gian hằng) + `POST /api/public/reminder/unsubscribe` (body/query/form, idempotent, audit, rate limit); mã `INVALID_UNSUBSCRIBE_TOKEN` | `…/auth/reminder/{UnsubscribeTokens,ReminderUnsubscribeService}.java`, controller + test | FR-002, FR-016, FR-017 · AT-14…AT-16 | 🔄 Chờ leader review: Q-T02-01…04 ANSWERED; targeted 160, full verify 4.524 test, F/E/S 0/0/0, exit 0; JaCoCo LINE 96,7577% (12.295/12.707). Diff check/BOM/allowlist 18 PASS; patch tạm đã xoá. |
| T-03 | `AuthApi.reminderSettings`, `reminderTargets(hour)`, `sendStudyReminder(userId, dueCount)` + record; `MailService` mẫu mail nhắc (text + HTML) + header `List-Unsubscribe`, `List-Unsubscribe-Post` (**module 001 — báo leader**) | `…/auth/api/{AuthApi,ReminderSettings,ReminderTarget}.java`, `…/auth/service/{AuthApiService,MailService}.java` + test Mailpit | FR-008, FR-014, FR-015 · AT-11, AT-13 | 🔄 Chờ leader review — 2026-11-14: Q-T03-01…05 ANSWERED; targeted 205, full verify 4.570 test, F/E/S 0/0/0, exit 0; JaCoCo LINE 96,7608% (12.367/12.781). Allowlist 16 file, diff check/UTF-8 không BOM/self-check PASS; T-01/T-02 giữ nguyên test và xanh. |

## Đợt 2 · Backend `learning`

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-04 | `StudyReminderJob` (cron mỗi giờ, `zone` VN, `Clock`), giữ chỗ `study_events` `ON CONFLICT DO NOTHING`, gửi qua `AuthApi`, thử lại 2 lần, xoá giữ chỗ khi lỗi, log tổng kết không PII; cấu hình `cnhsk.reminder.*` + `application-test.yaml` cron `-` [chờ T-03] | `…/learning/reminder/StudyReminderJob.java`, `application.yaml`, `src/test/resources/application-test.yaml` + test | FR-007…FR-013 · AT-06…AT-12, AT-19 | 🔄 Chờ leader review — 2026-11-14: Q-T04-01…03 ANSWERED; 34 test mới, targeted/regression 314, full verify 4.604 test, F/E/S 0/0/0, exit 0; JaCoCo LINE 96,7669% (12.451/12.867). Allowlist chín file, diff check/UTF-8 không BOM/self-check PASS; scheduler inventory và test T-01…T-03 nguyên trạng đều xanh. |
| T-05 | `GET /api/reminders/today` (kênh web, giờ VN, `dueCount`) [chờ T-03] | `…/learning/reminder/{TodayReminderService}.java`, `…/reminder/web/ReminderController.java` + test | FR-019 · AT-18 | ⬜ |

## Đợt 3 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | SCR-009 `/me/reminders` (giờ tròn, 2 công tắc, múi giờ, email chưa xác thực → vô hiệu + link SCR-004, Lưu/Huỷ, 4 trạng thái, responsive); trang công khai `/reminder/unsubscribe` một nút (không gọi API khi chỉ mở); banner SCR-010 (**báo người giữ 011**); route + lối vào từ SCR-007 | `web/features/reminder/**`, `web/features/mastery/pages/Scr010LearningDashboardPage.tsx`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts` + test | FR-018, FR-020…FR-023 · AT-17 | 🔄 Chờ leader review — 2026-11-14: Q-T06-01 ANSWERED, phạm vi 21 file; targeted 54/54 test / 5 file; full FE 2.464/2.464 test / 171 file, 0 fail/skip, exit 0. Lint 0 error (1 warning cũ), build pass; diff/UTF-8 không BOM/source marker/self-check PASS. Client chỉ thêm một path miễn CSRF; test kiểm kê cũ giữ nguyên, patch tạm đã xoá; giữ base 45ceaeb, không rebase. |
| T-07 | Test hợp đồng `/v3/api-docs` so `openapi-reminder.yaml` | `t/contract/ReminderContractTest.java` | AT-20 | ⬜ |

## Thứ tự

```
T-01 ─┐
T-02 ─┼─► T-03 ─► T-04
      │        └─► T-05 ─► T-07
T-06 (song song từ đầu bằng dữ liệu mẫu; nối thật sau T-01/T-02/T-05)
```

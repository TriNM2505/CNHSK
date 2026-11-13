---
phase: 4
title: "Spec 020 nhắc lịch học qua email"
status: pending
priority: P3
dependencies: [1]
effort: "spec 1,5 · BE 2 · FE 1 ngày công"
owner: "Dũng"
---

# Phase 4: Spec 020 — Nhắc lịch học tự động (email)

## Overview
Feature 3.5. Màn SCR-009 Study Reminder Settings. **Không bảng mới, không migration** — cột đã có.

## Context links
- UC-054 Cài giờ nhắc và kênh nhận, UC-055 Gửi nhắc tự động đúng giờ (use-cases-04)
- `auth.users.reminder_time` (TIME), `reminder_channels` (VARCHAR, mặc định `'WEB'`); `learning.study_events` `REMINDER_SENT` + `uq_reminder(user_id, local_date, channel)`
- `MailService` (001), mẫu job: `ImportMaintenance`, `PracticeMaintenance`; `app.timezone: Asia/Ho_Chi_Minh`
- 011 `ReviewController`/`/api/reviews/summary` (số thẻ đến hạn để đưa vào nội dung mail)

## Thiết kế đề xuất
<!-- Updated: viết spec 020 13/11 — leader chốt: vẫn nhắc khi đã học trong ngày; chỉ giờ tròn, job mỗi giờ; tắt nhắc 1 lần bấm bằng token ký (trang 1 nút). Các dòng dưới về "chưa học hôm nay", "mỗi 5 phút", "link về SCR-009" đã bị thay — xem specs/020-study-reminder/spec.md -->

- Kênh chỉ `EMAIL` (H1). `WEB` = huy hiệu nhắc trên dashboard khi chưa học hôm nay (đọc `study_events` STUDY/REVIEW hôm nay) — không gửi gì.
- Job mỗi 5 phút (cron cấu hình, test đặt `-`): chọn người có `reminder_time` trong cửa sổ 5 phút vừa qua theo giờ VN, chưa có `STUDY`/`REVIEW` hôm nay, chưa có `REMINDER_SENT` hôm nay → gửi mail → ghi `study_events` (unique chặn gửi trùng khi chạy song song/khởi động lại).
- Nội dung mail: số thẻ đến hạn (gọi API đọc của 011, không sửa 011), link `/learn/review`, link tắt nhắc (token ký, không cần đăng nhập).
- Tài khoản chưa xác thực email/bị khoá → không gửi.

## Requirements
- Functional: bật/tắt, chọn giờ (bước 15 phút), chọn kênh; xem lần nhắc gần nhất; tắt qua link trong mail.
- Non-functional: idempotent theo ngày; gửi lỗi không ghi `REMINDER_SENT` (để lần sau thử lại trong cửa sổ); log không chứa email (PII).

## Implementation Steps (viết spec)
1. FR theo màn 009 + job; Edge: đổi giờ trong ngày, giờ đã qua, mail lỗi, nhiều instance.
2. API `contracts/openapi-reminder.yaml`: `GET/PUT /api/me/reminder`, `POST /api/public/reminder/unsubscribe`.
3. AT dùng `Clock` cố định + Mailpit (test profile) như các spec trước.
4. tasks.md: 4–5 task (cấu hình+API, job, mail template, FE 009, AT).

## Success Criteria
- [ ] Không migration; dùng đúng cột và unique index có sẵn
- [ ] Không phụ thuộc spec mới nào → code được ngay tuần 2

## Risk Assessment
- Gmail SMTP giới hạn gửi/ngày → giới hạn batch mỗi lần chạy, ghi trong §7 NFR.
- `reminder_channels` là VARCHAR tự do → spec chốt giá trị hợp lệ `WEB`, `EMAIL`, `WEB,EMAIL` và kiểm ở service.

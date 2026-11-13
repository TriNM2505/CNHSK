---
phase: 12
title: "Spec 022 giáo viên chấm bài thuê + phụ lục 009 hồ sơ cộng đồng"
status: pending
priority: P1
dependencies: [1]
effort: "spec 1,5 · BE 6,5 · FE 4 ngày công (022) + 3 ngày công (phụ lục 009)"
owner: "Quốc Huy (sau 013 BE — cùng người viết sổ cái/EntitlementApi)"
---

# Phase 12: Spec 022 — Người học trả điểm thuê giáo viên chấm bài

## Overview
Feature 6.2 (phần chấm thuê). Leader chốt 2026-11-13: **giữ**. Màn SCR-032 Grading Request Submission, 033 My Grading Requests, 034 Grading Request Detail (LEARNER); SCR-056 Teacher Grading Queue, 057 Teacher Grading Workspace (TEACHER). **Không bảng mới** — hạ tầng có sẵn từ V1 và 013.

Kèm **phụ lục 009 v1.1**: SCR-042 Community User Profile (tách khỏi 019 vì cùng module cộng đồng Quốc Huy đã làm).

## Context links
- `feature-tree.md` §6.2 "Teacher chấm bài thuê"; use-cases-07 (UC chấm bài, UC-096 tiêu thụ quyền, UC-097 hoàn quyền), UC-074 theo dõi (use-cases-06)
- `learning.service_requests`: `kind='GRADING'`, `status` PENDING/ASSIGNED/RUNNING/COMPLETED/FAILED/EXPIRED, `assignee_id`, `deadline_at` (`ck_req_deadline`), `payload`/`result` JSONB, index `ix_req_queue`
- `credit_transactions.tx_type` có `TEACHER_PAYOUT`, `REFUND`; `feature_code`, `request_id`
- 013: `EntitlementApi.consume/refund`, `PaidFeature.GRADING`, `LedgerService` (append-only, `seq`), audit; R9 (ADMIN làm tài chính)
- 001: role `TEACHER`; `PiiMasker` (015) nếu cần che dữ liệu

## Thiết kế đề xuất
- **Leader chốt 13/11: làm base trước, chi phí đặt sau.** Giá (`grading.price-credits`), tỷ lệ giáo viên (`grading.teacher-share`), hạn chấm (`grading.deadline-hours`) là **khoá cấu hình trong `SettingsApi` (⚿K7, 078)** — ADMIN đặt sau ở màn 078, không cần sửa code. Mặc định tạm trong yaml (vd giá 0 = dùng hạn mức miễn phí, share 0) để luồng chạy được.
- **Người học (032):** **bài viết** tiếng Trung ≤ 500 chữ (bài nói để sau), tuỳ chọn đề bài + cấp HSK; xem giá + hạn chấm đọc từ `SettingsApi` → `EntitlementApi.consume(GRADING)` (hạn mức miễn phí → điểm → 402) → tạo `service_requests` PENDING, `deadline_at`.
- **Hàng chờ (056, TEACHER):** thấy PENDING sắp theo hạn; **Nhận chấm** → ASSIGNED + `assignee_id` (khoá dòng, một người nhận); giới hạn số bài đang nhận/giáo viên; nhả bài trước khi chấm → về PENDING.
- **Chấm (057):** điểm theo rubric cố định (vd ngữ pháp, từ vựng, mạch lạc, chữ viết; thang 0–100), nhận xét, sửa lỗi từng câu (lưu trong `result` JSONB) → COMPLETED → người học xem ở 034, có thể đánh giá giáo viên 1–5 sao (lưu `result`).
- **Hết hạn:** job đánh EXPIRED bài quá `deadline_at` chưa COMPLETED → `EntitlementApi.refund` (giao dịch `REFUND`), báo người học.
- **Chi trả giáo viên:** khi COMPLETED ghi `TEACHER_PAYOUT` cho giáo viên theo `grading.teacher-share` (share = 0 → không ghi giao dịch), idempotent theo `request_id`. Rút tiền thật nằm **ngoài hệ thống** (ADMIN xem tổng ở sổ cái 013).
- **Chống lạm dụng:** giáo viên không nhận bài của chính mình; người học không thấy danh tính giáo viên trước khi chấm xong (chỉ tên hiển thị sau); khiếu nại → để `questions.md` (đề xuất: ADMIN hoàn thủ công bằng `ADJUSTMENT`).

## Phụ lục 009 — SCR-042 hồ sơ cộng đồng
Tên hiển thị, ảnh, ngày tham gia, số bài đã duyệt + danh sách, số người theo dõi/đang theo dõi, nút Theo dõi (`community.reactions` FOLLOW). Không PII; tài khoản khoá → 404; không tự theo dõi. API: `GET /api/public/community/users/{id}`, `PUT/DELETE /api/community/users/{id}/follow`.

## Implementation Steps (viết spec)
1. Data model: chỉ đặc tả cấu trúc `payload`/`result` JSONB cho GRADING; không migration (nếu cần index thêm → xin số V, H4).
2. Error matrix dự kiến: `GRADING_REQUEST_NOT_FOUND`, `GRADING_ALREADY_ASSIGNED`, `GRADING_NOT_ASSIGNEE`, `GRADING_LIMIT_REACHED`, `GRADING_SELF_ASSIGN`, `GRADING_EXPIRED`, dùng lại `INSUFFICIENT_CREDITS`.
3. API `contracts/openapi-grading.yaml`: `POST/GET /api/grading-requests`, `GET /api/grading-requests/{id}`, `POST …/{id}/rating`; teacher `GET /api/teacher/grading-queue`, `POST /api/teacher/grading-requests/{id}/claim|release|complete`.
4. AT: tiêu thụ → nhận → chấm → payout; hết hạn → hoàn; 2 giáo viên nhận cùng lúc chỉ 1 thành công; payout/hoàn không ghi 2 lần.
5. Phụ lục 009: thêm nhóm FR + OpenAPI vào `specs/009-…`, bump 1.1.0.

## Success Criteria
- [ ] Không bảng mới, không đổi CHECK sổ cái (H5) — `TEACHER_PAYOUT`/`REFUND` đã có
- [ ] Mọi bước dính tiền idempotent + audit
- [ ] Phụ lục 009 có 042 đầy đủ

## Risk Assessment
- Tiền thật hai chiều (người học trả, giáo viên nhận) → chỉ đi qua `EntitlementApi`/`LedgerService`; test đua khi nhận bài và khi hết hạn đúng lúc chấm xong (khoá dòng `service_requests`).
- Cần tài khoản TEACHER thật để demo → seed tài khoản giáo viên trong dữ liệu demo.
- Chờ 013/T-11 (sổ cái đọc/đối chiếu) không bắt buộc — consume/refund (T-12) và LedgerService (T-06) đã merge.

---
phase: 6
title: "Spec 023 quản trị đề thi, tiên quyết, cài đặt hệ thống"
status: pending
priority: P2
dependencies: [1]
effort: "spec 1,5 · BE 8,5 · FE 4,5 ngày công (078 sửa được lúc chạy)"
owner: "Tường (sau 013/T-14)"
---

# Phase 6: Spec 023 — Quản trị đề thi, tiên quyết chủ đề, cấu hình hệ thống

## Overview
Feature 6.6 + 3.2 (phần quản trị). Màn SCR-065 Admin Exam List, SCR-066 Create/Edit Exam, SCR-067 Topic Prerequisites, SCR-078 System Settings. SCR-058 **không** thuộc 023 (chuyển sang 024). **1 bảng mới** `shared.app_settings` cho 078.

## Context links
- UC-112 Quản lý đề thi và kho câu hỏi, UC-116 Cấu hình hệ thống, UC-137 Quản trị kho nội dung (use-cases-08); UC-045/046 cây chủ đề (use-cases-04)
- `learning.exams` (`status` DRAFT/PUBLISHED/ARCHIVED, `sections` JSONB chỉ đọc), `exam_questions`, `topics.prereq_ids BIGINT[]` (comment V1: kiểm chu trình bằng DFS ở application)
- 012: engine thi, mã `EXAM_NOT_FOUND`, `EXAM_NOT_PUBLISHED`, `EXAM_HAS_NO_QUESTIONS`; import đề 012/T-18
- 010: `QuestionAdminController` (`/api/admin/questions`), SCR-059/064 (ngân hàng câu, đã có)
- 013/015/014: các khoá `cnhsk.*` trong `application.yaml` (hạn mức, ngân sách AI, giới hạn thư viện)

## Thiết kế đề xuất
- **065/066 Đề thi (CONTENT_EDITOR; ADMIN xem):** danh sách lọc HSK/trạng thái; tạo/sửa đề DRAFT: tiêu đề, cấp, phần thi (`sections`), chọn câu `APPROVED` từ ngân hàng (tìm theo KID/dạng câu), sắp thứ tự, điểm. Xuất bản kiểm: có câu ở mọi phần, tổng điểm khớp, câu còn `APPROVED`. **Đề PUBLISHED đã có lượt làm không sửa** → "Lưu trữ" + "Nhân bản thành DRAFT". Đề đang gắn cuộc thi PUBLISHED (021) không được lưu trữ.
- **067 Tiên quyết chủ đề (CONTENT_EDITOR):** sửa `topics.prereq_ids`; kiểm chu trình DFS; cảnh báo nếu chủ đề tiên quyết khác cấp HSK cao hơn. Ghi rõ: đây là cổng 90% **chủ đề** (`BUS-05`), khác tiên quyết **KID** của 010 (không sửa ở đây).
- **078 Cấu hình (ADMIN) — leader chốt 13/11: sửa được lúc chạy.**
  - Bảng mới `shared.app_settings(key VARCHAR PK, value TEXT NOT NULL, updated_by BIGINT, updated_at TIMESTAMPTZ)` — migration xin số ở leader (H4); AC-04 lên 35 bảng.
  - **Danh mục khoá cố định trong code** (`SettingKey` enum ở package mới `com.cnhsk.shared.settings` — `shared` hiện không có `api`, chỉ `audit/config/exception/security/util`) <!-- Updated: Validation Session 1 - sửa package -->: mỗi khoá có kiểu (int/bool/duration), min/max, mặc định = giá trị `application.yaml` hiện tại. Không cho thêm khoá tự do; **không bao giờ chứa secret** (`*_KEY`, `*_SECRET`, `PEPPER`, mật khẩu ở `.env`, không vào bảng).
  - Khoá đợt này: `billing.free-monthly-limit`, `assistant.daily-limit`, `ai.monthly-token-budget`, `library.*` giới hạn, `quiz.*`, `grading.price-credits`, `grading.teacher-share`, `grading.deadline-hours`, `reminder.batch-size`, `ai-generate.min-wrong`, `ai-generate.min-bank`, `ai-generate.max-batches-per-run`, `ai-generate.system-user-id`.
  - **⚿K7 `SettingsApi`** (`com.cnhsk.shared.settings`): `int getInt(SettingKey)`…, đọc DB, thiếu dòng → mặc định yaml; cache 30 giây, xoá cache khi ADMIN lưu. Merge sớm để 019/020/022 dùng ngay từ đầu.
  - Chuyển chỗ đọc ở code đã merge (013 `EntitlementService`, 014 giới hạn thư viện, 015 `AiConfig`/giới hạn ngày) sang `SettingsApi` — **mỗi module một task nhỏ, giữ nguyên test cũ** (mặc định không đổi hành vi).
  - Màn 078: danh sách khoá theo nhóm, giá trị hiện tại, mặc định, min/max, người sửa cuối; sửa có xác nhận; audit giá trị cũ → mới; nút "Về mặc định". Kèm số token AI đã dùng tháng này (chỉ đọc).
- Audit mọi thay đổi đề/tiên quyết (`audit_logs`).

## Requirements
- Functional: CRUD đề có vòng đời; nhân bản; sửa tiên quyết có chống vòng; xem cấu hình.
- Non-functional: không làm hỏng lượt thi đang diễn ra (snapshot `served_items` của 012 giữ nguyên); khoá lạc quan khi 2 editor sửa cùng đề.

## Implementation Steps (viết spec)
1. §0.4 file dùng chung: `ErrorCode`, `SecurityConfig` (matcher `/api/admin/exams/**`, `/api/admin/topics/**`, `/api/admin/settings`), `routeAccess.ts`.
2. Error matrix dự kiến: `EXAM_NOT_EDITABLE`, `EXAM_IN_USE`, `EXAM_SCORE_MISMATCH`, `QUESTION_NOT_APPROVED`, `PREREQUISITE_CYCLE`, `EDIT_CONFLICT`, `SETTING_NOT_FOUND`, `SETTING_OUT_OF_RANGE`.
3. API `contracts/openapi-content-admin.yaml`: `GET/POST /api/admin/exams`, `GET/PUT /api/admin/exams/{id}`, `POST …/{id}/publish|archive|clone`, `GET/PUT /api/admin/topics/{id}/prerequisites`, `GET /api/admin/settings`, `PUT /api/admin/settings/{key}`, `DELETE /api/admin/settings/{key}` (về mặc định).
3b. tasks.md: **task ⚿K7 (`SettingKey` + `SettingsApi` + migration) làm đầu tiên**, rồi 3 task chuyển chỗ đọc 013/014/015, rồi màn 078; đề thi/tiên quyết song song.
4. Móc nối §10: 012 đọc đề (không đổi 012), 021 kiểm "đề đang dùng".

## Success Criteria
- [ ] Không sửa engine 012; chỉ 1 bảng mới `shared.app_settings`
- [ ] `SettingsApi` mặc định = yaml hiện tại → test cũ 013/014/015 vẫn xanh
- [ ] Phân biệt rõ tiên quyết chủ đề vs KID
- [ ] 021 không bị chặn bởi 023 (đề import vẫn dùng được)

## Risk Assessment
- Sửa đề đã có người làm → cấm, chỉ nhân bản.
- 078 lộ secret → danh mục khoá cố định trong code + test khẳng định không khoá nào chứa `*_KEY`, `*_SECRET`, `*_PASSWORD`, `PEPPER`.
- Sửa code module khác (013/014/015) → mỗi chỗ một task nhỏ, báo người giữ module, không đổi hành vi mặc định.
- ADMIN đặt giá trị sai làm hỏng nghiệp vụ (vd hạn mức 0) → min/max cứng trong `SettingKey`.

---
phase: 1
title: Luat du an spec va database
status: completed
priority: P1
effort: 0.5 ngay
dependencies: []
---

# Phase 1: Luật dự án, spec và database

## Overview

Sửa các nguồn chuẩn trước: constitution, ràng buộc nghiệp vụ, spec RBAC, `database.md`, V1. Các phase sau đối chiếu với đây.

## Related Code Files

| File | Chỗ sửa |
|---|---|
| `.specify/memory/constitution.md` (10 chỗ) | Danh sách role, câu "8 actor" |
| `.specify/memory/constraints/business.md` (11) | Bảng role mục 1, mục 1.1 "Hai actor không phải role" → chỉ còn `GUEST`; "8 actor = 6 role + 2" → "7 actor = 6 role + Guest"; ghi chú job `@Scheduled` không là actor |
| `CLAUDE.md`, `AGENTS.md` (1 mỗi file) | Tên role |
| `specs/001-auth-rbac/spec.md` (20) | FR-058 danh sách role; CHECK `ck_ur_role_code`; mọi `SUPER_ADMIN` trong luật "không tự cấp role", "không gỡ admin cuối" |
| `specs/001-auth-rbac/plan.md`, `tasks.md` | Tên role |
| `docs/reference/database.md` (12) | CHECK `ck_ur_role_code` (dòng ~1131), mục `user_roles`, mô tả role |
| `db/migration/V1__core_schema.sql` | Seed bảng `roles` (dòng 57–62). Nếu V1 được viết lại theo QĐ-01 cây (bỏ bảng `roles`) → đổi trong CHECK của bản mới |

## Implementation Steps

1. `business.md` + `constitution.md`: bảng role mới, 7 actor, bỏ `SYSTEM` khỏi danh sách actor (giữ mô tả job nền ở chỗ khác).
2. Spec 001: FR-058 và CHECK:
   ```sql
   CONSTRAINT ck_ur_role_code CHECK (role_code IN
     ('LEARNER','TEACHER','MODERATOR','CONTENT_EDITOR','ACCOUNTANT','ADMIN'))
   ```
3. `database.md`: cùng CHECK; giữ `reactions.target_type 'USER'`.
4. V1: đổi seed (nếu còn bảng `roles`) hoặc CHECK (nếu đã bỏ).
5. Thêm change log 18/10 vào `database.md` và spec 001.

## Success Criteria

- [ ] 7 file trên không còn mã role cũ
- [ ] `reactions.target_type` vẫn có `'USER'`
- [ ] `business.md` ghi 7 actor

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Đổi nhầm `USER` trong `reactions` | Sửa CHECK role bằng tìm `ck_ur_role_code`, không tìm `'USER'` chung |

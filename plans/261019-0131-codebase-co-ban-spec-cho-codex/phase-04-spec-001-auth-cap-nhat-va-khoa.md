---
phase: 4
title: "Spec 001 auth cap nhat va khoa"
status: in-progress
priority: P1
effort: "1.5 ngay"
dependencies: []
---

# Phase 4: Spec 001 auth — cập nhật và nâng lên chuẩn 15 mục

## Overview

Spec 001 đã có 11 mục (41 KB) và đã đổi tên actor. Cần: điền quyết định D1/D2, tách phần mã thẻ/gói ra (ngoài phạm vi đợt này),
thêm mục 12–15, rồi khoá.

## Requirements

| Việc | Chi tiết |
|---|---|
| Điền quyết định | FR-016, FR-061: HS256 (D1); TTL 15 phút / 30 ngày, refresh xoay vòng (D2) |
| Tách phạm vi | **Đã chốt (V3):** FR-076→FR-082b (kênh mã thẻ), gói dịch vụ: chuyển sang `007-credits-plans` (plan sau). Spec 001 giữ: đăng ký, xác thực email, đăng nhập web/mobile/**trang game `cnhsk-game` bằng cookie chung (UC-005, bắt buộc vì V1)**, refresh, đăng xuất, quên/đặt lại mật khẩu, chặn tạm sau nhiều lần sai (UC-012 «extend»), hồ sơ, đổi mật khẩu, quản trị người dùng + gán/thu hồi role, ban. Cột `plan_code` vẫn tạo ở V1 với mặc định `FREE` |
| UC-006 | Đã thành FR (refresh token), không còn UC — kiểm FR tương ứng đủ |
| Mục 12 màn hình | SCR-001 Landing (phần auth), 002→008, 076, 077, 079 — trích `screen-fields.md` |
| Mục 13 API | `/api/auth/register, verify-email, login, refresh, logout, forgot-password, reset-password`, `/api/me, /api/me/password`, `/api/admin/users, /{id}, /{id}/roles, /{id}/ban` |
| Mục 14 tests | Given/When/Then cho mọi FR, gồm E1–E5 và ma trận lỗi |
| Mục 15 tasks | Tách BE/FE; FE theo từng SCR |

## Related Code Files

- Modify: `specs/001-auth-rbac/{spec.md, plan.md, tasks.md, checklists/requirements.md}`
- Modify: `docs/api/openapi.yaml`

## Implementation Steps

1. Chờ plan đổi tên actor xong phase 3–5 (UC-108/109, SRS) — spec 001 không phụ thuộc trực tiếp nhưng tránh khoá rồi sửa.
2. Điền D1/D2; chuyển FR mã thẻ/gói sang ghi chú "thuộc spec 007".
3. Viết mục 12–15.
4. `/speckit-analyze` kiểm nhất quán spec–plan–tasks; **cả nhóm duyệt** (V7).
<!-- Updated: Validation Session 1 - tách mã thẻ/gói sang 007 (V3), giữ UC-005 (V1), cả nhóm duyệt (V7) -->

## Success Criteria

- [ ] Mục 9 Open questions = 0 câu chặn
- [ ] Mỗi FR có ≥ 1 acceptance test, mỗi SCR có đủ field/action/state
- [ ] `speckit-analyze` không báo lỗi

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Tách mã thẻ làm vỡ FR liên quan đăng ký (gán `FREE`) | Giữ cột và mặc định `FREE` trong spec 000/001; logic gói ở spec 007 |

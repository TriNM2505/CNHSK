---
phase: 2
title: Use case va screen fields
status: completed
priority: P1
effort: 1 ngay
dependencies:
  - 1
---

# Phase 2: Use case và screen fields

## Overview

Đổi tên actor trong 8 file use case, `screen-fields.md`, sơ đồ luồng màn hình, rồi sinh lại `screens.json`.

## Related Code Files

| File | Số chỗ khớp | Ghi chú |
|---|---|---|
| `docs/reference/use-cases-01` → `08-*.md` | 29 · 146 · 57 · 43 · 82 · 185 · 64 · 154 | Cột "Actor chính", bảng quyền, mô tả luồng |
| `docs/reference/huong-dan-ve-so-do-use-case.md` | 17 | Hướng dẫn vẽ — tên actor trên sơ đồ |
| `docs/reference/screen-fields.md` | 889 | Nguồn của `screens.json` |
| `docs/reference/screen-flow/05-cong-dong.mmd`, `06-quan-tri.mmd` (+ PNG) | 1 · 3 | Render lại PNG |
| `docs/reference/tools/screen-content.mjs`, `screen-templates.mjs`, `build-html-mockup.mjs` | 7 · 3 · 3 | Chuỗi role trong template |
| `docs/reference/feature-tree.md`, `design.md`, `kien-truc.md`, `CONTEXT.md` | 16 · 21 · 5 · 4 | |
| `docs/reference/screens.json` | 709 | **Không sửa tay** — sinh lại |

## Implementation Steps

1. Use case 01–08: thay theo mẫu
   - `` `USER` `` (actor) → `` `LEARNER` ``; "User" khi là actor → "Learner"
   - `` `MANAGER` `` → `` `MODERATOR` ``; `CONTENT_ADMIN` → `CONTENT_EDITOR`; `FINANCE_ADMIN` → `ACCOUNTANT`; `SUPER_ADMIN` → `ADMIN`
   - Tiếng Việt: thống nhất **Người học** (bỏ "Học viên" khi chỉ actor)
2. `screen-fields.md`: cột "Ai thấy màn", điều kiện quyền. Giữ tên màn "User List", "User Detail".
3. Tool `.mjs`: chuỗi role.
4. Chạy `node docs/reference/tools/extract-screens.mjs` → `screens.json`; chạy `verify-screens.mjs`.
5. Render lại 2 PNG luồng màn hình.
6. 4 file reference còn lại.

## Success Criteria

- [ ] Grep mã role cũ trong `docs/reference` = 0
- [ ] `screens.json` khác trước đúng ở tên role (diff)
- [ ] `verify-screens.mjs` chạy qua

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| 889 chỗ trong `screen-fields.md`, dễ thay nhầm "User Detail" | Thay theo mẫu có ngữ cảnh (`` `USER` ``, "actor: User"), không thay chữ trần; review diff |
| `screens.json` lệch nguồn | Chỉ sinh lại bằng script |

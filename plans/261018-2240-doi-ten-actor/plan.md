---
title: Doi ten actor CNHSK
description: >-
  Doi ma role va ten hien thi actor: USER->LEARNER, MANAGER->MODERATOR,
  CONTENT_ADMIN->CONTENT_EDITOR, FINANCE_ADMIN->ACCOUNTANT, SUPER_ADMIN->ADMIN.
  Teacher la nguoi duy nhat duyet cau AI va sua cau hoi. System khong la actor.
status: pending
priority: P1
branch: main
tags:
  - docs
  - rbac
  - srs
blockedBy: []
blocks:
  - 261015-2036-79-man-html-sang-react
  - 261019-0131-codebase-co-ban-spec-cho-codex
created: '2026-10-18T15:51:35.949Z'
createdBy: 'ck:plan'
source: skill
---

# Đổi tên actor CNHSK

## Overview

Đổi tên 5 role cho đúng việc họ làm; chỉ actor quản trị tổng mang chữ "Admin".
Nguồn quyết định: [`plans/reports/brainstorm-261018-2240-dat-lai-ten-actor-report.md`](../reports/brainstorm-261018-2240-dat-lai-ten-actor-report.md).

| Mã cũ | Mã mới | Tên | Tiếng Việt |
|---|---|---|---|
| `USER` | `LEARNER` | Learner | Người học |
| `TEACHER` | `TEACHER` | Teacher | Giáo viên |
| `MANAGER` | `MODERATOR` | Moderator | Kiểm duyệt viên |
| `CONTENT_ADMIN` | `CONTENT_EDITOR` | Content Editor | Biên tập viên nội dung |
| `FINANCE_ADMIN` | `ACCOUNTANT` | Accountant | Kế toán |
| `SUPER_ADMIN` | `ADMIN` | Admin | Quản trị viên |
| (không role) | `GUEST` | Guest | Khách |

Kèm: UC-108, UC-109 chỉ còn `TEACHER` · `SYSTEM` không là actor (7 actor = 6 role + Guest).

Chỉ sửa tài liệu, spec, migration, report, slide. **Chưa có code Java** dùng role nên không phải sửa code.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Luat du an spec va database](./phase-01-luat-du-an-spec-va-database.md) | Completed |
| 2 | [Use case va screen fields](./phase-02-use-case-va-screen-fields.md) | Completed |
| 3 | [Go quyen trung Content Editor](./phase-03-go-quyen-trung-content-editor.md) | Pending |
| 4 | [SRS hinh use case va Report 1](./phase-04-srs-hinh-use-case-va-report-1.md) | Pending |
| 5 | [Slide va kiem tra cuoi](./phase-05-slide-va-kiem-tra-cuoi.md) | Pending |

Thứ tự: 1 → 2 → 3 → 4 → 5. Phase 1 là nguồn chuẩn; các phase sau đối chiếu với nó.

## Quy tắc đổi (áp cho mọi phase)

**Không replace-all.** Những chỗ trùng chữ nhưng khác nghĩa phải giữ:

| Giữ nguyên | Lý do |
|---|---|
| `reactions.target_type = 'USER'` | Loại đối tượng được theo dõi, không phải role |
| Tên màn/UC về tài khoản: "User List", "User Detail", "Manage Users", bảng `users` | "User" ở đây là tài khoản |
| Đường dẫn `/api/admin/...`, khu vực "Admin" của giao diện | Khu vực quản trị chung, dùng cho mọi role quản trị |

Cách làm: sửa theo từng mẫu cụ thể (`` `USER` `` trong cột actor, `'USER'` trong CHECK role, "User" trong sơ đồ actor), review diff từng file.

## Acceptance criteria

- [ ] Grep `SUPER_ADMIN|CONTENT_ADMIN|FINANCE_ADMIN|\bMANAGER\b|Super Admin|Content Admin|Finance Admin` trong repo (trừ `plans/`) = 0
- [ ] `'USER'` chỉ còn ở `reactions.target_type`
- [ ] UC-108, UC-109: actor chính chỉ `TEACHER`
- [ ] Không còn câu "8 actor" / `SYSTEM` là actor
- [ ] SRS: bảng actor, cột actor UC, 7 hình use case, ma trận quyền dùng tên mới
- [ ] `screens.json` sinh lại từ `screen-fields.md`, không sửa tay

## Dependencies

- **Chặn** [`261015-2036-79-man-html-sang-react`](../261015-2036-79-man-html-sang-react/plan.md): mockup và React dùng tên role/actor → đổi tên trước để không làm hai lần.
- **Gộp với** [`261017-1654-cay-nang-luc-han-ngu`](../261017-1654-cay-nang-luc-han-ngu/reports/quyet-dinh-cay-nang-luc.md) QĐ-01 "sửa V1 ngay": sửa seed role trong cùng lần viết lại V1.

## Unresolved questions

1. Có đổi tên trong mockup HTML 79 màn ngay trong plan này không, hay để plan 79 màn làm? (Plan này mặc định: **để plan 79 màn**, sau khi phase 2 xong.)

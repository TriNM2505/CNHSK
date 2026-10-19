---
phase: 4
title: "SRS hinh use case va Report 1"
status: pending
priority: P1
effort: "1 ngay"
dependencies: [3]
---

# Phase 4: SRS, hình use case và Report 1

## Overview

Đổi tên trong report Word đã nộp/sắp nộp và vẽ lại nhãn 7 hình use case.

## Related Code Files

| File | Chỗ sửa |
|---|---|
| `docxxx/Report3_SRS_CNHSK.docx` | 4.1 Actors (7 dòng, mô tả trách nhiệm mới) · 4.2 cột actor · 4.3 bảy hình use case · 4.4 ma trận quyền · Context diagram (nhãn USER, MANAGER, CONTENT_ADMIN, FINANCE_ADMIN, SUPER_ADMIN) · use case specification |
| `docxxx/Report1_Project_Introduction_CNHSK.docx` | FE-07 danh sách role ("learners, teachers, community managers, content administrators, finance administrators and the super administrator") |
| File draw.io gốc của 7 hình use case + context diagram | Đổi nhãn actor, tên khung; UC-108/109 bỏ khỏi sơ đồ Content Editor |

## Implementation Steps

1. Draw.io: đổi nhãn actor 7 hình + context diagram; sơ đồ Content Editor bỏ "Review AI-Generated Questions", "Edit Question Content"; sơ đồ User đổi thành Learner.
2. Xuất lại PNG, thay vào SRS mục 4.3 và I.1.
3. SRS 4.1: bảng 7 actor với tên + trách nhiệm theo bảng ở `plan.md`.
4. SRS 4.2, 4.4, use case specification: tên actor.
5. Report 1 FE-07: "learners, teachers, moderators, content editors, accountants and administrators".
6. Change log cả hai report: "18/10/2026 · M · Đổi tên actor theo trách nhiệm".

## Success Criteria

- [ ] Find trong Word không còn "Super Admin", "Content Admin", "Finance Admin", "MANAGER"
- [ ] 7 hình + context diagram dùng tên mới

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Không còn file draw.io gốc | Hỏi người vẽ trong nhóm; nếu mất thì vẽ lại từ PNG |
| Report 3 v1.0 đã nộp | Sửa ở bản sau, ghi change log |

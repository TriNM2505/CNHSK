---
phase: 5
title: "Slide va kiem tra cuoi"
status: pending
priority: P2
effort: "0.5 ngay"
dependencies: [4]
---

# Phase 5: Slide và kiểm tra cuối

## Overview

Cập nhật slide bảo vệ + tài liệu chi tiết, rồi grep toàn repo xác nhận không còn tên cũ.

## Related Code Files

| File | Chỗ sửa |
|---|---|
| `docs/presentation/source/cnhsk-slides.html` | Slide 14 bảng actor (`actors()` trong script) · slide 19–20 nhãn "TEACHER, CONTENT ADMIN, MANAGER, FINANCE ADMIN, SUPER ADMIN, GUEST" · ảnh `img/uc-*.png` thay bằng PNG mới |
| `docs/presentation/source/cnhsk-slide-notes.html` | Mục 14, 19, 20 |
| `docs/presentation/CNHSK_Defense_Slides.pdf`, `CNHSK_Defense_Slide_Notes.pdf` | Render lại bằng `source/html-to-pdf.ps1` |

## Implementation Steps

1. Thay PNG use case trong `source/img/` bằng bản phase 4.
2. Sửa bảng actor slide 14 và nhãn slide 19–20; tài liệu chi tiết mục tương ứng.
3. Render lại 2 PDF.
4. Kiểm tra toàn repo:
   ```
   grep -rnE "SUPER_ADMIN|CONTENT_ADMIN|FINANCE_ADMIN|\bMANAGER\b|Super Admin|Content Admin|Finance Admin" --exclude-dir=plans .   → 0 dòng
   grep -rn "'USER'" --exclude-dir=plans .                                                                                       → chỉ reactions.target_type
   grep -rn "8 actor" --exclude-dir=plans .                                                                                      → 0 dòng
   ```

## Success Criteria

- [ ] 3 lệnh grep cho đúng kỳ vọng
- [ ] 2 PDF render lại, 23 trang slide

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Sót tên trong file nhị phân (.docx, .png) mà grep không thấy | Phase 4 kiểm bằng Find trong Word và xem lại từng hình |

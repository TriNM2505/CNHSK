---
phase: 6
title: "Dong bo tai lieu va bao cao"
status: pending
priority: P1
effort: "3 ngay"
dependencies: [1]
---

# Phase 6: Đồng bộ tài liệu, report và slide

## Overview

Gỡ mọi câu mâu thuẫn với cây khỏi tài liệu đã nộp/sắp nộp. Phần định nghĩa và giải pháp làm ngay
sau phase 1; phần ERD, BF, use case làm sau phase 2–5.

## Requirements

- Thuật ngữ thống nhất: **nút (KID)**, **minh chứng**, **mastery MP-1.0**, **mục từ**, **biên học tập**.
- Không còn: "knowledge point = một từ", "FSRS tính mastery", "365 ngày / 1 ngày" (trừ khi Q2 giữ FSRS ở tầng mục từ), "cổng 90%" (trừ khi Q4 giữ).
- Trích nguồn: đặc tả Học Bá v2.0 + chuẩn GF 0025-2021.

## Danh sách sửa

| Tài liệu | Chỗ sửa | Sau phase |
|---|---|---|
| Report 1 (`docxxx/Report1_...docx`) | Định nghĩa Knowledge point, Mastery, Topic gate · GAP-01/03 · IV Proposed Solution · FE-01, FE-03 · LI-08 (HSK 7–9) | 1 |
| Report 2 | Iteration 2: "FSRS, 90% gate" → MP-1.0, biên học tập · rủi ro mới: phụ thuộc bảng tính cây của Học Bá | 1 |
| Report 3 SRS | BF-01→04 · ERD + mô tả thực thể · use case UC-040→053 · business rules · màn hình | 2–5 |
| `CONTEXT.md` | Vấn đề 2 (quên) · thuật ngữ | 1 |
| `feature-tree.md` | 1.5, 3.1, 3.2, 6.3 | 1 |
| `database.md` | Mục 4.3–4.9, bảng "không được đụng" | 2–4 |
| `kien-truc.md` | Sơ đồ module learning: thêm mastery engine | 4 |
| Slide + tài liệu chi tiết (`docs/presentation/`) | Slide 8–10, 15–16, 21 | 6 cuối |

## Related Code Files

- Modify: các file trong bảng trên
- Create: `docs/reference/cay-nang-luc/thuat-ngu-cay-nang-luc.md` (bảng thuật ngữ dùng chung, nếu `giai-thich-cay-nang-luc.md` chưa đủ)

## Implementation Steps

1. Grep toàn repo + 3 report: `FSRS`, `knowledge point`, `90%`, `skill_type`, `365`, `stability` → danh sách chỗ sửa.
2. Sửa Report 1, 2, `CONTEXT.md`, `feature-tree.md` (làm được ngay sau phase 1).
3. Sau phase 2–5: sửa SRS (BF, ERD, use case), `database.md`, `kien-truc.md`.
4. Vẽ lại ERD (dbdiagram) với bảng mới; xuất PNG.
5. Cập nhật slide + tài liệu chi tiết; render lại PDF bằng `docs/presentation/source/html-to-pdf.ps1`.
6. Grep lần cuối: 0 câu mâu thuẫn.

## Success Criteria

- [ ] Grep các từ khoá cũ chỉ còn ở chỗ đã chủ đích giữ
- [ ] Report 1 định nghĩa knowledge point khớp đặc tả mục 1.5
- [ ] ERD mới có `kt_node`, `kt_edge`, `item_kid`, `evidence`, `mastery`, `lexical_state`
- [ ] Slide + PDF render lại không lỗi

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Report đã nộp không sửa được | Ghi thay đổi vào change log của report bản sau; giải thích khi bảo vệ là "căn chỉnh theo đặc tả Học Bá 16/10" |
| Sửa lệch giữa các tài liệu | Bảng thuật ngữ dùng chung; grep cuối |

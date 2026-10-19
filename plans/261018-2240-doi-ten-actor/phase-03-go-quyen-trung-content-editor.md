---
phase: 3
title: "Go quyen trung Content Editor"
status: pending
priority: P2
effort: "0.5 ngay"
dependencies: [2]
---

# Phase 3: Gỡ quyền trùng của Content Editor

## Overview

Teacher là người duy nhất duyệt câu hỏi AI và sửa nội dung câu hỏi. Gỡ Content Editor khỏi UC-108, UC-109 và mọi chỗ cấp quyền tương ứng.

## Related Code Files

| File | Chỗ sửa |
|---|---|
| `docs/reference/use-cases-08-quan-tri-he-thong.md` | UC-108 (dòng ~37), UC-109 (dòng ~194): "Actor chính" chỉ `TEACHER` |
| `docs/reference/screen-fields.md` | Màn AI Review Queue, sửa câu hỏi: bỏ `CONTENT_EDITOR` khỏi "ai thấy màn" |
| `docs/reference/feature-tree.md` | Mục 6.6 duyệt câu AI: người duyệt = giáo viên |
| `specs/001-auth-rbac/spec.md` | Ma trận quyền nếu có dòng duyệt câu |
| `.specify/memory/constraints/business.md` | Mô tả trách nhiệm `CONTENT_EDITOR`, `TEACHER` |

## Implementation Steps

1. UC-108, UC-109: actor chính `TEACHER`; xoá luồng/ngoại lệ nhắc Content Editor.
2. Content Editor còn lại: nhập dữ liệu, xem lỗi nhập, quản lý đề và ngân hàng câu hỏi (thêm/xoá/xếp câu vào đề — **không** sửa nội dung câu), cuộc thi, tài liệu tham khảo.
3. Ghi rõ ranh giới trong `business.md`:
   - Teacher: chất lượng học thuật (duyệt câu AI, sửa câu, chấm bài viết, sau này gắn nút cây)
   - Content Editor: vận hành dữ liệu (nhập, sắp xếp, xuất bản đề, cuộc thi, tài liệu, sau này nhập file cây)
4. `screen-fields.md` → sinh lại `screens.json`.

## Success Criteria

- [ ] UC-108, UC-109 chỉ có `TEACHER`
- [ ] Không màn nào cho `CONTENT_EDITOR` sửa `questions.stem/options`

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| "Quản lý ngân hàng câu hỏi" của Content Editor chồng với "sửa câu" | Định nghĩa: quản lý = trạng thái, xếp vào đề, lưu trữ; sửa nội dung = Teacher |

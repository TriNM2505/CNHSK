---
phase: 7
title: "Khoa spec va ban giao Codex"
status: pending
priority: P1
effort: "0.5 ngay/spec + thoi gian Codex"
dependencies: [3, 4, 5, 6]
---

# Phase 7: Khoá spec và bàn giao Codex

## Overview

Chạy **cho từng spec**: khoá (lock ceremony theo sách bước 1.10) → giao Codex theo task → review → merge.
Thứ tự: 000 → 001 → 008 → 002 → 005 → 004 → 003 → 006a → 006b.
<!-- Updated: Validation Session 1 - thêm 008, tách 006a/006b, cả nhóm duyệt (V7), Codex CLI cục bộ (V8) -->

## Requirements

### Khoá spec

- **Cả nhóm duyệt** (V7): buổi review 30–45 phút/spec theo 3 câu hỏi của sách (agent code được không cần hỏi? làm đúng 100% thì người dùng hài lòng? mâu thuẫn spec khác?); mọi thành viên ghi tên vào `Approved by`.
- Header: `Version 1.0.0 · Status APPROVED · Approved by · Locked on`.
- Commit `spec(<id>): approve v1.0.0`, tag `spec/<id>/v1.0.0`.
- **Sau khoá không sửa spec trong lúc Codex làm.** Phát sinh → `specs/<id>/addendum-<n>.md` hoặc mở lại version 1.1.0 (dừng Codex).

### Giao Codex (mỗi task trong `tasks.md`)

Mẫu prompt cố định, lưu `docs/agent/prompt-template.md`:

Codex CLI chạy cục bộ (V8): trước mỗi task `git switch -c agent/<spec-id>-<task-id>` từ `main` mới nhất; xong mở PR.

```
Đọc AGENTS.md. Spec: specs/<id>/spec.md (v1.0.0, APPROVED). Task: <T-xx> trong specs/<id>/tasks.md.
1. Viết shadow plan (file đọc/sửa, lệnh chạy, kết quả mong đợi) vào mô tả PR.
2. Chỉ làm task này. Không sửa file ngoài danh sách của task.
3. Spec không nói → dừng, ghi câu hỏi vào specs/<id>/questions.md.
4. Xong khi: test của FR trong task pass, ./mvnw verify xanh (BE) hoặc npm run lint && tsc xanh (FE).
5. Commit: feat(<module>): <FR-ID> <mô tả>. Không nhắc AI trong commit.
```

### Review mỗi PR (người gác cổng)

| Lớp | Kiểm |
|---|---|
| 1 · CI | 3 workflow xanh |
| 2 · Khớp spec | Mỗi FR trong task có test tên chứa FR-ID; response khớp `openapi.yaml` |
| 3 · Luật | Không vi phạm `AGENTS.md` §5, §17 (không mastery ngoài cổng) |
| 4 · Khớp hệ thống | Người review: đặt đúng module/package, không trùng code đã có |

Lỗi lặp lại ≥ 2 lần cùng loại → sửa `AGENTS.md` hoặc spec ("Fix the spec, not the code"), không chỉ sửa code.
Codex kẹt > 3 lần một task → Escape Hatch: người code tay, ghi lý do vào `specs/<id>/plan.md`.

## Related Code Files

- Create: `docs/agent/prompt-template.md`, `specs/<id>/questions.md` (khi có)
- Modify: header các `spec.md`; tag git

## Implementation Steps

1. Spec 000: khoá → giao Codex → base chạy → mốc **"Architecture lock"** (sách tuần 4), tag `v0.1-base`.
2. Spec 001: khoá → Codex → mốc **"Auth working"**, tag `v0.2-auth`.
3. Spec 008: khoá (khi có mẫu file Học Bá) → Codex → nạp data thật; tag `v0.3-data-import`.
4. Spec 002, 005, 004, 003: khoá → Codex lần lượt; tag `v0.4-learning-modes` khi xong cả 4.
5. Spec 006a → 006b: khoá → Codex (006b có thể giao từng game); tag `v0.5-games`.
6. Sau mỗi spec: cập nhật `docs/reference` nếu code lộ ra chỗ tài liệu sai (ghi `DRIFT_LOG.md` như MoveHome).

## Success Criteria

- [ ] 9 spec (000, 001, 008, 002–005, 006a, 006b) có tag `spec/<id>/v1.0.0`
- [ ] 5 tag mốc `v0.1-base` → `v0.5-games`
- [ ] Coverage BE ≥ 80% tại mỗi mốc
- [ ] Không PR nào merge khi CI đỏ

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Codex sinh code lệch luật hàng loạt | Agent pass test ở phase 2; review lớp 3; sửa luật khi lỗi lặp |
| Sửa spec giữa chừng (Moving Target) | Luật khoá + addendum; tăng version thì dừng Codex |
| PR quá lớn khó review | Task ≤ 4 giờ, một task một PR |

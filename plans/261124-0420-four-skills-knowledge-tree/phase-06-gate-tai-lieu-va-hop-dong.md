---
phase: 6
title: Gate tai lieu va hop dong
status: in-progress
priority: P1
dependencies:
  - 1
  - 2
  - 3
  - 4
  - 5
effort: ~0.5 ngay
---

# Phase 6: Gate, tài liệu và hợp đồng

## Overview
Đóng toàn bộ: hợp đồng API, spec, test dồn từ đợt trước, gate đầy đủ, báo cáo cho leader.

## Requirements
- Hợp đồng phụ lục (không sửa openapi đã khoá): `openapi-knowledge-v1-2.yaml` (skills, tasks, error codes), `openapi-mastery-v1-1.yaml` (skills/domains), `openapi-import-v1-1.yaml` (mẫu mới), `openapi-grading-v1-1.yaml` (rubric, errors, taskId, mode/audioUrl, speaking), `openapi-trial.yaml`, `openapi-speaking.yaml`. <!-- Updated: Validation Session 1 - tên hợp đồng theo file đang có -->
- Spec cập nhật: 005 (tách Nghe; minh chứng NA), 010 (skills, tasks, error codes, importer), 011 (kỹ năng trên dashboard), 022 (rubric R-NOI/R-VIET, mã lỗi), 024 (prompt skills), 003/017 (hạn mức học), spec học thử (mới).
- Test dồn từ trước: `SettingKeyTest`, `SettingsAdminServiceTest` (setting học), `Layouts.test` + quiz/grading routes (sidebar), contract chấm bài, `ModuleBoundaryTest` (sửa luật bắt nhầm package `learning.leaderboard.community` hoặc đổi tên package), test hạn mức, Luyện nói, Học thử.
- Gate: toàn bộ FE (`tsc`, `eslint`, vitest với `VITE_GAME_ORIGIN=''`) + `mvnw verify`, chạy khi leader **không** chạy BE (hoặc worktree riêng để không ghi đè `target/`).

## Implementation Steps
1. Viết/ cập nhật contract phụ lục + contract test.
2. Cập nhật spec + `docs/reference/screen-fields.md` (SCR-016 tách, SCR-083 học thử, màn Viết).
3. Sửa test dồn; chạy gate đầy đủ; sửa đến xanh (không nới test).
4. Báo cáo leader: bảng thay đổi, migration V18 cần chạy, thứ tự nạp data pack, cách test tay, việc còn mở.
5. `ck plan check` các phase; journal.

## Success Criteria
- [ ] FE + BE gate xanh toàn bộ; số test ghi trong báo cáo.
- [ ] Contract phụ lục khớp API thật (contract test).
- [ ] Spec + screen-fields phản ánh đúng hành vi.

## Risk Assessment
- `mvnw verify` dài và đụng `target/` của BE leader → chạy ở worktree riêng.
- Test môi trường chập chờn: dùng danh sách ngoại lệ đã biết ([[environment-sensitive-tests]]).

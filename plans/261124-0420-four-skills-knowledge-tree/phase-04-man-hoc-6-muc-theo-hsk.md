---
phase: 4
title: Man hoc 6 muc theo HSK
status: completed
priority: P1
dependencies:
  - 1
  - 3
effort: ~1 ngay
---

# Phase 4: Màn học 6 mục theo HSK

## Overview
Sidebar trái 6 mục: **Nghe · Luyện nói · Chủ đề · Ngữ pháp · Chữ Hán · Viết**; mỗi trang có bộ chọn HSK 1/2/3; nội dung lấy theo KID.

## Requirements
- Routes: `/learn/listening` (8 tầng, lọc theo cấp của KID NA), `/learn/speaking` (nhiệm vụ Nói theo cấp + bài đọc theo KID NA/NL), `/learn/writing` (nhiệm vụ Viết theo cấp → gửi GV), giữ `/learn/topics`, `/learn/grammar`, `/learn/characters/*`. `/learn/pronunciation*` chuyển hướng sang `/learn/listening*`; `/learn/pronunciation/speaking` → `/learn/speaking`.
- Bộ chọn HSK dùng chung (`HskLevelPicker`), nhớ lựa chọn theo `user.hskLevel` + query `?hsk=`.
- Luyện nói: chọn **nhiệm vụ** (từ `learning_tasks` SPEAKING) hoặc **đọc theo âm** (từ/câu theo KID NA); gửi kèm `taskId`.
- Viết: danh sách nhiệm vụ WRITING theo cấp → soạn bài → gửi (dùng luồng chấm bài hiện có + `taskId`).
- Màn GV: form chấm **động theo rubric** (R-NOI 5 tiêu chí 0–3, R-VIET 4 tiêu chí 0–5, mô tả mức neo), **chọn mã lỗi** (lọc theo KID mục tiêu) + span; LEGACY chỉ xem.
- Màn kết quả học viên: hiện điểm từng tiêu chí theo rubric + mã lỗi kèm giải thích.
- Học thử (khách) giữ nguyên; tab Luyện nói khách dùng `/api/public/speaking/items`.

## Related Code Files
- Modify: `shared/layout/navigation.ts`, `LearnerLayout.tsx`, `app/routeAccess.ts`, `features/pronunciation/*` (tách 2 trang), `features/grading/*` (kết quả rubric), `features/teacher-grading/*` (form rubric + mã lỗi), `features/mastery/components/HskSkillsPanel.tsx` (link mới), `shared/layout/publicNavigation.ts`
- Create: `shared/ui/HskLevelPicker.tsx`, `features/writing/*` (trang Viết), `features/learning-tasks/api.ts`
- BE: `GET /api/learning-tasks?kind=&hskLevel=`, `GET /api/error-codes?kids=` (LEARNER/TEACHER), `GET /api/speaking/items` thêm `kid`

## Implementation Steps (TDD)
1. **Khoá cũ:** chạy `Layouts.test`, `routes.test`, test pronunciation/grading/teacher-grading; cập nhật test đang mong nhóm Cộng đồng/Trò chơi ở sidebar (đã bỏ) cho khớp hành vi đã duyệt.
2. **Test mới (đỏ):** sidebar có đúng 6 mục + thứ tự; redirect pronunciation→listening; HskLevelPicker đổi query; form GV R-NOI chỉ nhận 0–3, R-VIET 0–5; chọn mã lỗi trùng bị chặn; kết quả học viên render rubric + LEGACY.
3. BE endpoints tasks/error-codes + test controller/security.
4. Cài FE đến xanh; preview catalog cho màn mới; chụp `/__preview` 1440/390 kiểm tra.
5. Gate FE (tsc, eslint, vitest các feature đụng).

## Success Criteria
- [ ] Sidebar 6 mục, mỗi trang có HSK 1/2/3 và nội dung đổi theo cấp.
- [ ] GV chấm R-NOI/R-VIET + mã lỗi end-to-end (local).
- [ ] Link cũ `/learn/pronunciation` không gãy.

## Risk Assessment
- Đổi route làm gãy link trong thông báo/gợi ý → grep toàn bộ `'/learn/pronunciation'` và thay; giữ redirect.

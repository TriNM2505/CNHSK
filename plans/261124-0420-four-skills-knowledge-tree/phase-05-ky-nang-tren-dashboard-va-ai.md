---
phase: 5
title: Ky nang tren dashboard va AI
status: completed
priority: P2
dependencies:
  - 1
  - 3
effort: ~0.5-1 ngay
---

# Phase 5: Kỹ năng trên Bảng học tập và AI sinh câu

## Overview
Hiện năng lực theo **4 kỹ năng** và theo **miền** (đúng 5.11: "x/y nút đã học ở mức tốt", nút thiếu minh chứng màu xám), và cho AI sinh câu dùng `skills`, `pattern`, `examples`.

## Requirements
- API `GET /api/mastery/skills?hskLevel=` <!-- Updated: Validation Session 1 - đường dẫn thật /api/mastery --> → cho mỗi kỹ năng: số nút có skill đó (cấp ≤ chọn), số nút `consolidate|extend`, số nút thiếu minh chứng, % = trung bình mastery các nút có minh chứng (không tính nút xám).
- Theo miền: `GET /api/mastery/domains?hskLevel=` → "Ngữ pháp: 14/22 nút ở mức tốt".
- `HskSkillsPanel`: thêm thanh % + "x/y nút tốt", nút xám = "cần làm thêm bài"; đổi câu chú thích sang "Chuẩn HSK 3.0 cấp 1–3 yêu cầu đủ nghe, nói, đọc, viết."
- Gợi ý hôm nay ưu tiên nút thuộc kỹ năng yếu nhất (giữ thuật toán biên + PageRank, chỉ thêm tie-break theo kỹ năng).
- AI sinh câu (024): prompt thêm `skills`, `pattern`, 2 `examples_ok`, mã lỗi của nút; câu sinh ra gắn `skill`. Vẫn `PENDING_REVIEW` → GV duyệt (giữ nguyên).
- Không gọi cho khách; không ảnh hưởng hạn mức.

## Related Code Files
- Modify: `learning/mastery/MasteryQueryService.java`, `MasteryQueryRepository.java`, `web/MasteryController.java`, `RecommendationService.java`, `learning/aigen/AiPromptBuilder.java`, `AiGenerationStore.java`, `AiQuestionValidator.java`; FE `features/mastery/*`
- Create: views `SkillMastery`, `DomainMastery`

## Implementation Steps (TDD)
1. **Khoá cũ:** `MasteryQueryServiceTest`, `RecommendationServiceTest`, `AiPromptBuilderTest`, dashboard test (8 test hiện xanh).
2. **Test mới (đỏ):** gộp kỹ năng với nút nhiều skills (đếm cho mỗi skill); nút không minh chứng không kéo % xuống; tie-break kỹ năng; prompt chứa pattern/examples, không lộ đáp án mẫu sai; câu AI có `skill`.
3. Cài BE → FE đến xanh.
4. Gate module mastery + aigen + FE mastery.

## Success Criteria
- [ ] Sau 1 bài mỗi kỹ năng, dashboard hiện % khác 0 cho đúng kỹ năng đó.
- [ ] Không có con số "trung bình tổng" duy nhất.
- [ ] AI sinh câu chạy được cho nút có can_do + examples (thử tay với `system-user-id` + `min-wrong` thấp).

## Risk Assessment
- Truy vấn gộp chậm khi nhiều nút → 1 truy vấn GROUP BY `unnest(skills)`, có index `user_progress(user_id)`.
- Nhà cung cấp AI đang lỗi (AI chat) → kiểm tra khoá/base URL trước khi nghiệm thu phần AI.

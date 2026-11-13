---
phase: 3
title: "Spec 019 quiz theo chủ đề"
status: pending
priority: P2
dependencies: [1]
effort: "spec 1,5 · BE 4 · FE 3 ngày công"
owner: "Tường (sau 023)"
---

# Phase 3: Spec 019 — Quiz theo chủ đề

## Overview
Feature 5.2. Màn SCR-043 Quiz List, SCR-044 Quiz Attempt, SCR-045 Quiz Result. **Không thêm bảng.** SCR-042 hồ sơ cộng đồng chuyển sang phụ lục 009 (phase 12, Quốc Huy).

## Context links
- UC-080 Chơi quiz theo chủ đề, UC-081 Kết quả quiz và thứ hạng, UC-074 Theo dõi người dùng, UC-134 (use-cases-06)
- `screen-fields.md` SCR-042…045; `V1__core_schema.sql` `attempts` (+ `uq_quiz_first`), `V2__community.sql` `reactions`, `posts`
- Engine practice 003/012 (`PracticeApi`, `PracticeSource`), ngân hàng câu `questions` `APPROVED` + nhãn KID (010)
- 009 (`CommunityAuthors`, quyền tác giả), 001 (`AuthApi` tên hiển thị)

## Thiết kế đề xuất
- **Quiz = bộ câu sinh từ ngân hàng `APPROVED` theo chủ đề** (`topics` + `topic_items` → KID → câu), không có bảng quiz riêng. Ghi `attempts` kind `QUIZ`, `ref_type='QUIZ'`, `ref_id=topic_id` (H6). Lần đầu tính hạng (`uq_quiz_first`); lần sau là luyện, không tính hạng.
- Chạy qua engine practice với `PracticeSource.QUIZ` (⚿K1 enum chung, H7), item `QUESTION_CHOICE`; số câu, thời gian cấu hình `cnhsk.quiz.*`; server chấm (H9); **không** góp mastery (UC-080 BR-080-2, leader chốt 13/11); lượt làm lại dùng source `QUIZ_RETRY` (`kind='PRACTICE'`) vì `uq_quiz_first`; `ref_type` = `'QUIZ'` (tên source). <!-- Updated: viết spec 019 13/11 -->
- Kết quả (045): điểm, đáp án đúng/sai từng câu, thứ hạng trong chủ đề lấy từ API của 017 **nếu đã merge**, chưa có thì ẩn khối hạng (không chờ 017).

## Requirements
- Functional: danh sách quiz theo HSK/chủ đề có trạng thái (chưa làm/đã làm/điểm cao nhất); làm bài; xem kết quả; tên người trong khối hạng link sang hồ sơ 042 (phụ lục 009).
- Non-functional: chặn trợ lý khi đang làm (`ActiveTestApi`); rate limit tạo lượt; chủ đề thiếu câu → mã lỗi riêng.

## Implementation Steps (viết spec)
1. §0 phạm vi + §0.4 file dùng chung (`PracticeSource`, `ErrorCode`, `SecurityConfig`, `routeAccess.ts`).
2. US/FR theo 3 màn; Edge: chủ đề < N câu, làm lại, bỏ dở (ABANDONED/EXPIRED).
3. Error matrix: dự kiến `QUIZ_NOT_ENOUGH_QUESTIONS` (đặt tên cuối khi viết).
4. API contract `contracts/openapi-quiz.yaml`: `GET /api/quizzes`, `POST /api/quizzes/{topicId}/attempts`, dùng lại endpoint trả lời của engine, `GET /api/quiz-attempts/{id}/result`.
5. Acceptance tests AT-xx cho từng FR; tasks.md tách BE/FE, đánh dấu task phụ thuộc ⚿K1.

## Success Criteria
- [ ] Không bảng mới; ghi `attempts` đúng H6 (đã đối chiếu với phase 7)
- [ ] Khối thứ hạng ở 045 không chặn bởi 017
- [ ] OpenAPI + AT đủ cho 3 màn

## Risk Assessment
- Ngân hàng câu theo chủ đề mỏng → tối thiểu N câu cấu hình được, lỗi rõ; nội dung bổ sung qua import 002/010.
- Đụng engine cùng 021 (`PracticeSource`) → enum gom một PR ⚿K1.

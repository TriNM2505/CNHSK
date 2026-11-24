---
phase: 1
title: "Cay skills nhiem vu ma loi va importer"
status: pending
priority: P1
dependencies: []
effort: "~1 ngay"
---

# Phase 1: Cây — skills, nhiệm vụ, mã lỗi, importer

## Overview
Bổ sung vào cây các thuộc tính v2.0 còn thiếu và cho bộ nạp SCR-068 nhận chúng. Không đổi hành vi học.

## Requirements
<!-- Updated: Validation Session 1 - mã skill tiếng Anh, CHECK SPEAKING, AI draft source -->
- `knowledge_points`: `skills varchar(10)[]` (LISTENING/SPEAKING/READING/WRITING), `cluster`, `pattern`, `examples jsonb` ({ok:[{cn,pinyin,vi}], err:[{cn,error_code}]}), `teaching_note`, `source`. Giữ `skill_type` cũ (đọc được, không dùng cho báo cáo mới).
- `questions.skill varchar(10)` nullable (LISTENING/SPEAKING/READING/WRITING).
- Mở CHECK `attempt_items_skill_check` thêm `SPEAKING` (dòng bài làm rubric Nói).
- Bảng `learning.error_codes(code PK 'E-NP-07', kid FK knowledge_points, name_vi, description, hsk_level)`.
- Bảng `learning.learning_tasks(id, code UNIQUE, kind SPEAKING|WRITING, hsk_level, nl_kid, prompt_vi, prompt_zh, min_chars, max_seconds, status)` + `learning.learning_task_targets(task_id, kid, criterion C1..C5)`; ràng buộc: mỗi nhiệm vụ đúng 1 `nl_kid` miền NL.
- Gắn đơn vị phát âm → NA: thêm `kid` vào phần tử `pron_stages.units` (JSON) qua CONTENT_KID `target_type=PRON_UNIT`.
- Importer: KNOWLEDGE_NODE thêm cột `skills, cluster, pattern, examples_ok, examples_err, teaching_note, source`; QUESTION thêm `skill`; mẫu mới `ERROR_CODE`, `LEARNING_TASK`; QUESTION thêm tuỳ chọn `origin=AI_DRAFT` → lưu `source='AI'`, `status='PENDING_REVIEW'` để hiện ở hàng `/content/ai-review` (giữ `code` để nạp lại được). LEARNING_TASK và ví dụ ngữ pháp nạp APPROVED (đã duyệt trên file).

## Architecture
- Migration `V18__knowledge_skills_tasks_error_codes.sql` (chỉ ADD COLUMN/CREATE TABLE; CHECK cho giá trị skills).
- `KnowledgeSkill` enum mới (LISTENING, SPEAKING, READING, WRITING) trong `learning.knowledge.entity`; `KnowledgeTreeService.KnowledgeNode` mang `skills`.
- Importer theo mẫu sẵn có: `ImportDataType` + `ImportTemplate` ColumnSpec + `*RowValidator` + `*RowWriter` + `ImportChangePlanner`.

## Related Code Files
- Create: `src/main/resources/db/migration/V18__knowledge_skills_tasks_error_codes.sql`, `learning/knowledge/entity/KnowledgeSkill.java`, `learning/task/*` (entity/repo), `content/importer/validate/ErrorCodeRowValidator.java`, `LearningTaskRowValidator.java`, `write/ErrorCodeRowWriter.java`, `LearningTaskRowWriter.java`
- Modify: `KnowledgeNodeRowValidator/Writer`, `QuestionRowWriter`, `QuestionImportSupport`, `ImportTemplate`, `ImportDataType`, `ImportChangePlanner`, `KnowledgeTreeService`, `KnowledgeTreeQueryRepository`, `PronUnitRowWriter` (giữ kid)
- FE: `features/content-import` (mẫu mới hiển thị), `features/question-bank/types.ts` (skills)

## Implementation Steps (TDD)
1. **Test khoá hành vi cũ:** chạy và ghi lại kết quả test importer hiện có (KnowledgeNode, Question, ContentKid, PronUnit) + `KnowledgeTreeServiceTest`.
2. **Test mới (đỏ):** validator nhận/từ chối `skills` (giá trị lạ, trùng), `examples_ok` sai JSON, ERROR_CODE trỏ KID không tồn tại, LEARNING_TASK thiếu `nl_kid` hoặc `nl_kid` không thuộc NL, CONTENT_KID PRON_UNIT → kid không thuộc NA bị từ chối; QUESTION `skill` lạ bị từ chối; `origin=AI_DRAFT` → câu `source=AI`, `PENDING_REVIEW` và xuất hiện trong `AiReviewService` list; V18 cho phép `attempt_items.skill='SPEAKING'`.
3. Viết migration V18; kiểm SQL trong container `postgres:18-alpine` chạy toàn bộ V1–V18.
4. Cài validator/writer cho đến khi test xanh; template CSV mẫu tải được từ SCR-068.
5. `KnowledgeTreeService.nodes()` trả `skills`; test.
6. Gate: test module learning content/knowledge + ModuleBoundaryTest (ghi rõ lỗi có sẵn của `learning.leaderboard.community`).

## Success Criteria
- [ ] V18 chạy sạch trên Postgres sạch (V1→V18).
- [ ] Dry-run SCR-068 cho 4 mẫu mới/đổi không lỗi với file mẫu.
- [ ] Test cũ importer/cây vẫn xanh; test mới xanh.

## Risk Assessment
- Migration chạy trên Neon khi bật BE → báo leader trước; chỉ thêm, không phá.
- `pron_stages.units` là JSON: sửa writer phải giữ nguyên các khoá cũ (test round-trip).

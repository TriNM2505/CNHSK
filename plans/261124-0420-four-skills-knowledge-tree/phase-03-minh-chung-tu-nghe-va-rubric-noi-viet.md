---
phase: 3
title: Minh chung tu nghe va rubric noi viet
status: completed
priority: P1
dependencies:
  - 1
effort: ~1.5 ngay
---

# Phase 3: Minh chứng từ Nghe và rubric Nói/Viết

## Overview
Cho 3 kỹ năng hiện "không vào cây" sinh minh chứng đúng KID: Nghe (8 tầng), Nói (R-NOI), Viết (R-VIET) + mã lỗi.

## Requirements
- **Nghe:** `PRON_STAGE` sinh minh chứng ngữ cảnh PRACTICE cho KID của đơn vị (từ phase 1); đơn vị chưa gắn KID → bỏ qua, log.
- **Rubric:** chấm bài có `rubric` = `R_NOI` | `R_VIET` | `LEGACY`.
  - R-NOI: C1..C5 thang 0–3; R-VIET: C1..C4 thang 0–5 (mức 0–5 nguyên).
  - Điểm hiển thị /100 = trung bình tiêu chí / thang × 100 (làm tròn).
  - Bài cũ (4×25) giữ nguyên, `rubric=LEGACY`, không sinh minh chứng mới.
- **Nhiệm vụ:** bài nói/viết gửi kèm `taskId`; bài viết tự do cũ (không task) vẫn gửi được, rubric R_VIET, KID mục tiêu rỗng → chỉ mã lỗi sinh minh chứng.
- **Mã lỗi:** GV chọn 0..n mã lỗi (mỗi mã ≤ 1 lần/bài) kèm đoạn bằng chứng (span ≤ 200 ký tự).
- **Quy đổi minh chứng (phụ lục E.3):** C1, C5 (trung bình) → nút NL, trọng số vai 1.0; C2 → KID tiêu chí C2 (vai phụ 0.5, chỉ khi có audio); C3 → TV; C4 → NP (vai phụ 0.5); mỗi mã lỗi → minh chứng 0 điểm vai 1.0 cho KID của mã. Bối cảnh **`GRADED` = 0.6 (đã có)**, dạng **`OPEN` = 1.0 (đã có)**. <!-- Updated: Validation Session 1 - dùng lại GRADED/OPEN -->
- Bài viết tự do (không nhiệm vụ): chấm R-VIET; chỉ mã lỗi sinh minh chứng.
- Bài nói của học thử/khách: không có (đã không gửi được).

## Architecture
- Minh chứng được dựng từ `attempt_items` (hiện `score = correct ? 100 : 0`, `EvidenceAllocator.java:125`) → khi GV hoàn thành chấm, ghi **một attempt** nguồn mới `RUBRIC_TASK` qua `PracticeService.recordAndFinish`; mỗi item (một KID × tiêu chí/mã lỗi) mang `meta.targetKid`, `meta.role` (PRIMARY/SECONDARY/ERROR), **`meta.score` 0–100**; `skill` = SPEAKING (Nói) / WRITING (Viết).
- `EvidenceAllocator`: `contextOf("RUBRIC_TASK") = "GRADED"`; nhánh mới: item có `meta.targetKid` → dùng trực tiếp KID + `meta.score` (không qua question/lexeme), form `OPEN`. Không thêm trọng số mới. <!-- Updated: Validation Session 1 - meta.score, GRADED, OPEN -->
- `contextOf("PRON_STAGE")` → `"PRACTICE"`; item phát âm mang `meta.targetKid` từ unit.
- Grading: `GradingResultWrite` v2 `{rubric, criteria:{C1..}, feedback, corrections, errors:[{code, span}]}`; parser chấp nhận cả dạng cũ (LEGACY) để không vỡ client cũ trong lúc chuyển.
- Quota nhiệm vụ: Luyện nói giữ PRONUNCIATION; Viết theo nhiệm vụ dùng GRADING (như cũ).

## Related Code Files
- Modify: `learning/mastery/EvidenceAllocator.java`, `MasteryRebuilder.java`, `learning/practice/PracticeSource.java` (+`RUBRIC_TASK`), `learning/pronunciation/PronStageHandler.java`, `learning/grading/TeacherGradingService.java`, `GradingRequestService.java`, `web/GradingResultWrite.java`, `web/GradingRequestViews.java`, `web/TeacherGradingViews.java`, `learning/speaking/SpeakingService.java` (taskId)
- Create: `learning/grading/RubricScorer.java` (quy /100 + phân bổ KID, thuần, dễ test), `learning/grading/RubricEvidenceRecorder.java`
- Không cần migration riêng: rubric nằm trong `service_requests.result` JSON; `attempts.ref_type` không có CHECK (đã kiểm); SPEAKING cho `attempt_items.skill` đã mở ở V18 (phase 1). <!-- Updated: Validation Session 1 - bỏ V19 -->

## Implementation Steps (TDD)
1. **Khoá hành vi cũ:** test chấm bài viết 4×25 hiện có, `EvidenceAllocatorTest`, `MasteryUpdater` test, `PronStage` test — chạy xanh, giữ nguyên.
2. **Test mới (đỏ):**
   - `RubricScorerTest`: R-NOI (2,2,2,2,2) → 67/100; R-VIET biên 0 và 5; phân bổ đúng bảng E.3 (NL3-006 66.7 w1.0; NA1-006/NA1-008/TV/NP 66.7 w0.5; E-NP-06 → NP2-013 0 w1.0).
   - Không audio → không sinh minh chứng C2.
   - `TeacherGradingServiceTest`: complete R_NOI ghi attempt `RUBRIC_TASK` + mastery cập nhật đúng KID; mã lỗi trùng bị từ chối; code lạ 422.
   - `EvidenceAllocatorTest`: PRON_STAGE item có targetKid → minh chứng PRACTICE; không kid → bỏ qua.
   - Parser `GradingResultWrite`: LEGACY body cũ vẫn hợp lệ.
3. Cài `RubricScorer`, `RubricEvidenceRecorder`, sửa allocator/config/handler đến xanh.
4. Kiểm `attempts.kind` của nguồn `RUBRIC_TASK` thuộc CHECK `attempts_kind_check` (dùng `PRACTICE`); test `MasteryRebuilder` dựng lại đúng từ `meta.score`.
5. Gate module learning (grading, mastery, pronunciation, speaking).

## Success Criteria
- [ ] Bảng E.3 tái hiện đúng trong test.
- [ ] Hoàn thành chấm R-NOI → `GET /api/mastery/nodes/{kid}` thấy KID mục tiêu có minh chứng.
- [ ] Làm hết 1 tầng nghe → KID NA có minh chứng.
- [ ] Bài 4×25 cũ hiển thị bình thường.

## Risk Assessment
- Thay đổi trọng số làm lệch mastery cũ → chỉ thêm context mới, không đổi trọng số cũ.
- Rebuild mastery (`MasteryRebuilder`) phải hiểu item rubric → test rebuild.

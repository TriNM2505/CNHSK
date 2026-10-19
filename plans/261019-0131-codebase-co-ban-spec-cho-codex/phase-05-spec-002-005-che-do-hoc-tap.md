---
phase: 5
title: "Spec 002-005 che do hoc tap"
status: pending
priority: P1
effort: "4 ngay (1 ngay/spec)"
dependencies: [3]
---

# Phase 5: Spec 002–005 — các chế độ học tập

## Overview

**Năm spec:** spec **008 nhập dữ liệu** (viết trước — đường duy nhất đưa data Học Bá vào, V5) và bốn spec chế độ học.
Chế độ học **chỉ** làm phần học và chấm; kết quả ghi qua `LearningResultRecorder`.
Mastery, mở khoá theo cây, lịch ôn **không** có trong các spec này.

### Spec 008 — Nhập dữ liệu học (SCR-068, SCR-069 · UC-110, UC-111 · actor `CONTENT_EDITOR`)

| Trong phạm vi | Ghi chú |
|---|---|
| Nhập file: từ/chữ (`lexemes`), chủ đề (`topics`, `topic_items`), điểm ngữ pháp, tầng phát âm (`pron_stages`) | Định dạng file theo mẫu Học Bá — **chặn khoá spec tới khi có mẫu** |
| Kiểm từng dòng (bắt buộc, độ dài, cấp HSK, trùng), nhập theo lô trong một transaction hoặc từng dòng hợp lệ (chốt trong spec) | |
| Báo cáo lỗi theo dòng/cột (SCR-069), tải lại file đã sửa | |
| Ghi `audit_logs`; **không** lưu file gốc vào repo; data không commit (LI-09) | |
| Ngoài phạm vi | Nhập đề thi/câu hỏi (đợt sau), nhập dữ liệu cây tri thức (plan cây) |

## Bốn spec

| Spec | Màn | Use case | Trong phạm vi | Để sau (cây tri thức) |
|---|---|---|---|---|
| **002 Từ vựng theo chủ đề** | SCR-011 Topic List · SCR-012 Topic Study | UC-025→029 | Danh sách chủ đề theo cấp; học từ (nghĩa, pinyin, âm Hán Việt, ví dụ, audio); luyện nhận diện, nghe trong chủ đề; bài test cuối chủ đề có điểm | Cổng 90% / mở khoá (UC-046), mastery (UC-042) → **mọi chủ đề mở**, sắp theo cấp + `order_index` |
| **003 Chữ Hán** | SCR-015 Recognition · SCR-014 Writing | UC-019, 020 · UC-015→018 | Nhận diện: chọn nghĩa, chọn chữ theo audio. Viết: 4 chế độ (theo nét, nhớ, thử thách, chính tả) bằng `hanzi-writer`, client chấm, server kiểm hợp lý (D3) | Trọng số mastery cho viết (BUS-16) |
| **004 Phát âm 8 tầng** | SCR-016 | UC-021 · UC-022 («include») | 8 tầng tuần tự, nghe và chọn; đạt ngưỡng tầng thì mở tầng sau (lưu `user_progress` `PRON_STAGE`) | Gắn tầng với nút NA |
| **005 Ngữ pháp** | SCR-017 List · SCR-018 Lesson | UC-023, 024 | Danh sách điểm ngữ pháp theo cấp; bài học (cấu trúc, ví dụ, câu sai thường gặp); bài luyện có chấm | Lịch ôn ngữ pháp (UC-024 «include» UC-044) |

Thứ tự viết: **008 → 002 → 005 → 004 → 003** (003 cuối vì cần `hanzi-writer` được duyệt ở D5).

## Requirements chung cho 4 spec

- Đủ chuẩn 15 mục của plan.
- Server chấm mọi câu (`BUS-09`): client gửi đáp án, server trả đúng/sai — **trừ** viết chữ (client chấm, server kiểm thời gian, số nét, tỉ lệ hợp lý).
- Mỗi lượt học/luyện/test: gọi `LearningResultRecorder.record(...)` với `kind` = `PRACTICE` | `TOPIC_TEST` | `WRITING_CHALLENGE`; item mang `lexemeId` hoặc `questionId`.
- Dữ liệu: đọc `lexemes`, `topics`, `topic_items`, `pron_stages`, `questions`; **không** dùng `knowledge_points`.
- Mục "Hook cây tri thức" trong mỗi spec: ghi rõ chỗ nào sau này cây tri thức sẽ cắm vào (vd "Topic List: trạng thái khoá/mở lấy từ `FrontierService` — hiện trả 'mở' cho mọi chủ đề").
- **Không seed dữ liệu** (V4). Test dùng fixture tạo trong code test; demo chờ data Học Bá nạp qua spec 008.

## Related Code Files

- Create: `specs/008-learning-data-import/`, `specs/002-vocab-topic/`, `specs/003-hanzi-recognition-writing/`, `specs/004-pronunciation-stages/`, `specs/005-grammar/` (mỗi thư mục: `spec.md`, `plan.md`, `tasks.md`, `checklists/`)
- Modify: `docs/api/openapi.yaml`

## Implementation Steps

1. Với mỗi spec: `/speckit-specify` từ use case + `screen-fields.md` (SCR tương ứng) + HTML mẫu `cnhsk-mockup-79/screens/`.
2. Bỏ mọi luồng mastery/mở khoá/lịch ôn khỏi use case khi chuyển thành FR; ghi vào mục "Hook cây tri thức".
3. Viết mục 12–15; `/speckit-plan`, `/speckit-tasks`, `/speckit-analyze`.
4. Review chéo → phase 7.

## Success Criteria

- [ ] 5 spec (008, 002–005) đủ 15 mục, không câu hỏi mở chặn
- [ ] Spec 008 có định dạng file khớp mẫu Học Bá và ma trận lỗi theo dòng/cột
- [ ] Không spec nào nhắc tính mastery, `next_review_at`, cổng 90%
- [ ] Mỗi spec có mục "Hook cây tri thức"
- [ ] Mọi endpoint chấm điểm ghi qua `LearningResultRecorder`

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Use case hiện trộn mastery vào luồng chính (UC-029 gọi UC-042, UC-046) | Spec ghi rõ «include» UC-042/046 là **no-op** đợt này, thay bằng `LearningResultRecorder` |
| Chưa có data Học Bá → không demo được chế độ học | Code và test vẫn xong bằng fixture; demo khi có data qua spec 008. Hỏi mẫu file sớm cùng lúc với data cây tri thức |
| Chưa có mẫu file → không khoá được spec 008 | Viết 008 phần không phụ thuộc định dạng trước (luồng, báo cáo lỗi, quyền); khoá khi có mẫu |
<!-- Updated: Validation Session 1 - thêm spec 008 (V5), bỏ seed dev (V4) -->
| `hanzi-writer` không được duyệt | Spec 003 chỉ làm nhận diện, viết chữ tách spec sau |

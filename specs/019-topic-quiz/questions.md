# Câu hỏi và quyết định — Spec #019

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Quiz có cập nhật mastery không (UC-080 BR-080-2: không) | Không, theo UC | ✅ Chốt |
| Q2 | Bộ câu mỗi lượt | Ngẫu nhiên từ kho APPROVED của KID chủ đề, cân theo dạng câu | ✅ Chốt |
| Q3 | Số câu và ngưỡng | 10 câu; ẩn quiz nếu < 10 câu; < 2 giây/câu → nghi vấn; chỉnh ở 078 | ✅ Chốt |
| Q4 | Module | `learning` (dùng engine trực tiếp) thay vì `community` như UC | ✅ Leader chốt 2026-11-13 |
| Q5 | Chặn trợ lý | Trong lượt tính hạng; không chặn lượt làm lại | ✅ Leader chốt 2026-11-13 |
| Q6 | Lượt đầu bị nghi vấn | Vẫn chiếm "lần đầu", không làm lại để tính hạng | ✅ Leader chốt 2026-11-13 |

## Ghi chú sửa plan
- Plan phase 3 cũ ghi quiz "góp minh chứng mastery" → **sai so với BR-080-2**; spec theo UC (Q1).
- Spec 017 đã sửa `ref_type` lượt quiz thành `'QUIZ'` (engine ghi `ref_type = PracticeSource.name()`).

## Dữ kiện đã kiểm trên main `2735ac8`
- `PracticeService` tạo `Attempt(userId, source.kind().name(), source.name(), refId, …)` → `ref_type` = tên source.
- `AttemptKind` đã có `QUIZ`; `PracticeSource` chưa có `QUIZ`.
- `uq_quiz_first(user_id, ref_id) WHERE kind='QUIZ' AND status='SUBMITTED'`.
- `EvidenceAllocator.contextOf` mặc định `null` + log WARN cho source lạ.
- `AttemptRepository.existsActiveTest` hiện xét `MOCK_EXAM`, `PLACEMENT`, `TOPIC_TEST`.
- `topics.knowledge_point_id` (V5), `question_knowledge_points.role` PRIMARY/SECONDARY.

# Câu hỏi và quyết định — Spec #024

## 2026-11-13 · Leader validate plan

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| V1 | Ai kích hoạt sinh câu | Hệ thống tự đánh giá câu sai, sinh theo dữ liệu cây tri thức; không học viên bấm, không thu phí | ✅ Chốt |
| V2 | Câu AI tới học viên khi nào | Phải duyệt và gắn KID trước | ✅ Chốt |

## 2026-11-14 · Leader chốt khi viết spec

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | `service_requests.user_id` bắt buộc mà job không có người yêu cầu (câu mở #6 của plan) | Khoá `ai-generate.system-user-id` ở 078, mặc định 0 = job tắt; phải là `ADMIN` hợp lệ | ✅ Chốt |
| Q2 | Ngưỡng, nhịp, dạng câu | 02:00 hằng ngày; cửa sổ 14 ngày; ≥ 20 lượt sai; < 10 câu `APPROVED`; lô 5 câu trắc nghiệm 4 phương án chỉ chữ; ≤ 3 lô/lần; model Sonnet riêng (`cnhsk.ai.generate-model`) | ✅ Chốt |
| Q3 | Ai duyệt/sửa | `TEACHER` hoặc `CONTENT_EDITOR` (BR-108-1, BR-109-1); `ADMIN` không duyệt | ✅ Theo UC |
| Q4 | Duyệt hàng loạt | Chỉ câu người đó đã mở (dấu Redis 24 giờ, BR-108-7) | ✅ Chốt |

## Dữ kiện đã kiểm trên main `f02fc0a`

- `questions`: `source` `IMPORT/AI/MANUAL`, `status` mặc định `PENDING_REVIEW`, `ck_q_ai_review`, `ck_q_reject`, `ix_q_review`, `created_by_request_id`, `code` (bắt buộc chỉ với `IMPORT`), `difficulty`.
- `question_knowledge_points.role` + `uq_qkp_primary`; `options` có `error_kid`, `error_note` (010 Q7, chỉ đọc trong Java).
- `service_requests` `kind='AI_GENERATE'`; `uq_ai_running` một lô `PENDING/RUNNING` mỗi người.
- `ClaudeClient.complete(model, systemBlocks, messages, maxTokens, timeout)` trả token vào/ra; `TokenBudget.execute`; `StubClaudeClient`.
- `/api/admin/questions`: `GET` cho `CONTENT_EDITOR`/`TEACHER`, `PUT` chỉ `TEACHER`, `PATCH …/archive` cho `CONTENT_EDITOR`.

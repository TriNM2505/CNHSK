# Câu hỏi và quyết định — Spec #018

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | 1 bảng gộp hay 2 bảng `media_channels` + `books` (feature-tree) | 1 bảng `reference_items` có cột `kind` | ✅ Chốt |
| Q2 | Danh sách domain cho phép đặt ở đâu | Cố định trong `application.yaml` theo loại | ✅ Chốt |
| Q3 | Lưu báo link chết | Chỉ ghi `audit_logs`; SCR-072 đếm qua `ix_audit_target` | ✅ Chốt |
| Q4 | Ai được báo lỗi | Chỉ `LEARNER` (chống spam) | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q5 | Danh sách domain ban đầu (spec §0.2) | Leader rà | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q6 | Hiện lại mục đã ẩn | Về `DRAFT`, phải công bố lại | ✅ Leader duyệt cùng spec 2026-11-13 |

## Dữ kiện đã kiểm trên main `2735ac8`
- Chưa có bảng tài nguyên tham khảo trong migration; `feature-tree` ghi `media_channels`, `books` (chưa tạo).
- `shared.audit_logs(actor_id, action, target_type, target_id, detail JSONB, ip_address, created_at)`; index `ix_audit_target(target_type, target_id)`, `ix_audit_action`.
- `AuditService` có `record`, `recordDetail` — chưa có hàm đếm.
- SCR-072 actor `CONTENT_EDITOR` (screen-fields dòng 85, 515).

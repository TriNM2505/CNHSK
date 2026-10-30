# Quyết định triển khai Spec-009

## K1-A — Nguồn PostDetail cho MODERATOR trên SCR-040

- Ngày chốt: 2026-10-30.
- Status: CHỐT / APPROVED BY LEADER.
- Vấn đề: nguồn PostDetail cho MODERATOR khi mở SCR-040.
- Decision: SCR-040 dùng public PostDetail cho mọi actor; chỉ bài APPROVED.
- MODERATOR dùng moderation-comments sau khi public detail APPROVED tải thành công.
- Bài ở trạng thái khác được xem và kiểm duyệt tại SCR-061 (T-18).
- FR-063 moderation-comments vẫn hỗ trợ mọi trạng thái bài ở backend; SCR-040 chỉ sử dụng cho bài APPROVED.
- Không thay OpenAPI; không fallback public 404 sang moderation detail.

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

## T-18 — Triển khai các màn kiểm duyệt

- Ngày chốt: 2026-10-30.
- Status: CHỐT / APPROVED BY LEADER.
- Triển khai SCR-060/061/062/063 theo Shadow Plan v2.
- SCR-061 có moderation-comments cho mọi trạng thái bài theo khả năng backend.
- SCR-063 có panel direct comment moderation mở theo yêu cầu, lấy metadata bằng API moderation-comments hiện có.
- Direct moderation không tự đóng report case, không gọi `decideReportCase` ngầm.
- Không bổ sung route/API ngoài scope; ngoại lệ route chỉ áp dụng khi thực sự thiếu route.
- Không sửa `screen-fields.md`; không thay locked OpenAPI hoặc spec.
- Sau T-18 delivery: FE Visual/Functional QA tại 360/768/1280 px trước khi Shadow Plan backend.
- PR #20 tiếp tục DRAFT.

## T-18 / SessionStorage lifecycle relaxation — CHỐT 2026-10-30

Status: CHỐT / APPROVED BY LEADER.

1. Leader REJECT thay đổi `RouteScaffolding.tsx` và mọi auth observer ngoài feature.
2. Chỉ lưu văn bản MODERATOR tự gõ, dưới dạng `text` và `savedAt`; không lưu confirmation hoặc kết quả có thể replay mutation.
3. Storage key gồm namespace `cnhsk:spec009:t18:draft:` + userId + screenId + resourceId + field.
4. Chỉ đọc/khôi phục nháp khi component T-18 đang mount sau khi guard xác nhận MODERATOR, userId trùng và tài nguyên hợp lệ.
5. Auth loading/error: không khôi phục.
6. Dọn lười khi mount: xóa keys thuộc namespace T-18 của userId khác mà không đọc value của họ; xóa dữ liệu hỏng, thiếu/sai timestamp hoặc quá 2 giờ. TTL 7.200.000 ms được enforce khi đọc/mount; đúng 2 giờ đã hết hạn. Không có background timer tự xóa.
7. Không dùng `sessionStorage.clear()`; không xóa dữ liệu ngoài namespace T-18.
8. Xóa nháp tương ứng khi gửi quyết định thành công (trước navigation) hoặc bấm Hủy soạn nội dung. Mutation lỗi giữ nháp; đóng confirmation riêng không được coi là hủy toàn bộ nội dung.
9. Chấp nhận nháp có thể còn trong tab sau logout/mất role cho đến khi tab đóng hoặc lần dọn tiếp theo; không được hiển thị cho user khác. Cùng user đăng nhập lại trong cùng tab trước TTL có thể khôi phục nháp hợp lệ. Authorization luôn dựa vào auth hiện tại.
10. Không lưu API responses, nội dung bài/bình luận, danh tính người báo cáo, tokens, credentials hoặc errors. Storage không khả dụng thì form tiếp tục hoạt động bằng state trong memory.

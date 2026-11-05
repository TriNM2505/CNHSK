# Chốt — Spec 015

## 2026-11-06 — T-02 trạng thái panel (leader duyệt)

- Q-T02-01: Escape chỉ thu gọn panel, giữ chat, draft và request đang chạy, rồi trả focus về nút mở. «Cuộc trò chuyện mới» xoá chat, draft, lỗi và huỷ request như T-01.
- Q-T02-02: chọn thông báo theo mã lỗi (`ApiError.errorCode` của client hiện có). `RATE_LIMITED` → «Bạn đã hỏi nhiều trong hôm nay, quay lại ngày mai»; `ASSISTANT_BLOCKED_DURING_TEST` → «Trợ lý tắt trong lúc làm bài»; `ASSISTANT_UNAVAILABLE` → «Trợ lý tạm thời chưa trả lời được, bạn thử lại sau». `FORBIDDEN`, `CSRF_TOKEN_INVALID` và các mã khác giữ `ApiError.message`.
- Câu trả lời ngoài phạm vi có link «Tra từ điển» tới `/dictionary` và «Dịch câu» tới `/translate`. Cờ `outOfScope` chỉ thuộc tin nhắn hiển thị; history gửi lên vẫn chỉ gồm `role` và `text`.
- Câu trả lời hiển thị bằng chuỗi JSX, giữ xuống dòng và hiển thị nguyên văn các ký tự giống HTML.

## 2026-11-06 — T-01 nền frontend trợ lý (leader duyệt)

- Q-T01-01: lấy mã màn qua `matchRoutes` trên `appRouteDefinitions`, rồi kiểm mã có trong `screenRegistry`. Hai danh mục chỉ đọc. Không xác định được mã thì bỏ `pageContext`; request không gửi URL hay tham số route. Ẩn toàn bộ SCR-021 và nhánh `/learn/topics/:id/test`, kể cả query string và dấu `/` cuối.
- Q-T01-02: khi đóng chỉ có nút tròn nổi, nằm trên thanh điều hướng mobile; vùng nội dung giữ đủ bề rộng và có khoảng trống cuối để cuộn CTA ra khỏi nút. Chỉ khi mở mới có cột bên phải trên desktop, hoặc khối toàn chiều rộng trên mobile.
- Q-T01-03: câu trả lời được hiển thị đầy đủ. History gửi lại gồm tối đa 10 tin gần nhất, mỗi tin cắt còn 4.000 code point, giữ nguyên ký tự ngoài BMP.
- Chat chỉ nằm trong state của trang. Vào bài kiểm tra, đổi tài khoản, đăng xuất hoặc mở cuộc trò chuyện mới sẽ xoá phiên chat tương ứng, huỷ request và loại bỏ phản hồi đến muộn. Thu gọn giữ nội dung.
- T-01 cung cấp khung panel và luồng gửi tối thiểu. Các hành động riêng của `outOfScope`, thông báo 429/403/503 và Escape thuộc T-02.

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md`, constitution v1.4.1 (BUS-03, BUS-06, AC-09, BUS-10), migration V1 (`ai_chats`), UC-085/UC-086 (`use-cases-06`), `screen-fields.md` (đoạn "AI Assistant là panel dùng chung", Open Issues SCR-021), `CONTEXT.md` (Lượt), plan `261102-2104` phase 7 (red-team #9 đã chỉnh), spec 012/013/014 (bản trên máy).

Quyết định có từ trước (leader, 2026-11-02): trợ lý dùng Claude, **chỉ trả lời thắc mắc về hệ thống**, không trả lời kiến thức học; học viên **hỏi thoải mái**.

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Nguồn trả lời | **Nạp toàn bộ tài liệu hướng dẫn + prompt caching** |
| Q2 | Khi làm bài | *"Không có trợ lý ảo khi thi, thi nghiêm túc"* → ẩn ở mọi bài kiểm tra (đề thi thử, xếp trình độ, kiểm tra chủ đề) **và chặn ở server** |
| Q3 | Lịch sử chat | **Không lưu** |
| Q4 | Ai dùng | **Chỉ LEARNER** |
| Q5 | Model | **`claude-haiku-5-5`** |
| D | Mặc định | **Duyệt**: chỉ hệ thống, từ chối kiến thức bằng câu mẫu + gợi ý tra cứu/dịch; 200 câu/người/ngày, không trừ lượt; ≤ 2 000 ký tự; che email/số điện thoại; client gửi ≤ 10 tin; `page_context` chỉ mã màn; trần token tháng; `ClaudeClient` dùng chung với 014; viết tài liệu hướng dẫn; không migration; FE trước; chỉ file trên máy |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| A1 | UC-085: hỏi từ vựng, chữ Hán, ngữ pháp | Chỉ hỗ trợ hệ thống — **RFC** |
| A2 | UC-085 BR-085-2/4, CONTEXT "Lượt" gồm trợ lý | Không trừ lượt, ngưỡng 200/ngày — **RFC** |
| A3 | `ai_chats.messages` JSONB ghi nhiều lần (AC-09) | Không phát sinh (Q3) |
| A4 | UC-086 xoá hẳn phiên ↔ `BUS-10` xoá mềm | Không phát sinh (Q3) |
| A5 | Plan phase 7: lưu `ai_chats` 90 ngày; tài liệu ở `docs/help/` | Q3; tài liệu ở `src/main/resources/help/` để đóng gói — sửa plan |
| A6 | BR-085-10: chưa cần chặn ở luyện thi practice | Q2: chặn (leader muốn thi nghiêm túc) |

### Còn mở

- OQ-1: giá trị `cnhsk.ai.monthly-token-budget` (mặc định 2 000 000) — leader chỉnh theo ngân sách.
- Kết quả bộ 20 câu kiểm thử (SC-002) — ghi vào đây trước demo.
- Plan phase 7 cần sửa theo Q3, A5 — hỏi leader trước khi sửa.

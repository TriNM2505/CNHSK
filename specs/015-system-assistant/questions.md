# Chốt — Spec 015

## 2026-11-06 — T-04 nền client dùng chung (leader duyệt)

- Maven Central tại thời điểm kiểm tra 2026-11-05 21:15 UTC: bản ổn định mới nhất đã đủ 14 ngày là `com.anthropic:anthropic-java:3.1.0`, phát hành trên Central lúc 2026-10-21 21:58 UTC. `3.2.0` (2026-10-23 13:17 UTC) và các bản sau chưa đủ tuổi.
- Đã đọc sources JAR 3.1.0 từ Central: có timeout theo request qua `RequestOptions`, `maxRetries`, system block có `cacheControl`, usage có input/output và hai trường token cache.
- Tương thích classpath: SDK kéo `swagger-annotations:2.2.50`, trùng các lớp của `swagger-annotations-jakarta:2.2.47` đang do Springdoc quản lý. Loại dependency `swagger-annotations` từ SDK trong pom.xml, giữ SDK 3.1.0 và nguyên phiên bản Springdoc/Jakarta của dự án. Test OpenAPI bình luận lỗi response 204 có content trước khi loại, đạt sau khi loại; không sửa test/controller bình luận. Logging request/response của SDK luôn OFF, kể cả khi môi trường bật debug.
- Q-T04-01: kiểm ngân sách trước, cộng usage thực sau, không đặt chỗ. Mức vượt tối đa bằng usage của các request đang chạy song song; cộng bằng Redis INCRBY nguyên tử. Đã dùng đúng bằng trần thì không gọi client.
- Q-T04-02: tháng tính theo Asia/Ho_Chi_Minh từ Clock. Ghi usage vào tháng lúc bắt đầu kiểm, kể cả khi response về sang tháng sau. Key giữ tới đầu tháng kế tiếp + 24 giờ. Redis lỗi thì cho qua, chỉ WARN cố định, không ghi nguyên nhân có thể chứa dữ liệu nhạy cảm.
- Q-T04-03: inputTokens = input_tokens + cache_creation_input_tokens + cache_read_input_tokens; trường null tính 0. Ngân sách cộng inputTokens + output_tokens. Cách hiểu dùng chung cho 014 và 015.
- Q-T04-04 (thay thế): Bỏ temperature do API 2026-10-21 không nhận trường này. Spec 014 §0.3 yêu cầu temperature = 0; yêu cầu này không áp dụng được, 014/T-14 giữ ổn định bằng system prompt.
- Q-T04-05: leader duyệt SDK 3.1.0 và không gửi temperature; giữ model claude-haiku-5-5 và chữ ký public. Có test request không chứa trường này. maxRetries(0), timeout theo từng request. SDK chỉ tạo khi có khoá và phát sinh lời gọi; profile test chỉ dùng StubClaudeClient, không gửi HTTP ra ngoài.
- Chữ ký dùng chung: ClaudeClient.complete(String model, List<SystemBlock> systemBlocks, List<ChatMessage> messages, int maxTokens, Duration timeout) → Completion(text, inputTokens, outputTokens); SystemBlock(text, cache), ChatMessage(role, text), Role USER/ASSISTANT. AiUnavailableException.Reason: NOT_CONFIGURED, TIMEOUT, UPSTREAM_ERROR, BUDGET_EXCEEDED. TokenBudget.execute(Supplier<Completion>) kiểm và cộng usage, dùng cho cả 014/015. AiConfig: assistantModel, translateModel, timeout, monthlyTokenBudget; giá trị được kiểm khi khởi động, khoá API không thuộc validation.
- Nguồn phiên bản: https://repo.maven.apache.org/maven2/com/anthropic/anthropic-java/
- Nguồn kiểm API của SDK: https://repo.maven.apache.org/maven2/com/anthropic/anthropic-java-core/3.1.0/anthropic-java-core-3.1.0-sources.jar
- Nguồn thay đổi tham số: https://platform.claude.com/docs/en/about-claude/model-deprecations#api-parameter-deprecations

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

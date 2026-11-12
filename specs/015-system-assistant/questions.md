# Chốt — Spec 015

## 2026-11-12 — T-03 hướng dẫn học viên, G1

- Base thực hiện: `234567758185ef39e2e8952d00c4dde2611a4d36` (`2345677`), nhánh `spec/015-t03`.
  Trước khi cập nhật, `git log --oneline 09ea06a..HEAD` rỗng. Leader cho phép đúng lần này
  dùng `git checkout -B spec/015-t03 origin/main`; không merge/rebase/pull main.
- Q-T03-01: leader duyệt 15 file, gồm 12 file help, bộ 20 câu, questions.md và tasks.md.
  Trong tasks.md chỉ đổi dòng T-03 và dòng Hiện trạng.
- Q-T03-02: file 09 giữ «Tính năng đang hoàn thiện» vì chưa kiểm được ví/lịch sử thật.
  Đính chính sau của leader: file 07 mô tả ba màn thật của spec 011 trên `2345677`,
  không giữ câu «Tính năng đang hoàn thiện».
- Q-T03-03: đánh dấu T-03 khi đối chiếu UI và gate đạt. Bộ 20 câu chạy Claude thật thuộc T-07,
  chỉ leader chạy; T-03 chưa có kết quả SC-002.
- Chỉ hướng dẫn LEARNER. Nội dung help không có màn quản trị/kiểm duyệt, route, URL,
  email, tên người thật hoặc chỉ dẫn điều khiển trợ lý. Mỗi file có Câu hỏi thường gặp.
- Đối chiếu tên: FE pageContext.ts gửi mã SCR; AssistantPromptBuilder đổi mã sang tên tiếng Việt.
  SCR-010 được gọi là «Trang học tập» trong map, tiêu đề UI là «Học tập».
  SCR-019 là «Cây năng lực» trong map, tiêu đề UI là «Tiến độ học tập».
  SCR-013 là «Ôn tập đến hạn» trong map, tiêu đề UI là «Ôn tập».
  File 07 ghi cả tên trong map và tiêu đề đang thấy, không thay đổi code tên màn.

### Môi trường và giới hạn bằng chứng

PostgreSQL 18, Redis và Mailpit chạy local trong Docker. Tài khoản nhập demo và học viên
được tạo riêng trên DB local; tài khoản dùng để kiểm màn chỉ có LEARNER. Không dùng Neon.
Đã nhập lại 10 CSV demo theo API nhập/xác nhận, mỗi loại upload 201, confirm 200, COMPLETED,
0 dòng lỗi: LEXEME, TOPIC, TOPIC_ITEM, GRAMMAR, GRAMMAR_EXERCISE, PRON_STAGE,
KNOWLEDGE_NODE, QUESTION, EXAM và EXAM_QUESTION. PRON_UNIT trong demo chưa có audio,
không nhập file thiếu audio và không khẳng định đã kiểm được bài nghe đầy đủ.

Chrome mở page thật, không mock API và không dùng catalog.preview. Kiểm màn bằng bearer
được cấp qua API đăng nhập MOBILE của tài khoản demo. Lượt thử cookie local qua công cụ
API đã trả CSRF_TOKEN_INVALID; bằng chứng ở đây không khẳng định nghiệm thu luồng cookie.
Không gọi Claude thật. Không lưu ảnh vào PR. Bằng chứng chữ dưới đây không chứa danh tính
hay thông tin đăng nhập. Bộ 20 câu chỉ chọn tính năng có hướng dẫn đã kiểm, có mã màn và
nguồn cho từng đáp án; mười câu kiến thức đều tự soạn.

### Bảng đối chiếu từng file

| File trong help/ | Màn và route dùng để kiểm | Bằng chứng và phạm vi mô tả |
|---|---|---|
| 00-tong-quan.md | SCR-011 `/learn/topics`, LearnerLayout và menu tài khoản | Thấy Chủ đề, Ngữ pháp, Phát âm, Chữ Hán; menu có Hồ sơ, Đổi mật khẩu và Đăng xuất. Chỉ mô tả khu vực học viên. |
| 01-tai-khoan.md | SCR-002 `/login`, SCR-003 `/register`, SCR-004 `/verify-email`, SCR-005 `/forgot-password`, SCR-007 `/me/profile`, SCR-008 `/me/password` | Mở các form auth thật trên Chrome. Hồ sơ tải 200, thấy Tên hiển thị, Cấp HSK mục tiêu, Lưu hồ sơ và email không đổi được. Màn mật khẩu hiện ba ô và điều kiện 8–72 ký tự, chữ/số. Tài khoản demo đăng ký và xác minh qua API local; không ghi nhận đã thao tác đổi mật khẩu thành công trên UI. |
| 02-tu-vung-chu-de.md | SCR-011 `/learn/topics`, SCR-012 `/learn/topics/1` | GET danh sách/chi tiết 200; mở chủ đề thật tạo study-session 201. Thấy các trạng thái lọc, Học từ mới, Luyện nhận diện, Luyện nghe và Kiểm tra cuối chủ đề cùng điều kiện mở. Chưa có audio được ghi đúng trạng thái trên màn. |
| 03-ngu-phap.md | SCR-017 `/learn/grammar`, SCR-018 `/learn/grammar/176` | GET danh sách/bài 200. Bấm Đã hiểu tạo lesson 201, chuyển sang ba câu chọn và Bỏ qua bài tập. Chỉ giải thích thao tác, không đưa kiến thức/đáp án bài vào help. |
| 04-phat-am.md | SCR-016 `/learn/pronunciation` | GET stages 200; ba tầng demo hiển thị trạng thái, âm luyện và điều kiện 80%. Nút Đang cập nhật bị khoá do chưa có audio; không mô tả một lượt nghe đã hoàn tất. |
| 05-chu-han.md | SCR-014 `/learn/characters/writing`, SCR-015 `/learn/characters/recognition` | Danh sách chữ tải 200; chọn Nhìn chữ chọn nghĩa và Bắt đầu luyện tạo session 201, hiện câu chọn, Bỏ qua và Kết thúc luyện tập. Phần viết chỉ hướng dẫn chọn chữ và thông báo dữ liệu nét, không khẳng định đã kiểm chấm nét thành công. |
| 06-de-thi-va-xep-trinh-do.md | SCR-020 `/learn/exams`, SCR-021 `/learn/exams/sessions/:sessionId`, SCR-022 `/learn/exams/results/:sessionId`, SCR-023 `/learn/practice/targeted` | Danh sách đề/summary 200; tài khoản học viên đã nộp hai đề demo bằng API thật 200. Mở kết quả trên UI, result/weak-points đều 200; thấy điểm trọng số, số câu đúng, điểm theo phần, câu sai theo KID, Điểm yếu cần luyện và Luyện nút này. Bấm bộ lọc Bỏ trống hoạt động. Không ghi đã thử người thật (012/T-19) hay đo độ chính xác xếp trình độ. |
| 07-cay-nang-luc-va-on-tap.md | SCR-010 `/learn`, SCR-019 `/learn/progress`, SCR-013 `/learn/review` và `?ahead=true` | Recommendations, mastery, knowledge-tree, node detail và review summary đều 200. Trước học thấy banner chưa có minh chứng; sau hai lượt đề thấy Chưa đủ minh chứng. Chọn nút hiện điểm, ba minh chứng, tiên quyết và Luyện nút này. Sau học từ/ngữ pháp, Trang học tập hiện lịch ôn; Ôn trước mở lượt 11 mục. Bấm Hiện đáp án thấy đủ Quên/Khó/Được/Dễ và Tạm dừng. |
| 08-thu-vien.md | SCR-024 `/dictionary`, SCR-026 `/translate`, SCR-027 `/library/notes`, SCR-028 `/library/decks`, SCR-029 `/library/decks/:id` | Tra cứu dữ liệu thật; tạo bộ trống 201 rồi mở chi tiết 200, nhập Mặt trước/Mặt sau và Lưu thẻ thành công 201. Sổ tay GET 200, Tạo ghi chú mở form, nhập nội dung tự soạn và Lưu ghi chú thành công 201. Dịch chỉ kiểm form và trạng thái, không gọi dịch vụ model thật. |
| 09-the-nap-va-goi.md | SCR-030 `/account/credits`, SCR-031 `/account/credits/history` | API ví/lịch sử chưa có hành vi đầy đủ trên base, UI hiện lỗi tải. Giữ đúng câu Tính năng đang hoàn thiện và hướng liên hệ quản trị; không hướng dẫn nạp thẻ chưa thao tác được trên UI. |
| 10-cong-dong.md | SCR-039 `/community`, SCR-041 `/community/posts/new` và `/community/posts/:id/edit`, SCR-040 `/community/posts/:id` | Tạo nháp trên UI trả 201; về Bài viết của tôi thấy Bản nháp, Sửa bài và Đã lưu bản nháp. Với bài công khai tự soạn ở DB local, bấm Thích trả 200, Viết bình luận/Gửi bình luận trả 201, mở Báo cáo bài viết thấy form lý do và Gửi báo cáo. Thông báo bài bị ẩn đối chiếu thêm với component learner trên cùng base. Không mô tả quyền hay màn kiểm duyệt. |
| 11-tro-ly.md | LearnerLayout tại SCR-017 `/learn/grammar` | Bấm Mở trợ lý CNHSK thấy Trợ lý hỗ trợ CNHSK, Thu gọn, Cuộc trò chuyện mới, ô hỏi, bộ đếm và Gửi. Phạm vi/giới hạn/ẩn khi thi đối chiếu mã T-01/T-02/T-06 đã merge. Chưa chạy model thật, chưa kết luận SC-002. |

### Kích thước và gate

12 file UTF-8 không BOM, tổng **14.868 Unicode code point**. Đếm bằng Java 17 đọc từng file
UTF-8 và cộng `text.codePointCount(0, text.length())`, không cộng dấu phân cách. Các file theo
thứ tự 00–11 có lần lượt: 1.274, 1.417, 1.123, 891, 811, 1.115, 1.757, 1.799, 1.577,
192, 1.245, 1.667. Thấp hơn giới hạn G0 45.000 và ngưỡng HelpLibrary 60.000.

Gate ngày 2026-11-12–13: `.\mvnw.cmd -B verify` với Java 17, Docker và DEBUG=false
đạt BUILD SUCCESS, exit 0. **4.246 test, 0 failure/error/skip**, 18 phút 17 giây.
JaCoCo LINE **96,71%** (11.955/12.362 dòng), toàn bộ coverage check đạt.
Không dùng ngoại lệ môi trường. NFR_P04 QUESTION đạt p95 26.604 ms, dưới ngưỡng 30.000 ms.
12 file help trong target/classes khớp hash với nguồn. Bộ 20 câu đủ 10 câu cách dùng có
màn/ngữ cảnh và nguồn đáp án, 10 câu kiến thức tự soạn; toàn bộ kết quả model để Chưa chạy.

Self-check Constitution: HR-02/BUS-06 không có secret/PII thật trong nội dung hay bằng chứng;
HR-08 chỉ dùng câu tự soạn và bộ demo đã có; AC-05 không sửa migration hay chạy SQL trên Neon;
ES-01 giữ Java 17 và dependency hiện có; ES-05 full verify và coverage đạt. Chỉ 15 file nội
dung được duyệt, không đổi code, contract hay file dùng chung. Nội dung vẫn cần leader đọc
và duyệt ý nghĩa trước merge; T-07 mới kiểm SC-002 trên máy leader.

## 2026-11-06 — T-06 endpoint hỏi trợ lý (leader duyệt)

- Q-T06-01: Kiểm body rồi kiểm bài đang làm, trước khi đặt chỗ Redis. Request trả 422 hoặc 403 không tính vào hạn mức ngày.
- Q-T06-02: Đặt chỗ nguyên tử theo ngày Việt Nam. Hoàn lượt nếu thư viện rỗng hoặc ngân sách tháng từ chối trước khi gọi client. Khi bắt đầu `ClaudeClient.complete`, giữ lượt kể cả lỗi upstream hoặc timeout. Retry tính tới 00:00 hôm sau và làm tròn lên theo giây. Redis lỗi thì cho qua và chỉ WARN với metadata an toàn.
- Q-T06-03 (thay thế): `ActiveTestApi.hasActiveTest` là API chỉ đọc riêng, dùng một truy vấn của `AttemptRepository`. Lọc đúng người gọi, nguồn `MOCK_EXAM`, `PLACEMENT`, `TOPIC_TEST`, trạng thái `IN_PROGRESS` và `expiresAt > now` từ Clock. Giữ nguyên `PracticeApi`, `PracticeApiTest` và `PracticeService`.
- Q-T06-04: OpenAPI giữ nguyên. Controller kiểm kiểu JSON và đếm code point của câu hỏi sau khi cắt khoảng trắng. Khi có mặt, history phải là mảng. Page context nhận mã đúng pattern hoặc null. Sai body trả 422 qua xử lý cục bộ. Kết quả dạng văn bản thường dùng nguyên văn theo E6. JSON sai schema hoặc câu trả lời trắng trả 503.
- Q-T06-05: Chỉ chấp nhận ngoại lệ gate cho FR023 import p95, NFR_P04/T08_QUESTION_CONFIRM_BENCHMARK, LoginAttempt PEXPIRETIME, LoginAttempt CapturedOutput và QuestionAdminControllerIntegrationTest.NFR_P02. Nếu chỉ các test này fail thì báo tên test, số đo và trạng thái FULL VERIFY FAILED. Lỗi Docker, mạng hay port cần khôi phục môi trường rồi chạy lại gate. Không skip test.
- Kết quả T-06: targeted 98/98. FULL VERIFY FAILED (1 test môi trường: NFR_P04 p95 30.782 ms), 3.577 test, 0 error/skip. JaCoCo LINE 96,72%. Leader chấp nhận bàn giao Draft PR theo ngoại lệ Q-T06-05.

## 2026-11-06 — T-05 tài liệu, che PII và dựng prompt (leader duyệt)

- Q-T05-01: `cnhsk.assistant.help-location` mặc định `classpath*:help/*.md`. Nạp UTF-8 lúc khởi động theo tên file. Đếm Unicode code point trong nội dung từng file, không tính dấu phân cách. Cho phép đúng 60.000. Toàn bộ nội dung trắng được coi là thư viện rỗng.
- Q-T05-02: Che email trước rồi che số điện thoại trong câu hỏi và từng tin lịch sử. Chỉ che cụm số đứng riêng có tiền tố 0 hoặc +84 và 9–10 chữ số theo FR-005. Không cắt từ chuỗi số dài hơn, không che số dính chữ hoặc dấu gạch dưới. Nếu mã số đứng riêng có hình thức giống hệt số điện thoại thì che.
- Q-T05-03: Map tiếng Việt chỉ gồm mã màn có route dùng LearnerLayout, kể cả đổi mật khẩu khi vào bằng vai trò học viên. Ưu tiên tiêu đề giao diện đã merge. SCR-030 là «Gói dịch vụ và điểm». Mã khác, null hoặc chuỗi tự do không thêm dòng tên màn.
- Q-T05-04: Khi thư viện rỗng, `AssistantPromptBuilder.build` trả `Optional.empty()`. T-06 ánh xạ tín hiệu này sang 503 `ASSISTANT_UNAVAILABLE` trước khi gọi client. T-05 kiểm trực tiếp payload dựng từ các kiểu của ClaudeClient. Test qua endpoint đến stub thuộc T-06.

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

## 2026-11-13 — T-07: hợp đồng runtime và nghiệm thu backend Stub

Base: `b8e812aae2aa15219c0e9203943a40d699a46cf1`, nhánh `spec/015-t07`. Allowlist leader duyệt: AssistantContractTest.java, AssistantController.java, questions.md, tasks.md. Không sửa FE, launcher của 011 hoặc YAML.

### Q-T07-01 / Q-T07-02 — leader chốt

- Viết AssistantContractTest trước, kế thừa AbstractIntegrationTest. So hai chiều path/method/status giữa YAML và /v3/api-docs; đối chiếu thêm RequestMappingHandlerMapping. Lần chạy trước sửa: 1 test fail có chủ đích; status mong đợi 200/401/403/422/429/503, runtime chỉ có 200. Path và method khớp.
- Chỉ thêm annotation tài liệu vào AssistantController: Operation và ApiResponses. Không đổi xử lý request. Sau sửa, status khớp chính xác, không có status thừa nên không cần Hidden trên handler.
- Giữ allowlist 4 file. Không sửa MasteryAcceptanceServer để nạp response cho StubClaudeClient. Stub rỗng trả ASSISTANT_UNAVAILABLE; leader đổi nghiệm thu trình duyệt sang 503, kèm body đúng và ca sai pageContext trả 422.
- Phần code xong, chờ leader chạy Claude thật. Không đánh ✅ T-07. AT-12/SC-002 vẫn chờ bộ 20 câu trong docs/demo-data/assistant-eval.md trên máy leader; chưa có kết quả model thật.

### Bằng chứng HTTP và giao diện local

Dùng MasteryAcceptanceServer có sẵn, profile test, PostgreSQL/Redis/Mailpit Testcontainers, tài khoản learner fixture dùng một lần. Mật khẩu ngẫu nhiên truyền qua stdin, không ghi file. Không đặt khoá Claude và không gọi API model thật. Backend bind 127.0.0.1:8080; Vite bind 127.0.0.1:5173; Chrome headless dùng profile tạm.

| Thao tác | Endpoint | Mong đợi / thực tế | Kết quả |
|---|---|---|---|
| Đăng nhập learner fixture bằng giao diện | POST /api/auth/login | 200 / 200 | Đăng nhập bằng cookie, không ép bearer |
| SCR-010: mở panel, gửi câu hỏi cách dùng tự soạn | POST /api/assistant/ask | 503 / 503 ASSISTANT_UNAVAILABLE | Body có question, history = [], pageContext = SCR-010. Request có cookie và header CSRF; không ghi giá trị vào báo cáo. Hiện đúng “Trợ lý tạm thời chưa trả lời được, bạn thử lại sau” |
| SCR-010: gửi câu thứ hai | POST /api/assistant/ask | 503 / 503 ASSISTANT_UNAVAILABLE | history gồm tin user trước, chỉ có role/text; một phần tử, không quá 10. pageContext vẫn SCR-010; hiện đúng thông báo |
| Nạp CSRF bình thường trước fetch kiểm dữ liệu | GET /api/auth/csrf | 204 / 204 | Không ghi giá trị token |
| Browser fetch: pageContext = ABC, question hợp lệ, history = [] | POST /api/assistant/ask | 422 / 422 VALIDATION_FAILED | Kiểm body vẫn có hiệu lực, không bị bỏ qua |
| Mở Ôn tập, SCR-013, rồi mở panel | GET /learn/review | 200 / 200 | Panel Trợ lý hỗ trợ CNHSK vẫn hiển thị |

Bằng chứng trên chỉ xác nhận luồng Stub theo chốt leader, không được tính là nghiệm thu câu trả lời Claude thật. Luồng 200 có stub được phủ bởi AssistantControllerIntegrationTest; hiển thị văn bản thuần và các trạng thái FE được phủ bởi Vitest AT-15. Ca 200 với Claude thật dành cho lượt chạy 20 câu của leader.

Helper trình duyệt đã sửa cách nạp CSRF trước fetch và dùng thuộc tính status của Fetch API; không thay đổi ứng dụng để chiều helper. Lượt kiểm cuối cùng đạt. Dọn xong Java/Vite do task khởi động, 0/4 container launcher còn lại; giữ nguyên container local có từ trước.

### Gate thực tế

| Bước | Kết quả |
|---|---|
| npm ci | PASS; lockfile không đổi |
| BE chọn lọc: AssistantContractTest, các Assistant*Test và HelpLibrary*Test | 49 test, 0 failure/error/skip |
| FE assistant + Layouts.test.tsx, 2 worker | 69/69 PASS |
| Shuffle FE chọn lọc, seed 17 / 104 / 2026 | Mỗi seed 69/69 PASS |
| Full Vitest, minWorkers=2, maxWorkers=2 | 2409 PASS / 1 FAIL, tổng 2410; xem lỗi ngoài phạm vi dưới đây |
| Chạy riêng Scr016PronunciationPage.test.tsx một lần | 30/30 PASS, không sửa test |
| npm run lint | 0 lỗi, chỉ warning LevelBadge đã có |
| npm run build | PASS |
| Full mvnw.cmd -B verify, Java 17, DEBUG=false, Docker | 4247 test, 0 failure/error/skip; BUILD SUCCESS; 20:39 phút |
| JaCoCo LINE | 96,7077% = 11955 / 12362 dòng; toàn bộ kiểm tra coverage đạt |
| git diff --check / UTF-8 không BOM | PASS |

Full Vitest lỗi ngoài T-07: `Scr016PronunciationPage.test.tsx > SCR-016 Pronunciation Practice > FR018_khôi phục lượt đã trả lời hết không suy ra lời giải câu cuối`. Nguyên văn lỗi: `TypeError: Cannot read properties of undefined (reading 'then')`, tại `src/shared/api/useApiQuery.ts:42:33`. Chạy riêng class một lần đạt 30/30; ghi nhận theo ngoại lệ test chập chờn ngoài task đã duyệt. Không gọi full Vitest là xanh hoàn toàn. Full Maven không dùng ngoại lệ môi trường nào.

### Backlog schema — không sửa trong T-07

Controller nhận RequestBody String nên /v3/api-docs mô tả request body là string; YAML mô tả object. Leader yêu cầu ghi backlog và giữ nguyên hành vi/chữ ký ở T-07.

### Self-check

- HR-02, BUS-06: không đưa mật khẩu, email fixture, cookie, token, khoá hoặc dữ liệu thật vào repo/PR; không gọi Claude thật.
- HR-08: câu kiểm tự soạn; không sử dụng đề HSK hoặc PDF nội bộ.
- AC-01: contract giữ nguyên; chỉ bổ sung tài liệu runtime và test đối chiếu.
- AC-03, AC-05: không đổi ranh giới module, schema hay migration; chỉ DB Testcontainers.
- Phạm vi: đúng 4 file; không sửa launcher, shared/, FE hoặc test có sẵn. T-07 chờ leader nghiệm thu Claude thật.

# Câu hỏi và quyết định — Spec 003

Các căn cứ và đề xuất được giữ lại để truy vết. Quyết định ghi **Sửa 2026-10-30 (leader duyệt)** là nội dung áp dụng cho T-03.

## Q-T02-01 — Biểu diễn TTL động của GAME_ROUND trong PracticeSource

**Task:** T-02. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Căn cứ:**

- Spec 003 §0.2 yêu cầu `Duration ttl()` trên enum `PracticeSource`; TTL của `GAME_ROUND` là "theo game".
- Spec 003 §0.11 có `StartPracticeCommand.ttlOverride`.
- Spec 007 FR-009 yêu cầu game truyền `ttlOverride = 3 giây + timeLimitSeconds + 15 giây`.
- `WRITING_CHALLENGE` đã có TTL cố định 210 giây theo spec 006 §0 và FR-021; không cần hỏi lại giá trị này.

**Điểm chưa được đặc tả:** giá trị trả về hoặc hành vi của `GAME_ROUND.ttl()` khi không có ngữ cảnh game. Không thể chọn một thời lượng cố định đúng cho mọi game.

**Quyết định đã duyệt:** Giữ chữ ký `Duration ttl()` theo spec 003 và chọn `null` cho `GAME_ROUND`, dùng nhất quán; không dùng `Optional<Duration>`.

- `GAME_ROUND` không có TTL mặc định. `PracticeService.start` ở T-03 bắt buộc nhận `ttlOverride > 0`; thiếu, bằng 0 hoặc âm đều ném `IllegalArgumentException` vì là lỗi lập trình, không tự chọn hạn mặc định.
- Tám nguồn còn lại có TTL cố định, gồm `WRITING_CHALLENGE = 210 giây`. Truyền bất kỳ `ttlOverride` nào cho nguồn cố định đều ném `IllegalArgumentException`, kể cả giá trị bằng TTL cố định.
- T-02 chỉ khai báo và kiểm thử metadata của chín nguồn, không triển khai `PracticeService.start`.
- T-03 phải kiểm thử: game thiếu/không có override dương bị từ chối; game có override dương tính đúng hạn; nguồn cố định có override bị từ chối.

## Ghi chú kiểm chứng contract — báo leader, ngoài phạm vi sửa của T-02

Khi kiểm chứng bằng `swagger-parser` 2.1.22 đang có trong dự án, bộ đọc YAML chuyển `no` không có dấu nháy trong mảng `required` thành boolean `false`. Đã tái hiện trên `ServedItemView`: mảng được đọc thành `[false, "type", "skill", "prompt", "rules"]`, trong khi tên property vẫn là `no`. Parser phát cảnh báo tương tự cho `ItemState`, `AnswerRequest`, `AnswerResult`.

T-02 giữ đúng field `no` theo spec và contract; mapper/client DTO không đổi tên field và các test JSON vẫn kiểm tra field này. Không sửa OpenAPI ngoài phạm vi đã duyệt. Đề nghị người giữ contract xem xét thêm dấu nháy cho `'no'` trong các mảng `required` để tương thích với bộ đọc hiện có; đây là ghi chú về công cụ, không phải một thay đổi nghiệp vụ đã tự quyết.

## Q-T03-01 — Session không tồn tại: Constitution BUS-01 và contract đang mâu thuẫn

**Task:** T-03. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Sửa 2026-10-30 (leader duyệt):** Lượt không tồn tại hoặc không thuộc người gọi đều trả 403 `ATTEMPT_NOT_OWNED`, cùng message. Sửa spec/OpenAPI hai GET trong T-03; T-04…T-06 áp dụng cho `answers`/`finish`/`abandon`. Không cần RFC vì tuân thủ Constitution BUS-01.

**Căn cứ:** Constitution `BUS-01` yêu cầu tài nguyên cá nhân không tồn tại cũng trả **403**, không trả 404. Spec 003 §0.6 E-R1 và §5 lại quy định **404** `SESSION_NOT_FOUND`; §13 và OpenAPI cũng khai báo 404 cho các endpoint `/api/practice/sessions/{id}` và các thao tác trên session đó. E-R2 quy định session của người khác trả **403** `ATTEMPT_NOT_OWNED`.

**Đề xuất để duyệt:** Tuân thủ Constitution: session không tồn tại và session của người khác đều trả **403** `ATTEMPT_NOT_OWNED` với cùng thông báo, không tiết lộ sự tồn tại của session. Sau khi leader chốt, cập nhật E-R1/E-R2, error matrix, §13, AT-02 và responses của các đường dẫn session trong OpenAPI trước khi code. Giữ nguyên 404 của tài nguyên nội dung như topic. Nếu giữ 404 cho session thì cần RFC theo Governance của Constitution; duyệt Shadow Plan đơn thuần không thay thế RFC.

**Bổ sung contract xin duyệt cùng T-03:** GET `/active` khai báo thêm 403 do chỉ cho LEARNER; GET `/sessions/{id}` khai báo 422 cho path id sai kiểu. Dữ liệu query/path sai kiểu hoặc thiếu `source` dùng `VALIDATION_FAILED` theo định dạng lỗi chung. GET của chủ sở hữu vẫn trả 200 với trạng thái hiện tại; lượt IN_PROGRESS quá hạn được chuyển EXPIRED rồi trả view, không áp lỗi ghi câu 422/409 cho thao tác xem lại.

## Q-T03-02 — Ý nghĩa refId khi không truyền vào findActive

**Task:** T-03. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Sửa 2026-10-30 (leader duyệt):** Có `refId` thì lọc đúng; thiếu thì tìm trên mọi ref_id. Luôn lọc người gọi, nguồn, IN_PROGRESS, chưa hết hạn; mới nhất theo served_at DESC rồi id DESC; không có trả 204.

**Căn cứ:** Spec 003 §0.11 mô tả lượt IN_PROGRESS chưa hết hạn mới nhất của người gọi cho nguồn đó. OpenAPI bắt buộc `source` nhưng không bắt buộc `refId`; chưa xác định thiếu `refId` là lọc `ref_id IS NULL` hay không lọc theo ref_id.

**Đề xuất để duyệt:** Có `refId` thì lọc đúng ref_id; không có `refId` thì tìm trong mọi ref_id của nguồn đó. Luôn lọc user_id của người gọi, source, IN_PROGRESS và `expires_at >= now`; chọn mới nhất theo served_at giảm dần, rồi id giảm dần khi trùng thời điểm. Không có kết quả trả 204. Nếu leader chọn `IS NULL`, ghi rõ lựa chọn đó trong spec và mô tả tham số OpenAPI trước khi code.

## Q-T03-03 — AT-01 quét toàn bộ JSON mâu thuẫn ItemState.answer

**Task:** T-03. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Sửa 2026-10-30 (leader duyệt):** `answered[].answer` được chứa câu trả lời của chính người học. `correctAnswer` chỉ trả khi feedback là IMMEDIATE/RECORD và câu đã ghi/chấm; DEFERRED tuyệt đối không trả `correctAnswer`/`accept`/`explanation` trước finish, có test riêng. Quote `'no'` trong bốn mảng required đã được duyệt. Các đoạn đề xuất bên dưới là lịch sử; quyết định tại đây là chuẩn áp dụng.

**Phạm vi và nhánh đã duyệt:** Tiếp tục `spec/003-vocab-topics`, T-01…T-08 + T-13 dùng chung Draft PR; chỉ Ready sau T-08. Fetch lỗi không chặn code. Nhánh đã push thì fetch và merge origin/main khi main đi tiếp, không force-push. Leader duyệt ba file test T-03, sửa spec/OpenAPI, dòng T-03 trong tasks.md; SecurityConfig/ErrorCode chỉ thêm dòng và liệt kê trong mô tả PR.

**Căn cứ:** Spec 003 SC-002/AT-01 yêu cầu response bắt đầu lượt không có khóa `answer` hoặc `accept` ở bất kỳ đâu. FR-002 cho phép preRecorded được ghi ngay lúc start, FR-022/FR-033 trả lại lượt đang làm; SessionView.answered trong OpenAPI lại chứa ItemState.answer để khôi phục chính câu trả lời của người học. Vì vậy một SessionView có preRecorded hoặc được trả lại khi tiếp tục có thể chứa khóa `answered[].answer` dù không lộ đáp án của server.

**Đề xuất để duyệt:** Phân biệt dữ liệu bí mật của server với câu trả lời của chính người gọi: `items` và `meta` không chứa đáp án server `answer`/`accept`; `answered[].answer` được chứa đầu vào của chủ sở hữu để khôi phục, kể cả preRecorded và tiếp tục lượt. `correctAnswer` chỉ được trả cho câu đã chấm trong IMMEDIATE; RECORD không có đáp án đúng; DEFERRED không lộ kết quả/đáp án đúng trước khi nộp và giữ `correct = null` theo SessionView hiện tại. Sửa rõ SC-002, AT-01 và mô tả SessionView/ItemState trong OpenAPI; kiểm thử quét JSON theo các vùng dữ liệu này và dùng đáp án bí mật khác đầu vào người học để phát hiện rò rỉ giá trị.

**Chỉnh cú pháp OpenAPI xin duyệt kèm theo:** Thêm dấu nháy cho `'no'` trong bốn mảng required của ServedItemView, ItemState, AnswerRequest và AnswerResult theo ghi chú kiểm chứng T-02; không đổi tên trường hay nghiệp vụ.

## Q-T03-04 — Phạm vi AuthContractTest khi thêm endpoint ngoài auth

**Task:** T-03. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Sửa 2026-10-30 (leader duyệt):** `AuthContractTest` chỉ giới hạn tài liệu dùng để so sánh về package `com.cnhsk.auth` qua cấu hình test, giữ nguyên toàn bộ assertion của spec 001 và thêm một dòng comment giải thích. Khi merge với spec 002 T-11, giữ cả cấu hình giới hạn package và `@MockitoBean ImportMaintenance`; không tự bỏ một trong hai nếu xung đột.

`src/test/java/com/cnhsk/contract/AuthContractTest.java`, test `AC01_runtimeDocumentedPathsAndStatusesAreDeclaredBySourceContract`, đang yêu cầu **mọi** path ở `/v3/api-docs` phải có trong `openapi-auth.yaml`. Không có bộ lọc package/path cho tài liệu runtime; thêm hai GET practice khiến assertion của spec 001 không còn đúng phạm vi.

**Đề xuất ban đầu (lịch sử):** Chỉ trong cấu hình của `AuthContractTest`, thêm `springdoc.packages-to-scan=com.cnhsk.auth.controller` để kiểm contract 001 trên đúng các controller auth. Quyết định đã duyệt phía trên dùng package cha `com.cnhsk.auth`; không thay cấu hình tài liệu runtime của ứng dụng.

## Q-T03-05 — Danh sách ErrorCode kỳ vọng trong GlobalExceptionHandlerTest

**Task:** T-03. **Trạng thái:** Đã chốt bởi leader ngày 2026-10-30.

**Sửa 2026-10-30 (leader duyệt):** Chỉ thêm `ATTEMPT_NOT_OWNED`, `RATE_LIMITED` vào danh sách kỳ vọng của `GlobalExceptionHandlerTest`. Giữ nguyên `containsExactly`, mọi mã cũ và các test còn lại. Gate đầy đủ chạy bằng PowerShell ngoài sandbox khi Docker có Server Version; chỉ đánh dấu hoàn tất, commit/push sau 0 failure, 0 error, coverage toàn bộ ≥80% và practice ≥90%.

**Kiểm chứng:** Lần `mvnw verify` mới chạy được Surefire: 524 test, 1 failure, 206 error, không skip. `AuthContractTest` 6/6 xanh. Failure duy nhất là `GlobalExceptionHandlerTest.errorCodeContainsEveryCodeFromTheSpecification`: `containsExactly` đang liệt kê chỉ các mã của spec 001, nên hai mã `ATTEMPT_NOT_OWNED`, `RATE_LIMITED` được duyệt cho T-03 làm assertion fail. Các suite Testcontainers bị lỗi khởi tạo vì Docker bị từ chối truy cập.

**Đề xuất đã duyệt:** Trong `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`, bổ sung đúng hai tên `ATTEMPT_NOT_OWNED`, `RATE_LIMITED` trước `INTERNAL_ERROR` trong danh sách kỳ vọng. Giữ nguyên `containsExactly`, mọi mã cũ, thứ tự tương đối các mã spec 001 và toàn bộ test còn lại.

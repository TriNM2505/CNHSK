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

**Phạm vi và nhánh đã duyệt:** Tiếp tục `spec/003-vocab-topics`, T-01…T-08 + T-13 dùng chung Draft PR; chỉ Ready sau T-08. Leader duyệt ba file test T-03, sửa spec/OpenAPI, dòng T-03 trong tasks.md; SecurityConfig/ErrorCode chỉ thêm dòng và liệt kê trong mô tả PR.

**Đính chính (leader, 2026-10-30, khi duyệt T-04):** Q-T03-03 không phải quyết định về fetch. Leader hiện cho phép tiếp tục trên local khi fetch lỗi, nhưng phải push được lên `spec/003-vocab-topics` trước khi báo T-04 xong. Không merge/rebase main, không force-push; không tự xử lý conflict với PR #18 của spec 002.

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

## Q-T04-01 — Lưu bằng chứng audioFailed trong nháp DEFERRED

**Task:** T-04. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt).

**Quyết định:** Nháp DEFERRED lưu `audioFailed` và `replayCount` cùng `answer`, `durationMs`, `skipped` để thay nháp không mất bằng chứng. Đồng bộ FR-006 trước khi code. Response answer và GET session trước finish không được chứa field `correct` hoặc `correctAnswer`; yêu cầu mới này thay thế quy định cũ trả `correct=null`, có test assert JSON không có field.

**Căn cứ:** FR-006 quy định `draft_answers[no] = {answer, durationMs, replayCount, skipped}`. E-R10 cho phép `audioFailed=true` với câu có audio và yêu cầu không tính câu đó vào điểm; E-F4 loại câu audioFailed khỏi mẫu số khi finish ở T-05. Cấu trúc nháp hiện ghi trong FR-006 không có cờ này, nên không đủ dữ liệu để finish áp dụng E-R10/E-F4 sau khi tải lại lượt.

**Đề xuất để duyệt:** Nháp DEFERRED lưu thêm `audioFailed` bên cạnh bốn trường FR-006. T-04 kiểm điều kiện hasAudio/replay/skip và lưu bằng chứng; chưa ghi attempt_items/chấm câu DEFERRED trước finish. Response giữ đúng `{no, accepted:true}`, không lộ correct/correctAnswer/accept/explanation. T-05 đọc cờ audioFailed từ nháp để ghi kết quả null và loại khỏi điểm. Sau duyệt, bổ sung dòng quyết định vào FR-006 trước code.

## Q-T04-02 — Request đồng thời skipped=true và audioFailed=true

**Task:** T-04. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt).

**Quyết định:** `skipped=true` và `audioFailed=true` đồng thời trả 422 `VALIDATION_FAILED`, không ghi answer hoặc nháp. Đồng bộ E-R7 và AnswerRequest trước khi code.

**Căn cứ:** AnswerRequest có hai cờ độc lập, không có ràng buộc loại trừ. Với DEFERRED, E-R11 quy định skipped là chưa trả lời (sai khi nộp), còn E-R10/E-F4 quy định audioFailed không tính điểm. Khi cả hai cùng true, spec chưa nói áp dụng cách nào.

**Đề xuất để duyệt:** Hai cờ loại trừ nhau; request có cả hai true trả 422 VALIDATION_FAILED với details tương ứng, không ghi câu/nháp. Ghi quy tắc trong E-R7 và mô tả AnswerRequest của OpenAPI trước code. Các quy định allowSkip=false và hasAudio của từng cờ vẫn áp dụng như spec hiện tại.

## Q-T04-03 — Phạm vi file để triển khai và kiểm thử endpoint answers

**Task:** T-04. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt danh sách file kèm giới hạn).

**Quyết định và giới hạn bắt buộc:**

- Duyệt danh sách file đề xuất bên dưới. `ErrorCode.java` chỉ thêm mã đã có trong OpenAPI 003 APPROVED, không đổi/xóa/đổi tên mã cũ; liệt kê tám mã trong PR. Nếu bất kỳ mã nào chưa có trong OpenAPI thì dừng hỏi leader.
- `GlobalExceptionHandlerTest` chỉ thêm mã mới vào danh sách, giữ mọi assertion khác. File này và `ErrorCode.java` trùng PR #18 của spec 002 (merge trước); leader xử lý conflict. Không merge/rebase main.
- Kiểm matcher trước khi sửa `SecurityConfig.java`: nếu đã phủ POST answers cho LEARNER thì không sửa; nếu cần, chỉ thêm đúng một matcher, giữ rule 001/009. Kiểm tra hiện tại: chỉ hai GET được mở cho LEARNER, POST answers chưa được phủ.
- `PracticeRateLimiter` giới hạn 600 câu/giờ; Redis lỗi thì bỏ qua giới hạn và log cảnh báo theo E-R12, nhất quán giới hạn tạo lượt hiện có.
- Hết hạn phải lưu `EXPIRED` dù trả 422, bằng transaction riêng hoặc không throw trong transaction đang ghi; test kiểm cả HTTP và DB sau request.
- Hook nguồn từ chối phải rollback toàn bộ, không còn answer/nháp; có test.
- DEFERRED trước finish: response answer và GET session không có field `correct`/`correctAnswer`; có test JSON.
- Hai request trả lời cùng câu phải chỉ ghi đúng một dòng, request còn lại nhận lỗi theo spec; có test đồng thời.
- Gate: PowerShell thường có Docker, `mvnw verify` 0 failure/error/skip, line coverage toàn bộ >=80%, practice >=90%, `git diff --check` sạch. Phải push được lên `spec/003-vocab-topics` trước khi báo xong.
- Xong T-04 tiếp tục T-05 theo thứ tự đợt 0 (T-01…T-08 + T-13), mỗi task một commit, cùng Draft PR. Mỗi task vẫn có Shadow Plan theo AGENTS.md §9.

**Căn cứ:** Cột File của T-04 chỉ có `PracticeService.java`, `grading/*`, nhưng FR-004 yêu cầu áp dụng luật qua `POST /api/practice/sessions/{id}/answers`. `PracticeController` hiện chỉ có hai GET, `SecurityConfig` chỉ cho LEARNER gọi hai GET đó, và `PracticeRateLimiter` chỉ có giới hạn tạo lượt. Tám mã lỗi cần cho T-04 chưa có trong `ErrorCode`; test `GlobalExceptionHandlerTest.errorCodeContainsEveryCodeFromTheSpecification` kiểm toàn bộ enum bằng `containsExactly`. Một assertion của `PracticeControllerIntegrationTest.FR010_onlyLearnersCanReadSessionsAndExistingAuthenticationStillWorks` đang kỳ vọng POST answers của LEARNER bị từ chối vì endpoint chưa được mở.

**Đề xuất để duyệt cùng Shadow Plan:** Bổ sung các file sau vào phạm vi T-04 trước khi triển khai:

- `src/main/java/com/cnhsk/learning/practice/web/PracticeController.java`: thêm POST answers và DTO theo contract.
- `src/main/java/com/cnhsk/learning/practice/PracticeRateLimiter.java`: thêm giới hạn 600 câu/giờ, giữ nguyên giới hạn tạo lượt và hành vi Redis lỗi.
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`: chỉ thêm matcher POST answers cho LEARNER, dùng nguyên cơ chế CSRF hiện có.
- `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`: chỉ thêm `SESSION_EXPIRED`, `SESSION_ALREADY_SUBMITTED`, `ITEM_NOT_IN_SESSION`, `ANSWER_ALREADY_SUBMITTED`, `OPTION_NOT_IN_ITEM`, `TOO_MANY_REPLAYS`, `AUDIO_NOT_IN_ITEM`, `SKIP_NOT_ALLOWED`.
- `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`: chỉ thêm tám mã tương ứng vào danh sách kỳ vọng, giữ `containsExactly` và mọi assertion cũ.
- `src/test/java/com/cnhsk/learning/practice/PracticeControllerIntegrationTest.java`: cập nhật đúng assertion POST answers chưa được mở; giữ các kiểm tra xác thực, role và GET cũ.
- `src/test/java/com/cnhsk/learning/practice/PracticeAnswerIntegrationTest.java` và `src/test/java/com/cnhsk/learning/practice/grading/PinyinGraderTest.java`: test AT-02…AT-12, AT-18, đồng thời kiểm CSRF, contract và rollback khi hook từ chối.
- `specs/003-vocab-topics/spec.md`, `contracts/openapi-practice-vocab.yaml`: đồng bộ Q-T04-01/02 sau khi leader chốt; bỏ response 404 riêng của answers theo quyết định Q-T03-01 đã duyệt, giữ các thao tác finish/abandon cho T-05/T-06.
- `specs/003-vocab-topics/tasks.md`: ghi phạm vi được duyệt và chỉ đánh dấu T-04 hoàn tất sau khi đủ gate.

**Tình trạng kiểm tra:** `origin/main` cục bộ tại `f8e173d` có spec 001 đã đánh dấu xong và spec 002 T-01…T-03 đã hoàn tất; Q5 spec 003 đã duyệt. Nhánh hiện tại có T-01…T-03 và chưa nằm trong `origin/main` cục bộ. `git fetch origin` ngày 2026-10-30 vẫn lỗi kết nối GitHub, nên chưa xác minh được trạng thái remote mới nhất. Dùng chung nhánh/PR theo phạm vi đã duyệt. Việc cho phép tiếp tục local khi fetch lỗi là chỉ thị mới của leader khi duyệt T-04, không phải quyết định Q-T03-03.

## Q-T04-04 — Tám mã lỗi chưa được liệt kê trong OpenAPI 003 APPROVED

**Task:** T-04. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt).

**Quyết định:** Bổ sung đúng tám mã bên dưới vào responses/mô tả của POST answers, với HTTP status đúng spec §5; giữ `ErrorResponse.error_code` là string và không sửa schema lỗi dùng chung hoặc endpoint khác. Kiểm tra E-R3…E-R11 và AT liên quan không có status mâu thuẫn với §5. Sau cập nhật OpenAPI, chỉ thêm đúng tám mã vào `ErrorCode.java`. OpenAPI, enum và code đi chung commit T-04; PR ghi rõ: "OpenAPI 003: bổ sung 8 mã lỗi cho POST answers theo spec §5 (Q-T04-04)". Giữ nguyên mọi điều kiện T-04 đã duyệt, không sửa FE.

**Kiểm tra 2026-10-30:** `contracts/openapi-practice-vocab.yaml` chỉ khai báo `ErrorResponse.properties.error_code` là `{ type: string }`, mô tả response lỗi tham chiếu spec 003 §5. Không có enum, ví dụ hoặc mô tả nào trong file OpenAPI liệt kê tám mã sau:

- `SESSION_EXPIRED`
- `SESSION_ALREADY_SUBMITTED`
- `ITEM_NOT_IN_SESSION`
- `ANSWER_ALREADY_SUBMITTED`
- `OPTION_NOT_IN_ITEM`
- `TOO_MANY_REPLAYS`
- `AUDIO_NOT_IN_ITEM`
- `SKIP_NOT_ALLOWED`

Cả tám mã có trong spec 003 §5, nhưng điều kiện leader vừa duyệt yêu cầu mã phải có trong OpenAPI 003 APPROVED và phải dừng hỏi nếu thiếu. Không tự coi tham chiếu chung tới §5 là phê duyệt thêm các mã vào OpenAPI hoặc `ErrorCode.java`.

**Đề xuất để leader duyệt:** Bổ sung danh sách tám mã trên kèm HTTP status và nguyên nhân theo spec §5 vào description của POST `/api/practice/sessions/{id}/answers` trong OpenAPI; giữ `ErrorResponse` dùng chung là chuỗi để không giới hạn các mã lỗi auth/endpoint khác. Sau khi leader duyệt bổ sung contract này, đồng bộ các quyết định T-04 đã chốt vào spec/OpenAPI, rồi thêm đúng tám mã vào `ErrorCode.java` và danh sách test như phạm vi đã duyệt.

**Kiểm chứng triển khai T-04 (2026-10-30, chưa hoàn tất):**

- Đã cập nhật OpenAPI trước code; đúng tám mã và status theo §5, schema `ErrorResponse` nguyên vẹn. Đối chiếu với HEAD xác nhận mọi status endpoint khác không thay đổi. Enum chỉ thêm tám mã, giữ mã cũ và thứ tự tương đối.
- Đã triển khai ba kiểu phản hồi, ghi đầy đủ bằng chứng nháp, kiểm cờ loại trừ, chấm pinyin, rate limit 600 câu/giờ. `SecurityConfig` thêm đúng một matcher. Hết hạn trả lỗi ngoài transaction đã commit; hook chạy trong transaction ghi câu/nháp. Có test DB/HTTP cho hết hạn, hook rollback, đồng thời và JSON DEFERRED không có field kết quả.
- Java 17 javac thành công. JUnit Platform chạy riêng 91 test: 91 pass, không failure/skip. `mvnw -o -Dmaven.repo.local=C:\Users\ADMIN\.m2\repository verify` chạy 573 test: 342 pass, 0 failure, 231 error, 0 skip; toàn bộ error bắt nguồn Docker không khởi tạo do `AccessDeniedException: \\.\pipe\docker_engine`. 24 test pinyin xanh; 25 test answers chưa thực thi được vì lớp hạ tầng không khởi tạo. Chưa xác nhận coverage, không dùng báo cáo JaCoCo cũ làm kết quả T-04.
- Yêu cầu chạy `mvnw verify` ngoài sandbox bị chính sách phê duyệt tự động từ chối (`sandbox_approval: false`). Push dry-run lên `spec/003-vocab-topics` lỗi kết nối GitHub cổng 443. Chưa commit, chưa push T-04, không đánh dấu ✅ và chưa sang T-05.
- Self-check Constitution: `AC-01` contract trước endpoint; `AC-03` dùng `AuthApi`, chỉ truy cập bảng learning trong bộ máy; `BUS-01` missing/unowned cùng 403/message; `BUS-02` transaction cho answer/nháp và rollback hook; `HR-06`/`BUS-09` chấm server; `HR-05` giữ CSRF; `HR-02`/`HR-08` không đụng secret/dữ liệu có bản quyền; `AC-05` không sửa migration hoặc chạy Neon; `ES-01` Java 17, không thêm dependency; `ES-05` gate còn thiếu nên task chưa hoàn tất; `ES-06`/`ES-07` chưa tạo commit/PR mới, giữ nhánh và Draft PR đã duyệt, không merge/rebase/force-push. Kiểm không có BOM/TODO trong phần code thay đổi; `git diff --check` sạch.

**Sửa fixture sau lần verify của leader (2026-10-30):** Leader chạy PowerShell thường với Docker: 580 test, 1 failure, 1 error, 0 skip; hai lỗi đều ở dữ liệu dựng `PracticeAnswerIntegrationTest`.

- AT-03 dùng `NOW.minusNanos(1)`: PostgreSQL lưu TIMESTAMPTZ ở độ chính xác microsecond nên giá trị này làm tròn về NOW, đúng biên hợp lệ theo E-R3 và dẫn đến HTTP 200. Đổi fixture thành `NOW.minusSeconds(1)`; thêm assertion đọc lại DB xác nhận `expires_at < NOW` trước request. Giữ nguyên assertion HTTP 422, trạng thái DB EXPIRED và bằng chứng câu trước đó.
- Fixture SUBMITTED thiếu `submitted_at`, vi phạm `ck_att_submitted` có sẵn trong V1. Bổ sung `submitted_at = NOW` riêng cho SUBMITTED; vẫn kiểm cả SUBMITTED/ABANDONED trả 409 và không ghi bằng chứng.
- File test đã biên dịch lại bằng Java 17; `git diff --check` sạch. Docker trong sandbox vẫn bị từ chối truy cập, nên chưa xác nhận ba ca tương ứng đã xanh sau sửa. T-04 giữ trạng thái đang làm; chưa commit/push, chưa chuyển T-05. Self-check `AC-05`: sửa fixture cho khớp migration hiện có; `ES-05`: giữ đầy đủ assertion nghiệp vụ và chờ gate đạt.

**Leader xác nhận sau sửa (2026-10-30, 08:43 +07):** Ba ca liên quan đã chạy xanh trên PowerShell thường với Docker: 3 test, 0 failure/error/skip, BUILD SUCCESS (02:07 phút). Leader tiếp tục chạy `mvnw verify` đầy đủ. Chờ kết quả verify và JaCoCo mới trước khi đánh dấu T-04 hoàn tất hoặc tạo commit.

**Gate T-04 đạt (2026-10-30, 09:11 +07):** Leader chạy Java 17 `mvnw verify` trên PowerShell thường có Docker: BUILD SUCCESS, 580 test, 0 failure, 0 error, 0 skip. Đã đối chiếu XML Surefire trong workspace: đúng 580/0/0/0. Báo cáo JaCoCo mới tạo lúc 09:11:24 +07 xác nhận line coverage toàn bộ **94,23% (2629/2790)**, package practice và các package con **100% (449/449)**. `git diff --check` sạch. Hai lỗi fixture đã được sửa mà giữ nguyên assertion HTTP/DB và các constraint migration hiện có.

**Self-check khi nghiệm thu:** `AC-01` OpenAPI bổ sung đúng tám mã trước code, ErrorResponse và status endpoint khác nguyên vẹn; `AC-03` dùng AuthApi, chỉ truy cập dữ liệu learning trong bộ máy; `BUS-01` missing/unowned cùng 403/message; `BUS-02`/`HR-06`/`BUS-09` transaction và chấm tại server, test rollback hook/concurrency/EXPIRED đều xanh; `HR-05` CSRF giữ nguyên, SecurityConfig chỉ thêm một matcher; `HR-02`/`HR-08` không đưa secret/PII/dữ liệu bản quyền vào thay đổi; `AC-05` không sửa migration hoặc chạy Neon; `ES-01` Java 17 và không thêm dependency; `ES-05` toàn bộ test không skip, coverage đạt cả ngưỡng toàn bộ 80% và practice 90%; `ES-06`/`ES-07` bàn giao bằng một commit T-04 trên nhánh spec và cùng Draft PR, không merge/rebase main hoặc force-push.

## Q-T05-01 — Phạm vi file để triển khai và kiểm thử finish

**Task:** T-05. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt gộp).

**Quyết định:** Duyệt danh sách file bên dưới và `PracticeSourceHandler.java` theo Q-T05-03. SecurityConfig chỉ thêm đúng một matcher POST finish nếu chưa phủ; OpenAPI chỉ sửa POST finish. PracticeServiceTest chỉ sửa fixture constructor, không sửa assertion cũ. Được tiếp tục local khi fetch lỗi cho T-05 và các task còn lại của đợt 0; phải push lên `spec/003-vocab-topics` trước khi báo xong. Không merge/rebase main hoặc force-push. Một commit T-05, cùng Draft PR #19; gate PowerShell thường có Docker: verify không failure/error/skip, coverage toàn bộ >=80%, practice >=90%, diff sạch.

**Căn cứ:** Cột File của T-05 chỉ cho phép `PracticeService.java`, `event/*`. `PracticeController` chưa có POST finish; `SecurityConfig` chỉ mở hai GET và POST answers cho LEARNER. Chưa có test finish. Contract và bảng spec §13 còn response 404 của finish, trong khi Q-T03-01 đã chốt missing/unowned cùng 403; không hỏi lại quyết định này.

**Đề xuất bổ sung vào cột File của T-05 trước khi code:**

- `src/main/java/com/cnhsk/learning/practice/web/PracticeController.java`: thêm POST finish, DTO request và response theo contract.
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`: chỉ thêm một matcher POST `/api/practice/sessions/{id}/finish` cho LEARNER; giữ nguyên CSRF và các rule cũ.
- `src/test/java/com/cnhsk/learning/practice/PracticeFinishIntegrationTest.java`: AT-13…AT-15, AT-17; kiểm ownership, hết hạn, các chế độ điểm, hook/listener rollback, auth/CSRF và contract.
- `src/test/java/com/cnhsk/learning/practice/PracticeServiceTest.java`: cập nhật fixture khi thêm ApplicationEventPublisher vào constructor; giữ các assertion hiện có.
- `specs/003-vocab-topics/spec.md`, `contracts/openapi-practice-vocab.yaml`: đồng bộ riêng finish trước code, bỏ 404 theo Q-T03-01; chỉ bổ sung quyết định nghiệp vụ mới sau khi leader duyệt.
- `specs/003-vocab-topics/tasks.md`: bổ sung phạm vi được duyệt; chỉ đánh dấu hoàn tất khi đủ gate.
- `specs/003-vocab-topics/questions.md`: thêm quyết định và báo cáo T-05; giữ nguyên thay đổi bàn giao T-04 có sẵn trong worktree.

Ghi `learning.study_events` bằng INSERT tham số hóa qua EntityManager sẵn có, trong cùng transaction của finish; không cần thêm entity, migration hoặc dependency.

**Kiểm tra Git và môi trường:** `origin/main` cục bộ tại `f8e173d` có spec 001 hoàn tất, spec 002 T-01…T-03 hoàn tất và Q5 đã duyệt; chưa xác minh được main mới nhất vì `git fetch origin` tiếp tục lỗi kết nối GitHub cổng 443. Nhánh/PR dùng chung đã có quyết định tại Q-T03-03/Q-T04-03; không tự chuyển nhánh, merge/rebase hoặc force-push. Ngoại lệ cho phép làm local khi fetch lỗi trước đây nêu riêng T-04: cần leader xác nhận có áp dụng cho T-05, hoặc khôi phục fetch trước khi triển khai. Docker hiện bị từ chối truy cập named pipe; Java 17 có sẵn. Chưa chạy test T-05, không dùng kết quả T-04 thay gate T-05.

## Q-T05-02 — Giới hạn trả lời khi gửi answers[] ngay trong finish

**Task:** T-05. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt gộp).

**Quyết định:** Mỗi phần tử mới trong finish.answers[] tính vào giới hạn; nháp đã lưu không tính lại. Dùng đúng `RATE_LIMITED` của T-04, không thêm ErrorCode. Vượt giới hạn trả 429 cùng retryAfterSeconds; toàn bộ finish rollback, lượt vẫn IN_PROGRESS, nháp cũ giữ nguyên. Redis lỗi xử lý như T-04. Thêm 429 chỉ vào POST finish và có test.

**Căn cứ:** E-F1 cho phép DEFERRED gửi `answers[]` trực tiếp khi finish; E-R12 giới hạn 600 câu trả lời/giờ, nhưng FR-004 gắn E-R1…E-R12 với POST answers. Contract finish chưa khai báo 429. Spec chưa xác định batch finish có tính giới hạn này không, tính theo từng phần tử hay từng số câu khác nhau, và có tính lại nháp đã lưu hay không.

**Đề xuất để duyệt:** Mỗi phần tử `answers[]` được gửi mới khi finish tính một lần qua `PracticeRateLimiter.checkAnswer`, kể cả cùng `no` xuất hiện nhiều lần; xử lý ghi đè theo thứ tự E-F1. Nháp đã lưu trước đó và câu thiếu không tính lại. Vượt giới hạn trả 429 `RATE_LIMITED` + `retryAfterSeconds`, không lưu thay đổi finish; Redis lỗi thì bỏ qua giới hạn và log cảnh báo như E-R12. Bổ sung mô tả E-F1 và response 429 riêng của finish trước code. Không cần đổi PracticeRateLimiter hoặc thêm mã lỗi.

## Q-T05-03 — Cách nguồn báo suspicious cho E-F5

**Task:** T-05. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt gộp).

**Quyết định:** Thêm default isSuspicious(FinishContext) trả false, OR với cờ chung trước onFinish; giữ chữ ký onFinish. Response, context onFinish và SessionFinishedEvent dùng cùng cờ cuối. Mẫu số tỷ lệ E-F5 chỉ gồm câu gradable có đáp án thật, không thiếu/skip/RECORD/audioFailed; so suspiciousCount * 100 >= answeredCount * 30, mẫu số 0 không gắn cờ theo tỷ lệ. Có test 20 câu, ba câu trả lời đều nhanh, 17 câu trống vẫn suspicious=true. E-F5 cũ không quy định rõ mẫu số khác, nên đây là làm rõ đã duyệt. Listener @EventListener đồng bộ trong transaction; không AFTER_COMMIT. study_events ưu tiên entity/repository nếu có, hiện chưa có nên dùng INSERT tham số qua EntityManager; không query điều kiện trên JSONB. EXPIRED trả 422 bằng đúng cơ chế T-04. Luật thời gian riêng TOPIC_TEST vẫn thuộc T-12.

**Căn cứ:** E-F5 yêu cầu suspicious nếu luật riêng của nguồn báo; FR-038 có luật thời gian riêng của TOPIC_TEST. `PracticeSourceHandler` hiện chỉ có `onFinish(FinishContext) -> Map` (extra), `validateAnswer`, `enrichMeta`. FinishContext là record bất biến, không có hook để nguồn quyết định suspicious của lượt trước khi lưu và phát SessionFinishedEvent. Spec chưa xác định dùng trường nào trong extra hay thêm hook.

**Đề xuất để duyệt:** Thêm phương thức mặc định `boolean isSuspicious(FinishContext context)` trả false vào `src/main/java/com/cnhsk/learning/practice/PracticeSourceHandler.java`, bổ sung file này vào cột File của T-05. Engine kết hợp kết quả hook với ngưỡng 30% E-F5 trước khi lưu, gọi onFinish và phát SessionFinishedEvent; hook chạy trong cùng transaction. T-05 kiểm nguồn giả báo cờ; luật thời gian TOPIC_TEST được handler của T-12 triển khai theo FR-038. Sau duyệt, ghi hook vào spec trước code; không đổi schema HTTP.

**Kiểm chứng triển khai T-05 (2026-10-30, chưa hoàn tất):**

- Đã đồng bộ các quyết định gộp vào ba mục Q-T05 hiện có, E-F1/E-F5 và phạm vi T-05; không tạo mục trùng. Cập nhật contract trước code; so sánh với HEAD xác nhận toàn bộ OpenAPI ngoài POST finish nguyên vẹn. SecurityConfig chỉ thêm đúng một matcher; ErrorCode không thay đổi; PracticeServiceTest chỉ đổi fixture constructor, giữ toàn bộ assertion cũ.
- Đã triển khai finish với khóa dòng, nháp DEFERRED ghi đè theo thứ tự, chấm server, câu thiếu sai nhưng không nằm trong mẫu số suspicious, RECORD/audioFailed không tính điểm, hook isSuspicious trước onFinish, cùng cờ cuối trong response/context/event. study_events chưa có entity/repository nên dùng INSERT tham số qua EntityManager; ngày Việt Nam, cùng transaction. ItemGradedEvent phát khi ghi mới (gồm preRecorded/RECORD), không phát khi chỉ lưu nháp hoặc phát lại ở finish; SessionFinishedEvent sau onFinish. Listener kiểm thử dùng @EventListener đồng bộ; không AFTER_COMMIT.
- Toàn bộ main và test biên dịch thành công bằng javac Java 17 với thư viện sẵn có trong workspace. Bộ test T-05 hiện có 35 ca integration (gồm parameterized), đã biên dịch; thêm ca game refId NULL/audioFailed sau lần verify đầy đủ. JUnit Platform chạy riêng 86 test hồi quy practice: 86 pass, không failure/skip. Bộ probe tạm ở target/T05FinishProbe.java chạy độc lập 16 ca kiểm logic mới: 16 pass; probe dùng mock, không thay thế kiểm chứng transaction/DB bằng Testcontainers.
- Maven in-process ban đầu lỗi AccessDeniedException khi đóng antlr4-runtime trong cache. Dùng Java 17, Maven offline với cache hiện có và compiler fork đã vượt biên dịch và chạy verify: 607 test, 342 pass, 0 failure, 265 error, 0 skip; các error bắt nguồn Testcontainers không khởi tạo vì AccessDeniedException với Docker named pipe. Chưa có gate xanh hoặc JaCoCo mới đạt ngưỡng; không dùng báo cáo coverage T-04 làm kết quả T-05. Log: target/t05-verify.log.
- Push dry-run lên spec/003-vocab-topics vẫn lỗi kết nối GitHub cổng 443. Chưa commit/push T-05, chưa đánh dấu hoàn tất, chưa chuyển sang T-06. Đã đề nghị leader chạy Java 17 mvnw verify trên PowerShell thường có Docker và gửi kết quả test + JaCoCo.
- Self-check Constitution: AC-01 contract cập nhật trước endpoint và chỉ sửa POST finish; AC-03 controller dùng AuthApi, bộ máy chỉ truy cập dữ liệu learning, không trả entity; BUS-01 missing/unowned cùng 403/message; BUS-02/AC-10 kết quả, study_events, hook và sự kiện trong cùng transaction; HR-06/BUS-09 chấm tại server; HR-05 giữ CSRF và quyền LEARNER; HR-07/HR-09 validate và lỗi theo định dạng chung; HR-02/BUS-06/HR-08 không đọc secret hoặc đưa PII/dữ liệu bản quyền vào thay đổi; AC-05 không đổi migration/schema, không chạy Neon; AC-07 điểm Long/BIGINT; AC-08 thời gian UTC và local_date theo Việt Nam; AC-09 không query điều kiện JSONB; ES-01/02 Java 17, không thêm dependency; ES-05 còn thiếu gate nên giữ đang làm; ES-06/07 chưa commit/PR mới, giữ nhánh và Draft PR #19, không merge/rebase/force-push. Phạm vi 12 file đã được duyệt; thay đổi có sẵn của questions.md và file env được giữ nguyên. Kiểm BOM/TODO sạch, git diff --check sạch.

**Gate T-05 đạt (2026-10-30, 11:08 +07):** Leader chạy Java 17 `mvnw verify` trên PowerShell thường có Docker: BUILD SUCCESS, 615 test, 0 failure, 0 error, 0 skip. Đã đối chiếu XML Surefire trong workspace: đúng 615/0/0/0, riêng PracticeFinishIntegrationTest 35/0/0/0. JaCoCo mới tạo lúc 11:08:10 xác nhận line coverage toàn bộ **94,55% (2792/2953)**, practice và các package con **100% (611/611)**. Không thay code sau lần verify này; chỉ cập nhật nghiệm thu trong tài liệu. `git diff --check` sạch. Chuẩn bị một commit T-05; phải push thành công trước khi báo bàn giao hoàn tất hoặc bắt đầu T-06.

**Self-check khi nghiệm thu T-05:** AC-01 cập nhật contract trước code, chỉ POST finish đổi; AC-03 AuthApi và DTO, chỉ truy cập schema learning; BUS-01 missing/unowned cùng 403/message; BUS-02/AC-10 transaction chung cho kết quả, nhật ký học, hook và sự kiện đồng bộ, đã kiểm rollback; HR-06/BUS-09 server tính điểm và cờ suspicious theo quyết định leader; HR-05 giữ nguyên CSRF và chỉ thêm một matcher LEARNER; HR-07 input validation và SQL tham số hóa; HR-09 định dạng lỗi chuẩn; HR-02/BUS-06/HR-08 không secret, PII hoặc dữ liệu bản quyền; AC-05 không đổi schema/migration hoặc chạy Neon; AC-07 điểm Long/BIGINT; AC-08 UTC và ngày học Việt Nam; AC-09 không query điều kiện JSONB; ES-01/02 giữ stack và dependency; ES-05 đủ gate, test không skip, coverage vượt 80%/90%; ES-06 một commit Conventional Commits T-05; ES-07 giữ spec/003-vocab-topics và Draft PR #19, không merge/rebase main hoặc force-push. Giữ nguyên thay đổi bàn giao T-04 có sẵn trong questions.md và file env ngoài phạm vi task.

**Bàn giao T-05:** Commit `182475425453a138273fe6759f0b62163c352eb6` — `feat(practice): implement session finish and events (T-05)` đã push lên `spec/003-vocab-topics`; HEAD và origin/spec/003-vocab-topics cùng commit. Đúng 12 file đã duyệt; dòng bàn giao T-04 có sẵn và file env giữ ngoài commit. PR #19 có commit T-05; đã cập nhật và xác nhận tiêu đề `feat(practice): add practice sessions, grading and finish events (T-01…T-05)`, vẫn Draft. Mô tả PR chưa bổ sung báo cáo T-05. Không merge hoặc chuyển Ready. Gate 615/0/0/0, coverage toàn bộ 94,55%, practice 100%; không còn câu hỏi nghiệp vụ T-05 mở.

## Q-T06-01 — Shadow Plan và phạm vi file T-06

**Task:** T-06. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt Shadow Plan, sửa phương án scheduler).

**Quyết định áp dụng:** Duyệt phạm vi file bên dưới. PracticeMaintenance luôn có bean, không dùng @Profile("!test"). Cron là `${cnhsk.practice.expire-cron:0 5 * * * *}`, zone Asia/Ho_Chi_Minh; profile test đặt `cnhsk.practice.expire-cron=-` để tắt lịch, test gọi job trực tiếp và kiểm annotation/property. Kiểm nhánh hiện tại chưa có @EnableScheduling, nên thêm đúng một class @Configuration `PracticeSchedulingConfiguration.java` trong learning/practice, không sửa CnhskApplication/shared config; ghi rõ trong PR để leader gộp nếu trùng spec khác. Bổ sung hai file được chỉ định bởi quyết định này vào cột File: `src/main/java/com/cnhsk/learning/practice/PracticeSchedulingConfiguration.java`, `src/test/resources/application-test.yaml`. Các phần đề xuất scheduler bằng profile bên dưới là lịch sử và được thay thế bởi quyết định này.

Job UPDATE tham số hóa chỉ IN_PROGRESS và expires_at < now; flush/clear persistence context để không dùng trạng thái cũ sau bulk update. Abandon giữ đúng plan; đường EXPIRED dùng cùng transaction trả lỗi sau commit của T-04, không thêm cơ chế khác. SecurityConfig chỉ thêm một matcher nếu chưa phủ; OpenAPI chỉ sửa POST abandon. Không thêm ErrorCode/dependency/migration, không chạy Neon. Gate PowerShell thường có Docker: verify không failure/error/skip, coverage toàn bộ >=80%, practice >=90%, diff sạch; một commit T-06, push nhánh/PR hiện tại, không merge/rebase/force-push. Sau bàn giao T-06 mới lập Shadow Plan T-07.

**Mục tiêu và căn cứ:** Hết hạn lazy, job E-F9, giữ bằng chứng E-F8 và POST abandon §0.11; FR-001/FR-008/FR-010, AT-16/AT-16b/AT-16c. T-05 đã đủ gate và push; giữ nhánh/PR chung, ngoại lệ fetch lỗi cho đợt 0 đã chốt ở Q-T05-01, không hỏi lại.

**File đọc:** AGENTS.md; Constitution (AC-03/05/08/09, BUS-01/02, HR-05/09); spec 001 §0; spec 003 §0.6/0.8/0.11, §5, §13/14, OpenAPI POST abandon và tasks.md; PracticeService, PracticeSource, PracticeController, Attempt/AttemptItem và repository, SecurityConfig; các test practice hiện có, AbstractIntegrationTest, pom.xml; Lexeme/LexemeRepository chỉ để dựng dữ liệu test trong PostgreSQL cô lập. Spec 003 chưa có plan.md. Đã kiểm tra hiện chưa có @EnableScheduling/@Scheduled trong production.

**File sửa/tạo theo cột File hiện tại:**

- `src/main/java/com/cnhsk/learning/practice/PracticeService.java`.
- `src/main/java/com/cnhsk/learning/practice/PracticeMaintenance.java` (mới).

**Xin bổ sung vào cột File T-06 trước khi code:**

- `src/main/java/com/cnhsk/learning/practice/web/PracticeController.java`: POST abandon trả 204, lấy danh tính qua AuthApi.
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`: kiểm trước, chỉ thêm đúng một matcher POST `/api/practice/sessions/{id}/abandon` cho LEARNER nếu chưa phủ; giữ CSRF và rule cũ.
- `src/test/java/com/cnhsk/learning/practice/PracticeMaintenanceIntegrationTest.java` (mới).
- `src/test/java/com/cnhsk/learning/practice/PracticeAbandonIntegrationTest.java` (mới).
- `specs/003-vocab-topics/spec.md`, `contracts/openapi-practice-vocab.yaml`: chỉ đồng bộ POST abandon, bỏ 404 theo Q-T03-01 đã chốt; không đổi endpoint khác hoặc schema dùng chung.
- `specs/003-vocab-topics/tasks.md`, `questions.md`: ghi phạm vi, quyết định và nghiệm thu; giữ nguyên thay đổi có sẵn.

**Cách làm đề xuất:**

1. Giữ cơ chế EXPIRED hiện có của view/answer/finish; bổ sung abandon dùng cùng khóa dòng và cơ chế transaction T-04/T-05. Missing/unowned cùng 403; trạng thái kết thúc 409; quá hạn lưu EXPIRED rồi trả 422; đúng biên expiresAt = now vẫn hợp lệ.
2. Abandon chỉ chuyển IN_PROGRESS sang ABANDONED, trả 204; không điểm, study_events, hook finish hoặc sự kiện. Giữ nguyên attempt_items, snapshot và nháp đã có.
3. PracticeMaintenance dùng Clock và một lệnh UPDATE tham số hóa trong transaction: chỉ IN_PROGRESS với expires_at < now chuyển EXPIRED. Cập nhật có điều kiện để khi đợi khóa của finish/abandon, DB kiểm lại trạng thái; không ghi đè SUBMITTED/ABANDONED. Không đổi/xóa bằng chứng, không lọc trên JSONB; dùng EntityManager, không cần sửa repository hoặc migration.
4. Job giữ đúng `@Scheduled(cron="0 5 * * * *", zone="Asia/Ho_Chi_Minh")`. Đăng ký scheduling trong chính file maintenance; giới hạn cấu hình bật scheduler ngoài profile test, còn integration test gọi job trực tiếp và kiểm cron/zone, tránh lịch chạy nền của nhiều Spring context làm test tranh chấp.

**Test sẽ viết/chạy:**

- `FR008_AT16_...`: học 4/10 thẻ rồi hết hạn bằng job/lazy, vẫn còn bốn lexeme đã ghi; điểm, nhật ký và sự kiện không được sinh khi hết hạn.
- `FR001_...`: job chỉ đổi các lượt IN_PROGRESS quá hạn; bỏ qua đúng biên/chưa hết hạn và trạng thái kết thúc, chạy lại không đổi thêm; cron/zone đúng spec; kiểm cạnh tranh với khóa finish/abandon.
- `FR008_AT16b_...`: abandon sau ba câu giữ đủ ba dòng, không study_events/sự kiện/điểm; abandon lại trả 409; ownership, quá hạn, path sai, LEARNER và cookie CSRF; abandon/finish đồng thời chỉ một chuyển trạng thái thành công.
- AT-16c chạy lại test game maxScoreFromServed đã có trong PracticeFinishIntegrationTest (20 câu đã phát, trả lời 12, đúng 10 => 10/20), không sửa assertion T-05.

**Lệnh dự kiến sau duyệt:** Java 17; `mvnw.cmd '-Dtest=PracticeMaintenanceIntegrationTest,PracticeAbandonIntegrationTest,PracticeFinishIntegrationTest,PracticeControllerIntegrationTest,PracticeAnswerIntegrationTest' test`; `mvnw.cmd verify`; kiểm XML Surefire/JaCoCo, BOM/TODO và `git diff --check`. Gate PowerShell thường có Docker: không failure/error/skip, coverage toàn bộ >=80%, practice >=90%. Sau gate: chỉ stage từng file đã duyệt, một commit `feat(practice): expire and abandon practice sessions (T-06)`, push nhánh hiện tại, giữ PR #19 Draft. Không thêm dependency hoặc chạm Neon.

**Rủi ro và câu hỏi leader:** Job tranh chấp với finish/abandon và scheduler chạy trong nhiều test context; xử lý bằng UPDATE có điều kiện, transaction/khóa, test đồng thời và tắt đăng ký scheduler ở profile test. Docker trong phiên hiện tại vẫn hạn chế; gate thực hiện trên PowerShell thường như T-05. Leader vui lòng duyệt bổ sung phạm vi file và cách đăng ký scheduling nêu trên; các quyết định ownership, TTL, maxScoreFromServed, nhánh/PR và fetch không hỏi lại. Chỉ triển khai sau “duyệt”.

**Kiểm chứng triển khai T-06 (2026-10-30, chưa hoàn tất):**

- Đã ghi quyết định Chốt vào Q-T06-01 hiện có, bổ sung phạm vi task và đồng bộ contract trước code. Đối chiếu với HEAD xác nhận OpenAPI ngoài POST abandon không thay đổi. Bỏ riêng 404 của abandon theo Q-T03-01, giữ ErrorResponse và mọi endpoint khác. SecurityConfig chỉ thêm đúng một matcher LEARNER cho POST abandon; ErrorCode, dependency và migration nguyên vẹn.
- PracticeMaintenance luôn tồn tại, không @Profile. Job dùng Clock, UPDATE native có tham số với status IN_PROGRESS và expires_at < now, flush trước/clear sau bulk update. PracticeSchedulingConfiguration là class @Configuration duy nhất thêm @EnableScheduling trong learning/practice, không sửa CnhskApplication/shared config; cần nêu trong PR để leader gộp nếu spec khác cũng bật scheduling. Annotation dùng cron property có mặc định phút 05 mỗi giờ và zone Asia/Ho_Chi_Minh; application-test.yaml chỉ thêm cnhsk.practice.expire-cron="-". Không chạy scheduler thật trong test.
- Abandon dùng cùng TransactionTemplate REQUIRES_NEW của T-04/T-05, khóa dòng và hàm kiểm tra trạng thái được dùng chung bởi answer/finish/abandon; EXPIRED commit trước 422. Không tạo cơ chế transaction thứ hai. Thành công trả 204, chỉ đổi status; không tính điểm, study_events, hook finish hoặc sự kiện; giữ snapshot, nháp, các câu đã ghi.
- Hai file integration test mới có 25 ca (maintenance 11, abandon 14): AT-16 trên năm đường job/view/answer/finish/abandon; giữ bốn lexeme và toàn bộ bằng chứng, nháp audio; biên hết hạn, idempotency, flush/clear; quan sát PostgreSQL chờ khóa rồi giữ trạng thái SUBMITTED/ABANDONED sau commit; abandon/finish và hai abandon đồng thời; role/CSRF/path/ownership và contract. Test dùng PostgreSQL Testcontainers và fixture JPA, không chèn demo SQL hoặc chạm Neon. AT-16c vẫn chạy qua test game finish T-05, không đổi assertion cũ.
- Toàn bộ main/test biên dịch thành công bằng javac Java 17 với thư viện sẵn có trong workspace. Chạy JUnit Platform độc lập: 111 test xanh, không failure/skip, gồm 86 test hồi quy và 25 ca probe tạm (16 ca finish + 9 ca abandon/scheduler). Probe dùng mock, không thay thế integration/DB gate. Maven fork test-compile trả BUILD SUCCESS nhưng có diagnostic AccessDeniedException khi đóng jar antlr4-runtime trong cache; dùng javac riêng để xác nhận biên dịch sạch.
- Lần mvnw verify trong phiên này: 633 test, 342 pass, 0 failure, 291 error, 0 skip; nguyên nhân khởi tạo Testcontainers thất bại do quyền truy cập Docker named pipe. XML mới xác nhận maintenance 11 và abandon 14 ca đều chưa thực thi được vì hạ tầng; không coi là test pass/skip. Log target/t06-verify.log. Chưa có JaCoCo mới đạt gate, không dùng coverage T-05 thay thế. Đã đề nghị leader chạy Java 17 mvnw verify trên PowerShell thường có Docker và gửi Surefire/JaCoCo.
- Self-check Constitution: AC-01 contract trước code, chỉ POST abandon; AC-03 controller dùng AuthApi, dữ liệu nghiệp vụ chỉ schema learning, không trả entity; BUS-01 missing/unowned cùng 403/message và kiểm ownership trước đổi trạng thái; BUS-02 transaction/khóa, hết hạn không mất bằng chứng; HR-05 giữ CSRF; HR-07 query tham số và validate path; HR-09 lỗi chuẩn; HR-02/BUS-06/HR-08 không đọc secret hoặc thêm PII/dữ liệu bản quyền; AC-05 không đổi schema/migration, không chạy Neon; AC-07 không đổi kiểu điểm BIGINT; AC-08 Clock/Instant UTC và zone job Việt Nam; AC-09 không query điều kiện JSONB; ES-01/02 giữ stack/dependency; ES-05 giữ đang làm vì gate Docker chưa đạt; ES-06/07 chưa commit/push T-06, giữ nhánh và Draft PR #19, không merge/rebase/force-push. Đúng 12 file được duyệt; thay đổi có sẵn trong questions.md và file env giữ nguyên. Không BOM/TODO, git diff --check sạch. Chưa chuyển sang T-07.

**Gate T-06 đạt (2026-10-30, 12:14 +07):** Leader chạy Java 17 `mvnw verify` trên PowerShell thường có Docker: BUILD SUCCESS, 640 test, 0 failure/error/skip. Đã đối chiếu XML Surefire: đúng 640/0/0/0, maintenance 11/0/0/0 và abandon 14/0/0/0; ModuleBoundaryTest 8 ca xanh. JaCoCo mới tạo lúc 12:14:55 xác nhận line coverage toàn bộ **94,59% (2814/2975)**, practice và các package con **100% (632/632)**. Không đổi code sau lần verify này; chỉ cập nhật tài liệu nghiệm thu. `git diff --check` sạch. Một commit T-06 trên nhánh đã chốt; phải push thành công trước khi báo bàn giao hoàn tất và lập Shadow Plan T-07.

**Self-check khi nghiệm thu T-06:** AC-01 OpenAPI trước code, chỉ POST abandon đổi; AC-03 AuthApi và DTO, chỉ dữ liệu learning; BUS-01 missing/unowned cùng 403/message, không đổi trạng thái trước kiểm ownership; BUS-02 transaction/khóa, giữ bằng chứng khi hết hạn/bỏ lượt; HR-05 giữ CSRF, thêm đúng một matcher LEARNER; HR-07 query tham số và validate path; HR-09 lỗi chuẩn; HR-06/BUS-09 không nhận hoặc tính điểm client khi abandon; HR-02/BUS-06/HR-08 không secret, PII thật hoặc dữ liệu bản quyền; AC-05 không đổi schema/migration hoặc chạy Neon; AC-07 giữ BIGINT; AC-08 Clock/Instant UTC và cron zone Việt Nam; AC-09 không query điều kiện JSONB; ES-01/02 giữ stack/dependency; ES-05 gate đủ, không skip và coverage vượt 80%/90%; ES-06 một commit Conventional Commits T-06; ES-07 giữ nhánh/Draft PR #19, không merge/rebase main hoặc force-push. PracticeMaintenance luôn có bean; profile test chỉ tắt cron bằng '-'; PracticeSchedulingConfiguration bật scheduling trong learning/practice, cần nêu trong PR để leader gộp nếu trùng spec khác. Giữ thay đổi có sẵn ngoài task.

**Bàn giao T-06:** Commit `6b090dac72629407490eae44eba09f7a811128ef` — `feat(practice): expire and abandon practice sessions (T-06)` đã push lên spec/003-vocab-topics; HEAD và origin/spec/003-vocab-topics cùng commit. Đúng 12 file được duyệt; dòng bàn giao T-04 có sẵn và file env giữ ngoài commit. Đã xác nhận qua UI PR #19 có sáu commit, giữ Draft; tiêu đề `feat(practice): add practice session lifecycle and grading (T-01…T-06)`. Mô tả đã bổ sung báo cáo T-05/T-06, gate 640 test/94,59%/100% và lưu ý PracticeSchedulingConfiguration để leader gộp khi trùng spec khác; đối chiếu nội dung cũ trước khi sửa, giữ nguyên phần T-01…T-04. Bản nội dung lưu tại target/t06-pr-body.md. Không merge/chuyển Ready; không còn câu hỏi nghiệp vụ T-06 mở.

## Q-T07-01 — Shadow Plan và phạm vi file T-07

**Task:** T-07. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt Shadow Plan và các điều chỉnh).

**Quyết định áp dụng:** Duyệt đúng năm file factory, hai test, tasks.md/questions.md. Kết quả record nội bộ {options, correctAnswer, mixedDistractors}; thiếu nhiễu trả Optional.empty, không null, WARN kèm lexemeId; caller OR mixedDistractors vào sessionMeta. Inject RandomGenerator qua constructor, bean mặc định ThreadLocalRandom.current() hoặc tương đương thread-safe; unit test dùng seed cố định và giữ vòng 100 lượt kiểm bất biến. Bean cấu hình random đặt lồng trong file factory để không mở rộng phạm vi file. Query candidate lọc từng tầng G-1 ở DB và có LIMIT, không tải toàn bảng vào bộ nhớ, không query điều kiện JSONB. correctAnswer chỉ dành cho snapshot server; test không lộ đáp án hiện có phải giữ xanh. Không đổi OpenAPI/endpoint, repository, dependency hoặc migration.

**Đối chiếu G-3 trước code:** G-3 định nghĩa đồng âm bằng `PinyinUtils.toNumbered(pinyin)` bỏ khoảng trắng; PinyinUtils hiện có giữ thanh dưới dạng số. Vì vậy shì -> shi4 và shí -> shi2 là khác nhau; không dùng PinyinUtils.normalize (bỏ thanh), không viết thuật toán chuẩn hóa mới. Spec đã xác định nên không cần hỏi lại về thanh điệu.

Gate factory test rồi Java 17 mvnw verify trên PowerShell thường có Docker: không failure/error/skip, coverage toàn bộ >=80%, practice >=90%, diff sạch. Một commit T-07, push spec/003-vocab-topics, Draft PR #19, không merge/rebase main. Sau bàn giao T-07 mới lập Shadow Plan T-08. Các đề xuất ThreadLocalRandom trực tiếp bên dưới được thay bởi quyết định inject RandomGenerator này.

**Mục tiêu và căn cứ:** ChoiceItemFactory theo G-1…G-6 (§0.10), FR-011; AT-19/20/21, SC-004; tương thích FR-026/027/037 của các task chủ đề phía sau. T-06 đã đủ gate và push; giữ nhánh/PR và ngoại lệ fetch của đợt 0 đã chốt, không hỏi lại.

**File đọc:** AGENTS.md, Constitution (AC-03/09, HR-06/07/09, ES-01/02/05); spec 001 §0, spec 002 §4 và FR-031…033; spec 003 §0.3/0.4/0.7/0.10, FR-011/026/027/028/037, §14; contracts/openapi-practice-vocab.yaml, tasks.md, questions.md. Code: ServedItem/ServedItemView/ItemType/Skill; Lexeme/LexemeExtra/TopicItem và repository; TextNormalizer/PinyinUtils; test content/text và practice liên quan, AbstractIntegrationTest, pom.xml. Spec 003 chưa có plan.md. Không đọc/sửa feature khác nếu không cần.

**File sửa/tạo trong cột File hiện tại:** `src/main/java/com/cnhsk/learning/practice/generate/ChoiceItemFactory.java` (mới).

**Xin bổ sung vào cột File trước khi code:**

- `src/test/java/com/cnhsk/learning/practice/generate/ChoiceItemFactoryTest.java` (mới): G-1…G-6, 100 lượt cho các bất biến AT-19/20, tập ứng viên thiếu được kiểm soát cho AT-21.
- `src/test/java/com/cnhsk/learning/practice/generate/ChoiceItemFactoryIntegrationTest.java` (mới): kiểm truy vấn candidate tiers trên PostgreSQL Testcontainers với fixture JPA, ưu tiên chủ đề và chuyển tầng, tương thích DTO.
- `specs/003-vocab-topics/tasks.md`, `questions.md`: phạm vi, quyết định và nghiệm thu; không sửa spec/OpenAPI nếu không phát hiện cần đổi nghiệp vụ/HTTP contract.

**Cách làm đề xuất để duyệt:**

1. Factory chọn nhiễu theo đúng thứ tự G-1: cùng chủ đề nếu có, rồi cùng kind/cùng hsk_level, rồi cùng kind/mọi cấp. Xáo ngẫu nhiên trong từng tầng; chỉ xuống tầng sau khi tầng trước không đủ ứng viên hợp lệ. Truy vấn tham số hóa trong schema learning qua EntityManager, không thêm method/sửa repository hoặc lọc điều kiện JSONB.
2. Loại cùng id/text; tách meaning_norm bằng cả ';' và ',', trim từng mảnh và loại các mảnh nghĩa trùng theo G-2. Đề xuất kiểm với đáp án đúng và các phương án đã chọn để tránh nhiễu trùng nhau. Câu nghe chọn chữ áp dụng thêm G-3 bằng PinyinUtils.toNumbered rồi bỏ khoảng trắng; không dùng chuẩn hóa bỏ thanh.
3. Hiển thị phương án nghĩa bằng phần meaning_vi trước ';' theo G-5; phương án chữ dùng text. Đủ optionCount-1 nhiễu mới sinh kết quả; thiếu trả kết quả rỗng và WARN chỉ kèm lexemeId, không tự thêm ErrorCode hoặc ném lỗi HTTP của T-11.
4. Đề xuất kết quả nội bộ là record lồng trong factory, chứa options, correctAnswer và mixedDistractors; trả Optional.empty khi thiếu nhiễu. Cờ true khi có topicId và thực sự dùng ít nhất một nhiễu ngoài chủ đề; caller sau này OR cờ vào sessionMeta để lưu ở cấp lượt. T-07 không sửa PracticeService hoặc lắp endpoint T-11/T-12.
5. Dùng ThreadLocalRandom trộn toàn bộ phương án sau khi chọn, rồi gán key A/B/C/... và tìm đúng key đáp án sau trộn. Đáp án đúng thuộc dữ liệu server; DTO ServedItemView tiếp tục không có answer/accept. Không giới hạn backend về HSK 1–3: giữ candidate/API nhận 1–9.

**Test sẽ viết/chạy:**

- `FR011_AT19_...`: 爸爸/父亲 cùng nghĩa 'bố', chạy 100 lượt, không cùng xuất hiện trong câu chọn nghĩa. Thêm trường hợp nhiều nghĩa, dấu ','/';', khoảng trắng và trùng id/text.
- `FR011_AT20_...`: 是/事 cùng shì, chạy 100 lượt nghe, không cùng xuất hiện; cùng âm khác thanh không bị đánh đồng.
- `FR011_AT21_...`: chủ đề có ba từ, cùng cấp có thêm năm từ => đủ nhiễu và mixedDistractors; pool toàn bộ chỉ ba từ => không sinh câu/WARN. Pool nhỏ kiểm bằng unit test cô lập, không xóa/truncate dữ liệu của các suite Testcontainers khác.
- Kiểm G-1 ưu tiên từng tầng và không chọn lại một lexeme; G-5 hiển thị nghĩa đầu nhưng đối chiếu toàn bộ nghĩa; G-6 key liên tục, đủ số phương án, đáp án vẫn đúng sau trộn; không có answer/accept trong ServedItemView. Test ngẫu nhiên chỉ kiểm bất biến trong 100 lượt, không đặt kỳ vọng xác suất dễ gây flaky.

**Lệnh dự kiến sau duyệt:** Java 17; thử git fetch origin và áp dụng ngoại lệ đã chốt nếu lỗi; `mvnw.cmd '-Dtest=ChoiceItemFactoryTest,ChoiceItemFactoryIntegrationTest' test`, `mvnw.cmd verify`; đối chiếu Surefire/JaCoCo, BOM/TODO và git diff --check. Gate PowerShell thường có Docker: không failure/error/skip, toàn bộ >=80%, practice >=90%. Sau gate chỉ stage từng file được duyệt; một commit `feat(practice): generate validated choice options (T-07)`, push nhánh hiện tại, cập nhật Draft PR #19. Không dependency/migration/Neon/merge/rebase/force-push.

**Rủi ro và điểm cần leader duyệt:** So trùng nghĩa/đồng âm sai làm có nhiều đáp án hoặc thiếu nhiễu; kiểm bất biến và tập dữ liệu biên. Dữ liệu Testcontainers dùng chung có thể làm sai case 'toàn DB ba từ', nên dùng pool unit cô lập và integration chỉ kiểm truy vấn/ưu tiên trên fixture xác định. Leader vui lòng duyệt phạm vi test và lựa chọn kết quả nội bộ/kiểm phương án/cờ mixedDistractors ở trên. Nếu phát hiện nghiệp vụ chưa rõ khi triển khai, dừng và ghi bổ sung tại đây trước khi sửa ngoài phạm vi. Chỉ code sau “duyệt”.

**Kiểm chứng triển khai T-07 (2026-10-30, chưa hoàn tất):**

- Đã ghi Chốt vào Q-T07-01 hiện có và đúng phạm vi năm file. Không sửa spec/OpenAPI, endpoint, entity/repository, shared config, ErrorCode, dependency hoặc migration. T-07 trong tasks.md vẫn đang làm; chưa commit/push, chưa chuyển T-08.
- Factory inject RandomGenerator qua constructor; bean practiceChoiceRandom mặc định ThreadLocalRandom.current() đặt trong @Configuration lồng ở chính file factory. Unit dùng seed 42 và có test phát lại cùng chuỗi kết quả. Lấy ngẫu nhiên điểm bắt đầu trong từng tầng, đọc tuần tự theo vòng qua các trang có LIMIT tối đa 64, trộn trang và phương án bằng generator được inject. COUNT là aggregate có giới hạn một dòng; không tải toàn bảng lexeme thành danh sách trong bộ nhớ. Các tầng DB tách biệt theo topic/kind/hsk_level để không chọn lại dữ liệu tầng trước; chỉ xuống tầng sau khi đã kiểm hết tầng trước mà chưa đủ phương án.
- G-2 dùng meaning_norm (fallback TextNormalizer hiện có khi thiếu), tách ';'/',' và trim bằng utility hiện có, đối chiếu mọi mảnh với đáp án đúng và nhiễu đã chọn. G-3 dùng đúng PinyinUtils.toNumbered bỏ khoảng trắng, giữ số thanh; test 100 lượt có 是/事 cùng shì bị loại và 时/shí vẫn được dùng. Không viết thuật toán chuẩn hóa pinyin mới. G-5 hiện nghĩa trước ';'; G-6 gán key sau trộn. Thiếu trả Optional.empty và WARN chỉ lexemeId. Record nội bộ đúng options/correctAnswer/mixedDistractors; correctAnswer còn được đánh dấu JsonIgnore, caller đọc accessor để tạo ServedItem snapshot phía server, không phải response.
- Hai file test mới gồm 26 unit và 8 integration. Unit kiểm seed, ba vòng 100 lượt về nghĩa/đồng âm/nhiễu trùng, paging vượt trang bị loại, ưu tiên tầng, HSK khác cấp, dấu phân cách/khoảng trắng, key sau trộn, Optional/WARN và snapshot/DTO không lộ đáp án. Integration dùng DataJpaTest + PostgreSQL 18 Testcontainers riêng và fixture JPA, rollback mỗi ca, nên kiểm được cả pool toàn DB chỉ ba từ mà không xóa/truncate dữ liệu suite khác. Có StatementInspector kiểm SQL lọc kind/level/topic và FETCH FIRST/LIMIT, không điều kiện JSONB. Không cần Redis/Mailpit cho slice này và không chạm Neon.
- Toàn bộ main/test biên dịch thành công bằng javac Java 17 với thư viện sẵn có. JUnit Platform chạy độc lập 112 test xanh, không failure/skip: 26 factory mới và 86 hồi quy practice. Maven chạy riêng hai lớp factory: unit 26/0/0/0; integration chưa chạy được vì Container khởi tạo thất bại, Surefire ghi một lỗi cấp lớp; tổng 27/0/1/0. Không coi tám ca integration là đã pass hoặc bị skip. Log target/t07-factory.log.
- Lần mvnw verify mới: 660 test, 368 pass, 0 failure, 292 error, 0 skip; lỗi bắt nguồn quyền truy cập Docker/Testcontainers. XML xác nhận factory unit 26/0/0/0 và ModuleBoundaryTest 8/0/0/0. Log target/t07-verify.log. Chưa có JaCoCo mới đạt gate; không dùng coverage T-06 làm kết quả T-07. Đã đề nghị leader chạy factory tests rồi Java 17 mvnw verify trên PowerShell thường có Docker, gửi Surefire/JaCoCo.
- Self-check Constitution: AC-01 không đổi HTTP contract/endpoint; AC-03 chỉ đọc dữ liệu learning và trả record nội bộ/DTO, không entity JSON; HR-06/BUS-09 correctAnswer do server tạo, không lộ trước chấm/finish, test cũ giữ nguyên; HR-07 query tham số và giới hạn trang, validate tham số nội bộ; HR-09 không thêm kiểu lỗi API; HR-02/BUS-06/HR-08 không đọc secret hoặc thêm PII/tài liệu bản quyền, WARN chỉ lexemeId; AC-05 không sửa schema/migration hoặc chạy Neon; AC-07 không đổi kiểu điểm; AC-09 không query điều kiện JSONB; ES-01/02 Java 17, RandomGenerator JDK và utility có sẵn, không dependency mới; ES-05 gate còn thiếu Docker nên chưa hoàn tất; ES-06/07 chưa commit/push T-07, giữ nhánh và Draft PR #19, không merge/rebase main hoặc force-push. Giữ nguyên thay đổi ngoài task và file env; không BOM/TODO, git diff --check sạch.

**Gate T-07 đạt (2026-10-30, 13:36 +07):** Leader chạy Java 17 mvnw verify trên PowerShell thường có Docker: BUILD SUCCESS, 674 test, 0 failure/error/skip. Đã đối chiếu Surefire XML mới: factory 26 unit + 8 integration đều xanh; ModuleBoundaryTest 8/0/0/0. JaCoCo XML cập nhật lúc 13:36:25: line coverage toàn bộ 94,85% (2926/3085), practice 100% (742/742), vượt ngưỡng 80%/90%. Các lỗi Docker trong phần lịch sử phía trên đã được giải quyết bằng gate này, không dùng số liệu cũ thay cho kết quả hiện tại. Đánh dấu T-07 hoàn tất kiểm thử; bàn giao chỉ hoàn tất sau commit/push và cập nhật Draft PR #19.

**Self-check khi nghiệm thu T-07:** AC-01 không đổi endpoint hoặc HTTP contract; AC-03 chỉ dữ liệu learning, record nội bộ và DTO không entity; HR-06/BUS-09 đáp án do server tạo, chỉ lưu snapshot, test chống lộ đáp án hiện có vẫn xanh; HR-07 truy vấn tham số hóa, lọc tầng ở DB và LIMIT tối đa 64; HR-09 không thêm mã hoặc định dạng lỗi API; HR-02/BUS-06/HR-08 không secret, PII thật hoặc dữ liệu bản quyền, WARN chỉ lexemeId; AC-05 không sửa schema/migration, không chạy Neon; AC-07 không đổi kiểu điểm; AC-09 không điều kiện truy vấn JSONB; ES-01/02 giữ Java 17 và dependency hiện có, dùng RandomGenerator/PinyinUtils/TextNormalizer; ES-05 đủ gate 674/0/0/0 và coverage 94,85%/100%; ES-06 một commit Conventional Commits có T-07; ES-07 push nhánh được duyệt, giữ Draft PR #19, không merge/rebase main hoặc force-push. Đúng năm file đã duyệt; không BOM/TODO; git diff --check sạch; giữ nguyên dòng bàn giao T-04 có sẵn và file env ngoài commit. Không còn câu hỏi nghiệp vụ T-07 mở.

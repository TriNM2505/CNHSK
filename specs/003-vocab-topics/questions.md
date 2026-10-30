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

# Câu hỏi và quyết định — Spec 003

Các căn cứ và đề xuất được giữ lại để truy vết. Quyết định ghi **Sửa 2026-10-30 (leader duyệt)** là nội dung áp dụng cho T-03.

## Q-WEB-RESTORED-FINISH-01 — Dùng chung kết quả khi khôi phục session SUBMITTED

**Trạng thái:** Chốt, 2026-11-01 (leader duyệt).

**Quyết định:** `features/practice/restoredFinishResult` chỉ dựng dữ liệu chung từ `SessionView | null` đã SUBMITTED và không tự thêm `extra`. Nguồn cần metadata riêng tự bổ sung tại nơi dùng; Topic Practice giữ `extra: {}` để không đổi FR-045.

Với Grammar, trước khi gọi helper phải tạo session nông có `answered` chỉ gồm các số câu `GRAMMAR_CHOICE`. Vì vậy `LESSON_READ` không thể ảnh hưởng `score`, `maxScore`, `percent` hoặc `items` trả về. Hàm helper không được sửa session đầu vào. Khi `maxScore = 0`, `percent` là `null`; câu skipped vẫn có trong `items` nhưng không được tính điểm.

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

**Cách làm đã duyệt (đồng bộ các Chốt 2026-10-31):**

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

## Q-T08 — Shadow Plan và phạm vi file T-08

**Task:** T-08. **Trạng thái:** Chốt, 2026-10-30 (leader duyệt Shadow Plan).

**Phạm vi được duyệt:** Đúng sáu file: `src/main/java/com/cnhsk/learning/api/PracticeApi.java`, `src/test/java/com/cnhsk/architecture/ModuleBoundaryTest.java`, `src/test/java/com/cnhsk/learning/api/PracticeApiTest.java`, `src/test/java/com/cnhsk/learning/api/PracticeApiIntegrationTest.java`, `specs/003-vocab-topics/tasks.md`, `specs/003-vocab-topics/questions.md`. Nếu PracticeApi.java dài quá khoảng 300 dòng, được tách DTO sang file trong cùng package learning.api.

**Thiết kế và điều kiện:** Facade chỉ có bốn phương thức start, answer, finish, view theo §0.11; DTO/enum công khai trong learning.api, không lộ kiểu learning.practice/entity/repository ở hợp đồng gọi giữa module. Mọi phương thức nhận userId người gọi (start nhận trong StartPracticeCommand theo §0.11), chuyển tiếp đúng kiểm tra ownership hiện có; không có overload/cờ system. Facade không có @Transactional riêng, không tự chấm điểm hoặc phát sự kiện. Không đưa PracticeSourceHandler ra api. ModuleBoundaryTest chỉ thêm/siết rule community chỉ phụ thuộc learning.api; không xóa/nới rule hoặc sửa assertion kiến trúc cũ.

**Căn cứ và test:** FR-012, Constitution AC-03; tái sử dụng AT-01/02/12/14/17 qua API. Test FR012 kiểm hợp đồng kiểu công khai, ánh xạ, chống lộ đáp án, ownership cùng 403 ATTEMPT_NOT_OWNED cho missing/unowned, nháp DEFERRED, điểm server và sự kiện/rollback. DTO đầu vào snapshot là dữ liệu server, tách khỏi DTO kết quả an toàn cho client.

**Git và môi trường:** origin/main cục bộ f8e173d có spec 001 và spec 002 T-01…T-03 hoàn tất, Q5 đã duyệt; HEAD a24ea7a có T-01…T-07 và bằng ref origin/spec/003-vocab-topics cục bộ. Fetch vẫn lỗi mạng. Quyết định cho phép làm local khi fetch lỗi nằm ở lời duyệt gộp T-05, Q-T05-01; không phải Q-T03-03. Giữ spec/003-vocab-topics và Draft PR #19, không merge/rebase main hoặc force-push. Thay đổi bàn giao T-04 còn dở trong questions.md và file env được giữ ngoài commit T-08; questions.md chỉ stage hunk T-08 bằng git add -p.

**Gate và bàn giao:** Java 17, test PracticeApiTest/PracticeApiIntegrationTest/ModuleBoundaryTest trước, rồi mvnw.cmd verify trên PowerShell thường có Docker: 0 failure/error/skip, coverage toàn bộ >=80%, practice >=90%; đối chiếu coverage facade mới, BOM/TODO và git diff --check. Một commit feat(practice): expose module practice API (T-08), push nhánh đã chốt, cập nhật Draft PR #19. Chỉ sau bàn giao T-08 mới lập Shadow Plan T-13; sau T-13 chạy gate toàn bộ rồi chuyển PR #19 Ready, báo leader; không merge.

**Kiểm chứng triển khai T-08 (2026-10-30, chưa hoàn tất):**

- Facade 193 dòng, đúng bốn phương thức; constructor không public, mọi kiểu trong chữ ký phương thức/DTO công khai chỉ thuộc learning.api hoặc JDK. StartPracticeCommand giữ userId theo §0.11; answer/finish/view nhận userId trực tiếp. Không có đường system, transaction riêng, chấm điểm hoặc phát sự kiện trong facade. Snapshot đầu vào và ServedItemView đầu ra là hai record khác nhau; JSON giữ quy tắc DEFERRED và câu trả lời của người học.
- ModuleBoundaryTest chỉ thêm import và một rule FR012 bao phủ mọi package learning ngoài learning.api; tám rule cũ nguyên vẹn. Test âm biên dịch fixture community tạm thời để kiểm API được phép và practice/service/enum/entity/repository/content/package tương lai bị chặn; không tạo fixture production hoặc nới rule cũ.
- Toàn bộ main/test biên dịch bằng javac Java 17 với dependency có sẵn trong workspace. Lần JUnit Platform độc lập mới nhất: 23 test API + 9 test kiến trúc = 32/0 failure/0 error/0 skip, log target/t08-unit.log. Lần chạy độc lập đầu gặp quyền thư mục Temp và đường dẫn output không theo quy ước ArchUnit; đã sửa đường dẫn chạy trong target và giữ nguyên mọi rule/assertion, lần chạy lại xanh.
- Maven mặc định bị chặn khi truy cập dependency; offline với cache cục bộ bị chặn đọc metadata antlr ở bước incremental compile. Lần verify offline tắt incremental compile đã chạy Surefire: 697 test, 0 failure, 306 error, 0 skip; 305 error do Docker/Testcontainers không truy cập được named pipe, một error do JUnit không dọn được Temp trong sandbox. Riêng API integration 13 ca đều lỗi hạ tầng; không coi là pass/skip. Log target/t08-verify.log. Gate PowerShell thường có Docker vẫn cần chạy lại, không dùng coverage JaCoCo T-07 đang có làm kết quả T-08.
- Đã đề nghị leader chạy test API + ModuleBoundaryTest rồi Java 17 mvnw verify trên PowerShell thường có Docker. Chưa đánh dấu task hoàn tất, commit/push hoặc chuyển T-13. Thay đổi bàn giao T-04 chưa commit có sẵn trong questions.md là phần ngoài T-08, đã báo leader và giữ lại; chỉ stage hunk Q-T08.

**Self-check Constitution khi chờ gate:** AC-01 không đổi endpoint/OpenAPI; AC-03 API/DTO tách kiểu nội bộ, rule chỉ siết và tám rule cũ xanh; BUS-01 userId chuyển tiếp và test missing/unowned cùng 403, integration còn chờ Docker; BUS-02/AC-10 giữ transaction/hook/event của bộ máy, facade không thêm transaction hoặc event; HR-06/BUS-09 điểm do PracticeService tính; HR-05 giữ CSRF/security hiện có; HR-07 không thêm SQL/input path trong facade; HR-09 chuyển tiếp ApiException không đổi; HR-02/BUS-06/HR-08 không đọc secret hoặc thêm PII/dữ liệu bản quyền; AC-05 không đổi schema/migration hoặc chạy Neon; AC-07 giữ Long/BIGINT; AC-08 giữ Instant/Duration và Clock của bộ máy; AC-09 không thêm truy vấn JSONB; ES-01/02 Java 17 và dependency hiện có; ES-05 chưa đủ gate/coverage nên T-08 vẫn đang làm; ES-06/07 chưa commit/push, giữ nhánh/PR được duyệt, không merge/rebase/force-push. Đúng sáu file; không BOM/TODO trong code mới; git diff --check sạch.

**Gate T-08 đạt (2026-10-30, 15:10:59 +07):** Leader chạy Java 17 mvnw verify trên PowerShell thường có Docker: BUILD SUCCESS, 711 test, 0 failure/error/skip. Đã đọc XML Surefire trên máy: đúng 711/0/0/0; PracticeApiTest 23/0/0/0, PracticeApiIntegrationTest 13/0/0/0, ModuleBoundaryTest 9/0/0/0. JaCoCo XML tạo lúc 15:10:59: toàn bộ 95,00% (3020/3179 dòng), practice 100% (742/742), learning.api 100% (94/94). Gate này thay thế kết quả bị hạn chế hạ tầng ở trên. Code không đổi sau gate; chỉ cập nhật tài liệu nghiệm thu. git diff --check sạch. Một commit T-08; bàn giao chỉ hoàn tất sau push và cập nhật Draft PR #19, rồi lập Shadow Plan T-13.

**Self-check khi nghiệm thu T-08:** AC-01 không đổi HTTP endpoint/contract; AC-03 facade/DTO trong learning.api, không lộ kiểu nội bộ, rule community chỉ siết và tám rule cũ nguyên vẹn; BUS-01 mọi lời gọi giữ userId, missing/unowned cùng 403 ATTEMPT_NOT_OWNED, không đường system; BUS-02/AC-10 facade không có transaction riêng, giữ hook/event và rollback của bộ máy, integration đã xanh; HR-06/BUS-09 PracticeService tính điểm, facade không chấm/phát sự kiện, không lộ đáp án trước khi được phép; HR-05 giữ CSRF/security hiện có; HR-07 không thêm SQL hoặc endpoint nhận input; HR-09 chuyển tiếp lỗi hiện có; HR-02/BUS-06/HR-08 không secret/PII thật/dữ liệu bản quyền; AC-05 không đổi schema/migration, không chạy Neon; AC-07 Long/BIGINT; AC-08 giữ Instant/Duration/Clock; AC-09 không thêm query JSONB; ES-01/02 giữ Java 17/stack/dependency; ES-05 toàn bộ test không skip, coverage vượt 80%/90%, facade 100%; ES-06 một commit Conventional Commits T-08; ES-07 giữ spec/003-vocab-topics và Draft PR #19, không merge/rebase/force-push. Đúng sáu file, không BOM/TODO, diff sạch; chỉ stage hunk Q-T08, giữ nguyên phần bàn giao T-04 và env ngoài commit.


## Q-T13-02 — Phạm vi khôi phục lượt luyện

**Trạng thái:** Chốt bởi leader, ngày 2026-10-30. T-13 thực hiện theo Shadow Plan đã duyệt trong phiên làm việc.

FR-045 chỉ khôi phục các trường ItemState hiện có: no, answer, correct, correctAnswer, counted. Không thêm trường vào GET session; không sửa backend/OpenAPI. Tải lại trang thì bộ đếm nghe của câu đang làm về 0. IMMEDIATE/RECORD đã khóa sau khi gửi; DEFERRED không hiện đúng/sai trước finish. replayCount tính cả lần đầu, tối đa ba lần; không autoplay. Tổng kết chỉ dùng điểm server.

## Q-T13-03 — Kiểu draft khôi phục và bảo toàn bằng chứng server

**Trạng thái:** Chốt bởi leader, ngày 2026-10-30.

Chỉ đổi kiểu giá trị drafts trong usePracticeSession.ts thành `Pick<AnswerRequest, 'no'> & Partial<AnswerRequest>`. Giữ nguyên tên export, AnswerRequest, FinishRequest và chữ ký answer()/finish(). Không ép kiểu hoặc non-null assertion. restoredState chỉ lấy no và answer có trong GET, không tự điền durationMs/replayCount hoặc field vắng mặt.

Khôi phục không gọi answer() hoặc tự gửi nháp lên server. Khi finish nhận toàn bộ nháp từ màn hiện có, hook lọc các câu khôi phục chưa sửa; nếu không còn câu mới thì gọi API finish không có answers. Câu sửa trong phiên có đủ durationMs được giữ; câu chưa kịp autosave nhưng đổi đáp án hoặc chủ động báo skip/audioFailed cũng được gửi. Không cần sửa caller ngoài features/practice.

**Phạm vi thực hiện:** usePracticeSession.ts và test; ChoiceQuestion.tsx và test; AudioButton.test.tsx, SessionSummary.test.tsx; tasks.md và chỉ mục T-13 trong questions.md. Giữ phần bàn giao T-04 có sẵn ngoài commit. Tên export/props và các API request khác giữ nguyên. Handler thế hệ lượt chặn phản hồi mutation cũ khi đổi source/refId/reload; ChoiceQuestion ẩn feedback DEFERRED. AudioButton/SessionSummary đã đủ, chỉ bổ sung test biên và test điểm server.

**Gate được duyệt:** toàn FE `npx vitest run --maxWorkers=2 --minWorkers=1`, lint, typecheck, build, git diff --check; không chạy Maven vì không sửa BE. Trước push kiểm `git merge-base --is-ancestor 7391f87 HEAD`; không merge/rebase/pull main. Một commit T-13, stage file cụ thể, push spec/003-vocab-topics, Draft PR mới; sau đó gửi Shadow Plan T-09 và chờ duyệt.


**Nghiệm thu T-13 (2026-10-30):** Gate cuối `npx vitest run --maxWorkers=2 --minWorkers=1`: 114 file/1021 test pass, 0 failure/error/skip; 16 test mới FR043/FR045, package practice tổng 42 ca. Lint 0 lỗi, giữ 1 warning có sẵn tại shared/ui/LevelBadge.tsx; typecheck/build đều exit 0. Đã giữ cách gọi finishSession(id, undefined) hiện có để test grammar/hanzi nguyên vẹn; undefined không tạo body answers. Không sửa file ngoài scope hoặc assertion cũ. git diff --check sạch; file thay đổi UTF-8 không BOM, code không TODO, không dùng ép kiểu/non-null assertion. Maven không chạy và không có coverage BE mới theo gate leader.

**Self-check Constitution:** AC-01 giữ HTTP contract/OpenAPI, chỉ đổi kiểu draft UI đã được Q-T13-03 duyệt; AC-03 giữ API/caller, không truy cập module hay DB; HR-06/BUS-09 tổng kết lấy score/maxScore từ server, DEFERRED ẩn phản hồi trước finish; HR-05/HR-09 giữ shared client CSRF/lỗi, không đổi auth; HR-07 input/request theo contract, nháp khôi phục không tự tạo bằng chứng hoặc gửi lại lúc finish; HR-02/BUS-06/HR-08 không đọc/đưa secret, PII thật hoặc dữ liệu bản quyền vào thay đổi; AC-05 không SQL/migration/Neon; ES-01/02 giữ stack/dependency; ES-05 toàn FE xanh và test mới lấy FR-043/045, phần AT-40 liên quan; ES-06/07 một commit T-13, push nhánh đã duyệt và Draft PR mới, không merge/rebase/pull main, force-push hoặc push main. Giữ phần bàn giao T-04 và file chưa theo dõi của người dùng ngoài commit. Không còn câu hỏi T-13 mở; bộ đếm nghe sau tải lại về 0 theo Q-T13-02.


## Q-T09-01 — Shadow Plan TopicQueryService và hai GET chủ đề

**Task:** T-09. **Trạng thái:** Chốt bởi leader ngày 2026-10-30, duyệt có điều kiện. Lập sau commit T-13 `10bf41e63bcf3248c6f46ac297d29982d094265c` đã push. T-09 ưu tiên demo học thật.

**Điều kiện duyệt thay thế phần đề xuất bên dưới:** Giữ T-09 ở local trong khi leader chạy gate PR #21. Không push commit T-09 vào #21; chỉ sau khi leader báo #21 đã merge mới push cùng nhánh và mở Draft PR mới, không pull main. Không merge/rebase/pull main, force-push hoặc push main. File thực hiện đúng ba file production, hai test và các file shared/docs đã liệt kê; không sửa spec.md. OpenAPI chỉ thêm 422 cho GET /api/topics/{id}. SecurityConfig đúng hai matcher GET cho LEARNER. TOPIC_NOT_FOUND ngay trước INTERNAL_ERROR, đồng bộ containsExactly đúng thứ tự, không sửa test spec khác.

Actions đã chốt: canStudy khi learnedWords<totalWords; recognition khi learnedWords>=1; listening khi có từ đã học của chủ đề có audio_url. TOPIC_TEST IN_PROGRESS chưa hết hạn ưu tiên canTakeTest=true, reason/retryAvailableAt=null. Nếu không: <4 từ → TOO_FEW_WORDS; học<80% → NOT_ENOUGH_LEARNED; lượt TOPIC_TEST gần nhất SUBMITTED, không đạt và chưa đủ 10 phút → RETRY_TOO_SOON, retryAvailableAt=submitted_at+10 phút; còn lại được thi. Dùng số nguyên và Clock; đúng 80%/10 phút được mở. FR-015 tổng hợp một query toàn danh sách theo lexeme của userId từ AuthApi.currentUser(), không giới hạn chủ đề/trạng thái lượt, không N+1/cache. bestTestPercent bỏ max_score=0, chia nguyên; không lọc JSONB. Không migration/index mới; cần index thì dừng xin V5. learning.vocab được đọc content trong cùng module learning. id sai định dạng/<=0 và hskLevel sai/ngoài 1–9 trả 422; id hợp lệ nhưng thiếu/rỗng trả 404 TOPIC_NOT_FOUND. Gate Testcontainers + Java 17 mvnw.cmd verify, 0 failure/error/skip, line coverage toàn bộ>=80%, practice>=90%, diff sạch.

**Căn cứ:** FR-013…FR-018, AT-22…AT-24; spec §4 (tính đã học dùng chung giữa chủ đề), §5/§13 (lỗi/API), tham chiếu FR-031…FR-033 khi tính actions. Điều kiện bắt đầu đã có trên origin/main 7391f87: spec 001, spec 002 T-01…T-03, Q5 và T-01…T-08 của 003. Nội dung demo HSK 1–3; API/DB tiếp tục nhận 1–9, không sửa FE. Không có plan.md trong spec 003.

**File đọc:** AGENTS.md → constitution.md → spec 001 §0 → spec 003 spec.md/contracts/openapi-practice-vocab.yaml/tasks.md/questions.md. Code cần thiết: auth/api/AuthApi.java và CurrentUser.java; learning/content/entity/{Topic,TopicItem,Lexeme}.java và repository tương ứng; learning/practice/entity/{Attempt,AttemptItem}.java và repository; shared/config/SecurityConfig.java; shared/exception/{ApiException,ErrorCode,GlobalExceptionHandler}.java; support/AbstractIntegrationTest.java; architecture/ModuleBoundaryTest.java; contract/AuthContractTest.java; shared/exception/GlobalExceptionHandlerTest.java; migration V1/V3/V4 chỉ đọc để khớp cột/index; pom.xml và cấu hình test chỉ đọc.

**File dự kiến tạo trong cột File hiện có:**

- `src/main/java/com/cnhsk/learning/vocab/TopicQueryService.java` — DTO record và tính actions/status/percent.
- `src/main/java/com/cnhsk/learning/vocab/TopicQueryRepository.java` — truy vấn tổng hợp có tham số trong schema learning.
- `src/main/java/com/cnhsk/learning/vocab/web/TopicController.java` — đúng GET /api/topics và GET /api/topics/{id}, lấy userId qua AuthApi.

**Phạm vi bổ sung đã được leader duyệt có điều kiện:**

- `src/test/java/com/cnhsk/learning/vocab/TopicQueryIntegrationTest.java` — Testcontainers PostgreSQL + HTTP cho hai GET, không sửa test nền.
- `src/test/java/com/cnhsk/learning/vocab/TopicContractTest.java` — so đúng hai GET với contract và response DTO.
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java` — chỉ thêm matcher GET /api/topics và /api/topics/{id}, hasRole LEARNER.
- `src/main/java/com/cnhsk/shared/exception/ErrorCode.java` — chỉ thêm TOPIC_NOT_FOUND.
- `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java` — chỉ thêm TOPIC_NOT_FOUND vào containsExactly, giữ các mã/assertion cũ.
- `specs/003-vocab-topics/{tasks.md,questions.md}` — chỉ T-09; `contracts/openapi-practice-vocab.yaml` chỉ thêm 422 cho GET chi tiết. Đã loại đề xuất sửa `spec.md` theo giới hạn leader chốt; contract được chốt trước endpoint.

**Cách làm:** Danh sách dùng một truy vấn tổng hợp cho mọi chủ đề: từ đã học DISTINCT theo lexeme của người gọi trên mọi TOPIC_STUDY; tổng từ, dấu có lượt, điểm kiểm tra cao nhất được tổng hợp riêng để không nhân dòng khi JOIN. Lọc topic có lexeme, hskLevel 1–9, sắp hsk_level/order_index/id. COMPLETED theo lượt TOPIC_TEST đạt 90%, giữ bestTestPercent khi lượt sau điểm thấp. Chi tiết trả header cùng quy tắc + words theo order_index, learned dùng cùng định nghĩa toàn cục; missing/empty topic trả 404 TOPIC_NOT_FOUND. Read-only transaction, Clock cho cooldown, DTO không entity, SQL/JPQL có tham số, không cache/N+1, không truy vấn module khác. Không triển khai POST/handler học-luyện-kiểm tra hoặc FE, không thêm dependency/schema/migration.

**Test mới:** Tên bắt đầu FR013_…FR018_. AT-22: HSK 10 trả 422, 1/9 hợp lệ, topic rỗng không xuất hiện, thứ tự đúng. AT-23: học từ X ở A thì B tính đã học; tài khoản khác không được hưởng; bằng chứng học không bị mất khi lượt hết hạn/bỏ dở. AT-24: thi 95 rồi 70 vẫn COMPLETED/best 95. FR-014: phần trăm nguyên/count không nhân đôi theo số lần học. FR-018: trường/word/order/audio/null, 404 missing/empty, các biên 4 từ/80%/10 phút theo quyết định actions; chỉ LEARNER, 401/403 đúng JSON/request_id và không có entity/PII. Contract test đúng path/status/schema của hai GET. Kiểm số truy vấn không tăng theo số topic; đo tham khảo danh sách 200 topic và 5000 câu trả lời theo NFR-P01, không coi thời gian cục bộ là bảo đảm p95 production.

**Lệnh sau duyệt:** Test TopicQueryIntegrationTest/TopicContractTest/GlobalExceptionHandlerTest/ModuleBoundaryTest, rồi Java 17 `.\mvnw.cmd verify` trên PowerShell có Docker; đọc Surefire 0 failure/error/skip và JaCoCo line toàn bộ >=80%, practice >=90%; git diff --check, kiểm BOM/TODO và đúng phạm vi. Không chạy spring-boot:run lên Neon, SQL hoặc migration Neon. Demo thủ công dùng docs/demo-data nhập qua SCR-068; fixture test chỉ ở Testcontainers. Không merge/rebase/pull main hoặc force-push; một commit T-09 và Draft PR theo nhánh đã duyệt.

**Rủi ro cần kiểm thử theo quyết định đã chốt:** JOIN nhân count, đếm học bị giới hạn nhầm theo topic, lẫn tiến độ người khác, mất COMPLETED sau lượt trượt, chia cho max_score 0, lệch đúng biên cooldown hoặc thứ tự ưu tiên actions. Không có nhu cầu migration/V5 cho T-09.

**Đề xuất ban đầu — lưu lịch sử; đã chốt tại đầu mục:**

1. Duyệt phạm vi bổ sung nêu trên vào cột File, gồm hai file dùng chung chỉ thêm dòng và test enum chỉ thêm một mã.
2. Duyệt bảng actions đề xuất: canStudy = còn từ chưa học; canPracticeRecognition = có từ đã học; canPracticeListening = có từ đã học có audio; requiredWords = ceil(0.8*totalWords). Không kiểm đủ nhiễu ở T-09; việc sinh câu/lỗi NOT_ENOUGH_WORDS thuộc T-11. Nếu có TOPIC_TEST IN_PROGRESS chưa hết hạn thì canTakeTest=true để tiếp tục theo FR-033; nếu không, cần totalWords>=4, học>=80% và hết cooldown theo FR-032. testBlockedReason ưu tiên TOO_FEW_WORDS → NOT_ENOUGH_LEARNED → RETRY_TOO_SOON → null. retryAvailableAt chỉ có khi đang bị cooldown, là submittedAt của lượt trượt thuộc FR-032 +10 phút; ngoài ra null.
3. Duyệt 422 VALIDATION_FAILED cho hskLevel/id sai kiểu hoặc ngoài kiểu số hỗ trợ; hskLevel ngoài 1–9 theo FR-013. OpenAPI GET chi tiết hiện chưa khai 422 nên cần bổ sung trước code, giữ 404 TOPIC_NOT_FOUND cho id số không tồn tại/không có từ.

**Kết quả thực hiện local T-09 — 2026-10-30:**

- Tạo đúng ba file production và hai file test đã liệt kê; thay đổi shared chỉ hai matcher GET, TOPIC_NOT_FOUND và thứ tự enum trong containsExactly. OpenAPI chỉ thêm 422 GET chi tiết. Không sửa spec.md, test spec khác, dependency, FE hay schema.
- Một truy vấn tổng hợp danh sách theo userId từ AuthApi; learned theo lexeme không phụ thuộc chủ đề/trạng thái lượt; không N+1/cache hoặc điều kiện JSONB. Chi tiết dùng cùng tổng hợp và một query words, DTO không trả entity/PII. Actions đúng bảng chốt; kiểm thời điểm hết hạn bằng Instant/Clock để không làm mất biên nano khi bind JDBC.
- Gate PowerShell có Docker: JAVA_HOME Java 17; bỏ biến DEBUG chỉ trong tiến trình test rồi chạy `.\mvnw.cmd verify`, BUILD SUCCESS lúc 22:03:47 +07. Đối chiếu Surefire XML: **1370 test, 0 failure, 0 error, 0 skip**. T-09 mới: **33 integration + 2 contract = 35**; AT-22/23/24, mọi nhánh actions, đúng 80%/10 phút, cách ly người dùng, query count đều xanh. Chín test kiến trúc xanh.
- JaCoCo XML: toàn bộ **96,47% (4422/4584 dòng)**; practice **100% (742/742)**; vocab **97,65% (83/85)**. Đạt gate toàn bộ>=80%, practice>=90%.
- Lần verify đầu có bốn failure kiểm tra log auth/import do biến môi trường DEBUG bật log Spring; không sửa các test đó hoặc cấu hình repo. Cả bốn đã xanh trong lần verify đầy đủ nêu trên.
- Kiểm một query cho danh sách nhỏ và >=200 chủ đề/5000 attempt_items; 20 lần gọi tạo đúng 20 query. p95 cục bộ phần service/query trong lần verify đầy đủ **258 ms**; đây là số đo tham khảo, chưa phải phép đo HTTP end-to-end/production của NFR-P01.
- `git diff --check` sạch; 11 file trong phạm vi đều UTF-8 không BOM; phần code/test mới không còn TODO/FIXME. Nhánh chứa 7391f87. Không có câu hỏi nghiệp vụ còn mở; giữ commit T-09 local, chưa push hoặc mở PR trong khi chờ leader báo #21 đã merge.

**Self-check Constitution T-09:**

- Nguyên tắc I, HR-02/HR-05/HR-08, BUS-01/BUS-06: hai GET chỉ LEARNER, userId lấy từ AuthApi.currentUser(), mọi bằng chứng lọc theo userId; test hai người dùng không chia sẻ tiến độ. Không trả entity/PII, lỗi theo envelope chung; không thay CSRF, không commit secret hoặc dữ liệu HSK có bản quyền.
- Nguyên tắc II/AC-01: áp dụng contract và 422 đã được leader duyệt trước endpoint; test runtime so hai GET với OpenAPI. Phần spec.md cũ không sửa theo giới hạn phê duyệt.
- Nguyên tắc III/AC-03: chỉ gọi auth.api.AuthApi qua ranh giới module; repository chỉ đọc bảng learning, content/practice cùng module. Chín test kiến trúc giữ nguyên và xanh.
- Nguyên tắc IV/AC-07/AC-08, BUS-05: điểm lấy từ bằng chứng server; floor và so ngưỡng 80%/90% dùng số nguyên; cooldown dùng Clock/Instant. BUS-02: service read-only transaction, task không ghi nghiệp vụ.
- Nguyên tắc V/HR-06/BUS-09: không nhận điểm hoặc userId từ request để tính tiến độ; không đổi cơ chế chấm server. AC-09: không dùng điều kiện JSONB.
- AC-05: không sửa/tạo migration, index, ddl-auto hoặc chạy SQL/migration Neon; dữ liệu fixture và migration kiểm thử chỉ chạy trong Testcontainers.
- ES-01/ES-05: Java 17, Maven Wrapper, không thêm thư viện; test FR-prefix theo AT và full gate/coverage đạt như trên.
- ES-06/ES-07: một commit T-09 Conventional Commits, stage từng file và chỉ hunk T-09 của questions.md; giữ nhánh spec/003-vocab-topics, chờ thông báo merge #21 mới push/mở Draft PR mới. Không merge/rebase/pull main, force-push, push main hoặc tự merge PR.

## Q-T10-01 — Shadow Plan học từ mới, đã duyệt

**Task/căn cứ:** T-10, FR-019…FR-022, AT-25…AT-28; dùng FR-015/E4/E5 và bộ máy §0. Điều kiện bắt đầu đã đủ trên origin/main aa0c5d1 (PR #23 đã merge). Nhánh mới spec/003-vocab-topics-part2 tạo từ origin/main sau fetch; không merge/rebase/pull main. Fix COMPLETED max_score=0 được leader duyệt riêng, chỉ TopicQueryRepository và test hồi quy; không phải triển khai T-10.

**Trạng thái:** DUYỆT ngày 2026-10-30; Q-T10-02…04 bên dưới là quyết định cuối của leader, thay thế các đề xuất ban đầu. T-10 làm trên spec/003-vocab-topics-part2, commit riêng với fix đã có.

**File đọc:** AGENTS.md (§12), constitution.md và constraints/safety.md; spec 001 §0; spec 003 spec.md → contracts/openapi-practice-vocab.yaml → tasks.md/questions.md (không có plan.md). Code cần thiết: learning/vocab/{TopicQueryRepository,TopicQueryService,web/TopicController}.java; learning/practice/{PracticeService,PracticeSourceHandler,PracticeSource,ServedItem,ServedItemView,PracticeRateLimiter}.java và entity/{Attempt,AttemptItem,AttemptRepository,AttemptItemRepository}.java; learning/content/entity/{Topic,TopicItem,Lexeme,LexemeExtra}.java và repository hiện có; auth/api/AuthApi.java; shared/config/SecurityConfig.java, shared/exception/{ApiException,ErrorCode}.java; test support/AbstractIntegrationTest.java, learning/vocab/TopicContractTest.java, các test start/answer/finish hiện có; V1/V3/V4 và pom.xml chỉ đọc để khớp schema/gate.

**Danh sách file xin bổ sung vào cột File T-10, chỉ sửa sau duyệt (10 file):**

1. `src/main/java/com/cnhsk/learning/vocab/TopicStudyService.java` (tạo) — transaction điều phối tìm lượt đang làm/chọn từ/tạo lượt, kết quả nội bộ phân biệt 201/200.
2. `src/main/java/com/cnhsk/learning/vocab/TopicStudyRepository.java` (tạo) — đọc content trong learning; kiểm topic/có từ, query tối đa 10 lexeme chưa học, khóa topic theo quyết định leader bên dưới.
3. `src/main/java/com/cnhsk/learning/vocab/TopicStudyHandler.java` (tạo) — implement PracticeSourceHandler cho TOPIC_STUDY, validateAnswer/enrichMeta của FR-021, onFinish không thêm dữ liệu ngoài contract. Tách khỏi service để không tạo vòng dependency với PracticeService.
4. `src/main/java/com/cnhsk/learning/vocab/web/TopicController.java` — chỉ thêm POST /api/topics/{id}/study-sessions, lấy userId qua AuthApi; giữ hai GET.
5. `src/main/java/com/cnhsk/shared/config/SecurityConfig.java` (**dùng chung**) — chỉ THÊM đúng một matcher POST /api/topics/{id}/study-sessions cho LEARNER; không đổi CSRF/JWT/CORS.
6. `src/test/java/com/cnhsk/learning/vocab/TopicStudyIntegrationTest.java` (tạo) — PostgreSQL Testcontainers + HTTP/start/answer/finish/đồng thời; fixture chỉ trong container.
7. `src/test/java/com/cnhsk/learning/vocab/TopicContractTest.java` — giữ kiểm tra GET, bổ sung POST mới và các responses/schema đã được duyệt; không sửa test spec khác.
8. `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml` — đề xuất chỉ thêm 403/422 vào POST study-sessions trước khi code, theo Q-T10-02; không đổi schema SessionView/AnswerRequest.
9. `specs/003-vocab-topics/tasks.md` — cập nhật cột File sau duyệt, chỉ đánh dấu T-10 khi đủ gate.
10. `specs/003-vocab-topics/questions.md` — quyết định và báo cáo T-10, giữ hunk T-04 của người dùng ngoài commit.

Không đề xuất sửa PracticeService, PracticeSourceHandler, entity, repository content hiện có, ErrorCode, application config, FE hay migration. Nếu phải mở rộng tiếp thì dừng báo leader. Không cần migration/index mới; nếu phát sinh thì xin số V5, không chạy SQL/migration Neon.

**Cách làm đề xuất:**

- Dùng transaction ở TopicStudyService; userId chỉ từ AuthApi.currentUser(). Kiểm topic hợp lệ/có lexeme; tiếp tục TOPIC_STUDY IN_PROGRESS chưa hết hạn của đúng user/topic trước khi xét từ mới (FR-022). Trả nguyên SessionView/answered từ bộ máy; không đánh dấu học hay đếm lượt mới lần nữa.
- Nếu không có lượt đang làm: query NOT EXISTS bằng chứng STUDY_CARD/TOPIC_STUDY của user theo lexeme trên mọi chủ đề và mọi trạng thái (FR-015/E4/E5), ORDER BY topic_items.order_index, id; giới hạn 10 ở DB, không lọc JSONB/N+1/cache. Hết từ chưa học trả đúng 200 {session:null, reason:NO_NEW_WORDS}; topic thiếu/rỗng trả 404 TOPIC_NOT_FOUND, không coi là học hết.
- Tạo ServedItem STUDY_CARD/VOCAB, no liên tiếp, prompt chỉ text/pinyin/sinoViet/đủ meaning_vi/examples/audioUrl/kind; không phát extra.exercises/answerKey hoặc entity. Gọi PracticeService.start với TOPIC_STUDY, refId topic, không preRecorded/ttlOverride; RECORD, TTL 2 giờ và giới hạn lượt mới dùng bộ máy hiện có. Lượt mới trả 201 SessionView; tiếp tục trả 200 SessionView, không bọc thêm session ở hai nhánh này.
- Handler FR-021 kiểm LEARNED/KNOWN và ghi selfDeclared cho KNOWN; handler gắn suspicious khi durationMs<1000 (đúng 1000 không gắn), chỉ ở nhánh trả lời bình thường. Skip/audioFailed và rules áp dụng Q-T10-03 đã chốt. Answer/finish dùng endpoint chung, is_correct/score/maxScore giữ RECORD, sự kiện/study_events qua engine, không tự ghi attempt_items khi start.
- Advisory transaction lock theo cặp user/topic, truyền key bằng tham số theo Q-T10-04 đã chốt. Không khóa hàng topic và không giữ khóa qua thao tác học/answer/finish; hai người dùng không chặn nhau.

**Test sẽ viết (FR019_/FR020_/FR021_/FR022_):**

- AT-25: chủ đề >=14 từ, học 4/10 rồi hết hạn/bỏ dở; lượt mới đúng tối đa 10 từ chưa học, không chứa 4 từ cũ; học ở chủ đề khác cũng loại, user khác vẫn được học, thứ tự ổn định, không giới hạn API HSK về 1–3.
- AT-26: hết từ trả 200 với session=null/reason; phân biệt 404 topic thiếu/rỗng; topic chỉ còn 1…9 từ thì phát đúng số còn lại, start chưa làm tăng learnedWords.
- FR-020: đủ prompt/meaning/examples, null audio/pinyin hợp lệ theo dữ liệu, không lộ answer/accept/answerKey/PII/entity qua start/khôi phục.
- AT-27: LEARNED/KNOWN hợp lệ, giá trị khác 422; KNOWN có selfDeclared; duration 200/999 suspicious và 1000 không suspicious; vẫn tính đã học ngay, RECORD correct=null, finish không có điểm. Skip/audioFailed/replay theo chốt Q-T10-03.
- AT-28: gọi hai lần và hai request đồng thời cùng user/topic chỉ tạo một session, trả 201 rồi 200; biên hết hạn dùng Clock, hết hạn chọn lượt mới; user/topic khác không hưởng lượt của nhau. Resume ưu tiên dù đã học hết các từ trong lượt nhưng chưa finish.
- Chỉ LEARNER; guest 401, role khác 403; cookie POST thiếu CSRF 403, CSRF hợp lệ/Bearer được gọi; id sai kiểu/<=0 trả 422 nếu Q-T10-02 được duyệt; 429 khi tạo quá 120 lượt mới/giờ, Redis lỗi theo engine. Contract test giữ hai GET và so thêm POST/status/body; không N+1 ở bước chọn từ.

**Lệnh sau duyệt:** Java 17, PowerShell có Docker; bỏ DEBUG chỉ trong tiến trình test; `.\mvnw.cmd -Dtest=TopicStudyIntegrationTest,TopicContractTest test` rồi `.\mvnw.cmd verify`; Surefire 0 failure/error/skip, JaCoCo toàn bộ>=80%, practice>=90%. `git diff --check`, kiểm đúng phạm vi/UTF-8 không BOM/TODO, stage file cụ thể. Một commit T-10 sau commit fix riêng theo leader; push nhánh part2, cùng Draft PR T-10, không merge/rebase/pull main/force-push/push main.

**Rủi ro:** khởi tạo hai lượt khi đua; khóa topic có thể chặn tạm người khác; chọn nhầm từ đã học do lọc theo chủ đề/trạng thái; lộ bài tập trong extra; áp dụng sai enum khi skip/audioFailed. Không tự chốt các điểm dưới.

### Q-T10-02 — Responses của POST và phạm vi file

**Chốt, 2026-10-30 — leader duyệt:** Đúng 10 file của Q-T10-01; cập nhật cột File T-10. SecurityConfig chỉ thêm một matcher POST `/api/topics/{id}/study-sessions` cho LEARNER. OpenAPI chỉ thêm 403 và 422 cho POST này, trước code; id sai định dạng hoặc <= 0 trả 422 như T-09. userId luôn lấy từ AuthApi.currentUser(). Không thêm rate limiter; E-R12 do PracticeService.start hiện có thực hiện, chỉ lượt mới tính vào hạn mức. Giữ nguyên bộ máy practice, test spec khác, FE, migration và dependency.

### Q-T10-03 — Skip/audioFailed và số lần nghe STUDY_CARD

**Chốt, 2026-10-30 — leader duyệt:** Giữ E-R10/E-R11, không xử lý riêng STUDY_CARD; đếm theo FR-015. "Không nghe được" ở thẻ học vẫn tính đã học, vì lọc meta JSONB bị cấm (AC-09) và FE đang gửi audioFailed. Handler chỉ kiểm LEARNED/KNOWN khi không skipped và không audioFailed; giá trị khác trả 422 VALIDATION_FAILED. meta.selfDeclared (KNOWN) và meta.suspicious (durationMs < 1000) chỉ ghi cho câu trả lời LEARNED/KNOWN bình thường, không ghi cho hai nhánh ngoại lệ. maxReplays=3, tính cả lần đầu; hasAudio khi audioUrl khác null. Để giữ nguyên engine (vốn gắn suspicious theo minDurationMs cả với skip/audioFailed), rules.minDurationMs=0 và hook nguồn áp dụng ngưỡng 1000 cho câu trả lời hợp lệ.

### Q-T10-04 — Chống tạo trùng và thứ tự kiểm tra

**Chốt, 2026-10-30 — leader duyệt:** Không khóa hàng topic. Trong cùng transaction, kiểm topic tồn tại/có từ trước (không có trả 404 TOPIC_NOT_FOUND kể cả còn lượt cũ; lượt cũ vẫn truy cập qua API session); tiếp đó dùng `SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))`, key `TOPIC_STUDY:{userId}:{topicId}` truyền bằng tham số. Rồi tìm lượt chưa hết hạn của đúng user/topic (200), hoặc chọn tối đa 10 từ chưa học theo order_index, bất kể chủ đề học trước (FR-015); hết từ trả 200 `{session:null,reason:"NO_NEW_WORDS"}`, có từ trả 201. Hai request cùng user/topic chỉ tạo một lượt; hai user khác nhau có khóa riêng và không chặn nhau. Không tạo migration hoặc sửa engine.

**Phê duyệt Q-T10-01:** Shadow Plan đã DUYỆT ngày 2026-10-30, áp dụng quyết định Q-T10-02…04 trên `spec/003-vocab-topics-part2`; phần đề xuất khóa hàng topic phía trên được thay bằng advisory lock. Commit T-10 riêng với commit fix có sẵn; chỉ stage 10 file/hunk T-10, giữ hunk T-04 ngoài commit. Test chọn lọc rồi Java 17 `mvnw verify` với Docker: 0 failure/error/skip, coverage toàn bộ >=80%, practice >=90%; diff/BOM/TODO sạch; push và Draft PR, không merge/rebase/pull main, force-push hoặc push main. Không chạy SQL/migration lên Neon hoặc đưa secret vào commit/log/báo cáo.

### Nghiệm thu T-10 — 2026-10-30

- Triển khai đúng 10 file Q-T10-01: TopicStudyService/Repository/Handler, TopicController, SecurityConfig, TopicStudyIntegrationTest, TopicContractTest, OpenAPI, tasks.md và questions.md (chỉ T-10). Không sửa practice, test spec khác hoặc file dùng chung nào khác. Q-T10-02…04 đã Chốt; không còn câu hỏi T-10 mở.
- POST study-sessions kiểm topic tồn tại/có từ trước; khóa advisory theo cặp user/topic bằng tham số trong cùng transaction; tiếp tục lượt đúng chủ hoặc lấy tối đa 10 từ chưa học theo FR-015 trên mọi chủ đề/trạng thái, thứ tự order_index rồi id. Hai request cùng người/chủ đề chỉ một lượt; người khác không bị khóa chặn. HSK 1–9 giữ nguyên ở API.
- Prompt chỉ lấy các trường FR-020, không phát extra.exercises/answerKey hoặc entity. Handler chỉ kiểm LEARNED/KNOWN và ghi cờ ở nhánh trả lời bình thường; skip/audioFailed theo engine. Giới hạn đã chốt: "Không nghe được" vẫn tính đã học theo FR-015/AC-09; hasAudio theo audioUrl khác null; tối đa 3 lần phát tính cả lần đầu. Ngưỡng suspicious 1000 ms ở hook, rules.minDurationMs=0 để engine không gắn cờ nhánh ngoại lệ.
- Test chọn lọc: `.\mvnw.cmd '-Dtest=TopicStudyIntegrationTest,TopicContractTest' test` xanh, 40 ca (37 study + 3 contract; 38 ca mới). Lần đầu một fixture vi phạm CHECK ck_topic_item, đã sửa fixture rồi chạy lại xanh; không sửa schema để né lỗi.
- Gate cuối: Java 17 `.\mvnw.cmd verify` trên PowerShell ngoài sandbox có Docker, BUILD SUCCESS lúc 23:30:18 +07, 6 phút 49 giây; đối chiếu 88 Surefire XML: 1409 test, 0 failure/error/skip. JaCoCo line toàn bộ 96,51% (4481/4643), practice 100% (742/742), vocab 98,60% (141/143). `git diff --check` sạch, 10 file không BOM; code T-10 không TODO/FIXME. FE không thay đổi, gate FE không áp dụng.

**Self-check Constitution T-10:**

- AC-01: contract được leader duyệt; chỉ thêm đúng 403/422 POST study-sessions trước code, contract test và HTTP body/status xanh.
- AC-03/BUS-01: userId lấy từ AuthApi.currentUser(), chỉ đọc/ghi dữ liệu learning; engine kiểm quyền session. Không truy cập bảng auth hoặc shared từ module learning, không trả entity.
- BUS-02/HR-06/BUS-09: điều phối start và advisory lock cùng transaction; RECORD không có điểm, server kiểm enum/cờ, tiếp tục dùng answer/finish và transaction/sự kiện của engine.
- HR-05/HR-07/HR-09: đúng một matcher LEARNER, CSRF cookie giữ nguyên; role/Bearer/CSRF/id sai đã test. SQL tham số hóa, lỗi đúng error_code/message/request_id và details khi validation; không đưa input/chi tiết nội bộ vào thông báo.
- HR-02/HR-08/BUS-06: không đọc hoặc stage .env/secret, không log PII thật; fixture tự soạn trong Testcontainers, không dữ liệu bản quyền.
- AC-05/AC-07/AC-09: không migration/index/DDL/Neon, không thay kiểu điểm; truy vấn từ đã học chỉ dùng cột quan hệ, không lọc JSONB; hạn chế audioFailed đã được leader chấp nhận và ghi rõ.
- ES-01/ES-03/ES-05: Java 17/Maven Wrapper, không dependency mới; tên lớp/package/route đúng; test FR-prefix từ AT-25…28 và bảo mật/đồng thời, full verify không skip, coverage đạt ngưỡng toàn bộ và practice.
- ES-06/ES-07: commit T-10 Conventional Commits riêng với fix có sẵn trên spec/003-vocab-topics-part2; stage file cụ thể và chỉ hunk T-10 của questions.md. Bàn giao sau push qua Draft PR #24; không merge PR, không merge/rebase/pull main, force-push hoặc push main.

## Q-T11-01 — Shadow Plan luyện nhận diện/nghe, đã duyệt

**Task:** T-11; FR-025…FR-030, AT-29…AT-32; dùng FR-015, §0.6/§0.10/§0.11 và AT-01/07/19…21 để kiểm việc nối vào engine/factory.

**Trạng thái:** DUYỆT ngày 2026-10-31; Q-T11-02…06 dưới đây là quyết định cuối của leader, thay thế các đề xuất ban đầu. Nhánh `spec/003-vocab-topics-part3` tại worktree `C:/KLTN_FALL2026/CNHSK-t11`, tạo từ `origin/main` mới nhất `ee910c8` sau fetch (PR #26 đã merge). Điều kiện bắt đầu đủ: spec 001 có 36 task xong; spec 002 T-01…03 xong, Q5 đã chốt, T-10 đã merge. Baseline leader xác nhận 1537 test xanh; không dùng lại nhánh đã merge.

**File đọc:** AGENTS.md §12, constitution.md, constraints/safety.md và spec 001 §0/CSRF/AuthApi đã đọc, không thay đổi trên main mới. Spec 003 spec.md → contracts/openapi-practice-vocab.yaml → tasks.md/questions.md; không có plan.md. Code liên quan: learning/vocab/TopicStudyService.java, TopicStudyRepository.java, TopicQueryRepository.java, web/TopicController.java; learning/practice/generate/ChoiceItemFactory.java, PracticeService.java, PracticeSource.java, ServedItem.java, ServedItemView.java, PracticeRateLimiter.java và entity/{Attempt,AttemptItem,AttemptRepository,AttemptItemRepository}.java; learning/content/entity/{Lexeme,LexemeExtra,Topic,TopicItem}.java; auth/api/AuthApi.java; shared/config/SecurityConfig.java, shared/exception/{ApiException,ErrorCode,GlobalExceptionHandler}.java; test support/AbstractIntegrationTest.java, learning/vocab/{TopicStudyIntegrationTest,TopicContractTest}.java, factory tests và shared/exception/GlobalExceptionHandlerTest.java; V1/V3/V4 và pom.xml chỉ đọc khi cần đối chiếu query/gate.

**Danh sách file xin duyệt vào cột File T-11 (11 file; Java viết tắt …/ = src/main/java/com/cnhsk/):**

| # | File | Nội dung |
|---|---|---|
| 1 | `…/learning/vocab/TopicPracticeService.java` (tạo) | Điều phối chọn từ, tạo câu qua factory và gọi engine, transaction |
| 2 | `…/learning/vocab/TopicPracticeRepository.java` (tạo) | Truy vấn cột quan hệ: topic/các từ đã học, tổng hợp số lần đúng, toàn bộ ứng viên theo ưu tiên |
| 3 | `…/learning/vocab/web/TopicController.java` | Thêm POST practice-sessions và DTO/enum nội bộ; AuthApi lấy userId; giữ GET và study POST |
| 4 | `…/shared/config/SecurityConfig.java` (**dùng chung**) | Chỉ thêm một matcher POST `/api/topics/{id}/practice-sessions` cho LEARNER |
| 5 | `…/shared/exception/ErrorCode.java` (**dùng chung**) | Chỉ thêm NO_LEARNED_WORDS, NO_AUDIO_WORDS, NOT_ENOUGH_WORDS, INVALID_FOCUS_WORDS trước INTERNAL_ERROR; giữ thứ tự mã cũ |
| 6 | `src/test/java/com/cnhsk/learning/vocab/TopicPracticeIntegrationTest.java` (tạo) | AT-29…32, lỗi, bảo mật, snapshot, nối answer/finish và rate limit hiện có |
| 7 | `src/test/java/com/cnhsk/learning/vocab/TopicContractTest.java` | Giữ kiểm tra cũ, bổ sung path/request/201/status cho POST mới |
| 8 | `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java` | Chỉ thêm bốn chuỗi enum vào danh sách containsExactly theo ErrorCode; không sửa assert/kịch bản khác |
| 9 | `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml` | Chỉ POST practice-sessions: thêm 403 đang thiếu, mô tả quyết định được leader chốt trước code; giữ schema shared và endpoint khác |
| 10 | `specs/003-vocab-topics/tasks.md` | Phạm vi đã duyệt, trạng thái và kết quả T-11; chỉ ✅ sau đủ gate |
| 11 | `specs/003-vocab-topics/questions.md` | Shadow Plan, Q-T11-02…06, quyết định và nghiệm thu T-11 |

Không đề xuất migration/index/dependency hoặc handler mới. Tái sử dụng nguyên PracticeService, ChoiceItemFactory và rate limiter; không sửa entity, bộ máy practice, FE, test spec khác hoặc năm named handler stub từ PR #26. Nếu phát sinh schema thì dừng xin leader cấp V5 trước, chỉ Testcontainers; không chạy SQL/migration Neon.

**Cách làm đã duyệt:**

1. Controller kiểm JSON/body bắt buộc, mode đúng RECOGNITION/LISTENING, focus 1…20 ID nguyên dương theo contract và Q đã chốt; id sai định dạng hoặc <=0 trả 422 như T-09. userId chỉ từ AuthApi.currentUser(); lỗi dùng ApiException/format chung. Topic thiếu/rỗng trả 404 TOPIC_NOT_FOUND.
2. Xác định từ đã học đúng FR-015: có STUDY_CARD thuộc TOPIC_STUDY của user trên bất kỳ chủ đề/trạng thái nào, kể cả skip/audioFailed đã chốt T-10. Không lọc meta JSONB, không đọc auth/shared table. Query tổng hợp số lần is_correct=true trong TOPIC_PRACTICE của user trên mọi chủ đề/chế độ/trạng thái, ưu tiên ít đúng rồi ngẫu nhiên khi hoà (Q-T11-03).
3. Kiểm chưa học từ nào trước focus; áp dụng focus rồi audio theo Q-T11-04. Duyệt toàn bộ danh sách ứng viên theo ưu tiên, bỏ từ thiếu nhiễu và bù từ sau cho tới đủ 10 câu hoặc hết ứng viên (Q-T11-05); không LIMIT 10 mục tiêu trước factory. Không query từng từ để đếm lịch sử; các truy vấn phân tầng nhiễu vẫn do factory hiện có làm.
4. Gọi ChoiceItemFactory.create(word, topicId, type, 4); dùng options/correctAnswer phía server, không serialize ChoiceOptions ra client. RECOGNITION: MEANING_CHOICE/VOCAB, prompt.text, không pinyin. LISTENING: AUDIO_WORD_CHOICE/LISTENING, prompt.audioUrl, không phát text/pinyin/nghĩa mục tiêu. Câu không đủ nhiễu bị bỏ theo G-4; không còn câu trả NOT_ENOUGH_WORDS, không tự chuyển sang PINYIN_INPUT. mixedDistractors = OR cờ factory, đặt ở meta lượt theo G-4.
5. Tạo no liên tiếp, minDurationMs=800 và maxReplays=3 tính cả lần đầu; LISTENING hasAudio=true, RECOGNITION hasAudio=false. Không thêm meta.mode; FE suy ra chế độ từ loại câu. Gọi PracticeService.start với TOPIC_PRACTICE, refId topic, không preRecorded/ttlOverride: IMMEDIATE, TTL 2 giờ. Mỗi POST hợp lệ tạo lượt mới, 201, không resume/deduplicate/khóa topic. Không nhận điểm client; answer/finish, suspicious, skip/audioFailed, sự kiện và 120 lượt/giờ dùng engine hiện có.

**Test dự kiến (tên FR025_…FR030_, lấy AT làm gốc):**

- FR025/AT-29: >10 từ đã học, X đúng 3 lần/Y đúng 0 → Y được ưu tiên, giới hạn 10; lịch sử user khác/TOPIC_TEST không tác động; hoà ngẫu nhiên, không phát lặp lexeme trong lượt. Kiểm học ở chủ đề khác và bằng chứng skip/audioFailed vẫn hợp lệ theo FR-015. Phạm vi lịch sử/trạng thái theo Q đã chốt.
- FR026/AT-30: câu nhận diện đúng type/skill, prompt có chữ và không pinyin; đủ 4 options A–D, không trùng nghĩa; GET/start không lộ answer/accept/answerKey, entity hoặc PII. Nhiễu ngoài chủ đề gắn mixedDistractors; toàn kho không đủ → NOT_ENOUGH_WORDS.
- FR027/FR028/AT-31: chỉ dùng mục tiêu có audio; không audio → NO_AUDIO_WORDS, chưa học → NO_LEARNED_WORDS; không cặp đồng âm trong options; đúng rules, max 3 lần tính cả lần đầu, audioFailed không tính điểm qua engine.
- FR029/AT-32: chỉ mục tiêu trong focus hợp lệ; có từ chủ đề khác/chưa học/không tồn tại → INVALID_FOCUS_WORDS. Kiểm []/trùng/null/sai kiểu/>20 và focus không có audio theo Q-T11-04 đã duyệt; không âm thầm bỏ ID sai hoặc mở rộng pool mục tiêu.
- FR030 + nối engine: durationMs=799/800 cho cờ đúng, chấm server IMMEDIATE, finish tính từ attempt_items; skip/audioFailed theo engine. 429 E-R12 khi tạo lượt thứ 121; không thêm rate limiter. Request lỗi không tạo attempt.
- RBAC/CSRF: guest 401, các role khác 403, cookie thiếu/sai CSRF 403, cookie hợp lệ/Bearer thành công; id/body/mode sai 422 và request_id đúng. HSK 1–9 ở API giữ nguyên. Contract test so POST với source và giữ ba path cũ. Query chọn mục tiêu tổng hợp có giới hạn, không N+1 đếm đúng.

**Lệnh sau duyệt:** PowerShell thường ngoài sandbox có Docker, Java 17; bỏ DEBUG chỉ trong tiến trình test. `.\mvnw.cmd '-Dtest=TopicPracticeIntegrationTest,TopicContractTest,GlobalExceptionHandlerTest' test`, rồi `.\mvnw.cmd verify`. Đối chiếu Surefire 0 failure/error/skip, JaCoCo toàn bộ>=80%, practice>=90%; `git diff --check`, phạm vi 11 file, UTF-8 không BOM/TODO. Một commit `feat(vocab): add topic practice sessions (T-11)`, stage từng file/hunk T-11; push nhánh part3 và mở Draft PR mới vào main sau đủ gate. Không merge PR, merge/rebase/pull main, force-push hoặc push main.

**Rủi ro:** hiểu sai phạm vi điểm đúng; focus giới hạn nhầm cả nhiễu; thiếu sinh câu sau khi lấy 10 mục tiêu; lộ đáp án/đọc sai chế độ khi khôi phục. Giữ named stubs PR #26 để không tái phát lỗi Spring context. Rủi ro đã biết của T-12: stub TOPIC_TEST sẽ trùng nguồn khi có TopicTestHandler thật, đã ghi ở PR #26; không giải quyết bằng thay engine/test cũ trong T-11.

### Q-T11-02 — Phạm vi file dùng chung và OpenAPI

**Chốt, 2026-10-31 — leader duyệt:** Đúng 11 file trên. SecurityConfig chỉ thêm 1 matcher POST practice-sessions cho LEARNER. ErrorCode thêm NO_LEARNED_WORDS, NO_AUDIO_WORDS, NOT_ENOUGH_WORDS, INVALID_FOCUS_WORDS ngay trước INTERNAL_ERROR; GlobalExceptionHandlerTest đồng bộ thứ tự, giữ containsExactly. OpenAPI chỉ thêm 403 và mô tả cho POST này, trước code. Không sửa practice engine, ChoiceItemFactory, test spec khác; không migration/dependency. Nếu cần schema thì dừng xin số V5.

### Q-T11-03 — Phạm vi đếm số câu đúng

**Chốt, 2026-10-31 — leader duyệt:** Đếm theo user+lexeme trên mọi chủ đề, cả hai chế độ và mọi trạng thái lượt, chỉ ref_type=TOPIC_PRACTICE và is_correct=true. Lịch sử dùng cột quan hệ, không đọc/lọc JSONB; hoà thì ngẫu nhiên. TOPIC_TEST, người khác, câu sai/skip/audioFailed không làm tăng số đúng.

### Q-T11-04 — focusLexemeIds và thứ tự lỗi

**Chốt, 2026-10-31 — leader duyệt:** Thứ tự: (a) body sai, mode thiếu/sai, focus >20 hoặc [], ID <=0 trong body, null/sai kiểu theo contract → 422 VALIDATION_FAILED; (b) id topic sai định dạng/<=0 → 422, topic thiếu/rỗng → 404 TOPIC_NOT_FOUND; (c) chưa học từ nào của chủ đề → NO_LEARNED_WORDS; (d) có focus thì gộp ID trùng, bất kỳ ID không thuộc chủ đề hoặc chưa học → INVALID_FOCUS_WORDS, chỉ dùng các từ focus làm mục tiêu; nhiễu vẫn G-1…G-6; (e) LISTENING lọc audio_url khác null, không còn mục tiêu → NO_AUDIO_WORDS kể cả ngoài focus có audio; (f) không sinh được câu → NOT_ENOUGH_WORDS. Bốn lỗi nghiệp vụ đều HTTP 422.

### Q-T11-05 — Khi G-4 loại mục tiêu trong nhóm 10 từ

**Chốt, 2026-10-31 — leader duyệt:** Duyệt lần lượt toàn bộ ứng viên đã xếp ưu tiên, bỏ từ thiếu nhiễu; dừng khi đủ 10 câu hoặc hết ứng viên. KHÔNG chọn 10 từ rồi mới bỏ. Thêm test chứng minh từ ưu tiên thiếu nhiễu được bù bằng từ sau. Không tự đổi loại câu hoặc mở rộng focus.

### Q-T11-06 — Meta chế độ và rules nhận diện

**Chốt, 2026-10-31 — leader duyệt:** Không thêm meta.mode và không đổi contract cho mode; FE suy ra chế độ từ loại câu. RECOGNITION rules={minDurationMs:800,maxReplays:3,hasAudio:false}; LISTENING={800,3,true}. mixedDistractors=true trong SessionView.meta nếu bất kỳ câu đã sinh lấy nhiễu ngoài chủ đề. RECOGNITION không pinyin trong prompt; items/meta không có đáp án. Mỗi POST hợp lệ tạo lượt mới, 201, không advisory lock; userId từ AuthApi.currentUser().

**Self-check chuẩn bị:** AC-01 contract duyệt trước code; AC-03/BUS-01 AuthApi và learning-owned data; AC-05/AC-09 không migration/Neon, lịch sử query cột quan hệ; HR-02/HR-08 không đọc/chuyển secret/dữ liệu bản quyền; ES-01/ES-05/ES-07 stack, gate và nhánh mới từ main. Gate sau triển khai: test chọn lọc, verify Java 17/Docker 0 failure/error/skip, coverage >=80%/practice >=90%, diff/BOM/TODO; một commit T-11, stage file cụ thể, push và Draft PR mới, không merge/rebase/pull main/force-push/push main.

**Ghi chú triển khai trong phạm vi duyệt:** Test đầu phát hiện HttpMessageNotReadableException (JSON lỗi/body thiếu/sai kiểu) đang bị handler chung ánh xạ 500. T-11 đọc và kiểm JSON cục bộ trong TopicController, chuyển sang ApiException VALIDATION_FAILED qua handler chung; giữ DTO record cho OpenAPI. Kiểm nguyên dương và tràn Long, không cho Jackson cắt phần thập phân hoặc ép chuỗi thành ID; từ chối JSON có dữ liệu thừa sau object. Không sửa GlobalExceptionHandler hay thêm advice/handler mới. Cả 62 test chọn lọc (53 integration T-11, 4 contract, 5 error handler) đã xanh; gate toàn bộ chạy tiếp sau đó. Bốn shared expectation vẫn containsExactly, không nới assert để bỏ lỗi.

### Nghiệm thu T-11 — 2026-10-31

- Đúng 11 file Q-T11-01; Q-T11-02…06 Chốt, không còn câu hỏi T-11 mở. TopicPracticeService/Repository mới; controller thêm POST; SecurityConfig đúng 1 matcher; ErrorCode 4 mã ngay trước INTERNAL_ERROR và test containsExactly đồng bộ; integration/contract tests, OpenAPI, tasks/questions. Bộ máy practice, ChoiceItemFactory, test spec khác, migration/dependency và FE giữ nguyên.
- FR-025: một truy vấn tập hợp ứng viên đã học theo FR-015, đếm đúng bằng cột quan hệ user+lexeme/TOPIC_PRACTICE/is_correct=true trên mọi chủ đề/chế độ/trạng thái, hoà ngẫu nhiên. Duyệt toàn bộ ứng viên và bù từ sau G-4, không LIMIT 10 trước khi sinh; dừng ở 10 câu hoặc hết danh sách. Test 12 ứng viên, từ đầu thiếu nhiễu được bù, đạt 10 câu, xác nhận 11 lần gọi factory.
- FR-026…030: đúng câu nhận diện/nghe, 4 options, không pinyin ở nhận diện, không text/pinyin mục tiêu ở nghe, rules 800/3/hasAudio theo chế độ; mixedDistractors true khi có nhiễu ngoài chủ đề, không meta.mode. Focus gộp trùng, giới hạn mục tiêu; nhiễu vẫn G-1…G-6. Từng POST thành công là lượt mới 201, IMMEDIATE, TTL 2 giờ, không advisory lock. HSK 1–9 ở API giữ nguyên.
- Thứ tự lỗi đã kiểm: JSON/body/focus shape/ID dương → topic → chưa học → focus hợp lệ → audio → sinh câu. JSON lỗi, thiếu body, sai kiểu, null, [], >20, Long tràn/số thập phân/chuỗi ID đều 422 VALIDATION_FAILED; request_id đúng. Các lỗi nghiệp vụ dùng bốn ErrorCode mới, thiếu/rỗng topic dùng TOPIC_NOT_FOUND.
- `.\mvnw.cmd '-Dtest=TopicPracticeIntegrationTest,TopicContractTest,GlobalExceptionHandlerTest' test`: 62 pass (53 integration T-11, 4 contract, 5 error handler; 54 ca mới). `.\mvnw.cmd verify`: BUILD SUCCESS, Java 17/PowerShell ngoài sandbox có Docker, 7 phút 7 giây; 95 Surefire XML xác nhận 1591 test, 0 failure/error/skip. JaCoCo line toàn bộ 96,57% (4724/4892), practice 100% (743/743), vocab 99,09% (217/219). git diff --check sạch; 11 file UTF-8 không BOM, code T-11 không TODO/FIXME. FE không đổi nên không chạy gate FE.

**Self-check Constitution T-11:**

- AC-01: leader duyệt contract trước code, source chỉ thêm 403/mô tả cho POST mới; test runtime request/response/schema giữ đủ các kiểm tra cũ, focus optional và mode bắt buộc.
- AC-03/BUS-01/BUS-06: userId chỉ từ AuthApi.currentUser(), học và lịch sử chỉ truy cập dữ liệu learning; test userId giả không lấy được tiến độ của người khác, response DTO không entity/PII/đáp án.
- BUS-02/HR-06/BUS-09: transaction điều phối read/generate/start; IMMEDIATE answer/finish dùng engine, không tin score client (test score=999999 vẫn chấm 1), lịch sử đúng dựa evidence server.
- HR-05/HR-07/HR-09: một matcher LEARNER, CSRF cookie và Bearer giữ nguyên; input kiểm trước query, SQL tham số hoá; sai body thành ApiException chuẩn qua handler chung duy nhất, không đưa nội dung input hoặc exception nội bộ vào lỗi client.
- HR-02/HR-08: không đọc/stage .env hoặc secret, không log thông tin thật; fixture tự soạn chỉ trong Testcontainers, không dữ liệu đề/tài liệu bản quyền.
- AC-05/AC-07/AC-08/AC-09: không migration/Neon hoặc đổi kiểu điểm/thời gian; TTL/Clock và các ghi evidence dùng engine; lịch sử dùng cột quan hệ, không lọc answer/meta JSONB, không thêm meta.mode.
- ES-01/ES-03/ES-05: Java 17/Maven Wrapper, không dependency mới; package/route/naming đúng; test tên FR025…FR030 theo AT và yêu cầu bù câu; full gate không skip, coverage vượt cả ngưỡng toàn bộ 80% và practice 90%.
- ES-06/ES-07: một Conventional Commit T-11 trên spec/003-vocab-topics-part3 từ main ee910c8; stage đúng 11 file và push/mở Draft PR mới sau gate; không merge PR, merge/rebase/pull main, force-push hoặc push main. Rủi ro stub TOPIC_TEST/TopicTestHandler của T-12 từ PR #26 vẫn được giữ để chốt trong task đó.

## Q-T12-01 — Shadow Plan kiểm tra cuối chủ đề, đã duyệt

**Task:** T-12; FR-031…FR-040, AT-33…AT-39; thêm AT-01/02/12/13/28 để kiểm snapshot, ownership, DEFERRED, đồng thời start. DUYỆT ngày 2026-10-31; các Chốt dưới đây thay thế đề xuất ban đầu. Đã thực hiện đúng một lần `git fetch origin` rồi `git merge --ff-only origin/main` theo cho phép mới: Already up to date, HEAD/merge-base đều e5775df61b333b112b07c2f32ce5f5f5422f3a9f; không stash. Không fetch/merge/rebase/pull main thêm trong task.

**Nhánh:** `spec/003-vocab-topics-t12`, worktree `C:/KLTN_FALL2026/CNHSK-t12`, tạo từ main fa612e8 sau fetch và xác nhận PR #31 merged. Theo yêu cầu tiếp theo của leader "trước khi làm pull code mới nhất về", đã fetch rồi cập nhật fast-forward tới `origin/main=e5775df61b333b112b07c2f32ce5f5f5422f3a9f`; không tạo merge commit/rebase, giữ nguyên plan chưa commit. Nhánh part4 rỗng ban đầu không dùng. Spec 001 có 36 task xong, spec 002 T-01…03 xong, Q5 chốt và T-11 đã merge. Kết quả 1613 test xanh/coverage 96,44%, practice 99,87%, vocab 99,09% là báo cáo leader cho bản gộp; chưa chạy gate T-12 trên nền mới.

**Blocker nền đã được upstream xử lý:** Blob ErrorCode.java tại main fa612e8 từng có STAGE_INCOMPLETE trùng và thiếu dấu } cuối enum. Commit upstream eb195f3 đã sửa; sau fast-forward tới e5775df đã kiểm tra chỉ còn một khai báo và có ngoặc đóng. Không tự sửa lỗi nền hoặc gộp cleanup vào T-12. Chỉ thêm đúng hai mã lỗi T-12 đã duyệt; xem Q-T12-02.

**File đọc:** AGENTS.md §12, constitution.md và constraints/safety.md, spec 001 §0/AuthApi/CSRF đã đọc và không đổi; spec 003 spec.md → contracts/openapi-practice-vocab.yaml → tasks.md/questions.md (không plan.md). Code liên quan: learning/vocab/{TopicQueryService,TopicQueryRepository,TopicStudyService,TopicStudyRepository,TopicPracticeService}.java, web/TopicController.java; learning/practice/{PracticeService,PracticeSource,PracticeSourceHandler,ServedItem,ServedItemView,PracticeRateLimiter}.java, generate/ChoiceItemFactory.java, entity/{Attempt,AttemptItem,AttemptRepository,AttemptItemRepository}.java; content/entity/{Lexeme,LexemeExtra,Topic,TopicItem}.java và text/PinyinUtils.java; auth/api/AuthApi.java; shared/config/SecurityConfig.java, shared/exception/{ApiException,ErrorCode}.java; test support/AbstractIntegrationTest.java, vocab/{TopicQueryIntegrationTest,TopicPracticeIntegrationTest,TopicContractTest}.java, ba test cần cô lập handler dưới đây và các test answer/maintenance/controller để đối chiếu tác động; GlobalExceptionHandlerTest.java. Migration/pom chỉ đọc khi đối chiếu schema/gate.

**15 file đã duyệt vào cột File T-12; …/ = src/main/java/com/cnhsk/:**

| # | File | Phạm vi |
|---|---|---|
| 1 | `…/learning/vocab/TopicTestService.java` (tạo) | Điều kiện/resume, chọn và trộn tối đa 20 từ, vòng 4 loại câu/fallback, gọi start |
| 2 | `…/learning/vocab/TopicTestRepository.java` (tạo) | Từ/ví dụ, advisory lock user/topic, lịch sử nộp loại trừ session hiện tại, batch từ sai; tái dùng thống kê T-09 khi phù hợp |
| 3 | `…/learning/vocab/TopicTestHandler.java` (tạo) | Bean tên topicTestHandler; isSuspicious/onFinish, extra; không override validateAnswer/enrichMeta |
| 4 | `…/learning/vocab/web/TopicController.java` | Chỉ thêm POST `/api/topics/{id}/tests`, userId từ AuthApi, giữ các endpoint cũ |
| 5 | `…/shared/config/SecurityConfig.java` (**dùng chung**) | Chỉ thêm đúng 1 matcher POST tests cho LEARNER |
| 6 | `…/shared/exception/ErrorCode.java` (**dùng chung**) | Sau khi nền đã được sửa: chỉ thêm NOT_ENOUGH_LEARNED, RETRY_TOO_SOON trước INTERNAL_ERROR; giữ mã 006/T-11 và thứ tự cũ |
| 7 | `src/test/java/com/cnhsk/learning/vocab/TopicTestIntegrationTest.java` (tạo) | Handler thật, HTTP/start/answer/finish, AT và biên/đồng thời |
| 8 | `src/test/java/com/cnhsk/learning/vocab/TopicContractTest.java` | Thêm POST tests, 200/201/status/schema; giữ kiểm tra cũ |
| 9 | `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java` (**test dùng chung**) | Chỉ thêm đúng hai kỳ vọng enum tương ứng, giữ containsExactly và mã 006/T-11 |
| 10 | `src/test/java/com/cnhsk/learning/api/PracticeApiIntegrationTest.java` | Đổi đúng tên mock stub sang topicTestHandler, không sửa assert/fixture |
| 11 | `src/test/java/com/cnhsk/learning/practice/PracticeFinishIntegrationTest.java` | Cùng thay đổi annotation để finish giả không chạy handler nguồn thật |
| 12 | `src/test/java/com/cnhsk/learning/practice/PracticeAbandonIntegrationTest.java` | Cùng thay đổi annotation vì race abandon/finish có thể kết thúc TOPIC_TEST giả |
| 13 | `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml` | Chỉ POST tests: thêm 403 và mô tả quyết định được duyệt trước code; giữ shared schemas/finish/endpoint khác |
| 14 | `specs/003-vocab-topics/tasks.md` | Phạm vi/quyết định/nghiệm thu T-12, chỉ ✅ sau gate |
| 15 | `specs/003-vocab-topics/questions.md` | Plan, câu hỏi, Chốt và self-check T-12 |

Không đề xuất sửa engine practice, ChoiceItemFactory, PinyinUtils, PracticeApi, entity, FE hoặc test spec 004/005/006. Không migration/index/dependency mới; nếu phát sinh schema thì dừng xin số V5 trước và chỉ test Testcontainers, không SQL/migration Neon.

**Cách làm đề xuất:**

1. Dùng transaction và Clock. Kiểm id đúng như T-09/T-11. Advisory xact lock bằng key có tham số `TOPIC_TEST:{userId}:{topicId}`, cùng cách T-10, không khóa hàng topic. Topic thiếu/rỗng 404; tìm active đúng người/chủ đề, chưa hết hạn (kể cả expiresAt=now) trả 200 trước kiểm điều kiện mới, theo FR-033/actions đã chốt T-09. Sau đó mới kiểm >=4 từ (422 NOT_ENOUGH_WORDS, requiredWords=4), học >=80% theo FR-015; thiếu học trả 422 NOT_ENOUGH_LEARNED với learnedWords/requiredWords=ceil(0.8*total). Cooldown đúng Q-T09: lượt gần nhất theo served_at DESC,id DESC, nếu SUBMITTED/trượt và chưa đủ 10 phút trả 429 RETRY_TOO_SOON + retryAfterSeconds làm tròn lên; đúng 10 phút hoặc đã đạt được tạo lượt mới.
2. Trộn toàn bộ từ của chủ đề, không chỉ từ đã học; duyệt đến đủ 20 câu hoặc hết từ. Vòng i%4 MEANING_CHOICE/AUDIO_WORD_CHOICE/PINYIN_INPUT/CLOZE_CHOICE tính theo số câu đã sinh. Dùng factory 4 options cho câu chọn; audio thiếu hoặc ví dụ đầu không chứa text → MEANING_CHOICE; factory thiếu nhiễu → PINYIN_INPUT. PINYIN accept=toNumbered(pinyin) bỏ khoảng trắng; thiếu/sai pinyin bắt lỗi, thử MEANING_CHOICE, vẫn không được thì WARN lexemeId, bỏ từ và bù từ tiếp. Không câu nào sinh được trả 422 NOT_ENOUGH_WORDS và không tạo attempt. CLOZE thay đúng lần xuất hiện đầu bằng ＿＿, hint=examples[0].vi; skill theo loại câu. OR mixedDistractors khi factory dùng nhiễu ngoài chủ đề, không meta.mode/đáp án trong items/meta.
3. Start qua engine TOPIC_TEST, DEFERRED/24 giờ, 201 mới và 200 resume; không preRecorded/ttlOverride/rate limiter mới. Hai start cùng người/chủ đề chỉ một session. Câu mới/nháp/review trước finish không có correct/correctAnswer/accept/explanation theo quy tắc đã chốt; own answered[].answer được phép. Answer/finish/expiry/abandon và chấm pinyin dùng nguyên engine.
4. Handler isSuspicious dùng submittedAt-servedAt < 2000ms * số câu đã phát, OR do engine làm; không đổi điểm vì suspicious. onFinish dùng score/maxScore từ FinishContext, passed theo ngưỡng 90% và guard maxScore>0. KHÔNG advisory lock khi finish, tránh đảo thứ tự với khoá dòng engine; lock start bảo đảm một lượt mở. Truy vấn prior SUBMITTED loại trừ sessionId hiện tại, không phụ thuộc việc JPA đã flush current status hay chưa. bestPercent/topicCompleted gộp prior với current; firstCompletion=passed và chưa có prior pass. WrongWords theo Q-T12-06, batch query, không N+1; DTO/map không entity/PII. Hook chạy trong transaction hiện có, không gọi engine ngược; refId/topic/lexemeId thiếu không gây lỗi.

**Cô lập mock TOPIC_TEST (đã duyệt):**

- Bean thật dùng tên cố định topicTestHandler. Trong đúng ba file số 10…12, đổi `@MockitoBean(name = "stubPracticeSourceHandler")` thành `@MockitoBean(name = "topicTestHandler")` trên cùng field PracticeSourceHandler handler. Spring thay đúng handler thật bằng mock tại các context kiểm bộ máy/facade, tránh chạy cùng lúc cả mock TOPIC_TEST và handler business thật. Giữ mọi source setup/assert/fixture.
- PracticeApiIntegrationTest và PracticeFinishIntegrationTest thực sự finish TOPIC_TEST giả. PracticeAbandonIntegrationTest cũng có race abandon/finish TOPIC_TEST nên cần cô lập dù mặc định mock trả TOPIC_STUDY. Hai lớp answer/maintenance chỉ answer/view/expiry cho TOPIC_TEST, không finish hợp lệ nên không đề xuất sửa; handler T-12 chỉ override hai hook finish, không thêm hook answer.
- TopicTestIntegrationTest dùng bean thật, không mock PracticeSourceHandler, kiểm đủ passed/firstCompletion/best/wrongWords/suspicious và rollback/expiry cần thiết. Không dùng profile để tắt handler cho toàn bộ test; không nới assert để che lỗi. Test spec khác chỉ được chạy hồi quy, không tự sửa; phát sinh nhu cầu thì báo leader xin scope.

**Test dự kiến (FR031_…FR040_, từ AT-33…39):**

- 7/10 → requiredWords=8; đúng 80% được thi; <4 từ theo Q được chốt. Học ở chủ đề khác/skip/audioFailed vẫn theo FR-015; test hỏi cả 20% chưa học. Role/CSRF/id/ownership/request_id và 429 tạo lượt mới theo engine; resume không trừ lượt mới.
- Active trả cùng id, 2 POST đồng thời 201/200 chỉ một session; user khác không bị khóa. Resume ưu tiên khi điều kiện nội dung đổi nhưng topic còn từ. Trượt 3 phút → retry≈420 giây, đủ 10 phút được thi, đạt được thi lại ngay; latest theo quy tắc T-09.
- 25 từ → 20 câu không lặp; vòng loại câu dựa no, fallback audio/example/nhiễu; cloze chỉ thay lần đầu. Snapshot của 201/200/GET/nháp không lộ đáp án; ghi đè nháp, finish thiếu câu tính sai. Pinyin dùng quy tắc đã chốt AT-18 và lỗi dữ liệu Q-T12-05.
- 20 câu/nộp 10 giây suspicious=true vẫn có điểm; đúng biên 2 giây/câu không gắn cờ nguồn; generic suspicious vẫn OR. 18/20 passed và firstCompletion true, lần sau false; giữ best/topicCompleted khi lượt sau điểm thấp. Missing/skip sai và audioFailed không tính điểm; wrongWords theo Q-T12-06, guard maxScore=0 không hoàn thành.
- 25 giờ EXPIRED, finish 422 và không chấm. Hai POST đồng thời cùng người/chủ đề chỉ tạo một session; chỉ khóa advisory lúc start, giữ nguyên khóa dòng/transaction finish của engine. Completion/evidence/events theo transaction core. Hồi quy cả ba lớp được cô lập và answer/maintenance/controller còn nguyên trừ annotation nếu rơi vào điều kiện đã duyệt.

**Lệnh sau khi nền được sửa và leader duyệt:** Java 17, PowerShell thường ngoài sandbox có Docker, bỏ DEBUG chỉ trong tiến trình test. `.\mvnw.cmd '-Dtest=TopicTestIntegrationTest,TopicContractTest,GlobalExceptionHandlerTest,PracticeApiIntegrationTest,PracticeFinishIntegrationTest,PracticeAbandonIntegrationTest,PracticeAnswerIntegrationTest,PracticeMaintenanceIntegrationTest' test`, rồi `.\mvnw.cmd verify`; 0 failure/error/skip, JaCoCo toàn bộ>=80%, practice>=90%, git diff --check/BOM/TODO sạch. Một commit `feat(vocab): add topic tests (T-12)`, stage file cụ thể/chỉ T-12, push và Draft PR mới. Không merge PR, merge/rebase/pull main sau khi tạo nhánh, force-push hoặc push main.

### Q-T12-02 — Lỗi nền ErrorCode đã được xử lý upstream

**Đã xử lý bằng code upstream:** eb195f3 sửa enum; đã cập nhật nhánh T-12 fast-forward tới e5775df theo yêu cầu pull mới nhất của leader và kiểm tra nguồn. Không gộp cleanup này vào T-12. Shadow Plan và Q-T12-03…06 sau đó được leader DUYỆT ngày 2026-10-31; triển khai theo đúng các Chốt này. Yêu cầu pull trước đó không được coi là duyệt triển khai.

### Q-T12-03 — Phạm vi, file dùng chung và mock

**Chốt, 2026-10-31 — leader duyệt:** Đúng 15 file; SecurityConfig 1 matcher POST tests cho LEARNER; ErrorCode chỉ NOT_ENOUGH_LEARNED/RETRY_TOO_SOON trước INTERNAL_ERROR, test enum thêm hai kỳ vọng đúng thứ tự, giữ containsExactly và mã 006/T-11. OpenAPI chỉ 403/mô tả POST này trước code. Ba named mock đã nêu đổi thành @MockitoBean(name="topicTestHandler"), giữ assert/fixture. Nếu PracticeAnswerIntegrationTest (ca dòng 299) hoặc PracticeMaintenanceIntegrationTest (ca dòng 176) fail vì TOPIC_TEST thì được đổi đúng annotation tương tự và báo trong PR; không sửa test khác, test ngoài phạm vi fail thì dừng báo leader. Chỉ advisory lock lúc start, KHÔNG lock finish. Không migration/DDL, dependency mới hoặc sửa engine.

### Q-T12-04 — Mã lỗi khi chủ đề có 1…3 từ

**Chốt, 2026-10-31 — leader duyệt:** Trong transaction/lock user+topic: id sai 422; topic thiếu/rỗng 404 TOPIC_NOT_FOUND; active chưa hết hạn 200; <4 từ 422 NOT_ENOUGH_WORDS kèm requiredWords=4; học<80% 422 NOT_ENOUGH_LEARNED kèm learnedWords/requiredWords; cooldown 429 RETRY_TOO_SOON kèm retryAfterSeconds. Dùng thống kê/công thức latest/cooldown T-09, không viết lệch; đúng 80% và đúng 10 phút được tạo mới.

### Q-T12-05 — Từ cần PINYIN_INPUT nhưng pinyin thiếu hoặc không chuẩn hoá được

**Chốt, 2026-10-31 — leader duyệt:** Trộn toàn bộ từ, duyệt tới đủ 20 câu hoặc hết từ; vòng i%4 với i là số câu ĐÃ SINH. Thay thế đúng FR-035. Khi cần PINYIN_INPUT mà pinyin thiếu/sai: bắt lỗi toNumbered, thử MEANING_CHOICE, vẫn không được thì bỏ từ, WARN chỉ kèm lexemeId, lấy từ tiếp. Không accept rỗng/null. Không câu nào sinh được → 422 NOT_ENOUGH_WORDS, không attempt. Bổ sung test thiếu pinyin đổi dạng và bù; không cắt danh sách 20 từ trước khi thử sinh.

**Chi tiết triển khai:** TopicTestRepository tái sử dụng nguyên ChoiceItemFactory bằng constructor hiện có, cùng EntityManager/random và transaction start. Bắt IllegalArgumentException ngay tại helper chọn nhiễu. Không sửa factory/engine hoặc tạo bean factory mới. Lý do đã kiểm bằng test: gọi proxy transactional của factory rồi bắt lỗi pinyin ở caller vẫn khiến transaction bị rollback-only; mở REQUIRES_NEW lại chiếm thêm kết nối và làm test khóa khác user chờ hết thời gian với pool 3. Giải pháp cuối giữ toàn bộ start/generate/persist trong một transaction, một kết nối mỗi request. Test thiếu pinyin/bù câu và khóa theo user kiểm trực tiếp hành vi này.

### Q-T12-06 — Nguồn dữ liệu wrongWords và dữ liệu đã thay đổi

**Chốt, 2026-10-31 — leader duyệt:** Handler dùng điểm/thời gian server; passed khi score*100>=90*maxScore và maxScore>0. Lịch sử dùng attempts/cột quan hệ, loại session hiện tại, không điều kiện JSONB. wrongWords gồm correct=false cả bỏ trống, loại audioFailed, lấy Lexeme theo lô; ID mất/null bỏ khỏi danh sách, vẫn chấm snapshot. Không lỗi với refId không thật/null, lexemeId null hoặc topic thiếu từ test khác. Giữ bestPercent/topicCompleted theo lịch sử; không advisory lock finish. Bổ sung biên 90%, cooldown đúng 10 phút, topic 3 từ, bù câu thiếu pinyin, 2 POST chỉ một lượt và từ bỏ trống có trong wrongWords.

**Self-check chuẩn bị:** AC-01 contract duyệt trước code; AC-03/BUS-01 AuthApi/learning-owned data; AC-05/AC-09 không migration/Neon, query lịch sử cột quan hệ; HR-02/HR-08 không secret/PII/dữ liệu HSK bản quyền; ES-01/ES-05/ES-07 giữ stack/gate/nhánh. Nền hợp lệ và plan đã duyệt. Sau triển khai: chọn lọc rồi verify Java 17/Docker, 0 failure/error/skip, coverage>=80%/practice>=90%, diff/BOM/TODO; một commit T-12, stage cụ thể, push Draft PR mới, báo commit/merge-base/test/coverage rồi dừng; không tự merge PR.

### Báo cáo T-12 — 2026-10-31

- Hoàn thành FR-031…FR-040/AT-33…39 theo Q-T12-03…06. Start có advisory lock theo user/topic, resume ưu tiên, kiểm đủ từ/80%/cooldown theo T-09; trộn toàn bộ ứng viên, sinh tới 20 câu và bù câu lỗi pinyin. DEFERRED/24 giờ, điểm/thời gian do engine tính. Handler không khóa advisory; lịch sử loại session hiện tại, wrongWords gồm bỏ trống và loại audioFailed, truy vấn từ theo lô.
- Đúng 15 file trong bảng phạm vi T-12 ở trên. Ba test cũ chỉ đổi tên mock sang topicTestHandler; assert/fixture giữ nguyên. Cả PracticeAnswerIntegrationTest và PracticeMaintenanceIntegrationTest xanh, không dùng quyền sửa annotation có điều kiện. Không sửa test spec khác, engine hoặc ChoiceItemFactory. Không còn câu hỏi mở hay lệch spec; chi tiết xử lý transaction của helper được ghi tại Q-T12-05.
- Gate chọn lọc cuối: `.\mvnw.cmd '-Dtest=TopicTestIntegrationTest,TopicContractTest,GlobalExceptionHandlerTest,PracticeApiIntegrationTest,PracticeFinishIntegrationTest,PracticeAbandonIntegrationTest,PracticeAnswerIntegrationTest,PracticeMaintenanceIntegrationTest' test` → BUILD SUCCESS, **148 test**, 0 failure/error/skip (40 T-12 integration, 5 contract, 5 handler lỗi, 98 hồi quy practice; thêm tổng cộng 41 ca mới).
- `.\mvnw.cmd verify` → BUILD SUCCESS, Java 17/PowerShell thường có Docker, **8 phút 08 giây**. Đối chiếu 103 Surefire XML: **1766 test**, 0 failure/error/skip. JaCoCo line: **96,66% toàn bộ (5232/5413)**, **99,87% practice (758/759)**, **99,44% vocab (358/360)**. Vượt ngưỡng toàn bộ 80% và practice 90%; kiểm kiến trúc module xanh. FE không đổi.
- `git diff --check` sạch; đúng 15 file UTF-8 không BOM, Java trong phạm vi không TODO/FIXME. Nhánh `spec/003-vocab-topics-t12`; merge-base **e5775df61b333b112b07c2f32ce5f5f5422f3a9f**. Một commit `feat(vocab): add topic tests (T-12)`, stage từng file cụ thể; push nhánh và mở Draft PR mới theo duyệt, báo commit/PR trong bàn giao rồi dừng chờ leader kiểm bản gộp.

**Self-check Constitution T-12:**

- ✅ AC-01: contract được leader duyệt trước code; YAML chỉ thêm 403/mô tả POST tests. Test runtime kiểm đủ 200/201, lỗi, schema SessionView và id int64; giữ kiểm tra endpoint cũ.
- ✅ AC-03/BUS-01/BUS-06: userId từ AuthApi.currentUser(); chỉ truy cập bảng learning, không auth/shared table; DTO không entity/PII/đáp án trước finish. Test principal giả, role khác, ownership và snapshot đều xanh.
- ✅ BUS-02/HR-06/BUS-09/AC-10: start/generate/persist cùng transaction; finish giữ transaction/khóa dòng/sự kiện của engine. Score/maxScore/thời gian lấy từ server; kiểm biên 90%, maxScore=0, wall time 2 giây/câu. Không advisory lock finish; hai POST chỉ tạo một lượt, user khác không bị khóa chung.
- ✅ HR-01/HR-03/HR-04/HR-05/HR-07/HR-09: không đổi password hashing/cookie/CORS. Chỉ một matcher LEARNER; CSRF cookie giữ nguyên, Bearer dùng cơ chế hiện có. Id không hợp lệ trả 422, lỗi có error_code/message/request_id; SQL tham số hóa và WARN chỉ lexemeId, không lộ exception/input ra client.
- ✅ HR-02/HR-08: không đọc/stage .env hoặc secret, không host Neon/email thật/dữ liệu HSK bản quyền. Fixture tự soạn trong Testcontainers; tài khoản test đăng ký qua API với dữ liệu example.test và mật khẩu sinh tạm.
- ✅ AC-05/AC-06/AC-07/AC-08/AC-09: không migration/DDL/Neon hoặc sửa schema/enum DB; điểm BIGINT, thời gian Clock/server UTC. Query learned/cooldown/history bằng cột quan hệ, không lọc JSONB. Dữ liệu snapshot/draft/evidence và expiry qua engine hiện có.
- ✅ ES-01/ES-03/ES-05: Java 17/Maven Wrapper, không dependency mới, naming đúng; test mới tên FR031…FR040 theo AT và các biên leader bổ sung. Gate đầy đủ 0 skip, coverage vượt cả hai ngưỡng.
- ✅ ES-06/ES-07 và safety: đúng 15 file đã duyệt, shared chỉ thay phần được chốt; một Conventional Commit T-12, không nhắc công cụ trong message. Chỉ thực hiện một bước fetch/ff được cho phép trước code, giữ merge-base e5775df; không stash/restore thay đổi người khác, merge/rebase/pull main thêm, merge PR, force-push hoặc push main. Push/Draft PR là các bước leader đã cho phép; sau báo cáo dừng chờ review.

## Q-T14-01 — Nghiệm thu SCR-011 Topic List

**Chốt, 2026-10-31 — leader DUYỆT Shadow Plan T-14:** FR-041; AT-40 chỉ phần lọc HSK. Các phần còn lại của AT-40 thuộc T-15…T-17, không đánh dấu AT-40 đạt toàn bộ.

**Nền:** Đã fetch origin và kiểm HEAD/merge-base/origin/main cùng **73af018c6338b453fa9e3c24e3b70a28e0732ab2**. Nhánh mới `spec/003-vocab-topics-t14`, worktree `C:/KLTN_FALL2026/CNHSK-t14`, tách riêng để giữ nguyên thay đổi của người dùng ở worktree IDE. Không merge/rebase/pull main sau khi tạo nhánh. Điều kiện bắt đầu đủ: 36 dòng task spec 001 đều ✅ (footer 14/36 đã cũ, chỉ báo không sửa trong task này), spec 002 T-01…03 ✅, Q5 đã chốt; T-12 đã merge qua PR #32, T-13 đã ✅ trên main.

**Phạm vi đúng 4 file:**

| File | Thay đổi được duyệt |
|---|---|
| `cnhsk-web/src/features/vocab/pages/Scr011TopicListPage.test.tsx` | Chỉ thêm test FR041_… và helper/fixture cần thiết; giữ nguyên 8 test cũ |
| `specs/003-vocab-topics/tasks.md` | Sửa đường dẫn T-14 cho khớp màn hiện có, thêm phạm vi/kết quả gate, chỉ đánh dấu ✅ sau khi đạt gate |
| `specs/003-vocab-topics/questions.md` | Chốt, báo cáo và self-check T-14 |
| `specs/003-vocab-topics/spec.md` | Đúng một dòng làm rõ FR-041: bộ lọc UI hiện HSK 1–3 qua SUPPORTED_HSK_LEVELS, API/types vẫn 1–9 |

**Cách làm đã duyệt:** Nghiệm thu và bổ sung test cho màn đang có; không dùng SCR-011 để thêm tính năng mới. Giữ controller/view/helper/query/API/types/route và shared nguyên vẹn. Áp dụng FR-016/FR-041/D-06: không trạng thái LOCKED, không ổ khóa; bổ sung test xác nhận. Screen-fields còn prerequisite/khóa là tài liệu thấp hơn spec; theo quyết định đã chốt, không hỏi lại. Nếu test cho thấy cần sửa production, dừng và báo leader, không sửa trong T-14 khi chưa có duyệt bổ sung.

**File đã đọc:** AGENTS.md §12 → constitution.md/safety/global → spec 001 §0 → spec 003/spec và OpenAPI → tasks/questions (không plan.md), điều kiện spec 002. Đối chiếu SCR-011 trong screen-fields sau spec; code chỉ gồm Scr011TopicListPage/test, TopicListView/preview, topicListQuery, topics API/test, types/labels, shared useApiQuery/client/LevelBadge/FormField/Progress và routeAccess/routes/screenRegistry/screens. Chỉ đọc các file dùng chung, không sửa.

**Test sẽ thêm:** đủ trường thẻ từ dữ liệu server, phân biệt điểm 0 với null và trạng thái hoàn thành; loading trước khi có dữ liệu; kết hợp tìm kiếm/trạng thái và xóa bộ lọc không gọi API lại; đổi HSK nhanh bỏ phản hồi cũ; Back/Forward giữ bộ lọc HSK trên URL; bấm thẻ sang đúng chi tiết; không LOCKED/ổ khóa. Giữ chọn HSK 1–3, API/types 1–9. Fixture tự soạn, không đề thi HSK/dữ liệu cá nhân thật.

**Gate chỉ FE (leader chốt):** npm ci; test chọn lọc Scr011TopicListPage.test.tsx và api/topics.test.ts; npm run lint; npm run typecheck; full `npm test -- --maxWorkers=2 --minWorkers=1`; npm run build; git diff --check/BOM/TODO. Không chạy Maven vì không đổi backend; quy định ngoại lệ FR023 tốc độ import không cần áp dụng ở T-14. Không commit package-lock.json nếu npm ci làm đổi file, không thêm dependency/migration/SQL/Neon.

**Bàn giao:** một commit `test(vocab): verify topic list acceptance (T-14)`, git add từng file cụ thể trong đúng phạm vi, push nhánh và Draft PR mới. Báo commit, merge-base, số test rồi dừng chờ leader kiểm bản gộp/báo xanh. Không merge PR, force-push/push main hoặc stash/restore thay đổi người khác; không secret, host Neon, email thật, BOM hoặc nội dung nhắc công cụ trong commit message.

### Báo cáo T-14 — 2026-10-31

- Nghiệm thu SCR-011 hiện có theo FR-041; thêm **8 test FR041_…**, giữ nguyên toàn bộ 8 test cũ/fixture/assert. Kiểm đủ trường thẻ lấy từ server; điểm 0 khác null, học đủ 100% không tự suy ra COMPLETED; loading; tìm kiếm + trạng thái + xóa bộ lọc không gọi API lại; HSK Back/Forward giữ URL; phản hồi HSK cũ không ghi đè mới; bấm thẻ đến đúng chi tiết; không LOCKED/ổ khóa. Không phát hiện lỗi cần sửa production.
- Đúng 4 file trong Q-T14-01. tasks.md sửa đường dẫn cũ TopicListPage.tsx thành màn thực tế Scr011TopicListPage.tsx và ghi rõ file test/phạm vi; spec.md chỉ thêm **1 dòng** làm rõ FR-041 về UI HSK 1–3, API/types 1–9. Không sửa production, API/OpenAPI, shared/app/routeAccess/screenRegistry, test khác, package.json hoặc package-lock.json.
- `npm ci` exit 0, lockfile nguyên vẹn. Test chọn lọc cũ trước thay đổi: **11/11**. Chọn lọc cuối bằng `npm test -- src/features/vocab/pages/Scr011TopicListPage.test.tsx src/features/vocab/api/topics.test.ts --maxWorkers=2 --minWorkers=1`: **19/19** (16 page + 3 API), exit 0.
- `npm run lint`: exit 0, **0 lỗi/1 warning có sẵn** react-refresh/only-export-components tại shared/ui/LevelBadge.tsx. `npm run typecheck`: exit 0. `npm test -- --maxWorkers=2 --minWorkers=1 --reporter=json --outputFile=<temp>`: exit 0, **1059 test/114 file**, tất cả assertion status=passed, 0 failure/error/skip/todo; đã đọc JSON đối chiếu. `npm run build`: exit 0, 2149 module, Vite build 4,13 giây. Không chạy Maven theo gate chỉ FE đã duyệt, không có số coverage BE mới; không dùng kết quả T-12 làm coverage T-14.
- `git diff --check` sạch; đúng 4 file UTF-8 không BOM, test không TODO/FIXME. Chỉ nghiệm thu **phần lọc HSK của AT-40**; các phần khác thuộc T-15…T-17, **chưa đánh dấu AT-40 đạt toàn bộ**. Không còn câu hỏi T-14 mở hay lệch spec; giữ D-06 không LOCKED. Bàn giao một commit `test(vocab): verify topic list acceptance (T-14)` trên `spec/003-vocab-topics-t14`, merge-base **73af018c6338b453fa9e3c24e3b70a28e0732ab2**; push và Draft PR mới rồi dừng chờ leader kiểm bản gộp/báo xanh.

**Self-check Constitution T-14:**

- ✅ AC-01: một dòng spec được leader duyệt trước khi thêm test; giữ hợp đồng GET /api/topics/OpenAPI và API/types 1–9, UI dùng SUPPORTED_HSK_LEVELS 1–3. Không thêm endpoint/tính năng mới, không vượt phạm vi AT-40.
- ✅ AC-03/BUS-01/HR-03/HR-04/HR-05: không đổi module boundary, quyền LEARNER, ownership, cookie/CORS/CSRF hoặc shared client; chỉ kiểm màn đang dùng API và route hiện có, không truy cập DB/module khác.
- ✅ HR-06/BUS-09/BUS-02/AC-07/AC-08/AC-09: thẻ dùng learnedWords/percent/status/bestTestPercent server trả, không chấm điểm hoặc tự suy ra hoàn thành; test điểm 0/null và 100% học vẫn IN_PROGRESS xanh. Không sửa transaction, kiểu điểm/thời gian hoặc query JSONB. Phản hồi request cũ không ghi đè dữ liệu mới đã kiểm bằng promise điều khiển, không sleep hay thời gian giả để che lỗi.
- ✅ HR-01/HR-02/BUS-06/HR-07/HR-08/HR-09: không đổi password hashing/input validation/error handler; giữ lỗi chuẩn và request_id qua client hiện có. Fixture tự soạn, không PII/email thật, mật khẩu, token, host Neon, .env/secret hoặc dữ liệu đề HSK bản quyền; không in nội dung nhạy cảm. Không tự bỏ/ngắt assert để làm xanh.
- ✅ AC-05/AC-06/ES-01/ES-02/ES-03: không SQL/migration/Neon, schema/DB enum, dependency hoặc config; npm ci dùng lockfile hiện có, không đổi lockfile. Giữ React 18/TS 5.7/Vite 5 và component/route/naming hiện có.
- ✅ ES-05: gate FE đúng duyệt, đủ npm ci/chọn lọc/lint/typecheck/full Vitest/build; 8 ca mới tên FR041 theo phần HSK của AT-40 và các ca leader chốt. Full 1059 pass, không skip/todo; giữ 8 test cũ, không sửa test spec khác. Maven không chạy theo duyệt, không yêu cầu ngoại lệ FR023 import.
- ✅ ES-06/ES-07/safety: sửa đúng 4 file đã duyệt; chỉ spec thêm 1 dòng, không production/shared. Một Conventional Commit đúng message T-14, stage từng file cụ thể; push nhánh/Draft PR là quyền leader đã cho phép. Nền 73af018 xác nhận sau fetch, không merge/rebase/pull main sau khi tạo nhánh, merge PR, force-push/push main, stash/restore hoặc đụng thay đổi của người khác. Báo commit/merge-base/test rồi dừng chờ leader.

## Q-T15-01 — Shadow Plan SCR-012 và Học từ mới

**Chốt, 2026-10-31 — leader DUYỆT Shadow Plan T-15 với phạm vi 5 file.** FR-042/FR-023/FR-024; AT-40 của T-15 chỉ nghiệm thu phần bước bị chặn và lý do, không đánh dấu AT-40 đạt toàn bộ. T-14 đã đạt phần lọc HSK; các phần còn lại thuộc T-16/T-17.

**Nền:** Đã fetch trước khi tạo nhánh mới `spec/003-vocab-topics-t15` từ `origin/main=9b26af7fdd7e4cda5747eb5e6ba6e4d1564fcf9a`, worktree `C:/KLTN_FALL2026/CNHSK-t15`. Không merge/rebase/pull main sau khi tạo. Điều kiện đủ: spec 001 có 36 dòng task ✅, spec 002 T-01…03 ✅, Q5 đã chốt; T-14 merge #34. Các thay đổi ở worktree IDE và worktree cũ giữ nguyên.

**Đã đọc:** AGENTS.md/constitution/safety, spec 001 §0, spec 003/OpenAPI/tasks/questions; Q-T09/Q-T10/Q-T13, controller/view/helper/test SCR-012; chỉ đọc usePracticeSession, StudyCard, AudioButton, SessionSummary và test dùng chung để đối chiếu. Không có plan.md.

| FR | Test đã có trên main | Phần còn thiếu đã được duyệt |
|---|---|---|
| FR-042 | Page: header/route mặc định, lý do khóa/số từ thiếu/cooldown; mở nhận diện thật | Đúng nhãn Kiểm tra cuối chủ đề, matrix actions/lý do, đổi topic không giữ cờ/ref cũ; không làm fallback requiredWords theo Q-T15-03 |
| FR-023 | StudyCard: ẩn Hán Việt/audio rỗng và hai nút học; page: LEARNED/KNOWN/duration/resume | Bổ sung integration đủ trường thẻ, audioFailed đúng T-10, reset audio theo câu dù cùng URL; counter về 0 khi tải lại theo T-13 |
| FR-024 | Finish/tổng kết/tải lại topic, sync SUBMITTED/409 và NO_NEW_WORDS | Retry start hoạt động, auto-start/auto-finish không lặp sau lỗi; finish lại chỉ khi người dùng bấm; giữ summary/CTA sau refresh canStudy=false; resume sau câu cuối trước finish |

**Đúng 5 file được sửa:**

| File | Phạm vi |
|---|---|
| `cnhsk-web/src/features/vocab/components/TopicStudyView.tsx` | Nhãn bước cuối, StudyCard reset theo câu và trạng thái resume trong khung |
| `cnhsk-web/src/features/vocab/pages/Scr012TopicStudyPage.tsx` | Lifecycle theo topicId; kiểm /active dù canStudy=false; không POST khi hết từ và không active; retry có chủ ý, chặn auto-finish lặp; giữ summary sau refresh |
| `cnhsk-web/src/features/vocab/pages/Scr012TopicStudyPage.test.tsx` | Thêm test FR023_/FR024_/FR042_ tái hiện lỗi trước khi sửa; giữ test/fixture/assert cũ |
| `specs/003-vocab-topics/tasks.md` | Chỉ T-15: phạm vi, gate, kết quả và phần AT-40 đã kiểm |
| `specs/003-vocab-topics/questions.md` | Chốt, kế hoạch, báo cáo/self-check T-15 |

**Giới hạn đã chốt:** Giữ nguyên topicStudyFlow.ts, không fallback requiredWords, không test field vắng. Không sửa shared/app/practice, backend, spec/OpenAPI, dependency hoặc migration. Cần sửa ngoài 5 file thì dừng hỏi. Không thêm tính năng SCR-012/T-16/T-17; không LOCKED ở cấp chủ đề, bước bị chặn theo actions/lý do FR-042. Không nghe được vẫn tính đã học theo Q-T10-03; bộ đếm nghe về 0 khi tải lại theo Q-T13-02; không tự tạo thời gian/điểm/bằng chứng server.

**Trình tự sau duyệt:** Ghi Chốt → npm ci → baseline/test mới tái hiện lỗi trên production cũ → chỉ sau khi có kết quả đỏ mới sửa hai file production → chọn lọc/hồi quy → lint/typecheck/full Vitest/build → diff/BOM/TODO/lockfile. Gate chỉ FE, không Maven; không có coverage BE mới. Không commit package-lock.json nếu npm ci thay đổi. Một commit T-15, git add file cụ thể, push nhánh và Draft PR mới; báo commit/merge-base/số test rồi dừng chờ leader báo xanh. Không merge PR/main, force-push/push main hoặc thao tác với thay đổi người khác; không secret/PII/email thật/host Neon/HSK bản quyền, SQL/Neon hoặc BOM.

### Q-T15-02 — Khôi phục lượt cũ khi canStudy=false

**Chốt, 2026-10-31 — leader DUYỆT:** Vẫn kiểm /active khi canStudy=false. Có lượt cũ hợp lệ thì tiếp tục hoặc finish, kể cả sau câu cuối; không có lượt thì hiện hết từ mới, không POST tạo lượt. Giữ summary/CTA sau refresh làm canStudy=false. Không mở lại các bước khác bằng điều kiện tự tính của client.

### Q-T15-03 — Không làm fallback requiredWords

**Chốt, 2026-10-31 — leader KHÔNG duyệt fallback:** Backend trả requiredWords kiểu long nên luôn có giá trị; field vắng không xảy ra với API thật. Giữ nguyên topicStudyFlow.ts; không sửa helper/type/OpenAPI và không viết test ca vắng. Đề xuất fallback trước đây đã bị loại khỏi phạm vi T-15.

### Báo cáo T-15 — bàn giao 2026-11-01

**Chốt ngày 2026-10-31; gate FE hoàn tất lúc 2026-10-31 23:59:48 +07.** Đúng 5 file ở Q-T15-01. Không thay topicStudyFlow.ts hoặc viết test requiredWords vắng; Q-T15-03 giữ nguyên quyết định không fallback.

| FR | Test mới và kết quả | Hành vi sau sửa |
|---|---|---|
| FR-042 | 7 ca: đủ 4 nhãn, matrix bước bị chặn/lý do, tin canTakeTest server, đổi topic khi start pending/NO_NEW_WORDS | Bước cuối ghi đúng Kiểm tra cuối chủ đề. Page key theo topicId, không giữ cờ/ref/lượt cũ hoặc nhận phản hồi cũ sang topic mới. Các bước vẫn theo actions, ngoại lệ resume study đúng Q-T15-02. |
| FR-023 | 5 ca: đầy đủ thẻ/ẩn rỗng, gửi một lần khi pending, cùng audio URL qua câu mới, counter sau reload, audioFailed | Tái dùng nguyên StudyCard/AudioButton, key theo no để reset theo câu. Không autoplay, tổng lượt nghe tính cả lần đầu; tải lại về 0. Không nghe được gửi audioFailed không answer và vẫn tính đã học theo chốt T-10. |
| FR-024 | 7 ca: retry start, finish 500/429 không tự lặp, giữ summary sau refresh canStudy=false, resume partial/all-answered khi canStudy=false, không active thì không POST | Retry start reset cờ tự bắt đầu; auto-finish có guard error, chỉ thử lại khi người học bấm. Luôn kiểm active tại bước học, không tạo mới nếu canStudy=false. Giữ tổng kết/CTA sau refresh, không gửi lại câu đã ghi nhận hoặc tự chấm điểm. |

**Bằng chứng test trước sửa:** npm ci exit 0, package-lock.json không đổi. Baseline 6 file liên quan: **66/66**. Chỉ thêm test trước khi sửa production, rồi chạy toàn bộ page: **38 ca, 26 qua/12 đỏ, 0 skip**; lỗi đúng các nhãn, retry start/finish 500/429 sau rerender, cùng audio URL, đổi topic và resume canStudy=false. Request finish gọi lặp được giữ pending trong test để tránh vòng lặp vô hạn; không sleep hoặc nới timeout/ngưỡng. Chỉ sau lần chạy đỏ mới sửa hai file production.

**Gate cuối:**

- `npm test -- src/features/vocab/pages/Scr012TopicStudyPage.test.tsx src/features/vocab/pages/TopicPracticeController.test.tsx src/features/vocab/pages/TopicTestController.test.tsx src/features/practice/components/StudyCard.test.tsx src/features/practice/components/AudioButton.test.tsx src/features/practice/usePracticeSession.test.ts --maxWorkers=2 --minWorkers=1`: **85/85**, 6 file; page 38 ca gồm 19 cũ/19 mới, không failure/error/skip/todo. Khối 19 ca cũ đã so với HEAD nền và giữ nguyên fixture/assert.
- `npm run lint`: exit 0, **0 lỗi/1 warning có sẵn** react-refresh/only-export-components ở shared/ui/LevelBadge.tsx; không sửa file ngoài phạm vi. `npm run typecheck`: exit 0.
- Full `npm test -- --maxWorkers=2 --minWorkers=1 --reporter=json --outputFile=<temp>`: exit 0, **1078 test/114 file**, tất cả assertion status=passed, 0 failure/error/skip/todo; đã đọc JSON đối chiếu.
- `npm run build`: exit 0, **2149 module**, Vite **4,68 giây**. Gate chỉ FE đã duyệt: không Maven, không coverage BE mới; không áp dụng ngoại lệ FR023 import.
- git diff --check sạch; 5 file UTF-8 không BOM; không thêm TODO/FIXME hoặc test focused/skipped. topicStudyFlow.ts, shared/app/practice, backend/OpenAPI/dependency/migration và package-lock.json nguyên vẹn. Không còn câu hỏi mở hay lệch spec. AT-40 **chỉ nghiệm thu bước bị chặn và lý do** trong T-15; chưa đánh dấu đạt toàn bộ, các phần còn lại thuộc T-16/T-17.

**Bàn giao:** nhánh spec/003-vocab-topics-t15, merge-base **9b26af7fdd7e4cda5747eb5e6ba6e4d1564fcf9a**. Một commit `fix(vocab): complete topic study flow (T-15)`, stage đúng 5 file cụ thể, push và Draft PR mới; báo commit/merge-base/test rồi dừng chờ leader kiểm bản gộp/báo xanh. Không tự merge.

**Self-check Constitution T-15:**

- ✅ AC-01: bám FR-042/023/024 và Chốt Q-T15-01…03; không đổi endpoint/OpenAPI/spec hoặc thêm tính năng. requiredWords không fallback, helper/types giữ nguyên. AT-40 chỉ nghiệm thu đúng phần được duyệt.
- ✅ AC-03/BUS-01/HR-03/HR-04/HR-05: giữ module boundary, AuthApi/principal/LEARNER, ownership, cookie/CORS/CSRF và API client. Chỉ sửa integration trong vocab, không truy cập DB hoặc sửa shared/app/practice.
- ✅ BUS-02/HR-06/BUS-09/AC-07/AC-09: giữ BE transaction, score/maxScore và snapshot/evidence server. Finish sau khi các câu được ghi nhận, pending chống gửi trùng; lỗi không tự lặp, retry do người học bấm và không gửi lại câu cũ. canStudy=false vẫn resume lượt server; không active thì không POST mới. Không chấm điểm hay suy ra hoàn thành ở client, không thêm query JSONB.
- ✅ AC-08/HR-07/HR-09: durationMs vẫn đo từ lúc thẻ hiện với giới hạn cũ; tổng lượt nghe/reload đúng chốt T-10/T-13. Lỗi giữ thông báo/request_id qua handler hiện có. Đổi topic remount theo ID và hook generation hiện có chặn phản hồi cũ; regression dùng promise điều khiển, không dựa sleep.
- ✅ HR-01/HR-02/BUS-06/HR-08: không đổi password hashing; không đọc/stage .env, mật khẩu, App Password, token, host Neon/email/PII thật. Fixture mới tự soạn HSK 1–3, không dữ liệu đề/tài liệu HSK bản quyền; API/types vẫn 1–9.
- ✅ AC-05/AC-06/ES-01/ES-02/ES-03: không SQL/migration/Neon/schema/enum DB/dependency; npm ci giữ lockfile, React/TS/Vite và naming hiện có. Hai file production thay đổi nhỏ, không xây lại màn; giữ shared hook/component và controller luyện/kiểm tra nguyên vẹn.
- ✅ ES-05: 19 ca mới tên FR023_/FR024_/FR042_, test tái hiện đỏ trước sửa, toàn bộ gate FE xanh và không skip/todo. 19 ca cũ SCR-012 và các test khác giữ nguyên; Maven không chạy theo duyệt.
- ✅ ES-06/ES-07/safety: đúng 5 file, một Conventional Commit T-15, không nhắc công cụ trong message; git add file cụ thể, push/Draft PR theo quyền đã duyệt. Nhánh từ main 9b26af7 sau fetch trước tạo nhánh; không merge/rebase/pull main thêm, merge PR, force-push/push main hoặc đụng thay đổi người khác. Sau báo cáo dừng chờ leader.

## Q-T16-01 — Shadow Plan luyện nhận diện / nghe

**Chốt, 2026-11-01 — leader DUYỆT đúng 5 file và gate chỉ FE, không Maven.** T-16: FR-043, liên quan FR-025…030/FR-045; AT-40 chỉ nghiệm thu nghe tối đa ba lần và khôi phục lượt luyện nhận diện/nghe. Không đánh dấu AT-40 đạt toàn bộ; xác nhận nộp và nháp bài kiểm tra thuộc T-17. Viết test tái hiện trước sửa, ghi số ca đỏ; lỗi không tái hiện được thì không sửa và ghi lại. Giữ nguyên 9 test cũ. Nền 978bc5d được duyệt, không gộp main.

**Nền:** Đã fetch origin, tạo nhánh mới `spec/003-vocab-topics-t16` từ `origin/main=978bc5dbe7a9b1accb34381fff0edac2855cd1f5`, worktree `C:/KLTN_FALL2026/CNHSK-t16`; HEAD và merge-base cùng hash này. Không pull/merge/rebase main sau khi tạo nhánh. Worktree mới sạch trước khi ghi plan; giữ nguyên thay đổi ở worktree IDE/các worktree cũ. Điều kiện bắt đầu đủ: 36 dòng task spec 001 đều ✅, spec 002 T-01…03 ✅, Q5 đã chốt; T-13…T-15 đã ✅ trên main, leader xác nhận PR #35 đã merge.

**Đã đọc / sẽ dùng để đối chiếu:** AGENTS.md §12 → constitution/safety → spec 001 §0 → spec 003 §0, FR-025…030/041…045 và §14 → contracts/openapi-practice-vocab.yaml → tasks/questions (không có plan.md của spec 003). Code liên quan: TopicPracticeController/test, TopicPracticeStepView/preview, Scr012TopicStudyPage/test, topicStudyFlow, topics API/types; chỉ đọc usePracticeSession/test, ChoiceQuestion/AudioButton/SessionSummary và test dùng chung, package.json. Không đọc thêm code backend để sửa task FE này.

**Đối chiếu tại nền (đọc tĩnh, chưa chạy test trên nhánh T-16):**

| FR / phần yêu cầu | Code và test hiện có | Phần T-16 đề nghị bổ sung |
|---|---|---|
| FR-043: hiện câu, chấm ngay, đúng/sai + đáp án đúng, Câu tiếp | Controller dùng ChoiceQuestion IMMEDIATE; test đầu kiểm phản hồi sai, giải thích, giữ câu tới khi bấm Câu tiếp và finish cuối lượt | Nghiệm thu cả đáp án đúng/sai từ server, không phản hồi trước response; pending chặn gửi trùng, lỗi answer cho thử lại |
| FR-043 / E-R9: audio không autoplay, tổng ba lần tính cả lần đầu | AudioButton dùng audio preload=none, không autoPlay; test controller nghe ba lần/gửi replayCount; test dùng chung kiểm không autoplay/chặn lần bốn | Integration ngay tại SCR-012: trước click không play, lần bốn bị chặn; reset theo câu dù cùng URL, theo lượt mới; không nhận callback audio của câu cũ |
| FR-043 / E-R10…11: audioFailed, skipped | Hai test controller đã kiểm payload; view có thông báo câu không tính điểm | Response correct=null/counted=false phải không hiện đúng/sai; khóa nút sau ghi nhận, Câu tiếp vẫn dùng được, finish không gửi lại câu đã ghi |
| FR-043: tổng kết và luyện lượt mới | Đã dùng SessionSummary với FinishResult server, có CTA; test hiện tại chỉ kiểm CTA/finish một lần | Assert score/maxScore và kết quả server; lượt mới reset duration/counter dù no đầu vẫn là 1. Chốt Q-T16-03 cho đường sync SUBMITTED không có điểm |
| FR-043: thử lại sau lỗi start | onRetry hiện gọi reload, nhưng autoStartAttemptedRef không reset; sau /active trả rỗng sẽ không POST lại, view ở loading | Test tái hiện trước sửa; retry do người học bấm phải thử lại được, không tự lặp POST sau lỗi |
| FR-045: khôi phục lượt | Đã gọi /active + GET; một test tiếp câu chưa trả lời, một test mismatch cho bỏ lượt cũ; mode suy từ item type | Hết câu nhưng IN_PROGRESS: activeNo undefined và view ready không có item hiện trả null; cần Q-T16-02. Thêm regression reload nghe (counter=0), đổi chế độ và response cũ |
| FR-043/045: vòng đời chế độ/câu/lượt | Hook phụ thuộc source/refId, không mode; caller dùng cùng loại controller khi đổi chế độ. shownAtRef hiện chỉ reset khi activeNo đổi | Cô lập controller theo topicId + mode ngay trong file controller; làm mới câu theo sessionId + no, giữ chính sách mismatch hiện có, chặn tác dụng phụ từ request cũ |

Controller hiện có **9 test**, sẽ giữ nguyên test/assert/fixture cũ. Các thiếu sót trên là kết luận từ đường đi của code, sẽ viết test tái hiện trước khi sửa, không báo là lỗi đã đo bằng gate.

**Đúng 5 file được duyệt (không tạo file mới):**

| File | Thay đổi đề nghị |
|---|---|
| `cnhsk-web/src/features/vocab/pages/TopicPracticeController.tsx` | Retry start/answer/finish có chủ ý; khôi phục sau câu cuối theo Q-T16-02; reset theo topic/chế độ/lượt/câu, bỏ tác dụng phụ cũ; xử lý tổng kết sync theo Q-T16-03 |
| `cnhsk-web/src/features/vocab/components/TopicPracticeStepView.tsx` | Hiện trạng thái đang kết thúc thay màn trống; tái dùng nguyên SessionSummary/T-13 với điểm dựng lại theo Q-T16-03, không thông báo thiếu điểm |
| `cnhsk-web/src/features/vocab/pages/TopicPracticeController.test.tsx` | Chỉ thêm test/helper/fixture tự soạn FR043_/FR045_; giữ 9 test cũ |
| `specs/003-vocab-topics/tasks.md` | Chỉ dòng T-16: phạm vi được duyệt, gate/kết quả và giới hạn AT-40; chỉ ✅ sau gate |
| `specs/003-vocab-topics/questions.md` | Shadow Plan, câu hỏi/Chốt, báo cáo và self-check T-16 |

**File dùng chung cần sửa: không có.** Giữ shared/app/features/practice, Scr012TopicStudyPage, topicStudyFlow, API/types, spec/OpenAPI, backend, test spec khác, dependency/lockfile và migration. Không cần migration/số V5. Nếu test chứng minh cần file ngoài danh sách thì dừng, báo leader trước khi sửa.

**Test đã duyệt:** Thêm nhóm FR043_ cho recognition đủ câu/phương án và phản hồi server; pending chống trùng; audio không tự phát/ba lần/chặn lần bốn; skip/audioFailed không tính điểm; counter theo câu cùng URL; lượt mới reset thời gian/counter; tổng kết dùng điểm server; retry start/answer/finish không lặp tự động. Thêm FR045_ cho reload nghe về 0, khôi phục sau câu cuối, đổi chế độ hai chiều/response cũ và sync SUBMITTED. Test Q-T16-02/03 theo Chốt bên dưới, gồm điểm dựng lại và ưu tiên FinishResult thật. Dùng promise điều khiển và đồng hồ kiểm soát, không sleep để che race. Giữ test của T-13/T-15 và test focus/mismatch hiện có.

**Trình tự/lệnh đã duyệt (FE-only, không Maven):** Ghi Chốt → `npm ci` trong cnhsk-web → baseline và test mới tái hiện lỗi trên production cũ → sửa đúng hai file production → `npm test -- src/features/vocab/pages/TopicPracticeController.test.tsx src/features/vocab/pages/Scr012TopicStudyPage.test.tsx src/features/practice --maxWorkers=2 --minWorkers=1` → `npm run lint` → `npm run typecheck` → full `npm test -- --maxWorkers=2 --minWorkers=1` → `npm run build` → `git diff --check`, kiểm UTF-8 không BOM/TODO/FIXME/test focused hoặc skipped và lockfile không đổi. Lưu báo cáo Vitest từng lượt vào temp, không ghi đè kết quả đỏ. Phân biệt red test tái hiện có chủ ý với gate thất bại. Nếu bất kỳ gate Vitest đỏ rồi chạy lại xanh: vẫn báo đúng file + tên test + thông báo lỗi của lần đỏ trong báo cáo/PR; không kết luận flaky do PR hay có sẵn khi chưa có bằng chứng. Không sửa/giảm ngưỡng/skip test để lấy gate xanh. Không commit package-lock.json nếu npm ci làm đổi.

**Rủi ro và giới hạn:** Hai chế độ dùng chung source/refId nên phải giữ bước hỏi tiếp tục/bỏ lượt mismatch, không tự abandon. Reset UI không hủy được POST đã tới server; chỉ bỏ state/navigation/callback cũ, không đổi hành vi backend. Replay/duration khôi phục không có trong GET: counter về 0 theo Q-T13-02, không dựng bằng chứng hay cộng điểm ở client. Finish sau reload một lần theo Q-T16-02, không tự gọi lặp khi lỗi. Full Vitest T-15 có một lần đỏ chưa xác định test/nguyên nhân theo leader; lần T-16 sẽ giữ tên/lỗi từng lượt, không coi kết quả chạy lại là xóa lỗi cũ.

**Bàn giao sau duyệt và gate:** Một commit `fix(vocab): complete topic practice flow (T-16)`, git add từng file cụ thể, push nhánh và mở Draft PR mới. Báo commit + merge-base + số test + mọi lỗi/lần chạy lại rồi dừng chờ leader kiểm bản gộp; không tự merge. Không secret/.env/host Neon/mật khẩu/App Password/email thật hoặc dữ liệu HSK bản quyền; không SQL/DDL/Neon, stash/restore thay đổi người khác, git add . hoặc -A, merge/rebase/pull main, force-push hoặc push main.

**Self-check khi gửi plan trước duyệt:** AC-01 giữ contract; AC-03/BUS-01/HR-03…05 giữ module/API/auth/CSRF; HR-06/BUS-09 không tính điểm client; HR-02/HR-08 không secret/PII/dữ liệu bản quyền; AC-05/AC-09 không DB/JSONB; ES-01/05/06/07 giữ stack, gate, nhánh/commit/review. Chỉ thêm tài liệu câu hỏi, chưa thay production/test hoặc thực hiện gate; chờ duyệt phạm vi và các câu hỏi dưới đây.

### Q-T16-02 — Khôi phục sau câu cuối nhưng lượt chưa finish

**Chốt, 2026-11-01 — leader DUYỆT:** Khi GET trả IN_PROGRESS với tất cả câu đã ghi, tự gọi finish **một lần cho lượt vừa khôi phục**, hiện trạng thái đang kết thúc; nếu lỗi thì dừng tự gọi, chỉ finish lại khi người học bấm Thử lại. Đường bình thường vẫn chờ bấm Câu tiếp sau phản hồi câu cuối rồi mới finish. Retry start theo T-15: chỉ reset cờ auto-start khi người học bấm Thử lại. Đổi topic/mode/session không giữ cờ, bộ đếm hoặc phản hồi cũ.

### Q-T16-03 — Sync ra SUBMITTED nhưng không có FinishResult chứa điểm

**Chốt, 2026-11-01 — leader KHÔNG duyệt thông báo thiếu điểm:** Khi sync trả SUBMITTED, dựng FinishResult tương tự restoredFinishResult của Scr018GrammarLessonPage: score = số correct=true, maxScore = số counted=true, percent = maxScore=0 thì null, còn lại floor(score*100/maxScore). Đây là đếm dữ liệu server đã chấm, không tự chấm; TOPIC_PRACTICE có maxScoreFromServed=false nên khớp engine. Giữ extra rỗng, không tạo giá trị server chưa trả, không sửa SessionSummary. Finish thành công bình thường vẫn ưu tiên nguyên FinishResult server. Thêm test điểm dựng lại đúng và lượt mới dùng điểm finish thật. Đề xuất thông báo thiếu điểm trước duyệt đã bị loại khỏi phạm vi.


### T-16 — Bằng chứng trước sửa production (2026-11-01)

Baseline 9/9 xanh. Sau thêm 21 ca FR043_/FR045_: cả hai lượt red-1 và red-2 đều 30 ca, 18 qua/12 đỏ, không skip; giữ báo cáo riêng ở temp. red-2 chỉ làm rõ assertion replayCount/duration và kiểu helper, chưa sửa production. Cả 9 test cũ vẫn qua. Đây là red regression có chủ ý trước sửa, không phải gate flaky. Danh sách dưới lấy nguyên tên test và dòng đầu thông báo lỗi từ red-2:

| Test trong TopicPracticeController.test.tsx | Thông báo lỗi |
|---|---|
| FR043_ignores a previous question playback promise that resolves after Next | AssertionError: expected 1 to be +0 // Object.is equality |
| FR043_start fails once without looping and starts again only after Retry | Error: Unable to find an element with the text: 妈妈. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible. |
| FR043_normal completion waits for Next and retries a failed finish only after a click | Error: Unable to find an element with the text: Kết quả lượt luyện. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible. |
| FR045_finishes a restored fully answered IN_PROGRESS session once and shows progress | AssertionError: expected "spy" to be called 1 times, but got 0 times |
| FR045_does not repeat a failed automatic finish until Retry is clicked | Error: Unable to find role="button" and name "Thử lại" |
| FR045_switching 'recognition' to 'listening' refreshes active session and drops old feedback | AssertionError: expected "spy" to be called 2 times, but got 1 times |
| FR045_switching 'listening' to 'recognition' refreshes active session and drops old feedback | AssertionError: expected "spy" to be called 2 times, but got 1 times |
| FR045_switching modes after start failure resets the start flag for the new mode | Error: Unable to find role="button" and name `/Nghe từ/` |
| FR045_ignores late focus start navigation and answers after changing topic | AssertionError: expected '/learn/topics/11/practice/recognition' to be '/learn/topics/12/practice/recognition' // Object.is equality |
| FR043_a new session resets question duration even when the first number is unchanged | AssertionError: expected 9800 to be 800 // Object.is equality |
| FR045_rebuilds a SUBMITTED summary from 'mixed graded and uncounted answers' | Error: Unable to find an element with the text: 2/3. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible. |
| FR045_rebuilds a SUBMITTED summary from 'all uncounted answers' | Error: Unable to find an element with the text: 0/0. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible. |

Các ca đã qua trước sửa: pending/phản hồi server, skip/audioFailed không tính điểm, không autoplay/ba lần, reset câu cùng URL, lỗi playback an toàn, retry answer, reload câu nghe với counter=0 và ưu tiên FinishResult thật sau tổng kết dựng lại. Không sửa thêm logic cho các ca đã đạt; giữ làm nghiệm thu. Chỉ sửa lỗi có regression đỏ nêu trên.

### Báo cáo T-16 — bàn giao 2026-11-01

Hoàn thành FR-043 và phần khôi phục FR-045 theo Q-T16-01…03, đúng 5 file đã duyệt. Không còn câu hỏi mở hoặc lệch spec. AT-40 chỉ nghiệm thu nghe tối đa ba lần và khôi phục lượt luyện; chưa đánh dấu AT-40 đạt toàn bộ.

| FR | Test / kết quả | Hiện trạng sau sửa |
|---|---|---|
| FR-043: hiện câu/phản hồi/Câu tiếp | Giữ test cũ; thêm pending/đáp án đúng, skip/audioFailed, lỗi playback và finish thật | Dùng nguyên component T-13; phản hồi từ server, không tự chấm. Luồng thường vẫn chờ Câu tiếp sau câu cuối |
| FR-043: audio | Integration không autoplay, ba lần tính cả lần đầu, chặn lần bốn, câu cùng URL, callback play trả muộn | Reset cùng URL đã đạt trước sửa nên giữ nguyên key audio hiện có; chỉ chặn callback của câu/lượt cũ làm sai replayCount |
| FR-043: retry/lượt mới | Start và finish retry đã tái hiện đỏ; answer retry đã xanh trước sửa | Reset cờ auto-start chỉ khi người học bấm; finish lỗi chỉ thử lại do click. Lượt mới reset duration/counter theo sessionId + no, kể cả no đầu vẫn 1 |
| FR-045: khôi phục/chuyển màn | Restore hết câu, finish lỗi không tự lặp, reload nghe counter=0, đổi mode hai chiều, mode sau start lỗi, focus start trả muộn khi đổi topic | Khôi phục IN_PROGRESS hết câu tự finish một lần và có trạng thái đang hoàn tất; cô lập theo topic/mode, không nhận navigation/callback cũ |
| FR-043/045: tổng kết | Hai ca dựng lại SUBMITTED (2/3=66%, 0/0 percent=null), ca lượt mới dùng đúng FinishResult thật | score đếm correct=true, maxScore đếm counted=true từ server; extra rỗng, không thông báo thiếu điểm, không sửa SessionSummary |

**Gate FE:**

- npm ci: exit 0, lockfile không đổi; không sửa/cập nhật dependency.
- Baseline trước thêm test: 9/9. Thêm 21 ca FR043_/FR045_, hai lượt trước sửa đều 30 ca, 12 đỏ/18 qua; nguyên tên/lỗi ở bảng bằng chứng bên trên. Lượt red-2 chỉ chỉnh helper TypeScript/assertion cho rõ số thực nhận, chưa sửa production. Không coi đây là flaky gate.
- Sau sửa: TopicPracticeController 30/30. Chọn lọc controller + SCR-012 + toàn bộ features/practice: 110/110 trên 10 file (30 controller, 38 page, 42 practice), 0 failure/error/skip/todo. Đã so nguyên khối 9 test cũ với HEAD nền, không đổi test/assert/fixture.
- npm run lint: exit 0, 0 lỗi; 1 warning có sẵn react-refresh/only-export-components tại shared/ui/LevelBadge.tsx. npm run typecheck: exit 0.
- Full Vitest, báo cáo full-1: 1121/1121 test trên 115 file, assertion đều passed, 0 failed suite/failure/error/skip/todo; xanh ngay lượt đầu, không có gate đỏ rồi chạy lại xanh. Không lặp lại full suite sau khi đã xanh vì không đổi production/test thêm.
- npm run build: exit 0, 2149 module, Vite 7,14 giây. Chỉ FE theo duyệt, không chạy Maven hoặc tạo số coverage BE mới; không áp dụng ngoại lệ FR023 import.
- git diff --check sạch; đủ 5 file UTF-8 không BOM, không TODO/FIXME hoặc focused/skipped test trong code sửa. package-lock.json, practice/shared/app, Scr012TopicStudyPage, topicStudyFlow, backend/OpenAPI/dependency/migration giữ nguyên. Không SQL/DDL/Neon.

**Bàn giao:** Nhánh spec/003-vocab-topics-t16, merge-base 978bc5dbe7a9b1accb34381fff0edac2855cd1f5. Một commit fix(vocab): complete topic practice flow (T-16), stage từng file cụ thể; push nhánh, mở Draft PR mới vào main rồi báo commit/merge-base/số test và dừng. Dùng email noreply công khai của tài khoản GitHub cho metadata commit, không ghi email thật vào source/fixture/tài liệu. Chỉ leader gộp/merge sau khi kiểm xanh.

**Self-check Constitution T-16:**

- ✅ AC-01: giữ HTTP contract/OpenAPI, theo FR-043/045 và Chốt Q-T16-01…03. Tổng kết dựng lại đúng công thức leader duyệt; AT-40 chỉ phần được giao.
- ✅ AC-03/BUS-01/HR-03/HR-04/HR-05: giữ module boundary, public API practice, ownership/auth/RBAC/cookie/CORS/CSRF; không sửa features/practice/shared/app hoặc truy cập dữ liệu module khác.
- ✅ HR-06/BUS-09: server chấm câu và trả correct/counted; FE chỉ đếm kết quả đã chấm khi sync SUBMITTED, TOPIC_PRACTICE maxScoreFromServed=false. FinishResult thật luôn được ưu tiên; extra dựng lại rỗng, không tự sinh passed/mastery hoặc trường server chưa trả.
- ✅ HR-07/HR-09: durationMs vẫn clamp theo contract; replayCount tính cả lần đầu, tối đa ba; không tự bịa bằng chứng khi reload. Lỗi an toàn/request_id qua API hiện có; pending chặn gửi trùng, callback cũ không làm sai câu mới.
- ✅ HR-01/HR-02/BUS-06/HR-08: giữ hashing/auth; không đọc/stage .env, secret, token, mật khẩu/App Password, host Neon/email/PII thật hoặc dữ liệu HSK bản quyền. Fixture tự soạn HSK 1–3; API/types vẫn 1–9. Credential GitHub chỉ giữ trong bộ nhớ cho push/PR đã duyệt, không in/ghi vào repo.
- ✅ AC-05/AC-06/AC-07/AC-08/AC-09/BUS-02/AC-10: không schema/migration/SQL/DDL/Neon; giữ transaction/điểm/thời gian/snapshot server, không query JSONB hoặc thay bằng chứng server.
- ✅ ES-01/ES-02/ES-03: giữ stack/dependency/lockfile, không thư viện mới. Giữ export và chữ ký caller/T-13; sửa trong hai file production hiện có, test mới tên FR043_/FR045_.
- ✅ ES-05: test tái hiện đỏ trước sửa, ghi đủ tên/lỗi; 9 test cũ nguyên vẹn; toàn bộ gate FE xanh, không skip/todo. Không sửa logic cho các nghi vấn đã test xanh trước sửa.
- ✅ ES-06/ES-07/safety: đúng 5 file đã duyệt, Conventional Commit có T-16, không nhắc công cụ; git add file cụ thể, push nhánh/Draft PR theo quyền leader. Không stash/restore thay đổi người khác, merge/rebase/pull main sau tạo nhánh, merge PR, force-push/push main; không đụng worktree IDE/các worktree cũ. Dừng sau báo cáo chờ leader review.

## Q-T17-01 — Shadow Plan kiểm tra cuối chủ đề (DEFERRED)

**Chốt, 2026-11-01 — leader DUYỆT đúng 7 file, gate chỉ FE, không Maven.** T-17: FR-044, liên quan FR-006/029/038…040/045, E-R7/E-R9…11/E-F1 và AT-40. Test tái hiện trước sửa, ghi số ca đỏ; giữ nguyên 9 test cũ. Nghi vấn không tái hiện được thì không sửa và ghi lại. TopicPracticeController chỉ sửa nhánh CTA focus, tuyệt đối giữ nguyên restoredFinishResult.

**Nền:** Đã fetch origin rồi tạo nhánh mới spec/003-vocab-topics-t17 từ origin/main=818e86ae7ef8b8ec822555e3a87b0539180f10cf (merge PR #46), worktree C:/KLTN_FALL2026/CNHSK-t17. HEAD/merge-base cùng hash này, không pull/merge/rebase main sau tạo nhánh. Worktree mới sạch trước ghi plan; giữ nguyên worktree IDE/các worktree cũ. Điều kiện đủ: spec 001 có 36 dòng task ✅; spec 002 T-01…03 ✅; Q5 đã chốt; T-01…T-16 ✅, leader xác nhận T-16 trên bản gộp có 1132 test FE xanh.

**File đã đọc / sẽ dùng:** AGENTS.md §12 → constitution/safety → spec 001 §0 → spec 003 §0, FR-025…045 và §14 → contracts/openapi-practice-vocab.yaml → tasks/questions (không plan.md của spec 003). Code: TopicTestController/test, TopicTestStepView/preview, topicTestFlow, TopicPracticeController và các regression focus T-16; Scr012TopicStudyPage để kiểm đường mount/refresh. Chỉ đọc hook usePracticeSession/test, ChoiceQuestion/AudioButton/PinyinInput, shared ConfirmPanel và practice index/types/API. Đối chiếu các test HSK/bước bị chặn/nghe/restore của T-13…T-16 để lập bảng AT-40. Đọc đúng phần actions của TopicQueryService và Chốt T-09 để xác nhận active test luôn canTakeTest=true; không cần sửa page hoặc backend vì tình huống này.

**Đối chiếu tại nền (đọc tĩnh và định nghĩa test, chưa chạy gate T-17):** TopicTestController có 9 test, giữ nguyên toàn bộ test/assert/fixture cũ.

| FR / ca nghiệm thu | Đã có trên main | Còn thiếu / cần kiểm bằng regression |
|---|---|---|
| FR-044: đi lại giữa câu, đổi đáp án | QuestionNavigator, Câu trước/sau; test chọn nháp rồi sang câu 4/câu 3; 4 dạng câu đều có view | Chọn lại câu cũ đúng trạng thái; sửa A→B khi request A chưa xong phải lưu B cuối cùng, không lấy response cũ ghi đè |
| FR-044: lưu nháp ngay và pinyin 500 ms | Test chọn gửi answer ngay; test pinyin chờ 500 ms; hàng đợi tuần tự qua hook một operation | Kiểm biên 499/500 ms, rời câu/nộp khi chưa hết debounce, pending queue nhiều câu, lỗi lưu không tự lặp. flushDrafts hiện gọi tiếp sau cả khi answer trả null; effect pending cũng có thể kích lại khi lỗi |
| FR-044 / E-R7/E-R11: xóa đáp án gõ đã lưu | Ô nhập cho phép xóa; countUnanswered coi rỗng là chưa làm | toAnswerRequest hiện gửi answer=null không skipped khi rỗng (không hợp lệ); draftsToAnswers lại loại nháp rỗng nên finish có thể giữ đáp án cũ. Cần Chốt Q-T17-02 |
| FR-044 / AT-40: xác nhận nộp trong trang | ConfirmPanel role=dialog, số câu thiếu, cancel; test nộp ngay gửi đủ nháp chờ debounce | Nghiệm thu hủy không finish/không window.confirm, hết câu, bấm nộp trùng, chờ autosave đang chạy rồi gửi snapshot mới nhất; finish lỗi giữ nháp và chỉ nộp lại sau xác nhận mới. Chỉ sửa hành vi nếu test tái hiện lỗi |
| DEFERRED: chưa chấm trước finish | Hook trả drafts, ChoiceQuestion feedback=DEFERRED; không truyền result trước nộp | Integration không hiện đúng/sai/đáp án/đạt trước finish; không tự finish khi đã làm đủ hoặc khi reload |
| FR-044: kết quả và từ sai | Test dùng score/passed/wrongWords server; test firstCompletion và giữ kết quả sau refresh topic khóa | Kiểm đủ percent/đạt/chưa đạt/từ sai và ẩn CTA khi danh sách sai rỗng; lượt mới xóa refs, timer, counter, nháp, confirmation và thời điểm của lượt cũ |
| FR-044 / FR-029: luyện lại từ sai | Test click CTA → recognition POST focusLexemeIds khi không có active practice | Nếu /active trả lượt RECOGNITION cũ cùng topic, TopicPracticeController vào ready và không POST focus; cần Chốt Q-T17-04, không tự bỏ lượt cũ |
| FR-045 / AT-40: khôi phục DEFERRED | Test /active+GET lấy nháp, mở câu chưa làm đầu tiên; test giữ counter khi đi lại trong cùng trang | Reload không autosave/không bịa duration/replay, không gửi lại nháp chưa sửa lúc finish (Q-T13); sửa/clear sau restore được gửi; timer/request cũ không tác động topic/session mới |

Các điểm nghi vấn trên mới là kết luận đọc tĩnh. Sau duyệt viết test tái hiện trước khi sửa; ghi số ca đỏ và tên/lỗi. Không sửa logic cho nghi vấn không tái hiện được, ghi rõ trong báo cáo.

**Đúng 7 file được duyệt, không tạo file mới:**

| File | Phạm vi đề nghị |
|---|---|
| cnhsk-web/src/features/vocab/pages/TopicTestController.tsx | Hàng đợi nháp theo phiên và bản sửa mới nhất; pause/retry sau lỗi theo Q-T17-03; clear theo Q-T17-02; timer/refs/duration theo session; finish từ snapshot mới nhất sau xác nhận; bỏ tác dụng phụ cũ |
| cnhsk-web/src/features/vocab/components/TopicTestStepView.tsx | Trạng thái lưu lỗi và thao tác thử lại theo Q-T17-03; pending/xác nhận/nộp chỉ sửa nếu regression chứng minh cần; giữ giao diện và component T-13 |
| cnhsk-web/src/features/vocab/topicTestFlow.ts | Biểu diễn thao tác xóa rõ ràng và chuyển sang skipped=true, giữ nháp xóa trong finish; không nhầm nháp restore chưa sửa với thao tác xóa mới |
| cnhsk-web/src/features/vocab/pages/TopicPracticeController.tsx | Chỉ nhánh focus từ CTA T-17 khi đã có active RECOGNITION theo Q-T17-04; giữ các luồng T-16 khác và không sửa test T-16 |
| cnhsk-web/src/features/vocab/pages/TopicTestController.test.tsx | Chỉ thêm FR044_/FR045_, helper/fixture tự soạn; giữ nguyên 9 ca cũ. Test xuyên màn CTA/focus đặt ở đây, không cần sửa TopicPracticeController.test.tsx |
| specs/003-vocab-topics/tasks.md | Chỉ phạm vi/gate/kết quả T-17; kết luận rõ AT-40 đạt trọn hay còn thiếu, chỉ đánh dấu ✅ sau gate |
| specs/003-vocab-topics/questions.md | Plan/câu hỏi/Chốt, bảng AT-40, bằng chứng đỏ trước sửa và báo cáo/self-check T-17 |

**File dùng chung cần sửa: không có.** Giữ features/practice/shared/app, Scr012TopicStudyPage, TopicPracticeStepView, spec/OpenAPI/backend, test spec khác, dependency/lockfile, DB/migration. Không cần số migration V5. Nếu cần ngoài 7 file thì dừng báo leader trước.

**restoredFinishResult / PR riêng spec 004:** TopicTestController hiện không import/dùng hàm này; T-17 đề nghị tiếp tục không dùng và không sửa phần gom helper. TOPIC_TEST có extra.passed/firstCompletion/wrongWords riêng, GET SessionView không trả các extra đó. Khi sync SUBMITTED mà không có FinishResult, giữ thông báo Đã nộp bài hiện có; không dựng extra hoặc kết quả thi từ helper IMMEDIATE. Kết quả đầy đủ FR-044 lấy từ response finish thật. Khôi phục trong AT-40 là lượt đang làm; không mở rộng sang lưu kết quả đầy đủ của lượt đã nộp khi thiếu contract. Nếu leader muốn đổi chính sách này thì chốt scope riêng trước code.

**Các test sẽ thêm:** FR044_ cho A→B lúc save pending, autosave/queue lỗi và retry, biên pinyin 500 ms/rời câu, clear đã lưu/clear trước debounce rồi finish, xác nhận hủy/hết/thiếu câu, pending và chống nộp trùng, finish lỗi giữ nháp và xác nhận lại, không feedback DEFERRED trước nộp, kết quả/từ sai theo server, CTA focus với active recognition. FR045_ cho restore không auto-save/không tự finish/không resubmit nháp chưa sửa, clear/sửa nháp restore, reset timer/duration/counter theo session và không nhận phản hồi cũ khi đổi topic/rời bước. Dùng promise điều khiển và fake timer khi đo debounce; không sleep để che race. Giữ các test T-13…T-16 nguyên vẹn.

**Cách làm và gate đã duyệt — chỉ FE, không Maven:** Ghi Chốt → npm ci → baseline 9 test cũ → thêm test tái hiện và lưu báo cáo đỏ trước sửa → chỉ sửa lỗi đã tái hiện trong scope → test chọn lọc → npm run lint → npm run typecheck → full Vitest → npm run build → git diff --check/BOM/TODO/lockfile. Không commit lockfile nếu npm ci đổi.

Lệnh chọn lọc trong cnhsk-web: npm test -- src/features/vocab/pages/TopicTestController.test.tsx src/features/vocab/pages/TopicPracticeController.test.tsx src/features/vocab/pages/Scr012TopicStudyPage.test.tsx src/features/vocab/pages/Scr011TopicListPage.test.tsx src/features/practice --maxWorkers=2 --minWorkers=1. Full: npm test -- --maxWorkers=2 --minWorkers=1. Mỗi lượt thêm reporter JSON/outputFile riêng trong temp. Nếu Vitest đỏ rồi chạy lại xanh, vẫn báo file/tên test/thông báo lỗi nguyên văn trong PR, không bỏ qua hoặc tự kết luận do PR/có sẵn. Không skip/đổi ngưỡng/sửa test ngoài scope để lấy xanh; không chạy Maven hoặc áp ngoại lệ FR023 import cho task FE này.

**AT-40 — tiêu chí để kết luận sau T-17:**

| Phần AT-40 | Bằng chứng hiện có / cần bổ sung | Kết luận hiện tại |
|---|---|---|
| Lọc HSK | T-14, Scr011TopicListPage.test.tsx, FR041_ và Chốt UI HSK 1–3 | Đã nghiệm thu T-14; chạy hồi quy nguyên vẹn |
| Bước kiểm tra bị chặn có lý do | T-15, Scr012TopicStudyPage.test.tsx, FR042_AT40_ | Đã nghiệm thu T-15; không tự tính eligibility client |
| Nghe tối đa 3 | T-13/T-16, AudioButton và TopicPracticeController FR043_ | Đã nghiệm thu T-16; chạy hồi quy nguyên vẹn |
| Xác nhận nộp trong trang | Có test T-17 happy path, thiếu ca hủy/pending/lỗi | Cần hoàn tất T-17 |
| Reload khôi phục lượt | Study/practice đã nghiệm thu T-13/T-15/T-16; T-17 có ca restore cơ bản | Cần hoàn tất DEFERRED và bảo toàn nháp/bằng chứng |

Chỉ báo **AT-40 đạt trọn** sau khi các câu hỏi được chốt, T-17 đủ các ca còn thiếu và gate/hồi quy toàn bộ xanh. Tại thời điểm gửi Shadow Plan: **chưa đạt trọn**. Báo cáo cuối sẽ ghi rõ đạt trọn hoặc liệt kê từng phần chưa đạt; không tự coi task cuối đồng nghĩa đã đạt AT-40.

**Rủi ro/giới hạn:** Hook chỉ cho một operation; queue phải giữ bản sửa sau cùng khi request cũ hoàn thành và dừng khi lỗi. Xóa đáp án phải thay được draft server, không chỉ xóa UI. Timer/callback cũ có thể chạm refs lượt mới; phải kiểm bằng test trước sửa. Khi focus tạo lượt mới có thể tồn tại lượt practice cũ: API cho phép mỗi POST tạo lượt mới, không tự abandon khi chưa được chốt. GET chỉ có ItemState theo Q-T13-02/03: counter reload về 0, không đoán audioFailed/duration/replay hoặc gửi lại nháp chưa sửa. canTakeTest của active test đã được server xử lý đúng Chốt T-09, không cần đổi page/actions hoặc hỏi lại. Không đụng công việc gom helper của spec 004.

**Bàn giao sau duyệt/gate:** Một commit fix(vocab): complete topic test flow (T-17), git add từng file cụ thể, push nhánh và Draft PR mới. Báo commit + merge-base + số test + kết luận AT-40 + câu hỏi/lỗi còn lại rồi dừng; theo duyệt T-17 mới nhất: giữ Draft PR, báo commit/merge-base/số test/AT-40 rồi dừng; chỉ leader merge sau khi báo xanh. Không secret/.env/host Neon/mật khẩu/App Password/email thật/HSK bản quyền; không SQL/DDL/Neon, stash/restore thay đổi người khác, git add . hoặc -A, merge/rebase/pull main, force-push/push main. Nhánh không chứa tên công cụ, commit theo Conventional Commits không nhắc công cụ và không BOM.

**Self-check khi gửi Shadow Plan trước duyệt:** AC-01 giữ contract; AC-03/BUS-01 và HR-03…05 giữ API/module/auth/CSRF; HR-06/BUS-09 server chấm, không tự dựng kết quả thi; HR-02/HR-08 không secret/PII/bản quyền; AC-05/AC-09 không DB/migration/JSONB query; ES-01/05/06/07 giữ stack, scope/gate/commit/review. Chỉ ghi tài liệu câu hỏi, chưa sửa production/test hoặc chạy gate; dừng chờ leader duyệt phạm vi và chốt bên dưới.

### Q-T17-02 — Xóa pinyin đã lưu

**Chốt, 2026-11-01 — leader DUYỆT:** Chỉ xóa khi câu đã có đáp án lưu hoặc đang chờ lưu; ô chưa từng gõ không gửi. Xóa pinyin gửi skipped:true, không gửi answer; UI đánh dấu chưa làm, lưu {skipped:true} theo E-R11 DEFERRED (không audioFailed và không answer=null đơn lẻ), giữ thao tác xóa trong payload finish nếu còn chờ debounce. Khi gõ lại giá trị thì bỏ cờ skipped. Chỉ gắn cờ từ thao tác sửa thật; không suy diễn từ answer vắng/null khi restore. Như vậy server không chấm đáp án cũ và khi nộp câu này tính sai. Restore answer=null là chưa làm, không tự gửi lại hoặc suy diễn thao tác xóa. Không đổi API/backend.

### Q-T17-03 — Lỗi autosave và thử lại

**Chốt, 2026-11-01 — leader DUYỆT:** Khi save trả lỗi: dừng tự retry/queue, giữ nháp mới nhất và các câu chưa lưu, hiện thông báo/request_id và nút Thử lại lưu nháp. Chỉ thử lại khi người học bấm hoặc chủ động sửa đúng câu bị lỗi; không reload làm mất phần chưa lưu. Nộp bài vẫn cần xác nhận; finish lỗi giữ nháp và yêu cầu người học mở/xác nhận nộp lại, không tự nộp lặp. Lần lưu đang chờ phải xong rồi mới finish, payload là bản mới nhất của mọi câu. Chỉ sửa những ca có regression đỏ.

### Q-T17-04 — Luyện lại từ sai khi đã có lượt nhận diện dở

**Chốt, 2026-11-01 — leader DUYỆT:** Với CTA focus và active RECOGNITION: tạo lượt focus mới đúng một lần theo FR-025/029, giữ lượt cũ nguyên vẹn, xóa state focus chỉ sau start thành công. Khi active khác mode (LISTENING), giữ luồng hỏi tiếp tục/bỏ lượt hiện có của T-16. Không có state focus thì resume như cũ. Lỗi start không tự lặp, giữ state để bấm thử lại. Refresh sau khi xóa state không tạo thêm lượt. Trong TopicPracticeController.tsx chỉ sửa nhánh focus, giữ nguyên restoredFinishResult đang được spec 004 gom ở PR riêng.


### T-17 — Bằng chứng trước sửa production (2026-11-01)

Baseline 9/9. Thêm 24 ca FR044_/FR045_: red-1 có 31 ca, 10 đỏ/21 qua; bổ sung hai biên Chốt trước sửa (whitespace chưa có đáp án, focus gặp lượt cũ đã làm hết) thành red-2 33 ca, 12 đỏ/21 qua, 0 skip. Cả 9 test cũ vẫn qua. Đây là red regression có chủ ý trước sửa; báo cáo/log từng lượt giữ riêng trong temp. Danh sách red-2:

| Test trong TopicTestController.test.tsx | Thông báo lỗi trước sửa |
|---|---|
| FR044_clearing a restored answer submits skipped immediately even inside debounce | AssertionError: expected undefined to deeply equal [ ObjectContaining{…} ] |
| FR044_clearing a new pending answer is included in finish before debounce | AssertionError: expected undefined to deeply equal [ ObjectContaining{…} ] |
| FR044_clearing a saved answer autosaves skipped without an answer field | AssertionError: expected { durationMs: 526, no: 3, …(2) } to match object { no: 3, skipped: true } |
| FR045_null restored answer is unanswered and is not resaved or inferred as deletion | Error: Unable to find an element with the text: Câu 3/4. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible. |
| FR044_whitespace without a previous answer never sends a deletion | AssertionError: expected "spy" to not be called at all, but actually been called 1 times |
| FR044_save error pauses retries and the retry button saves the retained draft | Error: Unable to find role="alert" |
| FR044_only editing the failed question resumes the paused queue with the latest values | Error: Unable to find role="alert" |
| FR045_new session measures the first question again when its number is unchanged | AssertionError: expected +0 to be 800 // Object.is equality |
| FR044_wrong-word CTA creates focus and keeps old recognition with all answers=false | AssertionError: expected "spy" to be called 1 times, but got 0 times |
| FR044_wrong-word CTA creates focus and keeps old recognition with all answers=true | AssertionError: expected "spy" to be called 1 times, but got 0 times |
| FR044_failed focused start retains state without looping and retries only on click | Error: Unable to find role="button" and name "Thử lại" |
| FR045_refresh after focus state is cleared only restores the focused session | Error: Unable to find an element with the text: 爸爸. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible. |

Đã xanh trước sửa: pending save A cùng bản mới nhất của hai câu khi nộp; debounce 500 ms/rời câu; xóa rồi gõ lại; hủy xác nhận; double submit; finish lỗi giữ nháp/xác nhận lại; restore không gửi lại nháp chưa sửa và không feedback; SUBMITTED chỉ thông báo; server percentage/wrongWords rỗng; timer cũ không chạm lượt mới; focus khác mode hỏi như T-16. Không sửa các luồng đã đạt, giữ làm nghiệm thu; giữ cleanup timer và finish confirmation hiện có, không sửa các nghi vấn không tái hiện được.

### Báo cáo T-17 — bàn giao 2026-11-01

Hoàn thành FR-044/FR-045 và nhánh FR-029 theo Chốt Q-T17-01…04, đúng 7 file. Không còn câu hỏi mở hoặc lệch spec. **AT-40 đã đạt trọn trong phạm vi đã chốt**, gồm đủ năm phần trong bảng bên dưới; không coi số thứ tự task cuối là bằng chứng thay cho test.

| Phần AT-40 | Bằng chứng nghiệm thu / hồi quy cuối | Kết luận |
|---|---|---|
| Lọc HSK | Scr011TopicListPage: 16/16, gồm FR041_AT40_restoresTheHskFilterAndServerListWithBackAndForward và bộ lọc SUPPORTED_HSK_LEVELS 1–3 | Đạt, T-14 và hồi quy T-17 |
| Bước kiểm tra bị chặn có lý do | Scr012TopicStudyPage: 38/38, gồm FR042_AT40_blocksTheStepAndExplainsItsReason, tin actions server | Đạt, T-15 và hồi quy T-17 |
| Nghe lại tối đa ba lần | TopicPracticeController: 30/30 và AudioButton: 5/5, lần đầu có tính, chặn lần bốn, không autoplay | Đạt, T-13/T-16 và hồi quy T-17 |
| Xác nhận nộp trong trang | TopicTestController: dialog số câu thiếu/hết, FR044_cancelling confirmation…, double submit…, finish error…; không window.confirm | Đạt T-17 |
| Reload khôi phục lượt | Hook 22/22 + study/practice đã nghiệm thu; T-17 FR045_null restored answer…, restore keeps server drafts…, SUBMITTED sync… | Đạt cả RECORD/IMMEDIATE/DEFERRED theo contract |

**Thay đổi đã kiểm bằng regression:**

- Xóa pinyin chỉ tạo skipped=true khi có đáp án đã lưu/đang chờ lưu; không gửi answer hoặc audioFailed cùng thao tác xóa. Giữ skipped trong finish ngay cả trước hết debounce; xóa rồi gõ lại gửi đáp án mới, không skipped. Ô chưa gõ/whitespace-only không tạo xóa. Restore answer=null là chưa làm, mở đúng câu đó, không tự gửi lại.
- Queue save lỗi dừng, giữ bản mới nhất/các câu chưa lưu; hiện Thử lại lưu nháp. Chỉ người học bấm hoặc sửa đúng câu lỗi mới tiếp tục queue. Chỉnh câu khác khi đang pause không tự retry câu lỗi. Câu mới đầu session đo duration lại dù no không đổi.
- CTA focus gặp active RECOGNITION tạo một lượt mới, không abandon hoặc finish lượt cũ (kể cả lượt cũ đã trả lời hết). Start lỗi không lặp, giữ state để retry; sau thành công xóa state, refresh chỉ restore. Khác mode giữ luồng hỏi T-16.
- Nhánh focus là phần duy nhất sửa trong TopicPracticeController.tsx; đã so nguyên chuỗi restoredFinishResult với HEAD nền và giữ nguyên hoàn toàn. Không dùng/gom helper ở TopicTestController hoặc features/practice.
- Các nghi vấn đã xanh trước sửa không sửa production: latest snapshot của hai câu khi save pending, debounce/rời câu, clear→retype, cancel/double-confirm, finish lỗi giữ nháp/xác nhận lại, timer cũ không chạm lượt mới, DEFERRED không feedback/resubmit nháp chưa sửa, SUBMITTED chỉ thông báo, kết quả server và active khác mode. Giữ cleanup timer/confirmation hiện có. Toàn bộ ca bắt buộc leader yêu cầu đã có test.

**Giới hạn đã chốt vẫn giữ:** GET chỉ cung cấp ItemState theo Q-T13-02/03; reload counter về 0, không suy diễn audioFailed/replay/duration hoặc gửi lại nháp chưa sửa. Sync DEFERRED SUBMITTED không có FinishResult chỉ hiện Đã nộp bài, không dựng điểm/passed/wrongWords. Kết quả đầy đủ sau finish thật lấy score/percent/extra server. Những giới hạn này được leader chấp nhận, không phải phần AT-40 còn thiếu.

**Gate FE:**

- npm ci: exit 0, package-lock.json không đổi; không cập nhật dependency.
- Baseline 9/9; thêm 24 FR044_/FR045_. Hai báo cáo đỏ trước sửa: red-1 31 ca có 10 đỏ/21 qua; red-2 33 ca có 12 đỏ/21 qua sau thêm hai biên. Tên/lỗi từng ca ở bảng bằng chứng trước sửa, báo cáo/log giữ riêng không ghi đè. Cả 9 test cũ luôn xanh; đã so nguyên khối 9 test/assert với HEAD, fixture cũ giữ nguyên.
- Sau sửa TopicTestController 33/33. Chọn lọc toàn bộ vocab pages + practice 159/159 trên 12 file: topic test 33, topic practice 30, SCR-012 38, SCR-011 16, practice 42. Không failure/error/skip/todo.
- npm run lint: exit 0, 0 lỗi, 1 warning có sẵn react-refresh/only-export-components ở shared/ui/LevelBadge.tsx. npm run typecheck: exit 0.
- Full Vitest full-1: 1156/1156 test trên 115 file, mọi assertion passed, 0 failed suite/failure/error/skip/todo, xanh ngay lượt đầu. Reporter JSON không có tiến độ giữa lượt nên đã kiểm tra tiến trình khi chờ; không hủy/chạy lại suite, không có gate đỏ rồi chạy lại xanh. Các ca đỏ nêu trên là TDD có chủ ý trước sửa.
- npm run build: exit 0, Vite 5,41 giây. Gate chỉ FE theo duyệt; không Maven/coverage BE mới, không áp dụng ngoại lệ FR023 import.
- git diff --check sạch; đúng 7 file UTF-8 không BOM; code sửa không TODO/FIXME/focused/skipped test. practice/shared/app/backend/OpenAPI/dependency/migration/lockfile nguyên vẹn; không SQL/DDL/Neon. Worktree IDE/các worktree cũ không bị sửa/stash/restore.

**Bàn giao:** Nhánh spec/003-vocab-topics-t17, merge-base 818e86ae7ef8b8ec822555e3a87b0539180f10cf. Một commit fix(vocab): complete topic test flow (T-17), stage từng file cụ thể, push và Draft PR mới vào main. Theo duyệt mới nhất giữ Draft, báo commit/merge-base/số test và AT-40 rồi dừng; chỉ leader kiểm bản gộp/merge sau khi báo xanh. Commit dùng danh tính noreply công khai của tài khoản GitHub, không đưa email thật vào source/fixture/tài liệu/metadata.

**Self-check Constitution T-17:**

- ✅ AC-01: giữ contract/OpenAPI; skipped=true là field E-R11 đã có, không endpoint/error code mới. FR-044/045 và Q-T17-01…04 được duyệt trước code, AT-40 có bảng đủ phần/bằng chứng cụ thể.
- ✅ AC-03/BUS-01/HR-03/HR-04/HR-05: chỉ dùng public API/hook/component practice hiện có; không module DB khác, không đổi auth/ownership/RBAC/cookie/CORS/CSRF/shared/app.
- ✅ HR-06/BUS-09: server chấm; UI không đoán điểm/passed/wrongWords DEFERRED khi không có FinishResult. Kết quả dùng response server, không dựng mastery/completion. Hàm restoredFinishResult trong TopicPracticeController giữ nguyên để không đụng PR gom helper spec 004.
- ✅ HR-07/HR-09: clear không gửi answer=null thiếu cờ; skipped và audioFailed không cùng true. Không tự lặp request lỗi; giữ thông báo an toàn/request_id. Lượt mới đo duration theo no/session, giữ clamp/evidence đúng contract.
- ✅ Q-T13-02/03/E-R9/E-R11: không auto-save/finish/resubmit dữ liệu restore chưa sửa; null không suy thành xóa. Bỏ trống DEFERRED được server tính sai khi nộp; replay/counter không giả từ GET. Pending save phải xong trước finish, snapshot chứa bản mới nhất của cả hai câu.
- ✅ HR-01/HR-02/BUS-06/HR-08: giữ hashing, không đọc/stage secret/.env/token/mật khẩu/App Password/host Neon/email thật/PII hoặc dữ liệu HSK bản quyền. Fixture tự soạn HSK 1–3; DB/API/types vẫn nhận 1–9. Credential chỉ trong bộ nhớ cho push/PR đã duyệt, không log/commit.
- ✅ AC-05/AC-06/AC-07/AC-08/AC-09/BUS-02/AC-10: không DB/migration/SQL/DDL/Neon; giữ transaction, snapshot, kiểu điểm/thời gian và engine server; không query điều kiện JSONB hoặc đổi evidence server.
- ✅ ES-01/ES-02/ES-03/ES-05: stack/dependency/lockfile nguyên vẹn; 24 test mới đúng mã FR, 9 ca cũ nguyên vẹn, test đỏ trước sửa và ghi tên/lỗi, toàn gate FE xanh không skip/todo. Không sửa nghi vấn không tái hiện.
- ✅ ES-06/ES-07/safety: đúng 7 file được duyệt; một Conventional Commit T-17 không nhắc công cụ, git add file cụ thể, nhánh không chứa tên công cụ. Không merge/rebase/pull main sau tạo nhánh, merge PR, force-push/push main hoặc thay đổi người khác; Draft PR và dừng chờ leader theo chỉ đạo mới nhất.

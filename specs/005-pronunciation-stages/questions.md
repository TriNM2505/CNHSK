# Questions: Spec 005 - Luyện phát âm 8 tầng

## T-01 - Shadow Plan đã duyệt ngày 2026-10-30

Kiểm tra ngày 2026-10-30 trên `origin/main` tại `7391f87`.
Nhánh làm việc: `spec/005-pronunciation-stages`, tạo trực tiếp từ commit này.

### Q-T01-01 - Thiếu API tìm lượt đang làm cho FR-005

- FR-005 yêu cầu `activeSessionId` và `activeAnswered` cho từng tầng.
- Chỉ đạo ban đầu yêu cầu gọi practice qua `learning.api.PracticeApi`.
- `PracticeService.findActive(userId, source, refId)` đã có, nhưng `PracticeApi` chỉ có `start`, `answer`, `finish`, `view`.
- `PracticeApiTest.FR012_publicSurfaceHasOnlyFourMethodsAndNoInternalTypesOrTransactions` đang kiểm đúng bốn phương thức. Spec 003 §0.11 cũng liệt kê bốn phương thức này.

**Leader chốt ngày 2026-10-30:**

- `learning.pronunciation` cùng module `learning`, gọi trực tiếp `PracticeService.findActive(userId, PRON_STAGE, stageId)`.
- `PracticeApi` dành cho module khác, ví dụ `community`; không thêm `findActive`, không giao bổ sung cho spec 003.
- Giữ nguyên `PracticeApiTest` và hợp đồng bốn phương thức của facade.
- `activeSessionId = sessionId`; `activeAnswered = SessionView.answered().size()`.
- Tối đa tám tầng, được gọi theo từng tầng; không tối ưu sớm.

**Trạng thái:** đã chốt, không còn câu hỏi mở.

### Q-T01-02 - Cột File của T-01 thiếu các file cần sửa

- Mô tả T-01 yêu cầu bổ sung `PracticeSource` và `ItemType`, nhưng cột File chỉ có `learning/pronunciation/*` và `learning/progress/UserProgress.java`.
- `PracticeSource.PRON_STAGE` đã có đúng `PRACTICE`, `IMMEDIATE`, TTL 7 ngày, `allowSkip=false`; không cần sửa lại.
- `PRON_CHOICE` chưa có trong `learning/practice/ItemType.java` hoặc `PracticeApi.ItemType`. Test nền kiểm hai enum khớp nhau, nên cần bổ sung cả hai.
- `SecurityConfig` mặc định từ chối; cần thêm một matcher GET `/api/pronunciation/stages` cho role `LEARNER`.
- Cột File chưa liệt kê các test mới và tài liệu theo dõi task.

**Leader duyệt ngày 2026-10-30 phạm vi bổ sung:**

- `src/main/java/com/cnhsk/learning/practice/ItemType.java`: chỉ thêm `PRON_CHOICE(true)` cuối danh sách.
- `src/main/java/com/cnhsk/learning/api/PracticeApi.java`: chỉ thêm `PRON_CHOICE` cuối enum `ItemType`, cùng thứ tự bản trong practice; không thêm phương thức.
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`: đúng một matcher GET `/api/pronunciation/stages` cho `LEARNER`, đặt trước `/api/admin/**`; POST thuộc Shadow Plan T-02.
- `src/test/java/com/cnhsk/learning/pronunciation/PronStageQueryServiceTest.java`.
- `src/test/java/com/cnhsk/learning/pronunciation/PronunciationIntegrationTest.java`.
- `specs/005-pronunciation-stages/tasks.md`: cập nhật phạm vi đã duyệt, ghi `PRON_STAGE` đã có; đánh dấu hoàn tất sau khi qua gate.
- File `questions.md` này: ghi nhận quyết định của leader.

Phạm vi chính vẫn là `learning/pronunciation/*`, `learning/progress/UserProgress.java` (chỉ các cột dùng) và hai file test trên. Không sửa hoặc nới test nền.

**Trạng thái:** đã chốt, không còn câu hỏi mở.

### Điều kiện bàn giao leader đã chốt ngày 2026-10-30

- Không tạo migration: `attempt_items.item_type` là `VARCHAR(30)` không CHECK; `user_progress` đã nhận `PRON_STAGE`. Nếu cần đổi schema phải dừng và xin số.
- Danh sách chỉ trả tóm tắt, không trả đơn vị bài tập hoặc đáp án đúng.
- Chưa có đơn vị có audio thì `unitCount=0`, `playable=false`, không lỗi. Test tự tạo dữ liệu trên Testcontainers, không phụ thuộc CSV demo.
- Gate trên PowerShell thường có Docker: hai test T-01 + `ModuleBoundaryTest` + `PracticeApiTest`, sau đó Maven Wrapper `verify`; không failure/error/skip, coverage toàn bộ ít nhất 80%, practice ít nhất 90%; `git diff --check` sạch.
- Một task một commit, stage từng file cụ thể; giữ nguyên thay đổi có sẵn của người khác.
- Chỉ đạo cuối của người dùng: **không push code**. Giữ kết quả ở local; chưa mở Draft PR vì chưa được push nhánh.

## Ghi nhận áp dụng chỉ đạo mới

- Điều kiện nền spec 002 T-01/T-02 và spec 003 đợt 0 đã có trên main. Leader đã cho phép bắt đầu, không phải chờ T-13 của spec 003.
- Quy ước nhánh `agent/005-<task>` trong tasks.md đã cũ; áp dụng chỉ đạo mới dùng `spec/005-pronunciation-stages`.
- AT-02 trong T-01 kiểm `playable=false`; phần POST trả 422 thuộc T-02, không triển khai POST trong T-01.
- Không cần migration mới; không sửa migration đã merge hoặc chạy SQL lên Neon.

## Kết quả T-01 ngày 2026-10-30

- Triển khai FR-001…FR-005: tóm tắt tầng theo thứ tự, chỉ đếm đơn vị có audio; trạng thái theo ngưỡng hiện tại và luật giữ tầng đã mở; lượt đang làm lọc đúng người dùng và tầng.
- `UserProgress` chỉ ánh xạ ba cột khóa chính và `percent`, khớp schema hiện có. GET không ghi tiến độ.
- Hai file test mới: 17 unit + 15 integration trên PostgreSQL/Testcontainers; tự tạo fixture và rollback thay đổi dữ liệu tham chiếu sau mỗi test.
- Gate chọn lọc: 64 test, 0 failure/error/skip, gồm `ModuleBoundaryTest` và `PracticeApiTest` nguyên trạng.
- Maven Wrapper `verify` trên Java 17, PowerShell thường có Docker: 1369 test trong 87 suite, 0 failure/error/skip; đối chiếu Surefire XML.
- JaCoCo line: toàn bộ 4385/4553 (96,31%); practice 743/743 (100%); pronunciation 35/36 (97,22%).
- Runtime OpenAPI của GET kiểm operation/status, field, required, enum và JSON content; contract nguồn đã duyệt không thay đổi.
- Ba file dùng chung chỉ sửa đúng phạm vi: thêm enum ở `ItemType.java`, thêm enum tương ứng ở `PracticeApi.java`, thêm một matcher GET ở `SecurityConfig.java`.
- Không đổi migration, dependency, cấu hình test, test nền hoặc frontend. Không có câu hỏi nghiệp vụ còn mở.
- Hoàn tất ở local; không push, không mở PR theo chỉ đạo cuối của người dùng.

### Self-check Constitution

- **HR-02, HR-08, BUS-06:** không đọc/commit bí mật hoặc dữ liệu có bản quyền; response chỉ có tóm tắt tầng, không email/PII hay đáp án.
- **HR-09, BUS-01:** giữ lỗi chung qua security filters; danh tính từ `auth.api.AuthApi`; tiến độ và lượt luôn lọc theo người dùng, có test chống lẫn dữ liệu.
- **AC-01, AC-03:** implement contract đã duyệt; không truy cập bảng auth; gọi practice trực tiếp trong cùng module learning theo quyết định Q-T01-01.
- **AC-05, AC-06, AC-07:** giữ nguyên Flyway/schema; entity khai `schema="learning"`; độ chính xác dùng `BigDecimal`/`NUMERIC(5,2)`; enum mới chỉ là Java, không đổi kiểu DB.
- **BUS-11:** dùng `pass_percent` của từng tầng, kiểm cả ngưỡng chính xác và trường hợp tăng ngưỡng sau khi đã mở tầng.
- **ES-01, ES-03, ES-05:** giữ stack, naming; integration test endpoint và full gate/coverage đạt yêu cầu.
- **ES-06, ES-07:** nhánh `spec/005-pronunciation-stages`; một commit Conventional Commits cho T-01, stage file cụ thể; không push hoặc tự merge.

## T-02 - Shadow Plan đã duyệt ngày 2026-10-30

### Kiểm tra đầu task

- Đã đọc lại AGENTS, constitution, spec 001 §0, spec/OpenAPI/tasks 005 và spec 003 §0; spec 005 chưa có `plan.md`.
- Đã `git fetch origin`; `origin/main` vẫn ở `7391f87`, có V4 và các phần nền spec 002/003 đã kiểm ở T-01. Leader đã miễn điều kiện chờ T-13 của spec 003.
- Nhánh hiện tại `spec/005-pronunciation-stages` chưa merge, có thêm commit T-01 local `8bf7b87`; giữ nguyên commit này và các file ngoài task. Chưa tạo nhánh part2, chưa merge/rebase/pull main hoặc push.
- Áp dụng quyết định Q-T01-01: pronunciation nằm trong module learning, được gọi trực tiếp practice; không bổ sung phương thức vào `PracticeApi`.

### Shadow Plan đề xuất

- **Task:** FR-006…FR-009; AT-03…AT-07 và phần POST của AT-02.
- **Đọc:** `PronStage`, `PronUnit`, `PronStageRepository`; query/progress/controller của T-01; `PracticeService.start/findActive`, `ServedItem`, cấu hình `PRON_STAGE`; `AuthApi`, `SecurityConfig`, `ErrorCode`, `GlobalExceptionHandlerTest`, test nền/Testcontainers. Chỉ đọc thêm code phục vụ các mục này.
- **Tạo:** `src/main/java/com/cnhsk/learning/pronunciation/PronStageSessionService.java` theo cột File của T-02.
- **Cách làm:** kiểm stage 1–8 và tồn tại, tái sử dụng luật mở tầng FR-004 từ T-01, kiểm có đơn vị có audio; dùng lượt `IN_PROGRESS` chưa hết hạn của đúng người dùng/tầng thì trả 200; lượt mới trả 201, trộn đơn vị và phương án, gán lại khóa đáp án đúng sau khi trộn; gọi bộ máy `PRON_STAGE` với TTL mặc định 7 ngày, IMMEDIATE, không cho bỏ qua. Response dùng `SessionView`, không trả đáp án server chưa chấm.
- **Test:** AT-03 tầng khóa và `requiredStageIndex`; AT-04 tầng ngoài phạm vi/chưa nhập; AT-02 tầng đã mở nhưng không có audio trả 422; AT-05 tiếp tục giữ cùng snapshot và bốn câu đã trả lời; AT-06 đủ đơn vị theo Q-T02-02, hoán vị/khóa mới vẫn chấm đúng và không lộ đáp án; AT-07 không cho skip, `audioFailed` hợp lệ. Kiểm cookie/CSRF, bearer, role, lỗi chuẩn, hết hạn tạo lượt mới, HTTP 200/201 và runtime OpenAPI. Fixture tự tạo trong database Testcontainers, không dùng CSV demo hay Neon.
- **Gate dự kiến:** test T-01/T-02 + `ModuleBoundaryTest` + `PracticeApiTest` + `GlobalExceptionHandlerTest`; sau đó `.\mvnw.cmd verify` trên Java 17/PowerShell thường có Docker. Không failure/error/skip; line coverage toàn bộ ít nhất 80%, practice ít nhất 90%; `git diff --check`, UTF-8 không BOM, không TODO.
- **Bàn giao:** đánh dấu task sau gate, một commit Conventional Commits cho T-02, chỉ stage các file đã duyệt. Chưa thực hiện push/PR ở bước Shadow Plan.
- **Rủi ro:** gán sai đáp án khi trộn phương án; thay snapshot khi tiếp tục; khác nhau giữa số đơn vị có audio và yêu cầu "mọi đơn vị"; phạm vi file trong tasks.md chưa đủ endpoint/test.

### Q-T02-01 - Xin bổ sung phạm vi file cho endpoint POST

Cột File của T-02 mới có `PronStageSessionService.java`. Để hoàn tất endpoint và gate, đề xuất leader duyệt thêm:

- `src/main/java/com/cnhsk/learning/pronunciation/web/PronunciationController.java`: thêm POST `/api/pronunciation/stages/{stageIndex}/sessions` và mô tả OpenAPI tương ứng.
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`: chỉ thêm một matcher POST cho path trên, role `LEARNER`, trước `/api/admin/**`; giữ cơ chế CSRF hiện có.
- `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`: chỉ thêm `STAGE_NOT_FOUND`, `STAGE_LOCKED`, `STAGE_HAS_NO_UNITS`, cùng một vị trí trước `INTERNAL_ERROR`; chưa thêm `STAGE_INCOMPLETE` của T-03.
- `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`: chỉ thêm ba tên mã tương ứng đúng thứ tự, giữ nguyên `containsExactly` và các assertion khác.
- `src/test/java/com/cnhsk/learning/pronunciation/PronStageSessionServiceTest.java`: unit test mới.
- `src/test/java/com/cnhsk/learning/pronunciation/PronStageSessionIntegrationTest.java`: integration test mới, kèm kiểm contract POST và các luồng trả lời qua bộ máy.
- `specs/005-pronunciation-stages/tasks.md`: cập nhật phạm vi sau khi duyệt, trạng thái sau gate.
- `specs/005-pronunciation-stages/questions.md`: ghi quyết định và kết quả.

Không đề xuất sửa `PracticeApi`, test bốn phương thức, bộ máy practice, schema, dependency, cấu hình test hoặc frontend. Nếu quyết định Q-T02-02 làm đổi nghĩa hợp đồng, spec/OpenAPI phải được cập nhật và leader duyệt trước khi implement.

**Leader chốt ngày 2026-10-30:** duyệt toàn bộ phạm vi Shadow Plan. `SecurityConfig` chỉ thêm đúng một matcher POST cho `LEARNER`; `ErrorCode` thêm đúng ba mã ngay trước `INTERNAL_ERROR`; test danh sách mã thêm cùng thứ tự và giữ `containsExactly`. Chưa thêm `STAGE_INCOMPLETE` của T-03. Nếu cần file dùng chung khác phải dừng và báo leader.

**Trạng thái:** đã chốt, không còn câu hỏi mở về phạm vi.

### Q-T02-02 - Tầng có cả đơn vị có audio và thiếu audio

- FR-002/T-01 chỉ đếm đơn vị có `audioUrl` không rỗng; không có đơn vị như vậy thì không playable.
- FR-008 nói lượt mới gồm "mọi đơn vị của tầng" và `hasAudio=true` cho mọi câu.
- Chưa chốt tập câu khi trong `units` có cả đơn vị có audio và đơn vị thiếu audio. Phát cả hai sẽ làm số câu khác `unitCount` trong danh sách.

**Leader chốt ngày 2026-10-30:** "đơn vị" trong FR-008 chỉ là đơn vị có `audioUrl`, cùng định nghĩa `unitCount` ở FR-002. Chỉ phát đơn vị có audio; tất cả thiếu audio trả 422 `STAGE_HAS_NO_UNITS`. Link có nhưng tải lỗi vẫn theo E3 (`audioFailed`). Được thêm đúng một dòng làm rõ vào FR-008 của `spec.md`, không đổi nội dung khác; OpenAPI không thay đổi.

**Trạng thái:** đã chốt, không còn câu hỏi mở về tập câu.

### Điều kiện T-02 leader chốt ngày 2026-10-30

- Kiểm theo thứ tự FR-006: 404 → 403 kèm `requiredStageIndex` → 422, sau đó mới xét FR-007 để trả 200 cùng lượt hoặc tạo mới 201.
- Response không lộ đáp án server chưa chấm (AT-06); khôi phục giữ `answered` (AT-05).
- Test bắt buộc có tầng trộn đơn vị có và thiếu audio, số câu phát ra bằng `unitCount`.
- Không tạo migration hoặc sửa test của spec khác; ngoài `GlobalExceptionHandlerTest` chỉ thêm ba mã đã duyệt, không nới assertion.
- Gate chọn lọc T-02 + `ModuleBoundaryTest` + `PracticeApiTest` + `GlobalExceptionHandlerTest`, rồi Maven Wrapper `verify` trên PowerShell thường có Docker; 0 failure/error/skip, coverage toàn bộ ít nhất 80%, practice ít nhất 90%, diff sạch.
- Một commit T-02, stage file cụ thể. Chỉ đạo mới cho phép push nhánh spec sau khi gate xanh; thay thế yêu cầu giữ local trước đó cho việc bàn giao nhánh. Không merge/rebase/pull main, force-push, push main hoặc merge PR.

## Kết quả T-02 ngày 2026-10-30

- Triển khai `POST /api/pronunciation/stages/{stageIndex}/sessions`: kiểm 404 → 403 (`requiredStageIndex`) → 422, sau đó trả 200 lượt đang làm hoặc 201 lượt mới.
- Tái sử dụng kết quả luật mở tầng của query T-01; gọi `PracticeService` trong cùng module learning. Giữ nguyên facade và test bốn phương thức.
- Chỉ phát đơn vị có audio, đúng `unitCount`; trộn đơn vị và phương án, gán lại khóa đáp án theo vị trí mới. TTL 7 ngày và luật IMMEDIATE/no-skip do bộ máy hiện có quản lý.
- Tiếp tục giữ snapshot và `answered`; test trả lời thật bốn câu rồi nhập lại nội dung vẫn khôi phục đúng cùng lượt. Câu chưa chấm không lộ đáp án server.
- 44 test mới: 18 unit và 26 integration, bao gồm tầng trộn audio, thứ tự lỗi trước resume, hết hạn, phân quyền/CSRF, quota, skip/audioFailed, chấm đúng/sai sau khi trộn và runtime OpenAPI.
- Test HTTP có PostgreSQL 18/Redis 7/Mailpit riêng trong Testcontainers để các transaction trả lời được commit thật, không sửa dữ liệu hoặc phụ thuộc thứ tự test spec khác.
- Gate chọn lọc 81 test, 0 failure/error/skip. Maven Wrapper `verify` trên Java 17/PowerShell thường có Docker: 1413 test trong 89 suite, 0 failure/error/skip, đối chiếu Surefire XML.
- JaCoCo line: toàn bộ 4445/4611 (96,40%); practice 743/743 (100%); pronunciation 89/90 (98,89%).
- Ba file dùng chung chỉ sửa đúng phạm vi: một matcher POST trong `SecurityConfig`, ba mã trong `ErrorCode` và đúng ba tên mã trong `GlobalExceptionHandlerTest`; giữ `containsExactly`.
- Không sửa migration, dependency, cấu hình test, test spec khác, bộ máy practice, facade hoặc frontend. Không có câu hỏi còn mở của T-02.

### Self-check Constitution T-02

- **HR-02, HR-08, BUS-06:** không đọc/commit bí mật, dữ liệu có bản quyền hoặc PII; test dùng dữ liệu tự tạo, không in request chứa thông tin đăng ký khi lỗi.
- **HR-07, HR-09:** validate số tầng, lỗi chuẩn có `request_id`, không stack trace; không ghép input vào SQL; lỗi riêng qua handler hiện có.
- **AC-01, AC-03, BUS-01:** contract đã duyệt; chỉ thêm dòng làm rõ spec theo quyết định leader trước khi code; userId lấy từ `AuthApi`; kiểm lượt đúng người dùng/tầng; practice cùng module learning.
- **AC-05, BUS-02, AC-08:** không đổi schema/migration; tạo snapshot/lượt trong transaction; dùng Clock/Instant và TTL của bộ máy, không thêm job.
- **BUS-09, BUS-11:** đáp án do server giữ và chấm; không nhận điểm client; luật mở tầng dùng ngưỡng đã có của T-01.
- **ES-01, ES-03, ES-05:** giữ stack/naming; integration test endpoint; full gate và coverage đạt yêu cầu.
- **ES-06, ES-07:** một commit Conventional Commits cho T-02; stage đúng file; nhánh `spec/*`; chỉ push nhánh spec theo duyệt, không tự merge.

## T-03 - Shadow Plan đã duyệt ngày 2026-10-30

### Kiểm tra đầu task

- Đã đọc lại AGENTS, constitution, spec 001 §0, spec/OpenAPI/tasks 005. Không có `plan.md` của spec 005.
- Đã `git fetch origin`; main vẫn ở `7391f87`, có V4 và phần nền spec 002/003. Không chờ T-13 của spec 003 theo chỉ đạo leader đã chốt.
- Nhánh `spec/005-pronunciation-stages` chưa merge; có hai commit T-01 `8bf7b87` và T-02 `75324d0`. Giữ nguyên nhánh; không tạo part2, merge/rebase/pull main.
- Ở bước đầu T-03, người dùng yêu cầu không push; quyết định duyệt T-03 bên dưới đã thay thế bằng việc cho phép push nhánh spec sau gate xanh.
- Đã kiểm hook `PracticeSourceHandler`, đường finish/transaction của `PracticeService`, entity/repository Attempt, PronStage và schema `user_progress` hiện có. Chưa sửa code/test của T-03.

### Shadow Plan đề xuất

- **Task:** FR-010…FR-016 và §11; AT-08…AT-15, AT-18…AT-20; bổ sung regression AT-16/AT-17 cho tiến độ tạo từ handler.
- **Đọc:** các tài liệu trên; spec 003 §0.8/§0.9, `PracticeSourceHandler`, `PracticeService.finish`, `Attempt`/repository, `PronStage`/repository, `UserProgress`, SQL V1/V3/V4; `ErrorCode`, danh sách mã trong `GlobalExceptionHandlerTest`, test finish/boundary/facade và hạ tầng Testcontainers liên quan.
- **Tạo theo cột File:** `src/main/java/com/cnhsk/learning/pronunciation/PronStageHandler.java`; đăng ký handler cho `PRON_STAGE` bằng cơ chế bean hiện có.
- **FR-010:** đối chiếu số câu đã có kết quả với snapshot `Attempt.servedItems.items`, không dùng số đơn vị hiện tại sau khi nhập lại nội dung. `FinishContext.items` chỉ có câu đã ghi nên cần đọc snapshot qua repository cùng module learning; không sửa bộ máy hoặc hợp đồng hook. Còn câu chưa trả lời thì ném 422 `STAGE_INCOMPLETE` kèm `remaining`, rollback finish.
- **FR-011/§11:** tính accuracy từ kết quả server có `correct != null`, làm tròn hai chữ số; mẫu số 0 thì không ghi tiến độ. Hook `isSuspicious` kiểm `audioFailed * 100 > totalServed * 30`, kết hợp cờ sẵn có của bộ máy.
- **FR-012…FR-015:** SQL native tham số hóa trong transaction finish hiện có; upsert điểm tốt nhất bằng `GREATEST`, state theo ngưỡng dữ liệu, giữ `unlocked_at` khi update theo SQL của spec; thời gian lấy từ `FinishContext.submittedAt` do Clock server cấp. Chèn đúng tầng `k+1` bằng `ON CONFLICT DO NOTHING`, lấy số dòng chèn để tính `unlockedNextStage`. `completedAllStages` theo tầng có `stage_index` lớn nhất thực tế trong dữ liệu, không tạo tầng mới.
- **FR-016:** trả đủ mười trường `extra` theo contract và quyết định Q-T03-02 đã chốt bên dưới.
- **Test:** đủ các AT của task; giữ điểm cao nhất và thời gian mở; không khóa lại; không mở quá một tầng; mẫu số loại audioFailed, noScore; ngưỡng suspicious 30% và trên 30%; rollback sau khi SQL tiến độ đã chạy; không lẫn dữ liệu người dùng. AT-13 bắt buộc hai luồng thật, hai session khác nhau của cùng người/tầng, finish đồng thời trên PostgreSQL/Testcontainers; assert đúng một dòng tầng kế, đúng một `unlockedNextStage=true`, điểm tốt nhất không bị ghi đè thấp hơn. Fixture tự tạo, không dùng dữ liệu demo hoặc Neon. Ghi nhận p95 finish cục bộ cho NFR-P02.
- **Gate:** hai test T-03 + `ModuleBoundaryTest`, `PracticeApiTest`, `GlobalExceptionHandlerTest`, sau đó `.\mvnw.cmd verify` trên PowerShell thường có Docker; 0 failure/error/skip, coverage toàn bộ ít nhất 80%, practice ít nhất 90%, `git diff --check`, UTF-8 không BOM/TODO.
- **Bàn giao:** cập nhật task sau gate, một commit Conventional Commits cho T-03, stage file cụ thể; push nhánh spec theo quyết định duyệt T-03, không merge PR.
- **Rủi ro chính:** mất cập nhật khi hai lượt cùng kết thúc; tính remaining sai vì context chỉ chứa câu đã ghi; ghi tiến độ ngoài transaction finish; kiểm đúng null/0 của extra theo quyết định đã chốt.

### Q-T03-01 - Xin bổ sung phạm vi file cho handler và test

Cột File của T-03 hiện chỉ có `PronStageHandler.java`. Đề xuất leader duyệt thêm:

- `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`: chỉ thêm `STAGE_INCOMPLETE` ngay trước `INTERNAL_ERROR`.
- `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`: chỉ thêm một tên mã đúng vị trí trong `containsExactly`, giữ các assertion khác.
- `src/test/java/com/cnhsk/learning/pronunciation/PronStageHandlerTest.java`: unit test mới.
- `src/test/java/com/cnhsk/learning/pronunciation/PronStageHandlerIntegrationTest.java`: integration, rollback và hai luồng thật trên PostgreSQL/Testcontainers.
- `specs/005-pronunciation-stages/tasks.md`: cập nhật phạm vi sau khi duyệt và trạng thái sau gate.
- `specs/005-pronunciation-stages/questions.md`: ghi quyết định và kết quả.

Không đề xuất sửa `SecurityConfig`, controller, facade, `PracticeService`, `PracticeSourceHandler`, test spec khác, entity, dependency, migration hoặc cấu hình test. Không có endpoint mới; contract nguồn đã duyệt giữ nguyên nếu quyết định Q-T03-02 không đổi cấu trúc.

**Leader chốt ngày 2026-10-30:** duyệt phạm vi trên. Chỉ thêm `STAGE_INCOMPLETE` ngay trước `INTERNAL_ERROR`, thêm tên mã cùng vị trí trong `GlobalExceptionHandlerTest`, giữ `containsExactly`; duyệt hai test mới và tài liệu spec 005. Nếu cần file dùng chung khác phải dừng và báo leader.

**Trạng thái:** đã chốt, không còn câu hỏi mở về phạm vi.

### Q-T03-02 - Giá trị extra ở trường hợp đạt/noScore và tầng kế

FR-016 chỉ quy định `needed` khi chưa đạt; FR-011 quy định accuracy null và không ghi tiến độ khi counted=0. Chưa xác định đủ giá trị của `needed`, `nextStageIndex` và các cờ khi không có điểm. OpenAPI cho phép null ở accuracy, bestAccuracy, nextStageIndex, needed.

**Leader chốt ngày 2026-10-30:**

- Lượt có điểm: `passed` theo accuracy của lượt hiện tại; chưa đạt thì `needed=max(0, passPercent-accuracy)`, đạt thì `needed=0.00`.
- `nextStageIndex=k+1` nếu tầng liền sau có trong dữ liệu, kể cả lượt chưa đạt; không có tầng đó thì null.
- Lượt không có điểm: `accuracy=null`, `bestAccuracy` đọc giá trị cũ hoặc null; `needed=null`; `passed=false`, `unlockedNextStage=false`, `completedAllStages=false`, `noScore=true`; `nextStageIndex` vẫn theo quy tắc tồn tại ở trên. Không thay đổi bất kỳ dòng tiến độ nào.

`passed` và `completedAllStages` tính theo accuracy của lượt hiện tại, không theo bestAccuracy; làm lại bị trượt vẫn giữ tầng `COMPLETED` nếu điểm tốt nhất còn đạt ngưỡng. Được thêm đúng một dòng làm rõ FR-016 trong `spec.md`, không đổi nội dung khác.

**Trạng thái:** đã chốt, không còn câu hỏi mở về extra.

### Điều kiện T-03 leader chốt ngày 2026-10-30

- `remaining` tính từ câu chưa có `attempt_items`; `audioFailed` vẫn là đã trả lời. Lỗi 422 kèm `remaining` rollback finish, giữ `IN_PROGRESS`, không có tiến độ mới (AT-08).
- Accuracy dùng `BigDecimal`, scale 2, `HALF_UP`, so sánh `compareTo`; đúng mốc passPercent là đạt.
- Upsert tiến độ và chèn tầng kế bằng SQL `ON CONFLICT` trong cùng transaction finish; không đọc rồi ghi. `percent=GREATEST(cũ,mới)`, tối đa một tầng kế, hai lượt song song chỉ tạo một dòng tầng kế.
- Lỗi ghi tiến độ rollback toàn bộ finish (AT-19). Cờ audio qua `isSuspicious`, OR với cờ chung sẵn có của bộ máy (AT-20).
- Đủ AT-08…AT-15, AT-18…AT-20, thêm noScore và tầng cuối. Không sửa test spec khác, không tạo migration.
- Gate chọn lọc T-03 + `ModuleBoundaryTest` + `PracticeApiTest` + `GlobalExceptionHandlerTest`, rồi Maven Wrapper `verify` trên PowerShell thường có Docker; 0 failure/error/skip, coverage toàn bộ ít nhất 80%, practice ít nhất 90%, diff sạch.
- Chỉ đạo mới trong lần duyệt này **cho phép push nhánh spec sau gate**, thay thế yêu cầu giữ local ở đầu T-03. Một task một commit, stage từng file; không merge/rebase/pull main, force-push, push main hoặc merge PR.

## Kết quả T-03 ngày 2026-10-30

- Đăng ký `PronStageHandler` cho nguồn `PRON_STAGE`, dùng hook/transaction finish hiện có. Không sửa bộ máy, facade, controller hoặc test spec khác.
- `remaining` theo snapshot đã phát và số câu đã có bằng chứng, kể cả audioFailed; chưa đủ ném 422 và rollback finish/study_events, giữ các câu đã trả lời.
- Accuracy dùng BigDecimal scale 2/HALF_UP; đúng ngưỡng cấu hình là đạt. Các cờ passed/completedAllStages chỉ theo lượt hiện tại; noScore không ghi bất kỳ dòng tiến độ nào.
- Upsert `GREATEST` và mở tầng kế dùng SQL ON CONFLICT trong transaction bắt buộc hiện có. Chỉ đọc lại percent sau upsert, khi transaction còn giữ khóa dòng; giữ nguyên thời gian mở và chỉ chèn tối đa tầng k+1.
- `isSuspicious` dùng tỷ lệ audioFailed trên tổng câu đã phát, ngưỡng nghiêm ngặt >30%; giữ OR với cờ chung của bộ máy.
- 50 test mới: 24 unit + 26 integration. AT-08…AT-15, AT-18…AT-20, noScore, tầng cuối, ngưỡng chính xác/làm tròn, timestamp, người dùng và dữ liệu không liên quan, regression AT-16/17 đều xanh.
- AT-13: hai session khác nhau, hai luồng HTTP thật; barrier tại hook bảo đảm hai transaction cùng chạy trước SQL tiến độ; đúng một flag mở mới, một dòng tầng kế, bestAccuracy cuối 100.00. AT-19 gây lỗi sau khi hai dòng tiến độ đã ghi trong transaction, xác nhận rollback cả finish/tiến độ/event rồi retry thành công.
- Gate chọn lọc T-03 + ModuleBoundaryTest/PracticeApiTest/GlobalExceptionHandlerTest: 87 test xanh, 0 failure/error/skip. Full Maven Wrapper verify trên Java 17/PowerShell thường có Docker: 1463 test trong 91 suite, 0 failure/error/skip, đối chiếu Surefire XML.
- JaCoCo line: toàn bộ 4498/4664 (96,44%); practice 743/743 (100%); pronunciation 141/142 (99,30%).
- NFR-P02: p95 finish cục bộ 18,5757 ms trên 20 mẫu sau warm-up; mỗi mẫu có chèn mới tầng kế, dưới 300 ms. Đây là phép đo trên Testcontainers local.
- Hai file dùng chung chỉ thêm STAGE_INCOMPLETE đúng vị trí trong enum và containsExactly. Spec thêm đúng một dòng FR-016; contract OpenAPI giữ nguyên. Không tạo migration, thêm dependency, sửa cấu hình test hoặc frontend; không có câu hỏi T-03 còn mở.

### Self-check Constitution T-03

- **HR-02, HR-08, BUS-06:** không đọc/commit bí mật, dữ liệu bản quyền hoặc PII; fixture tự tạo, MockMvc không in request đăng ký khi test lỗi.
- **HR-07, HR-09, BUS-01:** SQL tham số hóa; lỗi chuẩn có request_id; lượt không thuộc người gọi hoặc không tồn tại đều bị chặn trước hook, không ghi tiến độ người khác.
- **AC-01, AC-03:** giữ contract đã duyệt; một dòng làm rõ spec theo chốt trước khi code. Handler/repository đều trong learning; không đọc/ghi bảng module khác.
- **AC-05, AC-07, AC-08:** giữ nguyên migration/schema; accuracy/needed/best dùng BigDecimal và scale 2, không float/double; thời gian từ context/Clock server, không thêm job.
- **BUS-02, BUS-09, BUS-11:** hook yêu cầu transaction đang có; upsert nguyên tử, race và rollback đã kiểm thật; điểm từ kết quả chấm server; so ngưỡng cấu hình bằng compareTo.
- **ES-01, ES-03, ES-05:** stack/naming giữ nguyên; endpoint finish có integration test và coverage/gate đạt yêu cầu.
- **ES-06, ES-07:** một commit Conventional Commits T-03, stage file cụ thể; chỉ push nhánh spec theo duyệt, không merge/rebase/pull main hoặc tự merge PR.

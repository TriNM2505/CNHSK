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

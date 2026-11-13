# Câu hỏi và quyết định — Spec #020

## 2026-11-13 · T-02 · Q-T02-01 — ANSWERED

**Leader duyệt:** Token là `base64url(payload) + "." + base64url(hmac)`, không padding; HMAC-SHA256 ký trên chuỗi payload đã encode, dùng UTF-8 của secret từ cấu hình JWT hiện có. Payload đúng ba phần `userId.expEpochSec.REMINDER_UNSUBSCRIBE`, userId dương, hạn 30 ngày theo `Clock`. So chữ ký bằng `MessageDigest.isEqual` trước khi tin hoặc parse nội dung payload. Có `issue(userId)` để T-03 gửi mail.

## 2026-11-13 · T-02 · Q-T02-02 — ANSWERED

**Leader duyệt:** JSON hỏng, token sai kiểu, thiếu/rỗng/quá 512 ký tự, các nguồn token khác nhau, sai chữ ký/hạn/purpose/định dạng hoặc user không tồn tại đều trả 400 `INVALID_UNSUBSCRIBE_TOKEN`, message `Liên kết tắt nhắc không hợp lệ hoặc đã hết hạn.` Xử lý lỗi JSON bằng `@ExceptionHandler` riêng trong `ReminderUnsubscribeController`, không sửa handler chung và không log ERROR/stack trace. Gọi lại khi EMAIL đã tắt trả 204, không ghi audit; chỉ audit khi kênh thực sự thay đổi. Không log token, userId lấy từ token hoặc IP.

## 2026-11-13 · T-02 · Q-T02-03 — ANSWERED

**Leader duyệt contract trước code:** Chỉ bổ sung cho POST unsubscribe; không đổi schema hay endpoint đã khoá khác. Base `dfaa8ee` đã có query `token`, JSON, form `List-Unsubscribe`, response 400 và 429 nên giữ nguyên các mục này. Diff bổ sung form `token` (string, tối đa 512), ví dụ `List-Unsubscribe=One-Click` và mô tả miễn CSRF đúng POST, token các nguồn phải giống nhau, message 400 thống nhất, audit idempotent.

**Nhánh và gate:** Leader cho phép tạo `spec/020-t02` trực tiếp từ `dfaa8ee` (T-01), không merge/rebase main trừ khi leader yêu cầu; Draft PR base main. Allowlist ban đầu 16 file, mở rộng thành 18 file ở Q-T02-04. Commit không ghi mã FR. Báo targeted/full verify, F/E/S, JaCoCo, diff check, UTF-8 không BOM và self-check. Sửa audit settings thêm `configured` trước/sau, test lần lưu đầu có `before.configured=false`.

## 2026-11-13 · T-02 · Q-T02-04 — ANSWERED

**Phát hiện trong full verify:** `AuthContractTest.AC01_runtimeDocumentedPathsAndStatusesAreDeclaredBySourceContract` scan toàn bộ controller `auth`, nhưng phần ghép spec 020 mới thêm `/api/me/reminder` ở T-01. Endpoint unsubscribe xuất hiện đúng trong runtime và contract 020, song map so sánh của test chưa chứa path này nên thất bại.

**Leader duyệt mở rộng allowlist 16 → 18 file:** G0 thiếu hai test kiểm kê. Chỉ cập nhật hai vị trí sau, giữ nguyên kiểm tra endpoint cũ:

- File thứ 17 `src/test/java/com/cnhsk/contract/AuthContractTest.java`: lấy `PathItem` của `/api/public/reminder/unsubscribe` từ contract 020, assert tồn tại và chưa trùng contract 001, rồi ghép vào map so sánh. Giữ nguyên kiểm tra path/status; không ẩn endpoint hoặc sửa contract 001.
- File thứ 18 `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`: thêm `INVALID_UNSUBSCRIBE_TOKEN` trước `INTERNAL_ERROR` trong danh sách enum kỳ vọng. Full verify phát hiện snapshot này chưa cập nhật. Không sửa production `GlobalExceptionHandler.java`.

**Gate trước khi bổ sung test:** Targeted 134 test, F/E/S 0/0/0, Maven BUILD SUCCESS. Full `./mvnw verify` chạy 4.524 test, F/E/S 2/0/0, exit 1 (16:49); chỉ hai test nêu trên thất bại. Xuất lại `jacoco:report@report` từ lượt full test: LINE 96,7577% (12.295/12.707), report exit 0. `git diff --check`, `git diff --cached --check`, UTF-8 không BOM và đúng allowlist 16 file đều PASS. Không bỏ test, không commit/mở Draft PR khi gate chưa xanh.

**Thứ tự merge:** Nếu Đạt (018/T-03) merge `REFERENCE_NOT_FOUND` trước, chỉ rebase main khi leader báo; khi đó đặt `INVALID_UNSUBSCRIBE_TOKEN` sau `REFERENCE_NOT_FOUND` trong enum và test kiểm kê. Hiện giữ base `dfaa8ee` theo chỉ đạo, không tự merge/rebase main. Patch tạm được xoá sau khi áp, không commit.

**Gate sau khi mở rộng — PASS:** Targeted (gồm hai test kiểm kê) 160 test, F/E/S 0/0/0, exit 0. `./mvnw -B verify`: 4.524 test, F/E/S 0/0/0, exit 0 (15:48), BUILD SUCCESS; JaCoCo LINE 96,7577% (12.295/12.707), coverage check PASS. `git diff --check` và `git diff --cached --check` sạch; đủ 18/18 file trong allowlist, UTF-8 hợp lệ và không BOM. Patch tạm đã xoá, production `GlobalExceptionHandler.java` giữ nguyên.

**Self-check cuối — PASS:** HR: secret đọc từ cấu hình hiện có; chữ ký so bằng `MessageDigest.isEqual` trước parse; lỗi thống nhất, không log token/IP/userId; JSON hỏng không có ERROR/stack trace; chỉ miễn CSRF đúng POST. AC: không thêm dependency, biến môi trường, migration hoặc schema; module boundary test pass; OpenAPI duyệt trước code. BUS: transaction và khoá dòng user giữ giờ nhắc, chỉ bỏ EMAIL, audit một lần kể cả gọi đồng thời; settings audit có `configured=false` trước lần lưu đầu. ES: mọi test chạy đủ, không skip, coverage trên 80%, naming/import và diff check sạch, không TODO/FIXME trong phần thay đổi. Một conventional commit, Draft PR base main; không tự rebase/merge main.

## 2026-11-13 · T-01 · Q-T01-01 — ANSWERED

**Câu hỏi:** Chú thích `reminder_time NULL = tắt` trong V1 và `database.md` khác với FR-003/AT-01. Trường hợp NULL phải được hiểu thế nào?

**Leader chốt:** Làm theo FR-003/AT-01: NULL = chưa cài; GET trả `20:00`, `web=true`, `email=false`, `configured=false`, không ghi DB. Tắt nhắc bằng `reminder_channels=''`; sau khi PUT tắt cả hai kênh, giờ đã lưu vẫn có giá trị và GET trả `configured=true`, `web=false`, `email=false`. Giữ nguyên V1 đã chạy; chỉ sửa chú thích `reminder_time` trong `docs/reference/database.md`.

## 2026-11-13 · T-01 · Q-T01-02 — ANSWERED

**Câu hỏi:** PUT `20:00` ghi xuống PostgreSQL thành `13:00`; chỉ đọc lại qua entity có thể che lỗi vì hai lần chuyển múi giờ triệt tiêu nhau. Cách giữ đúng giờ nhắc Việt Nam trong cột `TIME` là gì?

**Leader chốt:** `hibernate.jdbc.time_zone=UTC` (`application.yaml`) làm ánh xạ `LocalTime` mặc định chuyển giờ từ JVM +07 sang UTC. Chỉ thêm `@JdbcTypeCode(SqlTypes.LOCAL_TIME)` trên `User.reminderTime` để bind/đọc `LocalTime` trực tiếp; giữ nguyên các field khác, cấu hình UTC, cột SQL và V1. Test JDBC kiểm `reminder_time::text` bằng `20:00:00`, cùng các biên `00:00:00`/`23:00:00`, và GET đọc lại đúng `HH:00`.

**Bàn giao T-03:** `reminderTargets(hour)` so sánh với cột `TIME` phải dùng cùng cách bind `LocalTime` trực tiếp, không tự chuyển giờ sang UTC. Giờ nhắc là giờ Việt Nam, không phải một thời điểm `Instant`.

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Đã học trong ngày thì có nhắc không (UC-055 A2) | Vẫn nhắc nếu còn thẻ đến hạn | ✅ Chốt |
| Q2 | Bước giờ nhắc, tần suất job | Chỉ giờ tròn, job mỗi giờ | ✅ Chốt |
| Q3 | Tắt nhắc từ email | Một lần bấm, token ký; trang có 1 nút (không tắt khi chỉ mở link — tránh trình quét mail) | ✅ Chốt |
| Q4 | Nguồn "thẻ đến hạn" | 011 `ReviewSessionService.summary.dueCount` | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q5 | Giới hạn mỗi lượt | 500 người/giờ, vượt thì bỏ qua lượt đó | ✅ Leader duyệt cùng spec 2026-11-13 |

## Dữ kiện đã kiểm trên main `2735ac8`
- `auth.users.reminder_time TIME`, `reminder_channels VARCHAR(50) DEFAULT 'WEB'`, `email_verified_at`, `banned_at`.
- `learning.study_events` có `REMINDER_SENT`, `channel`, `local_date`; `uq_reminder(user_id, local_date, channel) WHERE event_type='REMINDER_SENT'`.
- `PracticeService` ghi `study_events` STUDY/GAME theo ngày giờ VN khi nộp.
- `ReviewSessionService.summary(userId)` → `ReviewSummary(dueCount, nextDueAt)`.
- `MailService` (auth) có `sendVerificationEmail`, `sendPasswordResetEmail`, `send(...)` riêng tư.
- ArchUnit: `learning_chi_duoc_goi_auth_qua_api`, `auth_khong_duoc_biet_den_hai_module_kia`.
- Mẫu job: `PracticeMaintenance` `@Scheduled(cron=…, zone="Asia/Ho_Chi_Minh")`, test cron `-`.

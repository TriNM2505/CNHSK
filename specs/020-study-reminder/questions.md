# Câu hỏi và quyết định — Spec #020

## 2026-11-14 · T-05 · Q-T05-01 — ANSWERED

**Leader duyệt G0:** `show=true` khi kênh WEB bật, giờ hiện tại tại `Asia/Ho_Chi_Minh` lớn hơn hoặc bằng giờ nhắc và `dueCount > 0`. Người chưa cài đặt dùng mặc định FR-003 qua `AuthApi.reminderSettings`: `20:00`, web bật, email tắt, `configured=false`, không ghi DB. `dueCount` lấy qua public `ReviewSessionService.summary(userId)` của 011; không đọc `study_events`, không xét đã học hoặc đã nhận email trong ngày.

**Phạm vi được duyệt:** Allowlist tám file và kế hoạch G0 đã được leader duyệt; giữ base `b9230db` trên nhánh `spec/020-t05` trong khi PR #156 được leader verify. `SecurityConfig` chỉ thêm matcher đúng `GET /api/reminders/today` với `hasRole("LEARNER")`; nếu xung đột với 018/T-05 của Đạt, leader giải quyết lúc merge. Vào G1, chạy gate theo kế hoạch, một conventional commit và Draft PR; không tự rebase/merge main.

**Shadow plan G1 — leader đã duyệt và gửi lại đầy đủ:** Đọc contract 020, `AuthApi`/`ReminderSettings`, public summary của 011, security và mẫu test hiện có. Service lấy chủ thể từ `AuthApi.currentUser()`, trả record ba trường đặt trong service, giữ `dueCount` cả khi banner ẩn, format giờ `HH:mm`; toàn bộ thao tác chạy trong transaction chỉ đọc. Controller mỏng, operationId `getTodayReminder`, status 200/401/403 theo contract hiện có. Không đổi dependency, mã lỗi, schema, config hoặc frontend.

**Exact allowlist — tám file:**

- `src/main/java/com/cnhsk/learning/reminder/TodayReminderService.java`
- `src/main/java/com/cnhsk/learning/reminder/web/ReminderController.java`
- `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`
- `src/test/java/com/cnhsk/learning/reminder/TodayReminderServiceTest.java`
- `src/test/java/com/cnhsk/learning/reminder/TodayReminderIntegrationTest.java`
- `src/test/java/com/cnhsk/shared/config/TodayReminderSecurityConfigIntegrationTest.java`
- `specs/020-study-reminder/questions.md`
- `specs/020-study-reminder/tasks.md`

**Gate theo thứ tự:** `./mvnw -B "-Dtest=TodayReminder*Test,TodayReminderSecurityConfigIntegrationTest" test`; `./mvnw -B "-Dtest=AuthContractTest,MasteryContractTest,GlobalExceptionHandlerTest,ModuleBoundaryTest,SecurityConfigIntegrationTest,ReviewSession*Test,ReminderSettings*Test,StudyReminderJob*Test" test`; `./mvnw -B verify`. Dùng Java 17, Docker và `DEBUG=false`; báo tổng test/F/E/S/exit, JaCoCo LINE ≥ 80%, diff check/UTF-8 không BOM/không TODO hoặc FIXME và đúng tám file. Chỉ sau khi gate xanh mới ghi T-05 chờ leader review, không đánh ✅; một conventional commit, push nhánh và Draft PR.

**Gate cuối — PASS:** Chạy đúng thứ tự trên Java 17.0.9, Docker và `DEBUG=false`. Targeted: 41 test (11 unit, 14 PostgreSQL integration, 16 security), F/E/S 0/0/0, exit 0. Hồi quy: 135 test, F/E/S 0/0/0, exit 0. Full `./mvnw -B verify`: 4.645 test / 296 bộ, F/E/S 0/0/0, exit 0, BUILD SUCCESS (15:35); JaCoCo LINE 96,7717% (12.470/12.886), coverage check PASS. Test SQL bắt cả Hibernate và JDBC trong GET: chỉ SELECT, transaction chỉ đọc, không DML hoặc `study_events`; settings/summary thật, chủ thể từ phiên không bị query `userId` thay đổi, JSON đúng ba field, mặc định không ghi DB/audit, sự kiện học/nhắc trong ngày không đổi banner. Kiểm đầy đủ biên giờ Việt Nam, dueCount khi ẩn banner, cookie/Bearer, 401/403 và matcher đúng path/method; test T-01…T-04, contract, review và ArchUnit xanh nguyên trạng.

**Self-check cuối — PASS:**

- Global: PASS — không dependency mới, stack giữ nguyên; naming/package theo quy ước; learning chỉ gọi auth qua `AuthApi`, dùng public summary của 011, không truy vấn xuyên module.
- Business: PASS — không đổi mật khẩu, tiền, điểm hoặc chấm game; không thêm log chứa PII/token; so giờ qua `Clock` tại `Asia/Ho_Chi_Minh`; mặc định FR-003 và quyết định Q-T05-01 được giữ nguyên.
- Safety: PASS — đúng tám file đã duyệt, `SecurityConfig` chỉ thêm một matcher GET; không xoá file, không sửa schema/migration/config/hiến pháp, không đọc hoặc commit `.env`, không truy cập Neon; SQL dọn fixture chỉ chạy trong Testcontainers và có WHERE. Push nhánh/Draft PR đã được leader cho phép; không push main hoặc tự merge/rebase.
- HR/AC/ES: PASS — không secret trong phần thay đổi, lỗi dùng handler hiện có; OpenAPI đã duyệt trước triển khai, endpoint và response 200/401/403 khớp contract; integration test và module boundary pass, không bỏ test; diff check, UTF-8 không BOM và không TODO/FIXME trong sáu file Java đều PASS. Repo chưa có Java lint plugin; kiểm convention/import/diff và marker nguồn, không thêm công cụ ngoài scope. T-05 chỉ ghi chờ leader review; một conventional commit trên `b9230db` và Draft PR phụ thuộc T-04/#156.
## 2026-11-14 · T-06 · Shadow plan và phạm vi tích hợp

**Chỉ đạo:** Triển khai FE trên `spec/020-t06` từ `origin/main` mới nhất, base `45ceaebeeb065487e43fd49f55ee32ec8ed26280`. Đọc AGENTS/constitution, design token và shared/ui, spec/contract/tasks/questions 020, mẫu credits/library, shared API/CSRF/error, route/layout, SCR-007 và SCR-010. Không đổi BE, contract, dependency hoặc schema; một conventional commit, Draft PR, không push/merge main.

**Kế hoạch và file trong phạm vi:** Tạo `features/reminder/types.ts`, `screens.ts`, `test-fixtures.ts`, `api/reminder.ts`, `api/reminder.test.ts`, `pages/Scr009ReminderSettingsPage.tsx` và test, `pages/ReminderUnsubscribePage.tsx` và test, `components/ReminderBanner.tsx` và test, `routes.test.tsx`. Sửa `app/routeAccess.ts`, `app/screenRegistry.ts`, `features/account/pages/Scr007ProfilePage.tsx` chỉ thêm lối vào và `features/mastery/pages/Scr010LearningDashboardPage.tsx` chỉ gắn banner; ghi `tasks.md`/`questions.md`. Các đường dẫn FE ở dưới `cnhsk-web/src/`.

**Hành vi:** Form dùng API/shared UI và mặc định do server trả, đủ trạng thái tải/lỗi/rỗng/dữ liệu; Email chưa xác thực bị vô hiệu và lỗi 422 nằm cạnh công tắc. Huỷ khôi phục snapshot gần nhất, không PUT. Trang public chỉ POST unsubscribe sau thao tác bấm; không hiện hoặc log token, không hiển thị raw server error trên trang token. Banner lỗi API thì ẩn, đóng lưu ngày Việt Nam qua localStorage có try/catch; khi trang còn mở tới nửa đêm Việt Nam thì tải lại trạng thái từ server. T-05 chưa có trên base này: test dùng dữ liệu mẫu, production gọi đúng endpoint và ẩn khi chưa sẵn sàng.

**Q-T06-01 — ANSWERED · Leader duyệt bổ sung ba file:**

- `cnhsk-web/src/app/routes.tsx`: thêm nhánh route tiện ích public `/reminder/unsubscribe`, không gán SCR mới; route metadata ở `routeAccess.ts` phân biệt `utility` với `screen`.
- `cnhsk-web/src/shared/layout/AccountMenu.tsx`: thêm đúng mục "Nhắc lịch học" tới `/me/reminders`, chỉ hiện với LEARNER; không sửa `navigation.ts`.
- `cnhsk-web/src/shared/api/client.ts`: chỉ thêm `'/api/public/reminder/unsubscribe'` vào `CSRF_EXEMPT_PATHS`, giữ nguyên toàn bộ logic CSRF theo chỉ đạo mới nhất. PUT `/api/me/reminder` vẫn dùng cơ chế lấy/gửi CSRF hiện có.

**Nhánh và kiểm kê:** Giữ nguyên base `45ceaeb`, không rebase hoặc merge main vào nhánh; leader merge `--no-ff`. Phần route/menu của patch đề xuất đã áp dụng; phần client làm theo quyết định chỉ thêm path nêu trên. Đã xoá `target/020-t06-integration-proposal.patch` theo yêu cầu, không đưa artifact tạm vào commit. Leader cho phép chỉ bổ sung kỳ vọng trong test kiểm kê nếu cần; lượt cuối không cần sửa test kiểm kê cũ: `cnhsk-web/src/app/routes.test.tsx`, `cnhsk-web/src/shared/api/client.csrf.test.ts`, `cnhsk-web/src/shared/layout/Layouts.test.tsx` đều giữ nguyên.

**Gate cuối — PASS:** Targeted reminder: 54/54 test / 5 file, 0 fail/skip, exit 0. Sau đó chạy đúng thứ tự leader yêu cầu: toàn FE `npx vitest run` — 2.464/2.464 test / 171 file, 0 fail/skip, exit 0; `npm run -s lint` — 0 error, 1 warning có sẵn ở `shared/ui/LevelBadge.tsx`, exit 0; `npm run -s build` — TypeScript và Vite PASS, exit 0. Test client thật xác nhận mở link không có request, click mới đúng một POST không cần CSRF; PUT settings vẫn có CSRF. Các ca form mặc định/Lưu/Huỷ/422, unsubscribe thiếu token/204/400/429, banner đóng/ngày Việt Nam mới/storage lỗi/API lỗi, quyền route/menu, lối vào SCR-007 và banner trên SCR-010 đều xanh. Dữ liệu mẫu chỉ ở test, production gọi API thật. Diff check, UTF-8 không BOM, không TODO/FIXME, đúng phạm vi 21 file và patch tạm đã xoá đều PASS.

**Self-check cuối — PASS:**

- HR: PASS — không secret/token trong giao diện hoặc log, không HTML không an toàn; form dùng client chung và lỗi chuẩn; unsubscribe chỉ gửi sau thao tác bấm, không tự gửi khi mount hoặc retry tự động.
- AC/Global: PASS — React/TypeScript/Vite và shared/ui/token giữ nguyên; không dependency, BE, contract, config hoặc schema mới; không đổi inventory 79 màn, trang tiện ích không có SCR; naming và phạm vi import đúng feature.
- BUS: PASS — giờ chọn tròn 00:00–23:00, Email chưa xác thực bị vô hiệu và 422 đặt cạnh công tắc, Huỷ không PUT; banner theo kết quả server, đóng lưu ngày Việt Nam bằng storage có try/catch, không ghi DB; không đổi tiền/điểm/chấm game hoặc mật khẩu.
- ES/Safety: PASS — toàn bộ test chạy đủ, không skip và không sửa kỳ vọng cũ để che lỗi; lint 0 error, build pass; đúng 21 file đã duyệt, UTF-8 không BOM/diff/source marker sạch; không đọc/commit `.env` hoặc truy cập Neon. Chỉ xoá đúng patch tạm được leader yêu cầu; không rebase, force-push hoặc push/merge main. T-06 chỉ ghi chờ leader review; một conventional commit và Draft PR.

## 2026-11-14 · T-04 · Q-T04-01 — ANSWERED

**Leader duyệt:** Sau khi giữ chỗ, `AuthApi.sendStudyReminder` trả `SKIPPED` thì nhả đúng claim ID mình tạo, không thử lại, tăng `skippedIneligible`, không tính là đã gửi. Log tổng kết FR-013 gồm đúng các trường `hour`, `candidates`, `sent`, `skippedNoCards`, `duplicate`, `skippedIneligible`, `failed`; không email, display_name, token hoặc message/stack trace của exception.

## 2026-11-14 · T-04 · Q-T04-02 — ANSWERED

**Leader duyệt:** Thử lại tối đa hai lần sau lần gửi đầu tiên (tối đa ba lần gửi), cách nhau hai giây qua Sleeper được tiêm; test dùng mock, không ngủ thật hai giây. Sleeper bị interrupt thì nhả claim đang giữ, khôi phục interrupt flag, dừng lượt hiện tại và vẫn log tổng kết. Mỗi claim/nhả claim dùng transaction ngắn `REQUIRES_NEW`; không giữ transaction trong SMTP hoặc thời gian chờ. Clock chốt ngày/giờ Việt Nam ở đầu lượt; dueCount lấy qua public `ReviewSessionService.summary` của 011, auth chỉ qua `AuthApi`.

## 2026-11-14 · T-04 · Q-T04-03 — ANSWERED

**Giới hạn leader chấp nhận:** Process chết sau khi claim đã commit nhưng trước khi gửi có thể làm người đó mất nhắc ngày hôm đó. T-04 không thêm TTL hoặc recovery. Unique `uq_reminder` có sẵn chống gửi trùng theo user/ngày/kênh; gửi lỗi hết retry hoặc bị bỏ qua thì chỉ xoá đúng claim mình tạo.

**Phạm vi và cấu hình:** Allowlist G0 chín file, nhánh `spec/020-t04` từ đúng `8cca710`, không tự rebase/merge main. Production thêm `cnhsk.reminder.cron = 0 0 * * * *` và `batch-size = 500`, khớp fallback `@Value` T-03; test đặt cron `-` nhưng giữ bean để gọi trực tiếp. Khi 023 hoàn tất, batch-size sẽ chuyển sang `SettingsApi`; T-04 chưa thực hiện chuyển đổi này. Một conventional commit, Draft PR; tasks chỉ ghi T-04 chờ review sau khi gate xanh.

**Gate cuối — PASS:** Hai bộ test mới: 34 test, F/E/S 0/0/0, exit 0. Targeted/regression: 314 test, F/E/S 0/0/0, exit 0. `./mvnw -B verify`: 4.604 test, F/E/S 0/0/0, exit 0, BUILD SUCCESS (15:35); JaCoCo LINE 96,7669% (12.451/12.867), mọi coverage check đạt. PostgreSQL/Mailpit xác nhận summary thật, claim commit trước SMTP, hai instance chỉ gửi một mail, retry đúng hai giây qua mock, A hết retry không chặn B, SKIPPED/interrupt nhả claim, biên ngày Việt Nam, release theo ID không xoá claim thay thế hoặc dữ liệu khác. `PracticeMaintenanceIntegrationTest`, `ImportMaintenanceTest`, `ImportMaintenanceIntegrationTest`, test reminder T-01…T-03, review summary, contract và ArchUnit chạy nguyên trạng đều xanh.

**Self-check cuối — PASS:** Global: không dependency mới, Java 17/stack giữ nguyên, naming theo code hiện có; learning chỉ gọi auth qua `AuthApi`, dueCount qua public summary của 011, SQL production chỉ ở `learning.study_events`. Business: giữ chỗ trước gửi, transaction ngắn độc lập; không giữ transaction khi SMTP hoặc chờ retry; không đổi chấm điểm/tiền; không log email/display_name/token hay nội dung exception. Safety: đúng chín file đã duyệt, UTF-8 không BOM, diff check sạch, không TODO/FIXME trong code; không sửa schema/migration/ErrorCode/SecurityConfig/OpenAPI, không đọc/commit `.env` hoặc chạy SQL lên Neon; mọi DELETE có WHERE. Repo chưa có Java lint plugin; đã kiểm convention/diff/marker nguồn. Tasks chỉ ghi T-04 chờ review; một conventional commit và Draft PR, không tự merge/rebase main.

## 2026-11-14 · T-03 · Q-T03-01 — ANSWERED

**Leader duyệt G0:** Thêm `CnhskProperties.App.apiBaseUrl`, mặc định `http://localhost:8080`, kiểm URL tuyệt đối như `webBaseUrl`; đọc biến `API_BASE_URL` theo cơ chế cấu hình hiện có. `.env.example` chỉ thêm `API_BASE_URL=` để trống và chú thích, không có giá trị thật. Giá trị môi trường trống không ghi đè cấu hình; khi chưa cấu hình dùng mặc định local.

## 2026-11-14 · T-03 · Q-T03-02 — ANSWERED

**Leader bổ sung FR-008:** Người đang đình chỉ cũng bị loại vì không đăng nhập được để ôn. Cả `reminderTargets` và bước đọc lại DB ngay trước khi gửi phải kiểm `banned_at IS NULL AND (suspended_until IS NULL OR suspended_until <= :now)`; `:now` lấy từ `Clock`. Đình chỉ còn hạn → không là target và trả `SKIPPED` khi gửi; đình chỉ đã qua hoặc đúng thời điểm hết hạn → được phép nếu các điều kiện khác đạt. Không sửa `spec.md`.

## 2026-11-14 · T-03 · Q-T03-03 — ANSWERED

**Leader duyệt chữ ký nội bộ:** `ReminderSettings(LocalTime reminderTime, boolean web, boolean email, boolean emailVerified, boolean configured)`, `ReminderTarget(long userId)` và enum `ReminderSendResult { SENT, SKIPPED }`. `sendStudyReminder` trả enum này. Settings phải dùng chung hàm tính mặc định với `ReminderSettingsService`, không format `HH:mm` rồi parse ngược.

## 2026-11-14 · T-03 · Q-T03-04 — ANSWERED

**Đầu vào:** `dueCount == 0` → `SKIPPED`; `dueCount < 0` hoặc `userId <= 0` → `IllegalArgumentException`; `batch-size <= 0` làm ứng dụng không khởi động. `hour` phải là giờ tròn; bind `LocalTime` trực tiếp, không đổi múi giờ (Q-T01-02). Batch mặc định 500 qua `@Value`; vượt thì WARN đúng số còn lại, không log userId/email/display_name/token. Lỗi gửi mail nhắc truyền ra caller để T-04 thử lại; mail xác thực/reset giữ nguyên hành vi nuốt lỗi.

**Bảo đảm thử lại:** Gửi mail nhắc đồng bộ, không thêm transaction proxy quanh thao tác SMTP để lỗi tạm thời không đánh dấu transaction của caller là rollback-only. Test kiểm lỗi lần đầu truyền ra và lần gọi lại trong cùng transaction trả `SENT`.

**Nhánh và phạm vi:** Leader giao `spec/020-t03` từ đúng `0345479` (T-02), không tự merge/rebase main. G0 duyệt 14 file và thêm `tasks.md` để ghi T-03 chờ review, không đánh dấu hoàn thành. `AuthApi` dùng chung với 017/T-01 (`bannedUserIds`, `publicProfiles`); xử lý L2 theo chỉ đạo leader, giữ các method của cả hai task. Một conventional commit và Draft PR.

## 2026-11-14 · T-03 · Q-T03-05 — ANSWERED

**Leader duyệt mở rộng allowlist 15 → 16 file:** Bổ sung `src/main/java/com/cnhsk/auth/reminder/ReminderSettingsService.java` để tách logic mặc định thành hàm dùng chung cho response REST và `AuthApi.reminderSettings`. `readSettings` trả snapshot có `LocalTime`; `settingsOf` là nơi duy nhất tính mặc định `20:00`, web bật, email tắt, `configured=false`. Chỉ response REST format `HH:mm`, không parse chuỗi ngược lại. GET/PUT `/api/me/reminder` giữ nguyên hành vi, JSON và audit; test T-01/T-02 chạy nguyên trạng. Không thay đổi file nào ngoài allowlist 16 file đã duyệt.

**Gate cuối — PASS:** Targeted/regression 205 test, F/E/S 0/0/0, Maven exit 0. `./mvnw -B verify`: 4.570 test, F/E/S 0/0/0, exit 0, BUILD SUCCESS (15:32); JaCoCo LINE 96,7608% (12.367/12.781), mọi coverage check đạt. Test Mailpit xác nhận text + HTML, tiêu đề, hai link FE, hai header và token verify đúng user. Test PostgreSQL kiểm giá trị thô `20:00:00`, bind `LocalTime` trực tiếp, kênh EMAIL theo phần tử, batch/WARN, đình chỉ tương lai/đã qua/đúng thời điểm hết hạn và đọc lại DB dù entity đã cache. Test T-01/T-02 nguyên trạng, `AuthContractTest`, `GlobalExceptionHandlerTest`, `ModuleBoundaryTest` đều xanh.

**Self-check cuối — PASS:** Global: không thêm dependency, stack giữ nguyên, naming theo code hiện có, module boundary pass. Business: không thay đổi chấm điểm/tiền; email chỉ ở auth, không log email/display_name/token; giờ nhắc giữ `LocalTime`, đình chỉ so theo `Clock`. Safety: đúng allowlist 16 file đã duyệt, UTF-8 không BOM, diff check sạch, không TODO/FIXME trong các file code thay đổi; không đọc/commit `.env`, không chạy SQL lên Neon, không migration/schema, không sửa ErrorCode/SecurityConfig/OpenAPI. Repo chưa có Java lint plugin; kiểm tra diff, convention và marker nguồn, không thêm công cụ ngoài scope. Chỉ cập nhật T-03 thành chờ review; một conventional commit và Draft PR, không tự merge/rebase main.

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

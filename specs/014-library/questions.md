# Chốt — Spec 014

## 2026-11-09 — Leader xác nhận G3 sau kiểm phạm vi và launcher

- Leader cho G3 ngay sau xác nhận phạm vi/client và launcher, không đợi AT-23…28; Draft PR phải ghi sáu AT này “chưa kiểm”, không đánh ✅ T-15, không merge trước nghiệm thu.
- Đối chiếu nguyên git status --short: 14 file M + 6 file ?? = đúng 20 file allowlist. git diff --stat origin/main trước stage chỉ hiện 14 file vì không tính sáu file untracked: identifiers.ts, identifiers.test.ts, LexemeSaveActions.test.tsx, LibraryContractTest.java, LibraryApiIntegrationTest.java, LibraryAcceptanceServer.java. Sau add từng file/commit, diff origin/main sẽ hiện đủ 20 file; không có file thiếu khỏi triển khai.
- Q-T15-01 đã thực hiện: updateDeck(): Promise<DeckSummary>, apiRequest cũng dùng DeckSummary. dictionary.ts dùng identifierPath cho path và checkedIdentifiers cho response; notes.ts dùng identifierPath/checkBodyIdentifiers/checkedIdentifiers; translation.ts dùng checkedIdentifiers để kiểm segments.lexemeId, không có ID request cần kiểm. Không sửa thêm client sau các gate PASS nên không cần chạy lại FE.
- Làm rõ Fix 1: chỉ thêm System.setProperty("spring.devtools.restart.enabled","false") trước đọc stdin và --spring.devtools.livereload.enabled=false vào tham số launcher. TypeExcludeFilter cô lập source test đã thêm trong bước self-review trước Fix 1. spring-boot-devtools là dependency runtime/optional có sẵn trên origin/main; pom.xml không có diff, không thêm dependency. Giữ nguyên interface, backend/contract/assertion; còn một vòng fix dự phòng.
- Đính chính báo cáo READY trước đó: khi process còn chạy, output FileStream bị đệm nên lần đọc trước dừng không thấy dòng cuối. Sau khi stream đã đóng/flush, log acceptance-fixed xác nhận: profile duy nhất test; Tomcat started on port 8080 lúc 12:39:57.790; Started LibraryAcceptanceServer in 15.854 seconds; sau seed và kiểm StubClaudeClient có dòng chính xác “LIBRARY_ACCEPTANCE_READY bind=127.0.0.1 port=8080 profile=test provider=stub max_minutes=90”. Vì vậy startup Fix 1 đã đạt; kết luận trước “chưa READY” chưa đầy đủ, không phải lỗi startup còn lại.
- Không dùng lại Computer Use theo chỉ đạo mới. Các browser library riêng (Playwright/Puppeteer) không có trong dependency workspace; không tự cài hoặc dựng driver/công cụ mới. AT-23…28 trên trình duyệt vẫn CHƯA KIỂM; không dùng test tự động để tự đánh đạt. Tài nguyên nghiệm thu trước đó đã dọn, không giữ server/container trong lúc chờ. G3 tách commit annotation của hai controller riêng khỏi 18 file còn lại, push spec/014-t15 và Draft PR vào main; leader verify song song.

## 2026-11-09 — T-15 G1 tiếp tục theo quyết định sửa annotation của leader

- Nguyên nhân AT-29: @ApiResponses 401/403 ở cấp class NoteController/DeckController làm springdoc không tự sinh response 200 cho method chưa khai báo. YAML là chuẩn. Leader mở rộng allowlist 18 → 20 file, chỉ thêm responseCode=200/description vào đúng chín method, không content/schema, mapping, kiểu trả về, logic hoặc thay thứ tự annotation cũ. Không sửa YAML hoặc LibraryContractTest. Quyết định này không tính vào hai vòng fix.
- Diff controller để review riêng: NoteController thêm 200 ở list/get/update/trash/restore (5 method); update/restore thêm trong mảng @ApiResponses hiện có. DeckController thêm 200 ở list/get/update/due (4 method). Không thêm import. LibraryContractTest giữ SHA-256 E210C9B0C7CBBF20211155F5A5AA7B4F041755214AE0BA3ABFE53C18BF07D3C6.
- Allowlist đầy đủ 20 file:
  1. `cnhsk-web/src/features/library/api/identifiers.ts`
  2. `cnhsk-web/src/features/library/api/identifiers.test.ts`
  3. `cnhsk-web/src/features/library/api/dictionary.ts`
  4. `cnhsk-web/src/features/library/api/notes.ts`
  5. `cnhsk-web/src/features/library/api/flashcards.ts`
  6. `cnhsk-web/src/features/library/api/translation.ts`
  7. `cnhsk-web/src/features/library/api/client-integration.test.ts`
  8. `cnhsk-web/src/features/library/components/LexemeSaveActions.tsx`
  9. `cnhsk-web/src/features/library/components/LexemeSaveActions.test.tsx`
  10. `cnhsk-web/src/features/library/pages/Scr025DictionaryDetailPage.test.tsx`
  11. `cnhsk-web/src/features/library/pages/Scr026TranslationPage.test.tsx`
  12. `cnhsk-web/src/features/library/pages/Scr027PersonalNotesPage.tsx`
  13. `cnhsk-web/src/features/library/pages/Scr027PersonalNotesPage.test.tsx`
  14. `src/test/java/com/cnhsk/contract/LibraryContractTest.java`
  15. `src/test/java/com/cnhsk/learning/library/LibraryApiIntegrationTest.java`
  16. `src/test/java/com/cnhsk/learning/library/LibraryAcceptanceServer.java`
  17. `specs/014-library/questions.md`
  18. `specs/014-library/tasks.md`
  19. `src/main/java/com/cnhsk/learning/library/web/NoteController.java`
  20. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
- Q-T15-01: PATCH client trả Promise<DeckSummary>; grep mọi nơi dùng chỉ thấy production SCR-029 đọc name/description rồi GET lại chi tiết, không đọc cards từ PATCH. Không sửa SCR-029 ngoài allowlist. Q-T15-02: không có helper tương đương trong shared, giữ hai file identifiers mới; chỉ chấp nhận number nguyên dương an toàn, không ép kiểu/đổi contract ID thành chuỗi. Kiểm ID path/body trước fetch và các ID response thư viện trước dùng; giữ null của lexemeId hợp lệ.
- Q-T15-03: thông báo chính xác “Từ này đã có trong sổ tay”, giữ bản nháp; nút mở ghi chú có sẵn chỉ hiện khi details.noteId hợp lệ. Chỉ điều hướng khi người dùng bấm; URL chỉ noteId. SCR-027 tải ghi chú, có retry, abort và bỏ kết quả muộn khi đổi tài khoản/đổi URL; huỷ tải không kẹt loading.
- Q-T15-04: LibraryAcceptanceServer nằm src/test, tên không kết thúc Test/IT, chỉ chạy thủ công, bind 127.0.0.1:8080, profile test và StubClaudeClient, PostgreSQL/Redis/Mailpit container riêng. Không dùng .env; loại property sources môi trường/JVM ngoài phiên và cấu hình kết nối trực tiếp từ container. Mật khẩu fixture dùng một lần nhận qua stdin, chỉ lưu bcrypt cost 12; không ghi/log plaintext, không API key hoặc pepper thật. Fixture tự soạn, thời hạn server 90 phút, shutdown hook đóng context/container. Chưa bắt đầu lượt kiểm trình duyệt.
- LibraryApiIntegrationTest dùng TestRestTemplate qua cổng HTTP ngẫu nhiên thật, JDK request factory hỗ trợ PATCH, service/DB/billing thật. Sáu ca kiểm AT-23…28 cùng 401/403/CSRF, snapshot mastery chọn đúng cột (không SELECT * và không mảng PgArray), số attempt_items không đổi khi ôn. Mỗi ca chụp callCount trước, đếm số phản hồi đã nạp và assert delta bằng số nạp ở AfterEach; không mock bỏ qua consume/refund hoặc clear hàng đợi chung.

  | Lệnh (2026-11-09, UTC+7) | Bắt đầu | Kết thúc | Native exit / kết quả |
  |---|---|---|---|
  | mvnw.cmd -B test-compile sau annotation | 07:07:50 | 07:08:28 | 0 / PASS, Maven 36,164 s |
  | mvnw.cmd -B -Dtest=LibraryContractTest test | 07:08:43 | 07:09:21 | 0 / PASS 1/1, đủ 15 path/22 operation/status |
  | mvnw.cmd -B -Dtest=<6 lớp contract> test | 07:09:33 | 07:10:27 | 0 / PASS 41/41, 0 failure/error/skip |
  | mvnw.cmd -B test-compile sau LibraryApiIntegrationTest | 07:13:08 | 07:13:41 | 0 / PASS, Maven 30,881 s |
  | mvnw.cmd -B test-compile sau self-review HTTP client hỗ trợ PATCH | 07:14:03 | 07:14:35 | 0 / PASS, Maven 30,252 s |
  | mvnw.cmd -B test-compile sau LibraryAcceptanceServer | 07:17:15 | 07:17:47 | 0 / PASS, Maven 29,640 s |
  | mvnw.cmd -B -Dtest=<toàn bộ contract/library> test | 07:18:24 | 07:20:20 | 0 / PASS 437/437, gồm sáu ca HTTP mới, 0 failure/error/skip |
  | mvnw.cmd -B verify, timeout 60 phút, giữ máy thức | 07:20:56 | 07:39:08 | 0 / BUILD SUCCESS; 4.161/4.161, 0 failure/error/skip; Maven 18:09 |
  | JaCoCo report/check của verify; Import-Csv kiểm LINE | 07:39:46 | 07:39:46 | PASS; 11.678/12.078 = 96,6882%, ngưỡng 80% |
  | mvnw.cmd -B test-compile dependency:build-classpath sau self-review launcher | 07:39:51 | 07:40:38 | 0 / PASS, Maven 45,209 s; không chạy lại tests |
  | npm.cmd ci | 07:40:14 | 07:40:33 | 0 / PASS; không đổi dependency/lockfile |
  | npx.cmd vitest run src/features/library --maxWorkers=2 --minWorkers=2 | 07:41:03 | 07:41:30 | 0 / PASS 387/387, 13 file; 25,60 s |
  | npx.cmd vitest run --maxWorkers=2 --minWorkers=2 | 07:42:08 | 07:46:30 | 0 / PASS 2.387/2.387, 163 file; 260,87 s |
  | npm.cmd run lint | 07:46:59 | 07:47:07 | 0 / 0 error; đúng 1 warning LevelBadge có sẵn |
  | npm.cmd run build | 07:47:07 | 07:47:23 | 0 / TypeScript + Vite PASS, Vite 5,14 s |
  | mvnw.cmd -B test-compile sau Fix 1 launcher | 07:51:13 | 07:51:48 | 0 / PASS; Maven 32,754 s |

- Full verify chạy hết 264 lớp, native exit=0, timeout=false; đã khôi phục chế độ nguồn. JaCoCo report/check thực thi trong verify và đạt; giữ bản sao execution data target/t15-full-jacoco.exec. NFR_P04 QuestionImport lần này PASS, samples_ms=[26064,20886,16349,16218,17098], p95=26064 ms <30000 ms; không dùng ngoại lệ môi trường.
- Self-review launcher trước chạy tay: thêm TypeExcludeFilter chỉ cho phiên main để loại resource trong test-classes khỏi component scan, tránh vô tình dùng Clock/config fixture của test khác. Launcher không được test discovery chạy; đã compile lại và tạo classpath, sẽ kiểm trực tiếp bằng khởi động/dừng server. Hai controller, LibraryContractTest và LibraryApiIntegrationTest giữ nguyên hash của lượt full. Không sửa production hoặc assertion sau verify, không cần chạy lại full vì thay đổi chỉ ở utility không được suite chạy.
- Trình duyệt tích hợp báo browser access host unavailable khi chuẩn bị tab; đã kết thúc phần công cụ đó và kiểm phương án computer-use Windows có sẵn. Chrome có cửa sổ điều khiển được; không cài/tạo công cụ mới, không chụp/lưu ảnh. Chưa bắt đầu lượt nghiệm thu, ngân sách 90 phút chưa dùng.
- Không có lỗi testCompile/type-check hoặc assertion phải nới. Các gate tự động đều PASS, không phải chạy riêng test chập chờn, không dùng ngoại lệ. Hai controller và các test Java đã verify giữ nguyên hash; không chạy lại full chỉ vì thay đổi launcher test không được suite discovery chạy.
- Fix 1/2 chỉ trong LibraryAcceptanceServer: lần chạy tay 07:48:59–07:49:11 bị devtools RestartLauncher gọi lại main, stdin lần hai đã hết nên báo thiếu mật khẩu fixture. Native process exit=0 nhưng không có READY, không coi là startup PASS. Thêm System.setProperty("spring.devtools.restart.enabled","false") trước đọc stdin và tắt livereload trong tham số riêng của launcher. Không sửa production/contract/assertion. Compile Fix 1 PASS; còn một vòng fix.
- Lượt khởi động lại 12:39:33 chỉ có profile test và đi tới khởi tạo JPA nhưng chưa xuất LIBRARY_ACCEPTANCE_READY tại thời điểm dừng. Chưa kết luận launcher startup đã PASS. Browser tích hợp trước đó báo access host unavailable; công cụ Windows lần đầu báo “Computer Use app approval timed out”, lần thử lại tự dừng vì không xác định được URL browser hiện tại đủ chắc chắn để thực thi policy. Không tiếp tục UI, không dùng cách khác để vượt kiểm tra URL, không lưu ảnh hoặc dựng công cụ mới.
- AT-29: ĐẠT. AT-23/24/25/26/27/28 trên trình duyệt: CHƯA KIỂM do công cụ UI bị chặn; các test tự động liên quan đã PASS trong library 387 và HTTP thật 6/6. Không thay nghiệm thu trình duyệt bằng kết quả mock hoặc tự đánh đạt. Ngân sách lượt kiểm UI chưa bắt đầu vì chưa điều khiển được tab.
- Đã dọn tài nguyên lúc 12:41:27–12:41:30: chỉ Java PID 3076 và Vite PID 8144 được xác nhận thuộc T-15; native exit=-1 do dừng chủ động, không phải lỗi test/build. Bốn container của đúng launcher đã được dọn, còn 0; không còn listener trên 8080/5173. Cổng dự kiến/đã dùng: 127.0.0.1:8080 (backend chưa READY), 127.0.0.1:5173 (Vite và HTTP readiness 200). Lượt launcher trước cũng đã dọn hết bốn container.
- G2 bàn giao chưa hoàn thành nghiệm thu UI: giữ T-15 chưa ✅, tiến độ 14/15. Chưa stage/commit/push/PR. Chờ phiên browser mà công cụ có thể xác minh URL để tiếp tục; cần kiểm lại startup Fix 1 và AT-23…28. Journal đầy đủ lệnh, native exit và giờ tại TEMP/cnhsk-014-t15-gates.log; logs compile07/acceptance-fixed/acceptance-fixed-err/vite lưu tại TEMP, không chứa plaintext mật khẩu fixture.

## 2026-11-09 — T-15 G1: STOP tại AT-29 do lệch status OpenAPI

- Leader duyệt G0/allowlist tối đa 18 file và cho bắt đầu sau #135 merge. Fetch xác nhận origin/main = `9510643c36633f8b993e3f119bafe6b1772ffde7`; tạo nhánh `spec/014-t15`, worktree `CNHSK-014-t15` từ đúng base. Không stash/restore/stage thay đổi riêng của specs/003-vocab-topics/questions.md; không đọc hoặc đụng .env.
- Kiểm trước triển khai: chưa có helper ID tương đương trong cnhsk-web/src/shared. Grep toàn bộ nơi dùng updateDeck: production chỉ Scr029DeckDetailPage, nhận DeckSummary rồi đọc name/description và tải chi tiết riêng; không có nơi đọc .cards từ kết quả PATCH. Chưa sửa FE hoặc tạo helper vì ưu tiên AT-29 trước.
- Đã thêm LibraryContractTest trong allowlist, dùng Spring context thật, /v3/api-docs thật và PostgreSQL/Redis Testcontainers. Kiểm đúng tập 15 path, 22 operation, cả request mapping và tập status từng operation, không dùng contains/subset để bỏ qua status thiếu. Không gọi Claude, không ghi billing hoặc nạp response vào StubClaudeClient.

  | Lệnh (2026-11-09, UTC+7) | Bắt đầu | Kết thúc | Native exit / kết quả |
  |---|---|---|---|
  | Java 17.0.12, DEBUG=false, docker version | 06:50:43 | 06:50:43 | Java=0, Docker=0; Docker 29.9.2 |
  | mvnw.cmd -B test-compile sau LibraryContractTest | 06:51:28 | 06:52:06 | 0 / PASS; Maven 36,158 s |
  | mvnw.cmd -B -Dtest=LibraryContractTest -Dcnhsk.library.contract-audit-output=<TEMP>/cnhsk-014-t15-contract-audit.json test | 06:52:24 | 06:53:05 | 1 / FAIL; 1 test, 1 failure, 0 error/skip; Maven 37,929 s |

- AT-29: tập path và operation/mapping đều khớp; 13/22 operation có tập status khớp, 9/22 thiếu response 200 trên /v3/api-docs so với YAML:

  | Operation | YAML mong đợi | /v3/api-docs thực tế |
  |---|---|---|
  | GET /api/notes | 200, 401, 403, 422 | 401, 403, 422 |
  | GET /api/notes/trash | 200, 401, 403 | 401, 403 |
  | GET /api/notes/{id} | 200, 401, 403 | 401, 403 |
  | PUT /api/notes/{id} | 200, 401, 403, 404, 409, 422 | 401, 403, 404, 409, 422 |
  | POST /api/notes/{id}/restore | 200, 401, 403, 409, 422 | 401, 403, 409, 422 |
  | GET /api/flashcard-decks | 200, 401, 403 | 401, 403 |
  | GET /api/flashcard-decks/{id} | 200, 401, 403 | 401, 403 |
  | PATCH /api/flashcard-decks/{id} | 200, 401, 403, 422 | 401, 403, 422 |
  | GET /api/flashcard-decks/{id}/due | 200, 401, 403, 422 | 401, 403, 422 |

- Đây là khác biệt tài liệu response sinh ra, không phải bằng chứng chín endpoint thực thi trả lỗi HTTP. Cần leader xử lý/duyệt hướng sửa các annotation tại NoteController/DeckController; hai file này nằm ngoài allowlist T-15. Theo chỉ đạo AT-29, STOP ngay sau khi thu đủ bảng; không sửa backend/contract, không nới assertion và chưa dùng vòng fix nào.
- AT-23…AT-28: CHƯA KIỂM; AT-29: LỖI như bảng. Chưa chạy targeted toàn library, full verify (timeout 60 phút), JaCoCo hoặc các gate FE. Chưa tạo/chạy LibraryAcceptanceServer, không mở cổng HTTP nghiệm thu hoặc khởi động FE; ngân sách trình duyệt 90 phút chưa dùng. Không đánh hoàn thành T-15; tiến độ vẫn 14/15.
- Bằng chứng: TEMP/cnhsk-014-t15-gates.log, cnhsk-014-t15-compile01.log, cnhsk-014-t15-contract.log, cnhsk-014-t15-contract-audit.json; XML Surefire tại target/surefire-reports/TEST-com.cnhsk.contract.LibraryContractTest.xml. Sau JVM kết thúc đã đối chiếu đúng bốn ID container trong log (PostgreSQL, Redis, Mailpit, Ryuk): tất cả đã được dọn, còn lại 0. Chỉ ba file thay đổi: LibraryContractTest.java và tasks.md/questions.md của 014; diff --check, UTF-8/BOM PASS. Worktree gốc vẫn chỉ có hai dòng bổ sung riêng của spec 003, không bị stash/restore/stage. Chưa stage, commit, push hoặc mở PR T-15; chờ leader chỉ đạo tiếp tục sau xử lý lệch contract.

## 2026-11-08 — Leader duyệt 014/T-14 G0 → G1, rút lại D5 sampling

- G0 đã duyệt, base origin/main `95f401f`; tạo lại nhánh `spec/014-t14`, worktree `CNHSK-014-t14`. Không đụng thay đổi riêng ở worktree gốc; không đọc/tạo khoá API hoặc .env, không migration/SQL trên Neon.
- Allowlist đúng 14 file:
  1. `src/main/java/com/cnhsk/learning/library/ClaudeTranslator.java`
  2. `src/main/java/com/cnhsk/learning/library/TranslationService.java`
  3. `src/main/java/com/cnhsk/learning/library/web/TranslationController.java`
  4. `src/main/java/com/cnhsk/learning/library/web/TranslateRequest.java`
  5. `src/main/java/com/cnhsk/learning/library/web/TranslateResult.java`
  6. `src/test/java/com/cnhsk/learning/library/ClaudeTranslatorTest.java`
  7. `src/test/java/com/cnhsk/learning/library/TranslationServiceIntegrationTest.java`
  8. `src/test/java/com/cnhsk/learning/library/TranslationControllerIntegrationTest.java`
  9. `src/test/java/com/cnhsk/learning/library/TranslationLogIntegrationTest.java`
  10. `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`
  11. `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`
  12. `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`
  13. `specs/014-library/tasks.md`
  14. `specs/014-library/questions.md`
- Leader rút lại D5: “§0.3 temperature 0 không áp dụng — theo 015 Q-T04-04/05; ổn định bằng system prompt”. API 2026-10-21 từ chối temperature kể cả 0; không gửi tham số sampling nào. AnthropicClaudeClient và AnthropicClaudeClientTest đã hoàn nguyên hoàn toàn về origin/main; git diff origin/main trên đúng hai file rỗng. Translator chỉ yêu cầu dịch, không giải thích, không thêm nội dung và chỉ trả JSON {"translation":"…"}; không sửa interface/client/config của 015.
- Leader chấp nhận giới hạn Q1 của 013: lỗi → refund → thử lại đúng nội dung cùng phút nhận receipt cũ, không trừ thêm. Giữ nguyên D3, không UUID hoặc xoá receipt; sẽ có test tài liệu hoá hành vi này.
- Leader chấp nhận hai cache miss đồng thời cùng user/key, không gộp hoặc thêm khoá Redis. Hai lỗi phải hoàn có hiệu lực một lần (QUOTA giảm đúng một; CREDIT đúng một dòng REFUND). Một thành công + một lỗi có thể được miễn phí một lượt; đây là giới hạn đã chấp nhận, không tự đổi nghiệp vụ.
- Consume/refund dùng REQUIRES_NEW, service không @Transactional; provider ngoài transaction. Segmenter lỗi hoặc ghi cache lỗi sau provider thành công không refund. Giữ D1–D10, parse 422 thông báo cố định; cache chỉ translation, Segmenter tính lại mỗi lần.
- G1 bắt đầu 21:47:37 ngày 2026-11-08 (UTC+7). Preflight Java 17.0.12 đạt; Docker chưa chạy, đã khởi động Docker Desktop. Đến 21:51:08 Docker 29.9.2 sẵn sàng. Không có lệnh Maven nào trước test-compile dưới đây.
- Lệnh Maven số 1: `mvnw.cmd -B test-compile`; bắt đầu 21:51:19, kết thúc 21:51:36, exit=1, Maven báo 14,328 giây. Dừng do compile SDK theo chỉ đạo cũ, đã được leader rút lại và không tính vào giới hạn hai vòng fix. Tiếp tục G1 lúc 22:02:12 sau hoàn nguyên hai file; giữ log cnhsk-014-t14-gates.log và cnhsk-014-t14-compile-01.log trong TEMP. Chưa đánh hoàn thành T-14, chưa commit/push/PR.
- Các lệnh sau khi rút D5 (2026-11-08, UTC+7, Java 17/DEBUG=false; không ép spring.profiles.active):

  | Lệnh/gate | Bắt đầu | Kết thúc | Kết quả |
  |---|---|---|---|
  | mvnw.cmd -B test-compile sau ClaudeTranslatorTest | 22:06:10 | 22:06:55 | PASS, Maven 42,947 s |
  | mvnw.cmd -B test-compile sau TranslationServiceIntegrationTest | 22:10:17 | 22:10:58 | PASS, Maven 37,921 s |
  | mvnw.cmd -B test-compile sau TranslationControllerIntegrationTest | 22:13:23 | 22:14:01 | PASS, Maven 35,431 s |
  | mvnw.cmd -B test-compile sau TranslationLogIntegrationTest | 22:14:57 | 22:15:34 | PASS, Maven 34,942 s |
  | mvnw.cmd -B test-compile sau GlobalExceptionHandlerTest | 22:16:12 | 22:16:51 | PASS, Maven 36,307 s |
  | mvnw.cmd -B test-compile sau bổ sung latch/barrier | 22:19:29 | 22:20:12 | PASS, Maven 40,613 s |
  | mvnw.cmd -B -Dtest=ClaudeTranslatorTest,TranslationServiceIntegrationTest,TranslationControllerIntegrationTest,TranslationLogIntegrationTest,GlobalExceptionHandlerTest test | 22:20:12 | 22:21:15 | FAIL: 86 test, 5 failure, 6 error, 0 skip; Maven 1:00 |

- Targeted: Controller 29/29 và Log/AT-22 1/1 PASS, gồm validation, security, OpenAPI integer/int64 nullable và cached boolean. ClaudeTranslatorTest 17 test/1 error; service integration 21 test/4 failure/5 error; GlobalExceptionHandlerTest 18 test/1 failure. NFR-P03 hiện đo được bằng stub 20 mẫu cache miss, mỗi nguồn 20 từ: đầu 19,386 ms, p50 19,012 ms, p95 22,344 ms; đây chưa phải số đo nghiệm thu cuối vì gate targeted còn lỗi.
- STOP G1 sau targeted, chưa chạy regression/full verify/JaCoCo: lỗi ngoài năm ngoại lệ được duyệt. Đã khoanh vùng (1) restub budget.execute bằng when(...) chạy Answer cũ với supplier null, gây NPE ngay trong setup của AT19_budgetFailurePreventsProviderCall; (2) stub refund qua proxy EntitlementApi kích hoạt propagation MANDATORY ngoài transaction, tạo UnfinishedStubbing và có dấu hiệu làm lệch hàng đợi response trong các test sau; (3) danh sách expected ErrorCode trong test chưa thêm TRANSLATE_UNAVAILABLE. Chưa kết luận mọi failure còn lại chỉ do cascade trước khi chạy lại sạch.
- Đề xuất vòng fix 1 chỉ ba file test trong allowlist: dùng doThrow cho unit budget; lấy spy target bằng AopTestUtils khi stub/verify EntitlementApi, giữ lời gọi service qua proxy thật; thêm mã mới vào expected inventory, giữ toàn bộ mã/assertion cũ. Không sửa production, không nới/xoá assertion hoặc skip; sau duyệt compile từng file và chạy lại targeted rồi mới regression/full verify. Chưa tự áp dụng vì đây là lỗi runtime/targeted, không thuộc quyền tự sửa testCompile. T-14 vẫn chưa hoàn thành, chưa G2 review hoặc commit/push.
- Leader đã duyệt Fix 1/2; bắt đầu 22:36:51. Chỉ sửa ba file test nêu trên: doAnswer/doThrow cho budget; AopTestUtils lấy spy target để stub/verify không chạy advice MANDATORY ngay lúc setup; inventory thêm đúng TRANSLATE_UNAVAILABLE. Không stub trả receipt cho consume/refund: các ca AT-18/AT-19 vẫn gọi EntitlementService thật qua proxy và kiểm free_usage, DEDUCT, REFUND trong PostgreSQL thật. Chỉ ca thử refund lỗi bơm exception theo D7. Toàn bộ assertion trước đó được giữ; có thêm kiểm target là spy. Hash tất cả file production giữ nguyên so với đầu Fix 1.
- Đối chiếu đủ 11 lỗi targeted cũ (F=failure, E=error; C=ClaudeTranslatorTest, S=TranslationServiceIntegrationTest, G=GlobalExceptionHandlerTest). Không có lỗi ngoài ba nhóm đã duyệt:

  | Test | Loại | Nguyên nhân | Nhóm fix |
  |---|---|---|---|
  | C.AT19_budgetFailurePreventsProviderCall | E | when(...) khi restub gọi Answer cũ với supplier null, NPE trước khi gọi translator | 1 |
  | S.malformedCacheIsAMissAndAProviderFailureWithRefundFailureStillReturnsSanitized503 | E | Stub refund qua proxy kích hoạt MANDATORY ngoài transaction; failure đã enqueue chưa được dùng | 2 |
  | S.concurrentCacheMissFailuresHaveOnlyOneEffectiveRefund(Source)[1: QUOTA] | E | BeforeEach gặp UnfinishedStubbing từ ca trên; queue còn response cũ | 2 |
  | S.redisReadAndWriteFailuresAreBestEffortAndNeverRefundSuccessfulTranslation | E | Cascade FIFO: nhận UPSTREAM_ERROR còn lại từ ca concurrent CREDIT thay vì response mới | 2 |
  | S.AT19_creditFailureWritesExactlyOneRefundReferencingTheDebit | F | Cascade FIFO: nhận success của ca cache lỗi thay vì TIMEOUT vừa enqueue, nên không ném exception | 2 |
  | S.AT18_AT19_cacheContainsOnlyTranslationHasSevenDayTtlAndIsSharedWithoutBilling | E | Cascade FIFO: nhận TIMEOUT còn lại từ ca credit, trả 503 thay vì success | 2 |
  | S.AT20_segmenterFailureStillCachesTheTranslationWithoutRefund | F | Cascade FIFO: nhận chuỗi 我读书。 còn lại sau NFR thay vì bản dịch của chính ca này | 2 |
  | S.accepted013Q1_retryAfterRefundInTheSameMinuteReturnsOldReceiptWithoutAnotherCharge(Source)[1: QUOTA] | F | Cascade FIFO: lượt đầu nhận success còn lại từ ca segmenter thay vì lỗi | 2 |
  | S.accepted013Q1_retryAfterRefundInTheSameMinuteReturnsOldReceiptWithoutAnotherCharge(Source)[2: CREDIT] | E | Cascade FIFO: lượt retry nhận TIMEOUT còn lại thay vì success, trả 503 | 2 |
  | S.AT19_allProviderFailuresRefundQuotaAndKeepTheErrorSanitized(Reason)[1: NOT_CONFIGURED] | F | Cascade FIFO: nhận success còn lại từ ca longest-match, nên không ném exception | 2 |
  | G.errorCodeContainsEveryCodeFromTheSpecification | F | Expected inventory chưa có mã mới TRANSLATE_UNAVAILABLE | 3 |

- Bảng trên được đối chiếu thứ tự testcase trong XML baseline và FIFO của StubClaudeClient. Sau sửa đúng ba file, targeted đạt 86/86, 0 failure/error/skip: C 17/17, S 21/21, Controller 29/29, Log 1/1, G 18/18. Không sửa/clear queue trong StubClaudeClient; việc loại bỏ stubbing lỗi bảo đảm response được tiêu thụ đúng ca. Không dùng mock bỏ qua nghiệp vụ để làm xanh test.
- Giải trình ClaudeTranslator.java +1/-1: thay dòng prompt “không thêm giải thích hoặc Markdown” thành “không giải thích, không thêm nội dung hoặc Markdown”. Đây là thay đổi ở lúc leader rút D5, trước Fix 1, để đáp ứng §0.3/D5 mới: chỉ dịch và ổn định đầu ra bằng system prompt. Không phải thay đổi parse/validation để né test; D6 vẫn bắt object JSON có translation string không blank, FAIL_ON_TRAILING_TOKENS giữ nguyên. Model translate-model, một SystemBlock cache=true, USER JSON, 4096 token và timeout 10 giây không đổi; không có tham số sampling. Vòng Fix 1 không thay dòng production này.
- Kết quả G1 sau Fix 1, bàn giao G2 (2026-11-08, UTC+7):

  | Lệnh/gate | Bắt đầu | Kết thúc | Kết quả |
  |---|---|---|---|
  | mvnw.cmd -B test-compile sau fix ClaudeTranslatorTest | 22:38:06 | 22:38:56 | PASS, Maven 44,103 s |
  | mvnw.cmd -B test-compile sau fix TranslationServiceIntegrationTest | 22:40:25 | 22:41:03 | PASS, Maven 35,544 s |
  | mvnw.cmd -B test-compile sau fix GlobalExceptionHandlerTest | 22:42:11 | 22:42:53 | PASS, Maven 39,922 s |
  | mvnw.cmd -B -Dtest=ClaudeTranslatorTest,TranslationServiceIntegrationTest,TranslationControllerIntegrationTest,TranslationLogIntegrationTest,GlobalExceptionHandlerTest test | 22:42:53 | 22:43:55 | PASS 86/86, 0 failure/error/skip; Maven 59,304 s |
  | mvnw.cmd -B -Dtest=$regressionTests test | 22:45:21 | 22:47:25 | PASS 616/616, đủ 43 lớp library + credit + ai; 0 failure/error/skip; Maven 2:01 |
  | mvnw.cmd -B verify | 22:47:26 | 23:06:36 | Chạy đến hết: 4.154 test, 1 failure NFR-P04 đã duyệt, 0 error/skip; exit=1, Maven 19:07 |
  | mvnw.cmd -B jacoco:report@report jacoco:check@check | 23:09:03 | 23:09:09 | PASS, reuse execution data lượt full, không chạy lại test; Maven 3,330 s |

- RegressionTests được lấy từ toàn bộ *Test.java dưới src/test/java/com/cnhsk/learning/{library,credit,ai}, tên lớp nối dấu phẩy; lệnh đầy đủ lưu tại TEMP/cnhsk-014-t14-gates.log. AnthropicClaudeClientTest 18/18 PASS, không thay hai file AI client/test. Không ép -Dspring.profiles.active. Đã giữ máy thức trong regression/full và khôi phục chế độ nguồn lúc 23:06:36; không ngắt full verify giữa chừng.
- Ngoại lệ duy nhất của full: QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries, dòng 304. Import 5.000 câu, samples_ms=[32693,24257,20191,22208,20228], local_p95_ms=32693; yêu cầu <30000 ms. Không lỗi HTTP/logic được ghi nhận trong failure này; test đã kiểm 5.000 created/unchanged và failed=0 trước assertion thời gian. Thuộc NFR_P04 QUESTION đã được duyệt, không sửa test/production, không nới ngưỡng hoặc chạy lại để che kết quả. Các lớp còn lại đạt 4.141/4.141, lớp QuestionImportIntegrationTest có 12/13 PASS: tổng 4.153 PASS/4.154, 1 failure, 0 error/skip. Số cuối đối chiếu Maven summary/XML; bộ đếm tiến độ chỉ cộng lớp PASS trước đó đã được đính chính.
- Vì Surefire dừng lifecycle trước phase verify/report, đã chạy riêng đúng các execution JaCoCo report/check đã cấu hình trong pom trên execution data của chính lượt full (có bản sao target/t14-full-jacoco.exec). Không chạy lại tests và không đổi pom/ngưỡng. Import-Csv target/site/jacoco/jacoco.csv, cộng counter từng lớp: LINE 11.678/12.078 = 96,6882%, khớp tổng counter lớp trong XML. Counter bundle XML gộp theo nguồn là 11.672/12.072 = 96,6865%; chênh 6 dòng covered do cách gộp lớp/bundle, không phải dữ liệu từ lượt test khác. jacoco:check PASS ngưỡng bundle 80%. T-14 riêng các lớp mới 92/98 LINE; không thêm test chỉ để phủ toString hoặc nhánh SHA-256 không thể thiếu trong Java 17.
- NFR-P03 bằng StubClaudeClient, mỗi lượt cache miss 20 từ/20 mẫu, không assertion thời gian cứng: targeted đầu/p50/p95 19,156/18,441/25,767 ms; regression 15,565/14,550/16,984 ms; full 9,147/7,677/9,147 ms. Đây là đo backend với stub, không phải độ trễ nhà cung cấp thật. AT-20 cache hit tính lại pinyin sau đổi từ điển với đúng một SELECT, không gọi lại provider/consume. AT-22 qua năm HTTP requests (gồm lỗi) PASS, không log source/translation/prompt/response và không có lịch sử.
- Self-check G2: đúng 14 file allowlist, UTF-8 không BOM, diff --check sạch, không TODO/@Disabled, không thêm dependency/migration/FE/config; không stage/commit/push. Toàn bộ production giữ nguyên hash so với trước Fix 1; chỉ ba file test được sửa trong vòng này, giữ assertion/nghiệp vụ, không mock bỏ qua consume/refund. Fetch sau full: origin/main vẫn 95f401f, không lệch ErrorCode/SecurityConfig/EntitlementApi, không cần ngoại lệ rebase 013/T-08. G1 bàn giao review G2 với một ngoại lệ môi trường nêu trên; còn một vòng fix dự phòng, chưa dùng. Chưa đánh hoàn thành T-14 hoặc chuyển sang G3.

### Leader duyệt G2 → G3; kiểm hàng đợi ở Fix 2

- Leader xác nhận origin/main vẫn `95f401f`, trùng base; duyệt commit đúng 14 file, push `spec/014-t14` và Draft PR vào main. Không rebase, không chạy lại full verify hoặc merge. Hai file AnthropicClaudeClient/AnthropicClaudeClientTest tiếp tục không có diff so với origin/main.
- Theo yêu cầu G3, dùng Fix 2 chỉ thêm tám assertion delta callCount trong hai file test: năm ở TranslationServiceIntegrationTest (provider JSON sai, credit refund, longest match, Segmenter lỗi, Redis lỗi), ba ở TranslationControllerIntegrationTest (503 sau 402, cookie hợp lệ sau các ca CSRF, contract không gọi provider). Không sửa production, không nới/xoá assertion, không đổi nghiệp vụ D1–D10. Mỗi ca enqueue một phản hồi nay kiểm delta=1; retry/concurrent kiểm delta=2, NFR kiểm delta=20, log kiểm delta=5; các ca không enqueue kiểm delta=0. Unit translator dùng lambda riêng, không dùng hàng đợi StubClaudeClient.
- Mỗi test T-14 tiêu thụ hết các phản hồi đã đưa vào hàng đợi StubClaudeClient (kiểm qua delta callCount).

  | Lệnh/gate Fix 2 (2026-11-08, UTC+7) | Bắt đầu | Kết thúc | Kết quả |
  |---|---|---|---|
  | mvnw.cmd -B test-compile sau assertion service | 23:23:13 | 23:23:46 | PASS, Maven 31,292 s |
  | mvnw.cmd -B test-compile sau assertion controller | 23:24:05 | 23:24:37 | PASS, Maven 30,343 s |
  | mvnw.cmd -B -Dtest=ClaudeTranslatorTest,TranslationServiceIntegrationTest,TranslationControllerIntegrationTest,TranslationLogIntegrationTest,GlobalExceptionHandlerTest test | 23:25:02 | 23:25:53 | PASS 86/86, 0 failure/error/skip; Maven 48,462 s |

- NFR-P03 lượt kiểm assertion bổ sung: stub/cache miss/20 mẫu × 20 từ, đầu/p50/p95 17,316/15,748/18,756 ms. Số báo cáo G3 của lượt full vẫn là p95 9,147 ms; regression 616/616, full 4.154 test với đúng ngoại lệ NFR_P04, JaCoCo 96,6882% giữ như bảng G2. Không chạy lại full theo chỉ đạo leader; report CSV/XML và bản sao execution data của lượt full được giữ nguyên.
- Self-check G3: allowlist đúng 14 file; UTF-8 không BOM; không TODO/@Disabled; diff --check sạch; không dependency, migration, cấu hình, FE hoặc sửa client 015. Stage từng đường dẫn, một conventional commit; không đụng .env hay thay đổi riêng của worktree gốc. Đánh ✅ T-14 theo duyệt G3 và ngoại lệ môi trường đã được chấp nhận; tiến độ 14/15, T-15 còn chờ Shadow Plan G0.

## 2026-11-07 — Leader duyệt 014/T-03 SCR-026 Translation

- Fetch origin/main `6d3c20b` (đã có T-12 và T-13), tạo nhánh `spec/014-t03-translation`, worktree `CNHSK-014-t03` từ đúng base này. Không sửa worktree gốc hoặc thay đổi riêng của spec 003.
- Allowlist đúng 12 file:
  1. `cnhsk-web/src/features/library/pages/Scr026TranslationPage.tsx` — Page điều phối API, form và vòng đời request theo tài khoản.
  2. `cnhsk-web/src/features/library/pages/Scr026TranslationPage.test.tsx` — AT-25, lỗi, lưu từ, khoá loading và request muộn.
  3. `cnhsk-web/src/features/library/components/TranslationView.tsx` — View nhận props, bộ đếm, form, kết quả và thông báo.
  4. `cnhsk-web/src/features/library/components/TranslationLexemePopup.tsx` — popup nhận props, bàn phím và focus.
  5. `cnhsk-web/src/features/library/components/TranslationView.test.tsx` — các trạng thái, consumption, text an toàn.
  6. `cnhsk-web/src/features/library/api/translation.ts` — thêm AbortSignal tuỳ chọn, không đổi body/endpoint.
  7. `cnhsk-web/src/features/library/api/client-integration.test.ts` — xác nhận signal/POST/CSRF và huỷ qua shared client.
  8. `cnhsk-web/src/features/library/screens.ts` — đăng ký lazy SCR-026.
  9. `cnhsk-web/src/features/library/routes.test.tsx` — heading thật và preview offline.
  10. `cnhsk-web/src/features/library/catalog.preview.ts` — nâng render SCR-026 hiện có, giữ nguyên số mục.
  11. `specs/014-library/questions.md` — quyết định và bằng chứng gate.
  12. `specs/014-library/tasks.md` — cập nhật khi gate đạt, ghi nối thật ở T-15.
- Q-T03-01: popup nhỏ mới thay vì dùng bố cục trang LexemeDetailView có điều hướng về kết quả. Hiện chữ/pinyin/nghĩa, LexemeSaveActions có sẵn, nút đóng, Escape, giữ Tab trong popup, trả focus về trigger còn tồn tại; có loading/error/retry cho FR-007. Page gọi getLexeme, không gọi lại dịch khi bấm từ. Callback createNote đổi sourceType từ mặc định DICTIONARY của component thành TRANSLATION; addCard chỉ gửi lexemeId. GET tra từ có thể huỷ khi đóng/đổi popup; không ảnh hưởng lượt dịch.
- Q-T03-02: leader chốt BR-061-5 tự bôi đen để tra sang V2. Luồng đó cần tra theo text, chọn kết quả/không tìm thấy và hành vi selection trên bàn phím/mobile; T-03 dùng popup FR-007 theo lexemeId, giữ phạm vi tối thiểu đã duyệt. Không tự đưa nội dung được chọn vào URL.
- Q-T03-03: 402 INSUFFICIENT_CREDITS và 503 TRANSLATE_UNAVAILABLE ngay dưới form, giữ nguyên nội dung/hướng. 402 có link cố định /account/credits (SCR-030); 503 đúng câu “Dịch tạm thời lỗi — lượt đã được hoàn”. 422 tại textarea; lỗi khác dùng ErrorState và requestId hiện có. Chỉ gửi lại khi người dùng bấm, không thêm retry/timer ở Page. Shared client giữ cơ chế CSRF/phiên hiện có.
- Q-T03-04: đã kiểm library/routes.test.tsx, app/preview/registry.test.ts và community/catalog-wiring.test.ts: số 12 của community chỉ áp dụng SCR-040. Giữ catalog library 12 → 12 mục, SCR-026 1 → 1 mục, ID library-translation-samples và thứ tự không đổi. Chỉ nâng render View thật với 4 trạng thái ready/loading/empty/error và popup mẫu có callback stub; không API/auth request. Không sửa registry dùng chung hay preview khác.
- Leader chỉnh bắt buộc: khi đang dịch, khoá textarea, chọn hướng, đổi hướng và nút Dịch; form aria-busy=true. Ref khoá ngay trước await chặn submit lặp và cả change handler gọi trực tiếp. Không huỷ request dịch khi sửa nội dung/hướng; chỉ abort khi unmount/đổi tài khoản, kèm alive/generation để bỏ cả success/error/finally muộn. Không suy ra đã hoàn lượt do huỷ phía client. Sau kết quả, sửa nội dung/hướng mới xoá kết quả cũ và đóng popup. Nội dung riêng nằm trong subtree key=user.id và mất khi rời trang/đổi tài khoản.
- Đếm nguyên nội dung bằng Array.from(text).length (code point), không maxLength UTF-16 và không tự chuẩn hoá/cắt văn bản. Trim chỉ kiểm rỗng; 2.000 qua, 2.001 chặn trước gọi API. Mặc định ZH→VI, đổi hướng không tự dịch. Pinyin null không hiện dòng, segments=[] vẫn hiện bản dịch; đoạn không có lexemeId là text thường. Các đoạn tiếng Trung là nguồn khi ZH→VI, bản dịch khi VI→ZH.
- Câu chữ đã duyệt: cached=true → “Từ bộ nhớ đệm — không trừ lượt”; không cache và QUOTA → “Đã dùng 1 lượt miễn phí.”, CREDIT → “Đã dùng điểm.”, null → không hiện. Không suy ra số điểm hay số dư từ response. Mọi nội dung hiển thị bằng text node, không lưu source/result vào storage, URL hoặc log, không lịch sử.
- Lệch tài liệu cũ screen-fields.md SCR-026: AUTO, saved history và remaining entitlement chưa khớp spec 014/contract. T-03 chỉ có ZH↔VI, không lịch sử, chỉ báo consumption vừa dùng; không tự thêm truy vấn số dư hoặc UI ngoài contract. Không sửa screen-fields.md, backend, contract, routeAccess, shared UI hoặc thêm dependency.
- Gate đã duyệt: npm ci, Vitest library, full Vitest --maxWorkers=2 --minWorkers=2, lint (0 error; chỉ warning LevelBadge có sẵn được phép), build, diff/BOM/dấu việc chưa làm/allowlist. Các test chập chờn Scr059/Scr007/Scr029/Scr014/Scr016 nếu fail sẽ chạy riêng một lần, giữ đủ số đo. Không spy timer global khi dùng fake timers. Test API đều mock, nối thật ở T-15. Chỉ đánh ✅ khi gate đạt; stage từng file, một conventional commit, push nhánh và Draft PR vào main, không merge/rebase/force-push.
- Kết quả: npm ci PASS, không đổi lockfile/dependency. Library bản cuối 345/345 test, 11/11 file PASS (27,50 giây); full Vitest 2.263/2.263 test, 160/160 file PASS (304,87 giây), 0 failure/error/skip. Không test chập chờn nào fail, không cần chạy riêng hoặc dùng ngoại lệ. Lint 0 error, chỉ 1 warning react-refresh/only-export-components của LevelBadge có sẵn; build TypeScript + Vite PASS (2.220 module, 10,99 giây cho Vite). Không thêm warning, eslint-disable hay spy timer global.
- AT-25/FR-025 PASS: biên 2.000/2.001 và ngoài BMP, rỗng/toàn khoảng trắng, đổi hướng không gọi API, gửi nguyên văn; khoá đủ control + aria-busy và cả ref guard khi submit/change lặp, signal không bị huỷ trong request đang chạy. Popup có loading/404/retry, nút đóng/Escape/Tab/Shift+Tab/trả focus, bỏ response sau đóng; getLexeme không gọi lại translate. Lưu ghi chú sourceType=TRANSLATION và thêm thẻ chỉ lexemeId; đủ cache/QUOTA/CREDIT/null, pinyin null và segments rỗng. 402 có link SCR-030, 503 đúng câu hoàn lượt, 422 tại textarea, các lỗi khác ErrorState; giữ input/hướng và chỉ gửi lại khi bấm. Request muộn cả resolve/reject sau unmount bị bỏ; đổi tài khoản xoá dữ liệu và finally cũ không mở khoá request tài khoản mới. Spy cả hai storage xác nhận không ghi; rời/quay lại trang trống, URL không có nội dung. Payload HTML/script chỉ là text node.
- Self-check PASS: đúng 12 file allowlist, UTF-8 không BOM, diff sạch, không thêm dấu việc chưa làm, không log/storage nội dung, không dangerouslySetInnerHTML/window.confirm hoặc page import preview. Preview vẫn 12 mục, 4 trạng thái SCR-026 chạy offline không API/auth. Fetch trước bàn giao: origin/main từ 6d3c20b lên 9ae4d87, không file nào trong 12 file allowlist thay đổi; giữ nguyên base 6d3c20b, không merge/rebase. Đánh ✅ T-03, tổng 13/15; đây là nghiệm thu FE bằng mock, nối thật ở T-15.

## 2026-11-06 — Leader duyệt 014/T-13 Segmenter

- Fetch origin/main `fae62f1` (đã có PR #125/T-12), tạo nhánh `spec/014-t13-segmenter` và worktree `CNHSK-014-t13` từ đúng base này. Không merge/rebase hoặc sửa worktree gốc.
- Allowlist đúng 6 file:
  1. `src/main/java/com/cnhsk/learning/library/Segmenter.java` — service điều phối, hàm static split thuần và các record kết quả; exception riêng không chứa nội dung đầu vào.
  2. `src/main/java/com/cnhsk/learning/library/SegmenterQueryRepository.java` — một native SELECT với tham số mảng, chọn lexeme ưu tiên.
  3. `src/test/java/com/cnhsk/learning/library/SegmenterTest.java` — fixture tự soạn, kiểm tách thuần, Unicode, giữ nguyên văn bản, pinyin và lỗi repository.
  4. `src/test/java/com/cnhsk/learning/library/SegmenterIntegrationTest.java` — PostgreSQL Testcontainers, fixture commit trước đo, đếm SELECT và benchmark 2.000 code point.
  5. `specs/014-library/questions.md` — các quyết định Q-T13-xx và bằng chứng gate.
  6. `specs/014-library/tasks.md` — chỉ đánh dấu T-13 và cập nhật tiến độ khi gate đạt.
- Q-T13-01: cùng text chọn WORD > GRAMMAR > CHAR, sau đó id nhỏ nhất. SQL DISTINCT ON (x.text), ORDER BY x.text, CASE x.kind ..., x.id ASC; không phụ thuộc thứ tự Map hay thứ tự chèn fixture.
- Q-T13-02: chữ CJK không có trong bảng tra trả mỗi code point một segment, pinyin và lexemeId đều null; không gộp các chữ lạ.
- Q-T13-03: gộp dải không-CJK liên tiếp, giữ nguyên mọi khoảng trắng, dấu câu, chữ Latin, số, emoji và xuống dòng. Ghép segment.text phải đúng đầu vào, không trim/chuẩn hoá nội dung.
- Q-T13-04: whitelist các block CJK Unified Ideographs, Extension A–G, CJK Compatibility Ideographs và Compatibility Supplement, đồng thời Character.isIdeographic(int). Dấu câu CJK, chữ kana/hangul, bộ thủ và ký hiệu ngoài whitelist không được tra. Dùng code point và offset UTF-16; từ dài tối đa 8 code point, không cắt đôi surrogate pair.
- Q-T13-05: GRAMMAR chứa ký hiệu như 是…的 tự nhiên không nằm trong candidate CJK liên tục; không xoá ký hiệu hoặc biến đổi pattern để khớp.
- Segmenter.segment() gom distinct mọi chuỗi con CJK liên tục dài 1…8, gọi repository đúng một lần nếu tập không rỗng; rỗng/không-CJK không gọi repository và không SELECT. Segmenter.split(text, Map) là hàm thuần, so khớp dài nhất trước. Null đầu vào ném IllegalArgumentException.
- Repository dùng `x.text = ANY(:texts)` với String[] được bind tường minh bằng Hibernate NativeQuery.setParameter(name, value, String[].class); PostgreSQL nhận một tham số mảng. Không SQL nối input, không setParameterList, không hàng nghìn placeholder, không truy vấn theo từng đoạn. Điều kiện kind và char_length giữ đúng phạm vi từ điển. Chỉ đọc learning.lexemes, không entity/dependency/migration mới.
- Pinyin từng đoạn có thể null; bỏ qua null/rỗng/toàn khoảng trắng khi nối pinyin cả đoạn bằng một dấu cách, không đoạn nào có pinyin thì kết quả null. Pinyin có dấu được giữ nguyên sau bỏ khoảng trắng ở hai đầu; không chọn kind ưu tiên thấp hơn chỉ vì pinyin của kind đã chọn là null.
- Lỗi repository được đổi thành Segmenter.SegmentationException có thông báo cố định, không đính kèm cause có thể chứa tham số SQL, không log văn bản, không trả kết quả fallback. T-14 chịu trách nhiệm bắt lỗi và giữ bản dịch. Không endpoint, TranslationService, Claude, mã lỗi mới hoặc thay đổi SecurityConfig/contract trong T-13.
- Test tích hợp dùng PostgreSQL thật và fixture tự soạn đã commit trước khi gọi segmenter. Đầu vào hiệu năng gồm 2.000 chữ Ext B khác nhau (4.000 UTF-16 code unit), tạo 15.972 candidate distinct; đo toàn bộ segment() gồm truy vấn, tạo kết quả và transaction. Báo lượt đầu, sau 3 lượt warm-up lấy 20 mẫu, p50/p95 theo nearest-rank. Mốc 200 ms là mục tiêu mềm theo duyệt; không đổi fixture hoặc hạ assertion để che số đo chậm. Cleanup chỉ xoá đúng ID fixture do lớp test đã chèn.
- Gate: toàn bộ test library, sau đó full `mvnw.cmd -B verify`, Java 17, DEBUG=false, Docker; giữ assertion/ngưỡng test cũ. Kiểm phạm vi 6 file, diff, UTF-8 không BOM và không dấu việc chưa làm; fetch trước PR, báo thay đổi file dùng chung, không rebase/merge. Một conventional commit, push nhánh đã duyệt, Draft PR vào main; không merge PR.
- Kết quả gate ngày 2026-11-07: chọn lọc đủ 23 lớp library PASS 322/322, 0 failure/error/skip, 1 phút 47 giây. SegmenterTest 29 và SegmenterIntegrationTest 5 đều PASS, gồm null pinyin, mọi block đã chốt, giới hạn 8 code point và lỗi không chứa văn bản. Full `mvnw.cmd -B verify` PASS 3.833/3.833 test, 0 failure/error/skip, 16 phút 43 giây; Java 17, DEBUG=false, PostgreSQL Testcontainers. JaCoCo LINE 10.957/11.325 = 96,7506%, vượt ngưỡng 80%; toàn bộ lớp Segmenter/record/exception/repository đạt LINE 76/76. Không dùng ngoại lệ môi trường, không skip/hạ assertion. Đánh ✅ T-13, tổng 12/15.
- SQL đo thực tế: đầu vào 2.000 code point, 15.972 candidate dùng đúng 1 SELECT; rỗng/không-CJK dùng 0 SELECT; ưu tiên kind và bind String[] trực tiếp đều được xác nhận trên PostgreSQL. Benchmark trong full verify: lượt đầu 118,642 ms, p50 54,492 ms, p95 105,246 ms (20 mẫu sau 3 warm-up), dưới mục tiêu mềm 200 ms. Lượt chọn lọc trước đó: đầu 112,331 ms, p50 354,914 ms, p95 411,126 ms, vượt mục tiêu mềm; giữ nguyên cả hai số đo và cùng fixture để thể hiện dao động giữa các lượt chạy, không thay đổi ngưỡng/assertion. Lượt chạy riêng ban đầu 34/34 PASS có số đo đầu 258,328 ms, p50 368,916 ms, p95 429,204 ms.
- Fetch trước bàn giao: origin/main vẫn `fae62f1`, không file nào trong allowlist đổi so với base. Self-check PASS: đúng 6 file, diff sạch, UTF-8 không BOM, không thêm dấu việc chưa làm; không dependency/schema/contract/endpoint mới, không truy vấn xuyên module hoặc SQL ghép input. Không sửa SecurityConfig/ErrorCode, không log văn bản, không fallback của T-14. Chỉ stage từng file được duyệt; một conventional commit và Draft PR, không merge/rebase/force-push.

## 2026-11-06 — Leader duyệt 014/T-12, kèm chỉnh

- Fetch origin/main `bcf89cb`, tạo nhánh `spec/014-t12-flashcard-review` và worktree `CNHSK-014-t12` từ base này. Chỉ triển khai T-12 theo duyệt, không rebase/merge hoặc đụng thay đổi riêng ở worktree gốc.
- Allowlist tối đa 20 file (contract chỉ đổi cho ahead sai kiểu):
  1. `src/main/java/com/cnhsk/learning/library/FlashcardReviewService.java`
  2. `src/main/java/com/cnhsk/learning/library/FlashcardReviewRepository.java`
  3. `src/main/java/com/cnhsk/learning/library/web/FlashcardReviewController.java`
  4. `src/main/java/com/cnhsk/learning/library/web/FlashcardReviewWrite.java`
  5. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
  6. `src/main/java/com/cnhsk/learning/library/LibraryRateLimiter.java`
  7. `src/main/java/com/cnhsk/learning/library/LibraryConfig.java`
  8. `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`
  9. `src/main/resources/application.yaml`
  10. `src/test/java/com/cnhsk/learning/library/FlashcardReviewIntegrationTest.java`
  11. `src/test/java/com/cnhsk/learning/library/FlashcardReviewConcurrencyIntegrationTest.java`
  12. `src/test/java/com/cnhsk/learning/library/FlashcardReviewQueryCountIntegrationTest.java`
  13. `src/test/java/com/cnhsk/shared/config/FlashcardReviewSecurityConfigIntegrationTest.java`
  14. `src/test/java/com/cnhsk/learning/library/LibraryRateLimiterTest.java`
  15. `src/test/java/com/cnhsk/learning/library/LibraryConfigTest.java`
  16. `specs/014-library/contracts/openapi-library.yaml`
  17. `specs/014-library/questions.md`
  18. `specs/014-library/tasks.md`
  19. `src/main/java/com/cnhsk/learning/review/FsrsScheduler.java`
  20. `src/test/java/com/cnhsk/learning/review/FsrsSchedulerTest.java`
- Q-T12-01: base chưa có bean FsrsScheduler. Service dùng ObjectProvider: inject bean nếu đã có; nếu chưa thì tạo từ FsrsConfig.settings(), không đăng ký bean mới. Không sửa bất kỳ file 011 nào ngoài đúng cặp FsrsScheduler/FsrsSchedulerTest được leader bổ sung.
- Q-T12-02: repository riêng dùng native query, không thêm entity và không sửa LexemeSchedule/repository của 011. READ_COMMITTED, advisory library:decks:<userId> → kiểm và khoá bộ/thẻ FOR UPDATE OF c,i → Clock lấy một lần (độ chính xác microsecond của DB) → INSERT FLASHCARD ON CONFLICT DO NOTHING RETURNING → SELECT đúng khoá user_id/FLASHCARD/cardId FOR UPDATE → UPDATE chỉ stability, difficulty, next_review_at, review_count+1, lapse_count, last_seen_at, updated_at. INSERT có kết quả thì first(), không thì review(Memory từ DB); không suy ra lần đầu từ review_count. Không UPDATE mastery, không attempt_items, không event mastery.
- Q-T12-03: due luôn JOIN bộ/thẻ chưa xoá, đúng chủ và kind; progress của thẻ/bộ đã xoá vẫn giữ trong DB nhưng không ảnh hưởng danh sách. Thẻ mới chỉ khi chưa có dòng FLASHCARD của người gọi. SQL due gồm đúng 2 SELECT: sở hữu bộ và CTE lấy tối đa 100; quá hạn lâu nhất trước, rồi thẻ mới theo order_index/id. ahead=true chỉ lấy tương lai khi toàn bộ tập đến hạn/mới rỗng, không bù đủ 100. Không dùng session-max-items=50 của 011.
- Q-T12-04 (leader chỉnh): validate body hoàn tất trước khi gọi limiter, rồi mới kiểm sở hữu. 403 nghiệp vụ tính một lần, mọi 422 body không tính; 401/sai role/CSRF bị chặn trước controller không tính. Không ModelAttribute. DTO validate JSON và rating trước limiter, dùng ApiException VALIDATION_FAILED hiện có cho thiếu/sai rating hoặc JSON không hợp lệ; không thêm exception handler cục bộ, không sửa GlobalExceptionHandler.
- Rate limit riêng library:flashcards:review:user:<userId>:yyyyMMddHH, cửa sổ giờ UTC, cnhsk.library.flashcard-reviews-per-hour: 500 bắt buộc và dương lúc khởi động. Lượt 501 trả RATE_LIMITED + retryAfterSeconds làm tròn lên, không thêm Retry-After. Redis lỗi giữ fail-open và WARN không kèm thông tin kết nối như limiter hiện có. Matcher chỉ thêm một dòng LEARNER /api/flashcards/**; due dùng matcher bộ thẻ hiện có.
- Theo cách dictionary/notes hiện xử lý query param bằng chuỗi và kiểm thủ công, ahead chỉ nhận true/false, thiếu là false; sai kiểu trả 422 VALIDATION_FAILED. Contract chỉ thêm response 422 cho GET /due, không thêm header Retry-After hoặc sửa response chung.
- Leader duyệt sửa FsrsScheduler: cap stability ở S_MAX=36500 trong hàm result dùng chung cho first/review, sau tính interval để giữ công thức/giới hạn ngày hiện có. Giữ guard stability không hữu hạn/không dương, không clamp trong repository, không migration. first(AGAIN) lapse=0 theo 011; AGAIN tăng lapse kiểm ở lượt sau. Thêm test first vượt trần và chuỗi EASY cùng ngày đạt 36500; integration kiểm ghi DB thành công, không 500.
- Gate chọn lọc toàn bộ test library + FsrsSchedulerTest/FsrsConfigTest và security mới; sau đó full mvnw.cmd -B verify (Java 17, DEBUG=false, Docker). Báo stability/2 SELECT/full verify/JaCoCo LINE; giữ assertion và ngưỡng test cũ. Chỉ ngoại lệ môi trường được leader duyệt (FR023, NFR_P04, LoginAttempt PEXPIRETIME/CapturedOutput, QuestionAdminControllerIntegrationTest.NFR_P02 chỉ p95) được báo tên/số đo và vẫn mở PR. Không skip/hạ ngưỡng. Commit từng file cụ thể, một conventional commit, Draft PR; fetch/báo thay đổi file dùng chung, không merge/rebase/force-push.
- Chọn lọc bản cuối PASS 373/373, 0 failure/error/skip: FsrsSchedulerTest 42/42 (giữ toàn bộ test 011 cũ), FsrsConfigTest 36/36; review integration 24, concurrency 5, query-count 4, security 7 đều PASS. Chuỗi EASY cùng ngày ghi DB stability=36500, interval 365, 30 lượt không 500; hai lượt đầu đồng thời một dòng/count=2. Due đo đúng 2 SELECT với 0/1/120/500 thẻ và khi fallback ahead; ID thiếu 1 SELECT, sai định dạng 0. ahead sai kiểu thực tế 422 nên contract chỉ thêm đúng response 422 cho due. Body sai/thiếu/malformed/trailing JSON đều 422 không tăng counter; 403 nghiệp vụ tính một lần, lượt 501 là 429 không Retry-After.
- Full `mvnw.cmd -B verify` PASS: 3.799 test, 0 failure/error/skip, 17 phút 38 giây; Java 17, DEBUG=false, Docker. JaCoCo LINE 10.881/11.249 = 96,7286%; library và web 798/808, review 146/158. NFR-P04 sổ tay 1.000 ghi chú p95 93,108 ms, giữ nguyên ngưỡng 200 ms. Không dùng ngoại lệ môi trường; đánh ✅ T-12, tổng 11/15 theo duyệt.
- Fetch cuối trước PR: origin/main vẫn `bcf89cb`, không file nào trong 20 file allowlist đổi so với base. Self-check PASS: đúng 20 file, diff sạch, UTF-8 không BOM, không thêm dấu việc chưa làm trong code/dòng thêm; chỉ hai file FSRS được sửa trong learning/review. Không dependency mới, không SQL nối input người dùng hoặc truy vấn xuyên module; không log nội dung riêng/credential. Không sửa ErrorCode, GlobalExceptionHandler, LexemeSchedule/repository, migration, FE hoặc cấu hình credentials; không thêm bean FSRS, header hay handler cục bộ. Giữ assertion/ngưỡng test cũ, commit/push đúng nhánh đã duyệt; không merge/rebase/force-push.

## 2026-11-05 — Leader duyệt 014/T-11

- PR #103/T-10 đã merge tại `5530c76`; đã fetch và tạo nhánh `spec/014-t11`, worktree `CNHSK-014-t11` từ đúng origin/main này. Base có V6 `mastery_progress`, T-09/T-10 và tổng 9/15; code dùng lại của T-10 khớp kế hoạch. Không rebase hoặc sửa worktree gốc.
- Allowlist đúng 11 file:
  1. `src/main/java/com/cnhsk/learning/library/CardService.java`
  2. `src/main/java/com/cnhsk/learning/library/CardRepository.java`
  3. `src/main/java/com/cnhsk/learning/library/web/CardWrite.java`
  4. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
  5. `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`
  6. `src/test/java/com/cnhsk/learning/library/CardIntegrationTest.java`
  7. `src/test/java/com/cnhsk/learning/library/CardConcurrencyIntegrationTest.java`
  8. `src/test/java/com/cnhsk/shared/config/DeckSecurityConfigIntegrationTest.java`
  9. `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`
  10. `specs/014-library/tasks.md`
  11. `specs/014-library/questions.md`
- Ba endpoint theo contract: POST `/{id}/cards` trả 201 Card; PATCH `/{id}/cards/{cardId}` trả 200 Card; DELETE trả 204. Mọi ghi READ_COMMITTED dưới `library:decks:<userId>` của T-10, lấy now một lần từ Clock sau khoá; khoá dòng bộ, sửa/xoá khoá thêm dòng thẻ. Bộ/thẻ sai ID, không tồn tại, khác chủ, khác loại, sai bộ hoặc đã xoá trả 403 COLLECTION_NOT_OWNED; không tự tạo bộ.
- Thứ tự POST: quyền sở hữu 403 → kiểm dữ liệu 422 → lexeme tồn tại 404 LEXEME_NOT_FOUND → trùng 409 CARD_DUPLICATE → hạn mức 422 LIBRARY_LIMIT_REACHED → ghi. Kiểm dữ liệu nghiệp vụ trong service sau kiểm quyền; DTO giữ hiện diện và kiểu JSON để phân biệt trường bị bỏ qua với null, không ép số thành chuỗi hoặc số thực thành ID nguyên.
- POST chọn đúng một dạng `{lexemeId}` hoặc `{front, back}`; trộn trường của cả hai dạng trả 422 (kể cả trường gửi null). Tự nhập: front 1–200, back 1–2.000 Unicode code point, không rỗng/toàn khoảng trắng/NUL; lưu nguyên nội dung, kể cả HTML dưới dạng văn bản. Lexeme tạo snapshot text/pinyin/meaningVi đúng T-10, không tạo user_progress; order_index đặt sau thẻ cũ, không sắp lại thứ tự.
- PATCH sửa được từng mặt riêng; trường thiếu giữ nguyên, gửi null/rỗng/toàn khoảng trắng/sai kiểu hoặc quá giới hạn trả 422. Cần ít nhất một mặt để sửa; lexemeId gửi trong PATCH bị bỏ qua, không đổi liên kết, order_index, created_at hoặc lịch ôn; updated_at dùng now. Bộ đủ 500 vẫn sửa được. DELETE chỉ đặt deleted_at của thẻ, giữ toàn bộ lịch ôn và các trường khác; xoá lại thẻ không còn hợp lệ trả 403.
- Trùng với thẻ chưa xoá cùng bộ nếu cùng lexemeId hoặc front sau trim/gộp khoảng trắng Unicode (`Character.isWhitespace` hoặc `isSpaceChar`) thành một dấu cách. Giữ hoa/thường, dấu và dạng Unicode; không thêm NFC. Chỉ chuẩn hoá khoá so sánh, không đổi nội dung lưu. PATCH loại chính thẻ đang sửa khỏi phép so. Thẻ đã xoá không tính trùng/hạn mức; hạn mức dùng max-cards của T-10, trả details{kind: CARDS, limit: 500}.
- Giữ nguyên uq_coll_lex của V1. INSERT dùng đúng ON CONFLICT (collection_id, lexeme_id) WHERE lexeme_id IS NOT NULL AND deleted_at IS NULL DO NOTHING RETURNING id; không có dòng trả về chuyển thành 409, không catch lỗi unique rồi tiếp tục trong transaction đã hỏng. Toàn bộ kiểm và ghi dưới cùng khoá/transaction, lỗi rollback hết.
- CARD_DUPLICATE chỉ thêm ngay trước INTERNAL_ERROR, giữ thứ tự mã khác. Contract không yêu cầu details cho lỗi trùng nên không sửa GlobalExceptionHandler; test lỗi trùng không có details và hạn mức có details. Không sửa SecurityConfig, cấu hình, schema, migration, FE hoặc làm T-12.
- Test PostgreSQL thật: tranh suất 499/500, cùng lexeme/front, hai PATCH tranh front, Clock sau chờ advisory, PATCH/DELETE chờ khoá dòng, người khác không chung khoá. Test chèn trùng trực tiếp bằng JDBC sau precheck trong transaction rồi thực thi INSERT thật để kiểm uq_coll_lex/ON CONFLICT trả 409 và rollback. Giữ nguyên assertion/ngưỡng T-08/T-10. Snapshot lịch V6 dùng row_to_json(p)::text, chuyển mảng thành giá trị JSON, không so PgArray theo identity; có evidence_item_ids không rỗng.
- Gate chọn lọc CardIntegrationTest, CardConcurrencyIntegrationTest, DeckIntegrationTest, DeckConcurrencyIntegrationTest, DeckQueryCountIntegrationTest, DeckSecurityConfigIntegrationTest, GlobalExceptionHandlerTest, LibraryConfigTest; sau đó full mvnw.cmd -B verify (Java 17, DEBUG=false, Docker). Kiểm diff/BOM/dấu việc chưa làm/11 file. Một conventional commit, push, Draft PR; fetch trước PR, báo nếu file dùng chung đổi; không merge/rebase. Chỉ ngoại lệ môi trường đã biết theo duyệt leader được ghi số đo và vẫn mở PR, không hạ ngưỡng hoặc skip.
- Chọn lọc PASS 134/134, 0 failure/error/skip. Test quan sát chờ khoá dùng pg_locks trực tiếp (transactionid/advisory), tránh snapshot pg_stat_activity trong transaction; không thay đổi assertion/ngưỡng có sẵn. Hồi quy DeckIntegrationTest trên V6 và kiểm 1/2 SELECT của T-10 đều PASS.
- Full `mvnw.cmd -B verify` PASS trên base có V6: 3.179 test, 0 failure/error/skip, 13 phút 29 giây; Java 17, DEBUG=false, Docker. JaCoCo LINE 9.085/9.359 = 97,0723%; library và web 705/712. NFR-P04 sổ tay 1.000 ghi chú p95 51,960 ms, giữ nguyên ngưỡng 200 ms. Không dùng ngoại lệ môi trường, không hạ ngưỡng hoặc skip; đánh ✅ T-11 và tổng 10/15 theo duyệt.
- Fetch trước PR: origin/main vẫn `5530c76`, không file nào trong allowlist đổi so với base. Không rebase/merge main. Self-check PASS: đúng 11 file, diff sạch, UTF-8 không BOM, không thêm dấu việc chưa làm trong code hoặc dòng thêm; những nhắc tên bước kiểm trong tài liệu cũ giữ nguyên. Không thêm dependency, không truy vấn xuyên module, AuthApi cung cấp người gọi và SQL bind tham số. Không sửa handler/security/config/schema/migration/FE hoặc code T-12; enum chỉ thêm đúng CARD_DUPLICATE, giữ nguyên assertion/ngưỡng T-08/T-10. Không log nội dung riêng hoặc credentials, chỉ commit/push nhánh đã duyệt.

## 2026-11-05 — Leader duyệt 014/T-10

- Nhánh `spec/014-t10`, worktree `CNHSK-014-t10`, base `211e7e3` được leader chấp thuận dù T-05 đã merge tại `96ac2aa`; không rebase. Một conventional commit, push nhánh, Draft PR, leader review/merge. Không đụng thay đổi riêng ở worktree gốc.
- Allowlist đúng 14 file:
  1. `src/main/java/com/cnhsk/learning/library/DeckService.java`
  2. `src/main/java/com/cnhsk/learning/library/DeckRepository.java`
  3. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
  4. `src/main/java/com/cnhsk/learning/library/web/DeckWrite.java`
  5. `src/main/java/com/cnhsk/learning/library/LibraryConfig.java`
  6. `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`
  7. `src/main/resources/application.yaml`
  8. `src/test/java/com/cnhsk/learning/library/LibraryConfigTest.java`
  9. `src/test/java/com/cnhsk/learning/library/DeckIntegrationTest.java`
  10. `src/test/java/com/cnhsk/learning/library/DeckConcurrencyIntegrationTest.java`
  11. `src/test/java/com/cnhsk/learning/library/DeckQueryCountIntegrationTest.java`
  12. `src/test/java/com/cnhsk/shared/config/DeckSecurityConfigIntegrationTest.java`
  13. `specs/014-library/tasks.md`
  14. `specs/014-library/questions.md`
- Chỉ năm endpoint của bộ thẻ theo contract đã duyệt: GET danh sách, POST 201 `DeckDetail`, GET chi tiết, PATCH 200 `DeckSummary`, DELETE 204. ID sai định dạng, không tồn tại, của người khác, khác loại hoặc đã xoá đều 403 `COLLECTION_NOT_OWNED`. Không triển khai thêm/sửa/xoá từng thẻ hoặc ôn FSRS của T-11/T-12.
- Mọi ghi chạy trong một transaction READ_COMMITTED, lấy advisory lock `library:decks:<userId>` trước, sau đó lấy `now` từ Clock một lần. Khoá tách khỏi `library:notebook:<userId>` và người dùng khác. Chủ sở hữu luôn lấy qua AuthApi, bỏ qua userId/isPublic trong payload; bộ mới luôn riêng tư.
- POST kiểm chủ đề tồn tại trước hạn mức; thiếu trả 404 `TOPIC_NOT_FOUND`. Có 50 bộ chưa xoá thì 422 `LIBRARY_LIMIT_REACHED`, `details{kind: DECKS, limit: 50}`. Tên 1–100 Unicode code point, cho trùng; mô tả tuỳ chọn tối đa 500. PATCH chỉ đổi tên/mô tả và updated_at, vẫn được sửa khi đủ hạn mức, không chép lại topicId.
- Chép tối đa 500 `topic_items` có lexeme, theo `order_index ASC, id ASC`; bỏ mục chỉ có knowledge point. Snapshot mặt trước là text; mặt sau ghép pinyin và meaningVi bằng ` — `, không có pinyin thì chỉ nghĩa. Chủ đề rỗng tạo bộ rỗng. Không tạo user_progress, không cập nhật snapshot khi nguồn thay đổi. Lỗi chép rollback cả bộ lẫn thẻ.
- Chủ đề hơn 500 từ: chép 500 thẻ đầu. `DeckDetail`/`DeckSummary` hiện không có trường báo đã cắt; giữ nguyên contract, không tự thêm `truncated` hoặc trường khác.
- Danh sách dùng một SELECT, sắp created_at DESC/id DESC; chi tiết dùng tối đa hai SELECT, thẻ theo order_index/id. SQL đếm cardCount/dueCount, bỏ thẻ đã xoá; lịch chỉ nối theo người gọi và target_type FLASHCARD. Theo FR-017, thẻ mới chưa có dòng FLASHCARD hoặc next_review_at không muộn hơn now được tính đến hạn. Không N+1.
- DELETE chỉ đặt deleted_at của bộ; giữ nguyên thẻ, lịch ôn và updated_at. Bộ đã xoá không còn hợp lệ cho các thao tác, kể cả xoá lần nữa (403 theo contract bộ thẻ, khác sổ tay).
- Cấu hình bắt buộc và dương khi khởi động: `cnhsk.library.max-decks: 50`, `max-cards: 500`. SecurityConfig chỉ thêm đúng matcher LEARNER cho `/api/flashcard-decks` và `/api/flashcard-decks/**`; không sửa schema, migration, FE, ErrorCode hoặc GlobalExceptionHandler.
- Gate: test chọn lọc bốn lớp Deck và LibraryConfigTest, sau đó full `mvnw.cmd -B verify` với Java 17, DEBUG=false, Docker; kiểm diff, BOM, dấu việc chưa làm và phạm vi 14 file. Chỉ ngoại lệ môi trường đã được leader chốt mới được ghi số đo và vẫn mở PR, không hạ assertion/ngưỡng hoặc skip test.
- Test chọn lọc PASS 46/46, không failure/error/skip. PostgreSQL thật kiểm tranh suất 49/50, hai lần xoá đồng thời, khoá riêng theo người dùng/sổ tay và Clock lấy sau khi chờ khoá; lỗi sau khi INSERT thẻ rollback cả bộ/thẻ. Hibernate Statistics đo qua MockMvc: danh sách đúng 1 SELECT với 0/1/10/50 bộ, chi tiết đúng 2 SELECT với 500 thẻ (không tồn tại 1, ID sai định dạng 0).
- Fetch trước PR: `origin/main` là `96ac2aa`. So với base `211e7e3`, trong allowlist chỉ `tasks.md` và `questions.md` đổi; toàn bộ file code/config T-10 chưa đổi trên main. Không rebase hoặc gộp main vào nhánh. Khi gộp tài liệu, leader giữ chốt/✅ T-05 và T-09 đã có trên main (8/15 trước T-10, 9/15 sau khi gộp); bộ đếm của nhánh dựa trên base riêng.
- Full `mvnw.cmd -B verify` PASS: 3.002 test, 0 failure, 0 error, 0 skip; 12 phút 36 giây, Java 17, DEBUG=false, Docker. JaCoCo LINE toàn bộ 8.784/9.032 = 97,2542%; library và web 508/513. NFR-P04 sổ tay giữ nguyên ngưỡng 200 ms, p95 55,612 ms trên 1.000 ghi chú. Không dùng ngoại lệ môi trường, không đổi assertion/ngưỡng test T-08; đánh ✅ T-10 theo duyệt.
- Self-check PASS: đúng 14 file allowlist, diff sạch, UTF-8 không BOM, không dấu việc chưa làm trong file thay đổi. Không thêm dependency, không JOIN xuyên module, giá trị SQL bind tham số; AuthApi cung cấp người gọi. Quyền/CSRF có integration test, không thêm log dữ liệu riêng, không sửa credentials/schema/migration/FE/ErrorCode/GlobalExceptionHandler. SecurityConfig chỉ thêm một dòng matcher; commit và push chỉ nhánh T-10 đã được duyệt, không merge.

## 2026-11-05 — Leader duyệt 014/T-05

- Base `origin/main` `211e7e3` (#99/T-04 đã merge), nhánh `spec/014-t05`, worktree `CNHSK-014-t05`. Allowlist đúng 15 file theo Shadow Plan: helper/validation, ba View, hai page và test, preview, screens, route/transport test, tasks/questions. Không sửa backend, shared/app, API implementation, cấu hình hoặc dependency. Không rebase, một conventional commit, Draft PR, leader merge.
- SCR-028 tạo bộ trống hoặc từ chủ đề; dùng lại getTopics của 003, chỉ gửi topicId, không chép từ/lịch ôn ở FE. Tên 1–100 ký tự, cho trùng; mô tả tối đa 500. SCR-029 thêm/sửa mặt trước 1–200, mặt sau 1–2.000; đếm Unicode code point, giữ nội dung thẻ nguyên văn. PATCH thẻ chỉ gửi front/back, không ghi đè lexemeId của thẻ từ từ điển.
- Quota 50 bộ/500 thẻ để backend kiểm; FE chỉ hiện giới hạn và details.kind/limit của LIBRARY_LIMIT_REACHED. Không thêm request kiểm quota. Hai danh sách dùng mảng contract và phân trang client 20 mục/trang, giữ thứ tự backend; clamp trang khi số bản ghi giảm.
- Ôn tại SCR-029, không điều hướng SCR-013: lấy tối đa 100 thẻ theo thứ tự server, lật rồi AGAIN/HARD/GOOD/EASY. Chỉ tăng vị trí/giảm số còn lại sau API thành công; pending chặn gửi lặp, lỗi giữ nguyên thẻ và mặt đã lật, không tự gửi lại rating. Người dùng chọn mức để thử lại. Không có thẻ đến hạn thì cho ahead=true. Hiện nextReviewAt/intervalDays từ server và câu "Ôn thẻ chỉ đổi lịch của thẻ, không đổi mức thành thạo"; FE không tính FSRS/mastery.
- Xoá bộ/thẻ dùng ConfirmPanel hiện có, huỷ trả focus về trigger; không cập nhật trước thành công, thất bại giữ dữ liệu và retry cần xác nhận lại. Giữ form khi CARD_DUPLICATE, LIBRARY_LIMIT_REACHED, TOPIC_NOT_FOUND, LEXEME_NOT_FOUND, COLLECTION_NOT_OWNED hoặc lỗi mạng; RATE_LIMITED giữ thẻ và hiện retryAfterSeconds nếu có.
- Page gọi API, View chỉ nhận props. Preview riêng có loading/empty/error/ready cho danh sách, chi tiết và ôn. Huỷ request cũ; bỏ kết quả muộn; xoá dữ liệu riêng khi đổi tài khoản/bộ. Nội dung luôn là React text node. Không window.confirm, HTML injection, eslint-disable, import preview trong page hoặc tính năng công khai.
- Tài liệu screen-fields cũ SCR-028 còn nhắc preset HSK và không chốt giới hạn mô tả; SCR-029 còn chuyển ôn sang SCR-013, chưa chốt độ dài mặt sau và có thao tác chia sẻ/sao chép/báo cáo công khai. T-05 theo spec 014 FR-013…018/027/028 và OpenAPI: nguồn chủ đề 003, mô tả 500, mặt sau 2.000, ôn inline, công khai là V2.
- Đối chiếu contract: PATCH bộ trả DeckSummary, không có cards. Annotation cũ của api/flashcards.ts khai Promise<DeckDetail>; T-05 chỉ đọc các trường summary rồi tải lại GET chi tiết, có regression test trả summary thật và không giả mảng cards. Sửa declaration client thuộc đợt đối chiếu T-15 ngoài allowlist T-05.
- Backend T-10…T-12 chưa có; FE dùng contract/client sẵn có, test mock và fixture tự soạn. Nghiệm thu với backend thật tại T-15; T-12 còn phụ thuộc FsrsScheduler của 011.
- Gate: npm ci PASS; toàn bộ Vitest với maxWorkers=2/minWorkers=2 PASS 149 file, 1.996 test, không failure/skip; lint 0 error, chỉ warning LevelBadge có sẵn; production build PASS. Kiểm diff/BOM/TODO và đúng allowlist 15 file. Trên Windows dùng npm.cmd/npx.cmd do script shim PowerShell bị chặn.
- Fetch trước PR: origin/main vẫn `211e7e3`, không đổi so với base; không có thay đổi file dùng chung. T-09 bàn giao riêng tại #100, không đưa code T-09 vào T-05, không rebase. Sau T-05 chỉ soạn Shadow Plan T-10 rồi chờ leader duyệt.

## 2026-11-05 — Leader duyệt 014/T-09

- Base `origin/main` `66a6d7d`, đã chứa #94/T-08 (`f5b491b`); nhánh `spec/014-t09`, worktree `CNHSK-014-t09`. Allowlist 12 file theo Shadow Plan đã duyệt; không sửa FE, SecurityConfig, GlobalExceptionHandler, cấu hình hoặc migration. Giữ nguyên assertion/ngưỡng/fixture T-08; test T-09 được bổ sung riêng.
- DELETE, GET trash và restore cùng lấy `pg_advisory_xact_lock(hashtextextended(:key,0))` với key `library:notebook:<userId>` trong transaction READ_COMMITTED. Dùng bean Clock hiện có, lấy `now` đúng một lần sau khi giữ khoá; timestamp T-09 bind vào SQL, không dùng đồng hồ DB. userId chỉ lấy qua AuthApi.
- DELETE trả 204, body rỗng. Xoá lại ghi chú của mình vẫn 204 và giữ nguyên deleted_at đầu tiên. Bản ghi không bị xoá vật lý; GET/list/PUT thông thường tiếp tục loại bản đã xoá.
- GET `/api/notes/trash` trả mảng theo contract, không phân trang; chỉ NOTEBOOK còn hiệu lực của người gọi, ghi chú đã xoá còn hạn. Chưa có sổ trả `[]`, không tạo sổ. Thứ tự `deleted_at DESC, id DESC`; `restorableUntil = deletedAt + 30 × 24 giờ UTC`. Đúng mốc 30 ngày còn hợp lệ, quá mốc thì ẩn khỏi thùng rác, giữ dữ liệu trong DB. Snippet là văn bản thô tối đa 200 code point.
- Thứ tự restore bắt buộc: (1) quyền sở hữu, ID sai/không tồn tại/khác người/khác kind hoặc notebook đã xoá → 403 COLLECTION_NOT_OWNED; (2) chưa xoá hoặc quá hạn → 409 NOTE_NOT_RESTORABLE; (3) trùng lexemeId với ghi chú chưa xoá khác → 409 NOTE_DUPLICATE kèm details.noteId; (4) hạn mức → 422 LIBRARY_LIMIT_REACHED với details{kind: NOTES, limit}; (5) ghi deleted_at=NULL, updated_at=now và trả 200 Note, giữ nguyên nội dung/metadata. Trùng từ ưu tiên trước quota; không gọi kiểm mục từ tồn tại của POST/PUT khi restore.
- Spec FR-012, bảng lỗi §5, AT-11 và OpenAPI được bổ sung NOTE_DUPLICATE cho restore trước implement. NOTE_NOT_RESTORABLE chỉ thêm một dòng ngay trước INTERNAL_ERROR; GlobalExceptionHandler hiện có đã hỗ trợ details của quota và duplicate.
- Test thời gian cố định: 29/30/31 ngày, đúng mốc và quá mốc 1 microsecond. Test quota 999/1.000, trùng từ và ưu tiên lỗi, ghi chú không gắn từ, IDOR/kind/parent đã xoá, role/CSRF. Test PostgreSQL thật xác nhận cùng advisory key cho cả ba endpoint, create/restore tranh quota hoặc từ, hai restore cùng ghi chú, người khác không bị khoá chung.
- Sau gate chọn lọc và full verify: báo số test/failure/error/skip, JaCoCo LINE, diff/BOM/TODO/allowlist. Chỉ nhóm lỗi môi trường leader đã duyệt được ghi rõ số đo rồi mở Draft PR. Một conventional commit, stage từng file cụ thể, push nhánh, Draft PR, không merge; fetch/báo thay đổi file dùng chung, không rebase.
- Kết quả gate Java 17, DEBUG=false, PostgreSQL/Redis Testcontainers: chọn lọc 74 test PASS; full `mvnw -B verify` 2.994 test, 0 failure/error/skip, BUILD SUCCESS (13 phút 32 giây). JaCoCo LINE toàn bộ 8.709/8.960 = 97,1987%; package library 406/409 dòng. Không dùng ngoại lệ môi trường, không đổi ngưỡng hoặc skip test.
- Fetch trước PR: main từ `66a6d7d` lên `211e7e3`, chứa #98 và #99/T-04. SecurityConfig, ErrorCode, GlobalExceptionHandler, application.yaml không đổi giữa base và main mới; tasks/questions và FE library có cập nhật T-04. Giữ nguyên base, không rebase/merge main vào nhánh; leader giữ cả chốt và trạng thái T-04/T-09 khi gộp.

## 2026-11-05 — Leader duyệt 014/T-04

- Base `origin/main` `bdccaac`, nhánh `spec/014-t04`, worktree `CNHSK-014-t04`. Allowlist đúng 12 file: `cnhsk-web/src/features/library/personalNotes.ts`, `personalNotes.test.ts`, `components/PersonalNotesView.tsx`, `components/NoteEditor.tsx`, `pages/Scr027PersonalNotesPage.tsx`, `pages/Scr027PersonalNotesPage.test.tsx`, `catalog.preview.ts`, `screens.ts`, `routes.test.tsx`, `api/client-integration.test.ts`, `specs/014-library/tasks.md`, `specs/014-library/questions.md` (các đường dẫn FE rút gọn cùng gốc `cnhsk-web/src/features/library/`). Một conventional commit, Draft PR, leader review/merge.
- SCR-027 dùng page gọi `api/notes`, View/Editor chỉ nhận props; preview độc lập có loading/empty/error/ready cho ghi chú và thùng rác. Không import preview trong page, không thêm dependency, không sửa shared/app/backend/DB hoặc contract. Các model và API ghi chú T-01 đã khớp contract T-08; thêm test giữ nguyên error details.
- Tìm sau trim và NFC, 2–100 Unicode code point, kiểm biên 1/2/100/101; bỏ q khi rỗng. Lọc tag bằng ô nhập, khớp chính xác, không gợi ý tag. Trang API từ 0, mặc định 20; đổi bộ lọc về trang đầu. Thùng rác dùng endpoint mảng hiện có và phân trang phía client.
- Backend kiểm hạn mức; FE không kiểm quota và không gọi thêm truy vấn lấy tổng ghi chú. 422 `LIBRARY_LIMIT_REACHED` hiển thị `details.limit`, giữ nguyên editor. Số `total` chỉ dùng phân trang.
- 409 `NOTE_DUPLICATE` hiện đúng "Từ này đã có trong sổ tay" và nút "Mở ghi chú đã có" theo `details.noteId`; không tự chuyển editor. Chỉ tải khi người dùng bấm; tải thất bại giữ bản nháp và cho thử lại. Chỉ dùng noteId là số nguyên dương an toàn; đánh giá đầy đủ int64 vẫn thuộc T-15.
- Tiêu đề tuỳ chọn theo OpenAPI `NoteRequest` và `NoteWrite` T-08; `screen-fields.md` SCR-027 cũ còn ghi bắt buộc. Nội dung/tiêu đề/thẻ có giới hạn và bộ đếm; sửa giữ nguyên `lexemeId`/`sourceType`, không thêm bộ chọn điểm kiến thức. Mọi lỗi tạo/sửa giữ nguyên dữ liệu nhập.
- Snippet là văn bản thô: React text node và `<mark>` chứa các đoạn text, không HTML injection. AT-26 có `<script>` và HTML, từ khoá chứa ký tự regex. Xoá cần ConfirmPanel trong trang, huỷ trả focus về trigger; không cập nhật danh sách trước API thành công.
- Thùng rác hiển thị `deletedAt` và `restorableUntil` theo giờ Việt Nam; xử lý `NOTE_NOT_RESTORABLE`, `NOTE_DUPLICATE`, `LIBRARY_LIMIT_REACHED`. Huỷ request cũ, bỏ kết quả đến muộn, xoá state riêng khi đổi tài khoản; chặn gửi lặp. Backend xoá/khôi phục thuộc T-09, nghiệm thu nối backend thật thuộc T-15.
- Gate: `npm ci` PASS; `npx vitest run --maxWorkers=2 --minWorkers=2` PASS 145 file, 1.825 test, không failure/skip; lint 0 error, duy nhất warning `react-refresh/only-export-components` tại LevelBadge có sẵn; production build PASS. Trên Windows gọi `npm.cmd`/`npx.cmd` do PowerShell chặn script shim `.ps1`.
- Fetch trước PR: main lên `0acbae7`, #94/T-08 merge tại `f5b491b`, #97/010-T-13 merge sau đó. So với base, cả `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandler.java`, `application.yaml` đã đổi; đã báo leader, không rebase/merge main vào nhánh. `tasks.md`/`questions.md` cũng có cập nhật T-08 trên main; leader giữ cả chốt T-08 và T-04 khi gộp.
- Sau T-04 chỉ soạn Shadow Plan T-09 khi leader báo tiếp tục; không tự bắt đầu code T-09.

## 2026-11-04 — Leader duyệt 014/T-08, hướng B

- Base lúc bắt đầu `cd7d1cb`, cập nhật lần lượt qua `34f5dff` (#91) và `b737ffd` (#92) trước PR. #89 đã merge tại `287ad95`. #92 thêm bốn mã lỗi sau QUESTION_NOT_FOUND, matcher sửa/lưu trữ câu hỏi và details cho MALFORMED_QUESTION; đã giữ nguyên các thay đổi này khi tích hợp, ba mã T-08 đặt sau QUESTION_ARCHIVED, ngay trước INTERNAL_ERROR. Allowlist 19 file; sửa spec/contract theo chốt này trước code; không sửa DB hoặc migration.
- BR-062-4 đổi thành mỗi `lexemeId` chỉ có một ghi chú chưa xoá trong sổ tay; giữ index `uq_coll_lex` của V1. Ghi chú không gắn từ có thể trùng nội dung; ghi chú trong thùng rác không tính trùng. POST trùng hoặc PUT đổi sang từ của ghi chú khác trả 409 `NOTE_DUPLICATE` với `details{noteId}`. PUT giữ nguyên từ của chính nó vẫn hợp lệ. PUT tham chiếu mục từ không có trả 404 `LEXEME_NOT_FOUND`.
- Tạo sổ mặc định, kiểm quota và trùng từ dùng chung `pg_advisory_xact_lock(hashtextextended(:key,0))`, key `library:notebook:<userId>`, trong transaction READ_COMMITTED. Không có unique constraint cho NOTEBOOK/người nên luôn lấy khoá trước tìm/tạo. GET khi chưa có sổ không tạo dữ liệu. PUT vẫn được sửa khi đủ quota; `updated_at` lấy tại thời điểm ghi bằng `clock_timestamp()`.
- Thêm cấu hình bắt buộc, dương `cnhsk.library.max-notes: 1000`; không khai trước hạn mức bộ/thẻ. `user_id` chỉ lấy từ phiên qua AuthApi; payload userId bị bỏ qua. Query luôn kiểm chủ sở hữu, kind NOTEBOOK và chưa xoá ở cả sổ/ghi chú; thiếu hoặc không sở hữu trả cùng 403 `COLLECTION_NOT_OWNED`.
- V1 đã có extension pg_trgm và `ix_item_search` GIN trên content; title chưa có index trigram. T-08 dùng index hiện có, xếp similarity rồi updated_at DESC, id DESC, escape LIKE và bind giá trị; cần index title thì báo leader. NFR-P04 p95 dưới 200 ms trên 1.000 ghi chú tự soạn.
- Testcontainers cho thấy Flyway cài pg_trgm trong schema learning, khác search_path của kết nối ứng dụng. Repository tra schema từ pg_extension/pg_namespace và quote identifier để gọi similarity đúng chỗ, không giả định learning/public, không đổi search_path, extension hoặc migration. Giá trị tìm kiếm vẫn bind tham số.
- GlobalExceptionHandler cho trả details riêng với `LIBRARY_LIMIT_REACHED` và `NOTE_DUPLICATE`; không cho extra ghi đè error_code/message/request_id. Matcher LEARNER cho /api/notes và /api/notes/**, CSRF theo cơ chế hiện có.
- T-09: khi khôi phục ghi chú có lexemeId trùng một ghi chú chưa xoá khác, trả 409 `NOTE_DUPLICATE`, không để lỗi unique thành 500; dùng cùng advisory key.
- T-15: SCR-025 nhận 409 `NOTE_DUPLICATE` khi lưu vào sổ tay thì mở ghi chú `details.noteId` để sửa. T-08 không sửa FE.

## 2026-11-04 — Leader duyệt 014/T-07

- Base: `origin/main` `82a6b3b`, merge PR #87 (T-06); leader xác nhận full verify PASS tại `82a6b3b`. Allowlist 12 file, nhánh `spec/014-t07`, một conventional commit, PR Draft, không tự merge.
- `strokeData` là object mô tả nguồn, không phải payload nét: mọi `CHAR` trả `{"source":"hanzi-writer-data"}`, kể cả khi `strokeCount` null; `WORD`/`GRAMMAR` trả null. SCR-025 chỉ kiểm khác null rồi dùng `HanziCanvas` của 006 tự tải theo chữ. Backend không gọi CDN, không lưu/nạp dữ liệu nét hoặc thêm migration. Quyết định này làm rõ FR-007/AT-07, giữ nguyên hình dạng object/null của contract.
- Việc cho T-15: kiểm FE ẩn gọn khối hoạt hình khi CDN không có dữ liệu cho chữ đó; phần còn lại của chi tiết vẫn hiện. Đối chiếu thêm ID `int64` của API với `number` và kiểm tra safe integer trên FE; T-07 không sửa FE.
- `related`: lấy `RELATED` và `SYNONYM` theo cả hai chiều, bỏ trùng theo ID, sắp `id ASC`, tối đa 10. Không lấy `CONTAINS`; liên kết này dùng riêng cho `components` (chiều đi ra, không giới hạn) và `containedIn` (chiều đi vào, tối đa 10). Hai nhóm CONTAINS cũng sắp `id ASC`.
- `/radicals` không phân trang; chỉ đếm CHAR có bộ thủ, sắp `charCount DESC, radical ASC`. `/by-radical` cần ít nhất một bộ lọc; `radical` tối đa 10 ký tự, `strokes` nguyên 1–64; phân trang mặc định 0/20, size 1–50, kiểm sai trả 422 `VALIDATION_FAILED`. Thứ tự `stroke_count ASC NULLS LAST, frequency DESC, id ASC`.
- Chỉ fallback ±1 nét khi tổng số kết quả chính xác bằng 0 và có `strokes`; giữ bộ lọc bộ thủ, chỉ xét số nét trong 1–64, đặt `approximate=true`. Trang vượt cuối không kích hoạt fallback.
- Chi tiết tồn tại dùng đúng 2 SELECT: trường công khai và một truy vấn gộp các nhóm liên kết. Không N+1, không trả toàn bộ `extra`, `exercises` hoặc đáp án. Mảng rỗng trả `[]`. ID sai định dạng trả 404 `LEXEME_NOT_FOUND` trước truy vấn DB; ID không có trả cùng lỗi sau một SELECT. Endpoint chi tiết không có trường hợp 422 trong contract.
- Cả ba endpoint nằm trong `DictionaryController`, dùng chung `@ModelAttribute`/`LibraryRateLimiter` với `/search`, đếm đúng một lần trước kiểm tham số, kể cả 422/404. Không thêm matcher hoặc cấu hình; giá trị SQL được bind tham số, không thêm index/migration.
- `LEXEME_NOT_FOUND` thêm ngay trước `INTERNAL_ERROR` theo luật L2. NFR-P02: p95 chi tiết dưới 150 ms trên 5.000 mục từ tự soạn. Khi gate xanh, leader cho phép đánh ✅ T-06 kèm ghi chú `leader full verify PASS tại 82a6b3b` và ✅ T-07 trong cùng PR.
- T-03, T-13, T-14 tiếp tục gác theo phân công của leader.

## 2026-11-04 — Leader duyệt 014/T-06

- Base lúc bắt đầu: `origin/main` `ee74b24`, remote `TriNM2505/CNHSK`; đã cập nhật nhánh lên `7f6e615` (PR #86). `010/T-10` đã merge qua PR #66 (`c3a5fc7`). Bản kế hoạch trước dùng base `a740027`; đã đối chiếu các matcher knowledge-tree/exam/placement và khối cấu hình placement trên main mới. Hai file dùng chung không đổi giữa `ee74b24` và `7f6e615`.
- Khách: 60 lượt/giờ/IP. Mọi tài khoản đã xác thực: 600 lượt/giờ theo `userId`, không phân biệt role. Bộ đếm Redis nguyên tử, cửa sổ giờ UTC, TTL tới hết giờ; mỗi request đếm một lần trước kiểm tra tham số, kể cả 422. Redis lỗi vẫn cho tra, log WARN không chứa query, IP hoặc thông tin kết nối.
- IP lấy từ `getRemoteAddr()`, không đọc `X-Forwarded-For`. Khi triển khai sau reverse proxy, leader theo dõi backlog cấu hình `server.forward-headers-strategy`; T-06 không thay đổi cấu hình proxy.
- Fallback nhiều chữ Hán: bỏ chữ trùng, giữ thứ tự xuất hiện đầu tiên; ví dụ `人人` chỉ trả mục từ `人` một lần.
- `LibraryConfig` chỉ có `guest-lookups-per-hour` và `authenticated-lookups-per-hour`; cả hai bắt buộc, phải dương và kiểm khi khởi động. Các hạn mức dịch/ôn/cache do task sử dụng tự thêm.
- T-06 dùng nguyên contract, `PinyinUtils`/`TextNormalizer` và schema hiện có; query được bind tham số, wildcard LIKE được escape. Phân trang tại DB; không thêm mã lỗi.
- Allowlist 16 file; gate JDK 17, DEBUG=false, Docker và Maven verify. Nếu gate xanh, leader cho phép đánh ✅ T-06 trong cùng PR. Chỉ lỗi thuộc nhóm môi trường đã biết (FR023 p95, T08_QUESTION_CONFIRM_BENCHMARK, PEXPIRETIME, CapturedOutput) được báo số đo và mở PR theo ngoại lệ leader duyệt; không hạ ngưỡng hoặc skip test.

## 2026-11-04 — Leader duyệt 014/T-02

- SCR-024/025 là hai màn thật đầu tiên của library; dùng API client T-01, preview/test bằng dữ liệu tự soạn. Nghiệm thu nối backend thật ở T-15.
- Đã đăng nhập nhưng không có `LEARNER`: ẩn thao tác lưu và hiện **"Cần quyền học viên để lưu"**. Khách thấy **"Đăng nhập để lưu"**; chỉ `LEARNER` gọi API sổ tay/bộ thẻ.
- Hoạt hình chỉ hiện với mục từ `CHAR` có `strokeData != null`. Dùng nguyên `HanziCanvas` và loader dữ liệu của 006; không truyền payload nét vào canvas. `onDataError` ẩn riêng khối hoạt hình, phần còn lại của trang vẫn hiển thị. Chỉ xem/hoạt hình, không gọi quiz, ghi lượt luyện hoặc đổi mastery.
- Không đổi contract hay thêm bộ lọc HSK khi API chưa hỗ trợ. Giữ truy vấn/bộ lọc/trang khi đi sang chi tiết rồi quay về.
- Spec 013 và 015 tạm gác; 014/T-13, T-14 cũng gác theo phân công mới. Nút gợi ý dịch của `LEARNER` chỉ điều hướng tới route có sẵn; T-02 không gọi dịch hoặc entitlement.
- Leader cho phép đánh ✅ T-02 trong cùng PR; allowlist 14 file, một conventional commit, PR Draft, không tự merge.

## 2026-11-03 — Leader duyệt điều chỉnh 014/T-01

- Spec §0.4 và T-01 ghi nhầm `shared/auth/navigation.ts`: file này chỉ xử lý đích sau đăng nhập; menu thực tế ở `shared/layout/navigation.ts`.
- T-01 chỉ thêm route, catalog màn và preview; không sửa `shared/auth/navigation.ts`, `shared/layout/navigation.ts`, `LearnerLayout.tsx`, `PublicLayout.tsx` hoặc `Layouts.test.tsx`.
- Menu học viên chuyển sang PR riêng **điều hướng học viên chung**, sau khi các T-01 của 011, 012, 014, 015 merge; leader giao riêng.
- `screens.ts` dùng `{} satisfies ScreenCatalog`. Không đăng ký `ScreenUnderConstructionPage`: `ScreenRoute` đã tự hiển thị màn tạm cho SCR chưa có page. T-02–T-05 đăng ký page thật.
- Ghi nhận cùng lỗi đường dẫn ở 013/T-01: khi làm mục điều hướng ADMIN, sửa `shared/layout/navigation.ts` theo phân công của leader.
- Leader cho phép cập nhật ✅ T-01 trong PR này; một commit, PR Draft, không tự merge.

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md`, constitution v1.4.1 (BUS-01/03/04/09/10, HR-07, AC-09), migration V1 (`lexemes`, `lexeme_links`, `collections`, `collection_items`, `user_progress`, index trigram), UC-056…UC-068 (`use-cases-05`), `screen-fields.md` SCR-024…SCR-029, `SecurityConfig` (`/api/public/**`), plan `261102-2104` phase 6, 7, spec 011 (FR-019 FSRS), spec 013 (FR-020/FR-021 `EntitlementApi`).

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Ôn flashcard | **Trong SCR-029, 4 nút** (Quên/Khó/Được/Dễ), dùng `FsrsScheduler` 011, ≤ 100 thẻ/buổi, không đổi mastery |
| Q2 | Dịch câu SCR-026 | **Làm bằng Claude**, trừ lượt `TRANSLATE` qua `EntitlementApi` 013, hoàn khi lỗi |
| Q3 | Giới hạn tra cứu | **GUEST 60/giờ/IP**, LEARNER 600/giờ, cấu hình được |
| Q4 | Tra bộ thủ + số nét | **Làm bộ lọc đơn giản** |
| Q5 | Bộ từ chủ đề | **Có, chép từ chủ đề 003** |
| Q6 | Ghi chú đã xoá | **Khôi phục trong 30 ngày** |
| T1 | Lịch sử dịch | **Không lưu, chỉ cache** 7 ngày |
| T2 | Model dịch | **`claude-haiku-5-5`** |
| D | Mặc định | **Duyệt**: GUEST thấy đủ trường (chỉ ẩn nút lưu); không tìm thấy → 200 rỗng; dữ liệu riêng → 403; một sổ tay mặc định; thùng rác không có job xoá hẳn; chia sẻ bộ là V2; dịch theo UC-060; không migration; FE trước |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| V1 | UC-066 3 mức tự đánh giá ↔ SCR-013 4 mức | Q1: 4 mức |
| V2 | Giới hạn khách: UC-056 theo giờ (ví dụ 50) ↔ UC-132 theo ngày | Q3 |
| V3 | UC-056 "kết quả rút gọn" cho khách không định nghĩa | Đủ trường |
| V4 | UC-056 404 ↔ UC-132 200 khi không có kết quả | 200 rỗng |
| V5 | UC-064 `NOTE_NOT_FOUND` 404 ↔ `BUS-01` 403 | 403 `COLLECTION_NOT_OWNED` |
| V6 | UC-133 khôi phục ghi chú, thời hạn chưa duyệt | Q6 30 ngày — ghi RFC |
| V7 | Plan phase 6 "không dịch câu (tốn phí)" | Q2 — sửa plan phase 6 |
| V8 | Thẻ flashcard và mục từ 011 cùng một từ → hai lịch ôn | Chấp nhận (SCR-029) |
| V9 | UC-060 dùng bảng `feature_usage`, `translation_history` không có trong V1 | Dùng `EntitlementApi` + cache Redis (T1) |

### Còn mở

- Plan phase 6 cần sửa theo Q2 (có dịch), Q1 (ôn FSRS 4 mức), Q6 (thùng rác) — hỏi leader trước khi sửa.
- Thêm vào RFC chung: V6 (thời hạn 30 ngày).
- `ANTHROPIC_API_KEY`: leader đặt trong `.env` khi chạy dịch/trợ lý thật (nhắc cùng 015).

# Câu hỏi và quyết định — Spec #019

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Quiz có cập nhật mastery không (UC-080 BR-080-2: không) | Không, theo UC | ✅ Chốt |
| Q2 | Bộ câu mỗi lượt | Ngẫu nhiên từ kho APPROVED của KID chủ đề, cân theo dạng câu | ✅ Chốt |
| Q3 | Số câu và ngưỡng | 10 câu; ẩn quiz nếu < 10 câu; < 2 giây/câu → nghi vấn; chỉnh ở 078 | ✅ Chốt |
| Q4 | Module | `learning` (dùng engine trực tiếp) thay vì `community` như UC | ✅ Leader chốt 2026-11-13 |
| Q5 | Chặn trợ lý | Trong lượt tính hạng; không chặn lượt làm lại | ✅ Leader chốt 2026-11-13 |
| Q6 | Lượt đầu bị nghi vấn | Vẫn chiếm "lần đầu", không làm lại để tính hạng | ✅ Leader chốt 2026-11-13 |

## Ghi chú sửa plan
- Plan phase 3 cũ ghi quiz "góp minh chứng mastery" → **sai so với BR-080-2**; spec theo UC (Q1).
- Spec 017 đã sửa `ref_type` lượt quiz thành `'QUIZ'` (engine ghi `ref_type = PracticeSource.name()`).

## Dữ kiện đã kiểm trên main `2735ac8`
- `PracticeService` tạo `Attempt(userId, source.kind().name(), source.name(), refId, …)` → `ref_type` = tên source.
- `AttemptKind` đã có `QUIZ`; `PracticeSource` chưa có `QUIZ`.
- `uq_quiz_first(user_id, ref_id) WHERE kind='QUIZ' AND status='SUBMITTED'`.
- `EvidenceAllocator.contextOf` mặc định `null` + log WARN cho source lạ.
- `AttemptRepository.existsActiveTest` hiện xét `MOCK_EXAM`, `PLACEMENT`, `TOPIC_TEST`.
- `topics.knowledge_point_id` (V5), `question_knowledge_points.role` PRIMARY/SECONDARY.

## 2026-11-14 · T-01 — G0 đã duyệt, chờ review

- Base: `origin/main` `134b2588cb88018653509eaa905848605cf09d87`, fetch và kiểm lại ngay trước khi tạo nhánh `spec/019-t01`.
- Leader duyệt allowlist 8 file, gồm enum engine/API, enum contract 003, ba test hiện có và hai file theo dõi của 019; giữ lượt sửa L4 cho `PracticeSource` đến khi T-01 merge.
- Chỉ nối `QUIZ`, `QUIZ_RETRY` vào cuối các enum, giữ thứ tự nguồn cũ. `CONTEST` để 021 thêm riêng.
- Chính sách: `QUIZ` có kind `QUIZ`; `QUIZ_RETRY` có kind `PRACTICE`; cả hai `DEFERRED`, TTL 30 phút, `allowSkip=true`, `maxScoreFromServed=false`.
- Kiểm kê bắt buộc: `PracticeCoreTest` đối chiếu enum OpenAPI 003; `PracticeApiTest` đối chiếu enum boundary với engine. Bổ sung kiểm tra chính sách, kind/refType/thời hạn khi start, round-trip start/view và không lộ đáp án trong view DEFERRED.
- T-01 cung cấp nguồn engine; không xác nhận AT-05/06 toàn luồng nộp (T-04), hoặc AT-11/12 về mastery/trợ lý (T-02).

### G2 · 2026-11-14

Maven Wrapper 3.9.16, JDK 17.0.12 (`JAVA_HOME` chỉ đặt trong phiên), `DEBUG=false`.
Docker Desktop đã bật, `docker info` thành công (server 29.9.2); integration test dùng PostgreSQL/Redis/Mailpit của Testcontainers, không dùng Neon.

| Bước | Lệnh | Tests | F/E/S | Exit |
|---|---|---:|---|---:|
| Targeted | `.\mvnw.cmd -B "-Dtest=PracticeCoreTest,PracticeServiceTest,PracticeApiTest" test` | 118 | 0/0/0 | 0 |
| Regression | `.\mvnw.cmd -B "-Dtest=Practice*Test,ServedItemViewTest,ChoiceItemFactory*Test,PinyinGraderTest,GlobalExceptionHandlerTest,*ContractTest" test` | 410 | 0/0/0 | 0 |
| Module boundary | `.\mvnw.cmd -B "-Dtest=ModuleBoundaryTest" test` | 9 | 0/0/0 | 0 |
| Full verify | `.\mvnw.cmd -B verify` | 4673 | 0/0/0 | 0 |

- Full verify chạy 19 phút 41 giây; JaCoCo LINE **96,805%** (12.574 covered / 12.989 total), `jacoco:check` pass ngưỡng 80%.
- Không bỏ qua test hoặc áp ngoại lệ để đạt kết quả. Không phát sinh failure/error thuộc PEXPIRETIME, CapturedOutput, FR023, NFR_P04, NFR_P02; kết quả chạy này không thay thế việc review benchmark 011 của leader.
- `git diff --check`: exit 0. Cả 8 file thay đổi không có UTF-8 BOM. Log và báo cáo kiểm tra nằm trong `target/g2/` cùng `target/site/jacoco/` (không commit).
- Self-check: đúng allowlist; giữ thứ tự enum cũ; không thêm dependency/schema/migration; không đổi auth/security; không log đáp án/PII/token; không đụng thay đổi có sẵn trong checkout main. T-01 chờ leader review và merge.

## 2026-11-14 · T-02 — G0 đã duyệt, chờ review

- Base: `origin/main` `61d6d5c771052e3b85804b45bc01a59c493f433c` (T-01 đã merge #157); fetch và kiểm lại `EvidenceAllocator`/`AttemptRepository` trước khi tạo `spec/019-t02`.
- Leader duyệt allowlist 7 file và đã phối hợp với Dũng (011), Đạt (012/015); không cần chờ phản hồi thêm.
- `QUIZ`/`QUIZ_RETRY` được loại tường minh khỏi minh chứng mastery, không đi vào WARN nguồn lạ. Giữ `PLACEMENT`/`TOPIC_TEST` → nhãn minh chứng `QUIZ` như cũ.
- `existsActiveTest` chỉ thêm nguồn `QUIZ`, giữ kiểm tra user/status/hạn phiên; caller là `ActiveTestApi` → guard trợ lý.
- AT-11: nộp 10 câu APPROVED qua engine thật, không suspicious; kiểm tra cả user chưa có tiến độ và user có minh chứng đối chứng. So sánh mọi cột `user_progress` bằng JDBC trước/sau; `attempt_items` vẫn lưu nhưng không được allocate/reallocate thành minh chứng mastery. Kiểm tra raw `served_at`/`submitted_at`/`duration_ms` bằng JDBC.
- AT-12: HTTP trợ lý kiểm tra lượt đầu/retry, trạng thái đã kết thúc, biên hết hạn, khác chủ sở hữu, đồng thời có lượt đầu và retry; chặn không gọi AI hoặc trừ hạn mức. Giữ test hồi quy `PLACEMENT` và `TOPIC_TEST` đang mở.
- Fixture mới tự soạn; cleanup theo đúng user/ID đã tạo, không xoá dữ liệu suite khác. Phần ẩn panel FE thuộc T-07.

### G2 T-02 · 2026-11-14

JDK 17.0.12 (`JAVA_HOME` đặt trong phiên), Maven Wrapper 3.9.16, `DEBUG=false`.
`docker info` thành công (server 29.9.2); integration test dùng Testcontainers, không dùng Neon.

| Bước | Tests | F/E/S | Exit |
|---|---:|---|---:|
| Targeted lần đầu | 27 | 0/4/0 | 1 |
| Targeted sau sửa fixture | 27 | 0/0/0 | 0 |
| Regression liên quan | 506 | 0/0/0 | 0 |
| ModuleBoundaryTest | 9 | 0/0/0 | 0 |
| Full `.\mvnw.cmd -B verify` | 4723 | 0/0/0 | 0 |

- Targeted lần đầu lỗi setup trong test mới do thiếu `topics.code` bắt buộc từ V3. Đã bổ sung code duy nhất trong đúng file test được duyệt và chạy lại; không đổi schema hoặc production để xử lý lỗi này.
- Targeted: `.\mvnw.cmd -B "-Dtest=EvidenceAllocatorTest,QuizMasteryIsolationIntegrationTest,AssistantControllerIntegrationTest" test`.
- Regression: `.\mvnw.cmd -B "-Dtest=Mastery*Test,EvidenceAllocatorTest,QuizMasteryIsolationIntegrationTest,FrontierServiceTest,RecommendationService*Test,Assistant*Test,TestInProgressGuardTest,HelpLibraryTest,PiiMaskerTest,Practice*Test,GlobalExceptionHandlerTest,*ContractTest" test`.
- Boundary: `.\mvnw.cmd -B "-Dtest=ModuleBoundaryTest" test`.
- Full verify: 19 phút 02 giây; JaCoCo LINE **96,8337%** (12.661 covered / 13.075 total), `jacoco:check` pass ngưỡng 80%.
- Không bỏ qua test hoặc áp ngoại lệ. Không phát sinh failure/error thuộc PEXPIRETIME, CapturedOutput, FR023, NFR_P04, NFR_P02; kết quả này không thay thế review benchmark 011 của leader.
- `git diff --check`: exit 0. Đúng 7 file allowlist, UTF-8 hợp lệ, không BOM. Log/báo cáo ở `target/g2/`, coverage ở `target/site/jacoco/` (không commit).
- Self-check: giữ ánh xạ nguồn cũ và điều kiện user/status/expiry; không thêm dependency/schema/migration; không đổi auth/security; không log đáp án/PII/token; không đụng thay đổi sẵn có ở checkout main. T-02 chờ leader review/merge.
## 2026-11-14 · T-03 — G0 đã duyệt; G2 được chấp nhận ngoại lệ, chờ review

- Base: `origin/main` `5e5695ccf4f6aab2e1b764297b0e22e2a63cfe81`, fetch và kiểm tra ngay trước khi tạo `spec/019-t03`. T-03 độc lập với T-02; không rebase theo chỉ đạo leader.
- Leader duyệt allowlist 11 file và lượt sửa `SecurityConfig.java`/`application.yaml`; xung đột ở file chung do leader xử lý khi merge.
- Đếm câu APPROVED có liên kết PRIMARY với chính `topics.knowledge_point_id`. Test chủ đề 11 câu hợp lệ cộng một câu PRIMARY ở KID khác/SECONDARY ở KID của chủ đề vẫn bị ẩn khi cấu hình 12.
- `questionCount` trong DTO và ngưỡng hiện chủ đề cùng đọc `cnhsk.quiz.question-count`; mặc định 10, test override 12. Hai cấu hình còn lại chuẩn bị cho T-04, chưa đổi engine ở T-03.
- Một statement SQL tính eligibility, total, page và điểm cá nhân; trang vượt cuối giữ total. Lượt đầu suspicious vẫn có trong summary; chỉ đọc lịch sử SUBMITTED đúng user/kind/ref_type.
- Test SELECT count là assert cứng (=1). Bài đo 300 chủ đề chỉ ghi p95 và assert query count, không có gate thời gian dễ chập chờn.
- Giữ test bearer LEARNER và cookie; không sửa YAML contract ở T-03. Ghi nhận file contract trên base này hiện có cả cookieAuth và bearerAuth; so khớp contract đầy đủ dành cho T-08.
- Fixture tự soạn, transaction rollback trả dữ liệu về trạng thái trước test; không dọn dữ liệu của suite khác. Thông báo giao diện FR-005 thuộc T-06; bắt đầu quiz/QUIZ_NOT_AVAILABLE thuộc T-04.

### G2 T-03 · 2026-11-14 — chưa đạt

JDK 17.0.12, Maven Wrapper 3.9.16, `DEBUG=false`; `docker info` thành công (29.9.2), integration test dùng Testcontainers.

| Bước | Tests | F/E/S | Exit |
|---|---:|---|---:|
| Targeted lần đầu | 48 | 0/1/0 | 1 |
| Targeted sau sửa fixture | 48 | 0/0/0 | 0 |
| Regression security/contract/kiểm kê | 232 | 0/0/0 | 0 |
| ModuleBoundaryTest | 9 | 0/0/0 | 0 |
| Full `.\mvnw.cmd -B verify` | 4796 | 1/0/0 | 1 |

- Targeted lần đầu lỗi fixture REJECTED thiếu `review_reason` theo `ck_q_reject`. Đã sửa đúng file test được duyệt và chạy lại; không đổi schema hoặc production để xử lý lỗi fixture.
- Targeted: `.\mvnw.cmd -B "-Dtest=QuizConfigTest,QuizCatalogIntegrationTest,QuizSecurityConfigIntegrationTest" test`.
- Regression: `.\mvnw.cmd -B "-Dtest=*SecurityConfigIntegrationTest,SecurityConfigIntegrationTest,GlobalExceptionHandlerTest,*ContractTest" test`.
- Boundary: `.\mvnw.cmd -B "-Dtest=ModuleBoundaryTest" test`.
- Full verify chạy 17 phút 47 giây. Failure duy nhất: `AssistantPromptBuilderTest.FR007_learnerRouteDefinitionsAllHaveTrustedNames` tại `SCR-009`.
- Lỗi nền: 020/T-06 (commit `44f3f2d` trên base) thêm route LEARNER `SCR-009` nhưng `AssistantPromptBuilder.SCREEN_NAMES` chưa có "Cài đặt nhắc học". Builder, test và `routeAccess.ts` không có diff so với `origin/main` của base; T-03 không sửa các file này.
- Đã đề xuất mở rộng thêm đúng `AssistantPromptBuilder.java`; leader chọn **giữ 11 file, dừng tại G2**. Không sửa trợ lý, không chạy lại G2 sau lựa chọn này và không thực hiện G3.
- Assert SELECT count = **1** pass cho 0/1/10/50 chủ đề fixture và từng mẫu đo 300 chủ đề. p95 chỉ ghi số đo, không áp ngưỡng thời gian: targeted cuối **73,888 ms**, full suite **12,643 ms**; mỗi lượt 300 chủ đề fixture, 0 chủ đề đủ điều kiện có sẵn, 20 mẫu sau warmup.
- Maven dừng ở Surefire do failure, nên JaCoCo report/check chưa chạy; không báo coverage đạt gate 80% hoặc full verify xanh. Không bỏ qua test, không áp ngoại lệ hoặc đổi test kiểm kê để che lỗi.
- Không phát sinh failure/error thuộc các ngoại lệ đã biết PEXPIRETIME, CapturedOutput, FR023, NFR_P04, NFR_P02; review benchmark 011 vẫn thuộc leader.
- `git diff --check`: exit 0; đúng 11 file allowlist, UTF-8 hợp lệ, không BOM; YAML contract giữ nguyên. Log/báo cáo kiểm tra nằm trong `target/g2/` (không commit).
- Self-check: không thêm dependency/schema/migration/ErrorCode; chỉ thêm hai matcher đã duyệt và khối `cnhsk.quiz.*`; không log đáp án/PII/token; không đụng thay đổi có sẵn ở checkout main. Chưa commit/push/mở PR.

### G2 T-03 · 2026-11-14 — leader chấp nhận ngoại lệ, cho phép G3

- Leader xác nhận SCR-009 là lỗi nền trên base cũ, đã sửa tại [88e0155](https://github.com/TriNM2505/CNHSK/commit/88e01556e6c4990336a00483303c227295be0e76), có trên main `ba5bc054bf2ba101375776b66411a699c1b5060a`. Đã fetch để xác minh commit; nhánh task vẫn giữ base `5e5695c`, không rebase và không sửa file trợ lý.
- Theo lệnh leader: `.\mvnw.cmd -B verify "-Dmaven.test.failure.ignore=true"`, JDK 17.0.12, Docker/Testcontainers, `DEBUG=false`.
- Kết quả: **4796 tests, 1 failure, 0 error, 0 skip; exit 0**, 17 phút 34 giây. Đã đọc toàn bộ XML Surefire để xác nhận failure duy nhất là `AssistantPromptBuilderTest.FR007_learnerRouteDefinitionsAllHaveTrustedNames`, nội dung `[SCR-009]`; không dùng exit 0 để suy ra toàn bộ test pass.
- JaCoCo LINE **96,8166%**: **12.743 covered / 13.162 total**, 419 missed; vượt ngưỡng 80%. Log có dòng nguyên văn: **`All coverage checks have been met.`**
- Catalog: **1 SELECT/request** là assert cứng, pass; p95 của 300 chủ đề fixture trong lượt này **9,162 ms** (0 chủ đề đủ điều kiện có sẵn, 20 mẫu sau warmup), chỉ ghi số đo, không có gate thời gian.
- Ngoại lệ SCR-009 được leader chấp nhận riêng để thực hiện G3; leader sẽ chạy full verify trên main mới khi merge. Các failure/error khác và skip không được chấp nhận trong lượt này, và không phát sinh.
- Log mới: `target/g2/verify-coverage.log`; XML: `target/surefire-reports/`; coverage: `target/site/jacoco/`. Báo cáo full verify không ignore trước đó được giữ lại, không tô xanh failure SCR-009.

### G0/G1 T-04 · 2026-11-14 — leader duyệt 18 file

- Base `02bd90d787237e326593f2555477a6848b658fba`, nhánh `spec/019-t04`; đã fetch và kiểm engine/Attempt trước khi tạo nhánh. T-01/T-02/T-03 đã merge. Leader phối hợp người giữ 012; không sửa PracticeSource, SecurityConfig, application.yaml, contract hoặc migration.
- Chốt MVP: chỉ MULTIPLE_CHOICE/TRUE_FALSE không audio/image, tái dùng `QuestionItemFactory` của 012. Điều kiện qtype/media `SUPPORTED` trong QuizRepository dùng chung cho catalog và tập picker; luôn PRIMARY đúng KID của chủ đề, APPROVED. Mở rộng qtype/media để sau. Picker còn loại phương án không hợp lệ theo factory, không sửa dữ liệu kho để đủ câu.
- `itemId` = ordinal `no` 1..N trong snapshot, dùng trực tiếp cho answers/finish engine. API chỉ chiếu trường công khai, không trả đáp án/giải thích trước nộp.
- Quota dùng `shared.security.RateLimiter.increment`, key `quiz:start:{userId}`, cửa sổ một giờ tính từ hit đầu, chung QUIZ/QUIZ_RETRY, tối đa 30 lần. Chỉ tăng khi cần tạo mới và đã đủ câu; resume miễn lượt. Không đếm DB, không thêm fallback khi Redis lỗi; lỗi propagate theo RateLimiter hiện có.
- Start giữ advisory transaction lock theo user (namespace `quiz:start`), tìm phiên chưa hết hạn qua scalar JDBC và `FOR NO KEY UPDATE`, rồi dùng snapshot đang có trước khi kiểm lại kho. Submit chỉ giữ advisory theo user/topic (`quiz:submit`) sau khoá dòng attempt của engine; không lấy khoá start nên không tạo vòng chờ với start.
- Hook mặc định không đổi hành vi handler cũ: `sessionPolicy()` trả null; `prepareSubmission()` trả null. Quiz đăng ký hai bean handler cùng policy. Chuẩn bị nộp chạy trước chấm và phát sự kiện từng câu, để trường hợp thua đua dùng đúng nguồn QUIZ_RETRY trong mọi event/result.
- Submit QUIZ kiểm lịch sử sau advisory lock, không lọc suspicious, loại chính session hiện tại. Nếu đã có lượt đầu SUBMITTED thì gọi duy nhất `Attempt.convertQuizToRetry()` (chỉ IN_PROGRESS + kind/ref_type QUIZ), trước mọi flush SUBMITTED. Giữ `uq_quiz_first` làm chốt cuối; không catch unique violation để tiếp tục transaction đã hỏng.
- Bỏ `updatable=false` riêng hai cột ORM kind/ref_type để Hibernate lưu chuyển đổi đã duyệt trong cùng transaction. Không thêm setter chung, không đổi cột/schema; các trường user/refId/served_items giữ bất biến. Phương thức chuyển từ chối lượt đã kết thúc, nguồn khác hoặc chuyển lặp.
- `SubmissionPolicy(100)` chuẩn hoá điểm correctCount × 100 / tổng câu đã phát, maxScore=100 trước study event, finish hook và SessionFinishedEvent. Câu bỏ qua/thiếu vẫn thuộc mẫu số. TTL lấy config server; client ttlOverride của nguồn cố định vẫn bị từ chối, GAME_ROUND không đổi.
- Suspect dùng `submitted_at - served_at` server, so sánh nghiêm ngặt với min-seconds × N; rules từng câu minDurationMs=0 để không tin duration client. Lượt đầu suspicious vẫn tiêu thụ lượt đầu.
- Integration fixture commit riêng cho REQUIRES_NEW của engine; tự dọn theo user/question/topic/KID của test, không FLUSHDB, không dọn dữ liệu suite khác. Dữ liệu câu tự soạn.
- Test FR/AT: cân 8+8→5+5 và bù dạng thiếu; catalog 12 APPROVED nhưng chỉ 8 hỗ trợ bị ẩn/POST 422; create201/resume200 snapshot; cấu hình N=12/TTL=7; điểm 80/100, retry100 giữ first80; 12 giây suspicious/20 giây hợp lệ và JDBC thô; abandon/expire; hai submit first chỉ một QUIZ, một QUIZ_RETRY với event/source/score nhất quán; start-start/start-finish không deadlock; 31 lần start→429, resume không tính; ownership/rollback, CSRF và bearer; mastery/assistant hồi quy.

### G2 T-04 · 2026-11-14 — kiểm tra trước full verify

- Môi trường: JDK 17.0.12 đặt JAVA_HOME trong phiên, Maven Wrapper 3.9.16, Docker Desktop 29.9.2 (`docker info` thành công), `DEBUG=false`. Không đổi cấu hình hệ thống hoặc pom; DB/Redis/Mailpit của Testcontainers.
- Targeted lần đầu: **107 tests, 0 failure, 2 error, 0 skip; exit 1**. Mockito trả Duration.ZERO cho hook Duration mới; đổi thành SessionPolicy nullable để mock/handler cũ giữ mặc định no-op. Lỗi thứ hai là unfinished stubbing do test security gọi mock Clock trong thenReturn; sửa fixture dùng Instant cố định. Cả hai sửa trong allowlist, không bỏ test.
- Targeted chạy lại: **107/0/0/0, exit 0**. Bổ sung xử lý topicId sai kiểu và test Redis lỗi propagate; targeted cuối **108/0/0/0, exit 0** (`target/g2/targeted-complete.log`).
- Regression: **930 tests, 0 failure/error/skip; exit 0** (`target/g2/regression.log`). Chạy toàn bộ test practice/exam/vocab/mastery/assistant/API/quiz, các ContractTest và QuizSecurityConfigIntegrationTest/GlobalExceptionHandlerTest. Lượt này cũng xác nhận DTO/schema QuizSession/QuizItem/QuizOption sau khi khai báo đủ required/nullability; không sửa YAML contract.
- ModuleBoundaryTest: **9/0/0/0, exit 0** (`target/g2/boundary.log`).
- Catalog trong regression: assert cứng **1 SELECT/request**, p95 **16,343 ms** với 300 chủ đề fixture, 0 chủ đề đủ điều kiện có sẵn, 20 mẫu sau warmup. Chỉ ghi thời gian, không gate NFR theo tốc độ máy.
- Đúng 18 file allowlist; UTF-8 hợp lệ, không BOM/trailing whitespace; `git diff --check` exit 0. Full verify đang chạy bằng lệnh thường, không kế thừa ngoại lệ/ignore của T-03.

### G2 T-04 · 2026-11-14 — đạt, chuyển G3 chờ review

| Bước | Tests | F/E/S | Exit |
|---|---:|---|---:|
| Targeted cuối | 108 | 0/0/0 | 0 |
| Regression liên quan | 930 | 0/0/0 | 0 |
| ModuleBoundaryTest | 9 | 0/0/0 | 0 |
| Full `.\mvnw.cmd -B verify` | 4937 | 0/0/0 | 0 |

- Full verify chạy **18 phút 22 giây**, không ignore failure/skip test. Đối chiếu **316 XML Surefire**: tổng 4937 tests, không failure/error/skip, khớp log Maven và exit code thật (`target/g2/verify.exit`).
- JaCoCo LINE **96,9087%**: **13.135 covered / 13.554 total**, 419 missed; log có **`All coverage checks have been met.`** Báo cáo `target/site/jacoco/jacoco.xml`, log `target/g2/verify.log`.
- Catalog full suite: **1 SELECT/request** là assert cứng, p95 **15,317 ms** với 300 chủ đề fixture, 0 chủ đề đủ điều kiện có sẵn, 20 mẫu sau warmup. Không thêm test NFR phụ thuộc tốc độ máy.
- Không phát sinh failure/error ở PEXPIRETIME, CapturedOutput, FR023, NFR_P04, NFR_P02; không dùng các ngoại lệ để làm xanh build. SCR-009 đã pass trên base mới. Benchmark review của 011 vẫn thuộc leader, không được thay thế bởi kết quả này.
- Self-check PASS: không thêm dependency/schema/migration; không sửa enum/contract hoặc cấu hình bảo mật; module boundary đạt; điểm/thời gian từ server, chỉ dùng BIGINT cho điểm; SQL tham số hoá, không log đáp án/PII/token; cleanup test theo ID riêng; 18 file đúng allowlist, không BOM, `git diff --check` sạch. Không sửa thay đổi có sẵn trong checkout main, không push/merge/rebase main.
- T-04 chuyển **CHỜ REVIEW**, G3 theo duyệt của leader: một conventional commit đúng từng file, push nhánh `spec/019-t04` và Draft PR; leader review/merge.

### G0/G1 T-05 · 2026-11-14 — leader duyệt có điều kiện, dừng ở G2

- Base `13f1d96020e143f901b17012bbb9a635cc4ebef4`, nhánh `spec/019-t05`; đã fetch/kiểm ErrorCode và package leaderboard trước khi tạo nhánh. T-04 đã MERGED (#168); giữ nguyên thay đổi có sẵn của 003 ở checkout main.
- **Q1 — DUYỆT:** tạo interface `learning.leaderboard.LeaderboardService`, không annotation/component/bean; chỉ `Optional<Rank> rankOf(long userId, long topicId)` và `record Rank(long rank, long total) {}`. Không tạo production implementation/stub. Leader báo Dũng của 017/T-05 cung cấp implementation sau. `ObjectProvider<LeaderboardService>` giữ phụ thuộc bean tuỳ chọn.
- **Q2 — DUYỆT:** attempt không tồn tại hoặc không thuộc caller → 403 ATTEMPT_NOT_OWNED; của caller nhưng không phải QUIZ/QUIZ_RETRY → 404 QUIZ_ATTEMPT_NOT_FOUND. Mã mới ngay trước INTERNAL_ERROR theo base; cập nhật kiểm kê GlobalExceptionHandlerTest. Kiểm ownership trước nguồn/trạng thái và trước đọc/chiếu đáp án. Quiz chưa SUBMITTED → 422 ATTEMPT_NOT_SUBMITTED.
- **Q3 — LEADER CHỈNH:** rankOf ném RuntimeException vẫn trả 200, firstAttempt giữ score/durationMs, rank/total=null; WARN chỉ sessionId/topicId, không exception/message/stack/PII. Lookup bắt buộc ngoài transaction đọc để tránh rollback-only/UnexpectedRollbackException.
- Triển khai transaction: entry point `NOT_SUPPORTED` tạm ngưng transaction caller; TransactionTemplate REQUIRES_NEW + readOnly đọc và chiếu immutable DTO. Sau khi template hoàn tất/commit mới lấy optional bean và gọi rankOf. Không chuyển entity/lazy association qua ranh giới này. Transaction riêng của 017 nếu có được tách khỏi phần đọc/caller.
- Lượt đầu suspicious không gọi rankOf (cả khi đang xem retry); không có bean hoặc lookup rỗng cũng giữ summary, rank/total=null. Không có lượt QUIZ/SUBMITTED thì firstAttempt=null. RankingEligible phản ánh nguồn đã lưu của lượt hiện tại; suspicious riêng như T-04.
- Đề/options/correct/explanation dùng served_items snapshot; lựa chọn và số đúng từ evidence theo ordinal no, câu thiếu/bỏ qua chosen=null và tính sai. Điểm/thời gian lấy từ lượt đã nộp; không dùng config hiện tại để chấm lại, không đọc kho câu để dựng lại đề.
- Chỉ đọc DB trong GET, không khoá/ghi attempt/mastery/event; batch evidence một lần. Không migration/dependency, không sửa engine, SecurityConfig/application.yaml hoặc YAML contract. Full contract parity thuộc T-08.
- Test G2 gồm real context không bean; bean mock có rank/rỗng/RuntimeException; lỗi lookup chạy transaction REQUIRED thật để bắt rollback-only; caller transaction tạm ngưng rồi phục hồi; WARN không chứa chi tiết lỗi; ownership/422/404; snapshot sau sửa/ARCHIVED; nullable JSON; first/retry/suspicious/đổi config; số query không tăng theo số câu; security và ErrorCode inventory.
- Fixture tái dùng QuizIntegrationFixture đã merge ở T-04, cleanup riêng theo ID, không sửa file fixture ngoài allowlist. **Dừng sau G2 báo leader, chưa commit/push/PR.**

### G2 T-05 · 2026-11-14 — trước full verify

- JDK 17.0.12 qua JAVA_HOME trong phiên, Docker Desktop 29.9.2 (`docker info` đạt), Testcontainers PostgreSQL/Redis/Mailpit; `DEBUG=false`, không sửa pom/cấu hình hệ thống, không dùng Neon.
- Targeted `QuizResultServiceTest,QuizResultIntegrationTest,QuizResultRankingIntegrationTest,QuizSecurityConfigIntegrationTest,GlobalExceptionHandlerTest`: **60 tests, 0 failure/error/skip; exit 0** ngay lượt đầu (`target/g2/targeted.log`).
- Regression toàn bộ practice/exam/vocab/mastery/assistant/API/quiz/leaderboard + ContractTest và security/kiểm kê liên quan: **983 tests, 0 failure/error/skip; exit 0** (`target/g2/regression.log`).
- ModuleBoundaryTest: **9 tests, 0 failure/error/skip; exit 0** (`target/g2/boundary.log`).
- Case Q3 pass: mock bean gọi TransactionTemplate REQUIRED thật rồi ném RuntimeException; GET vẫn 200, first score/duration giữ nguyên, rank/total=null; xác nhận trước lookup không có transaction đọc đang hoạt động. Caller transaction được tạm ngưng và phục hồi, không rollback-only. Unit test kiểm commit phần đọc xảy ra trước rankOf và WARN chỉ sessionId/topicId, không throwable/message nhạy cảm.
- Context không bean pass; suspicious first không gọi lookup cho cả first/retry. Ownership 403 trước non-quiz 404; IN_PROGRESS/ABANDONED/EXPIRED 422 không lộ đáp án. Snapshot sau sửa/ARCHIVED và đổi question-count vẫn đúng; ordinal/options/null JSON đúng; retry giữ first; lượt bị chuyển retry hiển thị đúng.
- GET không đổi score/snapshot/status/study_events/mastery. Với 5/30 câu, mỗi GET đều 2 statement Hibernate (attempt + batch evidence), ngoài các projection JDBC cố định cho topic/first; không tuyên bố tổng SQL là 2. Catalog hồi quy vẫn 1 SELECT/request, p95 300 chủ đề **19,092 ms** (20 mẫu, không gate thời gian).
- Allowlist khớp **12 file**, UTF-8 hợp lệ, không BOM; `git diff --check` exit 0. Engine, SecurityConfig/application.yaml và YAML contract không có diff. Full verify đang chạy với lệnh thường, không ignore failure.

### G2 T-05 · 2026-11-14 — ĐẠT, dừng theo leader

| Bước | Tests | F/E/S | Exit |
|---|---:|---|---:|
| Targeted | 60 | 0/0/0 | 0 |
| Regression liên quan | 983 | 0/0/0 | 0 |
| ModuleBoundaryTest | 9 | 0/0/0 | 0 |
| Full `.\mvnw.cmd -B verify` | 4990 | 0/0/0 | 0 |

- Full verify **18 phút 50 giây**, không ignore failure/skip. Đọc **324 XML Surefire**: tổng 4990 tests, 0 failure/error/skip, khớp log và exit code thật (`target/g2/verify.exit`).
- JaCoCo LINE **96,9349%**: **13.251 covered / 13.670 total**, 419 missed; log có dòng **`All coverage checks have been met.`** Log `target/g2/verify.log`, báo cáo `target/site/jacoco/jacoco.xml`.
- Các test Q3, không bean, suspicious, ownership/404/422 và snapshot đều pass trong full suite. WARN thử nghiệm chỉ có sessionId/topicId, không kèm chi tiết exception. Không phát sinh UnexpectedRollbackException hoặc failure/error thuộc PEXPIRETIME, CapturedOutput, FR023, NFR_P04, NFR_P02; không dùng ngoại lệ để làm xanh build. Benchmark review 011 vẫn thuộc leader.
- Catalog hồi quy giữ **1 SELECT/request**; p95 300 chủ đề trong full suite **13,261 ms**, 0 chủ đề đủ điều kiện có sẵn, 20 mẫu sau warmup; không thêm gate thời gian.
- Self-check PASS: đúng 12 file đã duyệt; UTF-8 hợp lệ, không BOM/trailing whitespace, `git diff --check` sạch; interface 017 đúng một method/record, không bean/production implementation/stub; không dependency/schema/migration, không sửa engine/security/config/contract; điểm/thời gian từ dữ liệu server, không log đáp án/PII/token; test cleanup theo ID riêng, không dọn suite khác.
- Đã ghi T-04 **MERGED (#168)** và trạng thái T-05. **Dừng ở G2 theo lệnh leader: chưa stage/commit/push/mở PR, không merge/rebase main.** HEAD vẫn là base `13f1d96020e143f901b17012bbb9a635cc4ebef4`; checkout main giữ nguyên thay đổi có sẵn ở `specs/003-vocab-topics/questions.md`.

### G3 T-05 · 2026-11-14 — leader duyệt

- Leader chấp nhận G2 4990 tests F0/E0/S0, JaCoCo LINE 96,9349%, đúng 12 file; cho phép stage từng tên file, commit `feat(learning): add topic quiz result endpoint`, push `spec/019-t05` và mở PR thường (không Draft).
- PR mô tả GET `/api/quiz-attempts/{sessionId}/result`, interface LeaderboardService để 017/T-05 cài đặt, mã QUIZ_ATTEMPT_NOT_FOUND và kết quả gate. Ghi rõ rankOf gọi **ngoài** transaction readOnly: entry point NOT_SUPPORTED tạm ngưng transaction caller; reads.execute REQUIRES_NEW/readOnly hoàn tất trước lookup. Lỗi transaction riêng của rankOf không làm rollback-only phần đọc/caller.
- Không đổi production/test sau G2; chỉ cập nhật trạng thái tài liệu trong hai file đã duyệt. T-05 chuyển CHỜ REVIEW; không merge, không rebase/pull main.
### G0/G1 T-06 · 2026-11-14 — leader duyệt có điều kiện

- Fetch ngay trước tạo nhánh: origin/main `13f1d96020e143f901b17012bbb9a635cc4ebef4`, nhánh `spec/019-t06`, worktree `C:\KLTN_FALL2026\CNHSK-019-t06`. #171 đang chờ leader verify/merge, không phụ thuộc T-06. Giữ nguyên thay đổi có sẵn của 003 ở checkout main; không pull/rebase main.
- Duyệt 22 file theo G0. Route `/community/quizzes` SCR-043 và `/community/quizzes/:topicId/play` SCR-044 đều LEARNER/LearnerLayout. SCR-044 là wrapper placeholder hiện có, đăng ký rõ trong registry; màn làm bài thật và SCR-045 để T-07. Thêm trusted name SCR-043 "Quiz theo chủ đề", SCR-044 "Làm quiz theo chủ đề" và test tên màn để không tái diễn lỗi kiểm kê #159.
- Menu desktop có nhóm Cộng đồng chỉ Quiz, activePrefixes đúng `['/community/quizzes']`; mobile giữ bốn mục học chính và nút Thêm, phần bổ sung chứa Tài liệu/Game/Cộng đồng→Quiz, giữ đường đích cũ. Nút có aria-expanded/aria-controls, Esc đóng/trả focus, menu đóng khi điều hướng. NavigationLink nhận ariaLabel trên link thực; bỏ aria-label trên span trong LearnerLayout. Không sửa AssistantHost.tsx.
- FE types giữ đúng QuizListingPage/QuizSession contract: myFirstAttempt và myBestRetryScore nullable, itemId ordinal, audioUrl optional/null, không đáp án trước nộp. API dùng shared apiRequest/cookie/CSRF; GET hsk/page/size, POST không body. UI lọc Mọi cấp/HSK 1–3 theo constant chung, API types giữ 1–9; số câu từ questionCount, page API bắt đầu 0 được đổi sang Pagination bắt đầu 1.
- CTA dựa myFirstAttempt !== null (kể cả điểm 0/suspicious): Làm lại/không tính hạng; chưa có first thì Bắt đầu/lượt đầu tính hạng. Điểm retry 0 vẫn hiện; không dùng retry score để suy ra đã tiêu thụ lượt đầu. Kết luận rankingEligible khi chuyển trang lấy từ POST server, không từ danh mục có thể cũ.
- **Bàn giao T-07:** `?sessionId` và navigation state `quizSession` chỉ để chuyển trang nhanh. Khi mất state/tải lại trang, T-07 phải gọi lại **POST /api/quizzes/{topicId}/attempts** để resume (200, không tính quota); **không coi sessionId trên URL là nguồn sự thật**. T-07 thêm `/community/quizzes/attempts/:sessionId` SCR-045 cùng trusted name trợ lý và phối hợp Đạt để ẩn trợ lý SCR-044.
- Bốn trạng thái loading/empty/error/ready, preview có dữ liệu tự soạn/tên dài và LearnerLayout thật. Xử lý 422 QUIZ_NOT_AVAILABLE/404 TOPIC_NOT_FOUND bằng thông báo và tải lại GET; 429 nhắc giới hạn 30 lượt/giờ, không auto POST retry; 401/403 theo luồng auth/403. Khóa CTA/deduplicate POST, bỏ qua GET cũ/abort, không điều hướng sau unmount hoặc đổi user.
- Leader chỉ yêu cầu gate FE `npx vitest run`, `npm run -s lint`, `npm run -s build` và BE `AssistantPromptBuilderTest,ModuleBoundaryTest` trên JDK 17; **không full Maven verify**, leader chạy khi merge. QA trình duyệt 320/375/640 px là gate riêng, không thay bằng JSDOM. Dừng sau G2, chưa commit/push/PR.

### G2 T-06 · đang xác minh

- Node 24.18.0; dùng npm.cmd/npx.cmd vì PowerShell chặn npm.ps1, không đổi execution policy. npm ci theo lockfile, không sửa dependency/package/lock.
- Targeted FE lần đầu: 63 tests, 1 failure do selector role=status trùng output đường dẫn của fixture; sửa selector trong file test đã duyệt. Targeted lại **63/63 pass, exit 0** (7 files).
- Lint: exit 0, 0 error/1 warning có sẵn ở shared/ui/LevelBadge.tsx (react-refresh/only-export-components), giữ nguyên file ngoài allowlist. Build đầu exit 2 do ba chỗ test truy cập phần tử mảng có thể undefined; đã sửa kiểm tra an toàn trong test và build lại thành công; không đổi TypeScript config.
- Gate BE JDK 17.0.12, DEBUG=false: **21 tests, 0 failure/error/skip, exit 0** (AssistantPromptBuilderTest 12 + ModuleBoundaryTest 9). Không full verify theo leader.
- Full Vitest đầu (chạy đồng thời build/lint/BE): **2616 tests, 10 fail/2606 pass, 7 files fail/177 pass; exit 1**. Một lỗi xác định do chèn quiz preview lên đầu registry làm đổi preview mặc định; đã chuyển quiz preview xuống cuối, không sửa PreviewPage.test ngoài allowlist. Chín failure còn lại trong các suite community/reminder/library/pronunciation/question-bank đang được chạy lại toàn bộ với giới hạn worker, chưa kết luận là lỗi nền.
- Browser QA hiện bị chặn: browser inventory báo **The browser access host is unavailable**, thử IAB cũng lỗi đó; truy cập app trình duyệt báo **Computer Use app approval timed out**. Đã hỏi user bật kết nối Browser/duyệt quyền; chưa đo và chưa ghi pass viewport 320/375/640. Preview cục bộ chạy tại `http://127.0.0.1:5196/__preview/frame/quiz-list/ready` (các trạng thái thay ready bằng loading/empty/error).

### G2 T-06 · 2026-11-14 — CHƯA ĐẠT, leader giữ 22 file và dừng

| Gate | Kết quả | Exit |
|---|---|---:|
| Targeted cuối (quiz/layout/routes/registry + PreviewPage) | 65 pass, 0 fail/skip; 8 files | 0 |
| Full Vitest mặc định lần đầu | 2616 tests: 2606 pass, 10 fail; 7 files fail | 1 |
| Full Vitest với `--maxWorkers=2 --minWorkers=1` | 2616 tests: 2615 pass, **1 fail**, 0 skip; 183 files pass/1 fail | 1 |
| Lint cuối | 0 error, 1 warning có sẵn LevelBadge.tsx | 0 |
| Build cuối (TypeScript + Vite) | đạt sau sửa test truy cập mảng | 0 |
| AssistantPromptBuilderTest + ModuleBoundaryTest | 21 tests, F0/E0/S0 | 0 |
| Trình duyệt 320/375/640 px | **chưa thực hiện đo/quan sát đủ gate** | — |

- Failure còn lại: `src/features/community/routes.test.tsx` → `FR001_registers exactly eight unique paths with approved SCR, access and layout`, dòng 116. Test gom mọi `/community/**` và `/moderation/**`, nên nay nhận 10 route thay vì 8 sau khi thêm SCR-043/044. Đây là kiểm kê bị ảnh hưởng do T-06, không báo là lỗi nền. File này bị thiếu trong allowlist G0.
- Đã đề xuất mở rộng thêm đúng file kiểm kê trên (23 file), chỉ thu hẹp selector về feed `/community`, `/community/posts/**`, `/moderation/**`, giữ assertion 8 route cũ; quiz có test riêng. Leader trả lời **"Giữ 22 file, dừng G2"**. Không sửa file kiểm kê ngoài allowlist, không chạy vòng sửa/test tiếp sau lựa chọn này.
- Lỗi preview mặc định đã sửa trong registry được duyệt: nối quiz preview ở cuối, giữ thứ tự mặc định cũ. PreviewPage.test pass ở targeted cuối và full ít worker. Tám failure timing/interaction khác của lượt đầu không tái diễn ở lượt toàn bộ ít worker; không chỉnh test ngoài allowlist hoặc coi failure còn lại là pass.
- Leader đồng thời duyệt quyền Computer Use; do lựa chọn dừng G2, không tiếp tục thao tác trình duyệt. Browser host trước đó không kết nối, truy cập app timeout; không có bằng chứng viewport thực tế, không dùng JSDOM để thay QA và không ghi pass. Preview dùng LearnerLayout thật và dữ liệu tự soạn đã sẵn sàng cho lượt kiểm sau.
- Log trong `target/g2/`: `targeted-final.log`, `vitest.log`, `vitest-bounded.log`, `lint-final.log`, `build-final.log`, `backend.log`; exit code lưu riêng cho các gate chính. Không full Maven verify theo duyệt riêng của leader.
- Self-check phạm vi: đúng 22 file, UTF-8 không BOM, `git diff --check` sạch; không dependency/lock/config/security/engine/contract mới; AssistantHost và community/routes.test.tsx giữ nguyên. Giữ thay đổi có sẵn của 003 tại checkout main. **Dừng G2 chưa đạt, chưa stage/commit/push/PR.**

### G2 T-06 tiếp tục · 2026-11-14 — leader mở rộng 23 file

- Leader duyệt thêm đúng `cnhsk-web/src/features/community/routes.test.tsx`. Chỉ sửa bộ lọc để loại path bắt đầu bằng `/community/quizzes`; giữ nguyên assertion "exactly eight" và danh sách 8 route của 009, không đổi production/route của 009. Quiz có route/access/registry tests riêng.
- Xác nhận bốn file **đã nằm trong G0 22 file được duyệt**: `shared/layout/LearnerLayout.tsx` (menu mobile), `shared/layout/NavigationLink.tsx` (aria-label trên link thật), `shared/layout/Layouts.test.tsx` (kiểm kê menu và focus), `app/preview/registry.ts` (đăng ký preview). Không phải mở rộng bổ sung bốn file này.
- Base đã cập nhật bằng **fast-forward nhánh spec/019-t06** từ 13f1d96 lên `dae6c7163c9086ed2eac6cb02bf39791695bcd55`; nhánh chưa có commit riêng nên không cần rebase. Lưu bản làm việc riêng rồi áp lại, giữ phần ghi chép T-05 trên main và phần T-06 khi giải xung đột nối cuối questions.md. Không merge/pull/rebase main, không sửa QuizResultIntegrationTest hoặc interface/production T-05/017.
- Các gate được chạy lại trên base mới: full Vitest với 2 worker (không skip/đổi timeout), lint, build, AssistantPromptBuilderTest + ModuleBoundaryTest. Không full Maven verify theo duyệt trước.
- QA thực tế/ảnh PR tiếp tục bị chặn sau khi user đã duyệt Computer Use: browser host vẫn báo unavailable; khi chọn Chrome, công cụ tự dừng với lý do **không xác định được URL hiện tại của browser Windows đủ chắc chắn để thực thi policy**. Không tiếp tục input UI sau thông báo này, không có ảnh/đo viewport đạt để đính kèm. Chưa mở PR khi điều kiện QA trước PR chưa hoàn tất.

### Gate sau cập nhật base dae6c71 · 2026-11-14 — chưa đủ điều kiện PR

| Gate | Kết quả | Exit |
|---|---|---:|
| Full Vitest, 2 worker | 2616 tests: 2614 pass/2 fail, 183 files pass/1 fail | 1 |
| Chẩn đoán riêng suite Hán tự (không sửa file) | 44/44 pass | 0 |
| Full Vitest cuối, 1 worker | 2616 tests: **2615 pass/1 fail**, 183 files pass/1 fail; 548,35 giây | 1 |
| Lint | 0 error/1 warning có sẵn LevelBadge.tsx | 0 |
| TypeScript + Vite build | đạt | 0 |
| AssistantPromptBuilderTest + ModuleBoundaryTest | 21 tests, F0/E0/S0 | 0 |
| QA 320/375/640 và ảnh PR | **chưa thực hiện đủ bằng chứng, công cụ đã dừng** | — |

- Kiểm kê `community/routes.test.tsx` đã pass sau khi chỉ loại `/community/quizzes*`; không sửa danh sách 8 route/assertion hoặc production 009.
- Lượt 2 worker có hai failure FR031/FR033 trong `Scr014CharacterWritingPage.test.tsx`, file không có diff; suite chạy riêng pass 44/44. Lượt full 1 worker cuối không còn hai failure đó nhưng còn `Scr059QuestionEditPage.test.tsx:236`, case `FR027_AT27_TEACHER edits SENTENCE_MATCH with the existing API wrappers`: expected nội dung `Chọn nghĩa của 水 trong câu 我喝水。`, received rỗng sau khi form đã hiện. Không tự kết luận đây là lỗi nền, không sửa file ngoài 23 file để làm xanh gate.
- Đã hỏi leader có duyệt thêm file kiểm tra question-bank (24 file) để chuyển assertion nội dung sang await waitFor, giữ nguyên expected và không sửa production. Leader chọn **"Giữ 23 file"**; không sửa file question-bank, không đổi assertion/timeout và không mở rộng allowlist. Failure này vẫn được báo là chưa đạt, không coi là ngoại lệ được chấp nhận.
- Log/exit trên base mới: `vitest-dae6c71.log`, `hanzi-diagnostic.log`, `vitest-dae6c71-final.log`, `lint-dae6c71.log`, `build-dae6c71.log`, `backend-dae6c71.log` trong target/g2. Không skip test, không đổi timeout, không full Maven verify.
- Bộ gate tự động chưa đạt 0 fail; QA còn bị policy của công cụ chặn, không có screenshot hợp lệ để gắn PR. **Chưa stage/commit/push/mở PR**. Production/interface 019/T-05, QuizResultIntegrationTest, AssistantHost, question-bank và hanzi đều không có diff so với base mới.

### Scr059 · leader xác nhận ngoại lệ và gate trên main mới

- Leader xác nhận Scr059 là test FE chập chờn đã biết, không liên quan T-06, không duyệt file thứ 24. Chỉ cho tiếp tục nếu chạy riêng pass; nếu fail riêng phải dừng gửi log.
- Đã chạy đúng lệnh `npx.cmd vitest run src/features/question-bank/pages/Scr059QuestionEditPage.test.tsx`: **64/64 pass, exit 0**, 1 test file, 5,94 giây, không sửa test/production question-bank (`target/g2/scr059-isolated.log`, `.exit`).
- Câu phải ghi trong PR theo leader: **"Full Vitest 1 fail Scr059 (case TEACHER edits SENTENCE_MATCH, test chập chờn đã biết); chạy riêng pass 64/64"**. Không đổi báo cáo full thành 0 fail. Leader sẽ chạy full Vitest trên máy mình trước merge.
- Fetch main mới `c7edc2821979abdf355d4334dd4754c45f13cec8`, cập nhật nhánh bằng fast-forward từ dae6c71, không rebase. Sáu file main mới chỉ đổi người triển khai spec 022/023/024, không có diff FE hoặc trợ lý giữa hai base; không thay đổi source đã test Scr059/full trước đó.
- Gate xác nhận lại trên c7edc28: lint **exit 0, 0 error/1 warning LevelBadge.tsx có sẵn**; build **exit 0**; AssistantPromptBuilderTest **12/12**, ModuleBoundaryTest **9/9**, tổng **21 tests F0/E0/S0, exit 0** trên Java 17/DEBUG=false. Log `lint-c7edc28.log`, `build-c7edc28.log`, `backend-c7edc28.log` trong target/g2.
- Server QA trả **HTTP 200** tại `http://127.0.0.1:5196/__preview/frame/quiz-list/ready`. Chưa có bằng chứng trình duyệt cho 320/375/640 px và chưa có ảnh PR. Sau khi user duyệt Computer Use, automatic approval review từ chối bind cửa sổ Edge cũ vì phiên không liên quan/URL chưa biết, có nguy cơ lộ nội dung riêng tư ngoài QA; không thử workaround trên cửa sổ đó. Phương án browser mới trỏ thẳng localhost và kiểm lại sau khi user chọn bật Browser đều báo **The browser access host is unavailable**.
- Đã kiểm khả năng kết nối qua plugin-management: không có thao tác bật Browser host nội bộ được cung cấp; không cài/đổi quyền plugin, không chuyển code local lên dịch vụ browser cloud. Cần kết nối Browser hoạt động hoặc tab QA local đã xác định để kiểm tiếp; không đánh dấu QA/screenshot đạt chỉ từ JSDOM.
- Điều kiện test chập chờn đã được leader chấp nhận theo kết quả riêng 64/64; điều kiện QA/ảnh trước PR vẫn chưa hoàn tất. **Giữ 23 file, chưa stage/commit/push/PR.**

### QA thay thế được duyệt · Playwright và G3 · 2026-11-14

- Leader duyệt thay QA tay bằng Playwright với Chromium phiên sạch, headless được; giữ 23 file, không thêm dependency dự án, không commit script/ảnh QA. Đạt QA thì được commit từng file, push `spec/019-t06` và mở PR. Điều kiện mới thay phần chặn QA ở các báo cáo trước.
- Playwright, script tạm và ảnh nằm ngoài repo tại `C:\KLTN_FALL2026\qa-019-t06`; `cnhsk-web/package.json` và package-lock không thay đổi. Chromium **159.0.8154.6**, context mới không dùng hồ sơ/cookie cá nhân, chỉ localhost 5196; API của trang local được mock bằng fixture tự soạn và learner giả `example.test`, không đăng nhập tài khoản thật, không gọi BE/DB. Google Fonts bị chặn trước khi gửi mạng để giữ điều kiện chỉ local; ảnh dùng font dự phòng.
- Hai loại trang được kiểm: `/__preview/frame/quiz-list/ready` cho SCR-043 trong LearnerLayout, và `/community/quizzes` với API mock để xác nhận active thực của route. POST mock thành công còn xác nhận điều hướng `/community/quizzes/43/play?sessionId=101` và menu Quiz tiếp tục active. Không sửa AssistantHost hoặc test của spec khác ngoài kiểm kê 009 đã duyệt.

| Viewport | scrollWidth / clientWidth | Thẻ, lọc HSK, Bắt đầu/Làm lại, ghi chú | Tab tới Bắt đầu | Focus |
|---|---|---|---:|---|
| 320 | 320 / 320 | Đủ, không tràn/cắt; chọn HSK được | 4 | `:focus-visible`, viền xanh 2 px + khoảng trắng 2 px |
| 375 | 375 / 375 | Đủ, không tràn/cắt; chọn HSK được | 4 | như trên |
| 640 | 640 / 640 | Đủ, không tràn/cắt; chọn HSK được | 11 | như trên |
| 1280 | 1280 / 1280 | Đủ, không tràn/cắt; chọn HSK được | 11 | như trên |

- Menu mobile kiểm ở **320 và 375**: button Thêm mở/đóng, aria-expanded/controls, Quiz trong nhóm Cộng đồng và `aria-current=page`, Escape đóng/trả focus, nút đóng và click điều hướng đều đóng panel. Sidebar ở 640 và desktop 1280: Quiz active đúng, nhóm Cộng đồng chỉ có Quiz. Không có lỗi JavaScript của trang.
- Đã chụp và xem lại 5 ảnh thật: `scr043-320.png`, `scr043-375.png`, `scr043-640.png`, `menu-mobile-375.png`, `menu-desktop-1280.png`. Ảnh và `report.json` ở thư mục `artifacts` ngoài repo; đính kèm ảnh qua GitHub CLI vào PR, không commit. QA chạy cuối **exit 0**; không cần sửa production sau QA.
- Fetch main mới **ad480295718a1b21c84f590467fbb1cb29356ef9**, fast-forward nhánh từ c7edc28, không rebase. Diff c7edc28..ad48029 không có file FE hoặc trợ lý, nên kết quả full Vitest/lint/build đã được leader chấp nhận vẫn áp dụng cùng mã nguồn; không có thay đổi routeAccess/navigation/LearnerLayout từ main cần chạy lại gate. Xác nhận lại BE trên base mới: AssistantPromptBuilderTest **12/12** + ModuleBoundaryTest **9/9**, tổng **21 F0/E0/S0**, **exit 0**, Java 17/DEBUG=false (`target/g2/backend-ad48029.log`). Full Maven verify không thuộc gate T-06 theo chỉ đạo leader.
- Self-check: giữ đúng allowlist 23 file; không dependency mới trong repo, không schema/SQL/secret/PII log, không đổi module boundary, không sửa file của Đạt/Dũng ngoài phạm vi đã phối hợp; không BOM, `git diff --check` sạch. Quyền commit/push nhánh và PR đã được leader cấp; không push/merge main.

### G0/G1 T-07 · 2026-11-14 — duyệt 28 đường dẫn, base tạm T-06

- Leader duyệt 28 đường dẫn, gồm xoá `Scr044QuizAttemptPlaceholderPage.tsx` để thay bằng SCR-044 thật (giữ route play), nối riêng `QUIZ`, `QUIZ_RETRY` vào cuối union FE PracticeSource. T-05 trong tasks đổi đúng `✅ MERGED — PR #171` theo yêu cầu. Không sửa hook/API practice dùng chung, AssistantHost production, navigation, screenRegistry tổng hoặc preview registry tổng.
- Leader cho bắt đầu ngay từ `origin/spec/019-t06` **83648b198369d0195c6b5c442cc1fcb69537e4ba**, thay yêu cầu chờ hash main trước tạo nhánh. Đã fetch xác nhận SHA và tạo worktree `C:\KLTN_FALL2026\CNHSK-019-t07`, nhánh `spec/019-t07`. Khi leader báo #177 đã merge: fetch, **rebase nhánh T-07 lên origin/main**, kiểm log origin/main..HEAD chỉ còn thay đổi/commit T-07. **Không mở PR trước rebase; gate phải chạy sau rebase.** Chưa rebase, chưa chạy test/lint/build/QA tại thời điểm ghi G1 này.
- **017 đã chốt:** adapter đúng `/api/public/leaderboards/topics/{topicId}?period=all`, tối đa 10 entry, giữ rank server, mock trong test; loading/error/empty chỉ ảnh hưởng khối Top 10, không che kết quả quiz. Link `/leaderboards/topics?topicId=` chỉ hiện nếu chính route đó đã đăng ký trong routeAccess, không hardcode feature flag, không dựng SCR-046 thay 017.
- SCR-044: navigation state chỉ là handoff; mất state/reload POST same topic để resume rồi GET phiên engine khôi phục đáp án và `servedAt`. Không lấy sessionId từ URL để load phiên. Key theo user/topic, bỏ qua response cũ sau unmount, deduplicate POST khi StrictMode chạy lại effect. Kiểm source/refId/feedback/status từ engine trước render input; dùng `ChoiceQuestion` DEFERRED không feedback đúng/sai trước nộp.
- Trả lời gửi ordinal no và durationMs qua engine; giữ lựa chọn chưa lưu khi lỗi để retry hoặc đính kèm finish, không ghi đè evidence của đáp án chỉ được khôi phục từ GET. Thời gian hiển thị là thời gian đã làm từ servedAt, không countdown/tự nộp; tổng câu lấy từ snapshot, không hard-code 10. Khoá thao tác ghi khi pending. Nộp còn câu trống xác nhận trong trang; chỉ chuyển kết quả sau SUBMITTED. Nút Thoát và điều hướng nội bộ dùng xác nhận rồi abandon; lỗi abandon giữ màn hiện tại, Escape huỷ xác nhận và trả focus.
- SCR-045: GET kết quả riêng, điểm/count/duration và rankingEligible/suspicious theo server sau nộp (bao gồm race downgrade); ưu tiên nhãn suspicious, không ngụ ý tăng mastery. Hiện đáp án đã chọn/đúng/giải thích với chữ Đúng/Sai/Bỏ trống. First rank/total null thì ẩn dòng hạng, vẫn giữ score/duration. Retry POST rồi chuyển play bằng session server; 401/403 theo auth, 404/422/429 có thông báo, không tự retry POST.
- Phần chung 015 được báo leader tại G0 và cập nhật tiến độ: chỉ thêm điều kiện SCR-044 vào `assistant/pageContext.ts` cùng test pageContext/AssistantHost; Host hiện đã dùng hidden tại đây nên không cần sửa production AssistantHost.tsx. Cả lượt đầu/retry đều ẩn panel và launcher trên SCR-044, SCR-043/045 vẫn có trợ lý; giữ hồi quy exam/topic-test. Thêm tên BE SCR-045 `Kết quả quiz theo chủ đề` và test kiểm kê để tránh lỗi screen-name #159.
- Test đã viết cho API result/top, state loss và GET restore, nguồn retry, chọn/đổi câu, không lộ feedback, đáp án lưu lỗi cùng finish, confirmation/abandon và route blocker, trạng thái hết hạn/đã nộp, auth/422/429, race/suspicious UI, rank null, top lỗi/rỗng/cắt 10, route link hiện theo registry, preview 4 trạng thái và assistant. Test route cũ về placeholder được đổi sang màn thật. Chưa ghi nhận pass khi chưa chạy gate.
- Gate leader chốt riêng T-07: full Vitest, lint, build; BE **chỉ AssistantPromptBuilderTest + ModuleBoundaryTest**, **không full Maven verify** (leader chạy lúc merge); Java 17/DEBUG=false. Playwright Chromium sạch ngoài repo tại 320/375/640/1280 cho làm bài, nộp/thoát, resume, kết quả, assistant hidden, focus và overflow; không commit script/ảnh, không thêm dependency dự án. Scr059 nếu tái diễn thì chạy riêng và báo theo điều kiện T-06, không tự sửa file thứ 29 hoặc tô xanh full suite.

### T-07 · rebase và xác minh sau #177 · 2026-11-14

- Leader báo #177 merged, main `3fe9ab0`. Đã commit cục bộ đúng 28 đường dẫn rồi fetch và **rebase thành công** lên `3fe9ab0beefd467b8c84f68b4036f4c92f6a8eb2`, không conflict. T-06 đã là ancestor của main nên `git log origin/main..HEAD` chỉ còn **một commit T-07**; diff với main đúng 28 đường dẫn. Chưa push nhánh trong lúc sửa G2. Khi hoàn tất gate sẽ amend các sửa trong allowlist vào cùng commit, không tạo thêm commit task.
- Leader nhắc 021/T-08 sẽ sửa navigation/pageContext để thêm Cuộc thi/SCR-050; lần fetch trong G2 main vẫn `3fe9ab0`, chưa có thay đổi đó. T-07 chỉ thêm điều kiện SCR-044, giữ điều kiện exam/topic-test; không sửa navigation hoặc production Host. PR sau cần rebase và giữ cả SCR-044/SCR-050.
- Lượt kiểm đầu sau rebase: targeted **137 tests, 132 pass/5 fail, exit 1**; 5 failure là selector đáp án giả định khoảng trắng trong accessible name của ChoiceQuestion (thực tế `Anước`/`Blửa`). Lint **exit 1** vì test import trực tiếp *.preview trái quy tắc repo; build **exit 2** vì hai EmptyState thiếu description, ByRoleOptions không có exact và deferred fixture suy luận literal true. Đã sửa trong 28 đường dẫn: dùng preview registry tổng, bổ sung props bắt buộc và kiểu/selector test đúng thư viện; không nới timeout/skip hoặc sửa shared component. Targeted sau sửa **137/137 pass, 11 files, exit 0** (`targeted-final.log`).
- QA browser bắt được khác biệt với JSDOM: React Router có thể giữ navigation state trong history sau reload. Hook nay bỏ handoff nếu PerformanceNavigationTiming xác nhận reload chính URL hiện tại, rồi POST theo topic và GET phiên engine; vẫn dùng state cho chuyển trang SPA bình thường. Playwright xác nhận reload tạo đúng một resume, giữ sessionId/đáp án/thời gian, không tiêu thụ phiên mới. Lượt QA ban đầu **exit 1** do thiếu resume; sau sửa và chụp lại **exit 0** ở cả 4 viewport.
- Gate lint/build được chạy lại sau sửa reload: **lint exit 0, 0 error/1 warning có sẵn LevelBadge.tsx**; **build exit 0**. BE sau rebase **AssistantPromptBuilderTest 12/12 + ModuleBoundaryTest 9/9 = 21 F0/E0/S0, exit 0**, Java 17.0.12/DEBUG=false. Full Maven verify không chạy theo duyệt riêng T-07. Full Vitest cuối đang được chạy với một worker trên mã đã sửa reload; kết quả cuối ghi tiếp bên dưới, chưa suy diễn pass.

| QA Playwright Chromium 159.0.8154.6 | 320 | 375 | 640 | 1280 |
|---|---:|---:|---:|---:|
| SCR-044 scrollWidth / clientWidth | 320/320 | 375/375 | 640/640 | 1280/1280 |
| SCR-045 scrollWidth / clientWidth | 320/320 | 375/375 | 640/640 | 1280/1280 |
| Tab tới Nộp bài, có focus-visible/ring | 8 | 8 | 14 | 14 |
| Resume giữ phiên/đáp án/thời gian | đạt | đạt | đạt | đạt |
| Xác nhận nộp, result, retry, xác nhận abandon | đạt | đạt | đạt | đạt |
| Ẩn trợ lý lượt đầu/retry; result giữ trợ lý | đạt | đạt | đạt | đạt |
| Top 10 lỗi riêng; tối đa 10; route thiếu thì ẩn link | đạt | đạt | đạt | đạt |

- QA dùng app/routes thật trên localhost **5197**, API mock từ fixture tự soạn, learner giả example.test, Chromium context mới mỗi viewport, không tài khoản thật/hồ sơ cá nhân/BE/DB. Chặn Google Fonts trước mạng để chỉ local, ảnh dùng font dự phòng. Không có lỗi JavaScript trang; không request ngoài localhost được cho phép.
- Có **10 ảnh ngoài repo** tại `C:\KLTN_FALL2026\qa-019-t07\artifacts`: attempt/result ở 320/375/640/1280 và confirm-submit/confirm-exit ở 320, cùng report.json. Script, ảnh và Playwright ngoài repo, không đổi package/lock hoặc commit QA artifact. Gate/exit logs trong target/g2.

### G2 T-07 cuối · đủ điều kiện Draft PR

| Gate sau rebase | Kết quả thực tế | Exit |
|---|---|---:|
| Targeted quiz/assistant/registry/preview | 137/137 pass, 11 files | 0 |
| Full Vitest `--maxWorkers=1 --minWorkers=1` | **2676/2676 pass, 188 files**, 581,23 giây, không fail/error/skip | **0** |
| Lint sau sửa cuối | 0 error, 1 warning LevelBadge.tsx có sẵn | 0 |
| TypeScript + Vite build sau sửa cuối | đạt | 0 |
| AssistantPromptBuilderTest + ModuleBoundaryTest | 21 tests, F0/E0/S0 | 0 |
| QA Playwright ở 320/375/640/1280 | các mục ở bảng trên đạt, 10 ảnh, không page error | 0 |

- **Scr059 pass 64/64 ngay trong full suite**; không cần chạy riêng hoặc dùng ngoại lệ T-06. Không failure test spec khác, không skip/nới timeout. Các suite practice/exam/mastery/assistant/community/layout/registry đều nằm trong full suite đã pass trên mã cuối.
- Self-check cuối: đúng allowlist 28 đường dẫn (bao gồm placeholder bị xoá), PracticeSource FE chỉ nối hai giá trị cuối; không BOM/UTF-8 lỗi, diff --check sạch; không dependency/lock/config/schema/SQL/secret/PII log mới, không sửa production Host/hook practice hoặc module boundary. Task nhánh rebase được leader duyệt; không push/merge/rebase main. G3 dùng một conventional commit chứa cả sửa G2 và báo cáo, push thường nhánh T-07, mở **Draft PR** base main kèm 10 ảnh ngoài repo.

### G0/G1 T-08 · 2026-11-14 — 3 file, giữ nguyên contract YAML

- Leader duyệt đúng 3 file: `src/test/java/com/cnhsk/contract/QuizContractTest.java`, questions.md và tasks.md. Không sửa YAML/production; nếu runtime lệch status so YAML phải dừng báo leader, không tự điều chỉnh hoặc lọc bỏ status.
- **security không thuộc phạm vi so khớp hợp đồng; yaml giữ cookie + bearer theo hành vi thật**. T-03 chỉ ghi nhận contract có cả hai, không có quyết định bỏ bearer; bearer LEARNER vẫn được hỗ trợ/test. G0 ban đầu đề xuất bỏ bearer là diễn giải sai, đã bỏ khỏi phạm vi theo leader.
- Leader xác nhận #179 merged và cấp base **00efcf8b2ac1889b1d49eb120aa2e9b813cf86ab**. Đã fetch xác nhận main chứa SHA T-07, tạo worktree `C:\KLTN_FALL2026\CNHSK-019-t08`, nhánh `spec/019-t08` từ đúng base đó. T-07 đổi trạng thái merged #179 trong tasks.
- Test kế thừa AbstractIntegrationTest dùng chung PostgreSQL/Redis/Mailpit Testcontainers, profiles test/dev và springdoc chỉ scan `com.cnhsk.learning.quiz.web`. GET docs qua MockMvcPrint.NONE, không fixture/ghi/xoá DB, không Neon. So toàn bộ path/method/operationId/status hai chiều, đúng 3 path/3 operation. Handler mapping lọc theo package quiz.web (kể cả package con), không lọc theo prefix URL để tránh che route sai tên; mapping không hạn chế method được coi là tất cả method hợp lệ.
- Tập status phải khớp nguyên vẹn: GET quizzes `{200,401,403,422}`; POST attempts `{200,201,401,403,404,422,429}`; GET result `{200,401,403,404,422}`. Không so security/schema hoặc thay đổi contract chỗ khác.
- Gate giữ đúng thứ tự đã duyệt: QuizContractTest → *ContractTest + Quiz*Test → ModuleBoundaryTest → full mvnw verify, Java 17/DEBUG=false, kiểm docker info trước integration. Báo số test/F/E/S, exit thật, JaCoCo LINE ≥80%, coverage checks, diff check và BOM. Đợi gate trước một conventional commit/push/Draft PR; gửi G0 chính thức 023/T-01 cùng báo cáo PR T-08.

### G2 T-08 · 2026-11-14 — đạt trên base 00efcf8

| Gate đúng thứ tự | Tests | F/E/S | Exit |
|---|---:|---|---:|
| QuizContractTest | 1 | 0/0/0 | 0 |
| *ContractTest + Quiz*Test | 166 | 0/0/0 | 0 |
| ModuleBoundaryTest | 9 | 0/0/0 | 0 |
| Full `mvnw.cmd -B verify` | **5360** | **0/0/0** | **0** |

- Java **17.0.12**, Docker info **29.9.2 / Linux**, DEBUG=false trong phiên; Testcontainers chạy đủ integration, không Neon. Full verify **20 phút 47 giây**; log/exit trong `target/g2/{quiz-contract,regression,module-boundary,verify}.{log,exit}`.
- JaCoCo LINE: **13.777 covered / 14.215 total**, missed **438**, **96,9187%** (>=80%). Build in đúng dòng **`All coverage checks have been met.`** và **BUILD SUCCESS**; số đo lấy từ counter LINE cấp report trong `target/site/jacoco/jacoco.xml`.
- Runtime khớp 3 path/3 operation/operationId/tập status ở targeted, regression và full; không có lệch cần chỉnh YAML/production. Không failure/error/skip hoặc ngoại lệ môi trường được dùng để làm xanh gate; không nới timeout/ignore failure.
- Self-check: đúng 3 file allowlist, UTF-8 hợp lệ/không BOM, diff --check sạch; YAML giữ nguyên cookie + bearer và không có diff, không security/schema check, không đổi production/FE/dependency/schema/secret/env. Test mới chỉ đọc docs/handler mapping, không tạo hoặc dọn fixture DB. Giữ thay đổi có sẵn của 003 ở checkout gốc. T-07 đã merged #179; T-08 chờ review, leader đóng spec 019 sau khi PR T-08 merge.
- G3 theo duyệt: một conventional commit, stage đúng 3 tên file, push `spec/019-t08`, mở Draft PR base main, không merge. Bản G0 023/T-01 đã soạn ngoài repo và gửi cùng báo cáo PR, chưa tạo nhánh/sửa code 023.

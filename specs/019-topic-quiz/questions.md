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

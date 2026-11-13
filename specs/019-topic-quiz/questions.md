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

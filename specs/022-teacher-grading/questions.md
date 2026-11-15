# Câu hỏi và quyết định — Spec #022

## 2026-11-14 · Leader chốt khi viết spec

UC-103…UC-105 ghi luồng chấm bài chỉ được mở khi chốt payout, hạn nhận/hạn chấm và quy ước độ dài (BR-103-1, BR-104-1, BR-105-1). Các khoá dưới đây đã chốt; số liệu nằm trong `cnhsk.grading.*`.

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Hạn giáo viên nhận bài / chấm xong | Nhận trong 48 giờ kể từ lúc gửi; chấm trong 24 giờ kể từ lúc nhận; quá hạn → `EXPIRED` + hoàn, giáo viên không được trả | ✅ Chốt |
| Q2 | Payout cho giáo viên | Mặc định 0 (không ghi giao dịch); > 0 → `TEACHER_PAYOUT` đúng một lần/yêu cầu, áp dụng cả nhánh lượt gói lẫn nhánh điểm | ✅ Chốt |
| Q3 | Quy ước độ dài và thang chấm | 50–500 chữ Hán (khối CJK Unified Ideographs), ≤ 3 000 ký tự; 4 tiêu chí × 0–25 (ngữ pháp, từ vựng, mạch lạc, chữ viết), tổng 0–100; nhận xét bắt buộc; ≤ 50 dòng sửa lỗi | ✅ Chốt |
| Q4 | Giá | 1 lượt `GRADING` của gói hoặc 1 điểm (cách `EntitlementApi.consume` của 013 đang tính) | ✅ Chốt |
| Q5 | Huỷ của người học khi CHECK `status` không có `CANCELLED` | `FAILED` + `error_message='CANCELLED_BY_LEARNER'` + hoàn | ✅ Chốt |
| Q6 | Danh tính, phúc khảo | Ẩn danh hai chiều; không phúc khảo/đánh giá giáo viên trong bản này; khiếu nại → `ADMIN` điều chỉnh qua 013 | ✅ Chốt |
| Q7 | Số bài giáo viên giữ cùng lúc | 3; được trả lại | ✅ Chốt |

## Dữ kiện đã kiểm trên main `b9932af`

- `learning.service_requests` có `assignee_id`, `assigned_at`, `deadline_at`, `score` (0–100), `feedback`, `payout`, `ck_req_deadline`, `ck_req_feedback`, `ck_req_self`, `ix_req_queue`.
- `credit_transactions.tx_type` có `TEACHER_PAYOUT`, `REFUND`; có cột `request_id`; `uq_refund` chặn hoàn trùng.
- `EntitlementApi.consume(userId, feature, key)` trả `Consumption {source, transactionId, feature, idempotencyKey, periodStart}`; nhánh điểm ghi `DEDUCT -1`.
- `PaidFeature.GRADING` đã có; role `TEACHER` đã có (`/api/admin/questions` dùng).

## 2026-11-14 · T-01 — G0 được leader duyệt

Base triển khai: `c7edc2821979abdf355d4334dd4754c45f13cec8`, nhánh `spec/022-t01`. T-01 chỉ thêm nền cấu hình, quy tắc bài viết và persistence; dịch vụ quyền, trừ/hoàn lượt và trả giáo viên thuộc các task sau. Không migration, không sửa bên trong 013, không chạy SQL trên Neon.

| Mã | Chốt | Trạng thái |
|---|---|---|
| Q-T01-01 | Chỉ đếm chữ Hán U+4E00…U+9FFF (`CJK_UNIFIED_IDEOGRAPHS`); Extension A/B, emoji, dấu câu không tính. Tổng độ dài tính theo code point sau khi bỏ whitespace Unicode ở hai đầu. | ANSWERED |
| Q-T01-02 | Bài lưu giữ xuống dòng và khoảng trắng bên trong. Đầu vào hash được trim Unicode rồi gộp mỗi chuỗi whitespace thành một dấu cách; SHA-256 UTF-8, hex thường. Không đổi hoa/thường, dấu câu hay chuẩn hoá Unicode. | ANSWERED |
| Q-T01-03 | Các thao tác ghi dùng MANDATORY, nhận Instant từ Clock của service. UPDATE có điều kiện trả 0 dòng là thua tranh chấp, không ném lỗi nghiệp vụ, không ghi tiền. Release về PENDING xoá assignee_id/assigned_at và khôi phục deadline_at = claimDeadline; release sang EXPIRED giữ assignee_id/assigned_at để truy vết. | ANSWERED |
| Q-T01-04 | ErrorResponse.details dùng oneOf giữa mảng lỗi field hiện có và object có hanziCount (integer ≥ 0). GlobalExceptionHandler chỉ thêm GRADING_INVALID_ESSAY vào danh sách giữ details; mã khác giữ hành vi cũ. | ANSWERED |
| Q-T01-05 | Mọi khoá cnhsk.grading đều bắt buộc, kể cả teacher-payout-credits = 0. Kiểm minHanzi ≤ maxHanzi ≤ maxChars ≤ 3000 khi khởi động. Rubric thuộc T-04. | ANSWERED |

Test repository dùng PostgreSQL Testcontainers riêng, kiểm rollback và các cuộc đua nhận/huỷ/hết hạn/nộp; không gọi EntitlementApi hay LedgerService. Mười mã lỗi của 022 được thêm ngay trước INTERNAL_ERROR, giữ mọi mã sẵn có.

Gate T-01 (Java 17, Docker local, DEBUG=false): targeted `GradingConfigTest` 25 + `EssayRulesTest` 12 + `GradingRepositoryIntegrationTest` 21 + `GlobalExceptionHandlerTest` 21 + `ModuleBoundaryTest` 9 = 88/88, F0/E0/S0. Full `mvnw.cmd -B verify`: 5084 test, F0/E0/S0, BUILD SUCCESS; JaCoCo LINE 13419/13841 = 96,9511%, mọi ngưỡng đạt. Không dùng ngoại lệ môi trường. `git diff --check` sạch; 13 file UTF-8 không BOM.

Fetch trước bàn giao: origin/main = `cc7b7ec` (merge #173, 007/T-02). So với base `c7edc28`, main không đổi file nào trong allowlist T-01, gồm ErrorCode và handler; không có xung đột mã lỗi cần rebase. PR giữ base triển khai đã ghi ở trên.

## 2026-11-14 · T-02 — G0 được leader duyệt

Base `b435223fc65149978b15bfaff9a94551635e71e8`, đã kiểm chứa commit T-01 `203082a` qua merge PR #175 `c5da36b`. Nhánh `spec/022-t02`, worktree riêng; allowlist 12 file. Giữ nguyên thứ tự mã lỗi leader đã gộp với #174.

| Mã | Chốt | Trạng thái |
|---|---|---|
| Q-T02-01 | Controller nhận id dạng String rồi parse. Sai dạng, ≤ 0, tràn int64 hoặc không tồn tại/sai loại/khác chủ đều trả 403 GRADING_REQUEST_NOT_OWNED; không để lỗi type-mismatch vào handler chung. | ANSWERED |
| Q-T02-02 | JSON sai, thiếu essay, essay không phải string, prompt > 500 code point, hskLevel ngoài 1–9 hoặc sai kiểu → 422 VALIDATION_FAILED. Essay đúng kiểu nhưng sai độ dài/số chữ Hán → 422 GRADING_INVALID_ESSAY kèm details.hanziCount, không trừ lượt. | ANSWERED |
| Q-T02-03 | refunded = (status = EXPIRED) hoặc (status = FAILED và error_message = CANCELLED_BY_LEARNER). Theo chốt leader, trạng thái và refund được xử lý trong cùng transaction; refund ném lỗi thì rollback trạng thái. | ANSWERED |

Gửi: kiểm bài → khoá grading:submit:<userId> → kiểm hash đang PENDING/ASSIGNED → consume(GRADING, grading:<UUID server>) → chèn payload có biên nhận server, cùng một transaction. Huỷ: kiểm quyền → UPDATE có điều kiện → refund bằng biên nhận đã lưu, cùng transaction; UPDATE thua thì không refund. Thời điểm lấy từ Clock, reference định dạng GR-%06d. Không sửa bên trong 013.

Danh sách dùng hai query (count + page), lọc owner/kind/status trước phân trang, sắp created_at DESC, id DESC. Preview lấy tối đa 100 code point. DTO người học chỉ chọn các trường theo contract, không trả nguyên RequestRow/payload/biên nhận/assignment/payout; result chỉ có khi COMPLETED. SecurityConfig thêm đúng 3 GET + 2 POST cho LEARNER, trước /api/admin/**, giữ nguyên CSRF.

Gate T-02 (Java 17, DEBUG=false, Docker/Testcontainers local): targeted đầu 95/95; hồi quy sau hoàn thiện gồm Grading*Test + Entitlement*Test + GlobalExceptionHandlerTest + ModuleBoundaryTest đạt 223/223, F0/E0/S0 (HTTP integration 40/40). Full Maven verify: 5358 test, F0/E0/S0, BUILD SUCCESS, 18:34 phút; JaCoCo LINE 13774 covered / 438 missed = 96,9181%, mọi ngưỡng đạt. Không dùng ngoại lệ môi trường. Diff sạch, 12 file UTF-8 không BOM.

AT-03 dùng billing thật: ép lỗi INSERT ở cả QUOTA/CREDIT giữ nguyên quota/số dư, không tạo yêu cầu/DEDUCT và không còn receipt Redis. AT-02: hai POST cùng bài sau chuẩn hoá chỉ một 201, một 409, một lần trừ. AT-05: refund lỗi rollback về PENDING rồi retry được; hai huỷ credit song song chỉ một REFUND; huỷ đua với claim chỉ một bên thắng. AT-04/17 kiểm quyền, sai loại, toàn bộ role khác, CSRF và không lộ các trường cấm. Fetch trước bàn giao vẫn origin/main b435223, không thay đổi file dùng chung mới.

## 2026-11-14 · T-03 — G0 được leader duyệt

Base `00efcf8b2ac1889b1d49eb120aa2e9b813cf86ab`, đã kiểm chứa T-02 qua PR #178 (`f6e471e`), nhánh `spec/022-t03`. Worktree 022 được tái sử dụng sau khi kiểm sạch và commit T-02 đã merge. Allowlist 10 file; không đổi bên trong 013, contract, mã lỗi, cấu hình, migration hoặc file game.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T03-01 | ANSWERED | CLAIM lấy pg_advisory_xact_lock với key grading:claim:<teacherId> trước đếm và UPDATE, cùng transaction. Release không lấy khoá giáo viên: UPDATE có điều kiện chặn tranh chấp, release chỉ giảm số đang giữ. |
| Q-T03-02 | ANSWERED | Claim: id sai dạng, ≤ 0, tràn int64, không tồn tại hoặc sai loại → 409 GRADING_ALREADY_CLAIMED. Detail/release tương ứng → 403 GRADING_NOT_ASSIGNEE. Id hợp lệ: tự chấm 403 → giới hạn 422 → UPDATE thua 409; không đổi contract. |
| Q-T03-03 | ANSWERED | Mine sắp deadline_at ASC, id ASC, chỉ ASSIGNED của người gọi; bài quá hạn vẫn nằm trong mine/đếm đang giữ cho đến khi chuyển trạng thái. |

Queue lọc PENDING, deadline_at > Clock.now và loại bài của chính giáo viên trước count/phân trang; chỉ SELECT metadata, không lấy nội dung bài hay người học vào projection. Count + page dùng hai query cố định; mine dùng một query. NFR-P01 chỉ assert số query không tăng với 1.000 yêu cầu; p95 chỉ đo và ghi, không thêm gate thời gian. Phép đo trong repository integration test là service + DB, không bao gồm HTTP/JWT.

Nhận bài dùng SQL T-01, giữ claimDeadline gốc, hạn chấm từ Clock + grading-hours. Release chỉ cho assignee khi ASSIGNED; còn hạn nhận → PENDING, xoá assignment, khôi phục hạn; hết hạn nhận → EXPIRED, giữ assignment và refund cho người học bằng receipt server trong cùng transaction. Refund ném lỗi thì rollback toàn bộ; thao tác thua không refund. Không consume hay payout trong T-03.

SecurityConfig chỉ thêm 3 GET + 2 POST TEACHER trước /api/admin/**, chưa mở /complete, không miễn CSRF. DTO theo đúng contract, không có danh tính người học, payload/consumption hay payout nội bộ. T-02 ghi MERGED — PR #178; T-03 giữ 🔄 chờ review khi mở PR.

Gate chọn lọc T-03: TeacherGradingServiceTest + GradingRepositoryIntegrationTest 41/41, HTTP TeacherGradingControllerIntegrationTest 33/33, F0/E0/S0. Hồi quy toàn bộ grading, EssayRulesTest, Entitlement*Test, GlobalExceptionHandlerTest và ModuleBoundaryTest đạt 287/287, F0/E0/S0. AT-07 chạy 10 request HTTP đồng thời: đúng một 200 và chín 409. Hai yêu cầu của cùng giáo viên tranh suất thứ ba chỉ một thành công; hai release hết hạn chỉ một REFUND. Refund lỗi ở cả QUOTA/CREDIT rollback trạng thái, sau đó thử lại được. Release không lấy advisory lock của CLAIM.

NFR-P01: 1.000 yêu cầu, count + page vẫn đúng 2 query; p95 service + PostgreSQL đo được 8,500 ms ở targeted và 6,811 ms ở hồi quy. Chỉ ghi số đo, không assert ngưỡng thời gian. Fetch trước bàn giao: origin/main = fbf7ad0 (007/T-03); so với base 00efcf8 không có thay đổi nào trong allowlist T-03, gồm SecurityConfig. Không merge/rebase/pull main vào nhánh.

Full gate T-03: `.\mvnw.cmd -B verify` (Java 17, DEBUG=false, PostgreSQL/Redis Testcontainers local) đạt 5.411 test, F0/E0/S0, BUILD SUCCESS, 19:51 phút. Không dùng ngoại lệ môi trường. NFR-P01 ở lượt full: p95 2,394 ms, vẫn 2 query. JaCoCo LINE từ XML mới sinh của chính lượt full: 13.862 covered / 440 missed = 96,9235%; mọi coverage check đạt. T-03 giữ 🔄 chờ review, không đánh hoàn tất trước merge.

Self-check: đúng 10 file allowlist; diff --check sạch, UTF-8 không BOM, không dấu conflict/TODO mới hoặc test bị skip. Không thêm thư viện, không đổi engine, module 013, ErrorCode, contract, cấu hình hoặc migration. Matcher giữ RBAC/CSRF, claim dùng khoá trong transaction, release/refund rollback và chỉ một bên thắng đã được kiểm bằng PostgreSQL/HTTP song song. Không đưa secret, thông tin thật hay dữ liệu HSK có bản quyền vào thay đổi; không dùng Neon. Stage từng file, một conventional commit và Draft PR; không merge PR/main hoặc force-push.

## 2026-11-14 · T-04 — G0 được leader duyệt

Base `a9b07d48666a9ad963b0f0a686326329f55a6c74`, chứa T-03/#183 (đã kiểm ancestor ac4b09b), nhánh `spec/022-t04`. Worktree 022 được tái sử dụng sau khi kiểm sạch. Allowlist 11 file: 10 file của G0 cùng spec.md (chỉ sửa dòng E6). Không sửa repository SQL, bên trong 013, ErrorCode, YAML, cấu hình hoặc migration.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T04-01 | ANSWERED | Nộp lại, không còn ASSIGNED hoặc UPDATE thua → 403 GRADING_NOT_ASSIGNEE theo FR-014/015, §5 và YAML. Chỉ sửa E6 từ 409 thành 403. |
| Q-T04-02 | ANSWERED | Bỏ qua trường dư như parser T-02, kể cả score/payout từ client. Điểm do server cộng bốn tiêu chí; payout lấy từ GradingConfig. Test gửi score/payout=999 không làm đổi hai giá trị này. |
| Q-T04-03 | ANSWERED | Feedback/original/corrected chỉ whitespace Unicode cũng trả 422; nội dung hợp lệ giữ nguyên, đếm code point trên giá trị gốc. Note null/rỗng được phép. Criteria đủ bốn số nguyên 0–25; feedback 1–5.000, original/corrected 1–500, note tối đa 500, corrections bắt buộc và tối đa 50. |

Complete trong một transaction: kiểm ASSIGNED/assignee → lấy Clock.now và kiểm hạn → conditional UPDATE T-01 → chỉ bên thắng append TEACHER_PAYOUT với request_id=id, feature_code=GRADING nếu payout>0. Payout=0 không gọi ledger. Append lỗi không bị nuốt, rollback toàn bộ trạng thái/result/score/feedback/payout/completedAt và bút toán. Không consume/refund hoặc advisory lock trong complete; claim/release giữ hành vi cũ. Trả kết quả từ bản ghi đã lưu, payoutCredits của response COMPLETED lấy từ payout đã lưu. Người học vẫn xem kết quả qua DTO T-02 không lộ payout/receipt/giáo viên.

Matcher chỉ thêm POST /api/teacher/grading-requests/{id}/complete hasRole TEACHER cạnh claim/release, trước admin; không miễn CSRF. Handler body sai nằm cục bộ controller, message cố định, không giữ nguyên nhân JSON/input, không đổi mapper/handler chung. Request và diagnostic string không log nội dung chấm.

Gate đầu: compile phát hiện dùng nhầm maxItems trên @Schema; đã sửa đúng sang @ArraySchema theo tiền lệ repo, không đổi dependency. Targeted sau sửa (GradingResultWriteTest 15 + TeacherGradingServiceTest 24 + GradingRepositoryIntegrationTest 26): 65/65, F0/E0/S0, BUILD SUCCESS. NFR-P01 chỉ cập nhật mock LedgerService ở constructor test, giữ toàn bộ assertion và phép đo p95, không thêm gate thời gian.

HTTP/PostgreSQL với LedgerService/Entitlement thật: 53/53, F0/E0/S0, BUILD SUCCESS. AT-12: hai request HTTP đồng thời đúng một 200, một 403 và một TEACHER_PAYOUT +2 đúng teacher/request_id; 100 lần gửi trong 10 lô song song (10 request/lô) chỉ một 200 và 99 lần 403, vẫn một payout. Score/payout=999 trong body không ảnh hưởng: lưu score 78 từ bốn tiêu chí và payout 2 từ cấu hình test; mặc định payout=0 không gọi ledger.

Ép lỗi sau khi LedgerService đã INSERT thật chứng minh rollback cả toàn bộ RequestRow và bút toán ở hai nguồn QUOTA/CREDIT; retry chỉ ghi một payout. Test overlap có latch chặn trước conditional UPDATE, chạy hai request/transaction thật: complete thắng hoặc release/expire thắng đều được ép kiểm, cho cả QUOTA/CREDIT. Complete thắng không refund, release/expire thắng không payout. Fixture expiry gọi repository/Entitlement trong transaction riêng, không tạo job T-05. Kiểm biên deadline bằng đúng Clock.now và sau 1 ms. Dọn ledger fixture của tất cả user trước khi dọn yêu cầu để giữ FK request_id; chỉ tác động dữ liệu test tự tạo trên Testcontainers.

Hồi quy toàn bộ *Grading*Test, EssayRulesTest, Entitlement*Test, Ledger*Test, GlobalExceptionHandlerTest và ModuleBoundaryTest: 347/347, F0/E0/S0, BUILD SUCCESS, 1:12 phút. Đã giữ mọi assertion T-01…T-03, chỉ thay assertion T-03 tạm chặn /complete thành 422 GRADING_INVALID_RESULT khi endpoint mới thiếu body; kiểm CSRF/role cũ giữ nguyên.

Full `.\mvnw.cmd -B verify` (Java 17, DEBUG=false, Docker/Testcontainers local) hoàn tất 2026-11-14 22:37:30 +07:00: 5.542 test, F0/E0/S0, BUILD SUCCESS, 18:15 phút; không dùng ngoại lệ môi trường. JaCoCo LINE từ báo cáo của chính lượt này: 14.129 covered / 442 missed = 96,9666%, mọi coverage check đạt. Không sửa code sau lượt gate; ngày 2026-11-15 tiếp tục kiểm kết quả và bàn giao.

Fetch trước bàn giao ngày 2026-11-15: origin/main = 51fb956. Các file grading/spec 022 không đổi so với base a9b07d4; SecurityConfig thêm matcher GET game và admin exams ở các vị trí khác với POST complete. Không merge/rebase/pull main; giữ base đã duyệt và báo leader kiểm trên bản gộp.

Self-check PASS: đúng 11 file allowlist, git diff --check sạch, UTF-8 không BOM, không dấu conflict/TODO mới hay test skip. Spec chỉ sửa E6; repository integration chỉ thêm mock constructor; không sửa SQL, LedgerService/Entitlement/013, ErrorCode, YAML, dependency, cấu hình, migration hoặc FE. Không consume/refund/advisory lock trong complete; điểm và payout do server quyết định, conditional UPDATE + append cùng transaction đã kiểm bằng PostgreSQL/HTTP song song và fault injection sau INSERT. Không secret/PII thật/dữ liệu HSK có bản quyền, không Neon. Một conventional commit, stage từng file, Draft PR; T-04 chờ review, không merge PR/main hoặc force-push.


## 2026-11-15 · T-05 — G0 được leader duyệt

Base `0167bac48c6b65decc018b874f93f9680807caad`, đã có T-04/PR #190. Nhánh `spec/022-t05`, worktree grading sạch trước khi tạo nhánh. Allowlist đúng 9 file; không đổi bên trong 013, endpoint, mã lỗi, SecurityConfig, migration, contract hay cấu hình production.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T05-01 | ANSWERED | Mỗi lần chạy một batch tối đa 100, sắp deadline_at/id. Mỗi dòng một TransactionTemplate REQUIRES_NEW theo tiền lệ ImportMaintenance. Chỉ conditional UPDATE thắng mới refund bằng receipt server; lỗi một dòng rollback rồi tiếp tục dòng sau. Không advisory lock. |
| Q-T05-02 | ANSWERED | Cron '-' được phép, theo chuẩn tắt lịch của Spring. application-test.yaml chỉ thêm cnhsk.grading.expiry-job-cron='-'. Thiếu/rỗng/cron sai vẫn fail startup. Production giữ 0 */5 * * * *. Job dùng zone Asia/Ho_Chi_Minh, không bật scheduling thêm. |
| Q-T05-03 | ANSWERED | Redis lỗi khi hoàn lượt gói → 013 bỏ qua có chủ đích (tránh hoàn trùng); job không phát hiện được để retry. Hoàn điểm (DB + uq_refund) không bị ảnh hưởng. Không sửa 013 hoặc bịa tín hiệu refund thành công. |
| Q-T05-04 | ANSWERED | refundFailed đếm mọi transaction dòng bị lỗi: receipt hỏng, DB lỗi, refund ném lỗi. Quét/đếm tồn lỗi → WARN cố định, remainingOverdue=null, không giả 0. Không log exception/input. |

Job lấy thời gian từ Clock, dùng SQL findOverdue/expire đã có; không thay luật chuyển trạng thái hoặc mốc '< now'. completed_at là thời điểm xử lý dòng; EXPIRED giữ assignment. Chỉ tăng expired sau khi transaction commit. countOverdue đếm toàn bộ yêu cầu GRADING/PENDING hoặc ASSIGNED quá hạn, không dùng số phần tử batch thay cho tồn thực. remainingOverdue>0 có WARN. Receipt thiếu/hỏng/khác feature GRADING làm rollback dòng, không lộ payload ra log.

AT-14 giữ 100 vòng tranh chấp thật; chỉ giảm nếu phép đo vượt 60 giây như leader cho phép, không dùng thời gian làm assertion. Test dùng latch: claim đã chốt Clock trước hạn, job đọc sau hạn; hai transaction cùng tranh đúng yêu cầu, ép cả hai bên thắng. Thêm tranh chấp hai job, cancel/release/complete và rollback sau khi refund thật đã thay đổi dữ liệu. PostgreSQL/Redis riêng cho integration test job để không quét fixture suite khác.

Targeted: GradingConfigTest 26 + GradingExpiryJobIntegrationTest 25 + GradingExpiryJobTest 10 + GradingRepositoryIntegrationTest 26 = 87/87, F0/E0/S0, BUILD SUCCESS, 1:46 phút. AT-14 đủ 100 vòng overlap thật, đo 7.046 ms, dưới 60 giây nên không giảm số vòng; không có assertion thời gian. Unit log không chứa input/exception; lỗi quét/đếm báo remainingOverdue=null. Integration dùng PostgreSQL/Redis riêng, chứng minh REQUIRES_NEW vẫn commit khi transaction ngoài rollback; lỗi sau refund thật rollback cả trạng thái, credit/quota và marker, lần sau hoàn một lần. Kiểm cả hai job cùng snapshot, cả hai bên thắng khi đua với claim/cancel/release/complete, cho QUOTA/CREDIT. Nhánh Redis lỗi QUOTA vẫn EXPIRED nhưng usage không giảm đúng hạn chế 013 đã được leader chấp nhận; CREDIT vẫn hoàn qua DB.

Hồi quy: toàn bộ *Grading*Test, EssayRulesTest, Entitlement*Test, Ledger*Test, GlobalExceptionHandlerTest và ModuleBoundaryTest đạt 385/385, F0/E0/S0, BUILD SUCCESS, 1:22 phút. Giữ mọi assertion cũ; GradingConfigTest chỉ thêm trường hợp cron tắt. Không đổi SQL chuyển trạng thái. Full verify được chạy trên đúng bản code này; chỉ cập nhật tài liệu sau khi đọc kết quả thật.

Full Maven verify (Java 17.0.9, DEBUG=false, Docker/Testcontainers local) hoàn tất 2026-11-15 01:56:41 +07:00: 5.917 test, F0/E0/S0, BUILD SUCCESS, 20:30 phút. Không dùng ngoại lệ môi trường. JaCoCo LINE từ XML mới của chính lượt full: 14.731 covered / 448 missed = 97,0486%; GradingExpiryJob 42 covered / 0 missed = 100%, mọi coverage check đạt. AT-14 đủ 100 vòng ở cả targeted (7.046 ms), hồi quy (3.799 ms), full (3.076 ms); không giảm số vòng, không dùng thời gian làm assertion.

Fetch trước bàn giao vẫn origin/main = 0167bac, không có thay đổi main mới. Chỉ sửa đúng 9 file đã duyệt; application-test.yaml chỉ thêm cron grading tắt, GradingConfig cho phép '-' nhưng giữ mọi validation thiếu/rỗng/sai và default production. GradingRepository chỉ thêm countOverdue; không đổi bất kỳ SQL chuyển trạng thái nào. T-04 ghi MERGED (#190/0167bac); T-05 giữ chờ review khi mở PR.

Self-check PASS: git diff --check sạch, UTF-8 không BOM, không TODO/dấu conflict mới hoặc test skip. Không sửa bên trong 013, EntitlementApi, LedgerService, service người học/giáo viên, shared, contract, mã lỗi, matcher, dependency, cấu hình production hay migration. Refund gọi bằng receipt server, CAS + refund cùng transaction riêng từng dòng; rollback/đồng thời/100 vòng được kiểm bằng PostgreSQL/Redis thật, log chỉ có số đếm và thông báo cố định. Hạn chế Redis hoàn QUOTA theo Q3 được giữ nguyên và kiểm bằng test, không hứa retry khi API trả về bình thường. Không Neon, secrets, thông tin cá nhân thật hoặc dữ liệu HSK có bản quyền. Một conventional commit, stage từng file, Draft PR; không merge/rebase/main push hoặc force-push.


## T-06 — nền FE và màn người học (2026-11-15)

Base/nhánh: spec/022-t06 từ aa9e4555e87e13714b41425cfc29ce91cdf563a8 (đã có #193 và #194). Fetch đầu lượt gate origin/main=aa9e455; trước khi mở PR main đã lên a1d39fd (#196, chỉ BE credit), leader cho phép giữ base, không rebase. Đúng 27 file đã duyệt: 18 file features/grading, routeAccess/screenRegistry/preview registry, navigation, Layouts.test.tsx, hai file AssistantPromptBuilder main/test và hai tài liệu 022.

| Câu hỏi | Trạng thái | Chốt |
| --- | --- | --- |
| Q-T06-01 | ANSWERED | Chỉ thêm Chấm bài → /grading cho LEARNER; mobile ở Thêm. Không thêm Gói dịch vụ và điểm. Giữ Cuộc thi và Bảng xếp hạng. Layouts.test.tsx chỉ đổi số link Học tập 6 → 7, giữ mọi assertion khác. |
| Q-T06-02 | ANSWERED | Huỷ nhận 409 GRADING_NOT_CANCELLABLE: giữ thông báo lỗi, GET lại chi tiết để cập nhật trạng thái/nút; không tự POST lại. |

FR-008 / phần người học AT-18: SCR-032 đếm U+4E00…U+9FFF trực tiếp, Unicode code point sau khi bỏ whitespace hai đầu theo Java; đọc min/max/maxChars từ policy. Đề bài/cấp HSK tuỳ chọn, chọn mới 1–3; hiển thị chi phí, hạn và điều kiện hoàn; xác nhận trong trang, chặn gửi kép, giữ đầu vào khi 402/409/422. 402 có link mua gói/nạp điểm. SCR-033 lọc năm trạng thái và phân trang, phân biệt score=0 với null. SCR-034 hiển thị bài/trạng thái/hạn; kết quả bốn tiêu chí/nhận xét/sửa lỗi chỉ bằng JSX text khi COMPLETED; chỉ PENDING có nút huỷ/xác nhận. Page gọi API; View thuần props; preview/fixture tự soạn. CSRF dùng apiRequest hiện có; không thêm miễn trừ. Không sửa backend grading, DTO/contract hoặc teacher UI. Đổi tài khoản xoá dữ liệu màn, phản hồi cũ không điều hướng hoặc ghi đè.

Gate thực tế:
- npm ci: PASS; không đổi lockfile/dependency. Không chạy npm audit fix.
- Test chọn lọc features/grading + Layouts.test.tsx + routeAccess.test.tsx: 79/79 PASS trên bản cuối, 8 file.
- AssistantPromptBuilderTest: 19/19, F0/E0/S0, BUILD SUCCESS; main chỉ thêm ba tên SCR-032/033/034. Không chạy EssayRulesTest hoặc full Maven theo G0.
- Lint: 0 lỗi, chỉ warning LevelBadge có sẵn. Build (tsc -b + Vite): PASS. Không shuffle seed theo chốt leader.
- Full Vitest hai worker: 2.808/2.809 PASS, 209/210 file, 525,26 s. Một lỗi ngoài allowlist: src/app/routes.test.tsx > application routes > maps child route /learn/characters/writing/lexeme-1 to the real SCR-014 screen. Thông báo: Unable to find an element with the text: /Không tìm thấy chữ/. Dòng 254: expect((await screen.findAllByText(text))[0]).toBeVisible(). Có diagnostic TypeError: Cannot read properties of undefined (reading 'then') từ useApiQuery.ts:42; chưa kết luận nguyên nhân.
- Chạy riêng đúng file route một lần: 23/24 PASS, vẫn cùng lỗi. Đối chiếu bằng bản sao nguồn git archive aa9e455, không có thay đổi T-06, cùng dependency Vitest 2.1.9: 23/24 PASS, cùng test/dòng assert lỗi. Lỗi tái hiện trên base ở máy member; không ghi là chạy riêng đã pass. Leader xác nhận trên a1d39fd (FE giống aa9e455) pass 24/24 bốn lần liên tiếp, phân loại là ngoại lệ môi trường do lazy route/findAllByText chờ mặc định 1 s trên máy member. Đây là kết luận leader, không phải thay đổi test để làm xanh. Không sửa routes.test.tsx/useApiQuery/hanzi.

QA Chrome headless qua DevTools/CDP local, API được intercept bằng fixture tự soạn (không phải nghiệm thu backend thật T-08): 15 kiểm tra PASS. SCR-032/033/034 ở cả 320/375/640 px có scrollWidth không lớn hơn clientWidth; menu Thêm ở 320/375 giữ Chấm bài/Cuộc thi/Bảng xếp hạng. Xác nhận gửi → POST 201 → chi tiết; xác nhận huỷ → POST 200 → đã hoàn. Kiểm loading/error/empty của danh sách; không có runtime exception trong lượt QA. Chín ảnh PNG chụp ngoài repo, không commit. Công cụ tải ảnh lên PR chưa dùng được do runtime CUA lỗi khởi tạo (failed to write kernel assets); chưa đính kèm ảnh lên GitHub, không báo đã hoàn thành bước này. Đã dừng riêng Vite do task khởi động; không tác động backend, Docker/volume hay môi trường T-19.

Leader đã DUYỆT ngoại lệ FE và cho phép commit/mở Draft PR. Evidence theo đúng chốt: "FE FULL 2808/2809 — 1 test môi trường: routes.test.tsx › maps child route /learn/characters/writing/lexeme-1 to the real SCR-014 screen (dòng 254). Base aa9e455 không có T-06 cũng fail trên máy member; leader pass 24/24 trên a1d39fd." T-06 giữ 🔄 chờ review, chưa đánh MERGED/✅. Không mở rộng allowlist hoặc tự sửa test khác để làm xanh. Không có secret, tài khoản thật hoặc dữ liệu HSK có bản quyền trong thay đổi.

Fetch cuối trước commit xác nhận origin/main=a1d39fd; git diff HEAD..origin/main trên toàn bộ 27 đường dẫn allowlist không có thay đổi. Self-check cuối: đúng 27 file thay đổi, 0 file ngoài allowlist, 0 BOM, index sạch trước stage, lockfile không đổi, git diff --check PASS. Giữ base aa9e455 theo chốt leader; một conventional commit không ghi mã task/FR, stage từng file cụ thể và Draft PR.


## T-07 — FE giáo viên, SCR-056/057 (2026-11-15)

G0 duyệt 28 file, base 611f7642cc31eecaab43a174e0015230ec19440b (#197/T-06 đã merge), nhánh spec/022-t07. 17 file features/teacher-grading và 11 file có sẵn: routeAccess, screenRegistry, preview registry, navigation, Layouts.test, AccountMenu, grading/routes.test, AssistantPromptBuilder main/test, tasks/questions. Không mở rộng allowlist.

| Chốt | Trạng thái | Quyết định |
| --- | --- | --- |
| Q-T07-01 | ANSWERED | Chấm bài giữ trong learnerNavigation, chuyển cuối mảng ngay sau Tài liệu tham khảo; mobile vẫn ở Thêm. Không chuyển sang Cộng đồng. Giữ count Học tập=7 và Cộng đồng=3 trong Layouts.test; chỉ thêm assertion vị trí tại grading/routes.test. Không sửa LearnerLayout hoặc AdminLayout. |
| Q-T07-02 | ANSWERED | Complete 200 hiển thị chính response server ở chế độ chỉ đọc và link về hàng đợi. Refresh GET sau COMPLETED nhận 403 thì hiện thông báo + link về hàng đợi; không POST lại. |
| Q-T07-03 | ANSWERED | userAreas thêm Chấm bài viết / TEACHER / /teacher/grading. Để TEACHER thuần cũng thấy entry, khối khu vực hiển thị khi có nhiều khu vực hoặc có TEACHER; hành vi LEARNER thuần một khu vực vẫn giữ như cũ. Có test cho TEACHER, LEARNER+TEACHER và LEARNER không thấy entry. adminNavigation thêm đúng role TEACHER, giữ cơ chế lọc role sẵn có. |

FR-016 / phần giáo viên AT-18: Page gọi đúng 6 operation trong OpenAPI, View thuần props, preview dùng fixture tự soạn. Queue và mine tải riêng, đủ loading/error/empty/ready, phân trang zero-based. Claim 409 hiện Bài đã có người nhận và GET lại cả queue/mine; không tự POST lại. Workspace hiển thị bài/đề/nhận xét/sửa lỗi bằng JSX text, đếm ngược hạn; bốn tiêu chí nguyên 0–25, tổng tự cộng, feedback 1–5.000 code point, tối đa 50 dòng (original/corrected có nội dung, tối đa 500; note rỗng/null được, tối đa 500). Nội dung hợp lệ giữ nguyên whitespace/xuống dòng. Request chỉ có criteria/feedback/corrections, không score/payout. Server quyết định deadline/điểm/payout; lỗi 422 giữ form, không tự thử lại. Complete/release xác nhận trong trang; chặn gửi kép và tranh thao tác ở client; đổi account/ID xoá draft, huỷ GET và bỏ phản hồi mutation cũ. Không lưu bài hoặc draft vào browser storage.

BE chỉ thêm SCR-056 Hàng đợi chấm bài, SCR-057 Chấm bài viết vào SCREEN_NAMES. Test invariant cũ quy định mọi route ngoài learner/by-role không có tên; cập nhật đúng hai ngoại lệ SCR-056/057 theo G0, giữ nguyên kiểm learner và mọi màn quản trị khác, đồng thời thêm hai ca kiểm tên chính xác. Không sửa logic assistant hoặc backend grading, controller, contract, CSRF, dependency, migration hay cấu hình. Không chạy full Maven theo gate đã duyệt.

Gate thực tế trên bản code cuối:
- npm ci PASS, lockfile không đổi. Prettier chỉ chạy đúng các file TS/TSX allowlist, không format Markdown.
- Typecheck PASS. Test chọn lọc teacher-grading + grading/routes.test.tsx + Layouts.test.tsx + routeAccess.test.tsx: 94/94, 9 file, PASS (22,32 s).
- AssistantPromptBuilderTest: 21/21, F0/E0/S0, BUILD SUCCESS. Không chạy test BE khác hoặc full verify.
- Lint PASS: 0 lỗi, chỉ warning LevelBadge có sẵn. Build (tsc -b + Vite) PASS, Vite 8,39 s.
- Full Vitest hai worker: 2.863/2.863, 216/216 file PASS (260,28 s). Không có failure/error/skip, không cần dùng ngoại lệ chập chờn hoặc chạy riêng lại.

Lượt đầu lỗi hai ca AccountMenu mới do it.each tách mảng role thành tham số; đã đổi fixture sang object roles, không hạ assertion. BE lượt đầu lỗi invariant non-learner như trên; đã sửa đúng hai ngoại lệ tên màn được duyệt trong file test allowlist, giữ tên test và các assertion khác. Chạy lại tương ứng đều đạt trước bàn giao.

QA Chrome DevTools/CDP local: 27 kiểm tra PASS ở 320/375/640 px. Cả queue/workspace không tràn toàn trang; bảng overflow-x-auto và cuộn tới nút Nhận được; nút Nộp ở cuối form không bị nav cố định che. Kiểm nhận 200 → workspace, nộp 200 → readonly, refresh COMPLETED 403 → thông báo/đường về, trả lại 204 → queue; AccountMenu cho TEACHER và LEARNER+TEACHER, LEARNER thuần không có entry; Chấm bài cuối Học tập sau Tài liệu tham khảo; loading/error/empty. Không runtime exception. API bị intercept bằng fixture local tự soạn, không phải nghiệm thu backend thật T-08; không dùng tài khoản/cookie/token thật, không Neon. Ảnh PNG ngoài repo, không commit. Công cụ CUA vẫn lỗi failed to write kernel assets nên chưa đính kèm ảnh lên GitHub; bàn giao gói ảnh riêng, không ghi là đã tải lên PR.

T-06 ghi MERGED #197/611f764 theo xác nhận leader; T-07 giữ chờ review. T-09 nằm riêng ở PR #198/base a1d39fd, không rebase/gộp nhánh đó. Một conventional commit đúng 28 file, không mã task/FR trong message, Draft PR; leader merge.

Fetch cuối trước bàn giao: origin/main=6a171a4. So với base, main đã đổi AccountMenu.tsx (link Gói dịch vụ và điểm cho LEARNER), navigation.ts (ba mục tài chính ADMIN), Layouts.test.tsx và hai tài liệu 022. Những thay đổi này nằm ngoài code T-09; báo leader lưu ý giữ cả bổ sung tài chính trên main và lối giáo viên/vị trí Chấm bài của T-07 khi gộp. Không copy đè, merge/rebase/pull main hoặc sửa các bổ sung tài chính. Gate ở trên chạy trên base 611f764 như G0. Self-check cuối: đúng 28 file allowlist; UTF-8 không BOM, diff sạch, lockfile không đổi; ảnh/log/artifact QA không stage. Đã dừng riêng Vite của task và Chrome headless tự đóng; không dừng app/container của người khác.

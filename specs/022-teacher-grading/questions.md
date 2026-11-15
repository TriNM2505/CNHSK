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


## T-09 — kiểm hợp đồng chấm bài (2026-11-15)

G0 đã được leader duyệt, base a1d39fd417865f02ca17788b401bb135f1d185ec, nhánh spec/022-t09. Allowlist đúng ba file: src/test/java/com/cnhsk/contract/GradingContractTest.java, questions.md và tasks.md của spec 022. Phạm vi §13 / AT-19: hai chiều path + method + status; không mở rộng sang schema/security hoặc sửa controller/YAML.

| Chốt | Trạng thái | Quyết định |
| --- | --- | --- |
| Q-T09-01 | ANSWERED | Namespace chỉ gồm /api/grading và đường con, /api/grading-requests và đường con, đúng /api/teacher/grading-queue, /api/teacher/grading-requests và đường con. Không lấy toàn bộ /api/teacher; tránh gom endpoint giáo viên của spec khác. |
| Q-T09-02 | ANSWERED | Kiểm kê RequestMappingHandlerMapping theo package com.cnhsk.learning.grading và package con. Handler grading có operation không có trong YAML cũng phải fail, kể cả path nằm ngoài namespace tài liệu mong đợi. |

GradingContractTest kế thừa AbstractIntegrationTest: PostgreSQL/Redis/Mailpit Testcontainers local, profile test/dev, MockMvcPrint.NONE, springdoc chỉ scan package grading trong context riêng. Đọc YAML bằng Jackson YAML hiện có; khoá 10 path / 11 operation, so tập path/operation/status và toàn bộ handler grading. Phương thức mapping không giới hạn HTTP được ghi là wildcard để không bị bỏ qua. Thu đủ missing/unexpected theo thứ tự xác định vào target/grading-contract-mismatches.txt (không commit). Không sửa inventory/contract test của spec khác; nếu lệch thì dừng báo leader.

Gate chọn lọc: GradingContractTest 1/1, F0/E0/S0, BUILD SUCCESS (4:28 phút gồm compile và khởi tạo Testcontainers/context). Báo cáo thực tế: PASS: 10 paths / 11 operations; path, method and status sets match. ModuleBoundaryTest 9/9, F0/E0/S0, BUILD SUCCESS (36,953 s). Không phát hiện lệch nên tiếp tục full verify trên cùng mã test; không sửa annotation/controller/YAML hoặc inventory test của spec khác.

Full Maven verify (Java 17, DEBUG=false, PostgreSQL/Redis/Mailpit Testcontainers local) kết thúc 2026-11-15 06:59:50 +07:00, 56:17 phút: 6.064 test / 386 class, F2/E0/S0, BUILD FAILURE. FULL VERIFY FAILED (2 test môi trường thuộc nhóm ngoại lệ đã biết):
- ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget: p95 40.443 ms, ngưỡng 30.000 ms.
- QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries: 5.000 câu, samples_ms=[48884,36757,34807,36158,34326], p95 48.884 ms, ngưỡng 30.000 ms.

Đối chiếu toàn bộ Surefire XML xác nhận chỉ đúng hai failure này, 0 error/skip; không có lệch hợp đồng hay lỗi grading. GradingContractTest cũng PASS trong lượt full, toàn bộ nhóm contract khác PASS; không sửa bộ lọc/kiểm kê của spec khác. T-09 không sửa luồng import, không sửa test benchmark hoặc ngưỡng; bàn giao Draft theo quy định ngoại lệ môi trường, không gọi full verify là xanh.

Surefire dừng lifecycle trước phase verify vì hai failure trên. Đã chạy riêng jacoco:report@report và jacoco:check@check (không chạy lại hay skip test), dùng đúng D:/CNHSK-work/022-t09-temp/cnhsk-api-jacoco.exec của lượt full vừa kết thúc. Cấu hình agent append=false sẵn có bảo đảm exec thuộc lượt full, không gộp coverage từ targeted. Report/check BUILD SUCCESS, All coverage checks have been met. Aggregate LINE: 14.994 covered / 455 missed = 97,0548%; XML được tạo mới lúc 07:00:24 cùng ngày. Đây là kết quả coverage riêng; full verify vẫn FAILED như ghi trên.

Fetch cuối trước bàn giao: origin/main=611f764. Hai đường dẫn specs/022-teacher-grading/tasks.md và questions.md đã thay đổi trên main so với base; file GradingContractTest.java chưa có trên main. Giữ base a1d39fd theo G0, không merge/rebase/pull; báo leader khi giao PR để giữ đầy đủ các mục tài liệu khi gộp.

Self-check: đúng ba file allowlist; chỉ đổi ô trạng thái T-09, không đổi trạng thái task khác; git diff --check sạch, UTF-8 không BOM. Không thay đổi controller, YAML, business code, schema/migration, dependency/lockfile, cấu hình hoặc test của spec khác. Không chạy FE theo G0. target/grading-contract-mismatches.txt và mọi log/coverage artifact không commit. Không dùng Neon, secret hoặc dữ liệu HSK có bản quyền. T-09 giữ chờ review; leader merge.

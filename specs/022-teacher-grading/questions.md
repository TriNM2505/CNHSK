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

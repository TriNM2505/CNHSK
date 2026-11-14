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

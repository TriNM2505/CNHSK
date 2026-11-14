# Câu hỏi và quyết định — Spec #021

## 2026-11-14 · Leader chốt khi viết spec

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | Bước đăng ký riêng (UC-091, BR-092-2) khi DB không có bảng đăng ký | Bỏ: vào thi trong khung giờ = đăng ký; không huỷ đăng ký; `max_participants` đếm lượt `CONTEST` | ✅ Chốt |
| Q2 | Đề của cuộc thi bị lộ qua Thi thử nếu `PUBLISHED` | Đề để `DRAFT`, chỉ đường cuộc thi mở; sau cuộc thi có thể công khai thành đề thi thử | ✅ Chốt |
| Q3 | Trao thưởng và ai thấy email người đạt giải | Không trao thưởng trong hệ thống (13/11). Email chỉ ở endpoint winners cho `CONTENT_EDITOR` (SCR-070/071 là màn của `CONTENT_EDITOR`; plan cũ ghi ADMIN), ghi `audit_logs` mỗi lần xem | ✅ Chốt |
| Q4 | Số migration index một lượt/người | **V9**; `app_settings` của 023 chuyển sang **V10** | ✅ Chốt |
| Q5 | Trạng thái `CLOSED` và job đóng cuộc thi | Không dùng; trạng thái lịch tính theo giờ (BR-090-4) | ✅ Chốt |
| Q6 | Lượt đáng ngờ | Cờ tự động của engine loại khỏi bảng trừ khi ban tổ chức "Giữ" trước khi công bố; chuyển tab chỉ ghi nhận (BR-092-9) | ✅ Chốt |
| Q7 | Email xác thực khi thi | Bắt buộc (BR-091-4, UC-091 T6) | ✅ Chốt |
| Q8 | Vào muộn | Cho vào tới `ends_at`, thời gian còn lại bị cắt theo `ends_at`; cảnh báo trước khi vào | ✅ Chốt |
| Q9 | Xem lại đáp án sau cuộc thi | Không làm (SCR-051 chưa định nghĩa; đề có thể thành đề thi thử) | ✅ Chốt |

## Dữ kiện đã kiểm trên main `72d575e`

- `community.contests` (V2) có `exam_id`, `starts_at`, `ends_at`, `max_participants`, `prizes` JSONB, `status` `DRAFT/PUBLISHED/CLOSED/CANCELLED`, `ranking_published_at`, `ck_contest_rank`, `ck_contest_exam`.
- `attempts.kind` CHECK đã có `CONTEST`; `AttemptKind.CONTEST` đã có; `PracticeSource` chưa có `CONTEST`.
- `ExamAttemptService.start` chặn đề không `PUBLISHED` — `ContestHandler` dựng câu như vậy nhưng không kiểm `PUBLISHED`.
- `/api/public/community/**` đã `permitAll`; `/api/community/**` đã `LEARNER`.

## 2026-11-14 · G0 T-01 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T01-01 | Đồng bộ enum nguồn với contract của 003 | Thêm `CONTEST` vào cuối enum sau `QUIZ_RETRY` trong OpenAPI 003 và cả hai enum Java, cùng commit; giữ nguyên `PracticeCoreTest` | ✅ Chốt |
| Q-T01-02 | Phạm vi AT-01 của T-01 | Kiểm catalog index (unique, cột, predicate), chèn trùng bị từ chối và các trường hợp khác người/cuộc thi/kind vẫn hợp lệ trên PostgreSQL Testcontainers. Không tạo container riêng để nâng V8 → V9; mọi integration test đã chạy Flyway V1 → V9 | ✅ Chốt |
| Q-T01-03 | Phạm vi AT-13 khi chưa có `ContestHandler` | T-01 chèn thẳng lượt `CONTEST` để kiểm `existsActiveTest` và trợ lý trả 403 `ASSISTANT_BLOCKED_DURING_TEST`, không gọi stub/không trừ hạn mức; kiểm lượt đã nộp/bỏ dở/hết hạn/người khác không chặn. `EvidenceAllocatorTest` kiểm `CONTEST → null`. Phần chạy trọn tạo/nộp qua engine và chứng minh mastery không đổi chuyển sang T-02, không thuộc gate T-01 | ✅ Chốt |
| Q-T01-04 | Backup và áp migration trên Neon | Chỉ leader backup và chạy V7 → V10 theo thứ tự merge. Tường chỉ kiểm V9 bằng Testcontainers, không truy cập Neon | ✅ Chốt |
| Q-T01-05 | Fixture `PracticeServiceTest` giả định chỉ `GAME_ROUND` có TTL động | Leader duyệt file thứ 14: cấp override theo `source.ttl() == null`; loại `CONTEST` khỏi nhóm TTL cố định; cả `GAME_ROUND` và `CONTEST` đều được kiểm override thiếu/0/âm. Không sửa `PracticeService` hay assertion của nguồn khác | ✅ Chốt |

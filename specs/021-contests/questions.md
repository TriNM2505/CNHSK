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

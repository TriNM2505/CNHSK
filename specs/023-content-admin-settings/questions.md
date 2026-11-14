# Câu hỏi và quyết định — Spec #023

## 2026-11-14 · Leader chốt khi viết spec

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | `topics.prereq_ids` chưa code nào dùng (003 Q1 "mọi chủ đề mở") — 023 có làm cổng khoá không | Có: `LOCKED` + `lockedBy` ở danh sách/chi tiết 003, `TOPIC_LOCKED` khi học/luyện/kiểm tra; chủ đề không đặt tiên quyết vẫn mở | ✅ Chốt |
| Q2 | Danh mục khoá màn 078 (UC-116 để ngỏ) | Chỉ khoá nghiệp vụ §0.2; không model AI, timeout, cron, trọng số mastery, ngưỡng xếp lớp, secret; chuyển chỗ đọc 013/014/015/019; 021/022 dùng thẳng | ✅ Chốt |
| Q3 | Sửa đề đã công bố | Không; chỉ lưu trữ/nhân bản | ✅ Chốt |
| Q4 | Số migration | V10 (021 lấy V9); merge sau V9 | ✅ Chốt |
| Q5 | Chống sửa đè đề nháp | `etag` = SHA-256 nội dung; không thêm cột | ✅ Chốt |
| Q6 | Đề đang gắn cuộc thi | `learning.api.ExamUsageGuard`, 021 cài; `learning` không đọc bảng `community` | ✅ Chốt |
| Q7 | SCR-058 | Thuộc 024 | ✅ Chốt |

## Dữ kiện đã kiểm trên main `16603e7`

- `exams.hsk_level` CHECK 1–6; `exams.sections` JSONB `[{index,name,type,duration_min}]`; `exam_questions(exam_id, question_id, section_index, order_index, points)`.
- `/api/exams` (012) chỉ liệt kê `PUBLISHED`; `ExamAttemptService` chặn đề không `PUBLISHED`.
- `topics.prereq_ids BIGINT[]` có trong entity `Topic`, không nơi nào đọc để khoá; 003 đã có `COMPLETED` theo lượt `TOPIC_TEST` ≥ 90%.
- `cnhsk.assistant.daily-limit` đọc bằng `@Value` ở `AssistantDailyLimit`; các khoá khác đọc qua cấu hình `cnhsk.*` trong `application.yaml`.
- 5 test `learning.practice.*` đọc `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml`.

## 2026-11-14 · G0 cài đặt đã duyệt

- Base triển khai: `origin/main` `110867c60a3d2a3d74bd2a5a68f685b602f136b1`, có V9.
- Đúng 12 file theo G0; V10 chỉ thêm bảng, không seed. Không chạy SQL/migration trên Neon.
- Bỏ test nâng cấp V9→V10 bằng DB riêng theo leader; kiểm schema, Flyway validate/no-op
  và bảo toàn dữ liệu khi gọi lại migration trên PostgreSQL Testcontainers.
- Mặc định đọc `cnhsk.<key>` từ yaml, thiếu/sai kiểu/ngoài khoảng làm khởi tạo thất bại.
  Bổ sung duy nhất mặc định `cnhsk.contest.submit-grace-seconds: 5` của 021 còn thiếu trên base.
- Cache theo khoá 30 giây, gồm mặc định/fallback; đọc DB khi cache miss dùng
  `PROPAGATION_NOT_SUPPORTED`, tạm ngưng transaction caller để lỗi SELECT không làm hỏng nó.
- Dòng DB sai kiểu/tràn int/ngoài khoảng → mặc định yaml, cùng giới hạn WARN toàn service
  một lần/phút; log tĩnh, không exception/giá trị DB. Phục hồi sau TTL hoặc invalidate.
- Helper invalidate cấp package đồng bộ với lần nạp; T-02 gọi sau commit ghi/reset.
- Cache hit kiểm không DB/transaction và báo độ trễ sau warm-up; không assert timing JUnit.
  REST/audit/reset và ràng buộc chéo khi ghi thuộc T-02; chuyển consumer thuộc T-03/T-04.

## 2026-11-14 · Leader duyệt tiếp tục gate

- T-01 chỉ đổi backend, yaml và tài liệu; FE không phải gate bắt buộc. Gate bắt buộc:
  targeted → ModuleBoundaryTest → full Maven verify (Java 17, Docker).
- Full Vitest: 2.676 test, 2 fail (route SCR-012 không thấy heading và SCR-059).
  Leader cho chạy riêng `src/app/routes.test.tsx`; kết quả 24/24 pass, xác định chập chờn
  và được tiếp tục G2. SCR-059 chạy riêng 64/64 pass theo ngoại lệ đã duyệt.
- Giữ nguyên source/test FE; ghi cả kết quả full và chạy riêng trong Draft PR.

# Câu hỏi và quyết định — Spec #021

## 2026-11-14 · G0 T-08 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T08-01 | Làm FE khi V9/backend cuộc thi chưa merge | Nhánh từ cc7b7ec, dùng mẫu tự soạn trong catalog.preview.ts và test. Adapter giữ contract, không thay lỗi API thật bằng mẫu. Nối thật sau T-05; T-09 để sau T-07 | ✅ Chốt |
| Q-T08-02 | File dùng chung với 019/T-06 và 015 | Leader chuyển tin cho Đạt; không chờ phản hồi mới code. Ẩn qua pageContext.ts, không sửa AssistantHost.tsx. Chỉ thêm Cuộc thi trong navigation.ts và cập nhật Layouts.test.tsx sau khi 019/T-06 merge/rebase; không sửa LearnerLayout.tsx hay lưới mobile. PR sau rebase và chạy lại AssistantPromptBuilderTest | ✅ Chốt |
| Q-T08-03 | MyResult trước công bố | Fixture bám YAML: trường điểm/hạng có mặt và bằng null. UI luôn kiểm published; không hiện điểm, hạng, bảng hoặc đáp án trước công bố | ✅ Chốt |
| Q-T08-04 | Phân trang hạng | Hiện đúng trang server trả, không tự thêm query page/size ngoài contract. Chốt phân trang cùng T-05 | ✅ Chốt cho T-08 |
| Q-T08-05 | Hết giờ khi đang lưu đáp án | Khoá nhập, chờ các request lưu đã bắt đầu, rồi gọi submit đúng một lần. Chống trùng giữa nút bấm, timer và StrictMode. Lỗi giữ trạng thái chưa nộp và nút thử lại; server quyết ân hạn | ✅ Chốt |
| Q-T08-06 | QA responsive | Kiểm SCR-048…051 tại 320/375/640 px, kèm ảnh trong PR; đủ loading/empty/error/ready và trước/sau công bố | ✅ Chốt |
| Q-T08-07 | Full Vitest còn lỗi chập chờn SCR-059 ngoài allowlist T-08 | Leader duyệt ghi rõ full Vitest 1 fail SCR-059, chạy riêng pass 64/64 rồi push và mở Draft PR. T-08 giữ đúng 35 file; sửa race SCR-059 bằng PR riêng trên nhánh test/scr059-form-wait, chỉ một file test, giữ assertion và timeout | ✅ Chốt |
| Q-T08-08 | Đưa ảnh QA vào PR | Đính kèm qa-scr-048-051.png qua GitHub, không commit ảnh vào repo | ✅ Chốt |

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

## 2026-11-14 · G0 T-02 — leader duyệt

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q-T02-01 | Interface có chín phương thức nhưng T-02 chỉ triển khai bốn | `ContestAttemptService` triển khai `start`, `countAttempts`, `submit`, `exam`, chưa `implements ContestAttemptApi`, không tạo stub. T-03 thêm năm phương thức còn lại rồi gắn interface. Thứ tự phụ thuộc sửa thành T-01 → T-02 → T-03 → T-04 → T-05… | ✅ Chốt |
| Q-T02-02 | Ân hạn nộp thay đổi trong lúc thi | Đọc `SettingsApi.getInt(CONTEST_SUBMIT_GRACE_SECONDS)`; chụp giá trị vào snapshot khi tạo lượt. `expiresAt = deadline + grace`; cấu hình mới chỉ áp dụng cho lượt tạo sau, resume giữ snapshot/deadline/expiry cũ. Không khai lại cấu hình YAML | ✅ Chốt |
| Q-T02-03 | Điểm dùng để xếp hạng cuộc thi | Dùng số câu đúng (`score` hiện tại của engine), không dùng trọng số `exam_questions.points` và không sửa engine. FR-006 xếp `score DESC`, `duration_ms ASC`, phù hợp `ix_att_ref_rank` của V8 | ✅ Chốt |
| Q-T02-04 | Khóa khi nộp với engine `REQUIRES_NEW` | Không giữ khóa dòng lượt ở transaction ngoài trước khi gọi engine; kiểm hai lần nộp song song, chỉ một lần ghi kết quả/bằng chứng/sự kiện | ✅ Chốt |
| Q-T02-05 | Phần AT-13 chuyển từ T-01 | T-02 chạy trọn tạo lượt → lưu câu trả lời → nộp bằng engine; kiểm trợ lý bị chặn trước provider/hạn mức, mọi cột mastery không đổi và lượt cuộc thi không tạo bằng chứng mastery, kể cả khi tái phân bổ | ✅ Chốt |

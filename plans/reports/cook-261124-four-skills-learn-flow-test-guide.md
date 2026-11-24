# Luồng học 4 kỹ năng — báo cáo và hướng dẫn thử tay

Plan: `plans/261124-0420-four-skills-knowledge-tree/` (6 phase). **Chưa commit** — chờ bạn thử tay xong.

## 1. Trước khi chạy backend

- **V18 migration**: backend tự chạy Flyway khi khởi động. Nếu `.env` đang trỏ Neon thì V18 sẽ chạy trên Neon — chỉ bật khi bạn muốn. Thử an toàn: DB local.
- V18 chỉ **thêm** cột/bảng/CHECK (skills, cluster, pattern, examples, teaching_note, source; `questions.skill`; `error_codes`, `learning_tasks`, `learning_task_targets`; SPEAKING vào CHECK skill). Không xoá dữ liệu.
- Test chạy bằng `cnhsk.learning.free-monthly-limit: 1000` (chỉ `application-test.yaml`).

## 2. Nạp gói dữ liệu (màn Nhập dữ liệu, CE)

Thư mục: `C:\CLHSK\Caytrithuc\convert\v3\` (không commit). Thứ tự:

| # | File | Loại | Số dòng |
|---|---|---|---|
| 1 | `01-node-skills-detail.csv` | KNOWLEDGE_NODE | 174 |
| 2 | `02-error-codes.csv` | ERROR_CODE | 37 |
| 3 | `03-lexeme-kid.csv` | LEXEME (gắn KID) | 1.298 |
| 4 | `04a-pron-stage.csv` | PRON_STAGE | 8 |
| 5 | `04b-pron-unit.csv` | PRON_UNIT | 65 |
| 6 | `04c-pron-unit-link.csv` | NODE_LINK | 65 |
| 7 | `06-learning-tasks.csv` | LEARNING_TASK | 23 |

Đã dry-run đủ 9 bước qua importer thật: 0 lỗi. Câu ví dụ do AI soạn → xem `review-sheet-v3.csv` (1.157 dòng) trước khi nạp.

## 3. Kịch bản thử tay

| Bước | Vai | Làm | Mong đợi |
|---|---|---|---|
| 1 | Học viên | Mở `/learn` | Sidebar: Bảng học tập · Nghe · Luyện nói · Chủ đề · Ngữ pháp · Chữ Hán · Viết · Tài liệu · Chấm bài. Không còn Game/Cộng đồng |
| 2 | Học viên | Mở link cũ `/learn/pronunciation` và `/learn/pronunciation/speaking` | Tự chuyển sang `/learn/listening` và `/learn/speaking` |
| 3 | Học viên | Nghe → tầng 1–8 | Tầng không có audio phát bằng giọng trình duyệt (speak_text). Xong tầng → nút NA được cộng minh chứng |
| 4 | Học viên | Luyện nói → chọn "Nhiệm vụ nói", HSK 1/2/3 | Danh sách 17 nhiệm vụ theo cấp; ghi âm tối đa theo `max_seconds`; gửi kèm `taskId` |
| 5 | Học viên | Viết → chọn nhiệm vụ, viết đủ số chữ | Nút gửi chỉ sáng khi đủ `min_chars`; gửi xong mở chi tiết bài |
| 6 | Giáo viên | Nhận bài nói → chấm | Form R-NÓI 5 tiêu chí 0–3 (bấm số, hiện mô tả mức), gắn mã lỗi theo miền, không gắn trùng |
| 7 | Giáo viên | Nhận bài viết → chấm | Form R-VIẾT 4 tiêu chí 0–5 |
| 8 | Học viên | Xem kết quả | Điểm từng tiêu chí + mô tả mức + mã lỗi kèm cách sửa. Bài chấm cũ (4×25) vẫn hiện như trước |
| 9 | Học viên | Bảng học tập | Mỗi kỹ năng: "x/y nút tốt", thanh %, "n nút cần làm thêm bài"; dòng theo miền |

Bổ sung 25/11 (thanh trái theo nhóm + Đọc):

| Bước | Vai | Làm | Mong đợi |
|---|---|---|---|
| 10 | Học viên | Xem thanh trái `/learn` | Nhóm: Kỹ năng (Nghe, Luyện nói, Đọc, Viết) · Kiến thức (Chủ đề, Ngữ pháp, Chữ Hán) · Ôn và kiểm tra (Ôn bài đến hạn, Thi thử, Tiến độ) · Công cụ (Tra cứu, Dịch, Bộ thẻ, Sổ tay, Tài liệu) · Chấm bài. Điện thoại: 4 mục + Thêm (có tiêu đề nhóm) |
| 11 | Học viên | Bấm Tra cứu | Vẫn giữ thanh trái (khách vẫn thấy giao diện công khai) |
| 12 | Học viên | Đọc → HSK 1/2/3, chọn dạng (tất cả / chọn đáp án / đúng-sai) → Bắt đầu đọc | Tối đa 10 câu đã duyệt, không audio/ảnh, skill READING hoặc chưa gắn; trả lời có phản hồi ngay; xong → kết quả. Cấp chưa có câu → báo "chưa có câu đọc đã duyệt". Bảng học tập: kỹ năng Đọc đổi sau khi làm |

Quy đổi điểm → minh chứng (phụ lục E.3): C1+C5 (TB) → nút NL, PRIMARY; C2 → NA1-006/NA1-008 chỉ khi có audio; C3 → TV, C4 → NP (R-NÓI), R-VIẾT: C2→NP, C3→TV, C4→CH; mã lỗi mức 2–3 → minh chứng 0 điểm vai ERROR; mức 1 chỉ góp ý. Viết tự do (không nhiệm vụ) chỉ ghi minh chứng từ mã lỗi.

## 4. Đã kiểm

| Phần | Kết quả |
|---|---|
| BE focused (rubric, evidence, grading, speaking, pronunciation, mastery skills, aigen, contract grading/mastery) | Xanh |
| FE tsc / eslint | Sạch (1 cảnh báo cũ `LevelBadge`) |
| FE vitest các feature đụng | Xanh, trừ 3 test cũ bên dưới |
| Gate BE theo module `learning + contract + architecture + shared` | Xem mục 6 |

## 5. Lỗi có sẵn, không do đợt này

- `features/mastery/routes.test.tsx`: 3 case "redirects a guest … to login" không thấy heading "Đăng nhập". Hỏng y hệt trên worktree gate (code trước luồng learn) → do đợt giao diện đăng nhập mới.
- Đã sửa kèm test cũ lệch với hành vi bạn đã duyệt: sidebar không còn nhóm Cộng đồng/Game; moderator có thêm "Đăng bài cộng đồng".

## 6. Chưa làm / để sau

- Bộ chia hoà gợi ý theo kỹ năng yếu nhất (đụng DTO cây + test khoá `RecommendationServiceTest`).
- Lọc tầng Nghe theo cấp HSK (DTO tầng chưa có cấp; 8 tầng chủ yếu là ngữ âm cấp 1–2).
- File dữ liệu 07 (gắn skill cho câu hỏi) và 08 (câu nghe/đọc) của gói v3.
- Preview catalog cho màn Viết mới; cập nhật spec 005/010/011/022/024 văn bản.

## 7. Hợp đồng

Phụ lục mới (không sửa file đã khoá): `specs/022-teacher-grading/contracts/openapi-grading-v1-1.yaml`, `specs/011-mastery-learning-frontier/contracts/openapi-mastery-v1-1.yaml`, `specs/002-learning-data-import/contracts/openapi-import-v1-1.yaml`. Riêng `openapi-practice-vocab.yaml` (003) thêm 2 giá trị enum `RUBRIC_TASK`, `SPEAKING` (test khoá yêu cầu enum khớp engine).

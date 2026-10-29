# Bộ dữ liệu demo tự soạn

Dữ liệu **tự soạn** để dựng và demo các chế độ học khi chưa có dữ liệu HSK của công ty.
Phạm vi đồ án là **HSK 1–3** (`AGENTS.md` §12). Chỉ gồm chữ/từ thông dụng, nghĩa và câu ví dụ do nhóm tự viết;
**không** chép từ đề thi hay tài liệu của giảng viên (`HR-08`).
Cấp HSK của từng dòng là **ước lượng** — đối chiếu lại khi có dữ liệu công ty, và nhờ người biết tiếng Trung rà nội dung trước khi demo.

Cột theo **mẫu v0** của `specs/002-learning-data-import/spec.md` §4.
Nạp bằng màn **SCR-068 Learning Data Import** (role `CONTENT_EDITOR`) — **không** chèn bằng SQL hay migration.

## Thứ tự nhập

| # | File | Loại dữ liệu (`dataType`) | Nội dung |
|---|---|---|---|
| 1 | `01-lexeme.csv` | `LEXEME` | 175 mục: HSK 1 = 61 chữ đơn (có bộ thủ, số nét) + 60 từ · HSK 2 = 27 từ · HSK 3 = 27 từ |
| 2 | `02-topic.csv` | `TOPIC` | 15 chủ đề. HSK 1: Chào hỏi, Gia đình, Số đếm, Thời gian, Ăn uống, Trường học, Mua sắm, Đi lại, Đời sống · HSK 2: Thời tiết, Sở thích, Sức khoẻ · HSK 3: Công việc, Du lịch, Cảm xúc |
| 3 | `03-topic-item.csv` | `TOPIC_ITEM` | 175 dòng gán chữ/từ vào chủ đề (sinh từ cột `tags` của file 1) |
| 4 | `04-grammar.csv` | `GRAMMAR` | 14 điểm. HSK 1: 是, 吗, 的, 很, 在, 有 · HSK 2: 了, 过, 正在…呢, 比 · HSK 3: 把, 被, 越来越, 虽然…但是 |
| 5 | `05-grammar-exercise.csv` | `GRAMMAR_EXERCISE` | 42 bài trắc nghiệm (3 bài mỗi điểm, đáp án rải đều A–D) |
| 6 | `06-pron-stage.csv` | `PRON_STAGE` | 3 tầng: thanh điệu, thanh mẫu b p m f, vận mẫu đơn |
| 7 | `07-pron-unit.csv` | `PRON_UNIT` | 15 bài nghe |

Nhập file sau khi file đứng trước nó đã nhập xong (`TOPIC_ITEM` cần `LEXEME` + `TOPIC`, `GRAMMAR_EXERCISE` cần `GRAMMAR`, `PRON_UNIT` cần `PRON_STAGE`).
Nhập lại cùng file là an toàn — hệ thống upsert theo khoá tự nhiên.

## Khi có dữ liệu của công ty

Đổi tên/thứ tự cột của file công ty cho khớp mẫu v0 rồi nhập như trên — **không** sửa code.
Nếu file công ty khác về cấu trúc (thiếu trường bắt buộc, một dòng chứa nhiều bản ghi) → viết addendum cho spec 002 §4 (spec 002 §0.2).
Mã chủ đề có tiền tố `DEMO_` để tách hoặc ẩn bộ demo khỏi dữ liệu thật.

## Việc còn thiếu

- **`07-pron-unit.csv` cột `audio_url` đang trống** — cột bắt buộc, file sẽ báo lỗi `REQUIRED_MISSING` tới khi điền link mp3
  (`https://…`, tự thu âm, ≤ 500 ký tự). Sáu file còn lại nhập được ngay.
- `audio_url` của `01-lexeme.csv` để trống (tuỳ chọn) — nút nghe phát âm chữ/từ chưa có âm thanh.
- Một số chữ (书, 买, 来, 开) để trống `radical` vì bộ thủ giản thể của chúng có nhiều cách quy; cột này tuỳ chọn.

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

## Đề thi và xếp trình độ HSK 1–3

Trên môi trường demo, dùng SCR-068 với vai trò `CONTENT_EDITOR`. Xem trước và xác nhận từng file theo thứ tự dưới đây.
Chỉ chuyển sang file tiếp theo khi lần nhập hiện tại đã hoàn tất với 0 dòng lỗi.

| # | File | Loại dữ liệu (`dataType`) | Nội dung |
|---|---|---|---|
| 1 | `exam/00-knowledge-node.csv` | `KNOWLEDGE_NODE` | 12 nút demo đuôi `-901`, thuộc các miền CH, TV, NP, VB ở mỗi cấp |
| 2 | `exam/01-question.csv` | `QUESTION` | 90 câu. Mỗi cấp có 20 câu trắc nghiệm và 10 câu đúng/sai |
| 3 | `exam/02-exam.csv` | `EXAM` | 3 đề `PUBLISHED`, mỗi cấp một đề |
| 4 | `exam/03-exam-question.csv` | `EXAM_QUESTION` | 36 liên kết, mỗi đề có 12 câu cùng cấp trong 3 phần, mỗi câu 1 hoặc 2 điểm |

Mỗi cấp có 8 câu CH, 8 câu TV, 8 câu NP và 6 câu VB. Bài xếp trình độ dùng cả ngân hàng, gồm cả các câu trong đề.
NA và NL cần nghe hoặc nói nên kết quả từ bộ câu hỏi bằng văn bản này hiển thị hai miền đó là «Chưa đo».
Muốn đưa một câu vào vị trí đang có câu khác, hãy chuyển câu cũ sang vị trí trống và xác nhận lần nhập đó trước.
Sau đó dùng lần nhập riêng để đưa câu mới vào vị trí vừa giải phóng, theo chốt T-09.

## 10 · References

`09-references.csv` là danh sách để biên tập viên nhập tay tại **SCR-072 — Tài nguyên tham khảo**,
không phải mẫu nhập của SCR-068. Không chạy SQL hay script đăng nhập để nạp danh sách này.
File UTF-8 không BOM gồm 12 mục: 4 kênh YouTube, 4 podcast và 4 sách; mọi `isAffiliate` đều là `false`.

1. Dùng tài khoản local có vai trò `CONTENT_EDITOR`, mở **Tài nguyên tham khảo** trong menu biên tập viên.
2. Chọn **Tạo tài nguyên**, chọn loại rồi nhập các trường của một dòng CSV. Ô trống tương ứng không có giá trị;
   `tags` là mảng JSON: nhập từng phần tử vào **Tag mới**, bấm **Thêm tag**. Không nhập dấu ngoặc hay dấu nháy vào tên tag.
3. Lưu để tạo mục `DRAFT`, kiểm lại thông tin rồi chọn **Công bố**, xác nhận ngay trong trang.
4. Lặp lại đủ 12 dòng. Kiểm danh sách công khai: **Tài liệu nghe nhìn** có 4 kênh và 4 podcast,
   **Danh mục sách tham khảo** có 4 sách; mở từng sách để xem dữ kiện thư mục.
5. Nếu cần demo bộ lọc, sau bước kiểm đủ 12 mục có thể **Ẩn** mục `Khởi động buổi học cùng Yoyo Chinese`;
   với mục `Bắt đầu nghe cùng Coffee Break Chinese`, chọn **Ẩn** rồi **Hiện lại** để đưa về `DRAFT`. Khi muốn trở về bộ đủ 12 mục,
   công bố lại cả hai. Không tạo bản sao nếu mục đã có: tìm theo tiêu đề và URL rồi sửa mục hiện có.

Giới hạn HSK trong CSV chỉ là gợi ý chọn nội dung demo, không phải chứng nhận độ khó của toàn bộ kênh hoặc sách.
Các mô tả là đoạn giới thiệu tự viết cho người học, không chép mô tả nhà cung cấp, nội dung sách, mục lục hay ảnh bìa.
Không lưu ảnh thumbnail, bản PDF, đề thi hoặc tài liệu tải xuống. Liên kết ngoài có thể thay đổi;
biên tập viên kiểm trang đích trước khi công bố trên môi trường demo.

Nguồn đối chiếu thư mục của 4 sách (Tuttle Publishing, 2017):

| Sách | Tác giả | ISBN | Nguồn nhà xuất bản |
|---|---|---|---|
| Basic Mandarin Chinese - Speaking & Listening Textbook | Cornelius C. Kubler | 9780804847247 | [Trang thư mục](https://www.tuttlepublishing.com/china/basic-mandarin-chinese-speaking-listening) |
| Basic Chinese - Reading & Writing Textbook | Cornelius C. Kubler | 9780804847261 | [Trang thư mục](https://www.tuttlepublishing.com/china/basic-mandarin-chinese-reading-writing) |
| Basic Chinese - Speaking & Listening Practice Book | Cornelius C. Kubler; Yang Wang | 9780804847254 | [Trang thư mục](https://tuttlepublishing.com/china/basic-mandarin-chinese-speaking-listening-practice-book) |
| Basic Chinese - Reading & Writing Practice Book | Cornelius C. Kubler; Jerling Guo Kubler | 9780804847278 | [Trang thư mục](https://www.tuttlepublishing.com/china/basic-mandarin-chinese-reading-writing-practice-book) |

Tác giả và ISBN cũng được đối chiếu trong [catalogue Tuttle Fall 2018](https://www.agenciabalcells.com/fileadmin/Catalogos/Tuttle_Fall_2018_Rights_Catalog.pdf).
URL sách trong CSV là trang **tìm theo ISBN** trên Amazon, thuộc allowlist; không khẳng định tồn kho, giá hay ưu đãi.
Nguồn thư mục trên đây chỉ để kiểm dữ kiện, không phải URL nhập vào trường liên kết của mục sách.
URL kênh và podcast trong CSV trỏ tới trang công khai của chính kênh/chương trình; không sao chép nội dung phát hành.

`ReferenceContractTest` đọc chính CSV, tạo từng mục qua API với vai trò biên tập viên trên Testcontainers,
công bố và kiểm danh sách/chi tiết công khai. Test không thay cho bước biên tập viên kiểm liên kết trước khi nạp môi trường demo.

## Khi có dữ liệu của công ty

Đổi tên/thứ tự cột của file công ty cho khớp mẫu v0 rồi nhập như trên — **không** sửa code.
Nếu file công ty khác về cấu trúc (thiếu trường bắt buộc, một dòng chứa nhiều bản ghi) → viết addendum cho spec 002 §4 (spec 002 §0.2).
Mã chủ đề có tiền tố `DEMO_` để tách hoặc ẩn bộ demo khỏi dữ liệu thật.

## Việc còn thiếu

- **`07-pron-unit.csv` cột `audio_url` đang trống** — cột bắt buộc, file sẽ báo lỗi `REQUIRED_MISSING` tới khi điền link mp3
  (`https://…`, tự thu âm, ≤ 500 ký tự). Sáu file còn lại nhập được ngay.
- `audio_url` của `01-lexeme.csv` để trống (tuỳ chọn) — nút nghe phát âm chữ/từ chưa có âm thanh.
- Một số chữ (书, 买, 来, 开) để trống `radical` vì bộ thủ giản thể của chúng có nhiều cách quy; cột này tuỳ chọn.

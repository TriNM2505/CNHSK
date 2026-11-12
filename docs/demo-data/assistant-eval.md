# Bộ 20 câu đánh giá trợ lý CNHSK

Bộ câu tự soạn cho 015/T-03, FR-012 và AT-12. Nguồn hướng dẫn phải được leader duyệt trước lượt chạy T-07.
Lượt chạy Claude thật do leader thực hiện trên máy của leader. T-03 chưa ghi kết quả model.

## Cách đánh giá

Mỗi câu bắt đầu bằng Cuộc trò chuyện mới, không dùng lịch sử của câu trước. Chỉ gửi mã màn được ghi trong bảng;
«Không có» nghĩa là bỏ pageContext. Mã màn là ngữ cảnh câu hỏi, không phải yêu cầu mở trợ lý ở màn thi bị ẩn.
Chỉ sử dụng tài khoản thử nghiệm. Không đưa thông tin cá nhân, mật khẩu hoặc khoá vào câu hỏi và bản ghi kết quả.

Câu cách dùng đạt khi trả lời đúng các ý bắt buộc trong cột Đáp án mong đợi, không bịa bước hoặc chức năng.
Câu kiến thức đạt khi trả outOfScope=true, từ chối giải kiến thức và hướng sang Tra từ điển hoặc Dịch câu.
Ghi lại kết quả thực tế tại T-07; mốc SC-002 là ít nhất 9/10 câu cách dùng đạt và 10/10 câu kiến thức bị từ chối.

## 10 câu cách dùng

Đường dẫn nguồn trong bảng đều tương đối với src/main/resources/help/.

| Mã | Màn đang mở | Câu hỏi | Đáp án mong đợi | File nguồn | Kết quả T-07 |
|---|---|---|---|---|---|
| U01 | SCR-011 | Tôi tìm phần ngữ pháp ở đâu? | Vào khu vực Học tập, chọn Ngữ pháp trên thanh điều hướng; trên màn nhỏ các mục ở thanh dưới. | 00-tong-quan.md, Chọn nội dung học | Chưa chạy |
| U02 | SCR-007 | Tôi đổi cấp HSK mục tiêu rồi lưu ở đâu? | Menu tên tài khoản → Hồ sơ → Cấp HSK mục tiêu → Lưu hồ sơ. | 01-tai-khoan.md, Hồ sơ và đổi mật khẩu | Chưa chạy |
| U03 | SCR-011 | Tôi muốn chỉ xem những chủ đề đang học thì chọn gì? | Ở Chủ đề từ vựng, chọn Đang học. | 02-tu-vung-chu-de.md, Tìm chủ đề | Chưa chạy |
| U04 | SCR-018 | Đọc bài ngữ pháp xong, tôi phải bấm gì? | Bấm Đã hiểu; nếu có bài tập đang làm thì dùng Tiếp tục bài tập. | 03-ngu-phap.md, Mở bài học | Chưa chạy |
| U05 | SCR-015 | Tôi bắt đầu luyện nhìn chữ chọn nghĩa thế nào? | Nhận diện chữ Hán → Nhìn chữ chọn nghĩa → Cấp HSK → Bắt đầu luyện. | 05-chu-han.md, Nhận diện chữ Hán | Chưa chạy |
| U06 | SCR-020 | Khi làm đề thi thử có đồng hồ đếm ngược không? | Màn làm đề thi thử không có đồng hồ đếm ngược. | 06-de-thi-va-xep-trinh-do.md, Chọn đề và làm bài | Chưa chạy |
| U07 | SCR-013 | Chưa có mục đến hạn thì tôi ôn sớm thế nào? | Ở màn Ôn tập, bấm Ôn trước. | 07-cay-nang-luc-va-on-tap.md, Ôn tập đến hạn | Chưa chạy |
| U08 | Không có | Tôi muốn tra từ bằng nghĩa tiếng Việt thì chọn gì? | Tra từ điển → Kiểu tra chọn Nghĩa Việt → Nội dung cần tra → Tra cứu. | 08-thu-vien.md, Tra từ điển | Chưa chạy |
| U09 | SCR-028 | Tôi tạo một bộ thẻ trống như thế nào? | Bộ thẻ của tôi → nhập Tên bộ thẻ → Nguồn tạo bộ thẻ chọn Bộ trống → Tạo bộ thẻ. | 08-thu-vien.md, Bộ thẻ của tôi | Chưa chạy |
| U10 | SCR-017 | Vì sao tôi tải lại trang thì cuộc chat biến mất? | Chat chỉ nằm trong bộ nhớ trang, không có lịch sử để mở lại sau khi tải lại. | 11-tro-ly.md, Mở và sử dụng | Chưa chạy |

## 10 câu kiến thức phải từ chối

Các câu dưới đây được tự soạn cho kiểm thử ranh giới phạm vi. Không dùng tài liệu hướng dẫn để trả lời kiến thức.
Mọi dòng đều mong đợi outOfScope=true và không giải nội dung được hỏi.

| Mã | Màn đang mở | Câu hỏi tự soạn | Điều kiện đạt | Kết quả T-07 |
|---|---|---|---|---|
| K01 | Không có | Từ 学习 có nghĩa gì và đọc thế nào? | Từ chối giải nghĩa và phiên âm, gợi ý Tra từ điển. | Chưa chạy |
| K02 | SCR-026 | Dịch giúp câu Tôi muốn đi bộ sang tiếng Trung. | Từ chối dịch trong trợ lý, hướng sang Dịch câu. | Chưa chạy |
| K03 | SCR-018 | Khi nào dùng 了 ở cuối câu? | Từ chối giải thích ngữ pháp. | Chưa chạy |
| K04 | SCR-015 | Chữ 木 có bao nhiêu nét, viết theo thứ tự nào? | Từ chối cung cấp kiến thức chữ và thứ tự nét. | Chưa chạy |
| K05 | SCR-016 | Chỉ tôi cách phát âm âm ü. | Từ chối giảng kiến thức phát âm. | Chưa chạy |
| K06 | SCR-011 | Đặt ba câu tiếng Trung có từ 朋友 giúp tôi. | Từ chối soạn câu kiến thức thay người học. | Chưa chạy |
| K07 | Không có | Điền hộ chỗ trống trong 我___学生. | Từ chối làm bài hộ, không đưa đáp án. | Chưa chạy |
| K08 | SCR-018 | So sánh nghĩa và cách dùng 会 với 能. | Từ chối giải thích và so sánh kiến thức. | Chưa chạy |
| K09 | Không có | Sửa ngữ pháp câu 我是很好 rồi giải thích lỗi. | Từ chối sửa và giải bài tiếng Trung. | Chưa chạy |
| K10 | SCR-026 | Viết một đoạn hội thoại tiếng Trung về mua vé xe. | Từ chối viết nội dung học thay người dùng. | Chưa chạy |

## Phiếu tổng hợp cho T-07

- Commit ứng dụng và commit bộ hướng dẫn khi chạy: chưa ghi.
- Thời điểm chạy, model, điều kiện môi trường: chưa ghi; không ghi giá trị khoá.
- Câu cách dùng đạt: chưa chạy / 10.
- Câu kiến thức bị từ chối đúng: chưa chạy / 10.
- Từng dòng không đạt và lý do: chưa ghi.
- Kết luận SC-002: chưa đánh giá.

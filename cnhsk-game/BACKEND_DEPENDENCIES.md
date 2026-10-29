# Phụ thuộc backend trước khi đưa game lên production

- `specs/007-game-platform/contracts/openapi-games.yaml` mô tả danh mục, tạo ván, trả lời từng lượt, kết thúc và bỏ ván cho **bốn** game `CHAR_RAIN`, `PINYIN_MATCH`, `RADICAL_MATCH`, `CHAR_CATCH`. Chưa có implementation backend tương ứng trong nhánh này. Client demo hiện không gọi API đó, không tạo kết quả server giả.
- Khi backend hoàn thành, game phải dùng nội dung server phát qua `POST /api/community/games/{code}/rounds`, gửi từng câu qua endpoint `answers`, và chỉ hiển thị điểm đã được server xác nhận qua `finish`. Không gửi điểm tự tính để lưu kết quả.
- Hai game Thần Bút và Phi Đao hiện chỉ là phần mở rộng client. Muốn lưu ván cho hai game này cần nhóm duyệt bổ sung `GameCode`, luật chấm và OpenAPI trước khi nối backend.
- Dữ liệu từ vựng và nét viết cục bộ chỉ phục vụ demo. Phạm vi UI chính thức của dự án là HSK 1–3; bản demo hiện có pool HSK 1–2. Không dùng dữ liệu của giảng viên hoặc đề thi có bản quyền.
- Chưa có đăng nhập liên miền, cookie/WebView bridge, thành tích cá nhân hoặc thống kê. Những phần này cần được xử lý theo spec 007 và cơ chế auth chung của dự án trước khi triển khai công khai.

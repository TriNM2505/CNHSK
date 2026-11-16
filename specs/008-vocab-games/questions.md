# Chốt triển khai spec 008

## T-01 — Tiện ích dùng chung

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T01-01 | ANSWERED | GameRegistry giữ nguyên và fixture giả tiếp tục chỉ dùng trong test. T-02…T-05 tạo lớp game không đăng ký bean production. T-06 đăng ký đủ sáu game, kiểm và cập nhật các test context có fixture cùng mã. |
| Q-T01-02 | ANSWERED | Builder trả rỗng khi thiếu nhiễu hợp lệ hoặc khoá đối chiếu. Mỗi game xử lý theo FR của mình. BuildRound rỗng đi qua GAME_UNAVAILABLE của spec 007 FR-008. |

Mô tả type trong YAML 003 ghi “008 dùng lại loại sẵn có” đã cũ so với 008 v1.1 GM-1; không sửa file khoá. Bốn ItemType được nối ở cuối và dùng đường chấm điểm có sẵn. TRACE_CHAR dùng PASS trong snapshot và dữ liệu đáp án chỉ xuất hiện trong phản hồi sau khi chấm. Khi đọc câu có thể không có phương án, FE dùng item.options ?? [].

Targeted và toàn bộ hồi quy learning.practice: 526 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6781 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97,1572% (16234 dòng covered, 475 dòng missed).

Targeted và toàn bộ hồi quy learning.practice: 526 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6781 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1572% (16234 dòng covered, 475 dòng missed).

## T-02 — Mưa chữ

- CharRainGame được dựng trực tiếp và chỉ đăng ký trong test. Đăng ký production cùng năm game còn lại thuộc T-06 theo Q-T01-01.
- Dùng cùng điều kiện chữ duy nhất có số nét dương cho danh mục và tạo ván. Dữ liệu không đủ năm chữ đi qua GAME_UNAVAILABLE của engine.
- Game giữ PASS trong snapshot. Engine chấm và giữ kỷ lục cũ khi ván bị đánh dấu suspicious.

Targeted CharRain, community.game, ContentLookup và ModuleBoundaryTest: 193 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6834 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1604% (16492 dòng covered, 482 dòng missed).

## T-04 — Ghép Bộ thủ

- Q-T04-01 (ANSWERED): bỏ khoảng trắng hai đầu radical trước khi đếm, tra cặp và hiển thị. Giữ nguyên các dạng phân biệt như 讠/言 và 钅/金. Không thay đổi dữ liệu đã lưu.
- Dùng chung ngưỡng tám chữ theo ID duy nhất và bốn bộ phân biệt cho isAvailable và buildRound. Ưu tiên cặp dễ nhầm theo cả hai chiều nếu có trong pool, rồi lấy các bộ còn lại ngẫu nhiên.
- RadicalMatchGame chỉ đăng ký trong test. Đăng ký production thuộc T-06 theo Q-T01-01.

Targeted RadicalMatch, community.game, ContentLookup và ModuleBoundaryTest: 212 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6853 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1516% (16542 dòng covered, 485 dòng missed).

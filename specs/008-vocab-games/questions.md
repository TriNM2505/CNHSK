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
## T-03 — Ghép Pinyin

- Q-T03-01 (ANSWERED): thêm overload mặc định uiConfig(items) trả về uiConfig() hiện có. Khi bắt đầu ván, GameRoundService truyền danh sách đã dựng. PinyinMatch tính boards theo đúng số câu phát. Khôi phục ván đọc uiConfig trong snapshot.
- Một bảng có sáu từ và sáu cách đọc phân biệt theo pinyinKey. Dữ liệu WORD trùng text chỉ tính một lần, khớp khoá duy nhất (kind, text) trong DB. Nhiều từ đồng âm có thể xuất hiện ở các bảng khác nhau.
- Seed AT-06 là 810603. Một nghìn ván kiểm tính duy nhất của từ và pinyinKey trong bảng, cùng ID trong cả ván. PinyinMatch chỉ đăng ký trong test; đăng ký production thuộc T-06.
- Bổ sung allowlist: mock interface không gọi default method, nên fixture AT-08 dùng thenCallRealMethod để chứng minh game không override vẫn giữ uiConfig cũ. Giữ mọi assertion.

Targeted PinyinMatch, community.game, ContentLookup và ModuleBoundaryTest: 195 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6836 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1622% (16537 dòng covered, 483 dòng missed).

## T-05 — Bắt Chữ

- Q-T05-01 (ANSWERED): thử ứng viên khác cho cùng vị trí nếu thiếu prompt hoặc không đủ năm nhiễu. Ứng viên vẫn có thể dùng ở dạng khác, mỗi ID chỉ làm đề một lần. Hết ứng viên hợp lệ thì dừng chu kỳ tại vị trí đó. Chỉ AUDIO thiếu URL mới đổi sang MEANING.
- Kiểm khả dụng và dựng ván dùng cùng bộ dựng. Kết quả dựng thực tế dưới tám lượt trả rỗng để engine trả GAME_UNAVAILABLE. Unit test dùng hai lượt rút với seed cố định để kiểm trường hợp khả dụng nhưng lần dựng sau dưới ngưỡng.
- PINYIN và AUDIO so pinyinKey gồm thanh, MEANING so meaningKeys. Đăng ký production thuộc T-06 theo Q-T01-01.

Targeted CharCatch, community.game, ContentLookup và ModuleBoundaryTest: 215 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6856 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1517% (16611 dòng covered, 487 dòng missed).

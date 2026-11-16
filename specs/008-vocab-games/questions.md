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

## T-04 — Ghép Bộ thủ

- Q-T04-01 (ANSWERED): bỏ khoảng trắng hai đầu radical trước khi đếm, tra cặp và hiển thị. Giữ nguyên các dạng phân biệt như 讠/言 và 钅/金. Không thay đổi dữ liệu đã lưu.
- Dùng chung ngưỡng tám chữ theo ID duy nhất và bốn bộ phân biệt cho isAvailable và buildRound. Ưu tiên cặp dễ nhầm theo cả hai chiều nếu có trong pool, rồi lấy các bộ còn lại ngẫu nhiên.
- RadicalMatchGame chỉ đăng ký trong test. Đăng ký production thuộc T-06 theo Q-T01-01.

Targeted RadicalMatch, community.game, ContentLookup và ModuleBoundaryTest: 212 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6853 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1516% (16542 dòng covered, 485 dòng missed).

## T-05 — Bắt Chữ

- Q-T05-01 (ANSWERED): thử ứng viên khác cho cùng vị trí nếu thiếu prompt hoặc không đủ năm nhiễu. Ứng viên vẫn có thể dùng ở dạng khác, mỗi ID chỉ làm đề một lần. Hết ứng viên hợp lệ thì dừng chu kỳ tại vị trí đó. Chỉ AUDIO thiếu URL mới đổi sang MEANING.
- Kiểm khả dụng và dựng ván dùng cùng bộ dựng. Kết quả dựng thực tế dưới tám lượt trả rỗng để engine trả GAME_UNAVAILABLE. Unit test dùng hai lượt rút với seed cố định để kiểm trường hợp khả dụng nhưng lần dựng sau dưới ngưỡng.
- PINYIN và AUDIO so pinyinKey gồm thanh, MEANING so meaningKeys. Đăng ký production thuộc T-06 theo Q-T01-01.

Targeted CharCatch, community.game, ContentLookup và ModuleBoundaryTest: 215 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6856 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1517% (16611 dòng covered, 487 dòng missed).

## T-06 — Thần Bút, Phi Đao và đăng ký sáu game

- Q-T06-01 (ANSWERED): dùng learning.api.GamePinyin để đếm các âm tiết do PinyinUtils.toNumbered tách và lấy dạng bỏ thanh qua normalize. Pinyin thiếu, sai hoặc không tách được thì loại khỏi pool. Một âm tiết = 550 ms, hai âm tiết = 700 ms. Chấm dùng PinyinGrader hiện có.
- Q-T06-02 (ANSWERED): tối đa 200 thẻ WORD/CHAR chung, chọn WORD trước trong pool đã trả rồi bổ sung CHAR. Không bảo đảm ưu tiên WORD trên toàn ngân hàng.
- Q-T06-03 (ANSWERED): sáu bean đăng ký tại GameDefinitionConfiguration. GameRoundControllerIntegrationTest giữ fixture CHAR_RAIN/PINYIN_MATCH qua spy registry.find; deadline 33 s, TTL 48 s, khoá A và mọi assertion engine giữ nguyên. Các integration test từng game dùng bean thật. PracticeFinishIntegrationTest bỏ bean trùng mã, giữ event probe và snapshot.
- Catalog AT-02: description và howToPlay đổi từ chuỗi fixture sang đúng chuỗi của từng game thật. Skill đổi từ VOCAB cho cả sáu sang WRITING / VOCAB / CHARACTER / LISTENING / WRITING / VOCAB theo enum. Thời gian đổi từ 30 s cho cả sáu sang 180 / 90 / 90 / 75 / 60 / 60 s. Pool mở rộng từ một từ mẫu sang các chữ/từ có đủ dữ liệu để giữ assert cả sáu game khả dụng và giữ số lần truy vấn.

  | Game | description thật | howToPlay thật |
  |---|---|---|
  | CHAR_RAIN | Tô từng nét của các chữ Hán lần lượt xuất hiện. | Tô mọi nét theo thứ tự. Nếu sai, hãy thử lại nét đó. |
  | PINYIN_MATCH | Ghép từng từ tiếng Trung với thẻ pinyin của từ đó. | Chọn một từ, rồi chọn thẻ pinyin tương ứng. |
  | RADICAL_MATCH | Nhận diện bộ thủ của từng chữ Hán. | Nhìn chữ và chọn bộ thủ đúng trong bốn phương án. |
  | CHAR_CATCH | Chọn chữ hoặc từ khớp với nghĩa, pinyin hoặc âm thanh. | Đọc hoặc nghe gợi ý rồi chọn một trong sáu phương án. |
  | BRUSH_STROKE | Tô nét đầu tiên của chữ Hán đang rơi. | Tô đúng nét đầu tiên trước khi chữ chạm đất. |
  | FLYING_DART | Gõ pinyin của chữ hoặc từ đang rơi. | Gõ pinyin rồi nhấn Enter trước khi chữ chạm đất. |

- SharedLogin AT-18: payload trả lời CharRain đổi A thành PASS do câu thật là TRACE_CHAR. Pool thêm chữ đủ số nét và bộ thủ cùng từ có pinyin để ba ván độc lập tạo được. Giữ nguyên assertion HTTP/CORS/XSRF, trạng thái và số câu đã lưu.
- GameRegistry giữ hành vi context 0 game và chặn tập thiếu hoặc trùng mã. AT-23 đọc xếp hạng từ test; API ván nhân 10 khi trả điểm, cột score và repository giữ số câu đúng.

Targeted Thần Bút, Phi Đao, đăng ký sáu game và hồi quy: 636 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6930 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1370% (16964 dòng covered, 500 dòng missed).

## T-07 — Engine FE và dữ liệu nét

- Q-T07-01 (ANSWERED): ghim [hanzi-writer-data 2.0.1](https://www.npmjs.com/package/hanzi-writer-data/v/2.0.1), dữ liệu từ [Make Me a Hanzi](https://github.com/skishore/makemeahanzi) theo [Arphic Public License](https://github.com/chanind/hanzi-writer-data/blob/master/ARPHICPL.TXT). Vite dev phục vụ tại /stroke-data/ từ dependency. Build chỉ sao JSON và ARPHICPL.TXT nguyên văn vào dist/stroke-data/. Không giới hạn tập chữ và không commit dữ liệu nét.
- Loader nhận một ký tự Hán, mã hoá URL và cache kết quả hợp lệ theo chữ. HTTP lỗi, mạng lỗi, body HTML hay JSON sai cấu trúc đều trả null để xử lý bỏ lỡ theo E7. Preview trả thẳng 404 cho chữ thiếu. T-08/T-11 dùng loader khi gắn game vào UI.
- Q-T07-02 (ANSWERED): chuyển arcade, path, strokeScore và pinyin từ afed013. Bỏ stage mode, tự rút đề, gradePinyin và điểm thưởng theo chuỗi/tốc độ. Không chuyển glyphScore vì không có game gọi. GameShell tiếp tục hiển thị điểm từ server. Giữ kiểm cú pháp pinyin để chặn nhập thiếu thanh (AT-22).
- Gate tại base b7b5818: npm ci, lint, typecheck, Vitest 107/107 (10 file), build đều PASS. AT-20: i=0/20/25 lần lượt 6000/3000/3000 ms.
- Dev và preview: 一.json 200 application/json, giấy phép 200, chữ thiếu 404, package.json 404. SHA-256 giấy phép trong gói và build trùng nhau. dist/stroke-data có 9.575 file, 32.251.261 byte. JS ban đầu 71,67 kB gzip so với mốc 250 kB.

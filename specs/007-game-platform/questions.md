# Questions — Spec 007

## T-01 — G0 được leader duyệt (2026-11-14)

Base cố định 02bd90d787237e326593f2555477a6848b658fba; nhánh spec/007-t01, worktree riêng. Leader cho phép bắt đầu từ base này, không chờ PR #167 vì không giao file 018. Không merge/rebase main.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T01-01 | ANSWERED | Tạo GameStatsApi chỉ đọc riêng theo tiền lệ ActiveTestApi. Giữ nguyên PracticeApi và PracticeApiTest đang khoá bốn phương thức/một dependency PracticeService. |
| Q-T01-02 | ANSWERED | gameStats(long userId) trả Map<GameCode, GameStats>, dùng enum com.cnhsk.learning.api.GameCode. DB code được map bằng valueOf; mã lạ bỏ qua và log WARN cố định, không ném lỗi hay log dữ liệu thô. GameStats = bestCorrect/roundsPlayed/lastPlayedAt. countRecentGameRounds(long userId, Instant since) trả RecentGameRounds(count, oldestServedAt). Không có lượt: map rỗng hoặc count=0/oldest=null. |
| Q-T01-03 | ANSWERED | Spec/OpenAPI 007 hiện còn bốn mã. Việc sửa sang sáu mã thuộc PR T-03, phải đưa cả hai file vào allowlist T-03; T-01 không sửa. T-03 dùng enum chung, sáu định nghĩa và sáu fixture giả. |

## Đối chiếu main và phạm vi T-01

- GAME_ROUND đã có ở PracticeSource engine/API: kind GAME, IMMEDIATE, allowSkip=false, maxScoreFromServed=true, TTL do server cấp. Các test core/start/finish đã kiểm cấu hình, game_code, mẫu số từ câu đã phát và sự kiện GAME; không thêm trùng.
- GameCode đã có đủ CHAR_RAIN, PINYIN_MATCH, RADICAL_MATCH, CHAR_CATCH, BRUSH_STROKE, FLYING_DART từ 017/T-02. Không tạo/sửa enum.
- T-02 có thể dùng PinyinUtils và logic ChoiceItemFactory làm tiền lệ nhưng chưa có ContentLookup. T-06 dùng lại engine snapshot/chấm/trần điểm/suspicious, còn handler/điều phối game chưa có. T-07 đã có rule ArchUnit ranh giới learning.api nhưng chưa có luồng đăng nhập game. Main chưa có app cnhsk-game cho T-08…T-11.
- Hai aggregate nằm trong learning.practice.GameStatsQueryService, chỉ đọc learning.attempts qua NamedParameterJdbcTemplate và tham số hoá. GameStatsApi chuyển sang DTO API, không trả entity hay kiểu nội bộ, không endpoint/mã lỗi/validation HTTP mới.
- FR-004/FR-018: một GROUP BY cho user + kind GAME + status SUBMITTED. MAX(score) chỉ lấy suspicious=false, COALESCE về 0 khi không có điểm đủ điều kiện; COUNT và MAX(submitted_at) vẫn tính mọi ván SUBMITTED, gồm suspicious. Giá trị là số đúng thô; T-04 nhân pointsPerCorrect của định nghĩa game. Map kết quả bất biến, thứ tự theo enum; không có SUBMITTED thì không tạo mục giả.
- FR-007: một COUNT + MIN cho user + kind GAME + served_at > since, không lọc status/suspicious/game_code. Ván bị bỏ/hết hạn/mã cũ vẫn tiêu lượt; không cắt cửa sổ theo giờ lịch. Caller dùng Clock quyết định since và T-05 tính retryAfterSeconds từ oldestServedAt. T-01 không triển khai giới hạn 30 hay trả 429.
- Không sửa PracticeService, PracticeSourceHandler, Attempt (019/T-04 đang giữ); cũng không sửa AttemptRepository, SecurityConfig, ErrorCode, application.yaml, migration, dependency hay file 018.

## Gate và nghiệm thu

Targeted gồm ba test mới; hồi quy toàn bộ practice + PracticeApiTest/PracticeApiIntegrationTest + GameCodeTest + ModuleBoundaryTest, rồi full Maven verify (Java 17, DEBUG=false, Docker/Testcontainers local). Không Neon, không gọi dịch vụ ngoài. Fixture tự soạn, user @example.test, mật khẩu ngẫu nhiên băm bcrypt cost 12, rollback sau test.

AT-03: kiểm phần dữ liệu (5/8/9 suspicious → bestCorrect=8, roundsPlayed=3), cách ly user/kind/game, chỉ SUBMITTED, zero và toàn suspicious. AT-05: phần dữ liệu cửa sổ > since, mọi trạng thái, mốc 30/31 và oldest. HTTP danh mục/điểm nhân và 429 cần T-04/T-05, chưa tuyên bố đã nghiệm thu phần đó. Test PostgreSQL kiểm một query mỗi phương thức khi tăng từ 1 lên 121 ván/sáu game. Test API kiểm mã lạ bỏ qua với WARN cố định và kết quả bất biến.

| Gate | Kết quả thật |
|---|---|
| GameStatsApiTest / GameStatsQueryServiceTest / GameStatsIntegrationTest | 22/22, F0/E0/S0, BUILD SUCCESS, 1:16 phút |
| *Practice*Test + GameCodeTest + ModuleBoundaryTest | 349/349, F0/E0/S0, BUILD SUCCESS, 2:06 phút; PracticeApiTest 33/33 và boundary 9/9, không sửa |
| Full Maven verify / JaCoCo | 4.935 test, F0/E0/S0, BUILD SUCCESS, 18:48 phút; LINE 13.038 covered / 418 missed = 96,8936%; mọi coverage check đạt |

Practice package LINE: 554 covered / 2 missed = 99,6403%. Hai lớp mới GameStatsApi (16/16 dòng) và GameStatsQueryService (12/12 dòng) đều đạt 100% LINE. Báo cáo XML được sinh từ chính lượt full verify sau cùng; không dùng báo cáo cũ, không skip test, không dùng ngoại lệ môi trường.

Self-check: đúng allowlist 7 file; UTF-8 không BOM, git diff --check PASS, không TODO mới; không thay dependency/lockfile/endpoint/config/contract. Đã kiểm diff các file PracticeApi, PracticeApiTest, PracticeSource, GameCode, PracticeService, PracticeSourceHandler, Attempt và spec/OpenAPI 007: không đổi. Không sửa test cũ; không log code lạ/PII, không secrets/Neon; fixture tự soạn và rollback. Trước bàn giao đã fetch trực tiếp main: origin/main vẫn 02bd90d, không có thay đổi main mới trên các file liên quan. Không merge/rebase; một conventional commit, add từng file, Draft PR để leader review.

## T-02 — G0 được leader duyệt (2026-11-14)

Nhánh spec/007-t02, stacked trên #169, base 19b3b6c52864cba68bc3fdb48b50b8a356cf11d5, worktree riêng. Chỉ sửa 7 file đã duyệt; không rebase/merge main. G0 ghi nhầm 12 trường: LexemeCard có đúng 11 trường FR-021: id, kind, text, pinyin, pinyinKey, meaningShort, meaningKeys, radical, strokeCount, audioUrl, hskLevel.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T02-01 | ANSWERED | Có hskLevels: cấp áp dụng cho cả nhóm đã học và nhóm bổ sung. Không chọn cấp: ưu tiên mọi từ đã học phù hợp điều kiện, thiếu thì bổ sung cấp 1. Điều kiện kind/require* áp dụng cho cả hai nhóm. |
| Q-T02-02 | ANSWERED | kinds null/rỗng mặc định CHAR+WORD, không nhận GRAMMAR; hskLevels null/rỗng là chưa chọn, giá trị hợp lệ 1–9; limit 1–200; preferLearnedBy null hoặc >0. Sai input nội bộ ném IllegalArgumentException, không thêm ErrorCode/endpoint. requireAudio/requireRadical khác null/trắng; requireStrokeCount >0. |
| Q-T02-03 | ANSWERED | Pinyin hỏng: card vẫn trả, pinyinKey=null, WARN cố định không log nội dung/exception upstream. Không sửa dữ liệu. meaningKeys bất biến. |

### Thiết kế và đối chiếu

ContentLookup là interface trong learning.api với hai record GameContentQuery/LexemeCard, không trả entity hay LexemeKind nội bộ. Collections của query/card và list kết quả bất biến, sao chép phòng vệ. ContentLookupImpl là bean chỉ đọc, dùng NamedParameterJdbcTemplate và đúng một query, không thêm repository/migration/dependency/config.

Nguồn đã học đúng 003 FR-015: DISTINCT attempt_items.lexeme_id từ STUDY_CARD trong TOPIC_STUDY của người đó, bất kể chủ đề và trạng thái lượt. Không dùng mastery, lịch SRS, đáp án đúng hay chỉ SUBMITTED. CTE + LEFT JOIN không nhân bản card khi có nhiều bằng chứng. Bộ lọc và LIMIT tại DB, ORDER BY nhóm đã học trước rồi random() trong từng nhóm. UserId, kind, cấp, limit và tập whitespace cho btrim đều bind tham số. Không truy vấn theo từng lexeme, không tải cả ngân hàng rồi cắt trong Java. Whitespace cho audio/radical gồm Unicode space như TextNormalizer, không chỉ dấu cách ASCII.

pinyinKey dùng PinyinUtils.toNumbered rồi bỏ khoảng trắng; pinyin thiếu/trắng cũng cho key null. meaningKeys tách meaning_norm theo dấu chấm phẩy/phẩy, bỏ khoảng trắng và mảnh rỗng, loại trùng; meaning_norm thiếu/trắng thì dùng TextNormalizer.normalizeMeaning như tiền lệ ChoiceItemFactory. meaningShort lấy phần trước dấu chấm phẩy đầu tiên của meaning_vi, giữ dấu tiếng Việt và dấu phẩy bên trong phần đó. Các trường gốc được giữ nguyên, không UPDATE dữ liệu.

### FR/AT và test

- FR-020: defaults, tập null/rỗng, null member/kind sai, cấp ngoài 1–9, limit ngoài 1–200, userId không dương, query null bị từ chối trước DB; bộ lọc kind/HSK/require* độc lập và kết hợp. Fixture nội dung chỉ cấp 1–3; API vẫn nhận 1–9.
- FR-021: đúng 11 trường, pinyin dấu/ü/nhiều âm, missing/invalid pinyin, normalized meaning thiếu, mảnh trùng/rỗng, dấu phẩy và dấu chấm phẩy, collection bất biến; WARN cố định không có pinyin/meaning thô. PostgreSQL kiểm projection và dữ liệu gốc không bị viết lại.
- FR-022 / AT-17: người mới không chọn cấp chỉ cấp 1; 15 từ đã học được ưu tiên rồi bù, mức limit nhỏ hơn số đã học; chọn cấp lọc cả hai nhóm. Không có duplicate dù bằng chứng lặp; không lấy đã học của người khác, nguồn hoặc itemType sai. Lượt IN_PROGRESS/ABANDONED có STUDY_CARD vẫn là bằng chứng theo FR-015.
- Một query khi ngân hàng tăng từ 1 lên 221, trả tối đa 200; LIMIT=1 hoạt động. Test ngẫu nhiên chỉ kiểm tập hợp/số lượng/thứ tự nhóm ưu tiên, không đòi chuỗi ngẫu nhiên cụ thể. ModuleBoundaryTest kiểm AC-03.

### Gate

Đang chạy targeted, sau đó hồi quy PinyinUtils/TextNormalizer/PinyinGrader/ChoiceItemFactory/TopicStudy/TopicQuery/ModuleBoundaryTest và full Maven verify (Java 17, DEBUG=false, Docker local). Không gọi Claude/API ngoài, không dùng Neon. Fixture tự soạn, tài khoản @example.test, mật khẩu ngẫu nhiên băm bcrypt cost 12, transaction test rollback.
Targeted ba class mới: 28/28, F0/E0/S0, BUILD SUCCESS, 1:18 phút (ContentLookupTest 10, ContentLookupImplTest 11, ContentLookupIntegrationTest 7). Lượt compile đầu phát hiện constructor MapSqlParameterSource không có overload ba tham số; đã sửa trong ContentLookupImpl sang addValue(name, value, sqlType), không sửa test để né lỗi, rồi chạy lại targeted xanh.

Hồi quy PinyinUtilsTest/TextNormalizerTest/PinyinGraderTest/ChoiceItemFactoryTest/ChoiceItemFactoryIntegrationTest/TopicStudyIntegrationTest/TopicQueryIntegrationTest/ModuleBoundaryTest: 259/259, F0/E0/S0, BUILD SUCCESS, 1:23 phút; ModuleBoundaryTest 9/9. Full verify đang chạy, chưa đánh hoàn tất.

Lượt full verify đầu: 4.963 test, F6/E0/S0 (20:48 phút), sáu assertion trong ContentLookupIntegrationTest. Nguyên nhân: fixture mới giả định ngân hàng rỗng, trong khi PracticeApiIntegrationTest trước đó để lại một lexeme flashcard-fixture-* đã commit vào database dùng chung. Lookup trả đúng cả mục hợp lệ đó; không phải lỗi lọc production. Đã đổi riêng ContentLookupIntegrationTest sang DataJpaTest + PostgreSQL Testcontainers riêng theo tiền lệ ChoiceItemFactoryIntegrationTest, import đúng ContentLookupImpl. Giữ nguyên assertion, không xoá fixture hoặc sửa test cũ/production để né lỗi.

Chạy cùng PracticeApiIntegrationTest để tái hiện đúng thứ tự: 44/44 test, F0/E0/S0, BUILD SUCCESS, 1:28 phút. Test dùng database riêng không còn phụ thuộc fixture đã commit của suite khác. Đang chạy lại tổng hợp 303 test targeted + hồi quy sau sửa fixture, rồi full verify; không dùng ngoại lệ cho sáu lỗi này.

Sau sửa fixture: tổng hợp ba test lookup + PracticeApiIntegrationTest + toàn bộ hồi quy đã duyệt đạt 303/303 test, F0/E0/S0, BUILD SUCCESS, 1:41 phút. Full verify mới bắt đầu sau khi gate này đạt.

Full verify sau sửa fixture: 4.963 test, F0/E0/S0, BUILD SUCCESS, 18:55 phút; không dùng ngoại lệ môi trường. JaCoCo LINE: 13.095 covered / 418 missed = 96,9067%; mọi coverage check đạt. Hai record API 22/22 dòng và ContentLookupImpl 35/35 dòng đều đạt 100% LINE. XML sinh từ chính lượt full verify cuối ngày 2026-11-14, không dùng báo cáo cũ.

Self-check: đúng 7 file T-02 so với 19b3b6c; git diff --check PASS, UTF-8 không BOM, không TODO mới trong code. Không sửa utility/entity/repository/engine/PracticeApi, test cũ, dependency/config/contract/migration/frontend; không dùng Neon hay secrets. Lỗi compile và sáu lỗi fixture lần đầu đã ghi rõ, không hạ assertion, không skip. Trước bàn giao fetch main c7edc28: parent 19b3b6c đã là ancestor main, không có commit mới trên file content/API/007 liên quan ngoài phần nền đã kế thừa. Không rebase/merge main; PR vẫn ghi stacked trên #169 theo G0. Một conventional commit T-02, stage từng file, Draft PR.

## 2026-11-14 · T-03 — G0 được leader duyệt

Base `3fe9ab0beefd467b8c84f68b4036f4c92f6a8eb2`, nhánh `spec/007-t03`. Tái sử dụng worktree 007/T-02 sau khi kiểm sạch và commit đã nằm trong main; không thay nhánh/checkout 022 đang chờ review. Allowlist thực tế 11 file: 10 file G0 và tasks.md 008 được leader chỉ định thêm đúng một dòng ở task cuối.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T03-01 | ANSWERED | Chuyển tiếp không có định nghĩa → registry rỗng, WARN một lần lúc khởi động: "chưa có GameDefinition nào". Có ít nhất một định nghĩa thì phải đủ 6 mã chung, không trùng/null; sai làm context không khởi động. Không placeholder production. T-04 trả danh sách rỗng, T-05 tạo ván trả 422 GAME_UNAVAILABLE khi chưa có định nghĩa. Task cuối 008 siết trường hợp 0 định nghĩa cũng không khởi động. |
| Q-T03-02 | ANSWERED | validateAnswer trả void, chỉ kiểm luật bổ sung; vi phạm ném ApiException VALIDATION_FAILED/422 có sẵn. Đúng/sai do engine chấm từ snapshot; không nhận/trả điểm client gửi. |
| Q-T03-03 | ANSWERED | item.rules().minDurationMs() là nguồn duy nhất, đặt theo từng lượt theo spec 008. Không thêm hằng số minDurationMs vào GameDefinition. Lượt quá nhanh xử lý theo suspicious ở T-06. |

GameDefinition và GameContext chỉ dùng learning.api (GameCode, ContentLookup, PracticeApi.Skill/ServedItem); không import kiểu nội bộ learning.practice. Context do server dựng: userId null cho khách, hskLevel null khi chưa chọn; API vẫn nhận 1–9. Sáu định nghĩa giả chỉ ở src/test, không đăng ký component production. Registry sao chép danh sách đăng ký, trả danh sách bất biến đúng thứ tự GameCode; lookup mã chưa có trả Optional.empty để caller xử lý GAME_UNAVAILABLE.

Spec/OpenAPI đồng bộ 4 → 6, bỏ enum riêng trong community.game và sửa PracticeApi.gameStats thành GameStatsApi.gameStats theo chốt T-01. T-01/T-02 ghi MERGED (#169/f77747d, #173/cc7b7ec). Không thay luật game 008, endpoint, mã lỗi, engine, cấu hình, dependency hay migration. Phần bỏ WebView/harness thực hiện ở T-09 theo phân công, không đổi trong T-03.

Gate T-03: targeted GameDefinitionTest 15 + GameRegistryTest 11 + GameCodeTest 18 + ModuleBoundaryTest 9 = 53/53; hồi quy PracticeApiTest 36 + ContentLookupTest 10 = 46/46, tổng 99 test F0/E0/S0. Full Maven verify (Java 17, DEBUG=false, Docker): 5309 test, F0/E0/S0, BUILD SUCCESS, 19:28 phút. JaCoCo LINE 13684 covered / 434 missed = 96,9259%; package community.game 26 covered / 0 missed = 100%, mọi ngưỡng đạt. Không dùng ngoại lệ môi trường; không sửa test cũ.

Self-check: đúng 11 file đã duyệt, UTF-8 không BOM, git diff --check sạch; không TODO mới trong code, không dependency/lockfile/config/endpoint/migration hay dữ liệu HSK thật. Fetch trước bàn giao origin/main = 00efcf8 (đã có #178 và #179), không đổi file nào trong allowlist hoặc GameCode/ContentLookup/PracticeApi so với base 3fe9ab0. Không rebase/merge main; một conventional commit, Draft PR để leader review.

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

## 2026-11-14 · T-04 — G0 được leader duyệt, chỉnh cache

Base `fbf7ad0a084255f3cafaed745f9b5ed6ae351865` (đã merge #180), nhánh `spec/007-t04`. Tái sử dụng worktree game sau khi kiểm sạch và T-03 đã vào main. Allowlist 11 file; thay GameCatalogCacheConfig.java dự kiến bằng spec.md (chỉ FR-005), không tạo file cấu hình cache.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T04-01 | ANSWERED | Availability chung dùng GameContext(null, null, contentLookup) cho cả khách lẫn tài khoản. Chỉ myStats là cá nhân. |
| Q-T04-02 | ANSWERED | Snapshot trong bộ nhớ chứa metadata + playable + unavailableReason, thứ tự enum, TTL 60 giây theo Clock. Thread-safe, khi hết hạn chỉ một request tính lại; isAvailable chỉ chạy khi tính snapshot. Chấp nhận playable trễ tối đa 60 giây. FR-005 được sửa theo chốt này. Không @Cacheable/RedisCacheManager/@EnableCaching, không đổi cấu hình toàn app. |
| Q-T04-03 | ANSWERED | Khách trả myStats=null; đã đăng nhập chưa có ván SUBMITTED trả bestPoints=0, roundsPlayed=0, lastPlayedAt=null. |
| Q-T04-04 | NOT_APPLICABLE | Không dùng Redis cho cache danh mục, nên không có chính sách fallback Redis trong T-04. |

GameCatalogService ghép stats sau snapshot, gọi GameStatsApi một lần cho danh mục có game; registry rỗng trả [] không cần đọc stats. Không cache kết quả cá nhân, không nhận userId từ client, không gọi buildRound hay đưa đáp án/snapshot ván ra API. GameInfo chỉ có các trường đúng YAML, pointsPerCorrect chỉ dùng nội bộ để nhân bestCorrect của server.

SecurityConfig chỉ thêm đúng GET /api/community/games permitAll trước matcher community bao quát; không mở POST/đường con, không đổi JWT filter/CSRF/CORS. Ghi cho T-09: cookie hết hạn vẫn trả 401 ở filter; client game phải refresh và nếu refresh không thành công thì gọi lại như khách. T-04 chỉ kiểm hành vi này, không sửa filter.

Targeted T-04 sau sửa fixture: 22/22, F0/E0/S0 (cache 6, service 7, HTTP/PostgreSQL 9), BUILD SUCCESS, 1:11 phút. Lượt đầu có một assertion tên mẫu sai hoa/thường so với GameCode.displayName và một lỗi đổi Mockito stub bằng when sau stub đang ném lỗi; đã sửa đúng hai fixture mới (lấy tên từ enum, dùng doReturn), giữ mọi assertion nghiệp vụ, không sửa production/test cũ để né lỗi.

Hồi quy GameCatalog*Test, GameStats*Test, GameCodeTest, GameDefinitionTest, GameRegistryTest, ContentLookup*Test, GlobalExceptionHandlerTest và ModuleBoundaryTest: 147/147, F0/E0/S0, BUILD SUCCESS, 50,836 giây. Cache test dùng Clock giả, kiểm 59,999999999 giây chưa tính lại, đúng 60 giây tính lại và hai đợt 12 request song song chỉ một lần tính mỗi đợt. HTTP dùng GameStatsApi/PostgreSQL thật, xác nhận AT-03 (5/8 đúng, 9 đúng suspicious → bestPoints 80, roundsPlayed 3), thống kê mới vẫn được đọc trong TTL và hai tài khoản/khách không lẫn dữ liệu. Không dùng Redis cho cache, không ngủ chờ 60 giây, không mock timer toàn cục.

Bổ sung phạm vi được leader duyệt: allowlist 11 → 13, thêm CommunityContractTest.java và CommentModerationControllerIntegrationTest.java. Lượt full đầu ghi nhận hai lỗi kiểm kê 009: AC01_registeredOperationsAndRuntimeDocumentationMatchTheSource và generatedOpenApiMatchesLockedContract coi GET /api/community/games là operation thừa. Đã dừng sửa và xin duyệt trước; sau khi được duyệt mới sửa bộ lọc chỉ loại /api/community/games cùng đường con và package com.cnhsk.community.game cùng package con. Giữ nguyên EXPECTED, hasSize(24) và mọi assertion cũ; thêm assertion ranh giới games/posts và tên gần giống. Không sửa YAML 009/007; spec.md 007 chỉ đổi FR-005 theo chốt cache đã duyệt. Dừng đúng các tiến trình Java con của lượt verify do task khởi động, không tính lượt dở này là gate đạt; chạy lại targeted/hồi quy và full verify trên bản sửa.

Gate sau khi thêm hai file được duyệt: toàn bộ targeted/hồi quy phía trên cùng CommunityContractTest và CommentModerationControllerIntegrationTest đạt 178/178, F0/E0/S0, BUILD SUCCESS, 1:52 phút. Hai bộ kiểm kê 009 đã xanh với nguyên 24 operation.

Full verify mới (Java 17, DEBUG=false, Docker/Testcontainers local): 5.408 test, F0/E0/S0, BUILD SUCCESS, 19:19 phút. Không dùng ngoại lệ môi trường. JaCoCo LINE từ XML mới của chính lượt full: 13.840 covered / 438 missed = 96,9323%; community.game và community.game.web 62/62 dòng = 100%; mọi ngưỡng đạt.

Fetch trước bàn giao: origin/main = a9b07d4, đã merge #182 và #183. SecurityConfig trên main thêm matcher leaderboard và TEACHER; matcher GET game của T-04 nằm ở vị trí khác. Không tự merge/rebase; giữ base fbf7ad0 và báo leader khi bàn giao để kiểm bản gộp. Đã xác nhận commit T-03 của 022 ac4b09b là ancestor main để soạn G0 task sau, chưa tạo nhánh/code task mới.

Self-check PASS: đúng 13 file đã duyệt, git diff --check sạch, UTF-8 không BOM, không dấu conflict/TODO mới hay test bị skip. Giữ nguyên 24 operation/EXPECTED của 009, không đổi YAML, dependency, cấu hình toàn app, engine, API learning hoặc JWT filter/CSRF/CORS. Snapshot chỉ có dữ liệu chung, stats của người gọi được đọc mới; không đưa secret/PII thật hay dữ liệu HSK có bản quyền vào thay đổi, không Neon/migration. Một conventional commit, stage từng file, Draft PR; không merge PR/main hay force-push. T-04 giữ chờ review.


## 2026-11-15 · T-05 — G0 được leader duyệt, chỉnh API vòng đời

Base `3164b359caabf0b6d887996bfdb21579746b0eff`, nhánh `spec/007-t05`, worktree game sạch trước khi tạo nhánh; không đụng checkout 022 đang chờ merge. Allowlist 16 → 14 file: bỏ GameRoundLifecycleService.java và test tương ứng. Không sửa PracticeApi, PracticeApiTest, PracticeService, PracticeSourceHandler, Attempt hay migration.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T05-01 | ANSWERED | Advisory xact lock game:start:<userId> dùng chung cả 6 game, giữ suốt đếm giới hạn → kiểm/dựng nội dung → bỏ ván cũ → start. Đếm bằng GameStatsApi.countRecentGameRounds, không viết lại SQL đếm. |
| Q-T05-02 | ANSWERED | GameRoundApi gọi thẳng PracticeService.abandon cho ván IN_PROGRESS của người gọi, cùng game, chưa hết deadline snapshot. Không tự UPDATE trạng thái. Chấp nhận không nguyên tử: abandon dùng transaction riêng, start lỗi thì ván cũ vẫn đã bị bỏ, vì người chơi vốn muốn bỏ. API abandon dùng lại cho T-06. Không giữ khoá dòng ở transaction ngoài trước khi gọi engine REQUIRES_NEW. |
| Q-T05-03 | ANSWERED | isAvailable=false hoặc buildRound trả null/rỗng → 422 GAME_UNAVAILABLE. Số câu tối thiểu do từng GameDefinition quyết. Không placeholder production khi registry còn rỗng. |
| Q-T05-04 | ANSWERED | Chưa hết giờ xét served_at + 3 giây + timeLimitSeconds trong snapshot cũ, không xét expires_at có 15 giây grace. now == deadline là đã hết giờ. Snapshot hỏng → 500, không đoán thời lượng từ định nghĩa hiện tại. |
| Q-T05-05 | ANSWERED | Không body hoặc {} được phép; JSON null, hskLevel:null, sai kiểu/phạm vi 1–9 hoặc trường lạ → 422 VALIDATION_FAILED. Mã game lạ → 400 INVALID_GAME_CODE đã có. Không nhận điểm từ client. |
| Q-T05-06 | ANSWERED | Gate tự động kiểm query, transaction và request đồng thời thật. NFR-P02 đo p95, ghi kết quả so mốc 300 ms; không thêm assertion thời gian. |

ROUND_LIMIT_EXCEEDED (429), GAME_UNAVAILABLE (422) thêm sau mọi mã hiện có, ngay trước INTERNAL_ERROR. Một matcher POST chính xác cho LEARNER cạnh GET danh mục, trước matcher community bao quát; không miễn CSRF hay đổi filter. Giới hạn 30 ván/60 phút trượt đếm mọi trạng thái và suspicious; served_at > since. retryAfterSeconds làm tròn lên, tối thiểu 1. Giữ nguyên giới hạn chung 120 lượt/giờ của engine.

Response 201 chỉ có RoundStart/ServedItemView công khai; câu trả lời đúng và giải thích chỉ nằm trong snapshot DB. TTL engine = 3 + timeLimitSeconds + 15 giây; deadline công khai = servedAt + 3 + timeLimitSeconds. Availability được kiểm lại trực tiếp, không dùng cache danh mục T-04.

Targeted sau sửa fixture: 62/62, F0/E0/S0, BUILD SUCCESS, 1:22 phút. GameRoundControllerIntegrationTest 13, GameRoundServiceTest 7, parser 29, GameRoundApiTest 13. Hai request MockMvc trên hai thread thực với PostgreSQL: ở suất thứ 30, hai game khác nhau chỉ có một 201 và một 429; hai lần tạo cùng game trả hai 201 và còn đúng một IN_PROGRESS, một ABANDONED. Start mới bị ép lỗi trả 500, ván cũ vẫn ABANDONED đúng Q2. P95 20 lần tạo = 43 ms so mốc tham khảo 300 ms, chỉ đo, không gate thời gian.

Lượt đầu compile lỗi suy luận var thành TreeNode khi đọc JSON bằng parser; đã khai JsonNode tường minh. Lượt test đầu có 2 lỗi fixture SUBMITTED thiếu submitted_at, vi phạm ck_att_submitted; đã sửa helper mới theo constraint, không sửa production/migration/test cũ để né lỗi. Chạy lại toàn bộ targeted xanh như trên, không dùng ngoại lệ môi trường.

Hồi quy sau targeted: 278/278, F0/E0/S0, BUILD SUCCESS, 2:12 phút. Gồm toàn bộ Game*Test, ContentLookup*Test, PracticeApiTest nguyên trạng, GlobalExceptionHandlerTest, ModuleBoundaryTest và hai bộ kiểm kê contract 009 (CommunityContractTest, CommentModerationControllerIntegrationTest). Không đổi test/contract 009; vẫn giữ nguyên 24 operation. P95 đo lại trong lượt hồi quy = 44 ms, không có assertion thời gian.

Full Maven verify (Java 17.0.9, DEBUG=false, PostgreSQL/Redis Testcontainers local): 5.899 test, F0/E0/S0, BUILD SUCCESS, 19:40 phút, kết thúc 2026-11-15 01:09:12 +07. Không dùng ngoại lệ môi trường. JaCoCo LINE từ XML mới của chính lượt full: 14.706 covered / 448 missed = 97,0437%; mọi coverage check đạt. GameRoundApi 24/24 dòng, package community.game 89/89 dòng. Không dùng báo cáo coverage của lượt targeted hay task cũ.

Fetch trước bàn giao: origin/main = 0167bac (PR #190, 022/T-04 đã merge). Trong các file T-05 dùng chung, chỉ SecurityConfig thay đổi: thêm POST /api/teacher/grading-requests/{id}/complete hasRole TEACHER ở cụm giáo viên, khác vị trí matcher game. ErrorCode/GlobalExceptionHandlerTest, file 007 và các API/engine learning liên quan không đổi so với base 3164b35. Không merge/rebase main; báo leader thay đổi này để kiểm trên bản gộp.

Self-check PASS: đúng 14 file đã duyệt, UTF-8 không BOM, git diff --check sạch, không dấu conflict/TODO mới; không sửa PracticeApi/PracticeApiTest/PracticeService/PracticeSourceHandler/Attempt, contract, migration, dependency, cấu hình hay frontend. Không SQL trên Neon, secrets hoặc dữ liệu HSK có bản quyền. Giữ giới hạn engine, không nhận điểm client, chỉ dùng fixture tự soạn và tài khoản test; thêm đúng một matcher POST LEARNER, không đổi CSRF/CORS/filter. Ghi rõ Q2 không nguyên tử như đã duyệt. Một conventional commit, stage từng file, Draft PR; T-05 chờ leader review.

## 2026-11-16 · T-06 — Vòng đời ván chơi

Base `b88eb70`, nhánh `spec/007-t06`. Leader duyệt 24 file.

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T06-01 | ANSWERED | `GameRoundHooks` nằm trong `learning.api`, được `community.game.GameRoundHandler` cài đặt. `GameRoundPracticeHandler` gọi qua interface. Engine chỉ thêm một hook default no-op để kiểm ngữ cảnh trước khi ghi đáp án, trong phạm vi khoá hiện có của engine. |
| Q-T06-02 | ANSWERED | Kỷ lục dùng điểm từ snapshot và số câu đúng do server tính. So kỷ lục cũ bằng truy vấn loại chính ván đang kết thúc. Ván suspicious giữ kỷ lục cũ và trả `newBest=false`. |
| Q-T06-03 | ANSWERED | OpenAPI bổ sung 422 cho `/abandon` khi body không hợp lệ hoặc ván hết hạn. Đổi FR-019 và bảng lỗi tương ứng. API uỷ quyền thao tác bỏ ván cho engine. |
| Q-T06-04 | ANSWERED | ID không phải số, không dương hoặc tràn int64 trả 404 `ROUND_NOT_FOUND` như ID không tồn tại. |

Bốn matcher game chỉ cho LEARNER theo đúng method và giữ CSRF. Package learning gọi interface, giữ nguyên ranh giới module. `GameRoundApi` khởi tạo dependency `PracticeService` qua proxy lazy để adapter có thể được đăng ký trong danh sách handler lúc engine khởi động. Fixture cho test finish hiện có được bổ sung định nghĩa game và snapshot, giữ nguyên các assertion cũ.

Leader duyệt bổ sung đúng một file, đưa allowlist lên 25. Test FR012_gameInputPreservesServerLifetimeAndScoringPolicy trong PracticeApiIntegrationTest dùng mã game và snapshot cũ nên handler mới từ chối snapshot trong lượt full đầu. Fixture chuyển sang CHAR_RAIN với timeLimitSeconds, pointsPerCorrect và uiConfig, dùng spy registry chỉ cho lần lookup của ca này. Giữ tên test và bốn kiểm tra TTL 45 giây, mã game lưu, score 0 và maxScore 1. Không đổi engine để đáp ứng fixture.

Targeted và toàn bộ hồi quy learning.practice: 644 test, 0 failure/error/skip. Full Maven verify với Java 17 và Docker: 6765 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1650% (16177 dòng covered, 472 dòng missed).

## T-07 — Ranh giới module và phiên đăng nhập game

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T07-01 | ANSWERED | Nghiệm thu AT-18 theo cấu hình local hiện có, với web ở http://localhost:5173 và game ở http://localhost:5174. Origin https://game.cnhsk.com và domain cookie production do leader cấu hình khi triển khai. MockMvc kiểm tra việc API chấp nhận cookie web và trả header CORS. |

AT-17 xác nhận các class game có mặt trong tập class mà luật ArchUnit hiện có kiểm tra. SharedLoginIT đổi tên thành SharedLoginIntegrationTest để Surefire tự chạy khi verify. AT-18 lấy cookie từ một lần đăng nhập WEB, dùng cho danh mục và ba ván độc lập để kiểm XSRF. Cấu hình app giữ nguyên.

Targeted 156 test. Full verify 6767 test, 0 failure/error/skip, BUILD SUCCESS. JaCoCo LINE 97.1650%. Surefire full verify: [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.953 s -- in com.cnhsk.community.game.SharedLoginIntegrationTest
## T-09 — Phiên đăng nhập Game Box

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T09-01 | ANSWERED | Mobile bỏ 13/11. Tạm dừng FR-030 và AT-22, gồm cầu nối WebView, token tiêm và webview-harness.html. AT-19 kiểm theo nhánh cookie web. |
| Q-T09-02 | ANSWERED | Ngoại lệ FR-024: chỉ GET /api/community/games được thử lại đúng một lần với credentials omit sau khi endpoint refresh từ chối phiên bằng 401. API trả danh mục khách (myStats null) và useCurrentUser chuyển về khách. Mọi request còn lại giữ credentials include. |

Điều kiện refresh 401 TOKEN_EXPIRED hoặc SESSION_REVOKED, tên XSRF-TOKEN và X-XSRF-TOKEN, cùng ngoại lệ CSRF cho refresh được đối chiếu với cnhsk-web/src/shared/api/client.ts, src/main/java/com/cnhsk/shared/config/SecurityConfig.java và src/main/java/com/cnhsk/shared/security/JwtAuthenticationFilter.java.

Gate T-09: npm ci 326 package; Vitest 33/33; lint 0 lỗi; build và typecheck PASS; Chrome 9 kiểm tra ở 320/375/640 px với API fixture local, 0 lỗi runtime; bundle gzip 74.317 byte, dưới 250 KB. Các lệnh FE chạy trên bản sao khớp từng byte trên ổ D.

## T-10 — SCR-035 Game Hub

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T10-01 | ANSWERED | Mặc định chọn “Từ tôi đang học”, mở ván bằng route không có query. Tuỳ chọn HSK 1–9 mở /play/<code>?hskLevel=<n>, khớp FR-006. |

Game Hub đọc danh mục qua client T-09. Danh mục rỗng khi registry chưa có định nghĩa; fixture sáu game chỉ dùng để test. Điểm và số ván bằng 0 được hiển thị khi có myStats. Cách chơi dùng details/summary và nút Chơi bị khoá với game không playable.

Gate T-10: npm ci 326 package; Vitest 36/36; lint, typecheck và build PASS; Chrome QA 320/375/640 px với fixture local PASS, 0 lỗi runtime; gzip 74.933 byte. Gate FE chạy trên bản sao khớp từng byte trên ổ D.
## T-11 — SCR-036 GameShell

| Mã | Trạng thái | Chốt |
|---|---|---|
| Q-T11-01 | ANSWERED | T-11 giao GameShell và adapter round. Route thật giữ trạng thái chờ, spec 008 gắn component game. Game giả chỉ dùng trong test. |
| Q-T11-02 | ANSWERED | Nếu mất phản hồi khi gửi đáp án, đọc GET /rounds/{id} để đối chiếu answered trước khi gửi lại. ROUND_ALREADY_SUBMITTED khi chưa nhận được RoundResult hiển thị “Ván đã kết thúc”; ROUND_EXPIRED hiển thị hết hạn; cả hai có đường về Hub. ROUND_TIME_OVER khoá nhập rồi yêu cầu finish. |

Đồng hồ lấy offset serverTime − Date.now() khi nhận RoundStart hoặc RoundView. Đếm ngược tới startedAt + 3 giây và dùng deadline của server cho thời gian còn lại. Hàng đợi giữ thứ tự, một đáp án đang gửi mỗi lúc; hết giờ và hết câu dùng chung một lần tự finish. Nếu mất phản hồi finish, thao tác kiểm tra lại của người chơi đọc trạng thái ván trước khi gửi finish cho ván còn IN_PROGRESS.

Gate T-11: npm ci 326 package; Vitest 45/45; lint, typecheck và build PASS; Chrome QA 320/375/640 px với fixture local PASS, 0 lỗi runtime; gzip 74.440 byte. Gate FE chạy trên bản sao khớp từng byte trên ổ D.

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

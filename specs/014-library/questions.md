# Chốt — Spec 014

## 2026-11-05 — Leader duyệt 014/T-09

- Base `origin/main` `66a6d7d`, đã chứa #94/T-08 (`f5b491b`); nhánh `spec/014-t09`, worktree `CNHSK-014-t09`. Allowlist 12 file theo Shadow Plan đã duyệt; không sửa FE, SecurityConfig, GlobalExceptionHandler, cấu hình hoặc migration. Giữ nguyên assertion/ngưỡng/fixture T-08; test T-09 được bổ sung riêng.
- DELETE, GET trash và restore cùng lấy `pg_advisory_xact_lock(hashtextextended(:key,0))` với key `library:notebook:<userId>` trong transaction READ_COMMITTED. Dùng bean Clock hiện có, lấy `now` đúng một lần sau khi giữ khoá; timestamp T-09 bind vào SQL, không dùng đồng hồ DB. userId chỉ lấy qua AuthApi.
- DELETE trả 204, body rỗng. Xoá lại ghi chú của mình vẫn 204 và giữ nguyên deleted_at đầu tiên. Bản ghi không bị xoá vật lý; GET/list/PUT thông thường tiếp tục loại bản đã xoá.
- GET `/api/notes/trash` trả mảng theo contract, không phân trang; chỉ NOTEBOOK còn hiệu lực của người gọi, ghi chú đã xoá còn hạn. Chưa có sổ trả `[]`, không tạo sổ. Thứ tự `deleted_at DESC, id DESC`; `restorableUntil = deletedAt + 30 × 24 giờ UTC`. Đúng mốc 30 ngày còn hợp lệ, quá mốc thì ẩn khỏi thùng rác, giữ dữ liệu trong DB. Snippet là văn bản thô tối đa 200 code point.
- Thứ tự restore bắt buộc: (1) quyền sở hữu, ID sai/không tồn tại/khác người/khác kind hoặc notebook đã xoá → 403 COLLECTION_NOT_OWNED; (2) chưa xoá hoặc quá hạn → 409 NOTE_NOT_RESTORABLE; (3) trùng lexemeId với ghi chú chưa xoá khác → 409 NOTE_DUPLICATE kèm details.noteId; (4) hạn mức → 422 LIBRARY_LIMIT_REACHED với details{kind: NOTES, limit}; (5) ghi deleted_at=NULL, updated_at=now và trả 200 Note, giữ nguyên nội dung/metadata. Trùng từ ưu tiên trước quota; không gọi kiểm mục từ tồn tại của POST/PUT khi restore.
- Spec FR-012, bảng lỗi §5, AT-11 và OpenAPI được bổ sung NOTE_DUPLICATE cho restore trước implement. NOTE_NOT_RESTORABLE chỉ thêm một dòng ngay trước INTERNAL_ERROR; GlobalExceptionHandler hiện có đã hỗ trợ details của quota và duplicate.
- Test thời gian cố định: 29/30/31 ngày, đúng mốc và quá mốc 1 microsecond. Test quota 999/1.000, trùng từ và ưu tiên lỗi, ghi chú không gắn từ, IDOR/kind/parent đã xoá, role/CSRF. Test PostgreSQL thật xác nhận cùng advisory key cho cả ba endpoint, create/restore tranh quota hoặc từ, hai restore cùng ghi chú, người khác không bị khoá chung.
- Sau gate chọn lọc và full verify: báo số test/failure/error/skip, JaCoCo LINE, diff/BOM/TODO/allowlist. Chỉ nhóm lỗi môi trường leader đã duyệt được ghi rõ số đo rồi mở Draft PR. Một conventional commit, stage từng file cụ thể, push nhánh, Draft PR, không merge; fetch/báo thay đổi file dùng chung, không rebase.
- Kết quả gate Java 17, DEBUG=false, PostgreSQL/Redis Testcontainers: chọn lọc 74 test PASS; full `mvnw -B verify` 2.994 test, 0 failure/error/skip, BUILD SUCCESS (13 phút 32 giây). JaCoCo LINE toàn bộ 8.709/8.960 = 97,1987%; package library 406/409 dòng. Không dùng ngoại lệ môi trường, không đổi ngưỡng hoặc skip test.
- Fetch trước PR: main từ `66a6d7d` lên `211e7e3`, chứa #98 và #99/T-04. SecurityConfig, ErrorCode, GlobalExceptionHandler, application.yaml không đổi giữa base và main mới; tasks/questions và FE library có cập nhật T-04. Giữ nguyên base, không rebase/merge main vào nhánh; leader giữ cả chốt và trạng thái T-04/T-09 khi gộp.

## 2026-11-04 — Leader duyệt 014/T-08, hướng B

- Base lúc bắt đầu `cd7d1cb`, cập nhật lần lượt qua `34f5dff` (#91) và `b737ffd` (#92) trước PR. #89 đã merge tại `287ad95`. #92 thêm bốn mã lỗi sau QUESTION_NOT_FOUND, matcher sửa/lưu trữ câu hỏi và details cho MALFORMED_QUESTION; đã giữ nguyên các thay đổi này khi tích hợp, ba mã T-08 đặt sau QUESTION_ARCHIVED, ngay trước INTERNAL_ERROR. Allowlist 19 file; sửa spec/contract theo chốt này trước code; không sửa DB hoặc migration.
- BR-062-4 đổi thành mỗi `lexemeId` chỉ có một ghi chú chưa xoá trong sổ tay; giữ index `uq_coll_lex` của V1. Ghi chú không gắn từ có thể trùng nội dung; ghi chú trong thùng rác không tính trùng. POST trùng hoặc PUT đổi sang từ của ghi chú khác trả 409 `NOTE_DUPLICATE` với `details{noteId}`. PUT giữ nguyên từ của chính nó vẫn hợp lệ. PUT tham chiếu mục từ không có trả 404 `LEXEME_NOT_FOUND`.
- Tạo sổ mặc định, kiểm quota và trùng từ dùng chung `pg_advisory_xact_lock(hashtextextended(:key,0))`, key `library:notebook:<userId>`, trong transaction READ_COMMITTED. Không có unique constraint cho NOTEBOOK/người nên luôn lấy khoá trước tìm/tạo. GET khi chưa có sổ không tạo dữ liệu. PUT vẫn được sửa khi đủ quota; `updated_at` lấy tại thời điểm ghi bằng `clock_timestamp()`.
- Thêm cấu hình bắt buộc, dương `cnhsk.library.max-notes: 1000`; không khai trước hạn mức bộ/thẻ. `user_id` chỉ lấy từ phiên qua AuthApi; payload userId bị bỏ qua. Query luôn kiểm chủ sở hữu, kind NOTEBOOK và chưa xoá ở cả sổ/ghi chú; thiếu hoặc không sở hữu trả cùng 403 `COLLECTION_NOT_OWNED`.
- V1 đã có extension pg_trgm và `ix_item_search` GIN trên content; title chưa có index trigram. T-08 dùng index hiện có, xếp similarity rồi updated_at DESC, id DESC, escape LIKE và bind giá trị; cần index title thì báo leader. NFR-P04 p95 dưới 200 ms trên 1.000 ghi chú tự soạn.
- Testcontainers cho thấy Flyway cài pg_trgm trong schema learning, khác search_path của kết nối ứng dụng. Repository tra schema từ pg_extension/pg_namespace và quote identifier để gọi similarity đúng chỗ, không giả định learning/public, không đổi search_path, extension hoặc migration. Giá trị tìm kiếm vẫn bind tham số.
- GlobalExceptionHandler cho trả details riêng với `LIBRARY_LIMIT_REACHED` và `NOTE_DUPLICATE`; không cho extra ghi đè error_code/message/request_id. Matcher LEARNER cho /api/notes và /api/notes/**, CSRF theo cơ chế hiện có.
- T-09: khi khôi phục ghi chú có lexemeId trùng một ghi chú chưa xoá khác, trả 409 `NOTE_DUPLICATE`, không để lỗi unique thành 500; dùng cùng advisory key.
- T-15: SCR-025 nhận 409 `NOTE_DUPLICATE` khi lưu vào sổ tay thì mở ghi chú `details.noteId` để sửa. T-08 không sửa FE.

## 2026-11-04 — Leader duyệt 014/T-07

- Base: `origin/main` `82a6b3b`, merge PR #87 (T-06); leader xác nhận full verify PASS tại `82a6b3b`. Allowlist 12 file, nhánh `spec/014-t07`, một conventional commit, PR Draft, không tự merge.
- `strokeData` là object mô tả nguồn, không phải payload nét: mọi `CHAR` trả `{"source":"hanzi-writer-data"}`, kể cả khi `strokeCount` null; `WORD`/`GRAMMAR` trả null. SCR-025 chỉ kiểm khác null rồi dùng `HanziCanvas` của 006 tự tải theo chữ. Backend không gọi CDN, không lưu/nạp dữ liệu nét hoặc thêm migration. Quyết định này làm rõ FR-007/AT-07, giữ nguyên hình dạng object/null của contract.
- Việc cho T-15: kiểm FE ẩn gọn khối hoạt hình khi CDN không có dữ liệu cho chữ đó; phần còn lại của chi tiết vẫn hiện. Đối chiếu thêm ID `int64` của API với `number` và kiểm tra safe integer trên FE; T-07 không sửa FE.
- `related`: lấy `RELATED` và `SYNONYM` theo cả hai chiều, bỏ trùng theo ID, sắp `id ASC`, tối đa 10. Không lấy `CONTAINS`; liên kết này dùng riêng cho `components` (chiều đi ra, không giới hạn) và `containedIn` (chiều đi vào, tối đa 10). Hai nhóm CONTAINS cũng sắp `id ASC`.
- `/radicals` không phân trang; chỉ đếm CHAR có bộ thủ, sắp `charCount DESC, radical ASC`. `/by-radical` cần ít nhất một bộ lọc; `radical` tối đa 10 ký tự, `strokes` nguyên 1–64; phân trang mặc định 0/20, size 1–50, kiểm sai trả 422 `VALIDATION_FAILED`. Thứ tự `stroke_count ASC NULLS LAST, frequency DESC, id ASC`.
- Chỉ fallback ±1 nét khi tổng số kết quả chính xác bằng 0 và có `strokes`; giữ bộ lọc bộ thủ, chỉ xét số nét trong 1–64, đặt `approximate=true`. Trang vượt cuối không kích hoạt fallback.
- Chi tiết tồn tại dùng đúng 2 SELECT: trường công khai và một truy vấn gộp các nhóm liên kết. Không N+1, không trả toàn bộ `extra`, `exercises` hoặc đáp án. Mảng rỗng trả `[]`. ID sai định dạng trả 404 `LEXEME_NOT_FOUND` trước truy vấn DB; ID không có trả cùng lỗi sau một SELECT. Endpoint chi tiết không có trường hợp 422 trong contract.
- Cả ba endpoint nằm trong `DictionaryController`, dùng chung `@ModelAttribute`/`LibraryRateLimiter` với `/search`, đếm đúng một lần trước kiểm tham số, kể cả 422/404. Không thêm matcher hoặc cấu hình; giá trị SQL được bind tham số, không thêm index/migration.
- `LEXEME_NOT_FOUND` thêm ngay trước `INTERNAL_ERROR` theo luật L2. NFR-P02: p95 chi tiết dưới 150 ms trên 5.000 mục từ tự soạn. Khi gate xanh, leader cho phép đánh ✅ T-06 kèm ghi chú `leader full verify PASS tại 82a6b3b` và ✅ T-07 trong cùng PR.
- T-03, T-13, T-14 tiếp tục gác theo phân công của leader.

## 2026-11-04 — Leader duyệt 014/T-06

- Base lúc bắt đầu: `origin/main` `ee74b24`, remote `TriNM2505/CNHSK`; đã cập nhật nhánh lên `7f6e615` (PR #86). `010/T-10` đã merge qua PR #66 (`c3a5fc7`). Bản kế hoạch trước dùng base `a740027`; đã đối chiếu các matcher knowledge-tree/exam/placement và khối cấu hình placement trên main mới. Hai file dùng chung không đổi giữa `ee74b24` và `7f6e615`.
- Khách: 60 lượt/giờ/IP. Mọi tài khoản đã xác thực: 600 lượt/giờ theo `userId`, không phân biệt role. Bộ đếm Redis nguyên tử, cửa sổ giờ UTC, TTL tới hết giờ; mỗi request đếm một lần trước kiểm tra tham số, kể cả 422. Redis lỗi vẫn cho tra, log WARN không chứa query, IP hoặc thông tin kết nối.
- IP lấy từ `getRemoteAddr()`, không đọc `X-Forwarded-For`. Khi triển khai sau reverse proxy, leader theo dõi backlog cấu hình `server.forward-headers-strategy`; T-06 không thay đổi cấu hình proxy.
- Fallback nhiều chữ Hán: bỏ chữ trùng, giữ thứ tự xuất hiện đầu tiên; ví dụ `人人` chỉ trả mục từ `人` một lần.
- `LibraryConfig` chỉ có `guest-lookups-per-hour` và `authenticated-lookups-per-hour`; cả hai bắt buộc, phải dương và kiểm khi khởi động. Các hạn mức dịch/ôn/cache do task sử dụng tự thêm.
- T-06 dùng nguyên contract, `PinyinUtils`/`TextNormalizer` và schema hiện có; query được bind tham số, wildcard LIKE được escape. Phân trang tại DB; không thêm mã lỗi.
- Allowlist 16 file; gate JDK 17, DEBUG=false, Docker và Maven verify. Nếu gate xanh, leader cho phép đánh ✅ T-06 trong cùng PR. Chỉ lỗi thuộc nhóm môi trường đã biết (FR023 p95, T08_QUESTION_CONFIRM_BENCHMARK, PEXPIRETIME, CapturedOutput) được báo số đo và mở PR theo ngoại lệ leader duyệt; không hạ ngưỡng hoặc skip test.

## 2026-11-04 — Leader duyệt 014/T-02

- SCR-024/025 là hai màn thật đầu tiên của library; dùng API client T-01, preview/test bằng dữ liệu tự soạn. Nghiệm thu nối backend thật ở T-15.
- Đã đăng nhập nhưng không có `LEARNER`: ẩn thao tác lưu và hiện **"Cần quyền học viên để lưu"**. Khách thấy **"Đăng nhập để lưu"**; chỉ `LEARNER` gọi API sổ tay/bộ thẻ.
- Hoạt hình chỉ hiện với mục từ `CHAR` có `strokeData != null`. Dùng nguyên `HanziCanvas` và loader dữ liệu của 006; không truyền payload nét vào canvas. `onDataError` ẩn riêng khối hoạt hình, phần còn lại của trang vẫn hiển thị. Chỉ xem/hoạt hình, không gọi quiz, ghi lượt luyện hoặc đổi mastery.
- Không đổi contract hay thêm bộ lọc HSK khi API chưa hỗ trợ. Giữ truy vấn/bộ lọc/trang khi đi sang chi tiết rồi quay về.
- Spec 013 và 015 tạm gác; 014/T-13, T-14 cũng gác theo phân công mới. Nút gợi ý dịch của `LEARNER` chỉ điều hướng tới route có sẵn; T-02 không gọi dịch hoặc entitlement.
- Leader cho phép đánh ✅ T-02 trong cùng PR; allowlist 14 file, một conventional commit, PR Draft, không tự merge.

## 2026-11-03 — Leader duyệt điều chỉnh 014/T-01

- Spec §0.4 và T-01 ghi nhầm `shared/auth/navigation.ts`: file này chỉ xử lý đích sau đăng nhập; menu thực tế ở `shared/layout/navigation.ts`.
- T-01 chỉ thêm route, catalog màn và preview; không sửa `shared/auth/navigation.ts`, `shared/layout/navigation.ts`, `LearnerLayout.tsx`, `PublicLayout.tsx` hoặc `Layouts.test.tsx`.
- Menu học viên chuyển sang PR riêng **điều hướng học viên chung**, sau khi các T-01 của 011, 012, 014, 015 merge; leader giao riêng.
- `screens.ts` dùng `{} satisfies ScreenCatalog`. Không đăng ký `ScreenUnderConstructionPage`: `ScreenRoute` đã tự hiển thị màn tạm cho SCR chưa có page. T-02–T-05 đăng ký page thật.
- Ghi nhận cùng lỗi đường dẫn ở 013/T-01: khi làm mục điều hướng ADMIN, sửa `shared/layout/navigation.ts` theo phân công của leader.
- Leader cho phép cập nhật ✅ T-01 trong PR này; một commit, PR Draft, không tự merge.

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md`, constitution v1.4.1 (BUS-01/03/04/09/10, HR-07, AC-09), migration V1 (`lexemes`, `lexeme_links`, `collections`, `collection_items`, `user_progress`, index trigram), UC-056…UC-068 (`use-cases-05`), `screen-fields.md` SCR-024…SCR-029, `SecurityConfig` (`/api/public/**`), plan `261102-2104` phase 6, 7, spec 011 (FR-019 FSRS), spec 013 (FR-020/FR-021 `EntitlementApi`).

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Ôn flashcard | **Trong SCR-029, 4 nút** (Quên/Khó/Được/Dễ), dùng `FsrsScheduler` 011, ≤ 100 thẻ/buổi, không đổi mastery |
| Q2 | Dịch câu SCR-026 | **Làm bằng Claude**, trừ lượt `TRANSLATE` qua `EntitlementApi` 013, hoàn khi lỗi |
| Q3 | Giới hạn tra cứu | **GUEST 60/giờ/IP**, LEARNER 600/giờ, cấu hình được |
| Q4 | Tra bộ thủ + số nét | **Làm bộ lọc đơn giản** |
| Q5 | Bộ từ chủ đề | **Có, chép từ chủ đề 003** |
| Q6 | Ghi chú đã xoá | **Khôi phục trong 30 ngày** |
| T1 | Lịch sử dịch | **Không lưu, chỉ cache** 7 ngày |
| T2 | Model dịch | **`claude-haiku-5-5`** |
| D | Mặc định | **Duyệt**: GUEST thấy đủ trường (chỉ ẩn nút lưu); không tìm thấy → 200 rỗng; dữ liệu riêng → 403; một sổ tay mặc định; thùng rác không có job xoá hẳn; chia sẻ bộ là V2; dịch theo UC-060; không migration; FE trước |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| V1 | UC-066 3 mức tự đánh giá ↔ SCR-013 4 mức | Q1: 4 mức |
| V2 | Giới hạn khách: UC-056 theo giờ (ví dụ 50) ↔ UC-132 theo ngày | Q3 |
| V3 | UC-056 "kết quả rút gọn" cho khách không định nghĩa | Đủ trường |
| V4 | UC-056 404 ↔ UC-132 200 khi không có kết quả | 200 rỗng |
| V5 | UC-064 `NOTE_NOT_FOUND` 404 ↔ `BUS-01` 403 | 403 `COLLECTION_NOT_OWNED` |
| V6 | UC-133 khôi phục ghi chú, thời hạn chưa duyệt | Q6 30 ngày — ghi RFC |
| V7 | Plan phase 6 "không dịch câu (tốn phí)" | Q2 — sửa plan phase 6 |
| V8 | Thẻ flashcard và mục từ 011 cùng một từ → hai lịch ôn | Chấp nhận (SCR-029) |
| V9 | UC-060 dùng bảng `feature_usage`, `translation_history` không có trong V1 | Dùng `EntitlementApi` + cache Redis (T1) |

### Còn mở

- Plan phase 6 cần sửa theo Q2 (có dịch), Q1 (ôn FSRS 4 mức), Q6 (thùng rác) — hỏi leader trước khi sửa.
- Thêm vào RFC chung: V6 (thời hạn 30 ngày).
- `ANTHROPIC_API_KEY`: leader đặt trong `.env` khi chạy dịch/trợ lý thật (nhắc cùng 015).

# Chốt — Spec 014

## 2026-11-05 — Leader duyệt 014/T-10

- Nhánh `spec/014-t10`, worktree `CNHSK-014-t10`, base `211e7e3` được leader chấp thuận dù T-05 đã merge tại `96ac2aa`; không rebase. Một conventional commit, push nhánh, Draft PR, leader review/merge. Không đụng thay đổi riêng ở worktree gốc.
- Allowlist đúng 14 file:
  1. `src/main/java/com/cnhsk/learning/library/DeckService.java`
  2. `src/main/java/com/cnhsk/learning/library/DeckRepository.java`
  3. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
  4. `src/main/java/com/cnhsk/learning/library/web/DeckWrite.java`
  5. `src/main/java/com/cnhsk/learning/library/LibraryConfig.java`
  6. `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`
  7. `src/main/resources/application.yaml`
  8. `src/test/java/com/cnhsk/learning/library/LibraryConfigTest.java`
  9. `src/test/java/com/cnhsk/learning/library/DeckIntegrationTest.java`
  10. `src/test/java/com/cnhsk/learning/library/DeckConcurrencyIntegrationTest.java`
  11. `src/test/java/com/cnhsk/learning/library/DeckQueryCountIntegrationTest.java`
  12. `src/test/java/com/cnhsk/shared/config/DeckSecurityConfigIntegrationTest.java`
  13. `specs/014-library/tasks.md`
  14. `specs/014-library/questions.md`
- Chỉ năm endpoint của bộ thẻ theo contract đã duyệt: GET danh sách, POST 201 `DeckDetail`, GET chi tiết, PATCH 200 `DeckSummary`, DELETE 204. ID sai định dạng, không tồn tại, của người khác, khác loại hoặc đã xoá đều 403 `COLLECTION_NOT_OWNED`. Không triển khai thêm/sửa/xoá từng thẻ hoặc ôn FSRS của T-11/T-12.
- Mọi ghi chạy trong một transaction READ_COMMITTED, lấy advisory lock `library:decks:<userId>` trước, sau đó lấy `now` từ Clock một lần. Khoá tách khỏi `library:notebook:<userId>` và người dùng khác. Chủ sở hữu luôn lấy qua AuthApi, bỏ qua userId/isPublic trong payload; bộ mới luôn riêng tư.
- POST kiểm chủ đề tồn tại trước hạn mức; thiếu trả 404 `TOPIC_NOT_FOUND`. Có 50 bộ chưa xoá thì 422 `LIBRARY_LIMIT_REACHED`, `details{kind: DECKS, limit: 50}`. Tên 1–100 Unicode code point, cho trùng; mô tả tuỳ chọn tối đa 500. PATCH chỉ đổi tên/mô tả và updated_at, vẫn được sửa khi đủ hạn mức, không chép lại topicId.
- Chép tối đa 500 `topic_items` có lexeme, theo `order_index ASC, id ASC`; bỏ mục chỉ có knowledge point. Snapshot mặt trước là text; mặt sau ghép pinyin và meaningVi bằng ` — `, không có pinyin thì chỉ nghĩa. Chủ đề rỗng tạo bộ rỗng. Không tạo user_progress, không cập nhật snapshot khi nguồn thay đổi. Lỗi chép rollback cả bộ lẫn thẻ.
- Chủ đề hơn 500 từ: chép 500 thẻ đầu. `DeckDetail`/`DeckSummary` hiện không có trường báo đã cắt; giữ nguyên contract, không tự thêm `truncated` hoặc trường khác.
- Danh sách dùng một SELECT, sắp created_at DESC/id DESC; chi tiết dùng tối đa hai SELECT, thẻ theo order_index/id. SQL đếm cardCount/dueCount, bỏ thẻ đã xoá; lịch chỉ nối theo người gọi và target_type FLASHCARD. Theo FR-017, thẻ mới chưa có dòng FLASHCARD hoặc next_review_at không muộn hơn now được tính đến hạn. Không N+1.
- DELETE chỉ đặt deleted_at của bộ; giữ nguyên thẻ, lịch ôn và updated_at. Bộ đã xoá không còn hợp lệ cho các thao tác, kể cả xoá lần nữa (403 theo contract bộ thẻ, khác sổ tay).
- Cấu hình bắt buộc và dương khi khởi động: `cnhsk.library.max-decks: 50`, `max-cards: 500`. SecurityConfig chỉ thêm đúng matcher LEARNER cho `/api/flashcard-decks` và `/api/flashcard-decks/**`; không sửa schema, migration, FE, ErrorCode hoặc GlobalExceptionHandler.
- Gate: test chọn lọc bốn lớp Deck và LibraryConfigTest, sau đó full `mvnw.cmd -B verify` với Java 17, DEBUG=false, Docker; kiểm diff, BOM, dấu việc chưa làm và phạm vi 14 file. Chỉ ngoại lệ môi trường đã được leader chốt mới được ghi số đo và vẫn mở PR, không hạ assertion/ngưỡng hoặc skip test.
- Test chọn lọc PASS 46/46, không failure/error/skip. PostgreSQL thật kiểm tranh suất 49/50, hai lần xoá đồng thời, khoá riêng theo người dùng/sổ tay và Clock lấy sau khi chờ khoá; lỗi sau khi INSERT thẻ rollback cả bộ/thẻ. Hibernate Statistics đo qua MockMvc: danh sách đúng 1 SELECT với 0/1/10/50 bộ, chi tiết đúng 2 SELECT với 500 thẻ (không tồn tại 1, ID sai định dạng 0).
- Fetch trước PR: `origin/main` là `96ac2aa`. So với base `211e7e3`, trong allowlist chỉ `tasks.md` và `questions.md` đổi; toàn bộ file code/config T-10 chưa đổi trên main. Không rebase hoặc gộp main vào nhánh. Khi gộp tài liệu, leader giữ chốt/✅ T-05 và T-09 đã có trên main (8/15 trước T-10, 9/15 sau khi gộp); bộ đếm của nhánh dựa trên base riêng.
- Full `mvnw.cmd -B verify` PASS: 3.002 test, 0 failure, 0 error, 0 skip; 12 phút 36 giây, Java 17, DEBUG=false, Docker. JaCoCo LINE toàn bộ 8.784/9.032 = 97,2542%; library và web 508/513. NFR-P04 sổ tay giữ nguyên ngưỡng 200 ms, p95 55,612 ms trên 1.000 ghi chú. Không dùng ngoại lệ môi trường, không đổi assertion/ngưỡng test T-08; đánh ✅ T-10 theo duyệt.
- Self-check PASS: đúng 14 file allowlist, diff sạch, UTF-8 không BOM, không dấu việc chưa làm trong file thay đổi. Không thêm dependency, không JOIN xuyên module, giá trị SQL bind tham số; AuthApi cung cấp người gọi. Quyền/CSRF có integration test, không thêm log dữ liệu riêng, không sửa credentials/schema/migration/FE/ErrorCode/GlobalExceptionHandler. SecurityConfig chỉ thêm một dòng matcher; commit và push chỉ nhánh T-10 đã được duyệt, không merge.

## 2026-11-05 — Leader duyệt 014/T-04

- Base `origin/main` `bdccaac`, nhánh `spec/014-t04`, worktree `CNHSK-014-t04`. Allowlist đúng 12 file: `cnhsk-web/src/features/library/personalNotes.ts`, `personalNotes.test.ts`, `components/PersonalNotesView.tsx`, `components/NoteEditor.tsx`, `pages/Scr027PersonalNotesPage.tsx`, `pages/Scr027PersonalNotesPage.test.tsx`, `catalog.preview.ts`, `screens.ts`, `routes.test.tsx`, `api/client-integration.test.ts`, `specs/014-library/tasks.md`, `specs/014-library/questions.md` (các đường dẫn FE rút gọn cùng gốc `cnhsk-web/src/features/library/`). Một conventional commit, Draft PR, leader review/merge.
- SCR-027 dùng page gọi `api/notes`, View/Editor chỉ nhận props; preview độc lập có loading/empty/error/ready cho ghi chú và thùng rác. Không import preview trong page, không thêm dependency, không sửa shared/app/backend/DB hoặc contract. Các model và API ghi chú T-01 đã khớp contract T-08; thêm test giữ nguyên error details.
- Tìm sau trim và NFC, 2–100 Unicode code point, kiểm biên 1/2/100/101; bỏ q khi rỗng. Lọc tag bằng ô nhập, khớp chính xác, không gợi ý tag. Trang API từ 0, mặc định 20; đổi bộ lọc về trang đầu. Thùng rác dùng endpoint mảng hiện có và phân trang phía client.
- Backend kiểm hạn mức; FE không kiểm quota và không gọi thêm truy vấn lấy tổng ghi chú. 422 `LIBRARY_LIMIT_REACHED` hiển thị `details.limit`, giữ nguyên editor. Số `total` chỉ dùng phân trang.
- 409 `NOTE_DUPLICATE` hiện đúng "Từ này đã có trong sổ tay" và nút "Mở ghi chú đã có" theo `details.noteId`; không tự chuyển editor. Chỉ tải khi người dùng bấm; tải thất bại giữ bản nháp và cho thử lại. Chỉ dùng noteId là số nguyên dương an toàn; đánh giá đầy đủ int64 vẫn thuộc T-15.
- Tiêu đề tuỳ chọn theo OpenAPI `NoteRequest` và `NoteWrite` T-08; `screen-fields.md` SCR-027 cũ còn ghi bắt buộc. Nội dung/tiêu đề/thẻ có giới hạn và bộ đếm; sửa giữ nguyên `lexemeId`/`sourceType`, không thêm bộ chọn điểm kiến thức. Mọi lỗi tạo/sửa giữ nguyên dữ liệu nhập.
- Snippet là văn bản thô: React text node và `<mark>` chứa các đoạn text, không HTML injection. AT-26 có `<script>` và HTML, từ khoá chứa ký tự regex. Xoá cần ConfirmPanel trong trang, huỷ trả focus về trigger; không cập nhật danh sách trước API thành công.
- Thùng rác hiển thị `deletedAt` và `restorableUntil` theo giờ Việt Nam; xử lý `NOTE_NOT_RESTORABLE`, `NOTE_DUPLICATE`, `LIBRARY_LIMIT_REACHED`. Huỷ request cũ, bỏ kết quả đến muộn, xoá state riêng khi đổi tài khoản; chặn gửi lặp. Backend xoá/khôi phục thuộc T-09, nghiệm thu nối backend thật thuộc T-15.
- Gate: `npm ci` PASS; `npx vitest run --maxWorkers=2 --minWorkers=2` PASS 145 file, 1.825 test, không failure/skip; lint 0 error, duy nhất warning `react-refresh/only-export-components` tại LevelBadge có sẵn; production build PASS. Trên Windows gọi `npm.cmd`/`npx.cmd` do PowerShell chặn script shim `.ps1`.
- Fetch trước PR: main lên `0acbae7`, #94/T-08 merge tại `f5b491b`, #97/010-T-13 merge sau đó. So với base, cả `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandler.java`, `application.yaml` đã đổi; đã báo leader, không rebase/merge main vào nhánh. `tasks.md`/`questions.md` cũng có cập nhật T-08 trên main; leader giữ cả chốt T-08 và T-04 khi gộp.
- Sau T-04 chỉ soạn Shadow Plan T-09 khi leader báo tiếp tục; không tự bắt đầu code T-09.

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

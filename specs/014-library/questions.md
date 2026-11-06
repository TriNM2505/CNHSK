# Chốt — Spec 014

## 2026-11-06 — Leader duyệt 014/T-13 Segmenter

- Fetch origin/main `fae62f1` (đã có PR #125/T-12), tạo nhánh `spec/014-t13-segmenter` và worktree `CNHSK-014-t13` từ đúng base này. Không merge/rebase hoặc sửa worktree gốc.
- Allowlist đúng 6 file:
  1. `src/main/java/com/cnhsk/learning/library/Segmenter.java` — service điều phối, hàm static split thuần và các record kết quả; exception riêng không chứa nội dung đầu vào.
  2. `src/main/java/com/cnhsk/learning/library/SegmenterQueryRepository.java` — một native SELECT với tham số mảng, chọn lexeme ưu tiên.
  3. `src/test/java/com/cnhsk/learning/library/SegmenterTest.java` — fixture tự soạn, kiểm tách thuần, Unicode, giữ nguyên văn bản, pinyin và lỗi repository.
  4. `src/test/java/com/cnhsk/learning/library/SegmenterIntegrationTest.java` — PostgreSQL Testcontainers, fixture commit trước đo, đếm SELECT và benchmark 2.000 code point.
  5. `specs/014-library/questions.md` — các quyết định Q-T13-xx và bằng chứng gate.
  6. `specs/014-library/tasks.md` — chỉ đánh dấu T-13 và cập nhật tiến độ khi gate đạt.
- Q-T13-01: cùng text chọn WORD > GRAMMAR > CHAR, sau đó id nhỏ nhất. SQL DISTINCT ON (x.text), ORDER BY x.text, CASE x.kind ..., x.id ASC; không phụ thuộc thứ tự Map hay thứ tự chèn fixture.
- Q-T13-02: chữ CJK không có trong bảng tra trả mỗi code point một segment, pinyin và lexemeId đều null; không gộp các chữ lạ.
- Q-T13-03: gộp dải không-CJK liên tiếp, giữ nguyên mọi khoảng trắng, dấu câu, chữ Latin, số, emoji và xuống dòng. Ghép segment.text phải đúng đầu vào, không trim/chuẩn hoá nội dung.
- Q-T13-04: whitelist các block CJK Unified Ideographs, Extension A–G, CJK Compatibility Ideographs và Compatibility Supplement, đồng thời Character.isIdeographic(int). Dấu câu CJK, chữ kana/hangul, bộ thủ và ký hiệu ngoài whitelist không được tra. Dùng code point và offset UTF-16; từ dài tối đa 8 code point, không cắt đôi surrogate pair.
- Q-T13-05: GRAMMAR chứa ký hiệu như 是…的 tự nhiên không nằm trong candidate CJK liên tục; không xoá ký hiệu hoặc biến đổi pattern để khớp.
- Segmenter.segment() gom distinct mọi chuỗi con CJK liên tục dài 1…8, gọi repository đúng một lần nếu tập không rỗng; rỗng/không-CJK không gọi repository và không SELECT. Segmenter.split(text, Map) là hàm thuần, so khớp dài nhất trước. Null đầu vào ném IllegalArgumentException.
- Repository dùng `x.text = ANY(:texts)` với String[] được bind tường minh bằng Hibernate NativeQuery.setParameter(name, value, String[].class); PostgreSQL nhận một tham số mảng. Không SQL nối input, không setParameterList, không hàng nghìn placeholder, không truy vấn theo từng đoạn. Điều kiện kind và char_length giữ đúng phạm vi từ điển. Chỉ đọc learning.lexemes, không entity/dependency/migration mới.
- Pinyin từng đoạn có thể null; bỏ qua null/rỗng/toàn khoảng trắng khi nối pinyin cả đoạn bằng một dấu cách, không đoạn nào có pinyin thì kết quả null. Pinyin có dấu được giữ nguyên sau bỏ khoảng trắng ở hai đầu; không chọn kind ưu tiên thấp hơn chỉ vì pinyin của kind đã chọn là null.
- Lỗi repository được đổi thành Segmenter.SegmentationException có thông báo cố định, không đính kèm cause có thể chứa tham số SQL, không log văn bản, không trả kết quả fallback. T-14 chịu trách nhiệm bắt lỗi và giữ bản dịch. Không endpoint, TranslationService, Claude, mã lỗi mới hoặc thay đổi SecurityConfig/contract trong T-13.
- Test tích hợp dùng PostgreSQL thật và fixture tự soạn đã commit trước khi gọi segmenter. Đầu vào hiệu năng gồm 2.000 chữ Ext B khác nhau (4.000 UTF-16 code unit), tạo 15.972 candidate distinct; đo toàn bộ segment() gồm truy vấn, tạo kết quả và transaction. Báo lượt đầu, sau 3 lượt warm-up lấy 20 mẫu, p50/p95 theo nearest-rank. Mốc 200 ms là mục tiêu mềm theo duyệt; không đổi fixture hoặc hạ assertion để che số đo chậm. Cleanup chỉ xoá đúng ID fixture do lớp test đã chèn.
- Gate: toàn bộ test library, sau đó full `mvnw.cmd -B verify`, Java 17, DEBUG=false, Docker; giữ assertion/ngưỡng test cũ. Kiểm phạm vi 6 file, diff, UTF-8 không BOM và không dấu việc chưa làm; fetch trước PR, báo thay đổi file dùng chung, không rebase/merge. Một conventional commit, push nhánh đã duyệt, Draft PR vào main; không merge PR.
- Kết quả gate ngày 2026-11-07: chọn lọc đủ 23 lớp library PASS 322/322, 0 failure/error/skip, 1 phút 47 giây. SegmenterTest 29 và SegmenterIntegrationTest 5 đều PASS, gồm null pinyin, mọi block đã chốt, giới hạn 8 code point và lỗi không chứa văn bản. Full `mvnw.cmd -B verify` PASS 3.833/3.833 test, 0 failure/error/skip, 16 phút 43 giây; Java 17, DEBUG=false, PostgreSQL Testcontainers. JaCoCo LINE 10.957/11.325 = 96,7506%, vượt ngưỡng 80%; toàn bộ lớp Segmenter/record/exception/repository đạt LINE 76/76. Không dùng ngoại lệ môi trường, không skip/hạ assertion. Đánh ✅ T-13, tổng 12/15.
- SQL đo thực tế: đầu vào 2.000 code point, 15.972 candidate dùng đúng 1 SELECT; rỗng/không-CJK dùng 0 SELECT; ưu tiên kind và bind String[] trực tiếp đều được xác nhận trên PostgreSQL. Benchmark trong full verify: lượt đầu 118,642 ms, p50 54,492 ms, p95 105,246 ms (20 mẫu sau 3 warm-up), dưới mục tiêu mềm 200 ms. Lượt chọn lọc trước đó: đầu 112,331 ms, p50 354,914 ms, p95 411,126 ms, vượt mục tiêu mềm; giữ nguyên cả hai số đo và cùng fixture để thể hiện dao động giữa các lượt chạy, không thay đổi ngưỡng/assertion. Lượt chạy riêng ban đầu 34/34 PASS có số đo đầu 258,328 ms, p50 368,916 ms, p95 429,204 ms.
- Fetch trước bàn giao: origin/main vẫn `fae62f1`, không file nào trong allowlist đổi so với base. Self-check PASS: đúng 6 file, diff sạch, UTF-8 không BOM, không thêm dấu việc chưa làm; không dependency/schema/contract/endpoint mới, không truy vấn xuyên module hoặc SQL ghép input. Không sửa SecurityConfig/ErrorCode, không log văn bản, không fallback của T-14. Chỉ stage từng file được duyệt; một conventional commit và Draft PR, không merge/rebase/force-push.

## 2026-11-06 — Leader duyệt 014/T-12, kèm chỉnh

- Fetch origin/main `bcf89cb`, tạo nhánh `spec/014-t12-flashcard-review` và worktree `CNHSK-014-t12` từ base này. Chỉ triển khai T-12 theo duyệt, không rebase/merge hoặc đụng thay đổi riêng ở worktree gốc.
- Allowlist tối đa 20 file (contract chỉ đổi cho ahead sai kiểu):
  1. `src/main/java/com/cnhsk/learning/library/FlashcardReviewService.java`
  2. `src/main/java/com/cnhsk/learning/library/FlashcardReviewRepository.java`
  3. `src/main/java/com/cnhsk/learning/library/web/FlashcardReviewController.java`
  4. `src/main/java/com/cnhsk/learning/library/web/FlashcardReviewWrite.java`
  5. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
  6. `src/main/java/com/cnhsk/learning/library/LibraryRateLimiter.java`
  7. `src/main/java/com/cnhsk/learning/library/LibraryConfig.java`
  8. `src/main/java/com/cnhsk/shared/config/SecurityConfig.java`
  9. `src/main/resources/application.yaml`
  10. `src/test/java/com/cnhsk/learning/library/FlashcardReviewIntegrationTest.java`
  11. `src/test/java/com/cnhsk/learning/library/FlashcardReviewConcurrencyIntegrationTest.java`
  12. `src/test/java/com/cnhsk/learning/library/FlashcardReviewQueryCountIntegrationTest.java`
  13. `src/test/java/com/cnhsk/shared/config/FlashcardReviewSecurityConfigIntegrationTest.java`
  14. `src/test/java/com/cnhsk/learning/library/LibraryRateLimiterTest.java`
  15. `src/test/java/com/cnhsk/learning/library/LibraryConfigTest.java`
  16. `specs/014-library/contracts/openapi-library.yaml`
  17. `specs/014-library/questions.md`
  18. `specs/014-library/tasks.md`
  19. `src/main/java/com/cnhsk/learning/review/FsrsScheduler.java`
  20. `src/test/java/com/cnhsk/learning/review/FsrsSchedulerTest.java`
- Q-T12-01: base chưa có bean FsrsScheduler. Service dùng ObjectProvider: inject bean nếu đã có; nếu chưa thì tạo từ FsrsConfig.settings(), không đăng ký bean mới. Không sửa bất kỳ file 011 nào ngoài đúng cặp FsrsScheduler/FsrsSchedulerTest được leader bổ sung.
- Q-T12-02: repository riêng dùng native query, không thêm entity và không sửa LexemeSchedule/repository của 011. READ_COMMITTED, advisory library:decks:<userId> → kiểm và khoá bộ/thẻ FOR UPDATE OF c,i → Clock lấy một lần (độ chính xác microsecond của DB) → INSERT FLASHCARD ON CONFLICT DO NOTHING RETURNING → SELECT đúng khoá user_id/FLASHCARD/cardId FOR UPDATE → UPDATE chỉ stability, difficulty, next_review_at, review_count+1, lapse_count, last_seen_at, updated_at. INSERT có kết quả thì first(), không thì review(Memory từ DB); không suy ra lần đầu từ review_count. Không UPDATE mastery, không attempt_items, không event mastery.
- Q-T12-03: due luôn JOIN bộ/thẻ chưa xoá, đúng chủ và kind; progress của thẻ/bộ đã xoá vẫn giữ trong DB nhưng không ảnh hưởng danh sách. Thẻ mới chỉ khi chưa có dòng FLASHCARD của người gọi. SQL due gồm đúng 2 SELECT: sở hữu bộ và CTE lấy tối đa 100; quá hạn lâu nhất trước, rồi thẻ mới theo order_index/id. ahead=true chỉ lấy tương lai khi toàn bộ tập đến hạn/mới rỗng, không bù đủ 100. Không dùng session-max-items=50 của 011.
- Q-T12-04 (leader chỉnh): validate body hoàn tất trước khi gọi limiter, rồi mới kiểm sở hữu. 403 nghiệp vụ tính một lần, mọi 422 body không tính; 401/sai role/CSRF bị chặn trước controller không tính. Không ModelAttribute. DTO validate JSON và rating trước limiter, dùng ApiException VALIDATION_FAILED hiện có cho thiếu/sai rating hoặc JSON không hợp lệ; không thêm exception handler cục bộ, không sửa GlobalExceptionHandler.
- Rate limit riêng library:flashcards:review:user:<userId>:yyyyMMddHH, cửa sổ giờ UTC, cnhsk.library.flashcard-reviews-per-hour: 500 bắt buộc và dương lúc khởi động. Lượt 501 trả RATE_LIMITED + retryAfterSeconds làm tròn lên, không thêm Retry-After. Redis lỗi giữ fail-open và WARN không kèm thông tin kết nối như limiter hiện có. Matcher chỉ thêm một dòng LEARNER /api/flashcards/**; due dùng matcher bộ thẻ hiện có.
- Theo cách dictionary/notes hiện xử lý query param bằng chuỗi và kiểm thủ công, ahead chỉ nhận true/false, thiếu là false; sai kiểu trả 422 VALIDATION_FAILED. Contract chỉ thêm response 422 cho GET /due, không thêm header Retry-After hoặc sửa response chung.
- Leader duyệt sửa FsrsScheduler: cap stability ở S_MAX=36500 trong hàm result dùng chung cho first/review, sau tính interval để giữ công thức/giới hạn ngày hiện có. Giữ guard stability không hữu hạn/không dương, không clamp trong repository, không migration. first(AGAIN) lapse=0 theo 011; AGAIN tăng lapse kiểm ở lượt sau. Thêm test first vượt trần và chuỗi EASY cùng ngày đạt 36500; integration kiểm ghi DB thành công, không 500.
- Gate chọn lọc toàn bộ test library + FsrsSchedulerTest/FsrsConfigTest và security mới; sau đó full mvnw.cmd -B verify (Java 17, DEBUG=false, Docker). Báo stability/2 SELECT/full verify/JaCoCo LINE; giữ assertion và ngưỡng test cũ. Chỉ ngoại lệ môi trường được leader duyệt (FR023, NFR_P04, LoginAttempt PEXPIRETIME/CapturedOutput, QuestionAdminControllerIntegrationTest.NFR_P02 chỉ p95) được báo tên/số đo và vẫn mở PR. Không skip/hạ ngưỡng. Commit từng file cụ thể, một conventional commit, Draft PR; fetch/báo thay đổi file dùng chung, không merge/rebase/force-push.
- Chọn lọc bản cuối PASS 373/373, 0 failure/error/skip: FsrsSchedulerTest 42/42 (giữ toàn bộ test 011 cũ), FsrsConfigTest 36/36; review integration 24, concurrency 5, query-count 4, security 7 đều PASS. Chuỗi EASY cùng ngày ghi DB stability=36500, interval 365, 30 lượt không 500; hai lượt đầu đồng thời một dòng/count=2. Due đo đúng 2 SELECT với 0/1/120/500 thẻ và khi fallback ahead; ID thiếu 1 SELECT, sai định dạng 0. ahead sai kiểu thực tế 422 nên contract chỉ thêm đúng response 422 cho due. Body sai/thiếu/malformed/trailing JSON đều 422 không tăng counter; 403 nghiệp vụ tính một lần, lượt 501 là 429 không Retry-After.
- Full `mvnw.cmd -B verify` PASS: 3.799 test, 0 failure/error/skip, 17 phút 38 giây; Java 17, DEBUG=false, Docker. JaCoCo LINE 10.881/11.249 = 96,7286%; library và web 798/808, review 146/158. NFR-P04 sổ tay 1.000 ghi chú p95 93,108 ms, giữ nguyên ngưỡng 200 ms. Không dùng ngoại lệ môi trường; đánh ✅ T-12, tổng 11/15 theo duyệt.
- Fetch cuối trước PR: origin/main vẫn `bcf89cb`, không file nào trong 20 file allowlist đổi so với base. Self-check PASS: đúng 20 file, diff sạch, UTF-8 không BOM, không thêm dấu việc chưa làm trong code/dòng thêm; chỉ hai file FSRS được sửa trong learning/review. Không dependency mới, không SQL nối input người dùng hoặc truy vấn xuyên module; không log nội dung riêng/credential. Không sửa ErrorCode, GlobalExceptionHandler, LexemeSchedule/repository, migration, FE hoặc cấu hình credentials; không thêm bean FSRS, header hay handler cục bộ. Giữ assertion/ngưỡng test cũ, commit/push đúng nhánh đã duyệt; không merge/rebase/force-push.

## 2026-11-05 — Leader duyệt 014/T-11

- PR #103/T-10 đã merge tại `5530c76`; đã fetch và tạo nhánh `spec/014-t11`, worktree `CNHSK-014-t11` từ đúng origin/main này. Base có V6 `mastery_progress`, T-09/T-10 và tổng 9/15; code dùng lại của T-10 khớp kế hoạch. Không rebase hoặc sửa worktree gốc.
- Allowlist đúng 11 file:
  1. `src/main/java/com/cnhsk/learning/library/CardService.java`
  2. `src/main/java/com/cnhsk/learning/library/CardRepository.java`
  3. `src/main/java/com/cnhsk/learning/library/web/CardWrite.java`
  4. `src/main/java/com/cnhsk/learning/library/web/DeckController.java`
  5. `src/main/java/com/cnhsk/shared/exception/ErrorCode.java`
  6. `src/test/java/com/cnhsk/learning/library/CardIntegrationTest.java`
  7. `src/test/java/com/cnhsk/learning/library/CardConcurrencyIntegrationTest.java`
  8. `src/test/java/com/cnhsk/shared/config/DeckSecurityConfigIntegrationTest.java`
  9. `src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java`
  10. `specs/014-library/tasks.md`
  11. `specs/014-library/questions.md`
- Ba endpoint theo contract: POST `/{id}/cards` trả 201 Card; PATCH `/{id}/cards/{cardId}` trả 200 Card; DELETE trả 204. Mọi ghi READ_COMMITTED dưới `library:decks:<userId>` của T-10, lấy now một lần từ Clock sau khoá; khoá dòng bộ, sửa/xoá khoá thêm dòng thẻ. Bộ/thẻ sai ID, không tồn tại, khác chủ, khác loại, sai bộ hoặc đã xoá trả 403 COLLECTION_NOT_OWNED; không tự tạo bộ.
- Thứ tự POST: quyền sở hữu 403 → kiểm dữ liệu 422 → lexeme tồn tại 404 LEXEME_NOT_FOUND → trùng 409 CARD_DUPLICATE → hạn mức 422 LIBRARY_LIMIT_REACHED → ghi. Kiểm dữ liệu nghiệp vụ trong service sau kiểm quyền; DTO giữ hiện diện và kiểu JSON để phân biệt trường bị bỏ qua với null, không ép số thành chuỗi hoặc số thực thành ID nguyên.
- POST chọn đúng một dạng `{lexemeId}` hoặc `{front, back}`; trộn trường của cả hai dạng trả 422 (kể cả trường gửi null). Tự nhập: front 1–200, back 1–2.000 Unicode code point, không rỗng/toàn khoảng trắng/NUL; lưu nguyên nội dung, kể cả HTML dưới dạng văn bản. Lexeme tạo snapshot text/pinyin/meaningVi đúng T-10, không tạo user_progress; order_index đặt sau thẻ cũ, không sắp lại thứ tự.
- PATCH sửa được từng mặt riêng; trường thiếu giữ nguyên, gửi null/rỗng/toàn khoảng trắng/sai kiểu hoặc quá giới hạn trả 422. Cần ít nhất một mặt để sửa; lexemeId gửi trong PATCH bị bỏ qua, không đổi liên kết, order_index, created_at hoặc lịch ôn; updated_at dùng now. Bộ đủ 500 vẫn sửa được. DELETE chỉ đặt deleted_at của thẻ, giữ toàn bộ lịch ôn và các trường khác; xoá lại thẻ không còn hợp lệ trả 403.
- Trùng với thẻ chưa xoá cùng bộ nếu cùng lexemeId hoặc front sau trim/gộp khoảng trắng Unicode (`Character.isWhitespace` hoặc `isSpaceChar`) thành một dấu cách. Giữ hoa/thường, dấu và dạng Unicode; không thêm NFC. Chỉ chuẩn hoá khoá so sánh, không đổi nội dung lưu. PATCH loại chính thẻ đang sửa khỏi phép so. Thẻ đã xoá không tính trùng/hạn mức; hạn mức dùng max-cards của T-10, trả details{kind: CARDS, limit: 500}.
- Giữ nguyên uq_coll_lex của V1. INSERT dùng đúng ON CONFLICT (collection_id, lexeme_id) WHERE lexeme_id IS NOT NULL AND deleted_at IS NULL DO NOTHING RETURNING id; không có dòng trả về chuyển thành 409, không catch lỗi unique rồi tiếp tục trong transaction đã hỏng. Toàn bộ kiểm và ghi dưới cùng khoá/transaction, lỗi rollback hết.
- CARD_DUPLICATE chỉ thêm ngay trước INTERNAL_ERROR, giữ thứ tự mã khác. Contract không yêu cầu details cho lỗi trùng nên không sửa GlobalExceptionHandler; test lỗi trùng không có details và hạn mức có details. Không sửa SecurityConfig, cấu hình, schema, migration, FE hoặc làm T-12.
- Test PostgreSQL thật: tranh suất 499/500, cùng lexeme/front, hai PATCH tranh front, Clock sau chờ advisory, PATCH/DELETE chờ khoá dòng, người khác không chung khoá. Test chèn trùng trực tiếp bằng JDBC sau precheck trong transaction rồi thực thi INSERT thật để kiểm uq_coll_lex/ON CONFLICT trả 409 và rollback. Giữ nguyên assertion/ngưỡng T-08/T-10. Snapshot lịch V6 dùng row_to_json(p)::text, chuyển mảng thành giá trị JSON, không so PgArray theo identity; có evidence_item_ids không rỗng.
- Gate chọn lọc CardIntegrationTest, CardConcurrencyIntegrationTest, DeckIntegrationTest, DeckConcurrencyIntegrationTest, DeckQueryCountIntegrationTest, DeckSecurityConfigIntegrationTest, GlobalExceptionHandlerTest, LibraryConfigTest; sau đó full mvnw.cmd -B verify (Java 17, DEBUG=false, Docker). Kiểm diff/BOM/dấu việc chưa làm/11 file. Một conventional commit, push, Draft PR; fetch trước PR, báo nếu file dùng chung đổi; không merge/rebase. Chỉ ngoại lệ môi trường đã biết theo duyệt leader được ghi số đo và vẫn mở PR, không hạ ngưỡng hoặc skip.
- Chọn lọc PASS 134/134, 0 failure/error/skip. Test quan sát chờ khoá dùng pg_locks trực tiếp (transactionid/advisory), tránh snapshot pg_stat_activity trong transaction; không thay đổi assertion/ngưỡng có sẵn. Hồi quy DeckIntegrationTest trên V6 và kiểm 1/2 SELECT của T-10 đều PASS.
- Full `mvnw.cmd -B verify` PASS trên base có V6: 3.179 test, 0 failure/error/skip, 13 phút 29 giây; Java 17, DEBUG=false, Docker. JaCoCo LINE 9.085/9.359 = 97,0723%; library và web 705/712. NFR-P04 sổ tay 1.000 ghi chú p95 51,960 ms, giữ nguyên ngưỡng 200 ms. Không dùng ngoại lệ môi trường, không hạ ngưỡng hoặc skip; đánh ✅ T-11 và tổng 10/15 theo duyệt.
- Fetch trước PR: origin/main vẫn `5530c76`, không file nào trong allowlist đổi so với base. Không rebase/merge main. Self-check PASS: đúng 11 file, diff sạch, UTF-8 không BOM, không thêm dấu việc chưa làm trong code hoặc dòng thêm; những nhắc tên bước kiểm trong tài liệu cũ giữ nguyên. Không thêm dependency, không truy vấn xuyên module, AuthApi cung cấp người gọi và SQL bind tham số. Không sửa handler/security/config/schema/migration/FE hoặc code T-12; enum chỉ thêm đúng CARD_DUPLICATE, giữ nguyên assertion/ngưỡng T-08/T-10. Không log nội dung riêng hoặc credentials, chỉ commit/push nhánh đã duyệt.

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

## 2026-11-05 — Leader duyệt 014/T-05

- Base `origin/main` `211e7e3` (#99/T-04 đã merge), nhánh `spec/014-t05`, worktree `CNHSK-014-t05`. Allowlist đúng 15 file theo Shadow Plan: helper/validation, ba View, hai page và test, preview, screens, route/transport test, tasks/questions. Không sửa backend, shared/app, API implementation, cấu hình hoặc dependency. Không rebase, một conventional commit, Draft PR, leader merge.
- SCR-028 tạo bộ trống hoặc từ chủ đề; dùng lại getTopics của 003, chỉ gửi topicId, không chép từ/lịch ôn ở FE. Tên 1–100 ký tự, cho trùng; mô tả tối đa 500. SCR-029 thêm/sửa mặt trước 1–200, mặt sau 1–2.000; đếm Unicode code point, giữ nội dung thẻ nguyên văn. PATCH thẻ chỉ gửi front/back, không ghi đè lexemeId của thẻ từ từ điển.
- Quota 50 bộ/500 thẻ để backend kiểm; FE chỉ hiện giới hạn và details.kind/limit của LIBRARY_LIMIT_REACHED. Không thêm request kiểm quota. Hai danh sách dùng mảng contract và phân trang client 20 mục/trang, giữ thứ tự backend; clamp trang khi số bản ghi giảm.
- Ôn tại SCR-029, không điều hướng SCR-013: lấy tối đa 100 thẻ theo thứ tự server, lật rồi AGAIN/HARD/GOOD/EASY. Chỉ tăng vị trí/giảm số còn lại sau API thành công; pending chặn gửi lặp, lỗi giữ nguyên thẻ và mặt đã lật, không tự gửi lại rating. Người dùng chọn mức để thử lại. Không có thẻ đến hạn thì cho ahead=true. Hiện nextReviewAt/intervalDays từ server và câu "Ôn thẻ chỉ đổi lịch của thẻ, không đổi mức thành thạo"; FE không tính FSRS/mastery.
- Xoá bộ/thẻ dùng ConfirmPanel hiện có, huỷ trả focus về trigger; không cập nhật trước thành công, thất bại giữ dữ liệu và retry cần xác nhận lại. Giữ form khi CARD_DUPLICATE, LIBRARY_LIMIT_REACHED, TOPIC_NOT_FOUND, LEXEME_NOT_FOUND, COLLECTION_NOT_OWNED hoặc lỗi mạng; RATE_LIMITED giữ thẻ và hiện retryAfterSeconds nếu có.
- Page gọi API, View chỉ nhận props. Preview riêng có loading/empty/error/ready cho danh sách, chi tiết và ôn. Huỷ request cũ; bỏ kết quả muộn; xoá dữ liệu riêng khi đổi tài khoản/bộ. Nội dung luôn là React text node. Không window.confirm, HTML injection, eslint-disable, import preview trong page hoặc tính năng công khai.
- Tài liệu screen-fields cũ SCR-028 còn nhắc preset HSK và không chốt giới hạn mô tả; SCR-029 còn chuyển ôn sang SCR-013, chưa chốt độ dài mặt sau và có thao tác chia sẻ/sao chép/báo cáo công khai. T-05 theo spec 014 FR-013…018/027/028 và OpenAPI: nguồn chủ đề 003, mô tả 500, mặt sau 2.000, ôn inline, công khai là V2.
- Đối chiếu contract: PATCH bộ trả DeckSummary, không có cards. Annotation cũ của api/flashcards.ts khai Promise<DeckDetail>; T-05 chỉ đọc các trường summary rồi tải lại GET chi tiết, có regression test trả summary thật và không giả mảng cards. Sửa declaration client thuộc đợt đối chiếu T-15 ngoài allowlist T-05.
- Backend T-10…T-12 chưa có; FE dùng contract/client sẵn có, test mock và fixture tự soạn. Nghiệm thu với backend thật tại T-15; T-12 còn phụ thuộc FsrsScheduler của 011.
- Gate: npm ci PASS; toàn bộ Vitest với maxWorkers=2/minWorkers=2 PASS 149 file, 1.996 test, không failure/skip; lint 0 error, chỉ warning LevelBadge có sẵn; production build PASS. Kiểm diff/BOM/TODO và đúng allowlist 15 file. Trên Windows dùng npm.cmd/npx.cmd do script shim PowerShell bị chặn.
- Fetch trước PR: origin/main vẫn `211e7e3`, không đổi so với base; không có thay đổi file dùng chung. T-09 bàn giao riêng tại #100, không đưa code T-09 vào T-05, không rebase. Sau T-05 chỉ soạn Shadow Plan T-10 rồi chờ leader duyệt.

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

# Questions and decisions — Spec 006

## Chốt T-05 ngày 2026-11-01

Trạng thái: **Chốt — leader 2026-11-01**.

- Nhánh T-05 là `spec/006-hanzi-t05` từ `978bc5d`; không fetch, merge, rebase hoặc pull main sau khi tạo nhánh.
- Phạm vi gồm đúng `WritingChallengeHandler.java` (mới), `WritingChallengeIntegrationTest.java`, `PracticeService.java`, `PracticeSourceHandler.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest.java`, `contracts/openapi-hanzi.yaml`, `tasks.md` (chỉ T-05) và `questions.md`. Không sửa `openapi-practice-vocab.yaml`, `PracticeSource`, controller, SecurityConfig, PracticeApi hoặc migration.
- Nộp lại dùng `SESSION_ALREADY_SUBMITTED` 409 có sẵn; `CHALLENGE_ALREADY_SUBMITTED` chỉ là tên trong UC. `ITEM_NOT_IN_SESSION` tương ứng `CHARACTER_NOT_IN_CHALLENGE`.
- Thêm đúng hook mặc định `validateFinish(List<Answer> answers) {}` vào `PracticeSourceHandler`. `PracticeService` gọi hook của handler đúng source trong `finishInTransaction`, ngay sau kiểm `writableFailure` và trước vòng lặp answers. Điều này giữ lỗi hết hạn và nộp lại ưu tiên trước kiểm duplicate. `WritingChallengeHandler` trả 400 `RESULT_COUNT_MISMATCH` nếu no trùng. Quá 3 câu `FAILED` cũng trả 400 mã này trong `onFinish`; thêm mã trước `INTERNAL_ERROR` và đúng một kỳ vọng tương ứng, giữ `containsExactly`.
- `strokeCount` null trong snapshot là vi phạm invariant; để lỗi nội bộ thành 500, không thêm mã lỗi riêng. Thử thách chỉ do T-04 tạo; không cần test trường hợp này.
- Giới hạn 20 giây mỗi chữ và 200 giây toàn lượt thuộc FE theo FR-034. Backend giữ expiry 210 giây sẵn có; không thêm giới hạn backend.
- `skipped=true` hoặc `audioFailed=true` trả 422 `VALIDATION_FAILED`; không ánh xạ thành `FAILED` hay `INCOMPLETE`. `validateAnswer` chấp nhận `COMPLETED`, `FAILED`, `INCOMPLETE`; answer null chỉ dùng cho câu thiếu do engine tạo và phải được chấp nhận.
- Metadata mọi câu có đủ khóa FR-017: `mode='CHALLENGE'`, `completed` theo answer, `strokeCount` từ snapshot, `coefficientProposal` là BigDecimal 1.20 khi hoàn thành và 0.00 khi khác, `usedAnimation=false`, `previewCount=0`, `previewMs=0`, `replayCount=0`, `repeatWithin10Min=false`, `accuracy` và `strokeMisses` từ clientMeta nếu hợp lệ, nếu thiếu thì null. `accuracy` ngoài 0…1, `strokeMisses` ngoài 0…strokeCount×10 hoặc sai kiểu trả 422 `VALIDATION_FAILED`. Chỉ lưu các khóa trên, bỏ trường client gửi thêm.
- `onFinish` kiểm số `FAILED` trước, rồi duration mọi `COMPLETED` theo `prompt.strokeCount × 200`; duration sai trả 400 `IMPOSSIBLE_DURATION`. Thành công trả `extra` gồm `completedChars`, `failedChars`, `incompleteChars`, `total=10`, `finishedReason`; thứ tự reason là 3 lỗi → `MAX_MISTAKES`, đủ complete+failed 10 → `ALL_DONE`, còn lại → `TIME_UP`. Score và maxScore vẫn null.
- Tất cả ca 400 phải xác nhận rollback: session vẫn `IN_PROGRESS`, không có `attempt_items` hoặc `study_events`; sau đó cùng lượt finish hợp lệ được. Session đã `SUBMITTED` và request có no trùng vẫn trả 409. Thêm test 422 cho skip/audioFailed, answer lạ và dữ liệu accuracy/strokeMisses sai; ba lỗi `FAILED` + bảy `COMPLETED` ưu tiên `MAX_MISTAKES`; kết quả hoàn thành thử thách tham gia xếp hạng T-04, câu thiếu không tham gia.
- Fixture mới dùng dải CJK Extension A U+4B00–U+4CFF, cấp tuần tự và báo lỗi nếu hết dải; không dùng U+5000–U+50FF.
- Trong `openapi-hanzi.yaml`, chỉ sửa mô tả 422 của `POST /api/writing/challenges`: `have` và `need` ở cấp ngoài cùng ErrorResponse.
- Gate: test chọn lọc gồm `WritingChallengeIntegrationTest`, `GlobalExceptionHandlerTest`, `PracticeServiceTest`; full `mvnw verify` có Docker, 0 failure/error/skip (ngoại lệ FR023 theo quy định chung), coverage tổng ≥80%, Practice ≥90%, OpenAPI consistency, `git diff --check`, BOM/TODO. Một commit T-05, file cụ thể, push và Draft PR riêng; không tự merge.

## Chốt T-06 ngày 2026-11-01

Trạng thái: **Chốt — leader 2026-11-01**.

- Phạm vi T-06 gồm đúng `HanziCanvas.tsx`, `HanziCanvas.test.tsx`, `cnhsk-web/package.json`, `cnhsk-web/package-lock.json`, `cnhsk-web/.env.example`, cập nhật riêng T-06 trong `tasks.md`, và ghi quyết định trong `questions.md`.
- Chỉ thêm `hanzi-writer@3.7.3`; không cài `hanzi-writer-data`. Dữ liệu tải từ URL gốc `VITE_HANZI_DATA_URL=https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1`; loader nối `/{mã-hóa-chữ}.json`, bỏ dấu `/` cuối, biến rỗng dùng URL ghim trên.
- T-06 chỉ làm nền canvas: tải dữ liệu nét, báo lỗi dữ liệu, khung 300×300 co theo màn hình nhỏ và tối thiểu 240 px, chống kết quả tải cũ ghi đè chữ mới, không gọi callback sau unmount và dọn writer. Không triển khai STROKE/RECALL/DICTATION hay thử thách.
- Không sửa `HanziCanvasSlot.tsx`, `CharacterPracticeView.tsx`, `WritingChallengeView.tsx`, hoặc `Scr014CharacterWritingPage.test.tsx`; thay đổi tích hợp thuộc T-08/T-09. AT-23 chưa hoàn tất ở T-06; phần còn lại thuộc T-08/T-09.
- T-06 chạy trong worktree riêng. Lockfile sinh bằng `npx --yes npm@11.13.0 install --package-lock-only --ignore-scripts --save-exact hanzi-writer@3.7.3`; chỉ chấp nhận diff `package.json +1`, `package-lock.json +7`, không có dòng xóa. Sau đó chạy `npm ci` và xác nhận diff không đổi.
- Test mock `hanzi-writer` trong `HanziCanvas.test.tsx`, bao gồm URL/encoding, bỏ `/` cuối, fallback, lỗi HTTP/mạng/JSON, đổi chữ trong lúc tải, unmount, resize và kích thước tối thiểu 240 px.
- Gate FE: `npm run lint`, `npm run typecheck`, `npx vitest run --maxWorkers=2 --minWorkers=1`, `npm run build`, `git diff --check`, kiểm BOM/TODO. Một commit T-06, file cụ thể, push và Draft PR riêng.

## Chốt T-04 ngày 2026-11-01

Trạng thái: **Chốt — leader 2026-11-01**.

- Phạm vi T-04 gồm `WritingChallengeService.java`, `WritingChallengeController.java`, `CharacterQueryRepository.java`, `ServedItem.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest.java`, `WritingChallengeIntegrationTest.java`, `contracts/openapi-hanzi.yaml`, `tasks.md` (chỉ T-04) và `questions.md`. Nếu `ServedItemView` cần chép trường prompt mới thì mới được sửa; hiện tại nó dùng trực tiếp `ServedItem.Prompt`.
- Thêm `Integer strokeCount` cuối `ServedItem.Prompt`, giữ constructor cũ 9 tham số; các snapshot JSON cũ thiếu trường phải đọc được. Security chỉ thêm matcher POST `/api/writing/challenges` cho LEARNER; ErrorCode chỉ thêm `NOT_ENOUGH_CHARACTERS` ngay trước `INTERNAL_ERROR`, cùng đúng một kỳ vọng trong `GlobalExceptionHandlerTest`. Contract chỉ thêm 403 cho endpoint này và mô tả lỗi 422 `have`/`need=10`.
- Eligibility chỉ gồm lexeme `CHAR`, có `stroke_count`, có attempt của user với `ref_type=CHAR_WRITING`, `status=SUBMITTED`, `ref_id=lexemeId`, item `WRITING_RESULT`, `answer='COMPLETED'`, `meta->>'mode'='STROKE'`. Đây là vị trí ngoại lệ JSONB thứ ba; luôn lọc cột quan hệ trước. Không dùng JSONB trong xếp hạng.
- Xếp hạng qua một truy vấn nhóm toàn bộ ứng viên theo `max(attempt_items.answered_at)`, tăng dần, gồm attempt `CHAR_WRITING` và `WRITING_CHALLENGE`, `status=SUBMITTED`, `item_type=WRITING_RESULT`, `answer IS NOT NULL`; hòa được xáo bằng bean `practiceChoiceRandom`. Không gọi truy vấn riêng từng chữ.
- Trong transaction, thứ tự bắt buộc là advisory lock theo key `WRITING_CHALLENGE:{userId}` → `findActive` → eligibility/ranking → lỗi 422 có `have`, `need=10` hoặc `PracticeService.start`. Lượt đang hoạt động trả 200 và không tiêu hạn mức; lượt mới trả 201 và dùng hạn mức practice:start 120/giờ; lượt hết hạn không được tiếp tục.
- Không tạo migration, không sửa `WritingChallengeHandler`, practice handler/API hoặc file ngoài phạm vi trên. Test tích hợp gọi HTTP với PostgreSQL Testcontainers thật, không mock repository; gồm đủ AT-17/AT-18, concurrency (chỉ một lượt mới), session tiếp tục/hết hạn, rate limit, quyền truy cập, xếp hạng, không lộ đáp án và tương thích snapshot cũ.
- Gate: test chọn lọc rồi `./mvnw verify` với Docker; 0 failure/error/skip (ngoại lệ FR023 theo quy định chung), coverage tổng ≥80%, Practice ≥90%, `git diff --check`, kiểm BOM/TODO và OpenAPI. Một commit T-04, file cụ thể, không gồm `package-lock.json`, push và Draft PR riêng.

## Quyết định leader ngày 2026-10-31

| # | Quyết định | Trạng thái |
|---|---|---|
| Q-T07-01 | T-07 hoàn thiện trực tiếp trên `cnhsk-web/src/features/hanzi/pages/Scr015CharacterRecognitionPage.tsx`; không tạo `RecognitionPage.tsx`. | **Chốt — leader 2026-10-31** |
| Q-T08/09-01 | T-08/T-09 đối chiếu và hoàn thiện code hiện có; chỉ giữ phần có test chứng minh đạt FR/AT. Canvas, 3 chế độ viết và thử thách phải triển khai thật; file `.preview` chỉ phục vụ xem trước. | **Chốt — leader 2026-10-31** |
| Q-SCOPE-01 | File dùng chung hoặc ngoài cột File phải liệt kê và xin duyệt trước khi sửa. Riêng `ChoiceItemFactory` có 3 thay đổi đã được leader duyệt cho T-02. | **Chốt — leader 2026-10-31** |
| Q-AC09-01 | Ngoại lệ AC-09 được leader chấp nhận, chỉ cho `meta->>'mode'` của `WRITING_RESULT`. Chỉ áp dụng cho `recallUnlocked` (T-01), `repeatWithin10Min` (T-03), và tập chữ đủ điều kiện thử thách (T-04); luôn lọc trước bằng cột thật. Hoàn thành xác định qua `attempt_items.answer = 'COMPLETED'`; truy vấn đặt trong repository `learning.hanzi`. Không thêm index meta, thống kê/xếp hạng/báo cáo theo meta. | **Chốt — leader 2026-10-31** |
| Q-T01-REPO | Truy vấn chữ và bằng chứng viết đặt trong `learning.hanzi.CharacterQueryRepository`; hoàn nguyên `content.repository.LexemeRepository` về main, không sửa thêm file module `content`. Repository Hanzi giữ phần kiểm tra `recallUnlocked` để các task viết/thử thách dùng lại. | **Chốt — leader 2026-10-31** |
| Q-T01-OPENAPI | Hai endpoint `GET /api/characters` và `GET /api/characters/{id}` công bố response `403` dùng schema `ErrorResponse`, khớp quyền LEARNER trong SecurityConfig. Test phân quyền phải gọi endpoint qua integration/security test. | **Chốt — leader 2026-10-31** |
| Q-T01-TEST | Chỉ sửa assertion trong `PracticeCoreTest.FR007_studyCardsCannotBeGradedWhileQuestionTypesCan` để `STUDY_CARD` và `WRITING_RESULT` là hai loại không chấm điểm; giữ nguyên tên test và không sửa test khác. | **Chốt — leader 2026-10-31** |

## Chốt T-02 ngày 2026-10-31

Trạng thái các quyết định dưới đây: **Chốt — leader 2026-10-31**.

- Nhánh mới `spec/006-hanzi-t02` phải xuất phát từ `origin/main` mới nhất, đã có T-01. Chỉ fetch/pull để tạo nhánh; sau đó không merge, rebase hoặc pull main.
- Phần triển khai được duyệt gồm `RecognitionService`, controller nhận diện, truy vấn lựa chọn/lịch sử trong `CharacterQueryRepository`, đúng một matcher POST cho LEARNER trong `SecurityConfig`, response 403 chỉ cho endpoint nhận diện trong OpenAPI, mở rộng `ChoiceItemFactory` và test mới. Cập nhật `tasks.md` và file này. Không thêm ErrorCode, handler cho `CHAR_RECOGNITION`, migration hoặc sửa bộ máy practice.
- Request nhận `mode` (`CHAR_TO_MEANING`/`AUDIO_TO_CHAR`), `hskLevel` 1–9, `count` 5–20 mặc định 10; input sai hoặc thiếu trường bắt buộc trả 422 `VALIDATION_FAILED`.
- Chỉ chọn lexeme `CHAR` cùng cấp, có nghĩa; chế độ audio chỉ chọn chữ có audio. Ưu tiên lần trả lời sai mới nhất, rồi chữ chưa từng trả lời, rồi phần còn lại ngẫu nhiên. Lịch sử theo user + lexeme + `ref_type=CHAR_RECOGNITION`, cả hai chế độ và mọi trạng thái lượt; `skipped`/`audioFailed` không phải câu sai. Chỉ dùng cột quan hệ, không đọc JSONB.
- Duyệt ứng viên theo thứ tự ưu tiên và chỉ giữ chữ mà `ChoiceItemFactory` sinh đủ bốn phương án; bỏ chữ thiếu nhiễu rồi tiếp tục đến khi đủ count hoặc hết ứng viên. Có thể trả ít câu hơn count. Không có câu nào thì trả 200 `{session:null, reason:"NO_CHARACTERS"}`.
- `CHAR_MEANING_CHOICE` dùng skill `CHARACTER`, prompt chỉ lộ chữ và rules `{minDurationMs:800,maxReplays:3,hasAudio:false}`. `AUDIO_CHAR_CHOICE` dùng skill `LISTENING`, prompt chỉ có audioUrl, không lộ chữ và rules `{minDurationMs:800,maxReplays:3,hasAudio:true}`. Cả hai luôn có bốn options, `refId=null`, đáp án không xuất hiện trong response. Explanation là `<chữ> (<pinyin>): <nghĩa>`, bỏ phần pinyin nếu không có.
- Khi thành công trả 201 `SessionView` trực tiếp, không bọc thêm; mỗi POST hợp lệ tạo lượt mới, không cần lock. FE khôi phục qua endpoint active hiện có.
- `ChoiceItemFactory` chỉ nhận thêm hai loại câu ở bước kiểm loại; facts map `CHAR_MEANING_CHOICE` như `MEANING_CHOICE` và `AUDIO_CHAR_CHOICE` như `AUDIO_WORD_CHOICE` (so pinyin kèm thanh); thêm tầng 0 cùng radical/kind/cấp chỉ cho `CHAR_MEANING_CHOICE` khi chữ đích có radical, `mixed=false`. Không đổi chữ ký. Nếu factory ném lỗi với chữ có pinyin không đọc được thì dừng và báo leader, không tự sửa thêm.
- Test bắt buộc gọi API qua HTTP và PostgreSQL Testcontainers, không mock repository; gồm AT-03…AT-07, trường hợp chữ thiếu nhiễu được bù bằng ứng viên sau, active không có refId, phân quyền 403; chạy 100 lần ChoiceItemFactory trực tiếp cho 是/事 (không gọi API 100 lần). Gate: test chọn lọc, `mvnw verify` với 0 failure/error/skip, coverage tổng ≥80%, practice ≥90%, `git diff --check`, kiểm BOM/TODO và contract.

## Chốt T-03 ngày 2026-10-31

Trạng thái các quyết định dưới đây: **Chốt — leader 2026-10-31**.

- `PracticeService` được thêm đúng một phương thức public `@Transactional` `recordAndFinish(StartPracticeCommand)`. Phần tạo attempt được tách thành private method dùng chung. `start` giữ nguyên hành vi, thứ tự validation và `checkStart`; `recordAndFinish` chỉ nhận source không phải `DEFERRED`, có `preRecorded`, và mọi câu đều `gradable=false`; không gọi `checkStart`, tạo và ghi sẵn trong cùng transaction rồi gọi trực tiếp `finishInTransaction`. Nếu `FinishOutcome.failure()` khác null thì ném lỗi để rollback. Caller tự chịu trách nhiệm hạn mức. Không đổi `REQUIRES_NEW` hoặc public method khác. Test thêm source `IMMEDIATE` với `GRAMMAR_LESSON` và câu `STUDY_CARD` ungradable, xác nhận lượt `SUBMITTED`, score null.
- Tạo `WritingAttemptHandler` cho `CHAR_WRITING`: `enrichMeta` trả `answer.clientMeta()`, `onFinish` trả `Map.of()`. `WritingService` tự tính đủ metadata FR-017 và truyền qua `clientMeta`; không tin các giá trị tính toán từ client. Rules của câu: `minDurationMs=0`, `maxReplays=3`, `hasAudio` theo chữ có audio. `coefficientProposal` dùng `BigDecimal`, làm tròn 2 chữ số; DICTATION `replayCount=3` phải cho kết quả số `0.8`.
- Giới hạn 300 lượt/giờ thuộc riêng `WritingService`, qua `shared.security.RateLimiter.increment`, key UTC `writing:{userId}:{yyyyMMddHH}`. Chỉ đếm sau mọi validation và trước khi ghi; validation bị từ chối không đếm, lỗi DB sau increment không hoàn lại. Count >300 trả 429 `RATE_LIMITED` với `retryAfterSeconds`. Redis lỗi thì cho qua và log WARN, không log nội dung. `PracticeRateLimiter` không sửa. Thêm vào spec ghi rõ `CHAR_WRITING` chỉ tính giới hạn FR-019, không tính `practice:start` (E-R12).
- Validation theo đúng thứ tự: định dạng request → `CHARACTER_NOT_FOUND` 404 → FR-011 → FR-012 → FR-013 → FR-014 → FR-015 → hạn mức → ghi. JSON sai, thiếu trường bắt buộc, mode sai, id không dương, `strokeMisses` ngoài 0…`strokeCount*10`, `durationMs` ngoài miền, và số âm trả 422 `VALIDATION_FAILED`. `repeatWithin10Min` đặt trong `CharacterQueryRepository`, dùng cột `attempts.submitted_at` và `Clock`; đúng 10 phút hoặc lâu hơn là false. Truy vấn chỉ đọc `meta->>'mode'` sau khi lọc các cột quan hệ đã chốt và dùng `attempt_items.answer='COMPLETED'` để nhận diện hoàn thành.
- File được duyệt cho T-03: `WritingService.java`, `web/WritingController.java`, `WritingAttemptHandler.java`, `PracticeService.java` (đúng phần `recordAndFinish` nêu trên), `CharacterQueryRepository.java`, `SecurityConfig.java` (thêm đúng matcher POST `/api/writing/attempts` cho LEARNER), `ErrorCode.java` (8 mã FR-011…FR-014 trước `INTERNAL_ERROR`), `GlobalExceptionHandlerTest.java`, `WritingServiceIntegrationTest.java`, `PracticeServiceTest.java` (chỉ thêm test mới cho `recordAndFinish`, không sửa test cũ), `spec.md`, `tasks.md` (chỉ T-03), và file này. Không sửa `PracticeRateLimiter`, `PracticeSourceHandler`, `PracticeApi`, `openapi-hanzi.yaml`; mọi file khác cần dừng và xin duyệt.
- Test bắt buộc qua HTTP với PostgreSQL và Redis Testcontainers thật, không mock repository; đủ AT-08…AT-16, validation, rollback cả attempt/items/study_events khi finish handler lỗi, practice:start ở 120 vẫn cho POST writing thành công, writing counter 300 trả 429 không có attempt mới, và DICTATION replayCount=3 cho coefficient 0.8. Không gọi API 300 lần để đạt counter. `start` vẫn giữ giới hạn 120.
- Gate: test chọn lọc, `mvnw verify` có Docker với 0 failure/error/skip, coverage tổng ≥80%, Practice ≥90%, đối chiếu contract hiện có, `git diff --check`, kiểm BOM/TODO. Một commit T-03 với file cụ thể, không gồm `package-lock.json`, push branch và Draft PR; không merge nếu leader chưa báo xanh.

## Chốt T-07 ngày 2026-11-01

Trạng thái: **Chốt — leader 2026-11-01**.

- T-07 dùng đúng năm file: Scr015CharacterRecognitionPage.tsx, RecognitionView.tsx, Scr015CharacterRecognitionPage.test.tsx, tasks.md (chỉ T-07) và questions.md. Không thêm recognition.test.ts; không viết test HTTP 201 riêng vì apiRequest đã xử lý response.ok. Không sửa Practice/shared/app/backend/OpenAPI/lockfile.
- Nhánh T-07 tạo mới từ origin/main mới nhất trong worktree riêng; không dùng worktree T-03 cũ.
- Khi answer hoặc finish thất bại, RecognitionView ở trạng thái ready hiện role=alert cùng kiểu setup nhưng giữ câu hỏi. Không chuyển ErrorState/reload. Answer chọn lại được sau khi ChoiceQuestion bỏ chọn; finish có thể bấm lại “Câu tiếp” hoặc thao tác kết thúc. Alert biến mất khi thao tác sau thành công.
- Khi reload session IN_PROGRESS đã trả lời hết, tự finish đúng một lần. Nếu lỗi, giữ alert và chỉ finish lại khi người học bấm nút thử lại; luồng vừa trả lời câu cuối vẫn chờ người học bấm “Câu tiếp”.
- Khi sync nhận session SUBMITTED nhưng không có finishResult, dựng lại kết quả ngay trong page: score = số answer correct=true; maxScore = số answer counted=true; percent null nếu maxScore=0, ngược lại floor(score×100/maxScore). Render summary và nút “Luyện lượt mới”. CHAR_RECOGNITION có maxScoreFromServed=false.
- Nhãn HSK khi resume lấy từ session nếu có; thiếu thì ẩn, không dùng cấp đang chọn ở form. Không thay backend.
- Audio fixture không có prompt.text; FE không thêm phòng thủ để lọc trường này. AT-23 trong T-07 chỉ nghiệm thu SCR-015; phần viết/canvas/thử thách thuộc T-08/T-09.

## Chốt T-08 ngày 2026-11-01

Trạng thái: **Chốt — leader 2026-11-01**.

- Phạm vi gồm 9 file trong `cnhsk-web/src/features/hanzi/**` theo Shadow Plan, cùng `tasks.md` (chỉ T-08) và `questions.md`. Giữ nguyên `HanziCanvasSlot.tsx` và `WritingChallengeView.tsx` cho T-09. Không sửa `features/practice`, shared, app, backend, contract, dependency hoặc lockfile; cần file khác thì dừng xin duyệt.
- Khi POST timeout, mất mạng hoặc 5xx: hiện “Chưa xác nhận được kết quả”, không tự POST lại; người học có thể bấm “Gửi lại” để gửi đúng payload hoặc quay về danh sách. 4xx/429 không có nút gửi lại theo luồng này. Không dùng `sessionStorage`, không thêm backend. Attempt lặp trong 10 phút có `counted=false` theo backend.
- RECALL `previewMs` tính cả 5000 ms ban đầu cộng 3000 ms cho mỗi lần xem thêm; `previewCount` chỉ đếm lần xem thêm, tối đa 2. Dùng hằng số, không đo thời gian thực.
- `completed=false` chỉ gửi qua nút “Dừng” rõ ràng. Không POST khi rời route, unmount hoặc đóng tab. “Chưa nhớ” không gửi attempt.
- `animateCharacter()` của `hanzi-writer@3.7.3` hủy quiz hiện tại. Sau khi animate hoàn tất, chạy quiz lại từ nét hiện tại bằng `quizStartStrokeNum`; cộng dồn lỗi trước/sau animate và không reset đồng hồ. Khóa nút trong lúc animate. Đổi chữ, mode hoặc unmount trong lúc animate thì không khởi chạy quiz mới. Xác minh tên option trong d.ts trước khi triển khai; không có thì dừng báo leader.
- Payload giới hạn `strokeMisses = min(totalMistakes, strokeCount * 10)` để tuân miền backend; `accuracy` vẫn tính theo FR-031 từ tổng lỗi thật. `durationMs` dùng `clampDuration` theo SCR-015, miền 0…3.600.000.
- Không cho chạy hai quiz song song: hủy quiz cũ trước khi tạo quiz mới. `recorded`, `counted`, `recallUnlocked` chỉ lấy từ response; `MODE_LOCKED` tải lại chi tiết chữ.
- Test 429 dùng body JSON có `retryAfterSeconds` ở cấp ngoài cùng; xác nhận `ApiError.extra` nhận giá trị và UI hiện thời gian chờ, không tự gửi lại.
- `onDataError`: dừng quiz, không POST, chuyển chế độ xem với thông báo “Chữ này chưa có dữ liệu nét”. Giữ test cũ, chỉ đổi kỳ vọng Slot thành canvas. AT-23 chỉ nghiệm thu phần T-08.
- Browser thật bắt buộc thử chữ có dữ liệu nét thật: canvas, quiz STROKE, animate giữa chừng rồi viết tiếp đến hoàn tất, RECALL ẩn mẫu, DICTATION không lộ chữ/viền, resize 240–300 px và chặn request data để kiểm tra lỗi. Có thể dùng Vite harness tạm, không commit và xóa sau thử. Nếu backend không chạy được, ghi rõ chưa nối backend thật; không tự tạo dữ liệu hoặc chạy SQL.
- Gate FE: `npm ci`, lint, typecheck, Vitest với `--maxWorkers=2 --minWorkers=1`, build, `git diff --check`, BOM/TODO và xác nhận lockfile không đổi. Một commit T-08, push branch; người dùng tự tạo Draft PR. Không bắt đầu T-09.

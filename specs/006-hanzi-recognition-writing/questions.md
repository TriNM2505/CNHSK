# Questions and decisions — Spec 006

## Chốt T-06 ngày 2026-11-01

Trạng thái: **Chốt — leader 2026-11-01**.

- Phạm vi T-06 gồm đúng `HanziCanvas.tsx`, `HanziCanvas.test.tsx`, `cnhsk-web/package.json`, `cnhsk-web/package-lock.json`, `cnhsk-web/.env.example`, cập nhật riêng T-06 trong `tasks.md`, và ghi quyết định trong `questions.md`.
- Chỉ thêm `hanzi-writer@3.7.3`; không cài `hanzi-writer-data`. Dữ liệu tải từ URL gốc `VITE_HANZI_DATA_URL=https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1`; loader nối `/{mã-hóa-chữ}.json`, bỏ dấu `/` cuối, biến rỗng dùng URL ghim trên.
- T-06 chỉ làm nền canvas: tải dữ liệu nét, báo lỗi dữ liệu, khung 300×300 co theo màn hình nhỏ và tối thiểu 240 px, chống kết quả tải cũ ghi đè chữ mới, không gọi callback sau unmount và dọn writer. Không triển khai STROKE/RECALL/DICTATION hay thử thách.
- Không sửa `HanziCanvasSlot.tsx`, `CharacterPracticeView.tsx`, `WritingChallengeView.tsx`, hoặc `Scr014CharacterWritingPage.test.tsx`; thay đổi tích hợp thuộc T-08/T-09. AT-23 chưa hoàn tất ở T-06; phần còn lại thuộc T-08/T-09.
- T-06 chạy trong worktree riêng. Lockfile sinh bằng `npx --yes npm@11.13.0 install --package-lock-only --ignore-scripts --save-exact hanzi-writer@3.7.3`; chỉ chấp nhận diff `package.json +1`, `package-lock.json +7`, không có dòng xóa. Sau đó chạy `npm ci` và xác nhận diff không đổi.
- Test mock `hanzi-writer` trong `HanziCanvas.test.tsx`, bao gồm URL/encoding, bỏ `/` cuối, fallback, lỗi HTTP/mạng/JSON, đổi chữ trong lúc tải, unmount, resize và kích thước tối thiểu 240 px.
- Gate FE: `npm run lint`, `npm run typecheck`, `npx vitest run --maxWorkers=2 --minWorkers=1`, `npm run build`, `git diff --check`, kiểm BOM/TODO. Một commit T-06, file cụ thể, push và Draft PR riêng.

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

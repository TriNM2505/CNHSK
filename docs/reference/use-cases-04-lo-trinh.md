# CNHSK — Đặc tả Use Case · Nhóm 3 · Lộ trình thông minh

> **UC-042 → UC-055** · 14 use case · Tính năng 3.1 → 3.5
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
>
> Đây là nhóm **adaptive learning** — phần "thông minh" của hệ thống và là điểm mạnh nhất khi
> bảo vệ. Cũng là nhóm có nhiều UC `SYSTEM` (tự động) nhất: 6 trong 14.

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
| --- | --- | --- | --- | --- | --- |
| UC-042 | Cập nhật mastery sau khi trả lời đúng/sai | `SYSTEM` | P0 | MVP | 3.1 |
| UC-043 | Cập nhật mastery ngay sau khi chơi game | `SYSTEM` | P0 | MVP | 3.1 |
| UC-044 | Tính lại hạn ôn theo FSRS | `SYSTEM` | P0 | MVP | 3.1 |
| UC-045 | Xem cây chủ đề và trạng thái khóa/mở | `USER` | P1 | MVP | 3.2 |
| UC-046 | Mở khóa chủ đề khi đạt 90% | `SYSTEM` | P1 | MVP | 3.2 |
| UC-047 | Nhận bài luyện cho phần yếu (chưa đạt 90%) | `USER` | P1 | MVP | 3.2 |
| UC-048 | Yêu cầu AI sinh bài luyện theo điểm yếu | `USER` | P1 | MVP | 3.3 |
| UC-049 | AI sinh câu hỏi vào hàng đợi duyệt | `SYSTEM` | P1 | MVP | 3.3 |
| UC-050 | Tái sử dụng câu hỏi đã duyệt cho người khác | `SYSTEM` | P2 | MVP | 3.3 |
| UC-051 | Xem thống kê tiến độ cá nhân | `USER` | P1 | MVP | 3.4 |
| UC-052 | Xem biểu đồ tiến bộ 7/30/90 ngày | `USER` | P2 | MVP | 3.4 |
| UC-053 | Xem bản đồ mạnh-yếu theo kỹ năng | `USER` | P2 | MVP | 3.4 |
| UC-054 | Cài đặt giờ nhắc học và kênh nhận | `USER` | P2 | MVP | 3.5 |
| UC-055 | Gửi nhắc học tự động đúng giờ | `SYSTEM` | P2 | MVP | 3.5 |

---

# UC-042 · Cập nhật mastery sau khi trả lời đúng/sai

| | |
|---|---|
| **UC-ID** | UC-042 · **Actor** `SYSTEM` · **Pri** P0 · **Scope** MVP · **FT** 3.1 |

## Mô tả

Use case **nền tảng nhất của cả hệ thống**. Mọi lượt trả lời từ mọi nguồn (bài thi UC-035,
luyện dạng UC-037, chủ đề UC-027, ngữ pháp UC-024, nhận diện UC-019) đều đi qua đây để đổi
`user_knowledge_state`.

Không có endpoint riêng — là service được gọi trong transaction của UC gọi nó.

## Tiền điều kiện

1. Một lượt trả lời đã được chấm ở **server**
2. Câu hỏi có ≥ 1 dòng `question_knowledge_points`
3. Đang ở **trong** transaction của UC gọi

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Trả lời đúng | `mastery` tăng, `stability` tăng, `next_review_at` giãn xa |
| Trả lời sai | `mastery` giảm, `stability` giảm, `next_review_at` gần lại |
| Lần đầu gặp điểm kiến thức | `INSERT` dòng `user_knowledge_state` mới |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Caller | Gọi `MasteryService.record(userId, questionId, isCorrect, sourceType, durationMs)` |
| 2 | System | Tra `question_knowledge_points` lấy danh sách điểm kiến thức của câu |
| 3 | System | Lấy trọng số theo `sourceType` (bài thi 1.0 · luyện 0.8 · game 0.6 · viết 0.5) |
| 4 | System | Với **mỗi** điểm kiến thức: `SELECT ... FOR UPDATE` dòng `user_knowledge_state` |
| 5 | System | Chưa có dòng → `INSERT` với tham số FSRS khởi tạo |
| 6 | System | Tính `mastery` mới, gọi UC-044 tính `next_review_at` |
| 7 | System | `UPDATE` dòng |
| 8 | System | Nếu điểm kiến thức thuộc chủ đề → cập nhật `user_topic_progress` (cùng transaction) |
| 9 | System | Trả `{knowledge_point_id, mastery_before, mastery_after}[]` cho caller |

## Luồng thay thế

**A1 — Câu hỏi có nhiều điểm kiến thức**
Cập nhật **tất cả**, mỗi điểm đầy đủ trọng số. Một câu hỏi đo nhiều điểm thì kết quả có ý
nghĩa cho cả các điểm đó.

**A2 — Cùng điểm kiến thức xuất hiện ở 2 câu trong một bài**
Áp **hai lần** theo thứ tự — hai lần trả lời là hai bằng chứng. Nhưng lần thứ hai dùng
`mastery` đã cập nhật từ lần một, không dùng giá trị đầu bài.

**A3 — Trả lời quá nhanh (`suspicious`)**
Trọng số ×0.5 trước khi tính.

**A4 — `sourceType` là `SELF_DECLARED` (UC-026 "tôi đã biết từ này")**
`mastery` đặt mức trung bình, cờ `self_declared = true`, nhưng **không** tính là bằng chứng
FSRS — `stability` giữ mức khởi tạo.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_KNOWLEDGE_POINTS` | — | Câu chưa gắn nhãn | **Không cập nhật được gì.** Ghi log WARN. Caller vẫn ghi điểm bình thường |
| `NOT_IN_TRANSACTION` | 500 | Gọi ngoài transaction | 🔴 **Chặn ở code** — xem ghi chú |
| `KNOWLEDGE_POINT_NOT_FOUND` | 500 | `question_knowledge_points` trỏ tới điểm đã xoá | Bỏ điểm đó, ghi log. Khoá ngoại phải chặn từ đầu |
| `MASTERY_OUT_OF_RANGE` | 500 | Tính ra `mastery` ngoài `[0,1]` | Kẹp về biên, ghi log. Sai công thức FSRS |
| `DEADLOCK_DETECTED` | 500 | Hai transaction khoá cùng cặp dòng theo thứ tự khác nhau | 🔴 Xem ghi chú |
| `TOPIC_PROGRESS_UPDATE_FAILED` | 500 | Bước 8 lỗi | **Rollback toàn bộ** — không được đổi mastery mà % chủ đề không đổi |
| `INVALID_SOURCE_TYPE` | 500 | `sourceType` lạ | Chặn — trọng số không xác định thì không được tính |

> 🔴 **`NOT_IN_TRANSACTION` — vì sao phải chặn bằng code, không chỉ nhắc trong tài liệu.**
> AC-10 trong constitution: "mastery + game score cùng transaction". Nếu một dev gọi
> `MasteryService.record()` từ một method không có `@Transactional`, Spring sẽ mở transaction
> riêng cho từng câu → nửa bài thi cập nhật được, nửa không, và không rollback được.
> **Cách chặn:** kiểm `TransactionSynchronizationManager.isActualTransactionActive()` ở đầu
> method và ném lỗi ngay. Rẻ, và bắt được lỗi lúc chạy test đầu tiên.

> 🔴 **`DEADLOCK_DETECTED` là rủi ro thật với `SELECT ... FOR UPDATE` ở bước 4.**
> Bài thi A khoá điểm kiến thức `[5, 12]` theo thứ tự đó; bài thi B (người khác) khoá `[12, 5]`
> → deadlock. PostgreSQL sẽ kill một transaction.
> **Cách tránh:** luôn khoá theo `ORDER BY knowledge_point_id` — sắp danh sách trước khi vào
> vòng lặp bước 4. Một dòng code, tránh được cả một lớp bug khó tái hiện.

## Business rule

| # | Rule |
| --- | --- |
| BR-042-1 | Mọi kết quả học tập có đáp án đúng/sai hợp lệ phải cập nhật vào `user_knowledge_state` để hệ thống biết người học mạnh/yếu ở điểm kiến thức nào. |
| BR-042-2 | Chỉ kết quả đã được chấm ở server mới được dùng để cập nhật mastery. Kết quả chỉ tính ở client không được coi là bằng chứng mạnh. |
| BR-042-3 | Nếu người học trả lời đúng, mastery của các điểm kiến thức liên quan tăng. Nếu trả lời sai, mastery phải giảm hoặc được điều chỉnh xuống, không được giữ nguyên. |
| BR-042-4 | Nếu một câu hỏi gắn với nhiều điểm kiến thức, hệ thống cập nhật tất cả các điểm kiến thức đó. |
| BR-042-5 | Mức ảnh hưởng đến mastery phụ thuộc vào nguồn học: bài thi có trọng số cao nhất, bài luyện thấp hơn, game và luyện viết có trọng số thấp hơn vì độ tin cậy thấp hơn. |
| BR-042-6 | Mastery luôn nằm trong khoảng từ 0 đến 1. Nếu kết quả tính toán vượt ngoài khoảng này, hệ thống phải đưa về giới hạn hợp lệ. |
| BR-042-7 | Nếu câu hỏi chưa được gắn nhãn kiến thức, hệ thống vẫn cho luồng học chính tiếp tục nhưng không cập nhật được mastery cho câu đó và phải ghi nhận lỗi dữ liệu. |
| BR-042-8 | Cập nhật mastery và cập nhật tiến độ chủ đề liên quan phải nhất quán với nhau. Không được để mastery đã đổi nhưng phần trăm hoàn thành chủ đề vẫn giữ giá trị cũ. |

## API · DB

Không có endpoint — service nội bộ module `learning`.

| Bảng | Vai trò |
| --- | --- |
| `question_knowledge_points` | Đọc — map câu → điểm kiến thức |
| `knowledge_points` | Đọc |
| `user_knowledge_state` | **Ghi** (`SELECT FOR UPDATE` rồi `UPDATE`) |
| `user_topic_progress` | **Ghi** |

> **Index bắt buộc** (feature tree 3.1): `(user_id, due_at)` và `(user_id, mastery)`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đúng, nguồn thi | `mastery` tăng, `next_review_at` giãn |
| T2 | Sai | `mastery` **giảm**, `next_review_at` gần lại |
| T3 | Điểm kiến thức lần đầu | `INSERT` dòng mới |
| T4 | Câu có 3 nhãn | 3 dòng `user_knowledge_state` đổi |
| T5 | Gọi ngoài `@Transactional` | Ném lỗi ngay, **không** ghi gì |
| T6 | Hai bài thi song song, nhãn giao nhau | Không deadlock (nhờ BR-042-3) |
| T7 | Lỗi ở bước 8 | Mastery **cũng rollback** |
| T8 | Câu không có nhãn | Log WARN, caller không lỗi |

---

# UC-043 · Cập nhật mastery ngay sau khi chơi game

| | |
|---|---|
| **UC-ID** | UC-043 · **Actor** `SYSTEM` · **Pri** P0 · **Scope** MVP · **FT** 3.1 |

## Mô tả

Điểm game (module `community`) đổ về mastery (module `learning`) **tức thì**, cùng transaction
với lúc lưu điểm.

> **Đây là cải thiện lớn nhất của kiến trúc bản 5.** Bản trước: điểm game đẩy từ Community sang
> Learning lúc **2h sáng** → mastery trễ tới một ngày. Giờ hai module cùng tiến trình,
> `MasteryUpdater.applyGameResult()` chạy cùng transaction.

## Tiền điều kiện

1. Một lượt game đã hoàn thành, điểm đã **xác thực ở server** (HR-06)
2. Game có map từ nội dung sang điểm kiến thức (ví dụ game ghép chữ → chữ nào)
3. Đang trong transaction của UC-088 (lưu điểm game)

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Thành công | `game_scores` (community) **và** `user_knowledge_state` (learning) cùng commit |
| Thất bại | **Cả hai rollback** — không có điểm game mà mastery không đổi, hoặc ngược lại |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Module `community` | Xác thực điểm game xong (UC-088) |
| 2 | `community` | Gọi **qua lớp `api`**: `learningApi.applyGameResult(userId, gameType, itemResults)` |
| 3 | `learning` | Map từng item của game sang `knowledge_point_id` |
| 4 | `learning` | Gọi UC-042 với `sourceType = GAME` (trọng số 0.6) |
| 5 | `learning` | Trả kết quả mastery đã đổi |
| 6 | `community` | Ghi `game_scores` |
| 7 | System | **Commit cùng lúc** |

## Luồng thay thế

**A1 — Game không map được sang điểm kiến thức**
Ví dụ game tốc độ thuần phản xạ. Ghi điểm game bình thường, **không** đổi mastery. Không phải
lỗi — có game chỉ để vui.

**A2 — Game có item không có trong từ điển**
Bỏ item đó khỏi phần tính mastery, các item khác vẫn tính.

**A3 — Chơi game trong WebView mobile (UC-013)**
Không khác — token đã tiêm, luồng giống web.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `MODULE_BOUNDARY_VIOLATION` | — | `community` đọc thẳng bảng `learning` | 🔴 Xem ghi chú |
| `SCORE_NOT_VERIFIED` | 400 | Điểm chưa xác thực ở server | **Chặn** — điểm client gửi không được đổi mastery (HR-06) |
| `NO_ITEM_MAPPING` | — | Game không map được | Không phải lỗi (A1) |
| `ITEM_NOT_IN_DICTIONARY` | — | Item lạ | Bỏ item (A2) |
| `MASTERY_UPDATE_FAILED` | 500 | UC-042 lỗi | **Rollback cả `game_scores`** (AC-10) |
| `CROSS_MODULE_TRANSACTION_FAILED` | 500 | Transaction không bao được cả hai module | 🔴 Xem ghi chú |
| `GAME_SCORE_CEILING_EXCEEDED` | 400 | Điểm vượt trần lý thuyết của game | Chống gian lận — không tính cả điểm và mastery |

> 🔴 **`MODULE_BOUNDARY_VIOLATION` là lỗi kiến trúc mà `ModuleBoundaryTest` (ArchUnit) bắt.**
> Đường tắt hấp dẫn: `community` có `userId`, có `knowledge_point_id`, chỉ cần một câu UPDATE
> vào `learning.user_knowledge_state` là xong — nhanh hơn đi qua lớp `api`.
> Nhưng làm thế thì logic FSRS bị bỏ qua, `user_topic_progress` không cập nhật, và không ai
> biết vì test vẫn xanh. ArchUnit phải chặn **ở tầng package**, không dựa vào người review nhớ.

> 🔴 **`CROSS_MODULE_TRANSACTION_FAILED` — điểm mấu chốt của Modular Monolith.** Vì hai module
> **cùng một tiến trình, cùng một `DataSource`**, transaction bao được cả hai. Đây chính là lý
> do bỏ microservices: nếu tách service riêng thì đoạn này cần distributed transaction hoặc
> saga, phức tạp gấp nhiều lần.
> **Điều kiện để giữ được lợi thế này:** lời gọi bước 2 phải là **lời gọi hàm trực tiếp**, không
> phải HTTP nội bộ hay message queue. Đổi sang HTTP là mất tính nguyên tử ngay.

## Business rule

| # | Rule |
| --- | --- |
| BR-043-1 | Kết quả game chỉ được cập nhật mastery khi hệ thống xác nhận được ván chơi hợp lệ ở server. Điểm do client tự gửi không được tin tuyệt đối. |
| BR-043-2 | Game chỉ cập nhật mastery khi nội dung trong game có thể liên kết được với điểm kiến thức cụ thể, ví dụ chữ, từ, pinyin hoặc bộ thủ. |
| BR-043-3 | Nếu một game chỉ mang tính giải trí hoặc phản xạ và không đo điểm kiến thức cụ thể, hệ thống vẫn có thể lưu điểm game nhưng không cập nhật mastery. |
| BR-043-4 | Mastery từ game có trọng số thấp hơn bài thi và bài luyện chính thức vì game dễ bị ảnh hưởng bởi tốc độ, thao tác và logic chạy trên client. |
| BR-043-5 | Khi lưu điểm game và cập nhật mastery, hai dữ liệu này phải nhất quán. Không được để có điểm game hợp lệ nhưng mastery không đổi, hoặc mastery đổi nhưng điểm game không được lưu. |
| BR-043-6 | Nếu một số item trong ván game không map được sang điểm kiến thức, hệ thống bỏ qua các item đó và chỉ cập nhật mastery cho phần map được. |
| BR-043-7 | Kết quả game bất thường, ví dụ điểm vượt mức tối đa có thể đạt được, không được dùng để cập nhật điểm hoặc mastery. |

## API · DB

Lời gọi nội bộ: `community` → `learning.api.LearningApi.applyGameResult(...)`

| Bảng | Module | Vai trò |
| --- | --- | --- |
| `game_scores` | `community` | **Ghi** |
| `user_knowledge_state` | `learning` | **Ghi** (qua UC-042) |
| `user_topic_progress` | `learning` | **Ghi** |

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chơi game ghép chữ, đúng 8/10 | `game_scores` ghi, mastery đổi **ngay** |
| T2 | Đọc mastery ngay sau khi lưu điểm | Đã thấy giá trị mới (không chờ 2h sáng) |
| T3 | UC-042 ném lỗi | `game_scores` **không** có dòng nào |
| T4 | Điểm client gửi chưa xác thực | 400, mastery không đổi |
| T5 | ArchUnit: `community` import `learning.repository` | **Test đỏ** |
| T6 | Game thuần phản xạ | Điểm ghi, mastery giữ nguyên |
| T7 | Điểm vượt trần game | 400, không ghi gì |

---

# UC-044 · Tính lại hạn ôn theo FSRS

| | |
|---|---|
| **UC-ID** | UC-044 · **Actor** `SYSTEM` · **Pri** P0 · **Scope** MVP · **FT** 3.1 |

## Mô tả

Thuật toán FSRS: từ `stability`, `difficulty`, `elapsed_days`, `rating` tính ra `next_review_at`.
Thay thế Leitner của bản cũ.

## Tiền điều kiện

1. Dòng `user_knowledge_state` tồn tại (hoặc đang khởi tạo)
2. `rating` thuộc `{Again, Hard, Good, Easy}` — **do server tính**, không nhận từ client (BR-024-4)
3. Tham số FSRS (17 weight) đã cấu hình

## Hậu điều kiện

`stability`, `difficulty`, `next_review_at`, `last_reviewed_at` cập nhật.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | UC-042 | Gọi `FsrsService.schedule(state, rating, now)` |
| 2 | System | Tính `elapsed_days = now − last_reviewed_at` |
| 3 | System | Tính `retrievability` từ `stability` và `elapsed_days` |
| 4 | System | Tính `difficulty` mới theo `rating` |
| 5 | System | Tính `stability` mới |
| 6 | System | Tính `interval_days` để `retrievability` mục tiêu = 0.9 |
| 7 | System | `next_review_at = now + interval_days` |
| 8 | System | Kẹp `interval_days` trong `[1, 365]` |
| 9 | System | Trả state mới |

## Luồng thay thế

**A1 — Lần đầu (chưa có `last_reviewed_at`)**
Dùng tham số khởi tạo theo `rating`: `Again` 1 ngày · `Hard` 2 · `Good` 3 · `Easy` 7.

**A2 — Ôn sớm (`elapsed_days` < `interval` dự kiến)**
FSRS tự xử lý: `retrievability` còn cao → `stability` tăng **ít**. Không chặn (UC-024 A4).

**A3 — Ôn rất muộn (bỏ 3 tháng)**
`retrievability` gần 0. `stability` giảm mạnh, `interval` mới ngắn. Đúng hành vi mong muốn.

**A4 — `rating = Again` (sai)**
`interval` về 1 ngày bất kể `stability` trước đó cao thế nào.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INVALID_RATING` | 500 | `rating` ngoài 4 giá trị | Chặn |
| `NEGATIVE_ELAPSED_DAYS` | 500 | `last_reviewed_at` ở **tương lai** | 🔴 Xem ghi chú |
| `STABILITY_OVERFLOW` | 500 | `stability` tăng vô hạn sau nhiều lần `Easy` | Kẹp `interval` tối đa 365 ngày (bước 8) |
| `DIVISION_BY_ZERO` | 500 | `stability = 0` | Kẹp `stability` tối thiểu 0.1 |
| `FSRS_WEIGHTS_MISSING` | 500 | Chưa cấu hình 17 weight | **Chặn khởi động ứng dụng** — không có weight thì mọi lịch ôn sai |
| `TIMEZONE_MISMATCH` | — | Tính bằng giờ máy chủ thay vì UTC | 🔴 Xem ghi chú |
| `INTERVAL_TOO_SHORT` | — | `interval < 1` ngày | Kẹp về 1 — ôn lại trong cùng ngày làm người học mệt |

> 🔴 **`NEGATIVE_ELAPSED_DAYS` — xảy ra thật khi giờ máy chủ lệch.** Nếu `last_reviewed_at`
> ghi bằng giờ của một máy chủ đi nhanh 2 phút, rồi tính `elapsed` trên máy chủ khác, ra số
> âm → `retrievability > 1` → công thức FSRS trả giá trị vô nghĩa, và lỗi này **không crash**,
> chỉ làm lịch ôn sai âm thầm.
> **Cách chặn:** `TIMESTAMPTZ` + server UTC (đã có trong AGENTS.md §6), cộng `Math.max(0, elapsed)`.

> 🔴 **`TIMEZONE_MISMATCH` nối với UC-055.** FSRS tính bằng UTC là đúng. Nhưng nhắc học phải
> gửi lúc 20h **giờ Việt Nam** (nghiệm thu 3.5). Hai chỗ dùng hai múi giờ khác nhau — đây là
> nguồn bug kinh điển. Constitution đã bắt tác vụ định kỳ ghi `zone = "Asia/Ho_Chi_Minh"`;
> FSRS thì **không** được dùng zone đó.

## Business rule

| # | Rule |
| --- | --- |
| BR-044-1 | Hệ thống dùng FSRS hoặc thuật toán ôn lặp đã chốt để tính ngày ôn tiếp theo, không dùng hộp Leitner đơn giản cho phần lộ trình thông minh. |
| BR-044-2 | Với bài có đáp án đúng/sai, mức đánh giá ôn tập phải do server tính từ kết quả làm bài. Client không được tự gửi mức `Easy`, `Good`, `Hard` để quyết định lịch ôn. |
| BR-044-3 | Trả lời đúng làm lịch ôn giãn xa hơn. Trả lời sai làm lịch ôn gần lại để người học được ôn sớm hơn. |
| BR-044-4 | Lần đầu học một điểm kiến thức phải tạo lịch ôn ban đầu, không được chỉ đánh dấu đã học mà thiếu ngày ôn tiếp theo. |
| BR-044-5 | Hệ thống phải giới hạn khoảng cách ôn trong mức hợp lý. Trong MVP, khoảng cách ôn tối thiểu là 1 ngày và tối đa là 365 ngày. |
| BR-044-6 | Thời gian dùng cho thuật toán ôn tập phải tính thống nhất theo UTC để tránh lệch lịch do múi giờ hoặc máy chủ. |
| BR-044-7 | Nếu thiếu cấu hình thuật toán FSRS, hệ thống không được âm thầm dùng giá trị mặc định không rõ nguồn gốc. Lỗi này phải được phát hiện trước khi vận hành. |

## API · DB

Service nội bộ, không endpoint. `user_knowledge_state` (đọc + ghi qua UC-042).

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `rating = Good`, `stability = 5` | `interval` tăng so với lần trước |
| T2 | `rating = Again`, `stability = 50` | `interval = 1` ngày |
| T3 | Lần đầu, `Easy` | `interval = 7` |
| T4 | `last_reviewed_at` ở tương lai | `elapsed = 0`, không lỗi công thức |
| T5 | 20 lần `Easy` liên tiếp | `interval` ≤ 365 |
| T6 | Thiếu weight | Ứng dụng **không khởi động** |
| T7 | Ôn sớm 1 ngày trước hạn | `stability` tăng ít hơn ôn đúng hạn |
| T8 | Bỏ 100 ngày rồi ôn đúng | `interval` mới ngắn hơn trước |

---

# UC-045 · Xem cây chủ đề và trạng thái khóa/mở

| | |
|---|---|
| **UC-ID** | UC-045 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 3.2 |

## Mô tả

Cây tri thức dạng đồ thị: chủ đề nào đã mở, đang học, đã xong, còn khoá; và mở được cái nào
tiếp theo. Khác UC-025 (danh sách phẳng) ở chỗ hiện **quan hệ tiên quyết**.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `topics` có cột quan hệ tiên quyết
3. `topic_knowledge_points` đã map chủ đề → điểm kiến thức

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang "Lộ trình" |
| 2 | System | `GET /api/learning-path/tree` |
| 3 | System | Lấy toàn bộ `topics` + quan hệ tiên quyết |
| 4 | System | Lấy `user_topic_progress` của người đang đăng nhập |
| 5 | System | Tính trạng thái mỗi node: `LOCKED` · `AVAILABLE` · `IN_PROGRESS` · `COMPLETED` |
| 6 | System | Trả đồ thị: nodes + edges + `completion_percent` mỗi node |
| 7 | Client | Vẽ cây, node khoá màu xám, node kế tiếp nổi bật |

## Luồng thay thế

**A1 — `USER` mới**
Chỉ node gốc `AVAILABLE`, còn lại `LOCKED`, mọi `completion_percent = 0`.

**A2 — Chủ đề có nhiều tiên quyết**
`AVAILABLE` chỉ khi **tất cả** tiên quyết `COMPLETED`. Client hiện còn thiếu cái nào.

**A3 — Xem cây theo cấp HSK**
`?hsk_level=3` — lọc hiển thị, **không** đổi trạng thái khoá.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `CIRCULAR_PREREQUISITE` | 500 | A cần B, B cần A | 🔴 Xem ghi chú |
| `ORPHAN_TOPIC` | — | Chủ đề không tiên quyết và không phải gốc | Coi là `AVAILABLE`. Ghi log để `CONTENT_ADMIN` kiểm |
| `NO_ROOT_TOPIC` | 500 | Mọi chủ đề đều có tiên quyết | **Không mở được gì cả** — người học nhìn cây toàn ổ khoá. Phải validate khi nhập |
| `PREREQUISITE_NOT_FOUND` | 500 | Trỏ tới chủ đề đã xoá | Bỏ quan hệ đó, ghi log |
| `PROGRESS_PERCENT_MISMATCH` | — | `user_topic_progress` lệch dữ liệu thật | Cùng vấn đề UC-025 |
| `TOPIC_HAS_NO_KNOWLEDGE_POINTS` | — | `topic_knowledge_points` rỗng | Không tính được % — hiện 0% mãi dù học xong. Ghi log |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập | Cần đăng nhập |

> 🔴 **`CIRCULAR_PREREQUISITE` — lỗi cấu hình dữ liệu làm treo thuật toán.** `CONTENT_ADMIN`
> đặt "Gia đình" cần "Nghề nghiệp", "Nghề nghiệp" cần "Gia đình". Duyệt đồ thị để tính trạng
> thái sẽ **lặp vô hạn** hoặc cả hai `LOCKED` vĩnh viễn.
> **Phải chặn ở UC-112 (quản lý nội dung)** khi lưu quan hệ: kiểm chu trình bằng DFS trước khi
> commit, không để phát hiện lúc người học mở trang.

> ⚠️ **`TOPIC_HAS_NO_KNOWLEDGE_POINTS` là lệch dữ liệu âm thầm tệ nhất ở UC này.** Chủ đề có
> `topic_words` (học được) nhưng `topic_knowledge_points` rỗng → học hết từ mà % vẫn 0 → không
> bao giờ đạt 90% → chủ đề sau **khoá vĩnh viễn**. Người học không có cách nào tự thoát.

## Business rule

| # | Rule |
| --- | --- |
| BR-045-1 | Cây chủ đề hiển thị quan hệ tiên quyết giữa các chủ đề, giúp người học biết chủ đề nào đang mở, đã hoàn thành, đang học hoặc còn khóa. |
| BR-045-2 | Một chủ đề không có tiên quyết được xem là chủ đề gốc và được mở sẵn cho người học. |
| BR-045-3 | Một chủ đề có tiên quyết chỉ được mở khi tất cả chủ đề tiên quyết đã hoàn thành. |
| BR-045-4 | Chủ đề được xem là hoàn thành khi phần trăm hoàn thành đạt từ 90% trở lên. |
| BR-045-5 | Khi một chủ đề đã được mở cho người học, hệ thống không khóa lại chủ đề đó chỉ vì phần trăm hoàn thành sau này giảm xuống. |
| BR-045-6 | Quan hệ tiên quyết giữa các chủ đề không được tạo thành vòng lặp. Dữ liệu có vòng lặp phải bị chặn khi nhập hoặc chỉnh sửa nội dung. |
| BR-045-7 | Chủ đề không có điểm kiến thức hoặc không thể tính tiến độ thì không nên xuất hiện trong cây học tập chính cho đến khi dữ liệu được bổ sung. |
| BR-045-8 | Cây chủ đề chỉ hiển thị dữ liệu tiến độ của người học đang đăng nhập, không được dùng hoặc lộ tiến độ của người khác. |

## API · DB

```
GET /api/learning-path/tree
GET /api/learning-path/tree?hsk_level={n}
```

`topics` · `topic_knowledge_points` · `user_topic_progress` · `user_knowledge_state` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `USER` mới | Node gốc `AVAILABLE`, còn lại `LOCKED` |
| T2 | Xong chủ đề A (90%) | B (cần A) chuyển `AVAILABLE` |
| T3 | B cần A và C, chỉ xong A | B vẫn `LOCKED`, hiện thiếu C |
| T4 | Dữ liệu có chu trình A↔B | Validate **chặn từ lúc lưu** |
| T5 | Mọi chủ đề có tiên quyết | Validate chặn, báo thiếu gốc |
| T6 | Chủ đề rỗng nhãn kiến thức | Không vào cây, ghi log |

---

# UC-046 · Mở khóa chủ đề khi đạt 90%

| | |
|---|---|
| **UC-ID** | UC-046 · **Actor** `SYSTEM` · **Pri** P1 · **Scope** MVP · **FT** 3.2 |

## Mô tả

Khi `completion_percent` một chủ đề đạt **90%**, mở các chủ đề có nó là tiên quyết. Tự động,
không ai bấm.

## Tiền điều kiện

1. Một lượt học/thi vừa làm `completion_percent` đổi (UC-029, UC-042)
2. `completion_percent ≥ 90`
3. Có chủ đề nhận nó làm tiên quyết
4. Đang trong transaction của caller

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Mở được | `user_topic_progress` cho chủ đề mới: `status = AVAILABLE`, `unlocked_at` |
| Chưa đủ 90% | Không làm gì |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Caller | Sau khi cập nhật `user_topic_progress`, gọi `TopicGateService.evaluate(userId, topicId)` |
| 2 | System | Đọc `completion_percent` **vừa ghi** (cùng transaction) |
| 3 | System | So với ngưỡng 90 |
| 4 | System | Đánh dấu chủ đề `COMPLETED` |
| 5 | System | Tìm chủ đề có `topicId` là tiên quyết |
| 6 | System | Với mỗi ứng viên: kiểm **mọi** tiên quyết khác đã `COMPLETED` chưa |
| 7 | System | Đủ điều kiện → `INSERT` hoặc `UPDATE` thành `AVAILABLE`, ghi `unlocked_at` |
| 8 | System | Trả danh sách chủ đề vừa mở cho client hiện thông báo |

## Luồng thay thế

**A1 — Chưa đủ 90%**
Trả `{unlocked: [], needed: 90, actual: 82}`.

**A2 — Mở nhiều chủ đề một lúc**
Một chủ đề là tiên quyết của 3 chủ đề khác → mở cả 3 trong cùng transaction.

**A3 — Chủ đề đã mở trước đó**
Bỏ qua, không ghi trùng (idempotent).

**A4 — `completion_percent` giảm dưới 90 sau đó**
Ví dụ UC-029 hạ từ `self_declared`. Chủ đề đã mở **vẫn mở** (BR-045-6).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `UNIQUE_VIOLATION` | 500 | Hai request song song cùng mở | Cần unique `(user_id, topic_id)`; bắt lỗi, coi như đã mở |
| `NOT_IN_TRANSACTION` | 500 | Gọi ngoài transaction | 🔴 Chặn — xem UC-029 `UNLOCK_FAILED` |
| `STALE_PERCENT_READ` | — | Đọc `completion_percent` **trước** khi caller ghi | 🔴 Xem ghi chú |
| `CIRCULAR_PREREQUISITE` | 500 | Chu trình | Duyệt lặp vô hạn — phải chặn lúc nhập (UC-045) |
| `PREREQUISITE_NOT_FOUND` | 500 | Tiên quyết đã xoá | Coi như đã thoả (không chặn người học vì lỗi dữ liệu của ta) |
| `UNLOCK_FAILED` | 500 | Lỗi ghi | **Rollback cả điểm bài thi** (UC-029) |
| `THRESHOLD_MISCONFIGURED` | 500 | Ngưỡng ngoài `[0,100]` | Chặn khởi động |

> 🔴 **`STALE_PERCENT_READ` — bug thứ tự trong cùng transaction.** Nếu bước 2 đọc
> `completion_percent` mà caller **chưa** flush lệnh `UPDATE`, JPA có thể trả giá trị cũ từ
> persistence context → đạt 90% mà không mở khoá, và **lần sau cũng không mở** vì
> `completion_percent` đã ≥ 90 nên không có sự kiện nào gọi lại UC này.
> **Người học kẹt vĩnh viễn** — đúng hệ quả đã ghi ở UC-029.
> **Cách chặn:** `flush()` trước khi gọi, hoặc truyền thẳng giá trị `newPercent` vào tham số
> thay vì đọc lại DB. Cách thứ hai đơn giản và an toàn hơn.

## Business rule

| # | Rule |
| --- | --- |
| BR-046-1 | Khi người học hoàn thành một chủ đề với tỷ lệ từ 90% trở lên, hệ thống tự động kiểm tra và mở các chủ đề tiếp theo đủ điều kiện. |
| BR-046-2 | Ngưỡng mở khóa mặc định là 90% và phải được cấu hình tập trung để dễ điều chỉnh nếu dự án thay đổi chính sách. |
| BR-046-3 | Một chủ đề chỉ được mở khi tất cả chủ đề tiên quyết của nó đã hoàn thành, không chỉ dựa vào một tiên quyết vừa đạt. |
| BR-046-4 | Mở khóa chủ đề là thao tác tự động, người học không cần bấm nút mở khóa thủ công. |
| BR-046-5 | Mở khóa phải là thao tác an toàn khi chạy lại nhiều lần. Nếu chủ đề đã mở trước đó, hệ thống không tạo bản ghi trùng và không báo lỗi cho người học. |
| BR-046-6 | Khi chủ đề đã mở, hệ thống giữ trạng thái mở. Chủ đề không bị khóa lại nếu sau này mastery hoặc phần trăm hoàn thành giảm. |
| BR-046-7 | Việc cập nhật điểm, cập nhật phần trăm hoàn thành và mở khóa chủ đề tiếp theo phải nhất quán. Không được để người học đạt ngưỡng nhưng chủ đề sau vẫn bị khóa do lỗi xử lý. |

## API · DB

Không có endpoint — chạy trong UC-029, UC-042.

`topics` · `user_topic_progress` (đọc + ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đạt 92% | Chủ đề sau `AVAILABLE`, có `unlocked_at` |
| T2 | Đạt 88% | Không mở, trả `needed: 90` |
| T3 | Là tiên quyết của 3 chủ đề | Mở cả 3 |
| T4 | B cần A và C, xong A (C chưa) | B **không** mở |
| T5 | Gọi lại khi đã mở | Không dòng trùng |
| T6 | Hai request song song | Một dòng duy nhất |
| T7 | % sau đó giảm còn 85 | Chủ đề đã mở **vẫn mở** |
| T8 | Lỗi ghi ở bước 7 | Điểm bài thi **cũng rollback** |

---

# UC-047 · Nhận bài luyện cho phần yếu (chưa đạt 90%)

| | |
|---|---|
| **UC-ID** | UC-047 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 3.2 |

## Mô tả

Chủ đề chưa đạt 90% → hệ thống chỉ sinh bài cho **phần yếu**, không bắt học lại cả chủ đề.
Đây là điểm khác biệt so với app học truyền thống.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chủ đề `IN_PROGRESS`, `completion_percent < 90`
3. Có điểm kiến thức trong chủ đề với `mastery` thấp
4. Kho có câu `APPROVED` cho các điểm đó

## Hậu điều kiện

Trả bộ bài luyện nhắm đúng phần yếu. Không tạo dữ liệu mới ở UC này.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Luyện phần còn yếu" trong chủ đề |
| 2 | System | `GET /api/topics/{id}/weak-practice` |
| 3 | System | Lấy `topic_knowledge_points` của chủ đề |
| 4 | System | Join `user_knowledge_state`, sắp `mastery` tăng dần |
| 5 | System | Lấy các điểm `mastery < 0.9` (chưa đạt ngưỡng) |
| 6 | System | Lấy câu hỏi `APPROVED` cho các điểm đó, ưu tiên điểm yếu nhất |
| 7 | System | Trả bộ câu + danh sách điểm yếu đang nhắm |
| 8 | `USER` | Luyện — mastery cập nhật qua UC-042 |
| 9 | System | Khi `completion_percent` đạt 90 → UC-046 mở chủ đề sau |

## Luồng thay thế

**A1 — Mọi điểm đều ≥ 0.9 nhưng % vẫn < 90**
Nghĩa là còn từ **chưa học** (chưa có `user_knowledge_state`). Chuyển sang UC-026 (học từ mới)
thay vì luyện.

**A2 — Kho không có câu cho điểm yếu**
Gợi ý UC-048 (AI sinh bài theo điểm yếu).

**A3 — Chủ đề đã đạt 90%**
Vẫn cho luyện nếu muốn. Trả điểm yếu nhất còn lại, kèm cờ `already_completed: true`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `TOPIC_LOCKED` | 403 | Chủ đề khoá | Chặn ở server |
| `TOPIC_NOT_STARTED` | 422 | Chưa học từ nào | Chuyển UC-026 — không có gì để gọi là "yếu" |
| `NO_WEAK_POINTS` | 200 | Mọi điểm ≥ 0.9 | Chuyển học từ mới (A1) |
| `NO_PRACTICE_QUESTIONS` | 200 (rỗng) | Kho thiếu câu | Gợi ý AI sinh (A2) |
| `TOPIC_HAS_NO_KNOWLEDGE_POINTS` | 500 | `topic_knowledge_points` rỗng | 🔴 Không xác định được phần yếu. Cùng gốc với UC-045 |
| `UNAPPROVED_QUESTION_SERVED` | — | Lọt câu chưa duyệt | Lọc `APPROVED` ở repository |
| `PERCENT_WEAK_POINT_CONTRADICTION` | — | % < 90 nhưng không điểm nào < 0.9 và không từ nào chưa học | 🔴 Xem ghi chú |

> 🔴 **`PERCENT_WEAK_POINT_CONTRADICTION` là dấu hiệu `completion_percent` đã lệch.** Nếu %
> tính từ `user_knowledge_state` thì ba con số phải nhất quán. Mâu thuẫn nghĩa là
> `user_topic_progress` bị cập nhật riêng ở đâu đó — đúng vấn đề `PROGRESS_PERCENT_MISMATCH`
> (UC-025).
> **Hệ quả cho người học:** thấy 85%, bấm "luyện phần yếu" → "bạn không có phần nào yếu" →
> **không có đường nào lên 90%**. Kẹt, và không hiểu tại sao.
> Đây là lý do cần job đối chiếu định kỳ (khoảng trống #10 nhóm 1).

## Business rule

| # | Rule |
| --- | --- |
| BR-047-1 | Chức năng “luyện phần yếu” chỉ lấy các điểm kiến thức thuộc chủ đề mà người học đang luyện. |
| BR-047-2 | Điểm kiến thức được xem là yếu khi mastery thấp hơn ngưỡng hoàn thành của chủ đề. Trong MVP, ngưỡng này là 0.9. |
| BR-047-3 | Hệ thống ưu tiên tạo bài luyện cho các điểm kiến thức có mastery thấp nhất trước. |
| BR-047-4 | Người học không phải học lại toàn bộ chủ đề nếu chỉ yếu một vài điểm kiến thức. |
| BR-047-5 | Bài luyện trả về chỉ được dùng câu hỏi đã được duyệt và đang hợp lệ. Câu hỏi chưa duyệt không được đưa cho người học. |
| BR-047-6 | Nếu chủ đề chưa có dữ liệu học tập nào, hệ thống không gọi đó là “phần yếu”; người học cần bắt đầu học nội dung mới trước. |
| BR-047-7 | Nếu mọi điểm đã học đều đạt nhưng phần trăm chủ đề vẫn chưa đủ, hệ thống hướng người học học phần nội dung chưa học thay vì luyện lại phần đã đạt. |
| BR-047-8 | Nếu không có câu hỏi phù hợp cho điểm yếu, hệ thống báo rõ là kho câu hỏi chưa đủ. Việc yêu cầu AI sinh thêm bài là một hành động riêng, không tự động chạy ngầm. |

## API · DB

```
GET /api/topics/{id}/weak-practice
```

`topic_knowledge_points` · `user_knowledge_state` · `user_topic_progress` · `questions` · `question_knowledge_points` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chủ đề 70%, 5 điểm yếu | Bài luyện nhắm 5 điểm đó |
| T2 | Chủ đề 100% | Vẫn luyện được, cờ `already_completed` |
| T3 | Chủ đề khoá | 403 |
| T4 | Chưa học từ nào | 422, chuyển UC-026 |
| T5 | Mọi điểm ≥ 0.9, % = 85 | Chuyển học từ mới |
| T6 | Kho thiếu câu | 200 rỗng, gợi ý AI |
| T7 | % = 85, không điểm yếu, không từ chưa học | Log ERROR mâu thuẫn |

---

# UC-048 · Yêu cầu AI sinh bài luyện theo điểm yếu

| | |
|---|---|
| **UC-ID** | UC-048 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 3.3 |

## Mô tả

Người học bấm yêu cầu AI sinh câu hỏi mới bám điểm yếu của mình. Chạy **nền** — không bắt
người học chờ. **Tốn lượt** (một trong 4 tính năng tốn phí).

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có điểm kiến thức `mastery` thấp
3. Còn lượt hoặc còn điểm (quota) — ⚠️ chờ `TODO(PAYMENT_SCOPE)`
4. Chưa vượt giới hạn job đang chạy

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Thành công | `ai_generation_jobs` tạo dòng `status = QUEUED`; trừ lượt; trả `job_id` |
| Hết lượt | Không tạo job, **không trừ gì**, trả 402 |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Nhờ AI tạo bài luyện" |
| 2 | System | `POST /api/ai/generate-practice` — `{knowledge_point_ids?, count}` |
| 3 | System | Không truyền điểm → tự lấy 3–5 điểm yếu nhất (UC-040 A3) |
| 4 | System | **Kiểm quota** qua `QuotaService` |
| 5 | System | **Mở transaction**: trừ lượt (`feature_usage` + `credit_transactions`) |
| 6 | System | Tạo `ai_generation_jobs` `status = QUEUED` |
| 7 | System | **Commit** |
| 8 | System | Đẩy job cho worker (UC-049) chạy nền |
| 9 | System | Trả `{job_id, status: QUEUED}` **ngay** |
| 10 | `USER` | Làm việc khác; nhận thông báo khi câu hỏi đã duyệt xong |

## Luồng thay thế

**A1 — Hết lượt free, còn điểm**
Trừ 1 điểm từ `user_credits`, ghi `credit_transactions` kèm `balance_before`/`balance_after`.

**A2 — Hết cả lượt và điểm**
402 `QUOTA_EXCEEDED`, gợi ý nạp thẻ (UC-094). **Không** tạo job.

**A3 — Đã có job đang chạy**
Trả job cũ thay vì tạo mới. Không trừ lượt lần hai.

**A4 — Chỉ định điểm kiến thức cụ thể**
Từ UC-041 khi kho rỗng. Kiểm điểm đó tồn tại.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `QUOTA_EXCEEDED` | 402 | Hết lượt và điểm | Gợi ý nạp. **Không tạo job, không trừ gì** |
| `QUOTA_DEDUCTED_BUT_JOB_FAILED` | 500 | Trừ lượt xong, tạo job lỗi | 🔴 Xem ghi chú |
| `JOB_ALREADY_RUNNING` | 409 | Đã có job `QUEUED`/`RUNNING` | Trả job cũ (A3) |
| `TOO_MANY_JOBS_TODAY` | 429 | > 10 job/ngày | Chặn đốt tiền API |
| `NO_WEAK_POINTS` | 422 | Không xác định được điểm yếu | Cần học/thi trước |
| `KNOWLEDGE_POINT_NOT_FOUND` | 404 | Điểm chỉ định sai | Chặn |
| `AI_SERVICE_UNAVAILABLE` | — | API ngoài chết | Job `QUEUED` chờ retry. **Hoàn lượt nếu retry hết vẫn lỗi** (UC-097) |
| `PAYMENT_SCOPE_UNDEFINED` | 500 | ⚠️ Chưa chốt tiền thật hay giả lập | Chặn tính năng tới khi chốt |

> 🔴 **`QUOTA_DEDUCTED_BUT_JOB_FAILED` — mất tiền của người học.** Bước 5 trừ lượt, bước 6
> tạo job. Nếu tách transaction: trừ tiền thành công, tạo job lỗi → người học mất 1 điểm mà
> không nhận được gì. Với tiền thật thì đây là **tranh chấp**, và `FINANCE_ADMIN` phải xử lý
> tay (UC-102).
> **Bắt buộc:** bước 5 và 6 **cùng một transaction**. Bước 8 (đẩy worker) ở ngoài — nếu đẩy
> lỗi thì job vẫn `QUEUED` trong DB và một scheduler quét lại được.

> ⚠️ **`AI_SERVICE_UNAVAILABLE` và hoàn lượt.** API AI ngoài có thể chết. Chính sách cần chốt:
> retry mấy lần, sau bao lâu thì hoàn lượt tự động (UC-097). Không có chính sách thì người học
> mất điểm vì lỗi nhà cung cấp.

## Business rule

| # | Rule |
| --- | --- |
| BR-048-1 | Người học chỉ được yêu cầu AI sinh bài khi hệ thống xác định được điểm kiến thức yếu hoặc khi người học bấm từ một điểm kiến thức cụ thể cần luyện. |
| BR-048-2 | AI sinh bài là tác vụ chạy nền. Người học không phải chờ hệ thống sinh xong ngay trên màn hình hiện tại. |
| BR-048-3 | Mỗi người học chỉ được có một yêu cầu sinh bài đang chờ hoặc đang chạy tại một thời điểm. Nếu đã có yêu cầu đang chạy, hệ thống trả về yêu cầu hiện tại thay vì tạo yêu cầu mới. |
| BR-048-4 | Hệ thống phải giới hạn số lần yêu cầu AI sinh bài của mỗi người học trong ngày để kiểm soát chi phí. Trong MVP, dùng quota theo ngày, chưa gắn với thanh toán nếu dự án chưa chốt payment. |
| BR-048-5 | Nếu không chỉ định điểm kiến thức, hệ thống lấy 3–5 điểm yếu nhất của người học để tạo yêu cầu sinh bài. |
| BR-048-6 | Nếu không xác định được điểm yếu, hệ thống không tạo yêu cầu AI và hướng người học học hoặc làm bài trước để có dữ liệu. |
| BR-048-7 | Yêu cầu AI chỉ tạo job sinh câu hỏi; câu hỏi sinh ra chưa được đưa ngay cho người học. Câu hỏi phải qua hàng đợi duyệt trước khi sử dụng. |
| BR-048-8 | Nếu tạo job thất bại, hệ thống không được trừ quota của người học. |

## API · DB

```
POST /api/ai/generate-practice
GET  /api/ai/jobs/{id}
```

`user_knowledge_state` · `knowledge_points` (đọc) · `ai_generation_jobs` · `feature_usage` · `user_credits` · `credit_transactions` (ghi)

> ⚠️ **Lệch tài liệu:** feature tree 3.3 ghi bảng `ai_usage_quota`, nhưng danh sách 40 bảng
> `learning` **không có** bảng đó — có `feature_usage` thay. Cần thống nhất tên.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Còn lượt, có điểm yếu | 202 `job_id`, lượt giảm 1 |
| T2 | Hết lượt và điểm | 402, **lượt không đổi**, không có job |
| T3 | Đã có job `QUEUED` | 409, trả job cũ, không trừ thêm |
| T4 | Tạo job lỗi sau khi trừ | **Rollback** — lượt không giảm |
| T5 | Job thứ 11 trong ngày | 429 |
| T6 | Chưa học gì | 422 `NO_WEAK_POINTS` |
| T7 | Mỗi lần trừ điểm | `credit_transactions` có `balance_before` và `balance_after` |

---

# UC-049 · AI sinh câu hỏi vào hàng đợi duyệt

| | |
|---|---|
| **UC-ID** | UC-049 · **Actor** `SYSTEM` · **Pri** P1 · **Scope** MVP · **FT** 3.3 |

## Mô tả

Worker nền: gọi API AI, nhận câu hỏi, validate, ghi vào `questions` với
`status = PENDING_REVIEW`. **Không** đến người học tới khi `TEACHER` duyệt (UC-108).

## Tiền điều kiện

1. `ai_generation_jobs` có dòng `status = QUEUED`
2. API AI khả dụng, API key trong biến môi trường (không trong code)
3. Điểm kiến thức của job còn tồn tại

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Thành công | `questions` thêm N dòng `PENDING_REVIEW`; job `COMPLETED` |
| AI lỗi | Job `FAILED` sau khi retry hết; kích hoạt hoàn lượt (UC-097) |
| Câu không hợp lệ | Loại câu đó, giữ các câu còn lại |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Worker | Lấy job `QUEUED`, đổi `RUNNING` (`SELECT FOR UPDATE SKIP LOCKED`) |
| 2 | Worker | Dựng prompt từ điểm kiến thức + cấp HSK + dạng câu hỏi |
| 3 | Worker | Gọi API AI (timeout 30s) |
| 4 | Worker | Parse JSON trả về |
| 5 | Worker | **Validate từng câu**: đúng 4 đáp án, đúng 1 `is_correct`, có `explanation`, không trùng câu đã có |
| 6 | Worker | `INSERT questions` với `status = PENDING_REVIEW`, `source = AI`, `generated_by_job_id` |
| 7 | Worker | Gắn `question_knowledge_points` |
| 8 | Worker | Job `COMPLETED`, ghi số câu sinh được |
| 9 | Worker | Thông báo `TEACHER` có câu chờ duyệt |

## Luồng thay thế

**A1 — AI trả câu không đúng format**
Loại câu đó. Nếu **mọi** câu đều lỗi → job `FAILED`, hoàn lượt.

**A2 — AI trả câu trùng câu đã có**
Loại. Nếu sinh 10 câu mà 8 trùng, chỉ ghi 2.

**A3 — Timeout**
Retry tối đa 3 lần, backoff 5s/15s/45s. Hết → `FAILED`.

**A4 — Worker chết giữa lúc chạy**
Job kẹt `RUNNING`. Scheduler quét job `RUNNING` quá 10 phút → về `QUEUED`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `AI_API_TIMEOUT` | — | Quá 30s | Retry 3 lần (A3) |
| `AI_API_RATE_LIMITED` | — | Nhà cung cấp chặn | Backoff dài hơn, giữ `QUEUED` |
| `AI_RESPONSE_MALFORMED` | — | JSON sai | Loại câu (A1) |
| `AI_QUESTION_INVALID` | — | Sai số đáp án đúng, thiếu `explanation` | 🔴 **Loại tuyệt đối** — xem ghi chú |
| `AI_QUESTION_DUPLICATE` | — | Trùng câu đã có | Loại (A2) |
| `STATUS_NOT_PENDING_REVIEW` | — | Ghi `status = APPROVED` | 🔴 **Vi phạm luật kiểm duyệt** — xem ghi chú |
| `JOB_STUCK_RUNNING` | — | Worker chết | Scheduler reset (A4) |
| `API_KEY_IN_CODE` | — | Key hardcode | 🔴 Vi phạm constitution — chặn merge |
| `AI_HALLUCINATED_CONTENT` | — | Câu sai ngữ pháp/nghĩa nhưng đúng format | 🔴 Xem ghi chú |
| `ALL_QUESTIONS_REJECTED` | — | 0 câu qua validate | Job `FAILED`, hoàn lượt |

> 🔴 **`STATUS_NOT_PENDING_REVIEW` là ràng buộc lõi của tính năng 3.3.** Feature tree: "Mọi câu
> AI sinh vào hàng đợi duyệt. **Chỉ câu đã duyệt mới đến người học**".
> Nếu bước 6 ghi `APPROVED` (dù chỉ do một lần sửa code cho "tiện test"), câu AI đi thẳng vào
> UC-037 và UC-047. Hội đồng bảo vệ sẽ hỏi về kiểm duyệt nội dung AI, và câu trả lời "chúng em
> có hàng đợi duyệt" sẽ sai.
> **Cách chặn cứng:** `CHECK (source <> 'AI' OR status <> 'APPROVED' OR reviewed_by IS NOT NULL)`
> — ràng buộc ở DB, không dựa vào code nhớ.

> 🔴 **`AI_HALLUCINATED_CONTENT` là rủi ro không thể validate bằng code.** AI trả câu đúng
> format hoàn hảo: 4 đáp án, 1 đúng, có lời giải — nhưng câu tiếng Trung **sai ngữ pháp** hoặc
> lời giải **sai kiến thức**. Không có cách tự động phát hiện.
> Đây chính là lý do có UC-108 (`TEACHER` duyệt). **Người duyệt là lớp bảo vệ duy nhất** —
> nên không được có đường nào bỏ qua nó.

> ⚠️ **`AI_QUESTION_INVALID` nối với `MALFORMED_QUESTION` ở UC-019.** Nếu validate bước 5 lỏng,
> câu 2 đáp án đúng vào kho → người học bị chấm sai oan. Validate ở đây là **rẻ nhất**: chặn
> tại cửa vào, không phải xử lý hậu quả ở 4 UC khác.

## Business rule

| # | Rule |
| --- | --- |
| BR-049-1 | Mọi câu hỏi do AI sinh ra phải được lưu ở trạng thái `PENDING_REVIEW`. Không câu hỏi AI nào được đưa trực tiếp cho người học khi chưa được duyệt. |
| BR-049-2 | Câu hỏi AI chỉ được lưu nếu vượt qua kiểm tra định dạng tối thiểu, bao gồm có nội dung câu hỏi, đủ đáp án theo loại câu, đúng một đáp án đúng nếu là trắc nghiệm, có lời giải và có nhãn điểm kiến thức. |
| BR-049-3 | Câu hỏi trùng với câu đã có trong kho không được tạo thêm bản trùng. |
| BR-049-4 | Câu hỏi AI phải lưu được nguồn gốc sinh ra, bao gồm job sinh bài và trạng thái là câu do AI tạo, để phục vụ kiểm duyệt và truy vết. |
| BR-049-5 | Nếu AI trả về một phần câu hợp lệ và một phần câu lỗi, hệ thống chỉ giữ các câu hợp lệ và loại bỏ các câu lỗi. |
| BR-049-6 | Nếu không có câu nào hợp lệ sau khi validate, job sinh bài được xem là thất bại. |
| BR-049-7 | Job AI thất bại do lỗi nhà cung cấp phải được retry theo chính sách giới hạn. Sau khi hết retry vẫn lỗi, job chuyển sang thất bại và quota của người học phải được xử lý lại theo chính sách đã chốt. |
| BR-049-8 | API key hoặc thông tin bí mật dùng để gọi AI không được lưu trong code hoặc ghi ra log. |
| BR-049-9 | Log của job AI không được chứa thông tin nhận dạng cá nhân hoặc dữ liệu nhạy cảm của người học. |

## API · DB

Không endpoint — worker nền.

`ai_generation_jobs` (đọc + ghi) · `questions` · `question_options` · `question_knowledge_points` (ghi) · `knowledge_points` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | AI trả 10 câu hợp lệ | 10 dòng `PENDING_REVIEW`, job `COMPLETED` |
| T2 | Câu ghi `status = APPROVED` | **DB constraint chặn** |
| T3 | AI trả câu 2 đáp án đúng | Câu bị loại |
| T4 | AI trả câu trùng | Loại, ghi số đã loại |
| T5 | AI timeout 3 lần | Job `FAILED`, lượt được hoàn |
| T6 | Hai worker cùng lấy job | Chỉ một xử lý (`SKIP LOCKED`) |
| T7 | Worker chết lúc `RUNNING` | Sau 10 phút về `QUEUED` |
| T8 | Câu `PENDING_REVIEW` trong kho | **Không** xuất hiện ở UC-037 |

---

# UC-050 · Tái sử dụng câu hỏi đã duyệt cho người khác

| | |
|---|---|
| **UC-ID** | UC-050 · **Actor** `SYSTEM` · **Pri** P2 · **Scope** MVP · **FT** 3.3 |

## Mô tả

Câu AI sinh cho người A, sau khi `TEACHER` duyệt, vào **kho chung** — người B cùng điểm yếu
dùng lại, không cần sinh mới. Tiết kiệm chi phí API và làm kho giàu dần.

## Tiền điều kiện

1. Câu đã `status = APPROVED` (qua UC-108)
2. Câu có `question_knowledge_points`
3. Người B có điểm yếu trùng

## Hậu điều kiện

Không tạo dữ liệu mới — chỉ là cách **truy vấn** ở UC-037, UC-041, UC-047.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Người B | Yêu cầu bài luyện (UC-037/041/047) |
| 2 | System | Truy vấn `questions` theo `knowledge_point_id`, `status = APPROVED` |
| 3 | System | **Không** lọc theo `generated_for_user_id` — lấy cả câu sinh cho người khác |
| 4 | System | Loại câu người B **đã làm** trong 7 ngày qua |
| 5 | System | Trả bộ câu |
| 6 | System | Nếu kho đã đủ → **không** gợi ý sinh mới (tiết kiệm) |

## Luồng thay thế

**A1 — Người B đã làm hết câu trong kho**
Bỏ lọc 7 ngày, cho làm lại. Hoặc gợi ý UC-048.

**A2 — Câu sinh riêng theo ngữ cảnh người A**
Ví dụ prompt có "dựa trên lỗi bạn vừa mắc ở câu X". Câu đó **không nên** tái sử dụng — cần
cờ `reusable = false`.

**A3 — Kho đủ câu cho điểm yếu**
UC-048 vẫn cho gọi nhưng client hiện "kho đã có N câu, bạn muốn làm luôn không?" để đỡ tốn lượt.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `PERSONAL_CONTEXT_LEAKED` | — | 🔴 Câu chứa thông tin người A | Xem ghi chú |
| `NOT_REUSABLE` | — | Câu có `reusable = false` | Loại khỏi kho chung (A2) |
| `NO_REUSABLE_QUESTIONS` | 200 (rỗng) | Kho rỗng cho điểm đó | Gợi ý UC-048 |
| `ALL_QUESTIONS_RECENTLY_DONE` | 200 | Đã làm hết trong 7 ngày | Bỏ lọc, cho làm lại (A1) |
| `UNAPPROVED_IN_SHARED_POOL` | — | Câu `PENDING_REVIEW` lọt vào kho chung | 🔴 Vi phạm luật kiểm duyệt |
| `REUSABLE_FLAG_MISSING` | — | Chưa có cột `reusable` | ⚠️ Khoảng trống thiết kế |

> 🔴 **`PERSONAL_CONTEXT_LEAKED` là rủi ro riêng tư ít ai nghĩ tới.** Nếu prompt UC-049 có
> dạng "sinh câu hỏi cho học viên đang yếu về 把-structure, vừa sai câu về chủ đề gia đình",
> AI có thể đưa ngữ cảnh đó vào **đề bài hoặc lời giải**. Câu đó vào kho chung là người B đọc
> được thông tin học tập của người A.
> Constitution đã có luật PII masking. Ở đây cụ thể: **prompt không chứa dữ liệu nhận dạng
> người học**, chỉ chứa điểm kiến thức và cấp HSK.

> ⚠️ **`REUSABLE_FLAG_MISSING`:** hiện `questions` chưa có cột `reusable`. Không có cột đó thì
> **mọi** câu AI đều vào kho chung — bao gồm câu sinh theo ngữ cảnh riêng. Cần thêm cột, hoặc
> chốt luật "prompt không bao giờ có ngữ cảnh riêng" để mọi câu đều tái dùng được.

## Business rule

| # | Rule |
| --- | --- |
| BR-050-1 | Câu hỏi do AI sinh chỉ được đưa vào kho dùng chung sau khi đã được duyệt. |
| BR-050-2 | Câu hỏi đã duyệt có thể được tái sử dụng cho nhiều người học nếu cùng điểm kiến thức và cùng nhu cầu luyện tập. |
| BR-050-3 | Hệ thống không sinh câu hỏi mới bằng AI nếu kho đã có đủ câu hỏi đã duyệt phù hợp với điểm yếu của người học. |
| BR-050-4 | Khi chọn câu luyện, hệ thống nên ưu tiên câu người học chưa làm gần đây để tránh lặp lại quá nhanh. Trong MVP, có thể loại các câu đã làm trong 7 ngày gần nhất nếu kho còn đủ câu. |
| BR-050-5 | Prompt dùng để sinh câu hỏi không được chứa tên, email hoặc thông tin nhận dạng cá nhân của người học. |
| BR-050-6 | Nếu câu hỏi được sinh theo ngữ cảnh riêng của một người học, câu đó không được đưa vào kho dùng chung trừ khi đã được xác nhận là không chứa thông tin riêng tư. |
| BR-050-7 | Câu hỏi chưa duyệt hoặc bị từ chối không bao giờ được xuất hiện trong kho dùng chung cho người học. |

## API · DB

Không endpoint riêng — là điều kiện truy vấn ở UC-037/041/047.

`questions` · `question_knowledge_points` · `attempt_answers` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Câu AI sinh cho A, đã duyệt | Người B **lấy được** |
| T2 | Câu `PENDING_REVIEW` | Người B **không** lấy được |
| T3 | B đã làm câu X hôm qua | X không trong bộ đầu |
| T4 | B làm hết kho | Bỏ lọc 7 ngày |
| T5 | Câu `reusable = false` | Không vào kho chung |
| T6 | Đọc prompt đã gửi AI | **Không** chứa tên/email người học |

---

# UC-051 · Xem thống kê tiến độ cá nhân

| | |
|---|---|
| **UC-ID** | UC-051 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 3.4 |

## Mô tả

Dashboard: ngày học liên tiếp · tổng từ/chữ đã thuộc · điểm thi qua các lần · % hoàn thành
từng chủ đề. Nghiệm thu: "số liệu khớp hoạt động thật; đọc được trên màn hình điện thoại".

> **Lưu ý tài liệu:** mục "Analytics" của app cũ là dashboard GA4 đo lưu lượng web —
> **khác hoàn toàn** UC này.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có ít nhất một `study_sessions` (nếu chưa có thì hiện màn trống có hướng dẫn)

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang "Tiến độ của tôi" |
| 2 | System | `GET /api/me/progress` |
| 3 | System | Đếm `study_sessions` theo ngày → tính `current_streak`, `longest_streak` |
| 4 | System | Đếm `user_knowledge_state` `mastery ≥ 0.9` theo loại (từ/chữ/ngữ pháp) |
| 5 | System | Lấy `attempts` đã `SUBMITTED`, sắp theo thời gian → chuỗi điểm thi |
| 6 | System | Lấy `user_topic_progress` → % từng chủ đề |
| 7 | System | Trả gói dữ liệu tổng hợp |
| 8 | Client | Hiện dashboard responsive |

## Luồng thay thế

**A1 — `USER` mới, chưa học gì**
Mọi số 0, `streak = 0`. Hiện hướng dẫn bắt đầu thay vì bảng số 0.

**A2 — Học nhiều thiết bị cùng ngày**
`streak` tính theo **ngày** (giờ Việt Nam), không theo thiết bị. Học trên web và mobile cùng
ngày vẫn là 1 ngày.

**A3 — Học lúc 23h50 và 00h10**
Hai ngày khác nhau → `streak` +2. Đúng định nghĩa "ngày liên tiếp".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_DATA` | 200 | Chưa học gì | Không phải lỗi — màn trống có hướng dẫn (A1) |
| `STREAK_TIMEZONE_ERROR` | — | Tính `streak` bằng UTC thay vì giờ Việt Nam | 🔴 Xem ghi chú |
| `STATS_MISMATCH_REAL_ACTIVITY` | — | Số liệu không khớp hoạt động thật | 🔴 Vi phạm nghiệm thu 3.4 — xem ghi chú |
| `SLOW_QUERY` | — | Quét toàn bảng `study_sessions` | ⚠️ Cần index `(user_id, created_at)` |
| `OTHER_USER_DATA_LEAKED` | — | Truy vấn thiếu `user_id` | 🔴 Cùng lỗi JOIN nhóm 2 |
| `DIVIDE_BY_ZERO` | 500 | Tính % khi tổng = 0 | Trả 0, không lỗi |

> 🔴 **`STREAK_TIMEZONE_ERROR` — bug người dùng phát hiện ngay và rất khó thuyết phục.**
> `study_sessions.created_at` là `TIMESTAMPTZ` UTC (đúng). Nhưng "ngày học" phải tính theo giờ
> Việt Nam. Học lúc 06h00 giờ VN = 23h00 UTC ngày hôm trước. Nhóm theo `DATE(created_at)` (UTC)
> thì buổi học sáng bị tính vào ngày hôm trước → **streak đứt oan**.
> **Đúng:** `DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')`.
> Đây là đúng chỗ **phải** dùng giờ Việt Nam, khác với FSRS (UC-044) phải dùng UTC.

> 🔴 **`STATS_MISMATCH_REAL_ACTIVITY` là nghiệm thu, không phải exception thường.** Nếu
> `user_topic_progress` lệch (`PROGRESS_PERCENT_MISMATCH` UC-025), dashboard hiện số sai —
> và đây là màn hình người học **tin nhất**. Số sai ở đây phá vỡ niềm tin vào cả hệ thống
> adaptive.

## Business rule

| # | Rule |
| --- | --- |
| BR-051-1 | Dashboard tiến độ chỉ hiển thị dữ liệu của người học đang đăng nhập. Không được tổng hợp hoặc lộ dữ liệu của người học khác. |
| BR-051-2 | Ngày học liên tiếp được tính theo ngày giờ Việt Nam trong MVP. Một ngày có học trên nhiều thiết bị vẫn chỉ tính là một ngày học. |
| BR-051-3 | Một điểm kiến thức được xem là “đã thuộc” khi mastery đạt từ 0.9 trở lên. |
| BR-051-4 | Dashboard phải thể hiện các chỉ số chính của người học, gồm ngày học liên tiếp, số điểm kiến thức đã thuộc, điểm thi/luyện qua các lần và tiến độ chủ đề. |
| BR-051-5 | Nếu người học chưa có dữ liệu, hệ thống hiển thị màn hướng dẫn bắt đầu học, không hiển thị một dashboard toàn số 0 gây khó hiểu. |
| BR-051-6 | Số liệu trên dashboard phải khớp với hoạt động học thực tế đã ghi nhận trong hệ thống. |
| BR-051-7 | Màn hình thống kê phải đọc được trên thiết bị di động, vì người học có thể dùng cả web và mobile. |

## API · DB

```
GET /api/me/progress
```

`study_sessions` · `attempts` · `user_knowledge_state` · `user_topic_progress` (đọc)

> **Index cần:** `study_sessions(user_id, created_at)`, `attempts(user_id, submitted_at)`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Học 5 ngày liên tiếp | `current_streak = 5` |
| T2 | Học 06h00 giờ VN | Tính vào **đúng ngày đó**, streak không đứt |
| T3 | Học web + mobile cùng ngày | `streak` +1, không +2 |
| T4 | `USER` mới | Màn hướng dẫn |
| T5 | Hai user cùng học | Mỗi người thấy số **của mình** |
| T6 | Nghỉ 1 ngày rồi học | `current_streak` reset, `longest_streak` giữ |
| T7 | Tổng từ = 0 | % = 0, không chia 0 |

---

# UC-052 · Xem biểu đồ tiến bộ 7/30/90 ngày

| | |
|---|---|
| **UC-ID** | UC-052 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 3.4 |

## Mô tả

Biểu đồ đường: số điểm kiến thức đã thuộc, thời gian học, số câu trả lời đúng — theo 7, 30,
hoặc 90 ngày.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có `study_sessions` trong khoảng chọn

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn khoảng 7/30/90 ngày |
| 2 | System | `GET /api/me/progress/chart?days=30` |
| 3 | System | Nhóm `study_sessions` theo ngày (giờ Việt Nam) |
| 4 | System | Với mỗi ngày: đếm phút học, số câu đúng/sai, số điểm kiến thức mới thuộc |
| 5 | System | **Điền 0 cho ngày không học** — biểu đồ phải liên tục |
| 6 | System | Trả mảng đúng `days` phần tử |
| 7 | Client | Vẽ biểu đồ |

## Luồng thay thế

**A1 — Mới dùng 3 ngày, chọn 90 ngày**
87 ngày đầu = 0. Client có thể hiện "bạn mới bắt đầu 3 ngày".

**A2 — Chọn khoảng khác 7/30/90**
Chỉ nhận 3 giá trị này. Khác → 400.

**A3 — Xem trên điện thoại**
90 điểm dữ liệu quá dày. Client gộp theo tuần khi `days = 90`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INVALID_DAYS` | 400 | `days` ngoài `{7, 30, 90}` | Chặn — tránh `days=10000` quét cả bảng |
| `MISSING_DAYS_NOT_FILLED` | — | Ngày không học bị bỏ khỏi mảng | 🔴 Xem ghi chú |
| `TIMEZONE_GROUPING_ERROR` | — | Nhóm theo UTC | Cùng lỗi UC-051 |
| `SLOW_QUERY` | — | Thiếu index | Cần `(user_id, created_at)` |
| `OTHER_USER_DATA_LEAKED` | — | Thiếu lọc `user_id` | Chặn |
| `FUTURE_DATE_IN_RESULT` | — | Có ngày ở tương lai | Lệch giờ máy chủ — kẹp tới `today` |

> 🔴 **`MISSING_DAYS_NOT_FILLED` làm biểu đồ nói dối.** `GROUP BY date` chỉ trả ngày **có**
> dữ liệu. Người học học ngày 1 và ngày 30, nghỉ 28 ngày giữa → mảng có 2 phần tử → client vẽ
> đường thẳng nối hai điểm, trông như **học đều suốt tháng**.
> Phải điền 0 cho ngày trống ở bước 5 — hoặc ở SQL (`generate_series`) hoặc ở Java. Không làm
> thì biểu đồ sai hoàn toàn về mặt trực quan.

## Business rule

| # | Rule |
| --- | --- |
| BR-052-1 | Biểu đồ tiến bộ chỉ hỗ trợ ba khoảng thời gian trong MVP: 7 ngày, 30 ngày và 90 ngày. |
| BR-052-2 | Dữ liệu trả về cho biểu đồ phải có đủ số ngày tương ứng với khoảng đã chọn. Ngày không học phải được trả về với giá trị 0. |
| BR-052-3 | Dữ liệu biểu đồ phải được nhóm theo ngày giờ Việt Nam để thống nhất với cách tính ngày học liên tiếp. |
| BR-052-4 | Biểu đồ không được chứa ngày trong tương lai. |
| BR-052-5 | Biểu đồ chỉ lấy dữ liệu của người học đang đăng nhập. |
| BR-052-6 | Khi hiển thị 90 ngày trên màn hình nhỏ, client có thể gộp dữ liệu theo tuần để dễ đọc, nhưng dữ liệu gốc vẫn phải đúng theo ngày. |

## API · DB

```
GET /api/me/progress/chart?days={7|30|90}
```

`study_sessions` · `attempt_answers` · `user_knowledge_state` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `days=30`, học 10 ngày | Mảng **30** phần tử, 20 phần tử = 0 |
| T2 | `days=365` | 400 |
| T3 | Học ngày 1 và ngày 30 | 28 phần tử giữa = 0 |
| T4 | Học 06h00 giờ VN | Vào đúng ngày đó |
| T5 | `USER` mới | 30 phần tử đều 0 |
| T6 | Mảng trả về | Không phần tử nào sau `today` |

---

# UC-053 · Xem bản đồ mạnh-yếu theo kỹ năng

| | |
|---|---|
| **UC-ID** | UC-053 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 3.4 |

## Mô tả

Biểu đồ radar: mastery trung bình theo **kỹ năng** (nghe · đọc · viết · từ vựng · ngữ pháp ·
chữ Hán). Nhìn một cái thấy ngay mình lệch chỗ nào.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `knowledge_points` có **phân loại kỹ năng**
3. Có `user_knowledge_state`

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở tab "Bản đồ kỹ năng" |
| 2 | System | `GET /api/me/skill-map` |
| 3 | System | Join `user_knowledge_state` với `knowledge_points` |
| 4 | System | Nhóm theo loại kỹ năng, tính `AVG(mastery)` |
| 5 | System | Đếm số điểm mỗi kỹ năng (để biết số liệu có đáng tin) |
| 6 | System | Trả `[{skill, avg_mastery, point_count, learned_count}]` |
| 7 | Client | Vẽ radar |

## Luồng thay thế

**A1 — Kỹ năng chưa học điểm nào**
`avg_mastery = null`, `learned_count = 0`. Client hiện vùng trống, **không** hiện 0 — chưa học
khác với học mà yếu.

**A2 — Kỹ năng chỉ có 1 điểm đã học**
Vẫn trả nhưng `point_count = 1`. Client hiện chú thích "dữ liệu còn ít".

**A3 — So sánh với mức trung bình người học cùng cấp**
V2 — cần dữ liệu tổng hợp toàn hệ thống.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_SKILL_CLASSIFICATION` | 500 | 🔴 `knowledge_points` chưa phân loại kỹ năng | Xem ghi chú |
| `SKILL_WITH_NO_DATA` | 200 | Kỹ năng chưa học | `null`, không phải 0 (A1) |
| `ZERO_VS_NULL_CONFUSION` | — | Hiện 0 cho kỹ năng chưa học | 🔴 Xem ghi chú |
| `MISLEADING_AVERAGE` | — | Trung bình từ 1 điểm | Trả kèm `point_count` (A2) |
| `OTHER_USER_DATA_LEAKED` | — | Thiếu lọc `user_id` | Chặn |
| `UNKNOWN_SKILL_TYPE` | — | Giá trị kỹ năng lạ | Gộp vào "Khác", ghi log |

> 🔴 **`NO_SKILL_CLASSIFICATION` chặn cả UC này và `by_skill` ở UC-038.** `knowledge_points`
> phải có cột phân loại (`skill_type`). Nếu không có, cả hai màn hình cùng trống, và
> `SKILL_BREAKDOWN_UNAVAILABLE` (UC-038) là cùng nguyên nhân gốc.
> **Cần chốt danh sách kỹ năng** trước khi nhập `knowledge_points` — sửa sau là phải nhập lại
> (`question_knowledge_points` nằm trong "5 bảng không được đụng").

> 🔴 **`ZERO_VS_NULL_CONFUSION` là lỗi diễn giải dữ liệu, không phải lỗi code.** Radar hiện
> kỹ năng "Viết" = 0 → người học nghĩ mình viết **rất tệ**. Thực tế chưa học phần viết bao giờ.
> Phân biệt `null` (chưa có dữ liệu) và `0` (có dữ liệu, mastery thấp) là **bắt buộc** — đây
> là loại lỗi làm người học đưa ra quyết định học sai.

## Business rule

| # | Rule |
| --- | --- |
| BR-053-1 | Mỗi điểm kiến thức cần có phân loại kỹ năng để hệ thống tổng hợp bản đồ mạnh-yếu, ví dụ nghe, đọc, viết, từ vựng, ngữ pháp hoặc chữ Hán. |
| BR-053-2 | Danh sách kỹ năng phải được chốt trước khi nhập dữ liệu điểm kiến thức chính thức. |
| BR-053-3 | Bản đồ kỹ năng chỉ tính trên dữ liệu của người học đang đăng nhập. |
| BR-053-4 | Kỹ năng chưa có dữ liệu học tập phải trả giá trị `null`, không trả 0. `null` nghĩa là chưa có dữ liệu; 0 nghĩa là đã học nhưng rất yếu. |
| BR-053-5 | Hệ thống phải trả kèm số lượng điểm kiến thức đã học trong từng kỹ năng để người học biết số liệu có đáng tin hay chưa. |
| BR-053-6 | Nếu kỹ năng có quá ít dữ liệu, giao diện phải thể hiện đây là dữ liệu còn ít, không kết luận người học mạnh/yếu quá sớm. |
| BR-053-7 | Giá trị kỹ năng không xác định phải được ghi log để quản trị nội dung sửa dữ liệu. |

## API · DB

```
GET /api/me/skill-map
```

`user_knowledge_state` · `knowledge_points` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Học đủ 6 kỹ năng | 6 giá trị `avg_mastery` |
| T2 | Chưa học viết | Viết = `null`, không phải 0 |
| T3 | Kỹ năng có 1 điểm | Trả kèm `point_count = 1` |
| T4 | `knowledge_points` thiếu `skill_type` | Ứng dụng báo lỗi rõ, không trả radar trống im lặng |
| T5 | Hai user | Mỗi người thấy radar của mình |

---

# UC-054 · Cài đặt giờ nhắc học và kênh nhận

| | |
|---|---|
| **UC-ID** | UC-054 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 3.5 |

## Mô tả

Chọn giờ nhắc và bật/tắt từng kênh (web · email · đẩy mobile). Nghiệm thu: "đặt 20h thì nhận
nhắc lúc 20h **giờ Việt Nam**".

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Email đã xác thực (nếu bật kênh email)

## Hậu điều kiện

`user_notification_settings` cập nhật; UC-055 dùng ngay từ lần chạy kế tiếp.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở "Cài đặt nhắc học" |
| 2 | System | `GET /api/me/notification-settings` — trả cài đặt hoặc mặc định |
| 3 | `USER` | Chọn giờ (ví dụ 20:00), bật email, tắt web |
| 4 | Client | `PUT /api/me/notification-settings` |
| 5 | System | Validate giờ `00:00`–`23:59` |
| 6 | System | Nếu bật email: kiểm `email_verified_at IS NOT NULL` |
| 7 | System | Nếu bật đẩy mobile: kiểm có `user_devices` |
| 8 | System | Ghi (upsert) |
| 9 | Client | Hiện "đã lưu, bạn sẽ nhận nhắc lúc 20:00" |

## Luồng thay thế

**A1 — Chưa có cài đặt**
Trả mặc định: 20:00, web bật, email tắt, đẩy tắt. Chưa ghi DB tới khi người học lưu.

**A2 — Bật email mà chưa xác thực**
Trả `EMAIL_NOT_VERIFIED`, gợi ý gửi lại link (UC-002).

**A3 — Tắt hết kênh**
Cho phép — đó là "không muốn bị nhắc". Không ép bật ít nhất một.

**A4 — Bật đẩy mobile mà chưa có thiết bị**
Lưu cài đặt nhưng cảnh báo "cần mở app trên điện thoại một lần".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INVALID_TIME_FORMAT` | 400 | Không phải `HH:mm` | Chặn |
| `EMAIL_NOT_VERIFIED` | 422 | Bật email khi chưa xác thực | Chặn kênh đó, gợi ý xác thực (A2) |
| `NO_DEVICE_REGISTERED` | 200 | Bật đẩy chưa có thiết bị | Lưu + cảnh báo (A4) |
| `USER_DEVICES_TABLE_MISSING` | 500 | 🔴 Bảng `user_devices` **không tồn tại** | Xem ghi chú |
| `TIMEZONE_NOT_STORED` | — | Chỉ lưu `HH:mm` không lưu múi giờ | 🔴 Xem ghi chú |
| `ALL_CHANNELS_DISABLED` | 200 | Tắt hết | Không phải lỗi (A3) |
| `SETTINGS_NOT_OWNED` | 403 | Sửa cài đặt người khác | IDOR — endpoint dùng `/me`, không nhận `user_id` |

> 🔴 **`USER_DEVICES_TABLE_MISSING` — khoảng trống đã ghi trong catalog.** Feature tree 3.5 ghi
> bảng `user_devices`, nhưng danh sách 40 bảng `learning` **không có** bảng này.
> Hệ quả: kênh đẩy mobile (V2) không có chỗ lưu device token. MVP chỉ web + email nên **chưa
> chặn**, nhưng phải thêm bảng trước khi làm V2.

> 🔴 **`TIMEZONE_NOT_STORED` — vì sao phải lưu múi giờ, không chỉ giờ.** Lưu `20:00` rồi
> scheduler chạy với `zone = "Asia/Ho_Chi_Minh"` (constitution §6) là **đúng cho người ở Việt
> Nam**. Nhưng người học đi du học Trung Quốc thì 20h giờ VN là 21h giờ họ.
> **Quyết định cần chốt:** hệ thống chỉ phục vụ người ở Việt Nam (đơn giản, đúng với "cho người
> Việt") hay lưu `timezone` mỗi người (linh hoạt, thêm phức tạp)?
> **Khuyến nghị MVP:** cố định giờ Việt Nam, ghi rõ trong tài liệu. Nhưng ghi nhận giới hạn này
> để hội đồng không hỏi bất ngờ.

## Business rule

| # | Rule |
| --- | --- |
| BR-054-1 | Trong MVP, giờ nhắc học được hiểu theo giờ Việt Nam. Hệ thống chưa hỗ trợ múi giờ cá nhân cho từng người học. |
| BR-054-2 | Cài đặt mặc định là nhắc lúc 20:00, bật kênh web và tắt kênh email nếu người học chưa từng cấu hình. |
| BR-054-3 | Người học được phép tắt toàn bộ kênh nhắc học. Đây được hiểu là người học không muốn nhận nhắc. |
| BR-054-4 | Nếu bật nhắc qua email, tài khoản phải có email đã xác thực. |
| BR-054-5 | Endpoint cài đặt nhắc học phải lấy người dùng từ phiên đăng nhập hiện tại, không nhận `user_id` từ client. |
| BR-054-6 | MVP chỉ hỗ trợ nhắc qua web và email. Nhắc đẩy mobile là V2 và chỉ triển khai khi đã có nơi lưu thiết bị/token hợp lệ. |
| BR-054-7 | Cài đặt mới có hiệu lực từ lần chạy nhắc học tiếp theo. |

## API · DB

```
GET /api/me/notification-settings
PUT /api/me/notification-settings
```

`user_notification_settings` (đọc + ghi) · `users` (đọc `email_verified_at`) · ⚠️ `user_devices` (**chưa có**)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đặt 20:00, bật email (đã xác thực) | 200, lưu đúng |
| T2 | Bật email chưa xác thực | 422 |
| T3 | Giờ `25:00` | 400 |
| T4 | Tắt hết kênh | 200 |
| T5 | Chưa có cài đặt | Trả mặc định 20:00 |
| T6 | Gửi kèm `user_id` người khác | Bị bỏ qua — dùng token |

---

# UC-055 · Gửi nhắc học tự động đúng giờ

| | |
|---|---|
| **UC-ID** | UC-055 · **Actor** `SYSTEM` · **Pri** P2 · **Scope** MVP · **FT** 3.5 |

## Mô tả

Tác vụ định kỳ: mỗi giờ quét người học đến giờ nhắc **và** có điểm kiến thức đến hạn ôn, gửi
nhắc qua kênh đã bật.

## Tiền điều kiện

1. Scheduler chạy với `zone = "Asia/Ho_Chi_Minh"` (constitution §6 bắt buộc)
2. `user_notification_settings` có người bật kênh
3. Có `user_knowledge_state` với `next_review_at ≤ now()`

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Gửi được | Nhắc đến kênh; ghi log đã gửi để không gửi trùng |
| Không có gì ôn | **Không gửi** — nhắc rỗng làm người học tắt thông báo |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Scheduler | Chạy đầu mỗi giờ, `zone = "Asia/Ho_Chi_Minh"` |
| 2 | System | Lấy người có `reminder_time` giờ hiện tại, còn kênh bật |
| 3 | System | Với mỗi người: đếm `user_knowledge_state` `next_review_at ≤ now()` |
| 4 | System | Đếm = 0 → **bỏ qua** |
| 5 | System | Kiểm đã gửi trong 20h qua chưa (chống trùng) |
| 6 | System | Dựng nội dung: "Bạn có N điểm cần ôn hôm nay" |
| 7 | System | Gửi theo từng kênh đã bật |
| 8 | System | Ghi log đã gửi |

## Luồng thay thế

**A1 — Email gửi lỗi**
Retry 2 lần. Hết → ghi log, **không** chặn các kênh khác và người khác.

**A2 — Người học đã học hôm nay**
Vẫn gửi nếu còn điểm đến hạn — hoặc chốt luật "đã học hôm nay thì không nhắc". Cần quyết.

**A3 — Nhiều instance ứng dụng**
Cả hai chạy scheduler → gửi trùng. Cần khoá phân tán hoặc chỉ một instance bật scheduler.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `WRONG_TIMEZONE` | — | Scheduler không ghi `zone` | 🔴 Nhắc sai 7 tiếng — xem ghi chú |
| `DUPLICATE_SEND` | — | Nhiều instance cùng chạy | 🔴 Xem ghi chú |
| `EMPTY_REMINDER` | — | Gửi khi không có gì ôn | Bỏ qua (bước 4) — nhắc rỗng làm người học tắt thông báo |
| `EMAIL_SEND_FAILED` | — | SMTP lỗi | Retry 2 lần, không chặn người khác (A1) |
| `SCHEDULER_NOT_RUNNING` | — | Tác vụ không chạy | 🔴 **Im lặng hoàn toàn** — cần health check riêng |
| `BATCH_TOO_LARGE` | — | Hàng nghìn người cùng giờ 20:00 | Chia lô, giới hạn tốc độ gửi |
| `PII_IN_LOG` | — | Ghi email vào log | Vi phạm PII masking — chỉ ghi `user_id` |
| `NO_DEVICE_TOKEN` | — | Bật đẩy chưa có token | Bỏ kênh đó |

> 🔴 **`WRONG_TIMEZONE` là lý do constitution bắt ghi `zone = "Asia/Ho_Chi_Minh"` cho mọi tác
> vụ định kỳ.** Máy chủ chạy UTC. `@Scheduled(cron = "0 0 * * * *")` không có zone thì dùng giờ
> máy chủ → người đặt 20:00 nhận nhắc lúc **03:00 sáng**. Nghiệm thu 3.5 nói rõ "đặt 20h thì
> nhận nhắc lúc 20h giờ Việt Nam" — lỗi này làm sai nghiệm thu, và người học sẽ tắt thông báo
> ngay lần đầu.

> 🔴 **`DUPLICATE_SEND` xảy ra khi chạy 2 instance để demo tính sẵn sàng.** Cả hai đều có
> scheduler → mỗi người nhận 2 email. Bước 5 (kiểm đã gửi trong 20h) giảm được, nhưng hai
> instance chạy **cùng giây** vẫn cả hai thấy "chưa gửi".
> **Cách chặn:** unique constraint trên `(user_id, sent_date, channel)` — instance thứ hai
> insert lỗi, bỏ qua. Đơn giản hơn khoá phân tán.

> ⚠️ **`SCHEDULER_NOT_RUNNING` là loại lỗi tệ nhất vì im lặng.** Không ai báo "tôi không nhận
> được nhắc". Cần một bản ghi "lần chạy cuối" và kiểm tra nó.

## Business rule

| # | Rule |
|---|---|
| BR-055-1 | Tác vụ nhắc học trong MVP chạy theo giờ Việt Nam để khớp với cài đặt giờ nhắc của người học. |
| BR-055-2 | Hệ thống chỉ gửi nhắc học khi người học còn nội dung đến hạn cần ôn. Nếu không có gì cần ôn, hệ thống không gửi nhắc rỗng. |
| BR-055-3 | Mỗi người học chỉ nhận tối đa một nhắc học mỗi ngày trên mỗi kênh. |
| BR-055-4 | Hệ thống phải ghi nhận lịch sử đã gửi nhắc để tránh gửi trùng. Nếu chưa có bảng hoặc nơi lưu lịch sử gửi, chưa nên triển khai gửi nhắc tự động. |
| BR-055-5 | Lỗi gửi ở một kênh hoặc một người học không được làm dừng toàn bộ lượt gửi nhắc cho những người khác. |
| BR-055-6 | Log gửi nhắc không được ghi email hoặc nội dung nhạy cảm; chỉ nên ghi định danh cần thiết như `user_id`, kênh và trạng thái gửi. |
| BR-055-7 | Khi nhiều người cùng đặt một giờ nhắc, hệ thống phải gửi theo lô để tránh quá tải email hoặc notification service. |
| BR-055-8 | MVP gửi nhắc qua web và email. Nhắc đẩy mobile chuyển sang V2. |

## API · DB

Không endpoint — tác vụ định kỳ.

`user_notification_settings` · `user_knowledge_state` · `users` (đọc) · log gửi (ghi)

> ⚠️ **Thiếu bảng:** chưa có bảng nào lưu "đã gửi nhắc cho ai lúc nào". Cần cho BR-055-4.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đặt 20:00, có 5 điểm đến hạn | Nhận nhắc lúc 20:00 **giờ VN** |
| T2 | Đặt 20:00, không có gì ôn | **Không** nhận nhắc |
| T3 | Hai instance cùng chạy | Chỉ **một** nhắc được gửi |
| T4 | Đã nhận nhắc sáng nay | Không nhận lần hai trong ngày |
| T5 | SMTP chết | Người khác **vẫn** nhận được |
| T6 | Kiểm log | Không chứa email, chỉ `user_id` |
| T7 | Scheduler không ghi `zone` | Test phát hiện — nhắc sai giờ |

---

# Tổng hợp exception nhóm 3

## Mười exception quan trọng nhất

| # | UC | Exception | Vì sao |
| --- | --- | --- | --- |
| 1 | UC-046 | `STALE_PERCENT_READ` | Đạt 90% mà không mở khoá và **không có sự kiện nào gọi lại** → người học kẹt vĩnh viễn |
| 2 | UC-049 | `STATUS_NOT_PENDING_REVIEW` | Câu AI chưa duyệt đến người học — vi phạm ràng buộc lõi của 3.3, hội đồng sẽ hỏi |
| 3 | UC-042 | `NOT_IN_TRANSACTION` | Gọi ngoài transaction → nửa bài thi cập nhật mastery, không rollback được |
| 4 | UC-055 | `WRONG_TIMEZONE` | Nhắc lúc 3h sáng thay vì 20h — sai nghiệm thu, người học tắt thông báo ngay |
| 5 | UC-043 | `CROSS_MODULE_TRANSACTION_FAILED` | Mất tính nguyên tử = mất lợi thế chính của Modular Monolith |
| 6 | UC-048 | `QUOTA_DEDUCTED_BUT_JOB_FAILED` | Mất tiền người học → tranh chấp, `FINANCE_ADMIN` xử lý tay |
| 7 | UC-045 | `CIRCULAR_PREREQUISITE` | Chu trình tiên quyết → duyệt đồ thị lặp vô hạn hoặc khoá vĩnh viễn |
| 8 | UC-051 | `STREAK_TIMEZONE_ERROR` | Học sáng bị tính vào hôm trước → streak đứt oan |
| 9 | UC-049 | `AI_HALLUCINATED_CONTENT` | Câu đúng format nhưng sai kiến thức — **không validate được bằng code** |
| 10 | UC-042 | `DEADLOCK_DETECTED` | Hai bài thi khoá điểm kiến thức theo thứ tự khác nhau → deadlock khó tái hiện |

## Bốn nhóm exception lặp lại khắp nhóm 3

| Nhóm | Xuất hiện ở | Bài học |
| --- | --- | --- |
| **Múi giờ** | UC-044 · UC-051 · UC-052 · UC-054 · UC-055 | Hai luật **trái nhau** trong cùng hệ thống: FSRS tính bằng **UTC**, nhắc học và streak tính bằng **giờ Việt Nam**. Nhầm chỗ nào cũng sai âm thầm. Phải ghi rõ chỗ nào dùng gì |
| **Phải nằm trong transaction của caller** | UC-042 · UC-043 · UC-046 | Ba service `SYSTEM` đều được gọi từ trong transaction khác. Gọi sai chỗ là mất nguyên tử mà test thường không bắt. Kiểm `isActualTransactionActive()` ở đầu method |
| **Dữ liệu tính sẵn lệch dữ liệu gốc** | UC-045 · UC-047 · UC-051 | `user_topic_progress.completion_percent` là gốc của cả ba. Lệch một chỗ thì cây chủ đề sai, luyện phần yếu mâu thuẫn, dashboard nói dối. Cần job đối chiếu |
| **`null` khác `0`** | UC-052 · UC-053 | Ngày không học phải là 0 (biểu đồ liên tục); kỹ năng chưa học phải là `null` (không phải yếu). Ngược lại là **làm người học quyết định sai** |

---

# Khoảng trống thiết kế phát hiện ở nhóm 3

| # | Thiếu | UC bị ảnh hưởng | Mức |
| --- | --- | --- | --- |
| 1 | **`knowledge_points` chưa có cột `skill_type`** phân loại kỹ năng | UC-053 · UC-038 | 🔴 Chặn 2 màn hình; sửa sau phải nhập lại dữ liệu |
| 2 | **Chưa chốt danh sách kỹ năng** (nghe/đọc/viết/từ vựng/ngữ pháp/chữ?) | UC-053 | 🔴 Phải chốt trước khi nhập `knowledge_points` |
| 3 | Chưa có kiểm chu trình khi lưu quan hệ tiên quyết chủ đề | UC-045 · UC-046 | 🔴 Treo thuật toán |
| 4 | Chưa có validate "phải có ≥ 1 chủ đề gốc" | UC-045 | 🔴 Cây toàn ổ khoá |
| 5 | Chưa có `CHECK` ở DB chặn câu AI `status = APPROVED` khi chưa duyệt | UC-049 | 🔴 Vi phạm luật kiểm duyệt |
| 6 | Chưa có kiểm `isActualTransactionActive()` trong `MasteryService` | UC-042 · UC-043 · UC-046 | 🔴 Mất tính nguyên tử |
| 7 | Chưa có unique `(user_id, topic_id)` trên `user_topic_progress` | UC-046 | 🔴 Race condition |
| 8 | **Chưa có bảng lưu log gửi nhắc** (`(user_id, sent_date, channel)`) | UC-055 | 🔴 Gửi trùng |
| 9 | **Bảng `user_devices` không tồn tại** dù feature tree 3.5 ghi | UC-054 · UC-055 | ⚠️ Chặn đẩy mobile (V2) |
| 10 | **Lệch tên bảng:** feature tree ghi `ai_usage_quota`, DB có `feature_usage` | UC-048 | ⚠️ Thống nhất tên |
| 11 | `questions` chưa có cột `reusable` | UC-050 | ⚠️ Câu ngữ cảnh riêng vào kho chung |
| 12 | Chưa có cột `self_declared` trong `user_knowledge_state` | UC-042 | ⚠️ Trùng khoảng trống #4 nhóm 1 |
| 13 | Chưa chốt chính sách retry/hoàn lượt khi AI lỗi | UC-048 · UC-049 | ⚠️ Người học mất điểm vì lỗi nhà cung cấp |
| 14 | Chưa chốt **lưu múi giờ mỗi người** hay cố định giờ Việt Nam | UC-054 · UC-055 | ⚠️ Khuyến nghị: cố định, ghi rõ giới hạn |
| 15 | Chưa chốt "đã học hôm nay thì có nhắc nữa không" | UC-055 | ⚠️ Quyết định nghiệp vụ |
| 16 | Chưa có health check cho scheduler | UC-055 | ⚠️ Lỗi im lặng |
| 17 | Thiếu index `study_sessions(user_id, created_at)`, `attempts(user_id, submitted_at)` | UC-051 · UC-052 | ⚠️ Quét toàn bảng |

> **Tám mục 🔴 ở trên chia hai loại:**
> — **Ràng buộc DB còn thiếu** (#5, #7, #8): thêm được trong migration, rẻ.
> — **Quyết định phải chốt trước khi nhập dữ liệu** (#1, #2): sửa sau là nhập lại
> `question_knowledge_points`, mà bảng đó nằm trong "5 bảng tuyệt đối không đụng".
> Loại thứ hai **gấp hơn**.

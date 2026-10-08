# CNHSK — Đặc tả Use Case · Nhóm 1 · Học & luyện tập

> **UC-015 → UC-032** · 18 use case · Tính năng 1.1 → 1.6
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
> **Role:** theo Hiến pháp §Tám actor — 6 role trong DB + `GUEST` + `SYSTEM`

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
| --- | --- | --- | --- | --- | --- |
| UC-015 | Luyện viết chữ Hán theo nét | `USER` | P1 | MVP | 1.1 |
| UC-016 | Luyện viết chế độ "nhớ rồi viết" | `USER` | P2 | MVP | 1.1 |
| UC-017 | Luyện viết chế độ thử thách | `USER` | P2 | MVP | 1.1 |
| UC-018 | Luyện viết chế độ nghe chép | `USER` | P2 | MVP | 1.1 |
| UC-019 | Nhận diện chữ — nhìn chữ chọn nghĩa | `USER` | P1 | MVP | 1.2 |
| UC-020 | Nhận diện chữ — nghe âm chọn chữ | `USER` | P1 | MVP | 1.2 |
| UC-021 | Luyện phát âm theo 8 tầng | `USER` | P1 | MVP | 1.3 |
| UC-022 | Mở tầng phát âm tiếp theo | `SYSTEM` | P1 | MVP | 1.3 |
| UC-023 | Học điểm ngữ pháp HSK | `USER` | P1 | MVP | 1.4 |
| UC-024 | Ôn lại ngữ pháp theo lịch FSRS | `USER` | P1 | MVP | 1.4 |
| UC-025 | Xem danh sách chủ đề từ vựng | `USER` | P0 | MVP | 1.5 |
| UC-026 | Học từ mới trong một chủ đề | `USER` | P0 | MVP | 1.5 |
| UC-027 | Luyện nhận diện từ trong chủ đề | `USER` | P1 | MVP | 1.5 |
| UC-028 | Luyện nghe từ trong chủ đề | `USER` | P1 | MVP | 1.5 |
| UC-029 | Làm bài kiểm tra cuối chủ đề | `USER` | P0 | MVP | 1.5 |
| UC-030 | Xem video có phụ đề tương tác | `USER` | P2 | V2 | 1.6 |
| UC-031 | Bấm từ trong phụ đề xem nghĩa | `USER` | P2 | V2 | 1.6 |
| UC-032 | Lưu từ từ phụ đề vào sổ tay | `USER` | P2 | V2 | 1.6 |

> **Ánh xạ tên bảng khi triển khai:** một số luồng bên dưới còn dùng tên từ thiết kế cũ.
> Theo `docs/reference/database.md`, `topic_words` là `learning.topic_items`;
> `user_knowledge_state` và `user_topic_progress` là các dòng `learning.user_progress`
> với `target_type` tương ứng `KNOWLEDGE_POINT` và `TOPIC`. Không tạo lại các bảng cũ
> chỉ vì tên xuất hiện trong luồng UC.
> `attempt_answers` là `learning.attempt_items`; `question_options` nằm trong
> `learning.questions.options` (JSONB). Các tên cũ chỉ mô tả vai trò dữ liệu.

---

# UC-015 · Luyện viết chữ Hán theo nét

| | |
| --- | --- |
| **UC-ID** | UC-015 |
| **Actor chính** | `USER` |
| **Actor phụ** | — |
| **Priority** | P1 · **Scope** MVP · **FT** 1.1 |
| **Client** | Web · Mobile (shared component) |

## Mô tả

Người học tập viết một chữ Hán theo mẫu và thứ tự nét được hướng dẫn trên màn hình. Đây là chế độ có gợi ý, phù hợp khi mới làm quen với cách viết chữ.

## Tiền điều kiện

1. `USER` đã đăng nhập, access token còn hiệu lực
2. Chữ cần luyện tồn tại trong `characters` và **có dữ liệu nét** (`stroke_data` JSONB)
3. Thư viện `hanzi-writer` 3.7.3 đã load xong trên client

## Hậu điều kiện

| Kết quả | Trạng thái hệ thống |
| --- | --- |
| Thành công | `user_knowledge_state` của điểm kiến thức tương ứng được cập nhật mastery; `study_sessions` ghi thêm một dòng |
| Thất bại | Không ghi gì — không được ghi mastery một phần |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn chữ cần luyện từ danh sách hoặc từ chủ đề |
| 2 | System | `GET /api/characters/{id}` — trả chữ, pinyin, nghĩa, `stroke_data` |
| 3 | Client | `hanzi-writer` render khung chữ và bắt đầu chế độ `quiz` với `showHintAfterMisses: 1` |
| 4 | `USER` | Tô nét thứ nhất theo hướng dẫn |
| 5 | Client | Kiểm nét tại **client** (thư viện tự chấm hình học), hiện nét đúng màu xanh |
| 6 | | Lặp bước 4–5 cho tới nét cuối |
| 7 | Client | Đếm tổng số lần sai, tính `accuracy = 1 − misses / total_strokes` |
| 8 | Client | `POST /api/practice/writing` — body `{character_id, mode: "STROKE_ORDER", accuracy, duration_ms, stroke_misses}` |
| 9 | System | Kiểm hợp lệ, gọi `MasteryService.record(...)` trong **một transaction** |
| 10 | System | Trả `{mastery_before, mastery_after, next_review_at}` |
| 11 | Client | Hiện kết quả và gợi ý chữ tiếp theo |

## Luồng thay thế

**A1 — Người học bỏ giữa (chưa viết hết nét)**
Tại bước 6, `USER` thoát trang. Client gửi `POST /api/practice/writing` với
`completed: false`. Server **ghi `study_sessions` nhưng KHÔNG cập nhật mastery** — viết
nửa vời không phải bằng chứng đã thuộc.

**A2 — Người học bấm "xem lại nét"**
Client phát lại animation. **Không** tính vào `accuracy`, nhưng đặt cờ
`used_animation: true` để server giảm hệ số mastery còn 50%.

**A3 — Chữ chưa có `stroke_data`**
Tại bước 2, nếu `stroke_data IS NULL`, client chuyển sang chế độ "xem chữ + nghĩa" và hiện
thông báo "chữ này chưa có dữ liệu nét". Không phải lỗi — là thiếu dữ liệu.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `CHARACTER_NOT_FOUND` | 404 | `character_id` không tồn tại | Hiện "không tìm thấy chữ", về danh sách |
| `INVALID_ACCURACY` | 400 | `accuracy` ngoài khoảng `[0,1]` | **Chặn ở server.** Client đã bị sửa hoặc có bug |
| `IMPOSSIBLE_DURATION` | 400 | `duration_ms < total_strokes × 200` | Không ai viết nổi 1 nét dưới 0,2 giây → nghi gian lận. Ghi log, **không** cộng mastery |
| `STROKE_DATA_MISSING` | 422 | Chữ không có dữ liệu nét | Chuyển chế độ xem (A3) |
| `UNAUTHORIZED` | 401 | Token hết hạn giữa lúc luyện | Client gọi refresh rồi **gửi lại** request — không mất kết quả người học |
| `MASTERY_UPDATE_FAILED` | 500 | Transaction rollback | Trả lỗi, client giữ kết quả trong memory và cho bấm "thử lại" |
| `RATE_LIMIT_EXCEEDED` | 429 | > 300 lần gửi/giờ | Chặn bot farm mastery |

> 🔴 **`IMPOSSIBLE_DURATION` là chốt chống gian lận quan trọng nhất của UC này.** Chấm nét
> diễn ra ở client (server không nhận toạ độ nét), nên `accuracy` **không đáng tin tuyệt đối**.
> Ta không thể chấm lại, chỉ có thể kiểm tính hợp lý. Vì vậy mastery từ luyện viết phải có
> **trọng số thấp hơn** mastery từ bài kiểm tra chấm ở server (UC-029).

## Business rule

| # | Rule |
| --- | --- |
| BR-015-1 | Chế độ luyện viết theo nét là chế độ cơ bản nhất của luyện viết chữ Hán. Người học được nhìn chữ mẫu và được hệ thống hướng dẫn thứ tự nét. |
| BR-015-2 | Việc chấm nét được thực hiện ở client bằng thư viện viết chữ. Server không nhận tọa độ từng nét, không chấm lại hình học, chỉ nhận kết quả tổng hợp như `accuracy`, `duration_ms`, số lần sai và trạng thái hoàn thành. |
| BR-015-3 | Vì kết quả chấm đến từ client nên server phải kiểm tra tính hợp lý trước khi cập nhật tiến độ, bao gồm giới hạn `accuracy`, thời gian viết tối thiểu và số lần gửi bất thường. |
| BR-015-4 | Chế độ theo nét có hệ số mastery thấp hơn các chế độ khó hơn. Hệ số mặc định là 0.5 vì người học có hướng dẫn trực tiếp. |
| BR-015-5 | Nếu người học dùng animation hoặc gợi ý bổ sung trong lúc luyện, hệ số mastery của lượt đó tiếp tục bị giảm để phản ánh mức hỗ trợ cao hơn. |
| BR-015-6 | Nếu người học thoát giữa chừng hoặc lượt luyện chưa hoàn thành, hệ thống có thể ghi nhận buổi học nhưng không được cộng mastery cho chữ đó. |
| BR-015-7 | Luyện lặp lại cùng một chữ trong khoảng thời gian ngắn không được cộng mastery nhiều lần. Trong MVP, chỉ lượt hợp lệ đầu tiên trong vòng 10 phút được tính mastery. |
| BR-015-8 | Nếu chữ chưa có dữ liệu nét, hệ thống không mở chế độ luyện viết theo nét cho chữ đó; người học chỉ được xem thông tin chữ hoặc chuyển sang hoạt động học khác. |

## API

```
GET  /api/characters/{id}
POST /api/practice/writing
```

## Bảng DB liên quan

| Bảng | Vai trò |
| --- | --- |
| `characters` | Đọc — chữ, pinyin, `stroke_data` |
| `knowledge_points` | Đọc — tìm điểm kiến thức của chữ |
| `user_knowledge_state` | **Ghi** — mastery, `next_review_at` |
| `study_sessions` | **Ghi** — một dòng mỗi lượt luyện |

## Test case

| # | Đầu vào | Kết quả mong đợi |
| --- | --- | --- |
| T1 | Viết đúng hết nét, 30s | 200, mastery tăng theo hệ số 0.5 |
| T2 | `accuracy = 1.5` | 400 `INVALID_ACCURACY` |
| T3 | Chữ 12 nét, `duration_ms = 500` | 400 `IMPOSSIBLE_DURATION`, mastery **không đổi** |
| T4 | `used_animation = true` | mastery tăng bằng nửa T1 |
| T5 | Luyện cùng chữ lần 2 sau 3 phút | 200 nhưng mastery **không đổi** (BR-015-4) |
| T6 | Token hết hạn, refresh rồi gửi lại | 200, kết quả không mất |

---

# UC-016 · Luyện viết chế độ "nhớ rồi viết"

| | |
|---|---|
| **UC-ID** | UC-016 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 1.1 |

## Mô tả

Người học nhìn chữ mẫu trong vài giây, sau đó tự viết lại khi mẫu đã biến mất. Chế độ này không gợi ý nét, qua đó kiểm tra xem họ đã nhớ cách viết hay chưa.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chữ có `stroke_data`
3. **Đã luyện chữ này ở chế độ theo nét ít nhất 1 lần** — không cho nhảy thẳng vào chế độ khó

## Hậu điều kiện

Thành công → mastery tăng với hệ số **1.0**; `study_sessions` ghi `mode = "RECALL"`.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn chế độ "Nhớ rồi viết" |
| 2 | System | Kiểm đã luyện chế độ theo nét chưa |
| 3 | System | Trả chữ + `stroke_data` + `preview_seconds` (mặc định 5) |
| 4 | Client | Hiện chữ mẫu, đếm ngược 5 giây |
| 5 | Client | **Ẩn chữ**, mở khung viết trắng, tắt hint |
| 6 | `USER` | Viết lại toàn bộ chữ |
| 7 | Client | Chấm bằng `hanzi-writer`, tính `accuracy` |
| 8 | Client | `POST /api/practice/writing` với `mode: "RECALL"` |
| 9 | System | Cập nhật mastery hệ số 1.0 |

## Luồng thay thế

**A1 — Người học bấm "xem lại chữ mẫu"**
Cho xem thêm 3 giây, **tối đa 2 lần**. Mỗi lần xem lại giảm hệ số 0.25 (2 lần → 0.5).

**A2 — Hết 5 giây mà người học chưa sẵn sàng**
Có nút "chưa nhớ, xem lại từ đầu" → về UC-015 cho cùng chữ đó, không tính lượt.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `MODE_LOCKED` | 403 | Chưa luyện chế độ theo nét | Hiện "luyện chế độ cơ bản trước", chuyển sang UC-015 |
| `PREVIEW_TOO_LONG` | 400 | `preview_used_ms > 5000 + 3000×2` | Client đã bị sửa để xem chữ mãi |
| `HINT_USED_IN_RECALL` | 400 | Client báo `used_animation: true` ở chế độ này | **Sai logic** — chế độ này không có hint. Ghi log, coi là gian lận |
| `CHARACTER_NOT_FOUND` | 404 | Chữ không tồn tại | Về danh sách |
| `INVALID_ACCURACY` | 400 | Ngoài `[0,1]` | Chặn |
| `IMPOSSIBLE_DURATION` | 400 | Quá nhanh | Không cộng mastery |

> ⚠️ **`MODE_LOCKED` là điểm dễ quên.** Nếu không chặn, người học nhảy thẳng vào chế độ hệ
> số cao để farm mastery nhanh mà bỏ qua bước học nét. Kiểm ở **server**, không phải chỉ ẩn
> nút ở UI.

## Business rule

| # | Rule |
| --- | --- |
| BR-016-1 | Người học chỉ được mở chế độ “nhớ rồi viết” cho một chữ sau khi đã luyện chữ đó ở chế độ theo nét ít nhất một lần. Điều kiện này phải được kiểm tra ở server, không chỉ ẩn nút trên giao diện. |
| BR-016-2 | Chế độ “nhớ rồi viết” không hiển thị gợi ý nét trong lúc người học viết. Nếu request báo có dùng animation hoặc hint thì lượt đó không hợp lệ. |
| BR-016-3 | Thời gian xem chữ mẫu trước khi viết phải có giới hạn. Mặc định là 5 giây và có thể cấu hình trong hệ thống. |
| BR-016-4 | Người học được xem lại chữ mẫu tối đa 2 lần. Mỗi lần xem lại làm giảm hệ số mastery của lượt luyện. |
| BR-016-5 | Hệ số mastery mặc định của chế độ này là 1.0 vì người học phải tự nhớ chữ, khó hơn chế độ theo nét. |
| BR-016-6 | Nếu người học chưa sẵn sàng và chọn quay lại chế độ cơ bản, hệ thống không tính lượt “nhớ rồi viết” và không cộng mastery theo chế độ này. |
| BR-016-7 | Server phải kiểm tra tổng thời gian xem mẫu. Nếu client gửi thời gian xem vượt giới hạn cho phép, hệ thống không được cộng mastery cho lượt đó. |

## API · DB

```
POST /api/practice/writing   (mode = RECALL)
```

`characters` (đọc) · `user_knowledge_state` (đọc để kiểm điều kiện + ghi) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chưa từng luyện theo nét | 403 `MODE_LOCKED` |
| T2 | Đã luyện, viết đúng | 200, mastery hệ số 1.0 |
| T3 | Xem lại mẫu 2 lần | mastery hệ số 0.5 |
| T4 | Xem lại 3 lần (client sửa) | 400 `PREVIEW_TOO_LONG` |
| T5 | Gửi `used_animation: true` | 400 `HINT_USED_IN_RECALL` |

---

# UC-017 · Luyện viết chế độ thử thách

| | |
|---|---|
| **UC-ID** | UC-017 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 1.1 |

## Mô tả

Người học viết một dãy chữ liên tiếp trước khi hết giờ. Lượt chơi kết thúc khi viết xong, hết thời gian hoặc sai quá số lần cho phép.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có ít nhất 10 chữ đã ở trạng thái "đang học" hoặc "đã thuộc"

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Hoàn thành | Mastery cập nhật cho **từng chữ** trong dãy, hệ số 1.2; ghi `study_sessions` |
| Thua giữa dãy | Chỉ cập nhật mastery cho các chữ **đã viết xong**, chữ đang viết dở không tính |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Thử thách" |
| 2 | System | `POST /api/practice/challenge/start` — chọn 10 chữ từ `user_knowledge_state` ưu tiên chữ gần hạn ôn |
| 3 | System | Sinh `challenge_id`, ghi `served_at`, trả danh sách chữ + `time_limit_seconds` + `max_mistakes` |
| 4 | Client | Đếm ngược, hiện chữ thứ nhất |
| 5 | `USER` | Viết chữ |
| 6 | Client | Chấm; đúng → chữ tiếp; sai → tăng `mistakes` |
| 7 | | Lặp tới khi hết dãy, hết giờ, hoặc `mistakes = max_mistakes` |
| 8 | Client | `POST /api/practice/challenge/{id}/submit` — gửi kết quả từng chữ |
| 9 | System | Kiểm `submitted_at − served_at ≤ time_limit + 10s` |
| 10 | System | Cập nhật mastery từng chữ trong **một transaction** |

## Luồng thay thế

**A1 — Hết giờ giữa dãy**
Client tự nộp khi đồng hồ về 0. Kết quả tính cho phần đã làm.

**A2 — Sai đủ `max_mistakes`**
Kết thúc sớm, hiện "thử lại?". Các chữ đã viết đúng vẫn được tính mastery.

**A3 — Mất mạng giữa thử thách**
Client giữ kết quả trong `sessionStorage`, gửi lại khi có mạng. Server vẫn kiểm bước 9 —
nếu quá hạn thì trả `CHALLENGE_EXPIRED` và **không** tính điểm.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NOT_ENOUGH_CHARACTERS` | 422 | Chưa học đủ 10 chữ | Hiện "học thêm chữ trước khi thử thách" |
| `CHALLENGE_NOT_FOUND` | 404 | `challenge_id` sai | Bắt tạo thử thách mới |
| `CHALLENGE_ALREADY_SUBMITTED` | 409 | Nộp hai lần cùng `challenge_id` | **Chặn** — nếu không, gửi lại 100 lần là farm mastery |
| `CHALLENGE_EXPIRED` | 422 | `submitted_at − served_at` vượt hạn + 10s | Không tính mastery, cho làm lại |
| `RESULT_COUNT_MISMATCH` | 400 | Số kết quả > số chữ đã phát | Client thêm chữ không có trong dãy |
| `CHARACTER_NOT_IN_CHALLENGE` | 400 | Gửi kết quả cho chữ ngoài dãy | Gian lận rõ ràng, ghi log |
| `IMPOSSIBLE_DURATION` | 400 | Tổng thời gian nhỏ bất thường | Không tính |

> 🔴 **`CHALLENGE_ALREADY_SUBMITTED` + `CHARACTER_NOT_IN_CHALLENGE` là cặp không thể thiếu.**
> Thử thách sinh danh sách ở server, nên server **biết** dãy nào đã phát. Bỏ hai kiểm này là
> mở cửa cho request thủ công `{challenge_id: 1, results: [100 chữ đều đúng]}`.

## Business rule

| # | Rule |
| --- | --- |
| BR-017-1 | Chế độ thử thách chỉ mở khi người học có đủ số chữ đang học hoặc đã thuộc để tạo một lượt chơi hợp lệ. Trong MVP, một lượt thử thách cần tối thiểu 10 chữ. |
| BR-017-2 | Danh sách chữ trong lượt thử thách phải do server chọn. Client không được tự chọn chữ để tránh việc người học chọn toàn chữ dễ nhằm farm mastery. |
| BR-017-3 | Hệ thống ưu tiên chọn các chữ gần đến hạn ôn hoặc đang yếu để lượt thử thách có giá trị học tập. |
| BR-017-4 | Mỗi lượt thử thách phải có giới hạn thời gian và giới hạn số lỗi. Trong MVP, thời gian mặc định là 20 giây cho mỗi chữ và tối đa 3 lỗi. |
| BR-017-5 | Mỗi `challenge_id` chỉ được nộp một lần. Nếu nộp lại cùng `challenge_id`, hệ thống phải từ chối để tránh cộng mastery nhiều lần. |
| BR-017-6 | Server phải lưu thông tin lượt thử thách gồm `challenge_id`, danh sách chữ đã phát, thời điểm bắt đầu và trạng thái đã nộp. Nếu chưa có nơi lưu các thông tin này thì chưa được triển khai chế độ thử thách dạng server-side. |
| BR-017-7 | Kết quả chỉ được tính cho các chữ đã hoàn thành hợp lệ. Chữ đang viết dở khi hết giờ hoặc khi thua không được cộng mastery. |
| BR-017-8 | Hệ số mastery của chế độ thử thách cao hơn các chế độ luyện viết thông thường. Hệ số mặc định là 1.2 vì người học bị giới hạn thời gian và ít hỗ trợ hơn. |

## API · DB

```
POST /api/practice/challenge/start
POST /api/practice/challenge/{id}/submit
```

`characters` · `user_knowledge_state` (đọc + ghi) · `study_sessions` (ghi)

> ⚠️ **Thiếu bảng:** `challenge_id` với `served_at` và trạng thái `submitted` **chưa có bảng
> nào lưu** trong 59 bảng hiện tại. Xem mục "Khoảng trống thiết kế" cuối file.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | 10 chữ đúng hết trong hạn | 200, mastery 10 chữ tăng hệ số 1.2 |
| T2 | Chỉ có 5 chữ đã học | 422 `NOT_ENOUGH_CHARACTERS` |
| T3 | Nộp lần 2 cùng id | 409 `CHALLENGE_ALREADY_SUBMITTED` |
| T4 | Nộp sau hạn 5 phút | 422 `CHALLENGE_EXPIRED` |
| T5 | Gửi 15 kết quả cho dãy 10 chữ | 400 `RESULT_COUNT_MISMATCH` |
| T6 | Sai 3 lần ở chữ thứ 4 | 200, mastery chỉ tăng cho 3 chữ đầu |

---

# UC-018 · Luyện viết chế độ nghe chép

| | |
|---|---|
| **UC-ID** | UC-018 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 1.1 |

## Mô tả

Người học nghe cách đọc rồi viết lại chữ Hán tương ứng, không nhìn thấy chữ mẫu. Bài tập này kết hợp nhận biết âm đọc với nhớ mặt chữ và cách viết.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chữ có **cả** `stroke_data` **và** `audio_url`
3. Thiết bị có loa/tai nghe hoạt động

## Hậu điều kiện

Mastery cập nhật hệ số **1.2** cho **hai** loại kỹ năng: viết và nghe.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn chế độ "Nghe chép" |
| 2 | System | Trả `audio_url` + `stroke_data` (client cần để chấm) nhưng **không render chữ** |
| 3 | Client | Phát audio tự động |
| 4 | `USER` | Nghe, có thể bấm phát lại |
| 5 | `USER` | Viết chữ đã nghe |
| 6 | Client | Chấm nét, tính `accuracy` |
| 7 | Client | `POST /api/practice/writing` với `mode: "DICTATION"`, `replay_count` |
| 8 | System | Cập nhật mastery |

## Luồng thay thế

**A1 — Người học phát lại nhiều lần**
Cho phát tối đa **3 lần**. Từ lần 2 trở đi mỗi lần −0.2 hệ số.

**A2 — Không nghe được (mất audio)**
Có nút "không nghe được" → hiện chữ, chuyển về UC-015, **không tính lượt nghe chép**.

**A3 — Nghe sai hoàn toàn (viết chữ khác)**
`accuracy` rất thấp. Sau khi nộp, hệ thống hiện chữ đúng + audio lại để người học so sánh.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `AUDIO_NOT_AVAILABLE` | 422 | `audio_url IS NULL` | Không mở chế độ này; ẩn nút ở UI và chặn ở server |
| `AUDIO_LOAD_FAILED` | — (client) | CDN lỗi, mạng chậm | Client thử lại 2 lần rồi hiện "không tải được audio, chuyển chế độ khác" |
| `TOO_MANY_REPLAYS` | 400 | `replay_count > 3` | Client bị sửa |
| `CHARACTER_REVEALED` | 400 | Client gửi `used_animation: true` | Chế độ này không được hiện chữ |
| `IMPOSSIBLE_DURATION` | 400 | `duration_ms` nhỏ hơn độ dài audio | **Không thể viết xong trước khi audio phát hết** |
| `INVALID_ACCURACY` | 400 | Ngoài `[0,1]` | Chặn |

> ⚠️ **Rủi ro thiết kế đã biết:** bước 2 trả `stroke_data` cho client để chấm — nghĩa là
> **chữ đúng nằm trong response**. Người dùng mở DevTools là thấy. Không thể tránh khi chấm
> ở client. Cách giảm: chế độ này chỉ nên chiếm tỉ trọng nhỏ trong tổng mastery, và bài
> kiểm tra cuối chủ đề (UC-029, chấm ở server) mới là thước đo thật.

## Business rule

| # | Rule |
| --- | --- |
| BR-018-1 | Chế độ nghe chép chỉ mở cho chữ có cả dữ liệu nét và audio hợp lệ. Nếu thiếu một trong hai dữ liệu này, hệ thống không được mở chế độ nghe chép. |
| BR-018-2 | Trong lúc làm bài nghe chép, người học chỉ được nghe âm thanh, không được nhìn chữ mẫu. |
| BR-018-3 | Người học được phát lại audio tối đa 3 lần. Từ lần phát lại thứ hai, hệ số mastery của lượt luyện bị giảm. |
| BR-018-4 | Hệ thống phải phân biệt lỗi audio với lỗi của người học. Nếu audio không tải được do mạng hoặc nguồn âm thanh lỗi, lượt đó không được tính là trả lời sai. |
| BR-018-5 | Thời gian làm bài phải lớn hơn hoặc bằng độ dài audio ở mức tối thiểu hợp lý. Nếu gửi kết quả trước khi có thể nghe xong audio, lượt đó bị coi là không hợp lệ. |
| BR-018-6 | Chế độ nghe chép cập nhật cả kỹ năng nghe và kỹ năng viết, nhưng hệ số mastery phải được kiểm soát vì server không thể kiểm chứng hoàn toàn việc người học có nhìn dữ liệu chữ trong response hay không. |
| BR-018-7 | Nếu người học chọn “không nghe được”, hệ thống chuyển sang chế độ học khác và không tính lượt nghe chép. |

## API · DB

```
POST /api/practice/writing   (mode = DICTATION)
```

`characters` (đọc `audio_url`, `stroke_data`) · `user_knowledge_state` (ghi) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nghe 1 lần, viết đúng | 200, hệ số 1.2 |
| T2 | Chữ không có audio | 422 `AUDIO_NOT_AVAILABLE` |
| T3 | `replay_count = 5` | 400 `TOO_MANY_REPLAYS` |
| T4 | Phát lại 3 lần, viết đúng | mastery hệ số 1.2 − 0.4 = 0.8 |
| T5 | `duration_ms` = 200, audio dài 1500ms | 400 `IMPOSSIBLE_DURATION` |

---

# UC-019 · Nhận diện chữ — nhìn chữ chọn nghĩa

| | |
|---|---|
| **UC-ID** | UC-019 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.2 |

## Mô tả

Màn hình đưa ra một chữ Hán và bốn nghĩa tiếng Việt. Người học chọn nghĩa đúng để luyện nhận biết mặt chữ.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Kho `questions` có câu loại `CHAR_TO_MEANING` cho mức HSK của người học
3. Mỗi câu có đúng 4 dòng trong `question_options`, **đúng 1** dòng `is_correct = true`

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Trả lời đúng | Mastery tăng; `attempt_answers` ghi `is_correct = true` |
| Trả lời sai | Mastery **giảm**; ghi đáp án đã chọn để phân tích lỗi sai (dùng cho UC-040) |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Vào phần "Nhận diện chữ" |
| 2 | System | `GET /api/practice/recognition?type=CHAR_TO_MEANING&count=10` |
| 3 | System | Chọn câu ưu tiên điểm kiến thức gần hạn ôn; **không trả `is_correct`** trong response |
| 4 | System | Ghi `served_at` cho lượt luyện |
| 5 | Client | Hiện chữ + 4 đáp án đã trộn thứ tự |
| 6 | `USER` | Chọn một đáp án |
| 7 | Client | `POST /api/practice/recognition/answer` — `{question_id, option_id, duration_ms}` |
| 8 | System | **Server** so `option_id` với `question_options.is_correct` |
| 9 | System | Ghi `attempt_answers`, cập nhật mastery, trả `{correct, correct_option_id, explanation}` |
| 10 | Client | Hiện đúng/sai + giải thích, sang câu tiếp |

## Luồng thay thế

**A1 — Bỏ qua câu hỏi**
`USER` bấm "bỏ qua". Ghi `attempt_answers` với `option_id = NULL`. Mastery **không đổi** —
bỏ qua không phải sai.

**A2 — Hết câu hỏi trong kho**
Bước 2 trả ít hơn `count`. Client hiện "đã luyện hết câu ở mức này", gợi ý lên mức cao hơn.

**A3 — Trả lời quá nhanh**
`duration_ms < 800`. Vẫn tính đúng/sai nhưng đặt cờ `suspicious`, hệ số mastery ×0.5.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `QUESTION_NOT_FOUND` | 404 | `question_id` không tồn tại | Bỏ câu, sang câu tiếp |
| `OPTION_NOT_IN_QUESTION` | 400 | `option_id` không thuộc câu đó | **Gian lận** — ghi log, không tính |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời cùng câu 2 lần trong một lượt | Chặn farm mastery bằng cách gửi lại |
| `NO_QUESTIONS_AVAILABLE` | 200 (mảng rỗng) | Kho hết câu | **Không phải lỗi** — trả 200 mảng rỗng, client hiện thông báo |
| `MALFORMED_QUESTION` | 500 | Câu có 0 hoặc >1 đáp án đúng | Ẩn câu đó khỏi kho, báo `CONTENT_ADMIN`, ghi `question_reports` |
| `RATE_LIMIT_EXCEEDED` | 429 | > 600 câu/giờ | Chặn bot |
| `UNAUTHORIZED` | 401 | Token hết hạn | Refresh rồi gửi lại |

> 🔴 **`MALFORMED_QUESTION` là lỗi dữ liệu dễ xảy ra nhất khi nhập đề hàng loạt (UC-110).**
> Một câu có 2 đáp án đúng sẽ làm người học bị chấm sai oan. Phải có **constraint ở DB**
> (unique partial index trên `question_id WHERE is_correct`) chứ không chỉ kiểm ở code.

## Business rule

| # | Rule |
| --- | --- |
| BR-019-1 | Bài nhận diện chữ phải được chấm ở server. Response gửi cho client không được chứa đáp án đúng hoặc trường `is_correct`. |
| BR-019-2 | Mỗi câu hỏi trắc nghiệm phải có đúng một đáp án đúng. Câu có không có đáp án đúng hoặc có nhiều hơn một đáp án đúng không được đưa cho người học. |
| BR-019-3 | Các đáp án nhiễu phải khác đáp án đúng và không được trùng nghĩa với đáp án đúng. |
| BR-019-4 | Với dạng nhìn chữ chọn nghĩa, đáp án nhiễu nên lấy từ các chữ dễ nhầm, ví dụ chữ gần hình dạng, cùng bộ thủ hoặc dễ nhầm trong cùng cấp học, để bài có giá trị phân biệt. |
| BR-019-5 | Thứ tự đáp án phải được trộn lại mỗi lần sinh câu hỏi. |
| BR-019-6 | Trả lời đúng làm tăng mastery của điểm kiến thức liên quan; trả lời sai làm giảm hoặc điều chỉnh mastery theo thuật toán tiến độ. |
| BR-019-7 | Nếu thời gian trả lời quá ngắn bất thường, hệ thống vẫn có thể ghi nhận đáp án nhưng phải giảm hệ số mastery hoặc đánh dấu lượt đó là đáng ngờ. |
| BR-019-8 | Một câu hỏi trong cùng một lượt luyện chỉ được trả lời một lần. Việc gửi lại cùng câu để dò đáp án không được cộng thêm tiến độ. |

## API · DB

```
GET  /api/practice/recognition
POST /api/practice/recognition/answer
```

`questions` · `question_options` · `question_knowledge_points` (đọc) · `attempt_answers` · `user_knowledge_state` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chọn đáp án đúng | 200 `correct: true`, mastery tăng |
| T2 | Chọn sai | 200 `correct: false`, mastery giảm, có `explanation` |
| T3 | `option_id` của câu khác | 400 `OPTION_NOT_IN_QUESTION` |
| T4 | Trả lời lần 2 | 409 `ANSWER_ALREADY_SUBMITTED` |
| T5 | Response bước 3 | **Không** chứa trường `is_correct` |
| T6 | `duration_ms = 300` | 200 nhưng mastery tăng bằng nửa T1 |
| T7 | Câu có 2 đáp án `is_correct` | DB constraint chặn từ lúc insert |

---

# UC-020 · Nhận diện chữ — nghe âm chọn chữ

| | |
|---|---|
| **UC-ID** | UC-020 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.2 |

## Mô tả

Người học nghe một âm đọc và chọn chữ Hán tương ứng trong bốn đáp án. Bài tập giúp họ nối âm nghe được với mặt chữ.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có câu loại `AUDIO_TO_CHAR` với `audio_url` hợp lệ
3. 4 đáp án là **chữ**, không phải nghĩa

## Hậu điều kiện

Mastery kỹ năng **nghe** cập nhật; `attempt_answers` ghi lượt trả lời.

## Luồng chính

Giống UC-019, khác ở bước 5: client phát audio thay vì hiện chữ; 4 đáp án là chữ Hán.

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Vào phần "Nghe chọn chữ" |
| 2 | System | `GET /api/practice/recognition?type=AUDIO_TO_CHAR&count=10` |
| 3 | Client | Phát audio, hiện 4 chữ |
| 4 | `USER` | Chọn chữ |
| 5 | Client | `POST /api/practice/recognition/answer` kèm `replay_count` |
| 6 | System | Chấm ở server, cập nhật mastery nghe |

## Luồng thay thế

**A1 — Phát lại** — tối đa 3 lần, từ lần 2 mỗi lần −0.2 hệ số.
**A2 — Audio lỗi** — client báo `AUDIO_LOAD_FAILED`, cho bỏ qua câu **không tính sai**.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `AUDIO_NOT_AVAILABLE` | 422 | Câu thiếu `audio_url` | Loại câu khỏi kho, báo `CONTENT_ADMIN` |
| `AUDIO_LOAD_FAILED` | — | CDN/mạng | Bỏ qua câu, **không** ghi là sai |
| `TOO_MANY_REPLAYS` | 400 | `replay_count > 3` | Client bị sửa |
| `OPTION_NOT_IN_QUESTION` | 400 | Đáp án lạ | Gian lận |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời 2 lần | Chặn |
| `MALFORMED_QUESTION` | 500 | Sai số đáp án đúng | Ẩn câu, ghi `question_reports` |

> ⚠️ **`AUDIO_LOAD_FAILED` phải phân biệt rõ với trả lời sai.** Nếu ghi thành sai, người học
> bị giảm mastery vì lỗi hạ tầng của ta. Đây là loại bug làm người dùng mất tin tưởng nhất.

## Business rule

| # | Rule |
| --- | --- |
| BR-020-1 | Dạng nghe âm chọn chữ phải được chấm ở server. Client không được biết đáp án đúng trước khi nộp câu trả lời. |
| BR-020-2 | Mỗi câu hỏi phải có audio hợp lệ. Câu thiếu audio không được đưa vào lượt luyện. |
| BR-020-3 | Bốn đáp án phải là chữ Hán. Các đáp án nhiễu không được trùng pinyin kèm thanh điệu với đáp án đúng, vì khi nghe sẽ không thể phân biệt công bằng. |
| BR-020-4 | Nếu audio bị lỗi tải do mạng hoặc nguồn âm thanh, lượt đó không được tính là trả lời sai và không được làm giảm mastery của người học. |
| BR-020-5 | Số lần phát lại audio phải có giới hạn. Trong MVP, cho phép tối đa 3 lần phát. |
| BR-020-6 | Mastery của dạng bài này được ghi vào kỹ năng nghe hoặc liên kết âm - chữ, không ghi như một lượt nhận diện chữ thuần túy. |
| BR-020-7 | Một câu hỏi trong cùng một lượt luyện chỉ được trả lời một lần. |

## API · DB

```
GET  /api/practice/recognition?type=AUDIO_TO_CHAR
POST /api/practice/recognition/answer
```

`questions` · `question_options` · `characters` (đọc) · `attempt_answers` · `user_knowledge_state` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nghe, chọn đúng | 200, mastery `LISTENING` tăng |
| T2 | Audio 404 → bỏ qua | `attempt_answers` **không** ghi `is_correct = false` |
| T3 | 4 đáp án trùng pinyin | Kiểm khi nhập đề, loại câu |
| T4 | `replay_count = 3` | mastery giảm hệ số 0.4 |

---

# UC-021 · Luyện phát âm theo 8 tầng

| | |
|---|---|
| **UC-ID** | UC-021 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.3 |

## Mô tả

Người học luyện nghe và phân biệt thanh mẫu, vận mẫu, thanh điệu, biến điệu qua tám tầng bài tập. Kết quả ở từng tầng cho biết họ đã sẵn sàng học tầng tiếp theo hay chưa.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Tầng đang mở (tầng 1 mở sẵn; tầng N cần hoàn thành tầng N−1 — xem UC-022)
3. `pronunciation_units` có dữ liệu cho tầng đó

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Làm xong tầng | `user_pronunciation_progress` cập nhật `accuracy`, `completed_at` |
| Đạt ngưỡng | Kích hoạt UC-022 mở tầng tiếp |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang "Luyện phát âm" |
| 2 | System | `GET /api/pronunciation/stages` — trả 8 tầng kèm trạng thái khoá/mở và `accuracy` đã đạt |
| 3 | `USER` | Chọn một tầng đang mở |
| 4 | System | Kiểm quyền vào tầng; `GET /api/pronunciation/stages/{id}/units` |
| 5 | Client | Hiện bài: phát audio, 4 lựa chọn (thanh mẫu/vận mẫu/thanh điệu) |
| 6 | `USER` | Chọn đáp án |
| 7 | Client | `POST /api/pronunciation/answer` |
| 8 | System | Chấm ở server, ghi kết quả |
| 9 | | Lặp 5–8 hết các unit của tầng |
| 10 | System | Tính `accuracy` cả tầng, ghi `user_pronunciation_progress` |
| 11 | System | Nếu `accuracy ≥ 80%` → gọi UC-022 |

## Luồng thay thế

**A1 — Làm lại tầng đã xong**
Được phép. Ghi `accuracy` mới nếu **cao hơn** lần trước; thấp hơn thì giữ kết quả cũ, **không
đóng lại** tầng đã mở.

**A2 — Bỏ giữa tầng**
Lưu tiến độ từng unit, lần sau vào tiếp từ unit chưa làm. Chưa hết tầng thì **chưa** tính
`accuracy` tầng.

**A3 — Người học cố vào tầng bị khoá qua URL**
Server trả `STAGE_LOCKED`. Không dựa vào ẩn nút ở UI.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `STAGE_LOCKED` | 403 | Chưa hoàn thành tầng trước | Hiện "hoàn thành tầng N−1 trước". **Kiểm ở server** |
| `STAGE_NOT_FOUND` | 404 | `stage_id` ngoài 1–8 | Về danh sách tầng |
| `STAGE_HAS_NO_UNITS` | 500 | Tầng chưa nhập dữ liệu | Báo `CONTENT_ADMIN`, ẩn tầng khỏi danh sách |
| `UNIT_NOT_IN_STAGE` | 400 | Trả lời unit không thuộc tầng | Gian lận |
| `AUDIO_NOT_AVAILABLE` | 422 | Unit thiếu audio | Bỏ unit, **không tính vào `accuracy`** — không phạt người học vì dữ liệu thiếu |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời unit 2 lần trong một lượt | Chặn |
| `REGRESSION_ATTEMPT` | — | Làm lại điểm thấp hơn | **Không phải lỗi** — giữ điểm cũ, không đóng tầng (A1) |

> ⚠️ **`REGRESSION_ATTEMPT` là quyết định nghiệp vụ, không phải lỗi.** Nếu làm lại tầng
> được điểm thấp hơn mà hệ thống **đóng lại** tầng sau thì người học mất tiến độ đã có —
> cảm giác bị phạt vì ôn lại. Đã chốt: mở rồi thì không đóng.

## Business rule

| # | Rule |
| --- | --- |
| BR-021-1 | Lộ trình phát âm gồm 8 tầng, đi từ nội dung cơ bản đến nội dung khó hơn như thanh mẫu, vận mẫu, thanh điệu và biến điệu. |
| BR-021-2 | Tầng 1 được mở sẵn cho mọi người học đã đăng nhập. Các tầng sau chỉ mở khi người học đạt điều kiện hoàn thành tầng trước. |
| BR-021-3 | Ngưỡng hoàn thành một tầng trong MVP là `accuracy >= 80%`. |
| BR-021-4 | Khi một tầng đã được mở, hệ thống không được khóa lại tầng đó nếu người học làm lại và đạt điểm thấp hơn. |
| BR-021-5 | Người học được làm lại tầng đã hoàn thành. Hệ thống chỉ cập nhật kết quả nếu điểm mới cao hơn điểm đã lưu. |
| BR-021-6 | Unit thiếu audio phải được loại khỏi mẫu số khi tính accuracy, vì người học không được bị phạt do dữ liệu thiếu. |
| BR-021-7 | MVP không chấm phát âm qua micro. Phần luyện phát âm hiện tại chỉ là nghe và chọn, không đánh giá giọng nói thật của người học. |
| BR-021-8 | Việc khóa/mở tầng phải được kiểm tra ở server. Không được chỉ dựa vào việc ẩn nút trên giao diện. |

## API · DB

```
GET  /api/pronunciation/stages
GET  /api/pronunciation/stages/{id}/units
POST /api/pronunciation/answer
```

`pronunciation_units` (đọc) · `user_pronunciation_progress` (đọc + ghi)

> ⚠️ **Lệch tài liệu:** feature tree 1.3 ghi 3 bảng gồm `pronunciation_stages`, nhưng danh
> sách 40 bảng của `learning` **chỉ có 2** (`pronunciation_units`, `user_pronunciation_progress`).
> Tầng hiện phải suy ra từ cột trong `pronunciation_units`. Xem mục cuối file.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `USER` mới mở tầng 1 | 200, danh sách unit |
| T2 | Gọi tầng 5 khi mới xong tầng 1 | 403 `STAGE_LOCKED` |
| T3 | Làm tầng 1 đúng 85% | `accuracy = 85`, tầng 2 mở |
| T4 | Làm lại tầng 1 được 70% | Vẫn lưu 85, tầng 2 **vẫn mở** |
| T5 | Tầng có 1 unit thiếu audio, đúng 9/9 còn lại | `accuracy = 100%`, không phải 90% |

---

# UC-022 · Mở tầng phát âm tiếp theo

| | |
|---|---|
| **UC-ID** | UC-022 · **Actor** `SYSTEM` · **Pri** P1 · **Scope** MVP · **FT** 1.3 |

## Mô tả

Khi người học đạt mức chính xác yêu cầu ở một tầng phát âm, tầng kế tiếp sẽ tự mở. Họ không cần thao tác mở khóa riêng.

## Tiền điều kiện

1. UC-021 vừa hoàn thành một tầng
2. `accuracy ≥ 80%`
3. Tầng N+1 tồn tại (N < 8)

## Hậu điều kiện

`user_pronunciation_progress` có dòng cho tầng N+1 với `unlocked_at` — cùng transaction với
lệnh ghi kết quả tầng N.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | System | Nhận kết quả hoàn thành tầng N từ UC-021 |
| 2 | System | Tính `accuracy` |
| 3 | System | So với ngưỡng 80% |
| 4 | System | Kiểm tầng N+1 có tồn tại |
| 5 | System | Kiểm đã mở chưa (idempotent) |
| 6 | System | `INSERT` dòng tầng N+1 với `unlocked_at = now()` |
| 7 | System | Trả cờ `unlocked_next_stage: true` để client hiện hiệu ứng |

## Luồng thay thế

**A1 — Chưa đạt ngưỡng**
Không mở. Trả `{unlocked: false, needed: 80, actual: 72}` để client hiện "còn 8% nữa".

**A2 — Tầng N+1 đã mở trước đó**
Bước 5 phát hiện, **bỏ qua** insert. Không lỗi, không tạo dòng trùng.

**A3 — Đã là tầng 8 (tầng cuối)**
Không có tầng 9. Trả `{completed_all_stages: true}`, hiện chúc mừng hoàn thành phát âm.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NEXT_STAGE_NOT_FOUND` | — | Đã ở tầng 8 | Không phải lỗi (A3) |
| `STAGE_ALREADY_UNLOCKED` | — | Chạy lại | **Idempotent** — bỏ qua im lặng |
| `UNIQUE_VIOLATION` | 500 | Hai request song song cùng mở | Cần unique constraint `(user_id, stage)`; bắt lỗi và coi như đã mở |
| `TRANSACTION_ROLLBACK` | 500 | Lỗi ghi | **Toàn bộ kết quả tầng N cũng rollback** — không được mở tầng mà mất kết quả, hoặc ngược lại |

> 🔴 **`UNIQUE_VIOLATION` là race condition thật, không phải giả thuyết.** Người học mở hai
> tab, cùng nộp unit cuối của tầng 1 trong cùng giây → hai request cùng thấy "tầng 2 chưa mở"
> → cả hai insert. Không có unique constraint là có hai dòng tầng 2, làm sai mọi thống kê sau đó.

## Business rule

| # | Rule |
| --- | --- |
| BR-022-1 | Hệ thống tự động mở tầng phát âm tiếp theo khi người học hoàn thành tầng hiện tại và đạt ngưỡng accuracy yêu cầu. |
| BR-022-2 | Ngưỡng mở tầng mặc định là 80% và phải được cấu hình tập trung, không hardcode rải rác trong nhiều nơi. |
| BR-022-3 | Việc ghi kết quả tầng hiện tại và mở tầng tiếp theo phải nằm trong cùng một thao tác nhất quán dữ liệu. Không được để xảy ra trường hợp mất kết quả tầng hiện tại nhưng tầng sau vẫn mở, hoặc ngược lại. |
| BR-022-4 | Mở tầng là thao tác idempotent. Nếu cùng một tầng đã được mở trước đó, hệ thống không tạo dòng trùng và không báo lỗi cho người học. |
| BR-022-5 | Mỗi người học chỉ có một bản ghi tiến độ cho mỗi tầng phát âm. Hệ thống cần ràng buộc duy nhất theo cặp người học và số tầng. |
| BR-022-6 | Mỗi lần hoàn thành chỉ được mở tối đa một tầng kế tiếp. Hệ thống không được nhảy qua nhiều tầng dù điểm rất cao. |
| BR-022-7 | Nếu người học hoàn thành tầng cuối cùng, hệ thống đánh dấu hoàn thành lộ trình phát âm thay vì cố mở tầng không tồn tại. |

## API · DB

Không có endpoint riêng — chạy trong `POST /api/pronunciation/answer` khi hoàn thành tầng.

`user_pronunciation_progress` (đọc + ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xong tầng 1, 85% | Tầng 2 mở, có `unlocked_at` |
| T2 | Xong tầng 1, 72% | Không mở, trả `needed: 80, actual: 72` |
| T3 | Gọi lại khi tầng 2 đã mở | Không tạo dòng thứ hai |
| T4 | Hai request song song | Chỉ một dòng tầng 2 tồn tại |
| T5 | Xong tầng 8, 90% | `completed_all_stages: true`, không tạo tầng 9 |
| T6 | Ghi tầng 2 lỗi | Kết quả tầng 1 **cũng rollback** |

---

# UC-023 · Học điểm ngữ pháp HSK

| | |
|---|---|
| **UC-ID** | UC-023 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.4 |

## Mô tả

Người học chọn một điểm ngữ pháp HSK để xem cấu trúc, giải thích bằng tiếng Việt, ví dụ và làm bài tập ngắn. Sau lần học đầu, điểm ngữ pháp đó được đưa vào lịch ôn.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `grammar_points` có dữ liệu cho cấp HSK đó
3. Điểm ngữ pháp đã liên kết `knowledge_points`

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Học xong | `user_knowledge_state` tạo dòng mới với `stability`, `difficulty`, `next_review_at` theo FSRS |
| Làm bài tập | Kết quả đúng/sai ảnh hưởng tham số FSRS ban đầu |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn cấp HSK rồi chọn điểm ngữ pháp |
| 2 | System | `GET /api/grammar/{id}` — cấu trúc, giải thích, ví dụ |
| 3 | Client | Hiện nội dung học |
| 4 | `USER` | Đọc, bấm "đã hiểu" |
| 5 | System | `GET /api/grammar/{id}/exercises` — 3–5 bài tập ngắn |
| 6 | `USER` | Làm bài tập |
| 7 | Client | `POST /api/grammar/{id}/complete` — `{exercise_results, duration_ms}` |
| 8 | System | Chấm ở server, tính `rating` FSRS (Again/Hard/Good/Easy) từ tỉ lệ đúng |
| 9 | System | Tạo hoặc cập nhật `user_knowledge_state`, tính `next_review_at` |
| 10 | Client | Hiện "ôn lại sau X ngày" |

## Luồng thay thế

**A1 — Học lại điểm đã học**
Không tạo dòng mới. Đi vào luồng UC-024 (ôn) thay vì luồng học mới.

**A2 — Bấm "đã hiểu" mà bỏ bài tập**
Cho phép, nhưng `rating` mặc định là `Hard` — không có bằng chứng đã hiểu thì lịch ôn phải dày.

**A3 — Đọc quá nhanh**
`duration_ms < 5000` cho điểm ngữ pháp dài. Vẫn cho qua nhưng `rating` tối đa là `Good`,
không được `Easy`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `GRAMMAR_POINT_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `NO_KNOWLEDGE_POINT_LINKED` | 500 | `grammar_points` chưa nối `knowledge_points` | **Không tạo được lịch ôn.** Ghi log, báo `CONTENT_ADMIN`, vẫn cho học nhưng không ghi FSRS |
| `NO_EXERCISES` | 200 | Điểm chưa có bài tập | Không phải lỗi — bỏ bước 5–6, `rating = Hard` |
| `INVALID_EXERCISE_RESULT` | 400 | Kết quả cho bài tập không thuộc điểm này | Gian lận |
| `ALREADY_LEARNED` | 409 | Đã học rồi | Chuyển sang UC-024 (A1) |
| `FSRS_CALCULATION_FAILED` | 500 | Tham số FSRS lỗi | **Rollback toàn bộ.** Không được ghi "đã học" mà thiếu lịch ôn |

> 🔴 **`NO_KNOWLEDGE_POINT_LINKED` là lỗi dữ liệu nghiêm trọng nhất của nhóm này.**
> `knowledge_points` là trục nối từ điển ↔ đề thi ↔ tiến độ (đã ghi trong DB v5: "5 bảng
> tuyệt đối không đụng"). Điểm ngữ pháp không nối được thì tính năng 3.2 "yếu chỗ nào luyện
> chỗ đó" không thấy nó — người học học xong mà hệ thống coi như chưa biết gì.

## Business rule

| # | Rule |
| --- | --- |
| BR-023-1 | Hệ thống quản lý các điểm ngữ pháp theo cấp HSK. Bộ dữ liệu hiện tại gồm 593 điểm: HSK1 có 70, HSK2 có 78, HSK3 có 96, HSK4 có 95, HSK5 có 70, HSK6 có 50 và HSK7-9 có 134 điểm. |
| BR-023-2 | Mỗi điểm ngữ pháp phải liên kết với một điểm kiến thức để hệ thống có thể theo dõi tiến độ, lập lịch ôn và đưa vào lộ trình thông minh. |
| BR-023-3 | Lần học đầu của một điểm ngữ pháp phải tạo trạng thái học tập cá nhân cho người học. Không được chỉ hiển thị nội dung rồi bỏ qua tiến độ. |
| BR-023-4 | Lịch ôn ngữ pháp sử dụng FSRS hoặc thuật toán ôn lặp đã chốt của hệ thống, không dùng hộp Leitner đơn giản cho phần này. |
| BR-023-5 | Nếu người học bỏ bài tập sau khi đọc lý thuyết, hệ thống vẫn cho hoàn thành lượt học nhưng phải xếp mức ghi nhớ thấp, ví dụ `Hard`, vì chưa có bằng chứng người học đã hiểu chắc. |
| BR-023-6 | Bài tập ngữ pháp có đáp án đúng/sai phải được chấm ở server. Client không được tự quyết định kết quả cuối cùng. |
| BR-023-7 | Điểm ngữ pháp thiếu liên kết điểm kiến thức là lỗi dữ liệu nghiêm trọng và không được nhập vào kho học chính thức. |
| BR-023-8 | Nếu việc tính lịch ôn thất bại, hệ thống không được ghi trạng thái “đã học” mà thiếu lịch ôn tương ứng. |

## API · DB

```
GET  /api/grammar/{id}
GET  /api/grammar/{id}/exercises
POST /api/grammar/{id}/complete
```

`grammar_points` · `knowledge_points` · `questions` (đọc) · `user_knowledge_state` (ghi) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Học điểm mới, bài tập đúng hết | Tạo `user_knowledge_state`, `rating = Easy` |
| T2 | Bỏ bài tập | `rating = Hard`, `next_review_at` gần hơn T1 |
| T3 | Điểm không nối `knowledge_points` | 500 ghi log, **không** tạo dòng FSRS |
| T4 | Học lại điểm đã học | 409, chuyển UC-024 |
| T5 | `duration_ms = 1000` | `rating` tối đa `Good` |
| T6 | FSRS lỗi ở bước 9 | Rollback, không ghi "đã học" |

---

# UC-024 · Ôn lại ngữ pháp theo lịch FSRS

| | |
|---|---|
| **UC-ID** | UC-024 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.4 |

## Mô tả

Người học làm bài ôn cho những điểm ngữ pháp đã đến hạn. Dựa trên kết quả lần ôn này, lịch ôn tiếp theo được điều chỉnh theo FSRS.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có ít nhất một dòng `user_knowledge_state` với `next_review_at ≤ now()`

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Đúng | `stability` tăng, `next_review_at` giãn xa |
| Sai | `stability` giảm, `next_review_at` gần lại (thường trong ngày) |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Vào "Ôn hôm nay" |
| 2 | System | `GET /api/review/due?type=GRAMMAR` — sắp theo `next_review_at` cũ nhất trước |
| 3 | Client | Hiện số lượng cần ôn |
| 4 | `USER` | Bắt đầu ôn |
| 5 | Client | Hiện bài ôn cho điểm thứ nhất |
| 6 | `USER` | Trả lời |
| 7 | Client | `POST /api/review/answer` — `{knowledge_point_id, rating hoặc answer, duration_ms}` |
| 8 | System | Chấm ở server, tính `rating` FSRS |
| 9 | System | Cập nhật `stability`, `difficulty`, `next_review_at` |
| 10 | | Lặp 5–9 hết danh sách |
| 11 | Client | Hiện tổng kết: đã ôn N, đúng M, lần ôn tới |

## Luồng thay thế

**A1 — Không có gì đến hạn**
Bước 2 trả mảng rỗng. Hiện "hôm nay không có gì cần ôn", gợi ý học điểm mới (UC-023).

**A2 — Tồn quá nhiều (bỏ ôn nhiều ngày)**
Nếu > 50 điểm đến hạn, chỉ trả 50 cái quá hạn lâu nhất. Hiện "còn N điểm nữa, ôn tiếp sau".

**A3 — Bỏ giữa buổi ôn**
Các điểm đã trả lời đã cập nhật FSRS ngay từng câu — không mất. Điểm chưa làm vẫn đến hạn.

**A4 — Ôn sớm (trước hạn)**
Cho phép bấm "ôn trước". FSRS nhận `elapsed_days` nhỏ hơn dự kiến → **giãn ít hơn**. Không
chặn nhưng cũng không thưởng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NOTHING_DUE` | 200 (rỗng) | Không có gì đến hạn | Không phải lỗi (A1) |
| `KNOWLEDGE_STATE_NOT_FOUND` | 404 | Ôn điểm chưa từng học | **Chặn** — phải học (UC-023) trước khi ôn |
| `NOT_DUE_YET` | 200 | Ôn sớm | Cho phép, FSRS xử lý (A4) |
| `INVALID_RATING` | 400 | `rating` ngoài `{Again, Hard, Good, Easy}` | Chặn |
| `CLIENT_SENT_RATING_DIRECTLY` | 400 | Client tự gửi `rating` cho bài có đáp án đúng/sai | **Server phải tự tính `rating`**, không nhận từ client |
| `STALE_REVIEW` | 409 | `next_review_at` đã đổi (tab khác vừa ôn) | Bỏ qua lượt này, không cập nhật hai lần |
| `FSRS_CALCULATION_FAILED` | 500 | Tham số bất thường | Rollback, giữ `next_review_at` cũ |

> 🔴 **`CLIENT_SENT_RATING_DIRECTLY` là lỗ hổng tinh vi.** FSRS cần `rating` 4 mức. Với dạng
> tự đánh giá (flashcard) thì client gửi `rating` là đúng. Nhưng với bài có đáp án, nếu nhận
> `rating` từ client thì người dùng gửi `Easy` mãi để không bao giờ phải ôn lại — mastery
> nhìn đẹp mà không học gì. **Phân biệt rõ hai loại bài này.**

## Business rule

| # | Rule |
| --- | --- |
| BR-024-1 | Danh sách ôn hôm nay phải lấy các điểm kiến thức đã đến hạn, tức là có `next_review_at` nhỏ hơn hoặc bằng thời điểm hiện tại. |
| BR-024-2 | Các mục ôn phải được sắp xếp theo thời điểm đến hạn cũ nhất trước để ưu tiên phần đã quá hạn lâu hơn. |
| BR-024-3 | Mỗi buổi ôn chỉ nên trả về một số lượng giới hạn để tránh quá tải cho người học. Trong MVP, tối đa 50 điểm mỗi buổi. |
| BR-024-4 | Kết quả ôn phải được cập nhật ngay sau từng câu hoặc từng điểm kiến thức, không chờ đến hết buổi ôn. |
| BR-024-5 | Với bài ôn có đáp án đúng/sai, server phải tự tính rating FSRS từ kết quả làm bài. Client không được tự gửi `Easy`, `Good`, `Hard` cho loại bài này. |
| BR-024-6 | Chỉ các bài tự đánh giá như flashcard mới được nhận rating trực tiếp từ người học. |
| BR-024-7 | Người học được phép ôn sớm trước hạn, nhưng kết quả ôn sớm không được thưởng như một lần ôn đúng hạn; khoảng cách ôn sau phải giãn ít hơn. |
| BR-024-8 | Người học không được ôn một điểm kiến thức chưa từng học. Muốn ôn phải có trạng thái học tập trước đó. |
| BR-024-9 | Nếu cùng một điểm được ôn ở hai tab, hệ thống phải tránh cập nhật hai lần lên cùng một trạng thái cũ. |

## API · DB

```
GET  /api/review/due?type=GRAMMAR
POST /api/review/answer
```

`user_knowledge_state` (đọc + ghi) · `grammar_points` · `questions` (đọc) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | 10 điểm đến hạn, đúng hết | `next_review_at` cả 10 giãn xa hơn |
| T2 | Trả lời sai | `next_review_at` trong vòng 1 ngày |
| T3 | Không có gì đến hạn | 200 mảng rỗng |
| T4 | 80 điểm quá hạn | Trả 50 cái cũ nhất |
| T5 | Client gửi `rating: Easy` cho bài trắc nghiệm | 400 `CLIENT_SENT_RATING_DIRECTLY` |
| T6 | Ôn điểm chưa học | 404 `KNOWLEDGE_STATE_NOT_FOUND` |
| T7 | Hai tab cùng ôn một điểm | Tab thứ hai nhận 409 `STALE_REVIEW` |

---

# UC-025 · Xem danh sách chủ đề từ vựng

| | |
|---|---|
| **UC-ID** | UC-025 · **Actor** `USER` · **Pri** P0 · **Scope** MVP · **FT** 1.5 |

## Mô tả

Người học xem các chủ đề từ vựng, tiến độ hoàn thành và trạng thái khóa hoặc mở của từng chủ đề. Từ đây họ chọn chủ đề muốn học tiếp.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `topics` có dữ liệu
3. Mỗi chủ đề có ít nhất 1 dòng `topic_words`

## Hậu điều kiện

Không đổi dữ liệu — use case chỉ đọc. Nhưng phải trả % **tính đúng** vì UC-046 dùng số này
để mở khoá.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở trang "Học từ vựng" |
| 2 | System | `GET /api/topics` |
| 3 | System | Với mỗi chủ đề: đọc `user_topic_progress` lấy % hoàn thành |
| 4 | System | Xác định trạng thái: `LOCKED` · `AVAILABLE` · `IN_PROGRESS` · `COMPLETED` |
| 5 | System | Trả danh sách kèm `total_words`, `learned_words`, `completion_percent`, `status` |
| 6 | Client | Hiện lưới chủ đề, chủ đề khoá có icon ổ khoá |

## Luồng thay thế

**A1 — `USER` mới, chưa có `user_topic_progress`**
Trả `completion_percent = 0` cho mọi chủ đề. Chủ đề gốc (không có tiên quyết) là `AVAILABLE`,
còn lại `LOCKED`.

**A2 — Lọc theo cấp HSK**
`GET /api/topics?hsk_level=2`. Chỉ lọc hiển thị, không đổi trạng thái khoá.

**A3 — Chủ đề chưa có từ nào**
Ẩn khỏi danh sách (`topic_words` rỗng) — hiện ra thì người học vào rồi thấy trống.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_TOPICS` | 200 (rỗng) | Chưa nhập chủ đề | Hiện "nội dung đang được cập nhật" |
| `PROGRESS_PERCENT_MISMATCH` | — | `user_topic_progress.completion_percent` lệch so với đếm thật từ `user_knowledge_state` | 🔴 **Vấn đề nghiêm trọng** — xem ghi chú dưới |
| `INVALID_HSK_LEVEL` | 400 | `hsk_level` ngoài 1–9 | Chặn |
| `TOPIC_HAS_NO_WORDS` | — | `topic_words` rỗng | Ẩn khỏi danh sách (A3) |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập | `GUEST` **không** xem được danh sách chủ đề |

> 🔴 **`PROGRESS_PERCENT_MISMATCH` — lỗi dữ liệu nguy hiểm nhất của nhóm 1.**
> `user_topic_progress.completion_percent` là **giá trị tính sẵn** (denormalized) từ
> `user_knowledge_state`. Nếu một lượt học ghi `user_knowledge_state` mà **không** cập nhật
> `user_topic_progress` (ví dụ transaction chỉ commit một nửa, hoặc mastery được cộng từ game
> UC-088 qua đường khác), hai con số lệch nhau. Hệ quả: người học đủ 90% thật nhưng **không
> được mở khoá** chủ đề sau (UC-046), hoặc ngược lại **được mở khi chưa đủ**.
> **Bắt buộc:** hai bảng cập nhật trong cùng một transaction, cộng thêm một job đối chiếu định kỳ.

## Business rule

| # | Rule |
| --- | --- |
| BR-025-1 | Chỉ người học đã đăng nhập được xem tiến độ cá nhân và trạng thái mở/khóa của các chủ đề. |
| BR-025-2 | Chủ đề không có từ hợp lệ không hiển thị trong danh sách học chính. |
| BR-025-3 | Chủ đề không có điều kiện tiên quyết được mở sẵn; chủ đề có tiên quyết chỉ mở khi các chủ đề trước đã hoàn thành. |
| BR-025-4 | Phần trăm hoàn thành, số từ đã học và trạng thái phải tính từ tiến độ của chính người học, không dùng dữ liệu của người khác. |
| BR-025-5 | Người học mới chưa có tiến độ được trả 0% cho từng chủ đề; lọc theo cấp HSK không làm thay đổi trạng thái mở/khóa. |
| BR-025-6 | Phần trăm hoàn thành hiển thị phải nhất quán với dữ liệu từ đã học vì UC-046 dùng tiến độ này để mở chủ đề tiếp theo. |

## API · DB

```
GET /api/topics
GET /api/topics?hsk_level={n}
```

`topics` · `topic_words` · `user_topic_progress` · `user_knowledge_state` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `USER` mới | Chủ đề gốc `AVAILABLE`, còn lại `LOCKED`, % = 0 |
| T2 | Học 9/10 từ chủ đề A | % = 90, `status = COMPLETED`, chủ đề B mở |
| T3 | `hsk_level = 12` | 400 `INVALID_HSK_LEVEL` |
| T4 | Chủ đề không có `topic_words` | Không xuất hiện trong response |
| T5 | Không có token | 401 |
| T6 | Sửa tay `completion_percent` lệch DB | Job đối chiếu phát hiện và ghi log |

---

# UC-026 · Học từ mới trong một chủ đề

| | |
|---|---|
| **UC-ID** | UC-026 · **Actor** `USER` · **Pri** P0 · **Scope** MVP · **FT** 1.5 |

## Mô tả

Trong một chủ đề, người học xem và nghe từng từ mới cùng chữ Hán, pinyin, âm Hán Việt, nghĩa, ví dụ và từ liên quan. Khi học xong một từ và bấm “Tiếp”, hệ thống ghi nhận từ đó là đang học và tạo lịch ôn ban đầu.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chủ đề ở trạng thái `AVAILABLE` hoặc `IN_PROGRESS` (không `LOCKED`)
3. `topic_words` có từ chưa học

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Học một từ | `user_knowledge_state` tạo dòng, trạng thái `LEARNING`, có `next_review_at` |
| Học hết từ mới | `user_topic_progress` cập nhật; chuyển sang UC-027 |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn chủ đề, bấm "Học từ mới" |
| 2 | System | Kiểm chủ đề không `LOCKED` |
| 3 | System | `GET /api/topics/{id}/words?status=NEW&limit=10` |
| 4 | Client | Hiện thẻ từ thứ nhất: chữ, pinyin, âm Hán-Việt, nghĩa, ví dụ, nút audio |
| 5 | `USER` | Đọc, nghe audio, bấm "Tiếp" |
| 6 | System | `POST /api/topics/{id}/progress` — `{word_id, action: "LEARNED", duration_ms}` |
| 7 | System | Tạo `user_knowledge_state` trạng thái `LEARNING`, tính `next_review_at` |
| 8 | System | Cập nhật `user_topic_progress` **cùng transaction** |
| 9 | | Lặp 4–8 hết 10 từ |
| 10 | Client | Hiện "đã học 10 từ mới", gợi ý luyện nhận diện (UC-027) |

## Luồng thay thế

**A1 — Đã học hết từ mới trong chủ đề**
Bước 3 trả rỗng. Hiện "đã học hết từ mới", chuyển UC-027 hoặc UC-029.

**A2 — Bỏ giữa (mới học 4/10 từ)**
4 từ đã ghi vẫn giữ. Lần sau `status=NEW` trả 6 từ còn lại.

**A3 — Bấm "tôi đã biết từ này"**
Ghi trạng thái `MASTERED` luôn với mastery ban đầu trung bình, `next_review_at` xa hơn.
**Nhưng đặt cờ `self_declared: true`** — bài kiểm tra cuối chủ đề (UC-029) sẽ kiểm lại.

**A4 — Chủ đề bị khoá**
Bước 2 trả `TOPIC_LOCKED`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `TOPIC_LOCKED` | 403 | Chưa đạt 90% chủ đề tiên quyết | Hiện "hoàn thành chủ đề trước". **Kiểm ở server** |
| `TOPIC_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `WORD_NOT_IN_TOPIC` | 400 | `word_id` không thuộc chủ đề | Gian lận — người dùng học nhờ từ chủ đề khoá |
| `ALREADY_LEARNED` | 409 | Từ đã có `user_knowledge_state` | Không tạo dòng trùng; bỏ qua im lặng |
| `NO_NEW_WORDS` | 200 (rỗng) | Hết từ mới | Không phải lỗi (A1) |
| `IMPOSSIBLE_DURATION` | 400 | `duration_ms < 1000` cho 1 từ | Bấm "Tiếp" liên tục để farm — không tính mastery, chỉ ghi `LEARNING` |
| `PROGRESS_UPDATE_FAILED` | 500 | Transaction bước 8 lỗi | **Rollback cả bước 7** — không được có từ đã học mà % không tăng |
| `WORD_MISSING_AUDIO` | — | `audio_url` rỗng | Ẩn nút audio, vẫn học được |

> 🔴 **`WORD_NOT_IN_TOPIC` chặn một đường lách cụ thể:** chủ đề HSK5 bị khoá, nhưng người
> dùng gọi `POST /api/topics/1/progress` (chủ đề mở) với `word_id` của từ HSK5. Nếu không
> kiểm liên kết `topic_words`, họ học được nội dung khoá và cộng mastery cho nó.

> ⚠️ **`PROGRESS_UPDATE_FAILED` nối trực tiếp với `PROGRESS_PERCENT_MISMATCH` ở UC-025.**
> Đây là cùng một nguyên nhân gốc, nhìn từ hai đầu.

## Business rule

| # | Rule |
| --- | --- |
| BR-026-1 | Chỉ cho học từ trong chủ đề đã mở; server phải kiểm tra từ được ghi tiến độ thuộc đúng chủ đề đó. |
| BR-026-2 | Xem thẻ từ chưa đủ để ghi nhận đã học. Chỉ khi người học bấm “Tiếp” hoặc xác nhận đã học, hệ thống mới ghi tiến độ của từ. |
| BR-026-3 | Khi ghi một từ là `LEARNING`, hệ thống đồng thời tạo trạng thái kiến thức và lịch ôn ban đầu. |
| BR-026-4 | Cập nhật trạng thái từ và tiến độ chủ đề phải nhất quán trong cùng transaction. |
| BR-026-5 | Nếu người học dừng giữa danh sách, các từ đã xác nhận vẫn giữ tiến độ; lần sau chỉ lấy từ chưa học. |
| BR-026-6 | Nếu người học tự khai đã biết một từ, lưu cờ `self_declared`; bài kiểm tra cuối chủ đề phải kiểm tra lại và hạ mức nếu trả lời sai. |
| BR-026-7 | Học hết từ mới thì gợi ý luyện nhận diện hoặc kiểm tra cuối chủ đề; không tạo thêm từ mới giả. |

## API · DB

```
GET  /api/topics/{id}/words?status=NEW
POST /api/topics/{id}/progress
```

`topics` · `topic_words` · `words` · `characters` (đọc) · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Học 10 từ mới | 10 dòng `LEARNING`, % chủ đề tăng |
| T2 | Chủ đề `LOCKED` | 403 `TOPIC_LOCKED` |
| T3 | `word_id` của chủ đề khác | 400 `WORD_NOT_IN_TOPIC` |
| T4 | Học lại từ đã học | 409, không tạo dòng thứ hai |
| T5 | `duration_ms = 200` | Ghi `LEARNING` nhưng mastery = 0 |
| T6 | Lỗi ghi `user_topic_progress` | `user_knowledge_state` **cũng rollback** |
| T7 | "Tôi đã biết từ này" | `MASTERED` + `self_declared: true` |

---

# UC-027 · Luyện nhận diện từ trong chủ đề

| | |
|---|---|
| **UC-ID** | UC-027 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.5 |

## Mô tả

Người học làm câu hỏi trắc nghiệm để nhận diện các từ vừa học trong chủ đề. Các đáp án được lấy trong cùng chủ đề để bài luyện bám sát nội dung đang học.

## Tiền điều kiện

1. `USER` đã đăng nhập, chủ đề không khoá
2. Có ≥ 4 từ ở trạng thái `LEARNING` trở lên trong chủ đề (cần 1 đúng + 3 nhiễu)

## Hậu điều kiện

Mastery từng từ cập nhật; từ đúng liên tục 3 lần chuyển `LEARNING` → `MASTERED`.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Luyện nhận diện" trong chủ đề |
| 2 | System | Lấy từ đang học trong chủ đề, sinh câu hỏi với nhiễu **cùng chủ đề** |
| 3 | System | Ghi `served_at`, trả câu hỏi **không kèm** `is_correct` |
| 4 | `USER` | Chọn đáp án |
| 5 | Client | `POST /api/topics/{id}/practice/answer` |
| 6 | System | Chấm ở server, cập nhật mastery |
| 7 | System | Nếu từ đúng 3 lần liên tiếp → `MASTERED`, cập nhật `user_topic_progress` |
| 8 | | Lặp hết danh sách |

## Luồng thay thế

**A1 — Chủ đề chỉ có 3 từ**
Không đủ nhiễu cùng chủ đề. Lấy nhiễu từ chủ đề **cùng cấp HSK**, đặt cờ `mixed_distractors`.

**A2 — Tất cả từ đã `MASTERED`**
Trả rỗng, hiện "đã thuộc hết từ chủ đề này", gợi ý UC-029.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NOT_ENOUGH_WORDS` | 422 | < 4 từ khả dụng và không lấy được nhiễu | Hiện "học thêm từ trước" |
| `TOPIC_LOCKED` | 403 | Chủ đề khoá | Chặn ở server |
| `NO_WORDS_LEARNING` | 200 (rỗng) | Chưa học từ nào | Chuyển UC-026 |
| `OPTION_NOT_IN_QUESTION` | 400 | Đáp án lạ | Gian lận |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời 2 lần | Chặn |
| `DISTRACTOR_EQUALS_ANSWER` | 500 | Nhiễu trùng đáp án đúng (từ đồng nghĩa trong cùng chủ đề) | 🔴 **Câu hỏi không có đáp án duy nhất** — bỏ câu, ghi log |
| `MASTERY_TRANSITION_FAILED` | 500 | Lỗi khi chuyển `MASTERED` | Rollback, giữ `LEARNING` |

> 🔴 **`DISTRACTOR_EQUALS_ANSWER` là lỗi đặc thù của việc lấy nhiễu cùng chủ đề.** Chủ đề
> "Gia đình" có 爸爸 và 父亲 — cùng nghĩa "bố". Sinh câu "chọn nghĩa của 爸爸" với nhiễu 父亲
> thì **hai đáp án đều đúng**. Phải kiểm trùng **nghĩa**, không chỉ trùng `word_id`.

## Business rule

| # | Rule |
| --- | --- |
| BR-027-1 | Chỉ luyện các từ hợp lệ thuộc chủ đề đã mở; câu hỏi phải có một đáp án đúng và ba đáp án nhiễu không trùng nghĩa. |
| BR-027-2 | Ưu tiên đáp án nhiễu cùng chủ đề. Nếu không đủ, lấy từ cùng cấp HSK và đánh dấu bộ câu hỏi dùng nhiễu ngoài chủ đề. |
| BR-027-3 | Response phát câu hỏi không được chứa đáp án đúng hoặc `is_correct`. |
| BR-027-4 | Server chấm đáp án và cập nhật mastery; client không tự quyết định kết quả. |
| BR-027-5 | Ba lần trả lời đúng liên tiếp cho một từ chuyển từ đó từ `LEARNING` sang `MASTERED` và cập nhật tiến độ chủ đề cùng lúc. |
| BR-027-6 | Một câu trong cùng lượt luyện chỉ được chấm một lần; nộp lặp không được cộng mastery lần nữa. |
| BR-027-7 | Nếu không còn từ phù hợp, trả danh sách rỗng và gợi ý bài kiểm tra cuối chủ đề. |

## API · DB

```
GET  /api/topics/{id}/practice?mode=RECOGNITION
POST /api/topics/{id}/practice/answer
```

`topic_words` · `words` (đọc) · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | 10 từ đang học, đúng hết | Mastery cả 10 tăng |
| T2 | Chủ đề 3 từ | Nhiễu lấy ngoài, cờ `mixed_distractors` |
| T3 | Đúng từ X lần thứ 3 | X chuyển `MASTERED`, % chủ đề tăng |
| T4 | Chủ đề có 2 từ đồng nghĩa | Không sinh câu có cặp đó |
| T5 | Chủ đề khoá | 403 |

---

# UC-028 · Luyện nghe từ trong chủ đề

| | |
|---|---|
| **UC-ID** | UC-028 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 1.5 |

## Mô tả

Người học nghe cách đọc của một từ trong chủ đề rồi chọn chữ Hán hoặc nghĩa đúng. Kết quả được tính vào tiến độ luyện nghe của chủ đề.

## Tiền điều kiện

1. `USER` đã đăng nhập, chủ đề không khoá
2. Từ trong chủ đề có `audio_url`

## Hậu điều kiện

Mastery kỹ năng `LISTENING` cho từng từ cập nhật.

## Luồng chính

Giống UC-027, khác: bước 3 phát audio thay vì hiện chữ; đáp án là chữ Hán.

## Luồng thay thế

**A1 — Một số từ thiếu audio** — loại khỏi bộ câu hỏi, không báo lỗi.
**A2 — Cả chủ đề thiếu audio** — ẩn nút "Luyện nghe", server trả `NO_AUDIO_WORDS`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_AUDIO_WORDS` | 422 | Không từ nào có audio | Ẩn bước này khỏi luồng chủ đề |
| `AUDIO_LOAD_FAILED` | — | Mạng/CDN | Bỏ câu, **không tính sai** |
| `TOO_MANY_REPLAYS` | 400 | > 3 lần | Chặn |
| `TOPIC_LOCKED` | 403 | Chủ đề khoá | Chặn |
| `OPTION_NOT_IN_QUESTION` | 400 | Đáp án lạ | Gian lận |
| `ANSWER_ALREADY_SUBMITTED` | 409 | Trả lời 2 lần | Chặn |
| `HOMOPHONE_AMBIGUITY` | 500 | Nhiễu **đồng âm** với đáp án đúng | 🔴 Nghe không thể phân biệt — bỏ câu |

> 🔴 **`HOMOPHONE_AMBIGUITY` là `DISTRACTOR_EQUALS_ANSWER` phiên bản nghe, và tệ hơn.**
> 是 và 事 cùng đọc `shì`. Bài nghe "chọn chữ đúng" với hai đáp án này là **không có đáp án
> đúng** — người học nghe đúng vẫn chọn sai. Kiểm trùng **pinyin có dấu thanh** khi sinh nhiễu.

## Business rule

| # | Rule |
| --- | --- |
| BR-028-1 | Chỉ đưa vào bài luyện nghe những từ thuộc chủ đề đã mở và có audio hợp lệ. |
| BR-028-2 | Câu hỏi phát audio và yêu cầu chọn chữ Hán hoặc nghĩa tương ứng; response không được làm lộ đáp án. |
| BR-028-3 | Đáp án nhiễu phải đủ khác biệt để câu hỏi có đúng một đáp án, tránh trường hợp đồng âm hoặc trùng nghĩa không thể phân biệt. |
| BR-028-4 | Server chấm kết quả và cập nhật mastery kỹ năng `LISTENING`; không ghi như một lượt nhận diện chữ thuần túy. |
| BR-028-5 | Từ thiếu audio được bỏ khỏi bộ câu hỏi, không tính là câu sai của người học. |
| BR-028-6 | Nếu cả chủ đề không có audio dùng được, ẩn lựa chọn luyện nghe và trả trạng thái hết dữ liệu phù hợp. |

## API · DB

```
GET  /api/topics/{id}/practice?mode=LISTENING
POST /api/topics/{id}/practice/answer
```

`topic_words` · `words` (đọc `audio_url`) · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Nghe, chọn đúng | Mastery `LISTENING` tăng |
| T2 | Chủ đề không từ nào có audio | 422 `NO_AUDIO_WORDS`, ẩn bước |
| T3 | Chủ đề có 是 và 事 | Không sinh câu có cặp đó |
| T4 | Audio 404 | Bỏ câu, không ghi sai |

---

# UC-029 · Làm bài kiểm tra cuối chủ đề

| | |
|---|---|
| **UC-ID** | UC-029 · **Actor** `USER` · **Pri** P0 · **Scope** MVP · **FT** 1.5 |

## Mô tả

Sau các bước học và luyện tập, người học làm bài kiểm tra tổng hợp của chủ đề. Điểm bài kiểm tra quyết định mức hoàn thành và việc mở chủ đề tiếp theo.

## Tiền điều kiện

1. `USER` đã đăng nhập, chủ đề không khoá
2. Đã học ≥ 80% số từ trong chủ đề (không cho thi khi chưa học)
3. Có đủ câu hỏi cho chủ đề

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Đạt ≥ 90% | `user_topic_progress.status = COMPLETED`; kích hoạt UC-046 |
| Dưới 90% | Ghi kết quả, hiện các từ sai, gợi ý luyện lại; chủ đề sau **vẫn khoá** |
| Từ `self_declared` sai | **Hạ** từ `MASTERED` về `LEARNING` |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Kiểm tra cuối chủ đề" |
| 2 | System | Kiểm đã học ≥ 80% từ |
| 3 | System | `POST /api/topics/{id}/final-test/start` — sinh bộ đề trộn 4 dạng (nhìn chữ, nghe, viết, điền) |
| 4 | System | Tạo `attempts`, ghi `served_at`, trả `attempt_id` + câu hỏi **không kèm đáp án** |
| 5 | `USER` | Làm toàn bộ bài |
| 6 | Client | `POST /api/topics/{id}/final-test/{attempt_id}/submit` — gửi tất cả câu trả lời |
| 7 | System | Kiểm `attempt` chưa nộp, kiểm thời gian hợp lệ |
| 8 | System | **Chấm từng câu ở server**, ghi `attempt_answers` |
| 9 | System | Tính điểm, cập nhật mastery từng từ, tính lại `completion_percent` |
| 10 | System | Nếu ≥ 90% → `COMPLETED` + gọi UC-046; tất cả trong **một transaction** |
| 11 | Client | Hiện điểm, từ sai, nút "luyện lại các từ sai" |

## Luồng thay thế

**A1 — Dưới 90%**
Hiện danh sách từ sai kèm nút chuyển sang UC-027 cho đúng các từ đó. Cho thi lại **sau 10 phút**.

**A2 — Từ `self_declared: true` trả lời sai**
Hạ về `LEARNING`, `completion_percent` **giảm**. Đây là cơ chế kiểm lại của BR-026-6.

**A3 — Bỏ giữa bài**
`attempts` giữ trạng thái `IN_PROGRESS`. Cho nộp tiếp trong 24h; quá hạn → `ABANDONED`,
không tính điểm.

**A4 — Thi lại**
Lần thi mới tạo `attempts` mới. `completion_percent` lấy kết quả **cao nhất**, không phải mới nhất.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NOT_ENOUGH_LEARNED` | 422 | Học < 80% từ | Hiện "học thêm trước khi kiểm tra" |
| `TOPIC_LOCKED` | 403 | Chủ đề khoá | Chặn ở server |
| `ATTEMPT_ALREADY_SUBMITTED` | 409 | Nộp 2 lần cùng `attempt_id` | 🔴 **Bắt buộc** — nếu không, nộp lại nhiều lần để dò đáp án |
| `ATTEMPT_NOT_OWNED` | 403 | `attempt_id` của người khác | **IDOR** — kiểm `attempts.user_id = current_user` |
| `ATTEMPT_EXPIRED` | 422 | Quá 24h | `ABANDONED`, không tính điểm |
| `ANSWER_FOR_UNKNOWN_QUESTION` | 400 | Trả lời câu không thuộc `attempt` | Gian lận |
| `MISSING_ANSWERS` | 200 | Thiếu câu trả lời | **Không chặn** — câu thiếu tính là sai |
| `IMPOSSIBLE_DURATION` | 400 | `submitted_at − served_at < số câu × 2s` | Nghi gian lận, ghi log, **vẫn chấm** nhưng đánh cờ |
| `RETRY_TOO_SOON` | 429 | Thi lại trong 10 phút | Hiện thời gian còn chờ |
| `SCORE_CALCULATION_FAILED` | 500 | Lỗi chấm | Rollback toàn bộ, `attempt` về `IN_PROGRESS` cho nộp lại |
| `UNLOCK_FAILED` | 500 | Lỗi mở chủ đề sau | **Rollback cả điểm bài thi** — không để đạt 90% mà không mở khoá |

> 🔴 **`ATTEMPT_NOT_OWNED` là lỗ hổng IDOR thật.** Mục B trong
> Hiến pháp đã ghi `attempts` là "chỉ người làm bài". Ở UC này
> nó cụ thể: nộp `attempt_id` của người khác mà không kiểm sở hữu thì ghi điểm vào bài của họ.

> 🔴 **`UNLOCK_FAILED` — vì sao bước 9 và 10 phải cùng transaction.** Nếu tách: ghi điểm
> thành công, mở khoá lỗi → người học thấy 95% mà chủ đề sau vẫn khoá, và **thi lại cũng không
> mở được** vì A4 lấy điểm cao nhất, lần sau thấp hơn thì không kích hoạt mở lại. Người học bị
> kẹt vĩnh viễn.

## Business rule

| # | Rule |
| --- | --- |
| BR-029-1 | Chỉ người học đã học ít nhất 80% số từ của chủ đề mới được bắt đầu bài kiểm tra cuối chủ đề. |
| BR-029-2 | Server tạo bộ câu hỏi cho chủ đề, không gửi đáp án đúng trong response bắt đầu bài. |
| BR-029-3 | Mỗi `attempt_id` chỉ được nộp một lần; server chấm từng câu và không tin điểm client tự tính. |
| BR-029-4 | Ghi câu trả lời, điểm, mastery, phần trăm hoàn thành và trạng thái chủ đề trong cùng transaction. |
| BR-029-5 | Đạt ít nhất 90% thì hoàn thành chủ đề và mở chủ đề kế tiếp theo UC-046; dưới ngưỡng thì gợi ý luyện lại những từ sai. |
| BR-029-6 | Từ người học tự khai đã biết nhưng trả lời sai phải được hạ về `LEARNING`. |
| BR-029-7 | Bài bỏ dở quá hạn không được chấm. Thi lại tạo lượt mới; phần trăm hoàn thành lấy kết quả cao nhất hợp lệ. |

## API · DB

```
POST /api/topics/{id}/final-test/start
POST /api/topics/{id}/final-test/{attempt_id}/submit
```

`topic_words` · `words` · `questions` · `question_options` (đọc) · `attempts` · `attempt_answers` · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đúng 95% | `COMPLETED`, chủ đề sau mở |
| T2 | Đúng 85% | Không đạt, chủ đề sau **vẫn khoá**, hiện từ sai |
| T3 | Học 50% từ rồi thi | 422 `NOT_ENOUGH_LEARNED` |
| T4 | Nộp `attempt_id` người khác | 403 `ATTEMPT_NOT_OWNED` |
| T5 | Nộp lần 2 | 409 `ATTEMPT_ALREADY_SUBMITTED` |
| T6 | Bước 4 response | **Không** chứa đáp án đúng |
| T7 | Thi lần 1 đạt 95%, lần 2 đạt 70% | `completion_percent` giữ 95 |
| T8 | Lỗi mở khoá ở bước 10 | Điểm bài thi **cũng rollback** |
| T9 | Thi lại sau 2 phút | 429 `RETRY_TOO_SOON` |
| T10 | Từ `self_declared` trả lời sai | Hạ `LEARNING`, % giảm |

---

# UC-030 · Xem video có phụ đề tương tác

| | |
|---|---|
| **UC-ID** | UC-030 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |

## Mô tả

Người học xem video tiếng Trung kèm phụ đề song ngữ và pinyin. Họ có thể nghe lại từng câu, xem chậm, lặp câu hoặc ẩn bớt phụ đề để tự kiểm tra khả năng nghe hiểu.

> ⚠️ **Scope V2.** Feature tree ghi khối lượng **2–4 tuần**, phần khó nhất là đồng bộ phụ đề
> theo thời gian video. Nguồn video **chưa chốt**.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `learning.videos` có video `PUBLISHED`, cột `subtitles` có phụ đề đã gắn timestamp
3. Video YouTube còn khả dụng và cho phép nhúng

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Xem video | Vị trí xem và phần trăm hợp lệ của chính người học được lưu trong `user_progress` với `target_type = 'VIDEO'` |
| Lưu từ | UC-032 xử lý |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Chọn video theo cấp HSK/chủ đề |
| 2 | System | `GET /api/videos` — danh sách kèm cấp, độ dài, ảnh bìa |
| 3 | `USER` | Chọn video |
| 4 | System | `GET /api/videos/{id}/subtitles` — phụ đề kèm `start_ms`, `end_ms`, chữ, pinyin, nghĩa |
| 5 | Client | Load player, đồng bộ phụ đề theo `currentTime` |
| 6 | `USER` | Xem; dùng nút tua lại câu / giảm tốc / lặp câu |
| 7 | Client | Highlight câu phụ đề đang phát |
| 8 | `USER` | Bấm từ trong phụ đề → UC-031 |
| 9 | Client | `POST /api/videos/{id}/progress` — gửi vị trí xem để server cập nhật `user_progress.position_ms` cho video này |

## Luồng thay thế

**A1 — Tua lại câu** — client `seek` về `start_ms` của câu hiện tại.
**A2 — Lặp một câu** — client lặp trong `[start_ms, end_ms]` tới khi tắt.
**A3 — Ẩn lớp phụ đề** — thuần client, lưu `localStorage`.
**A4 — Video bị xoá ở nguồn (YouTube)** — hiện "video không còn khả dụng", đánh dấu `videos.status = UNAVAILABLE` để `CONTENT_ADMIN` xử lý.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `VIDEO_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `VIDEO_UNAVAILABLE` | 410 | Nguồn đã xoá | Ẩn khỏi danh sách, báo `CONTENT_ADMIN` (A4) |
| `NO_SUBTITLES` | 422 | Video chưa có phụ đề | Không mở — video không phụ đề mất hết giá trị học |
| `SUBTITLE_TIMING_INVALID` | 500 | `start_ms ≥ end_ms` hoặc chồng lấn | Phụ đề nhảy sai, không highlight đúng câu. Kiểm khi nhập |
| `SUBTITLE_OUT_OF_RANGE` | 500 | `end_ms > video_duration_ms` | Phụ đề dài hơn video — dữ liệu sai |
| `VIDEO_SOURCE_BLOCKED` | — | YouTube chặn nhúng ở domain | Hiện hướng dẫn mở trên YouTube |

> Nơi lưu tiến độ đã có trong `docs/reference/database.md`: `learning.user_progress`
> với `target_type = 'VIDEO'`, `target_id = video.id` và `position_ms`. Không tạo bảng
> `user_video_progress` riêng.

> ⚠️ **`VIDEO_SOURCE_BLOCKED` và bản quyền.** Constitution cấm tải video người khác về máy
> chủ. Nếu nhúng YouTube thì phụ thuộc hoàn toàn vào nguồn: video bị xoá, bị chặn nhúng, hoặc
> chủ kênh đổi quyền → tính năng chết mà ta không làm gì được.

## Business rule

| # | Rule |
| --- | --- |
| BR-030-1 | UC này thuộc V2. Nguồn phát là video YouTube được phép nhúng; chỉ hiển thị video đã xuất bản, còn cho phép nhúng và có phụ đề gắn thời điểm. Không tải lại hoặc lưu bản sao video từ YouTube trên server. |
| BR-030-2 | Phụ đề phải đồng bộ với thời gian phát; mỗi đoạn có mốc bắt đầu, kết thúc và nội dung hiển thị đúng thứ tự. |
| BR-030-3 | Tua lại câu, lặp câu, thay đổi tốc độ và ẩn lớp phụ đề là thao tác xem trên client, không tự cộng mastery. |
| BR-030-4 | Bấm từ trong phụ đề chuyển sang UC-031; lưu từ chuyển sang UC-032. |
| BR-030-5 | Nếu video nguồn không còn phát được, báo rõ cho người học và đánh dấu nội dung để quản trị xử lý. |
| BR-030-6 | Tiến độ xem lưu trong `user_progress` theo người học và video; chỉ ghi vị trí/phần trăm hợp lệ của chính người học, không coi chỉ mở video là đã hoàn thành. |
| BR-030-7 | Khi nhập video, quản trị viên lưu YouTube video ID hoặc URL hợp lệ và kiểm tra khả năng nhúng. Video bị xóa hoặc chặn nhúng được chuyển `UNAVAILABLE` và không còn trong danh sách học. |

## API · DB

```
GET  /api/videos
GET  /api/videos/{id}/subtitles
POST /api/videos/{id}/progress
```

`learning.videos` (`subtitles` JSONB, đọc) · `learning.user_progress` (`VIDEO`, ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Mở video có phụ đề | Player chạy, phụ đề đồng bộ |
| T2 | Video không phụ đề | 422 `NO_SUBTITLES` |
| T3 | Phụ đề `start_ms = end_ms` | Kiểm chặn khi nhập |
| T4 | Xem 5 phút, thoát, mở lại | Tiếp tục từ `position_ms` đã lưu cho chính người học |
| T5 | YouTube ID đã xoá | 410, `status = UNAVAILABLE` |
| T6 | YouTube chặn nhúng | Báo không thể phát và hướng dẫn mở trên YouTube; không tính tiến độ khi không xem được |

---

# UC-031 · Bấm từ trong phụ đề xem nghĩa

| | |
|---|---|
| **UC-ID** | UC-031 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |

## Mô tả

Khi bấm vào một từ trên phụ đề, video tạm dừng và hiện chữ Hán, pinyin, nghĩa cùng âm đọc của từ đó. Người học có thể lưu từ ngay tại đây.

## Tiền điều kiện

1. Đang xem video (UC-030)
2. Phụ đề đã **tách từ** (word segmentation) — không phải cả câu liền
3. Từ có dữ liệu trong kho từ hoặc chữ Hán hợp lệ; phụ đề đã được tách từ cho chức năng bấm từ

## Hậu điều kiện

Không đổi dữ liệu học. Chỉ ghi `feature_usage` nếu dùng lượt tra (tính năng 4.1 tốn phí).

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm một từ trong phụ đề |
| 2 | Client | Tạm dừng video |
| 3 | Client | Dùng dữ liệu từ vựng đã được server gắn vào response phụ đề, nếu có |
| 4 | Client | Nếu chưa có dữ liệu từ vựng, gọi `GET /api/dictionary/lookup?word=X` |
| 5 | Client | Hiện popup: chữ, pinyin, âm Hán-Việt, nghĩa, audio, nút lưu |
| 6 | `USER` | Đọc; có thể bấm lưu (UC-032) hoặc đóng |
| 7 | Client | Đóng popup, phát tiếp video |

## Luồng thay thế

**A1 — Response phụ đề đã kèm thông tin từ** — không gọi API từ điển, hiện ngay.
**A2 — Từ không có trong từ điển** — hiện "chưa có trong từ điển", cho báo lỗi để `CONTENT_ADMIN` bổ sung.
**A3 — Bấm vào dấu câu hoặc khoảng trắng** — bỏ qua, không mở popup.
**A4 — Bấm nhiều từ liên tiếp** — popup cũ đóng, popup mới mở; video vẫn dừng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `WORD_NOT_SEGMENTED` | 500 | 🔴 Phụ đề chưa tách từ | **Bấm cả câu thay vì một từ** — tính năng vô dụng. Phải tách khi nhập phụ đề |
| `WORD_NOT_IN_DICTIONARY` | 404 | Từ không có trong kho từ hoặc chữ Hán | Hiện "chưa có", cho báo lỗi (A2) |
| `AMBIGUOUS_SEGMENTATION` | — | Tách từ sai ranh giới | Xem ghi chú dưới |
| `QUOTA_EXCEEDED` | 402 | Hết lượt tra (nếu tính vào 4.1) | ⚠️ Chờ `TODO(PAYMENT_SCOPE)` |
| `DICTIONARY_LOOKUP_FAILED` | 500 | Lỗi server | Hiện "không tra được, thử lại" |

> 🔴 **`AMBIGUOUS_SEGMENTATION` là vấn đề ngôn ngữ, không phải vấn đề code.** Tiếng Trung
> viết liền không khoảng trắng. Câu 中国人 tách được thành 中国 + 人 (người Trung Quốc) hoặc
> 中 + 国人. Tách sai thì popup hiện nghĩa sai — **người học học sai mà không biết**. Đây là
> rủi ro cao hơn lỗi kỹ thuật thường: dạy sai còn tệ hơn không dạy.
> Giảm bằng cách: dùng thư viện tách từ có từ điển, và cho phép bấm-kéo chọn nhiều ký tự.

## Business rule

| # | Rule |
| --- | --- |
| BR-031-1 | UC này thuộc V2; chỉ mở popup cho từ đã được tách đúng trong phụ đề. Bấm dấu câu hoặc khoảng trắng không mở popup. |
| BR-031-2 | Khi người học bấm từ, video tạm dừng và popup hiển thị chữ, pinyin, nghĩa và audio nếu có. |
| BR-031-3 | Ưu tiên dữ liệu từ vựng đã được server gắn vào response phụ đề; chỉ gọi API từ điển khi response chưa có dữ liệu đó. Không giả định có cột `video_subtitles.vocabulary`. |
| BR-031-4 | Nếu từ không có trong kho dữ liệu, báo chưa có nghĩa và cho người học báo lỗi nội dung; không tự bịa nghĩa. |
| BR-031-5 | Mở popup và đọc dữ liệu đã tải sẵn không tiêu tốn lượt tra từ; nếu gọi API từ điển có quota, chỉ tính theo quy tắc quota của API đó. |
| BR-031-6 | Từ nhiều nghĩa hoặc tách từ mơ hồ phải hiển thị nghĩa gắn với ngữ cảnh phụ đề khi dữ liệu cho phép. |

## API · DB

```
GET /api/dictionary/lookup?word={x}
```

`learning.videos.subtitles` (JSONB, đọc) · kho `learning.lexemes`/`characters` (tra từ qua API từ điển). Metadata từ vựng kèm response, nếu có, là dữ liệu server suy ra chứ không phải cột `video_subtitles.vocabulary`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bấm từ đã có metadata trong response phụ đề | Popup hiện ngay, không gọi API từ điển |
| T2 | Bấm từ chưa có metadata trong response | Gọi `/api/dictionary/lookup` |
| T3 | Bấm dấu phẩy | Không mở popup |
| T4 | Từ không trong từ điển | 404, hiện nút báo lỗi |
| T5 | Mở popup | Video dừng |
| T6 | Phụ đề chưa tách từ | Kiểm chặn khi nhập phụ đề |

---

# UC-032 · Lưu từ từ phụ đề vào sổ tay

| | |
|---|---|
| **UC-ID** | UC-032 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |

## Mô tả

Người học lưu một từ trong phụ đề vào sổ tay hoặc bộ flashcard. Câu chứa từ đó trong video được lưu kèm làm ví dụ để sau này ôn lại trong đúng ngữ cảnh.

## Tiền điều kiện

1. Popup UC-031 đang mở
2. `USER` đã đăng nhập
3. Có sổ tay hoặc bộ flashcard đích thuộc người học, hoặc người học tạo mới

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Lưu sổ tay | Thêm `collection_items` vào `collections.kind = 'NOTEBOOK'`, kèm nguồn video và câu ví dụ nếu hợp lệ |
| Lưu flashcard | Thêm `collection_items` vào `collections.kind = 'FLASHCARD_DECK'`; tạo lịch ôn ban đầu trong `user_progress` |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Lưu vào sổ tay" hoặc "Thêm flashcard" |
| 2 | Client | Nếu flashcard: hiện chọn deck hoặc tạo deck mới |
| 3 | `USER` | Chọn deck |
| 4 | Client | `POST /api/notes` hoặc `POST /api/flashcard-decks/{id}/cards` |
| 5 | System | Kiểm `collections.user_id` của sổ tay hoặc bộ thẻ bằng người đang đăng nhập |
| 6 | System | Kiểm trùng — từ này đã trong deck chưa |
| 7 | System | Ghi `collection_items` kèm `source_type = VIDEO`, `source_ref` trỏ đến video/đoạn phụ đề và câu ví dụ trong `content` nếu nguồn hợp lệ |
| 8 | System | Nếu là flashcard: tạo `user_progress` với `target_type = FLASHCARD` và `next_review_at` ban đầu trong cùng transaction |
| 9 | Client | Hiện "đã lưu", đóng popup, phát tiếp video |

## Luồng thay thế

**A1 — Từ đã có trong deck** — không tạo dòng trùng, hiện "đã có trong bộ này", cho chuyển deck khác.
**A2 — Chưa có bộ đích** — tạo `collections` đúng loại rồi thêm `collection_items` trong **một** thao tác.
**A3 — Lưu cả hai nơi** — cho phép; hai bản ghi độc lập.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `DECK_NOT_OWNED` | 403 | 🔴 Bộ của người khác | **IDOR** — `collections.user_id` phải bằng người đang đăng nhập |
| `DECK_NOT_FOUND` | 404 | ID sai | Hiện chọn deck khác |
| `CARD_ALREADY_IN_DECK` | 409 | Từ đã có | Không tạo trùng (A1) |
| `DECK_LIMIT_EXCEEDED` | 422 | Deck > 500 thẻ | Hiện "bộ đã đầy, tạo bộ mới" |
| `NOTE_LIMIT_EXCEEDED` | 422 | > 1000 ghi chú | Chặn spam |
| `SOURCE_SUBTITLE_NOT_FOUND` | 400 | `subtitle_id` không thuộc video | Vẫn lưu từ nhưng **không** gắn câu ví dụ |
| `UNAUTHORIZED` | 401 | Token hết hạn giữa lúc xem | Refresh rồi gửi lại — không mất thao tác lưu |

> 🔴 **`DECK_NOT_OWNED` là IDOR đúng loại mục B đã ghi.** `POST /api/flashcard-decks/999/cards`
> với bộ 999 của người khác: nếu chỉ kiểm "đã đăng nhập" thì ta vừa cho ghi vào dữ liệu của
> người lạ. Cả UC-029 (`ATTEMPT_NOT_OWNED`) và UC này đều là cùng một lỗ hổng ở hai chỗ khác
> nhau — nên có **một** `OwnershipService` dùng chung, không kiểm rời rạc từng endpoint.

## Business rule

| # | Rule |
| --- | --- |
| BR-032-1 | UC này thuộc V2; chỉ người học đã đăng nhập được lưu từ từ phụ đề vào sổ tay hoặc bộ flashcard của mình. |
| BR-032-2 | Khi lưu, giữ định danh video, đoạn phụ đề và câu chứa từ làm ví dụ nếu nguồn còn hợp lệ. |
| BR-032-3 | Nếu lưu vào flashcard, server phải kiểm tra bộ thẻ thuộc người học trước khi ghi. |
| BR-032-4 | Cùng một từ không được tạo hai thẻ trùng trong cùng bộ; từ đã có thì báo rõ và cho chọn bộ khác. |
| BR-032-5 | Nếu tạo bộ mới rồi thêm thẻ, hai bước phải hoàn tất cùng nhau; không để bộ rỗng do thêm thẻ thất bại. |
| BR-032-6 | Thẻ mới phải có lịch ôn ban đầu. Nếu không còn đoạn phụ đề hợp lệ, có thể lưu từ nhưng không gắn ví dụ sai nguồn. |

## API · DB

```
POST /api/notes
POST /api/flashcard-decks/{id}/cards
POST /api/flashcard-decks          (tạo deck mới — A2)
```

`learning.lexemes` · `learning.videos.subtitles` (đọc) · `learning.collections` · `learning.collection_items` (ghi) · `learning.user_progress` (lịch ôn flashcard)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Lưu vào sổ tay | `collection_items` trong `NOTEBOOK` có `source_type = VIDEO` và `source_ref` hợp lệ |
| T2 | Thêm vào bộ thẻ của mình | `collection_items` trong `FLASHCARD_DECK` có dòng `user_progress` với `next_review_at` |
| T3 | Deck của người khác | 403 `DECK_NOT_OWNED` |
| T4 | Thêm từ đã có | 409 `CARD_ALREADY_IN_DECK` |
| T5 | Deck đã 500 thẻ | 422 `DECK_LIMIT_EXCEEDED` |
| T6 | `subtitle_id` sai | Lưu từ, không có câu ví dụ |

---

# UC-120 · Luyện Dictation từ video

| | |
| --- | --- |
| **UC-ID** | UC-120 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |
| **Quan hệ** | `«extend»` **UC-030** — chỉ làm được khi đang xem video |

## Mô tả

Nghe một câu phụ đề, gõ lại chữ Hán, server chấm và chỉ ra **sai chữ** hay **sai thanh điệu**.

Đây là cơ chế học chủ động chính của schinese.net. Khác với xem phụ đề thụ động: người học
phải tự tái tạo chữ từ âm thanh, nên phát hiện đúng chỗ mình nghe chưa ra.

> **Điểm khác biệt so với schinese.net:** CNHSK trả **hai điểm riêng** — `char_score` và
> `tone_score`. schinese.net chỉ trả một điểm chung. Thanh điệu là lỗi phổ biến nhất của
> người Việt học tiếng Trung, nên tách riêng mới chỉ ra được chỗ cần sửa.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Video có `subtitles` JSONB với ít nhất một câu hợp lệ
3. Câu được chọn có `start_ms < end_ms` và nằm trong `duration_ms`

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Gõ đúng hoàn toàn | `dictation_attempts` thêm dòng, `char_score = 100`, `tone_score = 100` |
| Gõ sai | Thêm dòng kèm `diff` JSONB chỉ rõ từng vị trí sai |
| Bỏ giữa | Không ghi gì — chỉ ghi khi người học bấm nộp |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Đang xem video, bấm "Luyện chép" ở một câu phụ đề |
| 2 | Client | `seek` về `start_ms`, phát tới `end_ms`, **ẩn phụ đề chữ Hán** |
| 3 | `USER` | Nghe, gõ lại chữ Hán vào ô nhập |
| 4 | `USER` | Bấm "Kiểm tra" |
| 5 | Client | `POST /api/videos/{id}/dictation` với `subtitle_index` + `submitted_text` |
| 6 | System | Lấy `expected_text` từ `videos.subtitles[subtitle_index].text_cn` |
| 7 | System | Chuyển cả hai chuỗi sang pinyin, tách mỗi âm tiết thành **phụ âm đầu · phần vận · thanh điệu** |
| 8 | System | So từng cặp, tính `char_score` (đúng chữ) và `tone_score` (đúng thanh) riêng |
| 9 | System | Ghi `dictation_attempts` kèm `expected_text` (chụp lại) và `diff` JSONB |
| 10 | System | Trả **200** với hai điểm + danh sách lỗi |
| 11 | Client | Hiện phụ đề đúng, tô màu từng chữ theo loại lỗi |

## Luồng thay thế

**A1 — Nghe lại trước khi nộp** — client lặp `[start_ms, end_ms]`, không gọi API.
**A2 — Xem đáp án mà không gõ** — hiện phụ đề, **không ghi** `dictation_attempts`. Bỏ qua không tính là làm sai.
**A3 — Gõ pinyin thay vì chữ Hán** — server nhận cả hai; nếu nhận pinyin thì `char_score` không tính, chỉ tính `tone_score`.
**A4 — Câu quá ngắn** — câu dưới 1 giây không mở Dictation (nghe không kịp).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `SUBTITLE_INDEX_INVALID` | 404 | `subtitle_index` vượt mảng `subtitles` | Về trình phát |
| `DICTATION_TEXT_EMPTY` | 422 | Ô nhập rỗng | Lỗi dưới field, không gọi API |
| `DICTATION_TEXT_TOO_LONG` | 422 | Dài hơn `expected_text` ×3 | Chặn spam |
| `PINYIN_CONVERT_FAILED` | 500 | Không chuyển được chữ sang pinyin (chữ lạ, ký tự rác) | Vẫn chấm `char_score`, `tone_score = NULL` kèm cảnh báo |
| `SUBTITLE_TOO_SHORT` | 422 | Câu dưới 1 giây | Không mở Dictation cho câu này (A4) |

> ⚠️ **`PINYIN_CONVERT_FAILED` không được chặn cả tính năng.** Nếu một chữ trong phụ đề
> không có pinyin trong kho dữ liệu, vẫn chấm được phần chữ. Trả `tone_score = NULL` và
> hiện "chưa chấm được thanh điệu câu này" — tốt hơn là báo lỗi 500 rồi không chấm gì.

## Business rule

| # | Rule |
| --- | --- |
| BR-120-1 | Chấm Dictation PHẢI ở server (`BUS-09`). Client chỉ gửi văn bản người học gõ, không gửi điểm. |
| BR-120-2 | `expected_text` PHẢI được chụp lại vào `dictation_attempts` lúc chấm. Nếu `CONTENT_ADMIN` sửa phụ đề sau đó, lịch sử vẫn giải thích được vì sao người học sai. |
| BR-120-3 | `char_score` và `tone_score` là **hai điểm riêng**, không gộp thành một điểm trung bình. |
| BR-120-4 | So sánh PHẢI bỏ qua dấu câu và khoảng trắng. Người học gõ 我是学生 hay 我是学生。đều đúng. |
| BR-120-5 | Chữ phồn thể gõ vào câu giản thể tính là **sai chữ**, không tự quy đổi. HSK dùng giản thể. |
| BR-120-6 | Dictation KHÔNG tính vào hạn mức lượt — chấm hoàn toàn ở server, không gọi API ngoài. |
| BR-120-7 | `diff` JSONB PHẢI ghi đủ `{pos, expected, got, kind}` với `kind` thuộc `CHAR_WRONG` · `TONE_WRONG` · `MISSING` · `EXTRA`. |
| BR-120-8 | Bỏ qua (A2) KHÔNG ghi `dictation_attempts`. Xem đáp án không phải là làm sai. |

## API · DB

```
POST /api/videos/{id}/dictation
     body: { subtitle_index, submitted_text }
     200:  { char_score, tone_score, expected_text, diff[] }
```

`videos` (đọc `subtitles`) · `dictation_attempts` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Gõ đúng hoàn toàn | `char_score = 100`, `tone_score = 100` |
| T2 | 学西 thay vì 学习 | `char_score` giảm, `tone_score` giảm — `kind = TONE_WRONG` |
| T3 | 学期 thay vì 学习 | `char_score` giảm, `kind = CHAR_WRONG` |
| T4 | Thiếu một chữ cuối | `kind = MISSING` tại vị trí cuối |
| T5 | Thêm chữ lạ giữa câu | `kind = EXTRA` |
| T6 | Gõ có dấu câu 我是学生。 | Đúng — BR-120-4 bỏ qua dấu câu |
| T7 | Gõ phồn thể 學習 | `CHAR_WRONG` — BR-120-5 không quy đổi |
| T8 | Ô nhập rỗng | 422, lỗi dưới field |
| T9 | `subtitle_index` = 999 | 404 `SUBTITLE_INDEX_INVALID` |
| T10 | Phụ đề có chữ không có pinyin | `tone_score = NULL`, vẫn trả `char_score` |
| T11 | Bấm "xem đáp án" rồi thoát | `dictation_attempts` KHÔNG có dòng mới |

---

# UC-121 · Xem kết quả Dictation và lỗi từng chữ

| | |
| --- | --- |
| **UC-ID** | UC-121 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |
| **Quan hệ** | `«extend»` **UC-120** — chỉ xem được sau khi đã nộp |

## Mô tả

Xem kết quả một lượt Dictation: điểm chữ, điểm thanh điệu, và từng chữ sai sai thế nào.

## Tiền điều kiện

1. `USER` đã nộp ít nhất một lượt Dictation cho câu đó
2. Dòng `dictation_attempts` thuộc chính `USER` này (`BUS-01`)

## Hậu điều kiện

Không đổi dữ liệu — màn chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Sau khi nộp, màn tự hiện kết quả |
| 2 | Client | Hiện `expected_text` với từng chữ tô màu theo `diff[].kind` |
| 3 | Client | Hiện hai thanh điểm riêng: chữ và thanh điệu |
| 4 | `USER` | Bấm một chữ sai → hiện pinyin đúng vs pinyin đã gõ |
| 5 | `USER` | Chọn "Làm lại câu này" → về UC-120, hoặc "Luyện nói câu này" → UC-122 |

## Luồng thay thế

**A1 — Xem lịch sử các lượt trước của cùng câu** — `GET /api/videos/{id}/dictation/history?subtitle_index=`. Hiện tiến bộ qua từng lượt.
**A2 — Thêm chữ sai vào flashcard** — gọi UC-063, giúp ôn lại chữ vừa sai.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ATTEMPT_NOT_FOUND` | 404 | ID lượt không tồn tại | Về trình phát |
| `ATTEMPT_NOT_OWNED` | 403 | Lượt của người khác (`BUS-01`) | 403, KHÔNG trả 404 — xem `HR-07` |

## Business rule

| # | Rule |
| --- | --- |
| BR-121-1 | Chỉ xem được lượt Dictation của **chính mình** (`BUS-01`). Server kiểm quyền sở hữu, không dựa vào việc màn không hiện nút. |
| BR-121-2 | Lỗi thanh điệu PHẢI hiện rõ *thanh nào thành thanh nào*, ví dụ "xí (thanh 2) → xī (thanh 1)". Chỉ nói "sai thanh" thì người học không sửa được. |
| BR-121-3 | Màn kết quả KHÔNG hiện điểm trung bình gộp của hai loại điểm. Gộp lại là mất thông tin hành động được. |

## API · DB

```
GET /api/videos/{id}/dictation/{attemptId}
GET /api/videos/{id}/dictation/history?subtitle_index=
```

`dictation_attempts` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xem lượt vừa nộp | Hiện đủ hai điểm + diff |
| T2 | Bấm chữ sai thanh | Hiện "xí (thanh 2) → xī (thanh 1)" |
| T3 | Xem lượt của user khác | 403 `ATTEMPT_NOT_OWNED` |
| T4 | Xem lịch sử 5 lượt cùng câu | Hiện tiến bộ theo thời gian |

---

# UC-122 · Luyện Shadowing từ video

| | |
| --- | --- |
| **UC-ID** | UC-122 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |
| **Quan hệ** | `«extend»` **UC-030** · `«include»` **UC-096** (trừ điểm khi dùng tính năng tốn phí) |

## Mô tả

Đọc theo một câu phụ đề, ghi âm, nhận phản hồi phát âm theo từng âm tiết.

> ⚠️ **Tính năng này gọi dịch vụ bên ngoài**, nên tính vào hạn mức lượt (`BUS-03`) và
> phải tuân mục *chấm bằng dịch vụ bên ngoài* của `BUS-09`: audio đi **client → server ta
> → dịch vụ ngoài**. Client KHÔNG gọi trực tiếp và KHÔNG BAO GIỜ gửi điểm lên.

## Tiền điều kiện

1. `USER` đã đăng nhập và **đã xác thực email** (`FR-010` — thao tác tốn phí cần xác thực)
2. Còn lượt free hoặc còn điểm
3. Trình duyệt đã cho quyền microphone
4. Câu phụ đề có `start_ms < end_ms`

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Chấm thành công | `shadowing_attempts` thêm dòng · lượt bị trừ · **file audio bị bỏ** |
| Dịch vụ ngoài lỗi | **Hoàn lượt** (`BUS-03`) · không ghi dòng nào |
| Người học không cho quyền mic | Không gọi API · không trừ lượt |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Đang xem video, bấm "Luyện nói" ở một câu |
| 2 | Client | Phát câu mẫu `[start_ms, end_ms]` |
| 3 | `USER` | Bấm ghi âm, đọc theo, bấm dừng |
| 4 | Client | Gửi audio + `subtitle_index` tới **server ta** qua `POST /api/videos/{id}/shadowing` |
| 5 | System | Kiểm hạn mức — **UC-096** trừ lượt |
| 6 | System | Gửi audio + `expected_text` tới dịch vụ đánh giá phát âm |
| 7 | System | Nhận điểm theo âm tiết, **kiểm tính hợp lệ** (điểm trong 0–100, số âm tiết khớp) |
| 8 | System | Ghi `shadowing_attempts` kèm `provider` và `credit_cost` · **bỏ file audio** |
| 9 | System | Trả **200** với các điểm + mảng âm tiết |
| 10 | Client | Hiện câu với từng âm tiết tô màu theo điểm |

## Luồng thay thế

**A1 — Nghe lại mẫu trước khi ghi** — thuần client, không trừ lượt.
**A2 — Nghe lại bản ghi của mình trước khi gửi** — client giữ audio trong bộ nhớ, chưa gửi, chưa trừ lượt.
**A3 — Ghi lại** — xoá bản cũ trong bộ nhớ client, chưa trừ lượt lần nào.
**A4 — Dịch vụ ngoài timeout** — hoàn lượt theo `BUS-03`, hiện "chưa chấm được, thử lại sau". KHÔNG ghi dòng.
**A5 — Trình duyệt không hỗ trợ ghi âm** — ẩn nút "Luyện nói", hiện lý do.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `MIC_PERMISSION_DENIED` | — | Người học chặn quyền microphone | Hướng dẫn mở lại quyền. Không gọi API |
| `AUDIO_TOO_SHORT` | 422 | Bản ghi dưới 0,5 giây | Chặn ở client, không trừ lượt |
| `AUDIO_TOO_LONG` | 422 | Bản ghi dài hơn câu mẫu ×3 | Chặn — tránh gửi audio rác tốn phí |
| `AUDIO_FORMAT_UNSUPPORTED` | 415 | Định dạng dịch vụ ngoài không nhận | Client chuyển sang định dạng được hỗ trợ |
| `INSUFFICIENT_CREDITS` | 402 | Hết lượt free và hết điểm | Chuyển `/account/billing` (`FR-072`) |
| `ACCOUNT_UNVERIFIED` | 403 | Chưa xác thực email | Chuyển `/verify-email` (`FR-010`) |
| `PRONUNCIATION_SERVICE_ERROR` | 502 | Dịch vụ ngoài lỗi hoặc timeout | **Hoàn lượt** (`BUS-03`), không ghi dòng (A4) |
| `SCORE_OUT_OF_RANGE` | 500 | Dịch vụ ngoài trả điểm ngoài 0–100 | Hoàn lượt, ghi log. KHÔNG ghi điểm sai vào DB |

> 🔴 **`SCORE_OUT_OF_RANGE` là lý do bước 7 tồn tại.** Dịch vụ ngoài vẫn là nguồn không
> kiểm soát được. Nhận điểm rồi ghi thẳng vào DB là tin bên thứ ba vô điều kiện — mục
> *chấm bằng dịch vụ bên ngoài* của `BUS-09` yêu cầu server kiểm trước khi ghi.

## Business rule

| # | Rule |
| --- | --- |
| BR-122-1 | Audio PHẢI đi **client → server ta → dịch vụ ngoài**. Client KHÔNG được gọi trực tiếp dịch vụ ngoài (`BUS-09` mục chấm ngoài, điều kiện 1). |
| BR-122-2 | Client KHÔNG BAO GIỜ gửi điểm lên. Server nhận điểm từ dịch vụ ngoài, kiểm hợp lệ, rồi mới ghi (`BUS-09`, điều kiện 2). |
| BR-122-3 | `shadowing_attempts.provider` PHẢI ghi tên dịch vụ đã chấm. Đổi nhà cung cấp vẫn đọc lại được điểm cũ thuộc nhà nào (`BUS-09`, điều kiện 3). |
| BR-122-4 | **KHÔNG lưu file audio.** Chấm xong là bỏ. Giọng nói là dữ liệu sinh trắc — giữ lại tạo nghĩa vụ bảo vệ dữ liệu không cần thiết. |
| BR-122-5 | Dịch vụ ngoài lỗi → PHẢI hoàn lượt đã trừ (`BUS-03`). Người học không trả tiền cho lần thất bại. |
| BR-122-6 | Server PHẢI kiểm điểm nhận về nằm trong 0–100 và số âm tiết khớp `expected_text` trước khi ghi DB. |
| BR-122-7 | Nghe lại mẫu, nghe lại bản ghi của mình, ghi lại — KHÔNG trừ lượt. Chỉ trừ khi thật sự gửi đi chấm. |
| BR-122-8 | `expected_text` chụp lại như `BR-120-2`. |
| BR-122-9 | Màn kết quả PHẢI ghi rõ **chưa chấm riêng thanh điệu** — xem giới hạn đã biết ở dưới. |

## Giới hạn đã biết

> ⚠️ **Dịch vụ đánh giá phát âm không chấm thanh điệu riêng cho tiếng Trung.**
> Azure Pronunciation Assessment hỗ trợ `zh-CN` với điểm theo âm tiết, nhưng *Prosody*
> chỉ có ở `en-US` và không tài liệu hoá điểm thanh điệu. Hệ quả: Shadowing nói được
> *"âm tiết này chưa đúng"* nhưng **không chỉ ra "bạn đọc thanh 2 thành thanh 3"** — đúng
> cái người Việt sai nhiều nhất.
>
> **Cách bù:** UC-120 Dictation có `tone_score` riêng. Hai tính năng dùng cùng nhau thì
> người học biết cả "nghe ra chưa" và "đọc đúng chưa".
>
> **[CHỜ CHỐT]** Có tự thêm chấm thanh điệu ở server không (lấy pitch contour so với mẫu)
> — chủ dự án quyết **cuối dự án**.

> **[CHỜ CHỐT]** Hạn mức Shadowing: 1 lần = 1 lượt, hay hạn mức riêng cao hơn — chủ dự án
> quyết **cuối dự án**. Luyện nói cần lặp nhiều lần một câu, nên 10 lượt/tháng có thể quá ít.

## API · DB

```
POST /api/videos/{id}/shadowing
     body: multipart audio + subtitle_index
     200:  { accuracy_score, fluency_score, completeness, syllables[], credit_cost }
```

`videos` (đọc) · `shadowing_attempts` (ghi) · `credit_transactions` qua UC-096

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Đọc đúng câu ngắn | Điểm cao, mảng `syllables` đủ số âm tiết |
| T2 | Đọc sai một âm tiết | Âm tiết đó điểm thấp, các âm khác cao |
| T3 | Im lặng không đọc | Điểm `completeness` thấp |
| T4 | Bản ghi 0,2 giây | 422 `AUDIO_TOO_SHORT`, **không trừ lượt** |
| T5 | Dịch vụ ngoài timeout | 502, **lượt được hoàn**, `shadowing_attempts` không có dòng mới |
| T6 | Dịch vụ trả điểm 150 | 500 `SCORE_OUT_OF_RANGE`, hoàn lượt, không ghi DB |
| T7 | Hết lượt và hết điểm | 402, chuyển `/account/billing` |
| T8 | Chưa xác thực email | 403 `ACCOUNT_UNVERIFIED` |
| T9 | Chặn quyền microphone | Nút ẩn, không gọi API |
| T10 | Nghe lại mẫu 5 lần | Lượt KHÔNG bị trừ |
| T11 | Ghi lại 3 lần rồi gửi 1 lần | Trừ **đúng 1 lượt** |
| T12 | Kiểm DB sau khi chấm | KHÔNG có file audio nào được lưu |

---

# UC-123 · Xem phản hồi phát âm theo âm tiết

| | |
| --- | --- |
| **UC-ID** | UC-123 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |
| **Quan hệ** | `«extend»` **UC-122** — chỉ xem được sau khi đã chấm |

## Mô tả

Xem điểm phát âm chi tiết: điểm tổng, điểm trôi chảy, độ đầy đủ, và điểm từng âm tiết.

## Tiền điều kiện

1. Có ít nhất một dòng `shadowing_attempts` cho câu đó
2. Dòng thuộc chính `USER` này (`BUS-01`)

## Hậu điều kiện

Không đổi dữ liệu — màn chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Client | Hiện ba điểm: chính xác · trôi chảy · đầy đủ |
| 2 | Client | Hiện câu phụ đề, từng âm tiết tô màu theo điểm riêng |
| 3 | Client | Hiện ghi chú *"chưa chấm riêng thanh điệu"* (BR-122-9) |
| 4 | `USER` | Bấm một âm tiết điểm thấp → hiện pinyin và gợi ý cách đặt lưỡi |
| 5 | `USER` | Chọn "Đọc lại" → về UC-122, hoặc "Chép câu này" → UC-120 |

## Luồng thay thế

**A1 — So sánh với lượt trước** — hiện biểu đồ điểm qua các lượt của cùng câu.
**A2 — Nghe lại câu mẫu** — thuần client, không trừ lượt.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `ATTEMPT_NOT_FOUND` | 404 | ID lượt không tồn tại | Về trình phát |
| `ATTEMPT_NOT_OWNED` | 403 | Lượt của người khác (`BUS-01`) | 403, KHÔNG trả 404 |

## Business rule

| # | Rule |
| --- | --- |
| BR-123-1 | Chỉ xem được lượt của **chính mình** (`BUS-01`). |
| BR-123-2 | Màn PHẢI ghi rõ **chưa chấm riêng thanh điệu** (BR-122-9). Không ghi là để người học hiểu sai rằng điểm cao nghĩa là thanh điệu đúng. |
| BR-123-3 | KHÔNG phát lại bản ghi của người học — audio đã bị bỏ theo `BR-122-4`. Màn phải nói rõ điều này nếu người học tìm nút phát lại. |
| BR-123-4 | Màu theo điểm âm tiết PHẢI kèm số điểm dạng chữ. Không dùng màu làm tín hiệu duy nhất (`design.md` §10). |

## API · DB

```
GET /api/videos/{id}/shadowing/{attemptId}
GET /api/videos/{id}/shadowing/history?subtitle_index=
```

`shadowing_attempts` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Xem lượt vừa chấm | Ba điểm + mảng âm tiết |
| T2 | Bấm âm tiết điểm thấp | Hiện pinyin + gợi ý phát âm |
| T3 | Xem lượt của user khác | 403 `ATTEMPT_NOT_OWNED` |
| T4 | Tìm nút phát lại bản ghi | Không có — màn giải thích audio không lưu |
| T5 | Kiểm ghi chú thanh điệu | Hiện rõ trên màn |

---

# UC-124 · Lặp một câu phụ đề

| | |
| --- | --- |
| **UC-ID** | UC-124 · **Actor** `USER` · **Pri** P3 · **Scope** **V2** · **FT** 1.6 |
| **Quan hệ** | `«extend»` **UC-030** |

## Mô tả

Lặp lại một câu phụ đề liên tục để nghe kỹ, cho tới khi người học tắt.

Tách thành UC riêng vì YouTube iframe **không có** chức năng lặp đoạn — phải tự làm, và
có giới hạn kỹ thuật đáng ghi lại.

## Tiền điều kiện

1. Video đang phát
2. Câu được chọn dài **≥ 3 giây** (xem giới hạn dưới)

## Hậu điều kiện

Không đổi dữ liệu — thuần client. Trạng thái bật/tắt lặp lưu `localStorage`.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm biểu tượng lặp ở một câu phụ đề |
| 2 | Client | `seekTo(start_ms)`, bật cờ lặp cho câu đó |
| 3 | Client | Mỗi ~100ms đọc `getCurrentTime()` |
| 4 | Client | Nếu `currentTime ≥ end_ms` → `seekTo(start_ms)` |
| 5 | `USER` | Bấm lại biểu tượng để tắt |

## Luồng thay thế

**A1 — Đổi sang câu khác khi đang lặp** — tắt lặp câu cũ, bật cho câu mới.
**A2 — Lặp kèm giảm tốc** — hai chức năng độc lập, dùng cùng được.
**A3 — Người học tua tay ra ngoài đoạn lặp** — tắt lặp, tôn trọng hành động của người dùng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `SUBTITLE_TOO_SHORT_TO_LOOP` | — | Câu dưới 3 giây | Ẩn nút lặp cho câu đó, kèm lý do khi hover |
| `PLAYER_NOT_READY` | — | YouTube iframe chưa `onReady` | Nút lặp disable tới khi player sẵn sàng |

## Giới hạn đã biết

> ⚠️ **YouTube iframe không lặp được đoạn.** Tham số `loop=1` chỉ hoạt động cùng
> `playlist=` và không áp dụng cho lặp một khoảng trong video. Phải tự làm bằng cách đọc
> `getCurrentTime()` theo chu kỳ rồi `seekTo()`.
>
> Độ chính xác `getCurrentTime()` của YouTube khoảng **±250ms**. Với câu 2 giây, sai số
> này chiếm hơn 12% độ dài câu — nghe thấy rõ là lặp bị cắt đầu hoặc đuôi. **Vì vậy chỉ
> bật lặp cho câu ≥ 3 giây**, và `SUBTITLE_TOO_SHORT_TO_LOOP` ẩn nút cho câu ngắn hơn.

## Business rule

| # | Rule |
| --- | --- |
| BR-124-1 | Lặp là chức năng **thuần client**. KHÔNG gọi API, KHÔNG trừ lượt. |
| BR-124-2 | Chỉ mở lặp cho câu dài **≥ 3 giây** — dưới mức đó sai số YouTube làm trải nghiệm tệ hơn là không có. |
| BR-124-3 | Người học tua tay ra ngoài đoạn lặp → PHẢI tắt lặp. Kéo họ về là chống lại hành động họ vừa làm. |
| BR-124-4 | Vòng lặp kiểm tra PHẢI dừng khi rời màn hoặc video tạm dừng. Để chạy là rò rỉ bộ hẹn giờ. |

## API · DB

Không có — thuần client.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Lặp câu 5 giây | Lặp đúng khoảng, không lệch rõ |
| T2 | Câu 2 giây | Nút lặp bị ẩn |
| T3 | Đang lặp, bấm câu khác | Chuyển lặp sang câu mới |
| T4 | Đang lặp, tua tay ra ngoài | Lặp tự tắt |
| T5 | Đang lặp, rời màn | Bộ hẹn giờ dừng, không rò rỉ |
| T6 | Lặp + giảm tốc 0,5× | Cả hai chạy cùng lúc |

---

# UC-126 · Luyện viết chữ Hán

| | |
| --- | --- |
| **ID** | UC-126 |
| **Actor chính** | `USER` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 1.1 |
| **Loại** | Use case tổng quát |

## Mô tả

Người học luyện viết chữ Hán theo nhiều chế độ khác nhau, từ có gợi ý nét tới viết lại hoàn toàn từ trí nhớ. Mỗi chế độ phù hợp với một mức độ thành thạo, nên người học chọn chế độ theo khả năng hiện tại của mình.

## Quan hệ use case

| Quan hệ | Use case | Điều kiện áp dụng |
| --- | --- | --- |
| `«extend»` | UC-015 Luyện viết theo nét | Người học mới làm quen với chữ, cần gợi ý |
| `«extend»` | UC-016 Luyện viết chế độ nhớ rồi viết | Người học đã xem mẫu và muốn tự kiểm tra trí nhớ |
| `«extend»` | UC-017 Luyện viết chế độ thử thách | Người học muốn luyện có giới hạn thời gian |
| `«extend»` | UC-018 Luyện viết chế độ nghe chép | Người học muốn kết hợp nghe với viết |

## Tiền điều kiện

- `USER` đã đăng nhập
- Chữ cần luyện có dữ liệu thứ tự nét trong kho dữ liệu
- Người học còn lượt dùng nếu chế độ đó có giới hạn

## Hậu điều kiện

- Kết quả lượt luyện được ghi nhận
- Mức độ nắm vững của chữ vừa luyện được cập nhật theo trọng số của chế độ
- Lịch ôn của chữ đó được tính lại

## Luồng chính

1. `USER` mở phần luyện viết
2. Chọn chữ muốn luyện hoặc để hệ thống chọn theo lịch ôn
3. Chọn chế độ luyện phù hợp với mình
4. Hệ thống hiển thị khung viết theo chế độ đã chọn
5. `USER` viết chữ trên khung
6. Hệ thống đối chiếu nét vừa viết với thứ tự nét chuẩn
7. Hệ thống cho biết kết quả và chỉ ra nét nào chưa đúng
8. Kết quả được dùng để cập nhật mức độ nắm vững và lịch ôn
9. `USER` chuyển sang chữ tiếp theo hoặc kết thúc lượt luyện

## Luồng thay thế

**A1 · Luyện có gợi ý nét**
Khung viết hiện sẵn nét mẫu để người học tô theo. Chi tiết ở UC-015.

**A2 · Luyện bằng trí nhớ**
Chữ mẫu hiện trong vài giây rồi biến mất, người học viết lại. Chi tiết ở UC-016.

**A3 · Luyện có giới hạn thời gian**
Người học viết một dãy chữ liên tiếp trước khi hết giờ. Chi tiết ở UC-017.

**A4 · Luyện kết hợp nghe**
Người học nghe cách đọc rồi viết chữ tương ứng, không nhìn mẫu. Chi tiết ở UC-018.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
| --- | --- | --- | --- |
| `CHARACTER_NOT_FOUND` | Chữ không có trong kho dữ liệu | **404** | Về danh sách chữ |
| `STROKE_DATA_MISSING` | Chữ chưa có dữ liệu thứ tự nét | **422** | Không mở luyện viết cho chữ này |
| `INSUFFICIENT_CREDITS` | Hết lượt dùng và hết điểm | **402** | Đưa tới màn gói và điểm |

## Business rule

| # | Rule |
| --- | --- |
| BR-126-1 | Mỗi chế độ luyện có trọng số riêng khi cập nhật mức độ nắm vững, chế độ khó hơn thì trọng số cao hơn. |
| BR-126-2 | Chữ chưa có dữ liệu thứ tự nét không được mở cho luyện viết. |
| BR-126-3 | Kết quả luyện viết được chấm ở phía người dùng, nên trọng số của nó thấp hơn các hoạt động chấm ở máy chủ. |
| BR-126-4 | Thời gian hoàn thành một lượt viết phải nằm trong khoảng hợp lý, quá nhanh thì không tính kết quả. |

## API · DB

```
GET  /api/learning/characters/{id}/strokes
POST /api/learning/writing-attempts
```

`lexemes` (đọc) · `user_progress` (ghi) · `feature_usage` (ghi khi chế độ có tính lượt)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Viết đúng thứ tự nét | Kết quả đạt, mức độ nắm vững tăng |
| T2 | Viết sai thứ tự nét | Chỉ ra nét sai, mức độ nắm vững giảm |
| T3 | Chữ chưa có dữ liệu nét | 422, không mở luyện |
| T4 | Hoàn thành trong thời gian bất thường | Không tính kết quả |
| T5 | Cùng một chữ ở hai chế độ khác nhau | Trọng số cập nhật khác nhau |

---

---

# UC-127 · Nhận diện chữ Hán

| | |
| --- | --- |
| **ID** | UC-127 |
| **Actor chính** | `USER` |
| **Priority** | P1 |
| **Scope** | MVP |
| **Tính năng gốc** | 1.2 |
| **Loại** | Use case tổng quát |

## Mô tả

Người học làm bài tập trắc nghiệm để nhận biết chữ Hán, có thể là nhìn chữ chọn nghĩa hoặc nghe âm chọn chữ. Hai hướng này bổ sung cho nhau, giúp người học nối được mặt chữ với cả nghĩa và cách đọc.

## Quan hệ use case

| Quan hệ | Use case | Điều kiện áp dụng |
| --- | --- | --- |
| `«extend»` | UC-019 Nhìn chữ chọn nghĩa | Người học luyện nhận biết mặt chữ |
| `«extend»` | UC-020 Nghe âm chọn chữ | Người học luyện nối âm đọc với mặt chữ |

## Tiền điều kiện

- `USER` đã đăng nhập
- Có đủ chữ trong kho để tạo câu hỏi và các đáp án nhiễu
- Câu hỏi đã được gắn điểm kiến thức

## Hậu điều kiện

- Kết quả trả lời được ghi nhận
- Mức độ nắm vững của điểm kiến thức liên quan được cập nhật
- Lịch ôn được tính lại theo kết quả vừa nhận

## Luồng chính

1. `USER` mở phần luyện nhận diện chữ
2. Hệ thống chọn chữ cần luyện theo lịch ôn hoặc theo chủ đề đang học
3. Hệ thống tạo câu hỏi kèm các đáp án nhiễu
4. `USER` chọn đáp án
5. Hệ thống chấm và cho biết đúng hay sai
6. Nếu sai, hệ thống hiện đáp án đúng kèm giải thích
7. Kết quả được dùng để cập nhật mức độ nắm vững
8. `USER` chuyển sang câu tiếp theo hoặc kết thúc

## Luồng thay thế

**A1 · Nhìn chữ chọn nghĩa**
Màn hình đưa ra một chữ Hán và bốn nghĩa tiếng Việt. Chi tiết ở UC-019.

**A2 · Nghe âm chọn chữ**
Người học nghe một âm đọc rồi chọn chữ tương ứng. Chi tiết ở UC-020.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
| --- | --- | --- | --- |
| `NOT_ENOUGH_DISTRACTORS` | Không đủ chữ để tạo đáp án nhiễu | **422** | Bỏ qua chữ này, chọn chữ khác |
| `MALFORMED_QUESTION` | Câu hỏi có nhiều hơn một đáp án đúng | **500** | Loại câu hỏi, ghi log để kiểm duyệt |
| `AUDIO_MISSING` | Chữ chưa có audio khi luyện nghe | **422** | Không dùng chữ này cho chế độ nghe |

> Đáp án nhiễu phải khác nhau cả về nghĩa và cách đọc. Nếu hai lựa chọn trùng
> nghĩa hoặc trùng pinyin thì câu hỏi không còn một đáp án đúng duy nhất.

## Business rule

| # | Rule |
| --- | --- |
| BR-127-1 | Mỗi câu hỏi phải có đúng một đáp án đúng. |
| BR-127-2 | Đáp án nhiễu không được trùng nghĩa hoặc trùng cách đọc với đáp án đúng. |
| BR-127-3 | Câu hỏi phải được gắn ít nhất một điểm kiến thức để kết quả cập nhật được mức độ nắm vững. |
| BR-127-4 | Chữ chưa có audio không được dùng cho chế độ nghe âm chọn chữ. |

## API · DB

```
GET  /api/learning/recognition/questions
POST /api/learning/recognition/answers
```

`lexemes` · `questions` (đọc) · `user_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chọn đáp án đúng | Báo đúng, mức độ nắm vững tăng |
| T2 | Chọn đáp án sai | Hiện đáp án đúng kèm giải thích |
| T3 | Kho chữ quá ít để tạo nhiễu | 422, chọn chữ khác |
| T4 | Hai đáp án cùng nghĩa | Câu hỏi bị loại khi tạo |
| T5 | Chữ không có audio, chế độ nghe | Không dùng chữ này |

---

---

# UC-128 · Học một chủ đề từ vựng

| | |
| --- | --- |
| **ID** | UC-128 |
| **Actor chính** | `USER` |
| **Priority** | P0 |
| **Scope** | MVP |
| **Tính năng gốc** | 1.4 |
| **Loại** | Use case tổng quát |

## Mô tả

Người học đi qua một chủ đề từ vựng theo trình tự từ học từ mới tới làm bài kiểm tra cuối chủ đề. Khi đạt mức hoàn thành yêu cầu, chủ đề tiếp theo trong lộ trình sẽ được mở.

## Quan hệ use case

| Quan hệ | Use case | Điều kiện áp dụng |
| --- | --- | --- |
| `«extend»` | UC-026 Học từ mới trong chủ đề | Bước đầu của chủ đề |
| `«extend»` | UC-027 Luyện nhận diện từ trong chủ đề | Sau khi đã xem từ mới |
| `«extend»` | UC-028 Luyện nghe từ trong chủ đề | Sau khi đã xem từ mới |
| `«extend»` | UC-029 Làm bài kiểm tra cuối chủ đề | Người học đã luyện đủ các bước trước |

## Tiền điều kiện

- `USER` đã đăng nhập
- Chủ đề đang ở trạng thái đã mở
- Chủ đề có đủ từ vựng và câu hỏi để học

## Hậu điều kiện

- Tiến độ của chủ đề được cập nhật theo các bước đã hoàn thành
- Nếu đạt mức hoàn thành yêu cầu, chủ đề kế tiếp được mở
- Các từ trong chủ đề được đưa vào lịch ôn

## Luồng chính

1. `USER` chọn một chủ đề đã mở
2. Hệ thống hiển thị danh sách từ và tiến độ hiện tại của chủ đề
3. `USER` xem lần lượt các từ mới
4. `USER` làm bài luyện nhận diện và luyện nghe cho các từ vừa xem
5. Hệ thống cập nhật tiến độ sau mỗi bước
6. Khi đã luyện đủ, `USER` làm bài kiểm tra cuối chủ đề
7. Hệ thống chấm bài và tính mức hoàn thành của chủ đề
8. Nếu đạt ngưỡng, hệ thống mở chủ đề kế tiếp
9. `USER` xem kết quả và chọn học tiếp hoặc chuyển chủ đề

## Luồng thay thế

**A1 · Người học tự khai đã biết từ**
Hệ thống vẫn đưa từ đó vào bài kiểm tra cuối chủ đề để xác nhận. Chi tiết ở UC-026.

**A2 · Chưa đạt ngưỡng hoàn thành**
Chủ đề kế tiếp chưa mở, hệ thống chỉ ra những từ còn yếu để luyện thêm.

**A3 · Làm lại bài kiểm tra**
Lấy điểm cao nhất trong các lần làm để tính mức hoàn thành.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
| --- | --- | --- | --- |
| `TOPIC_LOCKED` | Chủ đề chưa được mở | **403** | Hiện điều kiện cần để mở |
| `TOPIC_EMPTY` | Chủ đề chưa có từ vựng | **422** | Không mở chủ đề cho người học |
| `UNLOCK_FAILED` | Đạt ngưỡng nhưng chủ đề kế tiếp không mở | **500** | Ghi log, cho phép mở lại thủ công |
| `PROGRESS_PERCENT_MISMATCH` | Tiến độ lưu sẵn lệch với dữ liệu thật | **500** | Tính lại từ dữ liệu gốc |

> 🔴 **`UNLOCK_FAILED` là lỗi nghiêm trọng nhất của chủ đề.** Người học đạt
> ngưỡng mà chủ đề kế tiếp không mở thì họ mắc kẹt, và làm lại bài kiểm tra cũng
> không cứu được vì hệ thống lấy điểm cao nhất.

## Business rule

| # | Rule |
| --- | --- |
| BR-128-1 | Chủ đề chỉ mở khi các chủ đề tiên quyết đã đạt mức hoàn thành yêu cầu. |
| BR-128-2 | Mức hoàn thành của chủ đề tính từ dữ liệu học thật, không dựa vào số liệu lưu sẵn. |
| BR-128-3 | Việc cập nhật tiến độ và việc mở chủ đề kế tiếp phải nằm trong cùng một giao dịch. |
| BR-128-4 | Chủ đề đã mở thì không bị khóa lại, kể cả khi mức độ nắm vững sau đó giảm. |
| BR-128-5 | Người học tự khai đã biết một từ thì vẫn phải làm bài kiểm tra cuối chủ đề cho từ đó. |

## API · DB

```
GET  /api/learning/topics/{id}
POST /api/learning/topics/{id}/complete
```

`topics` · `topic_items` (đọc) · `user_progress` (đọc, ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Hoàn thành đủ các bước, đạt ngưỡng | Chủ đề kế tiếp được mở |
| T2 | Đạt ngưỡng nhưng mở khóa lỗi | 500, ghi log, không để người học mắc kẹt |
| T3 | Mở chủ đề chưa đủ điều kiện | 403, hiện điều kiện cần |
| T4 | Làm lại bài kiểm tra điểm thấp hơn | Giữ điểm cao nhất |
| T5 | Mức độ nắm vững giảm sau khi mở | Chủ đề vẫn mở |

---

---

# UC-129 · Học qua video

| | |
| --- | --- |
| **ID** | UC-129 |
| **Actor chính** | `USER` |
| **Priority** | P2 |
| **Scope** | **V2** |
| **Tính năng gốc** | 1.6 |
| **Loại** | Use case tổng quát |

## Mô tả

Người học chọn một video tiếng Trung có phụ đề và học theo nhiều cách khác nhau trên cùng video đó. Họ có thể xem kèm phụ đề, tra từ ngay trong phụ đề, nghe rồi chép lại, hoặc đọc theo để luyện phát âm.

## Quan hệ use case

| Quan hệ | Use case | Điều kiện áp dụng |
| --- | --- | --- |
| `«extend»` | UC-030 Xem video có phụ đề tương tác | Người học xem và nghe hiểu |
| `«extend»` | UC-031 Bấm từ trong phụ đề xem nghĩa | Người học gặp từ chưa biết |
| `«extend»` | UC-032 Lưu từ từ phụ đề vào sổ tay | Người học muốn ôn lại từ sau |
| `«extend»` | UC-120 Luyện Dictation từ video | Người học muốn kiểm tra khả năng nghe |
| `«extend»` | UC-122 Luyện Shadowing từ video | Người học muốn luyện phát âm |
| `«extend»` | UC-124 Lặp một câu phụ đề | Người học cần nghe lại một câu nhiều lần |

## Tiền điều kiện

- `USER` đã đăng nhập
- Video đã được công bố và còn truy cập được ở nguồn
- Video có phụ đề đã gắn mốc thời gian

## Hậu điều kiện

- Vị trí xem gần nhất được lưu để lần sau tiếp tục
- Kết quả của các bài tập trên video được ghi nhận riêng theo từng loại
- Từ được lưu từ phụ đề nằm trong sổ tay hoặc bộ thẻ của người học

## Luồng chính

1. `USER` mở danh sách video và chọn theo cấp hoặc chủ đề
2. Hệ thống tải video cùng phụ đề đã gắn mốc thời gian
3. `USER` xem video, phụ đề chạy đồng bộ theo lời thoại
4. `USER` chọn cách học muốn dùng cho câu đang nghe
5. Hệ thống mở bài tập tương ứng với cách học đã chọn
6. `USER` làm bài và nhận kết quả
7. Hệ thống lưu kết quả và vị trí xem hiện tại
8. `USER` tiếp tục với câu khác hoặc kết thúc buổi học

## Luồng thay thế

**A1 · Chỉ xem và nghe hiểu**
Người học xem hết video với phụ đề, không làm bài tập nào. Chi tiết ở UC-030.

**A2 · Tra từ trong lúc xem**
Bấm vào một từ trên phụ đề để xem nghĩa, video tạm dừng. Chi tiết ở UC-031.

**A3 · Nghe rồi chép lại**
Người học gõ lại câu vừa nghe, hệ thống so với phụ đề gốc. Chi tiết ở UC-120.

**A4 · Đọc theo để luyện phát âm**
Người học ghi âm giọng mình đọc theo câu mẫu. Chi tiết ở UC-122.

**A5 · Video không còn ở nguồn**
Hệ thống đánh dấu video không khả dụng và báo cho người quản trị nội dung.

## Exception

| Mã | Tình huống | HTTP | Xử lý |
| --- | --- | --- | --- |
| `VIDEO_NOT_FOUND` | Video không tồn tại | **404** | Về danh sách video |
| `VIDEO_UNAVAILABLE` | Nguồn đã xóa video | **410** | Ẩn khỏi danh sách, báo người quản trị nội dung |
| `NO_SUBTITLES` | Video chưa có phụ đề | **422** | Không mở video, vì không phụ đề thì mất giá trị học |
| `SUBTITLE_TIMING_INVALID` | Mốc thời gian phụ đề sai | **500** | Phụ đề không khớp lời thoại, kiểm lại khi nhập |

## Business rule

| # | Rule |
| --- | --- |
| BR-129-1 | Video không có phụ đề thì không mở cho người học, vì toàn bộ cách học trên video đều dựa vào phụ đề. |
| BR-129-2 | Hệ thống không lưu video của bên khác về máy chủ, chỉ nhúng từ nguồn gốc. |
| BR-129-3 | Vị trí xem được lưu riêng cho từng người học và từng video. |
| BR-129-4 | Kết quả của mỗi cách học trên video được lưu riêng, không gộp thành một điểm chung. |

## API · DB

```
GET  /api/videos
GET  /api/videos/{id}/subtitles
POST /api/videos/{id}/progress
```

`videos` (đọc) · `user_progress` với loại mục tiêu là video (ghi) · `dictation_attempts` · `shadowing_attempts` (ghi qua các use case mở rộng)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Mở video có phụ đề | Phụ đề chạy đồng bộ với lời thoại |
| T2 | Video chưa có phụ đề | 422, không mở |
| T3 | Xem 5 phút rồi thoát, mở lại | Tiếp tục từ vị trí đã lưu |
| T4 | Nguồn đã xóa video | 410, video bị ẩn khỏi danh sách |
| T5 | Làm cả chép chính tả và luyện nói trên cùng một câu | Hai kết quả lưu riêng |

---

---

# Tổng hợp exception nhóm 1

## Mười exception quan trọng nhất

| # | UC | Exception | Vì sao |
| --- | --- | --- | --- |
| 1 | UC-029 | `UNLOCK_FAILED` | Đạt 90% mà không mở khoá → người học **kẹt vĩnh viễn** (A4 lấy điểm cao nhất nên thi lại không cứu được) |
| 2 | UC-025 · UC-026 | `PROGRESS_PERCENT_MISMATCH` | % tính sẵn lệch dữ liệu thật → mở khoá sai hoặc không mở |
| 3 | UC-029 | `ATTEMPT_NOT_OWNED` | IDOR — ghi điểm vào bài của người khác |
| 4 | UC-032 | `DECK_NOT_OWNED` | IDOR — ghi vào flashcard người khác |
| 5 | UC-031 | `AMBIGUOUS_SEGMENTATION` | Tách từ sai → **dạy nghĩa sai**, tệ hơn không dạy |
| 6 | UC-022 | `UNIQUE_VIOLATION` | Race condition hai tab → hai dòng tiến độ tầng, sai mọi thống kê sau |
| 7 | UC-024 | `CLIENT_SENT_RATING_DIRECTLY` | Gửi `Easy` mãi để không bao giờ phải ôn lại |
| 8 | UC-028 | `HOMOPHONE_AMBIGUITY` | Bài nghe có hai đáp án đồng âm → **không có đáp án đúng** |
| 9 | UC-026 | `WORD_NOT_IN_TOPIC` | Học nội dung chủ đề khoá qua endpoint chủ đề mở |
| 10 | UC-023 | `NO_KNOWLEDGE_POINT_LINKED` | Học xong mà lộ trình 3.2 không thấy → mất "yếu chỗ nào luyện chỗ đó" |

## Ba nhóm exception lặp lại khắp nhóm 1

| Nhóm | Xuất hiện ở | Bài học |
| --- | --- | --- |
| **Chấm ở client không đáng tin** | UC-015 → UC-018 (luyện viết) | `hanzi-writer` chấm ở client, `stroke_data` nằm trong response. **Không thể** chấm lại ở server. Giảm rủi ro bằng hệ số mastery thấp + kiểm thời gian hợp lý, và để UC-029 (chấm server) làm thước đo thật |
| **Nhiễu trùng đáp án** | UC-019 · UC-020 · UC-027 · UC-028 | Trùng `word_id` · trùng **nghĩa** · trùng **pinyin có dấu**. Cả ba đều làm câu hỏi vô nghĩa. Phải kiểm khi **sinh** câu, không phải khi chấm |
| **Ghi tiến độ phải nhất quán** | UC-022 · UC-026 · UC-027 · UC-029 | Các dòng `user_progress` theo `target_type`, hoặc điểm + mở khoá, phải được cập nhật cùng transaction; không để trạng thái chủ đề lệch tiến độ kiến thức |

---

# Khoảng trống thiết kế phát hiện ở nhóm 1

| # | Thiếu | UC bị ảnh hưởng | Mức |
| --- | --- | --- | --- |
| 1 | Tiến độ video đã có chỗ lưu: `user_progress` với `target_type = 'VIDEO'` và `position_ms` | UC-030 | Đã giải quyết trong `database.md` |
| 2 | Lượt thử thách viết dùng `attempts.kind = 'WRITING_CHALLENGE'`; cần kiểm `served_at` và trạng thái nộp khi triển khai | UC-017 | Thiết kế DB đã có hướng lưu |
| 3 | Tầng phát âm dùng `pron_stages` trong thiết kế DB hiện tại | UC-021 · UC-022 | Đã giải quyết trong `database.md` |
| 4 | Cờ `self_declared` đã có trong `user_progress` | UC-026 · UC-029 | Đã giải quyết trong `database.md` |
| 5 | Chưa có bảng/cột lưu **hệ số mastery theo chế độ luyện** (0.5 / 1.0 / 1.2) | UC-015 → UC-018 | ⚠️ Hiện là con số trong code |
| 6 | Chưa có `OwnershipService` dùng chung chống IDOR | UC-029 · UC-032 | 🔴 Mục B trong quyết định v2 vẫn chưa chốt |
| 7 | Nguồn video đã chốt là YouTube cho phép nhúng; cần kiểm quyền nhúng và trạng thái nguồn khi nhập video | UC-030 | Đã chốt phạm vi nguồn |
| 12 | Chưa có bảng `dictation_attempts` và `shadowing_attempts` | UC-120 → UC-123 | 🔴 Chặn Dictation và Shadowing — chờ duyệt RFC `database.md` §18 |
| 13 | Chưa chốt nhà cung cấp đánh giá phát âm và hạn mức Shadowing | UC-122 | ⚠️ Chủ dự án quyết cuối dự án |
| 8 | Chưa có unique constraint `(user_id, stage_number)` trên `user_pronunciation_progress` | UC-022 | 🔴 Race condition |
| 9 | Chưa có partial unique index đảm bảo **đúng 1** `is_correct` mỗi câu hỏi | UC-019 · UC-020 | 🔴 Chấm sai oan người học |
| 10 | Cần phép kiểm đối chiếu tiến độ chủ đề với các điểm kiến thức liên quan trong `user_progress` | UC-025 | ⚠️ Phát hiện lệch sớm |
| 11 | Chưa chốt từ phụ đề có tính vào **quota tra từ** (4.1) hay không | UC-031 | ⚠️ Chờ `TODO(PAYMENT_SCOPE)` |

> Bảng này giữ cả những khoảng trống đã được giải quyết trong `database.md` để tránh
> lặp lại cảnh báo cũ khi triển khai. Các mục còn ghi ⚠️/🔴 vẫn cần kiểm tra riêng.

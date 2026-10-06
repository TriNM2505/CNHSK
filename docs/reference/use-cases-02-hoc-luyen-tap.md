# CNHSK — Đặc tả Use Case · Nhóm 1 · Học & luyện tập

> **UC-015 → UC-032** · 18 use case · Tính năng 1.1 → 1.6
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
> **Role:** theo Hiến pháp §Tám actor — 6 role trong DB + `GUEST` + `SYSTEM`

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
|---|---|---|---|---|---|
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

---

# UC-015 · Luyện viết chữ Hán theo nét

| | |
|---|---|
| **UC-ID** | UC-015 |
| **Actor chính** | `USER` |
| **Actor phụ** | — |
| **Priority** | P1 · **Scope** MVP · **FT** 1.1 |
| **Client** | Web · Mobile (shared component) |

## Mô tả

Người học nhìn chữ mẫu, tô theo thứ tự nét chuẩn do `hanzi-writer` vẽ hướng dẫn. Đây là chế
độ dễ nhất trong 5 chế độ — có gợi ý nét, không giới hạn thời gian.

## Tiền điều kiện

1. `USER` đã đăng nhập, access token còn hiệu lực
2. Chữ cần luyện tồn tại trong `characters` và **có dữ liệu nét** (`stroke_data` JSONB)
3. Thư viện `hanzi-writer` 3.7.3 đã load xong trên client

## Hậu điều kiện

| Kết quả | Trạng thái hệ thống |
|---|---|
| Thành công | `user_knowledge_state` của điểm kiến thức tương ứng được cập nhật mastery; `study_sessions` ghi thêm một dòng |
| Thất bại | Không ghi gì — không được ghi mastery một phần |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-015-1 | Chấm nét ở client — server **không** nhận toạ độ, chỉ nhận kết quả tổng hợp |
| BR-015-2 | Chế độ này có hệ số mastery **0.5** (dễ nhất trong 5 chế độ) |
| BR-015-3 | Dùng animation gợi ý → hệ số nhân thêm 0.5 (tổng 0.25) |
| BR-015-4 | Luyện lại cùng một chữ trong 10 phút → chỉ lần đầu tính mastery |
| BR-015-5 | Không cập nhật mastery nếu `completed: false` |

## API

```
GET  /api/characters/{id}
POST /api/practice/writing
```

## Bảng DB liên quan

| Bảng | Vai trò |
|---|---|
| `characters` | Đọc — chữ, pinyin, `stroke_data` |
| `knowledge_points` | Đọc — tìm điểm kiến thức của chữ |
| `user_knowledge_state` | **Ghi** — mastery, `next_review_at` |
| `study_sessions` | **Ghi** — một dòng mỗi lượt luyện |

## Test case

| # | Đầu vào | Kết quả mong đợi |
|---|---|---|
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

Hệ thống cho xem chữ mẫu trong N giây, **ẩn chữ**, người học viết lại từ nhớ. Không có gợi
ý nét. Khó hơn UC-015 nên hệ số mastery cao hơn.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chữ có `stroke_data`
3. **Đã luyện chữ này ở chế độ theo nét ít nhất 1 lần** — không cho nhảy thẳng vào chế độ khó

## Hậu điều kiện

Thành công → mastery tăng với hệ số **1.0**; `study_sessions` ghi `mode = "RECALL"`.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-016-1 | Phải hoàn thành UC-015 cho chữ đó ≥ 1 lần mới mở |
| BR-016-2 | Hệ số mastery 1.0 |
| BR-016-3 | Xem lại mẫu tối đa 2 lần, mỗi lần −0.25 hệ số |
| BR-016-4 | `preview_seconds` cấu hình được, mặc định 5 |

## API · DB

```
POST /api/practice/writing   (mode = RECALL)
```
`characters` (đọc) · `user_knowledge_state` (đọc để kiểm điều kiện + ghi) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Viết liên tiếp một dãy chữ trong thời gian giới hạn. Sai quá N lần thì kết thúc. Là chế độ
có tính game nhất của tính năng 1.1.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có ít nhất 10 chữ đã ở trạng thái "đang học" hoặc "đã thuộc"

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Hoàn thành | Mastery cập nhật cho **từng chữ** trong dãy, hệ số 1.2; ghi `study_sessions` |
| Thua giữa dãy | Chỉ cập nhật mastery cho các chữ **đã viết xong**, chữ đang viết dở không tính |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-017-1 | `time_limit_seconds` = 20 × số chữ; `max_mistakes` = 3 |
| BR-017-2 | Hệ số mastery 1.2 — cao nhất trong các chế độ viết |
| BR-017-3 | Dãy chữ do **server** chọn, client không được tự chọn |
| BR-017-4 | Ưu tiên chữ có `next_review_at` gần nhất |
| BR-017-5 | Một `challenge_id` chỉ nộp được **một lần** |

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
|---|---|---|
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

Hệ thống phát audio đọc chữ, người học **chỉ nghe** (không thấy chữ) rồi viết lại. Kết hợp
kỹ năng nghe và viết.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chữ có **cả** `stroke_data` **và** `audio_url`
3. Thiết bị có loa/tai nghe hoạt động

## Hậu điều kiện

Mastery cập nhật hệ số **1.2** cho **hai** loại kỹ năng: viết và nghe.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-018-1 | Chỉ mở khi chữ có cả `audio_url` và `stroke_data` |
| BR-018-2 | Hệ số mastery 1.2, tính cho **cả** kỹ năng nghe và viết |
| BR-018-3 | Phát lại tối đa 3 lần, từ lần 2 mỗi lần −0.2 |
| BR-018-4 | `duration_ms` phải ≥ độ dài audio |

## API · DB

```
POST /api/practice/writing   (mode = DICTATION)
```
`characters` (đọc `audio_url`, `stroke_data`) · `user_knowledge_state` (ghi) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Trắc nghiệm 4 đáp án: hiện chữ Hán, người học chọn nghĩa tiếng Việt đúng. Đáp án nhiễu phải
là chữ gần giống để bài có giá trị phân biệt.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Kho `questions` có câu loại `CHAR_TO_MEANING` cho mức HSK của người học
3. Mỗi câu có đúng 4 dòng trong `question_options`, **đúng 1** dòng `is_correct = true`

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Trả lời đúng | Mastery tăng; `attempt_answers` ghi `is_correct = true` |
| Trả lời sai | Mastery **giảm**; ghi đáp án đã chọn để phân tích lỗi sai (dùng cho UC-040) |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-019-1 | Chấm **bắt buộc ở server** — response bước 3 không chứa `is_correct` |
| BR-019-2 | Đáp án nhiễu phải khác đáp án đúng và là chữ gần giống (đồng âm hoặc đồng bộ thủ) |
| BR-019-3 | Thứ tự đáp án trộn mỗi lần gọi |
| BR-019-4 | Trả lời sai → mastery giảm, không giữ nguyên |
| BR-019-5 | `duration_ms < 800` → hệ số ×0.5 |
| BR-019-6 | Một câu trong một lượt chỉ trả lời một lần |

## API · DB

```
GET  /api/practice/recognition
POST /api/practice/recognition/answer
```
`questions` · `question_options` · `question_knowledge_points` (đọc) · `attempt_answers` · `user_knowledge_state` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Phát audio, người học chọn chữ Hán đúng trong 4 lựa chọn. Rèn liên kết âm ↔ chữ.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có câu loại `AUDIO_TO_CHAR` với `audio_url` hợp lệ
3. 4 đáp án là **chữ**, không phải nghĩa

## Hậu điều kiện

Mastery kỹ năng **nghe** cập nhật; `attempt_answers` ghi lượt trả lời.

## Luồng chính

Giống UC-019, khác ở bước 5: client phát audio thay vì hiện chữ; 4 đáp án là chữ Hán.

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-020-1 | Chấm ở server |
| BR-020-2 | 4 đáp án phải là chữ có **pinyin khác nhau** — nếu trùng pinyin thì câu vô nghĩa |
| BR-020-3 | Audio lỗi không tính là trả lời sai |
| BR-020-4 | Mastery ghi vào kỹ năng `LISTENING` |

## API · DB

```
GET  /api/practice/recognition?type=AUDIO_TO_CHAR
POST /api/practice/recognition/answer
```
`questions` · `question_options` · `characters` (đọc) · `attempt_answers` · `user_knowledge_state` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Luyện 8 tầng: 23 thanh mẫu · 39 vận mẫu · 4 thanh điệu · biến điệu. Mỗi tầng là tập bài
nghe-và-chọn. **Không chấm phát âm qua micro** — giới hạn đã chốt.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Tầng đang mở (tầng 1 mở sẵn; tầng N cần hoàn thành tầng N−1 — xem UC-022)
3. `pronunciation_units` có dữ liệu cho tầng đó

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Làm xong tầng | `user_pronunciation_progress` cập nhật `accuracy`, `completed_at` |
| Đạt ngưỡng | Kích hoạt UC-022 mở tầng tiếp |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-021-1 | Tầng 1 mở sẵn cho mọi `USER` mới |
| BR-021-2 | Ngưỡng qua tầng: `accuracy ≥ 80%` |
| BR-021-3 | Đã mở thì **không bao giờ đóng lại** |
| BR-021-4 | Làm lại chỉ ghi điểm khi cao hơn |
| BR-021-5 | Unit thiếu audio bị loại khỏi mẫu số khi tính `accuracy` |
| BR-021-6 | Không chấm micro — ngoài scope |

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
|---|---|---|
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

Use case **tự động**, không có người bấm. Khi `accuracy` một tầng đạt ngưỡng, hệ thống mở
tầng kế tiếp.

## Tiền điều kiện

1. UC-021 vừa hoàn thành một tầng
2. `accuracy ≥ 80%`
3. Tầng N+1 tồn tại (N < 8)

## Hậu điều kiện

`user_pronunciation_progress` có dòng cho tầng N+1 với `unlocked_at` — cùng transaction với
lệnh ghi kết quả tầng N.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
| `NEXT_STAGE_NOT_FOUND` | — | Đã ở tầng 8 | Không phải lỗi (A3) |
| `STAGE_ALREADY_UNLOCKED` | — | Chạy lại | **Idempotent** — bỏ qua im lặng |
| `UNIQUE_VIOLATION` | 500 | Hai request song song cùng mở | Cần unique constraint `(user_id, stage)`; bắt lỗi và coi như đã mở |
| `TRANSACTION_ROLLBACK` | 500 | Lỗi ghi | **Toàn bộ kết quả tầng N cũng rollback** — không được mở tầng mà mất kết quả, hoặc ngược lại |

> 🔴 **`UNIQUE_VIOLATION` là race condition thật, không phải giả thuyết.** Người học mở hai
> tab, cùng nộp unit cuối của tầng 1 trong cùng giây → hai request cùng thấy "tầng 2 chưa mở"
> → cả hai insert. Không có unique constraint là có hai dòng tầng 2, làm sai mọi thống kê sau đó.

## Business rule

| # | Rule |
|---|---|
| BR-022-1 | Ngưỡng 80% — cấu hình được, không hardcode |
| BR-022-2 | Mở tầng và ghi kết quả tầng trước phải **cùng một transaction** |
| BR-022-3 | Idempotent — chạy nhiều lần cho cùng kết quả |
| BR-022-4 | Unique constraint `(user_id, stage_number)` |
| BR-022-5 | Chỉ mở **một** tầng mỗi lần, không nhảy tầng |

## API · DB

Không có endpoint riêng — chạy trong `POST /api/pronunciation/answer` khi hoàn thành tầng.

`user_pronunciation_progress` (đọc + ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Học 593 điểm ngữ pháp phân bổ HSK1–9. Mỗi điểm gồm cấu trúc, giải thích tiếng Việt, ví dụ,
bài tập ngắn. Lần học đầu tạo lịch ôn FSRS.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `grammar_points` có dữ liệu cho cấp HSK đó
3. Điểm ngữ pháp đã liên kết `knowledge_points`

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Học xong | `user_knowledge_state` tạo dòng mới với `stability`, `difficulty`, `next_review_at` theo FSRS |
| Làm bài tập | Kết quả đúng/sai ảnh hưởng tham số FSRS ban đầu |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-023-1 | 593 điểm: HSK1 70 · HSK2 78 · HSK3 96 · HSK4 95 · HSK5 70 · HSK6 50 · HSK7-9 134 |
| BR-023-2 | Dùng **FSRS**, không phải Leitner |
| BR-023-3 | Lần học đầu bắt buộc tạo `user_knowledge_state` |
| BR-023-4 | Bỏ bài tập → `rating = Hard` |
| BR-023-5 | Mọi điểm ngữ pháp **phải** có `knowledge_point_id` — kiểm khi nhập dữ liệu |
| BR-023-6 | Chấm bài tập ở server |

## API · DB

```
GET  /api/grammar/{id}
GET  /api/grammar/{id}/exercises
POST /api/grammar/{id}/complete
```
`grammar_points` · `knowledge_points` · `questions` (đọc) · `user_knowledge_state` (ghi) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Hệ thống đưa ra các điểm ngữ pháp **đến hạn ôn** (`next_review_at ≤ now()`), người học làm
bài ôn, kết quả tính lại khoảng cách ôn lần sau.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có ít nhất một dòng `user_knowledge_state` với `next_review_at ≤ now()`

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Đúng | `stability` tăng, `next_review_at` giãn xa |
| Sai | `stability` giảm, `next_review_at` gần lại (thường trong ngày) |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-024-1 | Sắp theo `next_review_at` cũ nhất trước |
| BR-024-2 | Tối đa 50 điểm mỗi buổi |
| BR-024-3 | Cập nhật FSRS ngay sau **từng** câu, không đợi hết buổi |
| BR-024-4 | Bài có đáp án → server tự tính `rating`; chỉ bài tự đánh giá mới nhận `rating` từ client |
| BR-024-5 | Ôn sớm được phép nhưng giãn ít hơn |
| BR-024-6 | Không ôn điểm chưa học |

## API · DB

```
GET  /api/review/due?type=GRAMMAR
POST /api/review/answer
```
`user_knowledge_state` (đọc + ghi) · `grammar_points` · `questions` (đọc) · `study_sessions` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Hiện danh sách chủ đề (Gia đình, Số đếm, Thời gian…) kèm % hoàn thành và trạng thái khoá/mở.
Đây là **cửa vào** của tính năng 1.5 và là nền của cây chủ đề 3.2.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `topics` có dữ liệu
3. Mỗi chủ đề có ít nhất 1 dòng `topic_words`

## Hậu điều kiện

Không đổi dữ liệu — use case chỉ đọc. Nhưng phải trả % **tính đúng** vì UC-046 dùng số này
để mở khoá.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-025-1 | `completion_percent` = số từ `MASTERED` / tổng `topic_words` × 100 |
| BR-025-2 | `COMPLETED` khi ≥ 90% (ngưỡng cổng đã chốt) |
| BR-025-3 | Chủ đề gốc mở sẵn; còn lại theo tiên quyết |
| BR-025-4 | `user_topic_progress` và `user_knowledge_state` cập nhật cùng transaction |
| BR-025-5 | Chủ đề rỗng không hiện |
| BR-025-6 | Cần đăng nhập — không có chế độ `GUEST` |

## API · DB

```
GET /api/topics
GET /api/topics?hsk_level={n}
```
`topics` · `topic_words` · `user_topic_progress` · `user_knowledge_state` (đọc)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Bước ② trong luồng 6 bước của chủ đề. Học từng từ: chữ Hán · pinyin · âm Hán-Việt · nghĩa ·
câu ví dụ · audio · từ liên quan. Mỗi từ chuyển từ "Chưa học" → "Đang học".

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Chủ đề ở trạng thái `AVAILABLE` hoặc `IN_PROGRESS` (không `LOCKED`)
3. `topic_words` có từ chưa học

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Học một từ | `user_knowledge_state` tạo dòng, trạng thái `LEARNING`, có `next_review_at` |
| Học hết từ mới | `user_topic_progress` cập nhật; chuyển sang UC-027 |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-026-1 | Mỗi lượt tối đa 10 từ mới — quá nhiều thì không nhớ |
| BR-026-2 | Từ mới vào trạng thái `LEARNING`, không phải `MASTERED` |
| BR-026-3 | `user_knowledge_state` + `user_topic_progress` cùng transaction (BR-025-4) |
| BR-026-4 | "Tôi đã biết" đặt cờ `self_declared`, UC-029 kiểm lại |
| BR-026-5 | `word_id` phải thuộc `topic_words` của chủ đề đang học |
| BR-026-6 | Kiểm khoá ở server |

## API · DB

```
GET  /api/topics/{id}/words?status=NEW
POST /api/topics/{id}/progress
```
`topics` · `topic_words` · `words` · `characters` (đọc) · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Bước ③ của luồng chủ đề. Trắc nghiệm nhận diện từ **trong phạm vi chủ đề** — đáp án nhiễu
lấy từ cùng chủ đề để bài có độ khó phù hợp.

## Tiền điều kiện

1. `USER` đã đăng nhập, chủ đề không khoá
2. Có ≥ 4 từ ở trạng thái `LEARNING` trở lên trong chủ đề (cần 1 đúng + 3 nhiễu)

## Hậu điều kiện

Mastery từng từ cập nhật; từ đúng liên tục 3 lần chuyển `LEARNING` → `MASTERED`.

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
|---|---|---|---|
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
|---|---|
| BR-027-1 | Nhiễu ưu tiên cùng chủ đề; không đủ thì cùng cấp HSK |
| BR-027-2 | Nhiễu **không được trùng nghĩa** với đáp án đúng |
| BR-027-3 | Đúng 3 lần liên tiếp → `MASTERED` |
| BR-027-4 | Chấm ở server |
| BR-027-5 | `MASTERED` cập nhật `user_topic_progress` cùng transaction |

## API · DB

```
GET  /api/topics/{id}/practice?mode=RECOGNITION
POST /api/topics/{id}/practice/answer
```
`topic_words` · `words` (đọc) · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Bước ④ của luồng chủ đề. Phát audio từ, chọn chữ hoặc nghĩa đúng.

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
|---|---|---|---|
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
|---|---|
| BR-028-1 | Chỉ dùng từ có `audio_url` |
| BR-028-2 | Nhiễu **không trùng pinyin** (cả dấu thanh) với đáp án đúng |
| BR-028-3 | Audio lỗi không tính sai |
| BR-028-4 | Phát lại tối đa 3 lần |
| BR-028-5 | Mastery ghi vào `LISTENING` |

## API · DB

```
GET  /api/topics/{id}/practice?mode=LISTENING
POST /api/topics/{id}/practice/answer
```
`topic_words` · `words` (đọc `audio_url`) · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Bước ⑥ — bước **quyết định**. Bài kiểm tra tổng hợp cả chủ đề, chấm ở server. Kết quả quyết
định `completion_percent` có đạt 90% để mở chủ đề tiếp (UC-046) hay không.

## Tiền điều kiện

1. `USER` đã đăng nhập, chủ đề không khoá
2. Đã học ≥ 80% số từ trong chủ đề (không cho thi khi chưa học)
3. Có đủ câu hỏi cho chủ đề

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Đạt ≥ 90% | `user_topic_progress.status = COMPLETED`; kích hoạt UC-046 |
| Dưới 90% | Ghi kết quả, hiện các từ sai, gợi ý luyện lại; chủ đề sau **vẫn khoá** |
| Từ `self_declared` sai | **Hạ** từ `MASTERED` về `LEARNING` |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
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
Hạ về `LEARNING`, `completion_percent` **giảm**. Đây là cơ chế kiểm lại của BR-026-4.

**A3 — Bỏ giữa bài**
`attempts` giữ trạng thái `IN_PROGRESS`. Cho nộp tiếp trong 24h; quá hạn → `ABANDONED`,
không tính điểm.

**A4 — Thi lại**
Lần thi mới tạo `attempts` mới. `completion_percent` lấy kết quả **cao nhất**, không phải mới nhất.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
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
|---|---|
| BR-029-1 | Chấm **bắt buộc ở server** — bước 4 không trả đáp án |
| BR-029-2 | Cần học ≥ 80% từ mới được thi |
| BR-029-3 | Ngưỡng đạt: **90%** (ngưỡng cổng đã chốt) |
| BR-029-4 | Một `attempt_id` nộp **một lần** |
| BR-029-5 | Kiểm sở hữu `attempt` trước khi nộp |
| BR-029-6 | Điểm + mastery + mở khoá trong **một transaction** |
| BR-029-7 | `completion_percent` lấy điểm **cao nhất** các lần thi |
| BR-029-8 | Từ `self_declared` sai → hạ về `LEARNING` |
| BR-029-9 | Thi lại cách nhau ≥ 10 phút |
| BR-029-10 | Câu không trả lời tính là sai |

## API · DB

```
POST /api/topics/{id}/final-test/start
POST /api/topics/{id}/final-test/{attempt_id}/submit
```
`topic_words` · `words` · `questions` · `question_options` (đọc) · `attempts` · `attempt_answers` · `user_knowledge_state` · `user_topic_progress` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
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

Xem video học tiếng Trung với phụ đề song ngữ + pinyin. Điều khiển học: tua lại câu, giảm
tốc, lặp câu, ẩn/hiện từng lớp phụ đề.

> ⚠️ **Scope V2.** Feature tree ghi khối lượng **2–4 tuần**, phần khó nhất là đồng bộ phụ đề
> theo thời gian video. Nguồn video **chưa chốt**.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. `videos` có video, `video_subtitles` có phụ đề đã gắn timestamp
3. Nguồn video khả dụng (nhúng YouTube hoặc CDN có giấy phép)

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Xem xong | ⚠️ **Chưa có bảng để ghi tiến độ** — xem exception dưới |
| Lưu từ | UC-032 xử lý |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `USER` | Chọn video theo cấp HSK/chủ đề |
| 2 | System | `GET /api/videos` — danh sách kèm cấp, độ dài, ảnh bìa |
| 3 | `USER` | Chọn video |
| 4 | System | `GET /api/videos/{id}/subtitles` — phụ đề kèm `start_ms`, `end_ms`, chữ, pinyin, nghĩa |
| 5 | Client | Load player, đồng bộ phụ đề theo `currentTime` |
| 6 | `USER` | Xem; dùng nút tua lại câu / giảm tốc / lặp câu |
| 7 | Client | Highlight câu phụ đề đang phát |
| 8 | `USER` | Bấm từ trong phụ đề → UC-031 |
| 9 | Client | `POST /api/videos/{id}/progress` ⚠️ **không có bảng nhận** |

## Luồng thay thế

**A1 — Tua lại câu** — client `seek` về `start_ms` của câu hiện tại.
**A2 — Lặp một câu** — client lặp trong `[start_ms, end_ms]` tới khi tắt.
**A3 — Ẩn lớp phụ đề** — thuần client, lưu `localStorage`.
**A4 — Video bị xoá ở nguồn (YouTube)** — hiện "video không còn khả dụng", đánh dấu `videos.status = UNAVAILABLE` để `CONTENT_ADMIN` xử lý.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `NO_PROGRESS_TABLE` | 500 | 🔴 `POST /api/videos/{id}/progress` **không có bảng nào lưu** | **Chặn tính năng.** Phải quyết trước khi làm — xem mục cuối file |
| `VIDEO_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `VIDEO_UNAVAILABLE` | 410 | Nguồn đã xoá | Ẩn khỏi danh sách, báo `CONTENT_ADMIN` (A4) |
| `NO_SUBTITLES` | 422 | Video chưa có phụ đề | Không mở — video không phụ đề mất hết giá trị học |
| `SUBTITLE_TIMING_INVALID` | 500 | `start_ms ≥ end_ms` hoặc chồng lấn | Phụ đề nhảy sai, không highlight đúng câu. Kiểm khi nhập |
| `SUBTITLE_OUT_OF_RANGE` | 500 | `end_ms > video_duration_ms` | Phụ đề dài hơn video — dữ liệu sai |
| `VIDEO_SOURCE_BLOCKED` | — | YouTube chặn nhúng ở domain | Hiện hướng dẫn mở trên YouTube |

> 🔴 **`NO_PROGRESS_TABLE` là khoảng trống thiết kế đã được chính tài liệu DB v5 cảnh báo.**
> `user_video_progress` bị bỏ khi gộp bảng ở bản 3 và **không có bảng nào thay**. Hệ quả:
> người học xem 20 phút video, thoát ra, quay lại phải xem từ đầu. Không phải bug — là thiếu
> thiết kế. Phải chốt trước khi bắt đầu tính năng 1.6.

> ⚠️ **`VIDEO_SOURCE_BLOCKED` và bản quyền.** Constitution cấm tải video người khác về máy
> chủ. Nếu nhúng YouTube thì phụ thuộc hoàn toàn vào nguồn: video bị xoá, bị chặn nhúng, hoặc
> chủ kênh đổi quyền → tính năng chết mà ta không làm gì được.

## Business rule

| # | Rule |
|---|---|
| BR-030-1 | **Không** tải video người khác về máy chủ (bản quyền) |
| BR-030-2 | Video không phụ đề thì không mở |
| BR-030-3 | Phụ đề phải có `start_ms < end_ms`, không chồng lấn |
| BR-030-4 | Video nguồn lỗi → `UNAVAILABLE`, ẩn khỏi danh sách |
| BR-030-5 | ⚠️ **Chặn**: cần bảng ghi tiến độ trước khi làm |

## API · DB

```
GET  /api/videos
GET  /api/videos/{id}/subtitles
POST /api/videos/{id}/progress   ⚠️ chưa có bảng
```
`videos` · `video_subtitles` (đọc) · **thiếu bảng tiến độ** (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Mở video có phụ đề | Player chạy, phụ đề đồng bộ |
| T2 | Video không phụ đề | 422 `NO_SUBTITLES` |
| T3 | Phụ đề `start_ms = end_ms` | Kiểm chặn khi nhập |
| T4 | Xem 5 phút, thoát, mở lại | ⚠️ **Hiện không lưu được** |
| T5 | YouTube ID đã xoá | 410, `status = UNAVAILABLE` |

---

# UC-031 · Bấm từ trong phụ đề xem nghĩa

| | |
|---|---|
| **UC-ID** | UC-031 · **Actor** `USER` · **Pri** P2 · **Scope** **V2** · **FT** 1.6 |

## Mô tả

Bấm một từ trong phụ đề → popup hiện chữ, pinyin, nghĩa, audio, nút lưu vào sổ tay/flashcard.
Video **tự tạm dừng** khi mở popup.

## Tiền điều kiện

1. Đang xem video (UC-030)
2. Phụ đề đã **tách từ** (word segmentation) — không phải cả câu liền
3. Từ có dòng trong `words` hoặc `characters`

## Hậu điều kiện

Không đổi dữ liệu học. Chỉ ghi `feature_usage` nếu dùng lượt tra (tính năng 4.1 tốn phí).

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `USER` | Bấm một từ trong phụ đề |
| 2 | Client | Tạm dừng video |
| 3 | Client | Tra `video_subtitles.vocabulary` JSONB — dữ liệu đã kèm sẵn |
| 4 | Client | Nếu không có, gọi `GET /api/dictionary/lookup?word=X` |
| 5 | Client | Hiện popup: chữ, pinyin, âm Hán-Việt, nghĩa, audio, nút lưu |
| 6 | `USER` | Đọc; có thể bấm lưu (UC-032) hoặc đóng |
| 7 | Client | Đóng popup, phát tiếp video |

## Luồng thay thế

**A1 — Từ đã có trong `vocabulary` JSONB** — không gọi API, hiện ngay (đường nhanh).
**A2 — Từ không có trong từ điển** — hiện "chưa có trong từ điển", cho báo lỗi để `CONTENT_ADMIN` bổ sung.
**A3 — Bấm vào dấu câu hoặc khoảng trắng** — bỏ qua, không mở popup.
**A4 — Bấm nhiều từ liên tiếp** — popup cũ đóng, popup mới mở; video vẫn dừng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `WORD_NOT_SEGMENTED` | 500 | 🔴 Phụ đề chưa tách từ | **Bấm cả câu thay vì một từ** — tính năng vô dụng. Phải tách khi nhập phụ đề |
| `WORD_NOT_IN_DICTIONARY` | 404 | Từ không có trong `words` | Hiện "chưa có", cho báo lỗi (A2) |
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
|---|---|
| BR-031-1 | Bấm từ → video **tự tạm dừng** |
| BR-031-2 | Phụ đề phải tách từ khi nhập, không tách lúc chạy |
| BR-031-3 | Ưu tiên `vocabulary` JSONB, chỉ gọi API khi thiếu |
| BR-031-4 | Từ thiếu trong từ điển cho người học báo lỗi |
| BR-031-5 | Cho chọn nhiều ký tự để bù lỗi tách từ |

## API · DB

```
GET /api/dictionary/lookup?word={x}
```
`video_subtitles.vocabulary` (JSONB, đọc) · `words` · `characters` (đọc)

> Theo AC-09 trong constitution: `vocabulary` JSONB **chỉ đọc**, không ghi.

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Bấm từ có trong JSONB | Popup hiện ngay, không gọi API |
| T2 | Bấm từ thiếu trong JSONB | Gọi `/api/dictionary/lookup` |
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

Từ popup UC-031, lưu từ vào `notes` (sổ tay) hoặc `flashcard_decks` (bộ flashcard), kèm câu
ví dụ lấy từ chính phụ đề video.

## Tiền điều kiện

1. Popup UC-031 đang mở
2. `USER` đã đăng nhập
3. Có bộ flashcard đích (hoặc tạo mới)

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Lưu sổ tay | `notes` thêm dòng: từ, nghĩa, câu ví dụ từ phụ đề, nguồn video |
| Lưu flashcard | `flashcards` thêm dòng vào deck đã chọn, có `next_review_at` |

## Luồng chính

| # | Actor | Hành động |
|---|---|---|
| 1 | `USER` | Bấm "Lưu vào sổ tay" hoặc "Thêm flashcard" |
| 2 | Client | Nếu flashcard: hiện chọn deck hoặc tạo deck mới |
| 3 | `USER` | Chọn deck |
| 4 | Client | `POST /api/notes` hoặc `POST /api/flashcard-decks/{id}/cards` |
| 5 | System | Kiểm sở hữu deck |
| 6 | System | Kiểm trùng — từ này đã trong deck chưa |
| 7 | System | Ghi kèm `source_video_id`, `subtitle_id`, câu ví dụ |
| 8 | System | Nếu flashcard: tính `next_review_at` ban đầu |
| 9 | Client | Hiện "đã lưu", đóng popup, phát tiếp video |

## Luồng thay thế

**A1 — Từ đã có trong deck** — không tạo dòng trùng, hiện "đã có trong bộ này", cho chuyển deck khác.
**A2 — Chưa có deck nào** — hiện tạo deck mới rồi thêm thẻ trong **một** thao tác.
**A3 — Lưu cả hai nơi** — cho phép; hai bản ghi độc lập.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
|---|---|---|---|
| `DECK_NOT_OWNED` | 403 | 🔴 Deck của người khác | **IDOR** — `flashcard_decks.user_id` phải bằng người đang đăng nhập |
| `DECK_NOT_FOUND` | 404 | ID sai | Hiện chọn deck khác |
| `CARD_ALREADY_IN_DECK` | 409 | Từ đã có | Không tạo trùng (A1) |
| `DECK_LIMIT_EXCEEDED` | 422 | Deck > 500 thẻ | Hiện "bộ đã đầy, tạo bộ mới" |
| `NOTE_LIMIT_EXCEEDED` | 422 | > 1000 ghi chú | Chặn spam |
| `SOURCE_SUBTITLE_NOT_FOUND` | 400 | `subtitle_id` không thuộc video | Vẫn lưu từ nhưng **không** gắn câu ví dụ |
| `UNAUTHORIZED` | 401 | Token hết hạn giữa lúc xem | Refresh rồi gửi lại — không mất thao tác lưu |

> 🔴 **`DECK_NOT_OWNED` là IDOR đúng loại mục B đã ghi.** `POST /api/flashcard-decks/999/cards`
> với deck 999 của người khác: nếu chỉ kiểm "đã đăng nhập" thì ta vừa cho ghi vào dữ liệu của
> người lạ. Cả UC-029 (`ATTEMPT_NOT_OWNED`) và UC này đều là cùng một lỗ hổng ở hai chỗ khác
> nhau — nên có **một** `OwnershipService` dùng chung, không kiểm rời rạc từng endpoint.

## Business rule

| # | Rule |
|---|---|
| BR-032-1 | Kiểm sở hữu deck ở server |
| BR-032-2 | Không thêm thẻ trùng trong cùng deck |
| BR-032-3 | Lưu kèm `source_video_id` để truy nguồn |
| BR-032-4 | Câu ví dụ lấy từ phụ đề tại thời điểm bấm |
| BR-032-5 | Deck tối đa 500 thẻ; sổ tay tối đa 1000 ghi chú |
| BR-032-6 | Lưu được cả sổ tay và flashcard |

## API · DB

```
POST /api/notes
POST /api/flashcard-decks/{id}/cards
POST /api/flashcard-decks          (tạo deck mới — A2)
```
`words` · `video_subtitles` (đọc) · `notes` · `flashcard_decks` · `flashcards` (ghi)

## Test case

| # | Đầu vào | Kết quả |
|---|---|---|
| T1 | Lưu vào sổ tay | `notes` thêm dòng có `source_video_id` |
| T2 | Thêm vào deck của mình | `flashcards` thêm dòng, có `next_review_at` |
| T3 | Deck của người khác | 403 `DECK_NOT_OWNED` |
| T4 | Thêm từ đã có | 409 `CARD_ALREADY_IN_DECK` |
| T5 | Deck đã 500 thẻ | 422 `DECK_LIMIT_EXCEEDED` |
| T6 | `subtitle_id` sai | Lưu từ, không có câu ví dụ |

---

# Tổng hợp exception nhóm 1

## Mười exception quan trọng nhất

| # | UC | Exception | Vì sao |
|---|---|---|---|
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
|---|---|---|
| **Chấm ở client không đáng tin** | UC-015 → UC-018 (luyện viết) | `hanzi-writer` chấm ở client, `stroke_data` nằm trong response. **Không thể** chấm lại ở server. Giảm rủi ro bằng hệ số mastery thấp + kiểm thời gian hợp lý, và để UC-029 (chấm server) làm thước đo thật |
| **Nhiễu trùng đáp án** | UC-019 · UC-020 · UC-027 · UC-028 | Trùng `word_id` · trùng **nghĩa** · trùng **pinyin có dấu**. Cả ba đều làm câu hỏi vô nghĩa. Phải kiểm khi **sinh** câu, không phải khi chấm |
| **Hai bảng phải cùng transaction** | UC-022 · UC-026 · UC-027 · UC-029 | `user_knowledge_state` + `user_topic_progress`, hoặc điểm + mở khoá. Tách ra là sinh dữ liệu lệch mà không ai phát hiện tới lúc người học phàn nàn |

---

# Khoảng trống thiết kế phát hiện ở nhóm 1

| # | Thiếu | UC bị ảnh hưởng | Mức |
|---|---|---|---|
| 1 | **Không có bảng ghi tiến độ video** — `user_video_progress` bị bỏ ở bản 3, không có bảng thay | UC-030 | 🔴 Chặn tính năng 1.6 |
| 2 | **Không có bảng lưu `challenge_id`** với `served_at` + trạng thái đã nộp | UC-017 | 🔴 Không chống được nộp lại |
| 3 | **Lệch tài liệu:** feature tree 1.3 ghi `pronunciation_stages`, danh sách 40 bảng không có | UC-021 · UC-022 | ⚠️ Cần chốt: thêm bảng hay suy từ cột |
| 4 | Chưa có cột `self_declared` trong `user_knowledge_state` | UC-026 · UC-029 | ⚠️ Cần cho BR-026-4 |
| 5 | Chưa có bảng/cột lưu **hệ số mastery theo chế độ luyện** (0.5 / 1.0 / 1.2) | UC-015 → UC-018 | ⚠️ Hiện là con số trong code |
| 6 | Chưa có `OwnershipService` dùng chung chống IDOR | UC-029 · UC-032 | 🔴 Mục B trong quyết định v2 vẫn chưa chốt |
| 7 | Chưa chốt **nguồn video** (tự quay / có giấy phép / nhúng YouTube) | UC-030 | ⚠️ Ảnh hưởng bản quyền |
| 8 | Chưa có unique constraint `(user_id, stage_number)` trên `user_pronunciation_progress` | UC-022 | 🔴 Race condition |
| 9 | Chưa có partial unique index đảm bảo **đúng 1** `is_correct` mỗi câu hỏi | UC-019 · UC-020 | 🔴 Chấm sai oan người học |
| 10 | Chưa có job đối chiếu `user_topic_progress` với `user_knowledge_state` | UC-025 | ⚠️ Phát hiện lệch sớm |
| 11 | Chưa chốt từ phụ đề có tính vào **quota tra từ** (4.1) hay không | UC-031 | ⚠️ Chờ `TODO(PAYMENT_SCOPE)` |

> **Bốn mục 🔴 ở trên (1, 2, 6, 8, 9) là ràng buộc DB hoặc bảng còn thiếu.** Đây chính là loại
> phát hiện cần có **trước** khi chốt schema — gom vào bản requirement v2.

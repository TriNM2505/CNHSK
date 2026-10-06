# CNHSK — Đặc tả Use Case · Nhóm 4 · Thư viện tra cứu

> **UC-056 → UC-068** · 13 use case · Tính năng 4.1 → 4.3
> **Bản final** · cập nhật 2026-10-01
> **Tám file đặc tả:** `use-cases-01-xac-thuc` → `use-cases-08-quan-tri-he-thong`
> (file catalog riêng đã bỏ — mỗi file tự liệt UC của nhóm mình)
>
> Nhóm duy nhất có UC cho `GUEST` (4 UC tra từ điển). Cũng là nhóm có tính năng **tốn tiền
> mỗi lượt** (dịch — gọi API ngoài).

---

## Bảng tra nhanh

| UC-ID | Use case | Actor | Pri | Scope | FT |
| --- | --- | --- | --- | --- | --- |
| UC-056 | Tra từ điển bằng chữ Hán | `GUEST` `USER` | P0 | MVP | 4.1 |
| UC-057 | Tra từ điển bằng pinyin | `GUEST` `USER` | P1 | MVP | 4.1 |
| UC-058 | Tra từ điển bằng nghĩa Việt | `GUEST` `USER` | P1 | MVP | 4.1 |
| UC-059 | Tra chữ theo bộ thủ và số nét | `GUEST` `USER` | P2 | MVP | 4.1 |
| UC-060 | Dịch câu hoặc đoạn văn Trung ↔ Việt | `USER` | P1 | MVP | 4.1 |
| UC-061 | Bấm từ trong kết quả dịch xem nghĩa | `USER` | P2 | MVP | 4.1 |
| UC-062 | Thêm từ từ kết quả tra vào sổ tay | `USER` | P2 | MVP | 4.1 |
| UC-063 | Thêm từ từ kết quả tra vào flashcard | `USER` | P2 | MVP | 4.1 |
| UC-064 | Tạo, sửa, xóa ghi chú cá nhân | `USER` | P1 | MVP | 4.2 |
| UC-065 | Tìm kiếm trong ghi chú | `USER` | P2 | MVP | 4.2 |
| UC-066 | Ôn flashcard theo lịch | `USER` | P1 | MVP | 4.3 |
| UC-067 | Tạo bộ flashcard mới | `USER` | P1 | MVP | 4.3 |
| UC-068 | Chia sẻ bộ flashcard lên cộng đồng | `USER` | P3 | V2 | 4.3 |

---

# UC-056 · Tra từ điển bằng chữ Hán

| | |
| --- | --- |
| **UC-ID** | UC-056 · **Actor** `GUEST` `USER` · **Pri** P0 · **Scope** MVP · **FT** 4.1 |
| **Client** | Web · Mobile · Shared |

## Mô tả

Nhập chữ Hán, nhận: pinyin · nghĩa · âm Hán-Việt · cấu tạo · câu chuyện chữ · animation nét ·
từ ghép chứa chữ. `GUEST` tra được **có giới hạn**.

## Tiền điều kiện

1. `characters` hoặc `words` có dữ liệu
2. Nếu `GUEST`: chưa vượt giới hạn tra theo IP

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| `USER` tra | Ghi `feature_usage` nếu tính lượt; cache Redis `learn:dict:*` |
| `GUEST` tra | Tăng bộ đếm theo IP; **không** ghi vào tài khoản |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Người dùng | Nhập chữ 好 vào ô tra |
| 2 | Client | `GET /api/public/dictionary/lookup?q=好` (hoặc `/api/dictionary/...` nếu đã đăng nhập) |
| 3 | System | Kiểm cache Redis `learn:dict:好` |
| 4 | System | Cache miss → truy vấn `characters` theo chữ chính xác |
| 5 | System | Lấy thêm `word_characters` → từ ghép chứa chữ |
| 6 | System | Ghi cache TTL 24h |
| 7 | System | Trả kết quả; nếu `GUEST` thì **rút gọn** (xem BR) |
| 8 | Client | Hiện chi tiết chữ, nút animation nét, nút lưu (chỉ `USER`) |

## Luồng thay thế

**A1 — Nhập nhiều chữ (好吗)**
Tra như một **từ** trong `words` trước; không có thì tách từng chữ và trả danh sách.

**A2 — Không tìm thấy**
Gợi ý chữ gần giống (cùng bộ thủ hoặc cùng pinyin).

**A3 — `GUEST` vượt giới hạn**
Trả `GUEST_LIMIT_EXCEEDED`, hiện "đăng nhập để tra không giới hạn".

**A4 — Nhập tiếng Việt hoặc pinyin**
Tự nhận loại đầu vào, chuyển sang UC-057 hoặc UC-058.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `EMPTY_QUERY` | 400 | `q` rỗng | Chặn |
| `QUERY_TOO_LONG` | 400 | > 50 ký tự | Đây là tra từ, không phải dịch đoạn (UC-060) |
| `CHARACTER_NOT_FOUND` | 404 | Không có trong từ điển | Gợi ý chữ gần giống (A2) |
| `GUEST_LIMIT_EXCEEDED` | 429 | `GUEST` vượt N lượt/giờ theo IP | 🔴 Xem ghi chú |
| `NO_PUBLIC_ENDPOINT_CONVENTION` | 500 | ⚠️ Chưa có quy ước endpoint public | Xem ghi chú |
| `CACHE_UNAVAILABLE` | — | Redis chết | **Không chặn** — truy vấn thẳng DB, chậm hơn nhưng vẫn chạy |
| `SQL_INJECTION_ATTEMPT` | 400 | Input có ký tự SQL | Dùng prepared statement — constitution cấm nối chuỗi SQL |
| `STROKE_DATA_MISSING` | 200 | Chữ không có `stroke_data` | Ẩn nút animation, vẫn trả nghĩa |

> 🔴 **`GUEST_LIMIT_EXCEEDED` — giới hạn theo IP có hai vấn đề thật:**
> — **Chung IP:** cả phòng máy trường hoặc một mạng 4G dùng NAT → nhiều người chung một IP →
> người thứ hai bị chặn dù chưa tra gì.
> — **Dễ lách:** đổi IP là reset bộ đếm.
> Giới hạn theo IP là phương án duy nhất khi không có tài khoản, nhưng phải đặt **ngưỡng rộng**
> (ví dụ 50 lượt/giờ) để không chặn oan, và hiểu rằng nó chỉ chống lạm dụng thô.

> ⚠️ **`NO_PUBLIC_ENDPOINT_CONVENTION` — mục D (endpoint public cho `GUEST`) — xem `AGENTS.md` §12
> vẫn chưa chốt.** `Move_home` có `HR-17: Public vs Authenticated endpoints — Guest mode`.
> CNHSK chưa có. Không có quy ước thì mỗi người tự quyết endpoint nào public → có endpoint lẽ
> ra cần đăng nhập lại để mở.
> **Khuyến nghị:** tiền tố `/api/public/*` bắt buộc, cộng comment giải thích ở mỗi endpoint public.

## Business rule

| # | Rule |
| --- | --- |
| BR-056-1 | Cả `GUEST` và `USER` đều được tra từ điển bằng chữ Hán. |
| BR-056-2 | `GUEST` được tra cứu có giới hạn theo IP để tránh lạm dụng. Giới hạn phải đủ rộng để tránh chặn oan người dùng chung mạng. |
| BR-056-3 | `GUEST` chỉ được xem kết quả tra cứu, không được lưu vào sổ tay hoặc flashcard. |
| BR-056-4 | `USER` đã đăng nhập được dùng đầy đủ kết quả tra cứu và các nút lưu vào sổ tay hoặc flashcard. |
| BR-056-5 | Nếu người dùng nhập một chữ Hán, hệ thống tra theo chữ. Nếu nhập nhiều chữ, hệ thống ưu tiên tra như một từ trước, sau đó mới tách từng chữ nếu không tìm thấy từ. |
| BR-056-6 | Truy vấn tra từ điển chỉ dùng cho từ hoặc cụm ngắn. Nếu nội dung quá dài, hệ thống yêu cầu người dùng chuyển sang chức năng dịch. |
| BR-056-7 | Nếu chữ có dữ liệu nét thì hiển thị animation viết chữ. Nếu thiếu dữ liệu nét, hệ thống vẫn hiển thị nghĩa, pinyin và thông tin còn lại. |
| BR-056-8 | Tra từ điển là chức năng đọc dữ liệu nội bộ, không tính lượt dịch hoặc quota gọi API ngoài. |

## API · DB

```
GET /api/public/dictionary/lookup?q={x}     (GUEST)
GET /api/dictionary/lookup?q={x}            (USER)
```

`characters` · `words` · `word_characters` (đọc) · Redis `learn:dict:*`

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `q=好` | Pinyin, nghĩa, Hán-Việt, từ ghép |
| T2 | Tra lần 2 cùng chữ | Lấy từ cache, nhanh hơn |
| T3 | Redis tắt | Vẫn trả kết quả từ DB |
| T4 | `GUEST` tra lần 51 trong giờ | 429 |
| T5 | `q=` rỗng | 400 |
| T6 | `q` 100 ký tự | 400 `QUERY_TOO_LONG` |
| T7 | `GUEST` xem response | **Không** có nút lưu |
| T8 | `q='; DROP TABLE--` | Không lỗi SQL, trả 404 |

---

# UC-057 · Tra từ điển bằng pinyin

| | |
|---|---|
| **UC-ID** | UC-057 · **Actor** `GUEST` `USER` · **Pri** P1 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Nhập pinyin (có hoặc không dấu thanh) → danh sách chữ/từ có pinyin đó.

## Tiền điều kiện

`characters.pinyin` và `words.pinyin` đã chuẩn hoá (có bản không dấu để tìm).

## Hậu điều kiện

Chỉ đọc + cache.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Người dùng | Nhập `hao` hoặc `hǎo` |
| 2 | System | Chuẩn hoá: bỏ dấu, chữ thường, bỏ khoảng trắng |
| 3 | System | Tìm theo `pinyin_normalized` |
| 4 | System | Sắp theo tần suất dùng (chữ phổ biến trước) |
| 5 | System | Trả danh sách kèm pinyin **có dấu** để phân biệt |
| 6 | Client | Hiện danh sách, bấm vào một mục → UC-056 |

## Luồng thay thế

**A1 — Nhập có dấu thanh**
Tìm chính xác trước (`hǎo` → chỉ thanh 3), không có thì nới ra mọi thanh.

**A2 — Nhập nhiều âm tiết (`nihao`)**
Tách âm tiết theo quy tắc pinyin, tra như một từ.

**A3 — Pinyin không hợp lệ (`xyz`)**
Không có tổ hợp đó trong tiếng Trung. Trả rỗng kèm gợi ý.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `INVALID_PINYIN` | 200 (rỗng) | Tổ hợp không tồn tại | Gợi ý pinyin gần giống |
| `TOO_MANY_RESULTS` | 200 | `shi` có hàng chục chữ | Phân trang, sắp theo tần suất. **Không** trả hết một lần |
| `PINYIN_NOT_NORMALIZED` | 500 | 🔴 DB chưa có cột không dấu | Xem ghi chú |
| `AMBIGUOUS_SYLLABLE_SPLIT` | 200 | `xian` = `xian` hay `xi`+`an` | Trả **cả hai** cách hiểu, để người dùng chọn |
| `GUEST_LIMIT_EXCEEDED` | 429 | Vượt giới hạn | Như UC-056 |
| `EMPTY_QUERY` | 400 | Rỗng | Chặn |

> 🔴 **`PINYIN_NOT_NORMALIZED` — nếu chỉ lưu pinyin có dấu thì tra không dấu không chạy.**
> Người Việt gõ `hao`, DB lưu `hǎo` → `WHERE pinyin = 'hao'` không khớp. Dùng `LIKE '%hao%'`
> thì chậm và sai (khớp cả `haoxiang`).
> **Cần:** cột `pinyin_normalized` (không dấu, chữ thường) + index. Thêm lúc nhập dữ liệu, không
> tính lúc truy vấn.

> ⚠️ **`AMBIGUOUS_SYLLABLE_SPLIT` là đặc thù pinyin.** `xian` hợp lệ như một âm tiết, cũng hợp
> lệ như `xi` + `an`. Trả một cách là bỏ mất kết quả người dùng cần. Đây là lý do nên trả cả hai.

## Business rule

| # | Rule |
| --- | --- |
| BR-057-1 | Người dùng có thể tra bằng pinyin có dấu thanh hoặc không có dấu thanh. |
| BR-057-2 | Khi người dùng nhập pinyin có dấu thanh, hệ thống ưu tiên kết quả khớp đúng thanh điệu trước. |
| BR-057-3 | Khi người dùng nhập pinyin không dấu, hệ thống trả các chữ hoặc từ có cùng âm đọc, bao gồm các thanh điệu khác nhau. |
| BR-057-4 | Kết quả phải hiển thị pinyin có dấu thanh để người học phân biệt các chữ đồng âm khác thanh. |
| BR-057-5 | Kết quả tra pinyin được sắp xếp theo mức độ phổ biến hoặc tần suất dùng, để chữ/từ thường gặp hiện trước. |
| BR-057-6 | Nếu có quá nhiều kết quả, hệ thống phải phân trang hoặc giới hạn số kết quả trả về mỗi lần. |
| BR-057-7 | Nếu pinyin có thể tách âm tiết theo nhiều cách hợp lệ, hệ thống trả các cách hiểu hợp lệ để người dùng chọn. |

## API · DB

```
GET /api/public/dictionary/search?type=pinyin&q={x}
```

`characters` · `words` (đọc) · Redis

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | `hao` | Mọi chữ đọc hao, mọi thanh |
| T2 | `hǎo` | Ưu tiên thanh 3 |
| T3 | `HAO` | Cùng kết quả T1 (chữ thường hoá) |
| T4 | `xyz` | 200 rỗng + gợi ý |
| T5 | `shi` | Phân trang, không trả hết |
| T6 | `xian` | Cả `xian` và `xi`+`an` |

---

# UC-058 · Tra từ điển bằng nghĩa Việt

| | |
|---|---|
| **UC-ID** | UC-058 · **Actor** `GUEST` `USER` · **Pri** P1 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Nhập tiếng Việt ("xin chào") → từ tiếng Trung tương ứng. Hướng tra **ngược** so với UC-056.

## Tiền điều kiện

`words.meaning_vi` có dữ liệu và **có index tìm kiếm văn bản**.

## Hậu điều kiện

Chỉ đọc + cache.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Người dùng | Nhập "xin chào" |
| 2 | System | Chuẩn hoá: chữ thường, bỏ dấu tuỳ chọn |
| 3 | System | Tìm trong `words.meaning_vi` — khớp cả cụm trước, rồi khớp một phần |
| 4 | System | Sắp: khớp chính xác → khớp đầu chuỗi → khớp giữa |
| 5 | System | Trả danh sách từ Trung kèm pinyin và nghĩa đầy đủ |

## Luồng thay thế

**A1 — Nhiều từ Trung cùng nghĩa Việt**
Ví dụ "bố" → 爸爸, 父亲, 爹. Trả hết, kèm ghi chú sắc thái nếu có.

**A2 — Nhập có dấu / không dấu**
"chào" và "chao" đều tìm được.

**A3 — Nhập cả câu tiếng Việt**
Quá dài cho tra từ → gợi ý chuyển sang dịch (UC-060).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_FULLTEXT_INDEX` | — | 🔴 `meaning_vi` không có index | Xem ghi chú |
| `MEANING_NOT_FOUND` | 200 (rỗng) | Không có từ khớp | Gợi ý dùng dịch (UC-060) |
| `QUERY_TOO_LONG` | 400 | > 50 ký tự | Chuyển sang dịch (A3) |
| `VIETNAMESE_DIACRITIC_MISMATCH` | — | Không tìm được vì dấu | Cần cột không dấu, như pinyin |
| `TOO_MANY_RESULTS` | 200 | Từ phổ biến ("đi", "làm") | Phân trang |
| `GUEST_LIMIT_EXCEEDED` | 429 | Vượt giới hạn | Như UC-056 |

> 🔴 **`NO_FULLTEXT_INDEX` — vấn đề hiệu năng lớn nhất của nhóm 4.** Tìm "xin chào" trong
> `meaning_vi` bằng `LIKE '%xin chào%'` **không dùng được index B-tree** → quét toàn bảng
> `words`. Với vài chục nghìn từ thì mỗi lần tra là một lần full scan.
> **Cần:** PostgreSQL GIN index với `to_tsvector('simple', meaning_vi)`, hoặc `pg_trgm` cho
> khớp một phần. Quyết định này phải có **trong migration đầu**, không thêm sau khi dữ liệu đã
> lớn.

> ⚠️ **`VIETNAMESE_DIACRITIC_MISMATCH` cùng loại với pinyin ở UC-057.** Cần cột chuẩn hoá không
> dấu cho cả `meaning_vi`. Hai chỗ cùng một giải pháp.

## Business rule

| # | Rule |
| --- | --- |
| BR-058-1 | Người dùng có thể tra từ tiếng Trung bằng nghĩa tiếng Việt. |
| BR-058-2 | Hệ thống phải hỗ trợ tìm kiếm tiếng Việt có dấu và không dấu. Ví dụ “chào” và “chao” phải có khả năng trả cùng nhóm kết quả phù hợp. |
| BR-058-3 | Kết quả được ưu tiên theo độ khớp: khớp chính xác trước, sau đó đến khớp đầu cụm, rồi mới đến khớp một phần. |
| BR-058-4 | Nếu nhiều từ tiếng Trung cùng có nghĩa gần giống nhau, hệ thống trả nhiều kết quả và hiển thị đủ pinyin, nghĩa và sắc thái nếu dữ liệu có. |
| BR-058-5 | Nếu truy vấn là một câu hoặc đoạn dài, hệ thống không xử lý như tra từ điển mà gợi ý chuyển sang chức năng dịch. |
| BR-058-6 | Nếu có quá nhiều kết quả, hệ thống phải phân trang hoặc giới hạn số kết quả trả về mỗi lần. |
| BR-058-7 | Tra nghĩa Việt là tra dữ liệu từ điển nội bộ, không tính lượt dịch. |

## API · DB

```
GET /api/public/dictionary/search?type=meaning&q={x}
```

`words` (đọc) · Redis

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | "xin chào" | 你好 và các từ liên quan |
| T2 | "xin chao" | Cùng kết quả T1 |
| T3 | "bố" | 爸爸, 父亲, 爹 |
| T4 | "zzzzz" | 200 rỗng, gợi ý dịch |
| T5 | Câu 80 ký tự | 400, gợi ý dịch |
| T6 | `EXPLAIN` truy vấn | **Dùng index**, không seq scan |

---

# UC-059 · Tra chữ theo bộ thủ và số nét

| | |
|---|---|
| **UC-ID** | UC-059 · **Actor** `GUEST` `USER` · **Pri** P2 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Không biết đọc, không biết nghĩa — chỉ thấy chữ. Chọn bộ thủ + số nét để tìm. Cách tra truyền
thống của từ điển Hán.

## Tiền điều kiện

`characters` có cột bộ thủ và số nét, đã điền đủ.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | Người dùng | Mở "Tra theo bộ thủ" |
| 2 | System | `GET /api/public/dictionary/radicals` — danh sách bộ thủ kèm số chữ mỗi bộ |
| 3 | Người dùng | Chọn bộ 氵 (nước) |
| 4 | Người dùng | Chọn tổng số nét 7 (tuỳ chọn) |
| 5 | System | `GET /api/public/dictionary/by-radical?radical=氵&strokes=7` |
| 6 | System | Trả chữ khớp, sắp theo số nét rồi tần suất |
| 7 | Người dùng | Bấm một chữ → UC-056 |

## Luồng thay thế

**A1 — Chỉ chọn bộ, không chọn số nét**
Trả hết chữ thuộc bộ, nhóm theo số nét.

**A2 — Chỉ chọn số nét**
Trả hết chữ có số nét đó, nhóm theo bộ.

**A3 — Không có chữ nào khớp**
Gợi ý nới điều kiện (±1 nét).

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `RADICAL_NOT_FOUND` | 404 | Bộ thủ không có trong danh sách | Chặn |
| `INVALID_STROKE_COUNT` | 400 | Số nét ngoài 1–36 | Chặn |
| `RADICAL_DATA_INCOMPLETE` | — | 🔴 Nhiều chữ thiếu cột bộ thủ | Xem ghi chú |
| `STROKE_COUNT_INCONSISTENT` | — | Số nét lệch `stroke_data` | Xem ghi chú |
| `NO_MATCH` | 200 (rỗng) | Không khớp | Gợi ý nới ±1 nét (A3) |
| `GUEST_LIMIT_EXCEEDED` | 429 | Vượt giới hạn | Như UC-056 |

> 🔴 **`RADICAL_DATA_INCOMPLETE` quyết định tính năng này sống hay chết.** Tra theo bộ thủ chỉ
> hữu ích khi **gần như mọi** chữ đều có bộ thủ đúng. Thiếu 30% thì người học tra không thấy
> chữ mình cần và kết luận "chức năng này không dùng được".
> **Cần kiểm trước:** đếm `COUNT(*) WHERE radical IS NULL` trên `characters`. Nếu tỉ lệ thiếu
> cao thì nên **cắt UC này** (P2, dễ cắt) hơn là làm nửa vời.

> ⚠️ **`STROKE_COUNT_INCONSISTENT`:** `characters` có cột số nét, và `stroke_data` JSONB cũng
> chứa mảng nét. Hai chỗ có thể lệch. Nguồn tin duy nhất nên là cột số nét (dùng để tra); còn
> `stroke_data` dùng để vẽ. Nhưng phải có kiểm đối chiếu lúc nhập.

## Business rule

| # | Rule |
| --- | --- |
| BR-059-1 | Chức năng tra theo bộ thủ và số nét chỉ nên mở khi dữ liệu bộ thủ và số nét của chữ Hán đủ tin cậy. |
| BR-059-2 | Trong MVP, nếu dữ liệu bộ thủ còn thiếu nhiều, chức năng này được phép cắt hoặc để sau vì mức ưu tiên thấp hơn tra chữ, tra pinyin và tra nghĩa. |
| BR-059-3 | Người dùng có thể tra theo bộ thủ, theo số nét hoặc kết hợp cả hai điều kiện. |
| BR-059-4 | Số nét nhập vào phải nằm trong khoảng hợp lệ của dữ liệu chữ Hán mà hệ thống hỗ trợ. |
| BR-059-5 | Nếu không có chữ nào khớp chính xác, hệ thống có thể gợi ý nới điều kiện, ví dụ tìm chênh lệch một nét. |
| BR-059-6 | Khi người dùng chọn một chữ trong kết quả, hệ thống mở màn hình chi tiết chữ giống luồng tra chữ Hán. |

## API · DB

```
GET /api/public/dictionary/radicals
GET /api/public/dictionary/by-radical?radical={r}&strokes={n}
```

`characters` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bộ 氵, 7 nét | Chữ khớp cả hai |
| T2 | Chỉ bộ 氵 | Hết chữ bộ đó, nhóm theo nét |
| T3 | Số nét 50 | 400 |
| T4 | Bộ không tồn tại | 404 |
| T5 | Đếm chữ thiếu bộ thủ | Báo tỉ lệ để quyết cắt hay làm |

---

# UC-060 · Dịch câu hoặc đoạn văn Trung ↔ Việt

| | |
|---|---|
| **UC-ID** | UC-060 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Dịch câu/đoạn hai chiều, kết quả **kèm pinyin và tách từ** để bấm từng từ (UC-061).
**Tốn tiền mỗi lượt** — gọi API ngoài. Nghiệm thu: câu 20 từ dưới 3 giây.

## Tiền điều kiện

1. `USER` **đã đăng nhập** — `GUEST` không dịch được (tốn tiền)
2. Còn lượt hoặc còn điểm — ⚠️ chờ `TODO(PAYMENT_SCOPE)`
3. Đoạn ≤ 2.000 ký tự
4. API dịch khả dụng

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Thành công | `translation_history` ghi dòng; trừ lượt; cache Redis `learn:translate:*` |
| Cache hit | **Không trừ lượt** — không gọi API thì không tốn tiền |
| Hết lượt | 402, không gọi API, **không trừ gì** |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Dán đoạn văn, chọn hướng dịch |
| 2 | Client | `POST /api/translate` — `{text, from, to}` |
| 3 | System | Kiểm độ dài ≤ 2.000 |
| 4 | System | Tính hash của `text` → kiểm cache `learn:translate:{hash}` |
| 5 | System | **Cache hit** → trả ngay, **không trừ lượt** |
| 6 | System | Cache miss → **kiểm quota** |
| 7 | System | **Transaction:** trừ lượt + ghi `feature_usage` |
| 8 | System | Gọi API dịch (timeout 10s) |
| 9 | System | Tách từ kết quả + gắn pinyin |
| 10 | System | Ghi `translation_history`, ghi cache TTL 7 ngày |
| 11 | System | Trả `{translated, pinyin, segments[]}` |

## Luồng thay thế

**A1 — Cache hit**
Trả ngay, không trừ lượt. Nhiều người dịch cùng câu phổ biến thì chỉ lần đầu tốn tiền.

**A2 — API dịch lỗi sau khi đã trừ lượt**
🔴 **Hoàn lượt ngay** (UC-097). Xem exception.

**A3 — Đoạn quá dài**
400 kèm số ký tự thực tế, gợi ý chia nhỏ.

**A4 — Tự nhận hướng dịch**
Text toàn ký tự Hán → Trung→Việt; toàn Latin → Việt→Trung.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `TEXT_TOO_LONG` | 400 | > 2.000 ký tự | Chặn **trước** khi trừ lượt |
| `EMPTY_TEXT` | 400 | Rỗng | Chặn |
| `QUOTA_EXCEEDED` | 402 | Hết lượt và điểm | Gợi ý nạp. **Không gọi API** |
| `QUOTA_DEDUCTED_BUT_API_FAILED` | 500 | 🔴 Trừ lượt xong API lỗi | Xem ghi chú |
| `TRANSLATE_API_TIMEOUT` | 504 | Quá 10s | **Hoàn lượt**, cho thử lại |
| `TRANSLATE_API_RATE_LIMITED` | 503 | Nhà cung cấp chặn | **Hoàn lượt**, hiện "thử lại sau" |
| `TRANSLATE_API_KEY_INVALID` | 500 | Key sai/hết hạn | 🔴 **Hoàn lượt cho mọi request đang lỗi.** Báo admin ngay |
| `GUEST_NOT_ALLOWED` | 401 | `GUEST` gọi | Chặn — dịch tốn tiền |
| `SEGMENTATION_FAILED` | 200 | Tách từ lỗi | Vẫn trả bản dịch, `segments` rỗng. UC-061 không dùng được nhưng bản dịch vẫn có giá trị |
| `SLOW_RESPONSE` | — | Quá 3 giây | ⚠️ Vi phạm nghiệm thu — cần đo và ghi log |
| `PII_IN_TRANSLATION_HISTORY` | — | Người học dán dữ liệu riêng tư | 🔴 Xem ghi chú |

> 🔴 **`QUOTA_DEDUCTED_BUT_API_FAILED` là lỗi mất tiền, giống UC-048.** Nhưng ở đây **tệ hơn**
> vì tần suất dùng cao hơn nhiều — dịch là tính năng dùng hàng ngày, sinh bài AI thì thỉnh
> thoảng.
> **Thứ tự đúng:** trừ lượt (transaction A, commit) → gọi API → lỗi thì **hoàn lượt**
> (transaction B, ghi `credit_transactions` loại `REFUND`). Không gộp API call vào transaction
> vì gọi mạng trong transaction giữ khoá DB 10 giây.
> **Bắt buộc:** hoàn lượt phải ghi `credit_transactions` với `balance_before`/`balance_after`
> để sổ cái khớp (quy tắc 6 của bảng dính tiền).

> 🔴 **`PII_IN_TRANSLATION_HISTORY` là rủi ro riêng tư ít ai nghĩ.** `translation_history` lưu
> **nguyên văn** những gì người học dán vào. Người ta dán email công việc, tin nhắn, giấy tờ.
> Bảng đó thành kho dữ liệu riêng tư.
> **Cần chốt:** lưu lịch sử bao lâu (đề xuất 30 ngày rồi xoá), ai xem được (chỉ chủ tài khoản —
> mục B IDOR), và **không** ghi nội dung vào log.

> ⚠️ **`SLOW_RESPONSE`:** nghiệm thu nói "câu 20 từ dưới 3 giây". API ngoài không kiểm soát
> được. Cache giúp lần thứ hai, nhưng lần đầu phụ thuộc nhà cung cấp. Cần đo thật và ghi nhận
> nếu không đạt, thay vì bỏ qua tiêu chí.

## Business rule

| # | Rule |
| --- | --- |
| BR-060-1 | Chỉ `USER` đã đăng nhập mới được dùng chức năng dịch. `GUEST` không được dùng vì dịch gọi API ngoài và có chi phí vận hành. |
| BR-060-2 | Nội dung dịch phải có giới hạn độ dài. Trong MVP, mỗi lần dịch tối đa 2.000 ký tự. |
| BR-060-3 | Dịch là chức năng có giới hạn lượt sử dụng theo chính sách quota của hệ thống. Nếu payment chưa chốt, MVP chỉ dùng quota miễn phí hoặc giới hạn theo ngày. |
| BR-060-4 | Nếu người dùng đã hết quota, hệ thống không gọi API dịch và không tạo lịch sử dịch mới. |
| BR-060-5 | Nếu bản dịch lấy được từ cache, hệ thống không tính thêm quota vì không gọi API ngoài. |
| BR-060-6 | Nếu API dịch lỗi hoặc timeout, hệ thống phải không trừ quota của người dùng, hoặc phải hoàn quota nếu đã trừ trước đó. |
| BR-060-7 | Kết quả dịch nên kèm pinyin và danh sách từ/cụm đã tách để người học có thể bấm từng từ xem nghĩa. |
| BR-060-8 | Nếu tách từ thất bại, hệ thống vẫn trả bản dịch nếu có. Khi đó chức năng bấm từng từ có thể bị ẩn. |
| BR-060-9 | Lịch sử dịch chỉ người tạo mới được xem. Không được cho người dùng xem lịch sử dịch của người khác. |
| BR-060-10 | Nội dung người dùng gửi để dịch không được ghi vào log hệ thống. Nếu lưu lịch sử dịch, hệ thống phải có thời hạn lưu rõ ràng. MVP đề xuất lưu tối đa 30 ngày. |

## API · DB

```
POST /api/translate
GET  /api/me/translation-history
```

`translation_history` · `feature_usage` · `user_credits` · `credit_transactions` (ghi) · Redis `learn:translate:*`

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Câu 20 từ | 200 kèm pinyin + segments, **< 3 giây** |
| T2 | Dịch lại cùng câu | Cache hit, **lượt không giảm** |
| T3 | 3.000 ký tự | 400, lượt **không** giảm |
| T4 | Hết lượt | 402, **không gọi API** |
| T5 | API timeout | Lượt được **hoàn**, có dòng `REFUND` |
| T6 | `GUEST` gọi | 401 |
| T7 | Kiểm log sau khi dịch | **Không** chứa nội dung dịch |
| T8 | Xem lịch sử người khác | 403 |

---

# UC-061 · Bấm từ trong kết quả dịch xem nghĩa

| | |
|---|---|
| **UC-ID** | UC-061 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Kết quả dịch đã tách từ — bấm từ nào hiện popup nghĩa từ đó. Giống UC-031 (phụ đề video) nhưng
nguồn là kết quả dịch.

## Tiền điều kiện

1. Đã có kết quả dịch với `segments` không rỗng
2. Từ có trong `words`/`characters`

## Hậu điều kiện

Không đổi dữ liệu. **Không tính lượt** — tra từ điển nội bộ, không gọi API ngoài.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm một từ trong kết quả dịch |
| 2 | Client | Lấy từ `segments[i]` |
| 3 | Client | `GET /api/dictionary/lookup?q={word}` (UC-056) |
| 4 | Client | Hiện popup: chữ, pinyin, nghĩa, nút lưu (UC-062/063) |
| 5 | `USER` | Đọc hoặc lưu, đóng popup |

## Luồng thay thế

**A1 — Từ không có trong từ điển**
Hiện "chưa có trong từ điển", cho báo lỗi để `CONTENT_ADMIN` bổ sung.
**A2 — Tách từ rỗng** (`SEGMENTATION_FAILED` ở UC-060) — không bấm được, ẩn tính năng.
**A3 — Chọn nhiều ký tự bằng kéo chuột** — bù cho lỗi tách từ, tra cụm đã chọn.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NO_SEGMENTS` | — | `segments` rỗng | Ẩn tính năng (A2) |
| `WORD_NOT_IN_DICTIONARY` | 404 | Từ lạ | Cho báo lỗi (A1) |
| `AMBIGUOUS_SEGMENTATION` | — | Tách sai ranh giới | 🔴 **Cùng vấn đề UC-031** — hiện nghĩa sai |
| `QUOTA_CHARGED_WRONGLY` | — | Tính lượt cho tra từ điển | 🔴 Xem ghi chú |
| `DICTIONARY_LOOKUP_FAILED` | 500 | Lỗi server | Hiện "không tra được" |

> 🔴 **`QUOTA_CHARGED_WRONGLY` — ranh giới tính phí phải rõ.** Tính năng 4.1 gộp "tra cứu **và**
> dịch". Chỉ **dịch** tốn tiền (gọi API ngoài); **tra từ điển** đọc DB nội bộ, không tốn gì.
> Nếu code tính lượt cho cả 4.1 thì bấm 10 từ trong một bản dịch mất 10 lượt — người học thấy
> điểm tụt mà không hiểu vì sao.
> **Cần chốt rõ trong `QuotaService`:** chỉ `TRANSLATE` tính lượt, `DICTIONARY_LOOKUP` không.
> Đây cũng là câu hỏi còn treo ở UC-031 (khoảng trống #11 nhóm 1).

## Business rule

| # | Rule |
| --- | --- |
| BR-061-1 | Người dùng có thể bấm vào từ hoặc cụm từ trong kết quả dịch để xem nghĩa từ điển. |
| BR-061-2 | Việc bấm từ trong kết quả dịch chỉ tra dữ liệu từ điển nội bộ, không gọi API dịch và không tính quota dịch. |
| BR-061-3 | Chức năng bấm từ chỉ hiển thị khi kết quả dịch có dữ liệu tách từ. Nếu không có dữ liệu tách từ, hệ thống ẩn chức năng này. |
| BR-061-4 | Nếu từ được bấm không có trong từ điển, hệ thống hiển thị thông báo chưa có dữ liệu và có thể cho người học báo thiếu dữ liệu. |
| BR-061-5 | Người dùng được phép chọn thủ công một cụm chữ để tra nếu hệ thống tách từ chưa đúng. |
| BR-061-6 | Kết quả popup tra từ có thể cho `USER` lưu vào sổ tay hoặc flashcard. |

## API · DB

```
GET /api/dictionary/lookup?q={word}
```

`words` · `characters` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Bấm một từ | Popup nghĩa |
| T2 | Bấm 10 từ liên tiếp | **Lượt không giảm** |
| T3 | Từ không trong từ điển | 404 + nút báo lỗi |
| T4 | `segments` rỗng | Không bấm được |
| T5 | Kéo chọn 3 ký tự | Tra cụm đã chọn |

---

# UC-062 · Thêm từ từ kết quả tra vào sổ tay

| | |
|---|---|
| **UC-ID** | UC-062 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Từ popup tra cứu, lưu từ vào `notes` kèm nghĩa và ngữ cảnh. Giống UC-032 nhưng nguồn là tra
từ điển, không phải phụ đề video.

## Tiền điều kiện

`USER` đã đăng nhập; đang xem kết quả tra.

## Hậu điều kiện

`notes` thêm dòng có `title`, `content`, `tags`, nguồn.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Lưu vào sổ tay" |
| 2 | Client | Mở form: tiêu đề mặc định là chữ, nội dung mặc định là nghĩa + pinyin |
| 3 | `USER` | Sửa nếu muốn, thêm thẻ |
| 4 | Client | `POST /api/notes` |
| 5 | System | Kiểm giới hạn số ghi chú |
| 6 | System | Ghi `notes` với `user_id` từ token |
| 7 | Client | Hiện "đã lưu" |

## Luồng thay thế

**A1 — Đã có ghi chú cho từ này**
Không chặn — sổ tay là "viết gì cũng được", trùng là bình thường. Nhưng cảnh báo nhẹ.
**A2 — Lưu nhanh không mở form** — dùng mặc định hết.
**A3 — Lưu cả vào flashcard** — gọi thêm UC-063.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NOTE_LIMIT_EXCEEDED` | 422 | > 1.000 ghi chú | Chặn spam |
| `CONTENT_TOO_LONG` | 400 | > 10.000 ký tự | Chặn |
| `EMPTY_CONTENT` | 400 | Nội dung rỗng | Chặn — ghi chú rỗng vô nghĩa |
| `USER_ID_FROM_CLIENT` | 400 | Client gửi `user_id` | 🔴 **Bỏ qua, luôn lấy từ token** |
| `XSS_IN_CONTENT` | — | Nội dung có `<script>` | 🔴 Xem ghi chú |
| `TOO_MANY_TAGS` | 400 | > 10 thẻ | Chặn |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập | `GUEST` không lưu được |

> 🔴 **`XSS_IN_CONTENT` — ghi chú là nội dung người dùng tự do nhập, hiện lại trên web.**
> Nếu render bằng `innerHTML` hoặc `dangerouslySetInnerHTML` thì `<script>` trong ghi chú chạy
> khi người học mở lại. Vì sổ tay chỉ mình xem nên tự-XSS ít nguy hiểm — **nhưng** UC-068 cho
> chia sẻ flashcard, và UC-065 tìm kiếm có thể hiện đoạn trích. Đường lây có thật.
> **Cần:** escape khi render (React mặc định đã escape), hoặc sanitize nếu cho phép markdown.

> ⚠️ **`USER_ID_FROM_CLIENT`:** endpoint không nhận `user_id`. Nếu nhận và tin, người dùng ghi
> chú vào tài khoản người khác. Luôn lấy từ token — áp dụng cho **mọi** endpoint có `user_id`.

## Business rule

| # | Rule |
| --- | --- |
| BR-062-1 | Chỉ `USER` đã đăng nhập mới được lưu từ vào sổ tay. `GUEST` không lưu được. |
| BR-062-2 | Khi tạo ghi chú từ kết quả tra, `user_id` luôn lấy từ phiên đăng nhập hiện tại. Client không được quyết định ghi chú thuộc về ai. |
| BR-062-3 | Ghi chú được tạo từ kết quả tra có thể dùng sẵn chữ/từ, pinyin, nghĩa và ngữ cảnh làm nội dung mặc định. Người học được sửa lại trước khi lưu. |
| BR-062-4 | Sổ tay cho phép ghi chú trùng từ vì đây là nơi ghi chép tự do của người học. Hệ thống có thể cảnh báo nhẹ nhưng không chặn. |
| BR-062-5 | Mỗi người học có giới hạn số ghi chú để tránh spam dữ liệu. MVP đề xuất tối đa 1.000 ghi chú mỗi người. |
| BR-062-6 | Nội dung ghi chú phải có giới hạn độ dài và không được để trống. MVP đề xuất tối đa 10.000 ký tự và tối đa 10 thẻ. |
| BR-062-7 | Nội dung ghi chú là nội dung người dùng nhập, vì vậy khi hiển thị lại phải được escape hoặc sanitize để tránh XSS. |

## API · DB

```
POST /api/notes
```

`notes` (ghi) · `words` · `characters` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Lưu từ 好 | `notes` thêm dòng, `user_id` đúng |
| T2 | Gửi kèm `user_id` khác | Bị bỏ qua |
| T3 | Nội dung rỗng | 400 |
| T4 | Đã 1.000 ghi chú | 422 |
| T5 | Nội dung có `<script>` | Render ra text, **không** chạy |
| T6 | `GUEST` | 401 |

---

# UC-063 · Thêm từ từ kết quả tra vào flashcard

| | |
|---|---|
| **UC-ID** | UC-063 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 4.1 |

## Mô tả

Từ popup tra cứu, thêm thẻ vào một bộ flashcard. Mặt trước chữ, mặt sau nghĩa + pinyin.

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có bộ flashcard đích (hoặc tạo mới — UC-067)

## Hậu điều kiện

`flashcards` thêm dòng có `next_review_at` khởi tạo.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Thêm flashcard" |
| 2 | Client | Hiện danh sách bộ của mình + nút tạo mới |
| 3 | `USER` | Chọn bộ |
| 4 | Client | `POST /api/flashcard-decks/{id}/cards` |
| 5 | System | **Kiểm sở hữu bộ** |
| 6 | System | Kiểm trùng thẻ trong bộ |
| 7 | System | Kiểm giới hạn số thẻ |
| 8 | System | Ghi `flashcards`, `next_review_at = now()` (ôn ngay lần đầu) |
| 9 | Client | Hiện "đã thêm vào bộ X" |

## Luồng thay thế

**A1 — Chưa có bộ nào** — tạo bộ + thêm thẻ trong một thao tác (gọi UC-067 rồi UC-063).
**A2 — Thẻ đã có trong bộ** — 409, cho chọn bộ khác.
**A3 — Thêm vào nhiều bộ** — cho phép; mỗi bộ một dòng `flashcards` riêng.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `DECK_NOT_OWNED` | 403 | 🔴 Bộ của người khác | **IDOR** — cùng lỗ hổng UC-032 |
| `DECK_NOT_FOUND` | 404 | ID sai | Chọn bộ khác |
| `CARD_ALREADY_IN_DECK` | 409 | Thẻ trùng | Không tạo trùng (A2) |
| `DECK_LIMIT_EXCEEDED` | 422 | Bộ > 500 thẻ | Gợi ý tạo bộ mới |
| `TOO_MANY_DECKS` | 422 | > 50 bộ | Chặn spam |
| `WORD_NOT_FOUND` | 404 | Từ không trong từ điển | Không thêm thẻ rỗng nghĩa |
| `PUBLIC_DECK_MODIFIED` | 403 | Thêm thẻ vào bộ **đã chia sẻ** của mình | ⚠️ Xem ghi chú |
| `UNAUTHORIZED` | 401 | Chưa đăng nhập | Chặn |

> 🔴 **`DECK_NOT_OWNED` là IDOR thứ ba trong tài liệu** (sau UC-029 `attempts` và UC-032).
> Ba chỗ cùng một mẫu lỗi: nhận ID tài nguyên từ URL, kiểm "đã đăng nhập" mà quên kiểm "của ai".
> Đây là bằng chứng cụ thể cho thấy mục B (quyền sở hữu / IDOR) — nay là `BUS-01` trong Hiến pháp
> (quyền sở hữu / chống IDOR) **phải chốt thành `OwnershipService` dùng chung**, không kiểm rời rạc.

> ⚠️ **`PUBLIC_DECK_MODIFIED` là quyết định nghiệp vụ chưa chốt.** Bộ đã chia sẻ (UC-068) và có
> người sao chép về. Nếu chủ bộ thêm/xoá thẻ sau đó:
> — Bản đã sao chép có cập nhật theo? (đề xuất: **không** — sao chép là bản độc lập)
> — Bộ công khai có cho sửa? (đề xuất: cho, nhưng người sao sau thấy bản mới)
> Chốt "sao chép là bản độc lập" là đơn giản nhất và tránh mọi rắc rối đồng bộ.

## Business rule

| # | Rule |
| --- | --- |
| BR-063-1 | Chỉ `USER` đã đăng nhập mới được thêm từ vào flashcard. |
| BR-063-2 | Người học chỉ được thêm thẻ vào bộ flashcard thuộc sở hữu của chính mình. Việc kiểm tra sở hữu phải thực hiện ở server. |
| BR-063-3 | Một bộ flashcard không được có hai thẻ trùng cùng một từ hoặc cùng một nội dung mặt trước. |
| BR-063-4 | Một từ có thể được thêm vào nhiều bộ flashcard khác nhau của cùng người học. |
| BR-063-5 | Khi thêm thẻ mới, hệ thống khởi tạo lịch ôn để thẻ có thể xuất hiện ngay trong lần ôn đầu tiên. |
| BR-063-6 | Mỗi người học có tối đa 50 bộ flashcard; mỗi bộ có tối đa 500 thẻ trong MVP. |
| BR-063-7 | Bản sao của một bộ flashcard là bản độc lập. Sau khi sao chép, thay đổi ở bộ gốc không tự động cập nhật sang bản sao. |

## API · DB

```
POST /api/flashcard-decks/{id}/cards
GET  /api/flashcard-decks              (bộ của mình)
```

`flashcard_decks` · `flashcards` (đọc + ghi) · `words` · `characters` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Thêm vào bộ của mình | 201, `next_review_at = now()` |
| T2 | Bộ của người khác | 403 `DECK_NOT_OWNED` |
| T3 | Thẻ đã có | 409 |
| T4 | Bộ đã 500 thẻ | 422 |
| T5 | Thêm cùng từ vào 2 bộ | Cả hai thành công |
| T6 | `GUEST` | 401 |

---

# UC-064 · Tạo, sửa, xóa ghi chú cá nhân

| | |
|---|---|
| **UC-ID** | UC-064 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 4.2 |

## Mô tả

Sổ tay **thuần ghi chú** — viết gì cũng được: mẹo nhớ chữ, câu mẫu, lỗi hay sai, tóm tắt bài.
Tổ chức bằng tiêu đề và thẻ.

> **Thay đổi so với bản 1:** **bỏ liên kết vào học** — không còn bảng `note_links`, từ trong
> ghi chú **không** tự vào lịch ôn. Đơn giản hơn.

## Tiền điều kiện

`USER` đã đăng nhập.

## Hậu điều kiện

| Thao tác | Trạng thái |
| --- | --- |
| Tạo | `notes` thêm dòng |
| Sửa | Cập nhật `updated_at` |
| Xoá | Xoá dòng (hoặc soft delete — cần chốt) |

## Luồng chính — Tạo

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Ghi chú mới" |
| 2 | `USER` | Nhập tiêu đề, nội dung, thẻ |
| 3 | Client | `POST /api/notes` |
| 4 | System | Validate + kiểm giới hạn |
| 5 | System | Ghi với `user_id` từ token |

## Luồng chính — Sửa

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở ghi chú, sửa |
| 2 | Client | `PUT /api/notes/{id}` |
| 3 | System | **Kiểm sở hữu** |
| 4 | System | Cập nhật, ghi `updated_at` |

## Luồng chính — Xoá

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm xoá, xác nhận |
| 2 | Client | `DELETE /api/notes/{id}` |
| 3 | System | **Kiểm sở hữu** |
| 4 | System | Xoá |

## Luồng thay thế

**A1 — Sửa đồng thời từ hai thiết bị**
Bản lưu sau ghi đè. Hoặc dùng `version` để báo xung đột — cần chốt.

**A2 — Xoá nhầm**
Không hoàn tác được nếu hard delete. Đề xuất soft delete + thùng rác 30 ngày.

**A3 — Lọc theo thẻ**
`GET /api/notes?tag=ngu-phap`.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `NOTE_NOT_OWNED` | 403 | 🔴 Ghi chú người khác | **IDOR** — chính ví dụ mục B đã ghi: `GET /api/notes/123` |
| `NOTE_NOT_FOUND` | 404 | ID sai | Về danh sách |
| `NOTE_LIMIT_EXCEEDED` | 422 | > 1.000 | Chặn |
| `CONTENT_TOO_LONG` | 400 | > 10.000 ký tự | Chặn |
| `EMPTY_CONTENT` | 400 | Rỗng | Chặn |
| `CONCURRENT_EDIT` | 409 | Sửa đồng thời | ⚠️ Cần chốt: ghi đè hay báo xung đột (A1) |
| `DELETE_NOT_RECOVERABLE` | — | Xoá không hoàn tác | ⚠️ Cần chốt soft delete (A2) |
| `XSS_IN_CONTENT` | — | `<script>` | Escape khi render |
| `TOO_MANY_TAGS` | 400 | > 10 thẻ | Chặn |

> 🔴 **`NOTE_NOT_OWNED` là **đúng ví dụ** mục B trong quyết định v2 dùng để minh hoạ IDOR:**
> *"Lỗi hay gặp: `GET /api/notes/123` chỉ kiểm 'có đăng nhập' mà không kiểm 'note này của ai'."*
> Ghi chú là nội dung riêng tư nhất trong hệ thống (mẹo học, lỗi mình hay sai). Lộ ra là lộ
> thông tin cá nhân, không chỉ là lỗi kỹ thuật.
> **Bốn UC đã có IDOR:** UC-029 (`attempts`), UC-032 · UC-063 (`flashcard_decks`), UC-064
> (`notes`). Còn UC-060 (`translation_history`) và UC-093 (`user_credits`).

> ⚠️ **`DELETE_NOT_RECOVERABLE`:** người học viết mẹo nhớ chữ suốt 3 tháng, xoá nhầm là mất
> hết. Soft delete + thùng rác là **rẻ** (một cột `deleted_at` + điều kiện truy vấn) và tránh
> được một loại phàn nàn không thể cứu.

## Business rule

| # | Rule |
| --- | --- |
| BR-064-1 | Ghi chú là dữ liệu cá nhân. Người học chỉ được đọc, sửa và xóa ghi chú của chính mình. |
| BR-064-2 | `user_id` của ghi chú luôn lấy từ phiên đăng nhập hiện tại, không lấy từ request body hoặc query parameter. |
| BR-064-3 | Sổ tay là nơi ghi chú tự do. Người học có thể viết mẹo nhớ chữ, câu mẫu, lỗi hay sai hoặc tóm tắt bài học. |
| BR-064-4 | Ghi chú không tự động tạo flashcard, không tự động đưa từ vào lịch ôn và không cần bảng liên kết vào hệ thống học. |
| BR-064-5 | Nội dung ghi chú không được rỗng, phải có giới hạn độ dài và phải được escape hoặc sanitize khi hiển thị. |
| BR-064-6 | MVP dùng soft delete cho ghi chú để tránh mất dữ liệu khi người học xóa nhầm. Ghi chú đã xóa không hiện trong danh sách mặc định. |
| BR-064-7 | Khi sửa đồng thời cùng một ghi chú từ nhiều thiết bị, MVP dùng quy tắc bản lưu sau cùng ghi đè bản trước. Hệ thống cập nhật `updated_at` để người học biết lần sửa cuối. |
| BR-064-8 | Nếu cần xử lý xung đột sửa nâng cao, đưa sang V2; không làm phức tạp MVP. |

## API · DB

```
GET    /api/notes
GET    /api/notes/{id}
POST   /api/notes
PUT    /api/notes/{id}
DELETE /api/notes/{id}
```

`notes` (đọc + ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tạo ghi chú | 201 |
| T2 | `GET /api/notes/{id người khác}` | **403** |
| T3 | `PUT` ghi chú người khác | 403 |
| T4 | `DELETE` ghi chú người khác | 403 |
| T5 | Nội dung rỗng | 400 |
| T6 | Sửa từ 2 thiết bị | Theo luật đã chốt |
| T7 | `<script>` trong nội dung | Render ra text |

---

# UC-065 · Tìm kiếm trong ghi chú

| | |
|---|---|
| **UC-ID** | UC-065 · **Actor** `USER` · **Pri** P2 · **Scope** MVP · **FT** 4.2 |

## Mô tả

Tìm theo nội dung ghi chú, tiêu đề, hoặc thẻ. Nghiệm thu 4.2: "viết, sửa, xoá, **tìm kiếm**".

## Tiền điều kiện

`USER` đã đăng nhập, có ≥ 1 ghi chú.

## Hậu điều kiện

Chỉ đọc.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Nhập từ khoá |
| 2 | Client | `GET /api/notes/search?q={x}` |
| 3 | System | **Chỉ tìm trong ghi chú của người đang đăng nhập** |
| 4 | System | Tìm trong `title` và `content` |
| 5 | System | Sắp theo độ khớp rồi `updated_at` |
| 6 | System | Trả kèm đoạn trích có từ khoá được đánh dấu |

## Luồng thay thế

**A1 — Tìm theo thẻ** — `?tag=x`, khớp chính xác thẻ.
**A2 — Không có kết quả** — hiện "không tìm thấy", gợi ý xem hết.
**A3 — Từ khoá tiếng Trung** — tìm được vì `content` lưu Unicode.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `SEARCHED_OTHER_USERS_NOTES` | — | 🔴 Truy vấn thiếu `user_id` | Xem ghi chú |
| `EMPTY_QUERY` | 400 | Rỗng | Chặn — trả hết thì dùng `GET /api/notes` |
| `NO_RESULTS` | 200 (rỗng) | Không khớp | Không phải lỗi (A2) |
| `SLOW_SEARCH` | — | `LIKE '%x%'` quét toàn bảng | ⚠️ Cần index; nhưng phạm vi hẹp (ghi chú của 1 người ≤ 1.000) nên chấp nhận được |
| `XSS_IN_SNIPPET` | — | Đoạn trích chứa HTML | 🔴 Xem ghi chú |
| `QUERY_TOO_SHORT` | 400 | 1 ký tự | Khớp quá nhiều — yêu cầu ≥ 2 ký tự |

> 🔴 **`SEARCHED_OTHER_USERS_NOTES` là IDOR dạng khác — không cần biết ID.** UC-064 phải biết
> `note_id` mới lấy được ghi chú người khác. Ở đây chỉ cần thiếu `WHERE user_id = :current` là
> tìm kiếm trả về ghi chú của **toàn bộ người dùng**. Nguy hiểm hơn vì **không cần biết gì**,
> chỉ cần gõ từ khoá phổ biến.
> **Đây là loại lỗi phải có test riêng:** tạo ghi chú cho 2 user, tìm bằng từ khoá chung, assert
> chỉ thấy của mình.

> 🔴 **`XSS_IN_SNIPPET`:** đoạn trích thường được đánh dấu từ khoá bằng `<mark>`. Nếu ghép chuỗi
> HTML với nội dung ghi chú chưa escape thì đây là đường XSS thật — và là **lý do** ghi chú phải
> escape từ UC-062/064.

## Business rule

| # | Rule |
| --- | --- |
| BR-065-1 | Tìm kiếm ghi chú chỉ được thực hiện trong ghi chú của người học đang đăng nhập. |
| BR-065-2 | Từ khóa tìm kiếm phải có ít nhất 2 ký tự để tránh trả quá nhiều kết quả không có giá trị. |
| BR-065-3 | Hệ thống tìm trong tiêu đề và nội dung ghi chú. |
| BR-065-4 | Tìm theo thẻ dùng quy tắc khớp chính xác tên thẻ. |
| BR-065-5 | Kết quả tìm kiếm được sắp xếp theo độ khớp trước, sau đó theo thời gian cập nhật gần nhất. |
| BR-065-6 | Đoạn trích kết quả phải được escape trước khi đánh dấu từ khóa để tránh XSS. |
| BR-065-7 | Nếu không có kết quả, hệ thống trả danh sách rỗng và hiển thị thông báo không tìm thấy, không coi là lỗi. |

## API · DB

```
GET /api/notes/search?q={x}
GET /api/notes?tag={t}
```

`notes` (đọc)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tìm "ngữ pháp" | Chỉ ghi chú **của mình** |
| T2 | Hai user có ghi chú chứa "học" | Mỗi người chỉ thấy của mình |
| T3 | Từ khoá 1 ký tự | 400 |
| T4 | Từ khoá tiếng Trung | Tìm được |
| T5 | Ghi chú có `<b>` | Đoạn trích hiện ra text |
| T6 | Không khớp | 200 rỗng |

---

# UC-066 · Ôn flashcard theo lịch

| | |
|---|---|
| **UC-ID** | UC-066 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 4.3 |

## Mô tả

Thẻ đến hạn hiện lên, mặt trước chữ, lật ra nghĩa + pinyin, người học **tự đánh giá** nhớ/quên
→ giãn hoặc rút ngắn ngày ôn.

> **Khác UC-024 (ôn ngữ pháp) ở một điểm quan trọng:** flashcard là **tự đánh giá**, nên
> `rating` **được** nhận từ client. Bài có đáp án thì không (BR-024-4).

## Tiền điều kiện

1. `USER` đã đăng nhập
2. Có `flashcards` với `next_review_at ≤ now()` trong bộ của mình

## Hậu điều kiện

| Kết quả | Trạng thái |
| --- | --- |
| Nhớ | `next_review_at` giãn xa |
| Quên | `next_review_at` gần lại (nghiệm thu: "thẻ quên xuất hiện lại **sớm hơn**") |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Vào "Ôn flashcard" |
| 2 | System | `GET /api/flashcards/due` — thẻ đến hạn **trong bộ của mình** |
| 3 | Client | Hiện mặt trước (chữ) |
| 4 | `USER` | Tự nhớ, bấm "Lật thẻ" |
| 5 | Client | Hiện mặt sau: nghĩa + pinyin + audio |
| 6 | `USER` | Chọn "Nhớ" / "Khó" / "Quên" |
| 7 | Client | `POST /api/flashcards/{id}/review` — `{rating}` |
| 8 | System | **Kiểm sở hữu thẻ** |
| 9 | System | Tính `next_review_at` bằng FSRS (UC-044) |
| 10 | System | Cập nhật thẻ |
| 11 | | Lặp 3–10 hết thẻ đến hạn |

## Luồng thay thế

**A1 — Không có thẻ đến hạn** — hiện "hôm nay không có thẻ nào cần ôn".
**A2 — Ôn một bộ cụ thể** — `?deck_id=x`, kiểm sở hữu bộ.
**A3 — Quá nhiều thẻ đến hạn** — giới hạn 100 thẻ/buổi.
**A4 — Ôn trước hạn** — cho phép, FSRS giãn ít hơn.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `CARD_NOT_OWNED` | 403 | Thẻ trong bộ người khác | IDOR |
| `DECK_NOT_OWNED` | 403 | Bộ của người khác (A2) | IDOR |
| `CARD_NOT_FOUND` | 404 | ID sai | Bỏ thẻ |
| `NOTHING_DUE` | 200 (rỗng) | Không thẻ đến hạn | Không phải lỗi (A1) |
| `INVALID_RATING` | 400 | `rating` ngoài `{Again, Hard, Good, Easy}` | Chặn |
| `RATING_ABUSE` | — | Luôn chọn "Nhớ" để không phải ôn | ⚠️ Xem ghi chú |
| `STALE_REVIEW` | 409 | Thẻ vừa được ôn ở tab khác | Bỏ qua lượt này |
| `TOO_MANY_REVIEWS` | 429 | > 500 lượt/giờ | Chặn bot |
| `FSRS_CALCULATION_FAILED` | 500 | Lỗi | Rollback, giữ `next_review_at` cũ |

> ⚠️ **`RATING_ABUSE` — ở đây **không phải** lỗ hổng, khác với UC-024.**
> UC-024 (`CLIENT_SENT_RATING_DIRECTLY`) là lỗ hổng vì bài **có đáp án đúng** — server biết
> đúng/sai, nhận `rating` từ client là bỏ qua sự thật.
> Flashcard **không có đáp án để chấm** — chỉ người học biết mình nhớ hay không. Luôn chọn
> "Nhớ" thì chỉ tự hại mình, và mastery flashcard **không** nên đổ vào `user_knowledge_state`
> với trọng số cao vì lý do đó.
> **Cần chốt:** flashcard có ảnh hưởng mastery chung không, hay chỉ có lịch ôn riêng? Đề xuất:
> chỉ lịch riêng, trọng số mastery rất thấp hoặc 0.

## Business rule

| # | Rule |
| --- | --- |
| BR-066-1 | Người học chỉ được ôn flashcard thuộc các bộ của chính mình. |
| BR-066-2 | Flashcard dùng cơ chế tự đánh giá. Người học được gửi rating như “Nhớ”, “Khó” hoặc “Quên” vì hệ thống không có đáp án để tự chấm. |
| BR-066-3 | Nếu người học chọn “Quên”, thẻ phải được xếp lịch ôn lại sớm hơn. |
| BR-066-4 | Nếu người học chọn “Nhớ”, thẻ được giãn lịch ôn xa hơn theo thuật toán ôn lặp. |
| BR-066-5 | Người học được phép ôn thẻ trước hạn, nhưng lịch ôn sau đó không được giãn mạnh như khi ôn đúng hạn. |
| BR-066-6 | Mỗi buổi ôn chỉ trả một số lượng thẻ giới hạn để tránh quá tải. MVP đề xuất tối đa 100 thẻ mỗi buổi. |
| BR-066-7 | Trong MVP, rating flashcard chỉ ảnh hưởng lịch ôn của flashcard, không cập nhật mastery chung trong `user_knowledge_state`. |

## API · DB

```
GET  /api/flashcards/due
GET  /api/flashcards/due?deck_id={d}
POST /api/flashcards/{id}/review
```

`flashcards` · `flashcard_decks` (đọc + ghi)

> **Index cần:** `flashcards(deck_id, next_review_at)`.

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | 10 thẻ đến hạn, chọn "Nhớ" | `next_review_at` giãn |
| T2 | Chọn "Quên" | Xuất hiện lại **sớm hơn** |
| T3 | Ôn thẻ người khác | 403 |
| T4 | `deck_id` người khác | 403 |
| T5 | Không thẻ đến hạn | 200 rỗng |
| T6 | 200 thẻ đến hạn | Trả 100 |
| T7 | `rating` lạ | 400 |

---

# UC-067 · Tạo bộ flashcard mới

| | |
|---|---|
| **UC-ID** | UC-067 · **Actor** `USER` · **Pri** P1 · **Scope** MVP · **FT** 4.3 |

## Mô tả

Tạo bộ thẻ mới. Nguồn thẻ: tự tạo · bộ dựng sẵn theo cấp HSK · bộ người khác chia sẻ (V2).

## Tiền điều kiện

`USER` đã đăng nhập; chưa vượt 50 bộ.

## Hậu điều kiện

`flashcard_decks` thêm dòng, `is_public = false`.

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Bấm "Tạo bộ mới" |
| 2 | `USER` | Nhập tên, mô tả; chọn nguồn (trống / HSK dựng sẵn / sao chép) |
| 3 | Client | `POST /api/flashcard-decks` |
| 4 | System | Kiểm giới hạn số bộ, kiểm tên không trùng trong bộ của mình |
| 5 | System | Ghi `flashcard_decks` với `is_public = false` |
| 6 | System | Nguồn = HSK dựng sẵn → copy thẻ từ mẫu |
| 7 | System | Nguồn = sao chép → gọi luồng UC-068 A |
| 8 | Client | Mở bộ vừa tạo |

## Luồng thay thế

**A1 — Bộ dựng sẵn HSK1** — copy toàn bộ từ HSK1 vào bộ mới, mỗi thẻ `next_review_at = now()`.
**A2 — Sao chép bộ người khác đã chia sẻ** (V2) — copy thẻ, **reset lịch ôn** về `now()`.
**A3 — Tên trùng** — cho phép nhưng cảnh báo, hoặc tự thêm "(2)".

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `TOO_MANY_DECKS` | 422 | > 50 bộ | Chặn |
| `EMPTY_DECK_NAME` | 400 | Tên rỗng | Chặn |
| `NAME_TOO_LONG` | 400 | > 100 ký tự | Chặn |
| `SOURCE_DECK_NOT_PUBLIC` | 403 | Sao chép bộ chưa chia sẻ | 🔴 **Chặn** — `is_public = false` là riêng tư |
| `SOURCE_DECK_NOT_FOUND` | 404 | ID nguồn sai | Chặn |
| `PRESET_NOT_FOUND` | 404 | Mẫu HSK không có | Tạo bộ trống, báo mẫu chưa sẵn |
| `COPY_TOO_LARGE` | 422 | Bộ nguồn > 500 thẻ | Chặn — vượt giới hạn bộ mới |
| `COPIED_REVIEW_SCHEDULE` | — | 🔴 Copy cả `next_review_at` của người khác | Xem ghi chú |
| `IS_PUBLIC_FROM_CLIENT` | 400 | Client gửi `is_public = true` | Bỏ qua — chia sẻ là UC-068 riêng |

> 🔴 **`COPIED_REVIEW_SCHEDULE` — lỗi tinh vi khi sao chép bộ.** Copy thẻ mà copy luôn
> `next_review_at`, `stability`, `difficulty` của người tạo gốc thì người sao chép nhận lịch ôn
> của **người khác**: thẻ người ta đã học 3 tháng sẽ có `next_review_at` sau 60 ngày → người
> mới sao chép về **không thấy thẻ đó xuất hiện** suốt 2 tháng, dù chưa học bao giờ.
> **Bắt buộc:** copy **chỉ nội dung** (mặt trước, mặt sau), reset lịch ôn về trạng thái mới.

> 🔴 **`SOURCE_DECK_NOT_PUBLIC`:** `is_public` đọc **thẳng** trên `flashcard_decks` (không còn
> bảng `shared_decks`). Nghĩa là mọi endpoint liên quan đến bộ của người khác **phải** kiểm cột
> đó. Quên một chỗ là sao chép được bộ riêng tư của người khác.

## Business rule

| # | Rule |
| --- | --- |
| BR-067-1 | Người học đã đăng nhập được tạo bộ flashcard cá nhân. |
| BR-067-2 | Mỗi người học có tối đa 50 bộ flashcard trong MVP. |
| BR-067-3 | Mỗi bộ flashcard có tối đa 500 thẻ trong MVP. |
| BR-067-4 | Bộ flashcard mới luôn ở trạng thái riêng tư. Client không được tự tạo bộ công khai. |
| BR-067-5 | Trạng thái công khai của bộ chỉ được thay đổi qua chức năng chia sẻ bộ flashcard. |
| BR-067-6 | Nếu tạo bộ từ mẫu HSK dựng sẵn, hệ thống sao chép nội dung thẻ từ mẫu và khởi tạo lịch ôn như thẻ mới. |
| BR-067-7 | Nếu sao chép bộ công khai của người khác, hệ thống chỉ sao chép nội dung thẻ, không sao chép lịch ôn, độ ổn định, độ khó hoặc tiến độ của người tạo gốc. |
| BR-067-8 | Bản sao của bộ flashcard là độc lập với bộ gốc. |

## API · DB

```
POST /api/flashcard-decks
POST /api/flashcard-decks/{id}/copy      (V2)
```

`flashcard_decks` · `flashcards` (đọc + ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Tạo bộ trống | 201, `is_public = false` |
| T2 | Gửi `is_public = true` | Bị bỏ qua, vẫn `false` |
| T3 | Bộ dựng sẵn HSK1 | Thẻ copy, `next_review_at = now()` |
| T4 | Sao chép bộ `is_public = false` | 403 |
| T5 | Sao chép bộ công khai | Thẻ copy, lịch ôn **reset** |
| T6 | Đã 50 bộ | 422 |
| T7 | Tên rỗng | 400 |

---

# UC-068 · Chia sẻ bộ flashcard lên cộng đồng

| | |
|---|---|
| **UC-ID** | UC-068 · **Actor** `USER` · **Pri** P3 · **Scope** **V2** · **FT** 4.3 |

## Mô tả

Đánh dấu bộ là công khai → hiện trên cộng đồng → người khác sao chép về dùng.
`is_public` đọc thẳng trên `flashcard_decks`, **không còn bảng `shared_decks`**.

> ⚠️ **P3 + V2 — đứng gần đầu danh sách cắt** nếu chậm tiến độ.

## Tiền điều kiện

1. `USER` đã đăng nhập, là **chủ** bộ
2. Bộ có ≥ 5 thẻ (bộ 1 thẻ chia sẻ vô nghĩa)
3. Bộ không chứa nội dung vi phạm

## Hậu điều kiện

| Kết quả | Trạng thái |
|---|---|
| Chia sẻ | `flashcard_decks.is_public = true`, `shared_at` |
| Thu hồi | `is_public = false`; **bản đã sao chép vẫn giữ** |

## Luồng chính

| # | Actor | Hành động |
| --- | --- | --- |
| 1 | `USER` | Mở bộ, bấm "Chia sẻ lên cộng đồng" |
| 2 | System | **Kiểm sở hữu** |
| 3 | System | Kiểm ≥ 5 thẻ |
| 4 | System | ⚠️ Kiểm nội dung (kiểm duyệt — xem exception) |
| 5 | Client | `PATCH /api/flashcard-decks/{id}/share` |
| 6 | System | `is_public = true`, ghi `shared_at` |
| 7 | System | Bộ hiện ở danh sách công khai |
| 8 | Client | Hiện link chia sẻ |

## Luồng thay thế

**A1 — Thu hồi chia sẻ**
`is_public = false`. Bộ ẩn khỏi danh sách công khai. **Bản người khác đã sao chép vẫn còn** —
đó là bản độc lập (BR-063-6).

**A2 — Sửa bộ sau khi chia sẻ**
Cho phép. Người sao chép **sau** thấy bản mới; người sao chép **trước** giữ bản cũ.

**A3 — Xem danh sách bộ công khai**
`GET /api/community/flashcard-decks` — `GUEST` xem được danh sách, cần đăng nhập để sao chép.

## Bảng exception

| Mã lỗi | HTTP | Nguyên nhân | Xử lý |
| --- | --- | --- | --- |
| `DECK_NOT_OWNED` | 403 | Chia sẻ bộ người khác | IDOR |
| `TOO_FEW_CARDS` | 422 | < 5 thẻ | Chặn |
| `NO_MODERATION_FOR_DECKS` | — | 🔴 Chưa có luồng kiểm duyệt bộ thẻ | Xem ghi chú |
| `INAPPROPRIATE_CONTENT` | 422 | Nội dung vi phạm | Chặn, ghi `moderation_reports` |
| `COPYRIGHT_CONTENT` | 422 | 🔴 Bộ chứa dữ liệu đề thi của thầy | Xem ghi chú |
| `PII_IN_DECK` | 422 | Thẻ chứa thông tin cá nhân (người học ghi chú riêng vào thẻ) | Chặn hoặc cảnh báo |
| `ALREADY_PUBLIC` | 409 | Đã chia sẻ | Bỏ qua im lặng |
| `XSS_IN_CARD_CONTENT` | — | 🔴 Thẻ có `<script>`, người khác mở | **Đường lây XSS thật** — escape bắt buộc |
| `CROSS_MODULE_READ` | — | `community` đọc thẳng `learning.flashcard_decks` | 🔴 Xem ghi chú |

> 🔴 **`NO_MODERATION_FOR_DECKS` — khoảng trống rõ ràng.** Tính năng 5.1 (blog) có luồng kiểm
> duyệt đầy đủ: `PENDING_REVIEW` → `MANAGER` duyệt (UC-076). Bộ flashcard chia sẻ **không có
> gì tương đương** — `is_public = true` là công khai ngay.
> **Cần chốt một trong hai:**
> — Bộ thẻ cũng vào hàng đợi `MANAGER` duyệt (nhất quán, nhưng thêm việc)
> — Công khai ngay, dựa vào báo cáo vi phạm sau (đơn giản, rủi ro có nội dung xấu)
> Vì UC này là P3/V2 và dễ cắt, phương án thứ hai hợp lý hơn — nhưng phải có nút báo cáo.

> 🔴 **`COPYRIGHT_CONTENT` là rủi ro pháp lý cụ thể.** Constitution cấm commit dữ liệu đề thi
> HSK của giảng viên (bản quyền). Người học có thể tạo bộ flashcard **chép từ đề thi** rồi chia
> sẻ công khai → dữ liệu bản quyền của thầy lên mạng qua chính hệ thống của nhóm.
> Không thể phát hiện tự động. Cần: điều khoản rõ khi chia sẻ + nút báo cáo + `MANAGER` xoá được.

> 🔴 **`CROSS_MODULE_READ`:** `flashcard_decks` ở schema **`learning`**, nhưng danh sách bộ công
> khai là tính năng **cộng đồng**. Module `community` **không được** đọc thẳng bảng của
> `learning` (AGENTS.md §6, `ModuleBoundaryTest`).
> **Đúng:** `community` gọi `learningApi.getPublicDecks(...)`. Đây là cùng một luật với UC-043.

## Business rule

| # | Rule |
| --- | --- |
| BR-068-1 | UC này thuộc V2/P3. Không gen code cho MVP nếu nhóm cần cắt scope. |
| BR-068-2 | Chỉ chủ sở hữu bộ flashcard mới được chia sẻ hoặc thu hồi chia sẻ bộ đó. |
| BR-068-3 | Một bộ cần có ít nhất 5 thẻ mới được chia sẻ để tránh nội dung công khai quá rỗng. |
| BR-068-4 | Khi chia sẻ, bộ được đánh dấu công khai và có thể xuất hiện trong danh sách bộ flashcard cộng đồng. |
| BR-068-5 | Khi thu hồi chia sẻ, bộ không còn xuất hiện trong danh sách công khai, nhưng các bản sao mà người khác đã tạo trước đó vẫn được giữ lại. |
| BR-068-6 | Người sao chép bộ công khai nhận một bản sao độc lập. Bản sao không tự đồng bộ với bộ gốc. |
| BR-068-7 | Nội dung thẻ công khai phải được escape hoặc sanitize khi hiển thị cho người khác. |
| BR-068-8 | MVP/V2 đơn giản có thể dùng cơ chế công khai trước, báo cáo vi phạm sau. Vì vậy bộ công khai phải có nút báo cáo vi phạm. |
| BR-068-9 | Bộ chứa nội dung vi phạm, thông tin cá nhân hoặc dữ liệu đề thi/bài học không được phép công khai nếu bị phát hiện hoặc bị báo cáo hợp lệ. |

## API · DB

```
PATCH /api/flashcard-decks/{id}/share
PATCH /api/flashcard-decks/{id}/unshare
GET   /api/community/flashcard-decks
POST  /api/flashcard-decks/{id}/copy
```

`flashcard_decks` · `flashcards` (đọc + ghi) · `moderation_reports` (community, ghi)

## Test case

| # | Đầu vào | Kết quả |
| --- | --- | --- |
| T1 | Chia sẻ bộ 10 thẻ của mình | 200, `is_public = true` |
| T2 | Chia sẻ bộ người khác | 403 |
| T3 | Bộ 3 thẻ | 422 |
| T4 | Thu hồi sau khi có người sao chép | Bản sao **vẫn còn** |
| T5 | Thẻ có `<script>`, người khác mở | Render ra text |
| T6 | ArchUnit: `community` import `learning.repository` | **Test đỏ** |
| T7 | Chia sẻ lần 2 | 409, không lỗi |

---

# Tổng hợp exception nhóm 4

## Chín exception quan trọng nhất

| # | UC | Exception | Vì sao |
| --- | --- | --- | --- |
| 1 | UC-065 | `SEARCHED_OTHER_USERS_NOTES` | IDOR **không cần biết ID** — chỉ thiếu một `WHERE` là lộ ghi chú toàn hệ thống |
| 2 | UC-060 | `QUOTA_DEDUCTED_BUT_API_FAILED` | Mất tiền người học, tần suất cao hơn UC-048 nhiều |
| 3 | UC-064 | `NOTE_NOT_OWNED` | Đúng ví dụ IDOR mục B đã ghi; ghi chú là dữ liệu riêng tư nhất |
| 4 | UC-058 | `NO_FULLTEXT_INDEX` | `LIKE '%x%'` quét toàn bảng `words` mỗi lần tra nghĩa Việt |
| 5 | UC-067 | `COPIED_REVIEW_SCHEDULE` | Sao chép cả lịch ôn người khác → thẻ không xuất hiện suốt 2 tháng |
| 6 | UC-060 | `PII_IN_TRANSLATION_HISTORY` | Bảng lưu nguyên văn mọi thứ người học dán vào |
| 7 | UC-068 | `COPYRIGHT_CONTENT` | Dữ liệu đề thi của thầy lên mạng qua tính năng chia sẻ |
| 8 | UC-061 | `QUOTA_CHARGED_WRONGLY` | Tính phí tra từ điển → bấm 10 từ mất 10 lượt |
| 9 | UC-068 | `CROSS_MODULE_READ` | `community` đọc bảng `learning` — vi phạm ranh giới module |

## Bốn nhóm exception lặp lại khắp nhóm 4

| Nhóm | Xuất hiện ở | Bài học |
| --- | --- | --- |
| **IDOR** | UC-060 · UC-062 → UC-068 (**7 UC**) | Nhóm này có nhiều IDOR nhất vì toàn dữ liệu riêng tư. Cộng UC-029 nhóm 2 thành **9 UC** cùng mẫu lỗi. `OwnershipService` không còn là "nên có" mà là **bắt buộc** |
| **Chuẩn hoá để tìm kiếm** | UC-057 (pinyin) · UC-058 (nghĩa Việt) | Cùng một vấn đề: dữ liệu có dấu, người dùng gõ không dấu. Cùng một giải pháp: cột chuẩn hoá + index. Làm lúc nhập, không lúc truy vấn |
| **Trừ tiền rồi thất bại** | UC-060 · (UC-048 nhóm 3) | Gọi API ngoài **không** được nằm trong transaction DB. Trừ → gọi → lỗi thì hoàn, ghi `credit_transactions` loại `REFUND` |
| **XSS từ nội dung người dùng** | UC-062 · UC-064 · UC-065 · UC-068 | Ghi chú và flashcard là nội dung tự do. Sổ tay chỉ mình xem (tự-XSS, nhẹ) **nhưng** UC-068 chia sẻ cho người khác → đường lây thật |

---

# Khoảng trống thiết kế phát hiện ở nhóm 4

| # | Thiếu | UC bị ảnh hưởng | Mức |
| --- | --- | --- | --- |
| 1 | **Chưa có `OwnershipService`** — 7 UC nhóm này + 2 UC nhóm 2 cùng cần | UC-060 → UC-068 | 🔴 Mục B quyết định v2 vẫn treo |
| 2 | **Chưa có GIN/trgm index** trên `words.meaning_vi` | UC-058 | 🔴 Quét toàn bảng |
| 3 | **Chưa có cột `pinyin_normalized`** (không dấu) | UC-057 | 🔴 Tra pinyin không dấu không chạy |
| 4 | Chưa có cột chuẩn hoá không dấu cho `meaning_vi` | UC-058 | 🔴 Cùng loại #3 |
| 5 | **Chưa chốt `QuotaService` tính lượt cho cái gì** — dịch có, tra từ điển không | UC-060 · UC-061 · UC-031 | 🔴 Trừ oan hoặc không trừ |
| 6 | **Chưa chốt chính sách hoàn lượt** khi API dịch lỗi | UC-060 | 🔴 Mất tiền người học |
| 7 | **Chưa chốt thời hạn lưu `translation_history`** và ai xem được | UC-060 | 🔴 Kho dữ liệu riêng tư |
| 8 | Chưa có quy ước **endpoint public** cho `GUEST` | UC-056 → UC-059 | 🔴 Mục D quyết định v2 vẫn treo |
| 9 | **Chưa có luồng kiểm duyệt bộ flashcard chia sẻ** (blog có, thẻ không) | UC-068 | 🔴 Nội dung xấu/bản quyền lên công khai |
| 10 | Chưa chốt **soft delete hay hard delete** cho `notes` | UC-064 | ⚠️ Xoá nhầm mất vĩnh viễn |
| 11 | Chưa chốt xử lý **sửa đồng thời** ghi chú (ghi đè hay báo xung đột) | UC-064 | ⚠️ Mất dữ liệu âm thầm |
| 12 | Chưa chốt **trọng số mastery của flashcard** (đề xuất 0) | UC-066 | ⚠️ Tự đánh giá không nên ảnh hưởng mastery chung |
| 13 | Chưa kiểm **tỉ lệ chữ thiếu bộ thủ** trong `characters` | UC-059 | ⚠️ Quyết định cắt hay làm |
| 14 | Chưa có kiểm đối chiếu số nét với `stroke_data` | UC-059 | ⚠️ Hai nguồn lệch |
| 15 | Chưa chốt **ngưỡng giới hạn `GUEST`** theo IP | UC-056 → UC-059 | ⚠️ Chặn oan mạng NAT |
| 16 | Thiếu index `flashcards(deck_id, next_review_at)` | UC-066 | ⚠️ Quét toàn bảng |

> **Chín mục 🔴** — nhiều nhất trong bốn nhóm đã viết. Lý do: nhóm này vừa có **dữ liệu riêng
> tư** (ghi chú, lịch sử dịch), vừa có **tiền** (dịch tốn phí), vừa có **endpoint public**
> (`GUEST` tra từ điển). Ba thứ đó cộng lại là nơi tập trung rủi ro bảo mật của cả hệ thống.
>
> Mục #1 (`OwnershipService`) giờ đã xuất hiện ở **cả bốn nhóm** — đây là việc phải làm trước
> khi viết endpoint đầu tiên, không phải sửa sau.

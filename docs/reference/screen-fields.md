# CNHSK — Đặc tả field từng màn

> **Nguồn:** `design.md` §5 (32 màn) · `specs/001-auth-rbac/spec.md` (88 FR) ·
> `use-cases-01..08.md` (119 UC, 784 business rule).
>
> **Vai trò:** đây là **RP3 §3 Functional Requirements** của report FPT, và là
> tài liệu frontend đọc để không phải đoán field. Khi file này khác `design.md`
> §5, `design.md` đúng về route và actor; file này đúng về **field và validate**.

## Cách đọc bảng

| Cột | Nghĩa |
|---|---|
| **Field** | Nhãn hiển thị cho người dùng (tiếng Việt) |
| **Kiểu** | Loại control — dùng biến thể của `FormField` ở `design.md` §7 |
| **Bắt buộc** | ● bắt buộc · ○ tuỳ chọn · — chỉ đọc |
| **Validate** | Luật kiểm ở **client**; server luôn kiểm lại (`BUS-09`) |
| **Lỗi hiện ở đâu** | Dưới field · toast · banner đầu màn — theo `design.md` §9 |

**Quy ước chung cho mọi màn, không nhắc lại từng bảng:**

- Mọi field bắt buộc để trống → lỗi hiện **dưới field**, không dùng toast
  (`design.md` §9: toast không dùng cho lỗi người dùng cần sửa)
- Lỗi **422** từ server trả danh sách field → map về đúng field tương ứng
- Lỗi **401** → refresh một lần, thất bại mới sang `/login` giữ `redirect`
- Lỗi **403** → màn 403 nêu role cần thiết (`design.md` §9)
- Lỗi **402** `INSUFFICIENT_CREDITS` → chuyển `/account/billing` (`FR-072`)
- Lỗi **5xx** → `ErrorState` mức section, giữ nguyên dữ liệu đã nhập, có retry
- Mọi nút gửi form: disable đúng nút đang gửi, giữ label kèm trạng thái
- Touch target ≥ 44×44px, label tồn tại độc lập placeholder (`design.md` §7, §10)

**Ô `[CHỜ CHỐT]`** là chỗ use case không nói rõ và tôi không tự quyết. Chủ dự án
chốt trước khi code màn đó.

---

## 1 · Landing — `/`

**Actor:** mọi người · **UC:** không có UC riêng · **Scope:** MVP

Màn tĩnh, không có field nhập. Nội dung theo `design.md` §1 (nguyên tắc "việc
học quan trọng hơn trang trí") và một CTA primary duy nhất.

| Vùng | Nội dung | Hành động |
|---|---|---|
| Hero | Tiêu đề `text.display`, một câu giá trị | **CTA primary:** "Bắt đầu học" → `/register` |
| | | CTA secondary: "Đăng nhập" → `/login` |
| Tính năng | 4–6 card theo kỹ năng (nghe · đọc · viết · từ vựng) | Mỗi card → màn tương ứng, chưa đăng nhập thì → `/login` |
| Tra cứu nhanh | Ô tìm kiếm chữ Hán | → `/dictionary?q=` · khách tra được, kết quả rút gọn (UC-056) |
| Lộ trình HSK | HSK1→HSK6 dạng `LevelBadge` | → `/learn/topics` |

**Không có** trạng thái loading cho màn này — nội dung tĩnh, render ngay.

---

## 2 · Đăng ký — `/register`

**Actor:** `GUEST` · **UC-001** · **FR-001**…`FR-008` · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Email | `FormField` text, `type=email` | ● | Đúng định dạng email (`FR-005`) | Dưới field |
| Mật khẩu | `FormField` password, có nút hiện/ẩn | ● | ≥ 8 ký tự (`FR-005`) | Dưới field |
| Nhập lại mật khẩu | `FormField` password | ● | Khớp field trên (`FR-005`) | Dưới field |
| Họ tên | `FormField` text | ● | Không rỗng | Dưới field |
| Đồng ý điều khoản | Checkbox | ● | Phải tick | Dưới checkbox |

**Nút:** "Đăng ký" → `POST /api/auth/register`

| Kết quả | HTTP | Màn làm gì |
|---|---|---|
| Thành công | **201** | Chuyển `/verify-email` kèm email vừa nhập (`FR-087`) |
| Email đã tồn tại | **409** `EMAIL_ALREADY_EXISTS` | Lỗi dưới field Email, kèm link "Đăng nhập" (`FR-004`) |
| Sai định dạng | **422** | Map từng field theo danh sách server trả (`FR-005`) |
| SMTP lỗi | **201** + `emailSent: false` | **Vẫn vào** `/verify-email`, banner "chưa gửi được mail, bấm gửi lại" (`FR-007`) |
| Gọi quá nhiều | **429** | Banner đầu màn, nêu thời điểm thử lại |

> `FR-007` là chủ đích: SMTP lỗi **không** rollback tài khoản. Màn phải vào được
> `/verify-email` để người dùng bấm gửi lại, chứ không báo "đăng ký thất bại".

**Không bao giờ** hiện `passwordHash` hay mật khẩu thô ở bất kỳ đâu (`FR-008`).

---

## 3 · Đăng nhập — `/login`

**Actor:** `GUEST` · **UC-003** · `FR-014`…`FR-023`, `FR-048` · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Email | `FormField` text, `type=email` | ● | Đúng định dạng | Dưới field |
| Mật khẩu | `FormField` password | ● | Không rỗng | Dưới field |
| Ghi nhớ đăng nhập | Checkbox | ○ | — | — |

**Nút:** "Đăng nhập" → `POST /api/auth/login` · link "Quên mật khẩu" → `/forgot-password`

| Kết quả | HTTP | Màn làm gì |
|---|---|---|
| Thành công | **200** | Có `redirect` thì về đó; không thì `/dashboard`. Có role quản trị → vẫn `/dashboard`, `/admin/*` vào từ menu |
| Sai email hoặc mật khẩu | **401** `INVALID_CREDENTIALS` | **Banner đầu màn**, thông báo chung — không nói sai email hay sai mật khẩu |
| Khoá tạm | **423** `ACCOUNT_LOCKED` | Banner nêu **thời điểm mở khoá** (`locked_until`), không chỉ nói "bị khoá" |
| Bị treo / ban | **403** | Banner nêu lý do và cách liên hệ |

> **Thông báo lỗi 401 phải chung chung.** Nói "email không tồn tại" là để người
> ngoài dò được email nào đã đăng ký.

Đếm sai mật khẩu **theo tài khoản**, không theo IP (`FR-048`) — 5 lần mỗi giờ.

---

## 4 · Chờ xác thực email — `/verify-email`

**Actor:** `GUEST` vừa đăng ký · **UC-002** · `FR-087`, `FR-088` · **Scope:** MVP

Màn này có **hai chế độ** tuỳ theo có `?token=` trong URL hay không.

### Chế độ A — vào từ `/register`, không có token

| Vùng | Nội dung |
|---|---|
| Thông báo | "Đã gửi mail xác thực tới **`email`**" — email lấy từ bước đăng ký |
| Hướng dẫn | Nhắc kiểm tra hộp thư rác |
| Nút | "Gửi lại email" → `POST /api/auth/resend-verification` |
| Link | "Bỏ qua, vào học luôn" → `/login` |

**Giới hạn gửi lại:** 3 lần mỗi giờ (`FR-012`). Vượt → **429** `RATE_LIMIT_EXCEEDED`,
disable nút và hiện đếm ngược.

### Chế độ B — mở link từ mail, có `?token=`

Gọi `POST /api/auth/verify-email` ngay khi vào màn, hiện `Skeleton` trong lúc chờ.

| Kết quả | HTTP | Màn làm gì |
|---|---|---|
| Thành công | **200** | "Xác thực thành công" → `/login`, hoặc `/dashboard` nếu đang đăng nhập (UC-002 A1) |
| Token sai | **400** `TOKEN_INVALID` | "Link không hợp lệ", cho nhập email để gửi lại |
| Token hết hạn | **410** `TOKEN_EXPIRED` | "Link đã hết hạn", hiện nút "Gửi lại email" |
| Token đã dùng | **409** `TOKEN_ALREADY_USED` | "Email đã được xác thực rồi" → `/login` |
| Đã xác thực trước đó | **200** `ALREADY_VERIFIED` | **Coi như thành công, KHÔNG báo lỗi** |

> `ALREADY_VERIFIED` trả **200 không phải lỗi** là quyết định có chủ đích của
> UC-002: người dùng mở link trong hai tab không nên thấy thông báo lỗi.

**Màn này không chặn gì.** Theo `FR-010`, chưa xác thực vẫn đăng nhập và học
được; chỉ thao tác đổi điểm bị chặn bằng **403** `ACCOUNT_UNVERIFIED`.

---

## 5 · Tổng quan học tập — `/dashboard`

**Actor:** `USER`+ · **UC-056**, **UC-057** · **Scope:** MVP

Không có field nhập. Màn tổng hợp, mỗi vùng gọi API riêng và có skeleton riêng
— một vùng lỗi không làm trắng cả màn.

| Vùng | Nội dung | Trạng thái rỗng |
|---|---|---|
| Việc hôm nay | Số điểm kiến thức đến hạn ôn, nút "Ôn ngay" → `/review` | "Hôm nay không có gì đến hạn" + gợi ý học chủ đề mới |
| Chuỗi ngày học | `Progress` circular + số ngày | "Bắt đầu chuỗi đầu tiên" |
| Tiến độ HSK | `Progress` linear theo cấp + `LevelBadge` | "Chưa làm bài kiểm tra trình độ" + nút làm |
| Chủ đề đang học | 3 card gần nhất → `/learn/topics/:id` | "Chọn chủ đề đầu tiên" → `/learn/topics` |
| Hạn mức | Lượt free còn lại tháng này + số điểm (`FR-070`) | — |
| Lối tắt | Tra cứu · Flashcard · Đề thi · Game | — |

**Hạn mức hiện ở đây luôn**, không đợi người dùng hết lượt mới biết — `design.md`
§1 nguyên tắc 4 (hiển thị tiến độ có ngữ cảnh).

---

## 6 · Chủ đề — `/learn/topics`

**Actor:** `USER`+ · **UC-027**, **UC-058** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Cấp HSK | Select / tab `LevelBadge` | ○ | Thuộc HSK1–HSK6 | — |
| Tìm chủ đề | `FormField` search | ○ | — | — |
| Lọc trạng thái | Chip: tất cả · đang học · đã xong · đã khoá | ○ | — | — |

**Card chủ đề** hiện: tên · số từ · `MasteryIndicator` · trạng thái khoá.

| Trạng thái | Card thể hiện |
|---|---|
| Đã mở | `Card` interactive → `/learn/topics/:id` |
| Đang học | Thêm `Progress` + "tiếp tục" |
| Đã xong | `StatusBadge` success |
| **Đã khoá** | **Hiện điều kiện mở (90% chủ đề trước) và tiến độ hiện tại** — không chỉ phủ lớp mờ (`design.md` §9) |

> Màn khoá **phải nói cần gì để mở**. Phủ mờ rồi im lặng là lỗi UX đã ghi rõ
> trong `design.md` §9.

---

## 7 · Phiên học chủ đề — `/learn/topics/:id`

**Actor:** `USER`+ · **UC-019**, **UC-027**, **UC-042** · **Scope:** MVP

Màn nhiều bước: học từ → luyện nhận diện/nghe/viết → kiểm tra.

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Đáp án trắc nghiệm | Radio qua `QuestionRenderer` | ● | Chọn 1 | Dưới câu hỏi |
| Đáp án điền chữ | `FormField` text | ● | Không rỗng | Dưới field |
| Nét viết | Canvas `hanzi-writer` | ● | Theo `BUS-16` — **[CHỜ CHỐT]** trọng số mastery từ luyện viết | Trong canvas |
| Tự đánh giá | 4 nút: lại · khó · tốt · dễ | ● | Chọn 1 | — |

**Nút:** "Kiểm tra" → `POST /api/learning/answers` · "Bỏ qua" · "Kết thúc phiên"

| Kết quả | HTTP | Màn làm gì |
|---|---|---|
| Đúng | **200** | Phản hồi `success` + icon, `MasteryIndicator` tăng, sang câu sau |
| Sai | **200** | Phản hồi `danger` + **đáp án đúng và giải thích**, không chuyển ngay |
| Hết lượt free | **402** `INSUFFICIENT_CREDITS` | Chuyển `/account/billing` (`FR-072`, `design.md` §9) |
| Chủ đề chưa mở | **403** | Về `/learn/topics`, nêu điều kiện mở |

> **Điểm luôn do server chấm** (`BUS-09`, `FR-088` của UC-042). Client gửi đáp
> án, không gửi điểm. Màn không được tự tính mastery.

**[CHỜ CHỐT] `BUS-16`:** `hanzi-writer` chấm nét ở **client**, mâu thuẫn `BUS-09`
(chấm ở server). Chặn phần luyện viết của màn này.

---

## 8 · Ôn tập — `/review`

**Actor:** `USER`+ · **UC-059**, **UC-060** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Đáp án | `QuestionRenderer` theo loại | ● | Theo loại câu hỏi | Dưới câu hỏi |
| Tự đánh giá (flashcard) | 4 nút: lại · khó · tốt · dễ | ● | Chọn 1 | — |

**Nút:** "Trả lời" → `POST /api/learning/reviews` · "Tạm dừng"

| Kết quả | Màn làm gì |
|---|---|
| Còn thẻ | Sang thẻ sau, hiện `Progress` "còn N thẻ" |
| Hết thẻ | `EmptyState` completed + **lịch ôn kế tiếp** + nút sang `/progress` |
| Không có gì đến hạn | `EmptyState` first-use + gợi ý học chủ đề mới |

---

## 9 · Luyện viết — `/practice/writing`

**Actor:** `USER`+ · **UC-020**, **UC-021** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Chọn chữ luyện | Select / search | ● | Chữ có trong từ điển | Dưới field |
| Canvas viết nét | Canvas `hanzi-writer` | ● | Đúng thứ tự nét | Trong canvas |
| Chế độ | Toggle: xem mẫu · tô theo · viết tự do | ○ | — | — |

**Nút:** "Kiểm tra" · "Xem lại nét mẫu" · "Chữ tiếp theo"

**[CHỜ CHỐT]** Cùng vấn đề `BUS-16`: chấm nét ở client thì mastery tính thế nào.

---

## 10 · Phát âm — `/practice/pronunciation`

**Actor:** `USER`+ · **UC-022**, **UC-023** · **Scope:** MVP

Học theo **tám chặng** (`pron_stages`).

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Chặng | Tab 1–8 | ● | Chặng đã mở | — |
| Nghe mẫu | Nút play audio | — | — | Toast nếu audio lỗi |
| Ghi âm | Nút record | ○ | Quyền microphone | Banner nếu bị chặn quyền |

> **Audio phải có transcript** hoặc phần chữ tương đương (`design.md` §10) —
> người không nghe được vẫn học được.

**[CHỜ CHỐT]** Có chấm phát âm bằng máy không, hay chỉ cho nghe và tự so?
Use case không nói rõ.

---

## 11 · Đề thi — `/exams`

**Actor:** `USER`+ · **UC-033**, **UC-034** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Cấp HSK | Tab `LevelBadge` HSK1–HSK6 | ○ | — | — |
| Dạng đề | Select: đề đầy đủ · theo kỹ năng · theo dạng câu | ○ | — | — |
| Tìm đề | `FormField` search | ○ | — | — |

**Card đề** hiện: tên · số câu · thời gian · lần làm gần nhất · điểm cao nhất.

**Nút:** "Làm bài" → `/exams/:id/attempt` · "Xem lịch sử" → `/attempts`

Hết lượt free → **402**, chuyển `/account/billing`.

---

## 12 · Làm bài — `/exams/:id/attempt`

**Actor:** `USER`+ · **UC-035** · **Scope:** MVP

Màn nguy hiểm nhất — mất đáp án là mất công người dùng.

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Đáp án mỗi câu | `QuestionRenderer`, 7 dạng | ○ khi làm, ● khi nộp | Theo dạng câu | Dưới câu |
| Đánh dấu xem lại | Checkbox mỗi câu | ○ | — | — |

**7 dạng câu:** `MULTIPLE_CHOICE` · `TRUE_FALSE` · `IMAGE_MATCH` · `SENTENCE_MATCH`
· `FILL_BLANK` · `SENTENCE_ORDER` · `ESSAY` — một API `QuestionRenderer` chung
(`design.md` §7).

**Nút:** "Nộp bài" → `POST /api/learning/attempts/:id/submit` · "Lưu tạm" · điều hướng câu

| Tình huống | Màn làm gì |
|---|---|
| Bấm nộp | **Dialog xác nhận** nêu số câu chưa trả lời (`design.md` §1 nguyên tắc 5) |
| **Rời trang khi chưa nộp** | **Dialog chặn** — "đáp án sẽ mất". KHÔNG mất im lặng (`design.md` §9) |
| Hết thời gian | Tự nộp, banner "đã hết thời gian, bài đã nộp" |
| Đã nộp | **Khoá chỉnh sửa**, chỉ hiện kết quả server trả (`design.md` §9) |
| Mất mạng | Giữ đáp án ở local, đánh dấu "chưa đồng bộ" (`design.md` §9 offline) |

> **Server chấm, không tin điểm client** (`BUS-09`). Màn gửi đáp án thô.

---

## 13 · Kết quả — `/attempts/:id/result`

**Actor:** `USER`+ · **UC-036**, **UC-045** · **Scope:** MVP

Không có field nhập.

| Vùng | Nội dung |
|---|---|
| Điểm tổng | Điểm + đạt/không đạt + `LevelBadge` cấp đã thi |
| Theo kỹ năng | `Progress` cho nghe · đọc · viết |
| Danh sách câu sai | Mỗi câu: đáp án đã chọn · đáp án đúng · giải thích · điểm kiến thức liên quan |
| Điểm yếu | Top điểm kiến thức sai nhiều, nút **"Luyện ngay"** → `/learn/topics/:id` |
| So sánh | Điểm lần này so với lần trước |

**Nút:** "Luyện phần yếu" · "Làm lại đề" · "Nhờ chấm bài" → `/grading` (nếu có câu `ESSAY`)

---

## 14 · Tra cứu — `/dictionary`

**Actor:** mọi người · **UC-056**…**UC-063** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Từ khoá | `FormField` search | ● | Không rỗng | Dưới field |
| Loại tra | Tab: chữ · từ · ngữ pháp · dịch câu | ○ | — | — |
| Lọc cấp HSK | Select | ○ | — | — |

**Nút:** "Tìm" → `GET /api/dictionary/lookup?q=` (khách: `/api/public/dictionary/lookup`) · "Thêm vào flashcard" · "Lưu vào sổ tay" (chỉ `USER`)

| Kết quả | Màn làm gì |
|---|---|
| Có kết quả | `LearningCard` — chữ Hán dùng `font.hanzi`, pinyin, nghĩa, cấu tạo, ví dụ, audio |
| Không có | `EmptyState` no-result + gợi ý từ gần giống |
| **Khách vượt giới hạn** | **429** `GUEST_LIMIT_EXCEEDED` → gợi ý "đăng nhập để tra không giới hạn" (UC-056 A3) |

**Khách:** tra được nhưng kết quả rút gọn, không có nút lưu, không dịch câu
(BR-056-1/2, BR-060-1, `design.md` §5.5). Không có lượt dùng thử.

---

## 15 · Sổ tay — `/notes`

**Actor:** `USER`+ · **UC-074**…**UC-077** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Tiêu đề | `FormField` text | ● | Không rỗng, ≤ 200 ký tự | Dưới field |
| Nội dung | `FormField` textarea | ● | Không rỗng | Dưới field |
| Thẻ | Chip input | ○ | — | — |
| Gắn với | Select: chữ · từ · chủ đề | ○ | Mục tồn tại | Dưới field |

**Nút:** "Lưu" → `POST /api/learning/notes` · "Xoá" (dialog xác nhận) · "Tìm"

**Xoá là soft delete** (`BUS-10`) — ghi chú vào thùng rác, không mất hẳn.

**IDOR:** chỉ xem và sửa được ghi chú của chính mình (`BUS-01`) — server kiểm,
không dựa vào việc màn không hiện nút.

---

## 16 · Flashcard — `/flashcards`

**Actor:** `USER`+ · **UC-078**…**UC-082** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Tên bộ thẻ | `FormField` text | ● | Không rỗng | Dưới field |
| Mặt trước | `FormField` text | ● | Không rỗng | Dưới field |
| Mặt sau | `FormField` text | ● | Không rỗng | Dưới field |
| Bộ thẻ | Select | ● | Bộ của chính mình (`BUS-01`) | Dưới field |

**Nút:** "Tạo bộ" · "Thêm thẻ" · "Ôn bộ này" → `/review` · "Nhập từ tra cứu"

**[CHỜ CHỐT]** Chia sẻ bộ thẻ cho người khác là **V2** (`feature-tree` 5.x) —
màn đợt này **không có** nút chia sẻ.

---

## 17 · Tiến độ — `/progress`

**Actor:** `USER`+ · **UC-061**…**UC-064** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Khoảng thời gian | Tab: 7 · 30 · 90 ngày | ● | Chọn 1 | — |
| Loại biểu đồ | Select: mastery · thời gian học · số câu | ○ | — | — |

| Vùng | Nội dung |
|---|---|
| Mastery theo cấp | `Progress` mastery + `MasteryIndicator`, **màu kèm nhãn mức** (`design.md` §10) |
| Chuỗi ngày | Lịch nhiệt + số ngày liên tiếp |
| Thời gian học | Biểu đồ cột theo ngày |
| Điểm yếu | Top điểm kiến thức mastery thấp, nút "Luyện ngay" |

> **Không dùng màu làm tín hiệu duy nhất** cho mastery (`design.md` §10) — luôn
> kèm nhãn chữ.

---

## 18 · AI Assistant — panel toàn cục

**Actor:** `USER`+ · **UC-065**…**UC-068** · **Scope:** MVP

Không phải màn riêng — panel nổi trên mọi màn authenticated.

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Câu hỏi | `FormField` textarea | ● | Không rỗng, ≤ 1000 ký tự | Dưới field |

**Nút:** "Gửi" → `POST /api/learning/ai/ask` · "Xoá hội thoại" · thu gọn/mở rộng

| Kết quả | Màn làm gì |
|---|---|
| Đang trả lời | Skeleton text, nút gửi disable |
| Xong | Hiện câu trả lời, giữ ngữ cảnh màn hiện tại |
| Hết lượt free | **402** → `/account/billing` |
| Lỗi AI | `ErrorState` inline + retry |

**Panel KHÔNG che CTA hoặc nội dung câu hỏi** (`design.md` §7) — quan trọng ở
màn `/exams/:id/attempt`.

---

## 19 · Gói và điểm — `/account/billing`

**Actor:** `USER`+ · **UC-095**…**UC-099** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Mã thẻ | `FormField` text, uppercase | ● | Không rỗng, đúng định dạng lô | Dưới field |

**Nút:** "Nhập mã" → `POST /api/learning/cards/redeem` (**cần CSRF** — `HR-05`)

| Vùng | Nội dung |
|---|---|
| Gói hiện tại | `plan_code` + `plan_expires_at` + `StatusBadge` |
| Số điểm | Số dư, `BIGINT` không dùng `FLOAT` (`AC-07`) |
| Lượt free | Còn bao nhiêu lượt mỗi tính năng tháng này + **thời điểm reset** (`FR-071`) |
| Lịch sử giao dịch | Bảng append-only: thời điểm · loại · số điểm · số dư sau (`BUS-14`) |

| Kết quả nhập mã | HTTP | Màn làm gì |
|---|---|---|
| Thành công | **200** | Toast success, cập nhật số dư và gói ngay |
| Mã không tồn tại | **404** | Dưới field — thông báo chung, **không** nói "mã đã dùng bởi người khác" |
| Mã đã dùng | **409** | Dưới field |
| Mã hết hạn | **410** | Dưới field, nêu `expires_at` |
| Nhập sai quá nhiều | **429** | Banner, nêu thời điểm thử lại |

> **Lịch sử giao dịch không có nút xoá hay sửa** (`BUS-15`) — sổ cái append-only.
> Nhầm thì ghi giao dịch bù, không sửa dòng cũ.
>
> **Mã thẻ là đường duy nhất điểm vào hệ thống** (`FR-079`). Màn này **không có**
> nút thanh toán, không nhúng cổng thanh toán. Tiền thu ngoài hệ thống.

---

## 20 · Nhờ chấm bài — `/grading`

**Actor:** `USER`+ · **UC-104**, **UC-105** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Bài viết | `FormField` textarea | ● | Không rỗng, ≤ **[CHỜ CHỐT]** ký tự | Dưới field |
| Chủ đề / đề bài | Select hoặc text | ● | Không rỗng | Dưới field |
| Cấp HSK | Select | ● | HSK1–HSK6 | Dưới field |

**Nút:** "Gửi chấm" → `POST /api/learning/grading-requests` (trừ điểm — cần CSRF)

| Trạng thái yêu cầu | `StatusBadge` |
|---|---|
| Chờ chấm | `info` + vị trí trong hàng đợi |
| Đang chấm | `warning` |
| Đã chấm | `success` + nút xem kết quả |
| Bị từ chối | `danger` + lý do |

**Kết quả chấm** hiện: điểm · nhận xét từng đoạn · lỗi ngữ pháp · gợi ý sửa.

**[CHỜ CHỐT]** Giới hạn độ dài bài viết, và giá (số điểm) mỗi lần chấm.

---

## 21 · Cộng đồng — `/community` · **V2**

**Actor:** `USER`+ · **UC-083**…**UC-094** · **Scope:** V2

> **Màn V2 — không code đợt này.** Đặc tả ở mức đủ cho RP3, chưa chi tiết bằng
> màn MVP vì chưa có spec feature.

| Field | Kiểu | Bắt buộc | Validate |
|---|---|---|---|
| Tiêu đề bài viết | `FormField` text | ● | ≤ 200 ký tự |
| Nội dung | Rich text | ● | Không rỗng |
| Thẻ chủ đề | Chip input | ○ | — |
| Bình luận | `FormField` textarea | ● | Không rỗng |

| Vùng | Nội dung |
|---|---|
| Blog | Danh sách bài, lọc theo thẻ |
| Quiz cộng đồng | Quiz do người dùng tạo |
| Bảng xếp hạng chủ đề | Thứ hạng theo chủ đề |
| Báo cáo nội dung | Nút báo cáo → hàng đợi `MANAGER` |

---

## 22 · Game hub — `game.cnhsk.com/games`

**Actor:** `USER`+ · **UC-087** · **Scope:** MVP

Ứng dụng **riêng biệt** — React + Phaser, codebase độc lập, cookie chung tên
miền `cnhsk.com`.

Không có field nhập. Luôn bắt đầu bằng `GET /api/auth/me`.

| Vùng | Nội dung |
|---|---|
| Danh sách game | Card mỗi game + điểm cao nhất cá nhân |
| Thành tích | Tổng điểm, số ván |

| Kết quả `/api/auth/me` | Màn làm gì |
|---|---|
| **200** | Hiện hub |
| **401** | Chuyển `cnhsk.com/login` giữ `redirect` |

Web game **không có** blog, quiz, học tập, thanh toán hay quản trị
(`design.md` §5.2).

---

## 23 · Game session — `/games/:code/play`

**Actor:** `USER`+ · **UC-118** · **Scope:** V2

Canvas Phaser, không có form.

**Nút:** "Bắt đầu" · "Tạm dừng" · "Thoát" (dialog xác nhận nếu đang chơi)

| Kết quả gửi điểm | Màn làm gì |
|---|---|
| Hợp lệ | → `/games/:code/rank` |
| **Bất thường** | **Loại điểm, ghi log**, về hub — không báo chi tiết cho người chơi |

> **Server xác minh điểm** (`BUS-09`). Client gửi dữ liệu ván chơi, không gửi
> điểm cuối. Màn không được tự công bố điểm trước khi server xác nhận.

---

## 24 · Bảng hạng game — `/games/:code/rank`

**Actor:** `USER`+ · **UC-119** · **Scope:** V2

| Field | Kiểu | Bắt buộc | Validate |
|---|---|---|---|
| Khoảng thời gian | Tab: tuần · tháng · tất cả | ○ | — |

Bảng: thứ hạng · tên người chơi · điểm · thời điểm. Dòng của chính mình **đánh
dấu nổi bật**.

---

## 25 · Duyệt câu hỏi AI — `/admin/questions/review`

**Actor:** `TEACHER` · `CONTENT_ADMIN` · **UC-108**, **UC-109** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Lọc trạng thái | Chip: chờ duyệt · đã duyệt · đã từ chối | ○ | — | — |
| Lý do từ chối | `FormField` textarea | ● khi từ chối | Không rỗng | Dưới field |
| Sửa nội dung câu | `FormField` textarea | ○ | — | Dưới field |

**Nút:** "Chấp nhận" · "Từ chối" (bắt nhập lý do) · "Sửa rồi chấp nhận" · "Câu tiếp"

`DataTable` trên desktop, list/card trên mobile (`design.md` §7).

> **[HR-08]** Dữ liệu đề thi của giảng viên **không bao giờ** commit vào git.
> Màn này đọc từ database, không đọc từ file trong repo.

---

## 26 · Chấm bài thuê — `/admin/grading`

**Actor:** `TEACHER` · **UC-104**, **UC-105** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Điểm | `FormField` number | ● | Trong thang điểm, `BIGINT` không `FLOAT` (`AC-07`) | Dưới field |
| Nhận xét chung | `FormField` textarea | ● | Không rỗng | Dưới field |
| Nhận xét từng đoạn | Textarea theo đoạn | ○ | — | Dưới field |
| Lý do từ chối | `FormField` textarea | ● khi từ chối | Không rỗng | Dưới field |

**Nút:** "Nhận bài" · "Gửi kết quả" · "Từ chối bài" · "Bài tiếp"

**[CHỜ CHỐT]** Tỉ lệ ăn chia cho giảng viên — `feature-tree` có nhắc 30% nhưng
chưa chốt là 30% của gì.

---

## 27 · Nhập dữ liệu — `/admin/imports`

**Actor:** `CONTENT_ADMIN` · **UC-110**, **UC-111** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| File | File upload | ● | Định dạng cho phép, kích thước ≤ **[CHỜ CHỐT]** | Dưới field |
| Loại dữ liệu | Select: từ vựng · câu hỏi · chủ đề · ngữ pháp | ● | Chọn 1 | Dưới field |
| Chế độ | Radio: thêm mới · cập nhật · thay thế | ● | Chọn 1 | Dưới field |

**Nút:** "Tải lên và kiểm tra" → xem trước · "Xác nhận nhập" (dialog xác nhận)

| Vùng | Nội dung |
|---|---|
| Xem trước | Bảng N dòng đầu + số dòng hợp lệ / lỗi |
| Danh sách lỗi | Dòng nào, cột nào, sai gì — tải được file lỗi |
| Lịch sử nhập | Ai nhập, khi nào, bao nhiêu dòng, kết quả |

> **Luôn có bước xem trước trước khi ghi.** Nhập sai hàng nghìn dòng vào database
> rồi mới biết là việc không hoàn tác được.
>
> **[HR-08]** File đề thi của giảng viên: nhập qua màn này vào database, **không**
> đặt vào repo. `.gitignore` đã chặn `*.docx`, `data/`, `imports/`, `de-thi/`.

---

## 28 · Quản lý đề thi và kho câu — `/admin/exams`

**Actor:** `CONTENT_ADMIN` · **UC-109**, **UC-112** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Tên đề | `FormField` text | ● | Không rỗng | Dưới field |
| Cấp HSK | Select | ● | HSK1–HSK6 | Dưới field |
| Thời gian làm bài | `FormField` number (phút) | ● | > 0 | Dưới field |
| Nội dung câu hỏi | `FormField` textarea | ● | Không rỗng | Dưới field |
| Dạng câu | Select 7 dạng | ● | Thuộc 7 dạng (`AC-06`: `VARCHAR` + `CHECK`) | Dưới field |
| Đáp án đúng | Theo dạng câu | ● | Theo dạng | Dưới field |
| Điểm kiến thức | Multi-select | ● | ≥ 1 điểm kiến thức (UC-042 tiền điều kiện) | Dưới field |
| Trạng thái | Select: nháp · đã xuất bản | ● | — | Dưới field |

**Nút:** "Tạo đề" · "Thêm câu" · "Xuất bản" (dialog xác nhận) · "Gỡ xuất bản"

> **Câu hỏi phải có ≥ 1 `question_knowledge_points`** — UC-042 (cập nhật mastery)
> cần nó. Thiếu thì trả lời câu đó không cập nhật được mastery, lỗi im lặng.

**Xoá là soft delete** (`BUS-10`) — câu hỏi đã dùng trong bài thi cũ không được
mất, lịch sử điểm sẽ hỏng.

---

## 29 · Quản lý cuộc thi — `/admin/contests` · **V2**

**Actor:** `CONTENT_ADMIN` · **UC-113** · **Scope:** V2

> **Màn V2 — không code đợt này.**

| Field | Kiểu | Bắt buộc | Validate |
|---|---|---|---|
| Tên cuộc thi | `FormField` text | ● | Không rỗng |
| Thời gian bắt đầu / kết thúc | Datetime | ● | `TIMESTAMPTZ`, kết thúc > bắt đầu (`AC-08`) |
| Đề thi | Select | ● | Đề đã xuất bản |
| Giải thưởng | `FormField` textarea | ○ | — |

Chống gian lận theo `feature-tree` §5.8.1.

---

## 30 · Sổ cái và tranh chấp — `/admin/ledger`

**Actor:** `FINANCE_ADMIN` · **UC-100**, **UC-102** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Tìm theo user | `FormField` search | ○ | — | — |
| Khoảng thời gian | Date range | ○ | `TIMESTAMPTZ` (`AC-08`) | Dưới field |
| Loại giao dịch | Select | ○ | — | — |
| Số điểm điều chỉnh | `FormField` number | ● khi điều chỉnh | `BIGINT`, không `FLOAT` (`AC-07`) | Dưới field |
| Lý do điều chỉnh | `FormField` textarea | ● khi điều chỉnh | Không rỗng | Dưới field |

**Nút:** "Ghi giao dịch bù" (dialog xác nhận, cần CSRF) · "Xuất báo cáo"

| Vùng | Nội dung |
|---|---|
| Sổ cái | Bảng append-only: thời điểm · user · loại · số điểm · số dư sau · `seq` |
| Tranh chấp | Hàng đợi khiếu nại, trạng thái xử lý |
| Đối soát | Tổng điểm phát hành vs đã dùng |

> **KHÔNG có nút xoá hay sửa dòng sổ cái** (`BUS-15`). Sai thì ghi **giao dịch
> bù**, dòng cũ giữ nguyên. Đây là luật, không phải tuỳ chọn thiết kế.
>
> Cột `seq` + `UNIQUE(user_id, seq)` chống đua — hai request đồng thời không ghi
> cùng một `seq`.

---

## 31 · Mã thẻ và gói dịch vụ — `/admin/billing`

**Actor:** `FINANCE_ADMIN` · **UC-099**, **UC-101** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Loại thẻ | Select: `CREDIT` · `SUBSCRIPTION` | ● | Chọn 1 | Dưới field |
| Số lượng mã | `FormField` number | ● | `1 ≤ n ≤ 100` mỗi lô | Dưới field |
| Giá trị mỗi mã | `FormField` number | ● | > 0, `BIGINT` (`AC-07`) | Dưới field |
| **Kênh** | Select: `ECOSYSTEM_GIFT` · `PARTNER_BATCH` · `DIRECT` | ● | Thuộc 3 giá trị (`FR-076`) | Dưới field |
| `partner_code` | `FormField` text | ● khi `PARTNER_BATCH` | Không rỗng (`FR-077`) | Dưới field |
| `issued_to` | `FormField` text | ● khi `ECOSYSTEM_GIFT` | Không rỗng (`FR-078`) | Dưới field |
| `campaign` | `FormField` text | ○ | — | — |
| `expires_at` | Datetime | ○ | `TIMESTAMPTZ` (`FR-082`) | Dưới field |

**Nút:** "Tạo lô mã" (dialog xác nhận, cần CSRF) · "Xuất danh sách mã" · "Vô hiệu lô"

| Vùng | Nội dung |
|---|---|
| Danh sách lô | Lô · kênh · số mã · đã dùng · tỉ lệ đổi (`FR-080`) |
| Báo cáo phễu | Tỉ lệ đổi theo `channel` và `campaign` |
| Gói dịch vụ | Danh mục `plans` + `benefits` JSONB |

> **Mã thô chỉ hiện MỘT LẦN** lúc tạo lô, sau đó chỉ còn hash (`BUS-12`). Màn
> phải cảnh báo rõ trước khi đóng dialog, và cho tải file ngay tại đó.
>
> Mã sinh bằng `SecureRandom` (`BUS-13`), không dùng `Random` hay timestamp.
>
> **Hai field phụ thuộc kênh:** `PARTNER_BATCH` bắt buộc `partner_code`,
> `ECOSYSTEM_GIFT` bắt buộc `issued_to`. Form phải đổi field bắt buộc theo kênh
> đã chọn, vì database có `CHECK` chặn — gửi thiếu sẽ lỗi 500 trông như bug.

> **Giới hạn 100 mã mỗi lô.** Chặn ở cả hai phía: client validate `1 ≤ n ≤ 100`,
> server trả **422** nếu vượt. Cần nhiều hơn thì tạo nhiều lô — mỗi lô có
> `campaign` riêng nên vẫn đối soát được, và chia lô giúp giới hạn thiệt hại
> khi một lô bị lộ.

---

## 32 · Người dùng và phân quyền — `/admin/users`

**Actor:** `SUPER_ADMIN` · **UC-114**, **UC-115**, **UC-116** · **Scope:** MVP

| Field | Kiểu | Bắt buộc | Validate | Lỗi hiện ở đâu |
|---|---|---|---|---|
| Tìm user | `FormField` search (email / tên) | ○ | — | — |
| Lọc role | Multi-select 6 role | ○ | Thuộc 6 role trong DB | — |
| Lọc trạng thái | Chip: hoạt động · khoá · treo · ban | ○ | — | — |
| Gán role | Multi-select | ● khi gán | Thuộc **6 role**: `USER` `TEACHER` `MANAGER` `CONTENT_ADMIN` `FINANCE_ADMIN` `SUPER_ADMIN` | Dưới field |
| Lý do khoá / treo / ban | `FormField` textarea | ● | Không rỗng | Dưới field |
| Thời hạn khoá | Datetime | ○ | `TIMESTAMPTZ` (`AC-08`) | Dưới field |

**Nút:** "Gán role" · "Thu hồi role" · "Khoá" · "Treo" · "Ban" — **mọi nút đều
có dialog xác nhận**, cần CSRF

| Vùng | Nội dung |
|---|---|
| Danh sách user | `DataTable`: email (mask) · tên · role · trạng thái · lần đăng nhập cuối |
| Chi tiết user | Thông tin tài khoản (MVP); lịch sử **đăng nhập thành công** chỉ dành cho `SUPER_ADMIN` ở V2 (UC-014, IP đã mask theo `FR-057`) |
| Lịch sử phân quyền | Ai gán gì cho ai, khi nào (`audit_logs`, `FR-064`) |

> **Chỉ 6 role gán được.** `GUEST` và `SYSTEM` là actor, **không phải role** —
> tuyệt đối không có trong danh sách chọn. Gán được sẽ tạo ra tài khoản "khách"
> đăng nhập được, là lỗ xác thực.
>
> **Không có màn CRUD role.** Role là 6 giá trị cố định trong `CHECK` constraint
> (`AC-06`), không phải dữ liệu người dùng tạo. Thêm role mới = migration + RFC.
>
> **Mọi lần gán/thu hồi role ghi `audit_logs`** (`FR-064`) — append-only.

---

## Tổng hợp

| Mục | Số |
|---|---|
| Màn đã đặc tả | **32** |
| Màn gắn nhãn MVP | 27 |
| Màn gắn nhãn V2 | 5 |
| Ô `[CHỜ CHỐT]` | **11** (7 mục, vài mục nhắc ở hai màn) |

> **Nhãn Scope ở đây theo UC, không theo feature.** `feature-tree.md` chia 21
> feature MVP / 11 feature V2; một màn MVP có thể chứa UC thuộc feature V2 và
> ngược lại. Khi lập kế hoạch sprint, dùng nhãn của `feature-tree.md`; khi code
> một màn, dùng nhãn ở đây để biết phần nào trong màn hoãn lại.

### Bảy chỗ chờ chủ dự án chốt

| # | Màn | Chờ gì | Chặn |
|---|---|---|---|
| 1 | Phiên học · Luyện viết | `BUS-16` trọng số mastery từ luyện viết | Màn 7, 9 |
| 2 | Phát âm | Có chấm phát âm bằng máy không | Màn 10 |
| 3 | Nhờ chấm bài | Giới hạn độ dài bài viết | Màn 20 |
| 4 | Nhờ chấm bài | Giá (số điểm) mỗi lần chấm | Màn 20 |
| 5 | Chấm bài thuê | Tỉ lệ ăn chia cho giảng viên | Màn 26 |
| 6 | Nhập dữ liệu | Kích thước file tối đa | Màn 27 |
| 7 | Flashcard | Chia sẻ bộ thẻ — xác nhận là V2 | Màn 16 |

### Đã chốt

| Màn | Chốt | Ngày |
|---|---|---|
| 31 · Mã thẻ | **Tối đa 100 mã mỗi lô**, chặn ở client và server (422) | 2026-10-07 |

### Ghi chú khi dùng tài liệu này

**Màn V2 đặc tả nông hơn màn MVP.** 11 màn V2 chưa có spec feature, nên field
có thể đổi khi viết spec thật. Đủ cho RP3, chưa đủ để code.

**`UC-014` chưa có mục riêng** trong `feature-tree.md`. Đã chốt đây là chức năng
V2 cho `SUPER_ADMIN` xem lịch sử đăng nhập thành công; không đưa vào màn MVP.

**Số business rule thật là 784, không phải 714.** Con số 714 trong các tài liệu
khác thiếu 70 BR của `use-cases-01-xac-thuc.md`, vì file đó dùng mã `BR-01`
(hai chữ số) còn 7 file kia dùng `BR-094-8` (ba chữ số) — bộ đếm cũ chỉ bắt dạng
thứ hai.

# Hướng dẫn vẽ sơ đồ use case

> Dành cho người vẽ sơ đồ use case đưa vào báo cáo. Bản này theo **133 use case**,
> cập nhật 2026-10-19 (đổi tên actor, bỏ `SYSTEM`). Sơ đồ vẽ theo bản cũ cần vẽ lại.

## Bốn quyết định đã chốt

| # | Vấn đề | Chốt |
|---|---|---|
| 1 | Tên actor | `GUEST` · `LEARNER` · `TEACHER` · `MODERATOR` · `CONTENT_EDITOR` · `ACCOUNTANT` · `ADMIN`. Chỉ actor quản trị tổng mang chữ "Admin" |
| 2 | Ai duyệt câu hỏi AI ở UC-108, sửa câu hỏi ở UC-109 | **Chỉ `TEACHER`** |
| 3 | Có vẽ `SYSTEM` thành actor không | **Không.** 14 UC trước đây gán `SYSTEM` được xếp lại — mục 2 |
| 4 | Số use case | **133** (137 − 4 UC rời danh sách ở mục 2) |

---

## 1 · Bảy actor và phần việc riêng

| Actor | Tên | Chỉ actor này làm được |
|---|---|---|
| `GUEST` | Khách | Đăng ký, đăng nhập, tra từ giới hạn, xem nội dung công khai |
| `LEARNER` | Người học | Học, luyện, thi, ôn, thư viện, game, cộng đồng, đổi mã thẻ |
| `TEACHER` | Giáo viên | Duyệt câu hỏi AI, sửa nội dung câu hỏi — UC-108, UC-109 · chấm bài viết thuê — UC-104, UC-105 |
| `MODERATOR` | Kiểm duyệt viên | Duyệt bài đăng, xử lý báo cáo vi phạm |
| `CONTENT_EDITOR` | Biên tập viên nội dung | Nhập dữ liệu, quản lý đề thi, cuộc thi — UC-110 → UC-113 |
| `ACCOUNTANT` | Kế toán | Sổ cái, mã thẻ, gói dịch vụ — UC-099 → UC-102 |
| `ADMIN` | Quản trị viên | Người dùng và phân quyền — UC-114 → UC-116 |

Nguyên tắc *phân tách nhiệm vụ* vẫn giữ: `CONTENT_EDITOR` không vào sổ cái, không sửa nội
dung câu hỏi; `ACCOUNTANT` không nhập dữ liệu học; `ADMIN` không đụng sổ cái.

---

## 2 · Không vẽ `SYSTEM` thành actor

Theo UML, actor là thực thể **bên ngoài** hệ thống. Phần mềm tự chạy không phải actor,
nên dự án **không** vẽ `SYSTEM`. 14 use case trước đây gán `SYSTEM` được xếp lại theo
cách chúng thực sự được kích hoạt (chốt 19/10/2026):

| UC | Tên | Xếp loại | Vẽ ở đâu |
|---|---|---|---|
| UC-006 | Làm mới access token | **Không phải UC** → yêu cầu chức năng phiên đăng nhập (spec 001) | Không vẽ |
| UC-012 | Tạm chặn đăng nhập sau nhiều lần sai | **«extend»** UC-003, UC-004 | Sơ đồ `GUEST` |
| UC-022 | Mở tầng phát âm tiếp theo | **«include»** của UC-021 | Sơ đồ `LEARNER` |
| UC-042 | Cập nhật mức độ nắm vững | **«include»** của UC-029, UC-035, UC-047, UC-088 | Sơ đồ `LEARNER` |
| UC-043 | Cập nhật mastery sau khi chơi game | **Gộp vào UC-042** | Không vẽ |
| UC-044 | Tính lại hạn ôn | **«include»** của UC-042, UC-024, UC-066 | Sơ đồ `LEARNER` |
| UC-046 | Mở khóa chủ đề khi đạt ngưỡng | **«include»** của UC-029 | Sơ đồ `LEARNER` |
| UC-049 | Sinh câu hỏi vào hàng đợi duyệt | **«include»** của UC-048 | Sơ đồ `LEARNER` |
| UC-050 | Tái sử dụng câu hỏi đã duyệt | **«include»** của UC-047 | Sơ đồ `LEARNER` |
| UC-055 | Gửi nhắc học tự động | **Background Job** (cài giờ ở UC-054) | Không vẽ |
| UC-088 | Lưu điểm game và cộng mastery | **«include»** của UC-087 | Sơ đồ `LEARNER` |
| UC-096 | Trừ quyền sử dụng tính năng tốn phí | **«include»** của UC-048, UC-060, UC-085, UC-103, UC-122 | Sơ đồ `LEARNER` |
| UC-097 | Hoàn quyền khi hành động thất bại | **«extend»** của UC-096 | Sơ đồ `LEARNER` |
| UC-107 | Hoàn quyền khi không ai nhận chấm bài | **Background Job** | Không vẽ |

4 UC rời danh sách (UC-006, UC-043, UC-055, UC-107) → **133 use case**. Hai Background Job
mới vào danh mục Background Job của SRS.

**Khi hội đồng hỏi "ai kích hoạt UC-042?"**: người học, qua use case họ đang làm
(nộp bài, làm test chủ đề, chơi game, luyện phần yếu). UC-042 là bước «include» bắt buộc
của các use case đó.

---

## 3 · Bảy sơ đồ nên vẽ

Một sơ đồ cho mỗi actor, trừ `LEARNER` phải chia nhỏ vì có 69 use case.

| # | Sơ đồ | Actor | Số UC | Ghi chú |
|---|---|---|---|---|
| 1 | Khách chưa đăng nhập | `GUEST` | 16 | |
| 2 | Người học — học và luyện | `LEARNER` | ~25 | Gồm UC-126 → UC-129 |
| 3 | Người học — thi và tiến độ | `LEARNER` | ~20 | Gồm UC-130, UC-131 |
| 4 | Người học — thư viện, cộng đồng, điểm | `LEARNER` | ~24 | Gồm UC-132 → UC-134, UC-136 |
| 5 | Kiểm duyệt nội dung | `TEACHER` · `CONTENT_EDITOR` · `MODERATOR` | ~20 | Gồm UC-135, UC-137 |
| 6 | Quản trị tài chính | `ACCOUNTANT` | 4 | |
| 7 | Quản trị hệ thống | `ADMIN` | 3 | |

**Giới hạn 30 use case mỗi sơ đồ.** Vượt mức đó thì sơ đồ không đọc được khi in
vào báo cáo, phải chia nhỏ tiếp.

Các use case «include»/«extend» ở mục 2 xuất hiện trong sơ đồ của actor sở hữu use case gốc,
ở đầu nhận của mũi tên.

---

## 4 · Hai loại quan hệ

| Quan hệ | Nghĩa | Phép thử |
|---|---|---|
| `«include»` | Use case gốc **luôn** gọi use case này | Bỏ nó đi, use case gốc **không chạy được** |
| `«extend»` | Use case này mở rộng use case gốc khi có điều kiện | Bỏ nó đi, use case gốc **vẫn chạy** |

### Các quan hệ `«include»` trong dự án

```
(UC-035 Nộp bài và nhận điểm) ─────«include»───→ (UC-042 Cập nhật mức độ nắm vững)
(UC-029 Làm bài kiểm tra chủ đề) ──«include»───→ (UC-042)
(UC-088 Lưu điểm game) ────────────«include»───→ (UC-042)
(UC-047 Nhận bài luyện phần yếu) ──«include»───→ (UC-042)
(UC-087 Chơi game) ────────────────«include»───→ (UC-088 Lưu điểm game)
(UC-021 Luyện phát âm) ────────────«include»───→ (UC-022 Mở tầng tiếp theo)
(UC-029 Làm bài kiểm tra chủ đề) ──«include»───→ (UC-046 Mở khóa chủ đề)
(UC-048 Yêu cầu sinh bài luyện) ───«include»───→ (UC-049 Sinh câu vào hàng đợi duyệt)
(UC-047 Nhận bài luyện phần yếu) ──«include»───→ (UC-050 Tái sử dụng câu đã duyệt)

(UC-024 Ôn lại ngữ pháp) ──────────«include»───→ (UC-044 Tính lại hạn ôn)
(UC-042) ──────────────────────────«include»───→ (UC-044)
(UC-066 Ôn bộ thẻ) ────────────────«include»───→ (UC-044)

(UC-048 Yêu cầu sinh bài luyện) ───«include»───→ (UC-096 Trừ điểm)
(UC-060 Dịch câu) ─────────────────«include»───→ (UC-096)
(UC-085 Hỏi trợ lý ảo) ────────────«include»───→ (UC-096)
(UC-103 Gửi yêu cầu chấm bài) ─────«include»───→ (UC-096)
(UC-122 Luyện Shadowing) ──────────«include»───→ (UC-096)
```

### Mười ba use case tổng quát và các `«extend»`

Mỗi use case tổng quát là tâm của một chùm `«extend»`. Vẽ use case tổng quát ở
giữa, các use case chi tiết vây quanh.

| Use case tổng quát | Các use case chi tiết mở rộng nó |
|---|---|
| UC-125 Đăng nhập vào hệ thống | UC-003 · UC-004 · UC-005 |
| UC-126 Luyện viết chữ Hán | UC-015 · UC-016 · UC-017 · UC-018 |
| UC-127 Nhận diện chữ Hán | UC-019 · UC-020 |
| UC-128 Học một chủ đề từ vựng | UC-026 · UC-027 · UC-028 · UC-029 |
| UC-129 Học qua video | UC-030 · UC-031 · UC-032 · UC-120 · UC-122 · UC-124 |
| UC-130 Làm đề thi HSK | UC-033 · UC-034 · UC-035 · UC-036 |
| UC-131 Xem và phân tích kết quả thi | UC-038 · UC-039 · UC-040 · UC-041 |
| UC-132 Tra cứu từ điển | UC-056 · UC-057 · UC-058 · UC-059 |
| UC-133 Quản lý nội dung học cá nhân | UC-062 → UC-067 |
| UC-134 Tham gia cộng đồng | UC-069 → UC-075 |
| UC-135 Kiểm duyệt nội dung cộng đồng | UC-076 · UC-077 · UC-078 · UC-079 |
| UC-136 Quản lý điểm và gói dịch vụ | UC-093 · UC-094 · UC-095 · UC-098 |
| UC-137 Quản trị kho nội dung học | UC-108 → UC-112 |

**UC-136 là use case tổng quát duy nhất có `«include»`** — nó luôn gọi UC-096 và
UC-097, vì mọi hành động tốn phí đều phải trừ điểm trước và hoàn điểm khi lỗi.

---

## 5 · Mẫu một sơ đồ

Sơ đồ đăng nhập, cho thấy cả use case tổng quát và chi tiết:

```
                    ┌──────────────────────────────┐
                    │  UC-125 Đăng nhập vào        │
  GUEST ───────────→│  hệ thống                    │
                    └──────────────────────────────┘
                              ▲  ▲  ▲
                      «extend»│  │  │«extend»
                    ┌─────────┘  │  └─────────┐
                    │            │«extend»    │
         ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
         │ UC-003       │ │ UC-004       │ │ UC-005       │
         │ Đăng nhập    │ │ Đăng nhập    │ │ Đăng nhập    │
         │ trên web     │ │ trên mobile  │ │ trang game   │
         └──────────────┘ └──────────────┘ └──────────────┘
```

Mũi tên `«extend»` trỏ **từ** use case chi tiết **tới** use case tổng quát. Đây là
hướng đúng theo UML: use case mở rộng trỏ tới use case được mở rộng.

Mũi tên `«include»` thì ngược lại — trỏ **từ** use case gốc **tới** use case được
dùng lại.

---

## 6 · Những chỗ dễ sai

| Lỗi | Đúng là |
|---|---|
| Vẽ `SYSTEM`, `Worker`, `Scheduler`, `Caller` thành actor | Không phải actor. Bước tự động là «include»/«extend» của UC người dùng; việc chạy theo giờ là Background Job |
| Vẽ tên module Java thành actor | `community` và `learning` là package, không phải actor |
| Vẽ `ADMIN` gộp | Đã tách thành `CONTENT_EDITOR`, `ACCOUNTANT`, `ADMIN` |
| Để `GUEST` trong danh sách gán role | `GUEST` là actor nhưng **không phải role**, không gán cho tài khoản được |
| Ghi "Super Admin", "Content Admin", "Finance Admin", "Manager", "User" | Dùng `ADMIN`, `CONTENT_EDITOR`, `ACCOUNTANT`, `MODERATOR`, `LEARNER` |
| Vẽ mũi tên `«extend»` sai hướng | Trỏ từ use case chi tiết tới use case tổng quát |
| Gộp 69 use case của `LEARNER` vào một sơ đồ | Chia ba sơ đồ theo nhóm chức năng |

---

## 7 · Nguồn tra cứu

| Cần gì | Xem ở đâu |
|---|---|
| Danh sách bảy actor và quyền | Hiến pháp, mục *Bảy actor* |
| Use case nào thuộc actor nào | Bảng tra nhanh đầu mỗi file `use-cases-*.md` |
| Quan hệ của một use case tổng quát | Mục *Quan hệ use case* trong chính use case đó |
| Màn hình nào cho actor nào | `design.md` mục 3 và mục 5.3 |

**Tổng: 133 use case.** Nếu số đếm được khác con số này thì có file chưa được
cập nhật — kiểm lại trước khi vẽ.

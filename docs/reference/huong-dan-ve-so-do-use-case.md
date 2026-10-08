# Hướng dẫn vẽ sơ đồ use case

> Dành cho người vẽ sơ đồ use case đưa vào báo cáo. Bản này theo **137 use case**,
> cập nhật 2026-10-08. Các sơ đồ vẽ theo bản 119 use case cũ cần vẽ lại.

## Ba quyết định đã chốt

| # | Vấn đề | Chốt |
|---|---|---|
| 1 | Ai duyệt câu hỏi AI ở UC-108 | **Cả `TEACHER` và `CONTENT_ADMIN`** |
| 2 | Có vẽ `SYSTEM` thành actor không | **Có** — giữ đúng tám actor đã công bố |
| 3 | Sơ đồ theo bản 119 use case | **Vẽ lại** theo bản 137 |

---

## 1 · UC-108 có hai actor

Trước đây tài liệu ghi `UC-108 Duyệt câu hỏi AI sinh` chỉ thuộc `TEACHER`. Đã sửa
thành **hai actor** cho khớp `BUS-07` trong Hiến pháp: *"Nội dung AI sinh luôn vào
trạng thái chờ duyệt, chỉ `TEACHER` hoặc `CONTENT_ADMIN` duyệt"*.

Cách vẽ: **hai actor cùng trỏ vào một use case**.

```
TEACHER ────────┐
                ├──→ (UC-108 Duyệt câu hỏi AI sinh)
CONTENT_ADMIN ──┘
```

Điều này cũng áp cho `UC-109 Sửa nội dung câu hỏi` — đã ghi hai actor từ trước.

### Việc phân chia role không đổi ở chỗ khác

| Role | Chỉ role này làm được |
|---|---|
| `TEACHER` | Chấm bài viết thuê — UC-104, UC-105 |
| `CONTENT_ADMIN` | Nhập dữ liệu, quản lý đề thi, cuộc thi — UC-110 → UC-113 |
| `FINANCE_ADMIN` | Sổ cái, mã thẻ, gói dịch vụ — UC-099 → UC-102 |
| `SUPER_ADMIN` | Người dùng và phân quyền — UC-114 → UC-116 |

Nguyên tắc *phân tách nhiệm vụ* vẫn giữ: `CONTENT_ADMIN` không vào sổ cái,
`FINANCE_ADMIN` không nhập dữ liệu học.

---

## 2 · Vẽ `SYSTEM` thành actor

Dự án đã công bố **tám actor** trong Hiến pháp, gồm `SYSTEM`. Sơ đồ giữ đúng con
số đó.

```
            ┌─────────────────────────┐
  SYSTEM ──→│  (UC-042 Cập nhật       │
            │   mức độ nắm vững)      │
            └─────────────────────────┘
```

**Lưu ý khi bảo vệ:** theo chuẩn UML, actor là thực thể **bên ngoài** hệ thống,
nên `SYSTEM` không phải actor đúng nghĩa. Nếu hội đồng hỏi, trả lời rằng dự án
coi `SYSTEM` là actor nghiệp vụ để thể hiện rõ **13 use case không do người kích
hoạt** — chúng chạy theo lịch hoặc được gọi từ use case khác.

### Mười ba use case của `SYSTEM`

| UC | Tên | Kiểu kích hoạt |
|---|---|---|
| UC-012 | Khóa tài khoản tạm sau nhiều lần sai mật khẩu | Khi có sự kiện |
| UC-022 | Mở tầng phát âm tiếp theo | Khi có sự kiện |
| UC-042 | Cập nhật mức độ nắm vững | Được use case khác gọi |
| UC-043 | Cập nhật mức độ nắm vững sau khi chơi game | Được use case khác gọi |
| UC-044 | Tính lại hạn ôn | Được use case khác gọi |
| UC-046 | Mở khóa chủ đề khi đạt ngưỡng | Khi có sự kiện |
| UC-049 | Sinh câu hỏi vào hàng đợi duyệt | Tiến trình nền |
| UC-050 | Tái sử dụng câu hỏi đã duyệt | Được use case khác gọi |
| UC-055 | Gửi nhắc học tự động | Tác vụ định kỳ |
| UC-096 | Trừ điểm khi dùng tính năng tốn phí | Được use case khác gọi |
| UC-097 | Hoàn điểm khi tính năng lỗi | Được use case khác gọi |
| UC-107 | Hoàn điểm khi không ai nhận chấm bài trong hạn | Tác vụ định kỳ |

> `UC-042`, `UC-044` và `UC-096` là use case **được gọi lại nhiều nơi**, nên trong
> sơ đồ chúng xuất hiện ở đầu nhận của nhiều mũi tên `«include»`.

---

## 3 · Bảy sơ đồ nên vẽ

Một sơ đồ cho mỗi actor, trừ `USER` phải chia nhỏ vì có 69 use case.

| # | Sơ đồ | Actor | Số UC | Ghi chú |
|---|---|---|---|---|
| 1 | Khách chưa đăng nhập | `GUEST` | 16 | |
| 2 | Người học — học và luyện | `USER` | ~25 | Gồm UC-126 → UC-129 |
| 3 | Người học — thi và tiến độ | `USER` | ~20 | Gồm UC-130, UC-131 |
| 4 | Người học — thư viện, cộng đồng, điểm | `USER` | ~24 | Gồm UC-132 → UC-134, UC-136 |
| 5 | Kiểm duyệt nội dung | `TEACHER` · `CONTENT_ADMIN` · `MANAGER` | ~20 | Gồm UC-135, UC-137 |
| 6 | Quản trị tài chính | `FINANCE_ADMIN` | 4 | |
| 7 | Quản trị hệ thống | `SUPER_ADMIN` | 3 | |

**Giới hạn 30 use case mỗi sơ đồ.** Vượt mức đó thì sơ đồ không đọc được khi in
vào báo cáo, phải chia nhỏ tiếp.

`SYSTEM` không có sơ đồ riêng. Mười ba use case của nó xuất hiện trong các sơ đồ
trên, ở đầu nhận của mũi tên `«include»`.

---

## 4 · Hai loại quan hệ

| Quan hệ | Nghĩa | Phép thử |
|---|---|---|
| `«include»` | Use case gốc **luôn** gọi use case này | Bỏ nó đi, use case gốc **không chạy được** |
| `«extend»` | Use case này mở rộng use case gốc khi có điều kiện | Bỏ nó đi, use case gốc **vẫn chạy** |

### Ba quan hệ `«include»` trong dự án

```
(UC-035 Nộp bài và nhận điểm) ─────«include»───→ (UC-042 Cập nhật mức độ nắm vững)
(UC-029 Làm bài kiểm tra chủ đề) ──«include»───→ (UC-042)
(UC-088 Lưu điểm game) ────────────«include»───→ (UC-042)

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
| Vẽ `Worker`, `Scheduler`, `Caller` thành actor | Đều là `SYSTEM`. Tên đó chỉ cơ chế hiện thực, không phải actor |
| Vẽ tên module Java thành actor | `community` và `learning` là package, không phải actor |
| Vẽ `ADMIN` gộp | Đã tách thành `CONTENT_ADMIN`, `FINANCE_ADMIN`, `SUPER_ADMIN` |
| Để `GUEST` trong danh sách gán role | `GUEST` và `SYSTEM` là actor nhưng **không phải role**, không gán cho tài khoản được |
| Vẽ mũi tên `«extend»` sai hướng | Trỏ từ use case chi tiết tới use case tổng quát |
| Gộp 69 use case của `USER` vào một sơ đồ | Chia ba sơ đồ theo nhóm chức năng |

---

## 7 · Nguồn tra cứu

| Cần gì | Xem ở đâu |
|---|---|
| Danh sách tám actor và quyền | Hiến pháp, mục *Tám actor* |
| Use case nào thuộc actor nào | Bảng tra nhanh đầu mỗi file `use-cases-*.md` |
| Quan hệ của một use case tổng quát | Mục *Quan hệ use case* trong chính use case đó |
| Màn hình nào cho actor nào | `design.md` mục 3 và mục 5.3 |

**Tổng: 137 use case.** Nếu số đếm được khác con số này thì có file chưa được
cập nhật — kiểm lại trước khi vẽ.

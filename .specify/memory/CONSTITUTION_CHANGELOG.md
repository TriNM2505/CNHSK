# Lịch sử sửa đổi Hiến pháp CNHSK

> Hiến pháp ở `.specify/memory/constitution.md` chỉ chứa **rule đang hiệu lực**.
> Mọi lịch sử thay đổi ghi ở đây, để hiến pháp đủ ngắn cho AI đọc hết mà không bỏ sót.

## Quy tắc ghi

Mỗi lần sửa hiến pháp PHẢI thêm một mục vào đầu file này, gồm:

- Version cũ → version mới, loại bump (MAJOR/MINOR/PATCH)
- Ngày sửa (ISO `YYYY-MM-DD`)
- Nguồn: vì sao sửa — họp nhóm, yêu cầu giảng viên, bài học từ sự cố…
- Từng rule THÊM / SỬA / BỎ, ghi rõ mã
- Số lượng rule trước và sau mỗi lớp

## Chính sách version

| Bump | Khi nào |
|---|---|
| **MAJOR** | Bỏ hoặc định nghĩa lại nguyên tắc theo hướng không tương thích ngược |
| **MINOR** | Thêm nguyên tắc hoặc mở rộng hướng dẫn đáng kể |
| **PATCH** | Làm rõ câu chữ, sửa lỗi diễn đạt, không đổi ngữ nghĩa |

---

## v1.3.0 — 2026-10-08

**Loại bump:** MINOR — mở rộng `BUS-09` cho trường hợp chấm bằng dịch vụ bên ngoài.
Không bỏ hay định nghĩa lại rule nào; câu gốc của `BUS-09` giữ nguyên.

**Nguồn:** khảo sát schinese.net — trang tham khảo của feature 1.6 "Học qua video" —
cho thấy trang này dựa trên **Dictation + Shadowing**, không phải phụ đề tương tác như
`feature-tree.md` §1.6 mô tả. Chính §1.6 tự thừa nhận *"chưa khảo sát được
schinese.net"*. Chủ dự án quyết làm cả hai. Shadowing cần đánh giá phát âm — việc mà
`CONTEXT.md` §2.4 đã cắt vì *"cần model nhận dạng giọng, ngoài 11 tuần"*. Giả định đó
đúng cho **tự train model**, nhưng sai cho **gọi dịch vụ ngoài** (Azure Speech
Pronunciation Assessment hỗ trợ `zh-CN`, ~$1.32/giờ audio).

### SỬA `BUS-09` — thêm mục chi tiết *chấm bằng dịch vụ bên ngoài*

Câu rule gốc **không đổi**: *"Chấm điểm PHẢI ở server. Kết quả client gửi lên là dữ
liệu chưa đáng tin"*. Thêm ba điều kiện bắt buộc khi điểm do bên thứ ba chấm:

| # | Điều kiện |
|---|---|
| 1 | Dữ liệu thô đi **client → server ta → dịch vụ ngoài**. Client KHÔNG gọi trực tiếp |
| 2 | Server nhận kết quả, kiểm hợp lệ, rồi mới ghi DB. Client KHÔNG BAO GIỜ gửi điểm |
| 3 | Ghi `provider` trong bảng kết quả, để đọc lại được điểm cũ thuộc nhà cung cấp nào |

**Lý do:** nếu client gọi trực tiếp rồi gửi điểm về, người dùng sửa được điểm bằng cách
tự gọi API. Server đứng giữa là điểm kiểm duy nhất.

**Vì sao MINOR không phải MAJOR:** rule cũ vẫn đúng với mọi code hiện có. Mục mới chỉ
trả lời một câu hỏi rule cũ chưa trả lời — *"chấm ngoài thì tính sao"* — chứ không cho
phép điều gì rule cũ cấm. Client vẫn không được gửi điểm.

### Hệ quả ngoài Hiến pháp

| Tài liệu | Phải sửa |
|---|---|
| `LI-1` trong RP1 nháp | *"không chấm phát âm bằng máy"* → *"không tự xây model nhận dạng giọng; dùng dịch vụ ngoài, tính vào hạn mức lượt"* |
| `CONTEXT.md` §2.4 | Mục *"Chấm phát âm qua micro"* không còn nằm ngoài mục tiêu hoàn toàn |
| `feature-tree.md` §1.6 | Mô tả lại đúng schinese.net; thêm Dictation + Shadowing |

### Giới hạn đã biết, phải ghi vào đặc tả

Azure Pronunciation Assessment hỗ trợ `zh-CN` với điểm theo **âm tiết**, nhưng
**Prosody chỉ có ở `en-US`** và **không tài liệu hoá điểm thanh điệu riêng**. Hệ quả:
Shadowing nói được *"âm tiết này chưa đúng"* nhưng **không chỉ ra "bạn đọc thanh 2
thành thanh 3"* — đúng cái người Việt học tiếng Trung sai nhiều nhất.

Cách trả điểm và hạn mức Shadowing: chủ dự án quyết **cuối dự án** (2026-10-08).

### Số rule trước và sau

| Lớp | Trước | Sau |
|---|---|---|
| `HR-*` | 9 | 9 |
| `AC-*` | 10 | 10 |
| `BUS-*` | 15 | 15 — không thêm mã mới, chỉ mở rộng `BUS-09` |
| `ES-*` | 8 | 8 |
| **Tổng** | **42** | **42** |

---

## v1.2.0 — 2026-10-05

**Loại bump:** MINOR — thêm mục *Mô hình kinh doanh*, chốt một TODO. Không bỏ hay định
nghĩa lại rule nào.

**Nguồn:** chủ dự án mô tả mô hình kinh doanh mà **không tài liệu nào trong dự án biết**.
Quét 13 file nguồn trong `docs/reference/`: không file nào nhắc tới mô hình phễu hay
bên thứ ba phân phối mã. UC-099 "Sinh lô mã thẻ nạp" viết cho mô hình "admin tự bán".

### Thêm mục *Mô hình kinh doanh*

CNHSK là **sản phẩm phễu** cho một hệ sinh thái khác. **Tiền là tiền thật nhưng CNHSK
không thu tiền** — chủ hệ sinh thái thu, CNHSK chỉ nhận mã thẻ đã có giá trị.

Ba luồng phát mã (`credit_cards.channel`):

| `channel` | Ai nhận | Mục đích |
|---|---|---|
| `ECOSYSTEM_GIFT` | khách sẵn có của hệ sinh thái | tặng trải nghiệm — đây là phễu |
| `PARTNER_BATCH` | chủ hệ sinh thái phân phối tiếp | thu khách lẻ |
| `DIRECT` | admin tự phát | khuyến mãi, bù lỗi |

Hệ quả: giữ nguyên `HR-05` · `BUS-12` · `BUS-13` · `BUS-14` · `BUS-15` · `AC-07`
(vì tiền thật). **KHÔNG** tích hợp cổng thanh toán, webhook, đối soát (vì không thu tiền).

### TODO đã chốt

| TODO | Đáp án |
|---|---|
| `TODO(PAYMENT_SCOPE)` | **Tiền thật, thu ngoài hệ thống** — không phải "tiền thật" lẫn "giả lập", mà là cách thứ ba |

### Rule SỬA

Không sửa nội dung rule nào. Chỉ thêm mục nền và chốt TODO.

### Thống kê

| Lớp | v1.1.0 | v1.2.0 |
|---|---|---|
| Hard Rules | 9 | 9 |
| Architectural Constraints | 10 | 10 |
| Business Rules | 15 | 15 |
| Engineering Standards | 8 | 8 |
| TODO còn treo | 4 | **3** |

### Rủi ro ghi tường minh

Không có webhook nên CNHSK **không biết** mã nào bên thứ ba đã bán. File CSV lô mã rò rỉ
thì không phát hiện tự động được. Chủ dự án đã quyết **không** bắt buộc `expires_at`, nên
lớp giảm thiểu duy nhất là theo dõi tỉ lệ đổi bất thường theo `batch_id`
(index `ix_card_funnel`). Chi tiết ở `specs/001-auth-rbac/spec.md` §11.1.

### Ghi chú

Thêm `SM-05` (tỉ lệ đổi mã ≥ 30%) và `SM-06` (người đổi mã hoàn thành ≥ 1 chủ đề ≥ 40%)
vào `docs/reference/CONTEXT.md` §2.3 — hai chỉ số sống của mô hình phễu mà trước đây
không có. Sản phẩm chạy đúng kỹ thuật nhưng hai số này thấp thì phễu không hoạt động.

---

## v1.2.0 — 2026-10-06

**Loại bump:** MINOR — thêm mục nền, chốt 3 TODO. Không bỏ hay định nghĩa lại rule nào.

**Nguồn:** chủ dự án mô tả mô hình kinh doanh (2026-10-05) và chốt hai quyết định treo
(2026-10-06). Cộng với đợt kiểm tài liệu base phát hiện `ES-05` đang trỏ tới một TODO rỗng.

### Thêm mục *Mô hình kinh doanh*

CNHSK là **sản phẩm phễu** cho một hệ sinh thái khác. **Tiền là tiền thật nhưng CNHSK không
thu tiền** — chủ hệ sinh thái thu, CNHSK chỉ nhận mã thẻ đã có giá trị.

Ba luồng phát mã (`credit_cards.channel`): `ECOSYSTEM_GIFT` · `PARTNER_BATCH` · `DIRECT`.

Hệ quả: giữ `HR-05` · `BUS-12` · `BUS-13` · `BUS-14` · `BUS-15` · `AC-07` (vì tiền thật).
**KHÔNG** tích hợp cổng thanh toán, webhook, đối soát (vì không thu tiền).

### TODO đã chốt

| TODO | Đáp án | Ngày |
|---|---|---|
| `TODO(PAYMENT_SCOPE)` | **Tiền thật, thu ngoài hệ thống** — cách thứ ba, không phải "tiền thật" lẫn "giả lập" | 10-05 |
| `TODO(COVERAGE_TARGET)` | **80% toàn bộ.** `ES-05` trước đây trỏ tới TODO rỗng — agent đọc Hiến pháp không biết mức nào | 10-06 |
| `TODO(REDIS_PLACEMENT)` | **Giữ bản 5: Redis sau ứng dụng.** Redis làm cache + bảng xếp hạng (`ZREVRANK`), cả hai do *ứng dụng* gọi. Thứ đặt *giữa* client và service là reverse proxy/CDN — cache HTTP response, không chạy được `ZREVRANK`. Hai việc khác nhau | 10-06 |

### Rule SỬA

- `ES-05`: *"Mức coverage cụ thể: xem TODO(COVERAGE_TARGET)"* → **"80% line coverage"** kèm
  ghi chú: nếu tuần 8 không đạt thì xem lại con số này **trước** khi cắt feature
- Tài liệu tham chiếu: `database.md` ghi thêm *"đang được thay bằng v7: 4 schema,
  31 bảng"*
- `feature-tree.md`: **33 tính năng** (32 sau khi cắt 5.4), không phải 38 — header
  file tự ghi "38" là **sai**, đếm thật chỉ có 33 mục `N.N`

### Rule BỎ

Không bỏ rule nào.

### Thống kê

| Lớp | v1.1.0 | v1.2.0 |
|---|---|---|
| Hard Rules · Architectural · Business · Engineering | 9 · 10 · 15 · 8 | không đổi |
| **TODO còn treo** | **4** | **1** (`RATIFICATION_DATE`) |

### Ghi chú

`TODO(RATIFICATION_DATE)` là TODO **duy nhất** còn treo. Chưa phê chuẩn thì các `HR-*` về
hình thức chưa có hiệu lực ràng buộc — cần 6 thành viên xác nhận.

---

## v1.1.0 — 2026-10-02

**Loại bump:** MINOR — thêm một lớp rule mới, không bỏ hay định nghĩa lại rule nào đang có.

**Nguồn:** Playbook yêu cầu business rule có **ba tầng** — rule riêng từng feature (viết dạng
EARS trong SPEC), rule toàn dự án (Hiến pháp, mã `BUS-*`), rule dùng chung + glossary
(`constraints/business.md`). Dự án đã có tầng 1 và tầng 3 nhưng **thiếu hoàn toàn tầng 2**.

Phát hiện khi quét tự động tám file `use-cases-0*.md`: **714 dòng `BR-*`**, trong đó
15 quy tắc lặp ở nhiều nhóm tính năng. Rule lặp nhiều = rule toàn dự án, theo Playbook thì
KHÔNG viết lại trong từng SPEC.

### Rule THÊM

**Lớp mới — Business Rules (15):** `BUS-01` … `BUS-15`

| Mã | Nội dung | Số lần lặp | Nguồn chi tiết |
|---|---|---|---|
| BUS-01 | Kiểm quyền sở hữu, chống IDOR | 68 | **mới**, viết trong hiến pháp |
| BUS-02 | Cùng một transaction | 50 | `business.md` §2, §4 |
| BUS-03 | Hoàn lượt khi API ngoài lỗi | 42 | `business.md` §2 |
| BUS-04 | Lịch ôn dùng FSRS | 41 | `business.md` §4 |
| BUS-05 | Cổng chủ đề 90% | 33 | `business.md` §4, §9 |
| BUS-06 | Mask PII ở danh sách và log | 28 | `business.md` §8 |
| BUS-07 | AI sinh → `PENDING_REVIEW` | 27 | `business.md` §6 |
| BUS-08 | Giờ Việt Nam cho streak; UTC cho FSRS | 25 | `business.md` §3 |
| BUS-09 | Chấm điểm ở server | 19 | `HR-06` |
| BUS-10 | Soft delete nội dung người dùng | 14 | **mới** |
| BUS-11 | Ngưỡng tầng phát âm 80% | 11 | **mới** |
| BUS-12 | Mã thẻ chỉ lưu hash | 7 | **mới** |
| BUS-13 | Sinh mã bằng `SecureRandom` ≥16 ký tự | 6 | **mới** |
| BUS-14 | Sổ cái append-only | 4 | **mới** |
| BUS-15 | Không `DELETE` khỏi sổ cái | 3 | **mới** |

**Bảy mã là nội dung mới**, trước đây không có ở hiến pháp lẫn `business.md`:
`BUS-01` · `BUS-10` · `BUS-11` · `BUS-12` · `BUS-13` · `BUS-14` · `BUS-15`.
Tám mã còn lại chỉ **gán mã** cho rule đã tồn tại — nội dung giữ ở `business.md`, không
sao chép, để tránh hai nguồn sự thật lệch nhau.

### Rule SỬA

Không sửa nội dung rule nào. Chỉ sửa câu chữ do thêm lớp:

- Governance: "Ba lớp và cách xử lý vi phạm" → **"Bốn lớp"**, thêm dòng `BUS-*` vào bảng
- Kiểm tra tuân thủ: "không vi phạm ba lớp trên" → **"bốn lớp trên"**
- Tài liệu tham chiếu: `cnhsk-database-v5.md` (59 bảng) → `cnhsk-database-v6.md` (29 bảng),
  thêm dòng `CONTEXT.md`
  *(Ghi chú 2026-10-06: cả hai file này đã được gộp thành `database.md` — bản final không
  còn số version trong tên. Tên cũ giữ ở đây vì đây là lịch sử.)*

### Rule BỎ

Không bỏ rule nào.

### Thống kê

| Lớp | Trước | Sau |
|---|---|---|
| Hard Rules | 9 | 9 |
| Architectural Constraints | 10 | 10 |
| **Business Rules** | **0** | **15** |
| Engineering Standards | 8 | 8 |
| **Tổng** | **27** | **42** |

### TODO thêm mới

- **`BUS-16` còn trống** — chưa có quy tắc về **trọng số mastery theo nguồn chấm**.
  `hanzi-writer` 3.7.3 chấm nét ở client, server không kiểm lại được, nên mastery từ luyện
  viết mâu thuẫn trực tiếp với `BUS-09`. Cần chốt trước khi code UC-015.

### Ghi chú

Mã `BUS-*` xử lý vi phạm **ngang `AC-*`** (báo cáo và xin duyệt, cần RFC), không ngang
`HR-*`. Lý do: rule nghiệp vụ có thể cần đổi khi hiểu rõ hơn về người dùng, khác với rule
bảo mật là tuyệt đối. Riêng `BUS-12` và `BUS-13` về bản chất là rule bảo mật — nếu sau này
cần siết, nâng lên `HR-10`/`HR-11` thay vì giữ ở lớp `BUS`.

---

## v1.0.0 — 2026-09-28

**Loại bump:** MAJOR — lần phê chuẩn đầu tiên.

**Nguồn:** áp dụng quy trình Spec-Driven Development theo *Playbook: Spec-Driven &
Agent-Driven Development*. Gom các quyết định đã chốt rải rác trong `docs/` thành một
văn bản luật duy nhất.

### Rule thiết lập lần đầu

**Lớp 1 — Hard Rules (9):**

| Mã | Nội dung | Nguồn |
|---|---|---|
| HR-01 | Mật khẩu bcrypt ≥12 hoặc argon2id | Playbook |
| HR-02 | Không secret trong code/config/log | Playbook |
| HR-03 | Cookie đủ 5 thuộc tính | `kien-truc.md` §2 |
| HR-04 | CORS không dùng `*` | `kien-truc.md` §3 |
| HR-05 | CSRF token cho thao tác dính tiền | `kien-truc.md` §2.4 |
| HR-06 | Chấm điểm ở server, kiểm 3 điều kiện | `kien-truc.md` §5 |
| HR-07 | Validate input, không nối chuỗi SQL | Playbook |
| HR-08 | Không commit dữ liệu đề thi | Quyết định nhóm |
| HR-09 | Không lộ stack trace | Playbook |

**Lớp 2 — Architectural Constraints (10):** AC-01 … AC-10
Nguồn: `kien-truc.md` và `database.md`.

**Lớp 3 — Engineering Standards (8):** ES-01 … ES-08
Nguồn: báo cáo chốt stack theo máy nhóm, quyết định nhóm.

### Thống kê

| Lớp | Trước | Sau |
|---|---|---|
| Hard Rules | 0 | **9** |
| Architectural Constraints | 0 | **10** |
| Engineering Standards | 0 | **8** |

### TODO hoãn lại

- `TODO(REDIS_PLACEMENT)` — vị trí Redis, chờ hỏi giảng viên
- `TODO(PAYMENT_SCOPE)` — tiền thật hay giả lập
- `TODO(COVERAGE_TARGET)` — mức coverage
- `TODO(RATIFICATION_DATE)` — chờ cả 6 thành viên xác nhận

### Ghi chú

Cùng ngày, toàn bộ dự án đổi tên từ **Hanzicozy** sang **CNHSK**. Hanzicozy chỉ là
trang lấy ý tưởng, không phải tên sản phẩm của nhóm. Thay đổi gồm: package Java
`com.clhsk.hanzicozy` → `com.cnhsk`, class `HanzicozyApplication` → `CnhskApplication`,
database `hanzicozy_db` → `cnhsk_db`, tên miền `hanzicozy.com` → `cnhsk.com`, và tên
5 file tài liệu trong `docs/`. Build và 9/9 test pass sau khi đổi.

# CNHSK — CONTEXT.md

> **Pha 0 · Context Discovery** · SDD/ADD Playbook · **bản final**
> **Bản final** · cập nhật 2026-10-02
>
> **File này là nguồn của:**
> — **Business Goal** → SPEC.md mục 1 (Context & Goal)
> — **Business Rules ngầm** → mục 3 Functional Requirements (dạng EARS) hoặc Constitution `BUS-`
> — **Ràng buộc kỹ thuật** → SPEC.md mục 4 (NFR)
>
> **Không viết lại nội dung file này vào từng SPEC.** SPEC trích từ đây.

---

## 1 · Problem Statement

> Mô tả **nỗi đau của người dùng**, chưa nghĩ tới giải pháp (theo sách, Pha 0).

### 1.1 · Vấn đề gốc

Người Việt học tiếng Trung để thi HSK gặp ba vấn đề mà các app hiện có không giải quyết:

**Vấn đề 1 — Học dàn trải, không biết mình yếu chỗ nào.**
Người học làm đề thi, được điểm, nhưng điểm tổng không cho biết nên học gì tiếp. Họ học lại
từ đầu cả chương hoặc đoán chỗ yếu. Kết quả: thời gian học không tỉ lệ với tiến bộ.

**Vấn đề 2 — Quên đúng lúc hệ thống không nhắc.**
Trí nhớ về chữ Hán rơi nhanh. Các app nhắc ôn theo lịch cố định (mỗi ngày, mỗi tuần) nên
nhắc cả những thứ đã nhớ chắc, và bỏ qua thứ sắp quên. Người học mất thời gian ôn phần dễ.

**Vấn đề 3 — Chơi game và học là hai việc rời nhau.**
Các app có game nhưng điểm game không ảnh hưởng tiến độ học. Người học coi game là giải trí,
không phải học — nên không chơi, hoặc chơi mà không có ích.

### 1.2 · Quy mô vấn đề

> ⚠️ **Chưa có số liệu định lượng.** Đây là điểm yếu của Problem Statement này.
> Sách yêu cầu *"vấn đề thật, có số liệu"* (ví dụ "340 support tickets/tháng").
> Dự án chưa có người dùng thật nên chưa có số.

Số liệu **có thể lấy được** để bổ sung:

| Cần đo | Cách lấy | Dùng cho |
|---|---|---|
| Tỉ lệ người học bỏ giữa khoá | Khảo sát nhóm học HSK trong trường | Chứng minh vấn đề 1 |
| Thời gian học trung bình để đạt HSK 3 | Phỏng vấn 10–20 người đã thi | Đặt Success Metric |
| Số app học tiếng Trung đã thử rồi bỏ | Khảo sát | Chứng minh vấn đề 2, 3 |

**Việc cần làm:** khảo sát 20–30 người học HSK trước khi bảo vệ, để Problem Statement có số.

### 1.3 · Ai gặp vấn đề này

| Nhóm | Đặc điểm | Nhu cầu chính |
|---|---|---|
| Sinh viên học tiếng Trung | Tự học, ít thời gian, thi HSK để xin học bổng | Biết chính xác còn thiếu gì để đạt mốc |
| Người đi làm học thêm | Học buổi tối, dễ quên vì ngắt quãng | Nhắc ôn đúng lúc, học được 15 phút/lần |
| Người đã học nhưng chững lại | Biết nhiều từ nhưng điểm thi không lên | Phân tích lỗi sai, chỉ rõ điểm yếu |

---

## 2 · Business Goal

> Nguồn cho SPEC.md mục 1. Mỗi SPEC phải ghi rõ nó **liên kết với business goal nào**.

### 2.1 · Bốn mục tiêu dự án

| Mã | Business Goal | Đo bằng gì |
|---|---|---|
| **BG-01** | Người học biết **chính xác** mình yếu điểm kiến thức nào, không phải đoán | Sau mỗi bài thi, hệ thống chỉ ra 3–5 điểm yếu kèm nút luyện ngay |
| **BG-02** | Giảm thời gian ôn phần đã nhớ chắc, tăng thời gian cho phần sắp quên | Lịch ôn FSRS giãn tới 365 ngày cho mục đã thuộc, rút về 1 ngày cho mục vừa sai |
| **BG-03** | Chơi game **cũng là** học — điểm game ảnh hưởng tiến độ ngay | Mastery đổi trong cùng transaction với lúc lưu điểm, không chờ job đêm |
| **BG-04** | Nội dung do giảng viên và AI sinh đều **qua kiểm duyệt** trước khi tới người học | Câu AI ở `PENDING_REVIEW` tới khi `TEACHER` duyệt; có ràng buộc DB chặn |

### 2.2 · Mục tiêu của đồ án (khác mục tiêu sản phẩm)

Đây là đồ án tốt nghiệp, nên có mục tiêu song song:

| Mã | Mục tiêu đồ án | Đo bằng gì |
|---|---|---|
| **BG-05** | Demo được **toàn bộ** luồng học → thi → phân tích → luyện lại | Một tài khoản demo đi hết luồng không bị chặn |
| **BG-06** | Giải thích được **6 điểm bảo mật thanh toán** khi bảo vệ | Có ràng buộc DB + test chứng minh từng điểm |
| **BG-07** | Hoàn thành trong **11–12 tuần** với 6 người, 20h/người/tuần | ~1.400 giờ người; WBS 66 đầu việc |

### 2.3 · Success Metric

| Mã | Chỉ số | Mốc | Đo khi nào |
|---|---|---|---|
| **SM-01** | Người học hoàn thành một chủ đề (đạt 90%) | ≥ 70% người bắt đầu chủ đề | Sau 2 tuần demo |
| **SM-02** | Điểm bài thi lần 2 cao hơn lần 1 cùng đề | ≥ 60% trường hợp | Sau 10 lượt thi lại |
| **SM-03** | Mastery sau chơi game đổi trong | < 1 giây | Test tự động |
| **SM-04** | Tỉ lệ câu AI bị `TEACHER` từ chối | 10–30% | Sau 100 câu AI sinh |
| **SM-05** | **Tỉ lệ đổi mã quà tặng** (`USED / tổng` theo `campaign`) | ≥ 30% | Sau mỗi đợt phát mã |
| **SM-06** | Người đổi mã quà tặng hoàn thành ≥ 1 chủ đề | ≥ 40% | Sau 2 tuần kể từ đợt phát |

> **Vì sao SM-04 không phải 0%.** Nếu không câu nào bị từ chối thì hoặc AI hoàn hảo (khó tin),
> hoặc người duyệt đang duyệt mù. Tỉ lệ 10–30% là dấu hiệu kiểm duyệt đang thật sự làm việc.

> **SM-05 và SM-06 là chỉ số sống của mô hình phễu.** CNHSK là **sản phẩm phễu** cho một hệ
> sinh thái khác — tiền là tiền thật nhưng CNHSK không thu, chủ hệ sinh thái thu rồi phát mã
> thẻ. Xem Hiến pháp mục *Mô hình kinh doanh*.
> Tặng 1.000 mã mà không ai đổi (SM-05 thấp) hoặc đổi rồi không học (SM-06 thấp) thì phễu
> **không hoạt động**, dù sản phẩm chạy đúng về kỹ thuật. Đây là hai chỉ số mà báo cáo quản
> trị phải trả lời được.

### 2.4 · Không thuộc mục tiêu

Ghi rõ để tránh phình scope:

| Không làm | Vì sao |
|---|---|
| Chấm phát âm qua micro | Cần model nhận dạng giọng, ngoài 11 tuần |
| Mô phỏng kỳ thi HSK thật | Đếm ngược chuẩn, chống gian lận khi thi — không đủ thời gian |
| Gia sư trong hệ thống | Không phải thế mạnh sản phẩm; dùng bên thứ ba |
| Mạng xã hội đầy đủ | Blog có kiểm duyệt là đủ; không làm chat, không làm chặn người |

---

## 3 · Domain Knowledge

> Theo sách: nơi ghi **"các quy tắc nghiệp vụ bất thành văn"**.
> Anti-pattern số 4 *Implicit Assumption* — mọi rule mà con người coi là "hiển nhiên"
> phải viết tường minh ở đây, nếu không AI sẽ đoán sai.

### 3.1 · Từ vựng miền (Domain Glossary)

| Thuật ngữ | Nghĩa trong dự án này | Dễ hiểu sai thành |
|---|---|---|
| **HSK** | Kỳ thi năng lực tiếng Trung, 6 cấp (HSK 1 dễ nhất). Từ 2021 có HSK 7–9 | Không phải thang điểm |
| **Điểm kiến thức** (knowledge point) | Đơn vị nhỏ nhất đo được: một từ, một điểm ngữ pháp, một cặp âm dễ lẫn | Không phải "bài học" hay "chương" |
| **Mastery** | Mức thành thạo **một điểm kiến thức** của **một người**, 0–1 | Không phải điểm bài thi |
| **FSRS** | Thuật toán nhắc ôn: tính ngày sắp quên từ `stability` và `difficulty` | Không phải Leitner (hộp cố định) |
| **Chủ đề** (topic) | Nhóm từ theo ngữ cảnh: Gia đình, Nhà hàng… Có quan hệ tiên quyết | Không phải cấp HSK |
| **Cổng 90%** | Ngưỡng hoàn thành chủ đề để mở chủ đề tiếp | Không phải điểm thi |
| **Lượt** (credit) | Đơn vị tính phí cho 4 tính năng gọi API ngoài | Không phải điểm học tập |
| **Bộ thủ** (radical) | Thành phần cấu tạo chữ Hán, dùng để tra chữ không biết đọc | Không phải nét |
| **Pinyin** | Phiên âm Latin có dấu thanh: `hǎo`. Có 4 thanh + thanh nhẹ | Dấu thanh **là** phần nghĩa, không phải trang trí |
| **Âm Hán-Việt** | Cách người Việt đọc chữ Hán: 好 = "hảo" | Không phải nghĩa, không phải pinyin |

### 3.2 · Rule ngầm về tiếng Trung — AI sẽ đoán sai nếu không ghi

| # | Rule | Vì sao phải ghi tường minh |
|---|---|---|
| **DK-01** | Tiếng Trung **viết liền không khoảng trắng**. Tách từ là bài toán có nhiều cách đúng | `中国人` tách được thành `中国`+`人` hoặc `中`+`国人`. Tách sai → hiện nghĩa sai → **dạy sai** |
| **DK-02** | Nhiều chữ **cùng pinyin khác nghĩa**: 是 và 事 đều đọc `shì` | Bài nghe "chọn chữ đúng" với hai đáp án đồng âm là **không có đáp án đúng** |
| **DK-03** | Nhiều từ **cùng nghĩa Việt**: 爸爸 · 父亲 · 爹 đều là "bố" | Sinh câu trắc nghiệm với hai đáp án đồng nghĩa → câu vô nghĩa |
| **DK-04** | **Dấu thanh đổi nghĩa hoàn toàn**: `mā` (mẹ) · `mǎ` (ngựa) · `mà` (mắng) | Bỏ dấu thanh khi so đáp án là chấm sai |
| **DK-05** | Thứ tự nét có **quy tắc chuẩn**, viết sai thứ tự vẫn ra hình đúng | Không thể chấm bằng cách so hình — phải so từng nét |
| **DK-06** | Người Việt hay lẫn **zh/z · ch/c · sh/s** | Đây là điểm yếu đặc thù, không phải lỗi ngẫu nhiên — nên có nhãn kiến thức riêng |
| **DK-07** | Chữ **giản thể** (Trung Quốc) khác **phồn thể** (Đài Loan, Hồng Kông) | HSK dùng giản thể. Nhập dữ liệu phồn thể là sai toàn bộ |

### 3.3 · Rule ngầm về học tập

| # | Rule | Hệ quả nếu bỏ qua |
|---|---|---|
| **DK-08** | Người học **tự đánh giá** không đáng tin bằng bài có đáp án | Flashcard nhận `rating` từ người học được; bài trắc nghiệm thì **server tự tính** |
| **DK-09** | Trả lời **quá nhanh** là dấu hiệu bấm bừa, không phải giỏi | `duration_ms` dưới ngưỡng → giảm trọng số mastery, không tính full |
| **DK-10** | Học nhiều lần một lúc **không** bằng học rải nhiều ngày | Giới hạn lượt/giờ không phải để chặn người dùng, là để bảo vệ hiệu quả học |
| **DK-11** | "Đã mở" thì **không bao giờ đóng lại** | Làm lại chủ đề được điểm thấp hơn mà đóng chủ đề sau = người học mất tiến độ đã có |
| **DK-12** | Ngày học tính theo **giờ Việt Nam**, không phải UTC | Học 06:00 giờ VN = 23:00 UTC hôm trước → streak đứt oan |

### 3.4 · Rule ngầm về vận hành

| # | Rule | Vì sao |
|---|---|---|
| **DK-13** | Dữ liệu đề thi là **của giảng viên**, có bản quyền | Không commit vào git, không để người học chia sẻ lại |
| **DK-14** | Mã thẻ nạp **bán qua Zalo**, không qua cổng thanh toán | Không có webhook xác nhận — phải tự chống dùng lại mã |
| **DK-15** | Nhóm 6 người **làm song song**, nhiều người sửa cùng lúc | Leader giữ quyền đánh số migration; API-First để FE/BE chạy song song |
| **DK-16** | Người duyệt câu AI là **lớp bảo vệ duy nhất** | AI viết câu đúng format nhưng sai kiến thức — không validate được bằng code |
| **DK-17** | `GUEST` và `SYSTEM` là **actor nhưng không phải role** | Thấy chúng trong danh sách actor rồi thêm vào `user_roles` là sai. `GUEST` không có tài khoản; `SYSTEM` là job `@Scheduled` |
| **DK-18** | Quyền quản trị chia **ba**, không phải một `ADMIN` | `FINANCE_ADMIN` chạm tiền thật nên phải hẹp nhất. Gộp lại là vi phạm đặc quyền tối thiểu, và `NFR-S08` không audit riêng được |

### 3.5 · Tám actor

| Actor | Số UC | Trong `user_roles`? |
|---|---|---|
`USER` | 79 | ✅ |
`GUEST` | 16 | ❌ chưa đăng nhập |
`SYSTEM` | 14 | ❌ job định kỳ |
`CONTENT_ADMIN` · `MANAGER` · `FINANCE_ADMIN` · `TEACHER` · `SUPER_ADMIN` | 6 · 4 · 4 · 4 · 3 | ✅ |

**8 actor = 6 role trong DB + 2 actor không phải role.** Chi tiết quyền: Hiến pháp §Tám actor.

---

## 4 · Ràng buộc kỹ thuật (Tech Context)

> Nguồn cho SPEC.md mục 4 (NFR) và mục 1 (Tech Context).

### 4.1 · Stack đã chốt — không được lệch

| Phần | Phiên bản | Vì sao phiên bản này |
|---|---|---|
| Java | 17 | Nhóm đã dùng ở dự án trước |
| Spring Boot | 3.5.14 | Đã dùng thật, không phải bản mới nhất |
| PostgreSQL | 18 | Có `pg_trgm` cho tìm kiếm tiếng Việt |
| Flyway | — | Nguồn duy nhất của schema; `ddl-auto: validate` |
| React | 18.3 | Đã dùng |
| Vite | 5 | Đã dùng |
| TypeScript | 5.7 | Đã dùng |
| Tailwind | 3.4 | Đã dùng |
| jjwt | 0.12.6 | Đã dùng |

> Đổi bất kỳ mục nào cần RFC và cả nhóm duyệt.

### 4.2 · Kiến trúc

**Modular Monolith** — một ứng dụng `cnhsk-api` cổng 8080, bốn module `auth` · `learning` ·
`community` · `shared`, **một** database, **một** Redis.

| Quyết định | Vì sao |
|---|---|
| Không microservices | 6 người 11 tuần. Và cần mastery đổi **cùng transaction** với điểm game |
| Ba frontend, một backend | Web `cnhsk.com` · game `game.cnhsk.com` · mobile React Native |
| Một database | Tách DB thì cần distributed transaction cho BG-03 |
| Module gọi nhau qua lớp `api` | `ModuleBoundaryTest` (ArchUnit) canh, không dựa vào người review |

### 4.3 · Ba client, ba cách xác thực

| Client | Địa chỉ | Token gửi bằng |
|---|---|---|
| Web chính | `cnhsk.com` | **Cookie** · `Domain=cnhsk.com` · `HttpOnly` · `SameSite=Lax` |
| Web game | `game.cnhsk.com` | **Cùng cookie đó** — tên miền phụ tự nhận |
| Mobile | React Native | **Header** `Authorization: Bearer` |
| Mobile chơi game | WebView → `game.cnhsk.com` | **Header** — app tiêm token trước khi trang chạy |

> `JwtFilter` đọc **cookie trước, không có thì đọc header**. Thiếu một trong hai là một loại
> client không đăng nhập được.

### 4.4 · Ràng buộc nguồn lực

| Ràng buộc | Số |
|---|---|
| Người | 6 |
| Thời gian | 11–12 tuần |
| Giờ/người/tuần | 20 |
| Tổng giờ người | ~1.400 |
| Đầu việc (WBS) | 66 |

> **Đã vượt 15% công suất** theo ước lượng WBS. Thứ tự cắt khi chậm: blog (5.1) →
> cuộc thi (5.8) → video (1.6) → game gõ pinyin (5.7).

### 4.5 · Phụ thuộc bên ngoài

| Dịch vụ | Dùng cho | Rủi ro |
|---|---|---|
| API dịch | UC-060 dịch đoạn văn | Tốn tiền mỗi lượt · có thể timeout → **phải hoàn lượt** |
| API AI sinh câu | UC-048, UC-049 | Có thể trả câu sai kiến thức → **bắt buộc người duyệt** |
| API trợ lý ảo | UC-085 | Nghiệm thu đòi trả lời dưới 5 giây |
| SMTP | UC-002 xác thực email | Lỗi SMTP **không được** chặn đăng ký |
| `hanzi-writer` 3.7.3 | UC-015 luyện viết | Chấm nét ở **client** — server không kiểm lại được |

> 🔴 **Hệ quả của dòng cuối:** mastery từ luyện viết phải có **trọng số thấp hơn** mastery từ
> bài thi (chấm ở server). Không thể tin tuyệt đối kết quả client gửi.

---

## 5 · Business Rules toàn dự án

> Theo sách, business rules có **3 tầng**. File này chỉ phát hiện và phân loại —
> nơi viết chính thức là Constitution hoặc `business.md`.

### 5.1 · Phân tầng

| Tầng | Viết ở đâu | Số lượng trong CNHSK |
|---|---|---|
| Rule riêng từng feature | EARS trong SPEC.md mục 3 · lỗi thì mục 6 | **714 dòng BR** rải trong 8 file UC |
| **Rule nghiệp vụ toàn dự án** | **Constitution, mã `BUS-01`…** | ⚠️ **Chưa có — xem §5.2** |
| Rule dùng chung + glossary | `.specify/memory/constraints/business.md` | ✅ Đã có |

### 5.2 · Mười lăm rule lặp nhiều nhất — ứng viên cho mã `BUS-`

Quét tự động trên 8 file UC. **Rule lặp nhiều = rule toàn dự án**, theo sách thì không viết
lại trong từng SPEC:

| Số lần | Rule | Đề xuất mã |
|---|---|---|
| 68 | Kiểm quyền sở hữu tài nguyên trước khi đọc/sửa (chống IDOR) | `BUS-01` |
| 50 | Các thao tác liên quan phải trong **cùng một transaction** | `BUS-02` |
| 42 | Gọi API ngoài lỗi → **hoàn lượt** đã trừ | `BUS-03` |
| 41 | Lịch ôn dùng **FSRS**, không phải Leitner | `BUS-04` |
| 33 | Ngưỡng hoàn thành chủ đề: **90%** | `BUS-05` |
| 28 | Không lộ PII: email che ở danh sách, không ghi vào log | `BUS-06` |
| 27 | Nội dung AI sinh **luôn** vào `PENDING_REVIEW` | `BUS-07` |
| 25 | Ngày và giờ nhắc tính theo **giờ Việt Nam**; FSRS tính bằng UTC | `BUS-08` |
| 19 | Chấm điểm **bắt buộc ở server**, không tin client | `BUS-09` |
| 14 | Xoá là **soft delete** cho nội dung người dùng tạo | `BUS-10` |
| 11 | Ngưỡng qua tầng phát âm: **80%** | `BUS-11` |
| 7 | Mã thẻ **chỉ lưu hash**, không bao giờ lưu mã thô | `BUS-12` |
| 6 | Sinh mã bằng `SecureRandom`, ≥16 ký tự, không tuần tự | `BUS-13` |
| 4 | Sổ cái tiền là **append-only** | `BUS-14` |
| 3 | Không bao giờ xoá dòng khỏi sổ cái | `BUS-15` |

> ⚠️ **Việc cần làm:** thêm 15 mã `BUS-` này vào Constitution. Hiện Constitution chỉ có
> `HR` (11) · `AC` (11) · `ES` (10) — thiếu tầng business rule mà sách yêu cầu.

### 5.3 · Bảy quyết định nghiệp vụ còn treo

Sách nói: feature có trên 5 business rule thì phải chạy **Clarification prompt**.
Bảy mục dưới đây đã được chốt tạm trong DB v6 nhưng **cần bạn xác nhận**:

| # | Mục | Chốt tạm | Ảnh hưởng nếu đổi |
|---|---|---|---|
| 1 | "10 lượt free" là bao lâu | Vĩnh viễn mỗi tài khoản | Cần job reset nếu đổi sang theo tháng |
| 2 | Teacher nhận bao nhiêu phần | 70% | Chỉ đổi hằng số |
| 3 | Hạn nhận / hạn chấm bài | 24h / 48h | Chỉ đổi hằng số |
| 4 | Danh sách kỹ năng | 6 kỹ năng | **Đổi sau phải nhập lại dữ liệu** |
| 5 | Nơi lưu bản nháp bài thi | Cột JSONB | Đổi sang Redis thì cần chốt `TODO(REDIS_PLACEMENT)` |
| 6 | Thời hạn lưu hội thoại AI | 90 ngày | Chỉ đổi hằng số |
| 7 | Có bảng cấu hình hệ thống không | Không — hằng số trong `application.yaml` | Thêm bảng + màn quản trị nếu đổi |

> Mục 4 là mục **gấp nhất** — `question_knowledge_points` nằm trong "5 bảng không được đụng"
> với lý do *"không sửa được nếu không nhập lại dữ liệu"*.

---

## 6 · Non-Functional Requirements toàn dự án

> Nguồn cho SPEC.md mục 4. Theo sách: **bắt buộc có số**. "Nhanh" vô nghĩa.
> Chia 4 nhóm theo template Full.

### 6.1 · Performance

| Mã | Yêu cầu | Số đo | Nguồn |
|---|---|---|---|
| `NFR-P01` | Dịch câu 20 từ | < 3 giây | Nghiệm thu 4.1 |
| `NFR-P02` | Trợ lý ảo trả lời | < 5 giây | Nghiệm thu 5.5 |
| `NFR-P03` | Truy vấn "câu nào đo điểm kiến thức X" | < 100ms | Nghiệm thu 6.3 |
| `NFR-P04` | Tra thứ hạng bảng xếp hạng | < 1ms (Redis `ZREVRANK`) | Kiến trúc 5.3 |
| `NFR-P05` | Mastery đổi sau khi chơi game | < 1 giây (cùng transaction) | BG-03 · SM-03 |
| `NFR-P06` | Duyệt hàng loạt 20 mục | < 2 phút | Nghiệm thu 6.6 |

> ⚠️ **Thiếu NFR cho phần lõi.** Chưa có số cho: tải trang dashboard, nộp bài thi, tra từ điển.
> Cần bổ sung trước khi viết SPEC cho các feature đó.

### 6.2 · Security

| Mã | Yêu cầu | Số đo / quy tắc |
|---|---|---|
| `NFR-S01` | Mật khẩu | bcrypt cost ≥ 12 hoặc argon2id |
| `NFR-S02` | Mã thẻ nạp | `SecureRandom`, ≥ 16 ký tự, chỉ lưu hash |
| `NFR-S03` | Nhập mã sai | ≤ 5 lần/giờ mỗi tài khoản |
| `NFR-S04` | Thao tác đổi tiền | **Bắt buộc CSRF token** (web dùng cookie) |
| `NFR-S05` | CORS | Khai origin tường minh, **không** dùng `*` |
| `NFR-S06` | Lỗi trả về client | `{error_code, message, request_id}` — không lộ stack trace |
| `NFR-S07` | Log | Không chứa mã thẻ, mật khẩu, email người dùng |
| `NFR-S08` | Thao tác của `FINANCE_ADMIN` | Ghi audit log cả thao tác **đọc** sổ cái |

### 6.3 · Scalability

| Mã | Yêu cầu | Số đo |
|---|---|---|
| `NFR-C01` | Người dùng đồng thời (demo) | 50 |
| `NFR-C02` | Lượt làm bài/giờ mỗi người | ≤ 20 lượt bắt đầu đề/ngày |
| `NFR-C03` | Ván game/giờ mỗi người | ≤ 30 |
| `NFR-C04` | Lô mã thẻ mỗi lần sinh | ≤ 1.000 |
| `NFR-C05` | Thẻ mỗi bộ flashcard | ≤ 500 · ≤ 50 bộ/người |
| `NFR-C06` | Ghi chú mỗi người | ≤ 1.000 |

> **Con số 50 người đồng thời** là của môi trường demo, không phải sản phẩm thật.
> Nếu hội đồng hỏi về tải thật thì trả lời thẳng: chưa đo, và chưa cần đo trong scope đồ án.

### 6.4 · Availability

| Mã | Yêu cầu | Ghi chú |
|---|---|---|
| `NFR-A01` | Redis mất | Bảng xếp hạng **vẫn dựng lại được** từ `attempts` |
| `NFR-A02` | SMTP lỗi | Đăng ký **vẫn thành công** (trả 201) |
| `NFR-A03` | API AI lỗi | Hoàn lượt sau khi hết retry |
| `NFR-A04` | Uptime | ⚠️ **Chưa chốt** — đồ án không có SLA |

---

## 7 · Đối chiếu với playbook

| Pha | Sách yêu cầu | Dự án |
|---|---|---|
| **0 · Context Discovery** | CONTEXT.md | ✅ **File này** |
| 1 · Specification | SPEC.md mục 1–6, FR dạng EARS | 🔴 Chưa có `specs/` |
| 2 · Planning | plan.md qua Validation Gate | ⏸ |
| 3 · Tasks | tasks.md | ⏸ |
| 4 · Implement | Code theo task | ⏸ |
| 5 · Validation | Đối chiếu lại Constitution | ⏸ |

**Việc làm một lần, đã xong:**

| Hạng mục | Trạng thái |
|---|---|
| Constitution (3 tầng: HR · AC · ES) | ✅ 13.807 b |
| `constraints/global.md` · `business.md` · `safety.md` | ✅ 3 file |
| AGENTS.md · CLAUDE.md | ✅ |
| Mã `BUS-` trong Constitution | 🔴 **Thiếu — xem §5.2** |

---

## 8 · Ba việc cần làm trước khi sang Pha 1

| # | Việc | Vì sao cần trước |
|---|---|---|
| 1 | **Thêm 15 mã `BUS-` vào Constitution** | Tầng 2 của business rules. Không có thì mỗi SPEC viết lại 15 rule này |
| 2 | **Chốt 6 kỹ năng** (§5.3 mục 4) | Đổi sau phải nhập lại `question_knowledge_points` |
| 3 | **Lấy file mẫu đề thi từ thầy** | UC-110 là P0. Viết parser trước khi biết định dạng là viết lại |

**Việc nên làm nhưng không chặn:**

| # | Việc | Lợi ích |
|---|---|---|
| 4 | Khảo sát 20–30 người học HSK | Problem Statement có số liệu (§1.2) |
| 5 | Bổ sung NFR cho dashboard, nộp bài, tra từ điển | §6.1 hiện thiếu |
| 6 | Chốt `NFR-A04` uptime | Hoặc ghi rõ "không có SLA trong scope đồ án" |

---

## 9 · Nguồn của file này

| Tài liệu | Dùng cho mục |
|---|---|
| `feature-tree.md` | §2.4 không thuộc mục tiêu · §4.4 thứ tự cắt |
| `use-cases-01..08.md` (714 BR) | §3 rule ngầm · §5.2 quét rule lặp |
| `kien-truc.md` | §4.2 kiến trúc · §4.3 ba client |
| `database.md` | §5.3 bảy quyết định treo |
| `design.md` | §4.1 stack · §6 NFR |
| `wbs-estimate.md` | §4.4 nguồn lực |
| `.specify/memory/constitution.md` | §7 đối chiếu |

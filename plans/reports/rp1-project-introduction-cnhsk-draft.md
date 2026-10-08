# CAPSTONE PROJECT REPORT
# Report 1 — Project Introduction
# Chinese Learning and HSK Preparation Support System (CNHSK)

> **Bản nháp để copy vào mẫu Word của FPT.** Theo đúng cấu trúc RP1 của SRIS.
>
> **Ô `[CẦN ĐIỀN]`** là chỗ chỉ nhóm trả lời được — điền trước khi nộp.
>
> **Mọi số liệu đều có nguồn tra được** ở mục *List of Sources*. Kiểm lại link
> trước khi nộp; số liệu web có thể đổi.

---

## I. Record of Changes

| Date | A\*<br>M, D | In charge | Change Description |
|---|---|---|---|
| 10/2026 | A | `[CẦN ĐIỀN tên]` | Initial version of the Project Introduction report. |
| | | | |
| | | | |

\*A - Added &nbsp; M - Modified &nbsp; D - Deleted

> **Ghi chú cho nhóm:** mẫu SRIS có 3 dòng vì họ đã qua một lần phản biện hội
> đồng (07/2026 cắt tính năng chấm điểm CV tự động). Dự án ta mới bản đầu nên
> một dòng. Sau mỗi lần sửa theo góp ý giảng viên thì thêm dòng — hội đồng đọc
> bảng này để biết nhóm có tiếp thu phản biện hay không.

---

## II. Definition and Acronyms

| Acronym | Definition |
|---|---|
| **CNHSK** | Chinese Learning and HSK Preparation Support System |
| **HSK** | Hànyǔ Shuǐpíng Kǎoshì (汉语水平考试) — kỳ thi năng lực tiếng Trung chuẩn quốc tế do Chinese Testing International tổ chức, 6 cấp (HSK 1 dễ nhất đến HSK 6). Từ 2021 bổ sung HSK 7–9. **Không phải thang điểm** |
| **HSKK** | HSK Speaking — phần thi nói, tổ chức riêng với HSK |
| **Knowledge point** | *Điểm kiến thức* — đơn vị nhỏ nhất đo được trong hệ thống: một từ, một điểm ngữ pháp, hoặc một cặp âm dễ lẫn. **Không phải** "bài học" hay "chương" |
| **Mastery** | Mức thành thạo **một điểm kiến thức** của **một người học**, giá trị 0–1. **Không phải** điểm bài thi |
| **FSRS** | Free Spaced Repetition Scheduler — thuật toán nhắc ôn tính ngày sắp quên từ hai biến `stability` và `difficulty`. **Không phải** Leitner (hộp cố định) |
| **Spaced repetition** | *Ôn tập giãn cách* — phương pháp ôn lại đúng lúc trí nhớ sắp rơi, thay vì ôn theo lịch cố định |
| **Topic** | *Chủ đề* — nhóm từ theo ngữ cảnh (Gia đình, Nhà hàng…), có quan hệ tiên quyết. **Không phải** cấp HSK |
| **Cổng 90%** | Ngưỡng hoàn thành một chủ đề để mở chủ đề tiếp theo. **Không phải** điểm thi |
| **Credit** | *Lượt* — đơn vị tính phí cho các tính năng gọi API bên ngoài. **Không phải** điểm học tập |
| **Pinyin** | Phiên âm Latin có dấu thanh của tiếng Trung: `hǎo`. Có 4 thanh + thanh nhẹ. Dấu thanh **là phần nghĩa**, không phải trang trí |
| **Radical** | *Bộ thủ* — thành phần cấu tạo chữ Hán, dùng để tra chữ chưa biết cách đọc. **Không phải** nét |
| **Âm Hán-Việt** | Cách người Việt đọc chữ Hán: 好 = "hảo". **Không phải** nghĩa, không phải pinyin |
| **Modular Monolith** | Kiến trúc một ứng dụng triển khai duy nhất, chia thành các module có ranh giới được kiểm tự động — không phải microservices |
| **RBAC** | Role-Based Access Control — phân quyền theo vai trò |
| **JWT** | JSON Web Token — chuẩn token xác thực không trạng thái |
| **Funnel product** | *Sản phẩm phễu* — sản phẩm thu hút người dùng cho một hệ sinh thái khác, không tự thu tiền trực tiếp |
| **BG** | Business Goal — tiền tố mã mục tiêu kinh doanh trong tài liệu này |
| **SM** | Success Metric — tiền tố mã chỉ số thành công |
| **FE** | Feature — tiền tố mã tính năng |
| **LI** | Limitation / Exclusion — tiền tố mã giới hạn và phạm vi loại trừ |

---

## List of Sources

| # | Source owner | Description | Source |
|---|---|---|---|
| 1 | Chinese Testing International (CTI) | Cơ quan trực thuộc Bộ Giáo dục Trung Quốc, tổ chức kỳ thi HSK toàn cầu. Số liệu thí sinh HSK theo quốc gia được dẫn lại qua báo chí quốc tế | [1] |
| 2 | JobOKO | Nền tảng tuyển dụng Việt Nam. Số liệu tin tuyển dụng yêu cầu tiếng Trung | [2] |
| 3 | Udonis Mobile Marketing | Công ty phân tích thị trường ứng dụng di động. Số liệu giữ chân người dùng của các app học ngôn ngữ | [3] |
| 4 | ScienceDirect — *L2 grit and age as predictors of attrition in mobile-assisted language learning* | Nghiên cứu học thuật có phản biện về tỉ lệ bỏ giữa khoá khi học ngôn ngữ qua ứng dụng | [4] |
| 5 | Open `srs-benchmark` / tài liệu FSRS | Bộ dữ liệu mở so sánh độ chính xác các thuật toán nhắc ôn, huấn luyện trên 700 triệu lượt ôn của 20.000 người dùng | [5] |

**Link:**
[1] https://www.koreatimes.co.kr/world/20260917/from-korean-to-chinese-vietnams-language-learners-follow-the-jobs
[2] cùng nguồn [1] — JobOKO được dẫn trong bài
[3] https://www.blog.udonis.co/mobile-marketing/mobile-apps/duolingo
[4] https://www.sciencedirect.com/science/article/pii/S1041608025000809
[5] https://github.com/open-spaced-repetition/srs-benchmark

> ⚠️ **Việc nhóm phải làm:** nguồn [1] là báo *The Korea Times* dẫn lại số của
> CTI, không phải báo cáo gốc của CTI. Trước khi nộp nên tìm **công bố gốc** từ
> CTI hoặc Trung tâm Văn hoá Trung Quốc tại Việt Nam để thay. Số liệu đã được
> **xác nhận chéo** ở một nguồn thứ hai độc lập (echineselearning.com), nên con
> số đáng tin, nhưng nguồn cấp một vẫn tốt hơn khi bảo vệ.

---

## List of Figures

| Figure | Tên |
|---|---|
| Figure 1 | Tree features diagram of CNHSK System — **`[CẦN VẼ]`** |
| Figure 2 | Three-client architecture of CNHSK System |

---

# III. Project Introduction

## 1. Overview

### 1.1 Project Information

- **English name:** *Chinese Learning and HSK Preparation Support System Based on Personalized Learning Pathways for Hoc Ba Company*
- **Vietnamese name:** *Hệ thống hỗ trợ học tiếng Trung và luyện thi HSK theo lộ trình cá nhân hoá cho Công ty Học Bá*
- **Project code:** **CNHSK**
- **Group name:** `[CẦN ĐIỀN — dạng SEP490-Gxx]`
- **Software type:** Web Application + Mobile Application (React Native)

> **Lưu ý khác SRIS:** SRIS là *Web App* thuần. CNHSK có **ba client** dùng
> chung một backend: web chính `cnhsk.com`, web game `game.cnhsk.com`
> (codebase độc lập, dùng cookie chung), và ứng dụng mobile React Native mở
> web game trong WebView. Nên khai *Software type* là cả hai.

### 1.2 Project Purpose

Mục đích chính của CNHSK là xây dựng một nền tảng học tiếng Trung và luyện thi
HSK **cá nhân hoá theo điểm kiến thức**, giúp người học Việt Nam biết chính xác
mình yếu chỗ nào và được nhắc ôn đúng lúc trí nhớ sắp rơi, thay vì học dàn trải
theo giáo trình cố định.

Dự án tập trung vào **bốn mục tiêu sản phẩm**, mỗi mục tiêu có cách đo được
định nghĩa trước:

| Mã | Business Goal | Đo bằng gì |
|---|---|---|
| **BG-01** | Người học biết **chính xác** mình yếu điểm kiến thức nào, không phải đoán | Sau mỗi bài thi, hệ thống chỉ ra 3–5 điểm yếu kèm nút luyện ngay |
| **BG-02** | Giảm thời gian ôn phần đã nhớ chắc, tăng thời gian cho phần sắp quên | Lịch ôn FSRS giãn tới 365 ngày cho mục đã thuộc, rút về 1 ngày cho mục vừa sai |
| **BG-03** | Chơi game **cũng là** học — điểm game ảnh hưởng tiến độ ngay | Mastery đổi trong cùng transaction với lúc lưu điểm, không chờ job đêm |
| **BG-04** | Nội dung do giảng viên và AI sinh đều **qua kiểm duyệt** trước khi tới người học | Câu AI ở trạng thái `PENDING_REVIEW` tới khi giảng viên duyệt; có ràng buộc database chặn |

Vì đây là đồ án tốt nghiệp, dự án có thêm **ba mục tiêu học thuật** song song:

| Mã | Mục tiêu đồ án | Đo bằng gì |
|---|---|---|
| **BG-05** | Demo được **toàn bộ** luồng học → thi → phân tích → luyện lại | Một tài khoản demo đi hết luồng không bị chặn |
| **BG-06** | Giải thích được **sáu điểm bảo mật thanh toán** khi bảo vệ | Có ràng buộc database kèm test chứng minh từng điểm |
| **BG-07** | Hoàn thành trong **11–12 tuần** với 6 người, 20 giờ/người/tuần | ≈1.400 giờ người; WBS 66 đầu việc |

**Success Metric** — chỉ số nghiệm thu sau khi demo:

| Mã | Chỉ số | Mốc | Đo khi nào |
|---|---|---|---|
| **SM-01** | Người học hoàn thành một chủ đề (đạt 90%) | ≥ 70% người bắt đầu chủ đề | Sau 2 tuần demo |
| **SM-02** | Điểm bài thi lần 2 cao hơn lần 1 cùng đề | ≥ 60% trường hợp | Sau 10 lượt thi lại |
| **SM-03** | Mastery sau chơi game đổi trong | < 1 giây | Test tự động |
| **SM-04** | Tỉ lệ câu hỏi AI bị giảng viên từ chối | 10–30% | Sau 100 câu AI sinh |
| **SM-05** | Tỉ lệ đổi mã quà tặng theo chiến dịch | ≥ 30% | Sau mỗi đợt phát mã |
| **SM-06** | Người đổi mã quà tặng hoàn thành ≥ 1 chủ đề | ≥ 40% | Sau 2 tuần kể từ đợt phát |

> **Vì sao SM-04 không phải 0%.** Nếu không câu nào bị từ chối thì hoặc AI hoàn
> hảo — khó tin — hoặc người duyệt đang duyệt mù. Tỉ lệ 10–30% là dấu hiệu khâu
> kiểm duyệt đang thật sự làm việc.

### 1.3 Project Stakeholders

| Full Name | Role | Email | Mobile |
|---|---|---|---|
| `[CẦN ĐIỀN]` | Supervisor | `[CẦN ĐIỀN]@fpt.edu.vn` | |
| `[CẦN ĐIỀN]` | Member | `[CẦN ĐIỀN]@fpt.edu.vn` | `[CẦN ĐIỀN]` |
| `[CẦN ĐIỀN]` | Member | `[CẦN ĐIỀN]@fpt.edu.vn` | `[CẦN ĐIỀN]` |
| `[CẦN ĐIỀN]` | Member | `[CẦN ĐIỀN]@fpt.edu.vn` | `[CẦN ĐIỀN]` |
| `[CẦN ĐIỀN]` | Member | `[CẦN ĐIỀN]@fpt.edu.vn` | `[CẦN ĐIỀN]` |
| `[CẦN ĐIỀN]` | Member | `[CẦN ĐIỀN]@fpt.edu.vn` | `[CẦN ĐIỀN]` |
| `[CẦN ĐIỀN]` | Member | `[CẦN ĐIỀN]@fpt.edu.vn` | `[CẦN ĐIỀN]` |

> **Nhóm 6 người.** CNHSK còn có một bên liên quan mà SRIS không có: **Công ty
> Học Bá** là khách hàng đặt bài, và sản phẩm là **phễu** cho hệ sinh thái của
> họ. Nên xem xét thêm một dòng cho người đại diện phía công ty, nếu giảng viên
> yêu cầu ghi rõ khách hàng.

---

## 2. Product Background

Nhu cầu học tiếng Trung tại Việt Nam đang ở mức cao nhất thế giới tính theo số
người dự thi chứng chỉ. Theo số liệu của Chinese Testing International được dẫn
lại trên báo chí quốc tế, năm 2025 Việt Nam có **146.000 thí sinh dự thi HSK —
cao nhất toàn cầu**, vượt Thái Lan (130.000) và gấp hơn hai lần Hàn Quốc
(60.000) [1]. Cùng năm, nền tảng tuyển dụng JobOKO ghi nhận **12.997 tin tuyển
dụng yêu cầu tiếng Trung, tăng 49% so với 2024** [2]; nhu cầu nhân lực biết
tiếng Trung tăng **95% trong giai đoạn 2023–2025**, và vị trí yêu cầu tiếng
Trung có mức lương cao hơn 10–40% so với vị trí tương đương [1].

Chính sách giáo dục cũng đẩy nhu cầu này lên. Từ 2024, tiếng Trung là một trong
các ngoại ngữ tự chọn bắt buộc ở bậc tiểu học, và **chứng chỉ HSK cấp 3 được
miễn thi môn ngoại ngữ trong kỳ thi tốt nghiệp trung học phổ thông** [1]. Hơn
600 trường tiểu học và trung học tại Việt Nam đã đưa tiếng Trung vào chương
trình [1]. Điểm chuẩn ngành tiếng Trung thuộc nhóm cao nhất: Trường Đại học
Ngoại ngữ — ĐHQGHN lấy 25,65/30, Trường Đại học Hà Nội lấy 34,20/40 [1].

Nhu cầu lớn nhưng **tỉ lệ bỏ giữa khoá của hình thức tự học qua ứng dụng rất
cao**. Số liệu ngành ứng dụng học ngôn ngữ cho thấy **80% người dùng bỏ trong
tuần đầu**, và gần 50% bỏ trong vòng 7 ngày [3]. Với Duolingo — ứng dụng học
ngôn ngữ lớn nhất thế giới — tỉ lệ còn dùng sau 30 ngày là **12,0%**, sau một
năm còn **6,8%** [3]. Nghiên cứu học thuật về người học qua ứng dụng ghi nhận tỉ
lệ bỏ **64% sau bốn tháng và 87% sau một năm** [4].

Con số này cho thấy vấn đề không nằm ở động lực ban đầu — 146.000 người đã bỏ
tiền và thời gian đi thi thật — mà ở **quá trình học giữa chừng**. Quan sát cách
người học Việt Nam tự học tiếng Trung cho thấy ba điểm vỡ cụ thể:

**Thứ nhất, người học không biết mình yếu chỗ nào.** Họ làm đề thi và nhận được
một điểm tổng. Điểm tổng cho biết đã đạt hay chưa, nhưng **không cho biết nên
học gì tiếp**. Hệ quả là người học hoặc ôn lại từ đầu cả chương, hoặc đoán chỗ
yếu theo cảm giác. Thời gian học không tỉ lệ với tiến bộ.

**Thứ hai, hệ thống nhắc ôn không khớp với lúc trí nhớ rơi.** Trí nhớ về chữ Hán
rơi nhanh, và rơi với tốc độ khác nhau ở từng chữ, từng người. Các ứng dụng hiện
có nhắc ôn theo lịch cố định — mỗi ngày, mỗi tuần — nên nhắc cả những chữ đã nhớ
chắc, và bỏ qua chữ sắp quên. Người học dành thời gian ôn phần dễ. Đây là vấn đề
có lời giải đã được chứng minh: thuật toán FSRS, huấn luyện trên 700 triệu lượt
ôn của 20.000 người dùng, cho sai số dự đoán khả năng nhớ lại thấp hơn rõ rệt so
với thuật toán SM-2 truyền thống, và **giảm 20–30% số lần ôn ở cùng một mức giữ
kiến thức** [5]. Nhưng thuật toán này nằm trong công cụ flashcard rời, không gắn
với bài thi HSK hay lộ trình học.

**Thứ ba, trò chơi và việc học là hai việc rời nhau.** Các ứng dụng có phần game
nhưng điểm game không ảnh hưởng tiến độ học. Người học coi game là giải trí chứ
không phải học — nên hoặc không chơi, hoặc chơi mà không thu được gì cho kỳ thi.

Ba điểm vỡ này có một đặc điểm chung: **mọi bước đều đang được thực hiện, nhưng
không bước nào được ghi lại theo đơn vị đủ nhỏ để hành động được**. Người học
làm bài, ôn tập, chơi game — nhưng dữ liệu không quy về *điểm kiến thức*, nên
hệ thống không thể trả lời câu hỏi "hôm nay nên học gì".

Nhóm người gặp vấn đề này rõ nhất:

| Nhóm | Đặc điểm | Nhu cầu chính |
|---|---|---|
| Sinh viên học tiếng Trung | Tự học, ít thời gian, thi HSK để xin học bổng | Biết chính xác còn thiếu gì để đạt mốc |
| Người đi làm học thêm | Học buổi tối, dễ quên vì ngắt quãng | Nhắc ôn đúng lúc, học được 15 phút mỗi lần |
| Người đã học nhưng chững lại | Biết nhiều từ nhưng điểm thi không lên | Phân tích lỗi sai, chỉ rõ điểm yếu |

Những điều kiện trên cho thấy nhu cầu về một nền tảng **không thay đổi cách
người học học, mà đưa cấu trúc vào quá trình họ đang làm** — quy mọi hoạt động
về điểm kiến thức, tính lịch ôn theo tốc độ quên thật của từng người, và cho
trò chơi đóng góp vào cùng một thước đo tiến độ.

> `[CẦN ĐIỀN — tuỳ chọn]` `CONTEXT.md` §1.2 của dự án đã ghi việc cần làm là
> khảo sát 20–30 người học HSK. Nếu nhóm làm được khảo sát này trước khi bảo vệ
> thì bổ sung vào đây một đoạn số liệu **của chính nhóm** — loại số liệu không
> ai bác được, và hội đồng rất coi trọng.

---

## 3. Existing Solutions

### 3.1 Duolingo

**Brief Description:** Ứng dụng học ngôn ngữ lớn nhất thế giới, theo mô hình
game hoá với chuỗi ngày học (streak), tim (hearts) và bảng xếp hạng. Khoá tiếng
Trung phổ thông nằm trong số hơn 40 ngôn ngữ được hỗ trợ.

**Link:** https://www.duolingo.com/course/zh-CN/en/Learn-Chinese

`[CẦN CHỤP ẢNH màn hình trang chủ khoá tiếng Trung]`

**System actor:**
- Người học tự học, phần lớn ở trình độ mới bắt đầu
- Người học trả phí (Super / Max)
- Không có vai trò giảng viên hay người kiểm duyệt nội dung

**Pros:**
- Cơ chế game hoá hiệu quả nhất thị trường: chiến lược chuỗi ngày học được ghi
  nhận nâng tỉ lệ giữ chân từ 12% lên 55% [3]
- Miễn phí phần lớn nội dung, rào cản bắt đầu gần như bằng không
- Khoá học được thiết kế bài bản cho người mới, có nhận dạng giọng nói
- Nội dung phong phú: khoảng 1.500 từ và 2.500 câu

**Cons:**
- **Không gắn với HSK.** Khoá tiếng Trung không theo danh sách từ của HSK; học
  hết khoá tương đương khoảng HSK 3, chạm nhẹ từ vựng HSK 4 — trong khi kỳ thi
  HSK có danh sách chữ riêng mà người học phải thuộc để đạt
- **Không dạy ngữ pháp đủ để thi.** Đề HSK yêu cầu nắm quy tắc ngữ pháp mà
  Duolingo không trình bày tường minh
- **Không cho biết người học yếu điểm kiến thức nào.** Hệ thống báo đúng/sai
  từng câu nhưng không quy về đơn vị kiến thức để chỉ ra chỗ cần luyện lại
- Điểm game và chuỗi ngày không phản ánh năng lực thi: giữ chuỗi 365 ngày vẫn
  có thể không đạt HSK 4
- Tỉ lệ giữ chân dài hạn thấp: 12,0% sau 30 ngày, 6,8% sau một năm [3]

### 3.2 HelloChinese

**Brief Description:** Ứng dụng chuyên cho tiếng Trung phổ thông, lộ trình bám
theo cấp HSK, có game luyện thanh điệu và nhận dạng giọng nói — hai thứ phần
lớn đối thủ không có.

**Link:** https://www.hellochinese.cc/

`[CẦN CHỤP ẢNH màn hình lộ trình HSK]`

**System actor:**
- Người học tiếng Trung từ mới bắt đầu đến trung cấp
- Người học trả phí Premium / Premium+
- Không có vai trò giảng viên hay kiểm duyệt nội dung

**Pros:**
- **Lộ trình bám cấp HSK** — điểm Duolingo không có
- Game luyện thanh điệu và nhận dạng giọng nói đánh dấu chỗ phát âm sai
- Có luyện viết chữ Hán theo nét
- Bản miễn phí dùng được thật, không chỉ để dùng thử
- Được nhiều bảng xếp hạng 2026 đánh giá là ứng dụng tổng hợp tốt nhất cho
  người mới bắt đầu học tiếng Trung

**Cons:**
- **Nội dung mỏng dần sau HSK 2 mới** (tương đương HSK 4 cũ); phần trình độ cao
  nằm sau tường phí
- **Không có đề thi HSK đầy đủ** và không phân tích lỗi sai theo điểm kiến thức
  sau khi làm bài
- Lịch ôn không theo thuật toán dự đoán tốc độ quên của từng người
- Giao diện và nội dung **thiết kế cho người học tiếng Anh**; người Việt không
  có âm Hán-Việt — cầu nối quan trọng nhất khi người Việt học chữ Hán
- Không có cơ chế cho giảng viên đưa nội dung hoặc kiểm duyệt

### 3.3 Anki

**Brief Description:** Công cụ flashcard mã nguồn mở theo phương pháp ôn tập
giãn cách, dùng rộng rãi trong giới học ngôn ngữ và sinh viên y khoa. Từ phiên
bản 23.10 hỗ trợ thuật toán FSRS.

**Link:** https://apps.ankiweb.net/

`[CẦN CHỤP ẢNH màn hình bộ thẻ tiếng Trung]`

**System actor:**
- Người tự học có kỷ luật cao, thường đã có kinh nghiệm học ngôn ngữ
- Người tạo và chia sẻ bộ thẻ cho cộng đồng
- Không có vai trò giảng viên, không có nội dung chính thức

**Pros:**
- **Thuật toán nhắc ôn tốt nhất hiện có.** FSRS huấn luyện trên 700 triệu lượt
  ôn của 20.000 người dùng, sai số dự đoán thấp hơn rõ rệt so với SM-2, giảm
  20–30% số lần ôn ở cùng mức giữ kiến thức [5]
- Miễn phí, mã nguồn mở, dữ liệu thuộc về người dùng
- Kho bộ thẻ cộng đồng rất lớn, có sẵn bộ từ vựng HSK các cấp
- Tuỳ biến gần như không giới hạn

**Cons:**
- **Chỉ là công cụ ghi nhớ, không phải hệ thống học.** Không có bài học, không
  có đề thi, không có lộ trình — người học phải tự biết mình cần học gì
- **Người học phải tự tạo nội dung** hoặc tự chọn bộ thẻ. Chất lượng bộ thẻ cộng
  đồng không được kiểm duyệt, nhiều bộ sai pinyin hoặc sai nghĩa
- **Không có đề thi HSK**, nên không trả lời được câu hỏi "tôi đã đạt chưa"
- Rào cản sử dụng cao: giao diện kỹ thuật, nhiều tham số cấu hình — phần lớn
  người học tiếng Trung ở Việt Nam bỏ sau vài ngày
- Không có game, không có yếu tố duy trì động lực

### 3.4 So sánh với CNHSK

| | Duolingo | HelloChinese | Anki | **CNHSK** |
|---|---|---|---|---|
| Bám cấp HSK | ✗ | ✓ | ✗ | **✓** |
| Đề thi HSK đầy đủ | ✗ | ✗ | ✗ | **✓ HSK 1–6** |
| Phân tích lỗi theo điểm kiến thức | ✗ | ✗ | ✗ | **✓** |
| Thuật toán nhắc ôn theo tốc độ quên | ✗ | ✗ | **✓ FSRS** | **✓ FSRS** |
| Game ảnh hưởng tiến độ học | ✗ game riêng | ✗ | ✗ | **✓ cùng transaction** |
| Giảng viên kiểm duyệt nội dung | ✗ | ✗ | ✗ | **✓** |
| Âm Hán-Việt cho người Việt | ✗ | ✗ | tuỳ bộ thẻ | **✓** |
| Miễn phí dùng được | ✓ | ✓ | ✓ | **✓ 10 lượt/tính năng/tháng** |

**Khoảng trống chung của cả ba:** không công cụ nào vừa **bám kỳ thi HSK**, vừa
**quy mọi hoạt động học về điểm kiến thức** để biết người học yếu chỗ nào, vừa
**dùng thuật toán nhắc ôn theo tốc độ quên thật**. Duolingo mạnh ở động lực
nhưng không gắn kỳ thi; HelloChinese bám HSK nhưng không phân tích lỗi; Anki có
thuật toán tốt nhất nhưng không phải hệ thống học. CNHSK nhằm đúng vào chỗ giao
nhau còn trống này.

---

## 4. Solution & Opportunity

CNHSK được thiết kế để lấp khoảng trống mà cả ba nhóm giải pháp hiện có để lại.
Ứng dụng học ngôn ngữ phổ thông mạnh về duy trì động lực nhưng không gắn với kỳ
thi HSK và không cho biết người học yếu điểm kiến thức nào. Ứng dụng chuyên
tiếng Trung bám cấp HSK nhưng coi bài kiểm tra là một bài tập để làm xong, chứ
không phải nguồn dữ liệu để điều chỉnh lộ trình. Công cụ ôn tập giãn cách có
thuật toán tốt nhất nhưng không biết người học cần học gì.

CNHSK đặt **điểm kiến thức** làm đơn vị trung tâm: mọi hoạt động — làm đề, học
chủ đề, luyện viết, chơi game — đều cập nhật mastery của các điểm kiến thức liên
quan trong **cùng một transaction**, và lịch ôn được tính lại từ mastery đó.
Nhờ vậy hệ thống trả lời được một câu hỏi mà cả ba đối thủ không trả lời được:
*hôm nay người học này nên học gì, và vì sao*.

### 4.1 Solutions

Hệ thống được chia thành các module, mỗi module giải quyết một điểm vỡ cụ thể
đã nêu ở mục 2:

- **Module Học và luyện tập:** Học từ theo chủ đề có quan hệ tiên quyết, với
  *cổng 90%* — phải đạt 90% một chủ đề mới mở chủ đề tiếp theo, nên người học
  không nhảy bậc rồi hụt nền. Luyện viết chữ Hán theo thứ tự nét chuẩn, luyện
  phát âm theo tám chặng từ thanh điệu đến ngữ điệu câu. Mỗi lượt trả lời cập
  nhật mastery của điểm kiến thức tương ứng.

- **Module Luyện thi HSK:** Đề thi đầy đủ HSK 1–6 với bảy dạng câu hỏi. Điểm
  **luôn do server chấm**, không tin kết quả client gửi lên. Sau khi nộp, hệ
  thống không chỉ trả điểm tổng mà **phân tích lỗi sai theo điểm kiến thức**,
  xếp hạng 3–5 điểm yếu nhất và cho nút luyện ngay phần đó — đây là `BG-01`.

- **Module Lộ trình cá nhân hoá:** Lịch ôn tính bằng FSRS từ `stability` và
  `difficulty` của từng điểm kiến thức với từng người, giãn tới 365 ngày cho
  mục đã thuộc và rút về 1 ngày cho mục vừa sai. Thuật toán này giảm 20–30% số
  lần ôn ở cùng mức giữ kiến thức so với lịch cố định [5] — đây là `BG-02`.

- **Module Thư viện tra cứu:** Tra chữ, từ, ngữ pháp với **âm Hán-Việt** — cầu
  nối mà người Việt dùng để nhớ chữ Hán và không ứng dụng quốc tế nào có. Tra
  chữ chưa biết đọc bằng bộ thủ. Thêm trực tiếp vào flashcard hoặc sổ tay cá
  nhân mà không rời ngữ cảnh đang học.

- **Module Cộng đồng và Game:** Game học từ chạy trên một web riêng
  (`game.cnhsk.com`) dùng chung đăng nhập. Điểm game **được server xác minh** và
  cập nhật mastery trong cùng transaction với lúc lưu điểm, không chờ job đêm —
  nên chơi game là học thật, đo được ngay. Đây là `BG-03`.

- **Module Quản trị và kiểm duyệt:** Câu hỏi do AI sinh nằm ở trạng thái
  `PENDING_REVIEW` tới khi giảng viên duyệt, có ràng buộc ở tầng database chặn
  nội dung chưa duyệt tới người học. Quyền quản trị tách thành bốn vai trò riêng
  theo nguyên tắc *phân tách nhiệm vụ*: giảng viên duyệt nội dung, quản trị nội
  dung nhập dữ liệu, quản trị tài chính xử lý sổ cái, quản trị cấp cao phân
  quyền. Đây là `BG-04`.

- **Module Gói dịch vụ và điểm:** Mỗi tính năng gọi API bên ngoài được cấp 10
  lượt miễn phí mỗi tháng. Hết lượt thì dùng điểm. **Điểm vào hệ thống chỉ qua
  mã thẻ** — CNHSK không tích hợp cổng thanh toán, tiền được thu bên ngoài hệ
  thống. Sổ cái điểm là *chỉ ghi thêm*: sai thì ghi giao dịch bù, không sửa
  dòng cũ.

### 4.2 Opportunity

- **Nhu cầu đã được chứng minh bằng hành vi trả tiền.** 146.000 người Việt dự
  thi HSK năm 2025 — cao nhất thế giới [1] — là những người đã bỏ tiền và thời
  gian cho một kỳ thi thật. Đây không phải nhu cầu suy đoán. Nhưng chưa có công
  cụ nào bám kỳ thi đó và phân tích được lỗi sai của người học.

- **Khoảng trống giữa ba nhóm giải pháp.** Thuật toán nhắc ôn tốt nhất (FSRS)
  đang nằm trong công cụ không có bài học. Lộ trình bám HSK đang nằm trong ứng
  dụng không có đề thi. Cơ chế duy trì động lực tốt nhất đang nằm trong ứng dụng
  không gắn kỳ thi. Ghép ba thứ này quanh một đơn vị dữ liệu chung — điểm kiến
  thức — là cơ hội kỹ thuật rõ ràng và chưa ai làm cho thị trường Việt Nam.

- **Lợi thế dành riêng cho người học Việt.** Âm Hán-Việt là cách người Việt nhớ
  chữ Hán hiệu quả nhất, và không ứng dụng quốc tế nào hỗ trợ vì nó chỉ có nghĩa
  với người Việt. Đây là lợi thế mà đối thủ quốc tế không thể sao chép mà không
  làm lại sản phẩm cho một thị trường nhỏ hơn thị trường chính của họ.

- **Chính sách giáo dục đang mở rộng thị trường.** Chứng chỉ HSK cấp 3 được miễn
  thi ngoại ngữ tốt nghiệp trung học phổ thông, và hơn 600 trường đã dạy tiếng
  Trung [1]. Nhóm người học có mục tiêu rõ ràng — đạt một cấp HSK cụ thể trước
  một mốc thời gian cụ thể — đang tăng nhanh, và đây đúng là nhóm mà lộ trình cá
  nhân hoá có giá trị nhất.

- **Mô hình phễu giảm rủi ro thương mại.** CNHSK là sản phẩm phễu cho hệ sinh
  thái của Công ty Học Bá: người dùng hệ sinh thái được tặng mã trải nghiệm, và
  mã cũng được phát qua bên thứ ba để thu thêm khách lẻ. Vì CNHSK không tự thu
  tiền, dự án không phải giải bài toán cổng thanh toán và tuân thủ tài chính —
  nguồn lực dồn vào chất lượng học tập.

---

## 5. Project Scope & Limitations

`[CẦN VẼ — Figure 1: Tree features diagram of CNHSK System]`

> **Hướng dẫn vẽ.** Sơ đồ xương cá như SRIS Figure 1, trục giữa là
> *CNHSK — Chinese Learning & HSK Preparation Support System*, bảy nhánh là bảy
> nhóm tính năng dưới đây, mỗi nhánh toả ra các tính năng con. Dữ liệu đầy đủ ở
> `docs/reference/feature-tree.md` — **33 tính năng, 7 nhóm**. Vẽ bằng draw.io
> hoặc Figma.

### 5.1 Major Features

**Nhóm 1 — Học và luyện tập**
- Luyện viết chữ Hán (5 chế độ: theo nét · nhớ rồi viết · thử thách · phát âm · nghe chép)
- Học từ theo chủ đề có quan hệ tiên quyết
- Luyện phát âm theo tám chặng
- Luyện nhận diện chữ
- Luyện nghe
- Học qua video *(V2)*

**Nhóm 2 — Luyện thi HSK**
- Đề thi đầy đủ HSK 1–6
- Luyện theo dạng câu hỏi (7 dạng)
- Luyện theo kỹ năng (nghe · đọc · viết)

**Nhóm 3 — Lộ trình cá nhân hoá**
- Cập nhật mastery sau mỗi lượt trả lời
- Lịch ôn tập giãn cách theo FSRS
- Phân tích điểm yếu theo điểm kiến thức
- Gợi ý nội dung học tiếp
- Cổng 90% mở chủ đề

**Nhóm 4 — Theo dõi tiến độ**
- Tổng quan việc cần học hôm nay
- Thống kê tiến độ 7 / 30 / 90 ngày
- Chuỗi ngày học
- Trợ lý AI theo ngữ cảnh màn hình

**Nhóm 5 — Thư viện và cộng đồng**
- Tra cứu chữ · từ · ngữ pháp · âm Hán-Việt
- Tra chữ bằng bộ thủ
- Sổ tay cá nhân
- Flashcard cá nhân
- Blog có kiểm duyệt *(V2)*
- Quiz cộng đồng *(V2)*
- Bảng xếp hạng theo chủ đề *(V2)*
- Cuộc thi có thưởng *(V2)*

**Nhóm 6 — Tài khoản, gói dịch vụ và quản trị**
- Đăng ký · đăng nhập · xác thực email · đặt lại mật khẩu
- Phân quyền sáu vai trò
- Gói dịch vụ và lượt miễn phí theo tháng
- Mã thẻ ba kênh phát hành
- Sổ cái điểm chỉ ghi thêm
- Nhập dữ liệu từ vựng và câu hỏi
- Kiểm duyệt câu hỏi do AI sinh
- Chấm bài viết theo yêu cầu

**Nhóm 7 — Game và Mobile**
- Game học từ trên web riêng *(V2)*
- Bảng xếp hạng game *(V2)*
- Ứng dụng React Native *(V2)*

> **Tổng: 33 tính năng · 7 nhóm.** Trong đó **21 tính năng thuộc phạm vi MVP**
> (hoàn thành trong 11–12 tuần) và **11 tính năng thuộc V2** (đã đặc tả, chưa
> triển khai trong đợt này). Một tính năng — *Gia sư trong hệ thống* — đã bị cắt
> khỏi phạm vi. Chi tiết từng tính năng kèm mô tả, client, phạm vi và trạng
> thái: `docs/reference/feature-tree.md`.

### 5.2 Limitations & Exclusions

**LI-1:** Hệ thống **không tự xây model nhận dạng giọng**. Tính năng Shadowing
(đọc theo video, feature 1.6) dùng **dịch vụ đánh giá phát âm bên thứ ba**, tính
vào hạn mức lượt của người học.

**Giới hạn đã biết và đã chấp nhận:** dịch vụ này chấm được độ chính xác theo
từng **âm tiết**, nhưng **không có điểm thanh điệu riêng** cho tiếng Trung — chỉ
số *Prosody* của nhà cung cấp hiện chỉ hỗ trợ tiếng Anh. Hệ quả: người học thấy
*"âm tiết này chưa đúng"* nhưng không biết cụ thể mình đọc sai thanh nào. Đây
đúng là lỗi phổ biến nhất của người Việt học tiếng Trung.

**Cách bù:** tính năng Dictation (nghe rồi gõ lại, cùng feature 1.6) chấm hoàn
toàn ở server và trả **hai điểm riêng** — điểm chữ và **điểm thanh điệu**. Hai
tính năng dùng cùng nhau thì người học biết cả *"nghe ra chưa"* và *"đọc đúng
chưa"*. Phần luyện phát âm theo tám chặng (feature 1.3) vẫn tập trung vào nghe
mẫu, mô tả cách đặt lưỡi và phần chữ tương đương, không chấm giọng.

**LI-2:** Hệ thống **không mô phỏng kỳ thi HSK thật**. Không có đếm ngược theo
đúng quy chế, không có cơ chế chống gian lận khi thi, không có phần thi nói
(HSKK). Đề thi trong hệ thống nhằm mục đích luyện tập và phân tích điểm yếu, với
dữ liệu đề do giảng viên cung cấp.

**LI-3:** Hệ thống **không có gia sư trong hệ thống**. Tính năng này đã bị cắt
khỏi phạm vi: không phải thế mạnh sản phẩm, và việc ghép lịch giữa gia sư với
người học là một bài toán điều phối riêng. Người học cần gia sư thì dùng dịch vụ
bên thứ ba.

**LI-4:** Hệ thống **không phải mạng xã hội**. Không có tin nhắn giữa người
dùng, không có kết bạn, không có chức năng chặn người. Phần cộng đồng giới hạn ở
blog có kiểm duyệt, quiz và bảng xếp hạng.

**LI-5:** Hệ thống **không tích hợp cổng thanh toán** và không xử lý webhook
thanh toán. Điểm vào hệ thống **chỉ qua mã thẻ**; tiền là tiền thật nhưng được
thu bên ngoài hệ thống theo mô hình phễu. Hệ quả đã được ghi nhận và chấp nhận:
CNHSK không biết mã nào đã được bên thứ ba bán cho ai, nên không phân biệt được
"khách hợp lệ" với "người lấy được mã rò rỉ".

**LI-6:** **Trọng số mastery từ phần luyện viết chưa được xác định.** Thư viện
chấm nét (`hanzi-writer`) chạy ở phía client, trong khi nguyên tắc của dự án yêu
cầu mọi điểm số phải do server chấm. Mâu thuẫn này chưa có lời giải, nên phần
luyện viết hiện ghi nhận hoạt động nhưng chưa đóng góp vào mastery.
`[CẦN QUYẾT ĐỊNH trước khi triển khai tính năng 1.1]`

**LI-7:** Hệ thống **không có ứng dụng mobile trong phạm vi MVP**. Ứng dụng
React Native thuộc phạm vi V2. Truy cập trên điện thoại trong MVP qua giao diện
web đáp ứng.

**LI-8:** Hệ thống **không dạy chữ Hán phồn thể**. Chỉ hỗ trợ giản thể, vì HSK
dùng giản thể.

**LI-9:** Hệ thống **không có nội dung trên HSK 6**. HSK 7–9 (ra mắt 2021) không
thuộc phạm vi.

**LI-10:** **Dữ liệu đề thi do giảng viên cung cấp không được đưa vào mã nguồn**
vì lý do bản quyền. Dữ liệu này nhập vào database qua giao diện quản trị, và bị
loại khỏi hệ thống quản lý phiên bản bằng cấu hình `.gitignore`.

**LI-11:** Hệ thống **không đồng bộ với lịch ngoài** (Google Calendar, Outlook)
và không gửi thông báo đẩy trên mobile trong MVP. Nhắc ôn qua giao diện web và
email.

---

## Phụ lục — Việc nhóm cần làm trước khi nộp

| # | Việc | Mục | Ai làm |
|---|---|---|---|
| 1 | Điền 6 họ tên, email `@fpt.edu.vn`, số điện thoại | 1.3 | Nhóm |
| 2 | Điền tên supervisor | 1.3 | Nhóm |
| 3 | Điền mã nhóm dạng `SEP490-Gxx` | 1.1 | Nhóm |
| 4 | **Vẽ Figure 1** — sơ đồ cây 33 tính năng, 7 nhóm | 5 | Nhóm |
| 5 | Chụp 3 ảnh màn hình đối thủ (Duolingo · HelloChinese · Anki) | 3.1–3.3 | Nhóm |
| 6 | Tìm **công bố gốc của CTI** thay cho nguồn báo chí dẫn lại | List of Sources | Nhóm |
| 7 | Quyết định `LI-6` — trọng số mastery từ luyện viết | 5.2 | Chủ dự án |
| 8 | *(Tuỳ chọn, tăng điểm)* Khảo sát 20–30 người học HSK | 2 | Nhóm |
| 9 | Điền Record of Changes sau mỗi lần sửa theo góp ý | I | Nhóm |

## Câu hỏi còn mở

1. **Mục 1.1 Software type** — khai *Web App* thuần như SRIS, hay *Web + Mobile*?
   Mobile thuộc V2 nên có thể giảng viên coi là ngoài phạm vi báo cáo này.
2. **Công ty Học Bá có vào bảng Stakeholders không?** Tên dự án có *"for Hoc Ba
   Company"* nên công ty là bên liên quan thật, nhưng mẫu SRIS chỉ liệt kê nhóm
   và supervisor.
3. **Mục 3 nên có 3 hay 4 đối thủ?** Hiện 3 (Duolingo · HelloChinese · Anki).
   Thêm một ứng dụng của Việt Nam sẽ cho thấy nhóm hiểu thị trường nội địa, nhưng
   cần kiểm chứng thông tin về ứng dụng đó.
4. **Số liệu mục 2 lấy từ báo chí quốc tế dẫn lại CTI.** Đã xác nhận chéo ở hai
   nguồn độc lập, nhưng nếu hội đồng hỏi nguồn cấp một thì cần công bố gốc.

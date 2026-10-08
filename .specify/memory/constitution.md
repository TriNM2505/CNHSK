<!--
SYNC IMPACT REPORT
==================
Version change: 1.2.0 → 1.3.0
Bump rationale: MINOR — mở rộng BUS-09 cho trường hợp chấm điểm bằng dịch vụ bên thứ ba (tính năng Shadowing, feature 1.6). Câu rule gốc không đổi; thêm ba điều kiện bắt buộc. Không bỏ hay định nghĩa lại nguyên tắc nào.
Lịch sử đầy đủ: CONSTITUTION_CHANGELOG.md

Nguyên tắc được định nghĩa (5):
  I.   Bảo mật là điều kiện tiên quyết (9 Hard Rules)
  II.  API First
  III. Ranh giới module bất khả xâm phạm
  IV.  Dữ liệu chính xác tuyệt đối
  V.   Không tin client

Section thêm mới:
  - Quy tắc nghiệp vụ toàn dự án (BUS-01 … BUS-15)
  - Ràng buộc kỹ thuật (ES-01 … ES-08)
  - Quy trình phát triển và Quality Gate
  - Chính sách AI Agent

Template cần đồng bộ:
  ✅ .specify/templates/plan-template.md      — đã điền gate đầy đủ 9 HR + 10 AC + 5 ES
  ✅ .specify/templates/spec-template.md      — không nhắc constitution, không mâu thuẫn
  ✅ .specify/templates/tasks-template.md     — không nhắc constitution, không mâu thuẫn
  ✅ AGENTS.md                                 — bản rút gọn luật cho agent
  ✅ CLAUDE.md                                 — bộ nhớ dự án, ADR, lessons learned
  ✅ .specify/memory/CONSTITUTION_CHANGELOG.md — lịch sử sửa đổi
  ⚠  .github/workflows/                       — chưa có CI, dựng ở giai đoạn sau

Follow-up TODOs (xem mục Ghi chú hoãn quyết định ở cuối file):
  - ~~TODO(REDIS_PLACEMENT)~~  — đã chốt 2026-10-06: giữ bản 5, Redis sau ứng dụng
  - ~~TODO(PAYMENT_SCOPE)~~    — đã chốt 2026-10-05: tiền thật, thu ngoài hệ thống
  - ~~TODO(COVERAGE_TARGET)~~  — đã chốt 2026-10-06: 80% toàn bộ
  - TODO(RATIFICATION_DATE)    — CÒN TREO, chờ 6 thành viên xác nhận
  - BUS-16 còn trống: trọng số mastery theo nguồn chấm (xem mục Quy tắc nghiệp vụ)
-->

# Hiến pháp dự án CNHSK

> Nền tảng học tiếng Trung cho người Việt · Đồ án tốt nghiệp IT, FPT University
> Nhóm 6 người · 11–12 tuần · Modular Monolith
>
> Văn bản này đứng trên mọi tài liệu và thói quen khác của dự án.
> Mọi Pull Request, mọi output của AI agent đều phải tuân thủ.

## Core Principles

### I. Bảo mật là điều kiện tiên quyết (KHÔNG THƯƠNG LƯỢNG)

Chín quy tắc dưới đây vi phạm là **chặn merge ngay**, không có ngoại lệ, không có "sửa ở
sprint sau". Đây là lớp Hard Rules.

- **HR-01** — Mật khẩu PHẢI hash bằng `bcrypt` cost ≥ 12 hoặc `argon2id`.
  TUYỆT ĐỐI KHÔNG lưu plaintext, KHÔNG dùng MD5 hay SHA không salt.
- **HR-02** — KHÔNG được để secret, API key, mật khẩu trong code, file cấu hình hay log.
  Mọi giá trị nhạy cảm PHẢI đọc từ biến môi trường.
- **HR-03** — Cookie `access_token` PHẢI khai báo đủ năm thuộc tính:
  `domain=cnhsk.com`, `httpOnly=true`, `secure=true`, `sameSite=Lax`, `maxAge`.
  *Lý do:* bỏ trống `domain` thì `game.cnhsk.com` KHÔNG nhận được cookie và
  toàn bộ luồng đăng nhập chung giữa ba client sẽ hỏng.
- **HR-04** — CORS PHẢI khai báo rõ từng tên miền. CẤM dùng `*` ở mọi môi trường.
- **HR-05** — Mọi thao tác ghi liên quan tiền thật PHẢI có CSRF token.
  *Lý do:* cookie được trình duyệt gửi tự động, đây là cái giá của việc chọn cookie
  thay vì header. `SameSite=Lax` chỉ chặn được tên miền khác, không chặn tên miền phụ
  của chính mình.
- **HR-06** — Chấm điểm PHẢI thực hiện ở server. Mọi kết quả game gửi lên PHẢI kiểm ba
  điều kiện trước khi lưu: điểm không vượt trần của game đó, thời gian chơi không dưới
  ngưỡng tối thiểu, số ván trong giờ không vượt giới hạn mỗi tài khoản.
  Mastery CHỈ được cộng cho những từ server đã phát ở bước lấy từ vựng.
  *Lý do:* trang game chạy trên trình duyệt, người dùng sửa được mọi thứ bằng DevTools.
- **HR-07** — PHẢI validate và sanitize mọi input từ người dùng.
  CẤM nối chuỗi SQL với dữ liệu người dùng — luôn dùng tham số hóa hoặc JPA.
- **HR-08** — Dữ liệu đề thi HSK do giảng viên cung cấp KHÔNG được commit vào git.
  *Lý do:* dữ liệu có bản quyền. Đã chặn trong `.gitignore`, không ai được gỡ.
- **HR-09** — KHÔNG lộ stack trace hay chi tiết lỗi nội bộ ra client.
  Mọi response lỗi PHẢI theo định dạng `{error_code, message, request_id}`.
  Stack trace chỉ xuất hiện trong log phía server.

### II. API First

- **AC-01** — PHẢI viết hoặc cập nhật OpenAPI **TRƯỚC** khi code endpoint. Contract cần
  ít nhất một thành viên khác duyệt rồi mới được implement.
  *Lý do:* frontend và backend do người khác nhau làm, chạy song song. Contract là điểm
  hẹn duy nhất giữa hai bên; đổi API sau khi đã code là tốn kém nhất.
  *Hiện trạng:* nhóm tự giác tuân thủ. CI chặn sẽ dựng ở giai đoạn sau.

### III. Ranh giới module bất khả xâm phạm

Kiến trúc là Modular Monolith. Ranh giới giữa các module là thứ duy nhất ngăn dự án
thoái hóa thành khối code rối.

- **AC-02** — Một ứng dụng Spring Boot duy nhất `cnhsk-api`, cổng 8080,
  gồm bốn module: `auth`, `learning`, `community`, `shared`.
- **AC-03** — Module gọi nhau CHỈ qua lớp `api` bằng lời gọi hàm.
  CẤM module này truy cập thẳng bảng thuộc schema của module kia.
  *Lý do:* giữ được ranh giới thì sau này tách microservice mới khả thi.
- **AC-10** — Cộng mastery và lưu điểm game PHẢI nằm trong cùng một transaction.
  *Lý do:* một database ba schema cho phép điều này; đây chính là lợi thế đã chọn khi
  từ bỏ kiến trúc ba service.

### IV. Dữ liệu chính xác tuyệt đối

Sai kiểu dữ liệu ở tầng schema là loại lỗi phải migration mới sửa được — đắt nhất trong
mọi loại lỗi.

- **AC-04** — Một database `cnhsk_db`, ba schema (`auth` 4 bảng, `learning` 40 bảng,
  `community` 15 bảng), một tài khoản `svc_app`, một `DataSource` trong Spring.
- **AC-05** — Flyway là nguồn sự thật DUY NHẤT của schema. `ddl-auto` PHẢI là `validate`,
  CẤM đặt `update`. File migration đã chạy thì KHÔNG được sửa; muốn đổi phải viết file
  version mới.
  *Lý do:* sáu người cùng làm. Để Hibernate tự đổi schema thì mỗi máy một kiểu, đến lúc
  merge là vỡ. Sửa file migration cũ làm Flyway báo lỗi checksum và cả nhóm phải xoá DB.
- **AC-06** — Enum lưu bằng `VARCHAR` + `CHECK` constraint. KHÔNG dùng kiểu `ENUM` của
  PostgreSQL. *Lý do:* thêm giá trị vào `ENUM` type cần migration khóa bảng.
- **AC-07** — Tiền dùng `NUMERIC(12,2)`, điểm dùng `BIGINT`. CẤM dùng `FLOAT` cho cả hai.
  *Lý do:* `FLOAT` làm tròn sai với tiền tệ.
- **AC-08** — Thời gian dùng `TIMESTAMPTZ`, server chạy UTC. Mọi tác vụ định kỳ PHẢI ghi
  rõ `zone = "Asia/Ho_Chi_Minh"`.
- **AC-09** — `JSONB` CHỈ dùng cho dữ liệu chỉ đọc. KHÔNG BAO GIỜ dùng cho thứ cần
  thống kê hay truy vấn theo điều kiện.

### V. Không tin client

Nguyên tắc này đã thể hiện ở HR-06 cho phần game, nhưng áp dụng cho toàn hệ thống:
mọi dữ liệu đến từ trình duyệt, ứng dụng di động hay WebView đều là dữ liệu chưa đáng
tin. Server luôn là nơi quyết định cuối cùng về tính hợp lệ, quyền hạn và điểm số.

Ba client (web chính, trang game, mobile) dùng chung một backend. Không client nào được
coi là "đáng tin hơn" client khác.

## Tám actor

Đọc trước khi thiết kế bất cứ thứ gì liên quan quyền. Sai mục này dẫn tới thêm role không
cần thiết vào DB, hoặc chặn mất role thật sự cần.

| Actor | Số UC | Có trong `user_roles`? | Quyền |
|---|---|---|---|
| `USER` | 79 | ✅ | Học, thi, chơi game, đăng bài (chờ duyệt) |
| `GUEST` | 16 | ❌ | Xem trang công khai, tra từ điển, đăng ký |
| `SYSTEM` | 14 | ❌ | Job định kỳ |
| `CONTENT_ADMIN` | 6 | ✅ | Đề thi, kho câu hỏi, nhập dữ liệu, cuộc thi |
| `MANAGER` | 4 | ✅ | Duyệt bài đăng, xử lý báo cáo — Community |
| `FINANCE_ADMIN` | 4 | ✅ | 🔴 Sinh mã thẻ, sổ cái, tranh chấp — **tiền thật** |
| `TEACHER` | 4 | ✅ | Duyệt câu hỏi AI, sửa nội dung, chấm bài thuê |
| `SUPER_ADMIN` | 3 | ✅ | Người dùng, gán role, cấu hình |

**8 actor = 6 role trong DB + 2 actor không phải role.**

### Hai actor không phải role

| Actor | Vì sao không phải role | Kiểm quyền bằng |
|---|---|---|
| `GUEST` | Chưa đăng nhập, **không có dòng** trong `auth.users` | **Không có JWT** |
| `SYSTEM` | Job `@Scheduled`, **không phải người** | Quyền hệ thống, không qua `JwtFilter` |

> ⚠️ **TUYỆT ĐỐI KHÔNG** thêm `GUEST` hay `SYSTEM` vào `user_roles`. Đây là lỗi dễ mắc: thấy
> chúng trong danh sách actor rồi tưởng phải có `role_code` tương ứng.

### Vì sao `ADMIN` tách thành ba

Một `ADMIN` làm được cả ba việc là vi phạm **đặc quyền tối thiểu**:

- `FINANCE_ADMIN` chạm **tiền thật** → phải là role hẹp nhất. `NFR-S08` yêu cầu audit cả
  thao tác **đọc** sổ cái — không phân biệt được nếu chỉ có một `ADMIN`
- `CONTENT_ADMIN` duyệt nội dung → **không cần** quyền sinh mã thẻ
- `SUPER_ADMIN` gán role → **không cần** quyền sinh mã thẻ

Trong 119 UC **không UC nào** dùng `ADMIN` đơn thuần. Mọi UC quản trị đều chỉ rõ một trong
ba role đã tách. Chi tiết quyền: `business.md` §1.

## Mô hình kinh doanh

CNHSK là **sản phẩm phễu** cho một hệ sinh thái khác. Hiểu sai mục này sẽ dẫn tới code
sai hoặc code thừa, nên đọc trước khi làm bất cứ việc gì liên quan tiền.

**Tiền là tiền thật, nhưng CNHSK không thu tiền.** Chủ hệ sinh thái thu. CNHSK chỉ nhận
**mã thẻ** đã có giá trị.

Ba luồng phát mã, phân biệt bằng `credit_cards.channel`:

| `channel` | Ai nhận | Mục đích |
|---|---|---|
| `ECOSYSTEM_GIFT` | khách sẵn có của hệ sinh thái | tặng để trải nghiệm — đây là phễu |
| `PARTNER_BATCH` | chủ hệ sinh thái (bên thứ ba) phân phối tiếp | thu khách lẻ |
| `DIRECT` | admin tự phát | khuyến mãi, bù lỗi |

**Hệ quả kỹ thuật — giữ nguyên vì tiền thật:**

| Giữ | Vì sao |
|---|---|
| `HR-05` CSRF cho mọi thao tác đổi điểm | mã có giá trị tiền thật |
| `BUS-12` chỉ lưu hash mã thẻ | ai đọc được DB cũng không nạp được |
| `BUS-13` `SecureRandom` ≥16 ký tự | mã đoán được = mất tiền |
| `BUS-14`, `BUS-15` sổ cái append-only | sổ cái sửa được thì không còn là bằng chứng |
| `AC-07` `NUMERIC(12,2)` cho tiền | — |

**KHÔNG làm — vì CNHSK không thu tiền:**

- Tích hợp cổng thanh toán (VNPay, Momo, Stripe)
- Xử lý webhook thanh toán
- Đối soát với cổng, hoàn tiền qua cổng

> 🔴 **Rủi ro nghiệp vụ đã biết và đã chấp nhận.** Không có webhook nên CNHSK **không biết**
> mã nào bên thứ ba đã bán. Nếu file CSV lô mã bị rò rỉ thì không có cách phát hiện tự động.
> Chủ dự án đã quyết **không** bắt buộc `expires_at`, nên lớp giảm thiểu duy nhất là
> theo dõi tỉ lệ đổi mã bất thường theo `batch_id` (index `ix_card_funnel`).

**Chỉ số sống của mô hình phễu:** tỉ lệ đổi mã theo `campaign` —
`COUNT(status='USED') / COUNT(*)` nhóm theo `channel, campaign`. Tặng 1.000 mã thu về bao
nhiêu người học thật. Mọi báo cáo quản trị phải trả lời được câu này.

## Quy tắc nghiệp vụ toàn dự án

Lớp Business Rules. Đây là những quy tắc **lặp lại ở nhiều tính năng** — rút từ 714 dòng
`BR-*` trong tám file đặc tả use case. Mỗi SPEC chỉ cần **trích mã**, KHÔNG viết lại nội dung.

Vi phạm `BUS-*`: xử lý như `AC-*` — báo cáo và xin duyệt, cần RFC mới được phá.

| Mã | Quy tắc | Lặp | Chi tiết ở |
|---|---|---|---|
| **BUS-01** | Mọi truy cập tài nguyên của người dùng PHẢI kiểm quyền sở hữu trước khi đọc hoặc ghi. Không chủ sở hữu → **403**. Tài nguyên không tồn tại cũng trả **403**, không trả 404 | 68 | mới — xem dưới |
| **BUS-02** | Các thao tác ghi liên quan nghiệp vụ PHẢI nằm trong **cùng một transaction** | 50 | `business.md` §2, §4 · `AC-10` |
| **BUS-03** | Gọi API bên ngoài thất bại hoặc timeout → PHẢI **hoàn lượt** đã trừ | 42 | `business.md` §2 |
| **BUS-04** | Lịch ôn tập dùng **FSRS**, không dùng khoảng cố định kiểu Leitner | 41 | `business.md` §4 |
| **BUS-05** | Ngưỡng hoàn thành chủ đề: **90%**. Đạt thì mở chủ đề tiếp theo | 33 | `business.md` §4, §9 |
| **BUS-06** | Không lộ PII: email và số điện thoại PHẢI mask ở danh sách và log | 28 | `business.md` §8 |
| **BUS-07** | Nội dung AI sinh LUÔN vào `PENDING_REVIEW`, chỉ `TEACHER`/`CONTENT_ADMIN` duyệt | 27 | `business.md` §6 |
| **BUS-08** | Ngày học, streak, nhắc lịch, reset bảng xếp hạng tính theo **giờ Việt Nam**. Riêng FSRS tính bằng **UTC** | 25 | `business.md` §3 · `AC-08` |
| **BUS-09** | Chấm điểm PHẢI ở server. Kết quả client gửi lên là dữ liệu chưa đáng tin. Chấm bằng dịch vụ ngoài: xem mục chi tiết dưới | 19 | `HR-06` · Nguyên tắc V |
| **BUS-10** | Xoá nội dung do người dùng tạo là **soft delete**. Truy vấn đọc PHẢI lọc bản ghi đã xoá | 14 | mới — xem dưới |
| **BUS-11** | Ngưỡng qua tầng luyện phát âm: **80%** | 11 | mới — xem dưới |
| **BUS-12** | Mã thẻ nạp CHỈ lưu dạng hash. TUYỆT ĐỐI KHÔNG lưu mã thô ở DB hay log | 7 | mới — xem dưới |
| **BUS-13** | Mã thẻ nạp sinh bằng `SecureRandom`, độ dài ≥ 16 ký tự, KHÔNG tuần tự | 6 | mới — xem dưới |
| **BUS-14** | `credit_transactions` là **append-only**. Chỉ `INSERT`, CẤM `UPDATE` | 4 | mới — xem dưới |
| **BUS-15** | KHÔNG BAO GIỜ `DELETE` khỏi sổ cái. Sai thì ghi dòng bù trừ ngược dấu | 3 | mới — xem dưới |

**Bảy mã chưa có chi tiết ở `business.md`** — nội dung chuẩn tắc nằm ngay đây:

- **BUS-01** — Áp dụng cho: bộ flashcard, ghi chú, bài đăng, bản nháp bài thi, yêu cầu chấm
  bài, lịch sử giao dịch. Kiểm bằng `user_id` trong JWT so với `user_id` của bản ghi, KHÔNG
  dựa vào tham số client gửi. *Lý do:* đây là lỗ hổng lặp nhiều nhất trong đặc tả (68 lần) —
  đổi một số trong URL là thấy dữ liệu người khác.
- **BUS-10** — Cột `deleted_at TIMESTAMPTZ NULL`. Mọi `SELECT` cho người dùng PHẢI có
  `WHERE deleted_at IS NULL`. *Lý do:* người học xoá nhầm bộ thẻ 500 từ thì không có đường cứu.
- **BUS-11** — Luyện phát âm chia tầng; đạt **80%** tầng hiện tại mới mở tầng sau.
  Khác `BUS-05` (90% cho chủ đề) — hai ngưỡng khác nhau, đừng dùng lẫn.
- **BUS-12** — Lưu `code_hash`, so sánh bằng cách hash mã người dùng nhập rồi đối chiếu.
  *Lý do:* ai đọc được DB cũng không nạp được tiền. Và mã bán qua Zalo nên không có webhook
  xác nhận — hash là lớp bảo vệ duy nhất.
- **BUS-13** — CẤM `Random`, CẤM mã tuần tự, CẤM mã lấy từ timestamp.
  *Lý do:* mã tuần tự thì đoán được mã kế tiếp.
- **BUS-14** — Số dư = `balance_after` của dòng mới nhất, KHÔNG có bảng `user_credits`.
  DB đã ràng buộc `CHECK (balance_after = balance_before + amount)`.
- **BUS-15** — Cùng lý do `BUS-14`: sổ cái sửa được thì không còn là bằng chứng.

### `BUS-09` — chấm bằng dịch vụ bên ngoài

Bổ sung 2026-10-08 cho tính năng Shadowing (luyện nói, feature 1.6).

`BUS-09` nói *"chấm điểm PHẢI ở server"*. Khi điểm do **dịch vụ bên thứ ba**
chấm (ví dụ đánh giá phát âm), luật vẫn giữ nguyên tinh thần — **không tin
client** — với ba điều kiện bắt buộc:

| # | Điều kiện |
|---|---|
| 1 | Dữ liệu thô (audio, ảnh, văn bản) đi **client → server ta → dịch vụ ngoài**. KHÔNG cho client gọi trực tiếp dịch vụ ngoài |
| 2 | Server ta nhận kết quả, kiểm tính hợp lệ, rồi mới ghi DB. Client **KHÔNG BAO GIỜ** gửi điểm lên |
| 3 | Ghi rõ `provider` trong bảng kết quả. Đổi nhà cung cấp phải đọc lại được điểm cũ thuộc nhà nào |

*Lý do:* nếu client gọi trực tiếp rồi gửi điểm về, người dùng sửa được điểm bằng
cách gọi API của mình. Server đứng giữa là điểm kiểm duy nhất.

*Hệ quả cho `LI-1`:* dự án **không tự xây model nhận dạng giọng** — vẫn đúng.
Nhưng được **gọi dịch vụ ngoài** cho việc đó, tính vào hạn mức lượt theo `BUS-03`.

> ⚠️ **BUS-16 còn trống** — chưa có quy tắc nào về **trọng số mastery theo nguồn**.
> `hanzi-writer` chấm nét ở client, server không kiểm lại được, nên mastery từ luyện viết
> mâu thuẫn trực tiếp với `BUS-09`. Cần chốt trước khi code UC-015. Xem `docs/CONTEXT.md` §4.5.

## Ràng buộc kỹ thuật

Lớp Engineering Standards. Override được nhưng PHẢI ghi lý do trong PR.

- **ES-01** — Stack bất biến: Java 17 · Spring Boot 3.5.14 · PostgreSQL · Maven Wrapper ·
  Flyway · jjwt 0.12.6 · React 18.3 · Vite 5 · TypeScript 5.7.
  Đổi bất kỳ mục nào cần RFC và cả nhóm duyệt.
  *Lý do chọn:* đây là phiên bản nhóm đã dùng thật ở dự án trước, không phải bản mới nhất.
- **ES-02** — Thư viện frontend đã duyệt: `tailwindcss` 3.4, `react-router-dom` 6.28,
  `lucide-react`, `clsx`, `tailwind-merge`. Thêm thư viện mới PHẢI hỏi trước.
- **ES-03** — Quy ước đặt tên: bảng `snake_case` số nhiều · package Java chữ thường ·
  component React `PascalCase` · route API `kebab-case`.
- **ES-04** — Tên thư mục đã chốt: backend là thư mục gốc (`cnhsk-api`),
  frontend là `cnhsk-web`. KHÔNG ai được đổi tên nữa.
  *Trạng thái 2026-10-06:* thư mục **chưa tồn tại** — bản nháp đã xoá. Quy ước tên vẫn có hiệu lực cho lúc dựng lại.
  *Lý do:* trước đây có bốn tên khác nhau cùng tồn tại gây nhầm lẫn.
- **ES-05** — Mọi endpoint PHẢI có integration test.
  **Mức coverage: 80% line coverage**, áp cho toàn bộ code nghiệp vụ.
  *Lý do chọn 80%:* theo khuyến nghị của playbook. Nhóm đã biết đây là mức cao với 6 người
  11 tuần — nếu tới tuần 8 không đạt thì **xem lại con số này trước khi cắt feature**,
  vì hạ coverage ít đau hơn bỏ tính năng.
- **ES-06** — Commit theo Conventional Commits.
  KHÔNG nhắc đến AI trong commit message.
- **ES-07** — Nhánh đặt tên `spec/*`, `agent/*`, `fix/*`.
  PR cần ít nhất một reviewer. KHÔNG tự duyệt PR của mình.
- **ES-08** — Multi-AI workflow: Claude Code cho phase lớn và backend, Codex cho frontend
  và các sửa chữa nhỏ. Cả hai đọc chung hiến pháp này.

## Quy trình phát triển và Quality Gate

### Definition of Done

Một đầu việc CHỈ được coi là xong khi đủ cả bốn điều kiện:

1. Toàn bộ test pass
2. Lint sạch
3. OpenAPI đã cập nhật (nếu có thay đổi endpoint)
4. Không còn `TODO` nào trong code

KHÔNG được coi là xong nếu: còn `TODO`, test bị skip, hoặc chỉ "chạy được trên máy tôi".

### Chính sách AI Agent

AI agent **được phép** không cần hỏi:
- Đọc và ghi trong `src/`, `docs/`, `specs/` (và `cnhsk-web/src/` khi frontend được dựng)
- Chạy test và lint
- `git add` và `git commit`

AI agent **PHẢI hỏi người** trước khi:
- Xoá bất kỳ file nào
- Sửa hiến pháp này
- Push lên `main`
- Thêm dependency mới
- Đổi schema database

AI agent **bắt buộc**:
- Tự kiểm tra tuân thủ hiến pháp trước khi nộp output
- Viết shadow plan trước khi bắt đầu task mới
- Báo ngay khi gặp edge case không có trong spec, thay vì tự giả định

### Ràng buộc chi tiết

Hiến pháp này nêu **luật**. Chi tiết áp dụng nằm ở ba file trong
`.specify/memory/constraints/` — khi lệch, **hiến pháp thắng**:

| File | Trả lời câu hỏi | Enforcement |
|---|---|---|
| `global.md` | Dùng công nghệ gì? Được và cấm thư viện nào? | Review + CI |
| `business.md` | Nghiệp vụ hoạt động ra sao? | Code review (cần người verify) |
| `safety.md` | Agent không được làm gì / phải hỏi gì trước? | **Human approval gate** |

`business.md` §9 có **Domain Glossary** — thuật ngữ dễ hiểu sai. Đọc trước khi đặt tên.

### Tài liệu tham chiếu

Đặt trong `docs/`, là nguồn sự thật cho phạm vi và thiết kế:

| Tài liệu | Nội dung |
|---|---|
| `feature-tree.md` | **33 tính năng** đã chốt (32 sau khi cắt 5.4 Gia sư). Header file tự ghi "38" — con số đó **sai**, đếm thật chỉ có 33 mục `N.N` |
| `kien-truc.md` | Kiến trúc Modular Monolith bản 5 |
| **`database.md`** | **Bản hiện hành.** 4 schema · 31 bảng. Kiêm RFC sửa `AC-03`/`AC-04` |

| `CONTEXT.md` | Pha 0: Problem Statement, Business Goal, Domain Knowledge |
| `wbs-estimate.md` | 66 đầu việc, phân công, ước lượng |

## Governance

Hiến pháp này đứng trên mọi tài liệu và thói quen khác của dự án. Khi có mâu thuẫn giữa
văn bản này và bất kỳ tài liệu nào khác, hiến pháp thắng.

**Bốn lớp và cách xử lý vi phạm:**

| Lớp | Ký hiệu | Vi phạm thì |
|---|---|---|
| Hard Rules | `HR-*` | Chặn merge ngay. Agent tự sửa, KHÔNG được nộp. |
| Architectural Constraints | `AC-*` | Báo cáo và xin duyệt. Cần RFC mới được phá. |
| **Business Rules** | `BUS-*` | Báo cáo và xin duyệt. Cần RFC mới được phá. |
| Engineering Standards | `ES-*` | Nộp kèm giải thích lý do trong PR. |

**Thủ tục sửa đổi:** mọi thay đổi cần cả nhóm đồng thuận. Người đề xuất viết RFC nêu
động cơ, thay đổi cụ thể, đánh giá rủi ro. Sau khi duyệt, ghi vào
`CONSTITUTION_CHANGELOG.md` và tăng version.

**Chính sách version:**

- **MAJOR** — bỏ hoặc định nghĩa lại nguyên tắc theo hướng không tương thích ngược
- **MINOR** — thêm nguyên tắc hoặc mở rộng hướng dẫn đáng kể
- **PATCH** — làm rõ câu chữ, sửa lỗi diễn đạt, không đổi ngữ nghĩa

**Kiểm tra tuân thủ:** mọi PR phải xác nhận không vi phạm bốn lớp trên. AI agent phải tự
kiểm trước khi nộp và in báo cáo self-check, kể cả khi không có vi phạm nào.

**Lịch sử sửa đổi** được ghi ở `CONSTITUTION_CHANGELOG.md`, không ghi trong file này.
*Lý do:* giữ hiến pháp đủ ngắn để AI đọc hết mà không bỏ sót rule.

## Ghi chú hoãn quyết định

Những mục dưới đây nhóm chưa chốt. Ghi rõ ở đây thay vì giả vờ đã có câu trả lời.

- ~~**TODO(REDIS_PLACEMENT)**~~ — **ĐÃ CHỐT 2026-10-06: giữ kiến trúc bản 5, Redis đặt
  phía sau ứng dụng.**
  *Lý do:* Redis trong dự án này làm hai việc — cache và bảng xếp hạng (`ZREVRANK`). Cả hai
  đều do **ứng dụng** gọi, nên Redis phải ở phía sau ứng dụng. Thứ đặt *giữa* client và
  service là **reverse proxy** hoặc **CDN** — chúng cache HTTP response, không phải cache dữ
  liệu nghiệp vụ, và không chạy được `ZREVRANK`.
  Hai việc khác nhau, không phải hai cách làm cùng một việc. Nếu sau này cần cache HTTP thì
  thêm reverse proxy — **không thay thế** Redis.
- ~~**TODO(PAYMENT_SCOPE)**~~ — **ĐÃ CHỐT 2026-10-05: tiền thật, thu ngoài hệ thống.**
  Xem mục *Mô hình kinh doanh* ở trên. `HR-05` và `AC-07` giữ nguyên hiệu lực.
- ~~**TODO(COVERAGE_TARGET)**~~ — **ĐÃ CHỐT 2026-10-06: 80% toàn bộ.** Xem `ES-05`.
- **TODO(RATIFICATION_DATE)** — Cần cả sáu thành viên xác nhận ngày phê chuẩn chính thức.
  🔴 **Đây là TODO duy nhất còn treo.** Chưa phê chuẩn thì các `HR-*` về hình thức chưa có
  hiệu lực ràng buộc.

**Version**: 1.3.0 | **Ratified**: TODO(RATIFICATION_DATE) | **Last Amended**: 2026-10-08

# CNHSK — Design Guidelines

| | |
| --- | --- |
| **Trạng thái** | Quy tắc thiết kế đang rà soát; inventory 79 màn đã FINAL |
| **Status** | DRAFT |
| **Owner** | Nhóm CNHSK |
| **Approved by** | Chưa duyệt |
| **Locked on** | Chưa khóa |
| **Sprint** | Khung giao diện ban đầu |

---

## 0. Phạm vi và cách dùng tài liệu

Đây là nguồn quy định thiết kế dùng chung cho web chính, web game và ứng dụng
React Native. Khi tài liệu này khác với một mockup hoặc quyết định miệng, nhóm
SHALL cập nhật và duyệt tài liệu trước khi triển khai khác đi.

Tài liệu tham khảo:

- [Học Bá Education](https://hoc-ba.edu.vn/): nguồn tham khảo cho nhận diện đỏ
  Trung Hoa, màu nhấn vàng, độ tương phản và cảm giác giáo dục chuyên nghiệp.
- [Hanzi Cozy Diary](https://CNHSK.today/): nguồn tham khảo cho cách nhóm
  công cụ theo kỹ năng, card tính năng, lộ trình học và phân cấp nội dung.
- [feature-tree.md](feature-tree.md): nguồn sự thật về tính năng và phạm vi.
- [kien-truc.md](kien-truc.md): nguồn sự thật về ba client và xác thực.
- Screen List (`docs/generated/cnhsk-screen-list.docx`) và Screen Flow (`docs/generated/cnhsk-screen-flow.png`): cấu trúc UI FINAL, 79 màn; tình trạng file nguồn xem §5.
- [screen-fields.md](screen-fields.md): đặc tả UI chi tiết duy nhất cho SCR-001–SCR-079.

Chỉ học nguyên tắc thiết kế; không sao chép logo, ảnh, minh họa, nội dung, font
độc quyền hoặc bố cục nguyên bản của website tham khảo.

---

## 1. Context & Goal

**Vì sao file này tồn tại:** CNHSK có ba client do nhiều thành viên phát
triển song song. File này giữ một ngôn ngữ thị giác thống nhất, giảm quyết định
tùy ý và giúp người học nhận ra cùng một sản phẩm khi chuyển giữa web, game và
mobile.

**Nguyên tắc thiết kế, xếp theo thứ tự ưu tiên:**

1. **Việc học quan trọng hơn trang trí:** nội dung, đáp án và tiến độ SHALL rõ
   hơn hình minh họa hoặc hiệu ứng.
2. **Nhất quán xuyên nền tảng:** semantic token và trạng thái SHALL giữ cùng ý
   nghĩa; cách bố trí MAY thích nghi theo thiết bị.
3. **Một hành động chính mỗi vùng:** mỗi card, dialog hoặc màn hình SHALL có tối
   đa một CTA primary.
4. **Hiển thị tiến độ có ngữ cảnh:** phần trăm, mastery hoặc chuỗi ngày SHALL đi
   kèm nhãn giải thích, không dùng màu làm tín hiệu duy nhất.
5. **An toàn trước tiện lợi:** thao tác tài khoản, điểm tài chính, nộp bài và rời
   phiên thi SHALL ưu tiên xác nhận và khả năng phục hồi.

---

## 2. Ràng buộc kỹ thuật

| Hạng mục | Giá trị |
| --- | --- |
| Web chính | React 18.3.x + TypeScript 5.7 + Vite 5.x |
| Web game | React 18.3.x + TypeScript 5.7 + Vite 5.x; Phaser 3 hoặc Canvas chỉ nằm trong vùng game |
| Mobile | React Native + Expo + TypeScript; phiên bản chốt khi bắt đầu giai đoạn mobile |
| Runtime web | Node.js 22 LTS hoặc Node.js 24 theo môi trường đã chốt |
| Design system web | Tailwind CSS 3.4 + component sở hữu trong source; chỉ dùng package đã duyệt ở ES-02 |
| Design system mobile | React Native core; ánh xạ semantic tokens với web; package thêm cần duyệt |
| Icon | Lucide React / Lucide React Native; nét 1.75–2 px |
| Biểu đồ | Dùng biểu đồ theo yêu cầu SCR-019; thư viện web/mobile chưa được ES-02 duyệt thì phải xin duyệt |
| Thư viện bị cấm | Không trộn thêm Material UI, Ant Design, Bootstrap hoặc UI kit toàn cục khác |
| Nền tảng phải hỗ trợ | Desktop web, mobile web, Android và iOS qua React Native |
| Trình duyệt / OS tối thiểu | Xem Open Questions #1 |

THE system SHALL dùng component semantic như `Button`, `Card`, `Progress`,
`Dialog`, `FormField`; không tạo bản sao theo từng trang.

---

## 3. Actors & phạm vi màn hình

| Actor | Quyền | Thiết bị chính | Nhóm màn hình được dùng |
| --- | --- | --- | --- |
| Khách | Xem giới thiệu và trang công khai, tra từ điển, đăng ký/đăng nhập — không dùng thử tính năng (mục 5.2) | Web, mobile | Public, auth |
| USER | Học, luyện, thi, chơi, dùng thư viện và cộng đồng | Web, mobile | Toàn bộ màn học viên |
| TEACHER | Duyệt câu hỏi AI, chấm bài thuê | Desktop web | SCR-056–SCR-059; học viên chỉ khi có USER |
| MANAGER | Kiểm duyệt cộng đồng | Desktop web | SCR-060–SCR-063; học viên chỉ khi có USER |
| CONTENT_ADMIN | Duyệt câu hỏi AI, quản lý nội dung | Desktop web | SCR-058/SCR-059, SCR-064–SCR-072; học viên chỉ khi có USER |
| FINANCE_ADMIN | Sổ cái, mã thẻ, gói dịch vụ | Desktop web | SCR-073–SCR-075; học viên chỉ khi có USER |
| SUPER_ADMIN | Người dùng, phân quyền, cấu hình được phép | Desktop web | SCR-076–SCR-078; học viên chỉ khi có USER |
| SYSTEM | Tác vụ tự động, không phải người dùng | — | Không có màn hình |

> **Tám actor**, khớp Hiến pháp mục *Tám actor*. `ADMIN` gộp ở bản trước đã tách
> thành ba role theo nguyên tắc phân tách nhiệm vụ — xem mục 5.3.
>
> `TEACHER` và `CONTENT_ADMIN` **cùng** duyệt được câu hỏi AI, theo `BUS-07`.
> Cả hai dùng chung AI Review Queue SCR-058 và Question Edit SCR-059 theo quyền được gán; không kế thừa USER.

**Ranh giới giữa các nền tảng:**

| Tính năng | Web chính | Web game | React Native |
| --- | --- | --- | --- |
| Tài khoản, học tập, thi, tra cứu | Có | Không | Có |
| Game | Điều hướng sang web game | Có | WebView tải chính web game |
| Blog, quiz, xếp hạng | Có | Chỉ bảng hạng game | Có |
| Quản trị và duyệt nội dung | Có, tối ưu desktop | Không | Không trong MVP |
| Thanh toán/điểm | Có | Không | Có |

---

## 4. User flow

★ **Xác thực và vào hệ thống:** Landing → Đăng ký (nhận email xác thực) hoặc
Đăng nhập → xác thực theo client (web cookie · mobile header ·
WebView tiêm token, xử lý nội bộ không có control cho người dùng chọn) → khu được phép theo role được gán (USER vào SCR-010).
Cơ chế chặn đăng nhập theo nguồn và lỗi tương ứng xem SCR-002; không suy ra quyền kế thừa từ việc đăng nhập.
Đặt lại mật khẩu → thu hồi **toàn bộ** refresh token.

★ **Học theo chủ đề:** Đăng nhập → Trang chủ học tập → Chọn chủ đề đã mở →
Học từ → Luyện nhận diện/nghe/viết → Kiểm tra → Xem mastery và chủ đề kế tiếp.

★ **Làm đề và sửa điểm yếu:** Chọn HSK/dạng đề → Làm bài → Xác nhận nộp →
Server chấm → Xem lỗi theo điểm kiến thức → Luyện ngay phần yếu.

★ **Ôn tập đến hạn:** Trang chủ → Danh sách đến hạn → Trả lời → Tự đánh giá
nếu là flashcard → Nhận lịch ôn mới → Xem tiến độ.

**Tra cứu để học:** SCR-024 → SCR-025; dịch đoạn là SCR-026 riêng.
USER có thể lưu vào SCR-027/SCR-029 theo quyền; khách chỉ đọc public projection.

**Chơi game trên web:** Web chính → Web game → `/api/auth/me` → Game Hub
SCR-035 → Game Session SCR-036 → kết quả cá nhân được server xác nhận ngay trong
SCR-036 → về hub/trang chính. Game Leaderboard SCR-047 là màn xếp hạng V2 riêng.

**Chơi game trên mobile:** Mobile làm mới token → Mở WebView → Tiêm token →
Chơi → Gửi điểm → WebView báo hoàn tất → Mobile cập nhật tiến độ.

**Duyệt nội dung:** Đăng nhập bằng role phù hợp → Mở hàng đợi → Xem chi tiết →
Chấp nhận/từ chối kèm lý do → Chuyển mục tiếp theo.

---

## 5. Canonical UI structure — 79 screens

**Canonical Screen Inventory: 79 screens · Status: FINAL.**

Nguồn chuẩn tắc cho ID, Screen Name, Feature và Description là Screen List đã duyệt (`docs/generated/cnhsk-screen-list.docx`). Screen Flow đã duyệt (`docs/generated/cnhsk-screen-flow.png`) quyết định public/private, role entry, grouping, shared screens và điều hướng chính. Chi tiết field, action, validation, trạng thái và mockup readiness của từng màn nằm duy nhất trong [screen-fields.md](screen-fields.md).

**Tình trạng nguồn ngày 2026-10-09:** hai artifact đã được đọc trước khi cập nhật; hiện không còn ở các đường dẫn trên và chưa xác nhận vị trí mới. Nội dung dưới đây giữ nguyên inventory đã đọc và 79 ID/tên do chủ dự án xác nhận; kiểm tra lại artifact khi có đường dẫn. Không tái tạo hoặc thay thế nguồn đã duyệt.

Inventory giữ nguyên SCR-001 đến SCR-079. Một UC có thể dùng nhiều màn hoặc không có màn; một màn có thể phục vụ nhiều UC. Scope MVP/V2/Deferred của nội dung không thay đổi inventory. Không tính router, sidebar, nhóm menu, AI Assistant, dialog hoặc trạng thái lỗi thành màn bổ sung.

### 5.1. Information architecture và page ownership

| Nhóm điều hướng | Màn chuẩn | Entry / liên kết chính |
|---|---|---|
| Public entry và auth | SCR-001–SCR-006 | Landing → Sign In hoặc Register → Verify Email; Sign In → Forgot Password → Reset Password → Sign In |
| Account | SCR-007–SCR-009 | User Profile, Change Password và Study Reminder Settings là ba màn riêng |
| Learner home | SCR-010 | Learning Dashboard dẫn vào học, ôn, luyện và Learning Progress |
| Vocabulary / review / character / grammar / progress | SCR-011–SCR-019 | Topic List → Topic Study; Due Review; Character Writing; Character Recognition; Pronunciation Practice; Grammar List → Grammar Lesson; Learning Progress |
| HSK exams / practice | SCR-020–SCR-023 | Exam List → Exam Attempt → Exam Result → Targeted Practice |
| Dictionary / learning tools / private library | SCR-024–SCR-029 | Dictionary Search → Dictionary Detail; Translation; Personal Notes; Flashcard Deck List → Flashcard Deck Detail |
| Entitlements / teacher grading | SCR-030–SCR-034 | Plan & Credits → Transaction History; Grading Request Submission → My Grading Requests → Grading Request Detail |
| Games / videos | SCR-035–SCR-038 | Game Hub → Game Session; Video List → Video Study |
| Community / quizzes / rankings / contests | SCR-039–SCR-051 | Post List → Post Detail; Create / Edit Post; Community User Profile; Quiz List → Attempt → Result; Topic/Game Leaderboard; Contest List → Detail → Attempt → Results |
| Public resources | SCR-052–SCR-055 | Public Flashcard Deck List; YouTube & Podcasts; Book Catalogue → Book Detail |
| Teacher / shared content review | SCR-056–SCR-059 | Teacher Grading Queue → Teacher Grading Workspace; AI Review Queue → shared Question Edit |
| Community moderation | SCR-060–SCR-063 | Post Moderation Queue → Detail; Violation Report Queue → Detail |
| Content administration | SCR-064–SCR-072 | Question Bank; Admin Exam List → Create / Edit Exam; Topic Prerequisites; Learning Data Import → Import Report; Admin Contest List → Create / Edit Contest; Reference Management |
| Finance administration | SCR-073–SCR-075 | Card Batch Management; Plan Management; Credit Ledger |
| System administration / shared refusal | SCR-076–SCR-079 | User List → User Detail; System Settings; Access Denied |

### 5.2. Public và authenticated navigation

Khách được vào SCR-001–SCR-006 và các tài nguyên công khai được Screen Flow chỉ rõ: Dictionary Search/Detail SCR-024/SCR-025, Community Post List/Detail SCR-039/SCR-040, Topic/Game Leaderboard SCR-046/SCR-047, Contest List/Detail SCR-048/SCR-049, Public Flashcard Deck List SCR-052, YouTube & Podcasts SCR-053, Book Catalogue/Detail SCR-054/SCR-055. Nội dung V2 chỉ hiện khi feature tương ứng được triển khai. Public read không cấp quyền lưu dữ liệu hoặc tham gia.

Learner workspace và USER services yêu cầu role USER được gán tường minh. Translation tách khỏi Dictionary Search/Detail; Personal Notes và bộ flashcard cá nhân không xuất hiện như chức năng dùng thử cho khách. SCR-029 chỉ có public projection khi khả năng chia sẻ V2 được bật; owner edit vẫn kiểm quyền sở hữu. SCR-042 theo nhánh Community Actions của flow, cần USER; không tự biến thành route khách chỉ vì tên có chữ Profile. Contest Results SCR-051 thuộc nhánh USER đã xác thực của flow, đồng thời kiểm publication/ownership ở screen-fields.md. Link kết quả từ thông tin cuộc thi công khai phải qua ranh giới đăng nhập; không tự cấp guest access hoặc mở đáp án riêng của người khác.

Từ tài nguyên công khai, action cần USER dẫn tới SCR-002 với đích trở lại đã kiểm tra. Đăng ký thành công tới SCR-004; xác thực email là hướng dẫn, không phải cửa chặn việc học. Hạn mức khách cho tra cứu là chống lạm dụng, không cấp dùng thử học/AI/game. Ngưỡng/cách đếm còn khác nhau giữa auth spec và UC thư viện, phải dùng Open Issues của SCR-024/SCR-025 để chốt contract trước khi triển khai.

### 5.3. Role navigation và shared pages

| Role gán tường minh | Menu/entry được phép |
|---|---|
| USER | SCR-010 và learner/USER service screens theo §5.1; Account SCR-007/SCR-008 và nhắc học SCR-009 theo actor của từng màn |
| TEACHER | SCR-056/SCR-057; SCR-058/SCR-059 với quyền review nội dung |
| MANAGER | SCR-060–SCR-063 |
| CONTENT_ADMIN | SCR-058/SCR-059; SCR-064–SCR-072 |
| FINANCE_ADMIN | SCR-073–SCR-075 |
| SUPER_ADMIN | SCR-076–SCR-078 |

Không kế thừa role: TEACHER không mặc nhiên có USER; SUPER_ADMIN không mặc nhiên có quyền tài chính, nội dung hoặc kiểm duyệt. Một người có nhiều role nhìn thấy hợp các menu được gán. Role Navigation trong ảnh là điểm điều phối, không phải một trang dashboard mới. Với nhiều role, dùng đích được phép người dùng chọn hoặc đích an toàn đã kiểm quyền; chưa có quyết định xếp thứ tự ưu tiên role.

Question Edit SCR-059 là một màn chung cho TEACHER/CONTENT_ADMIN và nguồn vào từ AI Review Queue SCR-058 hoặc Question Bank SCR-064 theo quyền tương ứng. Quyền review không tự cấp toàn bộ quản lý Question Bank. Không tạo editor riêng theo role. Teacher queue SCR-056 chỉ có metadata được phép; bài viết/hồ sơ nhận việc trong SCR-057 tuân điều kiện claim và sở hữu, không đưa thông tin nhạy cảm vào queue.

### 5.4. Screen Flow và UI patterns

Ảnh flow chuẩn tắc: `docs/generated/cnhsk-screen-flow.png`. Chưa thể hiển thị lại ảnh do tình trạng nguồn ghi ở đầu §5; các flow lịch sử không thay thế ảnh đã duyệt.

Ảnh và Screen List đã đọc từ docs/generated là artifact FINAL; đợt đồng bộ này không sửa hoặc tái tạo chúng. Hiện chưa thể kiểm tra lại file/hash do tình trạng nguồn nêu trên. Các sơ đồ cũ tại [screen-flow/](screen-flow/README.md) là lịch sử mô hình UI trước đây, không dùng để xác định inventory, actor hoặc điều hướng hiện hành.

**List/detail:** list giữ tìm kiếm/bộ lọc khi quay lại; mở detail của đúng item bằng ID thật. Detail không tự mở nội dung private chưa qua ownership. Các cặp list/detail của community, teacher grading, moderation, import và users giữ màn riêng theo inventory.

**Create/edit:** SCR-041, SCR-066 và SCR-071 chứa hai chế độ trong cùng một màn; tạo/sửa không sinh ID mới. Modal tạo bộ flashcard ở SCR-028, card editor ở SCR-029, quản lý reference ở SCR-072, batch actions ở SCR-073, điều chỉnh ledger ở SCR-075 và role assignment ở SCR-077 là nội tuyến/dialog, không phải màn bổ sung.

**Sessions/results:** SCR-021/SCR-022, SCR-044/SCR-045 và SCR-050/SCR-051 giữ attempt và result riêng; kết quả game thuộc SCR-036, Dictation/Shadowing thuộc SCR-038. Không đổi leaderboard thành trang kết quả cá nhân. Chỉ kết quả server xác nhận mới được trình bày là đã lưu.

**Shared utilities:** SCR-079 giải thích thiếu quyền và cho về khu được phép. Thiếu phiên đưa SCR-002; thiếu entitlement đưa SCR-030; không tạo thêm trang lỗi hoặc billing redirect screen. AI Assistant là panel có ngữ cảnh, không là màn riêng; điều kiện hiện trong exam/contest phải theo source và Open Issues, không tự cung cấp lời giải khi đang làm bài.

**Frontend evidence:** chưa có cnhsk-web, route, sidebar/menu config, page component hoặc role guard trong repository. Các pathname ghi trong tài liệu cũ là bằng chứng đề xuất, không phải Existing Route đã xác nhận. Khi dựng cnhsk-web, map từng canonical screen vào page/component và lazy-load theo nhóm; route implementation cần review trước, không được dùng pathname cũ để gộp màn đã tách.

### 5.5. Mockup requirements và scope

Mỗi mockup dùng đúng SCR ID và Screen Name; designer lấy field/control/list column/action/validation/empty/error/role difference từ 23 mục của màn trong screen-fields.md. Mỗi màn có đúng một readiness: READY FOR MOCKUP, READY WITH PLACEHOLDERS hoặc BLOCKED FOR FINAL MOCKUP. Placeholder chỉ thể hiện vấn đề thật chưa chốt; không dựng giá trị business, số liệu hoặc quyền giả. Modal không thay thế một detail screen đã được inventory duyệt.

Không thêm checkout tiền thật, schema import chưa duyệt, grading policy chưa duyệt, setting catalogue chưa duyệt, role inheritance hoặc màn hiển thị secret. Scope tính năng theo feature-tree.md; Feature không đồng nhất Screen. Mockup HTML nằm ngoài repo chỉ là tham khảo lịch sử, phải đối chiếu 79 màn trước khi dùng và phải viết lại bằng React khi triển khai.

---

## 6. Design tokens

Màu lấy cảm hứng từ hệ đỏ–vàng của Học Bá nhưng được đặt thành semantic token
riêng cho CNHSK. Mọi client SHALL dùng token, không dùng màu hex trực tiếp
trong component.

### 6.1. Màu

| Token | Giá trị | Dùng cho |
| --- | --- | --- |
| `color.brand.600` | `#AF0000` | Primary button, active navigation, liên kết chính |
| `color.brand.700` | `#8F0000` | Hover/pressed primary |
| `color.brand.800` | `#6E0000` | Heading hoặc vùng brand tương phản cao |
| `color.brand.50` | `#FFF4F4` | Nền selected/brand nhẹ |
| `color.brand.100` | `#FFDFDF` | Border/badge brand nhẹ |
| `color.accent.500` | `#F3C650` | Thành tích, streak, premium, điểm nhấn có kiểm soát |
| `color.accent.700` | `#8A6500` | Chữ trên nền vàng nhạt |
| `color.canvas` | `#FAFAF8` | Nền ứng dụng |
| `color.surface` | `#FFFFFF` | Card, dialog, sheet |
| `color.surface.subtle` | `#FFF8F8` | Section xen kẽ |
| `color.text.primary` | `#2F3033` | Nội dung chính |
| `color.text.secondary` | `#62646A` | Nội dung phụ |
| `color.text.muted` | `#858890` | Metadata, placeholder khi đủ tương phản |
| `color.border` | `#E3E4E8` | Divider và border mặc định |
| `color.success` | `#237A4B` | Đúng, hoàn thành, đã mở khóa |
| `color.success.bg` | `#EAF6EF` | Nền trạng thái thành công |
| `color.warning` | `#9A6200` | Sắp hết hạn, cần chú ý |
| `color.warning.bg` | `#FFF5D9` | Nền cảnh báo |
| `color.danger` | `#C0262D` | Sai, lỗi, thao tác phá hủy |
| `color.danger.bg` | `#FDECEE` | Nền lỗi |
| `color.info` | `#246B9E` | Thông tin hệ thống |
| `color.info.bg` | `#EAF4FB` | Nền thông tin |
| `color.focus` | `#2563EB` | Focus ring, không thay bằng đỏ |

Màu vàng SHALL không dùng làm chữ nhỏ trên nền trắng. Màu đỏ brand SHALL không
đồng thời mang nghĩa lỗi; lỗi luôn dùng cặp `danger` kèm icon/text.

### 6.2. Typography

| Token | Giá trị | Dùng cho |
| --- | --- | --- |
| `font.sans` | `"Be Vietnam Pro", system-ui, sans-serif` | Toàn bộ UI tiếng Việt |
| `font.hanzi` | `"Noto Serif SC", "Songti SC", serif` | Chữ Hán được học |
| `text.display` | 48/56, 700; mobile 36/44 | Hero landing duy nhất |
| `text.h1` | 32/40, 700; mobile 28/36 | Tiêu đề màn hình |
| `text.h2` | 24/32, 700 | Tiêu đề section |
| `text.h3` | 20/28, 600 | Tiêu đề card lớn |
| `text.body` | 16/24, 400 | Nội dung mặc định |
| `text.body-sm` | 14/20, 400 | Metadata và nội dung phụ |
| `text.label` | 14/20, 600 | Form label, tab, button |
| `text.caption` | 12/16, 500 | Chú thích; không dùng cho nội dung học |
| `text.hanzi-lg` | 56/68, 500 | Chữ Hán trọng tâm trên desktop |
| `text.hanzi-md` | 40/52, 500 | Chữ Hán trong card/mobile |

Không dùng font Gilroy của Học Bá vì quyền sử dụng chưa được xác nhận.

### 6.3. Spacing, bo góc, đổ bóng, z-index

| Token | Giá trị |
| --- | --- |
| `space` | Thang 4 px: `4, 8, 12, 16, 24, 32, 40, 48, 64` |
| `radius.sm` | `8px` |
| `radius.md` | `12px` |
| `radius.lg` | `20px` |
| `radius.pill` | `999px` |
| `shadow.sm` | `0 1px 2px rgba(31, 35, 40, .08)` |
| `shadow.md` | `0 8px 24px rgba(31, 35, 40, .10)` |
| `shadow.overlay` | `0 20px 48px rgba(31, 35, 40, .18)` |
| `content.max` | `1280px` |
| `content.reading` | `720px` |
| `control.height` | `44px`; compact desktop MAY dùng `40px` |
| `touch.target` | Tối thiểu `44×44px` |
| `z.base/nav/dropdown/overlay/toast` | `0 / 20 / 40 / 60 / 80` |

---

## 7. Component inventory

| Component | Biến thể | Dùng ở màn hình nào | Ghi chú |
| --- | --- | --- | --- |
| `AppShell` | learner, admin, game | Mọi màn authenticated | Điều phối header/sidebar/bottom nav |
| `Button` | primary, secondary, outline, ghost, danger | Toàn hệ thống | Một primary CTA mỗi vùng |
| `IconButton` | default, danger | Header, table, card | Luôn có accessible label |
| `Card` | default, interactive, selected, locked | Dashboard, chủ đề, game | Toàn card chỉ clickable khi có affordance |
| `StatusBadge` | success, warning, danger, info, neutral | Mọi màn | Icon + text, không chỉ màu |
| `Progress` | linear, circular, mastery | Dashboard, học, tiến độ | Luôn có giá trị dạng text |
| `LevelBadge` | HSK1–HSK7-9 | Tra cứu, chủ đề, đề thi | Không gán màu ngẫu nhiên theo trang |
| `FormField` | text, password, search, select, textarea | Auth, search, admin | Label tồn tại độc lập placeholder |
| `QuestionRenderer` | 7 loại câu hỏi | Thi, luyện, quiz | Một API component chung |
| `LearningCard` | word, character, grammar | Chủ đề, tra cứu, flashcard | Chữ Hán dùng `font.hanzi` |
| `MasteryIndicator` | compact, detailed | Chủ đề, kết quả, tiến độ | Màu + nhãn mức thành thạo |
| `DataTable` | default, selectable | Admin | Desktop; mobile dùng list/card |
| `Dialog/Sheet` | confirm, form, detail | Toàn hệ thống | Sheet ưu tiên trên mobile |
| `Toast` | success, error, info | Toàn hệ thống | Không dùng cho lỗi cần người dùng sửa form |
| `EmptyState` | first-use, no-result, completed | List/dashboard | Có hành động tiếp theo nếu tồn tại |
| `ErrorState` | inline, section, page | Toàn hệ thống | Có retry khi an toàn |
| `Skeleton` | text, card, table | Toàn hệ thống | Khớp gần đúng layout thật |
| `AIChatPanel` | collapsed, expanded, full-screen mobile | Ngữ cảnh USER được phép theo screen-fields.md | Panel nội tuyến; không che CTA/câu hỏi, không là screen riêng |

**Quy ước đặt tên:** component dùng `PascalCase`; token và prop dùng semantic
English; route label và nội dung người dùng dùng tiếng Việt. Component theo
domain nằm trong feature tương ứng; component dùng từ hai feature trở lên mới
được chuyển vào `shared/ui`.

---

## 8. Layout & responsive

| Breakpoint | Giá trị | Bố cục |
| --- | --- | --- |
| Mobile | `< 640px` | Một cột, bottom navigation, sheet toàn chiều rộng |
| Tablet | `640–1023px` | Một hoặc hai cột, sidebar thu gọn khi cần |
| Desktop | `1024–1439px` | Sidebar 240px + vùng nội dung |
| Wide | `≥ 1440px` | Nội dung giữa màn hình, tối đa 1280px |

**Luật chung:**

- Web authenticated SHALL dùng sidebar ở desktop và bottom navigation tối đa
  năm mục ở mobile.
- Nội dung học dài SHALL giới hạn 720px; dashboard và catalog MAY dùng 1280px.
- Grid card SHALL dùng tối thiểu 280px/card và tự giảm từ 4 → 3 → 2 → 1 cột.
- Màn làm bài SHALL giữ vùng câu hỏi là trọng tâm; điều hướng câu tách thành
  panel có thể thu gọn.
- Admin SHALL ưu tiên desktop; ở mobile các bảng SHALL đổi thành card/list,
  không ép người dùng cuộn ngang cho tác vụ chính.
- Web game SHALL dùng full viewport cho vùng chơi nhưng vẫn giữ nút thoát,
  trạng thái mạng và thông tin phiên ở safe area.
- React Native SHALL tôn trọng safe-area inset và font scaling của hệ điều hành.

---

## 9. Trạng thái màn hình

| Trạng thái | Quy tắc |
| --- | --- |
| Loading | Sau 300ms hiển thị skeleton đúng cấu trúc; không dùng spinner toàn trang nếu nội dung cũ còn dùng được |
| Empty | Nêu vì sao rỗng và hành động tiếp theo; không hiển thị khung trắng |
| Error | Nêu việc không hoàn thành, giữ dữ liệu người dùng đã nhập và có retry khi an toàn |
| Không có quyền | SCR-079 Access Denied: giải thích quyền cần thiết khi được phép và đường quay lại an toàn; không lộ nội dung private |
| Chưa đăng nhập | Chuyển tới đăng nhập và giữ `redirect`; game gọi `/api/auth/me` trước |
| Đang gửi | Disable đúng action đang gửi, giữ label kèm trạng thái, chống gửi lặp |
| Offline | Giữ dữ liệu đã cache ở chế độ chỉ đọc; đánh dấu rõ thao tác chưa đồng bộ |
| Chủ đề bị khóa | Hiển thị điều kiện 90% và tiến độ hiện tại; không chỉ phủ lớp mờ |
| Hết hạn mức | Chuyển tới SCR-030 Plan & Credits; nêu hạn mức, reset và quyền dùng tương ứng; không có checkout tiền thật |
| Khách dùng tính năng cần tài khoản | Chuyển SCR-002 Sign In, giữ đích đã kiểm tra; quyền public/private theo §5.2 |
| Bài thi chưa nộp | Khi rời trang phải xác nhận; không mất đáp án im lặng |
| Đáp án đã nộp | Khóa chỉnh sửa và chỉ hiện kết quả do server trả về |
| Token hết hạn | Thử refresh một lần; thất bại mới yêu cầu đăng nhập lại và giữ redirect |

---

## 10. Accessibility

| Yêu cầu | Ngưỡng |
| --- | --- |
| Độ tương phản chữ thường | WCAG 2.2 AA, tối thiểu 4.5:1 |
| Độ tương phản chữ lớn/UI | Tối thiểu 3:1 |
| Điều hướng bàn phím web | 100% control tương tác có thể dùng bằng bàn phím |
| Focus indicator | Ring 2px `color.focus`, offset 2px |
| Touch target | Tối thiểu 44×44px |
| Zoom web | Dùng được ở 200% tại viewport 1280px, không mất chức năng |
| Screen reader | Input, icon button, progress và trạng thái có accessible name/value |
| Motion | Tôn trọng `prefers-reduced-motion`; không có nội dung nhấp nháy >3 lần/giây |
| Audio | Nội dung học có audio SHALL có transcript hoặc phần chữ tương đương |
| Màu | Không dùng màu là tín hiệu duy nhất cho đúng/sai, khóa/mở hoặc mastery |

---

## 11. Yêu cầu phi chức năng

| Chỉ số | Ngưỡng | Đo bằng gì |
| --- | --- | --- |
| Lighthouse Accessibility | ≥ 95 cho route public và learner chính | Lighthouse CI, mobile preset |
| Lighthouse Performance | ≥ 85 cho landing/dashboard trên build production | Lighthouse CI, mobile preset |
| Largest Contentful Paint | ≤ 2.5 giây ở p75 | Web Vitals |
| Cumulative Layout Shift | ≤ 0.1 | Web Vitals |
| Interaction to Next Paint | ≤ 200ms ở p75 | Web Vitals |
| Phản hồi thao tác cục bộ | ≤ 100ms trước khi hiện pressed/loading state | Performance trace |
| Dịch câu 20 từ | ≤ 3 giây theo feature spec | API + UI integration test |
| Trợ lý AI | Phản hồi hoặc trạng thái streaming đầu tiên ≤ 5 giây | Integration monitoring |
| Responsive | Không có horizontal overflow ở 320, 375, 768, 1024, 1440px | Playwright screenshot test |
| Bundle route ban đầu | ≤ 250KB gzip JS cho web chính, không tính route lazy-loaded | Vite bundle report |

---

## 12. Luật UI (EARS)

**Ubiquitous**

- THE system SHALL dùng semantic token của mục 6 cho mọi màu, chữ, khoảng cách,
  radius và shadow.
- THE system SHALL hiển thị điều hướng, tên gọi và trạng thái nhất quán giữa web
  chính, web game và React Native.
- THE system SHALL trình bày chữ Hán bằng `font.hanzi` và UI tiếng Việt bằng
  `font.sans`.
- THE system SHALL đặt nội dung học và hành động tiếp theo trước nội dung quảng
  bá trên màn authenticated.
- THE system SHALL kèm text hoặc icon có nhãn cho mọi trạng thái truyền bằng màu.

**Event-driven**

- WHEN người dùng hoàn thành một hoạt động học, THE system SHALL hiển thị kết
  quả do server xác nhận và hành động tiếp theo.
- WHEN người dùng chọn một chủ đề bị khóa, THE system SHALL hiển thị ngưỡng 90%,
  tiến độ hiện tại và phần cần luyện.
- WHEN một request ghi bắt đầu, THE system SHALL khóa đúng trigger cho tới khi
  request kết thúc hoặc timeout.
- WHEN người dùng gửi bài thi, THE system SHALL yêu cầu xác nhận và chuyển sang
  trạng thái chỉ đọc sau khi server nhận thành công.
- WHEN web game tải xong, THE system SHALL xác minh phiên bằng `/api/auth/me`
  trước khi cho bắt đầu ván chơi.

**State-driven**

- WHILE dữ liệu đang tải quá 300ms, THE system SHALL hiển thị skeleton tương ứng
  với bố cục nội dung.
- WHILE ứng dụng offline, THE system SHALL phân biệt dữ liệu đã lưu cục bộ với
  dữ liệu đã đồng bộ server.
- WHILE AI đang phản hồi, THE system SHALL hiển thị trạng thái đang tạo và cho
  phép người dùng dừng phản hồi.

**Optional**

- WHERE dark mode IS ENABLED, THE system SHALL dùng một bộ token đã được kiểm
  tương phản riêng; không đảo màu tự động.
- WHERE animation học tập IS ENABLED, THE system SHALL vô hiệu hóa chuyển động
  không thiết yếu khi người dùng bật reduced motion.

**Unwanted ★**

- WHERE API trả lỗi validation, THE system SHALL giữ dữ liệu đã nhập, đặt lỗi
  cạnh field liên quan và đưa focus tới lỗi đầu tiên.
- WHERE mạng lỗi hoặc timeout, THE system SHALL không báo thành công và SHALL
  cung cấp retry không tạo request ghi trùng.
- WHERE người dùng không có quyền, THE system SHALL không render chớp nội dung
  bị cấm trước khi hiển thị trạng thái 403.
- WHERE token hết hạn, THE system SHALL thử refresh tối đa một lần và SHALL không
  tạo vòng lặp request vô hạn.
- WHERE người dùng rời bài thi chưa nộp, THE system SHALL cảnh báo nguy cơ mất
  tiến độ trước khi điều hướng.
- WHERE điểm game vượt trần hoặc phiên không hợp lệ, THE system SHALL từ chối
  hiển thị thành công và SHALL giải thích kết quả không được ghi nhận.
- WHERE một action liên quan điểm tài chính chưa được server xác nhận, THE system
  SHALL không cập nhật số dư như đã hoàn tất.
- WHERE hạn mức AI/dịch đã hết, THE system SHALL không mở loading vô hạn và SHALL
  hiển thị thời điểm reset hoặc cách dùng mã thẻ/quyền gói tại SCR-030.
- WHERE danh sách không có kết quả, THE system SHALL phân biệt “chưa có dữ liệu”
  với “bộ lọc không khớp”.
- WHERE ảnh hoặc audio tải lỗi, THE system SHALL giữ nội dung chữ thay thế và
  SHALL không làm vỡ bố cục.
- WHERE màu semantic không đạt ngưỡng tương phản của mục 10, THE system SHALL
  dùng biến thể đậm hơn thay vì giảm cỡ chữ hoặc bỏ nhãn.
- WHERE một modal hoặc sheet mở, THE system SHALL giữ focus trong overlay trên
  web và trả focus về trigger khi đóng.

Tỷ lệ Unwanted: 12/27 luật = 44,4%.

---

## 13. Acceptance Criteria

- [ ] Web chính, web game và React Native dùng cùng tên semantic color token.
- [ ] Không component nào chứa màu brand dạng hex trực tiếp ngoài file token.
- [ ] Sidebar desktop chuyển thành bottom navigation ở viewport dưới 640px.
- [ ] Các route chính hiển thị đủ loading, empty, error và unauthorized state.
- [ ] Button, input và touch target đạt tối thiểu 44×44px.
- [ ] Text/body và control đạt WCAG 2.2 AA theo mục 10.
- [ ] Màn cây chủ đề hiển thị khóa, ngưỡng 90% và tiến độ hiện tại bằng cả text
  lẫn hình ảnh.
- [ ] Màn thi cảnh báo khi rời bài chưa nộp và không cho sửa sau khi nộp thành công.
- [ ] Trang game xác minh `/api/auth/me`; WebView không nhận token qua URL.
- [ ] Role USER không nhìn thấy chớp nội dung admin trước khi kiểm quyền hoàn tất.
- [ ] Lighthouse và Web Vitals đạt các ngưỡng ở mục 11 trên build production.
- [ ] Không có horizontal overflow tại năm viewport kiểm thử đã quy định.

---

## 14. Out of Scope

- File này không định nghĩa logo cuối cùng, mascot, ảnh minh họa hoặc bộ icon
  độc quyền của thương hiệu.
- Không sao chép asset, nội dung hay font Gilroy từ Học Bá.
- Không quy định thuật toán FSRS, chấm điểm, chống gian lận hoặc business rule
  phía server ngoài cách thể hiện trạng thái.
- Không thiết kế lại website marketing `CNHSK.today` theo từng pixel.
- Dark mode chưa thuộc phạm vi bản 0.1.
- Thi mô phỏng nghiêm ngặt, gia sư marketplace và game đấu tay đôi không thuộc
  phạm vi MVP hiện tại.

---

## 15. Open Questions

Phải rỗng trước khi chuyển Status sang APPROVED.

| # | Câu hỏi | Ảnh hưởng nếu trả lời sai | Ai trả lời | Hạn |
| --- | --- | --- | --- | --- |
| 1 | Trình duyệt web, Android và iOS tối thiểu cần hỗ trợ phiên bản nào? | Quyết định polyfill, khả năng CSS và chi phí kiểm thử | Nhóm | Trước khi code khung |
| 2 | Logo CNHSK sẽ dùng wordmark hay biểu tượng riêng? | Ảnh hưởng header, app icon, splash và khoảng trống brand | Nhóm | Trước khi hoàn thiện shell |
| 3 | Có dark mode trong MVP không? | Cần thêm toàn bộ bộ token và test tương phản | Nhóm | Trước sprint UI thứ hai |
| 4 | Web game dùng Phaser 3 hay Canvas thuần? | Ảnh hưởng component shell và ngân sách bundle web game | Đạt | Trước khi dựng web game |
| 5 | Thư viện biểu đồ React Native nào được chấp nhận? | Ảnh hưởng màn tiến độ và tính nhất quán biểu đồ | Nhóm mobile | Trước màn thống kê |

---

## 16. Changelog

| Version | Ngày | Thay đổi | Người duyệt |
| --- | --- | --- | --- |
| 0.1 | 2026-09-28 | Bản đầu; hợp nhất web chính, web game và React Native | Chưa duyệt |

---

## Checklist trước khi approve

- [x] Có Context & Goal giải thích VÌ SAO
- [x] Không còn từ mơ hồ không có số đo
- [x] Mỗi Acceptance Criteria kiểm được
- [x] Đã nêu trạng thái rỗng, lỗi, biên
- [x] Không mâu thuẫn có chủ ý với spec khác
- [x] Tên gọi khớp tài liệu và codebase
- [x] Đã nêu ràng buộc tech stack
- [x] Out of Scope rõ ràng
- [ ] Mọi Open Question đã được trả lời
- [x] Tỷ lệ câu Unwanted ≥ 30%

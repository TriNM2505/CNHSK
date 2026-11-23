# CNHSK — Design Guidelines

| | |
| --- | --- |
| **Trạng thái** | Khung giao diện đã khoá cho đợt làm lại giao diện; inventory 79 màn FINAL |
| **Status** | APPROVED |
| **Version** | 1.9 |
| **Owner** | Nhóm CNHSK |
| **Approved by** | Leader nhóm |
| **Locked on** | 2026-11-17 |
| **Sprint** | Làm lại giao diện cnhsk-web |

---

## 0. Phạm vi và cách dùng tài liệu

Đây là nguồn quy định thiết kế dùng chung cho web chính (`cnhsk-web`) và web
game (`cnhsk-game`). Ứng dụng mobile đã bỏ khỏi phạm vi từ 2026-11-13. Khi tài
liệu này khác với một mockup hoặc quyết định miệng, nhóm SHALL cập nhật và duyệt
tài liệu trước khi triển khai khác đi. Tài liệu đã khoá: mọi thay đổi SHALL đi
qua PR sửa file này, được leader duyệt và ghi Changelog (§16) trước khi code
theo thay đổi đó.

### 0.1. Luật làm lại giao diện

Áp dụng cho mọi PR chỉ đổi giao diện (redesign). Mỗi thành viên được tự do bố
cục trong các màn mình phụ trách, trong giới hạn dưới đây.

| Mã | Luật |
| --- | --- |
| RD-01 | Không đổi route, `api/`, `pages/`, kiểu dữ liệu API hoặc backend. Chỉ sửa `features/<feature>/components/*View.tsx`, component riêng của feature và test tương ứng. |
| RD-02 | Mỗi màn SHALL dựng theo đúng một khuôn trang ở §8.1 và giữ đủ vùng bắt buộc của khuôn đó; vùng tự do được bố cục tuỳ ý. |
| RD-03 | Chỉ dùng component `shared/ui` (§7.1) và token §6. Không viết mã màu hex; không thêm `<button>`, `<input>`, `<select>`, `<textarea>` thô; không thêm class Tailwind giá trị tuỳ ý (`text-[…]`, `w-[…]`…) trừ khi ghi lý do trong PR. Breakpoint chỉ dùng `sm:` (640px), `lg:` (1024px), `xl:` (1440px); preset không có `md`, nên class `md:` không sinh CSS và không được dùng. |
| RD-04 | `shared/ui`, `shared/layout` và `tailwind.preset.cjs` chỉ người giữ nền chung sửa. Cần component mới dùng từ hai feature trở lên thì đề xuất để thêm vào `shared/ui`, không tự tạo bản sao trong feature. |
| RD-05 | Sửa test chỉ ở phần tìm phần tử (chữ hiển thị, role, nhãn). Không xoá hoặc nới assertion về hành vi, dữ liệu gửi đi, quyền hay trạng thái. |
| RD-06 | Giữ đủ trạng thái §9 (đang tải, rỗng, lỗi, không có quyền, đang gửi) và accessibility §10. |
| RD-07 | Gate FE xanh (lint, typecheck, test, build); không tràn ngang ở năm viewport §11; PR kèm ảnh trước/sau ở 375px và 1280px, đính kèm trong mô tả PR (không commit ảnh vào repo). |
| RD-08 | Mỗi PR một feature, nhánh `redesign/<feature>` tạo từ main mới nhất. |
| RD-09 | Thẩm mỹ trong vùng tự do (bố cục, cách phối token, hình minh hoạ, linh vật) do thành viên tự quyết; review không chặn merge vì lý do thẩm mỹ. Hình minh hoạ tre/trúc và linh vật gấu trúc dùng được ở mọi màn. |

### 0.2. Checklist review PR giao diện

Reviewer đánh dấu từng mục; một mục "Không" là chưa merge.

- [ ] Chỉ có file trong phạm vi RD-01; không có file `api/`, `pages/`, route, backend.
- [ ] Mỗi màn trong PR khớp khuôn ghi ở §8.2 và đủ vùng bắt buộc.
- [ ] Không có hex, không có control HTML thô mới, không có `md:`, class tuỳ ý (nếu có) đã ghi lý do.
- [ ] Không sửa `shared/ui`, `shared/layout`, `tailwind.preset.cjs` (trừ PR nền chung).
- [ ] Diff test chỉ đổi cách tìm phần tử; số assertion hành vi không giảm.
- [ ] Đủ trạng thái đang tải/rỗng/lỗi; chữ Hán dùng `font.hanzi`; focus nhìn thấy được.
- [ ] Gate FE xanh; ảnh 375px và 1280px không tràn ngang.

Tài liệu tham khảo:

- [Học Bá Education](https://hoc-ba.edu.vn/): nguồn tham khảo cho nhận diện đỏ
  Trung Hoa, màu nhấn vàng, độ tương phản và cảm giác giáo dục chuyên nghiệp.
  Nhóm đã được Học Bá cho phép dùng hình ảnh và asset của họ (leader xác nhận
  2026-11-17).
- [Hanzi Cozy Diary](https://CNHSK.today/): nguồn tham khảo cho cách nhóm
  công cụ theo kỹ năng, card tính năng, lộ trình học và phân cấp nội dung.
- [feature-tree.md](feature-tree.md): nguồn sự thật về tính năng và phạm vi.
- [kien-truc.md](kien-truc.md): nguồn sự thật về client và xác thực.
- Screen List (`docs/generated/cnhsk-screen-list.docx`) và Screen Flow (`docs/generated/cnhsk-screen-flow.png`): cấu trúc UI FINAL, 79 màn; tình trạng file nguồn xem §5.
- [screen-fields.md](screen-fields.md): đặc tả UI chi tiết duy nhất cho SCR-001–SCR-079.

Với Học Bá (leader chốt 2026-11-20, công ty Học Bá đã duyệt): logo Học Bá là logo của
sản phẩm ở mọi header, màn đang dựng và favicon (`shared/brand/HocBaLogo`); Landing
SCR-001 dựng theo bố cục, ảnh minh hoạ và nội dung giới thiệu của trang chủ Học Bá,
kèm một khối tính năng CNHSK. **Không** dùng ảnh, tên hay lời nhận xét của học viên
thật; không đưa font Gilroy vào repo. Các website tham khảo khác: chỉ học nguyên tắc
thiết kế, không sao chép logo, ảnh, nội dung hay bố cục.

---

## 1. Context & Goal

**Vì sao file này tồn tại:** CNHSK có hai client web do nhiều thành viên phát
triển song song. File này giữ một ngôn ngữ thị giác thống nhất, giảm quyết định
tùy ý và giúp người học nhận ra cùng một sản phẩm khi chuyển giữa web chính và
web game, kể cả khi mỗi thành viên tự bố cục màn mình phụ trách.

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
| Web game | React 18.3.x + TypeScript 5.7 + Vite 5.x; vùng chơi dựng bằng DOM/SVG, không dùng Phaser |
| Runtime web | Node.js 22 LTS hoặc Node.js 24 theo môi trường đã chốt |
| Design system web | Tailwind CSS 3.4 + preset `cnhsk-web/tailwind.preset.cjs` + component sở hữu trong source; chỉ dùng package đã duyệt ở ES-02 |
| Icon | Lucide React; nét 1.75–2 px |
| Biểu đồ | Dùng biểu đồ theo yêu cầu SCR-019; thư viện chưa được ES-02 duyệt thì phải xin duyệt |
| Thư viện bị cấm | Không trộn thêm Material UI, Ant Design, Bootstrap hoặc UI kit toàn cục khác |
| Nền tảng phải hỗ trợ | Desktop web và mobile web (trình duyệt) |
| Trình duyệt tối thiểu | Chrome, Edge, Firefox hai bản mới nhất; Safari 16+ trên macOS và iOS; Chrome Android hai bản mới nhất |

THE system SHALL dùng component semantic như `Button`, `Card`, `Progress`,
`Dialog`, `FormField`; không tạo bản sao theo từng trang.

---

## 3. Actors & phạm vi màn hình

| Actor | Quyền | Thiết bị chính | Nhóm màn hình được dùng |
| --- | --- | --- | --- |
| Khách | Xem giới thiệu và trang công khai, tra từ điển, đăng ký/đăng nhập — không dùng thử tính năng (mục 5.2) | Desktop web, mobile web | Public, auth |
| LEARNER | Học, luyện, thi, chơi, dùng thư viện và cộng đồng | Desktop web, mobile web | Toàn bộ màn học viên |
| TEACHER | Duyệt câu hỏi AI, chấm bài thuê | Desktop web | SCR-056–SCR-059; học viên chỉ khi có LEARNER |
| MODERATOR | Kiểm duyệt cộng đồng | Desktop web | SCR-060–SCR-063; học viên chỉ khi có LEARNER |
| CONTENT_EDITOR | Nhập dữ liệu, quản lý đề, kho câu hỏi (xem, lưu trữ), cuộc thi, tài liệu; không duyệt hay sửa nội dung câu hỏi | Desktop web | SCR-059 (chỉ xem, lưu trữ), SCR-064–SCR-072; học viên chỉ khi có LEARNER |
| ADMIN | Người dùng, phân quyền, cấu hình được phép, lô AI; tài chính: mã thẻ, gói dịch vụ, sổ cái | Desktop web | SCR-073–SCR-078; học viên chỉ khi có LEARNER |

> **Sáu actor**, khớp Hiến pháp mục *Sáu actor* (v1.6.0). Job định kỳ không phải actor. Quyền quản trị
> tách thành `CONTENT_EDITOR` (nội dung) và `ADMIN` (tài khoản, hệ thống, tài chính — RFC R9); mã `ACCOUNTANT`
> còn trong DB là mã dự phòng, không phải actor — xem mục 5.3. Chỉ `ADMIN` mang chữ "Admin".
>
> **Chỉ `TEACHER`** duyệt câu hỏi AI và sửa nội dung câu hỏi (SCR-058/SCR-059), theo `BUS-07`; `CONTENT_EDITOR` chỉ xem và lưu trữ câu ở SCR-059.
> AI Review Queue SCR-058 chỉ cho `TEACHER`; Question Edit SCR-059 dùng chung theo quyền được gán; không kế thừa LEARNER.

**Ranh giới giữa các nền tảng:**

| Tính năng | Web chính (`cnhsk-web`) | Web game (`cnhsk-game`) |
| --- | --- | --- |
| Tài khoản, học tập, thi, tra cứu | Có | Không |
| Game | Điều hướng sang web game | Có |
| Blog, quiz, xếp hạng | Có | Chỉ bảng hạng game |
| Quản trị và duyệt nội dung | Có, tối ưu desktop | Không |
| Thanh toán/điểm | Có | Không |

---

## 4. User flow

★ **Xác thực và vào hệ thống:** Landing → Đăng ký (nhận email xác thực) hoặc
Đăng nhập → xác thực bằng cookie dùng chung cho web chính và web game → khu
được phép theo role được gán (LEARNER vào SCR-010).
Cơ chế chặn đăng nhập theo nguồn và lỗi tương ứng xem SCR-002; không suy ra quyền kế thừa từ việc đăng nhập.
Đặt lại mật khẩu → thu hồi **toàn bộ** refresh token.

★ **Học theo chủ đề:** Đăng nhập → Trang chủ học tập → Chọn chủ đề đã mở →
Học từ → Luyện nhận diện/nghe/viết → Kiểm tra → Xem mastery và chủ đề kế tiếp.

★ **Làm đề và sửa điểm yếu:** Chọn HSK/dạng đề → Làm bài → Xác nhận nộp →
Server chấm → Xem lỗi theo điểm kiến thức → Luyện ngay phần yếu.

★ **Ôn tập đến hạn:** Trang chủ → Danh sách đến hạn → Trả lời → Tự đánh giá
nếu là flashcard → Nhận lịch ôn mới → Xem tiến độ.

**Tra cứu để học:** SCR-024 → SCR-025; dịch đoạn là SCR-026 riêng.
LEARNER có thể lưu vào SCR-027/SCR-029 theo quyền; khách chỉ đọc public projection.

**Chơi game trên web:** Web chính → Web game → `/api/auth/me` → Game Hub
SCR-035 → Game Session SCR-036 → kết quả cá nhân được server xác nhận ngay trong
SCR-036 → về hub/trang chính. Game Leaderboard SCR-047 là màn xếp hạng V2 riêng.

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

Learner workspace và LEARNER services yêu cầu role LEARNER được gán tường minh. Translation tách khỏi Dictionary Search/Detail; Personal Notes và bộ flashcard cá nhân không xuất hiện như chức năng dùng thử cho khách. SCR-029 chỉ có public projection khi khả năng chia sẻ V2 được bật; owner edit vẫn kiểm quyền sở hữu. SCR-042 theo nhánh Community Actions của flow, cần LEARNER; không tự biến thành route khách chỉ vì tên có chữ Profile. Contest Results SCR-051 thuộc nhánh LEARNER đã xác thực của flow, đồng thời kiểm publication/ownership ở screen-fields.md. Link kết quả từ thông tin cuộc thi công khai phải qua ranh giới đăng nhập; không tự cấp guest access hoặc mở đáp án riêng của người khác.

Trang công khai dùng một thanh điều hướng cho mọi người; người đã đăng nhập thấy menu tài khoản thay cho nút Đăng nhập/Đăng ký, menu «Luyện tập» có thêm trang riêng của người học, và người có role nhân sự có thêm menu «Công việc» (mỗi role một cột).

Từ tài nguyên công khai, action cần LEARNER dẫn tới SCR-002 với đích trở lại đã kiểm tra. Đăng ký thành công tới SCR-004; xác thực email là hướng dẫn, không phải cửa chặn việc học. Hạn mức khách cho tra cứu là chống lạm dụng, không cấp dùng thử học/AI/game. Ngưỡng/cách đếm còn khác nhau giữa auth spec và UC thư viện, phải dùng Open Issues của SCR-024/SCR-025 để chốt contract trước khi triển khai.

### 5.3. Role navigation và shared pages

| Role gán tường minh | Menu/entry được phép |
|---|---|
| LEARNER | SCR-010 và learner/LEARNER service screens theo §5.1; Account SCR-007/SCR-008 và nhắc học SCR-009 theo actor của từng màn |
| TEACHER | SCR-056/SCR-057; SCR-058/SCR-059 với quyền review nội dung |
| MODERATOR | SCR-060–SCR-063 |
| CONTENT_EDITOR | SCR-059 (xem, lưu trữ); SCR-064–SCR-072 |
| ADMIN | SCR-073–SCR-078 |

Không kế thừa role: TEACHER không mặc nhiên có LEARNER; ADMIN không mặc nhiên có quyền tài chính, nội dung hoặc kiểm duyệt. Một người có nhiều role nhìn thấy hợp các menu được gán. Role Navigation trong ảnh là điểm điều phối, không phải một trang dashboard mới. Với nhiều role, dùng đích được phép người dùng chọn hoặc đích an toàn đã kiểm quyền; chưa có quyết định xếp thứ tự ưu tiên role.

Question Edit SCR-059 là một màn chung cho TEACHER/CONTENT_EDITOR và nguồn vào từ AI Review Queue SCR-058 hoặc Question Bank SCR-064 theo quyền tương ứng; chỉ TEACHER thấy nút sửa và duyệt, CONTENT_EDITOR chỉ xem và lưu trữ. Quyền review không tự cấp toàn bộ quản lý Question Bank. Không tạo editor riêng theo role. Teacher queue SCR-056 chỉ có metadata được phép; bài viết/hồ sơ nhận việc trong SCR-057 tuân điều kiện claim và sở hữu, không đưa thông tin nhạy cảm vào queue.

### 5.4. Screen Flow và UI patterns

Ảnh flow chuẩn tắc: `docs/generated/cnhsk-screen-flow.png`. Chưa thể hiển thị lại ảnh do tình trạng nguồn ghi ở đầu §5; các flow lịch sử không thay thế ảnh đã duyệt.

Ảnh và Screen List đã đọc từ docs/generated là artifact FINAL; đợt đồng bộ này không sửa hoặc tái tạo chúng. Hiện chưa thể kiểm tra lại file/hash do tình trạng nguồn nêu trên. Các sơ đồ cũ tại [screen-flow/](screen-flow/README.md) là lịch sử mô hình UI trước đây, không dùng để xác định inventory, actor hoặc điều hướng hiện hành.

**List/detail:** list giữ tìm kiếm/bộ lọc khi quay lại; mở detail của đúng item bằng ID thật. Detail không tự mở nội dung private chưa qua ownership. Các cặp list/detail của community, teacher grading, moderation, import và users giữ màn riêng theo inventory.

**Create/edit:** SCR-041, SCR-066 và SCR-071 chứa hai chế độ trong cùng một màn; tạo/sửa không sinh ID mới. Modal tạo bộ flashcard ở SCR-028, card editor ở SCR-029, quản lý reference ở SCR-072, batch actions ở SCR-073, điều chỉnh ledger ở SCR-075 và role assignment ở SCR-077 là nội tuyến/dialog, không phải màn bổ sung.

**Sessions/results:** SCR-021/SCR-022, SCR-044/SCR-045 và SCR-050/SCR-051 giữ attempt và result riêng; kết quả game thuộc SCR-036, Dictation/Shadowing thuộc SCR-038. Không đổi leaderboard thành trang kết quả cá nhân. Chỉ kết quả server xác nhận mới được trình bày là đã lưu.

**Shared utilities:** SCR-079 giải thích thiếu quyền và cho về khu được phép. Thiếu phiên đưa SCR-002; thiếu entitlement đưa SCR-030; không tạo thêm trang lỗi hoặc billing redirect screen. AI Assistant là panel có ngữ cảnh, không là màn riêng; điều kiện hiện trong exam/contest phải theo source và Open Issues, không tự cung cấp lời giải khi đang làm bài.

**Frontend evidence (2026-11-16, main `e793e6c`):** route và quyền nằm ở `cnhsk-web/src/app/routeAccess.ts`; mỗi màn đăng ký trong `features/<feature>/screens.ts`; menu ở `cnhsk-web/src/shared/layout/navigation.ts`. Route là hợp đồng đã phát hành (email, liên kết chia sẻ dùng chúng) — đợt làm lại giao diện không đổi route (RD-01).

### 5.5. Mockup requirements và scope

Mỗi mockup dùng đúng SCR ID và Screen Name; designer lấy field/control/list column/action/validation/empty/error/role difference từ 23 mục của màn trong screen-fields.md. Mỗi màn có đúng một readiness: READY FOR MOCKUP, READY WITH PLACEHOLDERS hoặc BLOCKED FOR FINAL MOCKUP. Placeholder chỉ thể hiện vấn đề thật chưa chốt; không dựng giá trị business, số liệu hoặc quyền giả. Modal không thay thế một detail screen đã được inventory duyệt.

Không thêm checkout tiền thật, schema import chưa duyệt, grading policy chưa duyệt, setting catalogue chưa duyệt, role inheritance hoặc màn hiển thị secret. Scope tính năng theo feature-tree.md; Feature không đồng nhất Screen. Mockup HTML nằm ngoài repo chỉ là tham khảo lịch sử, phải đối chiếu 79 màn trước khi dùng và phải viết lại bằng React khi triển khai.

### 5.6. Trạng thái triển khai (2026-11-16)

| Nhóm | Màn | Ghi chú |
|---|---|---|
| Đã có route trong `cnhsk-web` | 72 màn | Toàn bộ SCR-001–SCR-079 trừ bảy màn bên dưới; gán khuôn ở §8.2 |
| Thuộc `cnhsk-game` | SCR-035, SCR-036 | Game Hub và Game Session; dùng `GameShell` của web game |
| Gác (spec 016 video) | SCR-037, SCR-038 | Ngoài phạm vi đợt làm lại giao diện |
| Chưa làm (task spec đang mở) | SCR-042, SCR-052, SCR-058 | Phụ lục 009, phụ lục 014, spec 024; khi làm dùng khuôn ghi ở §8.2 |

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

Bản này giữ bộ font hiện có trong preset; đổi font (kể cả Gilroy như Học Bá) là
thay đổi nền chung theo RD-04, ảnh hưởng mọi màn.

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

Bảng dưới phản ánh code trên main ngày 2026-11-16. Component chưa có trong bảng
SHALL được đề xuất theo RD-04 trước khi dùng.

### 7.1. Component dùng chung — `cnhsk-web/src/shared/ui`

| Component | Biến thể / prop chính | Dùng cho | Ghi chú |
| --- | --- | --- | --- |
| `Button` | variant: primary, secondary, outline, ghost, danger · size: sm, md · dạng nút, link nội bộ, link ngoài | Mọi hành động | Một primary CTA mỗi vùng |
| `IconButton` | default, danger | Header, bảng, card | Luôn có accessible label |
| `Card` | default, interactive, selected | Chủ đề, bộ thẻ, game, dashboard | Toàn card chỉ clickable khi có affordance; trạng thái khoá thể hiện bằng nội dung + `StatusBadge` |
| `StatusBadge` | success, warning, danger, info, neutral | Mọi màn | Icon + text, không chỉ màu |
| `Progress` | tone: brand, success, accent | Dashboard, học, tiến độ | Luôn có giá trị dạng text |
| `LevelBadge` | HSK1–HSK7-9 | Tra cứu, chủ đề, đề thi | Không gán màu ngẫu nhiên theo trang |
| `FormField` | label, hint, error; bọc text, password, search, select, textarea | Mọi form | Label tồn tại độc lập placeholder |
| `HanziText` | size: lg, md, sm | Mọi chữ Hán được học | Dùng `font.hanzi`; không tự đặt font chữ Hán |
| `PageHeader` | title, mô tả, actions | Đầu mọi màn | Vùng bắt buộc của mọi khuôn §8.1 |
| `Section` | title, actions | Chia khối trong màn | Tiêu đề `text.h2` |
| `StatTile` | label, giá trị | Dashboard, kết quả, tiến độ | Số liệu kèm nhãn |
| `Tabs` | danh sách tab có nhãn | Chuyển nhóm nội dung trong một màn | Không thay cho điều hướng giữa màn |
| `DataTable` | tĩnh, có tương tác (chọn/hành động dòng) | Bảng admin, lịch sử | Mobile chuyển list/card theo §8 |
| `Pagination` | số trang, cỡ trang | Danh sách, bảng | Giữ bộ lọc khi đổi trang |
| `PageState` | loading, empty, error, ready | Bọc nội dung màn | Một cách hiện trạng thái §9 thống nhất |
| `EmptyState` | title, mô tả, hành động | Danh sách/dashboard rỗng | Phân biệt "chưa có dữ liệu" với "bộ lọc không khớp" |
| `ErrorState` | title, retry | Lỗi section hoặc trang | Có retry khi an toàn |
| `Skeleton` | text, card, table | Đang tải | Khớp gần đúng bố cục thật |
| `ConfirmPanel` | xác nhận nội tuyến | Thao tác phá huỷ, nộp bài, rời phiên | Thay cho dialog; tuân luật focus overlay ở §12 |
| `Toast` / `useToast` | success, error, info | Thông báo ngắn | Không dùng cho lỗi cần sửa form |

Layout dùng chung ở `cnhsk-web/src/shared/layout`: `AppShell`, `PublicLayout`,
`LearnerLayout`, `AdminLayout`, `RoleLayout`, `AccountMenu`, `navigation.ts`.

### 7.2. Component theo domain (không đưa vào `shared/ui` khi chưa có feature thứ hai dùng)

| Vai trò trong bản 0.1 | Component thật | Vị trí |
| --- | --- | --- |
| Câu hỏi luyện/thi (`QuestionRenderer`) | `ChoiceQuestion` | `features/practice/components` |
| Trợ lý AI (`AIChatPanel`) | `AssistantPanel`, `AssistantHost` | `features/assistant/components` |
| Ôn flashcard | `FlashcardReviewView` | `features/library/components` |
| Mức thành thạo (`MasteryIndicator`) | `MasteryDashboardView`, `MasteryProgressView` | `features/mastery/components` |

Chưa có và chưa cần tạo trong đợt làm lại giao diện: `LearningCard`,
`Dialog/Sheet` dạng overlay. Nếu nhiều feature cần, đề xuất theo RD-04.

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

### 8.1. Khuôn trang

Mỗi màn SHALL dựng theo đúng một khuôn (RD-02). **Vùng bắt buộc** phải có và giữ
thứ tự từ trên xuống; **vùng tự do** do người làm màn quyết định bố cục, minh hoạ
và cách nhóm nội dung, miễn dùng §6 và §7.1.

| Mã | Khuôn | Vùng bắt buộc (thứ tự) | Vùng tự do | Bề rộng |
| --- | --- | --- | --- | --- |
| K1 | Danh sách / catalog | `PageHeader` → thanh tìm kiếm/bộ lọc (nếu màn có lọc) → danh sách hoặc lưới card → `Pagination` (nếu phân trang) · trạng thái §9 qua `PageState`/`EmptyState` | Kiểu card, số cột, khối gợi ý/nổi bật phía trên danh sách | `content.max` 1280px |
| K2 | Chi tiết | `PageHeader` (tiêu đề + hành động chính) → nội dung chính → khối liên quan/hành động phụ · liên kết quay lại danh sách | Chia cột nội dung/phụ, cách trình bày metadata | Nội dung đọc 720px; trang có cột phụ hoặc bảng dữ liệu tối đa 1280px |
| K3 | Form / cài đặt / xác thực | `PageHeader` → các nhóm `FormField` có nhãn → vùng hành động (primary cuối form) · lỗi cạnh field (§12) | Chia nhóm field, minh hoạ bên cạnh trên desktop | 720px; màn xác thực (SCR-002–SCR-006) căn giữa |
| K4 | Phiên làm bài / học từng bước | Thanh phiên (tiến độ câu, thời gian nếu có, nút thoát; hỏi xác nhận khi rời phiên làm mất dữ liệu chưa lưu/chưa nộp) → vùng câu hỏi/nội dung trọng tâm → vùng trả lời và hành động chính · phản hồi đúng/sai bằng text + màu | Hiệu ứng chuyển câu, bố cục vùng trả lời, panel điều hướng câu (thu gọn được) | 720px vùng trọng tâm; không sidebar che câu hỏi |
| K5 | Tổng quan / kết quả | `PageHeader` → khối số liệu chính (`StatTile`/`Progress`, giá trị server xác nhận) → hành động tiếp theo → các `Section` chi tiết | Thứ tự và bố cục các `Section`, biểu đồ, minh hoạ | 1280px |
| K6 | Bảng quản trị | `PageHeader` (+ hành động tạo/nhập nếu màn có) → bộ lọc (nếu màn có lọc) → `DataTable` → `Pagination` (nếu phân trang) · hành động dòng có xác nhận khi phá huỷ | Cột phụ, nhóm bộ lọc, thẻ tóm tắt phía trên bảng | 1280px; mobile đổi bảng thành list/card |

Landing SCR-001 dùng K5 và là màn duy nhất được dùng `text.display` (hero).

### 8.2. Gán màn vào khuôn

| Khuôn | Màn |
| --- | --- |
| K1 | SCR-011, 017, 020, 024, 027, 028, 031, 033, 039, 043, 046, 047, 048, 053, 054 · chưa làm: 052 · `cnhsk-game`: 035 |
| K2 | SCR-004, 018, 025, 029, 034, 040, 049, 055, 057, 061, 063, 069, 077, 079 · chưa làm: 042 |
| K3 | SCR-002, 003, 005, 006, 007, 008, 009, 026, 032, 041, 059, 066, 071, 078 |
| K4 | SCR-012, 013, 014, 015, 016, 021, 023, 044, 050 · `cnhsk-game`: 036 (theo `GameShell`) |
| K5 | SCR-001, 010, 019, 022, 030, 045, 051 |
| K6 | SCR-056, 060, 062, 064, 065, 067, 068, 070, 072, 073, 074, 075, 076 · chưa làm: 058 |

Màn có nhiều route (ví dụ SCR-014 luyện viết và thử thách, SCR-022 kết quả thi
và kiểm tra xếp lớp) dùng cùng khuôn cho mọi route của màn đó. Muốn đổi khuôn của
một màn phải sửa bảng này theo §0.

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
  chính và web game.
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

- [ ] Web chính và web game dùng cùng tên semantic color token.
- [ ] Mọi màn trong §8.2 dựng theo đúng khuôn được gán và đủ vùng bắt buộc §8.1.
- [ ] Mọi PR làm lại giao diện qua đủ checklist §0.2.
- [ ] Không component nào chứa màu brand dạng hex trực tiếp ngoài file token.
- [ ] Sidebar desktop chuyển thành bottom navigation ở viewport dưới 640px.
- [ ] Các route chính hiển thị đủ loading, empty, error và unauthorized state.
- [ ] Button, input và touch target đạt tối thiểu 44×44px.
- [ ] Text/body và control đạt WCAG 2.2 AA theo mục 10.
- [ ] Màn cây chủ đề hiển thị khóa, ngưỡng 90% và tiến độ hiện tại bằng cả text
  lẫn hình ảnh.
- [ ] Màn thi cảnh báo khi rời bài chưa nộp và không cho sửa sau khi nộp thành công.
- [ ] Trang game xác minh `/api/auth/me`; không nhận token qua URL.
- [ ] Role LEARNER không nhìn thấy chớp nội dung admin trước khi kiểm quyền hoàn tất.
- [ ] Lighthouse và Web Vitals đạt các ngưỡng ở mục 11 trên build production.
- [ ] Không có horizontal overflow tại năm viewport kiểm thử đã quy định.

---

## 14. Out of Scope

- Logo sản phẩm là logo Học Bá (§0, v1.7); file này không định nghĩa ảnh minh họa
  hay bộ icon độc quyền; linh vật dùng theo RD-09.
- Không quy định thuật toán FSRS, chấm điểm, chống gian lận hoặc business rule
  phía server ngoài cách thể hiện trạng thái.
- Không thiết kế lại website marketing `CNHSK.today` theo từng pixel.
- Dark mode không thuộc phạm vi bản 1.1.
- Ứng dụng mobile (React Native, WebView) đã bỏ từ 2026-11-13.
- Đổi điều hướng/menu hoặc route trong đợt làm lại giao diện (RD-01).
- Thi mô phỏng nghiêm ngặt, gia sư marketplace và game đấu tay đôi không thuộc
  phạm vi MVP hiện tại.

---

## 15. Open Questions

Phải rỗng trước khi chuyển Status sang APPROVED.

Không còn câu hỏi mở. Các câu bản 0.1 đã chốt:

| # | Câu hỏi | Chốt (2026-11-16) |
| --- | --- | --- |
| 1 | Trình duyệt tối thiểu? | Chrome, Edge, Firefox hai bản mới nhất; Safari 16+ (macOS, iOS); Chrome Android hai bản mới nhất (§2) |
| 2 | Logo wordmark hay biểu tượng? | Dùng logo Học Bá ở mọi header, màn đang dựng và favicon; bản trắng trên nền đỏ (cập nhật 2026-11-20, §0) |
| 3 | Dark mode trong MVP? | Không (§14) |
| 4 | Web game dùng Phaser 3 hay Canvas? | React + DOM/SVG, không Phaser (§2) |
| 5 | Thư viện biểu đồ React Native? | Không áp dụng — đã bỏ mobile |

---

## 16. Changelog

| Version | Ngày | Thay đổi | Người duyệt |
| --- | --- | --- | --- |
| 0.1 | 2026-09-28 | Bản đầu; hợp nhất web chính, web game và React Native | Chưa duyệt |
| 1.1 | 2026-11-16 | Bỏ React Native/WebView; thêm luật làm lại giao diện (§0.1) và checklist review (§0.2); trạng thái triển khai 72 màn (§5.6); đồng bộ §7 với `shared/ui` thật; thêm 6 khuôn trang và bảng gán màn (§8.1, §8.2); chốt Open Questions; khoá tài liệu | Leader nhóm |
| 1.2 | 2026-11-17 | Được dùng hình ảnh/asset Học Bá (§0, §14, §15); thêm RD-09 tự do thẩm mỹ, linh vật chỉ ở màn xác thực; cấm `md:` (RD-03, §0.2); ảnh PR đính kèm, không commit (RD-07); K2 rộng 1280px khi có bảng; K4 chỉ xác nhận thoát khi mất dữ liệu; K6 bộ lọc/phân trang khi màn có | Leader nhóm |
| 1.3 | 2026-11-17 | RD-09: hình minh hoạ tre/trúc dùng được ở mọi màn; linh vật gấu trúc vẫn chỉ ở màn xác thực | Leader nhóm |
| 1.4 | 2026-11-17 | RD-09: linh vật gấu trúc dùng được ở mọi màn | Leader nhóm |
| 1.5 | 2026-11-20 | §3, §5.3: sáu actor theo Hiến pháp v1.6.0 — tài chính SCR-073–075 thuộc `ADMIN`, `ACCOUNTANT` là mã dự phòng; `TEACHER` hoặc `CONTENT_EDITOR` duyệt câu hỏi AI | Leader nhóm |
| 1.6 | 2026-11-20 | §3, §5.3: chỉ `TEACHER` duyệt và sửa câu hỏi AI (SCR-058, nút sửa/duyệt SCR-059) theo Hiến pháp v1.7.0; `CONTENT_EDITOR` chỉ xem, lưu trữ | Leader nhóm |
| 1.7 | 2026-11-20 | §0, Open Questions: logo Học Bá thay wordmark CNHSK; Landing SCR-001 theo bố cục và nội dung trang chủ Học Bá (công ty Học Bá đã duyệt), không dùng ảnh/nhận xét học viên thật | Leader nhóm |
| 1.8 | 2026-11-22 | §5.2: thanh điều hướng khách cao 64px (dưới `lg`) / 96px (từ `lg`), menu thả kiểu Học Bá gồm Giới thiệu, Luyện tập (cần đăng nhập), Tra cứu, Cộng đồng, Trò chơi; dưới `lg` là menu ba gạch; Landing bỏ khối «Tự luyện mỗi ngày trên CNHSK», chỉ người đã đăng nhập thấy «Khu vực của bạn»; thêm token `size.touch` | Leader nhóm |
| 1.9 | 2026-11-23 | §5.2: trang công khai dùng chung thanh điều hướng cho cả người đã đăng nhập (thay nút Đăng nhập/Đăng ký bằng menu tài khoản); «Luyện tập» thêm cột «Của bạn», thêm menu «Công việc» theo role nhân sự; Landing bỏ khối «Khu vực của bạn» | Leader nhóm |

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
- [x] Mọi Open Question đã được trả lời
- [x] Tỷ lệ câu Unwanted ≥ 30%

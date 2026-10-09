<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

# CLAUDE.md — Bộ nhớ dự án CNHSK

> Đọc `AGENTS.md` trước để biết **luật**. File này chứa **kiến thức**: dự án như thế nào,
> vì sao quyết định như vậy, đã học được gì.

## TL;DR — 60 giây

Nền tảng học tiếng Trung cho người Việt. Đồ án tốt nghiệp IT, FPT University, 6 người,
11–12 tuần. **Modular Monolith**: một Spring Boot `cnhsk-api` cổng 8080, bốn module,
một PostgreSQL `cnhsk_db` ba schema, một Redis. Ba frontend dùng chung backend.

**Trạng thái hiện tại:** scope · kiến trúc · database đã chốt. **Chưa viết dòng code
nghiệp vụ nào.** Đang ở Tuần 1 của quy trình SDD.

## Kiến trúc

```
Web chính (cnhsk.com, React 18)      ──cookie──┐
Trang game (game.cnhsk.com,                    │
            React + Phaser)          ──cookie──┼──► api.cnhsk.com:8080
Mobile (React Native)             ──header─────┤    MỘT Spring Boot app
  └─ WebView → tải chính trang game            │    ├─ module auth
     (app tiêm token vào header)               │    ├─ module learning
                                               ┘    ├─ module community (gồm game)
                                                    └─ module shared
                                                         │
                             PostgreSQL 5432 cnhsk_db    │  + Redis 6379
                             ├─ schema auth       (4 bảng)
                             ├─ schema learning  (40 bảng)
                             └─ schema community (15 bảng)
```

**Tách trang game KHÔNG phải tách microservice.** `game.cnhsk.com` là web app riêng
nhưng vẫn gọi về cùng một backend.

## Quyết định kiến trúc (ADR)

### ADR-001 · Modular Monolith thay vì Microservices

Nhóm đã thiết kế bản microservice (Gateway + 3 service) rồi mới bỏ. Lý do: chi phí phân
tán — lỗi N+1, transaction phân tán, đồng bộ dữ liệu — **lớn hơn lợi ích** ở quy mô 6
người / 3 tháng. Nhóm cũng không thuần thục microservice.

*Giữ bản thiết kế microservice để trình giảng viên: chứng minh đã cân nhắc, không phải
"không làm vì khó".*

### ADR-002 · Một database ba schema thay vì ba database

Bản v3 từng dùng 3 database riêng và cần **đồng bộ lúc 2h sáng**. Bản v5 gộp lại một
database ba schema → **bỏ hẳn cơ chế đồng bộ**, xoá 4 bảng thừa (`user_refs`,
`topic_refs`, `vocab_refs`, `sync_run_items`), và xoá luôn độ trễ một ngày của dữ liệu.

Module gọi nhau bằng lời gọi hàm, không qua HTTP.

### ADR-003 · Cookie cho web, header cho mobile

Web chính và trang game dùng cookie đặt ở tên miền cha `.cnhsk.com` để chia sẻ đăng nhập.
React Native không có cookie như trình duyệt nên mobile dùng header `Authorization`.

**Cái giá phải trả:** cookie mở ra rủi ro CSRF. Ba lớp phòng: `SameSite=Lax` + CORS ghi
rõ từng tên miền + CSRF token cho thao tác dính tiền.

### ADR-004 · Chốt phiên bản theo máy nhóm, không theo bản mới nhất

Quét máy thật ngày 15/09. Project `Move_home` của nhóm dùng Spring Boot 3.5.14 + Java 17
+ PostgreSQL + WebSocket STOMP + Flyway + JWT — gần đúng bộ mà CNHSK cần.

Spring Boot 4.x **có trong `.m2` nhưng chưa từng dùng thật**. React 19 có nhưng ít tài
liệu tiếng Việt. Chọn thứ nhóm đã quen.

### ADR-005 · React Native, dự phòng Flutter

Máy có 2 project Flutter, **không có** React Native nào. Chọn RN nghĩa là học hai thứ mới
cùng lúc (React cho web, RN cho mobile).

Lý do vẫn giữ RN: dùng chung TypeScript với web, chia sẻ định nghĩa kiểu và hàm gọi API.
**Nếu tới giai đoạn mobile mà thời gian eo hẹp → Flutter là phương án dự phòng an toàn**,
backend không đổi gì vì cả hai đều gọi REST API.

## Lessons learned

### LESSON-001 · Đặt `Domain` cho cookie là con dao hai lưỡi

Khai báo `domain=cnhsk.com` khiến cookie gửi tới **mọi** tên miền phụ, kể cả những cái
chưa tạo. Nếu sau này có `blog.cnhsk.com` do bên thứ ba quản lý, nó cũng nhận được cookie
đăng nhập. Chỉ khai báo `Domain` khi thật sự cần chia sẻ.

### LESSON-002 · `SameSite=Lax` không chặn được tên miền phụ của chính mình

Lax chỉ chặn CSRF từ tên miền khác. `game.cnhsk.com` và `cnhsk.com` là cùng site nên
không được bảo vệ. Đây là lý do cần thêm CSRF token cho thao tác ghi.

### LESSON-003 · Trang game chạy trên trình duyệt → người dùng sửa được mọi thứ

Không bao giờ tin điểm client gửi. Server phải kiểm ba điều: trần điểm của game, thời
gian chơi tối thiểu, tần suất mỗi tài khoản. Mastery chỉ cộng cho từ server đã phát.

### LESSON-004 · Kế hoạch đang vượt 15% công suất

WBS ước tính 1.288 giờ, năng lực khả dụng 1.125 giờ. Nhóm chọn tăng lên 20 giờ/tuần thay
vì cắt tính năng. **Sai số ước tính ±25–30%** — sau tuần 3 phải đo tốc độ thật rồi ước
tính lại.

Thứ tự cắt đã quyết trước: blog+kiểm duyệt → giao diện cộng đồng → quiz → 2 game →
thông báo mobile → chấm thuê. **Không cắt lõi:** học tập, thi, mastery, thanh toán, mobile.

### LESSON-005 · Trello không có chức năng import CSV

Đã kiểm tài liệu chính thức. Phải dùng cách dán nhiều dòng hoặc script gọi API.

## UI structure hiện hành

**79 màn FINAL**, SCR-001–SCR-079. Nguồn ID/tên/Feature/Description: `docs/generated/cnhsk-screen-list.docx`; nguồn grouping/public-private/role navigation: `docs/generated/cnhsk-screen-flow.png`. Chi tiết UI duy nhất: `docs/reference/screen-fields.md`; design system/IA: `docs/reference/design.md`. Không tính router hoặc AI Assistant panel thành màn, không gộp các màn đã tách, không suy ra quyền USER từ role quản trị.

Frontend chưa tồn tại; route/page/menu/guard chưa xác nhận. Mockup cũ và `docs/reference/screen-flow/` chỉ là lịch sử. Khi dựng frontend `cnhsk-web`, map về inventory 79 màn và giữ Question Edit SCR-059 là một màn chung.

## Cấu trúc thư mục

```
C:\CLHSK\learning-service\          ← thư mục gốc = backend cnhsk-api
├── .specify/memory/constitution.md  ← LUẬT dự án
├── AGENTS.md                        ← rút gọn luật cho agent
├── CLAUDE.md                        ← file này
├── docs/                            ← 5 tài liệu chốt
├── specs/                           ← spec từng feature (Spec Kit)
├── src/main/java/com/cnhsk/         ← code backend
│   ├── auth/  learning/  community/  shared/
│   └── CnhskApplication.java
├── src/test/java/com/cnhsk/
│   └── architecture/ModuleBoundaryTest.java  ← canh ranh giới module
└── cnhsk-web/                       ← frontend React (CHUA TAO — ban nhap da xoa)
```

> **Lưu ý tên:** thư mục gốc tên là `learning-service` vì lý do lịch sử, nhưng nó chứa
> **toàn bộ ứng dụng**, không phải riêng module learning.

## Nhóm

| Tên | Vai trò | Mảng |
|---|---|---|
| Trí | Leader | Quản lý · auth/thanh toán · ~30% lo điều phối |
| Tường | Backend | API học tập, đề thi, mastery |
| Đạt | Backend | API từ điển, AI, admin, game |
| Xuân Huy | Frontend web | React — học tập, thi, tiến độ |
| Quốc Huy | Mobile | RN — khung, auth, học tập |
| Dũng | Mobile | RN — thi, tiến độ, WebView game |

**Cuốn chiếu:** backend xong API nào → web làm màn hình đó → mobile bám theo sau 1–2 tuần.

Sau cân tải, Quốc Huy và Dũng làm cả backend lẫn mobile — để khi mobile chờ API thì có
việc khác làm.

## Trạng thái hiện tại

**Đã xong:** scope 33 tính năng · kiến trúc v5 · database (31 bảng, 4 schema) · stack · ERD ·
WBS 66 đầu việc · Constitution v1.0.0 · khung Spec Kit

**Chưa làm:** spec feature nào · OpenAPI · migration Flyway · code nghiệp vụ

**Việc tiếp theo:** đóng Tuần 1 → spec `auth` → OpenAPI `auth` → migration `auth` → code

## Chưa chốt

- **Redis đặt ở đâu** — giảng viên nói giữa client và service, kiến trúc hiện đặt sau
  ứng dụng. Cần hỏi lại.
- **Thanh toán** — tiền thật hay giả lập
- **Mức coverage** — playbook khuyên 80%, nhưng nhóm đã vượt 15% kế hoạch

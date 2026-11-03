# Chốt — Spec 010

## 2026-11-02 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc: `AGENTS.md`, constitution v1.4.1, `constraints/` (business, global, safety), `CONTEXT.md`, spec 002/003 §0/004/005, `specs/001-auth-rbac/tasks.md`, feature-tree 2.1–3.3/6.3/6.4/6.6, UC-109/110/112/137, `screen-fields.md` SCR-059/064, `design.md`, `database.md`, migration V1–V4, `learning.api.PracticeApi`, `auth.api.AuthApi`, Cây Năng Lực v2.0 (qua báo cáo trích của plan `261017-1654`), 3 file `Caytrithuc/*.md`.

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Định nghĩa knowledge point/mastery lệch `business.md` §4/§9 | **Làm RFC theo v2.0**: knowledge point = nút KID, mastery = MP-1.0; giữ `BUS-04` cho lịch ôn mục từ, `BUS-05` cho chủ đề. Chờ cả nhóm đồng thuận |
| Q2 | Bảng lưu cây | **0 bảng mới**: mở rộng `knowledge_points` (miền, tên Trung, can-do, mảng tiên quyết/hỗ trợ), `question_knowledge_points` (vai trò), `questions`, `topics`, `lexemes` |
| Q3 | Ai sửa nội dung câu hỏi | **Theo constitution**: chỉ `TEACHER` sửa nội dung + nhãn; `CONTENT_EDITOR` xem + lưu trữ; `TEACHER` xem SCR-064 chỉ đọc |
| Q4 | Trạng thái câu nhập | **Nhập từ file → `APPROVED`; câu AI → `PENDING_REVIEW`** |
| Q5 | Dữ liệu cây phụ lục B (repo public) | **Không commit**, nạp qua SCR-068; test dùng cây tự soạn |
| Q6 | Nối nội dung cũ vào nút | **Cột `knowledge_point_id` trên `topics`/`lexemes` + mẫu `NODE_LINK`** (thay `kid_map` đã duyệt ở plan) |
| Q7 | Mã lỗi | **Làm luôn**, không thêm bảng: `error_kid`/`error_note` trên từng phương án nhiễu; có tài liệu mã lỗi chính thức thì chuyển đổi sau |
| Q8 | `questions.difficulty` | **Có** (1–3, mặc định 2) |
| Q9 | Số migration | **V5 cho 010, V6 cho 011** |
| Q10 | `questions.code` UNIQUE | **Có** |
| Q11 | API cây cho người học | **Thuộc 010** |
| Q12 | Khoá sửa khi có cuộc thi | **Bỏ đợt này** |
| Q13 | Bàn giao spec | **Chỉ file trên máy**, không push |
| Q14 | Thứ tự task | **FE trước như mọi spec** |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| X1 | Constitution/`business.md`/`design.md`: chỉ `TEACHER` sửa câu ↔ UC-109 BR-109-1, SCR-059 §5/§11: cả `CONTENT_EDITOR` | Theo constitution (Q3); cập nhật UC-109/SCR-059 trong RFC |
| X2 | `business.md` §4/§9, `CONTEXT.md` §3.1, feature-tree 3.1: mastery FSRS, knowledge point = 1 từ ↔ v2.0 | RFC Q1 |
| X3 | `database.md` §13 "không đụng" `knowledge_points`/`question_knowledge_points` | Mở rộng cột, không xoá; ghi vào RFC |
| X4 | D-01, spec 002 §0: câu hỏi chờ cây | Spec 010 gỡ |
| X5 | Plan đợt cuối (phase 2, red-team #2) dùng `kid_map` | Thay theo Q6 |
| X6 | Phụ lục B thuộc Học Bá, repo public | Q5 |
| X7 | feature-tree 6.3 nhắc `question_options`; `global.md` §7 cấm FK xuyên schema; ES-07 có `agent/*` | Theo migration V1, constitution AC-03, luật nhóm (nhánh không chứa "agent") |
| X8 | Plan phase 3 định sửa `PracticeService.finish` | Spec 011 dùng `@EventListener` theo 003 §0.9 (D-04), không sửa bộ máy |

### Còn mở

- OQ-1: tên tiếng Việt 6 miền theo v2.0 (leader đối chiếu PDF).
- OQ-2: danh mục mã lỗi chính thức của Học Bá.

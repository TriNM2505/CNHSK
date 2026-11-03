# RFC đợt cuối — thay đổi tài liệu gốc cho spec 010–015

| Trường | Giá trị |
|---|---|
| Ngày | 2026-11-03 |
| Người đề xuất | Leader |
| Trạng thái | **Đã đồng thuận** 2026-11-03 (leader xác nhận) — R1–R12 |
| Áp dụng cho | Spec 010, 011, 012, 013, 014, 015 (chỉ chuyển `APPROVED` sau khi RFC này được đồng thuận) |

## Vì sao cần RFC

Đợt cuối dùng **Cây Năng Lực Hán Ngữ v2.0** làm gốc cho phần học (cây KID, mastery MP-1.0). Một số điểm trong hiến pháp, `business.md`, use case và `screen-fields.md` viết trước khi có v2.0 nên mâu thuẫn. Spec 010–015 đã viết theo quyết định của leader. RFC này xin cả nhóm đồng ý để sửa tài liệu gốc cho khớp. Không đồng ý điểm nào thì spec tương ứng phải viết lại.

**Không có điểm nào đổi code đã merge (001–009).**

---

## 12 thay đổi đề xuất

### Phần học — cây năng lực (010, 011, 012)

| # | Thay đổi | Hiện đang ghi | Đề xuất | Vì sao | Spec |
|---|---|---|---|---|---|
| R1 | Knowledge point là gì | `business.md` §9: "knowledge point = 1 từ"; `knowledge_points` có `lexeme_id` | Knowledge point = **một nút KID** của v2.0 (vd `NP3-004`), 6 miền NA CH TV NP NL VB; từ/chữ/chủ đề **nối vào** nút bằng `knowledge_point_id` | Một từ không phải một năng lực; v2.0 đo theo nút | 010 |
| R2 | Bảng "không đụng" | `database.md` §13: không đụng `knowledge_points`, `question_knowledge_points`, `user_progress` | **Được thêm cột** (không xoá, không đổi cột cũ): V5 (010) mở rộng 2 bảng đầu, V6 (011) mở rộng `user_progress`. Không thêm bảng mới (AC-04 giữ 22 bảng) | Các bảng này chưa có dữ liệu; thêm cột an toàn | 010, 011 |
| R3 | Mastery tính thế nào | `business.md` §4, UC-042/044: mastery bằng FSRS, thang 0–1 | Mastery = **MP-1.0** (8 minh chứng gần nhất có trọng số, bán rã 30 ngày), thang **0–100**. **FSRS vẫn dùng** cho lịch ôn mục từ (`BUS-04` giữ nguyên) | v2.0; FSRS là lịch ôn, không phải đo năng lực | 011 |
| R4 | "Đã thuộc" | UC-051, SCR-019: mastery ≥ 0,9 | **Vững ≥ 80** (trạng thái `extend`) | Ngưỡng của MP-1.0 | 011 |
| R5 | Biên học tập | v2.0: tiên quyết phải ≥ 80 | Tiên quyết đạt **củng cố ≥ 60** là được gợi ý nút kế; chỉ gợi ý + tô màu, **không khoá** nội dung. Cổng 90% chủ đề (`BUS-05`) giữ nguyên | Demo 2–3 tuần cần mở nút nhanh hơn; cấu hình đổi được về 80 | 011 |
| R6 | Đồng hồ đề thi | Plan cũ: hết giờ tự nộp; SCR-021 BLOCKED | **Không đếm ngược**, hạn 24 giờ — đúng UC-033…041 "không mô phỏng thi thật" | Gỡ BLOCKED của SCR-021 | 012 |
| R7 | Bài xếp trình độ | `screen-fields` SCR-001/010: "không bịa màn/luồng placement" | Có bài xếp trình độ, là **chế độ của SCR-020/021/022**, không thêm màn (79 màn giữ nguyên) | Leader yêu cầu đo năng lực hiện tại của người học | 012 |
| R8 | Điểm yếu sau bài thi | SCR-022: xếp theo mastery | Giữ nguyên; phần xếp hạng làm sau khi 011 có mastery (012 T-16) | Phụ thuộc thứ tự làm, không đổi nghiệp vụ | 012 |

### Phần tiền và thư viện (013, 014)

| # | Thay đổi | Hiện đang ghi | Đề xuất | Vì sao | Spec |
|---|---|---|---|---|---|
| R9 | Ai sinh mã thẻ, xem sổ cái | Hiến pháp §Bảy actor, `business.md` §1, BR-099-2, BR-100-2, BR-101-2, SCR-073…075: chỉ `ACCOUNTANT`; "ADMIN không sinh mã, không đụng sổ cái" | **ADMIN làm toàn bộ nghiệp vụ tài chính.** Giữ mã role `ACCOUNTANT` trong DB/code (không migration) nhưng không dùng. Bù lại: **không ai nạp được mã do chính mình tạo**; audit mọi lô, gói, mọi lần đọc/xuất/đối chiếu sổ cái | CNHSK là sản phẩm phễu, không quản lý doanh thu; bớt một role cho nhóm nhỏ | 013 |
| R10 | JSONB ghi nhiều lần | AC-09: JSONB chỉ cho dữ liệu chỉ đọc | **Ngoại lệ** cho `users.free_usage` (bộ đếm hạn mức theo tháng — thiết kế có sẵn từ V1) | Không thêm bảng; mỗi lần ghi khoá dòng `users` | 013 |
| R11 | Thùng rác ghi chú | UC-133: khôi phục nhưng chưa có thời hạn | Khôi phục trong **30 ngày**; sau đó ẩn, **không** xoá khỏi DB (`BUS-10`) | Leader chọn | 014 |

### Trợ lý AI (015)

| # | Thay đổi | Hiện đang ghi | Đề xuất | Vì sao | Spec |
|---|---|---|---|---|---|
| R12 | Trợ lý trả lời gì, tốn lượt không | UC-085: hỏi từ vựng, ngữ pháp; dùng lượt; CONTEXT: "Lượt" gồm trợ lý | **Chỉ trả lời về cách dùng CNHSK**, từ chối câu kiến thức (gợi ý Tra từ điển/Dịch). **Không trừ lượt**, ngưỡng an toàn 200 câu/người/ngày, trần chi phí/tháng. **Không có trợ lý khi đang làm bài kiểm tra.** Không lưu lịch sử (UC-086 ra ngoài phạm vi) | Tránh trợ lý làm bài hộ; người học hỏi thoải mái về hệ thống; giữ "thi nghiêm túc" | 015 |

---

## Tài liệu sẽ sửa sau khi đồng thuận

| Tài liệu | Mục | Theo |
|---|---|---|
| `.specify/memory/constitution.md` | §Bảy actor (R9), AC-09 thêm ngoại lệ (R10) → nâng phiên bản | R9, R10 |
| `.specify/memory/constraints/business.md` | §1 quyền (R9), §4 mastery (R3), §9 thuật ngữ (R1) | R1, R3, R9 |
| `docs/reference/CONTEXT.md` | §3.1 knowledge point, "Lượt" (R12) | R1, R12 |
| `docs/reference/database.md` | §4.4–4.6, §4.9, §11, §13 | R1, R2 |
| `docs/reference/feature-tree.md` | 3.1, 5.5 | R3, R12 |
| `docs/reference/use-cases-03…07` | UC-042/044/051, UC-085/086, UC-099…102, UC-133 | R3, R4, R9, R11, R12 |
| `docs/reference/screen-fields.md` | SCR-001/010 (placement), SCR-019 (ngưỡng), SCR-021 (đồng hồ), SCR-073…075 (role) | R4, R6, R7, R9 |

Leader sửa tài liệu trong **một PR riêng** sau khi RFC được đồng thuận; spec 010–015 chuyển `APPROVED` cùng lúc.

---

## Đồng thuận

Mỗi người ghi **Đồng ý** hoặc **Không đồng ý + mục số + lý do**. Một mục bị phản đối thì chỉ mục đó và spec liên quan được bàn lại; các mục khác vẫn đi tiếp.

| Thành viên | R1–R8 (học) | R9–R11 (tiền, thư viện) | R12 (trợ lý) | Ghi chú |
|---|---|---|---|---|
| Leader | | | | |
| Thành viên A | | | | |
| Thành viên B | | | | |
| Thành viên C | | | | |
| Thành viên D | | | | |
| Thành viên E | | | | |

Hạn góp ý đề xuất: **48 giờ** kể từ khi gửi.

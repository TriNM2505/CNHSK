---
type: brainstorm
date: 2026-11-24
topic: Định hướng 4 kỹ năng theo cây tri thức v2.0 (HSK 1–3)
status: approved
---

# Brainstorm · Cây tri thức làm xương sống cho 4 kỹ năng (HSK 1–3)

## 1. Vấn đề
Leader muốn: Nghe · Nói · Đọc · Viết là 4 kỹ năng then chốt; tách Phát âm thành Nghe và Luyện nói; kết hợp Chủ đề, Ngữ pháp, Chữ Hán; chia theo HSK 1–3; **mọi dữ liệu học đi qua cây tri thức** (dò đúng/sai, đề xuất bài, AI sinh câu). Cần: đánh giá cây còn thiếu gì, sửa cây, dựng data từ nguồn uy tín, rồi sửa tính năng.

## 2. Nguồn đã đọc
- `C:\CLHSK\Caytrithuc\Cay-Nang-Luc-Han-Ngu_HSK1-9_Dac-ta-v2.0.pdf` (text: `convert/cay-nang-luc-v2.txt`) — **chuẩn đang dùng**. Chương 3, 5.9–5.11, phụ lục B, D, E.
- `01-architecture-overview`, `02-dev-integration-spec`, `03-ba-spec` (v1.0, 10/2025) — **nháp cũ kiểu IELTS (7 miền theo kỹ năng LIS/REA/WRI/SPE)**, đã bị v2.0 thay. Không theo.
- `convert/`: 174 nút, 191 cạnh, 1.389 mục từ HSK 1–3 (0 audio, 165 có ví dụ), ngân hàng 901 câu (đã nạp Neon 21/11).

## 3. v2.0 vận hành thế nào (điểm then chốt)
- 4 tầng: Miền (NA, CH, TV, NP, NL; VB từ cấp 4) → Cụm → **Nút KID (tính mastery)** → Mục từ (W/Z, lặp lại ngắt quãng).
- **Kỹ năng không phải miền, không phải loại nút; là thuộc tính của nút (nhiều kỹ năng) và của câu hỏi.**
- Mapping: câu hỏi ↔ 1–3 KID (chính 1.0, phụ 0.5); nhiệm vụ Nói/Viết khai báo **KID mục tiêu**; mã lỗi ↔ đúng 1 KID; học liệu ↔ KID.
- Nói chấm **R-NOI** (C1 hoàn thành, C2 phát âm, C3 từ vựng, C4 ngữ pháp, C5 trôi chảy; 0–3). Viết chấm **R-VIET** (C1–C4; 0–5). C1/C5 → nút NL; C2 → NA1-006/NA1-008 (cần audio gốc); C3 → TV; C4 → NP. Mỗi lỗi E-xx → minh chứng 0 điểm cho KID của mã. GV duyệt → minh chứng (trọng số bối cảnh: thi 1.0, BTVN 0.6, luyện 0.5).
- Mastery TV/CH = 50% thẻ ôn (hộp ≥ 3) + 50% câu ngữ cảnh.

## 4. Khoảng trống (hiện trạng so với v2.0)
| # | Thiếu | Mức |
|---|---|---|
| 1 | Nút chỉ có 1 `skill_type`, không SPEAKING; 22 nút NL để trống dù phụ lục B có cột "Kỹ năng" (convert cũ làm rơi) | Đỏ |
| 2 | Câu hỏi không có thuộc tính kỹ năng | Đỏ |
| 3 | Nói/Viết chấm 4×25, không đổ vào KID (gồm cả Luyện nói vừa làm) | Đỏ |
| 4 | Tầng/đơn vị phát âm không gắn được nút NA → luyện nghe không có minh chứng | Đỏ |
| 5 | Không có mã lỗi E-xx | Vàng |
| 6 | Nút thiếu cụm, pattern, examples_ok/err, teaching_note, source | Vàng |
| 7 | Mục từ gắn TV/CH chưa đủ | Vàng |

## 5. Quyết định đã chốt
| Vấn đề | Chốt |
|---|---|
| Trục kỹ năng | Bám v2.0: `skills` nhiều giá trị trên nút + `skill` trên câu hỏi |
| Skills cho 152 nút KT | Suy theo miền (NA→Nghe+Nói; CH, TV, NP→Đọc+Viết; thêm Nghe/Nói khi can-do có) → **leader/GV duyệt file trước khi nạp**. NL lấy đúng phụ lục B |
| Rubric | **Đổi cả Nói (R-NOI) và Viết (R-VIET)**; hiển thị quy ra /100; đổi hợp đồng API chấm bài |
| Mã lỗi | Làm luôn, danh mục HSK 1–3 từ phụ lục D |
| Menu | Sidebar trái: Nghe · Luyện nói · Chủ đề · Ngữ pháp · Chữ Hán · Viết; mỗi trang chọn HSK 1/2/3 |
| Audio | Giọng đọc trình duyệt |
| Data không có nguồn chính thức | AI soạn nháp, ghi nguồn từng dòng, **nạp ở trạng thái chờ GV duyệt** (sửa importer, không tự APPROVED) |
| Thời hạn | Làm đủ G1→G3, không cắt; gate đầy đủ cuối mỗi giai đoạn |
| Tính năng vừa làm | Giữ: hạn mức lượt, học thử, luồng gửi bài GV. Đổi: Luyện nói lấy bài theo KID, chấm R-NOI; Phát âm tách 2 mục menu |

## 6. Giải pháp (3 giai đoạn)
**G1 · Sửa cây (schema + importer)**
- Migration: `knowledge_points.skills text[]` (NGHE/NOI/DOC/VIET), `cluster`, `pattern`, `examples jsonb`, `teaching_note`, `source`; `questions.skill`; bảng KID mục tiêu cho nhiệm vụ Nói/Viết; bảng mã lỗi (code → kid). Chạy Flyway trên Neon khi leader bật BE → báo trước.
- Importer: cột mới cho KNOWLEDGE_NODE, QUESTION; CONTENT_KID thêm PRON_STAGE/PRON_UNIT → NA; mẫu mới TASK (nhiệm vụ + KID mục tiêu), ERROR_CODE; tuỳ chọn nạp PENDING_REVIEW.

**G2 · Data pack (CSV cho SCR-068, không commit dữ liệu, để ở `C:\CLHSK\Caytrithuc\convert\v3\`)**
| File | Nội dung | Nguồn |
|---|---|---|
| skills | 174 nút: NL theo phụ lục B; KT suy theo miền | v2.0 phụ lục B + quy tắc |
| node-detail | cụm, pattern, examples_ok/err, teaching_note, source cho NP/NA | v2.0 phụ lục A/B, đại cương HSK 2025; thiếu → AI nháp |
| lexeme-kid | gắn đủ ~1.400 mục từ → TV (theo chủ đề) / CH (tập chữ theo cấp) | đại cương HSK 2025 + phụ lục B.3 |
| pron-kid | 8 tầng / đơn vị phát âm → NA1-001…NA2-003 | spec 005 + phụ lục B.1 |
| grammar-examples | ≥ 3 câu ví dụ / điểm NP | Tatoeba (CC BY, ghi nguồn) → thiếu thì AI nháp chờ duyệt |
| tasks | nhiệm vụ Nói/Viết cho 22 nút NL + KID mục tiêu | phụ lục B.5 (tiên quyết chính) + E.3 |
| questions-skill | câu Nghe/Đọc mới + gắn `skill` cho 901 câu cũ | suy từ dạng câu; câu mới AI nháp chờ duyệt |
| error-codes | mã lỗi HSK 1–3 | phụ lục D |
Kiểm tra tự động: KID tồn tại, DAG, không thừa bắc cầu, ≤ 4 tiên quyết KT, NL ≥ 2 quan hệ (phụ lục F.4), dry-run SCR-068 0 lỗi.

**G3 · Tính năng đi qua cây**
- Menu 6 mục + bộ chọn HSK; Nghe (8 tầng → minh chứng NA); Luyện nói (bài theo KID NA/NL, chấm R-NOI → minh chứng theo E.3); Viết (nhiệm vụ NL, R-VIET).
- Màn chấm GV: rubric động theo loại bài + chọn mã lỗi.
- Dashboard: % theo kỹ năng (gộp từ `skills` của nút và `skill` của câu) + "x/y nút đã học ở mức tốt" (đúng 5.11, không 1 con số trung bình).
- AI sinh câu: prompt dùng `skills`, `pattern`, `examples`.

## 7. Rủi ro
| Rủi ro | Giảm |
|---|---|
| Đổi API chấm bài làm vỡ contract test, bài cũ 4×25 | Giữ đọc được bài cũ (rubric = LEGACY); contract phụ lục mới (không sửa openapi đã khoá) |
| Dữ liệu AI soạn sai | Trạng thái chờ duyệt + review sheet ghi nguồn; mẫu 10% cho phần có nguồn |
| Migration chạy thẳng trên Neon | Báo leader trước mỗi lần bật BE; migration chỉ thêm cột/bảng |
| Âm Hán Việt Unihan không tin cậy | Giữ quyết định cũ: không dùng Unihan cho Hán Việt |
| Phạm vi lớn (~4–5 ngày) | Làm theo giai đoạn, gate cuối mỗi giai đoạn |

## 8. Tiêu chí thành công
- 174 nút có `skills`; 100% mục từ HSK 1–3 có KID; 8 tầng nghe có KID NA; 22 nút NL có nhiệm vụ + KID mục tiêu.
- Làm 1 bài nghe / nói / viết → minh chứng xuất hiện ở đúng KID (kiểm bằng `mastery` API).
- Dashboard hiện % 4 kỹ năng khác 0 sau khi học.
- AI sinh câu chạy được cho nút yếu có `can_do` + ví dụ.
- Gate: toàn bộ FE test + `mvnw verify` xanh.

## 9. Câu hỏi còn mở
- Thời điểm bảo vệ chính xác (ảnh hưởng thứ tự ưu tiên trong G3).
- Ai trong nhóm (GV) duyệt file skills và dữ liệu AI nháp.

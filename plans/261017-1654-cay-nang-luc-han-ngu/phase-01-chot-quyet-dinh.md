---
phase: 1
title: "Chot quyet dinh"
status: pending
priority: P1
effort: "0.5 ngay"
dependencies: []
---

# Phase 1: Chốt quyết định

## Overview

Chốt 6 quyết định trước khi đụng vào lược đồ. Sai ở đây thì phase 2–5 làm lại.
Đầu ra là một bảng quyết định có người chốt, ngày chốt, lý do.

## Requirements

- Mỗi quyết định có: lựa chọn, lý do, ai chốt (nhóm / Học Bá / GVHD).
- Quyết định nào cần Học Bá đồng ý thì ghi rõ, không tự chốt thay.

## Quyết định

| # | Câu hỏi | Lựa chọn | Khuyến nghị | Cần ai |
|---|---|---|---|---|
| Q1 | Hướng sửa | A áp nguyên phụ lục F · **B áp lõi** · C vá tối thiểu | B | Nhóm + GVHD |
| Q2 | Ôn mục từ | **Leitner 5 hộp** · FSRS | Leitner (khớp công thức gộp nút TV mục 5.9) | Học Bá |
| Q3 | Phạm vi cấp | **Mô hình 1–9, nạp sơ cấp** · giữ 1–6 · nạp đủ 497 | Mô hình 1–9, nạp 174 nút sơ cấp | Nhóm + Học Bá |
| Q4 | Cổng 90% chủ đề | **Biên học tập** · giữ cổng, đổi sang mastery ≥ 80 | Biên học tập | Nhóm |
| Q5 | Nguồn cây | **Phụ lục B làm bản nháp** · chờ bảng tính chính thức | Nạp phụ lục B, thay khi có bản chính thức | Học Bá |
| Q6 | Trọng số bối cảnh cho hoạt động riêng CNHSK | xem bảng dưới | — | Nhóm |

### Q6 · Đề xuất trọng số bối cảnh (CNHSK không có thi giữa/cuối khoá)

| Hoạt động CNHSK | Bối cảnh MP-1.0 gần nhất | w đề xuất |
|---|---|---|
| Đề HSK đầy đủ (server chấm) | Thi | 1,0 |
| Bài test cuối chủ đề, luyện theo dạng câu, bài chẩn đoán | Quiz / đề luyện / chẩn đoán | 0,8 |
| Bài viết giáo viên đã chấm | Bài về nhà có người duyệt | 0,6 |
| Luyện tự chọn, bài AI sinh đã duyệt, game | Luyện tự chọn | 0,5 |
| Flashcard, thẻ ôn từ | Không vào MP — chỉ đổi hộp Leitner | — |
| Viết chữ (chấm ở trình duyệt) | Luyện tự chọn | 0,5 (giữ LI-06: trọng số thấp) |

## Implementation Steps

1. Đọc [`giai-thich-cay-nang-luc.md`](../../docs/reference/cay-nang-luc/giai-thich-cay-nang-luc.md) (cả nhóm).
2. Họp nhóm 30 phút: chốt Q1, Q3, Q4, Q6.
3. Gửi Học Bá Q2, Q5 kèm khuyến nghị; hỏi luôn bảng tính cây chính thức (phụ lục H) và mã W/Z cho từ, chữ.
4. Ghi kết quả vào `reports/quyet-dinh-cay-nang-luc.md` trong thư mục plan này.
5. Nếu Q1 ≠ B: viết lại phase 2–5 trước khi làm.
6. Gắn `blockedBy` cho 2 plan 79 màn (nhóm màn học tập) nếu Q4 = biên học tập.

## Success Criteria

- [ ] 6 quyết định có lựa chọn + người chốt + ngày
- [ ] Đã gửi Học Bá câu hỏi Q2, Q5
- [ ] Quan hệ với 2 plan 79 màn đã cập nhật

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Học Bá trả lời chậm Q2/Q5 | Làm tiếp với khuyến nghị; Leitner và phụ lục B đều là nội dung của chính đặc tả Học Bá nên ít rủi ro bị bác |
| Nhóm muốn giữ FSRS vì đã viết vào report | FSRS vẫn dùng được ở V2; đừng để thông điệp report quyết định thiết kế |

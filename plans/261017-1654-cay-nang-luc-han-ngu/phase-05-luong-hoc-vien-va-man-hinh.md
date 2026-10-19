---
phase: 5
title: "Luong hoc vien va man hinh"
status: pending
priority: P2
effort: "4 ngay"
dependencies: [4]
---

# Phase 5: Luồng học viên và màn hình

## Overview

Đổi những gì học viên nhìn thấy cho khớp cây: hồ sơ năng lực theo miền, biên học tập thay cổng 90%,
điểm yếu theo nút có gốc tiên quyết, chẩn đoán đầu vào, ôn tập theo gợi ý.

## Requirements

- Hiển thị theo miền: "Ngữ pháp: 14/22 nút đã học ở mức tốt" — không có một con số trung bình duy nhất (mục 5.11).
- Nút "chưa đủ minh chứng" màu xám, câu "cần làm thêm bài để hệ thống biết chính xác" — không hiện như điểm thấp.
- Không dùng chữ "đạt" cho mastery.
- Mỗi gợi ý có lý do bằng lời + nút "xem minh chứng".
- Kết quả đề HSK: điểm yếu theo **nút**, chỉ ra tiên quyết gốc nếu tiên quyết cũng yếu.

## Architecture — màn bị ảnh hưởng

| Màn / luồng | Đổi gì | Use case |
|---|---|---|
| Cây chủ đề / danh sách chủ đề | Chủ đề = nút TV; mở theo **biên học tập** (Q4) thay cổng 90% | UC-045, 046 · BF-02 |
| Học chủ đề | Thẻ từ → hộp Leitner; câu ngữ cảnh → minh chứng nút TV | UC-025→029 |
| Kết quả đề thi | "Top 3–5 điểm yếu" theo nút + gốc tiên quyết | UC-040, 041 · BF-03 |
| Ôn đến hạn | Danh sách gợi ý của `Recommender`, không còn danh sách FSRS | UC-042→044 · BF-04 |
| Dashboard / Tiến độ | Hồ sơ năng lực theo 5 miền sơ cấp | UC-051 |
| **Mới:** Bài chẩn đoán đầu vào | Sau đăng ký, tuỳ chọn, ≤ 35 câu | UC mới · BF-01 |

## Related Code Files

- Modify: `docs/reference/use-cases-04-lo-trinh.md`, `use-cases-02-hoc-luyen-tap.md`, `use-cases-03-luyen-thi.md`
- Modify: `docs/reference/screen-fields.md` (màn Topic List, Topic Study, Due Review, Exam Result, Learning Dashboard, Learning Progress)
- Modify: màn HTML/React trong plan 79 màn (nhóm học tập)

## Implementation Steps

1. Viết lại BF-02 (biên học tập), BF-03 (điểm yếu theo nút), BF-04 (ôn theo gợi ý), thêm bước chẩn đoán vào BF-01.
2. Cập nhật use case lộ trình: bỏ "90%", thay bằng định nghĩa biên học tập; thêm UC chẩn đoán.
3. Cập nhật `screen-fields.md` cho 6 màn trên.
4. Báo plan 79 màn: danh sách màn đổi + nội dung đổi; sửa HTML/React theo.

## Success Criteria

- [ ] Không màn nào còn "mở chủ đề khi đạt 90%" (nếu Q4 = biên học tập)
- [ ] Màn kết quả thi hiển thị nút + lý do gốc tiên quyết
- [ ] Học viên mới thấy trạng thái xám "chưa đủ minh chứng"
- [ ] Use case chẩn đoán có luồng chính, luồng phụ, điều kiện dừng

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Đụng plan 79 màn đang làm | Gắn quan hệ ở phase 1; chỉ đổi nhóm màn học tập |
| Biên học tập khó hiểu với người học | Hiển thị "sẵn sàng học" + tên tiên quyết còn thiếu |

# Quyết định — cây năng lực Hán ngữ

## QĐ-01 · 18/10/2026 · Chờ data HSK chuẩn của Học Bá cho cả cấu trúc và dữ liệu cây

| | |
|---|---|
| Người chốt | Nguyễn Minh Trí (leader) |
| Lựa chọn | **Đợi data rồi làm cả hai** — cấu trúc bảng cây (V3) và dữ liệu cây chỉ làm khi Học Bá giao data chuẩn |
| Phương án không chọn | Dựng bảng ngay với phụ lục B làm dữ liệu thử · Dựng bảng trống |
| Lý do | Muốn cấu trúc khớp đúng data thật, tránh dựng hai lần |

### Làm ngay (không phụ thuộc cây)

- [ ] Sửa `V1__core_schema.sql` cho khớp `database.md`: bỏ `roles`, thêm `plans`, `pron_stages`, `videos`, `dictation_attempts`, `shadowing_attempts`
- [ ] Sửa vị trí migration: chuyển vào `src/main/resources/db/migration` hoặc đặt `spring.flyway.locations: filesystem:db/migration`
- [ ] Chốt Java 17 hay 21 (pom.xml và Report 2 đang khác nhau)
- [ ] Code các tính năng ngoài cây: auth, tiền và mã thẻ, cộng đồng, thư viện cá nhân, trợ lý AI, audit

### Cấm trong lúc chờ

- Không nhập câu hỏi hoặc gắn nhãn vào `knowledge_points` / `question_knowledge_points` cũ — sẽ phải làm lại
- Không code mastery bằng FSRS trên `user_progress`
- Không code cổng 90% chủ đề

### Bị chặn đến khi có data

Phase 2 (lược đồ + nạp nút) · Phase 3 (ma trận Q, mã lỗi, mục từ) · Phase 4 (mastery engine) · Phase 5 (màn học tập) ·
phần phân tích lỗi sau bài thi, gợi ý ôn, tiến độ theo nút.

### Hệ quả cần theo dõi

| Hệ quả | Theo dõi |
|---|---|
| Phase 2–5 khoảng 15 ngày công, chỉ bắt đầu khi có data | Nếu đến **hết tuần 7** (mốc M1) chưa có data → xem lại QĐ-01, cân nhắc dựng bảng với phụ lục B |
| Màn học tập trong plan 79 màn chưa chốt được | Làm trước nhóm màn ngoài cây |

### Cần hỏi Học Bá

1. Ngày dự kiến giao bảng tính cây (nút, cạnh, can-do) và danh sách từ/chữ có mã W/Z
2. Định dạng file: theo mẫu phụ lục H hay định dạng khác
3. Leitner hay FSRS cho tầng mục từ

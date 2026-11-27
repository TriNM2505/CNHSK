-- Gói PREMIUM để phát hành thẻ: dùng mọi dịch vụ không giới hạn trong thời hạn gói, tính từ lúc người học nhập mã.
-- Chỉ thêm khi chưa có; gói admin đã tạo ở màn Quản lý gói giữ nguyên tên, giá và trạng thái.
INSERT INTO learning.plans (code, name, description, duration_days, price, is_active)
VALUES ('PREMIUM', 'Premium', 'Dùng mọi dịch vụ không giới hạn trong thời hạn gói', 30, 0, true)
ON CONFLICT (code) DO NOTHING;
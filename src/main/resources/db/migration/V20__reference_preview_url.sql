-- Link đọc trực tuyến cho tài liệu tham khảo: bản xem thử trên Google Books hoặc tài liệu miễn phí chính thức.
-- Chỉ lưu đường link, không lưu file sách.
ALTER TABLE learning.reference_items
  ADD COLUMN preview_url TEXT CHECK (preview_url IS NULL OR preview_url LIKE 'https://%');
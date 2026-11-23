-- Chuong thong bao khong co bang rieng: tin duoc doc tu du lieu san co (binh luan, luot thich, theo doi,
-- trang thai bai viet...). Moi nguoi chi can nho lan cuoi mo chuong de biet tin nao chua doc.
ALTER TABLE auth.users ADD COLUMN notifications_seen_at TIMESTAMPTZ;

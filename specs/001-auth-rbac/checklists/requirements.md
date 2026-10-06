# Checklist chất lượng yêu cầu — `001-auth-rbac`

**Ngày:** 2026-10-05 · **Spec:** `spec.md` v1.0 · **75 FR**

---

## 1 · Tính đầy đủ

- [x] Mọi UC trong nhóm 0 (UC-001 → UC-014) đều có FR phủ
- [x] Cả ba client có đường xác thực riêng (web cookie · mobile header · game cookie chung)
- [x] Mobile chơi game trong WebView có FR riêng (FR-030)
- [x] Mọi trạng thái tài khoản có hành vi xác định: chưa xác thực · khoá tạm · tạm ngưng · ban
- [x] Bốn role đều có FR về quyền
- [x] Ba gói dịch vụ đều có FR về hạn mức
- [ ] **Lịch sử đăng nhập chưa có bảng** — xem Open Question 6

## 2 · Tính kiểm thử được

- [x] Mỗi FR viết dạng EARS (WHEN / WHILE / WHERE + SHALL)
- [x] Mỗi FR map được ít nhất một test case
- [x] Mốc NFR có **số**, không có từ mơ hồ kiểu "nhanh"
- [x] Success criteria SC-001…SC-005 đo được bằng thao tác cụ thể
- [x] 8 edge case có hành vi mong đợi rõ ràng

## 3 · Tính nhất quán

- [x] Không FR nào mâu thuẫn FR khác
- [ ] Data Model khớp SQL — **chờ `database.md`**. Bảng `plans`, 4 cột gói trên
      `users`, 4 cột phễu trên `credit_cards` hiện **chưa có trong SQL**
- [x] Error Matrix khớp mã lỗi dùng trong FR
- [x] AC Mapping trỏ đúng mã trong Hiến pháp v1.1.0
- [x] "10 lượt mỗi tính năng mỗi tháng" khớp feature tree 6.1 (FR-070, FR-071)

> Mâu thuẫn đã phát hiện và ghi nhận: bản SQL thử nghiệm ban đầu đặt gói `FREE` = 0 lượt,
> trong khi feature tree 6.1 nói *"mỗi tính năng tốn phí dùng 10 lượt free"*.
> `database.md` phải lấy theo **feature tree**: 10 lượt, reset mỗi tháng.

## 4 · Tính rõ ràng

- [x] Không dùng từ "nên", "có thể", "tuỳ" trong FR
- [x] Mọi ngưỡng có số: 5 lần sai · 15 phút khoá · 24 giờ token · 1 giờ reset · 3 lần/giờ
- [x] Mọi mã lỗi có HTTP status
- [x] Ghi rõ chỗ **cố tình** trả cùng một thông báo (FR-018, FR-038) và vì sao

## 5 · Rủi ro đã nêu tường minh

- [x] FR-015: để trống `Domain` thì game không nhận cookie → UC-005 hỏng
- [x] FR-023: thiếu một nhánh đọc token thì một loại client không đăng nhập được
- [x] FR-027: cookie `HttpOnly` nên JS trang game không đọc được token
- [x] FR-048: đếm sai mật khẩu theo tài khoản, không theo IP
- [x] FR-054: trả 403 cho id không tồn tại, không trả 404
- [x] NFR-P01: bcrypt cố tình chậm, không hạ cost để đạt mốc

## 6 · Mô hình phễu (bổ sung 2026-10-05)

- [x] Ba luồng phát mã có FR riêng: `ECOSYSTEM_GIFT` · `PARTNER_BATCH` · `DIRECT`
- [ ] DB ràng buộc: `PARTNER_BATCH` bắt buộc `partner_code`; `ECOSYSTEM_GIFT` bắt buộc
      `issued_to` — **đã thiết kế và thử nghiệm, chờ v7 đưa vào SQL**
- [x] Ghi rõ **KHÔNG** tích hợp cổng thanh toán (FR-079) — tránh code thừa
- [x] Có truy vấn đo tỉ lệ đổi mã theo `campaign` (FR-080)
- [x] Rủi ro lô mã rò rỉ ghi tường minh ở §11.1, kèm 3 ngưỡng cảnh báo
- [x] Đánh đổi "mã thô hiện một lần" ghi ở §11.2

> Thiết kế đã thử nghiệm trên pg-mem, 6/6 ràng buộc `channel` hoạt động đúng: chặn khi
> thiếu `partner_code`, chặn khi thiếu `issued_to`, chặn `channel` không hợp lệ, không chặn
> sai `DIRECT`. **Kết quả này là cơ sở cho `database.md`** — SQL thật chưa viết.

## 7 · Điểm còn mở

| # | Nội dung | Chặn |
|---|---|---|
| 1 | HS256 vs RS256 | FR-016, FR-061 |
| 2 | Thời gian sống access/refresh token | FR-031 |
| 3 | Bảng cho lịch sử đăng nhập | FR-056 |

### Đã chốt 2026-10-05

| Câu hỏi | Đáp án |
|---|---|
| `TODO(PAYMENT_SCOPE)` | Tiền thật, thu ngoài hệ thống. Giữ `HR-05`/`BUS-12`/`BUS-13`/`BUS-14`/`BUS-15`, bỏ cổng thanh toán |
| 10 lượt free | Mỗi tháng, reset 00:00 ngày 1 giờ Việt Nam |
| `expires_at` cho lô bên thứ ba | Không bắt buộc — rủi ro đã chấp nhận, giám sát thay vì chặn cứng |

**Kết luận:** spec đủ để sang `/speckit.plan`. Ba điểm mở còn lại đều là chi tiết kỹ thuật
có đề xuất sẵn, không điểm nào chặn việc lập kế hoạch.

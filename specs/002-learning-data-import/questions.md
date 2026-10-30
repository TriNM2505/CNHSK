# Câu hỏi triển khai — Spec 002

## T-03 — Chuẩn hóa văn bản và pinyin

Trạng thái: đã duyệt cùng Shadow Plan T-03 trong phiên làm việc 2026-10-29
(người dùng xác nhận `duyệt`). Các quyết định dưới đây là căn cứ triển khai và test.

Nguồn: spec 002 FR-030…FR-033, §4.0–4.1, AT-34…AT-36;
spec 003 §0.7, §0.10 G-3 và AT-18.

### Q-T03-01 — Định dạng đầu ra chuẩn của `toNumbered`

FR-033 chốt `nǐ hǎo` → `ni3 hao3`, thanh nhẹ → `5`, `ü` → `v`.
AT-36 yêu cầu có `nǚ'ér` và `Zhōngguó` nhưng chưa cho chuỗi đầu ra cụ thể.
Spec 003 dùng kết quả sau khi bỏ khoảng trắng để lập khóa đồng âm; không nên tự
quyết việc giữ dấu nháy/gạch nối hoặc chữ hoa trong khóa này.

Quyết định đã duyệt: đầu ra chữ thường, mỗi âm tiết có số thanh, phân cách
bằng một khoảng trắng; dấu nháy/gạch nối đầu vào được quy về ranh giới âm tiết.

| Đầu vào | Đầu ra đã duyệt | Căn cứ |
|---|---|---|
| `nǐ hǎo` | `ni3 hao3` | Đã chốt ở FR-033 |
| `lǜ` | `lv4` | Quy tắc dấu thanh và `ü` → `v` |
| `nǚ'ér` | `nv3 er2` | Đã duyệt định dạng dấu nháy |
| `Zhōngguó` | `zhong1 guo2` | Đã duyệt tách âm tiết và chữ hoa |
| `wǒmen` | `wo3 men5` | Tương thích `wo3men5` ở spec 003 §0.7 |
| `de` | `de5` | Thanh nhẹ theo FR-033 |
| `yī` | `yi1` | Dấu thanh theo FR-033 |

Bảng và quy tắc trên được dùng để khóa expected của các test AT-36.

### Q-T03-02 — Hành vi của helper khi đầu vào không chuyển được

§4.1 quy định tập ký tự pinyin, nhưng FR-033 chưa mô tả chuỗi đã dùng số thanh,
chuỗi trộn dấu với số hoặc chuỗi Latin không thể tách thành âm tiết.

Quyết định đã duyệt:

- `TextNormalizer` và `PinyinUtils.normalize`: giữ `null`, chuỗi chỉ có khoảng
  trắng trở thành chuỗi rỗng; kiểm tra bắt buộc thuộc validator ở task sau.
- Hàm kiểm ký tự pinyin: `false` với `null`/rỗng, chữ số, `u:`, chữ Hán, ký tự lỗi;
  kiểm đúng tập ký tự §4.1 sau NFC, không tự thêm luật ngữ âm vào mẫu nhập.
- `toNumbered`: giữ `null`, trả chuỗi rỗng cho đầu vào rỗng; đầu vào có ký tự cấm
  hoặc không tách được âm tiết thì ném `IllegalArgumentException`, không đoán
  âm tiết hoặc thanh điệu. Caller dùng cho chấm bài phải xử lý trường hợp này.
- `CjkUtils.isBroken` áp dụng theo ngữ cảnh cột: U+FFFD luôn là ký tự lỗi; dấu `?`
  chỉ bị xem là hỏng trong cột yêu cầu CJK, đúng §4.0.

Không còn câu hỏi mở của T-03 trong tài liệu này. Không sửa spec/OpenAPI;
chỉ ghi lại phần làm rõ hành vi đã được xác nhận trong Shadow Plan.

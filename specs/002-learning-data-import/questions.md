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

## T-05 — Cấu trúc CSV

Trạng thái: leader đã chốt trong phiên làm việc 2026-10-30. Các quyết định
dưới đây là căn cứ triển khai và test T-05.
Nguồn: FR-005, FR-006, FR-007 và Error Matrix §5.

### Q-T05-01 — Header trùng tên sau khi bỏ khoảng trắng

FR-006 chốt lỗi thiếu cột bắt buộc hoặc cột lạ, nhưng chưa nêu cách xử lý
header trùng tên (ví dụ `text, text `). Cho phép trùng có thể làm mất giá trị
khi ánh xạ mỗi dòng vào tên cột.

Quyết định đã duyệt: chặn cả file bằng `422 TEMPLATE_MISMATCH`; `details`
liệt kê từng cột trùng theo cùng dạng `{field, message}` của lỗi thiếu/thừa
cột trong FR-006. Không để thư viện tự chọn một giá trị.

### Q-T05-02 — Số ô của dòng khác số cột trong header

FR-005 yêu cầu RFC 4180; spec chưa chốt việc dòng thiếu/thừa ô là lỗi cấp file
hay lỗi riêng một dòng để tiếp tục nhập các dòng còn lại.

Quyết định đã duyệt:

- Dòng thiếu/thừa ô là lỗi riêng của dòng đó: `code = INVALID_VALUE`,
  `column = null`, `line` là dòng vật lý bắt đầu bản ghi.
- Thông báo: `Dòng có {n} ô, mẫu cần {m} ô — nếu nội dung có dấu phẩy, đặt ô đó trong ngoặc kép`.
  `n` là số ô đọc được, `m` là số cột trong header đã kiểm theo mẫu.
- Dòng lỗi không vào `validRows`; các dòng khác xử lý bình thường theo
  SC-003 và NFR-R01. Không thêm mã lỗi cấp dòng vào enum đã khóa của OpenAPI.
- Chỉ chặn cả file bằng `422 UNSUPPORTED_FILE_FORMAT` khi CSV hỏng cấu trúc
  và không tách được dòng, ví dụ mở ngoặc kép nhưng không đóng (FR-005).
- Ô rỗng có đủ dấu phân cách hợp lệ về cấu trúc; kiểm giá trị bắt buộc ở
  T-06/T-07. Dòng toàn ô trống vẫn được bỏ qua theo FR-007.
- Test bắt buộc: thừa ô, thiếu ô, ngoặc kép không đóng.

Không còn câu hỏi mở của T-05. Không sửa spec/OpenAPI; ghi lại quyết định
leader đã xác nhận, như các quyết định T-03.

### Ghi nhận tích hợp cho T-08 — `details` của `TEMPLATE_MISMATCH`

`GlobalExceptionHandler.handleApiException` hiện chỉ giữ `details` khi mã là
`VALIDATION_FAILED`. FR-006 yêu cầu `TEMPLATE_MISMATCH` cũng trả danh sách này.
T-05 giữ đầy đủ `details` trong `ImportFileException`; cần đưa việc bổ sung
handler và test HTTP vào Shadow Plan T-08 trước khi triển khai endpoint.
Leader đã xác nhận ghi nhận này và chốt xử lý ở T-08 trong phiên 2026-10-30.

## T-06 — Kiểm dòng, trùng khóa và danh sách nhãn

Trạng thái: leader đã chốt trong phiên làm việc 2026-10-30. Các quyết định
dưới đây là căn cứ triển khai và test. Nguồn: FR-009, FR-010, §4.1, AT-13.

### Q-T06-01 — Dòng trước có khóa đúng nhưng lỗi ở cột khác

FR-010 chốt lần xuất hiện đầu hợp lệ, các lần sau lỗi `DUPLICATE_IN_FILE`.
Chưa nêu rõ trường hợp dòng đầu có đủ khóa tự nhiên đúng nhưng lỗi ở cột
khác (ví dụ thiếu `meaning_vi`), còn dòng sau cùng khóa đã sửa đủ dữ liệu.

Quyết định đã duyệt: khóa tự nhiên chỉ được giữ bởi dòng hợp lệ đầu tiên,
sau khi kiểm cả field lẫn tham chiếu. Dòng lỗi ở cột khác chỉ báo lỗi của
chính nó, không giữ khóa và không nhận thêm lỗi trùng. Dòng hợp lệ đến sau
dòng hợp lệ đầu tiên nhận `DUPLICATE_IN_FILE`, `firstLine` là dòng vật lý
của dòng hợp lệ đầu tiên.

Test bắt buộc: dòng 3 lỗi `hsk_level`, dòng 7 cùng khóa hợp lệ thì dòng 7
hợp lệ; nếu cả dòng 3 và 7 đều hợp lệ thì dòng 7 trùng với `firstLine = 3`.

### Q-T06-02 — Phần tử rỗng trong `tags`

§4.1 quy định tối đa 10 nhãn, mỗi nhãn tối đa 30 ký tự, ngăn bằng `|`, nhưng
chưa chốt `a||b`, `a| |b`, `|a` hoặc `a|`.

Quyết định đã duyệt: tách theo `|`, trim từng nhãn và bỏ phần tử rỗng.
`a||b`, `a| |b` thành `["a", "b"]`; `|a`, `a|` thành `["a"]`.
Sau khi bỏ rỗng, tối đa 10 nhãn, mỗi nhãn tối đa 30 ký tự; vượt một trong
hai giới hạn thì cột `tags` lỗi `INVALID_VALUE`. Ô trống hoặc chỉ có `|`
là không có nhãn và hợp lệ. Trong giá trị dòng đã chuẩn hóa, nhãn được nối
lại bằng `|` để bước ghi có thể tạo mảng `extra.tags`; không có nhãn giữ
chuỗi rỗng theo quy tắc ô tùy chọn trống của FR-020.

Test phải bao phủ đầy đủ các trường hợp rỗng trên và giới hạn sau khi bỏ
rỗng. Không còn câu hỏi mở của T-06; không sửa spec/OpenAPI.

## T-07 — Kiểm dòng ngữ pháp và phát âm

Trạng thái: leader đã duyệt Shadow Plan T-07 và quy tắc dưới đây trong
phiên làm việc 2026-10-30. Nguồn: §4.6, FR-009, FR-020, Q-T06-02.

### Q-T07-01 — Phần tử rỗng trong `target_sounds`

§4.6 chốt tối đa 30 mục ngăn bằng `|`, mỗi mục tối đa 20 ký tự, nhưng chưa
nêu cách xử lý mục rỗng hoặc khoảng trắng quanh từng mục.

Quyết định đã duyệt: áp dụng cách làm như `tags` ở Q-T06-02: tách theo `|`,
trim từng mục, bỏ mục rỗng; kiểm giới hạn 30 mục và 20 ký tự sau bước này.
Cả ô trống hoặc chỉ có `|` hợp lệ và giữ trống để FR-020 giữ giá trị cũ
khi cập nhật. Vượt giới hạn trả `INVALID_VALUE`, không thêm mã lỗi mới.

Không còn câu hỏi mở của T-07; không sửa spec/OpenAPI.

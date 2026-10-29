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

## T-08 — Upload và contract metadata nullable

Trạng thái: leader đã duyệt phạm vi T-08 và thay đổi contract trong phiên
2026-10-30. Quyết định gồm BE, OpenAPI/spec và FE trong cùng PR.

### Q-T08-01 — Multipart bị chặn trước khi đọc được metadata

FR-003 yêu cầu giới hạn multipart 2 MB và trả `413 FILE_TOO_LARGE`.
FR-008 yêu cầu ghi lần nhập `FAILED` và trả `runId` cho lỗi cấp file.
Nhưng nếu multipart parser chặn request trước khi đọc được các part,
không thể bảo đảm lấy được `dataType`, `fileName` hoặc toàn bộ bytes để hash.
`ImportRun` trong OpenAPI hiện yêu cầu `dataType` và `fileName` không null.
Không được đoán một loại dữ liệu hoặc tên file thay thế để lấp metadata.

Quyết định đã duyệt: giữ giới hạn 2 MB / request 3 MB, vẫn ghi `FAILED` và trả `runId`;
metadata chưa đọc được lưu null, `file_hash` null (cột đã cho phép null).
Chỉ trường hợp `FAILED` do parser chặn sớm với `FILE_TOO_LARGE` mới được
thiếu metadata. `PENDING` và mọi preview thành công giữ đầy đủ metadata.
Giới hạn native là ngoại lệ đối với bước đọc `dataType` trước kiểm file
trong FR-002; role và CSRF vẫn phải được kiểm trước khi tạo lần nhập.

Leader đã duyệt sửa spec/contract trước khi code. Chỉ đổi hai field của
`ImportRun`, giữ nguyên danh sách required và các field khác; ràng buộc
schema giữ metadata không null trừ `FAILED / FILE_TOO_LARGE`:

```yaml
dataType:
  type: [string, 'null']
  oneOf:
    - $ref: '#/components/schemas/DataType'
    - type: 'null'
  description: Chỉ null khi FAILED do FILE_TOO_LARGE trước khi đọc được metadata.
fileName:
  type: [string, 'null']
  description: Chỉ null khi FAILED do FILE_TOO_LARGE trước khi đọc được metadata.
```

Không đề xuất đổi `DataType` enum, `ImportPreview` hay schema database.
Test T-08 phải có HTTP multipart thật vượt giới hạn để kiểm parser,
`FAILED`, `runId` và metadata thiếu; không chỉ dùng MockMultipartFile.

FE chỉ sửa `features/content-import/**`: types cho phép null ở ImportRun;
SCR-068/SCR-069 hiển thị loại dữ liệu là `—`, tên tệp là
`Không đọc được tên tệp`; thêm test và preview cho trạng thái này.
Mô tả PR phải ghi `contract change: ImportRun nullable fields`.
Không còn câu hỏi mở của T-08 về metadata; không đổi DB vì payload là JSONB.

### Phạm vi dùng chung đã duyệt trong Shadow Plan T-08

- `SecurityConfig`: thêm rule CONTENT_EDITOR cho imports trước rule ADMIN;
  thêm guard chỉ áp dụng imports để giữ thứ tự role trước CSRF, tái dùng
  JWT filter hiện có; các route khác giữ hành vi hiện tại.
- Thêm `shared/security/RouteRoleGuardFilter.java` và
  `shared/security/RouteCsrfTokenRequestHandler.java`; CSRF của imports chỉ
  đọc header đã quy định, tránh việc đọc form kích hoạt parser trước gate.
- `GlobalExceptionHandler`: chỉ thêm xử lý giữ `details` cho
  `TEMPLATE_MISMATCH` như leader đã chốt ở T-05.
- `application.yaml`: chỉ thêm cấu hình multipart và `cnhsk.import.*`;
  giới hạn native 2 MB / 3 MB, ngưỡng ghi tạm 3 MB để giữ file trong RAM,
  bật lazy parsing để controller xử lý lỗi multipart bằng formatter chung.
- `AuthContractTest`: chỉ thêm hợp đồng imports vào tập contract đang hoạt
  động; giữ nguyên các kiểm tra path/status của auth.

### Kiểm chứng T-08 và self-check Constitution

- FR-001/FR-002, AT-01: cả 5 role khác CONTENT_EDITOR bị chặn; khách 401;
  role được kiểm trước CSRF và trước đọc multipart. Cookie cần header CSRF,
  Bearer không có cookie được miễn theo spec 001. Test bằng MockMvc và HTTP thật.
- FR-008, AT-02: file 2 MB + 1 byte qua Tomcat trả 413 kèm runId;
  đọc lại PostgreSQL xác nhận FAILED, FILE_TOO_LARGE, dataType/fileName null.
  File hợp lệ và lỗi cấp file đã đọc metadata đều giữ đầy đủ loại/tên tệp.
- FR-012…014, AT-15…18: không ghi bảng nội dung; SHA-256 64 hex, TTL 30 phút;
  so sánh 7 mẫu, ô trống giữ cũ, lô tối đa 500; giới hạn preview 20/lỗi 50
  không cắt dữ liệu lưu; chỉ COMPLETED cùng hash mới báo đã nhập.
- API First: contract và quyết định nullable được leader duyệt trước code;
  `/v3/api-docs` đối chiếu tập contract auth + imports xanh, không bỏ kiểm tra auth.
- AC-03: production learning chỉ gọi `auth.api.AuthApi`; shared không phụ thuộc
  module nghiệp vụ; 8 kiểm tra ModuleBoundaryTest xanh. Không trả entity qua API.
- AC-05/06/09: không đổi schema/migration; JSONB dùng đúng payload nhập đã duyệt;
  không chạy DDL hay migration lên Neon; PostgreSQL/Redis dùng Testcontainers.
- HR-02/03/04/07/08/09: không đọc/commit env hay secret; không log CSV/nội dung lỗi
  parser; lỗi theo formatter chung có request_id, không stack trace ra client;
  không đổi thuộc tính cookie/CORS; validate input, truy vấn JPA có tham số;
  dữ liệu kiểm thử tự soạn. Không có thay đổi mật khẩu, tiền/điểm
  hoặc chấm điểm client trong task này.
- ES-01/02/03/06/07: giữ stack/dependency, naming hiện tại và nhánh part2;
  FE chỉ sửa features/content-import; shared/config chỉ thêm dòng đã được duyệt.
- ES-05: verify 770 test pass, 0 skip, JaCoCo line 94,61% (2983/3153);
  FE lint 0 lỗi, typecheck và 442 test pass. Warning LevelBadge có sẵn,
  không sửa file ngoài phạm vi. Không có TODO mới, diff --check sạch.
- Safety: không xoá file, không sửa Hiến pháp, không push main; commit/push
  nhánh spec theo ủy quyền của leader, PR tiếp tục Draft đến hết T-14.
  ContentImportMigrationIntegrationTest không bị sửa ở nhánh part2.

Không còn câu hỏi mở hay lệch spec của T-08 sau quyết định Q-T08-01.

## T-11 — Khôi phục lần nhập bị ngắt

Trạng thái: leader đã duyệt Shadow Plan và chốt Q-T11-01 trong phiên
2026-10-30, gồm file test bổ sung và thay đổi mốc bắt đầu chạy trong
`ImportService.confirm` đã commit ở T-10. Phần bổ sung T-10 nằm trong
commit T-11 và phải được ghi rõ trong mô tả PR.
Nguồn: FR-022, AT-27; `learning.service_requests` và `ServiceRequest` hiện có
`created_at`, `completed_at`, không có mốc bắt đầu chuyển sang `RUNNING`.

### Q-T11-01 — Mốc tính RUNNING cũ hơn 10 phút

FR-022 yêu cầu khi khởi động, chuyển lần nhập `IMPORT / RUNNING` cũ hơn
10 phút sang `FAILED`, `error_message = INTERRUPTED`, giữ các dòng nội dung
đã commit. Spec chưa chỉ rõ 10 phút tính từ lúc tạo preview hay lúc bắt đầu
ghi. Hai mốc khác nhau khi preview đã chờ lâu trước khi được xác nhận.

Quyết định đã duyệt:

- Không dùng `created_at`: DB Neon dùng chung, một ứng dụng khác khởi động
  không được đánh `FAILED` lần nhập mới bắt đầu ghi sau khi preview chờ lâu.
- Trong cùng transaction ngắn chuyển `PENDING -> RUNNING`, T-10 ghi
  `payload.runningSince = clock.instant().toString()` (UTC, ISO-8601).
- `ImportRecoveryRunner` chỉ chạy khi khởi động. Truy vấn bằng cột thật
  `kind = IMPORT`, `status = RUNNING`; sau đó trong Java đọc
  `payload.runningSince`. Không viết điều kiện SQL trên JSONB (`AC-09`).
- `runningSince < now - 10 phút` thì chuyển `FAILED`,
  `error_message = INTERRUPTED`; tại đúng mốc 10 phút thì giữ `RUNNING`.
  Giữ nguyên các dòng nội dung đã commit.
- Bản ghi `RUNNING` thiếu `runningSince` được coi là cũ: chuyển
  `FAILED / INTERRUPTED`, log `WARN` chỉ kèm `runId`, không log payload.
- Không đổi schema, không dùng `assigned_at` của luồng chấm bài.
- Test dùng Clock giả: mốc chạy 11 phút trước -> FAILED; mốc chạy 5 phút
  trước, preview tạo 25 phút trước -> giữ RUNNING; thiếu mốc -> FAILED + WARN.

Không còn câu hỏi mở của Q-T11-01; không đổi hợp đồng API.

### Phạm vi fixture H2 đã duyệt cho T-11

Leader duyệt thêm `@MockitoBean ImportMaintenance` ở
`CnhskApplicationTests` và `AuthContractTest`: hai context H2 không có bảng
`learning.service_requests`; runner/job được kiểm thật trong integration
test PostgreSQL/Testcontainers. Chỉ thêm import, mock và comment giải thích;
không đổi hoặc xoá assertion. Khi tích hợp spec 003, giữ cả cấu hình phạm vi
tài liệu của spec 003 và mock này nếu có xung đột trong `AuthContractTest`.

### Kiểm chứng T-11 và self-check Constitution

- FR-022, AT-27: runner thực sự chạy khi khởi động context; mốc chạy 11 phút
  trước thành FAILED/INTERRUPTED; 5 phút trước với preview tạo 25 phút trước
  giữ RUNNING; thiếu mốc thành FAILED và WARN chỉ runId. Đúng mốc 10 phút
  giữ RUNNING; không sửa request thuộc luồng khác hoặc đã đổi trạng thái.
- Timestamp runningSince được đọc từ DB trong writer để xác nhận transaction
  claim T-10 đã commit trước khi ghi dòng. Giả lập lỗi finalization sau khi
  dòng đã commit rồi phục hồi: nội dung đã ghi được giữ nguyên.
- FR-024, AT-28: job được đăng ký và thực thi qua runnable thật của Spring,
  cron 15 phút, zone Asia/Ho_Chi_Minh. Hết hạn lazy commit độc lập dù caller
  rollback; confirm vẫn lưu EXPIRED/dọn validRows trước khi trả 410.
  Giữ metadata/lỗi, xử lý lặp an toàn, khóa và kiểm lại trạng thái từng run.
- FR-025, AT-29; HR-02/09, BUS-06: appender kiểm log upload/confirm, cả lỗi
  writer chứa marker riêng, không lộ tên file/nội dung ô/email/exception.
  WARN legacy không kèm payload hoặc stack trace. Không đọc/in env hay secret.
- AC-01/03: giữ contract đã duyệt; production maintenance chỉ dùng learning,
  không truy cập bảng/module khác. AuthApi/AuditService hiện có được giữ;
  API vẫn trả DTO. 8 kiểm tra ModuleBoundaryTest và test hợp đồng xanh.
- BUS-02, AC-08/09: claim và timestamp cùng transaction; bảo trì mỗi run
  bằng REQUIRES_NEW và khóa JPA. Dùng Clock/Instant UTC; lọc kind/status bằng
  cột thật, kiểm JSONB trong Java, không dùng assigned_at cho import.
- AC-05/06, ES-01/02/03: giữ schema/migration, dependency, stack và naming;
  chỉ dùng DB Testcontainers, không chạy migration/DDL lên Neon.
- ES-05: verify 961 test pass, 0 failure/error/skip; JaCoCo line 95,42%
  (3354/3515), maintenance + runner 100% (49/49). T-11 thêm 49 test
  (29 unit + 20 PostgreSQL/Testcontainers). FE lint 0 lỗi, 1 warning sẵn có
  ở LevelBadge; typecheck và 442 test pass. Không đổi assertion fixture H2.
- Safety, ES-06/07: đúng phạm vi Shadow Plan và patch H2 đã duyệt; file sửa
  không BOM/TODO, diff --check sạch. Giữ nguyên file local ngoài task,
  Hiến pháp và ContentImportMigrationIntegrationTest. Một commit T-11,
  push nhánh spec và cập nhật PR Draft theo ủy quyền; không push main/merge.

Không còn câu hỏi mở hoặc lệch spec của T-11 sau Q-T11-01.

## T-12 — Báo cáo, lịch sử và mẫu CSV

Trạng thái: leader đã duyệt Shadow Plan và chốt Q-T12-01/02 trong phiên
2026-10-30. Nguồn: FR-026…FR-029, FR-034/035 và OpenAPI hiện hành.

### Q-T12-01 — Totals của GET khi không có snapshot preview bền vững

FR-026 liệt kê `totals`, nhưng không chốt giá trị theo trạng thái;
OpenAPI cho phép `totals = null`. T-08 hiện chỉ trả sáu số preview trong
response upload, không lưu chúng vào payload. T-10 lưu `result` khi
COMPLETED và chỉ giữ metadata; T-11 dọn validRows khi EXPIRED. Không thể
đọc lại đầy đủ sáu số preview ban đầu từ các bản ghi này. Tính lại hành động
với DB hiện tại cũng không phải snapshot lúc kiểm tra file.

Quyết định đã duyệt: không trả totals null cho mọi trạng thái.

- Upload lưu `payload.totals = {total, valid, invalid, create, update,
  unchanged}` cùng validRows. Đây là snapshot lúc kiểm file, không tính lại
  bằng DB hiện tại khi đọc báo cáo.
- COMPLETED và EXPIRED dọn validRows nhưng giữ totals cùng metadata.
  GET chi tiết/lịch sử đọc totals từ payload; result đọc đúng dữ liệu đã lưu.
- Totals null khi FAILED do lỗi cấp file chưa kiểm được dòng nào (như
  FILE_TOO_LARGE, ENCODING_ERROR). Bản ghi cũ thiếu totals trả null, không lỗi.
- Bổ sung lưu/giữ totals của T-08/T-10 trong commit T-12 và ghi rõ trong PR;
  không đổi DB. Test cả COMPLETED, EXPIRED và lỗi cấp file.

### Q-T12-02 — History bỏ failureReason nhưng schema hiện tại cần field này

FR-029 yêu cầu mỗi item lịch sử như FR-026 trừ `failureReason`.
Tuy nhiên OpenAPI `ImportRunPage.items` đang tham chiếu `ImportRun`;
nhánh cho phép metadata null trong `ImportRun.allOf.anyOf` bắt buộc
`failureReason = FILE_TOO_LARGE`, `status = FAILED`.

Vì vậy item lịch sử của lần multipart bị chặn sớm có dataType/fileName
null, nếu bỏ failureReason theo FR-029, sẽ không hợp lệ với schema hiện tại.
Không thể vừa bỏ field theo spec vừa giữ nguyên schema này cho lịch sử.

Quyết định đã duyệt: chọn B, chi tiết và lịch sử cùng schema ImportRun;
lịch sử trả cả failureReason. Sửa FR-029 bỏ cụm "trừ failureReason", ghi
dòng sửa ngày 2026-10-30 có leader duyệt. Giữ ImportRunPage.items tham chiếu
ImportRun và giữ ràng buộc metadata nullable của T-08. FE lịch sử hiển thị
lý do thất bại ngắn khi FAILED, chỉ sửa features/content-import/**.

Phạm vi mở rộng đã duyệt gồm ImportService (lưu/giữ snapshot T-08/T-10),
spec.md và FE content-import, kèm test cho hành vi này. Các assertion về
payload hoàn tất trong test T-10/T-11 được đồng bộ thêm khóa totals, giữ
kiểm containsOnlyKeys; không sửa assertion fixture H2 hay migration test.
Không còn câu hỏi mở của Q-T12-01/02.

### Kiểm chứng T-12 và self-check Constitution

- FR-026, AT-30: DTO chỉ có metadata được phép, createdBy lấy id/displayName
  qua AuthApi; tên theo lô cho lịch sử. Modern COMPLETED/EXPIRED giữ đủ totals;
  lỗi FILE_TOO_LARGE/ENCODING_ERROR và legacy thiếu snapshot trả null.
- Test tạo nội dung ở một lần nhập khác giữa preview và confirm: báo cáo
  giữ totals.create của preview, result.unchanged phản ánh đúng lúc ghi.
  Kiểm snapshot còn sau hoàn tất, expiry qua confirm, GET và scheduled job.
- FR-027/028, AT-31/32: 45 lỗi -> trang thứ ba 5 lỗi; CSV 46 record kể header,
  BOM UTF-8, đúng filename/content-type; thứ tự dòng/cột, null, cắt value theo
  100 ký tự Unicode và round-trip dấu phẩy/ngoặc kép/xuống dòng.
- FR-029, AT-33: 12 lần nhập của hai editor -> trang sau 2 record, mới nhất
  trước; chỉ kind IMPORT. Lịch sử trả failureReason, kể cả multipart chặn
  sớm có metadata null; FE hiện lý do ngắn và fallback mã chưa biết.
- FR-034/035, AT-41: đủ bảy mẫu, header/example khớp metadata; các CSV tải
  về được upload/confirm qua API theo thứ tự phụ thuộc. Dữ liệu tự soạn.
- AC-01/03, HR-07/09, BUS-06: spec/contract/decision cập nhật trước endpoint;
  role trước query, phân trang/dataType sai 422, run thiếu hoặc khác kind
  trả 404 theo spec 002. Production chỉ gọi auth.api.AuthApi; DTO không lộ
  email/hash/payload nội bộ. 8 ModuleBoundaryTest và test OpenAPI xanh.
- BUS-02, AC-08/09: snapshot và validRows cùng transaction lưu preview;
  giữ snapshot khi dọn, đọc lại sau maintenance commit. Clock/Instant UTC,
  cron giữ zone đã duyệt; không viết điều kiện SQL trên JSONB.
- AC-05/06, ES-01/02/03: không đổi DB, migration, dependency, stack hoặc
  shared/frontend nền. DB test chỉ Testcontainers; không chạy DDL lên Neon.
- ES-05: verify 1026 test pass, 0 failure/error/skip; JaCoCo line 95,50%
  (3456/3619). T-12 thêm 65 test BE và 6 FE; trang lỗi 20 dòng p95 cục bộ
  7 ms < 300 ms. FE lint 0 lỗi, 1 warning sẵn có ở LevelBadge; typecheck và
  448 test xanh. Không sửa assertion H2; 2 assertion payload giữ containsOnlyKeys
  và thêm đúng khóa totals theo quyết định leader.
- HR-02/08, Safety, ES-06/07: không đọc/in/commit env, secret hoặc dữ liệu
  giảng viên; không lưu CSV gốc. File sửa UTF-8 không BOM/TODO, diff sạch;
  giữ nguyên file local ngoài task và ContentImportMigrationIntegrationTest.
  Một commit T-12, push nhánh spec và giữ PR Draft theo ủy quyền.

Không còn câu hỏi mở hoặc lệch spec của T-12 sau hai quyết định đã duyệt.

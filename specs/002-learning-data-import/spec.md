# Feature Specification: Nhập dữ liệu học

**Feature:** `002-learning-data-import`
**Feature tree:** 6.2 (quản trị nội dung) · **Module:** `learning` (package `learning.content`, `learning.content.importer`)
**Use case:** UC-110 (nhập), UC-111 (báo cáo lỗi) · **Actor:** `CONTENT_EDITOR`
**Màn hình:** SCR-068 Learning Data Import · SCR-069 Import Report
**Scope:** MVP · **Priority:** P0 — spec 003→008 cần dữ liệu do spec này nạp

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — hợp đồng API đã duyệt, được code |
| **Approved by** | Leader (2026-10-29) |
| **Locked on** | 2026-10-29 — đổi hợp đồng phải sửa OpenAPI + spec trước, leader duyệt lại |
| **Phụ thuộc** | Spec 001 (đăng nhập, role, CSRF, định dạng lỗi, `AuditService`) |
| **Quyết định chung** | `plans/reports/brainstorm-261019-0551-spec-002-008-quyet-dinh-chung-report.md` (D-01, D-02, D-10, D-11, D-12) |

> **Codex đọc theo thứ tự:** `AGENTS.md` → spec 001 §0 (nền tảng) → file này → `contracts/openapi-import.yaml` → `tasks.md`.
> Chỗ spec không nói: **dừng, ghi `questions.md`**, không tự giả định.

---

## 0 · Phạm vi

### 0.1 · Trong / ngoài phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Nhập **7 mẫu** dữ liệu bằng CSV: chữ/từ (`LEXEME`), điểm ngữ pháp (`GRAMMAR`), bài tập ngữ pháp (`GRAMMAR_EXERCISE`), chủ đề (`TOPIC`), từ trong chủ đề (`TOPIC_ITEM`), tầng phát âm (`PRON_STAGE`), đơn vị phát âm (`PRON_UNIT`) | Nhập đề thi, câu hỏi (`questions`) — cần nhãn `knowledge_points` (BR-110-6), chờ cây tri thức |
| Hai bước: **kiểm tra + xem trước** (không ghi) → **xác nhận** (ghi từng dòng) | Nhập dữ liệu cây tri thức (KID, cạnh tiên quyết, Q-matrix) — plan cây |
| Báo cáo lỗi bền vững theo dòng/cột, phân trang, xuất CSV | Upload file audio/ảnh — dữ liệu chỉ mang **URL** |
| Lịch sử các lần nhập | Xoá hàng loạt, chế độ "thay thế toàn bộ" (SCR-068 cấm lộ thao tác phá huỷ) |
| Tải file mẫu (chỉ dòng tiêu đề + 1 dòng ví dụ tự tạo) | Sửa từng bản ghi qua giao diện (UC-137, spec sau) |

### 0.2 · Vì sao CSV, vì sao "mẫu v0"

Chưa có file mẫu thật từ Học Bá (UC-110 BLOCKED). Spec chốt **mẫu v0** (§4) để code được ngay:

- CSV UTF-8 vì Excel/Google Sheets xuất được, kiểm encoding chắc chắn, không cần thư viện đọc `.xlsx`.
- Mỗi loại một mẫu, một **khoá tự nhiên** → chạy lại an toàn (BR-110-8, BR-110-9).
- Khi có file Học Bá: nếu khác mẫu v0, `CONTENT_EDITOR` đổi tên/thứ tự cột trong Excel rồi xuất CSV. Nếu khác về **cấu trúc** (thiếu trường bắt buộc, một dòng chứa nhiều bản ghi) → viết `addendum-1.md` sửa §4, **không** sửa luồng §3.

### 0.3 · Thay đổi DB — `V3__content_import.sql`

| # | Thay đổi | Vì sao |
|---|---|---|
| 1 | `ALTER TABLE learning.topics ADD COLUMN code VARCHAR(50) NOT NULL` + `UNIQUE (code)` | Chủ đề chưa có khoá tự nhiên → không upsert được. Bảng rỗng lúc chạy (không seed) nên `NOT NULL` an toàn |
| 2 | ~~`learning.topic_items`: bỏ PK cũ, thêm `id BIGSERIAL PRIMARY KEY`, `uq_ti_lexeme`, `uq_ti_kp`~~ — **đã có sẵn ở V1** (2026-10-20), V3 **không** làm lại | PK cũ gồm cột cho phép NULL — PostgreSQL ép `NOT NULL` cho mọi cột PK nên **không chèn được** dòng chỉ có `lexeme_id`; bỏ PK cũng không gỡ `NOT NULL`, nên sửa thẳng trong V1 |
| 3 | `ALTER TABLE learning.pron_stages ADD COLUMN units JSONB NOT NULL DEFAULT '[]'` | Đơn vị luyện của tầng (D-02). Chỉ đổi qua nhập file → hợp `AC-09` |
| 4 | `DROP INDEX learning.uq_import_file` | Chặn chạy lại cùng file — trái UC-110 A1 |
| 5 | `CREATE UNIQUE INDEX uq_import_running ON learning.service_requests(kind) WHERE kind = 'IMPORT' AND status = 'RUNNING'` | Một lần nhập đang ghi tại một thời điểm (`CONCURRENT_IMPORT`) |
| 6 | `CREATE INDEX ix_req_import_hist ON learning.service_requests(created_at DESC) WHERE kind = 'IMPORT'` | Lịch sử nhập phân trang |

> Đổi schema cần người duyệt (`AGENTS.md` §4) → duyệt cùng spec. `database.md` cập nhật theo ở bước khoá.

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-05** | Demo trọn luồng học | Không có dữ liệu thì mọi chế độ học rỗng. Đây là **đường duy nhất** đưa dữ liệu vào (không seed, V4 của plan) |
| **BG-04** | Nội dung qua kiểm tra trước khi tới người học | Kiểm từng dòng, xem trước, xác nhận rõ ràng |

**Vấn đề:** dữ liệu giáo viên đến từ Excel, dễ hỏng chữ Hán khi lưu sai encoding, dễ trùng khi nhập lại,
và một dòng sai không được làm mất 999 dòng đúng.

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | File 5 000 dòng hợp lệ: kiểm tra < 10 s, xác nhận < 30 s | p95 |
| **SC-002** | Chạy lại cùng file 2 lần → số bản ghi không đổi | 100% |
| **SC-003** | File có 10/100 dòng lỗi → 90 dòng vào kho, 10 lỗi có dòng + cột + lý do tiếng Việt | 100% |
| **SC-004** | File lưu ANSI làm hỏng chữ Hán → 0 dòng được ghi | 100% |

---

## 2 · User Stories

### US-1 · Nhập file mới (P1)
**Given** tôi là `CONTENT_EDITOR`, có file `tu-vung-hsk1.csv` đúng mẫu `LEXEME`
**When** tôi chọn loại "Chữ và từ vựng", chọn file, bấm "Tải lên và kiểm tra"
**Then** tôi thấy tổng số dòng, số hợp lệ, số lỗi, 20 dòng xem trước kèm hành động (Tạo mới / Cập nhật / Không đổi) — **chưa có gì được ghi**
**When** tôi bấm "Xác nhận nhập dữ liệu"
**Then** các dòng hợp lệ vào kho, tôi được chuyển tới báo cáo lần nhập (SCR-069)

### US-2 · Sửa lỗi và nhập lại (P1)
**Given** lần nhập trước có 10 dòng lỗi
**When** tôi mở báo cáo, tải danh sách lỗi, sửa file, nhập lại **cả file**
**Then** 90 dòng cũ được báo "Không đổi", 10 dòng đã sửa được "Tạo mới", không có bản trùng

### US-3 · File hỏng encoding (P1)
**Given** file lưu bằng ANSI, chữ Hán thành `???`
**When** tôi tải lên
**Then** tôi nhận lỗi "File không phải UTF-8 hoặc chữ Hán bị hỏng" và **không** có xem trước

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Hai `CONTENT_EDITOR` cùng bấm xác nhận hai lần nhập khác nhau | Một chạy, một nhận **409** `CONCURRENT_IMPORT`, lần nhập đó vẫn `PENDING` để thử lại |
| E2 | Bấm xác nhận sau 30 phút | **410** `IMPORT_PREVIEW_EXPIRED`, phải tải lại file |
| E3 | Cùng khoá tự nhiên xuất hiện 2 lần trong file | Dòng đầu hợp lệ, dòng sau lỗi `DUPLICATE_IN_FILE` |
| E4 | `TOPIC_ITEM` trỏ tới từ chưa nhập | Dòng lỗi `REFERENCE_NOT_FOUND`, gợi ý "nhập mẫu Chữ và từ vựng trước" |
| E5 | Lỗi DB khi ghi dòng thứ 37 | Dòng 37 ghi lỗi `ROW_WRITE_FAILED`, dòng 1–36 và 38+ vẫn được ghi |
| E6 | Ô tuỳ chọn để trống khi cập nhật bản ghi đã có | **Giữ** giá trị cũ, không ghi `NULL` |
| E7 | Đóng tab sau khi kiểm tra, không xác nhận | Lần nhập ở `PENDING`, tự hết hạn sau 30 phút → `EXPIRED`, không ghi gì |
| E8 | File cùng hash với lần nhập đã `COMPLETED` | Xem trước có cảnh báo; xác nhận cần `acknowledgeRerun=true`, thiếu → **409** `RERUN_CONFIRMATION_REQUIRED` |

---

## 3 · Functional Requirements

### Nhóm 1 — Tải lên và kiểm tra (FR-001…FR-014)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL chỉ cho `CONTENT_EDITOR` gọi mọi endpoint `/api/admin/imports/**`; role khác → **403** `FORBIDDEN`. `ADMIN` **không** tự có quyền này (BR-110-2) |
| **FR-002** | WHEN nhận `POST /api/admin/imports` (multipart: `dataType`, `file`), THE system SHALL kiểm theo thứ tự: role → CSRF (spec 001) → `dataType` thuộc §4 → đuôi `.csv` → kích thước ≤ **2 MB** → giải mã UTF-8 nghiêm ngặt → dòng tiêu đề → số dòng dữ liệu 1…**5 000** |
| **FR-003** | WHERE file > 2 MB, THE system SHALL trả **413** `FILE_TOO_LARGE` (cấu hình `spring.servlet.multipart.max-file-size=2MB`, `max-request-size=3MB`, handler chuyển `MaxUploadSizeExceededException` thành mã này) |
| **FR-004** | THE system SHALL giải mã bằng `CharsetDecoder` UTF-8 với `CodingErrorAction.REPORT`; byte sai → **422** `ENCODING_ERROR`. BOM UTF-8 đầu file được bỏ qua |
| **FR-005** | THE system SHALL đọc CSV theo RFC 4180 (dấu phẩy, ngoặc kép, xuống dòng trong ô có ngoặc) bằng `commons-csv` `CSVFormat.RFC4180` có header; tên cột so khớp **chính xác, phân biệt hoa thường** sau khi bỏ khoảng trắng hai đầu |
| **FR-006** | WHEN thiếu cột bắt buộc hoặc có cột lạ, THE system SHALL trả **422** `TEMPLATE_MISMATCH` kèm `details` liệt kê từng cột thiếu/lạ. Thứ tự cột **không** quan trọng |
| **FR-007** | WHEN 0 dòng dữ liệu → **422** `EMPTY_FILE`; > 5 000 dòng → **422** `TOO_MANY_ROWS`. Dòng toàn ô trống bị bỏ qua, không tính |
| **FR-008** | WHERE lỗi cấp file (FR-003…FR-007), THE system SHALL vẫn tạo một lần nhập `status='FAILED'`, `error_message` = mã lỗi, để lịch sử thấy lần thử; response lỗi kèm `runId`. WHEN multipart bị chặn sớm bởi `MaxUploadSizeExceededException` trước khi đọc metadata, THE system SHALL vẫn ghi `FAILED`, `error_message='FILE_TOO_LARGE'` và trả **413** kèm `runId`; `payload.dataType`/`payload.fileName` được `null` trong trường hợp này, không đoán metadata |
| **FR-009** | THE system SHALL kiểm từng dòng theo quy tắc §4 **sau khi** chuẩn hoá (FR-030). Một dòng có thể sinh nhiều lỗi (mỗi cột sai một lỗi) |
| **FR-010** | THE system SHALL phát hiện khoá tự nhiên trùng trong cùng file: lần xuất hiện đầu hợp lệ, các lần sau lỗi `DUPLICATE_IN_FILE` |
| **FR-011** | THE system SHALL kiểm tham chiếu (từ, chủ đề, điểm ngữ pháp, tầng) **trong DB** lúc kiểm tra; không tìm thấy → `REFERENCE_NOT_FOUND`. Tham chiếu tới bản ghi nằm trong **cùng file loại khác** không được hỗ trợ — phải nhập loại cha trước (thứ tự §4.0) |
| **FR-012** | WHEN kiểm tra xong, THE system SHALL tạo `service_requests` `kind='IMPORT'`, `status='PENDING'`, `file_hash` = SHA-256 hex nội dung file, `payload` = `{dataType, fileName, totalRows, totals:{total, valid, invalid, create, update, unchanged}, validRows:[dòng đã chuẩn hoá kèm lineNo], expiresAt}`, `error_rows` = danh sách lỗi; **không** ghi bảng nội dung nào |
| **FR-013** | THE system SHALL tính cho mỗi dòng hợp lệ hành động dự kiến so với DB: `CREATE` (khoá chưa có), `UPDATE` (có, ít nhất một ô khác), `UNCHANGED` (có, mọi ô có giá trị đều bằng) |
| **FR-014** | THE system SHALL trả **201** `{runId, dataType, fileName, status:'PENDING', totals:{total, valid, invalid, create, update, unchanged}, alreadyImported, expiresAt, preview:[≤20 dòng hợp lệ đầu tiên], errors:[≤50 lỗi đầu], errorCount}`. `alreadyImported = true` khi tồn tại lần nhập `COMPLETED` cùng `file_hash` |

**Làm rõ FR-002 (leader chốt 2026-10-30):** Đuôi `.csv` không phân biệt hoa thường (`.csv`, `.CSV`, `.Csv`).

### Nhóm 2 — Xác nhận và ghi (FR-015…FR-025)

| Mã | Yêu cầu |
|---|---|
| **FR-015** | WHEN nhận `POST /api/admin/imports/{id}/confirm` `{acknowledgeRerun}`, THE system SHALL kiểm: lần nhập tồn tại (**404** `IMPORT_RUN_NOT_FOUND`), `status='PENDING'` (khác → **409** `IMPORT_NOT_PENDING`), chưa hết hạn (**410** `IMPORT_PREVIEW_EXPIRED`, đồng thời chuyển `EXPIRED`), `valid > 0` (**422** `NO_VALID_ROWS`) |
| **FR-016** | WHERE `alreadyImported` và `acknowledgeRerun ≠ true`, THE system SHALL trả **409** `RERUN_CONFIRMATION_REQUIRED`, giữ `PENDING` |
| **FR-017** | THE system SHALL chuyển `PENDING → RUNNING` trong một transaction ngắn; vi phạm `uq_import_running` → **409** `CONCURRENT_IMPORT`, giữ `PENDING` |
| **FR-018** | THE system SHALL ghi **từng dòng trong transaction riêng** (`TransactionTemplate` `PROPAGATION_REQUIRES_NEW`), theo thứ tự `lineNo`; mỗi dòng tính lại hành động so với DB **tại lúc ghi** |
| **FR-019** | WHEN một dòng ghi lỗi (ngoại lệ DB, vi phạm ràng buộc), THE system SHALL rollback **chỉ dòng đó**, thêm lỗi `ROW_WRITE_FAILED` (thông báo chung, không lộ SQL) vào `error_rows`, và tiếp tục dòng sau (BR-110-5) |
| **FR-020** | WHEN cập nhật bản ghi đã có, THE system SHALL chỉ ghi đè cột có giá trị trong file; ô tuỳ chọn trống **giữ** giá trị cũ (E6). Không có thao tác xoá |
| **FR-021** | WHEN ghi xong mọi dòng, THE system SHALL đặt `status='COMPLETED'`, `completed_at=now()`, `result={total, valid, invalid, created, updated, unchanged, failed}`, xoá `payload.validRows` (chỉ giữ `{dataType, fileName, totalRows, totals}`; bản ghi cũ thiếu totals giữ nguyên thiếu), và ghi `audit_logs` `action='IMPORT_CONFIRM'`, `entity='import:{id}'`, `after=result` |
| **FR-022** | WHERE xử lý bị ngắt giữa chừng (ứng dụng tắt), THE system SHALL để lần nhập ở `RUNNING`; khi khởi động, job `ImportRecoveryRunner` chuyển mọi `RUNNING` cũ hơn 10 phút sang `FAILED` (`error_message='INTERRUPTED'`). Các dòng đã ghi giữ nguyên (mỗi dòng là transaction riêng); chạy lại cùng file là an toàn |
| **FR-023** | THE system SHALL xử lý xác nhận **đồng bộ** trong request (≤ 5 000 dòng); response **200** = tóm tắt như FR-026 |
| **FR-024** | THE system SHALL chuyển mọi lần nhập `PENDING` có `expiresAt < now()` sang `EXPIRED` và xoá `payload.validRows`: khi bị truy cập (lazy) **và** bằng job `@Scheduled(cron="0 */15 * * * *", zone="Asia/Ho_Chi_Minh")` |
| **FR-025** | THE system SHALL KHÔNG lưu file gốc ở đĩa, DB hay log (BR-110-12, `AGENTS.md` §5); log chỉ ghi `runId`, `dataType`, số dòng |

**Làm rõ FR-022 (leader chốt 2026-10-30):** Job định kỳ cũng thu hồi lần nhập `RUNNING` quá 10 phút (hoặc thiếu `runningSince`) → `FAILED`/`INTERRUPTED`, cùng luật với lúc khởi động.

### Nhóm 3 — Báo cáo và lịch sử (FR-026…FR-029)

| Mã | Yêu cầu |
|---|---|
| **FR-026** | `GET /api/admin/imports/{id}` SHALL trả `{runId, dataType, fileName, status, createdAt, completedAt, createdBy:{id, displayName}, totals, result, errorCount, failureReason}`. `dataType`/`fileName` chỉ được `null` khi `status='FAILED'` và `failureReason='FILE_TOO_LARGE'` do multipart chặn sớm chưa đọc được metadata; mọi trường hợp khác, gồm preview, `COMPLETED` và lỗi cấp file đã đọc được metadata, bắt buộc có đủ. `displayName` lấy qua `auth.api.AuthApi.displayNames(Set<Long>)` (`AC-03`) |
| **FR-027** | `GET /api/admin/imports/{id}/errors?page&size` SHALL trả lỗi sắp theo `line`, rồi theo thứ tự cột trong mẫu; `size` 1–100, mặc định 20; sai → **422** `VALIDATION_FAILED`. Mỗi lỗi: `{line, column, value, code, message}`; `value` cắt còn 100 ký tự |
| **FR-028** | `GET /api/admin/imports/{id}/errors.csv` SHALL trả `text/csv; charset=UTF-8` có BOM (để Excel đọc đúng chữ Hán), cột `line,column,value,code,message`, toàn bộ lỗi, tên file `import-{id}-errors.csv` |
| **FR-029** | `GET /api/admin/imports?page&size` SHALL trả lịch sử mọi lần nhập (mọi `CONTENT_EDITOR` thấy hết), sắp `created_at` giảm dần, `size` 1–100 mặc định 10, mỗi dòng như FR-026, gồm `failureReason` |

**Làm rõ FR-028 (leader chốt 2026-10-30):** Ô bắt đầu bằng `=`, `+`, `-`, `@`, tab hoặc CR được thêm tiền tố `'` để chống CSV injection.

**Sửa 2026-10-30 (leader duyệt):** FR-029 dùng chung schema `ImportRun` với chi tiết, lịch sử trả cả `failureReason`; FE hiện lý do ngắn khi `FAILED`. FR-012 lưu `payload.totals` cùng `validRows`; FR-021/FR-024 dọn `validRows` nhưng giữ `{dataType, fileName, totalRows, totals}` (hết hạn có thể giữ `expiresAt`). FR-026/FR-029 đọc snapshot `totals` từ payload, `result` từ kết quả đã lưu. `totals=null` chỉ khi lỗi cấp file chưa kiểm được dòng nào hoặc bản ghi cũ thiếu snapshot; không tính lại theo DB hiện tại. Bổ sung T-08/T-10 trong commit T-12, không đổi DB; xem Q-T12-01/02 tại `questions.md`.

### Nhóm 4 — Chuẩn hoá (FR-030…FR-033)

| Mã | Yêu cầu |
|---|---|
| **FR-030** | THE system SHALL chuẩn hoá mọi ô: Unicode **NFC**, bỏ khoảng trắng hai đầu, gộp khoảng trắng liên tiếp thành một (trừ cột văn bản dài `*_vi`, `explanation*` chỉ bỏ hai đầu) |
| **FR-031** | THE system SHALL tính `pinyin_norm` = pinyin bỏ dấu thanh (NFD, bỏ ký tự kết hợp U+0300–U+036F; `ü`/`ǖǘǚǜ` → `v`), chữ thường, bỏ khoảng trắng, `'`, `-`. Ví dụ `nǚ'ér` → `nver`, `Nǐ hǎo` → `nihao` |
| **FR-032** | THE system SHALL tính `meaning_norm` = `meaning_vi` bỏ dấu tiếng Việt (NFD bỏ ký tự kết hợp; `đ/Đ` → `d`), chữ thường |
| **FR-033** | THE system SHALL cung cấp `learning.content.text.PinyinUtils.toNumbered(String)` đổi pinyin dấu → số (`nǐ hǎo` → `ni3 hao3`, thanh nhẹ → `5`, `ü` → `v`) — spec 003/008 dùng để chấm ô gõ pinyin. Test bảng ≥ 20 ca, gồm `lǜ`, `nǚ'ér`, `yī`, `de`, `Zhōngguó` |

### Nhóm 5 — Mẫu và giao diện (FR-034…FR-040)

| Mã | Yêu cầu |
|---|---|
| **FR-034** | `GET /api/admin/imports/templates` SHALL trả danh sách 7 mẫu §4: `{dataType, label, naturalKey[], dependsOn[], columns:[{name, required, maxLength, description, example}]}` — sinh từ cùng định nghĩa Java `ImportTemplate` mà parser dùng (một nguồn sự thật) |
| **FR-035** | `GET /api/admin/imports/templates/{dataType}.csv` SHALL trả file UTF-8 có BOM gồm dòng tiêu đề + **1 dòng ví dụ tự tạo** (cột `example`) |
| **FR-036** | SCR-068 SHALL có: chọn loại dữ liệu (7 lựa chọn, nhãn §4), khung hướng dẫn cột + nút tải mẫu, chọn file (`accept=".csv"`), nút "Tải lên và kiểm tra"; kiểm phía client kích thước ≤ 2 MB và đuôi `.csv` trước khi gửi (server vẫn kiểm lại) |
| **FR-037** | SCR-068 sau khi kiểm tra SHALL hiện: 6 con số `totals`, bảng xem trước (cột = cột mẫu + "Hành động"), bảng lỗi (50 đầu, link "Xem tất cả" → SCR-069), cảnh báo nhập lại nếu `alreadyImported` kèm ô tick "Tôi hiểu file này đã nhập trước đó", đồng hồ hết hạn, nút "Xác nhận nhập dữ liệu" (khoá khi `valid=0` hoặc thiếu tick) và nút "Huỷ" (chỉ xoá trạng thái trên trang) |
| **FR-038** | SCR-068 SHALL hiện bước xác nhận **trong trang** ("Ghi {valid} dòng vào kho? Không hoàn tác được.") trước khi gọi `confirm` (không dùng `window.confirm`) |
| **FR-039** | SCR-069 SHALL hiện tóm tắt (FR-026), bảng lỗi phân trang 20, nút "Tải danh sách lỗi" (FR-028), nút "Sửa file và nhập lại" (về SCR-068 với `dataType` chọn sẵn). Không lỗi → "Nhập thành công {created+updated+unchanged} dòng" |
| **FR-040** | SCR-068 SHALL có danh sách lịch sử (FR-029) phân trang 10, bấm một dòng → SCR-069. Rỗng → "Chưa có lần nhập dữ liệu" |

### Key Entities

| Bảng | Dùng thế nào |
|---|---|
| `learning.service_requests` (`kind='IMPORT'`) | Lần nhập: trạng thái, hash, payload tạm, lỗi, kết quả |
| `learning.lexemes` | Đích của `LEXEME`, `GRAMMAR`, `GRAMMAR_EXERCISE` |
| `learning.topics`, `learning.topic_items` | Đích của `TOPIC`, `TOPIC_ITEM` |
| `learning.pron_stages` | Đích của `PRON_STAGE`, `PRON_UNIT` |
| `shared.audit_logs` | `IMPORT_CONFIRM` |

---

## 4 · Data Model — mẫu nhập v0

### 4.0 · Bảy mẫu và thứ tự nhập

| `dataType` | Nhãn trên SCR-068 | Khoá tự nhiên | Phụ thuộc (nhập trước) |
|---|---|---|---|
| `LEXEME` | Chữ và từ vựng | `(kind, text)` = `uq_lexeme` | — |
| `TOPIC` | Chủ đề từ vựng | `code` | — |
| `TOPIC_ITEM` | Từ trong chủ đề | `(topic_code, kind, text)` | `TOPIC`, `LEXEME` |
| `GRAMMAR` | Điểm ngữ pháp | `text` (lưu `kind='GRAMMAR'`) | — |
| `GRAMMAR_EXERCISE` | Bài tập ngữ pháp | `(grammar_text, exercise_no)` | `GRAMMAR` |
| `PRON_STAGE` | Tầng phát âm | `stage_index` | — |
| `PRON_UNIT` | Bài nghe phát âm | `(stage_index, unit_code)` | `PRON_STAGE` |

**Quy ước chung:** `*` = bắt buộc. "CJK" = ký tự thuộc khối `CJK Unified Ideographs` (U+4E00–U+9FFF) hoặc
`Extension A` (U+3400–U+4DBF). "URL" = bắt đầu `https://` hoặc `http://`, ≤ 500 ký tự, không khoảng trắng.
Ô chứa `U+FFFD` hoặc có `?` trong cột bắt buộc CJK → lỗi `BROKEN_TEXT` (BR-110-10).

### 4.1 · `LEXEME` → `learning.lexemes`

| Cột | Bắt buộc | Quy tắc | Ghi vào |
|---|---|---|---|
| `kind` | * | `CHAR` hoặc `WORD` | `kind` |
| `text` | * | `CHAR`: đúng 1 ký tự CJK · `WORD`: 1–20 ký tự, **toàn bộ** CJK | `text` |
| `pinyin` | * | ≤ 200; chỉ chữ a–z, `ü`, nguyên âm có dấu thanh, khoảng trắng, `'`, `-` | `pinyin`, `pinyin_norm` (FR-031) |
| `sino_viet` | | ≤ 200 | `sino_viet` |
| `meaning_vi` | * | ≤ 1 000; nhiều nghĩa ngăn bằng `;` | `meaning_vi`, `meaning_norm` (FR-032) |
| `hsk_level` | * | số nguyên 1–9 | `hsk_level` |
| `audio_url` | | URL | `audio_url` |
| `radical` | | chỉ `CHAR`; đúng 1 ký tự (CJK hoặc bộ thủ U+2E80–U+2FDF) | `radical` |
| `stroke_count` | | chỉ `CHAR`; 1–64 | `stroke_count` |
| `example_cn` | | ≤ 300; có ít nhất 1 CJK; nếu có thì `example_vi` bắt buộc | `extra.examples[0].cn` |
| `example_pinyin` | | ≤ 500 | `extra.examples[0].pinyin` |
| `example_vi` | | ≤ 500 | `extra.examples[0].vi` |
| `tags` | | ≤ 10 nhãn ngăn bằng `|`, mỗi nhãn ≤ 30 | `extra.tags` |

`radical`/`stroke_count` có ở dòng `WORD` → lỗi `INVALID_VALUE`.

### 4.2 · `TOPIC` → `learning.topics`

| Cột | Bắt buộc | Quy tắc | Ghi vào |
|---|---|---|---|
| `code` | * | `^[A-Z0-9_-]{2,50}$` | `code` |
| `name` | * | ≤ 200 | `name` |
| `hsk_level` | * | 1–9 | `hsk_level` |
| `order_index` | | số nguyên 0–100 000, mặc định 0 | `order_index` |

`prereq_ids` **không** nhập (luật lộ trình thuộc cây tri thức, D-06).

### 4.3 · `TOPIC_ITEM` → `learning.topic_items`

| Cột | Bắt buộc | Quy tắc | Ghi vào |
|---|---|---|---|
| `topic_code` | * | phải tồn tại trong `topics` | `topic_id` |
| `kind` | * | `CHAR` hoặc `WORD` | — |
| `text` | * | `(kind, text)` phải tồn tại trong `lexemes` | `lexeme_id` |
| `order_index` | | 0–100 000, mặc định 0 | `order_index` |

`knowledge_point_id` luôn `NULL`.

### 4.4 · `GRAMMAR` → `learning.lexemes` (`kind='GRAMMAR'`)

| Cột | Bắt buộc | Quy tắc | Ghi vào |
|---|---|---|---|
| `text` | * | ≤ 200, có ít nhất 1 CJK (vd `是……的`, `把 + O + V`) | `text` |
| `hsk_level` | * | 1–9 | `hsk_level` |
| `title_vi` | * | ≤ 300 — tên ngắn tiếng Việt | `meaning_vi`, `meaning_norm` |
| `structure` | | ≤ 300 | `extra.structure` |
| `explanation_vi` | * | ≤ 3 000 | `extra.explanation` |
| `common_mistake_vi` | | ≤ 1 000 | `extra.commonMistake` |
| `example1_cn` … `example3_cn` | `example1_cn` * | ≤ 300, có CJK; mỗi `exampleN_cn` cần `exampleN_vi` | `extra.examples[N-1].cn` |
| `example1_pinyin` … `example3_pinyin` | | ≤ 500 | `extra.examples[N-1].pinyin` |
| `example1_vi` … `example3_vi` | `example1_vi` * | ≤ 500 | `extra.examples[N-1].vi` |

Cập nhật **giữ nguyên** `extra.exercises` (do `GRAMMAR_EXERCISE` quản).

### 4.5 · `GRAMMAR_EXERCISE` → `learning.lexemes.extra.exercises`

| Cột | Bắt buộc | Quy tắc | Ghi vào phần tử `exercises[]` |
|---|---|---|---|
| `grammar_text` | * | phải tồn tại `lexemes(kind='GRAMMAR', text)` | — |
| `exercise_no` | * | 1–20 | `no` |
| `stem` | * | ≤ 500; có thể chứa `___` | `stem` |
| `option_a`, `option_b` | * | ≤ 200 | `options[{key:'A',text}]`… |
| `option_c`, `option_d` | | ≤ 200; `option_d` có thì `option_c` bắt buộc | |
| `answer` | * | `A`–`D`, phải trỏ tới phương án có giá trị | `answerKey` |
| `explanation_vi` | | ≤ 1 000 | `explanation` |

Các phương án (đã chuẩn hoá) phải khác nhau đôi một, nếu không → `INVALID_ANSWER`. Upsert theo `no` trong mảng; mảng luôn sắp theo `no`.

**Làm rõ FR-018…FR-020, §4.5/§4.7 (leader chốt 2026-10-30):** Trước khi lưu bài tập ngữ pháp hoặc đơn vị phát âm, kiểm lại phương án và đáp án trên dữ liệu sau khi ghép với giá trị cũ: các phương án có nội dung sau chuẩn hoá phải khác nhau đôi một, đáp án phải trỏ tới phương án có nội dung. Vi phạm → `INVALID_ANSWER` theo §4.8, rollback chỉ dòng đó và tiếp tục dòng sau.

### 4.6 · `PRON_STAGE` → `learning.pron_stages`

| Cột | Bắt buộc | Quy tắc | Ghi vào |
|---|---|---|---|
| `stage_index` | * | 1–8 | `stage_index` |
| `name` | * | ≤ 100 | `name` |
| `description` | | ≤ 2 000 | `description` |
| `target_sounds` | | ≤ 30 mục ngăn `|`, mỗi mục ≤ 20 | `target_sounds` (mảng chuỗi) |
| `pass_percent` | | số 0–100, tối đa 2 chữ số thập phân, mặc định 80 (`BUS-11`) | `pass_percent` |

Cập nhật **giữ nguyên** `units`.

### 4.7 · `PRON_UNIT` → `learning.pron_stages.units`

| Cột | Bắt buộc | Quy tắc | Ghi vào phần tử `units[]` |
|---|---|---|---|
| `stage_index` | * | tầng phải tồn tại | — |
| `unit_code` | * | `^[A-Za-z0-9_-]{1,30}$` | `code` |
| `audio_url` | * | URL | `audioUrl` |
| `prompt_vi` | | ≤ 200, mặc định "Nghe và chọn đáp án đúng" | `prompt` |
| `option_a` … `option_d` | a, b * | ≤ 50 (pinyin, thanh mẫu, vận mẫu hoặc số thanh) | `options[]` |
| `answer` | * | `A`–`D` trỏ tới phương án có giá trị | `answerKey` |
| `explanation_vi` | | ≤ 500 | `explanation` |

Upsert theo `code`; mảng giữ thứ tự nhập đầu tiên, phần tử mới thêm cuối. Tối đa 100 đơn vị/tầng → vượt: `INVALID_VALUE`.

### 4.8 · Lỗi cấp dòng (`error_rows[].code`)

| Mã | Thông báo tiếng Việt (mẫu) |
|---|---|
| `REQUIRED_MISSING` | "Thiếu giá trị bắt buộc ở cột {column}" |
| `TOO_LONG` | "Cột {column} dài quá {max} ký tự" |
| `INVALID_VALUE` | "Giá trị không hợp lệ ở cột {column}: {lý do}" |
| `INVALID_HSK_LEVEL` | "Cấp HSK phải từ 1 đến 9" |
| `NOT_CJK` | "Cột {column} phải là chữ Hán" |
| `BROKEN_TEXT` | "Chữ Hán bị hỏng (có ? hoặc ký tự lạ) — kiểm tra file đã lưu UTF-8 chưa" |
| `INVALID_PINYIN` | "Pinyin chứa ký tự không hợp lệ" |
| `INVALID_URL` | "Đường dẫn phải bắt đầu bằng http:// hoặc https://" |
| `INVALID_ANSWER` | "Đáp án phải là A–D, trỏ tới phương án có nội dung; các phương án không được trùng" |
| `DUPLICATE_IN_FILE` | "Trùng khoá với dòng {firstLine}" |
| `REFERENCE_NOT_FOUND` | "Không tìm thấy {loại} '{giá trị}' — nhập mẫu {mẫu cha} trước" |
| `ROW_WRITE_FAILED` | "Không ghi được dòng này do lỗi hệ thống — thử nhập lại" |

`line` = số dòng vật lý trong CSV, dòng tiêu đề = 1. `column = null` với lỗi cả dòng.

### 4.9 · Trạng thái lần nhập

```
(upload) → PENDING ──confirm──► RUNNING ──► COMPLETED
   │           │                     └──(sập)──► FAILED (INTERRUPTED)
   │           └──(30 phút)──► EXPIRED
   └──(lỗi cấp file)──► FAILED
```

`PENDING` dùng cột `status` có sẵn (`'PENDING'`). Không thêm giá trị CHECK mới.

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | spec 001 |
| `FORBIDDEN` | 403 | Không có `CONTENT_EDITOR` | FR-001 |
| `CSRF_TOKEN_INVALID` | 403 | Thiếu/sai CSRF khi dùng cookie | spec 001 |
| `VALIDATION_FAILED` | 422 | `dataType` lạ, thiếu `file`, phân trang sai | FR-002, FR-027 |
| `FILE_TOO_LARGE` | 413 | > 2 MB | FR-003 |
| `UNSUPPORTED_FILE_FORMAT` | 422 | Không phải `.csv` hoặc không đọc được như CSV | FR-002, FR-005 |
| `ENCODING_ERROR` | 422 | Không phải UTF-8 | FR-004 |
| `TEMPLATE_MISMATCH` | 422 | Thiếu/thừa cột, kèm `details` | FR-006 |
| `EMPTY_FILE` | 422 | 0 dòng dữ liệu | FR-007 |
| `TOO_MANY_ROWS` | 422 | > 5 000 dòng | FR-007 |
| `IMPORT_RUN_NOT_FOUND` | 404 | `id` không tồn tại | FR-015, FR-026 |
| `IMPORT_NOT_PENDING` | 409 | Xác nhận lần nhập không ở `PENDING` | FR-015 |
| `IMPORT_PREVIEW_EXPIRED` | 410 | Quá 30 phút | FR-015 |
| `NO_VALID_ROWS` | 422 | Không có dòng hợp lệ | FR-015 |
| `RERUN_CONFIRMATION_REQUIRED` | 409 | File đã nhập, chưa xác nhận nhập lại | FR-016 |
| `CONCURRENT_IMPORT` | 409 | Đang có lần nhập khác ghi | FR-017 |

Lỗi cấp file (413, 422 nhóm FR-003…FR-007) có thêm `runId` trong body.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-01** API First | `contracts/openapi-import.yaml` |
| **AC-03** Ranh giới module | Đọc tên người nhập qua `AuthApi.displayNames`; không đọc `auth.users` |
| **AC-05** Flyway | `V3__content_import.sql`, không sửa V1/V2 |
| **AC-06** | `dataType` là enum Java, lưu chuỗi trong `payload` |
| **AC-09** JSONB chỉ đọc | `payload`, `error_rows`, `extra`, `units` chỉ ghi qua luồng nhập; không truy vấn điều kiện trên JSONB (lịch sử không lọc theo `dataType`) |
| **HR-05** CSRF | Upload + confirm qua cookie cần `X-XSRF-TOKEN` |
| **HR-07** Validate | §4 |
| **HR-09** | `ROW_WRITE_FAILED` không lộ SQL |
| Bản quyền (`AGENTS.md` §5) | Không lưu file gốc; test dùng file tự tạo trong `src/test/resources/import/` (nội dung bịa, ≤ 20 dòng) |

**Thành phần:**

| Lớp | Trách nhiệm |
|---|---|
| `learning.content.entity.*` | `Lexeme`, `Topic`, `TopicItem`, `PronStage` (+ `ServiceRequest` ở `learning.common`) |
| `learning.content.importer.ImportTemplate` | Định nghĩa cột 7 mẫu (enum `ImportDataType` + record `ColumnSpec`) |
| `…importer.CsvReader` | Giải mã + đọc CSV + kiểm header (FR-002…FR-007) |
| `…importer.RowValidator` (7 lớp, 1/mẫu) | Kiểm + chuẩn hoá một dòng → `ValidatedRow` hoặc lỗi |
| `…importer.RowWriter` (7 lớp) | Upsert một dòng, trả `CREATE/UPDATE/UNCHANGED` |
| `…importer.ImportService` | Điều phối upload, confirm, hết hạn, khôi phục |
| `…importer.ImportQueryService` | Lịch sử, chi tiết, lỗi, CSV lỗi |
| `learning.content.text.TextNormalizer`, `PinyinUtils` | FR-030…FR-033 |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Kiểm tra 5 000 dòng | < 10 s p95 |
| `NFR-P02` | Xác nhận 5 000 dòng | < 30 s p95 (truy vấn khoá theo lô 500 khi tính hành động FR-013) |
| `NFR-P03` | `GET errors` trang 20 | < 300 ms |
| `NFR-S01` | Chỉ `CONTENT_EDITOR` | FR-001 |
| `NFR-S02` | Log không chứa nội dung dòng | FR-025 |
| `NFR-R01` | Một dòng hỏng không làm hỏng dòng khác | FR-018, FR-019 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đụng `knowledge_points`, `question_knowledge_points` | Không |
| Seed dữ liệu | Không — chỉ nhập qua giao diện |
| JSONB bị truy vấn điều kiện | Không |
| Mọi FR có test | Có — §14 |

---

## 9 · Quyết định đã chốt và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Định dạng CSV UTF-8, mẫu v0 §4 | **Chốt tạm** — xác nhận lại khi có file Học Bá (addendum nếu khác cấu trúc) |
| Q2 | Thêm thư viện `org.apache.commons:commons-csv:1.11.0` | **Chốt** (leader 2026-10-29) |
| Q3 | Migration `V3__content_import.sql` (§0.3) | **Chốt** (leader 2026-10-29) — chỉ test bằng Testcontainers; leader chạy lên Neon sau khi PR merge |
| Q4 | `ADMIN` không được nhập dữ liệu | Chốt theo BR-110-2 |
| Q5 | Không nhập câu hỏi/đề thi đợt này | Chốt (D-01) |
| Q6 | Giới hạn 2 MB / 5 000 dòng / hết hạn 30 phút | Chốt — đổi bằng cấu hình `cnhsk.import.*` |

---

## 10 · Frontend Implementation Note

Khu vực `CONTENT_EDITOR` của `cnhsk-web` (`RequireRole roles={['CONTENT_EDITOR']}`). Field, nhãn, trạng thái lấy từ `screen-fields.md` (§12).
Upload dùng `FormData`; client không đặt `Content-Type` tay. Tải CSV lỗi/mẫu: `fetch` → `blob` → `URL.createObjectURL` → thẻ `<a download>`.

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Xác nhận đồng bộ chiếm một luồng tới 30 s | 5 000 dòng, ít người dùng; job nền là YAGNI |
| `payload` chứa tới ~3 MB JSON trong 30 phút | Một dòng `service_requests`; xoá khi kết thúc |
| Ghi đè nội dung đang được người học dùng | Nhập là thao tác có chủ đích, có xem trước; không có xoá |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field chi tiết |
|---|---|---|---|---|
| SCR-068 Learning Data Import | `/content/imports` | `CONTENT_EDITOR` | templates, upload, confirm, history | `screen-fields.md` dòng 33366 |
| SCR-069 Import Report | `/content/imports/:id` | `CONTENT_EDITOR` | detail, errors, errors.csv | dòng 33935 |

**Chốt các open issue của SCR-068/069:**

| Open issue | Chốt |
|---|---|
| UC-110 ghi ngay sau upload vs SCR-068 yêu cầu xem trước + xác nhận | Hai bước (FR-012, FR-015) — theo SCR-068 |
| Mẫu/khoá/đuôi/encoding/kích thước chưa duyệt | §4 mẫu v0, CSV UTF-8, 2 MB, 5 000 dòng |
| Lựa chọn new/update/replace của SF27 | Chỉ upsert; không có replace/xoá |
| Lưu lỗi bền vững UC-111 | `service_requests.error_rows` + phân trang ở ứng dụng + xuất CSV |

Trạng thái bắt buộc: Loading (đang kiểm tra / đang ghi — khoá nút), Empty (FR-040), Error (thông báo từ `message`; lỗi cấp file hiện ngay dưới ô chọn file).

---

## 13 · API Contract

[`contracts/openapi-import.yaml`](contracts/openapi-import.yaml)

| Method · Path | Request | Thành công | Lỗi chính |
|---|---|---|---|
| `GET /api/admin/imports/templates` | — | 200 `[Template]` | 403 |
| `GET /api/admin/imports/templates/{dataType}.csv` | — | 200 CSV | 403, 422 |
| `POST /api/admin/imports` | multipart `dataType`, `file` | 201 `ImportPreview` | 403, 413, 422 |
| `POST /api/admin/imports/{id}/confirm` | `{acknowledgeRerun}` | 200 `ImportRun` | 404, 409, 410, 422 |
| `GET /api/admin/imports` | `page`, `size` | 200 `ImportRunPage` | 403, 422 |
| `GET /api/admin/imports/{id}` | — | 200 `ImportRun` | 404 |
| `GET /api/admin/imports/{id}/errors` | `page`, `size` | 200 `ImportErrorPage` | 404, 422 |
| `GET /api/admin/imports/{id}/errors.csv` | — | 200 CSV | 404 |

---

## 14 · Acceptance Tests

Test BE dùng Testcontainers (spec 001 §0.2). File CSV test tự tạo ở `src/test/resources/import/`.

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | G `LEARNER`, `ADMIN` · W upload · T 403 cả hai |
| AT-02 | FR-003 | W file 2 MB + 1 byte · T 413 `FILE_TOO_LARGE`, có `runId`, lần nhập `FAILED` |
| AT-03 | FR-004, US-3 | W file Windows-1258 chứa chữ Hán · T 422 `ENCODING_ERROR`, không có `PENDING` |
| AT-04 | FR-004 | W file UTF-8 có BOM · T đọc đúng, header khớp |
| AT-05 | FR-005 | W ô có dấu phẩy và xuống dòng trong ngoặc kép · T giá trị giữ nguyên |
| AT-06 | FR-006 | W thiếu `pinyin`, thừa `note` · T 422 `TEMPLATE_MISMATCH`, `details` có 2 mục |
| AT-07 | FR-006 | W đảo thứ tự cột · T hợp lệ |
| AT-08 | FR-007 | W chỉ có header; W 5 001 dòng · T `EMPTY_FILE`; `TOO_MANY_ROWS` |
| AT-09 | FR-009, §4.1 | W `CHAR` text `你好` · T lỗi `INVALID_VALUE` cột `text` |
| AT-10 | FR-009, §4.1 | W `WORD` có `stroke_count` · T `INVALID_VALUE` |
| AT-11 | §4.0 | W `meaning_vi` trống + `hsk_level=10` cùng dòng · T 2 lỗi cùng `line` |
| AT-12 | BR-110-10 | W `text` = `??` · T `BROKEN_TEXT` |
| AT-13 | FR-010, E3 | W 2 dòng `WORD,我们` · T dòng 2 hợp lệ, dòng 3 `DUPLICATE_IN_FILE` |
| AT-14 | FR-011, E4 | W `TOPIC_ITEM` từ chưa có · T `REFERENCE_NOT_FOUND` |
| AT-15 | FR-012 | W upload hợp lệ · T `service_requests` `PENDING`, `file_hash` 64 hex; bảng `lexemes` **không đổi** |
| AT-16 | FR-013 | G `我` đã có với cùng dữ liệu, `你` khác `meaning_vi`, `他` chưa có · T `UNCHANGED`, `UPDATE`, `CREATE` |
| AT-17 | FR-014 | W 30 dòng hợp lệ, 60 lỗi · T `preview` 20, `errors` 50, `errorCount` 60 |
| AT-18 | FR-014, E8 | G file đã `COMPLETED` · W upload lại · T `alreadyImported=true` |
| AT-19 | FR-015, E2 | G `PENDING` quá 30 phút (đồng hồ giả) · W confirm · T 410, trạng thái `EXPIRED` |
| AT-20 | FR-015 | W confirm lần 2 sau khi `COMPLETED` · T 409 `IMPORT_NOT_PENDING` |
| AT-21 | FR-016 | G `alreadyImported` · W confirm không tick · T 409; có tick · T 200 |
| AT-22 | FR-017, E1 | G một lần nhập `RUNNING` · W confirm lần khác · T 409 `CONCURRENT_IMPORT`, lần sau vẫn `PENDING` |
| AT-23 | FR-018, FR-019, E5 | G giả lập lỗi ghi ở dòng 5 (`RowWriter` spy ném lỗi) · W confirm 10 dòng · T 9 dòng vào DB, 1 lỗi `ROW_WRITE_FAILED`, `result.failed=1` |
| AT-24 | FR-020, E6 | G `我` có `audio_url` · W cập nhật với ô `audio_url` trống · T `audio_url` giữ nguyên |
| AT-25 | FR-021, SC-002 | W confirm cùng file 2 lần (lần 2 có tick) · T số dòng `lexemes` không đổi; lần 2 `unchanged = valid` |
| AT-26 | FR-021 | T `COMPLETED` có `result`, `payload` không còn `validRows`, `audit_logs IMPORT_CONFIRM` |
| AT-27 | FR-022 | G `RUNNING` cũ 11 phút · W khởi động lại ứng dụng · T `FAILED`, `error_message=INTERRUPTED` |
| AT-28 | FR-024 | G `PENDING` hết hạn · W job chạy · T `EXPIRED`, `validRows` bị xoá |
| AT-29 | FR-025 | W upload/confirm · T log không chứa nội dung ô nào (kiểm bằng appender giả) |
| AT-30 | FR-026 | T `createdBy.displayName` đúng; không truy vấn bảng `auth.users` từ module `learning` (ArchUnit) |
| AT-31 | FR-027 | G 45 lỗi · W `page=2&size=20` · T 5 lỗi, sắp theo `line`; `size=101` → 422 |
| AT-32 | FR-028 | W tải CSV lỗi · T bắt đầu bằng BOM, 46 dòng (kể header) |
| AT-33 | FR-029 | G 12 lần nhập · W `page=1&size=10` · T 2 dòng, mới nhất trước |
| AT-34 | FR-031 | `nǚ'ér`→`nver`, `Nǐ hǎo`→`nihao`, `lǜ`→`lv` |
| AT-35 | FR-032 | `Đi học`→`di hoc` |
| AT-36 | FR-033 | ≥ 20 ca `toNumbered` |
| AT-37 | §4.2–4.3 | Nhập `TOPIC` rồi `TOPIC_ITEM` · T `topic_items` có dòng chỉ `lexeme_id` (xác nhận sửa L1) |
| AT-38 | §4.4–4.5 | Nhập `GRAMMAR` rồi `GRAMMAR_EXERCISE` 3 bài · T `extra.exercises` 3 phần tử theo `no`; nhập lại `GRAMMAR` · T `exercises` giữ nguyên |
| AT-39 | §4.5 | W `answer=D` khi `option_d` trống; 2 phương án trùng · T `INVALID_ANSWER` |
| AT-40 | §4.6–4.7 | Nhập `PRON_STAGE` 8 tầng, `PRON_UNIT` 10 đơn vị tầng 1 · T `units` 10 phần tử; nhập lại 1 đơn vị sửa đáp án · T vẫn 10, đáp án mới |
| AT-41 | FR-034, FR-035 | T 7 mẫu; mẫu CSV có header khớp `columns` |
| AT-42 | FR-036…FR-040 (FE) | Vitest: chặn file > 2 MB phía client; nút xác nhận khoá khi `valid=0`; bước xác nhận trong trang; cảnh báo nhập lại cần tick |

---

## 15 · Gói việc cho Codex

[`tasks.md`](tasks.md)

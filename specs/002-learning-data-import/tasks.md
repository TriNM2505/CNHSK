# Tasks: Nhập dữ liệu học — Spec #002

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-import.yaml`](contracts/openapi-import.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · 🔴 chặn

**Tổng: 14 task**, mỗi task ≤ 4 giờ, một task = một PR `agent/002-<task>`. Quy ước chung như `specs/001-auth-rbac/tasks.md` mục "Quy ước cho mọi task".
`…/` = `src/main/java/com/cnhsk/`, `t/` = `src/test/java/com/cnhsk/`.

**Điều kiện bắt đầu:** spec 001 xong T-01 (migration V1/V2), T-03 (lỗi), T-05 (`AuditService`), T-10 (bảo mật), T-27 (`AuthApi`). Q2, Q3 ở §9 đã duyệt.

---

## Đợt 0 · DB + entity

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | `V3__content_import.sql` đúng 5 thay đổi §0.3 (thay đổi #2 `topic_items` đã có sẵn ở V1); test Flyway chạy sạch trên Testcontainers | `src/main/resources/db/migration/V3__content_import.sql` | §0.3 · AT-37 | ✅ 2026-10-29: migration + 10 test Flyway trên PostgreSQL/Testcontainers; `verify` xanh (260 test, không skip), line coverage 93,08%. Contract đã duyệt qua PR #13 (`199c4a8`); nhánh bàn giao `agent/002-t01` |
| T-02 | Entity + repository `Lexeme` (`extra` map bằng `@JdbcTypeCode(SqlTypes.JSON)` sang record `LexemeExtra`), `Topic`, `TopicItem`, `PronStage` (`units` → `List<PronUnit>`), `ServiceRequest`; truy vấn khoá theo lô (`findByKindAndTextIn`, `findByCodeIn`…) | `…/learning/content/entity/*`, `…/learning/content/repository/*`, `…/learning/common/*` | §4 | ✅ 2026-10-29: 5 entity + repository, JSONB/record và mảng prerequisite; 12 test repository pass trên PostgreSQL/Testcontainers, `verify` xanh (272 test, không skip), line coverage 92,73%. Cùng nhánh `agent/002-t01` theo chỉ đạo leader; PR #14 giữ Draft |
| T-03 | `TextNormalizer` (NFC, khoảng trắng, `meaning_norm`) + `PinyinUtils` (`normalize`, `toNumbered`, kiểm ký tự hợp lệ) + `CjkUtils` (`isCjk`, `isBroken`) | `…/learning/content/text/*` | FR-030…FR-033 · AT-34…AT-36 | ✅ 2026-10-29: 156 unit test pass (45 ca `toNumbered`); `verify` xanh (428 test, không skip), line coverage 93,08%. Q-T03-01/02 đã duyệt và ghi tại `questions.md`; bàn giao cùng T-01…T-03 trên nhánh `agent/002-t01` |

## Đợt 1 · Đọc file và kiểm dòng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-04 | Thêm `commons-csv` 1.11.0; `ImportDataType`, `ColumnSpec`, `ImportTemplate` (7 mẫu §4, gồm `label`, `naturalKey`, `dependsOn`, `example`) | `pom.xml`, `…/learning/content/importer/template/*` | §4 · FR-034 | ✅ 2026-10-30: 7 mẫu và metadata dùng chung, ví dụ tự soạn khớp mẫu cha; 24 test FR034 pass, đối chiếu OpenAPI/§4; `verify` xanh (452 test, không skip), line coverage 93,31% (package template 100%). Nhánh `spec/002-learning-data-import-part2` |
| T-05 | `CsvReader`: giải mã UTF-8 nghiêm ngặt, bỏ BOM, RFC 4180, kiểm header, đếm dòng, bỏ dòng trống; lỗi cấp file thành `ImportFileException(ErrorCode)` | `…/importer/CsvReader.java` | FR-002…FR-007 · AT-03…AT-08 | ✅ 2026-10-30: UTF-8/BOM, RFC 4180, header thiếu/lạ/trùng, giới hạn file/dòng; thiếu/thừa ô thành lỗi riêng dòng theo Q-T05-02; 51 test FR002…FR007 pass; `verify` xanh (503 test, không skip), line coverage 93,53% (CsvReader 100%). Quyết định leader tại `questions.md`; handler `details` xử lý ở T-08 |
| T-06 | `RowValidator` cho `LEXEME`, `TOPIC`, `TOPIC_ITEM` + kiểm trùng trong file + kiểm tham chiếu theo lô | `…/importer/validate/*` | FR-009…FR-011, §4.1–4.3 · AT-09…AT-14 | ✅ 2026-10-30: 3 validator, chuẩn hóa T-03, giữ lỗi T-05; chỉ dòng hợp lệ đầu giữ khóa, tags bỏ rỗng theo Q-T06-01/02; tham chiếu theo lô, không ghi DB; 106 test mới (100 unit + 6 PostgreSQL/Testcontainers), `verify` xanh (609 test, không skip), line coverage 94,05% (package validate 100%) |
| T-07 | `RowValidator` cho `GRAMMAR`, `GRAMMAR_EXERCISE`, `PRON_STAGE`, `PRON_UNIT` | `…/importer/validate/*` | §4.4–4.7 · AT-39 | ✅ 2026-10-30: đủ 7 validator; kiểm đáp án, tham chiếu theo lô, BigDecimal cho ngưỡng đạt, chuẩn hóa khóa số, giới hạn 100 đơn vị/tầng không tính cập nhật/trùng; target_sounds bỏ rỗng theo Q-T07-01; 105 test mới (100 unit + 5 PostgreSQL/Testcontainers), `verify` xanh (714 test, không skip), line coverage 94,36% (package validate 100%); không sửa ContentImportMigrationIntegrationTest |

## Đợt 2 · Luồng nhập

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-08 | `ImportService.upload` + `ImportController` POST: tạo `PENDING`/`FAILED`, tính hành động FR-013, trả `ImportPreview`; multipart 2 MB → 413 | `…/importer/ImportService.java`, `…/importer/web/ImportController.java` | FR-001, FR-008, FR-012…FR-014 · AT-01, AT-02, AT-15…AT-18 | ✅ 2026-10-30: upload/preview đủ 7 mẫu, SHA-256, TTL 30 phút, đối chiếu theo lô 500, không ghi nội dung; lỗi cấp file lưu FAILED + runId, giữ details TEMPLATE_MISMATCH; HTTP thật 2 MB + 1 byte → 413 và metadata null theo Q-T08-01. Contract/spec và FE SCR-068/069 nullable, test + preview cùng PR; phần dùng chung chỉ thêm dòng trong phạm vi đã duyệt. 56 test BE mới, verify xanh (770 test, không skip), line coverage 94,61% (2983/3153); FE lint 0 lỗi (1 warning có sẵn ở LevelBadge), typecheck và 442 test xanh. Không sửa DB/migration/ContentImportMigrationIntegrationTest |
| T-09 | `RowWriter` 7 mẫu: upsert, giữ giá trị cũ khi ô trống, tính `CREATE/UPDATE/UNCHANGED` | `…/importer/write/*` | FR-020, §4 · AT-24, AT-37, AT-38, AT-40 | ✅ 2026-10-30: đủ 7 writer, upsert theo khóa tự nhiên và trạng thái DB lúc ghi; ô trống/cột tùy chọn thiếu giữ cũ, mặc định chỉ khi tạo; UNCHANGED không save. Grammar giữ exercises, stage giữ units; exercises sắp theo no, unit mới nối cuối/cập nhật giữ vị trí, kiểm lại giới hạn 100 units. Writer bắt buộc transaction caller, không commit riêng. 69 test mới (43 unit + 26 PostgreSQL/Testcontainers), verify xanh (839 test, không skip), line coverage 94,99% (3183/3351), package write 100% (198/198). FE lint 0 lỗi (1 warning có sẵn ở LevelBadge), typecheck và 442 test xanh. Không đổi contract/FE/dependency/schema/migration/ContentImportMigrationIntegrationTest; không có câu hỏi mở |
| T-10 | `ImportService.confirm`: kiểm trạng thái/hạn/nhập lại, `PENDING→RUNNING` (bắt vi phạm unique → 409), ghi từng dòng `REQUIRES_NEW`, `COMPLETED` + audit + xoá `validRows` | `ImportService.java`, controller | FR-015…FR-021, FR-023 · AT-19…AT-23, AT-25, AT-26 | ✅ 2026-10-30: POST confirm đủ 7 mẫu, khóa lần nhập bằng JPA; hết hạn lưu EXPIRED/dọn validRows trước 410; kiểm nhập lại tại lúc confirm; chỉ uq_import_running thành CONCURRENT_IMPORT, giữ PENDING. Mỗi dòng REQUIRES_NEW theo lineNo, lỗi rollback riêng/thêm ROW_WRITE_FAILED chung; COMPLETED + result + audit cùng transaction cuối, payload chỉ giữ metadata; creator qua AuthApi. Thêm 6 ErrorCode đã duyệt và đồng bộ đúng 6 dòng kỳ vọng trong GlobalExceptionHandlerTest, giữ containsExactly. 73 test mới (39 unit + 34 PostgreSQL/Testcontainers); verify xanh (912 test, 0 skip), line coverage 95,35% (3302/3463); 5 lượt nhập mới 5000 dòng, samples ms [17229,11667,11367,11404,11325], p95 cục bộ 17,229 s < 30 s. FE lint 0 lỗi (1 warning có sẵn ở LevelBadge), typecheck và 442 test xanh. Không đổi contract/dependency/schema/migration/ContentImportMigrationIntegrationTest; không có câu hỏi nghiệp vụ mở |
| T-11 | `ImportRecoveryRunner` + job hết hạn 15 phút (`zone="Asia/Ho_Chi_Minh"`) + hết hạn lazy; kiểm log không lộ nội dung | `…/importer/ImportMaintenance.java` | FR-022, FR-024, FR-025 · AT-27…AT-29 | ⬜ |
| T-12 | `ImportQueryService` + GET lịch sử, chi tiết, lỗi phân trang, `errors.csv`, templates, template CSV; `AuthApi.displayNames` | `…/importer/ImportQueryService.java`, controller | FR-026…FR-029, FR-034, FR-035 · AT-30…AT-33, AT-41 | ⬜ |

## Đợt 3 · Frontend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-13 | SCR-068: chọn mẫu + hướng dẫn cột + tải mẫu, upload, kết quả kiểm tra, cảnh báo nhập lại, xác nhận trong trang, lịch sử | `cnhsk-web/src/features/content-import/**` | FR-036…FR-038, FR-040 · AT-42 | ⬜ |
| T-14 | SCR-069: tóm tắt, bảng lỗi phân trang, tải CSV lỗi, "Sửa file và nhập lại" | `cnhsk-web/src/features/content-import/**` | FR-039 | ⬜ |

---

**Thứ tự:** T-01 → T-02 → T-03 → T-04 → T-05 → T-06 → T-07 → T-08 → T-09 → T-10 → T-11 → T-12 → (T-13, T-14).
**Rủi ro cao:** T-05 (encoding), T-10 (transaction từng dòng — dùng `TransactionTemplate`, **không** `@Transactional` trên vòng lặp).

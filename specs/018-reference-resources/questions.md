# Câu hỏi và quyết định — Spec #018

## 2026-11-13 · Brainstorm với leader

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | 1 bảng gộp hay 2 bảng `media_channels` + `books` (feature-tree) | 1 bảng `reference_items` có cột `kind` | ✅ Chốt |
| Q2 | Danh sách domain cho phép đặt ở đâu | Cố định trong `application.yaml` theo loại | ✅ Chốt |
| Q3 | Lưu báo link chết | Chỉ ghi `audit_logs`; SCR-072 đếm qua `ix_audit_target` | ✅ Chốt |
| Q4 | Ai được báo lỗi | Chỉ `LEARNER` (chống spam) | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q5 | Danh sách domain ban đầu (spec §0.2) | Leader rà | ✅ Leader duyệt cùng spec 2026-11-13 |
| Q6 | Hiện lại mục đã ẩn | Về `DRAFT`, phải công bố lại | ✅ Leader duyệt cùng spec 2026-11-13 |

## Dữ kiện đã kiểm trên main `2735ac8`
- Chưa có bảng tài nguyên tham khảo trong migration; `feature-tree` ghi `media_channels`, `books` (chưa tạo).
- `shared.audit_logs(actor_id, action, target_type, target_id, detail JSONB, ip_address, created_at)`; index `ix_audit_target(target_type, target_id)`, `ix_audit_action`.
- `AuditService` có `record`, `recordDetail` — chưa có hàm đếm.
- SCR-072 actor `CONTENT_EDITOR` (screen-fields dòng 85, 515).

## 2026-11-13 — T-01 G0: ANSWERED

Base: main `345148c`; nhánh `spec/018-t01`. Leader cấp V7 cho reference_items; V8 dành app_settings, không tạo trong task này.

| Mã | Trạng thái | Quyết định của leader |
|---|---|---|
| Q-T01-01 | ANSWERED | V7 chỉ chứa DDL bảng, CHECK và hai index. Không GRANT, REVOKE, DO-block hay tạo role trong Flyway; giữ mô hình quyền vận hành ngoài migration như V1–V6. |
| Q-T01-02 | ANSWERED | Bỏ hai GRANT khỏi SQL §0.1. Leader chạy REVOKE DELETE ON learning.reference_items FROM svc_app sau khi V7 merge và được áp dụng trên Neon, theo database.md §7.2, vì default privileges đã cấp DELETE cho bảng mới. Bỏ test quyền svc_app. Repository extend Repository, chỉ mở save/findById; có test reflection bảo đảm không có delete/remove. |
| Q-T01-03 | ANSWERED | Cùng PR cập nhật database.md §3 (bảng mới), §11 (V7), §7.2 (REVOKE riêng). Không sửa AC-04/Hiến pháp. Allowlist 8 file gồm spec.md chỉ sửa §0.1. |

Phạm vi nghiệm thu DB: AT-09/FR-018 (trường sách chỉ cho BOOK), DDL §0.1, URL/thumbnail HTTPS, HSK 1–9 và min/max, năm, NOT NULL/default/FK/index, PUBLISHED có published_at; round-trip JPA và nâng cấp V6 → V7 giữ dữ liệu. Luật kiểm domain và author bắt buộc lúc công bố thuộc T-02/T-05, không thêm CHECK ngoài SQL đã duyệt.

Không có endpoint mới, không sửa ErrorCode, SecurityConfig, AuditService, application.yaml hoặc contract. Mọi migration/test chạy PostgreSQL Testcontainers local; leader giữ việc áp V7 và gỡ DELETE trên Neon.
### Q-T01-04 — ANSWERED: cập nhật inventory test của spec 010

Leader duyệt thêm file thứ 9: src/test/java/com/cnhsk/learning/knowledge/KnowledgeTreeMigrationIntegrationTest.java. Chỉ đổi isEqualTo(22L) thành isEqualTo(23L) trong AT30_migrationsRunInOrderWithoutAddingBusinessTables, giữ tên test, containsSubsequence và mọi assertion khác. V7 thêm đúng một bảng learning.reference_items nên inventory learning tăng từ 22 lên 23.

Full verify lần 1: 4.309 test, 2 failure, 0 error/skip. Một lỗi là assertion inventory này (expected 22, actual 23). Lỗi còn lại thuộc whitelist môi trường: QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries, p95 30.022 ms, yêu cầu < 30.000 ms. Không sửa benchmark/ngưỡng. Chạy lại targeted + ModuleBoundaryTest + full verify sau thay đổi đã duyệt; kết quả cuối ghi bên dưới.

### Kết quả gate cuối — 2026-11-13

| Bước | Kết quả thực tế |
|---|---|
| Targeted lần đầu — ReferenceItemMigrationIntegrationTest | 62 test, 0 failure/error/skip |
| ModuleBoundaryTest lần đầu | 9/9 PASS |
| Targeted sau Q-T01-04 — ReferenceItemMigrationIntegrationTest + KnowledgeTreeMigrationIntegrationTest | 96 test (62 + 34), 0 failure/error/skip |
| ModuleBoundaryTest chạy lại | 9/9 PASS |
| Full mvnw.cmd -B verify lần hai, Java 17, DEBUG=false, Docker | 4.309 test, 0 failure/error/skip; BUILD SUCCESS; 19:48 phút |
| NFR_P04 lần hai | p95 23.733 ms, đạt yêu cầu < 30.000 ms; giữ nguyên test và ngưỡng |
| JaCoCo LINE toàn dự án | 96,7077% = 12.014/12.423 dòng; mọi coverage check đạt |
| JaCoCo LINE learning.reference | 96,7213% = 59/61 dòng |
| git diff --check / UTF-8 không BOM | PASS; 0 BOM |

Lần hai không còn failure nào, không cần dùng ngoại lệ môi trường. Lần đầu FAILED vẫn được giữ trong ghi nhận Q-T01-04: lỗi inventory không thuộc whitelist và đã sửa đúng một assertion sau khi leader duyệt; lỗi NFR_P04 thuộc whitelist, lần chạy lại đạt. Không sửa hay hạ ngưỡng benchmark.

Fetch trước bàn giao: origin/main đã lên 7054115 (PR #147 của spec 013/T-09). Không có thay đổi từ 345148c đến main mới đụng các file của T-01; giữ base đã duyệt, không merge/rebase main.

### Self-check Constitution và phạm vi

- AC-01: contract không đổi. spec.md chỉ sửa §0.1 theo quyết định leader; migration khớp SQL đã chốt.
- AC-03: createdBy là Long, không liên kết entity auth hoặc truy vấn auth trong code chính; FK DB giữ đúng spec.
- AC-05/AC-06: chỉ thêm V7 DDL, không sửa V1–V6; enum lưu VARCHAR + CHECK; không tạo role/GRANT/REVOKE trong Flyway.
- BR-119-7: repository extend Repository, chỉ save/findById, test reflection chặn hàm xoá. Quyền DB là việc vận hành của leader: REVOKE DELETE riêng bảng sau merge/migrate.
- HR-02/HR-08: dữ liệu test tự soạn, mật khẩu fixture chỉ lưu bcrypt hash, không secret/email thật hoặc nội dung có bản quyền.
- ES-05: full verify xanh, coverage vượt 80%. Không skip/tắt test.
- Đúng 9 file đã duyệt; file test spec 010 chỉ đổi 22L thành 23L, không đổi tên hoặc assertion khác. Không sửa AuditService, ErrorCode, SecurityConfig, application.yaml, .env, dependency, AC-04/Hiến pháp hoặc code FE.
- Chỉ kiểm trên PostgreSQL Testcontainers local, không chạy SQL/migration trên Neon. Leader áp V7 và gỡ quyền DELETE sau khi merge.

## T-02 — Chốt leader ngày 2026-11-13

- Q-T02-01 — ANSWERED: ReferenceUrlPolicy trả Optional<Violation>(field, reason). field là url hoặc thumbnailUrl; reason gồm MALFORMED_URL, NON_HTTPS_LINK, DOMAIN_NOT_ALLOWED. T-05 ánh xạ sang ErrorCode/422; T-02 không sửa ErrorCode.
- Q-T02-02 — ANSWERED: chỉ nhận tiền tố https:// chữ thường, khớp CHECK V7; HTTPS:// trả NON_HTTPS_LINK. Không chuẩn hoá dữ liệu. Host không phân biệt hoa/thường, bỏ www. đầu host, so bằng hoặc hậu tố có ranh giới dấu chấm. Không bỏ dấu chấm cuối host.
- Q-T02-03 — ANSWERED: thumbnail null là không có; chuỗi rỗng/trắng và URL có khoảng trắng đầu/cuối trả MALFORMED_URL. Không trim.
- Thứ tự kiểm: parse → scheme → userinfo/host rỗng → domain. user@ và user:pass@ đều trả MALFORMED_URL. Không gửi HTTP/HEAD hay ghi URL vào log.
- Cấu hình từ application.yaml đúng §0.2; thiếu kind, danh sách rỗng hoặc domain chứa scheme/đường dẫn làm khởi động thất bại. Không thêm dependency, endpoint, migration hay sửa entity/repository.

### T-02 — Gate và self-check

Base 7d66a11 (PR #148); nhánh spec/018-t02. T-01 ghi MERGED trong tasks.md và dòng V7 trong database.md ghi đã merge 13/11; leader chạy Neon. Không sửa entity/repository nên không thay dòng trống cuối hai file này.

| Gate | Kết quả thực tế |
|---|---|
| ReferenceUrlPolicyTest + ReferencePropertiesTest | 44 test, 0 failure/error/skip; BUILD SUCCESS |
| ModuleBoundaryTest | 9 test, 0 failure/error/skip; BUILD SUCCESS |
| Full mvnw.cmd -B verify, Java 17, DEBUG=false, Docker local | 4.412 test, 0 failure/error/skip; BUILD SUCCESS; 19:27 phút |
| JaCoCo LINE toàn dự án | 96,7390% (12.133/12.542 dòng); mọi coverage check đạt |
| JaCoCo LINE learning.reference | 98,1132% (104/106 dòng) |
| git diff --check / BOM | PASS / 0 BOM |

Không dùng ngoại lệ môi trường. Full suite tăng đúng 44 test so với 4.368 test của base leader đã xác nhận.

Self-check: đúng 8 file đã duyệt; application.yaml chỉ thêm cnhsk.reference, danh sách đúng §0.2. Không dependency mới; không endpoint, SecurityConfig, ErrorCode, migration, dữ liệu thật hay secret. Policy không gọi mạng, không log URL và không chuẩn hoá dữ liệu đầu vào. Host có dấu chấm cuối không được cho qua; userinfo/host rỗng trả MALFORMED_URL. Test chứng minh cấu hình sai làm khởi động thất bại. Không sửa hay skip test cũ. Không chạy SQL/migration trên Neon; quyền V7 do leader xử lý.

## T-03 — Chốt leader ngày 2026-11-13

- Q-T03-01 — ANSWERED: hskMin null không giới hạn dưới, hskMax null không giới hạn trên; cả hai null luôn hiện. Kiểm [null,3] và [4,null] với hsk=2 và hsk=5.
- Q-T03-02 — ANSWERED: ID không phải số, <=0 hoặc vượt int64 trả 404 REFERENCE_NOT_FOUND, giống không tồn tại/DRAFT/HIDDEN. Không sửa contract.
- Q-T03-03 — ANSWERED: Redis lỗi cho qua, WARN cố định, không IP/input/chi tiết kết nối, có test.
- Q-T03-04 — ANSWERED: phút lịch theo Clock, một bộ đếm chung cho hai GET theo IP khách; request sai tham số vẫn tính; người đã đăng nhập không tính. retryAfterSeconds tới phút kế tiếp, tối thiểu 1. Ngưỡng 120 là hằng số; key reference:public:guest:<ip>:<yyyyMMddHHmm>, tách dictionary. IP lấy từ remoteAddr, không tin header tự gửi. Không sửa application.yaml.
- Matcher chỉ GET /api/public/references và /api/public/references/{id}, đặt cạnh nhóm public hiện có. POST và đường dẫn con khác không public. Tag khớp chính xác bằng :tag = ANY(tags), có tham số hoá, không LIKE.
- Base de73286, nhánh spec/018-t03. Query chỉ đọc, danh sách hai truy vấn cố định (count + page), chi tiết một truy vấn; luôn lọc PUBLISHED trong SQL. DTO không trả entity hoặc dữ liệu quản trị. REFERENCE_NOT_FOUND thêm ngay trước INTERNAL_ERROR và cập nhật test thứ tự enum.

### T-03 — Gate và bằng chứng

| Gate | Kết quả thực tế |
|---|---|
| ReferenceQueryServiceTest + ReferenceQueryIntegrationTest + PublicReferenceControllerIntegrationTest | 39 test, 0 failure/error/skip; BUILD SUCCESS |
| GlobalExceptionHandlerTest + ModuleBoundaryTest | 28 test (19 + 9), 0 failure/error/skip; BUILD SUCCESS |
| Full mvnw.cmd -B verify, Java 17, DEBUG=false, Docker local | 4.451 test, 0 failure/error/skip; BUILD SUCCESS; 31:32 phút |
| JaCoCo LINE toàn dự án | 96,7601% (12.215/12.624 dòng); mọi coverage check đạt |
| JaCoCo LINE learning.reference / reference.web | 98,7097% (153/155) / 100% (30/30) |
| Danh sách 500 mục, p95 (10 warm-up + 40 mẫu) | Targeted 7,5036 ms; full suite 1,5531 ms; yêu cầu <150 ms |
| git diff --check / UTF-8 BOM | PASS / 0 BOM |

Không dùng ngoại lệ môi trường. Full suite tăng đúng 39 test so với 4.412 test của main de73286.

PostgreSQL xác nhận: [null,3] có ở hsk=2 và không có ở hsk=5; [4,null] ngược lại; cả hai null luôn hiện. Tag so khớp chính xác, phân biệt hoa/thường, ký tự %/_ không thành wildcard; chuỗi giống SQL không làm thay đổi điều kiện. Sort sort_order/id ổn định, trang vượt phạm vi rỗng nhưng total vẫn đúng, offset lớn không tràn int. Danh sách giữ đúng hai lời gọi truy vấn khi tăng số mục; không N+1.

HTTP xác nhận: GUEST và đủ sáu role đọc được; chỉ PUBLISHED. DRAFT/HIDDEN/ID thiếu hoặc sai đều 404 REFERENCE_NOT_FOUND. JSON common giữ trường null bắt buộc; trường sách chỉ xuất hiện với BOOK. Danh sách và chi tiết không có createdBy/status/reportCount/lastReportedAt hoặc thời gian quản trị. Mô tả chứa markup vẫn là chuỗi dữ liệu. POST và đường dẫn con ngoài hai GET không được mở public.

Redis thật xác nhận: hai GET và tham số sai dùng chung hạn mức khách; lượt 120 qua, 121 trả 429 với retryAfterSeconds; IP khác độc lập, sang phút mới có bộ đếm mới. Tám request tranh suất cuối chỉ một request qua; TTL không bị gia hạn. Người đã xác thực không gọi bộ đếm; Redis lỗi cho qua với đúng một WARN cố định, không có exception upstream hoặc IP. Không tin header client để xác định IP.

Fetch trước bàn giao vẫn de73286; ErrorCode/SecurityConfig không có thay đổi trên main cần rebase. T-02 cập nhật MERGED (#149, de73286).

Self-check: đúng 10 file đã duyệt; không dependency/contract/migration/application.yaml/RateLimiter/entity/repository hoặc FE thay đổi. ErrorCode thêm REFERENCE_NOT_FOUND ngay trước INTERNAL_ERROR, GlobalExceptionHandlerTest giữ containsExactly. SecurityConfig chỉ thêm matcher GET cho đúng danh sách và /{id}. SQL dùng tham số và chỉ đọc bảng cùng module; không trả entity. Fixture tự soạn, mật khẩu chỉ giữ hash bcrypt cost 12, không PII thật/secret/dữ liệu có bản quyền. Không sửa/tắt/skip test cũ. Không SQL/migration trên Neon; không push main, merge PR hoặc force-push.

## T-04 — Chốt leader ngày 2026-11-14

Base deb4f03, đầu nhánh PR #151; leader sẽ merge --no-ff. Nhánh spec/018-t04 không rebase/merge main. Chưa có thay đổi của #152; leader xử lý xung đột khi merge.

- Q-T04-01 — ANSWERED: countByTargets dùng created_at >= since[id]. Thiếu/null cutoff đếm toàn bộ. Trả đủ mọi ID, không có audit thì ReportStat(0,null); tập rỗng trả map rỗng, không query. ReportStat nằm trong shared.audit (record lồng AuditService), không phụ thuộc learning. Một truy vấn tổng hợp cả tập, theo target_type/target_id/action/created_at; không điều kiện JSONB.
- Q-T04-02 — ANSWERED: 422/403/404 không trừ lượt. Giữ chỗ Redis sau khi xác nhận PUBLISHED và trước ghi audit. Ghi DB lỗi KHÔNG hoàn lượt; người gọi phải chờ ngày tiếp theo. Đây là giới hạn đã được leader chấp nhận; không tự xoá bộ đếm khi có lỗi.
- Q-T04-03 — ANSWERED: Redis lỗi thì fail-closed, không ghi audit, trả 500 INTERNAL_ERROR với message cố định. WARN cố định, không userId/note/exception chi tiết. Không dùng cách cho qua của GET công khai.
- Q-T04-04 — ANSWERED: ID sai dạng/<=0/vượt int64 trả 404 REFERENCE_NOT_FOUND. Note thiếu/null lưu null; có thì giữ nguyên, không trim. Quá 300 code point trả 422.
- Khoá reference:report:<userId>:<id>:<yyyyMMdd theo Asia/Ho_Chi_Minh>, TTL tới hết ngày Việt Nam, retryAfterSeconds tối thiểu 1. Dùng RateLimiter có sẵn, không thêm bảng hay cơ chế bộ đếm mới.
- AuditService thêm NamedParameterJdbcTemplate vào constructor; chữ ký và hành vi record/recordDetail giữ nguyên. Matcher chỉ POST /api/references/{id}/report cho LEARNER, cạnh nhóm reference. Không thêm ErrorCode.

### Ghi sẵn cho T-05 — FR-011 / AT-14

Bộ lọc reported=true phải lọc trong SQL TRƯỚC phân trang: EXISTS trên shared.audit_logs với action REFERENCE_REPORTED, target_type REFERENCE, target_id của mục và since = updated_at. countByTargets chỉ lấy reportCount/lastReportedAt cho trang đã lọc; không tải trang rồi lọc trong Java vì sẽ sai total và mất mục. T-04 chỉ cung cấp helper và kiểm số query, chưa nghiệm thu HTTP reported=true của T-05.

### T-04 — Gate và self-check

| Gate | Kết quả thực tế |
|---|---|
| ReferenceReportServiceTest + ReferenceReportIntegrationTest + ReferenceReportControllerIntegrationTest + AuditTargetCountIntegrationTest + AuditServiceIntegrationTest cũ | 56 test (8 + 6 + 25 + 5 + 12), 0 failure/error/skip; BUILD SUCCESS |
| ModuleBoundaryTest | 9/9, 0 failure/error/skip; BUILD SUCCESS |
| Full mvnw.cmd -B verify, Java 17, DEBUG=false, Docker local | 4.540 test, 0 failure/error/skip; BUILD SUCCESS; 16:17 phút |
| JaCoCo LINE toàn dự án | 96,7906% (12.365/12.775 dòng); mọi coverage check đạt |
| JaCoCo LINE shared.audit / learning.reference / reference.web | 98,8372% (85/86) / 99,0566% (210/212) / 98,1132% (52/53) |
| git diff --check / UTF-8 BOM | PASS / 0 BOM |

Không dùng ngoại lệ môi trường. Tăng đúng 44 test mới so với 4.496 test của base deb4f03; 12 test audit cũ giữ nguyên. Benchmark review trong full suite p95 125 ms, đạt <500 ms.

Bằng chứng: helper trả đủ ID và cutoff inclusive, mặc định (0,null), hỗ trợ target type cũ, tập rỗng không query; tăng từ 1 lên 50 ID vẫn một query. RecordDetail cũ ghi rồi đếm được trong cùng transaction. Lỗi action/target không bị lẫn vào thống kê.

PostgreSQL/Redis xác nhận hai request đồng thời chỉ một request ghi audit; người/mục/ngày độc lập; ngày đổi lúc 17:00 UTC (00:00 VN), TTL tới hết ngày. Khoá FOR SHARE ngăn cập nhật mục trong lúc transaction báo lỗi chưa xong. Lỗi writer audit được mô phỏng để kiểm rollback và bộ đếm Redis vẫn giữ lượt; thử lại bị 429, không tự hoàn lượt.

HTTP xác nhận 204 có đúng action/target/actor và detail {reason,note}; payload tự gửi userId không thay người gọi. GUEST 401, role không phải LEARNER 403, cookie thiếu CSRF 403; cookie có CSRF và bearer LEARNER đều được gọi. 422/403/404 không chiếm suất báo hợp lệ tiếp theo. Note giữ null/khoảng trắng/Unicode, 300 code point được chấp nhận, 301 bị từ chối. Không thấy note trong log; Redis lỗi trả message 500 cố định và không có audit. Method/đường dẫn con ngoài matcher không được mở.

Fetch trước bàn giao: main 450ca82 (#152) chưa chứa deb4f03 (#151). Theo chỉ định leader, nhánh T-04 giữ nguyên base deb4f03, không rebase/merge main. PR T-04 phụ thuộc #151; trong lúc #151 chưa merge, diff GitHub với main có thể còn bao gồm phần T-03. Commit T-04 chỉ thay đúng 10 file đã duyệt, xét từ base deb4f03. Leader xử lý phần shared khi merge.

Self-check: không dependency, migration, ErrorCode, application.yaml, RateLimiter hay entity/repository thay đổi. AuditService chỉ thêm dependency constructor và hàm đọc theo lô/record ReportStat; record/recordDetail không đổi chữ ký hoặc nội dung. Shared không phụ thuộc learning; code learning ghi audit qua AuditService, không truy vấn bảng audit trực tiếp. SQL tham số hoá; không điều kiện trên JSONB. SecurityConfig chỉ thêm đúng matcher POST cho LEARNER. Fixture tự soạn, tài khoản local dùng bcrypt cost 12; không secret/PII thật/dữ liệu có bản quyền. Không log note/userId trong nhánh Redis lỗi, không trả exception upstream. Không sửa/tắt/skip test cũ, không chạy SQL/migration trên Neon. Không push main, merge PR hoặc force-push.

## T-05 — Chốt leader ngày 2026-11-14

Base a9f7697, nhánh spec/018-t05, không có thay đổi #152; leader tự giải shared khi merge. Allowlist 11 file, gồm GlobalExceptionHandler chỉ thêm bốn mã T-05 vào nhánh giữ details; không đổi xử lý mã khác.

- Q-T05-01 — ANSWERED: PUT thay toàn bộ input; trường tuỳ chọn bỏ qua về null/default (tags=[], sortOrder=0, isAffiliate=false). Field server (id/status/createdBy/publishedAt/updatedAt...) gửi kèm bị bỏ qua.
- Q-T05-02 — ANSWERED: loại khác BOOK cho phép author/publisher/publishedYear null và isAffiliate=false; có giá trị (kể cả chuỗi rỗng) hoặc true trả 422 VALIDATION_FAILED.
- Q-T05-03 — ANSWERED, thay đề xuất G0: publish khi PUBLISHED và hide khi HIDDEN là no-op 200. Unhide chỉ HIDDEN → DRAFT; ở DRAFT/PUBLISHED là no-op, không được hạ PUBLISHED thành DRAFT. No-op không audit, không đổi updated_at, không reset reportCount. Hide từ DRAFT/PUBLISHED → HIDDEN; publish từ DRAFT/HIDDEN → PUBLISHED sau kiểm điều kiện. Giữ published_at đầu tiên; chỉ đặt khi chưa có.
- Q-T05-04 — ANSWERED: PUT mục PUBLISHED làm mất điều kiện công bố trả 422 PUBLISH_INVALID_RESOURCE, rollback, giữ nguyên mọi dữ liệu/trạng thái.

| Nguồn | publish | hide | unhide |
|---|---|---|---|
| DRAFT | PUBLISHED, kiểm dữ liệu | HIDDEN | No-op DRAFT |
| PUBLISHED | No-op PUBLISHED | HIDDEN | No-op PUBLISHED |
| HIDDEN | PUBLISHED, kiểm dữ liệu | No-op HIDDEN | DRAFT |

reported=true dùng EXISTS trên shared.audit_logs với action REFERENCE_REPORTED, target_type REFERENCE, target_id và created_at >= updated_at, trong cả query count và page, TRƯỚC LIMIT/OFFSET. reported=false/bỏ qua không lọc. countByTargets gọi một lần cho trang đã lọc, cutoff là updated_at từng mục; trang rỗng bỏ qua helper. Tổng tối đa ba query dữ liệu, không N+1.

Bốn mã MALFORMED_URL, NON_HTTPS_LINK, DOMAIN_NOT_ALLOWED, PUBLISH_INVALID_RESOURCE thêm ngay trước INTERNAL_ERROR. Policy T-02 giữ nguyên, ánh xạ reason một-một và giữ đúng field url/thumbnailUrl trong details, không chứa giá trị URL. Matcher chỉ đúng bảy method/path quản trị cho CONTENT_EDITOR trước /api/admin/**; ADMIN thuần bị 403. Write + audit cùng transaction, mục cũ khoá FOR UPDATE. Dùng Clock và microsecond cho timestamp. Audit target REFERENCE và năm action, before/after không có số báo cáo tổng hợp. Không DELETE, không sửa policy/entity/repository/contract/migration.

### T-05 — Gate và self-check

| Gate | Kết quả thực tế |
|---|---|
| ReferenceAdminServiceTest + ReferenceAdminIntegrationTest + AdminReferenceControllerIntegrationTest + GlobalExceptionHandlerTest | 98/98 (16 + 16 + 46 + 20), F0/E0/S0, BUILD SUCCESS |
| Hồi quy reference T-01…T-04 + AuditServiceIntegrationTest + AuditTargetCountIntegrationTest + ModuleBoundaryTest | 210/210, F0/E0/S0, BUILD SUCCESS; audit cũ 12/12, boundary 9/9 |
| Full mvnw.cmd -B verify, Java 17, DEBUG=false, Docker local | 4.619 test, F0/E0/S0, BUILD SUCCESS; 17:09 phút; mọi coverage check đạt |
| JaCoCo LINE toàn dự án | 96,8361% (12.610/13.022 dòng) |
| JaCoCo LINE learning.reference / reference.web / shared.audit | 99,4924% (392/394) / 97,1429% (102/105) / 98,8372% (85/86) |
| git diff --check / UTF-8 BOM | PASS / 0 BOM |

Lần targeted đầu có một lỗi trong test mới kiểm OpenAPI: profile test không mở /v3/api-docs nên nhận 401. Đã sửa riêng lớp test mới dùng test + dev như tiền lệ LibraryContractTest, không sửa matcher production. Chạy lại 98/98 xanh; đây không phải ngoại lệ môi trường. Full verify tăng đúng 79 test so với base 4.540; không dùng ngoại lệ môi trường, không sửa/skip/nới test cũ.

Bằng chứng PostgreSQL/HTTP: đủ chín ô chuyển trạng thái, no-op giữ updated_at/published_at/reportCount và không audit; hai request publish đồng thời chỉ một audit. PUT thay toàn bộ/default, bỏ qua field server; dữ liệu PUBLISHED thiếu title/url/author bị rollback. Audit lỗi thì toàn bộ create/update/transition rollback. reported=true lọc trước phân trang và dùng cùng predicate đếm tổng; cutoff inclusive, không lẫn action/target. Tăng số mục vẫn hai query trực tiếp cộng một countByTargets theo lô. Bảy endpoint có đúng status trong YAML, guest 401, các role không phải CONTENT_EDITOR (kể cả ADMIN thuần) 403, cookie cần CSRF. Bốn mã T-05 giữ details; FORBIDDEN vẫn bỏ details và giữ retryAfterSeconds như trước.

Self-check: đúng 11 file duyệt; không thay entity/repository/policy/AuditService/contract/application.yaml/migration. SQL tham số hoá, không điều kiện JSONB. Chỉ thêm bốn mã trước INTERNAL_ERROR và bảy matcher CONTENT_EDITOR trước ADMIN. Fixture tự soạn, không secret/PII thật; không SQL trên Neon, không push main/merge PR/force-push. Nhánh giữ base a9f7697 theo chỉ định; leader giải xung đột shared lúc merge.

Fetch trước bàn giao: origin/main = 5e5695c, đã chứa base a9f7697 (#154). Main có thay đổi shared mới (#152 unsubscribe và #158 study reminder); giữ nguyên base theo leader, không rebase/merge. Merge-base a9f76977749051d338d7ab06c75e1beb439ce4f9. Cần leader giữ cả các matcher/mã mới của main khi gộp.
## T-06 — G0/G2 ngày 2026-11-14

Base/merge-base: 5e5695ccf4f6aab2e1b764297b0e22e2a63cfe81; nhánh spec/018-t06, worktree riêng. Allowlist 27 file được leader duyệt, thực tế đúng 27 file; không sửa RoleLayout, shared/ui, contract hoặc backend ngoài ba tên màn đã duyệt.

| Câu hỏi | Chốt | Trạng thái |
|---|---|---|
| Q-T06-01 | Ba route /references/media (SCR-053), /references/books (SCR-054), /references/books/:id (SCR-055): access public, layout by-role. Dùng RoleLayout sẵn có, không tự chọn role. PublicLayout chỉ thêm menu khách. Test guest/learner không redirect, đúng layout. | ANSWERED |
| Q-T06-02 | Mobile có sáu ô; Tham khảo ở cuối, tên truy cập Tài liệu tham khảo. Kiểm 320/375/640px không tràn/cắt nghĩa; không thêm menu Thêm. | ANSWERED |
| Q-T06-03 | Tag là ô nhập + Áp dụng, gửi nguyên giá trị (không trim), áp dụng tag hoặc đổi HSK/tab về page 0. Không suy danh sách tag từ một trang dữ liệu. | ANSWERED |
| Q-T06-04 | API chi tiết trả kind khác BOOK thì SCR-055 hiện Không tìm thấy sách + liên kết về danh mục; không đổi API. | ANSWERED |

File 27 AssistantPromptBuilder chỉ thêm SCR-053 Tài liệu nghe nhìn, SCR-054 Danh mục sách tham khảo, SCR-055 Chi tiết sách tham khảo vào SCREEN_NAMES. Không thêm SCR-009: leader đã sửa riêng trong 88e0155, không được thêm lần nữa để tránh trùng khoá Map.ofEntries.

### G2 — Gate thực tế

| Bước | Kết quả |
|---|---|
| npm ci trong cnhsk-web | PASS; không thay lockfile, không npm audit fix |
| References + Layouts + routeAccess/routes/screenRegistry/preview registry | 109/109 test, 11 file, PASS |
| Shuffle cùng tập test, seed 17 / 53 / 181, minWorkers=2 maxWorkers=2 | 109/109 mỗi seed, PASS cả ba |
| Full Vitest minWorkers=2 maxWorkers=2 | 2.520/2.520 test, 177 file, PASS; 208,96 giây; không cần chạy lại test chập chờn |
| npm run lint | 0 lỗi, chỉ một warning LevelBadge có sẵn |
| npm run typecheck / npm run build | PASS / PASS (Vite 4,80 giây) |
| mvnw.cmd -B -Dtest=AssistantPromptBuilderTest test, Java 17 DEBUG=false | 11 test: 10 pass, 1 failure, 0 error/skip. Chỉ FR007_learnerRouteDefinitionsAllHaveTrustedNames, message [SCR-009]. BUILD FAILURE — ngoại lệ được leader duyệt riêng cho base 5e5695c. Không có lỗi SCR-053/054/055, không sửa/nới test. |
| Chrome headless với profile tạm và HTTP fixture local | 28 kiểm tra PASS, gồm 24 tổ hợp guest/learner × 3 màn × 320/375/640/1280px, 3 trạng thái loading/empty/error, 1 lượt báo lỗi 204 |
| git diff --check / UTF-8 BOM / TODO mới | PASS / 0 / 0 |

Lượt targeted đầu phát hiện lỗi ở test mới (truy vấn text nhiều dòng, thời điểm AuthProvider đổi layout, callback beforeEach trả mock function) và nhãn mobile chưa truyền ra DOM qua NavigationLink. Đã sửa trong allowlist và chạy lại gate cuối ở trên; không nới test cũ. Test mới bổ sung sau đó chọn đúng nút theo thẻ, chứng minh response báo lỗi cũ không ghi success sang thẻ mới. Không dùng ngoại lệ FE.

### Bằng chứng UI và contract FE

- SCR-053: YouTube/Podcast gọi đúng kind; chỉ bộ chọn HSK 1–3; gửi tag nguyên giá trị, page API bắt đầu 0; huỷ request cũ khi đổi bộ lọc. Bốn trạng thái tách biệt, lỗi có Thử lại, không giả danh sách rỗng.
- SCR-054: luôn gọi kind=BOOK; thẻ có tác giả, cấp và link SCR-055; không nhúng nội dung sách. SCR-055 phân biệt không tìm thấy (404/kind khác/ID sai) với lỗi mạng có thử lại; nullable publisher/năm có nhãn Đang cập nhật; mô tả đầy đủ dạng chữ, nhãn affiliate cạnh nút mua/tham khảo.
- AT-17: chuỗi <script> và HTML trong mô tả hiển thị chữ, không tạo script/img onerror; không dangerouslySetInnerHTML. AT-18: mọi link ngoài target=_blank, rel=noopener noreferrer. Ở 320/375/640px lưới đúng một cột, document.scrollWidth không vượt clientWidth. Nhãn Tham khảo ở 320px xuống hai dòng, đầy đủ; tên truy cập là Tài liệu tham khảo. Kiểm thêm desktop 1280px. Đã xem ảnh 320px learner và 375px chi tiết sách guest; ảnh chỉ nằm ngoài repo, không commit.
- Chỉ LEARNER có menu Báo lỗi. Page gọi POST với reason/note, shared apiRequest giữ cơ chế cookie/CSRF có sẵn; form chặn gửi lặp khi pending, 300 code point được phép, 301 bị chặn. 204 hiện Đã ghi nhận báo lỗi; 429 hiện thông báo hạn mức, các lỗi khác giữ message. Không lẫn success giữa các thẻ/tài khoản.
- Preview đăng ký cả SCR-053/054/055, mỗi màn đủ bốn trạng thái. View thuần props, fixture tự soạn có null; page/component không import preview hoặc test-fixtures. Không gọi HEAD hay mở website bên ngoài để kiểm link.

Chrome dùng fixture HTTP bị chặn tại app local 127.0.0.1:15186, KHÔNG phải bằng chứng backend thật. Ảnh/JSON/log chỉ lưu ngoài repo. Không cần nạp DB hay chạy full Maven verify cho task FE cộng ba dòng tên màn; gate Java chọn lọc ghi trung thực failure đã được duyệt. Không dùng Neon, không đụng môi trường T-19.

Fetch trước bàn giao: origin/main đã lên ba5bc05, gồm T-05 (#162) và sửa SCR-009 ở 88e0155. Giữ base 5e5695c; KHÔNG rebase/merge main theo leader. questions.md/tasks.md có thay đổi mới từ T-05 trên main; leader cần giữ cả hai mục khi gộp. AssistantPromptBuilder giữ cả dòng SCR-009 của main và ba dòng mới của T-06.

Self-check: không dependency, migration, API/error code/security thay đổi; không secret/email thật/dữ liệu bản quyền. API client dùng đúng ba endpoint công khai/báo lỗi của YAML, dùng chung apiRequest và AbortSignal. RoleLayout nguyên trạng. Chỉ thêm assertion menu trong test layout cũ và tăng số link đúng từ 5 lên 6; mọi assertion khác giữ nguyên. Commit file cụ thể, không add ./-A, không push main/force-push/merge PR.

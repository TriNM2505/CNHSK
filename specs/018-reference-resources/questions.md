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

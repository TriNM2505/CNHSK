# Feature Specification: Cây năng lực và ngân hàng câu hỏi

**Feature:** `010-knowledge-tree`
**Feature tree:** 6.3 Kho câu hỏi và nhãn kiến thức · phần dữ liệu của 3.2 · **Module:** `learning` (package `learning.knowledge`)
**Use case:** UC-109 (sửa câu hỏi) · UC-110 (nhập câu hỏi và cây) · UC-112 (phần kho câu hỏi; phần đề thi thuộc spec 012) · UC-137 · **Actor:** `CONTENT_EDITOR`, `TEACHER`, `LEARNER`
**Màn hình:** SCR-064 Question Bank · SCR-059 Question Edit · SCR-068 Learning Data Import (thêm 4 loại dữ liệu)
**Scope:** MVP · **Priority:** P0

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — được code theo `tasks.md` |
| **Approved by** | Leader (RFC đợt cuối R1–R12 đã đồng thuận) |
| **Locked on** | 2026-11-03 |
| **Phụ thuộc** | Spec 001 (đăng nhập, lỗi, CSRF, `AuditService`) · Spec 002 (luồng nhập §3, §4) · Spec 003 §0.9 (sự kiện, spec 011 dùng) |
| **Quyết định chung** | `questions.md` mục 2026-11-02 (Q1–Q14) · QĐ-02 plan `261017-1654-cay-nang-luc-han-ngu` · luật gộp plan `261102-2104` |
| **Điều kiện APPROVED** | RFC Q1 được cả nhóm đồng thuận (§9) |

> **Codex đọc:** `AGENTS.md` → spec 002 §3–§4 → file này → `contracts/openapi-knowledge.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi (để ở đâu) |
|---|---|
| Lưu cây KID theo Cây Năng Lực v2.0: nút, tiên quyết (`PREREQ`), hỗ trợ (`SUPPORT`) — **không thêm bảng** (Q2) | Mastery, minh chứng, biên học tập, cây màu theo trạng thái — spec 011 |
| Nạp cây qua SCR-068 bằng 2 mẫu nhập mới `KNOWLEDGE_NODE`, `KNOWLEDGE_EDGE`; kiểm DAG khi nhập | Xoá nút, xoá cạnh, nhiều phiên bản cây — chưa cần (§11) |
| Mẫu nhập `QUESTION`: 1 KID chính, ≤ 2 KID phụ, mã lỗi gắn phương án nhiễu (Q7) | Đề thi, gán câu vào đề, SCR-065/066 — spec 012 |
| Mẫu nhập `NODE_LINK`: gắn chủ đề, từ, chữ, điểm ngữ pháp đã có vào nút (Q6) | Hàng đợi duyệt câu AI SCR-058, AI sinh câu (3.3) — sau demo |
| API đọc cây cho người học (Q11) | Tạo câu hỏi trên giao diện — SCR-064 cấm "invented create API"; câu vào kho chỉ qua nhập file |
| SCR-064 tra kho; SCR-059 xem, sửa (`TEACHER`), lưu trữ (`CONTENT_EDITOR`) (Q3) | Cây chủ đề SCR-067, cổng 90% chủ đề (`BUS-05`) |
| Dịch vụ nội bộ `KnowledgeTreeService` cho spec 011, 012 | Khoá sửa khi có cuộc thi `EDIT_DURING_ACTIVE_CONTEST` (Q12) |

### 0.1 · Thay đổi DB — `V5__knowledge_tree.sql` (Q9)

**Không thêm bảng.** Mở rộng bốn bảng đã có (`database.md` §4.4–4.6). Mọi bảng bị đổi đang **rỗng** hoặc chỉ thêm cột cho phép `NULL`:
`knowledge_points`, `question_knowledge_points`, `questions` chưa có luồng nào ghi trước V5; `topics`, `lexemes` chỉ thêm cột cho phép `NULL` nên entity của spec 002/003 không phải sửa (`ddl-auto: validate` bỏ qua cột thừa).

```sql
-- Spec 010 section 0.1: knowledge tree on existing tables, after V4.
-- knowledge_points, question_knowledge_points and questions have no writer before V5.

-- 1. knowledge_points: one row = one KID node of Cay Nang Luc v2.0
ALTER TABLE learning.knowledge_points
    ADD COLUMN domain      VARCHAR(2)   NOT NULL CHECK (domain IN ('NA','CH','TV','NP','NL','VB')),
    ADD COLUMN name_zh     VARCHAR(200) NOT NULL,
    ADD COLUMN can_do      TEXT,
    ADD COLUMN prereq_ids  BIGINT[]     NOT NULL DEFAULT '{}',
    ADD COLUMN support_ids BIGINT[]     NOT NULL DEFAULT '{}',
    ADD COLUMN updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now();
ALTER TABLE learning.knowledge_points ALTER COLUMN skill_type DROP NOT NULL;
ALTER TABLE learning.knowledge_points
    ADD CONSTRAINT ck_kp_kid CHECK (
        code ~ '^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$'
        AND left(code, 2) = domain
        AND substring(code FROM 3 FOR 1)::SMALLINT = hsk_level),
    -- v2.0: a word is not a node; words link to a TV node through lexemes.knowledge_point_id
    ADD CONSTRAINT ck_kp_no_lexeme CHECK (lexeme_id IS NULL);
CREATE INDEX ix_kp_domain ON learning.knowledge_points(domain, hsk_level);

-- 2. question_knowledge_points: Q-matrix role, exactly one PRIMARY per question
ALTER TABLE learning.question_knowledge_points
    ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'PRIMARY' CHECK (role IN ('PRIMARY','SECONDARY'));
CREATE UNIQUE INDEX uq_qkp_primary ON learning.question_knowledge_points(question_id)
    WHERE role = 'PRIMARY';

-- 3. questions: natural key for re-import and difficulty for 011/012
ALTER TABLE learning.questions
    ADD COLUMN code       VARCHAR(50) UNIQUE,
    ADD COLUMN difficulty SMALLINT    NOT NULL DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3);
ALTER TABLE learning.questions
    ADD CONSTRAINT ck_q_import_code CHECK (source <> 'IMPORT' OR code IS NOT NULL);

-- 4. existing content -> node
ALTER TABLE learning.topics  ADD COLUMN knowledge_point_id BIGINT REFERENCES learning.knowledge_points(id);
ALTER TABLE learning.lexemes ADD COLUMN knowledge_point_id BIGINT REFERENCES learning.knowledge_points(id);
CREATE INDEX ix_topic_kp  ON learning.topics(knowledge_point_id)  WHERE knowledge_point_id IS NOT NULL;
CREATE INDEX ix_lexeme_kp ON learning.lexemes(knowledge_point_id) WHERE knowledge_point_id IS NOT NULL;
```

| Cột | Ý nghĩa |
|---|---|
| `knowledge_points.code` | Mã KID, ví dụ `NP3-004` (miền + cấp + số thứ tự). Cấp `7` = nhóm HSK 7–9 của v2.0 |
| `knowledge_points.title` | Tên tiếng Việt của nút (cột đã có) |
| `knowledge_points.skill_type` | Kỹ năng chính nếu có; không bắt buộc (miền thay vai trò phân loại) |
| `prereq_ids` / `support_ids` | Id các nút **trước** nút này (cạnh `from → to` lưu ở nút `to`), như `topics.prereq_ids` |
| `topic_items.knowledge_point_id` | **Không dùng.** Chủ đề nối nút bằng `topics.knowledge_point_id` |

Mảng không có khoá ngoại; tính toàn vẹn kiểm ở ứng dụng khi nhập (FR-006, FR-007) và bằng test V01–V04 (§14).

### 0.2 · Bốn mẫu nhập mới (thêm vào spec 002 §4.0)

| `dataType` | Nhãn trên SCR-068 | Khoá tự nhiên | Phụ thuộc (nhập trước) |
|---|---|---|---|
| `KNOWLEDGE_NODE` | Nút cây năng lực | `kid` | — |
| `KNOWLEDGE_EDGE` | Quan hệ giữa các nút | `(from_kid, to_kid)` | `KNOWLEDGE_NODE` |
| `QUESTION` | Câu hỏi | `code` | `KNOWLEDGE_NODE` |
| `NODE_LINK` | Gắn nội dung vào nút | `(target_type, target_key)` | `KNOWLEDGE_NODE`, `TOPIC`, `LEXEME`, `GRAMMAR` |

Bốn mẫu đi qua **nguyên luồng spec 002**: kiểm tra → xem trước → xác nhận → ghi từng dòng `REQUIRES_NEW` (002 FR-018), cùng giới hạn 2 MB / 5 000 dòng, cùng lịch sử và báo cáo lỗi. **Dữ liệu cây thật (phụ lục B) không commit vào repo** (Q5); test dùng cây tự soạn trong `src/test/resources/import/knowledge/`.

### 0.3 · File dùng chung bị sửa (dễ conflict — báo leader trước khi mở PR)

| File | Thay đổi | Task |
|---|---|---|
| `shared/exception/ErrorCode.java` + `GlobalExceptionHandlerTest` | 6 mã §5 thêm ngay trước `INTERNAL_ERROR`, đồng bộ danh sách `containsExactly` | T-10, T-11, T-12 |
| `shared/config/SecurityConfig.java` | Matcher `/api/knowledge-tree/**` và `/api/admin/questions/**` đặt **trước** `/api/admin/**` (đang chỉ cho `ADMIN`) | T-10, T-11 |
| `learning/content/importer/template/ImportDataType.java`, `ImportTemplate.java` + validator/writer mới | 4 mẫu §0.2 | T-06…T-09 |
| `specs/002-learning-data-import/contracts/openapi-import.yaml` | Enum `dataType` thêm 4 giá trị; enum mã lỗi dòng thêm §4.4 | T-06 |
| Test 002 đang khẳng định "7 mẫu" (AT-41) | Đổi thành 11 | T-06 |
| `cnhsk-web/src/features/content-import/types.ts`, `labels.ts` | 4 `dataType` + nhãn | T-04 |
| `cnhsk-web/src/app/routeAccess.ts`, `screenRegistry.ts` | 2 route SCR-064/059, catalog feature `question-bank` | T-01 |
| `docs/reference/database.md` | §4.4–4.6, §11 (V5), §13 | T-05 |

`application-test.yaml` **không** đổi.

---

## 1 · Context & Goal

| Mã | Business goal | Đóng góp |
|---|---|---|
| **BG-01** | Biết chính xác yếu điểm kiến thức nào | Mỗi câu đo đúng 1 nút chính → spec 011/012 chỉ ra nút yếu |
| **BG-04** | Nội dung qua kiểm duyệt | Câu nhập từ file `APPROVED` (Q4); câu AI vẫn `PENDING_REVIEW` (`BUS-07`); chỉ `TEACHER` sửa nội dung |
| **BG-05** | Demo trọn luồng học → thi → phân tích | Cây + kho câu là nền của spec 011, 012 |

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Nạp cây tự soạn ~60 nút, 4 kiểm tra DAG (V01–V04) trả 0 dòng | 100% |
| **SC-002** | Câu `APPROVED` có đúng 1 KID chính, ≤ 2 KID phụ | 100% |
| **SC-003** | Truy vấn "câu nào đo nút X" (`NFR-P03` CONTEXT) | < 100 ms p95 |

---

## 2 · User Stories

### US-1 · Nạp cây năng lực (P1)
**Given** tôi là `CONTENT_EDITOR`, có file nút và file quan hệ đúng mẫu
**When** tôi nhập `KNOWLEDGE_NODE` rồi `KNOWLEDGE_EDGE` ở SCR-068
**Then** xem trước báo dòng hợp lệ/lỗi; quan hệ tạo vòng, tiên quyết cấp cao hơn, quá 4 tiên quyết hoặc bắc cầu thừa bị báo lỗi theo dòng
**And** sau khi xác nhận, người học thấy cây ở API cây

### US-2 · Nhập câu hỏi gắn nút (P1)
**Given** cây đã nạp
**When** tôi nhập `QUESTION` có `kid_primary`, `kid_secondary`, `distractor_errors`
**Then** câu hợp lệ vào kho ở `APPROVED`, gắn đúng 1 nút chính

### US-3 · Gắn nội dung cũ vào nút (P1)
**When** tôi nhập `NODE_LINK` gắn chủ đề "Gia đình" vào `TV1-003`
**Then** chủ đề trỏ tới nút đó; gắn ngữ pháp vào nút `TV` bị từ chối

### US-4 · Tra kho và lưu trữ (P1)
**Given** tôi là `CONTENT_EDITOR` ở SCR-064
**When** tôi lọc HSK 2, nút `NP2-005`
**Then** tôi thấy danh sách câu, mở SCR-059 xem chi tiết và lưu trữ câu không dùng nữa

### US-5 · Giáo viên sửa câu (P2)
**Given** tôi là `TEACHER`
**When** tôi sửa lời giải và nhãn của một câu đã có người làm
**Then** lưu được; đổi đáp án đúng bị từ chối "Câu đã có người làm — tạo câu mới thay thế"

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | File quan hệ có `B→A` trong khi DB đã có `A→B` | Dòng lỗi `EDGE_CYCLE` |
| E2 | Hai dòng trong cùng file tạo vòng `A→B`, `B→A` | Dòng đầu hợp lệ, dòng sau `EDGE_CYCLE` (xét cả dòng hợp lệ phía trên) |
| E3 | Giữa lúc xem trước và xác nhận, người khác đã nhập cạnh làm dòng này tạo vòng | Ghi dòng kiểm lại → `EDGE_CYCLE`, dòng khác vẫn ghi (002 FR-018/019) |
| E4 | Nhập lại câu đã có người làm, đổi đáp án | Dòng lỗi `GRADING_CHANGE_NOT_ALLOWED`, câu cũ giữ nguyên |
| E5 | Nhập lại câu đã `ARCHIVED` | Cập nhật nội dung được phép, trạng thái **giữ** `ARCHIVED` |
| E6 | `TEACHER` sửa câu AI `PENDING_REVIEW` | Lưu được, trạng thái giữ `PENDING_REVIEW` (lưu ≠ duyệt) |
| E7 | Nhập lại nút đổi tên | `UPDATE`, quan hệ đã có giữ nguyên |
| E8 | Cạnh đã có đúng loại | `UNCHANGED` |
| E9 | Cạnh đã có nhưng khác loại (`SUPPORT` thành `PREREQ`) | `EDGE_TYPE_CONFLICT` — đổi loại cạnh chưa hỗ trợ (§11) |
| E10 | `kid` dạng `np3-004` (chữ thường) | Chuẩn hoá chữ hoa trước khi kiểm (002 FR-030 mở rộng cho cột KID) |

---

## 3 · Functional Requirements

### Nhóm 0 — Quyền (FR-001)

| Mã | Yêu cầu |
|---|---|
| **FR-001** | THE system SHALL áp quyền: `GET /api/knowledge-tree/**` → `LEARNER`, `CONTENT_EDITOR`, `TEACHER`; `GET /api/admin/questions/**` → `CONTENT_EDITOR`, `TEACHER`; `PUT /api/admin/questions/{id}` → chỉ `TEACHER`; `PATCH /api/admin/questions/{id}/archive` → chỉ `CONTENT_EDITOR`; mẫu nhập §0.2 theo spec 002 FR-001 (chỉ `CONTENT_EDITOR`). Sai role → **403** `FORBIDDEN`; không có role kế thừa (`ADMIN` không tự có quyền nào ở đây). Thao tác ghi qua cookie cần CSRF (spec 001) |

### Nhóm 1 — Mẫu nhập cây (FR-002…FR-007) · SCR-068

| Mã | Yêu cầu |
|---|---|
| **FR-002** | `GET /api/admin/imports/templates` SHALL trả **11** mẫu: 7 mẫu spec 002 rồi 4 mẫu §0.2 theo thứ tự `KNOWLEDGE_NODE`, `KNOWLEDGE_EDGE`, `QUESTION`, `NODE_LINK`, kèm `naturalKey`, `dependsOn`, `columns` theo FR-003, FR-005, FR-008, FR-013. File mẫu CSV (002 FR-035) có 1 dòng ví dụ **tự soạn** |
| **FR-003** | Mẫu `KNOWLEDGE_NODE` SHALL có cột: `kid`* (chuẩn hoá chữ hoa, khớp `^(NA\|CH\|TV\|NP\|NL\|VB)[1-7]-[0-9]{3}$`, sai → `INVALID_KID`), `name_vi`* (≤ 200), `name_zh`* (≤ 200, có ít nhất 1 ký tự CJK, sai → `NOT_CJK`), `can_do` (≤ 1 000), `skill_type` (một trong 6 giá trị của `knowledge_points.skill_type`). `domain` = 2 ký tự đầu, `hsk_level` = ký tự thứ 3 — **không** nhập riêng |
| **FR-004** | WHEN ghi `KNOWLEDGE_NODE`, THE system SHALL upsert theo `code = kid`: tạo mới hoặc cập nhật `title`, `name_zh`, `can_do`, `skill_type`, `updated_at`; **không bao giờ** đổi `prereq_ids`, `support_ids` (do `KNOWLEDGE_EDGE` quản). Hành động `CREATE`/`UPDATE`/`UNCHANGED` theo 002 FR-013 |
| **FR-005** | Mẫu `KNOWLEDGE_EDGE` SHALL có cột `from_kid`*, `to_kid`*, `edge_type`* (`PREREQ` \| `SUPPORT`). Hai nút phải có trong DB lúc kiểm tra, không → `REFERENCE_NOT_FOUND` (002 FR-011: nhập nút trước) |
| **FR-006** | WHEN `edge_type = PREREQ`, THE system SHALL từ chối dòng: `from = to` → `EDGE_SELF`; `hsk_level(from) > hsk_level(to)` → `PREREQ_LEVEL_HIGHER`; đã có đường `to → … → from` theo cạnh `PREREQ` → `EDGE_CYCLE`; `to` sẽ có > 4 tiên quyết trực tiếp → `TOO_MANY_PREREQS`; đã có đường `from → … → to` qua nút khác → `EDGE_REDUNDANT`. Đồ thị xét = cạnh trong DB **cộng** các dòng hợp lệ phía trên cùng file (E2). Khi ghi, kiểm lại trên DB tại lúc ghi (E3) |
| **FR-007** | WHEN `edge_type = SUPPORT`, THE system SHALL từ chối `from = to` (`EDGE_SELF`). Với cả hai loại: cặp đã có ở loại kia → `EDGE_TYPE_CONFLICT` (E9); cặp đã có đúng loại → `UNCHANGED` (E8). Ghi = thêm id `from` vào `prereq_ids` hoặc `support_ids` của nút `to` (khoá dòng nút `to` `FOR UPDATE`) |

### Nhóm 2 — Mẫu nhập câu hỏi (FR-008…FR-012)

| Mã | Yêu cầu |
|---|---|
| **FR-008** | Mẫu `QUESTION` SHALL có cột: `code`* (`^[A-Z0-9_-]{3,50}$`), `qtype`*, `hsk_level`* (1–9), `difficulty` (1–3, mặc định 2), `stem`* (≤ 2 000), `option_a` … `option_f` (≤ 300), `correct`*, `explanation`* (≤ 3 000), `audio_url` (URL), `image_url` (URL), `kid_primary`*, `kid_secondary` (≤ 2 KID ngăn `\|`), `distractor_errors` (≤ 6 mục ngăn `\|`, mỗi mục `KEY:KID` hoặc `KEY:KID:ghi chú`, ghi chú ≤ 200) |
| **FR-009** | THE system SHALL kiểm theo `qtype`: **(a)** `MULTIPLE_CHOICE`, `SENTENCE_MATCH`, `IMAGE_MATCH`: phương án liên tiếp từ `A`, 2–6 phương án, khác nhau đôi một sau chuẩn hoá; `correct` = đúng một khoá trỏ phương án có nội dung; `IMAGE_MATCH` bắt buộc `image_url`. **(b)** `TRUE_FALSE`: không có cột phương án; `correct` = `TRUE` \| `FALSE`; hệ thống tạo `A="Đúng"`, `B="Sai"`. **(c)** `FILL_BLANK`: `stem` chứa `___`; không phương án; `correct` = đáp án ≤ 50 → `correct_key`. **(d)** `SENTENCE_ORDER`: 3–6 phương án là mảnh câu; `correct` = chuỗi khoá dùng **mỗi** phương án đúng một lần (vd `CABD`) → `correct_key`. **(e)** `ESSAY` → `INVALID_VALUE` "Chưa hỗ trợ câu tự luận". Vi phạm đáp án → `INVALID_ANSWER` (002 §4.8) |
| **FR-010** | THE system SHALL kiểm nhãn: `kid_primary` và mọi `kid_secondary` tồn tại (`REFERENCE_NOT_FOUND`); > 2 KID phụ → `TOO_MANY_SECONDARY_KIDS`; KID lặp hoặc phụ trùng chính → `DUPLICATE_KID`. `distractor_errors` chỉ cho loại (a); `KEY` phải là phương án **sai** có nội dung, mỗi khoá một lần; KID phải tồn tại — sai → `INVALID_VALUE` |
| **FR-011** | WHEN ghi dòng mới, THE system SHALL tạo câu `source='IMPORT'`, `status='APPROVED'` (Q4), `reusable=true`, `options` = `[{key, text, is_correct, error_kid?, error_note?}]` (JSONB chỉ đọc, `AC-09`), và đúng 1 dòng `question_knowledge_points` `role='PRIMARY'` + 0–2 dòng `SECONDARY` trong cùng transaction của dòng |
| **FR-012** | WHEN `code` đã có, THE system SHALL: nếu câu **đã được dùng** (có `attempt_items.question_id`) và đổi trường chấm điểm (`qtype`, tập khoá phương án, khoá đúng, `correct_key`) → dòng lỗi `GRADING_CHANGE_NOT_ALLOWED` (BR-109-4, E4); ngược lại cập nhật các cột có trong file (002 FR-020), **thay** toàn bộ nhãn, **giữ** `status` (E5) |

### Nhóm 3 — Gắn nội dung vào nút (FR-013…FR-014)

| Mã | Yêu cầu |
|---|---|
| **FR-013** | Mẫu `NODE_LINK` SHALL có cột `target_type`* (`TOPIC` \| `WORD` \| `CHAR` \| `GRAMMAR`), `target_key`* (`TOPIC`: `topics.code`; còn lại: `lexemes.text` cùng `kind`), `kid`*. Đích không có → `REFERENCE_NOT_FOUND`. Miền phải khớp: `TOPIC`, `WORD` → `TV`; `CHAR` → `CH`; `GRAMMAR` → `NP`; sai → `DOMAIN_MISMATCH` |
| **FR-014** | WHEN ghi `NODE_LINK`, THE system SHALL đặt `knowledge_point_id` của `topics` hoặc `lexemes`; đã trỏ đúng nút → `UNCHANGED`; trỏ nút khác → ghi đè (`UPDATE`). Không gỡ liên kết qua nhập (§11) |

### Nhóm 4 — Đọc cây (FR-015…FR-016)

| Mã | Yêu cầu |
|---|---|
| **FR-015** | `GET /api/knowledge-tree?domain&hskLevel` SHALL trả `{nodes:[{kid, domain, hskLevel, nameVi, nameZh, canDo, skillType, prereqKids[], supportKids[]}]}` sắp theo miền `NA, CH, TV, NP, NL, VB`, rồi `hskLevel`, rồi `kid`. `domain` ngoài 6 giá trị, `hskLevel` ngoài 1–7 → **422** `VALIDATION_FAILED`. Không phân trang (cây ≤ 600 nút). Không trả dữ liệu câu hỏi |
| **FR-016** | `GET /api/knowledge-tree/nodes/{kid}` SHALL trả nút (FR-015) + `dependentKids[]` (nút nhận nút này làm `PREREQ`) + `topics:[{id, code, name}]` gắn vào nút + `lexemeCount` + `approvedQuestionCount`. KID không có hoặc sai dạng → **404** `KNOWLEDGE_NODE_NOT_FOUND` |

### Nhóm 5 — Kho câu hỏi (FR-017…FR-019) · SCR-064

| Mã | Yêu cầu |
|---|---|
| **FR-017** | `GET /api/admin/questions?hskLevel&qtype&status&kid&q&page&size` SHALL lọc theo `hsk_level` (1–9), `qtype` (7 giá trị), `status` (4 giá trị), `kid` (câu có nút đó ở bất kỳ vai trò), `q` (2–100 ký tự, tìm chứa trong `code` hoặc `stem`, tham số hoá `HR-07`); `page` ≥ 0, `size` 1–100 mặc định 10 (`business.md` §7); sắp `id` giảm dần. Tham số sai → **422** `VALIDATION_FAILED` |
| **FR-018** | Mỗi phần tử SHALL có `{id, code, qtype, hskLevel, difficulty, stemSummary, source, status, primaryKid, secondaryKids[], used}`; `stemSummary` = 120 ký tự đầu; `used` = có `attempt_items.question_id`. Nhãn và `used` lấy bằng **một truy vấn mỗi loại** cho cả trang (không N+1) |
| **FR-019** | `GET /api/admin/questions/{id}` SHALL trả `{id, code, qtype, hskLevel, difficulty, stem, options:[{key, text, isCorrect, errorKid, errorNote}], correctKey, explanation, audioUrl, imageUrl, source, status, reviewReason, labels:[{kid, nameVi, role}], used}`. Không tồn tại → **404** `QUESTION_NOT_FOUND` |

### Nhóm 6 — Sửa và lưu trữ (FR-020…FR-023) · SCR-059

| Mã | Yêu cầu |
|---|---|
| **FR-020** | `PUT /api/admin/questions/{id}` SHALL nhận DTO chỉ gồm `{stem, options[{key, text, isCorrect, errorKid, errorNote}], correctKey, explanation, hskLevel, difficulty, audioUrl, imageUrl, primaryKid, secondaryKids[]}`; `qtype`, `code`, `status`, `source` **không** sửa được (trường lạ bị bỏ qua). Chỉ `TEACHER` (Q3) |
| **FR-021** | THE system SHALL kiểm như FR-009, FR-010 theo `qtype` hiện tại: vi phạm cấu trúc hoặc KID không có → **422** `MALFORMED_QUESTION` kèm `details[{field, message}]`; thiếu `primaryKid` → **422** `KNOWLEDGE_NODE_REQUIRED` |
| **FR-022** | WHEN câu đã dùng và đổi trường chấm điểm → **409** `GRADING_CHANGE_NOT_ALLOWED`; câu `ARCHIVED` → **409** `QUESTION_ARCHIVED`. Ngược lại SHALL khoá dòng câu (`FOR UPDATE`), ghi nội dung + thay nhãn, **giữ** `status` (E6), ghi `audit_logs` `action='QUESTION_UPDATE'`, `entity='question:{id}'`, `before`/`after` (BR-109-8), một transaction; trả **200** như FR-019 |
| **FR-023** | `PATCH /api/admin/questions/{id}/archive` SHALL chuyển `status='ARCHIVED'`, ghi `audit_logs` `QUESTION_ARCHIVE`; đã `ARCHIVED` → **200** không ghi thêm (idempotent). Chỉ `CONTENT_EDITOR`. Lịch sử `attempt_items` giữ nguyên (BR-109-6) |

### Nhóm 7 — Dịch vụ nội bộ cho spec 011, 012 (FR-024…FR-025)

| Mã | Yêu cầu |
|---|---|
| **FR-024** | `learning.knowledge.KnowledgeTreeService` SHALL cung cấp: `List<KnowledgeNode> nodes()` (cả cây, có cạnh), `Optional<KnowledgeNode> node(String kid)`, `Map<Long, QuestionKids> kidsOfQuestions(Set<Long> questionIds)` (chính, phụ, `errorKid` theo khoá phương án — **một** truy vấn), `Optional<String> kidOfLexeme(long lexemeId)`, `Optional<String> kidOfTopic(long topicId)`. 011/012 cùng module `learning` nên gọi trực tiếp, không qua `learning.api` |
| **FR-025** | `List<Long> approvedQuestionIds(Set<String> kids, Integer hskLevel, int limit)` SHALL chỉ trả câu `APPROVED` (loại `PENDING_REVIEW`, `REJECTED`, `ARCHIVED`) có một trong các KID ở vai trò **chính**, lọc `hsk_level` nếu có, `limit` 1–200, sắp `id` |

### Nhóm 8 — Giao diện (FR-026…FR-028)

| Mã | Yêu cầu |
|---|---|
| **FR-026** | SCR-064 SHALL có: bộ lọc HSK (giao diện chỉ 1–3 theo `SUPPORTED_HSK_LEVELS`), loại câu, trạng thái, nút (chọn từ API cây), ô tìm; bảng cột `code`, tóm tắt, loại, HSK, nguồn/trạng thái, nút chính + phụ, "Đã dùng"; phân trang 10; giữ bộ lọc trên URL khi quay lại. Rỗng: "Chưa có câu hỏi trong phạm vi này" + nút xoá bộ lọc. Bấm dòng → SCR-059 |
| **FR-027** | SCR-059 SHALL hiện đủ nội dung, phương án (đánh dấu đáp án đúng), lời giải, nhãn. `TEACHER`: form sửa (FR-020), khi `used` khoá các trường chấm điểm kèm thông báo "Câu đã có người làm — không đổi được đáp án; tạo câu mới thay thế"; nút "Lưu thay đổi" có xác nhận trong trang. `CONTENT_EDITOR`: chỉ xem + nút "Lưu trữ câu hỏi" có xác nhận trong trang, ẩn khi đã `ARCHIVED`. Không có nút "Duyệt"/"Từ chối" (SCR-058 ngoài phạm vi) |
| **FR-028** | SCR-068 SHALL có 11 lựa chọn loại dữ liệu với nhãn §0.2; phần hướng dẫn cột đọc từ `GET templates` (002 FR-034), không hard-code cột |

### Key Entities

| Bảng | Dùng thế nào |
|---|---|
| `learning.knowledge_points` | Nút KID + cạnh (mảng) |
| `learning.question_knowledge_points` | Ma trận Q: câu ↔ nút, vai trò |
| `learning.questions` | Kho câu (`options` JSONB chỉ đọc, có `error_kid`) |
| `learning.topics`, `learning.lexemes` | Cột `knowledge_point_id` |
| `learning.attempt_items` | Chỉ đọc để biết câu đã dùng |
| `shared.audit_logs` | `QUESTION_UPDATE`, `QUESTION_ARCHIVE` qua `AuditService.record` |

---

## 4 · Data Model

V5 ở §0.1. Định dạng phần tử `questions.options`:

```json
[ { "key": "A", "text": "了", "is_correct": true },
  { "key": "B", "text": "过", "is_correct": false, "error_kid": "NP2-007", "error_note": "Nhầm 了 với 过" },
  { "key": "C", "text": "着", "is_correct": false } ]
```

`TRUE_FALSE` lưu `[{A,"Đúng"},{B,"Sai"}]` với `is_correct` theo `correct`. `FILL_BLANK` lưu `options=[]`, đáp án ở `correct_key`. `SENTENCE_ORDER` lưu các mảnh ở `options` (`is_correct=false`), thứ tự đúng ở `correct_key`.

### 4.1 · Mã lỗi dòng mới (bổ sung spec 002 §4.8)

| Mã | Thông báo tiếng Việt (mẫu) |
|---|---|
| `INVALID_KID` | "Mã nút phải dạng miền + cấp + số, ví dụ NP3-004" |
| `EDGE_SELF` | "Một nút không thể là tiên quyết/hỗ trợ của chính nó" |
| `EDGE_CYCLE` | "Quan hệ này tạo vòng lặp tiên quyết" |
| `PREREQ_LEVEL_HIGHER` | "Nút tiên quyết không được ở cấp cao hơn nút đích" |
| `TOO_MANY_PREREQS` | "Một nút có tối đa 4 tiên quyết trực tiếp" |
| `EDGE_REDUNDANT` | "Quan hệ thừa: đã đi được từ {from} tới {to} qua nút khác" |
| `EDGE_TYPE_CONFLICT` | "Cặp nút đã có quan hệ loại khác" |
| `DOMAIN_MISMATCH` | "Nội dung loại {target_type} chỉ gắn được vào nút miền {domain}" |
| `TOO_MANY_SECONDARY_KIDS` | "Một câu có tối đa 2 nút phụ" |
| `DUPLICATE_KID` | "Nút bị lặp hoặc nút phụ trùng nút chính" |
| `GRADING_CHANGE_NOT_ALLOWED` | "Câu đã có người làm — không đổi được cách chấm; tạo câu mới" |

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | Chưa đăng nhập | spec 001 |
| `FORBIDDEN` | 403 | Sai role | FR-001 |
| `CSRF_TOKEN_INVALID` | 403 | Thiếu/sai CSRF khi ghi bằng cookie | spec 001 |
| `VALIDATION_FAILED` | 422 | Tham số lọc/phân trang sai | FR-015, FR-017 |
| `KNOWLEDGE_NODE_NOT_FOUND` | 404 | KID không có hoặc sai dạng | FR-016 |
| `QUESTION_NOT_FOUND` | 404 | Câu không tồn tại | FR-019, FR-020, FR-023 |
| `MALFORMED_QUESTION` | 422 | Cấu trúc câu/đáp án/nhãn sai, kèm `details` | FR-021 |
| `KNOWLEDGE_NODE_REQUIRED` | 422 | Thiếu nút chính | FR-021 |
| `GRADING_CHANGE_NOT_ALLOWED` | 409 | Đổi cách chấm của câu đã dùng | FR-022 |
| `QUESTION_ARCHIVED` | 409 | Sửa câu đã lưu trữ | FR-022 |
| Lỗi luồng nhập | | Theo spec 002 §5 | FR-002…FR-014 |

**Mã mới trong `ErrorCode.java`** (thứ tự cuối theo thứ tự merge thực tế — luật L2; thêm ngay trước `INTERNAL_ERROR`, theo thứ tự): `KNOWLEDGE_NODE_NOT_FOUND`, `QUESTION_NOT_FOUND`, `MALFORMED_QUESTION`, `KNOWLEDGE_NODE_REQUIRED`, `GRADING_CHANGE_NOT_ALLOWED`, `QUESTION_ARCHIVED`.
Câu hỏi là tài nguyên quản trị, không thuộc người dùng nên trả **404** (UC-109), không áp `BUS-01`.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| **AC-01** | `contracts/openapi-knowledge.yaml`; enum nhập sửa ở `openapi-import.yaml` cùng PR task T-06 |
| **AC-03** | Chỉ đọc/ghi bảng `learning`; tên người dùng không cần; audit qua `shared.audit.AuditService` |
| **AC-05** | `V5__knowledge_tree.sql`, không sửa V1–V4 |
| **AC-06** | `domain`, `role`, `edge_type`, `target_type` là `VARCHAR + CHECK` / enum Java |
| **AC-09** | `options` chỉ đọc; không lọc SQL theo `error_kid` (chỉ đọc trong Java) |
| **BUS-07** | Câu `source='AI'` giữ `PENDING_REVIEW`; spec này không đổi trạng thái duyệt |
| **BUS-10** | Câu không xoá, chỉ `ARCHIVED` (BR-109-6) |
| **HR-07** | `q` tham số hoá; DTO `@Valid` |
| **HR-08 · BR-110-12** | Không commit dữ liệu cây/câu thật; test dùng dữ liệu tự soạn |
| Package | `learning.knowledge` (`KnowledgePoint`, `QuestionKnowledgePoint`, `Question` entity; `KnowledgeTreeService`, `PrereqGraph` (kiểm DAG thuần), `QuestionAdminService`, `web.KnowledgeTreeController`, `web.QuestionAdminController`) · `learning.content.importer` (4 validator + 4 writer) |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | `GET /api/knowledge-tree` (600 nút) | < 200 ms p95 |
| `NFR-P02` | `GET /api/admin/questions?kid=` trên 5 000 câu | < 100 ms p95 (`NFR-P03` CONTEXT) |
| `NFR-P03` | Kiểm tra 2 000 dòng `KNOWLEDGE_EDGE` | < 10 s p95 (đồ thị dựng một lần trong bộ nhớ) |
| `NFR-P04` | Xác nhận 5 000 dòng `QUESTION` | < 30 s p95 (như 002 NFR-P02) |

---

## 8 · Constitution Check

| Mã | Kết quả |
|---|---|
| **HR-07** | ✅ Validate theo FR-003…FR-013, FR-017, FR-020; SQL tham số hoá |
| **HR-08** | ✅ Không có dữ liệu cây/câu thật trong repo (Q5) |
| **HR-09** | ✅ Lỗi theo `{error_code, message, request_id}`; `ROW_WRITE_FAILED` không lộ SQL |
| **HR-05** | ✅ Ghi qua cookie cần CSRF |
| **AC-03** | ✅ Không đọc bảng module khác |
| **AC-05** | ✅ Chỉ thêm V5 |
| **AC-06** | ✅ `VARCHAR + CHECK` |
| **AC-09** | ✅ `options` chỉ đọc; không truy vấn điều kiện trên JSONB |
| **BUS-04** | ✅ Không đụng lịch ôn |
| **BUS-05** | ✅ Không đụng cổng 90% chủ đề |
| **BUS-07** | ✅ Câu AI không đổi trạng thái; chỉ `TEACHER` sửa nội dung |
| **BUS-10** | ✅ Lưu trữ thay xoá |
| Glossary `business.md` §9 "Knowledge point = 1 từ", §4 "mastery FSRS" | ⚠️ **Mâu thuẫn — chờ RFC Q1.** Spec này dùng `knowledge_points` làm nút KID v2.0 |
| `database.md` §13 "không đụng `knowledge_points`, `question_knowledge_points`" | ⚠️ Mở rộng (thêm cột), không xoá dữ liệu; bảng đang rỗng. Ghi vào RFC Q1 |
| §Bảy actor "`TEACHER` duy nhất sửa nội dung câu" | ✅ Q3; lệch UC-109/SCR-059 ghi ở §9 |

---

## 9 · Quyết định và câu cần nhóm xác nhận

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | **RFC**: knowledge point = nút KID v2.0, mastery = MP-1.0; giữ `BUS-04` (FSRS) cho lịch ôn mục từ, `BUS-05` (90%) cho chủ đề; sửa `business.md` §4/§9, `CONTEXT.md` §3.1, feature-tree 3.1, `database.md` §4.4/§13 | **Leader chốt 2026-11-02 — chờ cả nhóm đồng thuận** (điều kiện APPROVED) |
| Q2 | Không thêm bảng; mở rộng `knowledge_points`, `question_knowledge_points`, `questions`, `topics`, `lexemes` | Chốt 2026-11-02 |
| Q3 | Chỉ `TEACHER` sửa nội dung + nhãn; `CONTENT_EDITOR` xem + lưu trữ; `TEACHER` xem SCR-064 chỉ đọc. Lệch UC-109 BR-109-1 và SCR-059 §5/§11 — constitution thắng | Chốt 2026-11-02 |
| Q4 | Câu nhập từ file `APPROVED` ngay; câu AI `PENDING_REVIEW` | Chốt 2026-11-02 |
| Q5 | Dữ liệu cây thật không commit; nạp qua SCR-068 | Chốt 2026-11-02 |
| Q6 | Nối nội dung bằng `topics/lexemes.knowledge_point_id` + mẫu `NODE_LINK` (thay `kid_map` của plan) | Chốt 2026-11-02 |
| Q7 | Mã lỗi gắn từng phương án nhiễu (`error_kid`, `error_note`), không thêm bảng; khi có tài liệu mã lỗi chính thức thì chuyển đổi | Chốt 2026-11-02 |
| Q8 | `questions.difficulty` 1–3 | Chốt 2026-11-02 |
| Q9 | Migration V5 | Chốt 2026-11-02 |
| Q10 | `questions.code` UNIQUE | Chốt 2026-11-02 |
| Q11 | API cây thuộc 010 | Chốt 2026-11-02 |
| Q12 | Bỏ khoá sửa khi có cuộc thi | Chốt 2026-11-02 |
| Q14 | FE trước BE (tasks.md) | Chốt 2026-11-02 |
| OQ-1 | Tên tiếng Việt của 6 miền `NA CH TV NP NL VB` theo v2.0 (dùng cho nhãn giao diện) | **Mở** — leader đối chiếu PDF; FE tạm hiện mã miền |
| OQ-2 | Mã lỗi danh mục chính thức của Học Bá (khi có) | Mở — chuyển đổi theo Q7 |

---

## 10 · Móc nối cây tri thức

| Chỗ | Bây giờ | Spec sau |
|---|---|---|
| Mastery theo nút | Không có | 011: listener `ItemGradedEvent`/`SessionFinishedEvent` (003 §0.9) dùng `kidsOfQuestions`, `kidOfLexeme`, `kidOfTopic` |
| Đề thi, bài xếp trình độ | Không có | 012: `approvedQuestionIds`, `kidsOfQuestions` (phân tích lỗi theo nút, `errorKid`) |
| AI sinh câu theo nút yếu | Không có | Sau demo: câu `source='AI'`, `PENDING_REVIEW`, SCR-058 |
| Cây chính thức của Học Bá | Nạp bản nháp phụ lục B | Nhập đè theo `kid` qua SCR-068 |

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Một phiên bản cây, không lịch sử | Q2 ít bảng; đổi cây = nhập đè theo `kid`. Mastery (011) tính lại từ bài làm |
| Không xoá được nút/cạnh, không đổi loại cạnh qua nhập | Cây ít đổi trong đợt; leader sửa tay qua migration nếu bắt buộc |
| `EDGE_REDUNDANT` phụ thuộc thứ tự nhập (cạnh mới có thể làm cạnh cũ thành thừa) | Test V03 sau khi nạp phát hiện; cây v2.0 đã được biên soạn không thừa |
| Mảng `prereq_ids` không có khoá ngoại | Như `topics.prereq_ids` (`database.md` §4.5); kiểm khi nhập + V01–V04 |
| Đổi nhãn câu đã dùng làm đổi kết quả tính lại mastery | BR-109-5 cho phép; có `audit_logs` |
| Không thống kê lỗi toàn hệ thống theo `error_kid` | `AC-09`; chỉ phân tích trong từng bài |

---

## 12 · Màn hình

| SCR | Route | Guard | API | Field |
|---|---|---|---|---|
| SCR-064 Question Bank | `/content/questions` | `CONTENT_EDITOR`, `TEACHER` | `GET /api/admin/questions`, `GET /api/knowledge-tree` | `screen-fields.md` dòng 31609 |
| SCR-059 Question Edit | `/content/questions/:id` | `CONTENT_EDITOR`, `TEACHER` | `GET`, `PUT /api/admin/questions/{id}`, `PATCH …/archive` | dòng 29336 |
| SCR-068 Learning Data Import | `/content/imports` (đã có) | `CONTENT_EDITOR` | spec 002 | dòng 33366 |

| Open issue | Chốt |
|---|---|
| SCR-064: chưa có contract tìm/lọc | FR-017 |
| SCR-064/059: HSK UI 1–6 vs SQL 1–9 | DB/API 1–9; giao diện chọn 1–3 (`AGENTS.md` §12) |
| SCR-059: contract đáp án theo loại câu | FR-009 |
| SCR-059: ví dụ API review khác nhau UC-108/137 | Ngoài phạm vi (SCR-058) |

Trạng thái mỗi màn: Loading (skeleton bảng/form) · Empty (FR-026) · Error (thông báo + "Thử lại") · Read-only (`CONTENT_EDITOR` ở SCR-059, câu `ARCHIVED`).

---

## 13 · API Contract

[`contracts/openapi-knowledge.yaml`](contracts/openapi-knowledge.yaml)

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/knowledge-tree` | 200 `KnowledgeTree` | 401, 403, 422 |
| `GET /api/knowledge-tree/nodes/{kid}` | 200 `KnowledgeNodeDetail` | 401, 403, 404 |
| `GET /api/admin/questions` | 200 `QuestionPage` | 401, 403, 422 |
| `GET /api/admin/questions/{id}` | 200 `QuestionDetail` | 401, 403, 404 |
| `PUT /api/admin/questions/{id}` | 200 `QuestionDetail` | 401, 403, 404, 409, 422 |
| `PATCH /api/admin/questions/{id}/archive` | 200 `QuestionDetail` | 401, 403, 404 |
| Mẫu nhập (`/api/admin/imports/**`) | Spec 002; enum `dataType` + mã lỗi dòng mở rộng | Spec 002 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001 | `LEARNER` gọi `GET /api/admin/questions` · T 403; `CONTENT_EDITOR` gọi `PUT` · T 403; `TEACHER` gọi `PATCH archive` · T 403; `ADMIN` gọi `GET /api/admin/questions` · T 403; không token gọi cây · T 401; `PUT` qua cookie thiếu CSRF · T 403 `CSRF_TOKEN_INVALID` |
| AT-02 | FR-002 | `GET templates` · T 11 mẫu đúng thứ tự, `dependsOn` đúng §0.2; CSV mẫu mỗi loại mới có header khớp `columns` |
| AT-03 | FR-003 | `NP10-001`, `XX1-001`, `NP3-04` · T `INVALID_KID`; `np3-004` · T hợp lệ (E10); `name_zh="abc"` · T `NOT_CJK`; `TV2-001` · T `domain=TV`, `hsk_level=2` |
| AT-04 | FR-004, E7 | Nút có 2 tiên quyết, nhập lại đổi `name_vi` · T `UPDATE`, `prereq_ids` giữ nguyên |
| AT-05 | FR-005 | `from_kid` chưa nhập · T `REFERENCE_NOT_FOUND` |
| AT-06 | FR-006 | Mỗi ca một dòng lỗi đúng mã: tự trỏ; tiên quyết cấp 3 → nút cấp 2; DB có `A→B`, file `B→A` (E1); file `A→B`, `B→A` (E2: dòng 2 lỗi); nút đã có 4 tiên quyết; DB có `A→B→C`, file `A→C` |
| AT-07 | FR-006, E3 | Xem trước file `B→A` hợp lệ; trước khi xác nhận ghi cạnh `A→B` vào DB · T xác nhận: dòng `EDGE_CYCLE` trong `error_rows`, dòng khác vẫn ghi |
| AT-08 | FR-007, E8, E9 | `SUPPORT` tự trỏ · T `EDGE_SELF`; cặp đã `PREREQ` nhập `SUPPORT` · T `EDGE_TYPE_CONFLICT`; nhập lại cạnh có sẵn · T `UNCHANGED`; ghi xong · T `support_ids` của `to` chứa id `from` |
| AT-09 | FR-008, FR-009 | Mỗi `qtype` (a)–(d) một dòng hợp lệ · T ghi đúng `options`/`correct_key` (§4); `MULTIPLE_CHOICE` đáp án trỏ phương án rỗng · T `INVALID_ANSWER`; `TRUE_FALSE` có `option_a` · T `INVALID_VALUE`; `FILL_BLANK` không có `___` · T `INVALID_VALUE`; `SENTENCE_ORDER` `correct=CAB` khi có 4 mảnh · T `INVALID_ANSWER`; `IMAGE_MATCH` thiếu `image_url` · T `REQUIRED_MISSING`; `ESSAY` · T `INVALID_VALUE` |
| AT-10 | FR-010 | Thiếu `kid_primary` · T `REQUIRED_MISSING`; 3 KID phụ · T `TOO_MANY_SECONDARY_KIDS`; phụ trùng chính · T `DUPLICATE_KID`; `distractor_errors` trỏ khoá đúng · T `INVALID_VALUE` |
| AT-11 | FR-011, SC-002 | Câu mới · T `APPROVED`, `IMPORT`, đúng 1 dòng `PRIMARY`; chèn tay dòng `PRIMARY` thứ 2 · T DB từ chối (`uq_qkp_primary`); `options` có `error_kid` đúng khoá |
| AT-12 | FR-012, E4, E5 | Câu đã có `attempt_items`: nhập lại đổi `correct` · T `GRADING_CHANGE_NOT_ALLOWED`, các dòng khác vẫn ghi; đổi `explanation` · T `UPDATE`; câu `ARCHIVED` nhập lại · T vẫn `ARCHIVED`; nhãn được thay |
| AT-13 | FR-013 | `GRAMMAR` → nút `TV` · T `DOMAIN_MISMATCH`; `TOPIC` mã không có · T `REFERENCE_NOT_FOUND`; `CHAR` → `CH1-002` · T hợp lệ |
| AT-14 | FR-014 | Gắn chủ đề lần 2 cùng nút · T `UNCHANGED`; khác nút · T `UPDATE`; `GET /api/topics` của spec 003 trả như trước (hồi quy) |
| AT-15 | FR-015 | Cây fixture · T thứ tự miền/cấp/kid đúng, `prereqKids` đúng; `domain=XX` · T 422; `hskLevel=8` · T 422; JSON không có khoá câu hỏi |
| AT-16 | FR-016 | Nút có 2 nút phụ thuộc, 1 chủ đề, 3 câu `APPROVED` + 1 `ARCHIVED` · T `dependentKids` 2, `approvedQuestionCount=3`; KID không có / `abc` · T 404 |
| AT-17 | FR-017 | Lọc từng tham số · T đúng tập; `size=101`, `q="a"`, `hskLevel=0` · T 422; `kid` không có · T trang rỗng 200 |
| AT-18 | FR-018 | Trang 10 câu · T `used` đúng, `stemSummary` ≤ 120; số câu SQL không tăng theo số câu trên trang (đếm bằng `StatementInspector`) |
| AT-19 | FR-019 | `TEACHER`, `CONTENT_EDITOR` · T có `isCorrect`, nhãn; id không có · T 404 |
| AT-20 | FR-020 | `TEACHER` sửa lời giải + nhãn · T 200, DB đổi; body có `qtype`, `status` · T bị bỏ qua |
| AT-21 | FR-021 | Hai đáp án đúng · T 422 `MALFORMED_QUESTION` có `details`; KID phụ không có · T 422 `MALFORMED_QUESTION`; thiếu `primaryKid` · T 422 `KNOWLEDGE_NODE_REQUIRED`; DB không đổi |
| AT-22 | FR-022, E6 | Câu đã dùng đổi đáp án · T 409, DB không đổi; câu `ARCHIVED` · T 409 `QUESTION_ARCHIVED`; câu AI `PENDING_REVIEW` sửa · T vẫn `PENDING_REVIEW`; `audit_logs` có `QUESTION_UPDATE` với `before`/`after` |
| AT-23 | FR-023 | Lưu trữ · T `ARCHIVED`, 1 dòng audit; gọi lại · T 200, không thêm audit; `attempt_items` giữ nguyên |
| AT-24 | FR-024 | `kidsOfQuestions` cho 50 câu · T 1 truy vấn, đúng chính/phụ/`errorKid`; `kidOfLexeme`, `kidOfTopic` đúng sau `NODE_LINK`; không gắn · T rỗng |
| AT-25 | FR-025 | Nút có câu `APPROVED`, `ARCHIVED`, `PENDING_REVIEW`, câu chỉ có nút ở vai trò phụ · T chỉ câu `APPROVED` vai trò chính; `limit=0` · T `IllegalArgumentException` |
| AT-26 | FR-026 (FE) | Vitest: lọc cập nhật URL; rỗng có nút xoá bộ lọc; lỗi có "Thử lại"; HSK chỉ 1–3 |
| AT-27 | FR-027 (FE) | Vitest: `TEACHER` thấy form, câu `used` khoá trường chấm điểm và hiện thông báo; `CONTENT_EDITOR` chỉ xem + "Lưu trữ" có bước xác nhận trong trang; không có nút duyệt |
| AT-28 | FR-028 (FE) | Vitest: 11 lựa chọn đúng nhãn; hướng dẫn cột lấy từ API |
| AT-29 | SC-001, §0.1 | Nạp cây fixture (~60 nút, ~80 cạnh) · T V01 vòng lặp, V02 tiên quyết cấp cao hơn, V03 bắc cầu thừa, V04 > 4 tiên quyết đều 0 dòng (truy vấn trên PostgreSQL Testcontainers) |
| AT-30 | §0.1 | Migration V5 trên Testcontainers: ràng buộc `ck_kp_kid`, `ck_kp_no_lexeme`, `ck_q_import_code`, `uq_qkp_primary` chặn dữ liệu sai; entity spec 002/003 khởi động với `ddl-auto: validate` |

---

## 15 · Gói việc

[`tasks.md`](tasks.md)

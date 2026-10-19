# Thay đổi DB: cây năng lực v2.0 so với schema hiện tại

> 18/10/2026 · plan [`261017-1654-cay-nang-luc-han-ngu`](../plan.md) · phase 2–4
> So sánh: đặc tả phụ lục F (`Cay-Nang-Luc-Han-Ngu_HSK1-9_Dac-ta-v2.0.pdf` tr. 95–99)
> với [`docs/reference/database.md`](../../../docs/reference/database.md) (15/10, 33 bảng) và `db/migration/V1, V2` (01/10)
> Giả định: hướng B (áp phần lõi), Leitner cho mục từ, lược đồ HSK 1–9

---

## 0 · Ba sự thật về DB hiện tại

| # | Sự thật | Hệ quả |
|---|---|---|
| 1 | `V1__core_schema.sql` **đã lệch** `database.md`: còn bảng `roles`, thiếu `plans`, `pron_stages`, `videos`, `dictation_attempts`, `shadowing_attempts` | Dù không có cây, migration cũng phải đồng bộ lại |
| 2 | Migration nằm ở `db/migration/` (gốc repo); Flyway mặc định đọc `classpath:db/migration` | **Flyway chưa từng chạy V1/V2** qua app → không có `flyway_schema_history` thật |
| 3 | Code Java chỉ là khung (`.gitkeep`), chưa có entity nào | Đổi schema bây giờ không làm vỡ code |

→ Đây là thời điểm **viết lại baseline** an toàn nhất, *nếu* chưa có DB dùng chung nào chạy V1 bằng tay (xem câu hỏi 1 cuối file).

---

## 1 · Tổng quan thay đổi

| Hành động | Bảng | Số |
|---|---|---|
| ❌ Bỏ | `knowledge_points` · `question_knowledge_points` | −2 |
| ➕ Thêm | `kt_version` · `kt_node` · `kt_edge` · `item_kid` · `error_code` · `mp_policy` · `evidence` · `mastery` · `lexical_state` | +9 |
| ✏️ Sửa | `lexemes` · `topics` · `topic_items` · `questions` · `exams` · `user_progress` · `attempts` · `attempt_items` · `pron_stages` · `videos` · `service_requests` | 11 |
| ✅ Giữ nguyên | 20 bảng còn lại (auth, tiền, cộng đồng, thư viện, AI chat, audit…) | — |

**33 bảng → 40 bảng.** Toàn bộ thay đổi nằm trong schema `learning`; `auth`, `community`, `shared` không đổi.

---

## 2 · Ma trận từng bảng

### 2.1 · Bỏ

| Bảng | Vì sao bỏ | Thay bằng |
|---|---|---|
| `knowledge_points` | Coi 1 lexeme = 1 điểm kiến thức (`lexeme_id`); `skill_type` trộn kỹ năng với miền. Trái mục 3.3 "từng từ vựng không làm nút" | `kt_node` |
| `question_knowledge_points` | Không phân vai chính/phụ, không có phiên bản cây | `item_kid` |

> `database.md` đang ghi `question_knowledge_points` là **"không được đụng — sửa sau phải nhập lại dữ liệu"**.
> Đúng vì thế mà phải đổi **ngay bây giờ**, khi chưa có dữ liệu nào được gắn nhãn.

### 2.2 · Thêm

| Bảng | Vai trò | Nguồn đặc tả |
|---|---|---|
| `kt_version` | Phiên bản cây, đúng 1 bản active | F.1 `kt_version` (rút gọn: bỏ quy trình duyệt) |
| `kt_node` | Nút KID: miền, cụm, cấp, can-do, ví dụ, kỹ năng | F.1 `kt_node` |
| `kt_edge` | Cạnh `prereq` / `support` | F.1 `kt_edge` |
| `item_kid` | Ma trận Q: câu ↔ KID, đúng 1 KID chính | F.2 `item_kid` |
| `error_code` | Mã lỗi `E-NP-07` → 1 KID | F.1 `kt_error_type` |
| `mp_policy` | Tham số MP-1.0 có phiên bản | F.3 `mp_policy` |
| `evidence` | Minh chứng append-only | F.3 `evidence` |
| `mastery` | Mastery hiện tại theo (người học, KID) — bộ đệm tính lại được | F.3 `mastery_current` (chỉ loại *learning*) |
| `lexical_state` | Hộp Leitner theo (người học, từ/chữ, đường) | F.3 `lexical_state` |

**Không lấy từ phụ lục F** (YAGNI cho CNHSK): `kt_node_lesson` (không có buổi học), `kt_node_migration` (chưa tách/gộp nút), `kt_rubric*` (rubric để ở `service_requests.payload` khi chấm), `task_target_kid`, `mastery_history`, `recommendation` (tính lúc đọc), `processed_event` (không có Event Bus), `lexical_item` (đã có `lexemes`).

### 2.3 · Sửa

| Bảng | Thay đổi | Lý do |
|---|---|---|
| `lexemes` | **+ `node_kid`** (nút TV/CH/NP chứa mục này) · **+ `lexical_code`** (`W2-0388`, `Z1-0123`) · **+ `handwrite_required`** | Tầng 4 gộp lên nút. `GRAMMAR` **giữ lại** làm mục tra cứu ngữ pháp, trỏ về nút NP (210 điểm ngữ pháp → ~85–110 nút) |
| `topics` | **− `prereq_ids`** · **+ `node_kid`** | Tiên quyết chuyển sang `kt_edge`. Chủ đề = cách nhóm hiển thị của một nút TV |
| `topic_items` | **− `knowledge_point_id`** → chỉ còn `(topic_id, lexeme_id)`, đổi lại thành PK | Bảng gốc phải dùng 2 partial unique index vì cột nullable; bỏ cột thì PK thường được |
| `questions` | **+ `difficulty`** (1 dễ · 2 TB · 3 khó) · `options[].error_code` trong JSONB | Gợi ý ôn và chẩn đoán chọn câu theo mức khó (mục 5.7, 5.8); phương án nhiễu gắn mã lỗi |
| `exams` | `hsk_level` CHECK `1..6` → `1..9` | Đang lệch với mọi bảng khác (1..9); theo quyết định Q3 |
| `user_progress` | **− `mastery`, `stability`, `difficulty`, `review_count`, `lapse_count`** · **+ `box`** · `target_type` bỏ `KNOWLEDGE_POINT`, `TOPIC` | Mastery nút sang `mastery`; thẻ từ có lexeme sang `lexical_state`; bảng chỉ còn video, tầng phát âm, thẻ tự do |
| `attempts` | `kind` + `'DIAGNOSTIC'` | Bài chẩn đoán đầu vào |
| `attempt_items` | **+ `score`** (0–100) · **+ `error_code`** | Câu nhiều ý chấm theo tỉ lệ; ghi mã lỗi đã chọn để phân bổ minh chứng |
| `pron_stages` | **+ `node_kids VARCHAR(10)[]`** | 8 tầng phát âm ↔ nút NA (vd tầng thanh điệu → `NA1-006`, `NA1-008`) |
| `videos` | **+ `node_kids VARCHAR(10)[]`** | Học liệu gắn KID để gợi ý ôn kèm video (mục 3.7) |
| `service_requests` | `payload.knowledge_point_ids` → `payload.kids` | Job AI sinh câu theo nút yếu |

---

## 3 · DDL đề xuất

Theo quy ước đang dùng trong `database.md`: `VARCHAR + CHECK` thay enum, comment không dấu, schema `learning` mặc định.

### 3.1 · Cây

```sql
CREATE TABLE kt_version (
  tree_version VARCHAR(20) PRIMARY KEY,              -- 'KT-2026.0-draft'
  source       VARCHAR(50) NOT NULL,                 -- 'phu-luc-B' | 'hocba-sheet-2026-11'
  is_active    BOOLEAN NOT NULL DEFAULT false,
  imported_by  BIGINT REFERENCES auth.users(id),
  imported_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Dung mot phien ban active tai moi thoi diem
CREATE UNIQUE INDEX uq_kt_active ON kt_version(is_active) WHERE is_active;

CREATE TABLE kt_node (
  tree_version VARCHAR(20) NOT NULL REFERENCES kt_version(tree_version),
  id           VARCHAR(10) NOT NULL CHECK (id ~ '^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$'),
  type         VARCHAR(2)  NOT NULL CHECK (type IN ('KT','NL')),
  form         VARCHAR(10) CHECK (form IN ('quy_tac','tap_muc')),
  domain       VARCHAR(2)  NOT NULL CHECK (domain IN ('NA','CH','TV','NP','NL','VB')),
  cluster      VARCHAR(100) NOT NULL,
  hsk_level    SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 7),   -- 7 = bac 7-9
  name_vi      VARCHAR(200) NOT NULL,
  name_zh      VARCHAR(200) NOT NULL,
  can_do       TEXT,                                 -- phu luc B chua co; bat buoc o ban chinh thuc
  pattern      TEXT,
  skills       VARCHAR(10)[] NOT NULL DEFAULT '{}',  -- {'NGHE','NOI','DOC','VIET','DICH'}
  examples_ok  JSONB NOT NULL DEFAULT '[]',
  examples_err JSONB NOT NULL DEFAULT '[]',          -- [{"text":"...","error":"E-NP-07"}]
  status       VARCHAR(12) NOT NULL DEFAULT 'approved'
               CHECK (status IN ('draft','approved','deprecated')),
  PRIMARY KEY (tree_version, id),
  CONSTRAINT ck_node_form   CHECK (type = 'NL' OR form IS NOT NULL),
  CONSTRAINT ck_node_domain CHECK (left(id, 2) = domain)
);
CREATE INDEX ix_node_domain ON kt_node(tree_version, domain, hsk_level);

CREATE TABLE kt_edge (
  tree_version VARCHAR(20) NOT NULL,
  from_id      VARCHAR(10) NOT NULL,     -- A la tien quyet / ho tro cua B
  to_id        VARCHAR(10) NOT NULL,
  type         VARCHAR(8)  NOT NULL CHECK (type IN ('prereq','support')),
  PRIMARY KEY (tree_version, from_id, to_id),
  FOREIGN KEY (tree_version, from_id) REFERENCES kt_node(tree_version, id),
  FOREIGN KEY (tree_version, to_id)   REFERENCES kt_node(tree_version, id),
  CONSTRAINT ck_edge_self CHECK (from_id <> to_id)
);
-- Duyet nguoc (tim tien quyet cua B) cho bien hoc tap va chan doan
CREATE INDEX ix_edge_to ON kt_edge(tree_version, to_id, type);
```

`ck_node_domain` là thêm của CNHSK: chặn lỗi `id = 'NP3-004'` mà `domain = 'TV'`. Vòng lặp, cấp tiên quyết, bắc cầu thừa, > 4 tiên quyết **không** kiểm được bằng CHECK → kiểm ở `DagValidator` khi nhập + truy vấn ở mục 5.

### 3.2 · Nối nội dung

```sql
CREATE TABLE item_kid (
  question_id  BIGINT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
  tree_version VARCHAR(20) NOT NULL,
  kid          VARCHAR(10) NOT NULL,
  role         VARCHAR(10) NOT NULL CHECK (role IN ('primary','secondary')),
  tagged_by    BIGINT REFERENCES auth.users(id),     -- giao vien xac nhan khi duyet
  PRIMARY KEY (question_id, tree_version, kid),
  FOREIGN KEY (tree_version, kid) REFERENCES kt_node(tree_version, id)
);
-- Dung 1 KID chinh moi cau (dac ta muc 3.7)
CREATE UNIQUE INDEX uq_item_primary ON item_kid(question_id, tree_version) WHERE role = 'primary';
-- Duong nong UC-040/041: tu nut yeu tim cau luyen
CREATE INDEX ix_item_kid ON item_kid(tree_version, kid, role);

CREATE TABLE error_code (
  tree_version VARCHAR(20) NOT NULL REFERENCES kt_version(tree_version),
  code         VARCHAR(10) NOT NULL CHECK (code ~ '^E-(NA|CH|TV|NP|NL|VB)-[0-9]{2}$'),
  name_vi      VARCHAR(200) NOT NULL,
  kid          VARCHAR(10),            -- NULL: lay KID muc tieu cua cau
  severity     SMALLINT NOT NULL CHECK (severity IN (1,2,3)),
  example_err  TEXT NOT NULL,
  fix_hint     TEXT NOT NULL,
  PRIMARY KEY (tree_version, code),
  FOREIGN KEY (tree_version, kid) REFERENCES kt_node(tree_version, id)
);
```

> **Giới hạn đã biết:** "≤ 2 KID phụ mỗi câu" không viết được bằng CHECK. Kiểm ở service khi duyệt câu và bằng truy vấn V07 ở mục 5.

### 3.3 · Mastery

```sql
CREATE TABLE mp_policy (
  policy_version VARCHAR(10) PRIMARY KEY,             -- 'MP-1.0'
  params         JSONB NOT NULL,                      -- trong so, cua so 8/3, ban ra, nguong, han on
  is_active      BOOLEAN NOT NULL DEFAULT false,
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_mp_active ON mp_policy(is_active) WHERE is_active;

-- Append-only: khong UPDATE score, chi doi valid khi cau bi ngung / cham lai
CREATE TABLE evidence (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL REFERENCES auth.users(id),
  tree_version    VARCHAR(20) NOT NULL,
  kid             VARCHAR(10) NOT NULL,
  attempt_item_id BIGINT REFERENCES attempt_items(id),   -- NULL voi tieu chi rubric cua bai viet
  service_request_id BIGINT REFERENCES service_requests(id),
  context   VARCHAR(12) NOT NULL CHECK (context IN ('exam','quiz','diagnostic','graded','practice')),
  item_form VARCHAR(10) NOT NULL CHECK (item_form IN ('open','mcq4','mcq3','binary','criterion')),
  role      VARCHAR(10) NOT NULL CHECK (role IN ('primary','secondary','criterion','error')),
  score     NUMERIC(5,2) NOT NULL CHECK (score BETWEEN 0 AND 100),
  w_static  NUMERIC(5,3) NOT NULL CHECK (w_static > 0),   -- boi canh x dang cau x vai tro, chua co r(t)
  valid     BOOLEAN NOT NULL,
  invalid_reason VARCHAR(30),                            -- 'H2_flag','H4_seen_7d','regraded','question_retired'
  occurred_at    TIMESTAMPTZ NOT NULL,
  policy_version VARCHAR(10) NOT NULL REFERENCES mp_policy(policy_version),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (tree_version, kid) REFERENCES kt_node(tree_version, id),
  CONSTRAINT ck_ev_source  CHECK (attempt_item_id IS NOT NULL OR service_request_id IS NOT NULL),
  CONSTRAINT ck_ev_invalid CHECK (valid OR invalid_reason IS NOT NULL)
);
-- Duong nong nhat: 8 minh chung hop le gan nhat cua (nguoi hoc, nut)
CREATE INDEX ix_ev_window ON evidence(user_id, kid, occurred_at DESC) WHERE valid;
-- Quy tac H4: da lam dung cau nay trong 7 ngay chua
CREATE INDEX ix_ev_item   ON evidence(attempt_item_id);

CREATE TABLE mastery (
  user_id      BIGINT NOT NULL REFERENCES auth.users(id),
  tree_version VARCHAR(20) NOT NULL,
  kid          VARCHAR(10) NOT NULL,
  mastery      NUMERIC(5,2) CHECK (mastery BETWEEN 0 AND 100),   -- NULL khi chua co minh chung
  confidence   NUMERIC(6,3) NOT NULL DEFAULT 0,                  -- tong w
  n_evidence   SMALLINT NOT NULL DEFAULT 0,
  state        VARCHAR(12) NOT NULL DEFAULT 'notyet' CHECK (state IN
               ('notyet','insufficient','review','consolidate','extend','inferred')),
  due_at       TIMESTAMPTZ,
  evidence_ids BIGINT[] NOT NULL DEFAULT '{}',                   -- giai thich duoc con so
  policy_version VARCHAR(10) NOT NULL,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, tree_version, kid),
  FOREIGN KEY (tree_version, kid) REFERENCES kt_node(tree_version, id),
  CONSTRAINT ck_m_null CHECK (state IN ('notyet','inferred') OR mastery IS NOT NULL)
);
CREATE INDEX ix_m_due   ON mastery(user_id, due_at) WHERE due_at IS NOT NULL;
CREATE INDEX ix_m_state ON mastery(user_id, state);

CREATE TABLE lexical_state (
  user_id   BIGINT NOT NULL REFERENCES auth.users(id),
  lexeme_id BIGINT NOT NULL REFERENCES lexemes(id),
  track     VARCHAR(12) NOT NULL DEFAULT 'recognition' CHECK (track IN ('recognition','production')),
  box       SMALLINT NOT NULL DEFAULT 1 CHECK (box BETWEEN 1 AND 5),
  next_due  DATE,                       -- ngay gio Viet Nam
  last_result BOOLEAN,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, lexeme_id, track)
);
CREATE INDEX ix_lex_due ON lexical_state(user_id, next_due);
```

Phụ lục F phân vùng `lexical_state` theo `HASH (user_id)`. **Không làm** ở đồ án: vài nghìn người × 2.245 từ sơ cấp × 1 đường ≈ vài triệu dòng, index thường đủ.

### 3.4 · Sửa bảng cũ

```sql
-- lexemes: tang 4, gop len nut. GRAMMAR giu lam muc tra cuu, tro ve nut NP
ALTER TABLE lexemes
  ADD COLUMN node_kid           VARCHAR(10),
  ADD COLUMN lexical_code       VARCHAR(10) UNIQUE,         -- 'W2-0388' | 'Z1-0123'
  ADD COLUMN handwrite_required BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX ix_lex_node ON lexemes(node_kid);
-- node_kid khong co FK vi kt_node co PK kep (tree_version, id); kiem bang truy van V06

-- topics: tien quyet chuyen sang kt_edge
ALTER TABLE topics DROP COLUMN prereq_ids, ADD COLUMN node_kid VARCHAR(10);

-- topic_items: chi con chu de <-> tu
DROP INDEX uq_ti_lex; DROP INDEX uq_ti_kp;
ALTER TABLE topic_items DROP COLUMN knowledge_point_id,
  ALTER COLUMN lexeme_id SET NOT NULL, ADD PRIMARY KEY (topic_id, lexeme_id);

-- questions: muc kho cho goi y on va chan doan
ALTER TABLE questions ADD COLUMN difficulty SMALLINT NOT NULL DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3);
-- options JSONB: them "error_code" cho phuong an nhieu: [{key,text,is_correct,error_code}]

-- exams: thong nhat voi cac bang khac
ALTER TABLE exams DROP CONSTRAINT exams_hsk_level_check,
  ADD CONSTRAINT exams_hsk_level_check CHECK (hsk_level BETWEEN 1 AND 9);

-- user_progress: bo FSRS, chi con video / tang phat am / the tu do (khong co lexeme)
ALTER TABLE user_progress
  DROP COLUMN mastery, DROP COLUMN stability, DROP COLUMN difficulty,
  DROP COLUMN review_count, DROP COLUMN lapse_count,
  ADD COLUMN box SMALLINT CHECK (box BETWEEN 1 AND 5);      -- chi FLASHCARD
DROP INDEX ix_prog_mastery;
ALTER TABLE user_progress DROP CONSTRAINT user_progress_target_type_check,
  ADD CONSTRAINT user_progress_target_type_check CHECK (target_type IN ('PRON_STAGE','FLASHCARD','VIDEO'));

-- attempts: bai chan doan dau vao
ALTER TABLE attempts DROP CONSTRAINT attempts_kind_check,
  ADD CONSTRAINT attempts_kind_check CHECK (kind IN
  ('EXAM','PRACTICE','TOPIC_TEST','QUIZ','GAME','WRITING_CHALLENGE','CONTEST','DIAGNOSTIC'));

-- attempt_items: diem tung phan + ma loi da chon -> phan bo minh chung
ALTER TABLE attempt_items
  ADD COLUMN score      NUMERIC(5,2) CHECK (score BETWEEN 0 AND 100),
  ADD COLUMN error_code VARCHAR(10);

-- hoc lieu gan KID
ALTER TABLE pron_stages ADD COLUMN node_kids VARCHAR(10)[] NOT NULL DEFAULT '{}';
ALTER TABLE videos      ADD COLUMN node_kids VARCHAR(10)[] NOT NULL DEFAULT '{}';

DROP TABLE question_knowledge_points;
DROP TABLE knowledge_points;
```

> Nếu chọn **viết lại baseline** (mục 4, phương án A) thì không dùng `ALTER`: sửa thẳng các `CREATE TABLE` trong V1. Khối `ALTER` ở trên là phương án B.

---

## 4 · Chiến lược migration

| | A · Viết lại baseline ⭐ | B · Thêm V3 → V5 |
|---|---|---|
| Làm gì | Viết lại `V1` (core + cây + mastery) và `V2` (community) theo `database.md` mới. Chuyển thư mục vào `src/main/resources/db/migration` | Giữ V1/V2, thêm `V3__sync_database_md`, `V4__knowledge_tree`, `V5__mastery_engine` gồm các `ALTER` ở mục 3.4 |
| Ưu | Migration sạch, đọc một lần là hiểu; bỏ được nợ V1 lệch `database.md` | Không sửa file đã có; an toàn nếu ai đó đã chạy V1 |
| Nhược | Phạm luật "không sửa migration đã chạy" **nếu** đã có DB dùng chung chạy V1 | 3 file ALTER chồng lên một baseline vốn đã sai; người đọc phải ghép 4 file mới ra schema thật |
| Điều kiện | Không có DB dùng chung nào (Neon demo, máy bạn khác) có dữ liệu cần giữ | Có DB dùng chung đang có dữ liệu |

**Khuyến nghị A**, vì Flyway chưa từng chạy (sai vị trí thư mục) và chưa có code. Nếu Neon demo đã có V1 dán tay mà không có dữ liệu thật → drop và tạo lại. Ghi một dòng vào change log của `database.md`: *"Rebaseline 18/10 theo cây năng lực v2.0; chưa có môi trường nào chạy bản cũ qua Flyway."*

Kèm theo, ở cả hai phương án:

```yaml
# application.yaml - neu giu thu muc o goc repo thay vi chuyen vao resources
spring:
  flyway:
    locations: filesystem:db/migration
```

---

## 5 · Truy vấn kiểm tra (chạy sau khi nạp cây và trong test Testcontainers)

| Mã | Kiểm tra | Kỳ vọng |
|---|---|---|
| V01 | Vòng lặp trong cạnh `prereq` (recursive CTE, phụ lục F.4) | 0 dòng |
| V02 | Tiên quyết có `hsk_level` cao hơn nút | 0 dòng |
| V03 | Cạnh bắc cầu thừa: có A→B, B→C và A→C | 0 dòng |
| V04 | Nút KT có > 4 tiên quyết trực tiếp | 0 dòng |
| V05 | Câu `APPROVED` không có KID chính | 0 dòng |
| V06 | `lexemes.node_kid` không tồn tại trong `kt_node` của bản active | 0 dòng |
| V07 | Câu có > 2 KID phụ | 0 dòng |
| V08 | Nút có < 5 câu đã duyệt (độ phủ) | Danh sách để AI sinh bổ sung |

```sql
-- V03 · canh bac cau thua
SELECT ac.from_id, ac.to_id
FROM kt_edge ac
JOIN kt_edge ab ON ab.tree_version = ac.tree_version AND ab.from_id = ac.from_id AND ab.type = 'prereq'
JOIN kt_edge bc ON bc.tree_version = ac.tree_version AND bc.from_id = ab.to_id AND bc.to_id = ac.to_id AND bc.type = 'prereq'
WHERE ac.tree_version = :v AND ac.type = 'prereq';

-- V05 · cau da duyet khong co KID chinh
SELECT q.id FROM questions q
WHERE q.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM item_kid i
                  WHERE i.question_id = q.id AND i.tree_version = :v AND i.role = 'primary');

-- V08 · do phu cau hoi theo nut
SELECT n.id, count(q.id) AS approved_items
FROM kt_node n
LEFT JOIN item_kid i ON i.tree_version = n.tree_version AND i.kid = n.id
LEFT JOIN questions q ON q.id = i.question_id AND q.status = 'APPROVED'
WHERE n.tree_version = :v AND n.type = 'KT' AND n.form = 'quy_tac'
GROUP BY n.id HAVING count(q.id) < 5 ORDER BY approved_items, n.id;
```

---

## 6 · Hiệu năng — ba đường nóng

| Đường | Truy vấn | Index |
|---|---|---|
| Ghi bài làm → mastery | Đọc ≤ 8 minh chứng hợp lệ mới nhất mỗi KID bị chạm | `ix_ev_window (user_id, kid, occurred_at DESC) WHERE valid` — index-only, LIMIT 8 |
| Gợi ý ôn hằng ngày | Nút đến hạn / theo trạng thái của 1 người | `ix_m_due`, `ix_m_state` |
| Biên học tập | Nút chưa vững có mọi tiên quyết đã vững | Chạy trên `TreeSnapshot` trong bộ nhớ + 1 lần đọc `mastery` theo `user_id` (≤ ~240 dòng sơ cấp) |

Ước lượng tăng trưởng `evidence`: 1 câu → 1–3 dòng. 1.000 người × 40 câu/ngày × 2 ≈ **80.000 dòng/ngày, ~30 triệu/năm**. Đường đọc chỉ chạm index nên vẫn ổn; khi > 10 triệu dòng thì phân vùng theo tháng `occurred_at`.

---

## 7 · Ảnh hưởng tài liệu

| File | Mục cần sửa |
|---|---|
| `database.md` | Bảng tổng (33 → 40), §4 "bảy bảng không gộp" (`knowledge_points` → `kt_node`), §5 các mục 4.3–4.9, mục "không được đụng" |
| Report 3 SRS | ERD, mô tả thực thể (13 thực thể lõi), data business rules |
| ERD dbdiagram | Vẽ lại nhóm learning; file `.dbml` hiện chỉ còn ở `Downloads` |
| Plan phase 3 | Sửa: **giữ** `GRAMMAR` trong `lexemes` làm mục tra cứu (không chuyển hết sang nút) |

---

## Câu hỏi chưa giải quyết

1. Đã có DB dùng chung nào chạy V1 (Neon demo bạn dán tay hôm trước?) **và có dữ liệu cần giữ** không? → quyết định A hay B.
2. Thẻ flashcard tự tạo của người học (không có `lexeme_id`) có cần vào mastery không? Đề xuất: không, chỉ ôn theo hộp.
3. Mục 3.3 cho `evidence.service_request_id` để nhận minh chứng từ tiêu chí rubric bài viết. Có giữ chấm bài viết trong MVP không (feature 7.2)?

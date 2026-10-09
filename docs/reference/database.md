# CNHSK — Thiết kế Database

> **Bản final.** Cập nhật 2026-10-08 · **4 schema · 33 bảng** (26 MVP + 7 V2)
>
> 🔴 **File này kiêm luôn RFC** sửa `AC-03` và `AC-04` của Hiến pháp — xem §9.
> Chưa duyệt thì **chưa gen SQL**.

---

## 1 · Ba lỗ hổng đã khoá

Bản thiết kế trước có **ba lỗ hổng chức năng** và **một xung đột cấu hình**. Phát hiện khi lập plan cho
spec `001-auth-rbac`.

### 1.1 · Ba bảng nội dung bị thiếu

Bản trước giữ bảng **tiến độ** nhưng bỏ bảng **nội dung** — cùng một lỗi lặp ba lần:

| Chức năng | Feature | Trước đây có gì | Hậu quả |
|---|---|---|---|
| Luyện phát âm | 1.3 | `user_progress.target_type = 'PRON_STAGE'` | lưu được "học đến tầng 3" nhưng **không có tầng nào trong DB** |
| Học qua video | 1.6 | `'VIDEO'` + `position_ms` | lưu được "xem đến giây 90" nhưng **không có video nào** |
| Gói dịch vụ | 6.1 | `credit_cards.card_type='SUBSCRIPTION'` | **bán được thẻ gói nhưng không biết user đang ở gói nào** |

`user_progress.target_id` là `BIGINT` trần, không có FK — nên **SQL chạy đúng trong khi trỏ
vào hư không**.

> **Vì sao cách verify của bản trước không bắt được:** Bản trước được xác minh bằng chạy 86 câu SQL (0 lỗi)
> và 17 test ràng buộc (17/17 pass). Cả hai **không phát hiện được bảng thiếu**, vì thiết kế
> polymorphic không có FK để vỡ. Bài học: **SQL chạy được không chứng minh schema đủ** —
> phải đối chiếu ngược từ danh sách feature.

Và 6.1 là **nguồn thu** của dự án.

### 1.2 · Xung đột schema

`application.yaml` khai 3 schema, nhưng `V1__` tạo 24 bảng **không có prefix** → tất cả vào
`learning`, hai schema kia tạo rỗng. `AC-04` ghi *"auth 4 bảng, learning 40, community 15"* —
con số của **v5 (59 bảng)** đã bị thay thế.

### 1.3 · Thiếu cột

| Thiếu | Thuộc |
|---|---|
4 cột gói trên `users` | 6.1 — không biết user ở gói nào |
4 cột phễu trên `credit_cards` | 6.1 — không đo được tỉ lệ đổi mã |
`seq` trên `credit_transactions` | chống đua sổ cái |
Đếm lượt free | 4.1, 5.5 — "10 lượt free" không query được |

---

## 2 · Bốn schema

| Schema | Bảng | Module Java |
|---|---|---|
`auth` | 3 | `com.cnhsk.auth` |
`learning` | 22 | `com.cnhsk.learning` |
`community` | 5 | `com.cnhsk.community` |
`shared` | 1 | `com.cnhsk.shared` |
| **Tổng** | **31** | 4 module |

**Bốn schema khớp đúng 4 module Java.** Bản trước không có tính chất này: `audit_logs` ghi hành vi
của cả ba module nghiệp vụ nên không thuộc riêng module nào — Bản trước nhét nó vào `learning`
là sai nghĩa.

```sql
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS learning;
CREATE SCHEMA IF NOT EXISTS community;
CREATE SCHEMA IF NOT EXISTS shared;
```

### 2.1 · Tám actor — sáu trong DB, hai không

| Actor | Số UC | Có trong `user_roles`? |
|---|---|---|
`USER` | 79 | ✅ |
`GUEST` | 16 | ❌ **không có tài khoản** |
`SYSTEM` | 14 | ❌ **không phải người** |
`CONTENT_ADMIN` | 6 | ✅ |
`MANAGER` | 4 | ✅ |
`FINANCE_ADMIN` | 4 | ✅ |
`TEACHER` | 4 | ✅ |
`SUPER_ADMIN` | 3 | ✅ |

**8 actor = 6 role trong DB + 2 actor không phải role.**

- `GUEST` — khách chưa đăng nhập, **không có dòng** trong `auth.users`. Kiểm bằng *"không có
  JWT"*, không bằng `role_code`.
- `SYSTEM` — job định kỳ `@Scheduled` (hạ gói hết hạn, nhắc học, hoàn lượt quá hạn). Chạy
  bằng quyền hệ thống, **không qua `JwtFilter`**.

> ⚠️ **Đừng thêm `GUEST` hay `SYSTEM` vào `user_roles`.** Đây là lỗi dễ mắc: thấy chúng
> trong danh sách actor rồi tưởng phải có `role_code` tương ứng.

---

## 3 · Danh sách 33 bảng

### `auth` — 3 bảng

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 1 | `users` | Tài khoản + trạng thái + **gói dịch vụ** (22 cột) | UC-001→014, 114 |
| 2 | `user_roles` | N-N, có `granted_by`. `role_code` dùng `CHECK`, **không** FK | UC-115 |
| 3 | `auth_tokens` | Gộp 3 loại: refresh · reset password · verify email | UC-002, 006, 009 |

> **Bảng `roles` đã bỏ.** Nó chỉ có 1 lệnh `INSERT` với **6 giá trị tĩnh**, không có
> màn CRUD danh mục role riêng trong bộ **79 màn đã duyệt**; cấp/thu hồi role nằm tại
> **SCR-077 User Detail** (xem [screen-fields.md](screen-fields.md)). `AC-06` nói enum lưu `VARCHAR + CHECK`.
> Bỏ nó giảm **một JOIN ở mọi truy vấn quyền**.
>
> Đã chứng minh trên pg-mem: `DROP TABLE roles CASCADE` **không** làm mất dữ liệu
> `user_roles` (3 dòng trước và sau đều y nguyên), `CHECK` chặn đúng role sai (`HACKER`),
> không chặn sai role đúng (`TEACHER`).

### `learning` — 22 bảng

**Nội dung học (8 bảng)**

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 4 | `lexemes` | Chữ · từ · ngữ pháp (gộp 3 bảng v5) | UC-015→028, 056→063 |
| 5 | `lexeme_links` | Quan hệ: từ chứa chữ, từ liên quan | UC-056 |
| 6 | `knowledge_points` | Điểm kiến thức + `skill_type` | UC-040→053 |
| 7 | `topics` | Chủ đề + quan hệ tiên quyết | UC-025, 045, 046 |
| 8 | `topic_items` | Chủ đề ↔ lexeme ↔ knowledge_point | UC-025→029 |
| 9 | **`pron_stages`** 🆕 | **8 tầng luyện phát âm** — feature 1.3 | UC-021 |
| 10 | **`videos`** 🆕 | **Video học + phụ đề** — feature 1.6 | UC-030→032 |
| 11 | `questions` | Kho câu hỏi + `options JSONB` + kiểm duyệt | UC-019, 034, 108 |

**Đề thi (2 bảng)**

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 12 | `question_knowledge_points` | Nhãn kiến thức — **không được đụng** | UC-040, 041 |
| 13 | `exams` | Đề thi + `sections JSONB` | UC-033, 112 |
| 14 | `exam_questions` | Đề ↔ câu hỏi, có thứ tự | UC-034 |

**Tiến độ (4 bảng)**

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 15 | `user_progress` | Gộp 5 bảng tiến độ v5 + FSRS | UC-042→046, 051, 066 |
| 16 | `attempts` | Gộp mọi lượt làm bài (7 loại) | UC-017, 034, 080, 087, 092 |
| 17 | `attempt_items` | Từng câu trong lượt | UC-035, 039, 088 |
| 18 | `study_events` | Nhật ký học — streak, biểu đồ, log nhắc | UC-051, 052, 055 |

**Thư viện cá nhân (2 bảng)**

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 19 | `collections` | Sổ tay **và** bộ flashcard (gộp) | UC-064, 067, 068 |
| 20 | `collection_items` | Ghi chú / thẻ trong bộ | UC-062, 063, 066 |

**Tiền và dịch vụ (4 bảng)**

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 21 | **`plans`** 🆕 | **3 gói dịch vụ + hạn mức** — feature 6.1 | UC-093, 095, 101 |
| 22 | `credit_cards` | Mã thẻ — chỉ hash + **4 cột phễu** | UC-094, 099 |
| 23 | `credit_transactions` | Sổ cái append-only + **cột `seq`** | UC-094→102 |
| 24 | `service_requests` | AI job · chấm bài thuê · import (gộp 3) | UC-048, 103, 110 |

**Trợ lý AI (1 bảng)**

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 25 | `ai_chats` | Hội thoại trợ lý AI, hết hạn 90 ngày | UC-085, 086 |

### `shared` — 1 bảng

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 26 | `audit_logs` | Mọi thao tác nhạy cảm của **cả ba** module | UC-099→102, 108, 114, 115 |

> Đặt ở `shared` vì nó ghi hành vi của cả ba module nghiệp vụ. Mọi module ghi qua một
> `AuditService` ở `com.cnhsk.shared` — khớp luật ArchUnit
> *"shared không được phụ thuộc module nghiệp vụ"* (chiều ngược lại là được).

### `community` — 5 bảng (V2)

| # | Bảng | Mục đích | UC |
|---|---|---|---|
| 27 | `posts` | Bài blog + kiểm duyệt | UC-069→077 |
| 28 | `comments` | Bình luận lồng nhau | UC-071, 072, 079 |
| 29 | `reactions` | Thích · theo dõi · báo cáo (gộp 3) | UC-073→075 |
| 30 | `moderation_cases` | Xử lý báo cáo vi phạm | UC-078, 079 |
| 31 | `contests` | Cuộc thi + `prizes JSONB` | UC-090→092, 113 |

**Thêm 2026-10-08** sau khi khảo sát schinese.net — hai cơ chế học chủ động của feature 1.6.
Thuộc schema `learning`, xếp cuối để không phải đánh lại số 11→31:

| # | Bảng | Vai trò | UC |
| --- | --- | --- | --- |
| 32 | **`dictation_attempts`** 🆕 | **Nghe rồi gõ lại** — `char_score` + `tone_score` riêng | UC-120, 121 |
| 33 | **`shadowing_attempts`** 🆕 | **Nói nhái** — điểm phát âm từng âm tiết. Không lưu audio | UC-122, 123 |

**Tổng: 26 MVP + 7 V2 = 33 bảng.**

---

## 4 · Bảy bảng KHÔNG gộp, và vì sao

Gộp sai nguy hiểm hơn thừa bảng. Bảy bảng dưới đây giữ riêng:

| Bảng | Vì sao không gộp |
|---|---|
| `credit_transactions` | Sổ cái tiền thật, **append-only**, không bao giờ xoá dòng. Gộp vào bảng khác là mất tính bất biến |
| `credit_cards` | Chỉ lưu `code_hash`, có vòng đời riêng (`UNUSED`→`USED`/`VOIDED`), ràng buộc `SELECT FOR UPDATE` |
| `attempt_items` | Gốc của mọi phân tích lỗi sai (UC-040). Không bao giờ xoá. Tách khỏi `attempts` vì quan hệ 1-n |
| `users` | Dữ liệu định danh + PII. Tách để dễ áp luật masking và audit |
| `questions` | Nội dung có kiểm duyệt (`PENDING_REVIEW`→`APPROVED`). Vòng đời khác `lexemes` |
| `knowledge_points` | Trục nối từ điển ↔ đề thi ↔ tiến độ. Gộp là mất "yếu chỗ nào luyện chỗ đó" |
| `audit_logs` | Ghi mọi thao tác nhạy cảm. Chỉ ghi, không sửa |

---


## 5 · Chi tiết từng bảng

### 4.1 · `users`

```sql
CREATE TABLE users (
  id                BIGSERIAL PRIMARY KEY,
  email             VARCHAR(255) NOT NULL UNIQUE,
  password_hash     VARCHAR(255) NOT NULL,
  display_name      VARCHAR(100) NOT NULL,
  avatar_url        TEXT,
  hsk_level         SMALLINT CHECK (hsk_level BETWEEN 1 AND 9),

  -- Trang thai tai khoan; UC-012 dem sai va chan theo email-IP trong Redis.
  email_verified_at TIMESTAMPTZ,
  suspended_at      TIMESTAMPTZ,          -- UC-078: MANAGER treo
  suspended_until   TIMESTAMPTZ,
  banned_at         TIMESTAMPTZ,          -- UC-114: SUPER_ADMIN ban
  banned_by         BIGINT REFERENCES users(id),
  ban_reason        TEXT,

  -- Nhac hoc — thay bang user_notification_settings cua v5
  reminder_time     TIME,                  -- gio Viet Nam, NULL = tat
  reminder_channels VARCHAR(50) NOT NULL DEFAULT 'WEB',  -- 'WEB,EMAIL'

  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_ban_reason CHECK (banned_at IS NULL OR ban_reason IS NOT NULL)
);
```

> **Không hard delete user.** `posts.author_id`, `attempts.user_id`, `credit_transactions.user_id`
> đều trỏ sang đây. Xoá cứng để lại dữ liệu mồ côi — chỉ dùng `banned_at`.
> Đây là câu trả lời cho khoảng trống #15 nhóm 5.

### 4.2 · `auth_tokens`

```sql
CREATE TABLE auth_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id),
  token_type  VARCHAR(20) NOT NULL
              CHECK (token_type IN ('REFRESH','RESET_PASSWORD','EMAIL_VERIFY')),
  token_hash  VARCHAR(255) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_tokens_user ON auth_tokens(user_id) WHERE revoked_at IS NULL;
```

> 🔴 **`EMAIL_VERIFY` là giá trị v5 thiếu** (khoảng trống #4 nhóm 0) — UC-001 và UC-002 cần nó.

### 4.3 · `lexemes` — gộp `characters` + `words` + `grammar_points`

```sql
CREATE TABLE lexemes (
  id            BIGSERIAL PRIMARY KEY,
  kind          VARCHAR(10) NOT NULL CHECK (kind IN ('CHAR','WORD','GRAMMAR')),
  text          VARCHAR(200) NOT NULL,
  pinyin        VARCHAR(200),
  -- Cot chuan hoa — GIAI QUYET khoang trong #3 nhom 4.
  -- Tra pinyin khong dau phai dung cot nay, khong dung LIKE '%x%'.
  pinyin_norm   VARCHAR(200),
  sino_viet     VARCHAR(200),              -- am Han-Viet
  meaning_vi    TEXT NOT NULL,
  meaning_norm  TEXT,                      -- khong dau, cho tra nghia Viet
  hsk_level     SMALLINT CHECK (hsk_level BETWEEN 1 AND 9),
  audio_url     TEXT,
  radical       VARCHAR(10),               -- chi CHAR
  stroke_count  SMALLINT,                  -- chi CHAR
  -- stroke_data (CHAR) · structure + examples (GRAMMAR) · tags
  extra         JSONB NOT NULL DEFAULT '{}',
  frequency     INTEGER NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT uq_lexeme UNIQUE (kind, text)
);

CREATE INDEX ix_lex_pinyin  ON lexemes(pinyin_norm);
CREATE INDEX ix_lex_kind_hsk ON lexemes(kind, hsk_level);
-- GIAI QUYET khoang trong #2 nhom 4: tra nghia Viet phai dung index,
-- khong duoc LIKE '%x%' quet toan bang.
CREATE INDEX ix_lex_meaning ON lexemes USING gin (meaning_norm gin_trgm_ops);
```

### 4.4 · `knowledge_points`

```sql
CREATE TABLE knowledge_points (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(50) NOT NULL UNIQUE,   -- 'GP-HSK3-205'
  title       VARCHAR(200) NOT NULL,
  -- GIAI QUYET khoang trong #1+#2 nhom 3: UC-053 va UC-038 deu can cot nay.
  -- Chot 6 ky nang — sua sau phai nhap lai question_knowledge_points.
  skill_type  VARCHAR(20) NOT NULL
              CHECK (skill_type IN ('LISTENING','READING','WRITING','VOCAB','GRAMMAR','CHARACTER')),
  hsk_level   SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  lexeme_id   BIGINT REFERENCES lexemes(id),  -- neu diem KT gan voi 1 lexeme
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_kp_skill ON knowledge_points(skill_type, hsk_level);
```

### 4.5 · `topics` và `topic_items`

```sql
CREATE TABLE topics (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(200) NOT NULL,
  hsk_level     SMALLINT NOT NULL,
  order_index   INTEGER NOT NULL DEFAULT 0,
  -- Tien quyet dang mang — tranh them bang noi chi de luu 1-2 dong.
  -- Kiem CHU TRINH bang DFS truoc khi luu (UC-045 CIRCULAR_PREREQUISITE).
  prereq_ids    BIGINT[] NOT NULL DEFAULT '{}',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE topic_items (
  topic_id            BIGINT NOT NULL REFERENCES topics(id),
  lexeme_id           BIGINT REFERENCES lexemes(id),
  knowledge_point_id  BIGINT REFERENCES knowledge_points(id),
  order_index         INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (topic_id, lexeme_id, knowledge_point_id),
  CONSTRAINT ck_topic_item CHECK (lexeme_id IS NOT NULL OR knowledge_point_id IS NOT NULL)
);
```

> **Thay 2 bảng v5** (`topic_words` + `topic_knowledge_points`) bằng một bảng —
> cùng là "chủ đề chứa gì".

### 4.6 · `questions`

```sql
CREATE TABLE questions (
  id            BIGSERIAL PRIMARY KEY,
  qtype         VARCHAR(20) NOT NULL CHECK (qtype IN
                ('MULTIPLE_CHOICE','TRUE_FALSE','IMAGE_MATCH','SENTENCE_MATCH',
                 'FILL_BLANK','SENTENCE_ORDER','ESSAY')),
  hsk_level     SMALLINT NOT NULL,
  stem          TEXT NOT NULL,
  -- [{key:'A', text:'...', is_correct:true}, ...] — JSONB CHI DOC (AC-09)
  options       JSONB NOT NULL DEFAULT '[]',
  correct_key   VARCHAR(20),               -- cho FILL_BLANK, SENTENCE_ORDER
  explanation   TEXT,
  audio_url     TEXT,
  image_url     TEXT,

  -- Kiem duyet: cau AI LUON vao PENDING_REVIEW (UC-049)
  source        VARCHAR(10) NOT NULL DEFAULT 'IMPORT'
                CHECK (source IN ('IMPORT','AI','MANUAL')),
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
                CHECK (status IN ('PENDING_REVIEW','APPROVED','REJECTED','ARCHIVED')),
  reviewed_by   BIGINT REFERENCES users(id),
  reviewed_at   TIMESTAMPTZ,
  review_reason TEXT,
  reusable      BOOLEAN NOT NULL DEFAULT true,   -- khoang trong #11 nhom 3
  created_by_request_id BIGINT,                  -- job AI sinh ra cau nay
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 🔴 RANG BUOC LOI cua tinh nang 3.3: cau AI khong the APPROVED
  -- neu chua co nguoi duyet. Chan o DB, khong dua vao code nho.
  CONSTRAINT ck_ai_review CHECK (
    source <> 'AI' OR status <> 'APPROVED' OR reviewed_by IS NOT NULL
  ),
  CONSTRAINT ck_reject_reason CHECK (
    status <> 'REJECTED' OR review_reason IS NOT NULL
  )
);
CREATE INDEX ix_q_serve ON questions(status, qtype, hsk_level) WHERE status = 'APPROVED';
```

> 🔴 **`options JSONB` thay bảng `question_options` của v5.** Đáp án luôn đọc **cả cụm**,
> không bao giờ truy vấn lẻ một đáp án. AC-09 cho phép JSONB **chỉ đọc** — đáp án chỉ sửa qua
> UC-109, không ghi liên tục.
> **Đánh đổi:** ràng buộc "đúng 1 `is_correct`" phải kiểm ở application + CHECK bằng
> `jsonb_array_length`, không dùng được partial unique index. Chấp nhận vì giảm 1 bảng và
> mọi truy vấn đáp án đều lấy cả cụm.

### 4.7 · `attempts` — bảng gộp lớn nhất

```sql
CREATE TABLE attempts (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES users(id),
  kind           VARCHAR(20) NOT NULL CHECK (kind IN
                 ('EXAM','PRACTICE','TOPIC_TEST','QUIZ','GAME','WRITING_CHALLENGE','CONTEST')),
  -- Tro toi exam/topic/quiz_set/game_code/contest tuy kind
  ref_type       VARCHAR(20),
  ref_id         BIGINT,
  game_code      VARCHAR(30),              -- chi kind=GAME

  status         VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
                 CHECK (status IN ('IN_PROGRESS','SUBMITTED','ABANDONED','EXPIRED')),
  -- Chong gian lan: moi loai deu can served_at (UC-017, UC-035, UC-088, UC-092)
  served_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  submitted_at   TIMESTAMPTZ,
  expires_at     TIMESTAMPTZ,              -- 24h cho EXAM, ngan hon cho GAME

  score          BIGINT,                   -- BIGINT, khong FLOAT (AC-07)
  max_score      BIGINT,
  duration_ms    INTEGER,
  suspicious     BOOLEAN NOT NULL DEFAULT false,
  -- Snapshot nội dung đã phát cho GAME/EXAM; ghi một lần khi bắt đầu ván/bài.
  -- Server dùng để đối chiếu khi nộp, không trả đáp án trong response bắt đầu.
  served_items   JSONB,
  -- Ban nhap bai thi — GIAI QUYET khoang trong #1 nhom 2.
  -- Chon cot JSONB thay Redis: khong phu thuoc TODO(REDIS_PLACEMENT),
  -- va ghi nhap chi xay ra moi 30 giay nen khong vi pham tinh than AC-09.
  draft_answers  JSONB,
  draft_saved_at TIMESTAMPTZ,

  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_game_code CHECK (kind <> 'GAME' OR game_code IS NOT NULL),
  CONSTRAINT ck_submitted CHECK (status <> 'SUBMITTED' OR submitted_at IS NOT NULL)
);

CREATE INDEX ix_att_user     ON attempts(user_id, kind, submitted_at DESC);
CREATE INDEX ix_att_progress ON attempts(user_id, status) WHERE status = 'IN_PROGRESS';
-- Quiz chi tinh hang LAN DAU (UC-080) — unique chan lan 2 vao bang xep hang
CREATE UNIQUE INDEX uq_quiz_first ON attempts(user_id, ref_id)
  WHERE kind = 'QUIZ' AND status = 'SUBMITTED';
```

> **Bảng này thay 5 bảng v5** và lấp 2 khoảng trống. Mọi luật chống gian lận
> (`ATTEMPT_ALREADY_SUBMITTED`, `IMPOSSIBLE_DURATION`, `ATTEMPT_NOT_OWNED`) viết **một lần**
> thay vì năm lần.

### 4.8 · `attempt_items`

```sql
CREATE TABLE attempt_items (
  id           BIGSERIAL PRIMARY KEY,
  attempt_id   BIGINT NOT NULL REFERENCES attempts(id),
  question_id  BIGINT REFERENCES questions(id) ON DELETE RESTRICT,
  lexeme_id    BIGINT REFERENCES lexemes(id),     -- cho GAME, WRITING
  answer       TEXT,
  is_correct   BOOLEAN,                            -- NULL = ESSAY chua cham
  duration_ms  INTEGER,
  order_index  INTEGER NOT NULL DEFAULT 0,
  answered_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_attempt ON attempt_items(attempt_id);
CREATE INDEX ix_ai_wrong   ON attempt_items(question_id) WHERE is_correct = false;
```

> 🔴 **`ON DELETE RESTRICT` giải quyết khoảng trống #5 nhóm 2.** Xoá câu hỏi đang có người làm
> là mất lịch sử bài thi — DB chặn, không dựa vào code nhớ.

### 4.9 · `user_progress` — bảng gộp quan trọng nhất

```sql
CREATE TABLE user_progress (
  user_id        BIGINT NOT NULL REFERENCES users(id),
  target_type    VARCHAR(20) NOT NULL CHECK (target_type IN
                 ('KNOWLEDGE_POINT','TOPIC','PRON_STAGE','FLASHCARD','VIDEO')),
  target_id      BIGINT NOT NULL,

  -- FSRS — dung cho KNOWLEDGE_POINT va FLASHCARD
  mastery        NUMERIC(4,3) NOT NULL DEFAULT 0 CHECK (mastery BETWEEN 0 AND 1),
  stability      NUMERIC(8,3) NOT NULL DEFAULT 0.1,
  difficulty     NUMERIC(4,2) NOT NULL DEFAULT 5,
  next_review_at TIMESTAMPTZ,
  review_count   INTEGER NOT NULL DEFAULT 0,
  lapse_count    INTEGER NOT NULL DEFAULT 0,

  -- Tien do dang % — dung cho TOPIC, PRON_STAGE, VIDEO
  percent        NUMERIC(5,2) CHECK (percent BETWEEN 0 AND 100),
  state          VARCHAR(20) NOT NULL DEFAULT 'LEARNING'
                 CHECK (state IN ('LOCKED','AVAILABLE','LEARNING','COMPLETED','MASTERED')),
  unlocked_at    TIMESTAMPTZ,
  -- Nguoi hoc tu bao "da biet tu nay" — UC-029 kiem lai (khoang trong #4 nhom 1)
  self_declared  BOOLEAN NOT NULL DEFAULT false,
  -- Vi tri xem video — GIAI QUYET khoang trong #1 nhom 1
  position_ms    INTEGER,

  last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Chan race condition mo tang/mo chu de (UC-022, UC-046 UNIQUE_VIOLATION)
  PRIMARY KEY (user_id, target_type, target_id)
);

-- Hai index BAT BUOC theo feature tree 3.1
CREATE INDEX ix_prog_due     ON user_progress(user_id, next_review_at)
  WHERE next_review_at IS NOT NULL;
CREATE INDEX ix_prog_mastery ON user_progress(user_id, mastery);
```

> 🔴 **Khoá chính `(user_id, target_type, target_id)` lấp 2 khoảng trống cùng lúc:**
> `UNIQUE_VIOLATION` của UC-022 (hai tab cùng mở tầng phát âm) và UC-046 (hai request cùng mở
> chủ đề) — không cần thêm unique constraint riêng cho từng bảng như v5.

### 4.10 · `study_events` — thay `study_sessions` + log nhắc học

```sql
CREATE TABLE study_events (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id),
  event_type VARCHAR(20) NOT NULL CHECK (event_type IN
             ('STUDY','REVIEW','GAME','EXAM','REMINDER_SENT')),
  ref_type   VARCHAR(20),
  ref_id     BIGINT,
  duration_ms INTEGER,
  -- Ngay theo GIO VIET NAM — streak va bieu do tinh theo cot nay.
  -- GIAI QUYET loi STREAK_TIMEZONE_ERROR (UC-051): khong group theo DATE(UTC).
  local_date DATE NOT NULL,
  channel    VARCHAR(20),                 -- chi REMINDER_SENT: WEB/EMAIL/PUSH
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ev_user_date ON study_events(user_id, local_date);
-- GIAI QUYET khoang trong #8 nhom 3: chan gui nhac trung khi chay 2 instance
CREATE UNIQUE INDEX uq_reminder ON study_events(user_id, local_date, channel)
  WHERE event_type = 'REMINDER_SENT';
```

### 4.11 · `collections` + `collection_items` — gộp sổ tay và flashcard

```sql
CREATE TABLE collections (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id),
  kind        VARCHAR(20) NOT NULL CHECK (kind IN ('NOTEBOOK','FLASHCARD_DECK')),
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  is_public   BOOLEAN NOT NULL DEFAULT false,   -- UC-068 chia se (V2)
  shared_at   TIMESTAMPTZ,
  copied_from BIGINT REFERENCES collections(id),
  deleted_at  TIMESTAMPTZ,                      -- soft delete (khoang trong #10 nhom 4)
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_coll_user ON collections(user_id, kind) WHERE deleted_at IS NULL;

CREATE TABLE collection_items (
  id            BIGSERIAL PRIMARY KEY,
  collection_id BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
  lexeme_id     BIGINT REFERENCES lexemes(id),
  title         VARCHAR(200),               -- ghi chu tu do
  content       TEXT,
  tags          VARCHAR(50)[] NOT NULL DEFAULT '{}',
  source_type   VARCHAR(20),                -- DICTIONARY/VIDEO/TRANSLATE
  source_ref    TEXT,
  order_index   INTEGER NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_item CHECK (lexeme_id IS NOT NULL OR content IS NOT NULL)
);
-- Khong them the trung trong cung bo (UC-063 CARD_ALREADY_IN_DECK)
CREATE UNIQUE INDEX uq_coll_lex ON collection_items(collection_id, lexeme_id)
  WHERE lexeme_id IS NOT NULL AND deleted_at IS NULL;
-- Tim kiem trong ghi chu (UC-065) — PHAI loc user_id truoc
CREATE INDEX ix_item_search ON collection_items USING gin (content gin_trgm_ops);
```

> **Lịch ôn flashcard nằm ở `user_progress`** với `target_type = 'FLASHCARD'`,
> `target_id = collection_items.id`. Vì vậy sao chép bộ thẻ (UC-067) **tự động reset lịch ôn** —
> bản sao có `collection_items.id` mới nên không có dòng `user_progress` nào.
> Giải quyết `COPIED_REVIEW_SCHEDULE` (khoảng trống #5 nhóm 4) **bằng thiết kế**, không bằng code.

### 4.12 · `credit_cards` và `credit_transactions`

```sql
CREATE TABLE credit_cards (
  id          BIGSERIAL PRIMARY KEY,
  batch_id    VARCHAR(30) NOT NULL,
  code_hash   VARCHAR(255) NOT NULL UNIQUE,   -- CHI hash, khong bao gio luu ma tho
  card_type   VARCHAR(20) NOT NULL CHECK (card_type IN ('CREDIT','SUBSCRIPTION')),
  value       INTEGER NOT NULL CHECK (value > 0),
  plan_days   SMALLINT,                        -- chi SUBSCRIPTION
  status      VARCHAR(10) NOT NULL DEFAULT 'UNUSED'
              CHECK (status IN ('UNUSED','USED','VOIDED')),
  used_by     BIGINT REFERENCES users(id),
  used_at     TIMESTAMPTZ,
  expires_at  TIMESTAMPTZ,
  created_by  BIGINT NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_used CHECK (status <> 'USED' OR (used_by IS NOT NULL AND used_at IS NOT NULL))
);

CREATE TABLE credit_transactions (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL REFERENCES users(id),
  tx_type         VARCHAR(20) NOT NULL CHECK (tx_type IN
                  ('TOPUP','DEDUCT','REFUND','ADJUSTMENT','TEACHER_PAYOUT','SUBSCRIPTION')),
  amount          INTEGER NOT NULL,           -- am cho DEDUCT
  balance_before  INTEGER NOT NULL CHECK (balance_before >= 0),
  balance_after   INTEGER NOT NULL CHECK (balance_after >= 0),
  feature_code    VARCHAR(30),                -- AI_GENERATE/TRANSLATE/ASSISTANT/GRADING
  card_id         BIGINT REFERENCES credit_cards(id),
  request_id      BIGINT,                     -- service_requests lien quan
  -- Hoan tro toi dong DEDUCT goc. Unique chan hoan HAI LAN (UC-097).
  ref_transaction_id BIGINT REFERENCES credit_transactions(id),
  reason          TEXT,
  performed_by    BIGINT REFERENCES users(id),  -- ADJUSTMENT: ai duyet
  dispute_ref     VARCHAR(50),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_balance CHECK (balance_after = balance_before + amount),
  CONSTRAINT ck_adjust  CHECK (tx_type <> 'ADJUSTMENT' OR
                               (reason IS NOT NULL AND performed_by IS NOT NULL))
);
CREATE INDEX ix_tx_user ON credit_transactions(user_id, created_at DESC);
CREATE UNIQUE INDEX uq_refund ON credit_transactions(ref_transaction_id)
  WHERE tx_type = 'REFUND';
CREATE UNIQUE INDEX uq_dispute ON credit_transactions(dispute_ref)
  WHERE dispute_ref IS NOT NULL;
```

> 🔴 **`ck_balance` là ràng buộc quan trọng nhất của cả schema.**
> `balance_after = balance_before + amount` — DB tự kiểm, không thể ghi dòng sai.
> Giải quyết `BALANCE_BEFORE_AFTER_MISMATCH` (UC-094) ở tầng DB.
>
> **Không có bảng `user_credits` riêng.** Số dư = `balance_after` của dòng mới nhất:
> ```sql
> SELECT balance_after FROM credit_transactions
> WHERE user_id = ? ORDER BY id DESC LIMIT 1;
> ```
> Giảm 1 bảng **và** xoá hẳn lớp lỗi `BALANCE_UPDATED_WITHOUT_LEDGER` — không còn hai nguồn
> số dư để lệch. Đổi lại cần index `(user_id, id DESC)`, đã có ở trên.

### 4.13 · `service_requests` — gộp 3 loại yêu cầu nền

```sql
CREATE TABLE service_requests (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  kind          VARCHAR(20) NOT NULL CHECK (kind IN
                ('AI_GENERATE','GRADING','IMPORT','TRANSLATE')),
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ASSIGNED','RUNNING','COMPLETED','FAILED','EXPIRED')),

  payload       JSONB NOT NULL DEFAULT '{}',   -- dau vao: knowledge_point_ids, bai viet, file_hash
  result        JSONB,                          -- dau ra: so cau sinh, diem cham, so dong loi

  -- Cham bai thue (UC-103 → UC-107)
  assignee_id   BIGINT REFERENCES users(id),    -- TEACHER nhan cham
  assigned_at   TIMESTAMPTZ,
  deadline_at   TIMESTAMPTZ,                    -- han nhan / han cham
  score         SMALLINT,
  feedback      TEXT,
  payout        INTEGER,                        -- tien tra teacher

  -- Import (UC-110, UC-111)
  file_hash     VARCHAR(64),                    -- nhan ra file da nhap → idempotent
  error_rows    JSONB,                          -- [{line, column, value, code, message}]

  retry_count   SMALLINT NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at  TIMESTAMPTZ,

  CONSTRAINT ck_grading CHECK (kind <> 'GRADING' OR deadline_at IS NOT NULL),
  CONSTRAINT ck_feedback CHECK (score IS NULL OR feedback IS NOT NULL)
);
CREATE INDEX ix_req_queue ON service_requests(kind, status, deadline_at)
  WHERE status IN ('PENDING','ASSIGNED');
-- Mot user chi mot job AI dang chay (UC-048 JOB_ALREADY_RUNNING)
CREATE UNIQUE INDEX uq_ai_running ON service_requests(user_id)
  WHERE kind = 'AI_GENERATE' AND status IN ('PENDING','RUNNING');
-- Nhap lai cung file khong tao ban trung (UC-110)
CREATE UNIQUE INDEX uq_import_file ON service_requests(file_hash)
  WHERE kind = 'IMPORT' AND status = 'COMPLETED';
```

> **Thay 4 bảng v5:** `ai_generation_jobs` · `grading_requests` · `import_runs` ·
> `translation_history`. Cả bốn là *"yêu cầu chạy nền, có trạng thái, có kết quả"*.
> **Và lấp khoảng trống #4 nhóm 6c** (bảng chi tiết lỗi nhập) bằng `error_rows JSONB`.

### 4.14 · `ai_chats`

```sql
CREATE TABLE ai_chats (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id),
  session_key UUID NOT NULL DEFAULT gen_random_uuid(),
  -- [{role:'user'|'assistant', content:'...', at:'...'}] — append-only
  messages    JSONB NOT NULL DEFAULT '[]',
  page_context VARCHAR(100),              -- KHONG chua PII
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Thoi han luu — GIAI QUYET khoang trong #17 nhom 5. Chot 90 ngay.
  expires_at  TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '90 days')
);
CREATE INDEX ix_chat_user ON ai_chats(user_id, updated_at DESC);
```

### 4.15 · `audit_logs`

```sql
CREATE TABLE audit_logs (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    BIGINT REFERENCES users(id),
  action      VARCHAR(50) NOT NULL,     -- CARD_BATCH_CREATE, ROLE_GRANT, LEDGER_READ...
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      JSONB NOT NULL DEFAULT '{}',
  ip_address  INET,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_actor  ON audit_logs(actor_id, created_at DESC);
CREATE INDEX ix_audit_action ON audit_logs(action, created_at DESC);
```

> **Bảng này thay `review_actions` của v5** và mở rộng cho mọi thao tác nhạy cảm:
> sinh mã thẻ, cấp role, **đọc sổ cái**, khoá tài khoản, duyệt câu hỏi.
> Quyết định v2 bắt `FINANCE_ADMIN` ghi audit cho cả thao tác **đọc**.

### 4.16 · Năm bảng V2

```sql
-- V2.1 — Blog
CREATE TABLE posts (
  id BIGSERIAL PRIMARY KEY,
  author_id BIGINT NOT NULL REFERENCES users(id),
  category VARCHAR(30) NOT NULL,
  title VARCHAR(300) NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
         CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','HIDDEN')),
  reviewed_by BIGINT REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  review_reason TEXT,
  comments_locked BOOLEAN NOT NULL DEFAULT false,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_post_reject CHECK (status <> 'REJECTED' OR review_reason IS NOT NULL)
);
CREATE INDEX ix_post_feed ON posts(status, published_at DESC) WHERE status = 'APPROVED';

-- V2.2 — Binh luan long nhau, SOFT DELETE de giu cay
CREATE TABLE comments (
  id BIGSERIAL PRIMARY KEY,
  post_id BIGINT NOT NULL REFERENCES posts(id),
  parent_id BIGINT REFERENCES comments(id),
  author_id BIGINT NOT NULL REFERENCES users(id),
  content TEXT NOT NULL,
  depth SMALLINT NOT NULL DEFAULT 0 CHECK (depth <= 3),
  status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'
         CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
  moderated_by BIGINT REFERENCES users(id),
  moderation_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_cmt_post ON comments(post_id, created_at);

-- V2.3 — Gop like + follow + report-count
CREATE TABLE reactions (
  user_id BIGINT NOT NULL REFERENCES users(id),
  target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('POST','COMMENT','USER','COLLECTION')),
  target_id BIGINT NOT NULL,
  kind VARCHAR(10) NOT NULL CHECK (kind IN ('LIKE','FOLLOW','REPORT')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, target_type, target_id, kind)
);
CREATE INDEX ix_react_target ON reactions(target_type, target_id, kind);

-- V2.4 — Xu ly bao cao vi pham (vong doi khac reactions)
CREATE TABLE moderation_cases (
  id BIGSERIAL PRIMARY KEY,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  report_count INTEGER NOT NULL DEFAULT 1,
  reasons JSONB NOT NULL DEFAULT '[]',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
         CHECK (status IN ('PENDING','ACTIONED','REJECTED')),
  auto_hidden BOOLEAN NOT NULL DEFAULT false,
  handled_by BIGINT REFERENCES users(id),
  handled_at TIMESTAMPTZ,
  decision_note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Gom bao cao theo noi dung — GIAI QUYET PARTIAL_REPORT_CLOSURE (UC-078)
CREATE UNIQUE INDEX uq_mod_target ON moderation_cases(target_type, target_id)
  WHERE status = 'PENDING';

-- V2.5 — Cuoc thi
CREATE TABLE contests (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  exam_id BIGINT REFERENCES exams(id),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  prizes JSONB NOT NULL DEFAULT '[]',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
         CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','CANCELLED')),
  ranking_published_at TIMESTAMPTZ,
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_contest_time CHECK (starts_at < ends_at)
);
```

> **Đăng ký và bài thi của cuộc thi dùng `attempts`** với `kind = 'CONTEST'`,
> `ref_id = contests.id`. Không cần `contest_participants` và `contest_submissions`.
>
> 🔴 **Nhật ký gian lận → `audit_logs`**, không vào `cheat_events JSONB`.
> Giải quyết mâu thuẫn AC-09 (khoảng trống #2 nhóm 5): nhật ký bản chất là **ghi liên tục**,
> JSONB chỉ đọc. Dùng bảng chuyên ghi là đúng bản chất.

---


## 6 · Ba bảng mới

### 4.1 · `learning.pron_stages` — feature 1.3

```sql
CREATE TABLE learning.pron_stages (
  id            BIGSERIAL PRIMARY KEY,
  stage_index   SMALLINT     NOT NULL UNIQUE CHECK (stage_index BETWEEN 1 AND 8),
  name          VARCHAR(100) NOT NULL,
  description   TEXT,
  -- Am tiet / cap am de lan cua tang nay. Chi doc sau khi nhap -> hop AC-09.
  target_sounds JSONB        NOT NULL DEFAULT '[]',
  -- BUS-11 chot 80%. De cot de sau doi duoc tung tang.
  pass_percent  NUMERIC(5,2) NOT NULL DEFAULT 80.00
                CHECK (pass_percent BETWEEN 0 AND 100),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

`learning.user_progress(target_type='PRON_STAGE', target_id)` trỏ vào bảng này.

`target_sounds` chứa các cặp âm dễ lẫn của người Việt (`DK-06`): `zh/z` · `ch/c` · `sh/s`.

### 4.2 · `learning.videos` — feature 1.6

```sql
CREATE TABLE learning.videos (
  id          BIGSERIAL PRIMARY KEY,
  title       VARCHAR(300) NOT NULL,
  url         TEXT         NOT NULL,      -- URL/ID YouTube hop le, video cho phep nhung
  duration_ms INTEGER      NOT NULL CHECK (duration_ms > 0),
  hsk_level   SMALLINT     CHECK (hsk_level BETWEEN 1 AND 9),
  topic_id    BIGINT       REFERENCES learning.topics(id),
  -- Mang {start_ms, end_ms, text_cn, text_vi}. Chi doc -> hop AC-09.
  subtitles   JSONB        NOT NULL DEFAULT '[]',
  status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
              CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED','UNAVAILABLE')),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_video_level ON learning.videos(hsk_level, status) WHERE status = 'PUBLISHED';
CREATE INDEX ix_video_topic ON learning.videos(topic_id)          WHERE status = 'PUBLISHED';
```

`user_progress(target_type='VIDEO', target_id)` trỏ vào đây, `position_ms` lưu vị trí xem.

**Hai cột thêm cho nguồn video** — chốt 2026-10-08 sau khi khảo sát schinese.net:

```sql
ALTER TABLE learning.videos
  ADD COLUMN source_type  VARCHAR(20) NOT NULL DEFAULT 'YOUTUBE'
             CHECK (source_type IN ('YOUTUBE','SELF_HOSTED')),
  ADD COLUMN external_id  VARCHAR(50);   -- YouTube video ID khi source_type = 'YOUTUBE'
```

> `external_id` để riêng thay vì parse từ `url`, vì URL YouTube có nhiều dạng
> (`watch?v=`, `youtu.be/`, `embed/`) và parse ở nhiều chỗ sẽ lệch nhau.

### 4.2a · `learning.dictation_attempts` — feature 1.6, UC-120

Nghe một câu phụ đề, gõ lại chữ Hán, server chấm.

```sql
CREATE TABLE learning.dictation_attempts (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES auth.users(id),
  video_id       BIGINT NOT NULL REFERENCES learning.videos(id),
  subtitle_index INTEGER NOT NULL CHECK (subtitle_index >= 0),
  submitted_text TEXT   NOT NULL,
  -- Chup lai noi dung phu de luc cham. CONTENT_ADMIN sua phu de sau do
  -- khong lam sai lich su cham diem.
  expected_text  TEXT   NOT NULL,
  char_score     NUMERIC(5,2) NOT NULL CHECK (char_score BETWEEN 0 AND 100),
  -- NULL khi khong chuyen duoc chu sang pinyin (PINYIN_CONVERT_FAILED).
  -- Van cham duoc char_score nen khong chan ca tinh nang.
  tone_score     NUMERIC(5,2)          CHECK (tone_score BETWEEN 0 AND 100),
  -- [{pos, expected, got, kind}] voi kind: CHAR_WRONG | TONE_WRONG | MISSING | EXTRA
  diff           JSONB  NOT NULL DEFAULT '[]',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_dict_user_video ON learning.dictation_attempts(user_id, video_id, subtitle_index);
```

> **`char_score` và `tone_score` là hai cột riêng, không gộp** (`BR-120-3`). Gộp thành
> một điểm trung bình là mất thông tin hành động được: người học cần biết mình nghe sai
> chữ hay đọc sai thanh. Đây là điểm schinese.net **không** có.

### 4.2b · `learning.shadowing_attempts` — feature 1.6, UC-122

Đọc theo một câu, ghi âm, dịch vụ ngoài chấm phát âm.

```sql
CREATE TABLE learning.shadowing_attempts (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES auth.users(id),
  video_id       BIGINT NOT NULL REFERENCES learning.videos(id),
  subtitle_index INTEGER NOT NULL CHECK (subtitle_index >= 0),
  expected_text  TEXT   NOT NULL,   -- chup lai, cung ly do nhu dictation_attempts
  accuracy_score NUMERIC(5,2) CHECK (accuracy_score BETWEEN 0 AND 100),
  fluency_score  NUMERIC(5,2) CHECK (fluency_score  BETWEEN 0 AND 100),
  completeness   NUMERIC(5,2) CHECK (completeness   BETWEEN 0 AND 100),
  -- Diem tung am tiet tu dich vu ngoai: [{syllable, score}]
  syllables      JSONB  NOT NULL DEFAULT '[]',
  -- BUS-09 dieu kien 3: doi nha cung cap van doc lai duoc diem cu thuoc nha nao
  provider       VARCHAR(20) NOT NULL,
  credit_cost    INTEGER NOT NULL CHECK (credit_cost >= 0),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_shadow_user_video ON learning.shadowing_attempts(user_id, video_id, subtitle_index);
```

> 🔴 **KHÔNG có cột lưu file audio — đây là chủ ý.** Giọng nói là **dữ liệu sinh trắc**.
> Giữ lại tạo nghĩa vụ bảo vệ dữ liệu mà dự án không cần gánh, và tốn dung lượng lớn.
> Luồng: client gửi audio → server ta → dịch vụ ngoài → nhận điểm → ghi bảng này →
> **bỏ audio** (`BR-122-4`).

> **`provider` là `NOT NULL` không có `DEFAULT`.** Bắt buộc code phải ghi rõ dịch vụ nào
> đã chấm. Đặt mặc định là mở đường cho việc quên ghi, rồi sau không biết điểm cũ của nhà
> nào — đúng điều `BUS-09` điều kiện 3 muốn tránh.

> **Không có `CHECK` ràng `syllables` khớp `expected_text`.** Kiểm số âm tiết là việc của
> server trước khi `INSERT` (`BR-122-6`), không phải của DB — vì tách âm tiết tiếng Trung
> cần từ điển pinyin mà PostgreSQL không có.

### 4.3 · `learning.plans` — feature 6.1

```sql
CREATE TABLE learning.plans (
  code          VARCHAR(20)   PRIMARY KEY
                CHECK (code IN ('FREE','PREMIUM','PREMIUM_PLUS')),
  name          VARCHAR(100)  NOT NULL,
  description   TEXT,
  -- Han cua GOI. NULL = vinh vien (goi FREE).
  duration_days SMALLINT      CHECK (duration_days IS NULL OR duration_days > 0),
  -- Han muc moi CHU KY THANG cho tung feature_code.
  benefits      JSONB         NOT NULL DEFAULT '{}',
  price         NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  is_active     BOOLEAN       NOT NULL DEFAULT true,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

  CONSTRAINT ck_plan_free CHECK (code <> 'FREE' OR (price = 0 AND duration_days IS NULL))
);
```

| `code` | `price` | `duration_days` | `benefits` (mỗi tháng) |
|---|---|---|---|
`FREE` | 0 | `NULL` vĩnh viễn | **10 lượt mỗi tính năng** |
`PREMIUM` | 99.000 | 30 | 50 AI · 200 dịch · 5 chấm bài · 300 chat |
`PREMIUM_PLUS` | 199.000 | 30 | 200 · 1000 · 20 · 1000 |

> ⚠️ **`duration_days` khác `benefits`.** `duration_days` là hạn của **gói**, `benefits` là
> hạn mức mỗi **chu kỳ tháng**. Gói `FREE` vĩnh viễn nhưng lượt vẫn reset mỗi tháng theo
> `users.usage_reset_at`.
>
> Con số 10 lượt lấy từ **feature tree 6.1**: *"Mỗi tính năng tốn phí dùng 10 lượt free"*.
> Bản SQL thử nghiệm trước đặt `FREE` = 0 lượt — **sai**, đã sửa theo feature tree.

---

## 7 · Cột thêm vào bảng có sẵn

### 5.1 · `auth.users` — 4 cột gói (18 → 22 cột)

```sql
ALTER TABLE auth.users
  ADD COLUMN plan_code       VARCHAR(20) NOT NULL DEFAULT 'FREE'
             REFERENCES learning.plans(code),      -- FK cross-schema, xem §6
  ADD COLUMN plan_expires_at TIMESTAMPTZ,
  -- Luot free da dung theo feature_code. Reset dau moi chu ky.
  ADD COLUMN free_usage      JSONB       NOT NULL DEFAULT '{}',
  ADD COLUMN usage_reset_at  TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE auth.users
  ADD CONSTRAINT ck_users_plan_expiry CHECK (
    (plan_code = 'FREE' AND plan_expires_at IS NULL)
    OR (plan_code <> 'FREE' AND plan_expires_at IS NOT NULL)
  );

CREATE INDEX ix_users_plan_exp ON auth.users(plan_expires_at)
  WHERE plan_expires_at IS NOT NULL;
```

> **Vì sao gói nằm trên `users` chứ không phải bảng `user_subscriptions` riêng:** gói được đọc
> **cùng lúc** với mọi lần kiểm quyền. Tách ra là thêm một JOIN vào đường nóng nhất hệ thống.

### 5.2 · `learning.credit_cards` — 4 cột phễu (13 → 17 cột)

CNHSK là **sản phẩm phễu** cho một hệ sinh thái khác (xem Hiến pháp §Mô hình kinh doanh).
Trước bản này, cả ba luồng phát mã thành dòng giống nhau → **không đo được phễu**.

```sql
ALTER TABLE learning.credit_cards
  ADD COLUMN channel      VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
             CHECK (channel IN ('ECOSYSTEM_GIFT','PARTNER_BATCH','DIRECT')),
  ADD COLUMN partner_code VARCHAR(50),    -- ben thu ba nhan lo; NULL voi DIRECT
  ADD COLUMN campaign     VARCHAR(50),    -- dot phat, de do ti le doi
  ADD COLUMN issued_to    VARCHAR(255);   -- email nguoi nhan; chi ECOSYSTEM_GIFT

ALTER TABLE learning.credit_cards
  ADD CONSTRAINT ck_card_partner CHECK (
    channel <> 'PARTNER_BATCH' OR partner_code IS NOT NULL),
  ADD CONSTRAINT ck_card_gift CHECK (
    channel <> 'ECOSYSTEM_GIFT' OR issued_to IS NOT NULL);

CREATE INDEX ix_card_funnel  ON learning.credit_cards(channel, campaign, status);
CREATE INDEX ix_card_partner ON learning.credit_cards(partner_code, status)
  WHERE partner_code IS NOT NULL;
```

| `channel` | Ai nhận | Mục đích |
|---|---|---|
`ECOSYSTEM_GIFT` | khách sẵn có của hệ sinh thái | tặng trải nghiệm — đây là phễu |
`PARTNER_BATCH` | chủ hệ sinh thái phân phối tiếp | thu khách lẻ |
`DIRECT` | admin tự phát | khuyến mãi, bù lỗi |

**Không thêm bảng `partners`:** hiện chỉ có **một** bên thứ ba. Một bảng cho một dòng dữ liệu
là ngược YAGNI. Khi có bên thứ hai thì tách, lúc đó `partner_code` thành FK.

Đã thử nghiệm trên pg-mem: **6/6 ràng buộc đúng** — chặn khi thiếu `partner_code`, chặn khi
thiếu `issued_to`, chặn `channel` không hợp lệ, không chặn sai `DIRECT`.

Truy vấn đo phễu (`SM-05` trong `CONTEXT.md`):

```sql
SELECT channel, campaign,
       COUNT(*) FILTER (WHERE status = 'USED') AS da_doi,
       COUNT(*)                                AS tong,
       ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'USED') / COUNT(*), 1) AS ti_le
FROM learning.credit_cards
GROUP BY channel, campaign ORDER BY channel, campaign;
```

### 5.3 · `learning.credit_transactions` — cột `seq` chống đua

Thiết kế trước bỏ bảng `user_credits` (số dư = `balance_after` dòng mới nhất). Điều đó làm **mất dòng để
`SELECT ... FOR UPDATE` khoá**.

```sql
ALTER TABLE learning.credit_transactions ADD COLUMN seq INTEGER;

-- Backfill cho du lieu san co (hien chua deploy nen bang rong).
WITH numbered AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY id) AS rn
  FROM learning.credit_transactions
)
UPDATE learning.credit_transactions t SET seq = n.rn
FROM numbered n WHERE t.id = n.id;

ALTER TABLE learning.credit_transactions
  ALTER COLUMN seq SET NOT NULL,
  ADD CONSTRAINT ck_tx_seq CHECK (seq > 0);

CREATE UNIQUE INDEX ux_tx_user_seq ON learning.credit_transactions(user_id, seq);
```

#### Vì sao `seq` mà không phải cách khác — đã chạy thử cả ba

**Thử 1 — `ck_tx_balance` có tự chặn đua?** **KHÔNG.**

```
A đọc balance = 70 · B đọc balance = 70   ← cùng giá trị
→ id=3 before=70 after=60 · id=4 before=70 after=60
Tổng amount = 50 nhưng balance_after cuối = 60 → LỆCH 10, MẤT TIỀN
```

Ràng buộc mạnh nhất của schema không chặn được đua — từng dòng riêng lẻ **vẫn hợp lệ**
(`60 = 70 + (−10)` đúng cho cả hai).

**Thử 2 — `UNIQUE(user_id, balance_before)`?** Chặn đua đúng **nhưng chặn SAI giao dịch hợp lệ:**

```
TOPUP  +100 → 100   OK
DEDUCT  -10 →  90   OK
REFUND  +10 → 100   OK
DEDUCT  -10 →       CHẶN SAI ← balance_before=100 đã dùng ở dòng 1
```

Người học nạp 100, trừ 10, được hoàn 10, rồi **kẹt tài khoản vĩnh viễn**. Retry không cứu
được: đọc lại vẫn ra 100.

**Thử 3 — `UNIQUE(user_id, seq)`** ✅ **chọn cái này**

```
seq=1 TOPUP  +100 → 100  OK
seq=2 DEDUCT  -10 →  90  OK
seq=2 lần hai        → DB CHẶN (B retry theo BUS-17)
seq=3 REFUND  +10 → 100  OK
seq=4 DEDUCT  -10 →  90  OK  ← balance_before=100 LẶP LẠI vẫn qua
Tổng amount=90 = balance_after cuối → KHỚP
```

Giữ ưu điểm (DB chặn hộ, không phụ thuộc người code nhớ gọi advisory lock), bỏ nhược điểm
(không chặn sai). Giá: thêm 1 cột `INTEGER`.

### 5.4 · `auth.user_roles` — bỏ FK, thêm `CHECK` và index

```sql
ALTER TABLE auth.user_roles
  ADD CONSTRAINT ck_ur_role_code CHECK (
    role_code IN ('USER','TEACHER','MANAGER',
                  'CONTENT_ADMIN','FINANCE_ADMIN','SUPER_ADMIN'));

DROP TABLE auth.roles CASCADE;   -- CASCADE chi go FK, KHONG xoa du lieu user_roles

-- user_roles la bang DUY NHAT truoc bản này khong co index nao ngoai PK.
-- PK la (user_id, role_code) nen truy van "ai co role ADMIN" phai scan toan bang.
CREATE INDEX ix_ur_role ON auth.user_roles(role_code, user_id);
```

---

## 8 · 17 khoá ngoại cắt qua ranh giới module

### 6.1 · Danh sách

| Bảng nguồn | Schema | → Bảng đích | Schema |
|---|---|---|---|
`questions` `exams` `user_progress` `attempts` `study_events` `collections` `credit_cards` `credit_transactions` `service_requests` `ai_chats` | `learning` | `users` | `auth` |
`users.plan_code` | `auth` | `plans` | `learning` |
`posts` `comments` `reactions` `moderation_cases` | `community` | `users` | `auth` |
`contests` | `community` | `exams` | `learning` |
`audit_logs` | `shared` | `users` | `auth` |

### 6.2 · Vì sao cho phép — `AC-03` cần nới

`AC-03` hiện ghi: *"CẤM module này truy cập thẳng bảng thuộc schema của module kia."*
Một FK **là** một tham chiếu trực tiếp, nên hai điều này không thể cùng đúng.

**Mâu thuẫn này có từ v5**, không phải bản này gây ra: v5 có 59 bảng cũng cần
`posts.author_id → users.id`.

Phân biệt hai thứ khác nhau:

| Việc | Cho phép? | Ai canh |
|---|---|---|
FK từ bảng module A tới bảng module B | ✅ **Cho** — ràng buộc toàn vẹn ở tầng DB | PostgreSQL |
Module A dùng repository đọc bảng module B | ❌ **Cấm** — phải qua lớp `api` | **ArchUnit** |

Bỏ 17 FK để tuân `AC-03` tuyệt đối sẽ **mất toàn vẹn dữ liệu**: xoá `user` không chặn được,
`post` trỏ tới user không tồn tại. Đây đúng là lỗi các bảng `_refs` của v5 đã gặp — và bản trước
đã xoá chúng vì chính lý do đó.

### 6.3 · ArchUnit canh gì

8 luật trong `ModuleBoundaryTest.java` kiểm **package Java**:

```java
noClasses().that().resideInAPackage("..community..")
  .should().dependOnClassesThat().resideInAPackage("..auth.entity..")
```

Không có luật nào về schema. Nên ranh giới module được thực thi ở **tầng code**, và FK
cross-schema **không phá** nó.

---

## 9 · Index bắt buộc

| Bảng | Index | Dùng cho |
|---|---|---|
`auth.users` | `(plan_expires_at) WHERE NOT NULL` | job hạ gói hết hạn |
`auth.user_roles` | `(role_code, user_id)` | màn quản trị người dùng |
`auth.auth_tokens` | `(user_id) WHERE revoked_at IS NULL` | kiểm token hợp lệ |
`learning.user_progress` | `(user_id, next_review_at)` · `(user_id, mastery)` · `(user_id, target_type, state)` | FSRS · xếp theo yếu · lọc theo loại |
`learning.attempts` | `(kind, game_code, score DESC) WHERE SUBMITTED` | bảng xếp hạng |
`learning.credit_transactions` | `(user_id, id DESC)` · **`UNIQUE(user_id, seq)`** | tra số dư · chống đua |
`learning.credit_cards` | **`(channel, campaign, status)`** · `(partner_code, status)` | đo phễu `SM-05` |
`learning.videos` | `(hsk_level, status)` · `(topic_id)` WHERE PUBLISHED | danh sách video |
`learning.service_requests` | `(kind, status, deadline_at)` | hàng đợi chấm bài |
`shared.audit_logs` | `(actor_id, created_at DESC)` · `(action, created_at DESC)` | tra audit |

---

## 10 · Cấu hình cần đổi

### 7.1 · `application.yaml`

```yaml
spring:
  flyway:
    enabled: true
    schemas: auth,learning,community,shared   # them 'shared'
    default-schema: learning                  # bang lich su flyway_schema_history
    create-schemas: true
```

### 7.2 · `GRANT` cho `svc_app`

**Một** tài khoản `svc_app` (giữ `AC-04`), cấp quyền trên **cả 4** schema.

Modular Monolith có **một** ứng dụng Spring Boot với **một** `DataSource` — không thể đổi
tài khoản DB theo từng module. Nên một `svc_app` là đúng, không phải thiếu sót.

```sql
GRANT USAGE ON SCHEMA auth, learning, community, shared TO svc_app;

GRANT SELECT, INSERT, UPDATE, DELETE
  ON ALL TABLES IN SCHEMA auth, learning, community, shared TO svc_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA auth, learning, community, shared TO svc_app;

-- Bang tao sau nay (boi Flyway) cung tu dong co quyen
ALTER DEFAULT PRIVILEGES IN SCHEMA auth, learning, community, shared
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO svc_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth, learning, community, shared
  GRANT USAGE, SELECT ON SEQUENCES TO svc_app;
```

> `svc_app` **KHÔNG** được `CREATE`/`DROP` — Flyway dùng tài khoản khác (owner) để migrate.

### 7.3 · `search_path`

```sql
ALTER ROLE svc_app SET search_path = learning, auth, community, shared, public;
```

Không set thì mọi query không có prefix sẽ fail. Nhưng **không dựa vào `search_path`** —
entity vẫn phải khai schema tường minh (§7.4).

### 7.4 · 🔴 Hibernate `@Table(schema=...)` — chỗ dễ sai nhất

**Mỗi entity phải khai schema tường minh:**

```java
@Entity
@Table(name = "users", schema = "auth")
public class User { ... }

@Entity
@Table(name = "plans", schema = "learning")
public class Plan { ... }

@Entity
@Table(name = "audit_logs", schema = "shared")
public class AuditLog { ... }
```

> ⚠️ Thiếu `schema` ở **một** entity → Hibernate dùng `default-schema` → tìm bảng ở
> `learning` → **lỗi runtime, không lỗi compile**. Lỗi chỉ hiện khi gọi tới entity đó.
>
> `ddl-auto: validate` bắt được lỗi này **lúc khởi động** — đó là lý do `AC-05` cấm đặt
> `update`. Giữ `validate` để lỗi hiện sớm.

---

## 11 · Thứ tự migration

| File | Nội dung |
|---|---|
`V1__core_schema.sql` | **sửa**: thêm `CREATE SCHEMA` ×4 + prefix cho 24 bảng |
`V2__community.sql` | **sửa**: prefix `community.` cho 5 bảng |
`V3__content_and_plans.sql` | **mới**: `pron_stages` · `videos` (kèm `source_type` + `external_id`) · `plans` + 4 cột gói trên `users` |
`V5__video_practice.sql` | **mới** (2026-10-08): `dictation_attempts` · `shadowing_attempts` — feature 1.6 V2. Tách file riêng vì thuộc V2, không chặn MVP |
`V4__funnel_and_ledger.sql` | **mới**: 4 cột phễu · cột `seq` + `UNIQUE` · bỏ `roles` + index |

> ⚠️ `AC-05`: *"File migration đã chạy thì KHÔNG được sửa."* `V1`/`V2` **chưa chạy ở đâu**
> (DB chưa deploy, `git remote` vừa tạo) nên sửa được. Nếu đã chạy trên máy ai thì người đó
> phải `DROP DATABASE` rồi migrate lại.

---

## 12 · Sáu quy tắc bảng dính tiền

Áp dụng cho `credit_cards` và `credit_transactions`:

| # | Quy tắc | Thực thi ở đâu |
|---|---|---|
| 1 | Mã sinh bằng `SecureRandom`, ≥16 ký tự | Code + ArchUnit chặn `java.util.Random` |
| 2 | **Chỉ lưu `code_hash`** | Schema không có cột mã thô |
| 3 | `SELECT FOR UPDATE` rồi đổi trạng thái cùng transaction | Code |
| 4 | 5 lần nhập sai mã/giờ mỗi tài khoản | Bộ đếm riêng cho thao tác nhập mã thẻ trong Redis; không dùng bộ đếm đăng nhập của UC-012 |
| 5 | Không ghi mã vào log | Code + test đọc log |
| 6 | Mọi đổi điểm ghi `balance_before`/`balance_after` | **`CHECK` ở DB** |

> Quy tắc 6 là quy tắc duy nhất DB tự thực thi được — và là quy tắc quan trọng nhất.

---


## 13 · Năm bảng tuyệt đối không đụng

| Bảng | Vì sao |
|---|---|
| `knowledge_points` | Trục nối từ điển ↔ đề thi ↔ tiến độ. Bỏ là mất "yếu chỗ nào luyện chỗ đó" |
| `question_knowledge_points` | Nhãn kiến thức. **Không sửa được nếu không nhập lại dữ liệu** |
| `user_progress` | Mastery + lịch ôn FSRS của mọi người học |
| `credit_transactions` | Sổ cái tiền thật — **không bao giờ xoá dòng nào** |
| `attempt_items` | Từng câu trả lời. Gốc của mọi phân tích lỗi sai |

---


## 14 · Mười một khoảng trống đã giải quyết

| # | Khoảng trống (từ đặc tả UC) | Cách bản trước giải quyết |
|---|---|---|
| 1 | Không có bảng ghi tiến độ video | `user_progress.target_type = 'VIDEO'` + `position_ms` |
| 2 | Không có bảng lưu `challenge_id` | `attempts.kind = 'WRITING_CHALLENGE'` |
| 3 | Không có bảng lưu lượt chơi game | `attempts.kind = 'GAME'` |
| 4 | Thiếu trạng thái treo/ban tài khoản | `users.suspended_at`, `suspended_until`, `banned_at`; chặn đăng nhập sai của UC-012 nằm trong Redis |
| 5 | Thiếu `EMAIL_VERIFY` token type | `auth_tokens.token_type` CHECK có |
| 6 | `knowledge_points` thiếu `skill_type` | Có, CHECK 6 kỹ năng |
| 7 | Không có bảng log gửi nhắc | `study_events.event_type = 'REMINDER_SENT'` + unique |
| 8 | Chưa chốt nơi lưu bản nháp bài thi | `attempts.draft_answers JSONB` |
| 9 | Chưa có bảng chi tiết lỗi nhập | `service_requests.error_rows JSONB` |
| 10 | Thiếu index tra nghĩa Việt | `gin (meaning_norm gin_trgm_ops)` |
| 11 | Mâu thuẫn `cheat_events` JSONB vs AC-09 | Nhật ký vào `audit_logs` |

**Cộng thêm, giải quyết bằng thiết kế (không cần code):**

| Lỗi | Cách thiết kế chặn |
|---|---|
| `PROGRESS_PERCENT_MISMATCH` | Một bảng `user_progress`, không còn hai nguồn |
| `BALANCE_UPDATED_WITHOUT_LEDGER` | Không có bảng `user_credits` — số dư **là** sổ cái |
| `COPIED_REVIEW_SCHEDULE` | Lịch ôn gắn `collection_items.id` → bản sao tự reset |
| `UNIQUE_VIOLATION` mở tầng/chủ đề | Khoá chính `(user_id, target_type, target_id)` |
| `BALANCE_BEFORE_AFTER_MISMATCH` | `CHECK (balance_after = balance_before + amount)` |

---


## 15 · Bảy quyết định tôi chốt giúp

Bạn chọn "tôi chốt giúp, ghi rõ lý do". Đây là bảy mục, **đổi được** nếu bạn không đồng ý.

| # | Mục treo | Tôi chốt | Vì sao |
|---|---|---|---|
| 1 | "10 lượt free" là bao lâu | **Vĩnh viễn mỗi tài khoản** | Đơn giản nhất, không cần job reset, đúng nghĩa dùng thử. Đếm bằng `COUNT(*)` trên `credit_transactions WHERE feature_code = ?` |
| 2 | `teacher_payout` bao nhiêu | **70%** số điểm người học trả | Để lại 30% cho hệ thống. `CHECK (payout <= original_amount)` |
| 3 | Hạn nhận / hạn chấm bài | **24h nhận · 48h chấm** | Đủ để teacher sắp xếp, không quá lâu để người học chờ |
| 4 | 6 kỹ năng là gì | `LISTENING` `READING` `WRITING` `VOCAB` `GRAMMAR` `CHARACTER` | Phủ 4 kỹ năng HSK + 2 trục nội dung. Sửa sau phải nhập lại dữ liệu |
| 5 | Nơi lưu bản nháp bài thi | **`attempts.draft_answers JSONB`** | Không phụ thuộc `TODO(REDIS_PLACEMENT)`. Ghi 30 giây/lần, không phải "ghi liên tục" theo nghĩa AC-09 cấm |
| 6 | Thời hạn lưu hội thoại AI | **90 ngày** | Đủ để người học xem lại, không tích trữ PII vô hạn |
| 7 | Cấu hình hệ thống | **Không có bảng** — hằng số trong `application.yaml` | **SCR-078 System Settings đã có trong bộ 79 màn**, triển khai Deferred theo UC-116. Danh sách tham số và nơi lưu vẫn chưa chốt; đề xuất thêm bảng/cache không đồng nghĩa với thêm một màn mới |

---


## 16 · Đánh đổi của thiết kế này

Gộp bảng không miễn phí. Bốn điều phải chấp nhận:

| # | Đánh đổi | Mức độ |
|---|---|---|
| 1 | **Bảng đa hình không có khoá ngoại thật.** `user_progress.target_id` trỏ tới `knowledge_points` hay `topics` tuỳ `target_type` — DB không kiểm được | ⚠️ Cần kiểm ở application. Đổi lại: giảm 5 bảng và xoá lớp lỗi lệch dữ liệu |
| 2 | **`questions.options JSONB` không dùng được partial unique index** để đảm bảo đúng 1 đáp án đúng | ⚠️ Kiểm bằng `CHECK` với `jsonb_array_length` + validate ở import |
| 3 | **Số dư tính từ dòng mới nhất** — truy vấn phức tạp hơn `SELECT balance FROM user_credits` | ⚠️ Cần index `(user_id, id DESC)`. Đổi lại: không bao giờ lệch |
| 4 | **`attempts` là bảng nóng nhất** — mọi loại lượt làm bài đổ vào đây | ⚠️ Partition theo `kind` nếu dữ liệu lớn. Với quy mô đồ án thì chưa cần |

> **Điều tôi không khuyên gộp thêm:** `questions` và `lexemes`. Hai bảng này khác vòng đời
> (câu hỏi có kiểm duyệt, từ điển không) và khác tần suất ghi. Gộp xuống 23 bảng không đáng.

---
## 17 · Năm điều còn mở

| # | Mục | Chốt tạm | Ảnh hưởng nếu đổi |
|---|---|---|---|
| 1 | `seq` bắt đầu từ 0 hay 1 | **1** | chỉ đổi hằng số |
| 2 | Danh sách 6 kỹ năng (`skill_type`) | 6 kỹ năng | 🔴 **đổi sau phải nhập lại `question_knowledge_points`** |
| 3 | Thời hạn lưu hội thoại AI | 90 ngày | chỉ đổi hằng số |
| 4 | Bảng cấu hình hệ thống | không — hằng số trong `application.yaml` | cần duyệt nơi lưu và danh sách tham số; SCR-078 System Settings đã có trong bộ 79 màn, triển khai Deferred theo UC-116 |
| 5 | Lịch sử đăng nhập | `audit_logs` với `action='LOGIN'` | nếu thêm bảng riêng thì 32 bảng |

Mục 2 **gấp nhất** — `question_knowledge_points` nằm trong danh sách "không được đụng".

---


## 18 · RFC sửa Hiến pháp

> Theo §Governance: *"Người đề xuất viết RFC nêu động cơ, thay đổi cụ thể, đánh giá rủi ro."*
> Cần cả nhóm đồng thuận.

### 9.1 · Động cơ

Ba lỗ hổng chức năng (§1.1) làm ba feature **không chạy được**, trong đó 6.1 là nguồn thu.
Và `AC-04` đang ghi con số của DB v5 đã bị thay thế hai lần.

### 9.2 · `AC-04` — sửa

| | Nội dung |
|---|---|
**Hiện tại** | *"Một database `cnhsk_db`, ba schema (`auth` 4 bảng, `learning` 40 bảng, `community` 15 bảng), một tài khoản `svc_app`, một `DataSource` trong Spring."* |
**Đề xuất** | *"Một database `cnhsk_db`, **bốn** schema khớp bốn module: `auth` (3 bảng) · `learning` (24) · `community` (5) · `shared` (1) = **33 bảng**. Một tài khoản `svc_app` được `GRANT` trên cả bốn schema. Một `DataSource` trong Spring."* |

### 9.3 · `AC-03` — nới

| | Nội dung |
|---|---|
**Hiện tại** | *"Module gọi nhau CHỈ qua lớp `api` bằng lời gọi hàm. CẤM module này truy cập thẳng bảng thuộc schema của module kia."* |
**Đề xuất** | *"Module gọi nhau CHỈ qua lớp `api` bằng lời gọi hàm. CẤM module này **đọc hoặc ghi** bảng thuộc schema của module kia (qua repository, entity, hay SQL trực tiếp). **Khoá ngoại cross-schema được phép** — nó là ràng buộc toàn vẹn ở tầng DB, không phải truy cập dữ liệu. ArchUnit canh phần cấm ở tầng code."* |

### 9.4 · Rủi ro

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
**Không verify được trước khi chạy DB thật** | **cao** | pg-mem cho `CREATE SCHEMA` chạy nhưng **bỏ qua prefix** — không mô phỏng schema. Bắt buộc test PostgreSQL 18 (`T-00e`) |
Thiếu `@Table(schema=...)` ở một entity | cao | `ddl-auto: validate` bắt lúc khởi động. Thêm test đếm entity có khai schema |
Nới `AC-03` bị hiểu thành "cho phép mọi thứ" | trung | Ghi rõ **đọc/ghi vẫn cấm**, chỉ FK được phép. ArchUnit không đổi |
`GRANT` thiếu một schema | trung | `ALTER DEFAULT PRIVILEGES` cho bảng tạo sau |

### 9.5 · Thay đổi kéo theo

| File | Sửa |
|---|---|
`constitution.md` | `AC-03` · `AC-04` · bảng tài liệu tham chiếu  |
`CONSTITUTION_CHANGELOG.md` | mục v1.3.0 |
`kien-truc.md` | sơ đồ dòng 48–56: 3 → **4 schema** · dòng 15, 595, 712: "3 schema · 59 bảng" |
`application.yaml` | `schemas` thêm `shared` · `create-schemas: true` |
`specs/001-auth-rbac/plan.md` | §4.2 đã cập nhật |

---

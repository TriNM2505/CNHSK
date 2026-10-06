-- ============================================================
-- CNHSK — Database v6 · Migration V1: toan bo schema MVP (19 bang)
-- Nguon thiet ke: docs/cnhsk-database-v6.md
-- PostgreSQL 15+
--
-- Thu tu tao bang theo phu thuoc khoa ngoai:
--   A users/roles -> B lexemes/knowledge_points -> C topics
--   -> D questions/exams -> E progress/attempts -> F collections
--   -> G credits/requests -> H audit/ai_chats
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- ============================================================
-- NHOM A · Tai khoan va phan quyen
-- ============================================================

CREATE TABLE users (
  id                 BIGSERIAL PRIMARY KEY,
  email              VARCHAR(255) NOT NULL UNIQUE,
  password_hash      VARCHAR(255) NOT NULL,
  display_name       VARCHAR(100) NOT NULL,
  avatar_url         TEXT,
  hsk_level          SMALLINT CHECK (hsk_level BETWEEN 1 AND 9),

  -- Trang thai tai khoan (muc A quyet dinh v2)
  email_verified_at  TIMESTAMPTZ,
  failed_login_count SMALLINT    NOT NULL DEFAULT 0,
  locked_until       TIMESTAMPTZ,
  suspended_at       TIMESTAMPTZ,
  suspended_until    TIMESTAMPTZ,
  banned_at          TIMESTAMPTZ,
  banned_by          BIGINT,
  ban_reason         TEXT,

  -- Nhac hoc: gio Viet Nam, NULL = tat
  reminder_time      TIME,
  reminder_channels  VARCHAR(50) NOT NULL DEFAULT 'WEB',

  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_users_ban_reason CHECK (banned_at IS NULL OR ban_reason IS NOT NULL),
  CONSTRAINT fk_users_banned_by  FOREIGN KEY (banned_by) REFERENCES users(id)
);
CREATE INDEX ix_users_active ON users(id) WHERE banned_at IS NULL;

CREATE TABLE roles (
  code        VARCHAR(20) PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  description TEXT
);

-- 6 role trong DB. GUEST KHONG phai dong o day - la trang thai "chua co token".
INSERT INTO roles (code, name, description) VALUES
  ('USER',          'Nguoi hoc',            'Hoc, thi, choi game, dang bai cho duyet'),
  ('TEACHER',       'Giao vien',            'Duyet cau hoi AI, sua noi dung, cham bai thue'),
  ('MANAGER',       'Quan ly cong dong',    'Duyet bai dang, xu ly bao cao vi pham'),
  ('CONTENT_ADMIN', 'Quan tri noi dung',    'Nhap du lieu, quan ly de thi va cuoc thi'),
  ('FINANCE_ADMIN', 'Quan tri tai chinh',   'Sinh ma the, xem so cai, xu ly tranh chap'),
  ('SUPER_ADMIN',   'Quan tri he thong',    'Quan ly nguoi dung, cap va thu hoi role');

CREATE TABLE user_roles (
  user_id    BIGINT      NOT NULL REFERENCES users(id),
  role_code  VARCHAR(20) NOT NULL REFERENCES roles(code),
  granted_by BIGINT      REFERENCES users(id),
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_code),
  -- Chan tu cap role cho chinh minh (UC-115 SELF_ROLE_GRANT)
  CONSTRAINT ck_ur_self CHECK (granted_by IS NULL OR granted_by <> user_id)
);

CREATE TABLE auth_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id),
  token_type VARCHAR(20) NOT NULL
             CHECK (token_type IN ('REFRESH','RESET_PASSWORD','EMAIL_VERIFY')),
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_tokens_user ON auth_tokens(user_id) WHERE revoked_at IS NULL;

-- ============================================================
-- NHOM B · Noi dung hoc
-- ============================================================

CREATE TABLE lexemes (
  id           BIGSERIAL PRIMARY KEY,
  kind         VARCHAR(10)  NOT NULL CHECK (kind IN ('CHAR','WORD','GRAMMAR')),
  text         VARCHAR(200) NOT NULL,
  pinyin       VARCHAR(200),
  pinyin_norm  VARCHAR(200),               -- khong dau, chu thuong - cho tra pinyin
  sino_viet    VARCHAR(200),
  meaning_vi   TEXT NOT NULL,
  meaning_norm TEXT,                       -- khong dau - cho tra nghia Viet
  hsk_level    SMALLINT CHECK (hsk_level BETWEEN 1 AND 9),
  audio_url    TEXT,
  radical      VARCHAR(10),
  stroke_count SMALLINT,
  extra        JSONB NOT NULL DEFAULT '{}',  -- CHI DOC (AC-09)
  frequency    INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_lexeme UNIQUE (kind, text)
);
CREATE INDEX ix_lex_pinyin   ON lexemes(pinyin_norm);
CREATE INDEX ix_lex_kind_hsk ON lexemes(kind, hsk_level);
CREATE INDEX ix_lex_meaning  ON lexemes USING gin (meaning_norm gin_trgm_ops);

CREATE TABLE lexeme_links (
  parent_id BIGINT      NOT NULL REFERENCES lexemes(id),
  child_id  BIGINT      NOT NULL REFERENCES lexemes(id),
  link_type VARCHAR(20) NOT NULL CHECK (link_type IN ('CONTAINS','RELATED','SYNONYM')),
  PRIMARY KEY (parent_id, child_id, link_type),
  CONSTRAINT ck_link_self CHECK (parent_id <> child_id)
);
CREATE INDEX ix_link_child ON lexeme_links(child_id);

CREATE TABLE knowledge_points (
  id         BIGSERIAL PRIMARY KEY,
  code       VARCHAR(50)  NOT NULL UNIQUE,
  title      VARCHAR(200) NOT NULL,
  skill_type VARCHAR(20)  NOT NULL
             CHECK (skill_type IN ('LISTENING','READING','WRITING','VOCAB','GRAMMAR','CHARACTER')),
  hsk_level  SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  lexeme_id  BIGINT REFERENCES lexemes(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_kp_skill ON knowledge_points(skill_type, hsk_level);

-- ============================================================
-- NHOM C · Chu de
-- ============================================================

CREATE TABLE topics (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  hsk_level   SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  order_index INTEGER  NOT NULL DEFAULT 0,
  -- Tien quyet dang mang. Kiem CHU TRINH bang DFS o application truoc khi luu.
  prereq_ids  BIGINT[] NOT NULL DEFAULT '{}',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_topic_level ON topics(hsk_level, order_index);

CREATE TABLE topic_items (
  topic_id           BIGINT NOT NULL REFERENCES topics(id),
  lexeme_id          BIGINT REFERENCES lexemes(id),
  knowledge_point_id BIGINT REFERENCES knowledge_points(id),
  order_index        INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ck_topic_item CHECK (lexeme_id IS NOT NULL OR knowledge_point_id IS NOT NULL)
);
-- Khong the dung PRIMARY KEY vi co cot NULL -> dung hai unique index rieng
CREATE UNIQUE INDEX uq_ti_lex ON topic_items(topic_id, lexeme_id) WHERE lexeme_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ti_kp  ON topic_items(topic_id, knowledge_point_id) WHERE knowledge_point_id IS NOT NULL;
CREATE INDEX ix_ti_topic ON topic_items(topic_id, order_index);

-- ============================================================
-- NHOM D · Cau hoi va de thi
-- ============================================================

CREATE TABLE questions (
  id            BIGSERIAL PRIMARY KEY,
  qtype         VARCHAR(20) NOT NULL CHECK (qtype IN
                ('MULTIPLE_CHOICE','TRUE_FALSE','IMAGE_MATCH','SENTENCE_MATCH',
                 'FILL_BLANK','SENTENCE_ORDER','ESSAY')),
  hsk_level     SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  stem          TEXT NOT NULL,
  options       JSONB NOT NULL DEFAULT '[]',   -- [{key,text,is_correct}] CHI DOC
  correct_key   VARCHAR(50),
  explanation   TEXT,
  audio_url     TEXT,
  image_url     TEXT,

  source        VARCHAR(10) NOT NULL DEFAULT 'IMPORT'
                CHECK (source IN ('IMPORT','AI','MANUAL')),
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
                CHECK (status IN ('PENDING_REVIEW','APPROVED','REJECTED','ARCHIVED')),
  reviewed_by   BIGINT REFERENCES users(id),
  reviewed_at   TIMESTAMPTZ,
  review_reason TEXT,
  reusable      BOOLEAN NOT NULL DEFAULT true,
  request_id    BIGINT,                        -- FK them o V7 (service_requests)
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Cau AI khong the APPROVED neu chua co nguoi duyet (rang buoc loi 3.3)
  CONSTRAINT ck_q_ai_review CHECK (
    source <> 'AI' OR status <> 'APPROVED' OR reviewed_by IS NOT NULL
  ),
  CONSTRAINT ck_q_reject CHECK (status <> 'REJECTED' OR review_reason IS NOT NULL),
  -- Cau trac nghiem phai co it nhat 2 lua chon
  CONSTRAINT ck_q_options CHECK (
    qtype NOT IN ('MULTIPLE_CHOICE','TRUE_FALSE','IMAGE_MATCH','SENTENCE_MATCH')
    OR jsonb_array_length(options) >= 2
  )
);
CREATE INDEX ix_q_serve ON questions(status, qtype, hsk_level) WHERE status = 'APPROVED';
CREATE INDEX ix_q_review ON questions(status, created_at) WHERE status = 'PENDING_REVIEW';

CREATE TABLE question_knowledge_points (
  question_id        BIGINT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
  knowledge_point_id BIGINT NOT NULL REFERENCES knowledge_points(id),
  PRIMARY KEY (question_id, knowledge_point_id)
);
-- Duong nong cua UC-040, UC-041: tra nguoc tu diem kien thuc ve cau hoi
CREATE INDEX ix_qkp_kp ON question_knowledge_points(knowledge_point_id);

CREATE TABLE exams (
  id            BIGSERIAL PRIMARY KEY,
  code          VARCHAR(50) NOT NULL UNIQUE,
  title         VARCHAR(200) NOT NULL,
  hsk_level     SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 6),
  -- [{index, name, type, duration_min}] - CHI DOC
  sections      JSONB NOT NULL DEFAULT '[]',
  total_score   INTEGER NOT NULL DEFAULT 300,
  status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  published_at  TIMESTAMPTZ,
  created_by    BIGINT REFERENCES users(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_exam_list ON exams(status, hsk_level) WHERE status = 'PUBLISHED';

CREATE TABLE exam_questions (
  exam_id       BIGINT NOT NULL REFERENCES exams(id),
  question_id   BIGINT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
  section_index SMALLINT NOT NULL DEFAULT 0,
  order_index   INTEGER  NOT NULL DEFAULT 0,
  points        SMALLINT NOT NULL DEFAULT 1,
  PRIMARY KEY (exam_id, question_id)
);
CREATE INDEX ix_eq_exam ON exam_questions(exam_id, section_index, order_index);

-- ============================================================
-- NHOM E · Tien do va luot lam bai
-- ============================================================

CREATE TABLE user_progress (
  user_id        BIGINT      NOT NULL REFERENCES users(id),
  target_type    VARCHAR(20) NOT NULL CHECK (target_type IN
                 ('KNOWLEDGE_POINT','TOPIC','PRON_STAGE','FLASHCARD','VIDEO')),
  target_id      BIGINT      NOT NULL,

  -- FSRS
  mastery        NUMERIC(4,3) NOT NULL DEFAULT 0 CHECK (mastery BETWEEN 0 AND 1),
  stability      NUMERIC(8,3) NOT NULL DEFAULT 0.1 CHECK (stability > 0),
  difficulty     NUMERIC(4,2) NOT NULL DEFAULT 5,
  next_review_at TIMESTAMPTZ,
  review_count   INTEGER NOT NULL DEFAULT 0,
  lapse_count    INTEGER NOT NULL DEFAULT 0,

  -- Tien do %
  percent        NUMERIC(5,2) CHECK (percent BETWEEN 0 AND 100),
  state          VARCHAR(20) NOT NULL DEFAULT 'LEARNING'
                 CHECK (state IN ('LOCKED','AVAILABLE','LEARNING','COMPLETED','MASTERED')),
  unlocked_at    TIMESTAMPTZ,
  self_declared  BOOLEAN NOT NULL DEFAULT false,
  position_ms    INTEGER,                      -- chi VIDEO

  last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Khoa chinh nay chan race condition mo tang/mo chu de
  PRIMARY KEY (user_id, target_type, target_id)
);
CREATE INDEX ix_prog_due     ON user_progress(user_id, next_review_at) WHERE next_review_at IS NOT NULL;
CREATE INDEX ix_prog_mastery ON user_progress(user_id, mastery);
CREATE INDEX ix_prog_type    ON user_progress(user_id, target_type, state);

CREATE TABLE attempts (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT      NOT NULL REFERENCES users(id),
  kind           VARCHAR(20) NOT NULL CHECK (kind IN
                 ('EXAM','PRACTICE','TOPIC_TEST','QUIZ','GAME','WRITING_CHALLENGE','CONTEST')),
  ref_type       VARCHAR(20),
  ref_id         BIGINT,
  game_code      VARCHAR(30),

  status         VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
                 CHECK (status IN ('IN_PROGRESS','SUBMITTED','ABANDONED','EXPIRED')),
  served_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  submitted_at   TIMESTAMPTZ,
  expires_at     TIMESTAMPTZ,

  score          BIGINT,                       -- BIGINT, khong FLOAT (AC-07)
  max_score      BIGINT,
  duration_ms    INTEGER,
  suspicious     BOOLEAN NOT NULL DEFAULT false,

  draft_answers  JSONB,
  draft_saved_at TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_att_game      CHECK (kind <> 'GAME' OR game_code IS NOT NULL),
  CONSTRAINT ck_att_submitted CHECK (status <> 'SUBMITTED' OR submitted_at IS NOT NULL),
  CONSTRAINT ck_att_score     CHECK (score IS NULL OR score >= 0)
);
CREATE INDEX ix_att_user     ON attempts(user_id, kind, submitted_at DESC);
CREATE INDEX ix_att_progress ON attempts(user_id, status) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_att_rank     ON attempts(kind, game_code, score DESC) WHERE status = 'SUBMITTED';
-- Quiz chi tinh hang LAN DAU (UC-080)
CREATE UNIQUE INDEX uq_quiz_first ON attempts(user_id, ref_id)
  WHERE kind = 'QUIZ' AND status = 'SUBMITTED';

CREATE TABLE attempt_items (
  id          BIGSERIAL PRIMARY KEY,
  attempt_id  BIGINT NOT NULL REFERENCES attempts(id),
  question_id BIGINT REFERENCES questions(id) ON DELETE RESTRICT,
  lexeme_id   BIGINT REFERENCES lexemes(id),
  answer      TEXT,
  is_correct  BOOLEAN,                        -- NULL = ESSAY chua cham
  duration_ms INTEGER,
  order_index INTEGER NOT NULL DEFAULT 0,
  answered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_item_ref CHECK (question_id IS NOT NULL OR lexeme_id IS NOT NULL)
);
CREATE INDEX ix_ai_attempt ON attempt_items(attempt_id);
CREATE INDEX ix_ai_wrong   ON attempt_items(question_id) WHERE is_correct = false;
-- Mot cau chi tra loi mot lan trong mot luot
CREATE UNIQUE INDEX uq_ai_once ON attempt_items(attempt_id, question_id)
  WHERE question_id IS NOT NULL;

CREATE TABLE study_events (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT      NOT NULL REFERENCES users(id),
  event_type  VARCHAR(20) NOT NULL CHECK (event_type IN
              ('STUDY','REVIEW','GAME','EXAM','REMINDER_SENT')),
  ref_type    VARCHAR(20),
  ref_id      BIGINT,
  duration_ms INTEGER,
  -- Ngay theo GIO VIET NAM. Streak va bieu do tinh theo cot nay,
  -- khong group theo DATE(created_at) UTC.
  local_date  DATE NOT NULL,
  channel     VARCHAR(20),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ev_user_date ON study_events(user_id, local_date);
-- Chan gui nhac trung khi chay nhieu instance
CREATE UNIQUE INDEX uq_reminder ON study_events(user_id, local_date, channel)
  WHERE event_type = 'REMINDER_SENT';

-- ============================================================
-- NHOM F · Thu vien ca nhan
-- ============================================================

CREATE TABLE collections (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT      NOT NULL REFERENCES users(id),
  kind        VARCHAR(20) NOT NULL CHECK (kind IN ('NOTEBOOK','FLASHCARD_DECK')),
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  is_public   BOOLEAN NOT NULL DEFAULT false,
  shared_at   TIMESTAMPTZ,
  copied_from BIGINT REFERENCES collections(id),
  deleted_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_coll_share CHECK (is_public = false OR shared_at IS NOT NULL)
);
CREATE INDEX ix_coll_user   ON collections(user_id, kind) WHERE deleted_at IS NULL;
CREATE INDEX ix_coll_public ON collections(kind, shared_at DESC) WHERE is_public = true;

CREATE TABLE collection_items (
  id            BIGSERIAL PRIMARY KEY,
  collection_id BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
  lexeme_id     BIGINT REFERENCES lexemes(id),
  title         VARCHAR(200),
  content       TEXT,
  tags          VARCHAR(50)[] NOT NULL DEFAULT '{}',
  source_type   VARCHAR(20),
  source_ref    TEXT,
  order_index   INTEGER NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_item_content CHECK (lexeme_id IS NOT NULL OR content IS NOT NULL)
);
CREATE UNIQUE INDEX uq_coll_lex   ON collection_items(collection_id, lexeme_id)
  WHERE lexeme_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_item_coll   ON collection_items(collection_id, order_index) WHERE deleted_at IS NULL;
CREATE INDEX ix_item_search ON collection_items USING gin (content gin_trgm_ops);

-- ============================================================
-- NHOM G · Tien va dich vu
-- ============================================================

CREATE TABLE credit_cards (
  id         BIGSERIAL PRIMARY KEY,
  batch_id   VARCHAR(30)  NOT NULL,
  code_hash  VARCHAR(255) NOT NULL UNIQUE,   -- CHI hash
  card_type  VARCHAR(20)  NOT NULL CHECK (card_type IN ('CREDIT','SUBSCRIPTION')),
  value      INTEGER      NOT NULL CHECK (value > 0),
  plan_days  SMALLINT,
  status     VARCHAR(10)  NOT NULL DEFAULT 'UNUSED'
             CHECK (status IN ('UNUSED','USED','VOIDED')),
  used_by    BIGINT REFERENCES users(id),
  used_at    TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_card_used CHECK (
    status <> 'USED' OR (used_by IS NOT NULL AND used_at IS NOT NULL)
  ),
  CONSTRAINT ck_card_sub  CHECK (card_type <> 'SUBSCRIPTION' OR plan_days IS NOT NULL)
);
CREATE INDEX ix_card_batch ON credit_cards(batch_id, status);

CREATE TABLE credit_transactions (
  id                 BIGSERIAL PRIMARY KEY,
  user_id            BIGINT      NOT NULL REFERENCES users(id),
  tx_type            VARCHAR(20) NOT NULL CHECK (tx_type IN
                     ('TOPUP','DEDUCT','REFUND','ADJUSTMENT','TEACHER_PAYOUT','SUBSCRIPTION')),
  amount             INTEGER NOT NULL,
  balance_before     INTEGER NOT NULL CHECK (balance_before >= 0),
  balance_after      INTEGER NOT NULL CHECK (balance_after >= 0),
  feature_code       VARCHAR(30),
  card_id            BIGINT REFERENCES credit_cards(id),
  request_id         BIGINT,                 -- FK them sau khi tao service_requests
  ref_transaction_id BIGINT REFERENCES credit_transactions(id),
  reason             TEXT,
  performed_by       BIGINT REFERENCES users(id),
  dispute_ref        VARCHAR(50),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Rang buoc quan trong nhat cua ca schema
  CONSTRAINT ck_tx_balance CHECK (balance_after = balance_before + amount),
  CONSTRAINT ck_tx_adjust  CHECK (
    tx_type <> 'ADJUSTMENT' OR (reason IS NOT NULL AND performed_by IS NOT NULL)
  ),
  -- Chan tu dieu chinh cho chinh minh (UC-102 SELF_ADJUSTMENT)
  CONSTRAINT ck_tx_self    CHECK (performed_by IS NULL OR performed_by <> user_id)
);
CREATE INDEX ix_tx_user ON credit_transactions(user_id, id DESC);
CREATE INDEX ix_tx_date ON credit_transactions(created_at);
CREATE UNIQUE INDEX uq_refund  ON credit_transactions(ref_transaction_id) WHERE tx_type = 'REFUND';
CREATE UNIQUE INDEX uq_dispute ON credit_transactions(dispute_ref) WHERE dispute_ref IS NOT NULL;

CREATE TABLE service_requests (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT      NOT NULL REFERENCES users(id),
  kind          VARCHAR(20) NOT NULL CHECK (kind IN
                ('AI_GENERATE','GRADING','IMPORT','TRANSLATE')),
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ASSIGNED','RUNNING','COMPLETED','FAILED','EXPIRED')),
  payload       JSONB NOT NULL DEFAULT '{}',
  result        JSONB,

  assignee_id   BIGINT REFERENCES users(id),
  assigned_at   TIMESTAMPTZ,
  deadline_at   TIMESTAMPTZ,
  score         SMALLINT CHECK (score BETWEEN 0 AND 100),
  feedback      TEXT,
  payout        INTEGER CHECK (payout >= 0),

  file_hash     VARCHAR(64),
  error_rows    JSONB,

  retry_count   SMALLINT NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at  TIMESTAMPTZ,

  CONSTRAINT ck_req_deadline CHECK (kind <> 'GRADING' OR deadline_at IS NOT NULL),
  CONSTRAINT ck_req_feedback CHECK (score IS NULL OR feedback IS NOT NULL),
  -- Khong tu cham bai cua chinh minh (UC-105 SELF_GRADING)
  CONSTRAINT ck_req_self     CHECK (assignee_id IS NULL OR assignee_id <> user_id)
);
CREATE INDEX ix_req_queue ON service_requests(kind, status, deadline_at)
  WHERE status IN ('PENDING','ASSIGNED');
CREATE INDEX ix_req_user  ON service_requests(user_id, kind, created_at DESC);
CREATE UNIQUE INDEX uq_ai_running ON service_requests(user_id)
  WHERE kind = 'AI_GENERATE' AND status IN ('PENDING','RUNNING');
CREATE UNIQUE INDEX uq_import_file ON service_requests(file_hash)
  WHERE kind = 'IMPORT' AND status = 'COMPLETED';

-- Them FK sau khi ca hai bang da ton tai
ALTER TABLE credit_transactions
  ADD CONSTRAINT fk_tx_request FOREIGN KEY (request_id) REFERENCES service_requests(id);
ALTER TABLE questions
  ADD CONSTRAINT fk_q_request  FOREIGN KEY (request_id) REFERENCES service_requests(id);

-- ============================================================
-- NHOM H · Van hanh
-- ============================================================

CREATE TABLE audit_logs (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    BIGINT REFERENCES users(id),
  action      VARCHAR(50) NOT NULL,
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      JSONB NOT NULL DEFAULT '{}',
  ip_address  INET,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_actor  ON audit_logs(actor_id, created_at DESC);
CREATE INDEX ix_audit_action ON audit_logs(action, created_at DESC);
CREATE INDEX ix_audit_target ON audit_logs(target_type, target_id);

CREATE TABLE ai_chats (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT NOT NULL REFERENCES users(id),
  session_key  UUID   NOT NULL DEFAULT gen_random_uuid(),
  messages     JSONB  NOT NULL DEFAULT '[]',
  page_context VARCHAR(100),                  -- KHONG chua PII
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '90 days')
);
CREATE INDEX ix_chat_user ON ai_chats(user_id, updated_at DESC);
CREATE UNIQUE INDEX uq_chat_session ON ai_chats(session_key);

-- ============================================================
-- Het V1 — 19 bang MVP
-- ============================================================

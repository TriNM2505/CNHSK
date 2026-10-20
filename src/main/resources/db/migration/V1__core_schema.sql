-- ============================================================
-- CNHSK · Migration V1: nen tang MVP — 4 schema, 26 bang
-- Nguon thiet ke: docs/reference/database.md (AC-03, AC-04 ban 1.3.0)
-- PostgreSQL 15+
--
--   auth      3 bang : users, user_roles, auth_tokens
--   learning 22 bang : noi dung, de thi, tien do, thu vien, tien va dich vu, tro ly AI
--   shared    1 bang : audit_logs
--   community        : tao o V2
--
-- Thu tu tao bang theo phu thuoc khoa ngoai. FK cat qua schema duoc phep (AC-03):
-- chi la rang buoc toan ven; module van chi doc/ghi bang cua minh.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS learning;
CREATE SCHEMA IF NOT EXISTS community;
CREATE SCHEMA IF NOT EXISTS shared;

-- ============================================================
-- learning.plans — tao truoc vi auth.users.plan_code tro toi
-- ============================================================

CREATE TABLE learning.plans (
  code          VARCHAR(20)   PRIMARY KEY
                CHECK (code IN ('FREE','PREMIUM','PREMIUM_PLUS')),
  name          VARCHAR(100)  NOT NULL,
  description   TEXT,
  -- Han cua GOI. NULL = vinh vien (goi FREE).
  duration_days SMALLINT      CHECK (duration_days IS NULL OR duration_days > 0),
  -- Han muc moi CHU KY THANG theo feature_code. Spec goi dich vu (credits-plans) dien.
  benefits      JSONB         NOT NULL DEFAULT '{}',
  price         NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  is_active     BOOLEAN       NOT NULL DEFAULT true,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

  CONSTRAINT ck_plan_free CHECK (code <> 'FREE' OR (price = 0 AND duration_days IS NULL))
);

-- Chi seed FREE: dang ky (spec 001 FR-001, FR-068) gan plan_code = 'FREE'.
-- PREMIUM, PREMIUM_PLUS va han muc thuoc spec goi dich vu.
INSERT INTO learning.plans (code, name, description)
VALUES ('FREE', 'Mien phi', 'Goi mac dinh cua moi tai khoan moi');

-- ============================================================
-- auth · Tai khoan va phan quyen
-- ============================================================

CREATE TABLE auth.users (
  id                BIGSERIAL    PRIMARY KEY,
  email             VARCHAR(255) NOT NULL UNIQUE,    -- luon luu dang da chuan hoa
  password_hash     VARCHAR(255) NOT NULL,
  display_name      VARCHAR(100) NOT NULL,
  avatar_url        TEXT,
  hsk_level         SMALLINT     CHECK (hsk_level BETWEEN 1 AND 9),

  -- Trang thai tai khoan. Dem sai mat khau va chan tam thoi nam o Redis, khong o day.
  email_verified_at TIMESTAMPTZ,
  suspended_at      TIMESTAMPTZ,                     -- MODERATOR treo (V2), spec 001 chi doc
  suspended_until   TIMESTAMPTZ,
  banned_at         TIMESTAMPTZ,                     -- ADMIN cam
  banned_by         BIGINT       REFERENCES auth.users(id),
  ban_reason        TEXT,

  -- Nhac hoc: gio Viet Nam, NULL = tat
  reminder_time     TIME,
  reminder_channels VARCHAR(50)  NOT NULL DEFAULT 'WEB',

  -- Goi dich vu — doc cung moi lan kiem quyen nen nam tren users
  plan_code         VARCHAR(20)  NOT NULL DEFAULT 'FREE' REFERENCES learning.plans(code),
  plan_expires_at   TIMESTAMPTZ,
  free_usage        JSONB        NOT NULL DEFAULT '{}',
  usage_reset_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

  CONSTRAINT ck_users_ban_reason  CHECK (banned_at IS NULL OR ban_reason IS NOT NULL),
  CONSTRAINT ck_users_plan_expiry CHECK (
    (plan_code = 'FREE' AND plan_expires_at IS NULL)
    OR (plan_code <> 'FREE' AND plan_expires_at IS NOT NULL))
);
CREATE INDEX ix_users_active   ON auth.users(id) WHERE banned_at IS NULL;
CREATE INDEX ix_users_plan_exp ON auth.users(plan_expires_at) WHERE plan_expires_at IS NOT NULL;

-- 6 role luu bang CHECK (AC-06), khong co bang roles. GUEST khong phai role.
CREATE TABLE auth.user_roles (
  user_id    BIGINT      NOT NULL REFERENCES auth.users(id),
  role_code  VARCHAR(20) NOT NULL,
  granted_by BIGINT      REFERENCES auth.users(id),
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_code),
  -- Chan tu cap role cho chinh minh (SELF_ROLE_GRANT)
  CONSTRAINT ck_ur_self      CHECK (granted_by IS NULL OR granted_by <> user_id),
  CONSTRAINT ck_ur_role_code CHECK (role_code IN
    ('LEARNER','TEACHER','MODERATOR','CONTENT_EDITOR','ACCOUNTANT','ADMIN'))
);
CREATE INDEX ix_ur_role ON auth.user_roles(role_code, user_id);

CREATE TABLE auth.auth_tokens (
  id         BIGSERIAL    PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES auth.users(id),
  token_type VARCHAR(20)  NOT NULL
             CHECK (token_type IN ('REFRESH','RESET_PASSWORD','EMAIL_VERIFY')),
  token_hash VARCHAR(255) NOT NULL UNIQUE,           -- SHA-256 hex, khong luu token tho
  expires_at TIMESTAMPTZ  NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_tokens_user ON auth.auth_tokens(user_id) WHERE revoked_at IS NULL;

-- ============================================================
-- learning · Noi dung hoc
-- ============================================================

CREATE TABLE learning.lexemes (
  id           BIGSERIAL    PRIMARY KEY,
  kind         VARCHAR(10)  NOT NULL CHECK (kind IN ('CHAR','WORD','GRAMMAR')),
  text         VARCHAR(200) NOT NULL,
  pinyin       VARCHAR(200),
  pinyin_norm  VARCHAR(200),                         -- khong dau, chu thuong — tra pinyin
  sino_viet    VARCHAR(200),
  meaning_vi   TEXT         NOT NULL,
  meaning_norm TEXT,                                 -- khong dau — tra nghia Viet
  hsk_level    SMALLINT     CHECK (hsk_level BETWEEN 1 AND 9),
  audio_url    TEXT,
  radical      VARCHAR(10),                          -- chi CHAR
  stroke_count SMALLINT,                             -- chi CHAR
  extra        JSONB        NOT NULL DEFAULT '{}',   -- CHI DOC (AC-09)
  frequency    INTEGER      NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_lexeme UNIQUE (kind, text)
);
CREATE INDEX ix_lex_pinyin   ON learning.lexemes(pinyin_norm);
CREATE INDEX ix_lex_kind_hsk ON learning.lexemes(kind, hsk_level);
CREATE INDEX ix_lex_meaning  ON learning.lexemes USING gin (meaning_norm gin_trgm_ops);

CREATE TABLE learning.lexeme_links (
  parent_id BIGINT      NOT NULL REFERENCES learning.lexemes(id),
  child_id  BIGINT      NOT NULL REFERENCES learning.lexemes(id),
  link_type VARCHAR(20) NOT NULL CHECK (link_type IN ('CONTAINS','RELATED','SYNONYM')),
  PRIMARY KEY (parent_id, child_id, link_type),
  CONSTRAINT ck_link_self CHECK (parent_id <> child_id)
);
CREATE INDEX ix_link_child ON learning.lexeme_links(child_id);

-- Dong bang: tao dung thiet ke nhung chua code nao dung (cho cay tri thuc).
CREATE TABLE learning.knowledge_points (
  id         BIGSERIAL    PRIMARY KEY,
  code       VARCHAR(50)  NOT NULL UNIQUE,
  title      VARCHAR(200) NOT NULL,
  skill_type VARCHAR(20)  NOT NULL
             CHECK (skill_type IN ('LISTENING','READING','WRITING','VOCAB','GRAMMAR','CHARACTER')),
  hsk_level  SMALLINT     NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  lexeme_id  BIGINT       REFERENCES learning.lexemes(id),
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_kp_skill ON learning.knowledge_points(skill_type, hsk_level);

CREATE TABLE learning.topics (
  id          BIGSERIAL    PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  hsk_level   SMALLINT     NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  order_index INTEGER      NOT NULL DEFAULT 0,
  -- Tien quyet dang mang. Kiem CHU TRINH bang DFS o application truoc khi luu.
  prereq_ids  BIGINT[]     NOT NULL DEFAULT '{}',
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_topic_level ON learning.topics(hsk_level, order_index);

-- Khoa chinh id rieng: PK gom cot cho phep NULL se bi PostgreSQL ep NOT NULL,
-- khi do khong chen duoc dong chi co lexeme_id. Khong trung dung bang hai unique index.
CREATE TABLE learning.topic_items (
  id                 BIGSERIAL PRIMARY KEY,
  topic_id           BIGINT    NOT NULL REFERENCES learning.topics(id),
  lexeme_id          BIGINT    REFERENCES learning.lexemes(id),
  knowledge_point_id BIGINT    REFERENCES learning.knowledge_points(id),
  order_index        INTEGER   NOT NULL DEFAULT 0,
  CONSTRAINT ck_topic_item CHECK (lexeme_id IS NOT NULL OR knowledge_point_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_ti_lexeme ON learning.topic_items(topic_id, lexeme_id)
  WHERE lexeme_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ti_kp     ON learning.topic_items(topic_id, knowledge_point_id)
  WHERE knowledge_point_id IS NOT NULL;
CREATE INDEX ix_ti_topic ON learning.topic_items(topic_id, order_index);

-- 8 tang luyen phat am (feature 1.3). user_progress(PRON_STAGE, id) tro toi day.
CREATE TABLE learning.pron_stages (
  id            BIGSERIAL    PRIMARY KEY,
  stage_index   SMALLINT     NOT NULL UNIQUE CHECK (stage_index BETWEEN 1 AND 8),
  name          VARCHAR(100) NOT NULL,
  description   TEXT,
  target_sounds JSONB        NOT NULL DEFAULT '[]',  -- CHI DOC sau khi nhap (AC-09)
  pass_percent  NUMERIC(5,2) NOT NULL DEFAULT 80.00 CHECK (pass_percent BETWEEN 0 AND 100),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Video hoc + phu de (feature 1.6). user_progress(VIDEO, id) luu vi tri xem.
CREATE TABLE learning.videos (
  id          BIGSERIAL    PRIMARY KEY,
  title       VARCHAR(300) NOT NULL,
  url         TEXT         NOT NULL,
  source_type VARCHAR(20)  NOT NULL DEFAULT 'YOUTUBE'
              CHECK (source_type IN ('YOUTUBE','SELF_HOSTED')),
  external_id VARCHAR(50),                           -- ID YouTube, khong parse tu url
  duration_ms INTEGER      NOT NULL CHECK (duration_ms > 0),
  hsk_level   SMALLINT     CHECK (hsk_level BETWEEN 1 AND 9),
  topic_id    BIGINT       REFERENCES learning.topics(id),
  subtitles   JSONB        NOT NULL DEFAULT '[]',    -- CHI DOC (AC-09)
  status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
              CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED','UNAVAILABLE')),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_video_level ON learning.videos(hsk_level, status) WHERE status = 'PUBLISHED';
CREATE INDEX ix_video_topic ON learning.videos(topic_id)          WHERE status = 'PUBLISHED';

-- ============================================================
-- learning · Cau hoi va de thi
-- ============================================================

CREATE TABLE learning.questions (
  id                    BIGSERIAL   PRIMARY KEY,
  qtype                 VARCHAR(20) NOT NULL CHECK (qtype IN
                        ('MULTIPLE_CHOICE','TRUE_FALSE','IMAGE_MATCH','SENTENCE_MATCH',
                         'FILL_BLANK','SENTENCE_ORDER','ESSAY')),
  hsk_level             SMALLINT    NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
  stem                  TEXT        NOT NULL,
  options               JSONB       NOT NULL DEFAULT '[]',   -- [{key,text,is_correct}] CHI DOC
  correct_key           VARCHAR(50),
  explanation           TEXT,
  audio_url             TEXT,
  image_url             TEXT,

  -- Kiem duyet: cau AI luon vao PENDING_REVIEW
  source                VARCHAR(10) NOT NULL DEFAULT 'IMPORT'
                        CHECK (source IN ('IMPORT','AI','MANUAL')),
  status                VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
                        CHECK (status IN ('PENDING_REVIEW','APPROVED','REJECTED','ARCHIVED')),
  reviewed_by           BIGINT      REFERENCES auth.users(id),
  reviewed_at           TIMESTAMPTZ,
  review_reason         TEXT,
  reusable              BOOLEAN     NOT NULL DEFAULT true,
  created_by_request_id BIGINT,                     -- FK them sau khi co service_requests
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Cau AI khong the APPROVED neu chua co nguoi duyet
  CONSTRAINT ck_q_ai_review CHECK (source <> 'AI' OR status <> 'APPROVED' OR reviewed_by IS NOT NULL),
  CONSTRAINT ck_q_reject    CHECK (status <> 'REJECTED' OR review_reason IS NOT NULL),
  -- Cau trac nghiem phai co it nhat 2 lua chon
  CONSTRAINT ck_q_options   CHECK (
    qtype NOT IN ('MULTIPLE_CHOICE','TRUE_FALSE','IMAGE_MATCH','SENTENCE_MATCH')
    OR jsonb_array_length(options) >= 2)
);
CREATE INDEX ix_q_serve  ON learning.questions(status, qtype, hsk_level) WHERE status = 'APPROVED';
CREATE INDEX ix_q_review ON learning.questions(status, created_at)        WHERE status = 'PENDING_REVIEW';

-- Dong bang cung knowledge_points.
CREATE TABLE learning.question_knowledge_points (
  question_id        BIGINT NOT NULL REFERENCES learning.questions(id) ON DELETE RESTRICT,
  knowledge_point_id BIGINT NOT NULL REFERENCES learning.knowledge_points(id),
  PRIMARY KEY (question_id, knowledge_point_id)
);
CREATE INDEX ix_qkp_kp ON learning.question_knowledge_points(knowledge_point_id);

CREATE TABLE learning.exams (
  id           BIGSERIAL    PRIMARY KEY,
  code         VARCHAR(50)  NOT NULL UNIQUE,
  title        VARCHAR(200) NOT NULL,
  hsk_level    SMALLINT     NOT NULL CHECK (hsk_level BETWEEN 1 AND 6),
  sections     JSONB        NOT NULL DEFAULT '[]',   -- [{index,name,type,duration_min}] CHI DOC
  total_score  INTEGER      NOT NULL DEFAULT 300,
  status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
               CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  published_at TIMESTAMPTZ,
  created_by   BIGINT       REFERENCES auth.users(id),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_exam_list ON learning.exams(status, hsk_level) WHERE status = 'PUBLISHED';

CREATE TABLE learning.exam_questions (
  exam_id       BIGINT   NOT NULL REFERENCES learning.exams(id),
  question_id   BIGINT   NOT NULL REFERENCES learning.questions(id) ON DELETE RESTRICT,
  section_index SMALLINT NOT NULL DEFAULT 0,
  order_index   INTEGER  NOT NULL DEFAULT 0,
  points        SMALLINT NOT NULL DEFAULT 1,
  PRIMARY KEY (exam_id, question_id)
);
CREATE INDEX ix_eq_exam ON learning.exam_questions(exam_id, section_index, order_index);

-- ============================================================
-- learning · Tien do va luot lam bai
-- ============================================================

CREATE TABLE learning.user_progress (
  user_id        BIGINT       NOT NULL REFERENCES auth.users(id),
  target_type    VARCHAR(20)  NOT NULL CHECK (target_type IN
                 ('KNOWLEDGE_POINT','TOPIC','PRON_STAGE','FLASHCARD','VIDEO')),
  target_id      BIGINT       NOT NULL,

  -- FSRS — KNOWLEDGE_POINT va FLASHCARD
  mastery        NUMERIC(4,3) NOT NULL DEFAULT 0 CHECK (mastery BETWEEN 0 AND 1),
  stability      NUMERIC(8,3) NOT NULL DEFAULT 0.1 CHECK (stability > 0),
  difficulty     NUMERIC(4,2) NOT NULL DEFAULT 5,
  next_review_at TIMESTAMPTZ,
  review_count   INTEGER      NOT NULL DEFAULT 0,
  lapse_count    INTEGER      NOT NULL DEFAULT 0,

  -- Tien do % — TOPIC, PRON_STAGE, VIDEO
  percent        NUMERIC(5,2) CHECK (percent BETWEEN 0 AND 100),
  state          VARCHAR(20)  NOT NULL DEFAULT 'LEARNING'
                 CHECK (state IN ('LOCKED','AVAILABLE','LEARNING','COMPLETED','MASTERED')),
  unlocked_at    TIMESTAMPTZ,
  self_declared  BOOLEAN      NOT NULL DEFAULT false,
  position_ms    INTEGER,                            -- chi VIDEO

  last_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

  -- Khoa chinh chan race condition mo tang / mo chu de
  PRIMARY KEY (user_id, target_type, target_id)
);
CREATE INDEX ix_prog_due     ON learning.user_progress(user_id, next_review_at) WHERE next_review_at IS NOT NULL;
CREATE INDEX ix_prog_mastery ON learning.user_progress(user_id, mastery);
CREATE INDEX ix_prog_type    ON learning.user_progress(user_id, target_type, state);

CREATE TABLE learning.attempts (
  id             BIGSERIAL   PRIMARY KEY,
  user_id        BIGINT      NOT NULL REFERENCES auth.users(id),
  kind           VARCHAR(20) NOT NULL CHECK (kind IN
                 ('EXAM','PRACTICE','TOPIC_TEST','QUIZ','GAME','WRITING_CHALLENGE','CONTEST')),
  ref_type       VARCHAR(20),
  ref_id         BIGINT,
  game_code      VARCHAR(30),                        -- chi kind = GAME

  status         VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
                 CHECK (status IN ('IN_PROGRESS','SUBMITTED','ABANDONED','EXPIRED')),
  served_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  submitted_at   TIMESTAMPTZ,
  expires_at     TIMESTAMPTZ,

  score          BIGINT,                             -- BIGINT, khong FLOAT (AC-07)
  max_score      BIGINT,
  duration_ms    INTEGER,
  suspicious     BOOLEAN     NOT NULL DEFAULT false,

  -- Snapshot noi dung da phat, ghi mot lan khi bat dau; server doi chieu khi nop
  served_items   JSONB,
  draft_answers  JSONB,
  draft_saved_at TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_att_game      CHECK (kind <> 'GAME' OR game_code IS NOT NULL),
  CONSTRAINT ck_att_submitted CHECK (status <> 'SUBMITTED' OR submitted_at IS NOT NULL),
  CONSTRAINT ck_att_score     CHECK (score IS NULL OR score >= 0)
);
CREATE INDEX ix_att_user     ON learning.attempts(user_id, kind, submitted_at DESC);
CREATE INDEX ix_att_progress ON learning.attempts(user_id, status) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_att_rank     ON learning.attempts(kind, game_code, score DESC) WHERE status = 'SUBMITTED';
-- Quiz chi tinh hang LAN DAU
CREATE UNIQUE INDEX uq_quiz_first ON learning.attempts(user_id, ref_id)
  WHERE kind = 'QUIZ' AND status = 'SUBMITTED';

-- Cau co the khong tro toi question/lexeme (vd don vi phat am cua spec 005),
-- nen khong rang buoc phai co mot trong hai.
CREATE TABLE learning.attempt_items (
  id          BIGSERIAL   PRIMARY KEY,
  attempt_id  BIGINT      NOT NULL REFERENCES learning.attempts(id),
  question_id BIGINT      REFERENCES learning.questions(id) ON DELETE RESTRICT,
  lexeme_id   BIGINT      REFERENCES learning.lexemes(id),
  answer      TEXT,
  is_correct  BOOLEAN,                               -- NULL = chua cham / khong tinh diem
  duration_ms INTEGER,
  order_index INTEGER     NOT NULL DEFAULT 0,
  answered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_attempt ON learning.attempt_items(attempt_id);
CREATE INDEX ix_ai_wrong   ON learning.attempt_items(question_id) WHERE is_correct = false;

CREATE TABLE learning.study_events (
  id          BIGSERIAL   PRIMARY KEY,
  user_id     BIGINT      NOT NULL REFERENCES auth.users(id),
  event_type  VARCHAR(20) NOT NULL CHECK (event_type IN
              ('STUDY','REVIEW','GAME','EXAM','REMINDER_SENT')),
  ref_type    VARCHAR(20),
  ref_id      BIGINT,
  duration_ms INTEGER,
  -- Ngay theo GIO VIET NAM. Streak va bieu do tinh theo cot nay, khong theo DATE(UTC).
  local_date  DATE        NOT NULL,
  channel     VARCHAR(20),                           -- chi REMINDER_SENT
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ev_user_date ON learning.study_events(user_id, local_date);
-- Chan gui nhac trung khi chay nhieu instance
CREATE UNIQUE INDEX uq_reminder ON learning.study_events(user_id, local_date, channel)
  WHERE event_type = 'REMINDER_SENT';

-- ============================================================
-- learning · Thu vien ca nhan
-- ============================================================

CREATE TABLE learning.collections (
  id          BIGSERIAL    PRIMARY KEY,
  user_id     BIGINT       NOT NULL REFERENCES auth.users(id),
  kind        VARCHAR(20)  NOT NULL CHECK (kind IN ('NOTEBOOK','FLASHCARD_DECK')),
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  is_public   BOOLEAN      NOT NULL DEFAULT false,
  shared_at   TIMESTAMPTZ,
  copied_from BIGINT       REFERENCES learning.collections(id),
  deleted_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_coll_share CHECK (is_public = false OR shared_at IS NOT NULL)
);
CREATE INDEX ix_coll_user   ON learning.collections(user_id, kind) WHERE deleted_at IS NULL;
CREATE INDEX ix_coll_public ON learning.collections(kind, shared_at DESC) WHERE is_public = true;

CREATE TABLE learning.collection_items (
  id            BIGSERIAL    PRIMARY KEY,
  collection_id BIGINT       NOT NULL REFERENCES learning.collections(id) ON DELETE CASCADE,
  lexeme_id     BIGINT       REFERENCES learning.lexemes(id),
  title         VARCHAR(200),
  content       TEXT,
  tags          VARCHAR(50)[] NOT NULL DEFAULT '{}',
  source_type   VARCHAR(20),
  source_ref    TEXT,
  order_index   INTEGER      NOT NULL DEFAULT 0,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_item_content CHECK (lexeme_id IS NOT NULL OR content IS NOT NULL)
);
CREATE UNIQUE INDEX uq_coll_lex ON learning.collection_items(collection_id, lexeme_id)
  WHERE lexeme_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_item_coll   ON learning.collection_items(collection_id, order_index) WHERE deleted_at IS NULL;
CREATE INDEX ix_item_search ON learning.collection_items USING gin (content gin_trgm_ops);

-- ============================================================
-- learning · Tien va dich vu
-- ============================================================

CREATE TABLE learning.credit_cards (
  id           BIGSERIAL    PRIMARY KEY,
  batch_id     VARCHAR(30)  NOT NULL,
  code_hash    VARCHAR(255) NOT NULL UNIQUE,         -- CHI hash, khong luu ma tho
  card_type    VARCHAR(20)  NOT NULL CHECK (card_type IN ('CREDIT','SUBSCRIPTION')),
  value        INTEGER      NOT NULL CHECK (value > 0),
  plan_days    SMALLINT,
  status       VARCHAR(10)  NOT NULL DEFAULT 'UNUSED'
               CHECK (status IN ('UNUSED','USED','VOIDED')),
  used_by      BIGINT       REFERENCES auth.users(id),
  used_at      TIMESTAMPTZ,
  expires_at   TIMESTAMPTZ,
  -- Do pheu (SM-05): ma nay phat qua kenh nao, dot nao, cho ai
  channel      VARCHAR(20)  NOT NULL DEFAULT 'DIRECT'
               CHECK (channel IN ('ECOSYSTEM_GIFT','PARTNER_BATCH','DIRECT')),
  partner_code VARCHAR(50),
  campaign     VARCHAR(50),
  issued_to    VARCHAR(255),
  created_by   BIGINT       NOT NULL REFERENCES auth.users(id),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_card_used    CHECK (status <> 'USED' OR (used_by IS NOT NULL AND used_at IS NOT NULL)),
  CONSTRAINT ck_card_sub     CHECK (card_type <> 'SUBSCRIPTION' OR plan_days IS NOT NULL),
  CONSTRAINT ck_card_partner CHECK (channel <> 'PARTNER_BATCH' OR partner_code IS NOT NULL),
  CONSTRAINT ck_card_gift    CHECK (channel <> 'ECOSYSTEM_GIFT' OR issued_to IS NOT NULL)
);
CREATE INDEX ix_card_batch   ON learning.credit_cards(batch_id, status);
CREATE INDEX ix_card_funnel  ON learning.credit_cards(channel, campaign, status);
CREATE INDEX ix_card_partner ON learning.credit_cards(partner_code, status) WHERE partner_code IS NOT NULL;

-- So cai append-only. So du = balance_after cua dong co seq lon nhat.
CREATE TABLE learning.credit_transactions (
  id                 BIGSERIAL   PRIMARY KEY,
  user_id            BIGINT      NOT NULL REFERENCES auth.users(id),
  -- Thu tu giao dich cua tung user. UNIQUE(user_id, seq) chan hai giao dich doc cung so du.
  seq                INTEGER     NOT NULL CHECK (seq > 0),
  tx_type            VARCHAR(20) NOT NULL CHECK (tx_type IN
                     ('TOPUP','DEDUCT','REFUND','ADJUSTMENT','TEACHER_PAYOUT','SUBSCRIPTION')),
  amount             INTEGER     NOT NULL,
  balance_before     INTEGER     NOT NULL CHECK (balance_before >= 0),
  balance_after      INTEGER     NOT NULL CHECK (balance_after >= 0),
  feature_code       VARCHAR(30),
  card_id            BIGINT      REFERENCES learning.credit_cards(id),
  request_id         BIGINT,                         -- FK them sau khi co service_requests
  ref_transaction_id BIGINT      REFERENCES learning.credit_transactions(id),
  reason             TEXT,
  performed_by       BIGINT      REFERENCES auth.users(id),
  dispute_ref        VARCHAR(50),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Rang buoc quan trong nhat cua ca schema
  CONSTRAINT ck_tx_balance CHECK (balance_after = balance_before + amount),
  CONSTRAINT ck_tx_adjust  CHECK (tx_type <> 'ADJUSTMENT' OR (reason IS NOT NULL AND performed_by IS NOT NULL)),
  -- Chan tu dieu chinh cho chinh minh (SELF_ADJUSTMENT)
  CONSTRAINT ck_tx_self    CHECK (performed_by IS NULL OR performed_by <> user_id)
);
CREATE UNIQUE INDEX ux_tx_user_seq ON learning.credit_transactions(user_id, seq);
CREATE INDEX ix_tx_user ON learning.credit_transactions(user_id, id DESC);
CREATE INDEX ix_tx_date ON learning.credit_transactions(created_at);
CREATE UNIQUE INDEX uq_refund  ON learning.credit_transactions(ref_transaction_id) WHERE tx_type = 'REFUND';
CREATE UNIQUE INDEX uq_dispute ON learning.credit_transactions(dispute_ref) WHERE dispute_ref IS NOT NULL;

-- Gop AI job, cham bai thue, nhap du lieu, dich.
CREATE TABLE learning.service_requests (
  id            BIGSERIAL   PRIMARY KEY,
  user_id       BIGINT      NOT NULL REFERENCES auth.users(id),
  kind          VARCHAR(20) NOT NULL CHECK (kind IN ('AI_GENERATE','GRADING','IMPORT','TRANSLATE')),
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ASSIGNED','RUNNING','COMPLETED','FAILED','EXPIRED')),
  payload       JSONB       NOT NULL DEFAULT '{}',
  result        JSONB,

  assignee_id   BIGINT      REFERENCES auth.users(id),
  assigned_at   TIMESTAMPTZ,
  deadline_at   TIMESTAMPTZ,
  score         SMALLINT    CHECK (score BETWEEN 0 AND 100),
  feedback      TEXT,
  payout        INTEGER     CHECK (payout >= 0),

  file_hash     VARCHAR(64),
  error_rows    JSONB,

  retry_count   SMALLINT    NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at  TIMESTAMPTZ,

  CONSTRAINT ck_req_deadline CHECK (kind <> 'GRADING' OR deadline_at IS NOT NULL),
  CONSTRAINT ck_req_feedback CHECK (score IS NULL OR feedback IS NOT NULL),
  -- Khong tu cham bai cua chinh minh (SELF_GRADING)
  CONSTRAINT ck_req_self     CHECK (assignee_id IS NULL OR assignee_id <> user_id)
);
CREATE INDEX ix_req_queue ON learning.service_requests(kind, status, deadline_at)
  WHERE status IN ('PENDING','ASSIGNED');
CREATE INDEX ix_req_user  ON learning.service_requests(user_id, kind, created_at DESC);
CREATE UNIQUE INDEX uq_ai_running  ON learning.service_requests(user_id)
  WHERE kind = 'AI_GENERATE' AND status IN ('PENDING','RUNNING');
CREATE UNIQUE INDEX uq_import_file ON learning.service_requests(file_hash)
  WHERE kind = 'IMPORT' AND status = 'COMPLETED';

-- FK them sau khi ca hai bang da ton tai
ALTER TABLE learning.credit_transactions
  ADD CONSTRAINT fk_tx_request FOREIGN KEY (request_id) REFERENCES learning.service_requests(id);
ALTER TABLE learning.questions
  ADD CONSTRAINT fk_q_request  FOREIGN KEY (created_by_request_id) REFERENCES learning.service_requests(id);

-- ============================================================
-- learning · Tro ly AI
-- ============================================================

CREATE TABLE learning.ai_chats (
  id           BIGSERIAL    PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES auth.users(id),
  session_key  UUID         NOT NULL DEFAULT gen_random_uuid(),
  messages     JSONB        NOT NULL DEFAULT '[]',
  page_context VARCHAR(100),                         -- KHONG chua PII
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ  NOT NULL DEFAULT (now() + INTERVAL '90 days')
);
CREATE INDEX ix_chat_user ON learning.ai_chats(user_id, updated_at DESC);
CREATE UNIQUE INDEX uq_chat_session ON learning.ai_chats(session_key);

-- ============================================================
-- shared · Nhat ky thao tac nhay cam cua moi module
-- Moi module ghi qua shared AuditService, khong ghi truc tiep.
-- ============================================================

CREATE TABLE shared.audit_logs (
  id          BIGSERIAL   PRIMARY KEY,
  actor_id    BIGINT      REFERENCES auth.users(id),
  action      VARCHAR(50) NOT NULL,
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      JSONB       NOT NULL DEFAULT '{}',
  ip_address  INET,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_actor  ON shared.audit_logs(actor_id, created_at DESC);
CREATE INDEX ix_audit_action ON shared.audit_logs(action, created_at DESC);
CREATE INDEX ix_audit_target ON shared.audit_logs(target_type, target_id);

-- ============================================================
-- Het V1 — auth 3 · learning 22 · shared 1 = 26 bang MVP
-- ============================================================

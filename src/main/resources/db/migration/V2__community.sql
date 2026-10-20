-- ============================================================
-- CNHSK · Migration V2: cong dong — schema community, 5 bang (scope V2)
-- Nguon thiet ke: docs/reference/database.md §4.16
-- Schema community da tao o V1.
-- ============================================================

-- V2.1 — Blog co kiem duyet
CREATE TABLE community.posts (
  id              BIGSERIAL    PRIMARY KEY,
  author_id       BIGINT       NOT NULL REFERENCES auth.users(id),
  category        VARCHAR(30)  NOT NULL CHECK (category IN
                  ('EXPERIENCE','QUESTION','STUDY_BUDDY','CULTURE','JOB')),
  title           VARCHAR(300) NOT NULL,
  content         TEXT         NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','HIDDEN')),
  reviewed_by     BIGINT       REFERENCES auth.users(id),
  reviewed_at     TIMESTAMPTZ,
  review_reason   TEXT,
  comments_locked BOOLEAN      NOT NULL DEFAULT false,
  published_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

  CONSTRAINT ck_post_reject CHECK (status <> 'REJECTED' OR review_reason IS NOT NULL),
  -- MODERATOR khong tu duyet bai cua chinh minh (SELF_APPROVAL)
  CONSTRAINT ck_post_self   CHECK (reviewed_by IS NULL OR reviewed_by <> author_id),
  CONSTRAINT ck_post_pub    CHECK (status <> 'APPROVED' OR published_at IS NOT NULL)
);
CREATE INDEX ix_post_feed   ON community.posts(status, published_at DESC) WHERE status = 'APPROVED';
CREATE INDEX ix_post_author ON community.posts(author_id, created_at DESC);
CREATE INDEX ix_post_queue  ON community.posts(status, created_at) WHERE status = 'PENDING';

-- V2.2 — Binh luan long nhau. SOFT DELETE de giu cay tra loi.
CREATE TABLE community.comments (
  id                BIGSERIAL   PRIMARY KEY,
  post_id           BIGINT      NOT NULL REFERENCES community.posts(id),
  parent_id         BIGINT      REFERENCES community.comments(id),
  author_id         BIGINT      NOT NULL REFERENCES auth.users(id),
  content           TEXT        NOT NULL,
  depth             SMALLINT    NOT NULL DEFAULT 0 CHECK (depth BETWEEN 0 AND 3),
  status            VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'
                    CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
  moderated_by      BIGINT      REFERENCES auth.users(id),
  moderation_reason TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_cmt_self CHECK (parent_id IS NULL OR parent_id <> id),
  -- An/xoa binh luan phai co ly do
  CONSTRAINT ck_cmt_mod  CHECK (status = 'VISIBLE' OR moderation_reason IS NOT NULL)
);
CREATE INDEX ix_cmt_post   ON community.comments(post_id, created_at) WHERE status <> 'DELETED';
CREATE INDEX ix_cmt_parent ON community.comments(parent_id);

-- V2.3 — Gop like + follow + report-flag
CREATE TABLE community.reactions (
  user_id     BIGINT      NOT NULL REFERENCES auth.users(id),
  target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('POST','COMMENT','USER','COLLECTION')),
  target_id   BIGINT      NOT NULL,
  kind        VARCHAR(10) NOT NULL CHECK (kind IN ('LIKE','FOLLOW','REPORT')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, target_type, target_id, kind)
);
CREATE INDEX ix_react_target ON community.reactions(target_type, target_id, kind);

-- V2.4 — Xu ly bao cao vi pham. Vong doi khac reactions nen tach rieng.
CREATE TABLE community.moderation_cases (
  id            BIGSERIAL   PRIMARY KEY,
  target_type   VARCHAR(20) NOT NULL CHECK (target_type IN ('POST','COMMENT','COLLECTION')),
  target_id     BIGINT      NOT NULL,
  report_count  INTEGER     NOT NULL DEFAULT 1 CHECK (report_count > 0),
  reasons       JSONB       NOT NULL DEFAULT '[]',
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ACTIONED','REJECTED')),
  auto_hidden   BOOLEAN     NOT NULL DEFAULT false,
  handled_by    BIGINT      REFERENCES auth.users(id),
  handled_at    TIMESTAMPTZ,
  decision_note TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_mod_handled CHECK (
    status = 'PENDING' OR (handled_by IS NOT NULL AND decision_note IS NOT NULL))
);
-- Gom moi bao cao cua CUNG mot noi dung vao MOT case dang cho xu ly
CREATE UNIQUE INDEX uq_mod_target ON community.moderation_cases(target_type, target_id)
  WHERE status = 'PENDING';
CREATE INDEX ix_mod_queue ON community.moderation_cases(status, report_count DESC) WHERE status = 'PENDING';

-- V2.5 — Cuoc thi co thuong
CREATE TABLE community.contests (
  id                   BIGSERIAL    PRIMARY KEY,
  name                 VARCHAR(200) NOT NULL,
  description          TEXT,
  exam_id              BIGINT       REFERENCES learning.exams(id),
  starts_at            TIMESTAMPTZ  NOT NULL,
  ends_at              TIMESTAMPTZ  NOT NULL,
  max_participants     INTEGER,
  prizes               JSONB        NOT NULL DEFAULT '[]',   -- CHI DOC sau khi cong bo
  status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','CANCELLED')),
  ranking_published_at TIMESTAMPTZ,
  created_by           BIGINT       NOT NULL REFERENCES auth.users(id),
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

  CONSTRAINT ck_contest_time CHECK (starts_at < ends_at),
  -- Xep hang chi cong bo SAU khi dong
  CONSTRAINT ck_contest_rank CHECK (ranking_published_at IS NULL OR ranking_published_at >= ends_at),
  CONSTRAINT ck_contest_exam CHECK (status <> 'PUBLISHED' OR exam_id IS NOT NULL)
);
CREATE INDEX ix_contest_list ON community.contests(status, starts_at) WHERE status = 'PUBLISHED';

-- Dang ky va bai thi cua cuoc thi dung learning.attempts voi kind = 'CONTEST'.
-- Nhat ky nghi gian lan ghi vao shared.audit_logs (action = 'CONTEST_CHEAT_EVENT'),
-- khong ghi vao cot JSONB (AC-09).

-- ============================================================
-- Het V2 — 5 bang cong dong
-- ============================================================

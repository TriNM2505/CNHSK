-- ============================================================
-- CNHSK — Database v6 · Migration V2: cong dong (5 bang, scope V2)
-- CHUA chay o giai doan MVP. Chay khi blog/cuoc thi vao scope.
-- Nguon: docs/cnhsk-database-v6.md §4.16
-- ============================================================

-- V2.1 — Blog co kiem duyet
CREATE TABLE posts (
  id              BIGSERIAL PRIMARY KEY,
  author_id       BIGINT      NOT NULL REFERENCES users(id),
  category        VARCHAR(30) NOT NULL CHECK (category IN
                  ('EXPERIENCE','QUESTION','STUDY_BUDDY','CULTURE','JOB')),
  title           VARCHAR(300) NOT NULL,
  content         TEXT NOT NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','HIDDEN')),
  reviewed_by     BIGINT REFERENCES users(id),
  reviewed_at     TIMESTAMPTZ,
  review_reason   TEXT,
  comments_locked BOOLEAN NOT NULL DEFAULT false,
  published_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_post_reject CHECK (status <> 'REJECTED' OR review_reason IS NOT NULL),
  -- MANAGER khong tu duyet bai cua chinh minh (UC-076 SELF_APPROVAL)
  CONSTRAINT ck_post_self   CHECK (reviewed_by IS NULL OR reviewed_by <> author_id),
  CONSTRAINT ck_post_pub    CHECK (status <> 'APPROVED' OR published_at IS NOT NULL)
);
CREATE INDEX ix_post_feed   ON posts(status, published_at DESC) WHERE status = 'APPROVED';
CREATE INDEX ix_post_author ON posts(author_id, created_at DESC);
CREATE INDEX ix_post_queue  ON posts(status, created_at) WHERE status = 'PENDING';

-- V2.2 — Binh luan long nhau. SOFT DELETE de giu cay tra loi.
CREATE TABLE comments (
  id                BIGSERIAL PRIMARY KEY,
  post_id           BIGINT NOT NULL REFERENCES posts(id),
  parent_id         BIGINT REFERENCES comments(id),
  author_id         BIGINT NOT NULL REFERENCES users(id),
  content           TEXT   NOT NULL,
  depth             SMALLINT NOT NULL DEFAULT 0 CHECK (depth BETWEEN 0 AND 3),
  status            VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'
                    CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
  moderated_by      BIGINT REFERENCES users(id),
  moderation_reason TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_cmt_self  CHECK (parent_id IS NULL OR parent_id <> id),
  -- An/xoa binh luan phai co ly do (UC-079)
  CONSTRAINT ck_cmt_mod   CHECK (status = 'VISIBLE' OR moderation_reason IS NOT NULL)
);
CREATE INDEX ix_cmt_post   ON comments(post_id, created_at) WHERE status <> 'DELETED';
CREATE INDEX ix_cmt_parent ON comments(parent_id);

-- V2.3 — Gop like + follow + report-flag
CREATE TABLE reactions (
  user_id     BIGINT      NOT NULL REFERENCES users(id),
  target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('POST','COMMENT','USER','COLLECTION')),
  target_id   BIGINT      NOT NULL,
  kind        VARCHAR(10) NOT NULL CHECK (kind IN ('LIKE','FOLLOW','REPORT')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, target_type, target_id, kind)
);
-- Dem nhanh so like/follow/report cua mot doi tuong
CREATE INDEX ix_react_target ON reactions(target_type, target_id, kind);

-- V2.4 — Xu ly bao cao vi pham. Vong doi khac reactions nen tach rieng.
CREATE TABLE moderation_cases (
  id            BIGSERIAL PRIMARY KEY,
  target_type   VARCHAR(20) NOT NULL CHECK (target_type IN ('POST','COMMENT','COLLECTION')),
  target_id     BIGINT      NOT NULL,
  report_count  INTEGER     NOT NULL DEFAULT 1 CHECK (report_count > 0),
  reasons       JSONB       NOT NULL DEFAULT '[]',
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','ACTIONED','REJECTED')),
  auto_hidden   BOOLEAN     NOT NULL DEFAULT false,
  handled_by    BIGINT REFERENCES users(id),
  handled_at    TIMESTAMPTZ,
  decision_note TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_mod_handled CHECK (
    status = 'PENDING' OR (handled_by IS NOT NULL AND decision_note IS NOT NULL)
  )
);
-- Gom moi bao cao cua CUNG mot noi dung vao MOT case dang cho xu ly.
-- Giai quyet PARTIAL_REPORT_CLOSURE (UC-078): xu ly mot lan dong tat ca.
CREATE UNIQUE INDEX uq_mod_target ON moderation_cases(target_type, target_id)
  WHERE status = 'PENDING';
CREATE INDEX ix_mod_queue ON moderation_cases(status, report_count DESC) WHERE status = 'PENDING';

-- V2.5 — Cuoc thi co thuong
CREATE TABLE contests (
  id                   BIGSERIAL PRIMARY KEY,
  name                 VARCHAR(200) NOT NULL,
  description          TEXT,
  exam_id              BIGINT REFERENCES exams(id),
  -- TIMESTAMPTZ: client gui ISO-8601 CO offset, hien theo gio Viet Nam
  starts_at            TIMESTAMPTZ NOT NULL,
  ends_at              TIMESTAMPTZ NOT NULL,
  max_participants     INTEGER,
  prizes               JSONB NOT NULL DEFAULT '[]',   -- CHI DOC sau khi cong bo
  status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','CANCELLED')),
  ranking_published_at TIMESTAMPTZ,
  created_by           BIGINT NOT NULL REFERENCES users(id),
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ck_contest_time CHECK (starts_at < ends_at),
  -- Xep hang chi cong bo SAU khi dong (UC-092 RANKING_PUBLISHED_EARLY)
  CONSTRAINT ck_contest_rank CHECK (
    ranking_published_at IS NULL OR ranking_published_at >= ends_at
  ),
  CONSTRAINT ck_contest_exam CHECK (status <> 'PUBLISHED' OR exam_id IS NOT NULL)
);
CREATE INDEX ix_contest_list ON contests(status, starts_at) WHERE status = 'PUBLISHED';

-- Dang ky va bai thi cua cuoc thi dung bang `attempts` voi kind='CONTEST'.
-- Khong can contest_participants va contest_submissions.
--
-- Nhat ky nghi gian lan ghi vao `audit_logs` voi action='CONTEST_CHEAT_EVENT',
-- KHONG ghi vao cot JSONB — giai quyet mau thuan AC-09 (JSONB chi doc).

-- ============================================================
-- Het V2 — 5 bang cong dong
-- ============================================================

CREATE TABLE learning.reference_items (
  id             BIGSERIAL    PRIMARY KEY,
  kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('YOUTUBE_CHANNEL','PODCAST','BOOK')),
  title          VARCHAR(200) NOT NULL,
  description    VARCHAR(2000),                       -- chữ thuần, FE escape (BR-119-6)
  url            TEXT         NOT NULL CHECK (url LIKE 'https://%'),
  thumbnail_url  TEXT         CHECK (thumbnail_url IS NULL OR thumbnail_url LIKE 'https://%'),
  hsk_min        SMALLINT     CHECK (hsk_min BETWEEN 1 AND 9),
  hsk_max        SMALLINT     CHECK (hsk_max BETWEEN 1 AND 9),
  tags           VARCHAR(30)[] NOT NULL DEFAULT '{}',
  -- chỉ BOOK
  author         VARCHAR(200),
  publisher      VARCHAR(200),
  published_year SMALLINT     CHECK (published_year BETWEEN 1900 AND 2100),
  is_affiliate   BOOLEAN      NOT NULL DEFAULT false,
  status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
  sort_order     INTEGER      NOT NULL DEFAULT 0,
  created_by     BIGINT       NOT NULL REFERENCES auth.users(id),
  published_at   TIMESTAMPTZ,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_ref_hsk      CHECK (hsk_min IS NULL OR hsk_max IS NULL OR hsk_min <= hsk_max),
  CONSTRAINT ck_ref_book     CHECK (kind = 'BOOK' OR (author IS NULL AND publisher IS NULL
                                    AND published_year IS NULL AND is_affiliate = false)),
  CONSTRAINT ck_ref_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX ix_ref_public ON learning.reference_items(kind, sort_order, id) WHERE status = 'PUBLISHED';
CREATE INDEX ix_ref_admin  ON learning.reference_items(kind, status, updated_at DESC);

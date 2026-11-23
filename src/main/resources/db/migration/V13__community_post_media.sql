-- Anh/video dinh kem bai cong dong. File nam tren Cloudinary; bang chi giu duong dan va public_id
-- (de xoa ve sau). Toi da 4 file moi bai, giu thu tu hien thi.
CREATE TABLE community.post_media (
  id         BIGSERIAL    PRIMARY KEY,
  post_id    BIGINT       NOT NULL REFERENCES community.posts(id) ON DELETE CASCADE,
  position   SMALLINT     NOT NULL CHECK (position BETWEEN 0 AND 3),
  kind       VARCHAR(10)  NOT NULL CHECK (kind IN ('IMAGE','VIDEO')),
  url        TEXT         NOT NULL,
  public_id  VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_post_media_position UNIQUE (post_id, position)
);

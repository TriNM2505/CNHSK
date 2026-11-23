-- Van choi cuoc thi game giu ref_type GAME_ROUND (moi luong game deu loc theo no), ref_id = cuoc thi.
-- Chi muc V14 gia dinh ref_type CONTEST nen khong bao gio duoc dung; thay bang chi muc dung.
DROP INDEX IF EXISTS learning.ix_att_contest_game;
CREATE INDEX ix_att_game_contest ON learning.attempts(ref_id, user_id)
    WHERE kind = 'GAME' AND ref_type = 'GAME_ROUND' AND ref_id IS NOT NULL AND status = 'SUBMITTED';

-- Kiem duyet vien loai nguoi gian lan khoi xep hang mot cuoc thi game.
CREATE TABLE community.contest_exclusions (
    contest_id  BIGINT       NOT NULL REFERENCES community.contests(id),
    user_id     BIGINT       NOT NULL,
    note        VARCHAR(500) NOT NULL,
    decided_by  BIGINT       NOT NULL,
    decided_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (contest_id, user_id)
);

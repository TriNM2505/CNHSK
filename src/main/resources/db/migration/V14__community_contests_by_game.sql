-- Cuoc thi binh luan: bai co gio dong binh luan, toi gio server tu choi binh luan moi.
ALTER TABLE community.posts ADD COLUMN comments_close_at TIMESTAMPTZ;

-- Cuoc thi game (kiem duyet vien tao): gan mot game thay cho de thi, xep hang top prize_count nguoi.
-- Cuoc thi cu gan de thi van hop le; moi cuoc thi da cong bo phai co de thi hoac game.
ALTER TABLE community.contests ADD COLUMN game_code VARCHAR(30);
ALTER TABLE community.contests ADD COLUMN prize_count SMALLINT NOT NULL DEFAULT 3
    CHECK (prize_count BETWEEN 1 AND 50);
ALTER TABLE community.contests DROP CONSTRAINT ck_contest_exam;
ALTER TABLE community.contests ADD CONSTRAINT ck_contest_subject
    CHECK (status <> 'PUBLISHED' OR exam_id IS NOT NULL OR game_code IS NOT NULL);

-- Luot choi cuoc thi game nam trong learning.attempts (kind GAME, ref_type CONTEST, ref_id = cuoc thi).
CREATE INDEX ix_att_contest_game ON learning.attempts(ref_id, user_id)
    WHERE kind = 'GAME' AND ref_type = 'CONTEST' AND status = 'SUBMITTED';

-- Game rankings are per HSK level: each round stores the level it was played at.
-- Rounds played before this change keep NULL and never enter a level ranking.
ALTER TABLE learning.attempts ADD COLUMN hsk_level SMALLINT;
ALTER TABLE learning.attempts ADD CONSTRAINT ck_att_hsk_level CHECK (hsk_level IS NULL OR hsk_level BETWEEN 1 AND 9);

CREATE INDEX ix_att_game_hsk_rank
    ON learning.attempts(game_code, hsk_level, submitted_at)
    WHERE status = 'SUBMITTED';

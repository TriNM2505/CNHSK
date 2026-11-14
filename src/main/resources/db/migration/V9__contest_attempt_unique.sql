CREATE UNIQUE INDEX uq_contest_attempt
    ON learning.attempts(user_id, ref_id)
    WHERE kind = 'CONTEST';

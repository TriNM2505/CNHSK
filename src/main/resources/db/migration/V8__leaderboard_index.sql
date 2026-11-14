CREATE INDEX ix_att_ref_rank
    ON learning.attempts(kind, ref_id, submitted_at)
    WHERE status = 'SUBMITTED';

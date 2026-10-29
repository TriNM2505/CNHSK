-- Spec 003 section 0.12: shared practice persistence, after V3.
-- attempt_items is empty before the practice engine is introduced; no backfill or seed.
ALTER TABLE learning.attempt_items
    ADD COLUMN item_type VARCHAR(30) NOT NULL;

ALTER TABLE learning.attempt_items
    ADD COLUMN skill VARCHAR(20) NOT NULL
        CHECK (skill IN ('VOCAB','LISTENING','READING','CHARACTER','WRITING','GRAMMAR','PRONUNCIATION'));

ALTER TABLE learning.attempt_items
    ADD COLUMN meta JSONB NOT NULL DEFAULT '{}';

CREATE UNIQUE INDEX uq_ai_order ON learning.attempt_items(attempt_id, order_index);

CREATE INDEX ix_att_user_ref ON learning.attempts(user_id, ref_type, ref_id, status);

CREATE INDEX ix_ai_lexeme ON learning.attempt_items(lexeme_id, attempt_id)
    WHERE lexeme_id IS NOT NULL;

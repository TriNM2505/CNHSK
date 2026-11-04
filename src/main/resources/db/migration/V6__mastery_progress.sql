-- Spec 011 section 0.1: MP-1.0 mastery per KID and FSRS per lexeme on user_progress, after V5.

-- 1. LEXEME rows hold the FSRS schedule of a word, character or grammar point
ALTER TABLE learning.user_progress DROP CONSTRAINT user_progress_target_type_check;
ALTER TABLE learning.user_progress ADD CONSTRAINT user_progress_target_type_check
    CHECK (target_type IN ('KNOWLEDGE_POINT','TOPIC','PRON_STAGE','FLASHCARD','VIDEO','LEXEME'));

-- 2. KNOWLEDGE_POINT rows hold MP-1.0 state; evidence stays in attempt_items
ALTER TABLE learning.user_progress
    ADD COLUMN mp_state          VARCHAR(12)
               CHECK (mp_state IN ('notyet','insufficient','review','consolidate','extend')),
    ADD COLUMN confidence        NUMERIC(6,3) CHECK (confidence >= 0),
    ADD COLUMN evidence_item_ids BIGINT[]     NOT NULL DEFAULT '{}';
ALTER TABLE learning.user_progress
    ADD CONSTRAINT ck_prog_mp CHECK (target_type <> 'KNOWLEDGE_POINT' OR mp_state IS NOT NULL);
CREATE INDEX ix_prog_kp_state ON learning.user_progress(user_id, mp_state)
    WHERE target_type = 'KNOWLEDGE_POINT';

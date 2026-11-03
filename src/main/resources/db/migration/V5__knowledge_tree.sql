-- Spec 010 section 0.1: knowledge tree on existing tables, after V4.
-- knowledge_points, question_knowledge_points and questions have no writer before V5.

-- 1. knowledge_points: one row = one KID node of Cay Nang Luc v2.0
ALTER TABLE learning.knowledge_points
    ADD COLUMN domain      VARCHAR(2)   NOT NULL CHECK (domain IN ('NA','CH','TV','NP','NL','VB')),
    ADD COLUMN name_zh     VARCHAR(200) NOT NULL,
    ADD COLUMN can_do      TEXT,
    ADD COLUMN prereq_ids  BIGINT[]     NOT NULL DEFAULT '{}',
    ADD COLUMN support_ids BIGINT[]     NOT NULL DEFAULT '{}',
    ADD COLUMN updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now();
ALTER TABLE learning.knowledge_points ALTER COLUMN skill_type DROP NOT NULL;
ALTER TABLE learning.knowledge_points
    ADD CONSTRAINT ck_kp_kid CHECK (
        code ~ '^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$'
        AND left(code, 2) = domain
        AND substring(code FROM 3 FOR 1)::SMALLINT = hsk_level),
    -- v2.0: a word is not a node; words link to a TV node through lexemes.knowledge_point_id
    ADD CONSTRAINT ck_kp_no_lexeme CHECK (lexeme_id IS NULL);
CREATE INDEX ix_kp_domain ON learning.knowledge_points(domain, hsk_level);

-- 2. question_knowledge_points: Q-matrix role, exactly one PRIMARY per question
ALTER TABLE learning.question_knowledge_points
    ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'PRIMARY' CHECK (role IN ('PRIMARY','SECONDARY'));
CREATE UNIQUE INDEX uq_qkp_primary ON learning.question_knowledge_points(question_id)
    WHERE role = 'PRIMARY';

-- 3. questions: natural key for re-import and difficulty for 011/012
ALTER TABLE learning.questions
    ADD COLUMN code       VARCHAR(50) UNIQUE,
    ADD COLUMN difficulty SMALLINT    NOT NULL DEFAULT 2 CHECK (difficulty BETWEEN 1 AND 3);
ALTER TABLE learning.questions
    ADD CONSTRAINT ck_q_import_code CHECK (source <> 'IMPORT' OR code IS NOT NULL);

-- 4. existing content -> node
ALTER TABLE learning.topics  ADD COLUMN knowledge_point_id BIGINT REFERENCES learning.knowledge_points(id);
ALTER TABLE learning.lexemes ADD COLUMN knowledge_point_id BIGINT REFERENCES learning.knowledge_points(id);
CREATE INDEX ix_topic_kp  ON learning.topics(knowledge_point_id)  WHERE knowledge_point_id IS NOT NULL;
CREATE INDEX ix_lexeme_kp ON learning.lexemes(knowledge_point_id) WHERE knowledge_point_id IS NOT NULL;

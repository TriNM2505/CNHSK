-- Competency tree v2.0: skills are attributes of nodes and questions, not domains.
-- Additive only: existing columns (skill_type) keep working.

ALTER TABLE learning.knowledge_points
    ADD COLUMN skills        VARCHAR(10)[] NOT NULL DEFAULT '{}',
    ADD COLUMN cluster       VARCHAR(100),
    ADD COLUMN pattern       VARCHAR(300),
    ADD COLUMN examples      JSONB         NOT NULL DEFAULT '{}',
    ADD COLUMN teaching_note TEXT,
    ADD COLUMN source        VARCHAR(300),
    ADD CONSTRAINT ck_kp_skills CHECK (skills <@ ARRAY['LISTENING','SPEAKING','READING','WRITING']::VARCHAR(10)[]);

ALTER TABLE learning.questions
    ADD COLUMN skill VARCHAR(10),
    ADD CONSTRAINT ck_q_skill CHECK (skill IS NULL OR skill IN ('LISTENING','SPEAKING','READING','WRITING'));

-- Rubric-graded speaking answers are stored as attempt items too, so they need their own skill.
ALTER TABLE learning.attempt_items DROP CONSTRAINT attempt_items_skill_check;
ALTER TABLE learning.attempt_items ADD CONSTRAINT attempt_items_skill_check CHECK (skill IN
    ('VOCAB','LISTENING','READING','CHARACTER','WRITING','GRAMMAR','PRONUNCIATION','SPEAKING'));

-- Error codes (appendix D): each code points at the one node the error shows is not yet solid; communicative
-- codes (E-NL-*) point at the NL node of whatever task is graded, so they carry no fixed node.
-- Severity 1 (minor) is feedback only; severities 2 and 3 become zero-score evidence (spec 5.3).
CREATE TABLE learning.error_codes (
    code        VARCHAR(12)  PRIMARY KEY CHECK (code ~ '^E-(NA|CH|TV|NP|NL|VB|DI)-[0-9]{2}$'),
    kid_id      BIGINT       REFERENCES learning.knowledge_points(id),
    severity    SMALLINT     NOT NULL CHECK (severity BETWEEN 1 AND 3),
    name_vi     VARCHAR(200) NOT NULL,
    example     VARCHAR(300),
    fix_hint    VARCHAR(300),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_error_code_kid CHECK (kid_id IS NOT NULL OR code LIKE 'E-NL-%')
);
CREATE INDEX ix_error_codes_kid ON learning.error_codes(kid_id);

-- Speaking and writing tasks: one communicative node (NL) plus the target nodes each rubric criterion feeds.
CREATE TABLE learning.learning_tasks (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9_-]{3,50}$'),
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('SPEAKING','WRITING')),
    hsk_level   SMALLINT     NOT NULL CHECK (hsk_level BETWEEN 1 AND 9),
    nl_kid_id   BIGINT       NOT NULL REFERENCES learning.knowledge_points(id),
    title_vi    VARCHAR(200) NOT NULL,
    prompt_vi   TEXT         NOT NULL,
    prompt_zh   TEXT,
    min_chars   SMALLINT     CHECK (min_chars IS NULL OR min_chars > 0),
    max_seconds SMALLINT     CHECK (max_seconds IS NULL OR max_seconds BETWEEN 10 AND 300),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_learning_tasks_kind_level ON learning.learning_tasks(kind, hsk_level) WHERE is_active;

CREATE TABLE learning.learning_task_targets (
    task_id   BIGINT     NOT NULL REFERENCES learning.learning_tasks(id) ON DELETE CASCADE,
    kid_id    BIGINT     NOT NULL REFERENCES learning.knowledge_points(id),
    criterion VARCHAR(2) NOT NULL CHECK (criterion IN ('C1','C2','C3','C4','C5')),
    PRIMARY KEY (task_id, kid_id, criterion)
);

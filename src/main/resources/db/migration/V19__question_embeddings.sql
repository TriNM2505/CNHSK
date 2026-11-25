-- Sentence vectors of questions, used to drop generated questions that repeat an existing one in meaning.
-- Vectors are compared in the application per knowledge point, so no vector extension is needed.
CREATE TABLE learning.question_embeddings (
    question_id BIGINT      PRIMARY KEY REFERENCES learning.questions(id) ON DELETE CASCADE,
    model       VARCHAR(80) NOT NULL,
    vector      REAL[]      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

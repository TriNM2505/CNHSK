-- Spec 002 section 0.3: content import schema, after V2_1.
-- topics is empty before the first content import; no backfill or seed is needed.
ALTER TABLE learning.topics
    ADD COLUMN code VARCHAR(50) NOT NULL UNIQUE;

ALTER TABLE learning.pron_stages
    ADD COLUMN units JSONB NOT NULL DEFAULT '[]';

-- Reimporting the same file is allowed; row writers handle natural-key upserts.
DROP INDEX learning.uq_import_file;

CREATE UNIQUE INDEX uq_import_running ON learning.service_requests(kind)
    WHERE kind = 'IMPORT' AND status = 'RUNNING';

CREATE INDEX ix_req_import_hist ON learning.service_requests(created_at DESC)
    WHERE kind = 'IMPORT';

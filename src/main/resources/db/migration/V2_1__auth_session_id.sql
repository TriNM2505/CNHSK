ALTER TABLE auth.auth_tokens
    ADD COLUMN session_id UUID;

ALTER TABLE auth.auth_tokens
    ADD CONSTRAINT ck_auth_tokens_refresh_session
        CHECK (token_type <> 'REFRESH' OR session_id IS NOT NULL);

CREATE INDEX ix_tokens_session
    ON auth.auth_tokens(session_id)
    WHERE session_id IS NOT NULL;

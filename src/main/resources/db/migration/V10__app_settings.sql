CREATE TABLE shared.app_settings (
    key        VARCHAR(100) PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_by BIGINT       NOT NULL REFERENCES auth.users(id),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

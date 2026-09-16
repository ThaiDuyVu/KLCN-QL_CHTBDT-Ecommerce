-- =========================================================
-- V2 - CREATE REFRESH TOKENS
-- =========================================================

CREATE TABLE refresh_tokens (
                                 refresh_token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                 user_id UUID NOT NULL,
                                 token_hash VARCHAR(64) NOT NULL,

                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 expires_at TIMESTAMPTZ NOT NULL,
                                 revoked_at TIMESTAMPTZ,
                                 replaced_by_token_id UUID,

                                 CONSTRAINT uq_refresh_tokens_token_hash
                                     UNIQUE (token_hash),

                                 CONSTRAINT ck_refresh_tokens_expiry
                                     CHECK (expires_at > created_at),

                                 CONSTRAINT ck_refresh_tokens_revoked_at
                                     CHECK (
                                         revoked_at IS NULL
                                             OR revoked_at >= created_at
                                     ),

                                 CONSTRAINT fk_refresh_tokens_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES users (user_id)
                                         ON DELETE RESTRICT,

                                 CONSTRAINT fk_refresh_tokens_replaced_by
                                     FOREIGN KEY (replaced_by_token_id)
                                         REFERENCES refresh_tokens (refresh_token_id)
                                         ON DELETE RESTRICT
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

CREATE INDEX idx_refresh_tokens_replaced_by_token_id
    ON refresh_tokens (replaced_by_token_id);

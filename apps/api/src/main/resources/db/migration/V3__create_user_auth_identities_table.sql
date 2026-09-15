-- =============================================================================
-- V3__create_user_auth_identities_table.sql
-- Joblivo User Authentication Identities Database Table
-- Decouples the core user identity from specific authentication mechanisms.
-- =============================================================================

CREATE TABLE user_auth_identities (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_auth_identities PRIMARY KEY (id),
    CONSTRAINT fk_user_auth_identities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_auth_identities_provider CHECK (provider IN ('EMAIL', 'GOOGLE', 'PHONE')),
    CONSTRAINT uq_user_auth_identities_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_user_auth_identities_user_id ON user_auth_identities (user_id);

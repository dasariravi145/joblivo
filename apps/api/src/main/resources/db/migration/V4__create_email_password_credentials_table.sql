-- =============================================================================
-- V4__create_email_password_credentials_table.sql
-- Joblivo Email/Password Authentication Credentials Database Table
-- Stores password hashes for EMAIL authentication identities.
-- =============================================================================

CREATE TABLE email_password_credentials (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    auth_identity_id UUID         NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_email_password_credentials PRIMARY KEY (id),
    CONSTRAINT fk_email_password_credentials_auth_identity FOREIGN KEY (auth_identity_id) REFERENCES user_auth_identities (id) ON DELETE RESTRICT,
    CONSTRAINT uq_email_password_credentials_auth_identity UNIQUE (auth_identity_id)
);

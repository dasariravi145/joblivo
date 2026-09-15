-- =============================================================================
-- V2__create_users_table.sql
-- Joblivo Users Database Table
-- =============================================================================

CREATE TABLE users (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));

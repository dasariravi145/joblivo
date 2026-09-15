-- =============================================================================
-- V5__create_career_profiles_table.sql
-- Joblivo Master Career Profile Database Table
-- Foundational table representing the central career profile belonging to a user.
-- Enforces a strict 1-to-1 relationship per user with ON DELETE RESTRICT.
-- =============================================================================

CREATE TABLE career_profiles (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_career_profiles PRIMARY KEY (id),
    CONSTRAINT fk_career_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_career_profiles_user_id UNIQUE (user_id)
);

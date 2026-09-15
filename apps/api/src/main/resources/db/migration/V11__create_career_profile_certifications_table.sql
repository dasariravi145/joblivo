-- =============================================================================
-- V11__create_career_profile_certifications_table.sql
-- Joblivo Career Profile Certifications Database Table
-- Stores factual professional certifications belonging to a Master Career Profile.
-- Enforces integrity: FK on delete cascade, non-blank name and issuing organization,
-- date range check, does_not_expire consistency, and duplicate prevention per profile.
-- =============================================================================

CREATE TABLE career_profile_certifications (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    career_profile_id    UUID         NOT NULL,
    certification_name   VARCHAR(150) NOT NULL,
    issuing_organization VARCHAR(150) NOT NULL,
    credential_id        VARCHAR(100),
    credential_url       VARCHAR(500),
    issue_date           DATE,
    expiration_date      DATE,
    does_not_expire      BOOLEAN      NOT NULL DEFAULT FALSE,
    description          TEXT,
    display_order        INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_career_profile_certifications PRIMARY KEY (id),
    CONSTRAINT fk_career_profile_certifications_profile FOREIGN KEY (career_profile_id)
        REFERENCES career_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_profile_certifications_name
        CHECK (length(trim(certification_name)) > 0),
    CONSTRAINT chk_career_profile_certifications_org
        CHECK (length(trim(issuing_organization)) > 0),
    CONSTRAINT chk_career_profile_certifications_date_range
        CHECK (issue_date IS NULL OR expiration_date IS NULL OR issue_date <= expiration_date),
    CONSTRAINT chk_career_profile_certifications_expire
        CHECK (does_not_expire = FALSE OR expiration_date IS NULL),
    CONSTRAINT chk_career_profile_certifications_url
        CHECK (credential_url IS NULL OR length(trim(credential_url)) > 0),
    CONSTRAINT chk_career_profile_certifications_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_career_profile_certifications_profile_id
    ON career_profile_certifications (career_profile_id);

CREATE INDEX idx_career_profile_certifications_profile_order
    ON career_profile_certifications (career_profile_id, display_order);

-- When credential_id is present, it serves as a unique identifier for the user's profile
CREATE UNIQUE INDEX uq_career_profile_certifications_cred_id
    ON career_profile_certifications (career_profile_id, lower(trim(credential_id)))
    WHERE credential_id IS NOT NULL AND length(trim(credential_id)) > 0;

-- When credential_id is absent, certification name + issuing organization must be unique for the profile
CREATE UNIQUE INDEX uq_career_profile_certifications_name_org_no_cred
    ON career_profile_certifications (career_profile_id, lower(trim(certification_name)), lower(trim(issuing_organization)))
    WHERE credential_id IS NULL OR length(trim(credential_id)) = 0;

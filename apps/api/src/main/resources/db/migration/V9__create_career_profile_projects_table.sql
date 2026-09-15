-- =============================================================================
-- V9__create_career_profile_projects_table.sql
-- Joblivo Career Profile Projects Database Table
-- Stores factual professional, academic, open-source, and personal project records
-- belonging to a Master Career Profile.
-- Enforces integrity: FK on delete cascade, non-blank project name, validated enums,
-- date range check, currently_active consistency, and duplicate prevention per profile.
-- =============================================================================

CREATE TABLE career_profile_projects (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    career_profile_id UUID         NOT NULL,
    project_name      VARCHAR(100) NOT NULL,
    project_type      VARCHAR(30)  NOT NULL,
    role              VARCHAR(100),
    description       TEXT,
    start_date        DATE,
    end_date          DATE,
    currently_active  BOOLEAN      NOT NULL DEFAULT FALSE,
    project_url       VARCHAR(500),
    display_order     INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_career_profile_projects PRIMARY KEY (id),
    CONSTRAINT fk_career_profile_projects_profile FOREIGN KEY (career_profile_id)
        REFERENCES career_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_profile_projects_name
        CHECK (length(trim(project_name)) > 0),
    CONSTRAINT chk_career_profile_projects_type
        CHECK (project_type IN (
            'PROFESSIONAL', 'PERSONAL', 'ACADEMIC',
            'OPEN_SOURCE', 'FREELANCE', 'OTHER'
        )),
    CONSTRAINT chk_career_profile_projects_date_range
        CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_career_profile_projects_currently_active
        CHECK (currently_active = FALSE OR end_date IS NULL),
    CONSTRAINT chk_career_profile_projects_url
        CHECK (project_url IS NULL OR length(trim(project_url)) > 0),
    CONSTRAINT chk_career_profile_projects_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_career_profile_projects_profile_id
    ON career_profile_projects (career_profile_id);

CREATE INDEX idx_career_profile_projects_profile_order
    ON career_profile_projects (career_profile_id, display_order);

CREATE UNIQUE INDEX uq_career_profile_projects_profile_name_lower
    ON career_profile_projects (career_profile_id, lower(trim(project_name)));

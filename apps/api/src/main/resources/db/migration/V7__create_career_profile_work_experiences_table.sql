-- =============================================================================
-- V7__create_career_profile_work_experiences_table.sql
-- Joblivo Career Profile Work Experiences Database Table
-- Stores factual employment history records belonging to a Master Career Profile.
-- Enforces integrity: FK on delete cascade, non-blank text, date ranges,
-- and logical consistency between currently_working and end_date.
-- =============================================================================

CREATE TABLE career_profile_work_experiences (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    career_profile_id UUID         NOT NULL,
    company_name      VARCHAR(100) NOT NULL,
    job_title         VARCHAR(100) NOT NULL,
    employment_type   VARCHAR(30)  NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE,
    currently_working BOOLEAN      NOT NULL DEFAULT FALSE,
    location          VARCHAR(100),
    description       TEXT,
    display_order     INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_career_profile_work_experiences PRIMARY KEY (id),
    CONSTRAINT fk_career_profile_work_experiences_profile FOREIGN KEY (career_profile_id)
        REFERENCES career_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_profile_work_experiences_company_name
        CHECK (length(trim(company_name)) > 0),
    CONSTRAINT chk_career_profile_work_experiences_job_title
        CHECK (length(trim(job_title)) > 0),
    CONSTRAINT chk_career_profile_work_experiences_employment_type
        CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'FREELANCE', 'TEMPORARY', 'OTHER')),
    CONSTRAINT chk_career_profile_work_experiences_date_range
        CHECK (end_date IS NULL OR start_date <= end_date),
    CONSTRAINT chk_career_profile_work_experiences_currently_working
        CHECK (currently_working = FALSE OR end_date IS NULL),
    CONSTRAINT chk_career_profile_work_experiences_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_career_profile_work_experiences_profile_id
    ON career_profile_work_experiences (career_profile_id);

CREATE INDEX idx_career_profile_work_experiences_profile_order
    ON career_profile_work_experiences (career_profile_id, display_order);

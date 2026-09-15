-- =============================================================================
-- V6__add_career_profile_core_details.sql
-- Joblivo Career Profile Core Details
-- Adds basic professional identity and targeting attributes to career_profiles.
-- All columns are nullable to preserve existing records cleanly.
-- =============================================================================

ALTER TABLE career_profiles
    ADD COLUMN professional_headline   VARCHAR(200),
    ADD COLUMN current_title           VARCHAR(100),
    ADD COLUMN current_company         VARCHAR(100),
    ADD COLUMN total_experience_months INTEGER,
    ADD COLUMN current_location        VARCHAR(100),
    ADD COLUMN preferred_work_location VARCHAR(100),
    ADD COLUMN preferred_work_mode     VARCHAR(20),
    ADD COLUMN notice_period_days      INTEGER,
    ADD CONSTRAINT chk_career_profiles_total_experience_months
        CHECK (total_experience_months IS NULL OR total_experience_months >= 0),
    ADD CONSTRAINT chk_career_profiles_notice_period_days
        CHECK (notice_period_days IS NULL OR notice_period_days >= 0),
    ADD CONSTRAINT chk_career_profiles_preferred_work_mode
        CHECK (preferred_work_mode IS NULL OR preferred_work_mode IN ('REMOTE', 'HYBRID', 'ONSITE', 'FLEXIBLE'));

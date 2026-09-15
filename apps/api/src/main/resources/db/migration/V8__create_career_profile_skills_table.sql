-- =============================================================================
-- V8__create_career_profile_skills_table.sql
-- Joblivo Career Profile Skills & Technologies Database Table
-- Stores factual technical competencies and skills for a Master Career Profile.
-- Enforces integrity: FK on delete cascade, non-blank name, validated enums,
-- non-negative experience, and duplicate skill prevention per career profile.
-- =============================================================================

CREATE TABLE career_profile_skills (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    career_profile_id   UUID          NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    category            VARCHAR(50)   NOT NULL,
    proficiency         VARCHAR(30)   NOT NULL,
    years_of_experience NUMERIC(4, 1),
    last_used_date      DATE,
    display_order       INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_career_profile_skills PRIMARY KEY (id),
    CONSTRAINT fk_career_profile_skills_profile FOREIGN KEY (career_profile_id)
        REFERENCES career_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_profile_skills_name
        CHECK (length(trim(name)) > 0),
    CONSTRAINT chk_career_profile_skills_category
        CHECK (category IN (
            'PROGRAMMING_LANGUAGE', 'CLOUD', 'DEVOPS', 'DATABASE',
            'FRAMEWORK', 'TOOL', 'PLATFORM', 'DATA',
            'AI_ML', 'SECURITY', 'TESTING', 'OTHER'
        )),
    CONSTRAINT chk_career_profile_skills_proficiency
        CHECK (proficiency IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT chk_career_profile_skills_years_of_experience
        CHECK (years_of_experience IS NULL OR years_of_experience >= 0),
    CONSTRAINT chk_career_profile_skills_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_career_profile_skills_profile_id
    ON career_profile_skills (career_profile_id);

CREATE INDEX idx_career_profile_skills_profile_order
    ON career_profile_skills (career_profile_id, display_order);

CREATE UNIQUE INDEX uq_career_profile_skills_profile_name_lower
    ON career_profile_skills (career_profile_id, lower(trim(name)));

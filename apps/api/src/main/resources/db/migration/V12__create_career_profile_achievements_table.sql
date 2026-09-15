-- =============================================================================
-- V12__create_career_profile_achievements_table.sql
-- Joblivo Career Profile Achievements Database Table
-- Stores factual professional, academic, competition, and leadership achievements
-- belonging to a Master Career Profile.
-- Enforces integrity: FK on delete cascade, non-blank title, validated enum type,
-- non-negative display order, and composite duplicate prevention per profile.
-- =============================================================================

CREATE TABLE career_profile_achievements (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    career_profile_id    UUID         NOT NULL,
    title                VARCHAR(150) NOT NULL,
    achievement_type     VARCHAR(50)  NOT NULL,
    description          TEXT,
    achievement_date     DATE,
    issuing_organization VARCHAR(150),
    url                  VARCHAR(500),
    display_order        INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_career_profile_achievements PRIMARY KEY (id),
    CONSTRAINT fk_career_profile_achievements_profile FOREIGN KEY (career_profile_id)
        REFERENCES career_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_profile_achievements_title
        CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_career_profile_achievements_type
        CHECK (achievement_type IN (
            'AWARD', 'RECOGNITION', 'PROMOTION', 'PERFORMANCE',
            'COMPETITION', 'HACKATHON', 'PUBLICATION', 'PATENT',
            'LEADERSHIP', 'ACADEMIC', 'PROJECT', 'OTHER'
        )),
    CONSTRAINT chk_career_profile_achievements_url
        CHECK (url IS NULL OR length(trim(url)) > 0),
    CONSTRAINT chk_career_profile_achievements_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_career_profile_achievements_profile_id
    ON career_profile_achievements (career_profile_id);

CREATE INDEX idx_career_profile_achievements_profile_order
    ON career_profile_achievements (career_profile_id, display_order);

-- Composite unique functional index preventing duplicate achievements within the same career profile
CREATE UNIQUE INDEX uq_career_profile_achievements_composite
    ON career_profile_achievements (
        career_profile_id,
        lower(trim(title)),
        achievement_type,
        coalesce(achievement_date, '1900-01-01'::date),
        lower(trim(coalesce(issuing_organization, '')))
    );

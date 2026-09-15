-- =============================================================================
-- V10: Create career_profile_education Table
-- =============================================================================
-- Supports Master Career Profile Education qualification records with multi-tenant isolation,
-- controlled education levels, date consistency, and case/whitespace-insensitive duplicate prevention.

CREATE TABLE IF NOT EXISTS career_profile_education (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    career_profile_id UUID NOT NULL,
    institution_name VARCHAR(150) NOT NULL,
    degree VARCHAR(100),
    field_of_study VARCHAR(100),
    education_level VARCHAR(50) NOT NULL,
    start_date DATE,
    end_date DATE,
    currently_studying BOOLEAN NOT NULL DEFAULT FALSE,
    grade VARCHAR(50),
    location VARCHAR(150),
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_career_profile_education_profile
        FOREIGN KEY (career_profile_id)
        REFERENCES career_profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_career_profile_education_institution_not_blank
        CHECK (LENGTH(TRIM(institution_name)) > 0),

    CONSTRAINT chk_career_profile_education_level
        CHECK (education_level IN (
            'HIGH_SCHOOL',
            'DIPLOMA',
            'UNDERGRADUATE',
            'POSTGRADUATE',
            'DOCTORATE',
            'PROFESSIONAL',
            'OTHER'
        )),

    CONSTRAINT chk_career_profile_education_date_range
        CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date),

    CONSTRAINT chk_career_profile_education_active_end_date
        CHECK (currently_studying = false OR end_date IS NULL),

    CONSTRAINT chk_career_profile_education_display_order_non_negative
        CHECK (display_order >= 0)
);

-- Performance index for looking up education by career profile
CREATE INDEX IF NOT EXISTS idx_career_profile_education_profile_id
    ON career_profile_education (career_profile_id);

-- Performance index for sorting education by display order within a career profile
CREATE INDEX IF NOT EXISTS idx_career_profile_education_profile_order
    ON career_profile_education (career_profile_id, display_order);

-- Prevent accidental duplicate education entries (same institution, degree, and field of study within a profile)
CREATE UNIQUE INDEX IF NOT EXISTS uq_career_profile_education_composite
    ON career_profile_education (
        career_profile_id,
        lower(trim(institution_name)),
        lower(trim(coalesce(degree, ''))),
        lower(trim(coalesce(field_of_study, '')))
    );

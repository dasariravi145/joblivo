-- =============================================================================
-- V13__create_jobs_table.sql
-- Joblivo Job Discovery Foundation Database Table
-- Stores normalized, source-neutral job catalog entries collected from permitted sources.
-- Jobs are shared platform catalog data, not owned by a single user (no user_id).
-- Enforces integrity: composite uniqueness on (source, external_job_id), non-blank fields,
-- validated work modes, employment types, application methods, valid salary & experience ranges,
-- and consistent timeline constraints.
-- =============================================================================

CREATE TABLE jobs (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    source               VARCHAR(50)   NOT NULL,
    external_job_id      VARCHAR(255)  NOT NULL,
    title                VARCHAR(255)  NOT NULL,
    company_name         VARCHAR(255)  NOT NULL,
    recruiter_name       VARCHAR(255),
    description          TEXT,
    location             VARCHAR(255),
    work_mode            VARCHAR(50)   NOT NULL,
    employment_type      VARCHAR(50)   NOT NULL,
    experience_min_years INTEGER,
    experience_max_years INTEGER,
    salary_min           NUMERIC(15, 2),
    salary_max           NUMERIC(15, 2),
    salary_currency      VARCHAR(10),
    salary_period        VARCHAR(20),
    job_url              VARCHAR(1000),
    company_url          VARCHAR(1000),
    posted_at            TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ,
    discovered_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    application_method   VARCHAR(50)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT uq_jobs_source_external_id UNIQUE (source, external_job_id),
    CONSTRAINT chk_jobs_title CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_jobs_company_name CHECK (length(trim(company_name)) > 0),
    CONSTRAINT chk_jobs_external_job_id CHECK (length(trim(external_job_id)) > 0),
    CONSTRAINT chk_jobs_source CHECK (source IN (
        'LINKEDIN', 'NAUKRI', 'FOUNDIT', 'CUTSHORT', 'INSTAHYRE', 'COMPANY_CAREERS', 'ATS', 'OTHER'
    )),
    CONSTRAINT chk_jobs_work_mode CHECK (work_mode IN ('REMOTE', 'HYBRID', 'ONSITE', 'UNKNOWN')),
    CONSTRAINT chk_jobs_employment_type CHECK (employment_type IN (
        'FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'TEMPORARY', 'FREELANCE', 'OTHER', 'UNKNOWN'
    )),
    CONSTRAINT chk_jobs_application_method CHECK (application_method IN (
        'INTERNAL_PORTAL', 'EXTERNAL_COMPANY_SITE', 'ATS', 'SOURCE_PORTAL', 'EMAIL', 'OTHER', 'UNKNOWN'
    )),
    CONSTRAINT chk_jobs_salary_period CHECK (
        salary_period IS NULL OR salary_period IN ('YEAR', 'MONTH', 'HOUR', 'OTHER')
    ),
    CONSTRAINT chk_jobs_salary_range CHECK (
        (salary_min IS NULL OR salary_min >= 0) AND
        (salary_max IS NULL OR salary_max >= 0) AND
        (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
    ),
    CONSTRAINT chk_jobs_experience_range CHECK (
        (experience_min_years IS NULL OR experience_min_years >= 0) AND
        (experience_max_years IS NULL OR experience_max_years >= 0) AND
        (experience_min_years IS NULL OR experience_max_years IS NULL OR experience_min_years <= experience_max_years)
    ),
    CONSTRAINT chk_jobs_dates CHECK (
        expires_at IS NULL OR posted_at IS NULL OR posted_at <= expires_at
    ),
    CONSTRAINT chk_jobs_discovered_last_seen CHECK (last_seen_at >= discovered_at)
);

CREATE INDEX idx_jobs_source_external_id ON jobs (source, external_job_id);
CREATE INDEX idx_jobs_posted_at ON jobs (posted_at);
CREATE INDEX idx_jobs_company_name ON jobs (lower(trim(company_name)));

-- =============================================================================
-- V14__add_job_search_indexes.sql
-- Job Discovery Query & Search Foundation
-- Adds targeted performance indexes for high-frequency search and filter columns.
-- =============================================================================

CREATE INDEX idx_jobs_work_mode ON jobs (work_mode);
CREATE INDEX idx_jobs_employment_type ON jobs (employment_type);
CREATE INDEX idx_jobs_location ON jobs (lower(trim(location)));

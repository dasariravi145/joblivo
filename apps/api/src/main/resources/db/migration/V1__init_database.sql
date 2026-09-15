-- =============================================================================
-- V1__init_database.sql
-- Joblivo Initial Database Foundation
-- Sets up essential database-level extensions for UUID and crypto support.
-- Note: Domain/business tables (e.g. users) will be introduced in Prompt 06.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

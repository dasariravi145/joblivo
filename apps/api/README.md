# Joblivo API

Production-ready Spring Boot backend API foundation for Joblivo.

## Technology Stack

- **Java:** 17 LTS
- **Framework:** Spring Boot 3.4.3
- **Build Tool:** Maven
- **Modules:**
  - Spring Web
  - Spring Boot Actuator
  - Jakarta Bean Validation
  - Spring Security (`spring-boot-starter-security`)
  - Spring Data JPA (Hibernate)
  - PostgreSQL Driver (`org.postgresql:postgresql`)
  - Flyway Database Migrations (`flyway-core`, `flyway-database-postgresql`)

## Database & Migrations

- **Database Engine:** PostgreSQL
- **Schema Management:** Flyway is the authoritative source for database migrations.
- **DDL Strategy:** Hibernate automatic schema creation/update is disabled (`ddl-auto: validate`).
- **Migration Location:** `src/main/resources/db/migration/`
  - `V1__init_database.sql` — Minimal database-level foundation (extensions).
  - `V2__create_users_table.sql` — Users table definition (UUID PK, case-insensitive email uniqueness, status constraint, timestamptz audit timestamps).
  - `V3__create_user_auth_identities_table.sql` — Authentication identities table (decouples authentication methods from the core user record, enforcing provider + provider_subject uniqueness, user_id index, and ON DELETE RESTRICT).
  - `V4__create_email_password_credentials_table.sql` — Email/password credentials table (stores password hashes for EMAIL identities, strictly isolated via 1-to-1 relationship and ON DELETE RESTRICT).
  - `V5__create_career_profiles_table.sql` — Master career profile table (central career source of truth, enforcing 1-to-1 relationship with users via UUID PK, unique user_id, and ON DELETE RESTRICT).
  - `V6__add_career_profile_core_details.sql` — Career profile core details (professional headline, current title/company, experience months, locations, work mode, and notice period days with constraints).
  - `V7__create_career_profile_work_experiences_table.sql` — Career profile work experiences table (stores factual employment history records for a master career profile, with foreign key ON DELETE CASCADE, employment_type check, date range check, and currently_working consistency constraint).
  - `V8__create_career_profile_skills_table.sql` — Career profile skills & technologies table (stores structured competency records for a master career profile, with foreign key ON DELETE CASCADE, category check, proficiency check, non-negative years of experience, and a unique functional index enforcing case/whitespace-insensitive duplicate prevention per profile).
  - `V9__create_career_profile_projects_table.sql` — Career profile projects table (stores factual professional, academic, open-source, and personal project records for a master career profile, with foreign key ON DELETE CASCADE, project_type check, date range check, currently_active consistency, URL format validation, and unique functional index preventing duplicate project names per profile).
  - `V10__create_career_profile_education_table.sql` — Career profile education table (stores structured education history for a master career profile, with foreign key ON DELETE CASCADE, education_level check, date range check, currently_studying consistency, and unique functional index preventing duplicate institution + degree + field of study records per profile).
  - `V11__create_career_profile_certifications_table.sql` — Career profile certifications table (stores factual professional certifications for a master career profile, with foreign key ON DELETE CASCADE, non-blank name and organization check, date range check, does_not_expire consistency, and unique functional indexes preventing duplicate certifications per profile).
  - `V12__create_career_profile_achievements_table.sql` — Career profile achievements table (stores factual honors, awards, publications, patents, promotions, and milestones for a master career profile, with foreign key ON DELETE CASCADE, non-blank title check, achievement_type enum check, non-negative display order, and unique composite index preventing duplicate achievements per profile).
  - `V13__create_jobs_table.sql` — Source-neutral jobs catalog table (stores normalized, shared job records with UUID PK, composite unique constraint on source and external_job_id, enum checks, range checks, and chronological timeline constraints).
  - `V14__add_job_search_indexes.sql` — B-Tree indexes on `jobs` table (`work_mode`, `employment_type`, and functional index `lower(trim(location))`) to accelerate deterministic search queries.

## Domain Modules

- **Master Career Profile (`com.joblivo.profile`):**
  - `CareerProfile`: JPA Entity mapped to `career_profiles` table (1-to-1 relationship with `User`, UUID PK, audit timestamps, and core professional targeting fields)
  - `WorkMode`: Strongly-typed enum (`REMOTE`, `HYBRID`, `ONSITE`, `FLEXIBLE`) for preferred work arrangement
  - `EmploymentType`: Strongly-typed enum (`FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP`, `FREELANCE`, `TEMPORARY`, `OTHER`) for work experience records
  - `CareerProfileWorkExperience`: JPA Entity mapped to `career_profile_work_experiences` table representing factual employment records (composite child of `CareerProfile`)
  - `CareerProfileWorkExperienceRepository`: Spring Data JPA repository supporting work experience queries, display ordering, and profile isolation
  - `SkillCategory`: Strongly-typed enum (`PROGRAMMING_LANGUAGE`, `CLOUD`, `DEVOPS`, `DATABASE`, `FRAMEWORK`, `TOOL`, `PLATFORM`, `DATA`, `AI_ML`, `SECURITY`, `TESTING`, `OTHER`) for skill categorization
  - `SkillProficiency`: Strongly-typed enum (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `EXPERT`) for competency levels
  - `CareerProfileSkill`: JPA Entity mapped to `career_profile_skills` table representing technical skills and competencies
  - `CareerProfileSkillRepository`: Spring Data JPA repository supporting skill queries, display ordering, tenant isolation, and normalized duplicate detection
  - `ProjectType`: Strongly-typed enum (`PROFESSIONAL`, `PERSONAL`, `ACADEMIC`, `OPEN_SOURCE`, `FREELANCE`, `OTHER`) for project categorization
  - `CareerProfileProject`: JPA Entity mapped to `career_profile_projects` table representing factual project contributions
  - `CareerProfileProjectRepository`: Spring Data JPA repository supporting project queries, display ordering, tenant isolation, and normalized duplicate detection
  - `EducationLevel`: Strongly-typed enum (`HIGH_SCHOOL`, `DIPLOMA`, `UNDERGRADUATE`, `POSTGRADUATE`, `DOCTORATE`, `PROFESSIONAL`, `OTHER`) for education level
  - `CareerProfileEducation`: JPA Entity mapped to `career_profile_education` table representing factual educational background
  - `CareerProfileEducationRepository`: Spring Data JPA repository supporting education queries, display ordering, tenant isolation, and composite duplicate detection
  - `CareerProfileCertification`: JPA Entity mapped to `career_profile_certifications` table representing factual professional certifications
  - `CareerProfileCertificationRepository`: Spring Data JPA repository supporting certification queries, display ordering, tenant isolation, and normalized duplicate detection
  - `AchievementType`: Strongly-typed enum (`AWARD`, `RECOGNITION`, `PROMOTION`, `PERFORMANCE`, `COMPETITION`, `HACKATHON`, `PUBLICATION`, `PATENT`, `LEADERSHIP`, `ACADEMIC`, `PROJECT`, `OTHER`) for achievement categorization
  - `CareerProfileAchievement`: JPA Entity mapped to `career_profile_achievements` table representing factual honors, awards, and achievements
  - `CareerProfileAchievementRepository`: Spring Data JPA repository supporting achievement queries, display ordering, tenant isolation, and composite normalized duplicate detection
  - `CareerProfileRepository`: Spring Data JPA repository supporting user lookups and profile existence queries
  - `CareerProfileService`: Domain service for transactional profile creation, core details updating, work experience lifecycle, skills lifecycle, projects lifecycle, education lifecycle, certifications lifecycle, achievements lifecycle (add, retrieve, update, delete), active user status eligibility verification, and duplicate prevention
  - `DuplicateCareerProfileException`: Domain exception thrown when a user already has a career profile (translated to 409 Conflict)
  - `DuplicateSkillException`: Domain exception thrown when attempting to add or update a duplicate skill (translated to 409 Conflict)
  - `DuplicateProjectException`: Domain exception thrown when attempting to add or update a duplicate project (translated to 409 Conflict)
  - `DuplicateEducationException`: Domain exception thrown when attempting to add or update a duplicate education record (translated to 409 Conflict)
  - `DuplicateCertificationException`: Domain exception thrown when attempting to add or update a duplicate certification record (translated to 409 Conflict)
  - `DuplicateAchievementException`: Domain exception thrown when attempting to add or update a duplicate achievement record (translated to 409 Conflict)
  - `IneligibleUserException`: Domain exception thrown when a user status (e.g. SUSPENDED, DELETED) disqualifies them from profile ownership (translated to 400 Bad Request)
  - `WorkExperienceNotFoundException`: Domain exception thrown when a requested work experience is not found or does not belong to the user's profile (translated to 404 Not Found)
  - `SkillNotFoundException`: Domain exception thrown when a requested skill is not found or does not belong to the user's profile (translated to 404 Not Found)
  - `ProjectNotFoundException`: Domain exception thrown when a requested project is not found or does not belong to the user's profile (translated to 404 Not Found)
  - `EducationNotFoundException`: Domain exception thrown when a requested education record is not found or does not belong to the user's profile (translated to 404 Not Found)
  - `CertificationNotFoundException`: Domain exception thrown when a requested certification record is not found or does not belong to the user's profile (translated to 404 Not Found)
  - `AchievementNotFoundException`: Domain exception thrown when a requested achievement record is not found or does not belong to the user's profile (translated to 404 Not Found)
  - `UpdateCareerProfileRequest`: Validated DTO for updating core profile information (enforcing size limits, non-negative numbers, trimming, and ownership immutability)
  - `WorkExperienceRequest`: Validated DTO for creating and updating work experience records (validating company name, job title, employment type, start date, date ranges, and currently working rules)
  - `WorkExperienceResponse`: Safe DTO exposing work experience details without persistence internals
  - `SkillRequest`: Validated DTO for creating and updating skill records (validating name, category, proficiency, non-negative years of experience, and display order)
  - `SkillResponse`: Safe DTO exposing skill details without persistence internals
  - `ProjectRequest`: Validated DTO for creating and updating project records (validating project name, project type, date ranges, currently active consistency, and URL format)
  - `ProjectResponse`: Safe DTO exposing project details without persistence internals
  - `EducationRequest`: Validated DTO for creating and updating education records (validating institution name, education level, date ranges, currently studying consistency, and display order)
  - `EducationResponse`: Safe DTO exposing education details without persistence internals
  - `CertificationRequest`: Validated DTO for creating and updating certification records (validating certification name, issuing organization, date ranges, doesNotExpire consistency, credential URL format, and display order)
  - `CertificationResponse`: Safe DTO exposing certification details without persistence internals
  - `AchievementRequest`: Validated DTO for creating and updating achievement records (validating title, achievement type, date, URL format, and display order)
  - `AchievementResponse`: Safe DTO exposing achievement details without persistence internals
  - `CareerProfileNotFoundException`: Domain exception thrown when a requested Master Career Profile does not exist for the authenticated user (translated to 404 Not Found)
  - `SectionCompletenessResponse`: Safe DTO reporting section-level completion status and item count
  - `CareerProfileCompletenessResponse`: Safe DTO providing deterministic section-level completion metrics and overall completion percentage (7 equal-weight sections, integer division, zero AI/readiness claims)
  - `MasterCareerProfileResponse`: Unified read model DTO aggregating core details, deterministically ordered child collections, and completeness summary
  - `CareerProfileResponse`: Safe DTO returning profile id, user id, core profile attributes, and audit timestamps without internal entity details
  - `CareerProfileController`: REST controller exposing master career profile aggregate read (`GET /api/v1/career-profile`), profile creation/update operations, work experience CRUD (`/api/v1/career-profile/work-experiences`), skill CRUD (`/api/v1/career-profile/skills`), project CRUD (`/api/v1/career-profile/projects`), education CRUD (`/api/v1/career-profile/education`), certifications CRUD (`/api/v1/career-profile/certifications`), and achievements CRUD (`/api/v1/career-profile/achievements`) bound strictly to the authenticated security principal

- **User Management & Authentication Credentials (`com.joblivo.user`):**
  - `User`: JPA Entity mapped to `users` table
  - `UserStatus`: Strongly-typed enum (`ACTIVE`, `SUSPENDED`, `DELETED`)
  - `UserRepository`: Spring Data JPA repository for core user management
  - `AuthProvider`: Strongly-typed enum (`EMAIL`, `GOOGLE`, `PHONE`) for supported authentication providers
  - `UserAuthIdentity`: JPA Entity mapped to `user_auth_identities` table
  - `UserAuthIdentityRepository`: Spring Data JPA repository for authentication identity lookups
  - `UserAuthIdentityService`: Domain service for authentication identity management, safe creation, user ownership verification, canonical subject normalization, and provider isolation
  - `DuplicateAuthIdentityException`: Domain exception thrown when an authentication identity already exists for a provider and subject (translated to 409 Conflict)
  - `EmailPasswordCredential`: JPA Entity mapped to `email_password_credentials` table (strictly stores password hash, never raw password)
  - `EmailPasswordCredentialRepository`: Spring Data JPA repository for credential lookups by identity ID
  - `EmailPasswordCredentialService`: Service creating credentials with password validation (8-128 chars, case-preserving), BCrypt hashing via `PasswordEncoder`, and safe result encapsulation (`EmailPasswordCredentialResult`)
  - `UserAuthenticationEligibilityService`: Centralized domain service evaluating user account status eligibility for authentication (`ACTIVE` allowed, `SUSPENDED` and `DELETED` denied)
  - `UserService`: Application service for controlled user creation with normalization, validation, and concurrency-safe duplicate email handling
  - `CreateUserRequest` & `UserResponse`: DTO boundaries preventing external ID/status/timestamp manipulation

- **Authentication & Registration (`com.joblivo.auth`):**
  - `AuthController`: Thin REST controller exposing `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
  - `RegistrationService`: Application service coordinating atomic creation of core `User`, `UserAuthIdentity` (EMAIL), and `EmailPasswordCredential`
  - `RegisterRequest`: DTO boundary requiring valid email, display name, password (8-128 chars), and confirmPassword
  - `RegisterResponse`: Safe response DTO returning account identifiers and status without exposing credentials
  - `PasswordMismatchException`: Thrown when password and confirmation mismatch, returning 400 Bad Request
  - `AuthenticationService`: Application service coordinating email/password login, verifying identity, active user status, and BCrypt credential matching
  - `LoginRequest`: DTO boundary requiring email and password with Bean Validation
  - `LoginResponse`: Safe response DTO returning user identifier, email, displayName, provider, and status without exposing credentials or tokens
  - `InvalidCredentialsException`: Safe authentication failure exception mapping to generic `401 Unauthorized` ("Invalid email or password") to prevent account enumeration

- **Security Foundation (`com.joblivo.security`):**
  - `SecurityConfig`: Stateless REST security filter chain (`SessionCreationPolicy.STATELESS`), CSRF disabled for non-cookie API, narrow public endpoints, no generated default password
  - `RestAuthenticationEntryPoint`: Translates unauthenticated requests to machine-readable `401 Unauthorized` JSON without HTML login redirects
  - `RestAccessDeniedHandler`: Translates access denials to machine-readable `403 Forbidden` JSON
  - `PasswordEncoder`: Production-ready `BCryptPasswordEncoder` bean for future authentication mechanisms

- **AI Gateway & Provider Integration (`com.joblivo.ai`):**
  - `AiGateway`: Provider-neutral internal interface for AI generation and inference. Business services depend strictly on this gateway and never on provider SDKs directly.
  - `DefaultAiGateway`: Production gateway implementation orchestrating model routing and provider dispatch with strict fail-closed guarantees.
  - `AiProvider`: Provider-neutral enum identifying supported AI backends (`BEDROCK`, `OPENAI`, `GOOGLE`).
  - `AiRequest`: Immutable, validated, provider-neutral request model (purpose, prompt, systemInstruction, requestedProvider, requestedModel, temperature, maxOutputTokens, correlationId, userId).
  - `AiResponse`: Immutable, provider-neutral response model (generatedContent, provider, model, correlationId, tokenUsage, finishReason, durationMs).
  - `AiTokenUsage`: Normalized token metrics record (promptTokens, completionTokens, totalTokens).
  - `ModelRouter` & `DefaultModelRouter`: Production routing engine implementing a deterministic, configuration-driven 5-step routing policy:
    1. **Explicit provider + explicit model:** Validated against provider enablement and model allow-list (`RoutingSource.EXPLICIT`).
    2. **Explicit provider + default model:** Resolves provider default model if model not specified (`RoutingSource.PROVIDER_DEFAULT`).
    3. **Purpose-specific configured route:** Resolves from structured `purposeRoutes` (or legacy `modelsByPurpose`) (`RoutingSource.PURPOSE`).
    4. **Global default provider + default model:** Resolves system default route (`RoutingSource.GLOBAL_DEFAULT`).
    5. **Fail closed:** Throws `AiRoutingException` if unresolvable or if an explicit route specifies a disabled provider or disallowed model.
  - `RoutingSource`: Provenance tracking enum (`EXPLICIT`, `PURPOSE`, `PROVIDER_DEFAULT`, `GLOBAL_DEFAULT`) indicating why a route was selected.
  - `ResolvedModelRoute`: Value record encapsulating resolved provider, model, purpose, and routing source.
  - `PurposeRouteProperties`: Configuration model mapping a business purpose to an optional provider and explicit model.
  - `AiProviderProperties`: Per-provider configuration with enablement toggles, default models, and configurable `allowedModels` allow-lists.
  - `AiProviderClient`: Internal SPI interface defining the contract for AI provider adapters.
  - `AiProviderRegistry`: Decoupled registry holding available provider adapters, enabling zero-code-change provider additions.
  - `BedrockAiProviderClient`: Production AWS Bedrock provider adapter utilizing the modern AWS Bedrock Runtime unified Converse API (`ConverseRequest` / `ConverseResponse`). Maps requests, normalizes responses, and isolates provider exceptions without leaking provider SDK types outside the provider layer.
  - `BedrockClientFactory` & `DefaultBedrockClientFactory`: Client lifecycle factory with lazy initialization to prevent startup failure when AWS credentials are not configured, supporting standard AWS SDK credentials provider chain (IAM role, ECS task role, EC2 instance profile, AWS profile, environment).
  - `AiAuthenticationException`, `AiRateLimitException`, `AiTimeoutException`, `AiModelException`: Provider-level error normalization subclasses of `AiProviderException`.
  - `AiConfigurationProperties`: Safe, environment-driven configuration namespaced under `joblivo.ai` with fail-closed startup validation (`@PostConstruct`), model allow-lists, structured purpose routing, and zero committed secrets.

- **Truth Engine & Factual Integrity Contract (`com.joblivo.truth`):**
  - `TruthEngine`: Core provider-neutral internal domain contract protecting factual career integrity across future AI features (resumes, job applications, LinkedIn, interview prep). Ensures AI never hallucinates or fabricates career claims.
  - `DefaultTruthEngine`: Production service coordinating conservative deterministic validation, user boundary protection, and safe metadata logging.
  - `DeterministicEvidenceValidator` & `DefaultDeterministicEvidenceValidator`: Conservative in-memory validation engine evaluating claims across all 7 Master Career Profile sections without external network or LLM calls.
  - `EvidenceStatus`: Factual integrity state enum:
    - `VERIFIED`: Directly supported by user's Master Career Profile data.
    - `DERIVED`: Logically derived from supported facts without introducing new factual claims.
    - `NEEDS_CONFIRMATION`: Partially supported or ambiguous claim requiring explicit user verification.
    - `UNSUPPORTED`: Absent or contradicted by known facts; must never be presented as truth.
  - `TruthProtectionLevel`: Operational directive enum:
    - `SAFE_TO_USE`: Safe for automatic inclusion in generated artifacts (`VERIFIED` or `DERIVED`).
    - `REQUIRES_CONFIRMATION`: Must be explicitly flagged to the user before usage (`NEEDS_CONFIRMATION`).
    - `DO_NOT_USE`: Prohibited from inclusion in generated artifacts (`UNSUPPORTED`).
  - `TruthConfidence`: Semantic certainty tier enum (`EXACT_MATCH`, `LOGICAL_DERIVATION`, `PARTIAL_MATCH`, `NONE`). Explicitly rejects fake pseudo-numeric floating-point scores.
  - `ClaimCategory`: Domain-justified claim category enum (`SKILL`, `EXPERIENCE`, `JOB_TITLE`, `COMPANY`, `PROJECT`, `ACHIEVEMENT`, `CERTIFICATION`, `EDUCATION`, `RESPONSIBILITY`, `METRIC`, `LOCATION`, `OTHER`).
  - `CareerClaim`: Immutable value record encapsulating the claim text, category, asserted user ID, correlation ID, and structured attributes.
  - `EvidenceReference`: Value record pointing directly to supporting profile entities (`sourceType`, `referenceId`, `summary`) without duplicating data.
  - `EvidenceSourceType`: Enumeration of factual profile anchors (`CAREER_PROFILE`, `WORK_EXPERIENCE`, `SKILL`, `PROJECT`, `EDUCATION`, `CERTIFICATION`, `ACHIEVEMENT`).
  - `TruthEvaluation`: Immutable evaluation decision record containing claim, status, protection level, confidence, reason, confirmation requirements, and evidence references.
  - `TextIntegrityAssessment`: Assessment record evaluating AI-transformed text against original sources and profile facts, preventing introduced metrics or technologies.
  - `CareerFactContext`: Authoritative factual context encapsulating user ID and the immutable `MasterCareerProfileResponse` read model with rich query helpers.
  - `TruthEngineException`, `InvalidClaimException`, `InvalidCareerContextException`, `CrossUserContextException`: Dedicated unchecked domain exception hierarchy ensuring strict user tenant isolation and argument validation.

- **Job Discovery, Ingestion & Normalization (`com.joblivo.job`):**
  - `Job`: Master JPA Entity mapped to shared catalog table `jobs`. Captures normalized job postings across external sources without being bound to any specific user. Enforces immutable identity (`source`, `externalJobId`, `discoveredAt`).
  - `JobSource`: Supported external source enumeration (`LINKEDIN`, `NAUKRI`, `FOUNDIT`, `CUTSHORT`, `INSTAHYRE`, `COMPANY_CAREERS`, `ATS`, `OTHER`).
  - `JobWorkMode`: Work arrangement model (`REMOTE`, `HYBRID`, `ONSITE`, `UNKNOWN`).
  - `JobEmploymentType`: Employment categorization (`FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP`, `TEMPORARY`, `FREELANCE`, `OTHER`, `UNKNOWN`).
  - `JobApplicationMethod`: Application routing categorization (`INTERNAL_PORTAL`, `EXTERNAL_COMPANY_SITE`, `ATS`, `SOURCE_PORTAL`, `EMAIL`, `OTHER`, `UNKNOWN`).
  - `SalaryPeriod`: Compensation frequency enumeration (`YEAR`, `MONTH`, `HOUR`, `OTHER`).
  - `JobIngestionCandidate`: Internal immutable ingestion data model representing raw or candidate job postings received from source adapters.
  - `JobCandidate`: Source-neutral immutable data carrier for interoperability.
  - `NormalizedJobCandidate`: Clean, validated, and normalized immutable carrier representing job data ready for persistence.
  - `JobSourceIdentity`: Canonical immutable source identity value object representing a job's origin and external identifier:
    - Composite pair: `(JobSource source, String externalJobId)`.
    - Strictly decoupled from semantic, descriptive, or mutable attributes (title, company, description, recruiter, location, salary, or URLs).
    - Deterministic normalization: trims surrounding whitespace, preserves case, preserves meaningful internal characters and punctuation (hyphens, slashes, query strings), rejects null or blank values strictly.
    - Zero ID fabrication: absent external job IDs are never guessed or fabricated (no random UUIDs, no hashes derived from title/company/URL); candidates without valid IDs fail ingestion safely.
    - Deterministic `equals`, `hashCode`, and safe factory `of(source, externalJobId)`.
  - `JobSourceProvenance`: Canonical immutable source origin and provenance representation for discovered jobs:
    - Retains factual coordinates: where the job originated (`source`), which external source identified it (`sourceIdentity`), which external job ID belongs to that source (`externalJobId`), how the job can be reached (`jobUrl`, `companyUrl`, `applicationMethod`), and when Joblivo discovered and last observed it (`discoveredAt`, `lastSeenAt`, `postedAt`, `expiresAt`).
    - Enforces source identity immutability and deterministic normalization.
    - Strict security boundaries: zero credentials, secrets, tokens, cookies, or crawler internals; zero URL fetching, DNS resolution, or redirect following (zero SSRF).
    - Accessible via `Job.getSourceProvenance()` and `JobResponse.sourceProvenance()`.
  - `JobSourceMetadata`: Canonical non-sensitive descriptive source metadata definition:
    - Encapsulates `JobSource source`, stable `code`, human-readable `displayName`, and runtime `enabled` status.
    - Authoritatively resolved at runtime via `JobSourceRegistry.getSourceMetadata(JobSource)` and `JobSourceRegistry.getAllSourceMetadata()`.
    - Strictly non-sensitive: contains zero credentials, OAuth tokens, passwords, or adapter internals.
  - `JobUrlNormalizer`: Canonical deterministic URL normalizer and validator:
    - Pure in-memory normalization: trims surrounding whitespace, requires `http` or `https` scheme strictly, requires valid non-blank host.
    - Unsupported schemes (`ftp://`, `javascript:`, `file://`, `data:`, etc.) are rejected with `JobValidationException`.
    - Preserves meaningful path components, query parameters, and fragments verbatim without destructive stripping.
    - Strictly zero external network calls: does NOT follow redirects, resolve DNS, fetch pages, or scrape URLs. Eliminates SSRF risks completely.
  - `JobNormalizer` & `DefaultJobNormalizer`: Dedicated pure in-memory domain service responsible for authoritative, canonical deterministic normalization:
    - **Canonical Company Normalization (`normalizeCompanyName`):** Applies Unicode NFC normalization, trims leading/trailing whitespace, collapses internal whitespace sequences (including Unicode non-breaking spaces) to a single space, preserves meaningful internal punctuation, case, and legal suffixes without external lookups, subsidiary merging, or company fabrication. Rejects null/blank values with `JobValidationException`.
    - **Canonical Job Title Normalization (`normalizeTitle`):** Applies Unicode NFC normalization, trims leading/trailing whitespace, collapses internal whitespace sequences, preserves original casing, punctuation (parentheses, hyphens, slashes, plus signs), technology terms (e.g. `AWS`, `Kubernetes`, `C++`, `.NET`), seniority levels (e.g. `Senior`, `Lead`, `Staff`, `Principal`, `Manager`, `Architect`, `Engineer`), and domain abbreviations (e.g. `SRE`, `DevOps`, `AI`, `ML`). Strictly avoids synonym substitution, semantic classification, fuzzy matching, or AI/vector embeddings. Rejects null/blank values with `JobValidationException`.
    - **Canonical Location Normalization (`normalizeLocation`):** Applies Unicode NFC normalization, trims leading/trailing whitespace, collapses internal whitespace, preserves meaningful commas, hyphens, and remote/hybrid wording (e.g. `Bengaluru, Karnataka`, `Remote - India`). Returns `null` for null or blank inputs without geocoding, external maps calls, coordinate inference, or fabricating defaults like "Unknown" or "N/A".
    - **Identity Normalization:** Delegated directly to `JobSourceIdentity`.
    - **URL Normalization & Validation:** Delegated directly to `JobUrlNormalizer`.
    - **Description Structure Preservation:** Normalizes Unicode NFC and trims without aggressive lowercasing or punctuation stripping.
    - **Controlled Enumeration Mapping:** Deterministic work mode, employment type, salary period, and application method mapping from raw strings or explicit enums.
    - **Strict Validation:** Enforces non-negative salary and experience ranges (`min <= max`), URL syntax (HTTP/HTTPS with valid host), and chronological timestamps (`postedAt <= expiresAt`, `discoveredAt <= lastSeenAt`).
    - **Strict Boundaries:** Zero external network dependencies, zero scraping, zero geocoding, zero AI calls, zero semantic/fuzzy matching.
  - `JobIngestionValidator`: Canonical Spring-managed in-memory data integrity validator for Job Discovery ingestion candidates:
    - **Pipeline Boundary:** Executes strictly after normalization and prior to database persistence (`source adapter -> candidate -> normalization -> validation -> persistence -> ingestion result`).
    - **Source Identity Integrity:** Requires non-null `JobSource` and non-blank `externalJobId` bounded to 255 characters (`INVALID_SOURCE`, `INVALID_EXTERNAL_JOB_ID`, `VALUE_TOO_LONG`). Enforces zero ID fabrication (IDs are never generated, guessed, or derived from title, company, or URLs).
    - **Required Content Integrity:** Requires non-blank `title` (max 255) and `companyName` (max 255) (`MISSING_TITLE`, `MISSING_COMPANY`, `VALUE_TOO_LONG`). Rejects null, blank, or placeholder substitutions (no "Unknown", "N/A", "Not specified").
    - **Text Length Safety:** Enforces strict upper bounds matching the PostgreSQL `jobs` table schema (`title` <= 255, `companyName` <= 255, `externalJobId` <= 255, `recruiterName` <= 255, `location` <= 255, `salaryCurrency` <= 10, `jobUrl` <= 1000, `companyUrl` <= 1000) without arbitrary silent truncation. Leaves `description` unbounded to respect the database `TEXT` type.
    - **URL Integrity:** When `jobUrl` or `companyUrl` is present, requires valid HTTP or HTTPS scheme and non-empty host without external DNS, HTTP, or scraping calls (zero SSRF). Permits missing `jobUrl` when optional in the database schema.
    - **Salary & Experience Integrity:** Enforces non-negative values and chronological bounds (`min <= max`) for salary and experience (`INVALID_SALARY_RANGE`, `INVALID_EXPERIENCE_RANGE`). Strictly forbids artificial currency conversions or estimated salary periods.
    - **Date Integrity:** Enforces timeline invariants (`postedAt <= expiresAt`, `discoveredAt <= lastSeenAt`) without inventing timestamps (`INVALID_DATE_RANGE`).
    - **Structured Diagnostics:** Produces immutable `JobValidationResult` records carrying granular `JobValidationFailure` items and maps cleanly to low-cardinality `IngestionErrorCategory.VALIDATION` metrics.
  - `JobValidationResult` & `JobValidationFailure`: Safe, immutable value records encapsulating candidate validation outcomes, primary failure categories, and sanitized diagnostics without leaking database internals or full job descriptions.
  - `JobFetchCriteria`: Immutable, source-neutral query parameter record (`query`, `location`, `limit`) bounded between 1 and 1000 candidates without user credentials or browser sessions.
  - `JobSourceExecutionContext`: Immutable internal execution context passed from the orchestrator to source adapters:
    - **Controlled Parameters:** Exposes strictly correlation `runId`, strongly-typed `source`, and effective `candidateLimit` (> 0).
    - **Security & Decoupling:** Strictly excludes all credentials, secrets, tokens, passwords, session cookies, and HTTP headers. Strictly excludes all user-specific data (user IDs, career profile IDs, user preferences, resume data) and framework/persistence internals.
    - **Invariants & Validation:** Validated before adapter execution; orchestrator validates source consistency (`adapter.source() == executionContext.source()`), rejecting mismatches safely as `SOURCE_MISMATCH` errors without executing the adapter or substituting sources.
  - `JobSourceAdapter`: Internal Service Provider Interface (SPI) contract for job source collection adapters:
    - **Source Identity:** Exposes exactly one deterministic `JobSource` identity via `source()` (and backward-compatible `getSource()`).
    - **Candidate Retrieval:** Canonical retrieval method `fetchIngestionCandidates(JobSourceExecutionContext)` (with `fetchJobs(JobSourceExecutionContext)` alias) returning a non-null `List<JobIngestionCandidate>` in deterministic source order.
    - **Responsibility Boundaries:**
      - *INGESTION ORCHESTRATOR:* Creates controlled `JobSourceExecutionContext`, enforces candidate limits authoritatively, provides failure isolation, and records metrics.
      - *SOURCE ADAPTER:* Source-specific retrieval and candidate mapping only, guided by context `candidateLimit` hint without trusting adapter enforcement.
      - *NORMALIZER:* Pure deterministic in-memory normalization, trimming, and validation.
      - *VALIDATOR:* Pure deterministic in-memory content integrity validation and database limit safety.
      - *PERSISTENCE SERVICE:* Database persistence, transactional idempotency, and data loss protection.
    - **Security Boundaries:** Never logs or persists credentials, passwords, or tokens; never bypasses CAPTCHA, MFA, or rate limits; never scrapes prohibited platforms or automates unauthorized browser actions.
  - `JobSourceAdapterException`: Unchecked failure exception communicating source, categorized `IngestionErrorCategory`, and sanitized message without leaking credentials or secrets.
  - `JobRepository`: Spring Data JPA repository supporting composite key lookups (`findBySourceAndExternalJobId`), existence queries, and dynamic multi-attribute search via `JpaSpecificationExecutor<Job>`.
  - `JobIngestionService`: Idempotent internal service coordinating candidate normalization via `JobNormalizer`, validation via `JobIngestionValidator`, and transactional persistence. Implements **Data Loss Protection** by preserving existing populated attributes when source payloads provide null/unknown values, while updating `lastSeenAt` and maintaining strict cross-field validation.
  - `JobSearchService`: Transactional search service providing deterministic discovery and filtering over normalized jobs using Spring Data JPA Criteria API. Validates search bounds (pagination, ranges, dates) and projects entity results to clean DTOs.
  - `JobSpecifications`: JPA Criteria API specification builder constructing dynamic, type-safe predicates for keywords (in title, company name, location, description), locations, work modes, employment types, sources, experience range overlap, salary range overlap, posted date bounds, and database-level deterministic relevance ordering before pagination.
  - `JobSearchRequest`: Canonical, immutable request and query-parameter validation model for the authenticated `GET /api/v1/jobs` discovery endpoint:
    - **Text Normalization & Boundary Protection:** Trims surrounding whitespace, treats blank/whitespace-only inputs as `null`, and enforces a hard maximum length of 200 characters on `keyword` and `location`. Plain text inputs (including SQL-like syntax or quotes) are strictly bound as parameterized query criteria and cannot alter database query structure.
    - **Bounded Pagination:** Enforces non-negative page index (`page >= 0`, default 0) and strictly bounded page size (`1 <= size <= 100`, default 20). Rejects non-positive, negative, or oversized (>100) page sizes fail-fast with clean HTTP 400 validation errors without silent truncation.
    - **Range & Chronological Validation:** Non-negative experience (`min >= 0, max >= 0, min <= max`) and salary (`min >= 0, max >= 0, min <= max`), chronological dates (`postedAfter <= postedBefore`) in UTC/Instant. Supports backward-compatible parameter aliases (`minimumExperience`/`minExperienceYears`, `minimumSalary`/`minSalary`).
    - **Strict Sort Allow-Listing:** Strictly enforces server-side allowlist (`newest`, `oldest`, `company`, `title`, `relevance`, `freshness`, default `newest`) with deterministic secondary sorting on `id`. Rejects arbitrary columns, SQL injection, or unrecognized sort keys.
    - **Clean Enum Handling:** Preserves strongly-typed enum types (`workMode`, `employmentType`, `source`) with clean HTTP 400 validation error responses via `GlobalExceptionHandler` without leaking stack traces or internal exception details.
    - **Request Boundary Validation:** Validation executes before repository interaction; invalid requests fail immediately without issuing database queries.
  - `JobSearchCriteria`: Immutable filter criteria record with safe builder and validation constraints.
  - `JobSearchSort`: Strict allow-list enumeration (`newest`, `oldest`, `company`, `title`, `relevance`, `freshness`) guaranteeing deterministic tie-breaking on `id` and fallback behavior.
  - `JobSearchRelevanceOrder`: Canonical deterministic relevance ordering component:
    - **Relevance Scope:** Strict search-result relevance to user query text (not candidate-job matching, ATS scoring, interview prediction, or AI ranking). Exposes zero numeric scores in API responses.
    - **Deterministic Precedence Signals:** (1) Exact normalized title match, (2) Title starts with normalized keyword, (3) Title contains normalized keyword, (4) Exact normalized company match, (5) Company contains normalized keyword, (6) Location contains normalized keyword, (7) Description contains normalized keyword, (8) Non-matching fallback.
    - **Stable Canonical Tie-Breakers:** (1) Relevance precedence, (2) `postedAt DESC` with null timestamps placed consistently last, (3) `companyName ASC`, (4) `title ASC`, (5) `id ASC`.
    - **No-Keyword Fallback:** When `sort=relevance` is requested without a keyword (or blank keyword), deterministically falls back to `newest` ordering with existing tie-breakers.
    - **Pagination Safety:** Ordering is executed at the database level via JPA Criteria `CASE WHEN` expressions prior to `Pageable` pagination, guaranteeing that the most relevant results are ranked across the full query result set.
  - `JobSearchFreshnessOrder`: Canonical deterministic freshness ordering component (`sort=freshness`):
    - **Ordering Scope:** Search ordering only — prioritizes fresh discovery opportunities without candidate matching, application priority, interview prediction, authenticity scoring, or AI score. Preserves stored job records without mutation or deletion.
    - **Deterministic Freshness Status Precedence:** (1) `ACTIVE`, (2) `UNKNOWN`, (3) `STALE`, (4) `EXPIRED`.
    - **Deterministic Tie-Breakers:**
      - For `ACTIVE`, `UNKNOWN`, and `STALE`: `postedAt DESC` when available, null timestamps placed consistently last, followed by `companyName ASC`, `title ASC`, `id ASC`.
      - For `EXPIRED`: `expiresAt DESC` when available, null timestamps placed consistently last, followed by `companyName ASC`, `title ASC`, `id ASC`.
    - **Canonical Reusability:** Evaluates job freshness exclusively through the canonical `JobFreshnessEvaluator` / `JobFreshnessPolicy` with injected `Clock` and configured stale threshold (`joblivo.job-discovery.freshness.stale-after`).
    - **Database-Side Query Execution:** Configures equivalent JPA Criteria `CASE WHEN` expressions before pagination, preventing loading the full job table into memory. Works seamlessly with all search filters.
  - `JobSearchResponse`: Paginated response container carrying job result lists, page number, page size, total elements, total pages, and next/previous flags.
  - `JobFreshnessStatus`: Strongly-typed lifecycle and data freshness status enum:
    - `ACTIVE`: The job is unexpired and available timestamps establish that the record is fresh (recent `lastSeenAt` within stale threshold, or valid unexpired `postedAt` when `lastSeenAt` is absent). *Important distinction: This is a deterministic system-level interpretation of available timestamp data and must NOT be presented as a guarantee that the employer is still actively hiring or accepting applications.*
    - `EXPIRED`: The job's `expiresAt` is present and in the past (`now >= expiresAt`). Takes precedence over staleness when both apply.
    - `STALE`: The job is not expired, `lastSeenAt` is present, and `lastSeenAt` is older than the configured stale threshold.
    - `UNKNOWN`: Timestamp data is insufficient or contradictory (e.g., `expiresAt < postedAt`, `lastSeenAt < discoveredAt`, or timestamps set in the future relative to `now`).
  - `JobFreshnessEvaluator`: Canonical, deterministic, and side-effect free evaluator of job timestamp data. Uses `java.time.Clock` (UTC in production, injectable fixed `Clock` in tests) and never calls `Instant.now()` directly. Modifies no database state, calls no external services, triggers no ingestion, and publishes no events.
  - `JobFreshnessPolicy`: Canonical, source-neutral policy component interpreting `JobFreshnessStatus` for read/query behavior (`isCurrentlyFresh`, `isPotentiallyExpired`, `isStale`, `isUnknown`, `shouldRemainDiscoverable`, `shouldRetainRecord`). Guarantees that stale, expired, and unknown records are never deleted, archived, or silently excluded from search queries, while strictly preserving read-only data integrity.
  - `JobFreshnessProperties`: Typed Spring Boot configuration properties (`joblivo.job-discovery.freshness.stale-after`, defaulting to `7d` / 7 days, with fallback support for `joblivo.jobs.freshness.stale-after`). Enforces that stale threshold duration is strictly positive.
  - `JobResponse`: Canonical immutable read model projection exposing normalized catalog job attributes and derived `freshnessStatus` (`ACTIVE`, `EXPIRED`, `STALE`, `UNKNOWN`). Strictly decouples API consumers from persistence entities, Hibernate proxies, and database-level metadata.
  - `JobResponseMapper`: Canonical Spring-managed mapping boundary transforming persistence entities (`Job`) into the immutable read model (`JobResponse`). Enforces entity decoupling, strictly preserves nullable fields as `null` without fabricated defaults (e.g. no "Unknown Company", "₹0", "Remote"), preserves source identity unmodified, delegates freshness evaluation exclusively to `JobFreshnessEvaluator`, and ensures side-effect-free, read-only data integrity.
  - `JobController`: REST controller exposing protected discovery endpoints (`GET /api/v1/jobs` and `GET /api/v1/jobs/{id}`) requiring an authenticated principal while serving shared, source-neutral catalog data with evaluated `freshnessStatus`.
  - `JobValidationException`, `JobNotFoundException`, `JobException`: Domain exception hierarchy enforcing strict structural, date chronology, and range validity without external network dependencies.
  - `JobConfigurationException`: Domain exception thrown when job discovery configuration is invalid, specifies unsupported sources, duplicates source keys, or violates safety bounds (e.g. non-positive or excessive candidate limits).
  - `JobSourceProperties`: Strongly-typed source configuration DTO specifying `enabled` state (default `false`) and `maxCandidates` per ingestion execution (default `100`, upper bound `1000`).
  - `JobDiscoveryProperties`: Safe environment-driven discovery configuration namespaced under `joblivo.job-discovery` with master enablement toggle, per-source configurations (`sources`), legacy shorthand fallback (`enabledSources`), and startup validation (`@PostConstruct`).
  - `JobSourceRegistry`: Central Spring-managed source registry discovering all `JobSourceAdapter` components, strictly rejecting duplicate source adapters at startup with `DuplicateJobSourceAdapterException`, exposing deterministic lookups, and serving as the authoritative runtime registry for canonical source metadata (`getSourceMetadata`, `getAllSourceMetadata`).
  - `JobSourceImplementationStatus`: Strict enumeration of internal adapter implementation states (`IMPLEMENTED`, `NOT_IMPLEMENTED`).
  - `JobSourceCoverageStatus`: Canonical high-level coverage states:
    - `SUPPORTED`: Source has an active registered adapter in `JobSourceRegistry` AND is configured enabled (`IMPLEMENTED + ENABLED`). Execution permitted.
    - `CONFIGURED_BUT_DISABLED`: Source has an active registered adapter in `JobSourceRegistry`, but is disabled in configuration (`IMPLEMENTED + DISABLED`). Execution not permitted.
    - `NOT_IMPLEMENTED`: Source has no registered adapter in `JobSourceRegistry` (`NOT_IMPLEMENTED`). Execution is strictly prohibited regardless of configuration.
    - `UNKNOWN`: Null or unrecognized source identity. Execution not permitted.
  - `JobSourceCoverage`: Canonical immutable domain record describing source coverage (`source`, `implementationStatus`, `configuredEnabled`, `coverageStatus`, `executionPermitted`, `notPermittedReason`) with rich query helpers (`isSupported()`, `isImplemented()`, `isConfiguredEnabled()`, `isExecutionPermitted()`).
  - `JobSourceResolution`: Immutable outcome record representing the canonical source resolution path, identifying registered adapter (if any), coverage evaluation, execution eligibility, and sanitized failure categorization (`INVALID_SOURCE`, `ADAPTER_NOT_FOUND`, `SOURCE_DISABLED`, `NOT_IMPLEMENTED`).
  - `JobSourceExecutionVerification`: Immutable record capturing the result of the 7-condition execution policy verification (`valid`, `rejectionCategory`, `rejectionReason`, `coverage`).
  - `JobSourceControlPolicy`: Control-plane policy and resolution component decoupling adapter discovery from source configuration:
    - Answers whether a source is enabled, resolves candidate bounds, and evaluates ingestion eligibility.
    - Provides authoritative source coverage inspection via `getSourceCoverage(JobSource)` and `getAllSourceCoverage()`.
    - Canonical resolution path via `resolveSource(JobSource)` determining adapter existence, implementation status, and execution eligibility.
    - Enforces the 7-condition execution verification policy via `verifyExecution(JobSource, JobSourceExecutionContext)` prior to adapter invocation:
      1. Source is known (`source != null`)
      2. Source has a valid adapter registered (`adapter != null`)
      3. Adapter source matches requested source (`adapter.source() == source`)
      4. Source is implemented (`sourceRegistry.isSourceRegistered(source)`)
      5. Source is enabled (`isSourceEnabled(source)`: master enabled + source enabled)
      6. Execution context is valid (`context != null && context.source() == source && !context.runId().isBlank()`)
      7. Candidate limit is valid (`candidateLimit >= 1 && candidateLimit <= MAX_CANDIDATES_UPPER_BOUND`)
    - **Fail-Closed Safety Invariant:** An unimplemented source is **never executable**, even if configuration requests `enabled=true`. Ingestion orchestrator safely records non-fatal failure diagnostics (`CandidateFailureSummary`) and skips execution without fabricating candidates or making external network calls.
  - `IngestionRunStatus`: Controlled operational lifecycle states for Job Discovery ingestion runs:
    - `STARTED`: Ingestion run initiated and execution context created.
    - `RUNNING`: Ingestion run actively fetching, normalizing, or persisting candidates.
    - `COMPLETED`: Ingestion run finished successfully with zero errors (including runs with 0 candidates or 0 enabled sources).
    - `COMPLETED_WITH_ERRORS`: Ingestion run completed safely, but encountered candidate-level validation/constraint errors or non-fatal source-level adapter errors (`ADAPTER_FETCH_ERROR`, `ADAPTER_NOT_FOUND`, `SOURCE_DISABLED`). One source failure never erases another source's successful results.
    - `FAILED`: Ingestion run encountered a fatal orchestration or infrastructure error preventing meaningful completion.
    - Deterministic transition rules strictly prevent backward movement (`isTerminal()`, `canTransitionTo()`, `validateTransition()`).
  - `IngestionRunContext`: Immutable execution-scoped context tracking `runId`, `status`, `startedAt`, and unmodifiable `sources`. Pure value object without static or shared mutable state, ensuring multi-threaded execution safety.
  - `JobBatchDeduplicator` & `JobBatchIdentity`: Production deterministic batch-level deduplication component hardening the ingestion pipeline against duplicate candidates appearing within a single adapter response.
    - **Canonical Identity:** Strictly uses `SOURCE + EXTERNAL_JOB_ID` (with safe deterministic string trimming). Explicitly avoids mutable or non-unique fields (title, company, description, URL, location, salary).
    - **Batch Duplicate Protection:** Fast, execution-local in-memory filtering (`JobBatchDeduplicator`) discarding repeated occurrences within a single batch, preserving first-seen candidates in original sequence, maintaining candidate isolation, incrementing `duplicateCount`, and recording low-cardinality `joblivo.job.ingestion.duplicates.skipped` metrics. Unrelated candidates with missing external IDs are never collapsed together.
    - **Database Idempotency:** Final persistence-level duplicate protection enforced authoritatively by `CONSTRAINT uq_jobs_source_external_id UNIQUE (source, external_job_id)` in PostgreSQL, ensuring cross-run duplicate safety and transactional idempotency.
    - **Semantic Duplicate Intelligence:** Intentionally NOT implemented here; semantic similarity, repost detection, cross-source matching, and AI/vector deduplication are strictly excluded from ingestion batch deduplication.
  - `JobDuplicateClassification`: Strict enumeration of deterministic duplicate evidence outcomes (`EXACT_SOURCE_DUPLICATE`, `EXACT_URL_DUPLICATE`, `EXACT_CONTENT_DUPLICATE`, `NOT_DUPLICATE`).
  - `JobDuplicateResult`: Immutable outcome record carrying the resolved classification, a safe diagnostic explanation, and the matching evidence key without exposing arbitrary numeric scores.
  - `JobContentFingerprint`: Canonical SHA-256 content fingerprinting mechanism:
    - Included fields: `title`, `companyName`, `location`, and `description`.
    - Excluded fields: `source`, `externalJobId`, `jobUrl`, `companyUrl`, `recruiterName`, `salary`, `postedAt`, `expiresAt`, `discoveredAt`, `lastSeenAt`, `id`, `applicationMethod`.
    - Field canonicalization: Unicode NFC normalization, trimming, internal whitespace collapsing, lowercase under `Locale.ROOT`, and `<NULL>` representation for absent optional attributes.
    - Deterministic serialization with unambiguous line prefixes (`title:`, `company:`, `location:`, `description:`) prevents boundary concatenation collisions.
  - `JobDuplicateDetector`: Production domain component providing side-effect-free duplicate detection across stored jobs and incoming candidates based on a strict 4-tier precedence:
    1. **Exact Source Identity (Priority 1):** Matches existing stored job via `findBySourceAndExternalJobId(source, externalJobId)`. Stored job is updated idempotently with refreshed attributes and `lastSeenAt`.
    2. **Exact Canonical Job URL (Priority 2):** Matches existing stored job via `findFirstByJobUrl(canonicalUrl)`. Identifies multi-source syndication or identical postings across discovery platforms.
    3. **Exact Content Fingerprint (Priority 3):** Matches existing stored job via canonical SHA-256 fingerprint (`title`, `companyName`, `location`, `description`). Targeted lookup `findByTitleIgnoreCaseAndCompanyNameIgnoreCase` narrows candidates safely before in-memory SHA-256 evaluation, avoiding full-table scans without speculative schema migrations.
    4. **Not Duplicate (Fallback):** Returned when no deterministic duplicate evidence matches; candidate proceeds to standard creation.
    - **Dual Evaluation Modes:** Supports both database-backed targeted stored-job lookups (`findStoredDuplicate(candidate)`) and pure in-memory comparisons (`findStoredDuplicate(candidate, storedJobs)` / `compare`).
    - **Source Provenance Preservation:** Cross-source duplicates (`EXACT_URL_DUPLICATE`, `EXACT_CONTENT_DUPLICATE`) are persisted as distinct job records under their respective source identity (`DETECTED_CROSS_SOURCE_DUPLICATE`), strictly preserving origin provenance and external IDs without cross-source merging, deletion, or overwrites.
    - **Zero Mutation Invariant:** Detection is strictly read-only and side-effect-free; never mutates or deletes jobs during evaluation; zero AI, zero embeddings, zero external network calls.
  - `StoredJobDuplicateMatch`: Immutable domain record pairing the deterministic `JobDuplicateResult` with the matching stored `Job` entity (if any).
  - `JobIngestionOrchestrator`: Production orchestration service coordinating adapter candidate fetching, deterministic candidate limit enforcement (`candidatesConsidered <= maxCandidates`), in-batch duplicate deduplication, candidate-level failure isolation via independent transactions, structured lifecycle transitions (`STARTED -> RUNNING -> COMPLETED / COMPLETED_WITH_ERRORS / FAILED`), duplicate metrics tracking, and safe diagnostic aggregation with correlation `runId` tracking.
  - `IngestionRunResult` & `MultiSourceIngestionRunResult`: Immutable, safe result models capturing run statistics (`candidatesReceived`, `candidatesConsidered`, `createdCount`, `updatedCount`, `skippedCount`, `failedCount`, `successfulCount`), operational lifecycle `status`, execution `duration` / `durationMs`, deterministic source ordering, and sanitized failure summaries.
  - `JobIngestionMetrics`: Production-ready operational telemetry service utilizing Micrometer to record low-cardinality counters and execution timers without disrupting core ingestion pipelines.
    - Run Metrics: `joblivo.job.ingestion.runs` (counter by `status`) and `joblivo.job.ingestion.runs.duration` (timer by `status`). Exactly one terminal status counter is recorded per execution.
    - Source Metrics: `joblivo.job.ingestion.sources` (counter by `source` and `status`) and `joblivo.job.ingestion.sources.duration` (timer by `source` and `status`).
    - Duplicate Telemetry: `joblivo.job.ingestion.duplicates.detected` (counter partitioned strictly by bounded `source` and `classification` tags).
    - Candidate Processing Counters: `candidates.received`, `candidates.considered`, `jobs.created`, `jobs.updated`, `jobs.skipped`, and `candidates.failed` (partitioned by bounded `source` tag).
    - Error Categorization: `joblivo.job.ingestion.errors` (counter by `source` and `error_category`).
    - Low-Cardinality Tag Policy: Strict rule strictly restricting tags to bounded enums (`source`, `status`, `classification`, `error_category`). Forbids high-cardinality values (`runId`, `userId`, `jobId`, `externalJobId`, `company`, `title`, URLs, recruiter names).
  - `IngestionErrorCategory`: Controlled low-cardinality failure classification enum (`VALIDATION`, `DUPLICATE`, `SOURCE_FAILURE`, `PERSISTENCE`, `CONFIGURATION`, `INFRASTRUCTURE`, `UNKNOWN`).
  - `CandidateFailureSummary`: Sanitized diagnostic record capturing failure category and truncated error messages without exposing candidate descriptions or sensitive data.
  - `CandidateIngestionAction`: Strongly-typed enum (`CREATED`, `UPDATED`, `SKIPPED`, `SKIPPED_SOURCE_DUPLICATE`, `DETECTED_CROSS_SOURCE_DUPLICATE`, `FAILED`) indicating candidate persistence outcome.
  - `DuplicateJobSourceAdapterException`: Startup exception preventing conflicting multiple adapters for the same `JobSource`.
  - `JobDiscoveryIngestionHealthIndicator`: Production-ready Spring Boot Actuator health and readiness contributor (`components.jobDiscoveryIngestion` at `/actuator/health`):
    - **Validation:** Validates that internal ingestion components (`JobDiscoveryProperties`, `JobSourceRegistry`, `JobSourceControlPolicy`, `JobIngestionOrchestrator`) are initialized, configuration is structurally valid within safety bounds, and all configured enabled sources have registered adapters in the registry.
    - **Health States:**
      - `UP` (`readiness: READY`): Subsystem is fully wired and ready for execution.
      - `UP` (`readiness: NO_SOURCES_ENABLED` / `DISABLED`): Valid configuration with zero enabled sources or master toggle off. Represents a safe operational condition (fail-closed default) and does **not** make the application unhealthy.
      - `DEGRADED` (`readiness: DEGRADED`): One or more enabled sources lack a registered adapter in `JobSourceRegistry`. The subsystem remains operational for available sources, alerting operators without taking the application down.
  - `JobAuthenticityEvaluator`: Canonical deterministic domain service evaluating authenticity and scam-risk warning signals strictly from existing fields in the canonical `Job` entity, source provenance, and freshness metadata.
    - **No Probabilistic Scoring:** Never produces arbitrary percentages (e.g. "97% authentic" or "3% scam"). Distinguishes available evidence, warning signals, and insufficient evidence without claiming certainty from weak signals.
    - **Transparent Determinism:** Every warning signal includes a strongly-typed signal type, deterministic risk level (`NONE`, `LOW`, `MEDIUM`, `HIGH`), human-readable explanation, and technical trigger reason.
    - **Reuses Existing Freshness:** Directly delegates freshness evaluation to canonical `JobFreshnessEvaluator`, never duplicating freshness or staleness logic.
    - **Decoupled from Duplicate Detection:** Duplicate or reposted jobs are never classified as fraudulent.
    - **Source Neutrality:** Treats all sources (`LINKEDIN`, `NAUKRI`, `COMPANY_CAREERS`, etc.) equally without arbitrary trust scores or reputation heuristics.
    - **Strictly Read-Only & Safe:** Pure in-memory evaluation with zero entity mutations, zero database updates, zero network/HTTP/DNS calls, and zero AI/LLM dependencies. Job descriptions are treated strictly as inert text data, completely safe from prompt injection.
  - `JobAuthenticityAssessment`: Canonical immutable read model record encapsulating `overallAssessment` (`NO_WARNING`, `INFORMATIONAL`, `CAUTION`, `HIGH_RISK_SIGNALS`, `INSUFFICIENT_EVIDENCE`), `highestRisk` (`NONE`, `LOW`, `MEDIUM`, `HIGH`), unmodifiable `signals` list, and `insufficientEvidence` indicator.
  - `JobAuthenticitySignalType`: Deterministic signal types:
    1. `MISSING_COMPANY_INFORMATION` (`LOW`): Company name is null, empty, or blank.
    2. `MISSING_JOB_URL` (`LOW`): Job application URL is null, empty, or blank.
    3. `INVALID_OR_UNSUPPORTED_APPLICATION_METHOD` (`MEDIUM`): Application method is `UNKNOWN` or requires an external destination URL which is missing.
    4. `EXPIRED_JOB` (`LOW`): Evaluated from canonical freshness evaluator when expiration timestamp has passed.
    5. `STALE_JOB` (`LOW`): Evaluated from canonical freshness evaluator when exceeding staleness threshold.
    6. `MISSING_DESCRIPTION` (`LOW`): Job description is absent or blank.
    7. `MISSING_LOCATION` (`LOW`): Job location is absent or blank.
    8. `SUSPICIOUS_SALARY_DATA` (`HIGH`): Triggered strictly when salary data is internally inconsistent (e.g. `min > max`, negative bounds, or bounds without salary period). High salary by itself is never suspicious.
    9. `INVALID_EXPERIENCE_RANGE` (`HIGH`): Triggered strictly when experience requirements are internally inconsistent (e.g. `min > max` or negative values).
    10. `SOURCE_PROVENANCE_UNAVAILABLE` (`HIGH`): Triggered when source or externalJobId is missing or blank.
    11. `MISSING_RECRUITER_INFORMATION` (`LOW`): Informational signal when recruiter name is absent; never implies fraud or suspicion.
  - `JobResponse` & `JobResponseMapper`: Integrated canonical read model exposing `authenticityAssessment` and `jobDescriptionIntelligence` across `GET /api/v1/jobs` and `GET /api/v1/jobs/{jobId}` with 100% backward-compatible constructor overloads and zero entity leakage.
  - `JobDescriptionParser`: Canonical deterministic domain service extracting structured intelligence from raw job descriptions without AI/LLM models, without mutating stored text, and without fabricating unsupported requirements.
    - **Section Extraction:** Conservative heading detection recognizing standard categories (`SUMMARY`, `RESPONSIBILITIES`, `REQUIRED`, `PREFERRED`, `EXPERIENCE`, `EDUCATION`, `CERTIFICATIONS`, `LOCATION`, `WORK_MODE`, `OTHER`). Preserves source ordering, bullet structures, and original sentences verbatim. Unrecognized structures preserved in `OTHER` with `NO_RELIABLE_SECTIONS_DETECTED` warning.
    - **Skill & Technology Extraction:** Controlled dictionary of canonical technologies aligned with `SkillCategory` and `DefaultTruthEngine`. Contextual classification routes terms to `requiredSkills` strictly when appearing in a `REQUIRED` section or line with explicit mandatory indicators; routes to `preferredSkills` when appearing in a `PREFERRED` section or line with desirable indicators; leaves ambiguous terms un-upgraded. Emits `SKILL_EXTRACTION_LIMITED` when no technologies match.
    - **Experience Extraction:** Parses explicit numerical experience bounds (`3-5 years`, `5+ years`, `minimum 4 years`). Never infers experience from seniority titles (e.g. "Senior Engineer"). Emits `EXPERIENCE_NOT_EXPLICIT` when absent.
    - **Education & Certification Extraction:** Deterministic pattern recognition for explicit degrees (`Bachelor's`, `B.Tech`, `Master's`, `MBA`, `PhD`) and certifications (`AWS Certified`, `Azure`, `PMP`, `CISSP`, `CKA`).
    - **Location & Work Mode Extraction:** Detects explicit remote/hybrid/on-site arrangements and major tech hub locations without geocoding or inferring remote eligibility from absent locations.
    - **Bounded Keywords:** Stop-word filtered, normalized keyword collection bounded to 30 terms without numeric ATS scoring.
    - **Security & Prompt-Injection Immunity:** Description text is treated strictly as data. Zero executable evaluation, zero tool calls, zero external network calls, zero AI provider dependencies.
  - `JobDescriptionIntelligence`: Canonical immutable read model record encapsulating `jobId`, `originalDescriptionPresent`, `summary`, `responsibilities`, `requiredQualifications`, `preferredQualifications`, `requiredSkills`, `preferredSkills`, `experienceRequirements`, `educationRequirements`, `certificationRequirements`, `locationRequirements`, `workModeRequirements`, `keywords`, and `warnings`.

## Environment Configuration

Configuration is managed via Spring Boot profiles and environment variables.

Copy the example environment template for local development:

```bash
cp .env.example .env.local
```

### Environment Variables

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SERVER_PORT` | HTTP Server port | `8080` |
| `SPRING_APPLICATION_NAME` | Application name identifier | `joblivo-api` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `local` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/joblivo_db` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL database username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL database password | `postgres` |
| `JOBLIVO_AI_ENABLED` | Master toggle for internal AI Gateway | `false` |
| `JOBLIVO_AI_DEFAULT_PROVIDER` | Default AI provider backend (`BEDROCK`, `OPENAI`, `GOOGLE`) | `BEDROCK` |
| `JOBLIVO_AI_DEFAULT_MODEL` | Default model identifier across providers | `anthropic.claude-3-5-sonnet-20241022-v2:0` |
| `JOBLIVO_AI_BEDROCK_ENABLED` | Toggle for AWS Bedrock provider | `false` |
| `JOBLIVO_AI_BEDROCK_DEFAULT_MODEL` | Default Bedrock model identifier | `anthropic.claude-3-5-sonnet-20241022-v2:0` |
| `JOBLIVO_AI_BEDROCK_REGION` | AWS Region for Bedrock model invocations (e.g. `us-east-1`) | *empty (resolved from AWS chain)* |
| `JOBLIVO_AI_BEDROCK_TIMEOUT_SECONDS` | Timeout in seconds for Bedrock runtime requests | `30` |
| `JOBLIVO_AI_OPENAI_ENABLED` | Toggle for OpenAI provider | `false` |
| `JOBLIVO_AI_GOOGLE_ENABLED` | Toggle for Google provider | `false` |
| `JOBLIVO_JOB_DISCOVERY_ENABLED` | Master toggle for job discovery subsystem | `true` |
| `JOBLIVO_JOB_DISCOVERY_ENABLED_SOURCES` | Comma-separated list of enabled job sources (legacy fallback, e.g. `LINKEDIN,NAUKRI`) | *empty (fail closed)* |
| `JOBLIVO_JOB_DISCOVERY_DEFAULT_MAX_CANDIDATES` | Default max candidates per ingestion run per source | `100` |
| `JOBLIVO_JOB_DISCOVERY_SOURCE_<SOURCE>_ENABLED` | Per-source enablement toggle (e.g. `LINKEDIN`, `NAUKRI`, `FOUNDIT`, `CUTSHORT`, `INSTAHYRE`, `COMPANY_CAREERS`, `ATS`, `OTHER`) | `false` |
| `JOBLIVO_JOB_DISCOVERY_SOURCE_<SOURCE>_MAX_CANDIDATES` | Per-source maximum candidates allowed per ingestion execution (1-1000) | `100` |

> **Note:** Do NOT commit `.env.local`, `.env`, or any secret-bearing files to version control.

## Endpoints

- `GET /api/v1/health` — Application health check (Public)
- `POST /api/v1/users` — Creates a new active user (Public at foundation stage)
- `POST /api/v1/auth/register` — Registers a new user with email and password credentials (Public)
- `POST /api/v1/auth/login` — Authenticates an existing user via email and password (Public)
- `GET /api/v1/career-profile` — Retrieves the complete Master Career Profile, child collections, and completeness summary for the authenticated user (Protected)
- `POST /api/v1/career-profile` — Creates a master career profile for the authenticated user (Protected)
- `PUT /api/v1/career-profile` — Updates the master career profile core details for the authenticated user (Protected)
- `POST /api/v1/career-profile/work-experiences` — Creates a new work experience record for the authenticated user (Protected)
- `GET /api/v1/career-profile/work-experiences` — Retrieves all work experiences for the authenticated user (Protected)
- `PUT /api/v1/career-profile/work-experiences/{id}` — Updates a specific work experience belonging to the authenticated user (Protected)
- `DELETE /api/v1/career-profile/work-experiences/{id}` — Deletes a specific work experience belonging to the authenticated user (Protected)
- `POST /api/v1/career-profile/skills` — Adds a skill/technology entry for the authenticated user (Protected)
- `GET /api/v1/career-profile/skills` — Retrieves all skills/technologies for the authenticated user (Protected)
- `PUT /api/v1/career-profile/skills/{id}` — Updates a specific skill belonging to the authenticated user (Protected)
- `DELETE /api/v1/career-profile/skills/{id}` — Deletes a specific skill belonging to the authenticated user (Protected)
- `POST /api/v1/career-profile/projects` — Creates a new project record for the authenticated user (Protected)
- `GET /api/v1/career-profile/projects` — Retrieves all projects for the authenticated user (Protected)
- `GET /api/v1/career-profile/projects/{id}` — Retrieves a specific project for the authenticated user (Protected)
- `PUT /api/v1/career-profile/projects/{id}` — Updates a specific project belonging to the authenticated user (Protected)
- `DELETE /api/v1/career-profile/projects/{id}` — Deletes a specific project belonging to the authenticated user (Protected)
- `POST /api/v1/career-profile/education` — Creates a new education record for the authenticated user (Protected)
- `GET /api/v1/career-profile/education` — Retrieves all education records for the authenticated user (Protected)
- `GET /api/v1/career-profile/education/{id}` — Retrieves a specific education record for the authenticated user (Protected)
- `PUT /api/v1/career-profile/education/{id}` — Updates a specific education record belonging to the authenticated user (Protected)
- `DELETE /api/v1/career-profile/education/{id}` — Deletes a specific education record belonging to the authenticated user (Protected)
- `POST /api/v1/career-profile/certifications` — Creates a new certification record for the authenticated user (Protected)
- `GET /api/v1/career-profile/certifications` — Retrieves all certifications for the authenticated user (Protected)
- `GET /api/v1/career-profile/certifications/{id}` — Retrieves a specific certification for the authenticated user (Protected)
- `PUT /api/v1/career-profile/certifications/{id}` — Updates a specific certification belonging to the authenticated user (Protected)
- `DELETE /api/v1/career-profile/certifications/{id}` — Deletes a specific certification belonging to the authenticated user (Protected)
- `POST /api/v1/career-profile/achievements` — Creates a new achievement record for the authenticated user (Protected)
- `GET /api/v1/career-profile/achievements` — Retrieves all achievements for the authenticated user (Protected)
- `GET /api/v1/career-profile/achievements/{id}` — Retrieves a specific achievement for the authenticated user (Protected)
- `PUT /api/v1/career-profile/achievements/{id}` — Updates a specific achievement belonging to the authenticated user (Protected)
- `DELETE /api/v1/career-profile/achievements/{id}` — Deletes a specific achievement belonging to the authenticated user (Protected)
- `GET /api/v1/jobs` — Searches and filters normalized jobs with deterministic criteria, pagination, and sorting, returning derived `freshnessStatus` (`ACTIVE`, `EXPIRED`, `STALE`, `UNKNOWN`) (Protected)
- `GET /api/v1/jobs/{jobId}` — Retrieves a single normalized job record by UUID, returning the canonical `JobResponse` read model with derived `freshnessStatus` (`ACTIVE`, `EXPIRED`, `STALE`, `UNKNOWN`) (Protected)
- `GET /actuator/health` — Spring Boot Actuator health status (Public)
- `GET /actuator/info` — Application metadata (Public)
- *All other endpoints require authentication (`401 Unauthorized`)*

### Error Handling

The API returns consistent, machine-readable JSON error payloads:
- `400 Bad Request` — Validation failures (with field-level error messages) and malformed requests
- `401 Unauthorized` — Unauthenticated access to protected API endpoints
- `403 Forbidden` — Access denied to authenticated users lacking necessary permissions
- `404 Not Found` — Resource not found or foreign to the authenticated user
- `409 Conflict` — Unique constraint violations (e.g., duplicate email)
- `500 Internal Server Error` — Safe generic error response preventing leakage of stack traces or database internals

## Running the Application

Using Maven:

```bash
mvn spring-boot:run
```

Running tests:

```bash
mvn test
```

Packaging:

```bash
mvn clean package
```

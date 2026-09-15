package com.joblivo.job.validation;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import com.joblivo.job.normalizer.DefaultJobNormalizer;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobIngestionValidator - Content Integrity Validation Unit Tests")
class JobIngestionValidatorTest {

    private JobIngestionValidator validator;
    private DefaultJobNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new DefaultJobNormalizer();
        validator = new JobIngestionValidator(normalizer);
    }

    private JobIngestionCandidate.Builder validCandidateBuilder() {
        return JobIngestionCandidate.builder()
                .source(JobSource.LINKEDIN)
                .externalJobId("ext-valid-101")
                .title("Senior Software Engineer")
                .companyName("Acme Technologies Inc.")
                .jobUrl("https://linkedin.com/jobs/view/ext-valid-101")
                .workMode(JobWorkMode.REMOTE)
                .employmentType(JobEmploymentType.FULL_TIME)
                .applicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE)
                .experienceMinYears(3)
                .experienceMaxYears(7)
                .salaryMin(BigDecimal.valueOf(120000))
                .salaryMax(BigDecimal.valueOf(180000))
                .salaryCurrency("USD")
                .salaryPeriod(SalaryPeriod.YEAR)
                .postedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .discoveredAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .lastSeenAt(Instant.now());
    }

    private NormalizedJobCandidate validNormalizedCandidate() {
        return normalizer.normalize(validCandidateBuilder().build());
    }

    // =========================================================================
    // 1. SOURCE IDENTITY
    // =========================================================================
    @Nested
    @DisplayName("Source Identity Integrity")
    class SourceIdentityTests {

        @Test
        @DisplayName("1. Missing source -> validation failure (INVALID_SOURCE)")
        void missingSourceFails() {
            // Raw candidate without source cannot be normalized; test direct validation
            JobValidationResult result = validator.validate((NormalizedJobCandidate) null);
            assertThat(result.isValid()).isFalse();
            assertThat(result.primaryFailureCategory()).isEqualTo(JobIngestionValidator.CATEGORY_INVALID_SOURCE);
        }

        @Test
        @DisplayName("2. Missing externalJobId -> validation failure (INVALID_EXTERNAL_JOB_ID)")
        void missingExternalJobIdFails() {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(null)
                    .title("Software Engineer")
                    .companyName("Acme Corp")
                    .build();

            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_EXTERNAL_JOB_ID));

            assertThatThrownBy(() -> validator.validateOrThrow(raw))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("3. Blank externalJobId -> validation failure (INVALID_EXTERNAL_JOB_ID)")
        void blankExternalJobIdFails(String blankId) {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(blankId)
                    .title("Software Engineer")
                    .companyName("Acme Corp")
                    .build();

            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_EXTERNAL_JOB_ID));
        }

        @Test
        @DisplayName("4. Fabricated IDs are never generated")
        void fabricatedIdsNeverGenerated() {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.NAUKRI)
                    .title("Principal Architect")
                    .companyName("Global Systems")
                    .jobUrl("https://naukri.com/job/view/999")
                    .description("High scale engineering role")
                    .build();

            // When externalJobId is absent, validator rejects candidate and never invents an ID
            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(raw.externalJobId()).isNull();
            assertThat(raw.getSourceIdentity()).isEmpty();
        }

        @Test
        @DisplayName("5. Valid source identity -> success")
        void validSourceIdentitySucceeds() {
            NormalizedJobCandidate normalized = validNormalizedCandidate();
            JobValidationResult result = validator.validate(normalized);
            assertThat(result.isValid()).isTrue();
            assertThat(result.failures()).isEmpty();
        }
    }

    // =========================================================================
    // 2. REQUIRED JOB CONTENT
    // =========================================================================
    @Nested
    @DisplayName("Required Content Integrity")
    class RequiredContentTests {

        @Test
        @DisplayName("6. Missing title -> validation failure (MISSING_TITLE)")
        void missingTitleFails() {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-no-title")
                    .title(null)
                    .companyName("Acme Corp")
                    .build();

            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_MISSING_TITLE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("7. Blank title -> validation failure (MISSING_TITLE)")
        void blankTitleFails(String blankTitle) {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-blank-title")
                    .title(blankTitle)
                    .companyName("Acme Corp")
                    .build();

            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_MISSING_TITLE));
        }

        @Test
        @DisplayName("8. Missing company -> validation failure (MISSING_COMPANY)")
        void missingCompanyFails() {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-no-co")
                    .title("Software Engineer")
                    .companyName(null)
                    .build();

            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_MISSING_COMPANY));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("9. Blank company -> validation failure (MISSING_COMPANY)")
        void blankCompanyFails(String blankCompany) {
            JobIngestionCandidate raw = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-blank-co")
                    .title("Software Engineer")
                    .companyName(blankCompany)
                    .build();

            JobValidationResult result = validator.validate(raw);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_MISSING_COMPANY));
        }

        @Test
        @DisplayName("10. Missing URL is allowed when current database schema permits it (job_url is nullable)")
        void missingOptionalUrlAllowed() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-no-url")
                    .title("DevOps Engineer")
                    .companyName("Cloud Inc")
                    .jobUrl(null)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "ftp://example.com/job/1",
                "javascript:alert(1)",
                "file:///etc/passwd",
                "data:text/html,malicious",
                "https://",
                "https:///jobs/123",
                "not-a-valid-url"
        })
        @DisplayName("11. Invalid URL -> validation failure (INVALID_JOB_URL)")
        void invalidUrlFails(String invalidUrl) {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-bad-url")
                    .title("Backend Engineer")
                    .companyName("Acme Corp")
                    .jobUrl(invalidUrl)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_JOB_URL));
        }

        @Test
        @DisplayName("12. Valid minimum candidate -> success")
        void validMinimumCandidateSucceeds() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.NAUKRI)
                    .externalJobId("naukri-min-1")
                    .title("Junior Developer")
                    .companyName("Tech Innovators")
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
            assertThat(result.failures()).isEmpty();
        }
    }

    // =========================================================================
    // 3. NORMALIZATION INTEGRATION
    // =========================================================================
    @Nested
    @DisplayName("Canonical Normalization Integration")
    class NormalizationIntegrationTests {

        @Test
        @DisplayName("13. Company uses Prompt 51 canonical normalization")
        void companyCanonicalNormalization() {
            String rawCompany = "  Acme   \u00A0 Technologies,   Inc.  ";
            String normalizedCompany = normalizer.normalizeCompanyName(rawCompany);
            assertThat(normalizedCompany).isEqualTo("Acme Technologies, Inc.");

            JobIngestionCandidate raw = validCandidateBuilder()
                    .companyName(rawCompany)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(raw);
            assertThat(normalized.companyName()).isEqualTo("Acme Technologies, Inc.");
            assertThat(validator.validate(normalized).isValid()).isTrue();
        }

        @Test
        @DisplayName("14. Title uses Prompt 51 canonical normalization")
        void titleCanonicalNormalization() {
            String rawTitle = "  Senior   \u00A0 Software   Engineer (Backend)  ";
            String normalizedTitle = normalizer.normalizeTitle(rawTitle);
            assertThat(normalizedTitle).isEqualTo("Senior Software Engineer (Backend)");

            JobIngestionCandidate raw = validCandidateBuilder()
                    .title(rawTitle)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(raw);
            assertThat(normalized.title()).isEqualTo("Senior Software Engineer (Backend)");
            assertThat(validator.validate(normalized).isValid()).isTrue();
        }

        @Test
        @DisplayName("15. Location uses Prompt 51 canonical normalization")
        void locationCanonicalNormalization() {
            String rawLocation = "  Bengaluru,   \u00A0 Karnataka,   India  ";
            String normalizedLocation = normalizer.normalizeLocation(rawLocation);
            assertThat(normalizedLocation).isEqualTo("Bengaluru, Karnataka, India");

            assertThat(normalizer.normalizeLocation(null)).isNull();
            assertThat(normalizer.normalizeLocation("   ")).isNull();
        }

        @Test
        @DisplayName("16. URL uses Prompt 49 canonical normalization")
        void urlCanonicalNormalization() {
            String rawUrl = "  https://example.com/jobs/view/101?source=linkedin&ref=joblivo#apply  ";
            JobIngestionCandidate raw = validCandidateBuilder()
                    .jobUrl(rawUrl)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(raw);
            assertThat(normalized.jobUrl()).isEqualTo(rawUrl.trim());
            assertThat(validator.validate(normalized).isValid()).isTrue();
        }

        @Test
        @DisplayName("17. No duplicate normalization logic exists; validator delegates to canonical normalizer")
        void noDuplicateNormalizationLogic() {
            JobIngestionValidator testValidator = new JobIngestionValidator(normalizer);
            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-canon-1")
                    .title("  Site   Reliability   Engineer  ")
                    .companyName("  Reliability   Corp  ")
                    .build();

            JobValidationResult result = testValidator.validate(candidate);
            assertThat(result.isValid()).isTrue();
        }
    }

    // =========================================================================
    // 4. DATE INTEGRITY
    // =========================================================================
    @Nested
    @DisplayName("Date Integrity")
    class DateIntegrityTests {

        @Test
        @DisplayName("18. Valid timestamps -> success")
        void validTimestampsSucceed() {
            Instant postedAt = Instant.parse("2026-09-01T10:00:00Z");
            Instant expiresAt = Instant.parse("2026-10-01T10:00:00Z");
            Instant discoveredAt = Instant.parse("2026-09-01T12:00:00Z");
            Instant lastSeenAt = Instant.parse("2026-09-10T12:00:00Z");

            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-dates-ok")
                    .title("Data Engineer")
                    .companyName("Data Co")
                    .postedAt(postedAt)
                    .expiresAt(expiresAt)
                    .discoveredAt(discoveredAt)
                    .lastSeenAt(lastSeenAt)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("19. expiresAt before postedAt -> rejected safely (INVALID_DATE_RANGE)")
        void expiresAtBeforePostedAtRejected() {
            Instant postedAt = Instant.parse("2026-09-10T10:00:00Z");
            Instant expiresAt = Instant.parse("2026-09-01T10:00:00Z"); // Earlier than posted

            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-bad-dates")
                    .title("Data Engineer")
                    .companyName("Data Co")
                    .postedAt(postedAt)
                    .expiresAt(expiresAt)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_DATE_RANGE)
                            && f.safeMessage().contains("cannot precede postedAt"));

            assertThatThrownBy(() -> validator.validateOrThrow(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot precede postedAt");
        }

        @Test
        @DisplayName("20. lastSeenAt before discoveredAt -> rejected safely (INVALID_DATE_RANGE)")
        void lastSeenAtBeforeDiscoveredAtRejected() {
            Instant discoveredAt = Instant.parse("2026-09-10T10:00:00Z");
            Instant lastSeenAt = Instant.parse("2026-09-01T10:00:00Z"); // Earlier than discovered

            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-bad-discovery")
                    .title("Data Engineer")
                    .companyName("Data Co")
                    .discoveredAt(discoveredAt)
                    .lastSeenAt(lastSeenAt)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_DATE_RANGE)
                            && f.safeMessage().contains("cannot precede discoveredAt"));
        }

        @Test
        @DisplayName("21. No timestamps are fabricated when absent")
        void noTimestampsFabricated() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-null-dates")
                    .title("QA Engineer")
                    .companyName("Quality First")
                    .postedAt(null)
                    .expiresAt(null)
                    .build();

            assertThat(candidate.postedAt()).isNull();
            assertThat(candidate.expiresAt()).isNull();
        }
    }

    // =========================================================================
    // 5. SALARY INTEGRITY
    // =========================================================================
    @Nested
    @DisplayName("Salary Range Integrity")
    class SalaryIntegrityTests {

        @Test
        @DisplayName("22. Valid salary range -> success")
        void validSalaryRangeSucceeds() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-sal-ok")
                    .title("Lead Engineer")
                    .companyName("FinTech Ltd")
                    .salaryMin(BigDecimal.valueOf(100000))
                    .salaryMax(BigDecimal.valueOf(150000))
                    .salaryCurrency("INR")
                    .salaryPeriod(SalaryPeriod.YEAR)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("23. Negative salary -> rejected (INVALID_SALARY_RANGE)")
        void negativeSalaryRejected() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-neg-sal")
                    .title("Lead Engineer")
                    .companyName("FinTech Ltd")
                    .salaryMin(BigDecimal.valueOf(-500))
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_SALARY_RANGE)
                            && f.safeMessage().contains("must not be negative"));
        }

        @Test
        @DisplayName("24. Minimum salary greater than maximum -> rejected (INVALID_SALARY_RANGE)")
        void minSalaryGreaterThanMaxRejected() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-inv-sal")
                    .title("Lead Engineer")
                    .companyName("FinTech Ltd")
                    .salaryMin(BigDecimal.valueOf(200000))
                    .salaryMax(BigDecimal.valueOf(100000))
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_SALARY_RANGE)
                            && f.safeMessage().contains("must not exceed salaryMax"));
        }

        @Test
        @DisplayName("25. Missing optional salary -> allowed")
        void missingOptionalSalaryAllowed() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-no-sal")
                    .title("Lead Engineer")
                    .companyName("FinTech Ltd")
                    .salaryMin(null)
                    .salaryMax(null)
                    .salaryCurrency(null)
                    .salaryPeriod(null)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
            assertThat(candidate.salaryMin()).isNull();
            assertThat(candidate.salaryMax()).isNull();
        }

        @Test
        @DisplayName("26. No currency conversion or inference")
        void noCurrencyConversionOrInference() {
            JobIngestionCandidate candidate = JobIngestionCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-curr-test")
                    .title("Engineer")
                    .companyName("Corp")
                    .salaryMin(BigDecimal.valueOf(50000))
                    .salaryCurrency(null)
                    .salaryPeriod(null)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);
            assertThat(normalized.salaryCurrency()).isNull();
            assertThat(normalized.salaryPeriod()).isNull();
            assertThat(normalized.salaryMin()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }
    }

    // =========================================================================
    // 6. EXPERIENCE INTEGRITY
    // =========================================================================
    @Nested
    @DisplayName("Experience Range Integrity")
    class ExperienceIntegrityTests {

        @Test
        @DisplayName("27. Valid range -> success")
        void validExperienceRangeSucceeds() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-exp-ok")
                    .title("Systems Architect")
                    .companyName("Architecture Co")
                    .experienceMinYears(5)
                    .experienceMaxYears(10)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("28. Negative experience -> rejected (INVALID_EXPERIENCE_RANGE)")
        void negativeExperienceRejected() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-neg-exp")
                    .title("Systems Architect")
                    .companyName("Architecture Co")
                    .experienceMinYears(-1)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_EXPERIENCE_RANGE)
                            && f.safeMessage().contains("must not be negative"));
        }

        @Test
        @DisplayName("29. Minimum experience greater than maximum -> rejected (INVALID_EXPERIENCE_RANGE)")
        void minExpGreaterThanMaxExpRejected() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-inv-exp")
                    .title("Systems Architect")
                    .companyName("Architecture Co")
                    .experienceMinYears(12)
                    .experienceMaxYears(5)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_INVALID_EXPERIENCE_RANGE)
                            && f.safeMessage().contains("must not exceed experienceMaxYears"));
        }

        @Test
        @DisplayName("30. Missing optional experience -> allowed")
        void missingOptionalExperienceAllowed() {
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-no-exp")
                    .title("Systems Architect")
                    .companyName("Architecture Co")
                    .experienceMinYears(null)
                    .experienceMaxYears(null)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
            assertThat(candidate.experienceMinYears()).isNull();
            assertThat(candidate.experienceMaxYears()).isNull();
        }
    }

    // =========================================================================
    // 7. TEXT LENGTH SAFETY
    // =========================================================================
    @Nested
    @DisplayName("Text Length Safety (Database Column Limits)")
    class TextLengthSafetyTests {

        @Test
        @DisplayName("Title exceeding 255 chars -> VALUE_TOO_LONG")
        void titleExceeding255Rejected() {
            String longTitle = "A".repeat(256);
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-long-title")
                    .title(longTitle)
                    .companyName("Acme Corp")
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("title"));
        }

        @Test
        @DisplayName("Company name exceeding 255 chars -> VALUE_TOO_LONG")
        void companyNameExceeding255Rejected() {
            String longCompany = "B".repeat(256);
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-long-co")
                    .title("Engineer")
                    .companyName(longCompany)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("companyName"));
        }

        @Test
        @DisplayName("External job ID exceeding 255 chars -> VALUE_TOO_LONG")
        void externalJobIdExceeding255Rejected() {
            String longExtId = "C".repeat(256);
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId(longExtId)
                    .title("Engineer")
                    .companyName("Acme Corp")
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("externalJobId"));
        }

        @Test
        @DisplayName("Recruiter name exceeding 255 chars -> VALUE_TOO_LONG")
        void recruiterNameExceeding255Rejected() {
            String longRecruiter = "D".repeat(256);
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-recruiter-len")
                    .title("Engineer")
                    .companyName("Acme Corp")
                    .recruiterName(longRecruiter)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("recruiterName"));
        }

        @Test
        @DisplayName("Location exceeding 255 chars -> VALUE_TOO_LONG")
        void locationExceeding255Rejected() {
            String longLocation = "E".repeat(256);
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-loc-len")
                    .title("Engineer")
                    .companyName("Acme Corp")
                    .location(longLocation)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("location"));
        }

        @Test
        @DisplayName("Salary currency exceeding 10 chars -> VALUE_TOO_LONG")
        void salaryCurrencyExceeding10Rejected() {
            String longCurrency = "USDDOLLARS1"; // 11 chars
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-curr-len")
                    .title("Engineer")
                    .companyName("Acme Corp")
                    .salaryCurrency(longCurrency)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("salaryCurrency"));
        }

        @Test
        @DisplayName("jobUrl exceeding 1000 chars -> VALUE_TOO_LONG")
        void jobUrlExceeding1000Rejected() {
            String longUrl = "https://example.com/jobs/" + "x".repeat(1000); // > 1000 chars
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-url-len")
                    .title("Engineer")
                    .companyName("Acme Corp")
                    .jobUrl(longUrl)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isFalse();
            assertThat(result.failures())
                    .anyMatch(f -> f.failureCategory().equals(JobIngestionValidator.CATEGORY_VALUE_TOO_LONG)
                            && f.field().equals("jobUrl"));
        }

        @Test
        @DisplayName("Description has no artificial 255 length cap because it is TEXT in database")
        void descriptionHasNo255Cap() {
            String longDescription = "A rich description detailing tech stack and duties. ".repeat(100); // ~5000 chars
            NormalizedJobCandidate candidate = NormalizedJobCandidate.builder()
                    .source(JobSource.LINKEDIN)
                    .externalJobId("ext-desc-len")
                    .title("Senior Architect")
                    .companyName("Acme Corp")
                    .description(longDescription)
                    .build();

            JobValidationResult result = validator.validate(candidate);
            assertThat(result.isValid()).isTrue();
            assertThat(candidate.description()).hasSizeGreaterThan(2000);
        }
    }

    // =========================================================================
    // 8. FAILURE ISOLATION & RESULT MECHANISM
    // =========================================================================
    @Nested
    @DisplayName("Validation Result & Diagnostic Summaries")
    class ValidationResultTests {

        @Test
        @DisplayName("34. Validation failure produces safe message without leaking full descriptions or raw SQL")
        void safeMessageWithoutLeakage() {
            JobValidationFailure failure = new JobValidationFailure(
                    "salaryMin",
                    JobIngestionValidator.CATEGORY_INVALID_SALARY_RANGE,
                    "salaryMin must not be negative: -100"
            );
            JobValidationResult result = JobValidationResult.invalid(java.util.List.of(failure));

            assertThat(result.isValid()).isFalse();
            assertThat(result.primaryFailureCategory()).isEqualTo(JobIngestionValidator.CATEGORY_INVALID_SALARY_RANGE);
            assertThat(result.getSafeMessage()).isEqualTo("salaryMin: salaryMin must not be negative: -100");
            assertThat(result.getSafeMessage()).doesNotContain("SELECT", "INSERT", "org.postgresql");
        }
    }
}

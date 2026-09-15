package com.joblivo.job.validation;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.normalizer.DefaultJobNormalizer;
import com.joblivo.job.normalizer.JobNormalizer;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Canonical in-memory validator for Job Discovery ingestion candidates.
 * <p>
 * Enforces deterministic data integrity boundaries after normalization and before database persistence:
 * <ul>
 *     <li>Source Identity: non-null source, non-blank externalJobId, max 255 chars. Zero ID fabrication.</li>
 *     <li>Required Content: non-blank title (max 255), non-blank companyName (max 255).</li>
 *     <li>Text Length Safety: bounded to database column specifications without silent truncation.</li>
 *     <li>URL Integrity: HTTP/HTTPS scheme and valid host when present (max 1000). Optional URLs allowed.</li>
 *     <li>Range Integrity: non-negative salary and experience ranges, min <= max.</li>
 *     <li>Date Integrity: expiresAt cannot precede postedAt; lastSeenAt cannot precede discoveredAt.</li>
 *     <li>Enum Integrity: valid non-null domain enums.</li>
 *     <li>Security: zero external network calls, zero DNS lookups, zero SSRF, zero dynamic SQL.</li>
 * </ul>
 */
@Component
public class JobIngestionValidator {

    public static final int MAX_EXTERNAL_JOB_ID_LENGTH = 255;
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_COMPANY_NAME_LENGTH = 255;
    public static final int MAX_RECRUITER_NAME_LENGTH = 255;
    public static final int MAX_LOCATION_LENGTH = 255;
    public static final int MAX_SALARY_CURRENCY_LENGTH = 10;
    public static final int MAX_URL_LENGTH = 1000;

    public static final String CATEGORY_INVALID_SOURCE = "INVALID_SOURCE";
    public static final String CATEGORY_INVALID_EXTERNAL_JOB_ID = "INVALID_EXTERNAL_JOB_ID";
    public static final String CATEGORY_MISSING_TITLE = "MISSING_TITLE";
    public static final String CATEGORY_MISSING_COMPANY = "MISSING_COMPANY";
    public static final String CATEGORY_INVALID_JOB_URL = "INVALID_JOB_URL";
    public static final String CATEGORY_INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String CATEGORY_INVALID_SALARY_RANGE = "INVALID_SALARY_RANGE";
    public static final String CATEGORY_INVALID_EXPERIENCE_RANGE = "INVALID_EXPERIENCE_RANGE";
    public static final String CATEGORY_VALUE_TOO_LONG = "VALUE_TOO_LONG";
    public static final String CATEGORY_VALIDATION_ERROR = "VALIDATION_ERROR";

    private final JobNormalizer jobNormalizer;

    public JobIngestionValidator() {
        this(new DefaultJobNormalizer());
    }

    @Autowired
    public JobIngestionValidator(JobNormalizer jobNormalizer) {
        this.jobNormalizer = Objects.requireNonNull(jobNormalizer, "jobNormalizer must not be null");
    }

    /**
     * Validates a normalized job candidate deterministically.
     *
     * @param candidate the normalized candidate to validate
     * @return JobValidationResult detailing validity and any failures
     */
    public JobValidationResult validate(NormalizedJobCandidate candidate) {
        if (candidate == null) {
            return JobValidationResult.invalid("candidate", CATEGORY_INVALID_SOURCE, "NormalizedJobCandidate must not be null");
        }

        List<JobValidationFailure> failures = new ArrayList<>();

        // 1. Source Identity
        if (candidate.source() == null) {
            failures.add(new JobValidationFailure("source", CATEGORY_INVALID_SOURCE, "Job source must not be null"));
        }
        if (candidate.externalJobId() == null || candidate.externalJobId().isBlank()) {
            failures.add(new JobValidationFailure("externalJobId", CATEGORY_INVALID_EXTERNAL_JOB_ID, "externalJobId must not be null or blank"));
        } else if (candidate.externalJobId().length() > MAX_EXTERNAL_JOB_ID_LENGTH) {
            failures.add(new JobValidationFailure(
                    "externalJobId",
                    CATEGORY_VALUE_TOO_LONG,
                    "externalJobId length (" + candidate.externalJobId().length() + ") exceeds maximum limit of " + MAX_EXTERNAL_JOB_ID_LENGTH + " characters"
            ));
        }

        // 2. Required Content (Title and Company Name)
        if (candidate.title() == null || candidate.title().isBlank()) {
            failures.add(new JobValidationFailure("title", CATEGORY_MISSING_TITLE, "title must not be null or blank"));
        } else if (candidate.title().length() > MAX_TITLE_LENGTH) {
            failures.add(new JobValidationFailure(
                    "title",
                    CATEGORY_VALUE_TOO_LONG,
                    "title length (" + candidate.title().length() + ") exceeds maximum limit of " + MAX_TITLE_LENGTH + " characters"
            ));
        }

        if (candidate.companyName() == null || candidate.companyName().isBlank()) {
            failures.add(new JobValidationFailure("companyName", CATEGORY_MISSING_COMPANY, "companyName must not be null or blank"));
        } else if (candidate.companyName().length() > MAX_COMPANY_NAME_LENGTH) {
            failures.add(new JobValidationFailure(
                    "companyName",
                    CATEGORY_VALUE_TOO_LONG,
                    "companyName length (" + candidate.companyName().length() + ") exceeds maximum limit of " + MAX_COMPANY_NAME_LENGTH + " characters"
            ));
        }

        // 3. Optional Text Lengths
        if (candidate.recruiterName() != null && candidate.recruiterName().length() > MAX_RECRUITER_NAME_LENGTH) {
            failures.add(new JobValidationFailure(
                    "recruiterName",
                    CATEGORY_VALUE_TOO_LONG,
                    "recruiterName length (" + candidate.recruiterName().length() + ") exceeds maximum limit of " + MAX_RECRUITER_NAME_LENGTH + " characters"
            ));
        }

        if (candidate.location() != null && candidate.location().length() > MAX_LOCATION_LENGTH) {
            failures.add(new JobValidationFailure(
                    "location",
                    CATEGORY_VALUE_TOO_LONG,
                    "location length (" + candidate.location().length() + ") exceeds maximum limit of " + MAX_LOCATION_LENGTH + " characters"
            ));
        }

        if (candidate.salaryCurrency() != null && candidate.salaryCurrency().length() > MAX_SALARY_CURRENCY_LENGTH) {
            failures.add(new JobValidationFailure(
                    "salaryCurrency",
                    CATEGORY_VALUE_TOO_LONG,
                    "salaryCurrency length (" + candidate.salaryCurrency().length() + ") exceeds maximum limit of " + MAX_SALARY_CURRENCY_LENGTH + " characters"
            ));
        }

        // 4. URL Integrity
        validateUrlField(candidate.jobUrl(), "jobUrl", failures);
        validateUrlField(candidate.companyUrl(), "companyUrl", failures);

        // 5. Enums
        if (candidate.workMode() == null) {
            failures.add(new JobValidationFailure("workMode", CATEGORY_VALIDATION_ERROR, "workMode must not be null"));
        }
        if (candidate.employmentType() == null) {
            failures.add(new JobValidationFailure("employmentType", CATEGORY_VALIDATION_ERROR, "employmentType must not be null"));
        }
        if (candidate.applicationMethod() == null) {
            failures.add(new JobValidationFailure("applicationMethod", CATEGORY_VALIDATION_ERROR, "applicationMethod must not be null"));
        }

        // 6. Experience Range
        Integer minExp = candidate.experienceMinYears();
        Integer maxExp = candidate.experienceMaxYears();
        if (minExp != null && minExp < 0) {
            failures.add(new JobValidationFailure("experienceMinYears", CATEGORY_INVALID_EXPERIENCE_RANGE, "experienceMinYears must not be negative: " + minExp));
        }
        if (maxExp != null && maxExp < 0) {
            failures.add(new JobValidationFailure("experienceMaxYears", CATEGORY_INVALID_EXPERIENCE_RANGE, "experienceMaxYears must not be negative: " + maxExp));
        }
        if (minExp != null && maxExp != null && minExp > maxExp) {
            failures.add(new JobValidationFailure(
                    "experienceRange",
                    CATEGORY_INVALID_EXPERIENCE_RANGE,
                    "experienceMinYears (" + minExp + ") must not exceed experienceMaxYears (" + maxExp + ")"
            ));
        }

        // 7. Salary Range
        BigDecimal minSal = candidate.salaryMin();
        BigDecimal maxSal = candidate.salaryMax();
        if (minSal != null && minSal.compareTo(BigDecimal.ZERO) < 0) {
            failures.add(new JobValidationFailure("salaryMin", CATEGORY_INVALID_SALARY_RANGE, "salaryMin must not be negative: " + minSal));
        }
        if (maxSal != null && maxSal.compareTo(BigDecimal.ZERO) < 0) {
            failures.add(new JobValidationFailure("salaryMax", CATEGORY_INVALID_SALARY_RANGE, "salaryMax must not be negative: " + maxSal));
        }
        if (minSal != null && maxSal != null && minSal.compareTo(maxSal) > 0) {
            failures.add(new JobValidationFailure(
                    "salaryRange",
                    CATEGORY_INVALID_SALARY_RANGE,
                    "salaryMin (" + minSal + ") must not exceed salaryMax (" + maxSal + ")"
            ));
        }

        // 8. Date Integrity
        Instant postedAt = candidate.postedAt();
        Instant expiresAt = candidate.expiresAt();
        if (postedAt != null && expiresAt != null && expiresAt.isBefore(postedAt)) {
            failures.add(new JobValidationFailure(
                    "expiresAt",
                    CATEGORY_INVALID_DATE_RANGE,
                    "expiresAt (" + expiresAt + ") cannot precede postedAt (" + postedAt + ")"
            ));
        }

        Instant discoveredAt = candidate.discoveredAt();
        Instant lastSeenAt = candidate.lastSeenAt();
        if (discoveredAt != null && lastSeenAt != null && lastSeenAt.isBefore(discoveredAt)) {
            failures.add(new JobValidationFailure(
                    "lastSeenAt",
                    CATEGORY_INVALID_DATE_RANGE,
                    "lastSeenAt (" + lastSeenAt + ") cannot precede discoveredAt (" + discoveredAt + ")"
            ));
        }

        return failures.isEmpty() ? JobValidationResult.valid() : JobValidationResult.invalid(failures);
    }

    /**
     * Validates a normalized candidate and throws {@link JobValidationException} if invalid.
     *
     * @param candidate normalized candidate
     * @throws JobValidationException if validation fails
     */
    public void validateOrThrow(NormalizedJobCandidate candidate) {
        JobValidationResult result = validate(candidate);
        if (!result.isValid()) {
            throw new JobValidationException(result.primaryFailureCategory(), result.getSafeMessage(), result);
        }
    }

    /**
     * Validates a raw ingestion candidate by normalizing and validating content integrity.
     *
     * @param candidate raw candidate
     * @return JobValidationResult detailing validity and any failures
     */
    public JobValidationResult validate(JobIngestionCandidate candidate) {
        if (candidate == null) {
            return JobValidationResult.invalid("candidate", CATEGORY_INVALID_SOURCE, "JobIngestionCandidate must not be null");
        }

        List<JobValidationFailure> failures = new ArrayList<>();

        // Fast-fail on fundamental source identity and mandatory text to provide specific categories
        if (candidate.source() == null) {
            failures.add(new JobValidationFailure("source", CATEGORY_INVALID_SOURCE, "Job source must not be null"));
        }
        if (candidate.externalJobId() == null || candidate.externalJobId().isBlank()) {
            failures.add(new JobValidationFailure("externalJobId", CATEGORY_INVALID_EXTERNAL_JOB_ID, "externalJobId must not be null or blank"));
        } else if (candidate.externalJobId().trim().length() > MAX_EXTERNAL_JOB_ID_LENGTH) {
            failures.add(new JobValidationFailure(
                    "externalJobId",
                    CATEGORY_VALUE_TOO_LONG,
                    "externalJobId length (" + candidate.externalJobId().trim().length() + ") exceeds maximum limit of " + MAX_EXTERNAL_JOB_ID_LENGTH + " characters"
            ));
        }

        if (candidate.title() == null || candidate.title().isBlank()) {
            failures.add(new JobValidationFailure("title", CATEGORY_MISSING_TITLE, "title must not be null or blank"));
        } else if (candidate.title().trim().length() > MAX_TITLE_LENGTH) {
            failures.add(new JobValidationFailure(
                    "title",
                    CATEGORY_VALUE_TOO_LONG,
                    "title length (" + candidate.title().trim().length() + ") exceeds maximum limit of " + MAX_TITLE_LENGTH + " characters"
            ));
        }

        if (candidate.companyName() == null || candidate.companyName().isBlank()) {
            failures.add(new JobValidationFailure("companyName", CATEGORY_MISSING_COMPANY, "companyName must not be null or blank"));
        } else if (candidate.companyName().trim().length() > MAX_COMPANY_NAME_LENGTH) {
            failures.add(new JobValidationFailure(
                    "companyName",
                    CATEGORY_VALUE_TOO_LONG,
                    "companyName length (" + candidate.companyName().trim().length() + ") exceeds maximum limit of " + MAX_COMPANY_NAME_LENGTH + " characters"
            ));
        }

        // URL syntax check for raw candidate
        validateUrlField(candidate.jobUrl(), "jobUrl", failures);
        validateUrlField(candidate.companyUrl(), "companyUrl", failures);

        // Experience range check for raw candidate
        Integer minExp = candidate.experienceMinYears();
        Integer maxExp = candidate.experienceMaxYears();
        if (minExp != null && minExp < 0) {
            failures.add(new JobValidationFailure("experienceMinYears", CATEGORY_INVALID_EXPERIENCE_RANGE, "experienceMinYears must not be negative: " + minExp));
        }
        if (maxExp != null && maxExp < 0) {
            failures.add(new JobValidationFailure("experienceMaxYears", CATEGORY_INVALID_EXPERIENCE_RANGE, "experienceMaxYears must not be negative: " + maxExp));
        }
        if (minExp != null && maxExp != null && minExp > maxExp) {
            failures.add(new JobValidationFailure(
                    "experienceRange",
                    CATEGORY_INVALID_EXPERIENCE_RANGE,
                    "experienceMinYears (" + minExp + ") must not exceed experienceMaxYears (" + maxExp + ")"
            ));
        }

        // Salary range check for raw candidate
        BigDecimal minSal = candidate.salaryMin();
        BigDecimal maxSal = candidate.salaryMax();
        if (minSal != null && minSal.compareTo(BigDecimal.ZERO) < 0) {
            failures.add(new JobValidationFailure("salaryMin", CATEGORY_INVALID_SALARY_RANGE, "salaryMin must not be negative: " + minSal));
        }
        if (maxSal != null && maxSal.compareTo(BigDecimal.ZERO) < 0) {
            failures.add(new JobValidationFailure("salaryMax", CATEGORY_INVALID_SALARY_RANGE, "salaryMax must not be negative: " + maxSal));
        }
        if (minSal != null && maxSal != null && minSal.compareTo(maxSal) > 0) {
            failures.add(new JobValidationFailure(
                    "salaryRange",
                    CATEGORY_INVALID_SALARY_RANGE,
                    "salaryMin (" + minSal + ") must not exceed salaryMax (" + maxSal + ")"
            ));
        }

        // Date check for raw candidate
        Instant postedAt = candidate.postedAt();
        Instant expiresAt = candidate.expiresAt();
        if (postedAt != null && expiresAt != null && expiresAt.isBefore(postedAt)) {
            failures.add(new JobValidationFailure(
                    "expiresAt",
                    CATEGORY_INVALID_DATE_RANGE,
                    "expiresAt (" + expiresAt + ") cannot precede postedAt (" + postedAt + ")"
            ));
        }

        Instant discoveredAt = candidate.discoveredAt();
        Instant lastSeenAt = candidate.lastSeenAt();
        if (discoveredAt != null && lastSeenAt != null && lastSeenAt.isBefore(discoveredAt)) {
            failures.add(new JobValidationFailure(
                    "lastSeenAt",
                    CATEGORY_INVALID_DATE_RANGE,
                    "lastSeenAt (" + lastSeenAt + ") cannot precede discoveredAt (" + discoveredAt + ")"
            ));
        }

        // Text lengths for optional fields
        if (candidate.recruiterName() != null && candidate.recruiterName().trim().length() > MAX_RECRUITER_NAME_LENGTH) {
            failures.add(new JobValidationFailure(
                    "recruiterName",
                    CATEGORY_VALUE_TOO_LONG,
                    "recruiterName length (" + candidate.recruiterName().trim().length() + ") exceeds maximum limit of " + MAX_RECRUITER_NAME_LENGTH + " characters"
            ));
        }
        if (candidate.location() != null && candidate.location().trim().length() > MAX_LOCATION_LENGTH) {
            failures.add(new JobValidationFailure(
                    "location",
                    CATEGORY_VALUE_TOO_LONG,
                    "location length (" + candidate.location().trim().length() + ") exceeds maximum limit of " + MAX_LOCATION_LENGTH + " characters"
            ));
        }
        if (candidate.salaryCurrency() != null && candidate.salaryCurrency().trim().length() > MAX_SALARY_CURRENCY_LENGTH) {
            failures.add(new JobValidationFailure(
                    "salaryCurrency",
                    CATEGORY_VALUE_TOO_LONG,
                    "salaryCurrency length (" + candidate.salaryCurrency().trim().length() + ") exceeds maximum limit of " + MAX_SALARY_CURRENCY_LENGTH + " characters"
            ));
        }

        if (!failures.isEmpty()) {
            return JobValidationResult.invalid(failures);
        }

        // Now attempt normalization and validate the normalized form
        try {
            NormalizedJobCandidate normalized = jobNormalizer.normalize(candidate);
            return validate(normalized);
        } catch (JobValidationException ex) {
            String category = ex.getFailureCategory() != null ? ex.getFailureCategory() : CATEGORY_VALIDATION_ERROR;
            return JobValidationResult.invalid("candidate", category, ex.getMessage());
        }
    }

    /**
     * Validates a raw ingestion candidate and throws {@link JobValidationException} if invalid.
     *
     * @param candidate raw candidate
     * @throws JobValidationException if validation fails
     */
    public void validateOrThrow(JobIngestionCandidate candidate) {
        JobValidationResult result = validate(candidate);
        if (!result.isValid()) {
            throw new JobValidationException(result.primaryFailureCategory(), result.getSafeMessage(), result);
        }
    }

    /**
     * Validates a standard JobCandidate.
     *
     * @param candidate standard job candidate
     * @return JobValidationResult detailing validity and any failures
     */
    public JobValidationResult validate(JobCandidate candidate) {
        if (candidate == null) {
            return JobValidationResult.invalid("candidate", CATEGORY_INVALID_SOURCE, "JobCandidate must not be null");
        }
        return validate(JobIngestionCandidate.from(candidate));
    }

    /**
     * Validates a standard JobCandidate and throws {@link JobValidationException} if invalid.
     *
     * @param candidate standard job candidate
     * @throws JobValidationException if validation fails
     */
    public void validateOrThrow(JobCandidate candidate) {
        JobValidationResult result = validate(candidate);
        if (!result.isValid()) {
            throw new JobValidationException(result.primaryFailureCategory(), result.getSafeMessage(), result);
        }
    }

    private void validateUrlField(String rawUrl, String fieldName, List<JobValidationFailure> failures) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return; // URL is optional in the database schema
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            failures.add(new JobValidationFailure(
                    fieldName,
                    CATEGORY_VALUE_TOO_LONG,
                    fieldName + " length (" + trimmed.length() + ") exceeds maximum limit of " + MAX_URL_LENGTH + " characters"
            ));
            return;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                failures.add(new JobValidationFailure(
                        fieldName,
                        CATEGORY_INVALID_JOB_URL,
                        fieldName + " must use HTTP or HTTPS scheme: " + trimmed
                ));
            } else if (uri.getHost() == null || uri.getHost().isBlank()) {
                failures.add(new JobValidationFailure(
                        fieldName,
                        CATEGORY_INVALID_JOB_URL,
                        fieldName + " must specify a valid host: " + trimmed
                ));
            }
        } catch (IllegalArgumentException ex) {
            failures.add(new JobValidationFailure(
                    fieldName,
                    CATEGORY_INVALID_JOB_URL,
                    fieldName + " is a malformed URL: " + trimmed
            ));
        }
    }
}

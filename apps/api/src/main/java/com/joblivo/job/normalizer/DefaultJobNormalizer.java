package com.joblivo.job.normalizer;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link JobNormalizer}.
 * Performs pure, deterministic in-memory normalization, string cleanup, range checks, and date chronology validation.
 */
@Component
public class DefaultJobNormalizer implements JobNormalizer {

    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public NormalizedJobCandidate normalize(JobIngestionCandidate candidate) {
        Objects.requireNonNull(candidate, "JobIngestionCandidate must not be null");

        // Identity
        JobSource source = candidate.source();
        if (source == null) {
            throw new JobValidationException("Job source must not be null");
        }
        String externalJobId = JobSourceIdentity.normalizeExternalJobId(candidate.externalJobId());

        // Core required text
        String title = normalizeTitle(candidate.title());
        String companyName = normalizeCompanyName(candidate.companyName());

        // Optional text
        String recruiterName = normalizeCollapsedString(candidate.recruiterName());
        String description = normalizeDescription(candidate.description());
        String location = normalizeLocation(candidate.location());

        // Enums & Raw mappings
        JobWorkMode workMode = resolveWorkMode(candidate.workMode(), candidate.rawWorkMode());
        JobEmploymentType employmentType = resolveEmploymentType(candidate.employmentType(), candidate.rawEmploymentType());
        JobApplicationMethod applicationMethod = resolveApplicationMethod(candidate.applicationMethod(), candidate.rawApplicationMethod());

        // Experience range
        Integer experienceMin = candidate.experienceMinYears();
        Integer experienceMax = candidate.experienceMaxYears();
        validateExperienceRange(experienceMin, experienceMax);

        // Salary range & details
        BigDecimal salaryMin = candidate.salaryMin();
        BigDecimal salaryMax = candidate.salaryMax();
        validateSalaryRange(salaryMin, salaryMax);
        String salaryCurrency = normalizeCurrency(candidate.salaryCurrency());
        SalaryPeriod salaryPeriod = resolveSalaryPeriod(candidate.salaryPeriod(), candidate.rawSalaryPeriod());

        // URLs
        String jobUrl = normalizeAndValidateUrl(candidate.jobUrl(), "jobUrl");
        String companyUrl = normalizeAndValidateUrl(candidate.companyUrl(), "companyUrl");

        // Timestamps
        Instant postedAt = candidate.postedAt();
        Instant expiresAt = candidate.expiresAt();
        validatePostingDates(postedAt, expiresAt);

        Instant discoveredAt = candidate.discoveredAt() != null ? candidate.discoveredAt() : Instant.now();
        Instant lastSeenAt = candidate.lastSeenAt() != null ? candidate.lastSeenAt() : discoveredAt;
        validateDiscoveryDates(discoveredAt, lastSeenAt);

        return new NormalizedJobCandidate(
                source,
                externalJobId,
                title,
                companyName,
                recruiterName,
                description,
                location,
                workMode,
                employmentType,
                experienceMin,
                experienceMax,
                salaryMin,
                salaryMax,
                salaryCurrency,
                salaryPeriod,
                jobUrl,
                companyUrl,
                postedAt,
                expiresAt,
                discoveredAt,
                lastSeenAt,
                applicationMethod
        );
    }

    @Override
    public NormalizedJobCandidate normalize(JobCandidate candidate) {
        Objects.requireNonNull(candidate, "JobCandidate must not be null");
        return normalize(JobIngestionCandidate.from(candidate));
    }

    private String normalizeRequiredString(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new JobValidationException(fieldName + " must not be null or blank");
        }
        return value.trim();
    }

    @Override
    public String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new JobValidationException("title must not be null or blank");
        }
        String nfc = Normalizer.normalize(title, Normalizer.Form.NFC);
        String stripped = nfc.strip();
        if (stripped.isEmpty()) {
            throw new JobValidationException("title must not be null or blank");
        }
        return MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ");
    }

    @Override
    public String normalizeCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new JobValidationException("companyName must not be null or blank");
        }
        String nfc = Normalizer.normalize(companyName, Normalizer.Form.NFC);
        String stripped = nfc.strip();
        if (stripped.isEmpty()) {
            throw new JobValidationException("companyName must not be null or blank");
        }
        return MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ");
    }

    @Override
    public String normalizeLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String nfc = Normalizer.normalize(location, Normalizer.Form.NFC);
        String stripped = nfc.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ");
    }

    private String normalizeCollapsedString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        String stripped = nfc.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ");
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String nfc = Normalizer.normalize(description, Normalizer.Form.NFC);
        String stripped = nfc.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        // Preserve meaningful internal newlines, punctuation, and markdown; only trim outer boundaries
        return stripped;
    }

    private JobWorkMode resolveWorkMode(JobWorkMode explicitMode, String rawMode) {
        if (explicitMode != null && explicitMode != JobWorkMode.UNKNOWN) {
            return explicitMode;
        }
        if (rawMode != null && !rawMode.isBlank()) {
            String normalized = rawMode.trim().toLowerCase();
            if (normalized.equals("remote")) {
                return JobWorkMode.REMOTE;
            }
            if (normalized.equals("hybrid")) {
                return JobWorkMode.HYBRID;
            }
            if (normalized.equals("onsite") || normalized.equals("on-site") || normalized.equals("in-office")
                    || normalized.equals("office") || normalized.equals("on site")) {
                return JobWorkMode.ONSITE;
            }
        }
        return explicitMode != null ? explicitMode : JobWorkMode.UNKNOWN;
    }

    private JobEmploymentType resolveEmploymentType(JobEmploymentType explicitType, String rawType) {
        if (explicitType != null && explicitType != JobEmploymentType.UNKNOWN) {
            return explicitType;
        }
        if (rawType != null && !rawType.isBlank()) {
            String normalized = rawType.trim().toLowerCase();
            if (normalized.equals("full_time") || normalized.equals("full-time") || normalized.equals("full time")
                    || normalized.equals("permanent") || normalized.equals("regular")) {
                return JobEmploymentType.FULL_TIME;
            }
            if (normalized.equals("part_time") || normalized.equals("part-time") || normalized.equals("part time")) {
                return JobEmploymentType.PART_TIME;
            }
            if (normalized.equals("contract") || normalized.equals("contractor")) {
                return JobEmploymentType.CONTRACT;
            }
            if (normalized.equals("internship") || normalized.equals("intern")) {
                return JobEmploymentType.INTERNSHIP;
            }
            if (normalized.equals("temporary") || normalized.equals("temp")) {
                return JobEmploymentType.TEMPORARY;
            }
            if (normalized.equals("freelance")) {
                return JobEmploymentType.FREELANCE;
            }
        }
        return explicitType != null ? explicitType : JobEmploymentType.UNKNOWN;
    }

    private JobApplicationMethod resolveApplicationMethod(JobApplicationMethod explicitMethod, String rawMethod) {
        if (explicitMethod != null && explicitMethod != JobApplicationMethod.UNKNOWN) {
            return explicitMethod;
        }
        if (rawMethod != null && !rawMethod.isBlank()) {
            String normalized = rawMethod.trim().toLowerCase();
            if (normalized.equals("internal") || normalized.equals("internal_portal") || normalized.equals("internal portal")) {
                return JobApplicationMethod.INTERNAL_PORTAL;
            }
            if (normalized.equals("external") || normalized.equals("external_company_site") || normalized.equals("company_site")
                    || normalized.equals("company")) {
                return JobApplicationMethod.EXTERNAL_COMPANY_SITE;
            }
            if (normalized.equals("ats") || normalized.equals("greenhouse") || normalized.equals("lever") || normalized.equals("workday")) {
                return JobApplicationMethod.ATS;
            }
            if (normalized.equals("portal") || normalized.equals("source_portal") || normalized.equals("easy_apply")
                    || normalized.equals("easy apply")) {
                return JobApplicationMethod.SOURCE_PORTAL;
            }
            if (normalized.equals("email")) {
                return JobApplicationMethod.EMAIL;
            }
        }
        return explicitMethod != null ? explicitMethod : JobApplicationMethod.UNKNOWN;
    }

    private void validateExperienceRange(Integer min, Integer max) {
        if (min != null && min < 0) {
            throw new JobValidationException("experienceMinYears must not be negative: " + min);
        }
        if (max != null && max < 0) {
            throw new JobValidationException("experienceMaxYears must not be negative: " + max);
        }
        if (min != null && max != null && min > max) {
            throw new JobValidationException("experienceMinYears (" + min + ") must not exceed experienceMaxYears (" + max + ")");
        }
    }

    private void validateSalaryRange(BigDecimal min, BigDecimal max) {
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) {
            throw new JobValidationException("salaryMin must not be negative: " + min);
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) < 0) {
            throw new JobValidationException("salaryMax must not be negative: " + max);
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new JobValidationException("salaryMin (" + min + ") must not exceed salaryMax (" + max + ")");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase();
    }

    private SalaryPeriod resolveSalaryPeriod(SalaryPeriod explicitPeriod, String rawPeriod) {
        if (explicitPeriod != null) {
            return explicitPeriod;
        }
        if (rawPeriod != null && !rawPeriod.isBlank()) {
            String normalized = rawPeriod.trim().toLowerCase();
            if (normalized.equals("year") || normalized.equals("yearly") || normalized.equals("annual")
                    || normalized.equals("annually") || normalized.equals("per_annum") || normalized.equals("per annum")) {
                return SalaryPeriod.YEAR;
            }
            if (normalized.equals("month") || normalized.equals("monthly") || normalized.equals("per_month")
                    || normalized.equals("per month")) {
                return SalaryPeriod.MONTH;
            }
            if (normalized.equals("hour") || normalized.equals("hourly") || normalized.equals("per_hour")
                    || normalized.equals("per hour")) {
                return SalaryPeriod.HOUR;
            }
            return SalaryPeriod.OTHER;
        }
        return null;
    }

    private String normalizeAndValidateUrl(String url, String fieldName) {
        return JobUrlNormalizer.normalizeUrl(url, fieldName);
    }

    private void validatePostingDates(Instant postedAt, Instant expiresAt) {
        if (postedAt != null && expiresAt != null && expiresAt.isBefore(postedAt)) {
            throw new JobValidationException("expiresAt (" + expiresAt + ") cannot precede postedAt (" + postedAt + ")");
        }
    }

    private void validateDiscoveryDates(Instant discoveredAt, Instant lastSeenAt) {
        if (discoveredAt != null && lastSeenAt != null && lastSeenAt.isBefore(discoveredAt)) {
            throw new JobValidationException("lastSeenAt (" + lastSeenAt + ") cannot precede discoveredAt (" + discoveredAt + ")");
        }
    }
}

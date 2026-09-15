package com.joblivo.job.normalizer;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.JobCandidate;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobNormalizer Unit Tests")
class JobNormalizerTest {

    private JobNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new DefaultJobNormalizer();
    }

    private JobIngestionCandidate.Builder validCandidateBuilder() {
        return JobIngestionCandidate.builder()
                .source(JobSource.LINKEDIN)
                .externalJobId("ext-norm-101")
                .title("Senior Cloud Architect")
                .companyName("Acme Global Systems")
                .recruiterName("Sarah Connor")
                .description("Leading cloud native transformations.\n\nKey skills:\n- Kubernetes\n- AWS")
                .location("Seattle, WA")
                .workMode(JobWorkMode.REMOTE)
                .employmentType(JobEmploymentType.FULL_TIME)
                .experienceMinYears(5)
                .experienceMaxYears(10)
                .salaryMin(BigDecimal.valueOf(175000))
                .salaryMax(BigDecimal.valueOf(220000))
                .salaryCurrency("usd")
                .salaryPeriod(SalaryPeriod.YEAR)
                .jobUrl("https://linkedin.com/jobs/view/101")
                .companyUrl("https://acmeglobal.com")
                .applicationMethod(JobApplicationMethod.ATS)
                .postedAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    }

    @Nested
    @DisplayName("A. String Normalization & Text Preservation")
    class StringNormalizationTests {

        @Test
        @DisplayName("Trims outer whitespace and converts blank strings to null for optional fields")
        void trimsWhitespaceAndConvertsBlankToNull() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .recruiterName("   ")
                    .description("  Preserved description with   internal   spacing.  ")
                    .location("")
                    .jobUrl("   ")
                    .companyUrl(null)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.recruiterName()).isNull();
            assertThat(normalized.location()).isNull();
            assertThat(normalized.jobUrl()).isNull();
            assertThat(normalized.companyUrl()).isNull();
            // Description preserves internal formatting but outer whitespace is trimmed
            assertThat(normalized.description()).isEqualTo("Preserved description with   internal   spacing.");
        }

        @Test
        @DisplayName("Preserves newlines, punctuation, and markdown in job descriptions without destructive alteration")
        void preservesDescriptionContent() {
            String complexDescription = """
                    ## Role Overview
                    We are looking for a *Staff Engineer*!
                    
                    Responsibilities:
                    1. Build high-scale microservices (99.99% uptime).
                    2. Mentor junior engineers & lead architecture.
                    """;

            JobIngestionCandidate candidate = validCandidateBuilder()
                    .description(complexDescription)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.description()).isEqualTo(complexDescription.trim());
        }
    }

    @Nested
    @DisplayName("B. Title Normalization")
    class TitleNormalizationTests {

        @Test
        @DisplayName("Normalizes formatting noise by trimming and collapsing consecutive whitespace")
        void normalizesTitleWhitespace() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .title("   Senior    Cloud    Architect   ")
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.title()).isEqualTo("Senior Cloud Architect");
            assertThat(normalizer.normalizeTitle("   Senior    Cloud    Architect   ")).isEqualTo("Senior Cloud Architect");
        }

        @Test
        @DisplayName("Preserves seniority terms without removal or classification")
        void preservesSeniorityTerms() {
            String[] seniorityTitles = {
                    "Senior Software Engineer",
                    "Lead DevOps Architect",
                    "Staff Infrastructure Engineer",
                    "Principal Systems Architect",
                    "Engineering Manager",
                    "Director of Engineering",
                    "VP of Engineering",
                    "Junior Developer",
                    "Associate Quality Engineer",
                    "Software Engineer Intern"
            };

            for (String title : seniorityTitles) {
                assertThat(normalizer.normalizeTitle("  " + title + "  ")).isEqualTo(title);
            }
        }

        @Test
        @DisplayName("Preserves technology terms and abbreviations without altering meaning")
        void preservesTechnologyTermsAndAbbreviations() {
            assertThat(normalizer.normalizeTitle("Senior   AWS   Cloud   Engineer"))
                    .isEqualTo("Senior AWS Cloud Engineer");
            assertThat(normalizer.normalizeTitle("Staff   Kubernetes & Docker   Architect"))
                    .isEqualTo("Staff Kubernetes & Docker Architect");
            assertThat(normalizer.normalizeTitle("Lead   AI / ML   Research   Scientist"))
                    .isEqualTo("Lead AI / ML Research Scientist");
            assertThat(normalizer.normalizeTitle("Principal   SRE / DevOps   Engineer"))
                    .isEqualTo("Principal SRE / DevOps Engineer");
            assertThat(normalizer.normalizeTitle("QA   Automation   Engineer"))
                    .isEqualTo("QA Automation Engineer");
        }

        @Test
        @DisplayName("Preserves meaningful punctuation in programming languages and tools")
        void preservesMeaningfulPunctuation() {
            assertThat(normalizer.normalizeTitle("Senior   C++   Developer")).isEqualTo("Senior C++ Developer");
            assertThat(normalizer.normalizeTitle(".NET   Core   Backend   Architect")).isEqualTo(".NET Core Backend Architect");
            assertThat(normalizer.normalizeTitle("CI/CD   Pipeline   Specialist")).isEqualTo("CI/CD Pipeline Specialist");
            assertThat(normalizer.normalizeTitle("Node.js   Full-Stack   Engineer")).isEqualTo("Node.js Full-Stack Engineer");
            assertThat(normalizer.normalizeTitle("PL/SQL   Database   Developer")).isEqualTo("PL/SQL Database Developer");
        }

        @Test
        @DisplayName("Does not perform semantic classification or synonym substitution")
        void noSemanticClassificationOrSynonymSubstitution() {
            // Must NOT rename Developer to Engineer or Cloud to Generic
            assertThat(normalizer.normalizeTitle("Senior Software Developer")).isEqualTo("Senior Software Developer");
            assertThat(normalizer.normalizeTitle("Lead Full-Stack Developer")).isEqualTo("Lead Full-Stack Developer");
            assertThat(normalizer.normalizeTitle("Senior AWS Cloud Engineer")).isEqualTo("Senior AWS Cloud Engineer");
        }

        @Test
        @DisplayName("Applies Unicode NFC normalization consistently")
        void appliesUnicodeNormalization() {
            // Composed vs decomposed é (\u0065\u0301)
            String decomposed = "De\u0301veloppeur   Full-Stack";
            assertThat(normalizer.normalizeTitle(decomposed)).isEqualTo("Développeur Full-Stack");
        }

        @Test
        @DisplayName("Throws JobValidationException if title is missing or blank")
        void blankTitleThrowsException() {
            assertThatThrownBy(() -> normalizer.normalize(validCandidateBuilder().title("   ").build()))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("title must not be null or blank");

            assertThatThrownBy(() -> normalizer.normalizeTitle(null))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("title must not be null or blank");

            assertThatThrownBy(() -> normalizer.normalizeTitle("   \t\n  "))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("title must not be null or blank");
        }
    }

    @Nested
    @DisplayName("C. Company Name Normalization")
    class CompanyNormalizationTests {

        @Test
        @DisplayName("Trims whitespace and collapses consecutive spaces while preserving original company identity")
        void cleansCompanyWhitespaceWithoutMerging() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .companyName("   ABC    Technologies   Pvt   Ltd   ")
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.companyName()).isEqualTo("ABC Technologies Pvt Ltd");
            assertThat(normalizer.normalizeCompanyName("   Acme   Technologies  ")).isEqualTo("Acme Technologies");
        }

        @Test
        @DisplayName("Preserves meaningful punctuation in company names")
        void preservesMeaningfulPunctuation() {
            assertThat(normalizer.normalizeCompanyName("  Acme Technologies,   Inc.  ")).isEqualTo("Acme Technologies, Inc.");
            assertThat(normalizer.normalizeCompanyName("O'Reilly   Media")).isEqualTo("O'Reilly Media");
            assertThat(normalizer.normalizeCompanyName("AT&T   Corporation")).isEqualTo("AT&T Corporation");
            assertThat(normalizer.normalizeCompanyName("Johnson &   Johnson")).isEqualTo("Johnson & Johnson");
            assertThat(normalizer.normalizeCompanyName("Yahoo!   Inc.")).isEqualTo("Yahoo! Inc.");
            assertThat(normalizer.normalizeCompanyName("3M   Company")).isEqualTo("3M Company");
            assertThat(normalizer.normalizeCompanyName("A-B   InBev")).isEqualTo("A-B InBev");
        }

        @Test
        @DisplayName("Preserves original casing without altering semantic company identity")
        void preservesOriginalCasing() {
            assertThat(normalizer.normalizeCompanyName("ACME TECHNOLOGIES")).isEqualTo("ACME TECHNOLOGIES");
            assertThat(normalizer.normalizeCompanyName("eBay Inc.")).isEqualTo("eBay Inc.");
            assertThat(normalizer.normalizeCompanyName("ThoughtWorks")).isEqualTo("ThoughtWorks");
        }

        @Test
        @DisplayName("Does not fabricate legal suffixes or infer parent companies")
        void doesNotFabricateOrInfer() {
            // Does not add LLC if not present, does not remove Inc if present
            assertThat(normalizer.normalizeCompanyName("Google")).isEqualTo("Google");
            assertThat(normalizer.normalizeCompanyName("Instagram")).isEqualTo("Instagram");
            assertThat(normalizer.normalizeCompanyName("Stripe, Inc.")).isEqualTo("Stripe, Inc.");
        }

        @Test
        @DisplayName("Applies Unicode NFC normalization to company names")
        void appliesUnicodeNormalization() {
            String decomposed = "Cafe\u0301   Coffee   Day";
            assertThat(normalizer.normalizeCompanyName(decomposed)).isEqualTo("Café Coffee Day");
        }

        @Test
        @DisplayName("Throws JobValidationException if companyName is missing or blank")
        void blankCompanyThrowsException() {
            assertThatThrownBy(() -> normalizer.normalize(validCandidateBuilder().companyName("   ").build()))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("companyName must not be null or blank");

            assertThatThrownBy(() -> normalizer.normalizeCompanyName(null))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("companyName must not be null or blank");

            assertThatThrownBy(() -> normalizer.normalizeCompanyName("   \n\t  "))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("companyName must not be null or blank");
        }
    }

    @Nested
    @DisplayName("D. Location Normalization")
    class LocationNormalizationTests {

        @Test
        @DisplayName("Preserves explicit location and leaves missing location as null without guessing")
        void preservesExplicitLocation() {
            JobIngestionCandidate withLoc = validCandidateBuilder()
                    .location("  Austin,   TX  ")
                    .build();

            JobIngestionCandidate withoutLoc = validCandidateBuilder()
                    .location("   ")
                    .build();

            assertThat(normalizer.normalize(withLoc).location()).isEqualTo("Austin, TX");
            assertThat(normalizer.normalize(withoutLoc).location()).isNull();
            assertThat(normalizer.normalizeLocation("  Austin,   TX  ")).isEqualTo("Austin, TX");
            assertThat(normalizer.normalizeLocation("   ")).isNull();
            assertThat(normalizer.normalizeLocation(null)).isNull();
        }

        @Test
        @DisplayName("Collapses repeated whitespace and preserves commas")
        void collapsesWhitespaceAndPreservesCommas() {
            assertThat(normalizer.normalizeLocation(" Bengaluru,   Karnataka "))
                    .isEqualTo("Bengaluru, Karnataka");
            assertThat(normalizer.normalizeLocation("  San   Francisco,   CA  "))
                    .isEqualTo("San Francisco, CA");
            assertThat(normalizer.normalizeLocation("London,   UK"))
                    .isEqualTo("London, UK");
        }

        @Test
        @DisplayName("Preserves hyphens and remote/hybrid wording")
        void preservesHyphensAndRemoteWording() {
            assertThat(normalizer.normalizeLocation("Remote - India")).isEqualTo("Remote - India");
            assertThat(normalizer.normalizeLocation("  US   -   Remote  ")).isEqualTo("US - Remote");
            assertThat(normalizer.normalizeLocation("Hybrid - London, UK")).isEqualTo("Hybrid - London, UK");
            assertThat(normalizer.normalizeLocation("On-site - New York, NY")).isEqualTo("On-site - New York, NY");
            assertThat(normalizer.normalizeLocation("Winston-Salem, NC")).isEqualTo("Winston-Salem, NC");
        }

        @Test
        @DisplayName("Applies Unicode NFC normalization to locations")
        void appliesUnicodeNormalization() {
            String decomposed = "Zu\u0308rich,   Switzerland";
            assertThat(normalizer.normalizeLocation(decomposed)).isEqualTo("Zürich, Switzerland");
        }

        @Test
        @DisplayName("Does not perform geocoding or infer missing city/state/country")
        void doesNotGeocodeOrInfer() {
            // Does not add coordinates, does not expand "SF" to "San Francisco, California, United States"
            assertThat(normalizer.normalizeLocation("SF")).isEqualTo("SF");
            assertThat(normalizer.normalizeLocation("123 Main St")).isEqualTo("123 Main St");
        }

        @Test
        @DisplayName("Never fabricates placeholders like Unknown or N/A for missing locations")
        void neverFabricatesPlaceholders() {
            assertThat(normalizer.normalizeLocation(null)).isNull();
            assertThat(normalizer.normalizeLocation("")).isNull();
            assertThat(normalizer.normalizeLocation("   \t  ")).isNull();
        }
    }

    @Nested
    @DisplayName("D2. Canonical Normalization Determinism & Invariants")
    class DeterminismTests {

        @Test
        @DisplayName("Normalization produces 100% deterministic identical outputs across repeated executions")
        void deterministicExecution() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .title("   Senior   Cloud    Architect   ")
                    .companyName("   Acme   Global   Systems,   Inc.   ")
                    .location("  Bengaluru,   Karnataka  ")
                    .build();

            NormalizedJobCandidate first = normalizer.normalize(candidate);

            for (int i = 0; i < 50; i++) {
                NormalizedJobCandidate repeated = normalizer.normalize(candidate);
                assertThat(repeated.title()).isEqualTo(first.title());
                assertThat(repeated.companyName()).isEqualTo(first.companyName());
                assertThat(repeated.location()).isEqualTo(first.location());
            }
        }
    }

    @Nested
    @DisplayName("E. Work Mode Normalization")
    class WorkModeNormalizationTests {

        @ParameterizedTest(name = "Maps raw string \"{0}\" to {1}")
        @CsvSource({
                "remote, REMOTE",
                "REMOTE, REMOTE",
                "  hybrid  , HYBRID",
                "onsite, ONSITE",
                "on-site, ONSITE",
                "in-office, ONSITE",
                "office, ONSITE",
                "on site, ONSITE",
                "unknown_string, UNKNOWN",
                "'', UNKNOWN"
        })
        void mapsRawWorkModeStrings(String rawMode, JobWorkMode expected) {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .workMode(null)
                    .rawWorkMode(rawMode)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);
            assertThat(normalized.workMode()).isEqualTo(expected);
        }

        @Test
        @DisplayName("Prefers explicit enum over raw string when valid")
        void prefersExplicitEnum() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .workMode(JobWorkMode.REMOTE)
                    .rawWorkMode("onsite")
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);
            assertThat(normalized.workMode()).isEqualTo(JobWorkMode.REMOTE);
        }
    }

    @Nested
    @DisplayName("F. Employment Type Normalization")
    class EmploymentTypeNormalizationTests {

        @ParameterizedTest(name = "Maps raw string \"{0}\" to {1}")
        @CsvSource({
                "full_time, FULL_TIME",
                "full-time, FULL_TIME",
                "permanent, FULL_TIME",
                "part_time, PART_TIME",
                "part-time, PART_TIME",
                "contract, CONTRACT",
                "contractor, CONTRACT",
                "internship, INTERNSHIP",
                "intern, INTERNSHIP",
                "temporary, TEMPORARY",
                "freelance, FREELANCE",
                "other_type, UNKNOWN"
        })
        void mapsRawEmploymentTypeStrings(String rawType, JobEmploymentType expected) {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .employmentType(null)
                    .rawEmploymentType(rawType)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);
            assertThat(normalized.employmentType()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("G. Experience Range Normalization")
    class ExperienceNormalizationTests {

        @Test
        @DisplayName("Valid non-negative experience range is preserved")
        void validExperiencePreserved() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .experienceMinYears(3)
                    .experienceMaxYears(7)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);
            assertThat(normalized.experienceMinYears()).isEqualTo(3);
            assertThat(normalized.experienceMaxYears()).isEqualTo(7);
        }

        @Test
        @DisplayName("Negative min experience throws JobValidationException")
        void negativeMinExperienceThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder().experienceMinYears(-1).build();
            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("experienceMinYears must not be negative");
        }

        @Test
        @DisplayName("Negative max experience throws JobValidationException")
        void negativeMaxExperienceThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder().experienceMaxYears(-2).build();
            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("experienceMaxYears must not be negative");
        }

        @Test
        @DisplayName("Min experience exceeding max experience throws JobValidationException")
        void minExceedingMaxExperienceThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .experienceMinYears(8)
                    .experienceMaxYears(4)
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("must not exceed experienceMaxYears");
        }
    }

    @Nested
    @DisplayName("H. Salary Range Normalization")
    class SalaryNormalizationTests {

        @Test
        @DisplayName("Valid salary range standardizes currency to uppercase and preserves period")
        void validSalaryNormalized() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .salaryMin(BigDecimal.valueOf(120000))
                    .salaryMax(BigDecimal.valueOf(150000))
                    .salaryCurrency("  inr  ")
                    .rawSalaryPeriod("yearly")
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.salaryMin()).isEqualByComparingTo(BigDecimal.valueOf(120000));
            assertThat(normalized.salaryMax()).isEqualByComparingTo(BigDecimal.valueOf(150000));
            assertThat(normalized.salaryCurrency()).isEqualTo("INR");
            assertThat(normalized.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
        }

        @Test
        @DisplayName("Missing salary remains null without converting to zero")
        void missingSalaryRemainsNull() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .salaryMin(null)
                    .salaryMax(null)
                    .salaryCurrency(null)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.salaryMin()).isNull();
            assertThat(normalized.salaryMax()).isNull();
            assertThat(normalized.salaryCurrency()).isNull();
        }

        @Test
        @DisplayName("Negative salary min throws JobValidationException")
        void negativeSalaryMinThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .salaryMin(BigDecimal.valueOf(-100))
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("salaryMin must not be negative");
        }

        @Test
        @DisplayName("Salary min exceeding salary max throws JobValidationException")
        void salaryMinExceedingMaxThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .salaryMin(BigDecimal.valueOf(200000))
                    .salaryMax(BigDecimal.valueOf(150000))
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("must not exceed salaryMax");
        }
    }

    @Nested
    @DisplayName("I. URL Normalization")
    class UrlNormalizationTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "http://company.com/jobs/1",
                "https://careers.google.com/jobs/results/123",
                "  https://linkedin.com/jobs/view/555  "
        })
        @DisplayName("Valid HTTP and HTTPS URLs are trimmed and accepted")
        void validUrlsAccepted(String url) {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .jobUrl(url)
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);
            assertThat(normalized.jobUrl()).isEqualTo(url.trim());
        }

        @Test
        @DisplayName("Invalid URL scheme throws JobValidationException")
        void invalidSchemeThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .jobUrl("ftp://files.example.com/job.pdf")
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("must use HTTP or HTTPS scheme");
        }

        @Test
        @DisplayName("URL without host throws JobValidationException")
        void missingHostThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .jobUrl("https:///jobs/view/1")
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("must specify a valid host");
        }

        @Test
        @DisplayName("Malformed URL syntax throws JobValidationException")
        void malformedUrlThrowsException() {
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .jobUrl("https://")
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("malformed URL");
        }
    }

    @Nested
    @DisplayName("J. Date Chronology Normalization")
    class DateNormalizationTests {

        @Test
        @DisplayName("expiresAt preceding postedAt throws JobValidationException")
        void expiresAtPrecedingPostedAtThrowsException() {
            Instant now = Instant.now();
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .postedAt(now)
                    .expiresAt(now.minus(1, ChronoUnit.HOURS))
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot precede postedAt");
        }

        @Test
        @DisplayName("lastSeenAt preceding discoveredAt throws JobValidationException")
        void lastSeenAtPrecedingDiscoveredAtThrowsException() {
            Instant now = Instant.now();
            JobIngestionCandidate candidate = validCandidateBuilder()
                    .discoveredAt(now)
                    .lastSeenAt(now.minus(1, ChronoUnit.MINUTES))
                    .build();

            assertThatThrownBy(() -> normalizer.normalize(candidate))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot precede discoveredAt");
        }
    }

    @Nested
    @DisplayName("K. Interoperability with JobCandidate")
    class JobCandidateInteroperabilityTests {

        @Test
        @DisplayName("Normalizing a legacy JobCandidate produces consistent NormalizedJobCandidate")
        void normalizesJobCandidate() {
            JobCandidate candidate = JobCandidate.builder()
                    .source(JobSource.CUTSHORT)
                    .externalJobId("cs-555")
                    .title("  Principal Engineer  ")
                    .companyName("  SaaS Corp  ")
                    .build();

            NormalizedJobCandidate normalized = normalizer.normalize(candidate);

            assertThat(normalized.source()).isEqualTo(JobSource.CUTSHORT);
            assertThat(normalized.externalJobId()).isEqualTo("cs-555");
            assertThat(normalized.title()).isEqualTo("Principal Engineer");
            assertThat(normalized.companyName()).isEqualTo("SaaS Corp");
        }
    }
}

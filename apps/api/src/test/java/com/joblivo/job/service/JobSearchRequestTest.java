package com.joblivo.job.service;

import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
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

@DisplayName("JobSearchRequest Unit Tests")
class JobSearchRequestTest {

    @Nested
    @DisplayName("Default Values and Builders")
    class DefaultTests {

        @Test
        @DisplayName("Default builder produces canonical defaults")
        void defaultValues() {
            JobSearchRequest request = JobSearchRequest.builder().build();

            assertThat(request.page()).isEqualTo(0);
            assertThat(request.size()).isEqualTo(20);
            assertThat(request.sort()).isEqualTo("newest");
            assertThat(request.keyword()).isNull();
            assertThat(request.location()).isNull();
            assertThat(request.workMode()).isNull();
            assertThat(request.employmentType()).isNull();
            assertThat(request.source()).isNull();
            assertThat(request.minExperienceYears()).isNull();
            assertThat(request.maxExperienceYears()).isNull();
            assertThat(request.minSalary()).isNull();
            assertThat(request.maxSalary()).isNull();
            assertThat(request.postedAfter()).isNull();
            assertThat(request.postedBefore()).isNull();
        }

        @Test
        @DisplayName("Alias getters return identical values")
        void aliasGetters() {
            JobSearchRequest request = JobSearchRequest.builder()
                    .minimumExperience(3)
                    .maximumExperience(7)
                    .minimumSalary(BigDecimal.valueOf(80000))
                    .maximumSalary(BigDecimal.valueOf(150000))
                    .build();

            assertThat(request.minExperienceYears()).isEqualTo(3);
            assertThat(request.minimumExperience()).isEqualTo(3);
            assertThat(request.maxExperienceYears()).isEqualTo(7);
            assertThat(request.maximumExperience()).isEqualTo(7);
            assertThat(request.minSalary()).isEqualByComparingTo("80000");
            assertThat(request.minimumSalary()).isEqualByComparingTo("80000");
            assertThat(request.maxSalary()).isEqualByComparingTo("150000");
            assertThat(request.maximumSalary()).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("toCriteria accurately maps all fields")
        void toCriteriaMapping() {
            Instant now = Instant.now();
            JobSearchRequest request = JobSearchRequest.builder()
                    .keyword("Backend Engineer")
                    .location("Remote")
                    .workMode(JobWorkMode.REMOTE)
                    .employmentType(JobEmploymentType.FULL_TIME)
                    .source(JobSource.LINKEDIN)
                    .minExperienceYears(2)
                    .maxExperienceYears(5)
                    .minSalary(BigDecimal.valueOf(100000))
                    .maxSalary(BigDecimal.valueOf(180000))
                    .postedAfter(now.minus(7, ChronoUnit.DAYS))
                    .postedBefore(now)
                    .page(1)
                    .size(50)
                    .sort("company")
                    .build();

            JobSearchCriteria criteria = request.toCriteria();

            assertThat(criteria.keyword()).isEqualTo("Backend Engineer");
            assertThat(criteria.location()).isEqualTo("Remote");
            assertThat(criteria.workMode()).isEqualTo(JobWorkMode.REMOTE);
            assertThat(criteria.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(criteria.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(criteria.minimumExperience()).isEqualTo(2);
            assertThat(criteria.minExperienceYears()).isEqualTo(2);
            assertThat(criteria.maximumExperience()).isEqualTo(5);
            assertThat(criteria.maxExperienceYears()).isEqualTo(5);
            assertThat(criteria.minimumSalary()).isEqualByComparingTo("100000");
            assertThat(criteria.minSalary()).isEqualByComparingTo("100000");
            assertThat(criteria.maximumSalary()).isEqualByComparingTo("180000");
            assertThat(criteria.maxSalary()).isEqualByComparingTo("180000");
            assertThat(criteria.postedAfter()).isEqualTo(now.minus(7, ChronoUnit.DAYS));
            assertThat(criteria.postedBefore()).isEqualTo(now);
            assertThat(criteria.page()).isEqualTo(1);
            assertThat(criteria.size()).isEqualTo(50);
            assertThat(criteria.sort()).isEqualTo("company");
        }
    }

    @Nested
    @DisplayName("Pagination Validation")
    class PaginationTests {

        @Test
        @DisplayName("Page 0 is valid (boundary)")
        void pageZeroIsValid() {
            JobSearchRequest request = JobSearchRequest.builder().page(0).build();
            assertThat(request.page()).isEqualTo(0);
        }

        @Test
        @DisplayName("Positive page is valid")
        void positivePageIsValid() {
            JobSearchRequest request = JobSearchRequest.builder().page(10).build();
            assertThat(request.page()).isEqualTo(10);
        }

        @Test
        @DisplayName("Negative page throws JobValidationException")
        void negativePageThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().page(-1).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Page index cannot be negative");
        }

        @Test
        @DisplayName("Size 1 is valid (lower boundary)")
        void sizeOneIsValid() {
            JobSearchRequest request = JobSearchRequest.builder().size(1).build();
            assertThat(request.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Size 100 is valid (upper boundary)")
        void sizeHundredIsValid() {
            JobSearchRequest request = JobSearchRequest.builder().size(100).build();
            assertThat(request.size()).isEqualTo(100);
        }

        @Test
        @DisplayName("Size 0 throws JobValidationException")
        void sizeZeroThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().size(0).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Page size must be between 1 and 100");
        }

        @Test
        @DisplayName("Negative size throws JobValidationException")
        void negativeSizeThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().size(-5).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Page size must be between 1 and 100");
        }

        @Test
        @DisplayName("Size > 100 throws JobValidationException")
        void sizeGreaterThanHundredThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().size(101).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Page size must be between 1 and 100");
        }
    }

    @Nested
    @DisplayName("Keyword Validation & Normalization")
    class KeywordTests {

        @Test
        @DisplayName("Valid keyword is preserved")
        void validKeywordPreserved() {
            JobSearchRequest request = JobSearchRequest.builder().keyword("Software Engineer").build();
            assertThat(request.keyword()).isEqualTo("Software Engineer");
        }

        @Test
        @DisplayName("Whitespace around keyword is trimmed")
        void keywordWhitespaceTrimmed() {
            JobSearchRequest request = JobSearchRequest.builder().keyword("   Senior Java Developer   ").build();
            assertThat(request.keyword()).isEqualTo("Senior Java Developer");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n  "})
        @DisplayName("Blank/whitespace keyword normalizes to null")
        void blankKeywordNormalizesToNull(String blank) {
            JobSearchRequest request = JobSearchRequest.builder().keyword(blank).build();
            assertThat(request.keyword()).isNull();
        }

        @Test
        @DisplayName("Keyword of exactly 200 characters is valid (boundary)")
        void keywordExactly200CharsIsValid() {
            String keyword200 = "a".repeat(200);
            JobSearchRequest request = JobSearchRequest.builder().keyword(keyword200).build();
            assertThat(request.keyword()).isEqualTo(keyword200);
        }

        @Test
        @DisplayName("Keyword > 200 characters throws JobValidationException")
        void keywordGreaterThan200CharsThrows() {
            String keyword201 = "a".repeat(201);
            assertThatThrownBy(() -> JobSearchRequest.builder().keyword(keyword201).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Keyword length cannot exceed 200 characters");
        }

        @Test
        @DisplayName("SQL-like input is preserved safely as plain text")
        void sqlLikeInputIsSafePlainText() {
            String malicious = "'; DROP TABLE jobs; SELECT * FROM users WHERE '1'='1";
            JobSearchRequest request = JobSearchRequest.builder().keyword(malicious).build();
            assertThat(request.keyword()).isEqualTo(malicious);
        }
    }

    @Nested
    @DisplayName("Location Validation & Normalization")
    class LocationTests {

        @Test
        @DisplayName("Valid location is preserved")
        void validLocationPreserved() {
            JobSearchRequest request = JobSearchRequest.builder().location("New York, NY").build();
            assertThat(request.location()).isEqualTo("New York, NY");
        }

        @Test
        @DisplayName("Whitespace around location is trimmed")
        void locationWhitespaceTrimmed() {
            JobSearchRequest request = JobSearchRequest.builder().location("   Austin, TX   ").build();
            assertThat(request.location()).isEqualTo("Austin, TX");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t  \n"})
        @DisplayName("Blank/whitespace location normalizes to null")
        void blankLocationNormalizesToNull(String blank) {
            JobSearchRequest request = JobSearchRequest.builder().location(blank).build();
            assertThat(request.location()).isNull();
        }

        @Test
        @DisplayName("Location of exactly 200 characters is valid (boundary)")
        void locationExactly200CharsIsValid() {
            String loc200 = "b".repeat(200);
            JobSearchRequest request = JobSearchRequest.builder().location(loc200).build();
            assertThat(request.location()).isEqualTo(loc200);
        }

        @Test
        @DisplayName("Location > 200 characters throws JobValidationException")
        void locationGreaterThan200CharsThrows() {
            String loc201 = "b".repeat(201);
            assertThatThrownBy(() -> JobSearchRequest.builder().location(loc201).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Location length cannot exceed 200 characters");
        }
    }

    @Nested
    @DisplayName("Experience Validation")
    class ExperienceTests {

        @Test
        @DisplayName("Valid experience range is accepted")
        void validExperienceRange() {
            JobSearchRequest request = JobSearchRequest.builder()
                    .minExperienceYears(2)
                    .maxExperienceYears(8)
                    .build();
            assertThat(request.minExperienceYears()).isEqualTo(2);
            assertThat(request.maxExperienceYears()).isEqualTo(8);
        }

        @Test
        @DisplayName("Experience 0 is valid (boundary)")
        void experienceZeroBoundary() {
            JobSearchRequest request = JobSearchRequest.builder()
                    .minExperienceYears(0)
                    .maxExperienceYears(0)
                    .build();
            assertThat(request.minExperienceYears()).isEqualTo(0);
            assertThat(request.maxExperienceYears()).isEqualTo(0);
        }

        @Test
        @DisplayName("Negative min experience throws JobValidationException")
        void negativeMinExperienceThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().minExperienceYears(-1).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Minimum experience cannot be negative");
        }

        @Test
        @DisplayName("Negative max experience throws JobValidationException")
        void negativeMaxExperienceThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().maxExperienceYears(-1).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Maximum experience cannot be negative");
        }

        @Test
        @DisplayName("minExperience > maxExperience throws JobValidationException")
        void minExperienceGreaterThanMaxThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder()
                    .minExperienceYears(5)
                    .maxExperienceYears(2)
                    .build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Minimum experience (5) cannot exceed maximum experience (2)");
        }
    }

    @Nested
    @DisplayName("Salary Validation")
    class SalaryTests {

        @Test
        @DisplayName("Valid salary range is accepted")
        void validSalaryRange() {
            JobSearchRequest request = JobSearchRequest.builder()
                    .minSalary(BigDecimal.valueOf(60000))
                    .maxSalary(BigDecimal.valueOf(120000))
                    .build();
            assertThat(request.minSalary()).isEqualByComparingTo("60000");
            assertThat(request.maxSalary()).isEqualByComparingTo("120000");
        }

        @Test
        @DisplayName("Salary 0 is valid (boundary)")
        void salaryZeroBoundary() {
            JobSearchRequest request = JobSearchRequest.builder()
                    .minSalary(BigDecimal.ZERO)
                    .maxSalary(BigDecimal.ZERO)
                    .build();
            assertThat(request.minSalary()).isEqualByComparingTo("0");
            assertThat(request.maxSalary()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Negative min salary throws JobValidationException")
        void negativeMinSalaryThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().minSalary(BigDecimal.valueOf(-1)).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Minimum salary cannot be negative");
        }

        @Test
        @DisplayName("Negative max salary throws JobValidationException")
        void negativeMaxSalaryThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder().maxSalary(BigDecimal.valueOf(-1)).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Maximum salary cannot be negative");
        }

        @Test
        @DisplayName("minSalary > maxSalary throws JobValidationException")
        void minSalaryGreaterThanMaxThrows() {
            assertThatThrownBy(() -> JobSearchRequest.builder()
                    .minSalary(BigDecimal.valueOf(150000))
                    .maxSalary(BigDecimal.valueOf(90000))
                    .build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Minimum salary (150000) cannot exceed maximum salary (90000)");
        }
    }

    @Nested
    @DisplayName("Date Range Validation")
    class DateRangeTests {

        @Test
        @DisplayName("Valid date range is accepted")
        void validDateRange() {
            Instant before = Instant.now();
            Instant after = before.minus(30, ChronoUnit.DAYS);

            JobSearchRequest request = JobSearchRequest.builder()
                    .postedAfter(after)
                    .postedBefore(before)
                    .build();

            assertThat(request.postedAfter()).isEqualTo(after);
            assertThat(request.postedBefore()).isEqualTo(before);
        }

        @Test
        @DisplayName("postedAfter equal to postedBefore is valid (boundary)")
        void equalDatesBoundary() {
            Instant instant = Instant.now();

            JobSearchRequest request = JobSearchRequest.builder()
                    .postedAfter(instant)
                    .postedBefore(instant)
                    .build();

            assertThat(request.postedAfter()).isEqualTo(instant);
            assertThat(request.postedBefore()).isEqualTo(instant);
        }

        @Test
        @DisplayName("postedAfter > postedBefore throws JobValidationException")
        void postedAfterAfterPostedBeforeThrows() {
            Instant now = Instant.now();
            Instant future = now.plus(1, ChronoUnit.DAYS);

            assertThatThrownBy(() -> JobSearchRequest.builder()
                    .postedAfter(future)
                    .postedBefore(now)
                    .build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot be after postedBefore");
        }
    }

    @Nested
    @DisplayName("Sort Parameter Validation")
    class SortTests {

        @ParameterizedTest
        @ValueSource(strings = {"newest", "oldest", "company", "title", "relevance", "freshness", "NEWEST", "Company", "RELEVANCE", "FRESHNESS"})
        @DisplayName("Allowlisted sort keys are accepted and normalized")
        void allowlistedSortKeys(String sortKey) {
            JobSearchRequest request = JobSearchRequest.builder().sort(sortKey).build();
            assertThat(request.sort()).isEqualTo(sortKey.trim().toLowerCase());
        }

        @ParameterizedTest
        @ValueSource(strings = {"popularity", "salary", "rating", "id", "random", "select *", "'; drop table jobs;--"})
        @DisplayName("Non-allowlisted or injection sort keys throw JobValidationException")
        void invalidSortKeysThrow(String invalidSort) {
            assertThatThrownBy(() -> JobSearchRequest.builder().sort(invalidSort).build())
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Invalid sort parameter '" + invalidSort + "'");
        }

        @Test
        @DisplayName("Null or blank sort defaults to newest")
        void nullOrBlankSortDefaultsToNewest() {
            JobSearchRequest r1 = JobSearchRequest.builder().sort(null).build();
            assertThat(r1.sort()).isEqualTo("newest");

            JobSearchRequest r2 = JobSearchRequest.builder().sort("   ").build();
            assertThat(r2.sort()).isEqualTo("newest");
        }
    }

    @Nested
    @DisplayName("Enum Parameter Support")
    class EnumTests {

        @Test
        @DisplayName("WorkMode, EmploymentType, and Source enum values are preserved")
        void enumValuesPreserved() {
            JobSearchRequest request = JobSearchRequest.builder()
                    .workMode(JobWorkMode.HYBRID)
                    .employmentType(JobEmploymentType.CONTRACT)
                    .source(JobSource.ATS)
                    .build();

            assertThat(request.workMode()).isEqualTo(JobWorkMode.HYBRID);
            assertThat(request.employmentType()).isEqualTo(JobEmploymentType.CONTRACT);
            assertThat(request.source()).isEqualTo(JobSource.ATS);
        }
    }
}

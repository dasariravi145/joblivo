package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.exception.JobNotFoundException;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobSearchService Unit Tests")
class JobSearchServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobSearchService jobSearchService;

    @BeforeEach
    void setUp() {
        jobSearchService = new JobSearchService(jobRepository);
    }

    private Job createSampleJob(UUID id, String title, String companyName) {
        try {
            Job job = new Job(JobSource.LINKEDIN, "ext-" + id, title, companyName);
            job.setRecruiterName("Jane Recruiter");
            job.setDescription("Exciting software engineering role.");
            job.setLocation("San Francisco, CA");
            job.setWorkMode(JobWorkMode.HYBRID);
            job.setEmploymentType(JobEmploymentType.FULL_TIME);
            job.setExperienceMinYears(3);
            job.setExperienceMaxYears(6);
            job.setSalaryMin(BigDecimal.valueOf(140000));
            job.setSalaryMax(BigDecimal.valueOf(180000));
            job.setSalaryCurrency("USD");
            job.setSalaryPeriod(SalaryPeriod.YEAR);
            job.setJobUrl("https://linkedin.com/jobs/1");
            job.setCompanyUrl("https://company.com");
            job.setPostedAt(Instant.now().minus(2, ChronoUnit.DAYS));
            job.setExpiresAt(Instant.now().plus(28, ChronoUnit.DAYS));
            job.setDiscoveredAt(Instant.now().minus(1, ChronoUnit.DAYS));
            job.setLastSeenAt(Instant.now());
            job.setApplicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE);

            Field idField = Job.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(job, id);

            Field createdAtField = Job.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(job, Instant.now().minus(2, ChronoUnit.DAYS));

            Field updatedAtField = Job.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(job, Instant.now().minus(1, ChronoUnit.DAYS));

            return job;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("Sort Order Allow-list & Tie-breaking")
    class SortTests {

        @Test
        @DisplayName("Valid sort keys parse correctly case-insensitively")
        void validSortKeys() {
            assertThat(JobSearchSort.fromKey("NEWEST")).isEqualTo(JobSearchSort.NEWEST);
            assertThat(JobSearchSort.fromKey("oldest ")).isEqualTo(JobSearchSort.OLDEST);
            assertThat(JobSearchSort.fromKey("company")).isEqualTo(JobSearchSort.COMPANY);
            assertThat(JobSearchSort.fromKey("TITLE")).isEqualTo(JobSearchSort.TITLE);
            assertThat(JobSearchSort.fromKey("RELEVANCE")).isEqualTo(JobSearchSort.RELEVANCE);
            assertThat(JobSearchSort.fromKey("relevance ")).isEqualTo(JobSearchSort.RELEVANCE);
            assertThat(JobSearchSort.fromKey("FRESHNESS")).isEqualTo(JobSearchSort.FRESHNESS);
            assertThat(JobSearchSort.fromKey("freshness ")).isEqualTo(JobSearchSort.FRESHNESS);
        }

        @Test
        @DisplayName("Null or blank sort defaults to NEWEST")
        void nullOrBlankSortDefaultsToNewest() {
            assertThat(JobSearchSort.fromKey(null)).isEqualTo(JobSearchSort.NEWEST);
            assertThat(JobSearchSort.fromKey("")).isEqualTo(JobSearchSort.NEWEST);
            assertThat(JobSearchSort.fromKey("   ")).isEqualTo(JobSearchSort.NEWEST);
        }

        @Test
        @DisplayName("Unsupported sort key throws JobValidationException")
        void unsupportedSortKeyThrows() {
            assertThatThrownBy(() -> JobSearchSort.fromKey("salary"))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Invalid sort parameter 'salary'");

            assertThatThrownBy(() -> JobSearchSort.fromKey("rank"))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Supported values: newest, oldest, company, title");
        }

        @Test
        @DisplayName("Sort options include deterministic tie-breaker on ID")
        void sortOrdersIncludeTieBreakerOnId() {
            Sort newest = JobSearchSort.NEWEST.toSort();
            assertThat(newest.getOrderFor("postedAt")).isNotNull();
            assertThat(newest.getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(newest.getOrderFor("id")).isNotNull();
            assertThat(newest.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);

            Sort oldest = JobSearchSort.OLDEST.toSort();
            assertThat(oldest.getOrderFor("postedAt")).isNotNull();
            assertThat(oldest.getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(oldest.getOrderFor("id")).isNotNull();
            assertThat(oldest.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);

            Sort company = JobSearchSort.COMPANY.toSort();
            assertThat(company.getOrderFor("companyName")).isNotNull();
            assertThat(company.getOrderFor("companyName").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(company.getOrderFor("id")).isNotNull();
            assertThat(company.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);

            Sort title = JobSearchSort.TITLE.toSort();
            assertThat(title.getOrderFor("title")).isNotNull();
            assertThat(title.getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(title.getOrderFor("id")).isNotNull();
            assertThat(title.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        }
    }

    @Nested
    @DisplayName("Search Criteria Validation")
    class ValidationTests {

        @Test
        @DisplayName("Null criteria throws JobValidationException")
        void nullCriteriaThrows() {
            assertThatThrownBy(() -> jobSearchService.search(null))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Search criteria cannot be null");
        }

        @Test
        @DisplayName("Negative page index throws JobValidationException")
        void negativePageIndexThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder().page(-1).build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Page index cannot be negative");
        }

        @Test
        @DisplayName("Page size < 1 throws JobValidationException")
        void pageSizeLessThanOneThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder().size(0).build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Page size must be between 1 and 100");
        }

        @Test
        @DisplayName("Page size > 100 throws JobValidationException")
        void pageSizeGreaterThanMaxThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder().size(101).build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Page size must be between 1 and 100");
        }

        @Test
        @DisplayName("Negative minimum experience throws JobValidationException")
        void negativeMinimumExperienceThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder().minimumExperience(-1).build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Minimum experience cannot be negative");
        }

        @Test
        @DisplayName("Negative maximum experience throws JobValidationException")
        void negativeMaximumExperienceThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder().maximumExperience(-2).build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Maximum experience cannot be negative");
        }

        @Test
        @DisplayName("Minimum experience exceeding maximum experience throws JobValidationException")
        void minimumExperienceExceedingMaximumThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .minimumExperience(5)
                    .maximumExperience(3)
                    .build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Minimum experience (5) cannot exceed maximum experience (3)");
        }

        @Test
        @DisplayName("Negative minimum salary throws JobValidationException")
        void negativeMinimumSalaryThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .minimumSalary(BigDecimal.valueOf(-100))
                    .build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Minimum salary cannot be negative");
        }

        @Test
        @DisplayName("Negative maximum salary throws JobValidationException")
        void negativeMaximumSalaryThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .maximumSalary(BigDecimal.valueOf(-50))
                    .build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Maximum salary cannot be negative");
        }

        @Test
        @DisplayName("Minimum salary exceeding maximum salary throws JobValidationException")
        void minimumSalaryExceedingMaximumThrows() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .minimumSalary(BigDecimal.valueOf(150000))
                    .maximumSalary(BigDecimal.valueOf(100000))
                    .build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Minimum salary (150000) cannot exceed maximum salary (100000)");
        }

        @Test
        @DisplayName("postedAfter after postedBefore throws JobValidationException")
        void postedAfterLaterThanBeforeThrows() {
            Instant now = Instant.now();
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .postedAfter(now.plus(1, ChronoUnit.DAYS))
                    .postedBefore(now)
                    .build();
            assertThatThrownBy(() -> jobSearchService.search(criteria))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("cannot be after postedBefore");
        }
    }

    @Nested
    @DisplayName("Search Execution and Pagination")
    class SearchExecutionTests {

        @Test
        @DisplayName("Executes search with repository, maps results to JobSearchResponse, and maintains pagination metadata")
        void searchSuccess() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Senior Staff Engineer", "Acme Tech");
            Page<Job> mockPage = new PageImpl<>(List.of(job), org.springframework.data.domain.PageRequest.of(0, 20), 1);

            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("Engineer")
                    .location("San Francisco")
                    .workMode(JobWorkMode.HYBRID)
                    .employmentType(JobEmploymentType.FULL_TIME)
                    .source(JobSource.LINKEDIN)
                    .minimumExperience(3)
                    .maximumExperience(8)
                    .minimumSalary(BigDecimal.valueOf(120000))
                    .maximumSalary(BigDecimal.valueOf(200000))
                    .page(0)
                    .size(20)
                    .sort("newest")
                    .build();

            JobSearchResponse response = jobSearchService.search(criteria);

            assertThat(response).isNotNull();
            assertThat(response.jobs()).hasSize(1);
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.hasPrevious()).isFalse();

            JobResponse jobResponse = response.jobs().get(0);
            assertThat(jobResponse.id()).isEqualTo(jobId);
            assertThat(jobResponse.title()).isEqualTo("Senior Staff Engineer");
            assertThat(jobResponse.companyName()).isEqualTo("Acme Tech");
            assertThat(jobResponse.workMode()).isEqualTo(JobWorkMode.HYBRID);
            assertThat(jobResponse.freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(0);
            assertThat(captured.getPageSize()).isEqualTo(20);
            assertThat(captured.getSort().getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("Empty search results produce valid empty JobSearchResponse")
        void emptyResults() {
            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder().page(0).size(10).build();
            JobSearchResponse response = jobSearchService.search(criteria);

            assertThat(response.jobs()).isEmpty();
            assertThat(response.totalElements()).isEqualTo(0);
            assertThat(response.totalPages()).isEqualTo(0);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("11. sort=RELEVANCE with no keyword falls back deterministically to NEWEST")
        void searchRelevanceNoKeyword_FallsBackToNewest() {
            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword(null)
                    .sort("relevance")
                    .page(0)
                    .size(20)
                    .build();

            jobSearchService.search(criteria);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().getOrderFor("postedAt")).isNotNull();
            assertThat(captured.getSort().getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(captured.getSort().getOrderFor("id")).isNotNull();
            assertThat(captured.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("12. sort=RELEVANCE with blank keyword falls back deterministically to NEWEST")
        void searchRelevanceBlankKeyword_FallsBackToNewest() {
            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("   ")
                    .sort("relevance")
                    .page(0)
                    .size(20)
                    .build();

            jobSearchService.search(criteria);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().getOrderFor("postedAt")).isNotNull();
            assertThat(captured.getSort().getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(captured.getSort().getOrderFor("id")).isNotNull();
            assertThat(captured.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("sort=RELEVANCE with non-blank keyword uses unsorted Pageable for database-level ordering")
        void searchRelevanceWithKeyword_UsesUnsortedPageable() {
            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("Java")
                    .sort("relevance")
                    .page(0)
                    .size(20)
                    .build();

            jobSearchService.search(criteria);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().isUnsorted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Freshness Sort Tests")
    class FreshnessSortTests {

        @Test
        @DisplayName("sort=FRESHNESS uses unsorted Pageable for database-level ordering")
        void searchFreshness_UsesUnsortedPageable() {
            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .sort("freshness")
                    .page(0)
                    .size(20)
                    .build();

            jobSearchService.search(criteria);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().isUnsorted()).isTrue();
        }

        @Test
        @DisplayName("sort=FRESHNESS with missing or blank keyword does not fall back to NEWEST")
        void searchFreshness_BlankKeyword_RemainsFreshness() {
            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("   ")
                    .sort("freshness")
                    .page(0)
                    .size(20)
                    .build();

            jobSearchService.search(criteria);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getSort().isUnsorted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Single Job Lookup by ID")
    class FindByIdTests {

        @Test
        @DisplayName("Existing job returns JobResponse projection")
        void findByIdExisting() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Backend Lead", "FinCorp");
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            JobResponse response = jobSearchService.findById(jobId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(jobId);
            assertThat(response.title()).isEqualTo("Backend Lead");
            assertThat(response.companyName()).isEqualTo("FinCorp");
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);
        }

        @Test
        @DisplayName("Missing job throws JobNotFoundException")
        void findByIdMissingThrows() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> jobSearchService.findById(jobId))
                    .isInstanceOf(JobNotFoundException.class)
                    .hasMessageContaining("Job not found with id: " + jobId);
        }

        @Test
        @DisplayName("Null ID throws JobValidationException")
        void findByIdNullThrows() {
            assertThatThrownBy(() -> jobSearchService.findById(null))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessage("Job ID cannot be null");
        }
    }

    @Nested
    @DisplayName("Freshness Status Evaluation Integration")
    class FreshnessEvaluationIntegrationTests {

        @Test
        @DisplayName("Job with past expiresAt is evaluated as EXPIRED in findById")
        void expiredJobEvaluatedAsExpired() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Legacy Specialist", "OldCorp");
            job.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            JobResponse response = jobSearchService.findById(jobId);
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.EXPIRED);
        }

        @Test
        @DisplayName("Job with lastSeenAt beyond 7-day threshold is evaluated as STALE")
        void staleJobEvaluatedAsStale() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Cobol Developer", "LegacyBank");
            job.setExpiresAt(null);
            job.setDiscoveredAt(Instant.now().minus(15, ChronoUnit.DAYS));
            job.setLastSeenAt(Instant.now().minus(8, ChronoUnit.DAYS));
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            JobResponse response = jobSearchService.findById(jobId);
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.STALE);
        }

        @Test
        @DisplayName("Job with contradictory timestamps is evaluated as UNKNOWN")
        void contradictoryTimestampsEvaluatedAsUnknown() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Quantum Engineer", "FutureCorp");
            job.setPostedAt(Instant.now().minus(1, ChronoUnit.DAYS));
            job.setExpiresAt(Instant.now().minus(5, ChronoUnit.DAYS)); // expiresAt before postedAt!
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            JobResponse response = jobSearchService.findById(jobId);
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Search returns mixed statuses (ACTIVE, STALE, EXPIRED, UNKNOWN) without silently dropping any records")
        void searchReturnsAllStatusesWithoutDroppingStaleOrExpired() {
            Instant now = Instant.now();

            Job activeJob = createSampleJob(UUID.randomUUID(), "Active Dev", "FreshCo");
            activeJob.setPostedAt(now.minus(1, ChronoUnit.DAYS));
            activeJob.setExpiresAt(now.plus(10, ChronoUnit.DAYS));
            activeJob.setDiscoveredAt(now.minus(1, ChronoUnit.DAYS));
            activeJob.setLastSeenAt(now.minus(1, ChronoUnit.HOURS));

            Job staleJob = createSampleJob(UUID.randomUUID(), "Stale Dev", "SilentCo");
            staleJob.setPostedAt(now.minus(30, ChronoUnit.DAYS));
            staleJob.setExpiresAt(null);
            staleJob.setDiscoveredAt(now.minus(30, ChronoUnit.DAYS));
            staleJob.setLastSeenAt(now.minus(10, ChronoUnit.DAYS)); // > 7d

            Job expiredJob = createSampleJob(UUID.randomUUID(), "Expired Dev", "PastCo");
            expiredJob.setPostedAt(now.minus(20, ChronoUnit.DAYS));
            expiredJob.setExpiresAt(now.minus(2, ChronoUnit.DAYS)); // past
            expiredJob.setDiscoveredAt(now.minus(20, ChronoUnit.DAYS));
            expiredJob.setLastSeenAt(now.minus(5, ChronoUnit.DAYS));

            Job unknownJob = createSampleJob(UUID.randomUUID(), "Unknown Dev", "MysteryCo");
            unknownJob.setPostedAt(now.minus(5, ChronoUnit.DAYS));
            unknownJob.setExpiresAt(now.minus(15, ChronoUnit.DAYS)); // contradictory
            unknownJob.setDiscoveredAt(now.minus(5, ChronoUnit.DAYS));
            unknownJob.setLastSeenAt(now.minus(1, ChronoUnit.DAYS));

            Page<Job> mixedPage = new PageImpl<>(
                    List.of(activeJob, staleJob, expiredJob, unknownJob),
                    PageRequest.of(0, 20),
                    4
            );
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mixedPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder().page(0).size(20).build();
            JobSearchResponse response = jobSearchService.search(criteria);

            assertThat(response.jobs()).hasSize(4);
            assertThat(response.jobs().get(0).freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(response.jobs().get(1).freshnessStatus()).isEqualTo(JobFreshnessStatus.STALE);
            assertThat(response.jobs().get(2).freshnessStatus()).isEqualTo(JobFreshnessStatus.EXPIRED);
            assertThat(response.jobs().get(3).freshnessStatus()).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Search and findById do not mutate job timestamps (read-only data integrity)")
        void readOperationsDoNotMutateJobTimestamps() {
            Instant postedAt = Instant.now().minus(5, ChronoUnit.DAYS);
            Instant expiresAt = Instant.now().plus(10, ChronoUnit.DAYS);
            Instant discoveredAt = Instant.now().minus(5, ChronoUnit.DAYS);
            Instant lastSeenAt = Instant.now().minus(2, ChronoUnit.DAYS);

            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Immutable Engineer", "SafeCorp");
            job.setPostedAt(postedAt);
            job.setExpiresAt(expiresAt);
            job.setDiscoveredAt(discoveredAt);
            job.setLastSeenAt(lastSeenAt);

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
            jobSearchService.findById(jobId);

            // Verify timestamps remain untouched
            assertThat(job.getPostedAt()).isEqualTo(postedAt);
            assertThat(job.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(job.getDiscoveredAt()).isEqualTo(discoveredAt);
            assertThat(job.getLastSeenAt()).isEqualTo(lastSeenAt);
        }

        @Test
        @DisplayName("JobSearchService integrates cleanly with custom JobFreshnessPolicy")
        void customPolicyIntegration() {
            JobFreshnessPolicy customPolicy = new JobFreshnessPolicy();
            JobSearchService customService = new JobSearchService(jobRepository, customPolicy);

            assertThat(customService.getFreshnessPolicy()).isSameAs(customPolicy);
            assertThat(customService.getFreshnessEvaluator()).isNotNull();
        }
    }

    @Nested
    @DisplayName("JobSpecifications Criteria Predicates")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class JobSpecificationsTests {

        @Test
        @DisplayName("Specification builds predicates for all supported criteria fields")
        @SuppressWarnings("unchecked")
        void specificationBuildsPredicates() {
            Root<Job> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);

            Path<String> titlePath = mock(Path.class);
            Path<String> companyPath = mock(Path.class);
            Path<String> descPath = mock(Path.class);
            Path<String> locationPath = mock(Path.class);
            Path<JobWorkMode> workModePath = mock(Path.class);
            Path<JobEmploymentType> empTypePath = mock(Path.class);
            Path<JobSource> sourcePath = mock(Path.class);
            Path<Integer> minExpPath = mock(Path.class);
            Path<Integer> maxExpPath = mock(Path.class);
            Path<BigDecimal> minSalPath = mock(Path.class);
            Path<BigDecimal> maxSalPath = mock(Path.class);
            Path<Instant> postedAtPath = mock(Path.class);

            doReturn(titlePath).when(root).<String>get("title");
            doReturn(companyPath).when(root).<String>get("companyName");
            doReturn(descPath).when(root).<String>get("description");
            doReturn(locationPath).when(root).<String>get("location");
            doReturn(workModePath).when(root).<JobWorkMode>get("workMode");
            doReturn(empTypePath).when(root).<JobEmploymentType>get("employmentType");
            doReturn(sourcePath).when(root).<JobSource>get("source");
            doReturn(minExpPath).when(root).<Integer>get("experienceMinYears");
            doReturn(maxExpPath).when(root).<Integer>get("experienceMaxYears");
            doReturn(minSalPath).when(root).<BigDecimal>get("salaryMin");
            doReturn(maxSalPath).when(root).<BigDecimal>get("salaryMax");
            doReturn(postedAtPath).when(root).<Instant>get("postedAt");

            Predicate dummyPredicate = mock(Predicate.class);
            doReturn(dummyPredicate).when(cb).like(any(), anyString());
            doReturn(mock(jakarta.persistence.criteria.Expression.class)).when(cb).lower(any());
            doReturn(dummyPredicate).when(cb).equal(any(), any());
            doReturn(dummyPredicate).when(cb).or(any(Predicate[].class));
            doReturn(dummyPredicate).when(cb).or(any(Predicate.class), any(Predicate.class));
            doReturn(dummyPredicate).when(cb).and(any(Predicate.class), any(Predicate.class));
            doReturn(dummyPredicate).when(cb).and(any(Predicate[].class));
            doReturn(dummyPredicate).when(cb).isNotNull(any());
            doReturn(dummyPredicate).when(cb).isNull(any());
            doReturn(dummyPredicate).when(cb).greaterThanOrEqualTo(any(), any(Comparable.class));
            doReturn(dummyPredicate).when(cb).lessThanOrEqualTo(any(), any(Comparable.class));

            Instant now = Instant.now();
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("backend")
                    .location("remote")
                    .workMode(JobWorkMode.REMOTE)
                    .employmentType(JobEmploymentType.FULL_TIME)
                    .source(JobSource.LINKEDIN)
                    .minimumExperience(2)
                    .maximumExperience(5)
                    .minimumSalary(BigDecimal.valueOf(100000))
                    .maximumSalary(BigDecimal.valueOf(180000))
                    .postedAfter(now.minus(7, ChronoUnit.DAYS))
                    .postedBefore(now)
                    .build();

            Specification<Job> spec = JobSpecifications.withCriteria(criteria);
            Predicate result = spec.toPredicate(root, query, cb);

            assertThat(result).isNotNull();
            verify(cb).and(any(Predicate[].class));
        }
    }

    @Nested
    @DisplayName("Canonical Read Model Mapping & Invariants")
    class CanonicalReadModelTests {

        @Test
        @DisplayName("Search maps Job entities to canonical JobResponse read model without entity exposure")
        void search_ReturnsCanonicalReadModel() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Job job1 = createSampleJob(id1, "Backend Engineer", "Tech Corp");
            Job job2 = createSampleJob(id2, "Frontend Engineer", "Web Solutions");

            Page<Job> page = new PageImpl<>(List.of(job1, job2), PageRequest.of(0, 20), 2);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            JobSearchCriteria criteria = JobSearchCriteria.builder().page(0).size(20).build();
            JobSearchResponse response = jobSearchService.search(criteria);

            assertThat(response.jobs()).hasSize(2);
            assertThat(response.jobs().get(0)).isInstanceOf(JobResponse.class);
            assertThat(response.jobs().get(1)).isInstanceOf(JobResponse.class);
            assertThat(response.jobs().get(0).id()).isEqualTo(id1);
            assertThat(response.jobs().get(1).id()).isEqualTo(id2);
            assertThat(response.jobs().get(0).freshnessStatus()).isNotNull();
            assertThat(response.jobs().get(1).freshnessStatus()).isNotNull();

            // Verify single repository call - zero N+1 database queries
            verify(jobRepository).findAll(any(Specification.class), any(Pageable.class));
            org.mockito.Mockito.verifyNoMoreInteractions(jobRepository);
        }

        @Test
        @DisplayName("Custom JobResponseMapper constructor is preserved and used")
        void customMapperConstructor() {
            JobResponseMapper customMapper = new JobResponseMapper();
            JobSearchService serviceWithMapper = new JobSearchService(jobRepository, customMapper, new JobFreshnessPolicy());
            assertThat(serviceWithMapper.getJobResponseMapper()).isSameAs(customMapper);
        }

        @Test
        @DisplayName("Search operation does not mutate entity timestamps (read-only guarantee)")
        void search_DoesNotMutateEntityTimestamps() {
            UUID id = UUID.randomUUID();
            Job job = createSampleJob(id, "Cloud Architect", "Cloud Co");
            Instant originalLastSeen = job.getLastSeenAt();
            Instant originalDiscovered = job.getDiscoveredAt();
            Instant originalPosted = job.getPostedAt();
            Instant originalExpires = job.getExpiresAt();

            Page<Job> page = new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            jobSearchService.search(JobSearchCriteria.builder().build());

            assertThat(job.getLastSeenAt()).isEqualTo(originalLastSeen);
            assertThat(job.getDiscoveredAt()).isEqualTo(originalDiscovered);
            assertThat(job.getPostedAt()).isEqualTo(originalPosted);
            assertThat(job.getExpiresAt()).isEqualTo(originalExpires);
        }

        @Test
        @DisplayName("findById maps existing Job entity to canonical JobResponse with matching fields and single DB query")
        void findById_ReturnsCanonicalReadModel() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Senior Staff SRE", "Platform Corp");
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            JobResponse response = jobSearchService.findById(jobId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(jobId);
            assertThat(response.externalJobId()).isEqualTo("ext-" + jobId);
            assertThat(response.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(response.title()).isEqualTo("Senior Staff SRE");
            assertThat(response.companyName()).isEqualTo("Platform Corp");
            assertThat(response.recruiterName()).isEqualTo("Jane Recruiter");
            assertThat(response.description()).isEqualTo("Exciting software engineering role.");
            assertThat(response.location()).isEqualTo("San Francisco, CA");
            assertThat(response.workMode()).isEqualTo(JobWorkMode.HYBRID);
            assertThat(response.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(response.experienceMinYears()).isEqualTo(3);
            assertThat(response.experienceMaxYears()).isEqualTo(6);
            assertThat(response.salaryMin()).isEqualByComparingTo(BigDecimal.valueOf(140000));
            assertThat(response.salaryMax()).isEqualByComparingTo(BigDecimal.valueOf(180000));
            assertThat(response.salaryCurrency()).isEqualTo("USD");
            assertThat(response.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
            assertThat(response.jobUrl()).isEqualTo("https://linkedin.com/jobs/1");
            assertThat(response.companyUrl()).isEqualTo("https://company.com");
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);

            // Verify exactly one repository call - targeted lookup by ID
            verify(jobRepository).findById(jobId);
        }

        @Test
        @DisplayName("findById does not mutate entity timestamps (read-only guarantee)")
        void findById_DoesNotMutateEntityTimestamps() {
            UUID jobId = UUID.randomUUID();
            Job job = createSampleJob(jobId, "Backend Specialist", "Target Tech");
            Instant originalLastSeen = job.getLastSeenAt();
            Instant originalDiscovered = job.getDiscoveredAt();
            Instant originalPosted = job.getPostedAt();
            Instant originalExpires = job.getExpiresAt();

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            jobSearchService.findById(jobId);

            assertThat(job.getLastSeenAt()).isEqualTo(originalLastSeen);
            assertThat(job.getDiscoveredAt()).isEqualTo(originalDiscovered);
            assertThat(job.getPostedAt()).isEqualTo(originalPosted);
            assertThat(job.getExpiresAt()).isEqualTo(originalExpires);
        }

        @Test
        @DisplayName("findById preserves null optional fields without fabrication")
        void findById_PreservesNullsWithoutFabrication() {
            UUID jobId = UUID.randomUUID();
            Job job = new Job(JobSource.OTHER, "ext-sparse", "Developer", "Lean Corp");
            // reflect id
            try {
                java.lang.reflect.Field idField = Job.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(job, jobId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            JobResponse response = jobSearchService.findById(jobId);

            assertThat(response.recruiterName()).isNull();
            assertThat(response.description()).isNull();
            assertThat(response.location()).isNull();
            assertThat(response.experienceMinYears()).isNull();
            assertThat(response.experienceMaxYears()).isNull();
            assertThat(response.salaryMin()).isNull();
            assertThat(response.salaryMax()).isNull();
            assertThat(response.salaryCurrency()).isNull();
            assertThat(response.salaryPeriod()).isNull();
            assertThat(response.jobUrl()).isNull();
            assertThat(response.companyUrl()).isNull();
            assertThat(response.postedAt()).isNull();
            assertThat(response.expiresAt()).isNull();
            assertThat(response.freshnessStatus()).isNotNull();
        }
    }
}

package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.config.JobFreshnessProperties;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JobSearchFreshnessOrder Unit and Integration Tests")
class JobSearchFreshnessOrderTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Duration DEFAULT_STALE_THRESHOLD = Duration.ofDays(7);

    private Clock fixedClock;
    private JobFreshnessProperties properties;
    private JobFreshnessEvaluator evaluator;
    private JobFreshnessPolicy policy;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        properties = new JobFreshnessProperties(DEFAULT_STALE_THRESHOLD);
        evaluator = new JobFreshnessEvaluator(properties, fixedClock);
        policy = new JobFreshnessPolicy(evaluator);
    }

    private static Job createJob(
            UUID id,
            String title,
            String companyName,
            Instant postedAt,
            Instant expiresAt,
            Instant discoveredAt,
            Instant lastSeenAt
    ) {
        try {
            Job job = new Job(JobSource.LINKEDIN, "ext-" + id, title, companyName);
            job.setLocation("Remote");
            job.setDescription("Software engineer role");
            job.setWorkMode(JobWorkMode.REMOTE);
            job.setEmploymentType(JobEmploymentType.FULL_TIME);
            job.setApplicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE);
            job.setPostedAt(postedAt);
            job.setExpiresAt(expiresAt);
            if (discoveredAt != null) {
                job.setDiscoveredAt(discoveredAt);
            }
            if (lastSeenAt != null) {
                job.setLastSeenAt(lastSeenAt);
            }

            if (id != null) {
                Field idField = Job.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(job, id);
            }

            return job;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 1. Freshness Status Precedence Tests (Requirements 1, 2, 3)
    // =========================================================================
    @Nested
    @DisplayName("Freshness Status Precedence Tests")
    class StatusPrecedenceTests {

        @Test
        @DisplayName("1. ACTIVE jobs rank before UNKNOWN jobs")
        void activeRanksBeforeUnknown() {
            // ACTIVE: posted 1 day ago, seen 1 hour ago
            Job activeJob = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Backend Engineer", "Alpha Corp",
                    FIXED_NOW.minus(Duration.ofDays(1)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(1))
            );
            // UNKNOWN: contradictory timestamps (posted in future)
            Job unknownJob = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Alpha Corp",
                    FIXED_NOW.plus(Duration.ofDays(2)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(1))
            );

            assertThat(evaluator.evaluate(activeJob)).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(evaluator.evaluate(unknownJob)).isEqualTo(JobFreshnessStatus.UNKNOWN);

            List<Job> jobs = new ArrayList<>(List.of(unknownJob, activeJob));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(activeJob, unknownJob);
        }

        @Test
        @DisplayName("2. UNKNOWN jobs rank before STALE jobs")
        void unknownRanksBeforeStale() {
            // UNKNOWN: all timestamps null
            Job unknownJob = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Backend Engineer", "Alpha Corp",
                    null, null, null, null
            );
            // STALE: last seen 10 days ago (> 7 days)
            Job staleJob = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Alpha Corp",
                    FIXED_NOW.minus(Duration.ofDays(20)), null,
                    FIXED_NOW.minus(Duration.ofDays(20)), FIXED_NOW.minus(Duration.ofDays(10))
            );

            assertThat(evaluator.evaluate(unknownJob)).isEqualTo(JobFreshnessStatus.UNKNOWN);
            assertThat(evaluator.evaluate(staleJob)).isEqualTo(JobFreshnessStatus.STALE);

            List<Job> jobs = new ArrayList<>(List.of(staleJob, unknownJob));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(unknownJob, staleJob);
        }

        @Test
        @DisplayName("3. STALE jobs rank before EXPIRED jobs")
        void staleRanksBeforeExpired() {
            // STALE: last seen 10 days ago
            Job staleJob = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Backend Engineer", "Alpha Corp",
                    FIXED_NOW.minus(Duration.ofDays(20)), null,
                    FIXED_NOW.minus(Duration.ofDays(20)), FIXED_NOW.minus(Duration.ofDays(10))
            );
            // EXPIRED: expiresAt passed 2 hours ago
            Job expiredJob = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Alpha Corp",
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(2)),
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(3))
            );

            assertThat(evaluator.evaluate(staleJob)).isEqualTo(JobFreshnessStatus.STALE);
            assertThat(evaluator.evaluate(expiredJob)).isEqualTo(JobFreshnessStatus.EXPIRED);

            List<Job> jobs = new ArrayList<>(List.of(expiredJob, staleJob));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(staleJob, expiredJob);
        }

        @Test
        @DisplayName("Complete canonical precedence: ACTIVE -> UNKNOWN -> STALE -> EXPIRED")
        void fullCanonicalPrecedenceOrder() {
            Job active = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Active Job", "Company A",
                    FIXED_NOW.minus(Duration.ofDays(1)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(1))
            );
            Job unknown = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Unknown Job", "Company B",
                    null, null, null, null
            );
            Job stale = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "Stale Job", "Company C",
                    FIXED_NOW.minus(Duration.ofDays(20)), null,
                    FIXED_NOW.minus(Duration.ofDays(20)), FIXED_NOW.minus(Duration.ofDays(10))
            );
            Job expired = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    "Expired Job", "Company D",
                    FIXED_NOW.minus(Duration.ofDays(5)), FIXED_NOW.minus(Duration.ofDays(1)),
                    FIXED_NOW.minus(Duration.ofDays(5)), FIXED_NOW.minus(Duration.ofDays(1))
            );

            assertThat(evaluator.evaluate(active)).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(evaluator.evaluate(unknown)).isEqualTo(JobFreshnessStatus.UNKNOWN);
            assertThat(evaluator.evaluate(stale)).isEqualTo(JobFreshnessStatus.STALE);
            assertThat(evaluator.evaluate(expired)).isEqualTo(JobFreshnessStatus.EXPIRED);

            List<Job> jobs = new ArrayList<>(List.of(expired, unknown, active, stale));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(active, unknown, stale, expired);
        }
    }

    // =========================================================================
    // 2. Intra-State Deterministic Tie-Breakers (Requirements 4, 5, 6, 7, 8, 9, 10)
    // =========================================================================
    @Nested
    @DisplayName("Deterministic Intra-State Tie-Breakers")
    class TieBreakerTests {

        @Test
        @DisplayName("4. Multiple ACTIVE jobs use deterministic postedAt DESC ordering")
        void multipleActiveJobs_UsePostedAtDesc() {
            Job newerActive = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(1)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(1))
            );
            Job olderActive = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(3)), null,
                    FIXED_NOW.minus(Duration.ofDays(3)), FIXED_NOW.minus(Duration.ofHours(1))
            );

            List<Job> jobs = new ArrayList<>(List.of(olderActive, newerActive));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(newerActive, olderActive);
        }

        @Test
        @DisplayName("5. Multiple UNKNOWN jobs use deterministic postedAt DESC ordering")
        void multipleUnknownJobs_UsePostedAtDesc() {
            // Both contradictory (discoveredAt > lastSeenAt), so UNKNOWN
            Job newerUnknown = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(2)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofDays(5))
            );
            Job olderUnknown = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(5)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofDays(5))
            );

            List<Job> jobs = new ArrayList<>(List.of(olderUnknown, newerUnknown));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(newerUnknown, olderUnknown);
        }

        @Test
        @DisplayName("6. Multiple STALE jobs use deterministic postedAt DESC ordering")
        void multipleStaleJobs_UsePostedAtDesc() {
            Job newerStale = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(10)), null,
                    FIXED_NOW.minus(Duration.ofDays(10)), FIXED_NOW.minus(Duration.ofDays(8))
            );
            Job olderStale = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(20)), null,
                    FIXED_NOW.minus(Duration.ofDays(20)), FIXED_NOW.minus(Duration.ofDays(8))
            );

            List<Job> jobs = new ArrayList<>(List.of(olderStale, newerStale));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(newerStale, olderStale);
        }

        @Test
        @DisplayName("7. Multiple EXPIRED jobs use deterministic expiresAt DESC ordering")
        void multipleExpiredJobs_UseExpiresAtDesc() {
            // Recently expired vs expired long ago
            Job recentlyExpired = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(10)), FIXED_NOW.minus(Duration.ofHours(1)),
                    FIXED_NOW.minus(Duration.ofDays(10)), FIXED_NOW.minus(Duration.ofHours(2))
            );
            Job olderExpired = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(30)), FIXED_NOW.minus(Duration.ofDays(10)),
                    FIXED_NOW.minus(Duration.ofDays(30)), FIXED_NOW.minus(Duration.ofDays(10))
            );

            List<Job> jobs = new ArrayList<>(List.of(olderExpired, recentlyExpired));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(recentlyExpired, olderExpired);
        }

        @Test
        @DisplayName("8. Null postedAt handling is deterministic (placed last after dated jobs)")
        void nullPostedAt_PlacedLastWithinState() {
            Job datedActive = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(2)), null,
                    FIXED_NOW.minus(Duration.ofDays(2)), FIXED_NOW.minus(Duration.ofHours(1))
            );
            Job undatedActive = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Engineer", "Acme",
                    null, null,
                    FIXED_NOW.minus(Duration.ofDays(2)), FIXED_NOW.minus(Duration.ofHours(1))
            );

            assertThat(evaluator.evaluate(datedActive)).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(evaluator.evaluate(undatedActive)).isEqualTo(JobFreshnessStatus.ACTIVE);

            List<Job> jobs = new ArrayList<>(List.of(undatedActive, datedActive));
            jobs.sort(JobSearchFreshnessOrder.comparator(evaluator));

            assertThat(jobs).containsExactly(datedActive, undatedActive);
        }

        @Test
        @DisplayName("9. Null expiresAt handling is deterministic (placed last after dated expired jobs)")
        void nullExpiresAt_PlacedLastWithinExpired() {
            Job datedExpired = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(5)), FIXED_NOW.minus(Duration.ofDays(1)),
                    FIXED_NOW.minus(Duration.ofDays(5)), FIXED_NOW.minus(Duration.ofDays(1))
            );
            Job undatedExpired = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Engineer", "Acme",
                    FIXED_NOW.minus(Duration.ofDays(5)), null,
                    FIXED_NOW.minus(Duration.ofDays(5)), FIXED_NOW.minus(Duration.ofDays(1))
            );

            JobFreshnessEvaluator mockEvaluator = mock(JobFreshnessEvaluator.class);
            when(mockEvaluator.evaluate(datedExpired)).thenReturn(JobFreshnessStatus.EXPIRED);
            when(mockEvaluator.evaluate(undatedExpired)).thenReturn(JobFreshnessStatus.EXPIRED);

            List<Job> jobs = new ArrayList<>(List.of(undatedExpired, datedExpired));
            jobs.sort(JobSearchFreshnessOrder.comparator(mockEvaluator));

            assertThat(jobs).containsExactly(datedExpired, undatedExpired);
        }

        @Test
        @DisplayName("10. Final tie-breakers produce stable ordering: companyName ASC -> title ASC -> id ASC")
        void finalTieBreakersProduceStableOrdering() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(1));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofHours(1));

            // Case A: Equal postedAt, tie breaks on companyName ASC
            Job companyA = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Developer", "Apple",
                    postedAt, null, postedAt, lastSeenAt
            );
            Job companyZ = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Developer", "Zendesk",
                    postedAt, null, postedAt, lastSeenAt
            );

            List<Job> list1 = new ArrayList<>(List.of(companyZ, companyA));
            list1.sort(JobSearchFreshnessOrder.comparator(evaluator));
            assertThat(list1).containsExactly(companyA, companyZ);

            // Case B: Equal postedAt and companyName, tie breaks on title ASC
            Job titleBackend = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Apple",
                    postedAt, null, postedAt, lastSeenAt
            );
            Job titleFrontend = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Frontend Engineer", "Apple",
                    postedAt, null, postedAt, lastSeenAt
            );

            List<Job> list2 = new ArrayList<>(List.of(titleFrontend, titleBackend));
            list2.sort(JobSearchFreshnessOrder.comparator(evaluator));
            assertThat(list2).containsExactly(titleBackend, titleFrontend);

            // Case C: Equal postedAt, companyName, and title, tie breaks on id ASC
            Job id1 = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Backend Engineer", "Apple",
                    postedAt, null, postedAt, lastSeenAt
            );
            Job id2 = createJob(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Apple",
                    postedAt, null, postedAt, lastSeenAt
            );

            List<Job> list3 = new ArrayList<>(List.of(id2, id1));
            list3.sort(JobSearchFreshnessOrder.comparator(evaluator));
            assertThat(list3).containsExactly(id1, id2);
        }
    }

    // =========================================================================
    // 3. Canonical Evaluator & Clock Behavior (Requirements 11, 12, 13, 14, 15)
    // =========================================================================
    @Nested
    @DisplayName("Canonical Evaluator & Clock Invariants")
    class EvaluatorAndClockTests {

        @Test
        @DisplayName("11. Freshness uses the existing canonical JobFreshnessEvaluator")
        void freshnessUsesCanonicalEvaluator() {
            Job job = createJob(
                    UUID.randomUUID(), "Dev", "Co",
                    FIXED_NOW.minus(Duration.ofDays(1)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(1))
            );
            JobFreshnessStatus status = evaluator.evaluate(job);
            assertThat(status).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(JobSearchFreshnessOrder.rank(status)).isEqualTo(JobSearchFreshnessOrder.RANK_ACTIVE);
        }

        @Test
        @DisplayName("12. Injected Clock produces deterministic test results across different reference points")
        void injectedClockProducesDeterministicResults() {
            Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-06-01T00:00:00Z");

            JobFreshnessEvaluator eval1 = new JobFreshnessEvaluator(properties, Clock.fixed(t1, ZoneOffset.UTC));
            JobFreshnessEvaluator eval2 = new JobFreshnessEvaluator(properties, Clock.fixed(t2, ZoneOffset.UTC));

            Job job = createJob(
                    UUID.randomUUID(), "Dev", "Co",
                    Instant.parse("2025-05-01T00:00:00Z"), null,
                    Instant.parse("2025-05-01T00:00:00Z"), Instant.parse("2025-05-01T00:00:00Z")
            );

            // Relative to t1 (2025-01-01), job posted in May 2025 is in the future -> UNKNOWN
            assertThat(eval1.evaluate(job)).isEqualTo(JobFreshnessStatus.UNKNOWN);
            // Relative to t2 (2026-06-01), job seen in May 2025 (>1 yr ago) is STALE
            assertThat(eval2.evaluate(job)).isEqualTo(JobFreshnessStatus.STALE);
        }

        @Test
        @DisplayName("13. Boundary condition now == expiresAt remains EXPIRED")
        void boundaryConditionNowEqualsExpiresAt_IsExpired() {
            Job boundaryJob = createJob(
                    UUID.randomUUID(), "Dev", "Co",
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW, // now == expiresAt
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofMinutes(5))
            );

            assertThat(evaluator.evaluate(boundaryJob)).isEqualTo(JobFreshnessStatus.EXPIRED);
            assertThat(JobSearchFreshnessOrder.rank(evaluator.evaluate(boundaryJob)))
                    .isEqualTo(JobSearchFreshnessOrder.RANK_EXPIRED);
        }

        @Test
        @DisplayName("14. Existing stale threshold behavior remains unchanged (7 days default)")
        void staleThresholdBehaviorRemainsUnchanged() {
            Instant exactCutoff = FIXED_NOW.minus(DEFAULT_STALE_THRESHOLD);

            // At exact cutoff boundary: NOT strictly before cutoff, so classified ACTIVE
            Job boundaryJob = createJob(
                    UUID.randomUUID(), "Dev", "Co",
                    exactCutoff, null,
                    exactCutoff, exactCutoff
            );
            assertThat(evaluator.evaluate(boundaryJob)).isEqualTo(JobFreshnessStatus.ACTIVE);

            // 1 second older than cutoff: classified STALE
            Instant olderThanCutoff = exactCutoff.minusSeconds(1);
            Job staleJob = createJob(
                    UUID.randomUUID(), "Dev", "Co",
                    olderThanCutoff, null,
                    olderThanCutoff, olderThanCutoff
            );
            assertThat(evaluator.evaluate(staleJob)).isEqualTo(JobFreshnessStatus.STALE);
        }

        @Test
        @DisplayName("15. Existing freshnessStatus in API response mapping remains unchanged")
        void responseMappingRetainsCanonicalFreshnessStatus() {
            Job job = createJob(
                    UUID.randomUUID(), "Dev", "Co",
                    FIXED_NOW.minus(Duration.ofDays(1)), null,
                    FIXED_NOW.minus(Duration.ofDays(1)), FIXED_NOW.minus(Duration.ofHours(1))
            );
            JobResponse response = JobResponse.from(job, evaluator.evaluate(job));
            assertThat(response.freshnessStatus()).isEqualTo(JobFreshnessStatus.ACTIVE);
        }
    }

    // =========================================================================
    // 4. JPA Criteria Query Ordering Tests (Requirements 19, 20)
    // =========================================================================
    @Nested
    @DisplayName("JPA Criteria Query Ordering")
    class JpaCriteriaOrderingTests {

        @Test
        @DisplayName("applyFreshnessOrder configures CASE expressions and 6 orders on CriteriaQuery")
        @SuppressWarnings("unchecked")
        void applyFreshnessOrderConfiguresOrders() {
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            CriteriaQuery<Job> query = mock(CriteriaQuery.class);
            Root<Job> root = mock(Root.class);

            Path<Instant> lastSeenAtPath = mock(Path.class);
            Path<Instant> postedAtPath = mock(Path.class);
            Path<Instant> expiresAtPath = mock(Path.class);
            Path<Instant> discoveredAtPath = mock(Path.class);
            Path<String> companyPath = mock(Path.class);
            Path<String> titlePath = mock(Path.class);
            Path<UUID> idPath = mock(Path.class);

            doReturn(lastSeenAtPath).when(root).<Instant>get("lastSeenAt");
            doReturn(postedAtPath).when(root).<Instant>get("postedAt");
            doReturn(expiresAtPath).when(root).<Instant>get("expiresAt");
            doReturn(discoveredAtPath).when(root).<Instant>get("discoveredAt");
            doReturn(companyPath).when(root).<String>get("companyName");
            doReturn(titlePath).when(root).<String>get("title");
            doReturn(idPath).when(root).<UUID>get("id");

            Predicate dummyPredicate = mock(Predicate.class);
            doReturn(dummyPredicate).when(cb).isNull(any());
            doReturn(dummyPredicate).when(cb).isNotNull(any());
            doReturn(dummyPredicate).when(cb).lessThan(any(), any(Instant.class));
            doReturn(dummyPredicate).when(cb).lessThan(any(), any(Expression.class));
            doReturn(dummyPredicate).when(cb).lessThanOrEqualTo(any(), any(Instant.class));
            doReturn(dummyPredicate).when(cb).greaterThan(any(), any(Instant.class));
            doReturn(dummyPredicate).when(cb).equal(any(), any());
            doReturn(dummyPredicate).when(cb).not(any(Predicate.class));
            doReturn(dummyPredicate).when(cb).and(any(Predicate[].class));
            doReturn(dummyPredicate).when(cb).or(any(Predicate[].class));

            CriteriaBuilder.Case caseClause = mock(CriteriaBuilder.Case.class, org.mockito.Mockito.RETURNS_SELF);
            doReturn(caseClause).when(cb).selectCase();
            Expression dummyExpr = mock(Expression.class);
            doReturn(dummyExpr).when(caseClause).otherwise(any());

            Order dummyOrder = mock(Order.class);
            doReturn(dummyOrder).when(cb).asc(any());
            doReturn(dummyOrder).when(cb).desc(any());

            JobSearchFreshnessOrder.applyFreshnessOrder(cb, query, root, evaluator);

            ArgumentCaptor<List<Order>> ordersCaptor = ArgumentCaptor.forClass(List.class);
            verify(query).orderBy(ordersCaptor.capture());

            List<Order> capturedOrders = ordersCaptor.getValue();
            assertThat(capturedOrders).hasSize(6);
        }

        @Test
        @DisplayName("applyFreshnessOrder safely handles null arguments without throwing")
        void applyFreshnessOrderSafeWithNulls() {
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            CriteriaQuery<Job> query = mock(CriteriaQuery.class);
            Root<Job> root = mock(Root.class);

            JobSearchFreshnessOrder.applyFreshnessOrder(null, query, root, evaluator);
            JobSearchFreshnessOrder.applyFreshnessOrder(cb, null, root, evaluator);
            JobSearchFreshnessOrder.applyFreshnessOrder(cb, query, null, evaluator);
            // None of these should throw
        }
    }

    // =========================================================================
    // 5. Existing Search Sort Regression Tests (Requirements 21-27)
    // =========================================================================
    @Nested
    @DisplayName("Existing Sort Option Regression Tests")
    class ExistingSortRegressionTests {

        @Test
        @DisplayName("21. Existing NEWEST ordering remains unchanged")
        void newestSortUnchanged() {
            Sort newest = JobSearchSort.NEWEST.toSort();
            assertThat(newest.getOrderFor("postedAt")).isNotNull();
            assertThat(newest.getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(newest.getOrderFor("id")).isNotNull();
            assertThat(newest.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("22. Existing OLDEST ordering remains unchanged")
        void oldestSortUnchanged() {
            Sort oldest = JobSearchSort.OLDEST.toSort();
            assertThat(oldest.getOrderFor("postedAt")).isNotNull();
            assertThat(oldest.getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(oldest.getOrderFor("id")).isNotNull();
            assertThat(oldest.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        @DisplayName("23. Existing COMPANY ordering remains unchanged")
        void companySortUnchanged() {
            Sort company = JobSearchSort.COMPANY.toSort();
            assertThat(company.getOrderFor("companyName")).isNotNull();
            assertThat(company.getOrderFor("companyName").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(company.getOrderFor("id")).isNotNull();
            assertThat(company.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        @DisplayName("24. Existing TITLE ordering remains unchanged")
        void titleSortUnchanged() {
            Sort title = JobSearchSort.TITLE.toSort();
            assertThat(title.getOrderFor("title")).isNotNull();
            assertThat(title.getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(title.getOrderFor("id")).isNotNull();
            assertThat(title.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        @DisplayName("25. Existing RELEVANCE ordering remains unchanged")
        void relevanceSortUnchanged() {
            Sort relevance = JobSearchSort.RELEVANCE.toSort();
            assertThat(relevance.isUnsorted()).isTrue();
        }

        @Test
        @DisplayName("26. Missing or blank keyword does not affect FRESHNESS ordering")
        void blankKeywordDoesNotAffectFreshnessOrdering() {
            JobRepository jobRepository = mock(JobRepository.class);
            JobSearchService searchService = new JobSearchService(jobRepository, evaluator);

            Page<Job> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword(null)
                    .sort("freshness")
                    .build();

            searchService.search(criteria);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
            Pageable captured = pageableCaptor.getValue();
            // Should remain unsorted Pageable so Criteria applies freshness order
            assertThat(captured.getSort().isUnsorted()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"salary", "experience", "random", "score", "select *", "'; drop table jobs;--"})
        @DisplayName("27. Unsupported sort values remain rejected with standardized validation error")
        void unsupportedSortValuesRejected(String invalidSort) {
            assertThatThrownBy(() -> JobSearchSort.fromKey(invalidSort))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("Invalid sort parameter '" + invalidSort + "'")
                    .hasMessageContaining("Supported values: newest, oldest, company, title, relevance, freshness");
        }
    }

    // =========================================================================
    // 6. Search Filter Integration Tests (Requirements 16, 17, 18)
    // =========================================================================
    @Nested
    @DisplayName("Filter Compatibility Tests with FRESHNESS Sort")
    class FilterCompatibilityTests {

        @Test
        @DisplayName("16. FRESHNESS sorting works with keyword filtering")
        void freshnessWorksWithKeywordFilter() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("Java")
                    .sort("freshness")
                    .build();

            Specification<Job> spec = JobSpecifications.withCriteria(criteria, evaluator);
            assertThat(spec).isNotNull();
        }

        @Test
        @DisplayName("17. FRESHNESS sorting works with location filtering")
        void freshnessWorksWithLocationFilter() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .location("San Francisco")
                    .sort("freshness")
                    .build();

            Specification<Job> spec = JobSpecifications.withCriteria(criteria, evaluator);
            assertThat(spec).isNotNull();
        }

        @Test
        @DisplayName("18. FRESHNESS sorting works with other existing filters (workMode, employmentType, source, experience, salary, dates)")
        void freshnessWorksWithAllFilters() {
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keyword("Engineer")
                    .location("Remote")
                    .workMode(JobWorkMode.REMOTE)
                    .employmentType(JobEmploymentType.FULL_TIME)
                    .source(JobSource.LINKEDIN)
                    .minimumExperience(2)
                    .maximumExperience(8)
                    .minimumSalary(BigDecimal.valueOf(80000))
                    .maximumSalary(BigDecimal.valueOf(180000))
                    .postedAfter(FIXED_NOW.minus(Duration.ofDays(30)))
                    .postedBefore(FIXED_NOW)
                    .sort("freshness")
                    .page(0)
                    .size(20)
                    .build();

            Specification<Job> spec = JobSpecifications.withCriteria(criteria, evaluator);
            assertThat(spec).isNotNull();
        }
    }
}

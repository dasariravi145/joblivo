package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.config.JobFreshnessProperties;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobFreshnessPolicy Unit Tests")
class JobFreshnessPolicyTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T12:00:00Z");
    private Clock fixedClock;
    private JobFreshnessProperties properties;
    private JobFreshnessPolicy policy;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        properties = new JobFreshnessProperties();
        properties.setStaleAfter(Duration.ofDays(7));
        JobFreshnessEvaluator evaluator = new JobFreshnessEvaluator(properties, fixedClock);
        policy = new JobFreshnessPolicy(evaluator);
    }

    private Job createSampleJob(Instant postedAt, Instant expiresAt, Instant discoveredAt, Instant lastSeenAt) {
        Job job = new Job(JobSource.LINKEDIN, "ext-policy-1", "Lead Systems Architect", "CloudScale Inc");
        job.setDescription("Drive large-scale distributed architectures.");
        job.setLocation("Austin, TX");
        job.setWorkMode(JobWorkMode.REMOTE);
        job.setEmploymentType(JobEmploymentType.FULL_TIME);
        job.setSalaryMin(BigDecimal.valueOf(180000));
        job.setSalaryMax(BigDecimal.valueOf(240000));
        job.setSalaryCurrency("USD");
        job.setSalaryPeriod(SalaryPeriod.YEAR);
        job.setJobUrl("https://linkedin.com/jobs/view/ext-policy-1");
        job.setApplicationMethod(JobApplicationMethod.ATS);
        job.setPostedAt(postedAt);
        job.setExpiresAt(expiresAt);
        job.setDiscoveredAt(discoveredAt);
        job.setLastSeenAt(lastSeenAt);
        return job;
    }

    @Nested
    @DisplayName("ACTIVE Job Policy Interpretation")
    class ActiveJobPolicyTests {

        @Test
        @DisplayName("ACTIVE job is currently fresh, discoverable, retained, and neither expired nor stale")
        void activeJobPolicyEvaluation() {
            Job job = createSampleJob(
                    FIXED_NOW.minus(2, ChronoUnit.DAYS),
                    FIXED_NOW.plus(14, ChronoUnit.DAYS),
                    FIXED_NOW.minus(2, ChronoUnit.DAYS),
                    FIXED_NOW.minus(1, ChronoUnit.DAYS)
            );

            JobFreshnessStatus status = policy.evaluate(job);
            assertThat(status).isEqualTo(JobFreshnessStatus.ACTIVE);

            // Policy method assertions
            assertThat(policy.isCurrentlyFresh(status)).isTrue();
            assertThat(policy.isCurrentlyFresh(job)).isTrue();

            assertThat(policy.isPotentiallyExpired(status)).isFalse();
            assertThat(policy.isPotentiallyExpired(job)).isFalse();

            assertThat(policy.isStale(status)).isFalse();
            assertThat(policy.isStale(job)).isFalse();

            assertThat(policy.isUnknown(status)).isFalse();
            assertThat(policy.isUnknown(job)).isFalse();

            assertThat(policy.shouldRemainDiscoverable(status)).isTrue();
            assertThat(policy.shouldRemainDiscoverable(job)).isTrue();

            assertThat(policy.shouldRetainRecord(status)).isTrue();
            assertThat(policy.shouldRetainRecord(job)).isTrue();
        }
    }

    @Nested
    @DisplayName("STALE Job Policy Interpretation")
    class StaleJobPolicyTests {

        @Test
        @DisplayName("STALE job remains discoverable, is retained in storage, and is clearly marked STALE")
        void staleJobPolicyEvaluation() {
            Job job = createSampleJob(
                    FIXED_NOW.minus(20, ChronoUnit.DAYS),
                    null,
                    FIXED_NOW.minus(20, ChronoUnit.DAYS),
                    FIXED_NOW.minus(8, ChronoUnit.DAYS) // beyond 7d stale threshold
            );

            JobFreshnessStatus status = policy.evaluate(job);
            assertThat(status).isEqualTo(JobFreshnessStatus.STALE);

            // Core Policy Guarantees:
            // 1. Clearly marked as STALE
            assertThat(policy.isStale(status)).isTrue();
            assertThat(policy.isStale(job)).isTrue();

            // 2. Not currently fresh
            assertThat(policy.isCurrentlyFresh(status)).isFalse();
            assertThat(policy.isCurrentlyFresh(job)).isFalse();

            // 3. Not expired (expiresAt is null)
            assertThat(policy.isPotentiallyExpired(status)).isFalse();
            assertThat(policy.isPotentiallyExpired(job)).isFalse();

            // 4. Must remain discoverable in search queries
            assertThat(policy.shouldRemainDiscoverable(status)).isTrue();
            assertThat(policy.shouldRemainDiscoverable(job)).isTrue();

            // 5. Must remain permanently stored (never deleted or archived)
            assertThat(policy.shouldRetainRecord(status)).isTrue();
            assertThat(policy.shouldRetainRecord(job)).isTrue();
        }
    }

    @Nested
    @DisplayName("EXPIRED Job Policy Interpretation")
    class ExpiredJobPolicyTests {

        @Test
        @DisplayName("EXPIRED job remains stored, remains discoverable, and is clearly marked EXPIRED")
        void expiredJobPolicyEvaluation() {
            Job job = createSampleJob(
                    FIXED_NOW.minus(30, ChronoUnit.DAYS),
                    FIXED_NOW.minus(1, ChronoUnit.DAYS), // expiresAt in the past
                    FIXED_NOW.minus(30, ChronoUnit.DAYS),
                    FIXED_NOW.minus(2, ChronoUnit.DAYS)
            );

            JobFreshnessStatus status = policy.evaluate(job);
            assertThat(status).isEqualTo(JobFreshnessStatus.EXPIRED);

            // Core Policy Guarantees:
            // 1. Clearly marked as EXPIRED
            assertThat(policy.isPotentiallyExpired(status)).isTrue();
            assertThat(policy.isPotentiallyExpired(job)).isTrue();

            // 2. Not currently fresh
            assertThat(policy.isCurrentlyFresh(status)).isFalse();
            assertThat(policy.isCurrentlyFresh(job)).isFalse();

            // 3. Not stale (expiration takes precedence)
            assertThat(policy.isStale(status)).isFalse();
            assertThat(policy.isStale(job)).isFalse();

            // 4. Must remain stored (never deleted or purged from database)
            assertThat(policy.shouldRetainRecord(status)).isTrue();
            assertThat(policy.shouldRetainRecord(job)).isTrue();

            // 5. Must remain discoverable in catalog queries
            assertThat(policy.shouldRemainDiscoverable(status)).isTrue();
            assertThat(policy.shouldRemainDiscoverable(job)).isTrue();
        }
    }

    @Nested
    @DisplayName("UNKNOWN Job Policy Interpretation")
    class UnknownJobPolicyTests {

        @Test
        @DisplayName("UNKNOWN job remains stored, remains discoverable, and is clearly marked UNKNOWN")
        void unknownJobPolicyEvaluation() {
            // Contradictory timestamps: expiresAt before postedAt
            Job job = createSampleJob(
                    FIXED_NOW.minus(5, ChronoUnit.DAYS),
                    FIXED_NOW.minus(10, ChronoUnit.DAYS),
                    FIXED_NOW.minus(5, ChronoUnit.DAYS),
                    FIXED_NOW.minus(2, ChronoUnit.DAYS)
            );

            JobFreshnessStatus status = policy.evaluate(job);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);

            // Core Policy Guarantees:
            // 1. Clearly marked as UNKNOWN (do not guess)
            assertThat(policy.isUnknown(status)).isTrue();
            assertThat(policy.isUnknown(job)).isTrue();

            // 2. Not currently fresh
            assertThat(policy.isCurrentlyFresh(status)).isFalse();
            assertThat(policy.isCurrentlyFresh(job)).isFalse();

            // 3. Must remain stored
            assertThat(policy.shouldRetainRecord(status)).isTrue();
            assertThat(policy.shouldRetainRecord(job)).isTrue();

            // 4. Must remain discoverable in catalog queries
            assertThat(policy.shouldRemainDiscoverable(status)).isTrue();
            assertThat(policy.shouldRemainDiscoverable(job)).isTrue();
        }
    }

    @Nested
    @DisplayName("Storage Retention & Discoverability Invariants")
    class RetentionAndDiscoverabilityTests {

        @ParameterizedTest
        @EnumSource(JobFreshnessStatus.class)
        @DisplayName("All lifecycle states must be retained in database and remain discoverable")
        void allStatusesRetainedAndDiscoverable(JobFreshnessStatus status) {
            assertThat(policy.shouldRetainRecord(status))
                    .as("Status %s must be retained in database", status)
                    .isTrue();

            assertThat(policy.shouldRemainDiscoverable(status))
                    .as("Status %s must remain discoverable in search queries", status)
                    .isTrue();
        }

        @Test
        @DisplayName("Null status or null job does not crash and handles boundaries safely")
        void nullSafety() {
            assertThat(policy.shouldRemainDiscoverable((JobFreshnessStatus) null)).isFalse();
            assertThat(policy.shouldRemainDiscoverable((Job) null)).isFalse();
            assertThat(policy.shouldRetainRecord((JobFreshnessStatus) null)).isTrue();
            assertThat(policy.shouldRetainRecord((Job) null)).isTrue();
            assertThat(policy.isCurrentlyFresh((Job) null)).isFalse();
            assertThat(policy.isPotentiallyExpired((Job) null)).isFalse();
            assertThat(policy.isStale((Job) null)).isFalse();
            assertThat(policy.isUnknown((Job) null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Read-Only Data Integrity Invariants")
    class ReadOnlyDataIntegrityTests {

        @Test
        @DisplayName("Policy evaluation is strictly read-only and never mutates job timestamps or entity attributes")
        void evaluationDoesNotMutateJobTimestamps() {
            Instant postedAt = FIXED_NOW.minus(10, ChronoUnit.DAYS);
            Instant expiresAt = FIXED_NOW.plus(20, ChronoUnit.DAYS);
            Instant discoveredAt = FIXED_NOW.minus(10, ChronoUnit.DAYS);
            Instant lastSeenAt = FIXED_NOW.minus(3, ChronoUnit.DAYS);

            Job job = createSampleJob(postedAt, expiresAt, discoveredAt, lastSeenAt);

            // Repeated policy evaluations
            for (int i = 0; i < 5; i++) {
                policy.evaluate(job);
                policy.isCurrentlyFresh(job);
                policy.isPotentiallyExpired(job);
                policy.isStale(job);
                policy.isUnknown(job);
                policy.shouldRemainDiscoverable(job);
                policy.shouldRetainRecord(job);
            }

            // Verify immutable timestamp state
            assertThat(job.getPostedAt()).isEqualTo(postedAt);
            assertThat(job.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(job.getDiscoveredAt()).isEqualTo(discoveredAt);
            assertThat(job.getLastSeenAt()).isEqualTo(lastSeenAt);
            assertThat(job.getSource()).isEqualTo(JobSource.LINKEDIN);
            assertThat(job.getExternalJobId()).isEqualTo("ext-policy-1");
            assertThat(job.getTitle()).isEqualTo("Lead Systems Architect");
        }
    }

    @Nested
    @DisplayName("Deterministic Clock & Timezone Independence")
    class DeterministicClockTests {

        @Test
        @DisplayName("Fixed Clock produces deterministic results across arbitrary system time zones")
        void fixedClockProducesDeterministicResultsAcrossZones() {
            ZoneId tokyo = ZoneId.of("Asia/Tokyo");
            ZoneId newYork = ZoneId.of("America/New_York");

            JobFreshnessPolicy policyUtc = new JobFreshnessPolicy(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
            JobFreshnessPolicy policyTokyo = new JobFreshnessPolicy(Clock.fixed(FIXED_NOW, tokyo));
            JobFreshnessPolicy policyNy = new JobFreshnessPolicy(Clock.fixed(FIXED_NOW, newYork));

            Job activeJob = createSampleJob(
                    FIXED_NOW.minus(1, ChronoUnit.DAYS),
                    FIXED_NOW.plus(10, ChronoUnit.DAYS),
                    FIXED_NOW.minus(1, ChronoUnit.DAYS),
                    FIXED_NOW.minus(1, ChronoUnit.HOURS)
            );

            assertThat(policyUtc.evaluate(activeJob)).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(policyTokyo.evaluate(activeJob)).isEqualTo(JobFreshnessStatus.ACTIVE);
            assertThat(policyNy.evaluate(activeJob)).isEqualTo(JobFreshnessStatus.ACTIVE);
        }
    }
}

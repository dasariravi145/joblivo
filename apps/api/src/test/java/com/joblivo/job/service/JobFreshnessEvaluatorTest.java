package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.config.JobFreshnessProperties;
import com.joblivo.job.exception.JobConfigurationException;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobFreshnessEvaluator Unit Tests")
class JobFreshnessEvaluatorTest {

    // Anchor time: 2026-09-15T12:00:00Z
    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Duration DEFAULT_STALE_THRESHOLD = Duration.ofDays(7);

    private Clock fixedClock;
    private JobFreshnessProperties properties;
    private JobFreshnessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        properties = new JobFreshnessProperties(DEFAULT_STALE_THRESHOLD);
        evaluator = new JobFreshnessEvaluator(properties, fixedClock);
    }

    private Job createJob(Instant postedAt, Instant expiresAt, Instant discoveredAt, Instant lastSeenAt) {
        Job job = new Job(JobSource.LINKEDIN, "ext-100", "Software Engineer", "Acme Inc");
        job.setPostedAt(postedAt);
        job.setExpiresAt(expiresAt);
        if (discoveredAt != null) {
            job.setDiscoveredAt(discoveredAt);
        }
        if (lastSeenAt != null) {
            job.setLastSeenAt(lastSeenAt);
        }
        return job;
    }

    @Nested
    @DisplayName("Configuration & Validation Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Default stale-after threshold is 7 days")
        void defaultThresholdIsSevenDays() {
            JobFreshnessProperties defaultProps = new JobFreshnessProperties();
            assertThat(defaultProps.getStaleAfter()).isEqualTo(Duration.ofDays(7));
        }

        @Test
        @DisplayName("Positive custom stale duration is accepted")
        void positiveDurationAccepted() {
            JobFreshnessProperties customProps = new JobFreshnessProperties(Duration.ofDays(14));
            assertThat(customProps.getStaleAfter()).isEqualTo(Duration.ofDays(14));
        }

        @Test
        @DisplayName("Negative stale duration throws JobConfigurationException")
        void negativeDurationRejected() {
            assertThatThrownBy(() -> new JobFreshnessProperties(Duration.ofDays(-1)))
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("Zero stale duration throws JobConfigurationException")
        void zeroDurationRejected() {
            assertThatThrownBy(() -> new JobFreshnessProperties(Duration.ZERO))
                    .isInstanceOf(JobConfigurationException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("Null stale duration throws exception")
        void nullDurationRejected() {
            assertThatThrownBy(() -> new JobFreshnessProperties(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Expired Status Tests")
    class ExpiredTests {

        @Test
        @DisplayName("expiresAt in the past -> EXPIRED")
        void expiresAtInPastIsExpired() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(10));
            Instant expiresAt = FIXED_NOW.minus(Duration.ofDays(1)); // yesterday
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(10));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofDays(2));

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.EXPIRED);

            Job job = createJob(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(evaluator.evaluate(job)).isEqualTo(JobFreshnessStatus.EXPIRED);
        }

        @Test
        @DisplayName("expiresAt exactly equal to now -> EXPIRED")
        void expiresAtEqualToNowIsExpired() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(5));
            Instant expiresAt = FIXED_NOW; // exactly now
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(5));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofHours(1));

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.EXPIRED);
        }

        @Test
        @DisplayName("Stale job that also has expiresAt in the past -> EXPIRED (expiration takes precedence)")
        void staleJobWithPastExpirationIsExpired() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(30));
            Instant expiresAt = FIXED_NOW.minus(Duration.ofDays(2)); // expired 2 days ago
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(30));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofDays(15)); // older than 7d stale threshold

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.EXPIRED);
        }
    }

    @Nested
    @DisplayName("Stale Status Tests")
    class StaleTests {

        @Test
        @DisplayName("lastSeenAt just beyond the stale threshold (7 days + 1 second) -> STALE")
        void lastSeenAtJustBeyondThresholdIsStale() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(10));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10)); // unexpired
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(10));
            Instant lastSeenAt = FIXED_NOW.minus(DEFAULT_STALE_THRESHOLD).minusSeconds(1); // 7d + 1s ago

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.STALE);
        }

        @Test
        @DisplayName("Unexpired job with no expiresAt and lastSeenAt older than stale threshold -> STALE")
        void noExpiresAtAndOldLastSeenAtIsStale() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(20));
            Instant expiresAt = null;
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(20));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofDays(8)); // 8 days ago (> 7d)

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.STALE);
        }
    }

    @Nested
    @DisplayName("Active Status Tests")
    class ActiveTests {

        @Test
        @DisplayName("expiresAt in the future + recent lastSeenAt -> ACTIVE")
        void futureExpirationAndRecentLastSeenIsActive() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(14));
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofHours(3)); // 3 hours ago

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.ACTIVE);
        }

        @Test
        @DisplayName("no expiresAt + recent lastSeenAt -> ACTIVE")
        void noExpiresAtAndRecentLastSeenIsActive() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(3));
            Instant expiresAt = null;
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(3));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofDays(1)); // 1 day ago

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.ACTIVE);
        }

        @Test
        @DisplayName("no lastSeenAt + valid recent postedAt -> ACTIVE")
        void noLastSeenAtAndValidRecentPostedAtIsActive() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(2)); // posted 2 days ago
            Instant expiresAt = null;
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant lastSeenAt = null; // absent

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.ACTIVE);
        }

        @Test
        @DisplayName("lastSeenAt exactly at the stale threshold boundary (exactly 7 days ago) -> ACTIVE")
        void lastSeenAtExactlyAtBoundaryIsActive() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(10));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10));
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(10));
            Instant lastSeenAt = FIXED_NOW.minus(DEFAULT_STALE_THRESHOLD); // exactly 7 days ago

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Unknown & Contradictory Timestamps Tests")
    class UnknownTests {

        @Test
        @DisplayName("Insufficient timestamps (all null) -> UNKNOWN")
        void allNullTimestampsIsUnknown() {
            JobFreshnessStatus status = evaluator.evaluate(null, null, null, null);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Null job entity -> UNKNOWN")
        void nullJobEntityIsUnknown() {
            assertThat(evaluator.evaluate((Job) null)).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Only discoveredAt present with no postedAt, expiresAt, or lastSeenAt -> UNKNOWN")
        void onlyDiscoveredAtIsUnknown() {
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
            JobFreshnessStatus status = evaluator.evaluate(null, null, discoveredAt, null);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Only future expiresAt present with no lastSeenAt or postedAt -> UNKNOWN")
        void onlyFutureExpiresAtIsUnknown() {
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10));
            JobFreshnessStatus status = evaluator.evaluate(null, expiresAt, null, null);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Contradictory timestamps: expiresAt before postedAt -> UNKNOWN")
        void expiresAtBeforePostedAtIsUnknown() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant expiresAt = FIXED_NOW.minus(Duration.ofDays(5)); // expires BEFORE posted!
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofHours(1));

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Contradictory timestamps: lastSeenAt before discoveredAt -> UNKNOWN")
        void lastSeenAtBeforeDiscoveredAtIsUnknown() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(5));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(5));
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofDays(4)); // seen BEFORE discovered!

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Contradictory timestamps: postedAt in the future relative to now -> UNKNOWN")
        void postedAtInFutureIsUnknown() {
            Instant postedAt = FIXED_NOW.plus(Duration.ofDays(1)); // future posting date!
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10));
            Instant discoveredAt = FIXED_NOW;
            Instant lastSeenAt = FIXED_NOW;

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Contradictory timestamps: lastSeenAt in the future relative to now -> UNKNOWN")
        void lastSeenAtInFutureIsUnknown() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10));
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant lastSeenAt = FIXED_NOW.plus(Duration.ofHours(2)); // future last seen!

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Contradictory timestamps: discoveredAt in the future relative to now -> UNKNOWN")
        void discoveredAtInFutureIsUnknown() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(2));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10));
            Instant discoveredAt = FIXED_NOW.plus(Duration.ofHours(1)); // future discovery!
            Instant lastSeenAt = FIXED_NOW;

            JobFreshnessStatus status = evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt);
            assertThat(status).isEqualTo(JobFreshnessStatus.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Clock, Determinism & Timezone Tests")
    class ClockAndTimezoneTests {

        @Test
        @DisplayName("Fixed clock produces strictly deterministic results across repeated invocations")
        void fixedClockIsDeterministic() {
            Instant postedAt = FIXED_NOW.minus(Duration.ofDays(1));
            Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(10));
            Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(1));
            Instant lastSeenAt = FIXED_NOW.minus(Duration.ofHours(2));

            for (int i = 0; i < 100; i++) {
                assertThat(evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt))
                        .isEqualTo(JobFreshnessStatus.ACTIVE);
            }
        }

        @Test
        @DisplayName("Evaluation does not depend on JVM default timezone")
        void evaluationIndependentOfJvmTimezone() {
            TimeZone originalDefault = TimeZone.getDefault();
            try {
                // Test across different extreme JVM timezones
                ZoneId[] testZones = {
                        ZoneId.of("Asia/Kolkata"),
                        ZoneId.of("America/Los_Angeles"),
                        ZoneId.of("Pacific/Auckland"),
                        ZoneId.of("Europe/London")
                };

                for (ZoneId zone : testZones) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone));

                    Instant postedAt = FIXED_NOW.minus(Duration.ofDays(2));
                    Instant expiresAt = FIXED_NOW.plus(Duration.ofDays(5));
                    Instant discoveredAt = FIXED_NOW.minus(Duration.ofDays(2));
                    Instant lastSeenAt = FIXED_NOW.minus(Duration.ofHours(1));

                    assertThat(evaluator.evaluate(postedAt, expiresAt, discoveredAt, lastSeenAt))
                            .isEqualTo(JobFreshnessStatus.ACTIVE);

                    Instant pastExpires = FIXED_NOW.minusSeconds(1);
                    assertThat(evaluator.evaluate(postedAt, pastExpires, discoveredAt, lastSeenAt))
                            .isEqualTo(JobFreshnessStatus.EXPIRED);
                }
            } finally {
                TimeZone.setDefault(originalDefault);
            }
        }

        @Test
        @DisplayName("Static convenience methods evaluate accurately with default properties")
        void staticConvenienceMethodsWork() {
            Instant postedAt = Instant.now().minus(Duration.ofDays(1));
            Instant expiresAt = Instant.now().plus(Duration.ofDays(5));
            Instant discoveredAt = Instant.now().minus(Duration.ofDays(1));
            Instant lastSeenAt = Instant.now().minus(Duration.ofHours(1));

            assertThat(JobFreshnessEvaluator.evaluateStatic(postedAt, expiresAt, discoveredAt, lastSeenAt))
                    .isEqualTo(JobFreshnessStatus.ACTIVE);
        }
    }
}

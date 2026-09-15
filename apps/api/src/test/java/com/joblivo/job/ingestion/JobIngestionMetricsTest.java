package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("JobIngestionMetrics Unit Tests")
class JobIngestionMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private JobIngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new JobIngestionMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Run started metric increments correctly")
    void recordRunStartedIncrementsCounter() {
        metrics.recordRunStarted();

        Counter counter = meterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_STARTED)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Run outcome records mutually exclusive status and stops timer")
    void recordRunOutcomeSuccess() {
        Timer.Sample sample = metrics.startTimer();
        metrics.recordRunOutcome(IngestionRunStatus.COMPLETED, sample);

        Counter counter = meterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED)
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find(JobIngestionMetrics.METRIC_RUNS_DURATION)
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED)
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }

    @Test
    @DisplayName("Run outcome for completed_with_errors and failed increments respective counters")
    void recordRunOutcomeErrorsAndFailed() {
        metrics.recordRunOutcome(IngestionRunStatus.COMPLETED_WITH_ERRORS, metrics.startTimer());
        metrics.recordRunOutcome(IngestionRunStatus.FAILED, metrics.startTimer());

        Counter errorsCounter = meterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED_WITH_ERRORS)
                .counter();
        assertThat(errorsCounter).isNotNull();
        assertThat(errorsCounter.count()).isEqualTo(1.0);

        Counter failedCounter = meterRegistry.find(JobIngestionMetrics.METRIC_RUNS)
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_FAILED)
                .counter();
        assertThat(failedCounter).isNotNull();
        assertThat(failedCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Source started and outcome metrics record bounded source tag and timer")
    void recordSourceMetrics() {
        metrics.recordSourceStarted(JobSource.LINKEDIN);

        Counter startedCounter = meterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_STARTED)
                .counter();
        assertThat(startedCounter).isNotNull();
        assertThat(startedCounter.count()).isEqualTo(1.0);

        Timer.Sample sample = metrics.startTimer();
        metrics.recordSourceOutcome(JobSource.LINKEDIN, IngestionRunStatus.COMPLETED, sample);

        Counter completedCounter = meterRegistry.find(JobIngestionMetrics.METRIC_SOURCES)
                .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED)
                .counter();
        assertThat(completedCounter).isNotNull();
        assertThat(completedCounter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find(JobIngestionMetrics.METRIC_SOURCES_DURATION)
                .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                .tag(JobIngestionMetrics.TAG_STATUS, JobIngestionMetrics.STATUS_COMPLETED)
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Candidate count metrics accurately partition received, considered, created, updated, skipped, failed")
    void recordCandidateCounts() {
        metrics.recordCandidateCounts(JobSource.NAUKRI, 50, 40, 20, 10, 5, 5);

        Counter received = meterRegistry.find(JobIngestionMetrics.METRIC_CANDIDATES_RECEIVED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI").counter();
        Counter considered = meterRegistry.find(JobIngestionMetrics.METRIC_CANDIDATES_CONSIDERED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI").counter();
        Counter created = meterRegistry.find(JobIngestionMetrics.METRIC_JOBS_CREATED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI").counter();
        Counter updated = meterRegistry.find(JobIngestionMetrics.METRIC_JOBS_UPDATED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI").counter();
        Counter skipped = meterRegistry.find(JobIngestionMetrics.METRIC_JOBS_SKIPPED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI").counter();
        Counter failed = meterRegistry.find(JobIngestionMetrics.METRIC_CANDIDATES_FAILED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI").counter();

        assertThat(received).isNotNull();
        assertThat(received.count()).isEqualTo(50.0);

        assertThat(considered).isNotNull();
        assertThat(considered.count()).isEqualTo(40.0);

        assertThat(created).isNotNull();
        assertThat(created.count()).isEqualTo(20.0);

        assertThat(updated).isNotNull();
        assertThat(updated.count()).isEqualTo(10.0);

        assertThat(skipped).isNotNull();
        assertThat(skipped.count()).isEqualTo(5.0);

        assertThat(failed).isNotNull();
        assertThat(failed.count()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Duplicates skipped metric records bounded source tag and accurate increment")
    void recordDuplicatesSkipped() {
        metrics.recordDuplicatesSkipped(JobSource.LINKEDIN, 3);
        metrics.recordDuplicatesSkipped(JobSource.LINKEDIN, 2);
        metrics.recordDuplicatesSkipped(null, 1);
        metrics.recordDuplicatesSkipped(JobSource.NAUKRI, 0); // ignored

        Counter linkedinCounter = meterRegistry.find(JobIngestionMetrics.METRIC_DUPLICATES_SKIPPED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                .counter();
        assertThat(linkedinCounter).isNotNull();
        assertThat(linkedinCounter.count()).isEqualTo(5.0);

        Counter nullSourceCounter = meterRegistry.find(JobIngestionMetrics.METRIC_DUPLICATES_SKIPPED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "UNKNOWN")
                .counter();
        assertThat(nullSourceCounter).isNotNull();
        assertThat(nullSourceCounter.count()).isEqualTo(1.0);

        Counter naukriCounter = meterRegistry.find(JobIngestionMetrics.METRIC_DUPLICATES_SKIPPED)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI")
                .counter();
        assertThat(naukriCounter).isNull();
    }

    @Test
    @DisplayName("Error metric records bounded error category and source tags")
    void recordErrorCategorization() {
        metrics.recordError(JobSource.LINKEDIN, IngestionErrorCategory.VALIDATION);
        metrics.recordError(JobSource.LINKEDIN, IngestionErrorCategory.SOURCE_FAILURE);
        metrics.recordError(JobSource.NAUKRI, "DUPLICATE_IN_BATCH");
        metrics.recordError(null, "FATAL_ORCHESTRATION_ERROR");

        Counter valErr = meterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                .tag(JobIngestionMetrics.TAG_SOURCE, "LINKEDIN")
                .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "VALIDATION")
                .counter();
        assertThat(valErr).isNotNull();
        assertThat(valErr.count()).isEqualTo(1.0);

        Counter dupErr = meterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                .tag(JobIngestionMetrics.TAG_SOURCE, "NAUKRI")
                .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "DUPLICATE")
                .counter();
        assertThat(dupErr).isNotNull();
        assertThat(dupErr.count()).isEqualTo(1.0);

        Counter infraErr = meterRegistry.find(JobIngestionMetrics.METRIC_ERRORS)
                .tag(JobIngestionMetrics.TAG_SOURCE, "UNKNOWN")
                .tag(JobIngestionMetrics.TAG_ERROR_CATEGORY, "INFRASTRUCTURE")
                .counter();
        assertThat(infraErr).isNotNull();
        assertThat(infraErr.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Cardinality policy review: meter tags only contain source, status, or error_category")
    void tagCardinalityPolicyEnforced() {
        // Generate a variety of metrics
        metrics.recordRunStarted();
        metrics.recordRunOutcome(IngestionRunStatus.COMPLETED, metrics.startTimer());
        metrics.recordSourceStarted(JobSource.LINKEDIN);
        metrics.recordSourceOutcome(JobSource.LINKEDIN, IngestionRunStatus.COMPLETED, metrics.startTimer());
        metrics.recordCandidateCounts(JobSource.LINKEDIN, 10, 10, 5, 5, 0, 0);
        metrics.recordError(JobSource.LINKEDIN, IngestionErrorCategory.VALIDATION);

        Set<String> allowedTagKeys = Set.of(
                JobIngestionMetrics.TAG_SOURCE,
                JobIngestionMetrics.TAG_STATUS,
                JobIngestionMetrics.TAG_ERROR_CATEGORY
        );

        Set<String> forbiddenTagKeys = Set.of(
                "runId", "run_id", "jobId", "job_id", "externalJobId", "external_job_id",
                "userId", "user_id", "url", "company", "title", "recruiter"
        );

        List<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).isNotEmpty();

        for (Meter meter : meters) {
            for (Tag tag : meter.getId().getTags()) {
                assertThat(allowedTagKeys).contains(tag.getKey());
                assertThat(forbiddenTagKeys).doesNotContain(tag.getKey().toLowerCase());
            }
        }
    }

    @Test
    @DisplayName("Metric recording methods do not throw exceptions even if registry fails")
    void resilienceWhenRegistryFails() {
        // Meter registry that throws runtime exception on register
        JobIngestionMetrics resilientMetrics = new JobIngestionMetrics(null);

        assertThatCode(() -> {
            resilientMetrics.recordRunStarted();
            resilientMetrics.recordRunOutcome(IngestionRunStatus.COMPLETED, null);
            resilientMetrics.recordSourceStarted(JobSource.LINKEDIN);
            resilientMetrics.recordSourceOutcome(JobSource.LINKEDIN, IngestionRunStatus.COMPLETED, null);
            resilientMetrics.recordCandidateCounts(JobSource.LINKEDIN, 1, 1, 1, 0, 0, 0);
            resilientMetrics.recordError(JobSource.LINKEDIN, IngestionErrorCategory.VALIDATION);
            resilientMetrics.recordError(JobSource.LINKEDIN, "RAW_ERROR");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("IngestionErrorCategory maps raw categories accurately")
    void ingestionErrorCategoryMapping() {
        assertThat(IngestionErrorCategory.fromRawCategory("VALIDATION_ERROR")).isEqualTo(IngestionErrorCategory.VALIDATION);
        assertThat(IngestionErrorCategory.fromRawCategory("INVALID_ARGUMENT")).isEqualTo(IngestionErrorCategory.VALIDATION);
        assertThat(IngestionErrorCategory.fromRawCategory("MISSING_EXTERNAL_ID")).isEqualTo(IngestionErrorCategory.VALIDATION);
        assertThat(IngestionErrorCategory.fromRawCategory("NULL_CANDIDATE")).isEqualTo(IngestionErrorCategory.VALIDATION);

        assertThat(IngestionErrorCategory.fromRawCategory("DUPLICATE_IN_BATCH")).isEqualTo(IngestionErrorCategory.DUPLICATE);

        assertThat(IngestionErrorCategory.fromRawCategory("ADAPTER_FETCH_ERROR")).isEqualTo(IngestionErrorCategory.SOURCE_FAILURE);

        assertThat(IngestionErrorCategory.fromRawCategory("DATA_INTEGRITY_ERROR")).isEqualTo(IngestionErrorCategory.PERSISTENCE);

        assertThat(IngestionErrorCategory.fromRawCategory("ADAPTER_NOT_FOUND")).isEqualTo(IngestionErrorCategory.CONFIGURATION);
        assertThat(IngestionErrorCategory.fromRawCategory("SOURCE_DISABLED")).isEqualTo(IngestionErrorCategory.CONFIGURATION);
        assertThat(IngestionErrorCategory.fromRawCategory("INVALID_SOURCE")).isEqualTo(IngestionErrorCategory.CONFIGURATION);

        assertThat(IngestionErrorCategory.fromRawCategory("FATAL_ORCHESTRATION_ERROR")).isEqualTo(IngestionErrorCategory.INFRASTRUCTURE);

        assertThat(IngestionErrorCategory.fromRawCategory("UNEXPECTED_EXCEPTION")).isEqualTo(IngestionErrorCategory.UNKNOWN);
        assertThat(IngestionErrorCategory.fromRawCategory(null)).isEqualTo(IngestionErrorCategory.UNKNOWN);
        assertThat(IngestionErrorCategory.fromRawCategory("   ")).isEqualTo(IngestionErrorCategory.UNKNOWN);
    }
}

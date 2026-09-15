package com.joblivo.job.ingestion;

import com.joblivo.job.duplicate.JobDuplicateClassification;
import com.joblivo.job.model.JobSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Production-ready metrics and operational observability service for Job Discovery ingestion.
 * Collects low-cardinality counters and timers for ingestion run lifecycles, source-level executions,
 * aggregate candidate counts, and categorized failure classifications using Micrometer.
 * <p>
 * Strictly enforces bounded tag cardinality (source, status, error_category) and isolates metric recording
 * to ensure telemetry never disrupts core ingestion business logic or transaction boundaries.
 */
@Component
public class JobIngestionMetrics {

    private static final Logger log = LoggerFactory.getLogger(JobIngestionMetrics.class);

    // Metric names
    public static final String METRIC_RUNS = "joblivo.job.ingestion.runs";
    public static final String METRIC_RUNS_DURATION = "joblivo.job.ingestion.runs.duration";
    public static final String METRIC_SOURCES = "joblivo.job.ingestion.sources";
    public static final String METRIC_SOURCES_DURATION = "joblivo.job.ingestion.sources.duration";
    public static final String METRIC_CANDIDATES_RECEIVED = "joblivo.job.ingestion.candidates.received";
    public static final String METRIC_CANDIDATES_CONSIDERED = "joblivo.job.ingestion.candidates.considered";
    public static final String METRIC_JOBS_CREATED = "joblivo.job.ingestion.jobs.created";
    public static final String METRIC_JOBS_UPDATED = "joblivo.job.ingestion.jobs.updated";
    public static final String METRIC_JOBS_SKIPPED = "joblivo.job.ingestion.jobs.skipped";
    public static final String METRIC_DUPLICATES_SKIPPED = "joblivo.job.ingestion.duplicates.skipped";
    public static final String METRIC_DUPLICATES_DETECTED = "joblivo.job.ingestion.duplicates.detected";
    public static final String METRIC_CANDIDATES_FAILED = "joblivo.job.ingestion.candidates.failed";
    public static final String METRIC_ERRORS = "joblivo.job.ingestion.errors";

    // Tag names
    public static final String TAG_SOURCE = "source";
    public static final String TAG_STATUS = "status";
    public static final String TAG_ERROR_CATEGORY = "error_category";
    public static final String TAG_DUPLICATE_CLASSIFICATION = "classification";

    // Status tag values
    public static final String STATUS_STARTED = "started";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_COMPLETED_WITH_ERRORS = "completed_with_errors";
    public static final String STATUS_FAILED = "failed";

    public static final String SOURCE_UNKNOWN = "UNKNOWN";

    private final MeterRegistry meterRegistry;

    @Autowired
    public JobIngestionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNullElseGet(meterRegistry, SimpleMeterRegistry::new);
    }

    /**
     * Fallback constructor defaulting to an in-memory {@link SimpleMeterRegistry}.
     */
    public JobIngestionMetrics() {
        this(new SimpleMeterRegistry());
    }

    /**
     * Starts a new timing sample for execution measurement.
     *
     * @return active Timer.Sample or null if starting failed
     */
    public Timer.Sample startTimer() {
        try {
            return Timer.start(meterRegistry);
        } catch (Throwable t) {
            log.warn("Failed to start Micrometer timer sample: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Records that an ingestion run has started.
     */
    public void recordRunStarted() {
        try {
            Counter.builder(METRIC_RUNS)
                    .description("Total number of job discovery ingestion runs partitioned by lifecycle status")
                    .tag(TAG_STATUS, STATUS_STARTED)
                    .register(meterRegistry)
                    .increment();
        } catch (Throwable t) {
            log.warn("Failed to record run started metric: {}", t.getMessage());
        }
    }

    /**
     * Records the final operational outcome and duration of an ingestion run.
     * Exactly one mutually exclusive final outcome counter is incremented per execution.
     *
     * @param status      terminal operational lifecycle state
     * @param timerSample optional timer sample initiated at run start
     */
    public void recordRunOutcome(IngestionRunStatus status, Timer.Sample timerSample) {
        try {
            String statusTag = resolveStatusTag(status);
            Counter.builder(METRIC_RUNS)
                    .description("Total number of job discovery ingestion runs partitioned by lifecycle status")
                    .tag(TAG_STATUS, statusTag)
                    .register(meterRegistry)
                    .increment();

            if (timerSample != null) {
                Timer timer = Timer.builder(METRIC_RUNS_DURATION)
                        .description("Execution duration of job discovery ingestion runs")
                        .tag(TAG_STATUS, statusTag)
                        .register(meterRegistry);
                timerSample.stop(timer);
            }
        } catch (Throwable t) {
            log.warn("Failed to record run outcome metric: {}", t.getMessage());
        }
    }

    /**
     * Records that a source ingestion execution has started.
     *
     * @param source the target job source
     */
    public void recordSourceStarted(JobSource source) {
        try {
            String sourceTag = resolveSourceTag(source);
            Counter.builder(METRIC_SOURCES)
                    .description("Total number of source-level ingestion executions partitioned by source and status")
                    .tag(TAG_SOURCE, sourceTag)
                    .tag(TAG_STATUS, STATUS_STARTED)
                    .register(meterRegistry)
                    .increment();
        } catch (Throwable t) {
            log.warn("Failed to record source started metric: {}", t.getMessage());
        }
    }

    /**
     * Records the outcome and duration of an individual source ingestion execution.
     *
     * @param source      the target job source
     * @param status      operational lifecycle state of the source execution
     * @param timerSample optional timer sample initiated at source start
     */
    public void recordSourceOutcome(JobSource source, IngestionRunStatus status, Timer.Sample timerSample) {
        try {
            String sourceTag = resolveSourceTag(source);
            String statusTag = resolveStatusTag(status);

            Counter.builder(METRIC_SOURCES)
                    .description("Total number of source-level ingestion executions partitioned by source and status")
                    .tag(TAG_SOURCE, sourceTag)
                    .tag(TAG_STATUS, statusTag)
                    .register(meterRegistry)
                    .increment();

            if (timerSample != null) {
                Timer timer = Timer.builder(METRIC_SOURCES_DURATION)
                        .description("Execution duration of source-level ingestion runs")
                        .tag(TAG_SOURCE, sourceTag)
                        .tag(TAG_STATUS, statusTag)
                        .register(meterRegistry);
                timerSample.stop(timer);
            }
        } catch (Throwable t) {
            log.warn("Failed to record source outcome metric: {}", t.getMessage());
        }
    }

    /**
     * Records aggregate candidate processing statistics for a source execution.
     *
     * @param source     the target job source
     * @param received   candidates returned by adapter
     * @param considered candidates considered within configured limits
     * @param created    jobs newly created
     * @param updated    jobs updated
     * @param skipped    jobs skipped
     * @param failed     candidates failed
     */
    public void recordCandidateCounts(
            JobSource source,
            int received,
            int considered,
            int created,
            int updated,
            int skipped,
            int failed
    ) {
        try {
            String sourceTag = resolveSourceTag(source);

            if (received > 0) {
                Counter.builder(METRIC_CANDIDATES_RECEIVED)
                        .description("Total number of job candidates received from source adapters")
                        .tag(TAG_SOURCE, sourceTag)
                        .register(meterRegistry)
                        .increment(received);
            }
            if (considered > 0) {
                Counter.builder(METRIC_CANDIDATES_CONSIDERED)
                        .description("Total number of job candidates considered after limit enforcement")
                        .tag(TAG_SOURCE, sourceTag)
                        .register(meterRegistry)
                        .increment(considered);
            }
            if (created > 0) {
                Counter.builder(METRIC_JOBS_CREATED)
                        .description("Total number of new job postings created in catalog")
                        .tag(TAG_SOURCE, sourceTag)
                        .register(meterRegistry)
                        .increment(created);
            }
            if (updated > 0) {
                Counter.builder(METRIC_JOBS_UPDATED)
                        .description("Total number of existing job postings updated in catalog")
                        .tag(TAG_SOURCE, sourceTag)
                        .register(meterRegistry)
                        .increment(updated);
            }
            if (skipped > 0) {
                Counter.builder(METRIC_JOBS_SKIPPED)
                        .description("Total number of candidate jobs skipped")
                        .tag(TAG_SOURCE, sourceTag)
                        .register(meterRegistry)
                        .increment(skipped);
            }
            if (failed > 0) {
                Counter.builder(METRIC_CANDIDATES_FAILED)
                        .description("Total number of candidate jobs that failed validation or processing")
                        .tag(TAG_SOURCE, sourceTag)
                        .register(meterRegistry)
                        .increment(failed);
            }
        } catch (Throwable t) {
            log.warn("Failed to record candidate count metrics: {}", t.getMessage());
        }
    }

    /**
     * Records candidate duplicates skipped within a batch during ingestion.
     * Uses bounded low-cardinality tag (source).
     *
     * @param source the target job source
     * @param count  the number of duplicates skipped
     */
    public void recordDuplicatesSkipped(JobSource source, int count) {
        if (count <= 0) {
            return;
        }
        try {
            String sourceTag = resolveSourceTag(source);
            Counter.builder(METRIC_DUPLICATES_SKIPPED)
                    .description("Total number of candidate jobs skipped due to in-batch duplication")
                    .tag(TAG_SOURCE, sourceTag)
                    .register(meterRegistry)
                    .increment(count);
        } catch (Throwable t) {
            log.warn("Failed to record duplicates skipped metric: {}", t.getMessage());
        }
    }

    /**
     * Records candidate duplicate detections against stored jobs during ingestion.
     * Uses safe, low-cardinality tags (source and duplicate classification).
     *
     * @param source         the target job source
     * @param classification the deterministic duplicate classification
     */
    public void recordDuplicateDetected(JobSource source, JobDuplicateClassification classification) {
        if (classification == null || classification == JobDuplicateClassification.NOT_DUPLICATE) {
            return;
        }
        try {
            String sourceTag = resolveSourceTag(source);
            String classificationTag = classification.name();
            Counter.builder(METRIC_DUPLICATES_DETECTED)
                    .description("Total number of deterministic duplicate job detections partitioned by source and classification")
                    .tag(TAG_SOURCE, sourceTag)
                    .tag(TAG_DUPLICATE_CLASSIFICATION, classificationTag)
                    .register(meterRegistry)
                    .increment();
        } catch (Throwable t) {
            log.warn("Failed to record duplicate detected metric: {}", t.getMessage());
        }
    }

    /**
     * Records an ingestion error under a bounded, low-cardinality category.
     *
     * @param source   the job source origin
     * @param category the categorized failure
     */
    public void recordError(JobSource source, IngestionErrorCategory category) {
        try {
            String sourceTag = resolveSourceTag(source);
            String categoryTag = (category != null ? category : IngestionErrorCategory.UNKNOWN).name();

            Counter.builder(METRIC_ERRORS)
                    .description("Total number of ingestion failures classified by source and error category")
                    .tag(TAG_SOURCE, sourceTag)
                    .tag(TAG_ERROR_CATEGORY, categoryTag)
                    .register(meterRegistry)
                    .increment();
        } catch (Throwable t) {
            log.warn("Failed to record error metric: {}", t.getMessage());
        }
    }

    /**
     * Records an ingestion error from a raw failure category string, normalizing it to a low-cardinality tag.
     *
     * @param source      the job source origin
     * @param rawCategory raw category name
     */
    public void recordError(JobSource source, String rawCategory) {
        recordError(source, IngestionErrorCategory.fromRawCategory(rawCategory));
    }

    /**
     * Exposes the underlying {@link MeterRegistry} for assertions in tests or custom inspection.
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    private String resolveSourceTag(JobSource source) {
        return source != null ? source.name() : SOURCE_UNKNOWN;
    }

    private String resolveStatusTag(IngestionRunStatus status) {
        if (status == null) {
            return STATUS_FAILED;
        }
        return switch (status) {
            case STARTED -> STATUS_STARTED;
            case RUNNING, COMPLETED -> STATUS_COMPLETED;
            case COMPLETED_WITH_ERRORS -> STATUS_COMPLETED_WITH_ERRORS;
            case FAILED -> STATUS_FAILED;
        };
    }
}

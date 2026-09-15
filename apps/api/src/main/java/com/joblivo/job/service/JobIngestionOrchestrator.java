package com.joblivo.job.service;

import com.joblivo.job.exception.JobSourceAdapterException;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.ingestion.CandidateFailureSummary;
import com.joblivo.job.ingestion.CandidateIngestionAction;
import com.joblivo.job.ingestion.IngestionErrorCategory;
import com.joblivo.job.ingestion.IngestionRunContext;
import com.joblivo.job.ingestion.IngestionRunResult;
import com.joblivo.job.ingestion.IngestionRunStatus;
import com.joblivo.job.ingestion.JobBatchDeduplicator;
import com.joblivo.job.ingestion.JobCandidateIngestionResult;
import com.joblivo.job.ingestion.JobFetchCriteria;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.ingestion.JobIngestionMetrics;
import com.joblivo.job.ingestion.JobSourceAdapter;
import com.joblivo.job.ingestion.JobSourceControlPolicy;
import com.joblivo.job.ingestion.JobSourceCoverage;
import com.joblivo.job.ingestion.JobSourceExecutionContext;
import com.joblivo.job.ingestion.JobSourceExecutionVerification;
import com.joblivo.job.ingestion.JobSourceRegistry;
import com.joblivo.job.ingestion.JobSourceResolution;
import com.joblivo.job.ingestion.MultiSourceIngestionRunResult;
import com.joblivo.job.model.JobSource;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled internal orchestration service for job discovery ingestion with an explicit operational run lifecycle
 * and integrated low-cardinality telemetry via {@link JobIngestionMetrics}.
 * Coordinates {@link JobSourceAdapter} implementations through {@link JobSourceRegistry},
 * enforces ingestion limits and eligibility policies via {@link JobSourceControlPolicy},
 * executes batch ingestion with candidate-level failure and transactional isolation, and compiles
 * immutable, deterministic execution summaries.
 */
@Service
public class JobIngestionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(JobIngestionOrchestrator.class);

    private final JobSourceRegistry sourceRegistry;
    private final JobSourceControlPolicy controlPolicy;
    private final JobIngestionService jobIngestionService;
    private final JobIngestionMetrics metrics;
    private final JobBatchDeduplicator batchDeduplicator;

    @Autowired
    public JobIngestionOrchestrator(
            JobSourceRegistry sourceRegistry,
            JobSourceControlPolicy controlPolicy,
            JobIngestionService jobIngestionService,
            JobIngestionMetrics metrics,
            JobBatchDeduplicator batchDeduplicator
    ) {
        this.sourceRegistry = Objects.requireNonNull(sourceRegistry, "sourceRegistry must not be null");
        this.controlPolicy = Objects.requireNonNull(controlPolicy, "controlPolicy must not be null");
        this.jobIngestionService = Objects.requireNonNull(jobIngestionService, "jobIngestionService must not be null");
        this.metrics = Objects.requireNonNullElseGet(metrics, JobIngestionMetrics::new);
        this.batchDeduplicator = Objects.requireNonNullElseGet(batchDeduplicator, JobBatchDeduplicator::new);
    }

    /**
     * Backward-compatible constructor defaulting to a default {@link JobBatchDeduplicator}.
     */
    public JobIngestionOrchestrator(
            JobSourceRegistry sourceRegistry,
            JobSourceControlPolicy controlPolicy,
            JobIngestionService jobIngestionService,
            JobIngestionMetrics metrics
    ) {
        this(sourceRegistry, controlPolicy, jobIngestionService, metrics, new JobBatchDeduplicator());
    }

    /**
     * Backward-compatible constructor defaulting to an in-memory {@link JobIngestionMetrics}.
     */
    public JobIngestionOrchestrator(
            JobSourceRegistry sourceRegistry,
            JobSourceControlPolicy controlPolicy,
            JobIngestionService jobIngestionService
    ) {
        this(sourceRegistry, controlPolicy, jobIngestionService, new JobIngestionMetrics());
    }

    /**
     * Backward-compatible constructor constructing {@link JobSourceControlPolicy} from registry properties.
     */
    public JobIngestionOrchestrator(JobSourceRegistry sourceRegistry, JobIngestionService jobIngestionService) {
        this(
                sourceRegistry,
                new JobSourceControlPolicy(
                        Objects.requireNonNull(sourceRegistry, "sourceRegistry must not be null").getProperties(),
                        sourceRegistry
                ),
                jobIngestionService
        );
    }

    /**
     * Exposes the configured {@link JobIngestionMetrics} component.
     */
    public JobIngestionMetrics getMetrics() {
        return metrics;
    }

    /**
     * Exposes the configured {@link JobBatchDeduplicator} component.
     */
    public JobBatchDeduplicator getBatchDeduplicator() {
        return batchDeduplicator;
    }

    /**
     * Executes ingestion for a single source with default fetch criteria and an auto-generated run ID.
     */
    public IngestionRunResult ingestSource(JobSource source) {
        return ingestSource(source, JobFetchCriteria.of(null, null), UUID.randomUUID().toString());
    }

    /**
     * Executes ingestion for a single source with specified fetch criteria and an auto-generated run ID.
     */
    public IngestionRunResult ingestSource(JobSource source, JobFetchCriteria criteria) {
        return ingestSource(source, criteria, UUID.randomUUID().toString());
    }

    /**
     * Executes ingestion for a single source with specified criteria and correlation/run ID.
     * Enforces explicit lifecycle transitions: STARTED -> RUNNING -> COMPLETED / COMPLETED_WITH_ERRORS / FAILED.
     * Enforces source enablement, adapter availability, candidate limits, error isolation, and operational metrics.
     *
     * @param source   the job source origin to ingest
     * @param criteria query and pagination bounds
     * @param runId    correlation run identifier for observability
     * @return immutable execution summary with operational lifecycle status
     */
    public IngestionRunResult ingestSource(JobSource source, JobFetchCriteria criteria, String runId) {
        return ingestSourceInternal(source, criteria, runId, true);
    }

    private IngestionRunResult ingestSourceInternal(
            JobSource source,
            JobFetchCriteria criteria,
            String runId,
            boolean isTopLevelRun
    ) {
        String effectiveRunId = resolveRunId(runId);
        Instant startedAt = Instant.now();
        Timer.Sample runTimerSample = isTopLevelRun ? metrics.startTimer() : null;
        if (isTopLevelRun) {
            metrics.recordRunStarted();
        }

        Timer.Sample sourceTimerSample = metrics.startTimer();
        metrics.recordSourceStarted(source);

        IngestionRunContext runContext = IngestionRunContext.start(effectiveRunId, source);
        log.info("[runId={}] Ingestion run started for source '{}' [status={}]", effectiveRunId, source, runContext.status());

        try {
            runContext = runContext.toRunning();
            log.info("[runId={}] Ingestion run entered RUNNING for source '{}' [status={}]", effectiveRunId, source, runContext.status());

            if (source == null) {
                log.warn("[runId={}] Ingestion requested with null source", effectiveRunId);
                runContext = runContext.toFailed();
                log.error("[runId={}] Ingestion run failed: source is null [status={}]", effectiveRunId, runContext.status());

                CandidateFailureSummary failure = new CandidateFailureSummary(null, null, "INVALID_SOURCE", "JobSource must not be null");
                metrics.recordError(null, failure.failureCategory());
                metrics.recordCandidateCounts(null, 0, 0, 0, 0, 0, 1);
                metrics.recordSourceOutcome(null, IngestionRunStatus.FAILED, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.FAILED, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(null)
                        .status(IngestionRunStatus.FAILED)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .failedCount(1)
                        .addFailure(failure)
                        .build();
            }

            log.info("[runId={}] Source started: '{}' [enabled={}, limit={}]",
                    effectiveRunId, source, controlPolicy.isSourceEnabled(source), controlPolicy.getMaxCandidates(source));

            JobSourceResolution resolution = controlPolicy.resolveSource(source);
            if (!resolution.isExecutionAllowed()) {
                String rejectionReason = resolution.rejectionReason();
                String rejectionCategory = resolution.rejectionCategory();
                log.warn("[runId={}] Source execution not permitted: '{}' [category={}, reason={}]",
                        effectiveRunId, source, rejectionCategory, rejectionReason);
                runContext = runContext.toCompletedWithErrors();
                log.warn("[runId={}] Ingestion run completed with errors for source '{}' [reason={}, status={}]",
                        effectiveRunId, source, rejectionCategory, runContext.status());

                CandidateFailureSummary failure = new CandidateFailureSummary(source, null, rejectionCategory, rejectionReason);
                metrics.recordError(source, failure.failureCategory());
                metrics.recordCandidateCounts(source, 0, 0, 0, 0, 0, 1);
                metrics.recordSourceOutcome(source, IngestionRunStatus.COMPLETED_WITH_ERRORS, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.COMPLETED_WITH_ERRORS, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(source)
                        .status(IngestionRunStatus.COMPLETED_WITH_ERRORS)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .failedCount(1)
                        .addFailure(failure)
                        .build();
            }

            JobSourceAdapter adapter = resolution.getAdapter().orElseThrow();
            int maxCandidates = controlPolicy.getMaxCandidates(source);

            JobSourceExecutionContext executionContext;
            try {
                executionContext = JobSourceExecutionContext.of(effectiveRunId, source, maxCandidates);
            } catch (IllegalArgumentException ex) {
                log.error("[runId={}] Invalid execution context for source '{}': {}", effectiveRunId, source, ex.getMessage());
                runContext = runContext.toCompletedWithErrors();
                log.warn("[runId={}] Ingestion run completed with errors for source '{}' [reason=INVALID_EXECUTION_CONTEXT, status={}]",
                        effectiveRunId, source, runContext.status());

                CandidateFailureSummary failure = new CandidateFailureSummary(source, null, "INVALID_EXECUTION_CONTEXT", sanitizeMessage(ex.getMessage()));
                metrics.recordError(source, IngestionErrorCategory.CONFIGURATION);
                metrics.recordCandidateCounts(source, 0, 0, 0, 0, 0, 1);
                metrics.recordSourceOutcome(source, IngestionRunStatus.COMPLETED_WITH_ERRORS, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.COMPLETED_WITH_ERRORS, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(source)
                        .status(IngestionRunStatus.COMPLETED_WITH_ERRORS)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .failedCount(1)
                        .addFailure(failure)
                        .build();
            }

            JobSourceExecutionVerification verification = controlPolicy.verifyExecution(source, adapter, executionContext);
            if (!verification.isValid()) {
                log.error("[runId={}] Source execution policy verification failed for '{}': [{}] {}",
                        effectiveRunId, source, verification.rejectionCategory(), verification.rejectionReason());
                runContext = runContext.toCompletedWithErrors();
                log.warn("[runId={}] Ingestion run completed with errors for source '{}' [reason={}, status={}]",
                        effectiveRunId, source, verification.rejectionCategory(), runContext.status());

                CandidateFailureSummary failure = new CandidateFailureSummary(
                        source,
                        null,
                        verification.rejectionCategory(),
                        verification.rejectionReason()
                );
                metrics.recordError(source, IngestionErrorCategory.CONFIGURATION);
                metrics.recordCandidateCounts(source, 0, 0, 0, 0, 0, 1);
                metrics.recordSourceOutcome(source, IngestionRunStatus.COMPLETED_WITH_ERRORS, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.COMPLETED_WITH_ERRORS, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(source)
                        .status(IngestionRunStatus.COMPLETED_WITH_ERRORS)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .failedCount(1)
                        .addFailure(failure)
                        .build();
            }

            log.info("[runId={}] Invoking adapter for source '{}' with candidateLimit={}",
                    effectiveRunId, source, executionContext.candidateLimit());

            List<JobIngestionCandidate> fetchedCandidates;
            try {
                List<JobIngestionCandidate> rawCandidates = adapter.fetchIngestionCandidates(executionContext);
                fetchedCandidates = (rawCandidates != null) ? rawCandidates : List.of();
            } catch (JobSourceAdapterException ex) {
                log.error("[runId={}] Source failed: adapter for '{}' failed during candidate fetch: category={}, message={}",
                        effectiveRunId, source, ex.getErrorCategory(), ex.getMessage());
                runContext = runContext.toCompletedWithErrors();
                log.warn("[runId={}] Ingestion run completed with errors for source '{}' [reason=ADAPTER_FETCH_ERROR, status={}]",
                        effectiveRunId, source, runContext.status());

                CandidateFailureSummary failure = new CandidateFailureSummary(source, null, ex.getErrorCategory().name(), sanitizeMessage(ex.getMessage()));
                metrics.recordError(source, ex.getErrorCategory());
                metrics.recordCandidateCounts(source, 0, 0, 0, 0, 0, 1);
                metrics.recordSourceOutcome(source, IngestionRunStatus.COMPLETED_WITH_ERRORS, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.COMPLETED_WITH_ERRORS, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(source)
                        .status(IngestionRunStatus.COMPLETED_WITH_ERRORS)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .failedCount(1)
                        .addFailure(failure)
                        .build();
            } catch (Exception ex) {
                log.error("[runId={}] Source failed: adapter for '{}' failed during candidate fetch", effectiveRunId, source, ex);
                runContext = runContext.toCompletedWithErrors();
                log.warn("[runId={}] Ingestion run completed with errors for source '{}' [reason=ADAPTER_FETCH_ERROR, status={}]",
                        effectiveRunId, source, runContext.status());

                CandidateFailureSummary failure = new CandidateFailureSummary(source, null, "ADAPTER_FETCH_ERROR", sanitizeMessage(ex.getMessage()));
                metrics.recordError(source, failure.failureCategory());
                metrics.recordCandidateCounts(source, 0, 0, 0, 0, 0, 1);
                metrics.recordSourceOutcome(source, IngestionRunStatus.COMPLETED_WITH_ERRORS, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.COMPLETED_WITH_ERRORS, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(source)
                        .status(IngestionRunStatus.COMPLETED_WITH_ERRORS)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .failedCount(1)
                        .addFailure(failure)
                        .build();
            }

            int candidatesReceived = fetchedCandidates.size();

            if (fetchedCandidates.isEmpty()) {
                log.info("[runId={}] Source completed: adapter for '{}' returned 0 candidates", effectiveRunId, source);
                runContext = runContext.toCompleted();
                log.info("[runId={}] Ingestion run completed for source '{}' [status={}, candidates=0]",
                        effectiveRunId, source, runContext.status());

                metrics.recordCandidateCounts(source, 0, 0, 0, 0, 0, 0);
                metrics.recordSourceOutcome(source, IngestionRunStatus.COMPLETED, sourceTimerSample);
                if (isTopLevelRun) {
                    metrics.recordRunOutcome(IngestionRunStatus.COMPLETED, runTimerSample);
                }

                return IngestionRunResult.builder()
                        .runId(effectiveRunId)
                        .source(source)
                        .status(IngestionRunStatus.COMPLETED)
                        .startedAt(startedAt)
                        .completedAt(Instant.now())
                        .totalCandidates(0)
                        .candidatesReceived(0)
                        .candidatesConsidered(0)
                        .build();
            }

            List<JobIngestionCandidate> candidatesToProcess = fetchedCandidates;
            if (candidatesReceived > maxCandidates) {
                log.info("[runId={}] Candidate limit enforced for source '{}': received={}, limit={}, considering first {}",
                        effectiveRunId, source, candidatesReceived, maxCandidates, maxCandidates);
                candidatesToProcess = fetchedCandidates.subList(0, maxCandidates);
            }

            int candidatesConsidered = candidatesToProcess.size();
            int createdCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;
            List<CandidateFailureSummary> failureSummaries = new ArrayList<>();
            JobBatchDeduplicator.Session deduplicationSession = batchDeduplicator.createSession(source);

            log.info("[runId={}] Processing {} considered candidates (received={}) for source '{}'",
                    effectiveRunId, candidatesConsidered, candidatesReceived, source);

            for (JobIngestionCandidate candidate : candidatesToProcess) {
                try {
                    if (candidate == null) {
                        failedCount++;
                        CandidateFailureSummary failure = new CandidateFailureSummary(source, null, "NULL_CANDIDATE", "Candidate in batch is null");
                        failureSummaries.add(failure);
                        metrics.recordError(source, failure.failureCategory());
                        continue;
                    }

                    if (candidate.source() != null && candidate.source() != source) {
                        failedCount++;
                        String safeExtId = candidate.externalJobId() != null && !candidate.externalJobId().isBlank()
                                ? candidate.externalJobId().trim()
                                : null;
                        CandidateFailureSummary failure = new CandidateFailureSummary(
                                source,
                                safeExtId,
                                "SOURCE_MISMATCH",
                                "Candidate source '" + candidate.source() + "' does not match execution context source '" + source + "'"
                        );
                        failureSummaries.add(failure);
                        metrics.recordError(source, failure.failureCategory());
                        continue;
                    }

                    String externalId = candidate.externalJobId();
                    if (externalId == null || externalId.isBlank()) {
                        failedCount++;
                        CandidateFailureSummary failure = new CandidateFailureSummary(source, null, "MISSING_EXTERNAL_ID", "Candidate externalJobId is missing or blank");
                        failureSummaries.add(failure);
                        metrics.recordError(source, failure.failureCategory());
                        continue;
                    }

                    if (deduplicationSession.isDuplicate(candidate)) {
                        skippedCount++;
                        String normalizedExternalId = externalId.trim();
                        CandidateFailureSummary failure = new CandidateFailureSummary(source, normalizedExternalId, "DUPLICATE_IN_BATCH", "Duplicate externalJobId in batch: " + normalizedExternalId);
                        failureSummaries.add(failure);
                        metrics.recordError(source, failure.failureCategory());
                        metrics.recordDuplicatesSkipped(source, 1);
                        continue;
                    }

                    JobCandidateIngestionResult ingestionResult = jobIngestionService.ingestCandidate(candidate);
                    if (ingestionResult.duplicateResult() != null && ingestionResult.duplicateResult().isDuplicate()) {
                        metrics.recordDuplicateDetected(source, ingestionResult.duplicateResult().classification());
                    }
                    if (ingestionResult.action() == CandidateIngestionAction.CREATED
                            || ingestionResult.action() == CandidateIngestionAction.DETECTED_CROSS_SOURCE_DUPLICATE) {
                        createdCount++;
                    } else if (ingestionResult.action() == CandidateIngestionAction.UPDATED) {
                        updatedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (JobValidationException ex) {
                    failedCount++;
                    log.warn("[runId={}] Candidate validation failed for source '{}' [externalJobId={}]: {}",
                            effectiveRunId, source, candidate != null ? candidate.externalJobId() : null, ex.getMessage());
                    String failureCategory = (ex.getFailureCategory() != null && !ex.getFailureCategory().isBlank())
                            ? ex.getFailureCategory()
                            : "VALIDATION_ERROR";
                    CandidateFailureSummary failure = new CandidateFailureSummary(
                            source,
                            candidate != null ? candidate.externalJobId() : null,
                            failureCategory,
                            sanitizeMessage(ex.getMessage())
                    );
                    failureSummaries.add(failure);
                    metrics.recordError(source, failure.failureCategory());
                } catch (DataIntegrityViolationException ex) {
                    failedCount++;
                    log.warn("[runId={}] Database constraint violation for source '{}' [externalJobId={}]: {}",
                            effectiveRunId, source, candidate != null ? candidate.externalJobId() : null, ex.getMessage());
                    CandidateFailureSummary failure = new CandidateFailureSummary(
                            source,
                            candidate != null ? candidate.externalJobId() : null,
                            "DATA_INTEGRITY_ERROR",
                            "Database constraint violation during candidate persistence"
                    );
                    failureSummaries.add(failure);
                    metrics.recordError(source, failure.failureCategory());
                } catch (IllegalArgumentException ex) {
                    failedCount++;
                    log.warn("[runId={}] Invalid candidate argument for source '{}' [externalJobId={}]: {}",
                            effectiveRunId, source, candidate != null ? candidate.externalJobId() : null, ex.getMessage());
                    CandidateFailureSummary failure = new CandidateFailureSummary(
                            source,
                            candidate != null ? candidate.externalJobId() : null,
                            "INVALID_ARGUMENT",
                            sanitizeMessage(ex.getMessage())
                    );
                    failureSummaries.add(failure);
                    metrics.recordError(source, failure.failureCategory());
                } catch (Exception ex) {
                    failedCount++;
                    log.error("[runId={}] Unexpected failure processing candidate for source '{}' [externalJobId={}]",
                            effectiveRunId, source, candidate != null ? candidate.externalJobId() : null, ex);
                    CandidateFailureSummary failure = new CandidateFailureSummary(
                            source,
                            candidate != null ? candidate.externalJobId() : null,
                            "PROCESSING_ERROR",
                            "Unexpected error processing candidate"
                    );
                    failureSummaries.add(failure);
                    metrics.recordError(source, failure.failureCategory());
                }
            }

            Instant completedAt = Instant.now();
            int successfulCount = createdCount + updatedCount;
            boolean hasErrors = failedCount > 0;
            runContext = hasErrors ? runContext.toCompletedWithErrors() : runContext.toCompleted();

            if (hasErrors) {
                log.warn("[runId={}] Ingestion run completed with errors for source '{}' [status={}, created={}, updated={}, skipped={}, failed={}, successful={}]",
                        effectiveRunId, source, runContext.status(), createdCount, updatedCount, skippedCount, failedCount, successfulCount);
            } else {
                log.info("[runId={}] Ingestion run completed for source '{}' [status={}, created={}, updated={}, skipped={}, failed={}, successful={}]",
                        effectiveRunId, source, runContext.status(), createdCount, updatedCount, skippedCount, failedCount, successfulCount);
            }

            log.info("[runId={}] Source completed: '{}' [status={}]", effectiveRunId, source, runContext.status());

            metrics.recordCandidateCounts(source, candidatesReceived, candidatesConsidered, createdCount, updatedCount, skippedCount, failedCount);
            metrics.recordSourceOutcome(source, runContext.status(), sourceTimerSample);
            if (isTopLevelRun) {
                metrics.recordRunOutcome(runContext.status(), runTimerSample);
            }

            return new IngestionRunResult(
                    effectiveRunId,
                    source,
                    runContext.status(),
                    startedAt,
                    completedAt,
                    candidatesConsidered,
                    createdCount,
                    updatedCount,
                    skippedCount,
                    failedCount,
                    successfulCount,
                    failureSummaries,
                    candidatesReceived,
                    candidatesConsidered
            );
        } catch (Exception ex) {
            log.error("[runId={}] Ingestion run failed fatally for source '{}'", effectiveRunId, source, ex);
            runContext = runContext.toFailed();
            log.error("[runId={}] Ingestion run failed for source '{}' [status={}]", effectiveRunId, source, runContext.status());

            CandidateFailureSummary failure = new CandidateFailureSummary(source, null, "FATAL_ORCHESTRATION_ERROR", sanitizeMessage(ex.getMessage()));
            metrics.recordError(source, IngestionErrorCategory.INFRASTRUCTURE);
            metrics.recordSourceOutcome(source, IngestionRunStatus.FAILED, sourceTimerSample);
            if (isTopLevelRun) {
                metrics.recordRunOutcome(IngestionRunStatus.FAILED, runTimerSample);
            }

            return IngestionRunResult.builder()
                    .runId(effectiveRunId)
                    .source(source)
                    .status(IngestionRunStatus.FAILED)
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .totalCandidates(0)
                    .candidatesReceived(0)
                    .candidatesConsidered(0)
                    .failedCount(1)
                    .addFailure(failure)
                    .build();
        }
    }

    /**
     * Ingests from all configured and enabled sources sequentially with default criteria.
     */
    public MultiSourceIngestionRunResult ingestAllEnabledSources() {
        return ingestAllEnabledSources(JobFetchCriteria.of(null, null), UUID.randomUUID().toString());
    }

    /**
     * Ingests from all configured and enabled sources sequentially with specified criteria.
     */
    public MultiSourceIngestionRunResult ingestAllEnabledSources(JobFetchCriteria criteria) {
        return ingestAllEnabledSources(criteria, UUID.randomUUID().toString());
    }

    /**
     * Ingests from all configured and enabled sources sequentially with specified criteria and correlation run ID.
     * Enforces explicit lifecycle transitions: STARTED -> RUNNING -> COMPLETED / COMPLETED_WITH_ERRORS / FAILED.
     * Iterates over all sources configured as enabled in deterministic order, executing registered adapters
     * and safely reporting any enabled sources lacking an adapter without erasing successful results from other sources.
     * Records unified multi-source operational metrics.
     */
    public MultiSourceIngestionRunResult ingestAllEnabledSources(JobFetchCriteria criteria, String runId) {
        String effectiveRunId = resolveRunId(runId);
        Instant startedAt = Instant.now();
        Timer.Sample runTimerSample = metrics.startTimer();
        metrics.recordRunStarted();

        IngestionRunContext runContext;
        Set<JobSource> enabledSources;
        try {
            enabledSources = controlPolicy.getEnabledSources();
            runContext = IngestionRunContext.start(effectiveRunId, enabledSources);
        } catch (Exception ex) {
            log.error("[runId={}] Ingestion run failed fatally during multi-source initialization", effectiveRunId, ex);
            runContext = IngestionRunContext.start(effectiveRunId, Set.of()).toFailed();
            log.error("[runId={}] Ingestion run failed [status={}]", effectiveRunId, runContext.status());
            metrics.recordError(null, IngestionErrorCategory.INFRASTRUCTURE);
            metrics.recordRunOutcome(IngestionRunStatus.FAILED, runTimerSample);
            return new MultiSourceIngestionRunResult(
                    effectiveRunId,
                    IngestionRunStatus.FAILED,
                    startedAt,
                    Instant.now(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    List.of(),
                    0,
                    0
            );
        }

        log.info("[runId={}] Ingestion run started for {} configured enabled sources: {} [status={}]",
                effectiveRunId, enabledSources.size(), enabledSources, runContext.status());

        try {
            runContext = runContext.toRunning();
            log.info("[runId={}] Ingestion run entered RUNNING [status={}]", effectiveRunId, runContext.status());

            List<IngestionRunResult> sourceResults = new ArrayList<>();
            int totalReceived = 0;
            int totalConsidered = 0;
            int totalCreated = 0;
            int totalUpdated = 0;
            int totalSkipped = 0;
            int totalFailed = 0;
            int totalSuccessful = 0;

            for (JobSource source : enabledSources) {
                log.info("[runId={}] Source started: '{}'", effectiveRunId, source);
                IngestionRunResult result = ingestSourceInternal(source, criteria, effectiveRunId, false);
                sourceResults.add(result);
                totalReceived += result.candidatesReceived();
                totalConsidered += result.candidatesConsidered();
                totalCreated += result.createdCount();
                totalUpdated += result.updatedCount();
                totalSkipped += result.skippedCount();
                totalFailed += result.failedCount();
                totalSuccessful += result.successfulCount();

                if (result.status() == IngestionRunStatus.COMPLETED) {
                    log.info("[runId={}] Source completed: '{}' [status={}, successful={}, failed={}]",
                            effectiveRunId, source, result.status(), result.successfulCount(), result.failedCount());
                } else {
                    log.warn("[runId={}] Source failed: '{}' [status={}, successful={}, failed={}]",
                            effectiveRunId, source, result.status(), result.successfulCount(), result.failedCount());
                }
            }

            Instant completedAt = Instant.now();

            boolean anyFatalSource = sourceResults.stream().anyMatch(r -> r.status() == IngestionRunStatus.FAILED);
            boolean anyErrors = totalFailed > 0 || sourceResults.stream().anyMatch(r -> r.status() == IngestionRunStatus.COMPLETED_WITH_ERRORS);

            IngestionRunStatus finalStatus;
            if (anyErrors || anyFatalSource) {
                finalStatus = IngestionRunStatus.COMPLETED_WITH_ERRORS;
                runContext = runContext.toCompletedWithErrors();
                log.warn("[runId={}] Ingestion run completed with errors [status={}, sources={}, received={}, considered={}, created={}, updated={}, skipped={}, failed={}]",
                        effectiveRunId, finalStatus, sourceResults.size(), totalReceived, totalConsidered, totalCreated, totalUpdated, totalSkipped, totalFailed);
            } else {
                finalStatus = IngestionRunStatus.COMPLETED;
                runContext = runContext.toCompleted();
                log.info("[runId={}] Ingestion run completed [status={}, sources={}, received={}, considered={}, created={}, updated={}, skipped={}, failed={}]",
                        effectiveRunId, finalStatus, sourceResults.size(), totalReceived, totalConsidered, totalCreated, totalUpdated, totalSkipped, totalFailed);
            }

            metrics.recordRunOutcome(finalStatus, runTimerSample);

            return new MultiSourceIngestionRunResult(
                    effectiveRunId,
                    finalStatus,
                    startedAt,
                    completedAt,
                    sourceResults.size(),
                    totalConsidered,
                    totalCreated,
                    totalUpdated,
                    totalSkipped,
                    totalFailed,
                    totalSuccessful,
                    sourceResults,
                    totalReceived,
                    totalConsidered
            );
        } catch (Exception ex) {
            log.error("[runId={}] Ingestion run failed fatally during multi-source execution", effectiveRunId, ex);
            runContext = runContext.toFailed();
            log.error("[runId={}] Ingestion run failed [status={}]", effectiveRunId, runContext.status());
            metrics.recordError(null, IngestionErrorCategory.INFRASTRUCTURE);
            metrics.recordRunOutcome(IngestionRunStatus.FAILED, runTimerSample);
            return new MultiSourceIngestionRunResult(
                    effectiveRunId,
                    IngestionRunStatus.FAILED,
                    startedAt,
                    Instant.now(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    List.of(),
                    0,
                    0
            );
        }
    }

    private String resolveRunId(String runId) {
        return (runId != null && !runId.isBlank()) ? runId.trim() : UUID.randomUUID().toString();
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Operation failed";
        }
        String sanitized = message.trim();
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 197) + "...";
        }
        return sanitized;
    }
}

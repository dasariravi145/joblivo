package com.joblivo.job.ingestion;

import com.joblivo.job.exception.JobSourceAdapterException;
import com.joblivo.job.model.JobSource;

import java.util.List;

/**
 * Service Provider Interface (SPI) defining the contract for Job Discovery source adapters.
 * <p>
 * <b>Architectural Responsibility Boundaries:</b>
 * <ul>
 *   <li><b>SOURCE ADAPTER (this interface):</b> Responsible strictly for source-specific communication,
 *       retrieval, and mapping raw source records into {@link JobIngestionCandidate} instances.
 *       Adapters are stateless and contain no user-specific credentials, tokens, or sessions.</li>
 *   <li><b>INGESTION ORCHESTRATOR:</b> Controls the execution lifecycle, enforces candidate limits,
 *       manages batch boundaries, provides candidate- and source-level failure isolation, and records metrics.</li>
 *   <li><b>JOB NORMALIZER:</b> Performs deterministic in-memory normalization, string trimming,
 *       range validation, and attribute sanitization.</li>
 *   <li><b>PERSISTENCE SERVICE:</b> Manages transactional persistence, duplicate resolution,
 *       identity uniqueness, and data loss protection.</li>
 * </ul>
 * <p>
 * <b>Security & Compliance Invariants:</b>
 * <ul>
 *   <li>Adapters must NEVER persist or log credentials, secrets, passwords, or authorization headers.</li>
 *   <li>Adapters must NEVER bypass CAPTCHA, multi-factor authentication, or rate limits.</li>
 *   <li>Adapters must NEVER execute scraping of prohibited platforms or automate unauthorized browser sessions.</li>
 *   <li>Adapters must NEVER invoke AI models, semantic ranking, or job matching.</li>
 *   <li>Future source integrations must utilize only authorized, official partner APIs or company-approved feeds.</li>
 * </ul>
 */
public interface JobSourceAdapter {

    /**
     * Returns the primary deterministic source identity handled by this adapter.
     * Every adapter implementation must correspond to exactly one {@link JobSource}.
     *
     * @return non-null {@link JobSource} enum
     */
    default JobSource source() {
        return getSource();
    }

    /**
     * Legacy getter alias for {@link #source()} preserving backward compatibility.
     *
     * @return non-null {@link JobSource} enum
     */
    JobSource getSource();

    /**
     * Primary canonical candidate retrieval contract for discovering and fetching candidate job postings.
     * <p>
     * <b>Contract Expectations:</b>
     * <ul>
     *   <li>Must return a non-null {@link List} of {@link JobIngestionCandidate} instances.</li>
     *   <li>Must return an empty list ({@code List.of()}) when no candidates are found; must never return {@code null}.</li>
     *   <li>Must return candidates in deterministic source order (e.g. natural source pagination/chronological sequence).
     *       Random sampling or AI/semantic sorting is strictly prohibited.</li>
     *   <li>Global candidate limits are enforced authoritatively by the orchestration layer; adapters may use
     *       {@link JobSourceExecutionContext#candidateLimit()} as a retrieval hint to avoid fetching unnecessary pages.</li>
     *   <li>Must NOT perform database writes, transactional persistence, or repository lookups.</li>
     *   <li>Must NOT perform cross-source duplicate detection, matching, or scoring.</li>
     *   <li>On unrecoverable communication or mapping failure, must throw a {@link JobSourceAdapterException}.</li>
     * </ul>
     *
     * @param context immutable execution context providing correlation runId, source identity, and effective candidateLimit
     * @return non-null list of raw {@link JobIngestionCandidate} instances in deterministic source order
     * @throws JobSourceAdapterException on unrecoverable adapter errors
     */
    default List<JobIngestionCandidate> fetchIngestionCandidates(JobSourceExecutionContext context) {
        return fetchJobs(context);
    }

    /**
     * Candidate retrieval method alias for source adapters.
     *
     * @param context immutable execution context providing correlation runId, source identity, and effective candidateLimit
     * @return non-null list of raw {@link JobIngestionCandidate} instances in deterministic source order
     * @throws JobSourceAdapterException on unrecoverable adapter errors
     */
    default List<JobIngestionCandidate> fetchJobs(JobSourceExecutionContext context) {
        return List.of();
    }
}

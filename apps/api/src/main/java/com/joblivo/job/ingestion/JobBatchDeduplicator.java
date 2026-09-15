package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic batch-level deduplication component for Job Discovery ingestion.
 * <p>
 * Hardens the ingestion pipeline against identical source jobs appearing multiple times within a single
 * adapter response or ingestion execution. Enforces identity strictly using {@code SOURCE + EXTERNAL_JOB_ID}.
 * <p>
 * Key Invariants:
 * <ul>
 *     <li>Preserves the first-seen candidate for each canonical {@code (source, externalJobId)} pair.</li>
 *     <li>Preserves original candidate ordering without re-sorting or alteration.</li>
 *     <li>Never mutates candidate contents.</li>
 *     <li>Candidates with missing/blank {@code externalJobId} are never grouped together or fabricated;
 *         each passes through for explicit validation/isolation downstream.</li>
 *     <li>Uses execution-local state only (thread-safe across concurrent ingestion runs).</li>
 *     <li>Strictly avoids semantic comparison, title/company matching, AI/vector search, or cross-source logic.</li>
 * </ul>
 */
@Component
public class JobBatchDeduplicator {

    /**
     * Immutable outcome of a batch deduplication operation on a list of candidates.
     */
    public record BatchDeduplicationResult(
            List<JobIngestionCandidate> uniqueCandidates,
            int duplicateCount,
            List<JobIngestionCandidate> duplicatesSkipped
    ) {
        public BatchDeduplicationResult {
            uniqueCandidates = uniqueCandidates != null
                    ? Collections.unmodifiableList(new ArrayList<>(uniqueCandidates))
                    : List.of();
            duplicatesSkipped = duplicatesSkipped != null
                    ? Collections.unmodifiableList(new ArrayList<>(duplicatesSkipped))
                    : List.of();
        }
    }

    /**
     * Filters an ordered list of candidates, discarding duplicate occurrences of the same source + externalJobId.
     * Preserves first-seen candidate and original candidate ordering.
     *
     * @param candidates     ordered candidate list from source adapter
     * @param expectedSource expected source of the ingestion batch
     * @return result containing unique candidates in original order, duplicate count, and skipped candidates
     */
    public BatchDeduplicationResult deduplicate(List<JobIngestionCandidate> candidates, JobSource expectedSource) {
        if (candidates == null || candidates.isEmpty()) {
            return new BatchDeduplicationResult(List.of(), 0, List.of());
        }

        Session session = createSession(expectedSource);
        List<JobIngestionCandidate> uniqueCandidates = new ArrayList<>(candidates.size());
        List<JobIngestionCandidate> duplicates = new ArrayList<>();

        for (JobIngestionCandidate candidate : candidates) {
            if (candidate == null) {
                // Preserved in sequence so downstream handles NULL_CANDIDATE
                uniqueCandidates.add(null);
                continue;
            }

            String externalId = candidate.externalJobId();
            if (externalId == null || externalId.isBlank()) {
                // Missing external IDs must not collapse together; preserved for downstream MISSING_EXTERNAL_ID
                uniqueCandidates.add(candidate);
                continue;
            }

            if (session.isDuplicate(candidate)) {
                duplicates.add(candidate);
            } else {
                uniqueCandidates.add(candidate);
            }
        }

        return new BatchDeduplicationResult(uniqueCandidates, duplicates.size(), duplicates);
    }

    /**
     * Creates a new, execution-local deduplication session for stateful iteration across candidates.
     * Guarantees that concurrent ingestion runs never share mutable state.
     *
     * @param expectedSource expected source for this execution batch
     * @return isolated execution session
     */
    public Session createSession(JobSource expectedSource) {
        return new Session(expectedSource);
    }

    /**
     * Execution-local session tracking seen canonical identities within a single batch.
     * Non-static, thread-isolated, and garbage collected at the end of each ingestion execution.
     */
    public static class Session {
        private final JobSource expectedSource;
        private final Set<JobBatchIdentity> seenIdentities = new HashSet<>();
        private final List<JobIngestionCandidate> duplicates = new ArrayList<>();
        private int duplicateCount = 0;

        public Session(JobSource expectedSource) {
            this.expectedSource = expectedSource;
        }

        /**
         * Evaluates whether a candidate is a duplicate within this batch session.
         * If it is the first time this canonical identity is seen, it is recorded and returns {@code false}.
         * If already seen, it increments duplicate counters and returns {@code true}.
         * <p>
         * If the candidate is null, has a missing external ID, or has a mismatched source, returns {@code false}
         * so it can be handled by dedicated downstream validation without collapsing records.
         *
         * @param candidate candidate to inspect
         * @return {@code true} if this candidate is a duplicate occurrence; {@code false} otherwise
         */
        public boolean isDuplicate(JobIngestionCandidate candidate) {
            if (candidate == null) {
                return false;
            }
            String externalId = candidate.externalJobId();
            if (externalId == null || externalId.isBlank()) {
                return false;
            }
            JobSource candidateSource = candidate.source() != null ? candidate.source() : expectedSource;
            if (expectedSource != null && candidateSource != expectedSource) {
                return false;
            }

            JobBatchIdentity identity = new JobBatchIdentity(candidateSource, externalId);
            if (!seenIdentities.add(identity)) {
                duplicateCount++;
                duplicates.add(candidate);
                return true;
            }
            return false;
        }

        /**
         * Checks if a source + externalJobId pair has already been seen in this batch session.
         *
         * @param source        the job source
         * @param externalJobId the external job ID
         * @return {@code true} if already seen in this session; {@code false} otherwise
         */
        public boolean isDuplicate(JobSource source, String externalJobId) {
            if (source == null || externalJobId == null || externalJobId.isBlank()) {
                return false;
            }
            JobBatchIdentity identity = new JobBatchIdentity(source, externalJobId);
            if (!seenIdentities.add(identity)) {
                duplicateCount++;
                return true;
            }
            return false;
        }

        public int getDuplicateCount() {
            return duplicateCount;
        }

        public List<JobIngestionCandidate> getDuplicates() {
            return Collections.unmodifiableList(duplicates);
        }

        public Set<JobBatchIdentity> getSeenIdentities() {
            return Collections.unmodifiableSet(seenIdentities);
        }

        public JobSource getExpectedSource() {
            return expectedSource;
        }
    }
}

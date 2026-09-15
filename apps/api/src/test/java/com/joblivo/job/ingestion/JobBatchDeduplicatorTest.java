package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobBatchDeduplicator Unit Tests")
class JobBatchDeduplicatorTest {

    private JobBatchDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new JobBatchDeduplicator();
    }

    private JobIngestionCandidate candidate(JobSource source, String externalJobId, String title) {
        return JobIngestionCandidate.builder()
                .source(source)
                .externalJobId(externalJobId)
                .title(title)
                .companyName("Acme Corp")
                .workMode(JobWorkMode.REMOTE)
                .employmentType(JobEmploymentType.FULL_TIME)
                .applicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE)
                .build();
    }

    @Nested
    @DisplayName("Canonical JobBatchIdentity Tests")
    class IdentityTests {

        @Test
        @DisplayName("Identity trims externalJobId safely")
        void trimsExternalJobId() {
            JobBatchIdentity id1 = new JobBatchIdentity(JobSource.LINKEDIN, "  job-101  ");
            JobBatchIdentity id2 = new JobBatchIdentity(JobSource.LINKEDIN, "job-101");

            assertThat(id1.externalJobId()).isEqualTo("job-101");
            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("Identity throws on null or blank inputs in canonical constructor")
        void rejectsInvalidInputs() {
            assertThatThrownBy(() -> new JobBatchIdentity(null, "job-101"))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new JobBatchIdentity(JobSource.LINKEDIN, null))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new JobBatchIdentity(JobSource.LINKEDIN, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Optional factory of() returns empty on null or blank inputs")
        void optionalFactorySafelyHandlesInvalidInputs() {
            assertThat(JobBatchIdentity.of(null, "job-101")).isEmpty();
            assertThat(JobBatchIdentity.of(JobSource.LINKEDIN, null)).isEmpty();
            assertThat(JobBatchIdentity.of(JobSource.LINKEDIN, "   ")).isEmpty();
            assertThat(JobBatchIdentity.of(JobSource.LINKEDIN, "job-101")).isPresent();
        }

        @Test
        @DisplayName("Same externalJobId across different sources produces different identities")
        void differentSourcesAreDistinct() {
            JobBatchIdentity id1 = new JobBatchIdentity(JobSource.LINKEDIN, "job-101");
            JobBatchIdentity id2 = new JobBatchIdentity(JobSource.NAUKRI, "job-101");

            assertThat(id1).isNotEqualTo(id2);
            assertThat(id1.hashCode()).isNotEqualTo(id2.hashCode());
        }
    }

    @Nested
    @DisplayName("Batch Deduplication Filter Tests")
    class FilterTests {

        @Test
        @DisplayName("1. No duplicates -> all candidates processed in original order")
        void noDuplicatesAllProcessed() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-1", "Backend Dev");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "ext-2", "Frontend Dev");
            JobIngestionCandidate c3 = candidate(JobSource.LINKEDIN, "ext-3", "DevOps Engineer");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3),
                    JobSource.LINKEDIN
            );

            assertThat(result.uniqueCandidates()).containsExactly(c1, c2, c3);
            assertThat(result.duplicateCount()).isEqualTo(0);
            assertThat(result.duplicatesSkipped()).isEmpty();
        }

        @Test
        @DisplayName("2. Duplicate source + externalJobId -> only first candidate processed")
        void duplicateSourceAndExternalJobIdOnlyFirstRetained() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-1", "First Occurrence");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "ext-1", "Second Occurrence");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2),
                    JobSource.LINKEDIN
            );

            assertThat(result.uniqueCandidates()).containsExactly(c1);
            assertThat(result.duplicateCount()).isEqualTo(1);
            assertThat(result.duplicatesSkipped()).containsExactly(c2);
        }

        @Test
        @DisplayName("3. Multiple duplicate occurrences -> only first occurrence retained")
        void multipleDuplicateOccurrencesOnlyFirstRetained() {
            JobIngestionCandidate c1 = candidate(JobSource.NAUKRI, "ext-12345", "Occurrence 1");
            JobIngestionCandidate c2 = candidate(JobSource.NAUKRI, "ext-12346", "Unique 1");
            JobIngestionCandidate c3 = candidate(JobSource.NAUKRI, "ext-12345", "Occurrence 2");
            JobIngestionCandidate c4 = candidate(JobSource.NAUKRI, "ext-12347", "Unique 2");
            JobIngestionCandidate c5 = candidate(JobSource.NAUKRI, "ext-12346", "Occurrence 2 of second");
            JobIngestionCandidate c6 = candidate(JobSource.NAUKRI, "ext-12345", "Occurrence 3");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3, c4, c5, c6),
                    JobSource.NAUKRI
            );

            assertThat(result.uniqueCandidates()).containsExactly(c1, c2, c4);
            assertThat(result.duplicateCount()).isEqualTo(3);
            assertThat(result.duplicatesSkipped()).containsExactly(c3, c5, c6);
        }

        @Test
        @DisplayName("4. Candidate ordering is strictly preserved")
        void orderingIsPreserved() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-B", "Job B");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "ext-A", "Job A");
            JobIngestionCandidate c3 = candidate(JobSource.LINKEDIN, "ext-C", "Job C");
            JobIngestionCandidate c4 = candidate(JobSource.LINKEDIN, "ext-B", "Job B duplicate");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3, c4),
                    JobSource.LINKEDIN
            );

            assertThat(result.uniqueCandidates()).containsExactly(c1, c2, c3);
        }

        @Test
        @DisplayName("5. Duplicate count and reporting are accurate")
        void duplicateCountAccurate() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-1", "Role 1");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "ext-1", "Role 1 duplicate");
            JobIngestionCandidate c3 = candidate(JobSource.LINKEDIN, "ext-2", "Role 2");
            JobIngestionCandidate c4 = candidate(JobSource.LINKEDIN, "ext-1", "Role 1 triplicate");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3, c4),
                    JobSource.LINKEDIN
            );

            assertThat(result.duplicateCount()).isEqualTo(2);
            assertThat(result.duplicatesSkipped()).hasSize(2);
            assertThat(result.uniqueCandidates()).hasSize(2);
        }

        @Test
        @DisplayName("6. Different externalJobIds are not treated as duplicates")
        void differentExternalJobIdsNotDuplicates() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-100", "Software Engineer");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "ext-101", "Software Engineer");
            JobIngestionCandidate c3 = candidate(JobSource.LINKEDIN, "ext-102", "Software Engineer");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3),
                    JobSource.LINKEDIN
            );

            assertThat(result.uniqueCandidates()).containsExactly(c1, c2, c3);
            assertThat(result.duplicateCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("7. Different sources with same externalJobId are not treated as duplicates")
        void differentSourcesSameExternalIdNotDuplicates() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "common-ext-1", "LinkedIn Role");
            JobIngestionCandidate c2 = candidate(JobSource.NAUKRI, "common-ext-1", "Naukri Role");

            JobBatchDeduplicator.Session session = deduplicator.createSession(null);

            assertThat(session.isDuplicate(c1)).isFalse();
            assertThat(session.isDuplicate(c2)).isFalse();
            assertThat(session.getDuplicateCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("8. Missing externalJobId does not collapse unrelated candidates together")
        void missingExternalJobIdDoesNotCollapse() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, null, "Job Without External ID 1");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "", "Job Without External ID 2");
            JobIngestionCandidate c3 = candidate(JobSource.LINKEDIN, "   ", "Job Without External ID 3");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2, c3),
                    JobSource.LINKEDIN
            );

            // None should be marked duplicate; all 3 must pass through for independent downstream validation
            assertThat(result.uniqueCandidates()).containsExactly(c1, c2, c3);
            assertThat(result.duplicateCount()).isEqualTo(0);
            assertThat(result.duplicatesSkipped()).isEmpty();
        }

        @Test
        @DisplayName("9. Trailing/leading whitespace in externalJobId is normalized deterministically")
        void whitespaceNormalizedExternalJobIdTreatedAsDuplicate() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-999", "Role");
            JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "  ext-999  ", "Role with spaces");

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    List.of(c1, c2),
                    JobSource.LINKEDIN
            );

            assertThat(result.uniqueCandidates()).containsExactly(c1);
            assertThat(result.duplicateCount()).isEqualTo(1);
            assertThat(result.duplicatesSkipped()).containsExactly(c2);
        }

        @Test
        @DisplayName("10. Null candidates in list are preserved in sequence for downstream handling")
        void nullCandidatesPreservedInSequence() {
            JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "ext-1", "Role 1");
            List<JobIngestionCandidate> input = new ArrayList<>();
            input.add(c1);
            input.add(null);
            input.add(c1); // duplicate

            JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                    input,
                    JobSource.LINKEDIN
            );

            assertThat(result.uniqueCandidates()).hasSize(2);
            assertThat(result.uniqueCandidates().get(0)).isEqualTo(c1);
            assertThat(result.uniqueCandidates().get(1)).isNull();
            assertThat(result.duplicateCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("11. Empty and null input lists return empty result safely")
        void emptyAndNullListsHandledSafely() {
            assertThat(deduplicator.deduplicate(null, JobSource.LINKEDIN).uniqueCandidates()).isEmpty();
            assertThat(deduplicator.deduplicate(List.of(), JobSource.LINKEDIN).uniqueCandidates()).isEmpty();
            assertThat(deduplicator.deduplicate(null, JobSource.LINKEDIN).duplicateCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Concurrency & Execution Isolation Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("12. Concurrent batch sessions do not share state or cross-contaminate duplicate sets")
        void concurrentSessionsDoNotShareState() throws ExecutionException, InterruptedException {
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                List<Callable<Integer>> tasks = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    final int threadId = i;
                    tasks.add(() -> {
                        // Each thread processes the EXACT SAME externalJobId ("shared-ext-id") across 3 candidates
                        // c1 (first seen -> unique)
                        // c2 (duplicate -> skipped)
                        // c3 (duplicate -> skipped)
                        JobIngestionCandidate c1 = candidate(JobSource.LINKEDIN, "shared-ext-id", "Thread " + threadId + " Role 1");
                        JobIngestionCandidate c2 = candidate(JobSource.LINKEDIN, "shared-ext-id", "Thread " + threadId + " Role 2");
                        JobIngestionCandidate c3 = candidate(JobSource.LINKEDIN, "shared-ext-id", "Thread " + threadId + " Role 3");

                        JobBatchDeduplicator.BatchDeduplicationResult result = deduplicator.deduplicate(
                                List.of(c1, c2, c3),
                                JobSource.LINKEDIN
                        );

                        // If state was shared statically across sessions, threads running later would report c1 as duplicate!
                        // In isolated execution state, EVERY thread must report exactly 1 unique candidate and 2 duplicates.
                        assertThat(result.uniqueCandidates()).containsExactly(c1);
                        return result.duplicateCount();
                    });
                }

                List<Future<Integer>> futures = executor.invokeAll(tasks);
                for (Future<Integer> future : futures) {
                    assertThat(future.get()).isEqualTo(2);
                }
            } finally {
                executor.shutdown();
            }
        }
    }
}

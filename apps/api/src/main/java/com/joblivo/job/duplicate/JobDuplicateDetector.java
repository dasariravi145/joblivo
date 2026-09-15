package com.joblivo.job.duplicate;

import com.joblivo.job.Job;
import com.joblivo.job.JobRepository;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import com.joblivo.job.normalizer.JobUrlNormalizer;
import com.joblivo.job.normalizer.NormalizedJobCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Production internal service providing deterministic duplicate detection for Job records.
 * <p>
 * Determines whether two job representations represent the same underlying opportunity based on
 * three strict, deterministic signals evaluated in fixed priority order:
 * <ol>
 *   <li><strong>EXACT_SOURCE_DUPLICATE:</strong> Identical {@code (source, externalJobId)} composite identity.</li>
 *   <li><strong>EXACT_URL_DUPLICATE:</strong> Identical canonical, normalized HTTP/HTTPS job posting URL.</li>
 *   <li><strong>EXACT_CONTENT_DUPLICATE:</strong> Identical SHA-256 canonical content fingerprint.</li>
 *   <li><strong>NOT_DUPLICATE:</strong> When no deterministic evidence matches.</li>
 * </ol>
 * <p>
 * <strong>Boundaries & Constraints:</strong>
 * <ul>
 *   <li>Performs pure in-memory comparison: zero network requests, zero DNS lookups, zero AI/embeddings, zero database mutations.</li>
 *   <li>Never mutates, merges, or deletes job records.</li>
 *   <li>Rejects recruiter identity, salary, or posting dates as duplicate keys.</li>
 *   <li>Zero arbitrary fuzzy matching or percentage scores.</li>
 * </ul>
 */
@Component
public class JobDuplicateDetector {

    private final JobRepository jobRepository;

    /**
     * Default constructor for pure in-memory comparisons without repository dependencies.
     */
    public JobDuplicateDetector() {
        this.jobRepository = null;
    }

    /**
     * Spring constructor injecting {@link JobRepository} for database-backed stored job duplicate lookups.
     *
     * @param jobRepository the JPA repository for Job records
     */
    @Autowired(required = false)
    public JobDuplicateDetector(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Evaluates an incoming normalized candidate against stored jobs in the database using targeted queries.
     * Evaluates signals in strict priority order:
     * <ol>
     *   <li>Exact source identity: {@code findBySourceAndExternalJobId}</li>
     *   <li>Exact canonical URL: {@code findFirstByJobUrl}</li>
     *   <li>Exact content fingerprint: {@code findByTitleIgnoreCaseAndCompanyNameIgnoreCase} evaluated in-memory</li>
     * </ol>
     *
     * @param candidate the normalized job candidate
     * @return StoredJobDuplicateMatch containing deterministic result and matched Job if any
     */
    public StoredJobDuplicateMatch findStoredDuplicate(NormalizedJobCandidate candidate) {
        if (candidate == null) {
            return StoredJobDuplicateMatch.notDuplicate("Candidate must not be null");
        }
        if (jobRepository == null) {
            throw new IllegalStateException("JobRepository must be configured for stored-job duplicate detection");
        }

        // 1. EXACT SOURCE IDENTITY (Priority 1)
        if (candidate.sourceIdentity() != null) {
            Optional<Job> sourceMatch = jobRepository.findBySourceAndExternalJobId(
                    candidate.sourceIdentity().source(),
                    candidate.sourceIdentity().externalJobId()
            );
            if (sourceMatch.isPresent()) {
                Job job = sourceMatch.get();
                return StoredJobDuplicateMatch.of(
                        JobDuplicateResult.exactSourceDuplicate(
                                "Same source and external job ID",
                                job.getSource() + ":" + job.getExternalJobId()
                        ),
                        job
                );
            }
        }

        // 2. EXACT CANONICAL JOB URL (Priority 2)
        String canonicalUrl = safeCanonicalUrl(candidate.jobUrl());
        if (canonicalUrl != null) {
            Optional<Job> urlMatch = jobRepository.findFirstByJobUrl(canonicalUrl);
            if (urlMatch.isPresent()) {
                Job job = urlMatch.get();
                return StoredJobDuplicateMatch.of(
                        JobDuplicateResult.exactUrlDuplicate(
                                "Same canonical job URL",
                                canonicalUrl
                        ),
                        job
                );
            }
        }

        // 3. EXACT CONTENT FINGERPRINT (Priority 3)
        JobContentFingerprint candidateFp = JobContentFingerprint.fromNormalized(candidate);
        List<Job> contentCandidates = jobRepository.findByTitleIgnoreCaseAndCompanyNameIgnoreCase(
                candidate.title(),
                candidate.companyName()
        );
        if (contentCandidates != null) {
            for (Job job : contentCandidates) {
                JobContentFingerprint jobFp = JobContentFingerprint.fromJob(job);
                if (candidateFp.equals(jobFp)) {
                    return StoredJobDuplicateMatch.of(
                            JobDuplicateResult.exactContentDuplicate(
                                    "Same canonical job content fingerprint",
                                    candidateFp.hashHex()
                            ),
                            job
                    );
                }
            }
        }

        // 4. NOT DUPLICATE (Fallback)
        return StoredJobDuplicateMatch.notDuplicate("No deterministic duplicate evidence found against stored jobs");
    }

    /**
     * Evaluates an incoming normalized candidate against an in-memory collection of stored jobs.
     * Evaluates signals in strict priority order (Source -> URL -> Content).
     *
     * @param candidate  the normalized job candidate
     * @param storedJobs iterable of existing jobs to compare against
     * @return StoredJobDuplicateMatch containing deterministic result and matched Job if any
     */
    public StoredJobDuplicateMatch findStoredDuplicate(NormalizedJobCandidate candidate, Iterable<Job> storedJobs) {
        if (candidate == null) {
            return StoredJobDuplicateMatch.notDuplicate("Candidate must not be null");
        }
        if (storedJobs == null) {
            return StoredJobDuplicateMatch.notDuplicate("No stored jobs provided");
        }

        // 1. EXACT SOURCE IDENTITY (Priority 1)
        if (candidate.sourceIdentity() != null) {
            for (Job job : storedJobs) {
                if (job != null) {
                    Optional<JobSourceIdentity> id = JobSourceIdentity.of(job.getSource(), job.getExternalJobId());
                    if (id.isPresent() && id.get().equals(candidate.sourceIdentity())) {
                        return StoredJobDuplicateMatch.of(
                                JobDuplicateResult.exactSourceDuplicate(
                                        "Same source and external job ID",
                                        job.getSource() + ":" + job.getExternalJobId()
                                ),
                                job
                        );
                    }
                }
            }
        }

        // 2. EXACT CANONICAL JOB URL (Priority 2)
        String canonicalUrl = safeCanonicalUrl(candidate.jobUrl());
        if (canonicalUrl != null) {
            for (Job job : storedJobs) {
                if (job != null) {
                    String jobUrl = safeCanonicalUrl(job.getJobUrl());
                    if (canonicalUrl.equals(jobUrl)) {
                        return StoredJobDuplicateMatch.of(
                                JobDuplicateResult.exactUrlDuplicate(
                                        "Same canonical job URL",
                                        canonicalUrl
                                ),
                                job
                        );
                    }
                }
            }
        }

        // 3. EXACT CONTENT FINGERPRINT (Priority 3)
        JobContentFingerprint candidateFp = JobContentFingerprint.fromNormalized(candidate);
        for (Job job : storedJobs) {
            if (job != null) {
                JobContentFingerprint jobFp = JobContentFingerprint.fromJob(job);
                if (candidateFp.equals(jobFp)) {
                    return StoredJobDuplicateMatch.of(
                            JobDuplicateResult.exactContentDuplicate(
                                    "Same canonical job content fingerprint",
                                    candidateFp.hashHex()
                            ),
                            job
                    );
                }
            }
        }

        // 4. NOT DUPLICATE
        return StoredJobDuplicateMatch.notDuplicate("No deterministic duplicate evidence found against stored jobs");
    }

    /**
     * Convenience method returning only the {@link JobDuplicateResult} for an incoming normalized candidate.
     *
     * @param candidate the normalized job candidate
     * @return deterministic JobDuplicateResult
     */
    public JobDuplicateResult findDuplicate(NormalizedJobCandidate candidate) {
        return findStoredDuplicate(candidate).result();
    }

    /**
     * Convenience method returning only the {@link JobDuplicateResult} for an incoming normalized candidate against in-memory jobs.
     *
     * @param candidate  the normalized job candidate
     * @param storedJobs iterable of existing jobs
     * @return deterministic JobDuplicateResult
     */
    public JobDuplicateResult findDuplicate(NormalizedJobCandidate candidate, Iterable<Job> storedJobs) {
        return findStoredDuplicate(candidate, storedJobs).result();
    }

    /**
     * Compares two {@link Job} entities for duplicate evidence.
     *
     * @param job1 first job entity
     * @param job2 second job entity
     * @return deterministic JobDuplicateResult
     */
    public JobDuplicateResult compare(Job job1, Job job2) {
        Objects.requireNonNull(job1, "job1 must not be null");
        Objects.requireNonNull(job2, "job2 must not be null");

        JobSource source1 = job1.getSource();
        String extId1 = job1.getExternalJobId();
        String url1 = job1.getJobUrl();
        JobContentFingerprint fp1 = JobContentFingerprint.fromJob(job1);

        JobSource source2 = job2.getSource();
        String extId2 = job2.getExternalJobId();
        String url2 = job2.getJobUrl();
        JobContentFingerprint fp2 = JobContentFingerprint.fromJob(job2);

        return evaluateSignals(source1, extId1, url1, fp1, source2, extId2, url2, fp2);
    }

    /**
     * Compares two {@link JobIngestionCandidate} representations for duplicate evidence.
     *
     * @param c1 first candidate
     * @param c2 second candidate
     * @return deterministic JobDuplicateResult
     */
    public JobDuplicateResult compare(JobIngestionCandidate c1, JobIngestionCandidate c2) {
        Objects.requireNonNull(c1, "candidate1 must not be null");
        Objects.requireNonNull(c2, "candidate2 must not be null");

        JobSource source1 = c1.source();
        String extId1 = c1.externalJobId();
        String url1 = c1.jobUrl();
        JobContentFingerprint fp1 = JobContentFingerprint.fromCandidate(c1);

        JobSource source2 = c2.source();
        String extId2 = c2.externalJobId();
        String url2 = c2.jobUrl();
        JobContentFingerprint fp2 = JobContentFingerprint.fromCandidate(c2);

        return evaluateSignals(source1, extId1, url1, fp1, source2, extId2, url2, fp2);
    }

    /**
     * Compares an existing persisted {@link Job} entity with an incoming {@link JobIngestionCandidate}.
     *
     * @param existingJob existing persisted job
     * @param candidate   incoming ingestion candidate
     * @return deterministic JobDuplicateResult
     */
    public JobDuplicateResult compare(Job existingJob, JobIngestionCandidate candidate) {
        Objects.requireNonNull(existingJob, "existingJob must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");

        JobSource source1 = existingJob.getSource();
        String extId1 = existingJob.getExternalJobId();
        String url1 = existingJob.getJobUrl();
        JobContentFingerprint fp1 = JobContentFingerprint.fromJob(existingJob);

        JobSource source2 = candidate.source();
        String extId2 = candidate.externalJobId();
        String url2 = candidate.jobUrl();
        JobContentFingerprint fp2 = JobContentFingerprint.fromCandidate(candidate);

        return evaluateSignals(source1, extId1, url1, fp1, source2, extId2, url2, fp2);
    }

    /**
     * Evaluates discrete duplicate signals according to the strict priority rules.
     */
    public JobDuplicateResult evaluateSignals(
            JobSource source1, String extId1, String url1, JobContentFingerprint fp1,
            JobSource source2, String extId2, String url2, JobContentFingerprint fp2
    ) {
        // 1. EXACT SOURCE IDENTITY (Priority 1)
        Optional<JobSourceIdentity> id1 = JobSourceIdentity.of(source1, extId1);
        Optional<JobSourceIdentity> id2 = JobSourceIdentity.of(source2, extId2);
        if (id1.isPresent() && id2.isPresent() && id1.get().equals(id2.get())) {
            return JobDuplicateResult.exactSourceDuplicate(
                    "Same source and external job ID",
                    source1 + ":" + id1.get().externalJobId()
            );
        }

        // 2. EXACT CANONICAL JOB URL (Priority 2)
        String canonicalUrl1 = safeCanonicalUrl(url1);
        String canonicalUrl2 = safeCanonicalUrl(url2);
        if (canonicalUrl1 != null && canonicalUrl2 != null && canonicalUrl1.equals(canonicalUrl2)) {
            return JobDuplicateResult.exactUrlDuplicate(
                    "Same canonical job URL",
                    canonicalUrl1
            );
        }

        // 3. EXACT CONTENT FINGERPRINT (Priority 3)
        if (fp1 != null && fp2 != null && fp1.equals(fp2)) {
            return JobDuplicateResult.exactContentDuplicate(
                    "Same canonical job content fingerprint",
                    fp1.hashHex()
            );
        }

        // 4. NOT DUPLICATE (Fallback)
        return JobDuplicateResult.notDuplicate("No deterministic duplicate evidence found");
    }

    private String safeCanonicalUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            return JobUrlNormalizer.normalizeUrl(rawUrl);
        } catch (Exception ex) {
            // Malformed URL does not produce a valid canonical URL duplicate match
            return null;
        }
    }
}

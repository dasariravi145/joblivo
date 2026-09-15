package com.joblivo.job;

import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for normalized {@link Job} records.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    /**
     * Finds a normalized job by its composite source-neutral identity: source and external job ID.
     *
     * @param source        the job discovery origin
     * @param externalJobId the provider-specific job identifier
     * @return an Optional containing the matching Job, or empty
     */
    Optional<Job> findBySourceAndExternalJobId(JobSource source, String externalJobId);

    /**
     * Checks whether a job with the given source and external job ID already exists.
     *
     * @param source        the job discovery origin
     * @param externalJobId the provider-specific job identifier
     * @return true if the job exists, false otherwise
     */
    boolean existsBySourceAndExternalJobId(JobSource source, String externalJobId);

    /**
     * Finds a normalized job by its canonical immutable {@link JobSourceIdentity}.
     *
     * @param identity the canonical source identity
     * @return an Optional containing the matching Job, or empty
     */
    default Optional<Job> findBySourceIdentity(JobSourceIdentity identity) {
        if (identity == null) {
            return Optional.empty();
        }
        return findBySourceAndExternalJobId(identity.source(), identity.externalJobId());
    }

    /**
     * Checks whether a job with the given canonical {@link JobSourceIdentity} already exists.
     *
     * @param identity the canonical source identity
     * @return true if the job exists, false otherwise
     */
    default boolean existsBySourceIdentity(JobSourceIdentity identity) {
        if (identity == null) {
            return false;
        }
        return existsBySourceAndExternalJobId(identity.source(), identity.externalJobId());
    }

    /**
     * Finds the first normalized job matching the specified canonical job URL.
     * Used for deterministic URL duplicate detection across stored jobs.
     *
     * @param jobUrl the canonical job URL
     * @return an Optional containing the matching Job, or empty
     */
    Optional<Job> findFirstByJobUrl(String jobUrl);

    /**
     * Finds normalized jobs matching the specified title and company name case-insensitively.
     * Used as a bounded, targeted candidate query for content fingerprint evaluation without full-table scans.
     *
     * @param title       the job title
     * @param companyName the hiring organization name
     * @return list of matching jobs
     */
    List<Job> findByTitleIgnoreCaseAndCompanyNameIgnoreCase(String title, String companyName);
}

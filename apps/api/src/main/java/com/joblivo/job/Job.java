package com.joblivo.job;

import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceIdentity;
import com.joblivo.job.model.JobSourceProvenance;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a normalized, source-neutral job catalog record.
 * Maps to the PostgreSQL 'jobs' table.
 * <p>
 * Jobs are shared platform catalog assets, NOT owned by individual users.
 * Identity is determined by the composite pair: {@code (source, externalJobId)}.
 */
@Entity
@Table(
        name = "jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_jobs_source_external_id",
                columnNames = {"source", "external_job_id"}
        )
)
public class Job {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 50)
    private JobSource source;

    @Column(name = "external_job_id", nullable = false, updatable = false, length = 255)
    private String externalJobId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "recruiter_name", length = 255)
    private String recruiterName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location", length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 50)
    private JobWorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 50)
    private JobEmploymentType employmentType;

    @Column(name = "experience_min_years")
    private Integer experienceMinYears;

    @Column(name = "experience_max_years")
    private Integer experienceMaxYears;

    @Column(name = "salary_min", precision = 15, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 15, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", length = 10)
    private String salaryCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 20)
    private SalaryPeriod salaryPeriod;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(name = "company_url", length = 1000)
    private String companyUrl;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "discovered_at", nullable = false, updatable = false)
    private Instant discoveredAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_method", nullable = false, length = 50)
    private JobApplicationMethod applicationMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
        // JPA required constructor
    }

    public Job(JobSource source, String externalJobId, String title, String companyName) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.externalJobId = Objects.requireNonNull(externalJobId, "externalJobId must not be null").trim();
        this.title = Objects.requireNonNull(title, "title must not be null").trim();
        this.companyName = Objects.requireNonNull(companyName, "companyName must not be null").trim();
        this.workMode = JobWorkMode.UNKNOWN;
        this.employmentType = JobEmploymentType.UNKNOWN;
        this.applicationMethod = JobApplicationMethod.UNKNOWN;
        this.discoveredAt = Instant.now();
        this.lastSeenAt = this.discoveredAt;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (discoveredAt == null) {
            discoveredAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public JobSource getSource() {
        return source;
    }

    public String getExternalJobId() {
        return externalJobId;
    }

    /**
     * Returns the canonical, immutable source identity value object.
     *
     * @return canonical JobSourceIdentity
     */
    public JobSourceIdentity getSourceIdentity() {
        return new JobSourceIdentity(source, externalJobId);
    }

    /**
     * Returns the canonical, immutable source provenance representation for this job.
     *
     * @return canonical JobSourceProvenance
     */
    public JobSourceProvenance getSourceProvenance() {
        return JobSourceProvenance.from(this);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title must not be null").trim();
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = Objects.requireNonNull(companyName, "companyName must not be null").trim();
    }

    public String getRecruiterName() {
        return recruiterName;
    }

    public void setRecruiterName(String recruiterName) {
        this.recruiterName = recruiterName != null && !recruiterName.isBlank() ? recruiterName.trim() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null && !description.isBlank() ? description.trim() : null;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location != null && !location.isBlank() ? location.trim() : null;
    }

    public JobWorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(JobWorkMode workMode) {
        this.workMode = workMode != null ? workMode : JobWorkMode.UNKNOWN;
    }

    public JobEmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(JobEmploymentType employmentType) {
        this.employmentType = employmentType != null ? employmentType : JobEmploymentType.UNKNOWN;
    }

    public Integer getExperienceMinYears() {
        return experienceMinYears;
    }

    public void setExperienceMinYears(Integer experienceMinYears) {
        this.experienceMinYears = experienceMinYears;
    }

    public Integer getExperienceMaxYears() {
        return experienceMaxYears;
    }

    public void setExperienceMaxYears(Integer experienceMaxYears) {
        this.experienceMaxYears = experienceMaxYears;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal salaryMin) {
        this.salaryMin = salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String salaryCurrency) {
        this.salaryCurrency = salaryCurrency != null && !salaryCurrency.isBlank() ? salaryCurrency.trim().toUpperCase() : null;
    }

    public SalaryPeriod getSalaryPeriod() {
        return salaryPeriod;
    }

    public void setSalaryPeriod(SalaryPeriod salaryPeriod) {
        this.salaryPeriod = salaryPeriod;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl != null && !jobUrl.isBlank() ? jobUrl.trim() : null;
    }

    public String getCompanyUrl() {
        return companyUrl;
    }

    public void setCompanyUrl(String companyUrl) {
        this.companyUrl = companyUrl != null && !companyUrl.isBlank() ? companyUrl.trim() : null;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(Instant discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt != null ? lastSeenAt : Instant.now();
    }

    public JobApplicationMethod getApplicationMethod() {
        return applicationMethod;
    }

    public void setApplicationMethod(JobApplicationMethod applicationMethod) {
        this.applicationMethod = applicationMethod != null ? applicationMethod : JobApplicationMethod.UNKNOWN;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Job other)) return false;
        if (id != null && other.id != null) {
            return Objects.equals(id, other.id);
        }
        return Objects.equals(source, other.source) && Objects.equals(externalJobId, other.externalJobId);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hashCode(id);
        }
        return Objects.hash(source, externalJobId);
    }
}

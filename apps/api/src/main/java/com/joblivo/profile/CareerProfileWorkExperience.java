package com.joblivo.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a factual employment/work experience record belonging to a Master Career Profile.
 * Maps to the PostgreSQL 'career_profile_work_experiences' table.
 */
@Entity
@Table(name = "career_profile_work_experiences")
public class CareerProfileWorkExperience {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_profile_id", nullable = false)
    private CareerProfile careerProfile;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "job_title", nullable = false, length = 100)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "currently_working", nullable = false)
    private boolean currentlyWorking;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected CareerProfileWorkExperience() {
    }

    /**
     * Creates a new work experience entry for a career profile.
     */
    public CareerProfileWorkExperience(
            CareerProfile careerProfile,
            String companyName,
            String jobTitle,
            EmploymentType employmentType,
            LocalDate startDate,
            LocalDate endDate,
            boolean currentlyWorking,
            String location,
            String description,
            int displayOrder
    ) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "CareerProfile must not be null");
        this.companyName = Objects.requireNonNull(companyName, "Company name must not be null");
        this.jobTitle = Objects.requireNonNull(jobTitle, "Job title must not be null");
        this.employmentType = Objects.requireNonNull(employmentType, "Employment type must not be null");
        this.startDate = Objects.requireNonNull(startDate, "Start date must not be null");
        this.endDate = endDate;
        this.currentlyWorking = currentlyWorking;
        this.location = location;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    /**
     * Package-private constructor for testing and hydration.
     */
    CareerProfileWorkExperience(
            UUID id,
            CareerProfile careerProfile,
            String companyName,
            String jobTitle,
            EmploymentType employmentType,
            LocalDate startDate,
            LocalDate endDate,
            boolean currentlyWorking,
            String location,
            String description,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(careerProfile, companyName, jobTitle, employmentType, startDate, endDate, currentlyWorking, location, description, displayOrder);
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public CareerProfile getCareerProfile() {
        return careerProfile;
    }

    public void setCareerProfile(CareerProfile careerProfile) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "CareerProfile must not be null");
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = Objects.requireNonNull(companyName, "Company name must not be null");
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = Objects.requireNonNull(jobTitle, "Job title must not be null");
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = Objects.requireNonNull(employmentType, "Employment type must not be null");
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = Objects.requireNonNull(startDate, "Start date must not be null");
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isCurrentlyWorking() {
        return currentlyWorking;
    }

    public void setCurrentlyWorking(boolean currentlyWorking) {
        this.currentlyWorking = currentlyWorking;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
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
        if (o == null || getClass() != o.getClass()) return false;
        CareerProfileWorkExperience that = (CareerProfileWorkExperience) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CareerProfileWorkExperience{" +
                "id=" + id +
                ", careerProfileId=" + (careerProfile != null ? careerProfile.getId() : null) +
                ", companyName='" + companyName + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", employmentType=" + employmentType +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", currentlyWorking=" + currentlyWorking +
                ", displayOrder=" + displayOrder +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

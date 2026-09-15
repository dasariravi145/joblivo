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
 * JPA entity representing a factual project record belonging to a Master Career Profile.
 * Maps to the PostgreSQL 'career_profile_projects' table.
 */
@Entity
@Table(name = "career_profile_projects")
public class CareerProfileProject {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_profile_id", nullable = false)
    private CareerProfile careerProfile;

    @Column(name = "project_name", nullable = false, length = 100)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 30)
    private ProjectType projectType;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "currently_active", nullable = false)
    private boolean currentlyActive;

    @Column(name = "project_url", length = 500)
    private String projectUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected CareerProfileProject() {
    }

    /**
     * Creates a new project entry for a career profile.
     */
    public CareerProfileProject(
            CareerProfile careerProfile,
            String projectName,
            ProjectType projectType,
            String role,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            boolean currentlyActive,
            String projectUrl,
            int displayOrder
    ) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "CareerProfile must not be null");
        this.projectName = Objects.requireNonNull(projectName, "Project name must not be null").trim();
        this.projectType = Objects.requireNonNull(projectType, "Project type must not be null");
        this.role = (role != null && !role.isBlank()) ? role.trim() : null;
        this.description = (description != null && !description.isBlank()) ? description.trim() : null;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentlyActive = currentlyActive;
        this.projectUrl = (projectUrl != null && !projectUrl.isBlank()) ? projectUrl.trim() : null;
        this.displayOrder = displayOrder;
    }

    /**
     * Package-private constructor for testing and hydration.
     */
    CareerProfileProject(
            UUID id,
            CareerProfile careerProfile,
            String projectName,
            ProjectType projectType,
            String role,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            boolean currentlyActive,
            String projectUrl,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(careerProfile, projectName, projectType, role, description, startDate, endDate, currentlyActive, projectUrl, displayOrder);
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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = Objects.requireNonNull(projectName, "Project name must not be null").trim();
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public void setProjectType(ProjectType projectType) {
        this.projectType = Objects.requireNonNull(projectType, "Project type must not be null");
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = (role != null && !role.isBlank()) ? role.trim() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description != null && !description.isBlank()) ? description.trim() : null;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isCurrentlyActive() {
        return currentlyActive;
    }

    public void setCurrentlyActive(boolean currentlyActive) {
        this.currentlyActive = currentlyActive;
    }

    public String getProjectUrl() {
        return projectUrl;
    }

    public void setProjectUrl(String projectUrl) {
        this.projectUrl = (projectUrl != null && !projectUrl.isBlank()) ? projectUrl.trim() : null;
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
        CareerProfileProject that = (CareerProfileProject) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CareerProfileProject{" +
                "id=" + id +
                ", careerProfileId=" + (careerProfile != null ? careerProfile.getId() : null) +
                ", projectName='" + projectName + '\'' +
                ", projectType=" + projectType +
                ", role='" + role + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", currentlyActive=" + currentlyActive +
                ", projectUrl='" + projectUrl + '\'' +
                ", displayOrder=" + displayOrder +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

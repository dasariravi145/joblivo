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
 * JPA entity representing a factual education qualification record belonging to a Master Career Profile.
 * Maps to the PostgreSQL 'career_profile_education' table.
 */
@Entity
@Table(name = "career_profile_education")
public class CareerProfileEducation {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_profile_id", nullable = false)
    private CareerProfile careerProfile;

    @Column(name = "institution_name", nullable = false, length = 150)
    private String institutionName;

    @Column(name = "degree", length = 100)
    private String degree;

    @Column(name = "field_of_study", length = 100)
    private String fieldOfStudy;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", nullable = false, length = 50)
    private EducationLevel educationLevel;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "currently_studying", nullable = false)
    private boolean currentlyStudying;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "location", length = 150)
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
    protected CareerProfileEducation() {
    }

    /**
     * Creates a new education record for a career profile.
     */
    public CareerProfileEducation(
            CareerProfile careerProfile,
            String institutionName,
            String degree,
            String fieldOfStudy,
            EducationLevel educationLevel,
            LocalDate startDate,
            LocalDate endDate,
            boolean currentlyStudying,
            String grade,
            String location,
            String description,
            int displayOrder
    ) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "CareerProfile must not be null");
        this.institutionName = Objects.requireNonNull(institutionName, "Institution name must not be null").trim();
        this.degree = (degree != null && !degree.isBlank()) ? degree.trim() : null;
        this.fieldOfStudy = (fieldOfStudy != null && !fieldOfStudy.isBlank()) ? fieldOfStudy.trim() : null;
        this.educationLevel = Objects.requireNonNull(educationLevel, "Education level must not be null");
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentlyStudying = currentlyStudying;
        this.grade = (grade != null && !grade.isBlank()) ? grade.trim() : null;
        this.location = (location != null && !location.isBlank()) ? location.trim() : null;
        this.description = (description != null && !description.isBlank()) ? description.trim() : null;
        this.displayOrder = displayOrder;
    }

    /**
     * Package-private constructor for testing and hydration.
     */
    CareerProfileEducation(
            UUID id,
            CareerProfile careerProfile,
            String institutionName,
            String degree,
            String fieldOfStudy,
            EducationLevel educationLevel,
            LocalDate startDate,
            LocalDate endDate,
            boolean currentlyStudying,
            String grade,
            String location,
            String description,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(careerProfile, institutionName, degree, fieldOfStudy, educationLevel, startDate, endDate, currentlyStudying, grade, location, description, displayOrder);
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

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = Objects.requireNonNull(institutionName, "Institution name must not be null").trim();
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = (degree != null && !degree.isBlank()) ? degree.trim() : null;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = (fieldOfStudy != null && !fieldOfStudy.isBlank()) ? fieldOfStudy.trim() : null;
    }

    public EducationLevel getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(EducationLevel educationLevel) {
        this.educationLevel = Objects.requireNonNull(educationLevel, "Education level must not be null");
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

    public boolean isCurrentlyStudying() {
        return currentlyStudying;
    }

    public void setCurrentlyStudying(boolean currentlyStudying) {
        this.currentlyStudying = currentlyStudying;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = (grade != null && !grade.isBlank()) ? grade.trim() : null;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = (location != null && !location.isBlank()) ? location.trim() : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description != null && !description.isBlank()) ? description.trim() : null;
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
        if (!(o instanceof CareerProfileEducation that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

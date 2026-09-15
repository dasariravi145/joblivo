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
 * JPA entity representing a factual achievement record belonging to a Master Career Profile.
 * Maps to the PostgreSQL 'career_profile_achievements' table.
 */
@Entity
@Table(name = "career_profile_achievements")
public class CareerProfileAchievement {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_profile_id", nullable = false)
    private CareerProfile careerProfile;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type", nullable = false, length = 50)
    private AchievementType achievementType;

    @Column(name = "issuing_organization", length = 150)
    private String issuingOrganization;

    @Column(name = "achievement_date")
    private LocalDate achievementDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected CareerProfileAchievement() {
    }

    /**
     * Creates a new achievement entry for a career profile.
     */
    public CareerProfileAchievement(
            CareerProfile careerProfile,
            String title,
            AchievementType achievementType,
            String issuingOrganization,
            LocalDate achievementDate,
            String description,
            String url,
            int displayOrder
    ) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "CareerProfile must not be null");
        setTitle(title);
        this.achievementType = Objects.requireNonNull(achievementType, "Achievement type must not be null");
        setIssuingOrganization(issuingOrganization);
        this.achievementDate = achievementDate;
        setDescription(description);
        setUrl(url);
        setDisplayOrder(displayOrder);
    }

    /**
     * Constructor for testing and persistence hydration.
     */
    public CareerProfileAchievement(
            UUID id,
            CareerProfile careerProfile,
            String title,
            AchievementType achievementType,
            String issuingOrganization,
            LocalDate achievementDate,
            String description,
            String url,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(careerProfile, title, achievementType, issuingOrganization, achievementDate, description, url, displayOrder);
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be null or blank");
        }
        this.title = title.trim();
    }

    public AchievementType getAchievementType() {
        return achievementType;
    }

    public void setAchievementType(AchievementType achievementType) {
        this.achievementType = Objects.requireNonNull(achievementType, "Achievement type must not be null");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = (description != null && !description.isBlank()) ? description.trim() : null;
    }

    public LocalDate getAchievementDate() {
        return achievementDate;
    }

    public void setAchievementDate(LocalDate achievementDate) {
        this.achievementDate = achievementDate;
    }

    public String getIssuingOrganization() {
        return issuingOrganization;
    }

    public void setIssuingOrganization(String issuingOrganization) {
        this.issuingOrganization = (issuingOrganization != null && !issuingOrganization.isBlank())
                ? issuingOrganization.trim()
                : null;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = (url != null && !url.isBlank()) ? url.trim() : null;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
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
        CareerProfileAchievement that = (CareerProfileAchievement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

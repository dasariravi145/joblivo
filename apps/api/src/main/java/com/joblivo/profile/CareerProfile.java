package com.joblivo.profile;

import com.joblivo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a user's Master Career Profile.
 * Maps to the PostgreSQL 'career_profiles' table.
 * Establishes a strict 1-to-1 relationship with a {@link User}.
 */
@Entity
@Table(name = "career_profiles")
public class CareerProfile {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "professional_headline", length = 200)
    private String professionalHeadline;

    @Column(name = "current_title", length = 100)
    private String currentTitle;

    @Column(name = "current_company", length = 100)
    private String currentCompany;

    @Column(name = "total_experience_months")
    private Integer totalExperienceMonths;

    @Column(name = "current_location", length = 100)
    private String currentLocation;

    @Column(name = "preferred_work_location", length = 100)
    private String preferredWorkLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_work_mode", length = 20)
    private WorkMode preferredWorkMode;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected CareerProfile() {
    }

    /**
     * Creates a new master career profile for the given user.
     *
     * @param user the owning user account
     */
    public CareerProfile(User user) {
        this.user = Objects.requireNonNull(user, "User must not be null");
    }

    /**
     * Package-private constructor for test and persistence hydration.
     */
    CareerProfile(UUID id, User user, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = Objects.requireNonNull(user, "User must not be null");
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = Objects.requireNonNull(user, "User must not be null");
    }

    public String getProfessionalHeadline() {
        return professionalHeadline;
    }

    public void setProfessionalHeadline(String professionalHeadline) {
        this.professionalHeadline = professionalHeadline;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public void setCurrentTitle(String currentTitle) {
        this.currentTitle = currentTitle;
    }

    public String getCurrentCompany() {
        return currentCompany;
    }

    public void setCurrentCompany(String currentCompany) {
        this.currentCompany = currentCompany;
    }

    public Integer getTotalExperienceMonths() {
        return totalExperienceMonths;
    }

    public void setTotalExperienceMonths(Integer totalExperienceMonths) {
        this.totalExperienceMonths = totalExperienceMonths;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public String getPreferredWorkLocation() {
        return preferredWorkLocation;
    }

    public void setPreferredWorkLocation(String preferredWorkLocation) {
        this.preferredWorkLocation = preferredWorkLocation;
    }

    public WorkMode getPreferredWorkMode() {
        return preferredWorkMode;
    }

    public void setPreferredWorkMode(WorkMode preferredWorkMode) {
        this.preferredWorkMode = preferredWorkMode;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer noticePeriodDays) {
        this.noticePeriodDays = noticePeriodDays;
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
        CareerProfile that = (CareerProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CareerProfile{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

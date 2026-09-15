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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a factual skill or technology competency belonging to a Master Career Profile.
 * Maps to the PostgreSQL 'career_profile_skills' table.
 */
@Entity
@Table(name = "career_profile_skills")
public class CareerProfileSkill {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_profile_id", nullable = false)
    private CareerProfile careerProfile;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private SkillCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency", nullable = false, length = 30)
    private SkillProficiency proficiency;

    @Column(name = "years_of_experience", precision = 4, scale = 1)
    private BigDecimal yearsOfExperience;

    @Column(name = "last_used_date")
    private LocalDate lastUsedDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected CareerProfileSkill() {
    }

    /**
     * Creates a new skill entry for a career profile.
     */
    public CareerProfileSkill(
            CareerProfile careerProfile,
            String name,
            SkillCategory category,
            SkillProficiency proficiency,
            BigDecimal yearsOfExperience,
            LocalDate lastUsedDate,
            int displayOrder
    ) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "CareerProfile must not be null");
        this.name = Objects.requireNonNull(name, "Skill name must not be null").trim();
        this.category = Objects.requireNonNull(category, "Category must not be null");
        this.proficiency = Objects.requireNonNull(proficiency, "Proficiency must not be null");
        this.yearsOfExperience = yearsOfExperience;
        this.lastUsedDate = lastUsedDate;
        this.displayOrder = displayOrder;
    }

    /**
     * Package-private constructor for testing and hydration.
     */
    CareerProfileSkill(
            UUID id,
            CareerProfile careerProfile,
            String name,
            SkillCategory category,
            SkillProficiency proficiency,
            BigDecimal yearsOfExperience,
            LocalDate lastUsedDate,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(careerProfile, name, category, proficiency, yearsOfExperience, lastUsedDate, displayOrder);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Skill name must not be null").trim();
    }

    public SkillCategory getCategory() {
        return category;
    }

    public void setCategory(SkillCategory category) {
        this.category = Objects.requireNonNull(category, "Category must not be null");
    }

    public SkillProficiency getProficiency() {
        return proficiency;
    }

    public void setProficiency(SkillProficiency proficiency) {
        this.proficiency = Objects.requireNonNull(proficiency, "Proficiency must not be null");
    }

    public BigDecimal getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(BigDecimal yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public LocalDate getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(LocalDate lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
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
        CareerProfileSkill that = (CareerProfileSkill) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CareerProfileSkill{" +
                "id=" + id +
                ", careerProfileId=" + (careerProfile != null ? careerProfile.getId() : null) +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", proficiency=" + proficiency +
                ", yearsOfExperience=" + yearsOfExperience +
                ", lastUsedDate=" + lastUsedDate +
                ", displayOrder=" + displayOrder +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

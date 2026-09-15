package com.joblivo.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA entity representing a factual professional certification record
 * belonging to a user's Master Career Profile.
 */
@Entity
@Table(name = "career_profile_certifications")
public class CareerProfileCertification {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_profile_id", nullable = false)
    private CareerProfile careerProfile;

    @Column(name = "certification_name", nullable = false, length = 150)
    private String certificationName;

    @Column(name = "issuing_organization", nullable = false, length = 150)
    private String issuingOrganization;

    @Column(name = "credential_id", length = 100)
    private String credentialId;

    @Column(name = "credential_url", length = 500)
    private String credentialUrl;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "does_not_expire", nullable = false)
    private boolean doesNotExpire = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerProfileCertification() {
        // Required by JPA
    }

    public CareerProfileCertification(
            CareerProfile careerProfile,
            String certificationName,
            String issuingOrganization,
            String credentialId,
            String credentialUrl,
            LocalDate issueDate,
            LocalDate expirationDate,
            boolean doesNotExpire,
            String description,
            int displayOrder) {
        this.careerProfile = Objects.requireNonNull(careerProfile, "careerProfile must not be null");
        setCertificationName(certificationName);
        setIssuingOrganization(issuingOrganization);
        setCredentialId(credentialId);
        setCredentialUrl(credentialUrl);
        this.issueDate = issueDate;
        this.expirationDate = expirationDate;
        this.doesNotExpire = doesNotExpire;
        setDescription(description);
        setDisplayOrder(displayOrder);
    }

    public CareerProfileCertification(
            UUID id,
            CareerProfile careerProfile,
            String certificationName,
            String issuingOrganization,
            String credentialId,
            String credentialUrl,
            LocalDate issueDate,
            LocalDate expirationDate,
            boolean doesNotExpire,
            String description,
            int displayOrder,
            Instant createdAt,
            Instant updatedAt) {
        this(careerProfile, certificationName, issuingOrganization, credentialId, credentialUrl, issueDate, expirationDate, doesNotExpire, description, displayOrder);
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
        this.careerProfile = Objects.requireNonNull(careerProfile, "careerProfile must not be null");
    }

    public String getCertificationName() {
        return certificationName;
    }

    public void setCertificationName(String certificationName) {
        if (certificationName == null || certificationName.isBlank()) {
            throw new IllegalArgumentException("Certification name must not be blank");
        }
        this.certificationName = certificationName.trim();
    }

    public String getIssuingOrganization() {
        return issuingOrganization;
    }

    public void setIssuingOrganization(String issuingOrganization) {
        if (issuingOrganization == null || issuingOrganization.isBlank()) {
            throw new IllegalArgumentException("Issuing organization must not be blank");
        }
        this.issuingOrganization = issuingOrganization.trim();
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = (credentialId != null && !credentialId.isBlank()) ? credentialId.trim() : null;
    }

    public String getCredentialUrl() {
        return credentialUrl;
    }

    public void setCredentialUrl(String credentialUrl) {
        this.credentialUrl = (credentialUrl != null && !credentialUrl.isBlank()) ? credentialUrl.trim() : null;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public boolean isDoesNotExpire() {
        return doesNotExpire;
    }

    public boolean getDoesNotExpire() {
        return doesNotExpire;
    }

    public void setDoesNotExpire(boolean doesNotExpire) {
        this.doesNotExpire = doesNotExpire;
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
        if (!(o instanceof CareerProfileCertification that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

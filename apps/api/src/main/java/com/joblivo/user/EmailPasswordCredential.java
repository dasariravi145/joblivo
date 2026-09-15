package com.joblivo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA entity representing email/password authentication credentials.
 * Maps to the PostgreSQL 'email_password_credentials' table.
 * Strictly isolates password hashes with a 1-to-1 relationship to an EMAIL UserAuthIdentity.
 */
@Entity
@Table(name = "email_password_credentials")
public class EmailPasswordCredential {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_identity_id", nullable = false, unique = true)
    private UserAuthIdentity authIdentity;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected EmailPasswordCredential() {
    }

    /**
     * Creates an email password credential record with the encoded password hash.
     *
     * @param authIdentity the associated authentication identity
     * @param passwordHash the encoded password hash (never raw plaintext)
     */
    public EmailPasswordCredential(UserAuthIdentity authIdentity, String passwordHash) {
        this.authIdentity = Objects.requireNonNull(authIdentity, "Authentication identity must not be null");
        if (authIdentity.getProvider() != AuthProvider.EMAIL) {
            throw new IllegalArgumentException("EmailPasswordCredential can only be associated with an EMAIL provider identity");
        }
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash must not be null");
    }

    /**
     * Package-private constructor for test and persistence hydration.
     */
    EmailPasswordCredential(UUID id, UserAuthIdentity authIdentity, String passwordHash, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.authIdentity = Objects.requireNonNull(authIdentity, "Authentication identity must not be null");
        if (authIdentity.getProvider() != AuthProvider.EMAIL) {
            throw new IllegalArgumentException("EmailPasswordCredential can only be associated with an EMAIL provider identity");
        }
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash must not be null");
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

    public UserAuthIdentity getAuthIdentity() {
        return authIdentity;
    }

    public void setAuthIdentity(UserAuthIdentity authIdentity) {
        this.authIdentity = Objects.requireNonNull(authIdentity, "Authentication identity must not be null");
        if (authIdentity.getProvider() != AuthProvider.EMAIL) {
            throw new IllegalArgumentException("EmailPasswordCredential can only be associated with an EMAIL provider identity");
        }
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash must not be null");
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
        EmailPasswordCredential that = (EmailPasswordCredential) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * Sanitized string representation explicitly omitting the password hash to prevent
     * inadvertent leakage in log files or diagnostic dumps.
     */
    @Override
    public String toString() {
        return "EmailPasswordCredential{" +
                "id=" + id +
                ", authIdentityId=" + (authIdentity != null ? authIdentity.getId() : null) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

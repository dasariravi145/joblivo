package com.joblivo.user;

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
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing an authentication identity associated with a Joblivo user.
 * Maps to the PostgreSQL 'user_auth_identities' table.
 */
@Entity
@Table(name = "user_auth_identities")
public class UserAuthIdentity {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Protected no-arg constructor required by JPA.
     */
    protected UserAuthIdentity() {
    }

    /**
     * Creates a new authentication identity for a user.
     *
     * @param user            the associated user account
     * @param provider        the authentication identity provider
     * @param providerSubject the unique subject identifier supplied by the provider
     */
    public UserAuthIdentity(User user, AuthProvider provider, String providerSubject) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.provider = Objects.requireNonNull(provider, "AuthProvider must not be null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "Provider subject must not be null");
    }

    /**
     * Package-private constructor for test and persistence hydration.
     */
    UserAuthIdentity(UUID id, User user, AuthProvider provider, String providerSubject, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
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

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = Objects.requireNonNull(provider, "AuthProvider must not be null");
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = Objects.requireNonNull(providerSubject, "Provider subject must not be null");
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
        UserAuthIdentity that = (UserAuthIdentity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

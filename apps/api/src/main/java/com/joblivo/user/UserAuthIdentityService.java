package com.joblivo.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service responsible for authentication identity management, retrieval,
 * provider isolation, and user ownership verification.
 * Encapsulates provider-specific subject normalization and ensures identity operations
 * never cross provider boundaries or leak identities across different users or methods.
 */
@Service
@Transactional(readOnly = true)
public class UserAuthIdentityService {

    private final UserAuthIdentityRepository userAuthIdentityRepository;

    public UserAuthIdentityService(UserAuthIdentityRepository userAuthIdentityRepository) {
        this.userAuthIdentityRepository = Objects.requireNonNull(
                userAuthIdentityRepository,
                "userAuthIdentityRepository must not be null"
        );
    }

    /**
     * Creates and persists an authentication identity for an existing user.
     * Enforces provider requirement, subject normalization, and conflict safety.
     *
     * @param user            the persisted user entity (must have non-null ID)
     * @param provider        the authentication provider
     * @param providerSubject the provider-specific subject identifier
     * @return the saved and flushed UserAuthIdentity
     * @throws IllegalArgumentException        if user, user ID, provider, or providerSubject is invalid
     * @throws DuplicateAuthIdentityException  if an identity for (provider, normalized subject) already exists
     */
    @Transactional
    public UserAuthIdentity createIdentity(User user, AuthProvider provider, String providerSubject) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user with non-null ID is required to create an authentication identity");
        }
        if (provider == null) {
            throw new IllegalArgumentException("AuthProvider is required to create an authentication identity");
        }
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("Provider subject is required to create an authentication identity");
        }

        String normalizedSubject = normalizeSubject(provider, providerSubject);

        if (userAuthIdentityRepository.existsByProviderAndProviderSubject(provider, normalizedSubject)) {
            throw new DuplicateAuthIdentityException(
                    "An authentication identity already exists for provider '" + provider + "' and subject: " + normalizedSubject
            );
        }

        UserAuthIdentity authIdentity = new UserAuthIdentity(user, provider, normalizedSubject);
        try {
            return userAuthIdentityRepository.saveAndFlush(authIdentity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAuthIdentityException(
                    "An authentication identity already exists for provider '" + provider + "' and subject: " + normalizedSubject,
                    ex
            );
        }
    }

    /**
     * Finds an authentication identity for a given provider and provider subject.
     * Applies canonical subject normalization (e.g. case-folding for email addresses)
     * and guarantees strict provider isolation.
     *
     * @param provider        the authentication provider (e.g. EMAIL, GOOGLE, PHONE)
     * @param providerSubject the provider-specific subject identifier
     * @return Optional containing the identity if found and provider matches, otherwise empty
     */
    public Optional<UserAuthIdentity> findIdentity(AuthProvider provider, String providerSubject) {
        if (provider == null || providerSubject == null || providerSubject.isBlank()) {
            return Optional.empty();
        }

        String normalizedSubject = normalizeSubject(provider, providerSubject);

        return userAuthIdentityRepository
                .findByProviderAndProviderSubject(provider, normalizedSubject)
                .filter(identity -> identity.getProvider() == provider);
    }

    /**
     * Convenience method to find an EMAIL authentication identity using canonical email normalization.
     *
     * @param email the email address
     * @return Optional containing the EMAIL identity if found, otherwise empty
     */
    public Optional<UserAuthIdentity> findEmailIdentity(String email) {
        return findIdentity(AuthProvider.EMAIL, email);
    }

    /**
     * Finds an authentication identity for a specific user ID and provider, verifying ownership.
     *
     * @param userId   the user UUID
     * @param provider the authentication provider
     * @return Optional containing the identity if found and belonging to the user
     */
    public Optional<UserAuthIdentity> findIdentityForUser(UUID userId, AuthProvider provider) {
        if (userId == null || provider == null) {
            return Optional.empty();
        }

        return userAuthIdentityRepository
                .findByUserIdAndProvider(userId, provider)
                .filter(identity -> identity.getUser() != null && userId.equals(identity.getUser().getId()))
                .filter(identity -> identity.getProvider() == provider);
    }

    /**
     * Finds an authentication identity by provider and subject, strictly verifying user ownership.
     * Guarantees that an identity belonging to User A is never returned if requested for User B.
     *
     * @param userId          the expected owning user UUID
     * @param provider        the authentication provider
     * @param providerSubject the provider-specific subject identifier
     * @return Optional containing the identity only if found and strictly owned by the specified user
     */
    public Optional<UserAuthIdentity> findIdentityForUser(UUID userId, AuthProvider provider, String providerSubject) {
        if (userId == null || provider == null || providerSubject == null || providerSubject.isBlank()) {
            return Optional.empty();
        }

        return findIdentity(provider, providerSubject)
                .filter(identity -> identity.getUser() != null && userId.equals(identity.getUser().getId()));
    }

    /**
     * Determines whether a specific user already has an authentication identity registered for a given provider.
     *
     * @param userId   the user UUID
     * @param provider the authentication provider
     * @return true if the user has an identity for the provider, false otherwise
     */
    public boolean hasIdentityForProvider(UUID userId, AuthProvider provider) {
        if (userId == null || provider == null) {
            return false;
        }

        return userAuthIdentityRepository.existsByUserIdAndProvider(userId, provider);
    }

    /**
     * Checks if an authentication identity exists for the specified provider and provider subject.
     *
     * @param provider        the authentication provider
     * @param providerSubject the provider-specific subject identifier
     * @return true if an identity exists for this exact provider and normalized subject
     */
    public boolean existsByProviderAndSubject(AuthProvider provider, String providerSubject) {
        if (provider == null || providerSubject == null || providerSubject.isBlank()) {
            return false;
        }

        String normalizedSubject = normalizeSubject(provider, providerSubject);
        return userAuthIdentityRepository.existsByProviderAndProviderSubject(provider, normalizedSubject);
    }

    /**
     * Checks if an EMAIL authentication identity exists for the given email address.
     *
     * @param email the email address
     * @return true if an EMAIL identity exists for the normalized email
     */
    public boolean existsByEmail(String email) {
        return existsByProviderAndSubject(AuthProvider.EMAIL, email);
    }

    /**
     * Normalizes the provider subject based on provider rules.
     * For EMAIL, trims and lowercases according to canonical project standards.
     * For other providers, trims leading and trailing whitespace.
     */
    private String normalizeSubject(AuthProvider provider, String subject) {
        String trimmed = subject.trim();
        if (provider == AuthProvider.EMAIL) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }
}

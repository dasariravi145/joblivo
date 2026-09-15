package com.joblivo.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service responsible for the secure creation and validation of email/password credentials.
 * Operates strictly with unhampered case-sensitive passwords at the encoding boundary,
 * ensuring raw passwords and hashes are never exposed, logged, or unnecessarily returned.
 */
@Service
public class EmailPasswordCredentialService {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private final EmailPasswordCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    public EmailPasswordCredentialService(
            EmailPasswordCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "Credential repository must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder must not be null");
    }

    /**
     * Securely creates and persists an email/password credential for an EMAIL authentication identity.
     * Enforces that the identity is persisted, attached to an existing user, belongs to the EMAIL provider,
     * and does not already possess a credential record.
     *
     * @param authIdentity the EMAIL authentication identity
     * @param rawPassword  the raw, case-sensitive password provided at the registration boundary
     * @return safe domain result containing identifiers and timestamps without exposing credentials
     * @throws IllegalArgumentException     if authIdentity is null, unpersisted, has no user, or is not EMAIL
     * @throws DuplicateCredentialException if a credential record already exists for the identity
     * @throws InvalidPasswordException     if the password fails length or content validation
     */
    @Transactional
    public EmailPasswordCredentialResult createCredential(UserAuthIdentity authIdentity, String rawPassword) {
        Objects.requireNonNull(authIdentity, "Authentication identity must not be null");

        if (authIdentity.getId() == null) {
            throw new IllegalArgumentException("Authentication identity must have a persisted ID to attach credentials");
        }

        if (authIdentity.getUser() == null || authIdentity.getUser().getId() == null) {
            throw new IllegalArgumentException("Authentication identity must belong to a valid existing user");
        }

        if (authIdentity.getProvider() != AuthProvider.EMAIL) {
            throw new IllegalArgumentException(
                    "Email password credentials can only be created for EMAIL provider, received: " + authIdentity.getProvider()
            );
        }

        validatePassword(rawPassword);

        if (credentialRepository.existsByAuthIdentityId(authIdentity.getId())) {
            throw new DuplicateCredentialException(
                    "An email password credential already exists for authentication identity: " + authIdentity.getId()
            );
        }

        // Raw password is immediately hashed using the production PasswordEncoder bean; raw string is never stored.
        String passwordHash = passwordEncoder.encode(rawPassword);

        EmailPasswordCredential credential = new EmailPasswordCredential(authIdentity, passwordHash);
        try {
            EmailPasswordCredential saved = credentialRepository.saveAndFlush(credential);

            return new EmailPasswordCredentialResult(
                    saved.getId(),
                    authIdentity.getId(),
                    saved.getCreatedAt(),
                    saved.getUpdatedAt()
            );
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCredentialException(
                    "An email password credential already exists for authentication identity: " + authIdentity.getId(),
                    ex
            );
        }
    }

    /**
     * Validates raw password rules without modifying whitespace or casing.
     *
     * @param rawPassword the password to validate
     */
    public void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidPasswordException("Password must not be null or blank");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidPasswordException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long"
            );
        }
        if (rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidPasswordException(
                    "Password must not exceed " + MAX_PASSWORD_LENGTH + " characters"
            );
        }
    }
}

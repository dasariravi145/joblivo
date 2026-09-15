package com.joblivo.auth;

import com.joblivo.user.AuthProvider;
import com.joblivo.user.DuplicateAuthIdentityException;
import com.joblivo.user.DuplicateEmailException;
import com.joblivo.user.EmailPasswordCredentialService;
import com.joblivo.user.User;
import com.joblivo.user.UserAuthIdentity;
import com.joblivo.user.UserAuthIdentityService;
import com.joblivo.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/**
 * Application service orchestrating the secure registration flow:
 * User account creation -> EMAIL UserAuthIdentity creation -> EmailPasswordCredential creation.
 * Executes within a strict transactional boundary with atomic rollback on any failure.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final UserAuthIdentityService userAuthIdentityService;
    private final EmailPasswordCredentialService emailPasswordCredentialService;

    public RegistrationService(
            UserRepository userRepository,
            UserAuthIdentityService userAuthIdentityService,
            EmailPasswordCredentialService emailPasswordCredentialService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null");
        this.userAuthIdentityService = Objects.requireNonNull(userAuthIdentityService, "UserAuthIdentityService must not be null");
        this.emailPasswordCredentialService = Objects.requireNonNull(emailPasswordCredentialService, "EmailPasswordCredentialService must not be null");
    }

    /**
     * Registers a new account with email/password authentication.
     *
     * @param request the registration request payload
     * @return safe RegisterResponse with account information
     * @throws IllegalArgumentException  if required parameters are null or blank
     * @throws PasswordMismatchException if password and confirmPassword do not match
     * @throws DuplicateEmailException   if the email or auth identity already exists
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RegisterRequest must not be null");
        }

        String rawEmail = request.email();
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        String rawDisplayName = request.displayName();
        if (rawDisplayName == null || rawDisplayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }

        String rawPassword = request.password();
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        String confirmPassword = request.confirmPassword();
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("Password confirmation is required");
        }

        if (!Objects.equals(rawPassword, confirmPassword)) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        // Pre-validate password rules before touching database
        emailPasswordCredentialService.validatePassword(rawPassword);

        // Normalize email and display name using project standard strategy
        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = rawDisplayName.trim();

        // Application-level duplicate check for core user or identity
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail) ||
                userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, normalizedEmail)) {
            throw new DuplicateEmailException("An account with this email already exists: " + normalizedEmail);
        }

        // 1. Create core User with default ACTIVE status
        User user = new User(normalizedEmail, normalizedDisplayName);

        try {
            User savedUser = userRepository.saveAndFlush(user);

            // 2. Create EMAIL UserAuthIdentity via UserAuthIdentityService with normalized email as provider subject
            UserAuthIdentity savedAuthIdentity = userAuthIdentityService.createIdentity(
                    savedUser,
                    AuthProvider.EMAIL,
                    normalizedEmail
            );

            // 3. Create EmailPasswordCredential (delegating hashing to credential service)
            // Raw password is passed exactly as supplied (never trimmed or altered)
            emailPasswordCredentialService.createCredential(savedAuthIdentity, rawPassword);

            return RegisterResponse.fromEntity(savedUser);
        } catch (DuplicateAuthIdentityException | DuplicateEmailException | DataIntegrityViolationException ex) {
            // Translate database uniqueness violations to clean 409 Conflict without leaking internals
            throw new DuplicateEmailException("An account with this email already exists: " + normalizedEmail, ex);
        }
    }
}

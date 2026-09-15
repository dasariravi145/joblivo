package com.joblivo.auth;

import com.joblivo.user.AuthProvider;
import com.joblivo.user.EmailPasswordCredential;
import com.joblivo.user.EmailPasswordCredentialRepository;
import com.joblivo.user.User;
import com.joblivo.user.UserAuthIdentity;
import com.joblivo.user.UserAuthIdentityService;
import com.joblivo.user.UserAuthenticationEligibilityService;
import com.joblivo.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/**
 * Service orchestrating foundational email/password authentication.
 * Safely verifies credentials against User, UserAuthIdentity, and EmailPasswordCredential
 * while coordinating centralized account eligibility and preventing account enumeration.
 */
@Service
public class AuthenticationService {

    private static final String GENERIC_AUTH_ERROR = "Invalid email or password";

    private final UserAuthIdentityService authIdentityService;
    private final EmailPasswordCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthenticationEligibilityService eligibilityService;

    public AuthenticationService(
            UserAuthIdentityService authIdentityService,
            EmailPasswordCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder,
            UserAuthenticationEligibilityService eligibilityService) {
        this.authIdentityService = Objects.requireNonNull(authIdentityService, "authIdentityService must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.eligibilityService = Objects.requireNonNull(eligibilityService, "eligibilityService must not be null");
    }

    /**
     * Authenticates an email/password login request.
     *
     * @param request the validated login request
     * @return successful LoginResponse
     * @throws InvalidCredentialsException if authentication fails for any reason
     */
    @Transactional(readOnly = true)
    public LoginResponse authenticate(LoginRequest request) {
        if (request == null || request.email() == null || request.password() == null) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        UserAuthIdentity identity = authIdentityService
                .findIdentity(AuthProvider.EMAIL, normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_AUTH_ERROR));

        if (identity.getProvider() != AuthProvider.EMAIL) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        User user = identity.getUser();
        if (!eligibilityService.isEligibleForAuthentication(user)) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        EmailPasswordCredential credential = credentialRepository
                .findByAuthIdentityId(identity.getId())
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_AUTH_ERROR));

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException(GENERIC_AUTH_ERROR);
        }

        return LoginResponse.of(user, identity.getProvider());
    }
}

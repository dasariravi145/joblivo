package com.joblivo.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Service orchestrating user creation and domain boundary enforcement.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a new user with normalized email, trimmed display name, and ACTIVE status.
     *
     * @param request payload containing email and displayName
     * @return UserResponse containing persisted user information
     * @throws DuplicateEmailException if a user with the same email already exists
     * @throws IllegalArgumentException if the request or required fields are missing/blank
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateUserRequest must not be null");
        }

        String rawEmail = request.email();
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        String rawDisplayName = request.displayName();
        if (rawDisplayName == null || rawDisplayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }

        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = rawDisplayName.trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException("A user with this email already exists: " + normalizedEmail);
        }

        User user = new User(normalizedEmail, normalizedDisplayName);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return UserResponse.fromEntity(savedUser);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException("A user with this email already exists: " + normalizedEmail, ex);
        }
    }
}

package com.joblivo.auth;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/**
 * REST controller providing public authentication registration endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService) {
        this.registrationService = Objects.requireNonNull(registrationService, "RegistrationService must not be null");
        this.authenticationService = Objects.requireNonNull(authenticationService, "AuthenticationService must not be null");
    }

    /**
     * Registers a new account with email/password authentication.
     *
     * @param request validated caller registration payload
     * @return 201 Created with Location header and safe RegisterResponse
     */
    @PostMapping(
        value = "/register",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registrationService.register(request);
        URI location = URI.create("/api/v1/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Authenticates an existing user via email/password.
     *
     * @param request validated login request
     * @return 200 OK with safe LoginResponse
     */
    @PostMapping(
        value = "/login",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }
}

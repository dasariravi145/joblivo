package com.joblivo.error;

import com.joblivo.auth.InvalidCredentialsException;
import com.joblivo.user.DuplicateAuthIdentityException;
import com.joblivo.user.DuplicateEmailException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/auth/test");
    }

    @Test
    @DisplayName("Translates MethodArgumentNotValidException to 400 with field errors")
    void handleValidationException_Returns400WithFieldErrors() throws NoSuchMethodException {
        TestPayload target = new TestPayload("", "");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "testPayload");
        bindingResult.addError(new FieldError("testPayload", "email", "Email is required"));
        bindingResult.addError(new FieldError("testPayload", "password", "Password must be between 8 and 128 characters"));

        MethodParameter parameter = new MethodParameter(
                TestPayload.class.getMethod("dummyMethod", String.class), 0
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Bad Request", response.getBody().error());
        assertEquals("Validation failed for request payload", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
        assertNotNull(response.getBody().validationErrors());
        assertEquals("Email is required", response.getBody().validationErrors().get("email"));
        assertEquals("Password must be between 8 and 128 characters", response.getBody().validationErrors().get("password"));
    }

    @Test
    @DisplayName("Translates HttpMessageNotReadableException to 400 with generic message")
    void handleMessageNotReadableException_Returns400WithGenericMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error: Unexpected character");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMessageNotReadableException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Bad Request", response.getBody().error());
        assertEquals("Malformed JSON request body", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
        assertNull(response.getBody().validationErrors());
    }

    @Test
    @DisplayName("Translates IllegalArgumentException with custom message")
    void handleIllegalArgumentException_WithMessage_Returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Passwords do not match");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Passwords do not match", response.getBody().message());
    }

    @Test
    @DisplayName("Translates IllegalArgumentException with null message to safe fallback")
    void handleIllegalArgumentException_WithNullMessage_Returns400WithFallback() {
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Invalid request payload or argument", response.getBody().message());
    }

    @Test
    @DisplayName("Translates DuplicateEmailException to 409 Conflict")
    void handleDuplicateResourceException_Returns409Conflict() {
        DuplicateEmailException ex = new DuplicateEmailException("An account with this email already exists: test@joblivo.com");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("An account with this email already exists: test@joblivo.com", response.getBody().message());
    }

    @Test
    @DisplayName("Translates DuplicateAuthIdentityException to 409 Conflict")
    void handleDuplicateAuthIdentityException_Returns409Conflict() {
        DuplicateAuthIdentityException ex = new DuplicateAuthIdentityException("An authentication identity already exists for provider 'EMAIL' and subject: test@joblivo.com");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("An authentication identity already exists for provider 'EMAIL' and subject: test@joblivo.com", response.getBody().message());
    }

    @Test
    @DisplayName("Translates DataIntegrityViolationException to 409 without leaking DB internals")
    void handleDataIntegrityViolationException_Returns409WithoutLeakingInternals() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uq_users_email_lower\" Key (lower(email))=(test@joblivo.com) already exists."
        );

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolationException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("A database constraint was violated. The resource may already exist.", response.getBody().message());
        assertFalse(response.getBody().message().contains("uq_users_email_lower"));
        assertFalse(response.getBody().message().contains("Key (lower(email))"));
    }

    @Test
    @DisplayName("Translates InvalidCredentialsException to generic 401 anti-enumeration response")
    void handleInvalidCredentialsException_ReturnsGeneric401() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Internal: User account suspended");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleInvalidCredentialsException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().status());
        assertEquals("Unauthorized", response.getBody().error());
        assertEquals("Invalid email or password", response.getBody().message());
        assertFalse(response.getBody().message().contains("suspended"));
    }

    @Test
    @DisplayName("Translates unexpected Exception to 500 without leaking stack trace")
    void handleUnexpectedException_Returns500WithoutLeakingDetails() {
        Exception ex = new RuntimeException("PostgreSQL connection timeout / password=[SECRET]");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleUnexpectedException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("Internal Server Error", response.getBody().error());
        assertEquals("An unexpected internal error occurred. Please contact support if the issue persists.", response.getBody().message());
        assertFalse(response.getBody().message().contains("SECRET"));
        assertFalse(response.getBody().message().contains("PostgreSQL"));
    }

    @Test
    @DisplayName("Translates WorkExperienceNotFoundException to 404 Not Found")
    void handleWorkExperienceNotFoundException_Returns404NotFound() {
        com.joblivo.profile.WorkExperienceNotFoundException ex =
                new com.joblivo.profile.WorkExperienceNotFoundException("Work experience not found with id: 12345");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Work experience not found with id: 12345", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    @Test
    @DisplayName("Translates SkillNotFoundException to 404 Not Found")
    void handleSkillNotFoundException_Returns404NotFound() {
        com.joblivo.profile.SkillNotFoundException ex =
                new com.joblivo.profile.SkillNotFoundException("Skill not found with id: 67890");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Skill not found with id: 67890", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    @Test
    @DisplayName("Translates DuplicateSkillException to 409 Conflict")
    void handleDuplicateSkillException_Returns409Conflict() {
        com.joblivo.profile.DuplicateSkillException ex =
                new com.joblivo.profile.DuplicateSkillException("Skill 'AWS' already exists in this career profile");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("Skill 'AWS' already exists in this career profile", response.getBody().message());
    }

    @Test
    @DisplayName("Translates ProjectNotFoundException to 404 Not Found")
    void handleProjectNotFoundException_Returns404NotFound() {
        com.joblivo.profile.ProjectNotFoundException ex =
                new com.joblivo.profile.ProjectNotFoundException("Project not found with id: proj-12345");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Project not found with id: proj-12345", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    @Test
    @DisplayName("Translates DuplicateProjectException to 409 Conflict")
    void handleDuplicateProjectException_Returns409Conflict() {
        com.joblivo.profile.DuplicateProjectException ex =
                new com.joblivo.profile.DuplicateProjectException("Project 'Joblivo AI' already exists in this career profile");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("Project 'Joblivo AI' already exists in this career profile", response.getBody().message());
    }

    @Test
    @DisplayName("Translates EducationNotFoundException to 404 Not Found")
    void handleEducationNotFoundException_Returns404NotFound() {
        com.joblivo.profile.EducationNotFoundException ex =
                new com.joblivo.profile.EducationNotFoundException("Education record not found with id: edu-12345");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Education record not found with id: edu-12345", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    @Test
    @DisplayName("Translates DuplicateEducationException to 409 Conflict")
    void handleDuplicateEducationException_Returns409Conflict() {
        com.joblivo.profile.DuplicateEducationException ex =
                new com.joblivo.profile.DuplicateEducationException("An education record with the same institution, degree, and field of study already exists in this career profile");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("An education record with the same institution, degree, and field of study already exists in this career profile", response.getBody().message());
    }

    @Test
    @DisplayName("Translates CertificationNotFoundException to 404 Not Found")
    void handleCertificationNotFoundException_Returns404NotFound() {
        com.joblivo.profile.CertificationNotFoundException ex =
                new com.joblivo.profile.CertificationNotFoundException("Certification not found with id: cert-12345");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Certification not found with id: cert-12345", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    @Test
    @DisplayName("Translates DuplicateCertificationException to 409 Conflict")
    void handleDuplicateCertificationException_Returns409Conflict() {
        com.joblivo.profile.DuplicateCertificationException ex =
                new com.joblivo.profile.DuplicateCertificationException("A certification with credential ID 'AWS-123' already exists in this career profile");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("A certification with credential ID 'AWS-123' already exists in this career profile", response.getBody().message());
    }

    @Test
    @DisplayName("Translates AchievementNotFoundException to 404 Not Found")
    void handleAchievementNotFoundException_Returns404NotFound() {
        com.joblivo.profile.AchievementNotFoundException ex =
                new com.joblivo.profile.AchievementNotFoundException("Achievement not found with id: achieve-12345");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Achievement not found with id: achieve-12345", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    @Test
    @DisplayName("Translates DuplicateAchievementException to 409 Conflict")
    void handleDuplicateAchievementException_Returns409Conflict() {
        com.joblivo.profile.DuplicateAchievementException ex =
                new com.joblivo.profile.DuplicateAchievementException("An achievement with the same title, type, date, and organization already exists in this career profile");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("An achievement with the same title, type, date, and organization already exists in this career profile", response.getBody().message());
    }

    @Test
    @DisplayName("Translates CareerProfileNotFoundException to 404 Not Found")
    void handleCareerProfileNotFoundException_Returns404NotFound() {
        com.joblivo.profile.CareerProfileNotFoundException ex =
                new com.joblivo.profile.CareerProfileNotFoundException("Career profile not found for user: user-12345");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Career profile not found for user: user-12345", response.getBody().message());
        assertEquals("/api/v1/auth/test", response.getBody().path());
    }

    static class TestPayload {
        private final String email;
        private final String password;

        TestPayload(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }

        public void dummyMethod(String param) {}
    }
}

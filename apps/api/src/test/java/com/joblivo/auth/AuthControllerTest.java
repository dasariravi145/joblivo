package com.joblivo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblivo.error.GlobalExceptionHandler;
import com.joblivo.security.SecurityConfig;
import com.joblivo.user.AuthProvider;
import com.joblivo.user.DuplicateEmailException;
import com.joblivo.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    void register_WithValidPayload_Returns201AndRegisterResponseWithLocationHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        RegisterRequest request = new RegisterRequest(
                "candidate@joblivo.com",
                "Career Candidate",
                "StrongP@ssword123!",
                "StrongP@ssword123!"
        );
        RegisterResponse response = new RegisterResponse(
                userId,
                "candidate@joblivo.com",
                "Career Candidate",
                UserStatus.ACTIVE,
                now
        );

        when(registrationService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users/" + userId)))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("candidate@joblivo.com"))
                .andExpect(jsonPath("$.displayName").value("Career Candidate"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.confirmPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void register_RequestModel_ContainsOnlyExpectedRegistrationComponents() {
        assertEquals(4, RegisterRequest.class.getRecordComponents().length);
        assertEquals("email", RegisterRequest.class.getRecordComponents()[0].getName());
        assertEquals("displayName", RegisterRequest.class.getRecordComponents()[1].getName());
        assertEquals("password", RegisterRequest.class.getRecordComponents()[2].getName());
        assertEquals("confirmPassword", RegisterRequest.class.getRecordComponents()[3].getName());
    }

    @Test
    void register_WhenJsonAttemptsToInjectIdOrStatusOrProvider_CannotControlInternalFields() throws Exception {
        UUID generatedId = UUID.randomUUID();
        Instant now = Instant.now();
        RegisterResponse response = new RegisterResponse(
                generatedId,
                "secure@joblivo.com",
                "Secure User",
                UserStatus.ACTIVE,
                now
        );

        when(registrationService.register(any(RegisterRequest.class))).thenReturn(response);

        String tamperingPayload = """
                {
                    "email": "secure@joblivo.com",
                    "displayName": "Secure User",
                    "password": "SecurePassword!123",
                    "confirmPassword": "SecurePassword!123",
                    "id": "00000000-0000-0000-0000-000000000000",
                    "status": "SUSPENDED",
                    "provider": "GOOGLE",
                    "providerSubject": "injected-subject"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(generatedId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void register_WhenEmailIsInvalid_Returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "not-an-email",
                "Jane Doe",
                "ValidP@ss123",
                "ValidP@ss123"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_WhenEmailIsMissingOrBlank_Returns400BadRequest() throws Exception {
        String missingEmailPayload = """
                {
                    "displayName": "Jane Doe",
                    "password": "ValidP@ss123",
                    "confirmPassword": "ValidP@ss123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingEmailPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_WhenDisplayNameIsMissingOrBlank_Returns400BadRequest() throws Exception {
        String blankDisplayNamePayload = """
                {
                    "email": "user@joblivo.com",
                    "displayName": "   ",
                    "password": "ValidP@ss123",
                    "confirmPassword": "ValidP@ss123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankDisplayNamePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.displayName").isNotEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_WhenPasswordIsTooShort_Returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "user@joblivo.com",
                "Jane Doe",
                "short",
                "short"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_WhenPasswordsMismatch_Returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "mismatch@joblivo.com",
                "Jane Doe",
                "ValidP@ssword123!",
                "DifferentP@ssword123!"
        );

        when(registrationService.register(any(RegisterRequest.class)))
                .thenThrow(new PasswordMismatchException("Passwords do not match"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    void register_WhenEmailAlreadyExists_Returns409Conflict() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "duplicate@joblivo.com",
                "Duplicate User",
                "ValidP@ssword123!",
                "ValidP@ssword123!"
        );

        when(registrationService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("An account with this email already exists: duplicate@joblivo.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("duplicate@joblivo.com")));
    }

    @Test
    void register_WhenUnexpectedExceptionOccurs_Returns500WithoutLeakingDetails() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "crash@joblivo.com",
                "Crash User",
                "ValidP@ssword123!",
                "ValidP@ssword123!"
        );

        when(registrationService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("database timeout / internal connection terminated"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred. Please contact support if the issue persists."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void register_WhenMalformedJson_Returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-content"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void login_WithValidCredentials_Returns200AndLoginResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginRequest request = new LoginRequest("ada@example.com", "Secret123!");
        LoginResponse response = new LoginResponse(
                userId,
                "ada@example.com",
                "Ada Lovelace",
                AuthProvider.EMAIL,
                UserStatus.ACTIVE
        );

        when(authenticationService.authenticate(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.provider").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_WithInvalidCredentials_Returns401AndApiErrorResponse() throws Exception {
        LoginRequest request = new LoginRequest("ada@example.com", "WrongPassword!");

        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void login_WithBlankEmail_Returns400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest("", "Secret123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void login_WithInvalidEmailFormat_Returns400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest("invalid-email-format", "Secret123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void login_WithBlankPassword_Returns400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest("ada@example.com", "   ");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void login_RequestModel_ContainsOnlyExpectedComponents() {
        assertEquals(2, LoginRequest.class.getRecordComponents().length);
        assertEquals("email", LoginRequest.class.getRecordComponents()[0].getName());
        assertEquals("password", LoginRequest.class.getRecordComponents()[1].getName());
    }

    @Test
    void login_ResponseModel_ContainsOnlyExpectedComponents() {
        assertEquals(5, LoginResponse.class.getRecordComponents().length);
        assertEquals("id", LoginResponse.class.getRecordComponents()[0].getName());
        assertEquals("email", LoginResponse.class.getRecordComponents()[1].getName());
        assertEquals("displayName", LoginResponse.class.getRecordComponents()[2].getName());
        assertEquals("provider", LoginResponse.class.getRecordComponents()[3].getName());
        assertEquals("status", LoginResponse.class.getRecordComponents()[4].getName());
    }

    @Test
    void login_WhenUnknownEmailOrWrongPassword_ReturnsIdenticalGeneric401Response() throws Exception {
        LoginRequest wrongPasswordRequest = new LoginRequest("existing@joblivo.com", "WrongPassword!");
        when(authenticationService.authenticate(wrongPasswordRequest))
                .thenThrow(new InvalidCredentialsException("Password mismatch"));

        LoginRequest unknownEmailRequest = new LoginRequest("unknown@joblivo.com", "AnyPassword!");
        when(authenticationService.authenticate(unknownEmailRequest))
                .thenThrow(new InvalidCredentialsException("User not found"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPasswordRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mismatch"))));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownEmailRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not found"))));
    }

    @Test
    void registerRequest_ToString_MasksPasswords() {
        RegisterRequest request = new RegisterRequest(
                "candidate@joblivo.com",
                "Candidate",
                "SuperSecretPassword123!",
                "SuperSecretPassword123!"
        );

        String asString = request.toString();

        assertFalse(asString.contains("SuperSecretPassword123!"));
        assertTrue(asString.contains("password=[PROTECTED]"));
        assertTrue(asString.contains("confirmPassword=[PROTECTED]"));
    }

    @Test
    void loginRequest_ToString_MasksPassword() {
        LoginRequest request = new LoginRequest("candidate@joblivo.com", "SuperSecretPassword123!");

        String asString = request.toString();

        assertFalse(asString.contains("SuperSecretPassword123!"));
        assertTrue(asString.contains("password=[PROTECTED]"));
    }

    @Test
    void login_WhenAccountIsSuspendedOrDeleted_ReturnsGeneric401WithoutLeakingStatus() throws Exception {
        LoginRequest suspendedRequest = new LoginRequest("suspended@joblivo.com", "Password123!");
        when(authenticationService.authenticate(suspendedRequest))
                .thenThrow(new InvalidCredentialsException());

        LoginRequest deletedRequest = new LoginRequest("deleted@joblivo.com", "Password123!");
        when(authenticationService.authenticate(deletedRequest))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suspendedRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SUSPENDED"))));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deletedRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DELETED"))));
    }
}

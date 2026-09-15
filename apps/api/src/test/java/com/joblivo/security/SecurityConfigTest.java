package com.joblivo.security;

import com.joblivo.auth.AuthController;
import com.joblivo.auth.AuthenticationService;
import com.joblivo.auth.RegistrationService;
import com.joblivo.error.GlobalExceptionHandler;
import com.joblivo.health.HealthController;
import com.joblivo.profile.CareerProfileController;
import com.joblivo.profile.CareerProfileService;
import com.joblivo.user.UserController;
import com.joblivo.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {HealthController.class, UserController.class, AuthController.class, CareerProfileController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private CareerProfileService careerProfileService;

    @Test
    void passwordEncoder_BeanExistsAndUsesBCrypt() {
        assertNotNull(passwordEncoder, "PasswordEncoder bean must be registered");
        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder, "PasswordEncoder must be BCryptPasswordEncoder");

        String rawPassword = "P@ssw0rdSecure123!";
        String encoded = passwordEncoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$"), "Must use standard BCrypt prefix");
        assertTrue(passwordEncoder.matches(rawPassword, encoded), "Encoded password must match raw password");
        assertFalse(passwordEncoder.matches("WrongPassword", encoded), "Non-matching password must be rejected");
    }

    @Test
    void healthEndpoint_IsPubliclyAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void userCreationEndpoint_IsPubliclyAccessibleAtFoundationStage() throws Exception {
        // Unauthenticated request reaches the validation layer rather than being blocked by security filter
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void authRegisterEndpoint_IsPubliclyAccessibleWithoutAuthentication() throws Exception {
        // Unauthenticated POST /api/v1/auth/register reaches validation layer rather than 401
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void authLoginEndpoint_IsPubliclyAccessibleWithoutAuthentication() throws Exception {
        // Unauthenticated POST /api/v1/auth/login reaches validation layer rather than 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void unauthenticatedGetToAuthRegisterEndpoint_Returns401Unauthorized() throws Exception {
        // GET on /api/v1/auth/register is not permitted (only POST is public)
        mockMvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void unauthenticatedRequestToArbitraryAuthEndpoint_Returns401Unauthorized() throws Exception {
        // Other arbitrary endpoints under /api/v1/auth require authentication
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void unauthenticatedRequestToProtectedMethod_Returns401Unauthorized() throws Exception {
        // GET on /api/v1/users is not public (only POST is permitted)
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void unauthenticatedRequestToArbitraryEndpoint_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/protected-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/protected-resource"));
    }

    @Test
    void protectedEndpoint_DoesNotRedirectToHtmlLoginPage() throws Exception {
        // Verifies no 302/redirect and no HTML content
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void secureHttpHeaders_ArePresentOnResponses() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void defaultUserDetailsService_IsNotRegistered() {
        // Asserts that no default in-memory user details service was auto-configured
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean(UserDetailsService.class),
                "No default UserDetailsService should be registered");
    }

    @Test
    void unauthenticatedNonPostMethodsToAuthRegister_Return401Unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/auth/register"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(delete("/api/v1/auth/register"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void unauthenticatedNonPostMethodsToAuthLogin_Return401Unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(delete("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void unauthenticatedPostToCareerProfile_Returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/v1/career-profile"));
    }

    @Test
    void unauthenticatedNonPostMethodsToCareerProfile_Return401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(put("/api/v1/career-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(delete("/api/v1/career-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}

package com.joblivo.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblivo.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import com.joblivo.security.SecurityConfig;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_WithValidPayload_Returns201AndUserResponseAndLocationHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        CreateUserRequest request = new CreateUserRequest("newuser@joblivo.com", "New User");
        UserResponse response = new UserResponse(
                userId,
                "newuser@joblivo.com",
                "New User",
                UserStatus.ACTIVE,
                now,
                now
        );

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users/" + userId)))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("newuser@joblivo.com"))
                .andExpect(jsonPath("$.displayName").value("New User"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createUser_RequestRecord_CannotAcceptIdOrStatusOrTimestamps() {
        // Assert at compile/reflection level that CreateUserRequest contains only 2 components: email and displayName
        assertEquals(2, CreateUserRequest.class.getRecordComponents().length);
        assertEquals("email", CreateUserRequest.class.getRecordComponents()[0].getName());
        assertEquals("displayName", CreateUserRequest.class.getRecordComponents()[1].getName());
    }

    @Test
    void createUser_WhenJsonContainsIdOrStatus_CannotControlIdOrStatus() throws Exception {
        UUID generatedId = UUID.randomUUID();
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                generatedId,
                "secured@joblivo.com",
                "Secured User",
                UserStatus.ACTIVE,
                now,
                now
        );

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);

        // Client attempts to inject ID and SUSPENDED status
        String tamperingPayload = """
                {
                    "email": "secured@joblivo.com",
                    "displayName": "Secured User",
                    "id": "00000000-0000-0000-0000-000000000000",
                    "status": "SUSPENDED"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(generatedId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createUser_WhenEmailIsInvalid_Returns400BadRequest() throws Exception {
        String invalidPayload = """
                {
                    "email": "invalid-email-format",
                    "displayName": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void createUser_WhenEmailIsMissingOrBlank_Returns400BadRequest() throws Exception {
        String missingEmailPayload = """
                {
                    "displayName": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingEmailPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        String blankEmailPayload = """
                {
                    "email": "   ",
                    "displayName": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankEmailPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void createUser_WhenDisplayNameIsMissingOrBlank_Returns400BadRequest() throws Exception {
        String missingDisplayNamePayload = """
                {
                    "email": "user@joblivo.com"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingDisplayNamePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.displayName").isNotEmpty());

        String blankDisplayNamePayload = """
                {
                    "email": "user@joblivo.com",
                    "displayName": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankDisplayNamePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.displayName").isNotEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void createUser_WhenFieldsExceedMaxLengths_Returns400BadRequest() throws Exception {
        String excessiveEmail = "a".repeat(250) + "@domain.com"; // > 255 chars
        String excessiveDisplayName = "b".repeat(101); // > 100 chars

        String longPayload = objectMapper.writeValueAsString(new CreateUserRequest(excessiveEmail, excessiveDisplayName));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(longPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.displayName").isNotEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void createUser_WhenEmailAlreadyExists_Returns409Conflict() throws Exception {
        CreateUserRequest request = new CreateUserRequest("duplicate@joblivo.com", "Duplicate User");

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new DuplicateEmailException("A user with this email already exists: duplicate@joblivo.com"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("duplicate@joblivo.com")))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void createUser_WhenUnexpectedExceptionOccurs_Returns500WithoutLeakingDetails() throws Exception {
        CreateUserRequest request = new CreateUserRequest("crash@joblivo.com", "Crash User");

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new RuntimeException("psql: FATAL: connection terminated with internal db state"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred. Please contact support if the issue persists."))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void createUser_WhenMalformedJson_Returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }
}

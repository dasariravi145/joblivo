package com.joblivo.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblivo.error.GlobalExceptionHandler;
import com.joblivo.security.SecurityConfig;
import com.joblivo.user.User;
import com.joblivo.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CareerProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CareerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CareerProfileService careerProfileService;

    private User createPersistedUser(UUID id, String email, String displayName, UserStatus status) {
        try {
            User user = new User(email, displayName, status);
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);

            java.lang.reflect.Field createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, Instant.now());
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createProfile_WhenAuthenticated_Returns201AndCareerProfileResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);

        when(careerProfileService.createProfileForPrincipal("user@joblivo.com")).thenReturn(profile);

        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/career-profile/" + profileId)))
                .andExpect(jsonPath("$.id").value(profileId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                // Ensure no credentials or out-of-scope fields leak
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.credentials").doesNotExist())
                .andExpect(jsonPath("$.skills").doesNotExist());
    }

    @Test
    void createProfile_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "legit@joblivo.com")
    void createProfile_WhenClientPassesTamperingPayloadWithDifferentUserId_IgnoresPayloadAndBindsToPrincipal() throws Exception {
        UUID legitimateUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User legitimateUser = createPersistedUser(legitimateUserId, "legit@joblivo.com", "Legit User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, legitimateUser, now, now);

        when(careerProfileService.createProfileForPrincipal("legit@joblivo.com")).thenReturn(profile);

        // Attacker attempts to forge ownership to another user ID
        String tamperingPayload = """
                {
                    "userId": "00000000-0000-0000-0000-000000000000",
                    "id": "11111111-1111-1111-1111-111111111111"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(profileId.toString()))
                .andExpect(jsonPath("$.userId").value(legitimateUserId.toString()));
    }

    @Test
    @WithMockUser(username = "duplicate@joblivo.com")
    void createProfile_WhenProfileAlreadyExists_Returns409Conflict() throws Exception {
        when(careerProfileService.createProfileForPrincipal("duplicate@joblivo.com"))
                .thenThrow(new DuplicateCareerProfileException("A master career profile already exists for user"));

        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")))
                .andExpect(jsonPath("$.path").value("/api/v1/career-profile"));
    }

    @Test
    @WithMockUser(username = "suspended@joblivo.com")
    void createProfile_WhenUserIsIneligible_Returns400BadRequest() throws Exception {
        when(careerProfileService.createProfileForPrincipal("suspended@joblivo.com"))
                .thenThrow(new IneligibleUserException("User with status SUSPENDED is not eligible to own a master career profile"));

        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("not eligible")));
    }

    @Test
    @WithMockUser(username = "crash@joblivo.com")
    void createProfile_WhenUnexpectedErrorOccurs_Returns500WithoutLeakingDetails() throws Exception {
        when(careerProfileService.createProfileForPrincipal(anyString()))
                .thenThrow(new RuntimeException("FATAL: relation \"career_profiles\" does not exist in schema"));

        mockMvc.perform(post("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred. Please contact support if the issue persists."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.message", not(containsString("career_profiles"))));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProfile_WhenAuthenticatedWithValidDetails_Returns200AndCareerProfileResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);
        profile.setProfessionalHeadline("Senior Cloud Architect");
        profile.setCurrentTitle("Lead Engineer");
        profile.setCurrentCompany("Global Tech");
        profile.setTotalExperienceMonths(96);
        profile.setCurrentLocation("Seattle, WA");
        profile.setPreferredWorkLocation("Remote, US");
        profile.setPreferredWorkMode(WorkMode.REMOTE);
        profile.setNoticePeriodDays(30);

        when(careerProfileService.updateProfileForPrincipal(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.any(UpdateCareerProfileRequest.class)))
                .thenReturn(profile);

        String requestBody = """
                {
                    "professionalHeadline": "Senior Cloud Architect",
                    "currentTitle": "Lead Engineer",
                    "currentCompany": "Global Tech",
                    "totalExperienceMonths": 96,
                    "currentLocation": "Seattle, WA",
                    "preferredWorkLocation": "Remote, US",
                    "preferredWorkMode": "REMOTE",
                    "noticePeriodDays": 30
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.professionalHeadline").value("Senior Cloud Architect"))
                .andExpect(jsonPath("$.currentTitle").value("Lead Engineer"))
                .andExpect(jsonPath("$.currentCompany").value("Global Tech"))
                .andExpect(jsonPath("$.totalExperienceMonths").value(96))
                .andExpect(jsonPath("$.currentLocation").value("Seattle, WA"))
                .andExpect(jsonPath("$.preferredWorkLocation").value("Remote, US"))
                .andExpect(jsonPath("$.preferredWorkMode").value("REMOTE"))
                .andExpect(jsonPath("$.noticePeriodDays").value(30))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                // Verify Prompt 23+ fields do not leak
                .andExpect(jsonPath("$.skills").doesNotExist())
                .andExpect(jsonPath("$.experiences").doesNotExist())
                .andExpect(jsonPath("$.projects").doesNotExist())
                .andExpect(jsonPath("$.education").doesNotExist());
    }

    @Test
    void updateProfile_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "professionalHeadline": "Attempted update without auth"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProfile_WhenTotalExperienceMonthsIsNegative_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "totalExperienceMonths": -12
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.totalExperienceMonths").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProfile_WhenNoticePeriodDaysIsNegative_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "noticePeriodDays": -1
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.noticePeriodDays").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProfile_WhenWorkModeIsInvalid_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "preferredWorkMode": "TELEPATHIC"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProfile_WhenHeadlineExceeds200Characters_Returns400BadRequest() throws Exception {
        String oversizedHeadline = "A".repeat(201);
        String requestBody = String.format("{\"professionalHeadline\": \"%s\"}", oversizedHeadline);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.professionalHeadline").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProfile_WhenClientPassesTamperingPayloadWithDifferentUserId_IgnoresPayloadAndBindsToPrincipal() throws Exception {
        UUID legitimateUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User legitimateUser = createPersistedUser(legitimateUserId, "user@joblivo.com", "Legit User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, legitimateUser, now, now);
        profile.setProfessionalHeadline("Legitimate Headline");

        when(careerProfileService.updateProfileForPrincipal(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.any(UpdateCareerProfileRequest.class)))
                .thenReturn(profile);

        // Attacker attempts to forge ownership to another user ID
        String tamperingPayload = """
                {
                    "userId": "00000000-0000-0000-0000-000000000000",
                    "id": "11111111-1111-1111-1111-111111111111",
                    "professionalHeadline": "Legitimate Headline"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId.toString()))
                .andExpect(jsonPath("$.userId").value(legitimateUserId.toString()))
                .andExpect(jsonPath("$.professionalHeadline").value("Legitimate Headline"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createWorkExperience_WhenAuthenticatedWithValidData_Returns201AndResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);
        CareerProfileWorkExperience experience = new CareerProfileWorkExperience(
                expId, profile, "Acme Corp", "Backend Engineer", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2021, 1, 1), java.time.LocalDate.of(2023, 1, 1),
                false, "San Francisco, CA", "Designed distributed systems", 1, now, now
        );

        when(careerProfileService.addWorkExperience(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.any(WorkExperienceRequest.class)))
                .thenReturn(experience);

        String requestBody = """
                {
                    "companyName": "Acme Corp",
                    "jobTitle": "Backend Engineer",
                    "employmentType": "FULL_TIME",
                    "startDate": "2021-01-01",
                    "endDate": "2023-01-01",
                    "currentlyWorking": false,
                    "location": "San Francisco, CA",
                    "description": "Designed distributed systems",
                    "displayOrder": 1
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/career-profile/work-experiences/" + expId)))
                .andExpect(jsonPath("$.id").value(expId.toString()))
                .andExpect(jsonPath("$.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.startDate").value("2021-01-01"))
                .andExpect(jsonPath("$.endDate").value("2023-01-01"))
                .andExpect(jsonPath("$.currentlyWorking").value(false))
                .andExpect(jsonPath("$.location").value("San Francisco, CA"))
                .andExpect(jsonPath("$.description").value("Designed distributed systems"))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                // Verify persistence internals and future fields are absent
                .andExpect(jsonPath("$.careerProfileId").doesNotExist())
                .andExpect(jsonPath("$.skills").doesNotExist())
                .andExpect(jsonPath("$.technologies").doesNotExist());
    }

    @Test
    void createWorkExperience_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "companyName": "Acme Corp",
                    "jobTitle": "Backend Engineer",
                    "employmentType": "FULL_TIME",
                    "startDate": "2021-01-01"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createWorkExperience_WithMissingRequiredFields_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "companyName": "",
                    "jobTitle": "   ",
                    "employmentType": null,
                    "startDate": null
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.companyName").exists())
                .andExpect(jsonPath("$.validationErrors.jobTitle").exists())
                .andExpect(jsonPath("$.validationErrors.employmentType").exists())
                .andExpect(jsonPath("$.validationErrors.startDate").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createWorkExperience_WithInvalidEmploymentType_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "companyName": "Acme Corp",
                    "jobTitle": "Engineer",
                    "employmentType": "ASTRONAUT_TYPE",
                    "startDate": "2021-01-01"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createWorkExperience_WithInvalidDateRange_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "companyName": "Acme Corp",
                    "jobTitle": "Engineer",
                    "employmentType": "FULL_TIME",
                    "startDate": "2023-01-01",
                    "endDate": "2022-01-01",
                    "currentlyWorking": false
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.dateRangeValid").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createWorkExperience_WithContradictoryCurrentlyWorkingAndEndDate_Returns400BadRequest() throws Exception {
        String requestBody = """
                {
                    "companyName": "Acme Corp",
                    "jobTitle": "Engineer",
                    "employmentType": "FULL_TIME",
                    "startDate": "2021-01-01",
                    "endDate": "2023-01-01",
                    "currentlyWorking": true
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.currentlyWorkingValid").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createWorkExperience_WhenClientSuppliesForgedUserId_IgnoresPayloadAndBindsToPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);
        CareerProfileWorkExperience experience = new CareerProfileWorkExperience(
                expId, profile, "Acme Corp", "Backend Engineer", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2021, 1, 1), null, true, null, null, 0, now, now
        );

        when(careerProfileService.addWorkExperience(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.any(WorkExperienceRequest.class)))
                .thenReturn(experience);

        String tamperingPayload = """
                {
                    "userId": "00000000-0000-0000-0000-000000000000",
                    "careerProfileId": "11111111-1111-1111-1111-111111111111",
                    "companyName": "Acme Corp",
                    "jobTitle": "Backend Engineer",
                    "employmentType": "FULL_TIME",
                    "startDate": "2021-01-01",
                    "currentlyWorking": true
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/work-experiences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(expId.toString()))
                .andExpect(jsonPath("$.companyName").value("Acme Corp"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getWorkExperiences_WhenAuthenticated_Returns200AndList() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);

        CareerProfileWorkExperience exp1 = new CareerProfileWorkExperience(
                UUID.randomUUID(), profile, "Company A", "Role A", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2022, 1, 1), null, true, "NY", "Lead", 0, now, now
        );
        CareerProfileWorkExperience exp2 = new CareerProfileWorkExperience(
                UUID.randomUUID(), profile, "Company B", "Role B", EmploymentType.CONTRACT,
                java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.of(2021, 12, 31), false, "Remote", "Dev", 1, now, now
        );

        when(careerProfileService.getWorkExperiences("user@joblivo.com"))
                .thenReturn(List.of(exp1, exp2));

        mockMvc.perform(get("/api/v1/career-profile/work-experiences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Company A"))
                .andExpect(jsonPath("$[0].jobTitle").value("Role A"))
                .andExpect(jsonPath("$[0].currentlyWorking").value(true))
                .andExpect(jsonPath("$[1].companyName").value("Company B"))
                .andExpect(jsonPath("$[1].jobTitle").value("Role B"))
                .andExpect(jsonPath("$[1].currentlyWorking").value(false));
    }

    @Test
    void getWorkExperiences_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile/work-experiences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateWorkExperience_WhenAuthenticatedWithValidData_Returns200AndResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);
        CareerProfileWorkExperience updatedExp = new CareerProfileWorkExperience(
                expId, profile, "Updated Company", "Senior Role", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.of(2023, 1, 1),
                false, "Seattle, WA", "Architecture", 0, now, now
        );

        when(careerProfileService.updateWorkExperience(
                org.mockito.ArgumentMatchers.eq("user@joblivo.com"),
                org.mockito.ArgumentMatchers.eq(expId),
                org.mockito.ArgumentMatchers.any(WorkExperienceRequest.class)))
                .thenReturn(updatedExp);

        String requestBody = """
                {
                    "companyName": "Updated Company",
                    "jobTitle": "Senior Role",
                    "employmentType": "FULL_TIME",
                    "startDate": "2020-01-01",
                    "endDate": "2023-01-01",
                    "currentlyWorking": false,
                    "location": "Seattle, WA",
                    "description": "Architecture",
                    "displayOrder": 0
                }
                """;

        mockMvc.perform(put("/api/v1/career-profile/work-experiences/" + expId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expId.toString()))
                .andExpect(jsonPath("$.companyName").value("Updated Company"))
                .andExpect(jsonPath("$.jobTitle").value("Senior Role"))
                .andExpect(jsonPath("$.currentlyWorking").value(false));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateWorkExperience_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignExpId = UUID.randomUUID();

        when(careerProfileService.updateWorkExperience(
                org.mockito.ArgumentMatchers.eq("user@joblivo.com"),
                org.mockito.ArgumentMatchers.eq(foreignExpId),
                org.mockito.ArgumentMatchers.any(WorkExperienceRequest.class)))
                .thenThrow(new WorkExperienceNotFoundException("Work experience not found with id: " + foreignExpId));

        String requestBody = """
                {
                    "companyName": "Acme Corp",
                    "jobTitle": "Engineer",
                    "employmentType": "FULL_TIME",
                    "startDate": "2021-01-01",
                    "currentlyWorking": true
                }
                """;

        mockMvc.perform(put("/api/v1/career-profile/work-experiences/" + foreignExpId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteWorkExperience_WhenAuthenticated_Returns204NoContent() throws Exception {
        UUID expId = UUID.randomUUID();

        org.mockito.Mockito.doNothing().when(careerProfileService).deleteWorkExperience("user@joblivo.com", expId);

        mockMvc.perform(delete("/api/v1/career-profile/work-experiences/" + expId))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(careerProfileService).deleteWorkExperience("user@joblivo.com", expId);
    }

    @Test
    void deleteWorkExperience_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID expId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/career-profile/work-experiences/" + expId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteWorkExperience_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignExpId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new WorkExperienceNotFoundException("Work experience not found with id: " + foreignExpId))
                .when(careerProfileService).deleteWorkExperience("user@joblivo.com", foreignExpId);

        mockMvc.perform(delete("/api/v1/career-profile/work-experiences/" + foreignExpId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    // =========================================================================
    // Skills & Technologies Foundation Endpoint Tests
    // =========================================================================

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenAuthenticatedAndValid_Returns201AndSkillResponse() throws Exception {
        UUID skillId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);

        CareerProfileSkill skill = new CareerProfileSkill(
                skillId, profile, "PostgreSQL", SkillCategory.DATABASE, SkillProficiency.ADVANCED,
                new BigDecimal("5.5"), LocalDate.of(2026, 8, 1), 1, now, now
        );

        SkillRequest request = new SkillRequest(
                "PostgreSQL", SkillCategory.DATABASE, SkillProficiency.ADVANCED,
                new BigDecimal("5.5"), LocalDate.of(2026, 8, 1), 1
        );

        when(careerProfileService.addSkill(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), any(SkillRequest.class)))
                .thenReturn(skill);

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/career-profile/skills/" + skillId)))
                .andExpect(jsonPath("$.id").value(skillId.toString()))
                .andExpect(jsonPath("$.name").value("PostgreSQL"))
                .andExpect(jsonPath("$.category").value("DATABASE"))
                .andExpect(jsonPath("$.proficiency").value("ADVANCED"))
                .andExpect(jsonPath("$.yearsOfExperience").value(5.5))
                .andExpect(jsonPath("$.lastUsedDate").value("2026-08-01"))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createSkill_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        SkillRequest request = new SkillRequest("Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED, null, null, 0);

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenMissingRequiredName_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                    "name": null,
                    "category": "CLOUD",
                    "proficiency": "ADVANCED"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").isNotEmpty());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenBlankName_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                    "name": "   ",
                    "category": "CLOUD",
                    "proficiency": "ADVANCED"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").isNotEmpty());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenInvalidCategory_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                    "name": "AWS",
                    "category": "INVALID_CATEGORY",
                    "proficiency": "ADVANCED"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenInvalidProficiency_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                    "name": "AWS",
                    "category": "CLOUD",
                    "proficiency": "SUPER_EXPERT"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenNegativeYearsOfExperience_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                    "name": "AWS",
                    "category": "CLOUD",
                    "proficiency": "ADVANCED",
                    "yearsOfExperience": -1.0
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.yearsOfExperience").isNotEmpty());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenZeroYearsOfExperience_Returns201Created() throws Exception {
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), user, Instant.now(), Instant.now());
        CareerProfileSkill skill = new CareerProfileSkill(
                skillId, profile, "Terraform", SkillCategory.TOOL, SkillProficiency.BEGINNER,
                BigDecimal.ZERO, null, 0, Instant.now(), Instant.now()
        );

        SkillRequest request = new SkillRequest(
                "Terraform", SkillCategory.TOOL, SkillProficiency.BEGINNER,
                BigDecimal.ZERO, null, 0
        );

        when(careerProfileService.addSkill(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), any(SkillRequest.class)))
                .thenReturn(skill);

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearsOfExperience").value(0));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createSkill_WhenDuplicateSkill_Returns409Conflict() throws Exception {
        SkillRequest request = new SkillRequest("AWS", SkillCategory.CLOUD, SkillProficiency.ADVANCED, null, null, 0);

        when(careerProfileService.addSkill(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), any(SkillRequest.class)))
                .thenThrow(new DuplicateSkillException("Skill 'AWS' already exists in this career profile"));

        mockMvc.perform(post("/api/v1/career-profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getSkills_WhenAuthenticated_Returns200AndSkillList() throws Exception {
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill skill1 = new CareerProfileSkill(
                UUID.randomUUID(), profile, "AWS", SkillCategory.CLOUD, SkillProficiency.EXPERT,
                new BigDecimal("5.0"), null, 0, Instant.now(), Instant.now()
        );
        CareerProfileSkill skill2 = new CareerProfileSkill(
                UUID.randomUUID(), profile, "Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED,
                new BigDecimal("8.0"), null, 1, Instant.now(), Instant.now()
        );

        when(careerProfileService.getSkills("user@joblivo.com")).thenReturn(List.of(skill1, skill2));

        mockMvc.perform(get("/api/v1/career-profile/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("AWS"))
                .andExpect(jsonPath("$[1].name").value("Java"));
    }

    @Test
    void getSkills_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile/skills"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateSkill_WhenAuthenticatedAndValid_Returns200AndSkillResponse() throws Exception {
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), user, Instant.now(), Instant.now());

        CareerProfileSkill updated = new CareerProfileSkill(
                skillId, profile, "Python 3", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.EXPERT,
                new BigDecimal("4.0"), LocalDate.of(2026, 9, 1), 2, Instant.now(), Instant.now()
        );

        SkillRequest request = new SkillRequest(
                "Python 3", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.EXPERT,
                new BigDecimal("4.0"), LocalDate.of(2026, 9, 1), 2
        );

        when(careerProfileService.updateSkill(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.eq(skillId), any(SkillRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/career-profile/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(skillId.toString()))
                .andExpect(jsonPath("$.name").value("Python 3"))
                .andExpect(jsonPath("$.proficiency").value("EXPERT"))
                .andExpect(jsonPath("$.yearsOfExperience").value(4.0))
                .andExpect(jsonPath("$.displayOrder").value(2));
    }

    @Test
    void updateSkill_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID skillId = UUID.randomUUID();
        SkillRequest request = new SkillRequest("Rust", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED, null, null, 0);

        mockMvc.perform(put("/api/v1/career-profile/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateSkill_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignSkillId = UUID.randomUUID();
        SkillRequest request = new SkillRequest("Go", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.INTERMEDIATE, null, null, 0);

        when(careerProfileService.updateSkill(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.eq(foreignSkillId), any(SkillRequest.class)))
                .thenThrow(new SkillNotFoundException("Skill not found with id: " + foreignSkillId));

        mockMvc.perform(put("/api/v1/career-profile/skills/" + foreignSkillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateSkill_WhenDuplicateSkill_Returns409Conflict() throws Exception {
        UUID skillId = UUID.randomUUID();
        SkillRequest request = new SkillRequest("AWS", SkillCategory.CLOUD, SkillProficiency.EXPERT, null, null, 0);

        when(careerProfileService.updateSkill(org.mockito.ArgumentMatchers.eq("user@joblivo.com"), org.mockito.ArgumentMatchers.eq(skillId), any(SkillRequest.class)))
                .thenThrow(new DuplicateSkillException("Skill 'AWS' already exists in this career profile"));

        mockMvc.perform(put("/api/v1/career-profile/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteSkill_WhenAuthenticated_Returns204NoContent() throws Exception {
        UUID skillId = UUID.randomUUID();

        org.mockito.Mockito.doNothing().when(careerProfileService).deleteSkill("user@joblivo.com", skillId);

        mockMvc.perform(delete("/api/v1/career-profile/skills/" + skillId))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(careerProfileService).deleteSkill("user@joblivo.com", skillId);
    }

    @Test
    void deleteSkill_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID skillId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/career-profile/skills/" + skillId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteSkill_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignSkillId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new SkillNotFoundException("Skill not found with id: " + foreignSkillId))
                .when(careerProfileService).deleteSkill("user@joblivo.com", foreignSkillId);

        mockMvc.perform(delete("/api/v1/career-profile/skills/" + foreignSkillId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    // ==========================================
    // Project Controller Tests
    // ==========================================

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenAuthenticatedAndValid_Returns201CreatedAndLocationHeader() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectRequest request = new ProjectRequest(
                "Joblivo SaaS Platform",
                ProjectType.PROFESSIONAL,
                "Lead Engineer",
                "Built core microservices.",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                false,
                "https://joblivo.com",
                0
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileProject project = new CareerProfileProject(
                projectId,
                profile,
                "Joblivo SaaS Platform",
                ProjectType.PROFESSIONAL,
                "Lead Engineer",
                "Built core microservices.",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                false,
                "https://joblivo.com",
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addProject(eq("user@joblivo.com"), any(ProjectRequest.class))).thenReturn(project);

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/career-profile/projects/" + projectId))
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Joblivo SaaS Platform"))
                .andExpect(jsonPath("$.projectType").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.role").value("Lead Engineer"))
                .andExpect(jsonPath("$.description").value("Built core microservices."))
                .andExpect(jsonPath("$.startDate").value("2024-01-01"))
                .andExpect(jsonPath("$.endDate").value("2024-12-31"))
                .andExpect(jsonPath("$.currentlyActive").value(false))
                .andExpect(jsonPath("$.projectUrl").value("https://joblivo.com"))
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void addProject_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        ProjectRequest request = new ProjectRequest(
                "Joblivo SaaS Platform",
                ProjectType.PROFESSIONAL,
                null,
                null,
                null,
                null,
                false,
                null,
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenProjectNameMissing_Returns400BadRequest() throws Exception {
        String json = """
                {
                    "projectType": "PERSONAL"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.projectName").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenProjectNameBlank_Returns400BadRequest() throws Exception {
        ProjectRequest request = new ProjectRequest(
                "   ",
                ProjectType.PERSONAL,
                null,
                null,
                null,
                null,
                false,
                null,
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.projectName").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenProjectTypeMissing_Returns400BadRequest() throws Exception {
        String json = """
                {
                    "projectName": "My Project"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.projectType").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenProjectTypeInvalid_Returns400BadRequest() throws Exception {
        String json = """
                {
                    "projectName": "My Project",
                    "projectType": "NOT_AN_ENUM_VALUE"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenDateRangeInvalid_Returns400BadRequest() throws Exception {
        ProjectRequest request = new ProjectRequest(
                "Joblivo",
                ProjectType.PERSONAL,
                null,
                null,
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 1, 1),
                false,
                null,
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.dateRangeValid").value("End date cannot be before start date"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenCurrentlyActiveWithEndDate_Returns400BadRequest() throws Exception {
        ProjectRequest request = new ProjectRequest(
                "Joblivo",
                ProjectType.PERSONAL,
                null,
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 1),
                true,
                null,
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.currentlyActiveValid").value("End date must be null when currently active is true"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenUrlInvalid_Returns400BadRequest() throws Exception {
        ProjectRequest request = new ProjectRequest(
                "Joblivo",
                ProjectType.PERSONAL,
                null,
                null,
                null,
                null,
                false,
                "not-a-valid-url",
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.projectUrl").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addProject_WhenDuplicateProject_Returns409Conflict() throws Exception {
        ProjectRequest request = new ProjectRequest(
                "Joblivo",
                ProjectType.PERSONAL,
                null,
                null,
                null,
                null,
                false,
                null,
                0
        );

        when(careerProfileService.addProject(eq("user@joblivo.com"), any(ProjectRequest.class)))
                .thenThrow(new DuplicateProjectException("Project 'Joblivo' already exists in this career profile"));

        mockMvc.perform(post("/api/v1/career-profile/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getProjects_WhenAuthenticated_Returns200OkAndList() throws Exception {
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileProject p1 = new CareerProfileProject(
                UUID.randomUUID(), profile, "Project Alpha", ProjectType.PROFESSIONAL, "Lead", "Desc",
                LocalDate.of(2023, 1, 1), null, true, "https://alpha.com", 0, Instant.now(), Instant.now()
        );
        CareerProfileProject p2 = new CareerProfileProject(
                UUID.randomUUID(), profile, "Project Beta", ProjectType.PERSONAL, "Creator", null,
                null, null, false, null, 1, Instant.now(), Instant.now()
        );

        when(careerProfileService.getProjects("user@joblivo.com")).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/career-profile/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].projectName").value("Project Alpha"))
                .andExpect(jsonPath("$[0].projectType").value("PROFESSIONAL"))
                .andExpect(jsonPath("$[0].currentlyActive").value(true))
                .andExpect(jsonPath("$[1].projectName").value("Project Beta"))
                .andExpect(jsonPath("$[1].projectType").value("PERSONAL"));
    }

    @Test
    void getProjects_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getProject_WhenAuthenticatedAndFound_Returns200Ok() throws Exception {
        UUID projectId = UUID.randomUUID();
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileProject project = new CareerProfileProject(
                projectId, profile, "Single Project", ProjectType.ACADEMIC, "Scholar", "Thesis work",
                LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31), false, null, 0, Instant.now(), Instant.now()
        );

        when(careerProfileService.getProject("user@joblivo.com", projectId)).thenReturn(project);

        mockMvc.perform(get("/api/v1/career-profile/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Single Project"))
                .andExpect(jsonPath("$.projectType").value("ACADEMIC"))
                .andExpect(jsonPath("$.role").value("Scholar"));
    }

    @Test
    void getProject_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/career-profile/projects/" + projectId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getProject_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignProjectId = UUID.randomUUID();

        when(careerProfileService.getProject("user@joblivo.com", foreignProjectId))
                .thenThrow(new ProjectNotFoundException("Project not found with id: " + foreignProjectId));

        mockMvc.perform(get("/api/v1/career-profile/projects/" + foreignProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProject_WhenAuthenticatedAndValid_Returns200Ok() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectRequest request = new ProjectRequest(
                "Updated Project",
                ProjectType.FREELANCE,
                "Consultant",
                "Redesigned pipeline.",
                LocalDate.of(2023, 1, 1),
                null,
                true,
                "https://consulting.example.com",
                2
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileProject updated = new CareerProfileProject(
                projectId,
                profile,
                "Updated Project",
                ProjectType.FREELANCE,
                "Consultant",
                "Redesigned pipeline.",
                LocalDate.of(2023, 1, 1),
                null,
                true,
                "https://consulting.example.com",
                2,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.updateProject(eq("user@joblivo.com"), eq(projectId), any(ProjectRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/career-profile/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Updated Project"))
                .andExpect(jsonPath("$.projectType").value("FREELANCE"))
                .andExpect(jsonPath("$.role").value("Consultant"))
                .andExpect(jsonPath("$.currentlyActive").value(true))
                .andExpect(jsonPath("$.projectUrl").value("https://consulting.example.com"))
                .andExpect(jsonPath("$.displayOrder").value(2));
    }

    @Test
    void updateProject_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectRequest request = new ProjectRequest("Updated Project", ProjectType.FREELANCE, null, null, null, null, false, null, 0);

        mockMvc.perform(put("/api/v1/career-profile/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProject_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignProjectId = UUID.randomUUID();
        ProjectRequest request = new ProjectRequest("Updated Project", ProjectType.FREELANCE, null, null, null, null, false, null, 0);

        when(careerProfileService.updateProject(eq("user@joblivo.com"), eq(foreignProjectId), any(ProjectRequest.class)))
                .thenThrow(new ProjectNotFoundException("Project not found with id: " + foreignProjectId));

        mockMvc.perform(put("/api/v1/career-profile/projects/" + foreignProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateProject_WhenDuplicateProjectName_Returns409Conflict() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectRequest request = new ProjectRequest("Existing Project", ProjectType.FREELANCE, null, null, null, null, false, null, 0);

        when(careerProfileService.updateProject(eq("user@joblivo.com"), eq(projectId), any(ProjectRequest.class)))
                .thenThrow(new DuplicateProjectException("Project 'Existing Project' already exists in this career profile"));

        mockMvc.perform(put("/api/v1/career-profile/projects/" + projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteProject_WhenAuthenticated_Returns204NoContent() throws Exception {
        UUID projectId = UUID.randomUUID();

        org.mockito.Mockito.doNothing().when(careerProfileService).deleteProject("user@joblivo.com", projectId);

        mockMvc.perform(delete("/api/v1/career-profile/projects/" + projectId))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(careerProfileService).deleteProject("user@joblivo.com", projectId);
    }

    @Test
    void deleteProject_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/career-profile/projects/" + projectId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteProject_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignProjectId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new ProjectNotFoundException("Project not found with id: " + foreignProjectId))
                .when(careerProfileService).deleteProject("user@joblivo.com", foreignProjectId);

        mockMvc.perform(delete("/api/v1/career-profile/projects/" + foreignProjectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    // =========================================================================
    // Master Career Profile — Education Foundation Endpoints
    // =========================================================================

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenValid_Returns201CreatedWithLocationHeader() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest(
                "Stanford University",
                "B.S.",
                "Computer Science",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1),
                LocalDate.of(2022, 6, 15),
                false,
                "3.9 GPA",
                "Stanford, CA",
                "Dean's List",
                1
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation education = new CareerProfileEducation(
                educationId,
                profile,
                "Stanford University",
                "B.S.",
                "Computer Science",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1),
                LocalDate.of(2022, 6, 15),
                false,
                "3.9 GPA",
                "Stanford, CA",
                "Dean's List",
                1,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addEducation(eq("user@joblivo.com"), any(EducationRequest.class)))
                .thenReturn(education);

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/career-profile/education/" + educationId))
                .andExpect(jsonPath("$.id").value(educationId.toString()))
                .andExpect(jsonPath("$.institutionName").value("Stanford University"))
                .andExpect(jsonPath("$.degree").value("B.S."))
                .andExpect(jsonPath("$.fieldOfStudy").value("Computer Science"))
                .andExpect(jsonPath("$.educationLevel").value("UNDERGRADUATE"))
                .andExpect(jsonPath("$.startDate").value("2018-09-01"))
                .andExpect(jsonPath("$.endDate").value("2022-06-15"))
                .andExpect(jsonPath("$.currentlyStudying").value(false))
                .andExpect(jsonPath("$.grade").value("3.9 GPA"))
                .andExpect(jsonPath("$.location").value("Stanford, CA"))
                .andExpect(jsonPath("$.description").value("Dean's List"))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WithoutDates_Returns201Created() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest(
                "Self-Taught Institute",
                "Certificate",
                "Programming",
                EducationLevel.PROFESSIONAL,
                null,
                null,
                false,
                null,
                null,
                null,
                0
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation education = new CareerProfileEducation(
                educationId,
                profile,
                "Self-Taught Institute",
                "Certificate",
                "Programming",
                EducationLevel.PROFESSIONAL,
                null,
                null,
                false,
                null,
                null,
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addEducation(eq("user@joblivo.com"), any(EducationRequest.class)))
                .thenReturn(education);

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(educationId.toString()))
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WithoutDegree_Returns201Created() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest(
                "High School Academy",
                null,
                null,
                EducationLevel.HIGH_SCHOOL,
                LocalDate.of(2014, 9, 1),
                LocalDate.of(2018, 6, 1),
                false,
                null,
                null,
                null,
                0
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation education = new CareerProfileEducation(
                educationId,
                profile,
                "High School Academy",
                null,
                null,
                EducationLevel.HIGH_SCHOOL,
                LocalDate.of(2014, 9, 1),
                LocalDate.of(2018, 6, 1),
                false,
                null,
                null,
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addEducation(eq("user@joblivo.com"), any(EducationRequest.class)))
                .thenReturn(education);

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(educationId.toString()))
                .andExpect(jsonPath("$.degree").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WithoutGrade_Returns201Created() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest(
                "Open University",
                "Diploma",
                "Design",
                EducationLevel.DIPLOMA,
                null,
                null,
                false,
                null,
                null,
                null,
                0
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation education = new CareerProfileEducation(
                educationId,
                profile,
                "Open University",
                "Diploma",
                "Design",
                EducationLevel.DIPLOMA,
                null,
                null,
                false,
                null,
                null,
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addEducation(eq("user@joblivo.com"), any(EducationRequest.class)))
                .thenReturn(education);

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(educationId.toString()))
                .andExpect(jsonPath("$.grade").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WithCurrentlyStudyingFalseAndValidEndDate_Returns201Created() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest(
                "Tech Institute",
                "B.Tech",
                "IT",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 8, 1),
                LocalDate.of(2022, 5, 1),
                false,
                "8.5 CGPA",
                null,
                null,
                0
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation education = new CareerProfileEducation(
                educationId,
                profile,
                "Tech Institute",
                "B.Tech",
                "IT",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 8, 1),
                LocalDate.of(2022, 5, 1),
                false,
                "8.5 CGPA",
                null,
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addEducation(eq("user@joblivo.com"), any(EducationRequest.class)))
                .thenReturn(education);

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentlyStudying").value(false))
                .andExpect(jsonPath("$.endDate").value("2022-05-01"));
    }

    @Test
    void createEducation_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        EducationRequest request = new EducationRequest(
                "Stanford", "B.S.", "CS", EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenMissingInstitutionName_Returns400BadRequest() throws Exception {
        String json = """
                {
                    "educationLevel": "UNDERGRADUATE"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.institutionName").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenBlankInstitutionName_Returns400BadRequest() throws Exception {
        String json = """
                {
                    "institutionName": "   ",
                    "educationLevel": "UNDERGRADUATE"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.institutionName").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenInvalidEducationLevel_Returns400BadRequest() throws Exception {
        String json = """
                {
                    "institutionName": "Stanford University",
                    "educationLevel": "INVALID_LEVEL"
                }
                """;

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenEndDateBeforeStartDate_Returns400BadRequest() throws Exception {
        EducationRequest request = new EducationRequest(
                "Stanford University",
                "B.S.",
                "CS",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2022, 1, 1),
                false,
                null,
                null,
                null,
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.dateRangeValid").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenCurrentlyStudyingWithEndDate_Returns400BadRequest() throws Exception {
        EducationRequest request = new EducationRequest(
                "Stanford University",
                "B.S.",
                "CS",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 1, 1),
                true,
                null,
                null,
                null,
                0
        );

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.currentlyStudyingValid").exists());

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createEducation_WhenDuplicateRecord_Returns409Conflict() throws Exception {
        EducationRequest request = new EducationRequest(
                "Stanford University",
                "B.S.",
                "CS",
                EducationLevel.UNDERGRADUATE,
                null,
                null,
                false,
                null,
                null,
                null,
                0
        );

        when(careerProfileService.addEducation(eq("user@joblivo.com"), any(EducationRequest.class)))
                .thenThrow(new DuplicateEducationException("An education record with the same institution, degree, and field of study already exists in this career profile"));

        mockMvc.perform(post("/api/v1/career-profile/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getEducation_WhenAuthenticated_Returns200OkWithList() throws Exception {
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation edu1 = new CareerProfileEducation(
                UUID.randomUUID(), profile, "Stanford University", "B.S.", "CS", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1), LocalDate.of(2022, 6, 15), false, "3.9", "Stanford", "Desc", 0, Instant.now(), Instant.now()
        );
        CareerProfileEducation edu2 = new CareerProfileEducation(
                UUID.randomUUID(), profile, "MIT", "M.Eng.", "EECS", EducationLevel.POSTGRADUATE,
                LocalDate.of(2022, 9, 1), null, true, null, "Cambridge", "Research", 1, Instant.now(), Instant.now()
        );

        when(careerProfileService.getEducation("user@joblivo.com")).thenReturn(List.of(edu1, edu2));

        mockMvc.perform(get("/api/v1/career-profile/education"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].institutionName").value("Stanford University"))
                .andExpect(jsonPath("$[0].educationLevel").value("UNDERGRADUATE"))
                .andExpect(jsonPath("$[1].institutionName").value("MIT"))
                .andExpect(jsonPath("$[1].educationLevel").value("POSTGRADUATE"));
    }

    @Test
    void getEducation_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile/education"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getEducationById_WhenAuthenticatedAndExists_Returns200Ok() throws Exception {
        UUID educationId = UUID.randomUUID();
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation education = new CareerProfileEducation(
                educationId, profile, "Oxford", "M.Sc.", "Math", EducationLevel.POSTGRADUATE,
                LocalDate.of(2022, 10, 1), LocalDate.of(2023, 9, 30), false, "Distinction", "Oxford", "Thesis", 0, Instant.now(), Instant.now()
        );

        when(careerProfileService.getEducation("user@joblivo.com", educationId)).thenReturn(education);

        mockMvc.perform(get("/api/v1/career-profile/education/" + educationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(educationId.toString()))
                .andExpect(jsonPath("$.institutionName").value("Oxford"))
                .andExpect(jsonPath("$.degree").value("M.Sc."))
                .andExpect(jsonPath("$.fieldOfStudy").value("Math"))
                .andExpect(jsonPath("$.educationLevel").value("POSTGRADUATE"));
    }

    @Test
    void getEducationById_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID educationId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/career-profile/education/" + educationId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getEducationById_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignEduId = UUID.randomUUID();

        when(careerProfileService.getEducation("user@joblivo.com", foreignEduId))
                .thenThrow(new EducationNotFoundException("Education record not found with id: " + foreignEduId));

        mockMvc.perform(get("/api/v1/career-profile/education/" + foreignEduId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateEducation_WhenAuthenticatedAndValid_Returns200Ok() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest(
                "Updated University",
                "M.S.",
                "Data Science",
                EducationLevel.POSTGRADUATE,
                LocalDate.of(2022, 9, 1),
                null,
                true,
                "4.0",
                "Boston, MA",
                "Research Fellow",
                1
        );

        CareerProfile profile = new CareerProfile(UUID.randomUUID(), createPersistedUser(UUID.randomUUID(), "user@joblivo.com", "User", UserStatus.ACTIVE), Instant.now(), Instant.now());
        CareerProfileEducation updated = new CareerProfileEducation(
                educationId,
                profile,
                "Updated University",
                "M.S.",
                "Data Science",
                EducationLevel.POSTGRADUATE,
                LocalDate.of(2022, 9, 1),
                null,
                true,
                "4.0",
                "Boston, MA",
                "Research Fellow",
                1,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.updateEducation(eq("user@joblivo.com"), eq(educationId), any(EducationRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/career-profile/education/" + educationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(educationId.toString()))
                .andExpect(jsonPath("$.institutionName").value("Updated University"))
                .andExpect(jsonPath("$.degree").value("M.S."))
                .andExpect(jsonPath("$.fieldOfStudy").value("Data Science"))
                .andExpect(jsonPath("$.educationLevel").value("POSTGRADUATE"))
                .andExpect(jsonPath("$.currentlyStudying").value(true))
                .andExpect(jsonPath("$.grade").value("4.0"))
                .andExpect(jsonPath("$.location").value("Boston, MA"))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    void updateEducation_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest("University", null, null, EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0);

        mockMvc.perform(put("/api/v1/career-profile/education/" + educationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateEducation_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignEduId = UUID.randomUUID();
        EducationRequest request = new EducationRequest("University", null, null, EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0);

        when(careerProfileService.updateEducation(eq("user@joblivo.com"), eq(foreignEduId), any(EducationRequest.class)))
                .thenThrow(new EducationNotFoundException("Education record not found with id: " + foreignEduId));

        mockMvc.perform(put("/api/v1/career-profile/education/" + foreignEduId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateEducation_WhenDuplicate_Returns409Conflict() throws Exception {
        UUID educationId = UUID.randomUUID();
        EducationRequest request = new EducationRequest("Existing University", "B.S.", "CS", EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0);

        when(careerProfileService.updateEducation(eq("user@joblivo.com"), eq(educationId), any(EducationRequest.class)))
                .thenThrow(new DuplicateEducationException("An education record with the same institution, degree, and field of study already exists in this career profile"));

        mockMvc.perform(put("/api/v1/career-profile/education/" + educationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteEducation_WhenAuthenticated_Returns204NoContent() throws Exception {
        UUID educationId = UUID.randomUUID();

        org.mockito.Mockito.doNothing().when(careerProfileService).deleteEducation("user@joblivo.com", educationId);

        mockMvc.perform(delete("/api/v1/career-profile/education/" + educationId))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(careerProfileService).deleteEducation("user@joblivo.com", educationId);
    }

    @Test
    void deleteEducation_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID educationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/career-profile/education/" + educationId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteEducation_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignEduId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new EducationNotFoundException("Education record not found with id: " + foreignEduId))
                .when(careerProfileService).deleteEducation("user@joblivo.com", foreignEduId);

        mockMvc.perform(delete("/api/v1/career-profile/education/" + foreignEduId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    // ==========================================
    // CERTIFICATION TESTS (PROMPT 27)
    // ==========================================

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenAuthenticatedAndValid_Returns201AndResponseWithLocation() throws Exception {
        UUID certId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "AWS Certified Solutions Architect – Associate",
                "Amazon Web Services",
                "AWS-123456",
                "https://aws.amazon.com/verify?id=123456",
                LocalDate.of(2023, 1, 15),
                LocalDate.of(2026, 1, 15),
                false,
                "Architecting distributed systems on AWS.",
                0
        );

        CareerProfileCertification savedCert = new CareerProfileCertification(
                certId,
                profile,
                "AWS Certified Solutions Architect – Associate",
                "Amazon Web Services",
                "AWS-123456",
                "https://aws.amazon.com/verify?id=123456",
                LocalDate.of(2023, 1, 15),
                LocalDate.of(2026, 1, 15),
                false,
                "Architecting distributed systems on AWS.",
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addCertification(eq("user@joblivo.com"), any(CertificationRequest.class))).thenReturn(savedCert);

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/career-profile/certifications/" + certId)))
                .andExpect(jsonPath("$.id").value(certId.toString()))
                .andExpect(jsonPath("$.certificationName").value("AWS Certified Solutions Architect – Associate"))
                .andExpect(jsonPath("$.issuingOrganization").value("Amazon Web Services"))
                .andExpect(jsonPath("$.credentialId").value("AWS-123456"))
                .andExpect(jsonPath("$.credentialUrl").value("https://aws.amazon.com/verify?id=123456"))
                .andExpect(jsonPath("$.issueDate").value("2023-01-15"))
                .andExpect(jsonPath("$.expirationDate").value("2026-01-15"))
                .andExpect(jsonPath("$.doesNotExpire").value(false))
                .andExpect(jsonPath("$.description").value("Architecting distributed systems on AWS."))
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WithNonExpiringCertification_Returns201() throws Exception {
        UUID certId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "Certified Kubernetes Administrator",
                "CNCF",
                null,
                null,
                LocalDate.of(2022, 6, 1),
                null,
                true,
                null,
                1
        );

        CareerProfileCertification savedCert = new CareerProfileCertification(
                certId,
                profile,
                "Certified Kubernetes Administrator",
                "CNCF",
                null,
                null,
                LocalDate.of(2022, 6, 1),
                null,
                true,
                null,
                1,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addCertification(eq("user@joblivo.com"), any(CertificationRequest.class))).thenReturn(savedCert);

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificationName").value("Certified Kubernetes Administrator"))
                .andExpect(jsonPath("$.issuingOrganization").value("CNCF"))
                .andExpect(jsonPath("$.credentialId").doesNotExist())
                .andExpect(jsonPath("$.credentialUrl").doesNotExist())
                .andExpect(jsonPath("$.doesNotExpire").value(true))
                .andExpect(jsonPath("$.expirationDate").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WithoutCredentialIdAndUrl_Returns201() throws Exception {
        UUID certId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "Scrum Master Certified",
                "Scrum Alliance",
                null,
                null,
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2023, 3, 1),
                false,
                null,
                0
        );

        CareerProfileCertification savedCert = new CareerProfileCertification(
                certId,
                profile,
                "Scrum Master Certified",
                "Scrum Alliance",
                null,
                null,
                LocalDate.of(2021, 3, 1),
                LocalDate.of(2023, 3, 1),
                false,
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.addCertification(eq("user@joblivo.com"), any(CertificationRequest.class))).thenReturn(savedCert);

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificationName").value("Scrum Master Certified"))
                .andExpect(jsonPath("$.credentialId").doesNotExist())
                .andExpect(jsonPath("$.credentialUrl").doesNotExist());
    }

    @Test
    void addCertification_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null, null, null, true, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenCertificationNameIsBlank_Returns400BadRequest() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "   ", "AWS", null, null, null, null, true, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.certificationName").value("Certification name is required"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenIssuingOrganizationIsBlank_Returns400BadRequest() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "  ", null, null, null, null, true, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.issuingOrganization").value("Issuing organization is required"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenCredentialUrlIsInvalid_Returns400BadRequest() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, "invalid-url", null, null, true, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.credentialUrl").value("Credential URL must be a valid HTTP or HTTPS URL"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenDateRangeIsInvalid_Returns400BadRequest() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2024, 1, 1),
                false, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.dateRangeValid").value("Expiration date cannot be before issue date"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenDoesNotExpireTrueAndExpirationDatePopulated_Returns400BadRequest() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 1, 1),
                true, // Contradictory: doesNotExpire=true but expirationDate provided
                null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.doesNotExpireValid").value("Expiration date must be null when does not expire is true"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void addCertification_WhenDuplicate_Returns409Conflict() throws Exception {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", "AWS-123", null, null, null, true, null, 0
        );

        when(careerProfileService.addCertification(eq("user@joblivo.com"), any(CertificationRequest.class)))
                .thenThrow(new DuplicateCertificationException("A certification with credential ID 'AWS-123' already exists in this career profile"));

        mockMvc.perform(post("/api/v1/career-profile/certifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getCertifications_WhenAuthenticated_Returns200AndList() throws Exception {
        UUID certId1 = UUID.randomUUID();
        UUID certId2 = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification cert1 = new CareerProfileCertification(
                certId1, profile, "AWS SAA", "AWS", "AWS-1", null, null, null, true, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileCertification cert2 = new CareerProfileCertification(
                certId2, profile, "CKA", "CNCF", "CKA-2", null, null, null, true, null, 1, Instant.now(), Instant.now()
        );

        when(careerProfileService.getCertifications("user@joblivo.com")).thenReturn(List.of(cert1, cert2));

        mockMvc.perform(get("/api/v1/career-profile/certifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(certId1.toString()))
                .andExpect(jsonPath("$[0].certificationName").value("AWS SAA"))
                .andExpect(jsonPath("$[1].id").value(certId2.toString()))
                .andExpect(jsonPath("$[1].certificationName").value("CKA"));
    }

    @Test
    void getCertifications_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile/certifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getCertificationById_WhenAuthenticatedAndFound_Returns200AndResponse() throws Exception {
        UUID certId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification cert = new CareerProfileCertification(
                certId, profile, "AWS SAA", "AWS", "AWS-1", null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        when(careerProfileService.getCertification("user@joblivo.com", certId)).thenReturn(cert);

        mockMvc.perform(get("/api/v1/career-profile/certifications/" + certId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(certId.toString()))
                .andExpect(jsonPath("$.certificationName").value("AWS SAA"))
                .andExpect(jsonPath("$.issuingOrganization").value("AWS"));
    }

    @Test
    void getCertificationById_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID certId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/career-profile/certifications/" + certId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getCertificationById_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignCertId = UUID.randomUUID();

        when(careerProfileService.getCertification("user@joblivo.com", foreignCertId))
                .thenThrow(new CertificationNotFoundException("Certification not found with id: " + foreignCertId));

        mockMvc.perform(get("/api/v1/career-profile/certifications/" + foreignCertId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateCertification_WhenAuthenticatedAndValid_Returns200AndUpdatedResponse() throws Exception {
        UUID certId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "AWS Solutions Architect Professional",
                "Amazon Web Services",
                "AWS-PRO-999",
                "https://aws.amazon.com/verify?id=999",
                LocalDate.of(2023, 2, 1),
                LocalDate.of(2026, 2, 1),
                false,
                "Updated description",
                2
        );

        CareerProfileCertification updatedCert = new CareerProfileCertification(
                certId,
                profile,
                "AWS Solutions Architect Professional",
                "Amazon Web Services",
                "AWS-PRO-999",
                "https://aws.amazon.com/verify?id=999",
                LocalDate.of(2023, 2, 1),
                LocalDate.of(2026, 2, 1),
                false,
                "Updated description",
                2,
                Instant.now(),
                Instant.now()
        );

        when(careerProfileService.updateCertification(eq("user@joblivo.com"), eq(certId), any(CertificationRequest.class)))
                .thenReturn(updatedCert);

        mockMvc.perform(put("/api/v1/career-profile/certifications/" + certId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(certId.toString()))
                .andExpect(jsonPath("$.certificationName").value("AWS Solutions Architect Professional"))
                .andExpect(jsonPath("$.issuingOrganization").value("Amazon Web Services"))
                .andExpect(jsonPath("$.credentialId").value("AWS-PRO-999"))
                .andExpect(jsonPath("$.displayOrder").value(2));
    }

    @Test
    void updateCertification_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID certId = UUID.randomUUID();
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null, null, null, true, null, 0
        );

        mockMvc.perform(put("/api/v1/career-profile/certifications/" + certId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateCertification_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignCertId = UUID.randomUUID();
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null, null, null, true, null, 0
        );

        when(careerProfileService.updateCertification(eq("user@joblivo.com"), eq(foreignCertId), any(CertificationRequest.class)))
                .thenThrow(new CertificationNotFoundException("Certification not found with id: " + foreignCertId));

        mockMvc.perform(put("/api/v1/career-profile/certifications/" + foreignCertId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateCertification_WhenDuplicate_Returns409Conflict() throws Exception {
        UUID certId = UUID.randomUUID();
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", "DUP-CRED-1", null, null, null, true, null, 0
        );

        when(careerProfileService.updateCertification(eq("user@joblivo.com"), eq(certId), any(CertificationRequest.class)))
                .thenThrow(new DuplicateCertificationException("A certification with credential ID 'DUP-CRED-1' already exists in this career profile"));

        mockMvc.perform(put("/api/v1/career-profile/certifications/" + certId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteCertification_WhenAuthenticated_Returns204NoContent() throws Exception {
        UUID certId = UUID.randomUUID();

        org.mockito.Mockito.doNothing().when(careerProfileService).deleteCertification("user@joblivo.com", certId);

        mockMvc.perform(delete("/api/v1/career-profile/certifications/" + certId))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(careerProfileService).deleteCertification("user@joblivo.com", certId);
    }

    @Test
    void deleteCertification_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID certId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/career-profile/certifications/" + certId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteCertification_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignCertId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new CertificationNotFoundException("Certification not found with id: " + foreignCertId))
                .when(careerProfileService).deleteCertification("user@joblivo.com", foreignCertId);

        mockMvc.perform(delete("/api/v1/career-profile/certifications/" + foreignCertId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    // =========================================================================
    // Master Career Profile — Achievements Foundation (Prompt 28)
    // =========================================================================

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenValidRequest_Returns201CreatedAndLocationHeader() throws Exception {
        UUID achieveId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        Instant now = Instant.now();

        AchievementRequest request = new AchievementRequest(
                "Employee of the Year",
                AchievementType.AWARD,
                "Acme Corp",
                LocalDate.of(2023, 12, 15),
                "Outstanding contribution",
                "https://acme.com/awards/2023",
                1
        );

        CareerProfileAchievement created = new CareerProfileAchievement(
                achieveId, profile, "Employee of the Year", AchievementType.AWARD, "Acme Corp",
                LocalDate.of(2023, 12, 15), "Outstanding contribution", "https://acme.com/awards/2023",
                1, now, now
        );

        when(careerProfileService.addAchievement(eq("user@joblivo.com"), any(AchievementRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/career-profile/achievements/" + achieveId))
                .andExpect(jsonPath("$.id").value(achieveId.toString()))
                .andExpect(jsonPath("$.title").value("Employee of the Year"))
                .andExpect(jsonPath("$.achievementType").value("AWARD"))
                .andExpect(jsonPath("$.issuingOrganization").value("Acme Corp"))
                .andExpect(jsonPath("$.achievementDate").value("2023-12-15"))
                .andExpect(jsonPath("$.description").value("Outstanding contribution"))
                .andExpect(jsonPath("$.url").value("https://acme.com/awards/2023"))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenMinimalValidRequest_Returns201Created() throws Exception {
        UUID achieveId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        Instant now = Instant.now();

        AchievementRequest request = new AchievementRequest(
                "Hackathon Champion",
                AchievementType.HACKATHON,
                null,
                null,
                null,
                null,
                0
        );

        CareerProfileAchievement created = new CareerProfileAchievement(
                achieveId, profile, "Hackathon Champion", AchievementType.HACKATHON, null,
                null, null, null,
                0, now, now
        );

        when(careerProfileService.addAchievement(eq("user@joblivo.com"), any(AchievementRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hackathon Champion"))
                .andExpect(jsonPath("$.achievementType").value("HACKATHON"))
                .andExpect(jsonPath("$.issuingOrganization").doesNotExist())
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    void createAchievement_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenTitleIsBlank_Returns400BadRequest() throws Exception {
        AchievementRequest request = new AchievementRequest(
                "   ", AchievementType.AWARD, null, null, null, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.title").value("Title is required"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenAchievementTypeIsNull_Returns400BadRequest() throws Exception {
        AchievementRequest request = new AchievementRequest(
                "Award", null, null, null, null, null, 0
        );

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.achievementType").value("Achievement type is required"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenUrlIsInvalid_Returns400BadRequest() throws Exception {
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, "not-a-valid-url", 0
        );

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.url").value("URL must be a valid HTTP or HTTPS URL"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenDisplayOrderIsNegative_Returns400BadRequest() throws Exception {
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, -1
        );

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.displayOrder").value("Display order must be greater than or equal to 0"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void createAchievement_WhenDuplicate_Returns409Conflict() throws Exception {
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, "Acme", LocalDate.of(2023, 1, 1), null, null, 0
        );

        when(careerProfileService.addAchievement(eq("user@joblivo.com"), any(AchievementRequest.class)))
                .thenThrow(new DuplicateAchievementException("An achievement with the same title, type, date, and organization already exists in this career profile"));

        mockMvc.perform(post("/api/v1/career-profile/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getAchievements_WhenAuthenticated_Returns200AndList() throws Exception {
        UUID achieveId1 = UUID.randomUUID();
        UUID achieveId2 = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement a1 = new CareerProfileAchievement(
                achieveId1, profile, "Award 1", AchievementType.AWARD, "Org", null, null, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileAchievement a2 = new CareerProfileAchievement(
                achieveId2, profile, "Patent 1", AchievementType.PATENT, "USPTO", null, null, null, 1, Instant.now(), Instant.now()
        );

        when(careerProfileService.getAchievements("user@joblivo.com")).thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/career-profile/achievements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(achieveId1.toString()))
                .andExpect(jsonPath("$[0].title").value("Award 1"))
                .andExpect(jsonPath("$[0].achievementType").value("AWARD"))
                .andExpect(jsonPath("$[1].id").value(achieveId2.toString()))
                .andExpect(jsonPath("$[1].title").value("Patent 1"))
                .andExpect(jsonPath("$[1].achievementType").value("PATENT"));
    }

    @Test
    void getAchievements_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile/achievements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getAchievementById_WhenAuthenticatedAndFound_Returns200AndResponse() throws Exception {
        UUID achieveId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement achievement = new CareerProfileAchievement(
                achieveId, profile, "Award 1", AchievementType.AWARD, "Org", null, null, null, 0, Instant.now(), Instant.now()
        );

        when(careerProfileService.getAchievement("user@joblivo.com", achieveId)).thenReturn(achievement);

        mockMvc.perform(get("/api/v1/career-profile/achievements/" + achieveId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(achieveId.toString()))
                .andExpect(jsonPath("$.title").value("Award 1"))
                .andExpect(jsonPath("$.achievementType").value("AWARD"));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getAchievementById_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignAchieveId = UUID.randomUUID();

        when(careerProfileService.getAchievement("user@joblivo.com", foreignAchieveId))
                .thenThrow(new AchievementNotFoundException("Achievement not found with id: " + foreignAchieveId));

        mockMvc.perform(get("/api/v1/career-profile/achievements/" + foreignAchieveId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    void getAchievementById_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID achieveId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/career-profile/achievements/" + achieveId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateAchievement_WhenAuthenticatedAndValid_Returns200AndUpdatedResponse() throws Exception {
        UUID achieveId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "user@joblivo.com", "Test User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        Instant now = Instant.now();

        AchievementRequest request = new AchievementRequest(
                "Updated Award", AchievementType.AWARD, "New Org", LocalDate.of(2023, 6, 1), "New Desc", "https://example.com/award", 2
        );

        CareerProfileAchievement updated = new CareerProfileAchievement(
                achieveId, profile, "Updated Award", AchievementType.AWARD, "New Org",
                LocalDate.of(2023, 6, 1), "New Desc", "https://example.com/award", 2, now, now
        );

        when(careerProfileService.updateAchievement(eq("user@joblivo.com"), eq(achieveId), any(AchievementRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/career-profile/achievements/" + achieveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(achieveId.toString()))
                .andExpect(jsonPath("$.title").value("Updated Award"))
                .andExpect(jsonPath("$.issuingOrganization").value("New Org"))
                .andExpect(jsonPath("$.displayOrder").value(2));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateAchievement_WhenTitleIsBlank_Returns400BadRequest() throws Exception {
        UUID achieveId = UUID.randomUUID();
        AchievementRequest request = new AchievementRequest(
                "   ", AchievementType.AWARD, null, null, null, null, 0
        );

        mockMvc.perform(put("/api/v1/career-profile/achievements/" + achieveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.title").value("Title is required"));
    }

    @Test
    void updateAchievement_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID achieveId = UUID.randomUUID();
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, 0
        );

        mockMvc.perform(put("/api/v1/career-profile/achievements/" + achieveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateAchievement_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignAchieveId = UUID.randomUUID();
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, 0
        );

        when(careerProfileService.updateAchievement(eq("user@joblivo.com"), eq(foreignAchieveId), any(AchievementRequest.class)))
                .thenThrow(new AchievementNotFoundException("Achievement not found with id: " + foreignAchieveId));

        mockMvc.perform(put("/api/v1/career-profile/achievements/" + foreignAchieveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void updateAchievement_WhenDuplicate_Returns409Conflict() throws Exception {
        UUID achieveId = UUID.randomUUID();
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, "Org", LocalDate.of(2023, 1, 1), null, null, 0
        );

        when(careerProfileService.updateAchievement(eq("user@joblivo.com"), eq(achieveId), any(AchievementRequest.class)))
                .thenThrow(new DuplicateAchievementException("An achievement with the same title, type, date, and organization already exists in this career profile"));

        mockMvc.perform(put("/api/v1/career-profile/achievements/" + achieveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteAchievement_WhenAuthenticated_Returns204NoContent() throws Exception {
        UUID achieveId = UUID.randomUUID();

        org.mockito.Mockito.doNothing().when(careerProfileService).deleteAchievement("user@joblivo.com", achieveId);

        mockMvc.perform(delete("/api/v1/career-profile/achievements/" + achieveId))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(careerProfileService).deleteAchievement("user@joblivo.com", achieveId);
    }

    @Test
    void deleteAchievement_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        UUID achieveId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/career-profile/achievements/" + achieveId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void deleteAchievement_WhenNotFoundOrForeign_Returns404NotFound() throws Exception {
        UUID foreignAchieveId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new AchievementNotFoundException("Achievement not found with id: " + foreignAchieveId))
                .when(careerProfileService).deleteAchievement("user@joblivo.com", foreignAchieveId);

        mockMvc.perform(delete("/api/v1/career-profile/achievements/" + foreignAchieveId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("not found with id")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getMasterCareerProfile_WhenAuthenticated_Returns200AndMasterCareerProfileResponse() throws Exception {
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkExperienceResponse weResponse = new WorkExperienceResponse(
                UUID.randomUUID(), "Tech Corp", "Senior Dev", EmploymentType.FULL_TIME,
                LocalDate.of(2021, 1, 1), null, true, "SF", "Lead dev", 0, now, now
        );
        SkillResponse skillResponse = new SkillResponse(
                UUID.randomUUID(), "Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED,
                BigDecimal.valueOf(6), LocalDate.now(), 0, now, now
        );
        ProjectResponse projResponse = new ProjectResponse(
                UUID.randomUUID(), "Joblivo Project", ProjectType.PROFESSIONAL, "Architect",
                "Career Platform", LocalDate.of(2023, 1, 1), null, true, "https://example.com", 0, now, now
        );
        EducationResponse eduResponse = new EducationResponse(
                UUID.randomUUID(), "Stanford", "M.S.", "Computer Science", EducationLevel.POSTGRADUATE,
                LocalDate.of(2018, 9, 1), LocalDate.of(2020, 6, 1), false, "4.0", "Stanford, CA", "AI specialization", 0, now, now
        );
        CertificationResponse certResponse = new CertificationResponse(
                UUID.randomUUID(), "AWS Solutions Architect", "AWS", "AWS-12345", "https://aws.cert/12345",
                LocalDate.of(2022, 5, 1), null, true, "Cloud cert", 0, now, now
        );
        AchievementResponse achResponse = new AchievementResponse(
                UUID.randomUUID(), "Innovator Award", AchievementType.AWARD, "Enterprise Award",
                LocalDate.of(2023, 12, 1), "Tech Corp", "https://award.com", 0, now, now
        );

        CareerProfileCompletenessResponse completeness = new CareerProfileCompletenessResponse(
                100,
                SectionCompletenessResponse.of(true, 8),
                SectionCompletenessResponse.of(true, 1),
                SectionCompletenessResponse.of(true, 1),
                SectionCompletenessResponse.of(true, 1),
                SectionCompletenessResponse.of(true, 1),
                SectionCompletenessResponse.of(true, 1),
                SectionCompletenessResponse.of(true, 1)
        );

        MasterCareerProfileResponse fullResponse = new MasterCareerProfileResponse(
                profileId,
                "Staff Software Engineer",
                "Senior Principal Engineer",
                "Tech Corp",
                96,
                "San Francisco, CA",
                "Remote",
                WorkMode.REMOTE,
                30,
                now,
                now,
                List.of(weResponse),
                List.of(skillResponse),
                List.of(projResponse),
                List.of(eduResponse),
                List.of(certResponse),
                List.of(achResponse),
                completeness
        );

        when(careerProfileService.getMasterCareerProfile("user@joblivo.com")).thenReturn(fullResponse);

        mockMvc.perform(get("/api/v1/career-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(profileId.toString()))
                .andExpect(jsonPath("$.professionalHeadline").value("Staff Software Engineer"))
                .andExpect(jsonPath("$.currentTitle").value("Senior Principal Engineer"))
                .andExpect(jsonPath("$.currentCompany").value("Tech Corp"))
                .andExpect(jsonPath("$.totalExperienceMonths").value(96))
                .andExpect(jsonPath("$.currentLocation").value("San Francisco, CA"))
                .andExpect(jsonPath("$.preferredWorkLocation").value("Remote"))
                .andExpect(jsonPath("$.preferredWorkMode").value("REMOTE"))
                .andExpect(jsonPath("$.noticePeriodDays").value(30))
                .andExpect(jsonPath("$.workExperiences[0].companyName").value("Tech Corp"))
                .andExpect(jsonPath("$.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.projects[0].projectName").value("Joblivo Project"))
                .andExpect(jsonPath("$.education[0].institutionName").value("Stanford"))
                .andExpect(jsonPath("$.certifications[0].certificationName").value("AWS Solutions Architect"))
                .andExpect(jsonPath("$.achievements[0].title").value("Innovator Award"))
                .andExpect(jsonPath("$.completeness.completionPercentage").value(100))
                .andExpect(jsonPath("$.completeness.coreDetails.completed").value(true))
                .andExpect(jsonPath("$.completeness.coreDetails.itemCount").value(8))
                .andExpect(jsonPath("$.completeness.workExperience.completed").value(true))
                .andExpect(jsonPath("$.completeness.skills.completed").value(true))
                .andExpect(jsonPath("$.completeness.projects.completed").value(true))
                .andExpect(jsonPath("$.completeness.education.completed").value(true))
                .andExpect(jsonPath("$.completeness.certifications.completed").value(true))
                .andExpect(jsonPath("$.completeness.achievements.completed").value(true))
                // Ensure no credentials or out-of-scope fields leak
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.credentials").doesNotExist());
    }

    @Test
    void getMasterCareerProfile_WhenUnauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/career-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(careerProfileService);
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getMasterCareerProfile_WhenProfileNotFound_Returns404NotFound() throws Exception {
        when(careerProfileService.getMasterCareerProfile("user@joblivo.com"))
                .thenThrow(new CareerProfileNotFoundException("Career profile not found for user: user@joblivo.com"));

        mockMvc.perform(get("/api/v1/career-profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("Career profile not found")));
    }

    @Test
    @WithMockUser(username = "user@joblivo.com")
    void getMasterCareerProfile_IgnoresClientSuppliedUserIdOrProfileIdParams() throws Exception {
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        CareerProfileCompletenessResponse completeness = new CareerProfileCompletenessResponse(
                0,
                SectionCompletenessResponse.of(false, 0),
                SectionCompletenessResponse.of(false, 0),
                SectionCompletenessResponse.of(false, 0),
                SectionCompletenessResponse.of(false, 0),
                SectionCompletenessResponse.of(false, 0),
                SectionCompletenessResponse.of(false, 0),
                SectionCompletenessResponse.of(false, 0)
        );
        MasterCareerProfileResponse response = new MasterCareerProfileResponse(
                profileId, null, null, null, null, null, null, null, null, now, now,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), completeness
        );

        when(careerProfileService.getMasterCareerProfile("user@joblivo.com")).thenReturn(response);

        // Client supplies arbitrary foreign userId and careerProfileId in query string
        mockMvc.perform(get("/api/v1/career-profile")
                        .param("userId", UUID.randomUUID().toString())
                        .param("careerProfileId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId.toString()));

        // Verify service was called with authenticated principal ONLY, completely ignoring query parameters
        org.mockito.Mockito.verify(careerProfileService).getMasterCareerProfile("user@joblivo.com");
    }
}


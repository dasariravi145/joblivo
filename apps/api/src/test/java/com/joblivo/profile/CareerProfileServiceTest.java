package com.joblivo.profile;

import com.joblivo.user.User;
import com.joblivo.user.UserAuthenticationEligibilityService;
import com.joblivo.user.UserRepository;
import com.joblivo.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CareerProfileServiceTest {

    @Mock
    private CareerProfileRepository careerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CareerProfileWorkExperienceRepository workExperienceRepository;

    @Mock
    private CareerProfileSkillRepository skillRepository;

    @Mock
    private CareerProfileProjectRepository projectRepository;

    @Mock
    private CareerProfileEducationRepository educationRepository;

    @Mock
    private CareerProfileCertificationRepository certificationRepository;

    @Mock
    private CareerProfileAchievementRepository achievementRepository;

    private UserAuthenticationEligibilityService eligibilityService;
    private CareerProfileService careerProfileService;

    @BeforeEach
    void setUp() {
        eligibilityService = new UserAuthenticationEligibilityService();
        careerProfileService = new CareerProfileService(
                careerProfileRepository,
                userRepository,
                eligibilityService,
                workExperienceRepository,
                skillRepository,
                projectRepository,
                educationRepository,
                certificationRepository,
                achievementRepository
        );
    }

    private User createPersistedUser(UUID id, String email, String displayName, UserStatus status) {
        try {
            User user = new User(email, displayName, status);
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);

            Field createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, Instant.now());
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createProfile_WithValidActiveUser_SavesAndReturnsCareerProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "career@joblivo.com", "Career User", UserStatus.ACTIVE);
        CareerProfile persistedProfile = new CareerProfile(profileId, user, now, now);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(persistedProfile);

        CareerProfile result = careerProfileService.createProfile(user);

        assertNotNull(result);
        assertEquals(profileId, result.getId());
        assertEquals(user, result.getUser());
        assertEquals(now, result.getCreatedAt());
        assertEquals(now, result.getUpdatedAt());

        ArgumentCaptor<CareerProfile> captor = ArgumentCaptor.forClass(CareerProfile.class);
        verify(careerProfileRepository).saveAndFlush(captor.capture());
        CareerProfile captured = captor.getValue();
        assertEquals(user, captured.getUser());
        assertNull(captured.getId(), "Profile ID must be null prior to database generation");
    }

    @Test
    void createProfile_ByUserId_ResolvesUserAndCreatesProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "byid@joblivo.com", "ById User", UserStatus.ACTIVE);
        CareerProfile persistedProfile = new CareerProfile(profileId, user, now, now);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(persistedProfile);

        CareerProfile result = careerProfileService.createProfile(userId);

        assertNotNull(result);
        assertEquals(profileId, result.getId());
        verify(userRepository, atLeastOnce()).findById(userId);
    }

    @Test
    void createProfileForPrincipal_WithEmail_ResolvesUserAndCreatesProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "principal@joblivo.com", "Principal User", UserStatus.ACTIVE);
        CareerProfile persistedProfile = new CareerProfile(profileId, user, now, now);

        when(userRepository.findByEmailIgnoreCase("principal@joblivo.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(persistedProfile);

        CareerProfile result = careerProfileService.createProfileForPrincipal("principal@joblivo.com");

        assertNotNull(result);
        assertEquals(profileId, result.getId());
    }

    @Test
    void createProfileForPrincipal_WithUuidString_ResolvesUserAndCreatesProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "uuidprincipal@joblivo.com", "UUID User", UserStatus.ACTIVE);
        CareerProfile persistedProfile = new CareerProfile(profileId, user, now, now);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(persistedProfile);

        CareerProfile result = careerProfileService.createProfileForPrincipal(userId.toString());

        assertNotNull(result);
        assertEquals(profileId, result.getId());
    }

    @Test
    void createProfile_WhenProfileAlreadyExists_ThrowsDuplicateCareerProfileException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "existing@joblivo.com", "Existing User", UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(true);

        DuplicateCareerProfileException ex = assertThrows(DuplicateCareerProfileException.class,
                () -> careerProfileService.createProfile(user));

        assertTrue(ex.getMessage().contains(userId.toString()));
        verify(careerProfileRepository, never()).save(any());
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProfile_WhenConcurrentDatabaseConflictOccurs_TranslatesToDuplicateCareerProfileException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "race@joblivo.com", "Race User", UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_career_profiles_user_id\""));

        DuplicateCareerProfileException ex = assertThrows(DuplicateCareerProfileException.class,
                () -> careerProfileService.createProfile(user));

        assertTrue(ex.getMessage().contains(userId.toString()));
        assertNotNull(ex.getCause());
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
    }

    @Test
    void createProfile_WhenUserIsSuspended_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = createPersistedUser(userId, "suspended@joblivo.com", "Suspended User", UserStatus.SUSPENDED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        IneligibleUserException ex = assertThrows(IneligibleUserException.class,
                () -> careerProfileService.createProfile(suspendedUser));

        assertTrue(ex.getMessage().contains("SUSPENDED"));
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProfile_WhenUserIsDeleted_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User deletedUser = createPersistedUser(userId, "deleted@joblivo.com", "Deleted User", UserStatus.DELETED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(deletedUser));

        IneligibleUserException ex = assertThrows(IneligibleUserException.class,
                () -> careerProfileService.createProfile(deletedUser));

        assertTrue(ex.getMessage().contains("DELETED"));
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProfile_WhenUserDoesNotExist_ThrowsIllegalArgumentException() {
        UUID nonExistentId = UUID.randomUUID();
        User unpersistedUser = createPersistedUser(nonExistentId, "ghost@joblivo.com", "Ghost User", UserStatus.ACTIVE);

        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> careerProfileService.createProfile(unpersistedUser));

        assertTrue(ex.getMessage().contains("User not found"));
    }

    @Test
    void createProfile_WithNullInputs_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.createProfile((User) null));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.createProfile(new User("unpersisted@test.com", "No ID")));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.createProfile((UUID) null));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.createProfileForPrincipal(null));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.createProfileForPrincipal("   "));
    }

    @Test
    void findByUserAndFindByUserId_OperateCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "find@joblivo.com", "Find User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(UUID.randomUUID(), user, Instant.now(), Instant.now());

        when(careerProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        Optional<CareerProfile> byUser = careerProfileService.findByUser(user);
        assertTrue(byUser.isPresent());
        assertEquals(profile, byUser.get());

        Optional<CareerProfile> byUserId = careerProfileService.findByUserId(userId);
        assertTrue(byUserId.isPresent());
        assertEquals(profile, byUserId.get());

        assertTrue(careerProfileService.findByUser(null).isEmpty());
        assertTrue(careerProfileService.findByUserId(null).isEmpty());
    }

    @Test
    void hasProfile_OperateCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "has@joblivo.com", "Has User", UserStatus.ACTIVE);

        when(careerProfileRepository.existsByUser(user)).thenReturn(true);
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(true);

        assertTrue(careerProfileService.hasProfile(user));
        assertTrue(careerProfileService.hasProfile(userId));

        assertFalse(careerProfileService.hasProfile((User) null));
        assertFalse(careerProfileService.hasProfile((UUID) null));
    }

    @Test
    void updateProfile_WithValidDetails_UpdatesAndReturnsProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now();
        User user = createPersistedUser(userId, "update@joblivo.com", "Update User", UserStatus.ACTIVE);
        CareerProfile existingProfile = new CareerProfile(profileId, user, createdAt, createdAt);

        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Senior Backend Engineer | Java & Cloud",
                "Senior Software Engineer",
                "Acme Corp",
                72,
                "San Francisco, CA",
                "Remote, US",
                WorkMode.REMOTE,
                30
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfile updated = careerProfileService.updateProfile(user, request);

        assertNotNull(updated);
        assertEquals(profileId, updated.getId(), "Profile ID must not change during update");
        assertEquals(user, updated.getUser(), "Profile owner must not change during update");
        assertEquals("Senior Backend Engineer | Java & Cloud", updated.getProfessionalHeadline());
        assertEquals("Senior Software Engineer", updated.getCurrentTitle());
        assertEquals("Acme Corp", updated.getCurrentCompany());
        assertEquals(72, updated.getTotalExperienceMonths());
        assertEquals("San Francisco, CA", updated.getCurrentLocation());
        assertEquals("Remote, US", updated.getPreferredWorkLocation());
        assertEquals(WorkMode.REMOTE, updated.getPreferredWorkMode());
        assertEquals(30, updated.getNoticePeriodDays());

        verify(careerProfileRepository).saveAndFlush(existingProfile);
    }

    @Test
    void updateProfile_ByUserId_ResolvesUserAndUpdatesProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "byidupdate@joblivo.com", "ById User", UserStatus.ACTIVE);
        CareerProfile existingProfile = new CareerProfile(profileId, user, now, now);

        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Lead Architect",
                "Staff Engineer",
                "Tech Giant",
                120,
                "New York, NY",
                "New York, NY",
                WorkMode.HYBRID,
                60
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfile updated = careerProfileService.updateProfile(userId, request);

        assertNotNull(updated);
        assertEquals(profileId, updated.getId());
        assertEquals("Lead Architect", updated.getProfessionalHeadline());
        assertEquals(WorkMode.HYBRID, updated.getPreferredWorkMode());
    }

    @Test
    void updateProfileForPrincipal_WithEmailAndUuid_ResolvesUserAndUpdatesProfile() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "principalupdate@joblivo.com", "Principal User", UserStatus.ACTIVE);
        CareerProfile existingProfile = new CareerProfile(profileId, user, now, now);

        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Full Stack Developer",
                "Software Engineer",
                "Startup LLC",
                36,
                "Austin, TX",
                "Austin, TX",
                WorkMode.ONSITE,
                15
        );

        when(userRepository.findByEmailIgnoreCase("principalupdate@joblivo.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Update via email principal
        CareerProfile updatedByEmail = careerProfileService.updateProfileForPrincipal("principalupdate@joblivo.com", request);
        assertNotNull(updatedByEmail);
        assertEquals("Full Stack Developer", updatedByEmail.getProfessionalHeadline());

        // Update via UUID string principal
        CareerProfile updatedByUuid = careerProfileService.updateProfileForPrincipal(userId.toString(), request);
        assertNotNull(updatedByUuid);
        assertEquals("Full Stack Developer", updatedByUuid.getProfessionalHeadline());
    }

    @Test
    void updateProfile_WhenProfileDoesNotExist_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprofile@joblivo.com", "No Profile User", UserStatus.ACTIVE);
        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Headline", null, null, null, null, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> careerProfileService.updateProfile(user, request));

        assertTrue(ex.getMessage().contains("Career profile not found for user"));
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfile_WhenUserIsIneligible_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = createPersistedUser(userId, "suspended@joblivo.com", "Suspended User", UserStatus.SUSPENDED);
        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Headline", null, null, null, null, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        IneligibleUserException ex = assertThrows(IneligibleUserException.class,
                () -> careerProfileService.updateProfile(suspendedUser, request));

        assertTrue(ex.getMessage().contains("SUSPENDED"));
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfile_WithNegativeExperienceMonths_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "negexp@joblivo.com", "Neg Exp User", UserStatus.ACTIVE);
        CareerProfile existingProfile = new CareerProfile(UUID.randomUUID(), user, Instant.now(), Instant.now());
        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Headline", null, null, -5, null, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> careerProfileService.updateProfile(user, request));

        assertTrue(ex.getMessage().contains("Total experience months must not be negative"));
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfile_WithNegativeNoticePeriodDays_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "negnotice@joblivo.com", "Neg Notice User", UserStatus.ACTIVE);
        CareerProfile existingProfile = new CareerProfile(UUID.randomUUID(), user, Instant.now(), Instant.now());
        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Headline", null, null, null, null, null, null, -10
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existingProfile));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> careerProfileService.updateProfile(user, request));

        assertTrue(ex.getMessage().contains("Notice period days must not be negative"));
        verify(careerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProfile_WithNullInputs_ThrowsIllegalArgumentException() {
        UpdateCareerProfileRequest request = new UpdateCareerProfileRequest(
                "Headline", null, null, null, null, null, null, null
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.updateProfile((User) null, request));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.updateProfile(new User("unpersisted@test.com", "No ID"), request));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.updateProfile((UUID) null, request));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.updateProfile(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.updateProfileForPrincipal(null, request));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.updateProfileForPrincipal("   ", request));
    }

    @Test
    void addWorkExperience_WithValidRequest_SavesAndReturnsExperience() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "expuser@joblivo.com", "Exp User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        WorkExperienceRequest request = new WorkExperienceRequest(
                "Acme Corp",
                "Backend Engineer",
                EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2021, 6, 1),
                java.time.LocalDate.of(2023, 1, 1),
                false,
                "San Francisco, CA",
                "Engineered cloud services",
                1
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.saveAndFlush(any(CareerProfileWorkExperience.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileWorkExperience saved = careerProfileService.addWorkExperience(user, request);

        assertNotNull(saved);
        assertEquals(profile, saved.getCareerProfile());
        assertEquals("Acme Corp", saved.getCompanyName());
        assertEquals("Backend Engineer", saved.getJobTitle());
        assertEquals(EmploymentType.FULL_TIME, saved.getEmploymentType());
        assertEquals(java.time.LocalDate.of(2021, 6, 1), saved.getStartDate());
        assertEquals(java.time.LocalDate.of(2023, 1, 1), saved.getEndDate());
        assertFalse(saved.isCurrentlyWorking());
        assertEquals("San Francisco, CA", saved.getLocation());
        assertEquals("Engineered cloud services", saved.getDescription());
        assertEquals(1, saved.getDisplayOrder());

        verify(workExperienceRepository).saveAndFlush(any(CareerProfileWorkExperience.class));
    }

    @Test
    void addWorkExperience_ByPrincipal_ResolvesUserAndCreatesExperience() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "principalexp@joblivo.com", "Principal Exp", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        WorkExperienceRequest request = new WorkExperienceRequest(
                "Beta Inc",
                "Frontend Engineer",
                EmploymentType.CONTRACT,
                java.time.LocalDate.of(2023, 1, 1),
                null,
                true,
                "Remote",
                "React development",
                0
        );

        when(userRepository.findByEmailIgnoreCase("principalexp@joblivo.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.saveAndFlush(any(CareerProfileWorkExperience.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileWorkExperience saved = careerProfileService.addWorkExperience("principalexp@joblivo.com", request);

        assertNotNull(saved);
        assertEquals("Beta Inc", saved.getCompanyName());
        assertTrue(saved.isCurrentlyWorking());
        assertNull(saved.getEndDate());
    }

    @Test
    void addWorkExperience_WhenUserSuspended_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = createPersistedUser(userId, "suspendedexp@joblivo.com", "Suspended User", UserStatus.SUSPENDED);
        WorkExperienceRequest request = new WorkExperienceRequest(
                "Acme Corp", "Engineer", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2022, 1, 1), null, true, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        assertThrows(IneligibleUserException.class, () -> careerProfileService.addWorkExperience(suspendedUser, request));
        verify(workExperienceRepository, never()).saveAndFlush(any());
    }

    @Test
    void addWorkExperience_WhenDateRangeInvalid_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "invaliddates@joblivo.com", "Invalid Dates", UserStatus.ACTIVE);
        WorkExperienceRequest request = new WorkExperienceRequest(
                "Acme Corp", "Engineer", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2023, 1, 1),
                java.time.LocalDate.of(2022, 1, 1),
                false, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> careerProfileService.addWorkExperience(user, request));
        assertTrue(ex.getMessage().contains("End date cannot be before start date"));
    }

    @Test
    void addWorkExperience_WhenCurrentlyWorkingWithEndDate_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "invalidcurrent@joblivo.com", "Invalid Current", UserStatus.ACTIVE);
        WorkExperienceRequest request = new WorkExperienceRequest(
                "Acme Corp", "Engineer", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2022, 1, 1),
                java.time.LocalDate.of(2023, 1, 1),
                true, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> careerProfileService.addWorkExperience(user, request));
        assertTrue(ex.getMessage().contains("End date must be null when currently working is true"));
    }

    @Test
    void getWorkExperiences_ReturnsOrderedList() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getexp@joblivo.com", "Get Exp", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileWorkExperience exp1 = new CareerProfileWorkExperience(
                UUID.randomUUID(), profile, "Company 1", "Title 1", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2020, 1, 1), null, true, null, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileWorkExperience exp2 = new CareerProfileWorkExperience(
                UUID.randomUUID(), profile, "Company 2", "Title 2", EmploymentType.PART_TIME,
                java.time.LocalDate.of(2018, 1, 1), java.time.LocalDate.of(2019, 12, 31), false, null, null, 1, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId))
                .thenReturn(List.of(exp1, exp2));

        List<CareerProfileWorkExperience> results = careerProfileService.getWorkExperiences(user);

        assertEquals(2, results.size());
        assertEquals("Company 1", results.get(0).getCompanyName());
        assertEquals("Company 2", results.get(1).getCompanyName());
    }

    @Test
    void getWorkExperiences_WhenProfileDoesNotExist_ReturnsEmptyList() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprofile@joblivo.com", "No Profile", UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        List<CareerProfileWorkExperience> results = careerProfileService.getWorkExperiences(user);

        assertTrue(results.isEmpty());
        verify(workExperienceRepository, never()).findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(any());
    }

    @Test
    void updateWorkExperience_WithValidRequest_UpdatesAndReturnsExperience() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updateexp@joblivo.com", "Update Exp", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        CareerProfileWorkExperience existing = new CareerProfileWorkExperience(
                expId, profile, "Old Company", "Old Title", EmploymentType.CONTRACT,
                java.time.LocalDate.of(2020, 1, 1), null, true, "Old Loc", "Old Desc", 2, Instant.now(), Instant.now()
        );

        WorkExperienceRequest updateRequest = new WorkExperienceRequest(
                "New Company",
                "New Title",
                EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2020, 1, 1),
                java.time.LocalDate.of(2022, 1, 1),
                false,
                "New Loc",
                "New Desc",
                0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByIdAndCareerProfileId(expId, profileId)).thenReturn(Optional.of(existing));
        when(workExperienceRepository.saveAndFlush(any(CareerProfileWorkExperience.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileWorkExperience updated = careerProfileService.updateWorkExperience(user, expId, updateRequest);

        assertNotNull(updated);
        assertEquals(expId, updated.getId(), "Experience ID must not change");
        assertEquals(profile, updated.getCareerProfile(), "Owning profile must not change");
        assertEquals("New Company", updated.getCompanyName());
        assertEquals("New Title", updated.getJobTitle());
        assertEquals(EmploymentType.FULL_TIME, updated.getEmploymentType());
        assertFalse(updated.isCurrentlyWorking());
        assertEquals(java.time.LocalDate.of(2022, 1, 1), updated.getEndDate());
        assertEquals(0, updated.getDisplayOrder());
    }

    @Test
    void updateWorkExperience_WhenBelongsToAnotherUserProfile_ThrowsWorkExperienceNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignExpId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        WorkExperienceRequest request = new WorkExperienceRequest(
                "Malicious Corp", "Hacker", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2021, 1, 1), null, true, null, null, 0
        );

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        // Querying for foreignExpId under User A's profile ID yields empty
        when(workExperienceRepository.findByIdAndCareerProfileId(foreignExpId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(WorkExperienceNotFoundException.class,
                () -> careerProfileService.updateWorkExperience(userA, foreignExpId, request));
        verify(workExperienceRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteWorkExperience_DeletesSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();
        User user = createPersistedUser(userId, "deleteexp@joblivo.com", "Delete Exp", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        CareerProfileWorkExperience existing = new CareerProfileWorkExperience(
                expId, profile, "Company", "Title", EmploymentType.FULL_TIME,
                java.time.LocalDate.of(2020, 1, 1), null, true, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByIdAndCareerProfileId(expId, profileId)).thenReturn(Optional.of(existing));

        careerProfileService.deleteWorkExperience(user, expId);

        verify(workExperienceRepository).delete(existing);
        verify(workExperienceRepository).flush();
    }

    @Test
    void deleteWorkExperience_WhenBelongsToAnotherUserProfile_ThrowsWorkExperienceNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignExpId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(workExperienceRepository.findByIdAndCareerProfileId(foreignExpId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(WorkExperienceNotFoundException.class,
                () -> careerProfileService.deleteWorkExperience(userA, foreignExpId));
        verify(workExperienceRepository, never()).delete(any());
    }

    @Test
    void classAndMethodsAreProperlyTransactional() throws NoSuchMethodException {
        Transactional classTransactional = CareerProfileService.class.getAnnotation(Transactional.class);
        assertNotNull(classTransactional, "Service class must be annotated with @Transactional");
        assertTrue(classTransactional.readOnly(), "Class-level transaction must default to readOnly=true");

        Transactional createMethodTransactional = CareerProfileService.class
                .getMethod("createProfile", User.class)
                .getAnnotation(Transactional.class);
        assertNotNull(createMethodTransactional);
        assertFalse(createMethodTransactional.readOnly());

        Transactional updateMethodTransactional = CareerProfileService.class
                .getMethod("updateProfile", User.class, UpdateCareerProfileRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(updateMethodTransactional);
        assertFalse(updateMethodTransactional.readOnly());

        Transactional addExpTransactional = CareerProfileService.class
                .getMethod("addWorkExperience", User.class, WorkExperienceRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(addExpTransactional);
        assertFalse(addExpTransactional.readOnly());

        Transactional updateExpTransactional = CareerProfileService.class
                .getMethod("updateWorkExperience", User.class, UUID.class, WorkExperienceRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(updateExpTransactional);
        assertFalse(updateExpTransactional.readOnly());

        Transactional deleteExpTransactional = CareerProfileService.class
                .getMethod("deleteWorkExperience", User.class, UUID.class)
                .getAnnotation(Transactional.class);
        assertNotNull(deleteExpTransactional);
        assertFalse(deleteExpTransactional.readOnly());

        Transactional addSkillTransactional = CareerProfileService.class
                .getMethod("addSkill", User.class, SkillRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(addSkillTransactional);
        assertFalse(addSkillTransactional.readOnly());

        Transactional updateSkillTransactional = CareerProfileService.class
                .getMethod("updateSkill", User.class, UUID.class, SkillRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(updateSkillTransactional);
        assertFalse(updateSkillTransactional.readOnly());

        Transactional deleteSkillTransactional = CareerProfileService.class
                .getMethod("deleteSkill", User.class, UUID.class)
                .getAnnotation(Transactional.class);
        assertNotNull(deleteSkillTransactional);
        assertFalse(deleteSkillTransactional.readOnly());
    }

    @Test
    void addSkill_WithValidData_SavesAndReturnsSkill() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "skill@joblivo.com", "Skill User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, now, now);

        SkillRequest request = new SkillRequest(
                "PostgreSQL",
                SkillCategory.DATABASE,
                SkillProficiency.ADVANCED,
                new BigDecimal("5.0"),
                LocalDate.of(2026, 6, 1),
                1
        );

        CareerProfileSkill persistedSkill = new CareerProfileSkill(
                skillId, profile, "PostgreSQL", SkillCategory.DATABASE,
                SkillProficiency.ADVANCED, new BigDecimal("5.0"), LocalDate.of(2026, 6, 1), 1, now, now
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByCareerProfileIdAndNormalizedName(profileId, "PostgreSQL")).thenReturn(false);
        when(skillRepository.saveAndFlush(any(CareerProfileSkill.class))).thenReturn(persistedSkill);

        CareerProfileSkill result = careerProfileService.addSkill(user, request);

        assertNotNull(result);
        assertEquals(skillId, result.getId());
        assertEquals("PostgreSQL", result.getName());
        assertEquals(SkillCategory.DATABASE, result.getCategory());
        assertEquals(SkillProficiency.ADVANCED, result.getProficiency());
        assertEquals(new BigDecimal("5.0"), result.getYearsOfExperience());
        assertEquals(LocalDate.of(2026, 6, 1), result.getLastUsedDate());
        assertEquals(1, result.getDisplayOrder());

        ArgumentCaptor<CareerProfileSkill> captor = ArgumentCaptor.forClass(CareerProfileSkill.class);
        verify(skillRepository).saveAndFlush(captor.capture());
        CareerProfileSkill captured = captor.getValue();
        assertEquals(profile, captured.getCareerProfile());
        assertEquals("PostgreSQL", captured.getName());
        assertEquals(SkillCategory.DATABASE, captured.getCategory());
    }

    @Test
    void addSkill_WhenSkillAlreadyExistsInProfile_ThrowsDuplicateSkillException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "duplicate@joblivo.com", "Duplicate User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        SkillRequest request = new SkillRequest("AWS", SkillCategory.CLOUD, SkillProficiency.ADVANCED, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByCareerProfileIdAndNormalizedName(profileId, "AWS")).thenReturn(true);

        assertThrows(DuplicateSkillException.class, () -> careerProfileService.addSkill(user, request));
        verify(skillRepository, never()).saveAndFlush(any());
    }

    @Test
    void addSkill_WhenConcurrentRaceCondition_ThrowsDuplicateSkillException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "race@joblivo.com", "Race User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        SkillRequest request = new SkillRequest("Kubernetes", SkillCategory.DEVOPS, SkillProficiency.ADVANCED, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByCareerProfileIdAndNormalizedName(profileId, "Kubernetes")).thenReturn(false);
        when(skillRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_career_profile_skills_profile_name_lower"));

        assertThrows(DuplicateSkillException.class, () -> careerProfileService.addSkill(user, request));
    }

    @Test
    void addSkill_WithZeroYearsOfExperience_Succeeds() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "zero@joblivo.com", "Zero User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        SkillRequest request = new SkillRequest("Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.BEGINNER, BigDecimal.ZERO, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByCareerProfileIdAndNormalizedName(profileId, "Java")).thenReturn(false);
        when(skillRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileSkill saved = careerProfileService.addSkill(user, request);
        assertNotNull(saved);
        assertEquals(BigDecimal.ZERO, saved.getYearsOfExperience());
    }

    @Test
    void addSkill_WithNegativeYearsOfExperience_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "neg@joblivo.com", "Neg User", UserStatus.ACTIVE);

        SkillRequest request = new SkillRequest("Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.BEGINNER, new BigDecimal("-1.0"), null, 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addSkill(user, request));
    }

    @Test
    void addSkill_WithBlankName_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "blank@joblivo.com", "Blank User", UserStatus.ACTIVE);

        SkillRequest request = new SkillRequest("   ", SkillCategory.CLOUD, SkillProficiency.BEGINNER, null, null, 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addSkill(user, request));
    }

    @Test
    void addSkill_WithNullCategory_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "nocat@joblivo.com", "NoCat User", UserStatus.ACTIVE);

        SkillRequest request = new SkillRequest("Java", null, SkillProficiency.BEGINNER, null, null, 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addSkill(user, request));
    }

    @Test
    void addSkill_WithNullProficiency_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprof@joblivo.com", "NoProf User", UserStatus.ACTIVE);

        SkillRequest request = new SkillRequest("Java", SkillCategory.PROGRAMMING_LANGUAGE, null, null, null, 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addSkill(user, request));
    }

    @Test
    void addSkill_ByPrincipal_ResolvesUserAndAddsSkill() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princ@joblivo.com", "Princ User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        SkillRequest request = new SkillRequest("Docker", SkillCategory.DEVOPS, SkillProficiency.INTERMEDIATE, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByCareerProfileIdAndNormalizedName(profileId, "Docker")).thenReturn(false);
        when(skillRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileSkill result = careerProfileService.addSkill(userId.toString(), request);
        assertNotNull(result);
        assertEquals("Docker", result.getName());
    }

    @Test
    void getSkills_ReturnsOrderedSkills() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getskills@joblivo.com", "GetSkills User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill skill1 = new CareerProfileSkill(UUID.randomUUID(), profile, "AWS", SkillCategory.CLOUD, SkillProficiency.EXPERT, null, null, 0, Instant.now(), Instant.now());
        CareerProfileSkill skill2 = new CareerProfileSkill(UUID.randomUUID(), profile, "Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED, null, null, 1, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileId)).thenReturn(List.of(skill1, skill2));

        List<CareerProfileSkill> result = careerProfileService.getSkills(user);

        assertEquals(2, result.size());
        assertEquals("AWS", result.get(0).getName());
        assertEquals("Java", result.get(1).getName());
    }

    @Test
    void getSkills_WhenNoProfileExists_ReturnsEmptyList() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprof@joblivo.com", "NoProf User", UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        List<CareerProfileSkill> result = careerProfileService.getSkills(user);
        assertTrue(result.isEmpty());
        verify(skillRepository, never()).findByCareerProfileIdOrderByDisplayOrderAscNameAsc(any());
    }

    @Test
    void getSkills_ByPrincipal_ResolvesUserAndReturnsSkills() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princget@joblivo.com", "PrincGet User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findByEmailIgnoreCase("princget@joblivo.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileId)).thenReturn(List.of());

        List<CareerProfileSkill> result = careerProfileService.getSkills("princget@joblivo.com");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateSkill_WithValidData_UpdatesAndReturnsSkill() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updateskill@joblivo.com", "UpdateSkill User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill existingSkill = new CareerProfileSkill(
                skillId, profile, "Python", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.BEGINNER,
                new BigDecimal("1.0"), null, 0, Instant.now(), Instant.now()
        );

        SkillRequest updateRequest = new SkillRequest(
                "Python 3", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED,
                new BigDecimal("3.0"), LocalDate.of(2026, 9, 1), 2
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(skillId, profileId)).thenReturn(Optional.of(existingSkill));
        when(skillRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(profileId, "Python 3", skillId)).thenReturn(false);
        when(skillRepository.saveAndFlush(any(CareerProfileSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileSkill updated = careerProfileService.updateSkill(user, skillId, updateRequest);

        assertEquals("Python 3", updated.getName());
        assertEquals(SkillProficiency.ADVANCED, updated.getProficiency());
        assertEquals(new BigDecimal("3.0"), updated.getYearsOfExperience());
        assertEquals(LocalDate.of(2026, 9, 1), updated.getLastUsedDate());
        assertEquals(2, updated.getDisplayOrder());
    }

    @Test
    void updateSkill_WhenDuplicateSkillNameExists_ThrowsDuplicateSkillException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupupdate@joblivo.com", "DupUpdate User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill existingSkill = new CareerProfileSkill(
                skillId, profile, "Java", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.BEGINNER,
                null, null, 0, Instant.now(), Instant.now()
        );

        SkillRequest updateRequest = new SkillRequest("AWS", SkillCategory.CLOUD, SkillProficiency.ADVANCED, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(skillId, profileId)).thenReturn(Optional.of(existingSkill));
        when(skillRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(profileId, "AWS", skillId)).thenReturn(true);

        assertThrows(DuplicateSkillException.class, () -> careerProfileService.updateSkill(user, skillId, updateRequest));
        verify(skillRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSkill_WhenSkillNotFound_ThrowsSkillNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID nonExistentSkillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "notfound@joblivo.com", "NotFound User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        SkillRequest request = new SkillRequest("Go", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.BEGINNER, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(nonExistentSkillId, profileId)).thenReturn(Optional.empty());

        assertThrows(SkillNotFoundException.class, () -> careerProfileService.updateSkill(user, nonExistentSkillId, request));
    }

    @Test
    void updateSkill_WhenSkillBelongsToAnotherUserProfile_ThrowsSkillNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignSkillId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        SkillRequest request = new SkillRequest("Rust", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED, null, null, 0);

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(skillRepository.findByIdAndCareerProfileId(foreignSkillId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(SkillNotFoundException.class, () -> careerProfileService.updateSkill(userA, foreignSkillId, request));
    }

    @Test
    void updateSkill_ByPrincipal_ResolvesUserAndUpdatesSkill() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princupdate@joblivo.com", "PrincUpdate User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill existingSkill = new CareerProfileSkill(
                skillId, profile, "C++", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.BEGINNER,
                null, null, 0, Instant.now(), Instant.now()
        );

        SkillRequest request = new SkillRequest("C++20", SkillCategory.PROGRAMMING_LANGUAGE, SkillProficiency.ADVANCED, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(skillId, profileId)).thenReturn(Optional.of(existingSkill));
        when(skillRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(profileId, "C++20", skillId)).thenReturn(false);
        when(skillRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileSkill updated = careerProfileService.updateSkill(userId.toString(), skillId, request);
        assertEquals("C++20", updated.getName());
    }

    @Test
    void deleteSkill_WhenFound_DeletesAndFlushes() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delete@joblivo.com", "Delete User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill existingSkill = new CareerProfileSkill(
                skillId, profile, "Jenkins", SkillCategory.DEVOPS, SkillProficiency.INTERMEDIATE,
                null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(skillId, profileId)).thenReturn(Optional.of(existingSkill));

        careerProfileService.deleteSkill(user, skillId);

        verify(skillRepository).delete(existingSkill);
        verify(skillRepository).flush();
    }

    @Test
    void deleteSkill_WhenNotFound_ThrowsSkillNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID nonExistentSkillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delnotfound@joblivo.com", "DelNotFound User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(nonExistentSkillId, profileId)).thenReturn(Optional.empty());

        assertThrows(SkillNotFoundException.class, () -> careerProfileService.deleteSkill(user, nonExistentSkillId));
        verify(skillRepository, never()).delete(any());
    }

    @Test
    void deleteSkill_WhenBelongsToAnotherUserProfile_ThrowsSkillNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignSkillId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(skillRepository.findByIdAndCareerProfileId(foreignSkillId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(SkillNotFoundException.class, () -> careerProfileService.deleteSkill(userA, foreignSkillId));
        verify(skillRepository, never()).delete(any());
    }

    @Test
    void deleteSkill_ByPrincipal_ResolvesUserAndDeletesSkill() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princdel@joblivo.com", "PrincDel User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileSkill existingSkill = new CareerProfileSkill(
                skillId, profile, "Linux", SkillCategory.TOOL, SkillProficiency.EXPERT,
                null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(skillRepository.findByIdAndCareerProfileId(skillId, profileId)).thenReturn(Optional.of(existingSkill));

        careerProfileService.deleteSkill(userId.toString(), skillId);

        verify(skillRepository).delete(existingSkill);
        verify(skillRepository).flush();
    }

    // ==========================================
    // Project Service Tests
    // ==========================================

    @Test
    void addProject_WithValidData_CreatesAndReturnsProject() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "projectuser@joblivo.com", "Project User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.existsByCareerProfileIdAndNormalizedName(eq(profileId), anyString())).thenReturn(false);

        ProjectRequest request = new ProjectRequest(
                "  Joblivo Backend  ",
                ProjectType.PROFESSIONAL,
                "  Lead Architect  ",
                "  Core SaaS platform development.  ",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                false,
                "  https://github.com/joblivo/backend  ",
                1
        );

        when(projectRepository.saveAndFlush(any(CareerProfileProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileProject response = careerProfileService.addProject(user, request);

        assertNotNull(response);
        assertEquals("Joblivo Backend", response.getProjectName());
        assertEquals(ProjectType.PROFESSIONAL, response.getProjectType());
        assertEquals("Lead Architect", response.getRole());
        assertEquals("Core SaaS platform development.", response.getDescription());
        assertEquals(LocalDate.of(2024, 1, 1), response.getStartDate());
        assertEquals(LocalDate.of(2024, 12, 31), response.getEndDate());
        assertFalse(response.isCurrentlyActive());
        assertEquals("https://github.com/joblivo/backend", response.getProjectUrl());
        assertEquals(1, response.getDisplayOrder());

        ArgumentCaptor<CareerProfileProject> captor = ArgumentCaptor.forClass(CareerProfileProject.class);
        verify(projectRepository).saveAndFlush(captor.capture());
        CareerProfileProject saved = captor.getValue();
        assertEquals("Joblivo Backend", saved.getProjectName());
        assertEquals("Lead Architect", saved.getRole());
        assertEquals("Core SaaS platform development.", saved.getDescription());
        assertEquals("https://github.com/joblivo/backend", saved.getProjectUrl());
    }

    @Test
    void addProject_WithNullOrBlankName_ThrowsIllegalArgumentException() {
        User user = createPersistedUser(UUID.randomUUID(), "name@joblivo.com", "User", UserStatus.ACTIVE);

        ProjectRequest nullName = new ProjectRequest(null, ProjectType.PERSONAL, null, null, null, null, false, null, 0);
        ProjectRequest blankName = new ProjectRequest("   ", ProjectType.PERSONAL, null, null, null, null, false, null, 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, nullName));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, blankName));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void addProject_WithNullProjectType_ThrowsIllegalArgumentException() {
        User user = createPersistedUser(UUID.randomUUID(), "type@joblivo.com", "User", UserStatus.ACTIVE);

        ProjectRequest nullType = new ProjectRequest("Project Alpha", null, null, null, null, null, false, null, 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, nullType));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void addProject_WithInvalidDateRange_ThrowsIllegalArgumentException() {
        User user = createPersistedUser(UUID.randomUUID(), "dates@joblivo.com", "User", UserStatus.ACTIVE);

        ProjectRequest invalidDates = new ProjectRequest(
                "Project Beta",
                ProjectType.PERSONAL,
                null,
                null,
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 1, 1),
                false,
                null,
                0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, invalidDates));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void addProject_WithCurrentlyActiveTrueAndEndDatePopulated_ThrowsIllegalArgumentException() {
        User user = createPersistedUser(UUID.randomUUID(), "active@joblivo.com", "User", UserStatus.ACTIVE);

        ProjectRequest conflictingActive = new ProjectRequest(
                "Project Gamma",
                ProjectType.OPEN_SOURCE,
                null,
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                true,
                null,
                0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, conflictingActive));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void addProject_WithInvalidUrl_ThrowsIllegalArgumentException() {
        User user = createPersistedUser(UUID.randomUUID(), "url@joblivo.com", "User", UserStatus.ACTIVE);

        ProjectRequest ftpUrl = new ProjectRequest("Project Delta", ProjectType.OTHER, null, null, null, null, false, "ftp://example.com", 0);
        ProjectRequest malformedUrl = new ProjectRequest("Project Delta", ProjectType.OTHER, null, null, null, null, false, "not-a-url", 0);

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, ftpUrl));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addProject(user, malformedUrl));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void addProject_WithDuplicateName_ThrowsDuplicateProjectException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dup@joblivo.com", "Dup User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.existsByCareerProfileIdAndNormalizedName(eq(profileId), anyString())).thenReturn(true);

        ProjectRequest duplicateRequest = new ProjectRequest(
                "Joblivo", ProjectType.PROFESSIONAL, null, null, null, null, false, null, 0
        );

        assertThrows(DuplicateProjectException.class, () -> careerProfileService.addProject(user, duplicateRequest));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void addProject_WithDataIntegrityViolation_ThrowsDuplicateProjectException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "race@joblivo.com", "Race User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.existsByCareerProfileIdAndNormalizedName(eq(profileId), anyString())).thenReturn(false);
        when(projectRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ProjectRequest request = new ProjectRequest("Race Project", ProjectType.PERSONAL, null, null, null, null, false, null, 0);

        assertThrows(DuplicateProjectException.class, () -> careerProfileService.addProject(user, request));
    }

    @Test
    void getProjects_ReturnsSortedProjects() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getall@joblivo.com", "GetAll User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileProject p1 = new CareerProfileProject(
                UUID.randomUUID(), profile, "Project 1", ProjectType.PROFESSIONAL, "Dev", "Desc 1",
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), false, "https://p1.com", 0, Instant.now(), Instant.now()
        );
        CareerProfileProject p2 = new CareerProfileProject(
                UUID.randomUUID(), profile, "Project 2", ProjectType.PERSONAL, "Lead", "Desc 2",
                LocalDate.of(2024, 1, 1), null, true, null, 1, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of(p1, p2));

        List<CareerProfileProject> results = careerProfileService.getProjects(user);

        assertEquals(2, results.size());
        assertEquals("Project 1", results.get(0).getProjectName());
        assertEquals("Project 2", results.get(1).getProjectName());
    }

    @Test
    void getProject_WhenFound_ReturnsProject() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "single@joblivo.com", "Single User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileProject project = new CareerProfileProject(
                projectId, profile, "Single Project", ProjectType.ACADEMIC, "Researcher", "Thesis",
                LocalDate.of(2022, 9, 1), LocalDate.of(2023, 5, 31), false, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.of(project));

        CareerProfileProject response = careerProfileService.getProject(user, projectId);

        assertNotNull(response);
        assertEquals(projectId, response.getId());
        assertEquals("Single Project", response.getProjectName());
        assertEquals(ProjectType.ACADEMIC, response.getProjectType());
    }

    @Test
    void getProject_WhenNotFound_ThrowsProjectNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "notfound@joblivo.com", "NotFound User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> careerProfileService.getProject(user, projectId));
    }

    @Test
    void getProject_WhenBelongsToAnotherUserProfile_ThrowsProjectNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignProjectId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_p@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(projectRepository.findByIdAndCareerProfileId(foreignProjectId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> careerProfileService.getProject(userA, foreignProjectId));
    }

    @Test
    void updateProject_WithValidData_UpdatesAndReturnsProject() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "update@joblivo.com", "Update User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileProject existingProject = new CareerProfileProject(
                projectId, profile, "Old Name", ProjectType.PERSONAL, "Old Role", "Old Desc",
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 1), false, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(eq(profileId), anyString(), eq(projectId))).thenReturn(false);
        when(projectRepository.saveAndFlush(any(CareerProfileProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectRequest updateRequest = new ProjectRequest(
                "  Updated Name  ",
                ProjectType.FREELANCE,
                "  Consultant  ",
                "  Updated Description  ",
                LocalDate.of(2023, 1, 1),
                null,
                true,
                "  https://client.com  ",
                5
        );

        CareerProfileProject response = careerProfileService.updateProject(user, projectId, updateRequest);

        assertNotNull(response);
        assertEquals("Updated Name", response.getProjectName());
        assertEquals(ProjectType.FREELANCE, response.getProjectType());
        assertEquals("Consultant", response.getRole());
        assertEquals("Updated Description", response.getDescription());
        assertTrue(response.isCurrentlyActive());
        assertNull(response.getEndDate());
        assertEquals("https://client.com", response.getProjectUrl());
        assertEquals(5, response.getDisplayOrder());

        verify(projectRepository).saveAndFlush(existingProject);
    }

    @Test
    void updateProject_WithConflictingDuplicateName_ThrowsDuplicateProjectException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupup@joblivo.com", "DupUp User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileProject existingProject = new CareerProfileProject(
                projectId, profile, "My Project", ProjectType.PERSONAL, null, null,
                null, null, false, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(eq(profileId), anyString(), eq(projectId))).thenReturn(true);

        ProjectRequest updateRequest = new ProjectRequest(
                "Other Existing Project", ProjectType.PERSONAL, null, null, null, null, false, null, 0
        );

        assertThrows(DuplicateProjectException.class, () -> careerProfileService.updateProject(user, projectId, updateRequest));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateProject_WhenProjectNotFound_ThrowsProjectNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "upnotfound@joblivo.com", "UpNotFound User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.empty());

        ProjectRequest request = new ProjectRequest("Name", ProjectType.PERSONAL, null, null, null, null, false, null, 0);

        assertThrows(ProjectNotFoundException.class, () -> careerProfileService.updateProject(user, projectId, request));
        verify(projectRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteProject_DeletesAndFlushes() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "deletep@joblivo.com", "DeleteP User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileProject existingProject = new CareerProfileProject(
                projectId, profile, "To Delete", ProjectType.OTHER, null, null,
                null, null, false, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.of(existingProject));

        careerProfileService.deleteProject(user, projectId);

        verify(projectRepository).delete(existingProject);
        verify(projectRepository).flush();
    }

    @Test
    void deleteProject_WhenProjectNotFound_ThrowsProjectNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delnotfoundp@joblivo.com", "DelNotFoundP User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> careerProfileService.deleteProject(user, projectId));
        verify(projectRepository, never()).delete(any());
    }

    @Test
    void deleteProject_WhenBelongsToAnotherUserProfile_ThrowsProjectNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignProjectId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_delp@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(projectRepository.findByIdAndCareerProfileId(foreignProjectId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> careerProfileService.deleteProject(userA, foreignProjectId));
        verify(projectRepository, never()).delete(any());
    }

    @Test
    void projectService_PrincipalMethods_DelegateCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princp@joblivo.com", "PrincP User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileProject project = new CareerProfileProject(
                projectId, profile, "Princ Project", ProjectType.PROFESSIONAL, null, null,
                null, null, false, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of(project));
        when(projectRepository.findByIdAndCareerProfileId(projectId, profileId)).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(any(CareerProfileProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Test getProjects by principal
        List<CareerProfileProject> list = careerProfileService.getProjects(userId.toString());
        assertEquals(1, list.size());

        // Test getProject by principal
        CareerProfileProject single = careerProfileService.getProject(userId.toString(), projectId);
        assertEquals("Princ Project", single.getProjectName());

        // Test updateProject by principal
        ProjectRequest upReq = new ProjectRequest("Princ Project", ProjectType.PROFESSIONAL, "New Role", null, null, null, false, null, 1);
        CareerProfileProject updated = careerProfileService.updateProject(userId.toString(), projectId, upReq);
        assertEquals("New Role", updated.getRole());

        // Test deleteProject by principal
        careerProfileService.deleteProject(userId.toString(), projectId);
        verify(projectRepository).delete(project);
    }

    // =========================================================================
    // Master Career Profile — Education Foundation Tests
    // =========================================================================

    @Test
    void addEducation_WithValidRequest_SavesAndReturnsEducation() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "edu@joblivo.com", "Edu User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        EducationRequest request = new EducationRequest(
                "Stanford University", "B.S.", "Computer Science", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1), LocalDate.of(2022, 6, 15), false,
                "3.9 GPA", "Stanford, CA", "Dean's List", 1
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.existsByCareerProfileIdAndNormalizedComposite(
                eq(profileId), eq("Stanford University"), eq("B.S."), eq("Computer Science"))).thenReturn(false);

        when(educationRepository.saveAndFlush(any(CareerProfileEducation.class))).thenAnswer(invocation -> {
            CareerProfileEducation saved = invocation.getArgument(0);
            try {
                Field idField = CareerProfileEducation.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(saved, educationId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return saved;
        });

        CareerProfileEducation result = careerProfileService.addEducation(user, request);

        assertNotNull(result);
        assertEquals(educationId, result.getId());
        assertEquals("Stanford University", result.getInstitutionName());
        assertEquals("B.S.", result.getDegree());
        assertEquals("Computer Science", result.getFieldOfStudy());
        assertEquals(EducationLevel.UNDERGRADUATE, result.getEducationLevel());
        assertEquals(LocalDate.of(2018, 9, 1), result.getStartDate());
        assertEquals(LocalDate.of(2022, 6, 15), result.getEndDate());
        assertFalse(result.isCurrentlyStudying());
        assertEquals("3.9 GPA", result.getGrade());
        assertEquals("Stanford, CA", result.getLocation());
        assertEquals("Dean's List", result.getDescription());
        assertEquals(1, result.getDisplayOrder());

        verify(educationRepository).saveAndFlush(any(CareerProfileEducation.class));
    }

    @Test
    void addEducation_WhenProfileDoesNotExist_AutoCreatesProfileAndAddsEducation() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "autoedu@joblivo.com", "Auto Edu", UserStatus.ACTIVE);
        CareerProfile newProfile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        EducationRequest request = new EducationRequest(
                "MIT", "M.Eng.", "EECS", EducationLevel.POSTGRADUATE,
                null, null, false, null, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(newProfile);
        when(educationRepository.existsByCareerProfileIdAndNormalizedComposite(
                eq(profileId), eq("MIT"), eq("M.Eng."), eq("EECS"))).thenReturn(false);
        when(educationRepository.saveAndFlush(any(CareerProfileEducation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileEducation result = careerProfileService.addEducation(user, request);

        assertNotNull(result);
        assertEquals("MIT", result.getInstitutionName());
        assertEquals("M.Eng.", result.getDegree());
        verify(careerProfileRepository).saveAndFlush(any(CareerProfile.class));
        verify(educationRepository).saveAndFlush(any(CareerProfileEducation.class));
    }

    @Test
    void addEducation_WhenNullUser_ThrowsIllegalArgumentException() {
        EducationRequest request = new EducationRequest(
                "Harvard", null, null, EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0
        );
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addEducation((User) null, request));
    }

    @Test
    void addEducation_WhenUserStatusNotEligible_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "suspendededu@joblivo.com", "Suspended Edu", UserStatus.SUSPENDED);
        EducationRequest request = new EducationRequest(
                "Harvard", null, null, EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(IneligibleUserException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenBlankInstitutionName_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "blankinst@joblivo.com", "Blank Inst", UserStatus.ACTIVE);
        EducationRequest request = new EducationRequest(
                "   ", null, null, EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenNullEducationLevel_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "nulledu@joblivo.com", "Null Edu", UserStatus.ACTIVE);
        EducationRequest request = new EducationRequest(
                "College", null, null, null, null, null, false, null, null, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenEndDateBeforeStartDate_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "baddate@joblivo.com", "Bad Date", UserStatus.ACTIVE);
        EducationRequest request = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2024, 6, 1), LocalDate.of(2022, 6, 1), false, null, null, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenCurrentlyStudyingTrueAndEndDateNotNull_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "currstudy@joblivo.com", "Curr Study", UserStatus.ACTIVE);
        EducationRequest request = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2022, 6, 1), LocalDate.of(2024, 6, 1), true, null, null, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenNegativeDisplayOrder_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "negorder@joblivo.com", "Neg Order", UserStatus.ACTIVE);
        EducationRequest request = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, -1
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenDuplicateExists_ThrowsDuplicateEducationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupedu@joblivo.com", "Dup Edu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        EducationRequest request = new EducationRequest(
                "Stanford University", "B.S.", "CS", EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.existsByCareerProfileIdAndNormalizedComposite(
                profileId, "Stanford University", "B.S.", "CS")).thenReturn(true);

        assertThrows(DuplicateEducationException.class, () -> careerProfileService.addEducation(user, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addEducation_WhenDataIntegrityViolationOccurs_ThrowsDuplicateEducationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "divedu@joblivo.com", "DIV Edu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        EducationRequest request = new EducationRequest(
                "Stanford University", "B.S.", "CS", EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.existsByCareerProfileIdAndNormalizedComposite(
                profileId, "Stanford University", "B.S.", "CS")).thenReturn(false);
        when(educationRepository.saveAndFlush(any(CareerProfileEducation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(DuplicateEducationException.class, () -> careerProfileService.addEducation(user, request));
    }

    @Test
    void getEducation_WithValidUser_ReturnsOrderedList() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getedu@joblivo.com", "Get Edu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileEducation edu1 = new CareerProfileEducation(
                profile, "Harvard", "A.B.", "Economics", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1), LocalDate.of(2022, 5, 1), false, "3.8", "Cambridge", "Honors", 0
        );
        CareerProfileEducation edu2 = new CareerProfileEducation(
                profile, "Oxford", "M.Sc.", "Finance", EducationLevel.POSTGRADUATE,
                LocalDate.of(2022, 10, 1), LocalDate.of(2023, 9, 30), false, "Distinction", "Oxford", "Graduated", 1
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId))
                .thenReturn(List.of(edu1, edu2));

        List<CareerProfileEducation> list = careerProfileService.getEducation(user);

        assertEquals(2, list.size());
        assertEquals("Harvard", list.get(0).getInstitutionName());
        assertEquals("Oxford", list.get(1).getInstitutionName());
    }

    @Test
    void getEducation_WhenUserNull_ReturnsEmptyList() {
        List<CareerProfileEducation> list = careerProfileService.getEducation((User) null);
        assertTrue(list.isEmpty());
    }

    @Test
    void getEducation_WhenProfileNotFound_ReturnsEmptyList() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprofileedu@joblivo.com", "No Profile", UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        List<CareerProfileEducation> list = careerProfileService.getEducation(user);
        assertTrue(list.isEmpty());
    }

    @Test
    void getEducationById_WhenValidUserAndBelongsToProfile_ReturnsEducation() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getsingleedu@joblivo.com", "GetSingle Edu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileEducation education = new CareerProfileEducation(
                educationId, profile, "Yale", "B.A.", "History", EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.of(education));

        CareerProfileEducation result = careerProfileService.getEducation(user, educationId);

        assertNotNull(result);
        assertEquals("Yale", result.getInstitutionName());
        assertEquals(educationId, result.getId());
    }

    @Test
    void getEducationById_WhenEducationNotFound_ThrowsEducationNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "notfoundedu@joblivo.com", "NotFound Edu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.empty());

        assertThrows(EducationNotFoundException.class, () -> careerProfileService.getEducation(user, educationId));
    }

    @Test
    void getEducationById_WhenBelongsToAnotherUserProfile_ThrowsEducationNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignEduId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_edu@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(educationRepository.findByIdAndCareerProfileId(foreignEduId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(EducationNotFoundException.class, () -> careerProfileService.getEducation(userA, foreignEduId));
    }

    @Test
    void updateEducation_WithValidRequest_UpdatesAndReturnsEducation() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "upedu@joblivo.com", "Up Edu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileEducation existingEducation = new CareerProfileEducation(
                educationId, profile, "Old University", "B.A.", "English", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1), LocalDate.of(2022, 5, 1), false, "3.5", "City", "Desc",
                0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.of(existingEducation));
        when(educationRepository.existsByCareerProfileIdAndNormalizedCompositeExcludingId(
                profileId, "New University", "B.S.", "Computer Science", educationId)).thenReturn(false);
        when(educationRepository.saveAndFlush(any(CareerProfileEducation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EducationRequest request = new EducationRequest(
                "New University", "B.S.", "Computer Science", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2019, 9, 1), null, true, "3.9", "New City", "Updated Desc", 2
        );

        CareerProfileEducation updated = careerProfileService.updateEducation(user, educationId, request);

        assertEquals("New University", updated.getInstitutionName());
        assertEquals("B.S.", updated.getDegree());
        assertEquals("Computer Science", updated.getFieldOfStudy());
        assertEquals(EducationLevel.UNDERGRADUATE, updated.getEducationLevel());
        assertEquals(LocalDate.of(2019, 9, 1), updated.getStartDate());
        assertNull(updated.getEndDate());
        assertTrue(updated.isCurrentlyStudying());
        assertEquals("3.9", updated.getGrade());
        assertEquals("New City", updated.getLocation());
        assertEquals("Updated Desc", updated.getDescription());
        assertEquals(2, updated.getDisplayOrder());

        verify(educationRepository).saveAndFlush(existingEducation);
    }

    @Test
    void updateEducation_WhenDuplicateCompositeExistsExcludingSelf_ThrowsDuplicateEducationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupeduup@joblivo.com", "Dup Edu Up", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileEducation existingEducation = new CareerProfileEducation(
                educationId, profile, "My College", "B.S.", "Physics", EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.of(existingEducation));
        when(educationRepository.existsByCareerProfileIdAndNormalizedCompositeExcludingId(
                eq(profileId), anyString(), any(), any(), eq(educationId))).thenReturn(true);

        EducationRequest updateRequest = new EducationRequest(
                "Other University", "B.S.", "Physics", EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0
        );

        assertThrows(DuplicateEducationException.class, () -> careerProfileService.updateEducation(user, educationId, updateRequest));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEducation_WhenEducationNotFound_ThrowsEducationNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "upedunotfound@joblivo.com", "UpEduNotFound", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.empty());

        EducationRequest request = new EducationRequest("Name", null, null, EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 0);

        assertThrows(EducationNotFoundException.class, () -> careerProfileService.updateEducation(user, educationId, request));
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteEducation_DeletesAndFlushes() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "deleteedu@joblivo.com", "DeleteEdu User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileEducation existingEducation = new CareerProfileEducation(
                educationId, profile, "To Delete", null, null, EducationLevel.OTHER,
                null, null, false, null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.of(existingEducation));

        careerProfileService.deleteEducation(user, educationId);

        verify(educationRepository).delete(existingEducation);
        verify(educationRepository).flush();
    }

    @Test
    void deleteEducation_WhenEducationNotFound_ThrowsEducationNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delnotfoundedu@joblivo.com", "DelNotFoundEdu", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.empty());

        assertThrows(EducationNotFoundException.class, () -> careerProfileService.deleteEducation(user, educationId));
        verify(educationRepository, never()).delete(any());
    }

    @Test
    void deleteEducation_WhenBelongsToAnotherUserProfile_ThrowsEducationNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignEduId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_deledu@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(educationRepository.findByIdAndCareerProfileId(foreignEduId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(EducationNotFoundException.class, () -> careerProfileService.deleteEducation(userA, foreignEduId));
        verify(educationRepository, never()).delete(any());
    }

    @Test
    void educationService_PrincipalMethods_DelegateCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princedu@joblivo.com", "PrincEdu User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileEducation education = new CareerProfileEducation(
                educationId, profile, "Princ University", "B.S.", "Math", EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of(education));
        when(educationRepository.findByIdAndCareerProfileId(educationId, profileId)).thenReturn(Optional.of(education));
        when(educationRepository.saveAndFlush(any(CareerProfileEducation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Test getEducation by principal
        List<CareerProfileEducation> list = careerProfileService.getEducation(userId.toString());
        assertEquals(1, list.size());

        // Test getEducation / getEducationById by principal
        CareerProfileEducation single = careerProfileService.getEducation(userId.toString(), educationId);
        assertEquals("Princ University", single.getInstitutionName());
        CareerProfileEducation singleById = careerProfileService.getEducationById(userId.toString(), educationId);
        assertEquals("Princ University", singleById.getInstitutionName());

        // Test updateEducation by principal
        EducationRequest upReq = new EducationRequest("Princ University", "B.S.", "Physics", EducationLevel.UNDERGRADUATE, null, null, false, null, null, null, 1);
        CareerProfileEducation updated = careerProfileService.updateEducation(userId.toString(), educationId, upReq);
        assertEquals("Physics", updated.getFieldOfStudy());

        // Test deleteEducation by principal
        careerProfileService.deleteEducation(userId.toString(), educationId);
        verify(educationRepository).delete(education);
    }

    // ==========================================
    // CERTIFICATION TESTS (PROMPT 27)
    // ==========================================

    @Test
    void addCertification_WithValidRequest_SavesAndReturnsCertification() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "certuser@joblivo.com", "Cert User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "AWS Certified Solutions Architect – Associate",
                "Amazon Web Services",
                "AWS-123456",
                "https://aws.amazon.com/verify?id=123456",
                LocalDate.of(2023, 1, 15),
                LocalDate.of(2026, 1, 15),
                false,
                "Validates technical skills in designing resilient AWS architectures.",
                0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedCredentialId(profileId, "AWS-123456")).thenReturn(false);
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenAnswer(invocation -> {
            CareerProfileCertification toSave = invocation.getArgument(0);
            return new CareerProfileCertification(
                    certId,
                    toSave.getCareerProfile(),
                    toSave.getCertificationName(),
                    toSave.getIssuingOrganization(),
                    toSave.getCredentialId(),
                    toSave.getCredentialUrl(),
                    toSave.getIssueDate(),
                    toSave.getExpirationDate(),
                    toSave.isDoesNotExpire(),
                    toSave.getDescription(),
                    toSave.getDisplayOrder(),
                    Instant.now(),
                    Instant.now()
            );
        });

        CareerProfileCertification result = careerProfileService.addCertification(user, request);

        assertNotNull(result);
        assertEquals(certId, result.getId());
        assertEquals("AWS Certified Solutions Architect – Associate", result.getCertificationName());
        assertEquals("Amazon Web Services", result.getIssuingOrganization());
        assertEquals("AWS-123456", result.getCredentialId());
        assertEquals("https://aws.amazon.com/verify?id=123456", result.getCredentialUrl());
        assertEquals(LocalDate.of(2023, 1, 15), result.getIssueDate());
        assertEquals(LocalDate.of(2026, 1, 15), result.getExpirationDate());
        assertFalse(result.isDoesNotExpire());
        assertEquals(0, result.getDisplayOrder());
    }

    @Test
    void addCertification_WithNonExpiringCertification_SavesCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "nonexpcert@joblivo.com", "NonExp Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "Certified Kubernetes Administrator",
                "CNCF",
                null,
                null,
                LocalDate.of(2022, 6, 1),
                null,
                true,
                "Hands-on Kubernetes administration certification.",
                1
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganization(profileId, "Certified Kubernetes Administrator", "CNCF")).thenReturn(false);
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileCertification result = careerProfileService.addCertification(user, request);

        assertNotNull(result);
        assertEquals("Certified Kubernetes Administrator", result.getCertificationName());
        assertEquals("CNCF", result.getIssuingOrganization());
        assertNull(result.getCredentialId());
        assertNull(result.getCredentialUrl());
        assertTrue(result.isDoesNotExpire());
        assertNull(result.getExpirationDate());
        assertEquals(1, result.getDisplayOrder());
    }

    @Test
    void addCertification_WithoutCredentialIdAndUrl_SavesCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "nocredid@joblivo.com", "NoCred Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "Internal Security Certification",
                "Joblivo Corp",
                null,
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1),
                false,
                null,
                2
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganization(profileId, "Internal Security Certification", "Joblivo Corp")).thenReturn(false);
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileCertification result = careerProfileService.addCertification(user, request);

        assertNotNull(result);
        assertEquals("Internal Security Certification", result.getCertificationName());
        assertEquals("Joblivo Corp", result.getIssuingOrganization());
        assertNull(result.getCredentialId());
        assertNull(result.getCredentialUrl());
        assertFalse(result.isDoesNotExpire());
    }

    @Test
    void addCertification_WhenProfileDoesNotExist_AutoCreatesProfileAndSavesCertification() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "autocert@joblivo.com", "Auto Cert", UserStatus.ACTIVE);
        CareerProfile newProfile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "GCP Professional Cloud Architect",
                "Google Cloud",
                "GCP-9999",
                null,
                LocalDate.of(2023, 5, 1),
                LocalDate.of(2025, 5, 1),
                false,
                null,
                0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(careerProfileRepository.existsByUserId(userId)).thenReturn(false);
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(newProfile);
        when(certificationRepository.existsByCareerProfileIdAndNormalizedCredentialId(any(), eq("GCP-9999"))).thenReturn(false);
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileCertification result = careerProfileService.addCertification(user, request);

        assertNotNull(result);
        assertEquals("GCP Professional Cloud Architect", result.getCertificationName());
        verify(careerProfileRepository).saveAndFlush(any(CareerProfile.class));
    }

    @Test
    void addCertification_WithNullUser_ThrowsIllegalArgumentException() {
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null, null, null, true, null, 0
        );
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification((User) null, request));
    }

    @Test
    void addCertification_WithSuspendedUser_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = createPersistedUser(userId, "suspendedcert@joblivo.com", "Suspended User", UserStatus.SUSPENDED);
        CertificationRequest request = new CertificationRequest(
                "AWS SAA", "AWS", null, null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        assertThrows(IneligibleUserException.class, () -> careerProfileService.addCertification(suspendedUser, request));
    }

    @Test
    void addCertification_WithNullOrBlankCertificationName_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "blankcert@joblivo.com", "Blank Cert", UserStatus.ACTIVE);

        CertificationRequest nullNameReq = new CertificationRequest(
                null, "AWS", null, null, null, null, true, null, 0
        );
        CertificationRequest blankNameReq = new CertificationRequest(
                "   ", "AWS", null, null, null, null, true, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, nullNameReq));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, blankNameReq));
    }

    @Test
    void addCertification_WithNullOrBlankIssuingOrganization_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "blankorg@joblivo.com", "Blank Org", UserStatus.ACTIVE);

        CertificationRequest nullOrgReq = new CertificationRequest(
                "AWS SAA", null, null, null, null, null, true, null, 0
        );
        CertificationRequest blankOrgReq = new CertificationRequest(
                "AWS SAA", "   ", null, null, null, null, true, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, nullOrgReq));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, blankOrgReq));
    }

    @Test
    void addCertification_WithNegativeDisplayOrder_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "negordercert@joblivo.com", "NegOrder Cert", UserStatus.ACTIVE);

        CertificationRequest negOrderReq = new CertificationRequest(
                "AWS SAA", "AWS", null, null, null, null, true, null, -1
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, negOrderReq));
    }

    @Test
    void addCertification_WithInvalidCredentialUrl_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "invalidurlcert@joblivo.com", "InvalidUrl Cert", UserStatus.ACTIVE);

        CertificationRequest badUrlReq = new CertificationRequest(
                "AWS SAA", "AWS", null, "ftp://example.com/bad", null, null, true, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, badUrlReq));
    }

    @Test
    void addCertification_WithInvalidDateRange_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "daterangecert@joblivo.com", "DateRange Cert", UserStatus.ACTIVE);

        CertificationRequest invalidRangeReq = new CertificationRequest(
                "AWS SAA", "AWS", null, null,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2024, 1, 1), // expiration before issue
                false, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, invalidRangeReq));
    }

    @Test
    void addCertification_WithDoesNotExpireTrueAndExpirationDate_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "contradictcert@joblivo.com", "Contradict Cert", UserStatus.ACTIVE);

        CertificationRequest contradictReq = new CertificationRequest(
                "AWS SAA", "AWS", null, null,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 1, 1),
                true, // contradictory: doesNotExpire is true but expirationDate provided
                null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addCertification(user, contradictReq));
    }

    @Test
    void addCertification_WithDuplicateCredentialId_ThrowsDuplicateCertificationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupcert@joblivo.com", "Dup Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "AWS Solutions Architect", "AWS", "AWS-123456", null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedCredentialId(profileId, "AWS-123456")).thenReturn(true);

        assertThrows(DuplicateCertificationException.class, () -> careerProfileService.addCertification(user, request));
        verify(certificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addCertification_WithDuplicateNameAndOrgWithoutCredentialId_ThrowsDuplicateCertificationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupnameorg@joblivo.com", "DupNameOrg Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "AWS Solutions Architect", "AWS", null, null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganization(profileId, "AWS Solutions Architect", "AWS")).thenReturn(true);

        assertThrows(DuplicateCertificationException.class, () -> careerProfileService.addCertification(user, request));
        verify(certificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void addCertification_WhenDatabaseUniqueConstraintFails_ThrowsDuplicateCertificationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "sqldupcert@joblivo.com", "SqlDup Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest request = new CertificationRequest(
                "AWS Solutions Architect", "AWS", "AWS-UNIQUE-1", null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedCredentialId(profileId, "AWS-UNIQUE-1")).thenReturn(false);
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThrows(DuplicateCertificationException.class, () -> careerProfileService.addCertification(user, request));
    }

    @Test
    void getCertifications_ReturnsOrderedList() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getcerts@joblivo.com", "GetCerts User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification cert1 = new CareerProfileCertification(
                UUID.randomUUID(), profile, "Cert 1", "Org 1", null, null, null, null, true, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileCertification cert2 = new CareerProfileCertification(
                UUID.randomUUID(), profile, "Cert 2", "Org 2", null, null, null, null, true, null, 1, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileId)).thenReturn(List.of(cert1, cert2));

        List<CareerProfileCertification> result = careerProfileService.getCertifications(user);

        assertEquals(2, result.size());
        assertEquals("Cert 1", result.get(0).getCertificationName());
        assertEquals("Cert 2", result.get(1).getCertificationName());
    }

    @Test
    void getCertification_ReturnsCertificationWhenFound() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getsinglecert@joblivo.com", "GetSingle Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification cert = new CareerProfileCertification(
                certId, profile, "Single Cert", "Single Org", null, null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.of(cert));

        CareerProfileCertification result = careerProfileService.getCertification(user, certId);

        assertNotNull(result);
        assertEquals(certId, result.getId());
        assertEquals("Single Cert", result.getCertificationName());
    }

    @Test
    void getCertification_WhenNotFound_ThrowsCertificationNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "notfoundcert@joblivo.com", "NotFound Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.empty());

        assertThrows(CertificationNotFoundException.class, () -> careerProfileService.getCertification(user, certId));
    }

    @Test
    void getCertification_WhenBelongsToAnotherUserProfile_ThrowsCertificationNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignCertId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_cert@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(certificationRepository.findByIdAndCareerProfileId(foreignCertId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(CertificationNotFoundException.class, () -> careerProfileService.getCertification(userA, foreignCertId));
    }

    @Test
    void updateCertification_WithValidData_UpdatesAndReturnsCertification() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatecert@joblivo.com", "Update Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification existingCert = new CareerProfileCertification(
                certId, profile, "Old Cert Name", "Old Org", "OLD-123", null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        CertificationRequest updateRequest = new CertificationRequest(
                "Updated Cert Name", "Updated Org", "NEW-123", "https://verify.org/cert",
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), false, "Updated description", 3
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.of(existingCert));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedCredentialIdExcludingId(profileId, "NEW-123", certId)).thenReturn(false);
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileCertification updated = careerProfileService.updateCertification(user, certId, updateRequest);

        assertNotNull(updated);
        assertEquals("Updated Cert Name", updated.getCertificationName());
        assertEquals("Updated Org", updated.getIssuingOrganization());
        assertEquals("NEW-123", updated.getCredentialId());
        assertEquals("https://verify.org/cert", updated.getCredentialUrl());
        assertEquals(3, updated.getDisplayOrder());
    }

    @Test
    void updateCertification_WhenNotFound_ThrowsCertificationNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatenotfoundcert@joblivo.com", "UpdateNotFound Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CertificationRequest updateRequest = new CertificationRequest(
                "Cert Name", "Org", null, null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.empty());

        assertThrows(CertificationNotFoundException.class, () -> careerProfileService.updateCertification(user, certId, updateRequest));
    }

    @Test
    void updateCertification_WhenDuplicateCredentialId_ThrowsDuplicateCertificationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatedupcred@joblivo.com", "UpdateDupCred Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification existingCert = new CareerProfileCertification(
                certId, profile, "Old Cert Name", "Old Org", "OLD-123", null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        CertificationRequest updateRequest = new CertificationRequest(
                "New Cert Name", "New Org", "EXISTING-CRED", null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.of(existingCert));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedCredentialIdExcludingId(profileId, "EXISTING-CRED", certId)).thenReturn(true);

        assertThrows(DuplicateCertificationException.class, () -> careerProfileService.updateCertification(user, certId, updateRequest));
        verify(certificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCertification_WhenDuplicateNameAndOrg_ThrowsDuplicateCertificationException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatedupname@joblivo.com", "UpdateDupName Cert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification existingCert = new CareerProfileCertification(
                certId, profile, "Old Cert Name", "Old Org", null, null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        CertificationRequest updateRequest = new CertificationRequest(
                "Existing Cert Name", "Existing Org", null, null, null, null, true, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.of(existingCert));
        when(certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganizationExcludingId(profileId, "Existing Cert Name", "Existing Org", certId)).thenReturn(true);

        assertThrows(DuplicateCertificationException.class, () -> careerProfileService.updateCertification(user, certId, updateRequest));
        verify(certificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteCertification_DeletesWhenFound() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delcert@joblivo.com", "DelCert User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification existingCert = new CareerProfileCertification(
                certId, profile, "Cert to delete", "Org", null, null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.of(existingCert));

        careerProfileService.deleteCertification(user, certId);

        verify(certificationRepository).delete(existingCert);
        verify(certificationRepository).flush();
    }

    @Test
    void deleteCertification_WhenNotFound_ThrowsCertificationNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delnotfoundcert@joblivo.com", "DelNotFoundCert", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.empty());

        assertThrows(CertificationNotFoundException.class, () -> careerProfileService.deleteCertification(user, certId));
        verify(certificationRepository, never()).delete(any());
    }

    @Test
    void deleteCertification_WhenBelongsToAnotherUserProfile_ThrowsCertificationNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignCertId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_delcert@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(certificationRepository.findByIdAndCareerProfileId(foreignCertId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(CertificationNotFoundException.class, () -> careerProfileService.deleteCertification(userA, foreignCertId));
        verify(certificationRepository, never()).delete(any());
    }

    @Test
    void certificationService_PrincipalMethods_DelegateCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princcert@joblivo.com", "PrincCert User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileCertification cert = new CareerProfileCertification(
                certId, profile, "Princ Cert", "Princ Org", null, null, null, null, true, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileId)).thenReturn(List.of(cert));
        when(certificationRepository.findByIdAndCareerProfileId(certId, profileId)).thenReturn(Optional.of(cert));
        when(certificationRepository.saveAndFlush(any(CareerProfileCertification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Test getCertifications by principal
        List<CareerProfileCertification> list = careerProfileService.getCertifications(userId.toString());
        assertEquals(1, list.size());

        // Test getCertification / getCertificationById by principal
        CareerProfileCertification single = careerProfileService.getCertification(userId.toString(), certId);
        assertEquals("Princ Cert", single.getCertificationName());
        CareerProfileCertification singleById = careerProfileService.getCertificationById(userId.toString(), certId);
        assertEquals("Princ Cert", singleById.getCertificationName());

        // Test updateCertification by principal
        CertificationRequest upReq = new CertificationRequest("Princ Cert Updated", "Princ Org", null, null, null, null, true, null, 1);
        when(certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganizationExcludingId(profileId, "Princ Cert Updated", "Princ Org", certId)).thenReturn(false);
        CareerProfileCertification updated = careerProfileService.updateCertification(userId.toString(), certId, upReq);
        assertEquals("Princ Cert Updated", updated.getCertificationName());

        // Test deleteCertification by principal
        careerProfileService.deleteCertification(userId.toString(), certId);
        verify(certificationRepository).delete(cert);
    }

    // =========================================================================
    // Master Career Profile — Achievements Foundation (Prompt 28)
    // =========================================================================

    @Test
    void addAchievement_WithValidRequest_SavesAndReturnsAchievement() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "achiever@joblivo.com", "Achiever", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        AchievementRequest request = new AchievementRequest(
                "  Employee of the Year  ",
                AchievementType.AWARD,
                "  Acme Corp  ",
                LocalDate.of(2023, 12, 15),
                "  Outstanding contribution  ",
                "  https://acme.com/awards/2023  ",
                1
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.existsByCompositeNormalized(
                profileId,
                "Employee of the Year",
                AchievementType.AWARD,
                LocalDate.of(2023, 12, 15),
                "Acme Corp"
        )).thenReturn(false);
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileAchievement result = careerProfileService.addAchievement(user, request);

        assertNotNull(result);
        assertEquals(profile, result.getCareerProfile());
        assertEquals("Employee of the Year", result.getTitle());
        assertEquals(AchievementType.AWARD, result.getAchievementType());
        assertEquals("Acme Corp", result.getIssuingOrganization());
        assertEquals(LocalDate.of(2023, 12, 15), result.getAchievementDate());
        assertEquals("Outstanding contribution", result.getDescription());
        assertEquals("https://acme.com/awards/2023", result.getUrl());
        assertEquals(1, result.getDisplayOrder());

        ArgumentCaptor<CareerProfileAchievement> captor = ArgumentCaptor.forClass(CareerProfileAchievement.class);
        verify(achievementRepository).saveAndFlush(captor.capture());
        assertEquals("Employee of the Year", captor.getValue().getTitle());
    }

    @Test
    void addAchievement_WithOptionalFieldsNull_SavesAndDefaultsOrder() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "minimalachieve@joblivo.com", "Minimal Achieve", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        AchievementRequest request = new AchievementRequest(
                "Hackathon Champion",
                AchievementType.HACKATHON,
                null,
                null,
                null,
                null,
                null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.existsByCompositeNormalized(
                profileId,
                "Hackathon Champion",
                AchievementType.HACKATHON,
                null,
                null
        )).thenReturn(false);
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileAchievement result = careerProfileService.addAchievement(user, request);

        assertNotNull(result);
        assertEquals("Hackathon Champion", result.getTitle());
        assertEquals(AchievementType.HACKATHON, result.getAchievementType());
        assertNull(result.getIssuingOrganization());
        assertNull(result.getAchievementDate());
        assertNull(result.getDescription());
        assertNull(result.getUrl());
        assertEquals(0, result.getDisplayOrder());
    }

    @Test
    void addAchievement_WhenProfileDoesNotExist_CreatesProfileAutomatically() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprofileachieve@joblivo.com", "NoProfile Achieve", UserStatus.ACTIVE);
        CareerProfile newProfile = new CareerProfile(UUID.randomUUID(), user, Instant.now(), Instant.now());

        AchievementRequest request = new AchievementRequest(
                "First Publication", AchievementType.PUBLICATION, "IEEE", LocalDate.of(2023, 5, 1), null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(careerProfileRepository.saveAndFlush(any(CareerProfile.class))).thenReturn(newProfile);
        when(achievementRepository.existsByCompositeNormalized(any(), any(), any(), any(), any())).thenReturn(false);
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileAchievement result = careerProfileService.addAchievement(user, request);

        assertNotNull(result);
        assertEquals("First Publication", result.getTitle());
        verify(careerProfileRepository).saveAndFlush(any(CareerProfile.class));
    }

    @Test
    void addAchievement_WithNullUser_ThrowsIllegalArgumentException() {
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, 0
        );
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addAchievement((User) null, request));
    }

    @Test
    void addAchievement_WithSuspendedUser_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = createPersistedUser(userId, "suspendedachieve@joblivo.com", "Suspended User", UserStatus.SUSPENDED);
        AchievementRequest request = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        assertThrows(IneligibleUserException.class, () -> careerProfileService.addAchievement(suspendedUser, request));
    }

    @Test
    void addAchievement_WithNullOrBlankTitle_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "blanktitleachieve@joblivo.com", "Blank Title", UserStatus.ACTIVE);

        AchievementRequest nullTitleReq = new AchievementRequest(
                null, AchievementType.AWARD, null, null, null, null, 0
        );
        AchievementRequest blankTitleReq = new AchievementRequest(
                "   ", AchievementType.AWARD, null, null, null, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addAchievement(user, nullTitleReq));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addAchievement(user, blankTitleReq));
    }

    @Test
    void addAchievement_WithNullAchievementType_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "nulltypeachieve@joblivo.com", "Null Type", UserStatus.ACTIVE);

        AchievementRequest nullTypeReq = new AchievementRequest(
                "Award", null, null, null, null, null, 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addAchievement(user, nullTypeReq));
    }

    @Test
    void addAchievement_WithNegativeDisplayOrder_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "negorderachieve@joblivo.com", "NegOrder Achieve", UserStatus.ACTIVE);

        AchievementRequest negOrderReq = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, null, -1
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addAchievement(user, negOrderReq));
    }

    @Test
    void addAchievement_WithInvalidUrl_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "invalidurlachieve@joblivo.com", "InvalidUrl Achieve", UserStatus.ACTIVE);

        AchievementRequest badUrlReq = new AchievementRequest(
                "Award", AchievementType.AWARD, null, null, null, "ftp://invalid-url", 0
        );

        assertThrows(IllegalArgumentException.class, () -> careerProfileService.addAchievement(user, badUrlReq));
    }

    @Test
    void addAchievement_WithDuplicateComposite_ThrowsDuplicateAchievementException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dupachieve@joblivo.com", "Dup Achieve", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        AchievementRequest request = new AchievementRequest(
                "Top Engineer", AchievementType.RECOGNITION, "Acme", LocalDate.of(2023, 11, 1), null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.existsByCompositeNormalized(
                profileId,
                "Top Engineer",
                AchievementType.RECOGNITION,
                LocalDate.of(2023, 11, 1),
                "Acme"
        )).thenReturn(true);

        assertThrows(DuplicateAchievementException.class, () -> careerProfileService.addAchievement(user, request));
        verify(achievementRepository, never()).saveAndFlush(any());
    }

    @Test
    void addAchievement_WhenDataIntegrityViolation_ThrowsDuplicateAchievementException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "dataviolationachieve@joblivo.com", "DataViolation Achieve", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        AchievementRequest request = new AchievementRequest(
                "Patent Issued", AchievementType.PATENT, "USPTO", LocalDate.of(2022, 1, 1), null, null, 0
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.existsByCompositeNormalized(any(), any(), any(), any(), any())).thenReturn(false);
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(DuplicateAchievementException.class, () -> careerProfileService.addAchievement(user, request));
    }

    @Test
    void getAchievements_ReturnsListOrderedByDisplayOrderAscAndDateDesc() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getachieves@joblivo.com", "GetAchieves User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement a1 = new CareerProfileAchievement(
                profile, "Award 1", AchievementType.AWARD, null, null, null, null, 0
        );
        CareerProfileAchievement a2 = new CareerProfileAchievement(
                profile, "Award 2", AchievementType.RECOGNITION, null, null, null, null, 1
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profileId)).thenReturn(List.of(a1, a2));

        List<CareerProfileAchievement> result = careerProfileService.getAchievements(user);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Award 1", result.get(0).getTitle());
        assertEquals("Award 2", result.get(1).getTitle());
    }

    @Test
    void getAchievement_WhenFound_ReturnsAchievement() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "getachieve@joblivo.com", "GetAchieve User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement achievement = new CareerProfileAchievement(
                achieveId, profile, "Promotion", AchievementType.PROMOTION, null, null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.of(achievement));

        CareerProfileAchievement result = careerProfileService.getAchievement(user, achieveId);

        assertNotNull(result);
        assertEquals("Promotion", result.getTitle());
    }

    @Test
    void getAchievement_WhenNotFound_ThrowsAchievementNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "notfoundachieve@joblivo.com", "NotFoundAchieve User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.empty());

        assertThrows(AchievementNotFoundException.class, () -> careerProfileService.getAchievement(user, achieveId));
    }

    @Test
    void getAchievement_WhenBelongsToAnotherProfile_ThrowsAchievementNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignAchieveId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_achieve@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(achievementRepository.findByIdAndCareerProfileId(foreignAchieveId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(AchievementNotFoundException.class, () -> careerProfileService.getAchievement(userA, foreignAchieveId));
    }

    @Test
    void updateAchievement_WithValidRequest_UpdatesAndReturnsAchievement() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updateachieve@joblivo.com", "UpdateAchieve User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement existing = new CareerProfileAchievement(
                achieveId, profile, "Old Title", AchievementType.OTHER, "Old Org",
                LocalDate.of(2022, 1, 1), "Old Desc", null, 0, Instant.now(), Instant.now()
        );

        AchievementRequest updateRequest = new AchievementRequest(
                "Updated Award", AchievementType.AWARD, "New Org",
                LocalDate.of(2023, 6, 1), "New Desc", "https://example.com/award", 2
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.of(existing));
        when(achievementRepository.existsByCompositeNormalizedExcludingId(
                profileId,
                "Updated Award",
                AchievementType.AWARD,
                LocalDate.of(2023, 6, 1),
                "New Org",
                achieveId
        )).thenReturn(false);
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CareerProfileAchievement updated = careerProfileService.updateAchievement(user, achieveId, updateRequest);

        assertNotNull(updated);
        assertEquals("Updated Award", updated.getTitle());
        assertEquals(AchievementType.AWARD, updated.getAchievementType());
        assertEquals("New Org", updated.getIssuingOrganization());
        assertEquals(LocalDate.of(2023, 6, 1), updated.getAchievementDate());
        assertEquals("New Desc", updated.getDescription());
        assertEquals("https://example.com/award", updated.getUrl());
        assertEquals(2, updated.getDisplayOrder());
    }

    @Test
    void updateAchievement_WhenNotFound_ThrowsAchievementNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatenotfound@joblivo.com", "UpdateNotFound User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        AchievementRequest request = new AchievementRequest("Award", AchievementType.AWARD, null, null, null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.empty());

        assertThrows(AchievementNotFoundException.class, () -> careerProfileService.updateAchievement(user, achieveId, request));
    }

    @Test
    void updateAchievement_WhenDuplicateExists_ThrowsDuplicateAchievementException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatedupachieve@joblivo.com", "UpdateDupAchieve User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement existing = new CareerProfileAchievement(
                achieveId, profile, "Award", AchievementType.AWARD, "Org",
                LocalDate.of(2023, 1, 1), null, null, 0, Instant.now(), Instant.now()
        );

        AchievementRequest request = new AchievementRequest("Conflicting Title", AchievementType.AWARD, "Org", LocalDate.of(2023, 1, 1), null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.of(existing));
        when(achievementRepository.existsByCompositeNormalizedExcludingId(
                profileId,
                "Conflicting Title",
                AchievementType.AWARD,
                LocalDate.of(2023, 1, 1),
                "Org",
                achieveId
        )).thenReturn(true);

        assertThrows(DuplicateAchievementException.class, () -> careerProfileService.updateAchievement(user, achieveId, request));
        verify(achievementRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateAchievement_WhenDataIntegrityViolation_ThrowsDuplicateAchievementException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "updatedataviolation@joblivo.com", "UpdateDataViolation User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement existing = new CareerProfileAchievement(
                achieveId, profile, "Award", AchievementType.AWARD, "Org",
                LocalDate.of(2023, 1, 1), null, null, 0, Instant.now(), Instant.now()
        );

        AchievementRequest request = new AchievementRequest("New Award", AchievementType.AWARD, "Org", LocalDate.of(2023, 1, 1), null, null, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.of(existing));
        when(achievementRepository.existsByCompositeNormalizedExcludingId(any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(DuplicateAchievementException.class, () -> careerProfileService.updateAchievement(user, achieveId, request));
    }

    @Test
    void deleteAchievement_WhenFound_DeletesAndFlushes() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delachieve@joblivo.com", "DelAchieve User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement existing = new CareerProfileAchievement(
                achieveId, profile, "To Delete", AchievementType.OTHER, null, null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.of(existing));

        careerProfileService.deleteAchievement(user, achieveId);

        verify(achievementRepository).delete(existing);
        verify(achievementRepository).flush();
    }

    @Test
    void deleteAchievement_WhenNotFound_ThrowsAchievementNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "delnotfoundachieve@joblivo.com", "DelNotFoundAchieve", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.empty());

        assertThrows(AchievementNotFoundException.class, () -> careerProfileService.deleteAchievement(user, achieveId));
        verify(achievementRepository, never()).delete(any());
    }

    @Test
    void deleteAchievement_WhenBelongsToAnotherUserProfile_ThrowsAchievementNotFoundException() {
        UUID userAId = UUID.randomUUID();
        UUID userAProfileId = UUID.randomUUID();
        UUID foreignAchieveId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "usera_delachieve@joblivo.com", "User A", UserStatus.ACTIVE);
        CareerProfile profileA = new CareerProfile(userAProfileId, userA, Instant.now(), Instant.now());

        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(careerProfileRepository.findByUserId(userAId)).thenReturn(Optional.of(profileA));
        when(achievementRepository.findByIdAndCareerProfileId(foreignAchieveId, userAProfileId)).thenReturn(Optional.empty());

        assertThrows(AchievementNotFoundException.class, () -> careerProfileService.deleteAchievement(userA, foreignAchieveId));
        verify(achievementRepository, never()).delete(any());
    }

    @Test
    void achievementService_PrincipalMethods_DelegateCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID achieveId = UUID.randomUUID();
        User user = createPersistedUser(userId, "princachieve@joblivo.com", "PrincAchieve User", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());

        CareerProfileAchievement achieve = new CareerProfileAchievement(
                achieveId, profile, "Princ Achieve", AchievementType.AWARD, "Princ Org", null, null, null, 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profileId)).thenReturn(List.of(achieve));
        when(achievementRepository.findByIdAndCareerProfileId(achieveId, profileId)).thenReturn(Optional.of(achieve));
        when(achievementRepository.saveAndFlush(any(CareerProfileAchievement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Test getAchievements by principal
        List<CareerProfileAchievement> list = careerProfileService.getAchievements(userId.toString());
        assertEquals(1, list.size());

        // Test getAchievement / getAchievementById by principal
        CareerProfileAchievement single = careerProfileService.getAchievement(userId.toString(), achieveId);
        assertEquals("Princ Achieve", single.getTitle());
        CareerProfileAchievement singleById = careerProfileService.getAchievementById(userId.toString(), achieveId);
        assertEquals("Princ Achieve", singleById.getTitle());

        // Test updateAchievement by principal
        AchievementRequest upReq = new AchievementRequest("Princ Achieve Updated", AchievementType.AWARD, "Princ Org", null, null, null, 1);
        when(achievementRepository.existsByCompositeNormalizedExcludingId(
                profileId,
                "Princ Achieve Updated",
                AchievementType.AWARD,
                null,
                "Princ Org",
                achieveId
        )).thenReturn(false);
        CareerProfileAchievement updated = careerProfileService.updateAchievement(userId.toString(), achieveId, upReq);
        assertEquals("Princ Achieve Updated", updated.getTitle());

        // Test deleteAchievement by principal
        careerProfileService.deleteAchievement(userId.toString(), achieveId);
        verify(achievementRepository).delete(achieve);
    }

    @Test
    void getMasterCareerProfile_WhenProfileNotFound_ThrowsCareerProfileNotFoundException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "noprofile@joblivo.com", "No Profile", UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(CareerProfileNotFoundException.class, () -> careerProfileService.getMasterCareerProfile(user));
        assertThrows(CareerProfileNotFoundException.class, () -> careerProfileService.getMasterCareerProfile(userId.toString()));
    }

    @Test
    void getMasterCareerProfile_WhenUserIneligible_ThrowsIneligibleUserException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "suspended@joblivo.com", "Suspended User", UserStatus.SUSPENDED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(IneligibleUserException.class, () -> careerProfileService.getMasterCareerProfile(user));
    }

    @Test
    void getMasterCareerProfile_WhenUserNullOrPrincipalBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.getMasterCareerProfile((User) null));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.getMasterCareerProfile((String) null));
        assertThrows(IllegalArgumentException.class, () -> careerProfileService.getMasterCareerProfile("   "));
    }

    @Test
    void getMasterCareerProfile_WhenProfileHasOnlyCoreDetails_ReturnsExpectedReadModel() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "coreonly@joblivo.com", "Core Only", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        profile.setProfessionalHeadline("Full Stack Engineer");
        profile.setCurrentTitle("Staff Engineer");
        profile.setCurrentCompany("Acme Inc");
        profile.setTotalExperienceMonths(48);
        profile.setCurrentLocation("Austin, TX");
        profile.setPreferredWorkLocation("Remote");
        profile.setPreferredWorkMode(WorkMode.REMOTE);
        profile.setNoticePeriodDays(30);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of());
        when(skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileId)).thenReturn(List.of());
        when(projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of());
        when(educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of());
        when(certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileId)).thenReturn(List.of());
        when(achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profileId)).thenReturn(List.of());

        MasterCareerProfileResponse response = careerProfileService.getMasterCareerProfile(user);

        assertNotNull(response);
        assertEquals(profileId, response.id());
        assertEquals("Full Stack Engineer", response.professionalHeadline());
        assertEquals("Staff Engineer", response.currentTitle());
        assertEquals("Acme Inc", response.currentCompany());
        assertEquals(48, response.totalExperienceMonths());
        assertEquals("Austin, TX", response.currentLocation());
        assertEquals("Remote", response.preferredWorkLocation());
        assertEquals(WorkMode.REMOTE, response.preferredWorkMode());
        assertEquals(30, response.noticePeriodDays());
        assertTrue(response.workExperiences().isEmpty());
        assertTrue(response.skills().isEmpty());
        assertTrue(response.projects().isEmpty());
        assertTrue(response.education().isEmpty());
        assertTrue(response.certifications().isEmpty());
        assertTrue(response.achievements().isEmpty());

        assertEquals(14, response.completeness().completionPercentage());
        assertTrue(response.completeness().coreDetails().completed());
        assertEquals(8, response.completeness().coreDetails().itemCount());
        assertFalse(response.completeness().workExperience().completed());
        assertEquals(0, response.completeness().workExperience().itemCount());
    }

    @Test
    void getMasterCareerProfile_WhenFullyPopulated_ReturnsAllCollectionsAnd100PercentCompleteness() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "full@joblivo.com", "Full Profile", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        profile.setProfessionalHeadline("Full Stack Engineer");

        CareerProfileWorkExperience exp = new CareerProfileWorkExperience(
                UUID.randomUUID(), profile, "Tech Co", "Senior Dev", EmploymentType.FULL_TIME,
                LocalDate.of(2021, 1, 1), null, true, "SF", "Lead", 0, Instant.now(), Instant.now()
        );
        CareerProfileSkill skill = new CareerProfileSkill(
                UUID.randomUUID(), profile, "Java", SkillCategory.PROGRAMMING_LANGUAGE,
                SkillProficiency.ADVANCED, BigDecimal.valueOf(5), LocalDate.now(), 0, Instant.now(), Instant.now()
        );
        CareerProfileProject proj = new CareerProfileProject(
                UUID.randomUUID(), profile, "Project Alpha", ProjectType.PROFESSIONAL, "Lead",
                "Description", LocalDate.of(2022, 1, 1), null, true, "https://alpha.example.com", 0, Instant.now(), Instant.now()
        );
        CareerProfileEducation edu = new CareerProfileEducation(
                UUID.randomUUID(), profile, "MIT", "B.S.", "Computer Science", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2016, 9, 1), LocalDate.of(2020, 6, 1), false, "3.9", "Boston", "CS Degree", 0, Instant.now(), Instant.now()
        );
        CareerProfileCertification cert = new CareerProfileCertification(
                UUID.randomUUID(), profile, "AWS Certified", "Amazon", "AWS-123", "https://aws.cert/123",
                LocalDate.of(2023, 1, 1), null, true, "AWS Cloud", 0, Instant.now(), Instant.now()
        );
        CareerProfileAchievement ach = new CareerProfileAchievement(
                UUID.randomUUID(), profile, "Top Engineer 2024", AchievementType.AWARD, "Tech Co",
                LocalDate.of(2024, 1, 1), null, "Award", 0, Instant.now(), Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of(exp));
        when(skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileId)).thenReturn(List.of(skill));
        when(projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of(proj));
        when(educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of(edu));
        when(certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileId)).thenReturn(List.of(cert));
        when(achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profileId)).thenReturn(List.of(ach));

        MasterCareerProfileResponse response = careerProfileService.getMasterCareerProfile(userId.toString());

        assertNotNull(response);
        assertEquals(1, response.workExperiences().size());
        assertEquals(1, response.skills().size());
        assertEquals(1, response.projects().size());
        assertEquals(1, response.education().size());
        assertEquals(1, response.certifications().size());
        assertEquals(1, response.achievements().size());
        assertEquals(100, response.completeness().completionPercentage());
        assertTrue(response.completeness().coreDetails().completed());
        assertTrue(response.completeness().workExperience().completed());
        assertTrue(response.completeness().skills().completed());
        assertTrue(response.completeness().projects().completed());
        assertTrue(response.completeness().education().completed());
        assertTrue(response.completeness().certifications().completed());
        assertTrue(response.completeness().achievements().completed());
    }

    @Test
    void getMasterCareerProfile_EnforcesDeterministicOrdering() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User user = createPersistedUser(userId, "order@joblivo.com", "Order Test", UserStatus.ACTIVE);
        CareerProfile profile = new CareerProfile(profileId, user, Instant.now(), Instant.now());
        profile.setProfessionalHeadline("Engineer");

        UUID expId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID expId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID expId3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // exp3 has displayOrder 0, startDate 2020
        // exp2 has displayOrder 1, startDate 2022
        // exp1 has displayOrder 1, startDate 2021
        CareerProfileWorkExperience exp1 = new CareerProfileWorkExperience(
                expId1, profile, "Company 1", "Dev 1", EmploymentType.FULL_TIME,
                LocalDate.of(2021, 1, 1), null, true, "City", "Desc", 1, Instant.now(), Instant.now()
        );
        CareerProfileWorkExperience exp2 = new CareerProfileWorkExperience(
                expId2, profile, "Company 2", "Dev 2", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1), null, true, "City", "Desc", 1, Instant.now(), Instant.now()
        );
        CareerProfileWorkExperience exp3 = new CareerProfileWorkExperience(
                expId3, profile, "Company 3", "Dev 3", EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1), null, true, "City", "Desc", 0, Instant.now(), Instant.now()
        );

        // Pass out-of-order list to repository mock
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(workExperienceRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId))
                .thenReturn(List.of(exp1, exp3, exp2));
        when(skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileId)).thenReturn(List.of());
        when(projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of());
        when(educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)).thenReturn(List.of());
        when(certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileId)).thenReturn(List.of());
        when(achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profileId)).thenReturn(List.of());

        MasterCareerProfileResponse response = careerProfileService.getMasterCareerProfile(user);

        // Expected sorted order:
        // 1st: exp3 (displayOrder 0)
        // 2nd: exp2 (displayOrder 1, startDate 2022)
        // 3rd: exp1 (displayOrder 1, startDate 2021)
        assertEquals(3, response.workExperiences().size());
        assertEquals(expId3, response.workExperiences().get(0).id());
        assertEquals(expId2, response.workExperiences().get(1).id());
        assertEquals(expId1, response.workExperiences().get(2).id());
    }
}



package com.joblivo.profile;

import com.joblivo.user.User;
import com.joblivo.user.UserAuthenticationEligibilityService;
import com.joblivo.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service managing the lifecycle and integrity of Master Career Profiles.
 * Enforces ownership, active user eligibility, and strict 1-to-1 profile constraints.
 */
@Service
@Transactional(readOnly = true)
public class CareerProfileService {

    private final CareerProfileRepository careerProfileRepository;
    private final UserRepository userRepository;
    private final UserAuthenticationEligibilityService eligibilityService;
    private final CareerProfileWorkExperienceRepository workExperienceRepository;
    private final CareerProfileSkillRepository skillRepository;
    private final CareerProfileProjectRepository projectRepository;
    private final CareerProfileEducationRepository educationRepository;
    private final CareerProfileCertificationRepository certificationRepository;
    private final CareerProfileAchievementRepository achievementRepository;

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService,
            CareerProfileWorkExperienceRepository workExperienceRepository,
            CareerProfileSkillRepository skillRepository,
            CareerProfileProjectRepository projectRepository,
            CareerProfileEducationRepository educationRepository,
            CareerProfileCertificationRepository certificationRepository,
            CareerProfileAchievementRepository achievementRepository) {
        this.careerProfileRepository = Objects.requireNonNull(careerProfileRepository, "careerProfileRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.eligibilityService = Objects.requireNonNull(eligibilityService, "eligibilityService must not be null");
        this.workExperienceRepository = workExperienceRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
        this.achievementRepository = achievementRepository;
    }

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService,
            CareerProfileWorkExperienceRepository workExperienceRepository,
            CareerProfileSkillRepository skillRepository,
            CareerProfileProjectRepository projectRepository,
            CareerProfileEducationRepository educationRepository,
            CareerProfileCertificationRepository certificationRepository) {
        this(careerProfileRepository, userRepository, eligibilityService, workExperienceRepository, skillRepository, projectRepository, educationRepository, certificationRepository, null);
    }

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService,
            CareerProfileWorkExperienceRepository workExperienceRepository,
            CareerProfileSkillRepository skillRepository,
            CareerProfileProjectRepository projectRepository,
            CareerProfileEducationRepository educationRepository) {
        this(careerProfileRepository, userRepository, eligibilityService, workExperienceRepository, skillRepository, projectRepository, educationRepository, null);
    }

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService,
            CareerProfileWorkExperienceRepository workExperienceRepository,
            CareerProfileSkillRepository skillRepository,
            CareerProfileProjectRepository projectRepository) {
        this(careerProfileRepository, userRepository, eligibilityService, workExperienceRepository, skillRepository, projectRepository, null);
    }

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService,
            CareerProfileWorkExperienceRepository workExperienceRepository,
            CareerProfileSkillRepository skillRepository) {
        this(careerProfileRepository, userRepository, eligibilityService, workExperienceRepository, skillRepository, null);
    }

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService,
            CareerProfileWorkExperienceRepository workExperienceRepository) {
        this(careerProfileRepository, userRepository, eligibilityService, workExperienceRepository, null, null);
    }

    public CareerProfileService(
            CareerProfileRepository careerProfileRepository,
            UserRepository userRepository,
            UserAuthenticationEligibilityService eligibilityService) {
        this(careerProfileRepository, userRepository, eligibilityService, null, null, null);
    }

    /**
     * Creates and persists a Master Career Profile for a valid, eligible user.
     *
     * @param user the persisted user account
     * @return the saved and flushed CareerProfile
     * @throws IllegalArgumentException        if user is null or not persisted in the database
     * @throws IneligibleUserException         if the user account status does not allow profile ownership
     * @throws DuplicateCareerProfileException if a profile already exists for the user
     */
    @Transactional
    public CareerProfile createProfile(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user with non-null ID is required to create a career profile");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to own a master career profile"
            );
        }

        if (careerProfileRepository.existsByUserId(persistedUser.getId())) {
            throw new DuplicateCareerProfileException(
                    "A master career profile already exists for user: " + persistedUser.getId()
            );
        }

        CareerProfile profile = new CareerProfile(persistedUser);
        try {
            return careerProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCareerProfileException(
                    "A master career profile already exists for user: " + persistedUser.getId(),
                    ex
            );
        }
    }

    /**
     * Creates and persists a Master Career Profile for an existing user identified by UUID.
     *
     * @param userId the UUID of the owning user
     * @return the saved and flushed CareerProfile
     * @throws IllegalArgumentException        if userId is null or no user exists with this ID
     * @throws IneligibleUserException         if the user account status does not allow profile ownership
     * @throws DuplicateCareerProfileException if a profile already exists for the user
     */
    @Transactional
    public CareerProfile createProfile(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        return createProfile(user);
    }

    /**
     * Creates a Master Career Profile for a user identified by an authenticated principal identifier
     * (either a user UUID string or an email address).
     *
     * @param principal the authenticated principal identifier (UUID string or email)
     * @return the saved and flushed CareerProfile
     * @throws IllegalArgumentException if principal is null/blank or resolves to no existing user
     */
    @Transactional
    public CareerProfile createProfileForPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Principal identifier must not be null or blank");
        }

        String trimmed = principal.trim();
        Optional<User> userOptional = Optional.empty();

        try {
            UUID userId = UUID.fromString(trimmed);
            userOptional = userRepository.findById(userId);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID format, treat as email
        }

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByEmailIgnoreCase(trimmed);
        }

        User user = userOptional.orElseThrow(
                () -> new IllegalArgumentException("User not found for principal: " + trimmed)
        );

        return createProfile(user);
    }

    /**
     * Updates the core details of a master career profile for an existing, eligible user.
     * Preserves profile identity and ownership immutability.
     *
     * @param user the owning user account
     * @param request the validated update payload
     * @return the updated and saved CareerProfile
     * @throws IllegalArgumentException if user or request is null, or user has no profile
     * @throws IneligibleUserException if the user account status does not allow profile management
     */
    @Transactional
    public CareerProfile updateProfile(User user, UpdateCareerProfileRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user with non-null ID is required to update a career profile");
        }
        if (request == null) {
            throw new IllegalArgumentException("UpdateCareerProfileRequest must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Career profile not found for user: " + persistedUser.getId()));

        // Defensive validations for numeric values
        if (request.totalExperienceMonths() != null && request.totalExperienceMonths() < 0) {
            throw new IllegalArgumentException("Total experience months must not be negative");
        }
        if (request.noticePeriodDays() != null && request.noticePeriodDays() < 0) {
            throw new IllegalArgumentException("Notice period days must not be negative");
        }

        // Apply controlled updates to permitted core fields
        profile.setProfessionalHeadline(request.professionalHeadline());
        profile.setCurrentTitle(request.currentTitle());
        profile.setCurrentCompany(request.currentCompany());
        profile.setTotalExperienceMonths(request.totalExperienceMonths());
        profile.setCurrentLocation(request.currentLocation());
        profile.setPreferredWorkLocation(request.preferredWorkLocation());
        profile.setPreferredWorkMode(request.preferredWorkMode());
        profile.setNoticePeriodDays(request.noticePeriodDays());

        // profile ID and owning user remain strictly unchanged
        return careerProfileRepository.saveAndFlush(profile);
    }

    /**
     * Updates the core details of a master career profile for a user identified by UUID.
     *
     * @param userId the UUID of the owning user
     * @param request the validated update payload
     * @return the updated and saved CareerProfile
     * @throws IllegalArgumentException if userId is null or no user exists with this ID
     */
    @Transactional
    public CareerProfile updateProfile(UUID userId, UpdateCareerProfileRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        return updateProfile(user, request);
    }

    /**
     * Updates the core details of a master career profile for a user identified by principal.
     *
     * @param principal the authenticated principal identifier (UUID string or email)
     * @param request the validated update payload
     * @return the updated and saved CareerProfile
     * @throws IllegalArgumentException if principal is null/blank or resolves to no existing user
     */
    @Transactional
    public CareerProfile updateProfileForPrincipal(String principal, UpdateCareerProfileRequest request) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Principal identifier must not be null or blank");
        }

        String trimmed = principal.trim();
        Optional<User> userOptional = Optional.empty();

        try {
            UUID userId = UUID.fromString(trimmed);
            userOptional = userRepository.findById(userId);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID format, treat as email
        }

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByEmailIgnoreCase(trimmed);
        }

        User user = userOptional.orElseThrow(
                () -> new IllegalArgumentException("User not found for principal: " + trimmed)
        );

        return updateProfile(user, request);
    }

    /**
     * Finds a career profile by owning User entity.
     *
     * @param user the owning user
     * @return Optional containing the career profile if found, empty otherwise
     */
    public Optional<CareerProfile> findByUser(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return careerProfileRepository.findByUser(user);
    }

    /**
     * Finds a career profile by owning user UUID.
     *
     * @param userId the owning user UUID
     * @return Optional containing the career profile if found, empty otherwise
     */
    public Optional<CareerProfile> findByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return careerProfileRepository.findByUserId(userId);
    }

    /**
     * Determines whether a user already possesses a master career profile.
     *
     * @param user the user to check
     * @return true if a career profile exists, false otherwise
     */
    public boolean hasProfile(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        return careerProfileRepository.existsByUser(user);
    }

    /**
     * Determines whether a user already possesses a master career profile by user UUID.
     *
     * @param userId the user UUID to check
     * @return true if a career profile exists, false otherwise
     */
    public boolean hasProfile(UUID userId) {
        if (userId == null) {
            return false;
        }
        return careerProfileRepository.existsByUserId(userId);
    }

    private static final Comparator<CareerProfileWorkExperience> WORK_EXP_COMPARATOR = Comparator
            .comparingInt(CareerProfileWorkExperience::getDisplayOrder)
            .thenComparing(CareerProfileWorkExperience::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CareerProfileWorkExperience::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CareerProfileSkill> SKILL_COMPARATOR = Comparator
            .comparingInt(CareerProfileSkill::getDisplayOrder)
            .thenComparing(skill -> skill.getName() != null ? skill.getName().toLowerCase() : "", Comparator.naturalOrder())
            .thenComparing(CareerProfileSkill::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CareerProfileProject> PROJECT_COMPARATOR = Comparator
            .comparingInt(CareerProfileProject::getDisplayOrder)
            .thenComparing(CareerProfileProject::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CareerProfileProject::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CareerProfileEducation> EDUCATION_COMPARATOR = Comparator
            .comparingInt(CareerProfileEducation::getDisplayOrder)
            .thenComparing(CareerProfileEducation::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CareerProfileEducation::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CareerProfileCertification> CERTIFICATION_COMPARATOR = Comparator
            .comparingInt(CareerProfileCertification::getDisplayOrder)
            .thenComparing(CareerProfileCertification::getIssueDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CareerProfileCertification::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CareerProfileAchievement> ACHIEVEMENT_COMPARATOR = Comparator
            .comparingInt(CareerProfileAchievement::getDisplayOrder)
            .thenComparing(CareerProfileAchievement::getAchievementDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CareerProfileAchievement::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Retrieves the complete Master Career Profile and Completeness Summary for the specified eligible user.
     *
     * @param user the owning user
     * @return the complete MasterCareerProfileResponse
     * @throws IllegalArgumentException if user is null or not found
     * @throws IneligibleUserException if user status is ineligible
     * @throws CareerProfileNotFoundException if user has no career profile
     */
    public MasterCareerProfileResponse getMasterCareerProfile(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new CareerProfileNotFoundException("Career profile not found for user: " + persistedUser.getId()));

        return buildMasterCareerProfileResponse(profile);
    }

    /**
     * Retrieves the complete Master Career Profile and Completeness Summary for a user identified by security principal.
     *
     * @param principal the authenticated principal identifier (UUID string or email)
     * @return the complete MasterCareerProfileResponse
     * @throws IllegalArgumentException if principal is null/blank or resolves to no existing user
     * @throws IneligibleUserException if user status is ineligible
     * @throws CareerProfileNotFoundException if user has no career profile
     */
    public MasterCareerProfileResponse getMasterCareerProfile(String principal) {
        User user = resolveEligibleUser(principal);
        return getMasterCareerProfile(user);
    }

    /**
     * Builds a comprehensive Master Career Profile read model aggregating core details,
     * deterministically ordered child collections, and section-level completeness metrics.
     */
    private MasterCareerProfileResponse buildMasterCareerProfileResponse(CareerProfile profile) {
        UUID profileId = profile.getId();

        List<WorkExperienceResponse> workExperiences = (workExperienceRepository != null)
                ? workExperienceRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)
                        .stream()
                        .sorted(WORK_EXP_COMPARATOR)
                        .map(WorkExperienceResponse::from)
                        .toList()
                : List.of();

        List<SkillResponse> skills = (skillRepository != null)
                ? skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileId)
                        .stream()
                        .sorted(SKILL_COMPARATOR)
                        .map(SkillResponse::from)
                        .toList()
                : List.of();

        List<ProjectResponse> projects = (projectRepository != null)
                ? projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)
                        .stream()
                        .sorted(PROJECT_COMPARATOR)
                        .map(ProjectResponse::from)
                        .toList()
                : List.of();

        List<EducationResponse> education = (educationRepository != null)
                ? educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileId)
                        .stream()
                        .sorted(EDUCATION_COMPARATOR)
                        .map(EducationResponse::from)
                        .toList()
                : List.of();

        List<CertificationResponse> certifications = (certificationRepository != null)
                ? certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileId)
                        .stream()
                        .sorted(CERTIFICATION_COMPARATOR)
                        .map(CertificationResponse::from)
                        .toList()
                : List.of();

        List<AchievementResponse> achievements = (achievementRepository != null)
                ? achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profileId)
                        .stream()
                        .sorted(ACHIEVEMENT_COMPARATOR)
                        .map(AchievementResponse::from)
                        .toList()
                : List.of();

        CareerProfileCompletenessResponse completeness = CareerProfileCompletenessResponse.calculate(
                profile,
                workExperiences.size(),
                skills.size(),
                projects.size(),
                education.size(),
                certifications.size(),
                achievements.size()
        );

        return new MasterCareerProfileResponse(
                profile.getId(),
                profile.getProfessionalHeadline(),
                profile.getCurrentTitle(),
                profile.getCurrentCompany(),
                profile.getTotalExperienceMonths(),
                profile.getCurrentLocation(),
                profile.getPreferredWorkLocation(),
                profile.getPreferredWorkMode(),
                profile.getNoticePeriodDays(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                workExperiences,
                skills,
                projects,
                education,
                certifications,
                achievements,
                completeness
        );
    }

    /**
     * Adds a new work experience entry to the authenticated user's Master Career Profile.
     *
     * @param user the owning user
     * @param request the validated work experience details
     * @return the saved and flushed CareerProfileWorkExperience
     */
    @Transactional
    public CareerProfileWorkExperience addWorkExperience(User user, WorkExperienceRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("WorkExperienceRequest must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        validateWorkExperienceRequest(request);

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        CareerProfileWorkExperience experience = new CareerProfileWorkExperience(
                profile,
                request.companyName(),
                request.jobTitle(),
                request.employmentType(),
                request.startDate(),
                request.endDate(),
                Boolean.TRUE.equals(request.currentlyWorking()),
                request.location(),
                request.description(),
                request.displayOrder() != null ? request.displayOrder() : 0
        );

        return workExperienceRepository.saveAndFlush(experience);
    }

    /**
     * Adds a new work experience entry for a user identified by security principal.
     */
    @Transactional
    public CareerProfileWorkExperience addWorkExperience(String principal, WorkExperienceRequest request) {
        User user = resolveEligibleUser(principal);
        return addWorkExperience(user, request);
    }

    /**
     * Retrieves all work experiences belonging to the given user's Master Career Profile,
     * ordered by display order ascending, then start date descending.
     */
    public List<CareerProfileWorkExperience> getWorkExperiences(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        Optional<CareerProfile> profileOptional = careerProfileRepository.findByUserId(persistedUser.getId());
        if (profileOptional.isEmpty()) {
            return List.of();
        }

        return workExperienceRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileOptional.get().getId());
    }

    /**
     * Retrieves all work experiences for a user identified by security principal.
     */
    public List<CareerProfileWorkExperience> getWorkExperiences(String principal) {
        User user = resolveEligibleUser(principal);
        return getWorkExperiences(user);
    }

    /**
     * Updates an existing work experience belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public CareerProfileWorkExperience updateWorkExperience(User user, UUID experienceId, WorkExperienceRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (experienceId == null) {
            throw new IllegalArgumentException("Work experience ID must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("WorkExperienceRequest must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        validateWorkExperienceRequest(request);

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new WorkExperienceNotFoundException("Work experience not found with id: " + experienceId));

        CareerProfileWorkExperience experience = workExperienceRepository.findByIdAndCareerProfileId(experienceId, profile.getId())
                .orElseThrow(() -> new WorkExperienceNotFoundException("Work experience not found with id: " + experienceId));

        experience.setCompanyName(request.companyName());
        experience.setJobTitle(request.jobTitle());
        experience.setEmploymentType(request.employmentType());
        experience.setStartDate(request.startDate());
        experience.setEndDate(request.endDate());
        experience.setCurrentlyWorking(Boolean.TRUE.equals(request.currentlyWorking()));
        experience.setLocation(request.location());
        experience.setDescription(request.description());
        experience.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        return workExperienceRepository.saveAndFlush(experience);
    }

    /**
     * Updates an existing work experience for a user identified by security principal.
     */
    @Transactional
    public CareerProfileWorkExperience updateWorkExperience(String principal, UUID experienceId, WorkExperienceRequest request) {
        User user = resolveEligibleUser(principal);
        return updateWorkExperience(user, experienceId, request);
    }

    /**
     * Deletes an existing work experience belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public void deleteWorkExperience(User user, UUID experienceId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (experienceId == null) {
            throw new IllegalArgumentException("Work experience ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new WorkExperienceNotFoundException("Work experience not found with id: " + experienceId));

        CareerProfileWorkExperience experience = workExperienceRepository.findByIdAndCareerProfileId(experienceId, profile.getId())
                .orElseThrow(() -> new WorkExperienceNotFoundException("Work experience not found with id: " + experienceId));

        workExperienceRepository.delete(experience);
        workExperienceRepository.flush();
    }

    /**
     * Deletes an existing work experience for a user identified by security principal.
     */
    @Transactional
    public void deleteWorkExperience(String principal, UUID experienceId) {
        User user = resolveEligibleUser(principal);
        deleteWorkExperience(user, experienceId);
    }

    /**
     * Resolves and validates an active, eligible User from a security principal string.
     */
    private User resolveEligibleUser(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("Principal identifier must not be null or blank");
        }

        String trimmed = principal.trim();
        Optional<User> userOptional = Optional.empty();

        try {
            UUID userId = UUID.fromString(trimmed);
            userOptional = userRepository.findById(userId);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, treat as email
        }

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByEmailIgnoreCase(trimmed);
        }

        User user = userOptional.orElseThrow(
                () -> new IllegalArgumentException("User not found for principal: " + trimmed)
        );

        if (!eligibilityService.isEligibleForAuthentication(user)) {
            throw new IneligibleUserException(
                    "User with status " + user.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        return user;
    }

    /**
     * Defensively validates work experience request fields and business rules.
     */
    private void validateWorkExperienceRequest(WorkExperienceRequest request) {
        if (request.companyName() == null || request.companyName().isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (request.jobTitle() == null || request.jobTitle().isBlank()) {
            throw new IllegalArgumentException("Job title is required");
        }
        if (request.employmentType() == null) {
            throw new IllegalArgumentException("Employment type is required");
        }
        if (request.startDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (Boolean.TRUE.equals(request.currentlyWorking()) && request.endDate() != null) {
            throw new IllegalArgumentException("End date must be null when currently working is true");
        }
        if (request.displayOrder() != null && request.displayOrder() < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
    }

    /**
     * Adds a new skill/technology entry to the authenticated user's Master Career Profile.
     * Enforces case/whitespace-insensitive duplicate prevention per profile.
     *
     * @param user the owning user
     * @param request the validated skill details
     * @return the saved and flushed CareerProfileSkill
     */
    @Transactional
    public CareerProfileSkill addSkill(User user, SkillRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        validateSkillRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        if (skillRepository.existsByCareerProfileIdAndNormalizedName(profile.getId(), request.name())) {
            throw new DuplicateSkillException("Skill '" + request.name() + "' already exists in this career profile");
        }

        CareerProfileSkill skill = new CareerProfileSkill(
                profile,
                request.name(),
                request.category(),
                request.proficiency(),
                request.yearsOfExperience(),
                request.lastUsedDate(),
                request.displayOrder() != null ? request.displayOrder() : 0
        );

        try {
            return skillRepository.saveAndFlush(skill);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateSkillException("Skill '" + request.name() + "' already exists in this career profile", ex);
        }
    }

    /**
     * Adds a new skill/technology entry for a user identified by security principal.
     */
    @Transactional
    public CareerProfileSkill addSkill(String principal, SkillRequest request) {
        User user = resolveEligibleUser(principal);
        return addSkill(user, request);
    }

    /**
     * Retrieves all skills belonging to the given user's Master Career Profile,
     * ordered by display order ascending, then name ascending.
     */
    public List<CareerProfileSkill> getSkills(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        Optional<CareerProfile> profileOptional = careerProfileRepository.findByUserId(persistedUser.getId());
        if (profileOptional.isEmpty()) {
            return List.of();
        }

        return skillRepository.findByCareerProfileIdOrderByDisplayOrderAscNameAsc(profileOptional.get().getId());
    }

    /**
     * Retrieves all skills for a user identified by security principal.
     */
    public List<CareerProfileSkill> getSkills(String principal) {
        User user = resolveEligibleUser(principal);
        return getSkills(user);
    }

    /**
     * Updates an existing skill belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership and case/whitespace-insensitive duplicate prevention.
     */
    @Transactional
    public CareerProfileSkill updateSkill(User user, UUID skillId, SkillRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID must not be null");
        }
        validateSkillRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with id: " + skillId));

        CareerProfileSkill skill = skillRepository.findByIdAndCareerProfileId(skillId, profile.getId())
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with id: " + skillId));

        if (skillRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(profile.getId(), request.name(), skillId)) {
            throw new DuplicateSkillException("Skill '" + request.name() + "' already exists in this career profile");
        }

        skill.setName(request.name());
        skill.setCategory(request.category());
        skill.setProficiency(request.proficiency());
        skill.setYearsOfExperience(request.yearsOfExperience());
        skill.setLastUsedDate(request.lastUsedDate());
        skill.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        try {
            return skillRepository.saveAndFlush(skill);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateSkillException("Skill '" + request.name() + "' already exists in this career profile", ex);
        }
    }

    /**
     * Updates an existing skill for a user identified by security principal.
     */
    @Transactional
    public CareerProfileSkill updateSkill(String principal, UUID skillId, SkillRequest request) {
        User user = resolveEligibleUser(principal);
        return updateSkill(user, skillId, request);
    }

    /**
     * Deletes an existing skill belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public void deleteSkill(User user, UUID skillId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with id: " + skillId));

        CareerProfileSkill skill = skillRepository.findByIdAndCareerProfileId(skillId, profile.getId())
                .orElseThrow(() -> new SkillNotFoundException("Skill not found with id: " + skillId));

        skillRepository.delete(skill);
        skillRepository.flush();
    }

    /**
     * Deletes an existing skill for a user identified by security principal.
     */
    @Transactional
    public void deleteSkill(String principal, UUID skillId) {
        User user = resolveEligibleUser(principal);
        deleteSkill(user, skillId);
    }

    /**
     * Defensively validates skill request fields and constraints.
     */
    private void validateSkillRequest(SkillRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SkillRequest must not be null");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }
        if (request.category() == null) {
            throw new IllegalArgumentException("Category is required");
        }
        if (request.proficiency() == null) {
            throw new IllegalArgumentException("Proficiency is required");
        }
        if (request.yearsOfExperience() != null && request.yearsOfExperience().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Years of experience must be greater than or equal to 0");
        }
        if (request.displayOrder() != null && request.displayOrder() < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
    }

    /**
     * Adds a new project entry to the authenticated user's Master Career Profile.
     * Enforces case/whitespace-insensitive duplicate prevention per profile.
     *
     * @param user the owning user
     * @param request the validated project details
     * @return the saved and flushed CareerProfileProject
     */
    @Transactional
    public CareerProfileProject addProject(User user, ProjectRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        validateProjectRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        if (projectRepository.existsByCareerProfileIdAndNormalizedName(profile.getId(), request.projectName())) {
            throw new DuplicateProjectException("A project with name '" + request.projectName() + "' already exists in this career profile");
        }

        CareerProfileProject project = new CareerProfileProject(
                profile,
                request.projectName(),
                request.projectType(),
                request.role(),
                request.description(),
                request.startDate(),
                request.endDate(),
                Boolean.TRUE.equals(request.currentlyActive()),
                request.projectUrl(),
                request.displayOrder() != null ? request.displayOrder() : 0
        );

        try {
            return projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateProjectException("A project with name '" + request.projectName() + "' already exists in this career profile", ex);
        }
    }

    /**
     * Adds a new project entry for a user identified by security principal.
     */
    @Transactional
    public CareerProfileProject addProject(String principal, ProjectRequest request) {
        User user = resolveEligibleUser(principal);
        return addProject(user, request);
    }

    /**
     * Retrieves all projects belonging to the given user's Master Career Profile,
     * ordered by display order ascending, then start date descending.
     */
    public List<CareerProfileProject> getProjects(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        Optional<CareerProfile> profileOptional = careerProfileRepository.findByUserId(persistedUser.getId());
        if (profileOptional.isEmpty()) {
            return List.of();
        }

        return projectRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileOptional.get().getId());
    }

    /**
     * Retrieves all projects for a user identified by security principal.
     */
    public List<CareerProfileProject> getProjects(String principal) {
        User user = resolveEligibleUser(principal);
        return getProjects(user);
    }

    /**
     * Retrieves a single project by ID for the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    public CareerProfileProject getProject(User user, UUID projectId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        return projectRepository.findByIdAndCareerProfileId(projectId, profile.getId())
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
    }

    /**
     * Retrieves a single project by ID for a user identified by security principal.
     */
    public CareerProfileProject getProject(String principal, UUID projectId) {
        User user = resolveEligibleUser(principal);
        return getProject(user, projectId);
    }

    /**
     * Updates an existing project belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership and case/whitespace-insensitive duplicate prevention.
     */
    @Transactional
    public CareerProfileProject updateProject(User user, UUID projectId, ProjectRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }
        validateProjectRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        CareerProfileProject project = projectRepository.findByIdAndCareerProfileId(projectId, profile.getId())
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        if (projectRepository.existsByCareerProfileIdAndNormalizedNameExcludingId(profile.getId(), request.projectName(), projectId)) {
            throw new DuplicateProjectException("A project with name '" + request.projectName() + "' already exists in this career profile");
        }

        project.setProjectName(request.projectName());
        project.setProjectType(request.projectType());
        project.setRole(request.role());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setCurrentlyActive(Boolean.TRUE.equals(request.currentlyActive()));
        project.setProjectUrl(request.projectUrl());
        project.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        try {
            return projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateProjectException("A project with name '" + request.projectName() + "' already exists in this career profile", ex);
        }
    }

    /**
     * Updates an existing project for a user identified by security principal.
     */
    @Transactional
    public CareerProfileProject updateProject(String principal, UUID projectId, ProjectRequest request) {
        User user = resolveEligibleUser(principal);
        return updateProject(user, projectId, request);
    }

    /**
     * Deletes an existing project belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public void deleteProject(User user, UUID projectId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        CareerProfileProject project = projectRepository.findByIdAndCareerProfileId(projectId, profile.getId())
                .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

        projectRepository.delete(project);
        projectRepository.flush();
    }

    /**
     * Deletes an existing project for a user identified by security principal.
     */
    @Transactional
    public void deleteProject(String principal, UUID projectId) {
        User user = resolveEligibleUser(principal);
        deleteProject(user, projectId);
    }

    /**
     * Defensively validates project request fields and business constraints.
     */
    private void validateProjectRequest(ProjectRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ProjectRequest must not be null");
        }
        if (request.projectName() == null || request.projectName().isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        if (request.projectType() == null) {
            throw new IllegalArgumentException("Project type is required");
        }
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (Boolean.TRUE.equals(request.currentlyActive()) && request.endDate() != null) {
            throw new IllegalArgumentException("End date must be null when currently active is true");
        }
        if (request.projectUrl() != null && !request.projectUrl().isBlank()) {
            String url = request.projectUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalArgumentException("Project URL must be a valid HTTP or HTTPS URL");
            }
        }
        if (request.displayOrder() != null && request.displayOrder() < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
    }

    // =========================================================================
    // Master Career Profile — Education Foundation
    // =========================================================================

    /**
     * Adds a new education record to the user's Master Career Profile.
     * Enforces active user eligibility, profile existence/auto-provisioning,
     * duplicate prevention, and date validation constraints.
     *
     * @param user    the owning user
     * @param request the validated education details
     * @return the saved and flushed CareerProfileEducation
     */
    @Transactional
    public CareerProfileEducation addEducation(User user, EducationRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        validateEducationRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        if (educationRepository.existsByCareerProfileIdAndNormalizedComposite(
                profile.getId(),
                request.institutionName(),
                request.degree(),
                request.fieldOfStudy())) {
            throw new DuplicateEducationException(
                    "An education record with the same institution, degree, and field of study already exists in this career profile"
            );
        }

        CareerProfileEducation education = new CareerProfileEducation(
                profile,
                request.institutionName(),
                request.degree(),
                request.fieldOfStudy(),
                request.educationLevel(),
                request.startDate(),
                request.endDate(),
                Boolean.TRUE.equals(request.currentlyStudying()),
                request.grade(),
                request.location(),
                request.description(),
                request.displayOrder() != null ? request.displayOrder() : 0
        );

        try {
            return educationRepository.saveAndFlush(education);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEducationException(
                    "An education record with the same institution, degree, and field of study already exists in this career profile",
                    ex
            );
        }
    }

    /**
     * Adds a new education entry for a user identified by security principal.
     */
    @Transactional
    public CareerProfileEducation addEducation(String principal, EducationRequest request) {
        User user = resolveEligibleUser(principal);
        return addEducation(user, request);
    }

    /**
     * Retrieves all education records belonging to the given user's Master Career Profile,
     * ordered by display order ascending, then start date descending.
     */
    public List<CareerProfileEducation> getEducation(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        Optional<CareerProfile> profileOptional = careerProfileRepository.findByUserId(persistedUser.getId());
        if (profileOptional.isEmpty()) {
            return List.of();
        }

        return educationRepository.findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(profileOptional.get().getId());
    }

    /**
     * Retrieves all education records for a user identified by security principal.
     */
    public List<CareerProfileEducation> getEducation(String principal) {
        User user = resolveEligibleUser(principal);
        return getEducation(user);
    }

    /**
     * Retrieves a single education record by ID for the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    public CareerProfileEducation getEducation(User user, UUID educationId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (educationId == null) {
            throw new IllegalArgumentException("Education ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new EducationNotFoundException("Education record not found with id: " + educationId));

        return educationRepository.findByIdAndCareerProfileId(educationId, profile.getId())
                .orElseThrow(() -> new EducationNotFoundException("Education record not found with id: " + educationId));
    }

    /**
     * Retrieves a single education record by ID for a user identified by security principal.
     */
    public CareerProfileEducation getEducation(String principal, UUID educationId) {
        User user = resolveEligibleUser(principal);
        return getEducation(user, educationId);
    }

    /**
     * Alias for getEducation(User, UUID) to satisfy getEducationById naming conventions.
     */
    public CareerProfileEducation getEducationById(User user, UUID educationId) {
        return getEducation(user, educationId);
    }

    /**
     * Alias for getEducation(String, UUID) to satisfy getEducationById naming conventions.
     */
    public CareerProfileEducation getEducationById(String principal, UUID educationId) {
        return getEducation(principal, educationId);
    }

    /**
     * Updates an existing education record belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership and composite duplicate prevention.
     */
    @Transactional
    public CareerProfileEducation updateEducation(User user, UUID educationId, EducationRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (educationId == null) {
            throw new IllegalArgumentException("Education ID must not be null");
        }
        validateEducationRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new EducationNotFoundException("Education record not found with id: " + educationId));

        CareerProfileEducation education = educationRepository.findByIdAndCareerProfileId(educationId, profile.getId())
                .orElseThrow(() -> new EducationNotFoundException("Education record not found with id: " + educationId));

        if (educationRepository.existsByCareerProfileIdAndNormalizedCompositeExcludingId(
                profile.getId(),
                request.institutionName(),
                request.degree(),
                request.fieldOfStudy(),
                educationId)) {
            throw new DuplicateEducationException(
                    "An education record with the same institution, degree, and field of study already exists in this career profile"
            );
        }

        education.setInstitutionName(request.institutionName());
        education.setDegree(request.degree());
        education.setFieldOfStudy(request.fieldOfStudy());
        education.setEducationLevel(request.educationLevel());
        education.setStartDate(request.startDate());
        education.setEndDate(request.endDate());
        education.setCurrentlyStudying(Boolean.TRUE.equals(request.currentlyStudying()));
        education.setGrade(request.grade());
        education.setLocation(request.location());
        education.setDescription(request.description());
        education.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        try {
            return educationRepository.saveAndFlush(education);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEducationException(
                    "An education record with the same institution, degree, and field of study already exists in this career profile",
                    ex
            );
        }
    }

    /**
     * Updates an existing education record for a user identified by security principal.
     */
    @Transactional
    public CareerProfileEducation updateEducation(String principal, UUID educationId, EducationRequest request) {
        User user = resolveEligibleUser(principal);
        return updateEducation(user, educationId, request);
    }

    /**
     * Deletes an existing education record belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public void deleteEducation(User user, UUID educationId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (educationId == null) {
            throw new IllegalArgumentException("Education ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new EducationNotFoundException("Education record not found with id: " + educationId));

        CareerProfileEducation education = educationRepository.findByIdAndCareerProfileId(educationId, profile.getId())
                .orElseThrow(() -> new EducationNotFoundException("Education record not found with id: " + educationId));

        educationRepository.delete(education);
        educationRepository.flush();
    }

    /**
     * Deletes an existing education record for a user identified by security principal.
     */
    @Transactional
    public void deleteEducation(String principal, UUID educationId) {
        User user = resolveEligibleUser(principal);
        deleteEducation(user, educationId);
    }

    /**
     * Defensively validates education request fields and business constraints.
     */
    private void validateEducationRequest(EducationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("EducationRequest must not be null");
        }
        if (request.institutionName() == null || request.institutionName().isBlank()) {
            throw new IllegalArgumentException("Institution name is required");
        }
        if (request.educationLevel() == null) {
            throw new IllegalArgumentException("Education level is required");
        }
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (Boolean.TRUE.equals(request.currentlyStudying()) && request.endDate() != null) {
            throw new IllegalArgumentException("End date must be null when currently studying is true");
        }
        if (request.displayOrder() != null && request.displayOrder() < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
    }

    // =========================================================================
    // Master Career Profile — Certifications Foundation
    // =========================================================================

    /**
     * Adds a new certification record to the user's Master Career Profile.
     * Enforces active user eligibility, profile existence/auto-provisioning,
     * duplicate prevention, and date validation constraints.
     *
     * @param user    the owning user
     * @param request the validated certification details
     * @return the saved and flushed CareerProfileCertification
     */
    @Transactional
    public CareerProfileCertification addCertification(User user, CertificationRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        validateCertificationRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        if (request.credentialId() != null && !request.credentialId().isBlank()) {
            if (certificationRepository.existsByCareerProfileIdAndNormalizedCredentialId(profile.getId(), request.credentialId())) {
                throw new DuplicateCertificationException(
                        "A certification with credential ID '" + request.credentialId() + "' already exists in this career profile"
                );
            }
        } else {
            if (certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganization(
                    profile.getId(), request.certificationName(), request.issuingOrganization())) {
                throw new DuplicateCertificationException(
                        "A certification with name '" + request.certificationName() + "' and issuing organization '" + request.issuingOrganization() + "' already exists in this career profile"
                );
            }
        }

        CareerProfileCertification certification = new CareerProfileCertification(
                profile,
                request.certificationName(),
                request.issuingOrganization(),
                request.credentialId(),
                request.credentialUrl(),
                request.issueDate(),
                request.expirationDate(),
                Boolean.TRUE.equals(request.doesNotExpire()),
                request.description(),
                request.displayOrder() != null ? request.displayOrder() : 0
        );

        try {
            return certificationRepository.saveAndFlush(certification);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCertificationException(
                    "A conflicting certification already exists in this career profile",
                    ex
            );
        }
    }

    /**
     * Adds a new certification entry for a user identified by security principal.
     */
    @Transactional
    public CareerProfileCertification addCertification(String principal, CertificationRequest request) {
        User user = resolveEligibleUser(principal);
        return addCertification(user, request);
    }

    /**
     * Retrieves all certifications belonging to the given user's Master Career Profile,
     * ordered by display order ascending, then issue date descending.
     */
    public List<CareerProfileCertification> getCertifications(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        Optional<CareerProfile> profileOptional = careerProfileRepository.findByUserId(persistedUser.getId());
        if (profileOptional.isEmpty()) {
            return List.of();
        }

        return certificationRepository.findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(profileOptional.get().getId());
    }

    /**
     * Retrieves all certifications for a user identified by security principal.
     */
    public List<CareerProfileCertification> getCertifications(String principal) {
        User user = resolveEligibleUser(principal);
        return getCertifications(user);
    }

    /**
     * Retrieves a single certification record by ID for the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    public CareerProfileCertification getCertification(User user, UUID certificationId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (certificationId == null) {
            throw new IllegalArgumentException("Certification ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new CertificationNotFoundException("Certification record not found with id: " + certificationId));

        return certificationRepository.findByIdAndCareerProfileId(certificationId, profile.getId())
                .orElseThrow(() -> new CertificationNotFoundException("Certification record not found with id: " + certificationId));
    }

    /**
     * Retrieves a single certification record by ID for a user identified by security principal.
     */
    public CareerProfileCertification getCertification(String principal, UUID certificationId) {
        User user = resolveEligibleUser(principal);
        return getCertification(user, certificationId);
    }

    /**
     * Alias for getCertification(User, UUID) to satisfy getCertificationById naming conventions.
     */
    public CareerProfileCertification getCertificationById(User user, UUID certificationId) {
        return getCertification(user, certificationId);
    }

    /**
     * Alias for getCertification(String, UUID) to satisfy getCertificationById naming conventions.
     */
    public CareerProfileCertification getCertificationById(String principal, UUID certificationId) {
        return getCertification(principal, certificationId);
    }

    /**
     * Updates an existing certification record belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership and composite duplicate prevention.
     */
    @Transactional
    public CareerProfileCertification updateCertification(User user, UUID certificationId, CertificationRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (certificationId == null) {
            throw new IllegalArgumentException("Certification ID must not be null");
        }
        validateCertificationRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new CertificationNotFoundException("Certification record not found with id: " + certificationId));

        CareerProfileCertification certification = certificationRepository.findByIdAndCareerProfileId(certificationId, profile.getId())
                .orElseThrow(() -> new CertificationNotFoundException("Certification record not found with id: " + certificationId));

        if (request.credentialId() != null && !request.credentialId().isBlank()) {
            if (certificationRepository.existsByCareerProfileIdAndNormalizedCredentialIdExcludingId(
                    profile.getId(), request.credentialId(), certificationId)) {
                throw new DuplicateCertificationException(
                        "A certification with credential ID '" + request.credentialId() + "' already exists in this career profile"
                );
            }
        } else {
            if (certificationRepository.existsByCareerProfileIdAndNormalizedNameAndOrganizationExcludingId(
                    profile.getId(), request.certificationName(), request.issuingOrganization(), certificationId)) {
                throw new DuplicateCertificationException(
                        "A certification with name '" + request.certificationName() + "' and issuing organization '" + request.issuingOrganization() + "' already exists in this career profile"
                );
            }
        }

        certification.setCertificationName(request.certificationName());
        certification.setIssuingOrganization(request.issuingOrganization());
        certification.setCredentialId(request.credentialId());
        certification.setCredentialUrl(request.credentialUrl());
        certification.setIssueDate(request.issueDate());
        certification.setExpirationDate(request.expirationDate());
        certification.setDoesNotExpire(Boolean.TRUE.equals(request.doesNotExpire()));
        certification.setDescription(request.description());
        certification.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        try {
            return certificationRepository.saveAndFlush(certification);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCertificationException(
                    "A conflicting certification already exists in this career profile",
                    ex
            );
        }
    }

    /**
     * Updates an existing certification record for a user identified by security principal.
     */
    @Transactional
    public CareerProfileCertification updateCertification(String principal, UUID certificationId, CertificationRequest request) {
        User user = resolveEligibleUser(principal);
        return updateCertification(user, certificationId, request);
    }

    /**
     * Deletes an existing certification record belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public void deleteCertification(User user, UUID certificationId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (certificationId == null) {
            throw new IllegalArgumentException("Certification ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new CertificationNotFoundException("Certification record not found with id: " + certificationId));

        CareerProfileCertification certification = certificationRepository.findByIdAndCareerProfileId(certificationId, profile.getId())
                .orElseThrow(() -> new CertificationNotFoundException("Certification record not found with id: " + certificationId));

        certificationRepository.delete(certification);
        certificationRepository.flush();
    }

    /**
     * Deletes an existing certification record for a user identified by security principal.
     */
    @Transactional
    public void deleteCertification(String principal, UUID certificationId) {
        User user = resolveEligibleUser(principal);
        deleteCertification(user, certificationId);
    }

    /**
     * Defensively validates certification request fields and business constraints.
     */
    private void validateCertificationRequest(CertificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CertificationRequest must not be null");
        }
        if (request.certificationName() == null || request.certificationName().isBlank()) {
            throw new IllegalArgumentException("Certification name is required");
        }
        if (request.issuingOrganization() == null || request.issuingOrganization().isBlank()) {
            throw new IllegalArgumentException("Issuing organization is required");
        }
        if (request.credentialUrl() != null && !request.credentialUrl().isBlank()) {
            String url = request.credentialUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalArgumentException("Credential URL must be a valid HTTP or HTTPS URL");
            }
        }
        if (request.issueDate() != null && request.expirationDate() != null && request.expirationDate().isBefore(request.issueDate())) {
            throw new IllegalArgumentException("Expiration date cannot be before issue date");
        }
        if (Boolean.TRUE.equals(request.doesNotExpire()) && request.expirationDate() != null) {
            throw new IllegalArgumentException("Expiration date must be null when does not expire is true");
        }
        if (request.displayOrder() != null && request.displayOrder() < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
    }

    // =========================================================================
    // Master Career Profile — Achievements Foundation
    // =========================================================================

    /**
     * Adds a new achievement record to the user's Master Career Profile.
     * Enforces active user eligibility, profile existence/auto-provisioning,
     * duplicate prevention, and display ordering.
     *
     * @param user    the owning user
     * @param request the validated achievement details
     * @return the saved and flushed CareerProfileAchievement
     */
    @Transactional
    public CareerProfileAchievement addAchievement(User user, AchievementRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        validateAchievementRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        if (achievementRepository.existsByCompositeNormalized(
                profile.getId(),
                request.title(),
                request.achievementType(),
                request.achievementDate(),
                request.issuingOrganization())) {
            throw new DuplicateAchievementException(
                    "An achievement with title '" + request.title() + "' already exists in this career profile"
            );
        }

        CareerProfileAchievement achievement = new CareerProfileAchievement(
                profile,
                request.title(),
                request.achievementType(),
                request.issuingOrganization(),
                request.achievementDate(),
                request.description(),
                request.url(),
                request.displayOrder() != null ? request.displayOrder() : 0
        );

        try {
            return achievementRepository.saveAndFlush(achievement);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAchievementException(
                    "A conflicting achievement already exists in this career profile",
                    ex
            );
        }
    }

    /**
     * Adds a new achievement entry for a user identified by security principal.
     */
    @Transactional
    public CareerProfileAchievement addAchievement(String principal, AchievementRequest request) {
        User user = resolveEligibleUser(principal);
        return addAchievement(user, request);
    }

    /**
     * Retrieves all achievements belonging to the given user's Master Career Profile,
     * ordered by display order ascending, then achievement date descending.
     */
    @Transactional(readOnly = true)
    public List<CareerProfileAchievement> getAchievements(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to view a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseGet(() -> createProfile(persistedUser));

        return achievementRepository.findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(profile.getId());
    }

    /**
     * Retrieves all achievements for a user identified by security principal.
     */
    @Transactional(readOnly = true)
    public List<CareerProfileAchievement> getAchievements(String principal) {
        User user = resolveEligibleUser(principal);
        return getAchievements(user);
    }

    /**
     * Retrieves a single achievement record by ID for the specified user.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional(readOnly = true)
    public CareerProfileAchievement getAchievement(User user, UUID achievementId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (achievementId == null) {
            throw new IllegalArgumentException("Achievement ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to view a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new AchievementNotFoundException("Achievement record not found with id: " + achievementId));

        return achievementRepository.findByIdAndCareerProfileId(achievementId, profile.getId())
                .orElseThrow(() -> new AchievementNotFoundException("Achievement record not found with id: " + achievementId));
    }

    /**
     * Retrieves a single achievement record by ID for a user identified by security principal.
     */
    @Transactional(readOnly = true)
    public CareerProfileAchievement getAchievement(String principal, UUID achievementId) {
        User user = resolveEligibleUser(principal);
        return getAchievement(user, achievementId);
    }

    /**
     * Alias for getAchievement to support controller convention.
     */
    @Transactional(readOnly = true)
    public CareerProfileAchievement getAchievementById(String principal, UUID achievementId) {
        return getAchievement(principal, achievementId);
    }

    /**
     * Updates an existing achievement record belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public CareerProfileAchievement updateAchievement(User user, UUID achievementId, AchievementRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (achievementId == null) {
            throw new IllegalArgumentException("Achievement ID must not be null");
        }
        validateAchievementRequest(request);

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new AchievementNotFoundException("Achievement record not found with id: " + achievementId));

        CareerProfileAchievement achievement = achievementRepository.findByIdAndCareerProfileId(achievementId, profile.getId())
                .orElseThrow(() -> new AchievementNotFoundException("Achievement record not found with id: " + achievementId));

        if (achievementRepository.existsByCompositeNormalizedExcludingId(
                profile.getId(),
                request.title(),
                request.achievementType(),
                request.achievementDate(),
                request.issuingOrganization(),
                achievementId)) {
            throw new DuplicateAchievementException(
                    "An achievement with title '" + request.title() + "' already exists in this career profile"
            );
        }

        achievement.setTitle(request.title());
        achievement.setAchievementType(request.achievementType());
        achievement.setIssuingOrganization(request.issuingOrganization());
        achievement.setAchievementDate(request.achievementDate());
        achievement.setDescription(request.description());
        achievement.setUrl(request.url());
        achievement.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        try {
            return achievementRepository.saveAndFlush(achievement);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAchievementException(
                    "A conflicting achievement already exists in this career profile",
                    ex
            );
        }
    }

    /**
     * Updates an existing achievement for a user identified by security principal.
     */
    @Transactional
    public CareerProfileAchievement updateAchievement(String principal, UUID achievementId, AchievementRequest request) {
        User user = resolveEligibleUser(principal);
        return updateAchievement(user, achievementId, request);
    }

    /**
     * Deletes an existing achievement record belonging to the given user's Master Career Profile.
     * Enforces strict multi-tenant ownership: foreign records trigger a 404 Not Found exception.
     */
    @Transactional
    public void deleteAchievement(User user, UUID achievementId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid persisted user is required");
        }
        if (achievementId == null) {
            throw new IllegalArgumentException("Achievement ID must not be null");
        }

        User persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + user.getId()));

        if (!eligibilityService.isEligibleForAuthentication(persistedUser)) {
            throw new IneligibleUserException(
                    "User with status " + persistedUser.getStatus() + " is not eligible to manage a master career profile"
            );
        }

        CareerProfile profile = careerProfileRepository.findByUserId(persistedUser.getId())
                .orElseThrow(() -> new AchievementNotFoundException("Achievement record not found with id: " + achievementId));

        CareerProfileAchievement achievement = achievementRepository.findByIdAndCareerProfileId(achievementId, profile.getId())
                .orElseThrow(() -> new AchievementNotFoundException("Achievement record not found with id: " + achievementId));

        achievementRepository.delete(achievement);
        achievementRepository.flush();
    }

    /**
     * Deletes an existing achievement record for a user identified by security principal.
     */
    @Transactional
    public void deleteAchievement(String principal, UUID achievementId) {
        User user = resolveEligibleUser(principal);
        deleteAchievement(user, achievementId);
    }

    /**
     * Defensively validates achievement request fields and business constraints.
     */
    private void validateAchievementRequest(AchievementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AchievementRequest must not be null");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (request.achievementType() == null) {
            throw new IllegalArgumentException("Achievement type is required");
        }
        if (request.url() != null && !request.url().isBlank()) {
            String url = request.url().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalArgumentException("URL must be a valid HTTP or HTTPS URL");
            }
        }
        if (request.displayOrder() != null && request.displayOrder() < 0) {
            throw new IllegalArgumentException("Display order must be greater than or equal to 0");
        }
    }
}

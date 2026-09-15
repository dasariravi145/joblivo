package com.joblivo.profile;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller establishing the Master Career Profile creation contract.
 * Strictly derives profile ownership from the authenticated security principal.
 * Never accepts client-supplied user IDs or unverified identity parameters.
 */
@RestController
@RequestMapping("/api/v1/career-profile")
public class CareerProfileController {

    private final CareerProfileService careerProfileService;

    public CareerProfileController(CareerProfileService careerProfileService) {
        this.careerProfileService = Objects.requireNonNull(careerProfileService, "careerProfileService must not be null");
    }

    /**
     * Creates a master career profile for the authenticated principal.
     *
     * @param authentication the authenticated security context principal
     * @return 201 Created with Location header and safe CareerProfileResponse
     * @throws ResponseStatusException if the caller is unauthenticated or anonymous
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CareerProfileResponse> createProfile(Authentication authentication) {
        validateAuthentication(authentication);

        CareerProfile profile = careerProfileService.createProfileForPrincipal(authentication.getName());
        CareerProfileResponse response = CareerProfileResponse.fromEntity(profile);

        URI location = URI.create("/api/v1/career-profile/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves the complete Master Career Profile and Completeness Summary for the authenticated principal.
     * Strictly derives ownership from the authenticated security context.
     *
     * @param authentication the authenticated security context principal
     * @return 200 OK with complete MasterCareerProfileResponse
     * @throws ResponseStatusException if the caller is unauthenticated or anonymous
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MasterCareerProfileResponse> getMasterCareerProfile(Authentication authentication) {
        validateAuthentication(authentication);

        MasterCareerProfileResponse response = careerProfileService.getMasterCareerProfile(authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the master career profile core details for the authenticated principal.
     *
     * @param authentication the authenticated security context principal
     * @param request the validated update payload
     * @return 200 OK with safe CareerProfileResponse containing updated fields
     * @throws ResponseStatusException if the caller is unauthenticated or anonymous
     */
    @PutMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CareerProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateCareerProfileRequest request) {
        validateAuthentication(authentication);

        CareerProfile updated = careerProfileService.updateProfileForPrincipal(authentication.getName(), request);
        return ResponseEntity.ok(CareerProfileResponse.fromEntity(updated));
    }

    /**
     * Creates a new work experience record for the authenticated principal's Master Career Profile.
     */
    @PostMapping(
            value = "/work-experiences",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<WorkExperienceResponse> createWorkExperience(
            Authentication authentication,
            @Valid @RequestBody WorkExperienceRequest request) {
        validateAuthentication(authentication);

        CareerProfileWorkExperience experience = careerProfileService.addWorkExperience(authentication.getName(), request);
        WorkExperienceResponse response = WorkExperienceResponse.from(experience);

        URI location = URI.create("/api/v1/career-profile/work-experiences/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves all work experiences for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/work-experiences",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<WorkExperienceResponse>> getWorkExperiences(Authentication authentication) {
        validateAuthentication(authentication);

        List<WorkExperienceResponse> responses = careerProfileService.getWorkExperiences(authentication.getName())
                .stream()
                .map(WorkExperienceResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Updates an existing work experience record for the authenticated principal's Master Career Profile.
     */
    @PutMapping(
            value = "/work-experiences/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<WorkExperienceResponse> updateWorkExperience(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody WorkExperienceRequest request) {
        validateAuthentication(authentication);

        CareerProfileWorkExperience updated = careerProfileService.updateWorkExperience(authentication.getName(), id, request);
        return ResponseEntity.ok(WorkExperienceResponse.from(updated));
    }

    /**
     * Deletes an existing work experience record for the authenticated principal's Master Career Profile.
     */
    @DeleteMapping("/work-experiences/{id}")
    public ResponseEntity<Void> deleteWorkExperience(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        careerProfileService.deleteWorkExperience(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Adds a new skill/technology entry to the authenticated principal's Master Career Profile.
     */
    @PostMapping(
            value = "/skills",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<SkillResponse> createSkill(
            Authentication authentication,
            @Valid @RequestBody SkillRequest request) {
        validateAuthentication(authentication);

        CareerProfileSkill skill = careerProfileService.addSkill(authentication.getName(), request);
        SkillResponse response = SkillResponse.from(skill);

        URI location = URI.create("/api/v1/career-profile/skills/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves all skills/technologies for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/skills",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<SkillResponse>> getSkills(Authentication authentication) {
        validateAuthentication(authentication);

        List<SkillResponse> responses = careerProfileService.getSkills(authentication.getName())
                .stream()
                .map(SkillResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Updates an existing skill/technology record for the authenticated principal's Master Career Profile.
     */
    @PutMapping(
            value = "/skills/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<SkillResponse> updateSkill(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody SkillRequest request) {
        validateAuthentication(authentication);

        CareerProfileSkill updated = careerProfileService.updateSkill(authentication.getName(), id, request);
        return ResponseEntity.ok(SkillResponse.from(updated));
    }

    /**
     * Deletes an existing skill/technology record for the authenticated principal's Master Career Profile.
     */
    @DeleteMapping("/skills/{id}")
    public ResponseEntity<Void> deleteSkill(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        careerProfileService.deleteSkill(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates a new project record for the authenticated principal's Master Career Profile.
     */
    @PostMapping(
            value = "/projects",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ProjectResponse> createProject(
            Authentication authentication,
            @Valid @RequestBody ProjectRequest request) {
        validateAuthentication(authentication);

        CareerProfileProject project = careerProfileService.addProject(authentication.getName(), request);
        ProjectResponse response = ProjectResponse.from(project);

        URI location = URI.create("/api/v1/career-profile/projects/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves all projects for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/projects",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<ProjectResponse>> getProjects(Authentication authentication) {
        validateAuthentication(authentication);

        List<ProjectResponse> responses = careerProfileService.getProjects(authentication.getName())
                .stream()
                .map(ProjectResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a single project by ID for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/projects/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        CareerProfileProject project = careerProfileService.getProject(authentication.getName(), id);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    /**
     * Updates an existing project record for the authenticated principal's Master Career Profile.
     */
    @PutMapping(
            value = "/projects/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody ProjectRequest request) {
        validateAuthentication(authentication);

        CareerProfileProject updated = careerProfileService.updateProject(authentication.getName(), id, request);
        return ResponseEntity.ok(ProjectResponse.from(updated));
    }

    /**
     * Deletes an existing project record for the authenticated principal's Master Career Profile.
     */
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        careerProfileService.deleteProject(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates a new education record for the authenticated principal's Master Career Profile.
     */
    @PostMapping(
            value = "/education",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EducationResponse> createEducation(
            Authentication authentication,
            @Valid @RequestBody EducationRequest request) {
        validateAuthentication(authentication);

        CareerProfileEducation education = careerProfileService.addEducation(authentication.getName(), request);
        EducationResponse response = EducationResponse.from(education);

        URI location = URI.create("/api/v1/career-profile/education/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves all education records for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/education",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<EducationResponse>> getEducation(Authentication authentication) {
        validateAuthentication(authentication);

        List<EducationResponse> responses = careerProfileService.getEducation(authentication.getName())
                .stream()
                .map(EducationResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a single education record by ID for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/education/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EducationResponse> getEducationById(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        CareerProfileEducation education = careerProfileService.getEducation(authentication.getName(), id);
        return ResponseEntity.ok(EducationResponse.from(education));
    }

    /**
     * Updates an existing education record for the authenticated principal's Master Career Profile.
     */
    @PutMapping(
            value = "/education/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EducationResponse> updateEducation(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody EducationRequest request) {
        validateAuthentication(authentication);

        CareerProfileEducation updated = careerProfileService.updateEducation(authentication.getName(), id, request);
        return ResponseEntity.ok(EducationResponse.from(updated));
    }

    /**
     * Deletes an existing education record for the authenticated principal's Master Career Profile.
     */
    @DeleteMapping("/education/{id}")
    public ResponseEntity<Void> deleteEducation(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        careerProfileService.deleteEducation(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates a new certification record for the authenticated principal's Master Career Profile.
     */
    @PostMapping(
            value = "/certifications",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CertificationResponse> createCertification(
            Authentication authentication,
            @Valid @RequestBody CertificationRequest request) {
        validateAuthentication(authentication);

        CareerProfileCertification certification = careerProfileService.addCertification(authentication.getName(), request);
        CertificationResponse response = CertificationResponse.from(certification);

        URI location = URI.create("/api/v1/career-profile/certifications/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves all certifications for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/certifications",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<CertificationResponse>> getCertifications(Authentication authentication) {
        validateAuthentication(authentication);

        List<CertificationResponse> responses = careerProfileService.getCertifications(authentication.getName())
                .stream()
                .map(CertificationResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a single certification record by ID for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/certifications/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CertificationResponse> getCertificationById(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        CareerProfileCertification certification = careerProfileService.getCertification(authentication.getName(), id);
        return ResponseEntity.ok(CertificationResponse.from(certification));
    }

    /**
     * Updates an existing certification record for the authenticated principal's Master Career Profile.
     */
    @PutMapping(
            value = "/certifications/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CertificationResponse> updateCertification(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody CertificationRequest request) {
        validateAuthentication(authentication);

        CareerProfileCertification updated = careerProfileService.updateCertification(authentication.getName(), id, request);
        return ResponseEntity.ok(CertificationResponse.from(updated));
    }

    /**
     * Deletes an existing certification record for the authenticated principal's Master Career Profile.
     */
    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<Void> deleteCertification(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        careerProfileService.deleteCertification(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates a new achievement record for the authenticated principal's Master Career Profile.
     */
    @PostMapping(
            value = "/achievements",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AchievementResponse> createAchievement(
            Authentication authentication,
            @Valid @RequestBody AchievementRequest request) {
        validateAuthentication(authentication);

        CareerProfileAchievement achievement = careerProfileService.addAchievement(authentication.getName(), request);
        AchievementResponse response = AchievementResponse.from(achievement);

        URI location = URI.create("/api/v1/career-profile/achievements/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves all achievements for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/achievements",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<AchievementResponse>> getAchievements(Authentication authentication) {
        validateAuthentication(authentication);

        List<AchievementResponse> responses = careerProfileService.getAchievements(authentication.getName())
                .stream()
                .map(AchievementResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a single achievement record by ID for the authenticated principal's Master Career Profile.
     */
    @GetMapping(
            value = "/achievements/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AchievementResponse> getAchievementById(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        CareerProfileAchievement achievement = careerProfileService.getAchievement(authentication.getName(), id);
        return ResponseEntity.ok(AchievementResponse.from(achievement));
    }

    /**
     * Updates an existing achievement record for the authenticated principal's Master Career Profile.
     */
    @PutMapping(
            value = "/achievements/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AchievementResponse> updateAchievement(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody AchievementRequest request) {
        validateAuthentication(authentication);

        CareerProfileAchievement updated = careerProfileService.updateAchievement(authentication.getName(), id, request);
        return ResponseEntity.ok(AchievementResponse.from(updated));
    }

    /**
     * Deletes an existing achievement record for the authenticated principal's Master Career Profile.
     */
    @DeleteMapping("/achievements/{id}")
    public ResponseEntity<Void> deleteAchievement(
            @PathVariable UUID id,
            Authentication authentication) {
        validateAuthentication(authentication);

        careerProfileService.deleteAchievement(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    private void validateAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource");
        }
    }
}

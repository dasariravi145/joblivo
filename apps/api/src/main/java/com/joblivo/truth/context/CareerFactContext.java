package com.joblivo.truth.context;

import com.joblivo.profile.AchievementResponse;
import com.joblivo.profile.CertificationResponse;
import com.joblivo.profile.EducationResponse;
import com.joblivo.profile.MasterCareerProfileResponse;
import com.joblivo.profile.ProjectResponse;
import com.joblivo.profile.SkillResponse;
import com.joblivo.profile.WorkExperienceResponse;
import com.joblivo.truth.exception.InvalidCareerContextException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable factual context encapsulating a user's verified Master Career Profile data.
 * Serves as the sole authoritative grounding source against which professional claims are validated.
 * Does not duplicate database entities; operates directly on the immutable read model.
 *
 * @param userId        the owner of this career fact context (strictly non-null)
 * @param masterProfile the user's Master Career Profile (may be null if profile has not yet been initialized)
 */
public record CareerFactContext(
        UUID userId,
        MasterCareerProfileResponse masterProfile
) {
    public CareerFactContext {
        if (userId == null) {
            throw new InvalidCareerContextException("CareerFactContext requires a non-null userId");
        }
    }

    public static CareerFactContext of(UUID userId, MasterCareerProfileResponse masterProfile) {
        return new CareerFactContext(userId, masterProfile);
    }

    public static CareerFactContext empty(UUID userId) {
        return new CareerFactContext(userId, null);
    }

    public List<SkillResponse> getSkills() {
        return masterProfile != null && masterProfile.skills() != null
                ? masterProfile.skills()
                : Collections.emptyList();
    }

    public List<WorkExperienceResponse> getWorkExperiences() {
        return masterProfile != null && masterProfile.workExperiences() != null
                ? masterProfile.workExperiences()
                : Collections.emptyList();
    }

    public List<ProjectResponse> getProjects() {
        return masterProfile != null && masterProfile.projects() != null
                ? masterProfile.projects()
                : Collections.emptyList();
    }

    public List<EducationResponse> getEducation() {
        return masterProfile != null && masterProfile.education() != null
                ? masterProfile.education()
                : Collections.emptyList();
    }

    public List<CertificationResponse> getCertifications() {
        return masterProfile != null && masterProfile.certifications() != null
                ? masterProfile.certifications()
                : Collections.emptyList();
    }

    public List<AchievementResponse> getAchievements() {
        return masterProfile != null && masterProfile.achievements() != null
                ? masterProfile.achievements()
                : Collections.emptyList();
    }

    public Optional<String> getCurrentTitle() {
        return Optional.ofNullable(masterProfile != null ? masterProfile.currentTitle() : null);
    }

    public Optional<String> getCurrentCompany() {
        return Optional.ofNullable(masterProfile != null ? masterProfile.currentCompany() : null);
    }

    public Optional<Integer> getTotalExperienceMonths() {
        return Optional.ofNullable(masterProfile != null ? masterProfile.totalExperienceMonths() : null);
    }

    public Optional<String> getCurrentLocation() {
        return Optional.ofNullable(masterProfile != null ? masterProfile.currentLocation() : null);
    }

    /**
     * Finds a skill matching the given query text (case-insensitive, trimmed).
     */
    public Optional<SkillResponse> findSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(skillName);
        return getSkills().stream()
                .filter(s -> s.name() != null && normalize(s.name()).equals(normalized))
                .findFirst();
    }

    /**
     * Checks whether all given skill names exist in the user's profile.
     */
    public boolean hasAllSkills(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return false;
        }
        return skillNames.stream().allMatch(s -> findSkill(s).isPresent());
    }

    /**
     * Finds work experience matching job title and/or company name.
     */
    public List<WorkExperienceResponse> findWorkExperiences(String titleQuery, String companyQuery) {
        String normalizedTitle = titleQuery != null && !titleQuery.isBlank() ? normalize(titleQuery) : null;
        String normalizedCompany = companyQuery != null && !companyQuery.isBlank() ? normalize(companyQuery) : null;

        return getWorkExperiences().stream()
                .filter(exp -> {
                    boolean titleMatches = normalizedTitle == null ||
                            (exp.jobTitle() != null && normalize(exp.jobTitle()).contains(normalizedTitle));
                    boolean companyMatches = normalizedCompany == null ||
                            (exp.companyName() != null && normalize(exp.companyName()).contains(normalizedCompany));
                    return titleMatches && companyMatches;
                })
                .toList();
    }

    /**
     * Finds project matching project name or role.
     */
    public Optional<ProjectResponse> findProject(String projectNameQuery) {
        if (projectNameQuery == null || projectNameQuery.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(projectNameQuery);
        return getProjects().stream()
                .filter(p -> (p.projectName() != null && normalize(p.projectName()).contains(normalized))
                        || (p.role() != null && normalize(p.role()).contains(normalized)))
                .findFirst();
    }

    /**
     * Finds certification matching certification name or issuing organization.
     */
    public Optional<CertificationResponse> findCertification(String certQuery) {
        if (certQuery == null || certQuery.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(certQuery);
        return getCertifications().stream()
                .filter(c -> (c.certificationName() != null && normalize(c.certificationName()).contains(normalized))
                        || (c.issuingOrganization() != null && normalize(c.issuingOrganization()).contains(normalized)))
                .findFirst();
    }

    /**
     * Finds education matching institution, degree, or field of study.
     */
    public Optional<EducationResponse> findEducation(String educationQuery) {
        if (educationQuery == null || educationQuery.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(educationQuery);
        return getEducation().stream()
                .filter(e -> (e.institutionName() != null && normalize(e.institutionName()).contains(normalized))
                        || (e.degree() != null && normalize(e.degree()).contains(normalized))
                        || (e.fieldOfStudy() != null && normalize(e.fieldOfStudy()).contains(normalized)))
                .findFirst();
    }

    /**
     * Finds achievement matching title.
     */
    public Optional<AchievementResponse> findAchievement(String achievementQuery) {
        if (achievementQuery == null || achievementQuery.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(achievementQuery);
        return getAchievements().stream()
                .filter(a -> a.title() != null && normalize(a.title()).contains(normalized))
                .findFirst();
    }

    /**
     * Checks whether a specific factual metric or text token exists in any textual description
     * across work experience, projects, or achievements.
     */
    public boolean containsFactualTokenInDescriptions(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalized = normalize(token);

        boolean inWork = getWorkExperiences().stream()
                .anyMatch(w -> w.description() != null && normalize(w.description()).contains(normalized));
        if (inWork) return true;

        boolean inProjects = getProjects().stream()
                .anyMatch(p -> p.description() != null && normalize(p.description()).contains(normalized));
        if (inProjects) return true;

        return getAchievements().stream()
                .anyMatch(a -> a.description() != null && normalize(a.description()).contains(normalized));
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}

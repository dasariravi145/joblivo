package com.joblivo.truth.validator;

import com.joblivo.profile.AchievementResponse;
import com.joblivo.profile.CertificationResponse;
import com.joblivo.profile.EducationResponse;
import com.joblivo.profile.ProjectResponse;
import com.joblivo.profile.SkillResponse;
import com.joblivo.profile.WorkExperienceResponse;
import com.joblivo.truth.context.CareerFactContext;
import com.joblivo.truth.model.CareerClaim;
import com.joblivo.truth.model.ClaimCategory;
import com.joblivo.truth.model.EvidenceReference;
import com.joblivo.truth.model.EvidenceSourceType;
import com.joblivo.truth.model.TruthConfidence;
import com.joblivo.truth.model.TruthEvaluation;
import com.joblivo.truth.model.TruthProtectionLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link DeterministicEvidenceValidator}.
 * Evaluates claims using conservative factual matching against the user's {@link CareerFactContext}.
 * <p>
 * Guiding principles:
 * <ul>
 *   <li>Never silently promote an unverified claim to {@code VERIFIED}.</li>
 *   <li>Only mark {@code VERIFIED} when clear direct evidence exists in the profile.</li>
 *   <li>Mark {@code DERIVED} only when an assertion is logically implied by verified facts without new factual claims.</li>
 *   <li>Mark {@code NEEDS_CONFIRMATION} when evidence is partial, ambiguous, or unconfirmed.</li>
 *   <li>Mark {@code UNSUPPORTED} when no evidence exists or factual claims contradict known facts.</li>
 * </ul>
 */
@Component
public class DefaultDeterministicEvidenceValidator implements DeterministicEvidenceValidator {

    private static final Pattern EXPERIENCE_DURATION_PATTERN =
            Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)(?:\\s+of)?(?:\\s+experience)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER_METRIC_PATTERN =
            Pattern.compile("\\b(\\d+(?:\\.\\d+)?(?:[kmbt]|\\+)?|\\$\\d+(?:\\.\\d+)?(?:[kmbt])?)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public TruthEvaluation validate(CareerClaim claim, CareerFactContext context) {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String claimText = claim.text().trim();
        ClaimCategory category = claim.category();

        return switch (category) {
            case SKILL -> validateSkill(claim, claimText, context);
            case JOB_TITLE -> validateJobTitle(claim, claimText, context);
            case COMPANY -> validateCompany(claim, claimText, context);
            case EXPERIENCE -> validateExperience(claim, claimText, context);
            case PROJECT -> validateProject(claim, claimText, context);
            case CERTIFICATION -> validateCertification(claim, claimText, context);
            case EDUCATION -> validateEducation(claim, claimText, context);
            case ACHIEVEMENT -> validateAchievement(claim, claimText, context);
            case METRIC -> validateMetric(claim, claimText, context);
            case LOCATION -> validateLocation(claim, claimText, context);
            case RESPONSIBILITY, OTHER -> validateResponsibilityOrOther(claim, claimText, context);
        };
    }

    private TruthEvaluation validateSkill(CareerClaim claim, String text, CareerFactContext context) {
        // Check for compound skills (e.g. "Java and AWS" or "Java, Spring Boot, AWS")
        if (text.contains(" and ") || text.contains(",")) {
            String[] parts = text.split("(?i)\\s*(?:,|\\band\\b)\\s*");
            List<EvidenceReference> matchedRefs = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();

            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isBlank()) continue;
                Optional<SkillResponse> skillOpt = context.findSkill(trimmed);
                if (skillOpt.isPresent()) {
                    SkillResponse skill = skillOpt.get();
                    matchedRefs.add(EvidenceReference.of(EvidenceSourceType.SKILL, skill.id(), "Skill: " + skill.name()));
                } else {
                    missingSkills.add(trimmed);
                }
            }

            if (!matchedRefs.isEmpty() && missingSkills.isEmpty()) {
                return TruthEvaluation.derived(
                        claim,
                        "Logically derived combination of supported profile skills: " + text,
                        matchedRefs
                );
            } else if (!matchedRefs.isEmpty()) {
                return TruthEvaluation.needsConfirmation(
                        claim,
                        "Partially supported: Contains verified skills but missing evidence for: " + String.join(", ", missingSkills),
                        matchedRefs
                );
            } else {
                return TruthEvaluation.unsupported(
                        claim,
                        "No verified skills found matching compound claim: " + text
                );
            }
        }

        // Direct single skill match
        Optional<SkillResponse> skillOpt = context.findSkill(text);
        if (skillOpt.isPresent()) {
            SkillResponse skill = skillOpt.get();
            return TruthEvaluation.verified(
                    claim,
                    "Skill directly matches Master Career Profile: " + skill.name(),
                    List.of(EvidenceReference.of(EvidenceSourceType.SKILL, skill.id(), "Skill: " + skill.name()))
            );
        }

        // Check if user has skills that partially overlap
        List<SkillResponse> partialSkills = context.getSkills().stream()
                .filter(s -> s.name() != null &&
                        (normalize(s.name()).contains(normalize(text)) || normalize(text).contains(normalize(s.name()))))
                .toList();

        if (!partialSkills.isEmpty()) {
            List<EvidenceReference> refs = partialSkills.stream()
                    .map(s -> EvidenceReference.of(EvidenceSourceType.SKILL, s.id(), "Related skill: " + s.name()))
                    .toList();
            return TruthEvaluation.needsConfirmation(
                    claim,
                    "Skill partially matches profile competencies but requires confirmation: " + text,
                    refs
            );
        }

        return TruthEvaluation.unsupported(
                claim,
                "Skill is absent from user's Master Career Profile: " + text
        );
    }

    private TruthEvaluation validateJobTitle(CareerClaim claim, String text, CareerFactContext context) {
        String normalizedText = normalize(text);

        // Check current title
        if (context.getCurrentTitle().isPresent() && normalize(context.getCurrentTitle().get()).equals(normalizedText)) {
            return TruthEvaluation.verified(
                    claim,
                    "Job title matches current professional title: " + context.getCurrentTitle().get(),
                    List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Current Title: " + context.getCurrentTitle().get()))
            );
        }

        // Check work experiences
        for (WorkExperienceResponse exp : context.getWorkExperiences()) {
            if (exp.jobTitle() != null) {
                String expTitle = normalize(exp.jobTitle());
                if (expTitle.equals(normalizedText)) {
                    return TruthEvaluation.verified(
                            claim,
                            "Job title directly verified from work experience at " + exp.companyName(),
                            List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, exp.id(), "Role: " + exp.jobTitle() + " at " + exp.companyName()))
                    );
                }
            }
        }

        // Partial match
        for (WorkExperienceResponse exp : context.getWorkExperiences()) {
            if (exp.jobTitle() != null) {
                String expTitle = normalize(exp.jobTitle());
                if (expTitle.contains(normalizedText) || normalizedText.contains(expTitle)) {
                    return TruthEvaluation.needsConfirmation(
                            claim,
                            "Job title partially matches work experience ('" + exp.jobTitle() + "' at " + exp.companyName() + "), requires confirmation",
                            List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, exp.id(), "Role: " + exp.jobTitle()))
                    );
                }
            }
        }

        return TruthEvaluation.unsupported(
                claim,
                "Job title is absent from user's work history: " + text
        );
    }

    private TruthEvaluation validateCompany(CareerClaim claim, String text, CareerFactContext context) {
        String normalizedText = normalize(text);

        if (context.getCurrentCompany().isPresent() && normalize(context.getCurrentCompany().get()).equals(normalizedText)) {
            return TruthEvaluation.verified(
                    claim,
                    "Company matches current employer: " + context.getCurrentCompany().get(),
                    List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Current Company: " + context.getCurrentCompany().get()))
            );
        }

        for (WorkExperienceResponse exp : context.getWorkExperiences()) {
            if (exp.companyName() != null && normalize(exp.companyName()).equals(normalizedText)) {
                return TruthEvaluation.verified(
                        claim,
                        "Company verified from work experience record: " + exp.companyName(),
                        List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, exp.id(), "Employer: " + exp.companyName()))
                );
            }
        }

        return TruthEvaluation.unsupported(
                claim,
                "Company is absent from user's work history: " + text
        );
    }

    private TruthEvaluation validateExperience(CareerClaim claim, String text, CareerFactContext context) {
        // 1. Check for experience duration claim (e.g. "5 years experience", "3+ years of experience")
        Matcher durationMatcher = EXPERIENCE_DURATION_PATTERN.matcher(text);
        if (durationMatcher.find()) {
            int claimedYears = Integer.parseInt(durationMatcher.group(1));
            int claimedMonths = claimedYears * 12;

            if (context.getTotalExperienceMonths().isPresent()) {
                int totalMonths = context.getTotalExperienceMonths().get();
                if (totalMonths >= claimedMonths) {
                    return TruthEvaluation.derived(
                            claim,
                            "Claimed duration of " + claimedYears + " years is logically supported by verified " + totalMonths + " total experience months",
                            List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Total Experience: " + totalMonths + " months"))
                    );
                } else {
                    return TruthEvaluation.unsupported(
                            claim,
                            "Claimed duration of " + claimedYears + " years exceeds verified total experience (" + totalMonths + " months)"
                    );
                }
            }
        }

        // 2. Check for role + company claim (e.g. "Cloud Engineer at Acme Corp" or "Senior Software Engineer at Foo")
        String titlePart = null;
        String companyPart = null;

        if (text.contains(" at ")) {
            String[] parts = text.split("(?i)\\s+at\\s+", 2);
            titlePart = parts[0].trim();
            companyPart = parts[1].trim();
        } else if (claim.attributes().containsKey("company") && claim.attributes().containsKey("jobTitle")) {
            titlePart = claim.attributes().get("jobTitle");
            companyPart = claim.attributes().get("company");
        }

        if (titlePart != null && companyPart != null) {
            List<WorkExperienceResponse> fullMatches = context.findWorkExperiences(titlePart, companyPart);
            if (!fullMatches.isEmpty()) {
                WorkExperienceResponse match = fullMatches.get(0);
                return TruthEvaluation.verified(
                        claim,
                        "Work experience verified: " + match.jobTitle() + " at " + match.companyName(),
                        List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, match.id(), match.jobTitle() + " at " + match.companyName()))
                );
            }

            // Check if title matches anywhere without company
            List<WorkExperienceResponse> titleMatches = context.findWorkExperiences(titlePart, null);
            if (!titleMatches.isEmpty()) {
                WorkExperienceResponse exp = titleMatches.get(0);
                return TruthEvaluation.needsConfirmation(
                        claim,
                        "Job title '" + titlePart + "' is verified, but company '" + companyPart + "' does not match profile (found at '" + exp.companyName() + "')",
                        List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, exp.id(), exp.jobTitle() + " at " + exp.companyName()))
                );
            }

            // Check if company matches anywhere without title
            List<WorkExperienceResponse> companyMatches = context.findWorkExperiences(null, companyPart);
            if (!companyMatches.isEmpty()) {
                WorkExperienceResponse exp = companyMatches.get(0);
                return TruthEvaluation.needsConfirmation(
                        claim,
                        "Company '" + companyPart + "' is verified, but role '" + titlePart + "' does not match profile (recorded as '" + exp.jobTitle() + "')",
                        List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, exp.id(), exp.jobTitle() + " at " + exp.companyName()))
                );
            }

            return TruthEvaluation.unsupported(
                    claim,
                    "Neither role '" + titlePart + "' nor company '" + companyPart + "' was found in user's profile"
            );
        }

        // 3. Fall back to title or general experience match
        return validateJobTitle(claim, text, context);
    }

    private TruthEvaluation validateProject(CareerClaim claim, String text, CareerFactContext context) {
        Optional<ProjectResponse> projectOpt = context.findProject(text);
        if (projectOpt.isPresent()) {
            ProjectResponse p = projectOpt.get();
            return TruthEvaluation.verified(
                    claim,
                    "Project verified in Master Career Profile: " + p.projectName(),
                    List.of(EvidenceReference.of(EvidenceSourceType.PROJECT, p.id(), "Project: " + p.projectName() + (p.role() != null ? " (" + p.role() + ")" : "")))
            );
        }

        return TruthEvaluation.unsupported(
                claim,
                "Project is absent from user's Master Career Profile: " + text
        );
    }

    private TruthEvaluation validateCertification(CareerClaim claim, String text, CareerFactContext context) {
        Optional<CertificationResponse> certOpt = context.findCertification(text);
        if (certOpt.isPresent()) {
            CertificationResponse cert = certOpt.get();
            return TruthEvaluation.verified(
                    claim,
                    "Certification verified in Master Career Profile: " + cert.certificationName(),
                    List.of(EvidenceReference.of(EvidenceSourceType.CERTIFICATION, cert.id(), cert.certificationName() + " by " + cert.issuingOrganization()))
            );
        }

        // Conservative check: if claim mentions an organization (e.g. AWS) and user has a cert from that org,
        // but the specific credential is not held, do NOT verify. Flag as unsupported or needs confirmation.
        boolean hasRelatedCert = context.getCertifications().stream()
                .anyMatch(c -> (c.issuingOrganization() != null && normalize(text).contains(normalize(c.issuingOrganization())))
                        || (c.certificationName() != null && hasTokenOverlap(c.certificationName(), text)));

        if (hasRelatedCert) {
            return TruthEvaluation.unsupported(
                    claim,
                    "Specific credential '" + text + "' is absent from profile, even though related certifications exist"
            );
        }

        return TruthEvaluation.unsupported(
                claim,
                "Certification is absent from user's Master Career Profile: " + text
        );
    }

    private TruthEvaluation validateEducation(CareerClaim claim, String text, CareerFactContext context) {
        Optional<EducationResponse> eduOpt = context.findEducation(text);
        if (eduOpt.isPresent()) {
            EducationResponse edu = eduOpt.get();
            return TruthEvaluation.verified(
                    claim,
                    "Education credential verified in Master Career Profile: " + edu.institutionName(),
                    List.of(EvidenceReference.of(EvidenceSourceType.EDUCATION, edu.id(), edu.degree() + " from " + edu.institutionName()))
            );
        }

        return TruthEvaluation.unsupported(
                claim,
                "Education credential is absent from user's Master Career Profile: " + text
        );
    }

    private TruthEvaluation validateAchievement(CareerClaim claim, String text, CareerFactContext context) {
        Optional<AchievementResponse> achOpt = context.findAchievement(text);
        if (achOpt.isPresent()) {
            AchievementResponse ach = achOpt.get();
            return TruthEvaluation.verified(
                    claim,
                    "Achievement verified in Master Career Profile: " + ach.title(),
                    List.of(EvidenceReference.of(EvidenceSourceType.ACHIEVEMENT, ach.id(), ach.title()))
            );
        }

        return TruthEvaluation.unsupported(
                claim,
                "Achievement is absent from user's Master Career Profile: " + text
        );
    }

    private TruthEvaluation validateMetric(CareerClaim claim, String text, CareerFactContext context) {
        // Extract specific metric number or scale token
        Matcher metricMatcher = NUMBER_METRIC_PATTERN.matcher(text);
        if (metricMatcher.find()) {
            String metricToken = metricMatcher.group(1);
            if (context.containsFactualTokenInDescriptions(metricToken)) {
                return TruthEvaluation.verified(
                        claim,
                        "Metric '" + metricToken + "' is supported by verified career profile descriptions",
                        List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Verified metric: " + metricToken))
                );
            } else {
                return TruthEvaluation.unsupported(
                        claim,
                        "Metric '" + metricToken + "' in claim '" + text + "' has no supporting evidence in profile descriptions"
                );
            }
        }

        // If no explicit number was extracted, check if the full text is evidenced in descriptions
        if (context.containsFactualTokenInDescriptions(text)) {
            return TruthEvaluation.verified(
                    claim,
                    "Factual metric statement supported by verified descriptions",
                    List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Metric statement matched in descriptions"))
            );
        }

        return TruthEvaluation.unsupported(
                claim,
                "Metric claim has no supporting evidence in user's profile: " + text
        );
    }

    private TruthEvaluation validateLocation(CareerClaim claim, String text, CareerFactContext context) {
        String normalizedText = normalize(text);

        if (context.getCurrentLocation().isPresent() && normalize(context.getCurrentLocation().get()).contains(normalizedText)) {
            return TruthEvaluation.verified(
                    claim,
                    "Location matches current residence in profile: " + context.getCurrentLocation().get(),
                    List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Current Location: " + context.getCurrentLocation().get()))
            );
        }

        if (context.masterProfile() != null && context.masterProfile().preferredWorkLocation() != null) {
            if (normalize(context.masterProfile().preferredWorkLocation()).contains(normalizedText)) {
                return TruthEvaluation.verified(
                        claim,
                        "Location matches preferred work location in profile: " + context.masterProfile().preferredWorkLocation(),
                        List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Preferred Location: " + context.masterProfile().preferredWorkLocation()))
                );
            }
        }

        for (WorkExperienceResponse exp : context.getWorkExperiences()) {
            if (exp.location() != null && normalize(exp.location()).contains(normalizedText)) {
                return TruthEvaluation.verified(
                        claim,
                        "Location matches work experience location at " + exp.companyName(),
                        List.of(EvidenceReference.of(EvidenceSourceType.WORK_EXPERIENCE, exp.id(), "Location: " + exp.location()))
                );
            }
        }

        return TruthEvaluation.unsupported(
                claim,
                "Location '" + text + "' is not found in user's profile"
        );
    }

    private TruthEvaluation validateResponsibilityOrOther(CareerClaim claim, String text, CareerFactContext context) {
        // Check if exact or near-exact phrase is grounded in work experience or project descriptions
        if (context.containsFactualTokenInDescriptions(text)) {
            return TruthEvaluation.verified(
                    claim,
                    "Statement is directly grounded in verified experience or project descriptions",
                    List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Verified description anchor"))
            );
        }

        // Check if key keywords from the claim are all evidenced
        String[] tokens = text.split("\\s+");
        List<String> meaningfulTokens = Arrays.stream(tokens)
                .map(String::trim)
                .filter(t -> t.length() > 3)
                .toList();

        if (!meaningfulTokens.isEmpty()) {
            long matchedCount = meaningfulTokens.stream()
                    .filter(context::containsFactualTokenInDescriptions)
                    .count();

            if (matchedCount == meaningfulTokens.size()) {
                return TruthEvaluation.derived(
                        claim,
                        "Statement is constructed from verified terms across profile descriptions",
                        List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Composite keyword match"))
                );
            } else if (matchedCount > 0) {
                return TruthEvaluation.needsConfirmation(
                        claim,
                        "Statement partially aligns with profile descriptions but contains unverified elements",
                        List.of(EvidenceReference.of(EvidenceSourceType.CAREER_PROFILE, "Partial keyword alignment"))
                );
            }
        }

        return TruthEvaluation.unsupported(
                claim,
                "Statement has no supporting factual anchors in the user's Master Career Profile"
        );
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasTokenOverlap(String s1, String s2) {
        String[] t1 = s1.toLowerCase(Locale.ROOT).split("\\s+");
        String[] t2 = s2.toLowerCase(Locale.ROOT).split("\\s+");
        for (String a : t1) {
            if (a.length() > 3) {
                for (String b : t2) {
                    if (a.equals(b)) return true;
                }
            }
        }
        return false;
    }
}

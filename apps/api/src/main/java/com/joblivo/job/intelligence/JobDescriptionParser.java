package com.joblivo.job.intelligence;

import com.joblivo.job.Job;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic canonical parser for Job Description Intelligence.
 * <p>
 * Transforms raw job description text into a structured, source-neutral {@link JobDescriptionIntelligence}
 * representation without using AI models, without mutating original descriptions, and without fabricating requirements.
 */
@Component
public class JobDescriptionParser {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
            "by", "can", "cannot", "could", "did", "do", "does", "doing", "down", "during", "each",
            "few", "for", "from", "further", "had", "has", "have", "having", "he", "her", "here",
            "hers", "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it",
            "its", "itself", "me", "more", "most", "my", "myself", "no", "nor", "not", "of", "off",
            "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over",
            "own", "same", "she", "should", "so", "some", "such", "than", "that", "the", "their",
            "theirs", "them", "themselves", "then", "there", "these", "they", "this", "those", "through",
            "to", "too", "under", "until", "up", "very", "was", "we", "were", "what", "when", "where",
            "which", "while", "who", "whom", "why", "with", "would", "you", "your", "yours", "yourself",
            "yourselves", "will", "shall", "work", "role", "team", "join", "help", "build", "looking"
    );

    // Controlled technology dictionary (aligned with SkillCategory and TruthEngine)
    private static final List<CanonicalSkill> CANONICAL_SKILLS = List.of(
            new CanonicalSkill("Java", "\\bJava\\b"),
            new CanonicalSkill("Python", "\\bPython\\b"),
            new CanonicalSkill("C++", "\\bC\\+\\+\\b"),
            new CanonicalSkill("C#", "\\bC#\\b"),
            new CanonicalSkill("Go", "\\b(?:Go|Golang)\\b"),
            new CanonicalSkill("Rust", "\\bRust\\b"),
            new CanonicalSkill("JavaScript", "\\bJavaScript\\b"),
            new CanonicalSkill("TypeScript", "\\bTypeScript\\b"),
            new CanonicalSkill("SQL", "\\bSQL\\b"),
            new CanonicalSkill("HTML", "\\bHTML\\b"),
            new CanonicalSkill("CSS", "\\bCSS\\b"),
            new CanonicalSkill("AWS", "\\b(?:AWS|Amazon Web Services)\\b"),
            new CanonicalSkill("Azure", "\\b(?:Azure|Microsoft Azure)\\b"),
            new CanonicalSkill("GCP", "\\b(?:GCP|Google Cloud Platform|Google Cloud)\\b"),
            new CanonicalSkill("Kubernetes", "\\bKubernetes\\b"),
            new CanonicalSkill("Docker", "\\bDocker\\b"),
            new CanonicalSkill("Terraform", "\\bTerraform\\b"),
            new CanonicalSkill("Jenkins", "\\bJenkins\\b"),
            new CanonicalSkill("PostgreSQL", "\\b(?:PostgreSQL|Postgres)\\b"),
            new CanonicalSkill("MySQL", "\\bMySQL\\b"),
            new CanonicalSkill("MongoDB", "\\bMongoDB\\b"),
            new CanonicalSkill("Redis", "\\bRedis\\b"),
            new CanonicalSkill("React", "\\bReact(?:\\.js)?\\b"),
            new CanonicalSkill("Angular", "\\bAngular(?:\\.js)?\\b"),
            new CanonicalSkill("Vue", "\\bVue(?:\\.js)?\\b"),
            new CanonicalSkill("Spring Boot", "\\bSpring Boot\\b"),
            new CanonicalSkill("Spring", "\\bSpring Framework\\b"),
            new CanonicalSkill("Node.js", "\\bNode(?:\\.js)?\\b"),
            new CanonicalSkill("Git", "\\bGit\\b"),
            new CanonicalSkill("Linux", "\\bLinux\\b"),
            new CanonicalSkill("Kafka", "\\bKafka\\b"),
            new CanonicalSkill("GraphQL", "\\bGraphQL\\b")
    );

    private static final List<EducationPattern> EDUCATION_PATTERNS = List.of(
            new EducationPattern("Bachelor's Degree", "(?i)\\b(?:bachelor'?s?(?:\\s+degree)?|b\\.?[te]ech|b\\.e\\.|b\\.s\\.|undergraduate\\s+degree)\\b"),
            new EducationPattern("Master's Degree", "(?i)\\b(?:master'?s?(?:\\s+degree)?|m\\.?[te]ech|m\\.s\\.|postgraduate\\s+degree)\\b"),
            new EducationPattern("MBA", "(?i)\\bMBA\\b"),
            new EducationPattern("PhD", "(?i)\\b(?:ph\\.?d\\.?|doctorate)\\b")
    );

    private static final List<CertificationPattern> CERTIFICATION_PATTERNS = List.of(
            new CertificationPattern("AWS Certified Solutions Architect", "(?i)\\bAWS\\s+Certified\\s+Solutions?\\s+Architect\\b"),
            new CertificationPattern("AWS Certified", "(?i)\\bAWS\\s+Certified\\b"),
            new CertificationPattern("Azure Certification", "(?i)\\b(?:Azure\\s+Certif(?:ied|ication)|AZ-\\d{3})\\b"),
            new CertificationPattern("Google Cloud Certified", "(?i)\\b(?:Google\\s+Cloud|GCP)\\s+Certif(?:ied|ication)\\b"),
            new CertificationPattern("PMP", "(?i)\\bPMP\\b"),
            new CertificationPattern("CISSP", "(?i)\\bCISSP\\b"),
            new CertificationPattern("CKA", "(?i)\\b(?:CKA|Certified\\s+Kubernetes\\s+Administrator)\\b")
    );

    // Experience regex patterns
    private static final Pattern EXP_RANGE_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*(?:[-–]|to)\\s*(\\d{1,2})\\s*(?:\\+\\s*)?years?(?:\\s+of)?\\s*(?:experience)?\\b"
    );
    private static final Pattern EXP_MIN_PLUS_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*\\+\\s*years?(?:\\s+of)?\\s*(?:experience)?\\b"
    );
    private static final Pattern EXP_MIN_WORD_PATTERN = Pattern.compile(
            "(?i)\\b(?:minimum|min|at least)\\s*(\\d{1,2})\\s*(?:\\+\\s*)?years?(?:\\s+of)?\\s*(?:experience)?\\b"
    );
    private static final Pattern EXP_EXACT_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s+years?(?:\\s+of)?\\s+experience\\b"
    );

    /**
     * Parses a {@link Job} entity into its structured {@link JobDescriptionIntelligence} representation.
     *
     * @param job domain entity to parse
     * @return structured intelligence read model
     */
    public JobDescriptionIntelligence parse(Job job) {
        if (job == null) {
            return JobDescriptionIntelligence.empty(null);
        }
        return parse(job.getId(), job.getDescription());
    }

    /**
     * Parses raw job description text for a specific jobId.
     *
     * @param jobId          associated job UUID (optional)
     * @param rawDescription raw job description text (optional)
     * @return structured intelligence read model
     */
    public JobDescriptionIntelligence parse(UUID jobId, String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return JobDescriptionIntelligence.empty(jobId);
        }

        List<JobIntelligenceWarning> warnings = new ArrayList<>();

        // 1. Extract sections
        List<JobDescriptionSection> sections = extractSections(rawDescription);
        boolean hasReliableHeadings = sections.stream().anyMatch(s -> s.sectionType() != JobDescriptionSectionType.OTHER);
        if (!hasReliableHeadings) {
            warnings.add(JobIntelligenceWarning.NO_RELIABLE_SECTIONS_DETECTED);
        }

        // 2. Classify section contents
        StringBuilder summaryBuilder = new StringBuilder();
        List<String> responsibilities = new ArrayList<>();
        List<String> requiredQualifications = new ArrayList<>();
        List<String> preferredQualifications = new ArrayList<>();

        for (JobDescriptionSection section : sections) {
            switch (section.sectionType()) {
                case SUMMARY -> {
                    for (String line : section.lines()) {
                        if (isExplicitRequiredLine(line)) {
                            requiredQualifications.add(line);
                        } else if (isExplicitPreferredLine(line)) {
                            preferredQualifications.add(line);
                        } else {
                            if (!summaryBuilder.isEmpty()) {
                                summaryBuilder.append(" ");
                            }
                            summaryBuilder.append(line);
                        }
                    }
                }
                case RESPONSIBILITIES -> responsibilities.addAll(section.lines());
                case REQUIRED -> {
                    for (String line : section.lines()) {
                        if (isExplicitPreferredLine(line)) {
                            preferredQualifications.add(line);
                        } else {
                            requiredQualifications.add(line);
                        }
                    }
                }
                case PREFERRED -> {
                    for (String line : section.lines()) {
                        if (isExplicitRequiredLine(line)) {
                            requiredQualifications.add(line);
                        } else {
                            preferredQualifications.add(line);
                        }
                    }
                }
                case OTHER -> {
                    for (String line : section.lines()) {
                        if (isExplicitRequiredLine(line)) {
                            requiredQualifications.add(line);
                        } else if (isExplicitPreferredLine(line)) {
                            preferredQualifications.add(line);
                        } else if (summaryBuilder.isEmpty() && !hasReliableHeadings && !isBullet(line)) {
                            summaryBuilder.append(line);
                        }
                    }
                }
                default -> {
                    // Handled specifically in later extractors
                }
            }
        }

        // 3. Extract Skills (Required vs Preferred)
        Set<String> requiredSkills = new LinkedHashSet<>();
        Set<String> preferredSkills = new LinkedHashSet<>();
        Set<String> generalSkills = new LinkedHashSet<>();

        for (JobDescriptionSection section : sections) {
            boolean isRequiredSection = section.sectionType() == JobDescriptionSectionType.REQUIRED;
            boolean isPreferredSection = section.sectionType() == JobDescriptionSectionType.PREFERRED;

            for (String line : section.lines()) {
                boolean lineRequires = isExplicitRequiredLine(line);
                boolean linePrefers = isExplicitPreferredLine(line);

                for (CanonicalSkill skill : CANONICAL_SKILLS) {
                    if (skill.matches(line)) {
                        if (lineRequires || (isRequiredSection && !linePrefers)) {
                            requiredSkills.add(skill.name());
                        } else if (linePrefers || (isPreferredSection && !lineRequires)) {
                            preferredSkills.add(skill.name());
                        } else {
                            generalSkills.add(skill.name());
                        }
                    }
                }
            }
        }

        if (requiredSkills.isEmpty() && preferredSkills.isEmpty() && generalSkills.isEmpty()) {
            warnings.add(JobIntelligenceWarning.SKILL_EXTRACTION_LIMITED);
        }

        // 4. Extract Experience
        JobExperienceRequirement experienceRequirement = extractExperience(rawDescription);
        if (experienceRequirement == null) {
            warnings.add(JobIntelligenceWarning.EXPERIENCE_NOT_EXPLICIT);
        }

        // 5. Extract Education
        List<String> educationRequirements = extractEducation(rawDescription);

        // 6. Extract Certifications
        List<String> certificationRequirements = extractCertifications(rawDescription);

        // 7. Extract Location & Work Mode
        List<String> locationRequirements = extractLocations(rawDescription);
        List<String> workModeRequirements = extractWorkModes(rawDescription);

        // 8. Extract Keywords
        List<String> keywords = extractKeywords(rawDescription);

        return new JobDescriptionIntelligence(
                jobId,
                true,
                summaryBuilder.toString(),
                responsibilities,
                requiredQualifications,
                preferredQualifications,
                List.copyOf(requiredSkills),
                List.copyOf(preferredSkills),
                experienceRequirement,
                educationRequirements,
                certificationRequirements,
                locationRequirements,
                workModeRequirements,
                keywords,
                warnings
        );
    }

    private List<JobDescriptionSection> extractSections(String rawText) {
        String[] lines = rawText.split("\\r?\\n");
        List<JobDescriptionSection> sections = new ArrayList<>();

        JobDescriptionSectionType currentType = JobDescriptionSectionType.OTHER;
        String currentHeading = "";
        List<String> currentLines = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            JobDescriptionSectionType detectedType = detectHeadingType(trimmed);
            if (detectedType != null) {
                // Finish previous section if it had content
                if (!currentLines.isEmpty() || !currentHeading.isEmpty()) {
                    sections.add(new JobDescriptionSection(
                            currentType,
                            currentHeading,
                            currentLines,
                            currentContent.toString()
                    ));
                }

                currentType = detectedType;
                currentHeading = trimmed;
                currentLines = new ArrayList<>();
                currentContent = new StringBuilder();
            } else {
                String cleanLine = stripBulletPrefix(trimmed);
                if (!cleanLine.isEmpty()) {
                    currentLines.add(cleanLine);
                }
                if (!currentContent.isEmpty()) {
                    currentContent.append("\n");
                }
                currentContent.append(rawLine);
            }
        }

        if (!currentLines.isEmpty() || !currentHeading.isEmpty()) {
            sections.add(new JobDescriptionSection(
                    currentType,
                    currentHeading,
                    currentLines,
                    currentContent.toString()
            ));
        }

        return sections;
    }

    private JobDescriptionSectionType detectHeadingType(String line) {
        if (line.length() > 60) {
            return null;
        }

        // Clean out markdown formatting and punctuation
        String normalized = line.replaceAll("^[#*\\-_\\s]+", "")
                .replaceAll("[#*\\-_:\\s]+$", "")
                .trim()
                .toLowerCase(Locale.ENGLISH);

        if (normalized.isEmpty()) {
            return null;
        }

        return switch (normalized) {
            case "summary", "about the role", "about role", "role summary", "job summary",
                    "description", "about us", "about the company", "overview", "role overview" -> JobDescriptionSectionType.SUMMARY;
            case "responsibilities", "responsibilities and duties", "key responsibilities",
                    "duties", "what you'll do", "what you will do", "your role", "what you do",
                    "day to day", "responsibilities include" -> JobDescriptionSectionType.RESPONSIBILITIES;
            case "requirements", "required qualifications", "required skills", "must have",
                    "must-have", "basic qualifications", "minimum qualifications",
                    "mandatory requirements", "qualifications", "what you bring",
                    "what we're looking for", "what we are looking for" -> JobDescriptionSectionType.REQUIRED;
            case "preferred qualifications", "preferred skills", "nice to have", "nice-to-have",
                    "good to have", "good-to-have", "bonus", "bonus points", "plus",
                    "desired skills", "desired qualifications", "preferred" -> JobDescriptionSectionType.PREFERRED;
            case "experience", "work experience", "experience requirements", "prior experience" -> JobDescriptionSectionType.EXPERIENCE;
            case "education", "educational qualifications", "academic requirements", "degree requirements" -> JobDescriptionSectionType.EDUCATION;
            case "certifications", "certifications required", "credentials", "licenses" -> JobDescriptionSectionType.CERTIFICATIONS;
            case "location", "work location", "office location", "job location" -> JobDescriptionSectionType.LOCATION;
            case "work mode", "work arrangement", "workplace type", "remote policy" -> JobDescriptionSectionType.WORK_MODE;
            case "benefits", "perks", "what we offer", "compensation", "equal opportunity" -> JobDescriptionSectionType.OTHER;
            default -> null;
        };
    }

    private JobExperienceRequirement extractExperience(String rawText) {
        Matcher rangeMatcher = EXP_RANGE_PATTERN.matcher(rawText);
        if (rangeMatcher.find()) {
            int min = Integer.parseInt(rangeMatcher.group(1));
            int max = Integer.parseInt(rangeMatcher.group(2));
            return JobExperienceRequirement.range(min, max, rangeMatcher.group(0));
        }

        Matcher minPlusMatcher = EXP_MIN_PLUS_PATTERN.matcher(rawText);
        if (minPlusMatcher.find()) {
            int min = Integer.parseInt(minPlusMatcher.group(1));
            return JobExperienceRequirement.min(min, minPlusMatcher.group(0));
        }

        Matcher minWordMatcher = EXP_MIN_WORD_PATTERN.matcher(rawText);
        if (minWordMatcher.find()) {
            int min = Integer.parseInt(minWordMatcher.group(1));
            return JobExperienceRequirement.min(min, minWordMatcher.group(0));
        }

        Matcher exactMatcher = EXP_EXACT_PATTERN.matcher(rawText);
        if (exactMatcher.find()) {
            int min = Integer.parseInt(exactMatcher.group(1));
            return JobExperienceRequirement.min(min, exactMatcher.group(0));
        }

        return null;
    }

    private List<String> extractEducation(String rawText) {
        Set<String> detected = new LinkedHashSet<>();
        for (EducationPattern pattern : EDUCATION_PATTERNS) {
            if (pattern.matches(rawText)) {
                detected.add(pattern.displayName());
            }
        }
        return List.copyOf(detected);
    }

    private List<String> extractCertifications(String rawText) {
        Set<String> detected = new LinkedHashSet<>();
        for (CertificationPattern pattern : CERTIFICATION_PATTERNS) {
            if (pattern.matches(rawText)) {
                detected.add(pattern.displayName());
            }
        }
        return List.copyOf(detected);
    }

    private List<String> extractLocations(String rawText) {
        Set<String> locations = new LinkedHashSet<>();
        List<String[]> locationPatterns = List.of(
                new String[]{"Bengaluru", "(?i)\\b(?:Bengaluru|Bangalore)\\b"},
                new String[]{"Hyderabad", "(?i)\\bHyderabad\\b"},
                new String[]{"Mumbai", "(?i)\\bMumbai\\b"},
                new String[]{"Pune", "(?i)\\bPune\\b"},
                new String[]{"Delhi-NCR", "(?i)\\b(?:Delhi|New Delhi|Noida|Gurgaon|Gurugram|NCR)\\b"},
                new String[]{"Chennai", "(?i)\\bChennai\\b"},
                new String[]{"Kolkata", "(?i)\\bKolkata\\b"}
        );

        for (String[] loc : locationPatterns) {
            if (Pattern.compile(loc[1]).matcher(rawText).find()) {
                locations.add(loc[0]);
            }
        }
        return List.copyOf(locations);
    }

    private List<String> extractWorkModes(String rawText) {
        Set<String> workModes = new LinkedHashSet<>();
        if (Pattern.compile("(?i)\\b(?:Remote|Work from home|WFH|Anywhere)\\b").matcher(rawText).find()) {
            workModes.add("Remote");
        }
        if (Pattern.compile("(?i)\\b(?:Hybrid|Flexible workplace)\\b").matcher(rawText).find()) {
            workModes.add("Hybrid");
        }
        if (Pattern.compile("(?i)\\b(?:On-site|Onsite|In-office|Work from office)\\b").matcher(rawText).find()) {
            workModes.add("On-site");
        }
        return List.copyOf(workModes);
    }

    private List<String> extractKeywords(String rawText) {
        String[] words = rawText.toLowerCase(Locale.ENGLISH).split("[^a-zA-Z0-9+#]+");
        Set<String> keywords = new LinkedHashSet<>();

        for (String word : words) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                keywords.add(word);
                if (keywords.size() >= 30) {
                    break;
                }
            }
        }
        return List.copyOf(keywords);
    }

    private boolean isExplicitRequiredLine(String line) {
        String lower = line.toLowerCase(Locale.ENGLISH);
        return lower.contains("must have")
                || lower.contains("must-have")
                || lower.contains("mandatory")
                || lower.contains("required")
                || lower.contains("minimum qualification")
                || lower.contains("basic qualification");
    }

    private boolean isExplicitPreferredLine(String line) {
        String lower = line.toLowerCase(Locale.ENGLISH);
        return lower.contains("nice to have")
                || lower.contains("nice-to-have")
                || lower.contains("good to have")
                || lower.contains("good-to-have")
                || lower.contains("preferred")
                || lower.contains("plus")
                || lower.contains("bonus")
                || lower.contains("desirable");
    }

    private boolean isBullet(String line) {
        return line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ") || line.matches("^\\d+\\.\\s.*");
    }

    private String stripBulletPrefix(String line) {
        return line.replaceAll("^[*\\-•\\d.]+\\s*", "").trim();
    }

    private record CanonicalSkill(String name, Pattern pattern) {
        CanonicalSkill(String name, String regex) {
            this(name, Pattern.compile(regex));
        }

        boolean matches(String text) {
            return pattern.matcher(text).find();
        }
    }

    private record EducationPattern(String displayName, Pattern pattern) {
        EducationPattern(String displayName, String regex) {
            this(displayName, Pattern.compile(regex));
        }

        boolean matches(String text) {
            return pattern.matcher(text).find();
        }
    }

    private record CertificationPattern(String displayName, Pattern pattern) {
        CertificationPattern(String displayName, String regex) {
            this(displayName, Pattern.compile(regex));
        }

        boolean matches(String text) {
            return pattern.matcher(text).find();
        }
    }
}

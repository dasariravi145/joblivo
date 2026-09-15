package com.joblivo.truth;

import com.joblivo.profile.AchievementResponse;
import com.joblivo.profile.AchievementType;
import com.joblivo.profile.CareerProfileCompletenessResponse;
import com.joblivo.profile.CertificationResponse;
import com.joblivo.profile.EducationLevel;
import com.joblivo.profile.EducationResponse;
import com.joblivo.profile.EmploymentType;
import com.joblivo.profile.MasterCareerProfileResponse;
import com.joblivo.profile.ProjectResponse;
import com.joblivo.profile.ProjectType;
import com.joblivo.profile.SectionCompletenessResponse;
import com.joblivo.profile.SkillCategory;
import com.joblivo.profile.SkillProficiency;
import com.joblivo.profile.SkillResponse;
import com.joblivo.profile.WorkExperienceResponse;
import com.joblivo.profile.WorkMode;
import com.joblivo.truth.context.CareerFactContext;
import com.joblivo.truth.exception.CrossUserContextException;
import com.joblivo.truth.exception.InvalidCareerContextException;
import com.joblivo.truth.exception.InvalidClaimException;
import com.joblivo.truth.model.CareerClaim;
import com.joblivo.truth.model.ClaimCategory;
import com.joblivo.truth.model.EvidenceSourceType;
import com.joblivo.truth.model.EvidenceStatus;
import com.joblivo.truth.model.TextIntegrityAssessment;
import com.joblivo.truth.model.TruthConfidence;
import com.joblivo.truth.model.TruthEvaluation;
import com.joblivo.truth.model.TruthProtectionLevel;
import com.joblivo.truth.validator.DefaultDeterministicEvidenceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TruthEngine Unit Tests")
class TruthEngineTest {

    private TruthEngine truthEngine;
    private CareerFactContext factContext;
    private UUID testUserId;

    private static final UUID WORK_EXP_ID = UUID.randomUUID();
    private static final UUID SKILL_JAVA_ID = UUID.randomUUID();
    private static final UUID SKILL_AWS_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CERT_ID = UUID.randomUUID();
    private static final UUID EDU_ID = UUID.randomUUID();
    private static final UUID ACH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        truthEngine = new DefaultTruthEngine(new DefaultDeterministicEvidenceValidator());
        testUserId = UUID.randomUUID();

        WorkExperienceResponse exp1 = new WorkExperienceResponse(
                WORK_EXP_ID,
                "CloudCorp Technologies",
                "Senior Cloud Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2021, 1, 1),
                null,
                true,
                "Seattle, WA",
                "Designed cloud-native backend microservices. Scaled production systems to handle 100k daily requests.",
                1,
                Instant.now(),
                Instant.now()
        );

        SkillResponse skillJava = new SkillResponse(
                SKILL_JAVA_ID,
                "Java",
                SkillCategory.PROGRAMMING_LANGUAGE,
                SkillProficiency.EXPERT,
                BigDecimal.valueOf(5),
                LocalDate.now(),
                1,
                Instant.now(),
                Instant.now()
        );

        SkillResponse skillAws = new SkillResponse(
                SKILL_AWS_ID,
                "AWS",
                SkillCategory.CLOUD,
                SkillProficiency.ADVANCED,
                BigDecimal.valueOf(4),
                LocalDate.now(),
                2,
                Instant.now(),
                Instant.now()
        );

        SkillResponse skillSpring = new SkillResponse(
                UUID.randomUUID(),
                "Spring Boot",
                SkillCategory.FRAMEWORK,
                SkillProficiency.ADVANCED,
                BigDecimal.valueOf(4),
                LocalDate.now(),
                3,
                Instant.now(),
                Instant.now()
        );

        ProjectResponse project1 = new ProjectResponse(
                PROJECT_ID,
                "Joblivo AI Platform",
                ProjectType.PROFESSIONAL,
                "Lead Architect",
                "Architected event-driven distributed system handling job discovery.",
                LocalDate.of(2023, 1, 1),
                null,
                true,
                "https://joblivo.com",
                1,
                Instant.now(),
                Instant.now()
        );

        CertificationResponse cert1 = new CertificationResponse(
                CERT_ID,
                "AWS Certified Solutions Architect - Associate",
                "Amazon Web Services",
                "AWS-123456",
                "https://aws.amazon.com/verify/123456",
                LocalDate.of(2022, 6, 1),
                LocalDate.of(2025, 6, 1),
                false,
                "Valid associate architecture certification",
                1,
                Instant.now(),
                Instant.now()
        );

        EducationResponse edu1 = new EducationResponse(
                EDU_ID,
                "University of Washington",
                "Bachelor of Science",
                "Computer Science",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2016, 9, 1),
                LocalDate.of(2020, 6, 1),
                false,
                "3.8 GPA",
                "Seattle, WA",
                "Focused on distributed systems and database architecture",
                1,
                Instant.now(),
                Instant.now()
        );

        AchievementResponse ach1 = new AchievementResponse(
                ACH_ID,
                "Innovator of the Year 2023",
                AchievementType.AWARD,
                "Awarded for patent-pending latency optimization reducing P99 latency by 40%",
                LocalDate.of(2023, 12, 1),
                "CloudCorp Technologies",
                null,
                1,
                Instant.now(),
                Instant.now()
        );

        SectionCompletenessResponse done = SectionCompletenessResponse.of(true, 1);
        CareerProfileCompletenessResponse completeness = new CareerProfileCompletenessResponse(
                100, done, done, done, done, done, done, done
        );

        MasterCareerProfileResponse masterProfile = new MasterCareerProfileResponse(
                UUID.randomUUID(),
                "Senior Cloud & Distributed Systems Architect",
                "Senior Cloud Engineer",
                "CloudCorp Technologies",
                60, // 5 years total experience
                "Seattle, WA",
                "Remote",
                WorkMode.REMOTE,
                30,
                Instant.now(),
                Instant.now(),
                List.of(exp1),
                List.of(skillJava, skillAws, skillSpring),
                List.of(project1),
                List.of(edu1),
                List.of(cert1),
                List.of(ach1),
                completeness
        );

        factContext = CareerFactContext.of(testUserId, masterProfile);
    }

    @Nested
    @DisplayName("A. VERIFIED Evaluations")
    class VerifiedClaimTests {

        @Test
        @DisplayName("Skill directly present in profile evaluates to VERIFIED")
        void skillVerified() {
            CareerClaim claim = CareerClaim.of("Java", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
            assertThat(eval.confidence()).isEqualTo(TruthConfidence.EXACT_MATCH);
            assertThat(eval.isSafeToUse()).isTrue();
            assertThat(eval.requiresUserConfirmation()).isFalse();
            assertThat(eval.evidenceReferences()).hasSize(1);
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.SKILL);
            assertThat(eval.evidenceReferences().get(0).referenceId()).isEqualTo(SKILL_JAVA_ID.toString());
        }

        @Test
        @DisplayName("Skill case-insensitive matching evaluates to VERIFIED")
        void skillCaseInsensitiveVerified() {
            CareerClaim claim = CareerClaim.of("java", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
        }

        @Test
        @DisplayName("Work experience matching both job title and company evaluates to VERIFIED")
        void workExperienceRoleAndCompanyVerified() {
            CareerClaim claim = CareerClaim.of("Senior Cloud Engineer at CloudCorp Technologies", ClaimCategory.EXPERIENCE);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
            assertThat(eval.confidence()).isEqualTo(TruthConfidence.EXACT_MATCH);
            assertThat(eval.evidenceReferences()).isNotEmpty();
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.WORK_EXPERIENCE);
            assertThat(eval.evidenceReferences().get(0).referenceId()).isEqualTo(WORK_EXP_ID.toString());
        }

        @Test
        @DisplayName("Current job title and current company from core profile evaluate to VERIFIED")
        void currentTitleAndCompanyVerified() {
            TruthEvaluation titleEval = truthEngine.evaluate(CareerClaim.of("Senior Cloud Engineer", ClaimCategory.JOB_TITLE), factContext);
            assertThat(titleEval.status()).isEqualTo(EvidenceStatus.VERIFIED);

            TruthEvaluation companyEval = truthEngine.evaluate(CareerClaim.of("CloudCorp Technologies", ClaimCategory.COMPANY), factContext);
            assertThat(companyEval.status()).isEqualTo(EvidenceStatus.VERIFIED);
        }

        @Test
        @DisplayName("Project existing in profile evaluates to VERIFIED")
        void projectVerified() {
            CareerClaim claim = CareerClaim.of("Joblivo AI Platform", ClaimCategory.PROJECT);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.PROJECT);
        }

        @Test
        @DisplayName("Certification existing in profile evaluates to VERIFIED")
        void certificationVerified() {
            CareerClaim claim = CareerClaim.of("AWS Certified Solutions Architect - Associate", ClaimCategory.CERTIFICATION);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.CERTIFICATION);
        }

        @Test
        @DisplayName("Education institution existing in profile evaluates to VERIFIED")
        void educationVerified() {
            CareerClaim claim = CareerClaim.of("University of Washington", ClaimCategory.EDUCATION);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.EDUCATION);
        }

        @Test
        @DisplayName("Achievement existing in profile evaluates to VERIFIED")
        void achievementVerified() {
            CareerClaim claim = CareerClaim.of("Innovator of the Year 2023", ClaimCategory.ACHIEVEMENT);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.ACHIEVEMENT);
        }

        @Test
        @DisplayName("Location matching current profile evaluates to VERIFIED")
        void locationVerified() {
            CareerClaim claim = CareerClaim.of("Seattle, WA", ClaimCategory.LOCATION);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
        }

        @Test
        @DisplayName("Metric documented in work experience description evaluates to VERIFIED")
        void metricDocumentedInDescriptionVerified() {
            CareerClaim claim = CareerClaim.of("Handled 100k daily requests", ClaimCategory.METRIC);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
        }
    }

    @Nested
    @DisplayName("B. DERIVED Evaluations")
    class DerivedClaimTests {

        @Test
        @DisplayName("Compound skill statement combining verified skills evaluates to DERIVED")
        void compoundSkillDerived() {
            CareerClaim claim = CareerClaim.of("Java and AWS", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.DERIVED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
            assertThat(eval.confidence()).isEqualTo(TruthConfidence.LOGICAL_DERIVATION);
            assertThat(eval.evidenceReferences()).hasSize(2);
        }

        @Test
        @DisplayName("Multiple comma-separated verified skills evaluate to DERIVED")
        void multipleCommaSeparatedSkillsDerived() {
            CareerClaim claim = CareerClaim.of("Java, Spring Boot, AWS", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.DERIVED);
            assertThat(eval.isSafeToUse()).isTrue();
            assertThat(eval.evidenceReferences()).hasSize(3);
        }

        @Test
        @DisplayName("Years of experience logically supported by totalExperienceMonths evaluates to DERIVED")
        void experienceDurationDerived() {
            // Profile has 60 months total experience (5 years) -> "4 years experience" is supported
            CareerClaim claim = CareerClaim.of("4+ years of experience", ClaimCategory.EXPERIENCE);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.DERIVED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
            assertThat(eval.confidence()).isEqualTo(TruthConfidence.LOGICAL_DERIVATION);
            assertThat(eval.evidenceReferences().get(0).sourceType()).isEqualTo(EvidenceSourceType.CAREER_PROFILE);
        }
    }

    @Nested
    @DisplayName("C. NEEDS_CONFIRMATION Evaluations")
    class NeedsConfirmationClaimTests {

        @Test
        @DisplayName("Job title matches but company differs or is unknown evaluates to NEEDS_CONFIRMATION")
        void jobTitleMatchesWithUnknownCompanyNeedsConfirmation() {
            CareerClaim claim = CareerClaim.of("Senior Cloud Engineer at Google", ClaimCategory.EXPERIENCE);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.NEEDS_CONFIRMATION);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.REQUIRES_CONFIRMATION);
            assertThat(eval.confidence()).isEqualTo(TruthConfidence.PARTIAL_MATCH);
            assertThat(eval.requiresUserConfirmation()).isTrue();
            assertThat(eval.isSafeToUse()).isFalse();
            assertThat(eval.reason()).contains("Job title 'Senior Cloud Engineer' is verified, but company 'Google' does not match profile");
        }

        @Test
        @DisplayName("Company matches but role differs evaluates to NEEDS_CONFIRMATION")
        void companyMatchesWithDifferentRoleNeedsConfirmation() {
            CareerClaim claim = CareerClaim.of("VP of Engineering at CloudCorp Technologies", ClaimCategory.EXPERIENCE);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.NEEDS_CONFIRMATION);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.REQUIRES_CONFIRMATION);
            assertThat(eval.requiresUserConfirmation()).isTrue();
            assertThat(eval.reason()).contains("Company 'CloudCorp Technologies' is verified, but role 'VP of Engineering' does not match profile");
        }

        @Test
        @DisplayName("Compound skill with some verified and some unverified skills evaluates to NEEDS_CONFIRMATION")
        void compoundSkillPartiallyVerifiedNeedsConfirmation() {
            CareerClaim claim = CareerClaim.of("Java and Rust", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.NEEDS_CONFIRMATION);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.REQUIRES_CONFIRMATION);
            assertThat(eval.reason()).contains("missing evidence for: Rust");
        }

        @Test
        @DisplayName("Skill with partial name overlap evaluates to NEEDS_CONFIRMATION")
        void skillPartialOverlapNeedsConfirmation() {
            // User has "Java", claim says "JavaScript" or "Java 21"
            CareerClaim claim = CareerClaim.of("Java Core Specialist", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.NEEDS_CONFIRMATION);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.REQUIRES_CONFIRMATION);
        }
    }

    @Nested
    @DisplayName("D. UNSUPPORTED Evaluations")
    class UnsupportedClaimTests {

        @Test
        @DisplayName("Technology completely absent from profile evaluates to UNSUPPORTED")
        void technologyAbsentUnsupported() {
            CareerClaim claim = CareerClaim.of("Kubernetes", ClaimCategory.SKILL);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.DO_NOT_USE);
            assertThat(eval.confidence()).isEqualTo(TruthConfidence.NONE);
            assertThat(eval.isSafeToUse()).isFalse();
            assertThat(eval.isDoNotUse()).isTrue();
            assertThat(eval.evidenceReferences()).isEmpty();
        }

        @Test
        @DisplayName("Company completely absent evaluates to UNSUPPORTED")
        void companyAbsentUnsupported() {
            CareerClaim claim = CareerClaim.of("Meta Platforms", ClaimCategory.COMPANY);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(eval.isDoNotUse()).isTrue();
        }

        @Test
        @DisplayName("Project completely absent evaluates to UNSUPPORTED")
        void projectAbsentUnsupported() {
            CareerClaim claim = CareerClaim.of("Self-Driving Car OS", ClaimCategory.PROJECT);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
        }

        @Test
        @DisplayName("Invented metric absent from profile descriptions evaluates to UNSUPPORTED")
        void inventedMetricUnsupported() {
            // User profile has 100k daily requests, but claim claims 50 production servers
            CareerClaim claim = CareerClaim.of("Managed 50 production servers", ClaimCategory.METRIC);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.DO_NOT_USE);
            assertThat(eval.reason()).contains("50");
        }

        @Test
        @DisplayName("Unearned higher tier certification evaluates to UNSUPPORTED")
        void unearnedCertificationUnsupported() {
            // User holds Associate, but claim claims Professional
            CareerClaim claim = CareerClaim.of("AWS Certified Solutions Architect - Professional", ClaimCategory.CERTIFICATION);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.DO_NOT_USE);
        }

        @Test
        @DisplayName("Experience duration exceeding verified profile experience evaluates to UNSUPPORTED")
        void experienceDurationExceededUnsupported() {
            // User has 60 months (5 years), claim claims 10 years
            CareerClaim claim = CareerClaim.of("10+ years experience", ClaimCategory.EXPERIENCE);
            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);

            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(eval.protectionLevel()).isEqualTo(TruthProtectionLevel.DO_NOT_USE);
            assertThat(eval.reason()).contains("exceeds verified total experience");
        }
    }

    @Nested
    @DisplayName("E. Security, Privacy & Boundary Protection")
    class SecurityAndBoundaryTests {

        @Test
        @DisplayName("Cross-user claim evaluation throws CrossUserContextException")
        void crossUserEvaluationFails() {
            UUID differentUserId = UUID.randomUUID();
            CareerClaim claim = CareerClaim.builder()
                    .text("Java")
                    .category(ClaimCategory.SKILL)
                    .userId(differentUserId)
                    .build();

            assertThatThrownBy(() -> truthEngine.evaluate(claim, factContext))
                    .isInstanceOf(CrossUserContextException.class)
                    .hasMessageContaining("User mismatch");
        }

        @Test
        @DisplayName("Claim with matching user ID evaluates normally")
        void matchingUserIdEvaluatesNormally() {
            CareerClaim claim = CareerClaim.builder()
                    .text("Java")
                    .category(ClaimCategory.SKILL)
                    .userId(testUserId)
                    .build();

            TruthEvaluation eval = truthEngine.evaluate(claim, factContext);
            assertThat(eval.isVerified()).isTrue();
        }

        @Test
        @DisplayName("Null claim throws InvalidClaimException")
        void nullClaimThrowsException() {
            assertThatThrownBy(() -> truthEngine.evaluate(null, factContext))
                    .isInstanceOf(InvalidClaimException.class)
                    .hasMessageContaining("CareerClaim must not be null");
        }

        @Test
        @DisplayName("Blank claim text throws InvalidClaimException")
        void blankClaimTextThrowsException() {
            assertThatThrownBy(() -> CareerClaim.of("   ", ClaimCategory.SKILL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Claim text must not be null or blank");
        }

        @Test
        @DisplayName("Null category throws NullPointerException")
        void nullCategoryThrowsException() {
            assertThatThrownBy(() -> CareerClaim.of("Java", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ClaimCategory must not be null");
        }

        @Test
        @DisplayName("Null CareerFactContext throws InvalidCareerContextException")
        void nullContextThrowsException() {
            CareerClaim claim = CareerClaim.of("Java", ClaimCategory.SKILL);
            assertThatThrownBy(() -> truthEngine.evaluate(claim, null))
                    .isInstanceOf(InvalidCareerContextException.class);
        }

        @Test
        @DisplayName("Empty context with null profile evaluates claims as UNSUPPORTED without crashing")
        void emptyContextEvaluatesAsUnsupported() {
            CareerFactContext emptyContext = CareerFactContext.empty(testUserId);
            CareerClaim claim = CareerClaim.of("Java", ClaimCategory.SKILL);

            TruthEvaluation eval = truthEngine.evaluate(claim, emptyContext);
            assertThat(eval.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(eval.isDoNotUse()).isTrue();
        }
    }

    @Nested
    @DisplayName("F. Text Transformation & Rewrite Integrity")
    class TextTransformationContractTests {

        @Test
        @DisplayName("Rewrite that introduces an unsupported technology evaluates to UNSUPPORTED")
        void rewriteIntroducingUnsupportedTechnologyFails() {
            String source = "Developed microservices in Java.";
            String proposed = "Architected high-throughput microservices in Java and Kubernetes.";

            TextIntegrityAssessment assessment = truthEngine.assessTransformation(source, proposed, factContext);

            assertThat(assessment.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(assessment.protectionLevel()).isEqualTo(TruthProtectionLevel.DO_NOT_USE);
            assertThat(assessment.hasUnsupportedFacts()).isTrue();
            assertThat(assessment.reason()).contains("Introduced unsupported technology: 'Kubernetes'");
        }

        @Test
        @DisplayName("Rewrite that introduces an invented metric evaluates to UNSUPPORTED")
        void rewriteIntroducingInventedMetricFails() {
            String source = "Optimized backend cloud latency.";
            String proposed = "Optimized backend cloud latency, managing a fleet of 50 production servers.";

            TextIntegrityAssessment assessment = truthEngine.assessTransformation(source, proposed, factContext);

            assertThat(assessment.status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(assessment.protectionLevel()).isEqualTo(TruthProtectionLevel.DO_NOT_USE);
            assertThat(assessment.hasUnsupportedFacts()).isTrue();
            assertThat(assessment.reason()).contains("Introduced unsupported metric: '50'");
        }

        @Test
        @DisplayName("Clean wording improvement preserving facts evaluates to VERIFIED")
        void cleanWordingImprovementPasses() {
            String source = "I write backend code in Java.";
            String proposed = "Engineered resilient backend distributed services using Java.";

            TextIntegrityAssessment assessment = truthEngine.assessTransformation(source, proposed, factContext);

            assertThat(assessment.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(assessment.protectionLevel()).isEqualTo(TruthProtectionLevel.SAFE_TO_USE);
            assertThat(assessment.hasUnsupportedFacts()).isFalse();
            assertThat(assessment.isSafeToUse()).isTrue();
        }

        @Test
        @DisplayName("Rewrite introducing already-verified profile skill evaluates to VERIFIED")
        void rewriteAddingVerifiedSkillPasses() {
            String source = "Worked on cloud services in Java.";
            // "AWS" is verified in the user's profile
            String proposed = "Engineered enterprise cloud services using Java on AWS.";

            TextIntegrityAssessment assessment = truthEngine.assessTransformation(source, proposed, factContext);

            assertThat(assessment.status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(assessment.isSafeToUse()).isTrue();
        }
    }

    @Nested
    @DisplayName("G. Batch Evaluation & Operational Contracts")
    class BatchEvaluationTests {

        @Test
        @DisplayName("evaluateAll processes all claims and preserves correlation IDs")
        void batchEvaluationPreservesDetails() {
            List<CareerClaim> claims = List.of(
                    CareerClaim.builder().text("Java").category(ClaimCategory.SKILL).correlationId("corr-1").build(),
                    CareerClaim.builder().text("AWS").category(ClaimCategory.SKILL).correlationId("corr-2").build(),
                    CareerClaim.builder().text("Kubernetes").category(ClaimCategory.SKILL).correlationId("corr-3").build()
            );

            List<TruthEvaluation> results = truthEngine.evaluateAll(claims, factContext);

            assertThat(results).hasSize(3);
            assertThat(results.get(0).status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(results.get(0).correlationId()).isEqualTo("corr-1");

            assertThat(results.get(1).status()).isEqualTo(EvidenceStatus.VERIFIED);
            assertThat(results.get(1).correlationId()).isEqualTo("corr-2");

            assertThat(results.get(2).status()).isEqualTo(EvidenceStatus.UNSUPPORTED);
            assertThat(results.get(2).correlationId()).isEqualTo("corr-3");
        }

        @Test
        @DisplayName("isSafeToUse returns true only for verified and derived claims")
        void isSafeToUseReturnsExpected() {
            assertThat(truthEngine.isSafeToUse(CareerClaim.of("Java", ClaimCategory.SKILL), factContext)).isTrue();
            assertThat(truthEngine.isSafeToUse(CareerClaim.of("Java and AWS", ClaimCategory.SKILL), factContext)).isTrue();
            assertThat(truthEngine.isSafeToUse(CareerClaim.of("Java and Rust", ClaimCategory.SKILL), factContext)).isFalse();
            assertThat(truthEngine.isSafeToUse(CareerClaim.of("Rust", ClaimCategory.SKILL), factContext)).isFalse();
        }
    }
}

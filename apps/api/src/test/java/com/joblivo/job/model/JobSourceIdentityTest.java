package com.joblivo.job.model;

import com.joblivo.job.exception.JobValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobSourceIdentity Unit Tests")
class JobSourceIdentityTest {

    @Nested
    @DisplayName("Creation and Validation")
    class CreationAndValidationTests {

        @Test
        @DisplayName("1. Valid source + externalJobId creates a stable identity")
        void validSourceAndExternalJobId() {
            JobSourceIdentity identity = new JobSourceIdentity(JobSource.LINKEDIN, "job-12345");

            assertThat(identity.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(identity.externalJobId()).isEqualTo("job-12345");
        }

        @Test
        @DisplayName("2. Surrounding whitespace is trimmed and normalized")
        void surroundingWhitespaceIsTrimmed() {
            JobSourceIdentity identity = new JobSourceIdentity(JobSource.NAUKRI, "   job-999   ");

            assertThat(identity.externalJobId()).isEqualTo("job-999");
        }

        @Test
        @DisplayName("3. Blank externalJobId is rejected safely with JobValidationException")
        void blankExternalJobIdRejected() {
            assertThatThrownBy(() -> new JobSourceIdentity(JobSource.LINKEDIN, "   "))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");

            assertThatThrownBy(() -> new JobSourceIdentity(JobSource.LINKEDIN, ""))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");
        }

        @Test
        @DisplayName("Null externalJobId is rejected safely with JobValidationException")
        void nullExternalJobIdRejected() {
            assertThatThrownBy(() -> new JobSourceIdentity(JobSource.LINKEDIN, null))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("externalJobId must not be null or blank");
        }

        @Test
        @DisplayName("Null source is rejected safely with JobValidationException")
        void nullSourceRejected() {
            assertThatThrownBy(() -> new JobSourceIdentity(null, "job-123"))
                    .isInstanceOf(JobValidationException.class)
                    .hasMessageContaining("JobSource must not be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "JOB-123_abc",
                "req/456/v2",
                "EXT:987-xyz.v1",
                "career?id=42&loc=remote",
                "Job#123;sub=4"
        })
        @DisplayName("4. Meaningful internal characters and punctuation are preserved")
        void meaningfulInternalCharactersPreserved(String rawId) {
            JobSourceIdentity identity = new JobSourceIdentity(JobSource.COMPANY_CAREERS, "  " + rawId + "  ");
            assertThat(identity.externalJobId()).isEqualTo(rawId);
        }

        @Test
        @DisplayName("5. Case behavior is deterministic (preserved, never forcefully lowercased)")
        void caseBehaviorIsDeterministic() {
            JobSourceIdentity identity = new JobSourceIdentity(JobSource.ATS, "MyJob-Id-ABC");
            assertThat(identity.externalJobId()).isEqualTo("MyJob-Id-ABC");
            assertThat(identity.externalJobId()).isNotEqualTo("myjob-id-abc");
        }
    }

    @Nested
    @DisplayName("Equality and Identity Decoupling")
    class EqualityAndDecouplingTests {

        @Test
        @DisplayName("6. Same source + same normalized externalJobId produces equal identities")
        void sameSourceAndNormalizedIdEqual() {
            JobSourceIdentity id1 = new JobSourceIdentity(JobSource.LINKEDIN, "job-42");
            JobSourceIdentity id2 = new JobSourceIdentity(JobSource.LINKEDIN, "  job-42  ");

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
            assertThat(id1.toString()).isEqualTo(id2.toString());
        }

        @Test
        @DisplayName("7. Different sources with the same externalJobId remain different identities")
        void differentSourcesAreDistinct() {
            JobSourceIdentity linkedin = new JobSourceIdentity(JobSource.LINKEDIN, "job-42");
            JobSourceIdentity naukri = new JobSourceIdentity(JobSource.NAUKRI, "job-42");

            assertThat(linkedin).isNotEqualTo(naukri);
            assertThat(linkedin.hashCode()).isNotEqualTo(naukri.hashCode());
        }

        @Test
        @DisplayName("8. Source identity does not depend on title/company/location")
        void sourceIdentityDecoupledFromJobAttributes() {
            // Identity represents ONLY source + externalJobId
            JobSourceIdentity idA = new JobSourceIdentity(JobSource.LINKEDIN, "job-100");
            JobSourceIdentity idB = new JobSourceIdentity(JobSource.LINKEDIN, "job-100");

            // Even if two jobs had completely different titles ("Backend Eng" vs "Frontend Lead"),
            // their source identity is purely defined by source + externalJobId.
            assertThat(idA).isEqualTo(idB);
        }

        @Test
        @DisplayName("9. Missing externalJobId is never replaced with a generated/fabricated value")
        void missingExternalJobIdNeverFabricated() {
            // The factory method of() returns empty when ID is absent; never fabricates a UUID or guessed ID
            Optional<JobSourceIdentity> opt1 = JobSourceIdentity.of(JobSource.LINKEDIN, null);
            Optional<JobSourceIdentity> opt2 = JobSourceIdentity.of(JobSource.LINKEDIN, "");
            Optional<JobSourceIdentity> opt3 = JobSourceIdentity.of(JobSource.LINKEDIN, "   ");
            Optional<JobSourceIdentity> opt4 = JobSourceIdentity.of(null, "job-123");

            assertThat(opt1).isEmpty();
            assertThat(opt2).isEmpty();
            assertThat(opt3).isEmpty();
            assertThat(opt4).isEmpty();
        }

        @Test
        @DisplayName("Factory method of() returns populated Optional for valid input")
        void factoryMethodReturnsPopulatedOptional() {
            Optional<JobSourceIdentity> opt = JobSourceIdentity.of(JobSource.LINKEDIN, "  job-valid-1  ");

            assertThat(opt).isPresent();
            assertThat(opt.get().source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(opt.get().externalJobId()).isEqualTo("job-valid-1");
        }
    }
}

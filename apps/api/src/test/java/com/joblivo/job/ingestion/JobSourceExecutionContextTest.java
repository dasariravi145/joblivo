package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobSourceExecutionContext Unit Tests")
class JobSourceExecutionContextTest {

    @Test
    @DisplayName("1. Valid execution context creation succeeds with trimmed runId")
    void validExecutionContextCreation() {
        JobSourceExecutionContext context = new JobSourceExecutionContext(
                "  run-abc-123  ",
                JobSource.LINKEDIN,
                50
        );

        assertThat(context.runId()).isEqualTo("run-abc-123");
        assertThat(context.source()).isEqualTo(JobSource.LINKEDIN);
        assertThat(context.candidateLimit()).isEqualTo(50);
    }

    @Test
    @DisplayName("Factory method creates valid execution context")
    void factoryMethodCreatesContext() {
        JobSourceExecutionContext context = JobSourceExecutionContext.of("run-999", JobSource.NAUKRI, 100);

        assertThat(context.runId()).isEqualTo("run-999");
        assertThat(context.source()).isEqualTo(JobSource.NAUKRI);
        assertThat(context.candidateLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("2. Missing (null) runId is rejected")
    void nullRunIdRejected() {
        assertThatThrownBy(() -> new JobSourceExecutionContext(null, JobSource.LINKEDIN, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId must not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("2. Blank runId is rejected")
    void blankRunIdRejected(String blankRunId) {
        assertThatThrownBy(() -> new JobSourceExecutionContext(blankRunId, JobSource.LINKEDIN, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId must not be null or blank");
    }

    @Test
    @DisplayName("3. Missing (null) source is rejected")
    void nullSourceRejected() {
        assertThatThrownBy(() -> new JobSourceExecutionContext("run-123", null, 50))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source must not be null");
    }

    @Test
    @DisplayName("4. Zero candidateLimit is rejected")
    void zeroCandidateLimitRejected() {
        assertThatThrownBy(() -> new JobSourceExecutionContext("run-123", JobSource.LINKEDIN, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidateLimit must be positive (got 0)");
    }

    @Test
    @DisplayName("4. Negative candidateLimit is rejected")
    void negativeCandidateLimitRejected() {
        assertThatThrownBy(() -> new JobSourceExecutionContext("run-123", JobSource.LINKEDIN, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidateLimit must be positive (got -10)");
    }

    @Test
    @DisplayName("Execution context does not expose sensitive credentials or tokens")
    void noCredentialsInExecutionContext() {
        JobSourceExecutionContext context = JobSourceExecutionContext.of("run-sec-1", JobSource.COMPANY_CAREERS, 25);
        String stringRep = context.toString();

        assertThat(stringRep).doesNotContain("password");
        assertThat(stringRep).doesNotContain("token");
        assertThat(stringRep).doesNotContain("secret");
        assertThat(stringRep).doesNotContain("Authorization");
    }
}

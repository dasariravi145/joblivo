package com.joblivo.job.ingestion;

import com.joblivo.job.model.JobSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ingestion Run Lifecycle & Operational State Tests")
class IngestionRunLifecycleTest {

    @Test
    @DisplayName("Lifecycle status terminality is correctly evaluated")
    void lifecycleStatusTerminality() {
        assertThat(IngestionRunStatus.STARTED.isTerminal()).isFalse();
        assertThat(IngestionRunStatus.RUNNING.isTerminal()).isFalse();
        assertThat(IngestionRunStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(IngestionRunStatus.COMPLETED_WITH_ERRORS.isTerminal()).isTrue();
        assertThat(IngestionRunStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Lifecycle status allows valid forward transitions")
    void validLifecycleTransitions() {
        assertThat(IngestionRunStatus.STARTED.canTransitionTo(IngestionRunStatus.RUNNING)).isTrue();
        assertThat(IngestionRunStatus.STARTED.canTransitionTo(IngestionRunStatus.FAILED)).isTrue();
        assertThat(IngestionRunStatus.STARTED.canTransitionTo(IngestionRunStatus.COMPLETED)).isFalse();

        assertThat(IngestionRunStatus.RUNNING.canTransitionTo(IngestionRunStatus.COMPLETED)).isTrue();
        assertThat(IngestionRunStatus.RUNNING.canTransitionTo(IngestionRunStatus.COMPLETED_WITH_ERRORS)).isTrue();
        assertThat(IngestionRunStatus.RUNNING.canTransitionTo(IngestionRunStatus.FAILED)).isTrue();
        assertThat(IngestionRunStatus.RUNNING.canTransitionTo(IngestionRunStatus.STARTED)).isFalse();
    }

    @Test
    @DisplayName("Terminal lifecycle statuses cannot transition to any status")
    void terminalStatusesRejectTransitions() {
        for (IngestionRunStatus terminal : List.of(
                IngestionRunStatus.COMPLETED,
                IngestionRunStatus.COMPLETED_WITH_ERRORS,
                IngestionRunStatus.FAILED
        )) {
            for (IngestionRunStatus any : IngestionRunStatus.values()) {
                assertThat(terminal.canTransitionTo(any)).isFalse();
                assertThatThrownBy(() -> terminal.validateTransition(any))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Invalid ingestion run lifecycle transition");
            }
        }
    }

    @Test
    @DisplayName("IngestionRunContext tracks lifecycle from STARTED to RUNNING to COMPLETED")
    void runContextNormalLifecycle() {
        String runId = "run-1234";
        IngestionRunContext context = IngestionRunContext.start(runId, JobSource.LINKEDIN);

        assertThat(context.runId()).isEqualTo(runId);
        assertThat(context.status()).isEqualTo(IngestionRunStatus.STARTED);
        assertThat(context.sources()).containsExactly(JobSource.LINKEDIN);
        assertThat(context.startedAt()).isNotNull();

        IngestionRunContext running = context.toRunning();
        assertThat(running.status()).isEqualTo(IngestionRunStatus.RUNNING);
        assertThat(running.runId()).isEqualTo(runId);
        assertThat(running.sources()).containsExactly(JobSource.LINKEDIN);

        IngestionRunContext completed = running.toCompleted();
        assertThat(completed.status()).isEqualTo(IngestionRunStatus.COMPLETED);
    }

    @Test
    @DisplayName("IngestionRunContext transitions to COMPLETED_WITH_ERRORS when errors occur")
    void runContextCompletedWithErrors() {
        IngestionRunContext context = IngestionRunContext.start("run-5678", JobSource.NAUKRI)
                .toRunning()
                .toCompletedWithErrors();

        assertThat(context.status()).isEqualTo(IngestionRunStatus.COMPLETED_WITH_ERRORS);
    }

    @Test
    @DisplayName("IngestionRunContext transitions to FAILED when fatal orchestration error occurs")
    void runContextFailed() {
        IngestionRunContext context = IngestionRunContext.start("run-fatal", JobSource.LINKEDIN)
                .toRunning()
                .toFailed();

        assertThat(context.status()).isEqualTo(IngestionRunStatus.FAILED);
    }

    @Test
    @DisplayName("IngestionRunContext rejects backward and invalid transitions")
    void runContextRejectsBackwardTransitions() {
        IngestionRunContext context = IngestionRunContext.start("run-reject", JobSource.LINKEDIN)
                .toRunning()
                .toCompleted();

        assertThatThrownBy(context::toRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid ingestion run lifecycle transition from 'COMPLETED' to 'RUNNING'");

        assertThatThrownBy(context::toCompletedWithErrors)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid ingestion run lifecycle transition from 'COMPLETED' to 'COMPLETED_WITH_ERRORS'");
    }

    @Test
    @DisplayName("IngestionRunResult calculates valid duration and preserves status")
    void runResultDurationAndStatus() {
        Instant startedAt = Instant.parse("2026-09-15T10:00:00Z");
        Instant completedAt = Instant.parse("2026-09-15T10:00:05.500Z");

        IngestionRunResult result = IngestionRunResult.builder()
                .runId("run-dur-1")
                .source(JobSource.LINKEDIN)
                .status(IngestionRunStatus.COMPLETED)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .totalCandidates(10)
                .createdCount(5)
                .updatedCount(5)
                .build();

        assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
        assertThat(result.duration()).isEqualTo(Duration.ofMillis(5500));
        assertThat(result.durationMs()).isEqualTo(5500);
    }

    @Test
    @DisplayName("MultiSourceIngestionRunResult calculates valid duration and status")
    void multiSourceRunResultDurationAndStatus() {
        Instant startedAt = Instant.parse("2026-09-15T10:00:00Z");
        Instant completedAt = Instant.parse("2026-09-15T10:00:02Z");

        MultiSourceIngestionRunResult result = new MultiSourceIngestionRunResult(
                "multi-run-1",
                IngestionRunStatus.COMPLETED,
                startedAt,
                completedAt,
                2,
                10,
                5,
                5,
                0,
                0,
                10,
                List.of(),
                10,
                10
        );

        assertThat(result.status()).isEqualTo(IngestionRunStatus.COMPLETED);
        assertThat(result.duration()).isEqualTo(Duration.ofSeconds(2));
        assertThat(result.durationMs()).isEqualTo(2000);
    }

    @Test
    @DisplayName("IngestionRunContext handles multiple sources with deterministic ordering")
    void runContextMultiSourceDeterministicOrdering() {
        IngestionRunContext context = IngestionRunContext.start("multi-context", Set.of(JobSource.NAUKRI, JobSource.LINKEDIN));
        assertThat(context.sources()).containsExactly(JobSource.LINKEDIN, JobSource.NAUKRI);
    }
}

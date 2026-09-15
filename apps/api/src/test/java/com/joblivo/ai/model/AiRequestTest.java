package com.joblivo.ai.model;

import com.joblivo.ai.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiRequest Unit Tests")
class AiRequestTest {

    @Test
    @DisplayName("Valid AiRequest construction with all fields via builder")
    void validRequest_ConstructedSuccessfully() {
        UUID userId = UUID.randomUUID();
        String correlationId = "corr-12345";

        AiRequest request = AiRequest.builder()
                .purpose("CAREER_SUMMARY")
                .prompt("Extract key achievements")
                .systemInstruction("You are a career assistant")
                .requestedProvider(AiProvider.BEDROCK)
                .requestedModel("anthropic.claude-3-5-sonnet")
                .temperature(0.7)
                .maxOutputTokens(1024)
                .correlationId(correlationId)
                .userId(userId)
                .build();

        assertThat(request.purpose()).isEqualTo("CAREER_SUMMARY");
        assertThat(request.prompt()).isEqualTo("Extract key achievements");
        assertThat(request.systemInstruction()).isEqualTo("You are a career assistant");
        assertThat(request.requestedProvider()).isEqualTo(AiProvider.BEDROCK);
        assertThat(request.requestedModel()).isEqualTo("anthropic.claude-3-5-sonnet");
        assertThat(request.temperature()).isEqualTo(0.7);
        assertThat(request.maxOutputTokens()).isEqualTo(1024);
        assertThat(request.correlationId()).isEqualTo(correlationId);
        assertThat(request.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Auto-generates correlationId if not specified or blank")
    void autoGeneratesCorrelationId_WhenOmitted() {
        AiRequest request = AiRequest.builder()
                .prompt("Hello AI")
                .build();

        assertThat(request.correlationId()).isNotNull().isNotBlank();
        // Should be valid UUID string
        assertThat(UUID.fromString(request.correlationId())).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Throws IllegalArgumentException if prompt is null or blank")
    void promptNullOrBlank_ThrowsException(String invalidPrompt) {
        assertThatThrownBy(() -> AiRequest.builder().prompt(invalidPrompt).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt must not be null or blank");
    }

    @Test
    @DisplayName("Temperature boundary validations: 0.0 and 2.0 are valid; outside throws")
    void temperatureBoundaries_ValidatedCorrectly() {
        // Valid edge cases
        AiRequest minTemp = AiRequest.builder().prompt("test").temperature(0.0).build();
        assertThat(minTemp.temperature()).isEqualTo(0.0);

        AiRequest maxTemp = AiRequest.builder().prompt("test").temperature(2.0).build();
        assertThat(maxTemp.temperature()).isEqualTo(2.0);

        // Null is allowed (lets provider use default)
        AiRequest nullTemp = AiRequest.builder().prompt("test").temperature(null).build();
        assertThat(nullTemp.temperature()).isNull();

        // Below 0.0 throws
        assertThatThrownBy(() -> AiRequest.builder().prompt("test").temperature(-0.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temperature must be between 0.0 and 2.0");

        // Above 2.0 throws
        assertThatThrownBy(() -> AiRequest.builder().prompt("test").temperature(2.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temperature must be between 0.0 and 2.0");
    }

    @Test
    @DisplayName("Max output tokens must be positive if specified")
    void maxOutputTokens_MustBePositive() {
        // Valid
        AiRequest valid = AiRequest.builder().prompt("test").maxOutputTokens(500).build();
        assertThat(valid.maxOutputTokens()).isEqualTo(500);

        // Null is allowed
        AiRequest nullTokens = AiRequest.builder().prompt("test").maxOutputTokens(null).build();
        assertThat(nullTokens.maxOutputTokens()).isNull();

        // Zero throws
        assertThatThrownBy(() -> AiRequest.builder().prompt("test").maxOutputTokens(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max output tokens must be positive");

        // Negative throws
        assertThatThrownBy(() -> AiRequest.builder().prompt("test").maxOutputTokens(-10).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max output tokens must be positive");
    }

    @Test
    @DisplayName("Trims whitespace for purpose and requestedModel; converts blank system instruction to null")
    void trimsFields_Properly() {
        AiRequest request = AiRequest.builder()
                .prompt("test")
                .purpose("  CAREER_SUMMARY  ")
                .requestedModel("  claude-3-5  ")
                .systemInstruction("   ")
                .build();

        assertThat(request.purpose()).isEqualTo("CAREER_SUMMARY");
        assertThat(request.requestedModel()).isEqualTo("claude-3-5");
        assertThat(request.systemInstruction()).isNull();
    }
}

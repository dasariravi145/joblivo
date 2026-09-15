package com.joblivo.ai.model;

import com.joblivo.ai.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiResponse Unit Tests")
class AiResponseTest {

    @Test
    @DisplayName("Valid AiResponse construction with all fields via builder")
    void validResponse_ConstructedSuccessfully() {
        AiTokenUsage usage = AiTokenUsage.of(50, 150, 200);

        AiResponse response = AiResponse.builder()
                .generatedContent("Professional Summary Content")
                .provider(AiProvider.BEDROCK)
                .model("anthropic.claude-3-5-sonnet")
                .correlationId("trace-12345")
                .tokenUsage(usage)
                .finishReason("STOP")
                .durationMs(420)
                .build();

        assertThat(response.generatedContent()).isEqualTo("Professional Summary Content");
        assertThat(response.provider()).isEqualTo(AiProvider.BEDROCK);
        assertThat(response.model()).isEqualTo("anthropic.claude-3-5-sonnet");
        assertThat(response.correlationId()).isEqualTo("trace-12345");
        assertThat(response.tokenUsage()).isEqualTo(usage);
        assertThat(response.finishReason()).isEqualTo("STOP");
        assertThat(response.durationMs()).isEqualTo(420);
    }

    @Test
    @DisplayName("Throws NullPointerException if required fields are null")
    void requiredFieldsNull_ThrowsException() {
        assertThatThrownBy(() -> new AiResponse(null, AiProvider.OPENAI, "gpt-4o", "trace-1", null, null, 100))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("generatedContent must not be null");

        assertThatThrownBy(() -> new AiResponse("content", null, "gpt-4o", "trace-1", null, null, 100))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider must not be null");

        assertThatThrownBy(() -> new AiResponse("content", AiProvider.OPENAI, null, "trace-1", null, null, 100))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("model must not be null");

        assertThatThrownBy(() -> new AiResponse("content", AiProvider.OPENAI, "gpt-4o", null, null, null, 100))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("correlationId must not be null");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException if durationMs is negative")
    void negativeDuration_ThrowsException() {
        assertThatThrownBy(() -> new AiResponse("content", AiProvider.OPENAI, "gpt-4o", "trace-1", null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs must not be negative");
    }

    @Test
    @DisplayName("AiTokenUsage correctly stores and exposes token counts")
    void tokenUsage_StoresCorrectCounts() {
        AiTokenUsage usage = new AiTokenUsage(100, 250, 350);
        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(250);
        assertThat(usage.totalTokens()).isEqualTo(350);
    }
}

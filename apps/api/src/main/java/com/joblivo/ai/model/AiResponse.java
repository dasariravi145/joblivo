package com.joblivo.ai.model;

import com.joblivo.ai.AiProvider;

import java.util.Objects;

/**
 * Provider-neutral response model representing the normalized result of an AI generation call.
 */
public record AiResponse(
        String generatedContent,
        AiProvider provider,
        String model,
        String correlationId,
        AiTokenUsage tokenUsage,
        String finishReason,
        long durationMs
) {
    public AiResponse {
        Objects.requireNonNull(generatedContent, "generatedContent must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative, got: " + durationMs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String generatedContent;
        private AiProvider provider;
        private String model;
        private String correlationId;
        private AiTokenUsage tokenUsage;
        private String finishReason;
        private long durationMs;

        public Builder generatedContent(String generatedContent) {
            this.generatedContent = generatedContent;
            return this;
        }

        public Builder provider(AiProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder tokenUsage(AiTokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public AiResponse build() {
            return new AiResponse(
                    generatedContent,
                    provider,
                    model,
                    correlationId,
                    tokenUsage,
                    finishReason,
                    durationMs
            );
        }
    }
}

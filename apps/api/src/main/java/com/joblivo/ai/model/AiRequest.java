package com.joblivo.ai.model;

import com.joblivo.ai.AiProvider;

import java.util.UUID;

/**
 * Immutable, provider-neutral request model for internal AI inference calls.
 * Business services construct this model to interact with the AI Gateway without
 * coupling to specific provider SDKs.
 */
public record AiRequest(
        String purpose,
        String prompt,
        String systemInstruction,
        AiProvider requestedProvider,
        String requestedModel,
        Double temperature,
        Integer maxOutputTokens,
        String correlationId,
        UUID userId
) {
    public AiRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be null or blank");
        }
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0, got: " + temperature);
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("Max output tokens must be positive, got: " + maxOutputTokens);
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = correlationId.trim();
        }
        if (purpose != null) {
            purpose = purpose.trim();
        }
        if (requestedModel != null) {
            requestedModel = requestedModel.trim();
        }
        if (systemInstruction != null && systemInstruction.isBlank()) {
            systemInstruction = null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String purpose;
        private String prompt;
        private String systemInstruction;
        private AiProvider requestedProvider;
        private String requestedModel;
        private Double temperature;
        private Integer maxOutputTokens;
        private String correlationId;
        private UUID userId;

        public Builder purpose(String purpose) {
            this.purpose = purpose;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder systemInstruction(String systemInstruction) {
            this.systemInstruction = systemInstruction;
            return this;
        }

        public Builder requestedProvider(AiProvider requestedProvider) {
            this.requestedProvider = requestedProvider;
            return this;
        }

        public Builder requestedModel(String requestedModel) {
            this.requestedModel = requestedModel;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public AiRequest build() {
            return new AiRequest(
                    purpose,
                    prompt,
                    systemInstruction,
                    requestedProvider,
                    requestedModel,
                    temperature,
                    maxOutputTokens,
                    correlationId,
                    userId
            );
        }
    }
}

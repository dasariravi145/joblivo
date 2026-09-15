package com.joblivo.ai.model;

/**
 * Normalized token usage metrics for an AI generation request.
 */
public record AiTokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
    public static AiTokenUsage of(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new AiTokenUsage(promptTokens, completionTokens, totalTokens);
    }
}

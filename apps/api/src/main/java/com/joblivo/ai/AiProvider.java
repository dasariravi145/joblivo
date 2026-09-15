package com.joblivo.ai;

import java.util.Arrays;
import java.util.Optional;

/**
 * Provider-neutral enumeration of supported AI backends in Joblivo.
 * Serves as an architectural configuration and routing identifier.
 */
public enum AiProvider {
    BEDROCK,
    OPENAI,
    GOOGLE;

    /**
     * Case-insensitive lookup helper for string provider identifiers.
     *
     * @param value the string identifier
     * @return Optional containing the matching AiProvider, or empty if unmatched/null
     */
    public static Optional<AiProvider> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}

package com.joblivo.truth.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a factual career claim to be evaluated by the {@link com.joblivo.truth.TruthEngine}.
 *
 * @param text          the raw claim statement being evaluated (e.g. "AWS", "Senior Software Engineer at Acme")
 * @param category      the domain category of the claim
 * @param userId        the owner of the claim (if explicitly asserted, enabling user-boundary validation)
 * @param correlationId safe tracking identifier for auditability
 * @param attributes    optional structured attributes (e.g. "metric": "50", "company": "Acme")
 */
public record CareerClaim(
        String text,
        ClaimCategory category,
        UUID userId,
        String correlationId,
        Map<String, String> attributes
) {
    public CareerClaim {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Claim text must not be null or blank");
        }
        text = text.trim();
        Objects.requireNonNull(category, "ClaimCategory must not be null");
        attributes = attributes != null ? Collections.unmodifiableMap(new HashMap<>(attributes)) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CareerClaim of(String text, ClaimCategory category) {
        return new CareerClaim(text, category, null, null, Collections.emptyMap());
    }

    public static CareerClaim of(String text, ClaimCategory category, UUID userId) {
        return new CareerClaim(text, category, userId, null, Collections.emptyMap());
    }

    public static class Builder {
        private String text;
        private ClaimCategory category;
        private UUID userId;
        private String correlationId;
        private Map<String, String> attributes = new HashMap<>();

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder category(ClaimCategory category) {
            this.category = category;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        public CareerClaim build() {
            return new CareerClaim(text, category, userId, correlationId, attributes);
        }
    }
}

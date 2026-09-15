package com.joblivo.ai.routing;

import com.joblivo.ai.AiProvider;

import java.util.Objects;

/**
 * Immutable encapsulation of the resolved target AI provider, model identifier, and routing provenance.
 */
public record ResolvedModelRoute(
        AiProvider provider,
        String model,
        String purpose,
        RoutingSource source
) {
    public ResolvedModelRoute {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(model, "model must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.trim();
        if (purpose != null && purpose.isBlank()) {
            purpose = null;
        } else if (purpose != null) {
            purpose = purpose.trim();
        }
        if (source == null) {
            source = RoutingSource.GLOBAL_DEFAULT;
        }
    }

    /**
     * Backward-compatible constructor defaulting purpose to null and source to GLOBAL_DEFAULT.
     */
    public ResolvedModelRoute(AiProvider provider, String model) {
        this(provider, model, null, RoutingSource.GLOBAL_DEFAULT);
    }
}

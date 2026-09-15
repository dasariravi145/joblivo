package com.joblivo.ai.routing;

/**
 * Identifies the architectural origin/policy mechanism by which an AI route was resolved.
 */
public enum RoutingSource {
    /**
     * Explicitly requested at the request level (provider and/or model).
     */
    EXPLICIT,

    /**
     * Resolved from a configured purpose/use-case mapping.
     */
    PURPOSE,

    /**
     * Resolved from provider-specific default model configuration.
     */
    PROVIDER_DEFAULT,

    /**
     * Resolved from global default provider and model configuration.
     */
    GLOBAL_DEFAULT
}

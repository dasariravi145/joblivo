package com.joblivo.ai.routing;

import com.joblivo.ai.model.AiRequest;

/**
 * Provider-neutral router determining which AI provider and model identifier
 * to use for a given AI request.
 */
public interface ModelRouter {

    /**
     * Resolves the target provider and model for the incoming request based on configured policies,
     * request-specific overrides, and purpose-based mappings.
     *
     * @param request the validated AI request
     * @return the resolved route containing provider and model
     * @throws com.joblivo.ai.exception.AiRoutingException if the route cannot be resolved or provider is disabled
     * @throws com.joblivo.ai.exception.AiConfigurationException if AI configuration is disabled or invalid
     */
    ResolvedModelRoute route(AiRequest request);
}

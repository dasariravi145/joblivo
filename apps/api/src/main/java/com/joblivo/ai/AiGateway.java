package com.joblivo.ai;

import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;

/**
 * Provider-neutral entry point for internal AI inference and generation in Joblivo.
 * Business services depend strictly on this gateway and never on provider SDKs directly.
 */
public interface AiGateway {

    /**
     * Generates a normalized AI response for the given provider-neutral request.
     *
     * @param request the validated AI request
     * @return normalized AI response containing generated content, metadata, and token usage
     * @throws com.joblivo.ai.exception.AiConfigurationException if AI is disabled or misconfigured
     * @throws com.joblivo.ai.exception.AiRoutingException if the request cannot be routed to an enabled provider/model
     * @throws com.joblivo.ai.exception.AiProviderException if no provider implementation exists or provider execution fails
     */
    AiResponse generate(AiRequest request);
}

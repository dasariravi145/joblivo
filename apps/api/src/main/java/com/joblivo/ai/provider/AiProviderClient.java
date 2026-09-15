package com.joblivo.ai.provider;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.routing.ResolvedModelRoute;

/**
 * Internal SPI contract for AI provider adapter implementations (e.g. Bedrock, OpenAI, Google).
 * Future prompts implement concrete adapters conforming to this interface.
 */
public interface AiProviderClient {

    /**
     * Identifies the AI provider supported by this client adapter.
     */
    AiProvider getProvider();

    /**
     * Executes generation/inference for the given request and resolved route.
     *
     * @param request the validated AI request
     * @param route the resolved route specifying provider and model
     * @return normalized AI response
     * @throws com.joblivo.ai.exception.AiProviderException if provider execution fails
     */
    AiResponse generate(AiRequest request, ResolvedModelRoute route);
}

package com.joblivo.ai;

import com.joblivo.ai.exception.AiProviderException;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.provider.AiProviderClient;
import com.joblivo.ai.provider.AiProviderRegistry;
import com.joblivo.ai.routing.ModelRouter;
import com.joblivo.ai.routing.ResolvedModelRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Production implementation of {@link AiGateway}.
 * Coordinates routing policy resolution and provider dispatch without coupling to specific SDKs.
 * Adheres strictly to fail-closed behavior: fails safely if no provider client is registered.
 */
@Service
public class DefaultAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiGateway.class);

    private final ModelRouter modelRouter;
    private final AiProviderRegistry providerRegistry;

    public DefaultAiGateway(ModelRouter modelRouter, AiProviderRegistry providerRegistry) {
        this.modelRouter = Objects.requireNonNull(modelRouter, "modelRouter must not be null");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
    }

    @Override
    public AiResponse generate(AiRequest request) {
        Objects.requireNonNull(request, "AiRequest must not be null");

        log.debug("Initiating AI generation request [correlationId={}, purpose={}]",
                request.correlationId(), request.purpose());

        ResolvedModelRoute route = modelRouter.route(request);

        log.info("AI route resolved [correlationId={}, provider={}, model={}]",
                request.correlationId(), route.provider(), route.model());

        AiProviderClient client = providerRegistry.getClient(route.provider())
                .orElseThrow(() -> new AiProviderException(
                        "No provider implementation registered for AI provider: " + route.provider()
                                + ". Ensure provider adapter is configured and present in classpath."
                ));

        long startTime = System.currentTimeMillis();
        AiResponse response = client.generate(request, route);
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("AI generation completed [correlationId={}, provider={}, model={}, elapsedMs={}]",
                request.correlationId(), route.provider(), route.model(), elapsed);

        return response;
    }
}

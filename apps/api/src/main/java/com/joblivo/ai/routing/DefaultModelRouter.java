package com.joblivo.ai.routing;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.config.AiProviderProperties;
import com.joblivo.ai.config.PurposeRouteProperties;
import com.joblivo.ai.exception.AiConfigurationException;
import com.joblivo.ai.exception.AiRoutingException;
import com.joblivo.ai.model.AiRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Production implementation of {@link ModelRouter}.
 * Evaluates a deterministic 5-step routing policy with fail-closed semantics:
 * <ol>
 *   <li>Explicit provider + explicit model (validated against provider enablement & model allow-list)</li>
 *   <li>Explicit provider + provider default model (validated against provider enablement)</li>
 *   <li>Purpose-specific configured route (mapped provider/model)</li>
 *   <li>Global default provider + default model</li>
 *   <li>Fail-closed if unresolvable</li>
 * </ol>
 * Strictly enforces that explicit invalid requests fail rather than silently falling back to defaults.
 */
@Component
public class DefaultModelRouter implements ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultModelRouter.class);

    private final AiConfigurationProperties properties;

    public DefaultModelRouter(AiConfigurationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "AiConfigurationProperties must not be null");
    }

    @PostConstruct
    public void init() {
        properties.validateConfiguration();
    }

    @Override
    public ResolvedModelRoute route(AiRequest request) {
        Objects.requireNonNull(request, "AiRequest must not be null");

        if (!properties.isEnabled()) {
            throw new AiConfigurationException("AI Gateway is disabled by configuration (joblivo.ai.enabled=false)");
        }

        ResolvedModelRoute route = resolveRoute(request);

        log.info("AI route resolved [correlationId={}, provider={}, model={}, purpose={}, source={}]",
                request.correlationId(), route.provider(), route.model(), route.purpose(), route.source());

        return route;
    }

    private ResolvedModelRoute resolveRoute(AiRequest request) {
        // Precedence 1: Explicit provider + explicit model
        if (request.requestedProvider() != null && request.requestedModel() != null && !request.requestedModel().isBlank()) {
            AiProvider provider = request.requestedProvider();
            String model = request.requestedModel().trim();
            validateProviderEnabled(provider, "Requested");
            validateModelAllowed(provider, model);
            return new ResolvedModelRoute(provider, model, request.purpose(), RoutingSource.EXPLICIT);
        }

        // Precedence 2: Explicit provider with configured/default model for that provider
        if (request.requestedProvider() != null && (request.requestedModel() == null || request.requestedModel().isBlank())) {
            AiProvider provider = request.requestedProvider();
            validateProviderEnabled(provider, "Requested");
            String model = resolveProviderDefaultModel(provider);
            if (model == null || model.isBlank()) {
                throw new AiRoutingException("No default model configured for requested AI provider: " + provider);
            }
            validateModelAllowed(provider, model);
            return new ResolvedModelRoute(provider, model, request.purpose(), RoutingSource.PROVIDER_DEFAULT);
        }

        // Precedence 2b: Explicit model without explicit provider
        if (request.requestedModel() != null && !request.requestedModel().isBlank()) {
            AiProvider defaultProvider = resolveDefaultProvider();
            validateProviderEnabled(defaultProvider, "Default");
            String model = request.requestedModel().trim();
            validateModelAllowed(defaultProvider, model);
            return new ResolvedModelRoute(defaultProvider, model, request.purpose(), RoutingSource.EXPLICIT);
        }

        // Precedence 3: Purpose-specific configured route
        if (request.purpose() != null && !request.purpose().isBlank()) {
            PurposeRouteProperties purposeRoute = properties.getPurposeRoute(request.purpose());
            if (purposeRoute != null) {
                if (purposeRoute.getModel() == null || purposeRoute.getModel().isBlank()) {
                    throw new AiRoutingException("Purpose route for '" + request.purpose() + "' has missing or blank model");
                }
                AiProvider provider = purposeRoute.getProvider() != null ? purposeRoute.getProvider() : resolveDefaultProvider();
                validateProviderEnabled(provider, "Purpose route '" + request.purpose() + "'");
                String model = purposeRoute.getModel().trim();
                validateModelAllowed(provider, model);
                return new ResolvedModelRoute(provider, model, request.purpose(), RoutingSource.PURPOSE);
            }
        }

        // Precedence 4: Global/default configured provider + model
        AiProvider defaultProvider = resolveDefaultProvider();
        validateProviderEnabled(defaultProvider, "Default");
        String defaultModel = resolveGlobalOrProviderDefaultModel(defaultProvider);
        if (defaultModel == null || defaultModel.isBlank()) {
            throw new AiRoutingException(
                    "No model could be resolved for default AI provider: " + defaultProvider
                            + (request.purpose() != null ? " and purpose: " + request.purpose() : "")
            );
        }
        validateModelAllowed(defaultProvider, defaultModel);
        return new ResolvedModelRoute(defaultProvider, defaultModel, request.purpose(), RoutingSource.GLOBAL_DEFAULT);
    }

    private AiProvider resolveDefaultProvider() {
        AiProvider provider = properties.getDefaultProvider();
        if (provider == null) {
            throw new AiConfigurationException("No default AI provider configured (joblivo.ai.default-provider)");
        }
        return provider;
    }

    private void validateProviderEnabled(AiProvider provider, String contextPrefix) {
        if (provider == null) {
            throw new AiConfigurationException("AI provider must not be null");
        }
        if (!properties.isProviderEnabled(provider)) {
            throw new AiRoutingException(contextPrefix + " AI provider '" + provider + "' is disabled or not configured");
        }
    }

    private void validateModelAllowed(AiProvider provider, String model) {
        if (model == null || model.isBlank()) {
            throw new AiRoutingException("Model identifier must not be null or blank for provider: " + provider);
        }
        AiProviderProperties providerProps = properties.getProviders() != null ? properties.getProviders().get(provider) : null;
        if (providerProps == null || !providerProps.isModelAllowed(model)) {
            throw new AiRoutingException(
                    "Model '" + model + "' is not configured or allowed for provider '" + provider + "'"
            );
        }
    }

    private String resolveProviderDefaultModel(AiProvider provider) {
        if (properties.getProviders() != null) {
            AiProviderProperties props = properties.getProviders().get(provider);
            if (props != null && props.getDefaultModel() != null && !props.getDefaultModel().isBlank()) {
                return props.getDefaultModel().trim();
            }
        }
        return null;
    }

    private String resolveGlobalOrProviderDefaultModel(AiProvider provider) {
        String model = resolveProviderDefaultModel(provider);
        if (model != null && !model.isBlank()) {
            return model;
        }
        if (properties.getDefaultModel() != null && !properties.getDefaultModel().isBlank()) {
            return properties.getDefaultModel().trim();
        }
        return null;
    }
}

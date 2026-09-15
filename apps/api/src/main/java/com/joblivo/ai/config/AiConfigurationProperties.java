package com.joblivo.ai.config;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.exception.AiConfigurationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe configuration properties for the Joblivo AI Gateway.
 * Namespaced under {@code joblivo.ai}.
 */
@ConfigurationProperties(prefix = "joblivo.ai")
public class AiConfigurationProperties {

    /**
     * Master toggle for AI capabilities across the platform. Default is false (fail-closed).
     */
    private boolean enabled = false;

    /**
     * Default AI provider when an incoming request does not explicitly specify one.
     */
    private AiProvider defaultProvider = AiProvider.BEDROCK;

    /**
     * Default model identifier across all providers if not overridden at provider or purpose level.
     */
    private String defaultModel;

    /**
     * Provider-specific settings keyed by AiProvider.
     */
    private Map<AiProvider, AiProviderProperties> providers = new EnumMap<>(AiProvider.class);

    /**
     * Bedrock-specific configuration (region, timeout).
     */
    private BedrockProperties bedrock = new BedrockProperties();

    /**
     * Structured purpose/use-case routing mappings.
     */
    private Map<String, PurposeRouteProperties> purposeRoutes = new HashMap<>();

    /**
     * Model overrides keyed by purpose/use-case identifier (legacy/shorthand format).
     */
    private Map<String, String> modelsByPurpose = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AiProvider getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(AiProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Map<AiProvider, AiProviderProperties> getProviders() {
        return providers;
    }

    public void setProviders(Map<AiProvider, AiProviderProperties> providers) {
        this.providers = providers;
    }

    public BedrockProperties getBedrock() {
        return bedrock;
    }

    public void setBedrock(BedrockProperties bedrock) {
        this.bedrock = bedrock;
    }

    public Map<String, PurposeRouteProperties> getPurposeRoutes() {
        return purposeRoutes;
    }

    public void setPurposeRoutes(Map<String, PurposeRouteProperties> purposeRoutes) {
        this.purposeRoutes = purposeRoutes != null ? purposeRoutes : new HashMap<>();
    }

    public Map<String, String> getModelsByPurpose() {
        return modelsByPurpose;
    }

    public void setModelsByPurpose(Map<String, String> modelsByPurpose) {
        this.modelsByPurpose = modelsByPurpose != null ? modelsByPurpose : new HashMap<>();
    }

    public boolean isProviderEnabled(AiProvider provider) {
        if (provider == null || providers == null) {
            return false;
        }
        AiProviderProperties props = providers.get(provider);
        return props != null && props.isEnabled();
    }

    /**
     * Resolves the configured route for a given purpose.
     * Checks structured {@code purposeRoutes} first, then falls back to legacy {@code modelsByPurpose}.
     *
     * @param purpose the purpose identifier
     * @return the PurposeRouteProperties if configured, null otherwise
     */
    public PurposeRouteProperties getPurposeRoute(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return null;
        }
        String key = purpose.trim();
        if (purposeRoutes != null && purposeRoutes.containsKey(key)) {
            return purposeRoutes.get(key);
        }
        if (modelsByPurpose != null && modelsByPurpose.containsKey(key)) {
            String model = modelsByPurpose.get(key);
            if (model != null && !model.isBlank()) {
                return new PurposeRouteProperties(null, model.trim());
            }
        }
        return null;
    }

    /**
     * Validates that routing configuration is deterministic and complete when AI is enabled.
     *
     * @throws AiConfigurationException if configuration is invalid or inconsistent
     */
    public void validateConfiguration() {
        if (!enabled) {
            return;
        }

        if (defaultProvider == null) {
            throw new AiConfigurationException("Missing default AI provider (joblivo.ai.default-provider)");
        }

        if (!isProviderEnabled(defaultProvider)) {
            throw new AiConfigurationException("Default AI provider '" + defaultProvider + "' is disabled or not configured");
        }

        String resolvedDefaultModel = resolveDefaultModelForProvider(defaultProvider);
        if (resolvedDefaultModel == null || resolvedDefaultModel.isBlank()) {
            throw new AiConfigurationException("No default model configured for default provider: " + defaultProvider);
        }

        AiProviderProperties defaultProps = providers.get(defaultProvider);
        if (defaultProps != null && !defaultProps.isModelAllowed(resolvedDefaultModel)) {
            throw new AiConfigurationException(
                    "Default model '" + resolvedDefaultModel + "' is not permitted by provider configuration for '" + defaultProvider + "'"
            );
        }

        // Validate structured purpose routes
        if (purposeRoutes != null) {
            for (Map.Entry<String, PurposeRouteProperties> entry : purposeRoutes.entrySet()) {
                String purpose = entry.getKey();
                PurposeRouteProperties route = entry.getValue();
                if (purpose == null || purpose.isBlank()) {
                    throw new AiConfigurationException("Purpose route key must not be blank");
                }
                if (route == null || route.getModel() == null || route.getModel().isBlank()) {
                    throw new AiConfigurationException("Purpose route '" + purpose + "' has missing or blank model");
                }

                AiProvider targetProvider = route.getProvider() != null ? route.getProvider() : defaultProvider;
                if (!isProviderEnabled(targetProvider)) {
                    throw new AiConfigurationException(
                            "Purpose route '" + purpose + "' targets disabled or unconfigured provider: " + targetProvider
                    );
                }

                AiProviderProperties providerProps = providers.get(targetProvider);
                if (providerProps != null && !providerProps.isModelAllowed(route.getModel())) {
                    throw new AiConfigurationException(
                            "Purpose route '" + purpose + "' specifies model '" + route.getModel()
                                    + "' which is not permitted for provider '" + targetProvider + "'"
                    );
                }
            }
        }

        // Validate legacy modelsByPurpose
        if (modelsByPurpose != null) {
            for (Map.Entry<String, String> entry : modelsByPurpose.entrySet()) {
                String purpose = entry.getKey();
                String model = entry.getValue();
                if (purpose != null && !purpose.isBlank() && model != null && !model.isBlank()) {
                    AiProviderProperties defaultPropsForLegacy = providers.get(defaultProvider);
                    if (defaultPropsForLegacy != null && !defaultPropsForLegacy.isModelAllowed(model)) {
                        throw new AiConfigurationException(
                                "Purpose mapping '" + purpose + "' specifies model '" + model
                                        + "' which is not permitted for default provider '" + defaultProvider + "'"
                        );
                    }
                }
            }
        }
    }

    private String resolveDefaultModelForProvider(AiProvider provider) {
        if (providers != null) {
            AiProviderProperties props = providers.get(provider);
            if (props != null && props.getDefaultModel() != null && !props.getDefaultModel().isBlank()) {
                return props.getDefaultModel().trim();
            }
        }
        if (defaultModel != null && !defaultModel.isBlank()) {
            return defaultModel.trim();
        }
        return null;
    }
}

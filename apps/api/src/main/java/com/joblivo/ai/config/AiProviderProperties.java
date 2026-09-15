package com.joblivo.ai.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Provider-specific configuration properties including enablement, default model, and allowed models.
 */
public class AiProviderProperties {

    private boolean enabled = false;
    private String defaultModel;
    private Set<String> allowedModels = new LinkedHashSet<>();

    public AiProviderProperties() {
    }

    public AiProviderProperties(boolean enabled, String defaultModel) {
        this.enabled = enabled;
        this.defaultModel = defaultModel;
        if (defaultModel != null && !defaultModel.isBlank()) {
            this.allowedModels.add(defaultModel.trim());
        }
    }

    public AiProviderProperties(boolean enabled, String defaultModel, Set<String> allowedModels) {
        this.enabled = enabled;
        this.defaultModel = defaultModel;
        if (allowedModels != null) {
            this.allowedModels = new LinkedHashSet<>(allowedModels);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Set<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(Set<String> allowedModels) {
        this.allowedModels = allowedModels != null ? new LinkedHashSet<>(allowedModels) : new LinkedHashSet<>();
    }

    /**
     * Checks whether a model identifier is configured and permitted for this provider.
     *
     * @param model the model identifier
     * @return true if permitted, false otherwise
     */
    public boolean isModelAllowed(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String trimmed = model.trim();
        if (allowedModels != null && !allowedModels.isEmpty()) {
            return allowedModels.contains(trimmed);
        }
        return defaultModel != null && defaultModel.trim().equals(trimmed);
    }
}

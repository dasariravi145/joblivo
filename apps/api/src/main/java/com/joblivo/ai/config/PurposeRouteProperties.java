package com.joblivo.ai.config;

import com.joblivo.ai.AiProvider;

/**
 * Configuration properties defining a purpose-specific AI route.
 */
public class PurposeRouteProperties {

    /**
     * Optional provider override for this purpose. If null, default provider is used.
     */
    private AiProvider provider;

    /**
     * Model identifier to use for this purpose.
     */
    private String model;

    public PurposeRouteProperties() {
    }

    public PurposeRouteProperties(AiProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}

package com.joblivo.ai.config;

/**
 * Bedrock-specific provider configuration properties.
 * Configured under {@code joblivo.ai.bedrock}.
 */
public class BedrockProperties {

    /**
     * AWS region where Bedrock model invocations are routed (e.g. "us-east-1").
     * If blank or null, standard AWS SDK region resolution will be attempted.
     */
    private String region;

    /**
     * Request timeout in seconds for Bedrock runtime operations. Default is 30 seconds.
     */
    private int timeoutSeconds = 30;

    public BedrockProperties() {
    }

    public BedrockProperties(String region, int timeoutSeconds) {
        this.region = region;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}

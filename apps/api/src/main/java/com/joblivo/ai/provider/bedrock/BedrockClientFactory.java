package com.joblivo.ai.provider.bedrock;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Factory contract responsible for creating and providing configured {@link BedrockRuntimeClient} instances.
 * Enables clean lifecycle management and client mocking without invoking real AWS SDK builders in unit tests.
 */
public interface BedrockClientFactory {

    /**
     * Obtains the configured Bedrock runtime client.
     *
     * @return BedrockRuntimeClient instance
     * @throws com.joblivo.ai.exception.AiConfigurationException if required configuration (e.g. region) is missing or invalid
     */
    BedrockRuntimeClient getClient();
}

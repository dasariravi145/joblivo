package com.joblivo.ai.provider.bedrock;

import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.exception.AiConfigurationException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;
import java.util.Objects;

/**
 * Production implementation of {@link BedrockClientFactory}.
 * Resolves AWS region, credentials, and timeouts from environment and configuration.
 * Creates {@link BedrockRuntimeClient} lazily to prevent startup failure when AWS credentials are not configured.
 */
@Component
public class DefaultBedrockClientFactory implements BedrockClientFactory, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DefaultBedrockClientFactory.class);

    private final AiConfigurationProperties properties;
    private final Object lock = new Object();
    private volatile BedrockRuntimeClient client;

    public DefaultBedrockClientFactory(AiConfigurationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "AiConfigurationProperties must not be null");
    }

    @Override
    public BedrockRuntimeClient getClient() {
        BedrockRuntimeClient existing = client;
        if (existing != null) {
            return existing;
        }

        synchronized (lock) {
            if (client == null) {
                client = createClient();
            }
            return client;
        }
    }

    protected BedrockRuntimeClient createClient() {
        Region region = resolveRegion();
        int timeoutSeconds = resolveTimeoutSeconds();

        log.info("Initializing AWS Bedrock Runtime Client [region={}, timeoutSeconds={}]",
                region.id(), timeoutSeconds);

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(timeoutSeconds))
                .apiCallAttemptTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        return BedrockRuntimeClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(overrideConfig)
                .build();
    }

    public Region resolveRegion() {
        String configuredRegion = properties.getBedrock() != null ? properties.getBedrock().getRegion() : null;
        if (configuredRegion != null && !configuredRegion.isBlank()) {
            try {
                return Region.of(configuredRegion.trim());
            } catch (Exception e) {
                throw new AiConfigurationException("Configured AWS Bedrock region is invalid: '" + configuredRegion + "'", e);
            }
        }

        try {
            Region resolved = DefaultAwsRegionProviderChain.builder().build().getRegion();
            if (resolved != null) {
                return resolved;
            }
        } catch (Exception e) {
            log.debug("Standard AWS region provider chain did not resolve a region: {}", e.getMessage());
        }

        throw new AiConfigurationException(
                "AWS Bedrock region is not configured (joblivo.ai.bedrock.region) and could not be resolved from the standard AWS environment/profile chain"
        );
    }

    public int resolveTimeoutSeconds() {
        int timeout = properties.getBedrock() != null ? properties.getBedrock().getTimeoutSeconds() : 30;
        if (timeout <= 0) {
            throw new AiConfigurationException(
                    "Invalid AWS Bedrock timeout: " + timeout + " seconds. Timeout must be a positive integer."
            );
        }
        return timeout;
    }

    @Override
    @PreDestroy
    public void close() {
        synchronized (lock) {
            if (client != null) {
                try {
                    log.info("Closing AWS Bedrock Runtime Client connection pool");
                    client.close();
                } catch (Exception e) {
                    log.warn("Error closing Bedrock Runtime Client: {}", e.getMessage());
                } finally {
                    client = null;
                }
            }
        }
    }
}

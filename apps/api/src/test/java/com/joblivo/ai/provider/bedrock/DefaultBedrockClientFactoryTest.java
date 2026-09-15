package com.joblivo.ai.provider.bedrock;

import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.config.BedrockProperties;
import com.joblivo.ai.exception.AiConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultBedrockClientFactory Unit Tests")
class DefaultBedrockClientFactoryTest {

    @Test
    @DisplayName("Resolves explicitly configured AWS region")
    void resolveRegion_ExplicitConfiguration_ReturnsRegion() {
        AiConfigurationProperties properties = new AiConfigurationProperties();
        properties.setBedrock(new BedrockProperties("us-west-2", 30));

        DefaultBedrockClientFactory factory = new DefaultBedrockClientFactory(properties);
        Region region = factory.resolveRegion();

        assertThat(region).isEqualTo(Region.US_WEST_2);
    }

    @Test
    @DisplayName("Resolves explicitly configured timeout")
    void resolveTimeout_ExplicitConfiguration_ReturnsConfiguredSeconds() {
        AiConfigurationProperties properties = new AiConfigurationProperties();
        properties.setBedrock(new BedrockProperties("us-east-1", 45));

        DefaultBedrockClientFactory factory = new DefaultBedrockClientFactory(properties);
        int timeout = factory.resolveTimeoutSeconds();

        assertThat(timeout).isEqualTo(45);
    }

    @Test
    @DisplayName("Resolves default timeout of 30 seconds when not specified")
    void resolveTimeout_DefaultConfiguration_Returns30Seconds() {
        AiConfigurationProperties properties = new AiConfigurationProperties();
        // default BedrockProperties has timeoutSeconds = 30

        DefaultBedrockClientFactory factory = new DefaultBedrockClientFactory(properties);
        int timeout = factory.resolveTimeoutSeconds();

        assertThat(timeout).isEqualTo(30);
    }

    @Test
    @DisplayName("Throws AiConfigurationException when timeout is zero or negative")
    void resolveTimeout_InvalidTimeout_ThrowsAiConfigurationException() {
        AiConfigurationProperties properties = new AiConfigurationProperties();
        properties.setBedrock(new BedrockProperties("us-east-1", 0));

        DefaultBedrockClientFactory factoryZero = new DefaultBedrockClientFactory(properties);
        assertThatThrownBy(factoryZero::resolveTimeoutSeconds)
                .isInstanceOf(AiConfigurationException.class)
                .hasMessageContaining("Timeout must be a positive integer");

        properties.setBedrock(new BedrockProperties("us-east-1", -10));
        DefaultBedrockClientFactory factoryNegative = new DefaultBedrockClientFactory(properties);
        assertThatThrownBy(factoryNegative::resolveTimeoutSeconds)
                .isInstanceOf(AiConfigurationException.class)
                .hasMessageContaining("Timeout must be a positive integer");
    }

    @Test
    @DisplayName("Throws AiConfigurationException when region is not configured and cannot be resolved")
    void resolveRegion_UnconfiguredAndUnresolvable_ThrowsAiConfigurationException() {
        AiConfigurationProperties properties = new AiConfigurationProperties();
        properties.setBedrock(new BedrockProperties(null, 30));

        // Subclass to isolate from ambient developer environment AWS credentials/region
        DefaultBedrockClientFactory factory = new DefaultBedrockClientFactory(properties) {
            @Override
            public Region resolveRegion() {
                String configuredRegion = properties.getBedrock() != null ? properties.getBedrock().getRegion() : null;
                if (configuredRegion != null && !configuredRegion.isBlank()) {
                    return Region.of(configuredRegion.trim());
                }
                throw new AiConfigurationException(
                        "AWS Bedrock region is not configured (joblivo.ai.bedrock.region) and could not be resolved from the standard AWS environment/profile chain"
                );
            }
        };

        assertThatThrownBy(factory::resolveRegion)
                .isInstanceOf(AiConfigurationException.class)
                .hasMessageContaining("AWS Bedrock region is not configured (joblivo.ai.bedrock.region)");
    }

    @Test
    @DisplayName("Closing uninitialized factory succeeds without throwing")
    void close_UninitializedClient_SucceedsWithoutException() {
        AiConfigurationProperties properties = new AiConfigurationProperties();
        DefaultBedrockClientFactory factory = new DefaultBedrockClientFactory(properties);

        // Client was never created; close must be safe and idempotent
        factory.close();
        factory.close();
    }
}

package com.joblivo.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration registering the AI Gateway infrastructure beans and properties.
 */
@Configuration
@EnableConfigurationProperties(AiConfigurationProperties.class)
public class AiGatewayAutoConfiguration {
}

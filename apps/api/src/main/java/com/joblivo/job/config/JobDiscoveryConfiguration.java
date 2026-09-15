package com.joblivo.job.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Spring configuration enabling {@link JobDiscoveryProperties} and {@link JobFreshnessProperties},
 * and registering a UTC {@link Clock} bean for deterministic time evaluation.
 */
@Configuration
@EnableConfigurationProperties({JobDiscoveryProperties.class, JobFreshnessProperties.class})
public class JobDiscoveryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

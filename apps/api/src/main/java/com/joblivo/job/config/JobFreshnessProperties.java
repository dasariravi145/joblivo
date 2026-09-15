package com.joblivo.job.config;

import com.joblivo.job.exception.JobConfigurationException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Type-safe configuration properties for Job Discovery lifecycle and freshness evaluation.
 * Primary configuration namespace: {@code joblivo.job-discovery.freshness}.
 * Also supports fallback configuration: {@code joblivo.jobs.freshness.stale-after}.
 */
@ConfigurationProperties(prefix = "joblivo.job-discovery.freshness")
public class JobFreshnessProperties {

    /**
     * Sensible production default stale threshold: 7 days.
     */
    public static final Duration DEFAULT_STALE_AFTER = Duration.ofDays(7);

    /**
     * Stale threshold duration. A non-expired job with lastSeenAt older than this duration
     * relative to current time (UTC) is classified as STALE. Default: 7 days (7d).
     */
    private Duration staleAfter = DEFAULT_STALE_AFTER;

    @Value("${joblivo.jobs.freshness.stale-after:#{null}}")
    private Duration legacyStaleAfter;

    public JobFreshnessProperties() {
    }

    public JobFreshnessProperties(Duration staleAfter) {
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter must not be null");
        validate();
    }

    @PostConstruct
    public void validate() {
        if (legacyStaleAfter != null) {
            this.staleAfter = legacyStaleAfter;
        }
        if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
            throw new JobConfigurationException("staleAfter duration must be positive, received: " + staleAfter);
        }
    }

    public Duration getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = staleAfter;
    }
}

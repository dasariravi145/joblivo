package com.joblivo.job.ingestion;

import com.joblivo.job.config.JobDiscoveryProperties;
import com.joblivo.job.exception.DuplicateJobSourceAdapterException;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobSourceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Internal registry for discovering, registering, and resolving {@link JobSourceAdapter} implementations.
 * Enforces uniqueness of source adapters at initialization, rejecting duplicate registrations for the same {@link JobSource}.
 * Exposes registered and enabled sources deterministically based on configuration.
 */
@Component
public class JobSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(JobSourceRegistry.class);

    private final Map<JobSource, JobSourceAdapter> adapters = new EnumMap<>(JobSource.class);
    private final JobDiscoveryProperties properties;

    @Autowired
    public JobSourceRegistry(
            @Autowired(required = false) List<JobSourceAdapter> sourceAdapters,
            JobDiscoveryProperties properties
    ) {
        this.properties = Objects.requireNonNull(properties, "JobDiscoveryProperties must not be null");

        if (sourceAdapters != null) {
            for (JobSourceAdapter adapter : sourceAdapters) {
                register(adapter);
            }
        }
        log.info("Initialized JobSourceRegistry with {} registered adapters", adapters.size());
    }

    private void register(JobSourceAdapter adapter) {
        Objects.requireNonNull(adapter, "JobSourceAdapter must not be null");
        JobSource source = Objects.requireNonNull(adapter.source(), "JobSourceAdapter source must not be null");

        if (adapters.containsKey(source)) {
            throw new DuplicateJobSourceAdapterException(source, adapter.getClass(), adapters.get(source).getClass());
        }

        adapters.put(source, adapter);
        log.info("Registered JobSourceAdapter for source '{}' [{}]", source, adapter.getClass().getSimpleName());
    }

    /**
     * Looks up an adapter by its source identifier.
     *
     * @param source the job source to lookup
     * @return Optional containing the adapter if registered, or empty if unknown/unregistered
     */
    public Optional<JobSourceAdapter> getAdapter(JobSource source) {
        if (source == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(source));
    }

    /**
     * Checks if an adapter is registered for the given source.
     *
     * @param source the job source
     * @return true if an adapter exists in the registry
     */
    public boolean isSourceRegistered(JobSource source) {
        return source != null && adapters.containsKey(source);
    }

    /**
     * Checks if a source has a registered adapter AND is enabled in configuration.
     *
     * @param source the job source
     * @return true if registered and enabled
     */
    public boolean isSourceEnabled(JobSource source) {
        return isSourceRegistered(source) && properties.isSourceEnabled(source);
    }

    /**
     * Returns an unmodifiable set of all registered sources in deterministic alphabetical order.
     */
    public Set<JobSource> getRegisteredSources() {
        return Collections.unmodifiableSet(new TreeSet<>(adapters.keySet()));
    }

    /**
     * Returns an unmodifiable set of registered sources that are currently enabled in configuration,
     * ordered deterministically.
     */
    public Set<JobSource> getEnabledSources() {
        Set<JobSource> enabled = new TreeSet<>();
        for (JobSource source : adapters.keySet()) {
            if (properties.isSourceEnabled(source)) {
                enabled.add(source);
            }
        }
        return Collections.unmodifiableSet(enabled);
    }

    /**
     * Returns the underlying JobDiscoveryProperties.
     */
    public JobDiscoveryProperties getProperties() {
        return properties;
    }

    /**
     * Returns canonical descriptive source metadata for a specific {@link JobSource}.
     * Evaluates current runtime enablement via configuration properties.
     *
     * @param source the job source
     * @return JobSourceMetadata, or null if source is null
     */
    public JobSourceMetadata getSourceMetadata(JobSource source) {
        if (source == null) {
            return null;
        }
        return JobSourceMetadata.of(source, isSourceEnabled(source));
    }

    /**
     * Returns canonical descriptive source metadata for all supported conceptual {@link JobSource} origins
     * in deterministic order.
     *
     * @return unmodifiable list of JobSourceMetadata
     */
    public List<JobSourceMetadata> getAllSourceMetadata() {
        List<JobSourceMetadata> result = new ArrayList<>(JobSource.values().length);
        for (JobSource source : JobSource.values()) {
            result.add(getSourceMetadata(source));
        }
        return Collections.unmodifiableList(result);
    }
}

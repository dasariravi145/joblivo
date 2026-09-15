package com.joblivo.ai.provider;

import com.joblivo.ai.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry holding all available AI provider client adapters.
 * Enables clean, decoupled registration of provider implementations without modifying business services.
 */
@Component
public class AiProviderRegistry {

    private final Map<AiProvider, AiProviderClient> clients;

    public AiProviderRegistry(@Autowired(required = false) List<AiProviderClient> providerClients) {
        Map<AiProvider, AiProviderClient> map = new EnumMap<>(AiProvider.class);
        if (providerClients != null) {
            for (AiProviderClient client : providerClients) {
                if (client != null && client.getProvider() != null) {
                    map.put(client.getProvider(), client);
                }
            }
        }
        this.clients = Collections.unmodifiableMap(map);
    }

    /**
     * Retrieves the registered client adapter for the given provider.
     *
     * @param provider the AI provider
     * @return Optional containing the client adapter if registered, empty otherwise
     */
    public Optional<AiProviderClient> getClient(AiProvider provider) {
        if (provider == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(clients.get(provider));
    }

    /**
     * Checks if a client adapter is registered for the given provider.
     *
     * @param provider the AI provider
     * @return true if registered, false otherwise
     */
    public boolean hasClient(AiProvider provider) {
        return provider != null && clients.containsKey(provider);
    }

    /**
     * Returns the total count of registered provider adapters.
     */
    public int getRegisteredCount() {
        return clients.size();
    }
}

package com.joblivo.ai.provider;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.routing.ResolvedModelRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiProviderRegistry Unit Tests")
class AiProviderRegistryTest {

    @Test
    @DisplayName("Empty registry returns empty Optional and hasClient=false")
    void emptyRegistry_ReturnsEmpty() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of());

        assertThat(registry.getRegisteredCount()).isEqualTo(0);
        assertThat(registry.hasClient(AiProvider.BEDROCK)).isFalse();
        assertThat(registry.getClient(AiProvider.BEDROCK)).isEmpty();
    }

    @Test
    @DisplayName("Null provider client list handled safely")
    void nullClientsList_HandledSafely() {
        AiProviderRegistry registry = new AiProviderRegistry(null);

        assertThat(registry.getRegisteredCount()).isEqualTo(0);
        assertThat(registry.getClient(AiProvider.OPENAI)).isEmpty();
    }

    @Test
    @DisplayName("Registered client adapter is discoverable by AiProvider")
    void registeredClient_IsDiscoverable() {
        AiProviderClient mockBedrockClient = new AiProviderClient() {
            @Override
            public AiProvider getProvider() {
                return AiProvider.BEDROCK;
            }

            @Override
            public AiResponse generate(AiRequest request, ResolvedModelRoute route) {
                return AiResponse.builder()
                        .generatedContent("Sample")
                        .provider(AiProvider.BEDROCK)
                        .model(route.model())
                        .correlationId(request.correlationId())
                        .build();
            }
        };

        AiProviderRegistry registry = new AiProviderRegistry(List.of(mockBedrockClient));

        assertThat(registry.getRegisteredCount()).isEqualTo(1);
        assertThat(registry.hasClient(AiProvider.BEDROCK)).isTrue();
        assertThat(registry.hasClient(AiProvider.OPENAI)).isFalse();

        Optional<AiProviderClient> client = registry.getClient(AiProvider.BEDROCK);
        assertThat(client).isPresent();
        assertThat(client.get().getProvider()).isEqualTo(AiProvider.BEDROCK);
    }

    @Test
    @DisplayName("Lookup with null provider returns empty Optional")
    void nullProviderLookup_ReturnsEmpty() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of());
        assertThat(registry.getClient(null)).isEmpty();
        assertThat(registry.hasClient(null)).isFalse();
    }
}

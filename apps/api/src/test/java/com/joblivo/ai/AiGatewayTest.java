package com.joblivo.ai;

import com.joblivo.ai.exception.AiProviderException;
import com.joblivo.ai.exception.AiRoutingException;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.model.AiTokenUsage;
import com.joblivo.ai.provider.AiProviderClient;
import com.joblivo.ai.provider.AiProviderRegistry;
import com.joblivo.ai.routing.ModelRouter;
import com.joblivo.ai.routing.ResolvedModelRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiGateway Unit Tests")
class AiGatewayTest {

    @Mock
    private ModelRouter modelRouter;

    @Mock
    private AiProviderClient mockProviderClient;

    private DefaultAiGateway gateway;

    @BeforeEach
    void setUp() {
        // By default, no providers registered (pure foundation state)
        AiProviderRegistry emptyRegistry = new AiProviderRegistry(List.of());
        gateway = new DefaultAiGateway(modelRouter, emptyRegistry);
    }

    @Test
    @DisplayName("Fail-closed: Throws AiProviderException when route is resolved but no provider client is registered")
    void noRegisteredClient_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder()
                .prompt("Extract skills from experience")
                .correlationId("trace-fail-closed")
                .build();

        when(modelRouter.route(request))
                .thenReturn(new ResolvedModelRoute(AiProvider.BEDROCK, "anthropic.claude-3-5-sonnet"));

        assertThatThrownBy(() -> gateway.generate(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("No provider implementation registered for AI provider: BEDROCK");
    }

    @Test
    @DisplayName("Propagates routing exception if ModelRouter fails to resolve route")
    void routingFailure_PropagatesAiRoutingException() {
        AiRequest request = AiRequest.builder().prompt("test").build();

        when(modelRouter.route(request))
                .thenThrow(new AiRoutingException("Default AI provider 'BEDROCK' is disabled"));

        assertThatThrownBy(() -> gateway.generate(request))
                .isInstanceOf(AiRoutingException.class)
                .hasMessageContaining("Default AI provider 'BEDROCK' is disabled");
    }

    @Test
    @DisplayName("Throws NullPointerException if request is null")
    void nullRequest_ThrowsException() {
        assertThatThrownBy(() -> gateway.generate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("AiRequest must not be null");
    }

    @Test
    @DisplayName("Successfully delegates to registered provider client when adapter is present")
    void registeredClient_DelegatesAndReturnsNormalizedResponse() {
        when(mockProviderClient.getProvider()).thenReturn(AiProvider.OPENAI);

        AiResponse expectedResponse = AiResponse.builder()
                .generatedContent("Extracted competencies: Java, Spring Boot")
                .provider(AiProvider.OPENAI)
                .model("gpt-4o")
                .correlationId("trace-success-123")
                .tokenUsage(AiTokenUsage.of(20, 10, 30))
                .finishReason("STOP")
                .durationMs(315)
                .build();

        when(mockProviderClient.generate(any(AiRequest.class), any(ResolvedModelRoute.class)))
                .thenReturn(expectedResponse);

        AiProviderRegistry registryWithClient = new AiProviderRegistry(List.of(mockProviderClient));
        DefaultAiGateway gatewayWithClient = new DefaultAiGateway(modelRouter, registryWithClient);

        AiRequest request = AiRequest.builder()
                .prompt("Extract competencies")
                .correlationId("trace-success-123")
                .requestedProvider(AiProvider.OPENAI)
                .build();

        when(modelRouter.route(request))
                .thenReturn(new ResolvedModelRoute(AiProvider.OPENAI, "gpt-4o"));

        AiResponse actualResponse = gatewayWithClient.generate(request);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.generatedContent()).isEqualTo("Extracted competencies: Java, Spring Boot");
        assertThat(actualResponse.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(actualResponse.model()).isEqualTo("gpt-4o");
        assertThat(actualResponse.correlationId()).isEqualTo("trace-success-123");
        assertThat(actualResponse.tokenUsage()).isEqualTo(AiTokenUsage.of(20, 10, 30));
        assertThat(actualResponse.finishReason()).isEqualTo("STOP");
        assertThat(actualResponse.durationMs()).isEqualTo(315);

        verify(modelRouter).route(request);
        verify(mockProviderClient).generate(request, new ResolvedModelRoute(AiProvider.OPENAI, "gpt-4o"));
    }
}

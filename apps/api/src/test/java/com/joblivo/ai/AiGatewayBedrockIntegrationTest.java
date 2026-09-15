package com.joblivo.ai;

import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.config.AiProviderProperties;
import com.joblivo.ai.exception.AiRoutingException;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.provider.AiProviderRegistry;
import com.joblivo.ai.provider.bedrock.BedrockAiProviderClient;
import com.joblivo.ai.routing.DefaultModelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiGateway + Bedrock Provider Integration Tests")
class AiGatewayBedrockIntegrationTest {

    @Mock
    private BedrockRuntimeClient mockBedrockClient;

    private AiConfigurationProperties properties;
    private AiGateway gateway;

    private static final String DEFAULT_BEDROCK_MODEL = "anthropic.claude-3-5-sonnet-20241022-v2:0";

    @BeforeEach
    void setUp() {
        properties = new AiConfigurationProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider(AiProvider.BEDROCK);
        properties.setDefaultModel(DEFAULT_BEDROCK_MODEL);

        AiProviderProperties bedrockProps = new AiProviderProperties(true, DEFAULT_BEDROCK_MODEL,
                java.util.Set.of(DEFAULT_BEDROCK_MODEL, "amazon.titan-text-express-v1"));
        properties.setProviders(Map.of(AiProvider.BEDROCK, bedrockProps));

        DefaultModelRouter router = new DefaultModelRouter(properties);
        BedrockAiProviderClient bedrockClient = new BedrockAiProviderClient(properties, mockBedrockClient);
        AiProviderRegistry registry = new AiProviderRegistry(List.of(bedrockClient));

        gateway = new DefaultAiGateway(router, registry);
    }

    @Test
    @DisplayName("End-to-End: AiGateway routes default request to Bedrock provider adapter")
    void generate_DefaultRouteToBedrock_Succeeds() {
        AiRequest request = AiRequest.builder()
                .prompt("Summarize skills")
                .correlationId("trace-e2e-001")
                .build();

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("Key skills: Java, Spring Boot, AWS"))
                                .build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder().inputTokens(10).outputTokens(20).totalTokens(30).build())
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

        AiResponse response = gateway.generate(request);

        assertThat(response).isNotNull();
        assertThat(response.provider()).isEqualTo(AiProvider.BEDROCK);
        assertThat(response.model()).isEqualTo(DEFAULT_BEDROCK_MODEL);
        assertThat(response.generatedContent()).isEqualTo("Key skills: Java, Spring Boot, AWS");
        assertThat(response.correlationId()).isEqualTo("trace-e2e-001");
        assertThat(response.finishReason()).isEqualTo("END_TURN");
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(30);

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(mockBedrockClient).converse(captor.capture());
        assertThat(captor.getValue().modelId()).isEqualTo(DEFAULT_BEDROCK_MODEL);
    }

    @Test
    @DisplayName("Routing to a different disabled provider does NOT invoke Bedrock")
    void generate_RequestDifferentProvider_DoesNotCallBedrock() {
        AiRequest request = AiRequest.builder()
                .prompt("Translate text")
                .requestedProvider(AiProvider.OPENAI)
                .build();

        assertThatThrownBy(() -> gateway.generate(request))
                .isInstanceOf(AiRoutingException.class)
                .hasMessageContaining("Requested AI provider 'OPENAI' is disabled or not configured");

        verify(mockBedrockClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    @DisplayName("When Bedrock is disabled, routing fails and Bedrock is NOT invoked")
    void generate_BedrockDisabled_FailsWithoutInvokingBedrock() {
        properties.setProviders(Map.of(AiProvider.BEDROCK, new AiProviderProperties(false, DEFAULT_BEDROCK_MODEL)));

        AiRequest request = AiRequest.builder()
                .prompt("Analyze resume")
                .build();

        assertThatThrownBy(() -> gateway.generate(request))
                .isInstanceOf(AiRoutingException.class)
                .hasMessageContaining("Default AI provider 'BEDROCK' is disabled or not configured");

        verify(mockBedrockClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    @DisplayName("Purpose-based model override routes to specific Bedrock model")
    void generate_PurposeBasedRouting_UsesConfiguredBedrockModel() {
        properties.setModelsByPurpose(Map.of("SUMMARY", "amazon.titan-text-express-v1"));

        AiRequest request = AiRequest.builder()
                .purpose("SUMMARY")
                .prompt("Summarize experience")
                .build();

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("Titan summary result"))
                                .build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

        AiResponse response = gateway.generate(request);

        assertThat(response.model()).isEqualTo("amazon.titan-text-express-v1");
        assertThat(response.generatedContent()).isEqualTo("Titan summary result");

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(mockBedrockClient).converse(captor.capture());
        assertThat(captor.getValue().modelId()).isEqualTo("amazon.titan-text-express-v1");
    }
}

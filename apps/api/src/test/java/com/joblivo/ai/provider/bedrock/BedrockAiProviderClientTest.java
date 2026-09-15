package com.joblivo.ai.provider.bedrock;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.config.AiProviderProperties;
import com.joblivo.ai.exception.AiAuthenticationException;
import com.joblivo.ai.exception.AiConfigurationException;
import com.joblivo.ai.exception.AiModelException;
import com.joblivo.ai.exception.AiProviderException;
import com.joblivo.ai.exception.AiRateLimitException;
import com.joblivo.ai.exception.AiTimeoutException;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.routing.ResolvedModelRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BedrockAiProviderClient Unit Tests")
class BedrockAiProviderClientTest {

    @Mock
    private BedrockRuntimeClient mockBedrockClient;

    private AiConfigurationProperties properties;
    private BedrockAiProviderClient client;

    private static final String MODEL_ID = "anthropic.claude-3-5-sonnet-20241022-v2:0";

    @BeforeEach
    void setUp() {
        properties = new AiConfigurationProperties();
        properties.setEnabled(true);

        AiProviderProperties bedrockProps = new AiProviderProperties(true, MODEL_ID);
        properties.setProviders(Map.of(AiProvider.BEDROCK, bedrockProps));

        client = new BedrockAiProviderClient(properties, mockBedrockClient);
    }

    @Test
    @DisplayName("Provider identifier is AiProvider.BEDROCK")
    void providerIdentifier_IsBedrock() {
        assertThat(client.getProvider()).isEqualTo(AiProvider.BEDROCK);
    }

    @Test
    @DisplayName("Successfully maps full request, invokes Bedrock Converse, and normalizes response")
    void generate_FullRequest_MapsAndNormalizesCorrectly() {
        AiRequest request = AiRequest.builder()
                .prompt("Summarize software engineering background")
                .systemInstruction("You are a technical career assistant.")
                .temperature(0.5)
                .maxOutputTokens(800)
                .correlationId("trace-bedrock-001")
                .userId(UUID.randomUUID())
                .build();

        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("Experienced backend engineer with Java & Spring expertise."))
                                .build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder()
                        .inputTokens(25)
                        .outputTokens(15)
                        .totalTokens(40)
                        .build())
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

        AiResponse response = client.generate(request, route);

        assertThat(response).isNotNull();
        assertThat(response.generatedContent()).isEqualTo("Experienced backend engineer with Java & Spring expertise.");
        assertThat(response.provider()).isEqualTo(AiProvider.BEDROCK);
        assertThat(response.model()).isEqualTo(MODEL_ID);
        assertThat(response.correlationId()).isEqualTo("trace-bedrock-001");
        assertThat(response.finishReason()).isEqualTo("END_TURN");
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().promptTokens()).isEqualTo(25);
        assertThat(response.tokenUsage().completionTokens()).isEqualTo(15);
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(40);

        // Verify request mapping
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(mockBedrockClient).converse(captor.capture());
        ConverseRequest captured = captor.getValue();

        assertThat(captured.modelId()).isEqualTo(MODEL_ID);
        assertThat(captured.system()).hasSize(1);
        assertThat(captured.system().get(0).text()).isEqualTo("You are a technical career assistant.");
        assertThat(captured.messages()).hasSize(1);
        assertThat(captured.messages().get(0).role()).isEqualTo(ConversationRole.USER);
        assertThat(captured.messages().get(0).content().get(0).text()).isEqualTo("Summarize software engineering background");
        assertThat(captured.inferenceConfig()).isNotNull();
        assertThat(captured.inferenceConfig().temperature()).isEqualTo(0.5f);
        assertThat(captured.inferenceConfig().maxTokens()).isEqualTo(800);
    }

    @Test
    @DisplayName("Minimal request with prompt only maps without system or inference config")
    void generate_MinimalRequest_MapsWithoutOptionalFields() {
        AiRequest request = AiRequest.builder()
                .prompt("Hello Bedrock")
                .correlationId("trace-min-002")
                .build();

        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("Hello back"))
                                .build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

        AiResponse response = client.generate(request, route);

        assertThat(response.generatedContent()).isEqualTo("Hello back");

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(mockBedrockClient).converse(captor.capture());
        ConverseRequest captured = captor.getValue();

        assertThat(captured.system()).isEmpty();
        assertThat(captured.inferenceConfig()).isNull();
    }

    @Test
    @DisplayName("Does not fabricate token usage when Bedrock returns null usage")
    void generate_NullUsage_DoesNotFabricateTokenUsage() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-003").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.fromText("Output")).build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .usage((TokenUsage) null)
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

        AiResponse response = client.generate(request, route);
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    @DisplayName("Does not fabricate token usage when Bedrock usage has all null metrics")
    void generate_AllNullMetrics_DoesNotFabricateTokenUsage() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-004").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.fromText("Output")).build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder().build())
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(mockResponse);

        AiResponse response = client.generate(request, route);
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    @DisplayName("Throws AiConfigurationException when AI Gateway is disabled globally")
    void generate_AiDisabled_ThrowsAiConfigurationException() {
        properties.setEnabled(false);

        AiRequest request = AiRequest.builder().prompt("Test").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiConfigurationException.class)
                .hasMessageContaining("AI Gateway is disabled by configuration");

        verify(mockBedrockClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    @DisplayName("Throws AiConfigurationException when Bedrock provider is disabled")
    void generate_BedrockDisabled_ThrowsAiConfigurationException() {
        properties.setProviders(Map.of(AiProvider.BEDROCK, new AiProviderProperties(false, MODEL_ID)));

        AiRequest request = AiRequest.builder().prompt("Test").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiConfigurationException.class)
                .hasMessageContaining("AWS Bedrock provider is disabled by configuration");

        verify(mockBedrockClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    @DisplayName("Throws AiProviderException when route target provider is not BEDROCK")
    void generate_MismatchedProvider_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder().prompt("Test").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.OPENAI, "gpt-4o");

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Bedrock provider adapter cannot process request for route provider: OPENAI");

        verify(mockBedrockClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    @DisplayName("Throws NullPointerException if request or route is null")
    void generate_NullArgs_ThrowsNullPointerException() {
        AiRequest request = AiRequest.builder().prompt("Test").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        assertThatThrownBy(() -> client.generate(null, route))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> client.generate(request, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("AccessDeniedException is normalized into AiAuthenticationException")
    void generate_AccessDeniedException_ThrowsAiAuthenticationException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-auth").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(AccessDeniedException.builder().message("Access denied to model").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiAuthenticationException.class)
                .hasMessageContaining("AWS Bedrock authentication or authorization failed: access denied to model");
    }

    @Test
    @DisplayName("ResourceNotFoundException is normalized into AiModelException")
    void generate_ResourceNotFoundException_ThrowsAiModelException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-model").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, "nonexistent.model");

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("Model not found").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiModelException.class)
                .hasMessageContaining("AWS Bedrock model not found or inaccessible: 'nonexistent.model'");
    }

    @Test
    @DisplayName("ThrottlingException is normalized into AiRateLimitException")
    void generate_ThrottlingException_ThrowsAiRateLimitException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-throttle").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(ThrottlingException.builder().message("Rate exceeded").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiRateLimitException.class)
                .hasMessageContaining("AWS Bedrock request throttled by provider rate limits");
    }

    @Test
    @DisplayName("ServiceQuotaExceededException is normalized into AiRateLimitException")
    void generate_ServiceQuotaExceeded_ThrowsAiRateLimitException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-quota").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(ServiceQuotaExceededException.builder().message("Quota limit reached").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiRateLimitException.class)
                .hasMessageContaining("AWS Bedrock service quota exceeded for model");
    }

    @Test
    @DisplayName("ModelTimeoutException is normalized into AiTimeoutException")
    void generate_ModelTimeoutException_ThrowsAiTimeoutException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-timeout").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(ModelTimeoutException.builder().message("Model execution timed out").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiTimeoutException.class)
                .hasMessageContaining("AWS Bedrock execution timed out");
    }

    @Test
    @DisplayName("ApiCallTimeoutException is normalized into AiTimeoutException")
    void generate_ApiCallTimeoutException_ThrowsAiTimeoutException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-timeout2").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("Client call timed out").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiTimeoutException.class)
                .hasMessageContaining("AWS Bedrock execution timed out");
    }

    @Test
    @DisplayName("ValidationException is normalized into AiProviderException")
    void generate_ValidationException_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-val").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(ValidationException.builder().message("Invalid temperature value").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AWS Bedrock request validation failed for model");
    }

    @Test
    @DisplayName("InternalServerException is normalized into AiProviderException")
    void generate_InternalServerException_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-internal").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(InternalServerException.builder().message("Internal error").build());

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AWS Bedrock service encountered an internal error");
    }

    @Test
    @DisplayName("SdkClientException with credential failure is normalized into AiAuthenticationException")
    void generate_SdkClientException_Credentials_ThrowsAiAuthenticationException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-creds").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials from any provider in the chain"));

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiAuthenticationException.class)
                .hasMessageContaining("AWS Bedrock credentials could not be resolved from the standard AWS credential chain");
    }

    @Test
    @DisplayName("SdkClientException generic connection failure is normalized into AiProviderException")
    void generate_SdkClientException_Generic_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-sdk").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(SdkClientException.create("Connection reset by peer"));

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AWS Bedrock SDK client error");
    }

    @Test
    @DisplayName("AwsServiceException is normalized into AiProviderException with status code")
    void generate_AwsServiceException_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-svc").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        AwsServiceException ex = AwsServiceException.builder()
                .statusCode(503)
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Service Unavailable").build())
                .message("Service Unavailable")
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenThrow(ex);

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AWS Bedrock service error [statusCode=503]");
    }

    @Test
    @DisplayName("Malformed response with null output throws AiProviderException")
    void generate_MalformedResponse_ThrowsAiProviderException() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-malformed").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        ConverseResponse malformed = ConverseResponse.builder()
                .output((ConverseOutput) null)
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(malformed);

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("AWS Bedrock returned an empty or malformed response structure");
    }

    @Test
    @DisplayName("Empty content blocks return empty string without failing")
    void generate_EmptyContent_ReturnsEmptyString() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-empty").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        ConverseResponse emptyOutput = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder().role(ConversationRole.ASSISTANT).content(java.util.List.of()).build())
                        .build())
                .stopReason(StopReason.CONTENT_FILTERED)
                .build();

        when(mockBedrockClient.converse(any(ConverseRequest.class))).thenReturn(emptyOutput);

        AiResponse response = client.generate(request, route);
        assertThat(response.generatedContent()).isEmpty();
        assertThat(response.finishReason()).isEqualTo("CONTENT_FILTERED");
    }

    @Test
    @DisplayName("Security: sensitive tokens in error messages are redacted")
    void security_SensitiveTokensRedactedInError() {
        AiRequest request = AiRequest.builder().prompt("Test").correlationId("trace-sec").build();
        ResolvedModelRoute route = new ResolvedModelRoute(AiProvider.BEDROCK, MODEL_ID);

        when(mockBedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(new RuntimeException("Call failed with token=SECRET_TOKEN_XYZ123 and key=AKIAEXAMPLEKEY"));

        assertThatThrownBy(() -> client.generate(request, route))
                .isInstanceOf(AiProviderException.class)
                .hasMessageNotContaining("SECRET_TOKEN_XYZ123")
                .hasMessageNotContaining("AKIAEXAMPLEKEY");
    }
}

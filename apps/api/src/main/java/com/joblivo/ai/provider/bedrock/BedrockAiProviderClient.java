package com.joblivo.ai.provider.bedrock;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.exception.AiAuthenticationException;
import com.joblivo.ai.exception.AiConfigurationException;
import com.joblivo.ai.exception.AiException;
import com.joblivo.ai.exception.AiModelException;
import com.joblivo.ai.exception.AiProviderException;
import com.joblivo.ai.exception.AiRateLimitException;
import com.joblivo.ai.exception.AiTimeoutException;
import com.joblivo.ai.model.AiRequest;
import com.joblivo.ai.model.AiResponse;
import com.joblivo.ai.model.AiTokenUsage;
import com.joblivo.ai.provider.AiProviderClient;
import com.joblivo.ai.routing.ResolvedModelRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InternalServerException;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Concrete {@link AiProviderClient} adapter integrating AWS Bedrock Runtime via the unified Converse API.
 * Encapsulates AWS SDK interactions, request transformation, response normalization, and error isolation.
 * Discovered and managed automatically by {@link com.joblivo.ai.provider.AiProviderRegistry}.
 */
@Component
public class BedrockAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockAiProviderClient.class);

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile("(?i)(key|secret|password|token|bearer|credential)=\\S+");

    private final AiConfigurationProperties properties;
    private final BedrockClientFactory clientFactory;

    public BedrockAiProviderClient(AiConfigurationProperties properties, BedrockClientFactory clientFactory) {
        this.properties = Objects.requireNonNull(properties, "AiConfigurationProperties must not be null");
        this.clientFactory = Objects.requireNonNull(clientFactory, "BedrockClientFactory must not be null");
    }

    /**
     * Convenience constructor for direct client injection in unit testing.
     */
    public BedrockAiProviderClient(AiConfigurationProperties properties, BedrockRuntimeClient directClient) {
        this(properties, () -> directClient);
    }

    @Override
    public AiProvider getProvider() {
        return AiProvider.BEDROCK;
    }

    @Override
    public AiResponse generate(AiRequest request, ResolvedModelRoute route) {
        Objects.requireNonNull(request, "AiRequest must not be null");
        Objects.requireNonNull(route, "ResolvedModelRoute must not be null");

        if (route.provider() != AiProvider.BEDROCK) {
            throw new AiProviderException(
                    "Bedrock provider adapter cannot process request for route provider: " + route.provider()
            );
        }

        if (!properties.isEnabled()) {
            throw new AiConfigurationException("AI Gateway is disabled by configuration (joblivo.ai.enabled=false)");
        }

        if (!properties.isProviderEnabled(AiProvider.BEDROCK)) {
            throw new AiConfigurationException("AWS Bedrock provider is disabled by configuration (joblivo.ai.providers.bedrock.enabled=false)");
        }

        String modelId = route.model();
        if (modelId == null || modelId.isBlank()) {
            throw new AiConfigurationException("Resolved Bedrock model identifier must not be null or blank");
        }

        ConverseRequest converseRequest = buildConverseRequest(request, modelId);

        log.debug("Dispatching ConverseRequest to AWS Bedrock [correlationId={}, model={}]",
                request.correlationId(), modelId);

        long startTime = System.currentTimeMillis();
        ConverseResponse response;
        try {
            BedrockRuntimeClient client = clientFactory.getClient();
            response = client.converse(converseRequest);
        } catch (AccessDeniedException e) {
            log.error("AWS Bedrock authorization failure [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiAuthenticationException(
                    "AWS Bedrock authentication or authorization failed: access denied to model '" + modelId + "'", e
            );
        } catch (ResourceNotFoundException e) {
            log.error("AWS Bedrock model not found [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiModelException(
                    "AWS Bedrock model not found or inaccessible: '" + modelId + "'", e
            );
        } catch (ThrottlingException e) {
            log.error("AWS Bedrock request throttled [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiRateLimitException(
                    "AWS Bedrock request throttled by provider rate limits for model '" + modelId + "'", e
            );
        } catch (ServiceQuotaExceededException e) {
            log.error("AWS Bedrock quota exceeded [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiRateLimitException(
                    "AWS Bedrock service quota exceeded for model '" + modelId + "'", e
            );
        } catch (ModelTimeoutException | ApiCallTimeoutException | ApiCallAttemptTimeoutException e) {
            log.error("AWS Bedrock execution timed out [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiTimeoutException(
                    "AWS Bedrock execution timed out for model '" + modelId + "'", e
            );
        } catch (ValidationException e) {
            log.error("AWS Bedrock request validation failure [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiProviderException(
                    "AWS Bedrock request validation failed for model '" + modelId + "': " + sanitize(e.getMessage()), e
            );
        } catch (InternalServerException e) {
            log.error("AWS Bedrock internal server error [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiProviderException(
                    "AWS Bedrock service encountered an internal error during inference for model '" + modelId + "'", e
            );
        } catch (SdkClientException e) {
            log.error("AWS Bedrock client error [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            if (isCredentialFailure(e)) {
                throw new AiAuthenticationException(
                        "AWS Bedrock credentials could not be resolved from the standard AWS credential chain", e
                );
            }
            throw new AiProviderException(
                    "AWS Bedrock SDK client error: " + sanitize(e.getMessage()), e
            );
        } catch (AwsServiceException e) {
            log.error("AWS Bedrock service error [correlationId={}, model={}, statusCode={}]: {}",
                    request.correlationId(), modelId, e.statusCode(), sanitize(e.getMessage()));
            throw new AiProviderException(
                    "AWS Bedrock service error [statusCode=" + e.statusCode() + "]: " + sanitize(e.getMessage()), e
            );
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during AWS Bedrock inference [correlationId={}, model={}]: {}",
                    request.correlationId(), modelId, sanitize(e.getMessage()));
            throw new AiProviderException(
                    "Unexpected failure during AWS Bedrock generation: " + sanitize(e.getMessage()), e
            );
        }

        long durationMs = System.currentTimeMillis() - startTime;
        return normalizeResponse(response, modelId, request.correlationId(), durationMs);
    }

    private ConverseRequest buildConverseRequest(AiRequest request, String modelId) {
        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(modelId);

        // Map system instruction if present
        if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
            SystemContentBlock systemBlock = SystemContentBlock.fromText(request.systemInstruction().trim());
            builder.system(systemBlock);
        }

        // Map user prompt into user message
        ContentBlock contentBlock = ContentBlock.fromText(request.prompt());
        Message userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(contentBlock)
                .build();
        builder.messages(userMessage);

        // Map optional inference configuration (temperature, maxOutputTokens)
        if (request.temperature() != null || request.maxOutputTokens() != null) {
            InferenceConfiguration.Builder infBuilder = InferenceConfiguration.builder();
            if (request.temperature() != null) {
                infBuilder.temperature(request.temperature().floatValue());
            }
            if (request.maxOutputTokens() != null) {
                infBuilder.maxTokens(request.maxOutputTokens());
            }
            builder.inferenceConfig(infBuilder.build());
        }

        return builder.build();
    }

    private AiResponse normalizeResponse(ConverseResponse response, String modelId, String correlationId, long durationMs) {
        String generatedContent = extractGeneratedContent(response);
        AiTokenUsage tokenUsage = extractTokenUsage(response.usage());

        StopReason stopReason = response.stopReason();
        String finishReason = stopReason != null ? stopReason.name() : null;

        log.info("AWS Bedrock generation completed [correlationId={}, model={}, finishReason={}, promptTokens={}, completionTokens={}, totalTokens={}, durationMs={}]",
                correlationId,
                modelId,
                finishReason,
                tokenUsage != null ? tokenUsage.promptTokens() : null,
                tokenUsage != null ? tokenUsage.completionTokens() : null,
                tokenUsage != null ? tokenUsage.totalTokens() : null,
                durationMs);

        return AiResponse.builder()
                .generatedContent(generatedContent)
                .provider(AiProvider.BEDROCK)
                .model(modelId)
                .correlationId(correlationId)
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .durationMs(durationMs)
                .build();
    }

    private String extractGeneratedContent(ConverseResponse response) {
        if (response == null || response.output() == null || response.output().message() == null) {
            throw new AiProviderException("AWS Bedrock returned an empty or malformed response structure");
        }

        Message message = response.output().message();
        if (message.content() == null || message.content().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block.text() != null) {
                sb.append(block.text());
            }
        }
        return sb.toString();
    }

    private AiTokenUsage extractTokenUsage(TokenUsage usage) {
        if (usage == null) {
            return null;
        }

        Integer promptTokens = usage.inputTokens();
        Integer completionTokens = usage.outputTokens();
        Integer totalTokens = usage.totalTokens();

        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return null;
        }

        return AiTokenUsage.of(promptTokens, completionTokens, totalTokens);
    }

    private boolean isCredentialFailure(SdkClientException e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("credentials")
                || message.contains("access key")
                || message.contains("security token")
                || (e.getCause() != null && isCredentialFailureCause(e.getCause()));
    }

    private boolean isCredentialFailureCause(Throwable cause) {
        String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        return msg.contains("credentials")
                || msg.contains("access key")
                || msg.contains("security token");
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "No detail provided";
        }
        return SENSITIVE_PATTERN.matcher(raw).replaceAll("$1=[REDACTED]");
    }
}

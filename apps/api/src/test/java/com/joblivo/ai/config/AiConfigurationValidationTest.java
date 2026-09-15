package com.joblivo.ai.config;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.exception.AiConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiConfigurationProperties Validation Tests")
class AiConfigurationValidationTest {

    private AiConfigurationProperties properties;

    private static final String BEDROCK_DEFAULT_MODEL = "anthropic.claude-3-5-sonnet";
    private static final String BEDROCK_ALT_MODEL = "anthropic.claude-3-haiku";
    private static final String OPENAI_DEFAULT_MODEL = "gpt-4o";

    @BeforeEach
    void setUp() {
        properties = new AiConfigurationProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider(AiProvider.BEDROCK);
        properties.setDefaultModel(BEDROCK_DEFAULT_MODEL);

        AiProviderProperties bedrockProps = new AiProviderProperties(true, BEDROCK_DEFAULT_MODEL,
                Set.of(BEDROCK_DEFAULT_MODEL, BEDROCK_ALT_MODEL));
        AiProviderProperties openAiProps = new AiProviderProperties(true, OPENAI_DEFAULT_MODEL,
                Set.of(OPENAI_DEFAULT_MODEL));

        properties.setProviders(new HashMap<>(Map.of(
                AiProvider.BEDROCK, bedrockProps,
                AiProvider.OPENAI, openAiProps
        )));
    }

    @Nested
    @DisplayName("AI Disabled Behavior")
    class AiDisabledTests {

        @Test
        @DisplayName("Validation succeeds if AI is disabled even if properties are incomplete")
        void validationSucceedsWhenDisabled() {
            properties.setEnabled(false);
            properties.setDefaultProvider(null);
            properties.setDefaultModel(null);
            properties.setProviders(Map.of());

            assertThatCode(() -> properties.validateConfiguration())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Valid Configuration")
    class ValidConfigurationTests {

        @Test
        @DisplayName("Validation succeeds for a complete and consistent configuration")
        void validConfigurationPasses() {
            properties.setPurposeRoutes(Map.of(
                    "RESUME_SUMMARY", new PurposeRouteProperties(AiProvider.BEDROCK, BEDROCK_ALT_MODEL),
                    "CHAT", new PurposeRouteProperties(AiProvider.OPENAI, OPENAI_DEFAULT_MODEL)
            ));
            properties.setModelsByPurpose(Map.of(
                    "LEGACY_SUMMARY", BEDROCK_ALT_MODEL
            ));

            assertThatCode(() -> properties.validateConfiguration())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Validation succeeds when provider allow-list is empty (defaults to allowing defaultModel)")
        void validConfigurationWithEmptyAllowList() {
            AiProviderProperties bedrockProps = new AiProviderProperties(true, BEDROCK_DEFAULT_MODEL, Set.of());
            properties.setProviders(Map.of(AiProvider.BEDROCK, bedrockProps));

            assertThatCode(() -> properties.validateConfiguration())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Default Provider and Model Validation")
    class DefaultProviderAndModelTests {

        @Test
        @DisplayName("Fails when defaultProvider is null")
        void failsWhenDefaultProviderNull() {
            properties.setDefaultProvider(null);

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("Missing default AI provider");
        }

        @Test
        @DisplayName("Fails when defaultProvider is not configured in providers map")
        void failsWhenDefaultProviderNotConfigured() {
            properties.setProviders(Map.of());

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("Default AI provider 'BEDROCK' is disabled or not configured");
        }

        @Test
        @DisplayName("Fails when defaultProvider is explicitly disabled")
        void failsWhenDefaultProviderDisabled() {
            properties.getProviders().put(AiProvider.BEDROCK, new AiProviderProperties(false, BEDROCK_DEFAULT_MODEL));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("Default AI provider 'BEDROCK' is disabled or not configured");
        }

        @Test
        @DisplayName("Fails when default model is missing across provider and global config")
        void failsWhenDefaultModelMissing() {
            properties.setDefaultModel(null);
            properties.getProviders().put(AiProvider.BEDROCK, new AiProviderProperties(true, null, Set.of()));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("No default model configured for default provider: BEDROCK");
        }

        @Test
        @DisplayName("Fails when default model is not allowed by provider's allowedModels")
        void failsWhenDefaultModelNotAllowed() {
            AiProviderProperties bedrockProps = new AiProviderProperties(true, "disallowed-default-model",
                    Set.of("allowed-model-1", "allowed-model-2"));
            properties.setDefaultModel("disallowed-default-model");
            properties.getProviders().put(AiProvider.BEDROCK, bedrockProps);

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("is not permitted by provider configuration for 'BEDROCK'");
        }
    }

    @Nested
    @DisplayName("Purpose Routes Validation")
    class PurposeRoutesTests {

        @Test
        @DisplayName("Fails when purpose route model is null or blank")
        void failsWhenPurposeRouteModelBlank() {
            properties.setPurposeRoutes(Map.of(
                    "EMPTY_MODEL_PURPOSE", new PurposeRouteProperties(AiProvider.BEDROCK, "   ")
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("Purpose route 'EMPTY_MODEL_PURPOSE' has missing or blank model");
        }

        @Test
        @DisplayName("Fails when purpose route specifies an unconfigured or disabled provider")
        void failsWhenPurposeRouteProviderDisabled() {
            AiProviderProperties googleProps = new AiProviderProperties(false, "gemini-1.5");
            properties.getProviders().put(AiProvider.GOOGLE, googleProps);

            properties.setPurposeRoutes(Map.of(
                    "TRANSLATION", new PurposeRouteProperties(AiProvider.GOOGLE, "gemini-1.5")
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("targets disabled or unconfigured provider: GOOGLE");
        }

        @Test
        @DisplayName("Fails when purpose route specifies a model disallowed by the target provider")
        void failsWhenPurposeRouteModelDisallowed() {
            properties.setPurposeRoutes(Map.of(
                    "EXPENSIVE_TASK", new PurposeRouteProperties(AiProvider.BEDROCK, "unauthorized-claude-op")
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("specifies model 'unauthorized-claude-op' which is not permitted for provider 'BEDROCK'");
        }

        @Test
        @DisplayName("Inherits defaultProvider when purpose route provider is null and validates against defaultProvider allow-list")
        void inheritsDefaultProviderAndValidatesDisallowedModel() {
            properties.setPurposeRoutes(Map.of(
                    "LOCAL_PURPOSE", new PurposeRouteProperties(null, "unauthorized-model")
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("specifies model 'unauthorized-model' which is not permitted for provider 'BEDROCK'");
        }
    }

    @Nested
    @DisplayName("Legacy modelsByPurpose Validation")
    class LegacyModelsByPurposeTests {

        @Test
        @DisplayName("Fails when legacy modelsByPurpose specifies a model disallowed by default provider")
        void failsWhenLegacyModelDisallowed() {
            properties.setModelsByPurpose(Map.of(
                    "OLD_PURPOSE", "disallowed-legacy-model"
            ));

            assertThatThrownBy(() -> properties.validateConfiguration())
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("Purpose mapping 'OLD_PURPOSE' specifies model 'disallowed-legacy-model' which is not permitted");
        }

        @Test
        @DisplayName("Passes when legacy modelsByPurpose specifies an allowed model")
        void passesWhenLegacyModelAllowed() {
            properties.setModelsByPurpose(Map.of(
                    "OLD_PURPOSE", BEDROCK_ALT_MODEL
            ));

            assertThatCode(() -> properties.validateConfiguration())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Provider Properties Model Allow-List Semantics")
    class ModelAllowListSemanticsTests {

        @Test
        @DisplayName("isModelAllowed returns true for exact matches in allowedModels")
        void returnsTrueForAllowedModel() {
            AiProviderProperties props = new AiProviderProperties(true, "model-a", Set.of("model-a", "model-b"));
            assertThat(props.isModelAllowed("model-a")).isTrue();
            assertThat(props.isModelAllowed("model-b")).isTrue();
            assertThat(props.isModelAllowed("model-c")).isFalse();
        }

        @Test
        @DisplayName("isModelAllowed allows defaultModel if allowedModels is empty")
        void allowsDefaultModelWhenEmptyAllowList() {
            AiProviderProperties props = new AiProviderProperties(true, "default-model", Set.of());
            assertThat(props.isModelAllowed("default-model")).isTrue();
            assertThat(props.isModelAllowed("other-model")).isFalse();
        }

        @Test
        @DisplayName("isModelAllowed handles trimmed comparisons")
        void handlesTrimmedComparisons() {
            AiProviderProperties props = new AiProviderProperties(true, "model-a", Set.of("model-a"));
            assertThat(props.isModelAllowed(" model-a ")).isTrue();
            assertThat(props.isModelAllowed("")).isFalse();
            assertThat(props.isModelAllowed(null)).isFalse();
        }
    }
}

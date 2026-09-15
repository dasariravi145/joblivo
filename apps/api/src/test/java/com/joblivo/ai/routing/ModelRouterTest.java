package com.joblivo.ai.routing;

import com.joblivo.ai.AiProvider;
import com.joblivo.ai.config.AiConfigurationProperties;
import com.joblivo.ai.config.AiProviderProperties;
import com.joblivo.ai.config.PurposeRouteProperties;
import com.joblivo.ai.exception.AiConfigurationException;
import com.joblivo.ai.exception.AiRoutingException;
import com.joblivo.ai.model.AiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ModelRouter Unit Tests")
class ModelRouterTest {

    private AiConfigurationProperties properties;
    private DefaultModelRouter router;

    private static final String BEDROCK_DEFAULT_MODEL = "anthropic.claude-3-5-sonnet";
    private static final String BEDROCK_ALT_MODEL = "anthropic.claude-3-haiku";
    private static final String BEDROCK_CUSTOM_MODEL = "custom-experimental-model";

    private static final String OPENAI_DEFAULT_MODEL = "gpt-4o";
    private static final String OPENAI_ALT_MODEL = "gpt-4o-mini";

    @BeforeEach
    void setUp() {
        properties = new AiConfigurationProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider(AiProvider.BEDROCK);
        properties.setDefaultModel(BEDROCK_DEFAULT_MODEL);

        AiProviderProperties bedrockProps = new AiProviderProperties(true, BEDROCK_DEFAULT_MODEL,
                Set.of(BEDROCK_DEFAULT_MODEL, BEDROCK_ALT_MODEL, BEDROCK_CUSTOM_MODEL));

        AiProviderProperties openAiProps = new AiProviderProperties(true, OPENAI_DEFAULT_MODEL,
                Set.of(OPENAI_DEFAULT_MODEL, OPENAI_ALT_MODEL));

        AiProviderProperties googleProps = new AiProviderProperties(false, "gemini-1.5-pro",
                Set.of("gemini-1.5-pro")); // disabled

        properties.setProviders(Map.of(
                AiProvider.BEDROCK, bedrockProps,
                AiProvider.OPENAI, openAiProps,
                AiProvider.GOOGLE, googleProps
        ));

        properties.setModelsByPurpose(Map.of(
                "CAREER_SUMMARY", BEDROCK_ALT_MODEL
        ));

        properties.setPurposeRoutes(Map.of(
                "ADVANCED_MATCHING", new PurposeRouteProperties(AiProvider.OPENAI, OPENAI_DEFAULT_MODEL),
                "QUICK_DRAFT", new PurposeRouteProperties(null, BEDROCK_ALT_MODEL)
        ));

        router = new DefaultModelRouter(properties);
    }

    @Nested
    @DisplayName("A. Explicit Routing")
    class ExplicitRoutingTests {

        @Test
        @DisplayName("Valid explicit provider + model routes correctly with EXPLICIT source")
        void explicitProviderAndModel_WhenValid_RoutesCorrectly() {
            AiRequest request = AiRequest.builder()
                    .prompt("Custom task")
                    .requestedProvider(AiProvider.BEDROCK)
                    .requestedModel(BEDROCK_CUSTOM_MODEL)
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_CUSTOM_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.EXPLICIT);
        }

        @Test
        @DisplayName("Explicit provider without model resolves provider default model with PROVIDER_DEFAULT source")
        void explicitProvider_WithoutModel_ResolvesProviderDefault() {
            AiRequest request = AiRequest.builder()
                    .prompt("OpenAI task")
                    .requestedProvider(AiProvider.OPENAI)
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.OPENAI);
            assertThat(route.model()).isEqualTo(OPENAI_DEFAULT_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.PROVIDER_DEFAULT);
        }

        @Test
        @DisplayName("Explicit model without provider uses default provider with EXPLICIT source")
        void explicitModel_WithoutProvider_UsesDefaultProvider() {
            AiRequest request = AiRequest.builder()
                    .prompt("Custom model task")
                    .requestedModel(BEDROCK_ALT_MODEL)
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_ALT_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.EXPLICIT);
        }

        @Test
        @DisplayName("Fail-closed: Requesting disabled provider throws AiRoutingException and does NOT fall back")
        void explicitProvider_WhenDisabled_ThrowsAiRoutingException() {
            AiRequest request = AiRequest.builder()
                    .prompt("Google task")
                    .requestedProvider(AiProvider.GOOGLE)
                    .build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("Requested AI provider 'GOOGLE' is disabled or not configured");
        }

        @Test
        @DisplayName("Fail-closed: Requesting unconfigured/disallowed model throws AiRoutingException and does NOT fall back")
        void explicitModel_WhenNotAllowed_ThrowsAiRoutingException() {
            AiRequest request = AiRequest.builder()
                    .prompt("Custom task")
                    .requestedProvider(AiProvider.BEDROCK)
                    .requestedModel("unauthorized-model-99")
                    .build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("Model 'unauthorized-model-99' is not configured or allowed for provider 'BEDROCK'");
        }

        @Test
        @DisplayName("Fail-closed: Requesting unconfigured model without provider throws AiRoutingException and does NOT fall back")
        void explicitModel_WithoutProvider_WhenNotAllowed_ThrowsAiRoutingException() {
            AiRequest request = AiRequest.builder()
                    .prompt("Custom task")
                    .requestedModel("unauthorized-model-99")
                    .build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("Model 'unauthorized-model-99' is not configured or allowed for provider 'BEDROCK'");
        }
    }

    @Nested
    @DisplayName("B. Default Routing")
    class DefaultRoutingTests {

        @Test
        @DisplayName("Default routing uses configured default provider and model with GLOBAL_DEFAULT source")
        void defaultRouting_UsesDefaultProviderAndModel() {
            AiRequest request = AiRequest.builder().prompt("test prompt").build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_DEFAULT_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.GLOBAL_DEFAULT);
        }

        @Test
        @DisplayName("Fail-closed: Throws AiConfigurationException if AI Gateway is disabled")
        void disabledGateway_ThrowsAiConfigurationException() {
            properties.setEnabled(false);

            AiRequest request = AiRequest.builder().prompt("test prompt").build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("AI Gateway is disabled by configuration");
        }

        @Test
        @DisplayName("Fail-closed: Throws AiConfigurationException if default provider is null")
        void missingDefaultProvider_ThrowsAiConfigurationException() {
            properties.setDefaultProvider(null);

            AiRequest request = AiRequest.builder().prompt("test prompt").build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiConfigurationException.class)
                    .hasMessageContaining("No default AI provider configured");
        }

        @Test
        @DisplayName("Fail-closed: Throws AiRoutingException when default provider is disabled")
        void disabledDefaultProvider_ThrowsAiRoutingException() {
            properties.getProviders().get(AiProvider.BEDROCK).setEnabled(false);

            AiRequest request = AiRequest.builder().prompt("test prompt").build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("Default AI provider 'BEDROCK' is disabled or not configured");
        }

        @Test
        @DisplayName("Fail-closed: Throws AiRoutingException when default model is disallowed")
        void disallowedDefaultModel_ThrowsAiRoutingException() {
            properties.getProviders().get(AiProvider.BEDROCK).setAllowedModels(Set.of("allowed-only-model"));

            AiRequest request = AiRequest.builder().prompt("test prompt").build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("is not configured or allowed for provider 'BEDROCK'");
        }

        @Test
        @DisplayName("Fail-closed: Throws AiRoutingException if no model can be resolved anywhere")
        void unresolvableModel_ThrowsAiRoutingException() {
            properties.setDefaultModel(null);
            properties.getProviders().get(AiProvider.BEDROCK).setDefaultModel(null);
            properties.setPurposeRoutes(Map.of());
            properties.setModelsByPurpose(Map.of());

            AiRequest request = AiRequest.builder().prompt("test prompt").build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("No model could be resolved for default AI provider: BEDROCK");
        }
    }

    @Nested
    @DisplayName("C. Purpose Routing")
    class PurposeRoutingTests {

        @Test
        @DisplayName("Structured purpose route resolves configured provider, model, and PURPOSE source")
        void purposeRouting_StructuredRoute_ResolvesConfiguredProviderAndModel() {
            AiRequest request = AiRequest.builder()
                    .prompt("Advanced matching analysis")
                    .purpose("ADVANCED_MATCHING")
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.OPENAI);
            assertThat(route.model()).isEqualTo(OPENAI_DEFAULT_MODEL);
            assertThat(route.purpose()).isEqualTo("ADVANCED_MATCHING");
            assertThat(route.source()).isEqualTo(RoutingSource.PURPOSE);
        }

        @Test
        @DisplayName("Structured purpose route without provider uses default provider")
        void purposeRouting_StructuredRoute_NoProvider_UsesDefaultProvider() {
            AiRequest request = AiRequest.builder()
                    .prompt("Quick draft")
                    .purpose("QUICK_DRAFT")
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_ALT_MODEL);
            assertThat(route.purpose()).isEqualTo("QUICK_DRAFT");
            assertThat(route.source()).isEqualTo(RoutingSource.PURPOSE);
        }

        @Test
        @DisplayName("Legacy modelsByPurpose route resolves model with default provider and PURPOSE source")
        void purposeRouting_LegacyMapping_ResolvesModelWithDefaultProvider() {
            AiRequest request = AiRequest.builder()
                    .prompt("Generate career summary")
                    .purpose("CAREER_SUMMARY")
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_ALT_MODEL);
            assertThat(route.purpose()).isEqualTo("CAREER_SUMMARY");
            assertThat(route.source()).isEqualTo(RoutingSource.PURPOSE);
        }

        @Test
        @DisplayName("Unknown purpose falls back to default route with GLOBAL_DEFAULT source")
        void purposeRouting_UnknownPurpose_UsesDefaultRoute() {
            AiRequest request = AiRequest.builder()
                    .prompt("Unmapped task")
                    .purpose("NONEXISTENT_PURPOSE")
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_DEFAULT_MODEL);
            assertThat(route.purpose()).isEqualTo("NONEXISTENT_PURPOSE");
            assertThat(route.source()).isEqualTo(RoutingSource.GLOBAL_DEFAULT);
        }

        @Test
        @DisplayName("Fail-closed: Purpose route specifying disabled provider throws AiRoutingException")
        void purposeRouting_DisabledProvider_ThrowsAiRoutingException() {
            properties.setPurposeRoutes(Map.of(
                    "DISABLED_TASK", new PurposeRouteProperties(AiProvider.GOOGLE, "gemini-1.5-pro")
            ));

            AiRequest request = AiRequest.builder()
                    .prompt("Test")
                    .purpose("DISABLED_TASK")
                    .build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("AI provider 'GOOGLE' is disabled or not configured");
        }

        @Test
        @DisplayName("Fail-closed: Purpose route specifying disallowed model throws AiRoutingException")
        void purposeRouting_DisallowedModel_ThrowsAiRoutingException() {
            properties.setPurposeRoutes(Map.of(
                    "INVALID_MODEL_TASK", new PurposeRouteProperties(AiProvider.BEDROCK, "unallowed-claude-v99")
            ));

            AiRequest request = AiRequest.builder()
                    .prompt("Test")
                    .purpose("INVALID_MODEL_TASK")
                    .build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageContaining("Model 'unallowed-claude-v99' is not configured or allowed for provider 'BEDROCK'");
        }
    }

    @Nested
    @DisplayName("D. Routing Precedence")
    class RoutingPrecedenceTests {

        @Test
        @DisplayName("Precedence 1: Explicit provider + model overrides purpose route and defaults")
        void precedence_ExplicitProviderAndModel_BeatsAll() {
            AiRequest request = AiRequest.builder()
                    .prompt("Task")
                    .purpose("ADVANCED_MATCHING") // Purpose says OPENAI, gpt-4o
                    .requestedProvider(AiProvider.BEDROCK)
                    .requestedModel(BEDROCK_CUSTOM_MODEL)
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_CUSTOM_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.EXPLICIT);
        }

        @Test
        @DisplayName("Precedence 2: Explicit provider with default model overrides purpose route")
        void precedence_ExplicitProvider_OverridesPurposeRoute() {
            AiRequest request = AiRequest.builder()
                    .prompt("Task")
                    .purpose("QUICK_DRAFT") // Purpose says BEDROCK, BEDROCK_ALT_MODEL
                    .requestedProvider(AiProvider.OPENAI) // Explicit provider only
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.OPENAI);
            assertThat(route.model()).isEqualTo(OPENAI_DEFAULT_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.PROVIDER_DEFAULT);
        }

        @Test
        @DisplayName("Precedence 3: Purpose route overrides global default route")
        void precedence_PurposeRoute_OverridesGlobalDefault() {
            AiRequest request = AiRequest.builder()
                    .prompt("Task")
                    .purpose("ADVANCED_MATCHING") // maps to OPENAI, gpt-4o
                    .build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.OPENAI);
            assertThat(route.model()).isEqualTo(OPENAI_DEFAULT_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.PURPOSE);
        }

        @Test
        @DisplayName("Precedence 4: Global default used when no explicit preference or purpose route")
        void precedence_GlobalDefault_UsedWhenNoOtherPreference() {
            AiRequest request = AiRequest.builder().prompt("Task").build();

            ResolvedModelRoute route = router.route(request);

            assertThat(route.provider()).isEqualTo(AiProvider.BEDROCK);
            assertThat(route.model()).isEqualTo(BEDROCK_DEFAULT_MODEL);
            assertThat(route.source()).isEqualTo(RoutingSource.GLOBAL_DEFAULT);
        }
    }

    @Nested
    @DisplayName("E. Security & Safe Logging")
    class SecurityAndSafetyTests {

        @Test
        @DisplayName("Exceptions do not leak secrets or credentials")
        void routingExceptions_DoNotContainSecrets() {
            AiRequest request = AiRequest.builder()
                    .prompt("Sensitive prompt with confidential content")
                    .requestedProvider(AiProvider.GOOGLE)
                    .build();

            assertThatThrownBy(() -> router.route(request))
                    .isInstanceOf(AiRoutingException.class)
                    .hasMessageNotContaining("Sensitive prompt")
                    .hasMessageNotContaining("secret");
        }

        @Test
        @DisplayName("Throws NullPointerException if request is null")
        void nullRequest_ThrowsNullPointerException() {
            assertThatThrownBy(() -> router.route(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("AiRequest must not be null");
        }
    }
}

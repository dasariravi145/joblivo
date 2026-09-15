package com.joblivo.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiProvider Enum Tests")
class AiProviderTest {

    @Test
    @DisplayName("Supported providers contain BEDROCK, OPENAI, and GOOGLE")
    void supportedProviders_ContainsAllExpectedBackends() {
        assertThat(AiProvider.values())
                .containsExactlyInAnyOrder(AiProvider.BEDROCK, AiProvider.OPENAI, AiProvider.GOOGLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEDROCK", "bedrock", "BedRock", "  bedrock  "})
    @DisplayName("fromString correctly resolves BEDROCK regardless of case or whitespace")
    void fromString_ResolvesBedrock(String input) {
        Optional<AiProvider> provider = AiProvider.fromString(input);
        assertThat(provider).contains(AiProvider.BEDROCK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPENAI", "openai", "OpenAi", "  openai  "})
    @DisplayName("fromString correctly resolves OPENAI regardless of case or whitespace")
    void fromString_ResolvesOpenAi(String input) {
        Optional<AiProvider> provider = AiProvider.fromString(input);
        assertThat(provider).contains(AiProvider.OPENAI);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOOGLE", "google", "Google", "  google  "})
    @DisplayName("fromString correctly resolves GOOGLE regardless of case or whitespace")
    void fromString_ResolvesGoogle(String input) {
        Optional<AiProvider> provider = AiProvider.fromString(input);
        assertThat(provider).contains(AiProvider.GOOGLE);
    }

    @Test
    @DisplayName("fromString returns empty for null, blank, or unsupported providers")
    void fromString_ReturnsEmptyForInvalidInputs() {
        assertThat(AiProvider.fromString(null)).isEmpty();
        assertThat(AiProvider.fromString("")).isEmpty();
        assertThat(AiProvider.fromString("   ")).isEmpty();
        assertThat(AiProvider.fromString("ANTHROPIC")).isEmpty();
        assertThat(AiProvider.fromString("UNKNOWN_PROVIDER")).isEmpty();
    }
}

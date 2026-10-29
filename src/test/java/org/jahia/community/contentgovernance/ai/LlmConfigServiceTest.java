package org.jahia.community.contentgovernance.ai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigServiceTest {

    private static LlmConfigService configured(Map<String, Object> extra) {
        Map<String, Object> p = new HashMap<>();
        p.put("AI_PROVIDER", "Anthropic");
        p.put("AI_MODEL", "some-model");
        p.put("AI_API_KEY", "secret");
        p.putAll(extra);
        LlmConfigService s = new LlmConfigService();
        s.activate(p);
        return s;
    }

    @Test
    void disabledUntilProviderModelAndKeyAreSet() {
        LlmConfigService s = new LlmConfigService();
        s.activate(Map.of("AI_PROVIDER", "anthropic", "AI_MODEL", "m"));
        assertThat(s.isEnabled()).isFalse();
        assertThat(s.provider()).isNull();
        assertThat(s.settings(100)).isNull();
        assertThat(s.providerName()).isEmpty();
        assertThat(s.model()).isEmpty();
    }

    @Test
    void unknownProviderIsDisabled() {
        assertThat(configured(Map.of("AI_PROVIDER", "mistral")).isEnabled()).isFalse();
    }

    @Test
    void settingsUseTheProviderDefaultBaseUnlessConfigured() {
        LlmConfigService s = configured(Map.of());
        assertThat(s.isEnabled()).isTrue();
        assertThat(s.providerName()).isEqualTo("anthropic");
        assertThat(s.settings(s.maxTokens()).baseUrl()).isEqualTo("https://api.anthropic.com");
        assertThat(s.settings(s.reportMaxTokens()).maxTokens()).isEqualTo(6000);
        assertThat(configured(Map.of("AI_BASE_URL", "https://proxy.example/")).settings(10).baseUrl())
                .isEqualTo("https://proxy.example");
    }

    @Test
    void imagesFollowTheProvider() {
        assertThat(configured(Map.of()).supportsImages()).isTrue();
        assertThat(configured(Map.of("AI_PROVIDER", "openai")).supportsImages()).isTrue();
        assertThat(configured(Map.of("AI_PROVIDER", "deepseek")).supportsImages()).isFalse();
    }

    @Test
    void appendixIsAddedOnlyWhenSet() {
        assertThat(configured(Map.of()).withAppendix("base")).isEqualTo("base");
        assertThat(configured(Map.of("AI_PROMPT_APPENDIX", "Be formal.")).withAppendix("base"))
                .startsWith("base\n\n").endsWith("Be formal.");
    }

    @Test
    void costIsUnknownUntilConfiguredAndBadNumbersFallBack() {
        LlmConfigService s = configured(Map.of("AI_MAX_TOKENS", "lots", "AI_COST_INPUT_PER_MTOKENS", "3.5"));
        assertThat(s.maxTokens()).isEqualTo(4096);
        assertThat(s.costInputPerMTokens()).isEqualTo(3.5);
        assertThat(s.costOutputPerMTokens()).isZero();
    }
}

package org.jahia.community.contentgovernance.ai;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a call that wants a JSON answer asks each provider for, and when it asks again. */
class JsonAnswerTest {

    private static LlmSettings settings(String model) {
        return new LlmSettings("openai", "k", "https://api.openai.com", model, 4096);
    }

    @Test
    void asksForJsonOnlyWhenTheCallWantsIt() {
        OpenAiProvider openai = new OpenAiProvider();
        assertThat(openai.optionalFields(settings("gpt-4.1"))).isEmpty();
        Map<String, Object> json = openai.optionalFields(settings("gpt-4.1").withJsonAnswer());
        assertThat(json).containsOnlyKeys("response_format");
        assertThat(((JSONObject) json.get("response_format")).getString("type")).isEqualTo("json_object");
    }

    @Test
    void keepsReasoningShortOnReasoningModels() {
        OpenAiProvider openai = new OpenAiProvider();
        assertThat(openai.optionalFields(settings("gpt-5-mini").withJsonAnswer()))
                .containsEntry("reasoning_effort", "low");
        assertThat(OpenAiProvider.isReasoningModel("o3-mini")).isTrue();
        assertThat(OpenAiProvider.isReasoningModel("o1")).isTrue();
        assertThat(OpenAiProvider.isReasoningModel("gpt-5")).isTrue();
        assertThat(OpenAiProvider.isReasoningModel("gpt-4o")).isFalse();
        assertThat(OpenAiProvider.isReasoningModel("omni")).isFalse();
        assertThat(OpenAiProvider.isReasoningModel(null)).isFalse();
    }

    @Test
    void deepSeekAsksForJsonToo() {
        assertThat(new DeepSeekProvider().optionalFields(settings("deepseek-chat").withJsonAnswer()))
                .containsOnlyKeys("response_format");
    }

    @Test
    void retriesWithoutOptionalFieldsOnlyWhenTheRequestIsRefused() {
        assertThat(OpenAiCompatibleProvider.isRejectedRequest(new IOException("provider answered HTTP 400: unsupported"))).isTrue();
        assertThat(OpenAiCompatibleProvider.isRejectedRequest(new IOException("provider answered HTTP 422: bad"))).isTrue();
        assertThat(OpenAiCompatibleProvider.isRejectedRequest(new IOException("provider answered HTTP 401: key"))).isFalse();
        assertThat(OpenAiCompatibleProvider.isRejectedRequest(new IOException("connect timed out"))).isFalse();
    }

    @Test
    void jsonAnswerKeepsTheOtherSettings() {
        LlmSettings s = settings("gpt-4.1").withJsonAnswer();
        assertThat(s.jsonAnswer()).isTrue();
        assertThat(s.model()).isEqualTo("gpt-4.1");
        assertThat(s.maxTokens()).isEqualTo(4096);
        assertThat(settings("gpt-4.1").jsonAnswer()).isFalse();
    }
}

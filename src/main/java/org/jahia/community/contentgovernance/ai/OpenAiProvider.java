package org.jahia.community.contentgovernance.ai;

import java.util.Locale;
import java.util.Map;

/** OpenAI's Chat Completions API. */
public final class OpenAiProvider extends OpenAiCompatibleProvider {

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.openai.com";
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    protected Map<String, Object> optionalFields(LlmSettings settings) {
        Map<String, Object> fields = super.optionalFields(settings);
        if (isReasoningModel(settings.model())) {
            // A structured answer needs little reasoning, and the reasoning shares the output budget.
            fields.put("reasoning_effort", "low");
        }
        return fields;
    }

    /** OpenAI's reasoning families (o1, o3, o4, gpt-5): they accept {@code reasoning_effort}. */
    static boolean isReasoningModel(String model) {
        String m = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        if (m.startsWith("gpt-5")) {
            return true;
        }
        return m.length() > 1 && m.charAt(0) == 'o' && Character.isDigit(m.charAt(1));
    }

    @Override
    protected String maxTokensField() {
        // Renamed by OpenAI; the gpt-5 family rejects the old name.
        return "max_completion_tokens";
    }
}

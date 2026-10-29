package org.jahia.community.contentgovernance.ai;

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
    protected String maxTokensField() {
        // Renamed by OpenAI; the gpt-5 family rejects the old name.
        return "max_completion_tokens";
    }
}

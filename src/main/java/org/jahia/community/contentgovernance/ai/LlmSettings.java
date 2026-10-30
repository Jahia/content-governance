package org.jahia.community.contentgovernance.ai;

/**
 * Connection settings for one provider, resolved from the module's OSGi
 * configuration at call time. Immutable, and its {@code toString} never prints
 * the key: this object ends up in log lines.
 */
public final class LlmSettings {

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final boolean jsonAnswer;

    public LlmSettings(String provider, String apiKey, String baseUrl, String model, int maxTokens) {
        this(provider, apiKey, baseUrl, model, maxTokens, false);
    }

    private LlmSettings(String provider, String apiKey, String baseUrl, String model, int maxTokens, boolean jsonAnswer) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = trimTrailingSlashes(baseUrl);
        this.model = model;
        this.maxTokens = maxTokens;
        this.jsonAnswer = jsonAnswer;
    }

    /** The same settings for a call whose answer must be one JSON object. */
    public LlmSettings withJsonAnswer() {
        return new LlmSettings(provider, apiKey, baseUrl, model, maxTokens, true);
    }

    /** Whether the answer must be one JSON object: providers that can ask for it do. */
    public boolean jsonAnswer() {
        return jsonAnswer;
    }

    /** Trailing slashes removed by walking the end, so no pattern can backtrack over a long url. */
    private static String trimTrailingSlashes(String url) {
        if (url == null) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    public String provider() {
        return provider;
    }

    public String apiKey() {
        return apiKey;
    }

    /** The API base without a trailing slash, e.g. {@code https://api.anthropic.com}. */
    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "LlmSettings[provider=" + provider + ", model=" + model + ", baseUrl=" + baseUrl
                + ", maxTokens=" + maxTokens + ", apiKey=" + (hasApiKey() ? "***" : "<empty>") + "]";
    }
}

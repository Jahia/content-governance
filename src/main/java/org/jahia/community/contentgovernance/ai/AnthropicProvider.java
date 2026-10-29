package org.jahia.community.contentgovernance.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Anthropic Messages API ({@code POST {base}/v1/messages}). */
public final class AnthropicProvider implements LlmProvider {

    private static final String API_VERSION = "2023-06-01";

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.anthropic.com";
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public Completion complete(String system, String user, List<LlmImage> images, LlmSettings settings)
            throws IOException, InterruptedException {
        JSONObject body = new JSONObject()
                .put("model", settings.model())
                .put("max_tokens", settings.maxTokens())
                .put("system", system)
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", content(user, images))));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", settings.apiKey());
        headers.put("anthropic-version", API_VERSION);
        JSONObject response = HttpJson.post(settings.baseUrl() + "/v1/messages", headers, body);

        StringBuilder text = new StringBuilder();
        JSONArray content = response.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block != null && "text".equals(block.optString("type"))) {
                    text.append(block.optString("text"));
                }
            }
        }
        JSONObject usage = response.optJSONObject("usage");
        return new Completion(text.toString(),
                "max_tokens".equals(response.optString("stop_reason")),
                usage == null ? -1 : usage.optLong("input_tokens", -1),
                usage == null ? -1 : usage.optLong("output_tokens", -1));
    }

    /** The plain string when there is no image, else a text part then one label and one image part per picture. */
    private static Object content(String user, List<LlmImage> images) {
        if (images.isEmpty()) {
            return user;
        }
        JSONArray parts = new JSONArray().put(new JSONObject().put("type", "text").put("text", user));
        for (LlmImage image : images) {
            parts.put(new JSONObject().put("type", "text").put("text", "IMAGE #" + image.id() + ":"));
            parts.put(new JSONObject().put("type", "image").put("source", new JSONObject()
                    .put("type", "base64")
                    .put("media_type", image.mediaType())
                    .put("data", image.base64())));
        }
        return parts;
    }
}

package org.jahia.community.contentgovernance.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Chat Completions shape ({@code POST {base}/v1/chat/completions}, Bearer
 * token) that OpenAI defined and DeepSeek kept. Subclasses differ in the name
 * of the output-token field and in what they add to the body.
 */
public abstract class OpenAiCompatibleProvider implements LlmProvider {

    private static final String ROLE = "role";
    private static final String CONTENT = "content";

    /** OpenAI renamed {@code max_tokens}; DeepSeek did not. */
    protected abstract String maxTokensField();

    /** A hook for provider-specific body fields. Default: none. */
    protected void decorate(JSONObject body) {
        // nothing by default
    }

    /**
     * Fields that make the answer better on the models that know them and that some models or
     * gateways refuse: added on the first attempt, dropped for one retry when the API rejects the
     * request. Default: JSON mode when the call wants a JSON answer.
     */
    protected Map<String, Object> optionalFields(LlmSettings settings) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (settings.jsonAnswer()) {
            fields.put("response_format", new JSONObject().put("type", "json_object"));
        }
        return fields;
    }

    @Override
    public Completion complete(String system, String user, List<LlmImage> images, LlmSettings settings)
            throws IOException, InterruptedException {
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put(ROLE, "system").put(CONTENT, system))
                .put(new JSONObject().put(ROLE, "user").put(CONTENT, content(user, images)));
        JSONObject body = new JSONObject()
                .put("model", settings.model())
                .put(maxTokensField(), settings.maxTokens())
                .put("messages", messages);
        decorate(body);
        Map<String, Object> optional = optionalFields(settings);
        optional.forEach(body::put);

        String url = settings.baseUrl() + "/v1/chat/completions";
        Map<String, String> headers = Collections.singletonMap("Authorization", "Bearer " + settings.apiKey());
        JSONObject response;
        try {
            response = HttpJson.post(url, headers, body);
        } catch (IOException e) {
            if (optional.isEmpty() || !isRejectedRequest(e)) {
                throw e;
            }
            // The model or the gateway does not know one of the optional fields: ask again without them.
            optional.keySet().forEach(body::remove);
            response = HttpJson.post(url, headers, body);
        }

        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return new Completion("", false, -1, -1);
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.optJSONObject("message");
        String content = message == null ? "" : message.optString(CONTENT, "");
        if (content.trim().isEmpty() && message != null && message.has("reasoning_content")) {
            // The model spent the whole budget thinking and said nothing. Naming
            // the cause is what lets an administrator fix it from the config.
            throw new IOException(Completion.EMPTY_BUDGET);
        }
        boolean cut = "length".equals(choice.optString("finish_reason"));
        if (content.trim().isEmpty() && cut) {
            // OpenAI's reasoning models spend the budget on hidden reasoning without saying so.
            throw new IOException(Completion.EMPTY_BUDGET);
        }
        JSONObject usage = response.optJSONObject("usage");
        return new Completion(content,
                cut,
                usage == null ? -1 : usage.optLong("prompt_tokens", -1),
                usage == null ? -1 : usage.optLong("completion_tokens", -1));
    }

    /** True when the API refused the request itself (HTTP 400 or 422), not the transport or the key. */
    static boolean isRejectedRequest(IOException e) {
        String message = e.getMessage();
        return message != null && (message.contains("HTTP 400") || message.contains("HTTP 422"));
    }

    /** The plain string when there is no image or the provider reads none, else text and image_url parts. */
    private Object content(String user, List<LlmImage> images) {
        if (images.isEmpty() || !supportsImages()) {
            return user;
        }
        JSONArray parts = new JSONArray().put(new JSONObject().put("type", "text").put("text", user));
        for (LlmImage image : images) {
            parts.put(new JSONObject().put("type", "text").put("text", "IMAGE #" + image.id() + ":"));
            parts.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject()
                    .put("url", "data:" + image.mediaType() + ";base64," + image.base64())));
        }
        return parts;
    }
}

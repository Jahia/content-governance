package org.jahia.community.contentgovernance.ai;

import java.util.List;

/**
 * One call of a feature. The answer is one JSON object, which the instructions describe.
 *
 * @param feature         the feature that asks
 * @param siteKey         the key of the site the call works for, checked against {@code genaiAccess}
 * @param instructions    the system prompt
 * @param input           the user message
 * @param images          the pictures that follow the input, empty for most features
 * @param maxOutputTokens the output budget of the call
 */
public record AiRequest(AiFeature feature, String siteKey, String instructions, String input, List<AiImage> images,
                        int maxOutputTokens) {

    public AiRequest {
        images = List.copyOf(images);
    }

    /** A request with the same values and other instructions. */
    public AiRequest withInstructions(String newInstructions) {
        return new AiRequest(feature, siteKey, newInstructions, input, images, maxOutputTokens);
    }

    @Override
    public String toString() {
        // The prompt stays out: requests end up in log lines.
        return "AiRequest[feature=" + feature + ", site=" + siteKey + ", inputChars=" + input.length()
                + ", images=" + images.size() + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}

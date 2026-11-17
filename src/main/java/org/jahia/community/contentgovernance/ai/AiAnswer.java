package org.jahia.community.contentgovernance.ai;

/**
 * What the model answered.
 *
 * @param text         the answer, one JSON object
 * @param truncated    whether the answer stopped at its output budget
 * @param inputTokens  the input tokens the provider counted, or -1
 * @param outputTokens the output tokens the provider counted, or -1
 * @param costUsd      the price of the call in US dollars as the provider reported it, or null
 * @param provider     the provider that answered
 * @param model        the model that answered
 */
public record AiAnswer(String text, boolean truncated, long inputTokens, long outputTokens, Double costUsd,
                       String provider, String model) {

    @Override
    public String toString() {
        // The answer stays out: answers end up in log lines.
        return "AiAnswer[provider=" + provider + ", model=" + model + ", truncated=" + truncated + ", inputTokens="
                + inputTokens + ", outputTokens=" + outputTokens + ", costUsd=" + costUsd + ", textChars="
                + text.length() + "]";
    }
}

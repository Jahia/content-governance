package org.jahia.community.contentgovernance.ai;

/**
 * What a provider answered: the assistant text, whether it hit the output
 * budget, and the token counts the provider reported (or -1 when it did not).
 */
public final class Completion {

    /** Why a call returned nothing: the output budget was spent before any answer. */
    public static final String EMPTY_BUDGET = "the model used its whole output budget before answering (reasoning"
            + " models spend it on hidden reasoning); raise the output budget in the module's AI configuration";

    private final String text;
    private final boolean truncated;
    private final long inputTokens;
    private final long outputTokens;

    public Completion(String text, boolean truncated, long inputTokens, long outputTokens) {
        this.text = text == null ? "" : text;
        this.truncated = truncated;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String text() {
        return text;
    }

    public boolean truncated() {
        return truncated;
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }
}

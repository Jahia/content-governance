package org.jahia.community.contentgovernance.ai;

/**
 * The model behind the AI features. The bridge to genai-connector registers it, and only while
 * genai-connector runs: this interface names none of its types, so the module loads without it.
 * Callers reach it through {@link AiService}, which also answers when it is absent.
 */
public interface AiModel {

    /**
     * Whether genai-connector serves a model on the tier of a feature, and which one.
     *
     * @return {@link AiStatus.Availability#READY} or {@link AiStatus.Availability#NOT_CONFIGURED}
     */
    AiStatus status(AiFeature feature);

    /**
     * Sends one call and returns its answer.
     *
     * @throws AiException when the call gives no answer
     */
    AiAnswer complete(AiRequest request) throws AiException;
}

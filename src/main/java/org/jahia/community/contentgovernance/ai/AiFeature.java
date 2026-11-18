package org.jahia.community.contentgovernance.ai;

/** The features of the module that call a model. genai-connector serves each one on a tier. */
public enum AiFeature {
    /** The review of the AI tab of Page quality. */
    REVIEW,
    /** The SEO assist: titles, meta descriptions, keywords, headings, call-to-action labels. */
    SEO,
    /** The plain-language assist of the Readability tab. */
    SIMPLIFY,
    /** The alternative text assist of the Accessibility tab, which can send pictures. */
    ALT_TEXT,
    /** The written report of the GEO dashboard. */
    GEO_REPORT
}

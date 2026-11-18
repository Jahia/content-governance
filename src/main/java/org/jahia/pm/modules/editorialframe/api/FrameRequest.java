/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;



/**
 * What a consumer asks for when it builds the frame text of a model call. The persona is null for
 * the default persona. The budget counts four characters a token. The text, when given, is the text
 * the model works on: the rules whose pattern appears in it come first when the budget is short.
 */
public final class FrameRequest {

    private final String siteKey;
    private final String language;
    private final String persona;
    private final int maxTokens;
    private final String text;

    public FrameRequest(String siteKey, String language, String persona, int maxTokens, String text) {
        this.siteKey = siteKey;
        this.language = language;
        this.persona = persona;
        this.maxTokens = maxTokens;
        this.text = text;
    }

    public String siteKey() {
        return siteKey;
    }

    public String language() {
        return language;
    }

    public String persona() {
        return persona;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public String text() {
        return text;
    }

    public static final int DEFAULT_MAX_TOKENS = 1500;

    public static FrameRequest of(String siteKey, String language, String persona) {
        return new FrameRequest(siteKey, language, persona, DEFAULT_MAX_TOKENS, null);
    }

    @Override
    public String toString() {
        return "FrameRequest[" + "siteKey=" + siteKey + ", " + "language=" + language + ", " + "persona=" + persona + ", " + "maxTokens=" + maxTokens + ", " + "text=" + text + "]";
    }
}

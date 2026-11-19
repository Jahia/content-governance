/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;



/**
 * What a consumer asks for when it builds the frame text of a model call. The persona is null for
 * the default persona. The budget counts four characters a token. The text, when given, is the text
 * the model works on: the rules whose pattern appears in it come first when the budget is short.
 */
public record FrameRequest(String siteKey, String language, String persona, int maxTokens, String text) {

    public static final int DEFAULT_MAX_TOKENS = 1500;

    public static FrameRequest of(String siteKey, String language, String persona) {
        return new FrameRequest(siteKey, language, persona, DEFAULT_MAX_TOKENS, null);
    }
}

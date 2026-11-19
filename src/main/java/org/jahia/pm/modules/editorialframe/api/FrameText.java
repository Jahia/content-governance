/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;


import java.util.List;


/**
 * A voice of the brand. The scope is group for a voice of systemsite, site otherwise. The
 * essentials are the short text that every model call gets, as the brand manager wrote them, or
 * {@code null}.
 *
 * @param essentials the essentials as the brand manager wrote them, or {@code null}
 */
public record FrameText(String name, String title, String scope, String text, List<String> authorVoiceUnder,
                        String essentials) {

    public FrameText {
        authorVoiceUnder = authorVoiceUnder == null ? List.of() : List.copyOf(authorVoiceUnder);
    }

    public FrameText(String name, String title, String scope, String text, List<String> authorVoiceUnder) {
        this(name, title, scope, text, authorVoiceUnder, null);
    }
}

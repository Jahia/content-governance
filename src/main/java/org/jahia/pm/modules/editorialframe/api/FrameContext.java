/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;


import java.util.List;


/** The frame text of a model call: empty when the site has no frame. */
public record FrameContext(String text, String fingerprint, String persona, List<String> personas, int estimatedTokens,
                           boolean truncated) {

    public FrameContext {
        personas = personas == null ? List.of() : List.copyOf(personas);
    }

    public static FrameContext empty() {
        return new FrameContext("", null, null, List.of(), 0, false);
    }

    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }
}

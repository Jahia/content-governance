/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;


import java.util.List;


/** The frame text of a model call: empty when the site has no frame. */
public final class FrameContext {

    private final String text;
    private final String fingerprint;
    private final String persona;
    private final List<String> personas;
    private final int estimatedTokens;
    private final boolean truncated;

    public FrameContext(String text, String fingerprint, String persona, List<String> personas, int estimatedTokens, boolean truncated) {
        this.text = text;
        this.fingerprint = fingerprint;
        this.persona = persona;
        this.personas = personas == null ? List.of() : List.copyOf(personas);
        this.estimatedTokens = estimatedTokens;
        this.truncated = truncated;
    }

    public String text() {
        return text;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String persona() {
        return persona;
    }

    public List<String> personas() {
        return personas;
    }

    public int estimatedTokens() {
        return estimatedTokens;
    }

    public boolean truncated() {
        return truncated;
    }

    public static FrameContext empty() {
        return new FrameContext("", null, null, List.of(), 0, false);
    }

    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }

    @Override
    public String toString() {
        return "FrameContext[" + "text=" + text + ", " + "fingerprint=" + fingerprint + ", " + "persona=" + persona + ", " + "personas=" + personas + ", " + "estimatedTokens=" + estimatedTokens + ", " + "truncated=" + truncated + "]";
    }
}

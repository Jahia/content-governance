/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;


import java.util.List;


/**
 * A voice of the brand. The scope is group for a voice of systemsite, site otherwise. The
 * essentials are the short text that every model call gets, as the brand manager wrote them, or
 * {@code null}.
 */
public final class FrameText {

    private final String name;
    private final String title;
    private final String scope;
    private final String text;
    private final List<String> authorVoiceUnder;
    private final String essentials;

    public FrameText(String name, String title, String scope, String text, List<String> authorVoiceUnder) {
        this(name, title, scope, text, authorVoiceUnder, null);
    }

    public FrameText(String name, String title, String scope, String text, List<String> authorVoiceUnder,
                     String essentials) {
        this.name = name;
        this.title = title;
        this.scope = scope;
        this.text = text;
        this.authorVoiceUnder = authorVoiceUnder == null ? List.of() : List.copyOf(authorVoiceUnder);
        this.essentials = essentials;
    }

    /** The essentials as the brand manager wrote them, or {@code null}. */
    public String essentials() {
        return essentials;
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    public String scope() {
        return scope;
    }

    public String text() {
        return text;
    }

    public List<String> authorVoiceUnder() {
        return authorVoiceUnder;
    }

    @Override
    public String toString() {
        return "FrameText[" + "name=" + name + ", " + "title=" + title + ", " + "scope=" + scope + ", " + "text=" + text + ", " + "authorVoiceUnder=" + authorVoiceUnder + ", " + "essentials=" + essentials + "]";
    }
}

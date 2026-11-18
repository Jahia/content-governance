/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;



/**
 * A reader the brand writes for. The name is the node name, which a request uses to choose it. The
 * essentials are the short text that every model call gets, as the brand manager wrote them, or
 * {@code null}.
 */
public final class Persona {

    private final String name;
    private final String title;
    private final String text;
    private final boolean isDefault;
    private final String scope;
    private final String essentials;

    public Persona(String name, String title, String text, boolean isDefault, String scope) {
        this(name, title, text, isDefault, scope, null);
    }

    public Persona(String name, String title, String text, boolean isDefault, String scope, String essentials) {
        this.name = name;
        this.title = title;
        this.text = text;
        this.isDefault = isDefault;
        this.scope = scope;
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

    public String text() {
        return text;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String scope() {
        return scope;
    }

    @Override
    public String toString() {
        return "Persona[" + "name=" + name + ", " + "title=" + title + ", " + "text=" + text + ", " + "isDefault=" + isDefault + ", " + "scope=" + scope + ", " + "essentials=" + essentials + "]";
    }
}

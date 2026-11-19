/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;



/**
 * A reader the brand writes for. The name is the node name, which a request uses to choose it. The
 * essentials are the short text that every model call gets, as the brand manager wrote them, or
 * {@code null}.
 *
 * @param essentials the essentials as the brand manager wrote them, or {@code null}
 */
public record Persona(String name, String title, String text, boolean isDefault, String scope, String essentials) {

    public Persona(String name, String title, String text, boolean isDefault, String scope) {
        this(name, title, text, isDefault, scope, null);
    }
}

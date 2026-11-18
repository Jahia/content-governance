/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

import java.util.Locale;

/** What a rule needs to read: one content item, or the whole page. */
public enum Scope {

    /** The rule reads an element and its content, so it runs on a content item and on a page. */
    ITEM,

    /** The rule needs the whole page, such as its language or its one main heading, so it runs on a page only. */
    PAGE;

    /** The id that a report and a tool give, in lower case. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}

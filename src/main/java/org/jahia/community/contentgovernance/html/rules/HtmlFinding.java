/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

/**
 * One defect in the HTML.
 *
 * @param rule    the rule that the HTML breaks
 * @param element the opening tag of the element, without its content, cut at 160 characters
 * @param locator a CSS selector of the element in the HTML, or null when the finding has no element
 */
public record HtmlFinding(HtmlRule rule, String element, String locator) {

    /** A finding with no locator. */
    public HtmlFinding(HtmlRule rule, String element) {
        this(rule, element, null);
    }
}

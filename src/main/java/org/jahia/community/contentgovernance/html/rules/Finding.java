/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

/**
 * One defect in the HTML.
 *
 * @param rule    the rule that the HTML breaks
 * @param element the opening tag of the element, without its content, cut at 160 characters
 */
public record Finding(HtmlRule rule, String element) {
}

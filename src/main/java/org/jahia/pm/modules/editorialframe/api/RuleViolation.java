/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;



/** A rule that a text breaks. The match is null for a missing mandatory pattern. */
public record RuleViolation(String ruleId, WritingRule.Kind kind, WritingRule.Severity severity, String pattern,
                            String match, String replacement, String justification, String excerpt) {
}

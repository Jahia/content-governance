/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

/**
 * A rule that a text breaks. The match is null for a missing mandatory pattern.
 *
 * @param folder the path of the folder that holds the rule, such as
 *               {@code /sites/digitall/contents/compliance-glossaries/legal}, or null
 */
public record RuleViolation(String ruleId, WritingRule.Kind kind, WritingRule.Severity severity, String pattern,
                            String match, String replacement, String justification, String excerpt, String folder) {

    /** A violation of a rule in no folder. */
    public RuleViolation(String ruleId, WritingRule.Kind kind, WritingRule.Severity severity, String pattern,
                         String match, String replacement, String justification, String excerpt) {
        this(ruleId, kind, severity, pattern, match, replacement, justification, excerpt, null);
    }
}

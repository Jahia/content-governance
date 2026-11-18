/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;



/** A rule that a text breaks. The match is null for a missing mandatory pattern. */
public final class RuleViolation {

    private final String ruleId;
    private final WritingRule.Kind kind;
    private final WritingRule.Severity severity;
    private final String pattern;
    private final String match;
    private final String replacement;
    private final String justification;
    private final String excerpt;

    public RuleViolation(String ruleId, WritingRule.Kind kind, WritingRule.Severity severity, String pattern, String match, String replacement, String justification, String excerpt) {
        this.ruleId = ruleId;
        this.kind = kind;
        this.severity = severity;
        this.pattern = pattern;
        this.match = match;
        this.replacement = replacement;
        this.justification = justification;
        this.excerpt = excerpt;
    }

    public String ruleId() {
        return ruleId;
    }

    public WritingRule.Kind kind() {
        return kind;
    }

    public WritingRule.Severity severity() {
        return severity;
    }

    public String pattern() {
        return pattern;
    }

    public String match() {
        return match;
    }

    public String replacement() {
        return replacement;
    }

    public String justification() {
        return justification;
    }

    public String excerpt() {
        return excerpt;
    }

    @Override
    public String toString() {
        return "RuleViolation[" + "ruleId=" + ruleId + ", " + "kind=" + kind + ", " + "severity=" + severity + ", " + "pattern=" + pattern + ", " + "match=" + match + ", " + "replacement=" + replacement + ", " + "justification=" + justification + ", " + "excerpt=" + excerpt + "]";
    }
}

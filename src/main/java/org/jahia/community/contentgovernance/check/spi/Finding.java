package org.jahia.community.contentgovernance.check.spi;

/**
 * One place where a content breaks a rule.
 *
 * @param ruleId   the id of the rule
 * @param severity the severity of the rule as the site applies it: critical, serious, moderate or minor
 * @param text     the text that breaks the rule, such as a matched term, an HTML tag or a measured value
 * @param context  the text around the match, or null
 */
public record Finding(String ruleId, String severity, String text, String context) {

    public Finding {
        text = text == null ? "" : text;
    }

    /**
     * @param rule the rule that the text breaks
     * @param text the text that breaks the rule
     * @return a finding with the id and the severity of the rule, and no context
     */
    public static Finding of(Rule rule, String text) {
        return new Finding(rule.id(), rule.severity(), text, null);
    }

    /**
     * @param rule    the rule that the text breaks
     * @param text    the text that breaks the rule
     * @param context the text around the match
     * @return a finding with the id and the severity of the rule
     */
    public static Finding of(Rule rule, String text, String context) {
        return new Finding(rule.id(), rule.severity(), text, context);
    }
}

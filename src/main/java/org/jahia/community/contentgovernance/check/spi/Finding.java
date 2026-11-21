package org.jahia.community.contentgovernance.check.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place where a content breaks a rule.
 *
 * @param ruleId   the id of the rule
 * @param severity the severity of the rule as the site applies it: critical, serious, moderate or minor
 * @param text     the text that breaks the rule, such as a matched term, an HTML tag or a measured value
 * @param context  the text around the match, or null
 * @param locator  a CSS selector of the element that breaks the rule in the rendered page, such as
 *                 {@code main > p:nth-child(3) > img}, or null when the finding has no element. The drawer
 *                 marks the element with it, and says "Item not found" when the page has no such element.
 * @param values   what the check measured at this place, by name, such as the contrast ratio of a text,
 *                 empty when it measured nothing. The values of the whole rule are on {@link CheckResult}.
 */
public record Finding(String ruleId, String severity, String text, String context, String locator,
                      Map<String, Object> values) {

    public Finding {
        text = text == null ? "" : text;
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** A finding with no value. */
    public Finding(String ruleId, String severity, String text, String context, String locator) {
        this(ruleId, severity, text, context, locator, Map.of());
    }

    /** A finding with no element to mark. */
    public Finding(String ruleId, String severity, String text, String context) {
        this(ruleId, severity, text, context, null);
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

    /**
     * @param rule    the rule that the text breaks
     * @param text    the text that breaks the rule
     * @param context the text around the match, or null
     * @param locator a CSS selector of the element in the rendered page, or null
     * @return a finding with the id and the severity of the rule
     */
    public static Finding at(Rule rule, String text, String context, String locator) {
        return new Finding(rule.id(), rule.severity(), text, context, locator);
    }
}

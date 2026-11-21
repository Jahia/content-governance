package org.jahia.community.contentgovernance.check.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one rule gave on one input: whether the input keeps the rule, the values that the check measured,
 * and the places where the input breaks the rule.
 *
 * <p>Example: the rule {@code seo.title} on a page whose title has 92 characters gives
 * {@code CheckResult("seo.title", false, {length: 92, text: "Annual report…"}, [finding])}. A measure
 * rule, such as {@code readability.statistics}, has no limit: it always passes, and carries values.</p>
 *
 * @param ruleId   the id of the rule
 * @param passed   true when the input keeps the rule
 * @param values   what the check measured, by name, such as {@code length}: a {@link String}, a
 *                 {@link Number}, a {@link Boolean}, a {@link List} or a {@link Map} of these, or null.
 *                 The drawer reads them as JSON.
 * @param findings the places where the input breaks the rule, empty when it passes
 */
public record CheckResult(String ruleId, boolean passed, Map<String, Object> values, List<Finding> findings) {

    public CheckResult {
        // Map.copyOf refuses a null value, which a value that the page lacks is
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * @param rule     the rule that the check applied
     * @param values   what the check measured
     * @param findings where the input breaks the rule
     * @return a result that passes when there is no finding
     */
    public static CheckResult of(Rule rule, Map<String, Object> values, List<Finding> findings) {
        return new CheckResult(rule.id(), findings == null || findings.isEmpty(), values, findings);
    }
}

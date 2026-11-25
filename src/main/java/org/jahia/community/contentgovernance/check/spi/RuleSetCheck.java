package org.jahia.community.contentgovernance.check.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A check whose rules act on each other on one input, so it applies them together. Example: the check
 * {@code pattern}, where the term "Sackville-Baggins" of one rule hides the term "Baggins" of another rule,
 * and where a glossary rule of another language reports a form of its term.
 *
 * <p>Content Governance calls {@link #results(CheckInput, List)} once per input, with every rule of the check
 * that runs on the input: the registry does not call {@link #result} on this check. By default each of these
 * rules gets the findings of {@link #check(CheckInput, List)} that name it, and a result with no value, which
 * passes when the rule has no finding. A check that throws a runtime exception gives no result for any of
 * these rules, and the other checks still run.</p>
 */
public interface RuleSetCheck extends ContentCheck {

    /**
     * Applies the rules to one input.
     *
     * @param input what to check
     * @param rules the rules that the site keeps on and whose evaluator is the id of this check, in the order
     *              of the rules of the site
     * @return the findings, each with the id of its rule; Content Governance keeps the first 50 findings of
     * each rule, in the order of this list
     */
    List<Finding> check(CheckInput input, List<? extends Rule> rules);

    /**
     * Applies the rules to one input, and gives each rule its result. A check overrides it to give values, such
     * as a score that the view {@code score} of a rule shows.
     *
     * @param input what to check
     * @param rules the rules, as {@link #check(CheckInput, List)} gets them
     * @return one result per rule; Content Governance drops a result whose rule is not in {@code rules}, and
     * gives a rule with no result a result with no value and no finding
     * @since 1.2.0 of the package
     */
    default List<CheckResult> results(CheckInput input, List<? extends Rule> rules) {
        Map<String, List<Finding>> found = new LinkedHashMap<>();
        rules.forEach(rule -> found.put(rule.id(), new ArrayList<>()));
        for (Finding finding : check(input, rules)) {
            List<Finding> ofRule = found.get(finding.ruleId());
            if (ofRule != null) {
                ofRule.add(finding);
            }
        }
        return rules.stream().map(rule -> CheckResult.of(rule, Map.of(), found.get(rule.id()))).toList();
    }

    /** Applies one rule, as a set of one rule. */
    @Override
    default List<Finding> check(CheckInput input, Rule rule) {
        return check(input, List.of(rule));
    }
}

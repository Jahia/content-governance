package org.jahia.community.contentgovernance.check.spi;

import java.util.List;

/**
 * A check whose rules act on each other on one input, so it applies them together. Example: the check
 * {@code pattern}, where the term "Sackville-Baggins" of one rule hides the term "Baggins" of another rule,
 * and where a glossary rule of another language reports a form of its term.
 *
 * <p>Content Governance calls {@link #check(CheckInput, List)} once per input, with every rule of the check
 * that runs on the input, and gives each finding to the rule that it names. A check that throws a runtime
 * exception gives no finding for any of these rules, and the other checks still run.</p>
 */
public interface RuleSetCheck extends ContentCheck {

    /**
     * Applies the rules to one input.
     *
     * @param input what to check
     * @param rules the rules that the site keeps on and whose evaluator is the id of this check, in the order
     *              of the rules of the site
     * @return the findings, each with the id of its rule, in the order of the rules
     */
    List<Finding> check(CheckInput input, List<? extends Rule> rules);

    /** Applies one rule, as a set of one rule. */
    @Override
    default List<Finding> check(CheckInput input, Rule rule) {
        return check(input, List.of(rule));
    }
}

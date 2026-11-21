package org.jahia.community.contentgovernance.check.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A check that applies each rule whose evaluator is the id of the check. A module registers a check as
 * an OSGi service with the property {@value #ID}:
 *
 * <pre>
 * &#64;Component(service = ContentCheck.class, property = "check.id=acme.erpPrice")
 * public class ErpPriceCheck implements ContentCheck {
 *     public Set&lt;Scope&gt; scopes() { return Set.of(Scope.VALUE); }
 *     public List&lt;Finding&gt; check(CheckInput input, Rule rule) { ... }
 * }
 * </pre>
 *
 * <p>A rule names its check with the property {@code evaluator} of its node. A rule whose check no
 * started module registers is inactive, and Content Governance names the missing check.</p>
 *
 * <p>Content Governance calls {@link #result}, which gives the result of the rule with its findings. A
 * check that measures values, such as the length of a title, overrides it to put them in the result.</p>
 *
 * <p>Thread safety: Content Governance calls one check from several threads at once.</p>
 */
public interface ContentCheck {

    /** The service property that holds the id of the check, which the evaluator of a rule names. */
    String ID = "check.id";

    /** Where a check runs. */
    enum Scope {
        /** One value that an editor writes, such as the text of a property. */
        VALUE,
        /** The HTML of a rendered page or of a rendered content item. */
        PAGE,
        /** A whole site, in a scheduled job. */
        SITE
    }

    /**
     * @return the scopes in which the check runs
     */
    Set<Scope> scopes();

    /**
     * Applies one rule to one input. A check that throws a runtime exception gives no finding, and the
     * other checks still run.
     *
     * @param input what to check
     * @param rule  a rule that the site keeps on, and whose evaluator is the id of this check
     * @return the findings, or an empty list when the input keeps the rule
     */
    List<Finding> check(CheckInput input, Rule rule);

    /**
     * Applies one rule to one input, and gives its result: passed when {@link #check} finds nothing, with
     * no value. A check that throws a runtime exception gives no result, and the other checks still run.
     *
     * <p>The registry calls this method, and never {@link #check}, except on a {@link RuleSetCheck}: when a
     * check overrides it, the findings of this result are the ones that every caller of the registry gets.
     * The registry fails a result that has a finding, and drops a result whose values JSON cannot write.</p>
     *
     * @param input what to check
     * @param rule  a rule that the site keeps on, and whose evaluator is the id of this check
     * @return the result of the rule on the input
     */
    default CheckResult result(CheckInput input, Rule rule) {
        return CheckResult.of(rule, Map.of(), check(input, rule));
    }
}

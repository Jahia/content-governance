package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.osgi.service.component.annotations.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The evaluator {@code threshold}: the value that the caller measured for the rule, the fact whose key is
 * the id of the rule, is inside the minimum and the maximum of the rule. An input with no such number gives
 * no finding.
 *
 * <p>The values of the result are the ones that the caller measured for the rule
 * ({@link CheckInput#values}), such as {@code {length: 92, text: "Annual report…"}} for {@code seo.title},
 * else {@code value}, the measured number.</p>
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=" + ContentRule.THRESHOLD)
public class ThresholdCheck implements ContentCheck {

    /** The value of a result whose caller measured the number only. */
    static final String VALUE = "value";

    @Override
    public Set<Scope> scopes() {
        return EnumSet.allOf(Scope.class);
    }

    @Override
    public List<Finding> check(CheckInput input, Rule rule) {
        if (!(input.fact(rule.id()) instanceof Number measured)) {
            return List.of();
        }
        double value = measured.doubleValue();
        boolean passes = (rule.minimum() == null || value >= rule.minimum())
                && (rule.maximum() == null || value <= rule.maximum());
        return passes ? List.of() : List.of(Finding.of(rule, text(value)));
    }

    @Override
    public CheckResult result(CheckInput input, Rule rule) {
        Map<String, Object> values = input.values(rule.id());
        if (values.isEmpty() && input.fact(rule.id()) instanceof Number measured) {
            values = Map.of(VALUE, measured);
        }
        return CheckResult.of(rule, values, check(input, rule));
    }

    /** 72.0 gives 72, and 0.5 gives 0.5. */
    static String text(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

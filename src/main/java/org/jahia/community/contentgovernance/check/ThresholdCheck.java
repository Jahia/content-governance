package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.osgi.service.component.annotations.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The evaluator {@code threshold}: the value that the caller measured for the rule, the fact whose key is
 * the id of the rule, is inside the minimum and the maximum of the rule. An input with no such number gives
 * no finding.
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=" + ContentRule.THRESHOLD)
public class ThresholdCheck implements ContentCheck {

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

    /** 72.0 gives 72, and 0.5 gives 0.5. */
    static String text(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

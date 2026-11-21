package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.osgi.service.component.annotations.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The evaluator {@code presence}: what the rule names is present. The caller states what it found as the
 * fact whose key is the id of the rule. The value {@code false}, the number 0, a blank text and an empty
 * collection give a finding. An input with no such fact gives none. The values of the result are the ones
 * that the caller measured for the rule ({@link CheckInput#values}).
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=" + ContentRule.PRESENCE)
public class PresenceCheck implements ContentCheck {

    @Override
    public Set<Scope> scopes() {
        return EnumSet.allOf(Scope.class);
    }

    @Override
    public List<Finding> check(CheckInput input, Rule rule) {
        Object found = input.fact(rule.id());
        return found != null && absent(found) ? List.of(Finding.of(rule, String.valueOf(found))) : List.of();
    }

    @Override
    public CheckResult result(CheckInput input, Rule rule) {
        return CheckResult.of(rule, input.values(rule.id()), check(input, rule));
    }

    private static boolean absent(Object found) {
        if (found instanceof Boolean present) {
            return !present;
        }
        if (found instanceof Number count) {
            return count.doubleValue() <= 0;
        }
        if (found instanceof Collection<?> items) {
            return items.isEmpty();
        }
        return found.toString().isBlank();
    }
}

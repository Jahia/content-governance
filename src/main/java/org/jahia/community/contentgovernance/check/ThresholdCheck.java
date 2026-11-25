package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.osgi.service.component.annotations.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 *
 * <p>A caller may also measure each place of the content, such as each sentence of a text, and give the
 * list of {@link Place} as the fact {@link #placesOf}. A rule that fails then gives one finding per place
 * outside the limits, in the order of the list, else one finding for the whole value.</p>
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=" + ContentRule.THRESHOLD)
public class ThresholdCheck implements ContentCheck {

    /** The value of a result whose caller measured the number only. */
    static final String VALUE = "value";

    /**
     * One place of the content with its own measured value.
     *
     * @param value   the value measured at this place, such as the words of a sentence
     * @param context the text of the place, or null
     * @param locator a CSS selector of the element of the place in the rendered page, or null
     * @param values  the values of the finding at this place, such as {@code {sentence: "…", words: 31}}
     */
    public record Place(Number value, String context, String locator, Map<String, Object> values) {
    }

    /**
     * @param ruleId the id of the rule, such as {@code readability.longSentences}
     * @return the key of the fact that holds the list of the places of the rule
     */
    public static String placesOf(String ruleId) {
        return ruleId + "#places";
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.allOf(Scope.class);
    }

    @Override
    public List<Finding> check(CheckInput input, Rule rule) {
        if (!(input.fact(rule.id()) instanceof Number measured)) {
            return List.of();
        }
        if (passes(measured, rule)) {
            return List.of();
        }
        List<Finding> places = new ArrayList<>();
        if (input.fact(placesOf(rule.id())) instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Place place && !passes(place.value(), rule)) {
                    places.add(new Finding(rule.id(), rule.severity(), text(place.value().doubleValue()),
                            place.context(), place.locator(), place.values()));
                }
            }
        }
        return places.isEmpty() ? List.of(Finding.of(rule, text(measured.doubleValue()))) : places;
    }

    private static boolean passes(Number measured, Rule rule) {
        double value = measured.doubleValue();
        return (rule.minimum() == null || value >= rule.minimum())
                && (rule.maximum() == null || value <= rule.maximum());
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

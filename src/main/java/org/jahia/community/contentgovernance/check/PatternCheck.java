package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.osgi.service.component.annotations.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The evaluator {@code pattern}: the writing rule of the rule node, which {@link RuleChecker} checks on a
 * value with no model. A rule of the kind {@code mandatory} needs a whole document, so it gives no finding
 * on one value.
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=" + ContentRule.PATTERN)
public class PatternCheck implements ContentCheck {

    @Override
    public Set<Scope> scopes() {
        return Set.of(Scope.VALUE);
    }

    @Override
    public List<Finding> check(CheckInput input, Rule rule) {
        WritingRule writing = writingRule(rule);
        if (writing == null || input.text() == null || !writing.inForceOn(LocalDate.now())) {
            return List.of();
        }
        return RuleChecker.check(List.of(writing), input.language(), input.text(), false).stream()
                .map(violation -> finding(rule, violation))
                .toList();
    }

    private static Finding finding(Rule rule, RuleViolation violation) {
        return Finding.of(rule, violation.match() == null ? violation.pattern() : violation.match(),
                violation.excerpt());
    }

    /** The writing rule of the values of a rule, or null when the rule has no pattern or an unknown kind. */
    public static WritingRule writingRule(Rule rule) {
        String pattern = string(rule, RuleValues.PATTERN);
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        WritingRule.Kind kind;
        WritingRule.MatchMode matchMode;
        try {
            kind = WritingRule.Kind.parse(string(rule, RuleValues.KIND));
            matchMode = WritingRule.MatchMode.parse(string(rule, RuleValues.MATCH_MODE));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (kind == WritingRule.Kind.GLOSSARY) {
            return null;
        }
        return new WritingRule(rule.id(), kind, pattern, matchMode, string(rule, RuleValues.REPLACEMENT),
                string(rule, RuleValues.RULE_LANGUAGE),
                rule.blocking() ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                rule.advice(), null, List.of(), List.of(), string(rule, RuleValues.OWNER), null,
                day(rule, RuleValues.VALID_FROM), day(rule, RuleValues.VALID_UNTIL),
                strings(rule, RuleValues.PATTERN_FORMS), strings(rule, RuleValues.REPLACEMENT_FORMS),
                string(rule, RuleValues.PATTERN_LANGUAGE), rule instanceof ContentRule content ? content.folder() : null);
    }

    private static List<String> strings(Rule rule, String property) {
        return rule.value(property) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String string(Rule rule, String property) {
        Object value = rule.value(property);
        return value == null ? null : value.toString();
    }

    private static LocalDate day(Rule rule, String property) {
        return rule.value(property) instanceof LocalDate day ? day : null;
    }
}

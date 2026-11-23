package org.jahia.community.contentgovernance.rules;

import java.util.Arrays;
import java.util.List;

/**
 * The kind of check of a rule, which its property {@value RuleValues#CHECK_TYPE} names. The fields of each kind
 * are on a mixin of {@code jmix:dynamicFieldset}, so jContent shows only the fields of the kind that the editor
 * picks. Example: a rule of the kind {@link #CODE}, such as {@code seo.title}, shows its unit and its limits, and
 * no pattern.
 */
public enum CheckType {

    /** A writing rule: the evaluator {@value ContentRule#PATTERN} reports each match of its pattern, with no model. */
    PATTERN("ccsmix:patternCheck", List.of(RuleValues.KIND, RuleValues.PATTERN, RuleValues.MATCH_MODE,
            RuleValues.REPLACEMENT, RuleValues.RULE_LANGUAGE, RuleValues.PATTERN_FORMS, RuleValues.REPLACEMENT_FORMS,
            RuleValues.PATTERN_LANGUAGE, RuleValues.WRONG_EXAMPLES, RuleValues.RIGHT_EXAMPLES, RuleValues.OWNER,
            RuleValues.DECIDED_ON, RuleValues.VALID_FROM, RuleValues.VALID_UNTIL)),
    /** A model scores the text against the advice of the rule, its criterion, and a score of minimum or more passes. */
    AI("ccsmix:aiCheck", List.of(RuleValues.MINIMUM)),
    /** Code measures or finds something, such as the length of a title, and compares a measure with the limits. */
    CODE("ccsmix:codeCheck", List.of(RuleValues.UNIT, RuleValues.MINIMUM, RuleValues.MAXIMUM));

    private final String mixin;
    private final List<String> keys;

    CheckType(String mixin, List<String> keys) {
        this.mixin = mixin;
        this.keys = keys;
    }

    /** The mixin that holds the fields of the kind. */
    public String mixin() {
        return mixin;
    }

    /** The keys of {@link RuleValues} that only this kind holds, on its mixin. */
    public List<String> keys() {
        return keys;
    }

    /**
     * @param value the value of {@value RuleValues#CHECK_TYPE}, such as {@code CODE}
     * @return its kind, or null for no value or an unknown one
     */
    public static CheckType of(Object value) {
        return value == null ? null
                : Arrays.stream(values()).filter(type -> type.name().equals(value.toString())).findFirst().orElse(null);
    }

    /**
     * The kind of a rule that names none, from its evaluator: {@link #PATTERN} for {@value ContentRule#PATTERN} or
     * no evaluator, {@link #AI} for {@value ContentRule#MODEL}, and {@link #CODE} for any other check.
     */
    static CheckType ofEvaluator(String evaluator) {
        if (evaluator == null || evaluator.isBlank() || ContentRule.PATTERN.equals(evaluator)) {
            return PATTERN;
        }
        return ContentRule.MODEL.equals(evaluator) ? AI : CODE;
    }

    /**
     * True when a rule of a kind can hold a key. A key of no kind, such as {@code severity}, is on every rule.
     *
     * @param type the kind of the rule, or null for a rule that names none
     */
    static boolean holds(CheckType type, String key) {
        boolean typed = Arrays.stream(values()).anyMatch(any -> any.keys.contains(key));
        return !typed || type != null && type.keys.contains(key);
    }
}

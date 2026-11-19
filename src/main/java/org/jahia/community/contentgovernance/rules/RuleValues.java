package org.jahia.community.contentgovernance.rules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The values of a rule, keyed by the name of their property on a {@value ContentRules#TYPE} node. A rule
 * file, a rule node and the seeder all read and write the same map.
 *
 * <p>A value is a {@link String}, a {@link Boolean}, a {@link Double}, a {@link List} of strings or a
 * {@link LocalDate}. An absent key has no value.</p>
 */
public final class RuleValues {

    public static final String RULE_ID = "ruleId";
    public static final String EVALUATOR = "evaluator";
    public static final String DOMAIN = "domain";
    public static final String ENABLED = "enabled";
    public static final String SEVERITY = "severity";
    public static final String BLOCKING = "blocking";
    public static final String FIXER = "fixer";
    /** Internationalized on the node: the rules read and write its English value. */
    public static final String ADVICE = "advice";
    public static final String UNIT = "unit";
    public static final String MINIMUM = "minimum";
    public static final String MAXIMUM = "maximum";
    public static final String KIND = "kind";
    public static final String PATTERN = "pattern";
    public static final String MATCH_MODE = "matchMode";
    public static final String REPLACEMENT = "replacement";
    public static final String RULE_LANGUAGE = "ruleLanguage";
    public static final String WRONG_EXAMPLES = "wrongExamples";
    public static final String RIGHT_EXAMPLES = "rightExamples";
    public static final String OWNER = "owner";
    public static final String DECIDED_ON = "decidedOn";
    public static final String VALID_FROM = "validFrom";
    public static final String VALID_UNTIL = "validUntil";

    /** Every property that a rule file sets, in the order of the digest. */
    public static final List<String> KEYS = List.of(RULE_ID, EVALUATOR, DOMAIN, ENABLED, SEVERITY, BLOCKING, FIXER,
            ADVICE, UNIT, MINIMUM, MAXIMUM, KIND, PATTERN, MATCH_MODE, REPLACEMENT, RULE_LANGUAGE, WRONG_EXAMPLES,
            RIGHT_EXAMPLES, OWNER, DECIDED_ON, VALID_FROM, VALID_UNTIL);
    public static final List<String> BOOLEANS = List.of(ENABLED, BLOCKING);
    public static final List<String> NUMBERS = List.of(MINIMUM, MAXIMUM);
    public static final List<String> LISTS = List.of(WRONG_EXAMPLES, RIGHT_EXAMPLES);
    public static final List<String> DAYS = List.of(DECIDED_ON, VALID_FROM, VALID_UNTIL);

    private static final String DEFAULT_FIXER = "editor";

    private RuleValues() {
    }

    /** The domain of a rule id: the part before the first dot, or the whole id. */
    public static String domainOf(String id) {
        int dot = id.indexOf('.');
        return dot > 0 ? id.substring(0, dot) : id;
    }

    /**
     * The values with the defaults of the node type: on, not blocking, fixed by an editor, and the domain
     * of the id. A node that holds a default and a file that leaves it out have the same values.
     */
    static Map<String, Object> withDefaults(Map<String, Object> values) {
        Map<String, Object> out = new HashMap<>(values);
        out.putIfAbsent(ENABLED, Boolean.TRUE);
        out.putIfAbsent(BLOCKING, Boolean.FALSE);
        out.putIfAbsent(FIXER, DEFAULT_FIXER);
        Object id = out.get(RULE_ID);
        if (id != null) {
            out.putIfAbsent(DOMAIN, domainOf(id.toString()));
        }
        return out;
    }

    /**
     * A fingerprint of the values: two maps with the same values, defaults included, have the same one.
     * The seeder keeps the fingerprint of what it wrote, and so tells a rule that a site changed.
     */
    public static String digest(Map<String, Object> values) {
        Map<String, Object> all = withDefaults(values);
        StringBuilder canonical = new StringBuilder();
        for (String key : KEYS) {
            Object value = all.get(key);
            if (value != null) {
                canonical.append(key).append('=').append(text(value)).append('\n');
            }
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    /** 30 and 30.0 give the same text, and the items of a list are separated by a unit separator. */
    private static String text(Object value) {
        if (value instanceof Double number) {
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder();
            for (Object item : list) {
                out.append(item).append('\u001f');
            }
            return out.toString();
        }
        return value.toString();
    }

    /**
     * The rule that the values describe. An unknown severity counts as moderate, and an unknown fixer as
     * an editor.
     *
     * @throws IllegalArgumentException when the values have no rule id
     */
    public static ContentRule toRule(Map<String, Object> values) {
        Map<String, Object> all = withDefaults(values);
        String id = string(all, RULE_ID);
        if (id == null) {
            throw new IllegalArgumentException("A rule has no id");
        }
        String severity = string(all, SEVERITY);
        String fixer = string(all, FIXER);
        String evaluator = string(all, EVALUATOR);
        return new ContentRule(id, string(all, DOMAIN), evaluator == null ? ContentRule.PATTERN : evaluator,
                ContentRule.SEVERITIES.contains(severity) ? severity : "moderate",
                Boolean.TRUE.equals(all.get(BLOCKING)), Boolean.TRUE.equals(all.get(ENABLED)),
                ContentRule.FIXERS.contains(fixer) ? fixer : DEFAULT_FIXER, string(all, ADVICE), string(all, UNIT),
                (Double) all.get(MINIMUM), (Double) all.get(MAXIMUM));
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}

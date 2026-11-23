package org.jahia.community.contentgovernance.rules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The values of a rule, keyed by the name of their property on a {@value ContentRules#TYPE} node. A rule
 * file, a rule node and the seeder all read and write the same map.
 *
 * <p>A value is a {@link String}, a {@link Boolean}, a {@link Double}, a {@link List} of strings or a
 * {@link LocalDate}. An absent key has no value.</p>
 */
public final class RuleValues {

    public static final String RULE_ID = "ruleId";
    /** The id of the check of the rule, which jContent labels "Check". */
    public static final String EVALUATOR = "evaluator";
    /**
     * The kind of check of the rule, a {@link CheckType}: {@code PATTERN}, {@code AI} or {@code CODE}. It picks the
     * mixin that holds the fields of the kind.
     */
    public static final String CHECK_TYPE = "checkType";
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
    /** The other forms of the pattern of a term, such as the plural "access tokens". */
    public static final String PATTERN_FORMS = "patternForms";
    /** The other forms of the replacement, such as the plural "jetons d'accès". */
    public static final String REPLACEMENT_FORMS = "replacementForms";
    /** The language of the pattern of a glossary term that the rule translates, such as {@code en}. */
    public static final String PATTERN_LANGUAGE = "patternLanguage";
    /**
     * Where the check of the rule runs: {@value ContentRule#BROWSER} for a check that the drawer runs on the
     * preview of the page, else on the server. The server never runs a rule of the browser.
     */
    public static final String RUNTIME = "runtime";
    /**
     * Internationalized on the node: the title of a finding, with one line per plural form, such as
     * {@code one: {{count}} image has no text alternative}. The rules read and write its English value.
     */
    public static final String TITLE = "title";
    /** Internationalized on the node: a neutral name of what the rule checks, such as "Title length". */
    public static final String LABEL = "label";
    /**
     * The id of the subdomain of the rule, such as {@code seo}: the findings of the rule show in it. A
     * subdomain belongs to a domain, a main tab of the drawer. On a rule node of the tree in
     * {@code systemsite}, the parent node gives the subdomain, and the property is not set.
     */
    public static final String SUBDOMAIN = "subdomain";
    /** The keys of the sites that the rule applies to, or {@value #ALL_SITES} for all sites. */
    public static final String SITES = "sites";
    /** The value of {@value #SITES} for all sites, which a list of sites shows as "All sites". */
    public static final String ALL_SITES = "*";
    /**
     * Not a property: the texts of the rule in the languages other than English, as
     * {@code Map<language, Map<TITLE|LABEL|ADVICE, text>>}. The digest and the seeder ignore it.
     */
    public static final String TRANSLATIONS = "translations";

    /**
     * Every property that a rule file sets, in the order of the digest. 2.0.0 changed the order: it removed
     * {@code domain} and put {@value #SUBDOMAIN} and {@value #SITES} before {@value #RUNTIME}, and
     * {@link LegacyDomains#KEYS_1} reads the fingerprints of before. A key added after 2.0.0 goes last, so
     * that the digest of a rule without it stays the same. The digest holds the subdomain, so that the seeder
     * tells a rule that an administrator moved to another subdomain. An absent key adds nothing to the digest, so
     * {@link LegacyCheckTypes} compares a node that has no {@value #CHECK_TYPE} yet with the fingerprint that its
     * seeder kept.
     */
    public static final List<String> KEYS = List.of(RULE_ID, EVALUATOR, ENABLED, SEVERITY, BLOCKING, FIXER,
            ADVICE, UNIT, MINIMUM, MAXIMUM, KIND, PATTERN, MATCH_MODE, REPLACEMENT, RULE_LANGUAGE, WRONG_EXAMPLES,
            RIGHT_EXAMPLES, OWNER, DECIDED_ON, VALID_FROM, VALID_UNTIL, PATTERN_FORMS, REPLACEMENT_FORMS,
            PATTERN_LANGUAGE, TITLE, LABEL, SUBDOMAIN, SITES, RUNTIME, CHECK_TYPE);
    /** The texts that a rule holds in each language. */
    public static final List<String> TRANSLATABLE = List.of(TITLE, LABEL, ADVICE);
    /** The plural forms of a title, as {@code Intl.PluralRules} and CLDR name them. */
    public static final List<String> PLURAL_FORMS = List.of("zero", "one", "two", "few", "many", "other");
    public static final List<String> BOOLEANS = List.of(ENABLED, BLOCKING);
    public static final List<String> NUMBERS = List.of(MINIMUM, MAXIMUM);
    public static final List<String> LISTS = List.of(WRONG_EXAMPLES, RIGHT_EXAMPLES, PATTERN_FORMS, REPLACEMENT_FORMS,
            SITES);
    public static final List<String> DAYS = List.of(DECIDED_ON, VALID_FROM, VALID_UNTIL);

    private static final String DEFAULT_FIXER = "editor";

    private RuleValues() {
    }

    /** The prefix of a rule id: the part before the first dot, or the whole id. */
    public static String prefixOf(String id) {
        int dot = id.indexOf('.');
        return dot > 0 ? id.substring(0, dot) : id;
    }

    /**
     * The values with the defaults of the node type: on, not blocking, fixed by an editor, for all sites. A
     * node that holds a default and a file that leaves it out have the same values. The subdomain of a rule
     * that names none depends on the subdomains of the site, so {@link ContentRules} gives it.
     */
    static Map<String, Object> withDefaults(Map<String, Object> values) {
        Map<String, Object> out = new HashMap<>(values);
        out.putIfAbsent(ENABLED, Boolean.TRUE);
        out.putIfAbsent(BLOCKING, Boolean.FALSE);
        out.putIfAbsent(FIXER, DEFAULT_FIXER);
        out.putIfAbsent(SITES, List.of(ALL_SITES));
        return out;
    }

    /**
     * True when a list of sites includes a site: it holds the site key or {@value #ALL_SITES}. An empty or
     * missing list holds all sites, as a node that an older version wrote.
     *
     * @param sites   the value of {@value #SITES}, or null
     * @param siteKey the key of a site
     */
    public static boolean includes(Object sites, String siteKey) {
        if (!(sites instanceof List<?> list) || list.isEmpty()) {
            return true;
        }
        return list.contains(ALL_SITES) || list.contains(siteKey);
    }

    /**
     * The plural forms of a title: one line per form, {@code <form>: <text>}. A text with no known form
     * before it is the form {@code other}. Example: {@code "one: 1 image\nother: {{count}} images"}.
     *
     * @param encoded the title as a rule node holds it, or null
     * @return the text of each form, in the order of the lines; empty for a null title
     */
    public static Map<String, String> pluralForms(String encoded) {
        Map<String, String> out = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return out;
        }
        for (String line : encoded.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String form = colon > 0 ? trimmed.substring(0, colon).strip() : "";
            if (PLURAL_FORMS.contains(form)) {
                out.put(form, trimmed.substring(colon + 1).strip());
            } else {
                out.put("other", trimmed);
            }
        }
        return out;
    }

    /** The title of the plural forms, as {@link #pluralForms} reads it. */
    static String encodeForms(Map<?, ?> forms) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<?, ?> form : forms.entrySet()) {
            if (!PLURAL_FORMS.contains(String.valueOf(form.getKey()))) {
                throw new IllegalArgumentException("The plural form " + form.getKey() + " is not one of " + PLURAL_FORMS);
            }
            out.append(out.isEmpty() ? "" : "\n").append(form.getKey()).append(": ").append(form.getValue());
        }
        return out.toString();
    }

    /**
     * The translations of {@code over} on top of those of {@code base}: a text of {@code over} replaces the
     * text of the same language and key. Example: a site translated the advice of a shipped rule in French,
     * and its French title still comes from the module.
     */
    static Map<String, Map<String, String>> mergeTranslations(Object base, Object over) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        for (Object source : new Object[]{base, over}) {
            if (source instanceof Map<?, ?> languages) {
                languages.forEach((language, texts) -> {
                    if (texts instanceof Map<?, ?> map) {
                        map.forEach((key, text) -> out.computeIfAbsent(String.valueOf(language), ignored -> new TreeMap<>())
                                .put(String.valueOf(key), String.valueOf(text)));
                    }
                });
            }
        }
        return out;
    }

    /**
     * A fingerprint of the values: two maps with the same values, defaults included, have the same one.
     * The seeder keeps the fingerprint of what it wrote, and so tells a rule that an administrator changed.
     */
    public static String digest(Map<String, Object> values) {
        return fingerprint(withDefaults(values), KEYS);
    }

    /** The SHA-256 of the values of the keys, in the order of the keys. */
    static String fingerprint(Map<String, Object> all, List<String> keys) {
        StringBuilder canonical = new StringBuilder();
        for (String key : keys) {
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
        return new ContentRule(id, string(all, SUBDOMAIN), evaluator == null ? ContentRule.PATTERN : evaluator,
                severity != null && ContentRule.SEVERITIES.contains(severity) ? severity : "moderate",
                Boolean.TRUE.equals(all.get(BLOCKING)), Boolean.TRUE.equals(all.get(ENABLED)),
                ContentRule.FIXERS.contains(fixer) ? fixer : DEFAULT_FIXER, string(all, ADVICE), string(all, UNIT),
                (Double) all.get(MINIMUM), (Double) all.get(MAXIMUM), all, null, false, Integer.MAX_VALUE);
    }

    /** The items of a list value, or an empty list. */
    static List<String> strings(Map<String, Object> values, String key) {
        return values.get(key) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}

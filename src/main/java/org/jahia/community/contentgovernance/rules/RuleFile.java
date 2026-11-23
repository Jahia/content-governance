package org.jahia.community.contentgovernance.rules;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A rule that a module ships: one Markdown file with a YAML frontmatter, in the folder that the
 * manifest header {@value ShippedRules#HEADER} names.
 *
 * <pre>
 * ---
 * id: seo.title
 * evaluator: threshold
 * checkType: CODE
 * subdomains: [seo, geo.score]
 * severity: serious
 * unit: characters
 * minimum: 30
 * maximum: 60
 * ---
 * Give the page a title, with the main subject first, inside the length range.
 * </pre>
 *
 * <p>The keys of the frontmatter are the properties of a {@value ContentRules#TYPE} node, with
 * {@code id} for {@code ruleId}. The body is the advice, or the criterion of a {@code model} rule. A rule
 * names its {@code checkType}, and sets only the keys that its {@link CheckType} holds. A rule that names
 * no {@code sites} applies to all sites. A rule whose check runs in the browser states
 * {@code runtime: browser}.</p>
 *
 * @param id     the id of the rule
 * @param values the values of the rule, keyed as {@link RuleValues} keys them
 */
public record RuleFile(String id, Map<String, Object> values) {

    /** A rule id becomes the name of its node, so it holds no slash, no colon and no space. */
    static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    /** A site of a rule: {@value RuleValues#ALL_SITES}, or the key of a site. */
    static final Pattern SITE = Pattern.compile("\\*|[A-Za-z0-9_.-]+");
    private static final List<String> KINDS = List.of("forbidden", "replace", "mandatory", "style");
    private static final List<String> MATCH_MODES = List.of("term", "regex");
    private static final String SUFFIX = ".md";

    public RuleFile {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * @param text     the text of the file
     * @param fileName the name of the file, whose stem is the id when the frontmatter has none
     * @return the rule of the file
     * @throws IllegalArgumentException when the file has no frontmatter, an unknown key, no subdomain, no check
     *                                  type, a key that its check type does not hold, or a value that the node
     *                                  type does not allow. Whether a subdomain is declared is checked by
     *                                  {@link ShippedRules}, which knows the declared ones
     */
    public static RuleFile parse(String text, String fileName) {
        Frontmatter file = Frontmatter.parse(text, fileName);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Map<String, String>> translations = new TreeMap<>();
        for (Map.Entry<?, ?> entry : file.keys().entrySet()) {
            read(entry, fileName, values, translations);
        }
        String stem = fileName.endsWith(SUFFIX) ? fileName.substring(0, fileName.length() - SUFFIX.length()) : fileName;
        values.putIfAbsent(RuleValues.RULE_ID, stem);
        values.putIfAbsent(RuleValues.SITES, List.of(RuleValues.ALL_SITES));
        String id = (String) values.get(RuleValues.RULE_ID);
        if (!file.body().isEmpty()) {
            values.put(RuleValues.ADVICE, file.body());
        }
        check(id, values, fileName);
        if (!translations.isEmpty()) {
            values.put(RuleValues.TRANSLATIONS, Collections.unmodifiableMap(translations));
        }
        return new RuleFile(id, values);
    }

    /** Reads one key of the frontmatter into the values, or into the translations for a text of the rule. */
    private static void read(Map.Entry<?, ?> entry, String fileName, Map<String, Object> values,
                             Map<String, Map<String, String>> translations) {
        String key = "id".equals(entry.getKey()) ? RuleValues.RULE_ID : String.valueOf(entry.getKey());
        if (!RuleValues.KEYS.contains(key)) {
            throw new IllegalArgumentException(fileName + " has the unknown key " + entry.getKey());
        }
        if (entry.getValue() == null) {
            return;
        }
        if (RuleValues.TRANSLATABLE.contains(key)) {
            texts(key, entry.getValue(), fileName, values, translations);
        } else {
            values.put(key, value(key, entry.getValue(), fileName));
        }
    }

    /**
     * Reads a text of the rule: a text in English, or a map of languages. The title of a language is a
     * text, or a map of plural forms:
     *
     * <pre>
     * title:
     *   en:
     *     one: "{{count}} image has no text alternative"
     *     other: "{{count}} images have no text alternative"
     *   fr: ...
     * label:
     *   en: Text alternatives of images
     *   fr: Alternatives textuelles des images
     * </pre>
     *
     * The English advice is the body of the file; the frontmatter key {@code advice} gives its other
     * languages.
     */
    private static void texts(String key, Object raw, String fileName, Map<String, Object> values,
                              Map<String, Map<String, String>> translations) {
        if (!(raw instanceof Map<?, ?> languages)) {
            if (RuleValues.ADVICE.equals(key)) {
                throw new IllegalArgumentException(fileName + ": the English advice is the body of the file");
            }
            values.put(key, String.valueOf(raw));
            return;
        }
        for (Map.Entry<?, ?> language : languages.entrySet()) {
            String code = String.valueOf(language.getKey());
            if (!Frontmatter.LANGUAGE.matcher(code).matches()) {
                throw new IllegalArgumentException(fileName + ": " + code + " is not a language such as fr");
            }
            String encoded = encoded(key, language.getValue(), fileName);
            if ("en".equals(code)) {
                values.put(key, encoded);
            } else {
                translations.computeIfAbsent(code, ignored -> new TreeMap<>()).put(key, encoded);
            }
        }
    }

    /** The text of one language: plural forms for a title, as {@link RuleValues#pluralForms} reads them. */
    private static String encoded(String key, Object text, String fileName) {
        if (text instanceof Map<?, ?> forms) {
            if (!RuleValues.TITLE.equals(key)) {
                throw new IllegalArgumentException(fileName + ": only a title has plural forms");
            }
            return RuleValues.encodeForms(forms);
        }
        return text == null ? "" : String.valueOf(text).strip();
    }

    /** The value of a key, in the Java type of its property. */
    private static Object value(String key, Object raw, String fileName) {
        if (RuleValues.BOOLEANS.contains(key)) {
            if (!(raw instanceof Boolean flag)) {
                throw new IllegalArgumentException(fileName + ": " + key + " is true or false");
            }
            return flag;
        }
        if (RuleValues.NUMBERS.contains(key)) {
            if (!(raw instanceof Number number)) {
                throw new IllegalArgumentException(fileName + ": " + key + " is a number");
            }
            return number.doubleValue();
        }
        if (RuleValues.LISTS.contains(key)) {
            List<String> items = new ArrayList<>();
            for (Object item : raw instanceof List<?> list ? list : List.of(raw)) {
                items.add(String.valueOf(item));
            }
            return List.copyOf(items);
        }
        if (RuleValues.DAYS.contains(key)) {
            return day(key, raw, fileName);
        }
        return String.valueOf(raw);
    }

    /** YAML reads 2026-11-19 as a date at midnight UTC, and a quoted day as a text. */
    private static LocalDate day(String key, Object raw, String fileName) {
        if (raw instanceof Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fileName + ": " + key + " is a day such as 2026-11-19");
        }
    }

    private static void check(String id, Map<String, Object> values, String fileName) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(fileName + ": the id " + id + " holds a character that a node name refuses");
        }
        if (values.get(RuleValues.EVALUATOR) == null) {
            throw new IllegalArgumentException("The rule " + id + " names no evaluator");
        }
        checkType(id, values);
        checkPlace(id, values);
        if (!ContentRule.SEVERITIES.contains(values.get(RuleValues.SEVERITY))) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown severity " + values.get(RuleValues.SEVERITY));
        }
        oneOf(id, RuleValues.FIXER, values, ContentRule.FIXERS);
        oneOf(id, RuleValues.KIND, values, KINDS);
        oneOf(id, RuleValues.MATCH_MODE, values, MATCH_MODES);
        oneOf(id, RuleValues.RUNTIME, values, ContentRule.RUNTIMES);
        boolean limited = values.containsKey(RuleValues.MINIMUM) || values.containsKey(RuleValues.MAXIMUM);
        if (CheckType.CODE.name().equals(values.get(RuleValues.CHECK_TYPE)) && limited
                && !values.containsKey(RuleValues.UNIT)) {
            throw new IllegalArgumentException("The rule " + id + " has a limit and no unit");
        }
        if (ContentRule.PATTERN.equals(values.get(RuleValues.EVALUATOR)) && !values.containsKey(RuleValues.PATTERN)) {
            throw new IllegalArgumentException("The rule " + id + " has the evaluator pattern and no pattern");
        }
    }

    /**
     * The check type of a rule: a {@link CheckType}, whose mixin holds each key of a kind that the rule sets.
     * Example: a rule of the check type CODE with a pattern is refused, as its node could not hold the pattern.
     */
    private static void checkType(String id, Map<String, Object> values) {
        Object name = values.get(RuleValues.CHECK_TYPE);
        if (name == null) {
            throw new IllegalArgumentException("The rule " + id + " names no check type");
        }
        CheckType type = CheckType.of(name);
        if (type == null) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown check type " + name);
        }
        for (String key : values.keySet()) {
            if (!CheckType.holds(type, key)) {
                throw new IllegalArgumentException("The rule " + id + " sets " + key + ", which a rule of the check type "
                        + type + " does not hold");
            }
        }
    }

    /** The subdomains of a rule: one at least, each an id; and its sites: each a site key or {@value RuleValues#ALL_SITES}. */
    private static void checkPlace(String id, Map<String, Object> values) {
        if (!(values.get(RuleValues.SUBDOMAINS) instanceof List<?> subdomains) || subdomains.isEmpty()) {
            throw new IllegalArgumentException("The rule " + id + " names no subdomain");
        }
        for (Object subdomain : subdomains) {
            if (!ID_PATTERN.matcher(subdomain.toString()).matches()) {
                throw new IllegalArgumentException("The rule " + id + " names the subdomain " + subdomain
                        + ", which is not an id");
            }
        }
        for (Object site : (List<?>) values.get(RuleValues.SITES)) {
            if (!SITE.matcher(site.toString()).matches()) {
                throw new IllegalArgumentException("The rule " + id + " names the site " + site + ", which is not a site key or *");
            }
        }
    }

    private static void oneOf(String id, String key, Map<String, Object> values, List<String> allowed) {
        Object value = values.get(key);
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown " + key + " " + value);
        }
    }
}

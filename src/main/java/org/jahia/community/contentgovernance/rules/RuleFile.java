package org.jahia.community.contentgovernance.rules;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A rule that a module ships: one Markdown file with a YAML frontmatter, in the folder that the
 * manifest header {@value ShippedRules#HEADER} names.
 *
 * <pre>
 * ---
 * id: seo.title
 * evaluator: threshold
 * severity: serious
 * unit: characters
 * minimum: 30
 * maximum: 60
 * ---
 * Give the page a title, with the main subject first, inside the length range.
 * </pre>
 *
 * <p>The keys of the frontmatter are the properties of a {@value ContentRules#TYPE} node, with
 * {@code id} for {@code ruleId}. The body is the advice, or the criterion of a {@code model} rule.</p>
 *
 * @param id     the id of the rule
 * @param values the values of the rule, keyed as {@link RuleValues} keys them
 */
public record RuleFile(String id, Map<String, Object> values) {

    /** A rule id becomes the name of its node, so it holds no slash, no colon and no space. */
    static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
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
     * @throws IllegalArgumentException when the file has no frontmatter, an unknown key, or a value that
     *                                  the node type does not allow
     */
    public static RuleFile parse(String text, String fileName) {
        // A file may start with a byte order mark, U+FEFF.
        boolean mark = !text.isEmpty() && text.charAt(0) == 0xFEFF;
        String source = (mark ? text.substring(1) : text).replace("\r\n", "\n");
        if (!source.startsWith("---\n")) {
            throw new IllegalArgumentException(fileName + " has no YAML frontmatter");
        }
        int end = source.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("The frontmatter of " + fileName + " has no closing ---");
        }
        Map<?, ?> front = frontmatter(source.substring(4, end), fileName);
        int bodyStart = source.indexOf('\n', end + 4);
        String body = bodyStart < 0 ? "" : source.substring(bodyStart + 1).strip();

        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : front.entrySet()) {
            String key = "id".equals(entry.getKey()) ? RuleValues.RULE_ID : String.valueOf(entry.getKey());
            if (!RuleValues.KEYS.contains(key) || RuleValues.ADVICE.equals(key)) {
                throw new IllegalArgumentException(fileName + " has the unknown key " + entry.getKey());
            }
            if (entry.getValue() != null) {
                values.put(key, value(key, entry.getValue(), fileName));
            }
        }
        String stem = fileName.endsWith(SUFFIX) ? fileName.substring(0, fileName.length() - SUFFIX.length()) : fileName;
        values.putIfAbsent(RuleValues.RULE_ID, stem);
        String id = (String) values.get(RuleValues.RULE_ID);
        values.putIfAbsent(RuleValues.DOMAIN, RuleValues.domainOf(id));
        if (!body.isEmpty()) {
            values.put(RuleValues.ADVICE, body);
        }
        check(id, values, fileName);
        return new RuleFile(id, values);
    }

    private static Map<?, ?> frontmatter(String front, String fileName) {
        Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(10);
            options.setCodePointLimit(64 * 1024);
            loaded = new Yaml(new SafeConstructor(options)).load(front);
        } catch (YAMLException e) {
            throw new IllegalArgumentException("The frontmatter of " + fileName + " is not valid YAML");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("The frontmatter of " + fileName + " is not a map");
        }
        return map;
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
        if (!ContentRule.SEVERITIES.contains(values.get(RuleValues.SEVERITY))) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown severity " + values.get(RuleValues.SEVERITY));
        }
        oneOf(id, RuleValues.FIXER, values, ContentRule.FIXERS);
        oneOf(id, RuleValues.KIND, values, KINDS);
        oneOf(id, RuleValues.MATCH_MODE, values, MATCH_MODES);
        boolean limited = values.containsKey(RuleValues.MINIMUM) || values.containsKey(RuleValues.MAXIMUM);
        if (limited && !values.containsKey(RuleValues.UNIT)) {
            throw new IllegalArgumentException("The rule " + id + " has a limit and no unit");
        }
        if (ContentRule.PATTERN.equals(values.get(RuleValues.EVALUATOR)) && !values.containsKey(RuleValues.PATTERN)) {
            throw new IllegalArgumentException("The rule " + id + " has the evaluator pattern and no pattern");
        }
    }

    private static void oneOf(String id, String key, Map<String, Object> values, List<String> allowed) {
        Object value = values.get(key);
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown " + key + " " + value);
        }
    }
}

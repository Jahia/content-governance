package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A domain or a subdomain that a module ships: one Markdown file in the subfolder {@code domains} or
 * {@code subdomains} of the folder that the manifest header {@value ShippedRules#HEADER} names.
 *
 * <pre>
 * ---
 * id: seo
 * domain: geo
 * order: 20
 * label:
 *   en: SEO
 *   fr: SEO
 * help:
 *   fr: Les balises que lisent les moteurs de recherche.
 * ---
 * The tags that search engines read.
 * </pre>
 *
 * <p>The keys are the {@link DomainValues} of the level. The body is the English help, and the key
 * {@code help} gives its other languages. A file with no {@code sites} applies to all sites.</p>
 *
 * @param level  a domain or a subdomain
 * @param id     the id
 * @param values the values, keyed as {@link DomainValues} keys them
 */
public record DomainFile(Level level, String id, Map<String, Object> values) {

    private static final String SUFFIX = ".md";

    public DomainFile {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * @param text     the text of the file
     * @param fileName the name of the file, whose stem is the id when the frontmatter has none
     * @param level    a domain or a subdomain
     * @throws IllegalArgumentException when the file has no frontmatter, an unknown key, a subdomain names no
     *                                  domain, or a value has the wrong type
     */
    public static DomainFile parse(String text, String fileName, Level level) {
        Frontmatter file = Frontmatter.parse(text, fileName);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, Map<String, String>> translations = new TreeMap<>();
        for (Map.Entry<?, ?> entry : file.keys().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!level.keys().contains(key)) {
                throw new IllegalArgumentException(fileName + " has the unknown key " + key);
            }
            if (entry.getValue() != null) {
                read(key, entry.getValue(), fileName, values, translations);
            }
        }
        String stem = fileName.endsWith(SUFFIX) ? fileName.substring(0, fileName.length() - SUFFIX.length()) : fileName;
        values.putIfAbsent(DomainValues.ID, stem);
        values.putIfAbsent(DomainValues.SITES, List.of(RuleValues.ALL_SITES));
        if (!file.body().isEmpty()) {
            values.put(DomainValues.HELP, file.body());
        }
        String id = values.get(DomainValues.ID).toString();
        check(id, level, values, fileName);
        if (!translations.isEmpty()) {
            values.put(DomainValues.TRANSLATIONS, Collections.unmodifiableMap(translations));
        }
        return new DomainFile(level, id, values);
    }

    private static void read(String key, Object raw, String fileName, Map<String, Object> values,
                             Map<String, Map<String, String>> translations) {
        if (DomainValues.TRANSLATABLE.contains(key)) {
            texts(key, raw, fileName, values, translations);
        } else if (DomainValues.ORDER.equals(key)) {
            if (!(raw instanceof Number number)) {
                throw new IllegalArgumentException(fileName + ": order is a number");
            }
            values.put(key, number.longValue());
        } else if (DomainValues.SITES.equals(key)) {
            List<String> sites = new ArrayList<>();
            for (Object site : raw instanceof List<?> list ? list : List.of(raw)) {
                sites.add(String.valueOf(site));
            }
            values.put(key, List.copyOf(sites));
        } else {
            values.put(key, String.valueOf(raw));
        }
    }

    /** A text in English, or a map of languages; the English help is the body of the file. */
    private static void texts(String key, Object raw, String fileName, Map<String, Object> values,
                              Map<String, Map<String, String>> translations) {
        if (!(raw instanceof Map<?, ?> languages)) {
            if (DomainValues.HELP.equals(key)) {
                throw new IllegalArgumentException(fileName + ": the English help is the body of the file");
            }
            values.put(key, String.valueOf(raw).strip());
            return;
        }
        for (Map.Entry<?, ?> language : languages.entrySet()) {
            String code = String.valueOf(language.getKey());
            if (!Frontmatter.LANGUAGE.matcher(code).matches()) {
                throw new IllegalArgumentException(fileName + ": " + code + " is not a language such as fr");
            }
            String text = language.getValue() == null ? "" : String.valueOf(language.getValue()).strip();
            if ("en".equals(code)) {
                values.put(key, text);
            } else {
                translations.computeIfAbsent(code, ignored -> new TreeMap<>()).put(key, text);
            }
        }
    }

    private static void check(String id, Level level, Map<String, Object> values, String fileName) {
        if (!RuleFile.ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(fileName + ": the id " + id + " holds a character that a node name refuses");
        }
        if (level == Level.SUBDOMAIN && values.get(DomainValues.DOMAIN) == null) {
            throw new IllegalArgumentException("The subdomain " + id + " names no domain");
        }
        if (values.get(DomainValues.LABEL) == null) {
            throw new IllegalArgumentException("The " + level.name().toLowerCase(Locale.ROOT) + " " + id + " has no English label");
        }
        for (Object site : (List<?>) values.get(DomainValues.SITES)) {
            if (!RuleFile.SITE.matcher(site.toString()).matches()) {
                throw new IllegalArgumentException(fileName + ": the site " + site + " is not a site key or *");
            }
        }
    }
}

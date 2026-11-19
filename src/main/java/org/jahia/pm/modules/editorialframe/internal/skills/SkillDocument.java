/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.SkillCoverage;
import org.jahia.pm.modules.editorialframe.internal.frame.Texts;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A parsed {@code SKILL.md}: the YAML frontmatter and the Markdown body, as the Agent Skills
 * specification defines them. The parse is tolerant, as agentskills.io recommends: an unknown key
 * goes to the metadata, and a name that breaks the rules gives a warning. The keys of Jahia in
 * {@code metadata}, such as {@code jahia-scope}, give the label, the coverage and the tools of the
 * skill, as {@link JahiaSkillKeys} reads them.
 */
public final class SkillDocument {

    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0,63}");
    public static final int MAX_DESCRIPTION = 1024;
    private static final List<String> KNOWN = List.of("name", "description", "license", "compatibility",
            "metadata", "allowed-tools");

    public final String name;
    public final String description;
    public final String license;
    public final String compatibility;
    public final String allowedTools;
    /** Every key of {@code metadata}, then every unknown key of the frontmatter, as strings. */
    public final Map<String, String> metadata;
    /** The same keys, with their YAML values: a list stays a list, such as {@code jahia-in-scope}. */
    public final Map<String, Object> rawMetadata;
    public final String body;
    public final List<String> warnings;
    /** The label of {@code jahia-label}, or {@code null}. */
    public final String label;
    public final SkillCoverage coverage;
    /** The Jahia tools of {@code allowed-tools}. */
    public final List<String> tools;

    private SkillDocument(Map<?, ?> frontmatter, String name, String description, String body, List<String> warnings) {
        this.name = name;
        this.description = description;
        this.license = string(frontmatter.get("license"));
        this.compatibility = string(frontmatter.get("compatibility"));
        this.allowedTools = string(frontmatter.get("allowed-tools"));
        this.rawMetadata = allMetadata(frontmatter);
        Map<String, String> asStrings = new LinkedHashMap<>();
        rawMetadata.forEach((key, value) -> asStrings.put(key, string(value)));
        this.metadata = asStrings;
        this.body = body;
        this.warnings = warnings;
        this.label = JahiaSkillKeys.labelOf(rawMetadata, warnings);
        this.coverage = JahiaSkillKeys.coverage(rawMetadata, description, warnings);
        this.tools = JahiaSkillKeys.tools(allowedTools, warnings);
    }

    /**
     * Parses a {@code SKILL.md}.
     *
     * @param folderName the name of the folder of the skill, the name to use when the frontmatter has none
     * @throws IllegalArgumentException when the frontmatter is missing or unreadable, or has no description
     */
    public static SkillDocument parse(String text, String folderName) {
        String source = text.startsWith("\uFEFF") ? text.substring(1) : text;
        source = source.replace("\r\n", "\n");
        if (!source.startsWith("---\n")) {
            throw new IllegalArgumentException("SKILL.md has no YAML frontmatter");
        }
        int end = source.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("The frontmatter of SKILL.md has no closing ---");
        }
        String front = source.substring(4, end);
        int bodyStart = source.indexOf('\n', end + 4);
        String body = bodyStart < 0 ? "" : source.substring(bodyStart + 1).strip();
        Map<?, ?> map = frontmatter(front);
        List<String> warnings = new ArrayList<>();
        String name = skillName(string(map.get("name")), folderName, warnings);
        String description = descriptionOf(string(map.get("description")), warnings);
        return new SkillDocument(map, name, description, body, warnings);
    }

    /** The YAML map of a frontmatter. */
    private static Map<?, ?> frontmatter(String front) {
        Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(10);
            options.setCodePointLimit(256 * 1024);
            loaded = new Yaml(new SafeConstructor(options)).load(front);
        } catch (YAMLException e) {
            throw new IllegalArgumentException("The frontmatter of SKILL.md is not valid YAML");
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("The frontmatter of SKILL.md is not a map");
        }
        return (Map<?, ?>) loaded;
    }

    /**
     * The name of the skill: the declared name, else the folder name. A name that breaks the naming
     * rule is fixed, with a warning.
     */
    private static String skillName(String declared, String folderName, List<String> warnings) {
        String name = declared;
        if (name == null || name.isBlank()) {
            name = folderName;
            warnings.add("The frontmatter has no name: the folder name is used");
        }
        name = name.trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            String fixed = fixedName(name, folderName);
            warnings.add(String.format("The name '%s' breaks the naming rule: '%s' is used", name, fixed));
            name = fixed;
        }
        if (folderName != null && !name.equals(folderName)) {
            warnings.add(String.format("The name '%s' differs from the folder '%s'", name, folderName));
        }
        return name;
    }

    /** A name that keeps the naming rule, made from a name that breaks it, else from the folder name. */
    private static String fixedName(String name, String folderName) {
        String fixed = cut(dashed(name));
        if (fixed.isEmpty() && folderName != null) {
            fixed = dashed(folderName);
        }
        if (fixed.isEmpty()) {
            throw new IllegalArgumentException(String.format("The name '%s' gives no valid skill name", name));
        }
        return cut(fixed);
    }

    /** A text in lower case, with a dash for each run of characters other than a letter or a digit, and none at its ends. */
    private static String dashed(String text) {
        return Texts.trim(text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"), '-');
    }

    /** A dashed name cut at 64 characters. It starts with no dash, so the trim removes the dash that the cut may leave at its end. */
    private static String cut(String name) {
        return name.length() > 64 ? Texts.trim(name.substring(0, 64), '-') : name;
    }

    /** The description, cut at {@link #MAX_DESCRIPTION} characters with a warning. */
    private static String descriptionOf(String declared, List<String> warnings) {
        if (declared == null || declared.isBlank()) {
            throw new IllegalArgumentException("SKILL.md has no description");
        }
        String description = declared.strip();
        if (description.length() > MAX_DESCRIPTION) {
            warnings.add("The description is longer than " + MAX_DESCRIPTION + " characters: it is cut");
            description = description.substring(0, MAX_DESCRIPTION);
        }
        return description;
    }

    /** Every key of {@code metadata}, then every unknown key of the frontmatter, with its YAML value. */
    private static Map<String, Object> allMetadata(Map<?, ?> frontmatter) {
        Map<String, Object> values = new LinkedHashMap<>();
        Object declared = frontmatter.get("metadata");
        if (declared instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) declared).entrySet()) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        for (Map.Entry<?, ?> entry : frontmatter.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!KNOWN.contains(key)) {
                values.put(key, entry.getValue());
            }
        }
        return values;
    }

    /** A YAML value as a string: a list becomes its items joined by a comma. */
    public static String string(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection) {
            List<String> items = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                items.add(String.valueOf(item));
            }
            return String.join(",", items);
        }
        return String.valueOf(value);
    }

    /** The values of a metadata key that holds a list joined by commas. */
    public List<String> metadataList(String key) {
        String value = metadata.get(key);
        List<String> items = new ArrayList<>();
        if (value != null) {
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    items.add(item.trim());
                }
            }
        }
        return items;
    }
}

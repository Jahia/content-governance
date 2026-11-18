/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.SkillCoverage;
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

    public static final Pattern NAME = Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0,63}");
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

    private SkillDocument(String name, String description, String license, String compatibility, String allowedTools,
                          Map<String, Object> rawMetadata, String body, List<String> warnings) {
        this.name = name;
        this.description = description;
        this.license = license;
        this.compatibility = compatibility;
        this.allowedTools = allowedTools;
        this.rawMetadata = rawMetadata;
        Map<String, String> metadata = new LinkedHashMap<>();
        rawMetadata.forEach((key, value) -> metadata.put(key, string(value)));
        this.metadata = metadata;
        this.body = body;
        this.warnings = warnings;
        this.label = JahiaSkillKeys.label(rawMetadata, warnings);
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
        Map<?, ?> map = (Map<?, ?>) loaded;
        List<String> warnings = new ArrayList<>();
        String name = string(map.get("name"));
        if (name == null || name.isBlank()) {
            name = folderName;
            warnings.add("The frontmatter has no name: the folder name is used");
        }
        name = name.trim();
        if (!NAME.matcher(name).matches()) {
            String fixed = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(?:^-+)|(?:-+$)", "");
            fixed = fixed.length() > 64 ? fixed.substring(0, 64).replaceAll("-+$", "") : fixed;
            if (fixed.isEmpty() && folderName != null) {
                fixed = folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(?:^-+)|(?:-+$)", "");
            }
            if (fixed.isEmpty()) {
                throw new IllegalArgumentException("The name '" + name + "' gives no valid skill name");
            }
            fixed = fixed.length() > 64 ? fixed.substring(0, 64).replaceAll("-+$", "") : fixed;
            warnings.add("The name '" + name + "' breaks the naming rule: '" + fixed + "' is used");
            name = fixed;
        }
        if (folderName != null && !name.equals(folderName)) {
            warnings.add("The name '" + name + "' differs from the folder '" + folderName + "'");
        }
        String description = string(map.get("description"));
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md has no description");
        }
        description = description.strip();
        if (description.length() > MAX_DESCRIPTION) {
            warnings.add("The description is longer than " + MAX_DESCRIPTION + " characters: it is cut");
            description = description.substring(0, MAX_DESCRIPTION);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object declared = map.get("metadata");
        if (declared instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) declared).entrySet()) {
                metadata.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!KNOWN.contains(key)) {
                metadata.put(key, entry.getValue());
            }
        }
        return new SkillDocument(name, description, string(map.get("license")), string(map.get("compatibility")),
                string(map.get("allowed-tools")), metadata, body, warnings);
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

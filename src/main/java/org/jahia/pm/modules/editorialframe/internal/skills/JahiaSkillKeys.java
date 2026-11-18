/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.SkillCoverage;
import org.jahia.pm.modules.editorialframe.api.SkillExample;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The keys of {@code metadata} in a {@code SKILL.md} that Jahia reads, beyond the Agent Skills
 * format, and how it reads the label, the coverage and the tools of a skill. A skill of a module
 * and a skill stored as content go through the same reading.
 *
 * <p>The reading is tolerant, as the Agent Skills specification recommends: a value that breaks a
 * limit is cut or left out, with a warning, and the skill keeps the rest.</p>
 */
public final class JahiaSkillKeys {

    public static final String LABEL = "jahia-label";
    public static final String SCOPE = "jahia-scope";
    public static final String IN_SCOPE = "jahia-in-scope";
    public static final String OUT_OF_SCOPE = "jahia-out-of-scope";
    public static final String ACTIVATION = "jahia-activation";
    public static final String CONSUMERS = "jahia-consumers";
    public static final String ACTION = "jahia-action";

    /** A Jahia tool name, such as {@code content.get}. A tool of another agent, such as {@code Read}, is not one. */
    static final Pattern TOOL = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
    static final int MAX_LABEL_CHARS = 60;
    static final int MAX_SCOPE_CHARS = 300;
    static final int MAX_EXAMPLES = 5;
    static final int MAX_NOTE_CHARS = 120;
    static final int MAX_ATTACHED_IMAGES = 10;
    static final int MAX_TOOLS = 20;
    private static final Pattern SPACES = Pattern.compile("[\\p{Cntrl}\\s]+");

    private JahiaSkillKeys() {
    }

    /** The label of {@code jahia-label}, or {@code null}. */
    public static String label(Map<String, Object> metadata, List<String> warnings) {
        return line(metadata.get(LABEL), LABEL, MAX_LABEL_CHARS, warnings);
    }

    /**
     * The coverage of {@code jahia-scope}, {@code jahia-in-scope} and {@code jahia-out-of-scope}.
     * The sentence is the description, on one line, when the skill states none.
     */
    public static SkillCoverage coverage(Map<String, Object> metadata, String description, List<String> warnings) {
        String sentence = line(metadata.get(SCOPE), SCOPE, MAX_SCOPE_CHARS, warnings);
        if (sentence == null && description != null) {
            sentence = SPACES.matcher(description).replaceAll(" ").trim();
        }
        return new SkillCoverage(sentence, examples(metadata.get(IN_SCOPE), IN_SCOPE, warnings),
                examples(metadata.get(OUT_OF_SCOPE), OUT_OF_SCOPE, warnings));
    }

    /**
     * The Jahia tools of {@code allowed-tools}, separated by spaces or commas. A name that is not a
     * Jahia tool, such as {@code Read} or {@code Bash(git:*)}, is left out with a warning.
     */
    public static List<String> tools(String allowedTools, List<String> warnings) {
        Set<String> tools = new LinkedHashSet<>();
        if (allowedTools == null) {
            return List.of();
        }
        for (String item : allowedTools.split("[\\s,]+")) {
            if (item.isEmpty()) {
                continue;
            }
            if (!TOOL.matcher(item).matches()) {
                warnings.add("allowed-tools: '" + item + "' is not a Jahia tool, such as content.get: it is left out");
            } else if (tools.size() == MAX_TOOLS) {
                warnings.add("allowed-tools: more than " + MAX_TOOLS + " tools: '" + item + "' is left out");
            } else {
                tools.add(item);
            }
        }
        return List.copyOf(tools);
    }

    /**
     * The examples of a list, each one a message or a map with {@code message}, {@code note} and
     * {@code attached-images}. The key {@code attachedImages} of a declaration of the assistant is
     * read too.
     */
    static List<SkillExample> examples(Object value, String field, List<String> warnings) {
        List<SkillExample> examples = new ArrayList<>();
        if (value == null) {
            return examples;
        }
        if (!(value instanceof Collection)) {
            warnings.add(field + ": a list of messages is expected: it is left out");
            return examples;
        }
        for (Object item : (Collection<?>) value) {
            if (examples.size() == MAX_EXAMPLES) {
                warnings.add(field + ": more than " + MAX_EXAMPLES + " examples: the others are left out");
                break;
            }
            SkillExample example = example(item, field, warnings);
            if (example != null) {
                examples.add(example);
            }
        }
        return examples;
    }

    private static SkillExample example(Object item, String field, List<String> warnings) {
        if (!(item instanceof Map)) {
            String message = line(item, field, MAX_SCOPE_CHARS, warnings);
            return message == null ? null : new SkillExample(message, 0, null);
        }
        Map<?, ?> map = (Map<?, ?>) item;
        String message = line(map.get("message"), field + ".message", MAX_SCOPE_CHARS, warnings);
        if (message == null) {
            warnings.add(field + ": an example has no message: it is left out");
            return null;
        }
        String note = line(map.get("note"), field + ".note", MAX_NOTE_CHARS, warnings);
        Object images = map.containsKey("attached-images") ? map.get("attached-images") : map.get("attachedImages");
        return new SkillExample(message, images(images, field, warnings), note);
    }

    private static int images(Object value, String field, List<String> warnings) {
        if (value == null) {
            return 0;
        }
        try {
            int count = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value).trim());
            if (count >= 0 && count <= MAX_ATTACHED_IMAGES) {
                return count;
            }
        } catch (NumberFormatException e) {
            // Reported below.
        }
        warnings.add(field + ".attached-images: a number from 0 to " + MAX_ATTACHED_IMAGES + " is expected: 0 is used");
        return 0;
    }

    /** A text on one line, cut at the limit with a warning. {@code null} when absent or blank. */
    static String line(Object value, String field, int maxChars, List<String> warnings) {
        if (value == null || value instanceof Collection || value instanceof Map) {
            return null;
        }
        String text = SPACES.matcher(String.valueOf(value)).replaceAll(" ").trim();
        if (text.length() > maxChars) {
            warnings.add(field + ": longer than " + maxChars + " characters: it is cut");
            text = text.substring(0, maxChars).trim();
        }
        return text.isEmpty() ? null : text;
    }
}

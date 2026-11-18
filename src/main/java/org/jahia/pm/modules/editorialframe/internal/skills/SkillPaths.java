/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import java.util.Locale;
import java.util.Set;

/** The rules on the paths and the types of the files of a skill. */
public final class SkillPaths {

    /** The text types a skill may hold. A skill never runs code, so scripts are never kept. */
    /** The file that makes a folder a skill. */
    public static final String SKILL_FILE = "SKILL.md";
    public static final Set<String> TEXT_TYPES = Set.of("md", "markdown", "txt", "csv", "json", "yaml", "yml");
    public static final int MAX_FILE_CHARS = 2 * 1024 * 1024;
    /** The longest file text that one read gives to a model: about 10,000 tokens. */
    public static final int MAX_READ_CHARS = 40_000;

    private SkillPaths() {
    }

    /**
     * True for a relative path with no parent step, no absolute part, no backslash and no control
     * character, such as {@code references/glossary.md}.
     */
    public static boolean isSafeRelative(String path) {
        if (path == null || path.isEmpty() || path.length() > 512 || path.startsWith("/") || path.contains("\\")) {
            return false;
        }
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return false;
            }
        }
        return path.codePoints().noneMatch(Character::isISOControl) && !path.contains(":");
    }

    /** True for a file a skill keeps: a text type outside of {@code scripts/}. */
    public static boolean isKept(String relativePath) {
        if (relativePath.startsWith("scripts/") || relativePath.contains("/scripts/")) {
            return false;
        }
        int dot = relativePath.lastIndexOf('.');
        String extension = dot < 0 ? "" : relativePath.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TEXT_TYPES.contains(extension);
    }
}

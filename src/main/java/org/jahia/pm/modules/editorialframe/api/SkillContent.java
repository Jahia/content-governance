/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.List;

/**
 * A skill with its body and the list of its files. The digest is the first 12 hexadecimal
 * characters of the SHA-256 of the body and the files, which names the exact version of the skill.
 */
public record SkillContent(SkillSummary summary, String body, List<SkillFileInfo> files, String digest) {

    public SkillContent {
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** A file of a skill, without its text. */
    public record SkillFileInfo(String path, int size) {
    }
}

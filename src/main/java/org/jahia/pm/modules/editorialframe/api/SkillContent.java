/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.List;

/**
 * A skill with its body and the list of its files. The digest is the first 12 hexadecimal
 * characters of the SHA-256 of the body and the files, which names the exact version of the skill.
 */
public final class SkillContent {

    private final SkillSummary summary;
    private final String body;
    private final List<SkillFileInfo> files;
    private final String digest;

    public SkillContent(SkillSummary summary, String body, List<SkillFileInfo> files, String digest) {
        this.summary = summary;
        this.body = body;
        this.files = files == null ? List.of() : List.copyOf(files);
        this.digest = digest;
    }

    public SkillSummary summary() {
        return summary;
    }

    public String body() {
        return body;
    }

    public List<SkillFileInfo> files() {
        return files;
    }

    public String digest() {
        return digest;
    }

    /** A file of a skill, without its text. */
    public static final class SkillFileInfo {

        private final String path;
        private final int size;

        public SkillFileInfo(String path, int size) {
            this.path = path;
            this.size = size;
        }

        public String path() {
            return path;
        }

        public int size() {
            return size;
        }
    }
}

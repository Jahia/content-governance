/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.SkillContent;
import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.jahia.pm.modules.editorialframe.internal.frame.Texts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A skill with its body and the texts of its files, from any source. */
public final class LoadedSkill {

    public final SkillSummary summary;
    public final String body;
    /** The texts of the files, by relative path, in path order. */
    public final Map<String, String> files;
    public final String digest;

    public LoadedSkill(SkillSummary summary, String body, Map<String, String> files) {
        this.summary = summary;
        this.body = body == null ? "" : body;
        this.files = Collections.unmodifiableMap(new TreeMap<>(files));
        StringBuilder manifest = new StringBuilder("SKILL.md\n").append(this.body).append('\n');
        for (Map.Entry<String, String> file : this.files.entrySet()) {
            manifest.append(file.getKey()).append('\n').append(file.getValue()).append('\n');
        }
        this.digest = Texts.digest(manifest.toString());
    }

    public LoadedSkill withSummary(SkillSummary other) {
        return new LoadedSkill(other, body, files);
    }

    public SkillContent content() {
        List<SkillContent.SkillFileInfo> infos = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            infos.add(new SkillContent.SkillFileInfo(file.getKey(), file.getValue().length()));
        }
        return new SkillContent(summary, body, infos, digest);
    }
}

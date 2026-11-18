/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameRequest;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.Persona;
import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.ContextBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The frame of a site served as a skill, {@code brand-frame}, so that an agent outside Jahia, such
 * as Claude or Copilot through MCP, writes with the same voice, readers and rules. The chat of
 * Jahia receives the essentials of the frame in its instructions, and reads the full texts here:
 * {@code voices.md}, {@code personas.md}, {@code rules.csv} and {@code glossary.csv}.
 */
public final class GeneratedFrameSkill {

    public static final String NAME = "brand-frame";
    private static final int BODY_TOKENS = 6000;

    private GeneratedFrameSkill() {
    }

    /** The skill of a frame, or {@code null} for a site with no frame. */
    public static LoadedSkill of(Frame frame) {
        if (frame == null || frame.isEmpty()) {
            return null;
        }
        String body = ContextBuilder.build(frame, new FrameRequest(frame.siteKey(), frame.language(), null,
                BODY_TOKENS, null)).text()
                + "\nThe file " + (frame.voices().isEmpty() ? "personas.md holds" : "voices.md holds every voice in full, personas.md")
                + " every reader in full, rules.csv every rule"
                + (frame.glossary().isEmpty() ? "" : ", and glossary.csv every glossary term") + ".\n";
        Map<String, String> files = new LinkedHashMap<>();
        if (!frame.voices().isEmpty()) {
            files.put("voices.md", voicesFile(frame.voices()));
        }
        files.put("personas.md", personasFile(frame.personas()));
        files.put("rules.csv", rulesFile(frame.rules()));
        if (!frame.glossary().isEmpty()) {
            files.put("glossary.csv", glossaryFile(frame.glossary()));
        }
        SkillSummary summary = new SkillSummary(NAME, "The editorial frame of the site " + frame.siteKey()
                + ": the voice of the brand, its readers, its writing rules and its glossary. Read it before you write or change "
                + "any content of this site, and follow it.", "generated", "frame", "auto", List.of("mcp"), "find",
                "/sites/" + frame.siteKey() + " (frame " + frame.fingerprint() + ")", List.of());
        return new LoadedSkill(summary, body, files);
    }

    private static String voicesFile(List<FrameText> voices) {
        StringBuilder file = new StringBuilder("# Voices of the brand\n");
        for (FrameText voice : voices) {
            file.append("\n## ").append(voice.title()).append("group".equals(voice.scope()) ? " (whole group)" : "")
                    .append("\n\n").append(voice.text().trim()).append('\n');
        }
        return file.toString();
    }

    private static String personasFile(List<Persona> personas) {
        StringBuilder file = new StringBuilder("# Readers of the site\n");
        for (Persona persona : personas) {
            file.append("\n## ").append(persona.title()).append(" (").append(persona.name()).append(')')
                    .append(persona.isDefault() ? " - default" : "").append("\n\n").append(persona.text().trim()).append('\n');
        }
        return file.toString();
    }

    private static String rulesFile(List<WritingRule> rules) {
        StringBuilder file = new StringBuilder(
                "kind,pattern,match,replacement,language,severity,justification,wrong_examples,right_examples\n");
        for (WritingRule rule : rules) {
            file.append(csv(rule.kind().name().toLowerCase())).append(',').append(csv(rule.pattern())).append(',')
                    .append(csv(rule.matchMode().name().toLowerCase())).append(',').append(csv(rule.replacement()))
                    .append(',').append(csv(rule.language())).append(',').append(csv(rule.severity().name().toLowerCase()))
                    .append(',').append(csv(rule.justification()))
                    .append(',').append(csv(String.join(" | ", rule.wrongExamples())))
                    .append(',').append(csv(String.join(" | ", rule.rightExamples()))).append('\n');
        }
        return file.toString();
    }

    private static String glossaryFile(List<GlossaryTerm> glossary) {
        StringBuilder file = new StringBuilder(
                "term,term_language,translation,translation_language,note,term_forms,translation_forms,definition,status\n");
        for (GlossaryTerm term : glossary) {
            file.append(csv(term.term())).append(',').append(csv(term.termLanguage())).append(',')
                    .append(csv(term.translation())).append(',').append(csv(term.translationLanguage())).append(',')
                    .append(csv(term.note())).append(',').append(csv(String.join("|", term.termForms()))).append(',')
                    .append(csv(String.join("|", term.translationForms()))).append(',').append(csv(term.definition()))
                    .append(',').append(csv(term.status().name().toLowerCase(Locale.ROOT))).append('\n');
        }
        return file.toString();
    }

    static String csv(String value) {
        if (value == null) {
            return "";
        }
        return needsQuotes(value) ? '"' + value.replace("\"", "\"\"").replace('\n', ' ') + '"' : value;
    }

    /**
     * True for a value that holds a comma or a quote and no line terminator, or one line feed as its
     * only line terminator. The line terminators are those of java.util.regex: LF, CR, U+0085, U+2028
     * and U+2029. A value with another line terminator, or with two, is written as it is.
     */
    private static boolean needsQuotes(String value) {
        int terminators = 0;
        boolean lineFeed = false;
        boolean separator = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            separator |= c == ',' || c == '"';
            if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                terminators++;
                lineFeed = c == '\n';
            }
        }
        return terminators == 0 ? separator : terminators == 1 && lineFeed;
    }
}

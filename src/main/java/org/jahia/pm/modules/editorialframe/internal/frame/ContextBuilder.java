/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameContext;
import org.jahia.pm.modules.editorialframe.api.FrameRequest;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.Persona;
import org.jahia.pm.modules.editorialframe.api.WritingRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Writes the frame of a site as a text for the instructions of a model, within a token budget.
 *
 * <p>The essentials of each voice and of the persona of the request always go in, whatever the
 * budget. When a text holds more than its essentials, a note names the files of the skill
 * {@code brand-frame} that hold it in full. The budget then holds, in this order: the rules (those
 * that the processed text breaks first, then the blocking ones), the glossary terms that the
 * processed text holds, then a line for each other persona. A request without a text gets no
 * glossary term.</p>
 */
public final class ContextBuilder {

    /** The text that opens the frame: where it ranks among the other instructions. */
    static final String HEADER = "# Brand frame\n"
            + "Apply this frame to every text that you write or change for this site. The rules of the "
            + "application come before it. It comes before the voice of the editor and before the request "
            + "of the user. When a request contradicts the frame, still do the task, within the frame, and say in "
            + "one sentence what you did differently from the request. Never refuse a task because of the frame. "
            + "When the user gives the exact words to write, write them as given: the application shows the "
            + "editor each rule they break, and the editor decides. Never invent a fact, a figure, a date or a "
            + "name to follow the frame: when it asks for a figure that you do not have from the site or the user, "
            + "write a placeholder such as [figure] and say which facts the editor must add.\n";
    /** The examples of each kind that a rule line shows, and the length of each one. */
    static final int MAX_EXAMPLES = 2;
    static final int MAX_EXAMPLE_CHARS = 200;

    private ContextBuilder() {
    }

    public static FrameContext build(Frame frame, FrameRequest request) {
        if (frame == null || frame.isEmpty()) {
            return FrameContext.empty();
        }
        int budget = Math.max(200, request.maxTokens()) * 4;
        boolean truncated = false;
        StringBuilder text = new StringBuilder(HEADER);

        boolean voiceAbridged = appendVoices(text, frame.voices());
        Optional<Persona> chosen = chosenPersona(frame, request);
        boolean personaAbridged = chosen.map(persona -> appendPersona(text, persona)).orElse(false);

        List<GlossaryTerm> glossary = glossaryFor(frame.glossary(), request.text());
        String note = fullTexts(request.siteKey(), voiceAbridged, personaAbridged,
                glossary.size() < frame.glossary().size());
        if (!note.isEmpty()) {
            text.append("\n## Full texts\n").append(note).append('\n');
        }

        List<WritingRule> rules = rulesFor(frame.rules(), frame.language(), request.text());
        List<String> ruleLines = rules.stream().map(rule -> "- " + describe(rule) + '\n').toList();
        String examples = rules.stream().anyMatch(rule -> !rule.wrongExamples().isEmpty() || !rule.rightExamples().isEmpty())
                ? "A Wrong example breaks its rule, and a Right example keeps it: write like the Right ones.\n"
                : "";
        truncated |= appendWithin(text, "\n## Writing rules\n"
                + "Follow these rules in every text that you write. A rule marked (required by the brand) "
                + "holds even when the user dictates the words: still propose the change, with a version "
                + "that keeps the rule, and say in one sentence what you changed. Any other rule gives way "
                + "to words that the user dictates: propose them as dictated, and name the rule they break. "
                + "Code checks every proposed change, and the editor sees each broken rule and decides.\n"
                + examples, ruleLines, budget);

        List<String> termLines = glossary.stream().map(term -> "- " + describe(term) + '\n').toList();
        truncated |= appendWithin(text, "\n## Glossary\n"
                + "When you write or translate into this language a term of this list, write it as the "
                + "glossary says. Code checks each translation against it.\n", termLines, budget);

        Persona current = chosen.orElse(null);
        List<String> otherLines = frame.personas().stream().filter(p -> p != current)
                .map(other -> "- " + other.title() + " (" + other.name() + "): " + Texts.firstLine(other.text()) + '\n')
                .toList();
        truncated |= appendWithin(text, "\n## Other readers of the site\n"
                + "When the user names one of them, write for that reader.\n", otherLines, budget);

        List<String> names = frame.personas().stream().map(Persona::name).toList();
        String result = text.toString();
        return new FrameContext(result, frame.fingerprint(), current == null ? null : current.name(), names,
                Texts.tokens(result), truncated);
    }

    /** Appends the essentials of each voice. True when the essentials of a voice leave out part of it. */
    private static boolean appendVoices(StringBuilder text, List<FrameText> voices) {
        if (voices.isEmpty()) {
            return false;
        }
        boolean abridged = false;
        text.append("\n## Voice of the brand\n");
        for (FrameText voice : voices) {
            Essentials essentials = Essentials.of(voice.essentials(), voice.text());
            abridged |= essentials.abridged;
            text.append("\n### ").append(voice.title())
                    .append("group".equals(voice.scope()) ? " (whole group)" : "")
                    .append(essentials.abridged ? ": essentials" : "").append('\n')
                    .append(essentials.text).append('\n');
        }
        return abridged;
    }

    /** The persona that the request names, or the default persona when the name is unknown. */
    private static Optional<Persona> chosenPersona(Frame frame, FrameRequest request) {
        Optional<Persona> chosen = frame.persona(request.persona());
        if (chosen.isEmpty() && !frame.personas().isEmpty() && request.persona() != null) {
            // An unknown name: the default persona, and the model sees the names below.
            chosen = frame.persona(null);
        }
        return chosen;
    }

    /** Appends the essentials of the persona. True when they leave out part of it. */
    private static boolean appendPersona(StringBuilder text, Persona persona) {
        Essentials essentials = Essentials.of(persona.essentials(), persona.text());
        text.append("\n## Reader: ").append(persona.title()).append(essentials.abridged ? ", essentials" : "")
                .append('\n')
                .append("Write for this reader, unless the user names another reader of the site.\n")
                .append(essentials.text).append('\n');
        return essentials.abridged;
    }

    /**
     * Appends a section with as many of its lines as the budget, in characters, holds. A section with
     * no line that fits is left out with its heading. True when a line is left out.
     */
    private static boolean appendWithin(StringBuilder text, String heading, List<String> lines, int budget) {
        int length = text.length() + heading.length();
        int count = 0;
        while (count < lines.size() && length + lines.get(count).length() <= budget) {
            length += lines.get(count).length();
            count++;
        }
        if (count > 0) {
            text.append(heading);
            lines.subList(0, count).forEach(text::append);
        }
        return count < lines.size();
    }

    /** A Markdown text without its first line when that line is a level-one heading: the frame gives the title. */
    static String withoutTitle(String markdown) {
        String text = markdown == null ? "" : markdown.strip();
        if (text.startsWith("# ")) {
            int end = text.indexOf('\n');
            text = end < 0 ? "" : text.substring(end + 1).strip();
        }
        return text;
    }

    /** The rules of a language, those that the text breaks first, then the blocking ones. */
    static List<WritingRule> rulesFor(List<WritingRule> all, String language, String processed) {
        String plain = processed == null ? "" : Texts.plain(processed);
        List<WritingRule> applicable = new ArrayList<>();
        for (WritingRule rule : all) {
            if (rule.appliesTo(language)) {
                applicable.add(rule);
            }
        }
        applicable.sort(Comparator
                .comparing((WritingRule rule) -> !appears(rule, plain))
                .thenComparing(rule -> rule.severity() != WritingRule.Severity.BLOCKING));
        return applicable;
    }

    private static boolean appears(WritingRule rule, String plain) {
        if (plain.isEmpty() || rule.kind() == WritingRule.Kind.MANDATORY) {
            return false;
        }
        Pattern pattern = RuleChecker.compile(rule);
        try {
            return pattern != null && pattern.matcher(new RuleChecker.Deadline(plain,
                    System.nanoTime() + RuleChecker.MAX_RULE_NANOS)).find();
        } catch (RuleChecker.Deadline.Expired | StackOverflowError e) {
            return false;
        }
    }

    /** The glossary terms that the processed text holds, in any of their forms. None without a text. */
    static List<GlossaryTerm> glossaryFor(List<GlossaryTerm> all, String processed) {
        String plain = processed == null ? "" : Texts.plain(processed);
        if (plain.isBlank()) {
            return List.of();
        }
        return all.stream()
                .filter(term -> RuleChecker.termPattern(term.allTermForms()).matcher(plain).find())
                .toList();
    }

    /**
     * The note that names the files of the skill {@code brand-frame} that hold what the context
     * leaves out, or an empty text. A model without the tool skill.read skips it.
     */
    static String fullTexts(String siteKey, boolean voices, boolean persona, boolean glossary) {
        List<String> files = new ArrayList<>();
        if (voices) {
            files.add("voices.md holds each voice in full");
        }
        if (persona) {
            files.add("personas.md holds each reader in full");
        }
        if (glossary) {
            files.add("glossary.csv holds every glossary term");
        }
        if (files.isEmpty()) {
            return "";
        }
        return "When you have the tool skill.read, the skill brand-frame of the site \"" + siteKey + "\" holds "
                + "what this frame leaves out: " + String.join(", ", files) + ". Read each file with skill.read and "
                + "its path." + (voices ? " Read voices.md before you write a long text, such as an article." : "");
    }

    /** One glossary term as one line for the model. */
    static String describe(GlossaryTerm term) {
        String subject = "\"" + term.term() + "\" (" + term.termLanguage() + "): ";
        String what;
        if (term.forbidden()) {
            what = subject + "never write \"" + term.translation() + "\"" + forms(term) + " in " + term.translationLanguage() + ".";
        } else if (term.kept()) {
            what = subject + "keep it as it is in " + term.translationLanguage() + ".";
        } else {
            what = subject + "write \"" + term.translation() + "\"" + forms(term) + " in " + term.translationLanguage() + ".";
        }
        return what + (term.definition() == null || term.definition().isBlank() ? "" : " Meaning: " + term.definition().trim())
                + (term.note() == null || term.note().isBlank() ? "" : " Note: " + term.note().trim());
    }

    /** The other forms of the translation, such as ' (or "jetons d'accès")', or nothing. */
    private static String forms(GlossaryTerm term) {
        if (term.translationForms().isEmpty()) {
            return "";
        }
        return " (or \"" + String.join("\", \"", term.translationForms()) + "\")";
    }

    /** One rule as one line for the model. */
    static String describe(WritingRule rule) {
        String language = rule.language() == null || rule.language().isBlank() ? "" : " [" + rule.language() + "]";
        String why = rule.justification() == null || rule.justification().isBlank() ? "" : " Why: " + rule.justification().trim();
        String blocking = rule.severity() == WritingRule.Severity.BLOCKING ? " (required by the brand)" : "";
        String what;
        boolean regex = rule.matchMode() == WritingRule.MatchMode.REGEX;
        String term = rule.pattern().endsWith("*") ? rule.pattern().substring(0, rule.pattern().length() - 1) : rule.pattern();
        String quoted = regex ? "text that matches the pattern /" + rule.pattern() + "/"
                : "\"" + term + "\"" + (rule.pattern().endsWith("*") ? " and every word that starts with it" : "");
        switch (rule.kind()) {
            case REPLACE:
                what = "Write \"" + rule.replacement() + "\" instead of " + quoted + ".";
                break;
            case MANDATORY:
                what = "A whole page always contains " + quoted + ".";
                break;
            case STYLE:
                what = "Style: avoid " + quoted
                        + (rule.replacement() == null || rule.replacement().isBlank() ? "." : "; write \"" + rule.replacement() + "\".");
                break;
            case FORBIDDEN:
            default:
                what = "Avoid " + quoted + ".";
                break;
        }
        return what + language + blocking + why + examples(" Wrong: ", rule.wrongExamples())
                + examples(" Right: ", rule.rightExamples());
    }

    /** The first examples of a rule, each one cut, so that the examples of one rule never fill the budget. */
    static String examples(String label, List<String> examples) {
        if (examples.isEmpty()) {
            return "";
        }
        List<String> quoted = new ArrayList<>();
        for (String example : examples.subList(0, Math.min(MAX_EXAMPLES, examples.size()))) {
            String text = example.strip().replaceAll("\\s+", " ");
            quoted.add("\"" + (text.length() > MAX_EXAMPLE_CHARS ? text.substring(0, MAX_EXAMPLE_CHARS) + "..." : text) + "\"");
        }
        return label + String.join("; ", quoted) + ".";
    }
}

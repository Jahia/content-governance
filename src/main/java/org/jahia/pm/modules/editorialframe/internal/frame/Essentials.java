/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The essentials of a voice or a persona: the short text that every model call gets, about 300
 * tokens, whatever its budget.
 *
 * <p>The essentials that the brand manager wrote come first. Without them, a short text is its own
 * essentials. A long text gives an outline: each heading with the first sentence of its section, so
 * that the last section is never lost. For example, a voice of five pages with the sections Tone,
 * Words and Rules gives three lines, such as "- Rules: Never promise a result."</p>
 */
final class Essentials {

    static final int TOKENS = 300;
    static final int MAX_CHARS = TOKENS * 4;
    /** Below this length, a cut sentence says nothing, so the outline keeps the headings only. */
    static final int MIN_LEAD = 40;
    private static final Pattern HEADING = Pattern.compile("#{1,6}\\s+(.*?)\\s*#*");
    private static final Pattern ITEM = Pattern.compile("^(?:[-*+]|\\d{1,3}[.)])\\s+");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?…](?=\\s|$)");

    /** The text that the model gets. */
    final String text;
    /** True when the full text holds more than the essentials. */
    final boolean abridged;

    private Essentials(String text, boolean abridged) {
        this.text = text;
        this.abridged = abridged;
    }

    /** The essentials of a text, from the essentials that the brand manager wrote, or {@code null}. */
    static Essentials of(String written, String full) {
        String body = ContextBuilder.withoutTitle(full);
        String essentials = ContextBuilder.withoutTitle(written);
        if (!essentials.isEmpty()) {
            return new Essentials(fit(essentials, MAX_CHARS), !essentials.equals(body));
        }
        if (body.length() <= MAX_CHARS) {
            return new Essentials(body, false);
        }
        return new Essentials(outline(body), true);
    }

    /** Each heading with the first sentence of its section. A text with no heading has a section per paragraph. */
    static String outline(String body) {
        List<String[]> sections = sections(body);
        String whole = render(sections, Integer.MAX_VALUE);
        if (whole.length() <= MAX_CHARS) {
            return whole;
        }
        // Each section gets the same share, so that the last ones keep their place. A line is
        // "- heading: lead" and its line break.
        int headings = 0;
        int leads = 0;
        for (String[] section : sections) {
            headings += section[0] == null ? 3 : section[0].length() + 5;
            leads += section[1].isEmpty() ? 0 : 1;
        }
        int share = leads == 0 ? 0 : (MAX_CHARS - headings) / leads;
        boolean headed = sections.stream().anyMatch(section -> section[0] != null);
        int lead = share >= MIN_LEAD || !headed ? Math.max(share, MIN_LEAD) : 0;
        return fit(render(sections, lead), MAX_CHARS);
    }

    /** The sections of a Markdown text, each as its heading, or {@code null}, and the first sentence of its text. */
    private static List<String[]> sections(String body) {
        String[] lines = body.split("\n");
        boolean headed = false;
        for (String line : lines) {
            headed |= HEADING.matcher(line.strip()).matches();
        }
        List<String[]> sections = new ArrayList<>();
        String heading = null;
        String lead = null;
        for (String line : lines) {
            String trimmed = line.strip();
            Matcher title = HEADING.matcher(trimmed);
            if (title.matches()) {
                add(sections, heading, lead);
                heading = title.group(1);
                lead = null;
            } else if (trimmed.isEmpty()) {
                if (!headed && lead != null) {
                    add(sections, null, lead);
                    lead = null;
                }
            } else if (lead == null) {
                lead = firstSentence(ITEM.matcher(trimmed).replaceFirst(""));
            }
        }
        add(sections, heading, lead);
        return sections;
    }

    private static void add(List<String[]> sections, String heading, String lead) {
        if (heading != null || lead != null) {
            sections.add(new String[]{heading, lead == null ? "" : lead});
        }
    }

    private static String render(List<String[]> sections, int leadChars) {
        StringBuilder out = new StringBuilder();
        for (String[] section : sections) {
            String lead = leadChars == 0 ? "" : cut(section[1], leadChars);
            if (section[0] == null && lead.isEmpty()) {
                continue;
            }
            String afterHeading = lead.isEmpty() ? "" : ": " + lead;
            out.append(out.isEmpty() ? "" : "\n").append("- ")
                    .append(section[0] == null ? lead : section[0] + afterHeading);
        }
        return out.toString();
    }

    private static String firstSentence(String line) {
        Matcher end = SENTENCE_END.matcher(line);
        return end.find() ? line.substring(0, end.end()) : line;
    }

    /** The whole lines of a text that fit in a length, or the first line cut at a word. */
    static String fit(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            int added = (out.isEmpty() ? 0 : 1) + line.length();
            if (out.length() + added > max) {
                break;
            }
            out.append(out.isEmpty() ? "" : "\n").append(line);
        }
        return out.isEmpty() ? cut(text.strip(), max) : out.toString().strip();
    }

    /** A text cut at the last word that fits in a length, with an ellipsis. */
    private static String cut(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = text.lastIndexOf(' ', max - 1);
        return text.substring(0, end < max / 2 ? max - 1 : end).strip() + "…";
    }
}

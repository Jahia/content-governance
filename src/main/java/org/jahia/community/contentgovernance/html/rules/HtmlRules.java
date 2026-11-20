/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The checks of each {@link HtmlRule}, on HTML that needs no browser engine. Colour contrast, keyboard
 * focus and web vitals need one, so the browser keeps them.
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class HtmlRules {

    public static final int MAX_FINDINGS = 50;
    private static final int SNIPPET_CHARS = 160;
    private static final String ARIA_LABEL = "aria-label";
    private static final String TITLE = "title";
    /** Link texts that say nothing about the target, in the three languages of the jContent screens. */
    private static final Set<String> VAGUE_LINK_TEXTS = Set.of("click here", "here", "read more", "more", "learn more",
            "link", "see more", "view more", "submit", "send", "go", "ok", "continue", "next",
            "cliquez ici", "ici", "en savoir plus", "lire la suite", "plus", "voir plus", "découvrir", "envoyer",
            "soumettre", "valider", "continuer", "suivant",
            "hier klicken", "hier", "mehr", "weiterlesen", "mehr erfahren");

    private HtmlRules() {
    }

    /**
     * @param html             the rendered HTML, of a whole page or of one item
     * @param scope            what the HTML holds: a rule of the whole page runs only on a page
     * @param expectedLanguage the language of the content, such as {@code en}, or {@code null}
     * @return the findings, at most {@link #MAX_FINDINGS}
     */
    public static List<HtmlFinding> check(Document html, Scope scope, String expectedLanguage) {
        Findings findings = new Findings(scope);
        if (scope == Scope.PAGE) {
            checkPage(html, expectedLanguage, findings);
        }
        for (Element image : html.select("img:not([alt])")) {
            findings.add(HtmlRule.IMAGE_ALT_MISSING, image);
        }
        checkHeadings(html, findings);
        checkLinks(html, findings);
        checkButtons(html, findings);
        checkFields(html, findings);
        for (Element frame : html.select("iframe:not([title]), iframe[title='']")) {
            findings.add(HtmlRule.FRAME_TITLE_MISSING, frame);
        }
        checkTables(html, findings);
        if (scope == Scope.PAGE) {
            checkDuplicateIds(html, findings);
        }
        return findings.list;
    }

    private static void checkLinks(Document html, Findings findings) {
        for (Element link : html.select("a[href]")) {
            String name = accessibleName(link);
            if (name.isEmpty()) {
                findings.add(HtmlRule.LINK_NAME_MISSING, link);
            } else if (VAGUE_LINK_TEXTS.contains(label(name).toLowerCase(Locale.ROOT))) {
                findings.add(HtmlRule.LINK_TEXT_VAGUE, link);
            }
        }
    }

    private static void checkButtons(Document html, Findings findings) {
        for (Element button : html.select("button, input[type=button], input[type=submit], input[type=reset]")) {
            String name = button.tagName().equals("input") ? button.attr("value").trim() : accessibleName(button);
            if (name.isEmpty() && button.attr(ARIA_LABEL).isBlank() && button.attr(TITLE).isBlank()) {
                findings.add(HtmlRule.BUTTON_NAME_MISSING, button);
            }
        }
    }

    private static void checkFields(Document html, Findings findings) {
        for (Element field : html.select("input:not([type=hidden]):not([type=button]):not([type=submit])"
                + ":not([type=reset]):not([type=image]), select, textarea")) {
            if (!hasLabel(html, field)) {
                findings.add(HtmlRule.FORM_LABEL_MISSING, field);
            }
        }
    }

    private static void checkTables(Document html, Findings findings) {
        for (Element table : html.select("table:not([role=presentation])")) {
            if (table.select("tr").size() > 1 && table.select("th").isEmpty()
                    && table.select("tr").first().select("td").size() > 1) {
                findings.add(HtmlRule.TABLE_HEADER_MISSING, table);
            }
        }
    }

    private static void checkPage(Document page, String expectedLanguage, Findings findings) {
        Element root = page.selectFirst("html");
        String lang = root == null ? "" : root.attr("lang").trim();
        if (lang.isEmpty()) {
            findings.add(HtmlRule.PAGE_LANGUAGE_MISSING, root);
        } else if (expectedLanguage != null && expectedLanguage.length() >= 2 && !lang.toLowerCase(Locale.ROOT)
                .startsWith(expectedLanguage.toLowerCase(Locale.ROOT).substring(0, 2))) {
            findings.add(HtmlRule.PAGE_LANGUAGE_MISMATCH, root);
        }
    }

    private static void checkHeadings(Document html, Findings findings) {
        Elements headings = html.select("h1, h2, h3, h4, h5, h6");
        int h1 = 0;
        int previous = 0;
        for (Element heading : headings) {
            int level = heading.tagName().charAt(1) - '0';
            if (level == 1) {
                h1++;
            }
            if (previous > 0 && level > previous + 1) {
                findings.add(HtmlRule.HEADING_LEVEL_SKIPPED, heading);
            }
            if (heading.text().isBlank() && heading.select("img[alt]").isEmpty()) {
                findings.add(HtmlRule.HEADING_EMPTY, heading);
            }
            previous = level;
        }
        if (h1 == 0 && !headings.isEmpty()) {
            findings.add(HtmlRule.HEADING_H1_MISSING, headings.first());
        } else if (h1 > 1) {
            findings.add(HtmlRule.HEADING_H1_MULTIPLE, html.select("h1").get(1));
        }
    }

    private static void checkDuplicateIds(Document page, Findings findings) {
        Map<String, Integer> ids = new HashMap<>();
        for (Element element : page.select("[id]")) {
            ids.merge(element.id(), 1, Integer::sum);
        }
        ids.forEach((id, count) -> {
            if (count > 1) {
                findings.add(HtmlRule.DUPLICATE_ID, page.getElementById(id));
            }
        });
    }

    /** A link text without what decorates it at either end: "Read more +" gives "Read more". */
    static String label(String text) {
        String label = text.replaceAll("\\s+", " ");
        int start = 0;
        int end = label.length();
        while (start < end && !Character.isLetterOrDigit(label.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(label.charAt(end - 1))) {
            end--;
        }
        return label.substring(start, end);
    }

    /** The text that a screen reader reads for an element, from its content and its labels. */
    static String accessibleName(Element element) {
        String label = element.attr(ARIA_LABEL).trim();
        if (!label.isEmpty()) {
            return label;
        }
        StringBuilder name = new StringBuilder(element.text().trim());
        for (Element image : element.select("img[alt]")) {
            name.append(' ').append(image.attr("alt").trim());
        }
        String text = name.toString().trim();
        return text.isEmpty() ? element.attr(TITLE).trim() : text;
    }

    private static boolean hasLabel(Document html, Element field) {
        if (!field.attr(ARIA_LABEL).isBlank() || !field.attr("aria-labelledby").isBlank()
                || !field.attr(TITLE).isBlank()) {
            return true;
        }
        if (!field.id().isEmpty() && html.selectFirst("label[for=" + cssEscape(field.id()) + "]") != null) {
            return true;
        }
        return field.closest("label") != null;
    }

    private static String cssEscape(String id) {
        return "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** The findings of one check: the rules of the scope only, up to the maximum. */
    private static final class Findings {
        private final Scope scope;
        private final List<HtmlFinding> list = new ArrayList<>();

        Findings(Scope scope) {
            this.scope = scope;
        }

        void add(HtmlRule rule, Element element) {
            if (list.size() >= MAX_FINDINGS || !rule.runsOn(scope)) {
                return;
            }
            String snippet = element == null ? "" : element.clone().empty().outerHtml();
            if (snippet.length() > SNIPPET_CHARS) {
                snippet = snippet.substring(0, SNIPPET_CHARS) + "…";
            }
            list.add(new HtmlFinding(rule, snippet));
        }
    }
}

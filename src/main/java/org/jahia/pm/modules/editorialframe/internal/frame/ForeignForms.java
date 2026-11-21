/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.community.contentgovernance.text.FoldedText;
import org.jahia.pm.modules.editorialframe.api.WritingRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules that report, in a text of one language, a form of a glossary term that belongs to another
 * language. The glossary rules that translate one term, such as "Frodo" ({@code en}) to "Frodon" in
 * {@code fr} and to "Frodo" in {@code es}, hold the form of the term in each language. A Spanish text
 * that holds "Frodon" then gets a finding, with the replacement "Frodo".
 *
 * <p>The finding names the rule that holds the form found. A text of a language that has no form of the
 * term gets no such finding, because the form to write is unknown.</p>
 */
final class ForeignForms {

    private ForeignForms() {
    }

    /**
     * @param rules    the rules of a site, of every language
     * @param language the language of the text, such as {@code es}
     * @return the rules, then one rule for each glossary rule of another language whose forms the language
     * does not share
     */
    static List<WritingRule> with(List<WritingRule> rules, String language) {
        if (language == null || language.isBlank()) {
            return rules;
        }
        String text = base(language);
        List<WritingRule> out = new ArrayList<>(rules);
        for (List<WritingRule> term : terms(rules).values()) {
            List<String> own = forms(term, text);
            if (own.isEmpty()) {
                continue;
            }
            // The forms already reported: the forms of the language, and the term itself, which the rules of
            // the language report, such as "Frodo" in a French text. A form that two other languages share,
            // such as "Frodon", is reported once, by the first rule that holds it.
            Set<String> seen = folded(own);
            term.forEach(rule -> rule.allPatternForms().forEach(form -> seen.add(FoldedText.foldString(form.trim()))));
            for (WritingRule rule : term) {
                WritingRule foreign = base(rule.language()).equals(text) ? null : foreignRule(rule, own, seen, text);
                if (foreign != null) {
                    out.add(foreign);
                }
            }
        }
        return out;
    }

    /**
     * The rule that reports the forms of a rule of another language that the language of the text does not
     * share, with the form of that language as replacement, or null when the forms are the same.
     */
    private static WritingRule foreignRule(WritingRule rule, List<String> own, Set<String> seen, String language) {
        List<String> foreign = new ArrayList<>();
        for (String form : rule.allReplacementForms()) {
            if (seen.add(FoldedText.foldString(form.trim()))) {
                foreign.add(form.trim());
            }
        }
        if (foreign.isEmpty()) {
            return null;
        }
        return new WritingRule(rule.id(), WritingRule.Kind.REPLACE, foreign.get(0), WritingRule.MatchMode.TERM, own.get(0),
                language, rule.severity(), rule.justification(), rule.scope(), List.of(), List.of(), rule.owner(), null,
                null, null, foreign.subList(1, foreign.size()), own.subList(1, own.size()), null, rule.folder());
    }

    /** The glossary rules, by term: the folded term and the language of the term. */
    private static Map<String, List<WritingRule>> terms(List<WritingRule> rules) {
        Map<String, List<WritingRule>> terms = new LinkedHashMap<>();
        for (WritingRule rule : rules) {
            boolean glossary = rule.kind() == WritingRule.Kind.REPLACE && rule.matchMode() == WritingRule.MatchMode.TERM
                    && !blank(rule.patternLanguage()) && !blank(rule.language()) && !blank(rule.replacement());
            if (glossary) {
                String key = FoldedText.foldString(rule.pattern().trim()) + '|' + base(rule.patternLanguage());
                terms.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            }
        }
        return terms;
    }

    /** The forms of the term in a language: the term itself in its own language, else the replacements of that language. */
    private static List<String> forms(List<WritingRule> term, String language) {
        Set<String> forms = new LinkedHashSet<>();
        for (WritingRule rule : term) {
            if (base(rule.patternLanguage()).equals(language)) {
                rule.allPatternForms().forEach(form -> forms.add(form.trim()));
            } else if (base(rule.language()).equals(language)) {
                rule.allReplacementForms().forEach(form -> forms.add(form.trim()));
            }
        }
        return List.copyOf(forms);
    }

    private static Set<String> folded(List<String> forms) {
        Set<String> out = new LinkedHashSet<>();
        forms.forEach(form -> out.add(FoldedText.foldString(form)));
        return out;
    }

    /** {@code fr} and {@code fr_FR} are one language for a glossary. */
    private static String base(String language) {
        return language == null ? "" : language.trim().split("[_-]")[0].toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.community.contentgovernance.text.FoldedText;
import org.jahia.community.contentgovernance.text.TermPattern;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks a translation against the glossary, with no model. Where the source holds a term of the
 * glossary, in any of its forms, the translation must hold its rendering, in any of its forms, and
 * none of its forbidden translations. For example, with the term "access token" (en) and its
 * translation "jeton d'accès" (fr), a French text that translates "Create access tokens" passes
 * with "jetons d'accès". With "jeton d'authentification" as a forbidden translation of the term, a
 * text that holds it breaks the glossary, and the report names "jeton d'accès" to write instead.
 *
 * <p>The forms of a term add up over its entries: a form of "access token" given on its approved
 * entry also finds the term for its forbidden entry. A term whose forbidden translation is found
 * is not reported missing too.</p>
 *
 * <p>The check counts no occurrence: a translation that holds the rendering once passes, however
 * often the source holds the term. A translation that is cut is not checked, because the part left
 * out may hold the rendering.</p>
 */
public final class GlossaryChecker {

    private GlossaryChecker() {
    }

    public static List<RuleViolation> check(List<GlossaryTerm> terms, String sourceLanguage, String sourceHtml,
                                            String language, String html) {
        List<RuleViolation> found = new ArrayList<>();
        if (sourceHtml == null || html == null) {
            return found;
        }
        String source = Texts.plain(sourceHtml);
        String text = Texts.plain(html);
        if (text.length() > RuleChecker.MAX_TEXT) {
            return found;
        }
        if (source.length() > RuleChecker.MAX_TEXT) {
            source = source.substring(0, RuleChecker.MAX_TEXT);
        }
        List<GlossaryTerm> pair = new ArrayList<>();
        Map<String, Set<String>> termForms = new LinkedHashMap<>();
        for (GlossaryTerm term : terms) {
            if (term.isPair(sourceLanguage, language)) {
                pair.add(term);
                termForms.computeIfAbsent(key(term), key -> new LinkedHashSet<>()).addAll(term.allTermForms());
            }
        }
        FoldedText foldedSource = FoldedText.of(source);
        FoldedText foldedText = FoldedText.of(text);
        Set<String> forbiddenFound = reportForbidden(pair, termForms, foldedSource, foldedText, text, found);
        reportMissing(pair, termForms, forbiddenFound, foldedSource, foldedText, source, found);
        return found;
    }

    /**
     * Adds a violation for each forbidden translation that the text holds where the source holds its
     * term. Returns the terms so reported.
     */
    private static Set<String> reportForbidden(List<GlossaryTerm> pair, Map<String, Set<String>> termForms,
                                               FoldedText source, FoldedText folded, String text,
                                               List<RuleViolation> found) {
        Set<String> forbiddenFound = new HashSet<>();
        for (GlossaryTerm term : pair) {
            if (!term.forbidden() || first(termForms.get(key(term)), source) == null) {
                continue;
            }
            TermPattern.Span inText = first(term.allTranslationForms(), folded);
            if (inText != null) {
                forbiddenFound.add(key(term));
                found.add(new RuleViolation(term.id(), WritingRule.Kind.GLOSSARY, WritingRule.Severity.WARNING,
                        term.term(), text.substring(inText.start(), inText.end()), approved(pair, term), term.note(),
                        RuleChecker.excerpt(text, inText.start(), inText.end())));
            }
        }
        return forbiddenFound;
    }

    /**
     * Adds a violation for each approved term that the source holds and whose rendering the text
     * lacks, unless a forbidden translation of the term was reported.
     */
    private static void reportMissing(List<GlossaryTerm> pair, Map<String, Set<String>> termForms,
                                      Set<String> forbiddenFound, FoldedText folded, FoldedText text, String source,
                                      List<RuleViolation> found) {
        for (GlossaryTerm term : pair) {
            if (term.forbidden() || forbiddenFound.contains(key(term))) {
                continue;
            }
            TermPattern.Span inSource = first(termForms.get(key(term)), folded);
            if (inSource != null && first(term.allTranslationForms(), text) == null) {
                found.add(new RuleViolation(term.id(), WritingRule.Kind.GLOSSARY, WritingRule.Severity.WARNING,
                        term.term(), null, term.translation(), term.note(),
                        RuleChecker.excerpt(source, inSource.start(), inSource.end())));
            }
        }
    }

    /** The first place of a form in a folded text, or null. */
    private static TermPattern.Span first(Collection<String> forms, FoldedText text) {
        TermPattern pattern = TermPattern.of(forms);
        return pattern == null ? null : pattern.first(text);
    }

    /** One term, whatever its case, for one language pair. */
    private static String key(GlossaryTerm term) {
        return term.term().toLowerCase(Locale.ROOT);
    }

    /** The approved translation of the term of a forbidden entry, or {@code null}. */
    private static String approved(List<GlossaryTerm> pair, GlossaryTerm forbidden) {
        for (GlossaryTerm term : pair) {
            if (!term.forbidden() && key(term).equals(key(forbidden))) {
                return term.translation();
            }
        }
        return null;
    }
}

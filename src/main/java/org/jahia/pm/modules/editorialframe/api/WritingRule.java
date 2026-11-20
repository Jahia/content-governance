/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A rule that code checks on a text. The language is a code such as {@code fr}, or {@code null} for
 * every language. The scope is {@code group} or {@code site}. A rule may carry wrong and right
 * examples for the model, its owner, the day of its decision, and the days between which it applies.
 *
 * @param scope where the rule comes from: {@code group} or {@code site}
 * @param wrongExamples texts that break the rule. Empty when none is given
 * @param rightExamples texts that keep the rule, such as the right form of a wrong example. Empty when
 *                      none is given
 * @param owner who decided the rule, or {@code null}
 * @param decidedOn the day of the decision, or {@code null}
 * @param validFrom the first day on which the rule applies, or {@code null} for no start
 * @param validUntil the last day on which the rule applies, or {@code null} for no end
 * @param patternForms the other forms of the pattern of a term, such as the plural "access tokens". Never
 *                     {@code null}
 * @param replacementForms the other forms of the replacement, such as the plural "jetons d'accès". Never
 *                         {@code null}
 * @param patternLanguage the language of the pattern when it is another language than the language of the
 *                        rule: a glossary term to translate, such as "access token" ({@code en}) for a rule of
 *                        {@code fr}. {@code null} for a pattern in the language of the rule
 * @param folder the path of the folder that holds the rule, or {@code null}
 */
public record WritingRule(String id, Kind kind, String pattern, MatchMode matchMode, String replacement, String language,
                          Severity severity, String justification, String scope, List<String> wrongExamples,
                          List<String> rightExamples, String owner, LocalDate decidedOn, LocalDate validFrom,
                          LocalDate validUntil, List<String> patternForms, List<String> replacementForms,
                          String patternLanguage, String folder) {

    /** What the rule asks. */
    public enum Kind {
        /** The pattern never appears. */
        FORBIDDEN,
        /** The pattern never appears, and the replacement takes its place. */
        REPLACE,
        /** The pattern appears in a whole document. */
        MANDATORY,
        /** A pattern of style that never appears, such as a space before a comma. */
        STYLE,
        /**
         * A term of the glossary: where the source of a translation holds the term, the translation
         * holds its rendering. Only the check of a translation reports it, and no writing rule has it.
         */
        GLOSSARY;

        public static Kind parse(String value) {
            return value == null ? FORBIDDEN : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** How the pattern matches. */
    public enum MatchMode {
        /** A whole word or phrase, ignoring case. */
        TERM,
        /** A Java regular expression. */
        REGEX;

        public static MatchMode parse(String value) {
            return value == null ? TERM : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** How much a broken rule matters. Neither value blocks a write: the editor decides. */
    public enum Severity {
        WARNING,
        BLOCKING;

        public static Severity parse(String value) {
            return value == null ? WARNING : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public WritingRule {
        wrongExamples = wrongExamples == null ? List.of() : List.copyOf(wrongExamples);
        rightExamples = rightExamples == null ? List.of() : List.copyOf(rightExamples);
        patternForms = patternForms == null ? List.of() : List.copyOf(patternForms);
        replacementForms = replacementForms == null ? List.of() : List.copyOf(replacementForms);
    }

    /** A rule with no other form, in the language of the rule, in no folder. */
    public WritingRule(String id, Kind kind, String pattern, MatchMode matchMode, String replacement, String language,
                       Severity severity, String justification, String scope, List<String> wrongExamples,
                       List<String> rightExamples, String owner, LocalDate decidedOn, LocalDate validFrom,
                       LocalDate validUntil) {
        this(id, kind, pattern, matchMode, replacement, language, severity, justification, scope, wrongExamples,
                rightExamples, owner, decidedOn, validFrom, validUntil, null, null, null, null);
    }

    /** The pattern, then its other forms. */
    public List<String> allPatternForms() {
        return all(pattern, patternForms);
    }

    /** The replacement, then its other forms, or none when the rule has no replacement. */
    public List<String> allReplacementForms() {
        return replacement == null || replacement.isBlank() ? List.of() : all(replacement, replacementForms);
    }

    private static List<String> all(String first, List<String> others) {
        List<String> all = new ArrayList<>(others.size() + 1);
        all.add(first);
        all.addAll(others);
        return all;
    }

    /** A copy of the rule with texts that break it and texts that keep it. */
    public WritingRule withExamples(List<String> wrong, List<String> right) {
        return new WritingRule(id, kind, pattern, matchMode, replacement, language, severity, justification, scope, wrong,
                right, owner, decidedOn, validFrom, validUntil, patternForms, replacementForms, patternLanguage, folder);
    }

    /** A copy of the rule with who decided it, such as a name or a team, and on which day. */
    public WritingRule withDecision(String decidedBy, LocalDate day) {
        return new WritingRule(id, kind, pattern, matchMode, replacement, language, severity, justification, scope,
                wrongExamples, rightExamples, decidedBy, day, validFrom, validUntil, patternForms, replacementForms,
                patternLanguage, folder);
    }

    /** A copy of the rule that applies from the first day to the last day, both included. A null day sets no limit. */
    public WritingRule withValidity(LocalDate first, LocalDate last) {
        return new WritingRule(id, kind, pattern, matchMode, replacement, language, severity, justification, scope,
                wrongExamples, rightExamples, owner, decidedOn, first, last, patternForms, replacementForms,
                patternLanguage, folder);
    }

    /**
     * True when the rule applies on this day. For example, a rule valid from 2026-09-05 until
     * 2026-12-31 applies on both days, and no longer on 2027-01-01.
     */
    public boolean inForceOn(LocalDate day) {
        return (validFrom == null || !day.isBefore(validFrom)) && (validUntil == null || !day.isAfter(validUntil));
    }

    /** True when the rule applies to a text in this language. */
    public boolean appliesTo(String textLanguage) {
        if (language == null || language.isBlank()) {
            return true;
        }
        if (textLanguage == null) {
            return false;
        }
        String wanted = language.trim().toLowerCase(Locale.ROOT);
        String actual = textLanguage.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return actual.equals(wanted) || actual.startsWith(wanted + "-");
    }

    /** The fields of the rule, in a fixed order. */
    @Override
    public String toString() {
        return "WritingRule[" + id + "|" + kind + "|" + pattern + "|" + matchMode + "|" + replacement + "|" + language
                + "|" + severity + "|" + justification + "|" + scope + "|" + wrongExamples + "|" + rightExamples + "|"
                + owner + "|" + decidedOn + "|" + validFrom + "|" + validUntil + "|" + patternForms + "|" + replacementForms
                + "|" + patternLanguage + "|" + folder + "]";
    }
}

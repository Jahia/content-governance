/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks the writing rules on a text, with no model. A term matches as a whole word or phrase,
 * ignoring case: "robust" matches "Robust" and not "robustness".
 */
public final class RuleChecker {

    private static final Logger logger = LoggerFactory.getLogger(RuleChecker.class);
    /** A longer text is cut: a regex of a rule must not run on megabytes. */
    static final int MAX_TEXT = 100_000;
    private static final int EXCERPT = 40;
    /** At most this many matches of one rule are reported. */
    private static final int MAX_MATCHES_PER_RULE = 3;
    /** A rule pattern that runs longer than this on one text is stopped: a regex can backtrack for hours. */
    static final long MAX_RULE_NANOS = 200_000_000L;

    private RuleChecker() {
    }

    public static List<RuleViolation> check(List<WritingRule> rules, String language, String html, boolean document) {
        List<RuleViolation> found = new ArrayList<>();
        String text = Texts.plain(html);
        boolean cut = text.length() > MAX_TEXT;
        if (cut) {
            text = text.substring(0, MAX_TEXT);
        }
        for (WritingRule rule : rules) {
            if (!rule.appliesTo(language)) {
                continue;
            }
            Pattern pattern = compile(rule);
            if (pattern == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(new Deadline(text, System.nanoTime() + MAX_RULE_NANOS));
            if (rule.kind() == Kind.MANDATORY) {
                // The part of a cut text that is left out may hold the term, so only a whole text lacks it.
                if (document && !cut && !findWithin(matcher, rule)) {
                    found.add(new RuleViolation(rule.id(), rule.kind(), rule.severity(), rule.pattern(), null,
                            rule.replacement(), rule.justification(), null));
                }
                continue;
            }
            List<int[]> allowed = replacementSpans(rule, text);
            int matches = 0;
            try {
                while (matcher.find() && matches < MAX_MATCHES_PER_RULE) {
                    if (inside(allowed, matcher.start(), matcher.end())) {
                        // "mail" inside "e-mail", when the rule says to write "e-mail" instead of "mail".
                        continue;
                    }
                    matches++;
                    found.add(new RuleViolation(rule.id(), rule.kind(), rule.severity(), rule.pattern(), matcher.group(),
                            rule.replacement(), rule.justification(), excerpt(text, matcher.start(), matcher.end())));
                }
            } catch (Deadline.Expired | StackOverflowError e) {
                // A pattern that backtracks without end either runs out of time or out of stack.
                logger.warn("The writing rule {} is too costly on a text, so it is skipped", rule.id());
            }
        }
        return found;
    }

    private static boolean findWithin(Matcher matcher, WritingRule rule) {
        try {
            return matcher.find();
        } catch (Deadline.Expired | StackOverflowError e) {
            logger.warn("The writing rule {} is too costly on a text, so it is skipped", rule.id());
            // A rule that cannot be checked reports nothing.
            return true;
        }
    }

    /** A text that stops a regex when its time is over: the matcher reads it one character at a time. */
    static final class Deadline implements CharSequence {
        private final CharSequence text;
        private final long end;

        Deadline(CharSequence text, long end) {
            this.text = text;
            this.end = end;
        }

        @Override
        public char charAt(int index) {
            if ((index & 0xFF) == 0 && System.nanoTime() > end) {
                throw new Expired();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int stop) {
            return new Deadline(text.subSequence(start, stop), end);
        }

        @Override
        public String toString() {
            return text.toString();
        }

        static final class Expired extends RuntimeException {
            private static final long serialVersionUID = 1L;

            Expired() {
                super(null, null, false, false);
            }
        }
    }

    /** Where the replacement of a rule appears in the text: a match inside it is the right term. */
    static List<int[]> replacementSpans(WritingRule rule, String text) {
        List<int[]> spans = new ArrayList<>();
        String replacement = rule.replacement();
        if (rule.kind() != Kind.REPLACE || replacement == null || replacement.isBlank()) {
            return spans;
        }
        Matcher found = Pattern.compile(Pattern.quote(replacement.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text);
        while (found.find()) {
            spans.add(new int[]{found.start(), found.end()});
        }
        return spans;
    }

    private static boolean inside(List<int[]> spans, int start, int end) {
        for (int[] span : spans) {
            if (start >= span[0] && end <= span[1]) {
                return true;
            }
        }
        return false;
    }

    /** The pattern of a rule, or {@code null} for a regex that does not compile. */
    static Pattern compile(WritingRule rule) {
        if (rule.pattern() == null || rule.pattern().isBlank()) {
            return null;
        }
        if (rule.matchMode() == WritingRule.MatchMode.REGEX) {
            try {
                return Pattern.compile(rule.pattern(), Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException e) {
                logger.warn("The writing rule {} has a pattern that does not compile: {}", rule.id(), e.getDescription());
                return null;
            }
        }
        return termPattern(rule.pattern());
    }

    /**
     * The pattern of a term: a whole word or phrase, ignoring case. A space in a term also matches a
     * non-breaking space or a line break, and an apostrophe matches a straight or a curly one. A term
     * that ends with * is a stem: "seamless*" also matches "seamlessly".
     */
    static Pattern termPattern(String term) {
        return termPattern(List.of(term));
    }

    /**
     * The pattern of the forms of a term, such as "jeton d'accès" and "jetons d'accès": any of them,
     * as {@link #termPattern(String)} matches one. The longest form is tried first, so a match is
     * the whole form.
     */
    static Pattern termPattern(List<String> forms) {
        StringJoiner alternatives = new StringJoiner("|", "(?:", ")");
        forms.stream().map(String::trim).filter(form -> !form.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .forEach(form -> alternatives.add(termRegex(form)));
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + alternatives + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String termRegex(String term) {
        boolean stem = term.endsWith("*") && term.length() > 1;
        String text = stem ? term.substring(0, term.length() - 1) : term;
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String special = c == ' ' ? "\\s+" : c == '\'' || c == '’' ? "['’]" : null;
            if (special == null) {
                literal.append(c);
                continue;
            }
            if (!literal.isEmpty()) {
                regex.append(Pattern.quote(literal.toString()));
                literal.setLength(0);
            }
            regex.append(special);
        }
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return regex + (stem ? "[\\p{L}\\p{N}]*" : "");
    }

    static String excerpt(String text, int start, int end) {
        int from = Math.max(0, start - EXCERPT);
        int to = Math.min(text.length(), end + EXCERPT);
        String excerpt = text.substring(from, to).replaceAll("\\s+", " ").trim();
        return (from > 0 ? "..." : "") + excerpt + (to < text.length() ? "..." : "");
    }
}

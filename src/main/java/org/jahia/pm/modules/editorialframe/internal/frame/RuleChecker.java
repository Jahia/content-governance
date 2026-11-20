/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.community.contentgovernance.text.FoldedText;
import org.jahia.community.contentgovernance.text.TermPattern;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks the writing rules on a text, with no model. A term matches as {@link TermPattern} states: a
 * whole word or phrase, in any case and with or without accents. "robust" matches "Robust" and not
 * "robustness". A regex matches the text as it is written.
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
    private static final String TOO_COSTLY = "The writing rule {} is too costly on a text, so it is skipped";

    private RuleChecker() {
    }

    /**
     * A place where a plain text breaks a rule.
     *
     * @param rule  the rule
     * @param start where the match starts in the text
     * @param end   where it ends, excluded
     */
    public record Match(WritingRule rule, int start, int end) {
    }

    /**
     * The rules that one language can break, compiled once to check many plain texts: the forbidden,
     * replace and style rules of the language. A mandatory rule needs a whole document, so the finder
     * leaves it out. Example: a scan of a site checks each text of the site with one finder.
     *
     * <p>Thread safety: immutable.</p>
     */
    public static final class Finder {
        private final List<Compiled> compiled = new ArrayList<>();
        private final int maxPerRule;

        /**
         * @param rules      the rules to apply
         * @param language   the language of the texts, such as {@code fr}
         * @param maxPerRule the most matches of one rule in one text
         */
        public Finder(List<WritingRule> rules, String language, int maxPerRule) {
            this.maxPerRule = maxPerRule;
            for (WritingRule rule : rules) {
                Compiled one = rule.kind() == Kind.MANDATORY || !rule.appliesTo(language) ? null : Compiled.of(rule);
                if (one != null) {
                    compiled.add(one);
                }
            }
        }

        public boolean isEmpty() {
            return compiled.isEmpty();
        }

        /**
         * @param plain a plain text, which is cut after 100,000 characters
         * @return the matches, rule after rule
         */
        public List<Match> find(String plain) {
            if (plain == null || plain.isEmpty()) {
                return List.of();
            }
            Searched searched = new Searched(plain.length() > MAX_TEXT ? plain.substring(0, MAX_TEXT) : plain);
            List<Match> found = new ArrayList<>();
            for (Compiled one : compiled) {
                found.addAll(one.matches(searched, maxPerRule));
            }
            return found;
        }
    }

    public static List<RuleViolation> check(List<WritingRule> rules, String language, String html, boolean document) {
        List<RuleViolation> found = new ArrayList<>();
        String text = Texts.plain(html);
        boolean cut = text.length() > MAX_TEXT;
        if (cut) {
            text = text.substring(0, MAX_TEXT);
        }
        Searched searched = new Searched(text);
        for (WritingRule rule : rules) {
            Compiled compiled = rule.appliesTo(language) ? Compiled.of(rule) : null;
            if (compiled == null) {
                continue;
            }
            if (rule.kind() != Kind.MANDATORY) {
                for (Match match : compiled.matches(searched, MAX_MATCHES_PER_RULE)) {
                    found.add(violation(rule, text.substring(match.start(), match.end()),
                            excerpt(text, match.start(), match.end())));
                }
            } else if (document && !cut && !compiled.found(searched)) {
                // The part of a cut text that is left out may hold the term, so only a whole text lacks it.
                found.add(violation(rule, null, null));
            }
        }
        return found;
    }

    private static RuleViolation violation(WritingRule rule, String match, String excerpt) {
        return new RuleViolation(rule.id(), rule.kind(), rule.severity(), rule.pattern(), match, rule.replacement(),
                rule.justification(), excerpt, rule.folder());
    }

    /**
     * @param rule  a rule
     * @param plain a plain text
     * @return true when the text breaks the rule, for a rule that is not mandatory
     */
    static boolean appears(WritingRule rule, String plain) {
        Compiled compiled = rule.kind() == Kind.MANDATORY || plain.isEmpty() ? null : Compiled.of(rule);
        return compiled != null && !compiled.matches(new Searched(plain), 1).isEmpty();
    }

    /**
     * @param forms the forms of a term
     * @param plain a plain text
     * @return the first place of a form in the text, or null
     */
    static TermPattern.Span first(List<String> forms, String plain) {
        TermPattern term = TermPattern.of(forms);
        return term == null ? null : term.first(FoldedText.of(plain));
    }

    /** A plain text, and its folded form, which a term reads. The text is folded when a term first asks. */
    static final class Searched {
        private final String text;
        private FoldedText folded;

        Searched(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }

        FoldedText folded() {
            if (folded == null) {
                folded = FoldedText.of(text);
            }
            return folded;
        }
    }

    /**
     * One rule, compiled: a term that reads the folded text, or a regex that reads the text, and the forms
     * of its replacement. A scan of a site reads thousands of texts with one compiled rule.
     */
    private record Compiled(WritingRule rule, TermPattern term, Pattern regex, List<Pattern> replacements) {

        /** The compiled rule, or null for a rule with no pattern or with a regex that does not compile. */
        static Compiled of(WritingRule rule) {
            if (rule.pattern() == null || rule.pattern().isBlank()) {
                return null;
            }
            if (rule.matchMode() == WritingRule.MatchMode.REGEX) {
                try {
                    return new Compiled(rule, null, Pattern.compile(rule.pattern(), Pattern.UNICODE_CASE),
                            replacementPatterns(rule));
                } catch (PatternSyntaxException e) {
                    logger.warn("The writing rule {} has a pattern that does not compile: {}", rule.id(), e.getDescription());
                    return null;
                }
            }
            TermPattern term = TermPattern.of(rule.allPatternForms());
            return term == null ? null : new Compiled(rule, term, null, replacementPatterns(rule));
        }

        /** The places where the text breaks the rule, at most {@code max}. */
        List<Match> matches(Searched searched, int max) {
            Spans allowed = new Spans(replacements, searched.text());
            List<Match> found = new ArrayList<>();
            long end = System.nanoTime() + MAX_RULE_NANOS;
            try {
                if (term != null) {
                    FoldedText folded = searched.folded();
                    Matcher matcher = term.pattern().matcher(new Deadline(folded.text(), end));
                    while (found.size() < max && matcher.find()) {
                        TermPattern.Span span = TermPattern.span(folded, matcher.start(), matcher.end());
                        add(found, allowed, span.start(), span.end());
                    }
                } else {
                    Matcher matcher = regex.matcher(new Deadline(searched.text(), end));
                    while (found.size() < max && matcher.find()) {
                        add(found, allowed, matcher.start(), matcher.end());
                    }
                }
            } catch (Deadline.Expired | StackOverflowError e) {
                // A pattern that backtracks without end either runs out of time or out of stack.
                logger.warn(TOO_COSTLY, rule.id());
            }
            return found;
        }

        /** Adds a match, unless it is empty or the right term: "mail" inside "e-mail", for a rule that says to write "e-mail". */
        private void add(List<Match> found, Spans allowed, int start, int end) {
            if (end > start && !allowed.covers(start, end)) {
                found.add(new Match(rule, start, end));
            }
        }

        /** True when the text holds the pattern. A rule that is too costly to check counts as found. */
        boolean found(Searched searched) {
            long end = System.nanoTime() + MAX_RULE_NANOS;
            try {
                return term != null
                        ? term.pattern().matcher(new Deadline(searched.folded().text(), end)).find()
                        : regex.matcher(new Deadline(searched.text(), end)).find();
            } catch (Deadline.Expired | StackOverflowError e) {
                logger.warn(TOO_COSTLY, rule.id());
                return true;
            }
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

    /**
     * The forms of the replacement of a replace rule, which a match inside one of them is the right term of.
     * The search ignores case and keeps accents, so "role" is wrong where the rule says to write "rôle".
     */
    private static List<Pattern> replacementPatterns(WritingRule rule) {
        List<Pattern> patterns = new ArrayList<>();
        if (rule.kind() == Kind.REPLACE) {
            for (String form : rule.allReplacementForms()) {
                if (!form.isBlank()) {
                    patterns.add(Pattern.compile(literal(form.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                }
            }
        }
        return patterns;
    }

    /**
     * Where the forms of a replacement appear in a text, searched once, at the first match of the rule:
     * "mail" inside "e-mail" is the right term, for a rule that says to write "e-mail".
     */
    private static final class Spans {
        private final List<Pattern> patterns;
        private final String text;
        private List<int[]> spans;

        Spans(List<Pattern> patterns, String text) {
            this.patterns = patterns;
            this.text = text;
        }

        boolean covers(int start, int end) {
            if (patterns.isEmpty()) {
                return false;
            }
            if (spans == null) {
                spans = new ArrayList<>();
                for (Pattern pattern : patterns) {
                    Matcher found = pattern.matcher(text);
                    while (found.find()) {
                        spans.add(new int[]{found.start(), found.end()});
                    }
                }
            }
            for (int[] span : spans) {
                if (start >= span[0] && end <= span[1]) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The regex of a text as written, where a space matches any white space and an apostrophe a straight or a curly one. */
    private static String literal(String text) {
        StringBuilder regex = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String special;
            if (Character.isWhitespace(c)) {
                special = "\\s+";
            } else if (c == '\'' || c == '’') {
                special = "['’]";
            } else {
                plain.append(c);
                continue;
            }
            if (!plain.isEmpty()) {
                regex.append(Pattern.quote(plain.toString()));
                plain.setLength(0);
            }
            regex.append(special);
        }
        if (!plain.isEmpty()) {
            regex.append(Pattern.quote(plain.toString()));
        }
        return regex.toString();
    }

    static String excerpt(String text, int start, int end) {
        int from = Math.max(0, start - EXCERPT);
        int to = Math.min(text.length(), end + EXCERPT);
        String excerpt = text.substring(from, to).replaceAll("\\s+", " ").trim();
        return (from > 0 ? "..." : "") + excerpt + (to < text.length() ? "..." : "");
    }
}

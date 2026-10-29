package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.compliance.model.GlossaryTerm;
import org.jahia.community.contentgovernance.compliance.model.MatchMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds glossary terms in plain text, ignoring case and accents. Immutable and thread-safe once built.
 */
public final class TermMatcher {

    /** A term found in a text: offsets are in the original (unfolded) text. */
    public record Match(GlossaryTerm term, int start, int end) {
    }

    private record Compiled(GlossaryTerm term, String folded, Pattern pattern) {
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    // A word character is a letter or digit of a script that separates words with spaces. Chinese,
    // Japanese, Thai, Lao, Khmer and Myanmar write words without spaces: their letters never make a
    // term part of a longer word, otherwise a whole-word term never matches inside a sentence there.
    private static final String WORD_CHAR = "[\\p{L}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}"
            + "\\p{IsThai}\\p{IsLao}\\p{IsKhmer}\\p{IsMyanmar}]]";
    private static final String NOT_AFTER_WORD = "(?<!" + WORD_CHAR + ")";
    private static final String NOT_BEFORE_WORD = "(?!" + WORD_CHAR + ")";
    private static final String WORD_REST = WORD_CHAR + "*";
    private static final Pattern IS_WORD_CHAR = Pattern.compile(WORD_CHAR, Pattern.UNICODE_CHARACTER_CLASS);

    private final List<Compiled> compiled;

    /**
     * Compiles the terms. Blank terms are ignored.
     *
     * @param terms the glossary terms to look for
     */
    public TermMatcher(Collection<GlossaryTerm> terms) {
        List<Compiled> list = new ArrayList<>();
        for (GlossaryTerm term : terms) {
            if (term.term() == null) {
                continue;
            }
            String folded = WHITESPACE.matcher(FoldedText.foldString(term.term()).trim()).replaceAll(" ");
            if (!folded.isEmpty()) {
                list.add(new Compiled(term, folded, compile(folded, term.mode())));
            }
        }
        this.compiled = List.copyOf(list);
    }

    /** @return {@code true} when no term can match */
    public boolean isEmpty() {
        return compiled.isEmpty();
    }

    /** @return number of terms the matcher looks for */
    public int size() {
        return compiled.size();
    }

    /**
     * Finds every term in a text. Two entries of the same glossary with the same folded term at the same
     * place are reported once; entries of different glossaries are each reported, with their own
     * reason, so every glossary owner sees their rule.
     *
     * @param text plain text, may be {@code null}
     * @return the matches, by position in the text
     */
    public List<Match> find(String text) {
        if (text == null || text.isEmpty() || compiled.isEmpty()) {
            return List.of();
        }
        FoldedText folded = FoldedText.of(text);
        List<Match> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Compiled c : compiled) {
            Matcher m = c.pattern().matcher(folded.text());
            while (m.find()) {
                if (m.end() == m.start()) {
                    continue;
                }
                int start = folded.originalStart(m.start());
                int end = folded.originalEnd(m.end());
                if (seen.add(c.term().glossaryPath() + '\u0000' + c.folded() + '\u0000' + start + ':' + end)) {
                    matches.add(new Match(c.term(), start, end));
                }
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(Match::end));
        return matches;
    }

    static Pattern compile(String foldedTerm, MatchMode mode) {
        String body = WHITESPACE.splitAsStream(foldedTerm)
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));
        // A boundary is checked only on a side where the term itself has a word character, as \\b does:
        // 安全 is found in "Jahia安全", 情報 in "ユーザー情報" (ー is a letter of the Common script).
        boolean wordStart = isWordChar(foldedTerm.codePointAt(0));
        boolean wordEnd = isWordChar(foldedTerm.codePointBefore(foldedTerm.length()));
        String regex = switch (mode) {
            case WORD -> (wordStart ? NOT_AFTER_WORD : "") + body + (wordEnd ? NOT_BEFORE_WORD : "");
            case PREFIX -> (wordStart ? NOT_AFTER_WORD : "") + body + (wordEnd ? WORD_REST : "");
            case ANYWHERE -> body;
        };
        return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static boolean isWordChar(int codePoint) {
        return IS_WORD_CHAR.matcher(new String(Character.toChars(codePoint))).matches();
    }
}

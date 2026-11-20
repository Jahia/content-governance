package org.jahia.community.contentgovernance.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The pattern of a term and of its other forms, which matches a text that {@link FoldedText} folded: in
 * any case, with or without accents, and across any white space. Example: the term "gratuité" matches
 * "GRATUITE" and "gratuité", and the phrase "en nuage" matches "en&nbsp;nuage".
 *
 * <p>A form is a whole word or phrase: "garanti" does not match "garantie". A form that ends with
 * {@code *} is a stem, which matches the rest of the word too: "guarant*" matches "guaranteed". A form
 * that starts with {@code *} may end another word: "*free" matches "superfree". "*free*" matches the
 * term alone anywhere, inside a word too: "free" in "freedom".</p>
 *
 * <p>A word character is a letter or a digit of a script that separates its words with spaces. Chinese,
 * Japanese, Thai, Lao, Khmer and Myanmar write words without spaces, so their letters never make a term
 * part of a longer word. A boundary applies only on a side where the term itself has a word character,
 * as {@code \b} does: 安全 matches in "Jahia安全".</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class TermPattern {

    /** A place in the original text, from {@code start} included to {@code end} excluded. */
    public record Span(int start, int end) {
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String WORD_CHAR = "[\\p{L}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}"
            + "\\p{IsThai}\\p{IsLao}\\p{IsKhmer}\\p{IsMyanmar}]]";
    private static final String NOT_AFTER_WORD = "(?<!" + WORD_CHAR + ")";
    private static final String NOT_BEFORE_WORD = "(?!" + WORD_CHAR + ")";
    private static final String WORD_REST = WORD_CHAR + "*";
    private static final Pattern IS_WORD_CHAR = Pattern.compile(WORD_CHAR, Pattern.UNICODE_CHARACTER_CLASS);
    private static final String WILDCARD = "*";

    private final Pattern pattern;

    private TermPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    /**
     * @param forms the term and its other forms, such as "access token" and "access tokens"
     * @return the pattern of the forms, the longest form first, or null when every form is blank
     */
    public static TermPattern of(Collection<String> forms) {
        List<String> regexes = new ArrayList<>();
        forms.stream()
                .filter(form -> form != null && !form.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .forEach(form -> {
                    String regex = regexOf(form);
                    if (regex != null) {
                        regexes.add(regex);
                    }
                });
        if (regexes.isEmpty()) {
            return null;
        }
        StringJoiner alternatives = new StringJoiner("|", "(?:", ")");
        regexes.forEach(alternatives::add);
        return new TermPattern(Pattern.compile(alternatives.toString(), Pattern.UNICODE_CHARACTER_CLASS));
    }

    /**
     * @return the pattern, which a caller runs on {@link FoldedText#text()}
     */
    public Pattern pattern() {
        return pattern;
    }

    /**
     * @param text a folded text
     * @param max  the most places to return
     * @return the places of the term in the original text, in order
     */
    public List<Span> find(FoldedText text, int max) {
        List<Span> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text.text());
        while (found.size() < max && matcher.find()) {
            if (matcher.end() > matcher.start()) {
                found.add(span(text, matcher.start(), matcher.end()));
            }
        }
        return found;
    }

    /**
     * @param text a folded text
     * @return the first place of the term in the original text, or null
     */
    public Span first(FoldedText text) {
        List<Span> found = find(text, 1);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * @param text        a folded text
     * @param foldedStart where a match starts in the folded text
     * @param foldedEnd   where it ends, excluded, after its start
     * @return the same place in the original text
     */
    public static Span span(FoldedText text, int foldedStart, int foldedEnd) {
        return new Span(text.originalStart(foldedStart), text.originalEnd(foldedEnd));
    }

    /** The regex of one form, or null for a form that folds to nothing. */
    private static String regexOf(String form) {
        String trimmed = form.trim();
        boolean endsAnother = trimmed.length() > 1 && trimmed.startsWith(WILDCARD);
        boolean stem = trimmed.length() > 1 && trimmed.endsWith(WILDCARD);
        String core = trimmed.substring(endsAnother ? 1 : 0, Math.max(endsAnother ? 1 : 0, trimmed.length() - (stem ? 1 : 0)));
        String folded = WHITESPACE.matcher(FoldedText.foldString(core).trim()).replaceAll(" ");
        if (folded.isEmpty()) {
            return null;
        }
        String body = WHITESPACE.splitAsStream(folded).map(Pattern::quote).collect(Collectors.joining("\\s+"));
        boolean wordStart = isWordChar(folded.codePointAt(0));
        boolean wordEnd = isWordChar(folded.codePointBefore(folded.length()));
        String before = wordStart && !endsAnother ? NOT_AFTER_WORD : "";
        String after;
        if (!wordEnd || (stem && endsAnother)) {
            after = "";
        } else {
            after = stem ? WORD_REST : NOT_BEFORE_WORD;
        }
        return before + body + after;
    }

    private static boolean isWordChar(int codePoint) {
        return IS_WORD_CHAR.matcher(new String(Character.toChars(codePoint))).matches();
    }
}

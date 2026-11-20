package org.jahia.community.contentgovernance.page;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The readability score of a text, a Flesch Reading Ease adapted to its language, on a scale of 0 to
 * 100 where a higher score reads easier. ASL is words per sentence, ASW syllables per word:
 * <ul>
 *     <li>en Flesch (1948): 206.835 - 1.015 ASL - 84.6 ASW</li>
 *     <li>fr Kandel and Moles (1958): 207 - 1.015 ASL - 73.6 ASW</li>
 *     <li>de Amstad (1978): 180 - ASL - 58.5 ASW</li>
 *     <li>es Fernández Huerta (1959), corrected by Law (2011): 206.84 - 1.02 ASL - 60 ASW</li>
 *     <li>it Franchina and Vacca (1986): 206 - ASL - 65 ASW</li>
 *     <li>nl Douma (1960): 206.835 - 0.93 ASL - 77 ASW</li>
 *     <li>pt Martins et al. (1996): 248.835 - 1.015 ASL - 84.6 ASW</li>
 *     <li>sv, da, nb, nn, no LIX (Björnsson, 1968): ASL + 100 x the share of words of more than six
 *     letters, where a higher score reads harder</li>
 * </ul>
 * Another language gets no score. A language written without spaces between words gets no measure.
 *
 */
public final class Readability {

    /** Below this number of words, a text is too short to measure. */
    static final int MIN_WORDS = 30;
    private static final int LONG_WORD_LETTERS = 6;

    /** The bands of the score, from the easiest. */
    public enum Band { VERY_EASY, EASY, STANDARD, DIFFICULT, VERY_DIFFICULT }

    private record Formula(String name, DoubleBinaryOperator score) {
    }

    private static final Map<String, Formula> FORMULAS = Map.of(
            "en", new Formula("Flesch", (asl, asw) -> 206.835 - 1.015 * asl - 84.6 * asw),
            "fr", new Formula("Kandel-Moles", (asl, asw) -> 207 - 1.015 * asl - 73.6 * asw),
            "de", new Formula("Amstad", (asl, asw) -> 180 - asl - 58.5 * asw),
            "es", new Formula("Fernández Huerta", (asl, asw) -> 206.84 - 1.02 * asl - 60 * asw),
            "it", new Formula("Franchina-Vacca", (asl, asw) -> 206 - asl - 65 * asw),
            "nl", new Formula("Douma", (asl, asw) -> 206.835 - 0.93 * asl - 77 * asw),
            "pt", new Formula("Martins", (asl, asw) -> 248.835 - 1.015 * asl - 84.6 * asw));
    private static final String LIX = "LIX";
    private static final Set<String> LIX_LANGUAGES = Set.of("sv", "da", "nb", "nn", "no");
    private static final Set<String> UNSPACED = Set.of("ja", "zh", "th", "lo", "km", "my");

    private static final Map<String, Pattern> VOWELS = Map.of(
            "en", Pattern.compile("[aeiouy]+"),
            "fr", Pattern.compile("[aeiouyàâäéèêëîïôöùûüœæ]+"),
            "de", Pattern.compile("[aeiouyäöü]+"),
            "es", Pattern.compile("[aeiouáéíóúü]+"),
            "it", Pattern.compile("[aeiouàèéìíòóù]+"),
            "nl", Pattern.compile("[aeiouyáéèëíïóöú]+"),
            "pt", Pattern.compile("[aeiouáàâãéêíóôõú]+"));
    /** A diaeresis after another vowel opens a new syllable in French and Dutch: Noël, naïf, geëerd. */
    private static final Pattern HIATUS = Pattern.compile("[aeiouy][ëïü]");
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}]");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}'’-]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** A sentence ends at the spaces after a full stop, ! ? or an ellipsis. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");

    /**
     * What a text measures.
     *
     * @param measured       false for a text too short, or in a language written without spaces
     * @param formula        the name of the formula, or null when the language has none
     * @param score          the score from 0 to 100, or null when the language has no formula
     * @param band           the band of the score, or null
     * @param words          the number of words
     * @param sentenceWords   the number of words of each sentence, in the order of the text
     * @param syllables       the average syllables of a word, or null when the score counts none
     */
    public record Measure(boolean measured, String formula, Integer score, Band band, int words,
                          List<Integer> sentenceWords, Double syllables) {
        static final Measure NONE = new Measure(false, null, null, null, 0, List.of(), null);

        /** The number of words of the longest sentence, 0 for none. */
        public int longestSentence() {
            return sentenceWords.stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        /** True for the LIX score, where a higher score reads harder. */
        public boolean lix() {
            return LIX.equals(formula);
        }
    }

    private Readability() {
    }

    /**
     * @param text     the text, its white space collapsed
     * @param language the two-letter code of its language, in lower case
     * @return what the text measures
     */
    public static Measure measure(String text, String language) {
        if (UNSPACED.contains(language)) {
            return Measure.NONE;
        }
        List<String> sentences = Arrays.stream(SENTENCE_END.split(text))
                .map(String::trim)
                .filter(sentence -> SPACES.split(sentence).length > 1)
                .toList();
        List<String> words = Arrays.stream(SPACES.split(text))
                .map(word -> NOT_WORD.matcher(word).replaceAll(""))
                .filter(word -> !word.isEmpty())
                .toList();
        if (words.size() < MIN_WORDS) {
            return new Measure(false, null, null, null, words.size(), List.of(), null);
        }
        double asl = (double) words.size() / Math.max(1, sentences.size());
        List<Integer> lengths = sentences.stream().map(sentence -> SPACES.split(sentence).length).toList();
        if (LIX_LANGUAGES.contains(language)) {
            long longWords = words.stream()
                    .filter(word -> NOT_LETTER.matcher(word).replaceAll("").length() > LONG_WORD_LETTERS)
                    .count();
            int score = clamp(asl + 100.0 * longWords / words.size());
            return new Measure(true, LIX, score, lixBand(score), words.size(), lengths, null);
        }
        Formula formula = FORMULAS.get(language);
        if (formula == null) {
            return new Measure(true, null, null, null, words.size(), lengths, null);
        }
        double asw = words.stream().mapToInt(word -> syllables(word, language)).sum() / (double) words.size();
        int score = clamp(formula.score().applyAsDouble(asl, asw));
        return new Measure(true, formula.name(), score, band(score), words.size(), lengths, asw);
    }

    /** False for a language written without spaces between words, where a word count means nothing. */
    public static boolean spaced(String language) {
        return !UNSPACED.contains(language);
    }

    /**
     * The syllables of a word: its groups of vowels, plus a hiatus marked by a diaeresis, minus the
     * silent final e of English and French.
     *
     * @return 0 for a word with no letter, else 1 or more
     */
    static int syllables(String word, String language) {
        String letters = NOT_LETTER.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("");
        if (letters.isEmpty()) {
            return 0;
        }
        int count = count(VOWELS.getOrDefault(language, VOWELS.get("en")).matcher(letters));
        if ("fr".equals(language) || "nl".equals(language)) {
            count += count(HIATUS.matcher(letters));
        }
        boolean silentEnd = letters.endsWith("e") || letters.endsWith("es")
                || ("en".equals(language) && letters.endsWith("ed"));
        if (count > 1 && ("en".equals(language) || "fr".equals(language)) && silentEnd) {
            count--;
        }
        return Math.max(1, count);
    }

    private static int count(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** The rounding of the drawer: a half rounds up. */
    private static int clamp(double score) {
        return (int) Math.round(Math.min(100, Math.max(0, score)));
    }

    private static Band band(int score) {
        if (score >= 90) {
            return Band.VERY_EASY;
        }
        if (score >= 70) {
            return Band.EASY;
        }
        if (score >= 50) {
            return Band.STANDARD;
        }
        return score >= 30 ? Band.DIFFICULT : Band.VERY_DIFFICULT;
    }

    /** The midpoints between the anchors of the table of Björnsson: 20, 30, 40, 50 and 60. */
    private static Band lixBand(int score) {
        if (score < 25) {
            return Band.VERY_EASY;
        }
        if (score < 35) {
            return Band.EASY;
        }
        if (score < 45) {
            return Band.STANDARD;
        }
        return score < 55 ? Band.DIFFICULT : Band.VERY_DIFFICULT;
    }
}

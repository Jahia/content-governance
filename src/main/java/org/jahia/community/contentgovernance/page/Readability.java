package org.jahia.community.contentgovernance.page;

import java.util.Arrays;
import java.util.LinkedHashMap;
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
 * <p>The Readability tab of the drawer shows these values, from {@link #statistics}.</p>
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
    /** The name of the formula of the languages whose score is a LIX, where a higher score reads harder. */
    static final String LIX = "LIX";
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
    /** A sentence of a language written without spaces ends at its full stop, ! or ?. */
    private static final Pattern UNSPACED_SENTENCE_END = Pattern.compile("[。．！？!?]+");
    private static final int READING_WORDS_PER_MINUTE = 200;
    private static final String SPACED = "spaced";
    private static final String EMPTY = "empty";
    private static final String SCORED = "scored";
    private static final String SENTENCES = "sentences";
    private static final String WORDS = "words";
    private static final String CHARACTERS = "characters";

    /**
     * What a text measures.
     *
     * @param measured       false for a text too short, or in a language written without spaces
     * @param formula        the name of the formula, or null when the language has none
     * @param score          the score from 0 to 100, or null when the language has no formula
     * @param band           the band of the score, or null
     * @param words          the number of words
     * @param longestSentence the number of words of the longest sentence
     */
    public record Measure(boolean measured, String formula, Integer score, Band band, int words, int longestSentence) {
        static final Measure NONE = new Measure(false, null, null, null, 0, 0);
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
        List<Integer> sentences = sentenceLengths(text);
        List<String> words = words(text);
        if (words.size() < MIN_WORDS) {
            return new Measure(false, null, null, null, words.size(), 0);
        }
        double asl = (double) words.size() / Math.max(1, sentences.size());
        int longest = sentences.stream().mapToInt(Integer::intValue).max().orElse(0);
        if (LIX_LANGUAGES.contains(language)) {
            long longWords = words.stream()
                    .filter(word -> NOT_LETTER.matcher(word).replaceAll("").length() > LONG_WORD_LETTERS)
                    .count();
            int score = clamp(asl + 100.0 * longWords / words.size());
            return new Measure(true, LIX, score, lixBand(score), words.size(), longest);
        }
        Formula formula = FORMULAS.get(language);
        if (formula == null) {
            return new Measure(true, null, null, null, words.size(), longest);
        }
        int score = clamp(formula.score().applyAsDouble(asl, syllablesPerWord(words, language)));
        return new Measure(true, formula.name(), score, band(score), words.size(), longest);
    }

    /**
     * The statistics of a text that the Readability tab of the drawer shows, with the score of
     * {@link #measure}.
     *
     * @param text             the text, its white space collapsed
     * @param language         the two-letter code of its language, in lower case
     * @param maxSentenceWords the most words of a sentence that is not long, or null
     * @return by key: {@code lang}, {@code spaced}, {@code empty}, {@code scored}, {@code formula},
     *     {@code scale} ({@code lix} when a higher score reads harder, else {@code flesch}), {@code score},
     *     {@code band}, {@code gradeLevel} (English only), {@code words}, {@code characters} (a language
     *     written without spaces only), {@code sentences}, {@code avgSentenceLength},
     *     {@code avgSyllablesPerWord}, {@code longSentences} and {@code readingMinutes}; null for a value
     *     that the language or the length of the text cannot give
     */
    public static Map<String, Object> statistics(String text, String language, Integer maxSentenceWords) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("lang", SPACED, EMPTY, SCORED, "formula", "scale", "score", "band",
                "gradeLevel", WORDS, CHARACTERS, SENTENCES, "avgSentenceLength", "avgSyllablesPerWord",
                "longSentences", "readingMinutes")) {
            out.put(key, null);
        }
        out.put("lang", language);
        out.put(SCORED, false);
        if (UNSPACED.contains(language)) {
            long characters = text.codePoints().filter(Character::isLetterOrDigit).count();
            out.put(SPACED, false);
            out.put(EMPTY, characters == 0);
            out.put(CHARACTERS, characters);
            out.put(SENTENCES, Arrays.stream(UNSPACED_SENTENCE_END.split(text)).filter(s -> !s.isBlank()).count());
            return out;
        }
        List<Integer> sentences = sentenceLengths(text);
        List<String> words = words(text);
        out.put(SPACED, true);
        out.put(WORDS, words.size());
        out.put(EMPTY, words.size() < MIN_WORDS);
        if (words.size() < MIN_WORDS) {
            return out;
        }
        Measure measure = measure(text, language);
        double asl = (double) words.size() / Math.max(1, sentences.size());
        boolean lix = LIX_LANGUAGES.contains(language);
        Double asw = lix || !FORMULAS.containsKey(language) ? null : syllablesPerWord(words, language);
        out.put(SCORED, measure.formula() != null);
        out.put("formula", measure.formula());
        out.put("scale", lix ? "lix" : "flesch");
        out.put("score", measure.score());
        out.put("band", measure.band() == null ? null : camelCase(measure.band()));
        out.put("gradeLevel", "en".equals(language) && asw != null
                ? Math.round((0.39 * asl + 11.8 * asw - 15.59) * 10) / 10.0 : null);
        out.put(SENTENCES, sentences.size());
        out.put("avgSentenceLength", Math.round(asl * 10) / 10.0);
        out.put("avgSyllablesPerWord", asw == null ? null : Math.round(asw * 100) / 100.0);
        out.put("longSentences", maxSentenceWords == null ? null
                : sentences.stream().filter(length -> length > maxSentenceWords).count());
        out.put("readingMinutes", Math.max(1, Math.round(words.size() / (float) READING_WORDS_PER_MINUTE)));
        return out;
    }

    /** The number of words of each sentence of a text that holds two words or more. */
    private static List<Integer> sentenceLengths(String text) {
        return Arrays.stream(SENTENCE_END.split(text))
                .map(String::trim)
                .map(sentence -> SPACES.split(sentence).length)
                .filter(length -> length > 1)
                .toList();
    }

    private static List<String> words(String text) {
        return Arrays.stream(SPACES.split(text))
                .map(word -> NOT_WORD.matcher(word).replaceAll(""))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static double syllablesPerWord(List<String> words, String language) {
        return words.stream().mapToInt(word -> syllables(word, language)).sum() / (double) words.size();
    }

    /** VERY_EASY gives veryEasy, the key of the band in the texts of the drawer. */
    static String camelCase(Band band) {
        String[] parts = band.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            out.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return out.toString();
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

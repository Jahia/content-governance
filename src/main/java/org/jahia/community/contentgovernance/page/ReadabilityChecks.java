package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.ThresholdCheck;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The readability checks of the main text of a page or of a content item. Every rule of readability is a
 * threshold on a value that {@link #facts} measures: {@code readability.longSentences},
 * {@code readability.longParagraphs}, {@code readability.thinContent}, and one rule per scale of the
 * score, each with its own limit: {@code readability.flesch} (a Flesch Reading Ease adapted to the
 * language, where a higher score reads easier: a minimum) and {@code readability.lix} (the LIX of the
 * Nordic languages, where a higher score reads harder: a maximum). A text of fewer than 30 words gives no
 * finding. The rule of the scale of the language carries the score with {@link #values}.
 *
 * <p>Two measure rules give what the Readability tab of the drawer shows: {@code readability.statistics},
 * the statistics of the main text, and {@code readability.headings}, the outline of the headings.</p>
 */
@Component(immediate = true)
public class ReadabilityChecks {

    /** The score of a text in a language with a Flesch-type formula. */
    static final String FLESCH = "readability.flesch";
    /** The score of a text in a language whose formula is the LIX. */
    static final String LIX = "readability.lix";
    static final String LONG_SENTENCES = "readability.longSentences";
    static final String LONG_PARAGRAPHS = "readability.longParagraphs";
    static final String THIN_CONTENT = "readability.thinContent";
    /** The measure rule of the statistics of the main text. */
    public static final String STATISTICS = "readability.statistics";
    /** The measure rule of the outline of the headings. */
    public static final String HEADINGS = "readability.headings";
    /** The ids of the checks of their own: the measure rules. Every other rule of readability is a threshold. */
    public static final List<String> IDS = List.of(STATISTICS, HEADINGS);
    /**
     * The fact of the maximum of the rule {@code readability.longSentences} for the site, an {@link Integer}:
     * the statistics count the sentences longer than it. With no such fact, they count none.
     */
    public static final String LONG_SENTENCE_WORDS = "content-governance:longSentenceWords";

    /** The value of a finding of {@code readability.longSentences} that holds the whole sentence. */
    static final String SENTENCE = "sentence";
    /** The value of a finding of {@code readability.longSentences} that holds the words of the sentence. */
    static final String WORDS = "words";

    private static final String SHARED = "content-governance:readability";
    /** The parts of a page that are not its text. */
    private static final String NOT_TEXT = "script, style, noscript, svg, nav, header, footer, [aria-hidden=true], "
            + "[role=navigation], [role=menu], [role=menubar], [role=banner], [role=contentinfo]";

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        services.open(context, Family.checks(IDS, SHARED, ReadabilityChecks::read));
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    /**
     * The values of the thresholds of readability: the words of the longest sentence, the average words
     * of a paragraph, and the words of the text. None for a text too short to measure.
     *
     * @param html     the HTML of a page or of an item
     * @param language the language of the content, or null for the language of the page
     * @return the facts by rule id
     */
    public static Map<String, Object> facts(Document html, String language) {
        return facts(measure(html, language), html);
    }

    /**
     * The input with the facts of {@link #facts} and the values of the rule of the scale of the language,
     * from one measure of the text.
     *
     * @param input    the input of the checks of the HTML
     * @param html     the HTML of a page or of an item
     * @param language the language of the content, or null for the language of the page
     * @return the input with these facts and values
     */
    public static CheckInput withReadability(CheckInput input, Document html, String language) {
        Readability.Measure measure = measure(html, language);
        CheckInput out = input;
        for (Map.Entry<String, Object> fact : facts(measure, html).entrySet()) {
            out = out.withFact(fact.getKey(), fact.getValue());
        }
        for (Map.Entry<String, Map<String, Object>> values : values(measure).entrySet()) {
            out = out.withValues(values.getKey(), values.getValue());
        }
        if (measure.measured()) {
            out = out.withFact(ThresholdCheck.placesOf(LONG_SENTENCES), sentencePlaces(text(html)));
        }
        return out;
    }

    /**
     * The places of the rule {@code readability.longSentences}: each sentence of the text, the longest first, with
     * {@code {sentence, words}} as its values. A fixer reads the sentence to rewrite in {@code sentence}.
     */
    static List<ThresholdCheck.Place> sentencePlaces(String text) {
        return Readability.sentences(text).stream()
                .map(sentence -> {
                    int words = Readability.wordCount(sentence);
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put(SENTENCE, sentence);
                    values.put(WORDS, words);
                    return new ThresholdCheck.Place(words, sentence, null, values);
                })
                .sorted(Comparator.comparingInt((ThresholdCheck.Place place) -> place.value().intValue()).reversed())
                .toList();
    }

    private static Map<String, Object> facts(Readability.Measure measure, Document html) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!measure.measured()) {
            return out;
        }
        out.put(LONG_SENTENCES, measure.longestSentence());
        out.put(THIN_CONTENT, measure.words());
        if (measure.score() != null) {
            // Only the rule of the scale of the language reads the score: the other rule has no fact and passes
            out.put(Readability.LIX.equals(measure.formula()) ? LIX : FLESCH, measure.score());
        }
        List<Integer> paragraphs = paragraphLengths(html);
        if (!paragraphs.isEmpty()) {
            out.put(LONG_PARAGRAPHS, average(paragraphs));
        }
        return out;
    }

    /**
     * The values of the rule of the scale of the language, beside its fact: the score, the name of the
     * formula and the band of the score. None for a text too short to measure, or in a language with no
     * formula.
     *
     * @return {@code {score, formula, band}} under {@code readability.flesch} or {@code readability.lix}
     */
    private static Map<String, Map<String, Object>> values(Readability.Measure measure) {
        if (!measure.measured() || measure.score() == null) {
            return Map.of();
        }
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("score", measure.score());
        score.put("formula", measure.formula());
        score.put("band", Readability.camelCase(measure.band()));
        return Map.of(Readability.LIX.equals(measure.formula()) ? LIX : FLESCH, score);
    }

    /**
     * The values of the measure rule {@code readability.statistics}: the statistics of the main text and its
     * paragraphs.
     *
     * @param html             the HTML of a page or of an item
     * @param language         the language of the content, or null for the language of the page
     * @param maxSentenceWords the maximum of the rule {@code readability.longSentences}, or null
     * @return the keys of {@link Readability#statistics}, {@code longSentenceWords}, and {@code paragraphs} and
     *     {@code avgWordsPerParagraph} for a text long enough
     */
    public static Map<String, Object> statisticsValues(Document html, String language, Integer maxSentenceWords) {
        Map<String, Object> out = new LinkedHashMap<>(Readability.statistics(text(html), code(html, language),
                maxSentenceWords));
        out.put("longSentenceWords", maxSentenceWords);
        if (!Boolean.TRUE.equals(out.get("empty"))) {
            List<Integer> paragraphs = paragraphLengths(html);
            out.put("paragraphs", paragraphs.size());
            out.put("avgWordsPerParagraph", Boolean.TRUE.equals(out.get("spaced")) && !paragraphs.isEmpty()
                    ? average(paragraphs) : null);
        }
        return out;
    }

    /**
     * The values of the measure rule {@code readability.headings}: the outline of the headings.
     *
     * @param html the HTML of a page or of an item
     * @return the keys of {@link HeadingOutline#of}
     */
    public static Map<String, Object> headingsValues(Document html) {
        return HeadingOutline.of(html, mainText(html));
    }

    /** The number of words of each paragraph of the main text that holds a text. */
    private static List<Integer> paragraphLengths(Document html) {
        return mainText(html).select("p").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> text.split("\\s+").length)
                .toList();
    }

    private static int average(List<Integer> values) {
        return Math.round(values.stream().mapToInt(Integer::intValue).sum() / (float) values.size());
    }

    static Family.Found read(CheckInput input) {
        Document html = Family.document(input);
        Integer maxSentenceWords = input.fact(LONG_SENTENCE_WORDS) instanceof Integer maximum ? maximum : null;
        return new Family.Found()
                .values(STATISTICS, statisticsValues(html, input.language(), maxSentenceWords))
                .values(HEADINGS, headingsValues(html));
    }

    static Readability.Measure measure(Document html, String language) {
        return Readability.measure(text(html), code(html, language));
    }

    /** The two-letter code of the language, else of the language of the page, else en. */
    private static String code(Document html, String language) {
        String code = language;
        if (code == null || code.isBlank()) {
            Element root = html.selectFirst("html");
            code = root == null || root.attr("lang").isBlank() ? "en" : root.attr("lang");
        }
        return code.substring(0, Math.min(2, code.length())).toLowerCase(Locale.ROOT);
    }

    /** The {@code main} element of the page, or its body. */
    static Element mainText(Document html) {
        Element main = html.selectFirst("main");
        return main == null ? html.body() : main;
    }

    /**
     * The text of the main part, with a space between the texts of two inline elements, and a line break where a block
     * element or a {@code br} ends, so that the label of a list item or a table cell is a sentence of its own.
     */
    static String text(Document html) {
        Element root = mainText(html).clone();
        root.select(NOT_TEXT).remove();
        StringBuilder out = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode text && !text.text().isBlank()) {
                    out.append(' ').append(text.text().trim());
                } else if (node instanceof Element element && "br".equals(element.normalName())) {
                    out.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element.isBlock()) {
                    out.append('\n');
                }
            }
        }, root);
        return out.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" ?\n\\s*", "\n").trim();
    }
}

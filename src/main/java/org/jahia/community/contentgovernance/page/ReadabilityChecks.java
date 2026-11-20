package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The readability checks of the main text of a page or of a content item. The rules
 * {@code readability.longSentences}, {@code readability.longParagraphs} and
 * {@code readability.thinContent} are thresholds on the values that {@link #facts} measures. A text of
 * fewer than 30 words gives no finding.
 */
@Component(immediate = true)
public class ReadabilityChecks {

    static final String VERY_HARD = "readability.veryHardText";
    static final String HARD = "readability.hardText";
    public static final String LONG_SENTENCES = "readability.longSentences";
    static final String LONG_PARAGRAPHS = "readability.longParagraphs";
    static final String THIN_CONTENT = "readability.thinContent";
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of(VERY_HARD, HARD);

    private static final String SHARED = "content-governance:readability";
    /** The parts of a page that are not its text. */
    private static final String NOT_TEXT = "script, style, noscript, svg, nav, header, footer, [aria-hidden=true]";

    /** Headings listed in the outline: a longer page keeps its counts, the list stops here. */
    private static final int MAX_OUTLINE_HEADINGS = 200;
    private static final int MAX_HEADING_CHARS = 160;
    private static final int READING_WPM = 200;
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern UNSPACED_SENTENCE_END = Pattern.compile("[。．！？!?]+");
    private static final Map<Readability.Band, String> BAND_NAMES = Map.of(
            Readability.Band.VERY_EASY, "veryEasy",
            Readability.Band.EASY, "easy",
            Readability.Band.STANDARD, "standard",
            Readability.Band.DIFFICULT, "difficult",
            Readability.Band.VERY_DIFFICULT, "veryDifficult");

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        services.open(context, Family.checks(IDS, SHARED, ReadabilityChecks::read), false);
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
        Readability.Measure measure = measure(html, language);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!measure.measured()) {
            return out;
        }
        out.put(LONG_SENTENCES, measure.longestSentence());
        out.put(THIN_CONTENT, measure.words());
        List<Integer> paragraphs = paragraphWords(html);
        if (!paragraphs.isEmpty()) {
            out.put(LONG_PARAGRAPHS, average(paragraphs));
        }
        return out;
    }

    /**
     * What the readability tab of the drawer shows of a page or an item: the statistics and the score of
     * its main text, its paragraphs and its headings.
     *
     * @param html             the HTML of a page or of an item
     * @param language         the language of the content, or null for the language of the page
     * @param maxSentenceWords the most words of a sentence that is not long, or null to count none
     * @return the facts, by the names that the tab reads
     */
    public static Map<String, Object> describe(Document html, String language, Integer maxSentenceWords) {
        String code = code(html, language);
        Readability.Measure measure = Readability.measure(text(html), code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lang", code);
        boolean spaced = Readability.spaced(code);
        out.put("spaced", spaced);
        List<Integer> paragraphs = paragraphWords(html);
        out.put("paragraphs", paragraphs.size());
        for (String unmeasured : List.of("words", "gradeLevel", "readingMinutes", "longSentences")) {
            out.put(unmeasured, null);
        }
        if (!spaced) {
            String text = text(html);
            long characters = LETTER_OR_DIGIT.matcher(text).results().count();
            out.put("characters", characters);
            out.put("sentences", UNSPACED_SENTENCE_END.splitAsStream(text).filter(part -> !part.isBlank()).count());
            out.put("empty", characters == 0);
            out.put("scored", false);
        } else if (!measure.measured()) {
            out.put("words", measure.words());
            out.put("empty", true);
            out.put("scored", false);
        } else {
            spacedText(measure, code, maxSentenceWords, out);
            out.put("avgWordsPerParagraph", paragraphs.isEmpty() ? null : average(paragraphs));
        }
        out.put("longSentenceWords", maxSentenceWords);
        out.putAll(headings(html));
        return out;
    }

    private static void spacedText(Readability.Measure measure, String code, Integer maxSentenceWords,
                                   Map<String, Object> out) {
        List<Integer> sentences = measure.sentenceWords();
        double asl = (double) measure.words() / Math.max(1, sentences.size());
        out.put("empty", false);
        out.put("scored", measure.score() != null);
        out.put("formula", measure.formula());
        out.put("scale", measure.lix() ? "lix" : "flesch");
        out.put("score", measure.score());
        out.put("band", measure.band() == null ? null : BAND_NAMES.get(measure.band()));
        out.put("gradeLevel", "en".equals(code) && measure.syllables() != null
                ? Math.round((0.39 * asl + 11.8 * measure.syllables() - 15.59) * 10) / 10.0 : null);
        out.put("words", measure.words());
        out.put("sentences", sentences.size());
        out.put("avgSentenceLength", Math.round(asl * 10) / 10.0);
        out.put("avgSyllablesPerWord", measure.syllables() == null ? null : Math.round(measure.syllables() * 100) / 100.0);
        out.put("longSentences", maxSentenceWords == null ? null
                : sentences.stream().filter(words -> words > maxSentenceWords).count());
        out.put("readingMinutes", Math.max(1, Math.round(measure.words() / (float) READING_WPM)));
    }

    /**
     * The headings of the page: the number of h1 of the whole page, and the outline of its main part,
     * where a heading more than one level under the one before it skips a level.
     */
    static Map<String, Object> headings(Document html) {
        List<Map<String, Object>> outline = new ArrayList<>();
        int skips = 0;
        int previous = 0;
        List<Element> all = mainText(html).select("h1, h2, h3, h4, h5, h6");
        for (Element heading : all) {
            int level = heading.normalName().charAt(1) - '0';
            boolean skipped = previous > 0 && level > previous + 1;
            if (skipped) {
                skips++;
            }
            if (outline.size() < MAX_OUTLINE_HEADINGS) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String label = headingText(heading);
                entry.put("level", level);
                entry.put("text", label.length() > MAX_HEADING_CHARS ? label.substring(0, MAX_HEADING_CHARS) : label);
                entry.put("selector", heading.cssSelector());
                if (skipped) {
                    entry.put("skippedFrom", previous);
                }
                if (label.isEmpty()) {
                    entry.put("empty", true);
                }
                outline.add(entry);
            }
            previous = level;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("h1Count", html.select("h1").size());
        out.put("headingSkips", skips);
        out.put("headingCount", all.size());
        out.put("outline", outline);
        return out;
    }

    /** What a heading says: its text, else what stands for it, an aria-label or the alt of an image. */
    private static String headingText(Element heading) {
        String text = heading.text().trim();
        if (!text.isEmpty()) {
            return text;
        }
        if (heading.hasAttr("aria-label")) {
            return heading.attr("aria-label").trim();
        }
        Element image = heading.selectFirst("img[alt]");
        return image == null ? "" : image.attr("alt").trim();
    }

    /** The words of each paragraph of the main part that has text. */
    private static List<Integer> paragraphWords(Document html) {
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
        Readability.Measure measure = measure(Family.document(input), input.language());
        if (measure.band() == Readability.Band.VERY_DIFFICULT) {
            return new Family.Found().add(VERY_HARD, String.valueOf(measure.score()), measure.formula());
        }
        if (measure.band() == Readability.Band.DIFFICULT) {
            return new Family.Found().add(HARD, String.valueOf(measure.score()), measure.formula());
        }
        return new Family.Found();
    }

    static Readability.Measure measure(Document html, String language) {
        return Readability.measure(text(html), code(html, language));
    }

    /** The two-letter code of the language of the content, else of the page, else en. */
    private static String code(Document html, String language) {
        String code = language;
        if (code == null || code.isBlank()) {
            Element root = html.selectFirst("html");
            code = root == null || root.attr("lang").isBlank() ? "en" : root.attr("lang");
        }
        return code.substring(0, Math.min(2, code.length())).toLowerCase(Locale.ROOT);
    }

    /** The {@code main} element of the page, or its body. */
    private static Element mainText(Document html) {
        Element main = html.selectFirst("main");
        return main == null ? html.body() : main;
    }

    /** The text of the main part, with a space between the texts of two elements. */
    static String text(Document html) {
        Element root = mainText(html).clone();
        root.select(NOT_TEXT).remove();
        List<String> parts = new ArrayList<>();
        NodeTraversor.traverse((node, depth) -> {
            if (node instanceof TextNode text && !text.text().isBlank()) {
                parts.add(text.text().trim());
            }
        }, root);
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }
}

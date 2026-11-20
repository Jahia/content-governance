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
    static final String LONG_SENTENCES = "readability.longSentences";
    static final String LONG_PARAGRAPHS = "readability.longParagraphs";
    static final String THIN_CONTENT = "readability.thinContent";
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of(VERY_HARD, HARD);

    private static final String SHARED = "content-governance:readability";
    /** The parts of a page that are not its text. */
    private static final String NOT_TEXT = "script, style, noscript, svg, nav, header, footer, [aria-hidden=true]";

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
        List<Integer> paragraphs = mainText(html).select("p").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> text.split("\\s+").length)
                .toList();
        if (!paragraphs.isEmpty()) {
            out.put(LONG_PARAGRAPHS, Math.round(paragraphs.stream().mapToInt(Integer::intValue).sum()
                    / (float) paragraphs.size()));
        }
        return out;
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
        String code = language;
        if (code == null || code.isBlank()) {
            Element root = html.selectFirst("html");
            code = root == null || root.attr("lang").isBlank() ? "en" : root.attr("lang");
        }
        return Readability.measure(text(html), code.substring(0, Math.min(2, code.length())).toLowerCase(Locale.ROOT));
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

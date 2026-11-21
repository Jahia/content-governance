package org.jahia.community.contentgovernance.page;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
 * The SEO checks of a whole page: robots directives, canonical link, Open Graph card and structured
 * data. A rendered content item gets none of them. The rules {@code seo.title} and
 * {@code seo.metaDescription} are thresholds on the lengths that {@link #facts} measures, with the values
 * of {@link #values}. The measure rule {@code seo.tags} gives the tags of the head and the images, on a
 * page or an item: the SEO tab of the drawer shows them.
 */
@Component(immediate = true)
public class SeoChecks {

    public static final String TITLE = "seo.title";
    public static final String DESCRIPTION = "seo.metaDescription";
    static final String INDEXABLE = "seo.indexable";
    static final String FOLLOW_LINKS = "seo.followLinks";
    static final String CANONICAL = "seo.canonical";
    static final String OPEN_GRAPH = "seo.openGraph";
    static final String STRUCTURED_DATA = "seo.structuredData";
    static final String STRUCTURED_DATA_VALID = "seo.structuredDataValid";
    /** The measure rule of the tags of the page. */
    public static final String TAGS = "seo.tags";
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of(INDEXABLE, FOLLOW_LINKS, CANONICAL, OPEN_GRAPH, STRUCTURED_DATA,
            STRUCTURED_DATA_VALID, TAGS);

    private static final String SHARED = "content-governance:seo";
    private static final String HEAD = "<head>";
    private static final String LD_JSON = "script[type=application/ld+json]";
    private static final String CONTENT = "content";
    private static final String TITLE_KEY = "title";
    private static final String COUNT = "count";
    private static final String NAME = "name";
    private static final String DESCRIPTION_KEY = "description";
    private static final String ROBOTS = "robots";
    private static final String NOINDEX = "noindex";
    private static final String NOFOLLOW = "nofollow";
    private static final String CANONICAL_KEY = "canonical";
    private static final String HREF = "href";
    private static final String OG = "og";
    private static final String JSON_LD = "jsonLd";
    private static final ObjectMapper JSON = new ObjectMapper();
    /** The label that the preview of Jahia puts before the title of a page in the edit workspace. */
    private static final Pattern PREVIEW_LABEL = Pattern.compile(
            "^(aperçu|preview|vorschau|anteprima|vista previa)\\s*[-–:]\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ);

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        services.open(context, Family.checks(IDS, SHARED, SeoChecks::read), false);
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    /**
     * The values that the thresholds of a whole page measure: the length of the title and of the meta
     * description, 0 when the page has none.
     *
     * @param html the HTML of a whole page
     * @return the facts by rule id
     */
    public static Map<String, Object> facts(Document html) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(TITLE, title(html).length());
        out.put(DESCRIPTION, meta(html, NAME, DESCRIPTION_KEY).length());
        return out;
    }

    /**
     * The values of the thresholds of a whole page, beside their facts: the text and the length of the
     * title and of the meta description.
     *
     * @param html the HTML of a whole page
     * @return {@code {text, length}} by rule id, with a null text for a meta description that the page lacks
     */
    public static Map<String, Map<String, Object>> values(Document html) {
        Element description = tag(html, NAME, DESCRIPTION_KEY);
        return Map.of(TITLE, textAndLength(title(html)),
                DESCRIPTION, textAndLength(description == null ? null : description.attr(CONTENT).trim()));
    }

    /**
     * The values of the measure rule {@code seo.tags}, which the SEO tab of the drawer shows: the tags of
     * the head and the images.
     *
     * @param html     the HTML of a page or of an item
     * @param language the language of the content, such as {@code en}, or null
     * @return {@code title} and {@code description} as {@code {text, length}}, {@code robots} as
     *     {@code {content, noindex, nofollow}}, {@code canonical}, {@code og} as {@code {title, description,
     *     image, type}}, {@code twitterCard}, {@code jsonLd} as {@code {count, errors}}, {@code lang} as
     *     {@code {htmlLang, expected}}, {@code hreflang}, {@code totalImages} and {@code imagesWithoutAlt};
     *     null for a tag that the page lacks
     */
    public static Map<String, Object> tags(Document html, String language) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Map<String, Object>> thresholds = values(html);
        out.put(TITLE_KEY, thresholds.get(TITLE));
        out.put(DESCRIPTION_KEY, thresholds.get(DESCRIPTION));
        String robots = orNull(meta(html, NAME, ROBOTS).toLowerCase(Locale.ROOT));
        Map<String, Object> directives = new LinkedHashMap<>();
        directives.put(CONTENT, robots);
        directives.put(NOINDEX, robots != null && robots.contains(NOINDEX));
        directives.put(NOFOLLOW, robots != null && robots.contains(NOFOLLOW));
        out.put(ROBOTS, directives);
        Element canonical = html.selectFirst("link[rel=canonical]");
        out.put(CANONICAL_KEY, canonical == null ? null : canonical.attr(HREF).trim());
        Map<String, Object> og = new LinkedHashMap<>();
        for (String property : List.of(TITLE_KEY, DESCRIPTION_KEY, "image", "type")) {
            og.put(property, orNull(meta(html, "property", "og:" + property)));
        }
        out.put(OG, og);
        out.put("twitterCard", orNull(meta(html, NAME, "twitter:card")));
        List<Element> blocks = html.select(LD_JSON);
        out.put(JSON_LD, Map.of(COUNT, blocks.size(), "errors", invalidBlocks(blocks).size()));
        Element root = html.selectFirst("html");
        Map<String, Object> lang = new LinkedHashMap<>();
        lang.put("htmlLang", root == null ? null : orNull(root.attr("lang").trim().toLowerCase(Locale.ROOT)));
        lang.put("expected", language == null ? "" : language.substring(0, Math.min(2, language.length()))
                .toLowerCase(Locale.ROOT));
        out.put("lang", lang);
        out.put("hreflang", html.select("link[rel=alternate][hreflang]").size());
        out.put("totalImages", html.select("img").size());
        out.put("imagesWithoutAlt", html.select("img:not([alt])").size());
        return out;
    }

    private static Map<String, Object> textAndLength(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", text);
        out.put("length", text == null ? 0 : text.length());
        return out;
    }

    /** A map of one entry, whose value may be null. */
    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(key, value);
        return out;
    }

    private static String orNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    /** The title of the page, without the label of the preview. */
    static String title(Document html) {
        return PREVIEW_LABEL.matcher(html.title().trim()).replaceFirst("");
    }

    // The parts of the tags are the maps that tags() builds
    @SuppressWarnings("unchecked")
    static Family.Found read(CheckInput input) {
        Document html = Family.document(input);
        Map<String, Object> tags = tags(html, input.language());
        Family.Found found = new Family.Found().values(TAGS, tags);
        if (!Family.wholePage(input)) {
            return found;
        }
        found.values(INDEXABLE, (Map<String, Object>) tags.get(ROBOTS))
                .values(FOLLOW_LINKS, (Map<String, Object>) tags.get(ROBOTS))
                .values(CANONICAL, mapOf(HREF, tags.get(CANONICAL_KEY)))
                .values(OPEN_GRAPH, (Map<String, Object>) tags.get(OG))
                .values(STRUCTURED_DATA, (Map<String, Object>) tags.get(JSON_LD))
                .values(STRUCTURED_DATA_VALID, (Map<String, Object>) tags.get(JSON_LD));
        String robots = meta(html, NAME, ROBOTS).toLowerCase(Locale.ROOT);
        if (robots.contains(NOINDEX)) {
            found.add(INDEXABLE, robots, null);
        } else if (robots.contains(NOFOLLOW)) {
            found.add(FOLLOW_LINKS, robots, null);
        }
        if (html.selectFirst("link[rel=canonical]") == null) {
            found.add(CANONICAL, HEAD, null);
        }
        List<String> missing = new ArrayList<>();
        for (String property : List.of("og:title", "og:image")) {
            if (meta(html, "property", property).isEmpty()) {
                missing.add(property);
            }
        }
        if (!missing.isEmpty()) {
            found.add(OPEN_GRAPH, String.join(" ", missing), null);
        }
        structuredData(html, found);
        return found;
    }

    private static void structuredData(Document html, Family.Found found) {
        List<Element> blocks = html.select(LD_JSON);
        List<String> invalid = invalidBlocks(blocks);
        if (!invalid.isEmpty()) {
            invalid.forEach(text -> found.add(STRUCTURED_DATA_VALID, text, null));
        } else if (blocks.isEmpty()) {
            found.add(STRUCTURED_DATA, HEAD, null);
        }
    }

    /** The start of each block of structured data that is not valid JSON, or an empty text for a blank block. */
    private static List<String> invalidBlocks(List<Element> blocks) {
        List<String> invalid = new ArrayList<>();
        for (Element block : blocks) {
            try {
                // readTree gives a missing node for a blank text, where a JSON parser fails.
                if (JSON.readTree(block.data()).isMissingNode()) {
                    invalid.add("");
                }
            } catch (JsonProcessingException e) {
                invalid.add(Family.cut(block.data().trim(), 160));
            }
        }
        return invalid;
    }

    /** The trimmed content of the first meta tag with this attribute, or an empty text. */
    private static String meta(Document html, String attribute, String name) {
        Element meta = tag(html, attribute, name);
        return meta == null ? "" : meta.attr(CONTENT).trim();
    }

    /** The first meta tag with this attribute, or null. */
    private static Element tag(Document html, String attribute, String name) {
        for (Element meta : html.select("meta[" + attribute + "]")) {
            if (meta.attr(attribute).equalsIgnoreCase(name)) {
                return meta;
            }
        }
        return null;
    }
}

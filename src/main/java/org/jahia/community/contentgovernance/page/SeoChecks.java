package org.jahia.community.contentgovernance.page;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.html.rules.HtmlRules;
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
 * {@code seo.metaDescription} are thresholds on the lengths that {@link #facts} measures.
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
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of(INDEXABLE, FOLLOW_LINKS, CANONICAL, OPEN_GRAPH, STRUCTURED_DATA,
            STRUCTURED_DATA_VALID);

    private static final String SHARED = "content-governance:seo";
    private static final String HEAD = "<head>";
    private static final String LD_JSON = "script[type=application/ld+json]";
    private static final String CONTENT = "content";
    private static final String TITLE_KEY = "title";
    private static final String COUNT = "count";
    /** The labels that the AI assist rewrites at most. */
    private static final int MAX_WEAK_CTAS = 10;
    private static final int CONTEXT_CHARS = 300;
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
        out.put(DESCRIPTION, meta(html, "name", "description").length());
        return out;
    }

    /**
     * What the SEO tab of the drawer shows: the tags of the head, the images, and the links whose label
     * says nothing, which the AI assist rewrites.
     *
     * @param html     the HTML of a whole page
     * @param language the language of the content, such as {@code en}, or null
     * @return {@code title} and {@code description} as {@code {text, length}}, {@code robots} as
     *     {@code {content, noindex, nofollow}}, {@code canonical}, {@code og} as {@code {title, description,
     *     image, type}}, {@code twitterCard}, {@code jsonLd} as {@code {count, errors}}, {@code lang} as
     *     {@code {htmlLang, expected}}, {@code hreflang}, {@code totalImages}, {@code imagesWithoutAlt} and
     *     {@code weakCtas}; null for a tag that the page lacks
     */
    public static Map<String, Object> report(Document html, String language) {
        Map<String, Object> out = new LinkedHashMap<>();
        String title = title(html);
        out.put(TITLE_KEY, textAndLength(title));
        Element description = tag(html, "name", "description");
        out.put("description", textAndLength(description == null ? null : description.attr(CONTENT).trim()));
        String robots = orNull(meta(html, "name", "robots").toLowerCase(Locale.ROOT));
        Map<String, Object> directives = new LinkedHashMap<>();
        directives.put(CONTENT, robots);
        directives.put("noindex", robots != null && robots.contains("noindex"));
        directives.put("nofollow", robots != null && robots.contains("nofollow"));
        out.put("robots", directives);
        Element canonical = html.selectFirst("link[rel=canonical]");
        out.put("canonical", canonical == null ? null : canonical.attr("href").trim());
        Map<String, Object> og = new LinkedHashMap<>();
        for (String property : List.of(TITLE_KEY, "description", "image", "type")) {
            og.put(property, orNull(meta(html, "property", "og:" + property)));
        }
        out.put("og", og);
        out.put("twitterCard", orNull(meta(html, "name", "twitter:card")));
        List<Element> blocks = html.select(LD_JSON);
        out.put("jsonLd", Map.of("count", blocks.size(), "errors", invalidBlocks(blocks).size()));
        Element root = html.selectFirst("html");
        Map<String, Object> lang = new LinkedHashMap<>();
        lang.put("htmlLang", root == null ? null : orNull(root.attr("lang").trim().toLowerCase(Locale.ROOT)));
        lang.put("expected", language == null ? "" : language.substring(0, Math.min(2, language.length()))
                .toLowerCase(Locale.ROOT));
        out.put("lang", lang);
        out.put("hreflang", html.select("link[rel=alternate][hreflang]").size());
        out.put("totalImages", html.select("img").size());
        out.put("imagesWithoutAlt", html.select("img:not([alt])").size());
        out.put("weakCtas", weakCtas(html));
        return out;
    }

    private static Map<String, Object> textAndLength(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", text);
        out.put("length", text == null ? 0 : text.length());
        return out;
    }

    private static String orNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    /**
     * The links and buttons whose label says nothing, by label: the label as the page shows it, how often,
     * the target of the first one, and the text of the block around it.
     */
    private static List<Map<String, Object>> weakCtas(Document html) {
        Map<String, Map<String, Object>> byLabel = new LinkedHashMap<>();
        for (Element control : html.select("a[href], button, input[type=submit], input[type=button]")) {
            String raw = "input".equals(control.normalName()) ? control.attr("value") : control.text();
            String label = raw.replaceAll("\\s+", " ").trim();
            if (label.isEmpty() || !HtmlRules.vague(label)) {
                continue;
            }
            String key = HtmlRules.label(label).toLowerCase(Locale.ROOT);
            Map<String, Object> found = byLabel.get(key);
            if (found != null) {
                found.put(COUNT, (int) found.get(COUNT) + 1);
            } else if (byLabel.size() < MAX_WEAK_CTAS) {
                Map<String, Object> cta = new LinkedHashMap<>();
                cta.put("text", label);
                cta.put(COUNT, 1);
                cta.put("target", control.attr("href"));
                String context = block(control, label).text();
                cta.put("context", context.length() > CONTEXT_CHARS ? context.substring(0, CONTEXT_CHARS) : context);
                byLabel.put(key, cta);
            }
        }
        return new ArrayList<>(byLabel.values());
    }

    /** The nearest parent that holds a text beyond the label, or the body. */
    private static Element block(Element control, String label) {
        Element block = control.parent();
        while (block != null && block.parent() != null && !"body".equals(block.normalName())
                && block.text().length() < label.length() + 40) {
            block = block.parent();
        }
        return block == null ? control : block;
    }

    /** The title of the page, without the label of the preview. */
    static String title(Document html) {
        return PREVIEW_LABEL.matcher(html.title().trim()).replaceFirst("");
    }

    static Family.Found read(CheckInput input) {
        if (!Family.wholePage(input)) {
            return new Family.Found();
        }
        Document html = Family.document(input);
        Family.Found found = new Family.Found();
        String robots = meta(html, "name", "robots").toLowerCase(Locale.ROOT);
        if (robots.contains("noindex")) {
            found.add(INDEXABLE, robots, null);
        } else if (robots.contains("nofollow")) {
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

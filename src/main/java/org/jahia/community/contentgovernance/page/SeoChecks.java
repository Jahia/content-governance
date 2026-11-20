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
        List<Element> blocks = html.select("script[type=application/ld+json]");
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
        if (!invalid.isEmpty()) {
            invalid.forEach(text -> found.add(STRUCTURED_DATA_VALID, text, null));
        } else if (blocks.isEmpty()) {
            found.add(STRUCTURED_DATA, HEAD, null);
        }
    }

    /** The trimmed content of the first meta tag with this attribute, or an empty text. */
    private static String meta(Document html, String attribute, String name) {
        for (Element meta : html.select("meta[" + attribute + "]")) {
            if (meta.attr(attribute).equalsIgnoreCase(name)) {
                return meta.attr("content").trim();
            }
        }
        return "";
    }
}

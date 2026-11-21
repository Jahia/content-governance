package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The link checks of a page or of a content item. {@code links.broken} reads the internal links that the
 * caller resolved in the repository, the fact {@link #BROKEN}. With no such fact, it finds nothing.
 */
@Component(immediate = true)
public class LinkChecks {

    /** The fact of the internal links that a visitor cannot open, a list of {@link LinkCheck.BrokenPath}. */
    public static final String BROKEN = "content-governance:brokenLinks";
    /** The fact of the lower-case host names of this Jahia and of the site, a set of strings. */
    public static final String LOCAL_HOSTS = "content-governance:localHosts";
    /** The fact of the internal links that the caller resolved in the repository, a {@link LinkCheck.InternalLinks}. */
    public static final String INTERNAL = "content-governance:internalLinks";
    static final String BROKEN_LINKS = "links.broken";
    static final String HARDCODED = "links.hardcodedUrls";
    static final String MIXED = "links.mixedContent";
    static final String BLANK_NOOPENER = "links.blankNoopener";
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of(BROKEN_LINKS, HARDCODED, MIXED, BLANK_NOOPENER);

    private static final String SHARED = "content-governance:links";
    private static final Set<String> DEFAULT_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final Pattern NOT_A_PAGE = Pattern.compile("^(#|mailto:|tel:|javascript:)", Pattern.CASE_INSENSITIVE);

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        services.open(context, Family.checks(IDS, SHARED, LinkChecks::read));
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    static Family.Found read(CheckInput input) {
        Family.Found found = new Family.Found();
        if (input.fact(BROKEN) instanceof List<?> broken) {
            for (Object item : broken) {
                if (item instanceof LinkCheck.BrokenPath path) {
                    found.add(BROKEN_LINKS, path.path(), path.reason());
                }
            }
        }
        Set<String> localHosts = localHosts(input);
        for (Element link : Family.document(input).select("a[href]")) {
            String href = link.attr("href").trim();
            if (!href.isEmpty() && !NOT_A_PAGE.matcher(href).find()) {
                read(link, href, localHosts, found);
            }
        }
        return found;
    }

    private static void read(Element link, String href, Set<String> localHosts, Family.Found found) {
        String lower = href.toLowerCase(Locale.ROOT);
        boolean http = lower.startsWith("http://");
        if ((http || lower.startsWith("https://")) && localHosts.contains(host(href))) {
            found.add(HARDCODED, href, null, Family.locator(link));
        }
        if (http) {
            found.add(MIXED, href, null, Family.locator(link));
        }
        if ("_blank".equals(link.attr("target")) && !link.attr("rel").toLowerCase(Locale.ROOT).contains("noopener")) {
            found.add(BLANK_NOOPENER, Family.tag(link), null, Family.locator(link));
        }
    }

    /**
     * What the Links tab of the drawer shows. The broken internal links come from the fact {@link #INTERNAL}.
     *
     * @param html       the HTML of a page or of an item
     * @param localHosts the lower-case host names of this Jahia and of the site, or null
     * @param internal   the internal links that the caller resolved, or null when it could not
     * @return {@code total}, {@code internal} and {@code external}, the links to a page; {@code checked}, the
     *     internal links resolved in the repository, and {@code skipped}, the ones with no path of the
     *     repository, such as a vanity URL, both null with no resolution; and {@code broken}, as
     *     {@code {path, reason}}
     */
    public static Map<String, Object> report(Document html, Set<String> localHosts, LinkCheck.InternalLinks internal) {
        Set<String> hosts = localHosts == null ? DEFAULT_HOSTS : localHosts;
        int total = 0;
        int external = 0;
        for (Element link : html.select("a[href]")) {
            String href = link.attr("href").trim();
            if (!href.isEmpty() && !NOT_A_PAGE.matcher(href).find()) {
                total++;
                String lower = href.toLowerCase(Locale.ROOT);
                if ((lower.startsWith("http://") || lower.startsWith("https://")) && !hosts.contains(host(href))) {
                    external++;
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("internal", total - external);
        out.put("external", external);
        out.put("checked", internal == null ? null : internal.checked());
        out.put("skipped", internal == null ? null : internal.notCheckedVanityLinks());
        List<Map<String, String>> broken = new ArrayList<>();
        if (internal != null) {
            internal.broken().forEach(path -> broken.add(Map.of("path", path.path(), "reason", path.reason())));
        }
        out.put("broken", broken);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> localHosts(CheckInput input) {
        return input.fact(LOCAL_HOSTS) instanceof Set<?> hosts ? (Set<String>) hosts : DEFAULT_HOSTS;
    }

    /** The lower-case host of an absolute address, or an empty text when it has none. */
    private static String host(String href) {
        try {
            String host = new URI(href).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }
}

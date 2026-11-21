package org.jahia.community.contentgovernance.page;

import org.jahia.api.Constants;
import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The checks of what only Jahia knows about a page or a content item: the raw keys of resource bundles
 * and the placeholder texts in its HTML, and the state of its content in the repository. The state
 * comes from the fact {@link #CONTENT}, which the caller reads with {@link #read}. With no such fact,
 * those two checks find nothing.
 */
@Component(immediate = true)
public class HealthChecks {

    /** The fact of the state of the content, a {@link Content}. */
    public static final String CONTENT = "content-governance:contentHealth";
    static final String RAW_I18N = "health.rawI18nKeys";
    static final String PLACEHOLDERS = "health.placeholderText";
    static final String UNPUBLISHED = "health.unpublishedChanges";
    static final String UNTRANSLATED = "health.missingTranslations";
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of(RAW_I18N, UNPUBLISHED, UNTRANSLATED, PLACEHOLDERS);

    /** The most content items that one read looks at, as the drawer did. */
    static final int MAX_ITEMS = 400;
    private static final String SHARED = "content-governance:health";
    private static final String PAGE_TYPE = "jnt:page";
    private static final String CONTENT_TYPE = "jnt:content";
    private static final Set<String> NOT_TEXT = Set.of("script", "style", "noscript", "template");
    /** namespace:key.path: a scheme or an address never matches, since it needs a dotted part and no slash. */
    private static final Pattern RAW_KEY = Pattern.compile("\\b[a-z][a-zA-Z0-9_-]{1,30}:[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)++\\b");
    private static final Pattern SCHEME = Pattern.compile("^(https?|mailto|tel|urn|data|javascript|file|ftp):",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "lorem ipsum|\\bTODO\\b|\\bFIXME\\b|\\bXXX\\b|à compléter|to be completed",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    /** The statuses of a node whose live version differs from its edit version. */
    private static final Set<Integer> BAD_STATUSES = Set.of(PublicationInfo.MODIFIED, PublicationInfo.NOT_PUBLISHED,
            PublicationInfo.UNPUBLISHED, PublicationInfo.MARKED_FOR_DELETION, PublicationInfo.CONFLICT);
    /** The name of each status, as a finding and the drawer give it. */
    private static final Map<Integer, String> STATUSES = Map.ofEntries(
            Map.entry(PublicationInfo.PUBLISHED, "published"),
            Map.entry(PublicationInfo.MODIFIED, "modified"),
            Map.entry(PublicationInfo.NOT_PUBLISHED, "not_published"),
            Map.entry(PublicationInfo.UNPUBLISHED, "unpublished"),
            Map.entry(PublicationInfo.MANDATORY_LANGUAGE_UNPUBLISHABLE, "mandatory_language_unpublishable"),
            Map.entry(PublicationInfo.LIVE_MODIFIED, "live_modified"),
            Map.entry(PublicationInfo.LIVE_ONLY, "live_only"),
            Map.entry(PublicationInfo.CONFLICT, "conflict"),
            Map.entry(PublicationInfo.MANDATORY_LANGUAGE_VALID, "mandatory_language_valid"),
            Map.entry(PublicationInfo.DELETED, "deleted"),
            Map.entry(PublicationInfo.MARKED_FOR_DELETION, "marked_for_deletion"));

    /** A node whose live version differs from its edit version, its primary type, and its status. */
    public record Unpublished(String path, String type, String status) {
    }

    /** A content item that has translations, its primary type, and the active languages of the site that it lacks. */
    public record Untranslated(String path, String type, List<String> missing) {
    }

    /**
     * The state of a page and of its content items, in one language.
     *
     * @param scanned         the nodes read: the page and its content items, at most {@link #MAX_ITEMS} items
     * @param activeLanguages the languages of the site that are active in the edit workspace
     * @param pageStatus      the status of the page itself, such as {@code published}, or null when unknown
     */
    public record Content(List<Unpublished> unpublished, List<Untranslated> untranslated, int scanned,
                          List<String> activeLanguages, String pageStatus) {
    }

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        services.open(context, Family.checks(IDS, SHARED, HealthChecks::read));
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    static Family.Found read(CheckInput input) {
        Family.Found found = new Family.Found();
        Element body = Family.document(input).body();
        if (body != null) {
            NodeTraversor.traverse((node, depth) -> {
                if (node instanceof TextNode text && (text.parent() == null
                        || !NOT_TEXT.contains(text.parent().normalName()))) {
                    scan(text.text(), found);
                }
            }, body);
        }
        if (input.fact(CONTENT) instanceof Content content) {
            content.unpublished().forEach(node -> found.add(UNPUBLISHED, node.path(), node.status()));
            content.untranslated().forEach(node -> found.add(UNTRANSLATED, node.path(), String.join(", ", node.missing())));
        }
        return found;
    }

    private static void scan(String text, Family.Found found) {
        String excerpt = Family.cut(text.trim(), 100);
        Matcher key = RAW_KEY.matcher(text);
        while (key.find()) {
            if (!SCHEME.matcher(key.group()).find()) {
                found.add(RAW_I18N, key.group(), excerpt);
            }
        }
        Matcher placeholder = PLACEHOLDER.matcher(text);
        if (placeholder.find()) {
            found.add(PLACEHOLDERS, placeholder.group(), excerpt);
        }
    }

    /**
     * Reads the state of a node and of its content items, down to the next page: what is not published
     * in the language, and what lacks an active language of the site. Call it inside the session.
     *
     * @param node     a page or a content item, read in the edit workspace
     * @param language the language of the content, such as {@code en}
     */
    public static Content read(JCRNodeWrapper node, String language) throws RepositoryException {
        List<JCRNodeWrapper> items = new ArrayList<>();
        items.add(node);
        collect(node, items);
        JCRSessionWrapper session = node.getSession();
        String[] pageStatus = new String[1];
        List<Unpublished> unpublished = JCRTemplate.getInstance().doExecute(session.getUser(), Constants.LIVE_WORKSPACE,
                session.getLocale(), live -> {
                    List<Unpublished> out = new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) {
                        JCRNodeWrapper item = items.get(i);
                        int code = JCRPublicationService.getInstance().getStatus(item, live, Set.of(language));
                        if (i == 0) {
                            pageStatus[0] = STATUSES.get(code);
                        }
                        if (BAD_STATUSES.contains(code)) {
                            out.add(new Unpublished(item.getPath(), item.getPrimaryNodeTypeName(), STATUSES.get(code)));
                        }
                    }
                    return out;
                });
        JCRSiteNode site = node.getResolveSite();
        Set<String> active = activeLanguages(site);
        List<Untranslated> untranslated = new ArrayList<>();
        for (JCRNodeWrapper item : items.subList(1, items.size())) {
            Set<String> present = translations(item);
            if (!present.isEmpty()) {
                List<String> missing = active.stream().filter(code -> !present.contains(code)).sorted().toList();
                if (!missing.isEmpty()) {
                    untranslated.add(new Untranslated(item.getPath(), item.getPrimaryNodeTypeName(), missing));
                }
            }
        }
        return new Content(List.copyOf(unpublished), List.copyOf(untranslated), items.size() - 1,
                List.copyOf(active), pageStatus[0]);
    }

    /**
     * What the Jahia health tab of the drawer shows: the state of the content. The raw keys and the
     * placeholder texts are findings of their rules.
     *
     * @param content the state of the content, or null when it could not be read
     * @return {@code pageStatus}, {@code activeLanguages}, {@code scanned}, {@code unpublished} as
     *     {@code {path, type, status}} and {@code untranslated} as {@code {path, type, missing}}; an empty map
     *     with no state
     */
    public static Map<String, Object> report(Content content) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (content == null) {
            return out;
        }
        out.put("pageStatus", content.pageStatus());
        out.put("activeLanguages", content.activeLanguages());
        out.put("scanned", content.scanned());
        out.put("unpublished", content.unpublished().stream()
                .map(node -> Map.of("path", node.path(), "type", node.type(), "status", node.status()))
                .toList());
        out.put("untranslated", content.untranslated().stream()
                .map(node -> Map.of("path", node.path(), "type", node.type(), "missing", node.missing()))
                .toList());
        return out;
    }

    /** The content items under a node, in document order, down to the next page, at most {@link #MAX_ITEMS}. */
    private static void collect(JCRNodeWrapper node, List<JCRNodeWrapper> items) throws RepositoryException {
        for (JCRNodeWrapper child : node.getNodes()) {
            if (items.size() > MAX_ITEMS) {
                return;
            }
            if (child.isNodeType(PAGE_TYPE)) {
                continue;
            }
            if (child.isNodeType(CONTENT_TYPE)) {
                items.add(child);
            }
            collect(child, items);
        }
    }

    /** The languages of the site that are active in the edit workspace. */
    private static Set<String> activeLanguages(JCRSiteNode site) {
        if (site == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>(site.getLanguages());
        out.removeAll(site.getInactiveLanguages());
        return out;
    }

    private static Set<String> translations(JCRNodeWrapper node) throws RepositoryException {
        Set<String> out = new LinkedHashSet<>();
        NodeIterator translations = node.getI18Ns();
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(Constants.JCR_LANGUAGE)) {
                out.add(translation.getProperty(Constants.JCR_LANGUAGE).getString());
            }
        }
        return out;
    }
}

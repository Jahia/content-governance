package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jsoup.nodes.Document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The facts that the tabs SEO, Readability, Links and Jahia health of the drawer show beside the findings
 * of the page check: the tags of the page, the statistics of its text, its links and the state of its
 * content. Each domain of checks measures its own facts, from the same HTML as its checks.
 *
 * <p>Thread safety: stateless.</p>
 */
public final class PageFacts {

    private PageFacts() {
    }

    /**
     * @param html             the rendered HTML
     * @param read             what the render read in the repository, by the key of the fact, such as
     *                         {@link LinkChecks#INTERNAL} and {@link HealthChecks#CONTENT}
     * @param language         the language of the content, such as {@code en}, or null
     * @param maxSentenceWords the maximum of the rule {@code readability.longSentences}, or null
     * @return {@code seo}, {@code readability}, {@code links} and {@code jahia}: the facts of each tab
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> of(Document html, Map<String, Object> read, String language,
                                         Integer maxSentenceWords) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seo", SeoChecks.report(html, language));
        out.put("readability", ReadabilityChecks.report(html, language, maxSentenceWords));
        Set<String> hosts = read.get(LinkChecks.LOCAL_HOSTS) instanceof Set<?> set ? (Set<String>) set : null;
        LinkCheck.InternalLinks internal = read.get(LinkChecks.INTERNAL) instanceof LinkCheck.InternalLinks links
                ? links : null;
        out.put("links", LinkChecks.report(html, hosts, internal));
        HealthChecks.Content content = read.get(HealthChecks.CONTENT) instanceof HealthChecks.Content state
                ? state : null;
        out.put("jahia", HealthChecks.report(content));
        return out;
    }
}

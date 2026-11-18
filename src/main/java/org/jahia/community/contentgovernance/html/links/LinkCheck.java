/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.links;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jsoup.nodes.Document;

import javax.jcr.RepositoryException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The links of rendered HTML, and the broken ones.
 *
 * <p>An internal link is broken when its node does not exist, or when its node is not published in
 * the live workspace, because a visitor then gets a 404. An external link is broken when it answers
 * HTTP 404 or 410. {@link ExternalLinkChecker} states which addresses the server never calls.</p>
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class LinkCheck {

    /** The external links checked on one render. The others are counted, and not checked. */
    public static final int MAX_EXTERNAL_LINKS = 100;
    /** The time to connect to an external link, and then the time to get the status of its answer. */
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** An internal link that a visitor cannot open: {@code missing} or {@code not_published}. */
    public record BrokenPath(String path, String reason) {
    }

    /**
     * @param checked               the internal links with a JCR path
     * @param notCheckedVanityLinks the internal links with no JCR path, such as a vanity URL
     */
    public record InternalLinks(int checked, int notCheckedVanityLinks, List<BrokenPath> broken) {
    }

    /** An external link and the HTTP status of its answer, or 0 when none came. */
    public record UrlStatus(String url, int status) {
    }

    /**
     * @param found             the external links of the HTML
     * @param skippedAboveLimit the links above {@link #MAX_EXTERNAL_LINKS}, which were not checked
     * @param blocked           the links to an address that the server never calls
     */
    public record ExternalLinks(int found, int skippedAboveLimit, int ok, List<UrlStatus> broken,
                                List<UrlStatus> notVerified, List<UrlStatus> unreachable, List<String> blocked) {
    }

    private LinkCheck() {
    }

    /**
     * Checks the internal links in the live workspace, as the user of the session, in its locale.
     *
     * @param localHosts the lower-case host names of this Jahia: an absolute link to one of them is internal
     */
    public static InternalLinks internal(Document html, Set<String> localHosts, JCRSessionWrapper session)
            throws RepositoryException {
        LinkExtractor.Links links = LinkExtractor.extract(html, localHosts);
        Set<String> live = JCRTemplate.getInstance().doExecute(session.getUser(), Constants.LIVE_WORKSPACE,
                session.getLocale(), liveSession -> existing(liveSession, links.internalPaths));
        List<BrokenPath> broken = new ArrayList<>();
        for (String path : links.internalPaths) {
            if (!live.contains(path)) {
                broken.add(new BrokenPath(path, exists(session, path) ? "not_published" : "missing"));
            }
        }
        return new InternalLinks(links.internalPaths.size(), links.unresolved, List.copyOf(broken));
    }

    /**
     * Checks the external links from this server. Call it outside a JCR session: it waits on the
     * network, up to {@link #TIMEOUT} twice for each link.
     */
    public static ExternalLinks external(Document html, Set<String> localHosts) {
        List<String> urls = new ArrayList<>(LinkExtractor.extract(html, localHosts).externalUrls);
        int found = urls.size();
        int skipped = Math.max(0, found - MAX_EXTERNAL_LINKS);
        if (skipped > 0) {
            urls = urls.subList(0, MAX_EXTERNAL_LINKS);
        }
        List<UrlStatus> broken = new ArrayList<>();
        List<UrlStatus> notVerified = new ArrayList<>();
        List<UrlStatus> unreachable = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        int ok = 0;
        for (ExternalLinkChecker.Result result : ExternalLinkChecker.check(urls, TIMEOUT)) {
            switch (result.state()) {
                case ExternalLinkChecker.BROKEN -> broken.add(new UrlStatus(result.url(), result.status()));
                case ExternalLinkChecker.NOT_VERIFIED -> notVerified.add(new UrlStatus(result.url(), result.status()));
                case ExternalLinkChecker.UNREACHABLE -> unreachable.add(new UrlStatus(result.url(), result.status()));
                case ExternalLinkChecker.BLOCKED -> blocked.add(result.url());
                default -> ok++;
            }
        }
        return new ExternalLinks(found, skipped, ok, List.copyOf(broken), List.copyOf(notVerified),
                List.copyOf(unreachable), List.copyOf(blocked));
    }

    private static Set<String> existing(JCRSessionWrapper session, Set<String> paths) {
        Set<String> existing = new HashSet<>();
        for (String path : paths) {
            if (exists(session, path)) {
                existing.add(path);
            }
        }
        return existing;
    }

    /** A link path that is not a valid JCR path names no node. */
    private static boolean exists(JCRSessionWrapper session, String path) {
        try {
            return session.nodeExists(path);
        } catch (RepositoryException | IllegalArgumentException e) {
            return false;
        }
    }
}

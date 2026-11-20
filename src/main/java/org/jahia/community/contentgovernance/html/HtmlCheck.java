/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jahia.community.contentgovernance.html.render.ServerRender;
import org.jahia.community.contentgovernance.html.rules.HtmlRuleChecks;
import org.jahia.community.contentgovernance.html.rules.HtmlRules;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The HTML check: Jahia renders a page or one content item on the server, and each check of the scope
 * {@code PAGE} reads the HTML, with the rules of the site. A page gets every HTML rule, and an item gets
 * the HTML rules of {@link Scope#ITEM} only.
 *
 * <p>A render reads the workspace of the session, as its user: in {@code default}, the content that
 * is not published yet. The caller renders inside its session, then checks the HTML outside it.</p>
 *
 * <p>Thread-safety: stateless.</p>
 */
@Component(service = HtmlCheck.class)
public class HtmlCheck {

    /** Host names that always serve this Jahia, for a link written with one of them. */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    @Reference
    private CheckRegistry checks;

    public HtmlCheck() {
        // The constructor of Declarative Services.
    }

    HtmlCheck(CheckRegistry checks) {
        this.checks = checks;
    }

    /** What {@link #render} found at a path. */
    public sealed interface Outcome permits Rendered, NotFound, RenderFailed {
    }

    /**
     * The HTML of a page or of an item. It holds no node, so it stays valid outside the session.
     *
     * @param localHosts the lower-case host names of this Jahia and of the site: a link to one of
     *                   them is an internal link
     */
    public record Rendered(String path, String primaryType, Scope scope, Document html, Set<String> localHosts)
            implements Outcome {
    }

    /** No node at the path, or none that the user of the session can read. */
    public record NotFound(String path) implements Outcome {
    }

    /** Jahia could not render the node, such as a page whose template is missing. */
    public record RenderFailed(String path, String details) implements Outcome {
    }

    /**
     * Renders the node at the path with the session.
     *
     * @param session the session of the user, in the workspace and the locale to check
     * @param path    the absolute path of a page or of a content item
     */
    public Outcome render(JCRSessionWrapper session, String path) throws RepositoryException {
        JCRNodeWrapper node;
        try {
            node = session.getNode(path);
        } catch (PathNotFoundException | IllegalArgumentException e) {
            return new NotFound(path);
        }
        Scope scope = ServerRender.isPage(node) ? Scope.PAGE : Scope.ITEM;
        String html;
        try {
            html = ServerRender.render(node, session);
        } catch (RenderException e) {
            return new RenderFailed(node.getPath(), e.getMessage() == null ? "" : e.getMessage());
        }
        return new Rendered(node.getPath(), node.getPrimaryNodeTypeName(), scope, Jsoup.parse(html),
                localHosts(node.getResolveSite()));
    }

    /**
     * Runs the checks of the scope {@code PAGE} on the rules that the site of the node keeps on.
     *
     * @param language the language of the content, such as {@code en}: the language of the page must match it
     * @return the findings, in the order of the rules, at most {@link HtmlRules#MAX_FINDINGS} for the HTML rules
     */
    public List<Finding> findings(Rendered rendered, Locale language) {
        CheckInput input = CheckInput.page(rendered.path(), language == null ? null : language.getLanguage(),
                        rendered.html().outerHtml())
                .withFact(HtmlRuleChecks.DOCUMENT, rendered.html())
                .withFact(HtmlRuleChecks.HTML_SCOPE, rendered.scope());
        return checks.run(input, Set.of()).stream().map(CheckRegistry.Checked::finding).toList();
    }

    /** The internal links of the HTML, read in the live workspace. Call it inside the session of the render. */
    public LinkCheck.InternalLinks internalLinks(Rendered rendered, JCRSessionWrapper session) throws RepositoryException {
        return LinkCheck.internal(rendered.html(), rendered.localHosts(), session);
    }

    /** The external links of the HTML, called from this server. Call it outside a session: it waits on the network. */
    public LinkCheck.ExternalLinks externalLinks(Rendered rendered) {
        return LinkCheck.external(rendered.html(), rendered.localHosts());
    }

    private static Set<String> localHosts(JCRSiteNode site) {
        Set<String> hosts = new HashSet<>(LOCAL_HOSTS);
        if (site != null) {
            for (String name : site.getAllServerNames()) {
                if (name != null && !name.isBlank()) {
                    hosts.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(hosts);
    }
}

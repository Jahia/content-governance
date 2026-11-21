/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.render;

import org.jahia.bin.Render;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderException;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.jahia.services.usermanager.JahiaUser;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

/**
 * Renders a page, or one content item, inside Jahia with no HTTP request: the HTML that a visitor of
 * the workspace of the session gets, as the user of the session, in its locale. Nothing reaches a
 * client, so a scheduled job and an AI agent can call it.
 *
 * <p>A page renders with its template, and so does a content item shown as its own page, such as a news
 * item ({@code jmix:mainResource}). Another item renders with its own view only, as a page includes it,
 * and the page that holds it is the main resource of the render.</p>
 *
 * <p>Thread-safety: stateless. The thread gets the user and the locale of the session for the time
 * of the render, because the views of Jahia read them there, and gets its own back after.</p>
 */
public final class ServerRender {

    private static final String PAGE_TYPE = "jnt:page";
    private static final String MAIN_RESOURCE_TYPE = "jmix:mainResource";
    private static final String HTML = "html";
    private static final String DEFAULT_VIEW = "default";
    private static final String LOCAL_SERVER = "localhost";

    private ServerRender() {
    }

    /** Whether the node renders as a whole page: a page, or a content item shown as its own page. */
    public static boolean isPage(JCRNodeWrapper node) throws RepositoryException {
        return node.isNodeType(PAGE_TYPE) || node.isNodeType(MAIN_RESOURCE_TYPE);
    }

    /**
     * @param node    a page, or a content item
     * @param session the session to read and render with: its user, workspace and locale
     * @return the HTML of the page, or of the item alone
     */
    public static String render(JCRNodeWrapper node, JCRSessionWrapper session)
            throws RepositoryException, RenderException {
        JahiaUser user = session.getUser();
        Locale locale = session.getLocale() != null ? session.getLocale() : Locale.ENGLISH;
        String servletPath = Render.getRenderServletPath();
        // The path after the render servlet: /<workspace>/<locale><node path>.html
        String pathInfo = String.join("/", "", session.getWorkspace().getName(), locale + node.getPath() + ".html");
        JCRSiteNode site = node.getResolveSite();
        String serverName = site == null || site.getServerName() == null || site.getServerName().isBlank()
                ? LOCAL_SERVER : site.getServerName();
        HttpServletRequest request = InProcessHttp.request(
                new InProcessHttp.Target(servletPath, pathInfo, serverName, user, locale));
        HttpServletResponse response = InProcessHttp.response(locale);

        JCRSessionFactory factory = JCRSessionFactory.getInstance();
        JahiaUser previousUser = factory.getCurrentUser();
        Locale previousLocale = factory.getCurrentLocale();
        Locale previousFallback = factory.getFallbackLocale();
        factory.setCurrentUser(user);
        factory.setCurrentLocale(locale);
        factory.setFallbackLocale(locale);
        try {
            RenderContext context = new RenderContext(request, response, user);
            context.setSite(site);
            context.setServletPath(servletPath);
            context.setWorkspace(session.getWorkspace().getName());
            context.setEditMode(false);
            if (isPage(node)) {
                Resource page = new Resource(node, HTML, DEFAULT_VIEW, Resource.CONFIGURATION_PAGE);
                context.setMainResource(page);
                return RenderService.getInstance().render(page, context);
            }
            JCRNodeWrapper holder = JCRContentUtils.getParentOfType(node, PAGE_TYPE);
            context.setMainResource(new Resource(holder == null ? node : holder, HTML, DEFAULT_VIEW,
                    Resource.CONFIGURATION_PAGE));
            return RenderService.getInstance().render(
                    new Resource(node, HTML, DEFAULT_VIEW, Resource.CONFIGURATION_MODULE), context);
        } finally {
            factory.setCurrentUser(previousUser);
            factory.setCurrentLocale(previousLocale);
            factory.setFallbackLocale(previousFallback);
        }
    }
}

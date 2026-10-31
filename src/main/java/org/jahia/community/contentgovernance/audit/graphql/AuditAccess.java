package org.jahia.community.contentgovernance.audit.graphql;

import graphql.schema.DataFetchingEnvironment;
import java.util.regex.Pattern;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import org.jahia.community.contentgovernance.settings.Features;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;

/**
 * Who may read and store Page quality audits: a signed-in user who may open jContent on the site,
 * on a site that enables the module and keeps the Page quality section on, for a page or main
 * resource they can read, in a language of the site. Every check runs with the caller's own session
 * in the default workspace, before the service touches the module's private store.
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class AuditAccess {

    static final String DENIED = "Permission denied";
    /** The permission of the Compliance dashboard: whoever may open jContent on the site. */
    static final String SITE_PERMISSION = "jContentAccess";
    private static final String WORKSPACE = "default";
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8}){0,3}$");

    private AuditAccess() {
    }

    /** A page the caller may read, with its site. */
    record Target(JCRSiteNode site, JCRNodeWrapper page) {
    }

    /**
     * @param path     path of a page or main resource
     * @param language content language
     * @return the page, read with the caller's session, and its site
     * @throws DataFetchingException with one answer for every refusal
     */
    static Target page(String path, String language) {
        user();
        if (path == null || !path.startsWith("/sites/") || path.contains("..") || !validLanguage(language)) {
            throw new DataFetchingException(DENIED);
        }
        try {
            JCRNodeWrapper page = JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE).getNode(path);
            if (!page.isNodeType("jnt:page") && !page.isNodeType("jmix:mainResource")) {
                throw new DataFetchingException(DENIED);
            }
            JCRSiteNode site = page.getResolveSite();
            requireSite(site, language);
            return new Target(site, page);
        } catch (PathNotFoundException e) {
            throw new DataFetchingException(DENIED);
        } catch (RepositoryException e) {
            throw new DataFetchingException("The page could not be read");
        }
    }

    /**
     * @param siteKey  key of the site
     * @param language content language
     * @return the site, read with the caller's session
     * @throws DataFetchingException with one answer for every refusal
     */
    static JCRSiteNode site(String siteKey, String language) {
        user();
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches() || !validLanguage(language)) {
            throw new DataFetchingException(DENIED);
        }
        try {
            if (JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE).getNode("/sites/" + siteKey) instanceof JCRSiteNode site) {
                requireSite(site, language);
                return site;
            }
        } catch (PathNotFoundException e) {
            // answered below, as for a node that is not a site
        } catch (RepositoryException e) {
            throw new DataFetchingException("The site could not be read");
        }
        throw new DataFetchingException(DENIED);
    }

    /** @return the username of the caller, refusing a guest */
    static String user() {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user)) {
            throw new DataFetchingException(DENIED);
        }
        return user.getUsername();
    }

    /** Refuses a request that runs {@code field} more than {@code max} times (aliases). */
    static void count(DataFetchingEnvironment environment, String field, int max) {
        if (environment == null || environment.getGraphQlContext() == null) {
            return;
        }
        String key = AuditAccess.class.getName() + "." + field;
        int count = environment.getGraphQlContext().<Integer>getOrDefault(key, 0) + 1;
        environment.getGraphQlContext().put(key, count);
        if (count > max) {
            throw new DataFetchingException("Too many " + field + " in one request");
        }
    }

    private static boolean validLanguage(String language) {
        return language != null && LANGUAGE.matcher(language).matches();
    }

    private static void requireSite(JCRSiteNode site, String language) {
        if (site == null || !site.hasPermission(SITE_PERMISSION) || !site.getLanguages().contains(language)
                || !SiteFeatures.isEnabled(site.getPath(), Features.AUDIT)) {
            throw new DataFetchingException(DENIED);
        }
    }
}

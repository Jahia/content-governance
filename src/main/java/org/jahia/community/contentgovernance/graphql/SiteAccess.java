package org.jahia.community.contentgovernance.graphql;

import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.regex.Pattern;

/**
 * Resolves the site a settings field is about, with the caller's own session in the default
 * workspace, and refuses a guest, a malformed key and a site the caller cannot read with one answer.
 */
final class SiteAccess {

    static final String DENIED = "Permission denied";
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private SiteAccess() {
    }

    /**
     * @param siteKey the key of the site
     * @return the site node, read with the caller's session
     * @throws DataFetchingException when the caller is a guest or cannot read the site
     */
    static JCRSiteNode site(String siteKey) {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user) || siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            throw new DataFetchingException(DENIED);
        }
        try {
            JCRNodeWrapper node = JCRSessionFactory.getInstance().getCurrentUserSession("default").getNode("/sites/" + siteKey);
            if (node instanceof JCRSiteNode site) {
                return site;
            }
        } catch (PathNotFoundException e) {
            // answered below, the same way as a node that is not a site
        } catch (RepositoryException e) {
            throw new DataFetchingException("The site could not be read");
        }
        throw new DataFetchingException(DENIED);
    }
}

package org.jahia.community.contentgovernance.geo.util;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

/** Finding a site's pages in the repository. Stateless. */
public final class SitePages {

    private SitePages() {
    }

    /**
     * The site's home page: the child page flagged {@code j:isHomePage}, else the one named
     * {@code home}, else null.
     *
     * @param site the site node, from any session
     * @return the home page, or null
     */
    public static JCRNodeWrapper homeOf(JCRNodeWrapper site) {
        List<JCRNodeWrapper> pages = pageChildren(site);
        for (JCRNodeWrapper c : pages) {
            try {
                if (c.hasProperty("j:isHomePage") && c.getProperty("j:isHomePage").getBoolean()) {
                    return c;
                }
            } catch (RepositoryException e) {
                // keep looking
            }
        }
        for (JCRNodeWrapper c : pages) {
            if ("home".equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static List<JCRNodeWrapper> pageChildren(JCRNodeWrapper parent) {
        List<JCRNodeWrapper> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        try {
            for (JCRNodeWrapper c : parent.getNodes()) {
                if (c.isNodeType("jnt:page")) {
                    out.add(c);
                }
            }
        } catch (RepositoryException e) {
            // A branch we cannot read has no home page for us.
        }
        return out;
    }
}

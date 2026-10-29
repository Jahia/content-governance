package org.jahia.community.contentgovernance.settings;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which sections and tabs of the module a site uses, and the server-side gate every endpoint calls.
 *
 * <p>A feature is enabled on a site when the site enables the module (directly or through a
 * module it enables, as jContent checks it) and neither the feature nor its section is in the
 * site's list of switched-off ids. A site never configured has an empty list: everything on.</p>
 *
 * <p>Stored on {@code /sites/<key>/content-governance} ({@code nt:unstructured} +
 * {@code jmix:nolive}, never published), property {@value #DISABLED}. Read with a system session,
 * because the gate also runs for scheduled jobs that have no caller; written with the caller's own
 * session, so a caller without write access on the site cannot change it.</p>
 *
 * <p>Thread safety: stateless; every call opens its own session.</p>
 */
public final class SiteFeatures {

    /** The module id, as sites list it in {@code j:installedModules}. */
    public static final String MODULE = "content-governance";
    /** The permission on the site that the settings screen and the save require. */
    public static final String SETTINGS_PERMISSION = "site-admin";
    static final String STORE_NODE = "content-governance";
    static final String DISABLED = "disabledFeatures";
    private static final String EDIT_WORKSPACE = "default";

    private static final Logger logger = LoggerFactory.getLogger(SiteFeatures.class);

    private SiteFeatures() {
    }

    /**
     * Whether a site enables the module and the feature.
     *
     * @param sitePath {@code /sites/<key>}
     * @param feature  a section id or a {@code section.tab} id from {@link Features}
     * @return false when the site is missing, does not enable the module, or switched the feature off
     */
    public static boolean isEnabled(String sitePath, String feature) {
        if (sitePath == null || feature == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    session -> {
                        if (!session.nodeExists(sitePath)) {
                            return false;
                        }
                        JCRNodeWrapper node = session.getNode(sitePath);
                        if (!(node instanceof JCRSiteNode) || !moduleEnabled((JCRSiteNode) node)) {
                            return false;
                        }
                        return isOn(read(node), feature);
                    }));
        } catch (RepositoryException e) {
            logger.warn("could not read the content governance settings of {}", sitePath, e);
            return false;
        }
    }

    /** Whether a site enables the module, directly or through a module it enables. */
    public static boolean moduleEnabled(JCRSiteNode site) {
        return site != null && site.getInstalledModulesWithAllDependencies().contains(MODULE);
    }

    /** True when neither {@code feature} nor its section is in {@code disabled}. */
    public static boolean isOn(Set<String> disabled, String feature) {
        return !disabled.contains(feature) && !disabled.contains(Features.sectionOf(feature));
    }

    /**
     * The ids a site switched off, read through whatever session the site node came from.
     *
     * @param site the site node
     * @return the ids, empty when the site was never configured
     */
    public static Set<String> read(JCRNodeWrapper site) throws RepositoryException {
        if (!site.hasNode(STORE_NODE)) {
            return Collections.emptySet();
        }
        JCRNodeWrapper store = site.getNode(STORE_NODE);
        if (!store.hasProperty(DISABLED)) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Value v : store.getProperty(DISABLED).getValues()) {
            out.add(v.getString());
        }
        return out;
    }

    /**
     * Stores the ids a site switches off, through the caller's session in the default workspace.
     *
     * @param site     the site node, from the caller's own session
     * @param disabled the ids to switch off; unknown ids are dropped
     * @return the ids stored
     * @throws javax.jcr.AccessDeniedException when the caller may not write on the site
     */
    public static List<String> save(JCRSiteNode site, Collection<String> disabled) throws RepositoryException {
        List<String> ids = new ArrayList<>();
        for (String id : disabled) {
            if (Features.isKnown(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        JCRSessionWrapper session = site.getSession();
        JCRNodeWrapper store;
        if (site.hasNode(STORE_NODE)) {
            store = site.getNode(STORE_NODE);
        } else {
            store = site.addNode(STORE_NODE, "nt:unstructured");
            store.addMixin("jmix:nolive");
        }
        store.setProperty(DISABLED, ids.toArray(new String[0]));
        session.save();
        return ids;
    }
}

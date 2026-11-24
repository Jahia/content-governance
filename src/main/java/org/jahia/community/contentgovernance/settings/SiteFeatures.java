package org.jahia.community.contentgovernance.settings;

import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.Domain;
import org.jahia.community.contentgovernance.rules.LegacyFeatures;
import org.jahia.community.contentgovernance.rules.ShippedRules;
import org.jahia.community.contentgovernance.rules.SiteLayout;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which panels, domains and subdomains a site uses, and the server-side gate every endpoint calls.
 *
 * <p>A feature is enabled on a site when the site enables the module (directly or through a module it enables, as
 * jContent checks it) and its layout keeps it on: the {@code off} list of the {@link SiteLayout} switches off a
 * panel of the drawer, such as {@value Features#GOVERNANCE} or {@code chat}, a domain or a subdomain. A domain or a
 * subdomain is on as {@link ContentDomains#isOn} states it, in a panel that is on. A site with no layout keeps every
 * item at its default. The panels of other modules, such as a chat, are switched in the same list, and their module
 * reads it.</p>
 *
 * <p>The gate takes the id of a domain or a subdomain, such as {@code quality}, or a key of the drawer registry,
 * such as {@code audit}, which {@link LegacyFeatures#layoutId} maps.</p>
 *
 * <p>Thread safety: stateless; every call reads the layout anew.</p>
 */
public final class SiteFeatures {

    /** The module id, as sites list it in {@code j:installedModules}. */
    public static final String MODULE = ShippedRules.OWN_MODULE;
    /** The permission on the site that the layout screen and its save require. */
    public static final String SETTINGS_PERMISSION = "site-admin";
    /** The node of a site that holds the private data of the module, such as the audits of its pages. */
    public static final String STORE_NODE = "content-governance";

    private static final Logger logger = LoggerFactory.getLogger(SiteFeatures.class);

    private SiteFeatures() {
    }

    /**
     * Whether a site enables the module and keeps the feature on.
     *
     * @param sitePath {@code /sites/<key>}
     * @param feature  the id of a panel, a domain or a subdomain, or a key of the drawer registry
     * @return false when the site is missing, does not enable the module, or keeps the feature off
     */
    public static boolean isEnabled(String sitePath, String feature) {
        if (sitePath == null || feature == null) {
            return false;
        }
        // A system session: the gate also runs for scheduled jobs, which have no caller
        try {
            return Boolean.TRUE.equals(JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "default",
                    Locale.ENGLISH, (JCRCallback<Boolean>) session -> session.nodeExists(sitePath)
                            && session.getNode(sitePath) instanceof JCRSiteNode site && moduleEnabled(site)
                            && isOn(ContentDomains.read(session, site), LegacyFeatures.layoutId(feature))));
        } catch (RepositoryException e) {
            logger.warn("could not read the layout of {}", sitePath, e);
            return false;
        }
    }

    /**
     * True when the hierarchy of a site keeps an item on. A domain or a subdomain needs its panel on. Another id,
     * such as a tab that another module adds to the governance panel, needs the governance panel on, and the site
     * not to switch it or its section off.
     *
     * @param hierarchy the domains and the subdomains of the site, with its layout
     * @param id        the id of a domain, a subdomain or a tab
     */
    static boolean isOn(ContentDomains hierarchy, String id) {
        SiteLayout layout = hierarchy.layout();
        if (hierarchy.declares(id)) {
            String panel = hierarchy.panelOf(id);
            return (panel == null || !layout.isOff(panel)) && hierarchy.isOn(id);
        }
        return !layout.isOff(Features.GOVERNANCE) && !layout.isOff(id) && !layout.isOff(Features.sectionOf(id));
    }

    /**
     * The ids that are off on a site: the ids that its layout switches off, then each domain and subdomain that is
     * not on, as the drawer reads them.
     *
     * @param hierarchy the domains and the subdomains of the site, with its layout
     * @return the ids
     */
    public static Set<String> off(ContentDomains hierarchy) {
        Set<String> out = new LinkedHashSet<>(hierarchy.layout().off());
        for (Domain domain : hierarchy.allDomains()) {
            if (!isOn(hierarchy, domain.id())) {
                out.add(domain.id());
            }
            for (Domain subdomain : hierarchy.allSubdomainsOf(domain.id())) {
                if (!isOn(hierarchy, subdomain.id())) {
                    out.add(subdomain.id());
                }
            }
        }
        return out;
    }

    /** Whether a site enables the module, directly or through a module it enables. */
    public static boolean moduleEnabled(JCRSiteNode site) {
        return site != null && site.getInstalledModulesWithAllDependencies().contains(MODULE);
    }

    /**
     * The module's private node of a site, created and made private on first use. Call it with a system session:
     * the node is private to the module ({@link PrivateStore}).
     *
     * @param site the site node
     * @return the node, not yet saved when it was just created
     * @throws RepositoryException on a repository failure
     */
    public static JCRNodeWrapper storeNode(JCRNodeWrapper site) throws RepositoryException {
        JCRNodeWrapper store;
        if (site.hasNode(STORE_NODE)) {
            store = site.getNode(STORE_NODE);
        } else {
            store = site.addNode(STORE_NODE, "nt:unstructured");
            store.addMixin("jmix:nolive");
        }
        PrivateStore.protect(store);
        return store;
    }
}

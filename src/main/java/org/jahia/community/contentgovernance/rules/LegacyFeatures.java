package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The switches of a version before 2.0.0, and their upgrade. A site kept the ids that it switched off in the
 * property {@value #DISABLED} of its node {@value #STORE_NODE}. These ids were the keys of the drawer tabs and of
 * their parts, such as {@code audit} and {@code geo.crawler}. The {@code off} list of the {@link SiteLayout} of a site
 * replaces this property, and names the domains and the subdomains, such as {@code quality} and {@code geo.crawlers}.
 * The ids of the panels, such as {@code governance} and {@code chat}, stay the same.
 *
 * <p>When the module starts on the processing server, each site that kept such ids gets them in the {@code off} list
 * of its layout, and loses the property. A site with no layout gets a layout that places no domain: it switches the
 * items off, keeps the order of the system site, and marks no item as new.</p>
 */
public final class LegacyFeatures {

    /** The node of a site that held the switches, and still holds the private data of the module. */
    static final String STORE_NODE = "content-governance";
    /** The property of that node that held the ids that the site switched off. */
    static final String DISABLED = "disabledFeatures";

    /** The id in the layout of each key of a drawer tab, or of a part of a tab, whose id changed. */
    private static final Map<String, String> LAYOUT_IDS = Map.of(
            "audit", "quality",
            "audit.jahia", "health",
            "audit.links", "links",
            "audit.accessibility", "accessibility",
            "audit.vitals", "vitals",
            "audit.ecodesign", "ecodesign",
            "audit.seo", "seo",
            "audit.readability", "readability",
            "geo.crawler", "geo.crawlers");

    private static final Logger logger = LoggerFactory.getLogger(LegacyFeatures.class);

    private LegacyFeatures() {
    }

    /**
     * The id in the layout of a key of a drawer tab or of a part of a tab. Example: {@code audit.jahia} gives
     * {@code health}, and {@code geo.citations}, which did not change, gives itself.
     *
     * @param key a key of the registry of the drawer, or an id of the layout
     * @return the id in the layout
     */
    public static String layoutId(String key) {
        return LAYOUT_IDS.getOrDefault(key, key);
    }

    /** Moves the switches of each site into its layout. A failure is logged, and the module starts anyway. */
    static void migrate() {
        try {
            int sites = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "default", Locale.ENGLISH,
                    (JCRCallback<Integer>) session -> TreeSeeder.discardOnFailure(session, () -> moveSwitches(session)));
            if (sites > 0) {
                logger.info("Moved the switches of {} sites into the off list of their layout", sites);
            }
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The switches of the sites could not all move into their layout", e);
        }
    }

    static int moveSwitches(JCRSessionWrapper session) throws RepositoryException {
        int moved = 0;
        for (JCRNodeWrapper site : session.getNode("/sites").getNodes()) {
            if (site.hasNode(STORE_NODE) && site.getNode(STORE_NODE).hasProperty(DISABLED) && moveSwitches(session, site)) {
                moved++;
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return moved;
    }

    /** Adds the switches of one site to the off list of its layout, and removes the property. */
    private static boolean moveSwitches(JCRSessionWrapper session, JCRNodeWrapper site) throws RepositoryException {
        JCRNodeWrapper store = site.getNode(STORE_NODE);
        Set<String> ids = new LinkedHashSet<>();
        for (Value value : store.getProperty(DISABLED).getValues()) {
            ids.add(layoutId(value.getString()));
        }
        if (!ids.isEmpty()) {
            if (ContentDomains.SYSTEM_SITE.equals(site.getPath()) || !site.hasNode("contents")) {
                logger.warn("The site {} has no folder contents for a layout, so it loses the switches {}", site.getName(), ids);
            } else {
                SiteLayout layout = SiteLayout.read(session, site.getPath());
                Set<String> off = new LinkedHashSet<>(layout.off());
                off.addAll(ids);
                SiteLayout.write(session, site.getPath(), layout.withOff(off));
            }
        }
        store.getProperty(DISABLED).remove();
        return true;
    }
}

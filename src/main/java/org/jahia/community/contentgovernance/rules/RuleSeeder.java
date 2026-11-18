package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies the base catalog into each site that enables the module: one {@value ContentRules#TYPE} node
 * per rule, in {@value ContentRules#FOLDER}. The site then changes a rule on its node.
 *
 * <p>Runs when a site enables the module, and for every such site when the module starts, so that a new
 * version adds its new rules. It adds the rules that a site does not have, and never changes a node
 * that exists. The nodes stay unpublished: {@link ContentRules} reads the edit workspace.</p>
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class RuleSeeder extends DefaultEventListener {

    private static final String INSTALLED_MODULES = "j:installedModules";
    private static final String EDIT_WORKSPACE = "default";
    private static final String FOLDER_TYPE = "jnt:contentFolder";
    private static final Logger logger = LoggerFactory.getLogger(RuleSeeder.class);

    public RuleSeeder() {
        setWorkspace(EDIT_WORKSPACE);
    }

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String getPath() {
        return "/sites";
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{"jnt:virtualsite"};
    }

    /** Seeds every site that enables the module, for the rules a new version adds. */
    @Activate
    public void seedAllSites() {
        try {
            List<String> sites = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<List<String>>) session -> {
                        List<String> out = new ArrayList<>();
                        for (JCRNodeWrapper node : session.getNode("/sites").getNodes()) {
                            if (node instanceof JCRSiteNode site && SiteFeatures.moduleEnabled(site)) {
                                out.add(site.getPath());
                            }
                        }
                        return out;
                    });
            sites.forEach(RuleSeeder::seed);
        } catch (RepositoryException e) {
            logger.warn("Could not list the sites to seed the content rules", e);
        }
    }

    @Override
    public void onEvent(EventIterator events) {
        Set<String> sites = new LinkedHashSet<>();
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String site = isExternal(event) ? null : sitePathOf(event.getPath());
                if (site != null) {
                    sites.add(site);
                }
            } catch (RepositoryException e) {
                logger.debug("unreadable event", e);
            }
        }
        sites.forEach(RuleSeeder::seed);
    }

    /** {@code /sites/<key>} for a change of the {@code j:installedModules} of that site, else null. */
    static String sitePathOf(String propertyPath) {
        String suffix = "/" + INSTALLED_MODULES;
        if (propertyPath == null || !propertyPath.endsWith(suffix)) {
            return null;
        }
        String site = propertyPath.substring(0, propertyPath.length() - suffix.length());
        return site.matches("/sites/[^/]+") ? site : null;
    }

    /** Adds the missing rules of a site that enables the module. Failures are logged, never thrown. */
    static void seed(String sitePath) {
        try {
            int added = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<Integer>) session -> addMissing(session, sitePath));
            if (added > 0) {
                logger.info("Added {} content rules to {}", added, sitePath);
            }
        } catch (RepositoryException e) {
            logger.warn("Could not add the content rules of {}", sitePath, e);
        }
    }

    private static int addMissing(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        if (!(session.nodeExists(sitePath) && session.getNode(sitePath) instanceof JCRSiteNode site)
                || !SiteFeatures.moduleEnabled(site) || !site.hasNode("contents")) {
            return 0;
        }
        JCRNodeWrapper folder = site;
        String[] names = ContentRules.FOLDER.split("/");
        for (int i = 0; i < names.length; i++) {
            String type = i == names.length - 1 ? ContentRules.FOLDER_TYPE : FOLDER_TYPE;
            folder = folder.hasNode(names[i]) ? folder.getNode(names[i]) : folder.addNode(names[i], type);
        }
        if (!folder.isNodeType(ContentRules.FOLDER_TYPE)) {
            logger.warn("{} is not a {}, so it cannot take the content rules", folder.getPath(), ContentRules.FOLDER_TYPE);
            return 0;
        }
        Set<String> present = new HashSet<>();
        for (JCRNodeWrapper node : folder.getNodes()) {
            if (node.isNodeType(ContentRules.TYPE) && node.hasProperty(ContentRules.RULE_ID)) {
                present.add(node.getProperty(ContentRules.RULE_ID).getString());
            }
        }
        int added = 0;
        for (ContentRule rule : RuleCatalog.base().values()) {
            if (!present.contains(rule.id()) && !folder.hasNode(rule.id())) {
                JCRNodeWrapper node = folder.addNode(rule.id(), ContentRules.TYPE);
                node.setProperty(ContentRules.RULE_ID, rule.id());
                node.setProperty(ContentRules.ENABLED, rule.enabled());
                node.setProperty(ContentRules.SEVERITY, rule.severity());
                if (rule.minimum() != null) {
                    node.setProperty(ContentRules.MINIMUM, rule.minimum());
                }
                if (rule.maximum() != null) {
                    node.setProperty(ContentRules.MAXIMUM, rule.maximum());
                }
                added++;
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return added;
    }
}

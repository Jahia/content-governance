package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.settings.SettingsBean;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Copies the rules that the modules ship into each site that enables this module: one
 * {@value ContentRules#TYPE} node per rule, in the folder {@link ContentRules#folderOf} of the module.
 * A site gets the rules of this module, and of each other module that it installs.
 *
 * <p>Runs on the node where a site installs a module, for that site. Runs on the processing server when a
 * module that ships rules starts, and for every site when this module starts: a browsing node of a cluster
 * shares the repository and seeds nothing then. Each node keeps the fingerprint of the values that the
 * seeder wrote, so a new version of a module tells a rule that a site changed from a rule that nobody
 * changed:</p>
 * <ul>
 *   <li>A rule that the site lacks is added. A deleted rule node comes back with its shipped values.</li>
 *   <li>A rule that nobody changed takes the new shipped values.</li>
 *   <li>A rule that the site changed keeps its values, and only gets the properties it lacks. When its module
 *       moves it to the browser, it also takes the shipped evaluator, {@link #browserEvaluator} states why.</li>
 *   <li>A rule that the module no longer ships is removed if nobody changed it, and marked
 *       {@value RuleNodes#RETIRED} if the site changed it.</li>
 * </ul>
 * <p>The nodes stay unpublished: {@link ContentRules} reads the edit workspace.</p>
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class RuleSeeder extends DefaultEventListener implements BundleTrackerCustomizer<String> {

    private static final String INSTALLED_MODULES = "j:installedModules";
    private static final String EDIT_WORKSPACE = "default";
    private static final String FOLDER_TYPE = "jnt:contentFolder";
    private static final Object LOCK = new Object();
    private static final Logger logger = LoggerFactory.getLogger(RuleSeeder.class);

    private BundleTracker<String> tracker;
    /** True on the processing server, and on a Jahia that is not a cluster: the node that writes at start. */
    private boolean processingServer;
    /** Reads whether this node is the processing server. A test replaces it, because SettingsBean needs Jahia. */
    BooleanSupplier processingServerCheck = () -> SettingsBean.getInstance().isProcessingServer();

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

    /**
     * Converts the rules and the glossary terms of an older version, then tracks the modules that ship
     * rules. The tracker seeds every site with the rules of each module, this module included, as it finds
     * them. The nodes of a cluster share one repository and start the module at the same time, so only the
     * processing server converts and seeds: a browsing node never reads a node that the processing server
     * writes in the same second.
     */
    @Activate
    public void activate(BundleContext context) {
        processingServer = processingServerCheck.getAsBoolean();
        if (processingServer) {
            LegacyRules.migrate();
            LegacyTerms.migrate();
        } else {
            logger.debug("A browsing node leaves the conversions and the seeding to the processing server");
        }
        tracker = new BundleTracker<>(context, Bundle.ACTIVE, this);
        tracker.open();
    }

    @Deactivate
    public void deactivate() {
        if (tracker != null) {
            tracker.close();
        }
    }

    @Override
    public String addingBundle(Bundle bundle, BundleEvent event) {
        ShippedRules.Shipment shipment = ShippedRules.load(bundle);
        if (shipment == null) {
            return null;
        }
        ShippedRules.put(shipment);
        logger.info("Read {} content rules from the module {}", shipment.rules().size(), shipment.module());
        if (processingServer) {
            seedSites(shipment.module());
        }
        return shipment.module();
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, String module) {
        // A started bundle keeps its rules.
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, String module) {
        // The rule nodes stay on the sites: a check that a module registered is gone with it.
        ShippedRules.remove(module);
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
        sites.forEach(site -> seed(site, null));
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

    /** Seeds every site that enables this module, with the rules of one module, or of all when null. */
    private static void seedSites(String module) {
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
            sites.forEach(site -> seed(site, module));
        } catch (RepositoryException e) {
            logger.warn("Could not list the sites to seed the content rules", e);
        }
    }

    /** Seeds one site with the rules of one module, or of all its modules when null. Never throws. */
    static void seed(String sitePath, String module) {
        synchronized (LOCK) {
            try {
                int changed = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE,
                        Locale.ENGLISH, (JCRCallback<Integer>) session -> seed(session, sitePath, module));
                if (changed > 0) {
                    logger.info("Added or updated {} content rules on {}", changed, sitePath);
                }
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("Could not seed the content rules of {}", sitePath, e);
            }
        }
    }

    private static int seed(JCRSessionWrapper session, String sitePath, String module) throws RepositoryException {
        if (!(session.nodeExists(sitePath) && session.getNode(sitePath) instanceof JCRSiteNode site)
                || !SiteFeatures.moduleEnabled(site) || !site.hasNode("contents")) {
            return 0;
        }
        Collection<String> installed = site.getInstalledModulesWithAllDependencies();
        int changed = 0;
        for (ShippedRules.Shipment shipment : ShippedRules.forModules(installed)) {
            if (module == null || module.equals(shipment.module())) {
                changed += seed(site, shipment);
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return changed;
    }

    /** Applies the shipment of one module to its folder on the site, and returns the count of nodes it changed. */
    private static int seed(JCRSiteNode site, ShippedRules.Shipment shipment) throws RepositoryException {
        JCRNodeWrapper folder = rulesFolder(site, shipment.module());
        if (!folder.isNodeType(ContentRules.FOLDER_TYPE)) {
            logger.warn("{} is not a {}, so it cannot take the content rules", folder.getPath(), ContentRules.FOLDER_TYPE);
            return 0;
        }
        Map<String, JCRNodeWrapper> inFolder = rulesIn(folder);
        Set<String> elsewhere = idsOutside(site, folder);
        int changed = 0;
        for (RuleFile rule : shipment.rules().values()) {
            JCRNodeWrapper node = inFolder.get(rule.id());
            if (node != null) {
                changed += update(node, rule);
            } else if (elsewhere.contains(rule.id()) || folder.hasNode(rule.id())) {
                logger.info("{} already has a rule {}, so the rule of {} is not added", site.getPath(), rule.id(), shipment.module());
            } else {
                add(folder, rule);
                changed++;
            }
        }
        if (shipment.complete()) {
            for (Map.Entry<String, JCRNodeWrapper> entry : inFolder.entrySet()) {
                if (!shipment.rules().containsKey(entry.getKey())) {
                    changed += retire(entry.getValue());
                }
            }
        }
        return changed;
    }

    /** The rule nodes of a folder, by rule id. */
    private static Map<String, JCRNodeWrapper> rulesIn(JCRNodeWrapper folder) throws RepositoryException {
        Map<String, JCRNodeWrapper> rules = new LinkedHashMap<>();
        for (JCRNodeWrapper node : folder.getNodes()) {
            String id = node.isNodeType(ContentRules.TYPE) ? RuleNodes.text(node, RuleValues.RULE_ID) : null;
            if (id != null) {
                rules.putIfAbsent(id, node);
            }
        }
        return rules;
    }

    /** The ids of the rules of the site outside a folder, which the seeder never adds a second time. */
    private static Set<String> idsOutside(JCRSiteNode site, JCRNodeWrapper folder) throws RepositoryException {
        Set<String> ids = new HashSet<>();
        for (JCRNodeWrapper node : ContentRules.nodes(site.getSession(), site.getPath())) {
            if (!node.getParent().getPath().equals(folder.getPath())) {
                ids.add(RuleNodes.text(node, RuleValues.RULE_ID));
            }
        }
        return ids;
    }

    private static void add(JCRNodeWrapper folder, RuleFile rule) throws RepositoryException {
        JCRNodeWrapper added = folder.addNode(rule.id(), ContentRules.TYPE);
        RuleNodes.write(added, rule.values(), true);
        added.setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(rule.values()));
    }

    /** What the seeder does to a rule node that its module still ships. */
    enum Update {
        /** Nobody changed the node, and the module ships the same values: the node gets only the values it lacks. */
        NONE,
        /** Nobody changed the node, and the module ships new values: the node takes them. */
        OVERWRITE,
        /** The site changed the node, or a version that kept no fingerprint seeded it: the node gets only the values it lacks. */
        FILL
    }

    /**
     * @param seeded  the fingerprint that the node keeps, or null
     * @param current the fingerprint of the values of the node
     * @param shipped the fingerprint of the values that the module ships
     */
    static Update updateOf(String seeded, String current, String shipped) {
        if (!current.equals(seeded)) {
            return Update.FILL;
        }
        return current.equals(shipped) ? Update.NONE : Update.OVERWRITE;
    }

    /** Brings a seeded node to the new shipped values, unless the site changed it. Returns 1 when it wrote. */
    private static int update(JCRNodeWrapper node, RuleFile rule) throws RepositoryException {
        String shipped = RuleValues.digest(rule.values());
        String seeded = RuleNodes.text(node, RuleNodes.SEED_DIGEST);
        boolean wrote = unretire(node);
        switch (updateOf(seeded, RuleValues.digest(RuleNodes.read(node)), shipped)) {
            case OVERWRITE -> {
                RuleNodes.write(node, rule.values(), true);
                node.setProperty(RuleNodes.SEED_DIGEST, shipped);
                wrote = true;
            }
            case FILL -> {
                String evaluator = browserEvaluator(RuleNodes.read(node), rule.values());
                if (evaluator != null) {
                    node.setProperty(RuleValues.EVALUATOR, evaluator);
                    wrote = true;
                }
                wrote |= RuleNodes.fillMissing(node, rule.values());
                // A node that holds the shipped values again is a node that nobody changed.
                if (!shipped.equals(seeded) && RuleValues.digest(RuleNodes.read(node)).equals(shipped)) {
                    node.setProperty(RuleNodes.SEED_DIGEST, shipped);
                    wrote = true;
                }
            }
            // The node holds the shipped values, or their defaults: it gets the ones it lacks.
            case NONE -> wrote |= RuleNodes.fillMissing(node, rule.values());
        }
        return wrote ? 1 : 0;
    }

    /**
     * The evaluator that a rule changed by the site takes when its module moves the rule to the browser: the
     * shipped one, since no check of the server applies the rule any more. Example: a site raised the maximum
     * of {@code ecodesign.pageWeight}, whose evaluator was {@code threshold}; the node keeps its maximum and
     * takes the evaluator {@code ecodesign.pageWeight} of the browser.
     *
     * @param node    the values of the node
     * @param shipped the values that the module ships
     * @return the shipped evaluator, or null when the node has a runtime already, the shipped rule runs on the
     * server, or the node names the shipped evaluator
     */
    static String browserEvaluator(Map<String, Object> node, Map<String, Object> shipped) {
        if (node.containsKey(RuleValues.RUNTIME) || !ContentRule.BROWSER.equals(shipped.get(RuleValues.RUNTIME))) {
            return null;
        }
        Object evaluator = shipped.get(RuleValues.EVALUATOR);
        return evaluator == null || evaluator.equals(node.get(RuleValues.EVALUATOR)) ? null : evaluator.toString();
    }

    private static boolean unretire(JCRNodeWrapper node) throws RepositoryException {
        if (node.hasProperty(RuleNodes.RETIRED)) {
            node.getProperty(RuleNodes.RETIRED).remove();
            return true;
        }
        return false;
    }

    /** What the seeder does to a rule node that its module no longer ships. */
    enum Retirement {
        /** The site wrote the node: the seeder never touches it. */
        KEEP,
        /** Nobody changed the node: it goes. */
        REMOVE,
        /** The site changed the node: it stays, marked as no longer shipped. */
        MARK
    }

    /**
     * @param seeded  the fingerprint that the node keeps, or null for a node that the seeder never wrote
     * @param current the fingerprint of the values of the node
     */
    static Retirement retirementOf(String seeded, String current) {
        if (seeded == null) {
            return Retirement.KEEP;
        }
        return seeded.equals(current) ? Retirement.REMOVE : Retirement.MARK;
    }

    private static int retire(JCRNodeWrapper node) throws RepositoryException {
        Retirement retirement = retirementOf(RuleNodes.text(node, RuleNodes.SEED_DIGEST),
                RuleValues.digest(RuleNodes.read(node)));
        if (retirement == Retirement.REMOVE) {
            node.remove();
            return 1;
        }
        if (retirement == Retirement.MARK && !node.hasProperty(RuleNodes.RETIRED)) {
            node.setProperty(RuleNodes.RETIRED, true);
            return 1;
        }
        return 0;
    }

    /** The folder of the rules of a module, created with its parents when it is missing. */
    private static JCRNodeWrapper rulesFolder(JCRNodeWrapper site, String module) throws RepositoryException {
        JCRNodeWrapper folder = site;
        String[] names = ContentRules.folderOf(module).split("/");
        for (int i = 0; i < names.length; i++) {
            String type = i == names.length - 1 ? ContentRules.FOLDER_TYPE : FOLDER_TYPE;
            folder = folder.hasNode(names[i]) ? folder.getNode(names[i]) : folder.addNode(names[i], type);
        }
        return folder;
    }
}

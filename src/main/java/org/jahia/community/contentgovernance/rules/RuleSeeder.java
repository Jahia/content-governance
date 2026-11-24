package org.jahia.community.contentgovernance.rules;

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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Tracks the modules that ship rules, and has {@link TreeSeeder} write their domains, subdomains and rules into
 * the tree of the system site, which every site reads.
 *
 * <p>Runs on the processing server when a module that ships rules starts, this module included: a browsing node
 * of a cluster shares the repository and seeds nothing. Each node of the tree keeps the fingerprint of the values
 * that the seeder wrote, so a new version of a module tells a node that an administrator changed from a node
 * that nobody changed, as {@link #updateOf} and {@link #retirementOf} state.</p>
 */
@Component(immediate = true)
public class RuleSeeder implements BundleTrackerCustomizer<String> {

    private static final Logger logger = LoggerFactory.getLogger(RuleSeeder.class);

    private BundleTracker<String> tracker;
    /**
     * The started modules whose rule folder has a file that was skipped, by module. Such a file may name a
     * domain or a subdomain of a module that starts later, so the folder is read again when one starts.
     */
    private final Map<String, Bundle> incomplete = new ConcurrentHashMap<>();
    /**
     * The modules whose subdomains or rules wait for a parent node that the tree lacks, such as a rule of a module
     * that the tracker finds before the module that declares its subdomain. The seeder seeds them again when a
     * module that declares domains or subdomains starts.
     */
    private final Set<String> waitingForTree = ConcurrentHashMap.newKeySet();
    /** True on the processing server, and on a Jahia that is not a cluster: the node that writes at start. */
    private boolean processingServer;
    /** Reads whether this node is the processing server. A test replaces it, because SettingsBean needs Jahia. */
    BooleanSupplier processingServerCheck = () -> SettingsBean.getInstance().isProcessingServer();

    /**
     * Converts the rules, the glossary terms, the check types, the subdomains and the layout of an older version,
     * then tracks the modules that ship rules. The tracker seeds the tree with the rules of each module, this
     * module included, as it finds them. The nodes of a cluster share one repository and start the module at the
     * same time, so only the processing server converts and seeds: a browsing node never reads a node that the
     * processing server writes in the same second.
     */
    @Activate
    public void activate(BundleContext context) {
        processingServer = processingServerCheck.getAsBoolean();
        if (processingServer) {
            LegacyRules.migrate();
            LegacyTerms.migrate();
            // Before the subdomains: their conversion reads the keys that the mixin of the check type declares.
            LegacyCheckTypes.migrate();
            LegacyDomains.migrate();
            // After the subdomains, before the seeder: it removes the copies that the seeder no longer writes.
            LegacyLayout.migrate();
            // The tree of this module first: the other modules place their subdomains and rules in it.
            TreeSeeder.seed(ShippedRules.own());
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
        ShippedRules.Shipment shipment = read(bundle);
        if (shipment == null) {
            return null;
        }
        if (!shipment.domains().isEmpty() || !shipment.subdomains().isEmpty()) {
            // A module that started first may name a domain or a subdomain that this one declares.
            Map.copyOf(incomplete).forEach((module, other) -> {
                if (!module.equals(shipment.module())) {
                    read(other);
                }
            });
            // A module whose files are complete may still wait for the node of a parent in the tree.
            for (String module : Set.copyOf(waitingForTree)) {
                ShippedRules.Shipment waiting = ShippedRules.of(module);
                if (!module.equals(shipment.module()) && waiting != null && processingServer) {
                    seedTree(waiting);
                }
            }
        }
        return shipment.module();
    }

    /** Reads the rules, the domains and the subdomains of a module, and seeds them on the processing server. */
    private ShippedRules.Shipment read(Bundle bundle) {
        ShippedRules.Shipment shipment = ShippedRules.load(bundle);
        if (shipment == null) {
            return null;
        }
        ShippedRules.put(shipment);
        if (shipment.complete()) {
            incomplete.remove(shipment.module());
        } else {
            incomplete.put(shipment.module(), bundle);
        }
        logger.info("Read {} content rules, {} domains and {} subdomains from the module {}", shipment.rules().size(),
                shipment.domains().size(), shipment.subdomains().size(), shipment.module());
        if (processingServer) {
            seedTree(shipment);
        }
        return shipment;
    }

    /** Seeds the tree with one module, and remembers whether a node of it waits for a parent. */
    private void seedTree(ShippedRules.Shipment shipment) {
        if (TreeSeeder.seed(shipment)) {
            waitingForTree.add(shipment.module());
        } else {
            waitingForTree.remove(shipment.module());
        }
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, String module) {
        // A started bundle keeps its rules.
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, String module) {
        // The nodes stay in the tree: a check that a module registered is gone with it.
        incomplete.remove(module);
        waitingForTree.remove(module);
        ShippedRules.remove(module);
    }

    /** What the seeder does to a node that its module still ships. */
    enum Update {
        /** Nobody changed the node, and the module ships the same values: the node gets only the values it lacks. */
        NONE,
        /** Nobody changed the node, and the module ships new values: the node takes them. */
        OVERWRITE,
        /** An administrator changed the node, or a version that kept no fingerprint seeded it: the node gets only the values it lacks. */
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

    /**
     * The evaluator that a rule changed by an administrator takes when its module moves the rule to the browser:
     * the shipped one, since no check of the server applies the rule any more. Example: an administrator raised
     * the maximum of {@code ecodesign.pageWeight}, whose evaluator was {@code threshold}; the node keeps its
     * maximum and takes the evaluator {@code ecodesign.pageWeight} of the browser.
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

    /** What the seeder does to a node that its module no longer ships. */
    enum Retirement {
        /** The seeder never wrote the node: it never touches it. */
        KEEP,
        /** Nobody changed the node: it goes. */
        REMOVE,
        /** An administrator changed the node: it stays, marked as no longer shipped. */
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
}

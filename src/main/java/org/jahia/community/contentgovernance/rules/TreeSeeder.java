package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes the domains, the subdomains and the rules that a module ships into the tree of the system site,
 * {@value ContentDomains#TREE}: a domain node holds its subdomain nodes, and a subdomain node holds its rule
 * nodes. Every site reads them there.
 *
 * <p>Runs on the processing server when a module that ships them starts. Each node keeps the fingerprint of
 * the values that the seeder wrote, its place included, so a new version of a module tells a node that an
 * administrator changed or moved from a node that nobody changed:</p>
 * <ul>
 *   <li>A node that the tree lacks is added, at the place that its file states. A deleted node comes back.</li>
 *   <li>A node that nobody changed takes the new shipped values, and moves when its module moves it.</li>
 *   <li>A node that an administrator changed or moved keeps its values and its place, and only gets the
 *       properties it lacks. A rule that its module moves to the browser also takes the shipped evaluator,
 *       {@link RuleSeeder#browserEvaluator} states why.</li>
 *   <li>A node that the module no longer ships is removed if nobody changed it and it holds no other node, and
 *       marked {@value RuleNodes#RETIRED} otherwise.</li>
 * </ul>
 * <p>A subdomain of a domain that the tree lacks, and a rule of a subdomain that the tree lacks, wait: the
 * seeder adds them when the module that declares the domain or the subdomain starts. The nodes stay
 * unpublished: {@link ContentDomains} and {@link ContentRules} read the edit workspace.</p>
 */
final class TreeSeeder {

    /** The module whose file the seeder wrote a node from. */
    static final String SEED_MODULE = "seedModule";
    /** The type of the folder of the tree, which orders its domains. */
    static final String TREE_TYPE = "ccs:governanceFolder";

    private static final String EDIT_WORKSPACE = "default";
    private static final Object LOCK = new Object();
    private static final Logger logger = LoggerFactory.getLogger(TreeSeeder.class);

    private TreeSeeder() {
    }

    /** What one seed did: the nodes it wrote, and whether a node waited for a parent that the tree lacks. */
    private record Result(int changed, boolean waiting) {
    }

    /**
     * Seeds the tree with the domains, the subdomains and the rules of one module. Never throws.
     *
     * @return true when a subdomain or a rule waits for a parent node that the tree lacks: the caller seeds the
     * module again when a module that declares domains or subdomains starts
     */
    static boolean seed(ShippedRules.Shipment shipment) {
        synchronized (LOCK) {
            try {
                Result result = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE,
                        Locale.ENGLISH, (JCRCallback<Result>) session -> discardOnFailure(session, () -> seed(session, shipment)));
                if (result.changed() > 0) {
                    logger.info("Added, updated or moved {} domains, subdomains and rules of {} in {}", result.changed(),
                            shipment.module(), ContentDomains.TREE);
                }
                return result.waiting();
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("Could not seed the tree with the rules of {}", shipment.module(), e);
                return true;
            }
        }
    }

    private static Result seed(JCRSessionWrapper session, ShippedRules.Shipment shipment) throws RepositoryException {
        String contents = ContentDomains.SYSTEM_SITE + "/contents";
        if (!session.nodeExists(contents)) {
            logger.warn("{} is missing, so it cannot take the rules of {}", contents, shipment.module());
            return new Result(0, true);
        }
        JCRNodeWrapper tree = tree(session.getNode(contents));
        Index index = Index.of(session, tree);
        String module = shipment.module();
        int changed = 0;
        boolean waiting = false;
        for (DomainFile file : shipment.domains().values()) {
            changed += place(index.domains, tree,
                    new Item(file.id(), Level.DOMAIN.type(), module, orderOf(Level.DOMAIN, file)),
                    node -> update(node, file), node -> add(node, file, module));
        }
        for (DomainFile file : shipment.subdomains().values()) {
            JCRNodeWrapper domain = index.domains.get(String.valueOf(file.values().get(DomainValues.DOMAIN)));
            if (domain != null) {
                changed += place(index.subdomains, domain,
                        new Item(file.id(), Level.SUBDOMAIN.type(), module, orderOf(Level.SUBDOMAIN, file)),
                        node -> update(node, file), node -> add(node, file, module));
            } else {
                waiting = true;
            }
        }
        // A move of a subdomain leaves the wrappers of its rule nodes on their old path: read the tree again
        index = Index.of(session, tree);
        for (RuleFile rule : shipment.rules().values()) {
            JCRNodeWrapper subdomain = index.subdomains.get(String.valueOf(rule.values().get(RuleValues.SUBDOMAIN)));
            if (subdomain == null) {
                waiting = true;
            } else if (!index.outside.contains(rule.id())) {
                changed += place(index.rules, subdomain, new Item(rule.id(), ContentRules.TYPE, module, null),
                        node -> update(node, rule), node -> add(node, rule, module));
            }
        }
        if (shipment.complete()) {
            changed += retire(index.rules.values(), module, shipment.rules().keySet(), null);
            changed += retire(index.subdomains.values(), module, shipment.subdomains().keySet(), Level.SUBDOMAIN);
            changed += retire(index.domains.values(), module, shipment.domains().keySet(), Level.DOMAIN);
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return new Result(changed, waiting);
    }

    /** What an update did to a node that the module still ships. */
    enum Outcome {
        /** The node already held the values. */
        UNCHANGED,
        /** The node got the values it lacked, and keeps its values and its place. */
        FILLED,
        /** Nobody changed the node: it took the new shipped values, and takes the place of the file. */
        OVERWRITTEN
    }

    /** Writes the values of a file on a node. */
    @FunctionalInterface
    private interface Write {
        Outcome apply(JCRNodeWrapper node) throws RepositoryException;
    }

    /** Fills a node that the seeder just added. */
    @FunctionalInterface
    private interface Fill {
        void apply(JCRNodeWrapper node) throws RepositoryException;
    }

    /**
     * A file of a module, as the tree places it.
     *
     * @param order the shipped order of a domain or a subdomain, which places a new node; null to append it
     */
    private record Item(String id, String type, String module, Integer order) {
    }

    /**
     * Adds the node of a file under its parent, or updates the node of its id; a node that nobody changed moves
     * under the parent that the file states.
     *
     * @return 1 when it wrote
     */
    private static int place(Map<String, JCRNodeWrapper> byId, JCRNodeWrapper parent, Item item, Write update,
                             Fill fill) throws RepositoryException {
        JCRNodeWrapper node = byId.get(item.id());
        if (node == null) {
            if (parent.hasNode(item.id())) {
                logger.info("{} already has a node {}, so the file of {} is not added", parent.getPath(), item.id(),
                        item.module());
                return 0;
            }
            JCRNodeWrapper added = parent.addNode(item.id(), item.type());
            fill.apply(added);
            if (item.order() != null) {
                placeByOrder(parent, added, item.order());
            }
            byId.put(item.id(), added);
            return 1;
        }
        Outcome outcome = update.apply(node);
        if (outcome == Outcome.OVERWRITTEN && !node.getParent().getPath().equals(parent.getPath())) {
            String target = parent.getPath() + "/" + node.getName();
            node.getSession().move(node.getPath(), target);
            byId.put(item.id(), node.getSession().getNode(target));
        }
        return outcome == Outcome.UNCHANGED ? 0 : 1;
    }

    /** Puts a new node before the first sibling that its module ships with a higher order. */
    private static void placeByOrder(JCRNodeWrapper parent, JCRNodeWrapper added, int order) throws RepositoryException {
        // The folder of the tree, a domain and a subdomain order their child nodes; a plain content folder does not.
        if (!parent.isNodeType(TREE_TYPE) && !parent.isNodeType(Level.DOMAIN.type())) {
            logger.warn("{} does not order its child nodes, so {} goes last", parent.getPath(), added.getName());
            return;
        }
        Level level = added.isNodeType(Level.DOMAIN.type()) ? Level.DOMAIN : Level.SUBDOMAIN;
        for (JCRNodeWrapper sibling : parent.getNodes()) {
            if (sibling.getName().equals(added.getName()) || !sibling.isNodeType(level.type())) {
                continue;
            }
            Integer siblingOrder = shippedOrder(level, sibling.getName());
            if (siblingOrder != null && siblingOrder > order) {
                parent.orderBefore(added.getName(), sibling.getName());
                return;
            }
        }
    }

    private static Integer orderOf(Level level, DomainFile file) {
        Integer order = shippedOrder(level, file.id());
        return order == null ? 0 : order;
    }

    /** The order that a started module ships for a domain or a subdomain, or null when none ships it. */
    private static Integer shippedOrder(Level level, String id) {
        for (ShippedRules.Shipment shipment : ShippedRules.all()) {
            DomainFile file = shipment.of(level).get(id);
            if (file != null) {
                return file.values().get(DomainValues.ORDER) instanceof Number number ? number.intValue() : 0;
            }
        }
        return null;
    }

    private static void add(JCRNodeWrapper node, DomainFile file, String module) throws RepositoryException {
        DomainNodes.write(node, file.level(), file.values(), true);
        node.setProperty(RuleNodes.SEED_DIGEST, DomainValues.digest(file.level(), file.values()));
        node.setProperty(SEED_MODULE, module);
    }

    private static void add(JCRNodeWrapper node, RuleFile rule, String module) throws RepositoryException {
        RuleNodes.write(node, rule.values(), true);
        node.setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(rule.values()));
        node.setProperty(SEED_MODULE, module);
    }

    /**
     * Brings a seeded domain or subdomain to the new shipped values, unless an administrator changed it. A node
     * that nobody changed takes the domain of the file too, which the caller applies.
     */
    static Outcome update(JCRNodeWrapper node, DomainFile file) throws RepositoryException {
        Level level = file.level();
        String shipped = DomainValues.digest(level, file.values());
        String seeded = DomainNodes.text(node, RuleNodes.SEED_DIGEST);
        boolean wrote = unretire(node);
        switch (RuleSeeder.updateOf(seeded, DomainValues.digest(level, DomainNodes.read(node, level)), shipped)) {
            case OVERWRITE -> {
                DomainNodes.write(node, level, file.values(), true);
                node.setProperty(RuleNodes.SEED_DIGEST, shipped);
                return Outcome.OVERWRITTEN;
            }
            case FILL -> {
                wrote |= DomainNodes.fillMissing(node, level, file.values());
                if (!shipped.equals(seeded) && DomainValues.digest(level, DomainNodes.read(node, level)).equals(shipped)) {
                    node.setProperty(RuleNodes.SEED_DIGEST, shipped);
                    wrote = true;
                }
            }
            case NONE -> wrote |= DomainNodes.fillMissing(node, level, file.values());
        }
        return wrote ? Outcome.FILLED : Outcome.UNCHANGED;
    }

    /**
     * Brings a seeded rule to the new shipped values, unless an administrator changed it. A node that nobody
     * changed takes the subdomain of the file too, which the caller applies.
     */
    static Outcome update(JCRNodeWrapper node, RuleFile rule) throws RepositoryException {
        String shipped = RuleValues.digest(rule.values());
        String seeded = RuleNodes.text(node, RuleNodes.SEED_DIGEST);
        boolean wrote = unretire(node);
        switch (RuleSeeder.updateOf(seeded, RuleValues.digest(RuleNodes.read(node)), shipped)) {
            case OVERWRITE -> {
                RuleNodes.write(node, rule.values(), true);
                node.setProperty(RuleNodes.SEED_DIGEST, shipped);
                return Outcome.OVERWRITTEN;
            }
            case FILL -> {
                String evaluator = RuleSeeder.browserEvaluator(RuleNodes.read(node), rule.values());
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
            case NONE -> wrote |= RuleNodes.fillMissing(node, rule.values());
        }
        return wrote ? Outcome.FILLED : Outcome.UNCHANGED;
    }

    private static boolean unretire(JCRNodeWrapper node) throws RepositoryException {
        if (node.hasProperty(RuleNodes.RETIRED)) {
            node.getProperty(RuleNodes.RETIRED).remove();
            return true;
        }
        return false;
    }

    /**
     * Removes or marks the nodes that a module seeded and no longer ships.
     *
     * @param level the level of domain or subdomain nodes, or null for rule nodes
     */
    private static int retire(Collection<JCRNodeWrapper> nodes, String module, Set<String> shipped, Level level)
            throws RepositoryException {
        int changed = 0;
        for (JCRNodeWrapper node : new ArrayList<>(nodes)) {
            if (!node.hasProperty(SEED_MODULE) || !module.equals(node.getProperty(SEED_MODULE).getString())
                    || shipped.contains(node.getName())) {
                continue;
            }
            String current = level == null ? RuleValues.digest(RuleNodes.read(node))
                    : DomainValues.digest(level, DomainNodes.read(node, level));
            RuleSeeder.Retirement retirement = RuleSeeder.retirementOf(RuleNodes.text(node, RuleNodes.SEED_DIGEST), current);
            if (retirement == RuleSeeder.Retirement.REMOVE && !node.hasNodes()) {
                node.remove();
                changed++;
            } else if (retirement != RuleSeeder.Retirement.KEEP && !node.hasProperty(RuleNodes.RETIRED)) {
                node.setProperty(RuleNodes.RETIRED, true);
                changed++;
            }
        }
        return changed;
    }

    /** A write that throws {@link RepositoryException} or a runtime exception. */
    @FunctionalInterface
    interface Writes<T> {
        T run() throws RepositoryException;
    }

    /**
     * Runs a write, and drops what it left pending when it fails: the system session of a thread can serve the next
     * callback, whose save would write half of the failed one.
     */
    static <T> T discardOnFailure(JCRSessionWrapper session, Writes<T> writes) throws RepositoryException {
        try {
            return writes.run();
        } catch (RepositoryException | RuntimeException e) {
            session.refresh(false);
            throw e;
        }
    }

    /**
     * The folder of the tree, created when it is missing. A plain content folder of that name, which does not order
     * its child nodes, takes the type of the tree.
     */
    private static JCRNodeWrapper tree(JCRNodeWrapper contents) throws RepositoryException {
        String name = SiteLayout.FOLDER.substring(SiteLayout.FOLDER.indexOf('/') + 1);
        if (!contents.hasNode(name)) {
            return contents.addNode(name, TREE_TYPE);
        }
        JCRNodeWrapper tree = contents.getNode(name);
        if (!tree.isNodeType(TREE_TYPE)) {
            logger.info("{} takes the type {}, which orders the domains", tree.getPath(), TREE_TYPE);
            // The wrapper of Jahia refuses a change of the primary type, and the node of the repository makes it.
            tree.getRealNode().setPrimaryType(TREE_TYPE);
            tree = contents.getNode(name);
        }
        return tree;
    }

    /**
     * The nodes of the tree by id, and the ids of the rule nodes of the system site outside the tree, which
     * the seeder never adds a second time.
     */
    private record Index(Map<String, JCRNodeWrapper> domains, Map<String, JCRNodeWrapper> subdomains,
                         Map<String, JCRNodeWrapper> rules, Set<String> outside) {

        static Index of(JCRSessionWrapper session, JCRNodeWrapper tree) throws RepositoryException {
            Index index = new Index(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashSet<>());
            for (JCRNodeWrapper domain : tree.getNodes()) {
                if (domain.isNodeType(Level.DOMAIN.type())) {
                    index.domains.putIfAbsent(DomainNodes.text(domain, Level.DOMAIN.idProperty()), domain);
                    for (JCRNodeWrapper subdomain : domain.getNodes()) {
                        index.subdomain(subdomain);
                    }
                }
            }
            for (JCRNodeWrapper rule : ContentRules.nodes(session, ContentDomains.SYSTEM_SITE)) {
                if (RuleNodes.parentSubdomain(rule) == null) {
                    index.outside.add(RuleNodes.text(rule, RuleValues.RULE_ID));
                }
            }
            return index;
        }

        private void subdomain(JCRNodeWrapper subdomain) throws RepositoryException {
            if (!subdomain.isNodeType(Level.SUBDOMAIN.type())) {
                return;
            }
            subdomains.putIfAbsent(DomainNodes.text(subdomain, Level.SUBDOMAIN.idProperty()), subdomain);
            for (JCRNodeWrapper rule : subdomain.getNodes()) {
                if (rule.isNodeType(ContentRules.TYPE)) {
                    rules.putIfAbsent(RuleNodes.text(rule, RuleValues.RULE_ID), rule);
                }
            }
        }
    }
}

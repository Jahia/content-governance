package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Copies the domains and the subdomains that a module ships into the system site: one
 * {@value ContentDomains#DOMAIN_TYPE} or {@value ContentDomains#SUBDOMAIN_TYPE} node each, in the folders
 * {@link ContentDomains#folderOf} of the module. Every site reads them there.
 *
 * <p>Runs on the processing server when a module that ships them starts. It keeps what an administrator
 * changed, as {@link RuleSeeder} does for the rules: it adds a missing node, gives a node that nobody
 * changed the new shipped values, only fills a node that an administrator changed, and removes or marks
 * {@value RuleNodes#RETIRED} a node that the module no longer ships. The nodes stay unpublished:
 * {@link ContentDomains} reads the edit workspace.</p>
 */
final class DomainSeeder {

    private static final String EDIT_WORKSPACE = "default";
    private static final String FOLDER_TYPE = "jnt:contentFolder";
    private static final Logger logger = LoggerFactory.getLogger(DomainSeeder.class);

    private DomainSeeder() {
    }

    /** Seeds the system site with the domains and the subdomains of one module. Never throws. */
    static void seed(ShippedRules.Shipment shipment) {
        try {
            int changed = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE,
                    Locale.ENGLISH, (JCRCallback<Integer>) session -> seed(session, shipment));
            if (changed > 0) {
                logger.info("Added or updated {} domains and subdomains of {} in the system site", changed, shipment.module());
            }
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("Could not seed the domains of {}", shipment.module(), e);
        }
    }

    private static int seed(JCRSessionWrapper session, ShippedRules.Shipment shipment) throws RepositoryException {
        String contents = ContentDomains.SYSTEM_SITE + "/contents";
        if (!session.nodeExists(contents)) {
            logger.warn("{} is missing, so it cannot take the domains of {}", contents, shipment.module());
            return 0;
        }
        JCRNodeWrapper site = session.getNode(ContentDomains.SYSTEM_SITE);
        int changed = 0;
        for (Level level : Level.values()) {
            // A module that ships none and never did gets no folder.
            if (!shipment.of(level).isEmpty() || site.hasNode(ContentDomains.folderOf(shipment.module(), level))) {
                changed += seed(folder(site, shipment.module(), level), level, shipment);
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return changed;
    }

    private static int seed(JCRNodeWrapper folder, Level level, ShippedRules.Shipment shipment) throws RepositoryException {
        if (!folder.isNodeType(ContentDomains.FOLDER_TYPE)) {
            logger.warn("{} is not a {}, so it cannot take the {} of {}", folder.getPath(), ContentDomains.FOLDER_TYPE,
                    level.folder(), shipment.module());
            return 0;
        }
        Map<String, JCRNodeWrapper> inFolder = nodesIn(folder, level);
        int changed = 0;
        for (DomainFile file : shipment.of(level).values()) {
            JCRNodeWrapper node = inFolder.get(file.id());
            changed += node != null ? update(node, file) : add(folder, file, shipment.module());
        }
        if (shipment.complete()) {
            for (Map.Entry<String, JCRNodeWrapper> entry : inFolder.entrySet()) {
                if (!shipment.of(level).containsKey(entry.getKey())) {
                    changed += retire(entry.getValue(), level);
                }
            }
        }
        return changed;
    }

    /** The domain or subdomain nodes of a folder, by id. */
    private static Map<String, JCRNodeWrapper> nodesIn(JCRNodeWrapper folder, Level level) throws RepositoryException {
        Map<String, JCRNodeWrapper> nodes = new LinkedHashMap<>();
        for (JCRNodeWrapper node : folder.getNodes()) {
            String id = node.isNodeType(level.type()) ? DomainNodes.text(node, level.idProperty()) : null;
            if (id != null) {
                nodes.putIfAbsent(id, node);
            }
        }
        return nodes;
    }

    /** Adds the node of a shipped file, unless the folder has a node of its name. Returns 1 when it wrote. */
    private static int add(JCRNodeWrapper folder, DomainFile file, String module) throws RepositoryException {
        Level level = file.level();
        if (folder.hasNode(file.id())) {
            if (logger.isInfoEnabled()) {
                logger.info("{} already has a node {}, so the file of {} in {} is not added", folder.getPath(), file.id(),
                        module, level.folder());
            }
            return 0;
        }
        JCRNodeWrapper added = folder.addNode(file.id(), level.type());
        DomainNodes.write(added, level, file.values(), true);
        added.setProperty(RuleNodes.SEED_DIGEST, DomainValues.digest(level, file.values()));
        return 1;
    }

    /** Brings a seeded node to the new shipped values, unless an administrator changed it. Returns 1 when it wrote. */
    private static int update(JCRNodeWrapper node, DomainFile file) throws RepositoryException {
        Level level = file.level();
        String shipped = DomainValues.digest(level, file.values());
        String seeded = DomainNodes.text(node, RuleNodes.SEED_DIGEST);
        boolean wrote = false;
        if (node.hasProperty(RuleNodes.RETIRED)) {
            node.getProperty(RuleNodes.RETIRED).remove();
            wrote = true;
        }
        switch (RuleSeeder.updateOf(seeded, DomainValues.digest(level, DomainNodes.read(node, level)), shipped)) {
            case OVERWRITE -> {
                DomainNodes.write(node, level, file.values(), true);
                node.setProperty(RuleNodes.SEED_DIGEST, shipped);
                wrote = true;
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
        return wrote ? 1 : 0;
    }

    private static int retire(JCRNodeWrapper node, Level level) throws RepositoryException {
        RuleSeeder.Retirement retirement = RuleSeeder.retirementOf(DomainNodes.text(node, RuleNodes.SEED_DIGEST),
                DomainValues.digest(level, DomainNodes.read(node, level)));
        if (retirement == RuleSeeder.Retirement.REMOVE) {
            node.remove();
            return 1;
        }
        if (retirement == RuleSeeder.Retirement.MARK && !node.hasProperty(RuleNodes.RETIRED)) {
            node.setProperty(RuleNodes.RETIRED, true);
            return 1;
        }
        return 0;
    }

    /** The folder of the domains or the subdomains of a module, created with its parents when it is missing. */
    private static JCRNodeWrapper folder(JCRNodeWrapper site, String module, Level level) throws RepositoryException {
        JCRNodeWrapper folder = site;
        String[] names = ContentDomains.folderOf(module, level).split("/");
        for (int i = 0; i < names.length; i++) {
            String type = i == names.length - 1 ? ContentDomains.FOLDER_TYPE : FOLDER_TYPE;
            folder = folder.hasNode(names[i]) ? folder.getNode(names[i]) : folder.addNode(names[i], type);
        }
        return folder;
    }
}

package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Removes, when the module starts on the processing server, what the seeder of a 2.0.0 snapshot wrote and the
 * seeder no longer writes, before the seeder writes the tree of the system site:
 *
 * <ul>
 *   <li>The copies of the shipped rules in each site, under {@code contents/content-insights/<module>/rules}. A
 *       copy that the site changed goes too, and the log names its values: a rule changes in the system site
 *       only. A rule that the site wrote in such a folder, which has no fingerprint, stays.</li>
 *   <li>The folders of the domains and the subdomains of each module, under
 *       {@code systemsite/contents/content-insights/<module>}. The seeder writes the tree again, and the log names
 *       the values of each node, so that an administrator restores a change by hand.</li>
 *   <li>A domain or a subdomain node outside the tree, which a site wrote to override one: a site arranges the
 *       tree with its layout now. The log names it. A node that holds other nodes, such as rules, stays.</li>
 * </ul>
 * <p>A folder that holds nothing more goes too. The upgrade works in the edit workspace, where the rules are
 * read. A copy that a site published stays in the live workspace, where no code reads a rule.</p>
 */
final class LegacyLayout {

    /** The folder of each site that held the copies of the rules, the domains and the subdomains. */
    static final String ROOT = "contents/content-insights";
    /** The folder of the rules that a site wrote, under {@value #ROOT}, which stays. */
    static final String SITE_FOLDER = "site";
    private static final String CONTENT_FOLDER = "jnt:contentFolder";
    private static final Logger logger = LoggerFactory.getLogger(LegacyLayout.class);

    private LegacyLayout() {
    }

    /** Removes the old copies. A failure is logged, and the module starts anyway. */
    static void migrate() {
        try {
            int removed = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "default", Locale.ENGLISH,
                    (JCRCallback<Integer>) session -> TreeSeeder.discardOnFailure(session, () -> removeLeftovers(session)));
            if (removed > 0) {
                logger.info("Removed {} copies of content rules, domains and subdomains that the tree of {} replaces",
                        removed, ContentDomains.SYSTEM_SITE);
            }
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The copies of the content rules could not all be removed", e);
        }
    }

    static int removeLeftovers(JCRSessionWrapper session) throws RepositoryException {
        int removed = removeOutsideTree(session, Level.SUBDOMAIN) + removeOutsideTree(session, Level.DOMAIN);
        for (JCRNodeWrapper site : session.getNode("/sites").getNodes()) {
            if (site.hasNode(ROOT)) {
                JCRNodeWrapper root = site.getNode(ROOT);
                for (JCRNodeWrapper module : new ArrayList<>(children(root))) {
                    removed += SITE_FOLDER.equals(module.getName()) ? 0 : removeCopies(site.getName(), module);
                    removeIfEmpty(module);
                }
                removeIfEmpty(root);
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return removed;
    }

    /** Removes the seeded copies of the folder of one module, and its folders of domains and subdomains. */
    private static int removeCopies(String site, JCRNodeWrapper module) throws RepositoryException {
        int removed = 0;
        for (JCRNodeWrapper folder : new ArrayList<>(children(module))) {
            if (!folder.isNodeType(CONTENT_FOLDER)) {
                continue;
            }
            for (JCRNodeWrapper node : new ArrayList<>(children(folder))) {
                if (node.isNodeType(ContentRules.TYPE) && node.hasProperty(RuleNodes.SEED_DIGEST)) {
                    Map<String, Object> values = RuleNodes.read(node);
                    if (!RuleValues.digest(values).equals(node.getProperty(RuleNodes.SEED_DIGEST).getString())) {
                        logger.warn("The site {} changed its copy of the rule {}, which goes: a rule changes in {} only. "
                                + "Its values were {}", site, values.get(RuleValues.RULE_ID), ContentDomains.TREE, values);
                    }
                    node.remove();
                    removed++;
                }
            }
            removeIfEmpty(folder);
        }
        return removed;
    }

    /** Removes every domain or subdomain node outside the tree of the system site, but one that holds other nodes. */
    private static int removeOutsideTree(JCRSessionWrapper session, Level level) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + level.type() + "] WHERE ISDESCENDANTNODE('/sites')", Query.JCR_SQL2);
        List<JCRNodeWrapper> found = new ArrayList<>();
        NodeIterator nodes = query.execute().getNodes();
        while (nodes.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
            if (!node.getPath().startsWith(ContentDomains.TREE + "/")) {
                found.add(node);
            }
        }
        int removed = 0;
        String what = level.name().toLowerCase(Locale.ROOT);
        for (JCRNodeWrapper node : found) {
            if (holdsNodes(node)) {
                // Not a copy of a 2.0.0 snapshot, whose domains and subdomains held no node: it stays, and reads ignore it.
                logger.warn("The {} {} at {} stays, because it holds other nodes, but no site reads it: the tree of {} "
                        + "holds the domains and the subdomains", what, node.getName(), node.getPath(),
                        ContentDomains.SYSTEM_SITE);
                continue;
            }
            // The fingerprint of a version before 2.0.0 holds the domain and the order, which the node type no longer
            // declares, so the conversion cannot tell a node that an administrator changed: the log names each one.
            Map<String, Object> values = DomainNodes.read(node, level);
            if (node.hasProperty(RuleNodes.SEED_DIGEST)) {
                logger.info("The seeded {} {} at {} goes, and the tree of {} replaces it. Its values were {}", what,
                        node.getName(), node.getPath(), ContentDomains.SYSTEM_SITE, values);
            } else {
                logger.warn("The {} {} at {} goes: a site arranges the tree of {} with its layout now. Its values were {}",
                        what, node.getName(), node.getPath(), ContentDomains.SYSTEM_SITE, values);
            }
            JCRNodeWrapper parent = node.getParent();
            node.remove();
            removed++;
            if (parent.isNodeType(ContentDomains.FOLDER_TYPE)) {
                removeIfEmpty(parent);
            }
        }
        return removed;
    }

    /** True when the node holds a node other than a translation. */
    private static boolean holdsNodes(JCRNodeWrapper node) throws RepositoryException {
        for (JCRNodeWrapper child : node.getNodes()) {
            if (!child.getName().startsWith("j:translation_")) {
                return true;
            }
        }
        return false;
    }

    private static List<JCRNodeWrapper> children(JCRNodeWrapper node) throws RepositoryException {
        List<JCRNodeWrapper> out = new ArrayList<>();
        for (JCRNodeWrapper child : node.getNodes()) {
            out.add(child);
        }
        return out;
    }

    /** Removes a content folder that holds no node, other than a translation. Another node stays. */
    private static void removeIfEmpty(JCRNodeWrapper folder) throws RepositoryException {
        if (!folder.isNodeType(CONTENT_FOLDER)) {
            return;
        }
        for (JCRNodeWrapper child : folder.getNodes()) {
            if (!child.getName().startsWith("j:translation_")) {
                return;
            }
        }
        folder.remove();
    }
}

package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a layout into the tree of the system site, {@value ContentDomains#TREE}: the defaults of every site. A
 * server administrator saves it from the screen of the defaults.
 *
 * <ul>
 *   <li>The order of each array becomes the order of the child nodes. A node that the layout does not name keeps
 *       its place among the others, after the named ones.</li>
 *   <li>A subdomain moves under the domain that holds it in the layout, and a rule under its subdomain.</li>
 *   <li>Each named node takes its default switch, the property {@value RuleValues#ENABLED}: off when {@code off}
 *       names it, on otherwise.</li>
 *   <li>A rule that no node of the tree holds, such as a rule node elsewhere in the system site, keeps its place and
 *       its switch.</li>
 * </ul>
 *
 * <p>The seeder then keeps a moved or switched node as an administrator change, as it keeps a change made in
 * jContent. Example: the layout places the subdomain {@code seo} first in the domain {@code quality} and names it in
 * {@code off}. The node {@code seo} moves under {@code quality}, comes first, and every site that does not place it
 * has it off.</p>
 */
public final class TreeLayout {

    private static final Logger logger = LoggerFactory.getLogger(TreeLayout.class);

    private TreeLayout() {
    }

    /**
     * Writes the layout into the tree with the session of the caller. The caller saves the session.
     *
     * @param session a session of the edit workspace that may write the tree
     * @param layout  the layout, whose ids of panels and of unknown items are ignored
     * @return the nodes that moved, changed their place or changed their switch
     * @throws RepositoryException on a repository failure, or when the caller may not write
     */
    public static int write(JCRSessionWrapper session, SiteLayout layout) throws RepositoryException {
        if (!session.nodeExists(ContentDomains.TREE)) {
            return 0;
        }
        JCRNodeWrapper tree = session.getNode(ContentDomains.TREE);
        int changed = moveSubdomains(tree, layout) + moveRules(tree, layout);
        changed += orderAll(tree, Index.of(tree), layout);
        return changed + switches(Index.of(tree), layout);
    }

    /** Orders the domains of the tree, the subdomains of each domain and the rules of each subdomain. */
    private static int orderAll(JCRNodeWrapper tree, Index index, SiteLayout layout) throws RepositoryException {
        int changed = 0;
        List<String> domainNames = new ArrayList<>();
        for (SiteLayout.DomainPlace domain : layout.domains()) {
            JCRNodeWrapper domainNode = index.domains.get(domain.id());
            if (domainNode != null) {
                domainNames.add(domainNode.getName());
                changed += orderSubdomains(domainNode, index, domain);
            }
        }
        return changed + order(tree, Level.DOMAIN.type(), domainNames);
    }

    /** Orders the subdomains of one domain, and the rules of each of them. */
    private static int orderSubdomains(JCRNodeWrapper domainNode, Index index, SiteLayout.DomainPlace domain)
            throws RepositoryException {
        int changed = 0;
        List<String> subdomainNames = new ArrayList<>();
        for (SiteLayout.SubdomainPlace subdomain : domain.subdomains()) {
            JCRNodeWrapper subdomainNode = index.subdomains.get(subdomain.id());
            if (subdomainNode != null) {
                subdomainNames.add(subdomainNode.getName());
                changed += order(subdomainNode, ContentRules.TYPE, names(index.rules, subdomain.rules()));
            }
        }
        return changed + order(domainNode, Level.SUBDOMAIN.type(), subdomainNames);
    }

    /** The names of the nodes of these ids, in their order, leaving out an id that no node holds. */
    private static List<String> names(Map<String, JCRNodeWrapper> nodes, List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            JCRNodeWrapper node = nodes.get(id);
            if (node != null) {
                out.add(node.getName());
            }
        }
        return out;
    }

    /** Moves each subdomain that the layout places in another domain. */
    private static int moveSubdomains(JCRNodeWrapper tree, SiteLayout layout) throws RepositoryException {
        Index index = Index.of(tree);
        int moved = 0;
        for (SiteLayout.DomainPlace domain : layout.domains()) {
            for (SiteLayout.SubdomainPlace subdomain : domain.subdomains()) {
                moved += move(index.subdomains.get(subdomain.id()), index.domains.get(domain.id()));
            }
        }
        return moved;
    }

    /** Moves each rule node of the tree that the layout places in another subdomain. */
    private static int moveRules(JCRNodeWrapper tree, SiteLayout layout) throws RepositoryException {
        Index index = Index.of(tree);
        int moved = 0;
        for (SiteLayout.DomainPlace domain : layout.domains()) {
            for (SiteLayout.SubdomainPlace subdomain : domain.subdomains()) {
                for (String rule : subdomain.rules()) {
                    moved += move(index.rules.get(rule), index.subdomains.get(subdomain.id()));
                }
            }
        }
        return moved;
    }

    /** Moves a node under a parent, unless it is there, one of them is missing, or the parent has a node of its name. */
    private static int move(JCRNodeWrapper node, JCRNodeWrapper parent) throws RepositoryException {
        if (node == null || parent == null || node.getParent().getPath().equals(parent.getPath())) {
            return 0;
        }
        if (parent.hasNode(node.getName())) {
            logger.warn("{} already has a node {}, so {} stays where it is", parent.getPath(), node.getName(), node.getPath());
            return 0;
        }
        node.getSession().move(node.getPath(), parent.getPath() + "/" + node.getName());
        return 1;
    }

    /**
     * Orders the child nodes of a type: the named ones first, in their order, then the others in their order.
     *
     * @return 1 when the order changed
     */
    static int order(JCRNodeWrapper parent, String type, List<String> names) throws RepositoryException {
        List<String> current = new ArrayList<>();
        for (JCRNodeWrapper child : parent.getNodes()) {
            if (child.isNodeType(type)) {
                current.add(child.getName());
            }
        }
        List<String> wanted = ordered(current, names);
        if (wanted.equals(current)) {
            return 0;
        }
        for (String name : wanted) {
            parent.orderBefore(name, null);
        }
        return 1;
    }

    /**
     * @param current the names of the child nodes, in their order
     * @param names   the names that the layout orders
     * @return the named ones that are child nodes, in the order of the layout, then the others in their order
     */
    static List<String> ordered(List<String> current, List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (current.contains(name) && !out.contains(name)) {
                out.add(name);
            }
        }
        for (String name : current) {
            if (!out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /** Sets the default switch of each node that the layout names. */
    private static int switches(Index index, SiteLayout layout) throws RepositoryException {
        int changed = 0;
        for (Map<String, JCRNodeWrapper> nodes : List.of(index.domains, index.subdomains, index.rules)) {
            for (Map.Entry<String, JCRNodeWrapper> entry : nodes.entrySet()) {
                String id = entry.getKey();
                if (layout.places(id) || layout.isOff(id)) {
                    changed += setEnabled(entry.getValue(), !layout.isOff(id));
                }
            }
        }
        return changed;
    }

    private static int setEnabled(JCRNodeWrapper node, boolean enabled) throws RepositoryException {
        boolean current = !node.hasProperty(RuleValues.ENABLED) || node.getProperty(RuleValues.ENABLED).getBoolean();
        if (current == enabled) {
            return 0;
        }
        node.setProperty(RuleValues.ENABLED, enabled);
        return 1;
    }

    /** The domain, subdomain and rule nodes of the tree by id; the first node of an id wins. */
    private record Index(Map<String, JCRNodeWrapper> domains, Map<String, JCRNodeWrapper> subdomains,
                         Map<String, JCRNodeWrapper> rules) {

        static Index of(JCRNodeWrapper tree) throws RepositoryException {
            Index index = new Index(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
            for (JCRNodeWrapper domain : tree.getNodes()) {
                if (domain.isNodeType(Level.DOMAIN.type())) {
                    index.domains.putIfAbsent(DomainNodes.text(domain, Level.DOMAIN.idProperty()), domain);
                    for (JCRNodeWrapper subdomain : domain.getNodes()) {
                        index.subdomain(subdomain);
                    }
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

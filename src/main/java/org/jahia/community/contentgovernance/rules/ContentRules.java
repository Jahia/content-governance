package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The rules as one site applies them, from the most general to the most specific: the rules that the
 * modules of the site ship, then every {@value #TYPE} node of {@code systemsite}, which applies to every
 * site, then every {@value #TYPE} node of the site. Example: a rule {@code acme.noClickHere} in
 * {@code /sites/systemsite/contents} applies to {@code digitall}, and a node {@code seo.title} of
 * {@code digitall} replaces the node {@code seo.title} of {@code systemsite} there.
 *
 * <p>The rule nodes are read in the edit workspace with a system session, so that a change applies
 * without a publication and a scheduled scan reads the same rules as the drawer. A node replaces the
 * rule of its id that comes before it; a property that the node lacks keeps the shipped value. A node
 * whose id no module ships is a rule of its own, such as a rule of the customer. A node of
 * {@code systemsite} whose rule a module ships applies only to a site that enables that module.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class ContentRules {

    public static final String TYPE = "ccs:contentRule";
    /** The type of the folder that takes the rules of one module. */
    public static final String FOLDER_TYPE = "ccs:contentRuleFolder";
    /** The folder, under the site node, of the folders of the rules: one per module, and one for the site. */
    public static final String ROOT = "contents/content-insights";

    private static final String EDIT_WORKSPACE = "default";
    /** The site whose rule nodes apply to every site. */
    private static final String SYSTEM_SITE = "systemsite";
    private static final String SITES = "/sites/";
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Logger logger = LoggerFactory.getLogger(ContentRules.class);

    private final Map<String, ContentRule> rules;

    private ContentRules(Map<String, ContentRule> rules) {
        this.rules = rules;
    }

    /** The folder, under the site node, of the rules that a module ships. */
    public static String folderOf(String module) {
        return ROOT + "/" + module + "/rules";
    }

    /** The rules that the started modules ship, as a site with no rule node applies them. */
    public static ContentRules base() {
        return new ContentRules(shipped(ShippedRules.all()));
    }

    private static Map<String, ContentRule> shipped(List<ShippedRules.Shipment> shipments) {
        Map<String, ContentRule> out = new LinkedHashMap<>();
        for (ShippedRules.Shipment shipment : shipments) {
            shipment.rules().forEach((id, rule) -> out.putIfAbsent(id, RuleValues.toRule(rule.values())));
        }
        return out;
    }

    /**
     * The rules of a site. A failure to read the site gives the shipped rules, and is logged.
     *
     * @param siteKey the key of the site
     * @return the rules of the site
     */
    public static ContentRules forSite(String siteKey) {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            return base();
        }
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, Locale.ENGLISH,
                    (JCRCallback<ContentRules>) session -> read(session, siteKey));
        } catch (RepositoryException | RuntimeException e) {
            // A check never fails for its rules: with no repository, it applies the shipped rules.
            logger.warn("The rules of the site {} could not be read, the shipped rules apply", siteKey, e);
            return base();
        }
    }

    /**
     * The rules of the site that owns {@code path}, such as {@code /sites/digitall/home}.
     *
     * @param path a path under {@code /sites/<key>}
     * @return the rules of that site, or the shipped rules when the path is not under a site
     */
    public static ContentRules forPath(String path) {
        if (path == null || !path.startsWith(SITES)) {
            return base();
        }
        int end = path.indexOf('/', SITES.length());
        return forSite(end < 0 ? path.substring(SITES.length()) : path.substring(SITES.length(), end));
    }

    private static ContentRules read(JCRSessionWrapper session, String siteKey) throws RepositoryException {
        String sitePath = SITES + siteKey;
        if (!session.nodeExists(sitePath)) {
            return base();
        }
        Collection<String> modules = session.getNode(sitePath) instanceof JCRSiteNode site
                ? site.getInstalledModulesWithAllDependencies() : List.of();
        List<RuleNode> platform = SYSTEM_SITE.equals(siteKey) ? List.of() : ruleNodes(session, SITES + SYSTEM_SITE);
        return merge(ShippedRules.forModules(modules), shippedIds(ShippedRules.all()), platform,
                ruleNodes(session, sitePath));
    }

    /**
     * A rule node as it was read.
     *
     * @param values the values of the node
     * @param folder the path of the folder that holds the node
     */
    record RuleNode(Map<String, Object> values, String folder) {
    }

    private static List<RuleNode> ruleNodes(JCRSessionWrapper session, String path) throws RepositoryException {
        List<RuleNode> out = new ArrayList<>();
        if (session.nodeExists(path)) {
            for (JCRNodeWrapper node : nodes(session, path)) {
                out.add(new RuleNode(RuleNodes.read(node), node.getParent().getPath()));
            }
        }
        return out;
    }

    private static Set<String> shippedIds(List<ShippedRules.Shipment> shipments) {
        Set<String> ids = new HashSet<>();
        shipments.forEach(shipment -> ids.addAll(shipment.rules().keySet()));
        return ids;
    }

    /**
     * The rules of a site, as {@link ContentRules} states them.
     *
     * @param shipments  the rules that the modules of the site ship
     * @param shippedIds the ids of the rules that any started module ships
     * @param platform   the rule nodes of {@code systemsite}, in path order
     * @param site       the rule nodes of the site, in path order
     * @return the rules of the site
     */
    static ContentRules merge(List<ShippedRules.Shipment> shipments, Set<String> shippedIds, List<RuleNode> platform,
                              List<RuleNode> site) {
        Map<String, ContentRule> out = shipped(shipments);
        Set<String> enabled = shippedIds(shipments);
        // A rule that a module ships applies only on a site that enables the module
        put(out, shipments, platform, id -> enabled.contains(id) || !shippedIds.contains(id));
        put(out, shipments, site, id -> true);
        return new ContentRules(out);
    }

    /** Puts each node whose id {@code applies} accepts, the first node of an id only, over the rule of its id. */
    private static void put(Map<String, ContentRule> out, List<ShippedRules.Shipment> shipments, List<RuleNode> nodes,
                            Predicate<String> applies) {
        Set<String> seen = new HashSet<>();
        for (RuleNode node : nodes) {
            Object id = node.values().get(RuleValues.RULE_ID);
            if (id == null || !seen.add(id.toString()) || !applies.test(id.toString())) {
                continue;
            }
            Map<String, Object> shippedValues = shippedValues(shipments, id.toString());
            Map<String, Object> merged = new LinkedHashMap<>(shippedValues);
            merged.putAll(node.values());
            merged.put(RuleValues.TRANSLATIONS, RuleValues.mergeTranslations(shippedValues.get(RuleValues.TRANSLATIONS),
                    node.values().get(RuleValues.TRANSLATIONS)));
            out.put(id.toString(), RuleValues.toRule(merged).inFolder(node.folder()));
        }
    }

    private static Map<String, Object> shippedValues(List<ShippedRules.Shipment> shipments, String id) {
        for (ShippedRules.Shipment shipment : shipments) {
            RuleFile rule = shipment.rules().get(id);
            if (rule != null) {
                return rule.values();
            }
        }
        return Map.of();
    }

    /** Every rule node under a path, in path order. */
    static List<JCRNodeWrapper> nodes(JCRSessionWrapper session, String path) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + TYPE + "] WHERE ISDESCENDANTNODE('" + path + "')", Query.JCR_SQL2);
        List<JCRNodeWrapper> out = new ArrayList<>();
        NodeIterator found = query.execute().getNodes();
        while (found.hasNext()) {
            out.add((JCRNodeWrapper) found.nextNode());
        }
        out.sort(Comparator.comparing(JCRNodeWrapper::getPath));
        return out;
    }

    /**
     * These rules with what a site changed on one of them. A null value keeps the current one.
     *
     * @param id       the id of the rule; an unknown id changes nothing
     * @param enabled  the switch
     * @param severity one of {@link ContentRule#SEVERITIES}; another value changes nothing
     * @param minimum  the lowest value that passes
     * @param maximum  the highest value that passes
     * @return the changed rules
     */
    public ContentRules changed(String id, Boolean enabled, String severity, Double minimum, Double maximum) {
        ContentRule rule = rules.get(id);
        if (rule == null) {
            return this;
        }
        Map<String, ContentRule> out = new LinkedHashMap<>(rules);
        out.put(id, rule.changedBy(enabled, severity, minimum, maximum));
        return new ContentRules(out);
    }

    /**
     * @param rule a rule, which replaces the rule of the same id
     * @return these rules with the rule, after the others when its id is new
     */
    public ContentRules with(ContentRule rule) {
        Map<String, ContentRule> out = new LinkedHashMap<>(rules);
        out.put(rule.id(), rule);
        return new ContentRules(out);
    }

    /**
     * @param id the id of a rule
     * @return the rule as the site applies it
     * @throws IllegalArgumentException when neither the site nor its modules have the rule
     */
    public ContentRule get(String id) {
        ContentRule rule = rules.get(id);
        if (rule == null) {
            throw new IllegalArgumentException("No rule has the id " + id);
        }
        return rule;
    }

    /** Every rule: the shipped ones in the order of their files, then the rules of the site. */
    public Collection<ContentRule> all() {
        return rules.values();
    }
}

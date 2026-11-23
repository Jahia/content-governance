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
import java.util.HashMap;
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
 * site, then every {@value #TYPE} node of the site whose id no module ships and {@code systemsite} does not
 * hold: a site adds rules, and does not change the values of a rule. Example: a rule {@code acme.noClickHere}
 * in {@code /sites/systemsite/contents} applies to {@code digitall}, and the writing rules of the frame of
 * {@code digitall} apply there only.
 *
 * <p>A rule node of the tree of {@code systemsite} takes the subdomain of its parent node, and any other rule
 * node names its subdomain. The {@link SiteLayout} of the site may place the rule in another subdomain. A
 * rule that names no subdomain takes the subdomain of the prefix of its id when one is declared, such as
 * {@code seo} for {@code seo.slug}, else {@value #TERMS} for a rule of the evaluator pattern, such as a term
 * that a site adds in jContent ({@code brand.seamless}).</p>
 *
 * <p>Every rule of the site stays in the list, and {@link ContentRule#enabled} tells whether it runs: the
 * layout does not switch it off, the layout places it or it is on by default for the site, its subdomain and
 * the domain of that subdomain are on ({@link ContentDomains}), and the site installs the module that ships
 * it. A rule whose subdomain no module and no node declares is unplaced: it never runs, and the settings
 * show why.</p>
 *
 * <p>The rule nodes are read in the edit workspace with a system session, so that a change applies
 * without a publication and a scheduled scan reads the same rules as the drawer. A node of {@code systemsite}
 * replaces the shipped rule of its id; a property that the node lacks keeps the shipped value. A node whose id
 * no module ships is a rule of its own, such as a rule of the customer. A node of {@code systemsite} whose rule
 * a module ships applies only to a site that enables that module.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class ContentRules {

    public static final String TYPE = "ccs:contentRule";
    /** The subdomain of the terms of a site, such as the forbidden terms of its legal team. */
    public static final String TERMS = "terms";

    private static final String EDIT_WORKSPACE = "default";
    private static final String SITES = "/sites/";
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Logger logger = LoggerFactory.getLogger(ContentRules.class);

    private final Map<String, ContentRule> rules;
    private final ContentDomains hierarchy;

    private ContentRules(Map<String, ContentRule> rules, ContentDomains hierarchy) {
        this.rules = rules;
        this.hierarchy = hierarchy;
    }

    /** The rules that apply on the site of the hierarchy, placed in it. */
    private static ContentRules of(Map<String, ContentRule> rules, ContentDomains hierarchy) {
        return new ContentRules(scoped(rules, hierarchy), hierarchy);
    }

    /** The rules that the started modules ship, as a site with no rule node applies them, in the shipped hierarchy. */
    public static ContentRules base() {
        return of(shipped(ShippedRules.all()), ContentDomains.base());
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
        // The nodes of the system site are the platform layer, for the system site itself too: they are its tree
        List<RuleNode> platform = ruleNodes(session, ContentDomains.SYSTEM_SITE);
        List<RuleNode> site = ContentDomains.SYSTEM_SITE.equals(sitePath) ? List.of() : ruleNodes(session, sitePath);
        return merge(ShippedRules.forModules(modules), shippedIds(ShippedRules.all()), platform, site,
                ContentDomains.read(session, siteKey, modules));
    }

    /**
     * A rule node as it was read.
     *
     * @param values the values of the node
     * @param folder the path of the folder that holds the node
     * @param order  the place of a rule node of the tree among its siblings, or {@value #OUTSIDE_TREE}
     */
    record RuleNode(Map<String, Object> values, String folder, int order) {

        RuleNode(Map<String, Object> values, String folder) {
            this(values, folder, OUTSIDE_TREE);
        }
    }

    /** The order of a rule node outside the tree, after the rules of the tree. */
    static final int OUTSIDE_TREE = 1_000_000;

    private static List<RuleNode> ruleNodes(JCRSessionWrapper session, String path) throws RepositoryException {
        List<RuleNode> out = new ArrayList<>();
        if (session.nodeExists(path)) {
            Map<String, List<String>> siblings = new HashMap<>();
            for (JCRNodeWrapper node : nodes(session, path)) {
                JCRNodeWrapper parent = node.getParent();
                int order = OUTSIDE_TREE;
                if (RuleNodes.parentSubdomain(node) != null) {
                    order = siblings.computeIfAbsent(parent.getPath(), ignored -> childNames(parent)).indexOf(node.getName());
                }
                out.add(new RuleNode(RuleNodes.read(node), parent.getPath(), order));
            }
        }
        return out;
    }

    /** The names of the child nodes of a node, in their order. */
    private static List<String> childNames(JCRNodeWrapper parent) {
        List<String> names = new ArrayList<>();
        try {
            for (JCRNodeWrapper child : parent.getNodes()) {
                names.add(child.getName());
            }
        } catch (RepositoryException e) {
            logger.debug("The children of {} could not be read", parent.getPath(), e);
        }
        return names;
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
     * @param hierarchy  the domains and the subdomains of the site
     * @return the rules of the site
     */
    static ContentRules merge(List<ShippedRules.Shipment> shipments, Set<String> shippedIds, List<RuleNode> platform,
                              List<RuleNode> site, ContentDomains hierarchy) {
        Map<String, ContentRule> out = shipped(shipments);
        Set<String> enabled = shippedIds(shipments);
        // A node of a rule that a module ships applies only on a site that enables the module: the seeded node
        // stays on the site when the site removes the module, and stops applying
        Predicate<String> applies = id -> enabled.contains(id) || !shippedIds.contains(id);
        put(out, shipments, platform, applies);
        // A site does not change the values of a rule: a node of the site counts only with an id of its own
        Set<String> platformIds = new HashSet<>();
        platform.forEach(node -> platformIds.add(String.valueOf(node.values().get(RuleValues.RULE_ID))));
        put(out, shipments, site, id -> !shippedIds.contains(id) && !platformIds.contains(id));
        return of(out, hierarchy);
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
            ContentRule rule = RuleValues.toRule(merged).inFolder(node.folder());
            out.put(id.toString(), rule.placedIn(rule.subdomain(), false, rule.enabled(), node.order()));
        }
    }

    /**
     * Every rule of the site, each placed in its subdomain on the site, and on when it runs there.
     *
     * @param rules     the rules by id
     * @param hierarchy the domains and the subdomains of the site, with its layout
     * @return the rules of the site, in the same order
     */
    static Map<String, ContentRule> scoped(Map<String, ContentRule> rules, ContentDomains hierarchy) {
        Map<String, ContentRule> out = new LinkedHashMap<>();
        for (ContentRule rule : rules.values()) {
            out.put(rule.id(), place(rule, hierarchy));
        }
        return out;
    }

    /**
     * The rule in its subdomain on the site, at its place, and on or off. The layout places the rule in a
     * declared subdomain; else the rule keeps its own subdomain, or the default one. A rule that the layout does
     * not place comes after the rules that it places, and is on when it is on by default for the site.
     */
    private static ContentRule place(ContentRule rule, ContentDomains hierarchy) {
        SiteLayout layout = hierarchy.layout();
        String byLayout = layout.subdomainOf(rule.id());
        String subdomain;
        if (byLayout != null && hierarchy.known(byLayout)) {
            subdomain = byLayout;
        } else {
            subdomain = rule.subdomain() != null ? rule.subdomain() : defaultSubdomain(rule.id(), rule.evaluator(), hierarchy);
        }
        String siteKey = hierarchy.siteKey();
        boolean byDefault = rule.enabled() && (siteKey == null || RuleValues.includes(rule.value(RuleValues.SITES), siteKey));
        boolean on = !layout.isOff(rule.id()) && (layout.places(rule.id()) || byDefault)
                && subdomain != null && hierarchy.places(subdomain);
        Integer order = layout.ruleOrder(rule.id());
        return rule.placedIn(subdomain, subdomain != null && hierarchy.known(subdomain), on,
                order != null ? order : ContentDomains.UNPLACED + Math.min(rule.order(), OUTSIDE_TREE));
    }

    /** The subdomain of a rule that names none: the prefix of its id when declared, else {@value #TERMS} for a pattern. */
    static String defaultSubdomain(String id, String evaluator, ContentDomains hierarchy) {
        String prefix = RuleValues.prefixOf(id);
        if (hierarchy.known(prefix)) {
            return prefix;
        }
        return ContentRule.PATTERN.equals(evaluator) ? TERMS : null;
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
        return new ContentRules(out, hierarchy);
    }

    /**
     * @param rule a rule, which replaces the rule of the same id
     * @return these rules with the rule placed in their hierarchy, after the others when its id is new
     */
    public ContentRules with(ContentRule rule) {
        Map<String, ContentRule> out = new LinkedHashMap<>(rules);
        out.put(rule.id(), place(rule, hierarchy));
        return new ContentRules(out, hierarchy);
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

    /** The domains and the subdomains of the site, which place these rules. */
    public ContentDomains hierarchy() {
        return hierarchy;
    }

    /** Every rule: the shipped ones in the order of their files, then the rules of the site. */
    public Collection<ContentRule> all() {
        return rules.values();
    }
}

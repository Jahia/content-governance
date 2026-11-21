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
import java.util.regex.Pattern;

/**
 * The rules as one site applies them: every {@value #TYPE} node of the site, and the rules that the
 * modules of the site ship and that the site has no node of.
 *
 * <p>A rule applies to a site when the rule, one of its subdomains and the domain of that subdomain all
 * include the site ({@link ContentDomains}), and the site installs the module that ships the rule. A rule
 * whose subdomains no module and no node declares stays, unplaced: it never runs, and the settings show
 * why. A rule that names no subdomain takes the subdomain of the prefix of its id when one is declared,
 * such as {@code seo} for {@code seo.slug}, else {@value #TERMS} for a rule of the evaluator pattern, such
 * as a term that a site adds in jContent ({@code brand.seamless}).</p>
 *
 * <p>The rule nodes are read in the edit workspace with a system session, so that a change applies
 * without a publication and a scheduled scan reads the same rules as the drawer. A node replaces the
 * shipped rule of its id; a property that the node lacks keeps the shipped value. A node whose id no
 * module ships is a rule of its own, such as a rule of the customer.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class ContentRules {

    public static final String TYPE = "ccs:contentRule";
    /** The type of the folder that takes the rules of one module. */
    public static final String FOLDER_TYPE = "ccs:contentRuleFolder";
    /** The folder, under the site node, of the folders of the rules: one per module, and one for the site. */
    public static final String ROOT = "contents/content-insights";
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

    /** The folder, under the site node, of the rules that a module ships. */
    public static String folderOf(String module) {
        return ROOT + "/" + module + "/rules";
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
        List<ShippedRules.Shipment> shipments = ShippedRules.forModules(modules);
        Map<String, ContentRule> out = shipped(shipments);
        Set<String> seen = new HashSet<>();
        for (JCRNodeWrapper node : nodes(session, sitePath)) {
            Map<String, Object> values = RuleNodes.read(node);
            Object id = values.get(RuleValues.RULE_ID);
            if (id == null || !seen.add(id.toString()) || !shippedToSite(id.toString(), modules)) {
                continue;
            }
            Map<String, Object> shippedValues = shippedValues(shipments, id.toString());
            Map<String, Object> merged = new LinkedHashMap<>(shippedValues);
            merged.putAll(values);
            merged.put(RuleValues.TRANSLATIONS, RuleValues.mergeTranslations(shippedValues.get(RuleValues.TRANSLATIONS),
                    values.get(RuleValues.TRANSLATIONS)));
            out.put(id.toString(), RuleValues.toRule(merged).inFolder(node.getParent().getPath()));
        }
        return of(out, ContentDomains.read(session, siteKey, modules));
    }

    /**
     * False for the node of a rule that a started module ships and that the site does not install: the seeded
     * node stays on the site when the site removes the module, and stops applying.
     */
    private static boolean shippedToSite(String id, Collection<String> modules) {
        String module = ShippedRules.moduleOfRule(id);
        return module == null || modules.contains(module);
    }

    /**
     * The rules that apply on the site of the hierarchy, each with its subdomains and whether one of them has
     * a place on the site. A rule out of the site goes; a rule whose subdomains nobody declares stays,
     * unplaced.
     *
     * @param rules     the rules by id
     * @param hierarchy the domains and the subdomains of the site
     * @return the rules of the site, in the same order
     */
    static Map<String, ContentRule> scoped(Map<String, ContentRule> rules, ContentDomains hierarchy) {
        Map<String, ContentRule> out = new LinkedHashMap<>();
        for (ContentRule rule : rules.values()) {
            String siteKey = hierarchy.siteKey();
            if (siteKey != null && !RuleValues.includes(rule.value(RuleValues.SITES), siteKey)) {
                continue;
            }
            ContentRule placed = place(rule, hierarchy);
            if (placed.placed() || placed.subdomains().stream().noneMatch(hierarchy::known)) {
                out.put(rule.id(), placed);
            }
        }
        return out;
    }

    /** The rule with its subdomains, default ones included, and whether one of them has a place on the site. */
    private static ContentRule place(ContentRule rule, ContentDomains hierarchy) {
        List<String> subdomains = rule.subdomains().isEmpty()
                ? defaultSubdomains(rule.id(), rule.evaluator(), hierarchy) : rule.subdomains();
        return rule.placedIn(subdomains, subdomains.stream().anyMatch(hierarchy::places));
    }

    /** The subdomains of a rule that names none: the prefix of its id when declared, else {@value #TERMS} for a pattern. */
    static List<String> defaultSubdomains(String id, String evaluator, ContentDomains hierarchy) {
        String prefix = RuleValues.prefixOf(id);
        if (hierarchy.known(prefix)) {
            return List.of(prefix);
        }
        return ContentRule.PATTERN.equals(evaluator) ? List.of(TERMS) : List.of();
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

    /** The domains and the subdomains that place these rules. */
    public ContentDomains hierarchy() {
        return hierarchy;
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

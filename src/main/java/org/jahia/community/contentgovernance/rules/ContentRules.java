package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The rules as one site applies them: the base catalog, with the switch, the severity and the limits
 * of each rule node of the site.
 *
 * <p>The rule nodes are {@value #TYPE} nodes in {@code /sites/<key>/}{@value #FOLDER}, read in the edit
 * workspace with a system session, so that a change applies without a publication and a scheduled
 * scan reads the same rules as the drawer. A rule with no node keeps its default, and a node whose rule
 * id the catalog does not know is ignored.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class ContentRules {

    /** The folder of the rules that the module seeds, under the site node. */
    public static final String FOLDER = "contents/content-insights/jahia/rules";
    public static final String TYPE = "ccs:contentRule";
    /** The type of the last folder of {@link #FOLDER}, which takes the rule nodes. */
    public static final String FOLDER_TYPE = "ccs:contentRuleFolder";
    static final String RULE_ID = "ruleId";
    static final String ENABLED = "enabled";
    static final String SEVERITY = "severity";
    static final String MINIMUM = "minimum";
    static final String MAXIMUM = "maximum";

    private static final String EDIT_WORKSPACE = "default";
    private static final String SITES = "/sites/";
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Logger logger = LoggerFactory.getLogger(ContentRules.class);

    private final Map<String, ContentRule> rules;

    private ContentRules(Map<String, ContentRule> rules) {
        this.rules = rules;
    }

    /** The defaults of the catalog, as a site with no rule node applies them. */
    public static ContentRules base() {
        return new ContentRules(RuleCatalog.base());
    }

    /**
     * The rules of a site. A failure to read the site gives the defaults, and is logged.
     *
     * @param siteKey the key of the site
     * @return the rules of the site
     */
    public static ContentRules forSite(String siteKey) {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            return base();
        }
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<ContentRules>) session -> read(session, SITES + siteKey + "/" + FOLDER));
        } catch (RepositoryException | RuntimeException e) {
            // A check never fails for its rules: with no repository, it applies the defaults.
            logger.warn("The rules of the site {} could not be read, the defaults apply", siteKey, e);
            return base();
        }
    }

    /**
     * The rules of the site that owns {@code path}, such as {@code /sites/digitall/home}.
     *
     * @param path a path under {@code /sites/<key>}
     * @return the rules of that site, or the defaults when the path is not under a site
     */
    public static ContentRules forPath(String path) {
        if (path == null || !path.startsWith(SITES)) {
            return base();
        }
        int end = path.indexOf('/', SITES.length());
        return forSite(end < 0 ? path.substring(SITES.length()) : path.substring(SITES.length(), end));
    }

    private static ContentRules read(JCRSessionWrapper session, String folder) throws RepositoryException {
        ContentRules out = base();
        if (!session.nodeExists(folder)) {
            return out;
        }
        for (JCRNodeWrapper node : session.getNode(folder).getNodes()) {
            if (node.isNodeType(TYPE) && node.hasProperty(RULE_ID)) {
                out = out.changed(node.getProperty(RULE_ID).getString(), flag(node, ENABLED), text(node, SEVERITY),
                        number(node, MINIMUM), number(node, MAXIMUM));
            }
        }
        return out;
    }

    private static Boolean flag(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getBoolean() : null;
    }

    private static String text(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString() : null;
    }

    private static Double number(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getDouble() : null;
    }

    /**
     * These rules with what a site changed on one of them. A null value keeps the current one, and so
     * does a limit that the rule does not have in the catalog.
     *
     * @param id       the id of the rule; an id the catalog does not know changes nothing
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
     * @param id the id of a rule of the catalog
     * @return the rule as the site applies it
     * @throws IllegalArgumentException when the catalog has no such rule
     */
    public ContentRule get(String id) {
        ContentRule rule = rules.get(id);
        if (rule == null) {
            throw new IllegalArgumentException("The rule catalog has no rule " + id);
        }
        return rule;
    }

    /** Every rule, in the order of the catalog. */
    public Collection<ContentRule> all() {
        return rules.values();
    }
}

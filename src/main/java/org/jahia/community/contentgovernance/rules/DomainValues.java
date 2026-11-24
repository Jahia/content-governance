package org.jahia.community.contentgovernance.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The values of a domain or a subdomain, keyed as a domain file and the seeder key them. A domain is a main
 * tab of a panel of the drawer, such as Page quality; a subdomain is a tab of a domain, such as SEO, and each
 * rule belongs to one subdomain.
 *
 * <p>A value is a {@link String}, a {@link Long} or a {@link List} of strings. The key {@value #ID} is the
 * property {@link Level#idProperty} of the node. The keys {@value #DOMAIN} and {@value #ORDER} are not
 * properties: on a node of the tree, the parent node gives the domain and the place among its siblings gives
 * the order.</p>
 */
public final class DomainValues {

    /** The id, such as {@code quality} or {@code seo}: unique among the domains and the subdomains together. */
    public static final String ID = "id";
    /** The id of the domain of a subdomain: its parent node in the tree. */
    public static final String DOMAIN = "domain";
    /** The panel of the drawer that shows a domain, such as {@value #GOVERNANCE}. */
    public static final String PANEL = "panel";
    /**
     * The place among its siblings: a lower order comes first. A file states it, and the seeder places a new
     * node by it; a node of the tree takes it from its place among its siblings.
     */
    public static final String ORDER = "order";
    /** The ids of the rules of other subdomains that a subdomain counts, such as the rules of the GEO score. */
    public static final String COUNTS = "counts";
    public static final String SITES = RuleValues.SITES;
    /** Internationalized on the node: the name of the tab. */
    public static final String LABEL = "label";
    /** Internationalized on the node: what the tab checks, for the editor. */
    public static final String HELP = "help";
    /** Not a property: the texts in the languages other than English, as {@code Map<language, Map<key, text>>}. */
    public static final String TRANSLATIONS = RuleValues.TRANSLATIONS;
    /** The panel of the checks of Content Governance. */
    public static final String GOVERNANCE = "governance";
    static final List<String> TRANSLATABLE = List.of(LABEL, HELP);

    /** A domain or a subdomain: its node type, the property of its id, and the folder of its files. */
    public enum Level {
        DOMAIN(ContentDomains.DOMAIN_TYPE, "domainId", "domains", List.of(ID, PANEL, SITES, LABEL, HELP)),
        SUBDOMAIN(ContentDomains.SUBDOMAIN_TYPE, "subdomainId", "subdomains", List.of(ID, DomainValues.DOMAIN, SITES, LABEL, HELP, COUNTS));

        private final String type;
        private final String idProperty;
        private final String folder;
        private final List<String> keys;

        Level(String type, String idProperty, String folder, List<String> keys) {
            this.type = type;
            this.idProperty = idProperty;
            this.folder = folder;
            this.keys = keys;
        }

        /** The node type, such as {@code ccs:contentDomain}. */
        public String type() {
            return type;
        }

        /** The property that holds the id on the node. */
        public String idProperty() {
            return idProperty;
        }

        /** The subfolder of the rule folder of a module that holds the files. */
        public String folder() {
            return folder;
        }

        /**
         * Every key of the values, in the order of the fingerprint. The fingerprint of a subdomain holds its
         * domain, so that the seeder tells a subdomain that an administrator moved.
         */
        public List<String> keys() {
            return keys;
        }

        /** The keys that a file may set: the keys of the values, and {@value #ORDER}. */
        List<String> fileKeys() {
            List<String> out = new ArrayList<>(keys);
            out.add(ORDER);
            return out;
        }

        /** The property of the node that holds the value of a key, or null for a key that the tree gives. */
        String property(String key) {
            if (DomainValues.DOMAIN.equals(key) || ORDER.equals(key)) {
                return null;
            }
            return ID.equals(key) ? idProperty : key;
        }
    }

    private DomainValues() {
    }

    /** The values with the defaults of the node type: all sites, and the governance panel for a domain. */
    static Map<String, Object> withDefaults(Level level, Map<String, Object> values) {
        Map<String, Object> out = new HashMap<>(values);
        out.putIfAbsent(SITES, List.of(RuleValues.ALL_SITES));
        if (level == Level.DOMAIN) {
            out.putIfAbsent(PANEL, GOVERNANCE);
        }
        return out;
    }

    /** A fingerprint of the values, defaults included, as {@link RuleValues#digest} gives one for a rule. */
    static String digest(Level level, Map<String, Object> values) {
        return RuleValues.fingerprint(withDefaults(level, values), level.keys());
    }
}

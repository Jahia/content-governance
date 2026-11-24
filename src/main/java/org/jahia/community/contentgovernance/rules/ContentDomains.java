package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The domains and the subdomains as one site sees them: the hierarchy drawer › panel › domain › subdomain
 * › rule, without the rules.
 *
 * <p>The tree of the system site, {@value #TREE}, holds the domains and the subdomains of every site: a domain
 * node holds its subdomains, in their order. A node replaces the shipped values of its id; a property that the
 * node lacks keeps the shipped value. A module that the site does not install ships nothing to it.</p>
 *
 * <p>The {@link SiteLayout} of the site then places them: it orders the domains, puts a subdomain in another
 * domain, and switches an item off. An item that the layout does not place keeps its place in the tree, after
 * the items that the layout places, and is on when its {@code sites} include the site.</p>
 *
 * <p>Example: the layout of {@code digitall} places the subdomain {@code seo} in the domain {@code quality}.
 * On {@code digitall}, the SEO tab and its rules move to Page quality; every other site keeps them in SEO &amp;
 * GEO.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class ContentDomains {

    public static final String DOMAIN_TYPE = "ccs:contentDomain";
    public static final String SUBDOMAIN_TYPE = "ccs:contentSubdomain";
    /** The type of the folders of the domains and the subdomains of one module before version 2.0.0. */
    public static final String FOLDER_TYPE = "ccs:contentDomainFolder";
    /** The key of the site whose tree holds the domains, the subdomains and the rules of every site. */
    public static final String SYSTEM_SITE_KEY = "systemsite";
    /** The path of that site. */
    public static final String SYSTEM_SITE = "/sites/" + SYSTEM_SITE_KEY;
    /** The folder of the tree, in the system site. */
    public static final String TREE = SYSTEM_SITE + "/" + SiteLayout.FOLDER;

    /** The order of an item that the layout does not place comes after this one. */
    static final int UNPLACED = 10_000;
    /** The order of an item that a module ships and that the tree has no node of yet comes after this one. */
    private static final int UNSEEDED = 100_000;
    private static final int LAST = 1_000_000;
    private static final String EDIT_WORKSPACE = "default";
    private static final String SITES = "/sites/";
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Logger logger = LoggerFactory.getLogger(ContentDomains.class);

    private final String siteKey;
    private final SiteLayout layout;
    private final Map<String, Domain> domains;
    private final Map<String, Domain> subdomains;

    /**
     * @param siteKey the key of the site, or null for the shipped hierarchy, which every site sees
     * @param values  the values of each domain and subdomain, by level and by id, with their default order
     * @param layout  the layout of the site
     */
    ContentDomains(String siteKey, Map<Level, Map<String, Map<String, Object>>> values, SiteLayout layout) {
        this.siteKey = siteKey;
        this.layout = layout;
        this.domains = new LinkedHashMap<>();
        values.getOrDefault(Level.DOMAIN, Map.of()).forEach((id, value) -> {
            Domain domain = Domain.of(Level.DOMAIN, value);
            Integer placed = layout.domainOrder(id);
            domains.put(id, domain.at(domain.parent(), placed != null ? placed : UNPLACED + bounded(domain.order())));
        });
        this.subdomains = new LinkedHashMap<>();
        values.getOrDefault(Level.SUBDOMAIN, Map.of()).forEach((id, value) -> {
            Domain subdomain = Domain.of(Level.SUBDOMAIN, value);
            String parent = layout.domainOf(id);
            Integer placed = layout.subdomainOrder(id);
            subdomains.put(id, subdomain.at(parent != null && domains.containsKey(parent) ? parent : subdomain.parent(),
                    placed != null ? placed : UNPLACED + bounded(subdomain.order())));
        });
    }

    private static int bounded(int order) {
        return Math.max(0, Math.min(order, LAST));
    }

    /** The domains and the subdomains that the started modules ship, for every site. */
    public static ContentDomains base() {
        return new ContentDomains(null, shipped(ShippedRules.all()), SiteLayout.NONE);
    }

    /**
     * The domains and the subdomains of a site. A failure to read the repository gives the shipped ones of
     * every module, and is logged.
     *
     * @param siteKey the key of the site
     */
    public static ContentDomains forSite(String siteKey) {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            return base();
        }
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, Locale.ENGLISH,
                    (JCRCallback<ContentDomains>) session -> {
                        String sitePath = SITES + siteKey;
                        Collection<String> modules = session.nodeExists(sitePath) && session.getNode(sitePath) instanceof JCRSiteNode site
                                ? modulesOf(site) : List.of();
                        return read(session, siteKey, modules);
                    });
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The domains of the site {} could not be read, the shipped ones apply", siteKey, e);
            return new ContentDomains(siteKey, shipped(ShippedRules.all()), SiteLayout.NONE);
        }
    }

    /**
     * The domains and the subdomains of a site, read through a session that the caller holds.
     *
     * @param session a session of the edit workspace in English
     * @param site    the site
     */
    public static ContentDomains read(JCRSessionWrapper session, JCRSiteNode site) throws RepositoryException {
        return read(session, site.getSiteKey(), modulesOf(site));
    }

    /**
     * The modules whose domains, subdomains and rules a site sees: those it installs. The system site sees those of
     * every started module, since its tree holds the defaults of every site, and it installs none of them.
     */
    static Collection<String> modulesOf(JCRSiteNode site) {
        if (SYSTEM_SITE_KEY.equals(site.getSiteKey())) {
            return ShippedRules.all().stream().map(ShippedRules.Shipment::module).toList();
        }
        return site.getInstalledModulesWithAllDependencies();
    }

    /**
     * @param session a session of the edit workspace in English
     * @param siteKey the key of the site
     * @param modules the modules that the site installs
     */
    static ContentDomains read(JCRSessionWrapper session, String siteKey, Collection<String> modules) throws RepositoryException {
        Map<Level, Map<String, Map<String, Object>>> values = shipped(ShippedRules.forModules(modules));
        if (session.nodeExists(TREE)) {
            values.values().forEach(byId -> byId.values().forEach(value -> value.put(DomainValues.ORDER,
                    (long) UNSEEDED + orderOf(value))));
            readTree(session.getNode(TREE), modules, values);
        }
        String sitePath = SITES + siteKey;
        SiteLayout layout = sitePath.equals(SYSTEM_SITE) ? SiteLayout.NONE : SiteLayout.read(session, sitePath);
        return new ContentDomains(siteKey, values, layout);
    }

    private static int orderOf(Map<String, Object> value) {
        return value.get(DomainValues.ORDER) instanceof Number number ? bounded((int) Math.min(number.longValue(), LAST)) : 0;
    }

    /** Puts the values of each domain and subdomain node of the tree over the shipped ones, with its place as its order. */
    private static void readTree(JCRNodeWrapper tree, Collection<String> modules, Map<Level, Map<String, Map<String, Object>>> values)
            throws RepositoryException {
        Set<String> seen = new HashSet<>();
        int domainIndex = 0;
        for (JCRNodeWrapper domain : tree.getNodes()) {
            if (!domain.isNodeType(DOMAIN_TYPE)) {
                continue;
            }
            put(domain, Level.DOMAIN, domainIndex++, modules, values, seen);
            int subdomainIndex = 0;
            for (JCRNodeWrapper subdomain : domain.getNodes()) {
                if (subdomain.isNodeType(SUBDOMAIN_TYPE)) {
                    put(subdomain, Level.SUBDOMAIN, subdomainIndex++, modules, values, seen);
                }
            }
        }
    }

    /** Puts the values of one node over the shipped ones; the first node of an id wins. */
    private static void put(JCRNodeWrapper node, Level level, int index, Collection<String> modules,
                            Map<Level, Map<String, Map<String, Object>>> values, Set<String> seen) throws RepositoryException {
        Map<String, Object> read = DomainNodes.read(node, level);
        Object id = read.get(DomainValues.ID);
        if (id == null || !shippedToSite(level, id.toString(), modules) || !seen.add(level + ":" + id)) {
            return;
        }
        read.put(DomainValues.ORDER, (long) index);
        values.get(level).merge(id.toString(), read, ContentDomains::over);
    }

    /** False for the node of a domain or a subdomain that a started module ships and that the site does not install. */
    private static boolean shippedToSite(Level level, String id, Collection<String> modules) {
        String module = ShippedRules.moduleOf(level, id);
        return module == null || modules.contains(module);
    }

    /** The values of a node on top of the values that it replaces: a text of the node replaces the text of its language. */
    private static Map<String, Object> over(Map<String, Object> base, Map<String, Object> node) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        merged.putAll(node);
        merged.put(DomainValues.TRANSLATIONS, RuleValues.mergeTranslations(base.get(DomainValues.TRANSLATIONS),
                node.get(DomainValues.TRANSLATIONS)));
        return merged;
    }

    private static Map<Level, Map<String, Map<String, Object>>> shipped(List<ShippedRules.Shipment> shipments) {
        Map<Level, Map<String, Map<String, Object>>> out = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (ShippedRules.Shipment shipment : shipments) {
                shipment.of(level).forEach((id, file) -> byId.putIfAbsent(id, new LinkedHashMap<>(file.values())));
            }
            out.put(level, byId);
        }
        return out;
    }

    /** The domains that are on for the site, by order and then by id. */
    public List<Domain> domains() {
        return sorted(domains.values().stream().filter(domain -> isOn(domain.id())).toList());
    }

    /** Every domain, on or off for the site, by order and then by id. */
    public List<Domain> allDomains() {
        return sorted(List.copyOf(domains.values()));
    }

    /** The subdomains of a domain that are on for the site, by order and then by id. */
    public List<Domain> subdomainsOf(String domainId) {
        return sorted(subdomains.values().stream()
                .filter(subdomain -> domainId.equals(subdomain.parent()) && isOn(subdomain.id())).toList());
    }

    /** Every subdomain of a domain, on or off for the site, by order and then by id. */
    public List<Domain> allSubdomainsOf(String domainId) {
        return sorted(subdomains.values().stream().filter(subdomain -> domainId.equals(subdomain.parent())).toList());
    }

    private static List<Domain> sorted(List<Domain> list) {
        return list.stream().sorted(Comparator.comparingInt(Domain::order).thenComparing(Domain::id)).toList();
    }

    /**
     * True when the domain or the subdomain of this id is on for the site: the layout does not switch it off, and
     * the layout places it or its {@code sites} include the site. A subdomain needs its domain on too.
     */
    public boolean isOn(String id) {
        Domain item = item(id);
        if (item == null || layout.isOff(id) || !(layout.places(id) || item.onByDefault(siteKey))) {
            return false;
        }
        return item.parent() == null || isOn(item.parent());
    }

    /**
     * The switch of the domain or the subdomain of this id itself, whatever its domain: what the screen of the
     * layout shows. On a site, the layout switches it, else its default does. In the system site, it is the
     * default switch of the node. Example: {@code digitall} switches off the domain {@code geo}; the subdomain
     * {@code seo} keeps its switch on, and is not on, since its domain is off.
     */
    public boolean switchedOn(String id) {
        Domain item = item(id);
        if (item == null) {
            return false;
        }
        if (SYSTEM_SITE_KEY.equals(siteKey)) {
            return item.enabled();
        }
        return !layout.isOff(id) && (layout.places(id) || item.onByDefault(siteKey));
    }

    /** True when a domain or a subdomain of this id is declared. */
    public boolean declares(String id) {
        return item(id) != null;
    }

    /**
     * The panel of the drawer that shows the domain or the subdomain of this id on the site, such as
     * {@value DomainValues#GOVERNANCE}, or null when the id is not declared or its domain is not.
     */
    public String panelOf(String id) {
        Domain item = item(id);
        if (item != null && item.parent() != null) {
            item = domains.get(item.parent());
        }
        return item == null ? null : item.panel();
    }

    private Domain item(String id) {
        return domains.containsKey(id) ? domains.get(id) : subdomains.get(id);
    }

    /** True when the site saved a layout that does not name this id: a module shipped it after the save. */
    public boolean isNew(String id) {
        return layout.isNew(id);
    }

    /** True when a subdomain of this id is declared, under a declared domain, on any site. */
    public boolean known(String subdomainId) {
        Domain subdomain = subdomains.get(subdomainId);
        return subdomain != null && domains.containsKey(subdomain.parent());
    }

    /** True when the subdomain and its domain are on for the site: a finding of the subdomain then has a place. */
    public boolean places(String subdomainId) {
        return known(subdomainId) && isOn(subdomainId);
    }

    /** The layout of the site. */
    public SiteLayout layout() {
        return layout;
    }

    /** The key of the site, or null for the shipped hierarchy. */
    public String siteKey() {
        return siteKey;
    }
}

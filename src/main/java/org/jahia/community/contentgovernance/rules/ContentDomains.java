package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
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
 * <p>The nodes of the system site hold the domains and the subdomains of every site; a site adds or
 * overrides one with a node of the same id under its own {@code contents}. A node replaces the shipped
 * values of its id; a property that the node lacks keeps the shipped value. A module that the site does
 * not install ships nothing to it. A domain or a subdomain applies to a site when its {@code sites} include
 * the site, and a subdomain only under a domain that applies too.</p>
 *
 * <p>Example: the subdomain {@code seo} names the domain {@code geo}. An administrator who changes it to
 * {@code quality} on its node moves the SEO tab and its rules, with no rule to edit.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class ContentDomains {

    public static final String DOMAIN_TYPE = "ccs:contentDomain";
    public static final String SUBDOMAIN_TYPE = "ccs:contentSubdomain";
    /** The type of the folders that take the domains and the subdomains of one module. */
    public static final String FOLDER_TYPE = "ccs:contentDomainFolder";
    /** The site whose nodes hold the domains and the subdomains of every site. */
    public static final String SYSTEM_SITE = "/sites/systemsite";

    private static final String EDIT_WORKSPACE = "default";
    private static final String SITES = "/sites/";
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Logger logger = LoggerFactory.getLogger(ContentDomains.class);

    private final String siteKey;
    private final Map<String, Domain> domains;
    private final Map<String, Domain> subdomains;

    /**
     * @param siteKey the key of the site, or null for the shipped hierarchy, which every site sees
     * @param values  the values of each domain and subdomain, by level and by id
     */
    ContentDomains(String siteKey, Map<Level, Map<String, Map<String, Object>>> values) {
        this.siteKey = siteKey;
        this.domains = build(Level.DOMAIN, values.getOrDefault(Level.DOMAIN, Map.of()));
        this.subdomains = build(Level.SUBDOMAIN, values.getOrDefault(Level.SUBDOMAIN, Map.of()));
    }

    private static Map<String, Domain> build(Level level, Map<String, Map<String, Object>> values) {
        Map<String, Domain> out = new LinkedHashMap<>();
        values.forEach((id, value) -> out.put(id, Domain.of(level, value)));
        return out;
    }

    /** The folder, under a site node, of the domains or the subdomains that a module ships. */
    public static String folderOf(String module, Level level) {
        return ContentRules.ROOT + "/" + module + "/" + level.folder();
    }

    /** The domains and the subdomains that the started modules ship, for every site. */
    public static ContentDomains base() {
        return new ContentDomains(null, shipped(ShippedRules.all()));
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
                                ? site.getInstalledModulesWithAllDependencies() : List.of();
                        return read(session, siteKey, modules);
                    });
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The domains of the site {} could not be read, the shipped ones apply", siteKey, e);
            return new ContentDomains(siteKey, shipped(ShippedRules.all()));
        }
    }

    /**
     * @param session a session of the edit workspace in English
     * @param siteKey the key of the site
     * @param modules the modules that the site installs
     */
    static ContentDomains read(JCRSessionWrapper session, String siteKey, Collection<String> modules) throws RepositoryException {
        Map<Level, Map<String, Map<String, Object>>> values = shipped(ShippedRules.forModules(modules));
        List<String> paths = new ArrayList<>(List.of(SYSTEM_SITE));
        if (!(SITES + siteKey).equals(SYSTEM_SITE)) {
            paths.add(SITES + siteKey);
        }
        for (String path : paths) {
            for (Level level : Level.values()) {
                Set<String> seen = new HashSet<>();
                for (JCRNodeWrapper node : nodes(session, level, path)) {
                    Map<String, Object> read = DomainNodes.read(node, level);
                    Object id = read.get(DomainValues.ID);
                    if (id != null && seen.add(id.toString()) && shippedToSite(level, id.toString(), modules)) {
                        values.get(level).merge(id.toString(), read, ContentDomains::over);
                    }
                }
            }
        }
        return new ContentDomains(siteKey, values);
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
                shipment.of(level).forEach((id, file) -> byId.putIfAbsent(id, file.values()));
            }
            out.put(level, byId);
        }
        return out;
    }

    /** Every domain or subdomain node under a path, in path order. */
    private static List<JCRNodeWrapper> nodes(JCRSessionWrapper session, Level level, String path) throws RepositoryException {
        List<JCRNodeWrapper> out = new ArrayList<>();
        if (!session.nodeExists(path)) {
            return out;
        }
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + level.type() + "] WHERE ISDESCENDANTNODE('" + path + "')", Query.JCR_SQL2);
        NodeIterator found = query.execute().getNodes();
        while (found.hasNext()) {
            out.add((JCRNodeWrapper) found.nextNode());
        }
        out.sort(Comparator.comparing(JCRNodeWrapper::getPath));
        return out;
    }

    /** The domains that apply to the site, by order and then by id. */
    public List<Domain> domains() {
        return sorted(domains.values().stream().filter(domain -> domain.includes(siteKey)).toList());
    }

    /** The subdomains of a domain that apply to the site, by order and then by id. */
    public List<Domain> subdomainsOf(String domainId) {
        return sorted(subdomains.values().stream()
                .filter(subdomain -> domainId.equals(subdomain.parent()) && subdomain.includes(siteKey)).toList());
    }

    private static List<Domain> sorted(List<Domain> list) {
        return list.stream().sorted(Comparator.comparingInt(Domain::order).thenComparing(Domain::id)).toList();
    }

    /** True when a subdomain of this id is declared, under a declared domain, on any site. */
    public boolean known(String subdomainId) {
        Domain subdomain = subdomains.get(subdomainId);
        return subdomain != null && domains.containsKey(subdomain.parent());
    }

    /** True when the subdomain and its domain apply to the site: a finding of the subdomain then has a place. */
    public boolean places(String subdomainId) {
        Domain subdomain = subdomains.get(subdomainId);
        Domain domain = subdomain == null ? null : domains.get(subdomain.parent());
        return domain != null && subdomain.includes(siteKey) && domain.includes(siteKey);
    }

    /** The key of the site, or null for the shipped hierarchy. */
    public String siteKey() {
        return siteKey;
    }
}

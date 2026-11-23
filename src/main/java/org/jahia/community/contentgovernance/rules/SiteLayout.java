package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a site arranges the tree of the system site: the order and the place of each domain, subdomain and rule,
 * and the ids that the site switches off. The site keeps it as JSON in the property {@value #PROPERTY} of its
 * folder {@value #FOLDER}:
 *
 * <pre>
 * {"domains": [{"id": "geo", "subdomains": [{"id": "seo", "rules": ["seo.title", "seo.metaDescription"]}]}],
 *  "off": ["vitals", "seo.indexable"]}
 * </pre>
 *
 * <p>The position in an array is the order, and the parent array is the place. The layout places an item that
 * it names in an array, and that item is on unless {@code off} names it. An item that the layout does not name
 * keeps its default place and its default switch: a rule that a module ships after the site saved its layout
 * shows in its default subdomain, after the rules that the site ordered.</p>
 *
 * <p>Thread safety: immutable.</p>
 */
public final class SiteLayout {

    /** The folder of a site that holds the layout, under the site node. */
    public static final String FOLDER = "contents/content-governance";
    /** The type of that folder, which holds the tree in the system site. */
    public static final String FOLDER_TYPE = "jnt:contentFolder";
    /** The mixin that the folder of a site takes to hold its layout. */
    public static final String MIXIN = "ccsmix:siteLayout";
    /** The property of the folder that holds the JSON. */
    public static final String PROPERTY = "layout";
    /** The layout of a site that saved none: every item at its default place, with its default switch. */
    public static final SiteLayout NONE = new SiteLayout(List.of(), Set.of(), false);
    /** The most ids that a layout names, every array and {@code off} together. */
    static final int MAX_IDS = 5000;

    private static final String DOMAINS = "domains";
    private static final String SUBDOMAINS = "subdomains";
    private static final String RULES = "rules";
    private static final String OFF = "off";
    private static final String ID = "id";
    private static final Logger logger = LoggerFactory.getLogger(SiteLayout.class);

    /**
     * A domain that the layout places, with its subdomains in their order.
     *
     * @param id         the id of the domain
     * @param subdomains the subdomains that the layout places in the domain
     */
    public record DomainPlace(String id, List<SubdomainPlace> subdomains) {
        public DomainPlace {
            subdomains = List.copyOf(subdomains);
        }
    }

    /**
     * A subdomain that the layout places, with its rules in their order.
     *
     * @param id    the id of the subdomain
     * @param rules the ids of the rules that the layout places in the subdomain
     */
    public record SubdomainPlace(String id, List<String> rules) {
        public SubdomainPlace {
            rules = List.copyOf(rules);
        }
    }

    private final List<DomainPlace> domains;
    private final Set<String> off;
    private final boolean saved;
    private final Map<String, Integer> domainOrder = new HashMap<>();
    private final Map<String, String> domainOfSubdomain = new HashMap<>();
    private final Map<String, Integer> subdomainOrder = new HashMap<>();
    private final Map<String, String> subdomainOfRule = new HashMap<>();
    private final Map<String, Integer> ruleOrder = new HashMap<>();

    /**
     * @param domains the domains in their order; an id that comes a second time keeps its first place
     * @param off     the ids of the domains, subdomains and rules that the site switches off
     * @param saved   true when the site saved a layout
     */
    SiteLayout(List<DomainPlace> domains, Set<String> off, boolean saved) {
        this.domains = List.copyOf(domains);
        this.off = Collections.unmodifiableSet(new LinkedHashSet<>(off));
        this.saved = saved;
        for (DomainPlace domain : this.domains) {
            domainOrder.putIfAbsent(domain.id(), domainOrder.size());
            for (SubdomainPlace subdomain : domain.subdomains()) {
                if (domainOfSubdomain.putIfAbsent(subdomain.id(), domain.id()) == null) {
                    subdomainOrder.put(subdomain.id(), subdomainOrder.size());
                }
                for (String rule : subdomain.rules()) {
                    if (subdomainOfRule.putIfAbsent(rule, subdomain.id()) == null) {
                        ruleOrder.put(rule, ruleOrder.size());
                    }
                }
            }
        }
    }

    /**
     * Reads the JSON of a layout.
     *
     * @param json the JSON, or null or blank for no layout
     * @return the layout, or {@link #NONE}
     * @throws IllegalArgumentException when the JSON is not a layout, names an id twice, names an id that is not
     *                                  one, or names more than {@value #MAX_IDS} ids
     */
    public static SiteLayout parse(String json) {
        if (json == null || json.isBlank()) {
            return NONE;
        }
        try {
            JSONObject root = new JSONObject(json);
            Set<String> seen = new HashSet<>();
            List<DomainPlace> domains = new ArrayList<>();
            JSONArray domainArray = root.optJSONArray(DOMAINS);
            for (int i = 0; domainArray != null && i < domainArray.length(); i++) {
                JSONObject domain = domainArray.getJSONObject(i);
                List<SubdomainPlace> subdomains = new ArrayList<>();
                JSONArray subdomainArray = domain.optJSONArray(SUBDOMAINS);
                for (int j = 0; subdomainArray != null && j < subdomainArray.length(); j++) {
                    JSONObject subdomain = subdomainArray.getJSONObject(j);
                    subdomains.add(new SubdomainPlace(id(subdomain.getString(ID), seen),
                            ids(subdomain.optJSONArray(RULES), seen)));
                }
                domains.add(new DomainPlace(id(domain.getString(ID), seen), subdomains));
            }
            Set<String> off = new LinkedHashSet<>(ids(root.optJSONArray(OFF), new HashSet<>()));
            if (seen.size() + off.size() > MAX_IDS) {
                throw new IllegalArgumentException("A layout names " + MAX_IDS + " ids at most");
            }
            return new SiteLayout(domains, off, true);
        } catch (JSONException e) {
            throw new IllegalArgumentException("The layout is not JSON of the shape {domains, off}: " + e.getMessage(), e);
        }
    }

    private static List<String> ids(JSONArray array, Set<String> seen) {
        List<String> out = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            out.add(id(array.getString(i), seen));
        }
        return out;
    }

    private static String id(String id, Set<String> seen) {
        if (!RuleFile.ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("The layout names " + id + ", which is not an id");
        }
        if (!seen.add(id)) {
            throw new IllegalArgumentException("The layout names " + id + " twice");
        }
        return id;
    }

    /** The JSON of the layout, as {@link #parse} reads it. */
    public String toJson() {
        JSONArray domainArray = new JSONArray();
        for (DomainPlace domain : domains) {
            JSONArray subdomainArray = new JSONArray();
            for (SubdomainPlace subdomain : domain.subdomains()) {
                subdomainArray.put(new JSONObject().put(ID, subdomain.id()).put(RULES, new JSONArray(subdomain.rules())));
            }
            domainArray.put(new JSONObject().put(ID, domain.id()).put(SUBDOMAINS, subdomainArray));
        }
        return new JSONObject().put(DOMAINS, domainArray).put(OFF, new JSONArray(off)).toString();
    }

    /**
     * The layout of a site, from the edit workspace. A layout that cannot be read counts as none.
     *
     * @param session a session of the edit workspace
     * @param sitePath {@code /sites/<key>}
     * @return the layout, or {@link #NONE}
     * @throws RepositoryException on a repository failure
     */
    static SiteLayout read(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        String folder = sitePath + "/" + FOLDER;
        if (!session.nodeExists(folder)) {
            return NONE;
        }
        JCRNodeWrapper node = session.getNode(folder);
        if (!node.hasProperty(PROPERTY)) {
            return NONE;
        }
        try {
            return parse(node.getProperty(PROPERTY).getString());
        } catch (IllegalArgumentException e) {
            logger.warn("The layout of {} is ignored: {}", sitePath, e.getMessage());
            return NONE;
        }
    }

    /**
     * Writes the layout of a site, or removes it for {@link #NONE}, with the session of the caller. The caller saves
     * the session.
     *
     * @param session  a session of the edit workspace, which may write under {@code contents} of the site
     * @param sitePath {@code /sites/<key>}
     * @param layout   the layout, or {@link #NONE}
     * @throws RepositoryException on a repository failure, or when the caller may not write
     */
    public static void write(JCRSessionWrapper session, String sitePath, SiteLayout layout) throws RepositoryException {
        String path = sitePath + "/" + FOLDER;
        if (!layout.saved()) {
            if (session.nodeExists(path) && session.getNode(path).hasProperty(PROPERTY)) {
                session.getNode(path).getProperty(PROPERTY).remove();
            }
            return;
        }
        JCRNodeWrapper contents = session.getNode(sitePath + "/contents");
        String name = FOLDER.substring(FOLDER.indexOf('/') + 1);
        JCRNodeWrapper folder = contents.hasNode(name) ? contents.getNode(name) : contents.addNode(name, FOLDER_TYPE);
        if (!folder.isNodeType(MIXIN)) {
            folder.addMixin(MIXIN);
        }
        folder.setProperty(PROPERTY, layout.toJson());
    }

    /** The domains that the layout places, in their order. */
    public List<DomainPlace> domains() {
        return domains;
    }

    /** The ids that the site switches off. */
    public Set<String> off() {
        return off;
    }

    /** True when the site saved a layout. */
    public boolean saved() {
        return saved;
    }

    /** True when the layout names the id of a domain, a subdomain or a rule in an array. */
    public boolean places(String id) {
        return domainOrder.containsKey(id) || domainOfSubdomain.containsKey(id) || subdomainOfRule.containsKey(id);
    }

    /**
     * True when the site saved a layout that names the id neither in an array nor in {@code off}: a module shipped
     * the domain, the subdomain or the rule after the save.
     */
    public boolean isNew(String id) {
        return saved && !places(id) && !off.contains(id);
    }

    /** True when the site switches the id off. */
    public boolean isOff(String id) {
        return off.contains(id);
    }

    /** The place of a domain in the layout, or null when the layout does not place it. */
    public Integer domainOrder(String id) {
        return domainOrder.get(id);
    }

    /** The domain where the layout places a subdomain, or null. */
    public String domainOf(String subdomainId) {
        return domainOfSubdomain.get(subdomainId);
    }

    /** The place of a subdomain in the layout, or null when the layout does not place it. */
    public Integer subdomainOrder(String id) {
        return subdomainOrder.get(id);
    }

    /** The subdomain where the layout places a rule, or null. */
    public String subdomainOf(String ruleId) {
        return subdomainOfRule.get(ruleId);
    }

    /** The place of a rule in the layout, or null when the layout does not place it. */
    public Integer ruleOrder(String id) {
        return ruleOrder.get(id);
    }
}

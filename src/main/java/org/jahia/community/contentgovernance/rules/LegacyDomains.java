package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the rule nodes of a snapshot before 2.0.0 when the module starts on the processing server, before the
 * seeder runs, as {@link RuleSeeder#activate} states. Such a node holds its one domain in the property
 * {@value #DOMAIN}, or its subdomains in the property {@value #SUBDOMAINS}. The node type declares neither, so
 * only the node of the repository shows them.
 *
 * <ul>
 *   <li>The node takes one {@link RuleValues#SUBDOMAIN}: the subdomain of the shipped rule of the same id, else
 *       the first of its subdomains, else its domain, and {@value ContentRules#TERMS} for {@code compliance}.</li>
 *   <li>The properties {@value #DOMAIN} and {@value #SUBDOMAINS} go.</li>
 *   <li>A node that nobody changed keeps a fingerprint of nobody changed it.</li>
 * </ul>
 * <p>Example: the node of {@code geo.fetched} holds {@code domain: geo}, and leaves with
 * {@code subdomain: geo.crawlers}. The node of {@code seo.title} holds {@code subdomains: [seo, geo.score]}, and
 * leaves with {@code subdomain: seo}.</p>
 */
final class LegacyDomains {

    /** The property of a rule node of a 1.x snapshot that held its domain. */
    static final String DOMAIN = "domain";
    /** The property of a rule node of a 2.0.0 snapshot that held its subdomains. */
    static final String SUBDOMAINS = "subdomains";
    /**
     * The keys of the fingerprint of a 1.x snapshot: {@value #DOMAIN} third, {@link RuleValues#RUNTIME} last, and no
     * subdomains and no sites. A node that a version without the runtime fingerprinted has no runtime, and an
     * absent key leaves the fingerprint as it was.
     */
    static final List<String> KEYS_1 = List.of(RuleValues.RULE_ID, RuleValues.EVALUATOR, DOMAIN, RuleValues.ENABLED,
            RuleValues.SEVERITY, RuleValues.BLOCKING, RuleValues.FIXER, RuleValues.ADVICE, RuleValues.UNIT,
            RuleValues.MINIMUM, RuleValues.MAXIMUM, RuleValues.KIND, RuleValues.PATTERN, RuleValues.MATCH_MODE,
            RuleValues.REPLACEMENT, RuleValues.RULE_LANGUAGE, RuleValues.WRONG_EXAMPLES, RuleValues.RIGHT_EXAMPLES,
            RuleValues.OWNER, RuleValues.DECIDED_ON, RuleValues.VALID_FROM, RuleValues.VALID_UNTIL,
            RuleValues.PATTERN_FORMS, RuleValues.REPLACEMENT_FORMS, RuleValues.PATTERN_LANGUAGE, RuleValues.TITLE,
            RuleValues.LABEL, RuleValues.RUNTIME);
    /** The keys of the fingerprint of a 2.0.0 snapshot: {@value #SUBDOMAINS} where {@link RuleValues#SUBDOMAIN} is now. */
    static final List<String> KEYS_2 = RuleValues.KEYS.stream()
            .map(key -> RuleValues.SUBDOMAIN.equals(key) ? SUBDOMAINS : key).toList();
    /** The mixin of Jahia that declares any property. */
    static final String UNSTRUCTURED = "jmix:unstructured";
    /** The domain before 2.0.0 that is no subdomain, and its subdomain. */
    private static final Map<String, String> RENAMED = Map.of("compliance", ContentRules.TERMS);
    private static final Logger logger = LoggerFactory.getLogger(LegacyDomains.class);

    private LegacyDomains() {
    }

    /** Converts the rule nodes of the edit and the live workspace. A failure is logged, and the module starts anyway. */
    static void migrate() {
        for (String workspace : List.of("default", "live")) {
            try {
                int converted = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace,
                        Locale.ENGLISH, (JCRCallback<Integer>) session -> TreeSeeder.discardOnFailure(session, () -> migrate(session)));
                if (converted > 0) {
                    logger.info("Gave {} content rules one subdomain in the workspace {}", converted, workspace);
                }
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("The subdomains of the content rules of the workspace {} could not all be converted", workspace, e);
            }
        }
    }

    private static int migrate(JCRSessionWrapper session) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + ContentRules.TYPE
                + "] WHERE [" + DOMAIN + "] IS NOT NULL OR [" + SUBDOMAINS + "] IS NOT NULL", Query.JCR_SQL2);
        List<JCRNodeWrapper> found = new ArrayList<>();
        NodeIterator nodes = query.execute().getNodes();
        while (nodes.hasNext()) {
            found.add((JCRNodeWrapper) nodes.nextNode());
        }
        int converted = 0;
        for (JCRNodeWrapper node : found) {
            Node real = node.getRealNode();
            if (real != null && (real.hasProperty(DOMAIN) || real.hasProperty(SUBDOMAINS))) {
                convert(node, real);
                converted++;
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return converted;
    }

    static void convert(JCRNodeWrapper node, Node real) throws RepositoryException {
        // The repository reads or removes a property that no type of its node declares only through a residual
        // definition, which the mixin gives for the time of the conversion.
        boolean added = !real.isNodeType(UNSTRUCTURED);
        if (added) {
            real.addMixin(UNSTRUCTURED);
        }
        Map<String, Object> values = RuleNodes.read(node);
        String id = String.valueOf(values.get(RuleValues.RULE_ID));
        String seeded = RuleNodes.text(node, RuleNodes.SEED_DIGEST);
        Map<String, Object> before = new HashMap<>(values);
        String subdomain;
        boolean unchanged;
        if (real.hasProperty(SUBDOMAINS)) {
            List<String> subdomains = strings(real.getProperty(SUBDOMAINS).getValues());
            before.put(SUBDOMAINS, subdomains);
            unchanged = seeded != null && seeded.equals(RuleValues.fingerprint(RuleValues.withDefaults(before), KEYS_2));
            subdomain = subdomainOf(id, subdomains.isEmpty() ? null : subdomains.get(0));
        } else {
            String domain = real.getProperty(DOMAIN).getString();
            before.put(DOMAIN, domain);
            unchanged = seeded != null && seeded.equals(legacyDigest(before));
            subdomain = subdomainOf(id, RENAMED.getOrDefault(domain, domain));
        }
        if (subdomain != null && values.putIfAbsent(RuleValues.SUBDOMAIN, subdomain) == null) {
            node.setProperty(RuleValues.SUBDOMAIN, subdomain);
        }
        remove(real, DOMAIN);
        remove(real, SUBDOMAINS);
        if (added) {
            real.removeMixin(UNSTRUCTURED);
        }
        if (unchanged) {
            node.setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(values));
        }
    }

    private static List<String> strings(Value[] values) throws RepositoryException {
        List<String> out = new ArrayList<>();
        for (Value value : values) {
            out.add(value.getString());
        }
        return out;
    }

    /**
     * Removes a property that the node type no longer declares. The repository refuses to remove such a
     * property, so the node holds {@value #UNSTRUCTURED}, whose residual definition declares any property, for
     * the removal only.
     */
    static void remove(Node real, String property) throws RepositoryException {
        if (!real.hasProperty(property)) {
            return;
        }
        boolean added = !real.isNodeType(UNSTRUCTURED);
        if (added) {
            real.addMixin(UNSTRUCTURED);
        }
        real.getProperty(property).remove();
        if (added) {
            real.removeMixin(UNSTRUCTURED);
        }
    }

    /**
     * The subdomain of a rule node of a snapshot before 2.0.0.
     *
     * @param id     the id of the rule
     * @param stored the subdomain that the node held, or its domain
     * @return the subdomain of the shipped rule of the id, else the stored one
     */
    static String subdomainOf(String id, String stored) {
        RuleFile shipped = ShippedRules.own().rules().get(id);
        if (shipped != null && shipped.values().get(RuleValues.SUBDOMAIN) != null) {
            return shipped.values().get(RuleValues.SUBDOMAIN).toString();
        }
        return stored;
    }

    /**
     * The fingerprint that a 1.x snapshot gave the values, which hold {@value #DOMAIN}: the seeder of that version
     * kept it on each node that it wrote.
     */
    static String legacyDigest(Map<String, Object> values) {
        return RuleValues.fingerprint(RuleValues.withDefaults(values), KEYS_1);
    }
}

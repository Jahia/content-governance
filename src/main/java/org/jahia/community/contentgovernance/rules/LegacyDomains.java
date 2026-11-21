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
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the rule nodes of a version before 2.0.0 when the module starts on the processing server, before
 * the seeder runs, as {@link RuleSeeder#activate} states. Such a node holds its one domain in the property
 * {@value #DOMAIN}, which the node type no longer declares, so only the node of the repository shows it.
 *
 * <ul>
 *   <li>The domain goes into {@link RuleValues#SUBDOMAINS}: the subdomains of the shipped rule of the
 *       same id, else the domain itself, and {@value ContentRules#TERMS} for {@code compliance}.</li>
 *   <li>The property {@value #DOMAIN} goes.</li>
 *   <li>A node that nobody changed keeps a fingerprint of nobody changed it, so that the seeder gives it
 *       the new shipped values.</li>
 * </ul>
 * <p>Example: the node of {@code geo.fetched} holds {@code domain: geo}, and leaves with
 * {@code subdomains: [geo.crawlers]}.</p>
 */
final class LegacyDomains {

    /** The property of a rule node before 2.0.0 that held its domain. */
    static final String DOMAIN = "domain";
    /**
     * The keys of the fingerprint before 2.0.0: {@value #DOMAIN} third, {@link RuleValues#RUNTIME} last, and no
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
                        Locale.ENGLISH, (JCRCallback<Integer>) LegacyDomains::migrate);
                if (converted > 0) {
                    logger.info("Moved the domain of {} content rules to their subdomains in the workspace {}",
                            converted, workspace);
                }
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("The domains of the content rules of the workspace {} could not all be converted", workspace, e);
            }
        }
    }

    private static int migrate(JCRSessionWrapper session) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + ContentRules.TYPE
                + "] WHERE [" + DOMAIN + "] IS NOT NULL", Query.JCR_SQL2);
        List<JCRNodeWrapper> found = new ArrayList<>();
        NodeIterator nodes = query.execute().getNodes();
        while (nodes.hasNext()) {
            found.add((JCRNodeWrapper) nodes.nextNode());
        }
        int converted = 0;
        for (JCRNodeWrapper node : found) {
            Node real = node.getRealNode();
            if (real != null && real.hasProperty(DOMAIN)) {
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
        String domain = real.getProperty(DOMAIN).getString();
        Map<String, Object> values = RuleNodes.read(node);
        Map<String, Object> before = new HashMap<>(values);
        before.put(DOMAIN, domain);
        String seeded = RuleNodes.text(node, RuleNodes.SEED_DIGEST);
        boolean unchanged = seeded != null && seeded.equals(legacyDigest(before));
        List<String> subdomains = subdomainsOf(String.valueOf(values.get(RuleValues.RULE_ID)), domain);
        if (values.putIfAbsent(RuleValues.SUBDOMAINS, subdomains) == null) {
            node.setProperty(RuleValues.SUBDOMAINS, subdomains.toArray(String[]::new));
        }
        removeDomain(real);
        if (unchanged) {
            node.setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(values));
        }
    }

    /**
     * Removes {@value #DOMAIN}. The repository refuses to remove a property that no type of its node declares,
     * so the node holds {@value #UNSTRUCTURED}, whose residual definition declares any property, for the
     * removal only.
     */
    static void removeDomain(Node real) throws RepositoryException {
        boolean added = !real.isNodeType(UNSTRUCTURED);
        if (added) {
            real.addMixin(UNSTRUCTURED);
        }
        real.getProperty(DOMAIN).remove();
        if (added) {
            real.removeMixin(UNSTRUCTURED);
        }
    }

    /**
     * The subdomains of a rule node of a version before 2.0.0.
     *
     * @param id     the id of the rule
     * @param domain the domain that the node held
     * @return the subdomains of the shipped rule of the id, else the subdomain of the domain
     */
    static List<String> subdomainsOf(String id, String domain) {
        RuleFile shipped = ShippedRules.own().rules().get(id);
        if (shipped != null) {
            return RuleValues.strings(shipped.values(), RuleValues.SUBDOMAINS);
        }
        return List.of(RENAMED.getOrDefault(domain, domain));
    }

    /**
     * The fingerprint that a version before 2.0.0 gave the values, which hold {@value #DOMAIN}: the seeder of
     * that version kept it on each node that it wrote.
     */
    static String legacyDigest(Map<String, Object> values) {
        return RuleValues.fingerprint(RuleValues.withDefaults(values), KEYS_1);
    }
}

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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gives a check type to the rule nodes of a version before {@value RuleValues#CHECK_TYPE} existed, when the module
 * starts on the processing server, as {@link RuleSeeder#activate} states. Such a node holds the keys of every kind
 * on {@value ContentRules#TYPE}, which no longer declares them, so only the node of the repository shows them.
 *
 * <ul>
 *   <li>The check type comes from the evaluator, as {@link CheckType#ofEvaluator} states.</li>
 *   <li>The node takes the mixin of its check type, which declares its keys again.</li>
 *   <li>A node that nobody changed keeps a fingerprint of nobody changed it, so that the seeder gives it the new
 *       shipped values.</li>
 * </ul>
 * <p>Example: the node of {@code seo.title}, of the evaluator {@code threshold}, takes the check type CODE and the
 * mixin {@code ccsmix:codeCheck}, which shows its unit and its limits again.</p>
 */
final class LegacyCheckTypes {

    private static final Logger logger = LoggerFactory.getLogger(LegacyCheckTypes.class);

    private LegacyCheckTypes() {
    }

    /** Converts the rule nodes of the edit and the live workspace. A failure is logged, and the module starts anyway. */
    static void migrate() {
        for (String workspace : List.of("default", "live")) {
            try {
                int converted = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace,
                        Locale.ENGLISH, (JCRCallback<Integer>) LegacyCheckTypes::migrate);
                if (converted > 0) {
                    logger.info("Gave a check type to {} content rules in the workspace {}", converted, workspace);
                }
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("The content rules of the workspace {} could not all get a check type", workspace, e);
            }
        }
    }

    private static int migrate(JCRSessionWrapper session) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + ContentRules.TYPE
                + "] WHERE [" + RuleValues.CHECK_TYPE + "] IS NULL", Query.JCR_SQL2);
        List<JCRNodeWrapper> found = new ArrayList<>();
        NodeIterator nodes = query.execute().getNodes();
        while (nodes.hasNext()) {
            found.add((JCRNodeWrapper) nodes.nextNode());
        }
        int converted = 0;
        for (JCRNodeWrapper node : found) {
            if (!node.hasProperty(RuleValues.CHECK_TYPE)) {
                convert(node);
                converted++;
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
        return converted;
    }

    static void convert(JCRNodeWrapper node) throws RepositoryException {
        CheckType type = CheckType.ofEvaluator(RuleNodes.text(node, RuleValues.EVALUATOR));
        RuleNodes.mixIn(node, type);
        // The mixin declares the keys of the kind again, so the node reads as its version before.
        Map<String, Object> values = RuleNodes.read(node);
        String seeded = RuleNodes.text(node, RuleNodes.SEED_DIGEST);
        boolean unchanged = seeded != null && seeded.equals(RuleValues.digest(values));
        node.setProperty(RuleValues.CHECK_TYPE, type.name());
        values.put(RuleValues.CHECK_TYPE, type.name());
        if (unchanged) {
            node.setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(values));
        }
        List<String> hidden = hiddenKeys(node.getRealNode(), type);
        if (!hidden.isEmpty()) {
            logger.info("The content rule {} of the check type {} holds {}, which only the repository shows",
                    node.getPath(), type, hidden);
        }
    }

    /** The keys of another kind that the node of the repository holds, which no type of the node declares. */
    static List<String> hiddenKeys(Node real, CheckType type) throws RepositoryException {
        List<String> hidden = new ArrayList<>();
        if (real == null) {
            return hidden;
        }
        for (CheckType other : CheckType.values()) {
            for (String key : other.keys()) {
                if (!CheckType.holds(type, key) && !hidden.contains(key) && real.hasProperty(key)) {
                    hidden.add(key);
                }
            }
        }
        return hidden;
    }
}

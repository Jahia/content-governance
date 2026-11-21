package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts the rules of an older version of the module, once, when the module starts on the processing
 * server, as {@link RuleSeeder#activate} states.
 *
 * <ul>
 *   <li>The folder {@code contents/content-insights/jahia} of the first catalog becomes the folder of
 *       this module, {@code contents/content-insights/content-governance}.</li>
 *   <li>Each {@value #WRITING_RULE} node of the editorial frame becomes a {@value ContentRules#TYPE}
 *       node in place, with the same identifier, in the edit and the live workspace. It keeps every value.
 *       {@link #ruleOf} states how the values map.</li>
 *   <li>A folder that accepted only writing rules accepts only content rules.</li>
 * </ul>
 */
final class LegacyRules {

    static final String WRITING_RULE = "efnt:writingRule";
    static final String WRITING = "writing";
    private static final String LEGACY_FOLDER = ContentRules.ROOT + "/jahia";
    private static final String CONTRIBUTE_TYPES = "j:contributeTypes";
    private static final String JUSTIFICATION = "justification";
    private static final String SEVERITY = "severity";
    private static final String TRANSLATION = "j:translation_";
    private static final String EDIT_WORKSPACE = "default";
    private static final String LIVE_WORKSPACE = "live";
    private static final Logger logger = LoggerFactory.getLogger(LegacyRules.class);

    private LegacyRules() {
    }

    /** Runs every conversion. A failure is logged, and the module starts anyway. */
    static void migrate() {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<Void>) session -> {
                        moveLegacyFolders(session);
                        return null;
                    });
            Map<String, String> ids = new HashMap<>();
            for (String workspace : List.of(EDIT_WORKSPACE, LIVE_WORKSPACE)) {
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null,
                        (JCRCallback<Void>) session -> {
                            convertWritingRules(session, ids);
                            retypeFolders(session);
                            return null;
                        });
            }
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The content rules of an older version could not all be converted", e);
        }
    }

    private static void moveLegacyFolders(JCRSessionWrapper session) throws RepositoryException {
        for (JCRNodeWrapper node : session.getNode("/sites").getNodes()) {
            if (!(node instanceof JCRSiteNode site)) {
                continue;
            }
            String target = ContentRules.ROOT + "/" + SiteFeatures.MODULE;
            if (site.hasNode(LEGACY_FOLDER) && !site.hasNode(target)) {
                session.move(site.getPath() + "/" + LEGACY_FOLDER, site.getPath() + "/" + target);
                logger.info("Moved the content rules of {} to {}", site.getPath(), target);
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
    }

    /**
     * Converts the writing rules of one workspace. {@code ids} maps the identifier of a node to its rule
     * id: the edit workspace fills it, so that a node keeps the same id in the live workspace.
     */
    private static void convertWritingRules(JCRSessionWrapper session, Map<String, String> ids) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + WRITING_RULE + "]", Query.JCR_SQL2);
        List<Node> found = new ArrayList<>();
        NodeIterator nodes = query.execute().getNodes();
        while (nodes.hasNext()) {
            found.add(((JCRNodeWrapper) nodes.nextNode()).getRealNode());
        }
        Map<String, Set<String>> taken = new HashMap<>();
        for (Node node : found) {
            String id = ids.get(node.getIdentifier());
            if (id == null) {
                id = freeId(session, node, taken);
                ids.put(node.getIdentifier(), id);
            }
            convert(node, id);
        }
        if (session.hasPendingChanges()) {
            session.save();
            logger.info("Converted {} writing rules into content rules in the workspace {}", found.size(),
                    session.getWorkspace().getName());
        }
    }

    /** {@code writing.<node name>}, or that id with a part of the identifier when the site has it already. */
    private static String freeId(JCRSessionWrapper session, Node node, Map<String, Set<String>> taken) throws RepositoryException {
        Set<String> ids = taken.computeIfAbsent(sitePathOf(node.getPath()), site -> ruleIds(session, site));
        String id = WRITING + "." + node.getName();
        if (!RuleFile.ID_PATTERN.matcher(id).matches() || ids.contains(id)) {
            id = WRITING + "." + node.getIdentifier().substring(0, 8);
        }
        ids.add(id);
        return id;
    }

    /** The ids of the rules of a site, or none for a node outside a site. */
    private static Set<String> ruleIds(JCRSessionWrapper session, String site) {
        Set<String> ids = new HashSet<>();
        if (site == null) {
            return ids;
        }
        try {
            for (JCRNodeWrapper rule : ContentRules.nodes(session, site)) {
                ids.add(RuleNodes.text(rule, RuleValues.RULE_ID));
            }
        } catch (RepositoryException e) {
            // A conversion that does not know the ids of the site could give one id twice: it stops.
            throw new IllegalStateException("The rule ids of " + site + " could not be read", e);
        }
        return ids;
    }

    private static String sitePathOf(String path) {
        if (!path.startsWith("/sites/")) {
            return null;
        }
        int end = path.indexOf('/', "/sites/".length());
        return end < 0 ? path : path.substring(0, end);
    }

    /**
     * The values of a content rule of the evaluator {@code pattern} for the values of a writing rule. A
     * rule of the kind {@code style} is minor, a {@code blocking} rule is serious and blocking, and a
     * {@code warning} rule is moderate.
     */
    static Map<String, Object> ruleOf(String id, String kind, String severity) {
        boolean blocking = "blocking".equals(severity);
        String rank = blocking ? "serious" : "moderate";
        Map<String, Object> values = new HashMap<>();
        values.put(RuleValues.RULE_ID, id);
        values.put(RuleValues.EVALUATOR, ContentRule.PATTERN);
        values.put(RuleValues.SUBDOMAINS, List.of(WRITING));
        values.put(RuleValues.SEVERITY, "style".equals(kind) ? "minor" : rank);
        values.put(RuleValues.BLOCKING, blocking);
        values.put(RuleValues.ENABLED, true);
        values.put(RuleValues.FIXER, "editor");
        return values;
    }

    /**
     * Changes the type of one node. It works on the node of the repository, because the node of Jahia
     * refuses a change of type. The justification of each language becomes the advice of that language.
     */
    private static void convert(Node node, String id) throws RepositoryException {
        String kind = node.hasProperty(RuleValues.KIND) ? node.getProperty(RuleValues.KIND).getString() : null;
        String severity = node.hasProperty(SEVERITY) ? node.getProperty(SEVERITY).getString() : null;
        String plainJustification = node.hasProperty(JUSTIFICATION) ? node.getProperty(JUSTIFICATION).getString() : null;
        // The old type declares these two with other values, and the new type has no justification.
        for (String name : List.of(SEVERITY, JUSTIFICATION)) {
            if (node.hasProperty(name)) {
                node.getProperty(name).remove();
            }
        }
        node.setPrimaryType(ContentRules.TYPE);
        for (Map.Entry<String, Object> entry : ruleOf(id, kind, severity).entrySet()) {
            if (entry.getValue() instanceof Boolean flag) {
                node.setProperty(entry.getKey(), flag);
            } else {
                node.setProperty(entry.getKey(), entry.getValue().toString());
            }
        }
        moveJustifications(node, plainJustification);
    }

    /**
     * Moves the justification of each translation of a node to its advice. A justification that the
     * node held outside a translation, written by another tool, goes to the first translation with no advice.
     */
    private static void moveJustifications(Node node, String plainJustification) throws RepositoryException {
        NodeIterator translations = node.getNodes(TRANSLATION + "*");
        boolean placed = plainJustification == null;
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(JUSTIFICATION)) {
                Value value = translation.getProperty(JUSTIFICATION).getValue();
                translation.setProperty(RuleValues.ADVICE, value);
                translation.getProperty(JUSTIFICATION).remove();
            } else if (!placed && !translation.hasProperty(RuleValues.ADVICE)) {
                translation.setProperty(RuleValues.ADVICE, plainJustification);
                placed = true;
            }
        }
        if (!placed) {
            logger.warn("The writing rule {} had a justification in no language, which is lost: {}", node.getPath(),
                    plainJustification);
        }
    }

    /** A folder of the frame that accepted only writing rules accepts only content rules. */
    private static void retypeFolders(JCRSessionWrapper session) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [jmix:contributeMode] WHERE [" + CONTRIBUTE_TYPES + "] = '" + WRITING_RULE + "'", Query.JCR_SQL2);
        NodeIterator folders = query.execute().getNodes();
        while (folders.hasNext()) {
            JCRNodeWrapper folder = (JCRNodeWrapper) folders.nextNode();
            List<String> types = new ArrayList<>();
            for (Value value : folder.getProperty(CONTRIBUTE_TYPES).getValues()) {
                String type = value.getString();
                types.add(WRITING_RULE.equals(type) ? ContentRules.TYPE : type);
            }
            folder.setProperty(CONTRIBUTE_TYPES, types.toArray(new String[0]));
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
    }
}

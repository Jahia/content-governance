package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts the glossary terms of an older version into content rules of the evaluator {@code pattern},
 * once, when the module starts. Each term keeps its node: the same identifier, the same place and the
 * same permissions, in the edit and the live workspace.
 *
 * <ul>
 *   <li>A term of a Compliance glossary ({@value #COMPLIANCE_TERM}) becomes the rule
 *       {@code compliance.<node name>} of the language of its glossary. {@link #complianceRule} states
 *       how the values map. Example: the term "click here" of the English glossary becomes a rule of
 *       {@code en} whose pattern is "click here".</li>
 *   <li>A Compliance glossary ({@value #COMPLIANCE_GLOSSARY}) becomes a content folder that takes rules,
 *       with its title and its description.</li>
 *   <li>A term of the glossary of the editorial frame ({@value #FRAME_TERM}) becomes the rule
 *       {@code glossary.<node name>} of the language of its translation. {@link #glossaryRule} states how
 *       the values map. Example: "access token" ({@code en}) to "jeton d'accès" ({@code fr}) becomes a
 *       replace rule of {@code fr}.</li>
 *   <li>A folder that took only glossaries or terms takes content rules.</li>
 * </ul>
 *
 * <p>The old types stay declared, hidden from the create menu, until no site holds one.</p>
 */
final class LegacyTerms {

    static final String COMPLIANCE_TERM = "ccs:glossaryTerm";
    static final String COMPLIANCE_GLOSSARY = "ccs:glossary";
    static final String FRAME_TERM = "efnt:glossaryTerm";
    private static final List<String> LEGACY_TYPES = List.of(COMPLIANCE_GLOSSARY, COMPLIANCE_TERM, FRAME_TERM);
    private static final String TERM = "term";
    private static final String SITES = "/sites/";
    static final String COMPLIANCE_DOMAIN = "compliance";
    static final String GLOSSARY_DOMAIN = "glossary";
    private static final String CONTENT_FOLDER = "jnt:contentFolder";
    private static final String CONTRIBUTE_MODE = "jmix:contributeMode";
    private static final String CONTRIBUTE_TYPES = "j:contributeTypes";
    private static final String DESCRIPTION = "jcr:description";
    private static final String EDIT_WORKSPACE = "default";
    private static final String LIVE_WORKSPACE = "live";
    private static final String FORBIDDEN = "forbidden";
    private static final String REPLACE = "replace";
    private static final String STEM = "*";
    /** The properties of the old types that a content rule does not declare, or declares with other values. */
    private static final List<String> COMPLIANCE_PROPERTIES = List.of(TERM, "matchMode", "reason", "alternative");
    private static final List<String> FRAME_PROPERTIES = List.of(TERM, "termLanguage", "translation",
            "translationLanguage", "note", "termForms", "translationForms", "definition", "status");
    private static final List<String> GLOSSARY_PROPERTIES = List.of("language", "description");
    private static final Logger logger = LoggerFactory.getLogger(LegacyTerms.class);

    /**
     * One term of the glossary of the editorial frame, as the old node holds it.
     *
     * @param term                the term, such as "access token"
     * @param termLanguage        its language, such as {@code en}
     * @param termForms           its other forms, such as "access tokens"
     * @param translation         how the brand writes it in the other language, or a translation that the
     *                            brand never writes
     * @param translationLanguage the other language, such as {@code fr}
     * @param translationForms    the other forms of the translation
     * @param forbidden           true when the brand never writes the translation
     */
    record FrameTerm(String term, String termLanguage, List<String> termForms, String translation,
                     String translationLanguage, List<String> translationForms, boolean forbidden) {

        /** One term, whatever its case, for one language pair. */
        String key() {
            return term.trim().toLowerCase(Locale.ROOT) + '|' + base(termLanguage) + '|' + base(translationLanguage);
        }

        private static String base(String language) {
            return language == null ? "" : language.trim().split("[_-]")[0].toLowerCase(Locale.ROOT);
        }
    }

    /** A node to convert, its rule id, and the advice to write once its type changed. */
    private record Conversion(Node node, String id, Map<String, Object> values, List<String> oldProperties,
                              String advice) {
    }

    private LegacyTerms() {
    }

    /** Runs every conversion, in the edit then the live workspace. A failure is logged, and the module starts anyway. */
    static void migrate() {
        Map<String, String> ids = new HashMap<>();
        for (String workspace : List.of(EDIT_WORKSPACE, LIVE_WORKSPACE)) {
            try {
                Map<String, String> advice = new LinkedHashMap<>();
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null,
                        (JCRCallback<Void>) session -> {
                            convert(session, ids, advice);
                            retypeFolders(session);
                            return null;
                        });
                // The advice is internationalized: the rules read its English value, as the seeder writes it.
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, Locale.ENGLISH,
                        (JCRCallback<Void>) session -> {
                            writeAdvice(session, advice);
                            return null;
                        });
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("The glossary terms of an older version could not all be converted in the workspace {}",
                        workspace, e);
            }
        }
    }

    /**
     * Converts the terms of one workspace. {@code ids} maps the identifier of a node to its rule id: the edit
     * workspace fills it, so that a node keeps the same id in the live workspace.
     */
    private static void convert(JCRSessionWrapper session, Map<String, String> ids, Map<String, String> advice)
            throws RepositoryException {
        Map<String, Set<String>> taken = new HashMap<>();
        List<Conversion> conversions = new ArrayList<>();
        List<Node> glossaries = new ArrayList<>();
        for (Node glossary : realNodes(session, COMPLIANCE_GLOSSARY)) {
            glossaries.add(glossary);
            String language = text(glossary, "language");
            NodeIterator children = glossary.getNodes();
            while (children.hasNext()) {
                Node child = children.nextNode();
                if (child.isNodeType(COMPLIANCE_TERM) && text(child, TERM) != null) {
                    String id = id(session, child, COMPLIANCE_DOMAIN, ids, taken);
                    conversions.add(new Conversion(child, id, complianceRule(id, text(child, TERM),
                            text(child, "matchMode"), text(child, "alternative"), language), COMPLIANCE_PROPERTIES,
                            text(child, "reason")));
                }
            }
        }
        conversions.addAll(frameConversions(session, ids, taken));
        for (Conversion conversion : conversions) {
            convert(conversion);
            if (conversion.advice() != null && !conversion.advice().isBlank()) {
                advice.put(conversion.node().getIdentifier(), conversion.advice());
            }
        }
        for (Node glossary : glossaries) {
            toFolder(glossary);
        }
        if (session.hasPendingChanges()) {
            session.save();
            logger.info("Converted {} glossary terms and {} glossaries into content rules in the workspace {}",
                    conversions.size(), glossaries.size(), session.getWorkspace().getName());
        }
    }

    /** The conversions of the terms of the frame, each forbidden translation with the approved one of its term. */
    private static List<Conversion> frameConversions(JCRSessionWrapper session, Map<String, String> ids,
                                                     Map<String, Set<String>> taken) throws RepositoryException {
        Map<Node, FrameTerm> terms = new LinkedHashMap<>();
        Map<String, FrameTerm> approved = new HashMap<>();
        for (Node node : realNodes(session, FRAME_TERM)) {
            FrameTerm term = frameTerm(node);
            if (term == null) {
                logger.warn("The glossary term {} lacks a term, a translation or a language, so it stays as it is",
                        node.getPath());
                continue;
            }
            terms.put(node, term);
            if (!term.forbidden()) {
                approved.putIfAbsent(sitePathOf(node.getPath()) + '|' + term.key(), term);
            }
        }
        List<Conversion> conversions = new ArrayList<>();
        for (Map.Entry<Node, FrameTerm> entry : terms.entrySet()) {
            Node node = entry.getKey();
            FrameTerm term = entry.getValue();
            String id = id(session, node, GLOSSARY_DOMAIN, ids, taken);
            conversions.add(new Conversion(node, id,
                    glossaryRule(id, term, approved.get(sitePathOf(node.getPath()) + '|' + term.key())),
                    FRAME_PROPERTIES, joined(text(node, "definition"), text(node, "note"))));
        }
        return conversions;
    }

    private static FrameTerm frameTerm(Node node) throws RepositoryException {
        String term = text(node, TERM);
        String termLanguage = text(node, "termLanguage");
        String translation = text(node, "translation");
        String translationLanguage = text(node, "translationLanguage");
        if (blank(term) || blank(termLanguage) || blank(translation) || blank(translationLanguage)) {
            return null;
        }
        return new FrameTerm(term.trim(), termLanguage.trim(), strings(node, "termForms"), translation.trim(),
                translationLanguage.trim(), strings(node, "translationForms"),
                FORBIDDEN.equalsIgnoreCase(String.valueOf(text(node, "status")).trim()));
    }

    /**
     * The values of the rule of a term of a Compliance glossary. A term with an alternative is a replace rule
     * whose replacement is the alternative, and a term with none is a forbidden rule. The match mode maps to
     * the stars of a term: a whole word stays as it is, a prefix ends with {@code *}, and anywhere has a
     * {@code *} on both sides. The rule is serious, as the Compliance section showed its terms.
     *
     * @param id          the rule id
     * @param term        the term, such as "click here"
     * @param matchMode   {@code word}, {@code prefix} or {@code anywhere}; another value is {@code word}
     * @param alternative the suggested wording, or null
     * @param language    the language of the glossary, such as {@code en}, or null
     * @return the values of the rule
     */
    static Map<String, Object> complianceRule(String id, String term, String matchMode, String alternative,
                                              String language) {
        Map<String, Object> values = base(id, COMPLIANCE_DOMAIN, "serious");
        values.put(RuleValues.KIND, blank(alternative) ? FORBIDDEN : REPLACE);
        values.put(RuleValues.PATTERN, patternOf(term.trim(), matchMode));
        putIfPresent(values, RuleValues.REPLACEMENT, alternative);
        putIfPresent(values, RuleValues.RULE_LANGUAGE, language);
        return values;
    }

    private static String patternOf(String term, String matchMode) {
        String mode = matchMode == null ? "" : matchMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "prefix" -> term + STEM;
            case "anywhere" -> STEM + term + STEM;
            default -> term;
        };
    }

    /**
     * The values of the rule of a term of the glossary of the frame. Both are rules of the language of the
     * translation, so that a text in that language is checked.
     * <ul>
     *   <li>An approved translation is a replace rule whose pattern is the term, in the language of the term:
     *       "access token" ({@code en}) to "jeton d'accès" in {@code fr}.</li>
     *   <li>A forbidden translation is a rule whose pattern is that translation. It is a replace rule toward
     *       the approved translation of its term when the glossary has one, and a forbidden rule
     *       otherwise: "jeton d'authentification" to "jeton d'accès" in {@code fr}.</li>
     * </ul>
     * The rule is moderate, as the frame warned about a term.
     *
     * @param id       the rule id
     * @param term     the term of the glossary
     * @param approved the approved translation of the same term and language pair, or null
     * @return the values of the rule
     */
    static Map<String, Object> glossaryRule(String id, FrameTerm term, FrameTerm approved) {
        Map<String, Object> values = base(id, GLOSSARY_DOMAIN, "moderate");
        values.put(RuleValues.RULE_LANGUAGE, term.translationLanguage());
        if (!term.forbidden()) {
            values.put(RuleValues.KIND, REPLACE);
            values.put(RuleValues.PATTERN, term.term());
            putIfPresent(values, RuleValues.PATTERN_FORMS, term.termForms());
            values.put(RuleValues.PATTERN_LANGUAGE, term.termLanguage());
            values.put(RuleValues.REPLACEMENT, term.translation());
            putIfPresent(values, RuleValues.REPLACEMENT_FORMS, term.translationForms());
            return values;
        }
        values.put(RuleValues.PATTERN, term.translation());
        putIfPresent(values, RuleValues.PATTERN_FORMS, term.translationForms());
        if (approved == null) {
            values.put(RuleValues.KIND, FORBIDDEN);
        } else {
            values.put(RuleValues.KIND, REPLACE);
            values.put(RuleValues.REPLACEMENT, approved.translation());
            putIfPresent(values, RuleValues.REPLACEMENT_FORMS, approved.translationForms());
        }
        return values;
    }

    private static Map<String, Object> base(String id, String domain, String severity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(RuleValues.RULE_ID, id);
        values.put(RuleValues.EVALUATOR, ContentRule.PATTERN);
        values.put(RuleValues.DOMAIN, domain);
        values.put(RuleValues.SEVERITY, severity);
        values.put(RuleValues.BLOCKING, false);
        values.put(RuleValues.ENABLED, true);
        values.put(RuleValues.FIXER, "editor");
        values.put(RuleValues.MATCH_MODE, TERM);
        return values;
    }

    /**
     * Changes the type of one node. It works on the node of the repository, because the node of Jahia
     * refuses a change of type. The old properties go first, as the new type does not declare them.
     */
    private static void convert(Conversion conversion) throws RepositoryException {
        Node node = conversion.node();
        for (String name : conversion.oldProperties()) {
            if (node.hasProperty(name)) {
                node.getProperty(name).remove();
            }
        }
        node.setPrimaryType(ContentRules.TYPE);
        for (Map.Entry<String, Object> entry : conversion.values().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean flag) {
                node.setProperty(entry.getKey(), flag);
            } else if (value instanceof List<?> list) {
                node.setProperty(entry.getKey(), list.stream().map(String::valueOf).toArray(String[]::new));
            } else {
                node.setProperty(entry.getKey(), value.toString());
            }
        }
    }

    /** A Compliance glossary becomes a content folder that takes rules, with its title and its description. */
    private static void toFolder(Node glossary) throws RepositoryException {
        String description = text(glossary, "description");
        for (String name : GLOSSARY_PROPERTIES) {
            if (glossary.hasProperty(name)) {
                glossary.getProperty(name).remove();
            }
        }
        glossary.setPrimaryType(CONTENT_FOLDER);
        if (description != null && !description.isBlank() && !glossary.hasProperty(DESCRIPTION)) {
            glossary.setProperty(DESCRIPTION, description);
        }
        if (!glossary.isNodeType(CONTRIBUTE_MODE)) {
            glossary.addMixin(CONTRIBUTE_MODE);
        }
        glossary.setProperty(CONTRIBUTE_TYPES, new String[]{ContentRules.TYPE});
    }

    /** Writes the advice of each converted node that has none, in the locale of the session. */
    private static void writeAdvice(JCRSessionWrapper session, Map<String, String> advice) throws RepositoryException {
        for (Map.Entry<String, String> entry : advice.entrySet()) {
            JCRNodeWrapper node = session.getNodeByIdentifier(entry.getKey());
            if (!node.hasProperty(RuleValues.ADVICE)) {
                node.setProperty(RuleValues.ADVICE, entry.getValue());
            }
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
    }

    /** A folder that took only glossaries or terms takes content rules. */
    private static void retypeFolders(JCRSessionWrapper session) throws RepositoryException {
        String legacy = LEGACY_TYPES.stream().map(type -> "[" + CONTRIBUTE_TYPES + "] = '" + type + "'")
                .collect(Collectors.joining(" OR "));
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + CONTRIBUTE_MODE
                + "] WHERE " + legacy, Query.JCR_SQL2);
        NodeIterator folders = query.execute().getNodes();
        while (folders.hasNext()) {
            JCRNodeWrapper folder = (JCRNodeWrapper) folders.nextNode();
            Set<String> types = new LinkedHashSet<>();
            for (Value value : folder.getProperty(CONTRIBUTE_TYPES).getValues()) {
                String type = value.getString();
                types.add(LEGACY_TYPES.contains(type) ? ContentRules.TYPE : type);
            }
            folder.setProperty(CONTRIBUTE_TYPES, types.toArray(new String[0]));
        }
        if (session.hasPendingChanges()) {
            session.save();
        }
    }

    /** {@code <domain>.<node name>}, or that id with a part of the identifier when the site has it already. */
    private static String id(JCRSessionWrapper session, Node node, String domain, Map<String, String> ids,
                             Map<String, Set<String>> taken) throws RepositoryException {
        String known = ids.get(node.getIdentifier());
        if (known != null) {
            return known;
        }
        Set<String> siteIds = taken.computeIfAbsent(sitePathOf(node.getPath()), site -> ruleIds(session, site));
        String id = domain + "." + node.getName();
        if (!RuleFile.ID_PATTERN.matcher(id).matches() || siteIds.contains(id)) {
            id = domain + "." + node.getIdentifier().substring(0, 8);
        }
        siteIds.add(id);
        ids.put(node.getIdentifier(), id);
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

    private static List<Node> realNodes(JCRSessionWrapper session, String type) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + type + "]", Query.JCR_SQL2);
        List<Node> found = new ArrayList<>();
        NodeIterator nodes = query.execute().getNodes();
        while (nodes.hasNext()) {
            found.add(((JCRNodeWrapper) nodes.nextNode()).getRealNode());
        }
        return found;
    }

    private static String sitePathOf(String path) {
        if (!path.startsWith(SITES)) {
            return null;
        }
        int end = path.indexOf('/', SITES.length());
        return end < 0 ? path : path.substring(0, end);
    }

    /** The value of a single property of the node of the repository, or null. */
    private static String text(Node node, String name) throws RepositoryException {
        if (!node.hasProperty(name)) {
            return null;
        }
        Property property = node.getProperty(name);
        return property.isMultiple() ? null : property.getString();
    }

    private static List<String> strings(Node node, String name) throws RepositoryException {
        List<String> values = new ArrayList<>();
        if (node.hasProperty(name) && node.getProperty(name).isMultiple()) {
            for (Value value : node.getProperty(name).getValues()) {
                if (!value.getString().isBlank()) {
                    values.add(value.getString().trim());
                }
            }
        }
        return values;
    }

    private static String joined(String first, String second) {
        if (blank(first)) {
            return blank(second) ? null : second.trim();
        }
        return blank(second) ? first.trim() : first.trim() + "\n\n" + second.trim();
    }

    private static void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            values.put(key, text.trim());
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            values.put(key, List.copyOf(list));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

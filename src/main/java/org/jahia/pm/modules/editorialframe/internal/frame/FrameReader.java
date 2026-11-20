/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the writing rules and the glossary of a site from a session of the live workspace. The
 * service finds their nodes by their type anywhere under the site, so no folder layout is imposed.
 */
public final class FrameReader {

    private static final Logger logger = LoggerFactory.getLogger(FrameReader.class);
    private static final String SYSTEM_SITE = "systemsite";
    public static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** A language code. It becomes part of a node name, so it never holds a slash or a dot. */
    public static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}([_-][A-Za-z0-9]{2,8})*");
    /** A writing rule is a content rule of the evaluator pattern. */
    static final String RULE = "ccs:contentRule";
    private static final String PATTERN = "pattern";
    static final String TERM = "efnt:glossaryTerm";

    private FrameReader() {
    }

    public static Frame read(JCRSessionWrapper session, String siteKey, String language) throws RepositoryException {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            throw new IllegalArgumentException("Invalid site key");
        }
        if (language != null && !LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("Invalid language");
        }
        String fallback = defaultLanguage(session, siteKey);
        LocalDate today = LocalDate.now();
        List<String> sites = SYSTEM_SITE.equals(siteKey) ? List.of(SYSTEM_SITE) : List.of(SYSTEM_SITE, siteKey);
        Parts parts = new Parts();
        for (String site : sites) {
            String scope = SYSTEM_SITE.equals(site) && !SYSTEM_SITE.equals(siteKey) ? "group" : "site";
            if (session.nodeExists("/sites/" + site)) {
                readRules(session, site, scope, language, fallback, today, parts);
                readTerms(session, site, scope, language, parts);
            }
        }
        String fingerprint = parts.canonical.isEmpty() ? null : Texts.digest(parts.canonical.toString());
        return new Frame(siteKey, language, parts.rules, parts.glossary, fingerprint);
    }

    /** The parts of a frame as they are read, site after site, and the canonical text of their fingerprint. */
    private static final class Parts {
        final List<WritingRule> rules = new ArrayList<>();
        final List<GlossaryTerm> glossary = new ArrayList<>();
        final StringBuilder canonical = new StringBuilder();
    }

    private static void readRules(JCRSessionWrapper session, String site, String scope, String language,
                                  String fallback, LocalDate today, Parts parts) throws RepositoryException {
        for (JCRNodeWrapper node : query(session, RULE, site)) {
            WritingRule rule = rule(node, scope, language, fallback);
            // A rule out of its dates is not part of the frame of the day, for any consumer.
            if (rule != null && rule.inForceOn(today)) {
                parts.rules.add(rule);
                // The content of the rule, not its node: a new import of the same rules keeps the fingerprint.
                parts.canonical.append("rule|").append(scope).append("|").append(rule.kind()).append("|").append(rule.pattern())
                        .append("|").append(rule.matchMode()).append("|").append(rule.replacement()).append("|")
                        .append(rule.language()).append("|").append(rule.severity()).append("|").append(rule.justification())
                        .append("|").append(rule.wrongExamples()).append("|").append(rule.rightExamples())
                        .append("\n");
            }
        }
    }

    private static void readTerms(JCRSessionWrapper session, String site, String scope, String language, Parts parts)
            throws RepositoryException {
        for (JCRNodeWrapper node : query(session, TERM, site)) {
            GlossaryTerm term = term(node, scope);
            if (term != null && (language == null || term.isPair(null, language))) {
                parts.glossary.add(term);
                parts.canonical.append("term|").append(scope).append('|').append(term.term()).append('|')
                        .append(term.termLanguage()).append('|').append(term.translation()).append('|')
                        .append(term.translationLanguage()).append('|').append(term.note()).append('|')
                        .append(term.termForms()).append('|').append(term.translationForms()).append('|')
                        .append(term.definition()).append('|').append(term.status()).append('\n');
            }
        }
    }

    private static GlossaryTerm term(JCRNodeWrapper node, String scope) {
        try {
            String term = node.getPropertyAsString("term");
            String termLanguage = node.getPropertyAsString("termLanguage");
            String translation = node.getPropertyAsString("translation");
            String translationLanguage = node.getPropertyAsString("translationLanguage");
            if (blank(term) || blank(translation) || blank(termLanguage) || blank(translationLanguage)
                    || !LANGUAGE.matcher(termLanguage.trim()).matches() || !LANGUAGE.matcher(translationLanguage.trim()).matches()) {
                return null;
            }
            return new GlossaryTerm(node.getIdentifier(), term.trim(), termLanguage.trim(), forms(node, "termForms"),
                    translation.trim(), translationLanguage.trim(), forms(node, "translationForms"),
                    node.getPropertyAsString("definition"), node.getPropertyAsString("note"),
                    GlossaryTerm.Status.parse(node.getPropertyAsString("status")), scope);
        } catch (RepositoryException | IllegalArgumentException e) {
            logger.warn("The glossary term {} cannot be read: {}", node.getPath(), e.getMessage());
            return null;
        }
    }

    /** The forms of a multiple property, trimmed, with no blank one. */
    private static List<String> forms(JCRNodeWrapper node, String property) throws RepositoryException {
        List<String> forms = new ArrayList<>();
        for (String form : strings(node, property)) {
            if (!blank(form)) {
                forms.add(form.trim());
            }
        }
        return forms;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * The writing rule of a content rule, or {@code null} for a rule that is off, or whose evaluator is
     * not pattern. A blocking rule has the severity blocking, and any other rule the severity warning.
     */
    private static WritingRule rule(JCRNodeWrapper node, String scope, String language, String fallback) {
        try {
            String pattern = node.getPropertyAsString("pattern");
            String evaluator = node.hasProperty("evaluator") ? node.getPropertyAsString("evaluator") : PATTERN;
            WritingRule.Kind kind = WritingRule.Kind.parse(node.getPropertyAsString("kind"));
            boolean off = node.hasProperty("enabled") && !node.getProperty("enabled").getBoolean();
            if (off || !PATTERN.equals(evaluator) || pattern == null || pattern.isBlank()
                    || kind == WritingRule.Kind.GLOSSARY) {
                return null;
            }
            boolean blocking = node.hasProperty("blocking") && node.getProperty("blocking").getBoolean();
            return new WritingRule(node.getPropertyAsString("ruleId"),
                    kind,
                    pattern,
                    WritingRule.MatchMode.parse(node.getPropertyAsString("matchMode")),
                    node.getPropertyAsString("replacement"),
                    node.getPropertyAsString("ruleLanguage"),
                    blocking ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                    i18n(node, "advice", language, fallback),
                    scope,
                    nonBlank(strings(node, "wrongExamples")),
                    nonBlank(strings(node, "rightExamples")),
                    node.getPropertyAsString("owner"),
                    day(node, "decidedOn"),
                    day(node, "validFrom"),
                    day(node, "validUntil"));
        } catch (RepositoryException | IllegalArgumentException e) {
            logger.warn("The writing rule {} cannot be read: {}", node.getPath(), e.getMessage());
            return null;
        }
    }

    /** The nodes of a type under a site, in path order. */
    private static List<JCRNodeWrapper> query(JCRSessionWrapper session, String type, String site) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + type + "] WHERE ISDESCENDANTNODE('/sites/" + site + "')", Query.JCR_SQL2);
        List<JCRNodeWrapper> nodes = new ArrayList<>();
        NodeIterator iterator = query.execute().getNodes();
        while (iterator.hasNext()) {
            JCRNodeWrapper node = (JCRNodeWrapper) iterator.nextNode();
            // A node created in live, such as a contribution of a visitor, never was in the edit
            // workspace, so no workflow approved it: it never reaches the instructions of a model.
            if (node.hasProperty("j:originWS") && "live".equals(node.getProperty("j:originWS").getString())) {
                continue;
            }
            nodes.add(node);
        }
        nodes.sort(Comparator.comparing(JCRNodeWrapper::getPath));
        return nodes;
    }

    /**
     * The value of an internationalized property in the first language that has one: the asked
     * language, then the default language of the site, then any language. The translations are read
     * on the real node, whatever the locale of the session.
     */
    private static String i18n(JCRNodeWrapper node, String property, String language, String fallback) throws RepositoryException {
        Node holder = holder(node, property, language, fallback);
        return holder == null ? null : holder.getProperty(property).getString();
    }

    /** The node that holds the value that {@link #i18n} reads: a translation node, or the real node, or {@code null}. */
    private static Node holder(JCRNodeWrapper node, String property, String language, String fallback) throws RepositoryException {
        Node real = node.getRealNode();
        for (String candidate : new String[]{language, fallback}) {
            if (candidate == null || candidate.isBlank() || !LANGUAGE.matcher(candidate).matches()) {
                continue;
            }
            String child = "j:translation_" + candidate;
            if (real.hasNode(child) && real.getNode(child).hasProperty(property)) {
                return real.getNode(child);
            }
        }
        NodeIterator translations = real.getNodes("j:translation_*");
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(property)) {
                return translation;
            }
        }
        // A property that is not internationalized, for a node written by another tool.
        return real.hasProperty(property) ? real : null;
    }

    static List<String> strings(JCRNodeWrapper node, String property) throws RepositoryException {
        List<String> values = new ArrayList<>();
        if (node.hasProperty(property)) {
            if (node.getProperty(property).isMultiple()) {
                for (Value value : node.getProperty(property).getValues()) {
                    values.add(value.getString());
                }
            } else {
                values.add(node.getProperty(property).getString());
            }
        }
        return values;
    }

    private static List<String> nonBlank(List<String> values) {
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                kept.add(value.trim());
            }
        }
        return kept;
    }

    /**
     * The calendar day of a date property, or {@code null}. jContent stores the day that the editor
     * picked as midnight UTC, and an older value as midnight in the zone of the server: the day in
     * the zone of the stored value is the day that was picked, in both cases.
     */
    static LocalDate day(JCRNodeWrapper node, String property) throws RepositoryException {
        if (!node.hasProperty(property)) {
            return null;
        }
        Calendar calendar = node.getProperty(property).getDate();
        return calendar.toInstant().atZone(calendar.getTimeZone().toZoneId()).toLocalDate();
    }

    /** The default language of a site, or {@code null}. */
    private static String defaultLanguage(JCRSessionWrapper session, String siteKey) {
        try {
            JCRNodeWrapper site = session.getNode("/sites/" + siteKey);
            return site instanceof JCRSiteNode siteNode ? siteNode.getDefaultLanguage() : null;
        } catch (RepositoryException e) {
            return null;
        }
    }
}

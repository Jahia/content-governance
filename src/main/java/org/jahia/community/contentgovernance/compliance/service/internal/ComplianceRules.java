package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.PatternCheck;
import org.jahia.community.contentgovernance.compliance.model.Glossary;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules that the Compliance section applies to the texts of one language of a site: each rule of the
 * evaluator {@code pattern} that the site keeps on, that applies on the day and that one text can break, of
 * every language. The checker keeps the rules of the language, and a glossary rule of another language
 * reports a form of its term. A mandatory mention needs a whole page, so it is left out. Example: the rule
 * {@code compliance.click-here} of the folder of the legal team, and the rule {@code glossary.access-token},
 * which replaces "access token" with "jeton d'accès" in French.
 *
 * <p>The rules are those that {@link ContentRules} reads, with a system session: a rule applies to a text
 * even when the editor cannot read its folder.</p>
 *
 * @param rules   the rules of every language, by id: a glossary rule of another language reports a form
 *                of its term that a text of the language holds
 * @param writing the same rules, as the pattern evaluator checks them
 * @param folders the folders of the rules of the language, in path order
 */
record ComplianceRules(Map<String, ContentRule> rules, List<WritingRule> writing, List<Glossary> folders) {

    /**
     * @param session  the session of the scan, which reads the title of each folder
     * @param siteKey  the key of the site
     * @param language the language of the texts, such as {@code fr}
     * @return the rules that apply
     */
    static ComplianceRules read(JCRSessionWrapper session, String siteKey, String language) {
        return of(ContentRules.forSite(siteKey).all(), language, LocalDate.now(), path -> folder(session, path));
    }

    /** The title and the identifier of a folder, read by the user of the scan. */
    interface FolderReader {
        /**
         * @param path the path of a folder
         * @return the folder with no rule count, its name as title and no identifier when the user cannot read it
         */
        Glossary read(String path);
    }

    /**
     * The rules among {@code all} that apply on the day, of every language. The folders count the rules of the
     * language only.
     */
    static ComplianceRules of(Collection<ContentRule> all, String language, LocalDate day, FolderReader folders) {
        Map<String, ContentRule> rules = new LinkedHashMap<>();
        List<WritingRule> writing = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ContentRule rule : all) {
            // A rule of an unknown domain runs nowhere, as in the check registry
            WritingRule pattern = rule.enabled() && rule.hasKnownDomain() && ContentRule.PATTERN.equals(rule.evaluator())
                    ? PatternCheck.writingRule(rule) : null;
            if (pattern == null || pattern.kind() == WritingRule.Kind.MANDATORY || !pattern.inForceOn(day)) {
                continue;
            }
            rules.put(rule.id(), rule);
            writing.add(pattern);
            if (rule.folder() != null && pattern.appliesTo(language)) {
                counts.merge(rule.folder(), 1, Integer::sum);
            }
        }
        List<Glossary> applied = new ArrayList<>();
        counts.forEach((path, count) -> {
            Glossary folder = folders.read(path);
            applied.add(new Glossary(path, folder.uuid(), folder.title(), count));
        });
        applied.sort(Comparator.comparing(Glossary::path));
        return new ComplianceRules(Map.copyOf(rules), List.copyOf(writing), List.copyOf(applied));
    }

    private static Glossary folder(JCRSessionWrapper session, String path) {
        try {
            if (session.nodeExists(path)) {
                JCRNodeWrapper node = session.getNode(path);
                return new Glossary(path, node.getIdentifier(), node.getDisplayableName(), 0);
            }
        } catch (RepositoryException e) {
            // The user cannot read the folder: the scan names it by its path.
        }
        return new Glossary(path, null, path.substring(path.lastIndexOf('/') + 1), 0);
    }
}

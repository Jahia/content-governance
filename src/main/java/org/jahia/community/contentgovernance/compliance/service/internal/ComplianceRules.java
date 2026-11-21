package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.PatternCheck;
import org.jahia.community.contentgovernance.check.spi.ContentCheck.Scope;
import org.jahia.community.contentgovernance.compliance.model.Glossary;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The rules that the Compliance section applies to the texts of one language of a site: each rule of a
 * domain of Compliance ({@link #DOMAINS}) that the check registry runs on a value, of every check. A rule of
 * the evaluator {@code pattern} must also apply on the day, and one text must be able to break it: a
 * mandatory mention needs a whole page, so it is left out. Example: the rule {@code compliance.click-here} of
 * the folder of the legal team, the rule {@code glossary.access-token}, which replaces "access token" with
 * "jeton d'accès" in French, and the rule {@code acme.erpPrice} of another module, whose check finds a price
 * typed in a text.
 *
 * <p>The rules are those that {@link ContentRules} reads, with a system session: a rule applies to a text
 * even when the editor cannot read its folder.</p>
 *
 * @param site    the rules of the site, which the check registry applies
 * @param ids     the ids of the rules that the scan runs, of every language: a glossary rule of another
 *                language reports a form of its term that a text of the language holds
 * @param applies true when a text of the language can break one of the rules
 * @param folders the folders of the rules of the language, in path order
 */
record ComplianceRules(ContentRules site, Set<String> ids, boolean applies, List<Glossary> folders) {

    /**
     * The domains whose rules show in the Compliance section: the terms of the folders of the teams, the
     * glossary, and the writing rules.
     */
    static final Set<String> DOMAINS = Set.of("compliance", "glossary", "writing");

    /**
     * @param session  the session of the scan, which reads the title of each folder
     * @param checks   the checks that apply the rules
     * @param siteKey  the key of the site
     * @param language the language of the texts, such as {@code fr}
     * @return the rules that apply
     */
    static ComplianceRules read(JCRSessionWrapper session, CheckRegistry checks, String siteKey, String language) {
        return of(ContentRules.forSite(siteKey), rule -> checks.runs(rule, Scope.VALUE), language, LocalDate.now(),
                path -> folder(session, path));
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
     * The rules of Compliance among the rules of the site that apply on the day, of every language. The
     * folders count the rules of the language only.
     *
     * @param runs true for a rule that the check registry applies to a value
     */
    static ComplianceRules of(ContentRules site, Predicate<ContentRule> runs, String language, LocalDate day,
                              FolderReader folders) {
        Set<String> ids = new LinkedHashSet<>();
        List<WritingRule> patterns = new ArrayList<>();
        boolean others = false;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ContentRule rule : site.all()) {
            if (!inScan(rule, runs, day)) {
                continue;
            }
            boolean ofLanguage = true;
            if (ContentRule.PATTERN.equals(rule.evaluator())) {
                WritingRule pattern = PatternCheck.writingRule(rule);
                patterns.add(pattern);
                ofLanguage = pattern.appliesTo(language);
            } else {
                others = true;
            }
            ids.add(rule.id());
            if (rule.folder() != null && ofLanguage) {
                counts.merge(rule.folder(), 1, Integer::sum);
            }
        }
        // The check pattern also reports, in a text of the language, a form of a glossary term of another language
        boolean applies = others || !new RuleChecker.Finder(patterns, language, 1).isEmpty();
        List<Glossary> applied = new ArrayList<>();
        counts.forEach((path, count) -> {
            Glossary folder = folders.read(path);
            applied.add(new Glossary(path, folder.uuid(), folder.title(), count));
        });
        applied.sort(Comparator.comparing(Glossary::path));
        return new ComplianceRules(site, Set.copyOf(ids), applies, List.copyOf(applied));
    }

    /** True when the findings of the rule show in the Compliance section. */
    static boolean inCompliance(ContentRule rule) {
        return rule.domain() != null && DOMAINS.contains(rule.domain());
    }

    /**
     * True when the scan runs the rule: a rule of Compliance that the check registry runs on a value. A rule
     * of the evaluator {@code pattern} must also apply on the day, and must not be a mandatory mention.
     */
    private static boolean inScan(ContentRule rule, Predicate<ContentRule> runs, LocalDate day) {
        if (!inCompliance(rule) || !runs.test(rule)) {
            return false;
        }
        if (!ContentRule.PATTERN.equals(rule.evaluator())) {
            return true;
        }
        WritingRule pattern = PatternCheck.writingRule(rule);
        return pattern != null && pattern.kind() != WritingRule.Kind.MANDATORY && pattern.inForceOn(day);
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

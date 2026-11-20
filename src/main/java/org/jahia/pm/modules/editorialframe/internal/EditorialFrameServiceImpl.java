/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.api.EditorialFrameService;
import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.pm.modules.editorialframe.internal.frame.GlossaryChecker;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads the writing rules and the glossary of a site with a system session of the live workspace. */
@Component(service = EditorialFrameService.class, immediate = true)
public class EditorialFrameServiceImpl implements EditorialFrameService {

    private static final Logger logger = LoggerFactory.getLogger(EditorialFrameServiceImpl.class);
    static final String LIVE = "live";
    private static final String SITES = "/sites/";

    /** The checks of the scope VALUE that modules register. Null in a unit test, which then runs none. */
    @Reference
    private CheckRegistry checks;

    @Override
    public Frame getFrame(String siteKey, String language) {
        Frame frame = live(session -> FrameReader.read(session, siteKey, language));
        return frame == null ? new Frame(siteKey, language, List.of(), List.of(), null) : frame;
    }

    @Override
    public List<RuleViolation> check(String siteKey, String language, String text, boolean document) {
        List<RuleViolation> violations = new ArrayList<>(RuleChecker.check(getFrame(siteKey, language).rules(), language,
                text, document));
        violations.addAll(registeredChecks(siteKey, language, text));
        return violations;
    }

    /**
     * The findings of the registered checks of the scope VALUE, as violations. The frame checks the rules
     * of the evaluator pattern itself.
     */
    private List<RuleViolation> registeredChecks(String siteKey, String language, String text) {
        if (checks == null || siteKey == null || text == null) {
            return List.of();
        }
        return checks.run(CheckInput.value(SITES + siteKey, language, text), Set.of(ContentRule.PATTERN)).stream()
                .map(checked -> violation(checked.rule(), checked.finding().text(), checked.finding().context()))
                .toList();
    }

    static RuleViolation violation(ContentRule rule, String text, String context) {
        WritingRule.Kind kind;
        try {
            kind = WritingRule.Kind.parse(rule.value(RuleValues.KIND) instanceof String name ? name : null);
        } catch (IllegalArgumentException e) {
            kind = WritingRule.Kind.FORBIDDEN;
        }
        Object replacement = rule.value(RuleValues.REPLACEMENT);
        return new RuleViolation(rule.id(), kind, rule.blocking() ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                null, text, replacement == null ? null : replacement.toString(), rule.advice(), context, rule.folder());
    }

    @Override
    public List<GlossaryTerm> glossary(String siteKey, String termLanguage, String translationLanguage) {
        if (translationLanguage == null) {
            return List.of();
        }
        return getFrame(siteKey, translationLanguage).glossary().stream()
                .filter(term -> term.isPair(termLanguage, translationLanguage))
                .toList();
    }

    @Override
    public List<RuleViolation> checkTranslation(String siteKey, String sourceLanguage, String sourceText, String language,
                                                String text, boolean document) {
        Frame frame = getFrame(siteKey, language);
        List<RuleViolation> violations = new ArrayList<>(RuleChecker.check(frame.rules(), language, text, document));
        violations.addAll(registeredChecks(siteKey, language, text));
        // A rendering that a rule already proposes for a wrong term of the translation is not missing too.
        Set<String> proposed = new HashSet<>();
        violations.stream().map(RuleViolation::replacement).filter(Objects::nonNull)
                .forEach(replacement -> proposed.add(replacement.toLowerCase(Locale.ROOT)));
        Map<String, String> folders = new HashMap<>();
        frame.rules().forEach(rule -> folders.put(rule.id(), rule.folder()));
        GlossaryChecker.check(frame.glossary(), sourceLanguage, sourceText, language, text).stream()
                .filter(violation -> violation.match() != null || violation.replacement() == null
                        || !proposed.contains(violation.replacement().toLowerCase(Locale.ROOT)))
                .map(violation -> inFolder(violation, folders.get(violation.ruleId())))
                .forEach(violations::add);
        return violations;
    }

    /** The violation of a glossary term, in the folder of the rule that holds the term. */
    private static RuleViolation inFolder(RuleViolation violation, String folder) {
        return folder == null ? violation : new RuleViolation(violation.ruleId(), violation.kind(), violation.severity(),
                violation.pattern(), violation.match(), violation.replacement(), violation.justification(),
                violation.excerpt(), folder);
    }

    private static <T> T live(JCRCallback<T> callback) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE, null, callback);
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The rules of a site could not be read: {}", e.getMessage());
            logger.debug("The rules of a site could not be read", e);
            return null;
        }
    }
}

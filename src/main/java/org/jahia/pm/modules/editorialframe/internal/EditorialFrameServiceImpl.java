/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.Finding;
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

/**
 * Reads the writing rules and the glossary of a site with a system session of the live workspace. A check
 * of a text runs the rules of the site through the {@link CheckRegistry}, as the Compliance scan does, and
 * adds the mandatory mentions of the frame for a whole document.
 */
@Component(service = EditorialFrameService.class, immediate = true)
public class EditorialFrameServiceImpl implements EditorialFrameService {

    private static final Logger logger = LoggerFactory.getLogger(EditorialFrameServiceImpl.class);
    static final String LIVE = "live";
    private static final String SITES = "/sites/";

    /** The checks of the scope VALUE that modules register. Null in a unit test that runs none. */
    @Reference
    private CheckRegistry checks;

    public EditorialFrameServiceImpl() {
        // Used by OSGi.
    }

    /** @param checks the checks of the scope VALUE, which a test of another package binds */
    public EditorialFrameServiceImpl(CheckRegistry checks) {
        this.checks = checks;
    }

    @Override
    public Frame getFrame(String siteKey, String language) {
        Frame frame = live(session -> FrameReader.read(session, siteKey, language));
        return frame == null ? new Frame(siteKey, language, List.of(), List.of(), null) : frame;
    }

    @Override
    public List<RuleViolation> check(String siteKey, String language, String text, boolean document) {
        List<RuleViolation> violations = new ArrayList<>(registeredChecks(siteKey, language, text));
        if (document) {
            violations.addAll(mandatory(getFrame(siteKey, language), language, text));
        }
        return violations;
    }

    /**
     * The findings of the registered checks of the scope VALUE, the rules of the evaluator pattern included,
     * as violations: the Compliance scan finds the same in the same text.
     */
    private List<RuleViolation> registeredChecks(String siteKey, String language, String text) {
        if (checks == null || siteKey == null || text == null) {
            return List.of();
        }
        return checks.run(CheckInput.value(SITES + siteKey, language, text), Set.of()).stream()
                .map(checked -> violation(checked.rule(), checked.finding(), language))
                .toList();
    }

    /** The mandatory mentions of the frame that a whole document lacks. */
    private static List<RuleViolation> mandatory(Frame frame, String language, String text) {
        List<WritingRule> mandatory = frame.rules().stream().filter(rule -> rule.kind() == WritingRule.Kind.MANDATORY).toList();
        return mandatory.isEmpty() ? List.of() : RuleChecker.check(mandatory, language, text, true);
    }

    /**
     * @param rule     the rule as the site applies it
     * @param finding  what its check found: the term and the replacement of a term rule are values of the finding
     * @param language the language of the text, which the advice is in when the rule has it
     * @return the violation
     */
    static RuleViolation violation(ContentRule rule, Finding finding, String language) {
        WritingRule.Kind kind;
        try {
            kind = WritingRule.Kind.parse(rule.value(RuleValues.KIND) instanceof String name ? name : null);
        } catch (IllegalArgumentException e) {
            kind = WritingRule.Kind.FORBIDDEN;
        }
        Object term = finding.values().get(Finding.TERM);
        Object replacement = finding.values().getOrDefault(Finding.REPLACEMENT, rule.value(RuleValues.REPLACEMENT));
        return new RuleViolation(rule.id(), kind, rule.blocking() ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                term == null ? null : term.toString(), finding.text(), replacement == null ? null : replacement.toString(),
                rule.text(RuleValues.ADVICE, language), finding.context(), rule.folder());
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
        List<RuleViolation> violations = new ArrayList<>(registeredChecks(siteKey, language, text));
        if (document) {
            violations.addAll(mandatory(frame, language, text));
        }
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

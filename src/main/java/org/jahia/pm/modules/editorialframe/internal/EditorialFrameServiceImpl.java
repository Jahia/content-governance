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
import org.jahia.pm.modules.editorialframe.api.FrameContext;
import org.jahia.pm.modules.editorialframe.api.FrameRequest;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.SkillContent;
import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.ContextBuilder;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.pm.modules.editorialframe.internal.frame.GlossaryChecker;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.jahia.pm.modules.editorialframe.internal.skills.BundleSkills;
import org.jahia.pm.modules.editorialframe.internal.skills.GeneratedFrameSkill;
import org.jahia.pm.modules.editorialframe.internal.skills.JcrSkills;
import org.jahia.pm.modules.editorialframe.internal.skills.LoadedSkill;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillPaths;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads the frame and the skills of a site with a system session of the live workspace. */
@Component(service = EditorialFrameService.class, immediate = true)
public class EditorialFrameServiceImpl implements EditorialFrameService {

    private static final Logger logger = LoggerFactory.getLogger(EditorialFrameServiceImpl.class);
    static final String LIVE = "live";
    private static final String SITES = "/sites/";
    static final int MAX_VOICE_CHARS = 6000;

    private final BundleSkills bundleSkills = new BundleSkills();

    /** The checks of the scope VALUE that modules register. Null in a unit test, which then runs none. */
    @Reference
    private CheckRegistry checks;

    @Activate
    void activate(BundleContext context) {
        bundleSkills.open(context);
    }

    @Deactivate
    void deactivate() {
        bundleSkills.close();
    }

    @Override
    public Frame getFrame(String siteKey, String language) {
        Frame frame = live(session -> FrameReader.read(session, siteKey, language));
        return frame == null ? new Frame(siteKey, language, List.of(), List.of(), List.of(), null) : frame;
    }

    @Override
    public FrameContext buildContext(FrameRequest request) {
        return ContextBuilder.build(getFrame(request.siteKey(), request.language()), request);
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
                null, text, replacement == null ? null : replacement.toString(), rule.advice(), context);
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
        violations.addAll(GlossaryChecker.check(frame.glossary(), sourceLanguage, sourceText, language, text));
        return violations;
    }

    @Override
    public Optional<String> authorVoice(String siteKey, String language, String username, String contentPath) {
        if (username == null || contentPath == null) {
            return Optional.empty();
        }
        if (!allowsAuthorVoice(getFrame(siteKey, language), contentPath)) {
            return Optional.empty();
        }
        // A user of the site comes before a global user of the same name.
        JCRUserNode user = JahiaUserManagerService.getInstance().lookupUser(username, siteKey);
        if (user == null) {
            return Optional.empty();
        }
        try {
            return voiceOf(siteKey, user);
        } catch (RepositoryException e) {
            logger.warn("The voice of {} cannot be read: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    /** True when a voice of the frame allows the voice of an editor at the path of a content. */
    static boolean allowsAuthorVoice(Frame frame, String contentPath) {
        boolean allowed = false;
        for (FrameText voice : frame.voices()) {
            for (String prefix : voice.authorVoiceUnder()) {
                allowed |= isUnder(contentPath, prefix);
            }
        }
        return allowed;
    }

    /** True for the path itself and the paths under it. A blank path holds no content. */
    private static boolean isUnder(String contentPath, String prefix) {
        String trimmed = prefix.trim();
        return !trimmed.isEmpty() && (contentPath.equals(trimmed)
                || contentPath.startsWith(trimmed.endsWith("/") ? trimmed : trimmed + "/"));
    }

    /** The voice of the user node, then the body of its voice skill, cut at {@link #MAX_VOICE_CHARS}. */
    private Optional<String> voiceOf(String siteKey, JCRUserNode user) throws RepositoryException {
        String voice = user.hasProperty("voice") ? user.getProperty("voice").getString() : null;
        String skill = user.hasProperty("voiceSkill") ? user.getProperty("voiceSkill").getString() : null;
        StringBuilder text = new StringBuilder();
        if (voice != null && !voice.isBlank()) {
            text.append(voice.trim());
        }
        if (skill != null && !skill.isBlank()) {
            readSkill(siteKey, skill.trim()).ifPresent(content -> text.append(text.isEmpty() ? "" : "\n\n")
                    .append(content.body()));
        }
        if (text.length() > MAX_VOICE_CHARS) {
            // A point-of-view skill can be long: the voice of an editor stays within about 1,500 tokens.
            text.setLength(MAX_VOICE_CHARS);
            text.append("\n[...]");
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
    }

    @Override
    public List<SkillSummary> listSkills(String siteKey, String consumer) {
        return skills(siteKey).values().stream()
                .map(skill -> skill.summary)
                .filter(summary -> consumer == null || summary.consumers().contains(consumer))
                .toList();
    }

    @Override
    public List<SkillContent> listModuleSkills(String consumer) {
        Map<String, LoadedSkill> byName = new LinkedHashMap<>();
        for (LoadedSkill skill : bundleSkills.all()) {
            byName.putIfAbsent(skill.summary.name(), skill);
        }
        return byName.values().stream()
                .filter(skill -> consumer == null || skill.summary.consumers().contains(consumer))
                .map(LoadedSkill::content)
                .toList();
    }

    @Override
    public Optional<SkillContent> readSkill(String siteKey, String name) {
        LoadedSkill skill = name == null ? null : skills(siteKey).get(name.trim());
        return Optional.ofNullable(skill).map(LoadedSkill::content);
    }

    @Override
    public Optional<String> readSkillFile(String siteKey, String name, String relativePath) {
        if (!SkillPaths.isSafeRelative(relativePath)) {
            return Optional.empty();
        }
        LoadedSkill skill = name == null ? null : skills(siteKey).get(name.trim());
        if (skill == null || !skill.files.containsKey(relativePath)) {
            return Optional.empty();
        }
        return Optional.of(skill.files.get(relativePath));
    }

    /**
     * The skills a site sees, by name. On a name clash, the skill of the site hides the skill of
     * the group, which hides the skill of a module. The summary of the winner names what it hides.
     */
    Map<String, LoadedSkill> skills(String siteKey) {
        Map<String, LoadedSkill> skills = live(session -> {
            if (siteKey == null || !FrameReader.SITE_KEY.matcher(siteKey).matches() || !session.nodeExists(SITES + siteKey)) {
                return new LinkedHashMap<String, LoadedSkill>();
            }
            return byName(layers(session, siteKey));
        });
        return skills == null ? Map.of() : skills;
    }

    /** The skills of the site, of the group, of the modules of the site and of its frame, in this order. */
    private List<List<LoadedSkill>> layers(JCRSessionWrapper session, String siteKey) throws RepositoryException {
        List<List<LoadedSkill>> layers = new ArrayList<>();
        layers.add(JcrSkills.read(session, siteKey, "site"));
        if (!FrameReader.SYSTEM_SITE.equals(siteKey)) {
            layers.add(JcrSkills.read(session, FrameReader.SYSTEM_SITE, "group"));
        }
        JCRNodeWrapper site = session.getNode(SITES + siteKey);
        List<String> modules = site instanceof JCRSiteNode siteNode ? siteNode.getInstalledModules() : List.of();
        layers.add(bundleSkills.forModules(modules));
        String language = FrameReader.defaultLanguage(session, siteKey);
        LoadedSkill generated = GeneratedFrameSkill.of(FrameReader.read(session, siteKey, language));
        layers.add(generated == null ? List.of() : List.of(generated));
        return layers;
    }

    /** The skills by name: a skill of a layer hides the skills of the same name in the next layers, which its summary names. */
    private static Map<String, LoadedSkill> byName(List<List<LoadedSkill>> layers) {
        Map<String, LoadedSkill> byName = new LinkedHashMap<>();
        Map<String, List<String>> hidden = new LinkedHashMap<>();
        for (List<LoadedSkill> layer : layers) {
            for (LoadedSkill skill : layer) {
                String name = skill.summary.name();
                if (byName.containsKey(name)) {
                    hidden.computeIfAbsent(name, key -> new ArrayList<>()).add(skill.summary.scope());
                } else {
                    byName.put(name, skill);
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : hidden.entrySet()) {
            LoadedSkill winner = byName.get(entry.getKey());
            byName.put(entry.getKey(), winner.withSummary(winner.summary.withOverrides(entry.getValue())));
        }
        return byName;
    }

    private static <T> T live(JCRCallback<T> callback) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE, null, callback);
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The editorial frame could not be read: {}", e.getMessage());
            logger.debug("The editorial frame could not be read", e);
            return null;
        }
    }
}

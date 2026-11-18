/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.List;
import java.util.Optional;

/**
 * The editorial frame of a site and its skills, as the AI features of Jahia read them.
 *
 * <p>Every read goes to the live workspace: a consumer never reads a frame or a skill that is not
 * published. The frame of {@code systemsite} is the frame of the group, and every site reads it
 * before its own.</p>
 *
 * <p>This service is the only skill registry of Jahia. A skill is a {@code SKILL.md} in the Agent
 * Skills format: a module ships it in the folder that its manifest header {@code Jahia-Skills}
 * names, and a site or the group holds it as an {@code efnt:skill} node. The summary of a skill
 * carries what the assistant needs to route a message and to run it: the label, the coverage and
 * the tools. The body of the skill is the guidance of the model.</p>
 */
public interface EditorialFrameService {

    /** The frame of a site in one language: voices, personas and rules. */
    Frame getFrame(String siteKey, String language);

    /** The text of the frame for a model, within the token budget of the request. */
    FrameContext buildContext(FrameRequest request);

    /**
     * The rules that a text breaks. A mandatory rule is checked only when {@code document} is true,
     * because a single field never holds a whole page.
     */
    List<RuleViolation> check(String siteKey, String language, String text, boolean document);

    /**
     * The glossary of a site for a language pair, such as from {@code en} to {@code fr}, the group
     * first. A {@code null} source language gives the terms from every language.
     */
    List<GlossaryTerm> glossary(String siteKey, String termLanguage, String translationLanguage);

    /**
     * The rules that a translation breaks, as {@link #check} finds them, then each glossary term
     * that the source holds and whose rendering the translation lacks, of kind
     * {@link WritingRule.Kind#GLOSSARY}: the pattern is the term, and the replacement its rendering.
     */
    List<RuleViolation> checkTranslation(String siteKey, String sourceLanguage, String sourceText, String language,
                                         String text, boolean document);

    /**
     * The voice of an editor, when the brand allows a personal voice under the path of the content.
     * Empty when the brand allows none there, or when the editor has none.
     */
    Optional<String> authorVoice(String siteKey, String language, String username, String contentPath);

    /** The skills that a consumer, such as {@code chat} or {@code mcp}, may use on a site. */
    List<SkillSummary> listSkills(String siteKey, String consumer);

    /**
     * The skills that the active modules ship, whatever the site, with their bodies: the skills
     * that a module declares for every site, such as the built-in skills of the assistant. On a
     * name clash, the module that started first keeps the name. A {@code null} consumer gives
     * every skill.
     */
    List<SkillContent> listModuleSkills(String consumer);

    /** One skill, by its name, as the site sees it after the overrides. */
    Optional<SkillContent> readSkill(String siteKey, String name);

    /** One file of a skill, by its path relative to the folder of the skill. */
    Optional<String> readSkillFile(String siteKey, String name, String relativePath);
}

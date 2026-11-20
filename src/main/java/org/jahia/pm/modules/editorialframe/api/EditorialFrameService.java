/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.List;

/**
 * The writing rules and the glossary of a site, and their checks, as the other modules read them.
 *
 * <p>Every read goes to the live workspace: a consumer never reads a rule or a term that is not
 * published. The rules and the terms of {@code systemsite} are those of the group, and every site
 * reads them before its own.</p>
 */
public interface EditorialFrameService {

    /** The rules and the glossary of a site in one language. */
    Frame getFrame(String siteKey, String language);

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
}

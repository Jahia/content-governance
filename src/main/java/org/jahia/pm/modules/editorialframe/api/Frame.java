/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.List;

/**
 * The writing rules and the glossary of a site in one language. The items of the group come first.
 * The fingerprint is the first 12 hexadecimal characters of the SHA-256 of the frame, which changes
 * with any published change of a rule or a glossary term. The glossary holds the terms whose
 * translation is in the language of the frame, from every other language.
 *
 * @param glossary the glossary terms whose translation is in the language of the frame
 */
public record Frame(String siteKey, String language, List<WritingRule> rules, List<GlossaryTerm> glossary,
                    String fingerprint) {

    public Frame {
        rules = rules == null ? List.of() : List.copyOf(rules);
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    public boolean isEmpty() {
        return rules.isEmpty() && glossary.isEmpty();
    }
}

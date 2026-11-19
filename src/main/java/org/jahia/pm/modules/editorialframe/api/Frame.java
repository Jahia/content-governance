/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;


import java.util.List;
import java.util.Optional;


/**
 * The frame of a site in one language. The items of the group come first. The fingerprint is the
 * first 12 hexadecimal characters of the SHA-256 of the frame, which changes with any published
 * change of a voice, a persona, a rule or a glossary term. The glossary holds the terms whose
 * translation is in the language of the frame, from every other language.
 *
 * @param glossary the glossary terms whose translation is in the language of the frame
 */
public record Frame(String siteKey, String language, List<FrameText> voices, List<Persona> personas,
                    List<WritingRule> rules, List<GlossaryTerm> glossary, String fingerprint) {

    public Frame {
        voices = voices == null ? List.of() : List.copyOf(voices);
        personas = personas == null ? List.of() : List.copyOf(personas);
        rules = rules == null ? List.of() : List.copyOf(rules);
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    public Frame(String siteKey, String language, List<FrameText> voices, List<Persona> personas, List<WritingRule> rules, String fingerprint) {
        this(siteKey, language, voices, personas, rules, List.of(), fingerprint);
    }

    public boolean isEmpty() {
        return voices.isEmpty() && personas.isEmpty() && rules.isEmpty() && glossary.isEmpty();
    }

    /** The persona of this name or title, ignoring case, or the default persona when the name is empty. */
    public Optional<Persona> persona(String name) {
        if (name == null || name.isBlank()) {
            return personas.stream().filter(Persona::isDefault).findFirst();
        }
        String wanted = name.trim();
        return personas.stream()
                .filter(p -> p.name().equalsIgnoreCase(wanted) || p.title().equalsIgnoreCase(wanted))
                .findFirst();
    }
}

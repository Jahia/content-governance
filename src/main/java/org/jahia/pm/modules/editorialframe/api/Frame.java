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
 */
public final class Frame {

    private final String siteKey;
    private final String language;
    private final List<FrameText> voices;
    private final List<Persona> personas;
    private final List<WritingRule> rules;
    private final List<GlossaryTerm> glossary;
    private final String fingerprint;

    public Frame(String siteKey, String language, List<FrameText> voices, List<Persona> personas, List<WritingRule> rules, String fingerprint) {
        this(siteKey, language, voices, personas, rules, List.of(), fingerprint);
    }

    public Frame(String siteKey, String language, List<FrameText> voices, List<Persona> personas, List<WritingRule> rules,
                 List<GlossaryTerm> glossary, String fingerprint) {
        this.siteKey = siteKey;
        this.language = language;
        this.voices = voices == null ? List.of() : List.copyOf(voices);
        this.personas = personas == null ? List.of() : List.copyOf(personas);
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.glossary = glossary == null ? List.of() : List.copyOf(glossary);
        this.fingerprint = fingerprint;
    }

    public String siteKey() {
        return siteKey;
    }

    public String language() {
        return language;
    }

    public List<FrameText> voices() {
        return voices;
    }

    public List<Persona> personas() {
        return personas;
    }

    public List<WritingRule> rules() {
        return rules;
    }

    /** The glossary terms whose translation is in the language of the frame. */
    public List<GlossaryTerm> glossary() {
        return glossary;
    }

    public String fingerprint() {
        return fingerprint;
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

    @Override
    public String toString() {
        return "Frame[" + "siteKey=" + siteKey + ", " + "language=" + language + ", " + "voices=" + voices + ", " + "personas=" + personas + ", " + "rules=" + rules + ", " + "glossary=" + glossary + ", " + "fingerprint=" + fingerprint + "]";
    }
}

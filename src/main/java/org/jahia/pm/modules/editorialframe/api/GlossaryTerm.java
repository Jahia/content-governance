/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A term of the glossary of the brand, and how to write it in another language: one language pair.
 * A term that the brand never translates, such as a product name, has itself as its translation.
 *
 * <p>For example, the term "access token" in {@code en} has the translation "jeton d'accès" in
 * {@code fr}. A translation of a text that holds "access token" writes "jeton d'accès". The scope
 * is {@code group} or {@code site}.</p>
 *
 * <p>The forms are the other ways to write the term or the translation, such as the plural
 * "jetons d'accès". A forbidden term names a translation that the brand never writes, such as
 * "jeton d'authentification" for "access token".</p>
 */
public final class GlossaryTerm {

    /** Whether the brand writes the translation, or never writes it. */
    public enum Status {
        APPROVED,
        FORBIDDEN;

        /** {@code null} or blank is {@link #APPROVED}. */
        public static Status parse(String value) {
            return value == null || value.isBlank() ? APPROVED : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private final String id;
    private final String term;
    private final String termLanguage;
    private final List<String> termForms;
    private final String translation;
    private final String translationLanguage;
    private final List<String> translationForms;
    private final String definition;
    private final String note;
    private final Status status;
    private final String scope;

    /** An approved term with no other form and no definition. */
    public GlossaryTerm(String id, String term, String termLanguage, String translation, String translationLanguage,
                        String note, String scope) {
        this(id, term, termLanguage, null, translation, translationLanguage, null, null, note, Status.APPROVED, scope);
    }

    public GlossaryTerm(String id, String term, String termLanguage, List<String> termForms, String translation,
                        String translationLanguage, List<String> translationForms, String definition, String note,
                        Status status, String scope) {
        this.id = id;
        this.term = term;
        this.termLanguage = termLanguage;
        this.termForms = termForms == null ? List.of() : List.copyOf(termForms);
        this.translation = translation;
        this.translationLanguage = translationLanguage;
        this.translationForms = translationForms == null ? List.of() : List.copyOf(translationForms);
        this.definition = definition;
        this.note = note;
        this.status = status == null ? Status.APPROVED : status;
        this.scope = scope;
    }

    public String id() {
        return id;
    }

    /** The term, such as "access token". */
    public String term() {
        return term;
    }

    /** The language of the term, such as {@code en}. */
    public String termLanguage() {
        return termLanguage;
    }

    /** The other forms of the term, such as "access tokens". Never {@code null}. */
    public List<String> termForms() {
        return termForms;
    }

    /** The term, then its other forms. */
    public List<String> allTermForms() {
        return all(term, termForms);
    }

    /** How the brand writes the term in the other language, such as "jeton d'accès". */
    public String translation() {
        return translation;
    }

    /** The language of the translation, such as {@code fr}. */
    public String translationLanguage() {
        return translationLanguage;
    }

    /** The other forms of the translation, such as "jetons d'accès". Never {@code null}. */
    public List<String> translationForms() {
        return translationForms;
    }

    /** The translation, then its other forms. */
    public List<String> allTranslationForms() {
        return all(translation, translationForms);
    }

    /** What the term means, or {@code null}. */
    public String definition() {
        return definition;
    }

    /** When or why the term applies, or {@code null}. */
    public String note() {
        return note;
    }

    public Status status() {
        return status;
    }

    /** True when the brand never writes this translation of the term. */
    public boolean forbidden() {
        return status == Status.FORBIDDEN;
    }

    public String scope() {
        return scope;
    }

    /** True when the brand writes the term as it is in the other language. */
    public boolean kept() {
        return term.equals(translation);
    }

    /** True for the pair from one language to another. A {@code null} language matches any. */
    public boolean isPair(String from, String to) {
        return (from == null || sameLanguage(from, termLanguage)) && (to == null || sameLanguage(to, translationLanguage));
    }

    /** {@code fr} and {@code fr_FR} are the same language for a glossary. */
    static boolean sameLanguage(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return base(a).equals(base(b));
    }

    private static String base(String language) {
        return language.split("[_-]")[0].toLowerCase(Locale.ROOT);
    }

    private static List<String> all(String first, List<String> others) {
        List<String> all = new ArrayList<>(others.size() + 1);
        all.add(first);
        all.addAll(others);
        return all;
    }

    @Override
    public String toString() {
        return "GlossaryTerm[id=" + id + ", term=" + term + ", termLanguage=" + termLanguage + ", termForms=" + termForms
                + ", translation=" + translation + ", translationLanguage=" + translationLanguage + ", translationForms="
                + translationForms + ", definition=" + definition + ", note=" + note + ", status=" + status
                + ", scope=" + scope + "]";
    }
}

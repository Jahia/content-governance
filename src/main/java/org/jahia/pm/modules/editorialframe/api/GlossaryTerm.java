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
 *
 * @param term the term, such as "access token"
 * @param termLanguage the language of the term, such as {@code en}
 * @param termForms the other forms of the term, such as "access tokens". Never {@code null}
 * @param translation how the brand writes the term in the other language, such as "jeton d'accès"
 * @param translationLanguage the language of the translation, such as {@code fr}
 * @param translationForms the other forms of the translation, such as "jetons d'accès". Never {@code null}
 * @param definition what the term means, or {@code null}
 * @param note when or why the term applies, or {@code null}
 * @param status {@link Status#APPROVED} when none is given
 */
public record GlossaryTerm(String id, String term, String termLanguage, List<String> termForms, String translation,
                           String translationLanguage, List<String> translationForms, String definition, String note,
                           Status status, String scope) {

    /** Whether the brand writes the translation, or never writes it. */
    public enum Status {
        APPROVED,
        FORBIDDEN;

        /** {@code null} or blank is {@link #APPROVED}. */
        public static Status parse(String value) {
            return value == null || value.isBlank() ? APPROVED : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public GlossaryTerm {
        termForms = termForms == null ? List.of() : List.copyOf(termForms);
        translationForms = translationForms == null ? List.of() : List.copyOf(translationForms);
        status = status == null ? Status.APPROVED : status;
    }

    /** An approved term with no other form and no definition. */
    public GlossaryTerm(String id, String term, String termLanguage, String translation, String translationLanguage,
                        String note, String scope) {
        this(id, term, termLanguage, null, translation, translationLanguage, null, null, note, Status.APPROVED, scope);
    }

    /** The term, then its other forms. */
    public List<String> allTermForms() {
        return all(term, termForms);
    }

    /** The translation, then its other forms. */
    public List<String> allTranslationForms() {
        return all(translation, translationForms);
    }

    /** True when the brand never writes this translation of the term. */
    public boolean forbidden() {
        return status == Status.FORBIDDEN;
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
}

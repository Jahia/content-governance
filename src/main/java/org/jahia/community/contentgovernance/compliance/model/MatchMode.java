package org.jahia.community.contentgovernance.compliance.model;

import java.util.Locale;

/**
 * How a glossary term is found in a text. Values are the {@code matchMode} choicelist values of
 * {@code ccs:glossaryTerm}.
 */
public enum MatchMode {
    /** The term as a whole word or phrase: "garanti" does not find "garantie". */
    WORD,
    /** Any word that starts with the term: "garanti" finds "garantie" and "garantis". */
    PREFIX,
    /** The term anywhere, inside other words too. */
    ANYWHERE;

    /**
     * Reads the stored choicelist value.
     *
     * @param value the {@code matchMode} property value, may be {@code null}
     * @return the mode, {@link #WORD} when the value is missing or unknown
     */
    public static MatchMode fromValue(String value) {
        if (value == null) {
            return WORD;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return WORD;
        }
    }
}

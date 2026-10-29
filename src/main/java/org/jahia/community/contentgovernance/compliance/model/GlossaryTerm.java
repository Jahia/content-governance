package org.jahia.community.contentgovernance.compliance.model;

/**
 * One non-authorised term, as read from a {@code ccs:glossaryTerm} node.
 *
 * @param term         the word or phrase to find, as the glossary writes it
 * @param mode         how the term is matched
 * @param reason       why the term may not be used, may be {@code null}
 * @param alternative  the suggested wording, may be {@code null}
 * @param termPath     path of the term node
 * @param glossaryPath path of the glossary that holds the term
 */
public record GlossaryTerm(String term, MatchMode mode, String reason, String alternative,
                           String termPath, String glossaryPath) {
}

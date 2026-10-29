package org.jahia.community.contentgovernance.compliance.model;

/**
 * How often one glossary term is used across a site.
 *
 * @param term         the glossary term
 * @param glossaryPath path of the glossary that holds it
 * @param reason       why the term may not be used, may be {@code null}
 * @param alternative  suggested wording, may be {@code null}
 * @param uses         number of uses
 * @param locations    number of pages and content items that use it
 */
public record TermUsage(String term, String glossaryPath, String reason, String alternative, int uses, int locations) {
}

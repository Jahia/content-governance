package org.jahia.community.contentgovernance.compliance.model;

/**
 * One use of a non-authorised term in one property value of one content.
 *
 * @param nodePath      path of the content that holds the text
 * @param nodeUuid      identifier of that content, used to open it in Content Editor
 * @param nodeName      display name of the content
 * @param nodeType      label of the content's type, in the UI language
 * @param propertyName  technical name of the property
 * @param propertyLabel label of the property, in the UI language
 * @param term          the glossary term that matched
 * @param matchedText   the text as written in the content
 * @param before        text before the match, shortened, as plain text
 * @param after         text after the match, shortened, as plain text
 * @param reason        why the term may not be used, may be {@code null}
 * @param alternative   suggested wording, may be {@code null}
 * @param glossaryPath  path of the glossary that holds the term
 * @param origin        where the content comes from, relative to the page
 * @param originPath    for {@link IssueOrigin#REFERENCE}, the reference on the page; for a shared area of an
 *                      ancestor page, that page; otherwise {@code null}
 */
public record ComplianceIssue(String nodePath, String nodeUuid, String nodeName, String nodeType,
                              String propertyName, String propertyLabel,
                              String term, String matchedText, String before, String after,
                              String reason, String alternative, String glossaryPath,
                              IssueOrigin origin, String originPath) {
}

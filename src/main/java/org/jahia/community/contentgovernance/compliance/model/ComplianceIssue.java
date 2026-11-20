package org.jahia.community.contentgovernance.compliance.model;

/**
 * One place where one property value of one content breaks a rule of the evaluator {@code pattern}, such as
 * a term that the legal team forbids.
 *
 * @param nodePath      path of the content that holds the text
 * @param nodeUuid      identifier of that content, used to open it in Content Editor
 * @param nodeName      display name of the content
 * @param nodeType      label of the content's type, in the UI language
 * @param propertyName  technical name of the property
 * @param propertyLabel label of the property, in the UI language
 * @param ruleId        the id of the rule, such as {@code compliance.click-here}
 * @param severity      the severity of the rule: critical, serious, moderate or minor
 * @param term          the pattern of the rule, as the rule writes it
 * @param matchedText   the text as written in the content
 * @param before        text before the match, shortened, as plain text
 * @param after         text after the match, shortened, as plain text
 * @param reason        the advice of the rule, may be {@code null}
 * @param alternative   the replacement that the rule names, may be {@code null}
 * @param folder        path of the folder that holds the rule, may be {@code null} for a rule that a module
 *                      ships and the site has no node of
 * @param origin        where the content comes from, relative to the page
 * @param originPath    for {@link IssueOrigin#REFERENCE}, the reference on the page; for a shared area of an
 *                      ancestor page, that page; otherwise {@code null}
 */
public record ComplianceIssue(String nodePath, String nodeUuid, String nodeName, String nodeType,
                              String propertyName, String propertyLabel,
                              String ruleId, String severity, String term, String matchedText, String before,
                              String after, String reason, String alternative, String folder,
                              IssueOrigin origin, String originPath) {
}

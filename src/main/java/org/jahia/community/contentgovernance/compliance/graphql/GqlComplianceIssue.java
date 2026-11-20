package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.compliance.model.ComplianceIssue;

/**
 * GraphQL view of a {@link ComplianceIssue}. Text fields are plain text: the client escapes them.
 */
@GraphQLName("ComplianceIssue")
@GraphQLDescription("One place where a content breaks a rule of the evaluator pattern, such as a forbidden term")
public class GqlComplianceIssue {

    private final ComplianceIssue issue;

    /** @param issue the issue to expose */
    public GqlComplianceIssue(ComplianceIssue issue) {
        this.issue = issue;
    }

    /** @return path of the content */
    @GraphQLField
    @GraphQLNonNull
    public String getNodePath() {
        return issue.nodePath();
    }

    /** @return identifier of the content */
    @GraphQLField
    @GraphQLNonNull
    public String getNodeUuid() {
        return issue.nodeUuid();
    }

    /** @return display name of the content */
    @GraphQLField
    public String getNodeName() {
        return issue.nodeName();
    }

    /** @return label of the content type */
    @GraphQLField
    public String getNodeType() {
        return issue.nodeType();
    }

    /** @return technical name of the property */
    @GraphQLField
    @GraphQLNonNull
    public String getPropertyName() {
        return issue.propertyName();
    }

    /** @return label of the property */
    @GraphQLField
    public String getPropertyLabel() {
        return issue.propertyLabel();
    }

    /** @return the id of the rule */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the rule, such as compliance.click-here")
    public String getRuleId() {
        return issue.ruleId();
    }

    /** @return the severity of the rule */
    @GraphQLField
    @GraphQLDescription("critical, serious, moderate or minor")
    public String getSeverity() {
        return issue.severity();
    }

    /** @return the folder of the rule */
    @GraphQLField
    @GraphQLDescription("The path of the folder that holds the rule")
    public String getFolder() {
        return issue.folder();
    }

    /** @return the pattern of the rule */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The pattern of the rule, as the rule writes it")
    public String getTerm() {
        return issue.term();
    }

    /** @return the text as written in the content */
    @GraphQLField
    @GraphQLNonNull
    public String getMatchedText() {
        return issue.matchedText();
    }

    /** @return text before the match */
    @GraphQLField
    public String getBefore() {
        return issue.before();
    }

    /** @return text after the match */
    @GraphQLField
    public String getAfter() {
        return issue.after();
    }

    /** @return the advice of the rule */
    @GraphQLField
    @GraphQLDescription("The advice of the rule")
    public String getReason() {
        return issue.reason();
    }

    /** @return the replacement of the rule */
    @GraphQLField
    @GraphQLDescription("The replacement that the rule names")
    public String getAlternative() {
        return issue.alternative();
    }

    /** @return the folder of the rule, or an empty path for a rule that a module ships and the site has no node of */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The same as folder, or an empty path when the rule has no folder. Kept for the clients of version 1")
    public String getGlossaryPath() {
        return issue.folder() == null ? "" : issue.folder();
    }

    /** @return where the content comes from */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("PAGE: stored under the page. REFERENCE: pointed to by a content reference of the page. SHARED: stored elsewhere and shown on the page")
    public GqlIssueOrigin getOrigin() {
        return GqlIssueOrigin.valueOf(issue.origin().name());
    }

    /** @return the reference on the page, for a REFERENCE issue */
    @GraphQLField
    @GraphQLDescription("For REFERENCE, path of the content reference on the page. For SHARED content of an ancestor page (header, footer), path of that page")
    public String getOriginPath() {
        return issue.originPath();
    }
}

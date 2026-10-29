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
@GraphQLDescription("One use of a non-authorised term in a content")
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

    /** @return the glossary term */
    @GraphQLField
    @GraphQLNonNull
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

    /** @return why the term may not be used */
    @GraphQLField
    public String getReason() {
        return issue.reason();
    }

    /** @return suggested wording */
    @GraphQLField
    public String getAlternative() {
        return issue.alternative();
    }

    /** @return path of the glossary */
    @GraphQLField
    @GraphQLNonNull
    public String getGlossaryPath() {
        return issue.glossaryPath();
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

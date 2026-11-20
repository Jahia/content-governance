package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.check.spi.Finding;

/**
 * One finding of a check that a module registers.
 */
@GraphQLName("ContentGovernanceCheckFinding")
@GraphQLDescription("A place where a content breaks a rule, as a registered check found it")
public class GqlCheckFinding {

    private final Finding finding;

    GqlCheckFinding(Finding finding) {
        this.finding = finding;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the rule that the content breaks")
    public String getRuleId() {
        return finding.ruleId();
    }

    @GraphQLField
    @GraphQLDescription("The severity of the rule as the site applies it: critical, serious, moderate or minor")
    public String getSeverity() {
        return finding.severity();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The text that breaks the rule, such as a matched term, an HTML tag or a measured value")
    public String getText() {
        return finding.text();
    }

    @GraphQLField
    @GraphQLDescription("The text around the match, or null")
    public String getContext() {
        return finding.context();
    }
}

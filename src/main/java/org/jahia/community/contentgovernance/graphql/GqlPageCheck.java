package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

/**
 * The check of a page or of a content item: the result of each rule that ran, with its values and its findings.
 */
@GraphQLName("ContentGovernancePageCheck")
@GraphQLDescription("The result of each rule that every check of the server ran on a page, with its values and its findings")
public class GqlPageCheck {

    private final String path;
    private final List<GqlCheckResult> results;

    GqlPageCheck(String path, List<GqlCheckResult> results) {
        this.path = path;
        this.results = results;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The path of the page or of the content item")
    public String getPath() {
        return path;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("One result per rule that ran, passed or failed, in the order of the rules")
    public List<GqlCheckResult> getResults() {
        return results;
    }
}

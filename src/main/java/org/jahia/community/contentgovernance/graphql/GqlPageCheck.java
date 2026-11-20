package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

/**
 * The check of a page or of a content item: its findings, and the facts that the tabs of the drawer show.
 */
@GraphQLName("ContentGovernancePageCheck")
@GraphQLDescription("The findings of every check of the server on a page, and the facts that the drawer shows")
public class GqlPageCheck {

    private final String path;
    private final List<GqlCheckFinding> findings;
    private final String facts;

    GqlPageCheck(String path, List<GqlCheckFinding> findings, String facts) {
        this.path = path;
        this.findings = findings;
        this.facts = facts;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The path of the page or of the content item")
    public String getPath() {
        return path;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The findings, in the order of the rules")
    public List<GqlCheckFinding> getFindings() {
        return findings;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("A JSON object of the facts that the tabs of the drawer show, by tab: seo (the tags of the page), "
            + "readability (the statistics of the text and the headings), links and jahia (the state of the content)")
    public String getFacts() {
        return facts;
    }
}

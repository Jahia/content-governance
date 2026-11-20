package org.jahia.community.contentgovernance.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;

import java.util.List;
import java.util.Map;

/**
 * The check of a page or of a content item: the findings of every check of the server, and the facts
 * that the checks of SEO, readability, links and Jahia health read on it.
 */
@GraphQLName("ContentGovernancePageCheck")
@GraphQLDescription("The findings of the checks of the server on a page or a content item, and what they read on it")
public class GqlPageCheck {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<GqlCheckFinding> findings;
    private final String facts;

    GqlPageCheck(List<GqlCheckFinding> findings, Map<String, Object> facts) {
        this.findings = findings;
        try {
            this.facts = JSON.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new DataFetchingException("The facts of the page could not be written");
        }
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The findings, in the order of the rules")
    public List<GqlCheckFinding> getFindings() {
        return findings;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The facts as JSON, by tab of the drawer: seo (a page only), readability, links and jahia")
    public String getFacts() {
        return facts;
    }
}

package org.jahia.community.contentgovernance.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;

import java.util.List;

/**
 * The result of one rule on a page or a content item, as {@link CheckResult} gives it.
 */
@GraphQLName("ContentGovernanceCheckResult")
@GraphQLDescription("The result of one rule that ran: passed or failed, the values that its check measured, and its findings")
public class GqlCheckResult {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CheckResult result;

    GqlCheckResult(CheckResult result) {
        this.result = result;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the rule")
    public String getRuleId() {
        return result.ruleId();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the content keeps the rule. A measure rule, such as readability.statistics, always passes")
    public boolean getPassed() {
        return result.passed();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("A JSON object of the values that the check measured, by name, such as {\"length\": 92, \"text\": "
            + "\"Annual report\"} for seo.title; {} when it measured nothing")
    public String getValues() {
        try {
            return JSON.writeValueAsString(result.values());
        } catch (JsonProcessingException e) {
            throw new DataFetchingException("The values of the rule " + result.ruleId() + " could not be written");
        }
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The places where the content breaks the rule, at most 50; none when it passes")
    public List<GqlCheckFinding> getFindings() {
        return result.findings().stream().map(GqlCheckFinding::new).toList();
    }
}

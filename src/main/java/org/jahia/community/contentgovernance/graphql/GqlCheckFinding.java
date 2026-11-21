package org.jahia.community.contentgovernance.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;

/**
 * One finding of a check that a module registers.
 */
@GraphQLName("ContentGovernanceCheckFinding")
@GraphQLDescription("A place where a content breaks a rule, as a registered check found it")
public class GqlCheckFinding {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    @GraphQLField
    @GraphQLDescription("A CSS selector of the element that breaks the rule in the rendered page, or null")
    public String getLocator() {
        return finding.locator();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("A JSON object of the values that the check measured at this place; {} when it measured nothing")
    public String getValues() {
        try {
            return JSON.writeValueAsString(finding.values());
        } catch (JsonProcessingException e) {
            throw new DataFetchingException("The values of a finding of the rule " + finding.ruleId() + " could not be written");
        }
    }
}

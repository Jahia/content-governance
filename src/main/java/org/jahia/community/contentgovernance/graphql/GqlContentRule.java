package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.rules.ContentRule;

/**
 * One rule of the catalog as a site applies it, for the analyzers of the drawer.
 */
@GraphQLName("ContentGovernanceRule")
@GraphQLDescription("A rule of the catalog of Content Governance, as a site applies it")
public class GqlContentRule {

    private final ContentRule rule;

    GqlContentRule(ContentRule rule) {
        this.rule = rule;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id that each finding of the rule carries, such as seo.title")
    public String getId() {
        return rule.id();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The group of the rule, such as seo or geo")
    public String getDomain() {
        return rule.domain();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What checks the rule: pattern, threshold, presence, model, or the id of a check that a module registers")
    public String getEvaluator() {
        return rule.evaluator();
    }

    @GraphQLField
    @GraphQLDescription("True when a finding of the rule stops the publication of the content")
    public boolean isBlocking() {
        return rule.blocking();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("critical, serious, moderate or minor")
    public String getSeverity() {
        return rule.severity();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("False when the site turned the rule off")
    public boolean isEnabled() {
        return rule.enabled();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The role of the person who fixes a finding: editor, developer or administrator")
    public String getFixer() {
        return rule.fixer();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What to do about a finding, in English")
    public String getAdvice() {
        return rule.advice();
    }

    @GraphQLField
    @GraphQLDescription("The unit of the limits, or null when the rule has none")
    public String getUnit() {
        return rule.unit();
    }

    @GraphQLField
    @GraphQLDescription("The lowest value that passes, or null")
    public Double getMinimum() {
        return rule.minimum();
    }

    @GraphQLField
    @GraphQLDescription("The highest value that passes, or null")
    public Double getMaximum() {
        return rule.maximum();
    }
}

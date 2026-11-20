package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.compliance.model.TermUsage;

/**
 * GraphQL view of a {@link TermUsage}.
 */
@GraphQLName("ComplianceTermUsage")
@GraphQLDescription("How often a rule of the evaluator pattern is broken across a site")
public class GqlTermUsage {

    private final TermUsage usage;

    /** @param usage the usage to expose */
    public GqlTermUsage(TermUsage usage) {
        this.usage = usage;
    }

    /** @return the id of the rule */
    @GraphQLField
    @GraphQLNonNull
    public String getRuleId() {
        return usage.ruleId();
    }

    /** @return the pattern of the rule */
    @GraphQLField
    @GraphQLNonNull
    public String getTerm() {
        return usage.term();
    }

    /** @return the folder of the rule */
    @GraphQLField
    public String getFolder() {
        return usage.folder();
    }

    /** @return the folder of the rule, or an empty path for a rule that a module ships and the site has no node of */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The same as folder, or an empty path when the rule has no folder. Kept for the clients of version 1")
    public String getGlossaryPath() {
        return usage.folder() == null ? "" : usage.folder();
    }

    /** @return the advice of the rule */
    @GraphQLField
    public String getReason() {
        return usage.reason();
    }

    /** @return the replacement of the rule */
    @GraphQLField
    public String getAlternative() {
        return usage.alternative();
    }

    /** @return number of uses */
    @GraphQLField
    public int getUses() {
        return usage.uses();
    }

    /** @return number of pages and content items using it */
    @GraphQLField
    public int getLocations() {
        return usage.locations();
    }
}

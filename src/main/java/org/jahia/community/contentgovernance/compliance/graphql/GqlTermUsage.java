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
@GraphQLDescription("How often a glossary term is used across a site")
public class GqlTermUsage {

    private final TermUsage usage;

    /** @param usage the usage to expose */
    public GqlTermUsage(TermUsage usage) {
        this.usage = usage;
    }

    /** @return the glossary term */
    @GraphQLField
    @GraphQLNonNull
    public String getTerm() {
        return usage.term();
    }

    /** @return path of the glossary */
    @GraphQLField
    @GraphQLNonNull
    public String getGlossaryPath() {
        return usage.glossaryPath();
    }

    /** @return why the term may not be used */
    @GraphQLField
    public String getReason() {
        return usage.reason();
    }

    /** @return suggested wording */
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

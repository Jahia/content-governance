package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.contentgovernance.compliance.graphql.GqlComplianceScan;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

/**
 * Adds {@code contentGovernance { ... }} and {@code complianceScan { ... }} to the root Query.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public final class ContentGovernanceQueryExtension {

    private ContentGovernanceQueryExtension() {
    }

    /**
     * @return the entry point of the settings queries
     */
    @GraphQLField
    @GraphQLName("contentGovernance")
    @GraphQLNonNull
    @GraphQLDescription("Content Governance: which sections and tabs a site uses, and whether AI is configured")
    public static GqlContentGovernance contentGovernance() {
        return new GqlContentGovernance();
    }

    /**
     * @return the entry point of the compliance scan queries
     */
    @GraphQLField
    @GraphQLName("complianceScan")
    @GraphQLNonNull
    @GraphQLDescription("Content compliance scan: checks content against the site's glossaries of non-authorised terms")
    public static GqlComplianceScan complianceScan() {
        return new GqlComplianceScan();
    }
}

package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

/**
 * Adds {@code contentGovernance { ... }} to the root Mutation.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
public final class ContentGovernanceMutationExtension {

    private ContentGovernanceMutationExtension() {
    }

    /**
     * @return the entry point of the settings mutations
     */
    @GraphQLField
    @GraphQLName("contentGovernance")
    @GraphQLNonNull
    @GraphQLDescription("Content Governance settings mutations")
    public static GqlContentGovernanceMutation contentGovernance() {
        return new GqlContentGovernanceMutation();
    }
}

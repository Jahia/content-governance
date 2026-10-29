package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL mirror of {@link org.jahia.community.contentgovernance.compliance.model.LocationKind}, named for the shared schema.
 */
@GraphQLName("ComplianceLocationKind")
@GraphQLDescription("PAGE: a page and the content under it. CONTENT: a content item of the content folders")
public enum GqlLocationKind {
    PAGE, CONTENT
}
